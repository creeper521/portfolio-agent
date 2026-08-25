# Agent 四角色会话切换与回答适配设计
<!-- DOCUMENT_STATUS: APPROVED -->

> **日期：** 2026-08-25
> **状态：** 已由用户逐项确认；待用户审阅本文后进入实施计划
> **适用仓库：** `D:\code\agent`
> **Guardian 分级：** LEVEL_3（改变用户可见会话语义、共享前端状态与角色消费边界；不新增第二生产权威）
> **对应缺陷：** A2-53 及角色切换相关 Frontend Session 行为
> **设计原则：** 角色切换必须新建会话；角色只适配表达，不改变事实、意图、深度、证据与权限；优先复用现有模块，不建设通用策略平台

## 1. 文档目的

当前系统已经把闭合 `AudienceRole` 传入 Turn，并在 Portfolio 中调整部分 Facet 顺序、在 General 请求中携带角色，但尚未形成完整、可验收的四角色产品行为：

1. 完整 Agent 页面没有角色切换入口；
2. 用户可见的“切换角色”语义尚未冻结；
3. Goal Interpretation 仍可读取角色并据此选择 emphasis，存在角色改变意图解释的风险；
4. General Provider 收到角色枚举，但系统提示词没有解释四个角色的含义；
5. GUEST 尚无明确的 Portfolio 排序策略；
6. 完整 Agent 的公共推荐问题没有按会话角色过滤；
7. A2-53 所要求的真实 Provider/Browser typed 差异矩阵尚未完成。

本文冻结一个轻量闭环：把角色定义为用户可见本地会话的不可变表达偏好，切换角色时创建全新会话；在 Portfolio、General 与推荐问题三个现有消费点形成可测差异，同时明确 Goal、Synthesis、PresentationComposer、Claim 排序与权限边界不消费角色。

本文只定义功能、状态和验收语义。角色入口的位置、组件形态、图标、颜色、动效、移动端布局与文案细节由 Frontend Agent 在后续 UI 设计中决定。

## 2. 当前事实基线

### 2.1 已有合同与状态

- 请求闭合枚举为 `INTERVIEWER | MENTOR | HR | GUEST`；Backend 与 Frontend 已有对应类型。
- `AgentSession.role` 已存在于本地会话对象，直接新建会话默认 `INTERVIEWER`。
- `surfaceContextOf(session)` 会把 `session.role` 作为 `surfaceContext.audienceRole` 逐轮发送。
- `RequestFingerprintFactory` 已把角色纳入请求指纹；同一 requestId 不能用另一角色重放。
- 首页 `AudienceDialogue` 已按选中角色发送请求，并通过内存 handoff 把角色带入完整 Agent。
- 本地会话、消息、草稿、通知、模型选择、Conversation 凭证和 Discussion 状态只存在页面内存；仅当前 ResumeToken 使用现有 `sessionStorage` 槽位。
- `useLocalSessions.createSession()` 创建空白会话并将其设为活跃；当前实现只按是否存在 USER 消息保留旧会话，尚未保护只有未发送草稿的会话。
- Frontend 的 pending、failure、draft 与 notice 已按本地 session 隔离，完整 Agent 已支持最多两个会话请求并发。

### 2.2 已有角色消费

- Goal Interpretation 输入包含可信 `audienceProfile`；当前 prompt 允许模型依据它选择 emphasis。
- Semantic Task 携带闭合 Audience Profile，General、Portfolio 与 Cross-domain 的 supporting tasks 均能获得角色。
- `PortfolioInvocationFactory.prioritize()` 已为 INTERVIEWER、MENTOR、HR 重排 Facet；GUEST 当前直接使用原顺序。
- `GeneralKnowledgeRequest` 携带闭合 Audience，但 `general-knowledge-system.txt` 尚未定义四角色语义。
- Cross-domain Synthesis 当前消费 General 与 Portfolio 的强类型结果，不需要读取角色才能组合。
- `PresentationComposer` 当前负责确定性结构、引用和公开状态，不是角色策略权威。

### 2.3 当前缺口

