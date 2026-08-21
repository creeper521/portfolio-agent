# Agent 2.0 动态缺陷清单与修复边界
<!-- DOCUMENT_STATUS: CURRENT_AUTHORITY -->

> **记录日期：** 2026-08-19
> **适用版本：** Agent 2.0（完成 Agent 架构收敛 Slice 0—6 后的本地版本）
> **验证环境：** 最终 packaged JAR、Frontend closed PublicAgentTurn 消费链、`IN_MEMORY`/PostgreSQL 会话状态、本机 Chromium 与确定性 Provider fixture
> **文档性质：** Agent 2.0 真实交互验证账本；当前未关闭项以问题总览状态为准
> **维护原则：** 发现并确认 Bug 后添加；完成修复与对应 Exit Gate 后删除；已解决历史转记演进日志，不在本文累积
> **当前状态：** 2026-08-21 已批准失败恢复与项目讨论补完设计；A2-22—A2-29 进入实施，架构状态为 IN_PROGRESS

## 1. 文档目的

Agent 2.0 已完成 Command、Goal、Plan、Execution、Projection、State、无版本 API 与 Frontend 的整体替换，并通过确定性测试、Testcontainers、packaged-JAR Browser E2E 和单轮真实 Provider canary。但在真实浏览器连续操作中，仍暴露出自动化测试没有覆盖的跨轮状态与超时问题。

本文用于：

1. 统一记录已经实际观察到的用户现象；
2. 区分已复现事实、源码确认根因与仍待验证的推断；
3. 明确修复必须遵守的 Agent 2.0 冻结边界，避免重新引入旧 Router、兼容协议或第二套状态权威；
4. 给后续设计、实施和回归测试提供一份稳定问题清单；
5. 防止修复单个 UI 症状后遗漏同一状态链上的后端语义、取消、幂等与隐私问题。

### 1.1 动态维护规则

本文只描述 Agent 2.0 **当前仍然存在**的 Bug，不保存已关闭事项。

#### 添加 Bug

满足以下条件时，应在处理代码前或完成第一轮诊断后及时更新本文：

1. 用户在真实页面、API、packaged JAR 或真实 Provider 链路中观察到异常；
2. 自动化测试发现生产行为、公开合同、会话状态、安全边界或恢复语义存在缺陷；
3. 问题已经具备最小复现证据，或源码调用链能够确认风险；
4. 问题属于 Agent 2.0 当前生产链，而不是尚未批准的新功能诉求。

新增问题应包含：

- 唯一 ID、严重度、简短标题、证据等级和责任区；
- 用户可见现象或稳定复现方式；
- 已确认事实与待验证推断，二者不得混写；
- 根因、影响范围、修复边界和需要补充的测试；
- 不记录访客原文、模型原始输出、Prompt、Token、凭据或内部敏感数据。

ID 按 `A2-NN` 单调递增。删除已解决问题后允许出现编号空洞，不重排仍开放问题，也不复用旧 ID。

#### 更新 Bug

后续截图、日志、测试或源码分析改变原判断时，应直接修订对应问题，而不是在文末追加互相矛盾的阶段结论。证据不足时标为“待验证”；证据坐实后更新为“已复现”或“源码确认”。

相关症状共享同一根因时，可以合并问题，但必须保留所有用户可见影响与验收场景；不同权威或不同修复边界的问题不得仅因同时出现而强行合并。

#### 删除已解决 Bug

只有同时满足以下条件，才从本文删除该 Bug 的总览行、正文、专属测试缺口和专属 Exit Gate：

1. 生产代码修复已经完成；
2. 针对性单元/集成测试通过；
3. 受影响责任区的全量测试和构建通过；
4. 涉及跨端、浏览器、数据库或 Provider 时，对应 packaged-JAR/Testcontainers/真实 Provider 门已按风险执行；
5. 原始用户操作路径已经重新验证，现象不可复现；
6. 没有通过恢复旧协议、兼容桥、吞错或放宽安全校验掩盖问题。

Bug 删除后不在本文保留“已完成”“已修复”章节。重要行为修复按 `AGENTS.md` 写入 `docs/11-项目演进日志.md`；具体实现和验证证据由 Git 提交、测试及必要的专项报告承载。

如果所有 Bug 均已关闭，保留本文标题、定位、维护规则和一条“当前无未关闭 Agent 2.0 缺陷”，其余问题正文与临时 Exit Gate 删除。

本文不替代：

- `docs/08-当前实现状态.md` 的项目现状描述；
- `docs/13-Agent对话体验与智能编排改造路线图.md` 的历史产品背景（已被当前 Agent 2.0 设计取代，不是待办权威）；
- `docs/14-Agent架构债与防御性设计评审.md` 的历史治理观察（已被当前收敛设计取代，不是实施依据）；
- `docs/superpowers/specs/2026-08-17-agent-architecture-convergence-design.md` 的冻结架构决策；
- 针对本问题另行形成的正式设计与实施计划。

## 2. 证据等级

- **已复现：** 浏览器截图、当前 packaged JAR 安全诊断或稳定操作步骤可以直接证明；
- **源码确认：** 当前调用链能够直接解释现象，不依赖猜测；
- **待验证：** 已有合理机制解释，但仍需针对性测试或运行时观测确认；
- **修复后待验收：** 问题已进入修复范围，但必须通过本文 Exit Gate 才能关闭。

所有日志证据只使用状态码、耗时、operation、稳定错误码和安全枚举，不记录访客原文、模型原始输出、Prompt、ResumeToken 或凭据。

## 3. 问题总览

