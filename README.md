# 实习作品集 Agent

> **项目状态（2026-08-03）：** 当前随包运行时为 schema `4.0`、内容版本 `2026-07-29.1` 的七文件检索包：5 个 Project、49 个 Case、3 个 Collection、79 个 Claim、59 个 APPROVED Evidence、79 条 Claim–Evidence 关联、79 个检索 chunk、11 条 TimelineEvent 和 16 个 QuestionPreset。独立 Case 目录/详情、旧地址规范重定向、Case → Agent 页面内存交接、结构化诊断、PostgreSQL 公开快照、Markdown 增量导入，以及通过 `POST /api/v2/answers` 接入的作品集硬路由和无状态推荐均已进入当前代码；数据库、混合检索和模型能力默认关闭。当前仍未生产部署，也没有真实 Provider、PostgreSQL 主检索或线上数据的生产验收结论。详见 [`docs/08-当前实现状态.md`](docs/08-当前实现状态.md)。

一个面向技术面试官、实习导师、HR 和普通访客的交互式实习作品集。系统只展示经人工审核的公开事实，提供 Project/Case 浏览、证据追溯、确定性问答，以及在显式审批后才可启用的模型表达、本地检索和 PostgreSQL 组合推荐能力。

## 当前范围

- Vue 3 八个正式路由：概览、项目目录、项目详情、Case 目录、Case 详情、时间线、证据中心和完整 Agent 工作台
- Spring Boot 公开作品集 API，以及供正式页面使用的 `GET /api/v1/public-content` 聚合接口
- 独立 Case 领域模型、严格校验、只读服务、列表/详情 API、组合筛选、规范重定向和 Agent 交接
- 全量资产生成器：把获批公开资产编译为 5 个 Project、49 个 Case、3 个 Collection、脱敏 Claim/Evidence 和检索基准，且发布前必须经过人工批准
- 共享 Case 目录模型可按长期主线、单体任务、问题处理、知识与评测分组；故事型精选 Case 属于后续增强
- 公开 Bundle 包含 16 个 QuestionPreset 和 11 条 TimelineEvent；Case 专属预设由 Agent 后端执行
- 公开快照启动校验、APPROVED Evidence 过滤、项目/Evidence/Timeline 交叉引用
- 首页轻问答、Agent 真实 API 接线、错误重试、页面内存会话和响应式抽屉
- 请求关联 ID、结构化生产日志、封闭错误码和默认关闭的前端诊断入口
- 单个可执行 JAR、Docker 构建定义和 packaged-JAR Playwright 联调
- 可选的 DeepSeek V4 Flash 或 GLM-4.7 单 Provider；用于受约束分类、通用对话或基于已验证公开材料的表达，完整校验失败即安全降级
- 可选的本地 BGE-small-zh-v1.5 INT8 ONNX 混合检索；随包使用 `retrieval-policy-v2.1-query-risk`，文档向量在发布期生成，访客查询只在本机向量化
- 固定六类只读公开工具与页面内存引用式多轮；只传稳定公开 ID 和意图，不传历史问答正文
- 默认关闭的 PostgreSQL/pgvector 双库：公开运行库只读 active release，私有治理库负责显式 Markdown 扫描与增量导入
- Agent 内部确定性资产组合推荐：PostgreSQL 候选召回、2～5 项受约束组合、迁移完整度和 R0～R4 同口径基准
- 代码质量、架构、隐私、静态 bundle 与发布验证脚本

默认配置不连接大模型。显式启用后，外部 Provider 可接收本轮问题用于受约束分类或通用对话，也可接收由已批准公开材料构建的表达输入；不会接收历史回答正文、`turnId`、`requestId`、检索词项、向量、内部工具数据或私有知识。访客问题、回答和会话只存在于当前页面内存；首页通过随机、短时、一次性消费的 `handoffId` 进入 Agent，问题和回答不进入 URL 或浏览器持久化存储。

## 环境要求

