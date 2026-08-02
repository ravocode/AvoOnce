package io.github.ravocode.avoonce.spring;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import io.github.ravocode.avoonce.core.config.IdempotencyConfig;
import io.github.ravocode.avoonce.core.spi.IdempotencyRepository;
import io.github.ravocode.avoonce.jdbc.JdbcIdempotencyRepository;

/**
 * Auto-configuration for JDBC relational database idempotency repository.
 * Configures database schema initialization and background cleanup task.
 */
/**
 * Auto-configuration for JDBC-backed idempotency repositories.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
@ConditionalOnClass({ JdbcIdempotencyRepository.class, DataSource.class })
@EnableScheduling
public class JdbcIdempotencyAutoConfiguration {

    /**
     * Creates a new auto-configuration instance.
     */
    public JdbcIdempotencyAutoConfiguration() {
    }

    /**
     * Auto-wires the JDBC repository when DataSource is present, Caffeine is absent, and store property is auto.
     *
     * @param dataSource the Spring DataSource bean.
     * @param config     the shared idempotency configuration.
     * @return the {@link JdbcIdempotencyRepository} bean.
     */
    @Bean
    @ConditionalOnMissingBean(IdempotencyRepository.class)
    @ConditionalOnMissingClass("com.github.benmanes.caffeine.cache.Cache")
    @ConditionalOnProperty(prefix = "avoonce.idempotency", name = "store", havingValue = "auto", matchIfMissing = true)
    public JdbcIdempotencyRepository jdbcAutoRepository(DataSource dataSource, IdempotencyConfig config) {
        return new JdbcIdempotencyRepository(dataSource, config);
    }

    /**
     * Wires the JDBC repository when {@code avoonce.idempotency.store=jdbc} is explicitly set.
     *
     * @param dataSource the Spring DataSource bean.
     * @param config     the shared idempotency configuration.
     * @return the {@link JdbcIdempotencyRepository} bean.
     */
    @Bean
    @ConditionalOnMissingBean(IdempotencyRepository.class)
    @ConditionalOnProperty(prefix = "avoonce.idempotency", name = "store", havingValue = "jdbc")
    public JdbcIdempotencyRepository jdbcExplicitRepository(DataSource dataSource, IdempotencyConfig config) {
        return new JdbcIdempotencyRepository(dataSource, config);
    }

    /**
     * Automatically initializes the idempotency database table if auto-ddl is enabled.
     *
     * @param dataSource the Spring DataSource bean.
     * @return the {@link InitializingBean} initializer.
     */
    @Bean
    @ConditionalOnBean(JdbcIdempotencyRepository.class)
    @ConditionalOnProperty(prefix = "avoonce.idempotency.jdbc", name = "auto-ddl", havingValue = "true", matchIfMissing = true)
    public InitializingBean jdbcTableInitializer(DataSource dataSource) {
        return () -> new io.github.ravocode.avoonce.jdbc.JdbcIdempotencyTableInitializer().initialize(dataSource);
    }

    /**
     * Configures the scheduled background cleanup task for expired JDBC idempotency records.
     *
     * @param repository the JDBC idempotency repository.
     * @return the {@link JdbcEvictionTask} bean.
     */
    @Bean
    @ConditionalOnBean(JdbcIdempotencyRepository.class)
    @ConditionalOnProperty(prefix = "avoonce.idempotency.jdbc.eviction", name = "enabled", havingValue = "true", matchIfMissing = true)
    public JdbcEvictionTask jdbcEvictionTask(JdbcIdempotencyRepository repository) {
        return new JdbcEvictionTask(repository);
    }

    /**
     * Scheduled background task that triggers eviction of expired records from the JDBC store.
     */
    public static class JdbcEvictionTask {
        private final IdempotencyRepository repository;

        /**
         * Constructs a {@code JdbcEvictionTask} with the given repository.
         *
         * @param repository the backing repository to evict expired records from.
         */
        public JdbcEvictionTask(final IdempotencyRepository repository) {
            this.repository = repository;
        }

        /**
         * Periodically runs the eviction task based on configured interval.
         */
        @Scheduled(fixedDelayString = "${avoonce.idempotency.jdbc.eviction.interval-ms:3600000}")
        public void evictExpired() {
            if (repository instanceof JdbcIdempotencyRepository) {
                repository.evictExpired();
            }
        }
    }
}
