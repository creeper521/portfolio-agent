# Agent P5 Backend Implementation Plan
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

> **For backend agent:** 按本计划逐任务执行，严格 RED → GREEN → REFACTOR。项目所有者明确要求不使用 Superpowers；执行时不要调用 Superpowers 技能。未经用户明确授权，不要 stage、commit 或 push。

**Goal:** 在 P0—P4 已完成的生产主链上，实现 P5 的主体绑定、多来源回答、跨域关系、业务 Context v2、操作级模型配置、检索矩阵、公共支持契约和完整验收闭环，同时保持作品集事实边界、访客隐私和确定性降级能力。

**Architecture:** 保留 `SemanticTurnRequestMapper → TurnRouter → SemanticTurnCoordinator → SemanticTaskExecutor → AgentTurnResult → ConversationAnswerResponseMapper` 作为唯一整轮主链。P5 只演进该主链及 P4 `PortfolioAnswerComposition`、P3 Context、现有 Eval/Retrieval seam；不创建第二套 Router、Synthesis、Context 或 Retrieval Orchestrator。

**Tech Stack:** Java 21、Spring Boot 3.5.3、Jackson、JDBC/PostgreSQL、JUnit 5、AssertJ、Maven 3.9.9、现有 Evaluation Harness 与 PowerShell 发布门禁。

## 1. Authority and non-negotiable constraints

执行前完整阅读：

1. `AGENTS.md`
2. `docs/04-项目代码约束.md`
3. `docs/13-Agent对话体验与智能编排改造路线图.md`
4. `docs/superpowers/specs/2026-08-06-agent-answer-composition-design.md`
5. `docs/superpowers/specs/2026-08-10-semantic-turn-routing-design.md`
6. `docs/superpowers/specs/2026-08-13-model-grounded-answer-design.md`
7. `docs/superpowers/specs/2026-08-13-agent-context-and-runtime-modes-design.md`

冲突优先级：`AGENTS.md`/代码约束中的安全边界 → P5 Spec → P4/P3/P2 已确认设计 → 本 Plan → 旧状态描述。发现 P5 Spec 与实际代码存在无法局部迁移的冲突时，停止对应任务并先提出 Spec 修订，不得在代码中静默改语义。

全程约束：

- 不修改 `frontend/**`；前端由独立 Agent 设计和实施。
- `/api/v2/answers` 保持唯一公开 Answer API，不新建 `/api/v3/answers`。
- `responseKind` 保持 `ANSWER | COMPLETION_RECEIPT`；Context 失效使用 `ANSWER` 内部 disposition。
- 不保存问题、回答、Prompt、模型原始输出或自由历史；Context 只保存加密、短期、强类型业务值。
- General Material 不得携带 Portfolio 事实或来源；Portfolio Material 不得读取自由历史；Synthesis 不得读取原始消息。
- 模型不得决定 Portfolio 事实、推荐集合、排序或 Allowed Relation。
- 所有模型 Operation 默认关闭；所有 Cross-domain 产品能力默认关闭。
- Java 生产与测试代码禁止 `var`、`record`、Lombok；值对象使用显式不可变类和防御复制。
- 每个任务先写能失败的目标测试，再实现最小生产代码；普通测试不得访问真实网络。
- 新旧契约只允许显式适配，不保留平行执行主链。

## 2. Current code truth and target seams

现有代码事实：

