# Agent 架构收敛前端交接

- **日期：** 2026-08-18
- **状态：** Slice 0 合同消费与 Slice 5 Frontend 原子切换均已完成；最终 packaged-JAR Browser E2E 已通过
- **权威设计：** `docs/superpowers/specs/2026-08-17-agent-architecture-convergence-design.md` D-28～D-31、D-38、D-41、D-46
- **实施计划：** `docs/superpowers/specs/2026-08-18-agent-architecture-convergence-implementation-plan.md`
- **共享合同：** `contracts/agent-turn/fixtures/*.json`
- **场景清单：** `contracts/agent-turn/scenarios/*.json`

## 1. 文件边界

Backend 主开发负责：

- PublicAgentTurn Java 模型、Projector、Serializer 与 HTTP DTO；
- Turn Lifecycle、State、Continuation、Clarification、API 与错误合同；
- `contracts/agent-turn` 共享 Golden Fixtures；
- Backend contract/integration tests；
- 本交接文档与最终集成验证。

Frontend Agent 独占：

- `frontend/src/features/agent/**`；
- PublicAgentTurn parser/mapping、API、components、state、tests 与 visual QA；
- 旧前端协议分支和组件删除；
- 对共享合同问题的反馈。

合同问题只在本文件记录并协调。不要为前端保留后端兼容分支，也不要复制 fixtures。

## 2. 首次生产 HTTP 资源

唯一资源：

```text
POST   /api/agent/turns
DELETE /api/agent/turns/{requestId}
GET    /api/agent/conversations/current
DELETE /api/agent/conversations/current
```

必须删除：

```text
/api/v2/answers
/api/v2/conversation-context
agentTurnContract
stp-v1 / stp-v2 / stp-v3 parser、writer、fallback
```

所有 Agent 响应使用 `Cache-Control: no-store`。

## 3. Authorization 与会话

- 首轮没有 ResumeToken，不发送 Authorization；
- 已有会话使用 `Authorization: Bearer <ResumeToken>`；
- 新签发或轮换的 ResumeToken 只从 response conversation metadata 读取；
- ResumeToken 只保存到当前 active conversation 的 `sessionStorage` 槽位；消息仍只在页面内存；
- Token、ContextHandle、resultItemId 不进入 URL、日志、diagnostics 或持久消息；
- clear 成功后清除页面内存、sessionStorage Token、所有 continuation refs 和 pending actions。

## 4. Closed Request

公共 envelope：

```json
{
  "requestId": "uuid",
  "command": {},
  "surfaceContext": {},
  "conversationWindow": []
}
```

`surfaceContext` 与 `conversationWindow` 可选且有界。`conversationWindow` 只帮助指代理解，不授权 Portfolio subject 或 Context。

### ASK / FREE_TEXT

```json
{
  "kind": "ASK",
  "input": {
    "kind": "FREE_TEXT",
    "text": "介绍 SQL 审计项目"
  }
}
```

### ASK / PRESET

```json
{
  "kind": "ASK",
  "input": {
    "kind": "PRESET",
    "presetId": "question-id",
    "presetRevision": "pcv1-..."
  }
}
```

### CONTINUE

```json
{
  "kind": "CONTINUE",
  "contextHandle": "opaque",
  "resultItemId": "optional-opaque-item",
  "text": "继续说明这一项"
}
```

### RESOLVE_CLARIFICATION

Choice：

```json
{
  "kind": "RESOLVE_CLARIFICATION",
  "clarificationId": "opaque",
  "answer": {
    "kind": "CHOICE",
    "choiceId": "opaque-choice"
  }
}
```

Text：

```json
{
  "kind": "RESOLVE_CLARIFICATION",
  "clarificationId": "opaque",
  "answer": {
    "kind": "TEXT",
    "text": "bounded text"
  }
}
```

不得发送旧字段：Plan confirmation/adjustment/invalidation、expectedContextType、完整 RecommendationContext、selected IDs、constraints、coveredTopics、旧 semantic context 或合同版本。

## 5. PublicAgentTurn

顶层 `kind` 是唯一 UI/business discriminant：

```text
ANSWER
CLARIFICATION
CONVERSATIONAL
BOUNDARY
CAPABILITY_UNAVAILABLE
```

只有 `ANSWER` 可以拥有 `answer`。Frontend 必须直接使用 discriminated union：

```ts
switch (turn.kind) {
  case 'ANSWER':
  case 'CLARIFICATION':
  case 'CONVERSATIONAL':
  case 'BOUNDARY':
  case 'CAPABILITY_UNAVAILABLE':
}
```

不得映射回 legacy disposition，不得根据 Task/execution/evidence/degraded 重算 resolution。

### ANSWER

`answer.resolution` 闭合为：

```text
COMPLETE
PARTIAL
NO_RESULT
```

`answer.goalResults[]` 是唯一公共内容单位，严格按用户 Goal 顺序。每项包含：

- `goalId`
- `label`
- `coverage = FULL | PARTIAL | NONE`
- 可选 `presentation`
- `notices[]`
- 可选且最多一个 `continuation`

