package io.github.juliarezende521.workjourney.journey.marking;

import io.github.juliarezende521.workjourney.account.*;
import io.github.juliarezende521.workjourney.auth.AccountPrincipal;
import io.github.juliarezende521.workjourney.journey.persistence.JpaWorkSessionStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static io.github.juliarezende521.workjourney.journey.WorkEventType.*;
import static io.github.juliarezende521.workjourney.journey.marking.MarkingResult.Code.*;

@SpringBootTest
class OnlineMarkingSqlServerIT {
    private static final Instant NOW = Instant.parse("2026-10-13T14:00:00.123456789Z");
    @Autowired OnlineMarkingService service;
    @Autowired JpaWorkSessionStore sessions;
    @Autowired AppUserRepository users;
    @Autowired EmployeeRepository employees;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean Clock clock;

    @BeforeEach void time() { when(clock.instant()).thenReturn(NOW); }
    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test
    @Transactional
    void runtimePermissionsPreserveOriginalReceipts() {
        var owner = owner();
        authenticate(owner);
        UUID key = UUID.randomUUID();
        var entry = service.execute(key, new MarkingCommand(ENTRY, null));
        assertEquals(201, entry.httpStatus());
        assertEquals(123456700, entry.occurredAtUtc().getNano());
        assertEquals(entry.occurredAtUtc(), entry.confirmedAtUtc());
        assertEquals("Europe/Amsterdam", sessions.findCurrentOwned(owner.employeeId()).orElseThrow().session().getReferenceTimeZoneId());
        when(clock.instant()).thenReturn(NOW.plusSeconds(3600));
        assertEquals(entry, service.execute(key, new MarkingCommand(ENTRY, null)));
        UUID exitKey = UUID.randomUUID();
        var exit = service.execute(exitKey, new MarkingCommand(EXIT, entry.workSessionId()));
        assertEquals(201, exit.httpStatus());
        when(clock.instant()).thenReturn(NOW.plusSeconds(7200));
        var next = service.execute(UUID.randomUUID(), new MarkingCommand(ENTRY, null));
        assertNotEquals(entry.workSessionId(), next.workSessionId());
        entityManager.clear();
        assertEquals(entry, service.execute(key, new MarkingCommand(ENTRY, null)));
        assertEquals(entry, service.lookup(key).orElseThrow());
        assertEquals(exit, service.execute(exitKey, new MarkingCommand(EXIT, entry.workSessionId())));
        assertEquals(TARGET_CHANGED, service.execute(UUID.randomUUID(), new MarkingCommand(EXIT, entry.workSessionId())).code());
        assertEquals(4, count("idempotency_request", owner));
        assertEquals(3, eventCount(owner));
    }

    @Test
    @Transactional
    void rejectedReceiptReplaysAfterStateChangesAndKeyReuseNeverRewritesIt() {
        var owner = owner();
        authenticate(owner);
        UUID firstKey = UUID.randomUUID();
        var entry = service.execute(firstKey, new MarkingCommand(ENTRY, null));
        UUID rejectedKey = UUID.randomUUID();
        var rejected = service.execute(rejectedKey, new MarkingCommand(ENTRY, null));
        assertEquals(ALREADY_OPEN, rejected.code());
        assertEquals(409, rejected.httpStatus());
        assertEquals(KEY_REUSED, service.execute(firstKey, new MarkingCommand(EXIT, entry.workSessionId())).code());
        assertEquals(entry, service.lookup(firstKey).orElseThrow());
        service.execute(UUID.randomUUID(), new MarkingCommand(EXIT, entry.workSessionId()));
        assertEquals(rejected, service.execute(rejectedKey, new MarkingCommand(ENTRY, null)));
        assertTrue(sessions.findCurrentOwned(owner.employeeId()).isEmpty());
        assertEquals(3, count("idempotency_request", owner));
        assertEquals(2, eventCount(owner));
    }

