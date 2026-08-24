# Scripts 目录说明

本目录集中存放项目的本地开发、数据库管理、质量检查、验收评测和公开内容治理脚本。用户可直接执行的稳定入口保留在 `scripts/` 根目录；内部模块、测试夹具和特定基础设施脚本按职责放入子目录。

## 目录分配

- 根目录：稳定入口及其同名测试。
- `logging/`：本地日志内部模块。
- `postgres/`：由 PostgreSQL 容器调用的初始化脚本。
- `provider-probe/`：真实 Provider 探针及其测试。
- `test-fixtures/`：脚本自测使用的假服务和故障夹具。
- `migrations/`：绑定特定历史版本的数据生成、迁移脚本及其同名测试。

## 文件约定

- `*.ps1`：Windows PowerShell 入口或工具脚本。
- `*.mjs`：Node.js 数据生成或迁移脚本。
- `*.test.ps1`、`*.test.mjs`：对应脚本的自测，通常不作为日常入口。
- `*.psm1`：供其他 PowerShell 脚本导入的内部模块，不单独执行。
- `*.sh`：由 Linux 容器执行的初始化脚本。

## 功能分类

| 分类 | 主要脚本 | 用途 |
| --- | --- | --- |
| 本地开发 | `start-local.ps1`、`start-frontend.ps1` | 启动本地前后端，检查依赖、端口和运行配置。 |
| PostgreSQL | `postgres-local.ps1`、`postgres/` | 启动、初始化、检查和维护本地 PostgreSQL。 |
| 本地日志 | `watch-local-logs.ps1`、`archive-local-logs.ps1`、`logging/` | 汇总、查看、归档和清理本地日志。 |
| 工程门禁 | `code-quality-check.ps1`、`architecture-check.ps1`、`documentation-check.ps1`、`privacy-check.ps1` | 检查代码规则、模块依赖、文档事实和隐私边界。 |
| 发布验证 | `verify-release.ps1`、`verify-static-bundle.ps1`、`run-jar-e2e.ps1` | 构建并验证发布候选、JAR 静态资源和浏览器/API 路径。 |
| Agent 分层证据 | `write-agent-verification-summary.ps1` | 分开报告确定性、场景运行时、Browser contract/body、PostgreSQL/JVM restart 与 Provider Quality；未执行层不得汇总为 PASS。 |
| Agent 验收 | `run-agent-behavior-audit.ps1`、`assert-live-*.ps1`、`provider-probe/` | 验证 Agent 行为、公开响应及显式授权的真实 Provider 路径。 |
| 评测 | `run-eval.ps1`、`run-eval-offline.ps1` | 执行评测 CLI 和确定性的离线评测。 |
| 内容治理 | `portfolio-governance.ps1`、`build-retrieval-bundle.ps1`、`import-public-release.ps1` | 准备、审核、验证和导入公开内容发布包。 |
| 本地模型 | `install-local-embedding-model.ps1` | 下载或复制固定模型，并校验文件大小和哈希。 |
| 数据演进 | `migrations/build-public-asset-expansion.mjs`、`migrations/migrate-*.mjs` | 面向特定历史版本的数据生成或迁移，不是日常命令。 |
| 测试支持 | `test-fixtures/`、同名 `*.test.*` | 为脚本自测提供假服务、故障场景和回归用例。 |

## 常用入口

标准本地开发通常只需要：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/postgres-local.ps1 start
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1
```

完整发布候选验证使用：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-release.ps1
```

更多参数和运行前提以仓库根目录 `README.md`、`docs/06-公开内容发布运行手册.md` 和 `docs/10-本地PostgreSQL与pgvector运行手册.md` 为准。

## 使用注意

- 不要把 `.test.*`、`logging/*.psm1` 或 `test-fixtures/` 当作日常入口。
- `postgres-local.ps1 reset` 会删除本地数据库卷，必须使用脚本要求的确认值。
- `portfolio-governance.ps1 publish`、`rollback` 和公开 Release 导入属于显式治理操作。
- `migrations/migrate-public-portfolio-schema-v4.mjs` 会改写公开作品集数据，只应用于它声明的源版本。
- 真实 Provider 验收必须显式授权，并使用仓库外的 Secret 文件。
- 不确定脚本用途时，先阅读参数定义、同名测试和对应运行手册，不要直接执行。
