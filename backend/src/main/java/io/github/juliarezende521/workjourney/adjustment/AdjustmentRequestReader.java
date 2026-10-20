package io.github.juliarezende521.workjourney.adjustment;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.juliarezende521.workjourney.journey.WorkEventType;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Small strict JSON boundary: duplicate/unknown fields and oversized bodies are rejected. */
@Component
public class AdjustmentRequestReader {
    private static final int MAX_BYTES=4096;
    private final ObjectMapper mapper;
    public AdjustmentRequestReader(ObjectMapper mapper) {
        this.mapper=mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }
    public RequestCommand request(HttpServletRequest servletRequest) throws IOException {
        try {
            var input=read(servletRequest,RequestInput.class);
            return new RequestCommand(UUID.fromString(input.workSessionId()),WorkEventType.valueOf(input.requestedEventType()),
                    Instant.parse(input.proposedOccurredAtUtc()),input.reason());
        } catch (IllegalArgumentException | NullPointerException invalid) { throw new InvalidAdjustment(); }
    }
    public DecisionCommand decision(HttpServletRequest servletRequest) throws IOException {
        try {
            var input=read(servletRequest,DecisionInput.class);
            return new DecisionCommand(AdjustmentStatus.valueOf(input.decision()),input.note());
        } catch (IllegalArgumentException | NullPointerException invalid) { throw new InvalidAdjustment(); }
    }
    private <T> T read(HttpServletRequest request,Class<T> type) throws IOException {
        if(request.getContentLengthLong()>MAX_BYTES)throw new InvalidAdjustment(413);
        byte[] body=request.getInputStream().readNBytes(MAX_BYTES+1);
        if(body.length>MAX_BYTES)throw new InvalidAdjustment(413);
        try { return mapper.readValue(body,type); }
        catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new InvalidAdjustment(); }
    }
    private record RequestInput(String workSessionId,String requestedEventType,String proposedOccurredAtUtc,String reason) { }
    private record DecisionInput(String decision,String note) { }
    public record RequestCommand(UUID workSessionId,WorkEventType eventType,Instant proposedAt,String reason) { }
    public record DecisionCommand(AdjustmentStatus decision,String note) { }
}
