package io.github.ravocode.avoonce.jaxrs;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JAX-RS {@link NameBinding} annotation for selective idempotency protection.
 *
 * <p>When the {@link IdempotencyContainerFilter} is registered with
 * {@code nameBinding = true}, only resource methods or classes annotated with
 * {@code @Idempotent} will be intercepted. All other endpoints are left
 * unprotected.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @POST
 * @Path("/payments")
 * @Idempotent
 * public Response createPayment(PaymentRequest request) {
 *     // This endpoint is idempotency-protected
 * }
 *
 * @POST
 * @Path("/logs")
 * public Response createLog(LogEntry entry) {
 *     // This endpoint is NOT idempotency-protected
 * }
 * }</pre>
 *
 * @see IdempotencyContainerFilter
 */
@NameBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
}
