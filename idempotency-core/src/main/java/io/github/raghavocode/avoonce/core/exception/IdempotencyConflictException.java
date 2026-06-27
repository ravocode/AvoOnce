package io.github.raghavocode.avoonce.core.exception;

/**
 * Exception thrown when a request is made with an idempotency key that is already in progress.
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}