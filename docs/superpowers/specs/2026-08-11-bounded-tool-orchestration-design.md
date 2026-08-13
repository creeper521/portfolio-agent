# Agent P3：受限 Portfolio 执行、证据晋升与会话业务上下文设计

> 日期：2026-08-12
> 状态：设计决策已收敛，待最终审阅；尚未进入实施
> 适用范围：P3 v1 及其与 P2、P1、未来 P4 的边界
> 上游：`2026-08-06-agent-answer-composition-design.md`、P2 语义路由闭环、`13-Agent对话体验与智能编排改造路线图.md`
> 前端交接：`../../handoffs/2026-08-12-agent-p3-frontend-contract-handoff.md`
> 后端实施计划：`../plans/2026-08-12-agent-p3-backend-implementation.md`
> 说明：本文是 P3 的决策基线。产品、架构与首版实施参数已经收敛；未来能力只进入“明确延后项”。

## 0. 当前结论

P0、P1、P2 已完成各自批准范围。P3 不重新建设一套模型 Tool Planning，也不恢复旧 `ConversationToolService` 的决策权。

P3 v1 在单个 Portfolio 语义任务内部建立一个确定性、只读、有界的执行深模块：

```text
P2 强类型 SemanticTask
    ↓
SemanticTaskExecutionContext
    ↓
PortfolioExecutionPlanner
    ↓
唯一强类型 PortfolioEvidenceInvocation
    ↓
PORTFOLIO_EVIDENCE_RETRIEVAL_V1
    ↓
PortfolioRetrievalCandidateSet
    ↓
EvidencePromotionValidator
    ↓
ValidatedEvidenceBundle
    ↓
EvidenceSupportAssessor
    ↓
任务 Result Policy
    ↓
P1 Composer / TaskOutcome
```

核心取舍：

1. P2 仍是唯一语义决策权威；P3 不读取原始问题或 `goalLabel` 重新判断意图。
2. P3 v1 Catalog 只有一个真实业务能力：`PORTFOLIO_EVIDENCE_RETRIEVAL_V1`。
3. Subject 元数据与 Claim–Evidence 候选材料由该能力原子返回，不另设 metadata capability。
4. P3 不建设通用 Tool/Orchestrator 框架，不支持动态注册、任意参数 Map、任意 URL、SQL、路径或写操作。
5. `Timeline` 不属于 P3 v1。P2 已完成；Timeline 是未来需求，必须先形成正式强类型 `TIMELINE` facet，再单独决定是否成为第二个 capability。
6. RAG 不被删除。Chunk、向量、BM25、Hybrid/RRF 和 Grounding Gate 继续存在，但留在 Retriever 内部；只有正式 Subject、Claim–Evidence 候选跨越 P3 capability 边界。
7. 模型不再决定工具、主体范围、检索参数、证据准入、预算、重试或 fallback。模型仍可用于 P2 的闭集语义分类、通用知识回答，以及未来 P4 对已验证材料的受约束表达。
8. 前端不展示拟真的工具调用过程。P3 v1 提供安全的执行计划和最终进度快照；真正实时传输以后单独设计。
9. P3 v1 增加服务端强类型业务上下文，但不建设跨会话长期用户记忆。

## 1. 与当前仓库边界的关系

### 1.1 必须继续遵守的不变量

- 运行时只能读取已经审阅的公开作品集快照或与其等价的受控公开数据源。
- 不能读取私有 Obsidian、原始日报、凭据、未审核截图或其他非公开材料。
- Java 生产代码和测试代码不得使用 `record`、`var` 或 Lombok；值对象使用显式不可变类。
- P3 只读、失败关闭，不新增通用联网、MCP、代码执行或数据库写能力。
- 不记录访客问题原文，不记录答案正文，不在日志中泄露内部 ID、路径、SQL、主机、异常消息或证据正文。

### 1.2 已确认的产品与隐私边界变更

当前仓库规则规定：访客会话只存在于前端页签内存，刷新或关闭后消失，服务端不持久保存会话。

已经正式确认：为了让 Refine 和普通连续指代拥有可信来源，P3 v1 有意改变原有“刷新即失效”边界，持久保存**同一会话内的强类型业务上下文**，并在页面刷新和生产服务重启后恢复。这是 P3 的正式产品能力，不推迟到 P5。

持久化内容不包括问题原文、答案、CandidateSet、EvidenceBundle 或证据正文，只包括任务类型、公开主体引用、Facet/Dimension、ContentVersion、授权范围和推荐约束等结构化业务状态。

这项决策只把“同会话可信业务状态”前移到 P3，不提前建设 P5 的跨会话长期用户记忆、HYBRID/MIXED 或通用 Memory。

由于它改变了现有隐私和部署边界，实施前必须同步更新 `AGENTS.md`、权威产品/隐私文档、存储许可、删除策略和用户告知；这些文档未完成批准时，服务端上下文持久化不得接入生产。该同步是 P3 的实施前置门禁，不是可选的收尾文档工作。

### 1.3 后端与前端交付职责

P3 后端的全部详细设计由本文负责收敛，包括领域模型、模块边界、Planner、Capability、证据规则、状态机、公共 DTO、Context Store、数据库事务、加密、幂等、失败策略、迁移和验收门禁。

前端视觉、组件结构、交互样式、动效、响应式布局和高保真原型由独立前端 Agent 设计和实现。P3 后端不替前端预设拟真的工具过程 UI，也不直接修改前端原型。

为避免职责分离后出现语义漂移，本文配套维护 `docs/handoffs/2026-08-12-agent-p3-frontend-contract-handoff.md`。该交接文档只规定前端必须遵守的后端契约、状态语义、安全边界、时序、降级和验收场景；前端 Agent 可以自由决定呈现方案，但不得在客户端重建主体授权、推荐排序、证据准入、执行进度或会话业务上下文。任何需要改变公共语义或 DTO 的前端建议必须先回到本文修订，再进入实现。

## 2. 模块边界与唯一执行入口

P3 建议位于 `answer.intelligence.execution` 深模块中。模块对外只暴露窄执行接口，内部封装 Catalog、Planner、Capability、CandidateSet、证据晋升、支持度评估和结果策略。

P2 与 P3 的执行 seam 升级为：

```java
public interface SemanticTaskExecutor {
    TaskOutcome execute(SemanticTaskExecutionContext context);
}
```

`SemanticTaskExecutionContext` 至少包含：

```text
semanticTask
applicableExclusions
dependencyOutcomes
expectedContentVersion
taskExecutionAllowance
authorizedContextReferences
```

P3 不从 `goalLabel`、用户问题、Prompt 或依赖任务的自由文本中编译检索请求。

建议的内部依赖方向：

```text
service
  → planning
  → capability
  → validation
  → support
  → resultpolicy
  → domain / gateway
```

Bundle/PostgreSQL/Failover Adapter 保留在现有 Retriever 侧，通过窄接口接入；不为追求包结构整齐而机械搬迁现有 Adapter。

## 3. Capability Catalog 与生命周期

### 3.1 唯一业务能力

P3 v1 Catalog 只包含：

```text
PORTFOLIO_EVIDENCE_RETRIEVAL_V1
```

能力 Descriptor 是启动时冻结的业务描述，只包含稳定 ID、版本、只读标志、允许任务类型、输入输出类型和硬上限。v1 不提供运行时注册 API，不根据健康状态动态切换业务能力。

Bundle、PostgreSQL 与 Failover 是这一能力背后的启动时组合，不是 Planner 可见的三个 capability。

### 3.2 生命周期采用正交维度

能力状态不使用一个不断膨胀的扁平枚举，而按以下维度记录：

```text
registration
availability
authorization
selection
execution
```

执行结果至少区分：

```text
SUCCESS
EMPTY
UNAVAILABLE
TIMED_OUT
INTEGRITY_FAILED
FALLBACK_SUCCEEDED
```

### 3.3 Capability 窄接口

```java
public interface PortfolioEvidenceCapability {
    PortfolioCapabilityResult execute(
            PortfolioEvidenceInvocation invocation,
            CapabilityExecutionConstraints constraints);
}
```

