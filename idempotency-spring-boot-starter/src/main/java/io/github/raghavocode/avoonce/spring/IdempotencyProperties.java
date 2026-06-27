package io.github.raghavocode.avoonce.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
}