- 完整 Agent 的 `suggestionChips` 只按 `placements` 过滤公共 Preset，没有按 `preset.audiences` 过滤。
- Case 的 `suggestedQuestions` 是普通文本，没有受众元数据。
- 首页请求进行中仍可能发生角色按钮状态与返回答案角色不一致的竞态，需要由 UI 生命周期关闭。
- `Claim.audiencePriorities` 仍存在于内容模型和公开投影，但当前 Agent Answer 路径不消费它。
- 目前的 deterministic 测试只能证明角色被传播，不能证明四个角色在真实 Provider 和 Browser 中产生合适且安全的输出差异。

## 3. 目标与非目标

### 3.1 目标

1. 用户通过明确交互选择另一个角色时，系统创建并进入一个全新会话。
2. 每个用户可见会话在创建时确定一个不可变角色，并能在会话列表与当前会话区域被识别。
3. 四角色对宽泛问题产生稳定、可描述、可测试的重点与表达差异。
4. 角色绝不改变用户意图、请求深度、主体、事实、证据、状态、贡献边界、权限或隐私规则。
5. 首页、完整 Agent、历史会话、pending 请求和 handoff 对角色的处理一致且不串线。
6. 通过 deterministic tests、固定真实 Provider 样例和 Browser 流程完成 A2-53 的证据闭环。

### 3.2 明确不做

- 不建设统一 `AudiencePolicy` 引擎或规则 DSL；
- 不让 `PresentationComposer` 再次按角色重写答案；
- 不让 Cross-domain Synthesis 直接消费角色；
- 不接入 Claim 级 `audiencePriorities` 排序；
- 不改变检索范围或增加导师专属证据；
- 不做运行时动态角色、角色插件、后台角色管理或自定义 Persona；
- 不做模型动态生成推荐问题；
- 不新增推荐问题 API 或 Case suggestion 受众字段；
- 不新增角色策略版本、决策明细日志或可观测性平台；
- 不在本设计中冻结具体 UI 视觉和交互组件。

## 4. 方案比较

### 4.1 方案 A：现有消费点轻量闭环（采用）

角色由 `AgentSession.role` 固定；Portfolio 使用现有 Facet 排序，General 在现有系统提示词中增加紧凑角色说明，公共 Preset 使用现有 `audiences` 过滤。Goal、Synthesis 和最终 Composer 不消费角色。

优点是变更面小、权威清晰、容易验证，不增加新平台。缺点是角色规则分别位于少数真实消费点，但当前只有三个消费点，尚不足以证明需要抽象统一引擎。

### 4.2 方案 B：统一 AudiencePolicy 服务（不采用）

由一个新模块同时向 Goal、Retrieval、General、Synthesis、Composer 和 Frontend 输出策略。它能集中规则，但会制造跨层依赖和重复改写，还会把单纯的表达偏好扩张成平台能力。

当前没有足够的规则数量、变化频率或重复证据支撑该复杂度。只有后续出现三处以上重复映射、规则漂移或维护成本证据时，才重新评估抽取。

### 4.3 方案 C：Claim 级受众评分与动态角色（不采用）

让 `audiencePriorities` 参与检索、Claim 排名和预算，并允许运行时扩展角色。该方案会改变证据选择、内容合同、治理、安全与测试矩阵，也容易使角色改变事实覆盖。

这与“角色只影响表达”和“不要做重”的产品约束冲突，不进入当前路线。

## 5. 统一术语与权威边界

### 5.1 Audience Role

`AudienceRole` 是用户可见 Agent 会话在创建时确定的不可变表达偏好。

它允许影响：

- 宽泛问题下内容区块的优先顺序；
- 输出预算不足时优先保留的支撑内容；
- 必要术语的解释程度；
- 语气和表达方式；
- 公共推荐问题的筛选。

它禁止影响：

- Route、GoalKind、Goal 参数和 Goal 数量；
- Project/Case 主体；
- 用户明确要求的回答深度；
- 推荐数量、约束和比较维度；
- 可访问证据与检索主体范围；
- 关键 Claim、项目状态和贡献边界；
- `COMPLETE / PARTIAL / NONE` coverage；
- 权限、隐私、安全与 Provider 准入。

### 5.2 用户可见会话与服务端 Conversation

本设计中的“切换角色必须新建会话”指用户可见的本地 `AgentSession`：

