# 公开作品集 API 单资源收敛设计
<!-- DOCUMENT_STATUS: APPROVED -->

> **日期：** 2026-08-21
> **状态：** 用户已审核批准，作为 LEVEL_3 公开合同替换的设计权威
> **适用仓库：** `D:\code\agent`
> **范围：** 公开作品集读取面、客户端诊断路径、前端公开快照传输、打包验收与当前维护文档

## 1. 目标与决策

当前公开读取面同时存在 `/api/v1/portfolio`、`/api/v1/projects/{slug}`、`/api/v1/cases`、`/api/v1/cases/{slug}` 与 `/api/v1/public-content`。这些路径形成了摘要、详情、列表和完整聚合多套公开合同，但当前 Vue 生产链只消费完整聚合响应：`usePublicContent` 通过 `apiPublicContentRepository` 调用 `getPublicContent()`，随后在前端 Repository 内从同一快照派生 Project、Case、Timeline 与 Evidence。

本设计冻结以下目标：

```text
GET    /api/portfolio
POST   /api/client-diagnostics

POST   /api/agent/turns
DELETE /api/agent/turns/{requestId}
GET    /api/agent/conversations/current
DELETE /api/agent/conversations/current
```

其中 `GET /api/portfolio` 是公开作品集读取的唯一 HTTP 资源，直接承接当前 `/api/v1/public-content` 的完整聚合语义。Agent 四条无版本资源及其合同保持不变；客户端诊断保持独立的运维接口，不归入 Portfolio 或 Agent 领域。

本次替换不保留旧路由、重定向、转发、feature flag、payload 版本开关或运行时 fallback。回退只使用 Git、已验证 JAR 或整体部署版本。

## 2. 当前事实基线

### 2.1 真实消费面

- 页面加载只通过 `getPublicContent()` 请求 `/api/v1/public-content`；
- `apiPublicContentRepository` 缓存同一个 Promise，并从聚合快照派生各页面需要的数据；
- `portfolioApi.getPortfolio()` 与 `portfolioApi.getProject()` 没有非测试调用方；
- `/api/v1/cases` 与 `/api/v1/cases/{slug}` 没有前端生产调用方，当前正面消费只存在于后端测试与 packaged-JAR 脚本；
- 预览模式使用本地 fixture，不发送公开快照 HTTP 请求。

因此，删除摘要和详情 HTTP 路由不会改变当前页面数据流；需要保护的是聚合响应的字段、发布一致性、隐私过滤、能力门控和 `no-store` 语义。

### 2.2 当前响应权威

当前 `PublicContentResponse` 是目标快照合同的直接来源。`runtimeBundleHash` 同时进入前端公开类型与上游发布身份一致性链；`agentAvailability` 是前端 Agent 输入能力 fail-closed 门控的数据源。迁移不得删除、改名、重解释或改变任何现有字段类型。

### 2.3 路由退役行为

`SpaForwardController` 只转发显式网页路径，例如 `/projects`、`/cases`、`/timeline`、`/evidence` 与 `/agent`，不匹配 `/api/**`。旧 Controller 删除后，退休的 `/api/v1/*` 路由必须由 Spring 返回 404，不得落入 SPA `index.html`。

## 3. 模块与接口

### 3.1 Backend Portfolio HTTP 模块

目标 Controller 命名为 `PortfolioController`，唯一映射为：

```java
@RequestMapping("/api/portfolio")
```

它只负责：

1. 调用 `PortfolioService.getPublicContent()` 取得同一发布版本的完整公开投影；
2. 调用 `PortfolioResponseMapper` 生成 `PortfolioSnapshotResponse`；
3. 注入现有 `AgentAvailabilityResponse`；
4. 返回 `Cache-Control: no-store`。

Controller 不直接访问 Bundle、数据库、Agent State、Provider 或私有治理目录。现有 Portfolio Service 与 Repository 仍是内容读取权威，本次不改变发布数据来源和投影规则。

### 3.2 公开响应接口

`PublicContentResponse` 更名为 `PortfolioSnapshotResponse`。JSON 字段名、字段类型、序列化形状与语义保持不变。完整 14 字段合同为：

| 字段 | 冻结语义 |
|---|---|
| `contentVersion` | 当前公开发布版本 |
| `runtimeBundleHash` | 运行时公开 Bundle 身份摘要 |
| `publishedAt` | 当前公开版本发布时间 |
| `owner` | 已审核的公开所有者投影 |
| `collections` | 已发布 Case Collection 投影 |
| `projects` | 完整公开 Project 详情投影 |
| `cases` | 完整公开 Case 详情投影 |
| `claims` | 当前发布版本的公开 Claim |
| `claimEvidenceLinks` | Claim 与 Evidence 的公开关联 |
| `evidence` | 仅包含允许公开的 Evidence 投影 |
| `timeline` | 公开时间线投影 |
| `caseSlugsByEvidenceId` | Evidence 到 Case slug 的公开反向索引 |
| `questionPresets` | 当前发布且合同有效的问题预设 |
| `agentAvailability` | Agent 与自由文本语义路由的 fail-closed 可用性投影 |

