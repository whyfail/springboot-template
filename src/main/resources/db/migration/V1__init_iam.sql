-- CWA Spring Boot enterprise template initial IAM schema (MySQL 8.4).
-- Derived from the design baseline 04-schema.sql. App connections run in UTC.
-- Raw passwords and raw session tokens are never stored in the database.

CREATE TABLE iam_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    username VARCHAR(64) NOT NULL,
    email VARCHAR(254) NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    locked_until DATETIME(6) NULL,
    password_changed_at DATETIME(6) NOT NULL,
    last_login_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_user_public_id (public_id),
    UNIQUE KEY uk_iam_user_username (username),
    UNIQUE KEY uk_iam_user_email (email),
    KEY idx_iam_user_enabled_created (enabled, created_at DESC, id DESC),
    KEY idx_iam_user_display_name (display_name)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT='用户主表';

CREATE TABLE iam_role (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_role_public_id (public_id),
    UNIQUE KEY uk_iam_role_code (code),
    KEY idx_iam_role_enabled (enabled, id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT='角色';

CREATE TABLE iam_permission (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_permission_code (code)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT='权限点';

CREATE TABLE iam_user_role (
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT UNSIGNED NULL,
    PRIMARY KEY (user_id, role_id),
    KEY idx_iam_user_role_role_user (role_id, user_id),
    CONSTRAINT fk_iam_user_role_user
        FOREIGN KEY (user_id) REFERENCES iam_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_iam_user_role_role
        FOREIGN KEY (role_id) REFERENCES iam_role (id) ON DELETE RESTRICT,
    CONSTRAINT fk_iam_user_role_created_by
        FOREIGN KEY (created_by) REFERENCES iam_user (id) ON DELETE SET NULL
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT='用户角色关联';

CREATE TABLE iam_role_permission (
    role_id BIGINT UNSIGNED NOT NULL,
    permission_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    KEY idx_iam_role_permission_permission_role (permission_id, role_id),
    CONSTRAINT fk_iam_role_permission_role
        FOREIGN KEY (role_id) REFERENCES iam_role (id) ON DELETE CASCADE,
    CONSTRAINT fk_iam_role_permission_permission
        FOREIGN KEY (permission_id) REFERENCES iam_permission (id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT='角色权限关联';

CREATE TABLE audit_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    actor_user_id BIGINT UNSIGNED NULL,
    actor_public_id BINARY(16) NULL,
    event_type VARCHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL,
    target_type VARCHAR(64) NULL,
    target_public_id BINARY(16) NULL,
    request_id VARCHAR(100) NOT NULL,
    trace_id VARCHAR(64) NULL,
    client_ip_hash BINARY(32) NULL,
    user_agent_hash BINARY(32) NULL,
    details_json JSON NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_audit_event_public_id (public_id),
    KEY idx_audit_event_time_id (occurred_at DESC, id DESC),
    KEY idx_audit_event_actor_time (actor_user_id, occurred_at DESC, id DESC),
    KEY idx_audit_event_type_time (event_type, occurred_at DESC, id DESC),
    KEY idx_audit_event_request_id (request_id),
    CONSTRAINT fk_audit_event_actor
        FOREIGN KEY (actor_user_id) REFERENCES iam_user (id) ON DELETE SET NULL,
    CONSTRAINT chk_audit_event_result
        CHECK (result IN ('SUCCESS', 'FAILURE', 'DENIED'))
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT='安全与管理操作审计';

-- Permission seed data. Roles, the ADMIN role and the first administrator are created by the
-- explicit bootstrap entry (cwa.bootstrap.enabled + CWA_BOOTSTRAP_ADMIN_PASSWORD), never by a
-- migration with a default password.
INSERT INTO iam_permission (code, name, description, created_at) VALUES
    ('user:read', '读取用户', '查看用户列表与详情', UTC_TIMESTAMP(6)),
    ('user:write', '维护用户', '创建和更新用户资料、启用或禁用用户', UTC_TIMESTAMP(6)),
    ('user:role:write', '维护用户角色', '变更用户角色', UTC_TIMESTAMP(6)),
    ('audit:read', '读取审计', '查看安全和管理操作审计', UTC_TIMESTAMP(6));

-- Redis session key contract (informational, not SQL):
--   cwa:session:{sha256(token)}    -> session JSON, TTL equals session expiry
--   cwa:user-sessions:{userId}     -> ZSET of token digests scored by creation time
--   cwa:login-rate:{kind}:{hash}:{window} -> atomic rate-limit counters
