package io.github.juliarezende521.workjourney.journey.api;

import io.github.juliarezende521.workjourney.auth.AccountPrincipal;
import io.github.juliarezende521.workjourney.config.SecurityConfiguration;
import io.github.juliarezende521.workjourney.http.ProblemResponseWriter;
import io.github.juliarezende521.workjourney.journey.query.HrJourneyQueries;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HrJourneyController.class)
@Import({SecurityConfiguration.class, ProblemResponseWriter.class, MarkingRequestReader.class})
class HrJourneyHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean HrJourneyQueries queries;
    @MockitoBean UserDetailsService accounts;
    private AccountPrincipal principal(boolean hr) {
        return new AccountPrincipal(new AccountPrincipal.Identity(UUID.randomUUID(), UUID.randomUUID(),
                "fictitious@example.invalid", "Teste", "America/Sao_Paulo", hr), "unused", true);
    }
    @Test void anonymousAndEmployeeCannotReadAndEvenHrCannotWriteHrRoutes() throws Exception {
        String root = "/api/hr/employees";
        mvc.perform(get(root)).andExpect(status().isUnauthorized());
        for (String path : List.of(root, root + "/overview", root + "/" + UUID.randomUUID() + "/work-sessions?from=2026-10-01&to=2026-10-14",
                root + "/" + UUID.randomUUID() + "/work-sessions/" + UUID.randomUUID())) {
            mvc.perform(get(path).with(user(principal(false)))).andExpect(status().isForbidden());
        }
        mvc.perform(post(root).with(user(principal(true))).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete(root + "/" + UUID.randomUUID()).with(user(principal(true))).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(queries);
    }
    @Test void overviewAcceptsOnlyBoundedPaginationParameters() throws Exception {
        when(queries.overview(0, 20)).thenReturn(new HrJourneyQueries.Overview(List.of(), 0, 20, 0, java.time.Instant.EPOCH));
        mvc.perform(get("/api/hr/employees/overview").with(user(principal(true)))).andExpect(status().isOk());
        for (String query : List.of("?employeeId=forged", "?page=0&page=1", "?size=bad")) {
            mvc.perform(get("/api/hr/employees/overview" + query).with(user(principal(true)))).andExpect(status().isBadRequest());
        }
    }

    @Test void hrGetsMinimalDirectoryAndInvalidParametersAreRejected() throws Exception {
        when(queries.employees(0, 20)).thenReturn(new HrJourneyQueries.Directory(
                List.of(new HrJourneyQueries.Person(UUID.randomUUID(), "Brasil", "America/Sao_Paulo")), 0, 20, 1));
        mvc.perform(get("/api/hr/employees").with(user(principal(true))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].name").value("Brasil"))
                .andExpect(jsonPath("$.items[0].email").doesNotExist()).andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
        for (String query : List.of("?size=no", "?page=0&page=1", "?employeeId=forged")) {
            mvc.perform(get("/api/hr/employees" + query).with(user(principal(true)))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/hr/employees/invalid/work-sessions?from=2026-10-01&to=2026-10-14")
                .with(user(principal(true)))).andExpect(status().isBadRequest());
    }
}