`PortfolioEvidenceInvocation` 只携带：

```text
AuthorizedSubjectScope
FacetRetrievalProfile
ComparisonDimensionProfiles
EvidenceSelectionPolicy
ExpectedContentVersion
```

它不包含：

- 原始问题；
- `goalLabel`；
- Prompt；
- 任意查询字符串；
- 模型生成关键词；
- `Map<String, Object>`；
- SQL、URL、文件路径；
- 其他任务的自由文本结果。

Capability 只返回未经晋升的 `PortfolioRetrievalCandidateSet`。所有证据合法性判断统一由 Capability 外部的 `EvidencePromotionValidator` 完成。

## 4. 确定性 Planner

`PortfolioExecutionPlanner` 是纯确定性授权编译器，不是模型工具选择器。

同一强类型任务、Catalog 快照、排除项、Context 和 Allowance 必须产生同一计划或同一拒绝原因。

P3 v1 的合法 Portfolio 计划恰好包含一个强类型 `PortfolioEvidenceInvocation`，不包含通用 invocation 列表、工具图、可选工具探索或动态 plan repair。

Planner 必须拒绝：

- 主体范围扩大；
- ContentVersion 不一致；
- 明确任务参数与排除项冲突；
- Refine 缺少可信原上下文；
- 使用不支持的 Facet/Dimension/Profile；
- Allowance 无效或已过期；
- 原始问题、`goalLabel` 或自由文本渗入执行参数。

P2 负责语义一致性；P3 负责已编译执行的一致性。P3 不静默修复 P2 的显式参数冲突。

## 5. SubjectScope、排除项与 ContentVersion

### 5.1 推荐范围

推荐候选范围只有两种合法语义：

```text
EXACT_SUBJECTS(subjects)
ALL_PUBLISHED_CANDIDATES(contentVersion)
```

非空候选集合编译为 `EXACT_SUBJECTS`；空候选集合明确编译为当前 ContentVersion 下的全部已发布候选人，不能使用 `null` 或隐式 unbounded 表达。

Exact scope 绝不在执行阶段扩大。Open scope 可以应用负向 Subject 过滤，但不能突破该 ContentVersion 的已发布候选集合。

### 5.2 Refine 的授权绑定

`RecommendationScopeBinding` 至少保存：

```text
scopeMode
exactSubjectReferences（仅 exact）
contentVersion
```

Refine 继承原授权候选范围，只能缩小、排除、重新约束或重排，不能增加新主体，也不能从 exact 切换为 all-public。

### 5.3 ContentVersion 固定

Refine 必须继续使用原 Recommendation 的 ContentVersion。旧版本不可用时返回 `CONTEXT_VERSION_UNAVAILABLE`，要求用户基于最新版本创建新的 RecommendationTask；不得静默升级。

`ALL_PUBLISHED_CANDIDATES(V1)` 表示 V1 快照中的全部已发布候选人，不表示每轮执行时的最新候选人。

### 5.4 排除项

- 显式冲突不静默修复，直接拒绝。
- 输出排除不削弱内部证据验证。
- 排除项不能绕过 VERIFIED、APPROVED、PUBLIC、只读和隐私硬边界。
- 输出排除若使必要引用无法公开展示，应删除对应 Statement 并降低 `AnswerCoverage`。

## 6. Retrieval Profile 与 RAG 边界

### 6.1 Facet 与 Dimension Profile

每个 PortfolioFacet 映射到唯一、版本化的 `FacetRetrievalProfile`。普通 Facet 直接映射到受控 Claim categories；`CHALLENGE/INCIDENT` 使用固定类别白名单和固定受控术语，并且只有通过 Retriever 相关性门禁才能视为覆盖。

比较维度同样使用固定 `ComparisonDimensionProfile`。Profile 不能读取原始问题动态扩写查询，也不能由模型临时生成权重。

### 6.2 RAG 保留但边界收紧

保留：

```text
公开语料
Chunk
Keyword/Vector Index
Embedding
BM25
Hybrid/RRF
Grounding Gate
```

边界调整为：

```text
RAG Corpus
    ↓
Retriever
    ↓
PortfolioRetrievalCandidateSet
    ↓
EvidencePromotionValidator
    ↓
ValidatedEvidenceBundle
```

Chunk 正文、向量、原始浮点相关度、内部 Chunk ID 和检索查询不跨越 Capability 边界，不进入日志、HTTP、P1 或未来 P4。Chunk ID/排名只可作为 Retriever 内部短生命周期信息。

## 7. PortfolioRetrievalCandidateSet

CandidateSet 是一次受控检索快照，不是自由文本结果列表。

```text
PortfolioRetrievalCandidateSet
├─ capabilityVersion
├─ returnedContentVersion
├─ executedScope
├─ candidateSubjects[]
│  ├─ SubjectSnapshot
│  └─ claimEvidenceCandidates[]
│     ├─ ClaimSnapshot
│     ├─ EvidenceSnapshot
│     └─ RetrievalMatch
├─ coverageReport
└─ completionMetadata
```

Claim 与 Evidence 必须作为原子候选单元返回。禁止：

- 只有 Claim、没有对应 Evidence；
- Evidence 游离于 Claim；
- P3 根据 Chunk 自行推断 Claim 归属；
- 混合多个 ContentVersion；
- Adapter 返回自由文本让 P3 再理解。

`RetrievalMatch` 只包含受控 Profile、命中的 Facet/Dimension、rank bucket 和 relevance gate 状态，不包含动态关键词、Chunk 正文、向量或原始浮点分数。

### 7.1 完整 Coverage Report

成功 CandidateSet 必须为每一个：

```text
Authorized Subject × Retrieval Target
```

明确声明：

```text
MATCHED
EVALUATED_NO_QUALIFYING_MATCH
NOT_EVALUATED_BUDGET
```

`EVALUATED_NO_QUALIFYING_MATCH` 只表示在当前版本、Profile 和门槛下没有合格公开证据，不表示主体不具备能力。

只有计划内 `NOT_EVALUATED_BUDGET` 可以形成合法 Partial。超时、断连或返回中断造成的缺失属于 Attempt 失败，整次结果丢弃。

最终通过 Validator 后因输出容量未进入 Bundle 的原子材料单独记为 `VALIDATED_BUT_OMITTED_BY_OUTPUT_BUDGET`。

## 8. Evidence Promotion 与 ValidatedEvidenceBundle

### 8.1 晋升顺序

```text
1. 整体身份、安全与版本检查
2. 确定性去重
3. Claim–Evidence 原子预算截断
4. Coverage 计算
```

以下任一情况导致整个 CandidateSet `INTEGRITY_FAILED`：

- Subject 不在授权范围；
- ContentVersion 冲突；
- Claim 不是 VERIFIED；
- Evidence 不是 APPROVED；
- Subject/Claim/Evidence 不是 PUBLISHED/PUBLIC；
- Claim–Evidence 关系不存在或身份冲突；
- Profile、Facet、Dimension 或类别白名单不匹配；
- 冲突重复记录。

正常无匹配不是失败，而是 `COMPLETED + INSUFFICIENT`。完全相同的重复材料可以合并；相同身份但内容冲突必须失败。

### 8.2 纯证据包

```text
ValidatedEvidenceBundle
├─ authorizedScope
├─ contentVersion
├─ validatedSubjects[]
│  ├─ SubjectMaterial
│  └─ supportedEvidenceUnits[]
│     ├─ SupportTarget
│     ├─ VerifiedClaim
│     └─ ApprovedPublicEvidence[]
└─ ValidationSummary
   ├─ acceptedUnitCount
   ├─ deduplicatedUnitCount
   ├─ budgetOmittedUnitCount
   └─ coveredTargets
```

Bundle 不包含推荐排序、比较结论、自然语言答案、模型摘要、Capability 名称或 Adapter 细节。

## 9. 结果维度与职责分离

执行结果使用互相独立的维度：

```text
ExecutionCompletion = COMPLETED | REJECTED | FAILED
EvidenceSupport = SUFFICIENT | PARTIAL | INSUFFICIENT | NOT_APPLICABLE
degraded = true | false
AnswerCoverage = COMPLETE | PARTIAL | NONE
```

