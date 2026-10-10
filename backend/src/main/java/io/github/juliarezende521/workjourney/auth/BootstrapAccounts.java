package io.github.juliarezende521.workjourney.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.*;

@Configuration
@Profile("bootstrap")
public class BootstrapAccounts {
    @Bean
    UserDetailsService bootstrapUserDetailsService() {
        return email -> { throw new UsernameNotFoundException("Invalid credentials"); };
    }
}
