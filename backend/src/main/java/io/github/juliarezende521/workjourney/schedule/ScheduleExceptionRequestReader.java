package io.github.juliarezende521.workjourney.schedule;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
class ScheduleExceptionRequestReader {
    private static final int MAX_BYTES=2048;private final ObjectMapper mapper;
    ScheduleExceptionRequestReader(ObjectMapper mapper){this.mapper=mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);}
    Command read(HttpServletRequest request)throws IOException{
        if(request.getContentLengthLong()>MAX_BYTES)throw new InvalidSchedule(413);
        var body=request.getInputStream().readNBytes(MAX_BYTES+1);if(body.length>MAX_BYTES)throw new InvalidSchedule(413);
        try{var input=mapper.readValue(body,Input.class);return new Command(LocalDate.parse(input.workDate()),input.plannedMinutes(),
                input.toleranceMinutes(),WorkScheduleException.Category.valueOf(input.category()),input.reason());}
        catch(com.fasterxml.jackson.core.JsonProcessingException|IllegalArgumentException|NullPointerException invalid){throw new InvalidSchedule();}
    }
    private record Input(String workDate,Integer plannedMinutes,Integer toleranceMinutes,String category,String reason){}
    record Command(LocalDate workDate,int plannedMinutes,int toleranceMinutes,WorkScheduleException.Category category,String reason){}
}
