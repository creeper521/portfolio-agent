# 动态公开知识与作品集 Agent 扩展设计

**日期：** 2026-07-15  
**状态：** 待用户统一审核  
**适用项目：** `D:\code\agent`  
**关联原型：** `C:\Users\WIN10\Documents\杂项\实习学习-Obsidian\.superpowers\brainstorm\homepage-flow-20260715\content\portfolio-home-prototype-v1.html`

## 1. 背景

当前项目已经具备公开作品集快照、项目详情、确定性问答、Evidence 过滤、隐私检查和单 JAR 交付能力，但知识内容与应用制品仍然绑定：Spring Boot 只在启动时读取 JAR 内的 `public-data/public-portfolio.v1.json`。更新公开内容需要修改资源、重新打包 JAR 并重新部署。

现有回答引擎只支持规范问题及其精确别名，回答固定为背景、职责、方案、验证和状态五段。目标首页原型还包含动态统计、精选项目、四种访客身份、身份化推荐问题、时间线、证据档案和完整 Agent 工作台，这些能力尚未进入后端公开模型或 API。

本设计在保留当前公开数据隔离、确定性兜底和 API 基础能力的前提下，引入独立内容发布、Claim 事实层、公开 RAG、模型表达和安全回滚。第一阶段不建设数据库、管理后台、SSE 或服务端会话；这些能力保留后续演进空间。

## 2. 已确认决策

1. 知识更新通过独立发布生效，不重新构建 JAR；允许重启服务或容器。
2. 发布命令在部署服务器本机执行。
3. 第一阶段通过重启 Spring Boot 或容器加载新版本，不做进程内热重载或文件监听。
4. 动态范围包括事实内容与展示编排；页面栏目类型和安全规则仍由代码控制。
5. 首页统计数值由后端计算，快照只配置标签、说明、顺序和跳转目标。
6. 访客身份影响内容侧重和表达，不改变底层事实。
7. Agent 使用模型表达与确定性兜底；两条路径共享同一份受控 Answer Context。
8. 引入 Claim 作为最小可信事实，并使用审核后的公开片段建设 RAG。
9. RAG 采用关键词与向量混合检索；Embedding API 通过端口适配。供应商协议、模型和维度是阶段 3 开工前必须完成的选型输入，不进入领域契约。
10. 会话只保存在浏览器，七天自动过期；后端不持久化、不记录访客问题。
11. 第一阶段使用完整非流式响应，前端可以自行逐字展示；SSE 延后。
12. Obsidian 由 Codex/脚本只读增量扫描，分项生成并审核 Project、Claim、Evidence、TimelineEvent 和 RAG Chunk 候选。
13. 私有审核状态为 `DRAFT / APPROVED / REJECTED / BLOCKED`；公开发布包只包含已批准对象。
14. TimelineEvent 是一等实体。
15. 页面采用固定栏目类型，可配置显示、顺序、文案和数据来源，不允许配置任意 HTML、CSS、JavaScript 或可执行 Prompt。
16. 采用版本化公开发布包；后期通过仓储接口逐步迁移到数据库。

## 3. 目标

- 将公开知识更新与应用构建、镜像发布解耦。
- 保持公开应用无法读取私有 Obsidian、候选稿和原始 Evidence。
- 让项目、Claim、Evidence、时间线、身份和首页编排随已审核内容版本变化。
- 让自由问题能够通过结构化事实和公开 RAG 获得上下文。
- 让模型只负责受控表达，不负责决定事实是否存在。
- 模型、Embedding 或向量检索不可用时仍能返回可靠回答。
- 保留当前规范问题、五段式回答和 Evidence 返回作为兼容兜底。
- 支持服务器本机验证、发布、激活、回滚和审计。
- 为后续数据库、SSE、服务端会话和管理后台保留清晰替换边界。

## 4. 非目标

