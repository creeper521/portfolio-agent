# Agent P4：基于已验证证据的受约束模型表达设计

> 日期：2026-08-13
> 状态：用户已批准；后端生产链与 Mock 门禁已实施，默认关闭；真实 Provider 未授权、未验收
> 适用范围：P4.1 单主体 Fact、P4.2 Comparison、P4.3 Recommendation/Refine 的统一设计
> 上游：P0 统一 Eval、P1 确定性回答编排、P2 统一语义路由、P3 受限执行与证据晋升
> 后端入口：`POST /api/v2/answers` 的 Portfolio 任务表达阶段
> 前端交接：`../../handoffs/2026-08-13-agent-p4-frontend-contract-handoff.md`

## 0. 结论

P4 在 P3 已完成的证据执行链之后建立一个深表达模块。模型只对已经通过 P3 Result Policy 的强类型 `PortfolioAnswerMaterial` 进行受控编辑，不重新读取问题、不重新理解意图、不重新检索、不重新判断支持度，也不能改变比较关系、推荐候选、排序、Caveat、Resolution 或 EvidenceState。

唯一主链为：

```text
P2 SemanticTask
→ P3 Planner / Capability / Evidence Promotion / Support / Result Policy
→ PortfolioAnswerMaterial
├─ → GroundedAnswerContribution → P2 Synthesis（永远使用模型前事实）
└─ → PortfolioAnswerComposition
     ├─ 先生成完整 Deterministic Fallback Plan
     ├─ Eligibility / Allowance
     ├─ ModelExpressionInputProjection
     ├─ PortfolioExpressionPort
     ├─ Strict Draft Codec
     ├─ StatementGroundingValidator
     └─ Model Draft Plan Assembler
→ PortfolioCompositionResult
→ TaskResultPayload / HTTP / 前端
```

已确认的产品选择：

1. P4 的正式设计覆盖 Fact、Comparison、Recommendation/Refine，但按 P4.1、P4.2、P4.3 分阶段准入。
2. P4.1 只启用单主体 `PORTFOLIO_FACT`；Comparison、Recommendation 与 Refine 继续使用确定性表达。
3. P4 是 `PortfolioAnswerMaterial → PortfolioCompositionResult` 的深模块；P3 executor 不感知 Provider、Schema、Validator 与 fallback 细节。
4. 模型是受控编辑器：可合并、压缩和省略 `OPTIONAL`，不可省略 `REQUIRED`，不可改变业务结论。
5. Provider 不接收原问题、`questionSpan`、`goalLabel`、对话历史或 Context；只接收强类型 `ExpressionIntent` 与最小白名单事实投影。
6. Caveat、缺口、部分覆盖、比较不可用和推荐准入失败全部由核心代码使用受控文案表达，模型无权改写。
7. 确定性 Plan 必须在任何 Provider 调用前成功构造；模型调用或校验失败时整轮使用该 Plan，不混合模型片段和 fallback。
8. 模型输出中的引用只使用请求内临时 Statement Alias；最终 `PublicSourceReference` 由服务端反向派生，模型不能创建公开引用。
9. P4 v1 不使用第二个模型充当 Judge，不使用 Embedding、联网搜索或模型自我修复；Validator 使用严格结构、作用域和高风险语义原子校验，无法确定时失败关闭。
10. 前端不展示“AI 增强”徽标、不展示 Provider、不展示 fallback 原因，也不伪造模型执行进度；模型回答与确定性回答共用相同章节和证据 UI。

### 0.1 决策总表

| 编号 | 必须明确的问题 | 采用方案 | 不采用的主要替代方案 |
|---:|---|---|---|
| 1 | 首版任务范围 | 统一设计三类 Material，按 Fact→Comparison→Recommendation/Refine 分阶段准入 | Fact 专用架构；四类任务同时上线 |
| 2 | P4 seam | `PortfolioAnswerMaterial → PortfolioCompositionResult` 深模块 | P3 executor 直接编排；Plan 后文本润色 |
| 3 | 模型权力 | 受控编辑器：REQUIRED 必达、OPTIONAL 可省略、CONTEXT 不独立结论 | 只换措辞；让模型重新规划事实与章节 |
| 4 | 用户输入 | 只发送闭集 ExpressionIntent，不发送问题、goalLabel、questionSpan 或历史 | 发送当前问题；发送完整对话 |
| 5 | Material | Fact/Comparison/Recommendation 强类型变体 | `kind + List<String>` 扁平材料 |
| 6 | 引用 | 模型返回 Statement Alias，服务端派生 PublicSourceReference | 模型直接返回 reference key/route |
| 7 | Draft | 三种严格封闭 JSON 变体 | 任意 Markdown；统一自由文本 blocks |
| 8 | Validator | 结构＋Alias＋作用域＋高风险语义原子＋专项规则 | 只校验引用存在；第二 LLM Judge |
| 9 | Caveat | 服务端固定表达，模型不可见正文且不可改写 | 让模型润色或自行补缺口 |
| 10 | fallback | Provider 前预构造完整确定性 Plan，失败整轮回退 | 失败后临时重跑；拼接部分 Draft |
| 11 | 未尝试语义 | 正常确定性、非 degraded | 把配置关闭/预算不足标为 fallback |
| 12 | 模型失败语义 | `FALLBACK + EVIDENCE_COMPOSITION + degraded`，不改变证据/Resolution | 映射 NOT_SUPPORTED 或任务 FAILED |
| 13 | 多任务 | P4.1 每回合一个 attempt，task-level 权威，顶层可 MIXED | 每个任务无限调用；把模式压成单一值 |
| 14 | Provider | 单一启动时 Provider、一次非流式调用、无重试/跨 Provider | 动态选择、自动重试、Provider failover |
| 15 | 熔断 | 进程内 3 次失败/30 秒/单 Half-open | 每次都等 timeout；持久化分布式熔断 |
| 16 | 时间预算 | 共享 10 秒执行 deadline，最小窗口 1500ms，Provider 最多 4 秒 | 在 P3 后追加独立 8 秒 |
| 17 | 配置 | 默认关闭；启用但审批/密钥/兼容性缺失则生产启动失败 | 静默关闭；请求级覆盖配置 |
| 18 | 前端 | 同一内容与证据 UI，无 AI 徽标、无模型伪进度、fallback 无错误提示 | 根据模型状态切换两套体验 |
| 19 | Eval | Mock/隐私/对抗为 CI，真实 Provider 单独 INCOMPLETE/PASS | 用 Mock 代替真实验收；持久化正文 |
| 20 | 迁移 | 深化现有 P1/P3 seam，原子切换并删除旧决策岛 | 长期双链、旧 generate/review 包装 P4 |

