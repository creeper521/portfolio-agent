# 低信息输入与 Goal Interpretation 稳定化设计

<!-- DOCUMENT_STATUS: APPROVED -->

> 日期：2026-08-25
> 状态：已由用户批准；进入独立实施计划，完成状态仍以 Exit Gate 为准
> 变更等级：Level 2（内部模块行为调整，公开合同与生产权威不变）
> 直接事故：Qwen3.7-Flash 对低信息输入 `1` 返回不可采用的 `NEEDS_CLARIFICATION`，被 `GoalProposalCodec` 以 SCHEMA 层拒绝
> 对应缺陷：A2-116
> 关联权威：`docs/15-Agent 2.0真实交互问题清单与修复边界.md`、`docs/16-Agent单权威持续收敛范式.md`
> 对应计划：`docs/superpowers/plans/2026-08-25-low-information-goal-interpretation-stabilization.md`

## 1. 审核摘要

本批次只关闭一个已经取证的问题：低信息、不能形成项目目标的当前输入不应迫使模型伪造完整 `blockedGoal`，也不应因为无法构造该对象而把正常用户交互升级成模型能力故障。

拟采用的方案是：

1. 在服务端增加独立、保守的 `UnresolvedIntentPolicy`，仅处理可确定判定的低信息自由文本，并返回现有 `CONVERSATIONAL` 结果；
2. 修正 Goal Interpretation 提示词中“无状态时一律澄清”的冲突规则，允许模型在无法形成任何可持久化目标时返回现有 `CONVERSATIONAL`；
3. 保持 `NEEDS_CLARIFICATION` 的严格合同，不允许 `blockedGoal=null` 被接受或自动补全；
4. 将当前事故的拒绝原因类型化，并让本地日志输出安全的结构化键值；
5. 将 `1 → 给我推荐两个项目` 加入回归和现有真实 Provider 矩阵；
6. 不新增模型 root kind，不增加关键词引用闸门，不启动 `goal.proposal.v6`。

本文档审核通过后仍需单独进入实现与验证，不因文档通过而宣称问题已修复。

### 1.1 批准前治理前置项

以下动作不改变生产行为，已在本轮复审前同步完成：

1. 本文已以 `DRAFT` 身份加入 `scripts/documentation-check.ps1` 的 active work artifact 清单，评审期间不再被误判为 historical 文档；
2. `docs/15-Agent 2.0真实交互问题清单与修复边界.md` 已登记 A2-116，并将事实与待验证假设分开记录；
3. A2-80/A2-81 中“Qwen 未配置/等待用户配置”的过期事实已刷新：Qwen 已接通并取得直接推荐成功样本，但尚无受控 P50/P95 和完整质量矩阵；
4. A2-87/A2-88 继续保持 `IN_PROGRESS`，只补充已有少量真实样本、尚未形成可比较矩阵的事实；
5. `docs/08-当前实现状态.md` 和现有 configured model catalog plan 中已经过期的 Qwen 配置描述已同步修正。

用户批准本文后，再执行以下权威切换：

1. 将本文标记从 `DRAFT` 改为 `APPROVED`；
2. 将 checker 中本文的期望状态同步改为 `APPROVED`；
3. 将本文收录到 `docs/00-文档状态索引.md`；
4. 另建独立实施计划；设计批准本身不授权付费 Provider 调用。

## 2. 已确认事实

### 2.1 正常链路

- 直接输入“给我推荐两个项目”时，Qwen 可以返回可采用结果，HTTP 200；
- Provider 的 API Key、Base URL、模型名和网络调用因此已经生效；
- 模型切换后创建新请求、不复用失败请求的行为符合现有设计。

### 2.2 已复现失败链路

低信息输入 `1` 的一次真实 Qwen 返回为：

```json
{
  "kind": "SEMANTIC_ROUTE",
  "route": "NEEDS_CLARIFICATION",
  "candidateKey": null,
  "recentReference": null,
  "goal": null,
  "clarification": {
    "field": "SUBJECT",
    "prompt": "请提供您希望了解的具体项目或案例名称。",
    "blockedGoal": null
  }
}
```

