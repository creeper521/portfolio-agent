# Portfolio Agent 运行时可信度设计

- 状态：已实施并验证
- 日期：2026-07-20
- 更新：2026-07-21
- 范围：A 阶段——运行时可信度加固
- 后续：B 阶段“内容治理与学习闭环”、C 阶段“未来智能能力”分别设计

## 1. 背景

当前项目已经具备公开内容 API、确定性回答引擎、首页轻问答、Agent 工作台和 Evidence 浏览能力，但存在以下不一致：

1. 前端展示 4 种访客角色和 16 个推荐问题，后端仅稳定支持一个规范问题。
2. 回答响应中的 `matched`、`fallback`、`answerMode` 和 section 类型在前端映射时丢失，能力边界也会被标记为 Verified。
3. 角色和 Evidence 只影响界面状态，没有进入权威回答上下文。
4. 完整访客问题进入 URL 和 `localStorage`，与 V0 不持久化访客问题的安全约束冲突。
5. 无效 project 或 Evidence 会静默回退到第一项，单项目测试数据掩盖了上下文错误。
6. 后端五段结构化回答在前端被合并为一段长文本。

本设计先解决运行时的真实性、隐私性和可验证性，不在本阶段扩大为模型、RAG、多轮服务端会话或多 Agent 系统。

## 2. 设计目标

1. 页面只承诺运行时真实支持的能力。
2. 推荐问题、回答状态、Evidence 和项目上下文均由服务端权威数据驱动。
3. 明确区分“问题已识别”“答案生成方式”和“事实验证程度”。
4. 访客问题和临时会话不进入 URL、浏览器持久化存储、服务端存储或日志。
5. 首页、Agent 和项目档案按各自场景展示不同密度的同一份结构化回答。
6. 为后续扩展 QuestionPreset、身份化回答和模型能力保留稳定契约。

## 3. 非目标

本阶段不实现：

- 模型或外部 LLM；
- RAG、Embedding 或向量检索；
- 服务端会话和跨设备恢复；
- 真正的身份化答案生成；
- 多 Agent 编排；
- 通用 Hook、插件或扩展运行时；
- 自动内容发布和自我修改；
- B 阶段的 Claim、Case Registry、Benchmark Registry 和发布控制面。

## 4. 轻量运行时编排

A 阶段不引入通用 AgentHarness，而是在现有 Answer 模块内建立轻量 `PortfolioAgentRuntime`：

```text
AnswerController
→ PortfolioAgentRuntime
   ├─ QuestionResolver
   ├─ AnswerContextFactory
   ├─ AnswerEngine
   ├─ VerificationPolicy
   └─ AnswerDecisionPublisher
→ AnswerResponseMapper
```

职责：

- `AnswerController` 只处理 HTTP、输入校验和响应映射。
- `PortfolioAgentRuntime` 固定一次回答的执行顺序和错误归一化，不拥有具体事实规则。
- `QuestionResolver` 只解析 `ANSWERED / BOUNDARY / REJECTED`；Preset 形成事实回答时标记 `answerSource = PRESET`，它不能生成事实。
- `AnswerContextFactory` 从同一个公开内容快照构建权威上下文。
- `AnswerEngine` 生成结构化回答，不能自行授予验证状态。
- `VerificationPolicy` 根据实际公开 Evidence 独立计算验证状态，不能修改正文。
- `AnswerDecisionPublisher` 在最终响应确定后发布匿名观测事件，不能干预执行。

固定流程：

```text
接收请求
→ 获取一次公开内容快照
→ 解析问题
→ 构建权威上下文
→ 生成结构化回答
→ 独立计算验证状态
→ 构建最终响应
→ 尽力发布匿名决策事件
```

Runtime 不保存访客会话、不读取私人治理目录、不加载 Skill、模型或工具。A 阶段不建设通用 Hook、插件系统或 AnswerEngine Registry；出现第二种真实引擎后再在 C 阶段设计选择机制。

## 5. 权威问题模型

### 5.1 单一来源

