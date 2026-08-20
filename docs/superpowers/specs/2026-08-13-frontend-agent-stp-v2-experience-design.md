# P5 前端 Agent stp-v2 体验设计与接入方案
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

> 日期：2026-08-13
> 状态：前端设计稿，待评审；后端契约以 P5 Spec 为权威边界
> 对应后端：`docs/superpowers/specs/2026-08-13-agent-context-and-runtime-modes-design.md`（重点第 9、10、12、13、17、18 节）
> 对应路线图：`docs/13-Agent对话体验与智能编排改造路线图.md` 阶段 5
> 前置：P0—P4 已完成；前端当前契约 `stp-v1`、`POST /api/v2/answers`
> 本文用途：作为 P5 前端契约消费包（Spec §17.3/§20 要求）与前端独立交互/视觉设计的权威依据
> 约束：后端契约作为不可变边界，本文只消费、不改语义；发现的契约疑点见第 3 节

---

## 1. 范围与边界

### 1.1 本文规定

- 前端必须能够安全消费的公共语义（字段、枚举、权威性、兼容迁移）；
- 前端独立的交互与视觉设计（Spec §18 明确委托给前端的部分）；
- 分切片接入实施顺序（对齐 Spec §17.14 Slice 12 + §17.3 Consumer Compatibility Preflight）。

### 1.2 本文不规定（属后端权威）

- 字段如何产生、Task 图如何编译、Material 如何校验、Context 如何加密存储；
- 任何后端执行语义、降级判定、版本策略内部实现。

### 1.3 设计硬约束

1. 后端公共字段语义不可改；前端只解析、投影、渲染。
2. 新增字段一律**可选**，迁移期新旧并存；前端以新字段为权威，旧字段仅兼容回退（Spec §9.10/§17.17）。
3. 未知枚举值一律 **fail-closed** 或安全降级，绝不向访客暴露原始码/异常（Spec §16.11）。
4. 不在 P5 重做前端视觉系统（Spec §19 非目标）：**扩展**现有 dossier 主题，不另起视觉语言。
5. 安全不变量不变：不持久化问题/回答正文；ResumeToken/ContextHandle 不入 URL/Cookie/正文/诊断；不暴露内部主体 ID、堆栈、内部阈值（AGENTS.md Security）。
6. 不使用 Superpowers 流程。

---

## 2. 前端契约消费规范（stp-v1 → stp-v2）

> 现状来自 `frontend/src/features/agent/model/answerTypes.ts`、`api/answerApi.ts`、`portfolio/api/apiErrorActions.ts`。
> 下列类型草图为**前端消费目标形态**，均以可选字段增量加入现有类型，不破坏 stp-v1 解析。

### 2.1 协议与请求

| 维度 | 现状 (stp-v1) | P5 (stp-v2) | 前端落点 |
|---|---|---|---|
| 协议头 | `agentTurnContract:'stp-v1'` | `'stp-v2'` | `answerApi.ts` 默认发 `stp-v2`；保留显式 `stp-v1` 回退（见 3.7） |
| 协议不兼容 | 无 | `HTTP 409 + ApiErrorResponse.code=AGENT_TURN_CONTRACT_UNSUPPORTED` | `apiErrorActions.ts` 新增码与动作（见 2.7） |
| `contextReference.resultItemId` | 无（请求仅 `contextHandle`+`expectedContextType`） | 增加可选 `resultItemId`（Spec §12.12） | 有序结果项「继续」显式选择 |

请求类型扩展草图：

```ts
export type SemanticTurnContract = 'stp-v1' | 'stp-v2'

export interface ContextReferenceRequest {
  contextHandle: string
  expectedContextType: ConversationContextType
  // P5（Spec §12.12）：Context 内的显式结果项选择。缺省=整个 Context。
  resultItemId?: string
}
```

`answerApi.ts` 序列化时仅当 `resultItemId` 存在才写入；`agentTurnContract` 默认 `'stp-v2'`。

### 2.2 顶层响应字段

`AnswerResponse` 增量（均可选）：

