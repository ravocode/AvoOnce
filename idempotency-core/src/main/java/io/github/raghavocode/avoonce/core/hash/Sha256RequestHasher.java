package io.github.raghavocode.avoonce.core.hash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Default {@link RequestHasher} that produces a lowercase hex-encoded SHA-256 digest.
 *
 * <p>SHA-256 is collision-resistant and widely available on all JVM distributions
 * via {@link java.security.MessageDigest}, requiring no additional dependencies.
 *
 * <p>This class is stateless and thread-safe; the same instance may be shared
 * across the application.
 */
public class Sha256RequestHasher implements RequestHasher {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    @Override
    public String hash(final byte[] requestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(requestBody);
            return toHex(encoded);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java SE spec — this should never happen.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String toHex(final byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            result[i * 2]     = HEX_CHARS[v >>> 4];
            result[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(result);
    }
}
