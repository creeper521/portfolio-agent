# 实习作品集 Agent

> **项目状态（2026-07-28）：** 全量公开资产已完成人工 Approval、本地发布和原子导入，当前随包运行时为 schema 3.0、内容版本 `2026-07-27.1` 的七文件检索包：7 个 Project、49 个 Case、81 个 Claim、59 个 Evidence、81 条 Claim–Evidence 关联、11 条 TimelineEvent 和 16 个 QuestionPreset；公开 61/68 项资产，7 项 `EXCLUDE` 保持私有。89 例 Keyword/Vector/Hybrid 真实模型比较中 Hybrid 的正例充分判定为 32/38，三路 false-sufficient 均为 0。Case 后端契约、未知主体 fail-closed、随包 Case 冒烟和真实 Provider 显式验收门禁已经完成；下一阶段是独立 `/cases`、`/cases/:slug`、规范重定向与具体 UI，之后再做生产候选验收。本次没有部署，也没有取得真实 Provider 外部调用证据。详见 [`docs/reports/retrieval-full-public-assets-candidate-2026-07-27.md`](docs/reports/retrieval-full-public-assets-candidate-2026-07-27.md)。

一个面向技术面试官和实习导师的交互式实习作品集。V0 使用审核后的公开 JSON 快照，展示 SQL 审计与故障排查工具项目，并提供一个确定性问答闭环。

## 当前范围

- Vue 3 六路由作品集：概览、项目目录、项目详情、时间线、证据中心和完整 Agent 工作台
- Spring Boot 公开作品集 API，以及供正式页面使用的 `GET /api/v1/public-content` 聚合接口
- 独立 Case 领域模型、严格校验、只读服务、列表/详情 API，以及当前公开 49 个 Case
- 全量资产生成器：把获批公开资产编译为 7 个 Project、49 个 Case、脱敏 Claim/Evidence 和检索基准，且发布前必须经过人工批准
- 共享 Case 目录模型可按长期主线、单体任务、问题处理、知识与评测分组；独立 Case 信息架构复用既有 Dossier 能力，故事页属于后续的 selected-case 增强
- 公开 Bundle 包含 16 个 QuestionPreset 和 11 条 TimelineEvent；Case 专属预设由 Agent 后端执行
- 公开快照启动校验、APPROVED Evidence 过滤、项目/Evidence/Timeline 交叉引用
- 首页轻问答、Agent 真实 API 接线、错误重试、页面内存会话和响应式抽屉
- 单个可执行 JAR、Docker 构建定义和 packaged-JAR Playwright 联调
- 可选的 DeepSeek V4 Flash 或 GLM-4.7 单 Provider 表达；只接收公开 `AnswerPlan`，完整校验失败即整轮确定性回退
- 可选的本地 BGE-small-zh-v1.5 INT8 ONNX 混合检索；随包使用 `retrieval-policy-v2.1-query-risk`，文档向量在发布期生成，访客查询只在本机向量化
- 固定六类只读公开工具与页面内存引用式多轮；只传稳定公开 ID 和意图，不传历史问答正文
- 代码质量、架构、隐私、静态 bundle 与发布验证脚本

默认配置不连接大模型。即使显式启用 C1，外部 Provider 也只接收从已批准公开内容构建的白名单 `AnswerPlan`，不接收访客原问题、会话、`turnId`、`requestId` 或私有知识。访客问题、回答和会话只存在于当前页面内存；首页通过随机、短时、一次性消费的 `handoffId` 进入 Agent，问题和回答不进入 URL 或浏览器持久化存储。

## 环境要求

- Java 21
- Maven 3.9+
- Node.js 22+
- npm 10+
- Docker（仅容器构建和运行需要）

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

`local` Profile 输出便于本机阅读的文本日志；生产运行使用结构化 JSON 日志。生产制品构建完成后，
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

任一开关、审批、兼容 Registry 或所选 Provider 密钥缺失时，v2 都 fail-closed：问候和可匹配的已发布作品集预设仍可确定性降级，其余自由问题明确返回能力边界。关闭 `PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED` 即可单独回滚 v2，不影响 `/api/v1/answers`。

### Agent V1 生产保护

`POST /api/v2/answers` 使用稳定的单次 JSON 响应。每次请求必须携带 UUID
`requestToken`；同一匿名来源和令牌在 2 分钟内复用同一执行结果，避免重复调用
Provider。默认每个匿名来源每分钟最多 10 次请求、最多 2 个并发；超限返回
`429`，并在 `Retry-After` 响应头和错误 JSON 中给出重试秒数。

默认时间预算为 Provider 8 秒、后端总处理 12 秒、Agent 前端 15 秒。前端支持主动取消，
失败重试复用原令牌。回答 JSON 明确返回 `resolution`、`generationMode`、
`answerSource`、`degraded` 和 `noticeCode`；Provider 不可用或输出为空、超长、结构非法、
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

`DISABLED` 是默认值；`KEYWORD_ONLY` 只用于显式开发诊断。Preset 始终优先并跳过检索。自由问题只有通过 Grounding Gate 才返回 `ANSWERED + RETRIEVAL`，否则保持安全 `BOUNDARY`。查询、词项、向量、分数和候选不写日志、不持久化，也不发送给 DeepSeek、GLM 或任何外部 Embedding Provider。