| ID | 严重度 | 问题 | 当前证据 | 主要责任区 |
|---|---|---|---|---|
| A2-01 | P1 | 明确的项目推荐请求被错误转成主体澄清 | 已关闭（默认数量与确定性边界验收通过） | Goal Interpretation / Goal Policy |
| A2-02 | P0 | 澄清答案消费后丢失原始 Goal 与推荐约束 | 已关闭（两层澄清与 PostgreSQL 恢复通过） | Lifecycle / Clarification State |
| A2-03 | P0 | 前端未把澄清答案记为 USER 轮次，破坏 conversationWindow 交替 | 已关闭 | Frontend Session / Wire Window |
| A2-04 | P1 | 失败请求留下本地 USER 消息，后续窗口持续污染 | 已关闭 | Frontend Turn Lifecycle |
| A2-05 | P1 | 400 合同校验错误被显示成笼统的 Agent 不可用 | 已关闭 | API Error Projection |
| A2-06 | P2 | 澄清/失败期间来源栏仍标记为“当前回答来源” | 已关闭 | Frontend Source Context |
| A2-07 | P0 | 新建或切换会话后旧 failure 继续显示 | 已关闭 | Frontend Session State |
| A2-08 | P0 | pending、retry 等操作可能跨会话错位 | 已关闭 | Frontend Session State |
| A2-09 | P1 | draft、clearNotice、resumeNotice 可能跨会话滞留 | 已关闭 | Frontend Session State |
| A2-10 | P0 | 前端内部超时被当成主动取消，处理中状态直接消失 | 已关闭 | Frontend Transport |
| A2-11 | P0 | 前端超时只 abort fetch，不取消后端 Active Turn | 已关闭（冻结为同 requestId replay，不取消服务端） | Frontend/API Lifecycle |
| A2-12 | P0 | Provider/Goal/Turn deadline 未形成真正的端到端绝对超时 | 已关闭（body-stall 与 active cancel 通过） | Backend Model Transport / Lifecycle |
| A2-13 | P1 | 前后端超时预算相互冲突 | 已关闭（20/25/30/35 秒时间轴冻结） | Cross-end Contract |
| A2-14 | P1 | 后端最终完成的幂等结果无法由前端自动取回 | 已关闭（显式同 requestId 重试取回终局） | Replay / Frontend Recovery |
| A2-15 | P1 | 重新打开后“恢复正常”实际是状态重置加一次新的快速 Provider 调用 | 修复后待真实 Provider LIVE 验收 | Frontend Lifecycle / Provider Variance |
| A2-16 | P1 | 简单问候“你好”被错误升级为必填澄清 | 已关闭 | Goal Interpretation |
| A2-17 | P0 | 澄清可以连续生成新的 Critical Clarification，缺少级联终止规则 | 已关闭（最多两层 typed clarification） | Goal/Lifecycle Policy |
| A2-18 | P0 | 已提交、已一次性消费的历史澄清卡仍可编辑和重复提交 | 已关闭 | Frontend Clarification State |
| A2-20 | P1 | 通用知识生成文案在中文站点发生语言漂移 | 修复后真实 Provider 自动门通过；待浏览器语义验收 | Goal Interpretation / General Knowledge Prompt |
| A2-21 | P1 | EXPLANATION depth 未形成可执行的结构与篇幅差异 | 修复后真实 Provider 自动门通过；待浏览器语义验收 | Goal Interpretation / General Knowledge / Presentation |
| A2-22 | P1 | 同 requestId 重试未冻结完整提交身份 | 源码确认、批准实施 | Frontend Retry / Idempotency |
| A2-23 | P1 | Clarification 消费早于 terminal settlement | 源码确认、批准实施 | Clarification State / Settlement |
| A2-24 | P1 | 单候选 NEEDS_CLARIFICATION 被后端强制进入讨论 | 源码确认、批准实施 | Semantic Routing / Lifecycle |
| A2-25 | P2 | PostgreSQL Session replacement 残留 expired discussion pointer | 源码确认、批准实施 | PostgreSQL Session State |
| A2-26 | P1 | ENTER discussion TTL 被来源 Recommendation 过期时间裁剪 | 源码确认、批准实施 | Discussion Lifecycle |
| A2-27 | P2 | Pending 清理缺少 requestId generation guard | 源码确认、批准实施 | Frontend Turn Lifecycle |
| A2-28 | P1 | Discussion 权威投影、revision、TTL 与恢复动作未闭合 | 源码确认、批准实施 | Public Contract / Frontend State |
| A2-29 | P1 | Provider、Browser、共享合同与隐私门覆盖不足 | 源码确认、批准实施 | Release Verification |

### 3.1 A2-22—A2-29 修复边界

本批次以 [失败恢复与项目讨论补完设计](superpowers/specs/2026-08-21-agent-failure-recovery-and-discussion-completion-design.md) 为唯一实施依据：前端重试必须原样复用内存态提交快照；Clarification 使用 V5 reservation 并在 terminal transaction 消费；Project Discussion 修复单候选、TTL 和 Session replacement parity；V6 提升现有 Session revision 并让成功 Turn 返回当前权威 discussion summary。不得用前端推测状态、模型重试、兼容旧合同或持久化原始输入规避问题。

只有 A2-22—A2-29 的针对性、全量、PostgreSQL、packaged Browser、隐私与获授权 Provider 门全部通过后，才可删除对应条目并恢复架构 COMPLETE。

## 4. 问题簇一：推荐与澄清语义断裂

### 4.1 A2-01：推荐请求被错误转成主体澄清

#### 用户现象

用户输入“给我推荐两个项目”后，系统没有直接从已审核公开项目中选择两个候选，而是要求用户先从项目列表中选择项目。

#### 判断

该请求已经包含：

