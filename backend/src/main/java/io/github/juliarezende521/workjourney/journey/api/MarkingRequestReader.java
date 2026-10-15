package io.github.juliarezende521.workjourney.journey.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.juliarezende521.workjourney.journey.WorkEventType;
import io.github.juliarezende521.workjourney.journey.WorkMode;
import io.github.juliarezende521.workjourney.journey.marking.MarkingCommand;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Reads only this small command contract, after authentication and CSRF filters. */
@Component
public final class MarkingRequestReader {
    static final int MAX_BODY_BYTES = 1024;
    private static final Pattern UUID_FORMAT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private final ObjectMapper strictMapper;

    public MarkingRequestReader(ObjectMapper mapper) {
        strictMapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    public UUID key(HttpServletRequest request) {
        var values = Collections.list(request.getHeaders("Idempotency-Key"));
        if (values.size() != 1) throw new InvalidJourneyRequest(400);
        return uuid(values.get(0));
    }

    public MarkingCommand command(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) throw new InvalidJourneyRequest(413);
        // Bound actual bytes too: Content-Length may be absent (chunked transfer).
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) throw new InvalidJourneyRequest(413);
        try {
            var input = strictMapper.readValue(body, Input.class);
            if (input == null || input.type() == null) throw new InvalidJourneyRequest(400);
            return new MarkingCommand(WorkEventType.valueOf(input.type()),
                    input.expectedWorkSessionId() == null ? null : uuid(input.expectedWorkSessionId()), input.timeZoneId(),
                    input.workMode() == null ? null : WorkMode.valueOf(input.workMode()));
        } catch (JsonProcessingException | IllegalArgumentException invalid) {
            throw new InvalidJourneyRequest(400);
        }
    }

    public UUID uuid(String value) {
        if (value == null || !UUID_FORMAT.matcher(value).matches()) throw new InvalidJourneyRequest(400);
        return UUID.fromString(value);
    }

    private record Input(String type, String expectedWorkSessionId, String timeZoneId, String workMode) { }
}
