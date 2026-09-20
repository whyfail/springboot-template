# AGENTS.md — 工程维护规则

本文件约束 AI 助手与团队成员对本仓库的一切修改。任务完成前必须通过 `./mvnw clean verify`。

## 代码组织

- 按业务域放代码：`auth`、`user`、`authorization`、`audit`、`shared`。禁止创建项目级 `controller/service/repository` 大目录。
- 每个域内部结构固定：`api`（HTTP 映射与 DTO）、`application`（用例与事务）、`domain`（实体、仓储接口、领域规则）、`infrastructure`（外部适配）。
- `shared` 只存放跨域基础设施；禁止把业务代码堆进去。
- ArchUnit（`ArchitectureTest`）强制以上边界，新增结构前先看规则。

## 编码规范

- 所有依赖使用构造器注入，字段为 `private final`；禁止字段注入（`@Autowired` 注解字段被 ArchUnit 禁止）。
- 用 Java record 表示不可变请求、响应与配置对象；不引入 Lombok。
- Controller 只做 HTTP 映射、校验与 DTO 转换；事务全部位于应用服务（`@Transactional` 只出现在 application 层）。
- JPA Entity 不得进入 API 响应；API 只使用 DTO（ArchUnit 强制）。
- SQL/JPQL 排序与过滤字段必须走服务端白名单（`PageRequestFactory`、`UserSpecifications`）。

## 安全红线

- 原始密码、Token、密钥禁止写入日志、审计 details、异常消息、指标标签或任何示例配置。
- Redis 只保存 Token 的 SHA-256 摘要；数据库只保存密码哈希（`{bcrypt}...`）。
- 禁止硬编码默认管理员密码；首位管理员只能通过 `app.bootstrap.enabled=true` + `APP_BOOTSTRAP_ADMIN_PASSWORD` 显式创建。
- 生产配置禁止给数据库密码、管理账号密码提供默认值，禁止 CORS 通配符。
- 登录失败提示统一为「账号或密码错误」，避免账号枚举。
- 修改 Cookie 鉴权方案前必须重新评估 CSRF（当前 Bearer API 无 Cookie，CSRF 关闭）。

## 数据库

- 表结构只能通过新增 Flyway migration 演进（`src/main/resources/db/migration`）；禁止修改已发布的 migration。
- JPA 保持 `ddl-auto=validate`；结构变更必须先写迁移再改实体。
- 主键 `BIGINT UNSIGNED AUTO_INCREMENT`，对外标识 `BINARY(16)` UUID，时间 UTC `DATETIME(6)`。

## 接口演进

- 新增或修改接口必须同步更新：DTO Bean Validation、`openapi.yaml`、方法级权限（`@PreAuthorize`）、错误码、测试（单测 + 切片 + 集成）、契约测试（`OpenApiContractIT`）。
- 响应成功结构不加统一 envelope；错误一律 Problem Details，顶层带 `code`、`msg`、`requestId`。
- 分页契约：`items/page/size/totalElements/totalPages`，`page` 从 0 开始，`size` 最大 100。

## 提交规范

- 提交信息使用 Conventional Commits：`<类型>(<范围>)?: <描述>`，类型枚举与前端模板一致，标题行 ≤100 字符；`githooks/commit-msg` 钩子强制校验，CI 双重校验。

## 质量门禁

- 提交前运行 `./mvnw clean verify`（Spotless 格式、Enforcer、单元 + Testcontainers 集成测试、JaCoCo 覆盖率、ArchUnit）。
- 覆盖率门禁：全局行 ≥80% / 分支 ≥70%；`auth`/`user`/`authorization` 域行 ≥90%；`auth.application` 分支 ≥85%。禁止靠排除核心包、删除断言或降低阈值通过。
- 测试稳定优先：时间用注入的 `Clock`，随机用 `TokenGenerator` 接口注入。
