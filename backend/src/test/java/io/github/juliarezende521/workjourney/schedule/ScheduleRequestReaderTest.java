package io.github.juliarezende521.workjourney.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class ScheduleRequestReaderTest {
    private final ScheduleRequestReader reader=new ScheduleRequestReader(new ObjectMapper());
    @Test void readsToleranceAndTheExactScheduleContract() throws Exception {
        var command=reader.read(json("{\"effectiveFrom\":\"2026-11-01\",\"mondayMinutes\":480,\"tuesdayMinutes\":480,\"wednesdayMinutes\":480,\"thursdayMinutes\":480,\"fridayMinutes\":480,\"saturdayMinutes\":0,\"sundayMinutes\":0,\"toleranceMinutes\":10}"));
        assertEquals(10,command.toleranceMinutes());assertEquals(7,command.minutes().size());
    }
    @Test void rejectsMissingUnknownAndDuplicateTolerance() {
        var base="{\"effectiveFrom\":\"2026-11-01\",\"mondayMinutes\":480,\"tuesdayMinutes\":480,\"wednesdayMinutes\":480,\"thursdayMinutes\":480,\"fridayMinutes\":480,\"saturdayMinutes\":0,\"sundayMinutes\":0";
        assertThrows(InvalidSchedule.class,()->reader.read(json(base+"}")));
        assertThrows(InvalidSchedule.class,()->reader.read(json(base+",\"toleranceMinutes\":10,\"admin\":true}")));
        assertThrows(InvalidSchedule.class,()->reader.read(json(base+",\"toleranceMinutes\":10,\"toleranceMinutes\":20}")));
    }
    private MockHttpServletRequest json(String body){var request=new MockHttpServletRequest();request.setContent(body.getBytes(StandardCharsets.UTF_8));return request;}
}