## 1. 既有边界与 P4 权力范围

### 1.1 必须保持的不变量

- P2 是唯一语义决策权威；P4 不读取原问题重新判断任务、主体、Facet、Dimension 或 requested output。
- P3 是唯一证据准入、支持度、比较关系和推荐排序权威。
- 只有公开、已发布、VERIFIED Claim 与 APPROVED Evidence 能进入 P4 上游材料。
- P4 不接收 CandidateSet、Chunk、向量、检索分数、SQL Row、内部 Claim/Evidence ID 或 Content Store 对象。
- P4 不改变 `TaskResolution`、`TaskEvidenceState`、`AnswerCoverage`、推荐候选集合、RecommendationTier、候选顺序或比较关系。
- P4 不把缺少证据解释为负面能力事实。
- P4 不记录问题、Prompt、模型输入正文、模型输出正文、Statement Alias、公开 reference key、主体 ID 或 Provider 原始错误。
- Java 生产与测试代码继续禁止 `record`、`var` 和 Lombok；值对象使用显式不可变类。
- 普通 CI 不调用真实 Provider；真实 Provider 继续由显式授权门禁控制。

### 1.2 模型可以做什么

- 在受控章节内改写句式。
- 合并由 1–4 个 Statement 共同支持的句子。
- 使用受控连接词改善连贯性。
- 在 `OPTIONAL` 范围内压缩信息。
- 按闭集 `AudienceRole` 与 `ResponseDepth` 调整说明密度和强调点。
- 在服务端允许的章节内部顺序范围内调整句子顺序。

### 1.3 模型不能做什么

- 新增、推断或强化事实、数字、时间、版本、状态、贡献、因果、优劣或生产效果。
- 删除 `REQUIRED` Statement。
- 把 `CONTEXT` Statement 独立写成核心结论。
- 创建章节类型或章节标题。
- 改写 Caveat、omitted topic 或缺口文案。
- 输出 Resolution、EvidenceState、ConstructionMode、degraded、public route 或 reference key。
- 修改 Comparison dimension、relation、subject cell 或缺失状态。
- 修改 Recommendation candidate、tier、criterion、item order 或 result count。
- 请求更多材料、调用工具、重试、选择 Provider 或修改预算。

## 2. 分阶段准入

### 2.1 P4.1：单主体 Fact

允许模型表达：

- `PORTFOLIO_FACT`；
- 恰好一个授权主体；
- P3 Result Policy 已生成至少一个可展示 Statement；
- `SUFFICIENT` 或仍有可展示事实的 `PARTIAL`；
- Overview 或 Focused；
- 普通自由表达、显式结构化引用追问和合法连续 Fact。

保持确定性：

- 正式 Preset Contract；
- 多主体 Fact；
- Comparison；
- Recommendation 与 Refine；
- General 与 Synthesis；
- 纯 Boundary、无可展示 Statement、PRESENTATION_BLOCKED、NOT_SUPPORTED、REJECTED 或 FAILED；
- 本轮未获得 Expression Allowance 的任务。

### 2.2 P4.2：Comparison

只有以下内容全部实现并通过专项门禁后才可把 build-supported kind 扩展为 `COMPARISON`：

- `ComparisonAnswerMaterial` 强类型结构；
- Comparison Draft Codec；
- dimension/subject cell/controlled relation Validator；
- 非对称证据、不可比维度、数字单位和缺失 cell 对抗集；
- Mock、隐私捕获和真实 Provider 验收。

未通过时配置不得启用 Comparison；将 unsupported kind 写入配置必须启动失败。

### 2.3 P4.3：Recommendation 与 Refine

只有以下内容全部实现并通过专项门禁后才可扩展为 `RECOMMENDATION`：

- `RecommendationAnswerMaterial` 强类型结构；
- Candidate/Tier/Criterion 固定投影；
- Recommendation Draft Codec；
- candidate identity/order/criterion scope Validator；
- 排序改变、证据数量偏见、缺证据负面化、Refine 越权对抗集；
- Mock、隐私捕获和真实 Provider 验收。

Refine 与 Recommendation 共用表达 Material 和 Draft，但 `ExpressionIntent.taskKind` 保留两者差异。模型不能继承或解释上一轮答案正文。

## 3. 深模块与唯一 seam

P4 对 P3 暴露一个模块接口。该接口可以由一个 `final` 类提供，不为只有一个生产实现的模块建立额外 Java interface：

```java
public final class PortfolioAnswerComposition {
    public PortfolioCompositionResult compose(
            PortfolioAnswerMaterial material,
            PortfolioCompositionContext context);
}
```

调用方只需要理解：

- 输入必须是已验证的强类型 Material；
- Context 只含表达意图和不可变 allowance；
- 方法总是优先保证确定性可用性；
- 成功返回最终 Plan 与表达状态；
- 只有确定性 Plan 自身无法构造时才抛出/返回 presentation failure。

模块内部实现：

```text
PortfolioAnswerComposition
├─ DeterministicPortfolioAnswerComposer
├─ ModelExpressionEligibilityPolicy
├─ ModelExpressionInputProjector
├─ PortfolioExpressionPort
├─ ModelExpressionDraftCodec
├─ StatementGroundingValidator
├─ ModelDraftPlanAssembler
├─ ExpressionCircuitBreaker
└─ ExpressionDiagnostics
```

