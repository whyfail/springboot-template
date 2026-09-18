package com.example.app.user.application;

import com.example.app.user.api.dto.UserDetail;
import com.example.app.user.api.dto.UserSummary;
import com.example.app.user.domain.UserEntity;
import java.util.List;

/** Entity-to-DTO mapping. JPA entities never leave the application layer. */
final class UserDtoMapper {

    private UserDtoMapper() {}

    static UserSummary toSummary(UserEntity user, List<String> roleCodes) {
        return new UserSummary(
                user.getPublicId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.isEnabled(),
                List.copyOf(roleCodes.stream().sorted().toList()),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    static UserDetail toDetail(UserEntity user) {
        return new UserDetail(
                user.getPublicId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.isEnabled(),
                user.roleCodes().stream().sorted().toList(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getAvatarUrl(),
                user.getLastLoginAt(),
                user.getVersion());
    }
}
