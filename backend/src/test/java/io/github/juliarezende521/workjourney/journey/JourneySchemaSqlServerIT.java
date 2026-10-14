package io.github.juliarezende521.workjourney.journey;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in schema checks against real SQL Server; no HTTP or JPA journey mapping implied. */
@SpringBootTest
class JourneySchemaSqlServerIT {
    private static final String NOW = "2026-10-13T12:00:00.1234567+00:00";
    @Autowired DataSource dataSource;

    @Test
    void preservesPrecisionReferenceDateAndSequenceForEqualInstants() throws Exception {
        try (var connection = transaction()) {
            var owner = owner(connection);
            UUID session = session(connection, owner);
            event(connection, session, owner.userId(), 1, "ENTRY", NOW, NOW);
            event(connection, session, owner.userId(), 2, "PAUSE_START", NOW, NOW);
            try (var query = connection.prepareStatement("""
                    SELECT e.sequence, DATEPART(NANOSECOND, e.occurred_at_utc),
                           DATEPART(TZOFFSET, e.occurred_at_utc), s.work_date,
                           s.reference_time_zone_id, p.name
                    FROM dbo.work_event e JOIN dbo.work_session s ON s.id = e.work_session_id
                    JOIN dbo.employee p ON p.id = s.employee_id
                    WHERE s.id = ? ORDER BY e.sequence
                    """)) {
                query.setString(1, session.toString());
                try (var rows = query.executeQuery()) {
                    for (long sequence = 1; sequence <= 2; sequence++) {
                        assertTrue(rows.next());
                        assertEquals(sequence, rows.getLong(1));
                        assertEquals(123456700, rows.getInt(2));
                        assertEquals(0, rows.getInt(3));
                        assertEquals("2026-10-13", rows.getString(4));
                        assertEquals("America/Sao_Paulo", rows.getString(5));
                        assertEquals("João avaliação", rows.getNString(6));
                    }
                    assertFalse(rows.next());
                }
            }
            connection.rollback();
        }
    }

    @Test
    void rejectsTwoOpenSessionsButAllowsAnotherAfterClosingOnSameDate() throws Exception {
        try (var connection = transaction()) {
            var owner = owner(connection);
            UUID first = session(connection, owner);
            assertSqlError(2601, () -> session(connection, owner));
            execute(connection, "UPDATE dbo.work_session SET state = 'CLOSED', closed_at_utc = last_event_at_utc, version = version + 1 WHERE id = ?", first);
            session(connection, owner);
            session(connection, owner(connection));
            connection.rollback();
        }
    }

    @Test
    void rejectsOrphansAndAuthorsFromAnotherEmployee() throws Exception {
        try (var connection = transaction()) {
            var owner = owner(connection);
            var other = owner(connection);
            // Isolate the ownership FK from the already-open-session unique index.
            assertSqlError(547, () -> session(connection, new Owner(owner.employeeId(), other.userId())));
            UUID session = session(connection, owner);
            assertSqlError(547, () -> event(connection, session, other.userId(), 1, "ENTRY", NOW, NOW));
            assertSqlError(547, () -> event(connection, UUID.randomUUID(), owner.userId(), 1, "ENTRY", NOW, NOW));
            connection.rollback();
        }
    }

    @Test
    void rejectsInvalidStateVersionAndSessionTimes() throws Exception {
        try (var connection = transaction()) {
            UUID session = session(connection, owner(connection));
            assertSqlError(547, () -> execute(connection, "UPDATE dbo.work_session SET state = 'CLOSED' WHERE id = ?", session));
            assertSqlError(547, () -> execute(connection, "UPDATE dbo.work_session SET state = 'working' WHERE id = ?", session));
            assertSqlError(547, () -> execute(connection, "UPDATE dbo.work_session SET version = -1 WHERE id = ?", session));
            assertSqlError(547, () -> execute(connection, "UPDATE dbo.work_session SET last_event_at_utc = '2026-10-12T00:00:00+00:00' WHERE id = ?", session));
            assertSqlError(547, () -> execute(connection, "UPDATE dbo.work_session SET opened_at_utc = SWITCHOFFSET(opened_at_utc, '+01:00') WHERE id = ?", session));
            connection.rollback();
        }
    }

    @Test
    void rejectsDuplicateSequenceInvalidTypesAndEventTimes() throws Exception {
        try (var connection = transaction()) {
            var owner = owner(connection);
            UUID session = session(connection, owner);
            event(connection, session, owner.userId(), 1, "ENTRY", NOW, NOW);
            assertSqlError(2627, () -> event(connection, session, owner.userId(), 1, "ENTRY", NOW, NOW));
            assertSqlError(547, () -> event(connection, session, owner.userId(), 0, "ENTRY", NOW, NOW));
            assertSqlError(547, () -> event(connection, session, owner.userId(), 2, "ENTRY", NOW, NOW));
            assertSqlError(547, () -> event(connection, session, owner.userId(), 2, "pause_start", NOW, NOW));
            assertSqlError(547, () -> event(connection, session, owner.userId(), 2, "EXIT", NOW, "2026-10-12T00:00:00+00:00"));
            assertSqlError(547, () -> event(connection, session, owner.userId(), 2, "EXIT", "2026-10-13T13:00:00.1234567+01:00", NOW));
            connection.rollback();
        }
    }

