# P5 Agent 混合回答、上下文与运行模式设计
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

> 日期：2026-08-13
> 状态：讨论中，尚未批准实施
> 对应路线图：`docs/13-Agent对话体验与智能编排改造路线图.md` 阶段 5
> 前置阶段：P0、P1、P2、P3、P4 已完成
> 本文用途：持续记录 P5 讨论形成的设计决议；每完成一个主题即增量更新
> 约束：当前仅形成设计，不授权实施；前端只定义必须消费的公共契约，不规定组件、布局、交互或视觉方案

## 1. 背景与范围校正

原路线图将 P5 定义为“混合问题、多轮上下文、配置与检索矩阵”。结合 P0—P4 当前生产代码，P5 不能再被理解为从零建设这些能力：

- P2 已经具有 `GENERAL`、`PORTFOLIO`、`SYNTHESIS` 任务域，多任务计划、依赖、部分成功和确认/澄清协议；
- P3 已经具有只读 Portfolio 执行链、Evidence Promotion、确定性降级、短期加密业务 Context、ResumeToken 和请求回执；
- P4 已经建立 Portfolio 模型表达边界、严格 Codec/Validator、模型表达前的 `GroundedAnswerContribution` 和原子 fallback；
- 前后端已经接受 `MIXED`、`MIXED_COMPOSITION` 和任务级 `SYNTHESIS_RESULT`，但这些主要是类型与聚合能力，不等于真实跨域综合。

P5 的核心因此调整为：

> 将 P2 的语义任务图、P3 的可信 Portfolio Material 与短期 Context、P4 的受约束表达连接成一个来源清晰、逐句可追溯、可部分成功、可安全降级的跨域回答系统。

P5 同时承担若干跨阶段契约收口。这些收口不是重做 P0—P4，而是修复只有进入混合问题后才会显现的组合语义缺口。

## 2. 当前实现的关键事实

### 2.1 当前 `MIXED` 只是聚合状态

当同一回合同时出现可渲染 General 与非 General 结果时，运行时会投影：

```text
generationMode = MIXED
constructionMode = MIXED_COMPOSITION
```

该状态只证明存在多种生成或来源结果，不证明这些结果之间形成了新关系。

### 2.2 当前 Synthesis 不是实质综合

`DeterministicSynthesisTaskExecutor` 当前主要完成：

1. 收集成功的上游结果；
2. 读取上游 Block 或模型表达前的 `supportedStatements`；
3. 去重并按顺序拼接；
4. 聚合来源 Task 与旧 Claim/Evidence ID。

它不会形成“通用原理如何对应项目实现”“项目做法与通用实践有何差异”等受约束关系。

### 2.3 页面主体仍可能吞掉 General 意图

前端通常把当前 Project/Case 作为 `semanticContext.activeSubjects` 发送。当前确定性信号规则在识别到“解释、原理、是什么”等 General 表达后，如果上下文已有 Portfolio 主体，会删除 General Goal 并改为 Portfolio Fact。

这使页面位置实质上改变用户意图，与“页面主体只是范围提示”的目标不一致。

### 2.4 模型分类器尚不是完整语义规划器

当前可选模型分类器主要在确定性主体解析失败时提出受控主体候选；Goal 收集、任务类型和依赖仍主要由确定性关键词规则决定。P5 不能假定跨域 Goal 已由模型分类闭环实现。

### 2.5 P3 Context 基础存在，但 Recent Context 尚未进入路由

`RecentSemanticTaskContext` 已保存任务类型、公开主体、Facet、Dimension 和 `contentVersion`。但它被授权为 `AuthorizedContextReference` 后，除 Recommendation Scope 外，Recent Context 的强类型业务数据没有投影给路由器。

当前还存在：

- 普通自由追问不发送 `contextReference`，只有显式“从结果继续”才发送；
- General Executor 使用空 `ConversationWindow`，没有消费前端携带的历史消息；
-成功 Refine 当前不会提交新子 Context；
- “第二个”所需的有序结果集没有强类型 Context。

### 2.6 当前支持与引用粒度不足

`GroundedAnswerContribution` 将 `supportedStatements` 和 `publicSourceReferences` 分别保存为两个列表，没有逐陈述绑定关系。General 结果主要仍是字符串 Block。

当前响应 Mapper 还会跨整份回答删除后续 Block 中重复的公开引用，这会破坏“每个 Block 自己由什么支持”的语义关系。

### 2.7 混合回答的顶层状态可能误导

当前运行时存在两项组合问题：

- 任一执行任务真正失败时，顶层可能优先投影为 `CAPABILITY_UNAVAILABLE`，即使其他任务已有可渲染结果；
- 混合回答存在可渲染结果时，顶层 `evidenceState` 可能被投影为 `VERIFIED`，容易暗示 General 与 Synthesis 内容也由 Portfolio Evidence 直接验证。

此外，响应 Mapper 当前把所有非 Portfolio Block 映射成 `GENERAL`，包括 Synthesis Block。

### 2.8 检索模式尚未形成一致契约

配置中虽然存在 `DISABLED`、`KEYWORD_ONLY`、`HYBRID`，但 Bundle 非精确检索固定请求 Hybrid；Embedding 不可用时内部退回 Keyword，而 Bundle 顶层结果没有完整传播该降级状态。内容后端、搜索策略、向量可用性和后端 failover 目前仍存在概念耦合。

## 3. P5 设计原则

1. 页面位置提供主体候选，不自动决定用户意图。
2. 多来源回答不自动等于跨域综合。
3. Portfolio 事实只能来自 P3 已验证材料。
4. General 知识不得伪装成 Portfolio 公开证据。
5. Synthesis 只能表达服务端预先允许的跨域关系。
6. 下游任务消费强类型 Material，不消费最终 UI Block。
7. 模型调用前必须已经存在安全的确定性 fallback。
8. 模型输出必须整体通过确定性校验，否则原子丢弃。
9. 某个来源或 Synthesis 失败，不得抹掉其他已经成功的独立任务结果。
10. 短期业务 Context 不保存访客问题、回答正文或自由模型文本。
11. 前端实现交给独立前端设计；本文只规定公共数据契约和字段语义。
12. 旧协议采用增量兼容迁移，不在 P5 起步时一次性删除。

## 4. P5 建议分段

### 4.1 P5.0：跨阶段契约收口

- 页面主体恢复为弱提示；
- 修正纯 General 被页面主体改写的问题；
- 修正部分成功的顶层 Resolution；
- Synthesis 使用真实 `SYNTHESIS` 来源域；
- Task/Block 级支持关系成为权威；
- 授权后的 Recent Context 进入路由；
- Refine 能形成受限子 Context；
- 校正当前状态文档中的过期陈述。

### 4.2 P5.1：可靠多来源回答

同一问题可以稳定产生 General 与 Portfolio 两个独立任务，分别返回来源明确的内容。此阶段不要求一定生成跨域结论。

### 4.3 P5.2：受约束跨域综合

引入独立跨域组合边界、关系候选、关系 Policy、严格 Draft Schema、确定性 Validator 和原子 fallback。

### 4.4 P5.3：指代、结果集与版本语义

收口“它”“这个项目”“第二个”“刚才推荐的那个”，区分身份型引用与结果型引用，并为内容版本变化定义重新验证或固定版本语义。

### 4.5 P5.4：配置、有效状态与检索矩阵

拆分操作级模型能力，区分内容后端、检索策略、向量能力和后端 failover，并暴露真实有效运行状态。

## 5. 决议一：页面主体、显式引用与用户意图区分

### 5.1 基本规则

页面主体只提供 `HINT`，不能独立把 General 意图升级或改写成 Portfolio 意图。

应区分：

| 维度 | 语义 |
|---|---|
| 主体候选 | 用户可能正在谈论哪个公开主体 |
| 用户意图 | 用户要求通用解释、项目事实、比较、推荐还是综合 |
| 检索约束 | 如果确实创建 Portfolio Task，只允许在哪些主体中检索 |

### 5.2 建议的主体绑定角色

路由内部新增轻量绑定对象，公共 `SubjectReference` 暂不改变：

```java
record ResolvedSubjectBinding(
        SubjectReference subject,
        SubjectBindingRole role,
        SubjectResolutionSource source) {}

enum SubjectBindingRole {
    HINT,
    EXPLICIT,
    DEICTIC,
    RESULT_BOUND
}
```

语义：

- `HINT`：页面 Project/Case 等弱提示，不独立触发 Portfolio Task；
- `EXPLICIT`：用户本轮直接点名的主体；
- `DEICTIC`：用户使用“这个项目/该案例”等明确指代，并由唯一可信上下文完成解析；
- `RESULT_BOUND`：来自显式结果引用或授权 Context 的主体。

### 5.3 路由行为

| 用户问题 | 页面状态 | 计划结果 |
|---|---|---|
| 什么是乐观锁？ | Project A 页面 | General Explanation；A 只作 Hint |
| 这个项目用了乐观锁吗？ | Project A 页面 | Portfolio Fact(A) |
| 解释乐观锁，以及这个项目怎么使用 | Project A 页面 | General + Portfolio(A)，是否增加 Synthesis 由关系意图决定 |
| 乐观锁和悲观锁有什么区别？ | Project A 页面 | General Comparison |
| 这个项目如何处理并发？ | 无唯一主体 | 澄清，不猜测 |
| Project B 如何处理权限？ | Project A 页面 | 显式 B 优先，A 不制造冲突 |

### 5.4 暂定决议

1. 页面主体是 `HINT`，不是默认任务绑定。
2. 纯 General 问题不因页面主体改变。
3. 明确指代可以把唯一页面主体提升为 `DEICTIC`。
4. 文本显式主体优先于页面主体。
5. 一个问题可以同时包含 General Topic 和 Portfolio Subject。
6. 路由内部引入 `ResolvedSubjectBinding`，公共 `SubjectReference` 暂不变。
7. 删除“General Explanation + 存在主体就移除 General Goal”的现有规则。
8. 主体来源和绑定角色进入安全诊断与 Eval，不记录或输出用户原文。

## 6. 决议二：跨域问题的任务规划

### 6.1 多来源与跨域综合分离

多来源问题分为：

#### 并列型

```text
解释乐观锁，再介绍 Project A 的并发控制。
```

计划：

```text
Task 01 GENERAL_EXPLANATION
Task 02 PORTFOLIO_FACT
```

不自动创建 Synthesis。

#### 关系型

```text
解释乐观锁，并说明它在 Project A 中如何使用。
```

计划：

```text
Task 01 GENERAL_EXPLANATION
Task 02 PORTFOLIO_FACT
Task 03 SYNTHESIS(CONCEPT_APPLICATION)
```

#### 判断型

```text
与通用实践相比，这个项目的做法是否合理？
```

同样需要 General、Portfolio、Synthesis，但使用更严格的判断型 Policy、限制和 Caveat。

### 6.2 Synthesis 参数闭集

保留公共 `SemanticTaskType.SYNTHESIS`，增强内部参数：

```java
record SynthesisParameters(
        List<String> sourceTaskIds,
        SynthesisKind synthesisKind,
        SynthesisInputRequirement inputRequirement,
        Set<RequestedRelation> requestedRelations,
        SynthesisOutputShape outputShape) {}
```

第一版 `SynthesisKind`：

```text
CONCEPT_APPLICATION
PRACTICE_COMPARISON
EVIDENCE_BASED_JUDGMENT
CROSS_DOMAIN_SUMMARY
```

禁止开放式 `FREEFORM_REASONING`。

输入策略至少包含：

```text
ALL_DOMAINS_REQUIRED
AVAILABLE_RESULTS
```

### 6.3 依赖语义

Synthesis 的普通上游依赖使用：

```text
USES_AVAILABLE_RESULTS
```

而不是为全部上游固定使用 `REQUIRES_SUCCESS`。Synthesis 自己根据 `inputRequirement` 判断能否产生关系。这样上游某一域失败时，其他成功结果仍然可以返回。

`ORDER_AFTER` 只表达用户要求的展示/执行顺序，不表示下游必须消费上游结果。

### 6.4 路由识别

确定性规则识别高精度关系表达，例如：

- 在这个项目中如何使用；
- 这个设计体现在哪里；
- 为什么选择；
- 是否符合；
- 与通用实践相比；
- 用这个项目举例解释；
- 结合项目说明。

可选模型只能提出闭集 Goal 与关系候选。服务端必须验证任务类型、主体、依赖、任务数、绑定强度和边界；模型不能制造主体或直接决定 Portfolio 事实。

模型不可用或关系不确定时，允许降级成 General + Portfolio 来源分区式回答，不能降级成错误的单域回答。

### 6.5 暂定决议

1. 多来源回答不自动等于 Synthesis。
2. 并列问题生成两个任务；关系或判断问题才生成第三个任务。
3. 保留公共 `SYNTHESIS`，内部增加闭集 `SynthesisKind`。
4. Synthesis 使用 `USES_AVAILABLE_RESULTS` 并自行校验输入策略。
5. Synthesis 失败不影响成功的上游答案。
6. 模型不确定时退回来源分区式回答。
7. 普通单主体三任务计划无需强制确认。
8. 第一版禁止自由无边界综合及模型改变推荐决策。

## 7. 决议三：General 与 Portfolio 中间材料

### 7.1 Material 是下游权威输入

下游任务不得继续消费最终响应 Block。`resultPayload` 面向用户响应投影，强类型 Answer Material 面向下游任务。

General 与 Portfolio 保持不同领域类型，不使用包含大量空字段的统一宽松对象。

### 7.2 Portfolio Material

建议结构：

```java
record PortfolioAnswerMaterial(
        List<PortfolioGroundedStatement> statements,
        List<PublicSourceReference> sourceCatalog,
        List<MaterialCaveat> caveats,
        Set<String> omittedTopicLabels,
        String contentVersion) {}

record PortfolioGroundedStatement(
        String statementAlias,
        String text,
        PortfolioStatementRole role,
        List<SubjectReference> subjects,
        Set<String> claimIds,
        Set<String> sourceReferenceKeys,
        Set<String> semanticTags,
        PortfolioFactConstraints constraints) {}
```

`statementAlias` 是请求内短期别名；模型和 Validator 使用别名，不让模型生成 Claim/Evidence ID。

`PortfolioStatementRole` 应复用现有 Claim Category/Section Mapping 的权威映射，可包含：

```text
OVERVIEW
MECHANISM
DECISION
IMPLEMENTATION
CONSTRAINT
OUTCOME
CONTRIBUTION
LIMITATION
COMPARISON_POINT
RECOMMENDATION_REASON
```

每条可发布 Portfolio Statement 必须：

- 绑定至少一个公开来源 Key；
- Key 存在于同一 Material 的公开 Source Catalog；
- Claim 与 Source 具有合法关联；
- Subject 与 contentVersion 一致；
- 通过状态、数字、时间和贡献归属约束。

第一版原则上保持一条原子 Portfolio Statement 表达一个主体事实；跨主体差异由 Comparison Material 或 Synthesis 表达。

### 7.3 General Material

建议结构：

```java
record GeneralAnswerMaterial(
        String topic,
        List<GeneralStatement> statements,
        List<MaterialCaveat> caveats,
        GeneralKnowledgeMetadata metadata) {}

record GeneralStatement(
        String statementAlias,
        String text,
        GeneralStatementRole role,
        Set<String> conceptTags,
        GeneralSupportKind supportKind) {}
```

第一版 General Role：

```text
DEFINITION
MECHANISM
ADVANTAGE
LIMITATION
USE_CASE
CONTRAST
PRACTICE
CAUTION
```

General Provider 建议直接返回严格的 `GeneralAnswerMaterialDraft`，经 Codec 与 Validator 后形成 Material，再由确定性 Renderer 生成普通回答正文：

```text
Model
  -> GeneralAnswerMaterialDraft
  -> GeneralMaterialValidator
  -> GeneralAnswerMaterial
  -> Deterministic General Renderer
  -> SectionResultPayload
```

不采用“先生成自由正文，再反向猜测其中有什么事实”；也不采用两次模型调用抽取结构。

### 7.4 Synthesis 最小输入投影

Synthesis 不直接依赖两个完整领域对象，而消费 sealed 最小投影：

```java
sealed interface SynthesisInputStatement {}

record GeneralSynthesisInput(
        String alias,
        String text,
        GeneralStatementRole role,
        Set<String> conceptTags) implements SynthesisInputStatement {}

record PortfolioSynthesisInput(
        String alias,
        String text,
        PortfolioStatementRole role,
        Set<String> conceptTags,
        List<SubjectReference> subjects,
        Set<String> sourceReferenceKeys,
        PortfolioFactConstraints constraints) implements SynthesisInputStatement {}
```

确定性代码先依据受控 Tag、Role 兼容矩阵、Synthesis Kind 和主体范围生成允许关系；模型只能选择和表达允许关系。

### 7.5 兼容迁移

现有 `GroundedAnswerContribution` 暂不删除：

1. 增加逐句 `groundedStatements`；
2. 旧 `supportedStatements` 与公开引用列表由新结构确定性投影；
3. P4 逐步迁移到 `PortfolioAnswerMaterial`；
4. Synthesis 只消费新 Material，不读取 UI Block 或 P4 最终正文；
5. 所有消费者迁移后再评估删除旧字段。

### 7.6 暂定决议

1. 下游任务不消费 UI Block。
2. General 与 Portfolio 使用不同的强类型 Material。
3. Portfolio Statement 逐句绑定公开来源。
4. General Provider 输出结构化 Material Draft。
5. General Material 验证后再确定性渲染正文。
6. Synthesis 使用 sealed 最小投影。
7. 确定性代码先形成 Allowed Relation，模型不能创建关系。
8. 内部 Material 不完整暴露给前端。
9. `GroundedAnswerContribution` 渐进迁移，不立即删除。

## 8. 决议四：受约束跨域 Synthesis

### 8.1 独立组合边界

跨域综合不进入 P4 `PortfolioAnswerComposition`，新增：

```java
interface CrossDomainAnswerComposition {
    CrossDomainCompositionResult compose(
            CrossDomainSynthesisInput input,
            CrossDomainCompositionContext context);
}
```

内部职责建议拆分为：

```text
CrossDomainRelationCandidateBuilder
CrossDomainRelationPolicy
CrossDomainExpressionEligibilityPolicy
CrossDomainDeterministicComposer
CrossDomainModelPort
CrossDomainDraftCodec
CrossDomainGroundingValidator
```

流程：

```text
General Material + Portfolio Material
  -> Relation Candidate Builder
  -> Relation Policy
  -> Allowed Relations
  -> 先构建确定性 fallback
  -> Eligibility
  -> 可选模型表达
  -> 严格 Codec/Validator
  -> 通过则发布；失败则原子回退
```

### 8.2 允许关系闭集

第一版关系：

```text
ILLUSTRATES
IMPLEMENTS
ALIGNS_WITH
DIFFERS_FROM
PARTIALLY_ALIGNS_WITH
INSUFFICIENT_TO_CONFIRM
```

语义：

