package io.github.juliarezende521.workjourney.journey.marking;

import io.github.juliarezende521.workjourney.auth.AccountPrincipal;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentEmployeeIdentity {
    public Identity require() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AccountPrincipal principal)
                || !principal.isEnabled() || authentication.getAuthorities().stream()
                    .noneMatch(authority -> "ROLE_EMPLOYEE".equals(authority.getAuthority()))) {
            throw new AuthenticationCredentialsNotFoundException("Employee authentication required");
        }
        var identity = principal.identity();
        if (identity.userId() == null || identity.employeeId() == null) {
            throw new AuthenticationCredentialsNotFoundException("Employee authentication required");
        }
        return new Identity(identity.userId(), identity.employeeId());
    }

    public record Identity(UUID userId, UUID employeeId) { }
}
