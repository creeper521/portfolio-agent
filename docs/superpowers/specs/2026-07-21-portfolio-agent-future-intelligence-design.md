# Portfolio Agent 未来智能能力设计

- 状态：C1 与 C2 已实现；C3 中仅 Model Provider Registry 已按准入实现，其余能力待真实准入条件满足后再评估
- 日期：2026-07-21
- 范围：C 阶段——受约束模型表达、公开检索与条件式扩展架构
- 前置设计：`2026-07-20-portfolio-agent-runtime-trust-design.md`
- 前置设计：`2026-07-21-portfolio-agent-content-governance-design.md`

## 0. 实现状态（2026-07-22）

C1 已按本设计实现：运行时从已解析的公开 `QuestionPreset`、Claim/Evidence 投影和 AudienceRole 构建不可变白名单 `AnswerPlan`；每个部署只选择 DeepSeek V4 Flash 或 GLM-4.7 之一，通过固定 `ModelExpressionPort` 做一次非流式结构化表达。模型默认关闭，只有显式启用、所选密钥存在且部署方独立批准外部数据策略时才可调用。原始访客问题、别名、历史、`turnId`、`requestId` 和 `handoffId` 不进入 Provider 请求。

完整 Draft 必须通过 section、Claim/Evidence 引用、治理标签、长度、数字事实和禁止内容校验；任一 Provider、超时、解析或校验失败都会丢弃整个 Draft，并用同一 `AnswerPlan` 产生 `FALLBACK`。`verification` 仍只由核心 `VerificationPolicy` 计算。实现未引入 Spring AI、自动重试、跨 Provider 重发、RAG、通用工具、Hook、Orchestrator、DurableTask 或多 Agent。

C2 已按本设计实现：C2a 固定 `BAAI/bge-small-zh-v1.5` INT8 ONNX、512 维、L2/Cosine，发布期生成文档向量，运行期只在本机生成查询向量；BM25 与向量候选经 RRF 和 Grounding Gate，只有 `SUFFICIENT` 返回 `ANSWERED + RETRIEVAL`。Preset 跳过检索，向量失败只在本次请求内降级关键词。四文件旧 Bundle 兼容，声明 retrieval 的七文件 Bundle 与本地 descriptor/model hash 不匹配时失败关闭。

C2b 使用 `c2b-tools-v1` 封闭策略，在同一个 `RuntimeAnswerContent` 上确定性执行最多四次固定只读工具；未知、跨项目、跨版本、未批准或超预算结果失败关闭。引用式多轮只携带公开版本、Project/Claim/Preset/Section 稳定引用和封闭 `FollowUpIntent`，不携带历史问答正文；每轮重新验证引用，版本变化时使用当前版本并提示，引用失效时返回 `BOUNDARY`。前端上下文只存在当前页面内存。模型仍只接收最终 `AnswerPlan`。C3 中仅 Model Provider Registry 已实现；Tool Registry、Hook、Orchestrator、DurableTask、多 Agent 和持久会话未准入、未实现。

C3 Model Provider Registry 使用不可变 `registrySnapshotVersion=c3-model-registry-v1`，只有 DeepSeek V4 Flash 与 GLM-4.7 两个内建 Descriptor。仍沿用 `PORTFOLIO_AGENT_DEEPSEEK_API_KEY` 和 `PORTFOLIO_AGENT_GLM_API_KEY`，由 `PORTFOLIO_MODEL_PROVIDER` 显式选择单一 Provider；不自动故障转移、不跨 Provider 重发、不动态发现。Registry 不保存凭据、不提供可变 register/remove/replace API，且不记录原始 Provider 请求或响应。

## 1. 背景

A 阶段建立可信的确定性回答、不可变轮次快照、结构化前端和严格隐私会话；B 阶段建立 Claim/Evidence 治理、Benchmark、人工批准和不可变 Bundle。C 阶段只能在这些边界内增加表达、检索、工具和编排能力，不能让模型或扩展成为新的事实权威。

本设计借鉴 Pi 的分层、显式阶段和不可变轮次思想，但不复制面向通用 Coding Agent 的完整 Harness。作品集 Agent 优先采用固定、可验证的轻量 Pipeline。

## 2. 设计目标

