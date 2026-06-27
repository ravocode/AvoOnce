package io.github.raghavocode.avoonce.spring;

import io.github.raghavocode.avoonce.caffeine.CaffeineIdempotencyRepository;
import io.github.raghavocode.avoonce.core.IdempotencyManager;
import io.github.raghavocode.avoonce.core.config.IdempotencyConfig;
import io.github.raghavocode.avoonce.core.spi.IdempotencyRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@ConditionalOnClass(IdempotencyManager.class)
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyConfig idempotencyConfig(IdempotencyProperties properties) {
        return new IdempotencyConfig(
                properties.getTtl(),
                properties.getTtlUnit(),
                properties.getLockTimeout(),
                properties.getLockTimeoutUnit()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(CaffeineIdempotencyRepository.class)
    public IdempotencyRepository caffeineIdempotencyRepository(IdempotencyConfig config) {
        return new CaffeineIdempotencyRepository(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyManager idempotencyManager(IdempotencyRepository repository) {
        return new IdempotencyManager(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "avoonce.idempotency.filter", name = "enabled", matchIfMissing = true)
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration(
            IdempotencyManager manager, IdempotencyProperties properties) {
        FilterRegistrationBean<IdempotencyFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new IdempotencyFilter(manager, properties));
        registrationBean.addUrlPatterns("/*");
        // Execute early in the chain
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registrationBean;
    }
}