| 字段 | 类型 | 语义（Spec） | 前端用途 |
|---|---|---|---|
| `sourceComposition` | `SourceComposition` | 来源组成（§9.5） | 顶部来源徽标；区分多来源/跨域派生 |
| `evidenceState` | 增 `'MIXED'` | 兼容期顶层证据聚合（§9.6） | `MIXED` 时弱化顶层「已验证」语义，改读 Block Support |
| `publicSourceCatalog` | `PublicSourceCatalogEntry[]` | 顶层按 `referenceKey` 去重目录（§9.7） | EvidenceDesk SOURCES 权威来源；Block 按 Key 关联 |
| `degradationSummary` | `PublicDegradationSummary` | 结构化降级摘要（§10.8） | 降级徽标细化（仅聚合可见内容任务） |
| `caveats` | `PublicAnswerCaveat[]` | 结构化限定语（§9.9） | 挂在所涉 Block 下方，绝不省略/反转 |
| `contextInvalidation` | `ContextInvalidation` | Strict Context 失效（§13.9/§13.14），`responseKind=ANSWER` 内 | 独立恢复卡 |
| `contextResolution` | `ContextResolution` | 重验证成功摘要（§13.14） | 续接成功轻提示 |

> `continuationContext` **不是**顶层字段，而是每个可继续完成任务的字段 `completedTasks[].continuationContext`（定稿 §3.4 / 契约交接 §4）；同一响应可有多个可继续任务，前端不得跨任务拼接 Handle 与 Result Item。

```ts
export type SourceComposition =
  | 'GENERAL_ONLY'
  | 'PORTFOLIO_ONLY'
  | 'MULTI_SOURCE'          // General+Portfolio 并列，无成功 Synthesis
  | 'CROSS_DOMAIN_DERIVED'  // 至少一个合法 Synthesis Block

export type AnswerSupportKind =
  | 'VERIFIED_PUBLIC_EVIDENCE' // 仅 P3 Evidence Promotion 的 Portfolio 内容
  | 'GENERAL_KNOWLEDGE'        // 明确不是 Portfolio Evidence
  | 'DERIVED_FROM_TASKS'       // 仅通过 Relation Policy + 跨域 Validator 的 Synthesis

export interface PublicSourceCatalogEntry {
  referenceKey: string
  label: string
  sourceType: PublicSourceType
  subjectRoute: string
  evidenceRoute?: string
  publishedVersion: string
}
// PublicSourceCatalogEntry 与现有 PublicSourceReference 同构；catalog 以 referenceKey 去重。

export interface PublicDegradationSummary {
  degraded: boolean
  kinds: PublicDegradationKind[]
  affectedTaskIds: string[]
}

export type PublicDegradationKind =
  | 'RETRIEVAL_FALLBACK'
  | 'EXPRESSION_FALLBACK'
  | 'CROSS_DOMAIN_EXPRESSION_FALLBACK'
  | 'CONTENT_BACKEND_FALLBACK'

export interface PublicAnswerCaveat {
  code: string
  message: string
  appliesToBlockIds: string[]
  sourceTaskIds: string[]
}
```

### 2.3 Block 级契约（Spec §9.3）

`AnswerBlock` 增量（可选；`sourceDomain` 为权威，`sourceScope` 兼容）：

```ts
export interface AnswerBlock {
  // 现有 stp-v1 字段保留（sourceScope/sectionType/title/content/claimIds/evidenceIds/sourceReferences）
  sourceScope: BlockSourceScope
  // P5：真实来源域，权威。SYNTHESIS 在旧 sourceScope 无对应值（见 3.5）。
  blockId?: string
  sourceDomain?: SemanticSourceDomain // 'GENERAL' | 'PORTFOLIO' | 'SYNTHESIS'
  support?: AnswerBlockSupport
  // ...existing fields
}

export interface AnswerBlockSupport {
  kind: AnswerSupportKind
  statementReferences: StatementSupportReference[]
  sourceTaskIds: string[]
  publicSourceKeys: string[]
  contentVersion?: string
}

// 响应级 Provenance：消费者可用于校验来源链，前端不要求展示或理解完整内部 Material（§9.3）。
export interface StatementSupportReference {
  statementId: string
  sourceTaskId?: string
  publicSourceKeys?: string[]
  [field: string]: unknown
}
```

