package io.github.ravocode.avoonce.core.domain;

/**
 * Represents the lifecycle state of an idempotency request.
 */
public enum IdempotencyStatus {
    /**
     * The request is currently being processed by the system.
     */
    STARTED,
    /**
     * The request has been successfully processed, and a response is cached.
     */
    COMPLETED,
    /**
     * The request encountered an error or failed to process. 
     * Clients are usually allowed to retry in this state.
     */
    FAILED
}