- 公开服务运行时直接读取 Obsidian 或私有索引。
- 第一阶段建设数据库、认证、管理后台或多人审核系统。
- 第一阶段建设 SSE、WebSocket 回答流或服务端会话持久化。
- 允许内容配置注入任意前端代码、SQL、类名、Bean 名或模型 Prompt。
- 使用 RAG 召回结果替代 Claim 审核和 Evidence 约束。
- 将计划、原型、学习或协作工作自动表述为独立交付成果。
- 本设计阶段修改后端或前端代码。

## 5. 总体架构

系统分为严格隔离的私有内容生产平面和公开内容消费平面。

```text
私有 Obsidian（只读）
  -> 增量扫描与候选提取
  -> 分项人工审核
  -> 公开候选包构建
  -> 上传部署服务器 incoming 目录
  -> 服务器本机发布命令
  -> 校验并生成完整运行发布包
  -> 索引构建、原子激活
  -> 重启服务或容器
  -> Spring Boot 加载不可变 active 版本
  -> Portfolio API / Answer API
```

私有内容生产平面可以保存原文路径、来源片段、内容哈希、敏感扫描结果和审核记录；这些信息不得进入公开候选包、运行发布包、部署服务器、公开仓库、JAR 或 Docker 镜像。

“公开候选包”是上传到服务器的事实、展示和 RAG 源文本；“运行发布包”由服务器发布工具生成，在候选内容基础上增加关键词索引、向量索引、运行 Manifest 和 Checksums。只有完整运行发布包可以成为 active 版本。

公开消费平面只读取一个完整、不可变、已验证的 `ActivePublicContent`：

```text
ActivePublicContent
├─ ReleaseManifest
├─ PublicPortfolioFacts
├─ PublicPresentation
├─ KeywordIndex
└─ VectorIndex
```

一次请求从开始到结束只能引用同一个 `contentVersion`。即使未来支持热切换，也不得在一个请求中混合两个版本。

## 6. 后端组件边界

### 6.1 内容仓储

业务层依赖 `PublicContentRepository`，不依赖 classpath JSON、文件目录、具体向量库或数据库。

第一阶段实现：

```text
PublicContentRepository
  └─ VersionedFilePublicContentRepository
```

后期实现：

```text
PublicContentRepository
  └─ DatabasePublicContentRepository
```

版本化发布包继续作为数据库导入、导出、备份和灾难恢复格式，因此数据库迁移不是推翻现有发布机制。

### 6.2 展示查询

`PortfolioQueryService` 负责：

- 查询 Owner、Project、Claim、Evidence 和 TimelineEvent；
- 根据公开实体计算首页统计；
- 根据 presentation 配置组装固定栏目；
- 过滤不可公开或不存在的关联；
- 返回 application-owned 查询模型，由 API 层映射 DTO。

应用层不得返回 API DTO，Controller 不得直接读取仓储或文件。

### 6.3 回答编排

`AnswerOrchestrator` 负责：

1. 解析身份、问题和有限上下文；
2. 定位项目、主题和问题意图；
3. 检索 Claim；
4. 执行关键词与向量混合检索；
5. 过滤并重排公开片段；
6. 组装 Answer Context；
7. 调用表达模型；
8. 校验结构、事实和引用；
9. 校验失败或外部服务异常时调用确定性回答器。

### 6.4 外部模型端口

```text
EmbeddingProvider
├─ embedDocuments
├─ embedQuery
└─ modelMetadata

AnswerModelProvider
└─ generateStructuredAnswer
```

供应商协议、base URL 和模型名通过 infrastructure adapter 与环境配置提供。领域和应用层不依赖特定厂商 SDK。

## 7. 公开领域模型

### 7.1 Project

Project 保存项目身份和稳定元数据：

- `id`、`slug`、标题、摘要；
- 时间范围、技术标签；
- 项目状态与个人贡献类型；
- 是否为核心项目；
- 关联 Claim、Evidence 和 TimelineEvent。

项目长文由 Claim 聚合得到，避免项目页、首页和 Agent 分别维护相互漂移的事实正文。

### 7.2 Claim

Claim 是回答和展示的最小可信事实：

