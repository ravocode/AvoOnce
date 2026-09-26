package io.github.ravocode.avoonce.jdbc;

import io.github.ravocode.avoonce.core.config.IdempotencyConfig;
import io.github.ravocode.avoonce.core.domain.IdempotencyRecord;
import io.github.ravocode.avoonce.core.domain.IdempotencyResponse;
import io.github.ravocode.avoonce.core.domain.IdempotencyStatus;
import io.github.ravocode.avoonce.core.exception.IdempotencyConflictException;
import io.github.ravocode.avoonce.core.exception.IdempotencyMismatchException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JdbcIdempotencyRepository} using an H2 in-memory database.
 * No Spring context — pure JDBC.
 */
class JdbcIdempotencyRepositoryTest {

    private static DataSource dataSource;
    private JdbcIdempotencyRepository repository;

    @BeforeAll
    static void setUpDataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        // Shared, named in-memory DB so all connections see the same data.
        ds.setURL("jdbc:h2:mem:idempotency_test;DB_CLOSE_DELAY=-1;MODE=MySQL");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;

        // Initialise schema once.
        new JdbcIdempotencyTableInitializer().initialize(dataSource);
    }

    @BeforeEach
    void setUpRepository() {
        // Short TTL (10 seconds) and lock timeout (1 second) for tests.
        IdempotencyConfig config = new IdempotencyConfig(10, TimeUnit.SECONDS, 1, TimeUnit.SECONDS);
        repository = new JdbcIdempotencyRepository(dataSource, config);
    }

    @AfterEach
    void cleanUp() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM idempotency_records")) {
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // acquireOrGet — first time (no existing record)
    // -------------------------------------------------------------------------

    @Test
    void acquireOrGet_firstCall_returnsEmpty_andCreatesStartedRow() {
        String key = uniqueKey();
        Optional<IdempotencyRecord> result = repository.acquireOrGet(key);

        assertTrue(result.isEmpty(), "Should return empty on first acquire");

        Optional<IdempotencyRecord> stored = repository.get(key);
        assertTrue(stored.isPresent());
        assertEquals(IdempotencyStatus.STARTED, stored.get().getStatus());
    }

    // -------------------------------------------------------------------------
    // acquireOrGet — subsequent call on COMPLETED record
    // -------------------------------------------------------------------------

    @Test
    void acquireOrGet_onCompletedRecord_replaysResponse() {
        String key = uniqueKey();
        repository.acquireOrGet(key);

        IdempotencyResponse saved = sampleResponse();
        repository.saveSuccess(key, saved);

        Optional<IdempotencyRecord> result = repository.acquireOrGet(key);
        assertTrue(result.isPresent(), "Should return the cached record");
        assertEquals(200, result.get().getResponse().getStatusCode());
        assertArrayEquals("body".getBytes(), result.get().getResponse().getBody());
    }

    // -------------------------------------------------------------------------
    // acquireOrGet — conflict (active STARTED record)
    // -------------------------------------------------------------------------

    @Test
    void acquireOrGet_onActiveStartedRecord_throwsConflict() {
        String key = uniqueKey();
        repository.acquireOrGet(key); // acquires lock (STARTED)

        assertThrows(IdempotencyConflictException.class,
                () -> repository.acquireOrGet(key),
                "Second acquire on active STARTED should throw conflict");
    }

    // -------------------------------------------------------------------------
    // acquireOrGet — expired STARTED record allows re-acquire
    // -------------------------------------------------------------------------

    @Test
    void acquireOrGet_onExpiredStartedRecord_allowsReAcquire() throws SQLException {
        String key = uniqueKey();
        // Insert an already-expired STARTED row directly.
        insertStartedRow(key, System.currentTimeMillis() - 5000 /* expired 5s ago */);

        Optional<IdempotencyRecord> result = repository.acquireOrGet(key);
        assertTrue(result.isEmpty(), "Should re-acquire lock on expired STARTED");
    }

    // -------------------------------------------------------------------------
    // acquireOrGet — FAILED record allows re-acquire
    // -------------------------------------------------------------------------

    @Test
    void acquireOrGet_onFailedRecord_allowsRetry() {
        String key = uniqueKey();
        repository.acquireOrGet(key);
        repository.saveFailure(key, "something went wrong");

        Optional<IdempotencyRecord> result = repository.acquireOrGet(key);
        assertTrue(result.isEmpty(), "Should allow re-acquire after FAILED");
    }

    // -------------------------------------------------------------------------
    // acquireOrGet — hash mismatch
    // -------------------------------------------------------------------------

    @Test
    void acquireOrGet_withHashMismatch_throwsMismatchException() {
        String key = uniqueKey();
        repository.acquireOrGet(key, "hash-a");
        repository.saveSuccess(key, sampleResponse());

        assertThrows(IdempotencyMismatchException.class,
                () -> repository.acquireOrGet(key, "hash-b"),
                "Different hash on COMPLETED record should throw mismatch");
    }

    @Test
    void acquireOrGet_withSameHash_replaysResponse() {
        String key = uniqueKey();
        repository.acquireOrGet(key, "hash-a");
        repository.saveSuccess(key, sampleResponse());

        Optional<IdempotencyRecord> result = repository.acquireOrGet(key, "hash-a");
        assertTrue(result.isPresent());
    }

    // -------------------------------------------------------------------------
    // saveSuccess
    // -------------------------------------------------------------------------

    @Test
    void saveSuccess_persistsAllResponseFields() {
        String key = uniqueKey();
        repository.acquireOrGet(key);

        Map<String, List<String>> headers = Map.of(
                "Content-Type", List.of("application/json"),
                "X-Custom", List.of("val1", "val2")
        );
        IdempotencyResponse response = new IdempotencyResponse(201, headers, "ok".getBytes());
        repository.saveSuccess(key, response);

        Optional<IdempotencyRecord> stored = repository.get(key);
        assertTrue(stored.isPresent());
        assertEquals(IdempotencyStatus.COMPLETED, stored.get().getStatus());
        assertEquals(201, stored.get().getResponse().getStatusCode());
        assertArrayEquals("ok".getBytes(), stored.get().getResponse().getBody());

        Map<String, List<String>> storedHeaders = stored.get().getResponse().getHeaders();
        assertEquals(List.of("application/json"), storedHeaders.get("Content-Type"));
        assertEquals(List.of("val1", "val2"), storedHeaders.get("X-Custom"));
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_removesRecord() {
        String key = uniqueKey();
        repository.acquireOrGet(key);
        assertFalse(repository.get(key).isEmpty());

        repository.delete(key);
        assertTrue(repository.get(key).isEmpty());
    }

    // -------------------------------------------------------------------------
    // evictExpired
    // -------------------------------------------------------------------------

    @Test
    void evictExpired_deletesOnlyExpiredRows() throws SQLException {
        String expiredKey = uniqueKey();
        String activeKey  = uniqueKey();

        insertStartedRow(expiredKey, System.currentTimeMillis() - 10_000); // expired
        repository.acquireOrGet(activeKey);                                // active (future TTL)

        int deleted = repository.evictExpired();

        assertEquals(1, deleted, "Only the expired row should be evicted");
        assertTrue(repository.get(expiredKey).isEmpty(),    "Expired record should be gone");
        assertFalse(repository.get(activeKey).isEmpty(),    "Active record must remain");
    }

    // -------------------------------------------------------------------------
    // HeaderCodec round-trip
    // -------------------------------------------------------------------------

    @Test
    void headerCodec_roundTrip_preservesSpecialCharacters() {
        Map<String, List<String>> headers = Map.of(
                "X-Custom;Header", List.of("val=1", "val|2"),
                "Normal", List.of("plain")
        );
        String encoded = JdbcIdempotencyRepository.HeaderCodec.encode(headers);
        Map<String, List<String>> decoded = JdbcIdempotencyRepository.HeaderCodec.decode(encoded);
        assertEquals(headers, decoded);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String uniqueKey() {
        return UUID.randomUUID().toString();
    }

    private IdempotencyResponse sampleResponse() {
        return new IdempotencyResponse(200, Map.of(), "body".getBytes());
    }

    /** Directly inserts a STARTED row with the given expiry to simulate test conditions. */
    private void insertStartedRow(String key, long expiresAt) throws SQLException {
        String sql = "INSERT INTO idempotency_records "
                + "(idempotency_key, status, request_hash, response_status, response_headers, "
                + "response_body, expires_at, created_at, updated_at) "
                + "VALUES (?, 'STARTED', NULL, NULL, NULL, NULL, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            ps.setString(1, key);
            ps.setLong(2, expiresAt);
            ps.setLong(3, now);
            ps.setLong(4, now);
            ps.executeUpdate();
        }
    }
}