不变量（前端消费侧）：
- `blockId`/`statementId` 是当前回答内不透明 ID，**不保证跨请求/跨版本稳定**；同一已接受请求的幂等重放须返回相同值（§9.3）。
- General Block 的 `publicSourceKeys` 为空；Portfolio Block 至少一个已验证来源；Synthesis Block 至少一个 General + 一个 Portfolio Statement + 关系，其公开来源只追溯 Portfolio 输入（§9.3）。
- 同一 `referenceKey` 可出现在多个 Block 的 `publicSourceKeys`，**不得因展示去重丢失语义**（§9.7）。

### 2.4 Task 级契约（Spec §9.4 / §10.4 / §10.9 / §10.10）

`AgentTurnDisplayTaskResponse` 与 `AgentTurnCompletedTaskResponse` 增量：

```ts
export type FulfillmentRole = 'PRIMARY' | 'SUPPORTING' | 'OPTIONAL'

export interface TaskSupportSummary {
  kind: AnswerSupportKind
  statementCount: number
  publicSourceCount: number
  sourceTaskCount?: number   // 仅 Synthesis
  contentVersion?: string
}
```

- `fulfillmentRole` 在 `displayPlan.tasks[]` 与 `completedTasks[]` 两处必须来自同一已编译 Semantic Task，且确认后不变（§10.4）。**前端只读，不推断、不展示为可编辑**；单任务场景可不出。
- Block Support 是权威明细，Task Support Summary 是聚合投影（§9.4）。

#### 2.4.1 PublicTaskStatus 闭集重整（Spec §10.9）

前端权威消费集（stp-v2）：

```ts
export type PublicTaskStatus =
  | 'COMPLETED'
  | 'PARTIAL'
  | 'EMPTY'
  | 'NOT_SUPPORTED'
  | 'NOT_APPLICABLE'
  | 'BLOCKED'
  | 'UNAVAILABLE'
  | 'STALE'
  | 'FAILED'
  | 'REJECTED'
  | 'NOT_EXECUTED'
```

定稿映射（契约交接 §1）：`stp-v1` 响应中的旧值按下表归一显示；**`stp-v2` 响应出现旧值/未知值/混版即 fail-closed，不猜测映射**（单响应状态词汇不得混版）。

```ts
// stp-v1 旧值 → stp-v2 显示等价（仅用于渲染 stp-v1 响应）
const LEGACY_TASK_STATUS_MAP: Readonly<Record<string, PublicTaskStatus>> = {
  DEPENDENCY_UNAVAILABLE: 'BLOCKED',
  NOT_EXECUTED_BUDGET: 'NOT_EXECUTED',
  PRESENTATION_BLOCKED: 'BLOCKED',
  CANCELLED: 'NOT_EXECUTED',          // 内部 executionStatus=CANCELLED，表路由延后/未选择，非用户取消整轮
  CAPABILITY_UNAVAILABLE: 'UNAVAILABLE',
}
```

未知值 → fail-closed（`FAILED` 语义的安全通用态 + 诊断事件，不向用户暴露原始码）。

#### 2.4.2 Reason Category（Spec §10.10）

```ts
export type TaskReasonCategory =
  | 'INPUT' | 'CONTENT' | 'POLICY' | 'CAPABILITY'
  | 'DEPENDENCY' | 'BUDGET' | 'INTEGRITY' | 'BOUNDARY'
```

`taskReasonLabels.ts` 由「码→文案」扩展为「码→{文案, 类别}」白名单；类别用于分组与图标，文案仍走闭集白名单（未知码→克制通用句）。

### 2.5 Context 与版本契约（Spec §11/§12/§13）

