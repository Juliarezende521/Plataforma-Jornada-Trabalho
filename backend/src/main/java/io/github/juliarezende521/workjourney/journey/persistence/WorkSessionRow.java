package io.github.juliarezende521.workjourney.journey.persistence;

import io.github.juliarezende521.workjourney.journey.*;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

@Entity
@Table(name = "work_session", schema = "dbo")
public class WorkSessionRow {
    @Id private UUID id;
    @Column(name = "employee_id", nullable = false, updatable = false) private UUID employeeId;
    @Column(name = "author_user_id", nullable = false, updatable = false) private UUID authorUserId;
    @Column(name = "work_date", nullable = false, updatable = false) private LocalDate workDate;
    @Column(name = "reference_time_zone_id", nullable = false, length = 100, updatable = false)
    private String referenceTimeZoneId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private WorkState state;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "opened_at_utc", nullable = false, updatable = false, columnDefinition = "datetimeoffset(7)", precision = 7)
    private OffsetDateTime openedAtUtc;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "last_event_at_utc", nullable = false, columnDefinition = "datetimeoffset(7)", precision = 7)
    private OffsetDateTime lastEventAtUtc;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "closed_at_utc", columnDefinition = "datetimeoffset(7)", precision = 7)
    private OffsetDateTime closedAtUtc;
    @Version @Column(nullable = false) private Long version;

    protected WorkSessionRow() { }

    WorkSessionRow(WorkSession session) {
        id = session.getId();
        employeeId = session.getEmployeeId();
        authorUserId = session.getActorUserId();
        workDate = session.getWorkDate();
        referenceTimeZoneId = session.getReferenceTimeZoneId();
        openedAtUtc = session.getOpenedAtUtc().atOffset(ZoneOffset.UTC);
        updateCheckpoint(session);
    }

    WorkSession restore(List<WorkEvent> events) {
        var session = WorkSession.restore(id, employeeId, authorUserId, referenceTimeZoneId, workDate, events);
        var expectedClose = closedAtUtc == null ? null : closedAtUtc.toInstant();
        if (session.getState() != state || !session.getOpenedAtUtc().equals(openedAtUtc.toInstant())
                || !session.getLastEventAtUtc().equals(lastEventAtUtc.toInstant())
                || !Objects.equals(session.getClosedAtUtc(), expectedClose)) {
            throw new IllegalStateException("Stored checkpoint differs from original events");
        }
        return session;
    }

    void updateCheckpoint(WorkSession session) {
        state = session.getState();
        lastEventAtUtc = session.getLastEventAtUtc().atOffset(ZoneOffset.UTC);
        closedAtUtc = session.getClosedAtUtc() == null ? null : session.getClosedAtUtc().atOffset(ZoneOffset.UTC);
    }

    long version() { return version; }
    UUID id() { return id; }
}