`GoalProposalCodec.decode()` 要求标准 `NEEDS_CLARIFICATION` 的 `clarification.blockedGoal` 为完整对象，因此该响应在 SCHEMA 层被拒绝，并转换成 `SELECTED_MODEL_INVALID_RESPONSE`。

该证据足以确认：

- Provider 调用已完成；
- 失败发生在模型输出返回后的结构解码阶段；
- 当前这个样本不是 `recentConversation` 污染导致的第二轮问题，因为它在仅输入 `1` 时已经出现；
- “模糊历史污染明确新目标”仍是必须回归覆盖的风险，但不能在没有进一步证据时当作本次唯一根因。

### 2.3 与本事故无关的失败

- GLM 的 `SELECTED_MODEL_RATE_LIMITED` 是 Provider 限流；
- IDE 断点导致请求超过 Turn Deadline 时，可能出现 `attempted=false` 的临时不可用结果；
- 以上两者不得与本次 SCHEMA 拒绝合并成一个修复。

## 3. 当前设计中保留不变的部分

以下设计基本正确，本批次不得削弱：

1. 模型输出不可信，必须经过严格结构解码和语义校验；
2. `NEEDS_CLARIFICATION` 若要持久化部分目标，就必须携带合法的完整 `blockedGoal`；
3. `recentReference` 必须命中类型化 `recentSemanticState`，否则 fail-closed；
4. Provider 失败不自动跨模型降级；
5. 失败请求不复用结果，重试产生新请求标识；
6. 不记录模型原始输出、完整提示词、用户输入或密钥；
7. 不在失败后追加一次“修复模型输出”的 Provider 调用；
8. 不让前端承担 Goal Proposal 的结构修复职责。

现存的三类澄清形态也必须保留：

1. 标准模式下携带完整 `blockedGoal` 的部分目标澄清；
2. 存在 `routeCandidates` 时，模型可返回无 `clarification` 的 `NEEDS_CLARIFICATION`，由后端构造有界选择挑战；
3. DISCUSSION 模式下的既有 facet 澄清。

## 4. 问题定义

当前合同把两类语义不同的情况压在同一条模型路径上：

- 用户已经表达了一个可持久化的部分项目目标，只缺少一个字段；
- 用户只输入了 `1`、`?`、`...` 等内容，根本没有形成可持久化目标。

前一类适合 `NEEDS_CLARIFICATION + blockedGoal`；后一类没有合法 `blockedGoal` 可构造。当前提示词仍倾向要求“无状态时请求澄清”，导致模型选择 `NEEDS_CLARIFICATION` 后只能返回 `blockedGoal=null`，最终被严格合同正确拒绝。

因此，问题不在严格校验本身，而在于进入严格模型合同之前缺少一个极窄的确定性出口，同时提示词没有明确授权“零目标”使用既有 `CONVERSATIONAL`。

## 5. 目标与非目标

### 5.1 目标

- 让确定性的低信息输入稳定返回可理解的引导语；
- 保证低信息输入不调用 Provider；
- 保证低信息闲聊之后的明确新目标不被上一轮强制解释成 recent reference；
- 保持标准 `NEEDS_CLARIFICATION` 的严格性；
- 让拒绝日志能区分 SCHEMA/SEMANTIC，并在当前事故上给出闭集原因；
- 用自动化回归和真实 Provider 矩阵决定是否需要后续 v6，而不是凭单个样本重构合同。

### 5.2 非目标

- 不新增 `UNRESOLVED_INTENT`、`PARTIAL_GOAL` 等模型 root kind；
- 不修改公开 Turn variant、前端共享合同或持久化状态语义；
- 不使用“继续、上一个、刚才、第二个”等关键词实现 `ReferenceIntentGate`；
- 不默认停止发送 `recentConversation`；
- 不放宽 `blockedGoal` 必填约束；
- 不自动猜测或补齐 goal、route、subject、recommendation size；
- 不启动 `goal.proposal.v6`；
- 不修改 Provider 重试、超时、限流或跨模型策略；
- 不关闭 A2-87/A2-88，也不宣称 Agent 2.0 整体完成。

