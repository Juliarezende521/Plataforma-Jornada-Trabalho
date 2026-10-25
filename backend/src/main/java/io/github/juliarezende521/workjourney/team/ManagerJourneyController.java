package io.github.juliarezende521.workjourney.team;

import io.github.juliarezende521.workjourney.journey.api.MarkingRequestReader;
import io.github.juliarezende521.workjourney.journey.query.JourneyQueries;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/manager")
public class ManagerJourneyController {
    private final ManagerJourneyQueries queries;
    private final MarkingRequestReader uuids;

    ManagerJourneyController(ManagerJourneyQueries queries, MarkingRequestReader uuids) {
        this.queries = queries;
        this.uuids = uuids;
    }

    @GetMapping("/team")
    ManagerJourneyQueries.Directory team(HttpServletRequest request) {
        exact(request, Set.of("page", "size"));
        return queries.team(integer(request, "page", 0), integer(request, "size", 20));
    }

    @GetMapping("/employees/{employeeId}/work-sessions")
    JourneyQueries.History history(@PathVariable String employeeId, HttpServletRequest request) {
        exact(request, Set.of("from", "to", "page", "size"));
        return queries.history(uuids.uuid(employeeId), date(request, "from"), date(request, "to"),
                integer(request, "page", 0), integer(request, "size", 20));
    }

    @GetMapping("/employees/{employeeId}/work-sessions/{sessionId}")
    JourneyQueries.Detail detail(@PathVariable String employeeId, @PathVariable String sessionId) {
        return queries.detail(uuids.uuid(employeeId), uuids.uuid(sessionId));
    }

    @GetMapping("/employees/{employeeId}/work-sessions/summary")
    JourneyQueries.Summary summary(@PathVariable String employeeId, HttpServletRequest request) {
        exact(request, Set.of("from", "to"));
        return queries.summary(uuids.uuid(employeeId), date(request, "from"), date(request, "to"));
    }

    private static LocalDate date(HttpServletRequest request, String name) {
        try { return LocalDate.parse(request.getParameter(name)); }
        catch (RuntimeException invalid) { throw new JourneyQueries.InvalidFilter(); }
    }
    private static int integer(HttpServletRequest request, String name, int fallback) {
        try { return request.getParameter(name) == null ? fallback : Integer.parseInt(request.getParameter(name)); }
        catch (NumberFormatException invalid) { throw new JourneyQueries.InvalidFilter(); }
    }
    private static void exact(HttpServletRequest request, Set<String> allowed) {
        var parameters = request.getParameterMap();
        if (!allowed.containsAll(parameters.keySet())
                || parameters.values().stream().anyMatch(values -> values.length != 1)) {
            throw new JourneyQueries.InvalidFilter();
        }
    }
}
