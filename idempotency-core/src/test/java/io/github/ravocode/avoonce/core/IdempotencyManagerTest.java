package io.github.ravocode.avoonce.core;

import io.github.ravocode.avoonce.core.domain.IdempotencyRecord;
import io.github.ravocode.avoonce.core.domain.IdempotencyResponse;
import io.github.ravocode.avoonce.core.domain.IdempotencyStatus;
import io.github.ravocode.avoonce.core.hash.RequestHasher;
import io.github.ravocode.avoonce.core.spi.IdempotencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdempotencyManagerTest {

    private IdempotencyRepository repository;
    private IdempotencyManager manager;

    @BeforeEach
    void setUp() {
        repository = mock(IdempotencyRepository.class);
        manager = new IdempotencyManager(repository);
    }

    // -------------------------------------------------------------------------
    // execute(key, action) — no-hash path
    // -------------------------------------------------------------------------

    @Test
    void execute_whenRecordExists_returnsCachedResponse() throws Exception {
        String key = "test-key-123";
        IdempotencyResponse cachedResponse = new IdempotencyResponse(200, null, new byte[0]);
        IdempotencyRecord record = new IdempotencyRecord(key, IdempotencyStatus.COMPLETED, cachedResponse, null);

        when(repository.acquireOrGet(key)).thenReturn(Optional.of(record));

        AtomicInteger actionCallCount = new AtomicInteger(0);
        IdempotencyResponse result = manager.execute(key, () -> {
            actionCallCount.incrementAndGet();
            return new IdempotencyResponse(201, null, null);
        });

        assertEquals(cachedResponse, result);
        assertEquals(0, actionCallCount.get(), "Action must not be called on cache hit");
        verify(repository, never()).saveSuccess(anyString(), any());
        verify(repository, never()).saveFailure(anyString(), anyString());
    }

    @Test
    void execute_whenNoRecordExists_executesActionAndSavesSuccess() throws Exception {
        String key = "test-key-123";
        when(repository.acquireOrGet(key)).thenReturn(Optional.empty());

        IdempotencyResponse newResponse = new IdempotencyResponse(201, null, "Success".getBytes());

        IdempotencyResponse result = manager.execute(key, () -> newResponse);

        assertEquals(newResponse, result);
        verify(repository, times(1)).saveSuccess(key, newResponse);
        verify(repository, never()).saveFailure(anyString(), anyString());
    }

    @Test
    void execute_whenActionThrowsException_savesFailureAndRethrows() throws Exception {
        String key = "test-key-123";
        when(repository.acquireOrGet(key)).thenReturn(Optional.empty());

        RuntimeException exception = new RuntimeException("Underlying business logic failed");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> manager.execute(key, () -> { throw exception; }));
        assertEquals("Underlying business logic failed", thrown.getMessage());

        verify(repository, times(1)).saveFailure(key, "Underlying business logic failed");
        verify(repository, never()).saveSuccess(anyString(), any());
    }

    // -------------------------------------------------------------------------
    // execute(key, byte[], action) — auto-hash path
    // -------------------------------------------------------------------------

    @Test
    void execute_withBody_usesHasherAndPassesHashToRepository() throws Exception {
        String key = "key-abc";
        byte[] body = "request body".getBytes();

        RequestHasher stubbedHasher = b -> "fixed-hash";
        IdempotencyManager managerWithStub = new IdempotencyManager(repository, stubbedHasher);

        when(repository.acquireOrGet(key, "fixed-hash")).thenReturn(Optional.empty());

        IdempotencyResponse response = new IdempotencyResponse(200, null, null);
        IdempotencyResponse result = managerWithStub.execute(key, body, () -> response);

        assertEquals(response, result);
        // Must use the hash-aware overload, not the plain one
        verify(repository, times(1)).acquireOrGet(key, "fixed-hash");
        verify(repository, never()).acquireOrGet(key);
    }

    @Test
    void execute_withNullBody_treatsAsNoHash() throws Exception {
        String key = "key-abc";
        when(repository.acquireOrGet(key)).thenReturn(Optional.empty());

        manager.execute(key, (byte[]) null, () -> new IdempotencyResponse(200, null, null));

        verify(repository, times(1)).acquireOrGet(key);
        verify(repository, never()).acquireOrGet(anyString(), anyString());
    }

    @Test
    void execute_withBody_replaysOnCacheHit() throws Exception {
        String key = "key-abc";
        byte[] body = "payload".getBytes();

        RequestHasher stubbedHasher = b -> "hash-xyz";
        IdempotencyManager managerWithStub = new IdempotencyManager(repository, stubbedHasher);

        IdempotencyResponse cached = new IdempotencyResponse(200, null, null);
        IdempotencyRecord record = new IdempotencyRecord(key, IdempotencyStatus.COMPLETED, cached, null, "hash-xyz");
        when(repository.acquireOrGet(key, "hash-xyz")).thenReturn(Optional.of(record));

        AtomicInteger callCount = new AtomicInteger(0);
        IdempotencyResponse result = managerWithStub.execute(key, body, () -> {
            callCount.incrementAndGet();
            return new IdempotencyResponse(201, null, null);
        });

        assertEquals(cached, result);
        assertEquals(0, callCount.get(), "Action must not be called on cache hit");
    }

    // -------------------------------------------------------------------------
    // execute(key, String hash, action) — pre-computed hash path
    // -------------------------------------------------------------------------

    @Test
    void execute_withPrecomputedHash_passesHashDirectlyToRepository() throws Exception {
        String key = "key-abc";
        String hash = "pre-computed-hash";

        when(repository.acquireOrGet(key, hash)).thenReturn(Optional.empty());

        IdempotencyResponse response = new IdempotencyResponse(200, null, null);
        manager.execute(key, hash, () -> response);

        verify(repository, times(1)).acquireOrGet(eq(key), eq(hash));
        verify(repository, never()).acquireOrGet(key);
    }

    @Test
    void execute_withNullStringHash_treatsAsNoHash() throws Exception {
        String key = "key-abc";
        when(repository.acquireOrGet(key)).thenReturn(Optional.empty());

        manager.execute(key, (String) null, () -> new IdempotencyResponse(200, null, null));

        verify(repository, times(1)).acquireOrGet(key);
        verify(repository, never()).acquireOrGet(anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // Custom RequestHasher wiring
    // -------------------------------------------------------------------------

    @Test
    void execute_withCustomHasher_usesProvidedImplementation() throws Exception {
        String key = "key-custom";
        byte[] body = "data".getBytes();

        RequestHasher customHasher = b -> "CUSTOM:" + new String(b);
        IdempotencyManager customManager = new IdempotencyManager(repository, customHasher);

        String expectedHash = "CUSTOM:data";
        when(repository.acquireOrGet(key, expectedHash)).thenReturn(Optional.empty());

        customManager.execute(key, body, () -> new IdempotencyResponse(200, null, null));

        verify(repository, times(1)).acquireOrGet(key, expectedHash);
    }
}
