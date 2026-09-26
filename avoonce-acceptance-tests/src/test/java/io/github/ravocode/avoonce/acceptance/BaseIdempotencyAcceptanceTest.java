package io.github.ravocode.avoonce.acceptance;

import io.github.ravocode.avoonce.acceptance.dummy.PaymentController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIdempotencyAcceptanceTest {

    @LocalServerPort
    protected int port;

    protected final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    protected PaymentController paymentController;

    @BeforeEach
    void setUp() {
        paymentController.resetCount();
    }

    @Test
    void testSuccessfulIdempotentRequest() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = "{\"accountId\":\"acc-123\",\"amount\":100.50}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/payments"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // 1. First Request
        HttpResponse<String> response1 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response1.statusCode());
        assertTrue(response1.body().contains("\"status\":\"SUCCESS\""));
        assertTrue(response1.body().contains("\"processedAttempts\":1"));
        assertEquals("true", response1.headers().firstValue("X-Payment-Processed").orElse(null));

        // 2. Retry with same key
        HttpResponse<String> response2 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response2.statusCode());
        assertEquals(response1.body(), response2.body());
        assertEquals("true", response2.headers().firstValue("X-Payment-Processed").orElse(null));

        // Verify controller only executed once
        assertEquals(1, paymentController.getProcessCount());
    }

    @Test
    void testFailedValidationIdempotentRequestIsCached() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = "{\"accountId\":\"acc-123\",\"amount\":-50.00}"; // Invalid amount

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/payments"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // 1. First Request
        HttpResponse<String> response1 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response1.statusCode());
        assertTrue(response1.body().contains("INVALID_AMOUNT"));
        assertTrue(response1.body().contains("\"processedAttempts\":1"));

        // 2. Retry with same key
        HttpResponse<String> response2 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response2.statusCode());
        assertEquals(response1.body(), response2.body());

        // Processed attempts from cache should still be 1
        assertEquals(1, paymentController.getProcessCount());
    }

    @Test
    void testHashMismatchReturns422() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        // Request 1
        HttpRequest request1 = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/payments"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString("{\"accountId\":\"acc-123\",\"amount\":100.00}"))
                .build();

        HttpResponse<String> response1 = httpClient.send(request1, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response1.statusCode());

        // Request 2 with same key but different body
        HttpRequest request2 = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/payments"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString("{\"accountId\":\"acc-123\",\"amount\":200.00}"))
                .build();

        HttpResponse<String> response2 = httpClient.send(request2, HttpResponse.BodyHandlers.ofString());
        assertEquals(422, response2.statusCode());
        assertTrue(response2.body() != null && response2.body().contains("Idempotency mismatch"));
    }

    @Test
    void testConcurrentRequestsReturn409Conflict() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = "{\"accountId\":\"acc-123\",\"amount\":100.50}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/payments"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // Fire two requests concurrently
        CompletableFuture<HttpResponse<String>> future1 = CompletableFuture.supplyAsync(() -> {
            try {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture<HttpResponse<String>> future2 = CompletableFuture.supplyAsync(() -> {
            try {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture.allOf(future1, future2).join();

        HttpResponse<String> res1 = future1.get();
        HttpResponse<String> res2 = future2.get();

        // One should be CREATED, the other should be CONFLICT
        boolean oneCreated = res1.statusCode() == 201 || res2.statusCode() == 201;
        boolean oneConflict = res1.statusCode() == 409 || res2.statusCode() == 409;

        assertTrue(oneCreated, "One request should succeed");
        assertTrue(oneConflict, "One request should return 409 Conflict");

        // Ensure controller was executed exactly once
        assertEquals(1, paymentController.getProcessCount());
    }

    @Test
    void testRequestWithoutKeyPassesThrough() throws Exception {
        String requestBody = "{\"accountId\":\"acc-123\",\"amount\":100.50}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/payments"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response1 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response1.statusCode());
        assertEquals(1, paymentController.getProcessCount());

        // Second request also passes through and increments
        HttpResponse<String> response2 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response2.statusCode());
        assertEquals(2, paymentController.getProcessCount());

        assertNotEquals(response1.body(), response2.body());
    }
}