- 明确动作：推荐；
- 明确数量：两个；
- 明确候选域：当前公开作品集项目。

在 Agent 2.0 的职责划分中，Goal Model 应表达 `PORTFOLIO_RECOMMENDATION` Goal，确定性 Portfolio Capability 再完成候选选择。让用户先选择候选，相当于把推荐模块本应完成的工作退回给用户。

#### 当前结论

Goal Interpretation Prompt 与后续确定性边界缺少一条稳定规则：当公开候选域明确且请求给出推荐数量时，不得仅因未点名具体项目而要求主体澄清。

### 4.2 A2-02：澄清恢复丢失原始 Goal

#### 用户现象

用户为“推荐两个项目”补充一个项目名称后，系统进一步询问“想了解这个项目的哪些方面”，原始推荐目标已经消失。

#### 源码根因

冻结设计要求 Clarification Challenge 在服务端保存短期的 field、subject binding 与 blocked Goals，并在 `RESOLVE_CLARIFICATION` 后恢复原 Goal。

当前 `ClarificationStore.Record` 只保存：

- conversationId；
- resumeTokenHash；
- contentReleaseId；
- challenge fields；
- choice/text binding。

`AgentTurnLifecycleService.resolveInput()` 成功消费文本澄清答案后，没有使用 `consumed.record()` 与 `consumed.answer()` 合并原 Goal，而是把答案文本单独包装成新的 `ASK(FREE_TEXT)`，再次调用 `GoalResolver`。

实际语义因此变为：

```text
原始 Goal：推荐两个项目
澄清答案：周末登录奖励 ABTest 完整闭环
当前恢复方式：把“周末登录奖励 ABTest 完整闭环”当成全新问题重新理解
```

丢失的信息包括：

- recommendation goalKind；
- requestedSize=2；
- 原 Goal 的语义身份（不持久化原始输入锚点或访客问题）；
- 推荐约束；
- blocked Goal 身份；
- 澄清字段与原 Goal 参数的绑定关系。

这与 Agent 2.0 冻结设计 D-30/D-39 的澄清恢复语义不一致。

### 4.3 A2-03：澄清答案没有进入本地 USER 消息序列

#### 源码根因

普通 FREE_TEXT、PRESET 和 SuggestedAction 在请求前都会追加 USER 消息；`handleClarification()` 直接调用 `runTurn()`，没有把提交的 choice/text 记录为 USER 轮次。

一次澄清后，本地消息可能成为：

```text
USER       给我推荐两个项目
ASSISTANT  请选择项目
ASSISTANT  您想了解该项目的哪些方面
```

后端请求合同要求 conversationWindow 从 USER 开始并严格 `USER/ASSISTANT` 交替。当前 `conversationWindowOf()` 只会截取最后 12 条，并在第一条不是 USER 时丢弃首条；它不会发现或修复内部连续的两个 ASSISTANT。

真实运行已经记录到连续 HTTP 400：

```text
http.status_code=400
error.code=VALIDATION_ERROR
```

错误发生在 Provider 调用之前，因此不是模型失败。

### 4.4 A2-04：失败请求会继续污染本地窗口

FREE_TEXT 和 SuggestedAction 会在请求成功前先追加 USER 消息。如果请求随后失败，该消息不会回滚，也没有标记为 failed/not-delivered。

再次操作时，conversationWindow 会包含失败轮次，可能产生连续 USER、连续 ASSISTANT 或与服务端已结算会话不一致的历史，导致后续请求继续失败。

修复不能只在发送前“尽量修剪”数组；必须先定义什么是已提交、已结算、失败和取消的会话轮次，再从可信轮次生成窗口。

### 4.5 A2-16：简单问候被错误升级为必填澄清

#### 用户现象

用户只输入“你好”，系统没有返回 `CONVERSATIONAL` Turn，而是返回一张必填 TEXT 的 Critical Clarification，要求用户补充想了解的项目、案例或概念。

#### 判断

PublicAgentTurn 已有独立 `CONVERSATIONAL` variant，Goal Interpretation closed schema 也允许输出 `CONVERSATIONAL`。纯问候不需要形成 Portfolio/General Goal，更不应强迫用户填写目标后才能继续。

当前真实 Provider 输出说明 Prompt/Goal Policy 没有稳定保护这一最小 conversational 边界。后续应以确定性输入边界或可验证的 Goal policy 保证问候、致谢等安全社交输入不会进入 Critical Clarification；不能依赖 Provider 每次自行判断正确。

### 4.6 A2-17：澄清级联没有终止规则

#### 用户现象

当前页面连续出现两张 Critical Clarification：

1. “你好”后要求补充目标；
2. 用户填写“给我推荐一个项目”后，又要求补充推荐领域或目标；
3. 用户继续填写项目名称后，请求最终进入错误状态。

#### 源码根因

澄清答案当前被重新包装为全新 FREE_TEXT，因此 Provider 可以再次返回 CLARIFICATION。Lifecycle 没有保存原 blocked Goal，也没有澄清深度、同字段重复、无信息增益或最大轮次规则。

这会形成：

```text
CLARIFICATION
→ RESOLVE（答案被当作新问题）
→ CLARIFICATION
→ RESOLVE
→ CLARIFICATION / 400 / CAPABILITY_UNAVAILABLE
```

修复目标不是简单设置一个任意循环次数，而是首先恢复原 Goal；在此基础上，再对重复字段、无信息增益和不可恢复状态给出明确终局，避免无限 Critical Clarification。

### 4.7 A2-18：历史澄清卡没有 consumed/submitted UI 状态

#### 用户现象

