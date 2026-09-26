package io.github.ravocode.avoonce.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import io.github.ravocode.avoonce.redis.LettuceRedisOperations;
import io.github.ravocode.avoonce.redis.RedisOperations;

/**
 * Auto-configuration that adapts a Spring Lettuce RedisClient bean into a {@link RedisOperations} bridge.
 */
/**
 * Auto-configuration that adapts a Spring Lettuce RedisClient bean into a Redis operations bridge.
 */
@AutoConfiguration
@ConditionalOnClass(name = { "io.github.ravocode.avoonce.redis.RedisIdempotencyRepository",
        "io.lettuce.core.RedisClient" })
public class LettuceAdapterAutoConfiguration {

    /**
     * Creates a new auto-configuration instance.
     */
    public LettuceAdapterAutoConfiguration() {
    }

    /**
     * Creates a {@link LettuceRedisOperations} bridge bean when a Lettuce RedisClient bean is present.
     *
     * @param redisClient the Lettuce Redis client bean.
     * @return the {@link LettuceRedisOperations} adapter.
     */
    @Bean
    @ConditionalOnMissingBean(RedisOperations.class)
    @ConditionalOnBean(type = "io.lettuce.core.RedisClient")
    public LettuceRedisOperations lettuceRedisOperations(io.lettuce.core.RedisClient redisClient) {
        return new LettuceRedisOperations(redisClient);
    }
}