推荐问题以后端公开的 `QuestionPreset` 为唯一来源。前端不得维护独立的推荐问题清单。

只有同时满足以下条件的 QuestionPreset 才能展示：

- `status = ACTIVE`
- `reviewStatus = APPROVED`
- 关联项目可公开
- 运行时存在可执行的回答定义

新增问题必须同时提供：

- 稳定 `questionPresetId`
- 展示文案
- canonical 文本与可选 aliases
- 关联项目
- 结构化回答定义
- Evidence 关系
- 回归测试

### 5.2 混合提交模式

推荐问题按稳定 ID 提交，自由输入按文本解析：

```json
{
  "questionPresetId": "sql-audit-full-introduction",
  "question": "请详细介绍 SQL 审计与故障排查工具项目……",
  "context": {
    "projectSlug": "sql-audit",
    "audienceRole": "INTERVIEWER",
    "focusEvidenceIds": ["sql-audit-delivery-set"],
    "source": "HOME"
  }
}
```

规则：

1. 推荐问题以 `questionPresetId` 为权威，展示文案调整不得导致匹配失败。
2. `question` 用于界面展示和向后兼容，不得覆盖 preset 的权威定义。
3. 自由输入不携带 `questionPresetId`，服务端通过 canonical 和 aliases 解析。
4. 未解析的自由输入返回能力边界，不进行猜测。

## 6. 回答可信状态

回答状态拆为四个正交维度。`resolution` 只回答“是否形成回答”，`answerSource` 只回答“事实从哪里取得”，不得再用一个枚举同时表达两件事：

### 6.1 resolution

- `ANSWERED`：已形成可展示的事实回答。
- `BOUNDARY`：不在当前能力范围。
- `REJECTED`：输入违法、敏感或违反公开策略。

### 6.2 answerSource

- `PRESET`：由已发布 QuestionPreset 及其结构化回答定义形成。
- `RETRIEVAL`：由 C2 在已发布 Claim/Evidence 上检索并通过充分性判定后形成。

A 阶段的事实回答只实际产生 `PRESET`。`BOUNDARY / REJECTED` 的 `answerSource` 为 `null`。

### 6.3 generationMode

- `DETERMINISTIC`
- `MODEL`
- `FALLBACK`

A 阶段只实际产生 `DETERMINISTIC`。其余枚举用于稳定未来契约，不代表当前已实现。

### 6.4 verification

- `VERIFIED`：回答中的关键事实均由当前版本的 APPROVED Evidence 支持。
- `PARTIALLY_VERIFIED`：只有部分关键事实具有公开 Evidence。
- `UNVERIFIED`：尚未通过事实验证。
- `NOT_APPLICABLE`：能力边界或拒绝说明，不属于事实回答。

UI 必须按四个维度组合展示：

| 条件 | UI 文案 |
| --- | --- |
| `ANSWERED + PRESET + VERIFIED` | 已核验回答 |
| `ANSWERED + PRESET + PARTIALLY_VERIFIED` | 部分事实已核验 |
| `ANSWERED + PRESET + UNVERIFIED` | 尚未核验 |
| `BOUNDARY` | 当前能力边界 |
| `REJECTED` | 无法处理该请求 |
| `generationMode = FALLBACK` | 已使用降级回答 |

任何 `BOUNDARY`、`REJECTED` 或无 Evidence 的回答都不得显示 Verified。

## 7. AnswerContext 与回答快照

客户端提交的是上下文提示，服务端生成权威 `ResolvedAnswerContext`：

```json
{
  "contentVersion": "2026-07-20.1",
  "project": {},
  "questionPreset": {},
  "approvedEvidence": [],
  "audienceRole": "INTERVIEWER",
  "source": "HOME"
}
```

服务端必须验证：

1. project 是否存在且可公开；
2. Evidence 是否存在、为 `APPROVED` 且属于当前项目；
3. QuestionPreset 是否属于当前项目且可执行；
4. 客户端不得提交或覆盖项目状态、贡献类型、验证状态和公开审核状态。

### 7.1 两级不可变快照

服务端维护已完整验证的 `RuntimeContentSnapshot`：