第一张澄清卡提交后仍保留已填写文本、可编辑 textarea 和可点击“提交补充”；第二张澄清卡同时处于可提交状态。用户无法判断哪张 Challenge 仍有效。

#### 源码根因

`ClarificationChallengeForm` 只有当前表单本地的 selected/text values 和一个外部 `disabled` prop。ConversationThread 渲染历史 PublicAgentTurn 时：

- AgentMessage 不保存 clarification submitted/consumed 状态；
- PublicAgentTurnMessage 不接收 disabled/activeClarificationId；
- ClarificationTurnView 没有被传入 disabled；
- 新请求 pending 时，历史澄清表单也不会统一禁用；
- 请求成功后，旧表单不会转成只读的“已提交”摘要。

服务端 ClarificationStore 是一次消费权威；重复提交旧 clarificationId 只能得到 already-consumed/unavailable 终局。前端却继续把它展示为有效操作，制造了必然失败的入口。

后续 UI 必须只有当前会话中最新、未提交、仍有效的一张澄清卡可操作；历史卡应显示安全的已提交/已失效状态，不能再次发出 RESOLVE。

### 4.9 A2-20：通用知识生成文案发生语言漂移

#### 用户现象与证据

真实页面反馈指出通用概念回答夹杂英文；原截图识别未取得可用结果，因此此前只保留为待验证假设。2026-08-20 在明确授权下，对修改前 HEAD 使用固定合成矩阵运行真实 Provider 基线：三个 EXPLANATION 档位各三次均未通过简体中文判定；CONVERSATIONAL 三次均通过。验收只记录语言、结构、句数桶、公开终局和耗时聚合，没有记录问题、回答、Prompt 或原始模型输出。

#### 源码根因

修改前 Goal Interpretation 与 General Knowledge 的 system prompt 只描述 JSON shape，没有约束 CONVERSATIONAL message、clarification prompt、statement text 和 caveats 的生成语言。确定性展示标题虽为中文，但正文完全由 Provider 自由生成。

#### 修复边界

- 生成文案固定使用简体中文，允许 JWT、PostgreSQL 等技术标识符；
- topic、subject、dimension、anchor、ID 和闭合枚举仍按请求精确回显，不翻译；
- 不新增模型调用、重试、日志正文或运行时 prompt 覆盖；
- 修复后真实 Provider 使用相同固定矩阵，每个 EXPLANATION 档位至少三次，语言门必须全部通过。

2026-08-20 修复后自动行为门使用相同 Provider 与固定矩阵通过：CONCISE、STANDARD、DETAILED 各三次，CONVERSATIONAL 三次，COMPARISON 一次；语言、结构、句数桶与公开终局全部通过。正文未输出或持久化。独立浏览器语义覆盖尚未确认，因此本项暂不删除。

### 4.10 A2-21：depth 未形成可执行的结构与篇幅差异

#### 运行证据

修改前真实 Provider 基线中，CONCISE、STANDARD、DETAILED 各三次均未落入目标结构与句数桶；部分 STANDARD/DETAILED 请求还未形成完整 ANSWER。聚合证据证明 `depth` 字段虽然存在于 typed request，但没有稳定控制最终可见回答。

#### 源码根因

- Goal prompt 只有固定 `STANDARD` 示例，没有从“简要/默认/详细”语义选择 depth 的规则；
- General prompt 只要求至少一条 DEFINITION 和一条 MECHANISM，没有句数与语义覆盖范围；
- Validator 不限制同角色重复和 DEFINITION/MECHANISM 顺序，展示层可能产生重复标题。

#### 修复边界

- Goal Interpretation 负责从开放表达提出 closed depth；后端继续验证闭合枚举；
- General prompt 把 CONCISE/STANDARD/DETAILED 冻结为 2、4—6、8—12 个主句；
- EXPLANATION 草稿只接受按顺序出现的一条 DEFINITION 和一条 MECHANISM；
- 修复后相同真实 Provider 矩阵必须同时通过公开终局、固定结构、目标句数桶与简体中文门。

2026-08-20 修复后矩阵中三个 EXPLANATION 档位均为三次 `ANSWER:COMPLETE`，观察到的输出桶分别稳定为 CONCISE、STANDARD、DETAILED。独立浏览器对典型用途、边界、权衡与误区的语义覆盖仍待确认，因此本项暂不删除。

## 5. 问题簇二：错误表达与来源上下文

### 5.1 A2-05：合同错误被显示为通用不可用

截图中后端明确返回 400 `VALIDATION_ERROR`，前端显示：

> Agent 暂时无法处理这条请求

该文案会让用户误判为 Provider、网络或整个 Agent 服务不可用。当前错误投影至少没有让用户区分：

- 请求/会话合同错误；
- 澄清已过期或重复消费；
- Provider 不可用；
- 网络异常；
- 系统内部错误。

公开错误仍须避免泄露内部字段与校验细节，但应保留稳定错误类别和可行动建议。

### 5.2 A2-06：来源栏的“当前”语义不准确

`activeSources` 会向后寻找当前会话最近一条 `ANSWER`，因此当前 Turn 是澄清、失败或 pending 时，右侧仍可能显示旧回答来源，并标为“当前回答来源”。

这不会改变事实安全性，但容易使用户认为旧来源支持当前澄清或失败轮次。后续需要在以下语义中明确选择一种：

- 明确写为“最近回答来源”；
- 当前 Turn 非 ANSWER 时置灰并说明来源属于上一回答；
- 切换 Turn 焦点时按被选中的 Answer 展示来源。

## 6. 问题簇三：多会话状态未隔离

### 6.1 A2-07：新对话后旧错误滞留

#### 用户现象

旧会话出现“Agent 暂时无法处理这条请求”后，点击“新对话”，中间消息区已经为空，但底部错误仍继续显示。