迁移合同测试必须逐字段锁定这 14 个字段，不允许以文档中的示意列表替代序列化验证。

### 3.3 Frontend Repository seam

前端继续以 `PublicContentRepository` 作为页面读取 seam。生产 Adapter 只进行一次 `GET /api/portfolio`，缓存同一个 Promise，并在内存中派生 Project、Case、Timeline 与 Evidence。页面不直接拼接 HTTP 路径，也不改为多请求聚合。

传输方法 `getPublicContent()` 应更名为能表达目标合同的 `getPortfolioSnapshot()`；无生产调用方的 `portfolioApi.getPortfolio()` 与 `portfolioApi.getProject()` 删除。Repository 的领域方法 `getPortfolio()`、`getProjects()`、`getProject()`、`getTimeline()` 与 `getEvidence()` 可以继续保留，因为它们是页面调用的读取接口，不等同于退休的 HTTP 子资源。

### 3.4 客户端诊断

`FrontendDiagnosticsController`、`FrontendDiagnosticsBodyLimitFilter`、前端 `diagnosticTransport`、测试和 packaged-JAR 脚本同时从 `/api/v1/client-diagnostics` 切换到 `/api/client-diagnostics`。请求体上限、隐私字段白名单、错误处理和无内容响应语义保持不变。

## 4. Replacement Manifest

### 4.1 建立目标权威

- 使用 `PortfolioController` 暴露唯一 `GET /api/portfolio`；
- 使用 `PortfolioSnapshotResponse` 承接完整 14 字段合同；
- `PortfolioResponseMapper` 只保留完整快照和仍被快照嵌套结构使用的映射；
- 前端生产 Adapter 只调用 `/api/portfolio`；
- 客户端诊断原子切换为 `/api/client-diagnostics`。

### 4.2 同期退休

必须在同一个 Replacement Slice 中删除以下路由：

```text
GET  /api/v1/portfolio
GET  /api/v1/projects/{slug}
GET  /api/v1/cases
GET  /api/v1/cases/{slug}
GET  /api/v1/public-content
POST /api/v1/client-diagnostics
```

同期删除或收敛：

- 旧摘要/详情 `PortfolioController` 的四个 Handler；
- 旧 `PublicContentController` 类名与 `/api/v1/public-content` 映射；
- `PortfolioHomeResponse`；
- `PortfolioService.getPortfolio()`、`getProject()`、`getCases()`、`getCase()` 中删除路由后无调用方的方法；
- `PortfolioOverview` 等仅服务退休摘要接口且通过零引用确认的类型；
- `PortfolioResponseMapper.toPortfolioResponse()` 与独立列表映射 `toCaseResponses()`；
- 前端 `portfolioApi.getPortfolio()`、`portfolioApi.getProject()` 及仅验证旧 HTTP 面的测试；
- scripts、fake server、E2E 和当前维护文档中的旧路径。

`CaseSummaryResponse` 不在删除清单中：它仍是 `ProjectDetailResponse.featuredCases` 的嵌套合同。`ProjectDetailResponse`、`CaseDetailResponse`、`ProjectDetails` 与 `CaseDetails` 也继续被完整快照使用，不能因删除独立详情路由而误删。

## 5. 缓存、安全与失败语义

- `GET /api/portfolio` 无需身份认证；
- 响应保持 `Cache-Control: no-store`，因为同一快照包含运行时 `agentAvailability`；
- 公开内容仍只来自随包审核快照或已激活的公开数据库投影；
- 只有 `publicStatus = APPROVED` 的 Evidence 可以进入响应；
- 不返回私有路径、原始内部链接、访客问题、Prompt、模型原始输出、Token、Handle 或凭据；
- `agentAvailability` 缺失、损坏或不可确认时，前端必须继续 fail-closed，不能把未知解释为可用；
- 删除旧路由后统一返回 404，响应不得是 HTML SPA 页面；
- 不新增 CORS、认证、动态发布、分页、条件请求或缓存协商。

## 6. 为什么不保留详情子资源

本次只建立 `GET /api/portfolio`，不建立 `/api/portfolio/projects/{slug}` 或 `/api/portfolio/cases/{slug}`：

1. 当前没有真实生产消费者；
2. 完整聚合已携带相同详情，没有新增信息；
3. 单响应保证所有引用属于同一 `contentVersion`；
4. 保留子资源会扩大公共合同、fixtures、错误语义和发布门；
5. `agentAvailability` 要求根响应 `no-store`，当前不存在可证明的细粒度缓存收益；
6. 未来出现真实独立消费者时，新增子资源是兼容性加法，无需现在预建。

