package io.github.juliarezende521.workjourney.journey.marking;

import io.github.juliarezende521.workjourney.auth.AccountPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;

class CurrentEmployeeIdentityTest {
    private final CurrentEmployeeIdentity resolver = new CurrentEmployeeIdentity();
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void rejectsMissingAuthenticationAndUnrecognizedPrincipal() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, resolver::require);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("client-id", null, List.of()));
        assertThrows(AuthenticationCredentialsNotFoundException.class, resolver::require);
    }

    @Test
    void derivesIdentityOnlyFromAuthenticatedEmployeePrincipal() {
        var identity = new AccountPrincipal.Identity(UUID.randomUUID(), UUID.randomUUID(),
                "test@example.invalid", "Teste", "Europe/Amsterdam");
        var principal = new AccountPrincipal(identity, "test-only-hash", true);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        assertThrows(AuthenticationCredentialsNotFoundException.class, resolver::require);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        assertEquals(new CurrentEmployeeIdentity.Identity(identity.userId(), identity.employeeId()), resolver.require());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated(principal, null));
        assertThrows(AuthenticationCredentialsNotFoundException.class, resolver::require);
    }
}
