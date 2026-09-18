package com.wl.cwa.user.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** Composite key of iam_user_role. */
@Embeddable
public class UserRoleId implements Serializable {

    private Long userId;

    private Long roleId;

    public UserRoleId() {}

    public UserRoleId(Long userId, Long roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getRoleId() {
        return roleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserRoleId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(roleId, that.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, roleId);
    }
}
