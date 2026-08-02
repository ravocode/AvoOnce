package io.github.ravocode.avoonce.sample.quarkus;

import io.github.ravocode.avoonce.caffeine.CaffeineIdempotencyRepository;
import io.github.ravocode.avoonce.core.config.IdempotencyConfig;
import io.github.ravocode.avoonce.core.spi.IdempotencyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.util.concurrent.TimeUnit;

/**
 * CDI Producer providing the {@link IdempotencyRepository} in Quarkus.
 * The {@link io.github.ravocode.avoonce.jaxrs.IdempotencyContainerFilter}
 * automatically discovers and injects this repository.
 */
@ApplicationScoped
public class IdempotencyFilterProducer {

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
