package io.github.juliarezende521.workjourney.journey.query;

import io.github.juliarezende521.workjourney.account.EmployeeRepository;
import io.github.juliarezende521.workjourney.adjustment.EffectiveTimelineService;
import io.github.juliarezende521.workjourney.journey.*;
import io.github.juliarezende521.workjourney.journey.marking.CurrentEmployeeIdentity;
import io.github.juliarezende521.workjourney.journey.persistence.JpaWorkSessionStore;
import io.github.juliarezende521.workjourney.schedule.WorkSchedulePlan;
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
    private final WorkSchedulePlan schedules;
    public JourneyQueries(CurrentEmployeeIdentity identity, EmployeeRepository employees, JpaWorkSessionStore store,
                          EffectiveTimelineService timelines, WorkSchedulePlan schedules, Clock clock) {
        this.identity=identity; this.employees=employees; this.store=store; this.timelines=timelines; this.schedules=schedules; this.clock=clock;
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
    @Transactional
    public Summary summary(LocalDate from,LocalDate to) {
        validateSummary(from,to);
        return summarize(lockActor(),from,to,UtcInstants.normalize(clock.instant()));
    }
    Summary summarize(UUID employee,LocalDate from,LocalDate to,Instant observed) {
        return summarize(store,timelines,schedules,employee,from,to,observed);
    }
    static Summary summarize(JpaWorkSessionStore store,EffectiveTimelineService timelines,WorkSchedulePlan schedules,UUID employee,
                             LocalDate from,LocalDate to,Instant observed) {
        long total=store.historyCount(employee,from,to);
        long closed=0,adjusted=0;
        var duration=Duration.ZERO;
        var days=new HashSet<LocalDate>();
        final int batchSize=100;
        for(long offset=0;offset<total;offset+=batchSize) {
            var sessions=store.history(employee,from,to,Math.toIntExact(offset),batchSize);
            var projected=timelines.projectAll(sessions,observed);
            for(var session:sessions) {
                var timeline=projected.get(session.getId());
                duration=duration.plus(timeline.workDuration());
                if(timeline.state()==WorkState.CLOSED)closed++;
                if(timeline.adjusted())adjusted++;
                days.add(session.getWorkDate());
            }
        }
        var plan=schedules.calculate(employee,from,to);
        int periodDays=Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(from,to)+1);
        Long balanceSeconds=plan.coveredDays()==periodDays ? duration.minus(Duration.ofMinutes(plan.minutes())).getSeconds() : null;
        Long toleranceSeconds=balanceSeconds==null?null:Math.multiplyExact(plan.toleranceMinutes(),60);
        Boolean withinTolerance=balanceSeconds==null?null:Math.abs(balanceSeconds)<=toleranceSeconds;
        return new Summary(from,to,duration.toString(),Duration.ofMinutes(plan.minutes()).toString(),balanceSeconds,toleranceSeconds,withinTolerance,plan.coveredDays(),periodDays,
                total,closed,total-closed,adjusted,days.size(),observed);
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
    public static void validateSummary(LocalDate from,LocalDate to) {
        if(from==null||to==null||from.getYear()<1||to.getYear()>9999||to.isBefore(from)
                ||java.time.temporal.ChronoUnit.DAYS.between(from,to)>365)throw new InvalidFilter();
    }
    public static Detail describe(WorkSession session, Instant observed) {
        return describe(session,observed,EffectiveTimelineService.original(session,observed));
    }
    public static Detail describe(WorkSession session, Instant observed, EffectiveTimelineService.Timeline effective) {
        var events=session.getEvents();
        var corrections=effective.events().stream().filter(event -> "ADJUSTMENT".equals(event.source())&&event.originalEventId()!=null)
                .map(EffectiveTimelineService.Event::originalEventId).collect(java.util.stream.Collectors.toSet());
        return new Detail(Item.from(session, observed), observed,
                events.stream().map(e -> new Event(e.id(), e.sequence(), e.type(), e.occurredAtUtc(), e.recordedAtUtc(), e.recordedTimeZoneId(), e.workMode(),corrections.contains(e.id()))).toList(),
                new Effective(effective.state(),effective.workDuration().toString(),effective.adjusted(),effective.events()));
    }

    public record Item(UUID id, WorkState state, LocalDate workDate, String referenceTimeZoneId,
                       Instant openedAtUtc, Instant closedAtUtc) {
        static Item from(WorkSession s, Instant observed) {
            return new Item(s.getId(),s.getState(),s.getWorkDate(),s.getReferenceTimeZoneId(),s.getOpenedAtUtc(),s.getClosedAtUtc());
        }
    }
    public record History(List<Item> items, int page, int size, long totalElements, Instant observedAtUtc) { }
    public record Summary(LocalDate from,LocalDate to,String totalWorkDuration,String plannedWorkDuration,Long balanceSeconds,Long toleranceSeconds,Boolean withinTolerance,int scheduledDays,int periodDays,long totalSessions,long closedSessions,
                          long openSessions,long adjustedSessions,int workedDays,Instant observedAtUtc) { }
    public record Event(UUID id, long sequence, WorkEventType type, Instant occurredAtUtc, Instant recordedAtUtc, String recordedTimeZoneId, WorkMode workMode,boolean correction) { }
    public record Effective(WorkState state,String workDuration,boolean adjusted,List<EffectiveTimelineService.Event> events) { }
    public record Detail(Item session, Instant observedAtUtc, List<Event> events,Effective effective) { }
    public static final class InvalidFilter extends RuntimeException { }
    public static final class NotVisible extends RuntimeException { }
}