职责分工：

```text
EvidencePromotionValidator
    → 证据是否合法

EvidenceSupportAssessor
    → 合法证据是否足以支持当前任务

Result Policy
    → 在支持等级内形成结构化业务结果

OutputExclusionPolicy
    → 哪些受支持内容可以安全展示

P1 Composer
    → 确定性自然语言表达
```

Result Policy 不能提升 `EvidenceSupport`。输出排除不能改变内部证据合法性；它只降低 `AnswerCoverage`。

## 10. 事实、比较、推荐与 Refine 规则

### 10.1 Fact

事实任务按 `Subject × Facet` 覆盖：

- 全部请求单元格覆盖：`SUFFICIENT`；
- 至少一个覆盖但不完整：`PARTIAL`；
- 一个都未覆盖：`INSUFFICIENT`。

覆盖要求至少一组完整的 `Verified Claim + Approved Public Evidence`。主体元数据、Claim 标题或 RetrievalMatch 不算证据。

预算顺序：先让所有请求单元格获得最低评估，再轮转补充证据深度。

### 10.2 Compare

单维度状态：

```text
FULLY_COVERED
PARTIALLY_COMPARABLE
NOT_COMPARABLE
```

- 所有参与主体均覆盖：`FULLY_COVERED`；
- 至少两个主体覆盖但未覆盖全部：`PARTIALLY_COMPARABLE`；
- 少于两个主体覆盖：`NOT_COMPARABLE`。

任务级：

- 所有维度 fully covered：`SUFFICIENT`；
- 至少一个维度可比较：`PARTIAL`；
- 没有任何双主体可比维度：`INSUFFICIENT`。

预算顺序：固定维度优先级 → 维度内覆盖全部主体 → 最后补充证据深度。

只有同口径、同单位、同版本的结构化 ComparableValue 才允许 `LEFT_GREATER/RIGHT_GREATER/EQUAL`。否则只并列陈述事实和覆盖差异；Evidence 数量、文本长度和 Retriever 排名不得推导能力高低。

### 10.3 Recommendation 准入

推荐 Profile 分为：

```text
baselineRequirements
userConstraints
optionalCriteriaInPriorityOrder
perCriterionEvidenceCap
profileVersion
```

候选人必须在所有必需条件上拥有至少一组完整证据才能进入排序。可选条件只能影响排序，不能补偿必需证据缺失。

P3 v1 冻结两个 Profile：

```text
GENERAL_PORTFOLIO_RECOMMENDATION_V1
CAPABILITY_MATCH_RECOMMENDATION_V1
```

`GENERAL_PORTFOLIO_RECOMMENDATION_V1` 的唯一 baseline criterion 是 `PUBLIC_DELIVERY_EVIDENCE`：候选人至少拥有一组属于 `RESPONSIBILITY / IMPLEMENTATION / VERIFICATION / OUTCOME` 任一类别的完整公开证据。该 criterion 是受控 OR-group，不等于要求四个类别全部覆盖。

当 P2 RecommendationTask 含非空 `CapabilityCode` 集合时使用 `CAPABILITY_MATCH_RECOMMENDATION_V1`：除 `PUBLIC_DELIVERY_EVIDENCE` 外，每个用户明确要求的 CapabilityCode 都是独立 required criterion，必须有与该能力绑定的完整公开证据。`CareerTrack` 是硬元数据过滤条件，不是可由其他证据补偿的排序分；`audienceRole` 只影响 P1 展示措辞，不改变准入和排序。无法映射到闭集 Profile 的自由文本 goal 必须澄清，不进入 P3。

任务级：

- 所有授权候选人完成必需条件评估，且至少一人通过准入：`SUFFICIENT`；
- 至少一人通过准入，但部分候选人/条件未评估或关键材料未进入最终 Bundle：`PARTIAL`；
- 无人通过全部必需条件：`INSUFFICIENT`。

### 10.4 Recommendation 排序

P3 v1 采用：

```text
固定条件优先级
→ 受上限约束的证据深度
→ 允许并列 RecommendationTier
```

不使用原始检索分数、文本长度、重复证据数量、Subject ID 或姓名顺序强行打破业务并列。稳定 ID 只可用于 `displayOrder`，不能解释为质量排名。

两个 v1 Profile 共用固定 optional criterion 顺序：

```text
VERIFICATION
IMPLEMENTATION
TECHNICAL_DECISION
OUTCOME
RESPONSIBILITY
LEARNING
```

每个 criterion 最多计入 2 组 distinct Claim–Evidence 原子材料；超过上限只可作为已验证但未计分材料保留或按 Bundle 预算省略。`LIMITATION` 只用于边界说明，不作为正向加分；明确负向 Claim 也不参与正向排序。

预算采用公平轮转：

```text
必需条件广度优先
→ 可选条件广度优先
→ 证据深度轮转
```

所有候选人的必需条件完成最低评估前，不为某个候选人追加可选条件或多组深度证据。

### 10.5 Refine

Refine 只支持封闭强类型操作：

```text
ADD_USER_CONSTRAINT
REPLACE_USER_CONSTRAINT
REMOVE_USER_CONSTRAINT
REORDER_PREFERENCES
EXCLUDE_SUBJECTS
LIMIT_RESULT_COUNT
```

`baselineRequirements` 不可削弱，候选授权范围不可扩大。需要新增主体时必须创建新的 RecommendationTask。

Refine 继承授权范围、ContentVersion 和约束状态，但不继承上一轮推荐结论、自然语言答案或 EvidenceBundle。每次 Refine 针对修改后的 Profile 重新执行一次逻辑检索；缓存只能作为 Capability 内部透明优化。

### 10.6 缺少证据不等于负面事实

`NO_QUALIFYING_MATCH`、`NOT_EVALUATED` 和预算截断只能生成“当前公开材料不足”的 Caveat，不能转化为“主体不具备能力”或“候选人不合格”。

只有明确的、VERIFIED、APPROVED、PUBLIC 的负向 Claim 才能形成受控负面陈述，并且只能使用审核过的公开表达。

## 11. GroundedStatement、引用与 P1/P4

Result Policy 只生成结构化 `GroundedStatement`，不从 Evidence 正文自由总结新事实。

```text
GroundedStatement
├─ statementType
├─ subjectReferences
├─ controlledPredicate
├─ approvedPublicClaim
├─ publicSourceReferences
└─ supportTarget
```

每条事实至少绑定一个公开来源；比较结论绑定参与比较的各方来源；推荐理由绑定对应候选人和条件的来源。

公共引用使用 `PublicSourceReference`：

```text
referenceKey
label
sourceType
subjectRoute
evidenceRoute（可选）
publishedVersion
```

`sourceType` 复用公开 Evidence 类型闭集：`COLLECTION / DOCUMENT / SCREENSHOT / CODE / TEST_RESULT`，不使用 PROJECT/CASE 或任意字符串；来源所属主体由 `subjectRoute` 表达。

`referenceKey` 来自公开快照中专门发布的稳定 reference code（当前 Evidence `code` 可迁移为该字段），不是数据库主键、Claim/Evidence/Chunk ID。Route 只能是站内相对公开路由；不存在独立 Evidence 页面时 `evidenceRoute` 为空，由 Evidence Desk 使用 `referenceKey` 定位公开证据摘要。任何对象存储路径、未发布版本和内部身份不得进入该对象。

P3 Result Policy 先把 Bundle 转为显式不可变 `PortfolioAnswerMaterial`。该材料按 `FACT / COMPARISON / RECOMMENDATION` 使用强类型变体，但只携带 GroundedStatement、公开 Subject 标签、Caveat 和 PublicSourceReference。P1 的唯一公开入口演进为：

```java
public interface PortfolioAnswerComposer {
    PortfolioAnswerPlan compose(PortfolioAnswerMaterial material);
}
```

现有 `DeterministicPortfolioAnswerComposer` 收缩为该 Facade 的 Fact 内部策略；Comparison 和 Recommendation 由同一 Facade 内部的确定性策略处理，不对 P2 暴露多个 Composer。`PortfolioAnswerSection` 和 Recommendation item 改为绑定 `sourceReferences`，不再把 Claim/Evidence ID 当作公共引用。

