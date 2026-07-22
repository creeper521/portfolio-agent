# Portfolio 前后端公开内容联调设计

**日期：** 2026-07-17
**状态：** 已完成实施、发布验证与合并前复核（2026-07-20 状态核对保持有效）
**范围：** V0 公开内容聚合、真实 API 接线、确定性问答联调和单 JAR 验收

## 1. 背景

前端全量重构后，正式页面统一通过 `PublicContentRepository` 读取内容，但默认实现仍是 `previewPublicContentRepository`；首页轻问答、Agent 路由种子和 Agent 工作台也仍通过 `createPreviewAnswer` 在浏览器内生成回答。旧的 Portfolio 与 Answer API 客户端虽然保留在代码中，却没有进入重构后的正式页面数据流。

后端当前提供：

- `GET /api/v1/portfolio`：首页摘要；
- `GET /api/v1/projects/{slug}`：单个项目详情；
- `POST /api/v1/answers`：确定性问答。

这些接口不能一次满足重构后的项目目录、时间线、证据中心和 Agent 工作台。时间线、展示编号和部分关联字段目前只存在于前端 preview 数据中，因此正式运行时尚未形成后端唯一事实源。

## 2. 目标

本次联调必须达到：

1. 正式运行时的 owner、项目、证据、时间线和问答结果全部来自 Spring Boot；
2. preview 数据只作为单元测试或显式开发夹具，不再作为生产默认实现；
3. 新增内容继续只读取 `backend/src/main/resources/public-data/` 下经过审核的公开快照；
4. 现有页面结构、路由、视觉系统、本地会话和响应式交互保持不变；
5. 现有三个 API 的 URL 和响应语义保持兼容；
6. 发布验收通过真实 Spring Boot API 和打包后的前端资源完成，而不是只验证 Vite mock 页面。

## 3. 非目标

本次不引入：

- 大模型、Spring AI、RAG、SSE、数据库、认证或服务端会话；
- 访客问题日志或持久化；
- Pinia、通用请求框架或新的跨模块基础设施层；
- 依据项目描述自动生成或推断时间线；
- 身份化回答逻辑。访问者角色只用于前端问题引导，后端继续执行 V0 确定性回答规则；
- 与联调无关的页面重设计或后端架构重构。

## 4. 总体方案

新增只读聚合接口：

```text
GET /api/v1/public-content
```

该接口一次返回重构后正式页面所需的全部审核公开内容。现有 `/portfolio` 与 `/projects/{slug}` 继续保留，避免改变既有调用方和 DTO 语义；`POST /answers` 继续作为独立的问答用例。

正式数据流为：

```text
public-portfolio.v1.json
  -> JsonPublicPortfolioRepository
  -> PortfolioSnapshotValidator
  -> PortfolioService
  -> PublicContentResponseMapper
  -> GET /api/v1/public-content
  -> ApiPublicContentRepository
  -> usePublicContent
  -> Home / Projects / Project / Timeline / Evidence / Agent
```

问答数据流为：

```text
页面问题
  -> POST /api/v1/answers
  -> AnswerService + DeterministicAnswerEngine
  -> AnswerResponseMapper
  -> 前端 Answer 消息映射器
  -> 首页轻回答或 Agent 本地会话
```

## 5. 后端公开快照

### 5.1 新增正式字段

审核快照增加以下展示和时间线字段：

- Project 增加稳定公开编号 `code`；
- Evidence 增加稳定公开编号 `code`；
- 顶层增加 `timeline` 数组；
- Timeline 项包含 `id`、`dateLabel`、`title`、`problem`、`action`、`impact`、`projectSlugs` 和 `evidenceIds`。

项目的 `evidenceIds` 与问题引用继续使用现有权威关系。聚合响应中的 Evidence `projectSlugs` 由后端根据已校验的项目关系建立反向索引，不在前端推断。

时间线内容必须人工审核后写入公开快照。后端不得从项目描述、日期范围或问答内容自动生成时间线事实。

### 5.2 校验规则

应用启动加载快照时必须校验：

- 项目、证据和时间线 ID 唯一；
- Project/Evidence 的 `code` 非空且在各自集合内唯一；
- 时间线必填文本非空；
- 时间线引用的 project slug 与 evidence ID 均存在；
- 项目引用的 Evidence 存在且 `publicStatus = APPROVED`；
- 时间线不得引用未批准 Evidence；
- API 最终只返回 `APPROVED` Evidence；
- `rawContentPublic` 等现有公开边界继续生效。

任何校验失败都通过 `InvalidPortfolioSnapshotException` 阻止应用以不一致快照启动，不在 Controller 中临时修补数据。

## 6. 聚合 API 契约

`GET /api/v1/public-content` 返回专用 Response DTO，不直接返回 Repository 或 Domain 对象。顶层结构为：

```json
{
  "contentVersion": "2026-07-14.1",
  "publishedAt": "2026-07-14T00:00:00+08:00",
  "owner": {},
  "projects": [],
  "evidence": [],
  "timeline": []
}
```

其中：

- `projects` 返回页面需要的完整公开项目，而不是首页摘要；
- 每个项目包含 `evidenceIds` 和 `suggestedQuestions`；
- `evidence` 只包含审核通过的脱敏索引，并带稳定 `code` 和关联 `projectSlugs`；
- `timeline` 只引用响应中存在的项目和 Evidence；
- 空集合返回 `[]`，不返回 `null`；
- API 不返回私有源对象、原始证据、内部路径或未审核状态。

接口失败继续由 `GlobalExceptionHandler` 产生稳定公开错误，不暴露堆栈和内部异常消息。

## 7. 前端数据边界

### 7.1 API Repository

新增 `ApiPublicContentRepository` 实现现有 `PublicContentRepository`：