`PortfolioExpressionPort` 是真实外部依赖 seam：

```java
public interface PortfolioExpressionPort {
    ModelExpressionResult express(
            ModelExpressionRequest request,
            ModelExpressionDeadline deadline);
}
```

生产使用 OpenAI-compatible Adapter；测试使用显式 Fake/Mock Adapter。Provider 传输、Prompt、JSON 响应和错误映射位于 Adapter 内，不泄露到 P3。

旧 `PortfolioAnswerComposer` 在迁移完成后删除或收缩为模块内部确定性实现，不保留 P3 可选择的“确定性 Adapter/模型 Adapter”注册表。

## 4. 输入领域模型

### 4.1 `PortfolioCompositionContext`

```text
PortfolioCompositionContext
├─ ExpressionIntent expressionIntent
└─ ExpressionAllowance expressionAllowance
```

它不携带配置快照、Provider、Prompt、问题、对话、Context Token 或 Request DTO。

### 4.2 `ExpressionIntent`

`ExpressionIntent` 由强类型 SemanticTask 确定性投影，不能由客户端直接提交：

```text
ExpressionIntent
├─ taskKind: FACT | COMPARISON | RECOMMENDATION | REFINE_RECOMMENDATION
├─ focusMode: OVERVIEW | FOCUSED
├─ requestedFacets[]
├─ requestedDimensions[]
├─ requestedOutputs[]
├─ audienceRole
├─ responseDepth: BRIEF | MEDIUM | DETAILED
├─ locale: zh-CN
├─ taskSource: FREE_TEXT | STRUCTURED_REFERENCE | CONTINUATION | PRESET
└─ subjectDisplayLabels[]
```

约束：

- 所有值均来自闭集或已发布公开 label。
- 不包含 `questionSpan`、`goalLabel`、原问题、消息、对话摘要或任意自由文本指令。
- P4.1 只接受 `locale=zh-CN`；其他 locale 不尝试模型，确定性回答保持现有行为。
- `taskSource=PRESET` 在 P4.1 明确不适用模型。
- Subject Display Label 可发送；内部 Subject ID 与 route 不发送。

### 4.3 `ExpressionAllowance`

```text
ExpressionAllowance
├─ attemptAllowed
├─ absoluteDeadline
├─ characterLimit
├─ statementLimit
└─ requestLocalAttemptOrdinal
```

Allowance 由回合 Coordinator 按稳定拓扑分配，P4 不借用其他任务额度、不延长 deadline。P4.1 每回合最多一个 Provider expression attempt；第一个满足静态任务条件的 Portfolio Fact 获得额度，其余任务确定性表达。

`requestLocalAttemptOrdinal` 只用于内存内稳定 alias 和测试，不进入日志或 Provider 输入。

## 5. 强类型 `PortfolioAnswerMaterial`

`PortfolioAnswerMaterial` 演进为封闭强类型层次，不再使用 `kind + 扁平 statements` 让调用方自行解释：

```text
PortfolioAnswerMaterial
├─ FactAnswerMaterial
├─ ComparisonAnswerMaterial
└─ RecommendationAnswerMaterial
```

公共只读能力：

```text
materialKind
publicTitle
fixedCaveats
omittedTopicLabels
toGroundedContribution()
```

`toGroundedContribution()` 永远返回模型表达前的事实贡献，供 P2 Synthesis 使用。

### 5.1 `GroundedStatement`

```text
GroundedStatement
├─ statementType
├─ subjectReferences[]
├─ controlledPredicate
├─ publicStatement
├─ publicDetail
├─ claimCategory
├─ achievementStatus
├─ contributionType
├─ verificationBasis
├─ materiality
├─ supportTarget
└─ publicSourceReferences[]
```

不变量：

- 每条 Statement 至少一个公开来源。
- Subject、SupportTarget 与来源归属一致。
- 不保留数据库主键、Chunk ID、检索分数或私有对象。
- `publicStatement/publicDetail` 只能来自已批准 Claim 投影。
- 所有枚举非空；未知枚举失败关闭。
- `toString()` 只输出类型和计数。

### 5.2 表达角色

Material 不直接把所有 GroundedStatement 同等交给模型，而使用：

```text
ExpressionStatement
├─ GroundedStatement statement
├─ presentationRole: REQUIRED | OPTIONAL | CONTEXT
├─ allowedSection
└─ stableOrder
```

规则：

- `REQUIRED` 必须在 Draft 正文中覆盖；Summary 不计入覆盖。
- `OPTIONAL` 可在预算内省略。
- `CONTEXT` 只能与 REQUIRED/OPTIONAL 共同支持一句话，不能单独形成事实结论。
- Result Policy/P1 决定角色；模型不能返回或改变角色。

### 5.3 `FactAnswerMaterial`

```text
FactAnswerMaterial
├─ subject
├─ focusMode
├─ sections[]
│  ├─ sectionType
│  ├─ statementEntries[]
│  └─ orderingPolicy
├─ summaryPolicy: REQUIRED | FORBIDDEN
├─ fixedCaveats[]
└─ omittedTopicLabels[]
```

Overview 的 Summary 为 REQUIRED；Focused 为 FORBIDDEN。章节类型和顺序继续遵循 P1 权威规则。Boundary 不交给模型。

### 5.4 `ComparisonAnswerMaterial`

```text
ComparisonAnswerMaterial
├─ orderedSubjects[]
├─ orderedDimensions[]
│  ├─ dimensionKey
│  ├─ subjectCells[]
│  │  ├─ subjectReference
│  │  ├─ coverageState
│  │  └─ statementEntries[]
│  └─ controlledRelation
├─ fixedCaveats[]
└─ omittedTopicLabels[]
```

模型不能从 Cell 文本、Evidence 数量或 Statement 数量推导关系。`controlledRelation` 只能由 P3 产生。

### 5.5 `RecommendationAnswerMaterial`

