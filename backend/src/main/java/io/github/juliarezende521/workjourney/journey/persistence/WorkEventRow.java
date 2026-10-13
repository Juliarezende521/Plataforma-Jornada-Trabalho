package io.github.juliarezende521.workjourney.journey.persistence;

import io.github.juliarezende521.workjourney.journey.WorkEvent;
import io.github.juliarezende521.workjourney.journey.WorkEventType;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

@Entity
@Immutable
@Table(name = "work_event", schema = "dbo")
public class WorkEventRow {
    @Id private UUID id;
    @Column(name = "work_session_id", nullable = false, updatable = false) private UUID workSessionId;
    @Column(name = "author_user_id", nullable = false, updatable = false) private UUID authorUserId;
    @Column(nullable = false, updatable = false) private long sequence;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 16, updatable = false) private WorkEventType eventType;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "occurred_at_utc", nullable = false, updatable = false, columnDefinition = "datetimeoffset(7)", precision = 7)
    private OffsetDateTime occurredAtUtc;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "recorded_at_utc", nullable = false, updatable = false, columnDefinition = "datetimeoffset(7)", precision = 7)
    private OffsetDateTime recordedAtUtc;

    protected WorkEventRow() { }

    WorkEventRow(WorkEvent event) {
        id = event.id();
        workSessionId = event.workSessionId();
        authorUserId = event.actorUserId();
        sequence = event.sequence();
        eventType = event.type();
        occurredAtUtc = event.occurredAtUtc().atOffset(ZoneOffset.UTC);
        recordedAtUtc = event.recordedAtUtc().atOffset(ZoneOffset.UTC);
    }

    WorkEvent toDomain() {
        return new WorkEvent(id, workSessionId, authorUserId, sequence, eventType,
                occurredAtUtc.toInstant(), recordedAtUtc.toInstant());
    }
}
