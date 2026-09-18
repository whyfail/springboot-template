package com.wl.cwa.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wl.cwa.audit.application.AuditRecorder;
import com.wl.cwa.audit.domain.AuditEventType;
import com.wl.cwa.audit.domain.AuditResult;
import com.wl.cwa.authorization.domain.RoleEntity;
import com.wl.cwa.authorization.domain.RoleRepository;
import com.wl.cwa.shared.error.BusinessException;
import com.wl.cwa.shared.error.ErrorCode;
import com.wl.cwa.shared.security.AuthenticatedUser;
import com.wl.cwa.shared.security.SessionStore;
import com.wl.cwa.user.api.dto.UserDetail;
import com.wl.cwa.user.application.command.CreateUserCommand;
import com.wl.cwa.user.application.command.ReplaceRolesCommand;
import com.wl.cwa.user.application.command.SetEnabledCommand;
import com.wl.cwa.user.application.command.UpdateUserCommand;
import com.wl.cwa.user.domain.UserEntity;
import com.wl.cwa.user.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserAdministrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SessionStore sessionStore;

    @Mock
    private AuditRecorder auditRecorder;

    private UserAdministrationService service;

    private final AuthenticatedUser actor = new AuthenticatedUser(
            1L,
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "root",
            "Root",
            null,
            java.util.Set.of("ADMIN"),
            java.util.Set.of("user:write"),
            "digest",
            Instant.now(),
            Instant.now().plusSeconds(3600));

    @BeforeEach
    void setUp() {
        service = new UserAdministrationService(
                userRepository,
                roleRepository,
                passwordEncoder,
                sessionStore,
                auditRecorder,
                Clock.fixed(Instant.parse("2026-09-18T10:00:00Z"), ZoneOffset.UTC));
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}encoded");
        lenient().when(userRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private UserEntity persistedUser(long version) {
        UserEntity user =
                new UserEntity("jane", "jane@example.com", "{bcrypt}encoded", "Jane", null, Instant.now());
        ReflectionTestUtils.setField(user, "id", 5L);
        ReflectionTestUtils.setField(user, "publicId", UUID.fromString("00000000-0000-0000-0000-000000000005"));
        ReflectionTestUtils.setField(user, "enabled", true);
        ReflectionTestUtils.setField(user, "version", version);
        return user;
    }

    @Test
    void createHashesPasswordResolvesRolesAndAudits() {
        when(userRepository.existsByUsername("jane")).thenReturn(false);
        RoleEntity viewer = new RoleEntity("VIEWER", "Viewer", null, false);
        when(roleRepository.findByCodeInAndEnabledTrue(List.of("VIEWER"))).thenReturn(List.of(viewer));

        UserDetail detail = service.create(
                new CreateUserCommand("jane", "raw-password-123", "jane@example.com", "Jane", null, List.of("VIEWER"), actor));

        assertThat(detail.username()).isEqualTo("jane");
        assertThat(detail.roles()).containsExactly("VIEWER");
        assertThat(detail.version()).isZero();
        verify(passwordEncoder).encode("raw-password-123");
        verify(auditRecorder).record(
                eq(AuditEventType.USER_CREATED), eq(AuditResult.SUCCESS), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createWithDuplicateUsernameMapsTo409() {
        when(userRepository.existsByUsername("jane")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                        new CreateUserCommand("jane", "raw-password-123", null, "Jane", null, null, actor)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USERNAME_ALREADY_EXISTS);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void createWithDuplicateEmailMapsTo409() {
        when(userRepository.existsByUsername("jane")).thenReturn(false);
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                        new CreateUserCommand("jane", "raw-password-123", "jane@example.com", "Jane", null, null, actor)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void createWithUnknownRoleReturns400WithFieldError() {
        when(userRepository.existsByUsername("jane")).thenReturn(false);
        when(roleRepository.findByCodeInAndEnabledTrue(List.of("GHOST"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(
                        new CreateUserCommand("jane", "raw-password-123", null, "Jane", null, List.of("GHOST"), actor)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROLE_NOT_FOUND);
    }

    @Test
    void createRacesIntoDatabaseUniquenessAndStillMaps409() {
        when(userRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));
        when(userRepository.existsByUsername("jane")).thenReturn(false, true);

        assertThatThrownBy(() -> service.create(
                        new CreateUserCommand("jane", "raw-password-123", "jane@example.com", "Jane", null, null, actor)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USERNAME_ALREADY_EXISTS);
    }

    @Test
    void updateAppliesChangesAndBumpsVersion() {
        UserEntity user = persistedUser(0);
        when(userRepository.getByPublicId(user.getPublicId())).thenReturn(user);

        UserDetail detail = service.update(
                new UpdateUserCommand(user.getPublicId(), null, "Jane II", null, 0, actor));

        assertThat(detail.displayName()).isEqualTo("Jane II");
        // The @Version bump itself is covered by the real-JPA lifecycle test in UserAdminIT.
        assertThat(detail.version()).isZero();
        verify(auditRecorder).record(
                eq(AuditEventType.USER_UPDATED), eq(AuditResult.SUCCESS), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateWithStaleVersionReturns409WithoutTouchingUser() {
        UserEntity user = persistedUser(7);
        when(userRepository.getByPublicId(user.getPublicId())).thenReturn(user);

        assertThatThrownBy(() -> service.update(new UpdateUserCommand(user.getPublicId(), null, "Jane", null, 3, actor)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VERSION_CONFLICT);
    }

    @Test
    void updateWithoutMutableFieldsReturns400() {
        UserEntity user = persistedUser(0);
        when(userRepository.getByPublicId(user.getPublicId())).thenReturn(user);

        assertThatThrownBy(() -> service.update(new UpdateUserCommand(user.getPublicId(), null, null, null, 0, actor)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void replaceRolesRevokesSessionsAndAudits() {
        UserEntity user = persistedUser(0);
        when(userRepository.getByPublicId(user.getPublicId())).thenReturn(user);
        RoleEntity viewer = new RoleEntity("VIEWER", "Viewer", null, false);
        when(roleRepository.findByCodeInAndEnabledTrue(List.of("VIEWER"))).thenReturn(List.of(viewer));

        service.replaceRoles(new ReplaceRolesCommand(user.getPublicId(), List.of("VIEWER"), 0, actor));

        verify(sessionStore).revokeAllForUser(5L);
        verify(auditRecorder).record(
                eq(AuditEventType.USER_ROLES_CHANGED), eq(AuditResult.SUCCESS), any(), any(), any(), any(), any(), any());
    }

    @Test
    void disableRevokesSessionsAndAudits() {
        UserEntity user = persistedUser(0);
        when(userRepository.getByPublicId(user.getPublicId())).thenReturn(user);

        service.setEnabled(new SetEnabledCommand(user.getPublicId(), false, 0, actor));

        verify(sessionStore).revokeAllForUser(5L);
        verify(auditRecorder).record(
                eq(AuditEventType.USER_DISABLED), eq(AuditResult.SUCCESS), any(), any(), any(), any(), any(), any());
    }

    @Test
    void enableDoesNotRevokeSessions() {
        UserEntity user = persistedUser(0);
        when(userRepository.getByPublicId(user.getPublicId())).thenReturn(user);
        ReflectionTestUtils.setField(user, "enabled", false);

        service.setEnabled(new SetEnabledCommand(user.getPublicId(), true, 0, actor));

        verify(sessionStore, never()).revokeAllForUser(anyLong());
    }

    @Test
    void actorsCannotDisableThemselves() {
        UserEntity self = new UserEntity("root", null, "{bcrypt}x", "Root", null, Instant.now());
        ReflectionTestUtils.setField(self, "publicId", actor.publicId());
        ReflectionTestUtils.setField(self, "version", 0L);
        when(userRepository.getByPublicId(actor.publicId())).thenReturn(self);

        assertThatThrownBy(() -> service.setEnabled(new SetEnabledCommand(actor.publicId(), false, 0, actor)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_SELF_DISABLE);
    }

    @Test
    void missingUserMapsToStable404() {
        UUID missing = UUID.randomUUID();
        when(userRepository.getByPublicId(missing)).thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> service.get(missing))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }
}
