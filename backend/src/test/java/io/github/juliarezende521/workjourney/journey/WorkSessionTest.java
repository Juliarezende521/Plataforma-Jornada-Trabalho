package io.github.juliarezende521.workjourney.journey;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.juliarezende521.workjourney.journey.WorkEventType.*;

class WorkSessionTest {
    private static final UUID EMPLOYEE = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();

    private WorkSession open(String instant) {
        return WorkSession.open(EMPLOYEE, ACTOR, "America/Sao_Paulo", Instant.parse(instant));
    }

    @Test
    void calculatesNormalDayWithMultiplePauses() {
        var session = open("2026-10-11T12:00:00Z");
        session.record(PAUSE_START, Instant.parse("2026-10-11T15:00:00Z"));
        session.record(PAUSE_END, Instant.parse("2026-10-11T16:00:00Z"));
        session.record(PAUSE_START, Instant.parse("2026-10-11T18:00:00Z"));
        session.record(PAUSE_END, Instant.parse("2026-10-11T18:15:00Z"));
        session.record(EXIT, Instant.parse("2026-10-11T21:00:00Z"));
        assertEquals(Duration.ofHours(8).minusMinutes(15), session.closedDuration());
        assertEquals(WorkState.CLOSED, session.getState());
        assertEquals(6, session.getEvents().size());
    }

    @Test
    void exitsDuringPauseWithoutArtificialReturn() {
        var session = open("2026-10-11T12:00:00Z");
        session.record(PAUSE_START, Instant.parse("2026-10-11T15:00:00Z"));
        session.record(EXIT, Instant.parse("2026-10-11T15:30:00Z"));
        assertEquals(Duration.ofHours(3), session.closedDuration());
        assertEquals(Duration.ofMinutes(30), Duration.between(
                session.getEvents().get(1).occurredAtUtc(), session.getClosedAtUtc()));
        assertEquals(java.util.List.of(ENTRY, PAUSE_START, EXIT),
                session.getEvents().stream().map(WorkEvent::type).toList());
    }

    @Test
    void preservesStartDateAcrossMidnightAndMonthBoundary() {
        var session = open("2026-11-01T01:00:00Z"); // Oct 31, 22h in reference zone
        session.record(EXIT, Instant.parse("2026-11-01T09:00:00Z"));
        assertEquals(LocalDate.of(2026, 10, 31), session.getWorkDate());
        assertEquals(Duration.ofHours(8), session.closedDuration());
        assertEquals("America/Sao_Paulo", session.getReferenceTimeZoneId());
    }

    @Test
    void calculatesRealDurationAcrossBothDaylightSavingChanges() {
        for (String date : java.util.List.of("2026-03-29", "2026-10-25")) {
            var session = WorkSession.open(EMPLOYEE, ACTOR, "Europe/Amsterdam",
                    Instant.parse(date + "T00:30:00Z"));
            session.record(EXIT, Instant.parse(date + "T02:30:00Z"));
            assertEquals(Duration.ofHours(2), session.closedDuration());
        }
    }

    @Test
    void viewingAnotherZoneDoesNotChangeAggregate() {
        var session = open("2026-10-11T02:30:00Z");
        var event = session.getEvents().get(0);
        assertNotEquals(event.occurredAtUtc().atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate(),
                event.occurredAtUtc().atZone(ZoneId.of("Europe/Amsterdam")).toLocalDate());
        assertEquals(LocalDate.of(2026, 10, 10), session.getWorkDate());
        assertEquals(event, session.getEvents().get(0));
    }

    @Test
    void rejectsInvalidTransitionsWithoutMutation() {
        var session = open("2026-10-11T12:00:00Z");
        var initial = session.getEvents();
        assertThrows(IllegalStateException.class, () -> session.record(PAUSE_END, Instant.parse("2026-10-11T13:00:00Z")));
        assertThrows(IllegalStateException.class, () -> session.record(ENTRY, Instant.parse("2026-10-11T13:00:00Z")));
        assertEquals(initial, session.getEvents());
        assertEquals(WorkState.WORKING, session.getState());
        session.record(PAUSE_START, Instant.parse("2026-10-11T13:00:00Z"));
        assertThrows(IllegalStateException.class, () -> session.record(PAUSE_START, Instant.parse("2026-10-11T14:00:00Z")));
        session.record(EXIT, Instant.parse("2026-10-11T14:00:00Z"));
        for (var type : WorkEventType.values()) {
            assertThrows(IllegalStateException.class, () -> session.record(type, Instant.parse("2026-10-11T15:00:00Z")));
        }
        assertEquals(3, session.getEvents().size());
        assertEquals(Duration.ofHours(1), session.closedDuration());
    }