```text
RuntimeContentSnapshot
├─ schemaVersion
├─ contentVersion
├─ runtimeBundleHash
├─ loadedAt
├─ projects
├─ questionPresets
└─ approvedEvidence
```

Snapshot 构造后不可修改，集合使用只读副本。A 阶段由 classpath JSON Repository 提供；B 阶段可以替换为版本化 bundle 实现，而不改变 Runtime、Resolver、Engine 和 VerificationPolicy。

每次请求开始时只获取一次 active Snapshot，并创建 `AnswerTurnSnapshot`：

```json
{
  "turnId": "random-id",
  "requestId": "random-id",
  "contentVersion": "2026-07-20.1",
  "runtimeBundleHash": "sha256:...",
  "projectSlug": "sql-audit",
  "questionPresetId": "sql-audit-full-introduction",
  "approvedEvidenceIds": ["sql-audit-delivery-set"],
  "audienceRole": "INTERVIEWER",
  "source": "HOME"
}
```

同一轮的 Resolver、ContextFactory、Engine 和 VerificationPolicy 必须使用该快照，禁止再次读取 Repository。active 内容变化不修改正在执行的轮次；已完成轮次保留原 `contentVersion`，下一轮使用新的 active Snapshot。客户端提交的 contentVersion 只能用于兼容提示，不能决定服务端事实版本。

四类 Snapshot 的名称和职责固定如下，后续设计只能组合或投影，不能为同一语义再造别名：

| Snapshot | 生命周期 | 固定内容 |
| --- | --- | --- |
| `RuntimeContentSnapshot` | 一个 active 内容版本 | 已验证的公开内容、索引和 `runtimeBundleHash` |
| `AnswerTurnSnapshot` | A 的单次回答 | 对 `RuntimeContentSnapshot` 的只读引用，加本轮项目、Preset、Evidence 与内存 ID |
| `GovernanceRunSnapshot` | B 的一次治理运行 | 私人治理输入、Schema、Policy、Benchmark、工具版本与 `candidatePayloadHash` |
| `AgentExecutionSnapshot` | C 的单次智能执行 | 组合 `AnswerTurnSnapshot` 与 Model/Retrieval/Registry/Extension Policy 版本和预算 |

`turnId` 只存在于当前页面内存及该次请求链路，刷新后消失，不进入持久化事件。`requestId` 可用于响应关联和受控的短时诊断，但不得成为跨请求会话 ID，也不得进入长期聚合遥测。

同一临时会话跨版本时，UI 提示“公开内容已更新，后续回答基于新版本”，但不重写历史回答。历史项目在新版本不存在时显示版本说明，不回退到其他项目。

### 7.2 audienceRole

A 阶段只影响：

- 推荐问题筛选与排序；
- 摘要密度和默认展开方式；
- CTA 与后续探索路径。

它不改变项目事实、贡献边界、Evidence 结论和同一 QuestionPreset 的核心回答内容。真正的身份化表达留到 C 阶段设计。

### 7.3 focusEvidenceIds

Evidence 焦点表示用户当前关注的公开材料，可影响 Evidence 工作台选中状态和推荐问题，但不能由客户端直接授予回答 `VERIFIED` 状态。

## 8. 严格隐私会话

### 8.1 会话生命周期

- 会话只存在于当前浏览器标签页的前端应用内存。
- 同一标签页内切换站内路由后，会话继续存在。
- 刷新、关闭标签页或重新打开网站后，会话消失。
- 不写入 URL、`localStorage`、`sessionStorage`、IndexedDB、服务端存储或应用日志。
- 页面提供持续可见的隐私提示，但不拦截刷新和关闭。

建议文案：

> 临时隐私会话  
> 对话仅保存在当前标签页内存中。刷新或关闭页面后，记录将消失。

对话进行中持续显示：

> 临时会话 · 不保存

### 8.2 前端内存模型

前端不得把权威回答压缩为聊天字符串，使用三个层次：

```text
EphemeralConversation
└─ ConversationTurn[]
   └─ StructuredAnswerTurn
```

