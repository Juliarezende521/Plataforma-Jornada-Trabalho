package io.github.juliarezende521.workjourney.team;

import io.github.juliarezende521.workjourney.account.EmployeeRepository;
import io.github.juliarezende521.workjourney.journey.marking.CurrentEmployeeIdentity;
import java.time.*;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@Profile("!bootstrap")
public class ManagerAuthorization {
    private final CurrentEmployeeIdentity identity;
    private final EmployeeRepository employees;
    private final TeamAssignmentRepository assignments;
    private final Clock clock;

    public ManagerAuthorization(CurrentEmployeeIdentity identity, EmployeeRepository employees,
                                TeamAssignmentRepository assignments, Clock clock) {
        this.identity = identity;
        this.employees = employees;
        this.assignments = assignments;
        this.clock = clock;
    }

    public CurrentEmployeeIdentity.Identity requireTarget(UUID targetEmployeeId) {
        var actor = identity.require();
        var manager = employees.findOwnedActive(actor.employeeId(), actor.userId())
                .orElseThrow(() -> new AccessDeniedException("Manager access denied"));
        var today = clock.instant().atZone(ZoneId.of(manager.getReferenceTimeZoneId())).toLocalDate();
        var current = assignments
                .findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(targetEmployeeId, today)
                .orElseThrow(() -> new AccessDeniedException("Manager access denied"));
        if (!current.manager().getId().equals(actor.employeeId())) {
            throw new AccessDeniedException("Manager access denied");
        }
        return actor;
    }

    public Context requireManager() {
        var actor = identity.require();
        var manager = employees.findOwnedActive(actor.employeeId(), actor.userId())
                .orElseThrow(() -> new AccessDeniedException("Manager access denied"));
        var today = clock.instant().atZone(ZoneId.of(manager.getReferenceTimeZoneId())).toLocalDate();
        if (assignments.countCurrentTeam(actor.employeeId(), today) == 0) {
            throw new AccessDeniedException("Manager access denied");
        }
        return new Context(actor, today);
    }

    public record Context(CurrentEmployeeIdentity.Identity actor, LocalDate referenceDate) { }
}