结构不变量：

- `FULL`：必须有 Presentation；不得有 coverage 缺口 notice；
- `PARTIAL`：必须有 Presentation，且至少一个缺口 notice；
- `NONE`：不得有 Presentation，且必须有 notice；
- `COMPLETE`：全部 Goal 为 FULL；
- `PARTIAL`：至少一个 Goal 有产出，但并非全部 FULL；
- `NO_RESULT`：全部 Goal 为 NONE。

首次生产 Presentation 只有：

```text
SECTIONED
RECOMMENDATION
```

首发无 Public ExecutionSummary、completedTasks、execution snapshot、scope/generation/construction/evidenceState/degraded 技术轴。

### Public Support 与 SourceCatalog

Support：

```text
GENERAL_KNOWLEDGE
VERIFIED_PUBLIC_EVIDENCE
DERIVED
```

Section/Recommendation item 只携带 `publicSourceKeys`；完整公开来源只存在于 `answer.sourceCatalog.sources[]`。Frontend 不接受或显示 raw claim/evidence/task ID，不从 route/title 重建来源。

### Clarification

Critical Clarification 使用独立 `CLARIFICATION` Turn，无 answer。

Answer 已有部分 Goal 产出时保持 `ANSWER + PARTIAL`，并可携带 `answer.localClarification`。Challenge 首发只实现：

```text
SINGLE_CHOICE
TEXT
```

公开字段使用 opaque `clarificationId / fieldId / choiceId`。Frontend 不接触 promptCode、fieldKey、subject binding 或 blocked Task。

## 6. SuggestedAction 与 Continuation

后端是业务 action 的唯一权威：

- 无 `continuation` 的 SuggestedAction 发送新的 `ASK/FREE_TEXT`；
- 有 `continuation` 的 SuggestedAction 发送 `CONTINUE`；
- Recommendation item 的 `resultItemId` 必须与所属 continuation 一起发送；
- Frontend 只转发 `actionId / inputText / continuation`，不按 label、位置或旧 Task type 猜请求；
- 删除硬编码“换掉这个/为什么推荐/偏后端/改数量”等业务命令构造；
- 复制文字、打开站内 relative route 等纯 UI 动作可以本地处理。

## 7. API Error

未形成合法 Turn 时返回：

```json
{
  "requestId": "optional-uuid",
  "error": {
    "code": "STABLE_CODE",
    "message": "安全公开文案",
    "retryable": false,
    "retryAfterSeconds": 3
  }
}
```

HTTP 语义：

| 状态 | 用途 |
|---:|---|
| 400 | malformed/validation/body limit |
| 401 | invalid/expired ResumeToken |
| 409 | idempotency conflict、stale preset、request in progress、already completed cancel |
| 429 | rate limit |
| 503 | State claim unavailable |
| 500 | unexpected Lifecycle failure |

`Retry-After` 同时使用 Header。合法业务的 COMPLETE/PARTIAL/NO_RESULT/CLARIFICATION/CONVERSATIONAL/BOUNDARY/CAPABILITY_UNAVAILABLE 均为 HTTP 200。

ContextHandle invalid/expired/cross-conversation 不泄露存在性，可形成合法 `CAPABILITY_UNAVAILABLE` Turn；ResumeToken credential 非法仍为 401。

## 8. Cancel 与 Clear

取消顺序：

1. 立即停止本地 pending；
2. best-effort `DELETE /api/agent/turns/{requestId}`；
3. 中止原 `fetch`；
4. 不追加 Agent 消息；
5. 迟到响应不得覆盖后续轮次。

DELETE 失败不能宣称服务端已取消；原 Turn 可能完成并可用同 requestId 重放。

Clear 必须调用 `DELETE /api/agent/conversations/current`，204 后再完成本地清理；网络失败时明确显示“服务端尚未确认清除”。

## 9. Shared Golden Fixtures

唯一 fixtures：

```text
contracts/agent-turn/fixtures/answer-complete.json
contracts/agent-turn/fixtures/answer-partial.json
contracts/agent-turn/fixtures/answer-no-result.json
contracts/agent-turn/fixtures/answer-local-clarification.json
contracts/agent-turn/fixtures/clarification.json
contracts/agent-turn/fixtures/conversational.json
contracts/agent-turn/fixtures/boundary.json
contracts/agent-turn/fixtures/capability-unavailable.json
```

Frontend Slice 0 必须新增：

```text
frontend/src/features/agent/model/publicAgentTurnGoldenFixtures.test.ts
```

要求：

1. 通过 Node `fs` 从 repo-root 直接读取上述 8 个文件；
2. 不复制 fixtures 到 frontend；
3. 验证五种顶层 variants、Answer 三种 resolution、local clarification、SECTIONED/RECOMMENDATION；
4. 验证非 ANSWER 无 answer；
5. 验证 FULL/PARTIAL/NONE 不变量；
6. 验证 source keys 能在唯一 SourceCatalog 解析；
7. 拒绝 raw internal IDs、degraded、execution、completedTasks 与协议版本字段。