- `ILLUSTRATES`：Portfolio 事实可以作为 General 概念实例，不声称完整实现；
- `IMPLEMENTS`：Portfolio 事实直接实现 General 所述机制，需要最严格的 Tag、Role 和公开来源匹配；
- `ALIGNS_WITH`：项目做法与某项通用实践一致，不扩大为完整标准符合；
- `DIFFERS_FROM`：公开材料明确证明两种做法在结构化维度上不同，不能把“未提及”解释为“没有”；
- `PARTIALLY_ALIGNS_WITH`：只能确认部分要素，必须携带限定条件；
- `INSUFFICIENT_TO_CONFIRM`：公开材料不足以确认完整关系，是合法的受限结论。

第一版禁止：

```text
CAUSES
PROVES
GUARANTEES
BEST
SUPERIOR_TO
FULLY_COMPLIES_WITH
```

不允许根据两个分别成立的事实自动推导因果关系、能力证明、绝对评价或完整合规。

### 8.3 Allowed Relation

```java
record AllowedRelation(
        String relationAlias,
        String generalAlias,
        String portfolioAlias,
        RelationType relationType,
        RelationStrength strength,
        Set<String> sharedConcepts,
        Set<String> requiredQualifiers) {}
```

模型只能引用服务器提供的 Relation Alias，不重复决定 Relation Type。必需限定语义不得被删除或反转。

### 8.4 模型 Draft

模型返回严格 JSON，只能包含：

- 闭集 Section Kind；
- 正文；
- 已提供 Relation Alias；
- 已提供 Statement Alias；
- 已批准 Caveat Alias。

模型不能输出或创造：

- Claim/Evidence ID；
- Source URL；
- 新主体、新关系、新技术组件；
- 新数字、新日期、新状态、新贡献归属；
- 新推荐集合或排序。

公开引用由后端通过 Portfolio Statement 确定性反查。

### 8.5 Validator

确定性 Validator 至少覆盖：

1. Schema 闭集、数量和长度；
2. Statement/Relation Alias 合法性；
3. Relation 与输入绑定一致；
4. Portfolio 数字、时间、状态、贡献和主体约束；
5. General 与 Portfolio 来源域隔离，禁止来源漂白；
6. 必需限定语、否定语义、矛盾和公开引用映射。

第一版不使用第二个生产模型作为唯一审查门禁。模型审查未来可作为离线 Eval Grader。

### 8.6 降级层级

```text
合法模型 Synthesis
  -> 失败时使用确定性关系 Synthesis
  -> 没有合法关系时返回 General + Portfolio 来源分区
  -> 某一上游失败时保留另一域结果
```

三层都是合法产品行为。没有合法关系不是系统故障。

Synthesis Draft 原子通过或原子丢弃，不保留部分模型句子。

### 8.7 第一版能力边界

支持：

- 一个 General Topic；
- 一个 Portfolio 主体；
- 最多三个 Allowed Relation；
- `CONCEPT_APPLICATION`；
- `PRACTICE_COMPARISON`；
- 确定性 fallback；
- 可选模型表达；
- 明确 Caveat；
- 上游部分失败保留成功结果。

暂不支持：

- 多 General Topic × 多 Portfolio 主体的笛卡尔综合；
- 跨历史回合自由综合；
- 复杂因果推断；
- 能力等级或雇佣结论；
- 模型修改推荐；
- 无证据的最佳实践批判；
- 自动生成项目改造方案；
- 多 Synthesis Task 相互依赖。

### 8.8 暂定决议

1. 新增独立 `CrossDomainAnswerComposition`。
2. 必须先经过 Candidate Builder 和 Relation Policy。
3. 模型只能表达已批准 Relation。
4. 引用由后端确定性投影。
5. 使用确定性 Validator，不依赖第二个生产模型。
6. 调用模型前先构建确定性 fallback。
7. 模型 Draft 原子通过或原子丢弃。
8. 无合法关系退回来源分区式回答。
9. 第一版限制为一个 General Topic、一个 Portfolio 主体和最多三个关系。

## 9. 决议五：逐句支持、证据状态与公共响应契约

### 9.1 三类语义

公共契约明确区分：

| 概念 | 回答的问题 |
|---|---|
| Source Domain | 内容来自 General、Portfolio 还是 Synthesis |
| Support | 后端凭什么允许该内容发布 |
| Provenance | 内容具体依赖哪些 Task、Statement、公开来源和版本 |

### 9.2 支持类型

第一版 `AnswerSupportKind`：

```text
VERIFIED_PUBLIC_EVIDENCE
GENERAL_KNOWLEDGE
DERIVED_FROM_TASKS
```

- `VERIFIED_PUBLIC_EVIDENCE` 仅用于通过 P3 Evidence Promotion 的 Portfolio 内容；
- `GENERAL_KNOWLEDGE` 明确不是 Portfolio Evidence；
- `DERIVED_FROM_TASKS` 仅用于通过 Relation Policy 和跨域 Validator 的 Synthesis 内容。

没有可渲染正文的任务只返回 Task 状态和安全 `reasonCode`，不制造空 Block。

### 9.3 Block 级契约

概念结构：

```java
record AnswerBlockResponse(
        String blockId,
        TaskSourceDomain sourceDomain,
        String sectionType,
        String title,
        String content,
        AnswerBlockSupportResponse support) {}

record AnswerBlockSupportResponse(
        AnswerSupportKind kind,
        List<StatementSupportReferenceResponse> statementReferences,
        List<String> sourceTaskIds,
        List<String> publicSourceKeys,
        String contentVersion) {}
```

约束：

- General Block 的公开来源 Key 为空；
- Portfolio Block 必须关联至少一个已验证 Portfolio Statement 和公开来源；
- Synthesis Block 必须关联至少一个 General Statement、一个 Portfolio Statement和允许关系；
- Synthesis 的公开来源只追溯 Portfolio 输入，不表示来源直接证明整个综合句；
- `blockId` 与 `statementId` 都是当前不可变回答内的不透明 ID，不保证跨独立请求或跨内容版本稳定；
- 同一已接受请求的幂等重放必须返回相同 `blockId` 与 `statementId`；实现可以确定性生成 ID，或将其作为完成回执的一部分持久化；
- `statementReferences` 构成响应级 Provenance，消费者可以用它校验来源链，但前端渲染 Block/Source 时不要求直接展示或理解完整内部 Material；
- 第一版不承诺字符区间级引用；
- 一个 Block 只能组合相同来源域、相同主体范围和相近语义角色的 Statement；
- 跨来源关系必须形成独立 `SYNTHESIS` Block。

### 9.4 Task 级摘要

每个 Completed Task 增加 `supportSummary`，至少提供：

```text
kind
statementCount
publicSourceCount
sourceTaskCount（Synthesis）
contentVersion（适用时）
```

Block Support 是权威明细，Task Support Summary 是聚合投影。

### 9.5 顶层来源组成

新增 `sourceComposition`：

```text
GENERAL_ONLY
PORTFOLIO_ONLY
MULTI_SOURCE
CROSS_DOMAIN_DERIVED
```

- `MULTI_SOURCE`：存在 General 与 Portfolio，但没有成功的 Synthesis；
- `CROSS_DOMAIN_DERIVED`：至少存在一个合法 Synthesis Block。

`sourceComposition` 不替代：

- `generationMode`：描述可见内容使用的生成方式集合；
- `constructionMode`：描述构造路径；
- Task/Block Support：描述可信支持语义。

### 9.6 顶层 Evidence 兼容

现有顶层 `evidenceState` 暂时保留：

- Portfolio-only 且全部可发布内容有充分支持：`VERIFIED`；
- General-only：`NOT_REQUIRED`；
- Portfolio 无可发布内容且证据不足：`INSUFFICIENT`；
- 多来源或跨域回答：新增兼容聚合值 `MIXED`。

`MIXED` 只表示单一顶层 Evidence 状态不足以描述整份回答；消费者必须读取 Task/Block Support。未来公共协议大版本可评估废弃顶层 `evidenceState`。

为避免相同词形跨轴误读，以下字段彼此独立：

| 字段和值 | 权威含义 |
|---|---|
| `generationMode=MIXED` | 一轮可见内容使用了多种生成方式 |
| `constructionMode=MIXED_COMPOSITION` | 回答由多种构造路径组成 |
| `evidenceState=MIXED` | 兼容期顶层证据聚合值，不是来源组成的权威表达 |
| `sourceComposition=MULTI_SOURCE` | General 与 Portfolio 材料并列，没有成功的跨域派生关系 |
| `sourceComposition=CROSS_DOMAIN_DERIVED` | 至少存在一个已校验的跨域派生关系 |
| `retrieval.strategy=HYBRID` | 仅表示关键词与向量候选发现的组合策略 |

消费者不能根据任一轴推断另一轴；例如 `retrieval.strategy=HYBRID` 与多领域回答无关。

### 9.7 公开来源目录

取消跨 Block 删除相同引用关系。响应改为：

```text
顶层 publicSourceCatalog
  -> 按 referenceKey 去重保存公开引用对象

Block support.publicSourceKeys
  -> 保留该 Block 完整的支持关系
```

同一个 Reference Key 可以出现在多个 Block 的 `publicSourceKeys` 中，不能因展示去重而丢失语义关联。

现有 `claimIds`、`evidenceIds` 和 Block 内 `sourceReferences` 在迁移期保留。Synthesis 的这些字段只能由 Portfolio Statement 确定性并集投影，模型不能生成。

### 9.8 覆盖度与证据充分度分离

`PARTIALLY_ANSWERED` 表示请求覆盖不完整，不表示允许发布半可信事实。

```text
resolution = PARTIALLY_ANSWERED
support.kind = VERIFIED_PUBLIC_EVIDENCE
coverage = PARTIAL
```

含义是：已发布事实仍有充分支持，但用户要求的部分主题没有可发布结果。

证据不足的原子 Portfolio 事实不生成 Block，只返回不足状态。

### 9.9 Caveat

公共 Caveat 使用结构化闭集契约：

```java
record PublicAnswerCaveat(
        String code,
        String message,
        List<String> appliesToBlockIds,
        List<String> sourceTaskIds) {}
```

不得输出内部阈值、Provider 原始错误或用户原文；“未确认”不得改写成“不存在”。

### 9.10 兼容迁移

第一阶段增量新增：

```text
sourceComposition
publicSourceCatalog
blocks[].blockId
blocks[].sourceDomain
blocks[].support
displayPlan.tasks[].fulfillmentRole
completedTasks[].fulfillmentRole
completedTasks[].supportSummary
```

保留旧字段：

```text
sourceScope
claimIds
evidenceIds
sourceReferences
evidenceState
```

Mapper 同时从新 Material 生成新旧字段，并以自动化测试验证两套投影一致。未来公共协议大版本再评估删除旧字段。

### 9.11 暂定决议

1. Source Domain、Support、Provenance 明确分离。
2. Task/Block Support 成为权威；顶层 Evidence 只作兼容聚合。
3. 混合回答顶层暂用 `evidenceState=MIXED`。
4. 新增四值 `sourceComposition`。
5. 每个 Block 具有稳定 `blockId`、真实 `sourceDomain` 和 Support。
6. Synthesis 引用只追溯其 Portfolio 输入。
7. 公开引用在顶层 Catalog 去重，Block 的 Reference Key 关系不去重。
8. 部分回答描述覆盖度，不放宽 Portfolio 事实证据标准。
9. Caveat 使用结构化公共契约。
10. 新字段增量加入，P5 不立即删除旧协议。

## 10. 决议六：部分成功、失败、能力不可用与降级

### 10.1 四维状态模型

任务状态必须明确区分：

| 维度 | 语义 |
|---|---|
| Execution Status | Executor 是否正常完成受控执行流程 |
| Task Resolution | 用户要求的当前任务得到了什么结果 |
| Support/Coverage | 已发布内容是否有充分支持、任务覆盖是否完整 |
| Degradation | 是否使用了次优但安全的执行路径 |

例如模型超时但确定性 fallback 完整成功：

```text
executionStatus = SUCCEEDED
resolution = ANSWERED
support = VERIFIED_PUBLIC_EVIDENCE
coverage = COMPLETE
degraded = true
composition.mode = FALLBACK
```

该结果不是部分成功或能力不可用。

### 10.2 Execution Status

P5 暂不强制重命名现有 `SUCCEEDED`。其准确含义是：

> Executor 正常结束并返回一个受控领域结果；用户任务是否得到回答必须读取 Task Resolution。

因此合法状态可以是：

```text
executionStatus = SUCCEEDED
resolution = CAPABILITY_UNAVAILABLE
```

真正的 `executionStatus=FAILED` 仅用于未被领域边界吸收的执行异常、完整性破坏、不变量违反或无法形成安全领域结果的内部失败。

### 10.3 Task Resolution

各 Task Resolution 的规范语义如下：

| Resolution | 语义 |
|---|---|
| `ANSWERED` | 形成完整、可渲染且通过校验的结果；完整 fallback 同样属于 ANSWERED |
| `PARTIALLY_ANSWERED` | 有可渲染结果，但未覆盖全部任务要求；已发布内容仍必须充分可信 |
| `EMPTY` | 执行能力正常，但没有符合条件的公开材料或候选 |
| `NOT_SUPPORTED` | 当前公开产品能力或策略不支持该任务 |
| `CAPABILITY_UNAVAILABLE` | 任务在支持范围内，但所需运行能力暂不可用 |
| `DEPENDENCY_UNAVAILABLE` | 必需上游输入没有产生，本任务无法继续 |
| `NOT_EXECUTED_BUDGET` | 任务合法，但本轮预算不足，未开始执行 |
| `NOT_APPLICABLE` | 任务完成判断后发现不适用于当前输入，主要用于可选衍生任务 |
| `PRESENTATION_BLOCKED` | 已有候选材料，但无法安全形成公开正文，且确定性 fallback 也不可用 |
| `REJECTED` / `BOUNDARY` | 输入、动作或公开产品边界终止，不属于普通执行失败 |

约束：

- 数据源可访问但没有内容是 `EMPTY`，不是 `CAPABILITY_UNAVAILABLE`；
- Provider 超时且无 fallback 是 `CAPABILITY_UNAVAILABLE`；
- Provider 超时但 fallback 完整成功是 `ANSWERED + degraded`；
- 模型 Draft 校验失败但确定性 fallback 成功，不能返回 `PRESENTATION_BLOCKED`；
- `DEPENDENCY_UNAVAILABLE` 只是上游结果传播，不能重复计算为新的系统故障。

### 10.4 履约角色

Semantic Task 增加履约角色，并投影到公共计划与完成任务契约：

```text
PRIMARY
SUPPORTING
OPTIONAL
```

- `PRIMARY`：直接承担一个用户回答目标，未完成会影响顶层完整性；
- `SUPPORTING`：为 Primary Task 提供输入，本身可以产生可见内容，但不重复代表用户目标；
- `OPTIONAL`：编译器添加的增强任务，失败或不适用不降低顶层 Resolution。

示例：

```text
“解释乐观锁，再介绍 Project A 的并发控制”

Task 01 General    PRIMARY
Task 02 Portfolio  PRIMARY
```

```text
“判断 Project A 是否体现了乐观锁”

Task 01 General    SUPPORTING
Task 02 Portfolio  SUPPORTING
Task 03 Synthesis  PRIMARY
```

```text
“解释乐观锁，并说明它在 Project A 中怎么使用”

Task 01 General    PRIMARY
Task 02 Portfolio  SUPPORTING
Task 03 Synthesis  PRIMARY
```

履约角色由用户 Goal 决定，不能按 Task Type 固定映射。

公共字段：

```text
displayPlan.tasks[].fulfillmentRole
completedTasks[].fulfillmentRole
```

约束：

- 两处都使用 `PRIMARY / SUPPORTING / OPTIONAL` 闭集，且必须来自同一个已编译 Semantic Task；
- `fulfillmentRole` 纳入计划指纹，确认后的计划不得静默改变角色；
- 前端只读，不能修改，也不能根据任务顺序、标题、来源域或 Task Type 自行推断；
- 单任务场景也可以返回该字段，但前端不必强制展示；
- 顶层 Resolution 聚合只以服务端角色和 Task Outcome 为准。

### 10.5 无合法跨域关系

无合法关系时必须结合 Synthesis 的履约角色：

#### Optional Synthesis

```text
General = ANSWERED
Portfolio = ANSWERED
Synthesis = NOT_APPLICABLE
fulfillmentRole = OPTIONAL
reasonCode = NO_SUPPORTED_CROSS_DOMAIN_RELATION
```

顶层：

```text
resolution = ANSWERED
sourceComposition = MULTI_SOURCE
```

#### Primary Synthesis

```text
General = ANSWERED
Portfolio = ANSWERED
Synthesis = NOT_APPLICABLE
fulfillmentRole = PRIMARY
reasonCode = NO_SUPPORTED_CROSS_DOMAIN_RELATION
```

顶层：

```text
resolution = PARTIALLY_ANSWERED
sourceComposition = MULTI_SOURCE
```

#### 可形成受限否定结论

如果 Relation Policy 可以形成 `INSUFFICIENT_TO_CONFIRM` 并输出通过校验的受限结论，Synthesis 仍是：

```text
resolution = ANSWERED
support.kind = DERIVED_FROM_TASKS
```

`INSUFFICIENT_TO_CONFIRM` 是合法 Relation Type，不是 Task Resolution。它与完全不存在合法关系的 `NO_SUPPORTED_CROSS_DOMAIN_RELATION` 不同。

### 10.6 顶层 Resolution 聚合

聚合顺序：

1. 先处理 `INVALID_INPUT`、`BOUNDARY`、`CONFIRMATION_REQUIRED`、`CLARIFICATION_REQUIRED`、`PLAN_INVALIDATED` 等协议终止状态；
2. 再检查是否存在合法可渲染内容；
3. 存在可渲染内容时，根据 Primary Goal 是否完整满足投影 `ANSWERED` 或 `PARTIALLY_ANSWERED`；
4. 完全没有可渲染内容时，才区分 `CAPABILITY_UNAVAILABLE` 与 `NOT_SUPPORTED`。

伪代码：

```java
if (isProtocolTerminal(turn)) {
    return protocolResolution(turn);
}

List<TaskOutcome> renderable = renderableOutcomes(turn);
List<TaskOutcome> primary = primaryOutcomes(turn);

if (!renderable.isEmpty()) {
    return primary.stream().allMatch(this::fullySatisfied)
            ? ANSWERED
            : PARTIALLY_ANSWERED;
}

if (primary.stream().anyMatch(this::runtimeUnavailable)) {
    return CAPABILITY_UNAVAILABLE;
}

return NOT_SUPPORTED;
```

Primary Task 满足规则：

- `ANSWERED`：完整满足；
- `PARTIALLY_ANSWERED`：未完整满足；
- `EMPTY`、`NOT_SUPPORTED`、`CAPABILITY_UNAVAILABLE`、`DEPENDENCY_UNAVAILABLE`、`NOT_EXECUTED_BUDGET`、`NOT_APPLICABLE`、`PRESENTATION_BLOCKED`、执行 `FAILED`：未满足；
- Optional Task 不参与顶层完整性聚合。

只要存在合法可渲染内容，局部 `FAILED` 就不能把顶层覆盖成 `CAPABILITY_UNAVAILABLE`。

### 10.7 Degraded 与 Partial 分离

以下路径完整成功时均属于：

```text
resolution = ANSWERED
degraded = true
```

- PostgreSQL 失败后 Bundle 成功；
- Hybrid 失败后 Keyword 完整成功；
- Portfolio 模型表达失败后确定性 Composer 成功；
- Cross-domain 模型失败后确定性 Relation Composer 成功。

