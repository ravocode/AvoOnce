package io.github.ravocode.avoonce.jaxrs;

import io.github.ravocode.avoonce.core.domain.IdempotencyRecord;
import io.github.ravocode.avoonce.core.domain.IdempotencyResponse;
import io.github.ravocode.avoonce.core.exception.IdempotencyConflictException;
import io.github.ravocode.avoonce.core.exception.IdempotencyMismatchException;
import io.github.ravocode.avoonce.core.hash.RequestHasher;
import io.github.ravocode.avoonce.core.hash.Sha256RequestHasher;
import io.github.ravocode.avoonce.core.spi.IdempotencyRepository;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JAX-RS {@link ContainerRequestFilter}, {@link ContainerResponseFilter}, and
 * {@link WriterInterceptor} that provides zero-code idempotency protection for
 * any JAX-RS resource.
 *
 * <p>This filter works with any JAX-RS 3.1+ runtime (Jersey, RESTEasy, CXF, Quarkus).
 * It intercepts requests, validates or acquires idempotency keys, captures the serialized
 * response body via {@link WriterInterceptor}, and replays cached responses on retry.
 *
 * <h2>Registration (Jersey example)</h2>
 * <pre>{@code
 * IdempotencyRepository repo = new CaffeineIdempotencyRepository(config);
 * resourceConfig.register(new IdempotencyContainerFilter(repo));
 * }</pre>
 *
 * <h2>Registration (Quarkus CDI example)</h2>
 * <pre>{@code
 * @ApplicationScoped
 * public class IdempotencyFilterProducer {
 *     @Produces
 *     @Singleton
 *     public IdempotencyContainerFilter idempotencyFilter(IdempotencyRepository repo) {
 *         return new IdempotencyContainerFilter(repo);
 *     }
 * }
 * }</pre>
 *
 * @see IdempotencyJaxRsConfig
 * @see Idempotent
 */
