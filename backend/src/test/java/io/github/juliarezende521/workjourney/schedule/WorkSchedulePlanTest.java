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
        var exceptions=mock(WorkScheduleExceptionRepository.class);
        when(exceptions.findByEmployee_IdAndWorkDateBetweenOrderByWorkDateAscCreatedAtUtcAsc(any(),any(),any())).thenReturn(List.of());
        var first = new WorkSchedule(employee, LocalDate.of(2026, 10, 1),
                List.of(480, 480, 480, 480, 480, 0, 0), 10, UUID.randomUUID(), Instant.EPOCH);
        var second = new WorkSchedule(employee, LocalDate.of(2026, 10, 7),
                List.of(360, 360, 360, 360, 360, 0, 0), 10, UUID.randomUUID(), Instant.EPOCH);
        when(repository.findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                employeeId, LocalDate.of(2026, 10, 5))).thenReturn(java.util.Optional.of(first));
        when(repository.findByEmployee_IdAndEffectiveFromGreaterThanAndEffectiveFromLessThanEqualOrderByEffectiveFromAsc(
                employeeId, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11))).thenReturn(List.of(second));

        var plan = new WorkSchedulePlan(repository,exceptions).calculate(
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
        var exceptions=mock(WorkScheduleExceptionRepository.class);
        when(exceptions.findByEmployee_IdAndWorkDateBetweenOrderByWorkDateAscCreatedAtUtcAsc(any(),any(),any())).thenReturn(List.of());
        var schedule = new WorkSchedule(employee, LocalDate.of(2026, 10, 7),
                List.of(480, 480, 480, 480, 480, 0, 0), 10, UUID.randomUUID(), Instant.EPOCH);
        when(repository.findByEmployee_IdAndEffectiveFromGreaterThanAndEffectiveFromLessThanEqualOrderByEffectiveFromAsc(
                employeeId, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7))).thenReturn(List.of(schedule));

        var plan = new WorkSchedulePlan(repository,exceptions).calculate(
                employeeId, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7));
        assertEquals(480, plan.minutes());
        assertEquals(10,plan.toleranceMinutes());
        assertEquals(1, plan.coveredDays());
    }

    @Test void latestExceptionOverridesTheWeeklyPlanForItsDate() {
        var employeeId = UUID.randomUUID();
        var employee = mock(Employee.class);
        var schedules = mock(WorkScheduleRepository.class);
        var exceptions = mock(WorkScheduleExceptionRepository.class);
        var date = LocalDate.of(2026, 12, 25);
        var schedule = new WorkSchedule(employee, date.minusDays(10),
                List.of(480, 480, 480, 480, 480, 0, 0), 10, UUID.randomUUID(), Instant.EPOCH);
        var earlier = new WorkScheduleException(employee, date, 0, 0,
                WorkScheduleException.Category.HOLIDAY, "Feriado local", UUID.randomUUID(), Instant.EPOCH);
        var latest = new WorkScheduleException(employee, date, 240, 5,
                WorkScheduleException.Category.SPECIAL, "Plantão especial", UUID.randomUUID(), Instant.EPOCH.plusSeconds(1));
        when(schedules.findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(employeeId, date))
                .thenReturn(java.util.Optional.of(schedule));
        when(schedules.findByEmployee_IdAndEffectiveFromGreaterThanAndEffectiveFromLessThanEqualOrderByEffectiveFromAsc(employeeId, date, date))
                .thenReturn(List.of());
        when(exceptions.findByEmployee_IdAndWorkDateBetweenOrderByWorkDateAscCreatedAtUtcAsc(employeeId, date, date))
                .thenReturn(List.of(earlier, latest));

        var plan = new WorkSchedulePlan(schedules, exceptions).calculate(employeeId, date, date);

        assertEquals(240, plan.minutes());
        assertEquals(5, plan.toleranceMinutes());
        assertEquals(1, plan.coveredDays());
    }
}
