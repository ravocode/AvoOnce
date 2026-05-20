# AvoOnce - Project Plan

## 1. Executive Summary
**Project Name:** AvoOnce (Distributed Idempotency Starter)
**Goal:** To build a robust, framework-agnostic, open-source library that solves the "exactly-once" processing myth in distributed systems. It prevents duplicate processing (e.g., double-charging) during network retries by caching responses and enforcing a strict state machine based on an `Idempotency-Key`.

## 2. Core Design Principles
*   **Infrastructure Independent (Pluggable):** Provide an agnostic SPI (Service Provider Interface) so teams can bring their own storage (Caffeine, Redis, JDBC) without being forced into a specific tech stack.
*   **Framework Agnostic Core:** The core logic must be completely independent of web frameworks (zero Spring/Dropwizard dependencies).
*   **Standards Compliant:** Align with the IETF `Idempotency-Key` HTTP header draft.
*   **Safe by Default:** The default in-memory implementation will use **Caffeine** to prevent memory leaks via automated TTL and max-size eviction, outperforming standard `ConcurrentHashMap`.

## 3. Architecture & Multi-Module Structure
To support multiple frameworks and backends seamlessly, the project will be split into a multi-module build (Maven/Gradle).

*   **`idempotency-core`**: Pure Java. Contains the SPI (`IdempotencyRepository`), the core state machine (`IdempotencyManager`), standard Exception definitions, and Data Transfer Objects (`IdempotencyRecord`, `IdempotencyResponse`).
*   **`idempotency-caffeine`**: Pure Java. The default, safe-by-default in-memory implementation.
*   **`idempotency-redis`**: Pure Java. A distributed implementation using Redisson or Lettuce for locking and storage.
*   **`idempotency-jdbc`**: Pure Java/JDBC. A relational database implementation relying on SQL Unique Constraints for atomic locking. Great for teams avoiding new infrastructure.
*   **`idempotency-spring-boot-starter`**: Spring Web MVC specific logic. Contains Auto-Configuration, `HandlerInterceptor` for web requests, and `@Idempotent` AOP aspects for method-level execution.
*   **`idempotency-jaxrs`**: Dropwizard / Jersey specific logic. Contains `ContainerRequestFilter` and `ContainerResponseFilter` for JAX-RS pipelines.

## 4. Core Engine & State Machine
The `IdempotencyManager` orchestrates the logic using a strict state machine to prevent race conditions (The Thundering Herd problem).

**States:**
1.  **`STARTED`**: Request received, currently processing.
2.  **`COMPLETED`**: Processing finished successfully, response cached.
3.  **`FAILED`**: Processing failed, client should be allowed to retry.

**Execution Flow:**
1.  Client sends request with `Idempotency-Key`.
2.  Core attempts an **Atomic Check-and-Set** on the Repository.
    *   *If exists as `COMPLETED`*: Short-circuit and return the cached HTTP Response.
    *   *If exists as `STARTED`*: Throw a `409 Conflict` (or `425 Too Early`), telling the client a request is already in flight.
    *   *If NOT exists*: Atomically write `STARTED`.
3.  Execute the actual business logic / web controller.
4.  *On Success*: Update cache state to `COMPLETED` and store the HTTP response body, headers, and status code.
5.  *On Exception*: Delete the key or set to `FAILED` so the client can safely retry.

## 5. Storage SPI Contract (`IdempotencyRepository`)
```java
public interface IdempotencyRepository {
    // Atomically saves STARTED state or returns existing record
    Optional<IdempotencyRecord> acquireOrGet(String idempotencyKey);
    
    // Updates record with successful payload and COMPLETED state
    void saveSuccess(String idempotencyKey, IdempotencyResponse response);
    
    // Clears the lock / sets state to FAILED
    void saveFailure(String idempotencyKey, String errorMessage);
    
    // Read-only check
    Optional<IdempotencyRecord> get(String idempotencyKey);
}
```

## 6. Phased Implementation Plan

### Phase 1: Core Engine & SPI
*   Initialize the multi-module project structure.
*   Create `idempotency-core` module.
*   Define the `IdempotencyRepository` interface, data records, and exceptions.
*   Build the `IdempotencyManager` pure Java state machine.
*   Write pure Java unit tests covering the state machine logic.

### Phase 2: Caffeine In-Memory Provider
*   Create `idempotency-caffeine` module.
*   Implement `CaffeineIdempotencyRepository`.
*   Configure TTL and Max Size limits to ensure production-safety.
*   Write multi-threaded unit tests to prove atomic check-and-set works locally.

### Phase 3: Spring Boot Integration
*   Create `idempotency-spring-boot-starter` module.
*   Implement `SpringIdempotencyInterceptor` for the HTTP layer.
*   Implement `IdempotencyAutoConfiguration` to automatically bind Caffeine (or Redis if present) to the `IdempotencyManager`.
*   Implement the `@Idempotent` annotation and AOP aspect.
*   Build a sample Spring Boot application for manual testing.

### Phase 4: JAX-RS / Dropwizard Integration
*   Create `idempotency-jaxrs` module.
*   Implement `JaxRsIdempotencyFilter` capturing HTTP headers and responses.
*   Create a Dropwizard `Bundle` for easy registration.

### Phase 5: JDBC & Relational Database Locking
*   Create `idempotency-jdbc` module.
*   Implement `JdbcIdempotencyRepository` using standard JDBC `DataSource`.
*   Rely on SQL `UNIQUE` constraints to enforce atomic lock acquisition (`STARTED` phase).
*   Document the required SQL table schema for users to initialize.

### Phase 6: Redis & Distributed Locking
*   Create `idempotency-redis` module.
*   Implement `RedisIdempotencyRepository`.
*   Use distributed locks (e.g., Redisson or SETNX) to ensure the atomic `STARTED` phase is safe across multiple JVMs.
*   Handle network timeouts and orphaned lock cleanup.

### Phase 7: Polish & Open Source Launch
*   Write a comprehensive `README.md` with examples for Spring, Dropwizard, JDBC, and Redis.
*   Set up GitHub Actions for CI/CD, testing matrix across Java 17 and 21.
*   Publish to Maven Central.
