package com.wl.cwa.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wl.cwa.audit.application.AuditRecorder;
import com.wl.cwa.audit.domain.AuditEventType;
import com.wl.cwa.audit.domain.AuditResult;
import com.wl.cwa.authorization.domain.PermissionEntity;
import com.wl.cwa.authorization.domain.PermissionRepository;
import com.wl.cwa.authorization.domain.RoleEntity;
import com.wl.cwa.authorization.domain.RolePermissionRepository;
import com.wl.cwa.authorization.domain.RoleRepository;
import com.wl.cwa.shared.config.CwaBootstrapProperties;
import com.wl.cwa.user.domain.UserEntity;
import com.wl.cwa.user.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditRecorder auditRecorder;

    private AdminBootstrap bootstrap;

    private final CwaBootstrapProperties properties =
            new CwaBootstrapProperties(true, "admin", "Administrator", "Bootstrap-Pass-2026!");

    @BeforeEach
    void setUp() {
        bootstrap = new AdminBootstrap(
                properties,
                userRepository,
                roleRepository,
                permissionRepository,
                rolePermissionRepository,
                passwordEncoder,
                auditRecorder,
                Clock.fixed(Instant.parse("2026-09-18T10:00:00Z"), ZoneOffset.UTC));
        lenient().when(passwordEncoder.encode(any())).thenReturn("{bcrypt}encoded");
        lenient().when(userRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(roleRepository.findByCode("ADMIN")).thenReturn(Optional.empty());
        RoleEntity adminRole = new RoleEntity("ADMIN", "Administrator", null, true);
        ReflectionTestUtils.setField(adminRole, "id", 1L);
        lenient().when(roleRepository.save(any())).thenReturn(adminRole);
        lenient().when(permissionRepository.findAll()).thenReturn(List.of(new PermissionEntity("user:read", "r", null)));
    }

    @Test
    void missingPasswordFailsStartupLoudly() {
        AdminBootstrap withoutPassword = new AdminBootstrap(
                new CwaBootstrapProperties(true, "admin", "Administrator", ""),
                userRepository,
                roleRepository,
                permissionRepository,
                rolePermissionRepository,
                passwordEncoder,
                auditRecorder,
                Clock.systemUTC());

        assertThatThrownBy(() -> withoutPassword.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CWA_BOOTSTRAP_ADMIN_PASSWORD");
    }

    @Test
    void shortPasswordFailsStartup() {
        AdminBootstrap shortPassword = new AdminBootstrap(
                new CwaBootstrapProperties(true, "admin", "Administrator", "short12"),
                userRepository,
                roleRepository,
                permissionRepository,
                rolePermissionRepository,
                passwordEncoder,
                auditRecorder,
                Clock.systemUTC());

        assertThatThrownBy(() -> shortPassword.run(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createsAdminWithSystemRoleAllPermissionsAndAudit() {
        bootstrap.run(null);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("admin");
        assertThat(captor.getValue().roleCodes()).containsExactly("ADMIN");
        verify(auditRecorder)
                .record(eq(AuditEventType.ADMIN_BOOTSTRAPPED), eq(AuditResult.SUCCESS), any(), any(), any(), any(), any(), any());
    }

    @Test
    void existingAdministratorMakesRunIdempotent() {
        when(userRepository.existsByUsername("admin")).thenReturn(true);

        bootstrap.run(null);

        verify(userRepository, never()).saveAndFlush(any());
        verify(auditRecorder, never())
                .record(any(), any(), any(), any(), any(), any(), any(), any());
    }

}
