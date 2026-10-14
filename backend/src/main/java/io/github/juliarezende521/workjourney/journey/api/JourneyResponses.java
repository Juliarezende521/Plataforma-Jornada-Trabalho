package io.github.juliarezende521.workjourney.journey.api;

import io.github.juliarezende521.workjourney.journey.*;
import io.github.juliarezende521.workjourney.journey.marking.CurrentWorkSessionService;
import io.github.juliarezende521.workjourney.journey.marking.MarkingResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Explicit wire contracts; never serialize JPA entities or the domain aggregate. */
public final class JourneyResponses {
    private JourneyResponses() { }

    public record Receipt(UUID requestKey, WorkEventType type, int httpStatus, MarkingResult.Code code,
                          UUID workSessionId, UUID eventId, Instant occurredAtUtc, Instant confirmedAtUtc) {
        static Receipt from(MarkingResult result) {
            return new Receipt(result.requestKey(), result.type(), result.httpStatus(), result.code(),
                    result.workSessionId(), result.eventId(), result.occurredAtUtc(), result.confirmedAtUtc());
        }
    }

    public record Current(CurrentSession session, Instant observedAtUtc, List<WorkEventType> allowedActions) {
        static Current from(CurrentWorkSessionService.Snapshot snapshot) {
            var session = snapshot.session();
            return new Current(session == null ? null : CurrentSession.from(session, snapshot.observedAtUtc()),
                    snapshot.observedAtUtc(), session == null ? List.of(WorkEventType.ENTRY) : switch (session.getState()) {
                        case WORKING -> List.of(WorkEventType.PAUSE_START, WorkEventType.EXIT);
                        case PAUSED -> List.of(WorkEventType.PAUSE_END, WorkEventType.EXIT);
                        case CLOSED -> List.of(WorkEventType.ENTRY);
                    });
        }
    }

    public record CurrentSession(UUID id, WorkState state, LocalDate workDate, String referenceTimeZoneId,
                                 Instant openedAtUtc, LastEvent lastEvent, String provisionalWorkDuration) {
        static CurrentSession from(WorkSession session, Instant observed) {
            var events = session.getEvents();
            var last = events.get(events.size() - 1);
            return new CurrentSession(session.getId(), session.getState(), session.getWorkDate(),
                    session.getReferenceTimeZoneId(), session.getOpenedAtUtc(),
                    new LastEvent(last.id(), last.type(), last.occurredAtUtc(), last.recordedTimeZoneId()), session.durationAt(observed).toString());
        }
    }

    public record LastEvent(UUID id, WorkEventType type, Instant occurredAtUtc, String recordedTimeZoneId) { }
}