```ts
// TurnDisposition 增加 CONTEXT_INVALIDATED（Spec §13.9）
export type TurnDisposition =
  | 'READY' | 'PARTIAL_READY'
  | 'CONFIRMATION_REQUIRED' | 'CLARIFICATION_REQUIRED'
  | 'BOUNDARY' | 'REJECTED'
  | 'CONTEXT_INVALIDATED'

export interface ContextInvalidation {
  reasonCode: ContextInvalidationReasonCode
  recoveryAction: ContextInvalidationRecoveryAction
  contextType: ConversationContextType
  currentContentVersion: string
}

export interface ContextResolution {
  mode: 'REVALIDATED_TO_CURRENT'
  contextType: ConversationContextType
  currentContentVersion: string
}

export interface ContinuationContext {
  contextHandle: string
  contextType: ConversationContextType
  sourceTaskId: string
}
// 定稿：continuationContext 是 completedTasks[] 的字段，非顶层（契约交接 §4）。

// 定稿闭集（契约交接 §6）
export type ContextInvalidationRecoveryAction =
  | 'RESTART_FROM_CURRENT_CONTENT'
  | 'RESELECT_RESULTS'
  | 'REASK_WITHOUT_CONTEXT'

// reasonCode → 默认 recoveryAction（前端据此选文案，未知 action 走非破坏性通用出口 + 脱敏诊断）
const RECOVERY_ACTION_BY_REASON: Readonly<Record<string, ContextInvalidationRecoveryAction>> = {
  CONTEXT_RESULT_STALE: 'RESTART_FROM_CURRENT_CONTENT',
  REFERENCED_PUBLIC_SOURCE_CHANGED: 'RESTART_FROM_CURRENT_CONTENT',
  REFERENCED_SUBJECT_UNAVAILABLE: 'RESELECT_RESULTS',
  CONTEXT_REFERENCE_INVALID: 'REASK_WITHOUT_CONTEXT',
  CONTEXT_REFERENCE_EXPIRED: 'REASK_WITHOUT_CONTEXT',
}
// CONTEXT_RESOLUTION_UNAVAILABLE 属能力不可用，不配恢复动作。

// 有序结果项（Spec §12.12）—— 定稿落在 completedTasks[].resultPayload.recommendations[]
export interface OrderedResultItem {
  resultItemId: string
  position: number
  subject: SemanticSubjectReference
}
```

Context 安全 reasonCode 白名单（Spec §11.12/§12.9/§12.10/§13.8）：

```ts
export type ContextReasonCode =
  | 'CONTEXT_REFERENCE_INVALID'
  | 'CONTEXT_REFERENCE_EXPIRED'
  | 'CONTEXT_RESULT_STALE'
  | 'REFERENCED_SUBJECT_UNAVAILABLE'
  | 'REFERENCED_PUBLIC_SOURCE_CHANGED'
  | 'CONTEXT_RESOLUTION_UNAVAILABLE'
  | 'ROUTING_CONTEXT_CONFLICT'
  | 'CONTINUATION_GOAL_UNRESOLVED'
  | 'CONTEXT_SUBJECT_REQUIRED'
  | 'RESULT_POSITION_OUT_OF_RANGE'
  | 'RESULT_CONTEXT_AMBIGUITY'
```

> 上述闭集为「前端已知白名单」；后端可能产出其他安全码，前端对未知码一律 fail-closed 文案，不暴露原始码。

路由优先级（前端权威）：

```text
responseKind 判别
  -> COMPLETION_RECEIPT -> 回执卡
  -> ANSWER:
       agentTurn.disposition 优先于 answerResolution 决定卡片分支：
         CONTEXT_INVALIDATED     -> 恢复卡（不全屏覆盖局部 STALE）
         CONFIRMATION_REQUIRED   -> 计划确认
         CLARIFICATION_REQUIRED  -> 澄清
         READY/PARTIAL_READY     -> 正文渲染
         BOUNDARY/REJECTED       -> 边界卡
       局部 Task STALE 仅在任务摘要内标记（Spec §13.9）
```

### 2.6 幂等、并发与 409（Spec §17.2）

