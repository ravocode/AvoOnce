package io.github.ravocode.avoonce.acceptance;

import io.github.ravocode.avoonce.acceptance.dummy.DummyApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Runs the full {@link BaseIdempotencyAcceptanceTest} suite against the Caffeine
 * in-memory idempotency store.
 *
 * <p>The {@code avoonce.idempotency.store=caffeine} property is required because
 * {@code idempotency-jdbc} is also on the test classpath (for the JDBC acceptance test).
 * Without an explicit store selection, the ambiguity guard would fail at startup.
 */
@SpringBootTest(classes = DummyApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "avoonce.idempotency.store=caffeine"
})
public class CaffeineIdempotencyAcceptanceTest extends BaseIdempotencyAcceptanceTest {
    // All 5 test scenarios are inherited from BaseIdempotencyAcceptanceTest.
}
