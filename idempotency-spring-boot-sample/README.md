# AvoOnce Spring Boot Sample Application

This module demonstrates how to use the `idempotency-spring-boot-starter` in a standard Spring Boot application, showing how to select and configure different storage backends.

## Backend Selection

The sample application includes dependencies for both **Caffeine** (in-memory) and **JDBC** (relational/distributed, backed by an in-memory H2 database).

Because both store backends are present on the classpath, the Spring Boot starter's ambiguity fail-fast guard requires you to explicitly select a store.

### Configuring the Backend

You can select the store backend by editing `src/main/resources/application.properties`:

```properties
# Select your backend: 'caffeine' or 'jdbc'
avoonce.idempotency.store=caffeine
```

#### Caffeine Store Configuration (In-Memory)
To use the in-memory Caffeine store:
1. Set `avoonce.idempotency.store=caffeine` in `application.properties`.

#### JDBC Store Configuration (Distributed)
To use the JDBC store with an H2 in-memory database:
1. Set `avoonce.idempotency.store=jdbc` in `application.properties`.
2. Configure the database connection parameters (included in `application.properties`):
   ```properties
   spring.datasource.url=jdbc:h2:mem:idempotency_sample;DB_CLOSE_DELAY=-1
   spring.datasource.driver-class-name=org.h2.Driver
   spring.datasource.username=sa
   spring.datasource.password=
   avoonce.idempotency.jdbc.auto-ddl=true
   ```

---

## Running the Application

To run the sample application with the default backend selection (Caffeine), execute the following from the root directory:

```bash
mvn clean install -DskipTests
cd idempotency-spring-boot-sample
mvn spring-boot:run
```

To run and override the selected backend via the command line (e.g., to run with the JDBC backend):

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--avoonce.idempotency.store=jdbc"
```

---

## Testing Idempotency

Once the application is running (by default on port 8080), you can test idempotency by sending a request with an `Idempotency-Key` header:

```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Idempotency-Key: my-unique-key-123" \
  -H "Content-Type: application/json" \
  -d '{"amount": 100.00, "accountId": "acc-123"}'
```

If you send the exact same request again with the exact same key, the server will instantly return the cached response without processing the payment twice!