- `AgentSession.role` 是产品交互中的角色权威；
- 每个 Turn 从该 session 机械投影 `surfaceContext.audienceRole`；
- 新本地 session 在首次请求前没有服务端 Conversation 身份；
- 服务端 Conversation 继续承担短期 ResumeToken、Discussion 和幂等状态，不在本 Slice 新增一份持久化角色权威。

角色不是安全权限，因此不为防止直接 API 调用者在同一 ResumeToken 下改变角色而扩张服务端状态模型。正常产品路径必须通过本地 session 不变量保证切换角色创建新会话；Backend 仍严格校验闭合枚举和 request fingerprint。

### 5.3 优先级

当用户问题与角色偏好冲突时，固定优先级为：

```text
用户明确要求
  > 安全与证据约束
  > 用户明确要求的深度
  > 会话角色偏好
  > 默认内容顺序
```

角色只能为宽泛问题、内容预算和表达方式提供默认选择，不能覆盖明确问题。

## 6. 会话创建与角色切换

### 6.1 直接进入 Agent

- 没有 handoff 且没有活跃会话时，以 `INTERVIEWER` 创建本地会话。
- Backend 缺少角色时的 `GUEST` 仅保留为旧客户端兼容和安全兜底；正常 Frontend 请求必须显式发送角色。
- 明确传入未知角色时由闭合枚举校验拒绝，不能静默降级为 GUEST。

### 6.2 首页 handoff

- 首页使用用户选中的角色执行轻回答。
- 进入完整 Agent 时，handoff 以该角色创建新本地会话。
- handoff 可以沿用现有幂等 replay 与服务端 Conversation envelope，但不能复用一个其他角色的本地会话。
- 首页没有显式角色选择变化时继续使用现有默认角色。

### 6.3 切换为不同角色

当用户通过角色交互选择与当前会话不同的角色时：

1. 创建一个新 `AgentSession`；
2. 新会话角色设为目标角色并立即成为活跃会话；
3. 只继承当前公开 Project/Case 页面上下文，用于后续请求的 trusted `subjectHint`；
4. 不复制消息、conversationWindow、Goal、recentSemanticState、Discussion pointer、clarification、ResumeToken、conversationId、草稿、错误、通知、pending、模型选择或 seed fingerprint；
5. 新会话模型选择回到现有目录默认；
6. 旧会话有消息或非空草稿时完整保留，可返回继续；草稿只留在旧会话，绝不复制到新会话；
7. 只有没有消息、没有非空草稿、没有 pending/failure 的完全未使用空会话可以沿用清理规则，避免连续试选角色产生空壳历史；
8. 新会话创建或激活失败时保持旧会话活跃，不形成半切换状态。

这条路径不发送 Agent Turn，也不生成一条“已切换角色”的对话消息。

### 6.4 选择当前相同角色

- 不创建会话，不发送 Turn，不改变任何状态。
- 如果用户希望以相同角色重新开始，使用独立的“新建会话”。
- Frontend 可以隐藏、置灰或提示当前角色，具体方式由 Frontend Agent 决定。

### 6.5 角色可见性

功能要求只有两条：

- 会话列表中的每个历史会话可以识别其创建角色；
- 当前会话区域可以识别当前角色。

标签位置、图标、颜色、简称、辅助说明和移动端适配不在本设计中冻结。

### 6.6 自然语言中的角色请求

- “把当前会话切换成 HR”属于状态变更请求，不能暗中修改 `AgentSession.role`；界面应提供或提示使用角色切换入口。
- “请从 HR 的角度分析这个问题”属于本轮明确内容要求，可以影响本轮回答视角，但不改变 session 角色，也不影响后续轮次。
- 一次性视角请求仍受事实、证据、权限与深度不变量约束。

## 7. Pending、取消与状态隔离

### 7.1 完整 Agent

- 旧会话存在 pending Turn 时允许切换角色并创建新会话。
- 旧请求的成功、失败、取消、Conversation envelope、Discussion revision 和恢复动作只能写回发起请求的旧 sessionId。
- 旧请求返回时如果旧会话不再活跃，不得覆盖当前会话的消息、草稿、错误、通知、ResumeToken 槽位或角色标签。
- 返回旧会话时可以看到该请求结果和更新后的会话状态。
- 继续使用现有标签页最多两个 pending Turn 的上限；角色功能不增加第三套并发配额。

### 7.2 首页轻对话

