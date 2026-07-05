package io.github.ravocode.avoonce.core.domain;

import java.util.List;
import java.util.Map;

/**
 * Encapsulates the cached HTTP response for a successfully processed idempotent request.
 */
public class IdempotencyResponse {
    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final byte[] body;

    public IdempotencyResponse(int statusCode, Map<String, List<String>> headers, byte[] body) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }
}