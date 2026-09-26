package io.github.ravocode.avoonce.sample;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class BaseSampleIntegrationTest {

    @LocalServerPort
    protected int port;

    protected final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void testBasicIdempotencyWorks() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = "{\"accountId\":\"sample-acc-999\",\"amount\":50.00}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/payments"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // 1. Initial Request
        HttpResponse<String> response1 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response1.statusCode());
        String body1 = response1.body();
        assertTrue(body1.contains("transactionId"));

        // 2. Retry with same idempotency key
        HttpResponse<String> response2 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response2.statusCode());
        assertEquals(body1, response2.body());
    }
}
