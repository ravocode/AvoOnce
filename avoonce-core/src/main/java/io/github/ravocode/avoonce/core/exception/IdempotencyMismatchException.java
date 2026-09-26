package io.github.ravocode.avoonce.core.exception;

/**
 * Thrown when an existing idempotency key is reused with a different request payload.
 * Corresponds to HTTP 422 Unprocessable Entity.
 */
public class IdempotencyMismatchException extends RuntimeException {

    /**
     * Constructs an {@code IdempotencyMismatchException} with the given detail message.
     *
     * @param message a description of the mismatch; typically indicates key reuse with a different payload.
     */
    public IdempotencyMismatchException(String message) {
        super(message);
    }
}