@Provider
@Idempotent
@Priority(Priorities.HEADER_DECORATOR)
public class IdempotencyContainerFilter implements ContainerRequestFilter, ContainerResponseFilter, WriterInterceptor {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyContainerFilter.class);

    static final String PROP_KEY = "avoonce.idempotency.key";
    static final String PROP_ABORTED = "avoonce.idempotency.aborted";
    static final String PROP_STATUS = "avoonce.idempotency.status";
    static final String PROP_HEADERS = "avoonce.idempotency.headers";

    private final IdempotencyRepository repository;
    private final IdempotencyJaxRsConfig config;
    private final RequestHasher hasher;
    private boolean nameBinding = false;

    /**
     * Protected no-arg constructor for CDI proxying and frameworks.
     */
    protected IdempotencyContainerFilter() {
        this(null, new IdempotencyJaxRsConfig());
    }

    /**
     * Creates a filter with default configuration.
     * In CDI / Jakarta EE environments, the {@link IdempotencyRepository}
     * is injected automatically.
     *
     * @param repository the backing store for idempotency records.
     */
    @jakarta.inject.Inject
    public IdempotencyContainerFilter(final IdempotencyRepository repository) {
        this(repository, new IdempotencyJaxRsConfig());
    }

    /**
     * Creates a filter with custom configuration.
     *
     * @param repository the backing store for idempotency records.
     * @param config     filter configuration (header name, body hashing, etc.).
     */
    public IdempotencyContainerFilter(final IdempotencyRepository repository,
                                       final IdempotencyJaxRsConfig config) {
        this.repository = repository;
        this.config = config != null ? config : new IdempotencyJaxRsConfig();
        this.hasher = new Sha256RequestHasher();
    }

    /**
     * Marks this filter instance for name-binding via {@link Idempotent}.
     *
     * @return this filter instance for fluent chaining.
     */
    public IdempotencyContainerFilter withNameBinding() {
        this.nameBinding = true;
        return this;
    }

    /**
     * Checks if this filter instance is configured for name-binding.
     *
     * @return {@code true} if name-binding is enabled, {@code false} otherwise.
     */
    public boolean isNameBinding() {
        return nameBinding;
    }

    // -------------------------------------------------------------------------
    // Request Phase
    // -------------------------------------------------------------------------

    @Override
    public void filter(final ContainerRequestContext requestContext) throws IOException {
        final String key = requestContext.getHeaderString(config.getHeaderName());

        if (key == null || key.isBlank()) {
            if (config.isEnforce()) {
                log.debug("[idempotency] Missing required header '{}', rejecting request",
                        config.getHeaderName());
                requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST)
                        .entity("Missing required " + config.getHeaderName() + " header")
                        .type(MediaType.TEXT_PLAIN_TYPE)
                        .build());
                requestContext.setProperty(PROP_ABORTED, Boolean.TRUE);
            }
            return;
        }

        // Buffer the request body for hashing and allow downstream resource methods to re-read it.
        byte[] body = new byte[0];
        final InputStream entityStream = requestContext.getEntityStream();
        if (entityStream != null) {
            body = entityStream.readAllBytes();
            requestContext.setEntityStream(new ByteArrayInputStream(body));
        }

        final String hash = config.isHashBody() ? hasher.hash(body) : null;

        try {
            final Optional<IdempotencyRecord> existing = (hash != null)
                    ? repository.acquireOrGet(key, hash)
                    : repository.acquireOrGet(key);

            if (existing.isPresent()) {
                // Replay cached response
                log.debug("[idempotency] Replaying cached response for key='{}'", key);
                requestContext.setProperty(PROP_ABORTED, Boolean.TRUE);
                requestContext.abortWith(buildCachedResponse(existing.get().getResponse()));
                return;
            }

            // Lock acquired — let request proceed
            log.debug("[idempotency] Lock acquired for key='{}'", key);
            requestContext.setProperty(PROP_KEY, key);

        } catch (final IdempotencyConflictException e) {
            log.warn("[idempotency] Conflict for key='{}': returning HTTP 409", key);
            requestContext.setProperty(PROP_ABORTED, Boolean.TRUE);
            requestContext.abortWith(Response.status(Response.Status.CONFLICT)
                    .entity("Idempotency conflict: request is already in progress")
                    .type(MediaType.TEXT_PLAIN_TYPE)
                    .build());
        } catch (final IdempotencyMismatchException e) {
            log.warn("[idempotency] Payload mismatch for key='{}': returning HTTP 422", key);
            requestContext.setProperty(PROP_ABORTED, Boolean.TRUE);
            requestContext.abortWith(Response.status(422)
                    .entity("Idempotency mismatch: key reused with different payload")
                    .type(MediaType.TEXT_PLAIN_TYPE)
                    .build());
        }
    }

    // -------------------------------------------------------------------------
    // Response Phase
    // -------------------------------------------------------------------------

    @Override
    public void filter(final ContainerRequestContext requestContext,
                       final ContainerResponseContext responseContext) throws IOException {

        if (Boolean.TRUE.equals(requestContext.getProperty(PROP_ABORTED))) {
            return;
        }

        final String key = (String) requestContext.getProperty(PROP_KEY);
        if (key == null) {
            return;
        }

        final int status = responseContext.getStatus();

        // 5xx — mark as FAILED so client can retry
        if (status >= 500) {
            log.warn("[idempotency] Server error (status={}) for key='{}', marking as FAILED", status, key);
            requestContext.setProperty(PROP_ABORTED, Boolean.TRUE);
            repository.saveFailure(key, "Server error during processing (status " + status + ")");
            return;
        }

        // Capture response headers
        final Map<String, List<String>> headers = new HashMap<>();
        for (final Map.Entry<String, List<Object>> entry : responseContext.getHeaders().entrySet()) {
            final List<String> values = new ArrayList<>();
            for (final Object v : entry.getValue()) {
                values.add(v.toString());
            }
            headers.put(entry.getKey(), values);
        }

        final MediaType mediaType = responseContext.getMediaType();
        if (mediaType != null) {
            headers.put("Content-Type", List.of(mediaType.toString()));
        }

        requestContext.setProperty(PROP_STATUS, status);
        requestContext.setProperty(PROP_HEADERS, headers);

        // If there's no entity to write, save immediately
        if (!responseContext.hasEntity()) {
            final IdempotencyResponse idemResponse = new IdempotencyResponse(status, headers, new byte[0]);
            repository.saveSuccess(key, idemResponse);
            log.debug("[idempotency] Response (no entity) cached for key='{}'", key);
        }
    }

    // -------------------------------------------------------------------------
    // Writer Interceptor (captures serialized response bytes)
    // -------------------------------------------------------------------------

    @Override
    public void aroundWriteTo(final WriterInterceptorContext context) throws IOException {
        final Object aborted = context.getProperty(PROP_ABORTED);
        final String key = (String) context.getProperty(PROP_KEY);

        if (Boolean.TRUE.equals(aborted) || key == null) {
            context.proceed();
            return;
        }

        final Integer status = (Integer) context.getProperty(PROP_STATUS);
        if (status != null && status >= 500) {
            context.proceed();
            return;
        }

        final OutputStream originalStream = context.getOutputStream();
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        // Tee output to both original stream and our byte buffer
        context.setOutputStream(new TeeOutputStream(originalStream, buffer));

        try {
            context.proceed();
        } finally {
            context.setOutputStream(originalStream);
        }

        final byte[] bodyBytes = buffer.toByteArray();
        @SuppressWarnings("unchecked")
        final Map<String, List<String>> headers = (Map<String, List<String>>) context.getProperty(PROP_HEADERS);

        final int finalStatus = (status != null) ? status : 200;
        final Map<String, List<String>> finalHeaders = (headers != null) ? headers : Map.of();

        final IdempotencyResponse idemResponse = new IdempotencyResponse(finalStatus, finalHeaders, bodyBytes);
        repository.saveSuccess(key, idemResponse);
        log.debug("[idempotency] Serialized response body cached for key='{}' ({} bytes)", key, bodyBytes.length);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Response buildCachedResponse(final IdempotencyResponse cached) {
        final Response.ResponseBuilder builder = Response.status(cached.getStatusCode());

        String contentType = MediaType.APPLICATION_JSON;
        if (cached.getHeaders() != null && cached.getHeaders().containsKey("Content-Type")) {
            final List<String> ctValues = cached.getHeaders().get("Content-Type");
            if (ctValues != null && !ctValues.isEmpty()) {
                contentType = ctValues.get(0);
            }
        }
        builder.type(contentType);

        if (cached.getHeaders() != null) {
            cached.getHeaders().forEach((name, values) -> {
                if (name.equalsIgnoreCase("Content-Type")
                        || name.equalsIgnoreCase("Content-Length")
                        || name.equalsIgnoreCase("Transfer-Encoding")) {
                    return;
                }
                for (final String value : values) {
                    builder.header(name, value);
                }
            });
        }

        if (cached.getBody() != null) {
            builder.entity(cached.getBody());
        }

        return builder.build();
    }

    private static class TeeOutputStream extends OutputStream {
        private final OutputStream out1;
        private final OutputStream out2;

        TeeOutputStream(final OutputStream out1, final OutputStream out2) {
            this.out1 = out1;
            this.out2 = out2;
        }

        @Override
        public void write(final int b) throws IOException {
            out1.write(b);
            out2.write(b);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            out1.write(b, off, len);
            out2.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            out1.flush();
            out2.flush();
        }

        @Override
        public void close() throws IOException {
            out1.close();
            out2.close();
        }
    }
}
