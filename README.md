# AvoOnce - Distributed Idempotency Starter

AvoOnce is a robust, framework-agnostic, open-source library that solves the "exactly-once" processing myth in distributed systems. It prevents duplicate processing (e.g., double-charging) during network retries by caching responses and enforcing a strict state machine based on an `Idempotency-Key`.

## Features
*   **Infrastructure Independent (Pluggable):** Provide an agnostic SPI (Service Provider Interface) so teams can bring their own storage (Caffeine, Redis, JDBC).
*   **Framework Agnostic Core:** The core logic is completely independent of web frameworks.
*   **Standards Compliant:** Aligns with the IETF `Idempotency-Key` HTTP header draft.

## Modules
*   **`idempotency-core`**: Pure Java core containing the state machine and SPI.
*   **`idempotency-caffeine`**: Safe-by-default in-memory implementation using Caffeine.
*   **`idempotency-redis`**: Distributed implementation using Redis.
*   **`idempotency-jdbc`**: Relational database implementation using SQL Unique Constraints.
*   **`idempotency-spring-boot-starter`**: Spring Web MVC integration.
*   **`idempotency-jaxrs`**: Dropwizard / Jersey integration.

## Architecture
To support multiple frameworks and backends seamlessly, the project is split into a maven multi-module build.

```mermaid
graph TD
    Client((Client)) -->|HTTP Request with<br/>Idempotency-Key| Web[Web Layer]
    
    subgraph Framework Integrations
        Web --> SB[idempotency-spring-boot-starter]
        Web --> JAX[idempotency-jaxrs]
    end
    
    SB --> Core[idempotency-core<br/>IdempotencyManager & SPI]
    JAX --> Core
    
    subgraph Storage Implementations
        Core -->|SPI| Caff[idempotency-caffeine<br/>In-Memory]
        Core -->|SPI| Red[idempotency-redis<br/>Distributed]
        Core -->|SPI| JDBC[idempotency-jdbc<br/>Relational DB]
    end
```
