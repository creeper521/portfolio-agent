# Agent P3 前端交接：后端公共契约、安全边界与验收要求
<!-- DOCUMENT_STATUS: HISTORICAL -->

> 日期：2026-08-12
> 状态：P3 设计基线配套交接；尚未实现
> 后端权威设计：`../superpowers/specs/2026-08-11-bounded-tool-orchestration-design.md`
> 后端实施计划：`../superpowers/plans/2026-08-12-agent-p3-backend-implementation.md`
> 当前实现基线：P0、P1、P2 已完成；本文字段在 P3-E 原子接入
> 本文供独立前端 Agent 进行修改或原型设计，不授权其改变后端业务语义。

## 0. 交接目标

本文把 P3 后端设计投影为前端可以直接实现和验收的公共契约，覆盖：

1. `/api/v2/answers` 的请求、成功联合响应和安全状态；
2. 用户可见执行快照；
3. 公开来源引用；
4. 页签级 ResumeToken、刷新恢复和主动清除；
5. ContextHandle 的继续追问与推荐分支；
6. 幂等重试、Context Store 故障和异常恢复；
7. P3-E 的前后端原子迁移条件。

本文不决定页面视觉、组件树、卡片形态、色彩、动效、响应式布局或高保真原型。前端 Agent 可以自行设计这些内容，但必须保持本文的状态语义、信息边界和验收行为。

## 1. 权威边界

后端是以下事项的唯一权威：

- 语义任务与执行结果；
- 主体范围和 Context 授权；
- 推荐候选、排序、理由和排除条件；
- Evidence 准入与公开来源；
- 执行阶段的最终状态；
- 可续接 Context、Active Context 和分支关系；
- TTL、清除结果、幂等状态和安全原因码。

前端不得：

- 根据问题文本或聊天气泡重建 `SubjectScope`、推荐条件或 Context；
- 对后端推荐重新排序、补项、去重或推断负面事实；
- 根据网络耗时伪造“正在检索/正在核验”等真实工具阶段；
- 把旧 `claimIds/evidenceIds` 当作 P3 公开引用；
- 在浏览器持久化问题、答案、Context payload、Evidence 或 ContentVersion；
- 把 ResumeToken 放入 URL、Cookie、日志、埋点或错误上报。

如果前端方案需要新增或改变字段、枚举、状态含义或 Context 解析顺序，应先修订后端权威 Spec，再修改前端；不能只在前端自行兼容。

## 2. 版本与发布方式

P3 不保留长期新旧双轨。内置 SPA 与后端位于同一 JAR，P3-E 原子发布。

稳定版本字段：

```text
agentTurn.contractVersion = stp-v1       // 已有 P2 语义轮次
agentTurn.execution.contractVersion = p3-display-v1
GET context summary.contractVersion = p3-context-summary-v1
```

P3 完成时：

- 前端只消费 `sourceReferences`，不再消费回答中的 `claimIds/evidenceIds`；
- 前端只发送 `contextReference`，不再回传完整 `recommendationContext/referenceContext`；
- `agentTurn.plan` 仍是 P2 的用户可见语义计划；
- `agentTurn.execution` 是 P3 的最终执行快照，两者不能相互替代。

迁移提交可以为原子切换测试短暂双写，但不得进入最终生产契约。

## 3. 回答请求契约

### 3.1 HTTP

```http
POST /api/v2/answers
Content-Type: application/json
X-Conversation-Resume-Token: <opaque token>   // 已有会话时才发送
```

首次新会话不发送 ResumeToken Header。已有会话的每次回答、确认、重规划和 Refine 都发送与该本地会话绑定的 Token。

### 3.2 请求体增量

保留现有 `ConversationAnswerRequest` 和 `requestToken` UUID。P3 不新增请求 ID Header。

```ts
type ConversationContextType =
  | 'RECENT_SEMANTIC_TASK'
  | 'RECOMMENDATION'

interface ContextReferenceRequest {
  contextHandle: string
  expectedContextType: ConversationContextType
}

interface P3AnswerRequest extends ExistingAnswerRequest {
  requestToken: string
  contextReference?: ContextReferenceRequest
}
```

