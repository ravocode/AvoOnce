package io.github.ravocode.avoonce.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

/**
 * A {@link RedisOperations} adapter for the Jedis client.
 */
public class JedisRedisOperations implements RedisOperations {

    private final JedisPool jedisPool;

    public JedisRedisOperations(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    public boolean setIfAbsent(byte[] key, byte[] value, long ttlMillis) {
        try (Jedis jedis = jedisPool.getResource()) {
            String result = jedis.set(key, value, SetParams.setParams().nx().px(ttlMillis));
            return "OK".equalsIgnoreCase(result);
        }
    }

    @Override
    public void set(byte[] key, byte[] value, long ttlMillis) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(key, value, SetParams.setParams().xx().px(ttlMillis));
        }
    }

    @Override
    public byte[] get(byte[] key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(key);
        }
    }

    @Override
    public void delete(byte[] key) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key);
        }
    }
}
