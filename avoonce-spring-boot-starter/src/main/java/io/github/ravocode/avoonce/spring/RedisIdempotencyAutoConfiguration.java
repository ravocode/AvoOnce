package io.github.ravocode.avoonce.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import io.github.ravocode.avoonce.core.config.IdempotencyConfig;
import io.github.ravocode.avoonce.core.spi.IdempotencyRepository;
import io.github.ravocode.avoonce.redis.RedisIdempotencyRepository;
import io.github.ravocode.avoonce.redis.RedisOperations;

/**
 * Auto-configuration for Redis-backed distributed idempotency repository.
 */
/**
 * Auto-configuration for Redis-backed idempotency repositories.
 */
@AutoConfiguration
@AutoConfigureAfter({LettuceAdapterAutoConfiguration.class, JedisAdapterAutoConfiguration.class})
@ConditionalOnClass({ RedisIdempotencyRepository.class, RedisOperations.class })
public class RedisIdempotencyAutoConfiguration {

    /**
     * Creates a new auto-configuration instance.
     */
    public RedisIdempotencyAutoConfiguration() {
    }

    /**
     * Auto-wires the Redis repository when RedisOperations bean is present and store property is auto.
     *
     * @param redisOperations the Redis operations client bridge.
     * @param config          the shared idempotency configuration.
     * @return the {@link RedisIdempotencyRepository} bean.
     */
    @Bean
    @ConditionalOnMissingBean(IdempotencyRepository.class)
    @ConditionalOnBean(RedisOperations.class)
    @ConditionalOnProperty(prefix = "avoonce.idempotency", name = "store", havingValue = "auto", matchIfMissing = true)
    public RedisIdempotencyRepository redisAutoRepository(RedisOperations redisOperations,
            IdempotencyConfig config) {
        return new RedisIdempotencyRepository(redisOperations, config);
    }

    /**
     * Wires the Redis repository when {@code avoonce.idempotency.store=redis} is explicitly set.
     *
     * @param redisOperations the Redis operations client bridge.
     * @param config          the shared idempotency configuration.
     * @return the {@link RedisIdempotencyRepository} bean.
     */
    @Bean
    @ConditionalOnMissingBean(IdempotencyRepository.class)
    @ConditionalOnBean(RedisOperations.class)
    @ConditionalOnProperty(prefix = "avoonce.idempotency", name = "store", havingValue = "redis")
    public RedisIdempotencyRepository redisExplicitRepository(RedisOperations redisOperations,
            IdempotencyConfig config) {
        return new RedisIdempotencyRepository(redisOperations, config);
    }
}