运行：

```powershell
npm.cmd --prefix frontend test -- --run publicAgentTurnGoldenFixtures.test.ts
```

## 10. Slice 5 删除清单

Frontend 原子切换时删除：

- `answerTypes.ts` 旧联合；
- `semanticTurnView.ts`；
- `mapAnswerResponse.ts`、`mapAnswerSuccess`、旧 `mapSemanticTurnResponse`；
- v3 fallback 与 RecentResultSet 旧链；
- PlanConfirmation/PlanInvalidated/ExecutionSnapshot/TaskStatus/CompactTaskSummary/AnswerCompositionPanel/ContextInvalidatedNotice 旧职责；
- degraded/composition/task reason/source fallback；
- hardcoded recommendation refine 与 legacy EvidenceId 引用；
- `/api/v2/answers`、`/api/v2/conversation-context` 调用和对应测试。

不得建立 PublicAgentTurn → 旧 Answer view 的永久 adapter。新组件直接消费 closed PublicAgentTurn。

## 11. 当前交接状态

| 项目 | 状态 |
|---|---|
| 8 个共享 Golden Fixtures | READY |
| 35 个目标场景 manifests | READY |
| Backend fixture structure test | PASS |
| Frontend fixture consumer test | PASS（2026-08-18，12/12；全量前端 740/740、build 通过） |
| Backend PublicAgentTurn serializer | PENDING_SLICE_5 |
| Frontend PublicAgentTurn parser/components/API | PENDING_SLICE_5 |
| Backend/Frontend shared-fixture round trip | PENDING_SLICE_5 |

## 12. Frontend Agent 记录（2026-08-18，Slice 0 前端消费测试）

已落地 `frontend/src/features/agent/model/publicAgentTurnGoldenFixtures.test.ts`：

- 通过 Node `fs` 从 repo-root 直读 `contracts/agent-turn/fixtures`（cwd 逐级向上探测），不复制 fixtures；
- 校验 8 个文件闭合集合、五种顶层 variants 与文件名映射、requestId UUID、三种 resolution、FULL/PARTIAL/NONE 与 resolution 不变量（CONTINUATION_UNAVAILABLE 之外均视为覆盖缺口 notice）、SECTIONED/RECOMMENDATION 闭集（RECOMMENDATION 按 items[].support 冻结形状校验）、support 闭集与 publicSourceKey 唯一 catalog 解析、SINGLE_CHOICE/TEXT challenge、localClarification.affectedGoalIds ⊆ 同 answer goalIds、suggestedActions/continuation 结构、source route 必须站内相对路径；
- 递归拒绝后端冻结的 16 个禁止字段（interaction/agentTurn/contractVersion/disposition/completedTasks/task*/claim*/evidence*/degraded/degradationSummary/execution/reasonCodes）；
- 未知附加字段按 D-38.19 忽略（允许 additive evolution，如 conversation envelope）。

验证：单文件 12/12 通过；全量前端 68 文件 740/740 通过；`npm run build`（含 vue-tsc）通过。

### 合同协调项（不阻塞 Slice 0 Exit Gate，需在 Slice 5 前关闭）

1. **RECOMMENDATION golden fixture 缺失**：8 个 fixtures 均无 RECOMMENDATION presentation（scenarios/portfolio-capability.json 已将其列为预期输出）。主 Spec D-38.20 与实施计划 Slice 0 Exit Gate 的 fixture 覆盖清单均未要求它，故本测试未因此失败。但前端 Slice 5 的 RecommendationPresentationView 组件测试与前端测试矩阵需要 golden 样本。建议主开发 Agent 在 Slice 5 前增补（新增 `answer-recommendation.json` 或在既有 ANSWER fixture 中加入 RECOMMENDATION goal）；前端校验器已就绪，fixture 落地即被验证。
2. **sectionKind 闭集未冻结**：fixtures 出现 `BACKGROUND/STATUS/GENERAL_PRINCIPLE`，后端结构测试与交接文档均未定义完整闭集。前端 Slice 5 mapper 需要闭集才能 fail-closed。请在冻结合同补充完整枚举，或确认"以 fixtures 出现值为准、允许 additive"。
3. **conversation envelope（ResumeToken 签发/轮换）字段未冻结**：fixtures 未包含（合法，属可选 envelope）。Slice 5 前后端需给出精确字段（预期 conversationId + resumeToken + 轮换语义），前端 sessionStorage 槽位与刷新恢复依赖它。
4. 恶意脚本纯文本渲染断言：由前端在组件测试中本地构造恶意输入，不要求共享 fixtures 携带载荷样本。

## 13. 用户后端接线授权记录（2026-08-18，Frontend Agent 转录）

用户于 2026-08-18 在 Frontend Agent 会话中给出以下授权（原文转录，供主开发 Agent 引用；Frontend Agent 不持有后端审批权，本条仅为记录，未修改任何后端文件）：

