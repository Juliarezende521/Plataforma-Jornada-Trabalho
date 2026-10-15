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
class HrJourneySqlServerIT {
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

    @Test void hrReadsSelectedEmployeesButCannotUseOwnRoutesForForeignRecordsOrWriteThroughHr() throws Exception {
        var target = login(false); var other = login(false); var hr = login(true);
        String id = mark(target, UUID.randomUUID(), "ENTRY", null, "Europe/Lisbon", 201).get("workSessionId").asText();
        String employee = json(mvc.perform(get("/api/me").session(target.session())).andReturn()).get("employeeId").asText();
        String otherEmployee = json(mvc.perform(get("/api/me").session(other.session())).andReturn()).get("employeeId").asText();
        String base = "/api/hr/employees/" + employee + "/work-sessions";
        long eventsBefore = ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM dbo.work_event").getSingleResult()).longValue();
        mvc.perform(get("/api/hr/employees").session(target.session())).andExpect(status().isForbidden());
        mvc.perform(get(base + "?from=2026-10-14&to=2026-10-14").session(target.session())).andExpect(status().isForbidden());
        mvc.perform(get(base + "/" + id).session(target.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/hr/employees?size=1&page=0").session(hr.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].email").doesNotExist());
        mvc.perform(get(base + "?from=2026-10-14&to=2026-10-14").session(hr.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(id));
        mvc.perform(get(base + "/" + id).session(hr.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].recordedTimeZoneId").value("Europe/Lisbon"));
        mvc.perform(get("/api/hr/employees/" + otherEmployee + "/work-sessions/" + id).session(hr.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/me/work-sessions/" + id).session(hr.session())).andExpect(status().isNotFound());
        mvc.perform(post(base).session(hr.session()).header(hr.header(), hr.token()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        assertEquals(eventsBefore, ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM dbo.work_event").getSingleResult()).longValue());
    }

    @Test void revocationAndDeactivationApplyToExistingHrSession() throws Exception {
        var hr = login(true);
        mvc.perform(get("/api/hr/employees").session(hr.session())).andExpect(status().isOk());
        entityManager.createNativeQuery("UPDATE dbo.app_user SET hr_reader=0 WHERE id=:id").setParameter("id", hr.userId().toString()).executeUpdate();
        entityManager.clear();
        mvc.perform(get("/api/hr/employees").session(hr.session())).andExpect(status().isForbidden());
        entityManager.createNativeQuery("UPDATE dbo.app_user SET hr_reader=1, active=0 WHERE id=:id").setParameter("id", hr.userId().toString()).executeUpdate();
        entityManager.clear();
        mvc.perform(get("/api/hr/employees").session(hr.session())).andExpect(status().isForbidden());
    }

    @Test void inactiveTargetsAndInvalidFiltersAreNotReadable() throws Exception {
        var hr = login(true); var target = login(false);
        String employee = json(mvc.perform(get("/api/me").session(target.session())).andReturn()).get("employeeId").asText();
        String base = "/api/hr/employees/" + employee + "/work-sessions";
        for (String query : new String[]{"?from=bad&to=2026-10-14", "?from=2026-10-14&to=2026-10-13", "?from=2026-10-01&to=2026-10-14&size=101", "?from=2026-10-01&to=2026-10-14&employeeId=forged"}) {
            mvc.perform(get(base + query).session(hr.session())).andExpect(status().isBadRequest());
        }
        for (String query : new String[]{"?page=-1", "?size=101", "?size=0"}) {
            mvc.perform(get("/api/hr/employees" + query).session(hr.session())).andExpect(status().isBadRequest());
        }
        entityManager.createNativeQuery("UPDATE dbo.app_user SET active=0 WHERE id=:id").setParameter("id", target.userId().toString()).executeUpdate();
        entityManager.clear();
        mvc.perform(get(base + "?from=2026-10-01&to=2026-10-14").session(hr.session())).andExpect(status().isNotFound());
        var directory = json(mvc.perform(get("/api/hr/employees").session(hr.session())).andExpect(status().isOk()).andReturn());
        assertFalse(directory.findValuesAsText("id").contains(employee));
    }

    @Test void overviewFollowsRealTransitionsAndPreservesAnOpenJourneyAcrossMidnight() throws Exception {
        var target = login(false); var hr = login(true);
        String employee = json(mvc.perform(get("/api/me").session(target.session())).andReturn()).get("employeeId").asText();
        assertOverview(hr, employee, "NOT_STARTED");
        String id = mark(target, UUID.randomUUID(), "ENTRY", null, 201).get("workSessionId").asText();
        assertOverview(hr, employee, "WORKING");
        mark(target, UUID.randomUUID(), "PAUSE_START", id, 201);
        assertOverview(hr, employee, "PAUSED");
        when(clock.instant()).thenReturn(Instant.parse("2026-10-15T02:00:00Z"));
        assertOverview(hr, employee, "PAUSED");
        mark(target, UUID.randomUUID(), "EXIT", id, 201);
        assertOverview(hr, employee, "NOT_STARTED"); // closed yesterday in Amsterdam
        String next = mark(target, UUID.randomUUID(), "ENTRY", null, 201).get("workSessionId").asText();
        assertOverview(hr, employee, "WORKING");
        mark(target, UUID.randomUUID(), "EXIT", next, 201);
        assertOverview(hr, employee, "CLOSED");
        mark(target, UUID.randomUUID(), "ENTRY", null, 201); // equal UTC instant: open wins over a closed session
        assertOverview(hr, employee, "WORKING");
        entityManager.createNativeQuery("UPDATE dbo.app_user SET active=0 WHERE id=:id").setParameter("id", target.userId().toString()).executeUpdate();
        entityManager.clear();
        var result = json(mvc.perform(get("/api/hr/employees/overview?size=100").session(hr.session())).andExpect(status().isOk()).andReturn());
        assertFalse(result.findValuesAsText("id").contains(employee));
    }

    @Test void modalidadePersisteEmCadaEventoEReenvioPreservaRecibo() throws Exception {
        var target = login(false); var hr = login(true); var key = UUID.randomUUID();
        var entry = mark(target, key, "ENTRY", null, "Europe/Lisbon", "TRAVEL", 201);
        assertEquals(entry, mark(target, key, "ENTRY", null, "Europe/Lisbon", "TRAVEL", 201));
        mark(target, key, "ENTRY", null, "Europe/Lisbon", "REMOTE", 409);
        String id = entry.get("workSessionId").asText();
        mvc.perform(get("/api/me/work-sessions/current").session(target.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.session.lastEvent.workMode").value("TRAVEL"));
        mark(target, UUID.randomUUID(), "PAUSE_START", id, "Europe/Lisbon", "OFFICE", 201);
        mark(target, UUID.randomUUID(), "EXIT", id, "Europe/Lisbon", "REMOTE", 201);
        String employee = json(mvc.perform(get("/api/me").session(target.session())).andReturn()).get("employeeId").asText();
        for (String path : new String[]{"/api/me/work-sessions/" + id, "/api/hr/employees/" + employee + "/work-sessions/" + id}) {
            var actor = path.startsWith("/api/hr") ? hr : target;
            mvc.perform(get(path).session(actor.session())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.events[0].workMode").value("TRAVEL"))
                    .andExpect(jsonPath("$.events[1].workMode").value("OFFICE"))
                    .andExpect(jsonPath("$.events[2].workMode").value("REMOTE"));
        }
        mark(target, UUID.randomUUID(), "ENTRY", null, "Europe/Lisbon", "INVALID", 400);
        mark(target, UUID.randomUUID(), "ENTRY", null, 201);
        mvc.perform(get("/api/me/work-sessions/current").session(target.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.session.lastEvent.workMode").isEmpty());
    }

    private void assertOverview(Login hr, String employee, String state) throws Exception {
        var result = json(mvc.perform(get("/api/hr/employees/overview?size=100").session(hr.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.observedAtUtc").exists()).andReturn());
        for (var person : result.get("items")) {
            if (employee.equals(person.get("id").asText())) {
                assertEquals(state, person.get("state").asText());
                assertFalse(person.has("email"));
                return;
            }
        }
        fail("Expected active employee in overview");
    }

    private Login login(boolean hr) throws Exception {
        var account = users.save(new AppUser(UUID.randomUUID() + "@example.invalid", encoder.encode(PASSWORD), hr));
        employees.save(new Employee(account, "Colaborador fictício", "Europe/Amsterdam"));
        employees.flush();
        entityManager.clear();
        var csrf = mvc.perform(get("/api/auth/csrf")).andReturn();
        var token = json(csrf);
        var session = (MockHttpSession) csrf.getRequest().getSession();
        mvc.perform(post("/api/auth/login")
                // Isolated fictitious client: do not consume the own-history suite's limiter window.
                .with(request -> { request.setRemoteAddr("198.51.100.50"); return request; })
                .session(session).header(token.get("headerName").asText(), token.get("token").asText())
                .param("email", account.getEmail()).param("password", PASSWORD)).andExpect(status().isNoContent());
        var renewed = json(mvc.perform(get("/api/auth/csrf").session(session)).andReturn());
        return new Login(session, renewed.get("headerName").asText(), renewed.get("token").asText(), account.getId());
    }

    private JsonNode mark(Login actor, UUID key, String type, String target, int status) throws Exception {
        return mark(actor, key, type, target, null, status);
    }

    private JsonNode mark(Login actor, UUID key, String type, String target, String zone, int status) throws Exception {
        return mark(actor, key, type, target, zone, null, status);
    }

    private JsonNode mark(Login actor, UUID key, String type, String target, String zone, String mode, int status) throws Exception {
        var body = mapper.createObjectNode().put("type", type);
        if (mode != null) body.put("workMode", mode);
        if (zone != null) body.put("timeZoneId", zone);
        if (target != null) body.put("expectedWorkSessionId", target);
        return json(mvc.perform(post("/api/me/work-events").session(actor.session()).header(actor.header(), actor.token())
                .header("Idempotency-Key", key).contentType("application/json").content(mapper.writeValueAsBytes(body)))
                .andExpect(status().is(status)).andReturn());
    }

    private JsonNode json(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()); }
    private record Login(MockHttpSession session, String header, String token, UUID userId) { }
}
