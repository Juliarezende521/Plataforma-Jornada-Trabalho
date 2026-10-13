package io.github.juliarezende521.workjourney.journey;

import io.github.juliarezende521.workjourney.account.*;
import io.github.juliarezende521.workjourney.journey.persistence.JpaWorkSessionStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.juliarezende521.workjourney.journey.WorkEventType.*;

@SpringBootTest
class JourneyPersistenceSqlServerIT {
    private static final Instant ENTRY_TIME = Instant.parse("2026-10-25T00:30:00.123456789Z");
    @Autowired JpaWorkSessionStore store;
    @Autowired AppUserRepository users;
    @Autowired EmployeeRepository employees;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    @Transactional
    void runtimePermissionsSupportAppendWithoutRewritingOriginals() {
        var fixture = create();
        UUID id = fixture.session().getId();
        var original = fixture.session().getEvents().get(0);
        var paused = store.append(id, fixture.employeeId(), 0, PAUSE_START, ENTRY_TIME.plusSeconds(3600));
        assertEquals(1, paused.version());
        // Equal instants must still cause a real parent UPDATE and version increment.
        var closed = store.append(id, fixture.employeeId(), 1, EXIT, ENTRY_TIME.plusSeconds(3600));
        assertEquals(2, closed.version());
        entityManager.clear();
        var reloaded = store.findOwned(id, fixture.employeeId()).orElseThrow();
        assertEquals(2, reloaded.version());
        assertEquals(original, reloaded.session().getEvents().get(0));
        assertEquals(123456700, reloaded.session().getOpenedAtUtc().getNano());
        assertEquals(Duration.ofHours(1), reloaded.session().closedDuration());
        assertEquals("Europe/Amsterdam", reloaded.session().getReferenceTimeZoneId());
        assertEquals(fixture.session().getWorkDate(), reloaded.session().getWorkDate());
        assertEquals(3, reloaded.session().getEvents().size());
    }

    @Test
    @Transactional
    void anotherEmployeeCannotLoadOrAppendSession() {
        var fixture = create();
        UUID otherEmployee = UUID.randomUUID();
        assertTrue(store.findOwned(fixture.session().getId(), otherEmployee).isEmpty());
        assertThrows(InvalidDataAccessApiUsageException.class, () -> store.append(fixture.session().getId(), otherEmployee,
                0, EXIT, ENTRY_TIME.plusSeconds(1)));
    }

    @Test
    @Transactional
    void staleVersionCannotApplyAnotherTransition() {
        var fixture = create();
        var paused = store.append(fixture.session().getId(), fixture.employeeId(), 0, PAUSE_START, ENTRY_TIME.plusSeconds(1));
        assertEquals(1, paused.version());
        assertThrows(OptimisticLockingFailureException.class, () -> store.append(fixture.session().getId(), fixture.employeeId(),
                0, EXIT, ENTRY_TIME.plusSeconds(2)));
    }

    @Test
    @Transactional
    void inconsistentCheckpointIsRejectedInsteadOfSilentlyRepaired() {
        var fixture = create();
        entityManager.createNativeQuery("UPDATE dbo.work_session SET state = 'PAUSED' WHERE id = :id")
                .setParameter("id", fixture.session().getId()).executeUpdate();
        entityManager.clear();
        assertThrows(InvalidDataAccessApiUsageException.class, () -> store.findOwned(fixture.session().getId(), fixture.employeeId()));
    }

    @Test
    @Transactional
    void createRejectsAnAggregateWithUnversionedTransitions() {
        var fixture = create();
        var extra = WorkSession.open(fixture.employeeId(), fixture.userId(), "Europe/Amsterdam", ENTRY_TIME);
        extra.record(PAUSE_START, ENTRY_TIME.plusSeconds(1));
        assertThrows(InvalidDataAccessApiUsageException.class, () -> store.create(extra));
    }

    @Test
    void outerTransactionFailureRollsBackFlushedEventStateAndVersion() {
        var fixture = transaction().execute(status -> create());
        assertNotNull(fixture);
        try {
            assertThrows(SimulatedFailure.class, () -> transaction().execute(status -> {
                store.append(fixture.session().getId(), fixture.employeeId(), 0, PAUSE_START, ENTRY_TIME.plusSeconds(1));
                throw new SimulatedFailure();
            }));
            var reloaded = store.findOwned(fixture.session().getId(), fixture.employeeId()).orElseThrow();
            assertEquals(0, reloaded.version());
            assertEquals(WorkState.WORKING, reloaded.session().getState());
            assertEquals(fixture.session().getEvents(), reloaded.session().getEvents());
        } finally { cleanup(fixture); }
    }

    @Test
    void simultaneousTransitionsCommitOneVersionAndOneNewEvent() throws Exception {
        var fixture = transaction().execute(status -> create());
        assertNotNull(fixture);
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var pause = executor.submit(() -> compete(fixture, PAUSE_START, ready, start));
            var exit = executor.submit(() -> compete(fixture, EXIT, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            boolean pauseWon = pause.get(25, TimeUnit.SECONDS);
            boolean exitWon = exit.get(25, TimeUnit.SECONDS);
            assertNotEquals(pauseWon, exitWon, "Exactly one command must commit");
            var reloaded = store.findOwned(fixture.session().getId(), fixture.employeeId()).orElseThrow();
            assertEquals(1, reloaded.version());
            assertEquals(2, reloaded.session().getEvents().size());
            assertEquals(pauseWon ? WorkState.PAUSED : WorkState.CLOSED, reloaded.session().getState());
            assertEquals(fixture.session().getEvents().get(0), reloaded.session().getEvents().get(0));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(25, TimeUnit.SECONDS));
            cleanup(fixture);
        }
    }

    private boolean compete(Fixture fixture, WorkEventType type, CountDownLatch ready, CountDownLatch start) {
        try {
            transaction().execute(status -> {
                var loaded = store.findOwned(fixture.session().getId(), fixture.employeeId()).orElseThrow();
                assertEquals(0, loaded.version());
                ready.countDown();
                await(start);
                store.append(fixture.session().getId(), fixture.employeeId(), loaded.version(), type, ENTRY_TIME.plusSeconds(1));
                return null;
            });
            return true;
        } catch (OptimisticLockingFailureException | OptimisticLockException conflict) {
            return false;
        }
    }

    private void await(CountDownLatch start) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Barrier timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted before command", interrupted);
        }
    }

    private Fixture create() {
        var user = users.save(new AppUser(UUID.randomUUID() + "@example.invalid", "$2a$12$" + "a".repeat(53)));
        var employee = employees.save(new Employee(user, "João avaliação", "Europe/Amsterdam"));
        var session = WorkSession.open(employee.getId(), user.getId(), employee.getReferenceTimeZoneId(), ENTRY_TIME);
        assertEquals(0, store.create(session).version());
        return new Fixture(employee.getId(), user.getId(), session);
    }

    private TransactionTemplate transaction() {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(20);
        return transaction;
    }

    private void cleanup(Fixture fixture) {
        transaction().execute(status -> {
            entityManager.createNativeQuery("DELETE FROM dbo.work_event WHERE work_session_id = :id").setParameter("id", fixture.session().getId()).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM dbo.work_session WHERE id = :id").setParameter("id", fixture.session().getId()).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM dbo.employee WHERE id = :id").setParameter("id", fixture.employeeId()).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM dbo.app_user WHERE id = :id").setParameter("id", fixture.userId()).executeUpdate();
            return null;
        });
    }

    private record Fixture(UUID employeeId, UUID userId, WorkSession session) { }
    private static final class SimulatedFailure extends RuntimeException { }
}