- Java 21
- Maven 3.9+
- Node.js 22+
- npm 10+
- Docker（仅容器构建和运行需要）
- PostgreSQL 16+ 与 pgvector（仅启用数据库公开运行库、治理导入、主检索或组合 benchmark 时需要）

开始前确认 `java -version` 指向 Java 21。Windows 下命令使用 `mvn.cmd` 和 `npm.cmd`；其他系统可分别替换为 `mvn` 和 `npm`。

## 本地开发

先安装前端依赖：

```powershell
npm.cmd --prefix frontend ci
```

启动后端，默认监听 `http://localhost:8080`：

```powershell
mvn.cmd -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
```

`local` Profile 输出便于本机阅读的文本日志，并在仓库根目录创建 `logs/current/`。直接从 IntelliJ、
Maven 启动或使用 `scripts/start-local.ps1` 都遵守同一路径规则：Logback 独占
`backend-info.log`、`backend-error.log`，启动器独占 `frontend-info.log`、
`frontend-error.log`、`launcher.log`。可用仓库外绝对路径 `PORTFOLIO_LOG_DIRECTORY` 显式覆盖；
自动定位要求同一目录包含 `.git`、`backend/pom.xml` 与 `frontend/package.json`。日志目录已被 Git
忽略，可用 `scripts/watch-local-logs.ps1` 跟踪。生产运行使用结构化 JSON 日志。生产制品构建完成后，
显式启用 `prod` Profile 再启动：

```powershell
$env:SPRING_PROFILES_ACTIVE='prod'
java -jar backend/target/portfolio-agent.jar
```

### C1 模型表达（默认关闭）

每个进程只选择一个 Provider，不自动切换、不重试，也不把供应商 conversation/thread ID 保存到应用。未同时满足启用开关、所选 Provider 密钥和独立数据策略批准时，请求继续走 `DETERMINISTIC`。

仓库根目录提供不含真实密钥的 `.env.example`，仅作为变量名模板。不要把真实 API Key
保存在仓库目录内的 `.env`：完整发布门禁会扫描仓库风险制品并阻止凭据文件。请通过
IDE 的受保护环境配置、部署平台 Secret 或仓库外的本机 Secret 注入进程环境。禁止把
真实 API Key 写入 `.env.example`、其他受版本控制文件、聊天或日志；曾经暴露的密钥
必须先吊销再重新签发。

本地对话式 Agent 推荐使用仓库外 Secret 文件和统一启动入口。Spring Boot、Maven 与
`java -jar` 不会自动读取仓库根目录 `.env`，项目也不会隐式加载它：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/start-local.ps1 `
  -SecretsFile C:\secrets\portfolio-agent-model.env
```

Secret 文件必须位于仓库外，内容为受限 `KEY=VALUE` 格式，并同时提供四个批准开关、
`PORTFOLIO_MODEL_PROVIDER` 和所选 Provider 对应的密钥。脚本在创建子进程前检查
Java 21、Maven、Node、前端依赖与端口，只把白名单变量注入本次后端进程。只有固定公开
问题得到 `intentSource=MODEL`、`constructionMode=EVIDENCE_COMPOSITION`、
`evidenceState=VERIFIED`、`degraded=false`、`resolution=ANSWERED` 且包含回答块时才输出
`AI_CONNECTED`；否则服务保持运行并输出
`AI_DEGRADED:<安全类别>`。仅检查配置可增加 `-CheckOnly`。

DeepSeek V4 Flash：

```powershell
$env:PORTFOLIO_MODEL_ENABLED = "true"
$env:PORTFOLIO_MODEL_PROVIDER = "DEEPSEEK_V4_FLASH"
$env:PORTFOLIO_MODEL_DATA_POLICY_APPROVED = "true"
$env:PORTFOLIO_AGENT_DEEPSEEK_API_KEY = "<runtime-secret>"
```

GLM-4.7：