`contextReference` 是回答请求的顶层强类型字段，不放入现有 `context` 对象。它只在用户明确从某个结果继续时发送，例如点击某条回答的“继续追问”或从旧推荐分支调整。

约束：

- `contextHandle` 必须原样来自该任务响应，前端不得生成、解析或修改；
- Fact/Compare 结果使用 `RECENT_SEMANTIC_TASK`；
- Recommendation/Refine 结果使用 `RECOMMENDATION`；
- 没有明确结果关联的普通追问不发送该字段，由后端 Active Context 解析；
- ResumeToken 与 ContextHandle 必须同时存在，只有 Handle 不能获得 Context；
- 不再发送完整 `recommendationContext`、`referenceContext`、ContentVersion、SubjectScope 或推荐规则状态。

推荐请求示例：

```json
{
  "turnId": "turn-ui-17",
  "requestToken": "6b2d8895-4108-4b4d-aee0-21f6e7c4f333",
  "action": "ASK",
  "agentTurnContract": "stp-v1",
  "question": "优先考虑有验证闭环的项目",
  "messages": [],
  "context": {
    "projectSlug": null,
    "caseSlug": null,
    "audienceRole": "INTERVIEWER",
    "source": "AGENT_PAGE",
    "coveredTopics": []
  },
  "contextReference": {
    "contextHandle": "opaque-context-handle",
    "expectedContextType": "RECOMMENDATION"
  }
}
```

## 4. 回答成功联合类型

P3-E 后，`POST /api/v2/answers` 的 `200` 响应必须先按 `responseKind` 分流，禁止根据 `blocks` 是否存在猜类型。

```ts
type P3AnswerSuccess = AnswerResponse | CompletionReceiptResponse

interface AnswerResponse extends ExistingAnswerResponse {
  responseKind: 'ANSWER'
  conversation: ConversationResponse
  agentTurn?: P3AgentTurnResponse
}

interface CompletionReceiptResponse {
  responseKind: 'COMPLETION_RECEIPT'
  turnId: string
  requestToken: string
  requestStatus: 'REQUEST_ALREADY_COMPLETED'
  completedTasks: CompletionReceiptTask[]
  conversation: ConversationResponse
}

interface CompletionReceiptTask {
  displayIndex: string
  status: PublicTaskStatus
  contextHandle?: string
}

type PublicTaskStatus =
  | 'COMPLETED'
  | 'PARTIAL'
  | 'NOT_SUPPORTED'
  | 'PRESENTATION_BLOCKED'
  | 'REJECTED'
  | 'FAILED'
  | 'DEPENDENCY_UNAVAILABLE'
  | 'NOT_EXECUTED_BUDGET'
  | 'CANCELLED'
```

`COMPLETION_RECEIPT` 表示同一请求此前已经执行且 Context 写入已经提交，但原回答正文不被服务端持久化，所以无法重放。前端必须显示确定性提示，允许用户基于已保存 Context 继续或重新提问；不能生成空回答气泡、复原旧正文或再次自动提交一个新 `requestToken`。如果这是无 ResumeToken 的首轮丢响应恢复，`conversation.resumeToken` 会返回同一 conversation 的重签 Token；前端把它作为唯一有效 Token 保存。同一 `requestToken` 发起重试时，前端为网络 attempt 维护单调序号并忽略更早 attempt 的迟到响应，使其携带的旧 Token 无法覆盖新回执。

## 5. Conversation 响应

```ts
type ConversationContinuationStatus =
  | 'AVAILABLE'
  | 'PERSISTENCE_UNAVAILABLE'
  | 'CONTEXT_EXPIRED'
  | 'CONTEXT_CLEARED'
  | 'NOT_APPLICABLE'

interface ConversationResponse {
  resumeToken?: string
  continuationStatus: ConversationContinuationStatus
  activeContextSummary?: ConversationContextSummary
}
```

