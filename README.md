<div align="center">
  <h1>✨ Spring Boot 企业级后端模板 ✨</h1>

  <p>
    🚀 为 AI 而生的企业级 Spring Boot 后端模板<br>
    📦 模块化单体、opaque token 会话、限流审计，开箱即用<br>
    🔧 契约先行、Testcontainers 真实依赖测试、可验证质量门禁
  </p>

  <div>
    <a href="https://spring.io/projects/spring-boot" target="_blank">
      <img src="https://img.shields.io/badge/Spring_Boot_4.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot" />
    </a>
    <a href="https://openjdk.org/" target="_blank">
      <img src="https://img.shields.io/badge/Java_25-437291?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 25" />
    </a>
    <a href="https://www.mysql.com/" target="_blank">
      <img src="https://img.shields.io/badge/MySQL_8.4-4479A1?style=for-the-badge&logo=mysql&logoColor=white" alt="MySQL" />
    </a>
    <a href="https://redis.io/" target="_blank">
      <img src="https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white" alt="Redis" />
    </a>
    <a href="https://testcontainers.com/" target="_blank">
      <img src="https://img.shields.io/badge/Testcontainers-24B374?style=for-the-badge&logo=testcontainersorg&logoColor=white" alt="Testcontainers" />
    </a>
    <a href="https://maven.apache.org/" target="_blank">
      <img src="https://img.shields.io/badge/Maven-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white" alt="Maven" />
    </a>
  </div>

---

</div>

一个专为 AI 协作编码设计的企业级 Spring Boot 后端模板。模板通过 `AGENTS.md`、`auth/user/authorization/audit/shared` 业务域分层、opaque token 会话和可验证质量门禁，把架构边界、安全约定和测试策略明确写进项目本身，为 `vite_react_init`、`vite_vue3_init`、`vite_react_ssr_init`、`vite_vue3_ssr_init` 四套前端提供登录、鉴权、用户角色管理与可观测性基础能力。