## 6. 变更等级与边界

本设计定为 Level 2，原因如下：

- 新增的是 Goal Resolution 内部确定性策略；
- 对外仍使用现有 `CONVERSATIONAL` 和 `SEMANTIC_ROUTE`；
- `goal.proposal.v5` 字段与语义不变；
- 不新增数据库状态、API、公开事件或生产权威；
- 不改变 Provider 选择和失败终局。

若实施过程中需要新增公开 Turn variant、改变 `blockedGoal` 持久化含义、修改 v5 字段语义或引入 v6，必须停止当前批次，按 Level 3 重新冻结设计并获取批准。

## 7. 方案设计

### 7.1 新增独立 `UnresolvedIntentPolicy`

新增内部策略类，职责只有一个：识别可以由服务端确定判定的“零目标低信息自由文本”。它不得并入 `SafeConversationalFastPath`，以免问候/致谢与未解析意图混成一套持续膨胀的规则。

建议入口位于 `GoalResolver.resolveFreeText`：

```text
SafeConversationalFastPath.tryResolve(command)
        ├─ 命中：返回既有问候/致谢结果
        └─ 未命中：构造 GoalInterpretationInput
                         ↓
                 UnresolvedIntentPolicy.tryResolve(input)
                         ├─ 命中：返回 server-fixed CONVERSATIONAL，Provider attempt = false
                         └─ 未命中：进入既有 interpretTyped(...)
```

策略返回现有 `ResolvedGoalSet.conversational(...)`，沿用 `SafeConversationalFastPath` 的服务端固定来源语义；不得调用 `providerConversational(...)`，也不得把服务端固定消息伪装成 Provider 已采用结果。

新策略必须放在 `GoalInterpretationInput` 构造之后，确保它能读取 interpretation mode、routeCandidates、defaultSubject 和 recentSemanticState；不得放在既有问候/致谢 fast path 之前。

固定引导语建议为：

> 请说明你想介绍、比较还是推荐项目，例如“给我推荐两个项目”。

### 7.2 第一版判定范围

第一版只有在以下上下文前置条件全部成立时才允许命中：

1. 当前命令是 STANDARD 模式的自由文本 Ask；
2. `recentSemanticState == null`；任何现存 typed recent state 都必须放行给模型解释；
3. `routeCandidates` 为空；候选序号选择必须进入既有有界选择流程；
4. 当前不是 DISCUSSION typed action；
5. 当前不是 clarification resolve 请求。

在满足上下文前置条件后，对输入按 Unicode code point 遍历。只有每个 code point 都属于下列并集，策略才命中：

- `Character.isWhitespace(codePoint)`；
- `Character.isDigit(codePoint)`；
- `Character.getType(codePoint)` 属于 `CONNECTOR_PUNCTUATION`、`DASH_PUNCTUATION`、`START_PUNCTUATION`、`END_PUNCTUATION`、`INITIAL_QUOTE_PUNCTUATION`、`FINAL_QUOTE_PUNCTUATION` 或 `OTHER_PUNCTUATION`。

该定义采用并集语义，因此 `1`、`123`、`?`、`？`、`...`、`1?`、`12。。` 和 `1 ...` 都可命中。空字符串若已被更上层输入校验拒绝，不要求为了本策略改变 API 合同；若到达本策略，则按同一闭集返回固定引导。

第一版明确排除：

- 包含汉字或字母的内容；
- `继续`、`上一个`、`第二个` 等可能表达引用或选择的内容；
- Java Symbol 类字符，例如 `~`、货币符号和 emoji；它们保守放行给既有模型路径；
- 无法由确定性规则证明是零目标的短文本，例如 `嗯`。

这里的“空白”严格指 Java `Character.isWhitespace` 闭集，不宣称覆盖所有 Unicode Space；如需扩大到 `Character.isSpaceChar`，必须以新增样本和回归单独批准。

