package io.github.juliarezende521.workjourney.schedule;

import io.github.juliarezende521.workjourney.journey.api.MarkingRequestReader;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!bootstrap")
public class WorkScheduleController {
    private final WorkScheduleService service;private final ScheduleRequestReader reader;private final MarkingRequestReader uuids;
    WorkScheduleController(WorkScheduleService service,ScheduleRequestReader reader,MarkingRequestReader uuids){this.service=service;this.reader=reader;this.uuids=uuids;}
    @GetMapping("/api/me/work-schedule") public WorkScheduleService.View mine(HttpServletRequest request){return service.mine(on(request));}
    @GetMapping("/api/hr/employees/{employeeId}/work-schedule") public WorkScheduleService.View forHr(@PathVariable String employeeId,HttpServletRequest request){return service.forHr(uuids.uuid(employeeId),on(request));}
    @GetMapping("/api/hr/employees/{employeeId}/work-schedules") public WorkScheduleService.Page history(
            @PathVariable String employeeId,HttpServletRequest request){
        var parameters=request.getParameterMap();
        if(!Set.of("page","size").containsAll(parameters.keySet())||parameters.values().stream().anyMatch(values->values.length!=1))throw new InvalidSchedule();
        return service.historyForHr(uuids.uuid(employeeId),integer(request,"page",0),integer(request,"size",20));
    }
    @PostMapping(value="/api/hr/employees/{employeeId}/work-schedules",consumes="application/json") @ResponseStatus(HttpStatus.CREATED)
    public WorkScheduleService.View create(@PathVariable String employeeId,HttpServletRequest request)throws IOException{
        var command=reader.read(request);return service.create(uuids.uuid(employeeId),command.effectiveFrom(),command.minutes(),command.toleranceMinutes());
    }
    private LocalDate on(HttpServletRequest request){var parameters=request.getParameterMap();
        if(!Set.of("on").containsAll(parameters.keySet())||parameters.values().stream().anyMatch(values->values.length!=1))throw new InvalidSchedule();
        try{return request.getParameter("on")==null?null:LocalDate.parse(request.getParameter("on"));}
        catch(RuntimeException invalid){throw new InvalidSchedule();}
    }
    private int integer(HttpServletRequest request,String name,int fallback){
        try{return request.getParameter(name)==null?fallback:Integer.parseInt(request.getParameter(name));}
        catch(NumberFormatException invalid){throw new InvalidSchedule();}
    }
}
