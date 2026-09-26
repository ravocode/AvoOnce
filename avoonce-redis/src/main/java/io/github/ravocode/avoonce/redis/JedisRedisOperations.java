package io.github.ravocode.avoonce.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

/**
 * A {@link RedisOperations} adapter for the Jedis client.
 */
public class JedisRedisOperations implements RedisOperations {

    /** The connection pool used for Jedis resource acquisition. */
    private final JedisPool jedisPool;

    /**
     * Constructs a {@code JedisRedisOperations} instance using the given
     * {@link JedisPool}.
     *
     * @param jedisPool the Jedis connection pool.
     */
    public JedisRedisOperations(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    /**
     * {@inheritDoc}
     */
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean setIfAbsent(byte[] key, byte[] value, long ttlMillis) {
        try (Jedis jedis = jedisPool.getResource()) {
            String result = jedis.set(key, value, SetParams.setParams().nx().px(ttlMillis));
            return "OK".equalsIgnoreCase(result);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void set(byte[] key, byte[] value, long ttlMillis) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(key, value, SetParams.setParams().xx().px(ttlMillis));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] get(byte[] key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(key);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(byte[] key) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key);
        }
    }
}
