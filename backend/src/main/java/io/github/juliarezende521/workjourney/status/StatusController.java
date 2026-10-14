package io.github.juliarezende521.workjourney.status;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {
    private final boolean journeyAvailable;

    public StatusController(Environment environment) {
        journeyAvailable = !environment.acceptsProfiles(Profiles.of("bootstrap"));
    }

    @GetMapping("/api/status")
    public StatusResponse status() {
        return new StatusResponse("UP", journeyAvailable ? "JOURNEY_ONLINE" : "FOUNDATION", journeyAvailable);
    }

    public record StatusResponse(String status, String stage, boolean journeyAvailable) { }
}
