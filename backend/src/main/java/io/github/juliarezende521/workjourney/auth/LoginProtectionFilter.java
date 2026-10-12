package io.github.juliarezende521.workjourney.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import io.github.juliarezende521.workjourney.http.ProblemResponseWriter;
import org.springframework.web.filter.OncePerRequestFilter;

/** Single-instance safeguard; does not trust client-supplied forwarding headers. */
public class LoginProtectionFilter extends OncePerRequestFilter {
    private final LoginAttemptLimiter limiter;
    private final ProblemResponseWriter problems;

    public LoginProtectionFilter(LoginAttemptLimiter limiter, ProblemResponseWriter problems) {
        this.limiter = limiter;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !"/api/auth/login".equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!limiter.allow(request.getRemoteAddr())) {
            response.setHeader("Retry-After", Integer.toString(LoginAttemptLimiter.WINDOW_SECONDS));
            problems.write(response, 429, "Too many login attempts");
            return;
        }
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        if (email == null || email.length() > 254 || password == null || password.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            problems.write(response, 401, "Invalid credentials");
            return;
        }
        chain.doFilter(request, response);
    }

}
