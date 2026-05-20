package com.raghavocode.avoonce.core.config;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for the idempotency layer.
 */
public class IdempotencyConfig {

    private final long ttl;
    private final TimeUnit unit;

    /**
     * Default constructor with a 1-hour TTL, configurable via environment variables.
     * AVOONCE_IDEMPOTENCY_TTL: The time-to-live value (default: 1)
     * AVOONCE_IDEMPOTENCY_TIMEUNIT: The time unit for the TTL (default: HOURS)
     */
    public IdempotencyConfig() {
        this(
            Long.parseLong(System.getenv().getOrDefault("AVOONCE_IDEMPOTENCY_TTL", "1")),
            TimeUnit.valueOf(System.getenv().getOrDefault("AVOONCE_IDEMPOTENCY_TIMEUNIT", "HOURS"))
        );
    }

    public IdempotencyConfig(long ttl, TimeUnit unit) {
        this.ttl = ttl;
        this.unit = unit;
    }

    public long getTtl() {
        return ttl;
    }

    public TimeUnit getUnit() {
        return unit;
    }
}