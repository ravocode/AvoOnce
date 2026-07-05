package io.github.ravocode.avoonce.core.spi;

import java.util.Optional;

import io.github.ravocode.avoonce.core.domain.IdempotencyRecord;
import io.github.ravocode.avoonce.core.domain.IdempotencyResponse;
import io.github.ravocode.avoonce.core.exception.IdempotencyConflictException;
import io.github.ravocode.avoonce.core.exception.IdempotencyMismatchException;

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
     * Atomically attempts to save a new record with the state STARTED, verifying the request hash.
     * If the record already exists but the request hashes do not match, an {@link IdempotencyMismatchException} is thrown.
     *
     * @param idempotencyKey The unique key for the request.
     * @param requestHash    The hash of the request payload to ensure the key is not reused for a different payload.
     * @return An optional containing the existing record if it's completed, or an empty optional if a new lock was acquired.
     * @throws IdempotencyConflictException if a conflicting, non-expired request is in progress.
     * @throws IdempotencyMismatchException if the key exists but the request hashes do not match.
     */
    default Optional<IdempotencyRecord> acquireOrGet(String idempotencyKey, String requestHash) {
        Optional<IdempotencyRecord> record = acquireOrGet(idempotencyKey);
        record.ifPresent(r -> {
            if (r.getRequestHash() != null && requestHash != null && !r.getRequestHash().equals(requestHash)) {
                throw new IdempotencyMismatchException("Idempotency key reused with different request payload");
            }
        });
        return record;
    }

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

    /**
     * Removes the idempotency record associated with the given key.
     * Useful for administrative cleanup or explicit invalidation.
     *
     * @param idempotencyKey The unique key to remove.
     */
    default void delete(String idempotencyKey) {
        // Default no-op for backward compatibility
    }

    /**
     * Evicts all expired records from the underlying storage.
     * Useful for periodic cleanup in persistent data stores.
     *
     * @return The number of records deleted.
     */
    default int evictExpired() {
        return 0; // Default no-op for self-evicting stores
    }
}