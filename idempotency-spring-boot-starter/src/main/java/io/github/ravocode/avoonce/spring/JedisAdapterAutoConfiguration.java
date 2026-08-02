package io.github.ravocode.avoonce.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import io.github.ravocode.avoonce.redis.JedisRedisOperations;
import io.github.ravocode.avoonce.redis.RedisOperations;

/**
 * Auto-configuration that adapts a Spring JedisPool bean into a {@link RedisOperations} bridge.
 */
/**
 * Auto-configuration that adapts a Spring JedisPool bean into a Redis operations bridge.
 */
@AutoConfiguration
@ConditionalOnClass(name = { "io.github.ravocode.avoonce.redis.RedisIdempotencyRepository",
        "redis.clients.jedis.JedisPool" })
public class JedisAdapterAutoConfiguration {

    /**
     * Creates a new auto-configuration instance.
     */
    public JedisAdapterAutoConfiguration() {
    }

    /**
     * Creates a {@link JedisRedisOperations} bridge bean when a JedisPool bean is present.
     *
     * @param jedisPool the Jedis connection pool bean.
     * @return the {@link JedisRedisOperations} adapter.
     */
    @Bean
    @ConditionalOnMissingBean(RedisOperations.class)
    @ConditionalOnBean(type = "redis.clients.jedis.JedisPool")
    public JedisRedisOperations jedisRedisOperations(redis.clients.jedis.JedisPool jedisPool) {
        return new JedisRedisOperations(jedisPool);
    }
}
