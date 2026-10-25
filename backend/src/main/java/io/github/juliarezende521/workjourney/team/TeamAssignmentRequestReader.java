package io.github.juliarezende521.workjourney.team;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import io.github.juliarezende521.workjourney.journey.api.MarkingRequestReader;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class TeamAssignmentRequestReader {
    private static final int MAX_BYTES = 1024;
    private final ObjectMapper mapper;
    private final MarkingRequestReader uuids;

    TeamAssignmentRequestReader(ObjectMapper mapper, MarkingRequestReader uuids) {
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.uuids = uuids;
    }

    Command read(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > MAX_BYTES) throw new InvalidTeamAssignment(413, "TOO_LARGE");
        var body = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (body.length > MAX_BYTES) throw new InvalidTeamAssignment(413, "TOO_LARGE");
        try {
            var input = mapper.readValue(body, Input.class);
            return new Command(uuids.uuid(input.managerEmployeeId()), LocalDate.parse(input.effectiveFrom()));
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException invalid) {
            throw new InvalidTeamAssignment();
        }
    }

    private record Input(String managerEmployeeId, String effectiveFrom) { }
    record Command(UUID managerEmployeeId, LocalDate effectiveFrom) { }
}
