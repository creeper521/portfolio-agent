# Typed Project Discussion Context 设计
<!-- DOCUMENT_STATUS: APPROVED -->

> **日期：** 2026-08-20
> **状态：** 已由用户批准，作为 Free-text Semantic Routing 与 Project Discussion Context 的 LEVEL_3 实施依据
> **适用仓库：** `D:\code\agent`
> **范围：** Free-text Semantic Routing 收敛；推荐结果进入项目讨论、讨论内自由文本、项目切换、刷新恢复、显式退出和过期重建

## 1. 文档目的

当前 Agent 能完成项目推荐，也能调用真实 Provider 回答明确的通用问题，但无法可靠理解承接上一轮推荐结果的省略表达。真实诊断证明：Provider 在线并成功返回结构化 JSON，失败发生在本地 Goal 校验阶段。模型把承接式输入解释为 `GENERAL_EXPLANATION` 的 `SUBJECT` 澄清，而 General Goal 依赖当前输入 raw anchor，按隐私规则不能持久化为 `BlockedGoalTemplate`，因此最终投影为 `GOAL_INTERPRETATION_UNAVAILABLE`。

这不是 Provider 掉线，也不应通过放宽隐私校验或解析 Assistant 文本修补。当前缺口是：Recommendation 已经产生 typed Context 和 result item，但前端没有进入入口，后端 `CONTINUE` 也没有形成真正的项目讨论权威。

本文冻结目标产品语义、唯一状态权威、闭合命令、模型权限、迁移删除范围和 Exit Gates。实施计划在本文经用户审核批准后另行编写。

## 2. 已确认的产品决定

1. 用户通过推荐卡“与我讨论”显式进入项目讨论上下文；系统不暗中猜测活跃项目。
2. 讨论持续到用户显式退出、新建会话或 Context 到期。
3. 未点击卡片但输入承接式省略表达时，只允许从最近一轮 Recommendation 的实际结果中选择。
4. 进入讨论后上下文优先；通用概念应结合当前项目解释，不自动退出上下文。
5. 点击“与我讨论”后立即返回该项目的简要全景介绍。
6. 点击另一张历史推荐卡时直接切换项目，不要求二次确认。
7. 页面刷新恢复 typed 项目焦点与剩余 TTL，不恢复聊天消息。
8. Context 过期不静默重试；显示明确过期状态和“重新进入”动作。
9. 自由文本语义由 AI 提出 closed route 和候选引用；后端只验证闭合集合与状态转换，不维护自然语言短语表。
10. AI route 与候选在当前 typed scope 中唯一合法时立即执行；零命中、多命中或模型主动返回不确定时才澄清。
11. 单一 Recommendation result 仍要求 AI 明确判断进入讨论；判断成立后直接进入，不展示单选项 CHOICE。
12. EXPIRED 同时允许“重新进入项目”和“开始新话题”；两者可由按钮触发，也可由 AI 对自由文本提出 closed route。

## 3. 当前实现事实

### 3.1 已存在的能力

- `ContinuationContext.Recommendation` 已保存推荐授权范围、约束和 `selectedResults`；
- 每个推荐项已有 `resultItemId`；
- Goal Result 已投影 `contextHandle`；
- `CONTINUE` 已携带 `contextHandle`、可选 `resultItemId` 和文本；
- 当前 ContextMutationPlanner 还为普通 Fact、Comparison 和 Recommendation 都创建 Context；
- Goal 级 continuation 会进入 AnswerGoalResult，但前端没有生产消费者；
- Context 受 Conversation、ContentRelease、30 分钟 absolute TTL 和 PostgreSQL 加密状态约束；
- 前端 mapper 已解析 Goal continuation 与 Recommendation item result ID；
- SuggestedAction 已支持原样转发 continuation。

### 3.2 当前断点

- Recommendation 卡片只展示项目与证据，不触发 continuation；
- Goal 级 `contextHandle` 和 Item 级 `resultItemId` 没有在 UI 中形成完整动作；
- Recommendation Context 的现有 `CONTINUE` 固定生成 `PORTFOLIO_REFINE_RECOMMENDATION`；
- 当前 follow-up 文本只用作 InputAnchor，不决定 Goal、Facet 或输出；
- Conversation Session 没有 active discussion pointer；
- `GET /api/agent/conversations/current` 只返回 conversationId/status；
- 普通 `ASK` 不携带 typed recent recommendation reference，只能把文本历史交给模型；
- General Clarification 不能安全持久化 raw anchor，严格拒绝是正确的隐私行为。
- `MinimalGoalFallback` 仍通过推荐/项目关键词、数量正则、约束短语、比较词和指代短语理解自由文本；这些 NLP 规则不能覆盖开放表达。

