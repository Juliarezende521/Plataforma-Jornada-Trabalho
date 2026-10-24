package io.github.juliarezende521.workjourney.auth;

import io.github.juliarezende521.workjourney.account.AppUserRepository;
import io.github.juliarezende521.workjourney.account.EmployeeRepository;
import io.github.juliarezende521.workjourney.journey.marking.CurrentEmployeeIdentity;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Validates the HR role and its active employee ownership against current database state. */
@Service
@Profile("!bootstrap")
public class HrAuthorization {
    private final CurrentEmployeeIdentity identity;
    private final AppUserRepository users;
    private final EmployeeRepository employees;

    public HrAuthorization(CurrentEmployeeIdentity identity, AppUserRepository users, EmployeeRepository employees) {
        this.identity = identity;
        this.users = users;
        this.employees = employees;
    }

    public CurrentEmployeeIdentity.Identity require() {
        var actor = identity.require();
        if (!users.existsByIdAndActiveTrueAndHrReaderTrue(actor.userId())
                || employees.findOwnedActive(actor.employeeId(), actor.userId()).isEmpty()) {
            throw new AccessDeniedException("HR access denied");
        }
        return actor;
    }
}
