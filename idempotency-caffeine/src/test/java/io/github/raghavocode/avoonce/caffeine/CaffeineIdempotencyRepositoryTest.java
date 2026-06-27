package io.github.raghavocode.avoonce.caffeine;

import io.github.raghavocode.avoonce.core.config.IdempotencyConfig;
import io.github.raghavocode.avoonce.core.domain.IdempotencyRecord;
import io.github.raghavocode.avoonce.core.domain.IdempotencyResponse;
import io.github.raghavocode.avoonce.core.domain.IdempotencyStatus;
import io.github.raghavocode.avoonce.core.exception.IdempotencyConflictException;
import io.github.raghavocode.avoonce.core.exception.IdempotencyMismatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CaffeineIdempotencyRepositoryTest {

    private CaffeineIdempotencyRepository repository;
    private IdempotencyConfig config;

    @BeforeEach
    void setUp() {
        config = new IdempotencyConfig(1, TimeUnit.SECONDS);
        repository = new CaffeineIdempotencyRepository(config);
    }

    // -------------------------------------------------------------------------
    // acquireOrGet(key) — no-hash variant
    // -------------------------------------------------------------------------

    @Test
    void acquireOrGet_whenNewKey_shouldReturnEmptyAndStartRecord() {
        String key = UUID.randomUUID().toString();
        Optional<IdempotencyRecord> result = repository.acquireOrGet(key);

        assertFalse(result.isPresent());

        Optional<IdempotencyRecord> saved = repository.get(key);
        assertTrue(saved.isPresent());
        assertEquals(IdempotencyStatus.STARTED, saved.get().getStatus());
        assertNotNull(saved.get().getExpiresAt());
    }

    @Test
    void acquireOrGet_whenStartedAndNotExpired_shouldThrowConflict() {
        String key = UUID.randomUUID().toString();
        repository.acquireOrGet(key);

        assertThrows(IdempotencyConflictException.class, () -> repository.acquireOrGet(key));
    }

    @Test
    void acquireOrGet_whenStartedAndExpired_shouldAcquireNewLock() throws InterruptedException {
        String key = UUID.randomUUID().toString();
        repository.acquireOrGet(key);

        Thread.sleep(1100); // wait past the 1-second lock timeout

        Optional<IdempotencyRecord> result = repository.acquireOrGet(key);
        assertFalse(result.isPresent());

        Optional<IdempotencyRecord> saved = repository.get(key);
        assertTrue(saved.isPresent());
        assertEquals(IdempotencyStatus.STARTED, saved.get().getStatus());
    }

    @Test
    void acquireOrGet_whenCompleted_shouldReturnRecord() {
        String key = UUID.randomUUID().toString();
        IdempotencyResponse response = new IdempotencyResponse(200, null, null);
        repository.acquireOrGet(key);
        repository.saveSuccess(key, response);

        Optional<IdempotencyRecord> result = repository.acquireOrGet(key);

        assertTrue(result.isPresent());
        assertEquals(IdempotencyStatus.COMPLETED, result.get().getStatus());
        assertEquals(200, result.get().getResponse().getStatusCode());
    }

    @Test
    void acquireOrGet_whenFailed_shouldAcquireNewLock() {
        String key = UUID.randomUUID().toString();
        repository.acquireOrGet(key);
        repository.saveFailure(key, "error");

        Optional<IdempotencyRecord> result = repository.acquireOrGet(key);
        assertFalse(result.isPresent());

        Optional<IdempotencyRecord> saved = repository.get(key);
        assertTrue(saved.isPresent());
        assertEquals(IdempotencyStatus.STARTED, saved.get().getStatus());
    }

    // -------------------------------------------------------------------------
    // acquireOrGet(key, requestHash) — hash-aware variant
    // -------------------------------------------------------------------------

    @Test
    void acquireOrGetWithHash_whenNewKey_shouldStoreHashInStartedRecord() {
        String key = UUID.randomUUID().toString();
        String hash = "hash-abc";

        Optional<IdempotencyRecord> result = repository.acquireOrGet(key, hash);
        assertFalse(result.isPresent());

        Optional<IdempotencyRecord> saved = repository.get(key);
        assertTrue(saved.isPresent());
        assertEquals(IdempotencyStatus.STARTED, saved.get().getStatus());
        assertEquals(hash, saved.get().getRequestHash());
    }

    @Test
    void acquireOrGetWithHash_whenCompletedWithSameHash_shouldReturnRecord() {
        String key = UUID.randomUUID().toString();
        String hash = "hash-abc";
        IdempotencyResponse response = new IdempotencyResponse(200, null, null);

        repository.acquireOrGet(key, hash);
        repository.saveSuccess(key, response);

        Optional<IdempotencyRecord> result = repository.acquireOrGet(key, hash);

        assertTrue(result.isPresent());
        assertEquals(IdempotencyStatus.COMPLETED, result.get().getStatus());
        assertEquals(hash, result.get().getRequestHash());
    }

    @Test
    void acquireOrGetWithHash_whenCompletedWithDifferentHash_shouldThrowMismatch() {
        String key = UUID.randomUUID().toString();
        IdempotencyResponse response = new IdempotencyResponse(200, null, null);

        repository.acquireOrGet(key, "hash-original");
        repository.saveSuccess(key, response);

        assertThrows(IdempotencyMismatchException.class,
                () -> repository.acquireOrGet(key, "hash-different"));
    }

    @Test
    void acquireOrGetWithHash_whenStartedAndNotExpired_shouldThrowConflict() {
        String key = UUID.randomUUID().toString();
        repository.acquireOrGet(key, "hash-abc");

        assertThrows(IdempotencyConflictException.class,
                () -> repository.acquireOrGet(key, "hash-abc"));
    }

    @Test
    void acquireOrGetWithHash_whenFailed_shouldAcquireNewLockWithNewHash() {
        String key = UUID.randomUUID().toString();
        repository.acquireOrGet(key, "hash-old");
        repository.saveFailure(key, "error");

        Optional<IdempotencyRecord> result = repository.acquireOrGet(key, "hash-new");
        assertFalse(result.isPresent());

        Optional<IdempotencyRecord> saved = repository.get(key);
        assertTrue(saved.isPresent());
        assertEquals(IdempotencyStatus.STARTED, saved.get().getStatus());
        assertEquals("hash-new", saved.get().getRequestHash());
    }

    // -------------------------------------------------------------------------
    // saveSuccess / saveFailure
    // -------------------------------------------------------------------------

    @Test
    void saveSuccess_shouldUpdateRecordToCompleted() {
        String key = UUID.randomUUID().toString();
        repository.acquireOrGet(key);
        IdempotencyResponse response = new IdempotencyResponse(201, null, "body".getBytes());
        repository.saveSuccess(key, response);

        Optional<IdempotencyRecord> saved = repository.get(key);
        assertTrue(saved.isPresent());
        assertEquals(IdempotencyStatus.COMPLETED, saved.get().getStatus());
        assertEquals(201, saved.get().getResponse().getStatusCode());
        assertArrayEquals("body".getBytes(), saved.get().getResponse().getBody());
        assertNull(saved.get().getExpiresAt());
    }

    @Test
    void saveSuccess_shouldPreserveRequestHash() {
        String key = UUID.randomUUID().toString();
        String hash = "hash-abc";
        repository.acquireOrGet(key, hash);
        repository.saveSuccess(key, new IdempotencyResponse(200, null, null));

        Optional<IdempotencyRecord> saved = repository.get(key);
        assertTrue(saved.isPresent());
        assertEquals(hash, saved.get().getRequestHash());
    }

    @Test
    void saveFailure_shouldUpdateRecordToFailed() {
        String key = UUID.randomUUID().toString();
        repository.acquireOrGet(key);
        repository.saveFailure(key, "Something went wrong");

        Optional<IdempotencyRecord> saved = repository.get(key);
        assertTrue(saved.isPresent());
        assertEquals(IdempotencyStatus.FAILED, saved.get().getStatus());
        assertNull(saved.get().getResponse());
        assertNull(saved.get().getExpiresAt());
    }

    @Test
    void saveFailure_shouldPreserveRequestHash() {
        String key = UUID.randomUUID().toString();
        String hash = "hash-abc";
        repository.acquireOrGet(key, hash);
        repository.saveFailure(key, "error");

        Optional<IdempotencyRecord> saved = repository.get(key);
        assertTrue(saved.isPresent());
        assertEquals(hash, saved.get().getRequestHash());
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_shouldRemoveExistingRecord() {
        String key = UUID.randomUUID().toString();
        repository.acquireOrGet(key);

        repository.delete(key);

        assertFalse(repository.get(key).isPresent());
    }

    @Test
    void delete_onNonExistentKey_shouldBeNoOp() {
        // Must not throw
        assertDoesNotThrow(() -> repository.delete(UUID.randomUUID().toString()));
    }

    // -------------------------------------------------------------------------
    // evictExpired
    // -------------------------------------------------------------------------

    @Test
    void evictExpired_shouldReturnZero() {
        // Caffeine handles eviction automatically; the method is a documented no-op.
        assertEquals(0, repository.evictExpired());
    }
}