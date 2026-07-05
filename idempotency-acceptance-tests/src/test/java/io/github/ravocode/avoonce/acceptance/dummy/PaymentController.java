package io.github.ravocode.avoonce.acceptance.dummy;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final AtomicInteger processCount = new AtomicInteger(0);

    public static class PaymentRequest {
        public String accountId;
        public double amount;
    }

    public static class PaymentResponse {
        public String transactionId;
        public String status;
        public int processedAttempts;

        public PaymentResponse() {}

        public PaymentResponse(String transactionId, String status, int processedAttempts) {
            this.transactionId = transactionId;
            this.status = status;
            this.processedAttempts = processedAttempts;
        }
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(@RequestBody PaymentRequest request) {
        if (request.amount < 0) {
            return ResponseEntity.badRequest().body(new PaymentResponse(null, "INVALID_AMOUNT", processCount.incrementAndGet()));
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

    public int getProcessCount() {
        return processCount.get();
    }
    
    public void resetCount() {
        processCount.set(0);
    }
}