语义：

| 状态 | 当前答案 | 下一步前端行为 |
| --- | --- | --- |
| `AVAILABLE` | 正常显示 | 保存首次返回的 Token；允许连续追问 |
| `PERSISTENCE_UNAVAILABLE` | 当前答案仍可能完全有效 | 显示“不保证刷新恢复/连续调整”的非阻断提示；不要降低证据状态 |
| `CONTEXT_EXPIRED` | 当前请求中依赖 Context 的任务不可用，或恢复失败 | 删除本地 Token 和 ContextHandle；按新会话开始 |
| `CONTEXT_CLEARED` | 会话已主动清除 | 删除 Token、恢复卡和该会话本地 UI 状态 |
| `NOT_APPLICABLE` | 本轮不产生可续接业务状态 | 不展示续接能力 |

`degraded` 与 `continuationStatus` 是不同维度。Context 写入失败不能让前端把一个证据充分的答案标成证据不足。

## 6. 任务 ContextHandle

P3 在已有字段上增加：

```ts
interface P3CompletedTaskResponse extends ExistingCompletedTaskResponse {
  contextHandle?: string
}
```

只有产生可续接 Context 的完成任务才返回 Handle。Handle 与具体结果关联，适合用户从那条结果继续。

前端状态规则：

- ContextHandle 只保存在当前页签的消息/结果内存中，不写 `sessionStorage`；
- 渲染“从这条结果继续”时，把对应 Handle 和固定的 `expectedContextType` 发送给后端；
- 新推荐或 Refine 返回新 Handle，旧 Handle仍可形成合法分支；
- 不因某个旧分支不再 Active 就删除它的 Handle；
- 后端返回过期、被清理、类型不匹配或不属于当前 Token 时，前端停止该分支，不从旧答案猜 Context。

## 7. 用户可见执行快照

`agentTurn.plan` 保持现有 P2 语义计划。P3 新增同级字段：

```ts
type ExecutionFinalStatus = 'COMPLETED' | 'PARTIAL' | 'SKIPPED' | 'FAILED'

type ExecutionStageCode =
  | 'SCOPE_CONFIRMED'
  | 'MATERIALS_RETRIEVED'
  | 'EVIDENCE_VALIDATED'
  | 'RESULT_COMPOSED'

interface ExecutionDisplayStageResponse {
  code: ExecutionStageCode
  label: string
  status: ExecutionFinalStatus
}

interface ExecutionDisplayTaskResponse {
  displayIndex: string
  finalStatus: ExecutionFinalStatus
  stages: ExecutionDisplayStageResponse[]
}

interface ExecutionDisplayPlanResponse {
  contractVersion: 'p3-display-v1'
  snapshotType: 'FINAL'
  overallStatus: ExecutionFinalStatus
  tasks: ExecutionDisplayTaskResponse[]
}

type P3AgentTurnResponse = ExistingAgentTurnResponse & {
  execution?: ExecutionDisplayPlanResponse
}
```

行为要求：

- 同步请求等待期间只能显示本地固定骨架；
- 收到响应后用服务端 `FINAL` 快照替换骨架；
- 最终快照出现 `PENDING` 或 `IN_PROGRESS` 属于契约错误，不要继续渲染为实时进度；
- stage `label` 是后端安全文案，可直接展示；code 用于稳定测试和可访问性关联；
- 不根据阶段名、时长或顺序推断真实工具、Adapter、检索次数或内部错误；
- P3 v1 不实现 SSE、WebSocket 或轮询进度。

前端可以自行决定把快照做成折叠区、进度列表或其他视觉形式，也可以后续单独进行体验优化，但不得展示拟真的命令、工具日志、百分比或思维链。

## 8. 公开来源引用

P3 回答章节和推荐项统一使用：