    @Test
    @Transactional
    void receiptKeysAreScopedAndForeignTargetsDoNotExposeOrChangeOtherEmployees() {
        var first = owner();
        var second = owner();
        UUID sharedKey = UUID.randomUUID();
        authenticate(first);
        var firstEntry = service.execute(sharedKey, new MarkingCommand(ENTRY, null));
        authenticate(second);
        assertTrue(service.lookup(sharedKey).isEmpty());
        var secondEntry = service.execute(sharedKey, new MarkingCommand(ENTRY, null));
        assertEquals(201, secondEntry.httpStatus());
        assertNotEquals(firstEntry.workSessionId(), secondEntry.workSessionId());
        var foreign = service.execute(UUID.randomUUID(), new MarkingCommand(EXIT, firstEntry.workSessionId()));
        assertEquals(TARGET_CHANGED, foreign.code());
        assertNull(foreign.workSessionId());
        assertNull(foreign.eventId());
        var nonexistent = service.execute(UUID.randomUUID(), new MarkingCommand(EXIT, UUID.randomUUID()));
        assertEquals(foreign.code(), nonexistent.code());
        assertEquals(1, eventCount(first));
        assertEquals(1, eventCount(second));
    }

    @Test
    @Transactional
    void commitsDefinitiveInvalidTransitionAndClockRegressionWithoutEvents() {
        var owner = owner();
        authenticate(owner);
        assertEquals(NO_OPEN_SESSION, service.execute(UUID.randomUUID(), new MarkingCommand(EXIT, UUID.randomUUID())).code());
        var entry = service.execute(UUID.randomUUID(), new MarkingCommand(ENTRY, null));
        assertEquals(INVALID_TRANSITION, service.execute(UUID.randomUUID(), new MarkingCommand(PAUSE_END, entry.workSessionId())).code());
        when(clock.instant()).thenReturn(NOW.minusSeconds(1));
        assertEquals(CLOCK_REGRESSION, service.execute(UUID.randomUUID(), new MarkingCommand(EXIT, entry.workSessionId())).code());
        assertEquals(4, count("idempotency_request", owner));
        assertEquals(1, eventCount(owner));
    }

    @Test
    @Transactional
    void regressiveClockCannotOpenSessionBeforePreviousClosure() {
        var owner = owner();
        authenticate(owner);
        var entry = service.execute(UUID.randomUUID(), new MarkingCommand(ENTRY, null));
        when(clock.instant()).thenReturn(NOW.plusSeconds(3600));
        service.execute(UUID.randomUUID(), new MarkingCommand(EXIT, entry.workSessionId()));
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        assertEquals(CLOCK_REGRESSION, service.execute(UUID.randomUUID(), new MarkingCommand(ENTRY, null)).code());
        assertTrue(sessions.findCurrentOwned(owner.employeeId()).isEmpty());
        assertEquals(2, eventCount(owner));
    }