#### 源码根因

`AgentWorkspace` 中以下值是 Workspace 全局 `ref`，不是 session state：

- failure；
- pending；
- clearNotice；
- resumeNotice；
- questionDraft。

新建会话目前只创建 `AgentSession` 并清理活跃 ResumeToken，没有清理或重新绑定这些状态。模板又无条件渲染 `failure !== null`，没有检查 `failure.sessionId === activeSession.id`。

### 6.2 A2-08：pending 与 retry 可能跨会话操作

`PendingTurn` 和 `FailureView` 已经携带 sessionId，但 UI 渲染没有按当前活跃会话过滤。

由此产生的风险包括：

- 旧会话请求仍在执行时，新会话显示旧 pending 与“取消回答”；
- 在新会话点击取消，实际取消旧会话 requestId；
- 在新会话点击重试，实际使用旧 sessionId、requestId 和 command 重放旧请求；
- 任意会话的新请求会清空全局 failure，连带影响其他会话的错误状态。

这是跨会话行为错位，不只是视觉残留。

### 6.3 A2-09：草稿与通知可能跨会话

`questionDraft`、`clearNotice`、`resumeNotice` 同样不具备 session 归属。当前可能出现：

- 未发送草稿带入另一会话；
- 旧会话的 clear 失败通知显示在新会话；
- 恢复会话通知在切换后继续显示。

右侧来源已经按 `activeSession.messages` 计算，截图中能够在新会话正确清空；ResumeToken 也通过 watchEffect 跟随活跃会话。这两部分可以作为后续状态归属设计的参考。

## 7. 问题簇四：超时、取消与结果回收失配

### 7.1 A2-10：处理中状态直接消失

#### 用户现象

用户发送“你好”后，界面先显示“正在处理”，随后 pending 和取消按钮直接消失，只留下 USER 消息；没有回答，也没有错误。

#### 运行证据

同一时段后端三个真实 Goal Interpretation 请求最终均成功返回 HTTP 200，但耗时分别为：

- 215,972 ms；
- 183,324 ms；
- 172,687 ms。

安全诊断均记录：

```text
provider.call.completed
provider.operation=GOAL_INTERPRETATION
event.outcome=SUCCESS
```

前端固定 20 秒后 abort，因此在后端完成前已经停止等待。

#### 源码根因

`fetchWithTimeout()` 把两种来源合并到同一个 composite AbortController：

1. 用户主动点击取消；
2. 前端内部 20 秒计时器到期。

两者最终都表现为 DOM `AbortError`，并统一映射成 `AgentTurnFailure.kind = ABORTED`。`runTurn()` 对所有 ABORTED 都静默处理：清除 pending，不追加消息，也不显示 failure。

静默语义本应只属于用户主动取消，却同时吞掉了系统超时。

### 7.2 A2-11：前端超时没有取消后端 Active Turn

用户主动取消会先调用：

```text
DELETE /api/agent/turns/{requestId}
```

然后 abort 浏览器 fetch。

内部计时器超时只 abort fetch，不发送 DELETE。后端因此继续：

- 占用 Active Turn；
- 占用并发槽；
- 等待 Provider；
- 产生外部调用费用；
- 最终完成并保存一个前端不会接收的结果。

### 7.3 A2-12：后端绝对 deadline 没有覆盖完整模型调用

本地实例显式配置了：

- Goal timeout 12 秒；
- Model timeout 15 秒；
- Answer request timeout 30 秒；
- Semantic executionDuration 10 秒。

实际 Provider 请求却运行近三分钟。

当前 `StructuredModelTransport` 在 `HttpRequest` 上设置 timeout，并同步调用 `HttpClient.send(...BodyHandlers.ofString())`。现有运行证据表明该超时没有可靠覆盖完整响应体读取。

同时，Lifecycle 的 `TurnDeadline.after(executionDuration)` 只在 Goal 已解析、进入 `SemanticTurnEngine` 后创建；Goal Interpretation 位于该 Turn execution deadline 之前，因此不受这条绝对期限约束。

后续必须验证并覆盖：

- 建连超时；
- 等待响应头超时；
- 响应体读取停滞；
- Goal decode/validation 时间；
- 用户取消信号；
- 整个 Turn 的 absolute deadline。

### 7.4 A2-13：跨端预算顺序冲突

当前主要预算为：

```text
Frontend request timeout：20 秒
Backend answer request timeout：30 秒
Model timeout：15 秒
Goal timeout：12 秒
```

这些值没有形成明确的单调关系。即使单次 Provider timeout 正常生效，需要 Goal + General 两次模型操作的 Turn 也可能超过前端 20 秒，导致前端先放弃而后端仍认为请求合法运行。

修复前需要冻结一条跨端预算原则，例如：内部 operation deadline < Turn absolute deadline < 客户端等待上限，并为网络余量保留明确空间。具体数值必须通过真实 Provider 延迟分布决定，不能只调整一个常量。

### 7.5 A2-14：已完成结果无法自动回收

后端在前端断开后仍可能完成 requestId，并写入幂等 replay 快照。当前前端超时后：

- 不显示超时；
- 不保留重试入口；
- 不使用相同 requestId 重放；
- 不查询该 requestId 是否已完成；
- 用户再次输入会产生新的 requestId。

因此形成“后端已有答案、用户界面永久丢失”的状态。修复必须复用现有 requestId/Replay 权威，不能新增第二套结果查询状态机。

### 7.6 A2-15：重新打开页面后的“正常”不是旧结果恢复

#### 用户现象

