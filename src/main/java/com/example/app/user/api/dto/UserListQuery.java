package com.example.app.user.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * List filters bound from query parameters. The sort field is re-validated server-side against a
 * whitelist; {@code size} is capped by the contract.
 */
public record UserListQuery(
        @Size(max = 100, message = "关键词过长") String keyword,
        Boolean enabled,
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "角色编码格式不正确") String role,
        @Min(value = 0, message = "页码不能为负") Integer page,
        @Min(value = 1, message = "每页大小最小为 1") @Max(value = 100, message = "每页大小最大为 100") Integer size,
        String sort) {}