- 首页请求 pending 时禁止修改角色。
- pending 期间角色控件必须具有真实不可操作状态，而不只是视觉置灰。
- 请求结束或取消后恢复选择。
- 返回答案、推荐问题、role label 与 handoff 必须使用提交请求时冻结的角色快照，不能读取一个后来变化的响应式值。

## 8. Goal 与 Plan 不变量

### 8.1 Goal Interpretation 不消费角色

角色不能参与用户意图解释。目标状态为：

- 从 Goal Provider 的可信输入 JSON 和 prompt 语义中移除 `audienceProfile`；
- 删除当前 prompt 中“use it to choose suitable emphasis”的指令；
- 相同 currentInput、可信 subject/context、允许路由和深度条件下，四角色生成相同 Goal Proposal；
- 角色在 Goal 通过确定性校验后，由 Plan/Task 参数装配从原始 `SurfaceContext` 注入 supporting tasks；
- 不让模型通过角色改变 GoalKind、subjects、facets、dimensions、constraints、requestedSize 或 depth。

这不是删除 AudienceRole，而是把它从“意图解释输入”移动到“已确定目标之后的表达适配输入”。

### 8.2 角色不变量测试

固定同一输入分别使用四角色，必须断言：

- Route 与 GoalKind 相同；
- subject、facet、dimension、constraint、requestedSize 与 depth 相同；
- Plan 的 Goal 数量、fulfillment mapping 和证据授权范围相同；
- 允许不同的只有 Task 上的 Audience Profile 以及下游展示顺序/措辞。

## 9. Portfolio 角色策略

### 9.1 消费边界

Portfolio 使用现有 `PortfolioInvocationFactory.prioritize()` 作为 Facet 顺序唯一权威：

- 同一问题的 subject scope、Facet 集合、depth、backend、search strategy 和授权 evidence pool 不变；
- 角色只对已经由 Goal/depth 确定的 Facet 集合排序；
- 用户明确要求单一 Facet 时没有角色重排空间；
- 不读取 `Claim.audiencePriorities`；
- 不按角色改变 Claim 分数、verification 或 coverage。

当输出预算不足时，角色可以从同一个授权 evidence pool 中优先保留不同的 supporting facts；但回答所需的关键 Claim、项目状态、贡献边界及其必要公开支持必须保持一致。因预算产生的额外 source keys 可以不同，这不等于角色获得了不同证据访问范围。

### 9.2 固定顺序

对宽泛 Portfolio 问题，四角色优先顺序为：

| 角色 | Facet 顺序 |
|---|---|
| INTERVIEWER | IMPLEMENTATION → TECHNICAL_DECISION → VERIFICATION → OUTCOME → RESPONSIBILITY → BACKGROUND → LIMITATION |
| MENTOR | TECHNICAL_DECISION → LIMITATION → IMPLEMENTATION → VERIFICATION → OUTCOME → RESPONSIBILITY → BACKGROUND |
| HR | RESPONSIBILITY → OUTCOME → BACKGROUND → IMPLEMENTATION → VERIFICATION → TECHNICAL_DECISION → LIMITATION |
| GUEST | BACKGROUND → IMPLEMENTATION → OUTCOME → RESPONSIBILITY → VERIFICATION → TECHNICAL_DECISION → LIMITATION |

排序只作用于实际存在的 Facet。CONCISE/STANDARD/DETAILED 仍由现有 depth 规则决定集合大小，角色不能补入 depth 未要求的 Facet。

### 9.3 表达要求

- INTERVIEWER：强调为什么、如何实现、如何验证以及技术边界；允许使用公开技术术语。
- MENTOR：强调诊断、决策理由、迭代与限制；只有存在公开 LEARNING/REFLECTION 材料时才表达成长或反思。
- HR：强调背景、职责、贡献边界、交付状态和可迁移能力；不得从技术事实推断性格、稳定性或岗位匹配。
- GUEST：先说明解决的问题、做了什么和当前结果；必要术语首次出现时简短解释，避免不必要的类名、表名和调用链标识。

确定性 Portfolio Presentation 可以通过顺序和已有公开材料满足上述规则，不新增一次模型改写。

## 10. General 角色策略

### 10.1 Prompt 修改

