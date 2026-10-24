package io.github.juliarezende521.workjourney.closing;

import io.github.juliarezende521.workjourney.account.*;
import io.github.juliarezende521.workjourney.adjustment.*;
import io.github.juliarezende521.workjourney.auth.HrAuthorization;
import io.github.juliarezende521.workjourney.journey.marking.CurrentEmployeeIdentity;
import io.github.juliarezende521.workjourney.journey.persistence.JpaWorkSessionStore;
import io.github.juliarezende521.workjourney.schedule.WorkSchedulePlan;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MonthlyClosureServiceTest {
    private final HrAuthorization authorization = mock(HrAuthorization.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final MonthlyClosureRepository closures = mock(MonthlyClosureRepository.class);
    private final WorkAdjustmentRepository adjustments = mock(WorkAdjustmentRepository.class);
    private final JpaWorkSessionStore sessions = mock(JpaWorkSessionStore.class);
    private final EffectiveTimelineService timelines = mock(EffectiveTimelineService.class);
    private final WorkSchedulePlan schedules = mock(WorkSchedulePlan.class);
    private final AuditEntryRepository audits = mock(AuditEntryRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC);
    private final AppUser account = new AppUser("hr@example.invalid", "$2a$12$" + "A".repeat(53), true);
    private final Employee employee = new Employee(account, "Colaborador", "America/Sao_Paulo");
    private MonthlyClosureService service;

    @BeforeEach
    void setUp() {
        service = new MonthlyClosureService(authorization, employees, closures, adjustments,
                sessions, timelines, schedules, audits, clock);
        when(authorization.require()).thenReturn(
                new CurrentEmployeeIdentity.Identity(account.getId(), employee.getId()));
        when(employees.lockActive(employee.getId())).thenReturn(Optional.of(employee));
        when(sessions.historyCount(any(), any(), any())).thenReturn(0L);
        when(schedules.calculate(employee.getId(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(new WorkSchedulePlan.Plan(31L * 480, 31L * 10, 31));
        when(closures.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void closesFinishedCoveredMonthWithImmutableSnapshotAndAudit() {
        var result = service.close(employee.getId(), YearMonth.of(2026, 10));

        assertEquals("2026-10", result.competence());
        assertEquals(31L * 480 * 60, result.plannedWorkSeconds());
        assertEquals(-31L * 480 * 60, result.balanceSeconds());
        assertEquals("CLOSED", result.status());
        verify(closures).save(any(MonthlyClosure.class));
        verify(audits).save(any(AuditEntry.class));
    }

    @Test
    void rejectsCurrentMonthBeforeCalculating() {
        var error = assertThrows(InvalidMonthlyClosure.class,
                () -> service.close(employee.getId(), YearMonth.of(2026, 11)));

        assertEquals("PERIOD_NOT_FINISHED", error.reason());
        verifyNoInteractions(sessions, schedules, adjustments, audits);
    }

    @Test
    void rejectsIncompleteScheduleCoverage() {
        when(schedules.calculate(employee.getId(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(new WorkSchedulePlan.Plan(30L * 480, 30L * 10, 30));

        var error = assertThrows(InvalidMonthlyClosure.class,
                () -> service.close(employee.getId(), YearMonth.of(2026, 10)));

        assertEquals("INCOMPLETE_SCHEDULE", error.reason());
        verifyNoInteractions(audits);
    }

    @Test
    void rejectsPendingAdjustments() {
        when(adjustments.countInPeriod(employee.getId(), AdjustmentStatus.PENDING,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))).thenReturn(1L);

        var error = assertThrows(InvalidMonthlyClosure.class,
                () -> service.close(employee.getId(), YearMonth.of(2026, 10)));

        assertEquals("PENDING_ADJUSTMENTS", error.reason());
        verifyNoInteractions(audits);
    }
}
