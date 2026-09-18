# 安全设计

## 密码

- `DelegatingPasswordEncoder` 统一管理，存储格式 `{bcrypt}$2a$12$...`（算法标识在哈希内，支持平滑迁移）。
- bcrypt 强度默认 12（`cwa.security.bcrypt-strength`，4..15）。启动时运行单次哈希基准（`PasswordHashBenchmark`），超 1.5s 记录警告。
- 创建用户密码 12..128 字符（登录接口按契约 8..128）；不自动 trim 密码。
- 未知用户登录同样执行一次哈希比对（dummy 哈希），消除用户名枚举的时延侧信道。
- 登录失败统一 `账号或密码错误`（`AUTH_INVALID_CREDENTIALS`），不区分「用户不存在/密码错误/账号禁用」。

## Token 与会话

- opaque token：256-bit SecureRandom，Base64URL 无填充（43 字符）。
- Redis 只存 `SHA-256(token)` 摘要（`cwa:session:{digest}`），原始 Token 不入 Redis/数据库/日志/审计/指标。
- 普通会话 8h，remember 30d；同用户最多 5 个会话，Lua 原子驱逐最旧。
- 注销幂等；禁用用户与角色变更撤销该用户全部会话。
- Redis 故障时鉴权失败关闭：认证路径返回 503 `SESSION_STORE_UNAVAILABLE`，绝不退化为匿名。
- 服务无状态（`STATELESS`），不创建 HTTP Session；禁止 query string 传 Token。

## 限流

- 登录：IP 维度 5/min + 20/h，账号维度 5/min + 20/h。Lua 脚本原子 INCR+EXPIRE，无先读后写竞态。
- 限流键对主体做 SHA-256 哈希（`cwa:login-rate:{kind}:{hash}:{bucket}`），Redis 中无原始 IP/用户名。
- 429 携带 `Retry-After`。高风险管理操作可复用 `LoginRateLimiter` 模式扩展。

## 审计

`audit_event` 记录：LOGIN_SUCCESS/LOGIN_FAILURE/LOGOUT/USER_CREATED/USER_UPDATED/USER_ROLES_CHANGED/USER_ENABLED/USER_DISABLED/ADMIN_BOOTSTRAPPED，result ∈ SUCCESS/FAILURE/DENIED（数据库 CHECK）。

- `client_ip_hash`/`user_agent_hash` 为 SHA-256 摘要；`details_json` 为脱敏 JSON（服务端强制剔除 password/token/passwordHash 键）。
- 审计与业务写入同事务（创建用户时用户与审计原子提交）。

## Web 安全

- CSRF 关闭：Bearer API 无 Cookie。若未来引入 Cookie 鉴权必须重新启用 CSRF。
- CORS 显式白名单（`cwa.security.allowed-origins`），无 `*`、无凭据模式；仅 GET/POST/PUT/PATCH/DELETE/OPTIONS 与 `Authorization/Content-Type/X-Request-ID` 头。
- 安全头：`X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy: strict-origin-when-cross-origin`、`Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`。
- 请求限制：`max-http-request-header-size=16KB`、`max-swallow-size=2MB`；上传/大请求由网关限制。
- Actuator 在独立管理端口（默认 9090），`show-details: never`；`health/readiness/liveness` 公开，其余端点仅内网暴露。
- Swagger UI 仅 local/test profile 开放（生产 404/401）。

## 秘密管理

- 全部凭据来自环境变量/Secret 注入；`.env.example` 只有占位符；`.env` 已 gitignore。
- 生产 profile 无任何默认密码；缺失必需变量启动即失败。
- 仓库内不存在真实密钥、Token、数据库密码、生产域名。
- bootstrap 入口默认关闭，仅显式开启时激活，使用后必须关闭。

## 与 BFF 的边界

后端 API 不使用 Cookie。SSR Token 生命周期（HttpOnly/SameSite/Secure/Max-Age）由前端 BFF 承担，约定见 docs/frontend-integration.md。后端永不读取 Cookie。
