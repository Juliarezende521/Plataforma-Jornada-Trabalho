package io.github.juliarezende521.workjourney.journey.api;

import io.github.juliarezende521.workjourney.journey.query.JourneyQueries;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/me/work-sessions")
public class JourneyQueryController {
    private final JourneyQueries queries;
    private final MarkingRequestReader reader;
    public JourneyQueryController(JourneyQueries queries, MarkingRequestReader reader) { this.queries=queries; this.reader=reader; }
    @GetMapping
    public JourneyQueries.History history(HttpServletRequest request) {
        var parameters=request.getParameterMap();
        if (!Set.of("from","to","page","size").containsAll(parameters.keySet())
                || parameters.values().stream().anyMatch(values -> values.length!=1)) throw new JourneyQueries.InvalidFilter();
        LocalDate from, to;
        int page, size;
        try {
            from=LocalDate.parse(request.getParameter("from"));
            to=LocalDate.parse(request.getParameter("to"));
            page=Integer.parseInt(parameters.containsKey("page") ? request.getParameter("page") : "0");
            size=Integer.parseInt(parameters.containsKey("size") ? request.getParameter("size") : "20");
        } catch (java.time.DateTimeException | NumberFormatException | NullPointerException error) { throw new JourneyQueries.InvalidFilter(); }
        return queries.history(from, to, page, size);
    }
    @GetMapping("/{id}")
    public JourneyQueries.Detail detail(@PathVariable String id) { return queries.detail(reader.uuid(id)); }
}
