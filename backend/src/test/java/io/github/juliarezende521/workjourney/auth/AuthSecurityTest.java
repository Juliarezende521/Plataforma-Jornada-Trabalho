package io.github.juliarezende521.workjourney.auth;

import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MvcResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("bootstrap")
class AuthSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @MockitoBean UserDetailsService accounts;

    @Test
    void loginRotatesSessionAndCsrfThenLogoutRevokesAccess() throws Exception {
        var identity = new AccountPrincipal.Identity(UUID.randomUUID(), UUID.randomUUID(),
                "brasil@example.invalid", "Colaborador Brasil", "America/Sao_Paulo");
        when(accounts.loadUserByUsername("brasil@example.invalid"))
                .thenAnswer(invocation -> new AccountPrincipal(identity, encoder.encode("test-password-only"), true));
        var csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) csrf.getRequest().getSession();
        var json = responseJson(csrf);
        String oldId = session.getId();
        mvc.perform(post("/api/auth/login").session(session)
                .header(json.get("headerName").asText(), json.get("token").asText())
                .param("email", identity.email()).param("password", "test-password-only"))
                .andExpect(status().isNoContent()).andExpect(header().doesNotExist("Location"));
        org.junit.jupiter.api.Assertions.assertNotEquals(oldId, session.getId());
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(identity.employeeId().toString()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/auth/logout").session(session)
                .header(json.get("headerName").asText(), json.get("token").asText()))
                .andExpect(status().isForbidden());
        var renewed = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        var fresh = responseJson(renewed);
        mvc.perform(post("/api/auth/logout").session(session)
                .header(fresh.get("headerName").asText(), fresh.get("token").asText()))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(session.isInvalid());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithoutCsrfCannotAuthenticate() throws Exception {
        mvc.perform(post("/api/auth/login").param("email", "brasil@example.invalid")
                .param("password", "password" )).andExpect(status().isForbidden());
        verifyNoInteractions(accounts);
    }

    @Test
    void employeeCannotUseHrAdjustmentDecisionEndpoint() throws Exception {
        var identity = new AccountPrincipal.Identity(UUID.randomUUID(), UUID.randomUUID(),
                "employee@example.invalid", "Employee", "America/Sao_Paulo", false);
        mvc.perform(post("/api/hr/work-adjustments/00000000-0000-0000-0000-000000000001/decision")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user(new AccountPrincipal(identity, encoder.encode("unused-password"), true)))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyHrCanReachScheduleWriteEndpoints() throws Exception {
        var employee = new AccountPrincipal.Identity(UUID.randomUUID(), UUID.randomUUID(),
                "employee@example.invalid", "Employee", "America/Sao_Paulo", false);
        var hr = new AccountPrincipal.Identity(UUID.randomUUID(), UUID.randomUUID(),
                "hr@example.invalid", "HR", "America/Sao_Paulo", true);
        for (String path : new String[]{
                "/api/hr/employees/00000000-0000-0000-0000-000000000001/work-schedules",
                "/api/hr/employees/00000000-0000-0000-0000-000000000001/work-schedule-exceptions"}) {
            mvc.perform(post(path)
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                            .user(new AccountPrincipal(employee, encoder.encode("unused-password"), true)))
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post(path)
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                            .user(new AccountPrincipal(hr, encoder.encode("unused-password"), true)))
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void rateLimitedLoginReturnsSharedProblemAndRetryAfter() throws Exception {
        var csrf = mvc.perform(get("/api/auth/csrf")).andReturn();
        var token = responseJson(csrf);
        var session = (MockHttpSession) csrf.getRequest().getSession();
        for (int attempt = 1; attempt <= 11; attempt++) {
            var result = mvc.perform(post("/api/auth/login").session(session)
                    .with(request -> { request.setRemoteAddr("192.0.2.19"); return request; })
                    .header(token.get("headerName").asText(), token.get("token").asText())
                    .param("email", "brasil@example.invalid").param("password", ""));
            if (attempt <= 10) result.andExpect(status().isUnauthorized());
            else result.andExpect(status().isTooManyRequests())
                    .andExpect(header().string("Retry-After", "60"))
                    .andExpect(jsonPath("$.title").value("Too many login attempts"))
                    .andExpect(jsonPath("$.type").value("about:blank"));
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void missingAccountAndWrongPasswordHaveSamePublicError() throws Exception {
        when(accounts.loadUserByUsername("missing@example.invalid"))
                .thenThrow(new UsernameNotFoundException("Not found"));
        for (String email : new String[]{"missing@example.invalid", "other@example.invalid"}) {
            var result = mvc.perform(get("/api/auth/csrf")).andReturn();
            var token = responseJson(result);
            when(accounts.loadUserByUsername("other@example.invalid")).thenAnswer(invocation ->
                new AccountPrincipal(new AccountPrincipal.Identity(UUID.randomUUID(), UUID.randomUUID(),
                        "other@example.invalid", "Other", "Europe/Amsterdam"), encoder.encode("correct-password"), true));
            mvc.perform(post("/api/auth/login").session((MockHttpSession) result.getRequest().getSession())
                    .header(token.get("headerName").asText(), token.get("token").asText())
                    .param("email", email).param("password", "wrong-password"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.title").value("Invalid credentials"));
        }
    }
    private JsonNode responseJson(MvcResult result) throws java.io.IOException {
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
