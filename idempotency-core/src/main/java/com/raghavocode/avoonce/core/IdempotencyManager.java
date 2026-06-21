package com.raghavocode.avoonce.core;

import java.util.Optional;
import java.util.concurrent.Callable;

import com.raghavocode.avoonce.core.domain.IdempotencyRecord;
import com.raghavocode.avoonce.core.domain.IdempotencyResponse;
import com.raghavocode.avoonce.core.exception.IdempotencyConflictException;
import com.raghavocode.avoonce.core.exception.IdempotencyMismatchException;
import com.raghavocode.avoonce.core.hash.RequestHasher;
import com.raghavocode.avoonce.core.hash.Sha256RequestHasher;
import com.raghavocode.avoonce.core.spi.IdempotencyRepository;

/**
 * Core state machine that enforces exactly-once processing rules.
 *
 * <p>Three execution modes are supported:
 * <ol>
 *   <li><b>No hash</b> — {@link #execute(String, Callable)}: idempotency by key only.</li>
 *   <li><b>Auto hash</b> — {@link #execute(String, byte[], Callable)}: the library hashes
 *       the raw request body using the configured {@link RequestHasher} (SHA-256 by default).</li>
 *   <li><b>Pre-computed hash</b> — {@link #execute(String, String, Callable)}: the caller
 *       supplies an already-computed hash string (e.g. from a signed request header).</li>
 * </ol>
 */
public class IdempotencyManager {

    private final IdempotencyRepository repository;
    private final RequestHasher hasher;

    /**
     * Constructs an {@code IdempotencyManager} using the default {@link Sha256RequestHasher}.
     *
     * @param repository the backing store for idempotency records.
     */
    public IdempotencyManager(final IdempotencyRepository repository) {
        this(repository, new Sha256RequestHasher());
    }

    /**
     * Constructs an {@code IdempotencyManager} with a custom {@link RequestHasher}.
     *
     * @param repository the backing store for idempotency records.
     * @param hasher     the hashing strategy used when a raw request body is provided.
     */
    public IdempotencyManager(final IdempotencyRepository repository, final RequestHasher hasher) {
        this.repository = repository;
        this.hasher = hasher;
    }

    /**
     * Executes the given action idempotently based on the provided key alone.
     * No payload validation is performed — the same key always replays the cached response.
     *
     * @param idempotencyKey The unique key identifying the idempotent request.
     * @param action         The business logic to execute if this is the first attempt.
     * @return The cached response if previously completed, or the newly generated response.
     * @throws IdempotencyConflictException If another request with the same key is currently processing.
     * @throws Exception                    If the underlying action throws an exception.
     */
    public IdempotencyResponse execute(final String idempotencyKey,
                                       final Callable<IdempotencyResponse> action) throws Exception {
        return executeInternal(idempotencyKey, null, action);
    }

    /**
     * Executes the given action idempotently, hashing the raw {@code requestBody} using the
     * configured {@link RequestHasher} to detect key reuse with a different payload.
     *
     * <p>This is the recommended overload for HTTP filters and interceptors that already
     * buffer the request body for replay purposes.
     *
     * @param idempotencyKey The unique key identifying the idempotent request.
     * @param requestBody    The raw bytes of the HTTP request body to hash and validate.
     * @param action         The business logic to execute if this is the first attempt.
     * @return The cached response if previously completed, or the newly generated response.
     * @throws IdempotencyConflictException If another request with the same key is currently processing.
     * @throws IdempotencyMismatchException If the request body hash doesn't match the stored hash.
     * @throws Exception                    If the underlying action throws an exception.
     */
    public IdempotencyResponse execute(final String idempotencyKey,
                                       final byte[] requestBody,
                                       final Callable<IdempotencyResponse> action) throws Exception {
        String requestHash = (requestBody != null) ? hasher.hash(requestBody) : null;
        return executeInternal(idempotencyKey, requestHash, action);
    }

    /**
     * Executes the given action idempotently using a caller-supplied pre-computed hash string.
     * Use this when the hash is already available (e.g. extracted from a signed request header).
     *
     * @param idempotencyKey The unique key identifying the idempotent request.
     * @param requestHash    The pre-computed hash of the request payload; may be {@code null}
     *                       to skip payload validation.
     * @param action         The business logic to execute if this is the first attempt.
     * @return The cached response if previously completed, or the newly generated response.
     * @throws IdempotencyConflictException If another request with the same key is currently processing.
     * @throws IdempotencyMismatchException If the request hash doesn't match the stored hash.
     * @throws Exception                    If the underlying action throws an exception.
     */
    public IdempotencyResponse execute(final String idempotencyKey,
                                       final String requestHash,
                                       final Callable<IdempotencyResponse> action) throws Exception {
        return executeInternal(idempotencyKey, requestHash, action);
    }

    // -------------------------------------------------------------------------
    // Private implementation
    // -------------------------------------------------------------------------

    private IdempotencyResponse executeInternal(final String idempotencyKey,
                                                final String requestHash,
                                                final Callable<IdempotencyResponse> action) throws Exception {
        // 1. Attempt to acquire a lock or get an already COMPLETED record.
        Optional<IdempotencyRecord> existingRecord = (requestHash == null)
                ? repository.acquireOrGet(idempotencyKey)
                : repository.acquireOrGet(idempotencyKey, requestHash);

        // 2. If present, the record was previously COMPLETED — replay the cached response.
        if (existingRecord.isPresent()) {
            return existingRecord.get().getResponse();
        }

        // 3. Lock acquired (state is now STARTED). Execute the action.
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