- 首次读取时请求 `/api/v1/public-content`；
- 同一应用加载周期中的并发读取共享同一个 Promise；
- 成功后复用不可变公开内容；
- 失败不缓存为永久结果，用户重试时清除失败状态并重新请求；
- `getProject`、`getTimeline` 和 `getEvidence` 只对已经取得的聚合响应进行选择和过滤，不创造事实。

`publicContentRepository` 的生产默认实现切换为 API Repository。`previewPublicContentRepository` 保留，但只能由测试显式导入或注入。

### 7.2 共享加载状态

建立不依赖 Pinia 的轻量 `usePublicContent` 组合式状态：

```text
idle -> loading -> ready
                 -> error -> loading（retry）
```

所有公开内容页面复用同一份加载结果和重试动作。项目 slug 不存在属于“未公开/地址失效”空状态；请求失败属于系统错误状态，两者不得混淆。

加载、空状态和错误状态继续使用现有工程卷宗视觉语言。可恢复错误提供重试入口，文案不得显示请求地址、堆栈、内部错误体或本机信息。

## 8. 问答接线

首页轻问答、Agent 路由种子和 Agent 工作台全部调用现有 `askQuestion(projectSlug, question)`。

前端回答映射器负责：

- 按后端响应中的 section 顺序组合可读正文；
- 保留回答标题；
- 从返回 Evidence 提取 evidence ID；
- 不在前端根据关键词、角色或项目字段生成答案；
- 不把服务端错误保存成 Agent 回答。

问答提交期间：

- 立即显示用户消息；
- 显示克制的回答中状态；
- 禁止同一会话并发重复提交；
- 成功后保存完整 Agent 消息到现有版本化 localStorage；
- 失败时显示稳定错误和重试动作；
- 后端不保存、不记录访问者问题。

从首页携带问题进入 `/agent` 时，URL 只保留问题、项目和来源上下文。Agent 页面重新调用确定性接口取得种子回答，不把回答正文或内部请求 ID写入 URL，也不创建服务端会话。

## 9. 安全与兼容

- 运行时代码仍只读取 `public-data`；
- 新增时间线与展示编号必须接受现有隐私扫描；
- 只有 `APPROVED` Evidence 可进入聚合响应；
- 不记录访问者问题；
- 现有 API 保持可用，新聚合接口是增量契约；
- 前端正式路由和视觉不变；
- 本地会话键、七天过期、删除/清除和工作台宽度存储规则不变；
- 单 JAR 和 Docker 交付方式不变。

## 10. 测试策略

实现遵循 RED、GREEN、REFACTOR。

### 10.1 后端

- 快照模型和值语义测试；
- JSON Repository 对新增字段的反序列化测试；
- Validator 对重复编号、缺失字段、悬空引用和未审核 Evidence 引用的失败测试；
- Service 对完整公开聚合和 Evidence 反向关系的测试；
- Mapper 与 Controller 的响应契约测试；
- 现有 `/portfolio`、`/projects/{slug}` 与 `/answers` 回归测试；
- 代码质量、架构和隐私检查。

### 10.2 前端

- API Repository 的成功、单次请求复用、失败后重试和项目查找测试；
- `usePublicContent` 的 loading、ready、error 和 retry 测试；
- 回答 section 与 Evidence 引用映射测试；
- 首页和 Agent 的异步提交、防重复提交、成功和失败测试；
- 各页面加载、空状态和错误状态测试；
- preview Repository 不再进入生产默认依赖的约束测试。

### 10.3 真实联调与发布验收

Playwright 的完整联调验收必须访问真实 Spring Boot API 和生产前端资源。发布验证应在完成前端构建与 Maven 打包后启动 `portfolio-agent.jar`，再执行以下路径：

1. 首页从聚合 API 加载并完成一次真实确定性问答；
2. 项目详情跳转到关联 Evidence；
3. 时间线链接到项目和 Evidence；
4. Agent 调用真实 `/answers`，完成回答与 Evidence 关联；
5. 刷新恢复已完成的本地会话，并可清除；
6. 1219px 和 980px 工作台抽屉无横向溢出；
7. API 不可用或返回错误时显示安全错误和重试入口；
8. JAR 首页、SPA 深链接和三个既有 API 继续可用。

`scripts/verify-release.ps1`、README 和 Playwright 配置必须描述并执行同一种拓扑，消除“文档声称启动 JAR、实际只启动 Vite”的偏差。前端独立视觉测试与完整联调测试应使用不同命令或显式运行模式，避免把 mock 验收误报为发布验收。

## 11. 完成标准

满足以下条件后才能声明前后端联调完成：

- 正式源码不再通过 preview Repository 或 `createPreviewAnswer` 提供运行时事实；
- 所有正式路由均能从真实聚合 API 加载；
- 首页和 Agent 均调用真实 `/answers`；
- 新增快照引用规则具有失败测试；
- 现有 API 回归测试通过；
- 前端测试、构建、后端测试、代码质量、架构、隐私、Maven 打包和真实浏览器验收全部通过；
- 打包 JAR 可独立提供 SPA 与 API；
- Git 工作区只包含本次设计与后续计划明确列出的文件，以及用户原有未跟踪文件。

## 12. 实施边界

本设计适合作为一个实施计划，按以下顺序拆分：

1. 后端快照模型与校验；
2. 聚合 Service、DTO、Mapper 和 Controller 契约；
3. 前端 API Repository 与共享加载状态；
4. 首页和 Agent 真实问答；
5. 加载、错误和重试 UI；
6. 真实 JAR 联调、发布脚本和文档对齐；
7. 全量验证。

若实现过程中发现需要改变 V0 产品边界、引入私有数据或修改现有 API 语义，必须停止实施并重新评审设计。