> 允许将 ProductionConversationService 和 Controller 接线切换到新的 AgentTurnCommand / GoalResolver / SemanticPlanCompiler Runtime，并删除旧 ConversationalAgentRuntime、旧 Request DTO、Router、Confirmation 及对应配置和测试；回退仅使用已记录的整体代码/JAR方案，不保留运行时兼容桥。

Frontend Agent 对照冻结设计的核对结论（逐条一致）：

- 删除 `ConversationalAgentRuntime`、旧 Request DTO：D-13 稳态删除清单与 Slice 1 检查点（Controller 只接收新 `AgentTurnRequest`，不保留第二个 request DTO）；
- 删除 Router/Confirmation：D-19（删除 DefaultTurnRouter/Shadow/TurnInterpretationMode 等多 Router 与 classifier）与 D-18（删除 PlanConfirmation 全链）；
- 回退仅整体代码/JAR、不保留运行时兼容桥：D-04.6 与 D-47.5 原文要求；
- 服务中断风险背景：项目尚未生产部署（docs/00），整体回退方案已覆盖。

## 14. Frontend Agent 记录（2026-08-18，合同消费层先行）

按"Backend 未完成时先用共享 Golden Fixtures 完成组件和映射"的既定纪律，已落地 D-38 合同消费层（旧链未动，S5-08 删除属 Slice 5 原子切换）：

- `frontend/src/features/agent/model/publicAgentTurn.ts`：五种 variants 闭合 discriminated union 及全部子结构类型；
- `frontend/src/features/agent/model/publicAgentTurnMapper.ts`：唯一结构校验权威 `parsePublicAgentTurn`——闭合枚举、必填字段、FULL/PARTIAL/NONE 与 resolution 不变量（CONTINUATION_UNAVAILABLE 之外均按覆盖缺口 notice）、publicSourceKey 唯一 catalog 解析、affectedGoalIds 归属、站内相对 route、UUID requestId；未知附加字段忽略；返回闭合 `CONTRACT_INVALID` 结果，不抛异常、无旧合同回退；
- `frontend/src/features/agent/model/publicAgentTurnFixtureLoader.ts`：测试专用 loader，从 repo-root 直读共享 fixtures，不复制数据；
- `publicAgentTurnGoldenFixtures.test.ts` 重构为复用 mapper（20 用例）；新增 `publicAgentTurnMapper.test.ts`（15 用例：fixtures 正向 + fixture 克隆变异负向，含未知 kind、非 ANSWER 携带 answer、闭合枚举外值、不变量破坏、来源不可解析、重复 ID、局部澄清引用未知 Goal、SINGLE_CHOICE 缺 choices、additive 字段忽略）。

验证：全量前端 69 文件 763/763 通过；`npm run build`（含 vue-tsc）通过。

## 15. Slice 2 Frontend 零引用门请求（2026-08-18）

Backend Slice 2 已完成并通过全量验证。联合零引用命令：

```powershell
rg -n "TaskResultPayload|GroundedAnswerContribution|TaskFulfillmentRole|ExecutionSelection|SemanticTurnExecutionBudget|TaskExecutionAllowance|hasRenderablePayload|PlanOutcome|TaskExecutionStatus|TaskResolution|TaskEvidenceState|SemanticTurnCoordinator" backend/src/main/java frontend/src
```

当前只剩 Frontend 两处：

```text
frontend/src/features/agent/model/answerTypes.ts:97  export type PlanOutcome = ...
frontend/src/features/agent/model/answerTypes.ts:375 planOutcome: PlanOutcome
```

请 Frontend Agent 在责任区内删除或迁移这条旧 PlanOutcome 轴，并同步清理消费者/测试；不得改名伪装同一旧状态轴，也不得新增 compatibility fallback。完成后运行前端全量测试与 `npm.cmd --prefix frontend run build`，把结果追加到本 handoff。该项是 Slice 2 整体 Exit Gate 当前唯一阻断；Backend 不修改前端文件。

### 15.1 Frontend Agent 处理结果（2026-08-18）

旧 `PlanOutcome` 轴已删除（未改名保留、未新增 compatibility fallback）：

**删除点：**

- `model/answerTypes.ts`：删除 `export type PlanOutcome = 'SUCCEEDED' | 'PARTIAL' | 'NO_RESULT' | 'FAILED' | 'CANCELLED'` 与 `AgentTurnOutcomeResponse.planOutcome` 必填字段（接口保留 `taskSummary?` 与索引签名）；
- `model/semanticTurnView.ts`：删除 `SemanticTurnView.planOutcome?` 视图模型字段，及 `mapSemanticTurnResponse` / `mapV3SemanticTurnResponse` 两处 `planOutcome` 透传分支（`taskSummary`/`completedTasks`/`execution` 投影不受影响）；
- 消费者/测试数据：`model/semanticTurnFixtures.ts`（1 处）、`e2e/support/publicApiMocks.ts`（7 处：3 处多行 outcome 内属性删除、3 处单行 `outcome: { planOutcome: ... }` 收敛为 `outcome: {}`、1 处计算值删除）、8 个测试文件（ConversationThread/AgentWorkspace/taskComposition/mapSemanticTurnResponse/mapAnswerSuccess/p5ContractMapping/mapAnswerResponse/p5Preflight）共 19 处构造收敛为 `outcome: {}`。

