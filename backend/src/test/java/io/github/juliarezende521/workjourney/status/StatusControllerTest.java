package io.github.juliarezende521.workjourney.status;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class StatusControllerTest {
    @Test void onlineFeatureIsAnnouncedOnlyOutsideDiagnosticBootstrap() {
        var environment = new MockEnvironment();
        assertTrue(new StatusController(environment).status().journeyAvailable());
        assertEquals("JOURNEY_ONLINE", new StatusController(environment).status().stage());
        environment.setActiveProfiles("bootstrap");
        assertFalse(new StatusController(environment).status().journeyAvailable());
        assertEquals("FOUNDATION", new StatusController(environment).status().stage());
    }
}
