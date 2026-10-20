package io.github.juliarezende521.workjourney.adjustment;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!bootstrap")
public class WorkAdjustmentController {
    private final WorkAdjustmentService service;
    private final AdjustmentRequestReader reader;
    public WorkAdjustmentController(WorkAdjustmentService service,AdjustmentRequestReader reader) { this.service=service; this.reader=reader; }

    @PostMapping(value="/api/me/work-adjustments",consumes="application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkAdjustmentService.View request(HttpServletRequest request) throws IOException {
        var command=reader.request(request);
        return service.request(command.workSessionId(),command.eventType(),command.proposedAt(),command.reason());
    }

    @GetMapping("/api/me/work-adjustments")
    public WorkAdjustmentService.Page mine(@RequestParam(required=false) UUID workSessionId,
                                           @RequestParam(defaultValue="0") int page,
                                           @RequestParam(defaultValue="20") int size) {
        return service.mine(workSessionId,page,size);
    }

    @GetMapping("/api/hr/work-adjustments")
    public WorkAdjustmentService.Page pending(@RequestParam(defaultValue="0") int page,
                                              @RequestParam(defaultValue="20") int size) {
        return service.pending(page,size);
    }

    @PostMapping(value="/api/hr/work-adjustments/{id}/decision",consumes="application/json")
    public WorkAdjustmentService.View decide(@PathVariable UUID id,HttpServletRequest request) throws IOException {
        var command=reader.decision(request);
        return service.decide(id,command.decision(),command.note());
    }
}
