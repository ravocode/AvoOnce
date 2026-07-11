package io.github.ravocode.avoonce.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.ByteArrayCodec;

/**
 * A {@link RedisOperations} adapter for the Lettuce client.
 */
public class LettuceRedisOperations implements RedisOperations {

    private final StatefulRedisConnection<byte[], byte[]> connection;

    /**
     * Creates a new operations adapter using the provided Lettuce client.
     * Note: Lettuce connections are thread-safe and designed to be long-lived.
     * 
     * @param redisClient The configured Lettuce RedisClient.
     */
    public LettuceRedisOperations(RedisClient redisClient) {
        this.connection = redisClient.connect(ByteArrayCodec.INSTANCE);
    }

    /**
     * Creates a new operations adapter using an existing connection.
     * 
     * @param connection An existing StatefulRedisConnection using byte arrays.
     */
    public LettuceRedisOperations(StatefulRedisConnection<byte[], byte[]> connection) {
        this.connection = connection;
    }

    @Override
    public boolean setIfAbsent(byte[] key, byte[] value, long ttlMillis) {
        RedisCommands<byte[], byte[]> commands = connection.sync();
        String result = commands.set(key, value, SetArgs.Builder.nx().px(ttlMillis));
        return "OK".equalsIgnoreCase(result);
    }

    @Override
    public void set(byte[] key, byte[] value, long ttlMillis) {
        RedisCommands<byte[], byte[]> commands = connection.sync();
        commands.set(key, value, SetArgs.Builder.xx().px(ttlMillis));
    }

    @Override
    public byte[] get(byte[] key) {
        RedisCommands<byte[], byte[]> commands = connection.sync();
        return commands.get(key);
    }

    @Override
    public void delete(byte[] key) {
        RedisCommands<byte[], byte[]> commands = connection.sync();
        commands.del(key);
    }
}