现有 `requestToken`（UUID）幂等键、`requestVersion` 丢弃过期响应、`REQUEST_IN_PROGRESS`/`IDEMPOTENCY_KEY_CONFLICT` 处理保持不变。新增：

```ts
// apiErrorActions.ts
export type ApiErrorCode =
  | ... // 现有码
  | 'AGENT_TURN_CONTRACT_UNSUPPORTED'

// actionForApiError: AGENT_TURN_CONTRACT_UNSUPPORTED -> 新动作 UPGRADE_REQUIRED
//   （不自动静默回退；提供用户主动「以基础模式继续」入口，见 3.7）
```

### 2.7 兼容迁移权威性规则（前端实现准则）

1. `sourceDomain` 存在时为权威；否则回落 `sourceScope`（§16.11 一致性仅对 GENERAL/PORTFOLIO 成立，见 3.5）。
2. `publicSourceCatalog` 存在时，Block 来源按 `support.publicSourceKeys` 关联目录项；否则回落旧 `block.sourceReferences`。
3. `degradationSummary.degraded` 存在时为权威；否则回落顶层 `degraded:boolean`。
4. `completedTasks[].continuationContext` 存在时为该任务「继续」权威；仅在 `stp-v1` 回落同一任务的 `contextHandle`。
5. `fulfillmentRole`/`supportSummary` 缺省时不影响渲染，仅在「回答构成」层缺省隐藏。
6. 所有枚举：未知 → fail-closed（安全默认 + 诊断事件），不暴露原始值。

---

## 3. 契约疑点定稿

> 2026-08-13 已由 `../../handoffs/2026-08-13-agent-p5-frontend-public-contract.md`
> 定稿；以下为前端执行摘要，冲突时以该公共契约和主 Spec 为准。

1. `PRESENTATION_BLOCKED → BLOCKED`，`executionStatus=CANCELLED → NOT_EXECUTED`；`stp-v2` 单响应不得混用旧状态。
2. 有序项落在 `completedTasks[].resultPayload.recommendations[]`；不落在 Continuation/Recent Context 投影。
3. `CONTEXT_INVALIDATED` disposition 优先于 `NEEDS_CLARIFICATION`；`contextInvalidation` 是与 `agentTurn` 同级的顶层字段。
4. `completedTasks[].continuationContext` 是 `stp-v2` 权威；旧 `contextHandle` 仅同任务、`stp-v1` 兼容。
5. `sourceDomain` 权威；SYNTHESIS 的旧 `sourceScope` 省略或为 `null`，禁止映射 GENERAL。
6. recoveryAction 闭集为 `RESTART_FROM_CURRENT_CONTENT | RESELECT_RESULTS | REASK_WITHOUT_CONTEXT`；contextResolution.mode 仅 `REVALIDATED_TO_CURRENT`。
7. `409 + AGENT_TURN_CONTRACT_UNSUPPORTED` 只允许用户主动“以基础模式继续”，禁止静默降级。

---

## 4. 交互与视觉设计

> 变更项均先出 HTML 高保真原型评审，再进 Vue 实现（交付序列见 §7）。

### 4.1 设计原则

1. **来源诚实优先**：访客必须能区分「通用知识 / 已验证作品集证据 / 跨域推导」，不得让 General 冒充 Portfolio 证据（Spec 原则 #4、§9.2）。
2. **分层透明**：正文 + 来源默认可见；任务图/角色/支持计数/降级明细按需展开，信息密度可控。
3. **覆盖度 ≠ 不可信**：部分回答已发布事实仍充分可信，仅覆盖不完整（§9.8）。
4. **降级不恐慌**：降级是合法安全路径，提示克制（§10.7/§10.8）。
5. **失效可恢复**：Context 失效给出明确恢复动作，不静默替换目标（§13.10）。
6. **扩展不重做**：沿用 dossier 暖米/牛血红主题，新增 token，不另起视觉系统（Spec §19）。

### 4.2 透明度决策：分层透明（默认简洁 + 可展开「回答构成」信任层）