- `ConversationalAgentRuntime` 固定 `stp-v1`，顶层 Resolution 仍会被任意 FAILED Task 覆盖，Evidence 没有 `MIXED`。
- `SemanticTask` 没有 `fulfillmentRole`；`DisplayPlanResponse`、`CompletedTaskResponse` 与 `TaskSummaryResponse` 也未公开该角色。
- `ConversationAnswerBlockResponse` 仍以 `sourceScope/sourceReferences` 为主，没有 `blockId/sourceDomain/support`。
- `TaskOutcome` 已区分执行状态、任务 Resolution、Evidence 和 Degraded，但公共 Mapper 合并了部分状态。
- `DeterministicSynthesisTaskExecutor` 仍把上游字符串/Contribution 拼接成 Synthesis，尚无 Relation Policy 和强类型 Cross-domain Material。
- P4 已存在强类型 `PortfolioAnswerMaterial/GroundedStatement` 和唯一 `PortfolioAnswerComposition` seam，应继续使用。
- P3 Context 只有 `p3-recent-v1`、`p3-recommendation-v1`；Recommendation Context 尚未保存实际有序推荐项。
- `ConversationContextResolver` 会在多 Active Context 中选择最新项，不符合 P5 的显式 Context Demand 与歧义规则。
- 现有 `DefaultPortfolioEvidenceCapability` 已具备一个 Primary 加至多一个 Fallback，但底层仍有 `FailoverPortfolioRetriever`，存在双层 fallback 风险。
- 旧配置分散在 `portfolio.conversational-agent`、`portfolio.conversational-model`、`portfolio.model-expression` 与 `portfolio.retrieval.profile`。
- Evaluation Harness、P3/P4 Eval、发布脚本已经存在，应增量扩展，不新建第二套评测框架。

主要演进入口：

```text
answer/routing/domain                Task、Binding、Outcome、Plan
answer/routing/service               Router、Compiler、Validator、Coordinator
answer/routing/adapter/execution     General/Portfolio/Synthesis Executor
answer/composition                   P4 Portfolio Material/Expression
answer/context                       Context v2、授权、版本与提交
answer/adapter/model                 Operation 级配置/状态
answer/intelligence                  Retrieval Plan/Trace/Fallback
answer/mapper + answer/dto           公共 stp-v2 契约
evaluation                           六类 P5 Eval 与发布证据
```

## 3. Baseline and verification commands

开始执行前：

```powershell
git status --short
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true test
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
```

若基线失败，记录原始失败并停止；不要把基线修复混入 P5。工作树中现有未跟踪 P5 Spec 属于用户内容，禁止覆盖或删除。

## Task 0: Freeze P5 contract fixtures and traceability

**Files:**

- Create: `backend/src/test/resources/evaluation/p5/`
- Create: `backend/src/test/java/com/portfolio/agent/answer/contract/P5CurrentContractCharacterizationTest.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/contract/P5TargetContractFixtureTest.java`
- Modify later, not in RED baseline: `docs/00-文档状态索引.md`, `docs/08-当前实现状态.md`

Steps:

1. 固定现有纯 General、Portfolio Fact、并列双域、关系型双域、局部失败、计划确认、Context 继续和检索 fallback 行为。
2. 建立 P5 Target JSON Fixture，覆盖 `stp-v2`、履约角色、Block Support、来源目录、部分成功、Context Invalidated 和 409 契约。
3. Characterization 必须先 PASS；Target Fixture 必须在缺少 P5 字段时 RED。
4. 建立能力追踪表：`EXISTING_CORRECT / EXISTING_NEEDS_CHANGE / NEW / REMOVE_AFTER_MIGRATION / FRONTEND_CONTRACT_ONLY`。

Gate：现状与目标分开，测试 Oracle 不进入生产输入，文档尚不得标记 P5 已实现。

## Task 1: Introduce stp-v2 and the compatibility policy

**Files:**

- Modify: `answer/dto/request/ConversationAnswerRequest.java`
- Modify: `answer/mapper/SemanticTurnRequestMapper.java`
- Modify: `answer/service/ConversationalAgentRuntime.java`
- Modify: `answer/exception/AnswerErrorCode.java`
- Modify: `common/web/GlobalExceptionHandler.java` only if current `ApplicationException` mapping cannot express 409
- Create: `answer/routing/service/SemanticTurnContractPolicy.java`
- Tests: `ConversationAnswerRequestValidationTest`, `ConversationAnswerControllerTest`, new `SemanticTurnContractPolicyTest`

Steps:

1. RED：请求接受 `stp-v1/stp-v2`，拒绝未知版本；当前只允许 `stp-v1` 的正则应失败。
2. 实现显式 Compatibility Policy，不让 Bean Validation 代替版本策略。
3. `stp-v1` 与 `stp-v2` 都进入同一 Router/Core；v1 仅做输入缺省和旧 DTO 投影。
4. v1 请求触发无法安全表达的 P5 专属语义时抛出业务异常，映射为：

   ```text
   HTTP 409
   code=AGENT_TURN_CONTRACT_UNSUPPORTED
   ```