## 4. 候选方案与结论

### 4.1 只修 Prompt

让模型把省略表达输出为 Portfolio 主体澄清。改动小，但仍需解析自然语言历史，不能证明候选来自上一轮实际推荐，也不能覆盖切换、刷新、退出和过期。拒绝。

### 4.2 只接推荐卡

前端组合现有 handle/item ID 并发送旧 `CONTINUE`。能修复点击入口，但旧 `CONTINUE` 不理解后续文本，仍不具备项目讨论语义。拒绝作为最终方案。

### 4.3 Typed Project Discussion Context

深化现有 Continuation，建立一个 Project Discussion 权威。所有主体绑定来自已验证 Context 或公开主体，模型只在锁定项目内解释 Goal 参数。该方案完整覆盖已确认体验，作为本文目标架构。

### 4.4 Free-text Semantic Routing 收敛

本设计同期收敛与 Discussion 共用的 Free-text Goal seam：

- AI 负责提出 closed semantic route、Goal 参数和候选引用；
- 后端负责验证 allowed route、公开主体、候选集合、数字范围、约束闭集和状态转换；
- 删除 `MinimalGoalFallback` 中推荐、数量、约束、比较和指代的自然语言解析；
- 保留极小的安全社交 fast path、已发布 PRESET 和 typed page subject；
- Provider 不可用时诚实返回能力不可用，不用不完整正则伪装理解；
- Clarification CHOICE/binding 的确定性归一化保留；自由 TEXT 的 AI normalization 涉及一次消费失败语义，不纳入本次。

## 5. 目标权威

```text
Recommendation SemanticResult
→ RecommendationContext(selectedResults)
→ backend-owned item discussion action
→ CONTINUE ENTER_RESULT
→ ProjectDiscussionContext(locked public project)
→ Conversation activeDiscussion pointer
→ CONTINUE ROUTE_IN_CONTEXT
→ AI closed SemanticRouteProposal
→ backend route/state validator
→ GoalResolver contextual interpretation or state transition
→ existing SemanticPlanCompiler / SemanticTurnEngine
→ PublicAgentTurnProjector
```

权威边界：

| 概念 | 唯一权威 |
|---|---|
| 推荐结果及可选项 | `ContinuationContext.Recommendation` |
| 当前讨论项目 | `ProjectDiscussionContext` + Conversation active pointer |
| 讨论内 Goal | `GoalResolver` contextual branch |
| Task/Plan | `SemanticPlanCompiler` |
| 执行结果 | `SemanticTurnEngine` |
| 项目卡动作与恢复动作 | Backend Projection |
| 当前焦点展示 | Frontend 纯消费状态 |

不创建 RecentResultSet、第二会话状态机、Assistant 文本解析器或前端业务路由器。

### 5.1 非讨论 ContinuationContext 处置

旧 CONTINUE 被替换时，同 Slice 完成以下删除，不保留悬空合同：

- 删除 `ContinuationContext.PortfolioFact` 与 `PortfolioComparison`；
- ContextMutationPlanner 不再为普通 Fact/Comparison 结果创建 Context；
- 删除 `AnswerGoalResult.continuation` Goal 级公开字段；
- 删除 PublicAgentTurnProjector、contracts fixtures、frontend mapper/model 中对应 Goal continuation 消费；
- RecommendationContext 只通过 Recommendation Item 的 backend-owned discussionAction 暴露入口；
- 讨论内 Portfolio Goal 不创建普通结果 Context，稳定的 ProjectDiscussionContext 是唯一讨论焦点；
- 删除旧 Fact/Comparison `continuationProposal` 分支；
- 若迁移后 `PORTFOLIO_REFINE_RECOMMENDATION` 无生产消费者，同 Slice 删除 GoalKind、参数、Task 和执行分支。

RecommendationContext 只负责证明“这些 result items 确实由该次推荐产生”；ProjectDiscussionContext 只负责当前锁定项目。二者职责不重叠。

## 6. Command 合同

顶层 Command 继续保持 `ASK | CONTINUE | RESOLVE_CLARIFICATION`，但 `CONTINUE` 替换为必填 closed operation。项目尚未生产部署，不保留旧 shape 兼容分支。

### 6.1 进入推荐项

```json
{
  "kind": "CONTINUE",
  "operation": "ENTER_RESULT",
  "contextHandle": "opaque",
  "resultItemId": "opaque"
}
```

