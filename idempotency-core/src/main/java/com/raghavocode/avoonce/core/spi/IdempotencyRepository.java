package com.raghavocode.avoonce.core.spi;

import com.raghavocode.avoonce.core.domain.IdempotencyRecord;
import com.raghavocode.avoonce.core.domain.IdempotencyResponse;
import com.raghavocode.avoonce.core.exception.IdempotencyConflictException;

import java.util.Optional;

/**
 * Service Provider Interface for pluggable infrastructure storage.
 */
public interface IdempotencyRepository {

    /**
     * Atomically attempts to save a new record with the state STARTED.
     * If the record already exists and is in a COMPLETED state, it is returned.
     * If a record is in a STARTED state and has not expired, this method should throw an {@link IdempotencyConflictException}.
     * If a record is in a STARTED state and has expired, a new record is created.
     *
     * @param idempotencyKey The unique key for the request.
     * @return An optional containing the existing record if it's completed, or an empty optional if a new lock was acquired.
     * @throws IdempotencyConflictException if a conflicting, non-expired request is in progress.
     */
    Optional<IdempotencyRecord> acquireOrGet(String idempotencyKey);

    /**
     * Updates an existing record with a successful payload and sets the state to COMPLETED.
     */
    void saveSuccess(String idempotencyKey, IdempotencyResponse response);

    /**
     * Clears the active lock and updates the state to FAILED.
     * Usually implies the request can be retried by the client.
     */
    void saveFailure(String idempotencyKey, String errorMessage);

    /**
     * Pure read-only check of the current record.
     *
     * @return Optional containing the record if found, empty otherwise.
     */
    Optional<IdempotencyRecord> get(String idempotencyKey);
}