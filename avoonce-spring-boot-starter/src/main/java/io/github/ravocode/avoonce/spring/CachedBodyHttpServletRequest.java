package io.github.ravocode.avoonce.spring;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import org.springframework.util.StreamUtils;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * An {@link HttpServletRequestWrapper} that caches the HTTP request body in memory,
 * allowing it to be read multiple times (e.g. for SHA-256 hashing and downstream controller processing).
 */
public final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    /** The cached raw byte array of the request body. */
    private final byte[] cachedBody;

    /**
     * Constructs a {@code CachedBodyHttpServletRequest} by reading and caching the input stream of the given request.
     *
     * @param request the original HTTP servlet request.
     * @throws IOException if an I/O error occurs while reading the request body.
     */
    public CachedBodyHttpServletRequest(final HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    /**
     * Returns the cached byte array of the request body.
     *
     * @return the raw request body bytes.
     */
    public byte[] getCachedBody() {
        return cachedBody;
    }

    /**
     * Returns a servlet input stream backed by the cached request body.
     *
     * @return a reusable input stream over the cached bytes
     */
    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(this.cachedBody);
    }

    /**
     * Returns a buffered reader over the cached request body.
     *
     * @return a reader over the cached bytes
     */
    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(this.cachedBody)));
    }

    /**
     * Internal servlet input stream that reads from the in-memory cache.
     */
    private static class CachedBodyServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream buffer;

        /**
         * Creates a servlet input stream over the supplied cached bytes.
         *
         * @param contents the cached request body bytes
         */
        public CachedBodyServletInputStream(final byte[] contents) {
            this.buffer = new ByteArrayInputStream(contents);
        }

        @Override
        public int read() {
            return buffer.read();
        }

        @Override
        public boolean isFinished() {
            return buffer.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(final ReadListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