- `id`、`projectId`；
- `type`；
- `statement` 与公开 `detail`；
- `achievementStatus`；
- `contributionType`；
- `evidenceIds`；
- `topics`；
- 身份优先级。

Claim 类型固定为受支持枚举，包括背景、职责、技术决策、实现、验证、结果、限制、学习和复盘。

事实状态统一为：

- `DELIVERED`
- `IMPLEMENTED_TESTED`
- `PROTOTYPE`
- `DESIGNED`
- `LEARNING`
- `PLANNED`
- `UNKNOWN`

模型不得修改 Claim 的事实状态和贡献类型。

### 7.3 Evidence

Evidence 只保存脱敏公开摘要、类型、日期范围、来源数量、支持的 Claim 和展示标题。不得保存原始路径、内部链接、私有文件名、未脱敏截图或原始正文。

成果性 Claim 必须至少关联一个已批准 Evidence。没有 Evidence 的内容只能按学习、计划、未知或明确受限的口径公开。

### 7.4 TimelineEvent

TimelineEvent 包含日期范围、事件类型、标题、公开摘要、事实状态，以及 Project、Claim、Evidence 关联。事件类型包括交付、迭代、故障处理、设计、学习和复盘。

时间线只组织已审核事实，不能创造新成果结论。首页实习月份数根据已发布时间线边界或单独审核的实习周期计算，不能由展示配置手填。

### 7.5 QuestionPreset

推荐问题保存问题文本、适用身份、目标项目或主题、预期 Claim 类型、展示位置和排序。它可以作为确定性兜底入口，但不能直接保存未经事实关联的完整回答。

### 7.6 AudienceProfile

固定身份代码为 `INTERVIEWER / MENTOR / HR / GUEST`。配置可以控制显示文案、推荐问题、内容优先级和回答详细度。

身份只影响排序和表达，不得改变事实集合、审核状态或必要的限制说明。

## 8. 展示编排

代码提供固定栏目注册表：

- `HERO`
- `METRICS`
- `LIGHT_AGENT`
- `EXPLORE_PORTALS`
- `FOOTER`

发布配置可以控制显示、顺序、标题、说明、数据选择、跳转目标、精选项目和推荐问题。未知栏目类型、未知指标、任意 HTML、脚本、样式和可执行 Prompt 均拒绝发布。

首页统计采用“计算值 + 展示配置”：

- `INTERNSHIP_MONTH_COUNT`
- `PUBLIC_PROJECT_COUNT`
- `PUBLIC_EVIDENCE_COUNT`
- `PUBLIC_CLAIM_COUNT`

后端只统计当前 active 版本中实际公开的实体，不能泄漏候选对象或私有知识库总量。

## 9. RAG 与混合检索

RAG 只索引逐项审核通过的公开文本片段。每个片段必须携带 `chunkId`、Project、Claim、Evidence、主题和内容哈希。

检索流程：

1. 根据项目、主题、Claim 类型和上下文做元数据过滤；
2. 并行执行关键词和向量召回；
3. 融合关键词相关度、向量相似度、Claim 关联、项目上下文、身份优先级和 Evidence 完整度；
4. 去重、限制数量并组装 Answer Context。

RAG Chunk 只能补充上下文。涉及完成状态、贡献、结果或验证的表达必须能回落到 Claim 和 Evidence。

文档向量在发布阶段生成并写入版本包。线上只为访客问题生成查询向量。查询向量调用失败时退化为关键词与 Claim 检索；向量索引损坏则应用启动失败并触发发布回滚。

## 10. Agent 回答与兜底

请求包含身份、当前问题、可选项目提示、最近 4 至 6 轮有限上下文和会话内容版本。前端上下文只用于理解指代，不能作为事实来源；后端必须在当前 active 版本中重新检索。

模型接收受限 Answer Context，而不是完整发布包。模型输出使用结构化 JSON，包括摘要、sections、Claim 引用、Evidence 引用和推荐问题标识。

返回前必须验证：

