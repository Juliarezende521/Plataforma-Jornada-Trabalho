package io.github.juliarezende521.workjourney.journey.query;
import io.github.juliarezende521.workjourney.journey.*;
import java.time.*;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import io.github.juliarezende521.workjourney.adjustment.EffectiveTimelineService;
import io.github.juliarezende521.workjourney.journey.persistence.JpaWorkSessionStore;
import io.github.juliarezende521.workjourney.schedule.WorkSchedulePlan;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class JourneyQueriesTest {
    @Test void closedDetailPreservesEventOrderTimeZoneAndUtcPrecision() {
        var start=Instant.parse("2026-10-25T00:30:00.0000001Z");
        var s=WorkSession.open(UUID.randomUUID(),UUID.randomUUID(),"Europe/Amsterdam",start);
        s.record(WorkEventType.PAUSE_START,start.plusSeconds(1800));
        s.record(WorkEventType.PAUSE_END,start.plusSeconds(2700));
        s.record(WorkEventType.EXIT,start.plusSeconds(5400).plusNanos(100));
        var d=JourneyQueries.describe(s,start.plusSeconds(6000));
        assertEquals(4,d.events().size());
        assertEquals(start,d.events().get(0).occurredAtUtc());
        assertEquals("Europe/Amsterdam",d.events().get(0).recordedTimeZoneId());
        assertEquals(WorkState.CLOSED,d.session().state());
        assertEquals(s.getEvents().get(0).id(),d.events().get(0).id());
        assertEquals(LocalDate.of(2026,10,25),d.session().workDate());
        assertFalse(d.effective().adjusted());
        assertEquals("PT1H15M0.0000001S",d.effective().workDuration());
    }
    @Test void detailPreservesPausedAndClosedStates() {
        var now=Instant.parse("2026-10-14T23:30:00Z");
        var s=WorkSession.open(UUID.randomUUID(),UUID.randomUUID(),"UTC",now);
        s.record(WorkEventType.PAUSE_START,now.plusSeconds(3600));
        var d=JourneyQueries.describe(s,now.plusSeconds(5400));
        assertEquals(WorkState.PAUSED,d.session().state());
        assertEquals(2,d.events().size());
        s.record(WorkEventType.EXIT,now.plusSeconds(7200));
        assertEquals(WorkState.CLOSED,JourneyQueries.describe(s,now.plusSeconds(8000)).session().state());
    }
    @Test void filtersBoundInclusiveDatesPageAndSize() {
        var from=LocalDate.of(2024,1,1);
        assertDoesNotThrow(() -> JourneyQueries.validate(from,from.plusDays(365),0,100));
        assertThrows(JourneyQueries.InvalidFilter.class, () -> JourneyQueries.validate(from,from.plusDays(366),0,20));
        assertThrows(JourneyQueries.InvalidFilter.class, () -> JourneyQueries.validate(from,from.minusDays(1),0,20));
        assertThrows(JourneyQueries.InvalidFilter.class, () -> JourneyQueries.validate(from,from,-1,20));
        assertThrows(JourneyQueries.InvalidFilter.class, () -> JourneyQueries.validate(from,from,0,101));
        assertThrows(JourneyQueries.InvalidFilter.class, () -> JourneyQueries.validate(from,from,Integer.MAX_VALUE,20));
    }
    @Test void summaryUsesEffectiveDurationsAndDistinguishesOpenAndAdjustedSessions() {
        var employee=UUID.randomUUID();var day=LocalDate.of(2026,10,20);var observed=Instant.parse("2026-10-20T20:00:00Z");
        var closed=WorkSession.open(employee,UUID.randomUUID(),"UTC",observed.minusSeconds(10_800));
        closed.record(WorkEventType.EXIT,observed.minusSeconds(3_600));
        var open=WorkSession.open(employee,UUID.randomUUID(),"UTC",observed.minusSeconds(1_800));
        var store=mock(JpaWorkSessionStore.class);var timelines=mock(EffectiveTimelineService.class);var schedules=mock(WorkSchedulePlan.class);
        when(store.historyCount(employee,day,day)).thenReturn(2L);
        when(store.history(employee,day,day,0,100)).thenReturn(List.of(closed,open));
        when(timelines.projectAll(List.of(closed,open),observed)).thenReturn(Map.of(
                closed.getId(),new EffectiveTimelineService.Timeline(WorkState.CLOSED,Duration.ofHours(7),true,List.of()),
                open.getId(),new EffectiveTimelineService.Timeline(WorkState.WORKING,Duration.ofMinutes(30),false,List.of())));
        when(schedules.calculate(employee,day,day)).thenReturn(new WorkSchedulePlan.Plan(480,15,1));
        var summary=JourneyQueries.summarize(store,timelines,schedules,employee,day,day,observed);
        assertEquals("PT7H30M",summary.totalWorkDuration());assertEquals(2,summary.totalSessions());
        assertEquals("PT8H",summary.plannedWorkDuration());assertEquals(-1800,summary.balanceSeconds());
        assertEquals(900,summary.toleranceSeconds());assertFalse(summary.withinTolerance());
        assertEquals(1,summary.scheduledDays());assertEquals(1,summary.periodDays());
        assertEquals(1,summary.closedSessions());assertEquals(1,summary.openSessions());
        assertEquals(1,summary.adjustedSessions());assertEquals(1,summary.workedDays());
        verify(timelines).projectAll(List.of(closed,open),observed);
    }
    @Test void summaryRangeMatchesTheBoundedHistoryPeriod() {
        var from=LocalDate.of(2026,10,1);
        assertDoesNotThrow(() -> JourneyQueries.validateSummary(from,from.plusDays(365)));
        assertThrows(JourneyQueries.InvalidFilter.class,() -> JourneyQueries.validateSummary(from,from.plusDays(366)));
    }
    @Test void summaryOmitsBalanceWhenScheduleDoesNotCoverTheWholePeriod() {
        var employee=UUID.randomUUID();var from=LocalDate.of(2026,10,1);var to=from.plusDays(2);var observed=Instant.EPOCH;
        var store=mock(JpaWorkSessionStore.class);var timelines=mock(EffectiveTimelineService.class);var schedules=mock(WorkSchedulePlan.class);
        when(schedules.calculate(employee,from,to)).thenReturn(new WorkSchedulePlan.Plan(480,15,1));
        var summary=JourneyQueries.summarize(store,timelines,schedules,employee,from,to,observed);
        assertNull(summary.balanceSeconds());assertNull(summary.toleranceSeconds());assertNull(summary.withinTolerance());
        assertEquals(1,summary.scheduledDays());assertEquals(3,summary.periodDays());
    }
}
