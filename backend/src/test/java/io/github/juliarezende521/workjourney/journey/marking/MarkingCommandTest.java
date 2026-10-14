package io.github.juliarezende521.workjourney.journey.marking;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.juliarezende521.workjourney.journey.WorkEventType.*;

class MarkingCommandTest {
    @Test
    void entryHasNoTargetAndOtherCommandsRequireOne() {
        assertThrows(IllegalArgumentException.class, () -> new MarkingCommand(ENTRY, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> new MarkingCommand(EXIT, null));
        assertThrows(NullPointerException.class, () -> new MarkingCommand(null, null));
    }

    @Test
    void canonicalHashIsStableAndDistinguishesTypeAndTarget() {
        UUID target = UUID.randomUUID();
        var first = new MarkingCommand(EXIT, target);
        assertEquals(first.payloadHash(), new MarkingCommand(EXIT, target).payloadHash());
        assertTrue(first.payloadHash().matches("[0-9a-f]{64}"));
        assertNotEquals(first.payloadHash(), new MarkingCommand(PAUSE_START, target).payloadHash());
        assertNotEquals(first.payloadHash(), new MarkingCommand(EXIT, UUID.randomUUID()).payloadHash());
        assertNotEquals(first.payloadHash(), new MarkingCommand(ENTRY, null).payloadHash());
    }
    @Test
    void absentZoneKeepsLegacyHashAndSuppliedZoneIsPartOfPayload() {
        assertEquals("c6b369affe277a01fba2e9652eda3376c53611cd00ea480af1111028872e800e",
                new MarkingCommand(ENTRY, null).payloadHash());
        var lisbon = new MarkingCommand(ENTRY, null, "Europe/Lisbon");
        assertEquals(lisbon.payloadHash(), new MarkingCommand(ENTRY, null, "Europe/Lisbon").payloadHash());
        assertNotEquals(new MarkingCommand(ENTRY, null).payloadHash(), lisbon.payloadHash());
        assertNotEquals(new MarkingCommand(ENTRY, null, "America/Sao_Paulo").payloadHash(), lisbon.payloadHash());
        assertThrows(IllegalArgumentException.class, () -> new MarkingCommand(ENTRY, null, "Invalid/Zone"));
    }
}