在现有 `general-knowledge-system.txt` 中增加一段紧凑、闭合的 Audience 指令，并保持当前 JSON schema、中文、句数、aspect、comparison pair、caveat、安全和无重试规则不变。

建议语义如下：

```text
Audience changes emphasis and wording only. It must never change requested kind,
depth, subjects, dimensions, factual caution or safety boundaries.
INTERVIEWER: emphasize mechanism, trade-offs and boundaries.
MENTOR: emphasize learning path, common misconceptions and practice boundaries.
HR: emphasize purpose, impact and limitations; avoid unnecessary low-level detail.
GUEST: use plain language and briefly explain necessary technical terms on first use.
Explicit visitor requirements always override audience preferences.
```

具体英文措辞可以在实施时按现有 prompt 风格微调，但上述语义和优先级不可改变。

### 10.2 Validator 边界

- 第一阶段不增加 role-specific runtime Validator；
- 现有 Validator 继续只强制闭合结构、深度句数、中文、aspect、comparison pair 和 caveat；
- prompt 捕获测试必须证明四种闭合角色映射存在；
- 固定真实 Provider 矩阵若证明某条角色差异不稳定，再为已观察到的问题增加最小硬规则；
- 不用脆弱关键词计数或固定文案比较冒充角色质量验证。

## 11. Cross-domain 与最终组装

### 11.1 Synthesis

Cross-domain Synthesis 不直接读取角色：

- General supporting task 已按角色调整通用概念的措辞与重点；
- Portfolio supporting task 已按角色调整 Facet 顺序；
- Synthesis 继续只做一般概念到公开 Portfolio 实例的强类型关系映射；
- concept anchor、subject、Claim category、公开来源和 coverage 不因角色变化。

### 11.2 PresentationComposer

最终 Composer 不新增角色分支：

- 只负责既有结构、引用、状态和确定性 section 组合；
- 不再执行一次 audience rewrite；
- 不根据角色丢弃关键事实、来源或 notice；
- 如果 Browser/Provider 证据证明最终组装抹掉了上游差异，只修复实际抹平点，不预建通用策略层。

## 12. 推荐问题

### 12.1 完整 Agent

- 当前 Case 存在 `suggestedQuestions` 时，继续优先展示最多 3 条；它们保持角色中立。
- 没有 Case suggestions 时，从 `placements` 包含 `AGENT` 且 `audiences` 包含当前 `AgentSession.role` 的公共 QuestionPreset 中取前 3 条。
- 匹配不足 3 条时不使用其他角色 Preset 补足。
- Preset 顺序继续使用公开快照中的稳定顺序，不另建角色排序。

### 12.2 操作型建议

重试、进入案例、退出案例、继续讨论等 `SuggestedAction` 是当前 Turn 的功能动作，不按角色过滤或改写。

## 13. 历史字段、扩展与可观测性

### 13.1 `audiencePriorities`

- 当前功能不消费该字段；
- 在相关说明中标记为历史遗留/当前未消费，不能把字段存在当作功能完成；
- 本次不删除字段、不迁移发布包、不改变公开 Portfolio API；
- 后续是否删除作为独立内容合同清理处理。

### 13.2 闭合角色集合

- 四角色继续是编译期闭合枚举；
- 新增角色必须同时修改共享合同、Frontend 标签、Portfolio 顺序、General prompt、Preset 内容和验收矩阵，并随版本发布；
- 不提供运行时新增、配置化 Persona 或插件入口。

### 13.3 最小可观测性

- 继续使用请求中的闭合角色、requestId、现有 fingerprint 和低基数 operation/kind 诊断；
- 不记录访客问题、回答正文、Prompt、模型原始输出或角色化改写详情；
- 不新增 AudiencePolicy 版本或 Facet 决策流水。

## 14. 单一权威与 Replacement Manifest

