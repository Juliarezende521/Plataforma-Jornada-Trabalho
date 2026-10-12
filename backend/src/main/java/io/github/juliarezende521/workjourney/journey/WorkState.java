package io.github.juliarezende521.workjourney.journey;

import java.util.Objects;

public enum WorkState {
    WORKING, PAUSED, CLOSED;

    /** Online transitions only; manual revision approval is a separate application flow. */
    public WorkState nextFor(WorkEventType event) {
        Objects.requireNonNull(event, "Event type is required");
        return switch (this) {
            case WORKING -> switch (event) {
                case PAUSE_START -> PAUSED;
                case EXIT -> CLOSED;
                default -> throw invalidTransition();
            };
            case PAUSED -> switch (event) {
                case PAUSE_END -> WORKING;
                case EXIT -> CLOSED;
                default -> throw invalidTransition();
            };
            case CLOSED -> throw invalidTransition();
        };
    }

    private static IllegalStateException invalidTransition() {
        return new IllegalStateException("Event is incompatible with session state");
    }
}
