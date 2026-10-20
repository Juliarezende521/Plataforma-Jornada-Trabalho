package io.github.juliarezende521.workjourney.adjustment;

import io.github.juliarezende521.workjourney.journey.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Builds an effective read model without rewriting original work events. */
@Service
@Profile("!bootstrap")
public class EffectiveTimelineService {
    private final WorkAdjustmentRepository requests;
    public EffectiveTimelineService(WorkAdjustmentRepository requests) { this.requests=requests; }

    public Timeline project(WorkSession session,Instant observed) {
        var approved=requests.findByWorkSessionIdAndStatusOrderByProposedOccurredAtUtcAscIdAsc(
                session.getId(),AdjustmentStatus.APPROVED);
        var overlays=approved.stream().filter(request -> request.getAppliedEventId()==null).map(Approved::from).toList();
        var applied=approved.stream().filter(request -> request.getAppliedEventId()!=null)
                .collect(java.util.stream.Collectors.toMap(WorkAdjustmentRequest::getAppliedEventId,WorkAdjustmentRequest::getId));
        return build(session,overlays,applied,observed,!approved.isEmpty());
    }

    public Timeline projectIncluding(WorkSession session,WorkAdjustmentRequest candidate,Instant observed) {
        var approved=new ArrayList<>(requests.findByWorkSessionIdAndStatusAndAppliedEventIdIsNullOrderByProposedOccurredAtUtcAscIdAsc(
                session.getId(),AdjustmentStatus.APPROVED).stream().map(Approved::from).toList());
        approved.add(Approved.from(candidate));
        return build(session,approved,Map.of(),observed,true);
    }

    public static Timeline original(WorkSession session,Instant observed) { return build(session,List.of(),Map.of(),observed,false); }

    static Timeline build(WorkSession session,List<Approved> approved,Instant observed) {
        return build(session,approved,Map.of(),observed,!approved.isEmpty());
    }
    private static Timeline build(WorkSession session,List<Approved> approved,Map<UUID,UUID> applied,
                                  Instant observed,boolean adjusted) {
        Objects.requireNonNull(session); Objects.requireNonNull(observed);
        var events=new ArrayList<Event>();
        session.getEvents().forEach(event -> events.add(new Event(applied.containsKey(event.id())?"ADJUSTMENT":"ORIGINAL",
                event.id(),applied.get(event.id()),event.sequence(),event.type(),
                event.occurredAtUtc(),event.recordedTimeZoneId(),event.workMode())));
        approved.forEach(adjustment -> events.add(new Event("ADJUSTMENT",null,adjustment.id(),null,adjustment.type(),
                adjustment.occurredAt(),null,null)));
        events.sort(Comparator.comparing(Event::occurredAtUtc)
                .thenComparing(event -> event.originalSequence()==null ? Long.MAX_VALUE : event.originalSequence())
                .thenComparing(event -> event.originalEventId()==null ? event.adjustmentId() : event.originalEventId()));
        if(events.isEmpty()||events.get(0).type()!=WorkEventType.ENTRY)throw new AdjustmentConflict();
        for(int index=1;index<events.size();index++) {
            if(events.get(index).occurredAtUtc().equals(events.get(index-1).occurredAtUtc())
                    && (events.get(index).adjustmentId()!=null || events.get(index-1).adjustmentId()!=null))throw new AdjustmentConflict();
        }
        WorkState state=WorkState.WORKING;
        Duration completed=Duration.ZERO;
        Instant workingSince=events.get(0).occurredAtUtc();
        try {
            for(int index=1;index<events.size();index++) {
                var event=events.get(index);
                if(state==WorkState.WORKING)completed=completed.plus(Duration.between(workingSince,event.occurredAtUtc()));
                state=state.nextFor(event.type());
                workingSince=state==WorkState.WORKING?event.occurredAtUtc():null;
            }
        } catch(IllegalStateException invalid){throw new AdjustmentConflict();}
        if(state==WorkState.WORKING) {
            if(observed.isBefore(workingSince))throw new AdjustmentConflict();
            completed=completed.plus(Duration.between(workingSince,observed));
        }
        return new Timeline(state,completed,adjusted,List.copyOf(events));
    }

    record Approved(UUID id,WorkEventType type,Instant occurredAt) {
        static Approved from(WorkAdjustmentRequest request) {
            return new Approved(request.getId(),request.getRequestedEventType(),request.getProposedOccurredAtUtc());
        }
    }
    public record Event(String source,UUID originalEventId,UUID adjustmentId,Long originalSequence,WorkEventType type,
                        Instant occurredAtUtc,String recordedTimeZoneId,WorkMode workMode) { }
    public record Timeline(WorkState state,Duration workDuration,boolean adjusted,List<Event> events) { }
}