| 概念 | 目标唯一权威 | 替换/删除 |
|---|---|---|
| 用户可见会话角色 | `AgentSession.role` | 不新增可变 role store；角色切换只调用新会话创建 |
| 请求角色合同 | `SurfaceContext.audienceRole` 闭合枚举 | 未知值失败关闭；正常 Frontend 不依赖缺省 fallback |
| Goal 语义 | Goal Provider + deterministic Goal validator | 从 Goal 输入/prompt 删除 audience emphasis，不建 role-aware Goal 分支 |
| Portfolio 角色差异 | `PortfolioInvocationFactory.prioritize()` | 为 GUEST 增加明确顺序；不接 Claim priority |
| General 角色差异 | `general-knowledge-system.txt` | 解释已有 Audience 字段；不建第二 Provider 或角色 Validator |
| Agent 推荐问题 | `QuestionPreset.audiences` | 替换完整 Agent 仅按 placement 的过滤；Case 文本保持中立 |
| Cross-domain | 现有 typed Synthesis | 不增加角色依赖或表达重写 |
| 最终公开状态 | `PublicAgentTurnProjector` | 不按角色重算 coverage、notice 或来源 |

实施不得留下“旧 Goal 仍读取 audience + 新 Task 又适配 audience”的双重语义。进入生产路径后，Goal 中的旧 audience emphasis 指令和对应测试必须同期删除或改写。

## 15. 错误处理

- 未知 role：请求合同错误，不能降级为 GUEST。
- 新会话创建失败：旧会话和旧角色保持活跃，不发送 Turn。
- handoff role 非法：handoff 失效并回到安全的新 INTERVIEWER 会话，不复用非法种子。
- 旧会话异步响应晚到：只写回原 sessionId；目标 session 已删除时丢弃 UI 写回并执行现有安全清理，不写入当前会话。
- General Provider 不遵循角色偏好但结构合法：不自动重试、不跨 Provider 重发；记录为真实质量矩阵失败，保留安全输出或按现有语义质量门处理。
- 角色差异导致事实/coverage 不一致：属于硬验收失败，不能解释为随机模型差异。

## 16. 测试设计

### 16.1 Backend deterministic tests

1. Goal Input Factory：同一可信输入在四角色下投影给 Goal Provider 的 JSON 完全一致，且不包含 `audienceProfile`。
2. Goal prompt：不包含任何按角色选择 emphasis 的指令。
3. Plan Compiler：四角色的 Goal、subjects、typed parameters、depth 与 Task 图一致；Audience 只在目标确定后进入 Task 参数。
4. Portfolio Invocation：四角色的 scope、Facet 集合、depth、backend、strategy 相同，只有 Facet 顺序符合 §9.2；显式单 Facet 不变。
5. Portfolio evidence/result：同一 fixture 下关键 Claim、项目状态、贡献边界、必要公开支持与 coverage 相同；允许预算内额外 supporting facts/source keys 不同，但不得越过同一授权 evidence pool。
6. General request/prompt：四角色枚举进入同一 Provider 请求结构，system prompt 含闭合角色语义；既有 depth/aspect/comparison tests 全部保持通过。
7. Cross-domain：四角色的 concept anchor、subject、关系输入、授权 source scope、必要公开支持和 coverage 相同，Presentation 保留 supporting task 的顺序/措辞差异。
8. Request fingerprint：相同 requestId 改变 role 继续产生 fingerprint conflict。

### 16.2 Frontend unit/integration tests

1. 不同角色选择创建新 session；相同角色不创建。
2. 新 session 只继承当前 Project/Case 上下文，角色为目标值，其他会话状态为空且模型选择回默认。
3. 有消息或非空草稿的旧 session 保留；只有完全未使用的空 session 遵守清理规则，旧草稿不复制到新 session。
4. 会话列表与当前区域均可识别角色；历史角色不会随当前选择变化。
5. pending 旧 session 切换角色后，成功、失败、取消、draft、notice、Conversation envelope 和 Discussion 状态不串到新 session。
6. 全局最多两个 pending 的现有断言继续通过。
7. 首页 pending 时角色控件不可操作；结果和 handoff 使用提交时角色快照。
8. 首页 handoff 以所选角色创建新 session；直接新建默认 INTERVIEWER。
9. 完整 Agent 公共 Preset 按 role 过滤且不跨角色补足；Case suggestions 保持优先且中立。

### 16.3 固定真实 Provider 矩阵

使用四个固定、无敏感文本的输入，每个输入分别执行四角色，共 16 个样本：

1. 宽泛 Portfolio 项目介绍；
2. 明确要求技术实现细节的 Portfolio 问题；
3. 稳定通用概念解释；
4. 通用概念应用到一个公开项目的 Cross-domain 问题。

