package io.github.juliarezende521.workjourney.schedule;

import io.github.juliarezende521.workjourney.account.Employee;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class WorkSchedulePlanTest {
    @Test void appliesVersionChangesAcrossTheInclusivePeriodWithOneQuery() {
        var employeeId = UUID.randomUUID();
        var employee = mock(Employee.class);
        var repository = mock(WorkScheduleRepository.class);
        var first = new WorkSchedule(employee, LocalDate.of(2026, 10, 1),
                List.of(480, 480, 480, 480, 480, 0, 0), 10, UUID.randomUUID(), Instant.EPOCH);
        var second = new WorkSchedule(employee, LocalDate.of(2026, 10, 7),
                List.of(360, 360, 360, 360, 360, 0, 0), 10, UUID.randomUUID(), Instant.EPOCH);
        when(repository.findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                employeeId, LocalDate.of(2026, 10, 5))).thenReturn(java.util.Optional.of(first));
        when(repository.findByEmployee_IdAndEffectiveFromGreaterThanAndEffectiveFromLessThanEqualOrderByEffectiveFromAsc(
                employeeId, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11))).thenReturn(List.of(second));

        var plan = new WorkSchedulePlan(repository).calculate(
                employeeId, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11));

        assertEquals(2040, plan.minutes());
        assertEquals(50,plan.toleranceMinutes());
        assertEquals(7, plan.coveredDays());
        verify(repository).findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                employeeId, LocalDate.of(2026, 10, 5));
        verify(repository).findByEmployee_IdAndEffectiveFromGreaterThanAndEffectiveFromLessThanEqualOrderByEffectiveFromAsc(
                employeeId, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11));
        verifyNoMoreInteractions(repository);
    }

    @Test void treatsDaysBeforeTheFirstVersionAsUnplanned() {
        var employeeId = UUID.randomUUID();
        var employee = mock(Employee.class);
        var repository = mock(WorkScheduleRepository.class);
        var schedule = new WorkSchedule(employee, LocalDate.of(2026, 10, 7),
                List.of(480, 480, 480, 480, 480, 0, 0), 10, UUID.randomUUID(), Instant.EPOCH);
        when(repository.findByEmployee_IdAndEffectiveFromGreaterThanAndEffectiveFromLessThanEqualOrderByEffectiveFromAsc(
                employeeId, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7))).thenReturn(List.of(schedule));

        var plan = new WorkSchedulePlan(repository).calculate(
                employeeId, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7));
        assertEquals(480, plan.minutes());
        assertEquals(10,plan.toleranceMinutes());
        assertEquals(1, plan.coveredDays());
    }
}