同一个“你好”请求在当前页面先经历 pending 消失；重新打开 Agent 页面并再次操作后，很快出现正常的 Clarification Turn，因此看起来像刷新恢复了此前答案。

#### 运行证据

重新打开页面时，日志先记录一组静态资源与公开内容请求；随后出现一条新的 Goal Interpretation Provider 调用：

```text
provider.operation=GOAL_INTERPRETATION
event.outcome=SUCCESS
http.status_code=200
duration.ms=1881
```

它与此前 172—216 秒后才完成的三条调用不是同一次执行。

#### 源码判断

当前刷新恢复路径只从 sessionStorage 读取 ResumeToken，并通过 `GET /api/agent/conversations/current` 恢复 conversationId/status。该接口不返回历史消息或 Completed PublicAgentTurn；`useLocalSessions()` 也不会跨完整页面重载持久化消息。

因此现有证据不支持“页面重新打开后自动取回旧 requestId 结果”。更准确的解释是：

1. 页面重新加载清除了旧 Workspace 内存中的 pending/failure/损坏窗口；
2. 用户再次提交后产生了新的 Provider 调用；
3. 新调用只耗时 1.881 秒，落在前端 20 秒窗口内；
4. 新结果正常显示，从体验上掩盖了上一请求仍在后端长时间运行且无法回收的问题。

这说明故障具有明显的时序和 Provider 延迟波动特征。重新打开页面只是偶然绕过状态污染和慢调用，不是可靠恢复策略，也不能作为问题关闭依据。

## 8. 自动化为何没有发现

现有门禁覆盖了大量单点合同，但没有覆盖这些真实跨轮组合。

### 8.1 Frontend 单元测试缺口

当前测试覆盖：

- FREE_TEXT 提交与正常两轮 window；
- RESOLVE_CLARIFICATION command 的字段形状；
- API failure 与相同 requestId 重试；
- 主动取消；
- clear 与 ResumeToken。

缺少：

- 澄清答案写入 USER 轮次；
- `CLARIFICATION -> RESOLVE -> CLARIFICATION/ANSWER -> 下一轮` 的 window；
- 请求失败后本地消息是否进入下一窗口；
- failure 后新建/切换会话；
- pending 时新建/切换会话；
- 新会话不得取消或重试旧会话请求；
- timeout 与 user cancel 的不同 UI 语义；
- timeout 后使用同 requestId 恢复结果。
- 页面重新打开后不得通过新 requestId 假装恢复旧请求；测试必须区分 replay 与重新执行。

### 8.2 Backend 单元/集成测试缺口

ClarificationStore 测试证明了短 TTL、一次消费与 binding 校验，但没有证明消费后能够恢复原始 blocked Goal。Lifecycle 也缺少“原推荐 Goal + 澄清答案 -> 同一推荐 Goal Proposal”的完整断言。

模型 Transport 测试没有覆盖 Provider 已返回响应头但响应体长期不完成、外层 deadline 触发和取消传播。

### 8.3 Browser E2E 与真实 Provider canary 缺口

修复前的 packaged-JAR E2E 只覆盖 preset/replay/Bearer/clear、closed Turn UI 与 cancel requestId 目标隔离；当时的取消用例分别发生在 POST 尚未进入后端、以及后端已结算之后，不能证明 active cancel、cancel-wins 或迟到结果抑制。补充的慢 Provider active-cancel 证据见 §10.6；真实 Provider 仍未执行。

此前真实 Provider canary 是单轮、快速的稳定通用问题；它证明真实 Provider 可连接和结构输出可解析，但不能证明长延迟、跨轮澄清和多会话状态正确。

## 9. 修复边界

后续修复必须遵守已经批准的 Agent 2.0 架构，不重新讨论或扩张系统：

1. 保持唯一 `AgentTurnLifecycleService`、GoalResolver、SemanticPlanCompiler、SemanticTurnEngine 与 PublicAgentTurn Projector；
2. 保持无版本 `/api/agent/turns` 与 `/api/agent/conversations/current` 四条资源；
3. 保持 `ASK | CONTINUE | RESOLVE_CLARIFICATION` closed commands；
<!-- RETIRED_CONTRACT_REFERENCES:BEGIN -->
4. 不恢复旧 Router、Confirmation、stp-v1/v2/v3、ConversationAnswer DTO 或兼容桥；
<!-- RETIRED_CONTRACT_REFERENCES:END -->
5. 澄清状态继续短 TTL、一次消费、绑定 Conversation/ResumeToken/ContentReleaseId，并受加密 Agent State 管理；
6. 不保存完整访客问题、Prompt、模型原始输出或长期聊天记录；
7. 超时恢复复用 requestId 与现有 replay authority，不建设第二个结果缓存；
8. 模型不能决定 Task、DAG、Provider 或扩大公开主体；
9. 错误分类可以更准确，但不得向前端泄露内部字段、栈、Provider 响应或安全绑定。

## 10. 建议修复批次

本节只定义问题边界和依赖顺序，不代表具体实现已经批准。

### Batch A：澄清权威闭环

- Clarification State 保存恢复 blocked Goal 所需的最小、typed、加密绑定；
- RESOLVE 合并答案到原 Goal，而不是把答案文本重新当作独立问题；
- 推荐数量、goalKind、公开主体、输出与闭合约束在澄清前后保持不变；
- 本设计显式取代“恢复原始 input anchor”的字面要求：状态只保存隐私安全的 typed Goal，恢复时由服务端生成固定语义锚点与 goalKey；
- 明确 recommendation 在公开候选域下何时允许直接执行、何时必须澄清。

### Batch B：Frontend 轮次与会话状态隔离