矩阵只记录闭合判定结果、终局类型、角色差异布尔项、延迟桶和安全诊断，不记录 Prompt、访客输入或模型原始正文。

### 16.4 角色行为断言

| 角色 | 宽泛问题应优先体现 | 禁止行为 |
|---|---|---|
| INTERVIEWER | 实现、技术决策、验证、权衡与边界 | 不得虚构内部标识或把所有回答机械套成固定章节 |
| MENTOR | 决策过程、误区、迭代、限制与反思 | 无公开证据时不得编造成长故事 |
| HR | 背景、职责、贡献边界、交付状态与影响 | 不得推断性格、稳定性或岗位匹配 |
| GUEST | 问题、做法、结果、必要术语解释 | 不得删除关键事实、状态、限制或降低用户请求深度 |

明确技术问题必须在四角色下都回答技术问题；角色只能改变组织和解释方式。

### 16.5 Browser Exit Gate

桌面与移动至少覆盖：

1. 当前会话选择不同角色，新会话创建并显示正确角色；
2. 选择相同角色不创建会话；
3. 新会话没有旧消息、草稿、错误、Discussion 或 ResumeToken，当前 Project/Case 上下文仍可用；
4. 返回历史会话后，原角色和原状态完整；
5. 旧请求 pending 时切换角色，结果只回旧会话；两个 session 并发不越过现有上限；
6. 首页 pending 时无法改角色，答案与 handoff 角色一致；
7. 四角色固定样例的最终正文保留预期差异，同时关键事实、必要公开支持、状态、贡献边界与 coverage 一致；预算内额外 supporting sources 可以不同但不得扩大授权范围；
8. Browser console 无 Vue warning，Frontend 全量测试、类型检查和 build 通过。

真实 Provider 和 Browser 矩阵未实际运行前，A2-53 与整体状态保持 `IN_PROGRESS`，不得因 prompt/unit test 通过宣称角色能力完成。

## 17. 实施责任边界

### Backend Agent

- Goal 输入与 prompt 去角色化；
- Plan 后 Task Audience 注入保持闭合；
- Portfolio 四角色 Facet 顺序；
- General prompt 角色语义；
- deterministic invariants 与 Provider 固定矩阵支持；
- 保持公共事实、State、API 与隐私权威不扩张。

### Frontend Agent

- 角色切换入口及全部具体 UI/交互设计；
- 新 session 创建、角色可见性、首页 pending 锁定与角色快照；
- pending/response/sessionStorage/Conversation envelope 隔离；
- 公共 Preset 按角色过滤；
- Desktop/Mobile Browser 验收。

### 验收责任

- Backend deterministic tests 不能替代真实 Provider；
- Frontend component tests 不能替代 packaged-JAR Browser；
- Provider 输出差异不能替代事实与 coverage 不变量；
- 所有门只记录实际运行证据。

## 18. 回退与后续演进

- 回退只使用 Git/JAR/整体部署版本，不增加角色 feature flag 或旧/新策略双栈。
- 若 General 角色提示导致质量退化，回退包含该版本完整 prompt 与调用方，不在运行时并存两套角色模板。
- 若未来证据表明角色规则在三个以上模块重复并持续漂移，可另提轻量 typed policy 抽取；本设计不预先批准。
- 若未来决定删除 `audiencePriorities`，必须单独评估 Bundle schema、公开 API、数据库投影和内容迁移，不夹带在本功能中。

## 19. 完成定义

本设计的实现只有在以下条件全部满足时才可宣称完成：

1. 角色切换只创建新会话，同角色选择不创建；
2. Goal 与 Plan 语义在四角色下保持不变；
3. Portfolio、General 与推荐问题三个批准消费点进入唯一生产路径；
4. Goal 的旧 audience emphasis 被删除，不存在双重角色解释；
5. Frontend pending、handoff、历史会话和 ResumeToken 不串线；
6. deterministic tests、Backend/Frontend 全量、build、packaged-JAR Browser 与批准 Provider 矩阵按风险执行；
7. 四角色差异与事实/证据/状态不变量同时通过；
8. A2-53 只有在真实 Provider/Browser typed 差异矩阵取得证据后才从动态账本删除；
9. `scripts/agent-architecture-status.ps1` 通过，机器状态只记录新鲜证据。
