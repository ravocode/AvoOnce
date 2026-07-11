# Idempotency Redis Storage

This module provides a Redis-backed implementation of the `IdempotencyRepository` SPI for AvoOnce using an adapter pattern that supports popular clients like [Jedis](https://github.com/redis/jedis) and [Lettuce](https://github.com/lettuce-io/lettuce-core).

## Overview

The `idempotency-redis` module persists idempotency records in a Redis database. It serializes the idempotency state, request hashes, and cached HTTP responses using a custom, lightweight binary protocol that avoids heavy framework dependencies like Jackson or Gson. This makes it a great choice when you want high-performance, distributed idempotency checks across multiple application instances with very low latency.

It has **no external framework dependencies** other than `idempotency-core` and `slf4j-api`. Redis client libraries (`jedis` and `lettuce-core`) are marked as optional, allowing you to plug in your preferred client.

### Supported Datastores

Because this module uses standard Redis commands (`SET`, `GET`, `DEL`), it is compatible with almost all Redis-compatible datastores, including:
- Redis (Standalone, Sentinel, Cluster)
- KeyDB
- Amazon ElastiCache
- Google Cloud Memorystore

## Installation

Add the Redis storage backend to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.ravocode.avoonce</groupId>
    <artifactId>idempotency-redis</artifactId>
    <version>1.0.0-alpha.3.0</version>
</dependency>
```

If you are using Spring Boot, be sure to include the starter as well:

```xml
<dependency>
    <groupId>io.github.ravocode.avoonce</groupId>
    <artifactId>idempotency-spring-boot-starter</artifactId>
    <version>1.0.0-alpha.3.0</version>
</dependency>
```

You must also include your preferred Redis client (e.g., Jedis or Lettuce) in your project, as AvoOnce marks them as optional:

```xml
<!-- Example: Using Jedis -->
<dependency>
    <groupId>redis.clients</groupId>
    <artifactId>jedis</artifactId>
</dependency>
```

## Configuration

This module uses an adapter pattern (`RedisOperations`) to remain agnostic of the underlying Redis client. We provide out-of-the-box adapters for Jedis and Lettuce.

### With Spring Boot

If you are using `idempotency-spring-boot-starter`, AvoOnce will automatically detect your client and configure the repository. Simply expose your client as a Spring `@Bean`:

**Jedis Example:**
```java
@Bean
public JedisPool jedisPool() {
    return new JedisPool("localhost", 6379);
}
```

**Lettuce Example:**
```java
@Bean
public RedisClient redisClient() {
    return RedisClient.create("redis://localhost:6379");
}
```

### Without Spring (Manual Configuration)

If you are not using Spring, you can manually wrap your client in the provided adapter and instantiate the repository:

```java
// Example using Jedis
JedisPool pool = new JedisPool("localhost", 6379);
RedisOperations ops = new JedisRedisOperations(pool);
IdempotencyRepository repo = new RedisIdempotencyRepository(ops, config);
```

## Schema Initialization

Unlike the JDBC repository, Redis is a key-value store and does **not** require any schema definitions, DDL execution, or table initialization. The `RedisIdempotencyRepository` uses keys with the prefix `idempotency:` (e.g., `idempotency:my-unique-key`) to store the binary-serialized idempotency records.

## Concurrency, Locking, and Expiry

This repository leverages atomic Redis commands to prevent race conditions.

When two concurrent requests attempt to create the same key simultaneously, the repository uses `SET key value NX PX expiration`. The `NX` (Not eXists) flag ensures that only one request can successfully create the key and acquire the lock. The losing request will fallback to inspecting the existing state, throwing a conflict exception if the original request is still in progress.

Additionally, Redis natively handles the time-to-live (TTL) expiration via the `PX` parameter, ensuring that stalled or crashed requests eventually release their locks without requiring a manual background eviction process.
