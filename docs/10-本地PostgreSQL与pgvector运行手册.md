# 本地 PostgreSQL / pgvector 运行手册

> **状态：** 当前本地开发运行手册（2026-08-03）
> **边界：** 双库、Markdown 显式导入和公开数据库主检索均已实现但默认关闭；本文不构成数据库、模型或生产环境已验收的声明。

本文只描述本地开发环境。它使用一个 PostgreSQL 16 / pgvector 容器，但创建两个真正独立的数据库和两个 owner 账号：

- `portfolio_public_dev`：公开 Release、Project、Case、Claim、Evidence、检索文档和公开向量。
- `portfolio_governance_dev`：私有 Markdown 文档、revision、chunk、导入记录和私有向量。

线上公网环境只应部署公开数据库 `portfolio_public`。治理数据库、原始 Markdown、私有向量和本地模型不得部署到公网服务器。

## 1. 前置条件

- Docker Desktop，且 `docker compose version` 和 `docker info` 可成功执行。
- Java 21。
- Maven 3.9 或兼容版本。
- 可选：DBeaver、pgAdmin 或 DataGrip。

Compose 使用固定镜像 `pgvector/pgvector:0.8.5-pg16-bookworm`，只把 PostgreSQL 端口绑定到 `127.0.0.1`，不会启动 pgAdmin 或 Adminer 容器。

## 2. 首次启动

从仓库任意目录均可调用脚本。先复制环境变量模板：

```powershell
Copy-Item -LiteralPath .env.postgres.example `
  -Destination .env.postgres.local
```

在 `.env.postgres.local` 中为三个密码填写本机专用强密码。密码至少 12 位，
只使用大小写字母、数字和 `_@%+=:,./!?~-`。脚本会拒绝 `$`、引号、空格等
会改变 Compose `.env` 解析语义的字符。该文件已被 Git 忽略，不得提交。

```powershell
.\scripts\postgres-local.ps1 bootstrap
```

`bootstrap` 会：

1. 检查 Docker 和 Docker Compose；
2. 启动并等待健康检查，默认超时 90 秒；
3. 幂等创建两个 role、两个 database 和两套 `vector` extension；
4. 通过 Java `DatabaseMigrationCli` 运行现有两套 Flyway migration；
5. 不导入、更不会激活公开 Release。

数据库数据保存在 Compose named volume `portfolio-postgres-local_postgres_data`。

## 3. 本地 env 配置

默认配置文件是仓库根目录的 `.env.postgres.local`。也可以显式传入另一个本机文件，路径支持空格和中文：

```powershell
.\scripts\postgres-local.ps1 status `
  -EnvFile 'C:\local config\数据库.env'
```

脚本只把变量设置在当前脚本子进程中，不永久修改系统环境变量，也不会打印密码。

官方 PostgreSQL 镜像的 `/docker-entrypoint-initdb.d` 脚本只在空 volume 首次初始化时自动运行。修改 env 文件不会自动修改已有数据库密码。再次执行 `bootstrap` 会显式协调两个 owner 密码、数据库 ownership 和 extension；管理员密码若需轮换，应先在数据库内安全执行 `ALTER ROLE`，再同步本机 env。若允许丢弃全部本地数据，也可以使用本文的确认式 reset 后重新 bootstrap。

## 4. 状态、连接信息和验证

```powershell
.\scripts\postgres-local.ps1 status
.\scripts\postgres-local.ps1 connections
.\scripts\postgres-local.ps1 verify
```

- `status` 显示 Compose 状态，并确认两个数据库可连接。
- `connections` 只输出 host、port、database、username 和 JDBC URL；密码从本机 env 文件读取。
- `verify` 检查两个 database、`vector` extension、Flyway history、关键表、active Release、公开计数、非空 embedding 和向量自距离。它不打印治理文本、向量或 Markdown 根路径。

## 5. 公开 Bundle：校验、导入和激活

当前 Bundle 位于 `backend/src/main/resources/public-data/bundle`。只读校验不需要数据库：

```powershell
.\scripts\postgres-local.ps1 verify-public-bundle
```

导入会启动数据库、运行公开库 Flyway，并写入一个不可变 Release：

```powershell
.\scripts\postgres-local.ps1 import-public
```

