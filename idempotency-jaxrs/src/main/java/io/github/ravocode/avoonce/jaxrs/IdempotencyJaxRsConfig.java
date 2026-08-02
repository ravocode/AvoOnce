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

    /**
     * Creates a new configuration instance with the default values.
     */
    public IdempotencyJaxRsConfig() {
    }

    private String headerName = "Idempotency-Key";
    private boolean hashBody = true;
    private boolean enforce = false;

    // ---- Fluent setters ------------------------------------------------------

    /**
     * Sets the HTTP header used to transport the idempotency key.
     *
     * @param headerName the header name to use
     * @return this config instance for fluent chaining
     */
    public IdempotencyJaxRsConfig setHeaderName(final String headerName) {
        this.headerName = headerName;
        return this;
    }

    /**
     * Enables or disables hashing the request body for payload mismatch detection.
     *
     * @param hashBody {@code true} to hash request bodies
     * @return this config instance for fluent chaining
     */
    public IdempotencyJaxRsConfig setHashBody(final boolean hashBody) {
        this.hashBody = hashBody;
        return this;
    }

    /**
     * Enables or disables strict rejection of requests that omit the idempotency header.
     *
     * @param enforce {@code true} to reject missing headers
     * @return this config instance for fluent chaining
     */
    public IdempotencyJaxRsConfig setEnforce(final boolean enforce) {
        this.enforce = enforce;
        return this;
    }

    // ---- Getters -------------------------------------------------------------

    /**
     * Returns the configured request header name.
     *
     * @return the header name
     */
    public String getHeaderName() {
        return headerName;
    }

    /**
     * Returns whether request bodies are hashed for payload mismatch detection.
     *
     * @return {@code true} when request bodies are hashed
     */
    public boolean isHashBody() {
        return hashBody;
    }

    /**
     * Returns whether missing idempotency headers are rejected.
     *
     * @return {@code true} when missing headers are rejected
     */
    public boolean isEnforce() {
        return enforce;
    }
}
