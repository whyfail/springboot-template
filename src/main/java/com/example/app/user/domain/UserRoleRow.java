package com.example.app.user.domain;

/** Flattened (userId, roleCode) projection used to batch-render user summaries without N+1. */
public record UserRoleRow(Long userId, String roleCode) {}