    @Test
    void absentReceiptWhileFirstCommandIsInFlightDoesNotPredictItsFinalResult() throws Exception {
        var owner = transaction().execute(status -> owner());
        assertNotNull(owner);
        var inFlight = new CountDownLatch(1);
        var proceed = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        UUID key = UUID.randomUUID();
        when(clock.instant()).thenAnswer(invocation -> {
            inFlight.countDown();
            assertTrue(proceed.await(10, TimeUnit.SECONDS));
            return NOW;
        });
        try {
            var marking = executor.submit(() -> {
                authenticate(owner);
                try { return service.execute(key, new MarkingCommand(ENTRY, null)); }
                finally { SecurityContextHolder.clearContext(); }
            });
            assertTrue(inFlight.await(10, TimeUnit.SECONDS));
            var lookup = executor.submit(() -> {
                authenticate(owner);
                try { return service.lookup(key); }
                finally { SecurityContextHolder.clearContext(); }
            });
            assertTrue(lookup.get(5, TimeUnit.SECONDS).isEmpty());
            proceed.countDown();
            var recorded = marking.get(20, TimeUnit.SECONDS);
            authenticate(owner);
            assertEquals(recorded, service.lookup(key).orElseThrow());
        } finally {
            proceed.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(25, TimeUnit.SECONDS));
            cleanup(owner);
        }
    }

    @Test
    @Transactional
    void inactiveAccountCannotReadExistingReceiptDespiteActiveSessionSnapshot() {
        var owner = owner();
        authenticate(owner);
        UUID key = UUID.randomUUID();
        service.execute(key, new MarkingCommand(ENTRY, null));
        entityManager.createNativeQuery("UPDATE dbo.app_user SET active = 0 WHERE id = :id")
                .setParameter("id", owner.userId().toString()).executeUpdate();
        entityManager.clear();
        assertThrows(AccessDeniedException.class, () -> service.lookup(key));
    }

    @Test
    @Transactional
    void mismatchedUserAndEmployeeClaimsCannotCreateReceipt() {
        var first = owner();
        var second = owner();
        authenticate(new Owner(first.employeeId(), second.userId()));
        assertThrows(AccessDeniedException.class, () -> service.execute(UUID.randomUUID(), new MarkingCommand(ENTRY, null)));
    }

    @Test
    void rollbackAfterReceiptFlushRemovesReceiptEventAndSessionTogether() {
        var owner = transaction().execute(status -> owner());
        assertNotNull(owner);
        authenticate(owner);
        UUID key = UUID.randomUUID();
        try {
            assertThrows(SimulatedFailure.class, () -> transaction().execute(status -> {
                service.execute(key, new MarkingCommand(ENTRY, null));
                throw new SimulatedFailure();
            }));
            assertTrue(service.lookup(key).isEmpty());
            assertTrue(sessions.findCurrentOwned(owner.employeeId()).isEmpty());
            assertEquals(0, transaction().execute(status -> eventCount(owner)).intValue());
        } finally { cleanup(owner); }
    }

    @Test
    void concurrentSameKeyCreatesOneReceiptAndOneEventWithIdenticalResults() throws Exception {
        var owner = transaction().execute(status -> owner());
        assertNotNull(owner);
        UUID key = UUID.randomUUID();
        try {
            var results = race(owner, key, key, new MarkingCommand(ENTRY, null));
            assertEquals(results[0], results[1]);
            assertEquals(201, results[0].httpStatus());
            assertEquals(1, transaction().execute(status -> count("idempotency_request", owner)).intValue());
            assertEquals(1, transaction().execute(status -> eventCount(owner)).intValue());
        } finally { cleanup(owner); }
    }

    @Test
    void concurrentDifferentEntryKeysCommitOneEntryAndOneTerminalConflict() throws Exception {
        var owner = transaction().execute(status -> owner());
        assertNotNull(owner);
        try {
            var results = race(owner, UUID.randomUUID(), UUID.randomUUID(), new MarkingCommand(ENTRY, null));
            assertEquals(1, java.util.Arrays.stream(results).filter(result -> result.code() == RECORDED).count());
            assertEquals(1, java.util.Arrays.stream(results).filter(result -> result.code() == ALREADY_OPEN).count());
            assertEquals(2, transaction().execute(status -> count("idempotency_request", owner)).intValue());
            assertEquals(1, transaction().execute(status -> eventCount(owner)).intValue());
        } finally { cleanup(owner); }
    }

    @Test
    void concurrentPauseCommandsCommitOnePauseAndOneTerminalInvalidTransition() throws Exception {
        var owner = transaction().execute(status -> owner());
        assertNotNull(owner);
        authenticate(owner);
        try {
            var entry = service.execute(UUID.randomUUID(), new MarkingCommand(ENTRY, null));
            var results = race(owner, UUID.randomUUID(), UUID.randomUUID(), new MarkingCommand(PAUSE_START, entry.workSessionId()));
            assertEquals(1, java.util.Arrays.stream(results).filter(result -> result.code() == RECORDED).count());
            assertEquals(1, java.util.Arrays.stream(results).filter(result -> result.code() == INVALID_TRANSITION).count());
            assertEquals(1, sessions.findCurrentOwned(owner.employeeId()).orElseThrow().version());
            assertEquals(3, transaction().execute(status -> count("idempotency_request", owner)).intValue());
            assertEquals(2, transaction().execute(status -> eventCount(owner)).intValue());
        } finally { cleanup(owner); }
    }

    @Test
    @Transactional
    void databaseRejectsReceiptReferencingAnotherEmployeesSuccess() {
        var first = owner();
        var second = owner();
        authenticate(first);
        UUID key = UUID.randomUUID();
        service.execute(key, new MarkingCommand(ENTRY, null));
        authenticate(second);
        var other = service.execute(UUID.randomUUID(), new MarkingCommand(ENTRY, null));
        var error = assertThrows(PersistenceException.class, () -> entityManager.createNativeQuery("""
                UPDATE dbo.idempotency_request SET result_work_session_id = :session, result_event_id = :event
                WHERE employee_id = :employee AND request_key = :key
                """).setParameter("session", other.workSessionId().toString()).setParameter("event", other.eventId().toString())
                .setParameter("employee", first.employeeId().toString()).setParameter("key", key.toString()).executeUpdate());
        assertSqlCode(547, error);
    }

    @Test
    @Transactional
    void databaseRejectsDuplicateReceiptKeyIndependentlyOfServiceLock() {
        var owner = owner();
        authenticate(owner);
        UUID key = UUID.randomUUID();
        service.execute(key, new MarkingCommand(ENTRY, null));
        var error = assertThrows(PersistenceException.class, () -> entityManager.createNativeQuery("""
                INSERT INTO dbo.idempotency_request (id, employee_id, operation, request_key, payload_hash,
                    command_type, expected_work_session_id, http_status, result_code, result_work_session_id,
                    result_event_id, occurred_at_utc, confirmed_at_utc)
                SELECT NEWID(), employee_id, operation, request_key, payload_hash, command_type,
                    expected_work_session_id, http_status, result_code, result_work_session_id,
                    result_event_id, occurred_at_utc, confirmed_at_utc FROM dbo.idempotency_request
                WHERE employee_id = :employee AND request_key = :key
                """).setParameter("employee", owner.employeeId().toString()).setParameter("key", key.toString()).executeUpdate());
        assertSqlCode(2627, error);
    }

    private MarkingResult[] race(Owner owner, UUID firstKey, UUID secondKey, MarkingCommand command) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> executeAfterBarrier(owner, firstKey, command, ready, start));
            var second = executor.submit(() -> executeAfterBarrier(owner, secondKey, command, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            return new MarkingResult[] { first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS) };
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    private MarkingResult executeAfterBarrier(Owner owner, UUID key, MarkingCommand command,
                                             CountDownLatch ready, CountDownLatch start) throws Exception {
        authenticate(owner);
        ready.countDown();
        try {
            assertTrue(start.await(10, TimeUnit.SECONDS));
            return service.execute(key, command);
        } finally { SecurityContextHolder.clearContext(); }
    }

    private Owner owner() {
        var user = users.save(new AppUser(UUID.randomUUID() + "@example.invalid", "$2a$12$" + "a".repeat(53)));
        var employee = employees.save(new Employee(user, "Colaborador fictício", "Europe/Amsterdam"));
        employees.flush();
        return new Owner(employee.getId(), user.getId());
    }

    private void authenticate(Owner owner) {
        // Session snapshot intentionally has a different zone: ENTRY must use current DB configuration.
        var principal = new AccountPrincipal(new AccountPrincipal.Identity(owner.userId(), owner.employeeId(),
                "test@example.invalid", "Teste", "America/New_York"), "test-only-hash", true);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private int count(String table, Owner owner) {
        String sql = switch (table) {
            case "idempotency_request" -> "SELECT COUNT(*) FROM dbo.idempotency_request WHERE employee_id = :employee";
            default -> throw new IllegalArgumentException("Unsupported test table");
        };
        return ((Number) entityManager.createNativeQuery(sql).setParameter("employee", owner.employeeId().toString()).getSingleResult()).intValue();
    }

    private int eventCount(Owner owner) {
        return ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM dbo.work_event e JOIN dbo.work_session s ON s.id = e.work_session_id WHERE s.employee_id = :employee")
                .setParameter("employee", owner.employeeId().toString()).getSingleResult()).intValue();
    }

    private TransactionTemplate transaction() {
        var template = new TransactionTemplate(transactionManager);
        template.setTimeout(20);
        return template;
    }

    private void cleanup(Owner owner) {
        transaction().execute(status -> {
            entityManager.createNativeQuery("DELETE FROM dbo.idempotency_request WHERE employee_id = :employee").setParameter("employee", owner.employeeId().toString()).executeUpdate();
            entityManager.createNativeQuery("DELETE e FROM dbo.work_event e JOIN dbo.work_session s ON s.id = e.work_session_id WHERE s.employee_id = :employee").setParameter("employee", owner.employeeId().toString()).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM dbo.work_session WHERE employee_id = :employee").setParameter("employee", owner.employeeId().toString()).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM dbo.employee WHERE id = :employee").setParameter("employee", owner.employeeId().toString()).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM dbo.app_user WHERE id = :user").setParameter("user", owner.userId().toString()).executeUpdate();
            return null;
        });
    }

    private void assertSqlCode(int expected, Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) { assertEquals(expected, sql.getErrorCode()); return; }
        }
        fail("Expected SQL Server error code, not a generic persistence failure");
    }

    private record Owner(UUID employeeId, UUID userId) { }
    private static final class SimulatedFailure extends RuntimeException { }
}
