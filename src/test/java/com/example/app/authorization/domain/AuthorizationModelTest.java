package com.example.app.authorization.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Pure domain-model coverage for the authorization aggregate objects. */
class AuthorizationModelTest {

    private final Instant now = Instant.now();

    private RoleEntity role(String code) {
        RoleEntity role = new RoleEntity(code, "Name " + code, "Description " + code, true);
        ReflectionTestUtils.setField(role, "id", (long) code.hashCode());
        ReflectionTestUtils.setField(role, "publicId", java.util.UUID.randomUUID());
        ReflectionTestUtils.setField(role, "createdAt", now);
        ReflectionTestUtils.setField(role, "updatedAt", now);
        ReflectionTestUtils.setField(role, "version", 3L);
        return role;
    }

    private PermissionEntity permission(String code) {
        PermissionEntity permission = new PermissionEntity(code, "Name " + code, "Description " + code);
        ReflectionTestUtils.setField(permission, "id", (long) code.hashCode());
        ReflectionTestUtils.setField(permission, "createdAt", now);
        return permission;
    }

    @Test
    void roleExposesState() {
        RoleEntity role = role("ADMIN");

        assertThat(role.getId()).isNotNull();
        assertThat(role.getPublicId()).isNotNull();
        assertThat(role.getCode()).isEqualTo("ADMIN");
        assertThat(role.getName()).isEqualTo("Name ADMIN");
        assertThat(role.getDescription()).isEqualTo("Description ADMIN");
        assertThat(role.isSystemRole()).isTrue();
        assertThat(role.isEnabled()).isTrue();
        assertThat(role.getCreatedAt()).isEqualTo(now);
        assertThat(role.getUpdatedAt()).isEqualTo(now);
        assertThat(role.getVersion()).isEqualTo(3L);
    }

    @Test
    void roleDefaultStateIsNotSystem() {
        RoleEntity role = new RoleEntity("VIEWER", "Viewer", null, false);

        assertThat(role.isSystemRole()).isFalse();
        assertThat(role.isEnabled()).isTrue();
        assertThat(role.getDescription()).isNull();
    }

    @Test
    void permissionExposesState() {
        PermissionEntity permission = permission("user:read");

        assertThat(permission.getId()).isNotNull();
        assertThat(permission.getCode()).isEqualTo("user:read");
        assertThat(permission.getName()).isEqualTo("Name user:read");
        assertThat(permission.getDescription()).isEqualTo("Description user:read");
        assertThat(permission.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void rolePermissionAssociationWiresCompositeKey() {
        RoleEntity role = role("ADMIN");
        PermissionEntity permission = permission("user:read");

        RolePermissionEntity association = new RolePermissionEntity(role, permission);

        assertThat(association.getId()).isEqualTo(new RolePermissionId(role.getId(), permission.getId()));
        assertThat(association.getRole()).isSameAs(role);
        assertThat(association.getPermission()).isSameAs(permission);
    }

    @Test
    void compositeKeyEquality() {
        RolePermissionId key = new RolePermissionId(1L, 2L);

        assertThat(key).isEqualTo(new RolePermissionId(1L, 2L));
        assertThat(key).hasSameHashCodeAs(new RolePermissionId(1L, 2L));
        assertThat(key).isNotEqualTo(new RolePermissionId(1L, 3L));
        assertThat(key).isNotEqualTo(new RolePermissionId(2L, 2L));
        assertThat(key).isNotEqualTo("different-type");
        assertThat(key).isNotEqualTo(null);
    }
}
