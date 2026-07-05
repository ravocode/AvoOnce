package io.github.ravocode.avoonce.spring;

import io.github.ravocode.avoonce.core.IdempotencyManager;
import io.github.ravocode.avoonce.core.domain.IdempotencyResponse;
import io.github.ravocode.avoonce.core.exception.IdempotencyConflictException;
import io.github.ravocode.avoonce.core.exception.IdempotencyMismatchException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);

    private final IdempotencyManager manager;
    private final IdempotencyProperties properties;

    public IdempotencyFilter(final IdempotencyManager manager, final IdempotencyProperties properties) {
        this.manager = manager;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {

        final String key = request.getHeader(properties.getHeaderName());

        if (key == null) {
            if (properties.isEnforce()) {
                log.debug("[idempotency] Missing required header '{}', rejecting request",
                        properties.getHeaderName());
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Missing required " + properties.getHeaderName() + " header");
                return;
            }
            log.debug("[idempotency] No idempotency key present, bypassing filter");
            filterChain.doFilter(request, response);
            return;
        }

        final CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        final ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);

        try {
            final IdempotencyResponse idemResponse = manager.execute(key,
                    properties.isHashBody() ? cachedRequest.getCachedBody() : null,
                    () -> {
                        filterChain.doFilter(cachedRequest, cachedResponse);

                        int status = cachedResponse.getStatus();
                        if (status >= 500) {
                            throw new RuntimeException("Server error during processing (status " + status
                                    + "), failing idempotency record to allow retry");
                        }

                        byte[] bodyBytes = cachedResponse.getContentAsByteArray();
                        Map<String, List<String>> headers = new HashMap<>();
                        for (String headerName : cachedResponse.getHeaderNames()) {
                            headers.put(headerName, new ArrayList<>(cachedResponse.getHeaders(headerName)));
                        }

                        String ct = cachedResponse.getContentType();
                        if (ct != null) {
                            headers.put("Content-Type", Collections.singletonList(ct));
                        }

                        return new IdempotencyResponse(status, headers, bodyBytes);
                    });

            if (!response.isCommitted()) {
                response.setStatus(idemResponse.getStatusCode());

                String ct = "application/json";
                if (idemResponse.getHeaders().containsKey("Content-Type")
                        && !idemResponse.getHeaders().get("Content-Type").isEmpty()) {
                    ct = idemResponse.getHeaders().get("Content-Type").get(0);
                }
                response.setHeader("Content-Type", ct);

                idemResponse.getHeaders().forEach((headerName, values) -> {
                    if (headerName.equalsIgnoreCase("Transfer-Encoding")
                            || headerName.equalsIgnoreCase("Content-Length")
                            || headerName.equalsIgnoreCase("Content-Type")) {
                        return;
                    }
                    for (String value : values) {
                        response.addHeader(headerName, value);
                    }
                });

                if (idemResponse.getBody() != null) {
                    response.setContentLength(idemResponse.getBody().length);
                    response.getOutputStream().write(idemResponse.getBody());
                }
                response.flushBuffer();
            }

        } catch (final IdempotencyConflictException e) {
            log.warn("[idempotency] Conflict for key='{}': returning HTTP 409", key);
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.getWriter().write("Idempotency conflict: request is already in progress");
        } catch (final IdempotencyMismatchException e) {
            log.warn("[idempotency] Payload mismatch for key='{}': returning HTTP 422", key);
            response.setStatus(422); // Unprocessable Entity
            response.getWriter().write("Idempotency mismatch: key reused with different payload");
        } catch (final ServletException | IOException e) {
            throw e;
        } catch (final Exception e) {
            throw new ServletException(e);
        }
    }
}
