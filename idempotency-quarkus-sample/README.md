# Idempotency Quarkus Sample

A sample Quarkus application demonstrating the integration of **AvoOnce** idempotency in a modern Jakarta EE / JAX-RS environment.

## Overview

This project showcases how to protect REST endpoints in Quarkus using `idempotency-jaxrs` and an in-memory `idempotency-caffeine` storage backend.

Key highlights:
- Uses CDI (`IdempotencyFilterProducer`) to supply the `IdempotencyRepository`.
- Leverages `@Idempotent` name-binding to selectively protect specific endpoints.
- Intercepts requests to `/api/payments`, handling request body hashing, deduplication, conflict management, and cached response replay.
- Demonstrates selective protection: unannotated endpoints like `/api/payments/unprotected` bypass the filter entirely.

## Prerequisites

- JDK 21+
- Apache Maven 3.9+

## Running the Application

Start Quarkus in development mode:

```bash
./mvnw quarkus:dev
# or
mvn quarkus:dev -pl idempotency-quarkus-sample
```

The sample application will start on `http://localhost:8080`.

## Testing Idempotency via cURL

### 1. Protected Endpoint with `@Idempotent` (First Request)

```bash
curl -i -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 11111111-2222-3333-4444-555555555555" \
  -d '{"accountId": "acc-123", "amount": 100.50}'
```

**Response (HTTP 201 Created):**
```json
{
  "transactionId": "...",
  "status": "SUCCESS",
  "processedAttempts": 1
}
```

### 2. Retry with the Same Key (Cached Replay)

```bash
curl -i -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 11111111-2222-3333-4444-555555555555" \
  -d '{"accountId": "acc-123", "amount": 100.50}'
```

**Response (HTTP 201 Created):**
Exact same payload and transaction ID returned instantly from the cache. `processedAttempts` remains `1`.

### 3. Payload Mismatch Detection

Sending a different body with an already-used key:

```bash
curl -i -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 11111111-2222-3333-4444-555555555555" \
  -d '{"accountId": "acc-123", "amount": 200.00}'
```

**Response (HTTP 422 Unprocessable Entity):**
```text
Idempotency mismatch: key reused with different payload
```

### 4. Unprotected Endpoint (Selective Name-Binding)

Endpoints not annotated with `@Idempotent` are not intercepted, even if an `Idempotency-Key` header is present:

```bash
curl -i -X POST http://localhost:8080/api/payments/unprotected \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 11111111-2222-3333-4444-555555555555" \
  -d '{"accountId": "acc-123", "amount": 100.50}'
```

**Response (HTTP 200 OK):**
Every request will execute the backend logic and return a new `transactionId` and incremented `processedAttempts`.

## Running Tests

Execute the automated test suite:

```bash
mvn test -pl idempotency-quarkus-sample
```