- 澄清答案形成明确 USER 轮次；
- conversationWindow 只由合法、已提交的轮次生成；
- failure、pending、draft、notice 明确归属 session 或 workspace；
- 所有取消、重试和渲染按 active sessionId 校验；
- 定义 pending 时切换会话的产品行为。

### Batch C：端到端 deadline、取消与回收

- 区分 user cancel、client timeout、network abort；
- client timeout 必须显示可理解且可恢复的状态；
- 冻结跨端 deadline 顺序；
- Provider 调用增加覆盖完整响应体的 absolute deadline；
- Goal Interpretation 纳入 Turn 总预算和取消传播；
- timeout 后按相同 requestId 恢复或重放最终结果。

### Batch D：错误与来源体验

- 将公开错误收敛为稳定、可行动的类别；
- 修正“当前回答来源”的时间语义；
- 确保错误、来源和 pending 均与当前 Turn/Session 对齐。

### 10.5 修复进展（2026-08-19 前端批次）

前端责任区的生产修复已完成本地验证。以上条目在通过 §12 Exit Gate 前保持「修复后待验收」，不从本文删除。已实现内容：

1. **Batch B 前端（A2-03/04/07/08/09）：** pending、failure、draft、notice 全部归属 session；取消/重试/渲染按活跃会话过滤；pending 允许跨会话并存，结果回流原会话；澄清答案记为 USER 轮次（CHOICE 显示公开选项标签、TEXT 显示原文）；失败/取消 USER 轮次标记 `failed`、排除出 conversationWindow，同 requestId 重试成功后解除标记。
2. **Batch D（A2-05/06）：** 新增 `turnFailureProjection`，按冻结错误码/HTTP 状态把失败投影为 SESSION_EXPIRED、CONVERSATION_MISMATCH、TURN_CONFLICT、SERVICE_UNAVAILABLE、RATE_LIMITED、CONTRACT_INVALID、TIMEOUT、NETWORK、UNKNOWN 稳定类别，各配行动建议，仅可恢复类别提供同 requestId 重试；来源栏按最近 Turn 语义显示“当前回答来源/最近回答来源”并在 stale 时弱化。
3. **A2-10 前端部分：** 传输层区分内部计时器超时（`TIMEOUT`）与用户主动取消（`ABORTED`）；前端等待上限已从临时 20 秒切换为冻结的 25 秒，超时显示明确状态与同 requestId 重试入口，用户取消保持静默。后端绝对 deadline（A2-12/13）已实现，仍待完整 Exit Gate 验收。
4. **A2-18：** 澄清挑战卡引入 ACTIVE/CONSUMED/SUPERSEDED 生命周期；提交即把原卡转只读摘要，历史卡一律只读，仅最新未消费卡可操作，pending 期间全部禁用。
5. **交互恢复（重构中被简化、按现行合同重建）：** 滚动纪律（上滑停止自动跟随 + “回到最新回答”）；来源面板“定位”入口跳转并高亮回答内引用该来源的 section（`sectionId` + `publicSourceKeys` 推导，纯前端）；澄清卡在无后端建议时以已发布 QuestionPreset 作为脱困入口——叶子组件只渲染上层传入的已发布预设（presetId 走 PRESET 命令），前端不自造业务问题（§11 第 6 项确认后修订）。
6. **§11 第 1 项冻结后的补充实现：** 同一标签页合计 pending 上限 2（与后端来源级最大并发 2 对齐），超出时不发请求、仅输入区提示；上限作用于 FREE_TEXT、Preset、SuggestedAction、澄清提交与失败重试全部新轮次入口。

### 10.6 跨端验收进展（2026-08-19）

已将 packaged-JAR 和 Live Provider 脚本从旧版本化 Agent 合同迁移到四条最终无版本资源，请求使用闭合 Command，响应按根级 `PublicAgentTurn` 断言。当前进展：

- DEFAULT（显式 IN_MEMORY）的桌面/移动端最终合同、会话隔离、取消 requestId 目标隔离、澄清恢复、同 requestId replay 和来源语义已通过 packaged 验收；该结果尚不等于 active cancel 已验收；
- ADMISSION 使用独立低 RPM JVM 配置，双通道 429 与前端倒计时已通过；
- DEPTH_TWO 使用独立 JVM/浏览器项目，验证“产生 Challenge → 提交答案 → 恢复原 Goal”两阶段链路；当前生产模型仍是单轮澄清，不把未实现的第二轮伪装为已验收；
- CONTENT_ONLY 通过公开 `agentAvailability` 中性投影隐藏提交界面，公开内容仍可浏览，直接 POST 继续以 `AGENT_STATE_UNAVAILABLE` 失败关闭；
- BODY_STALL 在不增加任意生产 endpoint override 的前提下已通过 packaged 验收：测试 JVM 使用临时 hosts/truststore 将固定审核 Provider 主机映射到只接收固定假凭据的本地 HTTPS fixture；fixture 返回响应头和部分正文后停滞。浏览器等到 `ACTIVE:1` 后对精确 requestId 发送 DELETE 并得到 204，fixture 随后观测到 `CLOSED:1`，页面无迟到 PublicTurn 且 Provider 请求计数仍为 1；临时证书、hosts、truststore、端口与进程均已清理；
- 标准 PostgreSQL packaged lane 已使用仓库外一次性 EnvFile 和独立验证库完成 context V3 迁移；临时文件已在 `finally` 删除，未改写用户的 `.env.postgres.local`。首轮验收暴露的 `requestedSize: null` 解码问题已修复，最终桌面/移动端矩阵通过；
- LIVE 脚本已迁移最终合同并通过 Fake Backend 自测，本轮未获得真实 Provider 执行授权，因此未运行。
- 旧 model-led canary 与旧 Provider response checker 已在旧 Answer 表面清理中删除或退出最终验收路径；