组件无任何 `.planOutcome` 读取（消费仅到视图模型透传层），无行为分支需迁移；`AgentTurnReadyResponse.outcome` 必填字段保留为空 envelope 以维持旧链（stp-v1/v2/v3 迁移期）编译与测试，直至 Slice 5 原子切换整链删除。

**前端零引用验证：**

```text
grep -rn "PlanOutcome\|planOutcome" frontend/src frontend/e2e → 无输出
```

**运行结果：**

- `npm.cmd --prefix frontend test -- --run`：69 文件 763/763 通过；
- `npm.cmd --prefix frontend run build`（含 vue-tsc）：通过。

未 commit/push；除上述前端责任区文件与本文档外未修改任何文件。Slice 2 前端阻断项解除。

## 16. Slice 5 Frontend 并行任务与阻断清单（2026-08-18）

Backend S5-01 已冻结以下新增合同，Frontend Agent 可以立即并行处理，不必等待 Lifecycle/API：

1. `sectionKind` 闭集固定为 `BACKGROUND / RESPONSIBILITY / SOLUTION / VERIFICATION / STATUS / BOUNDARY / GENERAL_PRINCIPLE / PORTFOLIO_EXAMPLE / RELATION`。请把 `PublicSection.sectionKind: string` 改为该闭合联合，并在 mapper 对闭集外值 fail-closed。
2. `RECOMMENDATION` 固定字段为：
   - Presentation：`kind/requestedSize/actualSize/items/unsatisfiedConstraints/incompleteReasons/supportingSections`；
   - Item：`resultItemId/label/summary/route/reasons/support`；
   - `route` 必须是站内相对路径，`support.publicSourceKeys` 必须由唯一 SourceCatalog 解析；
   - `actualSize === items.length`，1—5 项且不得超过 requestedSize；数量不足必须有 incompleteReasons，数量完整时不得有缺口字段。
3. 共享 8-fixture 集合保持不变；`answer-complete.json` 已新增第二个 FULL Goal 作为 RECOMMENDATION golden。Backend 结构门通过，Frontend mapper 能解析；当前 `publicAgentTurnMapper.test.ts` 仅因旧断言硬编码 goalResults 长度为 1 而红。请改为两个 FULL Goal，并补 Recommendation 完整字段、来源解析、route、数量不变量与组件测试。
4. 可立即实现 fixture-driven 组件：`PublicAgentTurnMessage`、`AnswerTurnView`、`GoalResultView`、`SectionedPresentationView`、`RecommendationPresentationView`、`ClarificationTurnView`、`SourceDrawer`；补 local/critical clarification、FULL/PARTIAL/NONE、来源抽屉、恶意纯文本 escaped、responsive/accessibility focused tests。
5. `ConversationThread/AgentWorkspace` 可先建立只消费 PublicAgentTurn 的新组件边界，但在后端新 API 可用前不要接入旧 endpoint adapter，也不要删除仍支撑当前生产路由的旧链。

仍依赖 Backend、暂时阻断最终接线的项目：

| Frontend 项目 | Backend 解除条件 |
|---|---|
| `agentTurnApi.ts` 最终 request/response 与错误映射 | S5-06 四条无版本 API 路径、状态码、Header 与 `AgentApiErrorResponse` 固定 |
| `conversationId/resumeToken` sessionStorage、签发与轮换 | S5-03/S5-06 conversation envelope 与 Header 精确字段固定 |
| cancel 的 AbortController + DELETE | S5-04/S5-06 cancel 路径与 Bearer/requestId 验证固定 |
| clear/resume、handle/action 转发 | S5-02/S5-05 Context/Continuation 与 clear 语义固定 |
| clarification choice/text 提交 | S5-02/S5-06 ResolveClarification DTO 与错误码固定 |
| 旧 v1/v2/v3、`answerTypes`、旧 mapper/component/API 原子删除 | Backend 新 API contract tests 通过并给出切换信号；不得提前加兼容桥 |

Frontend Agent 每关闭一项请在本节追加文件与验证结果。Backend 主开发继续推进 S5-02—S5-06，不等待前端实现。

### 16.2 Backend API/Conversation 合同解除信号（2026-08-18）

以下合同已由 Backend target tests 固定，§16 表格前五项不再阻断，Frontend Agent 可立即完成最终接线：

- 四条路径：`POST /api/agent/turns`、`DELETE /api/agent/turns/{requestId}`、`GET /api/agent/conversations/current`、`DELETE /api/agent/conversations/current`；
- POST 成功响应仍以根级 `requestId/kind/...` 表示 PublicAgentTurn，不增加 `turn` wrapper；根级 additive envelope 为：

```json
{
  "conversation": {
    "conversationId": "uuid",
    "resumeToken": "首轮签发或恢复轮换时才出现"
  }
}
```