1. 模型只表达已批准事实，不能创造或验证事实。
2. 访客原问题、会话和请求身份不发送给外部 Provider。
3. RAG 只负责召回公开候选，Claim 与 ClaimEvidenceLink 继续拥有事实权威。
4. 工具调用有界、只读、类型化，并在模型调用前完成规划。
5. 多轮探索不引入服务端会话持久化。
6. 内容、模型、检索、Registry 和扩展策略分别版本化，每轮使用不可变执行快照。
7. 所有可选智能能力都具有确定性 fallback、Benchmark 和权威 Capability 声明。
8. Registry、Hook、长任务和多 Agent 只在真实需求满足准入条件后建设。

## 3. 非目标

C 第一版不实现：

- 服务端持久会话、刷新恢复或跨设备同步；
- 保存访客问题、Prompt、模型 Draft 或完整工具结果；
- 模型直接调用工具；
- 写文件、访问私人治理目录、操作系统或任意外部网络的工具；
- 多 Provider 自动故障转移；
- 动态插件安装、请求级 Hook 注册或开放式 Agent Loop；
- 模型授予 `VERIFIED`、批准内容或切换 active Bundle；
- 未满足准入条件的 Registry、通用 Hook、DurableTask 或多 Agent。

## 4. 分阶段路线

```text
C1：受约束的模型表达
→ C2：本地公开检索、只读工具和引用式多轮
→ C3：满足真实门槛后的扩展架构
```

- C1 使用显式模型端口，模型只表达不可变 AnswerPlan。
- C2 使用固定 Retrieval/Tool Pipeline，不建设通用 Hook。
- C3 只有在多实现、多消费者或长任务确实存在后，才逐项评估 Registry、Hook、Orchestrator 和 DurableTask。

## 5. 固定运行 Pipeline

```text
RESOLVE
→ RETRIEVE
→ PLAN_TOOLS
→ EXECUTE_TOOLS
→ BUILD_ANSWER_PLAN
→ EXPRESS
→ VALIDATE_OUTPUT
→ VERIFY
→ FINALIZE
→ OBSERVE
```

Preset 命中时可以明确跳过 RETRIEVE；无工具需要时可以跳过 Tool 阶段，但阶段不能动态重排。每个阶段接收不可变类型并返回成功结果或标准化失败，不使用可任意修改的 Context Map。最终响应只能由 FINALIZE 构建，OBSERVE 不能反向修改结果。

## 6. C1：确定性 AnswerPlan

```text
QuestionPreset + AnswerTurnSnapshot
→ AnswerPlanBuilder
→ Immutable AnswerPlan
→ ModelExpressionPort
→ ModelAnswerDraft
→ AnswerOutputValidator
→ VerificationPolicy
→ StructuredAnswer
```

示例：

```json
{
  "contentVersion": "2026-07-21.1",
  "questionPresetId": "sql-audit-full-introduction",
  "audienceRole": "INTERVIEWER",
  "requiredSectionTypes": [
    "BACKGROUND",
    "RESPONSIBILITY",
    "TECHNICAL_APPROACH",
    "VERIFICATION",
    "STATUS"
  ],
  "claims": [
    {
      "claimId": "claim-sql-audit-delivered",
      "statement": "核心版本已完成部署并形成使用文档。",
      "verificationBasis": "EVIDENCE_SUPPORTED",
      "materiality": "KEY",
      "allowedEvidenceIds": ["sql-audit-delivery-set"]
    }
  ],
  "expressionPolicy": {
    "tone": "CONCISE_TECHNICAL",
    "maxSummaryLength": 250,
    "mustLabelSelfDeclared": true,
    "mustLabelInference": true
  }
}
```

模型可以调整表达密度、措辞、section 组织和角色重点，只能返回 Plan 中已有的 Claim/Evidence ID。模型不能新增事实、状态、贡献、数字或 ID，不能改变 Claim category、achievementStatus、verificationBasis、materiality、Evidence 关系、required section 和 verification。

Validator 检查完整 section、引用范围、Link 支持、个人陈述/推断标签、长度、禁止内容和未知字段。任一检查失败时丢弃整个 Draft，用同一 AnswerPlan 生成确定性回答；禁止拼接部分模型结果和部分 fallback。

## 7. C1：Provider 与隐私边界