依据：后端逐 Block 暴露 `sourceDomain`+`support.kind`，诚实性原则要求**逐 Block 内联可见**来源域与支持类型（低成本、高诚实）；而任务图/履约角色/支持计数/降级明细对普通访客信息密度过高（Spec §18 把「是否显示内部任务摘要」留前端定），故放入**按需展开**层。

- **默认层（始终可见）**：正文、每 Block 来源域标记、每 Block 支持类型徽标、每 Block 来源引用、部分完成/降级/Caveat/失效等状态横幅。
- **信任层（按需展开「回答构成 ▾」）**：`sourceComposition`、任务清单（displayIndex/goalLabel/fulfillmentRole/supportSummary/status）、降级摘要 kinds、Caveat 汇总。

### 4.3 视觉语言扩展（tokens.css 新增，沿用现有描边/字体）

**已定稿为鲜明版 B**（2026-08-13，对照原型 `prototypes/p5-stp-v2/compare-source-domain.html`）：

```css
:root {
  /* 来源域色码（鲜明版 B）*/
  --agent-source-general:   #5b6470;            /* 冷灰墨 */
  --agent-source-portfolio: var(--red);          /* 牛血红 */
  --agent-source-synthesis: #54507e;            /* 靛蓝紫 */

  /* 饱和底色：12–16% 着色，让三类来源一眼可分 */
  --agent-source-general-bg:   color-mix(in srgb, var(--agent-source-general) 12%, var(--paper-hi));
  --agent-source-portfolio-bg: color-mix(in srgb, var(--agent-source-portfolio) 12%, var(--paper-hi));
  --agent-source-synthesis-bg: color-mix(in srgb, var(--agent-source-synthesis) 16%, var(--paper-hi));
}
```

处理语汇（鲜明版 B）：
- Block：饱和域色底 + 同色描边 + **满高 5px 域色左条**。
- 来源标记：**实底药丸**（域色底 + paper 字 + paper 色点）。
- 支持徽标：**实底**（`VERIFIED_PUBLIC_EVIDENCE`=牛血红、`GENERAL_KNOWLEDGE`=冷灰墨、`DERIVED_FROM_TASKS`=靛蓝紫）。
- SYNTHESIS 卡：**最强靛蓝底 + 头部色带**，跨域推导一眼可辨；公开来源只追溯 Portfolio 输入。
- 字体不变：标题 `--serif`、标记/眉签 `--mono`、正文 sans；动效沿用 `--agent-motion-fast/state` + `prefers-reduced-motion`。

### 4.4 逐表面设计

| 表面 | 设计要点 | Spec |
|---|---|---|
| 多来源/跨域 Block | 鲜明版 B：饱和域色底 + 满高左条 + 实底来源药丸/支持徽标；SYNTHESIS 最强靛蓝卡 + 头部色带，明确「由 通用+作品集 推导」，其来源只追溯 Portfolio 输入 | §9.2/§9.3 |
| 「回答构成」信任层 | 顶部克制入口「回答构成 ▾」，展开：sourceComposition 徽标 + 任务清单（角色 PRIMARY/SUPPORTING/OPTIONAL 用描边强弱区分）+ supportSummary 计数 + 降级 kinds + Caveat。默认折叠，单任务可隐藏 | §9.4/§10.4 |
| 公共来源目录 | EvidenceDesk SOURCES 改读 `publicSourceCatalog`（去重）；Block 内来源改为「按 publicSourceKeys 关联目录项」的紧凑引用，保留每 Block 完整支持关系 | §9.7 |
| 部分完成 | `PARTIALLY_ANSWERED` 温和横幅「已回答部分内容，X 主题暂无可发布结果」；已发布事实仍带 ✓已验证 | §9.8 |
| 降级 | `degraded`+kinds 克制提示「已切换到基础回答方式（检索回退/表达回退/…）」，仅聚合可见内容任务 | §10.8 |
| Caveat | 结构化限定语挂所涉 Block 下方，绝不省略/反转 | §9.9 |
| Context Invalidation | `disposition=CONTEXT_INVALIDATED` → 独立恢复卡（沿用 `PlanInvalidatedNotice.vue` 范式）：白名单原因 + recoveryAction（如「基于最新内容重新开始」）；局部 Task `STALE` 仅任务摘要内标记 | §13.9/§13.10 |
| contextResolution | 重验证成功 → 一次性轻提示（如「已基于最新内容重新核对」），不阻断 | §13.14 |
| 有序结果「继续」 | P5 第一版在 Recommendation 每项暴露 resultItemId+position，提供「继续了解这一项」affordance，发起携带 `contextReference.resultItemId`；Compare 未定义公共 Item 投影前不自行构造 ID | §12.12 |
| 409 升级 | `AGENT_TURN_CONTRACT_UNSUPPORTED` → 升级/刷新提示 + 可选「以基础模式继续」（用户主动显式 stp-v1 重试） | §17.2 |

