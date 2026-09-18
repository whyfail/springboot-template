package com.example.app.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * PATCH request. Absent (null) fields are left unchanged; at least one mutable field must be
 * provided alongside the optimistic-lock {@code version} (contract: minProperties 2).
 */
public record UpdateUserRequest(
        @Email(message = "邮箱格式不正确") @Size(max = 254, message = "邮箱过长") String email,
        @Size(min = 1, max = 100, message = "显示名长度须为 1-100 个字符") String displayName,
        @Size(max = 500, message = "头像地址过长") String avatarUrl,
        @NotNull(message = "version 必填") @PositiveOrZero(message = "version 不能为负") Long version) {}