旧 `compose(PortfolioIntelligenceResult)` 只允许在 P3-E 切换期间作为适配入口，P3 验收前删除。未来 P4 只能在 ValidatedEvidenceBundle/GroundedStatement 之后进行受约束改写，输出必须经过 Statement/Citation Validator；失败时回退同一 P1 Facade。

P2 的通用 `TaskOutcome` 不直接暴露 CandidateSet、ValidatedEvidenceBundle 或 Portfolio 持久化实体，而只接收领域无关的 `GroundedAnswerContribution`：

```text
supportedStatements
publicSourceReferences
caveats
omittedTopicLabels
```

Synthesis 只能组合这些 Contribution，不能重新解释原始证据、改变推荐或补写缺失事实。

现有 HTTP `claimIds/evidenceIds` 与 P3 `sourceReferences` 不长期双轨。P3-E 在一个原子后端＋内置前端发布中先让前端消费 `sourceReferences`，再删除回答契约中的内部 ID；兼容字段只允许存在于该切片的迁移提交中，不能进入 P3 最终验收。

## 12. Scheduler、依赖与预算

### 12.1 单任务 P3

P2 Scheduler 管理整个 SemanticPlan 的拓扑和回合级预算；P3 始终按单个 Portfolio 任务独立编译、检索和判定，不做跨任务超级 Query 或跨任务计划合并。

P3 v1 先按稳定拓扑顺序执行。未来可以在 P2 Scheduler 中增加有界并行，而不改变 P3 接口。

### 12.2 依赖失败隔离

依赖类型：

```text
REQUIRED_CONTEXT
OPTIONAL_CONTRIBUTION
SYNTHESIS_INPUT
```

- 独立任务继续执行，不因兄弟任务失败而中止。
- Synthesis 使用仍然有效的 Contribution，并安全说明失败项。
- 缺少强依赖的任务返回 `DEPENDENCY_UNAVAILABLE`，不进入 P3、不消耗 Capability 预算。

### 12.3 不可变 Allowance

P2 在执行前为每个任务分配不可变 `TaskExecutionAllowance`：

```text
logicalRetrievalLimit
backendAttemptLimit
evidenceUnitLimit
publicReferenceLimit
characterLimit
absoluteDeadline
```

Portfolio 任务最多一次逻辑检索、两次后端尝试；整个回合最多六次逻辑检索。General 与 Synthesis 的检索额度为零。

P3 不得追加检索、延长 deadline 或自行申请第三次尝试。预算不足的任务返回 `NOT_EXECUTED_BUDGET`。P3 v1 不运行时借用其他任务未使用的预算。

P3 首版预算已经按当前正式 Bundle（58 个主体、88 个 Claim–Evidence unit、单主体最多 14 个 Claim）冻结：单任务最多 64 个 Subject 元数据、128 个 Evidence unit、每主体 16 个 Evidence unit、96 个公开引用和 4000 个组合字符；整轮回答继续遵守现有 8000 字符上限。P2 在确定 executable task 数后按稳定拓扑等分整轮字符预算：每任务先取 `min(4000, floor(8000 / executableTaskCount))`，除法余数按稳定拓扑逐个补 1 且不突破 4000；未使用额度不在运行时借给其他任务。所有 Portfolio 任务共享请求开始后 10 秒的绝对执行截止时间，在现有 12 秒 HTTP 上限内保留 2 秒给 Context 提交和响应映射；剩余时间少于 250ms 的未开始任务返回 `NOT_EXECUTED_BUDGET`。这些是安全硬上限，不是必须填满的目标，不能由请求覆盖。

## 13. Primary/Fallback 状态机

只允许基础设施类 `UNAVAILABLE/TIMED_OUT` 触发一次固定 fallback；业务空结果不触发 fallback，数据完整性错误直接失败。

```text
Primary SUCCESS       → 使用 Primary
Primary EMPTY         → 完成但证据不足，不 fallback
Primary UNAVAILABLE   → 有预算和时间时调用 Fallback
Primary TIMED_OUT     → 有预算和时间时调用 Fallback
Primary INTEGRITY_FAIL→ 直接 FAILED
```

Primary 与 Fallback 按完整 Attempt 原子化。Primary 超时或中断时其部分材料全部丢弃；不得与 Fallback CandidateSet 混合。Fallback 仍经过完全相同的 Evidence Promotion。

Fallback 成功：`COMPLETED + degraded=true`；Fallback 空结果：`COMPLETED + INSUFFICIENT + degraded=true`；Fallback 再失败：`FAILED`。

## 14. TaskOutcome 与安全原因

P3/P2 的结果映射：

```text
COMPLETED + SUFFICIENT + COMPLETE → ANSWERED
COMPLETED + SUFFICIENT + PARTIAL  → PARTIALLY_ANSWERED
COMPLETED + PARTIAL + 可展示      → PARTIALLY_ANSWERED
COMPLETED + 任一有证据 + NONE     → PRESENTATION_BLOCKED
COMPLETED + INSUFFICIENT          → NOT_SUPPORTED
REJECTED                          → REJECTED
FAILED                            → FAILED
```

P2 还可在进入 P3 前产生：

```text
DEPENDENCY_UNAVAILABLE
NOT_EXECUTED_BUDGET
```

Portfolio 的 PARTIAL、INSUFFICIENT、REJECTED、FAILED 不得自动降级到 General Model 生成替代事实或结论。

执行层只返回封闭 `SafeReasonCode`，例如：

```text
SCOPE_CONFLICT
UNSUPPORTED_RETRIEVAL_PROFILE
CONTEXT_VERSION_UNAVAILABLE
REQUIRED_DEPENDENCY_UNAVAILABLE
TURN_BUDGET_UNAVAILABLE
EVIDENCE_NOT_FOUND
EVIDENCE_PARTIALLY_COVERED
OUTPUT_POLICY_BLOCKED
CAPABILITY_TEMPORARILY_UNAVAILABLE
EVIDENCE_INTEGRITY_FAILURE
```

用户文案由 P1 或前端基于 localization key 映射。执行层不得返回异常消息或动态拼接内部错误文本。

现有 P2 `TaskOutcome` 按最小破坏方式演进：

```text
TaskExecutionStatus
  NOT_STARTED | RUNNING | SUCCEEDED | REJECTED | FAILED | BLOCKED | CANCELLED

TaskResolution
  ANSWERED | PARTIALLY_ANSWERED | NOT_SUPPORTED | PRESENTATION_BLOCKED
  | REJECTED | DEPENDENCY_UNAVAILABLE | NOT_EXECUTED_BUDGET | BOUNDARY | NOT_APPLICABLE

TaskEvidenceState
  SUFFICIENT | PARTIAL | INSUFFICIENT | NOT_APPLICABLE
```

映射固定为：P3 `COMPLETED → SUCCEEDED`，P3 `REJECTED → REJECTED`，技术失败 → `FAILED`，强依赖缺失 → `BLOCKED + DEPENDENCY_UNAVAILABLE`，执行前无预算 → `NOT_STARTED + NOT_EXECUTED_BUDGET`。`ANSWERED/PARTIALLY_ANSWERED` 才能携带 renderable `GroundedAnswerContribution`；`PRESENTATION_BLOCKED` 不携带正文。

旧 `EMPTY` 与 task-level `CAPABILITY_UNAVAILABLE` 在 P3 迁移后删除：业务空结果统一为 `SUCCEEDED + NOT_SUPPORTED + INSUFFICIENT`；基础设施失败统一为 `FAILED` 并由 SafeReasonCode 区分。

顶层 `AnswerResolution` 新增 `PARTIALLY_ANSWERED` 和 `PRESENTATION_BLOCKED`。至少一个任务可展示但存在部分/失败任务时为 `PARTIALLY_ANSWERED`；全部可展示任务完整时为 `ANSWERED`；全部业务证据不足时为 `NOT_SUPPORTED`；全部内容因输出规则不可展示时为 `PRESENTATION_BLOCKED`；无可展示结果且存在技术失败时保留 `CAPABILITY_UNAVAILABLE`。依赖和预算细因留在 task summary，不伪装成证据不足原因。

