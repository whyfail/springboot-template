# 运维手册

## 进程与端口

| 端口 | 用途 |
| --- | --- |
| 8080（`SERVER_PORT`） | 业务 API（`/api/v1/**`） |
| 9090（`MANAGEMENT_PORT`） | Actuator：`health`、`info`、`metrics`、`prometheus` |

管理端口独立于业务端口。生产网络策略只允许平台与监控系统访问 9090；`health.show-details=never`，不泄露连接信息。

## 探针

| 探针 | URL | 说明 |
| --- | --- | --- |
| readiness | `GET :9090/actuator/health/readiness` | 包含 `readinessState + db + redis`；MySQL/Redis 故障即 DOWN |
| liveness | `GET :9090/actuator/health/liveness` | 仅进程存活 |

Kubernetes 示例：

```yaml
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: 9090 }
  periodSeconds: 10
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: 9090 }
  periodSeconds: 15
```

本地 compose 将管理端口映射为宿主 9091（`9091:9090`），宿主 9090 常与本地代理冲突。容器要求：非 root（uid 10001）、只读根文件系统 + `/tmp` tmpfs、`JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`。

## 必填配置

默认（生产）profile 下以下变量缺失即启动失败，不允许默认值：

- `DB_URL`（必须含 `connectionTimeZone=UTC`）、`DB_USERNAME`、`DB_PASSWORD`
- `REDIS_URL`

可选：`DB_POOL_MAX`（默认 20）、`DB_POOL_MIN`（5）、`SERVER_PORT`、`MANAGEMENT_PORT`、`OTLP_ENDPOINT`、`OTLP_EXPORT_ENABLED`（默认 false）、`TRACING_ENABLED`。

安全相关（`cwa.security.*`）：`session-ttl=8h`、`remembered-session-ttl=30d`、`max-sessions-per-user=5`、`bcrypt-strength=12`、`login-attempts-per-minute=5`、`login-attempts-per-hour=20`、`allowed-origins`（生产必须显式设置，不得通配）。

## 首管理员 bootstrap

迁移不创建任何管理员。创建首管理员：

```bash
CWA_BOOTSTRAP_ENABLED=true \
CWA_BOOTSTRAP_ADMIN_USERNAME=admin \
CWA_BOOTSTRAP_ADMIN_PASSWORD='<≥12字符>' \
java -jar app.jar
```

- 密码缺失或 <12 字符 → 启动失败（显式报错，不降级）。
- 用户名已存在 → 跳过（幂等）。
- 创建内容：`ADMIN` 系统角色 + 全部权限 + 管理员用户 + `ADMIN_BOOTSTRAPPED` 审计。
- **完成后关闭入口**：移除 `CWA_BOOTSTRAP_ENABLED=true`，重启后该代码路径不再激活。

## 日志

结构化 JSON（logstash 格式）输出到 stdout。字段：`@timestamp/level/logger/message` + MDC `requestId/traceId/spanId/service/environment`；访问日志 `event=http_access` 另含 `method/pathTemplate/status/durationMs/principalId`。

- `pathTemplate` 是路由模板（如 `/api/v1/users/{publicId}`），不记录原始 URL，防止标识符入日志。
- 5xx 记录完整堆栈（仅服务端）；响应体不返回堆栈与 SQL。
- 密码、Token、Authorization、Cookie 永不落日志。

## 指标与追踪

- Prometheus：`GET :9090/actuator/prometheus`。业务指标 `cwa.auth.login{result=success|failure|rate_limited|session_store_error}`（无用户维度标签）。
- Tracing：`TRACING_ENABLED=true` + `OTLP_ENDPOINT=http://otel-collector:4318` + `OTLP_EXPORT_ENABLED=true`。业务代码只使用 Micrometer Observation，不直接依赖 OTel API。

## 限流

- 维度：单 IP 与单账号，各 5 次/分钟、20 次/小时（Lua 原子计数，键含哈希与时间桶）。
- 超限返回 429 + `Retry-After`（秒）。
- 成功登录清空该账号计数；IP 计数保留。安全审计记录保留全部失败。

## 优雅停机

`server.shutdown=graceful`（等待在途请求完成）。Kubernetes 停止流程：摘流 → SIGTERM → 等待就绪探针翻红 → 退出。

## 本地 Compose

`compose.yml` 提供 MySQL 8.4、Redis 8、应用三个服务，均带 healthcheck，应用依赖数据库/缓存 healthy 才启动。应用容器以 `local` profile 运行并启用 bootstrap（密码来自 `.env`，无默认值）。数据在具名卷 `mysql-data`、`redis-data`；`docker compose down -v` 清空。

## 故障排查速查

| 现象 | 排查 |
| --- | --- |
| 启动即失败 `DB_URL` | 生产 profile 缺必需环境变量 |
| readiness DOWN | 查看 management 端口 `/actuator/health` 组成（db/redis） |
| 登录 429 | 命中限流；确认来源 IP 是否共享 |
| 登录 503 `SESSION_STORE_UNAVAILABLE` | Redis 不可用，鉴权失败关闭；恢复 Redis |
| 409 `VERSION_CONFLICT` | 乐观锁冲突，刷新后重试 |
