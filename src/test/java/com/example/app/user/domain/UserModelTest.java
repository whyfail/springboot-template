package com.example.app.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.example.app.authorization.domain.RoleEntity;
import com.example.app.shared.error.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/** Pure domain-model coverage for the user aggregate: role assignment lifecycle and accessors. */
class UserModelTest {

    private final Instant now = Instant.parse("2026-09-18T10:00:00Z");

    private UserEntity user() {
        UserEntity user =
                new UserEntity("admin", "admin@example.com", "{bcrypt}hash", "Administrator", "http://a/img.png", now);
        ReflectionTestUtils.setField(user, "id", 7L);
        ReflectionTestUtils.setField(user, "publicId", UUID.fromString("00000000-0000-0000-0000-000000000007"));
        ReflectionTestUtils.setField(user, "createdAt", now);
        ReflectionTestUtils.setField(user, "updatedAt", now);
        return user;
    }

    private RoleEntity role(String code) {
        RoleEntity role = new RoleEntity(code, "Name " + code, null, true);
        ReflectionTestUtils.setField(role, "id", (long) code.hashCode());
        return role;
    }

    @Test
    void newUsersAreEnabledWithVersionZero() {
        UserEntity user = user();

        assertThat(user.getId()).isEqualTo(7L);
        assertThat(user.getPublicId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000007"));
        assertThat(user.getUsername()).isEqualTo("admin");
        assertThat(user.getEmail()).isEqualTo("admin@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}hash");
        assertThat(user.getDisplayName()).isEqualTo("Administrator");
        assertThat(user.getAvatarUrl()).isEqualTo("http://a/img.png");
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getVersion()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getPasswordChangedAt()).isEqualTo(now);
        assertThat(user.getCreatedAt()).isEqualTo(now);
        assertThat(user.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void assignAndReplaceRoles() {
        UserEntity user = user();
        RoleEntity admin = role("ADMIN");
        RoleEntity viewer = role("VIEWER");
        RoleEntity auditor = role("AUDITOR");

        user.assignRole(admin, 1L);
        assertThat(user.roleCodes()).containsExactly("ADMIN");
        assertThat(user.getRoleAssignments()).hasSize(1);
        assertThat(user.getRoleAssignments().get(0).getCreatedBy()).isEqualTo(1L);
        assertThat(user.getRoleAssignments().get(0).getRole()).isSameAs(admin);
        assertThat(user.getRoleAssignments().get(0).getUser()).isSameAs(user);

        user.replaceRoles(List.of(viewer, auditor), 2L);
        assertThat(user.roleCodes()).containsExactly("VIEWER", "AUDITOR");
        assertThat(user.getRoleAssignments()).hasSize(2);
    }

    @Test
    void updateProfileOnlyTouchesProvidedFields() {
        UserEntity user = user();

        user.updateProfile(null, "New Name", null);
        assertThat(user.getDisplayName()).isEqualTo("New Name");
        assertThat(user.getEmail()).isEqualTo("admin@example.com");
        assertThat(user.getAvatarUrl()).isEqualTo("http://a/img.png");

        user.updateProfile("new@example.com", null, "http://a/new.png");
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getDisplayName()).isEqualTo("New Name");
        assertThat(user.getAvatarUrl()).isEqualTo("http://a/new.png");
    }

    @Test
    void enableAndDisableToggle() {
        UserEntity user = user();

        user.setEnabled(false);
        assertThat(user.isEnabled()).isFalse();

        user.setEnabled(true);
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    void getByPublicIdMapsMissingUserToStableNotFound() {
        UserRepository repository =
                mock(UserRepository.class, withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
        when(repository.findWithRolesByPublicId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> repository.getByPublicId(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("User not found");
    }

    @Test
    void userRoleIdEquality() {
        UserRoleId key = new UserRoleId(1L, 2L);

        assertThat(key).isEqualTo(new UserRoleId(1L, 2L));
        assertThat(key).hasSameHashCodeAs(new UserRoleId(1L, 2L));
        assertThat(key).isNotEqualTo(new UserRoleId(1L, 3L));
        assertThat(key).isNotEqualTo(new UserRoleId(2L, 2L));
        assertThat(key).isNotEqualTo("different-type");
        assertThat(key).isNotEqualTo(null);
    }
}
