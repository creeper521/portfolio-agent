# Agent 架构收敛前端交接

- **日期：** 2026-08-18
- **状态：** Slice 0 共享合同已冻结；Frontend 消费测试与 Slice 5 原子切换待 Frontend Agent 实施
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
