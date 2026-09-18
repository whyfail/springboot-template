package {{ package }}.user.application;

import {{ package }}.user.api.dto.UserDetail;
import {{ package }}.user.api.dto.UserSummary;
import {{ package }}.user.domain.UserEntity;
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