```text
RecommendationAnswerMaterial
├─ orderedCandidates[]
│  ├─ candidateReference
│  ├─ recommendationTier
│  ├─ orderedCriteria[]
│  │  ├─ criterionKey
│  │  └─ statementEntries[]
│  └─ fixedItemCaveats[]
├─ fixedGlobalCaveats[]
├─ omittedTopicLabels[]
└─ refineSource: NONE | REFINED
```

候选集合、顺序、Tier、Criterion 与 result count 固定。模型只表达每个 item 已有的 grounded reasons。

## 6. 最小 Provider 输入

### 6.1 输入投影

Provider 输入使用独立版本：

```text
portfolio-expression-input.v1
```

Fact 示例：

```json
{
  "schemaVersion": "portfolio-expression-input.v1",
  "materialKind": "FACT",
  "intent": {
    "focusMode": "FOCUSED",
    "requestedFacets": ["VERIFICATION", "OUTCOME"],
    "requestedOutputs": ["DIRECT_ANSWER", "EVIDENCE_REFERENCES"],
    "audienceRole": "INTERVIEWER",
    "responseDepth": "MEDIUM",
    "locale": "zh-CN"
  },
  "shape": {
    "summaryPolicy": "FORBIDDEN",
    "allowedSections": ["VERIFICATION", "STATUS"],
    "requiredSections": ["VERIFICATION"],
    "maxCharacters": 1800,
    "fixedBoundaryPresent": false
  },
  "subjects": [
    {"key": "P01", "label": "SQL 审计平台"}
  ],
  "statements": [
    {
      "key": "S001",
      "role": "REQUIRED",
      "section": "VERIFICATION",
      "subjectKey": "P01",
      "predicate": "VERIFIED_BY_TEST",
      "statement": "……",
      "detail": "……",
      "achievementStatus": "DELIVERED",
      "contributionType": "COLLABORATIVE",
      "verificationBasis": "EVIDENCE_SUPPORTED",
      "materiality": "KEY"
    }
  ]
}
```

Alias 规则：

- 按 Material 权威顺序生成 `P01...`、`S001...`、`D01...`、`C01...`。
- Alias 仅在本次调用内有效，不使用数据库 ID、hash 或公开 reference key。
- Alias 不进入日志、指标、HTTP 或持久化报告。

### 6.2 明确禁止进入 Provider 的字段

- 原问题、`questionSpan`、`goalLabel`、对话消息或摘要；
- turnId、requestToken、ResumeToken、ContextHandle、conversation ID；
- Subject/Claim/Evidence/Chunk 内部 ID；
- `PublicSourceReference` 的 key、route 和 publishedVersion；
- Caveat 正文、omitted topic 正文；
- CandidateSet、EvidenceBundle、Evidence 正文、检索文本和分数；
- Provider 配置、内部预算、SafeReasonCode、异常或诊断字段。

Provider 只需要 Statement 内容完成表达；公开引用保留在服务端 Alias Registry 中。

### 6.3 Prompt 注入边界

即使 Statement 已公开批准，它仍作为数据而不是指令处理：

- 使用静态 System Prompt；
- 白名单对象序列化为 JSON 数据块；
- Prompt 明确声明 Statement 文本中的指令性内容无效；
- 不把任意客户端字符串拼接进 System Prompt；
- 不允许模型请求工具、外部链接、更多上下文或修改规则。

## 7. 模型 Draft Schema

统一 Draft 版本为：

```text
portfolio-expression-draft.v1
```

三个 Material 使用封闭变体；Codec 根据请求的 MaterialKind 选择唯一类型，禁止跨类型解析。

### 7.1 Fact Draft

```json
{
  "schemaVersion": "portfolio-expression-draft.v1",
  "materialKind": "FACT",
  "summary": {
    "text": "……",
    "supports": ["S001", "S002"]
  },
  "sections": [
    {
      "sectionType": "SOLUTION",
      "sentences": [
        {"text": "……", "supports": ["S001"]},
        {"text": "……", "supports": ["S002", "S003"]}
      ]
    }
  ]
}
```

Focused 时 `summary` 必须为 `null`；Overview 时必须非空。

### 7.2 Comparison Draft

```json
{
  "schemaVersion": "portfolio-expression-draft.v1",
  "materialKind": "COMPARISON",
  "intro": {"text": "……", "supports": ["S001", "S004"]},
  "dimensions": [
    {
      "dimensionKey": "D01",
      "subjects": [
        {"subjectKey": "P01", "sentences": [{"text": "……", "supports": ["S001"]}]},
        {"subjectKey": "P02", "sentences": [{"text": "……", "supports": ["S004"]}]}
      ],
      "comparisonSentences": [
        {"text": "……", "supports": ["S007"]}
      ]
    }
  ]
}
```

Dimension、Subject 与顺序必须和请求完全一致。`comparisonSentences` 只能引用 P3 已产生的 relation Statement；不存在 relation Statement 时数组必须为空。

### 7.3 Recommendation/Refine Draft

```json
{
  "schemaVersion": "portfolio-expression-draft.v1",
  "materialKind": "RECOMMENDATION",
  "intro": {"text": "……", "supports": ["S001", "S006"]},
  "items": [
    {
      "candidateKey": "C01",
      "sentences": [
        {"text": "……", "supports": ["S001", "S002"]}
      ]
    }
  ]
}
```

Item 数量、candidateKey 和顺序必须与请求完全一致。模型没有 tier、rank、criterion mutation 或 result count 字段。

### 7.4 所有 Draft 的共同限制

- 严格拒绝未知字段。
- 不允许 Markdown 标题、代码块、HTML、URL 或图片。
- 单个句子不可为空，不可包含换行。
- 每个事实句绑定 1–4 个 Statement Alias。
- `CONTEXT` 不能成为唯一 supports。
- 相同正文不得重复。
- Summary/Intro 不计入 REQUIRED Statement 的正文覆盖。
- 模型不输出 title、section title、source reference、resolution、mode、degraded 或 caveat。

