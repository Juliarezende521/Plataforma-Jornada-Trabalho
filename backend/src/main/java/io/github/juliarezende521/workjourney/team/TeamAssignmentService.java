package io.github.juliarezende521.workjourney.team;

import io.github.juliarezende521.workjourney.account.EmployeeRepository;
import io.github.juliarezende521.workjourney.adjustment.*;
import io.github.juliarezende521.workjourney.auth.HrAuthorization;
import io.github.juliarezende521.workjourney.journey.query.JourneyQueries;
import io.github.juliarezende521.workjourney.time.UtcInstants;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!bootstrap")
class TeamAssignmentService {
    private final HrAuthorization hrAuthorization;
    private final EmployeeRepository employees;
    private final TeamAssignmentRepository assignments;
    private final AuditEntryRepository audits;
    private final Clock clock;

    TeamAssignmentService(HrAuthorization hrAuthorization, EmployeeRepository employees,
                          TeamAssignmentRepository assignments, AuditEntryRepository audits, Clock clock) {
        this.hrAuthorization = hrAuthorization;
        this.employees = employees;
        this.assignments = assignments;
        this.audits = audits;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    View current(UUID employeeId, LocalDate on) {
        hrAuthorization.require();
        var employee = employees.findActiveById(employeeId).orElseThrow(JourneyQueries.NotVisible::new);
        LocalDate date = on == null
                ? clock.instant().atZone(ZoneId.of(employee.getReferenceTimeZoneId())).toLocalDate() : on;
        return assignments.findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(employeeId, date)
                .map(View::from).orElse(null);
    }

    @Transactional(readOnly = true)
    Page history(UUID employeeId, int page, int size) {
        hrAuthorization.require();
        validatePage(page, size);
        employees.findActiveById(employeeId).orElseThrow(JourneyQueries.NotVisible::new);
        var result = assignments.findByEmployee_Id(employeeId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "effectiveFrom", "id")));
        return new Page(result.map(View::from).getContent(), page, size, result.getTotalElements());
    }

    @Transactional(timeout = 15)
    View create(UUID employeeId, UUID managerEmployeeId, LocalDate effectiveFrom) {
        var actor = hrAuthorization.require();
        if (effectiveFrom == null) throw new InvalidTeamAssignment();
        var employee = employees.lockActive(employeeId).orElseThrow(JourneyQueries.NotVisible::new);
        var manager = employees.findActiveById(managerEmployeeId).orElseThrow(JourneyQueries.NotVisible::new);
        if (employeeId.equals(managerEmployeeId)) throw new InvalidTeamAssignment(409, "SELF_ASSIGNMENT");
        if (assignments.existsByEmployee_IdAndEffectiveFrom(employeeId, effectiveFrom)) {
            throw new InvalidTeamAssignment(409, "VERSION_EXISTS");
        }
        requireAcyclic(employeeId, managerEmployeeId, effectiveFrom);
        var now = UtcInstants.normalize(clock.instant());
        var assignment = assignments.save(new TeamAssignment(employee, manager, effectiveFrom, actor.userId(), now));
        audits.save(new AuditEntry(actor.userId(), "TEAM_ASSIGNMENT_CREATED", "TEAM_ASSIGNMENT",
                assignment.id(), now, "employeeId=" + employeeId + ",managerEmployeeId=" + managerEmployeeId
                        + ",effectiveFrom=" + effectiveFrom));
        return View.from(assignment);
    }

    private void requireAcyclic(UUID employeeId, UUID managerEmployeeId, LocalDate on) {
        var visited = new HashSet<UUID>();
        UUID cursor = managerEmployeeId;
        while (visited.add(cursor)) {
            if (cursor.equals(employeeId)) throw new InvalidTeamAssignment(409, "CYCLE");
            var parent = assignments
                    .findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(cursor, on);
            if (parent.isEmpty()) return;
            cursor = parent.get().manager().getId();
        }
        throw new InvalidTeamAssignment(409, "CYCLE");
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || page > Integer.MAX_VALUE / 100 || size < 1 || size > 100) {
            throw new InvalidTeamAssignment();
        }
    }

    record View(UUID id, UUID employeeId, UUID managerEmployeeId, String managerName,
                LocalDate effectiveFrom, Instant createdAtUtc) {
        static View from(TeamAssignment value) {
            return new View(value.id(), value.employee().getId(), value.manager().getId(),
                    value.manager().getName(), value.effectiveFrom(), value.createdAt());
        }
    }
    record Page(List<View> items, int page, int size, long totalElements) { }
}
