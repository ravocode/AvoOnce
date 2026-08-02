package io.github.ravocode.avoonce.spring;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import io.github.ravocode.avoonce.core.IdempotencyManager;
import io.github.ravocode.avoonce.core.config.IdempotencyConfig;
import io.github.ravocode.avoonce.core.spi.IdempotencyRepository;

/**
 * Spring Boot auto-configuration for the AvoOnce idempotency library.
 *
 * <h2>Store selection</h2>
 * <ul>
 * <li>If only {@code idempotency-caffeine} is on the classpath → Caffeine is
 * wired automatically.</li>
 * <li>If only {@code idempotency-jdbc} is on the classpath and a
 * {@link DataSource} bean exists → JDBC is wired automatically.</li>
 * <li>If only {@code idempotency-redis} is on the classpath and a
 * {@code RedisOperations} bean exists → Redis is wired automatically.</li>
 * <li>If <b>multiple</b> are on the classpath → startup fails with a clear error
 * unless the user explicitly sets
 * {@code avoonce.idempotency.store=caffeine},
 * {@code avoonce.idempotency.store=jdbc}, or
 * {@code avoonce.idempotency.store=redis}.</li>
 * </ul>
 */
/**
 * Spring Boot auto-configuration for the AvoOnce idempotency library.
 */
@AutoConfiguration
@AutoConfigureAfter({CaffeineIdempotencyAutoConfiguration.class, JdbcIdempotencyAutoConfiguration.class, RedisIdempotencyAutoConfiguration.class})
@ConditionalOnClass(IdempotencyManager.class)
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyAutoConfiguration {

    /**
     * Creates a new auto-configuration instance.
     */
    public IdempotencyAutoConfiguration() {
    }

    // -------------------------------------------------------------------------
    // Shared IdempotencyConfig
    // -------------------------------------------------------------------------

    /**
     * Configures the core {@link IdempotencyConfig} based on application properties.
     *
     * @param properties idempotency configuration properties.
     * @return the shared {@link IdempotencyConfig} bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotencyConfig idempotencyConfig(IdempotencyProperties properties) {
        return new IdempotencyConfig(
                properties.getTtl(),
                properties.getTtlUnit(),
                properties.getLockTimeout(),
                properties.getLockTimeoutUnit());
    }

    // -------------------------------------------------------------------------
    // Fail-fast guard — multiple stores + no explicit store choice
    // -------------------------------------------------------------------------

    /**
     * Fail-fast bean that throws an {@link IllegalStateException} if multiple storage backends are on the classpath
     * without an explicit {@code avoonce.idempotency.store} property set.
     *
     * @return an {@link InitializingBean} guard.
     */
    @Bean
    @ConditionalOnClass(name = {
            "io.github.ravocode.avoonce.caffeine.CaffeineIdempotencyRepository",
            "io.github.ravocode.avoonce.jdbc.JdbcIdempotencyRepository"
    })
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "avoonce.idempotency", name = "store", havingValue = "auto", matchIfMissing = true)
    public InitializingBean ambiguousStoreGuard() {
        return () -> {
            throw new IllegalStateException(
                    "[AvoOnce] Multiple idempotency storage repositories are available on the classpath "
                            + "along with their required beans (e.g. DataSource or RedisOperations). Please set "
                            + "'avoonce.idempotency.store' explicitly to 'caffeine', 'jdbc', or 'redis'.");
        };
    }

    // -------------------------------------------------------------------------
    // Core manager + servlet filter
    // -------------------------------------------------------------------------

    /**
     * Creates the primary {@link IdempotencyManager} bean backed by the resolved {@link IdempotencyRepository}.
     *
     * @param repository the backing store for idempotency records.
     * @return the core {@link IdempotencyManager} bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotencyManager idempotencyManager(IdempotencyRepository repository) {
        return new IdempotencyManager(repository);
    }

    /**
     * Registers the {@link IdempotencyFilter} as a Spring Boot servlet filter.
     *
     * @param manager                the core idempotency state machine.
     * @param properties             idempotency configuration properties.
     * @param handlerMappingProvider provider for Spring's {@link RequestMappingHandlerMapping}.
     * @return the filter registration bean.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "avoonce.idempotency.filter", name = "enabled", matchIfMissing = true)
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration(
            IdempotencyManager manager,
            IdempotencyProperties properties,
            ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider) {
        FilterRegistrationBean<IdempotencyFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new IdempotencyFilter(manager, properties, handlerMappingProvider));
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registrationBean;
    }
}