- handle 必须属于当前 Conversation、当前 ContentRelease 且未过期；
- resultItemId 必须是该 RecommendationContext 的 selected result；
- 不接受自由文本，不让前端指定 subject ID；
- 成功后创建 ProjectDiscussionContext，原子设置 active discussion，并执行默认概览 Goal。

### 6.2 讨论内提问

```json
{
  "kind": "CONTINUE",
  "operation": "ROUTE_IN_CONTEXT",
  "contextHandle": "opaque",
  "text": "bounded current input"
}
```

- handle 必须等于当前 Conversation active/expired discussion pointer；
- text 只在当前 Turn 参与解释，不写入 Context、Session、Request Receipt 或 Replay；
- AI 根据 typed state 提出 CONTINUE、START_NEW_TOPIC、SWITCH 或 REENTER 等 closed route；
- subject 由 ProjectDiscussionContext 或已验证 Recommendation candidates 约束，Provider 不得扩大主体范围；
- ACTIVE 状态可形成 locked-project Goal 或退出/切换；EXPIRED 状态只允许 REENTER、START_NEW_TOPIC 或 NEEDS_CLARIFICATION。

### 6.3 显式退出

```json
{
  "kind": "CONTINUE",
  "operation": "EXIT_CONTEXT",
  "contextHandle": "opaque"
}
```

- 原子清除 active pointer；
- EXIT_CONTEXT 接受 active 或 expired pointer，效果均为只清除 pointer，不调用模型；
- 返回 `CONVERSATIONAL` 安全确认；
- 旧 Context 可留到 TTL cleanup，但不再可作为 active discussion 使用。

### 6.4 重新进入公开项目

```json
{
  "kind": "CONTINUE",
  "operation": "REENTER_SUBJECT",
  "subject": {"kind": "PROJECT", "reference": "public-project-id"}
}
```

- 只用于后端投影的过期恢复动作；
- reference 必须在当前 ContentRelease 中仍公开；
- 创建新 Context，不复活旧 handle，不复用旧 requestId；
- 当前会话已过期时不能重建，必须形成新会话。

显式按钮 operation 不调用模型；自由文本统一走 ROUTE_IN_CONTEXT 的 AI route proposal。AI 无权提出 clear conversation 或删除历史消息。

### 6.5 服务端上下文优先防御

已认证 Conversation 存在 active 或 expired discussion pointer 时，服务端收到 `ASK + FREE_TEXT` 必须忽略前端 command kind 的路由暗示，使用当前 pointer 按 DISCUSSION interpretationMode 解释；不得按 STANDARD ASK 绕过上下文，也不返回“命令类型错误”。没有 discussion pointer 时才使用 STANDARD mode。

该规则只覆盖自由文本。PRESET、RESOLVE_CLARIFICATION 和 backend-owned continuation action 按各自闭合语义执行。由 ASK 进入的 Discussion Goal settlement 同样校验 pointer generation，切换/退出先发生时旧结果不得提交。

## 7. 推荐卡与后端动作权威

`RecommendationItem` 增加完整的 backend-owned `discussionAction`，类型复用 `SuggestedAction`：

```json
{
  "resultItemId": "opaque",
  "label": "public label",
  "discussionAction": {
    "actionId": "opaque",
    "label": "与我讨论",
    "continuation": {
      "operation": "ENTER_RESULT",
      "contextHandle": "opaque",
      "resultItemId": "opaque"
    }
  }
}
```

前端只渲染并转发 action，不自行组合 Goal handle、Item ID、项目 ID 或命令文本。没有 backend action 时不显示入口。

点击后立即执行默认项目概览：

- GoalKind：`PORTFOLIO_FACT`；
- locked subject：选中项目；
- 默认 Facets：`OVERVIEW`、`RESPONSIBILITY`、`SOLUTION`、`VERIFICATION`、`STATUS`；
- 输出使用现有 Portfolio Capability 和公开证据，不让模型编造项目事实。

ProjectDiscussionContext、active pointer 与该次 PublicAgentTurn 必须同一次 settlement 成功后才可见。进入或切换执行失败时不留下半激活 Context；切换失败时原 active discussion 保持不变。

## 8. 承接式自由文本选择

普通 `ASK` 增加可选、opaque 的 `referenceContextHandle`。前端仅在当前会话最后一条可见 Recommendation 的所有可操作 Item 都携带同一个 backend-owned contextHandle 时附带；它只从 discussionAction 机械提取，不组合 Goal 字段，不写 sessionStorage，不跨会话，不从 Assistant 文本重建。

`referenceContextHandle` 是不可信的解释提示，不是 Command 授权。handle 过期、不存在、属于其他 Conversation 或 ContentRelease 不匹配时，服务端静默忽略该提示并按普通 ASK 解释；不返回 Context 错误，不泄露 handle 状态，也不生成基于失效候选的 CHOICE。

