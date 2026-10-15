package io.github.juliarezende521.workjourney.journey.query;

import io.github.juliarezende521.workjourney.account.*;
import io.github.juliarezende521.workjourney.journey.marking.CurrentEmployeeIdentity;
import io.github.juliarezende521.workjourney.journey.persistence.JpaWorkSessionStore;
import io.github.juliarezende521.workjourney.time.UtcInstants;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only HR capability. Never delegates cross-employee access to the own-history API. */
@Service
@Profile("!bootstrap")
public class HrJourneyQueries {
    private final CurrentEmployeeIdentity identity;
    private final AppUserRepository users;
    private final EmployeeRepository employees;
    private final JpaWorkSessionStore store;
    private final Clock clock;

    public HrJourneyQueries(CurrentEmployeeIdentity identity, AppUserRepository users, EmployeeRepository employees,
                            JpaWorkSessionStore store, Clock clock) {
        this.identity = identity; this.users = users; this.employees = employees; this.store = store; this.clock = clock;
    }

    private void requireHr() {
        var actor = identity.require();
        if (!users.existsByIdAndActiveTrueAndHrReaderTrue(actor.userId())
                || employees.findOwnedActive(actor.employeeId(), actor.userId()).isEmpty()) {
            throw new AccessDeniedException("HR read access denied");
        }
    }

    @Transactional(readOnly = true)
    public Directory employees(int page, int size) {
        requireHr();
        if (page < 0 || page > Integer.MAX_VALUE / 100 || size < 1 || size > 100) throw new JourneyQueries.InvalidFilter();
        var result = employees.findActive(PageRequest.of(page, size, Sort.by("name", "id")));
        return new Directory(result.map(e -> new Person(e.getId(), e.getName(), e.getReferenceTimeZoneId())).getContent(),
                page, size, result.getTotalElements());
    }

    // Same target-employee lock as marking: records and page describe a consistent snapshot.
    @Transactional
    public JourneyQueries.History history(UUID employeeId, LocalDate from, LocalDate to, int page, int size) {
        requireHr();
        JourneyQueries.validate(from, to, page, size);
        employees.lockActive(employeeId).orElseThrow(JourneyQueries.NotVisible::new);
        var observed = UtcInstants.normalize(clock.instant());
        long total = store.historyCount(employeeId, from, to);
        var items = store.history(employeeId, from, to, Math.multiplyExact(page, size), size).stream()
                .map(s -> JourneyQueries.Item.from(s, observed)).toList();
        return new JourneyQueries.History(items, page, size, total, observed);
    }

    @Transactional
    public JourneyQueries.Detail detail(UUID employeeId, UUID sessionId) {
        requireHr();
        employees.lockActive(employeeId).orElseThrow(JourneyQueries.NotVisible::new);
        var session = store.findOwned(sessionId, employeeId).orElseThrow(JourneyQueries.NotVisible::new).session();
        return JourneyQueries.describe(session, UtcInstants.normalize(clock.instant()));
    }

    public record Person(UUID id, String name, String referenceTimeZoneId) { }
    public record Directory(List<Person> items, int page, int size, long totalElements) { }
}
