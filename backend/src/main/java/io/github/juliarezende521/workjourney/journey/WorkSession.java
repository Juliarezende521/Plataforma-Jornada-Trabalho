package io.github.juliarezende521.workjourney.journey;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import io.github.juliarezende521.workjourney.time.ReferenceTimeZones;
import io.github.juliarezende521.workjourney.time.UtcInstants;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
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

    private WorkSession(UUID id, UUID employeeId, UUID actorUserId, String zoneId, Instant serverInstant) {
        this.employeeId = Objects.requireNonNull(employeeId, "Employee is required");
        this.actorUserId = Objects.requireNonNull(actorUserId, "Actor is required");
        var referenceZone = ReferenceTimeZones.requireValid(zoneId);
        Instant instant = UtcInstants.normalize(serverInstant);
        this.id = Objects.requireNonNull(id, "Session ID is required");
        this.referenceTimeZoneId = referenceZone.getId();
        this.workDate = instant.atZone(referenceZone).toLocalDate();
        this.workingSince = instant;
    }

    public static WorkSession open(UUID employeeId, UUID actorUserId, String zoneId,
                                   Instant serverInstant) {
        var session = new WorkSession(UUID.randomUUID(), employeeId, actorUserId, zoneId, serverInstant);
        session.events.add(session.event(WorkEventType.ENTRY, session.workingSince));
        return session;
    }

    /** Internal persistence boundary: replay originals, preserving IDs and frozen reference date. */
    public static WorkSession restore(UUID id, UUID employeeId, UUID actorUserId, String zoneId,
                                      LocalDate workDate, List<WorkEvent> storedEvents) {
        var originals = List.copyOf(storedEvents);
        if (originals.isEmpty() || originals.get(0).type() != WorkEventType.ENTRY) {
            throw new IllegalArgumentException("Stored session must start with ENTRY");
        }
        var session = new WorkSession(id, employeeId, actorUserId, zoneId, originals.get(0).occurredAtUtc());
        if (!session.workDate.equals(Objects.requireNonNull(workDate, "Work date is required"))) {
            throw new IllegalArgumentException("Stored reference date differs from entry date");
        }
        var ids = new HashSet<UUID>();
        for (var original : originals) {
            if (!original.workSessionId().equals(id) || !original.actorUserId().equals(actorUserId)
                    || original.sequence() != session.events.size() + 1L || !ids.add(original.id())) {
                throw new IllegalArgumentException("Stored event identity or sequence is invalid");
            }
            if (session.events.isEmpty()) session.events.add(original);
            else session.apply(original);
        }
        return session;
    }

    public void record(WorkEventType type, Instant serverInstant) {
        Objects.requireNonNull(type, "Event type is required");
        Instant instant = UtcInstants.normalize(serverInstant);
        apply(event(type, instant));
    }

    private void apply(WorkEvent event) {
        Instant instant = event.occurredAtUtc();
        if (instant.isBefore(lastInstant())) {
            throw new IllegalArgumentException("Event cannot precede the last event");
        }
        WorkState next = state.nextFor(event.type());
        // Validate before changing any field; rejected commands leave this aggregate unchanged.
        Duration updatedWork = state == WorkState.WORKING
                ? completedWork.plus(Duration.between(workingSince, instant)) : completedWork;
        completedWork = updatedWork;
        workingSince = next == WorkState.WORKING ? instant : null;
        state = next;
        if (next == WorkState.CLOSED) closedAtUtc = instant;
        events.add(event);
    }

    /** Provisional duration for open sessions; no deduction beyond the last pause start. */
    public Duration durationAt(Instant serverInstant) {
        Instant instant = UtcInstants.normalize(serverInstant);
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
    public UUID getActorUserId() { return actorUserId; }
    public Instant getOpenedAtUtc() { return events.get(0).occurredAtUtc(); }
    public Instant getLastEventAtUtc() { return lastInstant(); }
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

}
