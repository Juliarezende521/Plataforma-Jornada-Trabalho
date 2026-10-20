package io.github.juliarezende521.workjourney.adjustment;

import io.github.juliarezende521.workjourney.journey.*;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EffectiveTimelineServiceTest {
    @Test void approvedPauseChangesEffectiveDurationAndPreservesOriginals() {
        var start=Instant.parse("2026-10-20T12:00:00Z");
        var session=WorkSession.open(UUID.randomUUID(),UUID.randomUUID(),"UTC",start);
        session.record(WorkEventType.EXIT,start.plus(Duration.ofHours(8)));
        var timeline=EffectiveTimelineService.build(session,List.of(
                new EffectiveTimelineService.Approved(UUID.randomUUID(),WorkEventType.PAUSE_START,start.plus(Duration.ofHours(3))),
                new EffectiveTimelineService.Approved(UUID.randomUUID(),WorkEventType.PAUSE_END,start.plus(Duration.ofHours(4)))),
                start.plus(Duration.ofHours(9)));
        assertEquals(Duration.ofHours(7),timeline.workDuration());
        assertEquals(WorkState.CLOSED,timeline.state());
        assertTrue(timeline.adjusted());
        assertEquals(2,session.getEvents().size());
        assertEquals(List.of("ORIGINAL","ADJUSTMENT","ADJUSTMENT","ORIGINAL"),timeline.events().stream().map(EffectiveTimelineService.Event::source).toList());
    }

    @Test void duplicateInstantAndInvalidTransitionAreRejected() {
        var start=Instant.parse("2026-10-20T12:00:00Z");
        var session=WorkSession.open(UUID.randomUUID(),UUID.randomUUID(),"UTC",start);
        session.record(WorkEventType.EXIT,start.plusSeconds(3600));
        assertThrows(AdjustmentConflict.class,() -> EffectiveTimelineService.build(session,List.of(
                new EffectiveTimelineService.Approved(UUID.randomUUID(),WorkEventType.PAUSE_START,start)),start.plusSeconds(4000)));
        assertThrows(AdjustmentConflict.class,() -> EffectiveTimelineService.build(session,List.of(
                new EffectiveTimelineService.Approved(UUID.randomUUID(),WorkEventType.ENTRY,start.plusSeconds(60))),start.plusSeconds(4000)));
    }

    @Test void preservesOriginalSequenceWhenOriginalInstantsAreEqual() {
        var instant=Instant.parse("2026-10-20T12:00:00Z");
        var session=WorkSession.open(UUID.randomUUID(),UUID.randomUUID(),"UTC",instant);
        session.record(WorkEventType.PAUSE_START,instant);
        session.record(WorkEventType.PAUSE_END,instant);
        session.record(WorkEventType.EXIT,instant);
        var timeline=EffectiveTimelineService.original(session,instant);
        assertEquals(List.of(1L,2L,3L,4L),timeline.events().stream().map(EffectiveTimelineService.Event::originalSequence).toList());
        assertEquals(Duration.ZERO,timeline.workDuration());
    }
}
