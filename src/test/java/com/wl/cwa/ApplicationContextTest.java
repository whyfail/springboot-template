package com.wl.cwa;

import static org.assertj.core.api.Assertions.assertThat;

import com.wl.cwa.shared.config.CwaBootstrapProperties;
import com.wl.cwa.shared.config.CwaSecurityProperties;
import com.wl.cwa.support.ContainerTestSupport;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** Minimal context-load gate: the full application starts against real MySQL and Redis containers. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ApplicationContextTest extends ContainerTestSupport {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Clock clock;

    @Autowired
    private CwaSecurityProperties securityProperties;

    @Autowired
    private CwaBootstrapProperties bootstrapProperties;

    @Test
    void contextStartsAgainstRealDatabaseAndRedis() {
        assertThat(context.getBeanDefinitionCount()).isPositive();
        assertThat(clock).isNotNull();
    }

    @Test
    void securityPropertiesBindFromConfiguration() {
        assertThat(securityProperties.sessionTtl()).isEqualTo(Duration.ofHours(8));
        assertThat(securityProperties.rememberedSessionTtl()).isEqualTo(Duration.ofDays(30));
        assertThat(securityProperties.maxSessionsPerUser()).isEqualTo(5);
        assertThat(securityProperties.bcryptStrength()).isEqualTo(4); // test profile
    }

    @Test
    void bootstrapIsDisabledInTests() {
        assertThat(bootstrapProperties.enabled()).isFalse();
    }
}