### 4.5 文案白名单原则（扩展现有 `answerLabels.ts`/`taskReasonLabels.ts`）

- 所有面向访客文案走**闭集白名单**；未知码 → 克制通用句，永不暴露原始码/异常。
- 来源域标签：`GENERAL→通用知识`、`PORTFOLIO→作品集资料`、`SYNTHESIS→跨域综合`。
- 支持类型：`VERIFIED_PUBLIC_EVIDENCE→✓已验证证据`、`GENERAL_KNOWLEDGE→通用知识`、`DERIVED_FROM_TASKS→由通用+作品集推导`。
- 履约角色（仅在信任层）：`PRIMARY→主`、`SUPPORTING→辅`、`OPTIONAL→可选`。
- 降级 kinds：`RETRIEVAL_FALLBACK→检索回退`、`EXPRESSION_FALLBACK→表达回退`、`CROSS_DOMAIN_EXPRESSION_FALLBACK→跨域表达回退`、`CONTENT_BACKEND_FALLBACK→内容后端回退`。
- Context 失效/澄清 reasonCode 各配克制中文句（如 `CONTEXT_RESULT_STALE→该上下文已与最新内容不兼容`、`RESULT_POSITION_OUT_OF_RANGE→引用的结果序号超出范围`、`RESULT_CONTEXT_AMBIGUOUS→存在多个可指代结果，请明确所指`）。

---

## 5. 前端状态模型落点

| 文件 | 变更 |
|---|---|
| `model/answerTypes.ts` | 新增 §2 全部枚举与类型（可选字段增量）；`AnswerEvidenceState`+`MIXED`；`TurnDisposition`+`CONTEXT_INVALIDATED`；`PublicTaskStatus` 重整+旧值映射；`ContextReferenceRequest`+`resultItemId` |
| `model/mapAnswerResponse.ts` | 解析/校验新顶层字段；publicSourceCatalog 解析与 Block Key 关联；caveats 归属；CONTEXT_INVALIDATION 分支 |
| `model/semanticTurnView.ts` | sourceDomain 权威投影；fulfillmentRole/supportSummary 视图模型；未知枚举 fail-closed 工具 `safeEnum`；STALE/新 status 投影 |
| `model/answerLabels.ts` | 来源域/支持类型/角色/降级/sourceComposition 文案 |
| `model/taskReasonLabels.ts` | 码→{文案,类别} 白名单 + 新 Context reasonCode 文案 |
| `api/answerApi.ts` | 默认 `stp-v2`；`contextReference.resultItemId` 序列化 |
| `portfolio/api/apiErrorActions.ts` | 增 `AGENT_TURN_CONTRACT_UNSUPPORTED`(409) 码+`UPGRADE_REQUIRED` 动作 |
| `components/ConversationThread.vue` | Block 按域/support 渲染；部分完成/降级/Caveat/恢复卡/409 |
| `components/EvidenceDesk.vue` + `model/evidenceDeskModel.ts` | SOURCES 读 publicSourceCatalog |
| `components/CompactTaskSummary.vue` / `ExecutionSnapshot.vue` | 角色/supportSummary/STALE |
| `components/SourceReferenceList.vue` | 按 publicSourceKeys 关联目录项 |
| 新增 `components/AnswerCompositionPanel.vue` | 信任层 |
| 新增 `components/ContextInvalidatedNotice.vue` | 恢复卡 |