Goal Interpretation 增加内部 closed route proposal：

```json
{
  "kind": "DISCUSSION_ROUTE",
  "route": "ENTER_RECOMMENDED_RESULT | STANDARD_GOAL | NEEDS_CLARIFICATION",
  "candidateKey": "C1 | C2 | ... | null"
}
```

接受条件：

- ASK 携带有效 Recommendation reference context；
- Prompt 只给模型临时 candidateKey、公开 label/aliases 和 closed allowed routes；
- 模型可以提出 route 与 candidateKey，不能输出 Context handle、resultItemId 或候选集外项目；
- 后端把 candidateKey 映射回 RecommendationContext resultItem，并验证成员关系；
- route 与候选唯一且合法时立即进入；
- selectedResults 只有一项且 AI 明确提出 ENTER 时直接进入，不展示单项 CHOICE；
- candidateKey 缺失、零命中、多命中或 route=NEEDS_CLARIFICATION 时，服务端生成单字段 CHOICE，选项严格等于实际 selectedResults；
- route=STANDARD_GOAL 时完全忽略 Recommendation hint，继续普通 Goal 解释。

ClarificationStore 的恢复模板扩展为 sealed 类型：

- `BlockedGoalTemplate`：现有 Goal 参数恢复；
- `DiscussionSelectionTemplate`：只保存 recommendation context handle 和允许的 resultItem IDs。

Choice 解析后执行与 `ENTER_RESULT` 相同的内部路径。模板不保存用户输入、Assistant 文本、Prompt 或模型原始输出。

## 9. ProjectDiscussionContext

新增最终 Context subtype：

```text
ProjectDiscussionContext
- contextHandle
- conversationId
- contentReleaseId
- projectId
- switchCandidateProjectIds（最多五个，必须包含 projectId）
- startedAt
- expiresAt
- sourceRecommendationHandle?（仅 lineage；切换授权以复制后的 candidate IDs 为准）
```

不保存：

- 项目 label/summary（从当前公开内容派生）；
- 用户问题或 ConversationWindow；
- Provider 输出；
- Goal/Plan/Task/SemanticResult；
- 聊天消息。

TTL 最多 30 分钟，并受 Conversation Session absolute expiry 裁剪；读取、刷新、提问和切换不续期。前端必须显示服务端返回的真实 expiresAt，不能假设完整 30 分钟。

ENTER_RESULT 从 Recommendation selectedResults 复制 bounded switchCandidateProjectIds。讨论内 AI 只能在该集合内提出 SWITCH_PROJECT；source Recommendation 后续过期不影响已冻结的讨论切换范围。REENTER_SUBJECT 创建的新 Context 只含当前 projectId，除非用户从新的 Recommendation action 再次进入。

## 10. Conversation active discussion

Conversation Session 增加 typed pointer：

```text
ActiveDiscussionPointer
- contextHandle
- projectId
- contextExpiresAt
```

进入、切换、退出与 clear conversation 必须在 Session 行权威下原子更新：

- ENTER_RESULT/REENTER：写 Context 后切换 pointer；
- 切换项目：新 Context 成功后替换 pointer；
- SWITCH 创建的新 ProjectDiscussionContext 继承原 Context 的 switchCandidateProjectIds，不扩大也不缩减候选集合；
- EXIT：清 pointer；
- clear conversation：撤销 Session 并删除/过期化全部 Context；
- cleanup：删除过期 Context，不得把旧 pointer 恢复为 active。

普通 Fact/Comparison Goal 不再产生 ContinuationContext；ProjectDiscussionContext 是讨论焦点的唯一 pointer owner。

并发请求以 active contextHandle 作为 generation：ROUTE_IN_CONTEXT 的 Goal settlement 前必须再次确认 Session pointer 仍等于请求 handle。切换、退出或 clear 已先改变 pointer 时，旧讨论结果不得提交为新的 PublicTurn，也不得恢复旧焦点。已在切换前完成的终局仍可按同 requestId 只读 replay，但 replay 不执行任何 pointer mutation。

两个并发 ROUTE_IN_CONTEXT 若使用同一个仍 active 的 handle，可以分别形成只读终局，与现有来源级并发 2 一致；它们都不能续期 Context，也不能改变 pointer。只有 ENTER、SWITCH、EXIT、REENTER 属于 pointer mutation。

`projectId` 是公开 ID，可在 Session absolute TTL 内保留用于过期重建；不保存项目标题。当前 ContentRelease 不再包含该项目时，只返回不可重建状态。

