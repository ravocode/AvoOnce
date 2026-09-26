package io.github.ravocode.avoonce.core.hash;

/**
 * Strategy interface for computing a deterministic hash of an HTTP request body.
 *
 * <p>Implementations must be deterministic and thread-safe. The returned string
 * is stored alongside the idempotency record and compared on every subsequent
 * request with the same key to detect accidental key reuse with a different payload.
 *
 * <p>The default implementation is {@link Sha256RequestHasher}.
 */
@FunctionalInterface
public interface RequestHasher {

    /**
     * Computes a hash of the given request body bytes.
     *
     * @param requestBody the raw bytes of the HTTP request body; must not be {@code null}.
     * @return a non-null, non-empty hex (or otherwise encoded) string representing the hash.
     */
    String hash(byte[] requestBody);
}