```ts
type PublicSourceType =
  | 'COLLECTION'
  | 'DOCUMENT'
  | 'SCREENSHOT'
  | 'CODE'
  | 'TEST_RESULT'

interface PublicSourceReference {
  referenceKey: string
  label: string
  sourceType: PublicSourceType
  subjectRoute: string
  evidenceRoute?: string
  publishedVersion: string
}

interface P3AnswerBlock {
  sourceScope: 'GENERAL' | 'PORTFOLIO'
  sectionType?: AnswerSectionType
  title?: string
  content: string
  sourceReferences: PublicSourceReference[]
}

interface P3RecommendationItem {
  portfolioId: string
  title: string
  route: string
  matchReasons: string[]
  sourceReferences: PublicSourceReference[]
}
```

规则：

- `referenceKey` 是公开稳定 code，不是 Claim/Evidence/Chunk 或数据库 ID；
- Route 只接受站内相对公开路由；前端不得拼接对象存储地址；
- `evidenceRoute` 缺失时，Evidence Desk 可以用 `referenceKey` 定位公开摘要，但不得尝试内部 ID；
- Portfolio 事实和推荐理由必须显示其关联来源；General 内容可以没有 Portfolio 来源；
- 数组顺序由后端确定，前端只做展示层去重时也必须保持第一次出现顺序，不能改变结论与来源绑定；
- P3 最终类型删除 `claimIds/evidenceIds`，相关 Evidence Desk、测试 mock 和映射必须同步迁移。

`PublicSourceType` 直接复用当前公开 Evidence 类型闭集；PROJECT/CASE 归属由 `subjectRoute` 表达，不是 `sourceType`。前端不得增加自由字符串兜底。

## 9. TaskOutcome 与安全原因

P3 task summary 的公共 resolution 闭集为：

```text
ANSWERED
PARTIALLY_ANSWERED
NOT_SUPPORTED
PRESENTATION_BLOCKED
REJECTED
DEPENDENCY_UNAVAILABLE
NOT_EXECUTED_BUDGET
BOUNDARY
NOT_APPLICABLE
```

顶层 `AnswerResolution` 新增：

```text
PARTIALLY_ANSWERED
PRESENTATION_BLOCKED
```

公共任务展示状态固定为：

```text
COMPLETED
PARTIAL
NOT_SUPPORTED
PRESENTATION_BLOCKED
REJECTED
FAILED
DEPENDENCY_UNAVAILABLE
NOT_EXECUTED_BUDGET
CANCELLED
```

前端只根据后端闭集状态和 `SafeReasonCode` 映射文案。P3 首批新增或权威使用的安全原因包括：

```text
SCOPE_CONFLICT
UNSUPPORTED_RETRIEVAL_PROFILE
CONTEXT_VERSION_UNAVAILABLE
REQUIRED_DEPENDENCY_UNAVAILABLE
TURN_BUDGET_UNAVAILABLE
EVIDENCE_NOT_FOUND
EVIDENCE_PARTIALLY_COVERED
OUTPUT_POLICY_BLOCKED
CAPABILITY_TEMPORARILY_UNAVAILABLE
EVIDENCE_INTEGRITY_FAILURE
CONTEXT_PERSISTENCE_UNAVAILABLE
CONTEXT_STORE_TEMPORARILY_UNAVAILABLE
CONTEXT_PRUNED
```

未知状态、未知 stage code 或格式错误应进入统一契约错误恢复，不显示原始值；未知 `SafeReasonCode` 可以使用通用安全文案，但日志只能记录 code 和计数，不能附带问题、答案、Token 或来源正文。

## 10. ResumeToken 与本地会话

### 10.1 存储模型

ResumeToken 是 256-bit 随机不透明值。前端只允许：

```text
运行期内存：每个 AgentSession 绑定自己的 ResumeToken
sessionStorage：只保存当前活跃 AgentSession 的 ResumeToken，用于刷新恢复
```

因此：

- 多个本地 AgentSession 不能共享一个服务端 conversation；
- 切换本地会话时，把 `sessionStorage` 槽位替换为目标会话 Token；
- 新建会话时清空槽位，首个成功回答再保存新 Token；
- 刷新后最多恢复刷新前活跃的一个会话，不恢复本地聊天气泡列表；
- 新页签没有继承能力；关闭页签后不保证恢复；
- 禁止 `localStorage`、IndexedDB、Cookie、URL/query/hash 和 Service Worker Cache。

