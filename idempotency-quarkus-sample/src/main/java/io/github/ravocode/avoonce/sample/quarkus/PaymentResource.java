package io.github.ravocode.avoonce.sample.quarkus;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.ravocode.avoonce.jaxrs.Idempotent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Sample JAX-RS resource in Quarkus demonstrating selective idempotency
 * protection using the {@link Idempotent} name-binding annotation.
 */
@Path("/api/payments")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class PaymentResource {

    private final AtomicInteger processCount = new AtomicInteger(0);

    /**
     * Incoming payment request payload used by the Quarkus resource.
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
     * Requests with an Idempotency-Key header are deduplicated, locked, and cached.
     *
     * @param request the payment request payload
     * @return a response containing the processing outcome
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
     *
     * @param request the payment request payload
     * @return a response containing the processing outcome
     */
    @POST
    @Path("/unprotected")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response processUnprotectedPayment(final PaymentRequest request) {
        int count = processCount.incrementAndGet();
        PaymentResponse response = new PaymentResponse(UUID.randomUUID().toString(), "UNPROTECTED", count);
        return Response.ok(response).build();
    }

    /**
     * Response payload used for the process-count endpoint.
     */
    public static class CountResponse {
        /** The current process count. */
        public int count;

        /**
         * Default constructor for serialization frameworks.
         */
        public CountResponse() {
        }

        /**
         * Creates a count response with the supplied value.
         *
         * @param count the current process count
         */
        public CountResponse(int count) {
            this.count = count;
        }
    }

    /**
     * Returns the current number of processed payment requests.
     *
     * @return a response containing the process count
     */
    @GET
    @Path("/count")
    public Response getProcessCount() {
        return Response.ok(new CountResponse(processCount.get())).build();
    }

    /**
     * Resets the sample resource's processing counter.
     *
     * @return an empty success response
     */
    @POST
    @Path("/reset")
    public Response resetCount() {
        processCount.set(0);
        return Response.ok().build();
    }
}