```powershell
$env:PORTFOLIO_MODEL_ENABLED = "true"
$env:PORTFOLIO_MODEL_PROVIDER = "GLM_4_7"
$env:PORTFOLIO_MODEL_DATA_POLICY_APPROVED = "true"
$env:PORTFOLIO_AGENT_GLM_API_KEY = "<runtime-secret>"
$env:PORTFOLIO_MODEL_TIMEOUT = "30s" # GLM-4.7 latency allowance; tune from production evidence
```

`PORTFOLIO_MODEL_TIMEOUT` 默认 `8s`，`PORTFOLIO_MODEL_MAX_TOKENS` 默认 `1200`。是否允许向所选 Provider 发送已批准公开事实，必须由部署方根据当时有效的数据条款独立确认；无法确认时不要设置批准开关。回滚只需设置 `PORTFOLIO_MODEL_ENABLED=false` 并重启，不需要回滚 ContentBundle。

要启用独立的对话式 Agent v2，还必须额外批准访客问题和临时历史进入当前所选 Provider：

```powershell
$env:PORTFOLIO_MODEL_ENABLED = "true"
$env:PORTFOLIO_MODEL_DATA_POLICY_APPROVED = "true"
$env:PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED = "true"
$env:PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED = "true"
```

任一开关、审批、兼容 Registry 或所选 Provider 密钥缺失时，v2 都 fail-closed：问候、正式预设、高精度规则和本地公开检索仍可确定性执行；必须依赖通用模型的自由问题返回 `CAPABILITY_UNAVAILABLE`。当前不再存在 `/api/v1/answers` 回退入口，回滚按模型、数据库或检索能力分别关闭。

### Agent v2 生产保护

`POST /api/v2/answers` 使用稳定的单次 JSON 响应。每次请求必须携带 UUID
`requestToken`；同一匿名来源和令牌在 2 分钟内复用同一执行结果，避免重复调用
Provider。默认每个匿名来源每分钟最多 10 次请求、最多 2 个并发；超限返回
`429`，并在 `Retry-After` 响应头和错误 JSON 中给出重试秒数。

默认时间预算为 Provider 8 秒、后端总处理 12 秒、Agent 前端 15 秒。前端支持主动取消，
失败重试复用原令牌。回答 JSON 明确返回 `resolution`、`answerScope`、
`constructionMode`、`intentSource`、`evidenceState`、`degraded` 和 `noticeCode`；Provider 不可用或输出为空、超长、结构非法、
缺少公开引用时，不展示不完整模型结果，而是返回可用的确定性降级回答或明确能力边界。

可通过以下服务端环境变量调整生产预算：

```powershell
$env:PORTFOLIO_ANSWER_REQUESTS_PER_MINUTE = "10"
$env:PORTFOLIO_ANSWER_MAX_CONCURRENT = "2"
$env:PORTFOLIO_ANSWER_REQUEST_TIMEOUT = "12s"
$env:PORTFOLIO_ANSWER_IDEMPOTENCY_TTL = "2m"
```

默认不信任 `X-Forwarded-For`。只有部署方显式开启
`PORTFOLIO_ANSWER_TRUST_PROXY=true` 并配置可信代理地址时才读取该头。服务仅在内存中使用
进程级 HMAC 对来源地址做短期限流标识，不记录原始 IP、访客问题、请求令牌或 API Key。
访客问题仅在用户主动提交后发送；页面会话只保存在当前页面内存，刷新即清空。本版本按产品
决定不增加单独的访客许可弹窗。

Provider API Key 只允许从服务端环境或部署 Secret 注入；它不会进入前端请求、前端构建产物
或业务日志。前端代码不得新增任何 Provider Key 或 Provider 直连地址。

### C3 Model Provider Registry（仅此项已实现）