若浏览器禁用 `sessionStorage` 或写入失败，当前页签内存对话仍可运行；前端显示无法刷新恢复的非阻断状态，不把 Token 降级写入其他持久介质。

### 10.2 传输和脱敏

- 后续请求仅通过 `X-Conversation-Resume-Token` Header 发送；
- 所有回答与 Context API 响应都按 `Cache-Control: no-store` 处理；
- 前端网络封装、错误边界、诊断事件、Sentry 类上报、埋点和截图不得采集该 Header 或 `conversation.resumeToken`；
- 不把 Token 输出到 DOM、复制文本、开发调试提示或用户可见错误；
- Token 不能被当作登录身份或私有数据权限。

## 11. 刷新恢复 API

```http
GET /api/v2/conversation-context
X-Conversation-Resume-Token: <opaque token>
```

成功响应：

```ts
interface ConversationContextSummaryResponse {
  contractVersion: 'p3-context-summary-v1'
  continuationStatus: 'AVAILABLE'
  summary: ConversationContextSummary
}

interface ConversationContextSummary {
  recentTaskType?: 'FACT' | 'COMPARE' | 'RECOMMENDATION' | 'REFINE'
  subjectLabels: string[]
  facetLabels: string[]
  comparisonDimensionLabels: string[]
  preferenceLabels: string[]
  canRefine: boolean
}
```

恢复卡只显示这些服务端确定性字段以及“清除本次对话”。不得显示或推断：

- 原问题、原答案或聊天气泡；
- 推荐理由、Evidence 正文或执行过程；
- ContextHandle、ContentVersion、内部 ID；
- 模型生成摘要。

Token 已过期、被清除、不存在或归属校验失败时，恢复接口统一返回不可恢复状态，不向前端区分安全原因：

```json
{
  "contractVersion": "p3-context-summary-v1",
  "continuationStatus": "CONTEXT_EXPIRED"
}
```

前端收到后清除 Token，不显示恢复卡，并建立新会话。格式非法的 Token 使用公共错误码 `INVALID_CONVERSATION_RESUME_TOKEN`，处理结果相同。

## 12. 主动清除 API

```http
DELETE /api/v2/conversation-context
X-Conversation-Resume-Token: <opaque token>
```

服务端对语法合法 Token 幂等返回 `204 No Content`，并删除 Token 映射、全部 Context 版本和 request receipt。不存在或已经删除的 Token 也返回 204，避免泄露存在性。

清除动作的前端顺序：

1. 对目标本地会话的 Token 调用 DELETE；
2. 收到 204 后删除该会话内存 Token；
3. 如果它是活跃会话，同时删除 `sessionStorage` Token、恢复卡和该会话页签 UI；
4. 后续提问创建全新 conversation。

网络失败时不得本地宣称“服务端已清除”。可以先隐藏敏感 UI 并保留一个仅内存的待重试清除动作，但不能把待清除 Token 写入长期存储；用户应得到“清除尚未确认”的明确状态。

删除单个本地会话前调用其 DELETE；“清空全部会话”对当前内存中每个不同 Token 分别调用幂等 DELETE。前端可以并发发送，但必须逐项确认结果。

## 13. Context Store 故障

### 13.1 当前任务不依赖旧 Context

Fact/Compare/Recommendation 已形成有效答案，但保存新 Context 失败时：

```text
HTTP 200
答案按真实 Evidence 状态显示
degraded = true
conversation.continuationStatus = PERSISTENCE_UNAVAILABLE
reasonCode = CONTEXT_PERSISTENCE_UNAVAILABLE
```

前端只禁用或提示“连续追问/刷新恢复不可用”，不得把答案改成失败或证据不足。

### 13.2 当前任务强依赖旧 Context

Refine 或明确连续任务读取 Context Store 失败时，后端失败关闭，不执行 Portfolio Retrieval：

