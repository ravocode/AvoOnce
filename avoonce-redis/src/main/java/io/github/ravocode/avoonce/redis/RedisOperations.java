package io.github.ravocode.avoonce.redis;

/**
 * Service Provider Interface for basic Redis operations.
 * Allows decoupling AvoOnce from any specific Redis client (e.g. Jedis, Lettuce).
 */
public interface RedisOperations {

    /**
     * Atomically sets a key to a value with a time-to-live only if the key does not already exist.
     * Equivalent to Redis command: {@code SET key value NX PX ttlMillis}
     *
     * @param key       The key to set.
     * @param value     The value to store.
     * @param ttlMillis Time-to-live in milliseconds.
     * @return {@code true} if the key was set, {@code false} if it already existed.
     */
    boolean setIfAbsent(byte[] key, byte[] value, long ttlMillis);

    /**
     * Updates an existing key with a new value and extends its time-to-live.
     * Equivalent to Redis command: {@code SET key value XX PX ttlMillis}
     *
     * @param key       The key to update.
     * @param value     The new value to store.
     * @param ttlMillis Time-to-live in milliseconds.
     */
    void set(byte[] key, byte[] value, long ttlMillis);

    /**
     * Retrieves the value associated with a key.
     * Equivalent to Redis command: {@code GET key}
     *
     * @param key The key to retrieve.
     * @return The value, or {@code null} if the key does not exist.
     */
    byte[] get(byte[] key);

    /**
     * Deletes the specified key.
     * Equivalent to Redis command: {@code DEL key}
     *
     * @param key The key to delete.
     */
    void delete(byte[] key);
}
