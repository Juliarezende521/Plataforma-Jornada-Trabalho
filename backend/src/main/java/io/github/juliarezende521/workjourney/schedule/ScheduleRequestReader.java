package io.github.juliarezende521.workjourney.schedule;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class ScheduleRequestReader {
    private static final int MAX_BYTES=2048;private final ObjectMapper mapper;
    ScheduleRequestReader(ObjectMapper mapper){this.mapper=mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);}
    Command read(HttpServletRequest request)throws IOException{
        if(request.getContentLengthLong()>MAX_BYTES)throw new InvalidSchedule(413);
        var body=request.getInputStream().readNBytes(MAX_BYTES+1);if(body.length>MAX_BYTES)throw new InvalidSchedule(413);
        try{var input=mapper.readValue(body,Input.class);return new Command(LocalDate.parse(input.effectiveFrom()),
                List.of(input.mondayMinutes(),input.tuesdayMinutes(),input.wednesdayMinutes(),input.thursdayMinutes(),
                        input.fridayMinutes(),input.saturdayMinutes(),input.sundayMinutes()));}
        catch(com.fasterxml.jackson.core.JsonProcessingException|IllegalArgumentException|NullPointerException invalid){throw new InvalidSchedule();}
    }
    private record Input(String effectiveFrom,Integer mondayMinutes,Integer tuesdayMinutes,Integer wednesdayMinutes,
                         Integer thursdayMinutes,Integer fridayMinutes,Integer saturdayMinutes,Integer sundayMinutes){}
    record Command(LocalDate effectiveFrom,List<Integer> minutes){}
}
