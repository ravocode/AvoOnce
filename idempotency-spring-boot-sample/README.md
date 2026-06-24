# AvoOnce Spring Boot Sample Application

This module demonstrates how to use the `idempotency-spring-boot-starter` in a standard Spring Boot application.

## Running the Application

To run the sample application, execute the following from the root directory:

```bash
mvn clean install -DskipTests
cd idempotency-spring-boot-sample
mvn spring-boot:run
```

## Testing Idempotency

Once the application is running (by default on port 8080), you can test idempotency by sending a request with an `Idempotency-Key` header:

```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Idempotency-Key: my-unique-key-123" \
  -H "Content-Type: application/json" \
  -d '{"amount": 100.00, "accountId": "acc-123"}'
```

If you send the exact same request again with the exact same key, the server will instantly return the cached response without processing the payment twice!
