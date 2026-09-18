package com.wl.cwa.authorization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/** Association row linking a role to a permission (iam_role_permission). */
@Entity
@Table(name = "iam_role_permission")
public class RolePermissionEntity {

    @EmbeddedId
    private RolePermissionId id;

    @MapsId("roleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id")
    private RoleEntity role;

    @MapsId("permissionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id")
    private PermissionEntity permission;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RolePermissionEntity() {}

    public RolePermissionEntity(RoleEntity role, PermissionEntity permission) {
        this.role = role;
        this.permission = permission;
        this.id = new RolePermissionId(role.getId(), permission.getId());
    }

    public RolePermissionId getId() {
        return id;
    }

    public RoleEntity getRole() {
        return role;
    }

    public PermissionEntity getPermission() {
        return permission;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
