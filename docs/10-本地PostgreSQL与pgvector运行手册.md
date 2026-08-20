# 本地 PostgreSQL / pgvector 运行手册
<!-- DOCUMENT_STATUS: CURRENT_AUTHORITY -->

> **核对日期：** 2026-08-19
> **边界：** 只描述本地开发。数据库必须显式管理，应用启动器只检查 readiness，不代管 Docker 生命周期。

## 数据库职责

一个本地 PostgreSQL/pgvector 容器承载三个相互隔离的数据库和 owner：

- `portfolio_public_dev`：可选的公开内容 release 与检索投影；
- `portfolio_governance_dev`：可选的私有 Markdown 扫描和治理导入；
- `portfolio_context_dev`：标准本地 Agent State。

Agent State schema 固定为 `agent_context`，由 Flyway 管理，不能通过本地 env 自定义。生产公网不得部署治理数据库、原始 Markdown、私有向量或本地审核工作区。

## 首次准备

需要 Docker Desktop、Java 21 和 Maven。复制模板：

```powershell
Copy-Item -LiteralPath .env.postgres.example -Destination .env.postgres.local
```

在 `.env.postgres.local` 中填写本机专用密码和两把独立的 32 字节 Base64 密钥。该文件已被 Git 忽略，不得提交。Token key 与 payload key 不得相同；密钥应跨本地重启保持稳定，否则无法验证恢复语义。

初始化容器、账号、数据库和 Flyway schema：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/postgres-local.ps1 bootstrap
```

`bootstrap` 可重复执行，用于 reconcile 和 migration；它不会自动导入或激活公开内容，除非显式传入对应开关。

## 日常生命周期

启动并保留已有 volume：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/postgres-local.ps1 start
```

检查容器、三个数据库和 Agent State：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/postgres-local.ps1 status
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/postgres-local.ps1 check-context
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/postgres-local.ps1 verify
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/postgres-local.ps1 connections
```

停止容器但保留 volume：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/postgres-local.ps1 stop
```

`start-local.ps1` 不会隐式执行上述命令。PostgreSQL 未启动、schema 未迁移或 env 不完整时，它会明确失败并提示恢复动作；应用退出也不会停止数据库。

## 启动应用

标准本地模式：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1
```

指定其他本地配置文件：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1 `
  -PostgresEnvFile C:\private\portfolio-postgres.env
```

快速内存模式不连接 Agent State 数据库：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1 -ContextMode IN_MEMORY
```

`IN_MEMORY` 只适合单元测试、轻量前端开发和专项排障；进程退出后状态消失。`DISABLED` 是显式只读浏览模式，不是无状态 Agent 模式。普通确定性开发不需要 Provider Secret；只有 `-EnableGeneralAi` 时才要求仓库外 Secret 文件。

## State 生命周期

- Turn claim lease：35 秒；
- Clarification Challenge：5 分钟 absolute TTL；
- Conversation Session、Continuation Context、PublicAgentTurn replay 与终局记录：30 分钟 absolute TTL；
- key rotation retention：至少覆盖 30 分钟 TTL 和 cleanup 延迟；
- cleanup：小批量删除过期和孤儿状态，读取不续期。

数据库不得保存原始问题、ConversationWindow、Prompt、模型原始输出或私有 Evidence。完整边界见 [安全策略](../SECURITY.md)。

## 可选的公开与治理流程

验证随包公开内容不需要启动数据库：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/postgres-local.ps1 verify-public-bundle
```

导入公开 release 后，必须使用明确且相同的 release ID 二次确认激活。治理 Markdown 的 `scan-markdown`、`import-markdown` 与 `retry-markdown` 必须显式传入仓库外私有根目录；普通应用启动不会触发这些操作。

## Reset

Reset 会删除本地 Compose volume，不能出现在普通启动路径中。只有确认目标是本项目本地 volume 时执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/postgres-local.ps1 reset `
  -Confirm RESET-PORTFOLIO-LOCAL
```

Reset 后重新运行 `bootstrap`。任何生产环境都不得复用本地 reset 流程。