- `EphemeralConversation` 只组织当前标签页中的临时会话、角色、项目和轮次。
- `ConversationTurn` 保存独立 `turnId`、问题展示信息、请求上下文、状态、回答或错误。
- `StructuredAnswerTurn` 原样保存服务端的 contentVersion、四个可信维度、sections、Evidence 和建议问题。
- 隐私提示、内容版本变化和 handoff 失效使用独立 `SystemNotice`，不得伪装为 Agent 回答。

可以在同一标签页保留多个临时会话和会话列表，但 A 阶段只提供内存实现，不提供持久化 Adapter、导出或服务端同步。

### 8.3 首页 handoff

首页向 Agent 交接时：

1. 首页回答完成后，在应用内存创建随机一次性 `handoffId`；
2. 跳转到 `/agent?handoff=<id>`；
3. Agent 消费包含完整 `ConversationTurn` 的 `HandoffEnvelope`；
4. 消费后立即从内存 HandoffStore 删除。

`HandoffEnvelope` 包含创建时间、短时过期时间、来源、角色、项目和已完成的结构化轮次。默认五分钟过期；未完成的首页请求不生成 handoff。Agent 不重复请求首页已经取得的答案，避免同一次交接产生另一个 contentVersion。

URL 不包含问题、回答、角色或 Evidence 内容。刷新或重复使用已消费 ID 时，显示：

> 临时上下文已失效  
> 为保护隐私，刷新后不会恢复之前的问题。你可以从当前支持的问题重新开始。

## 9. 结构化回答与三级呈现

回答保留结构，不再映射为普通字符串：

```json
{
  "requestId": "random-id",
  "turnId": "memory-only-turn-id",
  "contentVersion": "2026-07-20.1",
  "questionPresetId": "sql-audit-full-introduction",
  "resolution": "ANSWERED",
  "answerSource": "PRESET",
  "generationMode": "DETERMINISTIC",
  "verification": "VERIFIED",
  "title": "SQL 审计与故障排查工具",
  "summary": "面向首页的简明摘要",
  "sections": [
    {
      "type": "BACKGROUND",
      "title": "项目背景",
      "content": "……",
      "evidenceIds": ["sql-audit-delivery-set"]
    }
  ],
  "evidenceIds": ["sql-audit-delivery-set"],
  "suggestedQuestionPresetIds": ["sql-audit-tech-highlights"]
}
```

`requestId` 用于响应关联和受控短时诊断，`turnId` 由前端为本次内存轮次生成并由服务端原样回传；两者都不得写入持久化遥测。`sections[*].evidenceIds` 是局部引用，顶层 `evidenceIds` 是去重后的回答全集；两者都不得包含未公开或不存在的 Evidence。

### 9.1 首页

- 显示可信状态；
- 显示 150～250 字摘要；
- 最多展示 1～2 条 Evidence；
- 提供“查看完整回答”“进入 Agent”“打开项目”入口；
- 不播放阻塞后续操作的整篇逐字动画。

### 9.2 Agent

- 显示问题和四个可信状态维度；
- 分 section 展示完整回答；
- section 可折叠，默认展开；
- 每个 section 只显示实际关联 Evidence；
- 能力边界和建议问题使用独立状态组件。

### 9.3 项目详情

项目详情继续作为最完整档案。回答 section 可以链接到项目页对应锚点，不复用聊天气泡承载全部项目内容。

### 9.4 Evidence 工作台

默认只显示当前回答实际引用的 Evidence，并提供“项目全部证据”视图。无效 Evidence 不得回退到第一项。

## 10. 前端状态机与错误处理

状态属于单个 `ConversationTurn`，而不是整页共享的全局请求状态：

```text
SUBMITTING
→ ANSWERED | BOUNDARY | REJECTED | FAILED
```

要求：