## 15. 用户可见执行计划

内部 `PortfolioExecutionPlan` 不对外。用户看到的是安全投影 `ExecutionDisplayPlan`。

Portfolio 阶段示例：

```text
任务范围已确认
正在查找已发布材料
正在核验证据
正在形成回答
```

状态：

```text
PENDING
IN_PROGRESS
COMPLETED
PARTIAL
SKIPPED
FAILED
```

P3 v1 响应只返回最终阶段快照，最终响应不得残留 `IN_PROGRESS`。请求进行中，前端显示固定处理中骨架；SSE/WebSocket/异步实时进度以后单独设计。

DisplayPlan 由真实执行记录确定性投影，不能由模型编写，不能展示 Capability ID、Adapter、SQL、内部预算、异常或内部 ID。

保留现有 P2 `agentTurn.plan` 作为语义计划，不用 P3 原始执行计划替换它。新增同级 `agentTurn.execution`：

```text
ExecutionDisplayPlanResponse
├─ contractVersion = p3-display-v1
├─ snapshotType = FINAL
├─ overallStatus
└─ tasks[]
   ├─ displayIndex
   ├─ finalStatus
   └─ stages[] { code, label, status }
```

四个稳定 stage code 为 `SCOPE_CONFIRMED / MATERIALS_RETRIEVED / EVIDENCE_VALIDATED / RESULT_COMPOSED`。中文 label 按任务类型确定性投影：Fact 使用“确认查询范围/查找已发布材料/核验证据/形成回答”；Compare 使用“确认比较范围/收集各主体材料/检查可比较维度/整理比较结果”；Recommend/Refine 使用“确认推荐范围或上下文/评估候选条件/核对推荐依据/形成推荐结果”。

最终 task status 只使用 `COMPLETED / PARTIAL / SKIPPED / FAILED`；stage status 使用 `COMPLETED / PARTIAL / SKIPPED / FAILED`，最终响应不允许 `PENDING/IN_PROGRESS`。前端等待同步响应时显示本地固定骨架，不把骨架当作服务端真实进度。

## 16. 可观察性与公共 API

内部只记录四类脱敏完成阶段：

```text
planning
capability
evidence_promotion
result_policy
```

日志和指标只允许闭集枚举、计数/耗时 bucket、布尔值和随机请求关联 ID。禁止记录问题、`goalLabel`、Subject/Claim/Evidence/Chunk ID、查询、正文、ContentVersion 实值、异常消息、路径、SQL、主机或后端名称。

Eval 可以在进程内检查完整结构对象，但持久化报告同样不能输出正文和内部 ID。

公共 API 只返回答案 Contribution、公开引用、TaskOutcome、安全原因和 ExecutionDisplayPlan；不返回 Raw CandidateSet、Bundle、CapabilityExecutionRecord 或内部计划。

公共任务状态固定为 `COMPLETED / PARTIAL / NOT_SUPPORTED / PRESENTATION_BLOCKED / REJECTED / FAILED / DEPENDENCY_UNAVAILABLE / NOT_EXECUTED_BUDGET / CANCELLED`。前端只根据闭集状态和 SafeReasonCode 映射文案，不根据后端异常字符串猜测。

## 17. 服务端会话业务上下文

### 17.1 分层

```text
原始对话窗口
    → 只帮助语言理解，不是授权权威

ConversationBusinessContext
    → 同一会话内的强类型任务锚点

RecommendationContext
    → 推荐/Refine 的授权和规则状态

UserMemory
    → 跨会话长期记忆，P3 v1 不实现
```

### 17.2 Context Store

```java
public interface ConversationBusinessContextStore {
    ContextHandle save(ConversationBusinessContext context);
    OptionalConversationContext resolve(
            ConversationId conversationId,
            ContextHandle contextHandle);
}
```

生产上下文必须绑定会话，并在刷新和服务重启后恢复。前端只持有不透明 `contextHandle`，不能提交授权 Subject、ContentVersion 或规则状态让服务端直接信任。

上下文随会话删除/过期，不跨会话自动继承。跨会话用户记忆、保存偏好和继续上次任务以后单独设计。

### 17.3 两类 v1 上下文

`RecentSemanticTaskContext`：

```text
taskType
publicSubjectReferences
requestedFacets
comparisonDimensions
contentVersion
sourceTaskId
```

用于“再看他的项目经验”“比较他们的交付”“第二个人呢”等普通连续追问。

`RecommendationContext`：

```text
sourceTaskId
authorizedScopeBinding
contentVersion
recommendationProfileVersion
baselineRequirements
currentUserConstraints
currentPreferencePriorities
accumulatedSubjectExclusions
resultLimit
```

它不保存上一轮答案、证据、CandidateSet、Bundle 或推荐名次。

### 17.4 Context 解析

P2 模型只读取裁剪后的 `ConversationContextView`，并输出受控 `ContextReference`：

```text
LATEST_COMPATIBLE
ACTIVE_RECOMMENDATION
SOURCE_TASK_ID
EXPLICIT_CONTEXT_HANDLE
```

服务端 Resolver 确定性解析真实 Context，再由 Validator 校验归属、类型、版本与范围。存在多个同样可能的上下文时要求 Clarification，不让模型根据聊天文本猜测和重建范围。

### 17.5 不可变版本链

RecommendationContext 不原地修改。每次 `COMPLETED` 的 Recommendation/Refine 创建新 Context，保存 `parentContextHandle`。默认继续最新版本；用户明确选择旧结果时允许从旧版本合法分支。

`SUFFICIENT/PARTIAL/INSUFFICIENT/PRESENTATION_BLOCKED` 均可保存 Context，因为保存的是授权和规则状态，不是结论。`REJECTED/FAILED/DEPENDENCY_UNAVAILABLE/NOT_EXECUTED_BUDGET` 不更新最近成功上下文。

### 17.6 页签内刷新恢复令牌

P3 使用服务端签发的随机、不透明 `conversationResumeToken` 关联同一页签中的服务端业务上下文：

```text
浏览器 sessionStorage：conversationResumeToken
服务端：token hash → conversationId → ConversationBusinessContext
```

浏览器不保存问题、答案、SubjectScope、ContentVersion、推荐规则、Evidence 或 Context 正文。Token 不编码任何业务状态，不能由前端构造，不进入 URL、浏览器历史或日志。

选择 `sessionStorage` 而不是 `localStorage` 或 Cookie：同一页签刷新后可以恢复；关闭页签、新建页签、换浏览器或换设备时不自动继承。该机制不形成跨会话身份或长期用户画像。

前端已有的多个本地 `AgentSession` 必须分别对应不同的服务端 conversation，不能因为它们位于同一浏览器页签就共享一个 ResumeToken。运行期每个本地会话只在内存中绑定自己的 Token；`sessionStorage` 只保存当前活跃会话的 Token。切换会话时同步替换该槽位，新建会话时清空该槽位，因而刷新后最多恢复刷新前的活跃会话。删除某个本地会话前必须清除它对应的服务端 conversation；清空全部本地会话时逐个幂等清除尚存的 Token。

Token 必须使用足够随机的不可预测值；服务端只保存散列值，逐请求验证归属，并支持用户主动“清除本次对话”后立即失效。服务端遗留 Context 按 TTL 清理。Token 只定位公开作品集会话状态，不是用户身份凭证，也不能获得私有数据访问权。

### 17.7 生产 Context Store

生产环境使用 PostgreSQL 实现 `ConversationBusinessContextStore`；`InMemoryConversationBusinessContextStore` 仅用于单元测试、Eval 和明确的本地开发模式。v1 不增加 JSON 文件、H2、SQLite 或其他本地持久化 fallback。

Context Store 与 Portfolio Retrieval Backend 是两条独立基础设施职责：即使公开证据检索使用 Bundle，服务端业务上下文仍写入 PostgreSQL，不能随 Retriever 模式切换为内存存储。

持久化字段仅限会话和强类型业务状态，例如：

```text
conversation_id
resume_token_hash
context_handle
context_type
parent_context_handle
source_task_id
content_version_binding
typed_context_payload
created_at
expires_at
superseded_by
```

