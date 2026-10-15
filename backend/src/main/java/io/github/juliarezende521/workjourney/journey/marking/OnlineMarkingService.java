package io.github.juliarezende521.workjourney.journey.marking;

import io.github.juliarezende521.workjourney.account.Employee;
import io.github.juliarezende521.workjourney.account.EmployeeRepository;
import io.github.juliarezende521.workjourney.journey.WorkEventType;
import io.github.juliarezende521.workjourney.journey.WorkSession;
import io.github.juliarezende521.workjourney.journey.persistence.JpaWorkSessionStore;
import io.github.juliarezende521.workjourney.time.UtcInstants;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static io.github.juliarezende521.workjourney.journey.marking.MarkingResult.Code.*;

@Service
@Profile("!bootstrap")
public class OnlineMarkingService {
    private final CurrentEmployeeIdentity identity;
    private final EmployeeRepository employees;
    private final JpaWorkSessionStore sessions;
    private final OnlineMarkingReceiptStore receipts;
    private final Clock clock;

    public OnlineMarkingService(CurrentEmployeeIdentity identity, EmployeeRepository employees,
                               JpaWorkSessionStore sessions, OnlineMarkingReceiptStore receipts, Clock clock) {
        this.identity = identity;
        this.employees = employees;
        this.sessions = sessions;
        this.receipts = receipts;
        this.clock = clock;
    }

    @Transactional(timeout = 15)
    public MarkingResult execute(UUID key, MarkingCommand command) {
        var actor = identity.require();
        Objects.requireNonNull(key, "Idempotency key is required");
        Objects.requireNonNull(command, "Command is required");
        // Database lock, not an in-memory mutex: all commands for this employee serialize.
        var employee = employees.lockOwnedActive(actor.employeeId(), actor.userId())
                .orElseThrow(() -> new AccessDeniedException("Employee access denied"));
        var previous = receipts.find(employee.getId(), key);
        if (previous.isPresent()) {
            return previous.get().matches(command) ? previous.get().result()
                    : MarkingResult.conflict(key, command, KEY_REUSED, now());
        }
        Instant instant = now();
        var result = apply(employee, actor.userId(), key, command, instant);
        // Success OR definitive business rejection, atomically with any new event/version.
        // Database failures escape; never catch them and continue in a failed transaction.
        receipts.save(employee.getId(), command, result);
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<MarkingResult> lookup(UUID key) {
        var actor = identity.require();
        Objects.requireNonNull(key, "Idempotency key is required");
        employees.findOwnedActive(actor.employeeId(), actor.userId())
                .orElseThrow(() -> new AccessDeniedException("Employee access denied"));
        return receipts.find(actor.employeeId(), key).map(OnlineMarkingReceipt::result);
    }

    private MarkingResult apply(Employee employee, UUID userId, UUID key, MarkingCommand command, Instant instant) {
        String recordedZone = command.timeZoneId() == null ? employee.getReferenceTimeZoneId() : command.timeZoneId();
        var current = sessions.findCurrentOwned(employee.getId());
        JpaWorkSessionStore.StoredSession stored;
        if (command.type() == WorkEventType.ENTRY) {
            if (current.isPresent()) return MarkingResult.conflict(key, command, ALREADY_OPEN, instant);
            if (sessions.latestClosedAt(employee.getId()).filter(instant::isBefore).isPresent()) {
                return MarkingResult.conflict(key, command, CLOCK_REGRESSION, instant);
            }
            stored = sessions.create(WorkSession.open(employee.getId(), userId, employee.getReferenceTimeZoneId(), instant, recordedZone, command.workMode()));
        } else {
            if (current.isEmpty()) return MarkingResult.conflict(key, command, NO_OPEN_SESSION, instant);
            var open = current.get();
            if (!open.session().getId().equals(command.expectedWorkSessionId())) {
                return MarkingResult.conflict(key, command, TARGET_CHANGED, instant);
            }
            if (instant.isBefore(open.session().getLastEventAtUtc())) {
                return MarkingResult.conflict(key, command, CLOCK_REGRESSION, instant);
            }
            try { open.session().getState().nextFor(command.type()); }
            catch (IllegalStateException invalidTransition) {
                return MarkingResult.conflict(key, command, INVALID_TRANSITION, instant);
            }
            stored = sessions.append(open.session().getId(), employee.getId(), open.version(), command.type(), instant, recordedZone, command.workMode());
        }
        var events = stored.session().getEvents();
        var event = events.get(events.size() - 1);
        return new MarkingResult(key, command.type(), 201, RECORDED, stored.session().getId(),
                event.id(), event.occurredAtUtc(), instant);
    }

    private Instant now() { return UtcInstants.normalize(clock.instant()); }
}
