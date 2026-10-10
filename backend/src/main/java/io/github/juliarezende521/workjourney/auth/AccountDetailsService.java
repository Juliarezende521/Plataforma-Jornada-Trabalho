package io.github.juliarezende521.workjourney.auth;

import io.github.juliarezende521.workjourney.account.AppUser;
import io.github.juliarezende521.workjourney.account.AppUserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!bootstrap")
public class AccountDetailsService implements UserDetailsService {
    private final AppUserRepository users;
    public AccountDetailsService(AppUserRepository users) { this.users = users; }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        AppUser account = users.findByEmail(AppUser.normalizeEmail(email))
                .filter(user -> user.getEmployee() != null)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        var employee = account.getEmployee();
        return new AccountPrincipal(new AccountPrincipal.Identity(account.getId(), employee.getId(),
                account.getEmail(), employee.getName(), employee.getReferenceTimeZoneId()),
                account.getPasswordHash(), account.isActive());
    }
}
