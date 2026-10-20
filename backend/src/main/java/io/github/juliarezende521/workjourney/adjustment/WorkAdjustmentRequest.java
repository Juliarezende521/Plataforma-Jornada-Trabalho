package io.github.juliarezende521.workjourney.adjustment;

import io.github.juliarezende521.workjourney.account.Employee;
import io.github.juliarezende521.workjourney.journey.WorkEventType;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

@Entity
@Table(name = "work_adjustment_request", schema = "dbo")
public class WorkAdjustmentRequest {
    @Id private UUID id;
    @Column(name = "work_session_id", nullable = false, updatable = false) private UUID workSessionId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false) private Employee employee;
    @Column(name = "requested_by_user_id", nullable = false, updatable = false) private UUID requestedByUserId;
    @Enumerated(EnumType.STRING)
    @Column(name = "requested_event_type", nullable = false, length = 16, updatable = false) private WorkEventType requestedEventType;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "proposed_occurred_at_utc", nullable = false, updatable = false, columnDefinition = "datetimeoffset(7)", precision = 7)
    private OffsetDateTime proposedOccurredAtUtc;
    @Column(nullable = false, length = 500, updatable = false) private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private AdjustmentStatus status;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "requested_at_utc", nullable = false, updatable = false, columnDefinition = "datetimeoffset(7)", precision = 7)
    private OffsetDateTime requestedAtUtc;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "decided_at_utc", columnDefinition = "datetimeoffset(7)", precision = 7) private OffsetDateTime decidedAtUtc;
    @Column(name = "decided_by_user_id") private UUID decidedByUserId;
    @Column(name = "decision_note", length = 500) private String decisionNote;
    @Column(name = "applied_event_id") private UUID appliedEventId;
    @Version @Column(nullable = false) private Long version;

    protected WorkAdjustmentRequest() { }

    WorkAdjustmentRequest(Employee employee, UUID requester, UUID sessionId, WorkEventType eventType,
                          Instant proposedAt, String reason, Instant requestedAt) {
        this.id = UUID.randomUUID();
        this.employee = employee;
        this.requestedByUserId = requester;
        this.workSessionId = sessionId;
        this.requestedEventType = eventType;
        this.proposedOccurredAtUtc = proposedAt.atOffset(ZoneOffset.UTC);
        this.reason = reason;
        this.status = AdjustmentStatus.PENDING;
        this.requestedAtUtc = requestedAt.atOffset(ZoneOffset.UTC);
    }

    void decide(AdjustmentStatus decision, UUID reviewer, Instant decidedAt, String note) {
        if (status != AdjustmentStatus.PENDING) throw new AdjustmentConflict();
        if (decision == null || decision == AdjustmentStatus.PENDING) throw new InvalidAdjustment();
        status = decision;
        decidedByUserId = reviewer;
        decidedAtUtc = decidedAt.atOffset(ZoneOffset.UTC);
        decisionNote = note;
    }
    void markApplied(UUID eventId) {
        if(status!=AdjustmentStatus.PENDING||appliedEventId!=null)throw new AdjustmentConflict();
        appliedEventId=java.util.Objects.requireNonNull(eventId);
    }

    public UUID getId() { return id; }
    public UUID getWorkSessionId() { return workSessionId; }
    public Employee getEmployee() { return employee; }
    public WorkEventType getRequestedEventType() { return requestedEventType; }
    public Instant getProposedOccurredAtUtc() { return proposedOccurredAtUtc.toInstant(); }
    public String getReason() { return reason; }
    public AdjustmentStatus getStatus() { return status; }
    public Instant getRequestedAtUtc() { return requestedAtUtc.toInstant(); }
    public Instant getDecidedAtUtc() { return decidedAtUtc == null ? null : decidedAtUtc.toInstant(); }
    public String getDecisionNote() { return decisionNote; }
    public UUID getAppliedEventId() { return appliedEventId; }
}