```text
访客输入
→ 本地 QuestionResolver
→ 公开 AnswerPlan
→ ModelExpressionPort
```

Provider 只接收白名单 AnswerPlan，不接收访客自由输入、会话历史、turnId、requestId、handoffId、IP、User-Agent、Cookie、PrivateSource、审核备注、未批准对象或整个 Bundle。alias 命中后只发送 Preset 的规范公开意图。

```text
ModelExpressionPort
└─ express(ModelExpressionRequest): ModelExpressionResult
```

C1 每次部署只显式配置一个 Provider；Model Provider Registry 仅提供两个内建 Descriptor 的不可变快照 `c3-model-registry-v1`，不保存凭据、不动态发现。单次调用使用明确超时，默认不重试、不跨 Provider 重发；原始请求、响应和 Prompt 不进入普通日志。Provider 不保存应用侧会话，也不使用供应商 conversation/thread ID。第一版采用非流式结构化输出，通过完整验证后一次性返回。不能确认数据保留策略时禁用模型路径。

## 8. C1：受治理的身份化表达

身份化表示“按访问目的调整表达”，不表示识别具体访客。`AudienceProfile` 由 B 的公开 Bundle 治理：

```json
{
  "id": "INTERVIEWER",
  "label": "技术面试官",
  "expressionPolicy": {
    "priorityTopics": [
      "PERSONAL_CONTRIBUTION",
      "TECHNICAL_DECISIONS",
      "VERIFICATION"
    ],
    "summaryStyle": "CONCISE_TECHNICAL",
    "defaultDepth": "MEDIUM",
    "preferredSectionOrder": [
      "BACKGROUND",
      "RESPONSIBILITY",
      "TECHNICAL_APPROACH",
      "VERIFICATION",
      "STATUS"
    ]
  }
}
```

Profile 可以改变顺序、密度、解释深度、强调点、默认展开、建议问题和 CTA，不能改变事实、贡献边界、Claim、Evidence、verification、隐私或能力边界。未知角色规范化为 DEFAULT；客户端不能提交自由 Persona Prompt。角色仍只保存在当前标签页内存。

## 9. C1：降级与 ModelPolicy

| generationMode | 触发条件 |
| --- | --- |
| `DETERMINISTIC` | 本轮按策略未调用模型 |
| `MODEL` | 模型 Draft 完整通过验证并成为最终回答 |
| `FALLBACK` | 已尝试模型但调用或验证失败，最终使用确定性回答 |

Provider 未配置、管理员关闭或问题不适用模型时是 `DETERMINISTIC`。只有模型确实被尝试且失败时才是 `FALLBACK`。模型失败不能映射为 `BOUNDARY`；确定性 fallback 也失败时返回 `FAILED`，不得输出 Draft。

ContentBundle 与 ModelPolicy 独立版本：

```text
ContentBundle: contentVersion + facts + deterministic plan data
ModelPolicy: modelPolicyVersion + adapter + schema + timeout + generation rules
```

ModelConformanceSuite 覆盖全部 active Preset、section、引用、标签、禁止内容、非法 JSON、未知字段、虚构 ID、超时和 fallback。结构、事实、隐私和 fallback 为 BLOCKER；表达质量未达阈值时不启用 MODEL，但不阻止确定性内容发布。ModelPolicy 显式启用和独立回滚。

连续调用失败时可以短暂熔断并直接走 `DETERMINISTIC`；半开探测只使用固定公开输入。C1 不建设通用重试、负载均衡或跨 Provider 故障转移。

## 10. C2：本地混合检索

```text
访客问题
→ 本地规范化
→ QuestionPreset 精确/alias 解析
├─ 命中 → 直接构建 AnswerPlan
└─ 未命中
   → 本地能力范围判断
   → Metadata Filter
   → Keyword Retrieval
   → Local Vector Retrieval
   → ClaimEvidenceLink Expansion
   → Rerank
   → RetrievalContextValidator
   → AnswerPlanBuilder
```

原问题只存在于请求内存，不发送给外部 Embedding Provider，也不记录文本、关键词、hash、向量或相似度。查询向量使用应用内本地模型；不可用时降级关键词。模型表达端仍只接收最终 AnswerPlan。