- JSON Schema 与 section 类型；
- Claim、Evidence 和 QuestionPreset 是否属于本次上下文；
- Evidence 是否支持对应 Claim；
- 状态和贡献类型是否被篡改；
- 是否出现内部地址、路径或凭据模式；
- 长度、栏目数量和未知字段。

任何模型超时、限流、无效 JSON、未知引用或安全校验失败都进入确定性兜底。兜底使用同一 Answer Context，按身份排序 Claim，通过固定模板生成可引用回答。

当前 V0 规范问题、五段式回答、Evidence 返回和边界文案应保留为兼容回归用例。

## 11. API 设计

现有三个端点保持可用：

- `GET /api/v1/portfolio`
- `GET /api/v1/projects/{slug}`
- `POST /api/v1/answers`

对现有响应采用加字段演进，不删除或改变已有字段含义。新增能力可以增加：

- 首页计算指标与固定栏目配置；
- AudienceProfile 和 QuestionPreset；
- Claim、TimelineEvent 和精确引用；
- `contentVersion`、`answerMode`、`fallback`、`fallbackReason`、`knowledgeUpdated`。

为独立页面增加只读端点：

- `GET /api/v1/timeline`
- `GET /api/v1/evidence`

Answer 请求允许旧请求继续传 `projectSlug + question`；扩展请求增加 `audience` 和有限 `context`。为支持跨项目、成长和总览问题，`projectSlug` 在新契约中可以为空，但格式校验在提供时仍保持严格。

响应中的 fallback 原因只使用安全枚举，例如 `MODEL_UNAVAILABLE / INVALID_MODEL_OUTPUT / EMBEDDING_UNAVAILABLE / INSUFFICIENT_PUBLIC_FACTS`，不得返回供应商错误原文。

## 12. 浏览器会话

会话只保存在浏览器并七天自动过期。前端在启动时清理过期项，并提供清除全部本地记录操作。

本地记录可以保存会话 ID、时间、身份、项目上下文、消息、contentVersion 和公开引用标识。不得保存 API Key、Embedding、服务端检索分数、私有路径或未公开内容。

当会话版本与当前响应版本不一致时，后端返回 `knowledgeUpdated=true`。前端提示公开资料已更新，旧引用不得直接沿用。

第一阶段后端无会话数据库、无 session state、无问题持久化。后期数据库会话必须另行设计身份、保留期、删除、导出和合规规则。

## 13. 发布与运行

每个公开版本是不可变发布包。服务器发布命令负责校验、隐私扫描、索引构建、检索冒烟、哈希生成、原子切换、服务重启、HTTP 验证和失败回滚。

应用启动时读取外部 active 指针，校验 manifest、文件哈希、Schema、应用兼容范围和索引完整性，然后一次性构造 ActivePublicContent。外部 active 存在但损坏时必须启动失败，不能静默退回 JAR 内快照。

JAR 兜底只允许用于首次安装且外部数据目录不存在的显式初始化场景。

## 14. 错误处理与降级矩阵

| 故障 | 行为 |
|---|---|
| 候选包结构错误 | 拒绝发布，旧版本继续运行 |
| 隐私扫描失败 | 拒绝发布，不生成可激活版本 |
| 文档向量生成不完整 | 拒绝发布 |
| 检索冒烟失败 | 拒绝发布 |
| 新版本启动失败 | 自动恢复旧 active 并重启 |
| HTTP 冒烟失败 | 自动回滚并记录失败阶段 |
| 查询向量 API 失败 | 关键词 + Claim 检索 |
| 表达模型超时或限流 | 确定性回答 |
| 模型输出或引用校验失败 | 确定性回答 |
| 公开事实不足 | 明确边界回答，不让模型补全 |
| 浏览器会话过期 | 本地删除，不影响公开内容浏览 |

## 15. 安全与隐私