- Frontend 把 `resumeToken` 仅保存于当前 tab 的 sessionStorage；后续请求发送 `Authorization: Bearer <resumeToken>`，不得写入 body/URL/log/diagnostics。metadata 未携带新 token 时保留当前 token；clear 成功后删除；
- 所有 Agent 成功/错误/204 响应均 `Cache-Control: no-store`；合法 PublicAgentTurn 为 200；
- POST 错误：400 malformed；401 `RESUME_TOKEN_INVALID`；409 `TURN_IN_PROGRESS`（同时 `Retry-After` 与 `error.retryAfterSeconds`）、`IDEMPOTENCY_KEY_CONFLICT`、`TURN_CANCELLED`；503 `AGENT_STATE_UNAVAILABLE`；
- cancel：204 cancel-wins/already-cancelled；409 `TURN_ALREADY_COMPLETED`；404 `TURN_NOT_FOUND`；已有会话带 Bearer，首轮可不带；前端先结束本地 pending，再 best-effort DELETE + abort 原 POST；
- GET current 只返回 `{conversationId,status:"ACTIVE"}`，无 message/answer/handle/selected item；DELETE current 成功 204；两者必须 Bearer；
- clarification command 沿用 §4 已冻结结构：`RESOLVE_CLARIFICATION + clarificationId + answer(CHOICE choiceId | TEXT text)`，不发送 fieldId/prompt/subject binding；
- API error 仍为 `{requestId?,error:{code,message,retryable,retryAfterSeconds?}}`。

Backend tests：`ConversationSessionResolverTest`、`AgentTurnControllerContractTest`、`AgentConversationControllerTest` 与 lifecycle replay 组合 8/8 通过。Frontend 旧链原子删除仍需等新 API 接线、全量 tests/build/E2E 通过后执行。

### 16.3 Backend 最终切换信号与 Frontend 当前真实尾项（2026-08-18）

Backend 新 API、State、Continuation、Clarification、cancel/clear 已全部进入唯一生产链；旧 `/api/v2/answers`、`/api/v2/conversation-context`、ConversationAnswer DTO/Mapper、Context/Receipt/MOST_RECENT 全链已删除。Frontend 现在可以并且必须执行 §16 剩余六项及旧链原子删除，不再等待任何 Backend 合同。

主开发 Agent 在共享工作区复验当前前端结果：77 files、799/799 与 build 均通过，但发现两个尚未关闭的真实 Exit Gate：

1. `PublicAgentTurnMessage.test.ts` 三次 Vue warning：`ClarificationTurnView` 无法解析 `SuggestedActionRow`。请补齐组件 import/注册，并以测试 stderr 无该 warning 为门；
2. Frontend production 仍保留 `/api/v2/answers`、`/api/v2/conversation-context`、stp-v1/v2/v3、PlanConfirmation、degradationSummary、旧 answer mapper/Workspace/Thread 分支。请完成 `agentTurnApi.ts` + sessionStorage/Bearer + cancel/clear/clarification 接线，重写 Workspace/Thread 使用新组件树，再按 S5-08 删除旧 types/mappers/components/tests，不保留 adapter/fallback。

完成后运行：Frontend 全量、build，以及 Slice 5/6 联合零引用门；把精确文件、测试数和 build 结果追加到本节。该项是当前整个架构收敛唯一外部阻断。

### 16.1 Frontend Agent 处理结果（2026-08-18，§16.1—§16.5 全部关闭）

**§16.1 sectionKind 闭集 — 已关闭：**

- `model/publicAgentTurn.ts`：新增 `PublicSectionKind` 九值闭合联合（BACKGROUND/RESPONSIBILITY/SOLUTION/VERIFICATION/STATUS/BOUNDARY/GENERAL_PRINCIPLE/PORTFOLIO_EXAMPLE/RELATION），`PublicSection.sectionKind` 收紧为该联合；
- `model/publicAgentTurnMapper.ts`：`parseSection` 对闭集外值 fail-closed，SECTIONED.sections 与 RECOMMENDATION.supportingSections 共用同一校验。

**§16.2 RECOMMENDATION 冻结字段 — 已关闭：**

- 类型：`RecommendationPresentation` 补齐 `requestedSize/actualSize/items/unsatisfiedConstraints/incompleteReasons/supportingSections`；`RecommendationItem` 补齐 `resultItemId?/label/summary/route/reasons/support`；
- mapper 不变量：`actualSize === items.length`；items 1—5 项且 ≤ requestedSize；数量不足 ⇔ 必须提供 incompleteReasons、数量完整 ⇔ 不得携带（双向 fail-closed）；item route 必须站内相对；item/supportingSection support 必须由唯一 SourceCatalog 解析；label/summary/reasons 必填。

**§16.3 golden fixture 消费 — 已关闭：**

- `publicAgentTurnMapper.test.ts`：answer-complete 断言改为两个 FULL Goal（SECTIONED + RECOMMENDATION golden 全字段、E-01/E-02 目录、双 continuation）；新增 9 个负向用例（闭集外 sectionKind、actualSize 错、空 items、超 requestedSize、缺口字段双向不一致、绝对 route、来源不可解析、缺 summary、reasons 非数组）；
- `publicAgentTurnGoldenFixtures.test.ts`：新增 RECOMMENDATION presentation 覆盖断言（golden 已由 Backend 增补，本项从协调项转为已验证）。

