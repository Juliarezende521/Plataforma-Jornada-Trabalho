package io.github.juliarezende521.workjourney.journey.marking;

import io.github.juliarezende521.workjourney.journey.WorkEventType;
import io.github.juliarezende521.workjourney.journey.WorkMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import io.github.juliarezende521.workjourney.time.ReferenceTimeZones;

/** Cliente informa apenas tipo, alvo, fuso e modalidade; identidade, instante e versão são do servidor. */
public record MarkingCommand(WorkEventType type, UUID expectedWorkSessionId, String timeZoneId, WorkMode workMode) {
    public MarkingCommand(WorkEventType type, UUID expectedWorkSessionId) {
        this(type, expectedWorkSessionId, null, null);
    }
    public MarkingCommand(WorkEventType type, UUID expectedWorkSessionId, String timeZoneId) {
        this(type, expectedWorkSessionId, timeZoneId, null);
    }
    public static final String OPERATION = "ONLINE_EVENT";

    public MarkingCommand {
        Objects.requireNonNull(type, "Event type is required");
        if (timeZoneId != null) ReferenceTimeZones.requireValid(timeZoneId);
        if ((type == WorkEventType.ENTRY) != (expectedWorkSessionId == null)) {
            throw new IllegalArgumentException("ENTRY has no target; other events require a session target");
        }
    }

    public String payloadHash() {
        String canonical = "v1|" + OPERATION + "|" + type.name() + "|"
                + (expectedWorkSessionId == null ? "-" : expectedWorkSessionId.toString());
        if (timeZoneId != null) canonical += "|timeZoneId=" + timeZoneId;
        if (workMode != null) canonical += "|workMode=" + workMode.name();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("Required SHA-256 algorithm unavailable", unavailable);
        }
    }
}
