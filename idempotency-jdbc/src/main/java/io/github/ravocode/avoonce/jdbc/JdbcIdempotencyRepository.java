package io.github.ravocode.avoonce.jdbc;

import io.github.ravocode.avoonce.core.config.IdempotencyConfig;
import io.github.ravocode.avoonce.core.domain.IdempotencyRecord;
import io.github.ravocode.avoonce.core.domain.IdempotencyResponse;
import io.github.ravocode.avoonce.core.domain.IdempotencyStatus;
import io.github.ravocode.avoonce.core.exception.IdempotencyConflictException;
import io.github.ravocode.avoonce.core.exception.IdempotencyMismatchException;
import io.github.ravocode.avoonce.core.spi.IdempotencyRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A distributed {@link IdempotencyRepository} backed by any JDBC-compatible database.
 *
 * <p>This class has <strong>zero framework dependencies</strong>: it uses only
 * {@code java.sql.*} from the JDK and is usable from Spring Boot, Quarkus, Micronaut,
 * JAX-RS, or plain Java. Framework-specific wiring (e.g., Spring Boot auto-configuration)
 * is handled by the {@code idempotency-spring-boot-starter} module.
 *
 * <h2>Supported databases</h2>
 * <ul>
 *   <li>PostgreSQL — {@code INSERT … ON CONFLICT DO NOTHING}</li>
 *   <li>MySQL / MariaDB — {@code INSERT IGNORE …}</li>
 *   <li>H2 — {@code INSERT IGNORE …}</li>
 *   <li>Any other JDBC driver — plain {@code INSERT}, duplicate-key detected via SQLState {@code 23xxx}</li>
 * </ul>
 *
 * <h2>Atomicity</h2>
 * <p>The {@link #acquireOrGet} method uses an <em>optimistic insert</em> as the distributed
 * lock primitive: the first caller to {@code INSERT} a {@code STARTED} row wins the lock;
 * concurrent callers receive a duplicate-key signal from the database (mapped to
 * {@link IdempotencyConflictException}).
 *
 * <h2>Header serialisation</h2>
 * <p>Response headers ({@code Map<String, List<String>>}) are persisted as a compact plain-text
 * string requiring no external libraries. Format: {@code name=v1|v2;name2=v3}. Values containing
 * {@code ;}, {@code =}, or {@code |} are percent-encoded.
 *
 * <h2>Usage (non-Spring)</h2>
 * <pre>{@code
 * DataSource ds = ...; // your connection pool
 * new JdbcIdempotencyTableInitializer().initialize(ds);  // one-time DDL
 * IdempotencyRepository repo = new JdbcIdempotencyRepository(ds, new IdempotencyConfig());
 * IdempotencyManager manager = new IdempotencyManager(repo);
 * }</pre>
 */
public class JdbcIdempotencyRepository implements IdempotencyRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcIdempotencyRepository.class);

    private static final String TABLE = "idempotency_records";

    // --- SQL templates filled in at construction time based on the detected dialect ---
    private final String sqlInsert;          // dialect-specific INSERT (returns rows affected)
    private final String sqlSelectByKey;
    private final String sqlUpdateSuccess;
    private final String sqlUpdateFailure;
    private final String sqlDeleteByKey;
    private final String sqlEvictExpired;

    private final DataSource dataSource;
    private final IdempotencyConfig config;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public JdbcIdempotencyRepository(final DataSource dataSource, final IdempotencyConfig config) {
        this.dataSource = dataSource;
        this.config = config;

        String dialect = detectDialect(dataSource);
        this.sqlInsert       = buildInsertSql(dialect);
        this.sqlSelectByKey  = "SELECT idempotency_key, status, request_hash, response_status, "
                             + "response_headers, response_body, expires_at "
                             + "FROM " + TABLE + " WHERE idempotency_key = ?";
        this.sqlUpdateSuccess = "UPDATE " + TABLE + " SET status = ?, response_status = ?, "
                             + "response_headers = ?, response_body = ?, updated_at = ? "
                             + "WHERE idempotency_key = ?";
        this.sqlUpdateFailure = "UPDATE " + TABLE + " SET status = ?, updated_at = ? "
                             + "WHERE idempotency_key = ?";
        this.sqlDeleteByKey  = "DELETE FROM " + TABLE + " WHERE idempotency_key = ?";
        this.sqlEvictExpired = "DELETE FROM " + TABLE + " WHERE expires_at < ?";
    }

    // -------------------------------------------------------------------------
    // SPI implementation
    // -------------------------------------------------------------------------

    /**
     * Delegates to {@link #acquireOrGet(String, String)} with a {@code null} hash.
     */
    @Override
    public Optional<IdempotencyRecord> acquireOrGet(final String idempotencyKey) {
        return acquireOrGet(idempotencyKey, null);
    }

    /**
     * Atomically acquires a distributed lock or returns an existing {@code COMPLETED} record.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Attempt {@code INSERT} of a {@code STARTED} row.</li>
     *   <li>If the insert succeeds (1 row affected) → lock acquired, return {@code empty()}.</li>
     *   <li>If the insert fails (0 rows / duplicate key) → {@code SELECT} the existing row and:
     *     <ul>
     *       <li>{@code COMPLETED}: validate hash, return the record for replay.</li>
     *       <li>{@code STARTED}, not expired: throw {@link IdempotencyConflictException}.</li>
     *       <li>{@code STARTED}, expired OR {@code FAILED}: delete stale row and re-insert.</li>
     *     </ul>
     *   </li>
     * </ol>
     */
    @Override
    public Optional<IdempotencyRecord> acquireOrGet(final String idempotencyKey, final String requestHash) {
        long now = System.currentTimeMillis();
        long expiresAt = now + config.getUnit().toMillis(config.getTtl());

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Optional<IdempotencyRecord> result = doAcquireOrGet(conn, idempotencyKey, requestHash, now, expiresAt);
                conn.commit();
                return result;
            } catch (Exception e) {
                safeRollback(conn);
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("JDBC error in acquireOrGet for key: " + idempotencyKey, e);
        }
    }

    @Override
    public void saveSuccess(final String idempotencyKey, final IdempotencyResponse response) {
        long now = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlUpdateSuccess)) {
            ps.setString(1, IdempotencyStatus.COMPLETED.name());
            ps.setInt(2, response.getStatusCode());
            ps.setString(3, HeaderCodec.encode(response.getHeaders()));
            ps.setBytes(4, response.getBody());
            ps.setLong(5, now);
            ps.setString(6, idempotencyKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("JDBC error in saveSuccess for key: " + idempotencyKey, e);
        }
    }

    @Override
    public void saveFailure(final String idempotencyKey, final String errorMessage) {
        long now = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlUpdateFailure)) {
            ps.setString(1, IdempotencyStatus.FAILED.name());
            ps.setLong(2, now);
            ps.setString(3, idempotencyKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("JDBC error in saveFailure for key: " + idempotencyKey, e);
        }
    }

    @Override
    public Optional<IdempotencyRecord> get(final String idempotencyKey) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlSelectByKey)) {
            ps.setString(1, idempotencyKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("JDBC error in get for key: " + idempotencyKey, e);
        }
    }

    @Override
    public void delete(final String idempotencyKey) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlDeleteByKey)) {
            ps.setString(1, idempotencyKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("JDBC error in delete for key: " + idempotencyKey, e);
        }
    }

    @Override
    public int evictExpired() {
        final long now = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlEvictExpired)) {
            ps.setLong(1, now);
            final int evicted = ps.executeUpdate();
            if (evicted > 0) {
                log.info("[idempotency] Evicted {} expired record(s) from {}", evicted, TABLE);
            }
            return evicted;
        } catch (SQLException e) {
            throw new IllegalStateException("JDBC error in evictExpired", e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Optional<IdempotencyRecord> doAcquireOrGet(final Connection conn,
                                                        final String key,
                                                        final String requestHash,
                                                        final long now,
                                                        final long expiresAt) throws SQLException {
        // 1. Try to INSERT a STARTED lock row.
        boolean inserted = tryInsert(conn, key, requestHash, now, expiresAt);

        if (inserted) {
            log.debug("[idempotency] Lock acquired via INSERT for key='{}'", key);
            return Optional.empty();
        }

        // 2. A row already exists — read it.
        final IdempotencyRecord existing = selectRow(conn, key);
        if (existing == null) {
            // Highly unlikely: row vanished between INSERT fail and SELECT (concurrent delete).
            // Treat as a fresh start — re-try the insert unconditionally.
            inserted = tryInsert(conn, key, requestHash, now, expiresAt);
            return inserted ? Optional.empty() : Optional.empty(); // give up on second miss
        }

        final IdempotencyStatus status = existing.getStatus();

        if (status == IdempotencyStatus.COMPLETED) {
            // Validate hash consistency before replaying.
            if (existing.getRequestHash() != null
                    && requestHash != null
                    && !existing.getRequestHash().equals(requestHash)) {
                throw new IdempotencyMismatchException(
                        "Idempotency key reused with a different request payload");
            }
            log.debug("[idempotency] Replaying COMPLETED record for key='{}'", key);
            return Optional.of(existing);
        }

        // STARTED or FAILED
        if (status == IdempotencyStatus.STARTED
                && existing.getExpiresAt() != null
                && now < existing.getExpiresAt()) {
            // Active lock held by another request.
            log.warn("[idempotency] Conflict: key='{}' is already in progress (lock held until {})",
                    key, existing.getExpiresAt());
            throw new IdempotencyConflictException(
                    "Request with key " + key + " is already in progress.");
        }

        // Expired STARTED or FAILED — evict and re-acquire.
        log.warn("[idempotency] Stale {} record found for key='{}', evicting and re-acquiring",
                status, key);
        deleteRow(conn, key);
        tryInsert(conn, key, requestHash, now, expiresAt);
        return Optional.empty();
    }

    /**
     * Attempts to INSERT a STARTED row. Returns {@code true} if the row was inserted,
     * {@code false} if a duplicate-key conflict was detected (row already exists).
     */
    private boolean tryInsert(Connection conn, String key, String requestHash,
                               long now, long expiresAt) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
            ps.setString(1, key);
            ps.setString(2, IdempotencyStatus.STARTED.name());
            ps.setString(3, requestHash);
            ps.setNull(4, java.sql.Types.INTEGER);   // response_status
            ps.setNull(5, java.sql.Types.VARCHAR);   // response_headers
            ps.setNull(6, java.sql.Types.BLOB);      // response_body
            ps.setLong(7, expiresAt);
            ps.setLong(8, now);
            ps.setLong(9, now);
            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            // SQLState 23xxx = integrity constraint violation (duplicate key)
            if (isDuplicateKey(e)) {
                return false;
            }
            throw e;
        }
    }

    private IdempotencyRecord selectRow(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sqlSelectByKey)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    private void deleteRow(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sqlDeleteByKey)) {
            ps.setString(1, key);
            ps.executeUpdate();
        }
    }

    private IdempotencyRecord mapRow(final ResultSet rs) throws SQLException {
        final String key             = rs.getString("idempotency_key");
        final IdempotencyStatus status = IdempotencyStatus.valueOf(rs.getString("status"));
        final String requestHash     = rs.getString("request_hash");
        final long expiresAt         = rs.getLong("expires_at");

        IdempotencyResponse response = null;
        final int responseStatus = rs.getInt("response_status");
        if (!rs.wasNull()) {
            final String headersText             = rs.getString("response_headers");
            final byte[] body                    = rs.getBytes("response_body");
            final Map<String, List<String>> headers = HeaderCodec.decode(headersText);
            response = new IdempotencyResponse(responseStatus, headers, body);
        }

        return new IdempotencyRecord(key, status, response, expiresAt, requestHash);
    }

    private static void safeRollback(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // Best-effort rollback — swallow to not mask the original exception.
        }
    }

    // -------------------------------------------------------------------------
    // Dialect detection
    // -------------------------------------------------------------------------

    private static String detectDialect(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            String product = conn.getMetaData().getDatabaseProductName().toLowerCase();
            if (product.contains("postgresql") || product.contains("postgres")) return "postgresql";
            if (product.contains("mysql"))    return "mysql";
            if (product.contains("mariadb"))  return "mariadb";
            if (product.contains("h2"))       return "h2";
            return "generic";
        } catch (SQLException e) {
            return "generic";
        }
    }

    private static String buildInsertSql(String dialect) {
        String cols = "(idempotency_key, status, request_hash, response_status, "
                    + "response_headers, response_body, expires_at, created_at, updated_at)";
        String vals = " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return switch (dialect) {
            case "postgresql"            -> "INSERT INTO " + TABLE + " " + cols + vals
                                          + " ON CONFLICT (idempotency_key) DO NOTHING";
            // H2 in MySQL compat mode (MODE=MySQL) supports INSERT IGNORE.
            // Fall through with MySQL/MariaDB.
            case "mysql", "mariadb" -> "INSERT IGNORE INTO " + TABLE + " " + cols + vals;
            default                       -> "INSERT INTO " + TABLE + " " + cols + vals;
        };
    }

    private static boolean isDuplicateKey(SQLException e) {
        // SQLState 23xxx is the standard integrity constraint violation class.
        String state = e.getSQLState();
        return state != null && state.startsWith("23");
    }

    // -------------------------------------------------------------------------
    // Header codec — zero-dependency plain-text serialisation
    //
    // Format:  headerName=value1|value2;anotherHeader=val3
    // Encoding: ; = | are percent-encoded inside name/value tokens.
    // -------------------------------------------------------------------------

    static final class HeaderCodec {

        private HeaderCodec() {}

        static String encode(Map<String, List<String>> headers) {
            if (headers == null || headers.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            boolean firstHeader = true;
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (!firstHeader) sb.append(';');
                firstHeader = false;
                sb.append(pct(entry.getKey())).append('=');
                List<String> values = entry.getValue();
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0) sb.append('|');
                    sb.append(pct(values.get(i)));
                }
            }
            return sb.toString();
        }

        static Map<String, List<String>> decode(String encoded) {
            Map<String, List<String>> result = new HashMap<>();
            if (encoded == null || encoded.isEmpty()) return result;
            for (String headerPart : encoded.split(";", -1)) {
                int eq = headerPart.indexOf('=');
                if (eq < 0) continue;
                String name = unpct(headerPart.substring(0, eq));
                String valsPart = headerPart.substring(eq + 1);
                List<String> values = new ArrayList<>();
                for (String v : valsPart.split("\\|", -1)) {
                    values.add(unpct(v));
                }
                result.put(name, values);
            }
            return result;
        }

        /** Percent-encode the reserved delimiters ; = | */
        private static String pct(String s) {
            return s.replace("%", "%25")
                    .replace(";", "%3B")
                    .replace("=", "%3D")
                    .replace("|", "%7C");
        }

        private static String unpct(String s) {
            return s.replace("%7C", "|")
                    .replace("%3D", "=")
                    .replace("%3B", ";")
                    .replace("%25", "%");
        }
    }
}
