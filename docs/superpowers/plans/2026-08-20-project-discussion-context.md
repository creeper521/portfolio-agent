# Typed Project Discussion Context 实施计划
<!-- DOCUMENT_STATUS: ACTIVE -->

> **状态：** Prompt、STANDARD Routing 与 Project Discussion V4 已完成代码、确定性全量、PostgreSQL 和模型关闭 packaged-JAR 门；等待明确授权运行 PROJECT_DISCUSSION 真实 Provider + Browser lane 后关闭 A2-19。
> **批准设计：** docs/superpowers/specs/2026-08-20-project-discussion-context-design.md
> **关联缺陷：** docs/15-Agent 2.0真实交互问题清单与修复边界.md 中 A2-19
> **Guardian：** 已批准的 LEVEL_3。实施期间不再重复申请同一架构批准；真实 Provider、外部环境、提交和推送仍按各自边界单独授权。

## 目标

按固定顺序完成三段收敛：

1. 先完成既有“Prompt 外部化、语言与深度” Slice；
2. 用 AI closed proposal + 后端 closed validation 替换 STANDARD 自由文本中的本地 NLP 路由；
3. 用 ProjectDiscussionContext、Conversation active pointer、闭合 CONTINUE operation 和 V4 State 建立唯一项目讨论权威，覆盖卡片进入、讨论内自由文本、切换、刷新、退出和过期重建。

最终稳态必须保持：

- AI 只提出语义 route、候选 key 和 locked scope 内 Goal 参数；
- 后端验证候选成员关系、公开主体、权限、闭合集合、状态转换和 pointer generation；
- 一个 Goal Interpretation prompt、一个 General Knowledge prompt，不新增第三个 Prompt；
- 不新增短语表、Assistant 文本解析、第二状态权威、前端业务路由或旧 CONTINUE 兼容；
- 不持久化问题、ConversationWindow、Prompt、原始模型输出、聊天消息或私有内容。

## 当前工作树与所有权门

计划编写时，以下并行 Prompt Slice 文件存在未提交改动，实施者必须保留：