State 不单独保存 ACTIVE/EXPIRED 枚举；Conversation Summary 根据 contextExpiresAt、Session absolute expiry 和当前 ContentRelease 派生公开状态，避免状态与时间字段双权威。

## 11. Conversation Summary

`GET /api/agent/conversations/current` 替换为：

```json
{
  "conversationId": "opaque",
  "status": "ACTIVE",
  "activeDiscussion": {
    "status": "ACTIVE | EXPIRED",
    "subject": {
      "kind": "PROJECT",
      "reference": "public-project-id",
      "label": "current reviewed public label",
      "route": "/projects/public-slug"
    },
    "expiresAt": "instant",
    "routeContinuation": {"operation": "ROUTE_IN_CONTEXT", "contextHandle": "opaque"},
    "exitAction": {
      "actionId": "opaque",
      "label": "结束讨论",
      "continuation": {"operation": "EXIT_CONTEXT", "contextHandle": "opaque"}
    },
    "reenterAction": null,
    "newTopicAction": null
  }
}
```

ACTIVE 返回 routeContinuation 与 exitAction；EXPIRED 返回 routeContinuation、backend-owned reenterAction 和“开始新话题”动作，后者使用 `EXIT_CONTEXT` 清除 expired pointer。EXPIRED 的文本只允许 AI 提出 REENTER、START_NEW_TOPIC 或 NEEDS_CLARIFICATION，不执行项目 Goal。

页面刷新恢复焦点，不恢复历史消息。ResumeToken 仍是浏览器唯一持久化的会话凭证；分栏宽度等非凭证 UI 偏好和一次性 handoff URL 不属于会话身份。

## 12. AI Semantic Routing

### 12.1 单一语义解释 seam

自由文本只通过一个 Goal Interpretation seam 进入 AI。输入增加可信字段：

- `interpretationMode = STANDARD | DISCUSSION`；
- 当前 discussion 状态 `NONE | ACTIVE | EXPIRED`；
- locked public project（如有）；
- Recommendation candidateKey、公开 label/aliases（如有）；
- 当前输入、允许的 GoalKinds、Facets、Outputs 和 closed routes；
- absolute deadline。

模型返回 closed `SemanticRouteProposal`，示意：

```json
{
  "kind": "DISCUSSION_ROUTE",
  "route": "CONTINUE_CURRENT_PROJECT | START_NEW_TOPIC | ENTER_RECOMMENDED_RESULT | SWITCH_PROJECT | REENTER_PROJECT | STANDARD_GOAL | NEEDS_CLARIFICATION",
  "candidateKey": "C1 | C2 | null",
  "goal": "closed goal parameters or null"
}
```

AI 负责理解开放语言；它不直接调用 Lifecycle、不写 State，也不能输出 Context handle、resultItemId、Token、Task、DAG、Provider 或证据。

现有 `CONVERSATIONAL` 根结果继续保留，不塞入 SemanticRouteProposal。STANDARD 与 DISCUSSION mode 都可返回 CONVERSATIONAL，用于开放式社交或 Agent 元问题；该结果不创建 Goal、不修改、不退出也不续期 discussion pointer。“开始新话题、切换项目、重新进入”必须返回对应 closed route，不能伪装成 CONVERSATIONAL。极小安全问候 fast path 继续保留，未覆盖的社交表达由 AI 解释。

### 12.2 STANDARD 模式收敛

`MinimalGoalFallback` 中以下自然语言路由同 Slice 删除：

- 推荐/项目关键词判断；
- 阿拉伯数字、中文数字和范围正则；
- 推荐否定句与约束短语表；
- 比较关键词与 alias contains；
- “这个项目/该案例”等指代短语表；
- Provider 失败后的自然语言 Goal fallback。

替换后的确定性输入层只保留：

- 极小的安全社交 fast path；
- 已发布 PRESET；
- typed page subject/handoff 的公开 ID 校验；
- Proposal 的 requestedSize 1—5、closed constraints、公开主体和 Goal shape 校验。

推荐数量、比较意图和公开约束由 AI 提出 closed 值，后端验证。Provider 不可用时自由文本能力诚实返回不可用，不以不完整规则伪装理解。

### 12.3 DISCUSSION 模式权限

ACTIVE 状态下，AI 可以提出：

- `CONTINUE_CURRENT_PROJECT`：当前项目的 `PORTFOLIO_FACT` 或 `APPLY_GENERAL_CONCEPT_TO_PORTFOLIO` Goal；
- `START_NEW_TOPIC`：显式退出项目讨论；
- `SWITCH_PROJECT`：仅指向 typed Recommendation candidate set 中的候选；
- `NEEDS_CLARIFICATION`。

