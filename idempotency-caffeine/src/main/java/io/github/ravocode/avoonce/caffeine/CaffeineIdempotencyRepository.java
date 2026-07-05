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

public class CaffeineIdempotencyRepository implements IdempotencyRepository {

    private final Cache<String, IdempotencyRecord> cache;
    private final IdempotencyConfig config;

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
        IdempotencyRecord result = cache.asMap().compute(idempotencyKey, (key, existing) -> {
            long now = System.currentTimeMillis();

            if (existing == null || existing.getStatus() == IdempotencyStatus.FAILED) {
                // No record yet, or the previous attempt failed — acquire a fresh lock.
                long expiresAt = now + config.getUnit().toMillis(config.getTtl());
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
                throw new IdempotencyConflictException(
                        "Request with key " + key + " is already in progress.");
            }

            // Lock has expired — allow re-acquisition.
            long expiresAt = now + config.getUnit().toMillis(config.getTtl());
            return new IdempotencyRecord(key, IdempotencyStatus.STARTED, null, expiresAt, requestHash);
        });

        return result.getStatus() == IdempotencyStatus.COMPLETED
                ? Optional.of(result)
                : Optional.empty();
    }

    @Override
    public void saveSuccess(final String idempotencyKey, final IdempotencyResponse response) {
        cache.asMap().computeIfPresent(idempotencyKey, (key, existing) ->
                new IdempotencyRecord(key, IdempotencyStatus.COMPLETED, response, null,
                        existing.getRequestHash())
        );
    }

    @Override
    public void saveFailure(final String idempotencyKey, final String errorMessage) {
        cache.asMap().computeIfPresent(idempotencyKey, (key, existing) ->
                new IdempotencyRecord(key, IdempotencyStatus.FAILED, null, null,
                        existing.getRequestHash())
        );
    }

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