package io.github.juliarezende521.workjourney.journey.api;

import io.github.juliarezende521.workjourney.auth.AccountPrincipal;
import io.github.juliarezende521.workjourney.config.SecurityConfiguration;
import io.github.juliarezende521.workjourney.http.ProblemResponseWriter;
import io.github.juliarezende521.workjourney.journey.*;
import io.github.juliarezende521.workjourney.journey.marking.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({JourneyController.class, JourneyQueryController.class})
@Import({SecurityConfiguration.class, ProblemResponseWriter.class, MarkingRequestReader.class})
class JourneyHttpTest {
    private static final String MARK = "/api/me/work-events";
    private static final Instant NOW = Instant.parse("2026-10-14T14:00:00Z");
    @Autowired MockMvc mvc;
    @MockitoBean OnlineMarkingService markings;
    @MockitoBean CurrentWorkSessionService current;
    @MockitoBean UserDetailsService accounts;
    @MockitoBean io.github.juliarezende521.workjourney.journey.query.JourneyQueries queries;

    private AccountPrincipal principal() {
        return new AccountPrincipal(new AccountPrincipal.Identity(UUID.randomUUID(), UUID.randomUUID(),
                "test@example.invalid", "Teste", "Europe/Amsterdam"), "unused-test-hash", true);
    }

    @Test void invalidFilterIs400ButInternalQueryFailureIs500() throws Exception {
        mvc.perform(get("/api/me/work-sessions?from=invalid&to=2026-10-14").with(user(principal())))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(queries);
        when(queries.history(any(),any(),anyInt(),anyInt())).thenThrow(new NullPointerException("secret internal failure"));
        mvc.perform(get("/api/me/work-sessions?from=2026-10-14&to=2026-10-14").with(user(principal())))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.correlationId").exists())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test void authenticationAndCsrfBlockBeforeServices() throws Exception {
        mvc.perform(get("/api/me/work-sessions/current")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/work-event-requests/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post(MARK).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(MARK).with(user(principal())).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(markings, current);
    }

    @Test void originalStatusAndReceiptSnapshotArePreserved() throws Exception {
        UUID key = UUID.randomUUID();
        var result = new MarkingResult(key, WorkEventType.ENTRY, 201, MarkingResult.Code.RECORDED,
                UUID.randomUUID(), UUID.randomUUID(), NOW, NOW);
        when(markings.execute(key, new MarkingCommand(WorkEventType.ENTRY, null))).thenReturn(result);
        when(markings.lookup(key)).thenReturn(Optional.of(result));
        var original = mvc.perform(post(MARK).with(user(principal())).with(csrf())
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"ENTRY\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.httpStatus").value(201)).andReturn();
        mvc.perform(get("/api/me/work-event-requests/" + key).with(user(principal())))
                .andExpect(status().isOk()).andExpect(content().json(original.getResponse().getContentAsString()))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        var conflict = new MarkingResult(key, WorkEventType.ENTRY, 409, MarkingResult.Code.ALREADY_OPEN, null, null, null, NOW);
        when(markings.execute(any(), any())).thenReturn(conflict);
        mvc.perform(post(MARK).with(user(principal())).with(csrf()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"ENTRY\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_OPEN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]", "{", "{\"type\":\"entry\"}",
            "{\"type\":\"EXIT\"}", "{\"type\":\"ENTRY\",\"expectedWorkSessionId\":\"1-1-1-1-1\"}",
            "{\"type\":\"ENTRY\",\"employeeId\":\"fake\"}",
            "{\"type\":\"ENTRY\",\"occurredAtUtc\":\"2020-01-01T00:00:00Z\"}",
            "{\"type\":\"ENTRY\",\"type\":\"EXIT\"}", "{\"type\":\"ENTRY\"} {}"})
    void malformedOrAdditionalFieldsNeverReachService(String body) throws Exception {
        mvc.perform(post(MARK).with(user(principal())).with(csrf()).header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid journey request"));
        verifyNoInteractions(markings);
    }

    @Test void keysMustBeSingleFullUuidAndBodyIsBounded() throws Exception {
        for (String key : new String[]{"1-1-1-1-1", "invalid", "", UUID.randomUUID() + "," + UUID.randomUUID()}) {
            mvc.perform(post(MARK).with(user(principal())).with(csrf()).header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"ENTRY\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post(MARK).with(user(principal())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"ENTRY\"}")).andExpect(status().isBadRequest());
        mvc.perform(post(MARK).with(user(principal())).with(csrf()).header("Idempotency-Key", UUID.randomUUID(), UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"ENTRY\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(MARK).with(user(principal())).with(csrf()).header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(" ".repeat(1025)))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(post(MARK).with(user(principal())).with(csrf()).header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.TEXT_PLAIN).content("{\"type\":\"ENTRY\"}"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(markings);
    }

    @Test void emptyReceiptIsGeneric404AndInvalidPathDoesNotQuery() throws Exception {
        UUID key = UUID.randomUUID();
        when(markings.lookup(key)).thenReturn(Optional.empty());
        mvc.perform(get("/api/me/work-event-requests/" + key).with(user(principal())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.title").value("Receipt not visible"));
        mvc.perform(get("/api/me/work-event-requests/1-1-1-1-1").with(user(principal())))
                .andExpect(status().isBadRequest());
        verify(markings).lookup(key);
        verifyNoMoreInteractions(markings);
    }

    @Test void currentStateHasMinimalSnapshotAndProvisionalDuration() throws Exception {
        when(current.current()).thenReturn(new CurrentWorkSessionService.Snapshot(null, NOW));
        mvc.perform(get("/api/me/work-sessions/current").with(user(principal())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.session").isEmpty())
                .andExpect(jsonPath("$.allowedActions[0]").value("ENTRY"));
        var session = WorkSession.open(UUID.randomUUID(), UUID.randomUUID(), "Europe/Amsterdam", NOW);
        session.record(WorkEventType.PAUSE_START, NOW.plusSeconds(60));
        when(current.current()).thenReturn(new CurrentWorkSessionService.Snapshot(session, NOW.plusSeconds(3600)));
        mvc.perform(get("/api/me/work-sessions/current").with(user(principal())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.session.state").value("PAUSED"))
                .andExpect(jsonPath("$.session.provisionalWorkDuration").value("PT1M"))
                .andExpect(jsonPath("$.allowedActions[0]").value("PAUSE_END"))
                .andExpect(jsonPath("$.allowedActions[1]").value("EXIT"))
                .andExpect(jsonPath("$.session.employeeId").doesNotExist())
                .andExpect(jsonPath("$.session.actorUserId").doesNotExist())
                .andExpect(jsonPath("$.session.events").doesNotExist());
    }

    @Test void failuresAreGenericAndDoNotPretendToBeTerminalReceipts() throws Exception {
        Exception[] failures = {new CannotAcquireLockException("secret SQL password"),
                new IllegalStateException("secret internal detail"), new AccessDeniedException("secret identity"),
                new AuthenticationCredentialsNotFoundException("secret session")};
        int[] statuses = {503, 500, 403, 401};
        for (int i = 0; i < failures.length; i++) {
            doThrow(failures[i]).when(markings).execute(any(), any());
            mvc.perform(post(MARK).with(user(principal())).with(csrf()).header("Idempotency-Key", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"ENTRY\"}"))
                    .andExpect(status().is(statuses[i]))
                    .andExpect(header().exists("X-Correlation-Id"))
                    .andExpect(jsonPath("$.correlationId").exists())
                    .andExpect(jsonPath("$.requestKey").doesNotExist())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
        }
    }
}