EXPIRED 状态下只允许：

- `REENTER_PROJECT`；
- `START_NEW_TOPIC`；
- `NEEDS_CLARIFICATION`。

带有效 Recommendation reference 的普通 ASK 允许：

- `ENTER_RECOMMENDED_RESULT`；
- `STANDARD_GOAL`；
- `NEEDS_CLARIFICATION`。

locked project 不由模型输出。CONTINUE_CURRENT_PROJECT 的 Proposal 由服务端注入唯一 subject；模型只提出 Facets/Outputs、稳定概念 anchor 和 closed Goal 参数。

### 12.4 后端验证与执行

`SemanticRouteValidator` 依据 typed state 使用闭合转换矩阵：

- route 必须属于当前状态 allowed routes；
- candidateKey 必须唯一映射到当前 Recommendation selectedResults；
- locked subject 不得改变或扩张；
- GoalKind、Facet、Output、requestedSize 和 constraints 必须在闭集中；
- clear conversation 永远不是 AI 可提出 route；
- 不使用模型 confidence 数值决定状态变化。

route 与候选唯一合法时立即执行；零命中、多命中、字段缺失或 NEEDS_CLARIFICATION 时只产生限定澄清，不修改 pointer。AI 输出未知字段、非法 route 或越权主体时 fail-closed。

显式 backend-owned 按钮 operation（ENTER_RESULT、EXIT_CONTEXT、REENTER_SUBJECT）不调用模型，直接进入同一状态转换 authority。

### 12.5 Prompt 资源与实施顺序

本设计不新增第三个 discussion prompt。它依赖 [通用回答语言与深度提示词约束设计](2026-08-20-general-answer-language-and-depth-prompt-design.md) 先完成两个资源的外部化：

- `goal-interpretation-system.txt`；
- `general-knowledge-system.txt`。

Project Discussion 只扩展同一个 `goal-interpretation-system.txt` 的 STANDARD/DISCUSSION schema 与 locked-scope 规则。实施顺序固定为：Prompt 外部化与语言/深度 Slice → STANDARD Free-text Semantic Routing Slice → Project Discussion Slice。两份设计不得各自创建 Goal prompt adapter 或重复公共规则。

### 12.6 失败语义

- Provider/Codec 失败时不修改 active pointer；
- DISCUSSION 模式不回退普通 ASK，也不维护自然语言短语表；
- 无可信 route 时返回 `DISCUSSION_INTERPRETATION_UNAVAILABLE` 与 backend-owned 新 request retry/exit actions；该结果已终局结算，同 requestId 只做幂等重放，不重新调用 Provider；
- STANDARD 模式无可信 Goal 时沿用公开 Goal Interpretation unavailable 终局；
- 跨项目请求只有 AI 提出合法 SWITCH 且候选唯一时执行，否则澄清；系统不自动扩大到候选集外项目。

### 12.7 模型关闭与能力投影

Public Content 的 `agentAvailability` 增加 closed capability：

```json
{
  "status": "AVAILABLE",
  "freeTextSemanticRouting": "AVAILABLE | DISABLED"
}
```

该字段表示配置与启动 readiness，不尝试投影实时外部网络健康。Goal operation 未启用、Provider 未配置或数据审批未满足时为 DISABLED；瞬时 Provider 调用失败仍走 Turn failure，不把 capability 永久切换为 DISABLED。

DISABLED 时前端禁用自由文本 composer 并说明 AI 语义理解未启用；PRESET、Recommendation item discussionAction、EXIT_CONTEXT、REENTER_SUBJECT 等 backend-owned deterministic actions 继续可用。公开作品集浏览不受影响。后端直接收到需要 Free-text Semantic Routing 的请求时返回稳定 `SEMANTIC_ROUTING_UNAVAILABLE`，不使用被删除的自然语言 fallback。

## 13. Frontend 状态与交互

Workspace 每个 session 增加内存态 `activeDiscussion`，来源只有：

- 当前 PublicAgentTurn 的 backend projection；
- `GET /conversations/current` 恢复结果。

不进入 localStorage/sessionStorage/URL/history。

UI 行为：

