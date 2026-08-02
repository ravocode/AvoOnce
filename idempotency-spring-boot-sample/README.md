# AvoOnce Spring Boot Sample Application

This module demonstrates how to use the `idempotency-spring-boot-starter` in a standard Spring Boot application, showcasing both storage backend selection and selective `@Idempotent` endpoint protection.

## Features Demonstrated

1. **Selective Endpoint Protection:** Using `@Idempotent` on specific controller methods (`/api/payments`) alongside unprotected endpoints (`/api/payments/unprotected`).
2. **Flexible Storage Backends:** Demonstrates in-memory (`caffeine`) and relational/distributed (`jdbc`) storage.

## Backend Selection

The sample application includes dependencies for both **Caffeine** (in-memory) and **JDBC** (relational/distributed, backed by an in-memory H2 database).

Because both store backends are present on the classpath, the Spring Boot starter's ambiguity fail-fast guard requires you to explicitly select a store.

### Configuring the Backend

You can select the store backend by editing `src/main/resources/application.properties`:

```properties
# Select your backend: 'caffeine' or 'jdbc'
avoonce.idempotency.store=caffeine

# Optional: Set mode to ANNOTATION to only protect @Idempotent endpoints
# avoonce.idempotency.mode=ANNOTATION
```

#### Caffeine Store Configuration (In-Memory)
Set `avoonce.idempotency.store=caffeine` in `application.properties`.

#### JDBC Store Configuration (Distributed)
Set `avoonce.idempotency.store=jdbc` in `application.properties`.
Connection parameters:
```properties
spring.datasource.url=jdbc:h2:mem:idempotency_sample;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
avoonce.idempotency.jdbc.auto-ddl=true
```

---

## Running the Application

To run the sample application with the default backend selection (Caffeine), execute:

```bash
mvn spring-boot:run -pl idempotency-spring-boot-sample
```

---

## Testing Idempotency

### 1. Protected Endpoint (`@Idempotent`)

```bash
curl -i -X POST http://localhost:8080/api/payments \
  -H "Idempotency-Key: my-unique-key-123" \
  -H "Content-Type: application/json" \
  -d '{"amount": 100.00, "accountId": "acc-123"}'
```

If you send the exact same request again with the exact same key, the server will instantly return the cached response without processing the payment twice!

### 2. Unprotected Endpoint

```bash
curl -i -X POST http://localhost:8080/api/payments/unprotected \
  -H "Idempotency-Key: my-unique-key-123" \
  -H "Content-Type: application/json" \
  -d '{"amount": 100.00, "accountId": "acc-123"}'
```

In `ANNOTATION` mode, this endpoint will process on every invocation and return a fresh transaction ID.
