package io.github.juliarezende521.workjourney.policy;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
class CountryWorkPolicyRequestReader {
    private static final int MAX_BYTES = 2048;
    private final ObjectMapper mapper;
    CountryWorkPolicyRequestReader(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }
    Command read(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > MAX_BYTES) throw new InvalidCountryWorkPolicy(413);
        var body = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (body.length > MAX_BYTES) throw new InvalidCountryWorkPolicy(413);
        try {
            var input = mapper.readValue(body, Input.class);
            return new Command(input.countryCode(), LocalDate.parse(input.effectiveFrom()),
                    input.standardWeeklyMinutes(), input.maximumDailyMinutes(), input.minimumBreakMinutes(),
                    input.overtimeToleranceMinutes());
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException invalid) {
            throw new InvalidCountryWorkPolicy();
        }
    }
    private record Input(String countryCode, String effectiveFrom, Integer standardWeeklyMinutes,
                         Integer maximumDailyMinutes, Integer minimumBreakMinutes,
                         Integer overtimeToleranceMinutes) { }
    record Command(String countryCode, LocalDate effectiveFrom, int standardWeeklyMinutes,
                   int maximumDailyMinutes, int minimumBreakMinutes, int overtimeToleranceMinutes) { }
}
