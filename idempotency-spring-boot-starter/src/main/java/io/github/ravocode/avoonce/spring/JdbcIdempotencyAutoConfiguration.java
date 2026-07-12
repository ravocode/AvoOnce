package io.github.ravocode.avoonce.spring;

import io.github.ravocode.avoonce.core.config.IdempotencyConfig;
import io.github.ravocode.avoonce.core.spi.IdempotencyRepository;
import io.github.ravocode.avoonce.jdbc.JdbcIdempotencyRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;

@AutoConfiguration
@AutoConfigureAfter(name = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
@ConditionalOnClass({ JdbcIdempotencyRepository.class, DataSource.class })
public class JdbcIdempotencyAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(IdempotencyRepository.class)
    @ConditionalOnMissingClass("com.github.benmanes.caffeine.cache.Cache")
    @ConditionalOnProperty(prefix = "avoonce.idempotency", name = "store", havingValue = "auto", matchIfMissing = true)
    public JdbcIdempotencyRepository jdbcAutoRepository(DataSource dataSource, IdempotencyConfig config) {
        return new JdbcIdempotencyRepository(dataSource, config);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotencyRepository.class)
    @ConditionalOnProperty(prefix = "avoonce.idempotency", name = "store", havingValue = "jdbc")
    public JdbcIdempotencyRepository jdbcExplicitRepository(DataSource dataSource, IdempotencyConfig config) {
        return new JdbcIdempotencyRepository(dataSource, config);
    }

    @Bean
    @ConditionalOnBean(JdbcIdempotencyRepository.class)
    @ConditionalOnProperty(prefix = "avoonce.idempotency.jdbc", name = "auto-ddl", havingValue = "true", matchIfMissing = true)
    public InitializingBean jdbcTableInitializer(DataSource dataSource) {
        return () -> new io.github.ravocode.avoonce.jdbc.JdbcIdempotencyTableInitializer().initialize(dataSource);
    }

    @Bean
    @ConditionalOnBean(JdbcIdempotencyRepository.class)
    @ConditionalOnProperty(prefix = "avoonce.idempotency.jdbc.eviction", name = "enabled", havingValue = "true", matchIfMissing = true)
    public JdbcEvictionTask jdbcEvictionTask(JdbcIdempotencyRepository repository) {
        return new JdbcEvictionTask(repository);
    }

    public static class JdbcEvictionTask {
        private final IdempotencyRepository repository;

        public JdbcEvictionTask(final IdempotencyRepository repository) {
            this.repository = repository;
        }

        @Scheduled(fixedDelayString = "${avoonce.idempotency.jdbc.eviction.interval-ms:3600000}")
        public void evictExpired() {
            if (repository instanceof JdbcIdempotencyRepository) {
                repository.evictExpired();
            }
        }
    }
}