## 8. `StatementGroundingValidator`

Validator 是 P4 的核心可信模块，按固定顺序执行。任一失败丢弃整个 Draft。

### 8.1 结构校验

1. 单一 JSON object；
2. Schema 与 MaterialKind 完全匹配；
3. 未知字段、null 位置和集合上限合法；
4. Summary policy、章节集合和顺序合法；
5. Comparison dimension/subject 与 Recommendation candidate 身份完全匹配；
6. 字符、句子、章节和 supports 数量不超预算。

### 8.2 Alias 与引用作用域

- 所有 Alias 必须来自本次 Input Registry。
- Statement 必须用于其授权的 Section/Dimension/Candidate/Subject。
- REQUIRED 必须在正文覆盖；OPTIONAL 可缺失；CONTEXT 不能独立使用。
- Citation 完全由 supports Alias 反向映射到服务端 `PublicSourceReference`。
- Draft 无权指定、删除、替换或排序 public reference key。
- 映射后每个事实句至少一个公开来源；越界引用整轮失败。

### 8.3 高风险语义原子校验

Validator 从支持 Statement 与 Draft Sentence 提取以下受保护原子：

- 数字、百分比、金额、数量、单位；
- 日期、时间范围、版本号；
- 公开主体 label 与专有技术名称；
- achievement status；
- contribution type；
- verification basis；
- 比较关系、最高级和排序语言；
- 否定、可能性、计划性、局部性与完成性限定词。

Draft 中的受保护原子必须是 supports Statement 原子的子集，或来自静态连接词/通用术语白名单。新数字、新日期、新主体、新技术名、新状态、新贡献、新比较级和新因果词均失败。

### 8.4 限定词保持

以下语义类别不能被删除或强化：

```text
计划/拟/将要
原型/试验/观察
部分/局部/阶段性
尚未/未覆盖/不确定
参与/协作/支持
可能/推测/倾向
```

若支持 Statement 含对应限定类别，使用它的 Draft Sentence 必须保留同类限定。禁止：

- `PLANNED → DELIVERED`；
- `PROTOTYPE → PRODUCTION`；
- `COLLABORATIVE → PRIMARY/独立完成`；
- `PARTIAL → COMPLETE`；
- `OBSERVED → PROVEN`；
- `NO_QUALIFYING_MATCH → 不具备能力`。

### 8.5 任务专用校验

Fact：

- Statement 只能进入授权 Section；
- Focused 不得输出非目标章节；
- Overview 必须覆盖所有 REQUIRED；
- 有 fixed Caveat 时禁止“完整覆盖、全部完成、没有限制”等总括断言。

Comparison：

- 每个事实句只能使用对应 Subject Cell 的 Statement；
- relation 句只能使用 P3 `controlledRelation` Statement；
- Evidence 数量、正文长度与 Retriever 顺序不能形成优劣；
- 单位、口径或版本不一致时禁止数值比较。

Recommendation/Refine：

- Candidate Alias 必须逐项一致且顺序固定；
- Item 句只能使用该 Candidate 对应 Criterion Statement；
- 不允许新增“最优、唯一、一定适合”等结论；
- 不允许通过 Optional 数量改变 Tier 或顺序；
- Refine 不得引用上一轮答案正文或扩大候选范围。

### 8.6 Validator 的保证边界

P4 v1 不宣称对自由自然语言建立形式化蕴含证明。它提供：

- 严格封闭结构；
- 句子级 Statement 支持关系；
- 引用、主体和任务作用域保证；
- 高风险事实原子与限定词的确定性保护；
- 对无法判断的输出失败关闭；
- 大规模对抗集与真实 Provider Conformance 验收。

首版不引入第二个 LLM Judge、Embedding 相似度或模型自我修复，因为它们会新增成本、延迟和同源误判，却不能成为事实权威。

## 9. 服务端 Plan 组装

Draft 通过后，由 `ModelDraftPlanAssembler` 生成最终 `PortfolioAnswerPlan`：

- title 与 section title 来自服务端本地化表；
- section type 来自 Material；
- content 来自已验证 Draft Sentence；
- sourceReferences 由 supports Alias 聚合并稳定去重；
- Caveat 与 omitted topic 由服务端固定附加；
- Comparison/Recommendation 的结构身份来自 Material，不读取模型自由文本推断；
- 组装后的 Plan 再执行一次 P1/P3 公共不变量校验。

模型 Draft 永不直接成为 HTTP DTO。

## 10. 确定性 fallback 与失败语义

### 10.1 先构造 fallback

固定顺序：

```text
1. compose deterministic fallback plan
2. validate fallback plan
3. evaluate model eligibility
4. optionally call model
5. decode and validate draft
6. assemble model plan
7. return model plan or the exact prebuilt fallback plan
```

若步骤 1 或 2 失败：

- 不调用 Provider；
- 返回/映射 `PRESENTATION_BLOCKED` 或既有安全失败；
- 不把模型当作修复不合法 Material 的手段。

### 10.2 原子回退

以下任一情况整轮使用预构造 fallback：

- Provider unavailable、timeout 或 error；
- 空响应或非法 JSON；
- Schema/未知字段错误；
- Alias、覆盖、作用域或高风险语义校验失败；
- Model Plan 组装或最终不变量失败；
- Circuit Breaker 已打开。

禁止：

- 使用通过的部分章节；
- 把模型 Summary 与确定性 Sections 拼接；
- 针对错误再次 Prompt 修复；
- 换 Provider 重发；
- 把模型失败映射为证据不足。

### 10.3 未尝试不等于 fallback

以下情况为正常确定性表达，`degraded=false`（除非 P3 自身已 degraded）：

- 配置关闭；
- 任务类型尚未准入；
- Preset；
- 本轮没有 Expression Allowance；
- 剩余时间不足最小模型窗口；
- Material 超出 P4 输入上限但仍可由确定性 Composer 安全表达。

只有已获准且本应尝试模型，却因 Provider、Breaker、Codec、Validator 或 Model Plan 失败而回退时，才是 Expression Fallback。

