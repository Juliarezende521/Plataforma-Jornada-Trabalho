package io.github.juliarezende521.workjourney.journey;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain aggregate for online events, independent of JPA and HTTP.
 * The application service must supply the authenticated identity and server Clock.
 * Database transactions, authorization, revisions and idempotency are not provided here.
 */
public final class WorkSession {
    private final UUID id;
    private final UUID employeeId;
    private final UUID actorUserId;
    private final String referenceTimeZoneId;
    private final LocalDate workDate;
    private final List<WorkEvent> events = new ArrayList<>();
    private WorkState state = WorkState.WORKING;
    private Duration completedWork = Duration.ZERO;
    private Instant workingSince;
    private Instant closedAtUtc;

    private WorkSession(UUID employeeId, UUID actorUserId, String zoneId, Instant serverInstant) {
        this.employeeId = Objects.requireNonNull(employeeId, "Employee is required");
        this.actorUserId = Objects.requireNonNull(actorUserId, "Actor is required");
        if (zoneId == null || !ZoneId.getAvailableZoneIds().contains(zoneId)) {
            throw new IllegalArgumentException("An IANA reference zone is required");
        }
        Instant instant = normalize(serverInstant);
        this.id = UUID.randomUUID();
        this.referenceTimeZoneId = zoneId;
        this.workDate = instant.atZone(ZoneId.of(zoneId)).toLocalDate();
        this.workingSince = instant;
        events.add(event(WorkEventType.ENTRY, instant));
    }

    public static WorkSession open(UUID employeeId, UUID actorUserId, String zoneId,
                                   Instant serverInstant) {
        return new WorkSession(employeeId, actorUserId, zoneId, serverInstant);
    }

    public void record(WorkEventType type, Instant serverInstant) {
        Objects.requireNonNull(type, "Event type is required");
        Instant instant = normalize(serverInstant);
        if (instant.isBefore(lastInstant())) {
            throw new IllegalArgumentException("Event cannot precede the last event");
        }
        WorkState next = switch (type) {
            case ENTRY -> throw new IllegalStateException("Entry already exists");
            case PAUSE_START -> {
                requireState(WorkState.WORKING);
                yield WorkState.PAUSED;
            }
            case PAUSE_END -> {
                requireState(WorkState.PAUSED);
                yield WorkState.WORKING;
            }
            case EXIT -> {
                if (state == WorkState.CLOSED) {
                    throw new IllegalStateException("Session is already closed");
                }
                yield WorkState.CLOSED;
            }
        };
        // Validate before changing any field; rejected commands leave this aggregate unchanged.
        WorkEvent event = event(type, instant);
        if (state == WorkState.WORKING) {
            completedWork = completedWork.plus(Duration.between(workingSince, instant));
        }
        workingSince = next == WorkState.WORKING ? instant : null;
        state = next;
        if (next == WorkState.CLOSED) closedAtUtc = instant;
        events.add(event);
    }

    /** Provisional duration for open sessions; no deduction beyond the last pause start. */
    public Duration durationAt(Instant serverInstant) {
        Instant instant = normalize(serverInstant);
        if (instant.isBefore(lastInstant())) {
            throw new IllegalArgumentException("Calculation instant precedes the last event");
        }
        return state == WorkState.WORKING
                ? completedWork.plus(Duration.between(workingSince, instant)) : completedWork;
    }

    public Duration closedDuration() {
        requireState(WorkState.CLOSED);
        return completedWork;
    }

    public UUID getId() { return id; }
    public UUID getEmployeeId() { return employeeId; }
    public String getReferenceTimeZoneId() { return referenceTimeZoneId; }
    public LocalDate getWorkDate() { return workDate; }
    public WorkState getState() { return state; }
    public Instant getClosedAtUtc() { return closedAtUtc; }
    public List<WorkEvent> getEvents() { return List.copyOf(events); }

    private WorkEvent event(WorkEventType type, Instant instant) {
        return new WorkEvent(UUID.randomUUID(), id, actorUserId, events.size() + 1L,
                type, instant, instant);
    }

    private Instant lastInstant() { return events.get(events.size() - 1).occurredAtUtc(); }

    private void requireState(WorkState expected) {
        if (state != expected) throw new IllegalStateException("Event is incompatible with session state");
    }

    private static Instant normalize(Instant instant) {
        Objects.requireNonNull(instant, "Server instant is required");
        return instant.minusNanos(instant.getNano() % 100);
    }
}
