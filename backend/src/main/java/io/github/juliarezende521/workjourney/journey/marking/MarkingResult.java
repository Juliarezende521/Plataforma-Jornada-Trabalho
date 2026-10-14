package io.github.juliarezende521.workjourney.journey.marking;

import io.github.juliarezende521.workjourney.journey.WorkEventType;
import java.time.Instant;
import java.util.UUID;

/** Original outcome snapshot; replay never adds fields or changes timestamps/status. */
public record MarkingResult(UUID requestKey, WorkEventType type, int httpStatus, Code code,
                            UUID workSessionId, UUID eventId, Instant occurredAtUtc, Instant confirmedAtUtc) {
    public enum Code {
        RECORDED, ALREADY_OPEN, NO_OPEN_SESSION, TARGET_CHANGED,
        INVALID_TRANSITION, CLOCK_REGRESSION, KEY_REUSED
    }

    static MarkingResult conflict(UUID key, MarkingCommand command, Code code, Instant now) {
        return new MarkingResult(key, command.type(), 409, code, null, null, null, now);
    }
}