- 公开运行时没有读取 Obsidian、候选包和审核源数据的文件权限。
- Embedding 和表达模型只能接收已审核公开文本。
- 凭据只通过服务器环境或 Secret 管理注入。
- 日志不记录访客问题、对话正文、召回片段、Prompt 或模型回答。
- 日志只记录 requestId、contentVersion、身份、耗时、召回数量、结果类别和安全错误码。
- 被 `BLOCKED` 的对象必须从公开包中物理排除。
- 安全撤回版本可以加入本机阻断清单，禁止普通 rollback 重新激活。
- 发布目录由发布工具写入，应用容器只读挂载。

## 16. 测试与验收

### 16.1 内容契约

- 所有实体 ID 唯一且引用存在；
- Claim 状态和 Evidence 约束正确；
- TimelineEvent、QuestionPreset 和 RAG Chunk 不引用未发布对象；
- 未知字段、未知枚举和不支持 Schema 被拒绝；
- 三个公开数据文件 contentVersion 一致；
- 审核状态变更后内容哈希变化会使旧批准失效。

### 16.2 检索

- 建立公开评测问题集，覆盖项目、技术、时间、身份和边界问题；
- 分别验证 Claim-only、关键词退化和混合检索；
- 验证跨项目误召回、撤回内容和无答案问题；
- 向量模型或维度变化必须强制重建索引。

### 16.3 Agent

- 模型正常输出通过结构和引用校验；
- 超时、限流、截断 JSON、未知 ID 和越权内容全部进入兜底；
- 四种身份只改变侧重，不改变事实；
- 当前 V0 规范问题仍返回完整五段和 Evidence；
- 不足事实返回边界说明。

### 16.4 发布

- 并发发布被锁阻止；
- 写入中断不改变 active；
- 哈希、索引、启动或 HTTP 失败均自动回滚；
- rollback 目标不兼容或被阻断时拒绝；
- 应用加载的 contentVersion 与 status、API 和 manifest 一致。

### 16.5 前端契约

- 固定栏目注册表可以正确处理显示与排序；
- 指标值来自后端计算；
- 7 天会话过期和手动清除有效；
- knowledgeUpdated 提示有效；
- 非流式响应可以安全执行逐字展示；
- 未知栏目不静默渲染。

## 17. 分阶段迁移

### 阶段 0：完成后端分层整改

先处理现有 `portfolio.application -> portfolio.api` 反向依赖、answer 跨模块类型泄漏、一次查询读取两次快照和隐私门禁缺口。动态扩展应建立在闭合的仓储和应用边界之上。

### 阶段 1：外部版本化发布包

保留当前事实结构，先实现外部 active 版本、启动校验、服务器发布、重启和回滚。此阶段不接模型。

### 阶段 2：Claim、TimelineEvent 与展示编排

扩展公开模型、API 和计算指标；建立私有候选审核包与公开包生成流程。当前项目内容迁移为 Claim 和 Evidence 关联。

### 阶段 3：混合检索与模型表达

增加公开 RAG Chunk、关键词与向量索引、EmbeddingProvider、AnswerModelProvider、引用校验和确定性兜底。

### 阶段 4：实现目标首页和完整 Agent

基于指定原型实现固定栏目渲染、身份、推荐问题、独立时间线/Evidence 页面和浏览器七天会话。前端业务数据全部来自 API。

### 后续演进

- 数据库存储与审核后台；
- 服务端会话及合规治理；
- SSE 与断线恢复；
- 多实例发布协调；
- 反馈和检索评测面板。

## 18. 完成定义

- 更新公开知识不需要重新构建 JAR 或镜像；
- 发布、激活、失败回滚和人工 rollback 可验证；
- 页面事实、统计、身份、推荐问题和栏目内容来自 active 版本；
- Claim 与 Evidence 能支撑精确引用；
- RAG 只包含审核后的公开片段；
- 模型和 Embedding 故障不影响核心问答可用性；
- 原有 V0 规范回答继续通过回归测试；
- 公开运行环境无法读取任何私有知识源；
- 浏览器会话七天过期，服务端不持久化访客问题；
- 文件仓储可以在不改变业务用例的前提下替换为数据库仓储。
