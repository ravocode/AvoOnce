package io.github.ravocode.avoonce.sample;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.ravocode.avoonce.spring.annotation.Idempotent;

/**
 * Sample REST controller demonstrating selective idempotency protection for payment endpoints.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final AtomicInteger processCount = new AtomicInteger(0);

    /**
     * Incoming payment request payload used by the controller.
     */
    public static class PaymentRequest {
        /** The account identifier for the payment. */
        public String accountId;
        /** The payment amount to process. */
        public double amount;
    }

    /**
     * Response payload returned after processing a payment request.
     */
    public static class PaymentResponse {
        /** The generated transaction identifier. */
        public String transactionId;
        /** The processing status for the payment. */
        public String status;
        /** The number of processing attempts observed so far. */
        public int processedAttempts;

        /**
         * Default constructor for serialization frameworks.
         */
        public PaymentResponse() {
        }

        /**
         * Creates a payment response with the supplied outcome details.
         *
         * @param transactionId the generated transaction identifier
         * @param status        the processing status
         * @param processedAttempts the number of attempts seen so far
         */
        public PaymentResponse(String transactionId, String status, int processedAttempts) {
            this.transactionId = transactionId;
            this.status = status;
            this.processedAttempts = processedAttempts;
        }
    }

    /**
     * Protected endpoint: annotated with {@link Idempotent}.
     * Deduplicated and cached in both GLOBAL mode and ANNOTATION mode.
     *
     * @param request the payment request payload
     * @return a response entity containing the processing outcome
     */
    @PostMapping
    @Idempotent
    public ResponseEntity<PaymentResponse> processPayment(final @RequestBody PaymentRequest request) {
        if (request.amount < 0) {
            return ResponseEntity.badRequest()
                    .body(new PaymentResponse(null, "INVALID_AMOUNT", processCount.incrementAndGet()));
        }

        // Simulate some processing time
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int count = processCount.incrementAndGet();
        PaymentResponse response = new PaymentResponse(UUID.randomUUID().toString(), "SUCCESS", count);

        return ResponseEntity.status(201)
                .header("X-Payment-Processed", "true")
                .body(response);
    }

    /**
     * Unprotected endpoint: NOT annotated with {@link Idempotent}.
     * In ANNOTATION mode, requests to this endpoint bypass the idempotency filter.
     *
     * @param request the payment request payload
     * @return a response entity containing the processing outcome
     */
    @PostMapping("/unprotected")
    public ResponseEntity<PaymentResponse> processUnprotectedPayment(final @RequestBody PaymentRequest request) {
        int count = processCount.incrementAndGet();
        PaymentResponse response = new PaymentResponse(UUID.randomUUID().toString(), "UNPROTECTED", count);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the number of processed payment requests seen by this sample controller.
     *
     * @return the current process count
     */
    @GetMapping("/count")
    public int getProcessCount() {
        return processCount.get();
    }

    /**
     * Resets the sample controller's processing counter.
     *
     * @return an empty success response
     */
    @PostMapping("/reset")
    public ResponseEntity<Void> resetCount() {
        processCount.set(0);
        return ResponseEntity.ok().build();
    }
}
