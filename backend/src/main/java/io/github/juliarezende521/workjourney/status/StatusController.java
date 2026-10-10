package io.github.juliarezende521.workjourney.status;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {
    @GetMapping("/api/status")
    public StatusResponse status() {
        return new StatusResponse("UP", "FOUNDATION", false);
    }

    public record StatusResponse(String status, String stage, boolean journeyAvailable) { }
}