```text
task status = FAILED
reasonCode = CONTEXT_STORE_TEMPORARILY_UNAVAILABLE
retryable = true
```

前端保留用户输入和原 ContextHandle 供显式重试，重试必须沿用原 `requestToken`。不能删除 Token、创建新请求冒充同一操作，也不能从聊天历史重建约束。

Context 已实际过期/清除/不属于会话时使用 `DEPENDENCY_UNAVAILABLE`，这与基础设施暂时不可读不同。

## 14. 请求幂等与网络重试

每次用户动作只通过 `crypto.randomUUID()` 生成一次 UUIDv4 `requestToken`。以下操作都沿用该 Token：请求超时后的重试、断网重试、Context Store 暂时失败后的显式重试。

后端行为：

| 场景 | HTTP/响应 | 前端行为 |
| --- | --- | --- |
| 同 key、同指纹、仍执行中 | `409 REQUEST_IN_PROGRESS`，可带 `retryAfterSeconds` | 保留加载/可重试状态；不得换 Token 并发重发 |
| 同 key、同指纹、已经完成 | `200 COMPLETION_RECEIPT` | 不伪造原答案；保存可能重签的 Token，提示已完成且可基于 Context 继续 |
| 同 key、不同指纹 | `409 IDEMPOTENCY_KEY_CONFLICT` | 停止自动重试，报告客户端状态错误 |
| 从未执行 | 正常执行并返回 `ANSWER` | 正常接收 |

前端在切换会话、调整表单或二次点击时必须使用请求快照，不能让同一个 `requestToken` 对应变化后的 question、messages、ContextHandle 或语义字段。

后端对未过期 request receipt 使用全局唯一 requestToken 索引，从而允许首次回答连同 ResumeToken 一起丢失后仍找到原 conversation。无 Token 的完成重试会原子重签 Token 并使旧 Token 失效；前端必须以完成回执中的 Token 为准，并防止迟到的旧响应回写本地状态。

## 15. 用户告知

前端必须在首次使用前或输入区附近持续可见地表达以下事实，具体文案和视觉由前端 Agent 设计：

- 系统短期保存任务范围和偏好，用于刷新恢复和连续追问；
- 不保存问题原文、助手答案或证据正文；
- 默认空闲 24 小时过期，最长保留 7 天；
- 关闭页签后不会跨页签、跨浏览器或跨设备自动恢复；
- 用户可以随时“清除本次对话”。

不增加阻断式同意弹窗、Cookie Banner 或强制勾选。不得把该能力称为完整聊天记录、账号记忆、长期记忆或用户画像。

## 16. 前端可能涉及的代码范围

以下只是当前仓库定位，前端 Agent 可按自己的模块设计调整：

- `frontend/src/features/agent/api/answerApi.ts`：Header、联合响应、Context API；
- `frontend/src/features/agent/model/answerTypes.ts`：P3 DTO 和闭集枚举；
- `frontend/src/features/agent/model/mapAnswerResponse.ts`：`responseKind`、引用和执行快照校验；
- `frontend/src/features/agent/model/sessionTypes.ts`：内存 Token、ContextHandle 与恢复摘要；
- `frontend/src/features/agent/composables/useLocalSessions.ts`：一会话一 Token、切换、删除和清空；
- `frontend/src/features/agent/components/AgentWorkspace.vue`：请求快照、恢复、清除和降级状态；
- Conversation/Task summary/Evidence Desk 相关组件：来源引用、执行快照和 Context 继续入口；
- `frontend/e2e/support/publicApiMocks.ts` 及相关 E2E：移除内部 ID，覆盖新联合响应。

本文不要求沿用现有组件结构，也不要求由后端 Agent 修改这些文件。

## 17. 必测场景

### 17.1 请求与 Context

