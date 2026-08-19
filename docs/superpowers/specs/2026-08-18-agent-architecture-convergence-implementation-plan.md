# Agent 架构收敛实施计划

- **状态：** `COMPLETE_SLICE_0_TO_6`
- **创建时间：** 2026-08-18
- **实施授权：** 2026-08-18 用户已明确授权按 Slice 0～6 顺序执行；后续消息追加授权删除旧权威、分批 commit、Frontend 原子切换联调与真实 Provider 本地验收；未授权 push/deploy
- **设计依据：** `2026-08-17-agent-architecture-convergence-design.md` 中 D-01～D-47
- **目标：** 用垂直 Replacement Slice 将当前多版本、多结果、多状态、多入口 Agent 链替换为首次生产唯一架构；新权威进入生产链时同步删除旧权威，不保留永久 fallback/compatibility stack。

## 1. 最终调用链

`HTTP -> AgentTurnLifecycleService -> TurnExecutionStore.claim -> GoalResolver -> SemanticPlanCompiler -> SemanticTurnEngine -> Capability Executors -> PublicAgentTurnProjector -> TurnSettlement -> PublicAgentTurn DTO`

核心数据流：

`AgentTurnCommand -> UserGoalProposal -> SemanticTurnPlan(UserGoals + Tasks + DataEdges) -> TaskOutcomes(TaskArtifact) -> GoalCoverage -> PublicAgentTurn`

## 2. 实施纪律

1. 在独立 convergence branch/worktree 完成，不把中间双结构部署为生产；
2. 每个 slice 开始前补充 Replacement Manifest，结束时同时删除旧生产类、配置、DTO、前端分支、测试和 fixtures；
3. 临时 bridge 只能存在于未完成 slice 内，名称包含 `Migration` 并在 slice exit gate 中列为必须为零；不得使用 Legacy/Compatibility 转发壳跨 slice；
4. 不在重构期间新增产品能力；运行数值先沿用 benchmark 配置，最终由指标/eval调整；
5. Public Content/Release 管线保持可用，Agent State schema 可在首次生产前重建；
6. 每个 slice 必须保持源码可编译、相关测试可运行；大范围公共 API/Frontend 切换在一个原子 slice 完成。

## 3. Slice 0：冻结范围与目标行为基线

### 新增/替换

- 建立 D-34 最终用户语义场景矩阵；
- 建立 D-38 PublicAgentTurn Golden Fixtures；
- 记录 Java/Frontend 文件数、LOC、public types、Beans、DTO字段、双向imports、测试运行时间；
- 建立简单模块依赖目标检查；
- 固定当前 ContentReleaseId 和 Eval dataset hash。

### 删除

- 不删除生产代码；只删除已确认完全无生产语义且不参与基线的重复/损坏 fixture（需逐项列证据）。

### Exit Gate

- Golden Fixtures 覆盖五种 Turn variants及Answer complete/partial/no-result/local clarification；
- 约30个目标场景有可执行测试骨架；
- feature freeze 生效；
- 基线报告写入本计划附录。

## 4. Slice 1：Closed Command、Goal Proposal 与唯一 Plan Compiler

### 新增/替换

- `com.portfolio.agent.turn.api` 的 closed request DTO；
- `AgentTurnCommand = Ask(FreeText|Preset) | Continue | ResolveClarification`；
- `GoalInterpretationPort`、strict Goal Proposal codec；
- `UserGoalProposal`、`UserGoal`、新的 `SemanticTurnPlan`；
- 唯一 deterministic `SemanticPlanCompiler/Validator`；
- bounded ConversationWindow 与 SurfaceContext；
- 三种真实 topology 和 data-only edges。

### 删除目标

- `ConversationAnswerRequest.TurnAction` 与 optional action 字段袋；
- `SemanticTurnInput` 多版本/兼容构造；
- `SemanticTurnRequestMapper` 旧 context/confirmation/contract branches；
- `LegacySemanticContextAdapter`、Legacy/Shadow/Default多Router；
- `SemanticClassifierPort/Codec` 与独立classifier；
- 模型 Task/DAG Proposal、`ProposalCompiler` 旧任务编译链；
- Plan Confirmation/Adjustment/Invalidation DTO/domain/service/crypto/config/tests；
- dependency type/origin/sourceTaskIds等非数据边权威；
- request/response stp-v1/v2/v3选择逻辑。

### Exit Gate

- 自由文本只调用Goal Interpretation；Preset/Continue/Clarification产生同一种Goal Proposal输入；
- 模型不能输出Task/DAG；
- Plan显式含UserGoals和唯一fulfillmentTaskId；
- 旧Router/Confirmation/Contract production references为零。

## 5. Slice 2：Execution Kernel、Deadline、Cancellation 与 TaskArtifact

### 新增/替换

- `com.portfolio.agent.turn.execution`；
- ready-set bounded parallel `SemanticTurnEngine`；
- 一个 TurnDeadline + SettlementReserve；
- `ActiveTurnRegistry` cancellation signal；
- `TaskOutcome` identity + sealed terminal variants；
- `TaskArtifact(TaskSemanticResult, optional TaskPresentation, provenance, minimal metadata)`；
- Goal Coverage 结算；
- stable outcome ordering、late result settlement gate。

### 删除目标

- 顺序 `SemanticTurnCoordinator` for-loop；
- `ExecutionSelection`/PRIMARY计数/PlanOutcome推导；
- `TaskExecutionStatus + TaskResolution + EvidenceState` 交叉状态；
- `TaskResultPayload` 多代结果；
- `GroundedAnswerContribution`；
- `TaskComposition.degraded`、所有public/cross-layer degraded；
- `SemanticTurnExecutionBudget/TaskExecutionAllowance` 混合字段袋；
- ORDER_AFTER/USES_AVAILABLE_RESULTS等非真实data edges；
- fake execution stages。

### Exit Gate

- 独立ready nodes并行且稳定提交；
- 普通失败只阻塞真实下游；
- deadline/cancel/late result race测试通过；
- Answer Resolution只按UserGoal coverage；
- 旧Outcome/Payload/Contribution production references为零。

## 6. Slice 3：Portfolio Capability 垂直替换

### 新增/替换

- `turn.capability.portfolio.PortfolioTaskExecutor`；
- `PortfolioInvocationFactory -> PortfolioEvidenceCapability`；
- raw `RetrievalAttemptResult`、一次 classified fallback；
- 唯一 Evidence Promotion/Support Evaluation；
- `PortfolioSemanticResult` closed variants；
- canonical `PortfolioPresentation`；
- 可选Fact Expression typed Port + shared StructuredModelTransport；
- selected-unit provenance和public references。

### 删除目标

- P2/P3/P4阶段命名；
- single-invocation ExecutionPlan/TrustedPlan/Validator/Catalog/Constraints；
- EffectiveRetrievalPlan与Spring ports双fallback权威；
- duplicate Bundle fallback bean；
- Adapter/Capability双Evidence Promotion；
- CapabilityExecutionResult同时表示attempt/final；
- ResultPolicy策略外壳、Refine空delegate；
- Material→Contribution→Plan→Payload多次复制；
- RecommendationProjection双权威；
- 旧 `answer.domain/service` Material/Composer平行实现；
- Expression CircuitBreaker、三次eligibility、Compare/Recommend伪表达投影、公开fallback/degraded；
- 旧Portfolio executor/config/tests/eval adapters。

### Exit Gate

- Task→Invocation→Capability→SemanticResult→Presentation→Artifact一条链；
- Primary最多一次+classified fallback最多一次，共享deadline；
- Business empty/integrity/version不fallback；
- canonical Presentation总能在合法budget产出；
- 模型表达失败只记diagnostic；
- fakeCitation/falseSufficient/scopeExpansion/contentVersionMix为零。

## 7. Slice 4：General 与 Cross-domain Capability 垂直替换

### 新增/替换

- `GeneralKnowledgeModelPort`、typed request/deadline；
- `GeneralSemanticResult(topic, statements, caveats)` 与 `GeneralPresentation`；
- stable/current/high-risk Goal边界；
- `CrossDomainTaskExecutor`；
- concept anchor、一个General+一个Portfolio fan-in；
- selected statements/provenance；
- deterministic Sectioned Presentation。

### 删除目标

- `ConversationalModelPort`万能接口及Legacy classify/generate/review/suggest/summary；
- General空ConversationWindow、Legacy ConversationRoute、自由文本Depth/Audience协议；
- General Material+Payload混合Result；
- unused statementAlias/conceptTags/discourseAliases/supportKind/publicSourceKeys；
- unused DraftValidator/compatibility overload；
- substring RelationPolicy、未实现RelationType、多输入泛化；
-全文破折号Composer；
- CrossDomain Expression Pipeline/Codec/Validator/operation/prompt/tests；
-模型suggest/summary运行链。

### Exit Gate

- General只支持稳定低风险知识，失败局部Failed；
- Synthesis只消费SemanticResult，不读rendered strings；
- Portfolio VERIFIED不传播给General；
- 无anchor匹配时NoResult；
- 旧ConversationalModelPort/GeneralPipeline/Synthesis旧类production references为零。

## 8. Slice 5：Projection、Continuation、Lifecycle、API 与 Frontend 外部边界原子切换

### 新增/替换

- 唯一 `PublicAgentTurnProjector`；
- 顶层kind五种closed variants；
- Answer resolution/GoalResults/SECTIONED|RECOMMENDATION Presentation；
- Goal Notice、SourceCatalog、Support、SuggestedAction、ContinuationRef；
- local/critical Clarification；
- `AgentTurnLifecycleService`；
- 一个 `TurnExecutionStore`（Postgres Production/Memory local-test）；
- 一个TurnExecutionStore业务权威下的Agent State同库事务边界；
- short encrypted PublicTurn replay；
- typed Continuation/Clarification stores；
- ResumeToken/ContextHandle两级授权、child refinement context；
- D-46两个Controllers和四个routes；
- HMAC request fingerprint；
- clear conversation/cancel active turn；
- Frontend discriminated union和variant/presentation components；
- Goal-first布局与来源抽屉；首次生产不实现Public ExecutionSummary；
- Frontend Authorization Bearer、new API、server cancel。