Registry 快照固定为 `registrySnapshotVersion=c3-model-registry-v1`，内建 DeepSeek V4 Flash 与 GLM-4.7 两个已审 Provider；环境变量仍分别为 `PORTFOLIO_AGENT_DEEPSEEK_API_KEY` 和 `PORTFOLIO_AGENT_GLM_API_KEY`。每个部署仍由 `PORTFOLIO_MODEL_PROVIDER` 显式选择且只使用一个 Provider；没有自动故障转移、跨 Provider 重发或动态 classpath、文件、网络发现。Tool Registry、Hook、Orchestrator、多 Agent、DurableTask 与持久会话不在本次准入范围内。

### C2a 本地公开检索（默认关闭）

模型文件不会进入 Git，也不会在应用启动时下载。先显式安装并核验固定 revision 的本地制品：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/install-local-embedding-model.ps1
```

只有 active Bundle 是完整七文件 retrieval 包，且本地 descriptor、逐文件 SHA-256、模型 ID 和 512 维全部匹配时，才可启用 Hybrid：

```powershell
$env:PORTFOLIO_RETRIEVAL_PROFILE = "HYBRID"
$env:PORTFOLIO_RETRIEVAL_MODEL_DIR = "<local-model-directory>"
```

`DISABLED` 是默认值；`KEYWORD_ONLY` 只用于显式开发诊断。正式 Preset 和主体约束仍经过统一的相关性与证据充分性校验。自由问题只有通过 Grounding Gate 才返回 `ANSWERED + VERIFIED`，材料不足返回 `NOT_SUPPORTED + INSUFFICIENT`。查询、词项、向量、分数和候选不写日志、不持久化，也不发送给 DeepSeek、GLM 或任何外部 Embedding Provider。

C2 候选先在仓库外私有工作区运行 `scripts/build-retrieval-bundle.ps1` 生成 canonical `rag-documents.jsonl`，再进行人工 review/Approval。服务器发布端逐字节复核已批准 RAG，只派生 keyword/vector 索引；候选不得携带预构建索引。

### 显式引用式多轮

旧 `ToolPlan`、`FollowUpIntent` 和 `ContextEnvelope` 决策链已删除。现在由 `PortfolioIntelligence` 校验显式 `PortfolioReferenceContext`，并通过 `PortfolioRetrievalPlanner` 生成受控检索请求。只有用户点击回答上的结构化追问按钮时才发送引用上下文；普通自由输入不会继承上一条回答的引用。

引用只包含公开内容版本、Project/Case/Claim/Preset/Section 稳定引用和封闭 `followUpAction`，不包含历史问题、回答正文、身份或 Provider thread。刷新后引用随页面内存会话清空；内容版本变化时按当前公开快照重新校验，引用仍有效则返回 `contextVersionUpdated=true`，引用失效则返回 `NEEDS_CLARIFICATION`。

### PostgreSQL 公开运行库与私有治理库（默认关闭）

默认启动仍使用随包 JSON Bundle，不要求数据库。PostgreSQL 能力分成两套物理隔离的数据源：

- **公开运行库**：保存规范化公开投影、不可变兼容快照、active release、FTS/pgvector 检索文档和组合推荐所需能力标签；启用后由 `PostgresPublicPortfolioRepository` 替代文件仓储。
- **私有治理库**：保存操作者显式选择的 Markdown 文档、revision、chunk、向量状态和链接建议；公开 API 不读取该库。

两套数据库分别由以下开关和连接信息控制：

```powershell
$env:PORTFOLIO_PUBLIC_DATABASE_ENABLED = "true"
$env:PORTFOLIO_PUBLIC_DATABASE_URL = "jdbc:postgresql://localhost:5432/portfolio_public"
$env:PORTFOLIO_PUBLIC_DATABASE_USERNAME = "<runtime-user>"
$env:PORTFOLIO_PUBLIC_DATABASE_PASSWORD = "<runtime-secret>"