不得保存问题原文、助手答案、Prompt、CandidateSet、EvidenceBundle、Evidence 正文、模型输出、浏览器指纹或用户画像。生产不提供本地文件 fallback，避免 PostgreSQL 故障时形成多实例不一致的第二状态源。

### 17.8 Context Store 故障与可续接性

Context Store 故障采用不对称降级：

- 当前任务不依赖旧 Context，只在完成后需要保存新 Context：事实/比较/推荐结果继续正常返回；Context 写入失败只设置 `degraded=true`、`ConversationContinuationStatus.PERSISTENCE_UNAVAILABLE` 和安全原因 `CONTEXT_PERSISTENCE_UNAVAILABLE`，不降低已经成立的 `EvidenceSupport` 或 `AnswerCoverage`。
- 当前任务强依赖旧 Context，例如 Refine 或明确的连续任务：Context Store 无法读取时失败关闭，不进入 P3 Retrieval，不从前端聊天历史重建授权范围；返回 `FAILED + CONTEXT_STORE_TEMPORARILY_UNAVAILABLE`，并标记可重试。

必须区分：

```text
DEPENDENCY_UNAVAILABLE
= Context 确实不存在、过期、已清除或不属于当前会话

CONTEXT_STORE_TEMPORARILY_UNAVAILABLE
= Context 可能存在，但当前基础设施无法读取
```

公共响应增加独立的 `ConversationContinuationStatus`：

```text
AVAILABLE
PERSISTENCE_UNAVAILABLE
CONTEXT_EXPIRED
CONTEXT_CLEARED
NOT_APPLICABLE
```

可续接性状态不替代 TaskOutcome，也不允许 Context 故障触发 General Model 或前端历史猜测。

### 17.9 保留、续期与主动清除

P3 v1 默认使用双重保留期限：

```text
空闲 TTL：24 小时
绝对 TTL：7 天
```

每次成功解析并合法使用 Context，可以把空闲期限重新延长到 24 小时；绝对期限从会话创建时计算，不能通过持续请求延长。无效 Token、归属错误、被拒绝请求和健康检查不得刷新 TTL。两个数值为受控配置，不接受请求参数覆盖。

用户执行“清除本次对话”时，服务端立即、幂等地删除 ResumeToken 映射和该会话的全部 Context 版本，前端同时删除 `sessionStorage` Token。

空闲或绝对期限到期后，后台清理任务物理删除 Context，不归档、不转存日志，也不保留作行为分析。过期 Token 再次请求时返回 `CONTEXT_EXPIRED`，前端清除旧 Token 并开始新会话，不尝试恢复旧 Context。

### 17.10 Context 容量与确定性清理

P3 v1 设置以下硬上限：

```text
单个 typed context payload：最大 16 KiB
单个 conversation：最多保留 32 个 Context 版本
```

写入新版本前超过会话上限时，按以下顺序在同一受控操作中清理：

```text
1. 最旧的非活跃 RecentSemanticTaskContext
2. 最旧的非活跃 Recommendation 分支
3. 最旧且不再活跃的 Recommendation 祖先版本
```

当前 Active Context、当前请求明确引用的父 Context 和本次待写入的新 Context 不得被清理。RecommendationContext 每个版本保存完整当前状态，不依赖父版本 diff 才能执行；被删除父版本的 handle 只保留为历史标识。

访问已被容量清理的旧 Context 时返回 `CONTEXT_PRUNED`，并映射为 `ConversationContinuationStatus.CONTEXT_EXPIRED`，不得从旧答案文本重建状态。完成确定性清理后仍无法满足限制时，当前单轮答案可以返回，但 Context 保存失败并标记不可续接；禁止扩大上限、静默截断 payload 或使用模型摘要压缩旧 Context。

### 17.11 Token 摘要与 Context Payload 加密

ResumeToken 使用 256-bit 随机值，数据库不保存原 Token，只保存使用独立 Token 密钥计算的 `HMAC-SHA-256(token)` 摘要。

`typed_context_payload` 不以明文 JSON 保存。强类型 payload 经过规范化序列化后使用 AES-256-GCM 应用层信封加密，并把 `conversationId`、`contextHandle`、`contextType`、`schemaVersion` 作为关联数据，防止密文被复制到另一条 Context 记录后继续生效。

数据库只明文保存定位、生命周期和解密所需的最小字段：

```text
context_handle
context_type
parent_context_handle
created_at
idle_expires_at
absolute_expires_at
schema_version
encryption_key_id
nonce / ciphertext / authentication_tag
```

SubjectReferences、ContentVersion binding、Facet/Dimension、推荐约束、偏好顺序和排除项均进入加密 payload。

Token HMAC 密钥与 Payload Encryption 密钥必须分离，来自正式 Secret 配置，不写入仓库、数据库或日志。每条记录保存 `encryption_key_id`；新写入只使用当前密钥，服务端至少保留覆盖 7 天绝对 TTL 的旧读取密钥。

PostgreSQL TLS、最小账号权限、备份保护和磁盘加密仍是独立要求。Payload 认证或解密失败时不得返回部分 Context、解析残缺 JSON 或从聊天历史恢复；依赖该 Context 的任务返回 `FAILED + CONTEXT_INTEGRITY_FAILURE`。

### 17.12 Payload Schema 演进

每种 Context 使用独立、封闭、版本化的 JSON Codec，例如 `RecentSemanticTaskContextCodec` 和 `RecommendationContextCodec`。禁止 Java 原生序列化、Java 类名元数据、默认多态反序列化、任意 Map 和模型生成迁移内容。

Payload 明确包含 `contextType`、`payloadSchemaVersion` 和白名单 typed fields。v1 Reader 至少支持当前版本 N 与上一版本 N-1：N 直接验证读取；N-1 通过确定性 Migrator 转为当前内存对象。未知旧版本、未来版本、未知字段、未知枚举或迁移失败均返回 `CONTEXT_SCHEMA_UNSUPPORTED`，不得部分读取、猜默认值、删除原记录或回读聊天历史。

普通读取不原地重写旧密文。成功 Refine 后创建的新子 Context 使用当前 Schema；旧版本在 7 天绝对 TTL 内自然清理。

部署采用先扩 Reader、后切 Writer 的顺序：所有运行实例先具备新 Schema 的读取能力，之后才允许写入新 Schema。不得在旧实例仍在线且无法读取时提前切换 writer schema version。

### 17.13 并发 Refine 与 Active 指针

同一父 RecommendationContext 上的并发 Refine 可以分别形成合法不可变分支，但禁止使用最后写入覆盖 Active Context，也禁止自动合并两次 Refine 的约束。

保存子 Context 时携带 `parentContextHandle` 与 `expectedActiveContextHandle`。新 Context 插入和 Active 指针 compare-and-set 必须在同一 PostgreSQL 事务中完成：第一个成功请求推进 Active；后续并发请求仍保存自己的合法子 Context，但不覆盖 Active，并返回安全原因 `CONTEXT_BRANCH_CREATED`。

前端从某条推荐结果继续时应显式携带该结果的 ContextHandle；只有没有明确结果关联的“继续调整”才解析服务端 Active Context。非活跃分支仍可通过明确 Handle 继续，不因失去 Active 状态而失效。

### 17.14 ResumeToken HTTP 传输

首次无 Token 请求完成后，公共响应的 `conversation.resumeToken` 字段返回服务端签发的随机不透明 Token，前端写入 `sessionStorage`。后续请求只通过 `X-Conversation-Resume-Token` Header 携带，不进入 URL、Query、路由、Cookie 或用户问题字段。

Token 在同一会话生命周期内保持稳定，P3 v1 不逐请求轮换，避免并发、乱序响应和网络重试互相使 Token 失效；用户主动清除时立即失效。

所有回答响应设置 `Cache-Control: no-store`。应用、反向代理、访问日志、前端错误上报和可观察性链路都必须对 ResumeToken Header 与响应字段脱敏。

ContextHandle 不是独立凭证。解析任何 Context 必须同时验证 ResumeToken 对应的 conversationId；只有 Handle、没有合法 Token，或 Token 与 Handle 不属于同一会话时一律拒绝。

