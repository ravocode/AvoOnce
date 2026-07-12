package io.github.ravocode.avoonce.spring;

import io.github.ravocode.avoonce.core.config.IdempotencyConfig;
import io.github.ravocode.avoonce.core.spi.IdempotencyRepository;
import io.github.ravocode.avoonce.redis.RedisIdempotencyRepository;
import io.github.ravocode.avoonce.redis.RedisOperations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter({LettuceAdapterAutoConfiguration.class, JedisAdapterAutoConfiguration.class})
@ConditionalOnClass({ RedisIdempotencyRepository.class, RedisOperations.class })
public class RedisIdempotencyAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(IdempotencyRepository.class)
    @ConditionalOnBean(RedisOperations.class)
    @ConditionalOnProperty(prefix = "avoonce.idempotency", name = "store", havingValue = "auto", matchIfMissing = true)
    public RedisIdempotencyRepository redisAutoRepository(RedisOperations redisOperations,
            IdempotencyConfig config) {
        return new RedisIdempotencyRepository(redisOperations, config);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotencyRepository.class)
    @ConditionalOnBean(RedisOperations.class)
    @ConditionalOnProperty(prefix = "avoonce.idempotency", name = "store", havingValue = "redis")
    public RedisIdempotencyRepository redisExplicitRepository(RedisOperations redisOperations,
            IdempotencyConfig config) {
        return new RedisIdempotencyRepository(redisOperations, config);
    }
}