1. 新会话首问不带 ResumeToken；成功响应返回 Token，前端写入当前会话内存和 `sessionStorage`。
2. 后续问答、确认和重规划只通过 Header 发送 Token，body/URL/Cookie 中不存在 Token。
3. 从 Fact、Compare、Recommendation 结果继续时发送对应 Handle 与正确 `expectedContextType`。
4. 从旧推荐结果继续能形成新分支；前端不强制改写为当前 Active 推荐。
5. ContextHandle 与另一个会话 Token 混用时失败，前端不尝试本地修复。

### 17.2 多本地会话

6. 两个本地会话各自得到不同 Token；切换时请求不串会话。
7. `sessionStorage` 始终只保存当前活跃会话 Token。
8. 新建会话清空恢复槽位，不继承上一会话 Token。
9. 删除单个会话会清除其服务端 Context；清空会话逐 Token 清除。

### 17.3 刷新恢复与清除

10. 页面刷新后只恢复安全 Context Summary，不恢复问题或回答气泡。
11. 过期/无效/不匹配 Token 清除本地槽位并开始新会话。
12. DELETE 204 后清除本地 Token、恢复卡和 UI；重复 DELETE 仍成功。
13. DELETE 网络失败时不显示“已从服务端清除”。
14. `sessionStorage` 不可用时仍能完成当前页签问答，并提示不能刷新恢复。

### 17.4 执行与答案

15. 等待同步回答时仅显示本地骨架；收到响应后显示四阶段 FINAL 快照。
16. 最终快照若出现 `IN_PROGRESS/PENDING`，进入契约错误恢复，不伪装实时进度。
17. `PARTIALLY_ANSWERED`、`PRESENTATION_BLOCKED`、证据不足和技术失败显示为不同语义。
18. `PERSISTENCE_UNAVAILABLE` 不改变已成立答案和 Evidence 状态。
19. `sourceReferences` 能打开站内公开来源；页面和请求均不再依赖回答中的内部 ID。
20. 推荐项保持后端顺序和理由—来源绑定。

### 17.5 幂等和异常

21. 超时重试沿用同一 `requestToken` 和完整请求快照。
22. `REQUEST_IN_PROGRESS` 不创建第二个请求 Token。
23. `COMPLETION_RECEIPT` 不生成伪回答，能继续使用返回 Context。
24. `IDEMPOTENCY_KEY_CONFLICT` 停止自动重试并进入受控错误状态。
25. Context Store 暂时不可读的 Refine 保留输入和原 Token 供重试，不从历史答案重建。
26. 日志、埋点、错误上报和测试快照不出现 Token、问题原文、答案正文或来源正文。

## 18. 前端交付验收门禁

前端 Agent 的实现或原型进入 P3-E 前，必须满足：

- 可以仅凭本文闭集契约完成请求、显示、恢复、清除和异常分流；
- 视觉层没有虚构的工具调用、实时百分比或思维链；
- 一个本地会话严格绑定一个服务端 conversation；
- ResumeToken 只存在于运行期内存、当前活跃会话的 `sessionStorage` 和请求 Header；
- 刷新只恢复业务摘要，不恢复聊天历史；
- Context 写失败和 Evidence 不足没有被混为一类；
- `sourceReferences` 完成接入，旧回答 ID 和完整 Context 回传已经删除；
- 多会话、分支、幂等丢响应、过期、清除和 Store 故障均有自动化测试；
- 前后端在同一发布切片通过契约测试和 E2E 后再切断旧字段。

## 19. 前端 Agent 自主决定项

在不改变上述语义的前提下，前端 Agent自主决定：

- 执行快照是折叠、展开、时间线还是紧凑摘要；
- 恢复摘要、不可续接提示和清除入口的具体视觉；
- 来源引用在回答、推荐卡和 Evidence Desk 中的排版；
- Context 分支入口的文案、布局和选中反馈；
- 移动端、键盘、屏幕阅读器、动效和高保真原型方案；
- 用户告知的自然语言润色，但不得改变保存范围、TTL 和清除含义。

如自主设计与本文发生冲突，以后端权威 Spec 和本文契约为准；需要改变语义时应发起契约修订，而不是前端静默兜底。
