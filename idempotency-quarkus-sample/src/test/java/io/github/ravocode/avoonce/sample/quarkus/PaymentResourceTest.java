package io.github.ravocode.avoonce.sample.quarkus;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class PaymentResourceTest {

    @TestHTTPResource("/api/payments")
    URI paymentsUri;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(paymentsUri.toString() + "/reset"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }

    @Test
    void testSuccessfulIdempotentRequest() throws Exception {
        String key = UUID.randomUUID().toString();
        String requestBody = "{\"accountId\":\"acc-123\",\"amount\":100.50}";

        // 1. First Request
        HttpRequest req1 = HttpRequest.newBuilder()
                .uri(paymentsUri)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> res1 = httpClient.send(req1, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, res1.statusCode());
        assertTrue(res1.headers().firstValue("X-Payment-Processed").isPresent());
        assertEquals("true", res1.headers().firstValue("X-Payment-Processed").get());
        assertTrue(res1.body().contains("\"status\":\"SUCCESS\""));
        assertTrue(res1.body().contains("\"processedAttempts\":1"));

        // 2. Retry with same key
        HttpResponse<String> res2 = httpClient.send(req1, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, res2.statusCode());
        assertEquals(res1.body(), res2.body(), "Response body must be identical and replayed from cache");

        // Verify resource method was only executed once
        HttpRequest countReq = HttpRequest.newBuilder()
                .uri(URI.create(paymentsUri.toString() + "/count"))
                .GET()
                .build();
        HttpResponse<String> countRes = httpClient.send(countReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, countRes.statusCode());
        assertTrue(countRes.body().contains("\"count\":1"));
    }

    @Test
    void testFailedValidationIdempotentRequestIsCached() throws Exception {
        String key = UUID.randomUUID().toString();
        String requestBody = "{\"accountId\":\"acc-123\",\"amount\":-50.00}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(paymentsUri)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // 1. First Request
        HttpResponse<String> res1 = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, res1.statusCode());
        assertTrue(res1.body().contains("\"status\":\"INVALID_AMOUNT\""));
        assertTrue(res1.body().contains("\"processedAttempts\":1"));

        // 2. Retry with same key
        HttpResponse<String> res2 = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, res2.statusCode());
        assertEquals(res1.body(), res2.body(), "Failed response must be replayed from cache");

        // Process count in backend should still be 1
        HttpRequest countReq = HttpRequest.newBuilder()
                .uri(URI.create(paymentsUri.toString() + "/count"))
                .GET()
                .build();
        HttpResponse<String> countRes = httpClient.send(countReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, countRes.statusCode());
        assertTrue(countRes.body().contains("\"count\":1"));
    }

    @Test
    void testHashMismatchReturns422() throws Exception {
        String key = UUID.randomUUID().toString();

        HttpRequest req1 = HttpRequest.newBuilder()
                .uri(paymentsUri)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString("{\"accountId\":\"acc-123\",\"amount\":100.00}"))
                .build();

        HttpResponse<String> res1 = httpClient.send(req1, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, res1.statusCode());

        // Same key, different amount
        HttpRequest req2 = HttpRequest.newBuilder()
                .uri(paymentsUri)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString("{\"accountId\":\"acc-123\",\"amount\":200.00}"))
                .build();

        HttpResponse<String> res2 = httpClient.send(req2, HttpResponse.BodyHandlers.ofString());
        assertEquals(422, res2.statusCode());
        assertTrue(res2.body().contains("mismatch"));
    }

    @Test
    void testRequestWithoutKeyPassesThrough() throws Exception {
        String requestBody = "{\"accountId\":\"acc-123\",\"amount\":100.50}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(paymentsUri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> res1 = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, res1.statusCode());

        HttpResponse<String> res2 = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, res2.statusCode());

        HttpRequest countReq = HttpRequest.newBuilder()
                .uri(URI.create(paymentsUri.toString() + "/count"))
                .GET()
                .build();
        HttpResponse<String> countRes = httpClient.send(countReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, countRes.statusCode());
        assertTrue(countRes.body().contains("\"count\":2"));
    }

    @Test
    void testNameBindingIgnoresUnannotatedEndpoint() throws Exception {
        String key = UUID.randomUUID().toString();
        String requestBody = "{\"accountId\":\"acc-456\",\"amount\":50.00}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(paymentsUri.toString() + "/unprotected"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // 1. First Request to unannotated endpoint
        HttpResponse<String> res1 = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res1.statusCode());
        assertTrue(res1.body().contains("\"status\":\"UNPROTECTED\""));
        assertTrue(res1.body().contains("\"processedAttempts\":1"));

        // 2. Second Request with same key to unannotated endpoint
        HttpResponse<String> res2 = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res2.statusCode());
        assertTrue(res2.body().contains("\"status\":\"UNPROTECTED\""));
        assertTrue(res2.body().contains("\"processedAttempts\":2"));

        assertNotEquals(res1.body(), res2.body(), "Unprotected endpoint should not replay cached response");

        HttpRequest countReq = HttpRequest.newBuilder()
                .uri(URI.create(paymentsUri.toString() + "/count"))
                .GET()
                .build();
        HttpResponse<String> countRes = httpClient.send(countReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, countRes.statusCode());
        assertTrue(countRes.body().contains("\"count\":2"));
    }

    @Test
    void testConcurrentRequestsReturn409ConflictOrReplay() throws Exception {
        String key = UUID.randomUUID().toString();
        String requestJson = "{\"accountId\":\"acc-123\",\"amount\":100.50}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(paymentsUri)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        CompletableFuture<HttpResponse<String>> f1 = CompletableFuture.supplyAsync(() -> {
            try {
                return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture<HttpResponse<String>> f2 = CompletableFuture.supplyAsync(() -> {
            try {
                return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture.allOf(f1, f2).join();

        HttpResponse<String> r1 = f1.get();
        HttpResponse<String> r2 = f2.get();

        int s1 = r1.statusCode();
        int s2 = r2.statusCode();

        boolean oneCreated = s1 == 201 || s2 == 201;
        boolean validOther = s1 == 409 || s2 == 409 || s1 == 201 || s2 == 201;

        assertTrue(oneCreated, "At least one request must succeed with 201");
        assertTrue(validOther, "The other request must return 409 Conflict or 201 Replay");
    }
}
