# Idempotency JAX-RS Integration

This module provides a JAX-RS `ContainerRequestFilter` / `ContainerResponseFilter` integration for AvoOnce, enabling selective idempotency protection for any JAX-RS application using the `@Idempotent` name-binding annotation.

## Overview

The `idempotency-jaxrs` module works with **any JAX-RS 3.1+ runtime** (Jakarta EE 10) — Quarkus, Dropwizard 4+, Jersey 3+, RESTEasy 6+, Helidon 4+, CXF, or any other compliant implementation. It has **no dependency on CDI, Spring, or any DI framework**. You simply construct the filter with your chosen `IdempotencyRepository` and register it.

## Installation

Add the JAX-RS module and your chosen storage backend to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.ravocode.avoonce</groupId>
    <artifactId>idempotency-jaxrs</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Choose a storage backend -->
<dependency>
    <groupId>io.github.ravocode.avoonce</groupId>
    <artifactId>idempotency-caffeine</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick Start

### 1. Register Filter in Your Framework

#### Dropwizard 4+
```java
@Override
public void run(MyConfiguration config, Environment environment) {
    IdempotencyConfig idempotencyConfig = new IdempotencyConfig();
    IdempotencyRepository repo = new CaffeineIdempotencyRepository(idempotencyConfig);

    environment.jersey().register(new IdempotencyContainerFilter(repo));
}
```

#### Quarkus / CDI
```java
@ApplicationScoped
public class IdempotencyProducer {

    @Produces
    @Singleton
    public IdempotencyContainerFilter idempotencyFilter() {
        IdempotencyConfig config = new IdempotencyConfig();
        IdempotencyRepository repo = new CaffeineIdempotencyRepository(config);
        return new IdempotencyContainerFilter(repo);
    }
}
```

#### Jersey 3+ Standalone
```java
IdempotencyConfig config = new IdempotencyConfig();
IdempotencyRepository repo = new CaffeineIdempotencyRepository(config);

ResourceConfig resourceConfig = new ResourceConfig();
resourceConfig.register(new IdempotencyContainerFilter(repo));
resourceConfig.register(MyResource.class);
```

### 2. Annotate Target Endpoints with `@Idempotent`

Use the `@Idempotent` name-binding annotation on your resource methods or classes:

```java
import io.github.ravocode.avoonce.jaxrs.Idempotent;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

@Path("/payments")
public class PaymentResource {

    @POST
    @Idempotent  // Selective idempotency protection
    public Response createPayment(PaymentRequest request) {
        return Response.status(201).entity(paymentService.process(request)).build();
    }

    @POST
    @Path("/unprotected")
    // No @Idempotent — unannotated endpoints bypass idempotency filter completely
    public Response createUnprotectedPayment(PaymentRequest request) {
        return Response.ok(paymentService.process(request)).build();
    }
}
```

## Configuration

Use `IdempotencyJaxRsConfig` to customize filter behavior:

```java
IdempotencyJaxRsConfig jaxrsConfig = new IdempotencyJaxRsConfig()
        .setHeaderName("Idempotency-Key")  // Header to look for (default)
        .setHashBody(true)                 // Hash body for mismatch detection (default)
        .setEnforce(false);                // Reject requests without key (default: false)

IdempotencyContainerFilter filter = new IdempotencyContainerFilter(repo, jaxrsConfig);
```

## How It Works

1. **Request Phase**: The filter extracts the `Idempotency-Key` header, buffers and hashes the request body, and attempts to acquire a lock via the configured `IdempotencyRepository`.
   - If a cached response exists → short-circuits with the cached response.
   - If the key is in progress → returns `409 Conflict`.
   - If the key was used with a different payload → returns `422 Unprocessable Entity`.
   - If the lock is acquired → the request proceeds to the resource method.

2. **Response Phase**: After the resource method executes, the filter captures the HTTP status, headers, and entity, then stores them in the repository for future replays.

3. **5xx Handling**: Server errors (`>= 500`) mark the record as `FAILED`, allowing the client to retry with the same key.

