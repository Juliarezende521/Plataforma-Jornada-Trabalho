package io.github.juliarezende521.workjourney.journey.marking;

import io.github.juliarezende521.workjourney.journey.WorkEventType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** No client identity, clock, time zone or optimistic version is accepted by this command. */
public record MarkingCommand(WorkEventType type, UUID expectedWorkSessionId) {
    public static final String OPERATION = "ONLINE_EVENT";

    public MarkingCommand {
        Objects.requireNonNull(type, "Event type is required");
        if ((type == WorkEventType.ENTRY) != (expectedWorkSessionId == null)) {
            throw new IllegalArgumentException("ENTRY has no target; other events require a session target");
        }
    }

    public String payloadHash() {
        String canonical = "v1|" + OPERATION + "|" + type.name() + "|"
                + (expectedWorkSessionId == null ? "-" : expectedWorkSessionId.toString());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("Required SHA-256 algorithm unavailable", unavailable);
        }
    }
}
