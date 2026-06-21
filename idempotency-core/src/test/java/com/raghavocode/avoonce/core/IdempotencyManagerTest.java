package com.raghavocode.avoonce.core;

import com.raghavocode.avoonce.core.domain.IdempotencyRecord;
import com.raghavocode.avoonce.core.domain.IdempotencyResponse;
import com.raghavocode.avoonce.core.domain.IdempotencyStatus;
import com.raghavocode.avoonce.core.spi.IdempotencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyManagerTest {

    private IdempotencyRepository repository;
    private IdempotencyManager manager;

    @BeforeEach
    void setUp() {
        repository = mock(IdempotencyRepository.class);
        manager = new IdempotencyManager(repository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_whenRecordExists_returnsCachedResponse() throws Exception {
        // Arrange
        String key = "test-key-123";
        IdempotencyResponse cachedResponse = new IdempotencyResponse(200, null, new byte[0]);
        IdempotencyRecord record = new IdempotencyRecord(key, IdempotencyStatus.COMPLETED, cachedResponse, null);
        
        when(repository.acquireOrGet(key)).thenReturn(Optional.of(record));

        Callable<IdempotencyResponse> action = mock(Callable.class);

        // Act
        IdempotencyResponse result = manager.execute(key, action);

        // Assert
        assertEquals(cachedResponse, result);
        verify(action, never()).call();
        verify(repository, never()).saveSuccess(anyString(), any());
        verify(repository, never()).saveFailure(anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_whenNoRecordExists_executesActionAndSavesSuccess() throws Exception {
        // Arrange
        String key = "test-key-123";
        when(repository.acquireOrGet(key)).thenReturn(Optional.empty());

        IdempotencyResponse newResponse = new IdempotencyResponse(201, null, "Success".getBytes());
        Callable<IdempotencyResponse> action = mock(Callable.class);
        when(action.call()).thenReturn(newResponse);

        // Act
        IdempotencyResponse result = manager.execute(key, action);

        // Assert
        assertEquals(newResponse, result);
        verify(action, times(1)).call();
        verify(repository, times(1)).saveSuccess(key, newResponse);
        verify(repository, never()).saveFailure(anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_whenActionThrowsException_savesFailureAndRethrows() throws Exception {
        // Arrange
        String key = "test-key-123";
        when(repository.acquireOrGet(key)).thenReturn(Optional.empty());

        Callable<IdempotencyResponse> action = mock(Callable.class);
        RuntimeException exception = new RuntimeException("Underlying business logic failed");
        when(action.call()).thenThrow(exception);

        // Act & Assert
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> manager.execute(key, action));
        assertEquals("Underlying business logic failed", thrown.getMessage());

        // Verify the failure state was tracked in the repository
        verify(action, times(1)).call();
        verify(repository, times(1)).saveFailure(key, "Underlying business logic failed");
        verify(repository, never()).saveSuccess(anyString(), any());
    }
}
