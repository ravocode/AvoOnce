package io.github.ravocode.avoonce.core.exception;

/**
 * Thrown when an existing idempotency key is used with a different request payload.
 */
public class IdempotencyMismatchException extends RuntimeException {
    public IdempotencyMismatchException(String message) {
        super(message);
    }
}