命令输出 JSON，其中包含 `releaseId`。重复导入同一 Bundle 会返回已有 Release，不重复写入语义数据。激活必须在第二步精确确认 UUID：

```powershell
.\scripts\postgres-local.ps1 activate-public `
  -ReleaseId '<UUID>' `
  -ConfirmReleaseId '<相同 UUID>'
```

然后执行：

```powershell
.\scripts\postgres-local.ps1 verify
```

也可用 `bootstrap -ImportPublic` 完成迁移和导入，但仍不会默认激活。除非已经明确知道 Release UUID，否则保持“导入 → 读取 UUID → 单独激活”的两步流程。

## 6. Markdown 治理库 scan/import

scan 是只读预览，并且只处理显式指定的根目录：

```powershell
.\scripts\postgres-local.ps1 scan-markdown `
  -Root 'C:\path\to\knowledge' `
  -DryRun
```

确认 ADDED、CHANGED、UNCHANGED、MISSING、FAILED、BLOCKED 后再导入：

```powershell
.\scripts\postgres-local.ps1 import-markdown `
  -Root 'C:\path\to\knowledge'
```

工具不增加文件 watcher，不打印 Markdown 原文、向量或知识库绝对路径。若需要本地 embedding，调用前在当前终端配置：

```powershell
$env:PORTFOLIO_RETRIEVAL_PROFILE = 'HYBRID'
$env:PORTFOLIO_RETRIEVAL_MODEL_DIR = 'C:\local-models\bge'
```

不配置本地模型时，导入会保留现有 `VECTOR_PENDING` 语义。模型与私有 Markdown 均不得加入 Git。
以后配置好本地模型时，可只重试最新 revision 仍为 `VECTOR_PENDING` 的未变化文档：

```powershell
.\scripts\postgres-local.ps1 retry-markdown `
  -Root 'C:\path\to\knowledge'
```

普通 `scan-markdown` 对这些内容仍报告 `UNCHANGED`；`retry-markdown` 是显式的向量补偿入口，
不会重复处理已经 READY 的未变化文档。

## 7. 应用使用 PostgreSQL

公开运行库：

```powershell
$env:PORTFOLIO_PUBLIC_DATABASE_ENABLED = 'true'
$env:PORTFOLIO_PUBLIC_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:54329/portfolio_public_dev'
$env:PORTFOLIO_PUBLIC_DATABASE_USERNAME = 'portfolio_public_owner'
$env:PORTFOLIO_PUBLIC_DATABASE_PASSWORD = '<从本机 env 读取>'
```

治理命令额外使用：

```powershell
$env:PORTFOLIO_GOVERNANCE_DATABASE_ENABLED = 'true'
$env:PORTFOLIO_GOVERNANCE_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:54329/portfolio_governance_dev'
$env:PORTFOLIO_GOVERNANCE_DATABASE_USERNAME = 'portfolio_governance_owner'
$env:PORTFOLIO_GOVERNANCE_DATABASE_PASSWORD = '<从本机 env 读取>'
```

本地 owner 账号用于 Flyway 和导入。生产环境未来应拆分 migration、importer 和 runtime reader 凭据；公网运行时不得使用 owner 账号。

启用公开库后，作品集检索通过唯一公开 Agent 入口 `POST /api/v2/answers` 使用
PostgreSQL/pgvector 主检索，并在基础设施不可用时受控降级到随包 Bundle。不存在独立的
Selection HTTP 接口；选择策略、服务和 benchmark 仅供 Agent 内部与离线回归使用。

## 8. DBeaver、pgAdmin 和 DataGrip

先运行：

```powershell
.\scripts\postgres-local.ps1 connections
```

分别创建两个 PostgreSQL 连接。

公开库：

- Host：`127.0.0.1`
- Port：`54329`（或 env 中的覆盖值）
- Database：`portfolio_public_dev`
- Username：`portfolio_public_owner`
- Password：从 `.env.postgres.local` 读取

治理库：

- Host：`127.0.0.1`
- Port：`54329`（或 env 中的覆盖值）
- Database：`portfolio_governance_dev`
- Username：`portfolio_governance_owner`
- Password：从 `.env.postgres.local` 读取

