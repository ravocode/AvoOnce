package io.github.ravocode.avoonce.sample;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class BaseSampleIntegrationTest {

    @Autowired
    protected TestRestTemplate restTemplate;

    @Test
    void testBasicIdempotencyWorks() {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentController.PaymentRequest request = new PaymentController.PaymentRequest();
        request.accountId = "sample-acc-999";
        request.amount = 50.00;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        HttpEntity<PaymentController.PaymentRequest> entity = new HttpEntity<>(request, headers);

        // 1. Initial Request
        ResponseEntity<PaymentController.PaymentResponse> response1 = restTemplate.exchange(
                "/api/payments", HttpMethod.POST, entity, PaymentController.PaymentResponse.class);

        assertEquals(HttpStatus.CREATED, response1.getStatusCode());
        assertNotNull(response1.getBody());
        String transactionId = response1.getBody().transactionId;

        // 2. Retry with same idempotency key
        ResponseEntity<PaymentController.PaymentResponse> response2 = restTemplate.exchange(
                "/api/payments", HttpMethod.POST, entity, PaymentController.PaymentResponse.class);

        assertEquals(HttpStatus.CREATED, response2.getStatusCode());
        assertNotNull(response2.getBody());

        // Assert it returned the exact same cached response (same transaction ID)
        assertEquals(transactionId, response2.getBody().transactionId);
    }
}
