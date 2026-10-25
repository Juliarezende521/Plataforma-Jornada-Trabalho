package io.github.juliarezende521.workjourney.auth;

import io.github.juliarezende521.workjourney.account.*;
import io.github.juliarezende521.workjourney.team.TeamAssignmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountDetailsServiceTest {
    @Test
    void grantsManagerRoleOnlyWhenEmployeeHasCurrentDirectReports() {
        var repository = mock(AppUserRepository.class);
        var assignments = mock(TeamAssignmentRepository.class);
        var account = mock(AppUser.class);
        var employee = mock(Employee.class);
        var employeeId = UUID.randomUUID();
        when(repository.findByEmail("manager@example.invalid")).thenReturn(Optional.of(account));
        when(account.getEmployee()).thenReturn(employee);
        when(account.getId()).thenReturn(UUID.randomUUID());
        when(account.getEmail()).thenReturn("manager@example.invalid");
        when(account.getPasswordHash()).thenReturn("hash-used-only-in-mock");
        when(account.isActive()).thenReturn(true);
        when(employee.getId()).thenReturn(employeeId);
        when(employee.getName()).thenReturn("Gestora");
        when(employee.getReferenceTimeZoneId()).thenReturn("America/Sao_Paulo");
        var clock = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);
        when(assignments.countCurrentTeam(employeeId, java.time.LocalDate.of(2026, 10, 23))).thenReturn(1L);

        var principal = (AccountPrincipal) new AccountDetailsService(repository, assignments, clock)
                .loadUserByUsername("manager@example.invalid");

        assertTrue(principal.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_MANAGER")));
        assertTrue(principal.identity().manager());
    }

    @Test
    void mapsInactiveAccountWithoutExposingEntityAsPrincipal() {
        var repository = mock(AppUserRepository.class);
        var account = mock(AppUser.class);
        var employee = mock(Employee.class);
        when(repository.findByEmail("brasil@example.invalid")).thenReturn(Optional.of(account));
        when(account.getEmployee()).thenReturn(employee);
        when(account.getId()).thenReturn(UUID.randomUUID());
        when(account.getEmail()).thenReturn("brasil@example.invalid");
        when(account.getPasswordHash()).thenReturn("hash-used-only-in-mock");
        when(account.isActive()).thenReturn(false);
        when(employee.getId()).thenReturn(UUID.randomUUID());
        when(employee.getName()).thenReturn("Brasil");
        when(employee.getReferenceTimeZoneId()).thenReturn("America/Sao_Paulo");
        var principal = (AccountPrincipal) new AccountDetailsService(
                repository, mock(TeamAssignmentRepository.class), Clock.systemUTC())
                .loadUserByUsername(" BRASIL@EXAMPLE.INVALID ");
        assertFalse(principal.isEnabled());
        assertEquals(employee.getId(), principal.identity().employeeId());
    }

    @Test
    void accountWithoutEmployeeCannotLogIn() {
        var repository = mock(AppUserRepository.class);
        when(repository.findByEmail("a@example.invalid")).thenReturn(Optional.of(mock(AppUser.class)));
        assertThrows(UsernameNotFoundException.class,
                () -> new AccountDetailsService(repository, mock(TeamAssignmentRepository.class), Clock.systemUTC())
                        .loadUserByUsername("a@example.invalid"));
    }
}