5. 不新增 `responseKind=CONTRACT_UPGRADE_REQUIRED`。
6. 将计划确认的 Version Binding 从硬编码 `stp-v1` 改为本次有效 Contract。

Gate：v1 回归全绿；v2 可解析但尚不要求生产 Router 产生新 Synthesis；错误体无内部兼容信息。

## Task 2: Add fulfillment roles and correct turn aggregation

**Files:**

- Modify: `routing/domain/SemanticTask.java`
- Create: `routing/domain/TaskFulfillmentRole.java`
- Modify: `routing/service/SemanticPlanCompiler.java`
- Modify: `routing/service/PlanFingerprintService.java`
- Modify: `routing/domain/TaskOutcome.java`
- Modify: `domain/AgentTurnResult.java`
- Modify: `service/ConversationalAgentRuntime.java`
- Modify: `dto/response/DisplayPlanResponse.java`
- Modify: `dto/response/CompletedTaskResponse.java`
- Modify: `dto/response/TaskSummaryResponse.java`
- Modify: `mapper/ConversationAnswerResponseMapper.java`
- Tests: `SemanticTaskContractTest`, `SemanticTurnPlanTest`, `PlanConfirmationServiceTest`, `ConversationalAgentRuntimeTest`, `ConversationAnswerResponseMapperTest`

Steps:

1. RED：角色闭集为 `PRIMARY/SUPPORTING/OPTIONAL`，不能由 Task Type 固定推断。
2. Compiler 根据用户 Goal 分配角色；Plan Validator 校验至少一个 Primary，依赖角色与任务图一致。
3. 角色进入 Plan Fingerprint；确认后任何角色变化都产生 Plan Invalidated。
4. 公共 `displayPlan.tasks[]`、`completedTasks[]`、必要的 Task Summary Item 投影同一角色。
5. 修正 `projectedResolution()`：只根据 Primary Goal 完整性聚合；存在合法 Block 时局部 FAILED 只能导致 Partial，不能覆盖成整轮 Capability Unavailable。
6. 扩展公共状态闭集，准确区分 `UNAVAILABLE/NOT_SUPPORTED/NOT_APPLICABLE/STALE/FAILED/NOT_EXECUTED`。
7. `AnswerEvidenceState` 增加兼容 `MIXED`，但 Task/Block Support 保持权威。

Gate：角色跨 Plan/Outcome/DTO 一致；Optional 失败不降低完整性；Primary 未完成时不得误报 ANSWERED。

## Task 3: Make subject binding explicit and treat page subject as hint

**Files:**

- Create: `routing/domain/ResolvedSubjectBinding.java`
- Create: `routing/domain/AuthorizedRoutingContextSnapshot.java`
- Modify: `routing/service/RoutingContextResolver.java`
- Modify: `routing/service/SemanticSignalCollector.java`
- Modify: `routing/service/DefaultTurnRouter.java`
- Modify: `mapper/SemanticTurnRequestMapper.java`
- Modify: `service/ConversationalAgentRuntime.java`
- Tests: `RoutingContextResolverTest`, `DefaultTurnRouterDeterministicTest`, `SemanticRoutingArchitectureTest`

Steps:

1. RED：Project/Case 页面上的通用知识问题保持 General；只有显式主体、指代或授权 Context 才绑定 Portfolio。
2. 将信号分为 Explicit Subject、Deictic Binding、Result Item Binding、Active Context Candidate 与 Page Hint。
3. Active Context 与 Page Hint 指向不同主体且文本无法消歧时返回 Clarification，不以“最新”覆盖。
4. Router 只消费授权后的最小 Snapshot，不直接访问 Context Store。
5. 模型分类只能提出闭集候选，不能把 Hint 升级成已绑定主体。