如果 fallback 只覆盖部分用户目标，才同时返回：

```text
resolution = PARTIALLY_ANSWERED
coverage = PARTIAL
degraded = true
```

`degraded` 描述执行路径；`resolution/coverage` 描述用户目标满足程度。

### 10.8 结构化降级摘要

保留现有 `degraded: boolean`，并增加安全闭集：

```text
RETRIEVAL_FALLBACK
EXPRESSION_FALLBACK
CROSS_DOMAIN_EXPRESSION_FALLBACK
CONTENT_BACKEND_FALLBACK
```

公共概念结构：

```java
record PublicDegradationSummary(
        boolean degraded,
        Set<PublicDegradationKind> kinds,
        List<String> affectedTaskIds) {}
```

公共协议不暴露 Provider 名、数据库地址、异常堆栈、Secret、重试次数或内部阈值。内部诊断可以保留更细的失败分类。

顶层 `degraded` 和降级摘要只聚合最终可见内容对应的任务，不能因为一个没有贡献正文的失败 Optional Task 而污染整份回答。

### 10.9 公共 Task Status

公共 Task Status 使用以下闭集：

```text
COMPLETED
PARTIAL
EMPTY
NOT_SUPPORTED
NOT_APPLICABLE
BLOCKED
UNAVAILABLE
STALE
FAILED
REJECTED
NOT_EXECUTED
```

映射：

| 内部结果 | 公共 Status |
|---|---|
| `ANSWERED` | `COMPLETED` |
| `PARTIALLY_ANSWERED` | `PARTIAL` |
| `EMPTY` | `EMPTY` |
| `NOT_SUPPORTED` | `NOT_SUPPORTED` |
| `NOT_APPLICABLE` | `NOT_APPLICABLE` |
| `DEPENDENCY_UNAVAILABLE` | `BLOCKED` |
| `PRESENTATION_BLOCKED` | `BLOCKED` |
| `CAPABILITY_UNAVAILABLE` | `UNAVAILABLE` |
| Context/Result 版本失效 | `STALE` |
| `executionStatus=FAILED` | `FAILED` |
| `executionStatus=CANCELLED` | `NOT_EXECUTED` |
| `REJECTED` / `BOUNDARY` | `REJECTED` |
| `NOT_EXECUTED_BUDGET` | `NOT_EXECUTED` |

Mapper 不再把 `CAPABILITY_UNAVAILABLE` 合并为 `NOT_SUPPORTED`。

`PRESENTATION_BLOCKED` 表示已有候选材料但无法安全公开，因此公共状态使用
`BLOCKED`；`executionStatus=CANCELLED` 当前只表示路由阶段延后或未选择、任务并未
执行，因此公共状态使用 `NOT_EXECUTED`，不能解释为用户取消了整个请求。

迁移期按契约版本隔离状态词汇：`stp-v2` 只允许上述公共闭集，`stp-v1` 仍可能
返回旧值。前端可以跨响应兼容两套版本，但单个响应不得混用新旧公共 Status；
`stp-v2` 中出现旧值或同一响应混用两套值均属于契约违规，消费者 fail closed。

### 10.10 Reason Code

公共 Task 返回安全、闭集的 `reasonCode`，并增加分类：

```text
INPUT
CONTENT
POLICY
CAPABILITY
DEPENDENCY
BUDGET
INTEGRITY
BOUNDARY
```

示例：

| Reason Code | Category |
|---|---|
| `PORTFOLIO_EVIDENCE_INSUFFICIENT` | `CONTENT` |
| `GENERAL_TASK_UNSUPPORTED` | `POLICY` |
| `GENERAL_PROVIDER_UNAVAILABLE` | `CAPABILITY` |
| `CROSS_DOMAIN_RELATION_DISABLED` | `CAPABILITY` |
| `CROSS_DOMAIN_INPUT_INSUFFICIENT` | `DEPENDENCY` |
| `NO_SUPPORTED_CROSS_DOMAIN_RELATION` | `CONTENT` |
| `TASK_BUDGET_EXHAUSTED` | `BUDGET` |
| `CONTENT_VERSION_STALE` | `CONTENT` |
| `EVIDENCE_INTEGRITY_FAILURE` | `INTEGRITY` |

内部异常必须映射成安全 Code，不能直接进入公共响应。

### 10.11 典型聚合矩阵

| General | Portfolio | Synthesis | 顶层结果 |
|---|---|---|---|
| 成功 | 成功 | 不需要 | `ANSWERED + MULTI_SOURCE` |
| 成功 | 成功 | 成功 | `ANSWERED + CROSS_DOMAIN_DERIVED` |
| 成功 | 成功 | 模型失败、确定性成功 | `ANSWERED + CROSS_DOMAIN_DERIVED + degraded` |
| 成功 | 成功 | Optional 且无合法关系 | `ANSWERED + MULTI_SOURCE` |
| 成功 | 成功 | Primary 且无合法关系 | `PARTIALLY_ANSWERED + MULTI_SOURCE` |
| 成功 | 不可用 | 依赖不足 | `PARTIALLY_ANSWERED` |
| 不可用 | 成功 | 依赖不足 | `PARTIALLY_ANSWERED` |
| 成功 | 部分覆盖 | 部分关系 | `PARTIALLY_ANSWERED` |
| 不可用 | 不可用 | 阻塞 | `CAPABILITY_UNAVAILABLE` |
| 内容为空 | 内容不足 | 不适用 | `NOT_SUPPORTED` |
| 成功 | `FAILED` | 阻塞 | `PARTIALLY_ANSWERED`，局部 FAILED 不覆盖正文 |
| fallback 完整成功 | fallback 完整成功 | 不需要 | `ANSWERED + degraded` |

### 10.12 实施收口点

实施时至少需要：

1. 修正 `ConversationalAgentRuntime.projectedResolution()` 的聚合顺序；
2. 混合回答的 `projectedEvidenceState()` 使用兼容 `MIXED`；
3. Semantic Task 增加 `fulfillmentRole`，Compiler 根据用户 Goal 标记，并投影到 Display Plan 与 Completed Task；
4. Response Mapper 区分 `NOT_SUPPORTED`、`UNAVAILABLE` 和真实 `SYNTHESIS` 来源；
5. 输出安全 Task Status、Reason Category 和降级摘要；
6. Eval 增加局部失败、完整 fallback、部分覆盖和无合法关系用例。

### 10.13 暂定决议

1. Execution Status、Task Resolution、Support/Coverage、Degradation 四维分离。
2. `SUCCEEDED` 表示 Executor 正常返回领域结果，不等于任务已回答。
3. Task 增加 `PRIMARY / SUPPORTING / OPTIONAL` 履约角色，并作为前端可消费但不可修改的公共计划/结果字段。
4. 顶层 Resolution 根据 Primary Goal 聚合，不根据任意失败 Task 聚合。
5. 有合法可渲染内容时，局部失败最多使顶层成为 `PARTIALLY_ANSWERED`。
6. `CAPABILITY_UNAVAILABLE` 只用于没有可渲染结果且 Primary 因运行能力未完成。
7. 内容为空或产品不支持不能伪装成能力故障。
8. Degraded 与 Partial 分离；完整 fallback 仍是 `ANSWERED`。
9. 无合法关系是否影响顶层取决于 Synthesis 是 Primary 还是 Optional。
10. `INSUFFICIENT_TO_CONFIRM` 是合法可渲染 Relation，不等于失败。
11. 公共 Task Status 区分 `NOT_SUPPORTED` 与 `UNAVAILABLE`。
12. 顶层降级状态只聚合最终可见内容。
13. Reason Code 使用安全闭集，并增加 Reason Category。
14. 修正当前 `FAILED` 优先于可渲染结果的聚合错误。

## 11. 决议七：Context 授权快照、普通追问与显式继续

### 11.1 两个上下文平面

P5 将多轮信息明确分成两个不同的平面。

#### Business Context Plane

服务端持久化、加密、短期、强类型。允许保存：

- 公开主体 ID；
- Task Type；
- Facet/Dimension；
- 公开结果集中的主体顺序；
- Recommendation Scope；
- contentVersion；
- sourceTaskId；
- Parent Context Handle 与 Revision。

禁止保存用户问题、回答正文、模型自由文本、任意对话摘要、未发布内容或客户端自定义元数据。

#### Discourse Window Plane

仅存在于当前请求和当前浏览器标签页，由受限的最近消息组成，服务端不持久化。它只服务 General 语言连续性，不能成为：

- Portfolio 主体授权来源；
- Portfolio Evidence；
- Recommendation Scope；
- contentVersion 权威；
- Cross-domain Synthesis 的事实输入。

原则：

> Business Context 提供可信业务身份和范围；Discourse Window 只提供不可信的语言连续性。

### 11.2 授权路由快照

新增内部最小投影：

```java
record AuthorizedRoutingContextSnapshot(
        String contextHandle,
        ConversationContextType contextType,
        ContextActivationMode activationMode,
        long revision,
        String sourceTaskId,
        AuthorizedRoutingBinding binding) {}

sealed interface AuthorizedRoutingBinding {}

record RecentSemanticTaskRoutingBinding(
        SemanticTaskType taskType,
        List<SubjectReference> subjects,
        Set<PortfolioFacet> facets,
        Set<ComparisonDimension> dimensions,
        String contentVersion) implements AuthorizedRoutingBinding {}

record RecommendationRoutingBinding(
        AuthorizedSubjectScope scope,
        String contentVersion) implements AuthorizedRoutingBinding {}
```

后续如果批准 Result Set，再增加 `ResultSetRoutingBinding`。

Snapshot 不携带 Conversation ID、ResumeToken、加密 Envelope、数据库主键、用户问题、回答正文、Context 原始序列化 Payload、Secret 或内部过期策略细节。

Router 只消费完成授权后的最小 Snapshot，不直接读取 Context Store。

### 11.3 激活模式

```text
EXPLICIT_REFERENCE
ACTIVE_CANDIDATE
```

#### `EXPLICIT_REFERENCE`

用户明确从指定结果继续，并发送 `contextReference`。服务端必须重新验证 ResumeToken、Conversation、Handle、类型、有效期、Schema、加密完整性与版本策略。

显式 Handle 是强绑定候选；失效时不得静默替换为当前页面主体或另一个 Active Context。

#### `ACTIVE_CANDIDATE`

普通追问没有显式 Handle，但携带合法 ResumeToken。服务端可以加载当前 Slot 的 Active Typed Context，但它只能作为候选。

只有当前问题存在明确指代、省略或 Recommendation Refine 信号时，Router 才能绑定 Active Candidate。

### 11.4 Context Demand

普通问题不默认继承上一轮主体。Router 先检测：

```text
NONE
SUBJECT_ANAPHORA
RESULT_POSITION
RECOMMENDATION_REFINEMENT
ELLIPTICAL_CONTINUATION
```

只有 `contextDemand != NONE` 才消费 Active Typed Context。

完整的新问题，例如“什么是悲观锁”“比较 Kafka 和 RabbitMQ”，不能因为存在 Recent Context 而被改写成上一项目的 Portfolio Task。

### 11.5 显式继续与普通追问

| 维度 | 显式继续 | 普通追问 |
|---|---|---|
| 是否发送 Handle | 是 | 否 |
| Context 是否强绑定 | 是 | 仅是候选 |
| Context 无效 | 明确失败，不换目标 | 可使用其他合法显式信息或澄清 |
| 文本主体冲突 | Context Conflict | 本轮显式主体优先 |
| 是否允许页面 Hint | 不允许静默回退 | 仅在明确指代且唯一时使用 |

普通追问中，上一轮 A 与本轮显式 B 不冲突，本轮 B 直接优先。

显式从 A 继续但本轮文本明确写 B 时，两个本轮强信号冲突，必须：

```text
CLARIFICATION_REQUIRED
reasonCode = ROUTING_CONTEXT_CONFLICT
```

不能静默选择 A 或 B。

### 11.6 多提及解析

Context 解析不再找到第一个候选后提前返回，而分为：

1. 收集本轮显式主体、指代、结果位置、Recommendation Refine 和 General Topic 提及；
2. 收集 `CURRENT_TEXT`、`EXPLICIT_CONTEXT_REFERENCE`、`ACTIVE_TYPED_CONTEXT`、`PAGE_HINT` 和受验证模型候选；
3. 为每个提及分别形成 `ResolvedSubjectBinding`；
4. 由 Goal Collector 根据任务意图选择主体。

单个提及的建议优先级：

```text
本轮显式主体
  > 本轮显式 Result Reference
  > 显式 Context Handle
  > 明确指代 + 唯一 Active Typed Context
  > 明确指代 + 唯一 Page Hint
  > 经闭集验证的模型主体候选
```

该顺序只用于不存在合理竞争候选的情况，不是无条件的机械覆盖规则。对于普通指代表达，如果 Active Typed Context 与 Page Hint 指向不同主体，二者都可能是用户所指对象，必须形成 Context Ambiguity，而不是默认选择 Active Context。

补充约束：

- Active Context 不与普通问题中的本轮显式主体竞争；
- 页面 Hint 不覆盖 Active Context 或文本主体；
- Active Context 与不同的 Page Hint 同时匹配同一指代时必须澄清；
- 模型只能选择 Public Catalog 内的主体；
- 模型不能把 Page Hint 从 `HINT` 升级成 `EXPLICIT`；
- 多候选无法唯一解析时必须澄清。

### 11.7 单独“继续”的语义

服务端不保存回答正文和自由对话摘要，因此不能从单独的“继续”推测用户希望继续哪一部分。

显式继续操作必须携带具体问题与 Handle。普通自由输入“继续”只有在存在唯一结构化 Pending Action 时才能执行，例如：

```text
Pending Plan Confirmation
Pending Clarification
Recommendation Refine
明确 Result Context 操作
```

否则：

```text
CLARIFICATION_REQUIRED
reasonCode = CONTINUATION_GOAL_UNRESOLVED
```

### 11.8 授权顺序

生产链调整为：

```text
Request
  -> 会话与 ResumeToken 校验
  -> 显式 Context Reference 授权
  -> Active Typed Context 解析与授权
  -> AuthorizedRoutingContextSnapshot
  -> SemanticTurnInput
  -> TurnRouter
```

概念接口：

```java
AuthorizedContextSet authorized = contextAuthorization.authorize(
        request.resumeToken(),
        request.contextReference());

SemanticTurnInput input = requestMapper.toInput(
        request,
        authorized.routingSnapshots());
```

客户端不能回传完整业务 Scope 或 Context Value。Router 不自行读取 Store。

### 11.9 Task 执行绑定

Executor 通过内部绑定消费 Context：

```java
record TaskContextBinding(
        String contextHandle,
        ConversationContextType contextType,
        ContextActivationMode activationMode,
        String contentVersion,
        AuthorizedRoutingBinding binding) {}
```

`SemanticTask.subjectReferences` 表达公开主体；`TaskContextBinding` 表达主体为何被授权。Executor 不再从原始请求中自行寻找 Context。

### 11.10 Context Commit Candidate

Refine 当前无法提交安全子 Context，是因为 Committer 只有父 Handle，没有执行层验证后的 Recommendation Scope。

建议由 Executor 在成功结果中产生：

```java
record ContextCommitCandidate(
        ConversationContextValue value,
        ContextHandle parentHandle,
        ContextSlot slot,
        String sourceTaskId) {}
```

Recommendation Refine Executor 使用授权 Scope 生成新结果，并构造继承同一 Scope 的 Commit Candidate。Committer 只负责 Revision、加密、保存和激活，不重新推导或扩大业务 Scope。

### 11.11 Context 写入规则

只有成功、可渲染、业务上可继续的结果才写 Context。

不写：

- General Task；
- 失败、Empty、Not Supported 或 Capability Unavailable；
- 被丢弃的模型 Draft；
- 无合法关系的 Optional Synthesis；
- 原始问题或回答正文。

写入候选：

- Portfolio Fact；
- Portfolio Compare；
- Recommendation；
- Recommendation Refine；
- 未来另行批准的 Result Set（不属于 P5 第一版）。

P5 第一版不保存独立 Synthesis/Derived Result Context。Synthesis 只返回回答级派生关系和 Provenance，不成为后续轮次可直接引用的持久业务上下文。

通过显式或 Active Context 继续形成的新 Context，可以记录 Parent Handle 形成不可变短链，不原地改写旧值。

### 11.12 Context 失败语义

#### 显式 Handle 无效或过期

必须 fail closed：

```text
CONTEXT_REFERENCE_INVALID
CONTEXT_REFERENCE_EXPIRED
```

不能回退到页面主体或最新 Active Context。

#### 显式 Handle 的 Store 暂不可用

```text
CAPABILITY_UNAVAILABLE
reasonCode = CONTEXT_RESOLUTION_UNAVAILABLE
```

不能假装 Handle 不存在或换用其他 Context。

#### 普通追问的 Active Context Store 不可用

本轮问题自包含时可以忽略 Active Context 继续执行。本轮依赖指代时，返回澄清：

```text
CLARIFICATION_REQUIRED
reasonCode = CONTEXT_SUBJECT_REQUIRED
```

#### Context Commit 失败

合法回答保持成功，只将继续能力标记为不可用：

```text
continuation.status = UNAVAILABLE
reasonCode = CONTEXT_PERSISTENCE_UNAVAILABLE
```

### 11.13 General Discourse Window

前端已携带的 bounded messages 经 `ConversationWindowPolicy` 后，只传给获批的 General Classification/Generation。

约束：

- 不写服务端数据库和日志；
- 不进入 Portfolio Retriever；
- 不作为 Portfolio Subject 授权或 Evidence；
- 不进入 Business Context Store；
- 不直接成为 Synthesis 输入；
- 轮数、长度、角色和总字符数受限；
- 当前问题始终是唯一当前指令；
- 历史 Assistant 正文只是不可信语言上下文。

模型关闭且 General 代词无法确定性解析时，应澄清而不是根据历史自由猜测。

Cross-domain Synthesis 只消费本轮验证后的 General Material、Portfolio Material 和 Allowed Relation，不能读取完整 Conversation Window。

### 11.14 公共接口

请求继续保留：

```json
{
  "resumeToken": "...",
  "contextReference": {
    "contextHandle": "...",
    "expectedContextType": "RECENT_SEMANTIC_TASK"
  },
  "messages": []
}
```

语义：

- `contextReference` 是显式强绑定请求；
- `resumeToken` 允许服务端解析 Active Context Candidate；
- `messages` 是临时 Discourse Window，不是业务 Context；
- 客户端不能提交强类型业务 Value 或授权 Scope。

响应将散落的 `contextHandle` 标准化到产生该 Context 的完成任务上：

```json
{
  "completedTasks": [
    {
      "continuationContext": {
        "contextHandle": "...",
        "contextType": "RECENT_SEMANTIC_TASK",
        "sourceTaskId": "task-02"
      }
    }
  ]
}
```

`completedTasks[].continuationContext` 是 `stp-v2` “继续” affordance 的权威来源。
保留的 `completedTasks[].contextHandle` 只是迁移期兼容字段：新旧字段同时存在时
Handle 必须完全一致；不一致属于契约违规并 fail closed。只收到旧字段时，前端
只能按 `stp-v1` 既有映射兼容；不能从正文、页面 Hint 或其他任务推断 Context。
不使用单一顶层 `continuationContext`，因为一个响应可以有多个可继续任务。

