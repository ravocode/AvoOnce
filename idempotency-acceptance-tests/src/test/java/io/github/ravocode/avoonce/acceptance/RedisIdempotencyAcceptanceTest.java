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
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.JedisPool;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

            String idempotencyKey = UUID.randomUUID().toString();
            String requestBody = "{\"accountId\":\"dist-redis-123\",\"amount\":500.00}";

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
            CompletableFuture<HttpResponse<String>> future1 = CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            return httpClient.send(req1, HttpResponse.BodyHandlers.ofString());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });

            CompletableFuture<HttpResponse<String>> future2 = CompletableFuture
                    .supplyAsync(() -> {
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
            boolean oneCreated = res1.statusCode() == 201
                    || res2.statusCode() == 201;
            boolean oneConflict = res1.statusCode() == 409
                    || res2.statusCode() == 409;

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