同样不采用 `/api/web/*` 或 `/api/pages/*`，避免把领域读取接口绑定到当前展示客户端。

## 7. 验证与 Exit Gates

### 7.1 目标合同

1. `GET /api/portfolio` 返回 HTTP 200、JSON 和 `Cache-Control: no-store`；
2. 序列化测试逐字段锁定完整 14 字段合同；
3. `runtimeBundleHash`、`contentVersion`、交叉引用、QuestionPreset 合同与 Evidence 公开过滤保持现有不变量；
4. `agentAvailability` 在可用、模型关闭、Provider 禁止和 Context 禁用场景继续正确 fail-closed；
5. 前端生产加载只发一次 `/api/portfolio` 请求，Repository 缓存和预览 fixture 行为保持不变。

### 7.2 退休证明

对以下旧路径建立显式 404 回归，且断言响应不是 SPA HTML：

```text
/api/v1/portfolio
/api/v1/projects/{slug}
/api/v1/cases
/api/v1/cases/{slug}
/api/v1/public-content
/api/v1/client-diagnostics
```

Backend/Frontend 生产源码、运行脚本、fake server、活动验收路径和当前权威文档不得再把 `/api/v1` 声明或调用为活动接口。唯一代码豁免是集中维护的退休合同回归，它可以列出上述六条旧路径并只断言 404 与非 SPA 响应；不得在其他测试、fixture 或 helper 中复制旧路径。本文的迁移基线/退休清单，以及正式标记为 `HISTORICAL`、`SUPERSEDED` 或 `NON_AUTHORITATIVE` 的历史文档，保留历史事实，不做机械改写，也不计入生产零引用失败。零引用门必须使用精确文件与语义豁免，不能全局忽略 `/api/v1`。

### 7.3 联合验证

- 将 `run-jar-e2e.ps1` 的 Case 详情请求改为对 `/api/portfolio` 中目标 Case 及其公开 Evidence 做正面断言；
- fake server 只镜像 `/api/portfolio` 和新诊断路径；
- frontend diagnostics 三条 packaged 断言切换到 `/api/client-diagnostics`；
- Backend 全量测试与 clean package；
- Frontend 全量测试、类型检查和 build；
- documentation、privacy、code-quality、architecture 与 release gates；
- PostgreSQL packaged-JAR 桌面/移动现有矩阵中所有公开内容准备步骤使用新路径；
- 不要求新的真实 Provider 调用，因为本次不改变 Agent Command、Goal、Plan、Execution、PublicAgentTurn 或模型合同；现有 Provider lane 只需证明公开快照改名没有破坏其准备步骤。

## 8. 文档与机器状态

实施完成后：

- 更新 `README.md` 的公开 HTTP 面；
- 更新 `docs/08-当前实现状态.md`，记录公开读取面收敛为无版本单资源；
- 更新 `docs/11-项目演进日志.md`，记录从多路由读取面到原子公开快照的演进关系；
- 必要时更新 `docs/05-公开发布包契约.md` 与 `docs/06-公开内容发布运行手册.md` 的当前路径；
- 更新 `docs/agent-architecture-status.json` 的当前证据，但只有实际完成全部 Exit Gates 后才恢复或声明 `COMPLETE`；
- 不重写历史 specs、plans、reports 和 handoffs 中的旧路径事实。

## 9. 实施纪律与回退

这是公开 HTTP 合同的 LEVEL_3 变更。实施计划必须把 Backend Controller/DTO、Frontend Adapter、客户端诊断、脚本、测试和当前文档作为一个原子 Replacement Slice：先建立目标权威，迁移全部调用方，证明替代安全，再删除旧权威并运行零引用门。

任何中间 commit 可以暂时不满足完整切换，但不得被宣称为可部署目标版本；Slice Exit 时不得存在新旧双栈。仓库已有用户改动必须保留，实施不得 reset、restore、覆盖或擅自提交。

出现回归时使用 Git/JAR/整体部署版本回退。不得通过重新注册 `/api/v1`、运行时开关或请求转发回退。

## 10. 完成定义

只有同时满足以下条件，才可声明公开作品集 API 收敛完成：

1. 六条目标 HTTP 资源是当前完整公开面；
2. `/api/portfolio` 是作品集读取的唯一 HTTP 权威；
3. 14 字段合同、发布一致性、隐私过滤、`agentAvailability` 与 `no-store` 全部保持；
4. 六条退休路由均显式返回 404 且不进入 SPA；
5. Frontend、scripts、fake server、packaged-JAR 与当前文档全部迁移；
6. 活动生产与验收路径的 `/api/v1` 零引用门通过，退休合同测试、本文迁移记录与历史文档豁免精确且可审计；
7. 全量与风险对应的联合门实际运行并通过；
8. 没有运行时兼容桥、旧链 fallback 或第二公开快照权威；
9. `docs/08`、`docs/11` 和架构状态已按真实证据更新。
