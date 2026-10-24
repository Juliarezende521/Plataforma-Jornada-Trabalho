package io.github.juliarezende521.workjourney.auth;

import io.github.juliarezende521.workjourney.account.AppUserRepository;
import io.github.juliarezende521.workjourney.account.Employee;
import io.github.juliarezende521.workjourney.account.EmployeeRepository;
import io.github.juliarezende521.workjourney.journey.marking.CurrentEmployeeIdentity;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class HrAuthorizationTest {
    private final CurrentEmployeeIdentity identity = mock(CurrentEmployeeIdentity.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final HrAuthorization authorization = new HrAuthorization(identity, users, employees);
    private final CurrentEmployeeIdentity.Identity actor =
            new CurrentEmployeeIdentity.Identity(UUID.randomUUID(), UUID.randomUUID());

    @Test
    void returnsCurrentActorWhenHrRoleAndEmployeeAreStillActive() {
        var employee = mock(Employee.class);
        when(identity.require()).thenReturn(actor);
        when(users.existsByIdAndActiveTrueAndHrReaderTrue(actor.userId())).thenReturn(true);
        when(employees.findOwnedActive(actor.employeeId(), actor.userId())).thenReturn(Optional.of(employee));

        assertEquals(actor, authorization.require());
    }

    @Test
    void rejectsRevokedRoleWithoutQueryingEmployee() {
        when(identity.require()).thenReturn(actor);

        assertThrows(AccessDeniedException.class, authorization::require);
        verifyNoInteractions(employees);
    }

    @Test
    void rejectsInactiveOrUnownedEmployeeIdentity() {
        when(identity.require()).thenReturn(actor);
        when(users.existsByIdAndActiveTrueAndHrReaderTrue(actor.userId())).thenReturn(true);
        when(employees.findOwnedActive(actor.employeeId(), actor.userId())).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, authorization::require);
    }
}
