package io.github.juliarezende521.workjourney.journey.marking;

import io.github.juliarezende521.workjourney.account.EmployeeRepository;
import io.github.juliarezende521.workjourney.journey.WorkSession;
import io.github.juliarezende521.workjourney.journey.persistence.JpaWorkSessionStore;
import io.github.juliarezende521.workjourney.time.UtcInstants;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!bootstrap")
public class CurrentWorkSessionService {
    private final CurrentEmployeeIdentity identity;
    private final EmployeeRepository employees;
    private final JpaWorkSessionStore sessions;
    private final Clock clock;

    public CurrentWorkSessionService(CurrentEmployeeIdentity identity, EmployeeRepository employees,
                                     JpaWorkSessionStore sessions, Clock clock) {
        this.identity = identity; this.employees = employees; this.sessions = sessions; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Snapshot current() {
        var actor = identity.require();
        employees.findOwnedActive(actor.employeeId(), actor.userId())
                .orElseThrow(() -> new AccessDeniedException("Employee access denied"));
        var current = sessions.findCurrentOwned(actor.employeeId());
        Instant observed = UtcInstants.normalize(clock.instant());
        var session = current.map(JpaWorkSessionStore.StoredSession::session).orElse(null);
        if (session != null && observed.isBefore(session.getLastEventAtUtc())) {
            throw new IllegalStateException("Server clock precedes stored events");
        }
        return new Snapshot(session, observed);
    }

    public record Snapshot(WorkSession session, Instant observedAtUtc) { }
}