不公开 Revision、Parent 链、内部 Scope、加密内容、存储键或精确失效策略。

### 11.15 安全不变量

1. Router 永远不直接读取 Context Store。
2. Router 只消费授权后的最小 Snapshot。
3. Context Handle 不是权限本身，必须与 ResumeToken 和 Conversation 重新验证。
4. 客户端消息不能建立 Portfolio Scope。
5. 普通问题不自动继承 Active Context。
6. 只有明确 Context Demand 才消费 Active Candidate。
7. 显式 Handle 无效时不能静默回退。
8. 显式 Handle 与文本主体冲突时必须澄清。
9. Context Committer 不重新推导业务授权范围。
10. Synthesis 不读取原始历史消息。
11. Context 读取失败与写入失败使用不同语义。
12. Context Store 继续禁止问题、回答和自由模型文本。

### 11.16 暂定决议

1. Context 分为持久 Business Context Plane 与请求级 Discourse Window Plane。
2. 新增 `AuthorizedRoutingContextSnapshot`，授权后的 Recent Context 强类型数据进入路由。
3. 显式继续使用 `EXPLICIT_REFERENCE`，普通追问只获得 `ACTIVE_CANDIDATE`。
4. Active Context 只有在问题出现明确 Context Demand 时才能绑定。
5. 普通追问中的显式主体覆盖 Active Context。
6. 显式 Handle 与文本主体冲突时必须澄清。
7. Context 解析改为收集提及、收集候选、逐项绑定。
8. 单独“继续”没有唯一结构化 Pending Action 时必须澄清。
9. Context 授权发生在 Request Mapper 和 Router 之前。
10. Executor 接收 `TaskContextBinding`，不自行寻找 Context。
11. Refine Executor 产生继承授权 Scope 的 `ContextCommitCandidate`，Committer 只保存。
12. 只有成功、可渲染、可继续的业务结果写 Context。
13. 显式 Context 读取失败不得回退到其他主体。
14. Context Commit 失败不撤销合法回答，只影响继续能力。
15. General 可以使用受限、非持久化 Discourse Window。
16. Discourse Window 不能成为 Portfolio Scope、Evidence 或 Synthesis 输入。
17. 公共响应只返回最小 `continuationContext`。

## 12. 决议八：指代、顺序引用与有序结果上下文

### 12.1 指代类型

第一版区分：

```text
SUBJECT_DEICTIC
SUBJECT_PLURAL
ABSOLUTE_POSITION
BINARY_POSITION
RECOMMENDATION_REFERENCE
ELLIPTICAL_ATTRIBUTE
RELATIVE_POSITION
DERIVED_CONCLUSION
```

| 类型 | 示例 |
|---|---|
| `SUBJECT_DEICTIC` | 它、这个项目、该案例 |
| `SUBJECT_PLURAL` | 它们、这些项目、这两个案例 |
| `ABSOLUTE_POSITION` | 第一个、第二个、最后一个 |
| `BINARY_POSITION` | 前者、后者 |
| `RECOMMENDATION_REFERENCE` | 第二个推荐、刚才推荐的项目 |
| `ELLIPTICAL_ATTRIBUTE` | 架构呢、成果呢、为什么 |
| `RELATIVE_POSITION` | 上一个、下一个 |
| `DERIVED_CONCLUSION` | 这个结论、刚才的判断 |

### 12.2 第一版支持边界

支持：

- 单主体“它/这个项目/该案例”；
- 复数“它们/这些项目”；
- 第一至第六、最后一个；
- 恰好两个 Item 时的前者/后者；
- 第一个推荐、第二个推荐；
- 具有唯一结果或唯一已选择项时的“刚才推荐的那个”；
- 架构呢、成果呢等属性省略；
- 显式选择某个结果项继续。

受限支持：

- “这两个”只在候选恰好为两个时解析；
- 候选超过两个时，只有明确“前两个”才选择 Position 1、2；
- “刚才推荐的那个”在多个未选择推荐项中属于歧义表达。

暂不自动支持：

- 无当前焦点的上一个/下一个；
- 其中一个；
- 那个比较好的、刚才最合适的；
- 这个结论；
- 前面提到的那个技术；
- 跨多个历史回合的自由语义指代。

### 12.3 不新增独立 Result Set Context

当前 `RecentSemanticTaskContext.publicSubjects` 已经是有序 `List`，但顺序语义没有成为用户契约；`RecommendationResultPayload` 已有实际有序推荐结果，而 `RecommendationContext` 当前将其丢弃。

第一版不增加 `ConversationContextType.RESULT_SET`，而增强现有 Context：

- Recent Context 保存可选的有序主体选择；
- Recommendation Context 保存本次实际返回的有序推荐项；
- 两者通过统一授权投影供 Router 消费。

独立 Result Set Context 会使同一任务产生两个 Handle，引入重复数据、双 Slot、原子批量提交和选择歧义，不符合第一版最小闭环。

### 12.4 Ordered Subject Selection

```java
record OrderedSubjectSelection(
        SubjectOrderKind orderKind,
        List<OrderedSubjectItem> items) {}

record OrderedSubjectItem(
        String resultItemId,
        int position,
        SubjectReference subject) {}
```

`SubjectOrderKind`：

```text
USER_DECLARED_ORDER
RECOMMENDATION_RANK
RESULT_PRESENTATION_ORDER
```

- `USER_DECLARED_ORDER`：Compare 中用户声明的主体顺序，不表示优劣；
- `RECOMMENDATION_RANK`：P3 Recommendation Projection 决定的结果顺序，模型不得改变；
- `RESULT_PRESENTATION_ORDER`：其他由后端确定性定义的多主体结果顺序，不能使用内部检索候选顺序或前端排序。

### 12.5 Result Item ID

`resultItemId` 是 Context 内稳定的不透明 ID：

- 只在所属 Context 中有效；
- 不保证跨 Context 稳定；
- 不等于数据库主键；
- 不能单独作为权限；
- 必须与 ResumeToken、Context Handle 和 Context Type 一起验证；
- 服务端可以由它确定性解析 Subject。

自然语言“第二个”按 Position 解析；显式操作按 `resultItemId` 解析。

### 12.6 Recent Context v2

```java
RecentSemanticTaskContext {
    taskType;
    publicSubjects;
    orderedSelection; // Optional
    facets;
    dimensions;
    contentVersion;
    sourceTaskId;
}
```

规则：

- 单主体 Fact 不需要 Ordered Selection；
- Compare 使用 `USER_DECLARED_ORDER`；
- 多主体 Fact 只有在结果具有稳定、公开、确定性顺序时才使用 `RESULT_PRESENTATION_ORDER`；
- 内部检索排序不得升级为用户可引用顺序；
- 多主体 Context 中的单数“它”不得默认绑定第一项。

### 12.7 Recommendation Context v2

```java
RecommendationContext {
    authorizedScope;
    profileVersion;
    baselineCriteria;
    constraints;
    preferences;
    exclusions;
    resultLimit;
    selectedResults;
    recommendationBatchId;
    parentContextHandle;
}
```

其中：

```text
selectedResults.orderKind = RECOMMENDATION_RANK
selectedResults.items.size <= resultLimit
```

实际 Selected Results 和 Batch ID 必须来自 `RecommendationResultPayload.projection`，由 Executor 放入 `ContextCommitCandidate`。Committer 不根据候选 Scope 或 Result Limit 重新计算推荐结果。

同一 Recommendation Handle 同时支持 Recommendation Refine、排除刚才推荐的结果、顺序引用、比较推荐项和从指定推荐项继续。

Refine 成功后创建不可变子 Context，继承授权 Scope 和父约束，但保存本轮新的 Selected Results、Batch ID、Preferences 和 Exclusions，不继续沿用父结果列表。

### 12.8 Codec 版本迁移

新增：

```text
p5-recent-v2
p5-recommendation-v2
```

#### Recent v1

旧 `publicSubjects` 虽然使用 List 保存，但旧契约没有声明该顺序可被用户按位置引用：

- 单主体可以继续支持“它/这个项目”；
- 多主体可以支持“它们”；
- 不追认第二个、前者、后者等顺序引用。

#### Recommendation v1

旧 Context 没有实际 Selected Results：

- 可以继续支持基于授权 Scope 的 Refine；
- 不能支持第二个推荐、前两个推荐或“刚才推荐的那个”；
- 需要具体结果时必须澄清或重新执行 Recommendation，不能根据候选 Scope 和 Result Limit 猜测。

### 12.9 数量与位置约束

#### 单主体

“它/这个项目/该案例”只有一个兼容 Subject Binding 时才能解析。多主体集合不能默认使用第一项。

#### 复数

“它们/这些项目”可以绑定整个有序集合；如果目标 Task 只允许单主体，应由 Planner/Validator 要求缩小范围。

#### 固定数量

“这两个”只在集合恰好为两个时直接解析。集合超过两个时不得自动选前两个；“前两个”可以选择 Position 1、2。

#### 前者/后者

只适用于恰好两个 Item。三个以上候选必须澄清。

#### 绝对位置

“第二个/最后一个”要求存在唯一兼容 Ordered Selection。越界返回：

```text
CLARIFICATION_REQUIRED
reasonCode = RESULT_POSITION_OUT_OF_RANGE
```

位置越界不是检索为空或数据库错误。

### 12.10 多 Active Context

Fact、Compare、Recommendation Slot 可以同时 Active。语言中的类型限定用于筛选：

```text
第二个推荐       -> Recommendation
比较里的第二个   -> Compare
```

没有限定且多个 Ordered Context 都兼容：

```text
CLARIFICATION_REQUIRED
reasonCode = RESULT_CONTEXT_AMBIGUOUS
```

不能单纯选择更新时间最近的 Context。

### 12.11 Page Hint 冲突

如果 Active Context 是 A、Page Hint 是 B，用户询问“这个项目”，A 与 B 都是合理候选，必须澄清。

只有以下情况可以直接解析：

- Active 与 Page 指向同一主体；
- 只有一方存在；
- 请求携带显式 Handle；
- 文本中有显式主体；
- 页面交接协议明确将主体标记为本轮显式选择，而不只是 Hint。

原则：

> 强信号优先；合理竞争的上下文信号指向不同对象时澄清。

### 12.12 显式结果项接口

在 `contextReference` 增加可选 Selector：

```json
{
  "contextReference": {
    "contextHandle": "...",
    "expectedContextType": "RECOMMENDATION",
    "resultItemId": "item-2-opaque"
  }
}
```

- 无 `resultItemId` 表示整个 Context；
- 有 `resultItemId` 表示 Context 内的明确结果项；
- Item 必须属于该 Context；
- 客户端不能直接提交 Subject ID 代替 Result Item 授权；
- Result Item 与本轮文本显式主体冲突时必须澄清。

`RECOMMENDATION_RESULT` 的响应结果项必须直接公开在对应完成任务的
`resultPayload.recommendations[]` 上：

```json
{
  "completedTasks": [{
    "resultPayload": {
      "kind": "RECOMMENDATION_RESULT",
      "recommendations": [{
        "resultItemId": "item-2-opaque",
        "position": 2,
        "subject": {
          "subjectType": "PROJECT",
          "subjectId": "project-b"
        }
      }]
    }
  }]
}
```

`resultItemId/position/subject` 不放入 `continuationContext`，也不从服务端私有的
Recent Context 投影给前端。用户选择某项继续时，请求组合为：

```json
{
  "contextReference": {
    "contextHandle": "<completedTasks[].continuationContext.contextHandle>",
    "expectedContextType": "RECOMMENDATION",
    "resultItemId": "<completedTasks[].resultPayload.recommendations[].resultItemId>"
  }
}
```

旧顶层 `portfolioRecommendation.items[]` 不是有序项授权的权威来源，可以在
`stp-v1` 兼容投影中继续缺少这些字段。前端不得用数组下标、`portfolioId`、
`subjectId` 或视觉顺序自行构造 `resultItemId`。

前端如何呈现和触发继续操作不在本文范围内。

### 12.13 Recommendation 指代

- 只有一个推荐结果时，“刚才推荐的那个”直接解析；
- 多结果且明确“第二个推荐”时按 `RECOMMENDATION_RANK` 解析；
- 用户已显式选择某一推荐项，且后续形成带 Parent 关系的单主体 Recent Context 时，可以解析到已选择主体；
- 多个结果但没有选择信息时，“那个”必须澄清，不能默认排名第一。

### 12.14 相对位置暂缓

“上一个/下一个”需要可靠的 `currentResultItemId`。仅有 Ordered Selection 无法知道用户当前焦点。

第一版不使用前端视觉焦点或前端排序作为服务端权威。后续只有在子 Context 明确保存 `parentResultContextHandle + selectedResultItemId` 后，才考虑支持相对位置。

### 12.15 Derived Conclusion 暂缓

“这个结论”指向 Synthesis 推导，而不是公开主体。安全支持它需要新的强类型 Derived Result Context，至少保存 Synthesis Kind、Relation Type、General Concept Tag、Portfolio Subject、上游 Context/Task 和 contentVersion。

第一版不新增该 Context：

- 不依靠 Discourse Window 将历史 Synthesis 正文重新作为事实输入；
- 没有显式结构化引用时要求用户明确所指结论；
- 未来如确有产品需求，再独立设计 `DERIVED_RESULT_CONTEXT`；
- 不通过保存 Synthesis 正文实现该能力。

### 12.16 模型权限

模型只能提出闭集引用信号，例如：

```json
{
  "referenceKind": "ABSOLUTE_POSITION",
  "position": 2,
  "expectedResultKind": "RECOMMENDATION"
}
```

模型不能返回任意 Subject ID、选择 Context、决定 Result Item、改变结果顺序、把“那个”默认解释为第一名或绕过 Context 授权。

服务端根据授权 Binding 完成最终解析。模型不可用时，确定性中文序数、代词和闭集表达仍须工作；无法唯一解析时澄清。

### 12.17 Eval 基线

至少覆盖：

1. 单主体 Recent Context 的“它”；
2. Compare A/B 的第二个、前者和后者；
3. Compare A/B/C 的“后者”澄清；
4. Recommendation A/B/C 的第二个推荐；
5. 多推荐结果中的“那个”澄清；
6. 单推荐结果中的“那个”；
7. Position 越界；
8. Compare 与 Recommendation 同时 Active 的“第二个”歧义；
9. Active A 与 Page B 的“这个项目”歧义；
10. Active A、本轮显式 B 选择 B；
11. 显式 Handle A、本轮显式 B 产生 Context Conflict；
12. v1 Recommendation Context 不猜测第二个推荐；
13. Result Item 不属于 Handle 时 fail closed；
14. 客户端改变顺序不影响服务端 Context 顺序；
15. 模型返回不存在 Position/Subject 时丢弃候选；
16. 没有 Current Item 的“下一个”澄清；
17. 普通 General 问题不消费 Ordered Context。

### 12.18 暂定决议

1. 第一版不新增独立 `RESULT_SET` Context。
2. 新增可复用 `OrderedSubjectSelection`。
3. Recent Context 可选保存 Compare 或多主体结果顺序。
4. Recommendation Context 保存实际 Selected Results 和 Batch ID。
5. Recommendation 结果来自 Executor Projection，Committer 不重新计算。
6. 同一个 Recommendation Handle 同时支持 Refine 和结果项引用。
7. Result Item 使用 Context 内不透明 `resultItemId`。
8. 自然语言序数按 Position 解析，显式操作按 Item ID 解析。
9. 单数指代只有唯一候选时才解析。
10. 前者/后者只适用于恰好两个 Item。
11. 多个 Active Ordered Context 同时匹配时必须澄清。
12. Active Context 与不同 Page Hint 同时匹配指代时必须澄清。
13. v1 Context 不追认未声明的顺序语义。
14. Recommendation v1 不猜测实际推荐结果。
15. 第一版暂缓无焦点的上一个/下一个。
16. 第一版不新增 Derived Result Context，不隐式解析“这个结论”。
17. 模型只能识别闭集指代类型和位置，最终主体由服务端授权 Context 决定。
18. 增加 `contextReference.resultItemId` 作为显式结果项选择契约。

## 13. 决议九：身份引用、结果引用与内容版本

### 13.1 三种版本绑定策略

```text
LATEST_REVALIDATED
SNAPSHOT_SELECT_THEN_LATEST
SNAPSHOT_STRICT
```

- `LATEST_REVALIDATED`：Context 只提供稳定身份，回答时在当前公开内容快照中重新验证和检索；
- `SNAPSHOT_SELECT_THEN_LATEST`：先按旧结果顺序确定所指主体，再以当前公开内容重新验证并回答；
- `SNAPSHOT_STRICT`：操作依赖旧结果、候选范围、排序或确认状态，版本变化后不能自动重算。

### 13.2 身份型引用

“它/这个项目/该案例”等单主体身份引用采用 `LATEST_REVALIDATED`：

```text
旧 Context 解析稳定 Subject ID
  -> 在本轮不可变当前内容快照中验证主体仍公开存在
  -> 使用当前版本重新执行 Portfolio Task
  -> 成功后创建当前版本子 Context
```

不能沿用旧 Claim、Evidence、Passage、标题或回答正文。稳定 ID 相同而标题、Alias、摘要或 Route 调整，不改变主体身份；最终只使用当前公开信息。

重新验证至少检查 Subject Type、稳定 ID、公开状态、任务所需 Facet/Dimension 和当前 P3 Evidence 约束。

主体已删除或取消发布时：

```text
REVALIDATION_FAILED
reasonCode = REFERENCED_SUBJECT_UNAVAILABLE
```

不得改用相似名称主体、页面 Hint 或旧事实。

### 13.3 顺序引用的两阶段语义

Compare 或 Recommendation 中的“第二个/前者/指定 Result Item”采用 `SNAPSHOT_SELECT_THEN_LATEST`：

```text
旧 Ordered Selection
  -> 按旧 Position/Result Item ID 选择稳定 Subject ID
  -> 在当前版本重新验证该主体
  -> 使用当前版本回答新的 Portfolio 问题
```

旧结果顺序决定“用户指的是谁”，当前内容决定“现在能对这个主体说什么”。不能在当前版本重算结果集后再取新的第二项。

### 13.4 Recommendation 的两种使用方式

#### 查看旧推荐项详情

例如“第二个推荐项目的架构”：旧 Recommendation Rank 选择主体，当前版本重新验证并回答，采用 `SNAPSHOT_SELECT_THEN_LATEST`。

即使该主体在当前新 Recommendation 中不再排名第二，用户所指仍是旧结果中的该主体。

#### Recommendation Refine

“换一个推荐”“排除刚才结果后重新推荐”依赖旧候选范围、已选结果、Exclusions、排序语义和 contentVersion，采用 `SNAPSHOT_STRICT`。

版本变化后不自动重算，返回 Context Invalidated，要求用户基于当前内容重新开始 Recommendation。

第一版不实现 `scopeFingerprint` 或 `candidateCorpusFingerprint` 优化；即使全局版本变化与推荐候选无关，也按严格版本处理。

### 13.5 精确 Public Reference 重验证

精确 Claim/Evidence/Public Reference 不宜仅依赖全局 contentVersion，否则无关内容更新会导致不必要失效。