真实 Provider 仍未获得本轮执行授权；依赖该证据的问题继续保持「修复后待验收」，不提前删除。

## 11. 修复前需要冻结的选择

以下选择会影响具体代码，但不改变 Agent 2.0 总架构。状态标注为「已冻结」的选择已于 2026-08-19 随前端修复批次确定：

1. **已冻结**：pending 时允许切换/新建会话；旧请求后台继续执行，结果与取消入口都归属原会话，不自动取消；每个会话最多一个 pending，同一标签页合计最多两个（与后端来源级最大并发 2 对齐），超出时其他会话仍可浏览但输入区提示“已有两个请求正在处理”并暂停一切新轮次提交；
2. **已冻结**：澄清答案在页面内存消息中展示公开安全摘要——CHOICE 显示选项标签，TEXT 显示原文；
3. **已冻结**：client timeout 后采用同 requestId 显式重试（复用现有 replay 权威，不自动轮询、不新建结果查询状态机）；超时不取消服务端 Active Turn，以重放回收最终结果（A2-11 的行为边界）；
4. **已冻结**：同一 absolute timeline 上，Goal/General/Portfolio/DB 单次上限为 8/10/4/3 秒，18 秒后不再启动新 Task，服务端 Turn 20 秒、前端等待 25 秒、网关至少 30 秒、lease 35 秒；子操作使用 `min(自身上限, Turn 剩余时间)`，不得独立延长 Turn；
5. **已冻结**：当前 Turn 非 ANSWER 时来源栏显示“最近回答来源”并整体弱化，不隐藏；
6. **已冻结**：澄清卡脱困入口只消费已发布 QuestionPreset 或后端 `suggestedActions`，前端叶子组件不自造业务问题（2026-08-19 确认第 6 项后由硬编码入口修订为预设驱动）。

在这些选择冻结前，不应通过零散条件分支修补 UI。

## 12. 最终 Exit Gate

### 12.1 Backend

- 原推荐 Goal 经 TEXT/CHOICE 澄清后保持同一 goalKind、requestedSize 与约束；
- Clarification 一次消费、过期、重复、错误 Token、内容版本变化继续 fail-closed；
- Goal、General 与完整 Turn absolute deadline 均有受控超时测试；
- 响应头已返回但响应体不结束时，Provider 调用仍在 deadline 内终止；
- user cancel 能传播到仍在进行的 Goal Provider 调用；
- timeout/cancel 后只允许一次终局结算；
- Maven 全量与 Testcontainers PostgreSQL 通过；独立 PostgreSQL migration/verify 与 packaged Browser E2E 进一步覆盖真实本地数据库路径。

### 12.2 Frontend

- 澄清提交后 conversationWindow 始终从 USER 开始并严格交替；
- 纯问候稳定返回 CONVERSATIONAL，不创建必填澄清；
- 一次澄清提交后原卡立即变为只读，历史 clarificationId 无重复提交入口；
- 连续澄清必须证明字段与原 blocked Goal 的信息增益，重复/不可恢复时进入明确终局；
- failed/cancelled/timeout 轮次是否进入窗口有明确且一致的断言；
- 新建/切换会话不显示旧 failure、pending、draft 或 notice；
- 新会话不能取消或重试旧会话 requestId；
- internal timeout 有明确错误和恢复入口，user cancel 继续静默；
- 全量 Vitest 与 vue-tsc/Vite build 通过，Vue warning 为零。

### 12.3 Packaged-JAR Browser E2E

至少覆盖桌面与移动端：

1. `推荐两个项目 -> 必要澄清 -> RESOLVE -> 两项推荐 ANSWER`；
2. `CLARIFICATION -> RESOLVE -> 下一轮 SuggestedAction`，无 400；
3. `你好 -> CONVERSATIONAL`，不得出现必填表单；
4. 澄清提交后旧表单只读，重复点击、双击或返回历史位置都不能重复 RESOLVE；
5. failure 后新建会话，旧错误不出现；
6. pending 时切换会话，状态和取消目标不串线；
7. 模拟慢 Provider 超过客户端预算，界面不得直接消失；
8. timeout 后以同 requestId 取回最终答案或得到明确终局；
9. 来源栏与当前 Turn/最近 Answer 的标签语义一致；
10. 同一输入在首次超时、重新进入页面后，能够证明是原 requestId replay/recovery，或明确提示这是一次新请求。

### 12.4 真实 Provider 验收

在普通 CI 之外显式授权运行：

- 快速 conversational；
- 稳定 general explanation；
- 两项目 recommendation；
- 至少一条真实 clarification/resolve；
- 一条受控慢响应或等价的 Fake Provider body-stall 测试。

验收只输出 requestId 是否一致、PublicAgentTurn kind/resolution、Goal 数量、Provider operation、耗时桶、cancel/replay 终局，不输出问题、回答或原始模型内容。

## 13. 当前结论

Agent 2.0 的单一合同和单一生产链已经成立；本轮问题不要求恢复旧架构，也不证明整体重构失败。当前缺口集中在四个跨边界连接处：

1. Clarification State 没有真正恢复原 Goal；
2. Frontend 消息列表没有形成可信 Turn ledger；
3. 多会话 UI 状态仍停留在 Workspace 全局；
4. Provider、Turn、HTTP 与浏览器没有共享一套可执行的 deadline/cancel/replay 语义。

这些问题应作为 Agent 2.0 的首次真实交互收口处理。任何单点修补只有在本文列出的跨轮、多会话、慢 Provider 和 packaged-JAR Exit Gate 同时通过后，才能标记为关闭。