## 11. 表达结果与状态

### 11.1 内部结果

```text
PortfolioCompositionResult
├─ answerPlan
├─ compositionMode: DETERMINISTIC | MODEL_GROUNDED | FALLBACK
├─ expressionDisposition
├─ expressionDegraded
└─ safeFailureCode?（仅内部）
```

`expressionDisposition` 使用闭集：

```text
NOT_ATTEMPTED_DISABLED
NOT_ATTEMPTED_INELIGIBLE
NOT_ATTEMPTED_ALLOWANCE
NOT_ATTEMPTED_DEADLINE
NOT_ATTEMPTED_INPUT_LIMIT
ACCEPTED
FALLBACK_CIRCUIT_OPEN
FALLBACK_PROVIDER_FAILURE
FALLBACK_EMPTY_RESPONSE
FALLBACK_SCHEMA_INVALID
FALLBACK_GROUNDING_INVALID
FALLBACK_PLAN_INVALID
```

SafeFailureCode 不进入普通访客响应正文。

### 11.2 公共任务状态

每个 renderable completed task 增加：

```json
{
  "composition": {
    "mode": "DETERMINISTIC | MODEL_GROUNDED | FALLBACK",
    "degraded": false
  }
}
```

公共字段不包含 Provider、Prompt、Validator reason 或 breaker 状态。

### 11.3 顶层聚合

顶层 `GenerationMode` 增加 `MIXED`；`AnswerConstructionMode` 增加 `MIXED_COMPOSITION`。

Portfolio renderable task 聚合：

| 任务组成 | generationMode | constructionMode |
|---|---|---|
| 全部确定性 | `DETERMINISTIC` | `EVIDENCE_COMPOSITION` |
| 全部模型通过 | `MODEL` | `MODEL_GROUNDED` |
| 单任务/全部任务 fallback | `FALLBACK` | `EVIDENCE_COMPOSITION` |
| 模型、确定性或 fallback 混合 | `MIXED` | `MIXED_COMPOSITION` |

顶层 `degraded` 是 P3 retrieval degradation、P4 expression fallback 与其他既有 degradation 的 OR。正常未尝试模型不设置 degraded。

P4 不改变 TaskResolution、AnswerResolution 或 EvidenceState。

## 12. Provider、策略与配置

### 12.1 Provider 调用

P4 复用现有不可变 Provider Registry 与 OpenAI-compatible 传输基础，但建立独立：

- `PortfolioExpressionPort`；
- `PortfolioExpressionPromptFactory`；
- `PortfolioExpressionDraftCodec`；
- Provider operation `EXPRESS`。

不把 P4 逻辑继续加入通用 `ConversationalModelPort.generate/review` 或单一大 Prompt switch。可以在 Adapter 内提取共享 JSON HTTP client，但 P4 的输入、输出和错误类型保持独立。

首版调用参数：

- 单一启动时 Provider；
- 非流式；
- structured JSON object；
- thinking disabled；
- temperature `0.1`；
- 每次最多 1600 output tokens；
- 不重试；
- 不跨 Provider fallback；
- 不调用模型 review/judge。

### 12.2 配置

```text
portfolio.model-expression.enabled=false
portfolio.model-expression.provider=DEEPSEEK_V4_FLASH
portfolio.model-expression.policy-version=p4-expression-policy-v1
portfolio.model-expression.input-schema-version=portfolio-expression-input.v1
portfolio.model-expression.draft-schema-version=portfolio-expression-draft.v1
portfolio.model-expression.allowed-material-kinds=FACT
portfolio.model-expression.timeout=4s
portfolio.model-expression.max-output-tokens=1600
portfolio.model-expression.external-public-data-policy-approved=false
```

规则：

- 默认关闭。
- 配置只能从当前 build-supported kinds 中收紧，不能通过配置提前启用 Comparison/Recommendation。
- `enabled=false` 时缺少 key/审批合法，且不得调用 Provider。
- 生产 `enabled=true` 时，缺少审批、key、Registry compatibility、HTTPS endpoint 或 Schema support 必须启动失败。
- timeout 最大 4 秒、attempt 固定为 1、max output tokens 最大 1600；配置只能收紧，超限启动失败。
- 请求不能覆盖 Provider、temperature、timeout、token 或 material kinds。

### 12.3 Circuit Breaker

P4 内建进程内短熔断，防止 Provider 连续失败让每个请求都消耗完整 timeout：

```text
CLOSED
→ 连续 3 次 eligible expression failure
→ OPEN 30 秒
→ 允许 1 次 HALF_OPEN eligible request
→ 成功则 CLOSED；失败则重新 OPEN 30 秒
```

计入 failure：Provider error/timeout、empty、invalid schema、grounding invalid。正常未尝试不计入。Breaker 状态不持久化，不跨实例协调，不改变证据或任务状态。OPEN 时使用预构造 fallback，记为 `FALLBACK_CIRCUIT_OPEN` 与 degraded。

## 13. 时间与容量预算

P4 共享 P3/P2 的绝对请求 deadline，不在 P3 之后追加独立 8 秒预算。

固定规则：

- 整体 HTTP 上限继续为 12 秒。
- 现有请求开始后 10 秒执行截止时间继续为 P2/P3/P4 共同 deadline；最后 2 秒保留 Context 提交与响应映射。
- Provider timeout 为 `min(4s, executionDeadline - now)`。
- 剩余可用窗口小于 1500ms 时不尝试模型，使用确定性 Plan，非 degraded。
- P4.1 每回合最多 1 次 Provider expression attempt。
- 单次最多 16 个 Statement、12000 个序列化输入字符。
- Fact Draft 最多 6 个 Section、每章 4 句、总计 18 句、每句 1–4 个 supports。
- Model content 最多 `min(2400, TaskExecutionAllowance.characterLimit)` 个字符。
- Summary 最多 300 字符；Focused 禁止 Summary。
- Caveat 字符由服务端预算，不计入模型可写字符，但最终 Plan 不得突破 task character limit。
- 输入超限不截断 REQUIRED，也不调用模型；完整 Material 继续走确定性 Composer。

