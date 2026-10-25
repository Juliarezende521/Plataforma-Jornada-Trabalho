package io.github.juliarezende521.workjourney.team;

import io.github.juliarezende521.workjourney.account.*;
import io.github.juliarezende521.workjourney.journey.marking.CurrentEmployeeIdentity;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagerAuthorizationTest {
    private final CurrentEmployeeIdentity identity = mock(CurrentEmployeeIdentity.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final TeamAssignmentRepository assignments = mock(TeamAssignmentRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void allowsOnlyCurrentDirectTeamMember() {
        var managerAccount = account("manager@example.invalid");
        var manager = new Employee(managerAccount, "Manager", "UTC");
        var target = new Employee(account("worker@example.invalid"), "Worker", "UTC");
        var outsider = new Employee(account("other@example.invalid"), "Other", "UTC");
        when(identity.require()).thenReturn(
                new CurrentEmployeeIdentity.Identity(managerAccount.getId(), manager.getId()));
        when(employees.findOwnedActive(manager.getId(), managerAccount.getId())).thenReturn(Optional.of(manager));
        when(assignments.findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                target.getId(), LocalDate.of(2026, 11, 1))).thenReturn(Optional.of(
                        new TeamAssignment(target, manager, LocalDate.of(2026, 10, 1), managerAccount.getId(), Instant.EPOCH)));
        when(assignments.findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                outsider.getId(), LocalDate.of(2026, 11, 1))).thenReturn(Optional.empty());
        var authorization = new ManagerAuthorization(identity, employees, assignments, clock);

        assertDoesNotThrow(() -> authorization.requireTarget(target.getId()));
        assertThrows(AccessDeniedException.class, () -> authorization.requireTarget(outsider.getId()));
    }

    private static AppUser account(String email) {
        return new AppUser(email, "$2a$12$" + "A".repeat(53));
    }
}
