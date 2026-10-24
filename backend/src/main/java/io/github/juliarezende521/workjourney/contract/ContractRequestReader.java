package io.github.juliarezende521.workjourney.contract;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
class ContractRequestReader {
    private static final int MAX_BYTES = 2048;
    private final ObjectMapper mapper;

    ContractRequestReader(ObjectMapper mapper) {
        this.mapper = mapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    Command read(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > MAX_BYTES) {
            throw new InvalidContract(413);
        }
        var body = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (body.length > MAX_BYTES) {
            throw new InvalidContract(413);
        }
        try {
            var input = mapper.readValue(body, Input.class);
            return new Command(LocalDate.parse(input.effectiveFrom()), input.countryCode(),
                    EmploymentContract.Type.valueOf(input.employmentType()), input.weeklyMinutes());
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException invalid) {
            throw new InvalidContract();
        }
    }

    private record Input(String effectiveFrom, String countryCode, String employmentType, Integer weeklyMinutes) { }

    record Command(LocalDate effectiveFrom, String countryCode,
                   EmploymentContract.Type type, int weeklyMinutes) { }
}
