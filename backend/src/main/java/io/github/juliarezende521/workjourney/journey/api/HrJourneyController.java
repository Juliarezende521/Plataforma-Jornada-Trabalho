package io.github.juliarezende521.workjourney.journey.api;

import io.github.juliarezende521.workjourney.journey.query.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/hr/employees")
public class HrJourneyController {
    private final HrJourneyQueries queries;
    private final MarkingRequestReader reader;
    public HrJourneyController(HrJourneyQueries queries, MarkingRequestReader reader) { this.queries = queries; this.reader = reader; }

    @GetMapping
    public HrJourneyQueries.Directory employees(HttpServletRequest request) {
        var page = page(request);
        return queries.employees(page[0], page[1]);
    }

    @GetMapping("/overview")
    public HrJourneyQueries.Overview overview(HttpServletRequest request) {
        var page = page(request);
        return queries.overview(page[0], page[1]);
    }

    private int[] page(HttpServletRequest request) {
        var parameters = request.getParameterMap();
        if (!Set.of("page", "size").containsAll(parameters.keySet())
                || parameters.values().stream().anyMatch(values -> values.length != 1)) throw new JourneyQueries.InvalidFilter();
        try {
            return new int[]{Integer.parseInt(request.getParameter("page") == null ? "0" : request.getParameter("page")),
                    Integer.parseInt(request.getParameter("size") == null ? "20" : request.getParameter("size"))};
        } catch (NumberFormatException invalid) { throw new JourneyQueries.InvalidFilter(); }
    }

    @GetMapping("/{employeeId}/work-sessions")
    public JourneyQueries.History history(@PathVariable String employeeId, HttpServletRequest request) {
        var filter = HistoryRequestFilter.read(request);
        return queries.history(reader.uuid(employeeId), filter.from(), filter.to(), filter.page(), filter.size());
    }

    @GetMapping("/{employeeId}/work-sessions/{id}")
    public JourneyQueries.Detail detail(@PathVariable String employeeId, @PathVariable String id) {
        return queries.detail(reader.uuid(employeeId), reader.uuid(id));
    }

    @GetMapping("/{employeeId}/work-sessions/summary")
    public JourneyQueries.Summary summary(@PathVariable String employeeId,HttpServletRequest request) {
        var filter=SummaryRequestFilter.read(request);
        return queries.summary(reader.uuid(employeeId),filter.from(),filter.to());
    }
}
