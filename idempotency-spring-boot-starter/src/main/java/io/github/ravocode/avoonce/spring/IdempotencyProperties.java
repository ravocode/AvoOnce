package io.github.ravocode.avoonce.spring;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for the AvoOnce idempotency library ({@code avoonce.idempotency.*}).
 */
/**
 * Configuration properties for the AvoOnce idempotency library.
 */
@ConfigurationProperties(prefix = "avoonce.idempotency")
public class IdempotencyProperties {

    /**
     * Creates a new properties instance with the default settings.
     */
    public IdempotencyProperties() {
    }

    private String headerName = "Idempotency-Key";
    private long ttl = 1;
    private TimeUnit ttlUnit = TimeUnit.HOURS;
    private long lockTimeout = 2;
    private TimeUnit lockTimeoutUnit = TimeUnit.MINUTES;
    private boolean hashBody = true;
    private boolean enforce = false;

    /**
     * Optional regular expression the idempotency key must fully match, e.g.
     * {@code ^[a-fA-F0-9\-]{36}$} to require UUID-shaped keys
     * ({@code avoonce.idempotency.key-pattern}). {@code null} or blank (the
     * default) disables pattern validation.
     */
    private String keyPattern;

    /**
     * Maximum accepted idempotency key length
     * ({@code avoonce.idempotency.max-key-length}). Requests whose key exceeds
     * this length are rejected with HTTP 400, guarding the backing store
     * against abusive keys. Default: 255.
     */
    private int maxKeyLength = 255;

    /**
     * Which backing store to use: {@code auto}, {@code caffeine}, {@code jdbc}, or {@code redis}.
     *
     * <ul>
     *   <li>{@code auto} — wires automatically when exactly one store is on the classpath.
     *       Fails at startup if multiple store implementations are present without an explicit choice.</li>
     *   <li>{@code caffeine} — always use the Caffeine in-memory store.</li>
     *   <li>{@code jdbc} — always use the JDBC distributed store.</li>
     *   <li>{@code redis} — always use the Redis distributed store.</li>
     * </ul>
     */
    private String store = "auto";

    @NestedConfigurationProperty
    private JdbcProperties jdbc = new JdbcProperties();

    // ---- accessors -----------------------------------------------------------

    /**
     * Returns the request header used to carry the idempotency key.
     *
     * @return the header name
     */
    public String getHeaderName() { return headerName; }
    /**
     * Sets the request header used to carry the idempotency key.
     *
     * @param headerName the header name to use
     */
    public void setHeaderName(String headerName) { this.headerName = headerName; }
    /**
     * Returns the configured cache TTL value.
     *
     * @return the TTL value
     */
    public long getTtl() { return ttl; }
    /**
     * Sets the configured cache TTL value.
     *
     * @param ttl the TTL value to use
     */
    public void setTtl(long ttl) { this.ttl = ttl; }
    /**
     * Returns the time unit for the cache TTL.
     *
     * @return the TTL time unit
     */
    public TimeUnit getTtlUnit() { return ttlUnit; }
    /**
     * Sets the time unit for the cache TTL.
     *
     * @param ttlUnit the TTL time unit to use
     */
    public void setTtlUnit(TimeUnit ttlUnit) { this.ttlUnit = ttlUnit; }
    /**
     * Returns the maximum duration a lock may remain active before it is considered stale.
     *
     * @return the lock timeout value
     */
    public long getLockTimeout() { return lockTimeout; }
    /**
     * Sets the maximum duration a lock may remain active before it is considered stale.
     *
     * @param lockTimeout the lock timeout value to use
     */
    public void setLockTimeout(long lockTimeout) { this.lockTimeout = lockTimeout; }
    /**
     * Returns the time unit for the lock timeout.
     *
     * @return the lock timeout time unit
     */
    public TimeUnit getLockTimeoutUnit() { return lockTimeoutUnit; }
    /**
     * Sets the time unit for the lock timeout.
     *
     * @param lockTimeoutUnit the lock timeout time unit to use
     */
    public void setLockTimeoutUnit(TimeUnit lockTimeoutUnit) { this.lockTimeoutUnit = lockTimeoutUnit; }
    /**
     * Returns whether request bodies should be hashed for payload mismatch detection.
     *
     * @return {@code true} when request bodies are hashed
     */
    public boolean isHashBody() { return hashBody; }
    /**
     * Sets whether request bodies should be hashed for payload mismatch detection.
     *
     * @param hashBody {@code true} to hash request bodies
     */
    public void setHashBody(boolean hashBody) { this.hashBody = hashBody; }
    /**
     * Returns whether requests missing the idempotency key are rejected.
     *
     * @return {@code true} when missing keys are enforced
     */
    public boolean isEnforce() { return enforce; }
    /**
     * Sets whether requests missing the idempotency key are rejected.
     *
     * @param enforce {@code true} to reject missing keys
     */
    public void setEnforce(boolean enforce) { this.enforce = enforce; }
    /**
     * Returns the regular expression the idempotency key must fully match, or
     * {@code null} when pattern validation is disabled.
     *
     * @return the key validation pattern
     */
    public String getKeyPattern() { return keyPattern; }
    /**
     * Sets the regular expression the idempotency key must fully match.
     * {@code null} or blank disables pattern validation.
     *
     * @param keyPattern the key validation pattern to use
     */
    public void setKeyPattern(String keyPattern) { this.keyPattern = keyPattern; }
    /**
     * Returns the maximum accepted idempotency key length.
     *
     * @return the maximum key length
     */
    public int getMaxKeyLength() { return maxKeyLength; }
    /**
     * Sets the maximum accepted idempotency key length.
     *
     * @param maxKeyLength the maximum key length to use
     */
    public void setMaxKeyLength(int maxKeyLength) { this.maxKeyLength = maxKeyLength; }
    /**
     * Returns the selected backing store name.
     *
     * @return the store selection value
     */
    public String getStore() { return store; }
    /**
     * Sets the selected backing store name.
     *
     * @param store the store selection value
     */
    public void setStore(String store) { this.store = store; }
    /**
     * Returns the JDBC-specific configuration properties.
     *
     * @return the JDBC configuration object
     */
    public JdbcProperties getJdbc() { return jdbc; }
    /**
     * Sets the JDBC-specific configuration properties.
     *
     * @param jdbc the JDBC configuration object
     */
    public void setJdbc(JdbcProperties jdbc) { this.jdbc = jdbc; }

