package io.github.ravocode.avoonce.core.domain;

/**
 * Immutable value object representing a single idempotency record stored in the repository.
 *
 * <p>A record captures the full lifecycle of an idempotent request:
 * from the initial lock acquisition ({@link IdempotencyStatus#STARTED}),
 * through successful completion ({@link IdempotencyStatus#COMPLETED}),
 * or failure ({@link IdempotencyStatus#FAILED}).
 *
 * <p>Repository implementations use this record to enforce exactly-once
 * processing guarantees and replay cached responses on retry.
 */
public class IdempotencyRecord {

    /** The unique idempotency key identifying this request. */
    private final String idempotencyKey;

    /** The current lifecycle status of this idempotency record. */
    private final IdempotencyStatus status;

    /** The cached HTTP response to replay on subsequent requests; may be {@code null} when STARTED or FAILED. */
    private final IdempotencyResponse response;

    /** The epoch-millisecond timestamp at which this record expires; may be {@code null} for COMPLETED records. */
    private final Long expiresAt;

    /** SHA-256 hash of the original request body; used to detect key reuse with a different payload. */
    private final String requestHash;

    /**
     * Constructs an {@code IdempotencyRecord} without a request hash (hash validation disabled).
     *
     * @param idempotencyKey the unique idempotency key.
     * @param status         the current lifecycle status.
     * @param response       the cached response, or {@code null} if not yet completed.
     * @param expiresAt      the epoch-millisecond expiry timestamp, or {@code null} for COMPLETED records.
     */
    public IdempotencyRecord(String idempotencyKey, IdempotencyStatus status, IdempotencyResponse response, Long expiresAt) {
        this(idempotencyKey, status, response, expiresAt, null);
    }

    /**
     * Constructs an {@code IdempotencyRecord} with full fields including a request hash.
     *
     * @param idempotencyKey the unique idempotency key.
     * @param status         the current lifecycle status.
     * @param response       the cached response, or {@code null} if not yet completed.
     * @param expiresAt      the epoch-millisecond expiry timestamp, or {@code null} for COMPLETED records.
     * @param requestHash    the SHA-256 hash of the original request body; may be {@code null} to skip validation.
     */
    public IdempotencyRecord(String idempotencyKey, IdempotencyStatus status, IdempotencyResponse response, Long expiresAt, String requestHash) {
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.response = response;
        this.expiresAt = expiresAt;
        this.requestHash = requestHash;
    }

    /**
     * Returns the unique idempotency key.
     *
     * @return the idempotency key string.
     */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /**
     * Returns the current lifecycle status of this record.
     *
     * @return the {@link IdempotencyStatus}.
     */
    public IdempotencyStatus getStatus() {
        return status;
    }

    /**
     * Returns the cached HTTP response for replay, or {@code null} if the request has not completed successfully.
     *
     * @return the {@link IdempotencyResponse}, or {@code null}.
     */
    public IdempotencyResponse getResponse() {
        return response;
    }

    /**
     * Returns the epoch-millisecond timestamp at which the STARTED lock expires.
     *
     * @return the expiry timestamp in milliseconds, or {@code null} for COMPLETED records.
     */
    public Long getExpiresAt() {
        return expiresAt;
    }

    /**
     * Returns the SHA-256 hash of the original request body used for payload mismatch detection.
     *
     * @return the hex-encoded SHA-256 hash, or {@code null} if hash validation is disabled.
     */
    public String getRequestHash() {
        return requestHash;
    }
}