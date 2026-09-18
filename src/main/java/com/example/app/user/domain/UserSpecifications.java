package com.example.app.user.domain;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Dynamic filters for the user list query; only whitelisted shapes reach the criteria API. */
public final class UserSpecifications {

    private UserSpecifications() {}

    public static Specification<UserEntity> matchesKeyword(String keyword) {
        return (root, query, cb) -> {
            String like = "%" + escapeLike(keyword.toLowerCase()) + "%";
            List<Predicate> alternatives = new ArrayList<>();
            alternatives.add(cb.like(cb.lower(root.get("username")), like));
            alternatives.add(cb.like(cb.lower(root.get("displayName")), like));
            alternatives.add(cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like));
            return cb.or(alternatives.toArray(Predicate[]::new));
        };
    }

    public static Specification<UserEntity> hasEnabled(Boolean enabled) {
        return (root, query, cb) -> enabled == null ? cb.conjunction() : cb.equal(root.get("enabled"), enabled);
    }

    public static Specification<UserEntity> hasRole(String roleCode) {
        return (root, query, cb) -> {
            var assignments = root.join("roleAssignments");
            var role = assignments.join("role");
            return cb.equal(role.get("code"), roleCode);
        };
    }

    private static String escapeLike(String input) {
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
