package io.github.raghavocode.avoonce.sample;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdempotencyIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentController paymentController;

    @BeforeEach
    void setUp() {
        paymentController.resetCount();
    }

    @Test
    void testSuccessfulIdempotentRequest() {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentController.PaymentRequest request = new PaymentController.PaymentRequest();
        request.accountId = "acc-123";
        request.amount = 100.50;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        HttpEntity<PaymentController.PaymentRequest> entity = new HttpEntity<>(request, headers);

        // 1. First Request
        ResponseEntity<PaymentController.PaymentResponse> response1 = restTemplate.exchange(
                "/api/payments", HttpMethod.POST, entity, PaymentController.PaymentResponse.class);

        assertEquals(HttpStatus.CREATED, response1.getStatusCode());
        assertNotNull(response1.getBody());
        assertEquals("SUCCESS", response1.getBody().status);
        assertEquals(1, response1.getBody().processedAttempts);
        assertEquals("true", response1.getHeaders().getFirst("X-Payment-Processed"));
        
        String transactionId = response1.getBody().transactionId;

        // 2. Retry with same key
        ResponseEntity<PaymentController.PaymentResponse> response2 = restTemplate.exchange(
                "/api/payments", HttpMethod.POST, entity, PaymentController.PaymentResponse.class);

        assertEquals(HttpStatus.CREATED, response2.getStatusCode());
        assertNotNull(response2.getBody());
        assertEquals("SUCCESS", response2.getBody().status);
        
        // The transactionId should match exactly (from cache)
        assertEquals(transactionId, response2.getBody().transactionId);
        
        // The processAttempts should still be 1 (from cache), showing controller didn't run again
        assertEquals(1, response2.getBody().processedAttempts);
        assertEquals("true", response2.getHeaders().getFirst("X-Payment-Processed"));
        
        // Verify controller only executed once
        assertEquals(1, paymentController.getProcessCount());
    }

    @Test
    void testFailedValidationIdempotentRequestIsCached() {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentController.PaymentRequest request = new PaymentController.PaymentRequest();
        request.accountId = "acc-123";
        request.amount = -50.00; // Invalid amount

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        HttpEntity<PaymentController.PaymentRequest> entity = new HttpEntity<>(request, headers);

        // 1. First Request
        ResponseEntity<PaymentController.PaymentResponse> response1 = restTemplate.exchange(
                "/api/payments", HttpMethod.POST, entity, PaymentController.PaymentResponse.class);

        assertEquals(HttpStatus.BAD_REQUEST, response1.getStatusCode());
        assertEquals("INVALID_AMOUNT", response1.getBody().status);
        assertEquals(1, response1.getBody().processedAttempts);

        // 2. Retry with same key
        ResponseEntity<PaymentController.PaymentResponse> response2 = restTemplate.exchange(
                "/api/payments", HttpMethod.POST, entity, PaymentController.PaymentResponse.class);

        assertEquals(HttpStatus.BAD_REQUEST, response2.getStatusCode());
        assertEquals("INVALID_AMOUNT", response2.getBody().status);
        
        // Processed attempts from cache should still be 1
        assertEquals(1, response2.getBody().processedAttempts);
        assertEquals(1, paymentController.getProcessCount());
    }

    @Test
    void testHashMismatchReturns422() {
        String idempotencyKey = UUID.randomUUID().toString();
        
        // Request 1
        PaymentController.PaymentRequest request1 = new PaymentController.PaymentRequest();
        request1.accountId = "acc-123";
        request1.amount = 100.00;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        HttpEntity<PaymentController.PaymentRequest> entity1 = new HttpEntity<>(request1, headers);

        ResponseEntity<PaymentController.PaymentResponse> response1 = restTemplate.exchange(
                "/api/payments", HttpMethod.POST, entity1, PaymentController.PaymentResponse.class);
        assertEquals(HttpStatus.CREATED, response1.getStatusCode());

        // Request 2 with same key but different body
        PaymentController.PaymentRequest request2 = new PaymentController.PaymentRequest();
        request2.accountId = "acc-123";
        request2.amount = 200.00;
        HttpEntity<PaymentController.PaymentRequest> entity2 = new HttpEntity<>(request2, headers);

        ResponseEntity<String> response2 = restTemplate.exchange(
                "/api/payments", HttpMethod.POST, entity2, String.class);

        // Should be rejected by IdempotencyFilter due to hash mismatch
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response2.getStatusCode());
        assertTrue(response2.getBody().contains("Idempotency mismatch"));
    }

    @Test
    void testConcurrentRequestsReturn409Conflict() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentController.PaymentRequest request = new PaymentController.PaymentRequest();
        request.accountId = "acc-123";
        request.amount = 100.50;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        HttpEntity<PaymentController.PaymentRequest> entity = new HttpEntity<>(request, headers);

        // Fire two requests concurrently
        CompletableFuture<ResponseEntity<String>> future1 = CompletableFuture.supplyAsync(() ->
                restTemplate.exchange("/api/payments", HttpMethod.POST, entity, String.class));
        
        CompletableFuture<ResponseEntity<String>> future2 = CompletableFuture.supplyAsync(() ->
                restTemplate.exchange("/api/payments", HttpMethod.POST, entity, String.class));

        CompletableFuture.allOf(future1, future2).join();

        ResponseEntity<String> res1 = future1.get();
        ResponseEntity<String> res2 = future2.get();

        // One should be CREATED, the other should be CONFLICT
        boolean oneCreated = res1.getStatusCode() == HttpStatus.CREATED || res2.getStatusCode() == HttpStatus.CREATED;
        boolean oneConflict = res1.getStatusCode() == HttpStatus.CONFLICT || res2.getStatusCode() == HttpStatus.CONFLICT;

        assertTrue(oneCreated, "One request should succeed");
        assertTrue(oneConflict, "One request should return 409 Conflict");
        
        // Ensure controller was executed exactly once
        assertEquals(1, paymentController.getProcessCount());
    }

    @Test
    void testRequestWithoutKeyPassesThrough() {
        PaymentController.PaymentRequest request = new PaymentController.PaymentRequest();
        request.accountId = "acc-123";
        request.amount = 100.50;

        // No Idempotency-Key header
        HttpEntity<PaymentController.PaymentRequest> entity = new HttpEntity<>(request);

        ResponseEntity<PaymentController.PaymentResponse> response1 = restTemplate.exchange(
                "/api/payments", HttpMethod.POST, entity, PaymentController.PaymentResponse.class);

        assertEquals(HttpStatus.CREATED, response1.getStatusCode());
        assertEquals(1, paymentController.getProcessCount());

        // Second request also passes through and increments
        ResponseEntity<PaymentController.PaymentResponse> response2 = restTemplate.exchange(
                "/api/payments", HttpMethod.POST, entity, PaymentController.PaymentResponse.class);

        assertEquals(HttpStatus.CREATED, response2.getStatusCode());
        assertEquals(2, paymentController.getProcessCount());
        
        assertNotEquals(response1.getBody().transactionId, response2.getBody().transactionId);
    }
}
