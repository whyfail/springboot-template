package {{ package }}.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SharedConfig {

    /** Injected everywhere so tests can freeze time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