Gate：纯 General 页面问题、显式 Portfolio、代词绑定、Active/Page 冲突和未知主体全部通过；普通问题不继承 Active Context。

## Task 4: Publish authoritative Task/Block Support

**Files:**

- Create: `answer/domain/AnswerBlockSupport.java`
- Create: `answer/domain/AnswerSupportKind.java`
- Create: `answer/domain/StatementSupportReference.java`
- Create: `answer/domain/AnswerSourceComposition.java`
- Modify: `domain/ConversationAnswerBlock.java`
- Modify: `dto/response/ConversationAnswerBlockResponse.java`
- Create: `dto/response/AnswerBlockSupportResponse.java`
- Create: `dto/response/PublicSourceCatalogEntryResponse.java`
- Create: `dto/response/TaskSupportSummaryResponse.java`
- Modify: `dto/response/ConversationAnswerResponse.java`
- Modify: `dto/response/CompletedTaskResponse.java`
- Modify: `mapper/ConversationAnswerResponseMapper.java`
- Tests: existing response/mapper tests plus new `AnswerSupportContractTest`

Steps:

1. 为每个可渲染 Block 生成响应内稳定 `blockId`；为 Material Statement 生成响应内稳定 `statementId`。
2. 幂等重放同一已接受请求时必须返回相同 ID；跨请求/内容版本不作稳定承诺。
3. Block 公开真实 `sourceDomain=GENERAL/PORTFOLIO/SYNTHESIS`，不再把 Synthesis 映射成 General。
4. Portfolio Block 的 Statement 必须映射至少一个公开来源；General Block 的公开来源为空。
5. 顶层 `publicSourceCatalog` 按 Key 去重；Block 的 `publicSourceKeys` 保留完整关系，不跨 Block 删除。
6. 新增 `sourceComposition=GENERAL_ONLY/PORTFOLIO_ONLY/MULTI_SOURCE/CROSS_DOMAIN_DERIVED`。
7. 迁移期从同一 Material 同时投影新旧字段；旧 `sourceScope/sourceReferences/evidenceState` 不再作为新逻辑输入。

Gate：Public Source Key 全部可解析且唯一；Portfolio Statement 无来源不能发布；General 不携带 Portfolio Key；幂等 ID 测试通过。

## Task 5: Replace free General output with a typed material pipeline

**Files:**

- Create package: `answer/general/domain`, `answer/general/codec`, `answer/general/validation`, `answer/general/service`, `answer/general/gateway`
- Modify: `routing/adapter/execution/GeneralSemanticTaskExecutor.java`
- Modify: current conversational model adapter/config only through the new General Material port
- Tests: focused General Material codec/validator/executor/privacy tests

Target seam:

```text
General Model
  -> GeneralAnswerMaterialDraft
  -> strict Codec
  -> Validator
  -> immutable GeneralAnswerMaterial
  -> deterministic Block renderer
```

Steps:

1. Material 只包含 General Statement、Section Role、Caveat 和受限 Discourse Window 投影。
2. Validator 拒绝 Portfolio 主体事实、公开来源 Key、内部 ID、历史正文复制和未授权关系结论。
3. General Provider 不可用时返回 Task `UNAVAILABLE`，不能伪造确定性知识 fallback。
4. 旧自由 Draft 只保留为迁移 DTO 投影，不保留第二执行链。

Gate：合法 Draft 可重复渲染；非法 Draft fail closed；General Block 的 `publicSourceKeys` 始终为空；隐私测试证明 Discourse Window 不进入 Portfolio/Synthesis。

## Task 6: Release Context v2 readers before writers

**Files:**

- Create: `context/domain/OrderedSubjectSelection.java`
- Create: `context/domain/OrderedSubjectItem.java`
- Extend: `context/domain/RecentSemanticTaskContext.java`
- Extend: `context/domain/RecommendationContext.java`
- Create: `context/codec/P5RecentSemanticTaskContextCodec.java`
- Create: `context/codec/P5RecommendationContextCodec.java`
- Modify: `context/codec/ConversationContextCodecRegistry.java`
- Tests: codec registry, v1/v2 round-trip, malformed payload and capacity tests

Steps:

1. 新 Codec 版本固定为 `p5-recent-v2`、`p5-recommendation-v2`。
2. Registry 按 `contextType + schemaVersion` 解码，不能假设每种 Context 只有一个 Codec。
3. Recent v2 可选保存用户声明/结果展示顺序；Recommendation v2 保存实际 Selected Results 与 Batch ID，最多五项。
4. 不新增 `ConversationContextType.RESULT_SET`，不保存 Synthesis Context。
5. 第一发布只注册 v2 Reader，Writer 仍写 v1。

Gate：旧 v1 全部可读；v2 Fixture 可读；生产 Writer 仍为 v1；Payload 容量与隐私门禁通过。

## Task 7: Add authorized context routing, ordered selectors and version policy

**Files:**

- Create: `context/service/ContextVersionPolicy.java`
- Create: `context/domain/ContextVersionDecision.java`
- Create: `context/domain/ContextCommitCandidate.java`
- Extend: `dto/request/ContextReferenceRequest.java` with optional `resultItemId`
- Modify: `context/service/ConversationContextResolver.java`
- Modify: `context/service/AuthorizedContextReferenceService.java`
- Modify: `context/service/ConversationContextCommitter.java`
- Modify: `service/ConversationalAgentRuntime.java`
- Modify: `domain/AgentTurnResult.java`
- Modify: response DTO/Mapper for Context Resolution and Invalidation
- Tests: resolver, authorization, committer, runtime and API contract tests with injected Clock/content snapshot

Steps:

1. 实现 `LATEST_REVALIDATED / SNAPSHOT_SELECT_THEN_LATEST / SNAPSHOT_STRICT` 三种版本策略。
2. 显式 Handle 无效、过期、Store 不可用、主体不可用、来源变化和 stale 必须分别映射安全 Reason Code。
3. 整轮依赖 stale Strict Context 时：

   ```text
   responseKind=ANSWER
   resolution=NEEDS_CLARIFICATION
   agentTurn.disposition=CONTEXT_INVALIDATED
   blocks=[]
   ```

4. 局部 stale 使用 Task `STALE`，其他任务继续，整轮可为 Partial。
5. 序数引用先按旧结果选择同一 Subject，再在当前不可变内容快照回答；Recommendation Refine 严格绑定旧 Batch。
6. Committer 接收 Executor 产生的 `ContextCommitCandidate`，不重新推导或扩大 Scope。
7. Reader 发布稳定后第二发布再切换 v2 Writer；回滚目标必须仍能读取 v2。

Gate：TTL、Parent/Child、并发 Revision、Result Item 所属、版本重验证、stale 不 Touch、Store Unavailable 不伪装 stale 全部通过。

## Task 8: Deliver reliable Multi-source answering

**Files:**

- Modify: `routing/service/SemanticPlanCompiler.java`
- Modify: `routing/service/SemanticPlanValidator.java`
- Modify: `routing/service/SemanticTurnCoordinator.java`
- Modify: `service/ConversationalAgentRuntime.java`
- Modify: `mapper/ConversationAnswerResponseMapper.java`
- Tests: Router/Coordinator/Runtime/Mapper integration tests

Steps:

1. 并列 General + Portfolio Goal 生成两个独立 Primary Task，不自动生成 Synthesis。
2. 两个来源的 Block 都保留真实域、Support 与稳定顺序。
3. 两域成功：`ANSWERED + MULTI_SOURCE + evidenceState=MIXED`。
4. 单域失败：保留另一域 Block，失败 Task 返回准确状态，整轮 `PARTIALLY_ANSWERED`。
5. 关系型 Goal 可以生成上游 Supporting Task，但在确定性关系能力开启前不发布伪 Synthesis。

Gate：并列问题不产生关系断言；任何单域故障不抹掉另一域合法结果；来源不漂白。

## Task 9: Replace string concatenation with deterministic cross-domain relations

**Files:**