C2 候选先在仓库外私有工作区运行 `scripts/build-retrieval-bundle.ps1` 生成 canonical `rag-documents.jsonl`，再进行人工 review/Approval。服务器发布端逐字节复核已批准 RAG，只派生 keyword/vector 索引；候选不得携带预构建索引。

### C2b 固定只读工具与引用式多轮

后端只允许 `getProject`、`getClaims`、`getEvidenceForClaims`、`getTimeline`、`searchPublicContent` 和 `compareProjects` 六类固定读操作。`ToolPlan` 在模型调用前由服务端根据封闭 `FollowUpIntent` 确定，最多四次调用，全部读取同一个 `RuntimeAnswerContent`；工具不能访问文件系统、网络、私有治理目录或写接口，模型只接收最终白名单 `AnswerPlan`。

前端只有在回答返回 `ContextEnvelope` 时才展示追问操作。Envelope 只包含当前公开内容版本、Project/Claim/Preset/Section 稳定引用和追问意图；不包含历史问题、回答正文、会话、身份或 Provider thread。刷新后引用式上下文随页面内存会话一起消失；内容版本变化时重新按稳定 ID 核对并明确提示，引用失效则返回 `BOUNDARY`。

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
- `POST /api/v1/answers`：四维契约问答；默认确定性，C1 合规启用后可返回 `MODEL` 或 `FALLBACK`
- `POST /api/v2/answers`：对话式回答；支持自然交流、通用知识、作品集检索回答、混合回答、20 轮临时上下文和动态追问

`GET /api/v1/public-content` 提供顶层 `cases` 和 `caseSlugsByEvidenceId`，QuestionPreset 与 Timeline 投影包含 `caseSlugs`。`POST /api/v1/answers` 的 `context` 支持 `projectSlug`/`caseSlug` 二选一，`ContextEnvelope` 使用显式 `caseSlugs` 保持主体隔离。`source=CASE` 时，Project 与 Case 必须互斥；未知主体 fail-closed，Case 不会隐式扩展为相关 Project。前端已调用 `/api/v2/answers`；剩余缺口是独立 `/cases`、`/cases/:slug`、规范重定向、具体 UI 与生产验收。

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

响应以 `intent` 区分 `CONVERSATION`、`GENERAL_KNOWLEDGE`、`PORTFOLIO_GROUNDED`、`HYBRID`、`TIME_SENSITIVE` 和 `UNSUPPORTED_OR_UNSAFE`；`blocks[].sourceScope` 明确标记 `GENERAL` 或 `PORTFOLIO`，作品集 block 同时返回 Claim/Evidence ID。`suggestedQuestions` 是本轮动态生成且经可回答性校验的 0～3 个问题。前端已接入 v2；Case 独立页面流程和生产验收仍未完成。

## 目录结构

- `backend/`：Spring Boot API、确定性回答引擎和公开快照
- `frontend/`：Vue 3 六路由页面、组件测试和 Playwright 测试
- `scripts/`：代码质量、架构、隐私、静态 bundle、JAR E2E 和完整发布门禁
- `docs/`：文档状态索引、背景、需求、技术选型、设计、计划和阶段审核

后端 Java 代码采用模块化单体结构：

```text
com.portfolio.agent
├─ common       仅保存跨模块共享机制
├─ portfolio    公开事实、作品集查询与文件仓储
└─ answer       知识转换、回答编排与确定性引擎
```

模块内部使用常见 Spring Boot 命名：

```text
portfolio/controller|service|domain|repository|mapper|validation
answer/controller|service|domain|engine|gateway|adapter|mapper
```

当前模块通信通过 Java Gateway 接口在同一进程内完成：

```text
AnswerService
→ PortfolioKnowledgeGateway
→ LocalPortfolioKnowledgeAdapter
→ PublicPortfolioRepository
```

项目当前不使用 Feign，也不通过 HTTP 或 localhost 对自身模块发起远程调用。

## 文档入口

- `docs/00-文档状态索引.md`：全部文档的当前状态、权威顺序和已知缺口
- `docs/04-项目代码约束.md`：当前代码与发布约束
- `docs/superpowers/specs/2026-07-14-internship-portfolio-v0-design.md`：当前 V0 事实与回答边界
- `docs/superpowers/specs/2026-07-16-modular-monolith-package-design.md`：当前后端结构
- `docs/superpowers/specs/2026-07-16-portfolio-frontend-full-rebuild-design.md`：当前前端产品与视觉基线
- `docs/superpowers/specs/2026-07-17-public-content-api-integration-design.md`：当前公开内容 API 与真实联调基线
- [`docs/superpowers/specs/2026-07-28-portfolio-v1-case-and-release-closure-design.md`](docs/superpowers/specs/2026-07-28-portfolio-v1-case-and-release-closure-design.md)：Case 前端与发布闭环的交接规范，供前端 AI 接手

`docs/01-03` 描述长期产品和技术路线；标记为历史、已取代或待审批的设计与计划不能直接作为当前实施授权。

本次后端闭环不实现 Vue 页面、路由、组件、CSS 或最终视觉设计。