    @Test
    void rejectsBackwardClockAndAllowsEqualInstantsWithStableSequence() {
        var session = open("2026-10-11T12:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> session.record(EXIT, Instant.parse("2026-10-11T11:59:59Z")));
        assertEquals(1, session.getEvents().size());
        assertNull(session.getClosedAtUtc());
        session.record(PAUSE_START, Instant.parse("2026-10-11T12:00:00Z"));
        session.record(EXIT, Instant.parse("2026-10-11T12:00:00Z"));
        assertEquals(java.util.List.of(1L, 2L, 3L), session.getEvents().stream().map(WorkEvent::sequence).toList());
        assertEquals(Duration.ZERO, session.closedDuration());
    }

    @Test
    void keepsOpenDurationProvisionalAndStopsAccumulatingDuringPause() {
        var session = open("2026-10-11T12:00:00Z");
        assertEquals(Duration.ofHours(1), session.durationAt(Instant.parse("2026-10-11T13:00:00Z")));
        assertThrows(IllegalStateException.class, session::closedDuration);
        session.record(PAUSE_START, Instant.parse("2026-10-11T13:00:00Z"));
        assertEquals(Duration.ofHours(1), session.durationAt(Instant.parse("2026-10-12T13:00:00Z")));
        assertThrows(IllegalArgumentException.class, () -> session.durationAt(Instant.parse("2026-10-11T12:59:59Z")));
    }

    @Test
    void normalizesPrecisionWithoutRoundingEachDuration() {
        var session = open("2026-10-11T12:00:00.123456789Z");
        session.record(EXIT, Instant.parse("2026-10-11T12:00:01.123456799Z"));
        assertEquals(123456700, session.getEvents().get(0).occurredAtUtc().getNano());
        assertEquals(Duration.ofSeconds(1), session.closedDuration());
    }

    @Test
    void eventsAreImmutableSnapshotsWithActorAndSessionIdentity() {
        var session = open("2026-10-11T12:00:00Z");
        var snapshot = session.getEvents();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        session.record(EXIT, Instant.parse("2026-10-11T13:00:00Z"));
        assertEquals(1, snapshot.size());
        assertEquals(EMPLOYEE, session.getEmployeeId());
        for (var event : session.getEvents()) {
            assertEquals(ACTOR, event.actorUserId());
            assertEquals(session.getId(), event.workSessionId());
            assertEquals(event.occurredAtUtc(), event.recordedAtUtc());
        }
    }

    @Test
    void rejectsMissingIdentityZoneAndInstant() {
        assertThrows(NullPointerException.class, () -> WorkSession.open(null, ACTOR, "Europe/Amsterdam", Instant.EPOCH));
        assertThrows(NullPointerException.class, () -> WorkSession.open(EMPLOYEE, null, "Europe/Amsterdam", Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> WorkSession.open(EMPLOYEE, ACTOR, null, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> WorkSession.open(EMPLOYEE, ACTOR, "+03:00", Instant.EPOCH));
        assertThrows(NullPointerException.class, () -> WorkSession.open(EMPLOYEE, ACTOR, "Europe/Amsterdam", null));
    }

    @Test
    void eventRejectsInvalidSequencePrecisionAndRecordingOrder() {
        UUID id = UUID.randomUUID();
        Instant instant = Instant.parse("2026-10-11T12:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new WorkEvent(id, id, ACTOR, 0, ENTRY, instant, instant));
        assertThrows(IllegalArgumentException.class, () -> new WorkEvent(id, id, ACTOR, 1, ENTRY, instant, instant.minusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new WorkEvent(id, id, ACTOR, 1, ENTRY, instant.plusNanos(1), instant.plusNanos(1)));
    }
    @Test
    void recordingZoneDoesNotChangeReferenceDateAtMidnight() {
        var instant = Instant.parse("2026-10-15T01:30:00Z");
        var session = WorkSession.open(UUID.randomUUID(), UUID.randomUUID(), "America/Sao_Paulo",
                instant, "Europe/Lisbon");
        assertEquals(LocalDate.of(2026, 10, 14), session.getWorkDate());
        assertEquals(LocalDate.of(2026, 10, 15), instant.atZone(java.time.ZoneId.of("Europe/Lisbon")).toLocalDate());
        assertEquals("America/Sao_Paulo", session.getReferenceTimeZoneId());
        assertEquals("Europe/Lisbon", session.getEvents().get(0).recordedTimeZoneId());
        assertEquals(instant, session.getEvents().get(0).occurredAtUtc());
    }
}