### 删除目标

- `ConversationAnswerResult/Response/Mapper`；
- AgentTurnResponse optional字段袋、completedTasks/blocks/top recommendation多权威；
- public raw claim/evidence/task/reason IDs；
- public degraded/summary/kinds/noticeCode旧轴；
- fake ExecutionDisplayPlan/TaskSummary/AnswerCompositionPanel旧职责；
- `ProductionConversationService` answer/execute/findCompleted分叉；
- `ConversationalAgentRuntime`；
- in-memory idempotency+Postgres receipt叠加；
- public CompletionReceipt union；
- 逐Context Committer和singular first handle；
- expectedContextType/fullRecommendationContext/batchId/client coveredTopics；
- MOST_RECENT_ACTIVE自动选择；
- source-compatible controller overload；
- Production自动Memory fallback；
- Frontend mapAnswerResponse/mapSemanticTurnResponse版本分支；
- Preset/semantic/legacy sections优先级；
- execution-answer conflict；
- hardcoded recommendation follow-up；
- 巨型ConversationThread内旧协议分支及对应测试；
- `/api/v2/answers`、`/api/v2/conversation-context`及所有前后端测试。

### Exit Gate

- Backend/Frontend共享Golden Fixtures；
- Frontend只switch(turn.kind)；
- Supporting Presentation不公开；
- FULL/PARTIAL/NONE结构约束通过；
- 单Goal COMPLETE无内部噪声；
- public contract invalid/hard leakage为零；
- 相同requestId精确重放业务PublicTurn；
- Settlement失败但Answer已交付时，lease期内retry-after、过期后重新执行且不承诺逐字一致；
- Context+Turn snapshot+terminal原子提交；
- cancel/complete唯一获胜；
- clear撤销全部state；
-新API四路径唯一存在；
-旧Runtime/Service/Controllers/DTO/routes production references为零；
- 该Slice可拆成多个本地commit，但在全部Exit Gate满足前不得合并/部署，且不得留下跨Slice新旧协议bridge。

## 9. Slice 6：Infrastructure、Eval、配置与包清理

### 新增/替换

- shared `StructuredModelTransport`；
- Goal/General/Portfolio Expression三个typed adapters；
- 最终module configurations；
- D-40低基数metrics/events；
- D-44最终能力Eval suites和reports；
- module dependency rule；
-最终State schema/Codec/Flyway；
-更新OpenAPI/文档/部署配置。

### 删除目标

-重复OpenAI request/response/HTTP adapters；
-旧ModelOperation/Provider-app-schema配置；
- P3/P4/P5/Legacy Eval executors/catalogs；
-下线Diagnostic events/fields；
-中央巨型Configuration；
-空包、旧phase/version/legacy/compatibility命名；
-`com.portfolio.agent.answer`中已迁移全部Turn代码；
-旧State codecs/migrations（无真实生产数据时）。
- 无主死代码直接删除且不迁移：
  - `answer/service/ConversationIntentRouter.java`
  - `answer/service/DynamicQuestionService.java`
  - `answer/service/DeterministicConversationFallback.java`
  - `answer/service/ConversationWindowManager.java`
  - `answer/service/ConversationProgressClassifier.java`
  - `answer/service/ConversationSubjectGuard.java`
  - `answer/service/DeterministicPortfolioAnswerComposer.java`、`PortfolioAnswerComposer.java`及死Bean
  - `backend/src/main/java/com/portfolio/agent/selection/service/*`
  - Frontend `PublicDegradationSummary/degradationSummary` 无后端生产者的死轴及测试
- 删除上述Bean wiring后以Spring context test确认无残留注册。

### Exit Gate

- 模块依赖单向；
- Eval PASS/INCOMPLETE语义正确；
- Production artifact不包含Eval CLI/runtime beans；
- 配置只剩真实能力；
- 完整Backend/Frontend/Eval/安全/并发测试通过；
- 文件/LOC/public types/Beans/DTO字段/双向imports较基线显著下降且无新God Class。

## 10. 跨 Slice 数据驱动参数

以下先沿用benchmark起点，绝不阻塞结构迁移：

- Turn timeout、SettlementReserve；
- max ActiveTurns、maxParallelTasks；
- Model/Retrieval operation caps；
- fallback floor；
- Presentation/response limits；
- Replay/Clarification/Context TTL；
- Eval非零容忍阈值；
- DB pool size/cleanup batch。

最终值依据D-40 metrics和D-44 controlled eval确定，关系约束由D-42保证。

## 11. 最终 Definition of Done

1. 首次生产只存在D-46 API和D-38 PublicAgentTurn；
2. v1/v2/v3/P2/P3/P4/P5/Legacy/Shadow/Compatibility生产引用为零；
3. Goal Interpretation→唯一Plan Compiler→ready-set Engine→三个Capability→唯一Projector→原子Settlement一条链；
4. TaskOutcome/Artifact/GoalCoverage无交叉状态或多正文；
5. Portfolio证据/General知识/Cross-domain支持边界明确；
6. Context/Token/State/隐私/安全满足D-29/D-39/D-43/D-45；
7. 公共degraded、CompletionReceipt、Plan Confirmation、假Execution阶段和前端业务重算全部删除；
8. D-34目标场景、Golden Fixtures、D-44能力Eval通过；
9. O-04关闭且Frontend/Backend/Docs无旧路径；
10. 主设计文档、实施计划、OpenAPI、部署配置和实际代码一致。

## 12. 已确认的提交与部署策略

- Slice 0基线 + 六个Replacement Slices；
- Slice 1～4、6分别形成独立commit组，但不独立部署仍依赖旧外部合同的半成品；
- Slice 5允许多个内部commit，只在Projection/State/Lifecycle/API/Frontend外部边界全部切换后整体合并/部署；
- 同Slice临时bridge必须在Exit Gate前删除，不跨Slice；
- 下一步展开每个Slice的文件级任务清单、删除清单和验证命令。
- 回退只使用完整代码/JAR/部署单元，不在运行时保留旧 Coordinator、Router、DTO、API 或兼容开关；当前未提交工作树的逐文件回退纪律见 `docs/handoffs/2026-08-18-agent-architecture-convergence-backend-rollback.md`。

## 13. Slice 0 文件级任务清单

### S0-01 建立共享场景与公共合同目录

**新增候选：**

- `contracts/agent-turn/scenarios/*.json`：输入、目标Goal顺序、预期Turn kind/resolution/coverage、允许Notice；
- `contracts/agent-turn/fixtures/answer-complete.json`
- `contracts/agent-turn/fixtures/answer-partial.json`
- `contracts/agent-turn/fixtures/answer-no-result.json`
- `contracts/agent-turn/fixtures/answer-local-clarification.json`
- `contracts/agent-turn/fixtures/clarification.json`
- `contracts/agent-turn/fixtures/conversational.json`
- `contracts/agent-turn/fixtures/boundary.json`
- `contracts/agent-turn/fixtures/capability-unavailable.json`
- `backend/src/test/java/com/portfolio/agent/turn/contract/AgentTurnScenarioManifestTest.java`
- `backend/src/test/java/com/portfolio/agent/turn/contract/PublicAgentTurnGoldenFixtureStructureTest.java`
- `frontend/src/features/agent/model/publicAgentTurnGoldenFixtures.test.ts`

**实施说明：**

- Slice 0 fixtures是目标合同，不引用旧Response DTO；先用JSON结构linter验证，Slice 5再由Backend serializer/Frontend parser共同消费；
- Frontend测试通过Node `fs` 从repo-root contracts读取，不复制fixture；若Vitest root限制读取，则只增加一个明确test loader，不复制数据；
- scenario manifest不保存生产问题，使用审核后的合成/公开问题。

