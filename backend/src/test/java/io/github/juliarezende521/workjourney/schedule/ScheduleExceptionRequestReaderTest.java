package io.github.juliarezende521.workjourney.schedule;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
class ScheduleExceptionRequestReaderTest {
    private final ScheduleExceptionRequestReader reader=new ScheduleExceptionRequestReader(new ObjectMapper());
    @Test void readsExactContract()throws Exception{var value=reader.read(json("{\"workDate\":\"2026-12-25\",\"plannedMinutes\":0,\"toleranceMinutes\":0,\"category\":\"HOLIDAY\",\"reason\":\"Feriado local\"}"));assertEquals(WorkScheduleException.Category.HOLIDAY,value.category());}
    @Test void rejectsUnknownDuplicateAndInvalidCategory(){assertThrows(InvalidSchedule.class,()->reader.read(json("{\"workDate\":\"2026-12-25\",\"plannedMinutes\":0,\"toleranceMinutes\":0,\"category\":\"OTHER\",\"reason\":\"Motivo\"}")));assertThrows(InvalidSchedule.class,()->reader.read(json("{\"admin\":true}")));}
    private MockHttpServletRequest json(String body){var request=new MockHttpServletRequest();request.setContent(body.getBytes(StandardCharsets.UTF_8));return request;}
}
