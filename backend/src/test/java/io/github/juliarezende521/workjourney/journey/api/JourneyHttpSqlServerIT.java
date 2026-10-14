package io.github.juliarezende521.workjourney.journey.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.juliarezende521.workjourney.account.*;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JourneyHttpSqlServerIT {
    private static final Instant NOW = Instant.parse("2026-10-14T14:00:00Z");
    private static final String PASSWORD = "isolated-test-password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder encoder;
    @Autowired AppUserRepository users;
    @Autowired EmployeeRepository employees;
    @Autowired EntityManager entityManager;
    @MockitoBean Clock clock;

    @BeforeEach void time() { when(clock.instant()).thenReturn(NOW); }

    @Test void runtimeHttpFlowUsesRealLoginAndKeepsOtherAccountsIsolated() throws Exception {
        mvc.perform(get("/api/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("JOURNEY_ONLINE"))
                .andExpect(jsonPath("$.journeyAvailable").value(true));
        var first = login();
        var second = login();
        UUID key = UUID.randomUUID();
        var entry = mark(first, key, "ENTRY", null, 201);
        String sessionId = entry.get("workSessionId").asText();
        assertEquals(entry, mark(first, key, "ENTRY", null, 201));
        assertEquals(entry, json(mvc.perform(get("/api/me/work-event-requests/" + key).session(first.session()))
                .andExpect(status().isOk()).andReturn()));
        mvc.perform(get("/api/me/work-event-requests/" + key).session(second.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/me/work-sessions/current").session(second.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.session").isEmpty());
        var other = mark(second, UUID.randomUUID(), "ENTRY", null, 201);
        var foreign = mark(second, UUID.randomUUID(), "EXIT", sessionId, 409);
        assertEquals("TARGET_CHANGED", foreign.get("code").asText());
        assertEquals(foreign.get("code"), mark(second, UUID.randomUUID(), "EXIT", UUID.randomUUID().toString(), 409).get("code"));
        assertTrue(foreign.get("workSessionId").isNull());
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        mark(first, UUID.randomUUID(), "PAUSE_START", sessionId, 201);
        when(clock.instant()).thenReturn(NOW.plusSeconds(600));
        mvc.perform(get("/api/me/work-sessions/current").session(first.session()))
                .andExpect(jsonPath("$.session.state").value("PAUSED"))
                .andExpect(jsonPath("$.session.provisionalWorkDuration").value("PT1M"));
        mark(first, UUID.randomUUID(), "PAUSE_END", sessionId, 201);
        when(clock.instant()).thenReturn(NOW.plusSeconds(660));
        mark(first, UUID.randomUUID(), "EXIT", sessionId, 201);
        mvc.perform(get("/api/me/work-sessions/current").session(first.session()))
                .andExpect(jsonPath("$.session").isEmpty()).andExpect(jsonPath("$.allowedActions[0]").value("ENTRY"));
        assertEquals(entry, mark(first, key, "ENTRY", null, 201));
        assertEquals("KEY_REUSED", mark(first, key, "EXIT", sessionId, 409).get("code").asText());
        assertEquals(entry, json(mvc.perform(get("/api/me/work-event-requests/" + key).session(first.session())).andReturn()));
        mvc.perform(get("/api/me/work-sessions/current").session(second.session()))
                .andExpect(jsonPath("$.session.id").value(other.get("workSessionId").asText()))
                .andExpect(jsonPath("$.session.state").value("WORKING"));
        assertEquals(4, ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM dbo.work_event WHERE work_session_id=:id")
                .setParameter("id", sessionId).getSingleResult()).intValue());
    }

    @Test void missingCsrfAndInvalidPayloadCannotWriteReceiptsOrEvents() throws Exception {
        var actor = login();
        UUID key = UUID.randomUUID();
        mvc.perform(post("/api/me/work-events").session(actor.session()).header("Idempotency-Key", key)
                .contentType("application/json").content("{\"type\":\"ENTRY\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/me/work-events").session(actor.session()).header(actor.header(), actor.token())
                .header("Idempotency-Key", key).contentType("application/json")
                .content("{\"type\":\"ENTRY\",\"employeeId\":\"unauthorized\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/work-event-requests/" + key).session(actor.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/me/work-sessions/current").session(actor.session())).andExpect(jsonPath("$.session").isEmpty());
    }

    @Test void disabledAccountCannotMarkOrReadWithExistingSession() throws Exception {
        var actor = login();
        entityManager.createNativeQuery("UPDATE dbo.app_user SET active=0 WHERE id=:id")
                .setParameter("id", actor.userId().toString()).executeUpdate();
        entityManager.clear();
        mvc.perform(get("/api/me/work-sessions/current").session(actor.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/me/work-event-requests/" + UUID.randomUUID()).session(actor.session())).andExpect(status().isForbidden());
        mark(actor, UUID.randomUUID(), "ENTRY", null, 403);
    }

    @Test void historyPagesSummarizeWholePeriodAndDetailIsOwned() throws Exception {
        var actor=login(); var other=login();
        String closed=mark(actor,UUID.randomUUID(),"ENTRY",null,201).get("workSessionId").asText();
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        mark(actor,UUID.randomUUID(),"PAUSE_START",closed,201);
        when(clock.instant()).thenReturn(NOW.plusSeconds(600));
        mark(actor,UUID.randomUUID(),"EXIT",closed,201);
        mark(actor,UUID.randomUUID(),"ENTRY",null,201);
        when(clock.instant()).thenReturn(NOW.plusSeconds(660));
        String filter="/api/me/work-sessions?from=2026-10-14&to=2026-10-14&size=1";
        mvc.perform(get(filter).session(actor.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].provisional").value(true))
                .andExpect(jsonPath("$.summary.closedWorkDuration").value("PT1M"))
                .andExpect(jsonPath("$.summary.closedCount").value(1)).andExpect(jsonPath("$.summary.pendingCount").value(1));
        mvc.perform(get(filter+"&page=1").session(actor.session())).andExpect(jsonPath("$.items[0].id").value(closed))
                .andExpect(jsonPath("$.summary.closedWorkDuration").value("PT1M"));
        mvc.perform(get("/api/me/work-sessions/"+closed).session(actor.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(3)).andExpect(jsonPath("$.intervals[1].kind").value("PAUSE"))
                .andExpect(jsonPath("$.session.employeeId").doesNotExist());
        mvc.perform(get("/api/me/work-sessions/"+closed).session(other.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/me/work-sessions/"+UUID.randomUUID()).session(other.session())).andExpect(status().isNotFound());
        mvc.perform(get(filter).session(other.session())).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.summary.closedWorkDuration").value("PT0S"));
        mvc.perform(get("/api/me/work-sessions?from=2026-10-15&to=2026-10-15").session(actor.session()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void queryFiltersAndSessionAreEnforced() throws Exception {
        mvc.perform(get("/api/me/work-sessions?from=2026-10-14&to=2026-10-14")).andExpect(status().isUnauthorized());
        var actor=login();
        for (String query : new String[]{"", "?from=invalid&to=2026-10-14", "?from=2026-10-15&to=2026-10-14",
                "?from=2025-01-01&to=2026-10-14", "?from=2026-10-14&to=2026-10-14&size=101",
                "?from=2026-10-14&to=2026-10-14&page=-1", "?from=2026-10-14&from=2026-10-13&to=2026-10-14",
                "?from=2026-10-14&to=2026-10-14&employeeId=foreign"}) {
            mvc.perform(get("/api/me/work-sessions"+query).session(actor.session())).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/me/work-sessions/invalid").session(actor.session())).andExpect(status().isBadRequest());
        entityManager.createNativeQuery("UPDATE dbo.app_user SET active=0 WHERE id=:id").setParameter("id",actor.userId().toString()).executeUpdate();
        entityManager.clear();
        mvc.perform(get("/api/me/work-sessions?from=2026-10-14&to=2026-10-14").session(actor.session())).andExpect(status().isForbidden());
    }

    private Login login() throws Exception {
        var account = users.save(new AppUser(UUID.randomUUID() + "@example.invalid", encoder.encode(PASSWORD)));
        employees.save(new Employee(account, "Colaborador fictício", "Europe/Amsterdam"));
        employees.flush();
        entityManager.clear();
        var csrf = mvc.perform(get("/api/auth/csrf")).andReturn();
        var token = json(csrf);
        var session = (MockHttpSession) csrf.getRequest().getSession();
        mvc.perform(post("/api/auth/login").session(session).header(token.get("headerName").asText(), token.get("token").asText())
                .param("email", account.getEmail()).param("password", PASSWORD)).andExpect(status().isNoContent());
        var renewed = json(mvc.perform(get("/api/auth/csrf").session(session)).andReturn());
        return new Login(session, renewed.get("headerName").asText(), renewed.get("token").asText(), account.getId());
    }

    private JsonNode mark(Login actor, UUID key, String type, String target, int status) throws Exception {
        var body = mapper.createObjectNode().put("type", type);
        if (target != null) body.put("expectedWorkSessionId", target);
        return json(mvc.perform(post("/api/me/work-events").session(actor.session()).header(actor.header(), actor.token())
                .header("Idempotency-Key", key).contentType("application/json").content(mapper.writeValueAsBytes(body)))
                .andExpect(status().is(status)).andReturn());
    }

    private JsonNode json(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()); }
    private record Login(MockHttpSession session, String header, String token, UUID userId) { }
}