**验证：**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=AgentTurnScenarioManifestTest,PublicAgentTurnGoldenFixtureStructureTest test
npm.cmd --prefix frontend test -- --run publicAgentTurnGoldenFixtures.test.ts
```

### S0-02 建立目标场景清单

至少写入D-34的30类场景，按以下文件组组织：

- `turn-interaction.json`
- `goal-dag.json`
- `portfolio-capability.json`
- `general-synthesis.json`
- `lifecycle-state.json`
- `public-contract.json`
- `security-adversarial.json`

每个case只保存：caseId、command、surface/window摘要、required capabilities、预期Goal/Turn结构、hard-error expectations；不保存内部Task类名/旧字段。

### S0-03 记录复杂度基线

**新增：**

- `docs/superpowers/specs/2026-08-18-agent-architecture-convergence-baseline.md`

记录：

- `answer`范围Java文件/LOC/public class/interface/Spring Beans；
- 顶层package import edges和双向edges；
- request/response DTO字段数；
- Backend/Frontend测试文件和最大测试文件；
- v1/v2/v3/P3/P4/P5/Legacy/Compatibility生产与测试引用；
- 当前全量验证耗时；
- 当前Git基线commit和ContentReleaseId。

基线只用于前后比较，不设置机械删行KPI。

### S0-04 建立行为冻结门

**更新：**

- 本实施计划的“非Replacement功能进入backlog”清单；
- `docs/00-文档状态索引.md` 登记本设计/计划；
- 若D-01～D-47改变当前实现状态，在真正代码Slice完成时再更新`docs/08`/`docs/11`，Slice 0不提前宣称已实现。

### Slice 0 验证全集

```powershell
git diff --check
mvn.cmd -f backend/pom.xml -Dtest=AgentTurnScenarioManifestTest,PublicAgentTurnGoldenFixtureStructureTest test
npm.cmd --prefix frontend test -- --run publicAgentTurnGoldenFixtures.test.ts
```

## 14. Slice 1 文件级任务清单：Command、Goal、Plan

### S1-01 建立目标包和Closed Application Command

**新增：**

- `backend/src/main/java/com/portfolio/agent/turn/lifecycle/AgentTurnCommand.java`
- `backend/src/main/java/com/portfolio/agent/turn/lifecycle/ConversationWindow.java`
- `backend/src/main/java/com/portfolio/agent/turn/api/request/AgentTurnRequest.java`
- `backend/src/main/java/com/portfolio/agent/turn/api/request/AgentTurnRequestMapper.java`

`AgentTurnCommand`在一个文件内使用显式immutable nested classes表达：Ask/FreeText/Preset、Continue、ResolveClarification、Choice/Text answer和小型SurfaceContext；不使用record/Lombok，不为每个三字段variant拆包。ConversationWindow只保存bounded `List<Message(role,text)>`及必要上限，不建设校验类体系。

**替换调用：**

- `ConversationAnswerController`在本branch检查点只接收新`AgentTurnRequest`并传入新application command；response仍保持当前临时输出直到Slice 5，不保留第二个request DTO；
- Frontend `answerApi.ts` 同步只发送closed command，但路径暂可保持旧Controller mapping直到Slice 5原子改路由；不同时发送old/new字段。

**删除：**

- `answer/dto/request/ConversationAnswerRequest.java`
- `answer/dto/request/ConversationAnswerContextRequest.java`
- `answer/dto/request/SemanticContextRequest.java`
- `answer/dto/request/PlanConfirmationRequest.java`
- `answer/dto/request/PlanAdjustmentRequest.java`
- `answer/dto/request/InvalidatedPlanReferenceRequest.java`
- `answer/dto/request/ClarificationResolutionRequest.java`
- `answer/dto/request/ContextReferenceRequest.java`
- 对应validation/mapper tests；Frontend旧request types/conditional serialization tests。

`ConversationMessageRequest`、AudienceRole、AnswerRequestSource的有效职责迁入新request/surface/window后删除旧文件。

### S1-02 建立Goal Proposal领域模型

**新增：**

- `turn/planning/UserGoalProposal.java`
- `turn/planning/UserGoal.java`
- `turn/planning/GoalKind.java`
- `turn/planning/GoalKnowledgeRequirement.java`
- `turn/planning/GoalSubjectReference.java`
- `turn/planning/GoalRequestedOutput.java`
- `turn/planning/ClarificationProposal.java`
- `turn/planning/GoalInterpretationResult.java`

模型只包含Goal语义、公开subject候选/anchor、requested outputs和stable/current/high-risk要求；不含Task type、sourceTaskIds、dependency、retrieval/backend/tool/provider。

**删除/替换：**

- `answer/routing/domain/TurnProposal.java`
- `answer/routing/service/ProposalCompilationResult.java`旧Task语义
- `answer/routing/service/SemanticSignals*`/`SemanticSignalCollector.java`旧GoalCandidate权威（逐引用核对后删除）；
- TurnProposal/Signal相关测试。

### S1-03 建立Goal Interpretation Port与严格Codec

**新增：**

- `turn/planning/GoalInterpretationPort.java`
- `turn/planning/GoalInterpretationInput.java`
- `turn/planning/GoalProposalCodec.java`
- `turn/infrastructure/model/GoalInterpretationAdapter.java`（Slice 6再抽共享Transport，当前不得复制业务Port）；
- `turn/planning/GoalInterpretationInputFactory.java`
- 对应unit/adversarial tests。

**替换/删除：**

- `answer/routing/gateway/TurnInterpretationPort.java`
- `answer/routing/adapter/model/TurnProposalCodec.java`
- `answer/adapter/model/OpenAiCompatibleTurnInterpretationAdapter.java`
- `answer/routing/service/TurnInterpretationInputFactory.java`
- `answer/adapter/model/TurnInterpretationMode.java`和旧mode矩阵；
- `SemanticClassifierPort/Codec`、旧semantic classifier wiring/tests；
- `OpenAiCompatibleTurnInterpretationAdapterTest`、`TurnProposalCodecTest`、`TurnInterpretationPortTest`由新tests替换。

Codec tests必须覆盖unknown/duplicate/oversized、Task/DAG字段注入、非公开subject、high-risk/current、损坏JSON不修复。

### S1-04 建立唯一Goal Resolver

**新增：**

- `turn/planning/GoalResolver.java`
- `turn/planning/GoalBoundaryPolicy.java`
- `turn/planning/MinimalGoalFallback.java`
- `turn/planning/ResolvedGoalSet.java`

职责：FreeText只调用GoalInterpretationPort；Preset/Continue/Clarification产生同一Proposal结构；Provider failure只走受限minimal fallback/CapabilityUnavailable，不调用旧Router。

**删除：**

- `answer/routing/service/TurnRouter.java`
- `DefaultTurnRouter.java`
- `ModelLedTurnRouter.java`
- `ShadowTurnRouter.java`
- `LegacySemanticContextAdapter.java`
- `SemanticTurnContractPolicy.java`
- `answer/service/ConversationIntentRouter.java`
- `answer/routing/service/GlobalBoundaryGate.java`有效规则迁入GoalBoundaryPolicy后删除；
- 上述全部tests，由`GoalResolverTest/GoalBoundaryPolicyTest`替换。

### S1-05 建立唯一Plan模型、Compiler与Validator

**新增：**

- `turn/planning/SemanticTurnPlan.java`
- `turn/planning/SemanticTask.java`
- `turn/planning/SemanticTaskParameters.java`
- `turn/planning/TaskDependency.java`（只保留from/to data edge）
- `turn/planning/SemanticPlanCompiler.java`
- `turn/planning/SemanticPlanValidator.java`
- `turn/planning/ValidatedSemanticTurnPlan.java`

规则：Plan保存ContentReleaseId和ordered UserGoals；每Goal唯一fulfillmentTaskId；只生成single deep node、independent goals、真实fan-in；Cross-domain恰好General+Portfolio；边无type/origin/order语义。

**替换/删除：**

- `answer/routing/domain/SemanticTurnInput.java`
- `SemanticContext.java`
- `SemanticTurnPlan.java`
- `SemanticTask.java`
- `SemanticTaskParameters.java`
- `TaskDependency.java`
- `answer/routing/service/SemanticPlanCompiler.java`
- `ProposalCompiler.java`
- `SemanticPlanValidator.java`
- `ValidatedSemanticTurnPlan.java`
- `PlanFingerprintService`及仅Confirmation使用的fingerprint路径；
- 旧Plan/Task/Compiler/Validator tests，保留产品行为后改写到新package。

Slice 2尚未替换Engine时，当前Coordinator必须直接消费新`ValidatedSemanticTurnPlan`；不保留旧Plan DTO/adapter。允许在本branch内同步修改Coordinator编译依赖，但不得建立new-plan→old-plan converter。

### S1-06 删除Plan Confirmation全链

**删除：**

- `answer/routing/domain/PlanConfirmation.java`
- `answer/routing/service/PlanConfirmationService.java`
- `answer/routing/adapter/crypto/PlanCryptographyPort.java`
- `answer/routing/adapter/crypto/JdkPlanCryptographyAdapter.java`
- confirmation/invalidation/adjustment response DTOs和Mapper分支；
- `ConversationalAgentProperties` confirmation keys；
- `ConversationalAgentConfiguration` crypto/confirmation beans；
- Frontend PlanConfirmation/PlanInvalidated components、state、events、tests；
- 所有PlanConfirmation/InvalidatedPlan/PlanAdjustment tests/fixtures。

### S1-07 Wiring与配置收敛

**更新：**

- 当前Configuration只注册GoalInterpretationPort、GoalResolver、唯一Compiler/Validator；
- 删除Legacy/Shadow/mode/contract/classifier/confirmation properties和env examples；
- `ConversationalAgentRuntime`在Slice 1临时只保留调用新Resolver/Plan和旧Execution/Response的最薄协调，不能继续包含旧routing/confirmation；该类必须在Slice 5删除，不新增wrapper；
- `docs/08`仅在新Goal/Plan真实进入生产链后更新；`docs/11`记录方向替换，不记录步骤。

### Slice 1 Target Tests

- `AgentTurnRequestValidationTest`
- `AgentTurnRequestMapperTest`
- `GoalProposalCodecTest`
- `GoalInterpretationInputFactoryTest`
- `GoalResolverTest`
- `GoalBoundaryPolicyTest`
- `SemanticPlanCompilerTest`
- `SemanticPlanValidatorTest`
- `PlanTopologyContractTest`
- `GoalFulfillmentContractTest`
- `ConversationAnswerControllerTest`（仅新request shape；response临时测试将在Slice 5替换）
- Frontend `answerApiRequest.test.ts`

### Slice 1 零引用门

```powershell
rg -n "stp-v1|stp-v2|stp-v3|CONFIRM_PLAN|REGENERATE_PLAN|PlanConfirmation|PlanAdjustment|InvalidatedPlanReference|LegacySemanticContextAdapter|ShadowTurnRouter|DefaultTurnRouter|SemanticTurnContractPolicy|TurnProposal|SemanticClassifier" backend/src/main/java frontend/src
```

预期：除历史docs/实施计划和明确待Slice 5删除的response-only字段外，生产引用为零；任何例外逐行登记，不能用宽泛allowlist。

### Slice 1 验证命令

```powershell
git diff --check
mvn.cmd -f backend/pom.xml -Dtest=AgentTurnRequestValidationTest,AgentTurnRequestMapperTest,GoalProposalCodecTest,GoalResolverTest,GoalBoundaryPolicyTest,SemanticPlanCompilerTest,SemanticPlanValidatorTest,PlanTopologyContractTest,GoalFulfillmentContractTest,ConversationAnswerControllerTest test
npm.cmd --prefix frontend test -- --run answerApiRequest.test.ts
mvn.cmd -f backend/pom.xml test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
```

### Slice 1 Exit Gate

1. 新closed request和Goal/Plan进入唯一生产路径；
2. FreeText只调用GoalInterpretationPort，Model只输出Goal；
3. Preset/Continue/Clarification进入同一Proposal/Compiler；
4. Plan显式UserGoals/fulfillment，只有三类topology/data edge；
5. Legacy/Shadow/Classifier/Confirmation/Contract/双Context生产引用为零；
6. 没有new→old Plan converter和跨SliceRequest bridge；
7. Target tests/full tests/build/privacy check通过；
8. 删除的旧tests没有以compatibility constructor形式复活。

## 15. Slice 2 文件级任务清单：Execution Kernel

### S2-01 建立Execution核心合同

**新增：**

- `backend/src/main/java/com/portfolio/agent/turn/execution/SemanticTaskExecutor.java`
- `turn/execution/TaskExecutionContext.java`
- `turn/execution/TaskOutcome.java`
- `turn/execution/TaskArtifact.java`
- `turn/execution/TaskSemanticResult.java`
- `turn/execution/TaskPresentation.java`
- `turn/execution/TaskProvenance.java`
- `turn/execution/TaskTerminalReason.java`
- `turn/execution/SemanticTurnOutcome.java`
- `turn/execution/GoalCoverage.java`

`TaskOutcome`为identity+一个nested sealed terminal：Produced/NoResult/Rejected/Failed/Blocked/Skipped/Cancelled/TimedOut；Produced持有TaskArtifact和FULL/PARTIAL。Reason使用typed enum/variant，不保留任意字符串Set。所有值对象使用显式immutable class，不用record/Lombok。

### S2-02 实现Ready-set Scheduler

**新增：**

- `turn/execution/SemanticTurnEngine.java`
- `turn/execution/ReadySetScheduler.java`（package-private）
- `turn/execution/TurnDeadline.java`
- `turn/execution/CancellationSignal.java`
- `turn/execution/LateResultGate.java`（可并入Scheduler/Engine，除非独立不变量足够深）

实现：

- stable topological validation/order；
- 每轮收集全部ready nodes，受`maxParallelTasks`限制并行；
- 全部inbound终态后只传Produced SemanticResults；无Produced输入直接Blocked；
- executionDeadline停止启动/等待，保留已完成分支；
- cancellation/deadline结算一次；
- Future完成顺序不改变Outcome/Goal顺序；
- late result只计diagnostic，不进入Outcome。

不实现动态completion-driven scheduler、Task type semaphore、provider quota、heartbeat或预测器。

### S2-03 Goal Coverage与Turn Outcome

**新增/更新：**

- `turn/execution/GoalCoverageProjector.java`可作为Engine内部package-private helper；
- 按Plan `goalId -> fulfillmentTaskId`映射产生FULL/PARTIAL/NONE；
- Turn内部只保存Plan顺序TaskOutcomes和GoalCoverage；Public resolution仍由Slice 5 Projector拥有。

**删除：**

- `answer/routing/domain/SemanticTurnOutcome.PlanOutcome`
- `derivePlanOutcome()`和PRIMARY计数；
- `TaskFulfillmentRole`及OPTIONAL；
- `ExecutionSelection`与selection reason map；
- `hasRenderablePayload()`作为执行/依赖成功判断。

### S2-04 三个现有Executor直接迁到TaskArtifact

为避免跨Slicebridge，本Slice同步改动：

- `P3PortfolioSemanticTaskExecutor`直接实现新`SemanticTaskExecutor`并返回TaskArtifact；当前`composition.domain.PortfolioAnswerMaterial`临时实现`TaskSemanticResult`，当前typed `PortfolioAnswerPlan`/section表示直接迁为`TaskPresentation`，不再构造TaskResultPayload/Contribution；Slice 3再移动/收窄命名；
- `GeneralSemanticTaskExecutor`让`GeneralAnswerMaterial`直接实现`TaskSemanticResult`，Renderer返回最小`TaskPresentation`；不再丢Material只存Payload；Slice 4再移包/删字段；
- `DeterministicSynthesisTaskExecutor`先产生最小`CrossDomainSemanticResult`和Sectioned Presentation，不读取rendered payload；Slice 4再替换relation算法；
- 不创建`LegacyTaskArtifactAdapter`、`PayloadSemanticResult`或new→old Outcome converter。

### S2-05 删除旧Execution模型

**删除：**

- `answer/routing/service/SemanticTurnCoordinator.java`
- `answer/routing/service/SemanticTaskExecutor.java`
- `answer/routing/domain/TaskOutcome.java`
- `TaskResultPayload.java`
- `TaskResultProvenance.java`
- `TaskComposition.java`
- `SemanticTurnOutcome.java`
- `SemanticTaskExecutionContext.java`
- `SemanticTurnExecutionBudget.java`
- `TaskExecutionAllowance.java`
- `ExecutionSelection.java`
- `TaskFulfillmentRole.java`
- `answer/domain/GroundedAnswerContribution.java`
- 旧类的contract/factory/budget/coordinator tests。

`TaskDependency`已在Slice 1只保留data edge；本Slice删除运行时对DependencyType/Origin/renderable payload的剩余判断。

### S2-06 Concurrency/Deadline/Cancel Tests

**新增目标测试：**

- `ReadySetSchedulerTest`
- `SemanticTurnEngineParallelismTest`
- `SemanticTurnEngineDependencyTest`
- `SemanticTurnEngineStableOrderTest`
- `SemanticTurnEngineDeadlineTest`
- `SemanticTurnEngineCancellationTest`
- `SemanticTurnEngineLateResultTest`
- `TaskOutcomeContractTest`（新terminal）
- `TaskArtifactContractTest`
- `GoalCoverageTest`

使用controlled executor/futures、fake clock/cancellation，不用真实sleep；并发断言检查最大inflight、开始集合、提交顺序和异常隔离。

### Slice 2 零引用门

```powershell
rg -n "TaskResultPayload|GroundedAnswerContribution|TaskFulfillmentRole|ExecutionSelection|SemanticTurnExecutionBudget|TaskExecutionAllowance|hasRenderablePayload|PlanOutcome|TaskExecutionStatus|TaskResolution|EvidenceState|SemanticTurnCoordinator" backend/src/main/java frontend/src
```

预期生产引用为零；Capability当前Material可直接实现新接口，但不得出现Legacy/Payload Adapter。

### Slice 2 验证命令

```powershell
git diff --check
mvn.cmd -f backend/pom.xml -Dtest=ReadySetSchedulerTest,SemanticTurnEngineParallelismTest,SemanticTurnEngineDependencyTest,SemanticTurnEngineStableOrderTest,SemanticTurnEngineDeadlineTest,SemanticTurnEngineCancellationTest,SemanticTurnEngineLateResultTest,TaskOutcomeContractTest,TaskArtifactContractTest,GoalCoverageTest test
mvn.cmd -f backend/pom.xml test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
```

### Slice 2 Exit Gate

1. 新Engine进入唯一执行路径，旧Coordinator删除；
2. ready-set并行、最大并发、deadline/cancel/late gate可控可测；
3. dependency只传SemanticResult；
4. 所有Executor直接产TaskArtifact；
5. 旧Outcome/Payload/Contribution/Budget/Role引用为零；
6. GoalCoverage只看fulfillment terminal；
7. 无跨SliceExecution bridge。

## 16. Slice 3 文件级任务清单：Portfolio Capability

### S3-01 收敛Task→Invocation入口

**新增/迁移：**

- `turn/capability/portfolio/PortfolioTaskExecutor.java`
- `turn/capability/portfolio/PortfolioInvocationFactory.java`
- `turn/capability/portfolio/PortfolioEvidenceInvocation.java`
- `turn/capability/portfolio/AuthorizedSubjectScope.java`
- `turn/capability/portfolio/PortfolioEvidenceCapability.java`

Factory一次完成Task type/parameters、subject scope、facet/dimension/recommendation profiles和current ContentRelease绑定；不读Instant.now、不产生二级Plan。

**删除：**

- `PortfolioExecutionPlan`
- `PortfolioExecutionPlanner`
- `PortfolioPlanValidator`
- `TrustedPortfolioExecutionPlan`
- `PortfolioCapabilityCatalog`
- `CapabilityExecutionConstraints`
- planned invocation wrapper和对应tests。

### S3-02 重塑Retrieval Attempt与一次Fallback

**新增/迁移：**

- `portfolio/retrieval/RetrievalAttemptResult.java`
- `portfolio/retrieval/RetrievalAttemptFailure.java`
- `portfolio/retrieval/RetrievalFallbackPolicy.java`
- `portfolio/retrieval/PortfolioRetrieverPort.java`
- `portfolio/retrieval/PostgresPortfolioRetrieverAdapter.java`
- `portfolio/retrieval/BundlePortfolioRetrieverAdapter.java`

Adapter只返回raw CandidateSet/closed failure；Capability选择最多一次fallback并对最终CandidateSet做一次Promotion。HYBRID→KEYWORD和DB→same-release Bundle共享deadline；business empty/evidence/integrity/version/budget不fallback。

**删除/合并：**

- `BundlePortfolioCandidateRetrievalAdapter`误导命名/双Promotion；
- `CapabilityExecutionResult` attempt/final混合；
- `PortfolioCandidateRetrievalPort`重复层；
- `FailoverPortfolioRetriever`与EffectiveRetrievalPlan/Spring双权威；
- DB disabled时primary/fallback两个同Retriever Bean；
- attempt number作为CandidateSet业务字段；
- Adapter只检查deadline但I/O不消费的路径。

### S3-03 唯一Evidence Promotion、Support与SemanticResult

**新增/迁移：**

- `portfolio/evidence/EvidencePromotionValidator.java`
- `portfolio/evidence/ValidatedEvidenceBundle.java`
- `portfolio/evidence/ValidatedEvidenceUnit.java`
- `portfolio/evidence/PublicSourceReference.java`
- `portfolio/semantic/PortfolioSupportEvaluator.java`
- `portfolio/semantic/PortfolioSemanticResultFactory.java`
- `portfolio/semantic/PortfolioSemanticResult.java`（Fact/Comparison/Recommendation nested或少量package-private variants）

ResultFactory对外一个入口，Fact/Comparison/Recommendation算法可分package-private文件；删除runtime策略注册/Executor switch持有多个Policy。

**Capability Coverage表：**

| Task | FULL | PARTIAL | NoResult |
|---|---|---|---|
| Portfolio Fact | 所有requested facets有selected validated units | 至少一项有支持、至少一项omitted | 无requested facet有支持 |
| Portfolio Compare | 所有requested dimensions覆盖全部subjects | 至少一个dimension可比较但非全部 | 无dimension可比较 |
| Recommendation/Refine | actual=requested且required constraints满足 | actual>0但数量/约束不完整 | actual=0或无evidence-supported candidate |

Coverage在SemanticResult构造时确定，Executor不从Presentation/card count重算。

**删除：**

- `PortfolioResultPolicy`接口；
- `FactResultPolicy/ComparisonResultPolicy/RecommendationResultPolicy`公开策略层（算法迁入Factory内部）；
- `RefineResultPolicy`；
- `EvidenceSupportAssessment`作为跨层大对象（有效omissions/selected units进入SemanticResult构造）；
- `SafeReasonCode`公共/字符串reason层；
- duplicate old `answer.domain.GroundedStatement/PortfolioAnswerMaterial`。

### S3-04 Canonical Presentation与可选Fact Expression

**新增/迁移：**

- `portfolio/presentation/PortfolioPresentation.java`
- `portfolio/presentation/PortfolioPresentationComposer.java`
- `portfolio/presentation/PortfolioFactExpressionPort.java`
- `portfolio/presentation/PortfolioFactExpressionCompiler.java`
- package-private input projector/strict codec/grounding validation；
- `portfolio/presentation/PresentationPolicy.java`拥有section/character bounds。

Canonical deterministic presentation先生成且对合法budget总能产出；Model只在Fact eligible时调用一次并原子替换。Codec严格decode，Compiler一次验证scope/alias/source/caveat并构造Presentation。

**删除：**

- `GroundedAnswerContribution`转换；
- `PortfolioAnswerPlan -> TaskResultPayload`复制；
- `PortfolioCompositionResult`多状态/fallback/degraded；
- `ExpressionCircuitBreaker`；
- 三次`ModelExpressionEligibilityPolicy.evaluate`；
- `ExpressionAllowance/requestLocalAttemptOrdinal`；
- Comparison/Recommendation expression draft/input伪泛化；
- `PortfolioAnswerPlanValidator`与Assembler重复字符口径；
- `PortfolioExpressionStartupGuard`空Bean；
- legacy enabled/schema/profile配置轴；
- `answer.service.PortfolioAnswerComposer/DeterministicPortfolioAnswerComposer`及死Bean；
-旧`composition.domain.PortfolioAnswerMaterial`在迁成SemanticResult后原文件/旧命名。

Slice 6再把三Model Adapter的HTTP细节抽到StructuredModelTransport；本Slice的Fact Adapter必须已经使用typed Port/deadline，不能依赖万能ConversationalModelPort。

### S3-05 Portfolio Executor与配置收口

- `P3PortfolioSemanticTaskExecutor`替换为`PortfolioTaskExecutor`，只顺读Invocation→Capability→SemanticResult→Presentation→Artifact；
- Executor不构造Recommendation第二Projection/Provenance空列表/Context；
- `PortfolioExecutionConfiguration`拆为一个module configuration，删除重复/死Beans；
- 当前`intelligence.execution/composition`有效类型迁入portfolio模块，旧包清空；
- 更新Eval入口只调用PortfolioTaskExecutor/typed seam，不引用P3/P4类。

### S3-06 Portfolio Target Tests

- `PortfolioInvocationFactoryTest`
- `RetrievalFallbackPolicyTest`
- `PortfolioEvidenceCapabilityTest`
- `EvidencePromotionValidatorTest`
- `PortfolioSupportEvaluatorTest`
- `PortfolioSemanticResultFactoryTest`
- `PortfolioCoverageTest`
- `PortfolioPresentationComposerTest`
- `PortfolioFactExpressionCompilerAdversarialTest`
- `PortfolioTaskExecutorTest`
- Postgres/Bundle same-release integration与deadline tests。

### Slice 3 零引用门

```powershell
rg -n "P3Portfolio|P4|PortfolioExecutionPlan|TrustedPortfolioExecutionPlan|PortfolioPlanValidator|PortfolioCapabilityCatalog|CapabilityExecutionConstraints|PortfolioResultPolicy|RefineResultPolicy|GroundedAnswerContribution|ExpressionCircuitBreaker|ExpressionDisposition|RecommendationProjection|answer\.service\.DeterministicPortfolioAnswerComposer" backend/src/main/java backend/src/test/java
```

### Slice 3 验证命令

```powershell
git diff --check
mvn.cmd -f backend/pom.xml -Dtest=PortfolioInvocationFactoryTest,RetrievalFallbackPolicyTest,PortfolioEvidenceCapabilityTest,EvidencePromotionValidatorTest,PortfolioSupportEvaluatorTest,PortfolioSemanticResultFactoryTest,PortfolioCoverageTest,PortfolioPresentationComposerTest,PortfolioFactExpressionCompilerAdversarialTest,PortfolioTaskExecutorTest test
mvn.cmd -f backend/pom.xml test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
```

### Slice 3 Exit Gate

1. Portfolio单链每层一次实质转换；
2. Promotion/Result/Presentation/provenance各一个权威；
3. fallback分类/次数/deadline符合D-23；
4. Coverage表由SemanticResult实现；
5. canonical Presentation必达，Expression失败不公开；
6. P2/P3/P4、旧Plan/Policy/Material/Composer/双Bean引用为零；
7. fakeCitation/falseSufficient/scope/contentVersion hard errors为零。

## 17. Slice 4 文件级任务清单：General 与 Cross-domain

### S4-01 General typed Model Capability

**新增/迁移：**

- `turn/capability/general/GeneralTaskExecutor.java`
- `general/GeneralKnowledgeModelPort.java`
- `general/GeneralKnowledgeRequest.java`
- `general/GeneralKnowledgeGenerator.java`
- `general/GeneralDraftCodec.java`
- `general/GeneralDraftValidator.java`
- `general/GeneralSemanticResult.java`
- `general/GeneralPresentationComposer.java`
- `general/GeneralPresentation.java`

Request直接表达explanation topic或comparison subjects/dimensions、depth、audience和deadline；不拼`Depth:/Audience:`自由文本，不传空ConversationWindow/Portfolio Route。

### S4-02 General范围与Coverage

- Goal Resolver只允许STABLE_GENERAL_EXPLANATION；CURRENT/HIGH_RISK形成Boundary/CapabilityUnavailable；
- Draft严格要求当前Goal所需roles/dimensions完整，合法Produced即FULL；缺少必需结构视为invalid draft→Failed，不用未经声明的半答案伪装PARTIAL；
- 若未来需要General PARTIAL，必须先扩展typed omission合同并独立评审，不从statement数量推断；
- General只标GENERAL_KNOWLEDGE，无source IDs/伪引用/Portfolio事实。

**删除：**

- `GeneralMaterialPipeline`
- `GeneralAnswerMaterialDraft`大字段形态；
- `GeneralKnowledgeMetadata.contentVersion` echo；
- statementAlias/conceptTags/discourseAliases/supportKind/publicSourceKeys；
- `GeneralMaterialValidationResult` nullable result；
- unused `ConversationDraftValidator`依赖/compat overload；
-旧general包在迁移后清空。

### S4-03 Cross-domain真实Fan-in

**新增/迁移：**

- `turn/capability/synthesis/CrossDomainTaskExecutor.java`
- `synthesis/CrossDomainSemanticResult.java`
- `synthesis/CrossDomainPresentationComposer.java`
- package-private anchor/statement selector。

恰好消费一个GeneralSemanticResult和一个PortfolioSemanticResult；使用Goal concept anchor选择General definition/mechanism与Portfolio grounded statements；无匹配NoResult，dependency未Produced则Blocked；选中双方并产生关系即FULL。首发一个anchor/一种“概念→项目实例”关系，不产生PARTIAL。

**删除：**

- `DeterministicSynthesisTaskExecutor`
- `CrossDomainRelationPolicy`
- `AllowedRelation/RelationType`
- `DeterministicCrossDomainComposer`
- `CrossDomainExpressionPipeline`
- `CrossDomainCompositionValidator`
- `CrossDomainDraftCodec/Exception`
- `CrossDomainRelationProperties`
- `CROSS_DOMAIN_EXPRESSION` operation/prompt/config/tests。

### S4-04 删除万能Conversation Model链

**删除：**

- `answer/gateway/ConversationalModelPort.java`
- `ConversationSummaryPort.java`
- `OpenAiCompatibleConversationalModelAdapter.java`中的legacy classify/generate/review/suggest/summary/general/cross-domain职责；
- `ConversationDraftValidator`
- `ConversationIntentRouter`
- `DynamicQuestionService`
- `ConversationWindowManager`
- `DeterministicConversationFallback`
- `ConversationProgressClassifier`
- `ConversationSubjectGuard`
- legacy ConversationRoute/Draft/GroundingReview等仅旧链使用的domain类型；
- model suggestion/summary prompts/config/tests。

保留并迁移LocalEmbeddingPort/PortfolioKnowledgeGateway等真实外部边界，不因删除God Port误删Portfolio基础能力。

### S4-05 General/Synthesis Target Tests

- `GeneralKnowledgeRequestTest`
- `GeneralDraftCodecAdversarialTest`
- `GeneralDraftValidatorTest`
- `GeneralKnowledgeGeneratorTest`
- `GeneralTaskExecutorTest`
- `GeneralBoundaryTest`
- `CrossDomainTaskExecutorTest`
- `CrossDomainAnchorSelectionTest`
- `CrossDomainSupportIsolationTest`
- `CrossDomainProvenanceTest`

### Slice 4 零引用门

```powershell
rg -n "ConversationalModelPort|ConversationSummaryPort|GeneralMaterialPipeline|GeneralAnswerMaterialDraft|GeneralSupportKind|DeterministicSynthesisTaskExecutor|CrossDomainExpression|CrossDomainRelationPolicy|RelationType|DynamicQuestionService|ConversationWindowManager|DeterministicConversationFallback|ConversationIntentRouter" backend/src/main/java backend/src/test/java
```

### Slice 4 验证命令

```powershell
git diff --check
mvn.cmd -f backend/pom.xml -Dtest=GeneralKnowledgeRequestTest,GeneralDraftCodecAdversarialTest,GeneralDraftValidatorTest,GeneralKnowledgeGeneratorTest,GeneralTaskExecutorTest,GeneralBoundaryTest,CrossDomainTaskExecutorTest,CrossDomainAnchorSelectionTest,CrossDomainSupportIsolationTest,CrossDomainProvenanceTest test
mvn.cmd -f backend/pom.xml test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
```

### Slice 4 Exit Gate

1. General typed request→model→strict result→presentation→artifact一条链；
2. General稳定/实时/高风险边界和FULL规则明确；
3. Synthesis只消费SemanticResult/anchor且support不传播；
4. 无model Synthesis/substring/多输入泛化；
5. 万能ConversationalModelPort和旧会话服务无生产引用；
6. General/Cross-domain hard errors为零。

## 18. Slice 5 文件级任务清单：外部边界原子切换

### S5-01 PublicAgentTurn模型与唯一Projector

**新增：**

- `turn/projection/PublicAgentTurn.java`
- `turn/projection/PublicAnswer.java`
- `turn/projection/AnswerGoalResult.java`
- `turn/projection/PublicPresentation.java`（Sectioned/Recommendation）
- `turn/projection/PublicSection.java`
- `turn/projection/PublicSupport.java`
- `turn/projection/PublicSourceCatalog.java`
- `turn/projection/GoalNotice.java`
- `turn/projection/SuggestedAction.java`
- `turn/projection/PublicAgentTurnProjector.java`

可将小variants作为同文件nested immutable classes，控制public类型数量。Projector按UserGoal/fulfillment投影，Supporting不公开；FULL/PARTIAL/NONE结构强校验；首发无ExecutionSummary。

### S5-02 Clarification与Continuation公共模型

**新增：**

- `turn/continuation/ClarificationChallenge.java`
- `turn/continuation/ClarificationStore.java`
- `turn/continuation/ContinuationReference.java`
- `turn/continuation/ContinuationContext.java` closed typed variants
- `turn/continuation/ContinuationResolver.java`
- `turn/continuation/ContextMutationPlanner.java`

Challenge只实现SINGLE_CHOICE/TEXT、短TTL、一次消费、opaque choice binding；local challenge携带affectedGoalIds，critical独立Turn。Continuation只公开handle+optional resultItemId；无MOST_RECENT_ACTIVE猜测。

### S5-03 TurnExecutionStore与Agent State事务

**新增：**

- `turn/lifecycle/TurnExecutionStore.java`
- `turn/lifecycle/TurnExecutionRecord.java`
- `turn/lifecycle/AgentTurnLifecycleService.java`
- package-private `ActiveTurnRegistry.java`、`TurnSettlement.java`
- `turn/state/postgres/JdbcAgentStateStore.java`
- `turn/state/memory/InMemoryTurnExecutionStore.java`（local/test only）
- final State codecs/Flyway migration；
- encrypted PublicAgentTurn replay codec；
- HMAC RequestFingerprint。

Postgres store在一个transaction中处理Turn terminal+Public snapshot+Context/Challenge mutations。TurnExecutionStore是claim/complete/cancel/replay唯一权威；Agent State只是共享DataSource/transaction描述。

### S5-04 Lifecycle、Deadline与取消

- Lifecycle顺序固定：credential/session→claim→ActiveTurn→GoalResolver→Plan→Engine→Projector→Settlement→DTO；
- claim前State失败返回API Error；claim后Settlement失败允许当前只读Answer无continuation返回，lease内retry-after、过期后重执行；
- cancel/complete竞争同一终局门；首TurnrequestId可取消，已有Conversation同时验证Bearer；
- TurnDeadline/SettlementReserve贯彻State I/O；
- new session创建/Token签发纳入claim/settlement，不留空session。

### S5-05 Context迁移与Refinement链

- 把现有`ResumeToken/ContextHandle/AuthorizedSubjectScope/RecommendationContext/RecentSemanticTaskContext`有效字段迁到`turn.continuation`；
- 合并P3/P5 codecs为最终v1；
- Context只从SemanticResult/authorized binding构造，不读Presentation；
- 只为公开fulfillment Goal创建Context；
- explicit handle优先，无handle仅唯一compatible active自动绑定；
- Recommendation refinement创建不可扩权child Context并保存新selected results/release；
- clear删除Context/Challenge/Replay/Token并取消Active Turns。

### S5-06 新API Controllers与DTO

**新增：**

- `turn/api/AgentTurnController.java`
- `turn/api/AgentConversationController.java`
- `turn/api/response/PublicAgentTurnResponse.java`或机械Jackson DTO；
- 统一`AgentApiErrorResponse`与exception mapping。

实现D-46四路径、Authorization Bearer、no-store、状态码/Retry-After；Controller不直接访问Store/Projector。

### S5-07 Frontend closed contract与组件

**新增/重写：**

- `frontend/src/features/agent/model/publicAgentTurn.ts`
- `publicAgentTurnMapper.ts`（只结构校验，不业务推导）
- `api/agentTurnApi.ts`
- `components/PublicAgentTurnMessage.vue`
- `AnswerTurnView.vue`
- `GoalResultView.vue`
- `SectionedPresentationView.vue`
- `RecommendationPresentationView.vue`
- `ClarificationTurnView.vue`
- `SourceDrawer.vue`

重写`AgentWorkspace/ConversationThread`只负责session/list/focus/pending和事件转发；所有业务actions来自SuggestedAction；取消同时abort+DELETE；消息仍memory-only、activeToken sessionStorage。

### S5-08 删除旧Projection/Lifecycle/API/Frontend

**Backend删除：**

- `ConversationAnswerResult`、`AgentTurnResult`旧字段袋；
- `ConversationAnswerResponse/Mapper`、`AgentTurnResponse`、CompletedTask/TaskSummary/DisplayPlan/ExecutionDisplayPlan/Composition/Recommendation重复DTO；
- `ProductionConversationService/Execution`、`ConversationalAgentRuntime`；
- `AnswerIdempotencyCoordinator`、旧RequestReceipt/CompletionReceipt公共联合；
- `ConversationContextCommitter/Facade`逐项save形态、MOST_RECENT resolver；
- `ConversationAnswerController`、`ConversationContextController`；
- `/api/v2/answers`与`/api/v2/conversation-context`；
- public degraded/noticeCode/generation/construction/evidenceState旧轴；
-旧Context codecs/schema/response DTO/expectedContextType/batchId。

**Frontend删除：**

- `answerTypes.ts`旧联合、`semanticTurnView.ts`、`mapAnswerResponse.ts`、`mapAnswerSuccess`、v3 fallback、recent result sets旧链；
- PlanConfirmation/PlanInvalidated/ExecutionSnapshot/TaskStatus/CompactTaskSummary/AnswerCompositionPanel/ContextInvalidatedNotice旧职责；
- degraded/composition/task reason/source fallback；
- hardcoded recommendation refine/legacy EvidenceId引用；
- 对应巨型兼容测试/fixtures。

### S5-09 Target Tests

**Backend：**

- `PublicAgentTurnProjectorTest`
- `PublicAgentTurnInvariantTest`
- `GoalNoticeProjectionTest`
- `SourceCatalogProjectionTest`
- `ClarificationChallengeStoreTest`
- `ContinuationResolverTest`
- `RecommendationChildContextTest`
- `TurnExecutionStoreContractTest`
- `JdbcAgentStateStoreIntegrationTest`
- `AgentTurnLifecycleReplayTest`
- `AgentTurnLifecycleSettlementFailureTest`
- `AgentTurnLifecycleCancellationTest`
- `AgentTurnControllerContractTest`
- `AgentConversationControllerTest`

**Frontend：**

- `publicAgentTurnGoldenFixtures.test.ts`
- variant/presentation/component tests；
- local/critical clarification；
- Goal partial/no-result/source/action；
- cancel/clear/resume；
- malicious text escaped/invalid contract；
- responsive/accessibility focused tests。

### Slice 5 零引用门

```powershell
rg -n "/api/v2/answers|/api/v2/conversation-context|stp-v1|stp-v2|stp-v3|ConversationAnswerResult|ConversationAnswerResponse|ConversationAnswerResponseMapper|CompletedTaskResponse|TaskSummaryResponse|ExecutionDisplayPlan|PlanConfirmation|CompletionReceiptResponse|degraded|degradationSummary|expectedContextType|recommendationBatchId|MOST_RECENT_ACTIVE|hasExecutionAnswerConflict" backend/src/main/java frontend/src
```

任何业务含义相同的命中必须为零；observability中普通英文“degraded”也按D-36改为具体fallback/coverage事件。

### Slice 5 验证命令

```powershell
git diff --check
mvn.cmd -f backend/pom.xml -Dtest=PublicAgentTurnProjectorTest,PublicAgentTurnInvariantTest,GoalNoticeProjectionTest,SourceCatalogProjectionTest,ClarificationChallengeStoreTest,ContinuationResolverTest,RecommendationChildContextTest,TurnExecutionStoreContractTest,JdbcAgentStateStoreIntegrationTest,AgentTurnLifecycleReplayTest,AgentTurnLifecycleSettlementFailureTest,AgentTurnLifecycleCancellationTest,AgentTurnControllerContractTest,AgentConversationControllerTest test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
mvn.cmd -f backend/pom.xml test
mvn.cmd -f backend/pom.xml package
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
```

### Slice 5 Exit Gate

1. D-38/D-46唯一wire/API；
2. Backend/Frontend共享fixtures；
3. Goal-first唯一正文、无ExecutionSummary/degraded/内部ID；
4. Lifecycle replay/cancel/settlement/context transaction语义通过；
5. Browser memory/token/clear/privacy通过；
6. 旧Runtime/Mapper/DTO/Controllers/routes/components/mappers引用为零；
7. Slice内部bridge为零后才整体合并/部署。

## 19. Slice 6 文件级任务清单：Infrastructure、Eval与最终清理

### S6-01 共享StructuredModelTransport

**新增：**

- `infrastructure/model/StructuredModelTransport.java`
- `StructuredModelRequest.java`
- `StructuredModelResponse.java`
- `StructuredModelFailure.java`
- `OpenAiCompatibleStructuredModelTransport.java`
- `ModelProviderConfiguration.java`
- `ModelOperationConfiguration.java`

Goal/General/PortfolioExpression adapters各自拥有prompt/codec，Transport统一HTTPS/auth/JSON mode/max tokens/temperature/deadline/error/diagnostics；不提供`execute(operation, Map)`领域万能Gateway。

**删除：** 重复ChatCompletion DTO/RestClient/JDK transport、旧ConversationalAgentConfiguration模型部分、Provider supported application schema/policy lists、String/enum双provider identity、dummy disabled ports、legacy ModelOperation值。

### S6-02 Module Configuration与Properties清理

建立少量：

- `TurnPlanningConfiguration`
- `TurnExecutionConfiguration`
- `PortfolioCapabilityConfiguration`
- `GeneralCapabilityConfiguration`
- `TurnLifecycleConfiguration`
- `ContinuationStateConfiguration`
- `StructuredModelConfiguration`

删除中央巨型Configuration、P3/P4/P5/legacy/mode/confirmation/degraded/schema aliases和重复timeout；启动校验D-42关系、Production state mode、model operation readiness、ContentRelease readiness。

### S6-03 Observability迁移

- module-owned event factories + sharedfield allowlist；
- 实现D-40 Turn/Goal/Task/Model/Retrieval/Settlement/Contract metrics；
- 删除route/tool/answer fallback/expression fallback/degraded/generation/假stage events和Frontend字段；
- 更新Frontend diagnostics只发request/contract/UI事件；
- tests验证Prompt/Answer/Token/Handle/Route/subject不进入events。

### S6-04 Eval按能力重组并隔离Production包

**目标suite：** goal-interpretation、planning-execution、portfolio-retrieval-evidence、general-knowledge、portfolio-expression、cross-domain、public-contract、continuation-settlement。

**删除/替换：**

- `P3EvalExecutor`
- `P4CompositionEvalRunner/P4Eval*`
- `P5SuiteCatalog`
- `LegacyRetrievalBenchmarkAdapter`
- `MockConversationalModelPort`
- phase-specific domain/enums/tests；
- old EvalAnswerShape/EvalSemanticTurnShape字段迁到D-38合同。

保留/重用dataset hash、oracle isolation、baseline comparator、PASS/FAIL/INCOMPLETE、hard-error gate和reporting有效逻辑。Eval CLI/Harness移独立tools module/source set/profile，不注册Production Beans/打入最终runtime artifact。

### S6-05 无主死代码零引用审计与空包清扫

以下类型按依赖已分别由Slice 3/4/5负责删除；本任务验证其production/test/Bean引用均为零，不重复建立第二删除Owner：

- `answer/service/ConversationIntentRouter.java`
- `DynamicQuestionService.java`
- `DeterministicConversationFallback.java`
- `ConversationWindowManager.java`
- `ConversationProgressClassifier.java`
- `ConversationSubjectGuard.java`
-旧 `answer.service.DeterministicPortfolioAnswerComposer/PortfolioAnswerComposer`及死Bean；
- `backend/src/main/java/com/portfolio/agent/selection/service/*`；
- Frontend `PublicDegradationSummary/degradationSummary`死轴；
- 所有空`answer/domain/service/adapter/gateway/intelligence/composition/routing/runtime/mapper`包与仅测试引用生产类。

若仍有残留，本Slice直接删除并将遗漏回写对应Slice manifest；逐类使用`rg`确认无真实消费者，不因Bean注册/旧测试存在而保留。`selection.service/*`没有更早依赖Owner，由本Slice直接删除。

### S6-06 模块依赖与复杂度收口

- 增加一个简单architecture test检查api→lifecycle→planning/execution/projection/continuation、capabilities实现SPI、synthesis单向依赖、核心不依赖DTO/adapter；
- 检查public class/interface数量，内部helper改package-private；
- `ActiveTurnRegistry/TurnSettlement/Factory/Codec/Validator/Composer`保持模块内部；
- 对比Slice0文件/LOC/Beans/DTO字段/双向imports/测试大小；
- 不以合并God Class/Map换取数字下降。

### S6-07 文档、State Schema与发布门

- docs/00/08/11、OpenAPI、README、SECURITY、env examples、运行手册同步最终事实；
- 删除旧stp/P3-P5/Context codecs/Flyway compatibility（无生产数据）；
- State cleanup/keys/TTL/health文档；
- Eval Report记录dataset/commit/release/provider/prompt/config；
-完整发布门只在相关能力PASS时开启，INCOMPLETE能力保持disabled；
-确认AGENTS.md D-39短期PublicTurn快照例外与实现完全一致。

### Slice 6 零引用门

```powershell
rg -n "P[2345]|stp-v|Legacy|Compatibility|Shadow|ConversationalModelPort|ModelOperation\.(ROUTING_SEMANTIC_ASSIST|CROSS_DOMAIN_EXPRESSION)|degradationSummary|ConversationIntentRouter|DynamicQuestionService|DeterministicConversationFallback|ConversationWindowManager|selection\.service" backend/src/main/java frontend/src
```

逐项判断真正历史字符串与生产引用；生产类型/配置/路由命中必须为零。

### Slice 6 最终验证

```powershell
git diff --check
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
mvn.cmd -f backend/pom.xml test
mvn.cmd -f backend/pom.xml package
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
```

再按环境可用性运行PostgreSQL integration、packaged-JAR/browser behavior audit和D-44 CONTROLLED_PROVIDER suites；缺环境必须报告INCOMPLETE，不能假PASS。

### Slice 6 Exit Gate

1. Production调用链/包/配置只有最终架构；
2. Eval工具不在runtime artifact；
3. 所有旧版本/阶段/兼容/死代码清除；
4. module dependency test通过，无新增双向依赖/God Class；
5. 完整unit/integration/frontend/build/package/privacy/eval通过或按规则INCOMPLETE；
6. docs/OpenAPI/config/current status与代码一致；
7. Slice0复杂度对比证明权威/转换/公开面显著减少。

## 20. 实际实施状态与证据

本节只记录已经发生的实现事实，不提前标记整个 Slice 完成。

### 2026-08-18 · Slice 0

| 任务 | 状态 | 证据 |
|---|---|---|
| S0-01 共享场景与公共合同目录 | COMPLETE | `contracts/agent-turn` 已建立；后端两个结构测试通过；Frontend 直接读取共享 fixtures 的 12 项消费测试通过 |
| S0-02 目标场景清单 | READY | 7 个 manifests、35 个场景，覆盖 closed interaction、Goal/DAG、Portfolio、General/Synthesis、Lifecycle、Public Contract 与 Security |
| S0-03 复杂度基线 | READY | `2026-08-18-agent-architecture-convergence-baseline.md` 固定 Git/Release/Eval、文件/LOC/public type/Bean/DTO/依赖/测试与旧引用基线 |
| S0-04 行为冻结门 | READY | 基线文档 Feature Freeze 已生效；`docs/00` 与 Frontend handoff 同步本轮状态 |

RED/GREEN 证据：

- RED：`AgentTurnScenarioManifestTest` 与 `PublicAgentTurnGoldenFixtureStructureTest` 因目录为空各失败一次，失败原因与目标能力缺失一致；
- GREEN：同一命令运行 2 tests，0 failure/error；
- 基线全量 Backend：1,273 tests，0 failure/error，21 environment-skipped，28.691 s；
- 基线全量 Frontend：67 files、728 tests，全部通过，7.37 s。

Frontend 集成复验：

- `publicAgentTurnGoldenFixtures.test.ts`：12/12 通过；
- Frontend 全量：68 files、740/740 通过；
- `npm.cmd --prefix frontend run build`：`vue-tsc` 与 Vite production build 通过；
- Frontend 未复制 fixtures，直接从仓库根读取唯一合同。

Slice 0 Exit Gate 已通过，进入 Slice 1。以下合同项不阻塞 Slice 0，但必须在 Slice 5 外部边界冻结前关闭：RECOMMENDATION Golden Fixture、完整 sectionKind 闭集、conversation metadata/ResumeToken 精确字段。

### 2026-08-18 · Slice 1（完成）

| 任务 | 状态 | 证据 |
|---|---|---|
| S1-01 Closed Application Command | COMPLETE | `AgentTurnRequest -> AgentTurnRequestMapper -> AgentTurnCommand` 已进入 `/api/v2/answers` 临时外部路径；旧 optional Request DTO/mapper 已删除 |
| S1-02 Goal Proposal 领域模型 | COMPLETE | `UserGoalProposal`、Goal kind/knowledge/output/subject 与闭合参数 variants 成为模型输出唯一权威 |
| S1-03 Goal Interpretation Port/Codec | COMPLETE | FreeText 只调用 `GoalInterpretationPort`；strict duplicate/unknown/size/anchor/catalog/Goal-count 门与对抗 Provider 测试通过；Task/DAG 注入失败关闭 |
| S1-04 Goal Resolver | COMPLETE | Preset/Continue/Clarification 统一进入 reviewed proposal seam；精确 alias fallback、Unavailable/Clarification、current/high-risk Boundary 均已接入生产链 |
| S1-05 Plan/Compiler/Validator | COMPLETE | 唯一 `turn.planning.SemanticPlanCompiler/Validator` 进入生产；Plan 显式 UserGoals/唯一 fulfillment，只允许三类 topology 与 data-only edges |
| S1-06 Plan Confirmation 删除 | COMPLETE | Confirmation/Adjustment/Invalidation DTO、domain、service、crypto、config 与专属 tests 已删除；后端生产引用为零 |
| S1-07 Wiring/配置收敛 | COMPLETE | 旧 Request/Runtime/Service/Controller、Legacy/Shadow/Default Router、Classifier、旧 Proposal/Compiler/Validator 与 mode/shadow 配置已删除；新链为唯一生产路径 |

Slice 1 Exit Gate：目标与安全替代测试 39/39 通过；CASE/Noise/Preset HTTP 基线 12/12 通过；后端全量 1,106 tests、0 failure/error、21 environment-skipped；`git diff --check` 无错误（仅 Windows 行尾提示）；`backend/src/main` 796 文件与 `contracts` 15 文件 privacy check 通过。零引用门仅剩明确登记待 Slice 5 原子删除的 response-only `stp-v1/v2/v3` 投影和 Eval 历史注释；后端 Confirmation/Legacy/Shadow/Default/Classifier/旧 Proposal 权威均为零。修复一项迁移中发现的真实语义损失：新 `SOLUTION` facet 完整覆盖实现与技术决策，`STATUS` 完整覆盖结果与局限，全部已发布 Preset 继续可执行。进入 Slice 2。

### 2026-08-18 · Slice 2（完成）

Backend 已完成：`turn.execution` 的 TaskArtifact、sealed TaskOutcome、TaskExecutionResult、GoalCoverage、TurnDeadline、CancellationSignal、ReadySetScheduler 与 SemanticTurnEngine 已进入唯一生产链；ready-set 并行/最大并发、反序 Future 稳定提交、Produced-only dependency、无 Produced 下游阻断、运行中 cancel、deadline 保留已完成分支、late result gate 与 FULL/PARTIAL/NONE coverage 均有确定性测试。General/Portfolio/Synthesis 三个 Executor 直接产强类型 Material/Presentation/Provenance，Synthesis 只消费 Material。旧 Coordinator、Payload、Contribution、Role、Selection、Budget/Allowance、ExecutionContext、交叉状态轴及旧 response-only agentTurn 执行投影已删除；Backend 零引用门无输出。目标 Engine 测试 15 tests、HTTP/合同组合 38 tests 通过；Backend 全量 1,060 tests、0 failure/error、20 environment-skipped；`git diff --check` 无错误（仅行尾提示）；backend 786 文件与 contracts 15 文件 privacy check 通过。

Frontend Agent 已删除 `PlanOutcome` type、wire 字段、view 透传及 fixtures/tests 中全部构造，未改名保留、未新增 compatibility fallback；前端全量 763/763 与 build 通过。联合零引用门现无输出，Slice 2 Exit Gate 全部关闭，进入 Slice 3。

### 2026-08-18 · Slice 3（完成）

Portfolio 已收敛为唯一 `Task -> PortfolioInvocationFactory -> raw Retriever Port -> PortfolioEvidenceCapability -> EvidencePromotionValidator -> PortfolioSemanticResultFactory -> PortfolioPresentationComposer -> TaskArtifact` 单链。DB 关闭时只有一个 Bundle Port；DB 开启时为 Postgres primary + same-release Bundle fallback。Capability 最多 primary + 一次分类 fallback，business empty/版本/完整性/取消不 fallback，Promotion 仅一次。Fact/Comparison/Recommendation Coverage 在 SemanticResult 构造时冻结；canonical Presentation 必达，可选 Fact expression 使用 typed Port + strict atomic Compiler，失败保留 canonical。

旧 P3/P4 Executor、二级 Plan/Validator/Catalog、CapabilityExecutionResult/Constraints、重复 Candidate Port/Promotion、ResultPolicy/SupportAssessment、整个旧 composition/Expression/CircuitBreaker/Disposition、P4 Eval 与旧配置轴均删除；Eval 改为直接消费 `PortfolioTaskExecutor`。Slice 3 精确零引用门与旧 expression 配置扫描均无输出。Target + HTTP 组合 34 tests 通过；Backend 全量 990 tests、0 failure/error、20 environment-skipped；Postgres 真实 integration 因 Docker/Testcontainers 不可用维持 environment-skipped，Bundle/Postgres adapter same-release 与 deadline-before-I/O 由 deterministic test 覆盖。

### 2026-08-18 · Slice 4（完成）

General 已收敛为 `SemanticTask -> GeneralKnowledgeRequest -> GeneralKnowledgeGenerator -> GeneralSemanticResult -> GeneralPresentationComposer -> GeneralPresentation -> TaskArtifact` 单链。Request 直接携带 explanation topic 或 comparison subjects/dimensions、depth、audience、expected content version 与 absolute deadline；模型 Port 不再接收空 ConversationWindow、legacy Route 或自由文本 Depth/Audience 协议。Generator 只执行一次 provider 调用、strict decode 与完整 role/dimension validation；缺少必需结构、非法输出、超时或 Provider unavailable 均形成局部 Failed，不伪装 PARTIAL 或本地答案。

Cross-domain 已收敛为恰好一个 `GeneralSemanticResult` 与一个 `PortfolioSemanticResult` 的真实 fan-in。Executor 使用 Goal 中的精确 concept anchor 选择 definition/mechanism 和已验证 Portfolio statements；不做 substring 关系猜测，不接受任意多输入，不调用模型表达。`CrossDomainSemanticResult` 保存实际选中 statements/caveats/support，deterministic Presentation 分离通用原理、项目实例和关系；Portfolio source 只附着项目与关系 section，不扩散为 General 支持。

旧 `ConversationalModelPort`/summary/classify/generate/review/suggest、General Material 大字段/nullable pipeline、ConversationWindowManager/DynamicQuestionService/DraftValidator、substring RelationPolicy、Cross-domain Expression/Codec/Validator/config，以及对应 mock Provider Eval 兼容链均已删除。冻结零引用门无输出。Slice 4 target 14 tests、Target + HTTP/合同组合 21 tests 通过；Backend 全量 935 tests、0 failure/error、20 environment-skipped；Frontend 69 files、763 tests 与 production build 通过；backend 662 files 与 contracts 15 files privacy check 通过。进入 Slice 5 外部边界原子切换。

### 2026-08-19 · Slice 5（完成）

Backend 已建立 D-38 唯一 PublicAgentTurn/Projector、闭合 SECTIONED/RECOMMENDATION、Goal-first coverage/notices/source catalog/support 与 Recommendation golden；建立 typed Continuation、一次性 Clarification、不可扩权 Recommendation child context、HMAC request fingerprint、claim/replay/conflict/cancel terminal gate、加密 Public snapshot/state mutation codec、PostgreSQL Flyway V2 与 Memory/local store。生产 API 已切换为 `/api/agent/turns` 与 `/api/agent/conversations/current` 四条无版本资源，Bearer/no-store/error/Retry-After/conversation envelope 已由 target tests 冻结。

旧 `/api/v2/answers`、`/api/v2/conversation-context`、Migration Runtime/Service、ConversationAnswerResult/Response/Mapper、Completion Receipt、Context Facade/Resolver/MOST_RECENT、p3/p5 Context codecs、旧 DTO 与对应集成测试均已删除。Frontend 已接入 `agentTurnApi`、Bearer/sessionStorage、cancel/clear/clarification、Workspace/Thread 与唯一组件树，并删除 58 个旧协议/mapper/component/test 文件。Frontend 48 files、417/417 与 build 通过，生产源码联合零引用门无业务命中；Slice 5 Exit Gate 关闭。

### 2026-08-19 · Slice 6（完成）

Backend 已用 typed `StructuredModelTransport` 统一 Goal/General HTTPS JSON-mode/deadline 传输，删除重复 ChatCompletion DTO；Eval 的旧 HTTP answer client、P5 suite、Legacy benchmark adapter 与 degraded 轴已删除/改为明确 fallback；evaluation/selection benchmark 已通过实际 JAR listing 证明不进入生产包。旧 Selection service、旧 Context/Receipt、推荐状态副本、客户端 expectedContextType 与无主兼容入口均删除；新增 Turn module dependency test，Backend production 零引用门仅有 `P50Latency` 非阶段号误匹配。

最终联合门：Backend 879 tests、0 failure/error、5 个与 Docker 无关的条件 skip；PostgreSQL 16/pgvector Testcontainers、公共库 Flyway 与 Agent State Flyway/Repository 均实际执行。Frontend 48 test files、417/417，vue-tsc/Vite build、Spring Boot package、backend 598 files 与 contracts 15 files privacy check、Turn module dependency test、生产源码零引用门均通过。旧 Browser fixtures/mocks 已由最终 packaged-JAR E2E 原子替换，桌面与移动 Chromium 6/6 通过；真实 DeepSeek 应用 canary 返回 `ANSWER/COMPLETE`，并由安全诊断确认 `GOAL_INTERPRETATION`、`GENERAL_KNOWLEDGE` 两个 Provider operation 均成功。Answer 主代码由 505 files/42,678 LOC 收敛为 169 files/8,217 LOC；新增最终 `turn+infrastructure` 为 138 files/7,797 LOC，合计仍显著低于旧 Answer。Frontend Agent 生产 TS/Vue 由 75 files/19,940 LOC 收敛为 46 files/6,231 LOC，测试文件由 37 收敛为 18；旧最大兼容测试矩阵与旧 E2E 资产已删除。Slice 0—6 本地实现和最终联合验收全部完成；未 push、未部署。
