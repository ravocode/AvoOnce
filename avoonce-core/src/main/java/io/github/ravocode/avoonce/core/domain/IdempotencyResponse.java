package io.github.ravocode.avoonce.core.domain;

import java.util.List;
import java.util.Map;

/**
 * Immutable value object encapsulating the cached HTTP response for a
 * successfully
 * processed idempotent request.
 *
 * <p>
 * When an idempotency record transitions to
 * {@link IdempotencyStatus#COMPLETED},
 * the response is stored in this object so that subsequent retries with the
 * same key
 * receive an exact replay of the original response — including status code,
 * headers,
 * and raw body bytes.
 */
public class IdempotencyResponse {

    /** The HTTP status code (e.g. 200, 201). */
    private final int statusCode;

    /** The captured HTTP response headers, as a multi-valued map. */
    private final Map<String, List<String>> headers;

    /** The raw serialized response body bytes. */
    private final byte[] body;

    /**
     * Creates a cached response snapshot for an idempotent request.
     *
     * @param statusCode the HTTP status code
     * @param headers    the response headers as a multi-valued map
     * @param body       the raw response body bytes
     */

    /**
     * Constructs an {@code IdempotencyResponse} capturing the full HTTP response.
     *
     * @param statusCode the HTTP status code.
     * @param headers    the response headers as a multi-valued map.
     * @param body       the raw response body bytes.
     */
    public IdempotencyResponse(int statusCode, Map<String, List<String>> headers, byte[] body) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
    }

    /**
     * Returns the HTTP status code of the cached response.
     *
     * @return the HTTP status code (e.g. 200, 201, 204).
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Returns the captured HTTP response headers.
     *
     * @return a multi-valued map of header names to header value lists.
     */
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    /**
     * Returns the raw serialized response body bytes.
     *
     * @return the response body byte array; may be {@code null} or empty for
     *         bodyless responses.
     */
    public byte[] getBody() {
        return body;
    }
}