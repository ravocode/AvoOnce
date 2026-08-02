# Idempotency Spring Boot Starter

This module provides seamless, annotation-driven integration of the AvoOnce Idempotency library for Spring Boot applications.

## Overview

The `idempotency-spring-boot-starter` auto-configures the core `IdempotencyManager` and injects an `IdempotencyFilter` (a standard Servlet `OncePerRequestFilter`) into your Spring Web MVC application. 

AvoOnce operates selectively: only controller classes or handler methods annotated with `@Idempotent` are intercepted and protected. All other endpoints in your application bypass the idempotency filter completely.

## How It Works

1.  **Inspection:** The `IdempotencyFilter` inspects the target handler method for the `@Idempotent` annotation.
2.  **Detection:** If `@Idempotent` is present and an `Idempotency-Key` header is provided, it hands the request off to the core state machine.
3.  **Caching:** It uses Spring's `ContentCachingResponseWrapper` to capture the outgoing HTTP status, headers, and body bytes.
4.  **Replay:** On a duplicate request, it bypasses the controller entirely and writes the cached raw bytes and headers directly back to the `HttpServletResponse`.

## Installation

Include this starter along with a chosen storage implementation (e.g., `idempotency-caffeine`, `idempotency-jdbc`, or `idempotency-redis`):

```xml
<dependency>
    <groupId>io.github.ravocode.avoonce</groupId>
    <artifactId>idempotency-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>io.github.ravocode.avoonce</groupId>
    <artifactId>idempotency-caffeine</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Selective Protection with `@Idempotent`

Annotate your target controller methods or classes with `@Idempotent`:

```java
import io.github.ravocode.avoonce.spring.annotation.Idempotent;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @PostMapping
    @Idempotent // Protected by AvoOnce
    public ResponseEntity<PaymentResponse> processPayment(@RequestBody PaymentRequest req) {
        return ResponseEntity.status(201).body(service.process(req));
    }

    @PostMapping("/unprotected")
    // Unannotated -> completely bypasses idempotency filter even if header is present
    public ResponseEntity<PaymentResponse> unrecorded(@RequestBody PaymentRequest req) {
        return ResponseEntity.ok(service.process(req));
    }
}
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

You can customize the starter's behavior using your `application.yml` or `application.properties`:

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
    
    # If true, requests to @Idempotent endpoints without the Idempotency-Key header are rejected (HTTP 400).
    # If false, unkeyed requests simply bypass the idempotency logic.
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
