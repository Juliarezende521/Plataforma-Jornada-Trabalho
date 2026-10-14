package io.github.juliarezende521.workjourney.journey;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import io.github.juliarezende521.workjourney.time.UtcInstants;

/** Immutable online event. Manual regularization must use a separate audited revision. */
public record WorkEvent(UUID id, UUID workSessionId, UUID actorUserId, long sequence,
                        WorkEventType type, Instant occurredAtUtc, Instant recordedAtUtc, String recordedTimeZoneId) {
    public WorkEvent(UUID id, UUID session, UUID actor, long sequence, WorkEventType type, Instant occurred, Instant recorded) {
        this(id, session, actor, sequence, type, occurred, recorded, "UTC");
    }
    public WorkEvent {
        io.github.juliarezende521.workjourney.time.ReferenceTimeZones.requireValid(recordedTimeZoneId);
        Objects.requireNonNull(id, "Event ID is required");
        Objects.requireNonNull(workSessionId, "Session ID is required");
        Objects.requireNonNull(actorUserId, "Actor ID is required");
        Objects.requireNonNull(type, "Event type is required");
        Objects.requireNonNull(occurredAtUtc, "Occurrence is required");
        Objects.requireNonNull(recordedAtUtc, "Recording instant is required");
        if (sequence < 1 || recordedAtUtc.isBefore(occurredAtUtc)
                || !UtcInstants.hasStoragePrecision(occurredAtUtc)
                || !UtcInstants.hasStoragePrecision(recordedAtUtc)) {
            throw new IllegalArgumentException("Invalid sequence, temporal order or 100 ns precision");
        }
    }
}
