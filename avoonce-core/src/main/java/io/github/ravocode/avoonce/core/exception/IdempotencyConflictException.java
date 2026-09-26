package io.github.ravocode.avoonce.core.exception;

/**
 * Exception thrown when a request is made with an idempotency key that is already in progress.
 * Corresponds to HTTP 409 Conflict.
 */
public class IdempotencyConflictException extends RuntimeException {

    /**
     * Constructs an {@code IdempotencyConflictException} with the given detail message.
     *
     * @param message a description of the conflict; typically includes the conflicting key.
     */
    public IdempotencyConflictException(String message) {
        super(message);
    }
}