- Create package: `answer/synthesis/domain`, `answer/synthesis/service`, `answer/synthesis/validation`
- Replace/evolve: `routing/adapter/execution/DeterministicSynthesisTaskExecutor.java`
- Extend: `routing/domain/SemanticTaskParameters.Synthesis`
- Modify: `routing/service/SemanticPlanCompiler.java`, `SemanticPlanValidator.java`
- Create: `answer/synthesis/config/CrossDomainRelationProperties.java`
- Modify: `application.yml` and profile configuration
- Tests: relation policy/composer/validator/executor, aggregation and disabled-mode integration tests

Steps:

1. 建立闭集 Relation Type、Relation Candidate、Allowed Relation、Cross-domain Material 与 Caveat。
2. Synthesis 必须消费一个 General Material 和一个 Portfolio Material；不得消费渲染后字符串或历史消息。
3. Relation Policy 决定允许关系；Composer 只能表达允许关系，Validator 防止关系升级、来源漂白、Portfolio 事实篡改和 Caveat 丢失。
4. 产品开关：`portfolio.agent.cross-domain-relations.enabled=false`。
5. 开关关闭时普通 Multi-source 不受影响；显式 Primary Relation Goal 返回 `UNAVAILABLE + CROSS_DOMAIN_RELATION_DISABLED`。
6. 无合法关系时依据 `fulfillmentRole` 聚合；允许 `INSUFFICIENT_TO_CONFIRM` 受限结论。
7. 只有成功 Relation Block 才产生 `sourceComposition=CROSS_DOMAIN_DERIVED`，公开来源只追溯 Portfolio 输入。

Gate：Unsupported Relation、Source Domain Bleed、Portfolio Fact Mutation、Caveat Drop 均为零；模型关闭时确定性关系可独立工作。

## Task 10: Replace global model switches with operation-level policy

**Files:**

- Create: `answer/adapter/model/ModelOperation.java`
- Create: `answer/adapter/model/ModelOperationProperties.java`
- Create: `answer/adapter/model/ModelOperationPolicy.java`
- Create: `answer/adapter/model/EffectiveModelOperationStatus.java`
- Create: `answer/adapter/model/ModelOperationAllowance.java`
- Modify/evolve: `ConversationalAgentProperties`, `PortfolioExpressionProperties`, `ProviderOperation`, configuration classes
- Modify: `ModelProviderRegistrySnapshot` only for operation-specific schema/policy lookup
- Modify: application config and example env files
- Tests: startup validation, operation isolation, alias conflict, allowance and public status projection

Steps:

1. 四个 Operation 固定为 Routing Assist、General Material、Portfolio Expression、Cross-domain Expression。
2. 每个 Operation 独立配置 `DISABLED/ENABLED`、Provider Ref、审批、Timeout、Allowed Kinds 与预算。
3. Desired Mode、Effective Readiness、Runtime Outcome 三层分离；Enabled 且静态配置不完整必须启动失败。
4. 旧键只做带移除版本的 Alias；旧新同时存在启动失败。
5. Operation A 的 Provider/Credential 不自动批准 Operation B；数据暴露档案由代码闭集决定。
6. 内部 `AVAILABLE_DETERMINISTIC` 对外投影 `AVAILABLE_WITH_DETERMINISTIC_FALLBACK`。
7. Probe 使用固定 Canary，禁止携带访客内容，不在启动时自动外发。

Gate：四 Operation 默认关闭；单独启用一个不会调用另一个；缺审批/密钥/Codec 时 fail startup；普通 CI 零网络调用。

## Task 11: Add optional cross-domain model expression

**Files:**

- Create package: `answer/synthesis/adapter/model`, `answer/synthesis/codec`
- Extend synthesis domain with strict Draft types
- Modify operation configuration/registry for Cross-domain Expression schemas
- Tests: codec, input privacy, six-layer validator, atomic fallback, adversarial dataset and probe

Steps:

1. 只有双域 Material 完整、Allowed Relation 非空、确定性 fallback 已构建时才允许调用。
2. 输入只含验证后的双域 Material、Allowed Relation、Caveat 与公开别名。
3. Draft 使用严格 JSON；任何 Provider/Codec/Schema/Grounding/Relation/Caveat 失败均原子丢弃。
4. 模型只能改善表达，不能增删关系、改写 Portfolio 原子事实或制造公开来源。
5. Shadow Lane 的模型结果只进入 Eval，不影响公共响应；Shadow 不是第三个持久 Mode。
6. fallback 成功时 Task 可为 ANSWERED，但返回 `CROSS_DOMAIN_EXPRESSION_FALLBACK` 降级摘要。

