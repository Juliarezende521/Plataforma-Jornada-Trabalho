package io.github.juliarezende521.workjourney.journey.marking;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!bootstrap")
public class JourneyClockConfiguration {
    @Bean
    Clock journeyClock() { return Clock.systemUTC(); }
}