1. `ANSWERED` 展示结构化回答、验证状态和实际 Evidence。
2. `BOUNDARY` 展示当前支持的问题，不显示 Verified。
3. `REJECTED` 给出安全、可执行的说明，不暴露内部策略细节。
4. `FAILED` 区分超时、网络错误、服务不可用和可修正输入错误。
5. 新问题不删除临时历史，但每一轮拥有独立 Context 和请求状态。
6. 迟到响应只能写回发起它的轮次。
7. 重复提交在请求进行期间被阻止。
8. 无项目、无 Evidence、失效 handoff 均有明确空状态。
9. 公共内容加载失败与回答失败分别呈现。
10. route query 变化必须同步页面状态；无效引用不得静默回退。
11. 每个提交先创建唯一 `turnId`；响应必须同时匹配 `turnId` 和当前请求身份，才能写回该轮次。
12. 用户开始新问题时创建新轮次，不复用旧轮次状态；被移除或取消的轮次收到迟到响应时直接丢弃。

## 11. 被动、匿名化可观测性

最终响应一旦确定，Runtime 可以尽力提交一个严格白名单的 `AnswerDecision`。它是事实记录，不是 Hook，也不是控制点：

```json
{
  "eventVersion": 1,
  "eventType": "ANSWER_DECIDED",
  "occurredAt": "2026-07-20T10:00:00Z",
  "contentVersion": "2026-07-20.1",
  "projectSlug": "sql-audit",
  "questionKind": "PRESET",
  "questionPresetId": "sql-audit-full-introduction",
  "audienceRole": "INTERVIEWER",
  "source": "AGENT_PAGE",
  "resolution": "ANSWERED",
  "answerSource": "PRESET",
  "generationMode": "DETERMINISTIC",
  "verification": "VERIFIED",
  "evidenceIds": ["sql-audit-delivery-set"],
  "durationBucket": "LT_100_MS",
  "errorCode": null
}
```

延迟只使用固定桶，例如 `LT_100_MS / 100_TO_499_MS / 500_TO_1999_MS / GE_2000_MS`，不记录精确耗时。自由输入只允许记录 `questionKind = FREE_TEXT` 及结果分类；canonical 或 alias 命中后也只记录 `questionPresetId`。不得记录自由输入的原文、规范化文本、长度、关键词、哈希、相似度或中间候选。

事件中禁止出现：

- 问题或回答原文；
- title、summary、sections 或其他生成正文；
- 可反推原文的全文哈希和任何 Prompt；
- IP、User-Agent、Cookie、设备或浏览器指纹；
- conversationId、handoffId、完整 URL；
- 私人文件、私人来源或未批准 Evidence；
- 异常堆栈、模型或 Provider 原始响应。

发布器只能接收白名单 DTO，不能接收 HTTP Request、HTTP Response、完整 `AnswerContext` 或完整回答。最终响应必须先确定；观察者失败、队列已满或进程退出都不能修改 verification、替换回答、改变 HTTP 状态或阻塞返回。实现采用有界内存和无重试持久化：队列满时丢弃事件，只累计匿名 dropped count；默认可使用 `NoopAnswerDecisionPublisher`，服务端仍保持无会话、无访客记录。

可聚合统计 Preset 使用次数、各 resolution 与 answerSource 比例、延迟桶、Evidence 覆盖率和错误码。`requestId` 只能进入具有访问控制和短 TTL 的诊断通道；聚合事件与长期统计不得保存 `requestId` 或 `turnId`。

## 12. 可访问性要求

1. 所有输入保留清晰的键盘焦点，不得用 `outline: 0` 移除而无替代。
2. 动态回答完成状态使用合适的 live region。
3. Evidence 选择器必须使用原生按钮或完整键盘语义。
4. 抽屉打开后正确转移焦点，关闭后恢复焦点，并隔离背景交互。
5. `prefers-reduced-motion` 不得导致任何内容保持隐藏。
6. 移动端使用动态 viewport 与 safe-area，软键盘不得遮挡输入区。

## 13. 验收标准