P4 不借用其他任务未使用的时间、字符或 attempt。

## 14. 前端契约与交互方案

前端由独立 Agent 设计和实现，P4 后端只规定以下公共语义。

### 14.1 展示原则

- `MODEL_GROUNDED`、`DETERMINISTIC` 与 `FALLBACK` 使用完全相同的 Answer Section、Recommendation Item 和 Evidence Desk 组件。
- 不展示“AI 增强”“模型生成”徽标、Provider 名称、耗时或 fallback 原因。
- Fallback 是成功回答，不弹错误 Toast，不显示重试按钮。
- Evidence 继续以 `sourceReferences` 为唯一新路径；模型不改变引用交互。
- `RESULT_COMPOSED` 继续是唯一用户可见形成回答阶段，不新增“调用模型/验证模型”拟真进度。
- P4 不引入 Streaming、逐字输出、SSE 或新的等待动画。

### 14.2 前端必须适配

- `GenerationMode` 新值 `MIXED`；
- `AnswerConstructionMode` 新值 `MIXED_COMPOSITION`；
- completed task 可选 `composition { mode, degraded }`；
- 未提供 task composition 的旧/兼容响应继续安全映射；
- 映射层保留 `sourceReferences`，不得根据 model mode 改写正文或引用；
- 前端诊断只记录非法 enum/contract，不记录正文、reference key、Token 或 Provider 信息。

### 14.3 前端验收

- 单任务模型回答与确定性回答布局完全一致；
- fallback 无错误 UI，Evidence 可正常打开；
- 多任务 `MIXED/MIXED_COMPOSITION` 不导致未知状态或内容丢失；
- task composition 缺省时兼容；
- 桌面与移动均无溢出、重复来源标签或引用回归；
- loading、ExecutionSnapshot、Context 恢复卡和 Recommendation 交互无回归。

## 15. 隐私、日志与可观察性

### 15.1 Provider 数据边界

允许发送：

- 已发布公开主体 label；
- 已批准公开 Claim statement/detail 的最小投影；
- 闭集 ExpressionIntent；
- 请求内临时 Alias；
- 结构和字符预算。

禁止发送：

- 用户原文和历史；
- 内部 ID、Token、Context、route、reference key；
- Evidence/Chunk 正文；
- 私有材料、审核备注、异常或诊断信息。

模型输入和输出只在请求内存存活，不写数据库、文件、URL、浏览器存储或普通日志。

### 15.2 诊断事件

只记录：

```text
expression.eligibility
expression.provider.completed
expression.provider.failed
expression.validation.completed
expression.fallback.used
```

字段只允许：

- task kind / material kind；
- disposition / closed failure code；
- statement/section/sentence count bucket；
- input/output size bucket；
- duration bucket；
- breaker state enum；
- provider operation enum；
- boolean attempted/accepted/fallback。

禁止记录正文、Alias、Prompt、JSON、主体、reference key、Provider response、HTTP body 或异常消息。

## 16. 测试与 Eval

### 16.1 领域与深模块测试

- 三种 Material 的不变量与 `toGroundedContribution()` 不受模型影响。
- ExpressionIntent 不含原文、goalLabel 或 Context。
- Alias 稳定、请求内唯一且不使用内部 ID/hash。
- fallback 先构造且与模型禁用时对象值一致。
- NOT_ATTEMPTED、ACCEPTED、FALLBACK 的状态映射准确。
- P3 Resolution/EvidenceState/Recommendation 排序不被 P4 改变。

### 16.2 Codec 与 Validator 对抗集

至少覆盖：

- 非法 JSON、顶层数组、未知字段、未知 enum、重复字段；
- 虚构 Alias、跨请求 Alias、空 supports、CONTEXT 单独支持；
- 漏掉 REQUIRED、越权章节、Focused 生成 Summary；
- 新数字、日期、版本、单位、主体、技术名；
- “参与”改成“独立完成”；
- “计划/原型/观察”改成“已上线/生产验证/证明”；
- 删除“部分、尚未、可能”等限定词；
- 否定翻转、因果强化、最高级和无依据比较；
- 缺少证据写成不具备能力；
- Recommendation 改序、增删候选、跨 Candidate 引用；
- Markdown、HTML、URL、控制字符、超长内容；
- Claim 文本中的 Prompt injection 不能改变 Schema 或规则。

### 16.3 Adapter 与隐私捕获

- Provider 请求恰好一个非流式 JSON call；
- thinking disabled、temperature/token/timeout 固定；
- 无原问题、历史、goalLabel、Token、route、reference key 或 Evidence 正文；
- Provider timeout/error/empty/invalid 均安全映射；
- 日志和诊断不含请求/响应正文与异常消息。

### 16.4 Circuit Breaker

- 连续三次失败开启；
- OPEN 不调用 Provider；
- 30 秒后只允许一个 HALF_OPEN；
- 成功关闭、失败重开；
- 并发状态线程安全；
- Clock 可注入，测试不 sleep。

### 16.5 生产集成与前端

- P2→P3→P4→TaskResultPayload→HTTP 真实可达；
- 配置关闭零 Provider 调用；
- 单主体 Fact 模型通过返回 MODEL_GROUNDED；
- Provider/Validator 失败返回完整确定性内容与 FALLBACK；
- 多任务状态正确聚合为 MIXED；
- sourceReferences、Evidence Desk、ExecutionSnapshot 和 Context 无回归。

### 16.6 Eval Verdict

P4 增加独立报告维度：

```text
OFFLINE_VALIDATION
MOCK_PROVIDER_INTEGRATION
PRIVACY_CAPTURE
MODEL_CONFORMANCE
REAL_PROVIDER_ACCEPTANCE
ANSWER_QUALITY_COMPARISON
```

规则：

