package io.github.juliarezende521.workjourney.journey.query;

import io.github.juliarezende521.workjourney.account.EmployeeRepository;
import io.github.juliarezende521.workjourney.adjustment.EffectiveTimelineService;
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
    private final EffectiveTimelineService timelines;
    public JourneyQueries(CurrentEmployeeIdentity identity, EmployeeRepository employees, JpaWorkSessionStore store,
                          EffectiveTimelineService timelines, Clock clock) {
        this.identity=identity; this.employees=employees; this.store=store; this.timelines=timelines; this.clock=clock;
    }
    // Same employee lock as markings: page, events and Clock describe a consistent snapshot.
    @Transactional
    public History history(LocalDate from, LocalDate to, int page, int size) {
        validate(from, to, page, size);
        UUID employee=lockActor();
        Instant observed=UtcInstants.normalize(clock.instant());
        long total=store.historyCount(employee, from, to);
        var items=store.history(employee, from, to, Math.multiplyExact(page, size), size).stream()
                .map(session -> Item.from(session, observed)).toList();
        return new History(items, page, size, total, observed);
    }
    @Transactional
    public Detail detail(UUID id) {
        var session=store.findOwned(id, lockActor()).orElseThrow(NotVisible::new).session();
        var observed=UtcInstants.normalize(clock.instant());
        return describe(session,observed,timelines.project(session,observed));
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
        return describe(session,observed,EffectiveTimelineService.original(session,observed));
    }
    public static Detail describe(WorkSession session, Instant observed, EffectiveTimelineService.Timeline effective) {
        var events=session.getEvents();
        return new Detail(Item.from(session, observed), observed,
                events.stream().map(e -> new Event(e.id(), e.sequence(), e.type(), e.occurredAtUtc(), e.recordedAtUtc(), e.recordedTimeZoneId(), e.workMode())).toList(),
                new Effective(effective.state(),effective.workDuration().toString(),effective.adjusted(),effective.events()));
    }

    public record Item(UUID id, WorkState state, LocalDate workDate, String referenceTimeZoneId,
                       Instant openedAtUtc, Instant closedAtUtc) {
        static Item from(WorkSession s, Instant observed) {
            return new Item(s.getId(),s.getState(),s.getWorkDate(),s.getReferenceTimeZoneId(),s.getOpenedAtUtc(),s.getClosedAtUtc());
        }
    }
    public record History(List<Item> items, int page, int size, long totalElements, Instant observedAtUtc) { }
    public record Event(UUID id, long sequence, WorkEventType type, Instant occurredAtUtc, Instant recordedAtUtc, String recordedTimeZoneId, WorkMode workMode) { }
    public record Effective(WorkState state,String workDuration,boolean adjusted,List<EffectiveTimelineService.Event> events) { }
    public record Detail(Item session, Instant observedAtUtc, List<Event> events,Effective effective) { }
    public static final class InvalidFilter extends RuntimeException { }
    public static final class NotVisible extends RuntimeException { }
}
