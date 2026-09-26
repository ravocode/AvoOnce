package io.github.ravocode.avoonce.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.ravocode.avoonce.core.config.IdempotencyConfig;
import io.github.ravocode.avoonce.core.domain.IdempotencyRecord;
import io.github.ravocode.avoonce.core.domain.IdempotencyResponse;
import io.github.ravocode.avoonce.core.domain.IdempotencyStatus;
import io.github.ravocode.avoonce.core.exception.IdempotencyConflictException;
import io.github.ravocode.avoonce.core.exception.IdempotencyMismatchException;
import io.github.ravocode.avoonce.core.spi.IdempotencyRepository;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory {@link IdempotencyRepository} backed by a Caffeine cache.
 *
 * <p>Suitable for single-node deployments or local testing. TTL-based expiry and
 * conflict detection are handled entirely in-process via Caffeine's atomic
 * {@link Cache#asMap()} compute operations.
 */
public class CaffeineIdempotencyRepository implements IdempotencyRepository {

    private static final Logger log = LoggerFactory.getLogger(CaffeineIdempotencyRepository.class);

    /** The underlying Caffeine cache keyed by idempotency key string. */
    private final Cache<String, IdempotencyRecord> cache;
    /** The TTL and lock-timeout configuration for this repository. */
    private final IdempotencyConfig config;

    /**
     * Constructs a {@code CaffeineIdempotencyRepository} with the given configuration.
     * A new Caffeine cache is created with TTL-based expiry matching {@code config.getTtl()}.
     *
     * @param config the idempotency TTL and lock-timeout configuration.
     */
    public CaffeineIdempotencyRepository(final IdempotencyConfig config) {
        this.config = config;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(config.getTtl(), config.getUnit())
                .build();
    }

    /**
     * Delegates to {@link #acquireOrGet(String, String)} with a {@code null} hash,
     * keeping all locking logic in one place.
     */
    @Override
    public Optional<IdempotencyRecord> acquireOrGet(final String idempotencyKey) {
        return acquireOrGet(idempotencyKey, null);
    }

    /**
     * Atomically acquires a lock or returns an existing COMPLETED record.
     * <p>
     * This override is required to ensure the {@code requestHash} is persisted inside
     * the newly-created STARTED record. Without it, the SPI default would delegate to
     * {@link #acquireOrGet(String)}, producing a record with a {@code null} requestHash
     * and making the mismatch guard permanently ineffective.
     *
     * @throws IdempotencyConflictException if a non-expired STARTED record already exists.
     * @throws IdempotencyMismatchException if a COMPLETED record exists with a different hash.
     */
    @Override
    public Optional<IdempotencyRecord> acquireOrGet(final String idempotencyKey, final String requestHash) {
        final IdempotencyRecord result = cache.asMap().compute(idempotencyKey, (key, existing) -> {
            final long now = System.currentTimeMillis();

            if (existing == null || existing.getStatus() == IdempotencyStatus.FAILED) {
                // No record yet, or the previous attempt failed — acquire a fresh lock.
                log.debug("[idempotency] Acquiring lock for key='{}'", key);
                final long expiresAt = now + config.getUnit().toMillis(config.getTtl());
                return new IdempotencyRecord(key, IdempotencyStatus.STARTED, null, expiresAt, requestHash);
            }

            if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
                // Validate hash consistency before replaying the cached response.
                if (existing.getRequestHash() != null
                        && requestHash != null
                        && !existing.getRequestHash().equals(requestHash)) {
                    throw new IdempotencyMismatchException(
                            "Idempotency key reused with a different request payload");
                }
                return existing;
            }

            // STARTED state — check whether the lock has timed out.
            if (existing.getExpiresAt() != null && now < existing.getExpiresAt()) {
                log.warn("[idempotency] Conflict: key='{}' is already in progress (lock held until {})",
                        key, existing.getExpiresAt());
                throw new IdempotencyConflictException(
                        "Request with key " + key + " is already in progress.");
            }

            // Lock has expired — allow re-acquisition.
            log.warn("[idempotency] Lock expired for key='{}', re-acquiring", key);
            final long expiresAt = now + config.getUnit().toMillis(config.getTtl());
            return new IdempotencyRecord(key, IdempotencyStatus.STARTED, null, expiresAt, requestHash);
        });

        return result.getStatus() == IdempotencyStatus.COMPLETED
                ? Optional.of(result)
                : Optional.empty();
    }

    /**
     * Transitions the record for the given key to {@link IdempotencyStatus#COMPLETED} and stores the response.
     *
     * @param idempotencyKey the idempotency key of the completing request.
     * @param response       the response to cache for future replay.
     */
    @Override
    public void saveSuccess(final String idempotencyKey, final IdempotencyResponse response) {
        cache.asMap().computeIfPresent(idempotencyKey, (key, existing) ->
                new IdempotencyRecord(key, IdempotencyStatus.COMPLETED, response, null,
                        existing.getRequestHash())
        );
    }

    /**
     * Transitions the record for the given key to {@link IdempotencyStatus#FAILED},
     * allowing the client to safely retry the operation.
     *
     * @param idempotencyKey the idempotency key of the failed request.
     * @param errorMessage   a description of the failure cause (not persisted in Caffeine).
     */
    @Override
    public void saveFailure(final String idempotencyKey, final String errorMessage) {
        cache.asMap().computeIfPresent(idempotencyKey, (key, existing) ->
                new IdempotencyRecord(key, IdempotencyStatus.FAILED, null, null,
                        existing.getRequestHash())
        );
    }

    /**
     * Retrieves the current {@link IdempotencyRecord} for the given key without modifying it.
     *
     * @param idempotencyKey the key to look up.
     * @return an {@link Optional} containing the record, or empty if not found or expired.
     */
    @Override
    public Optional<IdempotencyRecord> get(final String idempotencyKey) {
        return Optional.ofNullable(cache.getIfPresent(idempotencyKey));
    }

    /**
     * Removes the idempotency record for the given key from the in-memory cache.
     * Useful for administrative invalidation of a specific key.
     */
    @Override
    public void delete(final String idempotencyKey) {
        cache.invalidate(idempotencyKey);
    }

    /**
     * No-op for Caffeine: TTL-based expiry is handled automatically by the underlying cache.
     *
     * @return always {@code 0}
     */
    @Override
    public int evictExpired() {
        return 0;
    }
}