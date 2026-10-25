package io.github.juliarezende521.workjourney.auth;

import java.util.ArrayList;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class AccountPrincipal extends User {
    private final Identity identity;

    public AccountPrincipal(Identity identity, String hash, boolean active) {
        super(identity.email(), hash, active, true, true, true, authorities(identity));
        this.identity = identity;
    }
    public Identity identity() { return identity; }

    private static java.util.List<SimpleGrantedAuthority> authorities(Identity identity) {
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
        if (identity.hrReader()) authorities.add(new SimpleGrantedAuthority("ROLE_HR"));
        if (identity.manager()) authorities.add(new SimpleGrantedAuthority("ROLE_MANAGER"));
        return java.util.List.copyOf(authorities);
    }

    public record Identity(UUID userId, UUID employeeId, String email, String name,
                           String referenceTimeZoneId, boolean hrReader, boolean manager) implements java.io.Serializable {
        public Identity(UUID userId, UUID employeeId, String email, String name, String referenceTimeZoneId) {
            this(userId, employeeId, email, name, referenceTimeZoneId, false, false);
        }
        public Identity(UUID userId, UUID employeeId, String email, String name,
                        String referenceTimeZoneId, boolean hrReader) {
            this(userId, employeeId, email, name, referenceTimeZoneId, hrReader, false);
        }
    }
}
