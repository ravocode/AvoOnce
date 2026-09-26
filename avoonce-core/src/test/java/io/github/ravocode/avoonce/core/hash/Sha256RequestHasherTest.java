package io.github.ravocode.avoonce.core.hash;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Sha256RequestHasherTest {

    private final Sha256RequestHasher hasher = new Sha256RequestHasher();

    @Test
    void hash_shouldReturnNonNullNonEmptyString() {
        String result = hasher.hash("hello".getBytes());
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void hash_shouldProduceLowercaseHex64Chars() {
        // SHA-256 produces 32 bytes → 64 hex chars
        String result = hasher.hash("any payload".getBytes());
        assertEquals(64, result.length());
        assertTrue(result.matches("[0-9a-f]+"), "Expected lowercase hex string, got: " + result);
    }

    @Test
    void hash_shouldBeDeterministic() {
        byte[] body = "idempotent request body".getBytes();
        assertEquals(hasher.hash(body), hasher.hash(body));
    }

    @Test
    void hash_shouldDifferForDifferentInputs() {
        assertNotEquals(hasher.hash("payload A".getBytes()), hasher.hash("payload B".getBytes()));
    }

    @Test
    void hash_knownVector() {
        // echo -n "" | sha256sum => e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        String result = hasher.hash(new byte[0]);
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", result);
    }
}
