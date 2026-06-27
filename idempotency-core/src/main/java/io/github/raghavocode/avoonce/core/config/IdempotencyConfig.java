package io.github.raghavocode.avoonce.core.config;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for the idempotency layer.
 */
public class IdempotencyConfig {

    private final long ttl;
    private final TimeUnit unit;
    private final long lockTimeout;
    private final TimeUnit lockTimeoutUnit;

    /**
     * Default constructor with a 1-hour TTL, configurable via environment variables.
     * AVOONCE_IDEMPOTENCY_TTL: The time-to-live value (default: 1)
     * AVOONCE_IDEMPOTENCY_TIMEUNIT: The time unit for the TTL (default: HOURS)
     * AVOONCE_IDEMPOTENCY_LOCK_TIMEOUT: The lock timeout value (default: 2)
     * AVOONCE_IDEMPOTENCY_LOCK_TIMEUNIT: The time unit for the lock timeout (default: MINUTES)
     */
    public IdempotencyConfig() {
        this(
            Long.parseLong(System.getenv().getOrDefault("AVOONCE_IDEMPOTENCY_TTL", "1")),
            TimeUnit.valueOf(System.getenv().getOrDefault("AVOONCE_IDEMPOTENCY_TIMEUNIT", "HOURS")),
            Long.parseLong(System.getenv().getOrDefault("AVOONCE_IDEMPOTENCY_LOCK_TIMEOUT", "2")),
            TimeUnit.valueOf(System.getenv().getOrDefault("AVOONCE_IDEMPOTENCY_LOCK_TIMEUNIT", "MINUTES"))
        );
    }

    public IdempotencyConfig(long ttl, TimeUnit unit) {
        this(ttl, unit, 2, TimeUnit.MINUTES);
    }

    public IdempotencyConfig(long ttl, TimeUnit unit, long lockTimeout, TimeUnit lockTimeoutUnit) {
        this.ttl = ttl;
        this.unit = unit;
        this.lockTimeout = lockTimeout;
        this.lockTimeoutUnit = lockTimeoutUnit;
    }

    public long getTtl() {
        return ttl;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    public long getLockTimeout() {
        return lockTimeout;
    }

    public TimeUnit getLockTimeoutUnit() {
        return lockTimeoutUnit;
    }
}