Gate：Adversarial Provider 不能发布非法关系；确定性 fallback 可用率 100%；真实 Provider 未显式授权时如实标记 INCOMPLETE。

## Task 12: Implement the retrieval matrix and remove double fallback

**Files:**

- Create: `answer/intelligence/retrieval/EffectiveRetrievalPlan.java`
- Create: `answer/intelligence/retrieval/RetrievalIntent.java`
- Create: `answer/intelligence/retrieval/CorpusBackend.java`
- Create: `answer/intelligence/retrieval/SearchStrategy.java`
- Create: `answer/intelligence/retrieval/RetrievalAttemptTrace.java`
- Create: `answer/intelligence/retrieval/RetrievalFallbackPolicy.java`
- Modify: `PortfolioExecutionPlanner`, `PortfolioRetrievalRequest`, `DefaultPortfolioEvidenceCapability`
- Modify: Bundle/PostgreSQL retrieval adapters to execute the supplied plan rather than decide strategy
- Remove after migration: `answer/intelligence/adapter/FailoverPortfolioRetriever.java`
- Evolve/remove: `RetrievalProfile`, `RetrievalProperties`
- Tests: planner/policy/adapter/capability/benchmark/version-alignment tests

Steps:

1. 拆分 Intent、Backend、Strategy、Vector Capability、Fallback Layer 五轴。
2. Explicit Subject、Context Revalidation、Preset 使用 Exact；Recommendation Discovery 才依赖 Keyword/Hybrid。
3. Keyword 配置必须实际执行 Keyword；Adapter 不得固定 Hybrid。
4. `DefaultPortfolioEvidenceCapability` 成为唯一 Fallback Orchestrator。
5. 每个逻辑检索只有一个 Primary，加至多一个 Fallback，总数最多两个。
6. 只有 Connection/Timeout/Vector Runtime 等基础设施可恢复失败触发 fallback；Empty、Evidence Insufficient、Version Mismatch、Integrity Failure 不 fallback。
7. 所有 Attempt 使用同一 expectedContentVersion；fallback 版本不同直接 Integrity Failure。
8. 内部 Trace 记录 requested/actual strategy/backend/fallback reason；公共响应只投影安全降级摘要。

Gate：无双层 fallback；Keyword 不偷跑 Hybrid；Business Empty 不 fallback；跨版本材料为零；统一 Sufficiency Benchmark 不回归、False Sufficient 为零。

## Task 13: Extend the existing Evaluation Harness for P5

**Files:**

- Extend: `evaluation/domain`, `execution`, `grading`, `reporting`, `coverage`
- Create P5 fixtures under `backend/src/test/resources/evaluation/p5/`
- Reuse: existing Eval CLI/Harness/report writers
- Tests: P5 loader/oracle/grader/verdict/report tests

Suites:

```text
Routing & Binding
Material & Support
Cross-domain Synthesis
Context & Version
Failure & Degradation
Configuration & Retrieval
```

Required lanes：Model Off、Fake/Adversarial Provider、显式授权 Live Provider。

Zero-tolerance gates：

```text
unsupported_relation_publish_rate = 0
source_domain_bleed_rate = 0
portfolio_fact_mutation_rate = 0
invalid_public_reference_publish_rate = 0
cross_version_material_mix_rate = 0
secret_or_private_content_leak = 0
```

Steps:

1. 扩展现有 Eval Schema，不平行创建 `P5EvalHarness` 孤岛；P4 专属类可由兼容 Adapter 进入统一维度。
2. Oracle 只评判结构与事实，不进入生产 Router/Provider 输入。
3. 增加 Contract Conformance：角色一致、Block/Statement ID 幂等、Source Catalog、Context disposition、409 契约。
4. Context 测试注入 Clock、内容快照和 Revision，禁止真实等待。
5. 生成不含访客内容的发布证据包；真实 Provider 未运行时状态必须为 INCOMPLETE。