- Recommendation item 显示“与我讨论”；
- 点击立即发 ENTER_RESULT；
- Composer 上方显示项目焦点条、剩余时间和“结束讨论”；
- active/expired focus 下的自由文本发送 ROUTE_IN_CONTEXT，由 AI 提出 closed route；
- 点击另一卡直接发送新的 ENTER_RESULT；
- 新建会话不继承焦点；
- 切换本地 session 时只显示该 session 的焦点；
- 刷新后显示恢复提示但消息列表为空；
- EXPIRED 时展示“重新进入项目”和“开始新话题”，同时允许输入文本进入仅限 REENTER/START_NEW_TOPIC/CLARIFICATION 的 AI route；
- freeTextSemanticRouting=DISABLED 时禁用自由文本，但不禁用 backend-owned PRESET/discussion/exit/reenter actions；
- pending、failure、retry 与 discussion 继续按 session 隔离。

前端不得按文案、项目 label、卡片位置或历史文本重建 Context。

## 14. 公开错误与恢复

| 稳定码 | 语义 | 公开动作 |
|---|---|---|
| `DISCUSSION_CONTEXT_EXPIRED` | Context absolute TTL 到期 | REENTER_SUBJECT / START_NEW_TOPIC |
| `DISCUSSION_CONTEXT_UNAVAILABLE` | handle 不存在、错误 Conversation 或 Token | 重新从公开项目进入 |
| `DISCUSSION_CONTEXT_MISMATCH` | handle 不是当前 active pointer | 使用当前焦点或重新选择 |
| `DISCUSSION_SUBJECT_UNAVAILABLE` | 项目不再属于当前 ContentRelease | 退出讨论 |
| `DISCUSSION_INTERPRETATION_UNAVAILABLE` | locked scope 内无法可靠形成 Goal | 新 requestId retry / 退出 |

错误不得泄露 handle 是否属于其他会话、内部 State、Prompt、Provider 输出或校验路径。错误响应与 PublicTurn 均禁止缓存。

## 15. State 与数据库迁移

该变化属于 Level 3 State/API Replacement Slice：

- 在 `backend/src/main/resources/db/context` 的独立 Flyway history 上新增 Context schema V4（当前最高 V3）；
- Conversation Session 增加 active/last discussion metadata；
- AgentStatePayloadCodec 增加 ProjectDiscussionContext 和 DiscussionSelectionTemplate closed codec；
- Memory/PostgreSQL 同步实现；
- claim、enter、switch、exit、clear 和 cleanup 遵循现有 TurnDeadline 与 database operation cap；
- 不保留旧 CONTINUE reader、缺省 operation 或双写；
- 本地旧 Continuation State 可失效，不迁移短期 payload；
- `contracts/agent-turn` 的 command、PublicTurn、conversation summary、discussion actions 和 `DISCUSSION_*` 错误 fixtures 必须同 Slice 更新，继续作为前后端共享合同源。

## 16. Replacement 与删除范围

同一 Slice 必须删除：

- 旧 `CONTINUE` 无 operation shape；
- `ContinuationContext.PortfolioFact`、`PortfolioComparison` 及其创建/Codec/Store 测试；
- `AnswerGoalResult.continuation` Goal 级公开字段及后端 projector、contracts fixture、frontend mapper/model 消费；
- `MinimalGoalFallback` 中推荐、数量、约束、比较、alias contains 和表面指代的自然语言路由；
- Lifecycle 中固定把 Recommendation CONTINUE 编译为 `PORTFOLIO_REFINE_RECOMMENDATION` 的分支；
- 前端任何基于 label/text 生成讨论命令的临时逻辑；
- 仅为旧 shape 存在的 fixtures/tests；
- 若 `PORTFOLIO_REFINE_RECOMMENDATION` 迁移后无生产消费者，则删除该 GoalKind、参数和执行分支。

不得新增：

- RecentResultSet；
- 第二个会话存储；
- Assistant 文本到主体的解析器；
- 前端业务 action 映射；
- DiscussionControlPolicy 或任何退出/切换短语表；
- 第三个 discussion system prompt；
- 兼容旧 CONTINUE 的 optional operation；
- 长期聊天历史或 GET result polling。

## 17. Exit Gates

### 17.1 Backend