Metadata Filter 先约束项目、公开状态、角色允许范围、内容版本、Claim 类型和有效期，再做相似度召回。向量只找候选，不能创建 Claim、改变 materiality 或授予 VERIFIED。候选数、最终 Claim、Context 字符数和 Token 均有上限，关键边界 Claim 不得因截断而消失。

## 11. C2：Claim 派生 Chunk 与索引包

```json
{
  "chunkId": "chunk-sql-audit-delivery",
  "contentVersion": "2026-07-21.1",
  "projectSlugs": ["sql-audit"],
  "claimIds": ["claim-sql-audit-delivered"],
  "text": "SQL 审计工具核心版本已完成部署，并形成使用文档。",
  "topics": ["DELIVERY_STATUS", "DOCUMENTATION"],
  "validFrom": "2026-07-10",
  "contentHash": "sha256:..."
}
```

Chunk 必须引用当前 Claim，并由已审核公开字段重新生成。Evidence 通过 ClaimEvidenceLink 派生，不在 Chunk 维护第二套关系。Chunk 不是事实来源；冲突时以 Claim 和 Link 为准。Claim 修改、撤回或过期后重建相关 Chunk 和全部索引。

```text
runtime-bundle/
├─ manifest.json
├─ portfolio.json
├─ presentation.json
├─ rag-documents.jsonl
├─ keyword-index.json
├─ vector-index.bin
└─ checksums.json
```

Manifest 记录 strategyVersion、normalizationVersion、embeddingModelId、dimension、chunkCount、chunkSetHash 和索引格式。索引只由发布工具从 RAG 文档重建；文档向量和查询向量使用同一模型、维度和规范化版本。C2 第一版只有一个本地 EmbeddingPort。任一 ID、hash、维度、模型或数量不一致时拒绝激活完整 Bundle；内容回滚同时回滚 RAG 文档和索引。

## 12. C2：answerSource 与 RetrievalDecision

```text
resolution                         answerSource
├─ ANSWERED                        ├─ PRESET
├─ BOUNDARY                        └─ RETRIEVAL
└─ REJECTED
```

`resolution` 只表达是否形成回答；`answerSource` 只表达事实来源。Preset 命中产生 `ANSWERED + PRESET`。检索内部决策为 `SUFFICIENT / INSUFFICIENT / AMBIGUOUS / CONFLICTING / OUT_OF_SCOPE`；只有 SUFFICIENT 可以进入 AnswerPlan 并产生 `ANSWERED + RETRIEVAL`。`BOUNDARY / REJECTED` 的 `answerSource` 为 `null`。SUFFICIENT 要求相关 KEY Claim、单一 Snapshot、有效状态、无未解决冲突、Evidence 关系完整且 Context 足以保留关键事实。

INSUFFICIENT 返回能力边界和相关 Preset；AMBIGUOUS 请求用户选择；CONFLICTING 返回公开资料待确认说明并产生匿名治理信号；OUT_OF_SCOPE 返回支持问题；安全策略命中为 REJECTED。`ANSWERED + RETRIEVAL` 仍独立计算 generationMode 和 verification，不能直接显示为 Verified。

## 13. C2：只读 ToolPlan

```text
QueryIntent
→ ToolPlanBuilder
→ ToolPolicy
→ PublicKnowledgeTools
→ ToolResultValidator
→ AnswerPlanBuilder
→ ModelExpressionPort
```

第一版工具为 `getProject`、`getClaims`、`getEvidenceForClaims`、`getTimeline`、`searchPublicContent` 和 `compareProjects`。ToolPlan 在执行前完整确定，模型不能新增调用。所有调用共享同一个 Snapshot，参数只接受稳定 ID、枚举和有界过滤条件，不接受路径、URL、命令或任意查询表达式。

工具只返回公开白名单 DTO，不能访问 PrivateSource、治理目录、文件系统、外部网络或写接口；不递归调用工具或触发 Agent。调用数、结果、耗时和 Context 有界。模型只看到最终 AnswerPlan。C2 使用封闭 ToolKind 或显式接口，不建设动态 Tool Registry。

## 14. C2：引用式多轮上下文

多轮继续使用浏览器内存会话和无状态服务端：

