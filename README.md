# 实习作品集 Agent
<!-- DOCUMENT_STATUS: CURRENT_AUTHORITY -->

这是一个面向技术面试官、实习导师、HR 和普通访客的公开实习作品集。前端提供 Project、Case、时间线、证据中心和 Agent 工作台；后端只从已审核的公开 Bundle 或其受控数据库投影回答问题。

当前代码尚未生产部署。实现状态、默认开关和未完成验收以 [当前实现状态](docs/08-当前实现状态.md) 为准；内容数量只在该文档的受检快照中展示。

## 环境要求

- Java 21
- Maven 3.9+
- Node.js 22+
- npm 10+
- Docker Desktop（标准本地 PostgreSQL 模式需要）

Windows 示例使用 `mvn.cmd`、`npm.cmd` 和 PowerShell。其他系统使用对应可执行文件。

## 标准本地启动

标准本地环境使用 PostgreSQL 保存短期 Agent State，以便在开发阶段验证 Flyway、幂等、澄清、取消和进程重启后的恢复语义。

首次准备：

```powershell
Copy-Item -LiteralPath .env.postgres.example -Destination .env.postgres.local
# 填写管理员、公开库、治理库、Context 库四个密码，以及两把独立的 32 字节 Base64 密钥
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/postgres-local.ps1 bootstrap
```

日常启动：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/postgres-local.ps1 start
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1
```

数据库由 `postgres-local.ps1` 显式管理；应用启动器只检查 readiness，不会代替用户启动、停止或重置 Docker。数据库与固定 `agent_context` schema 的完整说明见 [本地 PostgreSQL 运行手册](docs/10-本地PostgreSQL与pgvector运行手册.md)。

## 其他运行模式

快速、非生产同构的内存模式：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1 -ContextMode IN_MEMORY
```

`IN_MEMORY` 与 PostgreSQL 提供相同的 Agent 行为，但状态在进程退出后消失，也不验证数据库迁移或跨重启恢复。`DISABLED` 是显式只读浏览模式：公开作品集仍可访问，Agent 状态与 Turn 执行不可用。

普通本地开发不需要模型 Secret。只有在已经完成访客数据与 Provider 审批后，才使用仓库外的 Secret 文件显式启用模型：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1 `
  -EnableGeneralAi `
  -SecretsFile C:\private\portfolio-model.env
```

生产使用 `prod` Profile，并强制 PostgreSQL；缺少数据库、schema 或加密配置时应失败关闭，不能退回内存。

## 公开 Agent API

公开 Agent 只有以下四条无版本资源：

- `POST /api/agent/turns`：创建或以同一 `requestId` 幂等重放 Turn；
- `DELETE /api/agent/turns/{requestId}`：请求取消仍在执行的 Turn；
- `GET /api/agent/conversations/current`：验证 ResumeToken 并读取当前匿名会话摘要；
- `DELETE /api/agent/conversations/current`：清理当前匿名会话。

除首次创建会话外，客户端通过 `Authorization: Bearer <ResumeToken>` 证明会话归属。成功响应和错误响应均禁止缓存；`429 RATE_LIMITED` 同时提供标准 `Retry-After` Header 与 JSON `retryAfterSeconds`。

公开内容浏览继续使用 `/api/v1` 下的只读资源；Agent API 不取代 Project、Case、Evidence 与 `public-content` 接口。

## 验证

后端：

```powershell
mvn.cmd -f backend/pom.xml test
```

前端：

```powershell
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run check
npm.cmd --prefix frontend run build
```

文档、架构、质量与隐私门：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/documentation-check.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/documentation-check.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path backend/src/main
```

完整发布候选使用：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-release.ps1
```

packaged-JAR、真实 PostgreSQL 和真实 Provider 的验收受环境与外部调用授权约束。没有新鲜证据时，不得把本地实现写成生产已验收。

## 文档入口

- [文档权威地图](docs/00-文档状态索引.md)
- [工程约束](docs/04-项目代码约束.md)
- [当前实现状态](docs/08-当前实现状态.md)
- [安全边界](SECURITY.md)
- [当前缺陷清单](<docs/15-Agent 2.0真实交互问题清单与修复边界.md>)
