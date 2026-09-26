package io.github.ravocode.avoonce.acceptance;

import io.github.ravocode.avoonce.acceptance.dummy.DummyApplication;
import io.github.ravocode.avoonce.acceptance.dummy.PaymentController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the full {@link BaseIdempotencyAcceptanceTest} suite against the JDBC
 * distributed idempotency store backed by an H2 in-memory database.
 *
 * <p>The {@code avoonce.idempotency.store=jdbc} property forces the Spring Boot
 * auto-configuration to wire {@code JdbcIdempotencyRepository} even though
 * {@code idempotency-caffeine} is also on the test classpath (for the Caffeine
 * acceptance test). Without it the ambiguity guard would fail fast at startup.
 */
@SpringBootTest(classes = DummyApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "avoonce.idempotency.store=jdbc",
        "spring.datasource.url=jdbc:h2:mem:idempotency_acceptance;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // Disable the eviction scheduler to keep tests fast and deterministic
        "avoonce.idempotency.jdbc.eviction.enabled=false"
})
public class JdbcIdempotencyAcceptanceTest extends BaseIdempotencyAcceptanceTest {
    // All 5 test scenarios are inherited from BaseIdempotencyAcceptanceTest.
    // The JDBC store is wired via test properties above.

    @Test
    void testDistributedIdempotencyAcrossMultipleInstances() throws Exception {
        // Start a second instance of the application on a random port
        // It connects to the exact same H2 in-memory named database as the first instance
        ConfigurableApplicationContext secondContext = new SpringApplicationBuilder(DummyApplication.class)
                .properties(
                        "server.port=0",
                        "avoonce.idempotency.store=jdbc",
                        "spring.datasource.url=jdbc:h2:mem:idempotency_acceptance;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "avoonce.idempotency.jdbc.eviction.enabled=false"
                ).run();

        try {
            int secondPort = secondContext.getEnvironment().getProperty("local.server.port", Integer.class);

            String idempotencyKey = UUID.randomUUID().toString();
            String requestBody = "{\"accountId\":\"dist-123\",\"amount\":500.00}";

            HttpRequest req1 = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/payments"))
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpRequest req2 = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + secondPort + "/api/payments"))
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            // Fire requests to both instances concurrently
            CompletableFuture<HttpResponse<String>> future1 = CompletableFuture.supplyAsync(() -> {
                try {
                    return httpClient.send(req1, HttpResponse.BodyHandlers.ofString());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            CompletableFuture<HttpResponse<String>> future2 = CompletableFuture.supplyAsync(() -> {
                try {
                    return httpClient.send(req2, HttpResponse.BodyHandlers.ofString());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            CompletableFuture.allOf(future1, future2).join();

            HttpResponse<String> res1 = future1.get();
            HttpResponse<String> res2 = future2.get();

            // One should succeed (201 CREATED) and one should get a conflict (409) 
            // since they are processed simultaneously and locking prevents both from running.
            boolean oneCreated = res1.statusCode() == 201 || res2.statusCode() == 201;
            boolean oneConflict = res1.statusCode() == 409 || res2.statusCode() == 409;

            assertTrue(oneCreated, "One request should succeed across distributed instances");
            assertTrue(oneConflict, "One request should return 409 Conflict across distributed instances");

            // Check process count on both controllers to ensure it was only processed EXACTLY once globally
            int count1 = paymentController.getProcessCount();
            int count2 = secondContext.getBean(PaymentController.class).getProcessCount();
            assertEquals(1, count1 + count2, "Controller should only be executed once across all instances");

        } finally {
            secondContext.close();
        }
    }
}
