package io.github.juliarezende521.workjourney.team;

import io.github.juliarezende521.workjourney.account.AppUserRepository;
import java.time.*;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("evaluation & !bootstrap")
@Order(20)
class EvaluationTeamAssignments implements ApplicationRunner {
    private static final LocalDate EFFECTIVE_FROM = LocalDate.of(2020, 1, 1);
    private final AppUserRepository users;
    private final TeamAssignmentRepository assignments;
    private final Clock clock;

    EvaluationTeamAssignments(AppUserRepository users, TeamAssignmentRepository assignments, Clock clock) {
        this.users = users;
        this.assignments = assignments;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var employee = users.findByEmail("brasil@example.invalid").orElseThrow().getEmployee();
        var manager = users.findByEmail("europa@example.invalid").orElseThrow().getEmployee();
        var creator = users.findByEmail("rh@example.invalid").orElseThrow();
        if (!assignments.existsByEmployee_IdAndEffectiveFrom(employee.getId(), EFFECTIVE_FROM)) {
            assignments.save(new TeamAssignment(employee, manager, EFFECTIVE_FROM, creator.getId(), clock.instant()));
        }
    }
}