1. 页面展示的每一个推荐问题提交后都返回 `ANSWERED + PRESET`。
2. 推荐问题只来自公开 API，前端不存在独立问题清单。
3. 推荐问题按 `questionPresetId` 请求。
4. 自由输入可通过 canonical 或 alias 解析。
5. 未支持问题返回 `BOUNDARY` 且不显示 Verified。
6. 无 Evidence 的回答不能标记为 `VERIFIED`。
7. 无效 project 或 Evidence 返回明确状态，不回退第一项。
8. 问题原文不进入 URL、浏览器持久化存储、服务端存储或日志。
9. 刷新后临时会话消失，并显示隐私说明。
10. 首页只显示摘要，Agent 保留结构化 section。
11. 新旧请求不会互相覆盖，迟到响应被丢弃。
12. 空项目、空 Evidence、handoff 失效均有明确页面状态。
13. reduced-motion、键盘焦点和动态回答播报正常。
14. 桌面和移动端均无水平溢出。
15. 打包后的 JAR 使用真实 API 完成同一套核心链路。
16. 单次请求只获取一次 `RuntimeContentSnapshot`，整个轮次使用同一个 contentVersion。
17. 活跃内容切换不改变进行中或已完成轮次；切换后的新轮次才读取新版本。
18. 观察者抛错、队列满或不可用时，返回给访客的响应内容、可信状态和 HTTP 状态完全不变。
19. 前端按轮次原样保存结构化回答，不把 sections 或 Evidence 压平成消息字符串。
20. 首页 handoff 传递已经完成的完整结构化轮次；Agent 消费后不重复请求答案。
21. A 阶段不存在访客会话持久化适配器、通用 Hook、模型/工具注册表或插件加载器。

## 14. 与后续设计的边界

B 阶段负责：

- Claim 与 Evidence 的细粒度关系；
- Case Registry、Benchmark Registry 和评测集；
- 内容候选、隐私审核、人工批准、版本发布与回滚；
- Feedback Bus 和受控学习闭环。

C 阶段负责：

- 真正的身份化回答；
- 多轮上下文；
- 模型、RAG 和工具调用；
- 模型输出验证与 deterministic fallback；
- 是否需要多 Agent 编排。

A 阶段不得以未来扩展为理由提前接入上述能力。

### 14.1 通用 Hook 的实现阶段

- C1 接入模型表达时使用显式的模型端口和验证端口，不建设通用 Hook。
- C2 接入检索、RAG 与工具时使用显式 Pipeline，每一步都有固定输入、输出和策略边界，仍不建设运行时通用 Hook。
- 只有进入 C3，且已经出现至少两类真实 Provider、工具或独立消费者，固定 Pipeline 产生重复扩展代码时，才评估通用 Hook。
- 若 C3 确需 Hook，必须区分只读观测 Hook 与受控执行 Hook，并为每个事件定义类型化载荷、超时、失败策略和顺序。
- 任何 Hook 都不能授予 `VERIFIED`、绕过 Evidence 校验、替代人工批准或发布、扩大工具权限，也不能访问不在其载荷白名单内的数据。

## 15. 实施记录（2026-07-21）

A 阶段已经按本设计实施并通过发布验证：

- 后端已落地 `RuntimeContentSnapshot`、`AnswerTurnSnapshot`、固定运行时编排、四维回答契约和默认 `NoopAnswerDecisionPublisher`；观察发布失败不影响回答。
- 前端会话改为当前页面内存态，首页通过随机、五分钟有效、一次性消费的内存 `handoffId` 进入 Agent；问题和回答不进入 URL 或浏览器持久化存储。
- 首页和 Agent 只展示后端 `QuestionPreset`，并原样保留结构化 sections 与四维状态；`BOUNDARY` 和 `REJECTED` 不再显示为已核验。
- 无效 Evidence 不再回退第一项，Evidence/Timeline 的 project query 已真实过滤；动态播报、键盘调整、抽屉焦点管理、焦点可见性和 reduced-motion 已纳入实现与浏览器验收。
- 隐私扫描范围和发布门禁顺序已经扩大，扫描在公开数据、前端 bundle 和 packaged JAR 风险制品最终形成后执行。

B、C 及 `docs/05-06` 中的内容治理、发布包、模型、检索和多轮能力仍未实施，不属于本次完成范围。