### 17.15 刷新后的安全恢复卡

由于服务端不持久保存问题、答案和聊天气泡，页面刷新后不恢复或伪造历史对话。前端使用 ResumeToken 请求由强类型 Context 确定性投影的 `ConversationContextSummary`，显示“已恢复本次对话的业务上下文”卡片。

恢复卡只允许展示最近任务类型、公开主体名称、公开 Facet/Dimension 标签、当前推荐偏好的公开标签、是否可继续 Refine，以及“清除本次对话”操作。禁止展示原问题、原答案、Evidence、推荐理由、内部 ContextHandle、ContentVersion 实值、工具/执行信息或模型生成摘要。

Context Summary 由服务端 `SafeContextSummaryProjector` 从已验证的业务 Context 生成，不使用模型。Context 已过期、Token 无效或归属失败时，前端清除 `sessionStorage`，不显示恢复卡，并按新会话开始。

### 17.16 访客告知与清除入口

同会话强类型业务上下文持久化采用“清晰告知 + 随时清除”，不增加阻断式同意弹窗、Cookie Banner 或必须勾选的显式同意流程。

访客首次使用 Agent 前或输入区附近必须持续可见地说明：系统会短期保存任务范围和偏好以支持刷新恢复与连续追问；不保存问题原文、助手答案或证据正文；默认 24 小时空闲过期、最长 7 天；关闭页签后不会跨页签或跨设备自动恢复。完整隐私说明提供可访问链接。

“清除本次对话”入口在正常会话和恢复卡中始终可用。清除后服务端立即、幂等删除 ResumeToken 映射和全部 Context 版本，前端删除 `sessionStorage` Token、恢复卡和当前页签内对话 UI 状态；后续请求创建新会话。

告知文案不得声称保存完整聊天记录，也不得把短期业务上下文描述成登录账号、长期记忆或个性化画像。权威隐私文档、`AGENTS.md` 与前端说明必须在生产启用前同步更新。

### 17.17 请求与 Context 写入幂等

复用现有 `ConversationAnswerRequest.requestToken` UUID 作为请求幂等 ID，不新增 `X-Conversation-Request-Id` Header。生产调用方必须使用加密安全随机 UUIDv4；未过期 receipt 对 `requestToken` 建立全局唯一索引，解析后再校验并绑定 `conversationId + requestToken`、规范化请求结构、父 ContextHandle 和 ContentVersion binding 的安全摘要。现有来源地址 Hash 只用于 Admission/Rate Limit，不再作为业务幂等身份。

全局唯一索引用于首轮响应丢失恢复：首次请求还没有 ResumeToken，重试仍能按不可预测的 requestToken 找到原 conversation 的 receipt。若完成重试没有携带 ResumeToken，服务端为同一 conversation 原子重签 ResumeToken、替换旧 token hash，并在完成回执中返回新 Token；可能迟到的旧 Token 随即失效。若重试已经携带并通过当前 ResumeToken，则不重签。任何相同 requestToken 与另一合法 ResumeToken 或不同请求指纹组合都返回 `IDEMPOTENCY_KEY_CONFLICT`。

同一幂等键且请求指纹相同时：执行中的重试返回 `REQUEST_IN_PROGRESS`；已经完成的重试不重新执行 P2/P3、不创建第二个 Context，也不重复推进 Active。相同幂等键对应不同请求指纹时返回 `IDEMPOTENCY_KEY_CONFLICT`，不得执行。

HTTP 映射固定为：执行中重试和幂等冲突均返回 409，使用现有安全错误 envelope；`REQUEST_IN_PROGRESS` 可返回 `retryAfterSeconds`。已完成重试返回 200 `COMPLETION_RECEIPT`，因为它是已经提交成功的业务回执，不是冲突或失败。

幂等记录不持久化问题原文、完整答案、Evidence 或可重放的回答正文。若首次响应丢失，后续重试只返回 `REQUEST_ALREADY_COMPLETED`、TaskOutcome 完成状态、新 ContextHandle 和 ContinuationStatus；前端不得伪造原回答，可以提示用户基于已保存上下文继续或重新提问。

公共 HTTP 响应使用显式联合类型避免把“完成回执”伪装成空答案：正常回答为 `responseKind = ANSWER`；命中已完成 receipt 时返回 `responseKind = COMPLETION_RECEIPT`，只携带 `turnId`、`requestToken`、`requestStatus = REQUEST_ALREADY_COMPLETED`、公开任务完成状态、可选 ContextHandle 和 `conversation`，不得填充伪造的 title、blocks、推荐正文或引用。正常回答和完成回执均使用 200；调用方必须先按 `responseKind` 分流。

幂等记录的保留期限不得超过所属会话的绝对 TTL，用户清除会话时一并物理删除。

现有内存 `AnswerIdempotencyCoordinator` 继续承担进程内并发合并，但 Context Store 增加持久 request receipt，使用短租约的 `IN_PROGRESS / COMPLETED` 状态约束跨请求和服务重启后的 Context 写入。Producer 崩溃后只有租约过期才能重新执行；已经 `COMPLETED` 的 receipt 永不重复创建 Context。receipt 与 Context 插入/Active CAS 在同一数据库事务中完成。

### 17.18 普通任务 Active Context

每个会话只维护一个 `ACTIVE_FACT_CONTEXT` 和一个 `ACTIVE_COMPARE_CONTEXT`，与 `ACTIVE_RECOMMENDATION` 并列。Fact/Compare 得到 `ANSWERED / PARTIALLY_ANSWERED / NOT_SUPPORTED / PRESENTATION_BLOCKED` 时创建不可变 Context 并替换对应 Active 指针；`REJECTED / FAILED / DEPENDENCY_UNAVAILABLE / NOT_EXECUTED_BUDGET` 不更新。

默认语言指代只考虑当前 UI 显式 ContextHandle、三个 Active 槽位和唯一兼容性；旧 Fact/Compare Context 仅在显式 Handle 引用时可用，不建立父子分支链。容量清理时优先删除非活跃普通 Context，当前 Active Fact/Compare 不删除。

解析优先级固定为：UI 明确 Handle → 唯一类型兼容 Active → 最近创建的唯一兼容 Active → 多个同等候选时 Clarification。模型不能改变该优先级。

### 17.19 Context 公共 API

保留 `POST /api/v2/answers` 作为唯一回答入口。请求继续使用现有 `requestToken`，ResumeToken 通过 Header 携带，并在请求顶层新增可选的强类型 `contextReference { contextHandle, expectedContextType }`；`expectedContextType` 只有 `RECENT_SEMANTIC_TASK / RECOMMENDATION`。现有由前端回传完整 `recommendationContext/referenceContext` 的做法在 P3-E 删除。

回答响应新增：

```text
responseKind = ANSWER

conversation
├─ resumeToken（仅首次签发或明确替换时返回）
├─ continuationStatus
└─ activeContextSummary（可选）

agentTurn.completedTasks[].contextHandle（仅产生可续接 Context 的任务）
```

`POST /api/v2/answers` 的另一种成功响应是 `responseKind = COMPLETION_RECEIPT`，仅用于相同 `requestToken` 的已完成重试，字段和前端行为按 17.17 执行。P3-E 后公共返回体必须以 `responseKind` 作为第一层判别字段，不能继续依赖“有没有 blocks”猜响应类型。

新增 `GET /api/v2/conversation-context`：使用 ResumeToken Header 返回 `p3-context-summary-v1` 的 `ConversationContextSummary`，不返回历史消息或 Payload。可恢复时返回 200 `AVAILABLE + summary`；Token 不存在、过期、已清除或归属失败统一返回 200 `CONTEXT_EXPIRED` 且不带 summary，避免泄露存在性；格式非法返回 400 `INVALID_CONVERSATION_RESUME_TOKEN`。新增幂等 `DELETE /api/v2/conversation-context`：语法合法 Token 无论是否存在都清除当前 Token 映射、全部 Context/receipt，并返回 204；格式非法返回 400。前端在确认 204 后清除 sessionStorage 和页签 UI。