`defaultSubject` 本身不构成排除条件：它只提供主体提示，不能为裸数字或标点补造 goal kind/facet。在没有 recentSemanticState、routeCandidates 或 typed action 时，`defaultSubject + 1` 仍返回固定引导。

因此，本策略不是通用 NLU 分类器，也不追求覆盖所有低信息表达。未命中的尾部样本继续进入模型路径，并保留既有 fail-closed 终局。

### 7.3 当前输入优先级

模型提示词需要增加以下优先级规则：

1. 当前输入若明确表达完整、独立的新目标，应优先按当前输入解释；
2. `recentConversation` 只能帮助消歧，不能把明确新目标改写成 recent reference；
3. 只有当前输入显式依赖历史且 `recentSemanticState` 中存在合法目标时，才允许生成 `recentReference`；
4. 不增加服务端关键词闸门，引用语义仍由模型判断、由类型化状态校验兜底。

### 7.4 提示词中的零目标出口

修改 `backend/src/main/resources/prompts/goal-interpretation-system.txt`：

- 明确：当前输入无法形成任何受支持目标，且不存在可持久化的部分项目目标时，返回现有 `CONVERSATIONAL`；
- 明确：标准 `NEEDS_CLARIFICATION` 仅在模型能够生成完整合法 `blockedGoal` 时使用；
- 保留 routeCandidates 和 DISCUSSION 的既有例外形态；
- 将“recentSemanticState 缺失或含糊时请求 bounded clarification”改写为：只有当前输入已经形成可澄清的部分目标时才请求澄清，否则返回 `CONVERSATIONAL`；
- 明确 recentReference 必须同时满足显式历史依赖和 typed state 命中。

可加入一个静态低信息示例以稳定 Provider 行为，但不得引入完整模型输出日志或运行期原文采集。

### 7.5 Schema 标识单一来源

`GoalInterpretationAdapter` 当前 prompt projection 代码和相邻 Javadoc 中都硬编码了 `semantic-route-proposal-v1`，与 `GoalProposalCodec.SCHEMA_VERSION = goal.proposal.v5` 不一致。

本批次应同时删除代码与 Javadoc 中的旧硬编码：运行期 projection 统一引用 codec 的 schema version 常量，Javadoc 改为引用 `GoalProposalCodec.SCHEMA_VERSION` 所代表的当前合同，不再复制字面版本。该动作只修复标识漂移，不修改 v5 字段或语义；不得以此为由静默改变 v5 合同。

### 7.6 拒绝原因类型化

本批次只对已取证事故增加闭集、安全的内部原因，不进行整个 Codec 的异常体系重写。

建议新增：

```text
GoalProposalRejectionReason.CLARIFICATION_BLOCKED_GOAL_REQUIRED
```

当标准 `NEEDS_CLARIFICATION` 的 `clarification.blockedGoal` 缺失、为 null 或不是对象时，Codec 抛出携带该闭集原因的内部解码异常。Adapter 将其映射为：

```text
failure.layer=SCHEMA
failure.reason=CLARIFICATION_BLOCKED_GOAL_REQUIRED
```

其他尚未类型化的 `IllegalArgumentException` 保持既有 SCHEMA 拒绝，不得通过解析异常 message 猜测原因。后续原因只能按真实样本逐项加入闭集。

### 7.7 本地日志可观测性

`backend/src/main/resources/logback-spring.xml` 当前第一个显式 pattern 属于 `BACKEND_INFO` 文件 appender，第二个属于 `BACKEND_ERROR` 文件 appender；Console appender 由 Spring Boot resource include 引入，并不是这两个 pattern 之一。

本批次应：

1. 在 `BACKEND_INFO` 文件 pattern 中增加 `%kvp`；
2. 在 `BACKEND_ERROR` 文件 pattern 中增加 `%kvp`；
3. 为 local/non-prod Console 显式覆盖安全的 `CONSOLE_LOG_PATTERN`，或定义等价的 Console appender，使 IDE 控制台也输出 `%kvp`；
4. 验证 prod structured console 已原生输出 structured key-value，不在 JSON 输出后重复拼接文本 `%kvp`。