Gate：六 Suite 全部通过；零容忍指标为零；确定性契约/版本/路由门禁 100%。

## Task 14: Consumer checkpoint, cleanup and documentation closure

**Backend files/docs:**

- Consume, do not design: frontend contract-preflight result
- Modify: `docs/13-Agent对话体验与智能编排改造路线图.md`
- Modify: `docs/00-文档状态索引.md`
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/11-项目演进日志.md`
- Remove only with call-path proof: v1 Writer、expired aliases、string Synthesis、bottom Failover Retriever、old free General path and obsolete DTO projection code

Steps:

1. 在后端正式发出新枚举/新语义前，取得 Consumer Compatibility Preflight：前端能解析可选字段、未知枚举、409 与 `ANSWER + CONTEXT_INVALIDATED`。
2. 完整前端体验不属于本后端计划；后端只交付 JSON/OpenAPI Schema、Fixture、字段权威性和兼容周期。
3. `stp-v1` 只保留一个明确发布窗口；v1 Context Reader 至少保留 `7 天 Absolute TTL + Rollback Window`。
4. 通过 `rg`、架构测试和运行路径测试证明旧路径不可达后再删除；不能只按类名猜测。
5. 文档如实更新为 implemented/disabled/incomplete/deployed 等状态，不把默认关闭或未跑 Live Probe 写成已上线。

Final verification：

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true test
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-release.ps1
```

若完整发布门禁会执行前端，而前端 P5 尚未合入，先运行后端门禁并如实标记 `FRONTEND_INTEGRATION_INCOMPLETE`；不得宣称 P5 整体完成。

## 4. Dependency and release order

```text
Task 0 Baseline
  -> Task 1 stp-v2
  -> Task 2 Role/Status
  -> Task 3 Subject Binding
  -> Task 4 Public Support
  -> Task 5 General Material

Task 1
  -> Task 6 Context v2 Reader
  -> Task 7 Context Routing + v2 Writer

Task 3 + Task 4 + Task 5
  -> Task 8 Multi-source
  -> Task 9 Deterministic Cross-domain
  -> Task 10 Operation Config
  -> Task 11 Cross-domain Expression

Task 9
  -> Task 12 Retrieval Matrix

Tasks 1—12
  -> Task 13 Eval
  -> Consumer Compatibility Preflight
  -> Task 14 Cleanup/Docs
```

发布纪律：

- 新 DTO 可先存在，但前端 Preflight 前后端不得开始依赖其新语义。
- Context 必须 Reader-first、Writer-second，回滚版本必须识别 v2。
- Multi-source 先于 Deterministic Cross-domain。
- Deterministic Cross-domain 先于 Cross-domain Model Expression。
- Retrieval Matrix 独立发布，不与 Synthesis 同时改变输入和输出。
- 不增加全局 `p5.enabled`；使用具体产品开关、Operation Mode、Retrieval 配置和版本化契约。

## 5. Definition of done

后端任务只有在以下条件同时满足时才可标记完成：

1. P5 Spec 第 5—15 节生产语义已实现，第 16 节后端 Eval 门禁通过，第 17 节后端迁移完成。
2. `/api/v2/answers` 的 v1 兼容和 v2 Contract Tests 全绿。
3. Model Off Lane 与 Adversarial Provider Lane 全绿。
4. Context v1/v2、TTL、版本、Ordered Result、幂等 ID 和回滚测试通过。
5. Retrieval 无双层 fallback、无跨版本 Material，False Sufficient 为零。
6. 所有新模型 Operation 与 Cross-domain 产品能力默认关闭。
7. 未经授权不调用 Live Provider；未运行时如实标记 INCOMPLETE。
8. 代码质量、架构、隐私和后端全量测试通过。
9. 前端 Consumer Preflight 已确认；完整前端体验未完成时不得宣称整体 P5 完成。
10. 状态索引、当前实现状态、路线图和演进日志均与代码事实一致。