```json
{
  "previousContentVersion": "2026-07-21.1",
  "projectSlugs": ["sql-audit"],
  "questionPresetId": "sql-audit-full-introduction",
  "referencedClaimIds": ["claim-sql-audit-delivered"],
  "selectedSectionType": "TECHNICAL_APPROACH",
  "followUpIntent": "EXPAND_SECTION"
}
```

ContextEnvelope 不携带历史问题、回答正文、完整会话、用户身份、Provider thread ID 或客户端验证状态。第一版意图为 EXPAND_SECTION、SHOW_EVIDENCE、EXPLAIN_DECISION、COMPARE_PROJECTS、CURRENT_STATUS 和 RELATED_QUESTION。

每轮重新验证引用并读取一次 active Snapshot。新回答使用当前版本；版本变化时稳定 ID 重新解析并提示更新，删除、语义变化或冲突则返回上下文失效，不回退其他对象。刷新后会话消失，服务端不保存 conversationId，模型不接收聊天记录。

## 15. C2：注入防护

信任顺序为 Runtime Policy > AnswerPlan/ToolPlan > 访客输入与公开文本。访客输入只用于本地解析和检索；Chunk、Claim 和 Evidence 都是引用数据，即使包含伪指令也不能改变策略。

Prompt 用结构化字段区分 Policy、Allowed Facts 和 Output Schema。模型没有工具、文件、网络或系统入口。Provider 返回的 ToolCall、代码执行、URL 获取和额外动作字段均为非法 Draft。Markdown、HTML、控制字符和模板表达式分别执行白名单规范化，前端不得渲染未清洗 HTML。

B 发布门禁加入伪指令、转义、Unicode 隐藏字符、类 ToolCall、外部 URL 和数据泄露诱导测试。无法安全规范化的内容被排除并生成治理 Case。

## 16. C2：Benchmark 与匿名观测

三层 Benchmark：

1. Retrieval：预期 Project/Claim、负例、过期/撤回过滤和 Keyword fallback。
2. Grounding：KEY Claim、Link 展开、冲突、Evidence 降级、Context 截断和 RetrievalDecision。
3. End-to-End：resolution、generationMode、verification、section、引用、fallback 和注入防护。

Retrieval 与 Grounding 关键用例是 Bundle BLOCKER；表达质量属于独立 ModelPolicy 门禁。索引损坏或不兼容拒绝激活；运行时本地向量失败可标记 `KEYWORD_FALLBACK`，关键词也失败时不能让模型猜测。

观测只记录 contentVersion、策略版本、retrievalMode、resolution、RetrievalDecision、候选/Claim 数量桶、延迟桶和标准化 fallbackReason。禁止记录问题、关键词、向量、相似度、完整候选排序、Chunk、ContextEnvelope、AnswerPlan、Prompt 或 Draft。

## 17. C3：扩展能力准入

| 能力 | 必要条件 |
| --- | --- |
| Model Provider Registry | 至少两个真实 Provider，且需要部署时选择或故障隔离 |
| Tool Registry | 多组独立 Tool Provider，封闭 ToolKind 已产生重复注册与权限代码 |
| 通用 Hook | 至少两个独立消费者需要同一稳定事件，显式端口开始重复 |
| 长任务状态机 | 真实任务不能在单请求完成，且需要暂停和恢复 |
| 多 Agent | 单 Pipeline 无法清晰承载多个独立目标、权限或并行单元 |
| 持久会话 | 产品明确需要恢复，并重新批准隐私模型 |

每项还必须具备运行证据、稳定类型化契约、失败/超时/取消语义、可审计权限和 Benchmark，并形成 ADR 说明为什么固定 Pipeline 已不足。不满足条件时继续使用显式端口和单 Pipeline，不能为未来想象预建空框架。

## 18. C3：类型化 Hook

只读观测 Hook：

```text
ObservationHook<AnswerDecidedEvent>
ObservationHook<RetrievalDecidedEvent>
ObservationHook<ToolCompletedEvent>
ObservationHook<ModelAttemptCompletedEvent>
```

它只接收白名单不可变事件，无返回值，不能接收 Request、Response、Prompt、完整 Context 或私人数据。失败、超时和队列满不改变结果，不无限重试或递归触发。

受控执行 Hook 只能在固定点进行有限变换：

