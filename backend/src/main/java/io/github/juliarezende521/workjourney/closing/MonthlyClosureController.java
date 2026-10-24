package io.github.juliarezende521.workjourney.closing;

import io.github.juliarezende521.workjourney.journey.api.MarkingRequestReader;
import jakarta.servlet.http.HttpServletRequest;
import java.time.YearMonth;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/hr/employees/{employeeId}/monthly-closures")
public class MonthlyClosureController {
    private final MonthlyClosureService service;
    private final MarkingRequestReader uuids;

    MonthlyClosureController(MonthlyClosureService service, MarkingRequestReader uuids) {
        this.service = service;
        this.uuids = uuids;
    }

    @GetMapping("/{competence}")
    MonthlyClosureService.View find(@PathVariable String employeeId, @PathVariable String competence) {
        return service.find(uuids.uuid(employeeId), competence(competence));
    }

    @GetMapping
    MonthlyClosureService.Page history(@PathVariable String employeeId, HttpServletRequest request) {
        exact(request, Set.of("page", "size"));
        return service.history(uuids.uuid(employeeId), integer(request, "page", 0), integer(request, "size", 20));
    }

    @PostMapping("/{competence}")
    @ResponseStatus(HttpStatus.CREATED)
    MonthlyClosureService.View close(@PathVariable String employeeId, @PathVariable String competence) {
        return service.close(uuids.uuid(employeeId), competence(competence));
    }

    private static YearMonth competence(String value) {
        try {
            return YearMonth.parse(value);
        } catch (RuntimeException invalid) {
            throw new InvalidMonthlyClosure();
        }
    }

    private static void exact(HttpServletRequest request, Set<String> allowed) {
        var parameters = request.getParameterMap();
        if (!allowed.containsAll(parameters.keySet())
                || parameters.values().stream().anyMatch(values -> values.length != 1)) {
            throw new InvalidMonthlyClosure();
        }
    }

    private static int integer(HttpServletRequest request, String name, int fallback) {
        try {
            var value = request.getParameter(name);
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            throw new InvalidMonthlyClosure();
        }
    }
}
