package io.github.ravocode.avoonce.sample.quarkus;

import io.github.ravocode.avoonce.jaxrs.Idempotent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sample JAX-RS resource in Quarkus demonstrating selective idempotency
 * protection using the {@link Idempotent} name-binding annotation.
 */
@Path("/api/payments")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class PaymentResource {

    private final AtomicInteger processCount = new AtomicInteger(0);

    public static class PaymentRequest {
        public String accountId;
        public double amount;
    }

    public static class PaymentResponse {
        public String transactionId;
        public String status;
        public int processedAttempts;

        public PaymentResponse() {
        }

        public PaymentResponse(String transactionId, String status, int processedAttempts) {
            this.transactionId = transactionId;
            this.status = status;
            this.processedAttempts = processedAttempts;
        }
    }

    /**
     * Protected endpoint: annotated with {@link Idempotent}.
     * Requests with an Idempotency-Key header are deduplicated, locked, and cached.
     */
    @POST
    @Idempotent
    @Consumes(MediaType.APPLICATION_JSON)
    public Response processPayment(final PaymentRequest request) {
        if (request == null || request.amount < 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new PaymentResponse(null, "INVALID_AMOUNT", processCount.incrementAndGet()))
                    .build();
        }

        // Simulate work
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int count = processCount.incrementAndGet();
        PaymentResponse response = new PaymentResponse(UUID.randomUUID().toString(), "SUCCESS", count);

        return Response.status(Response.Status.CREATED)
                .header("X-Payment-Processed", "true")
                .entity(response)
                .build();
    }

    /**
     * Unprotected endpoint: NOT annotated with {@link Idempotent}.
     * Even if an Idempotency-Key header is provided, this endpoint is NOT intercepted by the filter.
     */
    @POST
    @Path("/unprotected")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response processUnprotectedPayment(final PaymentRequest request) {
        int count = processCount.incrementAndGet();
        PaymentResponse response = new PaymentResponse(UUID.randomUUID().toString(), "UNPROTECTED", count);
        return Response.ok(response).build();
    }

    public static class CountResponse {
        public int count;

        public CountResponse() {
        }

        public CountResponse(int count) {
            this.count = count;
        }
    }

    @GET
    @Path("/count")
    public Response getProcessCount() {
        return Response.ok(new CountResponse(processCount.get())).build();
    }

    @POST
    @Path("/reset")
    public Response resetCount() {
        processCount.set(0);
        return Response.ok().build();
    }
}