$env:PORTFOLIO_GOVERNANCE_DATABASE_ENABLED = "true"
$env:PORTFOLIO_GOVERNANCE_DATABASE_URL = "jdbc:postgresql://localhost:5432/portfolio_governance"
$env:PORTFOLIO_GOVERNANCE_DATABASE_USERNAME = "<governance-user>"
$env:PORTFOLIO_GOVERNANCE_DATABASE_PASSWORD = "<governance-secret>"
```

Flyway 分别从 `db/public` 和 `db/governance` 初始化 schema。公开发布导入使用
`PublicBundleDatabaseImportCli`，Markdown 扫描/导入使用 `MarkdownImportCli`，R0～R4
同口径评测和文件/数据库迁移完整度校验使用 `PortfolioSelectionBenchmarkCli`。
这些都是显式操作者命令，不存在自动文件 watcher；未审核 Markdown 不会自动进入公开发布链路。

公开数据库开关启用时，`POST /api/v2/answers` 内部以 PostgreSQL/pgvector 为作品集主检索，
基础设施不可用时受控降级到随包 Bundle。独立 Selection HTTP 接口不再注册；候选召回、
确定性组合策略和 benchmark 仍作为内部能力保留，且不能绕过 APPROVED Evidence、跨 release
阻断或人工发布批准。

在另一个终端启动前端，Vite 会把 `/api` 请求代理到后端：

```powershell
npm.cmd --prefix frontend run dev
```

## 测试

后端测试：

```powershell
mvn.cmd -f backend/pom.xml test
```

前端单元与组件测试：

```powershell
npm.cmd --prefix frontend test -- --run
```

Playwright 分成两种拓扑。前端独立验收由 Vite 启动页面，并且只对公开内容与问答两个 API 使用浏览器内 mock；完整联调则启动已打包 JAR，访问其中的生产前端资源与真实 Spring Boot API：

```powershell
# Frontend-only visual/interaction acceptance with API mocks
npm.cmd --prefix frontend run test:e2e

# Full packaged-JAR frontend/backend integration
powershell -ExecutionPolicy Bypass -File scripts/run-jar-e2e.ps1
```

完整联调命令要求先完成一次新的前端构建和 Maven 打包，不能复用来源不明的旧制品。

## 构建并运行单 JAR

必须先构建前端，再执行 Maven 打包。Maven 会拒绝缺少 `frontend/dist/index.html` 的构建，并把当前静态资源复制进 JAR。

```powershell
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run build
mvn.cmd -f backend/pom.xml clean package
java -jar backend/target/portfolio-agent.jar
```

最终制品为 `backend/target/portfolio-agent.jar`。打开 `http://localhost:8080` 查看首页。

## 按请求 ID 排障

接口响应会返回 `X-Request-Id`。定位一次请求时按同一条关联链排查：

1. 从浏览器 Network 响应头复制 `X-Request-Id`。
2. 在日志系统中查询 `request.id=<复制的值>`。
3. 找到该请求的 `http.request.started`，以及对应的 `http.request.completed` 或 `http.request.failed`。
4. 从这些 HTTP 事件取得 `trace.id`，继续查看该请求内部产生的诊断事件。

日志只保留封闭的运行状态、错误码和关联标识，不记录访客问题、回答正文、原始 IP、Provider
载荷或密钥。`X-Request-Id` 用于定位请求，不代表可以绕过这条隐私边界。

## 隐私检查

先验证扫描器自身，再扫描公开快照和前端构建产物：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.test.ps1
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path backend/src/main/resources/public-data
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path frontend/dist
```

扫描器检查 IPv4、常见内部绝对路径、内部域名、凭据字面量、原始模型 Prompt/响应日志以及访客问题直传 Provider 的高风险代码形态。它是发布前防线，不替代人工脱敏审核。

## Docker

```powershell
docker build --check .
docker build -t internship-portfolio-agent .
docker run --rm -p 8080:8080 internship-portfolio-agent
```

容器以非 root 用户运行，服务端口为 `8080`。

## 完整发布验证

从项目根目录运行原子化发布门禁。它会依次执行代码质量、架构与静态 bundle 校验器自测，完成前端测试与构建、后端 `clean package`、隐私扫描、JAR 解包检查、区分大小写的静态路径及逐文件 SHA-256 对比，再启动该 JAR 完成真实 Playwright 联调，避免验证旧的前端或 JAR。默认也会在 Docker CLI 可用时运行 Dockerfile 检查。

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/verify-release.ps1 `
  -SkipInstall `
  -RequireLiveProvider
```

