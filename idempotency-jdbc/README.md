# Idempotency JDBC Storage

This module provides a relational database implementation of the `IdempotencyRepository` SPI for AvoOnce using plain JDBC.

## Overview

The `idempotency-jdbc` module persists idempotency records in a standard relational database table. It uses a single table (`idempotency_records`) and relies on standard JDBC and SQL `BLOB` (or equivalent) for payload caching. This makes it a great choice when your application already uses a relational database and you want to ensure distributed lock safety across multiple instances without adding new infrastructure like Redis.

It has **no dependencies** other than `idempotency-core` and the standard JDK `java.sql` classes, making it completely framework-agnostic.

### Supported Databases

Because this module uses standard JDBC and basic ANSI SQL, it is compatible with almost all relational databases, including:
- PostgreSQL
- MySQL
- MariaDB
- Oracle
- Microsoft SQL Server
- H2 (In-Memory / File)

## Installation

Add the JDBC storage backend to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.ravocode.avoonce</groupId>
    <artifactId>idempotency-jdbc</artifactId>
    <version>1.0.0-alpha.2</version>
</dependency>
```

If you are using Spring Boot, be sure to include the starter as well:

```xml
<dependency>
    <groupId>io.github.ravocode.avoonce</groupId>
    <artifactId>idempotency-spring-boot-starter</artifactId>
    <version>1.0.0-alpha.2</version>
</dependency>
```

## Schema Initialization

AvoOnce needs a table named `idempotency_records` to store its state.

### Spring Boot

If you are using `idempotency-spring-boot-starter`, the starter will automatically execute the DDL to create the table and index at startup using the `JdbcIdempotencyTableInitializer`. The initializer automatically detects the database vendor via JDBC metadata and maps the `response_body` column type accordingly (e.g., `BYTEA` for PostgreSQL, `BLOB` for others).

You can disable this behavior by setting the following property in your `application.yml` or `application.properties`:

```yaml
avoonce:
  idempotency:
    jdbc:
      auto-ddl: false
```

### Manual / Flyway / Liquibase

If you disable `auto-ddl` and prefer to manage the schema yourself, you can add the following to your database migration scripts:

```sql
CREATE TABLE IF NOT EXISTS idempotency_records (
    idempotency_key  VARCHAR(512) NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    request_hash     VARCHAR(64),
    response_status  INTEGER,
    response_headers TEXT,
    response_body    BLOB,       -- use BYTEA for PostgreSQL, BLOB for most others (MySQL, H2, etc.)
    expires_at       BIGINT       NOT NULL,
    created_at       BIGINT       NOT NULL,
    updated_at       BIGINT       NOT NULL,
    PRIMARY KEY (idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_idempotency_expires_at ON idempotency_records(expires_at);
```

*(Note: Depending on your specific database vendor (e.g. PostgreSQL), you may need to adjust the `BLOB` type to `BYTEA`, etc.)*

## Concurrency and Locking

This repository uses unique constraint violations on the `idempotency_key` primary key to prevent race conditions. When two concurrent requests attempt to create the same key simultaneously, only one transaction will succeed in inserting the row; the other will receive a primary key violation, which `JdbcIdempotencyRepository` handles natively to enforce exactly-once semantics across multiple application nodes.
