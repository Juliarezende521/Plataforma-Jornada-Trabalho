package io.github.juliarezende521.workjourney.team;

import io.github.juliarezende521.workjourney.account.*;
import io.github.juliarezende521.workjourney.adjustment.*;
import io.github.juliarezende521.workjourney.auth.HrAuthorization;
import io.github.juliarezende521.workjourney.journey.marking.CurrentEmployeeIdentity;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TeamAssignmentServiceTest {
    private final HrAuthorization authorization = mock(HrAuthorization.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final TeamAssignmentRepository assignments = mock(TeamAssignmentRepository.class);
    private final AuditEntryRepository audits = mock(AuditEntryRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);
    private final AppUser hrAccount = account("hr@example.invalid");
    private final Employee employee = employee("worker@example.invalid", "Worker");
    private final Employee manager = employee("manager@example.invalid", "Manager");
    private TeamAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new TeamAssignmentService(authorization, employees, assignments, audits, clock);
        when(authorization.require()).thenReturn(
                new CurrentEmployeeIdentity.Identity(hrAccount.getId(), UUID.randomUUID()));
        when(employees.lockActive(employee.getId())).thenReturn(Optional.of(employee));
        when(employees.findActiveById(manager.getId())).thenReturn(Optional.of(manager));
        when(assignments.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createsVersionedAuditedAssignment() {
        var result = service.create(employee.getId(), manager.getId(), LocalDate.of(2026, 11, 1));

        assertEquals(manager.getId(), result.managerEmployeeId());
        assertEquals("Manager", result.managerName());
        verify(assignments).save(any(TeamAssignment.class));
        verify(audits).save(any(AuditEntry.class));
    }

    @Test
    void rejectsSelfAssignmentBeforePersistence() {
        when(employees.findActiveById(employee.getId())).thenReturn(Optional.of(employee));

        var error = assertThrows(InvalidTeamAssignment.class,
                () -> service.create(employee.getId(), employee.getId(), LocalDate.of(2026, 11, 1)));

        assertEquals("SELF_ASSIGNMENT", error.reason());
        verify(assignments, never()).save(any());
    }

    @Test
    void rejectsHierarchyCycle() {
        var parent = new TeamAssignment(manager, employee, LocalDate.of(2026, 10, 1),
                hrAccount.getId(), Instant.EPOCH);
        when(assignments.findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                manager.getId(), LocalDate.of(2026, 11, 1))).thenReturn(Optional.of(parent));

        var error = assertThrows(InvalidTeamAssignment.class,
                () -> service.create(employee.getId(), manager.getId(), LocalDate.of(2026, 11, 1)));

        assertEquals("CYCLE", error.reason());
        verify(assignments, never()).save(any());
    }

    private static AppUser account(String email) {
        return new AppUser(email, "$2a$12$" + "A".repeat(53));
    }
    private static Employee employee(String email, String name) {
        return new Employee(account(email), name, "UTC");
    }
}
