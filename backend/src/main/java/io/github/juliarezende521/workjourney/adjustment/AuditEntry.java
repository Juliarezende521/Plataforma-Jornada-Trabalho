package io.github.juliarezende521.workjourney.adjustment;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

@Entity
@Table(name = "audit_entry", schema = "dbo")
public class AuditEntry {
    @Id private UUID id;
    @Column(name = "actor_user_id", nullable = false, updatable = false) private UUID actorUserId;
    @Column(nullable = false, length = 64, updatable = false) private String action;
    @Column(name = "entity_type", nullable = false, length = 64, updatable = false) private String entityType;
    @Column(name = "entity_id", nullable = false, updatable = false) private UUID entityId;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "recorded_at_utc", nullable = false, updatable = false, columnDefinition = "datetimeoffset(7)", precision = 7)
    private OffsetDateTime recordedAtUtc;
    @Column(length = 1000, updatable = false) private String details;
    protected AuditEntry() { }
    AuditEntry(UUID actor, String action, UUID entityId, Instant at, String details) {
        this(actor,action,"WORK_ADJUSTMENT",entityId,at,details);
    }
    public AuditEntry(UUID actor,String action,String entityType,UUID entityId,Instant at,String details) {
        if(actor==null||entityId==null||at==null||action==null||action.isBlank()||action.length()>64
                ||entityType==null||entityType.isBlank()||entityType.length()>64||details!=null&&details.length()>1000)
            throw new IllegalArgumentException("Invalid audit entry");
        this.id=UUID.randomUUID(); this.actorUserId=actor; this.action=action; this.entityType=entityType;
        this.entityId=entityId; this.recordedAtUtc=at.atOffset(ZoneOffset.UTC); this.details=details;
    }
    public UUID id() { return id; }
    public UUID actorUserId() { return actorUserId; }
    public String action() { return action; }
    public String entityType() { return entityType; }
    public UUID entityId() { return entityId; }
    public Instant recordedAt() { return recordedAtUtc.toInstant(); }
    public String details() { return details; }
}
