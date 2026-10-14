package io.github.juliarezende521.workjourney.journey.persistence;

import io.github.juliarezende521.workjourney.journey.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Internal storage API. The marking service supplies authenticated identity, Clock and receipt transaction. */
@Repository
@Profile("!bootstrap")
public class JpaWorkSessionStore {
    private final EntityManager entityManager;

    public JpaWorkSessionStore(EntityManager entityManager) { this.entityManager = entityManager; }

    @Transactional
    public StoredSession create(WorkSession session) {
        Objects.requireNonNull(session, "Session is required");
        var events = session.getEvents();
        if (events.size() != 1 || events.get(0).type() != WorkEventType.ENTRY) {
            throw new IllegalArgumentException("Create accepts only the initial entry");
        }
        var row = new WorkSessionRow(session);
        entityManager.persist(row);
        // Insert the parent first; there is no cascading relationship to order the FK insert.
        entityManager.flush();
        entityManager.persist(new WorkEventRow(events.get(0)));
        entityManager.flush();
        return new StoredSession(session, row.version());
    }

    @Transactional(readOnly = true)
    public Optional<StoredSession> findOwned(UUID id, UUID employeeId) {
        return ownedRow(id, employeeId).map(row -> new StoredSession(restoreConsistent(row, id), row.version()));
    }

    @Transactional(readOnly = true)
    public Optional<StoredSession> findCurrentOwned(UUID employeeId) {
        Objects.requireNonNull(employeeId, "Employee ID is required");
        return entityManager.createQuery("SELECT s FROM WorkSessionRow s WHERE s.employeeId = :employee AND s.closedAtUtc IS NULL", WorkSessionRow.class)
                .setParameter("employee", employeeId).getResultList().stream().findFirst()
                .map(row -> new StoredSession(restoreConsistent(row, row.id()), row.version()));
    }

    @Transactional(readOnly = true)
    public Optional<StoredSession> findLatestOwnedOnDate(UUID employeeId, java.time.LocalDate date) {
        return entityManager.createQuery("SELECT s FROM WorkSessionRow s WHERE s.employeeId=:employee AND s.workDate=:date ORDER BY s.openedAtUtc DESC, s.id DESC", WorkSessionRow.class)
                .setParameter("employee",employeeId).setParameter("date",date).setMaxResults(1).getResultList().stream().findFirst()
                .map(row -> new StoredSession(restoreConsistent(row,row.id()),row.version()));
    }

    @Transactional(readOnly = true)
    public Optional<Instant> latestClosedAt(UUID employeeId) {
        Objects.requireNonNull(employeeId, "Employee ID is required");
        var closed = entityManager.createQuery("SELECT MAX(s.closedAtUtc) FROM WorkSessionRow s WHERE s.employeeId = :employee", java.time.OffsetDateTime.class)
                .setParameter("employee", employeeId).getSingleResult();
        return Optional.ofNullable(closed).map(java.time.OffsetDateTime::toInstant);
    }

    @Transactional
    public StoredSession append(UUID id, UUID employeeId, long expectedVersion,
                                WorkEventType type, Instant serverInstant) {
        return append(id, employeeId, expectedVersion, type, serverInstant, null);
    }

    @Transactional
    public StoredSession append(UUID id, UUID employeeId, long expectedVersion, WorkEventType type, Instant serverInstant, String recordedZone) {
        var row = ownedRow(id, employeeId).orElseThrow(() -> new IllegalArgumentException("Session is not visible"));
        if (expectedVersion < 0 || row.version() != expectedVersion) {
            throw new OptimisticLockException("Session version changed");
        }
        var session = restoreConsistent(row, id);
        session.record(type, serverInstant, recordedZone == null ? session.getReferenceTimeZoneId() : recordedZone);
        row.updateCheckpoint(session);
        // A valid transition always changes state, including when timestamps are equal.
        // Flush the @Version UPDATE before inserting the new event; failures roll back both.
        entityManager.flush();
        var originals = session.getEvents();
        entityManager.persist(new WorkEventRow(originals.get(originals.size() - 1)));
        entityManager.flush();
        return new StoredSession(session, row.version());
    }

    /** Bounded parent page + one event query, avoiding one query per journey. */
    public List<WorkSession> history(UUID employee, java.time.LocalDate from, java.time.LocalDate to,
                                     int offset, int size) {
        var rows = entityManager.createQuery("SELECT s FROM WorkSessionRow s WHERE s.employeeId=:employee AND s.workDate BETWEEN :from AND :to ORDER BY s.workDate DESC, s.openedAtUtc DESC, s.id DESC", WorkSessionRow.class)
                .setParameter("employee", employee).setParameter("from", from).setParameter("to", to)
                .setFirstResult(offset).setMaxResults(size).getResultList();
        if (rows.isEmpty()) return List.of();
        var ids = rows.stream().map(WorkSessionRow::id).toList();
        var eventRows = entityManager.createQuery("SELECT e FROM WorkEventRow e WHERE e.workSessionId IN :ids ORDER BY e.sequence", WorkEventRow.class)
                .setParameter("ids", ids).getResultList();
        var grouped = eventRows.stream().map(WorkEventRow::toDomain)
                .collect(java.util.stream.Collectors.groupingBy(WorkEvent::workSessionId));
        var restored = rows.stream().map(row -> row.restore(grouped.getOrDefault(row.id(), List.of()))).toList();
        // Avoid accumulating managed entities while scanning summary batches. DB locks survive detach.
        eventRows.forEach(entityManager::detach);
        rows.forEach(entityManager::detach);
        return restored;
    }

    public long historyCount(UUID employee, java.time.LocalDate from, java.time.LocalDate to) {
        return entityManager.createQuery("SELECT COUNT(s) FROM WorkSessionRow s WHERE s.employeeId=:employee AND s.workDate BETWEEN :from AND :to", Long.class)
                .setParameter("employee", employee).setParameter("from", from).setParameter("to", to).getSingleResult();
    }

    private Optional<WorkSessionRow> ownedRow(UUID id, UUID employeeId) {
        Objects.requireNonNull(id, "Session ID is required");
        Objects.requireNonNull(employeeId, "Employee ID is required");
        return entityManager.createQuery("SELECT s FROM WorkSessionRow s WHERE s.id = :id AND s.employeeId = :employee", WorkSessionRow.class)
                .setParameter("id", id).setParameter("employee", employeeId).getResultList().stream().findFirst();
    }

    private List<WorkEvent> events(UUID id) {
        return entityManager.createQuery("SELECT e FROM WorkEventRow e WHERE e.workSessionId = :id ORDER BY e.sequence", WorkEventRow.class)
                .setParameter("id", id).getResultList().stream().map(WorkEventRow::toDomain).toList();
    }

    private WorkSession restoreConsistent(WorkSessionRow row, UUID id) {
        var originals = events(id);
        // READ COMMITTED can see a newer event list than the earlier parent query.
        // A scalar query checks the database version, bypassing the managed entity snapshot.
        long currentVersion = entityManager.createQuery("SELECT s.version FROM WorkSessionRow s WHERE s.id = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        if (currentVersion != row.version()) throw new OptimisticLockException("Session changed while loading");
        return row.restore(originals);
    }

    public record StoredSession(WorkSession session, long version) { }
}
