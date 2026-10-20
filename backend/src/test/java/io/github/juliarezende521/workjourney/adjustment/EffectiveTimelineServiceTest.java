package io.github.juliarezende521.workjourney.adjustment;

import io.github.juliarezende521.workjourney.journey.*;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    @Test void materializedCorrectionIsLabeledAndNotProjectedTwice() {
        var start=Instant.parse("2026-10-20T12:00:00Z"); var recorded=start.plusSeconds(7200);
        var userId=UUID.randomUUID();
        var account=new io.github.juliarezende521.workjourney.account.AppUser("worker@example.invalid","$2a$12$"+"A".repeat(53));
        var employee=new io.github.juliarezende521.workjourney.account.Employee(account,"Worker","UTC");
        var session=WorkSession.open(employee.getId(),userId,"UTC",start);
        session.recordCorrection(WorkEventType.EXIT,start.plusSeconds(3600),recorded,"UTC");
        var request=new WorkAdjustmentRequest(employee,userId,session.getId(),WorkEventType.EXIT,start.plusSeconds(3600),"Saída esquecida ao final do turno.",recorded.minusSeconds(60));
        var eventId=session.getEvents().get(1).id(); request.markApplied(eventId);
        request.decide(AdjustmentStatus.APPROVED,UUID.randomUUID(),recorded,"Conferido.");
        var repository=mock(WorkAdjustmentRepository.class);
        when(repository.findByWorkSessionIdAndStatusOrderByProposedOccurredAtUtcAscIdAsc(session.getId(),AdjustmentStatus.APPROVED))
                .thenReturn(List.of(request));
        var timeline=new EffectiveTimelineService(repository).project(session,recorded);
        assertTrue(timeline.adjusted()); assertEquals(2,timeline.events().size());
        assertEquals("ADJUSTMENT",timeline.events().get(1).source());
        assertEquals(request.getId(),timeline.events().get(1).adjustmentId());
    }
}
