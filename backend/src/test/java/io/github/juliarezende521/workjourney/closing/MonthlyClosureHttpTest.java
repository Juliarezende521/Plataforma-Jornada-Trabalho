package io.github.juliarezende521.workjourney.closing;

import io.github.juliarezende521.workjourney.auth.AccountPrincipal;
import io.github.juliarezende521.workjourney.config.SecurityConfiguration;
import io.github.juliarezende521.workjourney.http.ProblemResponseWriter;
import io.github.juliarezende521.workjourney.journey.api.JourneyExceptionHandler;
import io.github.juliarezende521.workjourney.journey.api.MarkingRequestReader;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MonthlyClosureController.class)
@Import({SecurityConfiguration.class, ProblemResponseWriter.class,
        JourneyExceptionHandler.class, MarkingRequestReader.class})
class MonthlyClosureHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean MonthlyClosureService service;
    @MockitoBean UserDetailsService accounts;

    @Test
    void onlyHrWithCsrfCanCloseACompetence() throws Exception {
        var employeeId = UUID.randomUUID();
        var path = "/api/hr/employees/" + employeeId + "/monthly-closures/2026-09";
        when(service.close(employeeId, YearMonth.of(2026, 9))).thenReturn(view(employeeId));

        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(path).with(user(principal(false))).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user(principal(true)))).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user(principal(true))).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.competence").value("2026-09"))
                .andExpect(jsonPath("$.status").value("CLOSED"));
        verify(service).close(employeeId, YearMonth.of(2026, 9));
    }

    @Test
    void rejectsInvalidCompetenceAndUnexpectedPaginationParameters() throws Exception {
        var root = "/api/hr/employees/" + UUID.randomUUID() + "/monthly-closures";
        mvc.perform(get(root + "/2026-13").with(user(principal(true))))
                .andExpect(status().isBadRequest());
        mvc.perform(get(root + "?employeeId=forged").with(user(principal(true))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private static AccountPrincipal principal(boolean hr) {
        return new AccountPrincipal(new AccountPrincipal.Identity(UUID.randomUUID(), UUID.randomUUID(),
                "fictitious@example.invalid", "Teste", "America/Sao_Paulo", hr), "unused", true);
    }

    private static MonthlyClosureService.View view(UUID employeeId) {
        return new MonthlyClosureService.View(UUID.randomUUID(), employeeId, "2026-09",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 100, 100, 0, 60,
                true, 1, 0, 1, "CLOSED", Instant.EPOCH);
    }
}
