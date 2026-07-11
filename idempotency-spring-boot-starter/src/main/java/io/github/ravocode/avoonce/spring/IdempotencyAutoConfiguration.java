package io.github.ravocode.avoonce.spring;

import io.github.ravocode.avoonce.caffeine.CaffeineIdempotencyRepository;
import io.github.ravocode.avoonce.core.IdempotencyManager;
import io.github.ravocode.avoonce.core.config.IdempotencyConfig;
import io.github.ravocode.avoonce.core.spi.IdempotencyRepository;
import io.github.ravocode.avoonce.jdbc.JdbcIdempotencyRepository;
import io.github.ravocode.avoonce.jdbc.JdbcIdempotencyTableInitializer;
import io.github.ravocode.avoonce.redis.JedisRedisOperations;
import io.github.ravocode.avoonce.redis.LettuceRedisOperations;
import io.github.ravocode.avoonce.redis.RedisIdempotencyRepository;
import io.github.ravocode.avoonce.redis.RedisOperations;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import redis.clients.jedis.JedisPool;

import javax.sql.DataSource;

/**
 * Spring Boot auto-configuration for the AvoOnce idempotency library.
 *
 * <h2>Store selection</h2>
 * <ul>
 *   <li>If only {@code idempotency-caffeine} is on the classpath → Caffeine is wired automatically.</li>
 *   <li>If only {@code idempotency-jdbc} is on the classpath and a {@link DataSource} bean exists → JDBC is wired automatically.</li>
 *   <li>If <b>both</b> are on the classpath → startup fails with a clear error unless the user explicitly sets
 *       {@code avoonce.idempotency.store=caffeine} or {@code avoonce.idempotency.store=jdbc}.</li>
 * </ul>
 */
