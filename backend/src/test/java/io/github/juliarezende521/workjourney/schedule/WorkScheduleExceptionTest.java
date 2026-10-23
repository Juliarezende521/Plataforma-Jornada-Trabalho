package io.github.juliarezende521.workjourney.schedule;

import io.github.juliarezende521.workjourney.account.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkScheduleExceptionTest {
    private final Employee employee=new Employee(new AppUser("worker@example.invalid","$2a$12$"+"A".repeat(53)),"Worker","UTC");
    @Test void preservesAValidatedImmutableOverride(){
        var value=new WorkScheduleException(employee,LocalDate.of(2026,12,25),0,0,
                WorkScheduleException.Category.HOLIDAY," Feriado local ",UUID.randomUUID(),Instant.EPOCH);
        assertEquals("Feriado local",value.reason());assertEquals(0,value.plannedMinutes());
    }
    @Test void rejectsInvalidMinutesToleranceAndReason(){
        assertThrows(InvalidSchedule.class,()->new WorkScheduleException(employee,LocalDate.now(),1441,0,WorkScheduleException.Category.SPECIAL,"Especial",UUID.randomUUID(),Instant.EPOCH));
        assertThrows(InvalidSchedule.class,()->new WorkScheduleException(employee,LocalDate.now(),0,121,WorkScheduleException.Category.HOLIDAY,"Feriado",UUID.randomUUID(),Instant.EPOCH));
        assertThrows(InvalidSchedule.class,()->new WorkScheduleException(employee,LocalDate.now(),0,0,WorkScheduleException.Category.TIME_OFF,"x",UUID.randomUUID(),Instant.EPOCH));
    }
}