建议使用 `LATEST_REVALIDATED` 的严格变体 `EXACT_REFERENCE_REVALIDATE`：

1. 在当前版本查找相同 Subject、Claim、Evidence 和 Public Reference；
2. 验证仍处于公开状态；
3. 验证关键公开字段或完整性 Fingerprint 未变化；
4. 相同则允许在当前版本继续；
5. 改变或删除则 stale，不替换为相似 Claim。

```java
record PublicReferenceBinding(
        SubjectReference subject,
        Set<String> claimIds,
        Set<String> evidenceIds,
        Set<String> publicReferenceKeys,
        String sourceFingerprint,
        String contentVersion) {}
```

Fingerprint 只用于完整性判断，不包含正文，不提供给模型。

### 13.6 统一 Context Version Policy

版本规则不散落在 Router、Planner 和 Executor：

```java
interface ContextVersionPolicy {
    ContextVersionDecision evaluate(
            ContextUseIntent useIntent,
            AuthorizedRoutingContextSnapshot context,
            RuntimeAnswerContent currentContent);
}
```

`ContextUseIntent`：

```text
SUBJECT_IDENTITY
ORDERED_ITEM_SELECTION
RECOMMENDATION_REFINEMENT
EXACT_PUBLIC_REFERENCE
PLAN_CONFIRMATION
```

`ContextVersionDecision`：

```text
ACCEPT_CURRENT
REVALIDATE_TO_CURRENT
SELECT_SNAPSHOT_THEN_REVALIDATE
REJECT_STALE
REJECT_SUBJECT_UNAVAILABLE
REJECT_REFERENCE_CHANGED
```

Router 和执行链只消费 Policy Decision，不各自发明规则。

### 13.7 策略矩阵

| 使用方式 | 策略 | 版本变化后的行为 |
|---|---|---|
| Page Hint | `LATEST_REVALIDATED` | 当前 Catalog 校验 |
| Recent Fact 单主体 | `LATEST_REVALIDATED` | 主体存在则使用最新版 |
| Compare 原主体集合 | `LATEST_REVALIDATED` | 当前版本重新比较同一批主体 |
| Compare 中第二个 | `SNAPSHOT_SELECT_THEN_LATEST` | 旧顺序选主体，最新版回答 |
| Recommendation 中第二个的详情 | `SNAPSHOT_SELECT_THEN_LATEST` | 旧 Rank 选主体，最新版回答 |
| Recommendation Refine | `SNAPSHOT_STRICT` | 版本变化则失效并重启 |
| 换一个推荐 | `SNAPSHOT_STRICT` | 版本变化则失效并重启 |
| 精确 Claim/Evidence 引用 | `EXACT_REFERENCE_REVALIDATE` | Fingerprint 相同可继续，变化则失效 |
| Plan Confirmation | `SNAPSHOT_STRICT` | 版本变化则 Plan Invalidated |
| Preset Contract | 现有严格策略 | Contract/内容变化则失效 |

### 13.8 Context 生命周期状态

必须区分：

| 状态 | 语义 |
|---|---|
| `EXPIRED` | 超过 Idle/Absolute TTL，Handle 不再可用，不执行版本重验证 |
| `STALE` | Context 未过期，但严格结果语义与当前内容不兼容 |
| `SUBJECT_UNAVAILABLE` | 允许身份重验证，但引用主体已删除或取消发布 |
| `REFERENCE_CHANGED` | 精确公开引用可定位，但完整性 Fingerprint 已变化 |
| `STORE_UNAVAILABLE` | 无法安全判断 Context 是否存在、过期或 stale |

公共安全 Reason Code：

```text
CONTEXT_REFERENCE_EXPIRED
CONTEXT_RESULT_STALE
REFERENCED_SUBJECT_UNAVAILABLE
REFERENCED_PUBLIC_SOURCE_CHANGED
CONTEXT_RESOLUTION_UNAVAILABLE
```

不得合并成单一 `CONTEXT_INVALID`。

### 13.9 Context Invalidated 与 Task Stale

整个请求只依赖一个显式 Strict Context 时，在现有 `ANSWER` 响应信封内返回专门的 Turn Disposition：

```text
responseKind = ANSWER
answerResolution = NEEDS_CLARIFICATION
agentTurn.disposition = CONTEXT_INVALIDATED
contextInvalidation.reasonCode = CONTEXT_RESULT_STALE
contextInvalidation.recoveryAction = RESTART_FROM_CURRENT_CONTENT
```

它与 `PLAN_INVALIDATED` 一样属于 `ANSWER` 内部的 Turn Disposition，不扩张顶层 `responseKind` 闭集；两者语义独立。

路由优先级由 `agentTurn.disposition` 决定：当
`disposition=CONTEXT_INVALIDATED` 与 `answerResolution=NEEDS_CLARIFICATION`
并存时，前端必须进入专用上下文失效恢复流程，而不是通用澄清流程。
`contextInvalidation` 是顶层字段，与 `agentTurn` 同级；该 disposition 必须伴随
非空 `contextInvalidation` 和空 `blocks`，反向出现孤立的 `contextInvalidation`
同样属于契约违规。

多任务中只有一个 Context Task stale 时：

```text
Context Task public status = STALE
Context Task reasonCode = CONTEXT_RESULT_STALE
其他合法 Task 正常返回
Turn 可为 PARTIALLY_ANSWERED
```

局部失效不设置整轮 `agentTurn.disposition=CONTEXT_INVALIDATED`。`STALE` 不归为 `UNAVAILABLE`、`NOT_SUPPORTED`、`FAILED` 或一般 `REJECTED`。

### 13.10 不自动重启 stale 操作

服务端可以返回安全恢复动作，但不能自动：

- 创建新 Recommendation；
- 复用旧排序；
- 改变候选集合；
- 替用户接受新内容版本；
- 将 Strict Context 原地升级到当前版本。

用户重新发起后才产生新的 Context 和 Batch。具体恢复交互由前端 Agent 设计。

### 13.11 TTL 触碰规则

沿用 P3：

```text
Idle TTL = 24h
Absolute TTL = 7d
```

成功授权且实际被 Task 使用的显式 Handle、Active Context 或 Result Item 可以更新 `lastAccessedAt`。

以下情况不更新：

- 只加载 Active Candidate 但未绑定；
- 类型不兼容或指代歧义；
- Context stale；
- 主体不可用；
- Handle 校验失败；
- 只投影 Safe Context Summary。

Absolute TTL 永远不延长。

### 13.12 Context 不自动删除或改写

内容版本变化不立即删除 Context。它可以保留到 TTL，以返回准确 stale 原因、保留不可变 Parent 链和 Result Item 映射。

但 stale Context：

- 不能继续 Strict 操作；
- 不能重新成为当前版本 Active Context；
- 不能因读取而延长 Idle TTL；
- 不能被 Committer 原地改写到新版本。

Identity Revalidation 成功后创建当前版本子 Context，并保留 Parent Handle。

### 13.13 单请求不可变内容快照

Context Version Policy、Router、Planner、Retriever、Validator、Mapper 和 Context Commit 必须使用同一个请求级不可变 `RuntimeAnswerContent` 快照。

本轮所有新 Context、公开引用和响应 DTO 使用同一 `currentContentVersion`。

Retriever fallback 返回不同版本属于：

```text
EVIDENCE_INTEGRITY_FAILURE
```

不是 Context stale，也不能混合两个版本的材料。

### 13.14 公共契约

成功重新验证：

```json
{
  "contextResolution": {
    "mode": "REVALIDATED_TO_CURRENT",
    "contextType": "RECENT_SEMANTIC_TASK",
    "currentContentVersion": "v2"
  }
}
```

Strict Context 失效：

```json
{
  "responseKind": "ANSWER",
  "answerResolution": "NEEDS_CLARIFICATION",
  "agentTurn": {
    "disposition": "CONTEXT_INVALIDATED"
  },
  "contextInvalidation": {
    "reasonCode": "CONTEXT_RESULT_STALE",
    "recoveryAction": "RESTART_FROM_CURRENT_CONTENT",
    "contextType": "RECOMMENDATION",
    "currentContentVersion": "v2"
  },
  "blocks": []
}
```

公共协议不必输出旧版本号；内部诊断可以记录安全版本标识。

公共闭集：

```text
ContextInvalidationRecoveryAction
  RESTART_FROM_CURRENT_CONTENT
  RESELECT_RESULTS
  REASK_WITHOUT_CONTEXT

ContextResolutionMode
  REVALIDATED_TO_CURRENT
```

Reason 与恢复动作的确定性映射：

| Reason Code | Recovery Action |
|---|---|
| `CONTEXT_RESULT_STALE` | `RESTART_FROM_CURRENT_CONTENT` |
| `REFERENCED_PUBLIC_SOURCE_CHANGED` | `RESTART_FROM_CURRENT_CONTENT` |
| `REFERENCED_SUBJECT_UNAVAILABLE` | `RESELECT_RESULTS` |
| `CONTEXT_REFERENCE_INVALID` | `REASK_WITHOUT_CONTEXT` |
| `CONTEXT_REFERENCE_EXPIRED` | `REASK_WITHOUT_CONTEXT` |

`CONTEXT_RESOLUTION_UNAVAILABLE` 是能力不可用，不是已确认的 Context 失效，不得
伪装成 `CONTEXT_INVALIDATED` 或附带上述恢复动作。所有恢复动作都只由用户主动
触发；前端不得自动重放、自动去掉 Context 或静默切换主体。没有发生重新验证时
省略 `contextResolution`；内部的 `ACCEPT_CURRENT`、Strict/Snapshot 决策不进入
公共 `contextResolution.mode`。未知 mode/action 必须 fail closed，并保留一个
不发起请求的通用安全出口。

### 13.15 Eval 基线

至少覆盖：

1. 旧单主体 Context 在新版本中重新验证；
2. 重验证成功后创建当前版本子 Context；
3. 主体删除后的 `REFERENCED_SUBJECT_UNAVAILABLE`；
4. Compare 旧第二项在新版本仍选择同一 Subject；
5. Recommendation 旧第二项在新版本查看详情仍选择同一 Subject；
6. 旧 Recommendation 在新版本执行 Refine 时 Invalidated；
7. Refine 不自动重跑；
8. 全局版本变化但精确 Claim Fingerprint 未变时允许继续；
9. Claim 改变后的 Public Source Changed；
10. Context Expired 时不进行版本重验证；
11. stale Context 不更新 Idle TTL；
12. Active Candidate 加载但未使用时不更新 Idle TTL；
13. 同一请求所有 Task 使用同一内容版本；
14. Retriever fallback 版本不一致时完整性失败；
15. General 成功、Recommendation stale 时顶层部分回答且 Task 为 STALE；
16. 旧 Context 重验证后不原地修改；
17. Strict Context stale 后不回退 Page Hint；
18. Store Unavailable 不伪装成 stale。

### 13.16 暂定决议

1. 版本策略分为 `LATEST_REVALIDATED`、`SNAPSHOT_SELECT_THEN_LATEST` 和 `SNAPSHOT_STRICT`。
2. 身份型引用在当前不可变内容快照中重新验证。
3. 顺序引用先按旧结果选择主体，再使用最新版回答。
4. Recommendation Refine 和换一个推荐严格绑定旧版本。
5. 不自动把 stale Recommendation 升级到最新内容。
6. 精确公开引用使用 ID + Fingerprint 重验证。
7. 建立统一 `ContextVersionPolicy`。
8. Expired、Stale、Subject Unavailable、Reference Changed 和 Store Unavailable 明确区分。
9. 整体依赖 stale Context 时在 `responseKind=ANSWER` 内返回 `agentTurn.disposition=CONTEXT_INVALIDATED`。
10. 多任务局部 stale 使用公共 Task Status `STALE`。
11. stale Context 不延长 Idle TTL，也不原地改写。
12. 只有实际绑定并使用的 Context 才更新 Idle TTL。
13. Identity Revalidation 成功后创建当前版本子 Context。
14. 每个请求固定一个不可变内容快照。
15. Retrieval fallback 版本不一致属于完整性失败。
16. 公共契约提供安全 Context Resolution/Invalidation 摘要。

## 14. 决议十：模型能力、配置与有效运行状态

### 14.1 产品能力与模型 Operation 分层

产品能力：

```text
PORTFOLIO_ANSWERING
GENERAL_ANSWERING
MULTI_SOURCE_ANSWERING
CROSS_DOMAIN_RELATION_ANSWERING
CONTEXT_CONTINUATION
```

模型 Operation：

```text
ROUTING_SEMANTIC_ASSIST
GENERAL_ANSWER_MATERIAL
PORTFOLIO_EXPRESSION
CROSS_DOMAIN_EXPRESSION
```

模型不可用不自动等于产品能力不可用。Portfolio 与 Cross-domain Relation 都必须具有确定性主链或 fallback。

### 14.2 Operation 职责

#### Routing Semantic Assist

- 确定性路由无法唯一确定时提出闭集主体、Goal 和 Relation 候选；
- 不能直接创建 Task、决定 Portfolio 事实或绕过服务端验证；
- 输入只包含当前问题、受控 Public Subject Catalog 和安全确定性信号；
- 当前实现主要是 Subject Resolution Assist，P5 扩展后仍不是完整 Router。

#### General Answer Material

- 生成结构化 `GeneralAnswerMaterialDraft`；
- 可使用受限 Discourse Window；
- 不能生成 Portfolio 事实、公开引用或跨域 Relation；
- 必须经过 General Material Codec/Validator。

#### Portfolio Expression

- 只表达 P3 已决定的 Portfolio Material；
- 不决定事实、推荐集合、排序、状态或贡献归属；
- 第一版继续只允许 `FACT` Material Kind；
- Comparison/Recommendation Model Expression 不因 P5 自动开放。

#### Cross-domain Expression

- 只表达已批准 Allowed Relation；
- 不创建 Relation，不读取完整历史，不修改双域 Material；
- 第一版模型允许 `CONCEPT_APPLICATION`、`PRACTICE_COMPARISON`；
- `EVIDENCE_BASED_JUDGMENT` 第一版可只使用确定性 Composer。

### 14.3 不增加模型 Recommendation Generation

Recommendation 候选、排序和授权 Scope 继续由 P3 确定性决定。未来若模型只润色 Recommendation 说明，它仍属于 `PORTFOLIO_EXPRESSION` 的受约束 Material Kind，不是 Recommendation Generation。

### 14.4 Operation Policy

多个 Operation 可以共享 Provider Credential 和 Adapter，但必须分别拥有启用状态、数据审批、输入边界、输出 Schema、Timeout、预算、允许 Kind、fallback 和 Eval 门槛。

```java
enum ModelOperation {
    ROUTING_SEMANTIC_ASSIST,
    GENERAL_ANSWER_MATERIAL,
    PORTFOLIO_EXPRESSION,
    CROSS_DOMAIN_EXPRESSION
}

interface ModelOperationPolicy {
    ModelOperationDecision evaluate(
            ModelOperation operation,
            ModelOperationRequestDescriptor descriptor);
}
```

General 获批不自动授权 Routing、Portfolio Expression 或 Cross-domain Expression。

### 14.5 数据暴露档案

代码闭集：

```text
CURRENT_QUESTION
PUBLIC_SUBJECT_CATALOG
BOUNDED_DISCOURSE_WINDOW
VERIFIED_PORTFOLIO_MATERIAL
VALIDATED_GENERAL_MATERIAL
ALLOWED_RELATIONS
```

| Operation | 允许数据 |
|---|---|
| Routing Assist | 当前问题、公开主体目录、安全确定性信号 |
| General Material | 当前问题、受限 Discourse Window、General 参数 |
| Portfolio Expression | 验证后的 Portfolio Material、公开引用别名 |
| Cross-domain Expression | 验证后的双域 Material、Allowed Relation、Caveat |

配置只能启停 Operation，不能扩大其数据暴露档案。

禁止 Routing 读取完整历史、General 读取 Portfolio Context Store、Portfolio Expression 读取用户历史、Synthesis 读取原始历史或全量内容库。

### 14.6 操作级配置

概念配置：

```yaml
portfolio:
  agent:
    cross-domain-relations:
      enabled: false

    model-operations:
      routing-semantic-assist:
        mode: DISABLED
        provider-ref: conversational-default
        timeout: 800ms

      general-answer-material:
        mode: DISABLED
        provider-ref: conversational-default
        timeout: 5s
        max-history-messages: 12

      portfolio-expression:
        mode: DISABLED
        provider-ref: expression-default
        timeout: 3s
        allowed-material-kinds: [FACT]

      cross-domain-expression:
        mode: DISABLED
        provider-ref: expression-default
        timeout: 3s
        allowed-synthesis-kinds:
          - CONCEPT_APPLICATION
          - PRACTICE_COMPARISON
```

第一版 Mode 只支持：

```text
DISABLED
ENABLED
```

不增加语义不确定、可能导致隐式外发的 `AUTO`。

`cross-domain-relations.enabled` 是产品能力开关，独立于 `cross-domain-expression.mode`：

- `false`：普通 General + Portfolio Multi-source Answering 仍可工作，但 Router 不得把两个独立答案拼接成关系结论；显式关系 Goal 的 Primary Synthesis 返回 `UNAVAILABLE`、`reasonCode=CROSS_DOMAIN_RELATION_DISABLED`，已有单域 Block 可以使整轮成为 `PARTIALLY_ANSWERED`；
- `true` 且 Cross-domain Expression 为 `DISABLED`：允许 Deterministic Relation Composer 提供关系回答；
- `true` 且模型在获批 Shadow Lane 运行：公开结果仍取确定性路径，模型 Draft 只进入校验与 Eval，不影响公共响应；
- `true` 且 Cross-domain Expression 为 `ENABLED`：模型只能在确定性事实、Allowed Relation 和 Caveat 约束内改善表达，失败时回退确定性 Composer。

Shadow Lane 不是第三种持久产品 Mode，不改变第一版 `DISABLED / ENABLED` 闭集；它由发布/Eval 环境控制模型结果是否有资格进入公共响应。

### 14.7 Provider、审批与 Credential

Provider Registry 保存 Provider 类型、Endpoint、模型标识、Credential 引用和连接参数；Operation 配置保存是否允许该操作、数据审批状态、Provider Reference、预算和 Schema。

Credential 可以共享，审批必须独立。API Key 存在不能被当成操作审批。

### 14.8 严格启动规则

Mode 为 `DISABLED` 时允许缺少 Provider、Credential、审批或运行依赖，Effective Readiness 为 `DISABLED`。

Mode 为 `ENABLED` 时必须满足：

- 实现存在；
- Provider Ref 存在；
- Operation 审批明确允许；
- 必需 Credential 可解析；
- Timeout/预算合法；
- Allowed Kind 非空且实现支持；
- Codec/Validator 已注册；
- 合法 fallback 已定义，或产品明确接受无 fallback 的 Task Unavailable。

静态条件缺失必须启动失败，不能显示 Enabled 后在运行时静默视为 Disabled。

### 14.9 旧配置迁移

公开 Agent 入口总开关可以保留，但一个全局 `conversational-model.enabled` 不再推导全部模型能力。

旧键可以作为 Deprecated Alias，但：

- 有明确移除版本；
- 旧键与新键同时存在时启动失败；
- 不允许新键静默覆盖旧键；
- 新 Operation 默认全部关闭；
- 测试显式配置每个 Operation。