依赖已经通过 `npm ci` 安装时，可使用 `-SkipInstall`；明确只做本机无 Docker 的验收时，可再加 `-SkipDockerCheck`。正常 CI 不调用真实 Provider；生产候选必须用上面的 `-RequireLiveProvider` 命令单独留存真实 Provider 调用证据，不能把普通 CI 结果当作该证据。

## 公开 API

- `GET /api/v1/public-content`：正式页面使用的审核公开内容聚合
- `GET /api/v1/portfolio`：首页公开快照
- `GET /api/v1/projects/{slug}`：项目详情
- `GET /api/v1/cases`：公开案例摘要列表
- `GET /api/v1/cases/{slug}`：公开案例详情
- `POST /api/v2/answers`：唯一公开 Agent 入口；支持自然交流、通用知识、作品集检索、比较、推荐和动态追问。对话与推荐上下文由当前标签页内存随请求传入，刷新即清空，后端不保存会话状态
- `POST /api/v1/client-diagnostics`：默认关闭的前端诊断批量入口，只接受封闭且不持久化的事件契约

`GET /api/v1/public-content` 提供顶层 `cases`、`collections` 和 `caseSlugsByEvidenceId`，QuestionPreset 与 Timeline 投影包含 `caseSlugs`。`POST /api/v2/answers` 的 `context` 支持 `projectSlug`/`caseSlug` 二选一及可选 `referenceContext`。`source=CASE` 时 Project 与 Case 必须互斥；未知主体 fail-closed，Case 不会隐式扩展为相关 Project。前端已经实现 `/cases`、`/cases/:slug`、旧项目地址规范重定向和 Case → Agent 交接；剩余缺口是生产部署、线上数据验证和完整生产验收。

除浏览器诊断入口外，公开 API 只读取版本化 JSON 快照，不读取私有知识库，也不保存访客问题。
`POST /api/v1/client-diagnostics` 是只读公开端点的唯一例外：它只接受封闭、限流且不持久化的
诊断事件契约。该入口永不接受访客内容、任意元数据、原始堆栈、URL、Headers、请求体、
响应体、原始来源地址或凭据；关闭时返回 404，只有部署方显式启用后才接收事件。

`POST /api/v2/answers` 请求示例：

```json
{
  "turnId": "turn-7",
  "question": "这个 SQL 审计功能具体怎么实现的？",
  "messages": [
    {"role": "USER", "content": "先介绍一下 SQL 审计项目"},
    {"role": "ASSISTANT", "content": "这是一个围绕审计与故障排查的项目。"}
  ],
  "context": {
    "projectSlug": "sql-audit",
    "caseSlug": null,
    "audienceRole": "INTERVIEWER",
    "source": "AGENT_PAGE"
  }
}
```

响应使用 `ANSWERED / NEEDS_CLARIFICATION / NOT_SUPPORTED / CAPABILITY_UNAVAILABLE / REJECTED` 区分结果，使用 `GLOBAL / GENERAL / PORTFOLIO / MIXED` 区分范围，并分别返回构造方式、意图来源和证据状态。`blocks[].sourceScope` 明确标记 `GENERAL` 或 `PORTFOLIO`，作品集 block 同时返回 Claim/Evidence ID。`suggestedQuestions` 是本轮动态生成且经可回答性校验的 0～3 个问题。前端已接入 v2，Case 页面流程和 packaged-JAR 本地联调已完成；生产验收仍未完成。

## 目录结构

