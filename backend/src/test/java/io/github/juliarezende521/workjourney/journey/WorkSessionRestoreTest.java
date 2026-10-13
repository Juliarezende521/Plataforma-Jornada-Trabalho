package io.github.juliarezende521.workjourney.journey;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.juliarezende521.workjourney.journey.WorkEventType.*;

class WorkSessionRestoreTest {
    @Test
    void restoresOriginalIdsDurationAndCanContinuePausedSession() {
        var original = WorkSession.open(UUID.randomUUID(), UUID.randomUUID(), "Europe/Amsterdam", Instant.parse("2026-10-25T00:30:00Z"));
        original.record(PAUSE_START, Instant.parse("2026-10-25T01:30:00Z"));
        var restored = restore(original, original.getEvents());
        assertEquals(original.getEvents(), restored.getEvents());
        assertEquals(original.getId(), restored.getId());
        assertEquals(WorkState.PAUSED, restored.getState());
        restored.record(EXIT, Instant.parse("2026-10-25T02:30:00Z"));
        assertEquals(Duration.ofHours(1), restored.closedDuration());
        assertEquals(2, original.getEvents().size());
        assertEquals(3, restored.getEvents().get(2).sequence());
    }

    @Test
    void rejectsMissingEntryWrongIdentityDateAndSequenceGaps() {
        var original = WorkSession.open(UUID.randomUUID(), UUID.randomUUID(), "America/Sao_Paulo", Instant.EPOCH);
        var entry = original.getEvents().get(0);
        assertThrows(IllegalArgumentException.class, () -> restore(original, List.of()));
        var gap = new WorkEvent(UUID.randomUUID(), original.getId(), original.getActorUserId(), 3, EXIT, Instant.EPOCH, Instant.EPOCH);
        assertThrows(IllegalArgumentException.class, () -> restore(original, List.of(entry, gap)));
        var foreign = new WorkEvent(entry.id(), UUID.randomUUID(), entry.actorUserId(), 1, ENTRY, Instant.EPOCH, Instant.EPOCH);
        assertThrows(IllegalArgumentException.class, () -> restore(original, List.of(foreign)));
        var wrongAuthor = new WorkEvent(entry.id(), original.getId(), UUID.randomUUID(), 1, ENTRY, Instant.EPOCH, Instant.EPOCH);
        assertThrows(IllegalArgumentException.class, () -> restore(original, List.of(wrongAuthor)));
        assertThrows(IllegalArgumentException.class, () -> WorkSession.restore(original.getId(), original.getEmployeeId(), original.getActorUserId(),
                original.getReferenceTimeZoneId(), original.getWorkDate().plusDays(1), original.getEvents()));
    }

    @Test
    void rejectsRepeatedIdsInvalidTransitionsAndRegressiveClock() {
        var original = WorkSession.open(UUID.randomUUID(), UUID.randomUUID(), "Europe/Amsterdam", Instant.EPOCH);
        var entry = original.getEvents().get(0);
        var duplicateId = new WorkEvent(entry.id(), original.getId(), entry.actorUserId(), 2, EXIT, Instant.EPOCH, Instant.EPOCH);
        assertThrows(IllegalArgumentException.class, () -> restore(original, List.of(entry, duplicateId)));
        var invalidReturn = new WorkEvent(UUID.randomUUID(), original.getId(), entry.actorUserId(), 2, PAUSE_END, Instant.EPOCH, Instant.EPOCH);
        assertThrows(IllegalStateException.class, () -> restore(original, List.of(entry, invalidReturn)));
        original.record(EXIT, Instant.EPOCH.plusSeconds(1));
        var events = new ArrayList<>(original.getEvents());
        events.add(new WorkEvent(UUID.randomUUID(), original.getId(), entry.actorUserId(), 3, PAUSE_START, Instant.EPOCH.plusSeconds(2), Instant.EPOCH.plusSeconds(2)));
        assertThrows(IllegalStateException.class, () -> restore(original, events));
        var earlier = new WorkEvent(UUID.randomUUID(), original.getId(), entry.actorUserId(), 2, EXIT, Instant.EPOCH.minusSeconds(1), Instant.EPOCH);
        assertThrows(IllegalArgumentException.class, () -> restore(original, List.of(entry, earlier)));
    }

    private WorkSession restore(WorkSession session, List<WorkEvent> events) {
        return WorkSession.restore(session.getId(), session.getEmployeeId(), session.getActorUserId(),
                session.getReferenceTimeZoneId(), session.getWorkDate(), events);
    }
}
