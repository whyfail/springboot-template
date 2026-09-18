package {{ package }}.user;

import static org.assertj.core.api.Assertions.assertThat;

import {{ package }}.audit.domain.AuditEventEntity;
import {{ package }}.audit.domain.AuditEventRepository;
import {{ package }}.authorization.domain.RoleEntity;
import {{ package }}.authorization.domain.RolePermissionEntity;
import {{ package }}.authorization.domain.RolePermissionRepository;
import {{ package }}.authorization.domain.RoleRepository;
import {{ package }}.user.application.AdminBootstrap;
import {{ package }}.user.domain.UserEntity;
import {{ package }}.user.domain.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Boots the full context with {@code app.bootstrap.enabled=true} so the ApplicationRunner executes
 * against real MySQL, then verifies the created administrator, its role-permission wiring and the
 * idempotent second run. A dedicated MySQL container keeps the committed bootstrap rows isolated
 * from the other integration tests.
 */
@SpringBootTest(
        properties = {
            "app.bootstrap.enabled=true",
            "app.bootstrap.username=bootstrap-admin",
            "app.bootstrap.password=Bootstrap-Pass-2026!",
            "app.bootstrap.display-name=Bootstrap Admin"
        })
class AdminBootstrapIT {

    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("app");

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(MYSQL::stop, "bootstrap-it-cleanup"));
        MYSQL.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        String base = MYSQL.getJdbcUrl();
        String separator = base.contains("?") ? "&" : "?";
        registry.add("spring.datasource.url", () -> base + separator + "connectionTimeZone=UTC");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.url", () -> "redis://127.0.0.1:2"); // unused: no auth flow here
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private AdminBootstrap bootstrap;

    @Test
    void bootstrapCreatesAdminWithAllPermissionsAndAuditRow() {
        UserEntity admin = userRepository.findWithRolesByUsername("bootstrap-admin").orElseThrow();
        RoleEntity adminRole = roleRepository.findByCode("ADMIN").orElseThrow();

        assertThat(admin.roleCodes()).containsExactly("ADMIN");
        List<RolePermissionEntity> grants = rolePermissionRepository.findByIdRoleId(adminRole.getId());
        assertThat(grants).hasSize(4);

        List<AuditEventEntity> events =
                auditEventRepository.findAll().stream()
                        .filter(event -> "ADMIN_BOOTSTRAPPED".equals(event.getEventType()))
                        .toList();
        assertThat(events).hasSize(1);

        // Idempotent: a second run must not create anything new.
        bootstrap.run(null);
        assertThat(userRepository.findWithRolesByUsername("bootstrap-admin").orElseThrow().getId())
                .isEqualTo(admin.getId());
    }
}
