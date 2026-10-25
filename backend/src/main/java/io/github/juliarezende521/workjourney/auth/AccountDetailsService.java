package io.github.juliarezende521.workjourney.auth;

import io.github.juliarezende521.workjourney.account.AppUser;
import io.github.juliarezende521.workjourney.account.AppUserRepository;
import io.github.juliarezende521.workjourney.team.TeamAssignmentRepository;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!bootstrap")
public class AccountDetailsService implements UserDetailsService {
    private final AppUserRepository users;
    private final TeamAssignmentRepository assignments;
    private final Clock clock;
    public AccountDetailsService(AppUserRepository users, TeamAssignmentRepository assignments, Clock clock) {
        this.users = users;
        this.assignments = assignments;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        AppUser account = users.findByEmail(AppUser.normalizeEmail(email))
                .filter(user -> user.getEmployee() != null)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        var employee = account.getEmployee();
        var referenceDate = clock.instant().atZone(ZoneId.of(employee.getReferenceTimeZoneId())).toLocalDate();
        boolean manager = assignments.countCurrentTeam(employee.getId(), referenceDate) > 0;
        return new AccountPrincipal(new AccountPrincipal.Identity(account.getId(), employee.getId(),
                account.getEmail(), employee.getName(), employee.getReferenceTimeZoneId(),
                account.isHrReader(), manager),
                account.getPasswordHash(), account.isActive());
    }
}
