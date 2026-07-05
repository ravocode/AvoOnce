package io.github.ravocode.avoonce.acceptance;

import io.github.ravocode.avoonce.acceptance.dummy.DummyApplication;
import io.github.ravocode.avoonce.acceptance.dummy.PaymentController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

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
        "spring.datasource.url=jdbc:h2:mem:idempotency_acceptance;DB_CLOSE_DELAY=-1;MODE=MySQL",
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
                        "spring.datasource.url=jdbc:h2:mem:idempotency_acceptance;DB_CLOSE_DELAY=-1;MODE=MySQL",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "avoonce.idempotency.jdbc.eviction.enabled=false"
                ).run();

        try {
            int secondPort = secondContext.getEnvironment().getProperty("local.server.port", Integer.class);
            TestRestTemplate secondRestTemplate = new TestRestTemplate();
            
            String idempotencyKey = UUID.randomUUID().toString();
            PaymentController.PaymentRequest request = new PaymentController.PaymentRequest();
            request.accountId = "dist-123";
            request.amount = 500.00;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Idempotency-Key", idempotencyKey);
            HttpEntity<PaymentController.PaymentRequest> entity = new HttpEntity<>(request, headers);

            String url1 = "/api/payments";
            String url2 = "http://localhost:" + secondPort + "/api/payments";

            // Fire requests to both instances concurrently
            CompletableFuture<ResponseEntity<String>> future1 = CompletableFuture.supplyAsync(() ->
                    restTemplate.exchange(url1, HttpMethod.POST, entity, String.class));
            
            CompletableFuture<ResponseEntity<String>> future2 = CompletableFuture.supplyAsync(() ->
                    secondRestTemplate.exchange(url2, HttpMethod.POST, entity, String.class));

            CompletableFuture.allOf(future1, future2).join();

            ResponseEntity<String> res1 = future1.get();
            ResponseEntity<String> res2 = future2.get();

            // One should succeed (201 CREATED) and one should get a conflict (409) 
            // since they are processed simultaneously and locking prevents both from running.
            boolean oneCreated = res1.getStatusCode() == HttpStatus.CREATED || res2.getStatusCode() == HttpStatus.CREATED;
            boolean oneConflict = res1.getStatusCode() == HttpStatus.CONFLICT || res2.getStatusCode() == HttpStatus.CONFLICT;

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