---

## 6. 接入实施切片（对齐 Spec §17.14 Slice 12 + §17.3 Preflight）

> 每切片：Vitest mapper/单元测试 + Vue Test Utils 组件测试 + `npm run build` 通过。
> 视觉/交互变更切片（FE-2/3/4/5）须先过 HTML 原型评审。

- **FE-0 Preflight 安全前置**（§17.3 Consumer Compatibility Preflight，不依赖后端新语义）：新枚举可选接入；未知枚举 fail-closed（`safeEnum`）；409+`AGENT_TURN_CONTRACT_UNSUPPORTED` 处理；`CONTEXT_INVALIDATED` 解析不崩溃；默认发 `stp-v2`。门禁：旧响应仍正确、新字段缺省无影响、未知值安全降级。
- **FE-1 契约类型与映射层**：完整类型化+映射 §9/§10 全部字段（sourceDomain/support/supportSummary/sourceComposition/publicSourceCatalog/fulfillmentRole/Task Status 重整/reason category/degradation/caveat/contextInvalidation/contextResolution/continuationContext）。暂不改渲染（仍读旧字段），数据已解析校验。门禁：mapper 单测全绿，含新旧字段并存与未知枚举用例。
- **FE-2 多来源/跨域 Block 视觉**（先 HTML 原型）：按 sourceDomain+support 区分渲染，SYNTHESIS 新卡片。
- **FE-3 回答构成信任层 + 公共来源目录**（先 HTML 原型）：opt-in 面板；SOURCES 读 catalog；provenance。
- **FE-4 部分完成/降级/Caveat 表面**（先 HTML 原型）。
- **FE-5 Context Invalidation + 有序结果续接**（先 HTML 原型）：恢复卡、contextResolution 轻提示、continuationContext、resultItemId「继续」。
- **FE-6 清理旧字段依赖**（Slice 12 step 11）：新字段存在时停止读旧 sourceScope/inline sourceReferences/scattered handle；保留兼容回退分支。

切片依赖：`FE-0 → FE-1 → (FE-2 ‖ FE-3 ‖ FE-4 ‖ FE-5) → FE-6`。FE-2/3/4/5 在 FE-1 映射层就绪后可并行推进，但视觉项须先原型评审。

---

## 7. 交付序列与验收

### 7.1 交付序列（用户确认）

1. **设计文档**（本文）。
2. **HTML 高保真原型 demo**（视觉/交互变更项，待评审）：2 个文件——(a) 多来源+跨域综合回答主秀场（含回答构成/来源目录/继续按钮）；(b) 边缘态秀场（部分完成/降级/Caveat/Context Invalidation/409）。沿用仓库 `tokens.css` 主题。
3. **Vue 完整实现**（原型评审通过后，按 §6 切片）。

### 7.2 验收命令

```powershell
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
```

Playwright 端到端按需补充。

### 7.3 安全不变量

- 不持久化问题/回答正文；ResumeToken/ContextHandle 不入 URL/Cookie/正文/诊断。
- 未知枚举 fail-closed；不暴露内部主体 ID/堆栈/内部阈值。
- CONTEXT_INVALIDATED 不静默回退到页面主体或另一 Context（§11.12/§13.10）。
- 诊断事件走固定白名单 schema，结构上无法携带 Token/PII。

### 7.4 文档维护

完成后更新 `docs/11-项目演进日志.md`；能力/默认变更同步 `docs/08-当前实现状态.md`。

---

## 8. 开放项

- 第 3 节原契约疑点 1—7 已全部定稿（依据 `docs/handoffs/2026-08-13-agent-p5-frontend-public-contract.md`），不阻塞任何切片。
- 来源域视觉方向**已定稿为鲜明版 B**（饱和底色 + 满高左条 + 实底药丸/徽标 + 最强 SYNTHESIS 卡），对照原型 `prototypes/p5-stp-v2/compare-source-domain.html`；§4.3/§4.4 已回填，进入 FE-2 实现。
