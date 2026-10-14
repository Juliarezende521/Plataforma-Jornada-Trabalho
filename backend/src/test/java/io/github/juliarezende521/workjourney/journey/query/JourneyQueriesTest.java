package io.github.juliarezende521.workjourney.journey.query;
import io.github.juliarezende521.workjourney.journey.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class JourneyQueriesTest {
    @Test void closedDetailExplainsDstPauseAndPreservesPrecision() {
        var start=Instant.parse("2026-10-25T00:30:00.0000001Z");
        var s=WorkSession.open(UUID.randomUUID(),UUID.randomUUID(),"Europe/Amsterdam",start);
        s.record(WorkEventType.PAUSE_START,start.plusSeconds(1800));
        s.record(WorkEventType.PAUSE_END,start.plusSeconds(2700));
        s.record(WorkEventType.EXIT,start.plusSeconds(5400).plusNanos(100));
        var d=JourneyQueries.describe(s,start.plusSeconds(6000));
        assertEquals("PT1H15M0.0000001S",d.session().workDuration());
        assertEquals(3,d.intervals().size());
        assertEquals("PAUSE",d.intervals().get(1).kind());
        assertEquals("PT15M",d.intervals().get(1).duration());
        assertFalse(d.session().provisional());
        assertEquals(s.getEvents().get(0).id(),d.events().get(0).id());
        assertEquals(LocalDate.of(2026,10,25),d.session().workDate());
    }
    @Test void openAndPausedDetailsRemainProvisionalAndExitInPauseDoesNotCountPause() {
        var now=Instant.parse("2026-10-14T23:30:00Z");
        var s=WorkSession.open(UUID.randomUUID(),UUID.randomUUID(),"UTC",now);
        s.record(WorkEventType.PAUSE_START,now.plusSeconds(3600));
        var d=JourneyQueries.describe(s,now.plusSeconds(5400));
        assertTrue(d.session().provisional());
        assertEquals("PT1H",d.session().workDuration());
        assertTrue(d.intervals().get(1).provisional());
        assertThrows(IllegalStateException.class, () -> JourneyQueries.describe(s,now));
        s.record(WorkEventType.EXIT,now.plusSeconds(7200));
        assertEquals("PT1H",JourneyQueries.describe(s,now.plusSeconds(8000)).session().workDuration());
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