ContextHandle 为随机不透明定位符，不含业务语义；它必须与 ResumeToken 联合校验。Context Summary 和所有 DTO 都启用未知字段失败、长度上限和闭集枚举校验。

## 18. 旧链路迁移

生产执行必须从：

```text
ConversationToolService
→ modelPort.planTools
→ ToolCall / ToolKind
→ PublicKnowledgeTools
```

迁移为：

```text
P2 TurnRouter
→ SemanticTaskExecutionContext
→ PortfolioExecutionPlanner
→ PortfolioEvidenceCapability
→ CandidateSet
→ ValidatedEvidenceBundle
→ Result Policy
```

`PortfolioSemanticTaskExecutor` 不再调用 `PortfolioIntelligence.resolveTypedTask`。Eval 也必须迁移到相同 seam，之后删除旧 `resolveTypedTask/tryResolve` 决策入口和无剩余职责的旧工具类型。

不允许长期 feature flag 双轨，不把新 Engine 包回旧 `ConversationToolService`。

实施切片保持六段：

```text
P3-A：P2–P3 seam、Allowance、ScopeBinding、Display/Context 模型
P3-B：Catalog、Planner、Trusted Plan
P3-C：CandidateSet、Evidence Promotion、Validated Bundle
P3-D：唯一 Retrieval Capability、Retriever Adapter 与 Failover 合同
P3-E：生产切流、Result Policies、P1/API/Display 前端接入
P3-F：Eval 迁移、旧链删除、权威文档与隐私边界同步
```

### 18.1 启动配置与发布切换

P3 不新增可长期按请求分流的 `portfolio.execution.enabled` 双轨开关。P3-A 至 P3-D 在不可达的新 seam 中开发；P3-E 通过一次启动时 wiring 切换使新 Engine 成为唯一生产路径；P3-F 随即删除旧路径。回滚依靠发布版本回滚，不依靠运行时随机分流。

Context Store 使用独立配置：

```text
portfolio.conversation-context.mode = DISABLED | IN_MEMORY | POSTGRESQL
portfolio.database.context.*
portfolio.conversation-context.crypto.*
```

`IN_MEMORY` 只允许 test/local；P3 生产 wiring 只接受 `POSTGRESQL`。缺少 DataSource、HMAC/AES 密钥或 Schema 配置时启动失败，不能静默使用内存。配置完整但数据库运行中暂时不可用时，按 17.8 的不对称降级处理，并让 continuation readiness 报告不可用；P3 正式验收要求数据库健康。

Context DataSource、账号和 Flyway history 与公开内容检索数据库逻辑隔离，即使物理上使用同一 PostgreSQL 集群也使用独立 schema/最小权限账号。当前 `DataSourceAutoConfiguration/FlywayAutoConfiguration` 排除策略必须在 P3 实施计划中按专用 Context 配置调整，不能意外接管其他数据库。

发布顺序固定为：数据库 schema 与 Secret → 权威隐私文档/页面告知 → 支持新 DTO 的内置前端 → P3 后端唯一 wiring → 验证 Context/清除/降级 → 删除旧 ID 与旧执行链。后端与内置 SPA 作为同一 JAR 原子发布，不维持长期旧客户端兼容层。

### 18.2 公共契约迁移

P3-E 为公共响应引入 `responseKind` 联合类型、`sourceReferences`、`agentTurn.execution`、`conversation`、task contextHandle、`PARTIALLY_ANSWERED` 和 `PRESENTATION_BLOCKED`。前端映射层先支持这些闭集字段，再移除对完整 recommendationContext、claimIds/evidenceIds 和旧 TaskResolution 的依赖。

迁移提交可以短暂同时填充新旧字段以通过原子切换测试，但 P3 完成定义要求：生产前端只读取新字段，后端不再输出内部 Claim/Evidence ID，旧 `ConversationToolService/PortfolioIntelligence` 决策入口不可达，Eval 只使用新 seam。

## 19. 验证体系

五层验证：

1. 纯 Planner/Domain：确定性、范围、排除、Profile、Allowance、Context 引用。
2. Evidence Promotion：身份、版本、状态、公开性、原子关系、重复冲突、预算截断。
3. Retriever Contract：Bundle/PostgreSQL/Failover 输出相同 CandidateSet 合同，空结果不 fallback，Attempt 不混合。
4. Production Integration：P2→P3→P1/API/Display 真实可达，旧链不可达。
5. Eval：与生产完全相同 seam，报告脱敏。

必须覆盖：

- Fact/Compare/Recommend/Refine 的支持矩阵；
- Exact 与 All-Published scope；
- Refine 不扩张、旧 ContentVersion 不可用；
- 负证据与缺证据区分；
- 私有/草稿材料导致完整性失败；
- Primary/Fallback 原子状态机；
- `PRESENTATION_BLOCKED` 与输出排除；
- 依赖失败隔离和 `NOT_EXECUTED_BUDGET`；
- Context 会话归属、版本链、旧分支、歧义澄清；
- DisplayPlan 最终快照与脱敏；
- GoalLabel、原问题和模型关键词无法影响 Invocation。

## 20. 当前规则体系盘点

按“可独立实现、测试和版本化的 Policy”计算，目前约有 16 套核心规则：

### 20.1 跨场景公共规则（12）

1. Capability Catalog。
2. 执行计划编译。
3. 主体范围授权。
4. ContentVersion。
5. Facet/Dimension Profile。
6. Exclusion。
7. 检索预算。
8. Fallback。
9. CandidateSet 合同。
10. Evidence Promotion。
11. 去重与 Claim–Evidence 原子截断。
12. 执行、支持度与展示状态判定。

### 20.2 业务场景规则（4）

1. Fact 支持与结果策略。
2. Compare 可比性与结果策略。
3. Recommendation 准入、分层与公平预算。
4. Refine 上下文继承、约束变更与重新检索。

另有三套外围治理规则：DisplayPlan、脱敏可观察性、五层 Eval/验收。

这些规则必须集中在少量深模块中，不能散落成大量 Controller/Adapter `if/else`。

## 21. 已冻结实施参数与明确延后项

用户已授权剩余设计问题采用本文推荐方案直接收敛。当前没有阻塞 Spec 批准的产品/架构未决项；以下数值与物理实现参数已随实施计划冻结：

### 21.1 已冻结的实施参数

后端实施计划已经冻结以下首版参数，并要求用边界测试和 PostgreSQL 集成测试验证：

1. 执行预算：64 Subject、128 Evidence unit、每主体 16 unit、96 PublicSourceReference、单任务 4000 字符、整轮 8000 字符、请求开始后 10 秒绝对执行截止时间、250ms 最小启动窗口。
2. Context 清理：每 15 分钟触发一次，每批最多物理删除 500 个过期 conversation；同一实例单线程执行，多实例通过 PostgreSQL advisory lock 只允许一个清理者生效。
3. Context Store：独立 `agent_context` schema、`flyway_schema_history_context`，表名固定为 `conversation_session / conversation_context / conversation_active_context / conversation_request_receipt`；具体列、约束和索引见后端实施计划，必须遵守本文事务、加密、TTL、容量和 CAS 规则。

若正式 Eval 证明任一上限不能覆盖已批准用例，只能通过 Spec 修订提高；不得在运行时静默放宽或使用用户参数覆盖。

### 21.2 明确延后，不阻塞 P3 v1

1. Timeline 是否成为第二 Capability；只有 P2 正式增加 `TIMELINE` facet 后才讨论。
2. SSE/WebSocket/异步实时进度传输。
3. 跨会话长期用户记忆、用户画像和多设备恢复。

## 22. 本文档维护规则

后续每确认一个问题：

1. 更新对应设计章节；
2. 若推翻旧决策，明确记录替代关系，不保留互相冲突的双重结论；
3. 新增尚未确认的问题只进入“已知未决项”；
4. 不把设计中状态写成已实现；
5. 实施前再次对照 `AGENTS.md`、权威文档索引、当前实现状态和隐私边界。

本文当前只代表 P3 设计决策已经持续收敛，不代表代码已实现、生产已启用或持久化边界已经完成批准。