- CandidateRerankHook 只能重排已授权候选，不能新增 Claim。
- ToolAuthorizationHook 只能拒绝或收紧，不能授予新权限。
- ToolResultFilterHook 只能删除或脱敏，不能创造结果。
- ExpressionPolicyHook 只能收紧表达规则，不能改变事实。

禁止万能 `eventName + Map`。Hook 不能授予 VERIFIED、添加 Snapshot 外对象、绕过 Gate、批准/发布/回滚、扩大权限或读取问题与会话。注册来自受审配置，顺序固定且版本化；每个 Hook 有超时和结果限制。隐私/权限 Hook 失败时 fail-closed，可选排序/表达 Hook 失败时回到无 Hook 行为，输出仍经过核心 Validator。

## 19. C3：Registry

### 19.1 已实施的 Model Provider Registry

Model Provider Registry 以 2026-07-22 的 C3 ADR 为准。第一版只校验和选择两个受审的内建实现，不负责动态发现：

```text
DeepSeek V4 Flash / GLM-4.7 内建 Descriptor
→ Descriptor 完整性校验
→ ID / Version Collision Check
→ ModelPolicy / Answer Schema / Capability Compatibility Check
→ 单 Provider 显式绑定
→ Immutable ModelProviderRegistrySnapshot (c3-model-registry-v1)
```

`ModelProviderDescriptor` 声明 `providerId`、`adapterVersion`、内建 HTTPS `endpoint` 常量、`modelName`、capabilities，以及支持的 ModelPolicy 与 Answer Schema 版本。endpoint 必须进入内建 Descriptor，不接受请求、公开 Bundle、目录、网络或配置中心覆盖。密钥与私人配置不进入 Descriptor、Registry Snapshot 或公开 Bundle，只在配置边界为已选 Provider 注入 Adapter。

同 ID 异实现或版本冲突时启动失败，不能 first-wins。ModelPolicy 只显式绑定一个兼容 Provider；未知或不兼容选择 fail-closed，不选择替代项。Registry Snapshot 构造后不可变，不提供 `register/remove/replace`，不扫描 classpath、文件、网络或插件，也不支持热更新。Descriptor、版本或兼容性变更必须形成新的受审常量并通过重新部署生效；每轮只固定 `registrySnapshotVersion`。即使存在两个 Provider，也不自动重试、故障转移或跨 Provider 重发。

### 19.2 尚未准入的通用/Tool Registry

通用 Extension Registry 与 Tool Registry 仍是未来设想，当前未准入、未实现，也不能覆盖 19.1 或 C3 ADR 的已实施约束。只有第 17 节的真实多实现、重复权限/注册代码、稳定类型化契约、失败语义、运行证据和 Benchmark 条件全部满足后，才可另立 ADR 评估 Tool Descriptor、权限声明、版本冲突和更新策略。在此之前，C2 继续使用封闭 `ToolKind`、固定 `ToolPlan` 和显式端口；不得预建动态发现、动态安装、可变注册或热更新框架。

## 20. C3：多 Agent 与 DurableTask

普通访客回答保持单 Pipeline。多 Agent 只用于具有两个以上独立子目标、不同工具或权限、真实并行/长任务需求且结果可确定性验证的任务：

```text
TaskRequest
→ Orchestrator
   ├─ RetrievalWorker
   ├─ EvidenceAnalysisWorker
   └─ DraftWorker
→ Deterministic Aggregator
→ VerificationPolicy
→ Human Gate（需要时）
```

Orchestrator 拥有目标、预算、取消状态和任务快照；Worker 使用类型化 Envelope/Result，不自由聊天或共享可变内存，权限只能是父任务子集。Worker 不能批准 Claim、授予 Verified、发布 Bundle 或修改 active；最终聚合器是确定性组件。所有 Worker 共享同一 Content、ModelPolicy、Registry 和 ExtensionPolicy 版本快照，取消后拒绝迟到结果。

第一版 C 不实现 DurableTask。默认 EphemeralConversation 仍在浏览器内存、刷新即失效。未来若确需恢复任务，必须作为独立隐私能力重新审批，明确用户确认、最小任务状态、TTL、删除、幂等恢复、权限快照和迟到结果规则；不得默认保存问题、Prompt、Draft、会话、PrivateSource 或身份信息。

