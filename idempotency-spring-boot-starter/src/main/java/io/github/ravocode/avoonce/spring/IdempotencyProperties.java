package io.github.ravocode.avoonce.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.concurrent.TimeUnit;

@ConfigurationProperties(prefix = "avoonce.idempotency")
public class IdempotencyProperties {

    private String headerName = "Idempotency-Key";
    private long ttl = 1;
    private TimeUnit ttlUnit = TimeUnit.HOURS;
    private long lockTimeout = 2;
    private TimeUnit lockTimeoutUnit = TimeUnit.MINUTES;
    private boolean hashBody = true;
    private boolean enforce = false;

    /**
     * Which backing store to use: {@code auto}, {@code caffeine}, or {@code jdbc}.
     *
     * <ul>
     *   <li>{@code auto} — wires automatically when exactly one store is on the classpath.
     *       Fails at startup if both Caffeine and JDBC are present without an explicit choice.</li>
     *   <li>{@code caffeine} — always use the Caffeine in-memory store.</li>
     *   <li>{@code jdbc} — always use the JDBC distributed store.</li>
     * </ul>
     */
    private String store = "auto";

    @NestedConfigurationProperty
    private JdbcProperties jdbc = new JdbcProperties();

    // ---- accessors -----------------------------------------------------------

    public String getHeaderName() { return headerName; }
    public void setHeaderName(String headerName) { this.headerName = headerName; }
    public long getTtl() { return ttl; }
    public void setTtl(long ttl) { this.ttl = ttl; }
    public TimeUnit getTtlUnit() { return ttlUnit; }
    public void setTtlUnit(TimeUnit ttlUnit) { this.ttlUnit = ttlUnit; }
    public long getLockTimeout() { return lockTimeout; }
    public void setLockTimeout(long lockTimeout) { this.lockTimeout = lockTimeout; }
    public TimeUnit getLockTimeoutUnit() { return lockTimeoutUnit; }
    public void setLockTimeoutUnit(TimeUnit lockTimeoutUnit) { this.lockTimeoutUnit = lockTimeoutUnit; }
    public boolean isHashBody() { return hashBody; }
    public void setHashBody(boolean hashBody) { this.hashBody = hashBody; }
    public boolean isEnforce() { return enforce; }
    public void setEnforce(boolean enforce) { this.enforce = enforce; }
    public String getStore() { return store; }
    public void setStore(String store) { this.store = store; }
    public JdbcProperties getJdbc() { return jdbc; }
    public void setJdbc(JdbcProperties jdbc) { this.jdbc = jdbc; }

    // -------------------------------------------------------------------------

    /**
     * JDBC-specific configuration properties ({@code avoonce.idempotency.jdbc.*}).
     */
    public static class JdbcProperties {

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

        public boolean isAutoDdl() { return autoDdl; }
        public void setAutoDdl(boolean autoDdl) { this.autoDdl = autoDdl; }
        public EvictionProperties getEviction() { return eviction; }
        public void setEviction(EvictionProperties eviction) { this.eviction = eviction; }
    }

    /**
     * Scheduled eviction configuration ({@code avoonce.idempotency.jdbc.eviction.*}).
     */
    public static class EvictionProperties {

        /**
         * Whether to register a scheduled task that calls {@code evictExpired()} periodically.
         */
        private boolean enabled = true;

        /**
         * Interval between eviction runs in milliseconds. Default: 3600000 (1 hour).
         */
        private long intervalMs = 3_600_000L;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
    }
}

