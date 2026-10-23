package io.github.juliarezende521.workjourney.schedule;

import io.github.juliarezende521.workjourney.account.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkScheduleTest {
    private final Employee employee=new Employee(new AppUser("worker@example.invalid","$2a$12$"+"A".repeat(53)),"Worker","UTC");
    @Test void preservesEffectiveDateAndSevenDailyDurations(){
        var at=Instant.parse("2026-10-22T12:00:00.123456789Z");
        var schedule=new WorkSchedule(employee,LocalDate.of(2026,11,1),List.of(480,480,480,480,360,0,0),10,UUID.randomUUID(),at);
        assertEquals(List.of(480,480,480,480,360,0,0),schedule.minutes());assertEquals(LocalDate.of(2026,11,1),schedule.effectiveFrom());
        assertEquals(123456700,schedule.createdAt().getNano());
    }
    @Test void rejectsEmptyNegativeOrOverTwentyFourHourDays(){
        assertThrows(InvalidSchedule.class,()->new WorkSchedule(employee,LocalDate.now(),List.of(0,0,0,0,0,0,0),0,UUID.randomUUID(),Instant.now()));
        assertThrows(InvalidSchedule.class,()->new WorkSchedule(employee,LocalDate.now(),List.of(-1,0,0,0,0,0,0),0,UUID.randomUUID(),Instant.now()));
        assertThrows(InvalidSchedule.class,()->new WorkSchedule(employee,LocalDate.now(),List.of(1441,0,0,0,0,0,0),0,UUID.randomUUID(),Instant.now()));
        assertThrows(InvalidSchedule.class,()->new WorkSchedule(employee,LocalDate.now(),List.of(480,0,0,0,0,0,0),121,UUID.randomUUID(),Instant.now()));
    }
}