Spring Boot 3.5.3 对应的 Logback 版本支持 `%kvp`，但仍必须以启动测试或日志捕获测试证明 local file、local console 和 prod structured console 的最终行为。

允许记录：

- operation；
- modelRef / provider 的非敏感标识；
- failure.layer；
- failure.code；
- failure.reason；
- requestId / conversationId 的既有安全标识。

禁止记录：

- Provider 原始响应；
- prompt；
- 用户原文；
- API Key、Authorization、会话 token；
- Java 异常 message 中未经闭集化的模型字段内容。

日志测试必须分别捕获文件 appender 与 Console 输出，不能用“文件里可见”替代 IDE Console 的排障目标。

## 8. 明确删除与替换

### 8.1 删除

- 删除 prompt projection 代码及其相邻 Javadoc 中的硬编码 `semantic-route-proposal-v1`；
- 删除提示词中“recentSemanticState 缺失时无条件请求澄清”的绝对表达；
- 不保留任何把 `blockedGoal=null` 当作有效标准澄清的暗示。

### 8.2 不新增或不删除

- 不新增 `UNRESOLVED_INTENT` root kind；
- 不新增 `ReferenceIntentGate`；
- 不删除 `recentConversation`；
- 不删除 `NEEDS_CLARIFICATION`；
- 不删除严格 Codec、route validator 或 typed recent-state 校验；
- 不删除现有失败终局和模型切换后的新请求行为。

## 9. 自动化验证

### 9.1 确定性策略测试

至少覆盖：

| 输入/上下文 | 预期结果 |
| --- | --- |
| `1` | server-fixed `CONVERSATIONAL`；Provider 调用 0 次 |
| `？` | server-fixed `CONVERSATIONAL`；Provider 调用 0 次 |
| `...` | server-fixed `CONVERSATIONAL`；Provider 调用 0 次 |
| `1?`、`12。。`、`1 ...` | 按空白/数字/标点并集命中；Provider 调用 0 次 |
| `~` 或 emoji | Symbol 不在第一版闭集，不由本策略命中 |
| `嗯` | 不由本策略命中 |
| `继续` | 不由本策略命中 |
| 有 routeCandidates 且输入 `1` | 不由本策略拦截，进入既有候选选择流程 |
| 有 recentSemanticState 且输入 `1` | 不由本策略拦截，进入模型引用解释路径 |
| 有 defaultSubject、无 recentSemanticState/routeCandidates 且输入 `1` | 返回固定引导；不得凭 defaultSubject 补造目标 |
| 问候语“你好” | 由既有 `SafeConversationalFastPath` 优先处理；即使单独调用新策略，因包含汉字也不得命中 |
| DISCUSSION typed action | 不由本策略拦截 |
| clarification resolve | 不由本策略拦截 |

### 9.2 两轮回归

同一会话执行：

1. `1`；
2. `给我推荐两个项目`。

预期：

- 第一轮返回固定引导，不写入伪造 semantic state；
- 第二轮形成独立推荐目标；
- recommendation size 为 2；
- 不生成 recentReference；
- 不复用第一轮结果或 requestId。

### 9.3 Codec 与诊断回归

- `NEEDS_CLARIFICATION + blockedGoal=null` 继续被拒绝；
- 拒绝层为 SCHEMA；
- 当前样本原因是 `CLARIFICATION_BLOCKED_GOAL_REQUIRED`；
- 通用 schema 错误仍 fail-closed；
- 日志包含闭集层/码/原因，不包含 Provider JSON、prompt 或用户原文。

### 9.4 既有澄清回归

- 标准模式的合法完整 `blockedGoal` 可解码并恢复；
- routeCandidates 下的无 clarification `NEEDS_CLARIFICATION` 仍可由后端构造挑战；
- DISCUSSION facet clarification 保持可用；
- recentReference 无 typed state 时仍拒绝；
- recentReference 命中 typed state 时仍可用。

### 9.5 模型执行投影

确定性低信息路径必须显示：

- Goal Interpretation 未尝试 Provider；
- 不得标记为 Provider adopted；
- 不得把固定引导归因给当前所选模型。

