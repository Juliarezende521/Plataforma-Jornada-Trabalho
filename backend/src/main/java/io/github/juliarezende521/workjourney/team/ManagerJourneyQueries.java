package io.github.juliarezende521.workjourney.team;

import io.github.juliarezende521.workjourney.account.EmployeeRepository;
import io.github.juliarezende521.workjourney.adjustment.EffectiveTimelineService;
import io.github.juliarezende521.workjourney.journey.persistence.JpaWorkSessionStore;
import io.github.juliarezende521.workjourney.journey.query.JourneyQueries;
import io.github.juliarezende521.workjourney.schedule.WorkSchedulePlan;
import io.github.juliarezende521.workjourney.time.UtcInstants;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!bootstrap")
class ManagerJourneyQueries {
    private final ManagerAuthorization authorization;
    private final TeamAssignmentRepository assignments;
    private final EmployeeRepository employees;
    private final JpaWorkSessionStore sessions;
    private final EffectiveTimelineService timelines;
    private final WorkSchedulePlan schedules;
    private final Clock clock;

    ManagerJourneyQueries(ManagerAuthorization authorization, TeamAssignmentRepository assignments,
                          EmployeeRepository employees, JpaWorkSessionStore sessions,
                          EffectiveTimelineService timelines, WorkSchedulePlan schedules, Clock clock) {
        this.authorization = authorization;
        this.assignments = assignments;
        this.employees = employees;
        this.sessions = sessions;
        this.timelines = timelines;
        this.schedules = schedules;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    Directory team(int page, int size) {
        validatePage(page, size);
        var context = authorization.requireManager();
        var result = assignments.findCurrentTeam(context.actor().employeeId(), context.referenceDate(),
                PageRequest.of(page, size, Sort.by("employee.name", "employee.id")));
        var items = result.map(value -> new Person(value.employee().getId(), value.employee().getName(),
                value.employee().getReferenceTimeZoneId())).getContent();
        return new Directory(items, page, size, result.getTotalElements());
    }

    @Transactional
    JourneyQueries.History history(UUID employeeId, LocalDate from, LocalDate to, int page, int size) {
        authorization.requireTarget(employeeId);
        JourneyQueries.validate(from, to, page, size);
        employees.lockActive(employeeId).orElseThrow(JourneyQueries.NotVisible::new);
        var observed = UtcInstants.normalize(clock.instant());
        long total = sessions.historyCount(employeeId, from, to);
        var items = sessions.history(employeeId, from, to, Math.multiplyExact(page, size), size).stream()
                .map(value -> JourneyQueries.Item.from(value, observed)).toList();
        return new JourneyQueries.History(items, page, size, total, observed);
    }

    @Transactional
    JourneyQueries.Detail detail(UUID employeeId, UUID sessionId) {
        authorization.requireTarget(employeeId);
        employees.lockActive(employeeId).orElseThrow(JourneyQueries.NotVisible::new);
        var session = sessions.findOwned(sessionId, employeeId).orElseThrow(JourneyQueries.NotVisible::new).session();
        var observed = UtcInstants.normalize(clock.instant());
        return JourneyQueries.describe(session, observed, timelines.project(session, observed));
    }

    @Transactional
    JourneyQueries.Summary summary(UUID employeeId, LocalDate from, LocalDate to) {
        authorization.requireTarget(employeeId);
        JourneyQueries.validateSummary(from, to);
        employees.lockActive(employeeId).orElseThrow(JourneyQueries.NotVisible::new);
        return JourneyQueries.summarize(sessions, timelines, schedules, employeeId, from, to,
                UtcInstants.normalize(clock.instant()));
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || page > Integer.MAX_VALUE / 100 || size < 1 || size > 100) {
            throw new JourneyQueries.InvalidFilter();
        }
    }

    record Person(UUID id, String name, String referenceTimeZoneId) { }
    record Directory(List<Person> items, int page, int size, long totalElements) { }
}
