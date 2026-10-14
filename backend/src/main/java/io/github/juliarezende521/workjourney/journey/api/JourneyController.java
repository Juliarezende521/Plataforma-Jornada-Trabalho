package io.github.juliarezende521.workjourney.journey.api;

import io.github.juliarezende521.workjourney.journey.marking.CurrentWorkSessionService;
import io.github.juliarezende521.workjourney.journey.marking.OnlineMarkingService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/me")
public class JourneyController {
    private final OnlineMarkingService markings;
    private final CurrentWorkSessionService current;
    private final MarkingRequestReader requests;

    public JourneyController(OnlineMarkingService markings, CurrentWorkSessionService current, MarkingRequestReader requests) {
        this.markings = markings; this.current = current; this.requests = requests;
    }

    @PostMapping(value = "/work-events", consumes = "application/json")
    public ResponseEntity<JourneyResponses.Receipt> mark(HttpServletRequest request) throws IOException {
        var key = requests.key(request);
        var command = requests.command(request);
        var result = markings.execute(key, command);
        return ResponseEntity.status(result.httpStatus()).body(JourneyResponses.Receipt.from(result));
    }

    @GetMapping("/work-event-requests/{key}")
    public ResponseEntity<JourneyResponses.Receipt> receipt(@PathVariable String key) {
        return markings.lookup(requests.uuid(key)).map(JourneyResponses.Receipt::from)
                .map(ResponseEntity::ok).orElseThrow(() -> new ReceiptNotFound());
    }

    @GetMapping("/work-sessions/current")
    public JourneyResponses.Current current() { return JourneyResponses.Current.from(current.current()); }

    static final class ReceiptNotFound extends RuntimeException { }
}