## 10. 真实 Provider conformance

该实现会改变 Goal Interpretation 和 prompt 行为，因此真实 Provider gate 分类固定为 `REQUIRED`。本批次不新建第二套矩阵，直接将以下样本加入现有 A2-87/A2-88 Goal Interpretation lane：

- `1`；
- `?`；
- `给我推荐两个项目`；
- `1 → 给我推荐两个项目`；
- 有 typed recent state 的显式引用；
- 无 typed recent state 的显式引用；
- 合法部分目标澄清。

其中 Qwen 必须复跑原始用户可见路径：同一会话先输入 `1`，确认第一轮 Provider 调用为 0，再输入“给我推荐两个项目”，确认第二轮由 Qwen 生成可采用结果且未形成 recentReference。若共享 prompt/codec 变更可能同时影响 GLM，则在两家凭据可用并获授权时分别执行。

真实 Provider 验证涉及限流和费用，应在已有 canary/matrix 授权下执行。本 spec 本身不授权调用付费 Provider。缺少授权、凭据、Provider 可用性或完整矩阵时，门状态只能是 `BLOCKED` 或 `IN_PROGRESS`，不得宣称 A2-116 或相关 AI 能力完成。

## 11. v6 的数据触发条件

`Provider DTO` 与持久化 `BlockedGoalTemplate` 耦合过深是有效的架构风险，但单个零信息失败不足以证明必须立即重构。

只有同时满足以下条件，才立项 Level 3 的 `goal.proposal.v6`：

1. 样本本身属于合法的部分目标，而不是零目标输入；
2. 多次采样或多个 Provider 重复出现结构失败；
3. 失败集中在服务端可推导或回声型字段，例如 asked/remaining fields、depth、requested outputs、knowledge requirement；
4. v5 conformance 数据表明提示词小修不能把失败率降到冻结阈值内；
5. 已冻结 v6 权威表、替换清单、迁移范围与 Exit Gate，并取得 Level 3 批准。

届时再评估 `PartialGoalDraft → server compiler → BlockedGoalTemplate`，并将回声字段删除与 schema 升级一次完成。不得在 v5 内静默改义。

## 12. 预期改动面

审核通过后的预计改动文件如下，最终以实现前代码检索为准：

- `backend/src/main/java/com/portfolio/agent/turn/planning/GoalResolver.java`
- 新增 `backend/src/main/java/com/portfolio/agent/turn/planning/UnresolvedIntentPolicy.java`
- `backend/src/main/java/com/portfolio/agent/turn/infrastructure/model/GoalInterpretationAdapter.java`
- `backend/src/main/java/com/portfolio/agent/turn/planning/GoalProposalCodec.java`
- 新增或就近定义闭集 rejection reason / typed decode exception
- `backend/src/main/resources/prompts/goal-interpretation-system.txt`
- `backend/src/main/resources/logback-spring.xml`
- 对应单元、集成、日志安全和真实 Provider matrix fixture/report
- `docs/15-Agent 2.0真实交互问题清单与修复边界.md`：批准前登记 A2-116 并刷新 Provider 事实，完成后按账本规则关闭
- `docs/08-当前实现状态.md`：批准前刷新 Qwen 配置事实，完成后记录低信息输入的用户可见行为
- `docs/11-项目演进日志.md`：仅在重要行为修复完成后记录结果，不提前写完成事实
- `docs/00-文档状态索引.md`、`scripts/documentation-check.ps1` 与独立实施计划：按第 1.1 节分阶段维护

不得修改前端公开合同，除非实施时发现当前模型执行投影无法正确表达“未调用 Provider”；若出现该情况，必须暂停并重新评定变更等级。

## 13. 风险与控制