## 21. AgentExecutionSnapshot

每轮开始时一次性创建：

```json
{
  "turnId": "memory-only-id",
  "requestId": "random-id",
  "contentVersion": "2026-07-21.1",
  "runtimeBundleHash": "sha256:...",
  "audienceProfileVersion": "1.0.0",
  "modelPolicyVersion": "1.0.0",
  "retrievalPolicyVersion": "hybrid-v1",
  "registrySnapshotVersion": "c3-model-registry-v1",
  "extensionPolicyVersion": "none",
  "budgets": {
    "totalDeadlineMs": 5000,
    "maxToolCalls": 4,
    "maxRetrievedClaims": 8,
    "maxContextTokens": 4000,
    "maxModelAttempts": 1
  }
}
```

`AgentExecutionSnapshot` 组合 A 的 `AnswerTurnSnapshot`，沿用其 `turnId`、`requestId`、内容版本和 `runtimeBundleHash`，再增加本轮 Policy、Capability 与预算；它不重新加载或复制另一份 active 内容。创建前校验 ModelPolicy/Answer Schema、RetrievalPolicy/索引、Registry 绑定、AudienceProfile/Bundle 和 ExtensionPolicy/Pipeline 的兼容性。任一不兼容在执行前失败或禁用可选能力，不能中途混用版本。热更新只影响下一轮。

跨阶段 Snapshot 对应关系固定为：`RuntimeContentSnapshot`（active 内容）→ `AnswerTurnSnapshot`（A 单轮投影）→ `AgentExecutionSnapshot`（C 单轮策略组合）；`GovernanceRunSnapshot` 是 B 控制面的独立治理运行输入，不得进入访客执行链路。

## 22. 失败、预算与取消

内部失败使用类型化 AgentFailure，包含 stage、标准 code、category、retryable、safeMessageKey 和请求内 causeId，不包含原始内容。主要映射：

- 检索不足是 BOUNDARY，不是系统失败。
- 冲突返回内容边界并产生治理信号。
- 模型失败且确定性成功是 FALLBACK。
- 向量失败且关键词成功是 KEYWORD_FALLBACK。
- 可选工具失败可以返回部分能力说明；关键工具失败则降级或 FAILED。
- Snapshot/Policy 不兼容不能混用版本。
- 最终验证失败且无 fallback 时 FAILED。
- 隐私或权限命中是 REJECTED。
- 用户取消是 CANCELLED，迟到结果丢弃。

总 Deadline 从进入 Runtime 开始；各阶段共享剩余预算。预算不足时优先保证确定性验证和 Finalize，不能因模型或工具耗时跳过 VerificationPolicy。达到调用、Token、候选或 Context 上限时返回标准化结果，不静默截断 KEY Claim。

取消信号从页面、路由或轮次替代传播到 Adapter；未开始阶段停止，正在调用尽力取消，结束后不接受迟到结果。取消不触发重试或 DurableTask。

## 23. 模块边界

```text
answer
├─ domain       AnswerPlan / StructuredAnswer / RetrievalDecision / VerificationResult
├─ application  PortfolioAgentRuntime / PlanBuilder / Validator / VerificationPolicy
├─ port         PublicKnowledge / ModelExpression / Retrieval / PublicTool / Publisher
└─ adapter      model / retrieval / tool / observability
```

Portfolio 拥有 Project、Claim、Evidence、Link 和 Snapshot，通过 Adapter 投影为 Answer 自有只读类型；Answer 不直接依赖 Portfolio Domain。ModelExpressionPort 属于 Answer，因为表达边界是业务规则。Provider SDK、向量库和 HTTP DTO 只存在于 Adapter。治理 CLI/Skill 构建 Bundle，不进入公开运行时依赖图。common 不放 Claim、Prompt、Retrieval 或 Agent 业务对象。C3 抽象只有通过准入后才新增独立包。

## 24. Capability 权威声明与启用

```json
{
  "contentVersion": "2026-07-21.1",
  "capabilities": {
    "presetAnswers": {"enabled": true},
    "modelExpression": {
      "enabled": true,
      "modelPolicyVersion": "1.0.0"
    },
    "groundedQuestions": {"enabled": false},
    "readOnlyTools": {"enabled": false},
    "multiTurnReferences": {"enabled": false},
    "durableTasks": {"enabled": false},
    "multiAgent": {"enabled": false}
  }
}
```

