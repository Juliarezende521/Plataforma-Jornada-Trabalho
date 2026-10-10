package io.github.juliarezende521.workjourney.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("bootstrap")
class StatusSecurityTest {
    @Autowired MockMvc mvc;

    @Test
    void statusIsPublicButDoesNotClaimJourneyIsAvailable() throws Exception {
        mvc.perform(get("/api/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stage").value("FOUNDATION"))
            .andExpect(jsonPath("$.journeyAvailable").value(false));
    }

    @Test
    void businessRoutesRequireAuthenticationWithoutRedirect() throws Exception {
        mvc.perform(get("/api/me/work-sessions"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void mutationWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/me/work-events").with(user("test-user")))
            .andExpect(status().isForbidden());
    }
}
