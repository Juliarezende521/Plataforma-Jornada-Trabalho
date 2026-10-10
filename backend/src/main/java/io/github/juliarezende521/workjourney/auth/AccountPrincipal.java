package io.github.juliarezende521.workjourney.auth;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class AccountPrincipal extends User {
    private final Identity identity;

    public AccountPrincipal(Identity identity, String hash, boolean active) {
        super(identity.email(), hash, active, true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
        this.identity = identity;
    }
    public Identity identity() { return identity; }

    public record Identity(UUID userId, UUID employeeId, String email, String name,
                           String referenceTimeZoneId) implements java.io.Serializable { }
}
