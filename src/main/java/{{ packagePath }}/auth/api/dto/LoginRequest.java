package {{ package }}.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login request. Password length follows the login contract (8..128); stricter rules apply to
 * password creation.
 */
public record LoginRequest(
        @NotBlank(message = "用户名必填") @Size(min = 2, max = 64, message = "用户名长度须为 2-64 个字符") String username,
        @NotBlank(message = "密码必填") @Size(min = 8, max = 128, message = "密码长度须为 8-128 个字符") String password,
        Boolean remember) {}
