package io.github.raghavocode.avoonce.core.domain;

/**
 * Represents an idempotency log entry securely stored in the repository.
 */
public class IdempotencyRecord {
    private final String idempotencyKey;
    private final IdempotencyStatus status;
    private final IdempotencyResponse response;
    private final Long expiresAt;
    private final String requestHash;

    public IdempotencyRecord(String idempotencyKey, IdempotencyStatus status, IdempotencyResponse response, Long expiresAt) {
        this(idempotencyKey, status, response, expiresAt, null);
    }

    public IdempotencyRecord(String idempotencyKey, IdempotencyStatus status, IdempotencyResponse response, Long expiresAt, String requestHash) {
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.response = response;
        this.expiresAt = expiresAt;
        this.requestHash = requestHash;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public IdempotencyStatus getStatus() {
        return status;
    }

    public IdempotencyResponse getResponse() {
        return response;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public String getRequestHash() {
        return requestHash;
    }
}