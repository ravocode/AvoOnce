package io.github.ravocode.avoonce.spring;

import io.github.ravocode.avoonce.redis.LettuceRedisOperations;
import io.github.ravocode.avoonce.redis.RedisOperations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = { "io.github.ravocode.avoonce.redis.RedisIdempotencyRepository",
        "io.lettuce.core.RedisClient" })
public class LettuceAdapterAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(RedisOperations.class)
    @ConditionalOnBean(type = "io.lettuce.core.RedisClient")
    public LettuceRedisOperations lettuceRedisOperations(io.lettuce.core.RedisClient redisClient) {
        return new LettuceRedisOperations(redisClient);
    }
}
