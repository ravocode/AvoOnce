package io.github.ravocode.avoonce.sample;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "avoonce.idempotency.store=caffeine"
})
class SelectiveAnnotationIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() throws Exception {
        HttpRequest resetRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/payments/reset"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        httpClient.send(resetRequest, HttpResponse.BodyHandlers.discarding());
    }

    @Test
    void testAnnotatedEndpointIsProtected() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = "{\"accountId\":\"annotated-acc\",\"amount\":75.00}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/payments"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // 1. Initial Request to @Idempotent endpoint
        HttpResponse<String> response1 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response1.statusCode());
        String body1 = response1.body();
        assertTrue(body1.contains("\"processedAttempts\":1"));

        // 2. Retry with same key
        HttpResponse<String> response2 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response2.statusCode());
        assertEquals(body1, response2.body(), "Annotated endpoint should return cached response body");
    }

    @Test
    void testUnannotatedEndpointBypassesFilter() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = "{\"accountId\":\"unprotected-acc\",\"amount\":50.00}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/payments/unprotected"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // 1. First Request to unannotated endpoint
        HttpResponse<String> response1 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response1.statusCode());
        assertTrue(response1.body().contains("\"processedAttempts\":1"));

        // 2. Second Request with same key to unannotated endpoint
        HttpResponse<String> response2 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response2.statusCode());
        assertTrue(response2.body().contains("\"processedAttempts\":2"));

        assertNotEquals(response1.body(), response2.body(),
                "Unannotated endpoint must execute on every request even with Idempotency-Key");
    }
}
