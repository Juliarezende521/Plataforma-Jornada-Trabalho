package io.github.juliarezende521.workjourney.journey;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkStateTest {
    @Test
    void acceptsExactlyTheFourOnlineTransitions() {
        var transitions = java.util.Map.of(
                "WORKING:PAUSE_START", WorkState.PAUSED,
                "WORKING:EXIT", WorkState.CLOSED,
                "PAUSED:PAUSE_END", WorkState.WORKING,
                "PAUSED:EXIT", WorkState.CLOSED);
        int accepted = 0;
        for (var state : WorkState.values()) {
            for (var event : WorkEventType.values()) {
                WorkState expected = transitions.get(state.name() + ":" + event.name());
                if (expected == null) assertThrows(IllegalStateException.class, () -> state.nextFor(event));
                else { assertEquals(expected, state.nextFor(event)); accepted++; }
            }
        }
        assertEquals(4, accepted);
        assertThrows(NullPointerException.class, () -> WorkState.WORKING.nextFor(null));
    }
}
