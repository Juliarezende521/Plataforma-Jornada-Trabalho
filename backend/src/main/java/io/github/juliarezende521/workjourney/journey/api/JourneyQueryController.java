package io.github.juliarezende521.workjourney.journey.api;

import io.github.juliarezende521.workjourney.journey.query.JourneyQueries;
import jakarta.servlet.http.HttpServletRequest;
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
        var filter = HistoryRequestFilter.read(request);
        return queries.history(filter.from(), filter.to(), filter.page(), filter.size());
    }

    @GetMapping("/{id}")
    public JourneyQueries.Detail detail(@PathVariable String id) { return queries.detail(reader.uuid(id)); }
}