    @Test
    void rollbackRemovesEventAndRestoresSessionStateAndVersion() throws Exception {
        try (var connection = transaction()) {
            var owner = owner(connection);
            UUID session = session(connection, owner);
            event(connection, session, owner.userId(), 1, "ENTRY", NOW, NOW);
            var beforeCommand = connection.setSavepoint();
            execute(connection, "UPDATE dbo.work_session SET state = 'PAUSED', version = version + 1 WHERE id = ?", session);
            event(connection, session, owner.userId(), 2, "PAUSE_START", NOW, NOW);
            connection.rollback(beforeCommand);
            try (var query = connection.prepareStatement("SELECT state, version, (SELECT COUNT(*) FROM dbo.work_event WHERE work_session_id = s.id) FROM dbo.work_session s WHERE id = ?")) {
                query.setString(1, session.toString());
                try (var row = query.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals("WORKING", row.getString(1));
                    assertEquals(0, row.getLong(2));
                    assertEquals(1, row.getInt(3));
                }
            }
            connection.rollback();
        }
    }

    @Test
    void concurrentConnectionsCommitOnlyOneOpenSession() throws Exception {
        Owner owner;
        try (var connection = transaction()) {
            owner = owner(connection);
            connection.commit(); // Both independent connections must see the fixture.
        }
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> concurrentOpen(owner, ready, start));
            var second = executor.submit(() -> concurrentOpen(owner, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both connections must be ready");
            start.countDown();
            int firstResult = first.get(20, TimeUnit.SECONDS);
            int secondResult = second.get(20, TimeUnit.SECONDS);
            assertTrue((firstResult == 0 && secondResult == 2601)
                    || (firstResult == 2601 && secondResult == 0), "Exactly one insert must commit");
            try (var connection = transaction(); var query = connection.prepareStatement("SELECT COUNT(*) FROM dbo.work_session WHERE employee_id = ? AND closed_at_utc IS NULL")) {
                query.setString(1, owner.employeeId().toString());
                try (var row = query.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals(1, row.getInt(1));
                }
                connection.rollback();
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS), "Workers must stop before fixture cleanup");
            try (var connection = transaction()) {
                execute(connection, "DELETE FROM dbo.work_session WHERE employee_id = ?", owner.employeeId());
                execute(connection, "DELETE FROM dbo.employee WHERE id = ?", owner.employeeId());
                execute(connection, "DELETE FROM dbo.app_user WHERE id = ?", owner.userId());
                connection.commit();
            }
        }
    }

    private int concurrentOpen(Owner owner, CountDownLatch ready, CountDownLatch start) throws Exception {
        try (var connection = transaction()) {
            execute(connection, "SET LOCK_TIMEOUT 10000");
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
            try {
                session(connection, owner);
                connection.commit();
                return 0;
            } catch (SQLException error) {
                connection.rollback();
                return error.getErrorCode();
            }
        }
    }

    private Connection transaction() throws SQLException {
        var connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        return connection;
    }

    private Owner owner(Connection connection) throws SQLException {
        UUID user = UUID.randomUUID();
        UUID employee = UUID.randomUUID();
        execute(connection, "INSERT INTO dbo.app_user (id, email, password_hash, active) VALUES (?, ?, ?, 1)",
                user, user + "@example.invalid", "$2a$12$" + "a".repeat(53));
        execute(connection, "INSERT INTO dbo.employee (id, user_id, name, reference_time_zone_id) VALUES (?, ?, N'João avaliação', 'America/Sao_Paulo')", employee, user);
        return new Owner(employee, user);
    }

    private UUID session(Connection connection, Owner owner) throws SQLException {
        UUID id = UUID.randomUUID();
        execute(connection, """
                INSERT INTO dbo.work_session (id, employee_id, author_user_id, work_date,
                    reference_time_zone_id, state, opened_at_utc, last_event_at_utc, version)
                VALUES (?, ?, ?, '2026-10-13', 'America/Sao_Paulo', 'WORKING', ?, ?, 0)
                """, id, owner.employeeId(), owner.userId(), NOW, NOW);
        return id;
    }

    private void event(Connection connection, UUID session, UUID author, long sequence,
                       String type, String occurred, String recorded) throws SQLException {
        execute(connection, """
                INSERT INTO dbo.work_event (id, work_session_id, author_user_id, sequence,
                    event_type, occurred_at_utc, recorded_at_utc, recorded_time_zone_id) VALUES (?, ?, ?, ?, ?, ?, ?, 'America/Sao_Paulo')
                """, UUID.randomUUID(), session, author, sequence, type, occurred, recorded);
    }

    private void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(15);
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (value instanceof UUID id) statement.setString(i + 1, id.toString());
                else statement.setObject(i + 1, value);
            }
            statement.executeUpdate();
        }
    }

    private void assertSqlError(int code, SqlOperation operation) {
        assertEquals(code, assertThrows(SQLException.class, operation::run).getErrorCode());
    }

    @FunctionalInterface
    private interface SqlOperation { void run() throws SQLException; }
    private record Owner(UUID employeeId, UUID userId) { }
}
