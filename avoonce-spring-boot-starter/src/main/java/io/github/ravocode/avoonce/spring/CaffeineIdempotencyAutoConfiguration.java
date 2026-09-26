package io.github.ravocode.avoonce.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import io.github.ravocode.avoonce.caffeine.CaffeineIdempotencyRepository;
import io.github.ravocode.avoonce.core.config.IdempotencyConfig;
import io.github.ravocode.avoonce.core.spi.IdempotencyRepository;

/**
 * Auto-configuration for Caffeine in-memory idempotency repository.
 */
/**
 * Auto-configuration for Caffeine in-memory idempotency repositories.
 */
@AutoConfiguration
@ConditionalOnClass(CaffeineIdempotencyRepository.class)
public class CaffeineIdempotencyAutoConfiguration {

    /**
     * Creates a new auto-configuration instance.
     */
    public CaffeineIdempotencyAutoConfiguration() {
    }

    /**
     * Auto-wires the Caffeine in-memory repository when no other store is present and store property is auto.
     *
     * @param config the shared idempotency configuration.
     * @return the {@link CaffeineIdempotencyRepository} bean.
     */
    @Bean
    @ConditionalOnMissingBean(IdempotencyRepository.class)
    @ConditionalOnMissingClass("io.github.ravocode.avoonce.jdbc.JdbcIdempotencyRepository")
    @ConditionalOnProperty(prefix = "avoonce.idempotency", name = "store", havingValue = "auto", matchIfMissing = true)
    public CaffeineIdempotencyRepository caffeineAutoRepository(IdempotencyConfig config) {
        return new CaffeineIdempotencyRepository(config);
    }

    /**
     * Wires the Caffeine in-memory repository when {@code avoonce.idempotency.store=caffeine} is explicitly set.
     *
     * @param config the shared idempotency configuration.
     * @return the {@link CaffeineIdempotencyRepository} bean.
     */
    @Bean
    @ConditionalOnMissingBean(IdempotencyRepository.class)
    @ConditionalOnProperty(prefix = "avoonce.idempotency", name = "store", havingValue = "caffeine")
    public CaffeineIdempotencyRepository caffeineExplicitRepository(IdempotencyConfig config) {
        return new CaffeineIdempotencyRepository(config);
    }
}
