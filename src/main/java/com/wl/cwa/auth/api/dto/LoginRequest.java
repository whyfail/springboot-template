package com.wl.cwa.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login request accepting both the canonical CWA fields ({@code username}/{@code remember}) and the
 * legacy SPA aliases ({@code name}/{@code checked}). If both spellings are sent, the canonical
 * field wins. Password length follows the login contract (8..128); stricter rules apply to
 * password creation.
 */
public record LoginRequest(
        @JsonAlias("name") @Size(min = 2, max = 64, message = "用户名长度须为 2-64 个字符") String username,
        @NotBlank(message = "密码必填") @Size(min = 8, max = 128, message = "密码长度须为 8-128 个字符") String password,
        @JsonAlias("checked") Boolean remember) {}