> ⚠️ **本仓库是模板源码**：Java 包名与项目名使用 `{{ package }}` / `{{ name }}` 占位符（与前端模板的 `{{ name }}` 一致），**不能直接编译**。请先通过 [cwa-stack](https://www.npmjs.com/package/cwa-stack) 或 [`scripts/generate.sh`](scripts/generate.sh) 生成自己的项目，生成后即为可运行的完整工程。

HTTP 契约：[`openapi.yaml`](./openapi.yaml)（OpenAPI 3.1）。数据模型基线：`src/main/resources/db/migration/V1__init_iam.sql`。

## 生成项目

**方式一：cwa-stack（推荐，与前端模板同一入口）**

```bash
# 管道符一行生成：模板名\n项目名\nJava包名\n项目描述（可选）
echo -e "spring-boot\nmy-backend\ncom.mycompany.mybackend\n我的后端服务" | npx cwa-stack create
```

**方式二：本仓库自带的生成脚本**

```bash
git clone https://github.com/whyfail/springboot-template.git
cd springboot-template
./scripts/generate.sh -n my-backend -p com.mycompany.mybackend -d ../my-backend
cd ../my-backend
```

生成的项目里，`{{ package }}` / `{{ name }}` 已被替换为你的 Java 包名与项目名，直接按下面步骤启动。

## 提交规范

仓库通过 `githooks/commit-msg` 强制 [Conventional Commits](https://www.conventionalcommits.org/)（与前端模板规则一致）。跑过任意一次 `./mvnw` 后钩子自动生效（Maven 通过 `core.hooksPath` 安装；需要项目已完成 `git init`）：

```text
<类型>(<范围>)?: <描述>

feat: 添加新功能          fix(auth): 修复会话撤销
docs: 更新文档            refactor(user): 抽取分页逻辑
perf/test/build/ci/chore/types/style/revert/wip/release 同理
```

类型枚举与前端对齐：`feat|fix|docs|style|refactor|perf|test|build|ci|chore|types|wip|release`；标题行不超过 100 字符；破坏性变更用 `!` 标记。CI 会校验推送范围内的全部提交信息。

## 技术基线

| 类别 | 选型 |
| --- | --- |
| Java | 25 LTS（Maven Enforcer 强制 `[25,26)`） |
| 框架 | Spring Boot 4.1.1（Spring Framework 7 / Spring Security 7.1 / Hibernate 7） |
| 构建 | Maven Wrapper 3.9.11（统一使用 `./mvnw`） |
| Web | `spring-boot-starter-webmvc`（MVC + Tomcat，无 WebFlux） |
| 数据 | MySQL 8.4 + Spring Data JPA + Flyway 12.4 |
| 会话 | Redis（opaque token，只存 SHA-256 摘要） |
| 文档 | springdoc-openapi 3.1.1（生产默认关闭 UI） |
| 可观测 | Actuator + Micrometer（Prometheus + OTLP tracing）+ 结构化 JSON 日志 |
| 测试 | JUnit、Mockito、AssertJ、Testcontainers（真实 MySQL/Redis）、ArchUnit |
| 门禁 | Spotless、Maven Enforcer、JaCoCo（行 ≥80%/分支 ≥70%，核心域行 ≥90%） |

版本原则：Spring 生态由 Boot BOM 管理；BOM 未管理的依赖仅 `springdoc-openapi 3.1.1`（Boot 4 兼容线）与 `archunit-junit5 1.5.0`（本机 JDK 25 支持线）。

## 运行要求

- JDK 25、Docker（Testcontainers 集成测试需要）。
- 本地启动：Docker（或 colima）。

## 10 分钟本地启动（在生成的项目里）

```bash

# 1. 准备环境变量（会强制要求填写三个密码，无默认值）
cp .env.example .env
# 编辑 .env：MYSQL_ROOT_PASSWORD / MYSQL_APP_PASSWORD / BOOTSTRAP_ADMIN_PASSWORD

# 2. 启动 MySQL + Redis + 应用
docker compose up -d --build
docker compose ps                    # 三个服务 healthy

# 3. readiness 探针（独立管理端口 9090）
curl --fail http://localhost:9091/actuator/health/readiness   # 宿主 9091 -> 容器 9090

# 4. 首管理员已由 bootstrap 创建（compose 使用 local profile + APP_BOOTSTRAP_ENABLED=true）
curl -s http://localhost:8080/api/v1/login \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"$(grep BOOTSTRAP_ADMIN_PASSWORD .env | cut -d= -f2)\"}"
```

登录响应顶层直接是 `token`：

```json
{
  "token": "43-char-url-safe-token",
  "tokenType": "Bearer",
  "expiresAt": "2026-09-18T18:00:00Z",
  "user": { "publicId": "...", "username": "admin", "roles": ["ADMIN"], "permissions": ["user:read", "..."] }
}
```

后续请求携带 `Authorization: Bearer <token>`。

## 必填环境变量（生产 / 默认 profile）

| 变量 | 说明 |
| --- | --- |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | MySQL 连接（必须含 `connectionTimeZone=UTC`） |
| `REDIS_URL` | 例如 `redis://host:6379` |
| `SERVER_PORT`（默认 8080）、`MANAGEMENT_PORT`（默认 9090） | 业务端口与管理端口分离 |
| `APP_BOOTSTRAP_ENABLED` | 默认 `false`；开启时必须同时提供下一项 |
| `APP_BOOTSTRAP_ADMIN_PASSWORD` | ≥12 字符；bootstrap 完成后关闭入口 |
| `OTLP_ENDPOINT` / `OTLP_EXPORT_ENABLED` | OTLP tracing 输出，默认关闭 |
| `APP_ALLOWED_ORIGINS` | 无此默认时使用 application.yml 的本地白名单；生产必须显式设置 |

`local` profile 为本地开发提供了连接默认值（见 `application-local.yml`）；生产 profile 无任何凭据默认值，缺失即启动失败。

## 常用命令

```bash
./mvnw clean verify                 # 全量门禁：格式 + Enforcer + 单测 + 集成 + 覆盖率 + ArchUnit
./mvnw -q test                      # 仅单元/切片测试
./mvnw -q verify -DskipUnitTests    # 仅集成测试（跳过覆盖率门禁）
./mvnw -q spotless:apply            # 自动格式化
docker compose up -d --build        # 本地全栈
docker compose down -v              # 停止并清空数据卷
```

覆盖率报告：`target/site/jacoco/index.html`。

## 与四套前端接入

- **SPA（React/Vue）**：`VITE_API_BASE=/api/v1`、`VITE_API_TARGET=http://localhost:8080`；登录字段 `username/password/remember`，兼容旧字段 `name/checked`；取响应顶层 `token` 走 `Authorization: Bearer`。
- **SSR（Next/Nuxt）**：BFF 调 `/api/v1/login`，把 `token` 写入 `auth_token` HttpOnly Cookie（`SameSite=Lax`、生产加 `Secure`，`Max-Age` 不超过 `expiresAt`）；服务端读 Cookie 转 Bearer。

完整示例（含 axios、Next.js Route Handler、Nuxt server/api 代码）见 [docs/frontend-integration.md](./docs/frontend-integration.md)。

## 文档入口

- [docs/architecture.md](./docs/architecture.md) — 模块化单体结构、依赖方向、会话与限流设计。
- [docs/frontend-integration.md](./docs/frontend-integration.md) — 四套前端接入示例。
- [docs/operations.md](./docs/operations.md) — 配置、探针、日志/指标/tracing、bootstrap 流程、Kubernetes 要点。
- [docs/security.md](./docs/security.md) — 密码策略、Token 模型、限流、CORS、安全头、红线清单。
- [AGENTS.md](./AGENTS.md) — AI 与团队成员的共同维护规则。

## 端点与权限

| 端点 | 权限 |
| --- | --- |
| `POST /api/v1/login` | 公开（IP + 账号双重限流：5 次/分钟、20 次/小时） |
| `GET /api/v1/me`、`POST /api/v1/logout` | 已登录 |
| `GET /api/v1/users`、`GET /api/v1/users/{publicId}` | `user:read` |
| `POST /api/v1/users`、`PATCH /api/v1/users/{publicId}`、`PUT /api/v1/users/{publicId}/enabled` | `user:write` |
| `PUT /api/v1/users/{publicId}/roles` | `user:role:write` |

Swagger UI（local/test profile）：`http://localhost:8080/swagger-ui.html`。Actuator 在管理端口 9090（`/actuator/health/readiness`、`/actuator/health/liveness`、`/actuator/prometheus`）。

## 生产注意事项

- 容器以非 root（uid 10001）运行，只读根文件系统 + `/tmp` tmpfs。
- 只通过 Flyway 变更表结构；JPA `ddl-auto=validate`。
- readiness 包含 MySQL 与 Redis 健康状态；Redis 故障时鉴权失败关闭（503），绝不退化为匿名。
- 密码/Token/密钥不出现在日志、审计、指标与响应中；详见 docs/security.md。
