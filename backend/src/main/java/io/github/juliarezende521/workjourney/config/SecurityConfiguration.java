package io.github.juliarezende521.workjourney.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import io.github.juliarezende521.workjourney.auth.LoginProtectionFilter;
import io.github.juliarezende521.workjourney.auth.LoginAttemptLimiter;
import io.github.juliarezende521.workjourney.http.ProblemResponseWriter;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, LoginAttemptLimiter limiter,
                                           ProblemResponseWriter problems) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/status", "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/hr/work-adjustments/*/decision").hasRole("HR")
                .requestMatchers(HttpMethod.POST, "/api/hr/employees/*/work-schedules").hasRole("HR")
                .requestMatchers(HttpMethod.POST, "/api/hr/employees/*/work-schedule-exceptions").hasRole("HR")
                .requestMatchers(HttpMethod.POST, "/api/hr/employees/*/employment-contracts").hasRole("HR")
                .requestMatchers(HttpMethod.POST, "/api/hr/employees/*/monthly-closures/*").hasRole("HR")
                .requestMatchers(HttpMethod.POST, "/api/hr/employees/*/manager-assignments").hasRole("HR")
                .requestMatchers(HttpMethod.POST, "/api/manager/work-adjustments/*/decision").hasRole("MANAGER")
                .requestMatchers(HttpMethod.GET, "/api/hr/**").hasRole("HR")
                .requestMatchers("/api/hr/**").denyAll()
                .requestMatchers(HttpMethod.GET, "/api/manager/**").hasRole("MANAGER")
                .requestMatchers("/api/manager/**").denyAll()
                .anyRequest().authenticated())
            // Construct only inside the security chain, avoiding duplicate servlet registration.
            .addFilterBefore(new LoginProtectionFilter(limiter, problems), UsernamePasswordAuthenticationFilter.class)
            .formLogin(form -> form.loginProcessingUrl("/api/auth/login")
                .usernameParameter("email")
                .successHandler((request, response, authentication) -> response.setStatus(204))
                .failureHandler((request, response, exception) ->
                    problems.write(response, 401, "Invalid credentials")))
            .httpBasic(basic -> basic.disable())
            .requestCache(cache -> cache.disable())
            .logout(logout -> logout.logoutUrl("/api/auth/logout")
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    problems.write(response, 401, "Authentication required"))
                .accessDeniedHandler((request, response, exception) ->
                    problems.write(response, 403, "Access denied")));
        // Default session CSRF repository and session-fixation protection stay enabled.
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    LoginAttemptLimiter loginAttemptLimiter() {
        return new LoginAttemptLimiter(Clock.systemUTC());
    }
}
