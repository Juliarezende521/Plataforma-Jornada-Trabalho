package io.github.juliarezende521.workjourney.closing;

import io.github.juliarezende521.workjourney.auth.*;
import io.github.juliarezende521.workjourney.config.SecurityConfiguration;
import io.github.juliarezende521.workjourney.http.ProblemResponseWriter;
import io.github.juliarezende521.workjourney.journey.api.JourneyExceptionHandler;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MonthlyClosureReportController.class)
@Import({SecurityConfiguration.class, ProblemResponseWriter.class, JourneyExceptionHandler.class})
class MonthlyClosureReportControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean HrAuthorization authorization;
    @MockitoBean MonthlyClosureRepository closures;
    @MockitoBean UserDetailsService accounts;

    private AccountPrincipal principal(boolean hr) {
        return new AccountPrincipal(new AccountPrincipal.Identity(UUID.randomUUID(), UUID.randomUUID(),
                "person@example.invalid", "Pessoa", "America/Sao_Paulo", hr), "unused", true);
    }

    @Test void onlyHrCanDownloadAndUnknownOrRepeatedParametersAreRejected() throws Exception {
        mvc.perform(get("/api/hr/monthly-closures/report?competence=2026-09"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/hr/monthly-closures/report?competence=2026-09").with(user(principal(false))))
                .andExpect(status().isForbidden());
        for (String query : List.of("", "?competence=invalid", "?competence=2026-09&extra=x",
                "?competence=2026-09&competence=2026-10")) {
            mvc.perform(get("/api/hr/monthly-closures/report" + query).with(user(principal(true))))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(authorization, closures);
    }

    @Test void downloadsOneQueryCsvWithoutCachingAndNeutralizesSpreadsheetFormula() throws Exception {
        var row = mock(MonthlyClosureRepository.ConsolidatedRow.class);
        when(row.getEmployeeName()).thenReturn("=Nome perigoso");
        when(row.getReferenceTimeZoneId()).thenReturn("America/Sao_Paulo");
        when(row.getTotalWorkSeconds()).thenReturn(28_800L);
        when(row.getPlannedWorkSeconds()).thenReturn(28_800L);
        when(row.getWithinTolerance()).thenReturn(true);
        when(row.getTotalSessions()).thenReturn(20L);
        when(row.getWorkedDays()).thenReturn(20);
        when(row.getClosedAtUtc()).thenReturn(OffsetDateTime.parse("2026-10-01T12:00:00Z"));
        when(closures.consolidated(eq((short) 2026), eq((byte) 9), any(Pageable.class)))
                .thenReturn(List.of(row));

        mvc.perform(get("/api/hr/monthly-closures/report?competence=2026-09").with(user(principal(true))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"fechamentos-2026-09.csv\""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("'=Nome perigoso")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PT8H")));
        verify(authorization).require();
        verify(closures).consolidated(eq((short) 2026), eq((byte) 9), argThat(page -> page.getPageSize() == 10_001));
    }
}
