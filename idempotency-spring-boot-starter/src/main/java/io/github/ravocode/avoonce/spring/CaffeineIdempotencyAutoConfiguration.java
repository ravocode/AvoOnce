package io.github.ravocode.avoonce.spring;

import io.github.ravocode.avoonce.caffeine.CaffeineIdempotencyRepository;
import io.github.ravocode.avoonce.core.config.IdempotencyConfig;
import io.github.ravocode.avoonce.core.spi.IdempotencyRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(CaffeineIdempotencyRepository.class)
public class CaffeineIdempotencyAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(IdempotencyRepository.class)
    @ConditionalOnMissingClass("io.github.ravocode.avoonce.jdbc.JdbcIdempotencyRepository")
    @ConditionalOnProperty(prefix = "avoonce.idempotency", name = "store", havingValue = "auto", matchIfMissing = true)
    public CaffeineIdempotencyRepository caffeineAutoRepository(IdempotencyConfig config) {
        return new CaffeineIdempotencyRepository(config);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotencyRepository.class)
    @ConditionalOnProperty(prefix = "avoonce.idempotency", name = "store", havingValue = "caffeine")
    public CaffeineIdempotencyRepository caffeineExplicitRepository(IdempotencyConfig config) {
        return new CaffeineIdempotencyRepository(config);
    }
}
