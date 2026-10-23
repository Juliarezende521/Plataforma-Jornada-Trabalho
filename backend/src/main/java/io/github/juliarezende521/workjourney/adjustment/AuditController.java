package io.github.juliarezende521.workjourney.adjustment;

import io.github.juliarezende521.workjourney.journey.query.JourneyQueries.InvalidFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!bootstrap")
public class AuditController {
    private final AuditQueryService service;
    AuditController(AuditQueryService service) { this.service = service; }

    @GetMapping("/api/hr/audit-entries")
    AuditQueryService.Page history(HttpServletRequest request) {
        var parameters = request.getParameterMap();
        if (!Set.of("page", "size").containsAll(parameters.keySet())
                || parameters.values().stream().anyMatch(values -> values.length != 1)) throw new InvalidFilter();
        return service.history(integer(request, "page", 0), integer(request, "size", 20));
    }

    private int integer(HttpServletRequest request, String name, int fallback) {
        try { return request.getParameter(name) == null ? fallback : Integer.parseInt(request.getParameter(name)); }
        catch (NumberFormatException invalid) { throw new InvalidFilter(); }
    }
}
