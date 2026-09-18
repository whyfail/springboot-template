package {{ package }}.user.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Full replacement of a user's roles; requires the optimistic-lock version. */
public record ReplaceRolesRequest(
        @NotNull(message = "roleCodes 必填") @Size(max = 20, message = "角色数量超出限制") List<String> roleCodes,
        @NotNull(message = "version 必填") @PositiveOrZero(message = "version 不能为负") Long version) {}
