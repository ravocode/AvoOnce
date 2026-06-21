package com.raghavocode.avoonce.core;

import java.util.Optional;
import java.util.concurrent.Callable;

import com.raghavocode.avoonce.core.domain.IdempotencyRecord;
import com.raghavocode.avoonce.core.domain.IdempotencyResponse;
import com.raghavocode.avoonce.core.exception.IdempotencyConflictException;
import com.raghavocode.avoonce.core.exception.IdempotencyMismatchException;
import com.raghavocode.avoonce.core.spi.IdempotencyRepository;

/**
 * Core state machine that enforces exactly-once processing rules.
 */
public class IdempotencyManager {

    private final IdempotencyRepository repository;

    public IdempotencyManager(IdempotencyRepository repository) {
        this.repository = repository;
    }

    /**
     * Executes the given action idempotently based on the provided key.
     *
     * @param idempotencyKey The unique key identifying the idempotent request.
     * @param action         The business logic to execute if this is the first attempt.
     * @return The cached response if previously completed, or the newly generated response.
     * @throws IdempotencyConflictException If another request with the same key is currently processing.
     * @throws Exception                    If the underlying action throws an exception.
     */
    public IdempotencyResponse execute(String idempotencyKey, Callable<IdempotencyResponse> action) throws Exception {
        return execute(idempotencyKey, null, action);
    }

    /**
     * Executes the given action idempotently based on the provided key and request hash.
     *
     * @param idempotencyKey The unique key identifying the idempotent request.
     * @param requestHash    The hash of the request payload to ensure the key is not reused for a different payload.
     * @param action         The business logic to execute if this is the first attempt.
     * @return The cached response if previously completed, or the newly generated response.
     * @throws IdempotencyConflictException If another request with the same key is currently processing.
     * @throws IdempotencyMismatchException If the request hash doesn't match the existing record.
     * @throws Exception                    If the underlying action throws an exception.
     */
    public IdempotencyResponse execute(String idempotencyKey, String requestHash, Callable<IdempotencyResponse> action) throws Exception {
        // 1. Attempt to acquire a lock or get an already completed record
        Optional<IdempotencyRecord> existingRecord;
        if (requestHash == null) {
            existingRecord = repository.acquireOrGet(idempotencyKey);
        } else {
            existingRecord = repository.acquireOrGet(idempotencyKey, requestHash);
        }

        // 2. If a record is present, it means it was previously COMPLETED.
        if (existingRecord.isPresent()) {
            return existingRecord.get().getResponse();
        }

        // 3. We successfully acquired the lock (state is now STARTED). Execute the action.
        try {
            IdempotencyResponse response = action.call();
            repository.saveSuccess(idempotencyKey, response);
            return response;
        } catch (Exception e) {
            repository.saveFailure(idempotencyKey, e.getMessage());
            throw e;
        }
    }
}