Capability 只有依赖、Policy、Benchmark 和 Adapter 全部就绪后才启用。前端只展示 enabled 能力，不显示未来假入口。ContentBundle 是核心 readiness 条件；可选 Provider 不健康不能让整个应用失去 readiness。未声明 retrieval 的 B 第一版 Bundle 对应 `groundedQuestions=false`；声明了 retrieval 却存在索引不兼容的 Bundle 必须拒绝激活并保留旧 active。已经验证的索引在运行期遇到本地查询 Adapter 故障时，Preset 仍可服务，并可在下一份 Capability Snapshot 中将 `groundedQuestions` 暂时关闭。Capability Snapshot 与执行快照一起固定。

启用顺序：C1 默认关闭模型，ConformanceSuite 通过后小范围启用；C2 先关键词、再本地向量、再 `ANSWERED + RETRIEVAL`、最后引用式多轮和只读 ToolPlan；C3 默认不实施，准入条件满足并通过 ADR 后逐项建设。

## 25. 验收标准

### 25.1 C1

1. 模型只能接收白名单 AnswerPlan，测试证明原问题和会话不会进入 Provider 请求。
2. 模型不能引用 Plan 外 Claim/Evidence，未知字段和非法结构被拒绝。
3. 任一 Draft 验证失败时整轮使用同一 Plan 的确定性 fallback。
4. generationMode 的 DETERMINISTIC、MODEL、FALLBACK 触发语义准确。
5. verification 只由核心 Policy 计算，模型和 Profile 无法修改。
6. ContentBundle 与 ModelPolicy 独立发布和回滚。

### 25.2 C2

7. Preset 命中优先且不经过 RAG。
8. 原问题、关键词和查询向量不离开服务端或进入日志。
9. Chunk 全部可追踪到当前 Claim，Evidence 只从 Link 派生。
10. 文档与查询 Embedding 的模型、维度和规范化版本一致。
11. 索引不一致拒绝激活整个 Bundle；运行时向量失败可靠降级关键词。
12. 只有 RetrievalDecision=SUFFICIENT 才产生 `ANSWERED + RETRIEVAL`。
13. `ANSWERED + RETRIEVAL` 不自动等于 VERIFIED。
14. ToolPlan 在模型前固定，工具只读、有界且共享同一 Snapshot。
15. ContextEnvelope 不包含历史正文，刷新后多轮上下文消失。
16. 注入内容无法改变 ToolPlan、AnswerPlan、权限、verification 或 Schema。
17. Retrieval、Grounding 和端到端关键 Benchmark 全部通过。

### 25.3 C3 与运行可靠性

18. Registry、Hook、Orchestrator 和 DurableTask 未通过准入与 ADR 时不存在空实现框架。
19. Hook 使用类型化载荷，不能扩大权限、添加事实或绕过 Gate。
20. Registry 冲突 fail-closed，策略显式绑定实现。
21. 普通访客问答保持单 Pipeline，多 Agent 不能批准或发布。
22. 每轮固定 Content、Model、Retrieval、Registry 和 Extension 版本。
23. 总预算、取消和迟到结果规则可测试。
24. OBSERVE、观测 Hook 和遥测失败不改变最终响应。
25. 前端只展示 Capability Snapshot 中 enabled 的能力。
26. C 第一版没有服务端会话持久化或 DurableTask。

## 26. 与 A、B 的边界

A 继续拥有严格隐私会话、结构化回答、四个可信维度、前端轮次和基础 Runtime；C 通过组合 A 的 AnswerTurnSnapshot 形成 AgentExecutionSnapshot，但不改变刷新即失效和服务端无状态原则。

B 继续拥有 Claim、Evidence、ClaimEvidenceLink、QuestionPreset、AudienceProfile、Benchmark、Bundle 和人工发布权；C 的模型、检索、工具、Hook 和 Worker 只能消费 B 已发布对象，不能写治理状态或切换 active。

C1/C2 使用显式端口和固定 Pipeline。通用 Hook、Registry、多 Agent 和持久任务只属于满足准入条件后的 C3，不能反向污染 A/B 第一版实现。
