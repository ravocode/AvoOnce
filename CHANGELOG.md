# Changelog

## 1.0.0 (2026-08-02)

### Initial Release

AvoOnce is now available as a lightweight, framework-agnostic distributed idempotency engine for Java. This first release provides the foundation for safely handling duplicate requests, replaying exact HTTP responses, and preserving reliable retry behavior across distributed systems.

### What’s New

- Annotation-driven protection with `@Idempotent` for controllers, methods, and endpoints
- First-class integrations for Spring Boot 4+, Quarkus 3.12+, Dropwizard 4.0+, and Jakarta EE / JAX-RS 3.1+
- Byte-perfect HTTP response caching and replay, including status codes, headers, and raw response bytes
- In-flight concurrency locking to prevent duplicate execution while a request is still being processed
- Payload tamper protection using SHA-256 hashing to reject modified requests that reuse the same idempotency key
- Automatic handling of transient server errors so clients can safely retry after failed executions
- Pluggable storage support with built-in backends for Caffeine, JDBC, and Redis
- Support for the `Idempotency-Key` header flow in a standards-aligned, framework-flexible design

### Included Modules

- `idempotency-core` – core state machine, hashing logic, response wrappers, and storage SPI
- `idempotency-caffeine` – high-performance in-memory backend
- `idempotency-jdbc` – relational backend for PostgreSQL, MySQL, H2, Oracle, MariaDB, and SQL Server
- `idempotency-redis` – distributed Redis backend with TTL-based expiration
- `idempotency-spring-boot-starter` – Spring Boot integration and auto-configuration
- `idempotency-jaxrs` – Jakarta EE / JAX-RS integration
- `idempotency-spring-boot-sample`, `idempotency-quarkus-sample`, and acceptance tests – reference applications and validation coverage

### Getting Started

Start by adding the relevant starter or adapter dependency, choosing a storage backend, annotating your endpoint with `@Idempotent`, and sending requests with an `Idempotency-Key` header.

### Notes

This initial release focuses on the core building blocks needed for robust idempotent request handling in modern Java services, with a modular architecture that can be extended in future releases.
