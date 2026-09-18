package com.example.app.user.domain;

import com.example.app.authorization.domain.RoleEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * User aggregate root (iam_user). Never leaves the application layer; API responses use DTOs. The
 * optimistic-lock {@code version} column backs PATCH concurrency control.
 */
@Entity
@Table(
        name = "iam_user",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_iam_user_public_id", columnNames = "public_id"),
            @UniqueConstraint(name = "uk_iam_user_username", columnNames = "username"),
            @UniqueConstraint(name = "uk_iam_user_email", columnNames = "email")
        })
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @UuidGenerator
    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserRoleEntity> roleAssignments = new ArrayList<>();

    protected UserEntity() {}

    public UserEntity(String username, String email, String passwordHash, String displayName, String avatarUrl, Instant passwordChangedAt) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.passwordChangedAt = passwordChangedAt;
    }

    public void assignRole(RoleEntity role, Long createdBy) {
        UserRoleEntity assignment = new UserRoleEntity(this, role, createdBy);
        roleAssignments.add(assignment);
    }

    /** Replaces the full role assignment list inside the owning transaction. */
    public void replaceRoles(List<RoleEntity> roles, Long createdBy) {
        roleAssignments.clear();
        for (RoleEntity role : roles) {
            assignRole(role, createdBy);
        }
    }

    public List<String> roleCodes() {
        return roleAssignments.stream()
                .map(assignment -> assignment.getRole().getCode())
                .toList();
    }

    public void updateProfile(String email, String displayName, String avatarUrl) {
        if (email != null) {
            this.email = email;
        }
        if (displayName != null) {
            this.displayName = displayName;
        }
        if (avatarUrl != null) {
            this.avatarUrl = avatarUrl;
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<UserRoleEntity> getRoleAssignments() {
        return roleAssignments;
    }
}