- `backend/`：Spring Boot API、问答运行时、公开发布、PostgreSQL 仓储、治理导入、组合推荐与基准评测
- `frontend/`：Vue 3 八路由页面、领域组件、Vitest 测试和 Playwright 验收
- `governance/`：候选、策略、schema、基准和公开资产治理 CLI
- `runtime-models/`：本机检索模型安装目标；模型二进制不进入 Git
- `scripts/`：代码质量、架构、隐私、发布导入、检索构建、静态 bundle、JAR E2E 和完整发布门禁
- `docs/`：文档状态索引、背景、需求、技术选型、决策、设计、计划、交接和阶段审核

后端 Java 代码采用模块化单体结构：

```text
com.portfolio.agent
├─ common       请求关联、错误契约、诊断与跨模块共享机制
├─ portfolio    公开事实、文件/PostgreSQL 仓储、校验与发布激活
├─ answer       知识转换、回答编排、模型适配、本地检索与固定工具
├─ release      Bundle 编译、数据库导入、发布验证与检索比较 CLI
├─ ingestion    私有治理库的 Markdown 扫描、增量导入与分块
└─ selection    PostgreSQL 候选召回、确定性组合推荐和 R0～R4 评测
```

模块内部使用常见 Spring Boot 命名：

```text
portfolio/controller|service|domain|repository|release|mapper|validation
answer/controller|service|domain|engine|gateway|adapter|mapper
ingestion/adapter|cli|domain|gateway|service
selection/adapter|benchmark|controller|domain|dto|gateway|mapper|service
```

当前模块通信通过 Java Gateway 接口在同一进程内完成：

```text
ConversationAnswerController
→ ProductionConversationService
→ ConversationalAgentRuntime（全局安全/通用对话）
→ PortfolioIntelligence.tryResolve（作品集唯一语义入口）
→ PortfolioRetriever
→ Bundle（默认）或 PostgreSQL 主检索 + Bundle 故障切换（显式启用）
```

项目当前不使用 Feign，也不通过 HTTP 或 localhost 对自身模块发起远程调用。

## 文档入口

- `docs/00-文档状态索引.md`：全部文档的当前状态、权威顺序和已知缺口
- `docs/11-项目演进日志.md`：按日期回顾功能、重要修复、产品决策和技术选型的演进
- `docs/10-本地PostgreSQL与pgvector运行手册.md`：本地 PostgreSQL 16 / pgvector 双库启动、连接和隔离说明
- `docs/08-当前实现状态.md`：按代码、配置、测试和制品盘点当前已实现/受限/未实现能力
- `docs/04-项目代码约束.md`：当前代码与发布约束
- `docs/superpowers/specs/2026-07-14-internship-portfolio-v0-design.md`：当前 V0 事实与回答边界
- `docs/superpowers/specs/2026-07-16-modular-monolith-package-design.md`：当前后端结构
- `docs/superpowers/specs/2026-07-16-portfolio-frontend-full-rebuild-design.md`：当前前端产品与视觉基线
- `docs/superpowers/specs/2026-07-17-public-content-api-integration-design.md`：当前公开内容 API 与真实联调基线
- [`docs/superpowers/specs/2026-07-28-portfolio-v1-case-and-release-closure-design.md`](docs/superpowers/specs/2026-07-28-portfolio-v1-case-and-release-closure-design.md)：已实现的 Case 前端与发布闭环契约
- [`docs/superpowers/specs/2026-07-30-postgresql-portfolio-composition-design.md`](docs/superpowers/specs/2026-07-30-postgresql-portfolio-composition-design.md)：PostgreSQL 双库、增量导入、组合推荐与基准评测契约
- [`docs/decisions/2026-07-30-postgresql-portfolio-composition-evolution.md`](docs/decisions/2026-07-30-postgresql-portfolio-composition-evolution.md)：组合策略与数据库演进边界

`docs/01-03` 描述长期产品和技术路线；标记为历史、已取代或待审批的设计与计划不能直接作为当前实施授权。