- 普通 CI 必须通过前四项；真实 Provider 未授权/未运行为 `INCOMPLETE`，不能伪装 PASS。
- 结构、事实、引用、隐私和 fallback 任一失败为 BLOCKER。
- 模型回答质量未显著优于确定性基线时保持默认关闭，不影响确定性发布。
- 质量比较至少覆盖直接性、连贯性、重复、限定词保持、章节覆盖和引用完整性。
- 持久化报告只存枚举、计数、bucket 与 verdict，不存问题、答案、Alias 或 reference key。

## 17. 实施切片与迁移

### 17.1 P4-A：深化 Material 与 composition seam

- 将扁平 `PortfolioAnswerMaterial` 迁移为强类型变体。
- 补全 `GroundedStatement` 的 subject/predicate/status/contribution/support target。
- 建立 `toGroundedContribution()`，保证 Synthesis 使用模型前事实。
- 引入 `PortfolioAnswerComposition` 与 `PortfolioCompositionResult`。
- 先只接确定性 Composer，冻结 P3 行为基线。
- P3 executor 只依赖深模块，不感知模型分支。

### 17.2 P4-B：Input、Draft 与 Validator

- 实现 ExpressionIntent、Allowance、Alias Registry 与 Input Projector。
- 实现三种 Draft 类型和严格 Codec；P4.1 只将 Fact 标记 build-supported。
- 实现公共 Validator 与 Fact 专项 Validator。
- 完成全部对抗测试；Provider Port 使用 Fake，不接生产调用。

### 17.3 P4-C：Provider、fallback 与状态

- 建立独立 `PortfolioExpressionPort` 和 OpenAI-compatible Adapter。
- 接入 ModelPolicy、启动校验、deadline 与 Circuit Breaker。
- 完成先构造 fallback、整轮回退和 composition 状态传播。
- 默认配置保持关闭。

### 17.4 P4-D：公共契约、前端交接与 Eval

- 增加 task composition、MIXED 与 MIXED_COMPOSITION。
- 后端完成 DTO/Mapper；前端 Agent 按交接文档实施。
- Eval 迁移到生产同一 composition seam。
- 完成 Mock、隐私、packaged-JAR 与桌面/移动 E2E。

### 17.5 P4-E：真实 Provider 验收与 P4.1 准入

- 显式授权真实 Provider；
- 运行 Conformance、延迟、timeout、限流和质量比较；
- 通过后允许部署配置启用 `FACT`；默认仓库配置仍关闭；
- 未通过时确定性路径继续作为正式能力。

### 17.6 P4.2/P4.3

Comparison 与 Recommendation 分别建立专项实现切片和验收证据。它们不得仅因类型和 Codec 已存在而标为已实现或可启用。

### 17.7 旧代码清理

- 删除旧的 Model Expression 决策岛和不可达 Prompt contract。
- 不复用旧 `generate + review` 双调用冒充 P4。
- 不保留长期 feature-flag 双实现；唯一生产 composition seam 内由策略决定尝试或确定性。
- 前端和后端原子发布新增 enum/DTO；不长期维护两套任务 composition 契约。

## 18. 验收标准

P4.1 完成必须同时满足：

1. 单主体 Fact 在配置允许时真实可达 `MODEL_GROUNDED`。
2. Provider 只接收最小公开 Material 与闭集 ExpressionIntent，不接收原问题和历史。
3. 模型不能改变 P2 任务、P3 支持度、Resolution、EvidenceState、比较或推荐结果。
4. REQUIRED 全部覆盖；OPTIONAL 可省略；CONTEXT 不能独立结论。
5. 引用完全由服务端 Statement Alias 派生，模型不能虚构 reference。
6. 数字、时间、版本、状态、贡献、限定词和主体归属通过确定性校验。
7. Caveat 与 omitted topic 始终由服务端表达。
8. 任一 Provider、Codec、Validator 或 Plan 失败整轮返回预构造确定性 Plan。
9. 正常未尝试与真实 fallback 状态可区分；degraded 语义准确。
10. 多任务顶层与 task-level composition 状态准确。
11. 前端不展示 Provider/AI/fallback 技术细节，引用与章节体验无回归。
12. 普通 CI 不外发；Mock、隐私、对抗、架构、前端和 E2E 门禁通过。
13. 真实 Provider 未运行时报告 INCOMPLETE；通过 Conformance 和质量门槛后才能在部署中启用。
14. P2 Synthesis 只消费模型前 Grounded Contribution。
15. 权威状态文档能区分“代码存在、配置允许、真实调用、Draft 通过、最终采用和 fallback”。

## 19. 明确不包含

- 不实现 HYBRID/MIXED 通用知识与作品集事实的来源融合；该能力仍属于 P5。
- 不发送问题或对话历史给作品集表达 Provider。
- 不实现 Streaming、SSE、WebSocket 或逐字展示。
- 不实现模型工具选择、检索、计划修复或动态 Agent loop。
- 不实现多 Agent、长期记忆、用户画像或跨会话模型上下文。
- 不实现第二模型 Judge、模型自我修复、跨 Provider fallback 或自动重试。
- 不允许客户端自由 Persona、Prompt、temperature、Provider 或 token 设置。
- 不把 Comparison/Recommendation 的类型存在写成已准入；必须完成各自专项门禁。
- 不修改前端视觉设计；前端由独立 Agent 根据公共契约实施。

## 20. 文档与状态维护

实施时同步：

- `docs/08-当前实现状态.md`；
- `docs/11-项目演进日志.md`；
- `docs/13-Agent对话体验与智能编排改造路线图.md`；
- 状态索引、隐私说明、环境变量示例与发布门禁；
- 前端交接与 Eval 文档。

状态必须分别表达：

```text
DESIGNED
IMPLEMENTED_DISABLED
MOCK_VERIFIED
REAL_PROVIDER_INCOMPLETE
REAL_PROVIDER_VERIFIED
DEPLOYMENT_ENABLED
```

不能把 enum、类、Mock 或本地配置存在直接写成真实 Provider 已验收或生产已启用。