### 14.10 三层运行状态

#### Desired Mode

```text
DISABLED
ENABLED
```

#### Effective Readiness

```text
DISABLED
UNIMPLEMENTED
BLOCKED_BY_POLICY
INVALID_CONFIGURATION
DEPENDENCY_NOT_READY
READY_TO_ATTEMPT
```

#### Runtime Outcome

```text
NOT_ATTEMPTED
SUCCEEDED
FALLBACK_USED
TEMPORARILY_UNAVAILABLE
OUTPUT_REJECTED
BUDGET_REJECTED
```

`READY_TO_ATTEMPT` 只表示静态条件满足，不表示 Provider 健康。合法组合包括：

```text
mode = ENABLED
readiness = READY_TO_ATTEMPT
runtimeOutcome = TEMPORARILY_UNAVAILABLE
```

### 14.11 Operation Probe

Live Probe 按 Operation 拆分：Routing Assist、General Material、Portfolio Expression、Cross-domain Expression。

每个 Probe：

- 使用固定 Canary；
- 不携带访客问题、真实 Context 或私有内容；
- 经过同一生产 Codec/Validator；
- 不创建正常会话 Context；
- 不在应用启动时自动外发；
- 由显式运维命令或发布验收触发。

Probe 只证明探测时刻该 Operation 的生产 Seam 可用，不将 Readiness 永久标记为 Healthy。

### 14.12 Operation fallback

| Operation | 失败时行为 |
|---|---|
| Routing Assist | 回退确定性路由；仍不明确则澄清 |
| General Material | Task `UNAVAILABLE`；当前无可信通用知识 fallback |
| Portfolio Expression | P1/P3 Deterministic Composer |
| Cross-domain Expression | Deterministic Relation Composer |

各 Operation 不能借用另一个 Operation 的 Provider 绕过数据边界。例如 Cross-domain 关闭时，General Provider 不能被当作自由 Synthesis Provider。

### 14.13 产品能力有效状态

- Portfolio Answering 由 Portfolio Executor、公开内容、P3 Evidence 和 Deterministic Composer 决定，模型表达关闭时仍可用；
- General Answering 当前依赖 General Material Operation；
- Multi-source Answering 要求 General、Portfolio 和 Task 聚合可用，不要求 Cross-domain 模型；
- Cross-domain Relation Answering 要求双域 Material、Relation Policy 和 Deterministic Relation Composer，可处于 `AVAILABLE_DETERMINISTIC`。

内部产品能力状态 `AVAILABLE_DETERMINISTIC` 对外投影为 `AVAILABLE_WITH_DETERMINISTIC_FALLBACK`。前者表达内部主链形态，后者是公共粗粒度能力提示，不能被实现成两套独立能力状态。

概念内部 DTO：

```java
record EffectiveModelOperationStatus(
        ModelOperation operation,
        CapabilityDesiredMode desiredMode,
        CapabilityReadiness readiness,
        String providerReference,
        boolean fallbackAvailable,
        Set<String> allowedKinds) {}
```

### 14.14 Operator 与公共状态

Operator 状态用于启动诊断、发布验收、运维 CLI、安全内部 Actuator 和 Eval，可区分 Policy、Configuration、Dependency 和 Ready To Attempt。

公共状态如确有需要，只提供粗粒度闭集：

```text
AVAILABLE
AVAILABLE_WITH_DETERMINISTIC_FALLBACK
UNAVAILABLE
```

公共状态不得暴露 Provider、模型名、Endpoint、Credential 或审批细节，也不能替代实际 Task Outcome。

### 14.15 请求级 Allowance

```java
record ModelOperationAllowance(
        ModelOperation operation,
        int maxAttempts,
        Duration timeout,
        int maxInputUnits,
        int maxOutputCharacters) {}
```

预算按 Operation 核算，不因前一个 Operation 未调用就无限转移给后一个 Operation。

Cross-domain Expression 只有在上游 Material 完成、Allowed Relation 非空且确定性 fallback 已构建后才获得调用资格。

### 14.16 安全诊断

允许记录：

```text
operation
desiredMode
readiness
runtimeOutcome
fallbackUsed
latencyBucket
errorCategory
```

禁止记录访客问题、模型响应、Material 正文、Prompt、API Key、Context Handle、ResumeToken 或 Provider 原始错误体。

### 14.17 配置与运行矩阵

| Mode | 静态配置 | Policy | 运行结果 | 行为 |
|---|---|---|---|---|
| DISABLED | 不要求 | 不要求 | 不调用 | 使用 Operation 的既定 fallback |
| ENABLED | 合法 | 未批准 | 启动失败 | 防止假启用 |
| ENABLED | 缺 Provider/Credential | 已批准 | 启动失败 | 防止假启用 |
| ENABLED | 合法 | 已批准 | 成功 | 发布模型结果 |
| ENABLED | 合法 | 已批准 | Timeout | 使用自己的 fallback |
| ENABLED | 合法 | 已批准 | Schema Invalid | 原子丢弃并 fallback |
| ENABLED | 合法 | 已批准 | Validator Reject | 原子丢弃并 fallback |
| ENABLED | 合法 | 已批准 | 无 fallback | Task `UNAVAILABLE` |

### 14.18 Eval 基线

至少覆盖：

1. 四个 Operation 默认关闭；
2. 单独启用 General 不调用 Routing/Synthesis；
3. 单独启用 Routing 不生成 General Answer；
4. Portfolio Expression 只允许已批准 Material Kind；
5. Cross-domain 模型关闭时确定性关系仍可回答；
6. General 关闭时 Portfolio 仍可回答；
7. Enabled 缺审批或 Credential 时启动失败；
8. 旧键与新键同时出现时启动失败；
9. Routing 失败后的确定性成功或澄清；
10. Portfolio/Cross-domain Expression 失败后的确定性 fallback；
11. General Provider 失败时 Task 为 `UNAVAILABLE`；
12. Probe 不创建 Context；
13. `READY_TO_ATTEMPT` 不被解释为 Provider Healthy；
14. 公共状态不暴露 Provider/Model；
15. Operation A 的审批不授权 Operation B；
16. Synthesis 不读取 Discourse Window；
17. Allowance 不跨 Operation 无限转移；
18. `cross-domain-relations.enabled=false` 时 Multi-source 仍可回答，显式 Primary Relation Goal 返回 `CROSS_DOMAIN_RELATION_DISABLED`；
19. `cross-domain-relations.enabled=true` 且模型关闭时，对外能力状态正确投影为 `AVAILABLE_WITH_DETERMINISTIC_FALLBACK`；
20. Shadow Lane 的模型 Draft 永远不影响公共响应。

### 14.19 暂定决议

1. 产品能力与模型 Operation 分层。
2. 第一版 Operation 为 Routing Assist、General Material、Portfolio Expression、Cross-domain Expression。
3. 不新增模型 Recommendation Generation。
4. Provider Credential 可以共享，Operation 审批和数据边界不能共享。
5. 每个 Operation 使用代码闭集的数据暴露档案。
6. 模型配置改为操作级 `DISABLED/ENABLED`，不增加 `AUTO`。
7. Operation Enabled 但静态条件不完整时启动失败。
8. 不使用全局 Conversational Model 开关推导全部能力。
9. Desired Mode、Effective Readiness、Runtime Outcome 三层分离。
10. `READY_TO_ATTEMPT` 不表示 Provider Healthy。
11. Probe 按 Operation 显式运行，不在启动时自动外发。
12. 各 Operation 只使用自己的 fallback。
13. Portfolio 与 Cross-domain 产品能力可在模型关闭时确定性运行。
14. Cross-domain Relation Answering 使用独立产品开关 `portfolio.agent.cross-domain-relations.enabled`，不影响普通 Multi-source Answering。
15. 内部 `AVAILABLE_DETERMINISTIC` 对外投影为 `AVAILABLE_WITH_DETERMINISTIC_FALLBACK`。
16. Shadow Lane 不是新的持久产品 Mode，模型结果不进入公共响应。
17. Operator 状态与公共粗粒度状态分离。
18. 请求级模型预算按 Operation 核算。
19. 旧键与新键不得同时出现。
20. 安全诊断不记录问题、回答、Material、Prompt、Handle 或 Provider 原始错误。

## 15. 决议十一：检索模式、内容后端与降级矩阵

### 15.1 五个正交维度

```text
Retrieval Intent
Corpus Backend
Search Strategy
Vector Capability
Fallback Layer
```

`Retrieval Intent`：

```text
EXACT_SUBJECT
REFERENCE_SCOPED
CONTEXT_REVALIDATION
PRESET_CONTRACT
SEMANTIC_FACT_DISCOVERY
RECOMMENDATION_CANDIDATE_DISCOVERY
```

`Corpus Backend`：

```text
BUNDLE
POSTGRESQL
```

`Search Strategy`：

```text
EXACT
KEYWORD
HYBRID
```

`Vector Capability`：

```text
DISABLED
READY
FAILED
```

`Fallback Layer`：

```text
NONE
STRATEGY_FALLBACK
BACKEND_FALLBACK
```

这些维度不能继续压缩为一个 `RetrievalProfile`。

### 15.2 Exact 由 Intent 决定

显式 Subject、Context Revalidation、Preset Contract、精确 Claim/Public Source 和已选择 Result Item 等操作由受控身份确定范围，必须使用 `EXACT`。

Exact 的语义是：

> 主体范围已经由受控身份确定，不允许语义搜索扩大主体集合。

它仍可在主体内按 Claim Category、Facet、Dimension 或 Reference Key 过滤，但默认 Discovery Strategy 不能把它改成 Hybrid。

### 15.3 Keyword 与 Hybrid

Keyword 使用规范化 Query、受控 Topic/Category、FTS/Keyword Matching 和确定性排序，不依赖 Embedding。

Hybrid 使用 Keyword Candidate、Local Vector Candidate、确定性 Fusion 和 Context Validation，需要 Vector Capability Ready。

Hybrid 不等于 PostgreSQL，也可以作用于 Bundle 或 PostgreSQL Adapter；PostgreSQL 不等于 pgvector，类型和诊断命名必须反映真实实现。

### 15.4 Discovery Mode 与 Strategy

不再把 `DISABLED` 放进 Search Strategy。建议：

```text
retrieval.discovery.mode = DISABLED | ENABLED
retrieval.discovery.strategy = KEYWORD | HYBRID
```

行为：

```text
strategy = KEYWORD
  -> 直接执行 Keyword
  -> degraded = false
```

```text
strategy = HYBRID + vector READY
  -> 执行 Hybrid
```

```text
strategy = HYBRID + vector runtime failure
  -> Policy 允许时退 Keyword
  -> degraded = true
  -> STRATEGY_FALLBACK
```

```text
discovery.mode = DISABLED
  -> 非 Exact Discovery 不可用
```

Keyword 配置不能先请求 Hybrid，再通过异常路径退回 Keyword。

### 15.5 建议配置

```yaml
portfolio:
  retrieval:
    backend:
      primary: POSTGRESQL
      fallback: BUNDLE

    discovery:
      mode: ENABLED
      strategy: HYBRID
      fallback-strategy: KEYWORD

    vector:
      mode: ENABLED
      engine: LOCAL_ONNX
      model-directory: ...

    exact:
      enabled: true
```

约束：

- Primary 为 Bundle 时不再配置 Backend Fallback；
- Primary 为 PostgreSQL 时可选 Bundle Fallback；
- Keyword Strategy 允许 Vector Disabled；
- Hybrid Strategy 要求 Vector Enabled 且静态依赖合法；
- `fallback-strategy=KEYWORD` 只在 Hybrid 运行失败时生效；
- Exact 默认可用，依赖 Exact 的产品能力不能在它不可用时伪装 Ready。

### 15.6 Effective Retrieval Plan

PostgreSQL 与 Bundle 实现同一语义端口，执行上层已经解析的 Intent、Strategy、Subject Scope、Content Version 和 Limit。

```java
record EffectiveRetrievalPlan(
        RetrievalIntent intent,
        CorpusBackend primaryBackend,
        SearchStrategy primaryStrategy,
        Optional<SearchStrategy> fallbackStrategy,
        Optional<CorpusBackend> fallbackBackend,
        String expectedContentVersion) {}
```

Adapter 不自行将 Keyword 改成 Hybrid，也不因自己是 PostgreSQL 而改变任务语义。

### 15.7 单一决策与编排权

```text
PortfolioExecutionPlanner
  -> Retrieval Intent

RetrievalModePolicy
  -> Effective Retrieval Plan

PortfolioEvidenceCapability
  -> 执行 Plan 与受控 fallback
```

Retriever Adapter 只执行，不重新决策。

### 15.8 单一 Fallback Orchestrator

只保留 `PortfolioEvidenceCapability` 编排 fallback。底层 Adapter 不再组合 `FailoverPortfolioRetriever`，避免同一后端重复调用、Attempt 计数失真和降级来源不明。

每个逻辑检索只有一个 Primary Attempt，并且至多增加一个 Fallback Attempt，总数最多两个：

```text
Attempt 1: Primary Plan
Attempt 2: Optional Fallback Plan
```

失败后按闭集分类选择最有价值的唯一 fallback：

- Vector/Embedding 失败：同 Backend + Keyword；
- PostgreSQL Connection/Timeout：Bundle + 可执行 Strategy；
- 同一 Vector 已失败时，Bundle 直接使用 Keyword；
- 完整性失败、业务 Empty 或证据不足：不 fallback。

### 15.9 Fallback 触发矩阵

| 主 Attempt 结果 | Fallback | 行为 |
|---|---:|---|
| 成功且充分 | 否 | 使用结果 |
| Business Empty | 否 | EMPTY |
| Evidence Insufficient | 否 | 不伪装基础设施故障 |
| Subject 不存在 | 否 | 受控内容结果 |
| Content Version 不匹配 | 否 | Integrity Failure |
| Vector 初始化/推理失败 | 是 | 同 Backend Keyword |
| PostgreSQL Connection Unavailable | 是 | Bundle 可执行策略 |
| PostgreSQL Timeout | 是 | Bundle 可执行策略 |
| SQL/Schema Integrity Error | 否 | Fail Closed |
| Bundle 内容不存在 | 否 | Capability Unavailable |
| Promotion Validator 失败 | 否 | Integrity Failure |
| Budget 不足 | 否 | Not Executed Budget |

只有基础设施类可恢复失败触发 fallback。

### 15.10 Fallback Policy

```java
interface RetrievalFallbackPolicy {
    Optional<EffectiveRetrievalAttempt> fallbackFor(
            EffectiveRetrievalPlan plan,
            RetrievalAttemptFailure failure,
            RetrievalAllowance remainingAllowance);
}
```

闭集失败分类：

```text
VECTOR_UNAVAILABLE
BACKEND_CONNECTION_UNAVAILABLE
BACKEND_TIMEOUT
BUSINESS_EMPTY
EVIDENCE_INSUFFICIENT
CONTENT_VERSION_MISMATCH
INTEGRITY_FAILURE
BUDGET_EXHAUSTED
```

Policy 不读取异常正文，最多输出一个额外 Attempt。

### 15.11 版本一致性

所有 Backend 和 Strategy 返回 `returnedContentVersion`，Promotion 前必须满足：

```text
returnedContentVersion == request.expectedContentVersion
```

Fallback 同样必须满足。PostgreSQL v2 与 Bundle v1 不能混合回答；版本不一致是 `EVIDENCE_INTEGRITY_FAILURE`，不是普通 Backend Fallback 或 Context Stale。

### 15.12 Retrieval Execution Trace

```java
record RetrievalExecutionTrace(
        RetrievalIntent intent,
        List<RetrievalAttemptTrace> attempts,
        int selectedAttempt) {}

record RetrievalAttemptTrace(
        int attempt,
        CorpusBackend backend,
        SearchStrategy requestedStrategy,
        SearchStrategy actualStrategy,
        RetrievalAttemptStatus status,
        SafeRetrievalReason reason) {}
```

Trace 不包含 Query 正文、命中文本、SQL、Embedding 或异常正文。

Task Degradation 从真实 Trace 确定性投影，不由 Adapter 随意设置布尔值。

### 15.13 Requested 与 Actual Strategy

```text
requested=HYBRID, actual=HYBRID
  -> degraded=false

requested=HYBRID, actual=KEYWORD
  -> degraded=true
  -> RETRIEVAL_FALLBACK

requested=KEYWORD, actual=KEYWORD
  -> degraded=false
```

最终使用 Keyword 不自动代表 fallback。Exact 同样进入 Trace，但成功时不算降级。

### 15.14 Retrieval Capability Status

```java
record RetrievalCapabilityStatus(
        RetrievalDesiredMode discoveryMode,
        Set<SearchStrategy> supportedStrategies,
        Set<CorpusBackend> readyBackends,
        VectorCapabilityStatus vectorStatus,
        boolean exactAvailable,
        boolean fallbackConfigured) {}
```

Hybrid 配置但 Vector 静态依赖缺失时启动失败；运行中 ONNX 推理失败由请求级 Fallback Policy 处理，不动态改写全局配置。

### 15.15 产品能力关系

- 显式 Subject Fact/Compare 通常使用 Exact，不因 Vector Disabled 失效；
- Recommendation 通常依赖 Candidate Discovery，Discovery Disabled 时 Capability Unavailable；
- Context Revalidation 必须依赖 Exact；
- Preset Contract 使用 Exact，不受 Discovery Profile 改写；
- 精确候选 Scope 是否允许 Recommendation 由 Planner 任务语义决定，Adapter 不扩大范围。

### 15.16 Operator 与公共状态

Operator 可安全显示：

```text
primaryBackend
fallbackBackend
discoveryStrategy
fallbackStrategy
vectorReadiness
exactAvailable
backendVersionAligned
```

不输出数据库 URL、Credential、本地模型绝对路径、原始异常或内容正文。

公共响应只提供 `degraded`、安全 Degradation Kind、Task Status 和 Reason Code，不暴露 PostgreSQL、Bundle、ONNX 等内部执行细节。

### 15.17 旧配置迁移

旧：

```text
RetrievalProfile.DISABLED
RetrievalProfile.KEYWORD_ONLY
RetrievalProfile.HYBRID
```

迁移为：

```text
discovery.mode
discovery.strategy
vector.mode
backend.primary
backend.fallback
exact.enabled
```

映射：

- 旧 DISABLED -> Discovery Disabled；
- 旧 KEYWORD_ONLY -> Discovery Enabled + Keyword + Vector Disabled；
- 旧 HYBRID -> Discovery Enabled + Hybrid + Vector Enabled。

旧键与新键同时存在时启动失败。迁移后删除 Adapter 内固定 `HYBRID_ENABLED`，Retriever 完全服从 Effective Plan。

### 15.18 Eval 基线

至少覆盖：