    // -------------------------------------------------------------------------

    /**
     * JDBC-specific configuration properties ({@code avoonce.idempotency.jdbc.*}).
     */
    public static class JdbcProperties {

        /**
         * Creates a new JDBC properties instance with default values.
         */
        public JdbcProperties() {
        }

        /**
         * Whether to create the {@code idempotency_records} table automatically on startup.
         * Set to {@code false} if you manage the schema with Flyway or Liquibase.
         */
        private boolean autoDdl = true;

        /**
         * Eviction scheduler settings ({@code avoonce.idempotency.jdbc.eviction.*}).
         */
        @NestedConfigurationProperty
        private EvictionProperties eviction = new EvictionProperties();

        /**
         * Returns whether the schema should be initialized automatically.
         *
         * @return {@code true} when DDL is created automatically
         */
        public boolean isAutoDdl() { return autoDdl; }
        /**
         * Sets whether the schema should be initialized automatically.
         *
         * @param autoDdl {@code true} to initialize the schema automatically
         */
        public void setAutoDdl(boolean autoDdl) { this.autoDdl = autoDdl; }
        /**
         * Returns the eviction scheduling settings.
         *
         * @return the eviction properties
         */
        public EvictionProperties getEviction() { return eviction; }
        /**
         * Sets the eviction scheduling settings.
         *
         * @param eviction the eviction properties to use
         */
        public void setEviction(EvictionProperties eviction) { this.eviction = eviction; }
    }

    /**
     * Scheduled eviction configuration ({@code avoonce.idempotency.jdbc.eviction.*}).
     */
    public static class EvictionProperties {

        /**
         * Creates a new eviction properties instance with default values.
         */
        public EvictionProperties() {
        }

        /**
         * Whether to register a scheduled task that calls {@code evictExpired()} periodically.
         */
        private boolean enabled = true;

        /**
         * Interval between eviction runs in milliseconds. Default: 3600000 (1 hour).
         */
        private long intervalMs = 3_600_000L;

        /**
         * Returns whether periodic eviction is enabled.
         *
         * @return {@code true} when eviction is scheduled
         */
        public boolean isEnabled() { return enabled; }
        /**
         * Sets whether periodic eviction is enabled.
         *
         * @param enabled {@code true} to enable periodic eviction
         */
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        /**
         * Returns the interval between eviction runs in milliseconds.
         *
         * @return the eviction interval in milliseconds
         */
        public long getIntervalMs() { return intervalMs; }
        /**
         * Sets the interval between eviction runs in milliseconds.
         *
         * @param intervalMs the interval in milliseconds
         */
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
    }
}
