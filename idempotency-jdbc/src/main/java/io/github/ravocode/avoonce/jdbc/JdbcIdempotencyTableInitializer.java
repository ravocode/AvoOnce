package io.github.ravocode.avoonce.jdbc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Plain POJO that creates the {@code idempotency_records} table if it does not already exist.
 *
 * <p>This class has <strong>no framework dependencies</strong>. It operates directly on a
 * {@link DataSource} using standard JDBC, and can be used from any runtime environment:
 *
 * <ul>
 *   <li><b>Spring Boot</b>: The {@code idempotency-spring-boot-starter} wraps this in an
 *       {@code InitializingBean} and calls it automatically when
 *       {@code avoonce.idempotency.jdbc.auto-ddl=true} (the default).</li>
 *   <li><b>JAX-RS / CDI</b>: Call {@link #initialize(DataSource)} from a {@code @PostConstruct}
 *       method or an application lifecycle event.</li>
 *   <li><b>Plain Java</b>: Call {@link #initialize(DataSource)} at application startup before
 *       the first request is handled.</li>
 * </ul>
 *
 * <p>If you manage schema migrations with Flyway or Liquibase, set
 * {@code avoonce.idempotency.jdbc.auto-ddl=false} and include the following DDL in your
 * migration scripts instead:
 *
 * <pre>{@code
 * CREATE TABLE IF NOT EXISTS idempotency_records (
 *     idempotency_key  VARCHAR(512) NOT NULL,
 *     status           VARCHAR(16)  NOT NULL,
 *     request_hash     VARCHAR(64),
 *     response_status  INTEGER,
 *     response_headers TEXT,
 *     response_body    BLOB,        -- use BYTEA for PostgreSQL, BLOB for most others (MySQL, H2, etc.)
 *     expires_at       BIGINT       NOT NULL,
 *     created_at       BIGINT       NOT NULL,
 *     updated_at       BIGINT       NOT NULL,
 *     PRIMARY KEY (idempotency_key)
 * );
 * CREATE INDEX IF NOT EXISTS idx_idempotency_expires_at ON idempotency_records(expires_at);
 * }</pre>
 */
public class JdbcIdempotencyTableInitializer {

    /**
     * Creates the {@code idempotency_records} table and its index if they do not already exist.
     * Safe to call multiple times (idempotent).
     *
     * @param dataSource the JDBC data source to execute DDL against.
     * @throws IllegalStateException if a {@link SQLException} occurs during DDL execution.
     */
    public void initialize(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            String blobType = resolveBlobType(conn);
            String createTable = "CREATE TABLE IF NOT EXISTS idempotency_records ("
                    + "idempotency_key  VARCHAR(512) NOT NULL, "
                    + "status           VARCHAR(16)  NOT NULL, "
                    + "request_hash     VARCHAR(64), "
                    + "response_status  INTEGER, "
                    + "response_headers TEXT, "
                    + "response_body    " + blobType + ", "
                    + "expires_at       BIGINT NOT NULL, "
                    + "created_at       BIGINT NOT NULL, "
                    + "updated_at       BIGINT NOT NULL, "
                    + "PRIMARY KEY (idempotency_key)"
                    + ")";

            String createIndex = "CREATE INDEX IF NOT EXISTS idx_idempotency_expires_at "
                    + "ON idempotency_records(expires_at)";

            stmt.execute(createTable);
            try {
                stmt.execute(createIndex);
            } catch (SQLException indexEx) {
                // Some drivers do not support IF NOT EXISTS on CREATE INDEX — swallow safely.
                // The index is an optimisation; absence does not break correctness.
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialise idempotency_records table", e);
        }
    }

    private String resolveBlobType(Connection conn) throws SQLException {
        String dbName = conn.getMetaData().getDatabaseProductName().toLowerCase();
        if (dbName.contains("postgres")) {
            return "BYTEA";
        }
        return "BLOB";
    }
}