1. Keyword 配置直接执行 Keyword；
2. Hybrid + Vector Ready 执行 Hybrid；
3. Hybrid 静态缺模型时启动失败；
4. Hybrid 运行时 Vector 失败时单次退 Keyword；
5. PostgreSQL Connection/Timeout 时单次退 Bundle；
6. Empty、Evidence Insufficient、Integrity Failure、Version Mismatch 不 fallback；
7. Fallback Backend 版本不同导致 Integrity Failure；
8. Exact Intent 无视默认 Hybrid；
9. Context Revalidation 和 Preset 使用 Exact；
10. 显式 Subject Fact 不因 Vector Disabled 失败；
11. Recommendation Discovery Disabled 时能力不可用；
12. P3 Capability 不重复调用内部 Failover Retriever；
13. 每个逻辑检索只有一个 Primary Attempt，并且至多增加一个 Fallback Attempt，总数最多两个；
14. Requested/Actual Strategy 准确记录；
15. Keyword->Keyword 不标 degraded；
16. Hybrid->Keyword 标 Retrieval Fallback；
17. PostgreSQL->Bundle 标 Content Backend Fallback；
18. 公共响应不泄露 Backend/Vector 细节；
19. 所有 Attempt 使用同一 expectedContentVersion；
20. Exact、Keyword、Hybrid 使用同一业务充分性标准。

### 15.19 暂定决议

1. 检索拆成 Intent、Backend、Strategy、Vector Capability、Fallback Layer 五轴。
2. Exact 由任务 Intent 决定，不受默认 Discovery Strategy 改写。
3. Keyword/Hybrid 是候选发现策略，不等于 Backend。
4. PostgreSQL 不等于 pgvector，命名反映真实实现。
5. Discovery Mode 与 Strategy 分离。
6. Keyword 配置直接执行 Keyword。
7. 统一 `EffectiveRetrievalPlan`，Adapter 只执行不决策。
8. 只保留 Portfolio Evidence Capability 一个 fallback Orchestrator。
9. 每个逻辑检索只有一个 Primary Attempt，并且至多增加一个 Fallback Attempt，总数最多两个。
10. 只有基础设施类可恢复失败触发 fallback。
11. Empty、Evidence Insufficient、Version Mismatch、Integrity Failure 不 fallback。
12. Fallback Policy 根据闭集失败分类确定性选择。
13. 所有 Attempt 必须与请求 contentVersion 一致。
14. 版本不一致是完整性失败。
15. 内部 Trace 区分 Requested/Actual Strategy 和 Backend。
16. Task Degradation 从 Trace 确定性投影。
17. Exact 可观测但不算降级。
18. 显式 Subject、Context、Preset 不依赖 Hybrid。
19. Recommendation Discovery 状态由产品能力计算。
20. 公共响应只暴露安全降级摘要。
21. 旧 RetrievalProfile 渐进迁移，旧新配置不能并存。

## 16. 决议十二：Eval、灰度与验收门槛

### 16.1 六个 Eval Suite

```text
Routing & Binding
Material & Support
Cross-domain Synthesis
Context & Version
Failure & Degradation
Configuration & Retrieval
```

每个 Suite 同时覆盖确定性路径、模型关闭、合法模型输出、对抗性模型输出、依赖故障和边界输入。

### 16.2 Routing & Binding

验证：

- Page Hint 不吞掉 General Intent；
- 明确指代才绑定 Context；
- 本轮显式主体优先；
- Context Conflict 正确澄清；
- 多主体提及逐项解析；
- 并列问题不自动产生 Synthesis；
- 关系问题产生正确 Synthesis 与 Fulfillment Role；
- 模型候选不能升级 Page Hint；
- 模型关闭不改变高置信确定性意图。

最低 Fixture 包括纯 General、显式 Portfolio、并列双域、关系型双域、Active/Page 冲突、显式 Handle 冲突、多主体比较、未知模型主体和 Model Off 路由。

### 16.3 Material & Support

核心不变量：

```text
Portfolio Statement with text
  -> publicSourceKeys 非空

General Block
  -> publicSourceKeys 为空

Synthesis publicSourceKeys
  -> 是 Portfolio dependency publicSourceKeys 的子集

Response publicSourceKey
  -> 顶层 Catalog 中存在且唯一

Portfolio/Synthesis Block
  -> 不因展示去重失去自己的引用关系
```

同时验证 General/Portfolio 类型隔离、逐句来源、Claim/Evidence 映射、部分覆盖、Caveat 和新旧 DTO 投影一致。

### 16.4 Cross-domain Synthesis

回答拆分为 General Claim、Portfolio Claim、Relation Claim 和 Caveat 分别评估。

Relation Verdict：

```text
SUPPORTED
SUPPORTED_WITH_REQUIRED_QUALIFIER
UNSUPPORTED
RELATION_TYPE_ESCALATED
SOURCE_DOMAIN_BLEED
PORTFOLIO_FACT_MUTATED
CAVEAT_DROPPED
```

- `SUPPORTED`：Relation Alias、双域输入和表达一致；
- `SUPPORTED_WITH_REQUIRED_QUALIFIER`：合法但必须保留限定语；
- `UNSUPPORTED`：不存在 Allowed Relation；
- `RELATION_TYPE_ESCALATED`：例如 ILLUSTRATES 被写成完整实现；
- `SOURCE_DOMAIN_BLEED`：General 知识被写成项目已验证结果；
- `PORTFOLIO_FACT_MUTATED`：数字、时间、状态、主体或贡献归属被改变；
- `CAVEAT_DROPPED`：必需限制被省略或反转。

### 16.5 Golden Case

```yaml
id:
question:
pageContext:
activeContexts:
expectedPlan:
generalMaterial:
portfolioMaterial:
allowedRelations:
expectedTaskOutcomes:
expectedPublicContract:
forbiddenClaims:
```

不把完整预期自然语言作为唯一 Oracle。权威检查 Task Shape、Subject Binding、Fulfillment Role、Support、Relation、Public Source、Caveat、Resolution、Degradation 和版本行为。

Fixture 增加：

```text
expectedRelationRequirement:
  REQUIRED
  OPTIONAL
  NONE
```

Relation Coverage 只对人工批准为 REQUIRED 的 Eligible Fixture 计算。

### 16.6 Oracle 隔离

Expected Plan、Relation、Forbidden Claim、Expected Source 和 Eval ID 不进入 Router、Relation Builder、Prompt、Retriever 或生产 Task 输入。Eval Adapter 只在生产执行完成后采集脱敏结果比较。

### 16.7 三条模型 Lane

#### Lane A：Model Off

证明确定性路由、Portfolio、确定性 Cross-domain 和 fallback 均是真实生产路径。

#### Lane B：Fake/Adversarial Provider

固定返回合法 Draft、未知 Alias、新技术、状态升级、来源漂白、Caveat 丢失、非法 JSON、超长输出、Timeout 和错误 Relation Type，验证 Codec、Validator 与原子 fallback。

#### Lane C：Live Provider

只在获批环境显式运行，通过同一生产 Seam，记录安全摘要，不修改 Golden Oracle，不成为普通 CI 稳定依赖。

### 16.8 指标

Routing：

```text
intent_exact_match
subject_binding_exact_match
task_graph_exact_match
fulfillment_role_exact_match
clarification_precision
unnecessary_clarification_rate
```

Material：

```text
portfolio_statement_support_precision
public_reference_binding_precision
general_portfolio_source_isolation_rate
material_schema_valid_rate
```

Synthesis：

```text
allowed_relation_precision
relation_coverage
source_domain_bleed_rate
portfolio_fact_mutation_rate
required_caveat_retention_rate
unsupported_relation_publish_rate
```

Context：

```text
anaphora_resolution_accuracy
ordered_reference_accuracy
context_conflict_detection_rate
stale_context_rejection_rate
version_revalidation_accuracy
```

Failure：

```text
partial_success_projection_accuracy
fallback_success_rate
false_capability_unavailable_rate
false_degraded_rate
business_empty_fallback_rate
```

Retrieval：

```text
requested_actual_strategy_accuracy
fallback_policy_accuracy
version_alignment_rate
false_sufficient_rate
```

### 16.9 发布门禁

零容忍：

```text
unsupported_relation_publish_rate = 0
source_domain_bleed_rate = 0
portfolio_fact_mutation_rate = 0
invalid_public_reference_publish_rate = 0
cross_version_material_mix_rate = 0
secret_or_private_content_leak = 0
```

确定性与契约门禁：

```text
Task graph exact match               = 100%
Public contract shape                = 100%
Subject binding on unambiguous cases = 100%
Ordered result reference             = 100%
Context version decision             = 100%
Fallback trigger classification      = 100%
Source catalog integrity             = 100%
```

模型质量建议门槛：

```text
Valid draft acceptance >= 95%
Deterministic fallback availability = 100%
Relation semantic correctness >= 98%
Required qualifier retention = 100%
```

模型合法 Draft 接受率不足不一定破坏正确性，但意味着该 Operation 不应公开启用。

### 16.10 Failure 与 Degradation Eval

Eval 必须验证最终产品状态。例如 General 成功、Portfolio 失败、Synthesis Blocked 时：

```text
Turn = PARTIALLY_ANSWERED
General Block 仍存在
Portfolio Task = FAILED/UNAVAILABLE
Synthesis Task = BLOCKED
Top-level != CAPABILITY_UNAVAILABLE
```

模型失败但确定性 fallback 成功时：

```text
Task = COMPLETED
degraded = true
Turn 可以是 ANSWERED
```

Business Empty 不触发 Backend Fallback，也不标记 Infrastructure Degraded。

### 16.11 Contract Conformance

机器验证：

- 迁移期旧字段仍存在；
- `sourceDomain` 是 Block 来源的权威字段；
- `GENERAL/PORTFOLIO` Block 同时携带旧 `sourceScope` 时必须与 `sourceDomain` 一致；
- `SYNTHESIS` Block 的旧 `sourceScope` 必须省略或为 `null`，不得映射成 `GENERAL` 或 `PORTFOLIO`；
- Public Source Catalog Key 唯一；
- Block Key 全部可解析；
- General Key 为空；
- Display Plan 与 Completed Task 的 `fulfillmentRole` 一致，并且确认后不变；
- 同一已接受请求的幂等重放保持 `blockId/statementId` 一致；
- Task Support Summary 与 Block Support 一致；
- `sourceComposition` 与实际 Block 集合一致；
- `evidenceState=MIXED` 只用于多来源；
- `STALE/UNAVAILABLE/NOT_SUPPORTED` 不合并；
- 整体 Context 失效保持 `responseKind=ANSWER`，并使用 `agentTurn.disposition=CONTEXT_INVALIDATED`；
- 不支持的 Semantic Turn Contract 返回 `HTTP 409 + AGENT_TURN_CONTRACT_UNSUPPORTED`；
- 未知枚举由消费者 fail closed 或安全降级。

### 16.12 Context 时间测试

Context 测试注入 `Clock`、Current Content Snapshot 和 Context Revision，不依赖真实等待。

覆盖 Idle/Absolute TTL、成功使用才 Touch、Stale 不 Touch、Parent/Child、v1/v2 Codec、Store Unavailable、并发 Active Revision、ResumeToken 错误和 Result Item 所属校验。

### 16.13 Retrieval Eval

Contract/Policy Lane 验证 Effective Plan、Requested/Actual Strategy、Fallback Trigger、Attempt 数、Version 和 Degradation。

Quality Benchmark 对 Keyword、Hybrid 和不同 Backend 使用同一 Fixture 与 Evidence Sufficiency Oracle，False Sufficient 优先保持为零。

### 16.14 灰度阶段

#### Stage 0：契约收口，模型关闭

开放 Page Hint 修正、Fulfillment Role、部分成功、Task/Block Support、Context Snapshot、Ordered Result、Version Policy 和 Retrieval Matrix。此阶段 `cross-domain-relations.enabled=false`，只提供来源分区和 Multi-source，不发布关系结论。

#### Stage 1：确定性 Cross-domain

开放 Allowed Relation、Deterministic Relation Composer、无模型 Synthesis 和完整公共契约；通过具体产品开关对 Eligible 环境启用。

#### Stage 2：获批环境 Model Expression

只为批准 Kind 和 Golden Dataset 开启 Cross-domain Expression，不面向普通访客全面开放。

#### Stage 3：公开灰度

只允许单主体、批准 Kind、始终存在确定性 fallback；异常立即回退，不改变产品 Resolution，并保留 Operation Kill Switch。

### 16.15 灰度隐私

公开灰度可以使用固定环境、显式部署配置、请求级非持久随机抽样、固定 Canary 和公开 Task Kind Eligibility。

禁止使用访客内容、浏览器指纹、IP、持久 Cookie、Context Handle Hash 或问题主题画像。无法安全稳定分流时采用环境级灰度。

### 16.16 Kill Switch

四个 Model Operation 分别可关闭。关闭 Cross-domain Expression 后，确定性 Relation、公共 Contract 和 Context 保持工作，不要求前端发布。

检索可从 Hybrid 切换 Keyword、从 PostgreSQL Primary 切换 Bundle Primary，但切换后仍必须满足 contentVersion 一致性。

### 16.17 发布证据包

至少包含：

```text
artifact identity
contentVersion
enabled operations
effective readiness summary
contract test summary
deterministic eval summary
adversarial provider summary
retrieval policy summary
context/version summary
live probe summary（显式要求时）
known exclusions
```

禁止包含访客问题、回答正文、Prompt、模型原始输出、Context Handle、ResumeToken、Secret 或数据库 URL。

### 16.18 P5 完成定义

1. 路线图 P5 项目全部映射到本 Spec；
2. 第 5—17 节决议均得到落实：第 5—15 节具有生产实现，第 16 节 Eval/灰度门禁通过，第 17 节迁移与清理完成；
3. 新旧公共契约迁移测试通过；
4. Model Off Lane 通过；
5. Adversarial Provider Lane 通过；
6. 零容忍门禁全部为零；
7. Context v1/v2、TTL、Version、Ordered Reference 通过；
8. Retrieval Fallback 与版本一致性通过；
9. Model Operation 默认关闭；
10. Live Provider 只在显式批准环境留证；
11. 当前实现状态文档完成校正；
12. 前端 Agent 基于公共契约完成独立设计与验收；
13. 发布候选安全证据包完整；
14. 不保留与新主链并行的隐式旧路径。

### 16.19 暂定决议

1. Eval 分为 Routing、Material、Synthesis、Context、Failure、Configuration/Retrieval 六个 Suite。
2. Oracle 检查结构语义，不把逐字全文作为唯一答案。
3. Expected Plan/Relation/Source 不进入生产输入。
4. Model Off、Adversarial Provider、Live Provider 三条 Lane 分开。
5. Synthesis 逐 Relation 判定支持、升级、来源漂白、事实篡改和 Caveat。
6. Unsupported Relation、Source Bleed、Fact Mutation、Invalid Reference、Cross-version Mix 和隐私泄露零容忍。
7. 确定性契约与版本决策要求 100%。
8. 模型表达正确率和接受率独立衡量。
9. Relation Coverage 只对人工标记 Required 的 Eligible Fixture 计算。
10. 公共 DTO 使用 Contract Conformance Tests。
11. Context 时间测试注入 Clock。
12. 不同 Retrieval Strategy 使用同一 Evidence Sufficiency Oracle。
13. 灰度顺序为契约收口、确定性 Cross-domain、获批模型环境、公开灰度。
14. 灰度不使用用户身份、内容或持久跟踪。
15. 每个 Model Operation 具有独立 Kill Switch。
16. 发布候选生成不含访客内容的安全证据包。
17. P5 完成要求生产实现、六类 Eval、前端契约消费与发布证据共同满足。

## 17. 决议十三：迁移顺序、实施切片与兼容窗口

### 17.1 迁移原则

1. 每个切片独立可编译、测试和发布。
2. 每个切片不依赖下一切片才能恢复正确行为。
3. 新能力默认关闭或尚不可由生产路由触达。
4. 公共消费者先能解析新契约，后端再产生新语义。
5. 持久格式先实现向前兼容读取，再开始写入新版本。
6. 不长期保留两个可选择的业务主链。
7. 旧路径只能是明确的兼容投影或确定性 fallback。
8. 每个切片都有完成门禁和回滚边界。

### 17.2 API 与 Semantic Turn Contract

P5 保留：

```text
POST /api/v2/answers
```

公共 Answer API 以增量字段迁移，不为 P5 新建 `/api/v3/answers`。

Semantic Turn Contract 升级为：

```text
stp-v2
```

因为 P5 改变了 Fulfillment Role、Synthesis 参数、依赖语义、主体绑定、Task Status 和 Context/Version 规则。

迁移期服务端可短期接受 `stp-v1` 与 `stp-v2`，但两者都进入同一新 Router/Core。`stp-v1` 适配器只能补充缺省字段、限制旧消费者不能安全表达的结果并投影旧形状，不能保留旧执行主链。

旧客户端请求 P5 专属语义但无法安全消费时：

```text
HTTP 409 Conflict
ApiErrorResponse.code = AGENT_TURN_CONTRACT_UNSUPPORTED
```

这不是一个 200 Answer，也不新增 `responseKind=CONTRACT_UPGRADE_REQUIRED`。客户端连当前 Semantic Turn Contract 都不支持时，不能要求它理解一个新的成功响应信封。

`ApiErrorResponse.message` 只提供安全、可本地化的刷新/升级提示，不暴露内部兼容矩阵。请求 DTO 不再用只允许 `stp-v1` 的硬编码正则作为最终策略：它先接受服务端可识别的版本格式，再由显式 Compatibility Policy 判断 `stp-v1`、`stp-v2` 或拒绝。不能静默降级成错误单域回答。兼容期结束后删除 `stp-v1` 请求适配器。

### 17.3 Slice 1：基线与契约冻结

目标：改变行为前固定现状与新协议。

工作：

- 校正当前状态文档；
- 建立 P5 Golden Dataset 骨架与 Model Off 基线；
- 定义新 DTO、JSON Schema、枚举和 `stp-v2`；
- 建立新旧字段一致性测试；
- 产出前端契约包；
- 暂不让生产 Router 生成新 Synthesis。

前端契约包至少提供：

```text
GENERAL_ONLY
PORTFOLIO_ONLY
MULTI_SOURCE
CROSS_DOMAIN_DERIVED
PARTIALLY_ANSWERED
agentTurn.disposition=CONTEXT_INVALIDATED
STALE_TASK
DEGRADED_FALLBACK
```

以及 JSON/OpenAPI Schema、字段权威性、枚举变更和兼容周期。

门禁：旧响应仍可解析，新 Fixture Schema 通过，未知枚举有安全策略，无生产行为变化。

#### Consumer Compatibility Preflight

Slice 1 完成后、Slice 2 后端首次产生新语义前，所有已知公共消费者必须先完成最小安全接入：

- 接受新增可选字段；
- 识别已公布的新枚举；
- 对未知枚举 fail closed 或执行书面定义的安全降级；
- 能处理 `HTTP 409 + AGENT_TURN_CONTRACT_UNSUPPORTED`；
- 能解析 `responseKind=ANSWER` 内的 `agentTurn.disposition=CONTEXT_INVALIDATED`；
- 不因为尚未实现完整 P5 视觉体验而崩溃或误展示。

Preflight 只建立消费者安全性，不等于完整前端体验完成。后端在该门禁通过前不得发出新枚举值或依赖新增字段的新语义；完整交互仍在 Slice 12 由前端 Agent 独立设计。

### 17.4 Slice 2：P5.0 路由与状态收口

工作：

