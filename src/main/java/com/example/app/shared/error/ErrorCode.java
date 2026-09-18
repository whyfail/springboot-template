package com.example.app.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, contract-visible business error codes. Every error rendered to clients carries one of
 * these codes in the Problem Details {@code code} property.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Bad Request", "请求参数不合法"),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "Unauthorized", "未登录或登录状态已过期"),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Unauthorized", "账号或密码错误"),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden", "权限不足"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Not Found", "资源不存在"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", "不支持的请求方法"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type", "不支持的媒体类型"),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Conflict", "用户名已存在"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Conflict", "邮箱已存在"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "Conflict", "数据状态冲突"),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "Conflict", "数据已被他人修改，请刷新后重试"),
    USER_SELF_DISABLE(HttpStatus.CONFLICT, "Conflict", "不能禁用当前登录账号"),
    ROLE_NOT_FOUND(HttpStatus.BAD_REQUEST, "Bad Request", "角色不存在"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", "请求过于频繁，请稍后再试"),
    SESSION_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", "会话服务暂不可用，请稍后再试"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "服务器内部错误");

    private final HttpStatus status;
    private final String title;
    private final String defaultMsg;

    ErrorCode(HttpStatus status, String title, String defaultMsg) {
        this.status = status;
        this.title = title;
        this.defaultMsg = defaultMsg;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String defaultMsg() {
        return defaultMsg;
    }
}
