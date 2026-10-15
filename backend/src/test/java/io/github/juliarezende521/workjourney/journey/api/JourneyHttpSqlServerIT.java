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
                .andExpect(jsonPath("$.session.state").value("CLOSED")).andExpect(jsonPath("$.session.id").value(sessionId)).andExpect(jsonPath("$.allowedActions[0]").value("ENTRY"));
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

    @Test void historyPagesAndDetailAreOwned() throws Exception {
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
                .andExpect(jsonPath("$.items[0].state").value("WORKING"));
        mvc.perform(get(filter+"&page=1").session(actor.session())).andExpect(jsonPath("$.items[0].id").value(closed));
        mvc.perform(get("/api/me/work-sessions/"+closed).session(actor.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(3)).andExpect(jsonPath("$.events[1].type").value("PAUSE_START"))
                .andExpect(jsonPath("$.session.employeeId").doesNotExist());
        mvc.perform(get("/api/me/work-sessions/"+closed).session(other.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/me/work-sessions/"+UUID.randomUUID()).session(other.session())).andExpect(status().isNotFound());
        mvc.perform(get(filter).session(other.session())).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/me/work-sessions?from=2026-10-15&to=2026-10-15").session(actor.session()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void historyKeepsCrossMonthJourneyInReferenceDateAndExactPrecision() throws Exception {
        var actor=login();
        Instant start=Instant.parse("2026-10-31T22:30:00.0000001Z");
        when(clock.instant()).thenReturn(start);
        String id=mark(actor,UUID.randomUUID(),"ENTRY",null,201).get("workSessionId").asText();
        when(clock.instant()).thenReturn(start.plusSeconds(10800).plusNanos(100));
        mark(actor,UUID.randomUUID(),"EXIT",id,201);
        mvc.perform(get("/api/me/work-sessions?from=2026-10-31&to=2026-10-31").session(actor.session()))
                .andExpect(jsonPath("$.items[0].workDate").value("2026-10-31"));
        mvc.perform(get("/api/me/work-sessions?from=2026-11-01&to=2026-11-01").session(actor.session()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/me/work-sessions/"+id).session(actor.session()))
                .andExpect(jsonPath("$.events[0].occurredAtUtc").value(start.toString()));
    }

    @Test void samePersistedEventIsViewedInSaoPauloAndLisbonWithoutChangingIt() throws Exception {
        var actor=login();
        var entry=mark(actor,UUID.randomUUID(),"ENTRY",null,201);String id=entry.get("workSessionId").asText();
        var before=json(mvc.perform(get("/api/me/work-sessions/"+id).session(actor.session())).andExpect(status().isOk()).andReturn());
        var event=before.get("events").get(0);Instant instant=Instant.parse(event.get("occurredAtUtc").asText());
        var format=java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm XXX");
        assertEquals("2026-10-14 11:00 -03:00",instant.atZone(java.time.ZoneId.of("America/Sao_Paulo")).format(format));
        assertEquals("2026-10-14 15:00 +01:00",instant.atZone(java.time.ZoneId.of("Europe/Lisbon")).format(format));
        assertEquals("Europe/Amsterdam",event.get("recordedTimeZoneId").asText());
        entityManager.createNativeQuery("UPDATE dbo.employee SET reference_time_zone_id='Europe/Lisbon' WHERE user_id=:id").setParameter("id",actor.userId().toString()).executeUpdate();entityManager.clear();
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));mark(actor,UUID.randomUUID(),"PAUSE_START",id,201);
        var after=json(mvc.perform(get("/api/me/work-sessions/"+id).session(actor.session())).andReturn());
        assertEquals(event,after.get("events").get(0));assertEquals("Europe/Lisbon",after.get("events").get(1).get("recordedTimeZoneId").asText());
    }

    @Test void browserZoneIsRecordedWhileBrazilReferenceDateAndReplayStayStable() throws Exception {
        var actor = login();
        entityManager.createNativeQuery("UPDATE dbo.employee SET reference_time_zone_id='America/Sao_Paulo' WHERE user_id=:id")
                .setParameter("id", actor.userId().toString()).executeUpdate();
        entityManager.clear();
        Instant instant = Instant.parse("2026-10-15T01:30:00Z");
        when(clock.instant()).thenReturn(instant);
        UUID key = UUID.randomUUID();
        var entry = mark(actor, key, "ENTRY", null, "Europe/Lisbon", 201);
        String id = entry.get("workSessionId").asText();
        when(clock.instant()).thenReturn(instant.plusSeconds(60));
        assertEquals(entry, mark(actor, key, "ENTRY", null, "Europe/Lisbon", 201));
        assertEquals("KEY_REUSED", mark(actor, key, "ENTRY", null, "America/Sao_Paulo", 409).get("code").asText());
        mark(actor, UUID.randomUUID(), "PAUSE_START", id, "Europe/Lisbon", 201);
        mvc.perform(get("/api/me/work-sessions/current").session(actor.session()))
                .andExpect(jsonPath("$.session.lastEvent.recordedTimeZoneId").value("Europe/Lisbon"));
        mark(actor, UUID.randomUUID(), "PAUSE_END", id, 201);
        mark(actor, UUID.randomUUID(), "EXIT", id, "Europe/Lisbon", 201);
        mvc.perform(get("/api/me/work-sessions/" + id).session(actor.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.referenceTimeZoneId").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.session.workDate").value("2026-10-14"))
                .andExpect(jsonPath("$.events[0].occurredAtUtc").value(instant.toString()))
                .andExpect(jsonPath("$.events[0].recordedTimeZoneId").value("Europe/Lisbon"))
                .andExpect(jsonPath("$.events[1].recordedTimeZoneId").value("Europe/Lisbon"))
                .andExpect(jsonPath("$.events[2].recordedTimeZoneId").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.events[3].recordedTimeZoneId").value("Europe/Lisbon"));
        mvc.perform(get("/api/me/work-sessions?from=2026-10-14&to=2026-10-14").session(actor.session()))
                .andExpect(jsonPath("$.items[0].id").value(id));
        mvc.perform(get("/api/me/work-sessions?from=2026-10-15&to=2026-10-15").session(actor.session()))
                .andExpect(jsonPath("$.totalElements").value(0));
        UUID invalidKey = UUID.randomUUID();
        mark(actor, invalidKey, "ENTRY", null, "Invalid/Zone", 400);
        mvc.perform(get("/api/me/work-event-requests/" + invalidKey).session(actor.session()))
                .andExpect(status().isNotFound());
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
        return mark(actor, key, type, target, null, status);
    }

    private JsonNode mark(Login actor, UUID key, String type, String target, String zone, int status) throws Exception {
        var body = mapper.createObjectNode().put("type", type);
        if (zone != null) body.put("timeZoneId", zone);
        if (target != null) body.put("expectedWorkSessionId", target);
        return json(mvc.perform(post("/api/me/work-events").session(actor.session()).header(actor.header(), actor.token())
                .header("Idempotency-Key", key).contentType("application/json").content(mapper.writeValueAsBytes(body)))
                .andExpect(status().is(status)).andReturn());
    }

    private JsonNode json(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()); }
    private record Login(MockHttpSession session, String header, String token, UUID userId) { }
}