- AGENTS.md
- backend/src/main/java/com/portfolio/agent/infrastructure/model/SystemPromptCatalog.java
- backend/src/main/java/com/portfolio/agent/turn/capability/general/GeneralDraftValidator.java
- backend/src/main/java/com/portfolio/agent/turn/infrastructure/AgentCapabilityConfiguration.java
- backend/src/main/java/com/portfolio/agent/turn/infrastructure/model/GoalInterpretationAdapter.java
- backend/src/main/java/com/portfolio/agent/turn/infrastructure/model/OpenAiCompatibleGeneralKnowledgeAdapter.java
- backend/src/main/resources/prompts/*
- 对应 backend 测试、docs/00、docs/08、docs/11、Prompt spec/plan
- scripts/assert-live-general-answer-quality*.ps1
- scripts/documentation-check*.ps1、privacy-check*.ps1、verify-release.ps1

实施前必须运行：

    git status --short
    git diff -- backend/src/main/java/com/portfolio/agent/turn/infrastructure/model/GoalInterpretationAdapter.java
    git diff -- backend/src/main/java/com/portfolio/agent/turn/infrastructure/AgentCapabilityConfiguration.java
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/agent-architecture-status.ps1

只有并行 Agent 已明确完成、提交或移交这些文件后，才能修改重叠路径。禁止 reset、restore、checkout、覆盖、暂存或提交其他 Agent 的改动。若所有权仍未释放，只继续读代码和补充不重叠测试设计，不进入生产修改。

## Authority 与 Replacement Manifest

| Slice | 旧权威 | 目标权威 | 必须同期删除 | 完成条件 |
|---|---|---|---|---|
| 0：Prompt prerequisite | Java 内嵌 prompt 与宽松 EXPLANATION validator | 两个 classpath prompt + SystemPromptCatalog + 严格 validator | 死 prompt 文件与两个 Java prompt 常量 | 既有 Prompt Plan 的确定性门完成，重叠文件释放 |
| 1：STANDARD Semantic Routing | MinimalGoalFallback 的推荐、数量、约束、比较、alias 与指代 NLP；Provider 失败后的自然语言 fallback | GoalInterpretationPort → SemanticRouteProposal → SemanticRouteValidator → GoalBoundaryPolicy | MinimalGoalFallback 及其规则测试；旧 GOALS/CLARIFICATION model root shape；失败后 Goal fallback | 新路径进入唯一生产入口、freeTextSemanticRouting 投影闭合、旧规则零引用、受影响全门通过 |
| 2：Project Discussion V4 | 无 operation 的 CONTINUE；Fact/Comparison ContinuationContext；Goal continuation；Recommendation 固定 refine 分支 | RecommendationContext → backend action → closed CONTINUE → ProjectDiscussionContext → active pointer → DISCUSSION route validator → existing Plan/Engine | 旧 CONTINUE reader/fixture、Fact/Comparison Context、Goal continuation、PORTFOLIO_REFINE_RECOMMENDATION 全链 | State/API/Frontend 同期迁移，V4、PostgreSQL、packaged Browser、真实 Provider 原始路径通过 |

Slice 1 和 Slice 2 之间不得反转。Slice 2 可以先写不注册到生产的 RED fixtures，但不得在 Slice 1 完成前接入 Project Discussion 生产入口。

## Slice 0：完成并验收 Prompt prerequisite

**权威计划：**

- docs/superpowers/plans/2026-08-20-general-answer-language-and-depth-prompt.md

**本计划不重复实现：**

- SystemPromptCatalog；
- goal-interpretation-system.txt 与 general-knowledge-system.txt 的创建；
- 固定简体中文、三档 depth、EXPLANATION 角色与顺序收口；
- Prompt 专用 canary。

- [x] Prompt Slice 已完成自动行为门并明确移交共享文件所有权；独立浏览器语义覆盖继续由 Prompt Plan 跟踪。
- [x] 已读取 Prompt Slice diff 和验证证据，后续只做增量修改，不覆盖未提交文件。
- [x] `goal-interpretation-system.txt` 是 STANDARD/DISCUSSION schema 的唯一 Goal prompt 资源。
- [x] Prompt Plan 的确定性测试、JAR 资源门和真实 Provider 自动矩阵已运行。
- [x] 真实 Provider 已授权并运行，无需复制 `AUTHORIZATION` deferred item。
- [x] 已知基线为 `e51d085`；其后的 Prompt/Semantic Routing 改动保持未提交，未获得提交授权时不改写 Git 历史。

**Exit Gate：**

- 两个 prompt 资源可从最终 JAR 加载；
- Goal/General adapter 均从同一 Catalog 注入；
- Prompt Slice 的未提交所有权已释放；
- 本计划后续不创建 discussion-system.txt 或第二 Goal adapter。

---

## Replacement Slice 1：STANDARD Free-text Semantic Routing

### Task 1.1：先冻结 closed proposal 与 validator 的 RED 合同

**新增生产文件：**

- backend/src/main/java/com/portfolio/agent/turn/planning/SemanticRouteProposal.java
- backend/src/main/java/com/portfolio/agent/turn/planning/SemanticRouteValidator.java
- backend/src/main/java/com/portfolio/agent/turn/planning/SafeConversationalFastPath.java

**修改生产文件：**

- backend/src/main/java/com/portfolio/agent/turn/planning/GoalInterpretationInput.java
- backend/src/main/java/com/portfolio/agent/turn/planning/GoalInterpretationInputFactory.java
- backend/src/main/java/com/portfolio/agent/turn/planning/GoalInterpretationResult.java
- backend/src/main/java/com/portfolio/agent/turn/planning/GoalProposalCodec.java
- backend/src/main/java/com/portfolio/agent/turn/planning/GoalResolver.java
- backend/src/main/java/com/portfolio/agent/turn/planning/ResolvedGoalSet.java
- backend/src/main/java/com/portfolio/agent/turn/infrastructure/model/GoalInterpretationAdapter.java
- backend/src/main/resources/prompts/goal-interpretation-system.txt
- backend/src/main/java/com/portfolio/agent/turn/infrastructure/AgentCapabilityConfiguration.java

**测试文件：**

- 新增 backend/src/test/java/com/portfolio/agent/turn/planning/SemanticRouteValidatorTest.java
- 修改 backend/src/test/java/com/portfolio/agent/turn/planning/GoalProposalCodecTest.java
- 修改 backend/src/test/java/com/portfolio/agent/turn/planning/GoalInterpretationInputFactoryTest.java
- 修改 backend/src/test/java/com/portfolio/agent/turn/planning/GoalResolverTest.java
- 修改 backend/src/test/java/com/portfolio/agent/turn/infrastructure/model/GoalInterpretationAdapterTest.java
- 修改 backend/src/test/java/com/portfolio/agent/turn/planning/TurnInputSafetyReplacementTest.java
- 修改 backend/src/test/java/com/portfolio/agent/turn/architecture/SemanticRoutingArchitectureTest.java

- [ ] 先写 Codec RED：模型根结果只接受 CONVERSATIONAL 或 closed SEMANTIC_ROUTE；不再接受旧 GOALS/CLARIFICATION root shape、未知 route、未知字段、模型输出 handle/resultItemId/Token。
- [ ] 冻结 STANDARD allowed routes：STANDARD_GOAL、ENTER_RECOMMENDED_RESULT、NEEDS_CLARIFICATION；没有 typed Recommendation hint 时 ENTER_RECOMMENDED_RESULT 必须非法。
- [ ] 冻结 Proposal shape：route、可空 candidateKey、可空 closed Goal；route 与字段组合必须互斥且完备。
- [ ] 冻结后端校验：GoalKind、requestedSize 1—5、Facet、Output、Depth、dimension/constraint closed names、公开主体和 Goal shape均验证；不读取 confidence。
- [ ] 冻结 conversational fast path：只覆盖极小安全问候/致谢并且只能产生 CONVERSATIONAL；不得包含项目、推荐、数量、比较、约束、alias 或指代解析。
- [ ] 运行 focused tests，确认失败原因来自尚未实现新 schema/validator，而不是 fixture 或并行 Prompt 改动。

运行：

    mvn.cmd -f backend/pom.xml -Dtest=SemanticRouteValidatorTest,GoalProposalCodecTest,GoalInterpretationInputFactoryTest,GoalResolverTest,TurnInputSafetyReplacementTest,SemanticRoutingArchitectureTest,GoalInterpretationAdapterTest test

预期：RED，且失败点对应 closed route 合同。

### Task 1.2：实现单一 STANDARD AI seam

- [ ] GoalInterpretationInput 增加可信 interpretationMode=STANDARD 和 closed allowedRoutes；STANDARD 不伪造 discussion state。
- [ ] Goal prompt 在既有 goal-interpretation-system.txt 中替换旧 GOALS/CLARIFICATION schema 为 closed route schema；保留 CONVERSATIONAL 根结果和 Prompt Slice 的语言/depth/防注入条款。
- [ ] GoalInterpretationAdapter 仍只调用一次既有 transport；不新增模型调用、重试、provider abstraction 或第三个 prompt。
- [ ] GoalProposalCodec 严格拒绝未知字段、未知 route、越界 candidateKey、非法 Goal shape；解析结果进入 SemanticRouteProposal。
- [ ] SemanticRouteValidator 只消费 typed public subject catalog、allowed routes 和候选映射；AI 不能扩大主体、候选或约束集合。
- [ ] GoalResolver 的顺序固定为：typed PRESET/page subject → SafeConversationalFastPath → AI proposal → validator → GoalBoundaryPolicy。
- [ ] Provider disabled、超时、invalid JSON 或 validator 拒绝时返回稳定 SEMANTIC_ROUTING_UNAVAILABLE；不再调用本地自然语言 Goal fallback。
- [ ] Clarification CHOICE/binding 的确定性恢复保持；自由 TEXT 的 AI normalization 仍不进入本 Slice。

### Task 1.3：删除本地 NLP authority 与 refine 前置耦合

**删除：**

- backend/src/main/java/com/portfolio/agent/turn/planning/MinimalGoalFallback.java

**修改/删除旧断言：**

- backend/src/test/java/com/portfolio/agent/turn/planning/DeterministicConversationBoundaryTest.java
- backend/src/test/java/com/portfolio/agent/turn/planning/GoalResolverTest.java 中所有本地推荐数量、约束、比较、alias、指代和 Provider-failure fallback 断言

- [ ] 删除推荐/项目关键词判断。
- [ ] 删除阿拉伯数字、中文数字、数量范围正则。
- [ ] 删除推荐否定、约束、比较与指代短语。
- [ ] 删除 alias contains/negation 等自然语言扫描。
- [ ] 删除 Provider 失败后的 Goal fallback。
- [ ] 用模型 proposal fixture 覆盖推荐数量、比较、约束和主体选择；后端测试只断言 closed 值及范围验证，不把中文表达写成生产规则。
- [ ] Architecture test 证明 production planning source 不再依赖 Pattern、数字 parser 或 phrase table 来形成 Goal。

零引用：

    rg -n "MinimalGoalFallback|tryResolveBeforeProvider|parseChinese|negatedAlias|recommendationCount" backend/src/main/java backend/src/test/java

预期：生产源码零引用；测试只允许迁移说明中出现名称，最终也应清理。

### Task 1.4：投影 freeTextSemanticRouting capability

**修改后端：**

- backend/src/main/java/com/portfolio/agent/portfolio/dto/response/AgentAvailabilityResponse.java
- backend/src/main/java/com/portfolio/agent/portfolio/controller/PublicContentController.java
- backend/src/test/java/com/portfolio/agent/portfolio/controller/PublicContentControllerAvailabilityTest.java
- backend/src/main/java/com/portfolio/agent/turn/api/response/AgentApiErrorResponse.java
- backend/src/main/java/com/portfolio/agent/turn/api/AgentTurnController.java
- backend/src/test/java/com/portfolio/agent/turn/api/AgentTurnClosedContractIntegrationTest.java

**修改前端：**

- frontend/src/features/public-content/model/publicContentTypes.ts
- frontend/src/features/public-content/data/previewPublicContent.ts
- frontend/src/features/portfolio/api/portfolioApi.ts
- frontend/src/features/portfolio/api/portfolioApi.test.ts
- frontend/src/pages/AgentPage.vue
- frontend/src/pages/AgentPage.test.ts
- frontend/src/features/agent/components/AgentWorkspace.vue
- frontend/src/features/agent/components/AgentWorkspace.test.ts

- [ ] AgentAvailabilityResponse 增加 freeTextSemanticRouting=AVAILABLE|DISABLED；status 继续只表示 Turn/State 整体是否可用。
- [ ] readiness 只根据 TURN_INTERPRETATION 配置和启动完整性投影，不尝试反映瞬时网络健康。
- [ ] DISABLED 时 composer 禁用并显示中性说明；PRESET 与后续 backend-owned deterministic action 不被隐藏。
- [ ] 缺字段、未知值和旧损坏响应在前端 fail-closed 为 DISABLED。
- [ ] 直接 API 提交自由文本时返回稳定 SEMANTIC_ROUTING_UNAVAILABLE；瞬时 Provider 失败仍是本 Turn 的失败，不永久改变 capability。

### Task 1.5：完成 Slice 1 的替代门、文档和条件式提交

**修改：**

- contracts/agent-turn/scenarios/turn-interaction.json
- contracts/agent-turn/scenarios/security-adversarial.json
- contracts/agent-turn/scenarios/general-synthesis.json
- docs/00-文档状态索引.md
- docs/08-当前实现状态.md
- docs/11-项目演进日志.md
- docs/agent-architecture-status.json
- scripts/documentation-check.ps1
- scripts/documentation-check.test.ps1
- scripts/privacy-check.ps1
- scripts/privacy-check.test.ps1
- scripts/verify-release.ps1

- [ ] 在生产切换开始时把 architecture overallStatus 设为 IN_PROGRESS；activeAuthorities 在新入口真正接入前仍描述当前生产权威。
- [ ] Contract scenarios 覆盖合法 STANDARD_GOAL、合法 CONVERSATIONAL、非法主体、非法 route、模型关闭和 Provider 失败无 NLP fallback。
- [ ] 运行 focused backend/frontend tests。
- [ ] 运行生产源码零引用、code quality、privacy、documentation、architecture gate。
- [ ] 运行 backend 全量、frontend 全量/check/build 和 clean package。
- [ ] 更新 docs/08 与 docs/11，只记录已验证的当前行为；A2-19 此时仍保留。
- [ ] 仅在明确获得提交授权后，创建一个中文小提交；不得捎带并行 Prompt 未归属改动。

建议提交：

    git commit -m "refactor(agent): 收口自由文本语义路由"

Slice 1 Exit Gate：

- 自由文本只有一个 Goal Interpretation AI seam；
- AI 提出 closed route/Goal，后端验证 closed shape；
- MinimalGoalFallback 与全部开放语义 NLP 规则物理删除；
- Provider failure 不形成 Goal fallback；
- freeTextSemanticRouting 投影与前端禁用逻辑闭合；
- Prompt 仍恰好两个；
- Slice 1 全量门通过后才进入 Slice 2。

---

## Replacement Slice 2：Project Discussion Context V4

### Task 2.1：以 RED 冻结 Command、Action 与共享合同

**修改后端 Command：**

- backend/src/main/java/com/portfolio/agent/turn/api/request/AgentTurnRequest.java
- backend/src/main/java/com/portfolio/agent/turn/api/request/AgentTurnRequestMapper.java
- backend/src/main/java/com/portfolio/agent/turn/lifecycle/AgentTurnCommand.java
- backend/src/test/java/com/portfolio/agent/turn/api/request/AgentTurnRequestValidationTest.java
- backend/src/test/java/com/portfolio/agent/turn/api/request/AgentTurnRequestMapperTest.java
- backend/src/test/java/com/portfolio/agent/turn/api/AgentTurnControllerContractTest.java

**修改共享合同：**

- contracts/agent-turn/fixtures/answer-complete.json
- contracts/agent-turn/fixtures/capability-unavailable.json
- contracts/agent-turn/scenarios/lifecycle-state.json
- contracts/agent-turn/scenarios/public-contract.json
- contracts/agent-turn/scenarios/security-adversarial.json
- contracts/agent-turn/scenarios/turn-interaction.json
- 新增 contracts/agent-turn/fixtures/conversation-active-discussion.json
- 新增 contracts/agent-turn/fixtures/conversation-expired-discussion.json
- 新增 contracts/agent-turn/fixtures/discussion-context-error.json

- [ ] ASK 增加可选 referenceContextHandle，仅作为不可信解释提示；PRESET 不允许携带。
- [ ] CONTINUE operation 必填且闭合为 ENTER_RESULT、ROUTE_IN_CONTEXT、EXIT_CONTEXT、REENTER_SUBJECT。
- [ ] 每个 operation 使用互斥 shape：ENTER_RESULT 仅 handle+item；ROUTE_IN_CONTEXT 仅 handle+text；EXIT_CONTEXT 仅 handle；REENTER_SUBJECT 仅公开 PROJECT subject。
- [ ] 缺 operation 的旧 CONTINUE、旧 handle+item+text shape、额外字段和 operation/字段错配全部 RED。
- [ ] 不提供 optional operation、默认 operation、旧 reader 或兼容 fixture。
- [ ] Conversation Summary fixture 冻结 ACTIVE/EXPIRED 的 backend-owned actions；公共错误冻结 DISCUSSION_* 稳定码。

### Task 2.2：建立 typed Context、clarification template 与 V4 codec

**新增：**

- backend/src/main/java/com/portfolio/agent/turn/continuation/ProjectDiscussionContext.java
- backend/src/main/java/com/portfolio/agent/turn/continuation/ActiveDiscussionPointer.java
- backend/src/main/java/com/portfolio/agent/turn/continuation/DiscussionStateMutation.java
- backend/src/main/java/com/portfolio/agent/turn/planning/ClarificationResumeTemplate.java
- backend/src/main/java/com/portfolio/agent/turn/planning/DiscussionSelectionTemplate.java
- backend/src/main/resources/db/context/V4__project_discussion_context.sql
- backend/src/test/java/com/portfolio/agent/turn/continuation/ProjectDiscussionContextTest.java
- backend/src/test/java/com/portfolio/agent/turn/continuation/DiscussionSelectionTemplateTest.java

**修改：**

- backend/src/main/java/com/portfolio/agent/turn/continuation/ContinuationContext.java
- backend/src/main/java/com/portfolio/agent/turn/continuation/ClarificationStore.java
- backend/src/main/java/com/portfolio/agent/turn/planning/BlockedGoalTemplate.java
- backend/src/main/java/com/portfolio/agent/turn/state/postgres/AgentStatePayloadCodec.java
- backend/src/test/java/com/portfolio/agent/turn/continuation/BlockedGoalTemplateTest.java
- backend/src/test/java/com/portfolio/agent/turn/continuation/ClarificationChallengeStoreTest.java
- backend/src/test/java/com/portfolio/agent/turn/state/postgres/AgentStatePayloadCodecTest.java

- [ ] ProjectDiscussionContext 只保存 handle、conversationId、releaseId、projectId、最多五个 switch candidate IDs、startedAt、expiresAt 和可空 sourceRecommendationHandle。
- [ ] Context 不保存 label、summary、问题、窗口、Prompt、模型输出、Goal、Plan、Task、Result 或消息。
- [ ] ActiveDiscussionPointer 只保存 handle、projectId、contextExpiresAt；不新增 ACTIVE/EXPIRED 持久化枚举。
- [ ] ClarificationStore.Record 从单一 BlockedGoalTemplate 改为 sealed ClarificationResumeTemplate，允许 BlockedGoalTemplate 或 DiscussionSelectionTemplate；两种模板字段与恢复路径严格分离。
- [ ] DiscussionSelectionTemplate 只保存 Recommendation context handle 和实际允许 result item IDs，不保存输入文本。
- [ ] V4 为 conversation_session 增加 all-null/all-present 的 pointer 列与约束；handle 作为 generation，不新增第二 session 表或双写。
- [ ] V4 不迁移短期旧 payload，不增加兼容 view/reader；旧 Context 可失效。
- [ ] Codec 的正反例覆盖 unknown subtype、oversized candidate set、release/conversation mismatch、加密完整性和隐私字段扫描。

### Task 2.3：扩展 Session/State 原子 settlement authority

**修改：**

- backend/src/main/java/com/portfolio/agent/turn/continuation/ConversationSessionStore.java
- backend/src/main/java/com/portfolio/agent/turn/continuation/InMemoryConversationSessionStore.java
- backend/src/main/java/com/portfolio/agent/turn/state/postgres/JdbcConversationSessionStore.java
- backend/src/main/java/com/portfolio/agent/turn/lifecycle/AgentStateStore.java
- backend/src/main/java/com/portfolio/agent/turn/lifecycle/TurnExecutionStore.java
- backend/src/main/java/com/portfolio/agent/turn/lifecycle/TurnExecutionRecord.java
- backend/src/main/java/com/portfolio/agent/turn/state/memory/InMemoryTurnExecutionStore.java
- backend/src/main/java/com/portfolio/agent/turn/state/postgres/JdbcAgentStateStore.java
- backend/src/main/java/com/portfolio/agent/turn/state/postgres/AgentStateCleanupJob.java
- backend/src/test/java/com/portfolio/agent/turn/lifecycle/TurnExecutionStoreContractTest.java
- backend/src/test/java/com/portfolio/agent/turn/state/postgres/JdbcAgentStateStoreIntegrationTest.java
- backend/src/test/java/com/portfolio/agent/turn/state/postgres/AgentStateCleanupIntegrationTest.java
- backend/src/test/java/com/portfolio/agent/turn/state/postgres/configuration/ConversationContextDatabaseConfigurationTest.java

- [ ] TurnExecutionStore.complete 同时接收 expected pointer generation 与 DiscussionStateMutation；PublicTurn、Context insert 和 pointer update 是一个 terminal transaction。
- [ ] ENTER/REENTER/SWITCH 只有新 Context 与 PublicTurn 都成功时替换 pointer；失败保留旧 pointer。
- [ ] EXIT 对 active/expired pointer 原子清空，不调用模型。
- [ ] ROUTE_IN_CONTEXT settlement 用 expected handle 做 generation guard；切换/退出/clear 先完成时，旧结果不得提交。
- [ ] 同 handle 的两个并发只读请求可以分别结算，均不续期、不修改 pointer。
- [ ] clear conversation 撤销 session 并删除/过期化 Context、Challenge 和 pointer。
- [ ] cleanup 删除过期 Context，不把旧 pointer 恢复 active；Summary 可从 pointer time 派生 EXPIRED。
- [ ] Memory/PostgreSQL 用同一 contract test 证明 parity；所有 DB 操作仍受 TurnDeadline 和 operation cap。

### Task 2.4：实现唯一 Project Discussion transition authority

**新增：**

- backend/src/main/java/com/portfolio/agent/turn/continuation/ProjectDiscussionCoordinator.java
- backend/src/main/java/com/portfolio/agent/turn/planning/DiscussionGoalFactory.java
- backend/src/test/java/com/portfolio/agent/turn/continuation/ProjectDiscussionCoordinatorTest.java
- backend/src/test/java/com/portfolio/agent/turn/planning/DiscussionGoalFactoryTest.java

**修改：**

- backend/src/main/java/com/portfolio/agent/turn/continuation/ContinuationResolver.java
- backend/src/main/java/com/portfolio/agent/turn/continuation/ContextMutationPlanner.java
- backend/src/main/java/com/portfolio/agent/turn/planning/GoalInterpretationInput.java
- backend/src/main/java/com/portfolio/agent/turn/planning/GoalInterpretationInputFactory.java
- backend/src/main/java/com/portfolio/agent/turn/planning/SemanticRouteProposal.java
- backend/src/main/java/com/portfolio/agent/turn/planning/SemanticRouteValidator.java
- backend/src/main/java/com/portfolio/agent/turn/planning/GoalResolver.java
- backend/src/main/resources/prompts/goal-interpretation-system.txt
- backend/src/main/java/com/portfolio/agent/turn/lifecycle/AgentTurnLifecycleService.java
- backend/src/test/java/com/portfolio/agent/turn/continuation/ContinuationResolverTest.java
- backend/src/test/java/com/portfolio/agent/turn/continuation/RecommendationChildContextTest.java
- backend/src/test/java/com/portfolio/agent/turn/lifecycle/AgentTurnLifecycleContinuationTest.java
- backend/src/test/java/com/portfolio/agent/turn/lifecycle/AgentTurnLifecycleClarificationRecoveryTest.java
- backend/src/test/java/com/portfolio/agent/turn/lifecycle/AgentTurnLifecycleSettlementFailureTest.java
- backend/src/test/java/com/portfolio/agent/turn/lifecycle/AgentTurnLifecycleReplayTest.java
- backend/src/test/java/com/portfolio/agent/turn/lifecycle/AgentTurnLifecycleDeadlineTest.java
- backend/src/test/java/com/portfolio/agent/turn/planning/GoalResolverTest.java
- backend/src/test/java/com/portfolio/agent/turn/planning/SemanticRouteValidatorTest.java

- [ ] ProjectDiscussionCoordinator 是 ENTER/SWITCH/EXIT/REENTER 的唯一状态转换入口；显式 backend action 不调用模型。
- [ ] ENTER_RESULT 验证 Conversation、release、TTL 和 selected result 成员关系，复制 bounded switch candidates，创建新 Context，并执行默认 OVERVIEW/RESPONSIBILITY/SOLUTION/VERIFICATION/STATUS 概览 Goal。
- [ ] 点击历史其他 Recommendation item 仍走同一 ENTER_RESULT authority，成功后直接切换；不二次确认。
- [ ] REENTER_SUBJECT 验证项目仍属于当前公开 release，创建新 handle 且候选集合仅含当前项目；不复活旧 handle/requestId。
- [ ] ACTIVE mode 允许 CONTINUE_CURRENT_PROJECT、START_NEW_TOPIC、SWITCH_PROJECT、NEEDS_CLARIFICATION；EXPIRED 只允许 REENTER_PROJECT、START_NEW_TOPIC、NEEDS_CLARIFICATION。
- [ ] locked project 由服务端注入；模型只能提出 Facet/Output、稳定 concept anchor 与 closed Goal 参数。
- [ ] 通用概念在 ACTIVE 中形成 APPLY_GENERAL_CONCEPT_TO_PORTFOLIO，不自动退出。
- [ ] active/expired pointer 下的 ASK+FREE_TEXT 由服务端强制按 DISCUSSION mode；忽略前端 ASK 的路由暗示。
- [ ] CONVERSATIONAL 不创建 Goal、不修改、不退出、不续期 pointer。
- [ ] Provider/Codec/validator failure 不修改 pointer，投影 DISCUSSION_INTERPRETATION_UNAVAILABLE 与 backend retry/new-topic action。
- [ ] DISCUSSION prompt 只扩展现有 goal-interpretation-system.txt 的 interpretationMode、typed state、candidateKey 与 allowed routes；不创建第三个 prompt。

### Task 2.5：承接最近 Recommendation 的 typed selection

**修改：**

- backend/src/main/java/com/portfolio/agent/turn/api/request/AgentTurnRequest.java
- backend/src/main/java/com/portfolio/agent/turn/lifecycle/AgentTurnCommand.java
- backend/src/main/java/com/portfolio/agent/turn/planning/GoalInterpretationInputFactory.java
- backend/src/main/java/com/portfolio/agent/turn/planning/SemanticRouteValidator.java
- backend/src/main/java/com/portfolio/agent/turn/lifecycle/AgentTurnLifecycleService.java
- backend/src/main/java/com/portfolio/agent/turn/continuation/ClarificationStore.java
- backend/src/test/java/com/portfolio/agent/turn/lifecycle/AgentTurnLifecycleClarificationRecoveryTest.java
- backend/src/test/java/com/portfolio/agent/turn/lifecycle/AgentTurnLifecycleContinuationTest.java

- [ ] referenceContextHandle 只有在同 Conversation、同 release、未过期且为 Recommendation Context 时才构造候选；无效提示静默忽略并走普通 STANDARD，不泄露状态。
- [ ] 给模型的候选只含临时 C1…C5、公开 label/aliases 和 allowed routes；不含 handle、resultItemId 或候选外项目。
- [ ] AI 明确提出 ENTER 且 candidateKey 唯一合法时直接进入；单一 result 也不展示单项 CHOICE。
- [ ] candidate 缺失、零命中、多命中或 NEEDS_CLARIFICATION 时生成一字段 CHOICE，选项严格等于 selectedResults。
- [ ] Choice 恢复通过 DiscussionSelectionTemplate 汇入同一 ENTER_RESULT authority。
- [ ] route=STANDARD_GOAL 时完全忽略 recommendation hint。

### Task 2.6：替换 Public projection 与 Conversation Summary

**修改/新增后端：**

- backend/src/main/java/com/portfolio/agent/turn/continuation/ContinuationReference.java
- backend/src/main/java/com/portfolio/agent/turn/projection/SuggestedAction.java
- backend/src/main/java/com/portfolio/agent/turn/projection/PublicPresentation.java
- backend/src/main/java/com/portfolio/agent/turn/projection/AnswerGoalResult.java
- backend/src/main/java/com/portfolio/agent/turn/projection/PublicAgentTurn.java
- backend/src/main/java/com/portfolio/agent/turn/projection/PublicAgentTurnProjector.java
- backend/src/main/java/com/portfolio/agent/turn/api/AgentConversationController.java
- 新增 backend/src/main/java/com/portfolio/agent/turn/api/response/ConversationSummaryResponse.java
- backend/src/test/java/com/portfolio/agent/turn/projection/PublicAgentTurnProjectorTest.java
- backend/src/test/java/com/portfolio/agent/turn/projection/PublicAgentTurnInvariantTest.java
- backend/src/test/java/com/portfolio/agent/turn/contract/PublicAgentTurnGoldenFixtureStructureTest.java
- backend/src/test/java/com/portfolio/agent/turn/api/AgentConversationControllerTest.java

- [ ] Recommendation item 增加完整 backend-owned discussionAction；前端不得组合 handle/item/subject。
- [ ] AnswerGoalResult 删除 Goal 级 continuation 字段；Recommendation action 是唯一推荐进入入口。
- [ ] ACTIVE Summary 返回 public subject、真实 expiresAt、routeContinuation 和 exitAction。
- [ ] EXPIRED Summary 返回受限 routeContinuation、reenterAction 和 newTopicAction；状态由 pointer time/session expiry/current release 派生。
- [ ] Subject label/route 每次从当前 reviewed public content 派生；State 不保存。
- [ ] DISCUSSION_CONTEXT_EXPIRED、UNAVAILABLE、MISMATCH、SUBJECT_UNAVAILABLE、INTERPRETATION_UNAVAILABLE 均使用稳定公开动作且不泄露 handle 所属。
- [ ] PublicTurn 和错误响应继续 no-store。

### Task 2.7：同期删除退休 Continuation/Refine authority

**修改/删除：**

- backend/src/main/java/com/portfolio/agent/turn/continuation/ContinuationContext.java 中 PortfolioFact、PortfolioComparison subtype
- backend/src/main/java/com/portfolio/agent/turn/continuation/ContextMutationPlanner.java 中 Fact/Comparison context creation
- backend/src/main/java/com/portfolio/agent/turn/lifecycle/AgentTurnLifecycleService.java 中 continuationProposal 与固定 refine 分支
- backend/src/main/java/com/portfolio/agent/turn/planning/GoalKind.java 中 PORTFOLIO_REFINE_RECOMMENDATION
- backend/src/main/java/com/portfolio/agent/turn/planning/UserGoalProposal.java 中 PortfolioRefineParameters
- backend/src/main/java/com/portfolio/agent/turn/planning/GoalProposalCodec.java 中 refine codec
- backend/src/main/java/com/portfolio/agent/turn/planning/BlockedGoalTemplate.java 中 refine 分支
- backend/src/main/java/com/portfolio/agent/turn/planning/SemanticPlanCompiler.java 中 refine task
- backend/src/main/java/com/portfolio/agent/turn/planning/SemanticTask.java 中 refine task type
- backend/src/main/java/com/portfolio/agent/turn/capability/portfolio/PortfolioInvocationFactory.java 中 refine invocation
- backend/src/main/java/com/portfolio/agent/turn/capability/portfolio/semantic/PortfolioSemanticResultFactory.java 中 refine result
- 删除/改写对应 RecommendationChildContextTest、ContinuationResolverTest、GoalProposalCodecTest、SemanticPlanCompilerTest、PortfolioInvocationFactoryTest、PortfolioSemanticResultFactoryTest
- 删除旧 contracts fixtures/frontend mapper 中 Goal continuation 消费

- [ ] 普通 Fact/Comparison 结果不再创建 Context。
- [ ] RecommendationContext 只证明 result membership；ProjectDiscussionContext 只证明当前 locked project。
- [ ] 讨论内 Goal 不创建普通结果 Context。
- [ ] PORTFOLIO_REFINE_RECOMMENDATION 生产消费者为零后，同 Slice 删除 enum、parameters、task、invocation、result 和测试。
- [ ] 不保留 deprecated class、兼容 serializer、optional operation、feature flag 或双栈。

零引用：

    rg -n "PORTFOLIO_REFINE_RECOMMENDATION|PortfolioRefineParameters|ContinuationContext\\.PortfolioFact|ContinuationContext\\.PortfolioComparison|continuationProposal" backend/src/main/java backend/src/test/java frontend/src contracts
    rg -n "getContinuation\\(|\"continuation\"\\s*:" backend/src/main/java/com/portfolio/agent/turn/projection frontend/src/features/agent contracts/agent-turn

预期：第一条零命中；第二条只允许 SuggestedAction 的 closed continuation，不允许 AnswerGoalResult continuation。

### Task 2.8：迁移 Frontend typed contract、session 和交互

**修改类型/API：**

- frontend/src/features/agent/api/agentTurnApi.ts
- frontend/src/features/agent/api/agentTurnApi.test.ts
- frontend/src/features/agent/model/publicAgentTurn.ts
- frontend/src/features/agent/model/publicAgentTurnMapper.ts
- frontend/src/features/agent/model/publicAgentTurnMapper.test.ts
- frontend/src/features/agent/model/publicAgentTurnGoldenFixtures.test.ts
- frontend/src/features/agent/model/sessionTypes.ts
- frontend/src/features/agent/composables/useLocalSessions.ts
- frontend/src/features/agent/composables/useLocalSessions.test.ts
- frontend/src/features/agent/composables/useConversationResume.ts
- frontend/src/features/agent/composables/useConversationResume.test.ts

**修改 UI：**

- frontend/src/features/agent/components/RecommendationPresentationView.vue
- frontend/src/features/agent/components/RecommendationPresentationView.test.ts
- frontend/src/features/agent/components/GoalResultView.vue
- frontend/src/features/agent/components/GoalResultView.test.ts
- frontend/src/features/agent/components/AnswerTurnView.vue
- frontend/src/features/agent/components/AnswerTurnView.test.ts
- frontend/src/features/agent/components/PublicAgentTurnMessage.vue
- frontend/src/features/agent/components/PublicAgentTurnMessage.test.ts
- frontend/src/features/agent/components/SuggestedActionRow.vue
- frontend/src/features/agent/components/SuggestedActionRow.test.ts
- frontend/src/features/agent/components/AgentWorkspace.vue
- frontend/src/features/agent/components/AgentWorkspace.test.ts
- frontend/src/features/agent/components/ConversationThread.vue
- frontend/src/features/agent/components/ConversationThread.test.ts
- frontend/src/pages/AgentPage.vue
- frontend/src/pages/AgentPage.test.ts

- [ ] Mapper 严格解析 closed CONTINUE operation、discussionAction、activeDiscussion Summary 和 DISCUSSION_* actions；未知字段/operation fail-closed。
- [ ] Recommendation 卡只渲染并转发 discussionAction；没有 action 不显示入口。
- [ ] 当前会话最后一条可见 Recommendation 的全部可操作 item 共享同一 backend handle 时，ASK 机械附带 referenceContextHandle；不从文本/位置/label 重建。
- [ ] active/expired focus 的自由文本发送 ROUTE_IN_CONTEXT；active pointer 存在时不发送普通 ASK。
- [ ] 每个 AgentSession 增加内存 activeDiscussion；来源仅 PublicTurn projection 或 current conversation Summary。
- [ ] active focus 显示项目、真实剩余 TTL 和退出；expired 显示重新进入/开始新话题并允许受限文本 route。
- [ ] 点击历史另一项目 action 直接切换；失败保持原 focus。
- [ ] 刷新只恢复 token 与 typed focus，不恢复消息；新会话不继承 focus。
- [ ] handle、resultItemId、问题、消息和 Context 不进入 URL、history、localStorage 或 sessionStorage；sessionStorage 仍只有当前 tab 的 ResumeToken。
- [ ] pending、retry、cancel、failure、clarificationConsumed 和 discussion state 按 session 隔离。
- [ ] freeTextSemanticRouting=DISABLED 时只禁用 composer，不禁用 PRESET/discussion/exit/reenter action。

### Task 2.9：Backend/Frontend focused GREEN

Backend：

    mvn.cmd -f backend/pom.xml -Dtest=AgentTurnRequestValidationTest,AgentTurnRequestMapperTest,SemanticRouteValidatorTest,ProjectDiscussionContextTest,DiscussionSelectionTemplateTest,ProjectDiscussionCoordinatorTest,DiscussionGoalFactoryTest,AgentTurnLifecycleContinuationTest,AgentTurnLifecycleClarificationRecoveryTest,AgentTurnLifecycleSettlementFailureTest,AgentConversationControllerTest,PublicAgentTurnProjectorTest,AgentStatePayloadCodecTest,TurnExecutionStoreContractTest test

Frontend：

    npm.cmd --prefix frontend test -- --run src/features/agent/api/agentTurnApi.test.ts src/features/agent/model/publicAgentTurnMapper.test.ts src/features/agent/composables/useLocalSessions.test.ts src/features/agent/composables/useConversationResume.test.ts src/features/agent/components/RecommendationPresentationView.test.ts src/features/agent/components/AgentWorkspace.test.ts src/pages/AgentPage.test.ts

必须覆盖：

- recommendation membership、single candidate direct enter、bounded CHOICE；
- locked subject、switch candidate inheritance、no scope expansion；
- ASK+FREE_TEXT discussion override；
- active/expired routes；
- enter/switch/exit/clear atomicity 与 pointer generation race；
- same-handle concurrent read-only settlement；
- no TTL extension；
- wrong token/conversation/release/item fail-closed；
- Memory/PostgreSQL parity；
- refresh focus without messages；
- multi-session isolation；
- model disabled deterministic actions。

### Task 2.10：PostgreSQL、packaged Browser 与真实 Provider 原始路径

**新增/修改：**

- 新增 frontend/e2e/agent-project-discussion.spec.ts
- 修改 frontend/e2e/agent-final-contract.spec.ts
- 修改 frontend/playwright.config.ts
- 修改 scripts/run-jar-e2e.ps1
- 修改 scripts/run-jar-e2e.test.ps1
- 新增 scripts/assert-live-project-discussion-context.ps1
- 新增 scripts/assert-live-project-discussion-context.test.ps1

- [ ] Fake Provider packaged lane 覆盖设计 §17.3 的桌面与移动端场景，不使用 API mock 替代 packaged JAR。
- [ ] PostgreSQL 重启后 TTL 内恢复 active focus；过期后 Summary 为 EXPIRED，reenter 生成新 handle。
- [ ] Browser 检查 storage/URL/history 中无 handle、resultItemId、问题、回答或聊天历史。
- [ ] Provider invalid JSON、deadline、cancel、late result 不越过 pointer generation。
- [ ] 实际获得用户授权后才运行真实 Provider；脚本使用固定脱敏输入，任何路径不打印问题、回答、Prompt、原始模型输出、Token、handle 或凭据。
- [ ] 真实 Provider 只记录 operation、公开 GoalKind、locked subject 是否保持、候选是否属于 typed scope、耗时桶、终局和 pass/fail。
- [ ] 原始 A2-19 路径必须覆盖“Recommendation → 承接式省略表达 → 限定 CHOICE/直接进入 → 项目讨论”，不能用显式按钮替代。

运行：

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/postgres-local.ps1 verify
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-jar-e2e.ps1 -ContextMode POSTGRESQL
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/assert-live-project-discussion-context.test.ps1

真实 Provider 命令只在脚本定义的显式授权参数和仓库外 secrets 就绪后运行。未获授权时，先向用户确认；确认未授权后才登记 category=AUTHORIZATION 的完整 WAIVED deferred item，并把 overallStatus 保持 VERIFICATION_IN_PROGRESS。WAIVED 不等于 PASS，也不得删除 A2-19。

### Task 2.11：最终删除门、全量门、文档与条件式提交

**修改：**

- docs/00-文档状态索引.md
- docs/08-当前实现状态.md
- docs/11-项目演进日志.md
- docs/15-Agent 2.0真实交互问题清单与修复边界.md
- docs/agent-architecture-status.json
- scripts/agent-architecture-status.ps1
- scripts/agent-architecture-status.test.ps1
- scripts/documentation-check.ps1
- scripts/documentation-check.test.ps1
- scripts/privacy-check.ps1
- scripts/privacy-check.test.ps1
- scripts/verify-release.ps1

- [ ] 运行旧 authority 零引用门和“恰好两个 prompt”门。
- [ ] 扫描 State/日志/前端持久化，证明无问题、窗口、Prompt、原始模型输出、Token、handle、消息或私有路径。
- [ ] 运行 code quality、architecture、documentation、privacy 的脚本自测与生产门。
- [ ] 运行 backend 全量 test 和 clean package。
- [ ] 运行 frontend 全量 test/check/build。
- [ ] 运行 PostgreSQL/Testcontainers、packaged-JAR desktop/mobile。
- [ ] 运行已授权真实 Provider 原始路径。
- [ ] 只有上述门全部通过，才从 docs/15 删除 A2-19 的 overview row、4.8、对应测试缺口、批次和 Exit Gate 文本；不创建历史归档。
- [ ] 更新 docs/08、docs/11、docs/00 与 architecture status，机器状态只写闭合枚举和安全证据，不写输入/输出/Prompt/handle。
- [ ] overallStatus 只有在硬不变量 PASS、deferredItems 为空且所有风险门真实通过后才恢复 COMPLETE。
- [ ] 再运行 scripts/agent-architecture-status.ps1，输出必须与账本一致。
- [ ] 仅在明确获得提交授权后创建一个中文小提交；不得暂存或提交不属于本 Slice 的并行改动。

全量命令：

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.test.ps1
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1 -Path backend/src
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/documentation-check.test.ps1
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/documentation-check.ps1
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.test.ps1
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path backend/src/main
    mvn.cmd -f backend/pom.xml test
    mvn.cmd -f backend/pom.xml clean package
    npm.cmd --prefix frontend test -- --run
    npm.cmd --prefix frontend run check
    npm.cmd --prefix frontend run build
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/postgres-local.ps1 verify
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-jar-e2e.ps1 -ContextMode POSTGRESQL
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-release.ps1
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/agent-architecture-status.ps1

建议提交：

    git commit -m "feat(agent): 建立项目讨论上下文权威"

## 固定禁止项

实施者不得：

- 新增任何自然语言退出、切换、推荐、数量、约束、比较或指代短语表；
- 解析 Assistant 文本、历史卡片文案或 DOM 位置恢复主体；
- 新增 RecentResultSet、第二 Conversation store、前端业务 router 或通用 workflow framework；
- 新增 discussion prompt、第二 Goal adapter、模型重试或 fallback Goal parser；
- 兼容无 operation 的旧 CONTINUE；
- 让 AI 输出/选择 handle、resultItemId、Token、Task、DAG、Provider 或证据；
- 让 AI 直接写 State、clear conversation 或越过 candidate set；
- 持久化问题、ConversationWindow、Prompt、raw model output 或聊天消息；
- 用访问续期 Context；
- 用配置式双栈、兼容桥或 runtime feature flag 回退；
- 在真实 Provider 未运行时宣称 A2-19 完成。

## 完成定义

本计划只有同时满足以下条件才可标记完成：

1. Prompt prerequisite 完成且只有两个 system prompt；
2. STANDARD 自由文本完全通过 AI closed proposal + backend validation；
3. MinimalGoalFallback 的开放语义 NLP 规则物理删除；
4. closed CONTINUE、ProjectDiscussionContext、active pointer 和 V4 是唯一生产路径；
5. Fact/Comparison Context、Goal continuation、PORTFOLIO_REFINE_RECOMMENDATION 与旧 CONTINUE 全部删除且零引用；
6. Backend/Frontend 共享合同同期迁移，无旧消费者；
7. Memory/PostgreSQL parity、pointer generation race、TTL/refresh/exit/switch/reenter 全部通过；
8. packaged-JAR desktop/mobile 与真实 Provider 原始 A2-19 路径通过；
9. 隐私、文档、架构、发布门通过；
10. A2-19 按动态账本规则删除，architecture status 与真实证据一致，没有未关闭必需 deferred item。

## 条件式中文提交顺序

提交不是本计划批准的默认副作用；每次都需用户明确授权。获得授权后的顺序固定：

1. 并行 Prompt Slice：feat(agent): 外部化模型提示词并收口回答深度
2. STANDARD Slice：refactor(agent): 收口自由文本语义路由
3. Project Discussion Slice：feat(agent): 建立项目讨论上下文权威

每个提交只包含对应 Slice 已归属文件。回退只使用 Git commit、已验证 JAR 或整体部署版本，不保留 runtime 兼容链。
