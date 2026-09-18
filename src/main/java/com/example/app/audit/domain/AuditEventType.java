package com.example.app.audit.domain;

/** Security-relevant event types persisted to audit_event. */
public enum AuditEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    USER_CREATED,
    USER_UPDATED,
    USER_ROLES_CHANGED,
    USER_ENABLED,
    USER_DISABLED,
    ADMIN_BOOTSTRAPPED
}
