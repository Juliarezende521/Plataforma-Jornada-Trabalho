package io.github.juliarezende521.workjourney.journey.query;
import io.github.juliarezende521.workjourney.journey.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
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
}
