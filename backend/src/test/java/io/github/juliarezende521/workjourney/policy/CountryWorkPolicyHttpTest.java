package io.github.juliarezende521.workjourney.policy;

import io.github.juliarezende521.workjourney.auth.AccountPrincipal;
import io.github.juliarezende521.workjourney.config.SecurityConfiguration;
import io.github.juliarezende521.workjourney.http.ProblemResponseWriter;
import io.github.juliarezende521.workjourney.journey.api.JourneyExceptionHandler;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CountryWorkPolicyController.class)
@Import({SecurityConfiguration.class, ProblemResponseWriter.class, JourneyExceptionHandler.class,
        CountryWorkPolicyRequestReader.class})
class CountryWorkPolicyHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean CountryWorkPolicyService service;
    @MockitoBean UserDetailsService accounts;
    private AccountPrincipal principal(boolean hr) {
        return new AccountPrincipal(new AccountPrincipal.Identity(UUID.randomUUID(), UUID.randomUUID(),
                "person@example.invalid", "Pessoa", "UTC", hr), "unused", true);
    }
    @Test void onlyHrWithCsrfCreatesStrictPolicy() throws Exception {
        var view = new CountryWorkPolicyService.View(UUID.randomUUID(), "BR", LocalDate.parse("2026-11-01"),
                2400, 600, 60, 10, Instant.EPOCH);
        when(service.create(any())).thenReturn(view);
        var body = """
                {"countryCode":"BR","effectiveFrom":"2026-11-01","standardWeeklyMinutes":2400,
                 "maximumDailyMinutes":600,"minimumBreakMinutes":60,"overtimeToleranceMinutes":10}
                """;
        mvc.perform(post("/api/hr/work-policies").with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/hr/work-policies").with(user(principal(false))).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/hr/work-policies").with(user(principal(true))).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.countryCode").value("BR"));
        verify(service).create(any());
    }
    @Test void rejectsUnknownQueryParametersBeforeService() throws Exception {
        mvc.perform(get("/api/hr/work-policies/current?countryCode=BR&employeeId=forged")
                .with(user(principal(true)))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
