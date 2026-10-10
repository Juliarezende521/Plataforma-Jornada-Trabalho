package io.github.juliarezende521.workjourney.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/status").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    writeProblem(response, 401, "Authentication required"))
                .accessDeniedHandler((request, response, exception) ->
                    writeProblem(response, 403, "Access denied")));
        // CSRF stays enabled. Login, accounts and logout will be added in the next stage.
        return http.build();
    }

    @Bean
    UserDetailsService noAccountsYet() {
        // Prevent Boot from creating a default user/password in this foundation stage.
        return username -> { throw new UsernameNotFoundException("Account unavailable"); };
    }

    private static void writeProblem(HttpServletResponse response, int status, String title)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title
                + "\",\"status\":" + status + "}");
    }
}