DBeaver/DataGrip 选择 PostgreSQL 驱动并填写上述字段。pgAdmin 中依次使用 Register → Server，Connection 页填写 Host、Port、Maintenance database、Username 和本机密码。管理工具主要用于查看和诊断，不建议直接修改业务表。

## 9. 常用只读 SQL

公开库：

```sql
SELECT * FROM flyway_schema_history_public ORDER BY installed_rank;
SELECT * FROM content_release ORDER BY created_at DESC;
SELECT * FROM active_release;
SELECT count(*) FROM portfolio_subject;
SELECT count(*) FROM project_profile;
SELECT count(*) FROM case_study;
SELECT count(*) FROM claim;
SELECT count(*) FROM evidence;
SELECT count(*) FROM retrieval_document;
SELECT count(*) FROM retrieval_document WHERE embedding IS NOT NULL;
SELECT embedding <=> embedding AS self_distance
FROM retrieval_document
WHERE embedding IS NOT NULL
LIMIT 1;
SELECT extversion FROM pg_extension WHERE extname = 'vector';
```

治理库：

```sql
SELECT * FROM flyway_schema_history_governance ORDER BY installed_rank;
SELECT lifecycle_status, count(*) FROM source_document GROUP BY lifecycle_status;
SELECT parse_status, count(*) FROM source_revision GROUP BY parse_status;
SELECT vector_status, count(*) FROM source_chunk GROUP BY vector_status;
SELECT extversion FROM pg_extension WHERE extname = 'vector';
```

表结构由 Flyway 管理；公开数据由 Bundle Import/Activate 管理；Markdown 数据由 scan/import 管理。

## 10. 停止、重启和安全 reset

停止但保留 volume：

```powershell
.\scripts\postgres-local.ps1 stop
.\scripts\postgres-local.ps1 start
```

只有精确确认字符串才允许删除本项目的 Compose volume：

```powershell
.\scripts\postgres-local.ps1 reset `
  -Confirm RESET-PORTFOLIO-LOCAL
```

脚本会先打印 Compose project 和精确 volume 名称，只执行当前项目的 `docker compose down --volumes`。它不会使用 `docker volume prune`、`docker system prune`，也不会删除其他项目的容器或 volume。

## 11. 常见故障

- `POSTGRES_LOCAL_DOCKER_MISSING`：安装 Docker Desktop，并重新打开终端。
- `POSTGRES_LOCAL_DOCKER_UNAVAILABLE`：启动 Docker Desktop，等待引擎就绪。
- `POSTGRES_LOCAL_HEALTH_TIMEOUT`：运行 `docker compose --env-file .env.postgres.local -f compose.postgres.local.yml -p portfolio-postgres-local logs postgres` 查看 PostgreSQL 日志；不要复制含敏感信息的 env 内容。
- `POSTGRES_LOCAL_ENV_FILE_MISSING`：从示例复制 `.env.postgres.local`。
- `POSTGRES_LOCAL_REQUIRED_ENV_MISSING`：必填账号、数据库名或密码为空。
- `DATABASE_MIGRATION_FAILED`：确认两个 JDBC URL、账号密码和数据库 ownership，再重跑 `bootstrap`。
- `PUBLIC_BUNDLE_IMPORT_FAILED`：先运行 `verify-public-bundle`，再检查公开库 Flyway 和约束。
- `MARKDOWN_COMMAND_FAILED`：确认 Root 存在、仅包含预期 Markdown；需要 embedding 时检查本地模型配置。

## 12. 生产环境边界

- 公网只部署 `portfolio_public`，不部署 `portfolio_governance`。
- 原始 Markdown、私有 embedding、本地模型路径和治理凭据不得进入镜像、Git、日志或公网服务器。
- PostgreSQL 本地端口只监听 `127.0.0.1`。
- 生产运行账号必须是最小权限 reader，不得沿用本地 owner。
- 当前工具不负责线上部署，也不为本地便利大规模重构现有数据源。

## 13. 数据库与容量评审观察入口

真实 PostgreSQL/pgvector 容量、模型性能、单实例限流与幂等边界、多实例触发条件，以及公开数据库成为主存储前的验收要求，集中记录在 [`12-工程质量与未来优化评审备忘录.md`](12-工程质量与未来优化评审备忘录.md)。这些内容是未来条件判断，不改变本手册的本地运行范围，也不表示数据库已经生产启用。