- Page Subject 改为 Hint；
- 引入 `ResolvedSubjectBinding`；
- 删除 General Intent 被页面主体吞掉的规则；
- 增加 `fulfillmentRole`；
- 修正顶层 Resolution 聚合；
- 区分 `UNAVAILABLE/NOT_SUPPORTED/STALE`；
- Synthesis 来源不再映射 General；
- Degraded 与 Partial 分离；
- 增加安全 Reason Category。

此切片不扩展旧字符串 Synthesis 能力。

门禁：纯 General 页面问题、局部失败、混合 Evidence 与既有单域用例全部正确。

### 17.5 Slice 3：公共 Support 与 Portfolio Material

工作：

- `PortfolioAnswerMaterial` 与逐句 Grounded Statement；
- Claim/Public Source/约束绑定；
- 顶层 Public Source Catalog；
- Block `publicSourceKeys`；
- 停止跨 Block 删除引用关系；
- Block Support 与 Task Support Summary；
- 新 Material 向旧 Contribution 的兼容投影。

P4 可在迁移中暂时消费兼容 Contribution。

门禁：Portfolio Statement 来源绑定、Public Reference 完整性和新旧 DTO 投影均为 100%。真正 Synthesis 在本切片完成前不可启用。

### 17.6 Slice 4：General Material Pipeline

```text
General Model
  -> GeneralAnswerMaterialDraft
  -> Codec
  -> Validator
  -> GeneralAnswerMaterial
  -> Deterministic Renderer
```

同时接通受限 Discourse Window，保证 General 不携带 Portfolio Source，并删除直接把自由 Draft 当最终 Block 的主执行路径。旧 General 形状只作为 DTO 投影，不保留第二执行链。

门禁：合法 Draft 稳定渲染，非法 Draft fail closed，General Public Source 永远为空，Discourse Window 不进入 Portfolio/Synthesis。

### 17.7 Slice 5：Context v2 Reader-first

Context 采用双发布迁移。

#### 发布 N：Reader-first

增加：

```text
p5-recent-v2 Codec
p5-recommendation-v2 Codec
OrderedSubjectSelection
AuthorizedRoutingContextSnapshot
ContextVersionPolicy
```

但默认仍写 P3 v1 Context。发布 N 能读取 v1/v2。

#### 发布 N+1：Write v2

在 N 已成为可回滚版本后开始写 v2。若 N+1 回滚到 N，N 仍能读取已写入的 v2 Context。

不得从完全不认识 v2 的版本直接发布到写 v2 的版本。

v1 Reader 至少保留：

```text
7 天 Absolute TTL + Rollback Window
```

且确认没有旧活跃记录后才可删除。

### 17.8 Slice 6：Context 路由与 Result Item

工作：

- Context 授权前置；
- Recent Context 强类型 Binding；
- Active Candidate 按 Context Demand 消费；
- `contextReference.resultItemId`；
- Ordered Result 与冲突澄清；
- Explicit Handle Conflict；
- `TaskContextBinding`；
- `ContextCommitCandidate`；
- Recommendation Refine 子 Context；
- `agentTurn.disposition=CONTEXT_INVALIDATED` 与 Task `STALE`。

门禁：代词、序数、Recommendation Item、版本、显式 Handle、Commit Failure 和隐私不变量全部通过。

### 17.9 Slice 7：可靠 Multi-source

工作：

- 双域问题产生 General 与 Portfolio Goal；
- 并列问题生成两个 Primary Task；
- 关系型问题形成上游 Task，但尚不发布未经实现的关系；
- `sourceComposition=MULTI_SOURCE`；
- 混合 `evidenceState=MIXED`；
- 任一域失败仍返回另一域结果。

门禁：Task Shape、来源隔离、部分成功和 Support 正确，不把多来源伪装成 Cross-domain Derived。

这是 P5 第一个可独立交付的明显产品能力。

### 17.10 Slice 8：确定性 Cross-domain Synthesis

工作：

- Relation Candidate Builder 与 Policy；
- Allowed Relation；
- Cross-domain Material；
- Deterministic Relation Composer；
- Cross-domain Validator；
- `DERIVED_FROM_TASKS`；
- `CROSS_DOMAIN_DERIVED`；
- `INSUFFICIENT_TO_CONFIRM`；
- Primary/Optional Synthesis 聚合；
- `portfolio.agent.cross-domain-relations.enabled` 产品开关及 Disabled 行为。

门禁：Unsupported Relation、Source Bleed、Fact Mutation、Caveat Drop 均为零，确定性 fallback 可用率 100%；开关关闭时 Multi-source 正常、关系结论不发布，开关开启时只发布通过 Relation Policy 的结果。

完成后，P5 核心跨域能力可在模型关闭状态发布。

### 17.11 Slice 9：Operation 级模型配置

工作：

- 四个 Model Operation；
- Operation Policy 与数据暴露档案；
- Desired/Readiness/Runtime 三层状态；
- 旧配置 Alias 与冲突校验；
- Operation Allowance、Probe 和 Operator Status；
- 全部 Operation 默认关闭。

配置迁移先让发布 N 同时识别旧键与新键，仓库默认改用新键；外部仅旧键时映射到新 Policy，旧新同时存在启动失败。已知环境迁移完成后再删除 Alias。

Alias 只能映射到新 Operation Policy，不能启动旧模型执行链。

### 17.12 Slice 10：Cross-domain Model Expression

在确定性 Synthesis 稳定后增加：

- Cross-domain Eligibility；
- 最小 Input Bundle；
- 严格 JSON Draft；
- Codec 与六层 Validator；
- 原子 fallback；
- Adversarial Provider Lane；
- Operation Probe 和 Degradation Summary。

启用顺序：

```text
默认关闭
  -> 获批测试环境
  -> 固定 Canary
  -> Golden Dataset
  -> 环境级灰度
  -> 受限公开灰度
```

不能与确定性 Synthesis 在同一切片首次上线。

### 17.13 Slice 11：Retrieval Matrix

检索单独实施，避免同时改变 Synthesis 的输入与输出：

1. 引入 Effective Plan 与 Trace，但保持旧执行；
2. Planner/Policy 成为唯一决策权；
3. Keyword 直接执行 Keyword；
4. 拆除底层 Failover Retriever；
5. P3 Capability 成为唯一 Fallback Orchestrator；
6. 启用一个 Primary Attempt，并允许至多一个 Fallback Attempt；
7. 迁移旧 RetrievalProfile；
8. 运行统一 Benchmark；
9. 验证 Backend Content Version Alignment；
10. 删除 Adapter 固定 Hybrid。

门禁：无双层 fallback、每个逻辑检索一个 Primary 加至多一个 Fallback、Business Empty 不 fallback、无跨版本材料、Portfolio Sufficiency 不回归、False Sufficient 为零。

### 17.14 Slice 12：前端契约接入

前端 Agent 独立设计，建议消费顺序：

1. 新枚举与未知值安全策略；
2. Display/Completed Task 的 `fulfillmentRole`；
3. `sourceDomain`；
4. Task Status/Reason；
5. Block Support；
6. Public Source Catalog/Keys；
7. `sourceComposition`；
8. `ANSWER` 内的 Context Invalidated/Continuation Context；
9. Ordered Result Item；
10. Degradation Summary；
11. 停止依赖旧兼容字段。

只有前端和所有已知消费者不再读取旧字段后，才评估删除旧 DTO 字段。

### 17.15 Slice 13：清理与正式验收

清理候选：

- 旧 Block 字符串 Synthesis；
- 跨 Block Public Reference 去重；
- Executor 消费 UI Block；
- 底层 Failover Retriever；
- Adapter 固定 Hybrid；
- 旧 General 自由 Draft 主路径；
- 到期 `stp-v1` 适配器；
- v1 Context Writer，以及满足 TTL 条件后的 Reader；
- 不再使用的旧配置 Alias；
- 过期状态文档描述。

删除前必须通过代码引用与运行路径证明旧路径不可达，不能只凭命名判断。

### 17.16 切片依赖

```text
基线与契约
  -> Consumer Compatibility Preflight
  -> 路由/状态收口
  -> Portfolio Material
  -> General Material
  -> Context v2 Reader
  -> Context 路由
  -> Multi-source
  -> Deterministic Synthesis
  -> Operation Config
  -> Cross-domain Model Expression

Deterministic Synthesis
  -> Retrieval Matrix

契约冻结 + Consumer Compatibility Preflight
  -> 前端契约接入

Model Expression + Retrieval + Frontend
  -> 清理与正式验收
```

Portfolio Material 与路由状态可以分别推进，但必须独立提交和验证，不能共享半成品领域对象。

### 17.17 兼容窗口

#### 公共 DTO

保留旧字段直到前端只消费新字段、Contract Tests 不再依赖旧字段、经过至少一个稳定发布窗口且没有已知外部消费者。

#### Context v1

Reader 保留至少 `7 天 Absolute TTL + Rollback Window`；v2 稳定后停止 v1 Writer。

#### `stp-v1`

建议保留一个发布窗口。旧客户端请求 P5 专属能力时返回 Contract Upgrade，不进入错误兼容路径。

#### 旧配置

Alias 保留到所有已知环境完成迁移；旧新键不能并存。

#### 旧 Failover

新 Fallback Orchestrator 稳定后立即删除旧组合 Bean，不长期双重存在。

### 17.18 数据库迁移边界

Context v2 继续使用加密 Payload，预计不新增问题或回答字段。实施前必须验证：

- Payload 最大尺寸；
- 最多五个 Recommendation Item 的编码大小；
- Codec Version 字段容量；
- Safe Summary 不暴露 Result Item；
- 索引不依赖旧 Payload Schema；
- Store 可并存 v1/v2；
- Context Capacity Policy 是否需要调整。

如需 SQL Migration，只允许服务闭集 Context Type/Codec Version、安全元数据容量、索引或约束。不得增加问题、回答或模型正文列。

### 17.19 回滚策略

- 路由/Synthesis 问题：关闭对应产品能力或 Model Operation，保留确定性单域，不用旧事实恢复正文；
- Context v2：只回滚到支持 v2 Reader 的版本；
- Public DTO：保留增量字段，消费者对可选字段和未知枚举安全处理；
- Retrieval：可切换 Hybrid 到 Keyword、PostgreSQL 到 Bundle，但必须先验证 Content Version 对齐。

### 17.20 不增加全局 P5 Flag

不增加：

```text
p5.enabled
```

模型使用 Operation Mode；Cross-domain 产品能力使用 `portfolio.agent.cross-domain-relations.enabled`；Retrieval 使用 Discovery/Backend/Vector 配置；公共 DTO 和 Context 使用版本化契约。

部分成功、来源映射和 Page Hint 等错误语义修复不保留长期回退开关。

### 17.21 每个切片的交付物

至少包含：

- 生产代码；
- 单元/契约/集成测试；
- Eval Fixture；
- 安全诊断；
- 配置说明；
- 兼容与回滚说明；
- Spec 状态更新；
- 当前实现状态更新。

只有涉及真实 Model Operation 的切片才需要显式 Live Probe 证据。

### 17.22 暂定决议

1. P5 采用纵向可发布切片，不做跨包大爆炸重写。
2. 保留 `/api/v2/answers`，Semantic Turn Contract 升级为 `stp-v2`。
3. `stp-v1` 只做短期输入兼容，不保留旧执行主链。
4. Slice 1 后先完成 Consumer Compatibility Preflight，公共协议消费者能够安全解析后，后端才产生新语义；完整前端体验仍在 Slice 12 完成。
5. Context 采用 Reader-first、Writer-second 双发布迁移。
6. 回滚目标必须能够读取 v2 Context。
7. 先修路由与状态，再建设 Material。
8. 先实现可靠 Multi-source，再实现确定性 Synthesis。
9. 确定性 Synthesis 稳定后才开放 Cross-domain Model Expression。
10. Retrieval Matrix 独立切片，避免与 Synthesis 同时改变输入和输出。
11. 前端 Agent 基于契约包独立完成设计。
12. 旧 DTO、Context、Config 和 Contract 都有明确兼容窗口。
13. 不增加全局 `p5.enabled`。
14. 错误语义修复不保留长期回退开关。
15. 清理旧路径必须基于调用证据和完整门禁。
16. P5 完成以生产实现、前端契约消费、六类 Eval 和发布证据为准。

### 17.23 2026-08-14 前后端联调收口

Consumer Compatibility Preflight 与生产请求切换已经完成：

- Agent 工作区默认显式请求 `agentTurnContract=stp-v2`，不再依赖服务端缺省值；
- `HTTP 409 + AGENT_TURN_CONTRACT_UNSUPPORTED` 必须停止当前请求，只展示一个“以基础模式继续”的用户主动操作；
- 用户确认后以新的 `requestToken` 发起 `stp-v1` 新请求，禁止自动重试、复用失败请求的幂等键或把失败响应按 v1 解析；
- 基础模式请求保留兼容的 `contextHandle` 与 `expectedContextType`，移除 v2 专属 `resultItemId`，避免再次请求无法由 v1 安全表达的有序结果语义；
- 前端继续同时解析合法的 `stp-v1/stp-v2` 响应，未知版本、枚举和不一致的新旧字段维持 fail-closed。

联调门禁：前端 62 个测试文件、673 个测试通过，`vue-tsc` 与生产构建通过；后端 P5 Fixture、公共序列化、请求校验、版本策略和响应 Mapper 共 33 个契约测试通过。该结论只表示公共契约与客户端状态机已经对齐，不替代真实 Provider、PostgreSQL 容器集成和六类 P5 Eval 发布证据。

## 18. 前端边界

本文只规定前端必须能够消费的公共语义：

- Task、履约角色、Block、来源域和顺序；
- 支持类型；
- 来源 Task，以及用于响应级 Provenance 校验的 Statement Reference；前端不必展示或理解完整内部 Material；
- 公开来源目录与 Block 关联 Key；
- Resolution、Coverage、Degraded、Caveat 和安全 Reason Code；
- Context Handle 与受控继续操作所需字段；
- 兼容字段的迁移周期。

本文不规定：

- 页面布局；
- Block 或来源的视觉层级；
- 标签文案和颜色；
- 引用展开、折叠或抽屉形式；
- 桌面端和移动端交互；
- 是否显示内部任务摘要；
- 动效或信息密度。

这些由后续前端 Agent 基于公共契约独立设计。

## 19. 当前明确非目标

- 动态 Tool Registry；
- 多 Agent；
- 长期用户记忆；
- 保存访客问题或回答正文；
- Web Search 或外部通用知识引用；
- 私有内容或未发布 Portfolio 数据；
- 模型决定 Portfolio 事实、推荐集合或排序；
- 自由无边界跨域推理；
- 用第二个生产模型代替确定性 Validator；
- 在 P5 同时重做前端视觉与交互。

## 20. 实施前最终审阅项

原计划中的 P5 主题已经逐项讨论完毕。正式批准 Spec 或编写实施计划前仍需完成：

- [x] 全文一致性审阅，消除跨章节的重复枚举、命名冲突和相互矛盾规则（2026-08-13 完成）；
- [ ] 将本 Spec 的最终范围逐项回填路线图，并校正当前实现状态文档；
- [ ] 核对所有建议 DTO 与现有 Java 类型的最小迁移距离；
- [ ] 确认建议门槛值与现有 Eval Dataset 规模是否匹配；
- [ ] 产出供前端 Agent 使用的独立公共契约包；
- [x] 按用户明确要求提前创建后端实施计划 `docs/superpowers/plans/2026-08-13-agent-p5-backend-implementation.md`；
- [ ] 用户最终批准 P5 Spec 并明确授权后，才执行该实施计划。Plan 已存在不代表获准修改生产代码。

## 21. 决议状态说明

本文第 5—17 节是当前讨论形成的暂定决议，不代表已经获得最终实施批准。后续审阅如果推翻某项结论，应直接修改对应章节并记录变更原因，避免同时保留互相冲突的旧结论。

## 22. 2026-08-14 联调收口：单主体多 Facet 履约

真实前后端联调发现，单个 Portfolio 主体的详细介绍问题虽然明确要求背景、职责、技术方案、验证过程和最终状态，编译器仍把 `PORTFOLIO_FACT` 固定收窄为 `OVERVIEW`，导致只返回背景却误报 `ANSWERED / COMPLETED`。

收口规则如下：

- 单主体多 Facet 请求仍编译为一个 `PORTFOLIO_FACT`，不人为拆成五个 Task；
- 确定性信号层识别问题中显式出现的闭集 Facet，并原样传入强类型 Task 参数；无显式 Facet 时才回退 `OVERVIEW`；
- `OUTCOME` Claim 必须映射为独立 `STATUS` Section，不得混入 `SOLUTION`；
- Fact Section 固定按 `BACKGROUND → RESPONSIBILITY → SOLUTION → VERIFICATION → STATUS` 输出；模型只能改写受约束正文，不能改变服务端结构顺序；
- 截图原问题的验收结果必须为一个完成的 Portfolio Task、五个非空 Section，且顺序与上述闭集一致。任一必需 Facet 未履约时，不得把该请求误报为完整成功。

2026-08-14 真实链路验收已得到：`READY / ANSWERED / SUCCEEDED / COMPLETED`，Section 顺序为 `BACKGROUND, RESPONSIBILITY, SOLUTION, VERIFICATION, STATUS`，`degraded=false`。本次本地启动未启用 Portfolio 模型表达 operation，因此任务级 composition 为 `DETERMINISTIC`，符合当前启用边界。

## 23. 2026-08-14 联调收口：推荐发现与失败历史隔离

截图联调进一步暴露两个相互独立的问题：无主体推荐计划使用占位 `contentVersion`，导致证据晋升正确地 fail-closed；失败回答又被前端作为后续会话历史回传，使下一轮请求受到无有效正文的失败轮次干扰。

收口规则如下：

- 无显式主体的 `PORTFOLIO_RECOMMEND` 必须绑定公共主体目录的当前唯一内容快照版本；禁止使用 `public-v1` 等占位版本绕过或触发版本校验；
- Bundle profile discovery 使用强类型 Claim Category 闭集做精确公开材料发现，不得把内部 profile ID 当作自然语言检索词；请求上限使用 `PortfolioRetrievalRequest.MAX_LIMIT`，不得混用其他边界对象的容量常量；
- 用户明确要求的推荐数量按 `1..5` 闭集传入任务参数和结果投影；“推荐一个项目”必须只返回一个有序结果项，并携带 `{resultItemId, position, subject}`；
- 泛化推荐不得凭空添加 `JAVA` 等能力约束；仅将问题中显式出现的公共能力词编译为能力约束，避免同一约束同时出现在 satisfied 与 unsatisfied；
- 前端会话历史只回传 `ANSWERED` 且具有非空摘要或 Section 正文的完整 USER/ASSISTANT 对；`CAPABILITY_UNAVAILABLE`、`NOT_SUPPORTED` 等无有效回答轮次仍可留在本地界面供用户理解，但不得进入后续模型历史；
- 以上规则不改变 409 的产品决策：`stp-v2` 不支持时仍只允许用户主动以新的 request token 选择 `stp-v1` 基础模式，不静默降级。

真实前端代理验收结果：泛化单项目推荐返回 `ANSWER / ANSWERED / READY / SUCCEEDED / COMPLETED`，结果项数量为 1，`unsatisfiedConstraints=[]`，且有序结果身份字段完整。随后独立复测详细 SQL 审计问题仍返回五个闭集 Section，证明推荐修复未回归单主体多 Facet 履约。
