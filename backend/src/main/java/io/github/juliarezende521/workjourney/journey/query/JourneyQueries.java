package io.github.juliarezende521.workjourney.journey.query;

import io.github.juliarezende521.workjourney.account.EmployeeRepository;
import io.github.juliarezende521.workjourney.journey.*;
import io.github.juliarezende521.workjourney.journey.marking.CurrentEmployeeIdentity;
import io.github.juliarezende521.workjourney.journey.persistence.JpaWorkSessionStore;
import io.github.juliarezende521.workjourney.time.UtcInstants;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!bootstrap")
public class JourneyQueries {
    private final CurrentEmployeeIdentity identity;
    private final EmployeeRepository employees;
    private final JpaWorkSessionStore store;
    private final Clock clock;
    public JourneyQueries(CurrentEmployeeIdentity identity, EmployeeRepository employees, JpaWorkSessionStore store, Clock clock) {
        this.identity=identity; this.employees=employees; this.store=store; this.clock=clock;
    }
    // Same employee lock as markings: page, summary and Clock describe a consistent snapshot.
    @Transactional
    public History history(LocalDate from, LocalDate to, int page, int size) {
        validate(from, to, page, size);
        UUID employee=lockActor();
        Instant observed=UtcInstants.normalize(clock.instant());
        long total=store.historyCount(employee, from, to);
        var items=store.history(employee, from, to, Math.multiplyExact(page, size), size).stream()
                .map(session -> Item.from(session, observed)).toList();
        Duration closed=Duration.ZERO;
        long closedCount=0, pendingCount=0;
        // Bounded batches; never retain the full period's events in memory.
        for (int offset=0; offset < total; offset=Math.addExact(offset, 100)) {
            for (var session : store.history(employee, from, to, offset, 100)) {
                if (session.getState()==WorkState.CLOSED) { closed=closed.plus(session.closedDuration()); closedCount++; }
                else pendingCount++;
            }
        }
        return new History(items, page, size, total, observed, new Summary(closed.toString(), closedCount, pendingCount));
    }
    @Transactional
    public Detail detail(UUID id) {
        var session=store.findOwned(id, lockActor()).orElseThrow(NotVisible::new).session();
        return describe(session, UtcInstants.normalize(clock.instant()));
    }
    private UUID lockActor() {
        var actor=identity.require();
        employees.lockOwnedActive(actor.employeeId(), actor.userId()).orElseThrow(() -> new AccessDeniedException("Employee access denied"));
        return actor.employeeId();
    }
    public static void validate(LocalDate from, LocalDate to, int page, int size) {
        if (from==null || to==null || from.getYear()<1 || to.getYear()>9999 || to.isBefore(from) || java.time.temporal.ChronoUnit.DAYS.between(from,to)>365
                || page<0 || size<1 || size>100 || page>Integer.MAX_VALUE/100) throw new InvalidFilter();
    }
    public static Detail describe(WorkSession session, Instant observed) {
        var events=session.getEvents();
        var intervals=new ArrayList<Interval>();
        for (int i=0;i<events.size();i++) {
            var event=events.get(i);
            if (event.type()==WorkEventType.EXIT) continue;
            boolean provisional=i+1==events.size();
            Instant end=provisional ? observed : events.get(i+1).occurredAtUtc();
            if (end.isBefore(event.occurredAtUtc())) throw new IllegalStateException("Server clock precedes stored events");
            intervals.add(new Interval(event.type()==WorkEventType.PAUSE_START ? "PAUSE" : "WORK",
                    event.occurredAtUtc(), end, Duration.between(event.occurredAtUtc(),end).toString(), provisional));
        }
        return new Detail(Item.from(session, observed), observed,
                events.stream().map(e -> new Event(e.id(), e.sequence(), e.type(), e.occurredAtUtc(), e.recordedAtUtc())).toList(), List.copyOf(intervals));
    }
    public record Item(UUID id, WorkState state, LocalDate workDate, String referenceTimeZoneId,
                       Instant openedAtUtc, Instant closedAtUtc, String workDuration, boolean provisional) {
        static Item from(WorkSession s, Instant observed) {
            return new Item(s.getId(),s.getState(),s.getWorkDate(),s.getReferenceTimeZoneId(),s.getOpenedAtUtc(),s.getClosedAtUtc(),
                    (s.getState()==WorkState.CLOSED ? s.closedDuration() : s.durationAt(observed)).toString(),s.getState()!=WorkState.CLOSED);
        }
    }
    public record Summary(String closedWorkDuration, long closedCount, long pendingCount) { }
    public record History(List<Item> items, int page, int size, long totalElements, Instant observedAtUtc, Summary summary) { }
    public record Event(UUID id, long sequence, WorkEventType type, Instant occurredAtUtc, Instant recordedAtUtc) { }
    public record Interval(String kind, Instant startAtUtc, Instant endAtUtc, String duration, boolean provisional) { }
    public record Detail(Item session, Instant observedAtUtc, List<Event> events, List<Interval> intervals) { }
    public static final class InvalidFilter extends RuntimeException { }
    public static final class NotVisible extends RuntimeException { }
}
