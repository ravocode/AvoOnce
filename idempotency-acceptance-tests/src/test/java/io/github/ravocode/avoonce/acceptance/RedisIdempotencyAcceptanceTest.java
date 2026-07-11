package io.github.ravocode.avoonce.acceptance;

import io.github.ravocode.avoonce.acceptance.dummy.DummyApplication;
import io.github.ravocode.avoonce.acceptance.dummy.PaymentController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.JedisPool;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = { DummyApplication.class,
        RedisIdempotencyAcceptanceTest.RedisTestConfig.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "avoonce.idempotency.store=redis"
})
public class RedisIdempotencyAcceptanceTest extends BaseIdempotencyAcceptanceTest {

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @BeforeAll
    static void startContainer() {
        Assumptions.assumeTrue(org.testcontainers.DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available");
        REDIS.start();
        System.setProperty("test.redis.host", REDIS.getHost());
        System.setProperty("test.redis.port", REDIS.getMappedPort(6379).toString());
    }

    @AfterAll
    static void stopContainer() {
        if (REDIS.isRunning()) {
            REDIS.stop();
        }
    }

    @TestConfiguration
    public static class RedisTestConfig {
        @Bean
        public JedisPool jedisPool() {
            String host = System.getProperty("test.redis.host");
            int port = Integer.parseInt(System.getProperty("test.redis.port"));
            return new JedisPool(host, port);
        }
    }

    @Test
    void testDistributedIdempotencyAcrossMultipleInstances() throws Exception {
        // Start a second instance of the application on a random port
        // It connects to the exact same Redis container
        ConfigurableApplicationContext secondContext = new SpringApplicationBuilder(
                DummyApplication.class, RedisTestConfig.class)
                .properties(
                        "server.port=0",
                        "avoonce.idempotency.store=redis")
                .run();

        try {
            int secondPort = secondContext.getEnvironment().getProperty("local.server.port", Integer.class);
            TestRestTemplate secondRestTemplate = new TestRestTemplate();

            String idempotencyKey = UUID.randomUUID().toString();
            PaymentController.PaymentRequest request = new PaymentController.PaymentRequest();
            request.accountId = "dist-redis-123";
            request.amount = 500.00;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Idempotency-Key", idempotencyKey);
            HttpEntity<PaymentController.PaymentRequest> entity = new HttpEntity<>(request, headers);

            String url1 = "/api/payments";
            String url2 = "http://localhost:" + secondPort + "/api/payments";

            // Fire requests to both instances concurrently
            CompletableFuture<ResponseEntity<String>> future1 = CompletableFuture
                    .supplyAsync(() -> restTemplate.exchange(url1, HttpMethod.POST, entity, String.class));

            CompletableFuture<ResponseEntity<String>> future2 = CompletableFuture
                    .supplyAsync(() -> secondRestTemplate.exchange(url2, HttpMethod.POST, entity, String.class));

            CompletableFuture.allOf(future1, future2).join();

            ResponseEntity<String> res1 = future1.get();
            ResponseEntity<String> res2 = future2.get();

            // One should succeed (201 CREATED) and one should get a conflict (409)
            boolean oneCreated = res1.getStatusCode() == HttpStatus.CREATED
                    || res2.getStatusCode() == HttpStatus.CREATED;
            boolean oneConflict = res1.getStatusCode() == HttpStatus.CONFLICT
                    || res2.getStatusCode() == HttpStatus.CONFLICT;

            assertTrue(oneCreated, "One request should succeed across distributed instances");
            assertTrue(oneConflict, "One request should return 409 Conflict across distributed instances");

            // Check process count on both controllers to ensure it was only processed
            // EXACTLY once globally
            int count1 = paymentController.getProcessCount();
            int count2 = secondContext.getBean(PaymentController.class).getProcessCount();
            assertEquals(1, count1 + count2, "Controller should only be executed once across all instances");

        } finally {
            secondContext.close();
        }
    }
}