| 风险 | 控制 |
| --- | --- |
| 确定性策略演变成弱 NLU Router | 第一版只接受空白/数字/标点 code point 的并集；字母、汉字和 Symbol 一律放行 |
| `1` 本来是候选选择 | routeCandidates / typed action / clarification resolve 优先，策略不得拦截 |
| `1` 是上一轮编号内容引用 | `recentSemanticState != null` 时一律不拦截，交给模型解释并由 typed state 校验 |
| `CONVERSATIONAL` 被误标为模型输出 | 使用 server-fixed 来源并断言 Provider attempt=false |
| 提示词改动破坏合法澄清 | 三类既有澄清分别做回归 |
| `%kvp` 泄露敏感数据 | 仅记录闭集 structured arguments；增加日志负向断言 |
| 借事故扩大为 v6 重写 | 明确数据触发条件和 Level 3 停线门 |
| 调试断点造成伪故障 | Provider 调试使用条件断点/更长本地 deadline；不改变生产超时 |

## 14. Exit Gate

实现批次只有在以下条件全部满足时才能声明本问题关闭：

- [ ] A2-116 已在实现前登记，事实、假设、修复范围和专属门完整；
- [ ] A2-80/A2-81 与 docs/08/现有 plan 中的 Qwen 配置事实已刷新；
- [ ] `1` 单轮不调用 Provider，返回固定引导；
- [ ] `1?`、`12。。` 等混合低信息输入命中同一闭集；
- [ ] `recentSemanticState != null + 1` 不被确定性策略拦截；
- [ ] `1 → 给我推荐两个项目` 两轮回归通过；
- [ ] 直接“给我推荐两个项目”行为不回退；
- [ ] `defaultSubject + 1` 与问候 fast path 顺序测试通过；
- [ ] 标准合法 blockedGoal、routeCandidates、DISCUSSION 三类澄清回归通过；
- [ ] invalid `blockedGoal=null` 仍 fail-closed；
- [ ] 当前事故日志可见 `failure.layer=SCHEMA` 与闭集 `failure.reason`；
- [ ] local info file、local error file、IDE Console 与 prod structured console 的日志行为分别验证；
- [ ] 日志安全负向测试通过，未出现用户输入、prompt、Provider 原始响应或密钥；
- [ ] schema 标识只保留 `GoalProposalCodec.SCHEMA_VERSION` 单一来源；
- [ ] 无新增公开合同、无 v5 静默改义、无 Provider retry/fallback；
- [ ] `mvn.cmd -f backend/pom.xml test` 全量通过；
- [ ] `scripts/privacy-check.ps1 -Path backend/src/main` 通过；
- [ ] `scripts/documentation-check.ps1` 通过；
- [ ] 真实 Provider gate 记为 `REQUIRED`，Qwen 原始两轮用户路径通过；未获外部调用授权时保持 `BLOCKED/IN_PROGRESS`；
- [ ] 原始浏览器/packaged 用户可见路径通过，不能只用 Codec 单测替代；
- [ ] docs/08 已记录最终行为；重要修复完成后 docs/11 已记录演进结果；
- [ ] A2-87/A2-88 只记录新增证据，不被本批次越权关闭；
- [ ] A2-116 只在生产修复、目标回归、受影响全量门和原始用户路径全部通过后关闭；
- [ ] 本批次提交范围不包含与本批次无关的文件；允许工作区同时存在其他已识别、未被本批次改写的用户在途修改。

## 15. 审核决策

第一轮评审结论为 `APPROVED_WITH_CHANGES`，第二轮复审结论为 `APPROVED`。批准依据如下：

1. 低信息谓词已改为 Java 空白/数字/标点 code point 并集，并以 `recentSemanticState == null` 为硬前提；
2. `SafeConversationalFastPath → input factory → UnresolvedIntentPolicy → interpretTyped` 顺序已冻结；
3. 继续复用现有 `CONVERSATIONAL`，不增加 `UNRESOLVED_INTENT`；
4. v6 继续作为 A2-87/A2-88 数据触发的 Level 3 备选；
5. DRAFT/APPROVED 文档门、A2-116、过期 Provider 事实与完成后 docs/08/docs/11 义务已分阶段列明；
6. Qwen 原始两轮路径被明确列为 `REQUIRED` 的真实 Provider Exit Gate。

本文已由用户明确批准并切换为 `APPROVED`。实现必须通过独立计划推进；设计批准不等于实现完成，也不授权付费 Provider 调用。
