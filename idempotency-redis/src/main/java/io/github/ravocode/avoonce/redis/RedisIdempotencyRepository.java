package io.github.ravocode.avoonce.redis;

import io.github.ravocode.avoonce.core.config.IdempotencyConfig;
import io.github.ravocode.avoonce.core.domain.IdempotencyRecord;
import io.github.ravocode.avoonce.core.domain.IdempotencyResponse;
import io.github.ravocode.avoonce.core.domain.IdempotencyStatus;
import io.github.ravocode.avoonce.core.exception.IdempotencyConflictException;
import io.github.ravocode.avoonce.core.exception.IdempotencyMismatchException;
import io.github.ravocode.avoonce.core.spi.IdempotencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * A distributed {@link IdempotencyRepository} backed by Redis using Jedis.
 */
public class RedisIdempotencyRepository implements IdempotencyRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyRepository.class);
    private static final String KEY_PREFIX = "idempotency:";

    private final RedisOperations redisOperations;
    private final IdempotencyConfig config;

    public RedisIdempotencyRepository(RedisOperations redisOperations, IdempotencyConfig config) {
        this.redisOperations = redisOperations;
        this.config = config;
    }

    @Override
    public Optional<IdempotencyRecord> acquireOrGet(final String idempotencyKey) {
        return acquireOrGet(idempotencyKey, null);
    }

    @Override
    public Optional<IdempotencyRecord> acquireOrGet(final String idempotencyKey, final String requestHash) {
        long now = System.currentTimeMillis();
        long ttlMillis = config.getUnit().toMillis(config.getTtl());
        long expiresAt = now + ttlMillis;

        final byte[] redisKey = getRedisKey(idempotencyKey);
        final IdempotencyRecord newRecord = new IdempotencyRecord(
                idempotencyKey, IdempotencyStatus.STARTED, null, expiresAt, requestHash
        );
        final byte[] serializedRecord = RecordSerializer.serialize(newRecord);

        // Attempt to acquire lock atomically
        boolean acquired = redisOperations.setIfAbsent(redisKey, serializedRecord, ttlMillis);

        if (acquired) {
            log.debug("[idempotency] Lock acquired in Redis for key='{}'", idempotencyKey);
            return Optional.empty();
        }

        // Lock not acquired, fetch existing record
        byte[] existingBytes = redisOperations.get(redisKey);
        if (existingBytes == null) {
            // Key vanished between SETNX failure and GET. Retry once.
            acquired = redisOperations.setIfAbsent(redisKey, serializedRecord, ttlMillis);
            if (acquired) {
                log.debug("[idempotency] Lock acquired in Redis for key='{}' on retry", idempotencyKey);
                return Optional.empty();
            }
            // If it fails again, we just fail and let the user retry.
            throw new IdempotencyConflictException("Concurrent operations on key: " + idempotencyKey);
        }

        IdempotencyRecord existing = RecordSerializer.deserialize(existingBytes);
        if (existing == null) {
            throw new IllegalStateException("Failed to deserialize existing record for key: " + idempotencyKey);
        }

        if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
            if (existing.getRequestHash() != null && requestHash != null && !existing.getRequestHash().equals(requestHash)) {
                throw new IdempotencyMismatchException("Idempotency key reused with a different request payload");
            }
            log.debug("[idempotency] Replaying COMPLETED record for key='{}'", idempotencyKey);
            return Optional.of(existing);
        }

        // Status is STARTED or FAILED.
        if (existing.getExpiresAt() != null && now < existing.getExpiresAt()) {
            log.warn("[idempotency] Conflict: key='{}' is already in progress (lock held until {})",
                    idempotencyKey, existing.getExpiresAt());
            throw new IdempotencyConflictException("Request with key " + idempotencyKey + " is already in progress.");
        }

        // Expiry occurred but Redis hasn't evicted it yet? We can just delete and retry.
        log.warn("[idempotency] Stale {} record found for key='{}', evicting and re-acquiring",
                existing.getStatus(), idempotencyKey);
        redisOperations.delete(redisKey);
        acquired = redisOperations.setIfAbsent(redisKey, serializedRecord, ttlMillis);
        if (acquired) {
            return Optional.empty();
        }
        throw new IdempotencyConflictException("Could not acquire lock after evicting stale record for key: " + idempotencyKey);
    }

    @Override
    public void saveSuccess(final String idempotencyKey, final IdempotencyResponse response) {
        long now = System.currentTimeMillis();
        long ttlMillis = config.getUnit().toMillis(config.getTtl());
        long expiresAt = now + ttlMillis;

        final byte[] redisKey = getRedisKey(idempotencyKey);
        
        // We need to keep the request hash from the previous state, so we get it first.
        byte[] existingBytes = redisOperations.get(redisKey);
        String requestHash = null;
        if (existingBytes != null) {
            IdempotencyRecord existing = RecordSerializer.deserialize(existingBytes);
            if (existing != null) {
                requestHash = existing.getRequestHash();
            }
        }

        IdempotencyRecord completedRecord = new IdempotencyRecord(
                idempotencyKey, IdempotencyStatus.COMPLETED, response, expiresAt, requestHash
        );
        byte[] serializedRecord = RecordSerializer.serialize(completedRecord);
        
        // Overwrite and extend TTL
        redisOperations.set(redisKey, serializedRecord, ttlMillis);
    }

    @Override
    public void saveFailure(final String idempotencyKey, final String errorMessage) {
        long now = System.currentTimeMillis();
        long ttlMillis = config.getUnit().toMillis(config.getTtl());
        long expiresAt = now + ttlMillis;

        final byte[] redisKey = getRedisKey(idempotencyKey);
        
        byte[] existingBytes = redisOperations.get(redisKey);
        String requestHash = null;
        if (existingBytes != null) {
            IdempotencyRecord existing = RecordSerializer.deserialize(existingBytes);
            if (existing != null) {
                requestHash = existing.getRequestHash();
            }
        }

        IdempotencyRecord failedRecord = new IdempotencyRecord(
                idempotencyKey, IdempotencyStatus.FAILED, null, expiresAt, requestHash
        );
        byte[] serializedRecord = RecordSerializer.serialize(failedRecord);
        
        // Overwrite and extend TTL
        redisOperations.set(redisKey, serializedRecord, ttlMillis);
    }

    @Override
    public Optional<IdempotencyRecord> get(final String idempotencyKey) {
        byte[] bytes = redisOperations.get(getRedisKey(idempotencyKey));
        if (bytes == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(RecordSerializer.deserialize(bytes));
    }

    @Override
    public void delete(final String idempotencyKey) {
        redisOperations.delete(getRedisKey(idempotencyKey));
    }

    @Override
    public int evictExpired() {
        // Redis handles TTL eviction natively.
        return 0;
    }

    private byte[] getRedisKey(String idempotencyKey) {
        return (KEY_PREFIX + idempotencyKey).getBytes(StandardCharsets.UTF_8);
    }
}
