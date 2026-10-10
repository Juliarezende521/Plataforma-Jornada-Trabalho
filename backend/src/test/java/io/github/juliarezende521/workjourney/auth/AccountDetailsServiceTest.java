package io.github.juliarezende521.workjourney.auth;

import io.github.juliarezende521.workjourney.account.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountDetailsServiceTest {
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
        var principal = (AccountPrincipal) new AccountDetailsService(repository)
                .loadUserByUsername(" BRASIL@EXAMPLE.INVALID ");
        assertFalse(principal.isEnabled());
        assertEquals(employee.getId(), principal.identity().employeeId());
    }

    @Test
    void accountWithoutEmployeeCannotLogIn() {
        var repository = mock(AppUserRepository.class);
        when(repository.findByEmail("a@example.invalid")).thenReturn(Optional.of(mock(AppUser.class)));
        assertThrows(UsernameNotFoundException.class,
                () -> new AccountDetailsService(repository).loadUserByUsername("a@example.invalid"));
    }
}