- RecommendationContext 只能进入自身 selected result；
- ENTER_RESULT 创建 locked ProjectDiscussionContext 并返回默认概览；
- ProjectDiscussionContext 的 switch candidates 等于来源 Recommendation selected subjects，不能扩张；
- ROUTE_IN_CONTEXT 不允许主体扩张；
- 通用概念形成 APPLY_GENERAL_CONCEPT_TO_PORTFOLIO；
- STANDARD free text 的推荐、数量、约束、比较由 AI closed proposal 表达，后端范围校验；
- STANDARD/DISCUSSION 的 CONVERSATIONAL 不修改 discussion pointer；
- active/expired pointer 下的 ASK+FREE_TEXT 由服务端强制走 DISCUSSION mode；
- 模型关闭时 Free-text 请求稳定返回 SEMANTIC_ROUTING_UNAVAILABLE，deterministic actions 不受影响；
- 生产源码不再出现被删除的自然语言 route regex/phrase list；
- 承接式 ASK 的 CHOICE 只包含最近 Recommendation selectedResults；
- 单一候选且 AI route 唯一合法时直接进入，不生成单项 CHOICE；
- 无效/过期 referenceContextHandle 静默退化普通 ASK，不泄露 Context 状态；
- Choice 与直接点击汇入同一 enter authority；
- switch/exit/clear 原子更新 active pointer；
- EXIT_CONTEXT 对 active/expired pointer 都只清 pointer；
- SWITCH 后的新 Context 继承同一 switch candidate set；
- wrong Token/Conversation/release/resultItem fail-closed；
- switch/exit 与旧 ROUTE_IN_CONTEXT 竞争时，旧结果不能越过 pointer generation 提交；
- 同 handle 的两个并发只读讨论请求均可结算且不修改 pointer；
- Context 与 Session absolute TTL 不因读取续期；
- expired reenter 创建新 handle；
- State payload 扫描不含用户问题、ConversationWindow、Prompt 和 raw model output；
- Memory/PostgreSQL contract parity；
- Goal/Provider/DB 继续受同一 TurnDeadline。

### 17.2 Frontend

- 卡片只转发 backend discussionAction；
- 点击项目立即得到概览并显示焦点；
- active/expired focus 的自由文本发送 ROUTE_IN_CONTEXT；
- 多 session 焦点不串线；
- 点击历史其他项目直接切换；
- 退出后下一问题恢复 ASK；
- 刷新只恢复焦点、不恢复消息；
- expired 显示 reenter/new-topic 动作，并允许受限 AI route；
- handle/resultItemId 不进入可见文本、URL 或 storage；
- pending/retry/cancel 与 discussion session 归属一致。
- freeTextSemanticRouting=DISABLED 时仅禁用自由文本，不隐藏确定性 actions。

### 17.3 Browser / Provider

桌面与移动端至少覆盖：

1. 推荐三项 → 点击第二项 → 立即项目概览；
2. 项目上下文内询问稳定概念 → 同项目 APPLY Goal；
3. 直接输入承接式省略表达 → 三项限定 CHOICE → 进入同一项目讨论；
4. 历史卡切换项目；
5. 刷新恢复焦点但无历史消息；
6. 显式退出后普通通用问题不绑定旧项目；
7. 自由文本表达退出/切换/重进时，AI closed route 唯一合法则直接执行，含糊时澄清；
8. 单一推荐结果的承接式输入直接进入，不展示单项 CHOICE；
9. Context 过期 → 明确错误 → 按钮或自然语言重进/开始新话题；
10. PostgreSQL 重启后在 TTL 内恢复 active focus；
11. 真实 Provider 输出只能改变 allowed route、候选引用与 locked scope 内 Goal 参数；
12. invalid JSON、deadline、cancel 与 late result 保持现有终局语义。
13. 模型关闭环境中自由文本禁用，PRESET/卡片讨论/退出/重进继续可执行；
14. 直接 API 误发 ASK+FREE_TEXT 时仍遵守 active/expired discussion pointer。

真实 Provider 验收只记录 operation、公开 GoalKind、locked subject 是否保持、耗时桶和 pass/fail，不记录问题、回答或 Provider 原始响应。

## 18. Rollback

- 回退只使用 Git commit、已验证 JAR 或整体部署版本；
- State 是短期可丢失数据，版本回退时清空 V4 discussion state；
- 不保留旧/new CONTINUE 双栈；
- Public Content 不受 Context state 清理影响；
- 任一 command/state/frontend 消费者未迁移完成时，不把新 authority 接入生产。

## 19. 非目标

- 不恢复完整聊天历史；
- 不让模型选择 Recommendation candidate set 之外的项目；
- 不让模型绕过 SemanticRouteValidator 直接修改、退出或重建 Context；
- 不在讨论中扩张到其他项目或私有主体；
- 不新增长期记忆、跨标签页同步、SSE 或多 Agent；
- 不把项目讨论做成通用 workflow/state-machine framework。

## 20. 审核边界

用户批准本文后，下一步只创建 Implementation Plan，列出 Replacement Slices、精确文件、RED/GREEN 测试、V4 migration、前后端原子迁移、删除清单、真实 Provider gate 和中文提交顺序。

在本文审核通过前：

- 不修改生产 Command、State、API 或 Frontend；
- 不更新 architecture status；
- 不创建兼容桥；
- 不生成 Implementation Plan。
