# Idempotency Spring Boot Starter

This module provides seamless, zero-code integration of the AvoOnce Idempotency library for Spring Boot applications.

## Overview

The `idempotency-spring-boot-starter` auto-configures the core `IdempotencyManager` and injects an `IdempotencyFilter` (a standard Servlet `OncePerRequestFilter`) into your Spring Web MVC application. 

Because it operates at the HTTP filter layer, it completely decouples idempotency logic from your business code. You do not need to add any annotations or modify your `@RestController` classes.

## How It Works

1.  **Interception:** The `IdempotencyFilter` intercepts all incoming HTTP requests.
2.  **Detection:** If the `Idempotency-Key` header is present, it hands the request off to the core state machine.
3.  **Caching:** It uses Spring's `ContentCachingResponseWrapper` to capture the outgoing HTTP status, headers, and body bytes.
4.  **Replay:** On a duplicate request, it bypasses the Spring `DispatcherServlet` entirely and writes the cached raw bytes and headers directly back to the `HttpServletResponse`.

## Installation

You must include this starter along with a chosen storage implementation (e.g., `idempotency-caffeine` or `idempotency-jdbc`):

```xml
<dependency>
    <groupId>io.github.ravocode.avoonce</groupId>
    <artifactId>idempotency-spring-boot-starter</artifactId>
    <version>1.0.0-alpha.3.0</version>
</dependency>
<dependency>
    <groupId>io.github.ravocode.avoonce</groupId>
    <artifactId>idempotency-caffeine</artifactId>
    <version>1.0.0-alpha.3.0</version>
</dependency>
```

## Storage Backend Selection

By default, the starter automatically registers the repository implementation based on the dependencies present in your classpath:
- **Caffeine (In-Memory):** Wired automatically if only `idempotency-caffeine` is present.
- **JDBC (Distributed):** Wired automatically if only `idempotency-jdbc` is present and a `DataSource` bean is configured.
- **Redis (Distributed):** Wired automatically if only `idempotency-redis` is present and a supported Redis client bean (e.g., `JedisPool` or `RedisClient`) is configured.
- **Ambiguity / Fail-Fast Guard:** If **multiple** storage backends are present on the classpath (and their required beans are configured), the application will fail to start to prevent ambiguity. You must explicitly configure the `avoonce.idempotency.store` property to choose one.

To switch or explicitly define your backend, set:
```yaml
avoonce:
  idempotency:
    store: redis # Options: auto, caffeine, jdbc, redis
```

## Configuration Properties

You can customize the starter's behavior using your `application.yml` or `application.properties`. Below are the default values:

```yaml
avoonce:
  idempotency:
    # Which store to use: "auto", "caffeine", "jdbc", or "redis"
    store: "auto"

    # The HTTP header used to identify the idempotency key
    header-name: "Idempotency-Key"
    
    # TTL (Time-To-Live) for successful response caches
    ttl: 1
    ttl-unit: HOURS
    
    # How long to maintain a lock while the request is actively processing
    lock-timeout: 2
    lock-timeout-unit: MINUTES
    
    # If true, hashes the request body and rejects requests that reuse 
    # the same key but have a different payload (HTTP 422).
    hash-body: true
    
    # If true, requests without the Idempotency-Key header are rejected (HTTP 400).
    # If false, they simply bypass the idempotency filter.
    enforce: false
    
    filter:
      # Set to false to disable the auto-configured servlet filter entirely
      enabled: true

    # JDBC-specific configuration (ignored if using Caffeine)
    jdbc:
      # Automatically run CREATE TABLE IF NOT EXISTS on startup
      auto-ddl: true
      eviction:
        # Schedule background task to delete expired records
        enabled: true
        # Frequency of the eviction task in milliseconds (default: 1 hour)
        interval-ms: 3600000
```

## Advanced: Custom Configuration

If you need to customize the `IdempotencyFilter` registration (for example, to restrict it to specific URL patterns rather than `/*`), you can define your own `FilterRegistrationBean` in your Spring context. When you do this, the starter will automatically back off and use your bean.

```java
@Bean
public FilterRegistrationBean<IdempotencyFilter> customIdempotencyFilter(
        IdempotencyManager manager, IdempotencyProperties properties) {
        
    FilterRegistrationBean<IdempotencyFilter> registrationBean = new FilterRegistrationBean<>();
    registrationBean.setFilter(new IdempotencyFilter(manager, properties));
    
    // Only apply idempotency to the payments API
    registrationBean.addUrlPatterns("/api/payments/*");
    registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    
    return registrationBean;
}
```