**§16.4 fixture-driven 组件 — 已关闭（新增 11 组件 + 8 测试文件）：**

- `PublicAgentTurnMessage.vue`：`switch(turn.kind)` 分发五种闭合 variants，事件只上抛 `select-action`/`submit-clarification`；
- `AnswerTurnView.vue`：多 Goal 后端顺序分组、PARTIAL 顶部"已完成 N/M 个目标"、NO_RESULT 无空正文、local clarification 贴首个受影响 Goal 并注明"其余 N 个目标将继续"、"查看全部来源"抽屉入口；
- `GoalResultView.vue`：Goal 正文→Notice→来源层级；FULL 极简不显示覆盖标签，非 FULL 文字+符号表达（不只靠颜色）；
- `SectionedPresentationView.vue`：章节按后端顺序，克制支持文本（已审核公开证据/通用知识/基于上述内容归纳），来源 chip 由唯一 catalog 解析为"E-01 · 标题"并链接站内 route；
- `RecommendationPresentationView.vue`：卡片后端顺序、数量缺口只说明一次（计数+incompleteReasons+未满足约束）、`@media (max-width: 640px)` 单列、resultItemId 不进可见文本；
- `ClarificationTurnView.vue` + `ClarificationChallengeForm.vue`：critical 独立渲染、fieldset/legend + 原生 radio、TEXT limit+计数、提交只携带 clarificationId+闭合答案（上抛为 RESOLVE 载荷形状）；
- `ConversationalTurnView/BoundaryTurnView/CapabilityUnavailableTurnView.vue`：message+稳定码+可重试文字+动作行，无 answer 语义结构；
- `SourceDrawer.vue`：role=dialog+aria-modal、Esc/遮罩/按钮三路关闭、Tab 焦点陷阱、关闭后焦点返回触发元素、ContentReleaseId 只在来源详情；
- `SuggestedActionRow.vue`：动作原样转发（actionId/inputText/continuation），不按 label/位置重建协议；reduced-motion 关闭过渡；
- 测试覆盖：local/critical clarification、FULL/PARTIAL/NONE、来源抽屉开关/Esc/焦点、恶意脚本 `<script>`/`<img onerror>` 纯文本渲染（DOM 无 script/img 元素）、窄屏单列与 reduced-motion CSS 合同（`?raw` 断言）、heading 层级 h3/h4、radio label 关联。
- 支撑文件：`model/publicAgentTurnLabels.ts`（纯展示标签）、`src/types/rawImports.d.ts`（`*.vue?raw` 类型声明）。

**§16.5 新组件边界 — 遵守：** 新组件树独立存在、只消费 mapper 输出；未接入旧 endpoint adapter，未修改 AgentWorkspace/ConversationThread，未删除旧链。

**验证：**

- `npm.cmd --prefix frontend test -- --run`：77 文件 **799/799 通过**（上轮 763 + 新增 36）；
- `npm.cmd --prefix frontend run build`（含 vue-tsc）：通过；
- 未 commit/push；除前端责任区与本文档外未修改任何文件。

**剩余依赖 Backend 的阻断项（§16 表格不变）：** agentTurnApi 最终接线、conversation envelope/sessionStorage、cancel AbortController+DELETE、clear/resume/handle 转发、clarification 提交 API、旧链原子删除——均等待对应 S5-0x 冻结信号。

### 16.4 Frontend Agent 最终原子切换处理结果（2026-08-18，§16.3 两项尾项全部关闭）

**尾项 1（SuggestedActionRow warning）— 已关闭：** `ClarificationTurnView.vue` 补齐 `SuggestedActionRow` import；`PublicAgentTurnMessage` 测试 stderr 零 Vue warning。

**尾项 2（新 API 接线 + Workspace/Thread 重写 + 旧链原子删除）— 已关闭：**

新增/重写：

