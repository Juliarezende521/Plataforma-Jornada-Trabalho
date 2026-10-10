package io.github.juliarezende521.workjourney.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {
    @GetMapping("/api/auth/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @GetMapping("/api/me")
    public AccountPrincipal.Identity me(@AuthenticationPrincipal AccountPrincipal principal) {
        return principal.identity();
    }

    public record CsrfResponse(String headerName, String token) { }
}
