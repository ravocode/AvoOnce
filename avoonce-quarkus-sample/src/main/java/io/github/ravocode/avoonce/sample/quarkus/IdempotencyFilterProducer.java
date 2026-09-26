package io.github.ravocode.avoonce.sample.quarkus;

import java.util.concurrent.TimeUnit;

import io.github.ravocode.avoonce.caffeine.CaffeineIdempotencyRepository;
import io.github.ravocode.avoonce.core.config.IdempotencyConfig;
import io.github.ravocode.avoonce.core.spi.IdempotencyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * CDI Producer providing the {@link IdempotencyRepository} in Quarkus.
 * The {@link io.github.ravocode.avoonce.jaxrs.IdempotencyContainerFilter}
 * automatically discovers and injects this repository.
 */
/**
 * CDI producer that exposes the sample in-memory repository for Quarkus.
 */
@ApplicationScoped
public class IdempotencyFilterProducer {

    /**
     * Creates a new producer instance.
     */
    public IdempotencyFilterProducer() {
    }

    /**
     * Produces the repository used by the Quarkus JAX-RS filter.
     *
     * @return an in-memory repository configured with a one-hour TTL and two-minute lock timeout
     */
    @Produces
    @Singleton
    public IdempotencyRepository idempotencyRepository() {
        IdempotencyConfig config = new IdempotencyConfig(
                1, TimeUnit.HOURS,
                2, TimeUnit.MINUTES
        );
        return new CaffeineIdempotencyRepository(config);
    }
}
