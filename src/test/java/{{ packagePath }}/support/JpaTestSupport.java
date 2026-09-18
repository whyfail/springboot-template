package {{ package }}.support;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base for {@link DataJpaTest} slices against the shared MySQL container (no embedded database).
 * Flyway auto-configuration is imported explicitly because the JPA slice does not include it, and
 * Hibernate runs in validate mode against the migrated schema.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@ActiveProfiles("test")
public abstract class JpaTestSupport extends SharedContainers {}