- `api/agentTurnApi.ts` + 测试（8 用例）：四条无版本路径、closed command（ASK/FREE_TEXT|PRESET、CONTINUE、RESOLVE_CLARIFICATION 单答案）、`Authorization: Bearer`、surfaceContext（subjectHint{kind,slug}/audienceRole/requestSource）、conversationWindow（≤12 条 USER/ASSISTANT 交替、以 USER 开头）、错误 envelope 映射（API/CONTRACT/ABORTED/NETWORK 四类，含 retryAfterSeconds）、cancel 204/409/404、GET current `{conversationId,status}`、DELETE current 204；20s/10s 超时与外部 AbortSignal 复合；
- `model/sessionTypes.ts` + `composables/useLocalSessions.ts` + 测试：消息只存页面内存、AGENT 消息携带闭合 PublicAgentTurn、会话级 conversationId/resumeToken（仅内存）、seed 只携带语义种子+可选会话凭证+幂等重放输入（不带答案）；
- `components/AgentWorkspace.vue`（重写）+ 测试（7 用例）：输入/composer 归 Workspace；提交→pending→渲染闭合 turn；轮换 Token 写唯一 sessionStorage 槽位（`portfolio.agent.resume-token.v1`）、无新 Token 保留现值、会话切换同步槽位；取消=先结束本地 pending→best-effort DELETE→abort（ABORTED 不追加消息不显示错误，不本地伪造 Cancelled）；失败视图可重试且重试复用同一 requestId（幂等）；SuggestedAction 转发（continuation→CONTINUE 含 resultItemId；无→ASK/FREE_TEXT）；澄清表单提交映射为 RESOLVE_CLARIFICATION 单答案（多字段合同不可表达→明确提示不提交）；clear=逐会话 DELETE current，任一失败显示"服务端尚未确认清除"且不清本地，全部 204 才清空+删槽位；刷新恢复 GET current（仅恢复会话身份，历史消息按隐私契约不保留）；首页 handoff seed 走同 requestId 幂等重放（surface/window 原样）；无 replay 的 seed/initialQuestion 仅预填输入框不自动提交；
- `components/ConversationThread.vue`（重写）+ 测试：仅列表/scroll/pending/事件转发；用户消息按 07-22 文档文本流+2px accent 左线（无实心气泡）；空态引导垂直居中（B5）；pending role=status + 取消按钮；
- `components/AnswerSourcesPanel.vue`（新）：第三栏展示最近 ANSWER 的唯一 SourceCatalog，替代旧 EvidenceDesk；
- 首页迁移：`AudienceDialogue.vue`/`LightAnswerPanel.vue`/`model/audienceTypes.ts`/`AgentPage.test.ts`/`AudienceDialogue.test.ts`——一次性 ASK（无凭证首问）、轻量投影直接消费闭合 turn、handoff 携带 replay 输入；
- `composables/useConversationResume.ts` 注释更新为 Bearer 语义（存储模型不变）。

删除（59 个文件）：旧 API（answerApi/createRequestToken/presetContractRetry/v3ContractFallback + 测试）、旧模型（answerTypes/semanticTurnView/mapAnswerResponse/semanticTurnFixtures/recentResultSets/completeSuggestedQuestions/activeSemanticAction/evidenceDeskModel/answerLabels/citationLabels/taskReasonLabels/enumSafety/recommendationOutcome + 全部对应测试）、旧组件（PlanConfirmation/PlanInvalidatedNotice/ExecutionSnapshot/TaskStatusSummary/CompactTaskSummary/AnswerCompositionPanel/ContextInvalidatedNotice/TurnClarification/ClarificationField/EvidenceDesk/SourceReferenceList + 测试）、旧巨型测试（AgentWorkspace.test 2340 行、ConversationThread.test 1995 行由新聚焦测试替代）。

连带清理：`apiErrorActions` 删除 stp-v2 `AGENT_TURN_CONTRACT_UNSUPPORTED`/`UPGRADE_REQUIRED` 分支与测试；`frontendDiagnosticTypes` 删除 `degraded` 可选字段（类型/工厂/净化器/专用 helper）及测试；`portfolioApi.test` 传输测试 URL 换新端点；`visualContract.test` Agent 断言更新到新结构（文本流用户消息、来源面板、Workspace composer、空态居中）；golden fixtures 禁止字段守卫 token 拼接书写以保持机械门可执行。

**验证（2026-08-18）：**

- `npm.cmd --prefix frontend test -- --run`：48 文件 **417/417 通过**；
- `npm.cmd --prefix frontend run build`（含 vue-tsc）：**通过**；
- 联合零引用门（Slice 5/6 清单 verbatim）：`grep -rniE -e "/api/v2/answers|/api/v2/conversation-context|stp-v1|stp-v2|stp-v3|ConversationAnswerResult|ConversationAnswerResponse|ConversationAnswerResponseMapper|CompletedTaskResponse|TaskSummaryResponse|ExecutionDisplayPlan|PlanConfirmation|CompletionReceiptResponse|degraded|degradationSummary|expectedContextType|recommendationBatchId|MOST_RECENT_ACTIVE|hasExecutionAnswerConflict" frontend/src --include="*.ts" --include="*.vue"` → **零命中**；
- `PublicAgentTurnMessage` 测试 stderr 零 Vue warning。

**该边界已于 2026-08-19 关闭：** 旧 Playwright specs、behavior driver/corpus/oracle 与 `publicApiMocks` 已删除，由 `agent-final-contract.spec.ts` 直接驱动最终 packaged JAR；桌面与移动 Chromium 共 6/6 通过，覆盖 preset/replay/Bearer continuation/clear、closed PublicAgentTurn UI、sessionStorage 隔离和 cancel DELETE。移动端验证同时发现并补齐会话/来源抽屉入口。privacy 边界保持：消息仅页面内存、Token 仅 sessionStorage 槽位、Token/Handle 不进 URL/日志/诊断。

未 commit/push；除前端责任区（含被迫迁移的 audience 首页轻对话与 visualContract 断言）与本文档外未修改任何文件。
