package {{ package }}.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Create-user request; password policy is 12..128 chars per the OpenAPI contract. */
public record CreateUserRequest(
        @NotBlank(message = "用户名必填")
                @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "用户名仅允许字母、数字、点、下划线和连字符")
                @Size(min = 2, max = 64, message = "用户名长度须为 2-64 个字符")
                String username,
        @NotBlank(message = "密码必填") @Size(min = 12, max = 128, message = "密码长度须为 12-128 个字符") String password,
        @Email(message = "邮箱格式不正确") @Size(max = 254, message = "邮箱过长") String email,
        @NotBlank(message = "显示名必填") @Size(min = 1, max = 100, message = "显示名长度须为 1-100 个字符") String displayName,
        @Size(max = 500, message = "头像地址过长") String avatarUrl,
        @Size(max = 20, message = "角色数量超出限制") List<String> roleCodes) {}
