package io.github.ravocode.avoonce.jaxrs;

/**
 * Framework-agnostic configuration for the JAX-RS idempotency filter.
 *
 * <p>This is a simple POJO with no dependency on CDI, Spring, or any other
 * framework. Users create an instance and pass it to the
 * {@link IdempotencyContainerFilter} constructor.
 *
 * <pre>{@code
 * IdempotencyJaxRsConfig config = new IdempotencyJaxRsConfig()
 *         .setHeaderName("Idempotency-Key")
 *         .setHashBody(true)
 *         .setEnforce(false);
 * }</pre>
 */
public class IdempotencyJaxRsConfig {

    private String headerName = "Idempotency-Key";
    private boolean hashBody = true;
    private boolean enforce = false;

    // ---- Fluent setters ------------------------------------------------------

    public IdempotencyJaxRsConfig setHeaderName(final String headerName) {
        this.headerName = headerName;
        return this;
    }

    public IdempotencyJaxRsConfig setHashBody(final boolean hashBody) {
        this.hashBody = hashBody;
        return this;
    }

    public IdempotencyJaxRsConfig setEnforce(final boolean enforce) {
        this.enforce = enforce;
        return this;
    }

    // ---- Getters -------------------------------------------------------------

    public String getHeaderName() {
        return headerName;
    }

    public boolean isHashBody() {
        return hashBody;
    }

    public boolean isEnforce() {
        return enforce;
    }
}
