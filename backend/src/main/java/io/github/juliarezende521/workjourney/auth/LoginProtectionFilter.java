package io.github.juliarezende521.workjourney.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;

/** Single-instance safeguard; does not trust client-supplied forwarding headers. */
public class LoginProtectionFilter extends OncePerRequestFilter {
    private final Map<String, Window> attempts = new HashMap<>();
    private final Clock clock;
    public LoginProtectionFilter() { this(Clock.systemUTC()); }
    LoginProtectionFilter(Clock clock) { this.clock = clock; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !"/api/auth/login".equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!allow(request.getRemoteAddr())) {
            response.setHeader("Retry-After", "60");
            problem(response, 429, "Too many login attempts");
            return;
        }
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        if (email == null || email.length() > 254 || password == null || password.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            problem(response, 401, "Invalid credentials");
            return;
        }
        chain.doFilter(request, response);
    }

    synchronized boolean allow(String address) {
        long now = clock.millis();
        attempts.entrySet().removeIf(entry -> now - entry.getValue().started >= 60_000);
        Window window = attempts.get(address);
        if (window == null) {
            if (attempts.size() >= 4096) return false;
            window = new Window(now);
            attempts.put(address, window);
        }
        return ++window.count <= 10;
    }

    private static void problem(HttpServletResponse response, int status, String title) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"status\":" + status
                + ",\"title\":\"" + title + "\"}");
    }
    private static class Window {
        final long started;
        int count;
        Window(long started) { this.started = started; }
    }
}