@AutoConfiguration
@AutoConfigureAfter(name = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
@ConditionalOnClass(IdempotencyManager.class)
@EnableConfigurationProperties(IdempotencyProperties.class)
@EnableScheduling
public class IdempotencyAutoConfiguration {

    // -------------------------------------------------------------------------
    // Shared IdempotencyConfig
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Caffeine store — auto mode (JDBC absent) or explicitly selected
    // -------------------------------------------------------------------------

    @Configuration
    @ConditionalOnClass(CaffeineIdempotencyRepository.class)
    public static class CaffeineRepositoryConfiguration {
        @Bean
        @ConditionalOnMissingBean(IdempotencyRepository.class)
        @ConditionalOnMissingClass("io.github.ravocode.avoonce.jdbc.JdbcIdempotencyRepository")
        @ConditionalOnProperty(prefix = "avoonce.idempotency", name = "store",
                havingValue = "auto", matchIfMissing = true)
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

    // -------------------------------------------------------------------------
    // JDBC store — auto mode (Caffeine absent + DataSource present) or explicitly selected
    // -------------------------------------------------------------------------

    @Configuration
    @ConditionalOnClass({JdbcIdempotencyRepository.class, DataSource.class})
    public static class JdbcRepositoryConfiguration {
        @Bean
        @ConditionalOnMissingBean(IdempotencyRepository.class)
        @ConditionalOnMissingClass("com.github.benmanes.caffeine.cache.Cache")
        @ConditionalOnProperty(prefix = "avoonce.idempotency", name = "store",
                havingValue = "auto", matchIfMissing = true)
        public JdbcIdempotencyRepository jdbcAutoRepository(DataSource dataSource, IdempotencyConfig config) {
            return new JdbcIdempotencyRepository(dataSource, config);
        }

        @Bean
        @ConditionalOnMissingBean(IdempotencyRepository.class)
        @ConditionalOnProperty(prefix = "avoonce.idempotency", name = "store", havingValue = "jdbc")
        public JdbcIdempotencyRepository jdbcExplicitRepository(DataSource dataSource, IdempotencyConfig config) {
            return new JdbcIdempotencyRepository(dataSource, config);
        }
    }

    // -------------------------------------------------------------------------
    // Redis store — auto mode or explicitly selected
    // -------------------------------------------------------------------------

    @Configuration
    @ConditionalOnClass(name = {"io.github.ravocode.avoonce.redis.RedisIdempotencyRepository", "io.lettuce.core.RedisClient"})
    public static class LettuceAdapterConfiguration {
        @Bean
        @ConditionalOnMissingBean(RedisOperations.class)
        @ConditionalOnBean(type = "io.lettuce.core.RedisClient")
        public LettuceRedisOperations lettuceRedisOperations(io.lettuce.core.RedisClient redisClient) {
            return new LettuceRedisOperations(redisClient);
        }
    }

    @Configuration
    @ConditionalOnClass(name = {"io.github.ravocode.avoonce.redis.RedisIdempotencyRepository", "redis.clients.jedis.JedisPool"})
    public static class JedisAdapterConfiguration {
        @Bean
        @ConditionalOnMissingBean(RedisOperations.class)
        @ConditionalOnBean(type = "redis.clients.jedis.JedisPool")
        public JedisRedisOperations jedisRedisOperations(redis.clients.jedis.JedisPool jedisPool) {
            return new JedisRedisOperations(jedisPool);
        }
    }

    @Configuration
    @ConditionalOnClass({RedisIdempotencyRepository.class, RedisOperations.class})
    public static class RedisRepositoryConfiguration {
        @Bean
        @ConditionalOnMissingBean(IdempotencyRepository.class)
        @ConditionalOnBean(RedisOperations.class)
        @ConditionalOnProperty(prefix = "avoonce.idempotency", name = "store",
                havingValue = "auto", matchIfMissing = true)
        public RedisIdempotencyRepository redisAutoRepository(RedisOperations redisOperations, IdempotencyConfig config) {
            return new RedisIdempotencyRepository(redisOperations, config);
        }

        @Bean
        @ConditionalOnMissingBean(IdempotencyRepository.class)
        @ConditionalOnBean(RedisOperations.class)
        @ConditionalOnProperty(prefix = "avoonce.idempotency", name = "store", havingValue = "redis")
        public RedisIdempotencyRepository redisExplicitRepository(RedisOperations redisOperations, IdempotencyConfig config) {
            return new RedisIdempotencyRepository(redisOperations, config);
        }
    }

    // -------------------------------------------------------------------------
    // JDBC DDL — runs CREATE TABLE IF NOT EXISTS at startup
    // -------------------------------------------------------------------------

    @Bean
    @ConditionalOnBean(JdbcIdempotencyRepository.class)
    @ConditionalOnProperty(prefix = "avoonce.idempotency.jdbc", name = "auto-ddl",
            havingValue = "true", matchIfMissing = true)
    public InitializingBean jdbcTableInitializer(DataSource dataSource) {
        return () -> new JdbcIdempotencyTableInitializer().initialize(dataSource);
    }

    // -------------------------------------------------------------------------
    // Fail-fast guard — both stores + DataSource + no explicit store choice
    // -------------------------------------------------------------------------

    @Bean
    @ConditionalOnClass({CaffeineIdempotencyRepository.class, JdbcIdempotencyRepository.class})
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "avoonce.idempotency", name = "store",
            havingValue = "auto", matchIfMissing = true)
    public InitializingBean ambiguousStoreGuard() {
        return () -> {
            throw new IllegalStateException(
                    "[AvoOnce] Multiple idempotency storage repositories are available on the classpath "
                    + "along with their required beans (e.g. DataSource or RedisOperations). Please set "
                    + "'avoonce.idempotency.store' explicitly to 'caffeine', 'jdbc', or 'redis'.");
        };
    }

    // -------------------------------------------------------------------------
    // JDBC eviction scheduler — periodically deletes expired records
    // -------------------------------------------------------------------------

    @Bean
    @ConditionalOnBean(JdbcIdempotencyRepository.class)
    @ConditionalOnProperty(prefix = "avoonce.idempotency.jdbc.eviction", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public JdbcEvictionTask jdbcEvictionTask(JdbcIdempotencyRepository repository) {
        return new JdbcEvictionTask(repository);
    }

    /**
     * Scheduled task that delegates to {@link IdempotencyRepository#evictExpired()}.
     * Only operates when the repository is a {@link JdbcIdempotencyRepository}.
     */
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

    // -------------------------------------------------------------------------
    // Core manager + servlet filter
    // -------------------------------------------------------------------------

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
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registrationBean;
    }
}
