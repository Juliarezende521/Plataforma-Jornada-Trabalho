package io.github.juliarezende521.workjourney.team;

import io.github.juliarezende521.workjourney.journey.api.MarkingRequestReader;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/hr/employees/{employeeId}")
public class TeamAssignmentController {
    private final TeamAssignmentService service;
    private final TeamAssignmentRequestReader reader;
    private final MarkingRequestReader uuids;

    TeamAssignmentController(TeamAssignmentService service, TeamAssignmentRequestReader reader,
                             MarkingRequestReader uuids) {
        this.service = service;
        this.reader = reader;
        this.uuids = uuids;
    }

    @GetMapping("/manager-assignment")
    TeamAssignmentService.View current(@PathVariable String employeeId, HttpServletRequest request) {
        exact(request, Set.of("on"));
        return service.current(uuids.uuid(employeeId), date(request.getParameter("on")));
    }

    @GetMapping("/manager-assignments")
    TeamAssignmentService.Page history(@PathVariable String employeeId, HttpServletRequest request) {
        exact(request, Set.of("page", "size"));
        return service.history(uuids.uuid(employeeId), integer(request, "page", 0), integer(request, "size", 20));
    }

    @PostMapping(value = "/manager-assignments", consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    TeamAssignmentService.View create(@PathVariable String employeeId, HttpServletRequest request) throws IOException {
        var command = reader.read(request);
        return service.create(uuids.uuid(employeeId), command.managerEmployeeId(), command.effectiveFrom());
    }

    private static LocalDate date(String value) {
        if (value == null) return null;
        try { return LocalDate.parse(value); }
        catch (RuntimeException invalid) { throw new InvalidTeamAssignment(); }
    }

    private static void exact(HttpServletRequest request, Set<String> allowed) {
        var parameters = request.getParameterMap();
        if (!allowed.containsAll(parameters.keySet())
                || parameters.values().stream().anyMatch(values -> values.length != 1)) {
            throw new InvalidTeamAssignment();
        }
    }

    private static int integer(HttpServletRequest request, String name, int fallback) {
        try { return request.getParameter(name) == null ? fallback : Integer.parseInt(request.getParameter(name)); }
        catch (NumberFormatException invalid) { throw new InvalidTeamAssignment(); }
    }
}
