package io.github.ravocode.avoonce.core.config;

import java.util.concurrent.TimeUnit;

/**
 * Immutable configuration for the AvoOnce idempotency layer.
 *
 * <p>Controls the response cache TTL and the maximum lock-hold duration before
 * an in-progress request is considered stale and its lock can be reclaimed.
 *
 * <p>In non-Spring environments this class is constructed directly. In Spring Boot,
 * it is wired automatically from {@code IdempotencyProperties} via auto-configuration.
 */
public class IdempotencyConfig {

    /** The duration value for idempotency response caching. */
    private final long ttl;

    /** The time unit for the {@link #ttl} value. */
    private final TimeUnit unit;

    /** The maximum duration a STARTED lock is considered valid before it can be reclaimed. */
    private final long lockTimeout;

    /** The time unit for the {@link #lockTimeout} value. */
    private final TimeUnit lockTimeoutUnit;

    /**
     * Creates an {@code IdempotencyConfig} from the environment variables.
     */

    /**
     * Default constructor with configurable environment variable overrides.
     *
     * <p>Reads the following environment variables (falling back to safe defaults):
     * <ul>
     *   <li>{@code AVOONCE_IDEMPOTENCY_TTL} — TTL value (default: {@code 1})</li>
     *   <li>{@code AVOONCE_IDEMPOTENCY_TIMEUNIT} — TTL unit (default: {@code HOURS})</li>
     *   <li>{@code AVOONCE_IDEMPOTENCY_LOCK_TIMEOUT} — lock timeout value (default: {@code 2})</li>
     *   <li>{@code AVOONCE_IDEMPOTENCY_LOCK_TIMEUNIT} — lock timeout unit (default: {@code MINUTES})</li>
     * </ul>
     */
    public IdempotencyConfig() {
        this(
            Long.parseLong(System.getenv().getOrDefault("AVOONCE_IDEMPOTENCY_TTL", "1")),
            TimeUnit.valueOf(System.getenv().getOrDefault("AVOONCE_IDEMPOTENCY_TIMEUNIT", "HOURS")),
            Long.parseLong(System.getenv().getOrDefault("AVOONCE_IDEMPOTENCY_LOCK_TIMEOUT", "2")),
            TimeUnit.valueOf(System.getenv().getOrDefault("AVOONCE_IDEMPOTENCY_LOCK_TIMEUNIT", "MINUTES"))
        );
    }

    /**
     * Constructs an {@code IdempotencyConfig} with the given TTL and default lock timeout (2 minutes).
     *
     * @param ttl  the cache duration value.
     * @param unit the time unit for the TTL.
     */
    public IdempotencyConfig(long ttl, TimeUnit unit) {
        this(ttl, unit, 2, TimeUnit.MINUTES);
    }

    /**
     * Constructs an {@code IdempotencyConfig} with explicit TTL and lock-timeout settings.
     *
     * @param ttl             the cache duration value.
     * @param unit            the time unit for the TTL.
     * @param lockTimeout     the maximum duration a STARTED lock is valid.
     * @param lockTimeoutUnit the time unit for {@code lockTimeout}.
     */
    /**
     * Constructs an {@code IdempotencyConfig} with explicit TTL and lock-timeout settings.
     *
     * @param ttl             the cache duration value
     * @param unit            the time unit for the TTL
     * @param lockTimeout     the maximum duration a STARTED lock is valid
     * @param lockTimeoutUnit the time unit for {@code lockTimeout}
     */
    public IdempotencyConfig(long ttl, TimeUnit unit, long lockTimeout, TimeUnit lockTimeoutUnit) {
        this.ttl = ttl;
        this.unit = unit;
        this.lockTimeout = lockTimeout;
        this.lockTimeoutUnit = lockTimeoutUnit;
    }

    /**
     * Returns the idempotency response cache TTL value.
     *
     * @return the TTL duration as a long.
     */
    public long getTtl() {
        return ttl;
    }

    /**
     * Returns the time unit for the TTL value.
     *
     * @return the {@link TimeUnit} for the TTL.
     */
    public TimeUnit getUnit() {
        return unit;
    }

    /**
     * Returns the maximum lock-hold duration before a STARTED record is considered stale.
     *
     * @return the lock timeout duration as a long.
     */
    public long getLockTimeout() {
        return lockTimeout;
    }

    /**
     * Returns the time unit for the lock timeout value.
     *
     * @return the {@link TimeUnit} for the lock timeout.
     */
    public TimeUnit getLockTimeoutUnit() {
        return lockTimeoutUnit;
    }
}