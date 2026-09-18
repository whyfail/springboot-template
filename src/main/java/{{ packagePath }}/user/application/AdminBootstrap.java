package {{ package }}.user.application;

import {{ package }}.audit.application.AuditRecorder;
import {{ package }}.audit.domain.AuditEventType;
import {{ package }}.audit.domain.AuditResult;
import {{ package }}.authorization.domain.PermissionEntity;
import {{ package }}.authorization.domain.PermissionRepository;
import {{ package }}.authorization.domain.RoleEntity;
import {{ package }}.authorization.domain.RolePermissionEntity;
import {{ package }}.authorization.domain.RolePermissionRepository;
import {{ package }}.authorization.domain.RoleRepository;
import {{ package }}.shared.config.BootstrapProperties;
import {{ package }}.user.domain.UserEntity;
import {{ package }}.user.domain.UserRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator. The entry is OFF by default; it activates only when
 * {@code app.bootstrap.enabled=true} (local profile or an explicit operator decision) and the
 * password comes from the environment without any default. No migration ships an administrator.
 */
@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    static final String ADMIN_ROLE_CODE = "ADMIN";

    private final BootstrapProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    public AdminBootstrap(
            BootstrapProperties properties,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository,
            PasswordEncoder passwordEncoder,
            AuditRecorder auditRecorder,
            Clock clock) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String password = properties.password();
        if (password == null || password.length() < 12) {
            throw new IllegalStateException(
                    "Bootstrap enabled but no administrator password provided via APP_BOOTSTRAP_ADMIN_PASSWORD "
                            + "(minimum 12 characters)");
        }
        if (userRepository.existsByUsername(properties.username())) {
            log.info("Bootstrap administrator '{}' already exists; nothing to do", properties.username());
            return;
        }

        RoleEntity adminRole = roleRepository
                .findByCode(ADMIN_ROLE_CODE)
                .orElseGet(() -> roleRepository.save(new RoleEntity(ADMIN_ROLE_CODE, "Administrator", "Bootstrap system role", true)));
        List<PermissionEntity> permissions = permissionRepository.findAll();
        for (PermissionEntity permission : permissions) {
            if (!rolePermissionRepository.existsByIdRoleIdAndIdPermissionId(adminRole.getId(), permission.getId())) {
                rolePermissionRepository.save(new RolePermissionEntity(adminRole, permission));
            }
        }

        UserEntity admin = new UserEntity(
                properties.username(), null, passwordEncoder.encode(password), properties.displayName(), null, clock.instant());
        admin.assignRole(adminRole, null);
        userRepository.saveAndFlush(admin);

        auditRecorder.record(
                AuditEventType.ADMIN_BOOTSTRAPPED,
                AuditResult.SUCCESS,
                new AuditRecorder.Actor(admin.getId(), admin.getPublicId()),
                AuditRecorder.TARGET_USER,
                admin.getPublicId(),
                java.util.Map.of("username", admin.getUsername(), "role", ADMIN_ROLE_CODE),
                null,
                null);
        log.info(
                "Bootstrap administrator '{}' created with role {} (grant permissions: {})",
                properties.username(),
                ADMIN_ROLE_CODE,
                permissions.size());
    }
}
