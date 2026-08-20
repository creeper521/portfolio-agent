# Agent P3 后端实施计划：受限 Portfolio 执行、证据晋升与会话业务上下文
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

> 日期：2026-08-12
> 状态：待在独立开发上下文执行
> 范围：P3 后端、公共后端 DTO、数据库、评测、隐私与发布门禁
> 不包含：前端组件、视觉、交互原型和实时进度传输

## 0. 目标

把当前 P2 的 `SemanticTask → PortfolioSemanticTaskExecutor → PortfolioIntelligence` 适配链替换为唯一、确定性、只读、有界的 P3 执行深模块，并增加同一页签可恢复的服务端强类型业务 Context。

最终生产链固定为：

```text
POST /api/v2/answers
  → ResumeToken / request receipt 解析
  → P2 TurnRouter
  → SemanticTaskExecutionContext
  → PortfolioExecutionPlanner
  → PORTFOLIO_EVIDENCE_RETRIEVAL_V1
  → PortfolioRetrievalCandidateSet
  → EvidencePromotionValidator
  → ValidatedEvidenceBundle
  → EvidenceSupportAssessor
  → Fact / Compare / Recommend / Refine Result Policy
  → PortfolioAnswerComposer
  → TaskOutcome + ExecutionDisplayPlan
  → Context mutation + receipt 原子提交
  → P3 公共响应
```

完成后必须满足：

- P2 仍是唯一语义决策权威；P3 不读原始问题或 `goalLabel` 重新判断意图。
- P3 Catalog 只有 `PORTFOLIO_EVIDENCE_RETRIEVAL_V1`。
- RAG 留在 Retriever 内部，P3 边界只出现强类型 Invocation、Subject 和 Claim–Evidence candidate。
- 旧 `ConversationToolService/modelPort.planTools` 和 `PortfolioIntelligence.resolveTypedTask/tryResolve` 生产入口删除且不可达。
- 回答不再公开 `claimIds/evidenceIds`，改用 `sourceReferences`。
- PostgreSQL 只保存加密的强类型业务 Context、Active 指针和最小 request receipt；不保存问题、答案或 Evidence 正文。
- P3 v1 仍为同步 JSON，只返回最终执行快照。

## 1. 权威输入

执行前完整阅读：

1. `AGENTS.md`
2. `docs/00-文档状态索引.md`
3. `docs/04-项目代码约束.md`
4. `docs/superpowers/specs/2026-08-11-bounded-tool-orchestration-design.md`
5. `docs/handoffs/2026-08-12-agent-p3-frontend-contract-handoff.md`
6. `docs/superpowers/specs/2026-08-10-semantic-turn-routing-design.md`
7. `docs/superpowers/plans/2026-08-10-semantic-turn-routing-phase2.md`
8. `docs/superpowers/specs/2026-08-06-agent-answer-composition-design.md`
9. `docs/13-Agent对话体验与智能编排改造路线图.md`

发生冲突时优先级为：P3 主 Spec → P3 前端公共契约 → 本计划 → P2/P1 既有文档。实现中发现主 Spec 不可实现时先停止该切片并修订 Spec，不能用代码静默改变语义。

## 2. 全局实施规则

- 严格 TDD：每项先写最小失败测试并观察 RED，再实现 GREEN，最后重构。
- Java 21；生产和测试代码禁止 `var`、`record`、Lombok。
- 值对象使用显式不可变 `final class`、构造校验、防御复制、`equals/hashCode` 和脱敏 `toString()`。
- 不记录问题、答案、`goalLabel`、Token、ContextHandle、内部主体/Claim/Evidence/Chunk ID、ContentVersion 实值、SQL、路径、主机或异常消息。
- 不读取私有 Obsidian、日报、凭据、未审核截图或任何未发布材料。
- 不增加通用 Tool Registry、Hook、MCP、任意 URL/SQL/文件能力、工作流 DSL、多 Agent、SSE 或 WebSocket。
- 保留用户已有工作树修改；未经明确授权不 stage、不 commit、不 push。
- 每个任务完成后运行其 focused tests；每个切片完成后运行切片回归；最终运行全量发布门禁。
- 前端修改由独立 Agent 完成。本计划只实现后端 DTO 与 API，并提供契约测试；不要代替前端 Agent 修改 Vue/TypeScript。

## 3. 已冻结的首版参数

| 参数 | P3 v1 值 | 依据/行为 |
| --- | ---: | --- |
| Capability 数 | 1 | 仅 `PORTFOLIO_EVIDENCE_RETRIEVAL_V1` |
| 单 Portfolio 任务逻辑检索 | 1 | Planner 恰好一个 Invocation |
| 后端尝试 | 2 | Primary + 一次基础设施 fallback |
| 整轮逻辑检索 | 6 | 与 P2 最大 6 个任务一致 |
| Subject 元数据 | 64/任务 | 当前正式 Bundle 58 个主体 |
| Evidence unit | 128/任务 | 当前 88 个 Claim–Evidence unit |
| Evidence unit | 16/主体 | 当前单主体最大 14 |
| PublicSourceReference | 96/任务 | 覆盖 5 个推荐项和最多 8 个能力条件 |
| 组合正文 | 4000 字符/任务 | 整轮仍受 8000 字符上限约束 |
| P3 绝对截止时间 | 请求开始后 10 秒 | HTTP 12 秒内留 2 秒给 Context 提交和映射 |
| 最小启动窗口 | 250ms | 不足时任务返回 `NOT_EXECUTED_BUDGET` |
| Context payload | 16KiB/条 | 超限拒绝保存，不截断 |
| Context 数量 | 32/会话 | 确定性清理后仍超限则不可续接 |
| 空闲 TTL | 24 小时 | 合法使用才续期 |
| 绝对 TTL | 7 天 | 不能续期 |
| 清理频率 | 15 分钟 | 多实例 advisory lock |
| 清理批次 | 500 conversation | 物理删除，级联 Context/receipt |
| receipt lease | 30 秒 | Producer 崩溃后才允许接管 |
| Token | 256-bit 随机 | Base64url 无 padding；数据库只存 HMAC |
| ContextHandle | 192-bit 随机 | Base64url 无 padding；必须和 Token 联合校验 |

如果测试证明正式用例超过这些上限，先修订 Spec；不得从请求参数放宽。

字符预算由 P2 在 execution selection 后确定性分配：每个 executable task 先取 `min(4000, floor(8000 / executableTaskCount))`，余数字符按稳定拓扑逐个补 1 且不突破 4000。任务未使用的额度不再借给后续任务，避免执行顺序改变输出上限。

## 4. 实施切片和依赖

```text
P3-0 文档/基线/边界冻结
  ↓
P3-A P2→P3 seam、Allowance、Scope/Display/Context 基础模型
  ↓
P3-B Catalog、Planner、Trusted Plan
  ↓
P3-C CandidateSet、Evidence Promotion、Validated Bundle、Support
  ↓
P3-D 唯一 Retrieval Capability、Bundle/PostgreSQL 原子 failover
  ↓
P3-E Result Policy、P1 Composer、Context Store、HTTP/API、生产切流
  ↓
P3-F Eval 迁移、旧链删除、隐私/权威文档、完整发布门禁
```

P3-A～P3-D 新代码在生产 wiring 中保持不可达；P3-E 一次性切换。禁止长期 feature flag 双轨。

## 5. 目标包结构

### 5.1 P2 seam

修改：

- `answer.routing.service.SemanticTaskExecutor`
- `answer.routing.service.SemanticTurnCoordinator`
- `answer.routing.domain.TaskOutcome`
- `answer.routing.domain.TaskResultPayload`
- `answer.routing.domain.TaskResultProvenance`

新增：

- `answer.routing.domain.SemanticTaskExecutionContext`
- `answer.routing.domain.TaskExecutionAllowance`
- `answer.routing.domain.AuthorizedContextReference`
- `answer.routing.domain.SemanticTurnExecutionBudget`

### 5.2 P3 深模块

根包：`com.portfolio.agent.answer.intelligence.execution`

```text
domain/
  PortfolioExecutionTypes
  SafeReasonCode
  AuthorizedSubjectScope
  RecommendationScopeBinding
  FacetRetrievalProfile
  ComparisonDimensionProfile
  EvidenceSelectionPolicy
  PortfolioEvidenceInvocation
  PortfolioExecutionPlan
  TrustedPortfolioExecutionPlan
  CapabilityExecutionConstraints
  CapabilityExecutionResult
  PortfolioRetrievalCandidateSet
  CandidateCoverageReport
  ValidatedEvidenceBundle
  EvidenceSupportAssessment
  ExecutionDisplayPlan

planning/
  PortfolioCapabilityCatalog
  PortfolioExecutionPlanner
  PortfolioPlanValidator

capability/
  PortfolioEvidenceCapability
  DefaultPortfolioEvidenceCapability
  PortfolioCandidateRetrievalPort

validation/
  EvidencePromotionValidator
  PublicReferenceValidator

support/
  EvidenceSupportAssessor
  RecommendationProfiles
  RecommendationRankingPolicy

resultpolicy/
  PortfolioResultPolicy
  FactResultPolicy
  ComparisonResultPolicy
  RecommendationResultPolicy
  RefineResultPolicy
  ExecutionDisplayPlanProjector

adapter/bundle/
  BundlePortfolioCandidateRetrievalAdapter

adapter/postgres/
  PostgresPortfolioCandidateRetrievalAdapter

adapter/failover/
  AtomicFailoverPortfolioCandidateRetrievalAdapter

adapter/config/
  PortfolioExecutionConfiguration
  PortfolioExecutionProperties
```

### 5.3 P1 回答材料

新增或演进到 `answer.domain` / `answer.service`：

- `GroundedStatement`
- `PublicSourceReference`
- `GroundedAnswerContribution`
- `PortfolioAnswerMaterial`（FACT/COMPARISON/RECOMMENDATION 强类型变体）
- `PortfolioAnswerComposer` 接口
- `DeterministicPortfolioAnswerComposer`（Facade）
- Fact/Comparison/Recommendation 内部确定性策略

### 5.4 会话业务 Context

根包：`com.portfolio.agent.answer.context`

```text
domain/
  ConversationId
  ResumeToken
  ContextHandle
  ConversationContextType
  ConversationContinuationStatus
  RecentSemanticTaskContext
  RecommendationContext
  ConversationContextSummary
  ContextMutationSet
  RequestReceipt
  RequestFingerprint

gateway/
  ConversationBusinessContextStore
  ContextEnvelopeCryptographyPort
  ResumeTokenHashPort

service/
  ConversationContextFacade
  ConversationContextResolver
  ConversationContextMutationFactory
  ConversationContextCapacityPolicy
  ConversationContextCodecRegistry
  RecentSemanticTaskContextCodec
  RecommendationContextCodec
  SafeContextSummaryProjector
  RequestReceiptService
  ConversationContextCleanupService

adapter/memory/
  InMemoryConversationBusinessContextStore

adapter/crypto/
  JdkContextEnvelopeCryptographyAdapter
  JdkResumeTokenHashAdapter

adapter/postgres/
  ConversationContextDatabaseProperties
  ConversationContextDatabaseConfiguration
  JdbcConversationBusinessContextStore
  ConversationContextCleanupJob
```

### 5.5 HTTP 与公共 DTO

新增/修改：

- `ConversationAnswerController`
- `ProductionConversationService`
- `ConversationalAgentRuntime`
- `ConversationAnswerRequest`、新增 `ContextReferenceRequest`
- `ConversationAnswerResponse`、新增 `CompletionReceiptResponse`
- `ConversationResponse`
- `ConversationContextController`
- `ConversationContextSummaryResponse`
- `ExecutionDisplayPlanResponse`
- `PublicSourceReferenceResponse`
- `CompletedTaskResponse.contextHandle`
- `ApiErrorResponse` 的既有安全 envelope 映射

## 6. 数据库物理设计

Flyway 位置固定为 `backend/src/main/resources/db/context/`，schema 固定为 `agent_context`，history table 固定为 `flyway_schema_history_context`。

### 6.1 `conversation_session`

```text
conversation_id uuid primary key
resume_token_hash bytea unique not null check octet_length = 32
token_key_id varchar(64) not null
created_at timestamptz not null
last_accessed_at timestamptz not null
idle_expires_at timestamptz not null
absolute_expires_at timestamptz not null
context_count integer not null check 0..32
payload_bytes integer not null check 0..524288
revision bigint not null
```

索引：`(idle_expires_at)`、`(absolute_expires_at)`。不保存 Token 明文、IP、UA 或浏览器指纹。

### 6.2 `conversation_context`

```text
context_handle varchar(64) primary key
conversation_id uuid not null references conversation_session on delete cascade
context_type varchar(32) not null
parent_context_handle varchar(64) null
source_task_id varchar(100) not null
content_version_binding varchar(128) not null
schema_version integer not null
encryption_key_id varchar(64) not null
nonce bytea not null check octet_length = 12
typed_context_ciphertext bytea not null
payload_bytes integer not null check 1..16384
created_at timestamptz not null
expires_at timestamptz not null
unique (conversation_id, context_handle)
foreign key (conversation_id, parent_context_handle)
  references conversation_context (conversation_id, context_handle)
```

约束：组合外键保证 parent 属于同一 conversation；Context 行不可 UPDATE，只允许 INSERT/DELETE。索引：`(conversation_id, created_at desc)`、`(conversation_id, context_type, created_at desc)`、`(expires_at)`。

### 6.3 `conversation_active_context`

```text
conversation_id uuid not null references conversation_session on delete cascade
active_slot varchar(32) not null
context_handle varchar(64) not null references conversation_context
revision bigint not null
updated_at timestamptz not null
primary key (conversation_id, active_slot)
foreign key (conversation_id, context_handle)
  references conversation_context (conversation_id, context_handle)
```

slot 闭集：`ACTIVE_FACT_CONTEXT / ACTIVE_COMPARE_CONTEXT / ACTIVE_RECOMMENDATION`。更新使用 `WHERE revision = expectedRevision` CAS。

### 6.4 `conversation_request_receipt`

```text
request_token uuid primary key
conversation_id uuid not null references conversation_session on delete cascade
request_fingerprint bytea not null check octet_length = 32
parent_context_handle varchar(64) null
status varchar(16) not null
lease_id uuid null
lease_expires_at timestamptz null
completion_key_id varchar(64) null
completion_nonce bytea null
completion_ciphertext bytea null
created_at timestamptz not null
updated_at timestamptz not null
expires_at timestamptz not null
foreign key (conversation_id, parent_context_handle)
  references conversation_context (conversation_id, context_handle)
```

status 闭集：`IN_PROGRESS / COMPLETED`。completion 只保存公开任务状态、ContextHandle 和 ContinuationStatus，不保存答案、标题、blocks、引用正文或问题。索引：`(conversation_id, expires_at)`、`(status, lease_expires_at)`、`(expires_at)`。

## 7. Task 0：建立实施门禁与干净基线

**文件：**

- 修改 `AGENTS.md`
- 修改 `docs/00-文档状态索引.md`
- 核对主 Spec、前端交接和本计划均存在
- 暂不修改 `docs/08-当前实现状态.md` 或 `docs/11-项目演进日志.md`

- [ ] 记录 `git status --short`，确认两份 P3 文档属于用户现有修改，不覆盖其他未提交内容。
- [ ] 在 `AGENTS.md` 增加“P3 已批准的强类型短期业务 Context 例外”：仍禁止问题/答案落库、长期记忆、认证和私有数据；只允许按主 Spec 实施。
- [ ] 在文档状态索引登记主 Spec、本计划、前端交接为“已批准、待实施”，不把 P3 写成已实现。
- [ ] 运行后端基线：

```powershell
mvn.cmd -f backend/pom.xml test
powershell -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1
powershell -ExecutionPolicy Bypass -File scripts/architecture-check.ps1
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
```

- [ ] 若基线失败，只记录与 P3 无关的既有失败并先请求处理方向；不得在 P3 任务中顺手修复无关问题。
- [ ] 用当前 Bundle 断言 58 Subject、88 Claim–Evidence unit、单主体最大 14；如果快照已升级且突破冻结预算，先修订 Spec。

**完成条件：** 权威文档允许 P3 开发；基线结果可复现；没有生产行为变化。

## 8. Task 1（P3-A）：升级 P2→P3 强类型执行 seam

**修改：**

- `answer.routing.service.SemanticTaskExecutor`
- `answer.routing.service.SemanticTurnCoordinator`
- `answer.routing.domain.TaskOutcome`

**新增：**

- `SemanticTaskExecutionContext`
- `TaskExecutionAllowance`
- `AuthorizedContextReference`
- `SemanticTurnExecutionBudget`
- 对应 domain/coordinator tests

- [ ] RED：断言 executor 只有 `execute(SemanticTaskExecutionContext)`，不再接收裸 task + dependency list。
- [ ] RED：断言 Context 包含且只包含 task、applicable exclusions、dependency outcomes、expected contentVersion、allowance、authorized Context references。
- [ ] RED：Portfolio allowance 为 `1 logical / 2 attempts / 128 units / 96 refs / 4000 chars / absolute deadline`；General/Synthesis 检索为 0。
- [ ] RED：整轮 8000 字符按 executable task 稳定等分并分配余数；1/2/3/6 个任务分别覆盖 4000、4000、2667/2667/2666、1334/1334/1333/1333/1333/1333，未用额度不转借。
- [ ] RED：Coordinator 在任务开始前剩余时间不足 250ms 时返回 `NOT_STARTED + NOT_EXECUTED_BUDGET`，不调用 executor。
- [ ] GREEN：增加显式不可变类，所有 Context `toString()` 只输出类型、枚举和计数。
- [ ] GREEN：Coordinator 仍按稳定拓扑顺序执行，按任务构造不可变 Context；不改变 P2 计划编译与依赖语义。
- [ ] REFACTOR：删除 `SemanticTaskExecutor.execute(SemanticTask, List<TaskOutcome>)` 旧签名和所有适配调用。

Focused verification：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=SemanticTaskExecutionContextTest,SemanticTurnCoordinatorTest,SemanticTaskExecutorAdapterTest test
```

**完成条件：** P2 测试继续通过；P3 获得唯一强类型入口；尚未接入新 Engine。

## 9. Task 2（P3-A）：收敛 TaskOutcome、Contribution 和安全原因

**修改：**

- `TaskOutcome.java`
- `TaskResultPayload.java`
- `TaskResultProvenance.java`
- `SemanticTurnOutcome.java`
- `AnswerResolution.java`

**新增：**

- `answer.domain.GroundedAnswerContribution`
- `answer.intelligence.execution.domain.SafeReasonCode`

- [ ] RED：覆盖新的 `PARTIALLY_ANSWERED / PRESENTATION_BLOCKED / DEPENDENCY_UNAVAILABLE / NOT_EXECUTED_BUDGET`。
- [ ] RED：`EMPTY` 和 task-level `CAPABILITY_UNAVAILABLE` 不再可构造。
- [ ] RED：只有 `ANSWERED/PARTIALLY_ANSWERED` 可携带 renderable Contribution；`PRESENTATION_BLOCKED` 无正文。
- [ ] RED：SafeReasonCode 为闭集，异常消息和任意字符串不能进入公开结果。
- [ ] GREEN：按主 Spec 14 节实现 execution status、resolution、evidence state 映射。
- [ ] GREEN：Synthesis 只消费 `GroundedAnswerContribution`，不能访问 CandidateSet 或 Evidence 实体。
- [ ] REFACTOR：更新现有 P2 task summary mapper，保留 P2 disposition 不变。

Focused verification：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=TaskOutcomeContractTest,SemanticTurnCoordinatorTest,ConversationAnswerResponseMapperTest test
```

**完成条件：** 业务空结果、展示阻断、技术失败、依赖失败和预算失败五类不再混淆。

## 10. Task 3（P3-A）：建立 Scope、Profile、Invocation 与 Display 基础模型

**新增：**

- `AuthorizedSubjectScope`
- `RecommendationScopeBinding`
- `FacetRetrievalProfile`
- `ComparisonDimensionProfile`
- `EvidenceSelectionPolicy`
- `PortfolioEvidenceInvocation`
- `CapabilityExecutionConstraints`
- `ExecutionDisplayPlan`
- 对应 contract tests

- [ ] RED：`EXACT_SUBJECTS` 非空且不可扩大；`ALL_PUBLISHED_CANDIDATES` 必须绑定 contentVersion。
- [ ] RED：Refine 必须有已授权 RecommendationScopeBinding，新增排除只能收缩范围。
- [ ] RED：Facet/Dimension 只能从 P2 闭集映射；`TIMELINE` 不存在。
- [ ] RED：Invocation 的字段扫描中不得出现 question、goalLabel、prompt、query、Map、SQL、URL 或 path。
- [ ] RED：ExecutionDisplayPlan 只能有四个 stage code，FINAL 响应不能有 PENDING/IN_PROGRESS。
- [ ] GREEN：实现所有不可变值对象和闭集映射。
- [ ] GREEN：建立 `ExecutionDisplayPlanProjector` 的输入契约，但此时不接 HTTP。

Focused verification：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioExecutionDomainContractTest,ExecutionDisplayPlanContractTest test
```

**完成条件：** P3 后续代码不能借自由文本或泛型 Map 扩大能力。

## 11. Task 4（P3-B）：实现冻结 Catalog、确定性 Planner 和 Trusted Plan

**新增：**

- `PortfolioCapabilityCatalog`
- `PortfolioExecutionPlanner`
- `PortfolioPlanValidator`
- `PortfolioExecutionPlan`
- `TrustedPortfolioExecutionPlan`
- `PortfolioExecutionProperties`
- 对应 planner/catalog tests

- [ ] RED：Catalog 快照恰好一个 descriptor，ID 为 `PORTFOLIO_EVIDENCE_RETRIEVAL_V1`，只读且无注册 API。
- [ ] RED：同一 task/context/catalog/allowance 必须产生相同 Plan。
- [ ] RED：合法 Plan 恰好一个 Invocation；主体扩大、contentVersion 冲突、unsupported profile、过期 allowance 和自由文本渗入全部拒绝。
- [ ] RED：空推荐候选显式编译为 `ALL_PUBLISHED_CANDIDATES`，不是 null/unbounded。
- [ ] RED：General/Synthesis 任务不能由 P3 Planner 接受。
- [ ] GREEN：Planner 只做授权编译；Validator 是唯一 Trusted wrapper 签发者。
- [ ] GREEN：配置属性只允许收紧冻结硬上限；生产启动时大于 Spec 值直接失败。

Focused verification：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioCapabilityCatalogTest,PortfolioExecutionPlannerTest,PortfolioPlanValidatorTest test
```

**完成条件：** P3-B 完成，尚未调用 Retriever；不存在模型规划或 plan repair。

## 12. Task 5（P3-C）：定义 CandidateSet 与完整 Coverage Report

**新增：**

- `PortfolioRetrievalCandidateSet`
- `CandidateCoverageReport`
- `CandidateSubject`
- `ClaimEvidenceCandidate`
- `PublicEvidenceDescriptor`
- 对应 contract tests

- [ ] RED：CandidateSet 必须绑定 capability ID、attempt、contentVersion、authorized scope、Subject 元数据、Claim–Evidence candidates 和 coverage。
- [ ] RED：Subject、Claim、Evidence 关系必须可验证；孤儿 Claim、孤儿 Evidence、跨主体 link、重复 reference code 均拒绝构造。
- [ ] RED：Coverage 明确记录每个 requested facet/dimension/criterion 的 `PRESENT / ABSENT / TRUNCATED`，不能只返回全局布尔值。
- [ ] RED：超过 64 Subject、128 unit 或每主体 16 unit 时返回 typed `LIMIT_EXCEEDED`，不能截断后伪装完整。
- [ ] RED：CandidateSet 不包含 Chunk、向量、BM25 score、SQL row、Prompt、查询文本或 Retriever 内部 ID。
- [ ] GREEN：实现 CandidateSet 和测试 fixture；保留稳定输入顺序并显式去重。

Focused verification：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioRetrievalCandidateSetTest,CandidateCoverageReportTest test
```

**完成条件：** Retriever 与 P3 之间形成唯一正式材料边界。

## 13. Task 6（P3-C）：实现 Evidence Promotion 与公开引用校验

**新增：**

- `EvidencePromotionValidator`
- `PublicReferenceValidator`
- `ValidatedEvidenceBundle`
- `ValidatedEvidenceUnit`
- `PublicSourceReference`
- 对应 validator tests

- [ ] RED：只允许 `publicStatus=APPROVED`、有效期有效、contentVersion 一致、Claim–Evidence link 完整的 unit 晋升。
- [ ] RED：Evidence code 必须唯一并可转为 `referenceKey`；数据库 ID、Claim ID、Chunk ID 不能进入 PublicSourceReference。
- [ ] RED：`sourceType` 只允许 `COLLECTION / DOCUMENT / SCREENSHOT / CODE / TEST_RESULT`。
- [ ] RED：`subjectRoute/evidenceRoute` 只允许站内相对公开路由；绝对 URL、文件路径、对象存储路径拒绝。
- [ ] RED：任一 integrity failure 使整个 attempt 失败，不能丢弃坏 unit 后继续声称完整。
- [ ] RED：ValidatedEvidenceBundle 不保留 raw candidate、chunk、score 或 mutable collection。
- [ ] GREEN：按 Spec 8.1 固定顺序晋升，产生 typed rejection report 和安全 reason code。

Focused verification：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=EvidencePromotionValidatorTest,PublicReferenceValidatorTest,ValidatedEvidenceBundleTest test
```

**完成条件：** 只有 ValidatedEvidenceBundle 可以进入 Support/Result Policy。

## 14. Task 7（P3-C）：实现 Support Assessor 和四类结果判定

**新增：**

- `EvidenceSupportAssessor`
- `EvidenceSupportAssessment`
- `RecommendationProfiles`
- `RecommendationRankingPolicy`
- support tests

- [ ] RED：Fact 对每个请求 Facet 独立判断支持度，缺失项进入 omitted labels，不补写。
- [ ] RED：Compare 要求每个主体在同一 dimension 有可比较 Evidence；缺一方只能 partial/unsupported，不能不对称下结论。
- [ ] RED：`GENERAL_PORTFOLIO_RECOMMENDATION_V1` baseline 为 RESPONSIBILITY/IMPLEMENTATION/VERIFICATION/OUTCOME 的 OR。
- [ ] RED：`CAPABILITY_MATCH_RECOMMENDATION_V1` 在 baseline 之外要求每个 requested capability 都有 Evidence。
- [ ] RED：每 criterion 最多选 2 个 Evidence unit；缺少 Evidence 不能产生负面评价。
- [ ] RED：careerTrack 是 hard filter；audienceRole 只影响呈现，不影响排序。
- [ ] RED：排序优先级固定为 VERIFICATION、IMPLEMENTATION、TECHNICAL_DECISION、OUTCOME、RESPONSIBILITY、LEARNING，并以公开主体稳定 key 最终打破平局。
- [ ] GREEN：输出 `SUFFICIENT / PARTIAL / INSUFFICIENT / PRESENTATION_BLOCKED` 与完整 criterion report。

Focused verification：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=EvidenceSupportAssessorTest,RecommendationProfilesTest,RecommendationRankingPolicyTest test
```

**完成条件：** 推荐、比较和事实支持度完全由规则与已验证 Evidence 决定。

## 15. Task 8（P3-D）：实现唯一 Capability 与原子 Primary/Fallback

**新增：**

- `PortfolioEvidenceCapability`
- `DefaultPortfolioEvidenceCapability`
- `PortfolioCandidateRetrievalPort`
- `BundlePortfolioCandidateRetrievalAdapter`
- `PostgresPortfolioCandidateRetrievalAdapter`
- `AtomicFailoverPortfolioCandidateRetrievalAdapter`
- capability/adapter tests

**修改：**

- `BundlePortfolioRetriever`、`PostgresPortfolioRetriever` 相关内部查询代码，仅保留可复用 Retriever 内核
- `FailoverPortfolioRetriever` 迁移后删除或改为新 port 的内部实现

- [ ] RED：Capability 只接受 `PortfolioEvidenceInvocation + CapabilityExecutionConstraints`。
- [ ] RED：Primary SUCCESS/EMPTY 不触发 fallback；只有 UNAVAILABLE/TIMED_OUT 且 allowance 足够才触发一次 fallback。
- [ ] RED：INTEGRITY_FAILED 直接失败；Primary 的部分 CandidateSet 永不与 Fallback 混合。
- [ ] RED：Fallback 结果仍必须经过相同 EvidencePromotionValidator。
- [ ] RED：deadline 中断后丢弃整个 attempt；不得返回半 CandidateSet。
- [ ] RED：Bundle 和 PostgreSQL 对相同 Invocation 产生同语义 scope/profile/coverage。
- [ ] GREEN：在 adapter 内将闭集 Profile 转为固定 Retriever 参数；禁止使用用户文本或模型关键词。
- [ ] GREEN：RAG chunk/keyword/vector/RRF 保留在现有 Retriever 内部，不出 adapter。
- [ ] GREEN：PostgreSQL 查询参数化，账号只读公开内容 schema；无任意 SQL 拼接。

Focused verification：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=DefaultPortfolioEvidenceCapabilityTest,AtomicFailoverPortfolioCandidateRetrievalAdapterTest,BundlePortfolioCandidateRetrievalAdapterTest,PostgresPortfolioCandidateRetrievalAdapterTest test
```

PostgreSQL integration：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PostgresPortfolioCandidateRetrievalIntegrationTest test
```

**完成条件：** P3-D 能从两类后端得到同构 CandidateSet，但尚未成为生产 executor。

## 16. Task 9（P3-E 前半）：实现 Result Policy 与 P1 Composer Facade

**新增：**

- `PortfolioResultPolicy`
- `FactResultPolicy`
- `ComparisonResultPolicy`
- `RecommendationResultPolicy`
- `RefineResultPolicy`
- `GroundedStatement`
- `PortfolioAnswerMaterial`
- `PortfolioAnswerComposer`
- Comparison/Recommendation 内部 composer strategy tests

**修改：**

- `DeterministicPortfolioAnswerComposer`
- `PortfolioAnswerPlan`
- `PortfolioAnswerSection`
- `TaskResultPayload`

- [ ] RED：Result Policy 只能从 ValidatedEvidenceBundle + SupportAssessment 生成受控 GroundedStatement。
- [ ] RED：每条事实、比较结论、推荐理由都绑定对应 PublicSourceReference。
- [ ] RED：Composer 唯一公开方法为 `compose(PortfolioAnswerMaterial)`，不再接受 `PortfolioIntelligenceResult`。
- [ ] RED：Fact、Comparison、Recommendation 使用同一 Facade 的内部策略；P2 不感知多个 Composer。
- [ ] RED：Composer 不能读取问题、调用模型、Retriever、数据库或 Context Store。
- [ ] RED：引用越界、statement 无来源、正文超过 allowance 时返回 `PRESENTATION_BLOCKED`，不泄露半成品。
- [ ] GREEN：迁移现有 Fact 章节顺序和保守 gap 文案；新增确定性比较矩阵和推荐 item 投影。
- [ ] GREEN：`GroundedAnswerContribution` 只暴露 statements、public sources、caveats、omitted labels。
- [ ] REFACTOR：删除 `compose(PortfolioIntelligenceResult)` 和旧 ID scope 校验，改用 referenceKey scope 校验。

Focused verification：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioResultPolicyTest,DeterministicPortfolioAnswerComposerTest,ComparisonPortfolioAnswerComposerTest,RecommendationPortfolioAnswerComposerTest test
```

**完成条件：** Fact/Compare/Recommend/Refine 都能从已验证材料形成 P1 结果，不经过旧 Intelligence result。

## 17. Task 10（P3-E）：建立 Context 领域模型、Codec 与密码学

**新增：**

- `answer.context.domain` 全部值对象
- `ConversationContextCodecRegistry`
- `RecentSemanticTaskContextCodec`
- `RecommendationContextCodec`
- `ContextEnvelopeCryptographyPort`
- `ResumeTokenHashPort`
- `JdkContextEnvelopeCryptographyAdapter`
- `JdkResumeTokenHashAdapter`
- context domain/crypto tests

- [ ] RED：Recent Context 只包含 taskType、公开主体引用、Facet/Dimension、contentVersion、sourceTaskId。
- [ ] RED：Recommendation Context 只包含授权 scope、profile version、baseline、约束、偏好、排除、result limit 和 parent handle。
- [ ] RED：任何 Context 字段中都不存在 question、answer、Evidence、CandidateSet、Prompt 或模型输出。
- [ ] RED：ResumeToken 为 32 随机字节，ContextHandle 为 24 随机字节；`toString()` 全部脱敏。
- [ ] RED：Token hash 使用 HMAC-SHA-256，不使用裸 SHA；当前 key 写、当前/上一 key 读。
- [ ] RED：payload 使用 AES-256-GCM、96-bit nonce；AAD 绑定 conversationId、contextHandle、contextType、schemaVersion。
- [ ] RED：密文/nonce/AAD 任一位改变必须认证失败；失败只返回 typed integrity code。
- [ ] RED：Codec 只读 N/N-1；未知版本或 N-2 失败关闭，不用 Map 或反射自动迁移。
- [ ] GREEN：实现 canonical JSON/byte codec，序列化稳定且 payload >16KiB 在加密前拒绝。

Focused verification：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ConversationContextDomainTest,ConversationContextCodecRegistryTest,JdkContextEnvelopeCryptographyAdapterTest,JdkResumeTokenHashAdapterTest test
```

**完成条件：** Context 可以安全序列化和轮换读取，仍未落库。

## 18. Task 11（P3-E）：实现 InMemory Store、Resolver、容量与摘要

**新增：**

- `ConversationBusinessContextStore`
- `InMemoryConversationBusinessContextStore`
- `ConversationContextFacade`
- `ConversationContextResolver`
- `ConversationContextCapacityPolicy`
- `ConversationContextMutationFactory`
- `SafeContextSummaryProjector`
- service/store tests

- [ ] RED：ContextHandle 只能与同一 ResumeToken 对应 conversation 联合解析。
- [ ] RED：解析优先级固定为 UI 显式 Handle → 唯一兼容 Active → 最近创建的唯一兼容 Active → Clarification。
- [ ] RED：Recommendation 建不可变 parent chain；Fact/Compare 只维护各自 Active slot。
- [ ] RED：并发 Refine 从同一 parent 可以各自成功保存；Active 推荐仅 CAS 胜者推进，失败分支仍可显式访问。
- [ ] RED：最多 32 Context/16KiB 每条；按“非 Active 普通 Context → 非 Active 推荐旧版本”确定性清理，不删除当前 Active。
- [ ] RED：空闲 24h、绝对 7d；只有合法 resolve 才续期，非法 Token/归属失败不续期。
- [ ] RED：Summary 只输出公开 label、task type、Facet/Dimension/preference label、canRefine；不包含 handle、版本、问题、答案或模型摘要。
- [ ] GREEN：实现内存版本供 unit/Eval/local 明确模式使用，不加入生产 fallback。

Focused verification：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=InMemoryConversationBusinessContextStoreTest,ConversationContextResolverTest,ConversationContextCapacityPolicyTest,SafeContextSummaryProjectorTest test
```

**完成条件：** Context 业务规则在无数据库环境中可完整单测；生产仍不可启用 IN_MEMORY。

## 19. Task 12（P3-E）：实现 PostgreSQL schema、Store、CAS 与清理

**新增：**

- `db/context/V1__conversation_context_schema.sql`
- `ConversationContextDatabaseProperties`
- `ConversationContextDatabaseConfiguration`
- `JdbcConversationBusinessContextStore`
- `ConversationContextCleanupService`
- `ConversationContextCleanupJob`
- PostgreSQL integration tests

**修改：**

- `application.yml`
- `application-local.yml`
- `application-prod.yml`
- `.env.example`
- `.env.postgres.example`
- `scripts/postgres/init-databases.sh`
- 本地 PostgreSQL 启动/测试脚本（只增加 context schema/账号）

- [ ] RED：Flyway 创建第 6 节四张表、closed CHECK、FK、索引和 history table。
- [ ] RED：Context insert + Active CAS + receipt COMPLETED 在一个事务；任一步失败全部回滚。
- [ ] RED：requestToken 全局唯一；相同 fingerprint 可查，相同 token 不同 fingerprint 冲突。
- [ ] RED：30s lease 未过期不能接管，过期可重新 claim；COMPLETED 永不重复创建 Context。
- [ ] RED：清除 conversation 物理级联删除 Context、Active、receipt；重复清除语法合法 Token仍成功。
- [ ] RED：清理任务每 15 分钟、500 会话一批；advisory lock 未获得时本实例跳过。
- [ ] RED：数据库没有 question/answer/evidence text 列，集成测试查询 information_schema 断言。
- [ ] GREEN：独立 Hikari DataSource、独立 schema、最小权限账号、独立 Flyway；不复用 public/governance JdbcTemplate。
- [ ] GREEN：`POSTGRESQL` 模式缺 URL/账号/密码/HMAC/AES key/schema 时启动失败；P3 生产 wiring 只接受 `POSTGRESQL`，`IN_MEMORY/DISABLED` 在 prod profile 均启动失败。
- [ ] GREEN：数据库运行时故障返回 typed availability，不静默切内存。

配置键：

```text
portfolio.conversation-context.mode = DISABLED | IN_MEMORY | POSTGRESQL
portfolio.conversation-context.idle-ttl = 24h
portfolio.conversation-context.absolute-ttl = 7d
portfolio.conversation-context.cleanup-interval = 15m
portfolio.conversation-context.cleanup-batch-size = 500
portfolio.database.context.url / username / password / schema
portfolio.conversation-context.crypto.current-token-key-id/key
portfolio.conversation-context.crypto.previous-token-key-id/key
portfolio.conversation-context.crypto.current-payload-key-id/key
portfolio.conversation-context.crypto.previous-payload-key-id/key
```

Focused verification：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ConversationContextDatabaseConfigurationTest,JdbcConversationBusinessContextStoreIntegrationTest,ConversationContextCleanupIntegrationTest test
```

**完成条件：** PostgreSQL Context Store 满足事务、TTL、CAS、清除和加密约束；无生产 fallback。

## 20. Task 13（P3-E）：实现持久 request receipt 与首轮丢响应恢复

**新增/修改：**

- `RequestReceiptService`
- `RequestFingerprint`
- `CompletionReceipt`
- `ProductionConversationService`
- `AnswerIdempotencyCoordinator`
- receipt tests

- [ ] RED：生产 `requestToken` 只接受 UUIDv4；测试 helper 不再用 nameUUID 模拟真实请求。
- [ ] RED：fingerprint 绑定规范化请求结构、parent ContextHandle、contentVersion binding，但数据库不保存原始请求。
- [ ] RED：同 token/同 fingerprint/IN_PROGRESS → 409 `REQUEST_IN_PROGRESS` + 可选 retryAfter。
- [ ] RED：同 token/不同 fingerprint 或不同合法 ResumeToken → 409 `IDEMPOTENCY_KEY_CONFLICT`。
- [ ] RED：同 token/COMPLETED → 200 `COMPLETION_RECEIPT`，不再执行 P2/P3、不再创建 Context。
- [ ] RED：首次响应和 Token 同时丢失时，无 Token 重试通过全局 requestToken 找到 receipt，为原 conversation 原子重签 Token，旧 token hash 失效。
- [ ] RED：携带当前合法 Token 的完成重试不重签。
- [ ] RED：重签后旧 Token 在后端立即失效；前端契约要求同一 requestToken 的重试发起后忽略更早 attempt 的迟到响应，后端测试只验证旧 Token 不再可用。
- [ ] GREEN：保留内存 coordinator 只做单实例 live coalescing，持久 receipt 承担跨请求/重启幂等。
- [ ] GREEN：receipt completion payload 加密且只包含公共任务状态、ContextHandle、ContinuationStatus。

Focused verification：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=RequestReceiptServiceTest,ProductionConversationServiceTest,RequestReceiptPostgresIntegrationTest test
```

**完成条件：** 首轮和后续轮次的丢响应都不会重复执行或重复推进 Context。

## 21. Task 14（P3-E）：接入 ResumeToken、Context 解析与不对称故障

**修改：**

- `ConversationAnswerController`
- `ProductionConversationService`
- `ConversationalAgentRuntime`
- `SemanticTurnRequestMapper`
- `ConversationAnswerRequest`

**新增：**

- `ContextReferenceRequest`
- `ConversationRequestContext`
- `ConversationTurnExecutionResult`
- runtime/context integration tests

- [ ] RED：首次请求无 Header 时创建 conversation；后续只从 `X-Conversation-Resume-Token` 读取。
- [ ] RED：`contextReference` 位于 request 顶层，expected type 只有 RECENT_SEMANTIC_TASK/RECOMMENDATION。
- [ ] RED：P2 模型只能读取裁剪 `ConversationContextView`，不能收到 payload、Token 或 handle 密文。
- [ ] RED：Refine/明确连续任务读 Store 失败时，在 Retrieval 前返回 FAILED + `CONTEXT_STORE_TEMPORARILY_UNAVAILABLE`，可重试。
- [ ] RED：不依赖旧 Context 的任务正常执行；完成后写 Store 失败只设置 `PERSISTENCE_UNAVAILABLE`，不改变 EvidenceSupport/AnswerCoverage。
- [ ] RED：Context 不存在/过期/清除/归属错误使用 DEPENDENCY_UNAVAILABLE，不触发 General Model 或历史文本重建。
- [ ] RED：Context mutation 由 validated plan + task outcome + authorized parent 确定性生成，不从答案正文解析。
- [ ] GREEN：Runtime 接受显式 `ConversationRequestContext` 和 request-scoped execution budget；不在领域内部读 servlet Header。
- [ ] GREEN：正常 answer、Context commit 和 receipt completion 按事务边界协调；当前 answer write failure 按不对称策略返回。

Focused verification：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ConversationContextRuntimeIntegrationTest,ConversationAnswerControllerTest,ConversationalAgentRuntimeTest test
```

**完成条件：** P2/P3 已能安全使用服务端 Context，但旧 Portfolio executor 尚未切除。

## 22. Task 15（P3-E）：切换生产 Portfolio executor 与最终执行快照

**修改：**

- `PortfolioSemanticTaskExecutor`
- `SemanticTurnCoordinator`
- `ConversationalAgentConfiguration`
- `PortfolioIntelligenceConfiguration`（拆除旧生产入口）
- `AgentTurnResult`
- response mapper

**新增：**

- `PortfolioExecutionConfiguration`
- `ExecutionDisplayPlanProjector`
- production reachability tests

- [ ] RED：Portfolio executor 依赖 Planner、唯一 Capability、Promotion、Support、Result Policy、Composer；不依赖 PortfolioIntelligence。
- [ ] RED：Fact/Compare/Recommend/Refine 都只执行一个 logical invocation。
- [ ] RED：最终 `agentTurn.execution` 为 `p3-display-v1 + FINAL`，按真实 outcome 投影四阶段状态。
- [ ] RED：P2 `agentTurn.plan` 仍存在且未被 P3 Plan 替换。
- [ ] RED：最终响应不得含 PENDING/IN_PROGRESS、Capability ID、Adapter、SQL、budget、exception 或内部 ID。
- [ ] RED：Spring context 中只有一个 PORTFOLIO SemanticTaskExecutor，类型为 P3 executor；旧 `PortfolioIntelligence` 不再被 runtime 引用。
- [ ] GREEN：P3-E 通过启动 wiring 一次切换，不增加 `portfolio.execution.enabled` 长期双轨开关。
- [ ] GREEN：Rollback 依靠旧发布版本，不在同一版本中保留随机/请求级分流。

Focused verification：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioSemanticTaskExecutorTest,PortfolioExecutionProductionWiringTest,ExecutionDisplayPlanProjectorTest,ConversationalAgentRuntimeTest test
```

**完成条件：** 新 P3 Engine 成为唯一生产 Portfolio 执行路径。

## 23. Task 16（P3-E）：完成公共 DTO、Context API 与来源引用迁移

**修改：**

- `ConversationAnswerResponse`
- `ConversationAnswerBlockResponse`
- `PortfolioRecommendationItemResponse`
- `CompletedTaskResponse`
- `AgentTurnResponse`
- `ConversationAnswerResponseMapper`
- `ConversationAnswerController`
- controller/serialization tests

**新增：**

- `AnswerSuccessResponse` 或等价 sealed-free 显式联合 DTO
- `CompletionReceiptResponse`
- `ConversationResponse`
- `ExecutionDisplayPlanResponse`
- `PublicSourceReferenceResponse`
- `ConversationContextController`
- `ConversationContextSummaryResponse`

- [ ] RED：正常 200 返回 `responseKind=ANSWER`；完成回执返回 `responseKind=COMPLETION_RECEIPT`，二者不能靠空 blocks 区分。
- [ ] RED：所有 answer/context 响应设置 `Cache-Control: no-store`。
- [ ] RED：首次/明确替换时才返回 `conversation.resumeToken`；其余响应不重复回显。
- [ ] RED：`completedTasks[].contextHandle` 只在产生可续接 Context 时出现。
- [ ] RED：Block/Recommendation item 只返回 `sourceReferences`，不返回 claimIds/evidenceIds。
- [ ] RED：GET context：合法可恢复 → 200 AVAILABLE + `p3-context-summary-v1`；不存在/过期/清除/归属失败 → 200 CONTEXT_EXPIRED，无 summary。
- [ ] RED：格式非法 Token → 400 `INVALID_CONVERSATION_RESUME_TOKEN`。
- [ ] RED：DELETE 对任意语法合法 Token 幂等 204；格式非法 400。
- [ ] RED：未知请求字段、超长 handle/token、未知 enum 全部 fail-closed。
- [ ] GREEN：Controller 只做 Header/DTO 映射；Token 和 ContextHandle 不进入 RequestContext diagnostics。
- [ ] REFACTOR：删除 request DTO 中完整 `recommendationContext/referenceContext` 和对应 response DTO。

Focused verification：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ConversationAnswerResponseTest,ConversationAnswerResponseMapperTest,ConversationAnswerControllerTest,ConversationContextControllerTest,P3PublicContractSerializationTest test
```

**完成条件：** 后端公共契约与前端交接文档一致；旧内部 ID 和完整 Context 回传已消失。

## 24. Task 17（P3-F）：迁移 Eval 并删除旧决策链

**修改/新增：**

- 将 `EvalIntelligenceExecutor` 替换为消费真实 P3 seam 的 executor
- 修改 `EvalCliBootstrap`
- 修改 `HttpEvalExecutor/JdkEvalAnswerClient`
- 修改 deterministic grader、metrics、report writer
- 增加 Fact/Compare/Recommend/Refine、fallback、integrity、Context、budget Eval cases
- 更新 architecture/privacy checks

**删除（确认无生产或 Eval 使用后）：**

- `ConversationToolService`
- `ConversationToolPlan`
- `ToolCall`
- `ToolKind`
- `PublicToolResult`
- `PublicToolResultStatus`
- `LocalPublicKnowledgeTools`
- `PublicKnowledgeTools`
- `ConversationalModelPort.planTools`
- `OpenAiCompatibleConversationalModelAdapter.planTools`
- Tool Planning prompt/codec/tests
- `PortfolioIntelligence.resolveTypedTask/tryResolve`、`DefaultPortfolioIntelligence` 和只为旧决策入口存在的 task resolver/validator/context classes
- `PortfolioIntelligenceAnswerAssembler`
- `ConversationalAgentProperties.maxToolCalls/maxToolRounds`

- [ ] RED：architecture test 禁止生产/Eval 引用上述旧符号。
- [ ] RED：Eval 输入只能是 P2 typed task/context；Oracle 不能进入 Planner/Capability 输入。
- [ ] RED：Eval 覆盖 CandidateSet integrity、Promotion、support、recommendation profile、atomic fallback、Context read/write failure、branch CAS、receipt。
- [ ] RED：持久化 Eval 报告不含问题、答案、Token、ContextHandle、内部 ID、Evidence 正文或异常消息。
- [ ] GREEN：Eval 与生产使用同一 `SemanticTaskExecutionContext → P3 executor` seam。
- [ ] GREEN：删除旧类、测试 fixture、Prompt 片段和 Spring beans；不留 deprecated 空壳。

Focused verification：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=SemanticRoutingArchitectureTest,PortfolioExecutionArchitectureTest,EvalIntelligenceExecutorTest,EvalReportJsonWriterTest test
powershell -ExecutionPolicy Bypass -File scripts/architecture-check.ps1
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
```

静态确认：

```powershell
rg -n "ConversationToolService|planTools\(|ToolKind|resolveTypedTask|tryResolve" backend/src/main backend/src/test
```

Expected：除明确的迁移历史文档或负向架构测试字符串外无命中。

**完成条件：** 生产与 Eval 均只使用 P3 seam，旧决策岛物理删除。

## 25. Task 18（P3-F）：同步隐私、状态、部署与最终门禁

**修改：**

- `AGENTS.md`
- `README.md`
- `SECURITY.md`
- `docs/00-文档状态索引.md`
- `docs/08-当前实现状态.md`
- `docs/11-项目演进日志.md`
- `docs/13-Agent对话体验与智能编排改造路线图.md`
- `.env.example`、部署说明和必要脚本
- `scripts/privacy-check.ps1` 及其自测
- `scripts/architecture-check.ps1` 及其自测
- `scripts/verify-release.ps1`（只在需要纳入 Context 集成门禁时修改）

- [ ] 把“页面完全无状态”更新为“问题/答案仍仅页签内存；服务端短期保存加密强类型业务 Context；Token 仅 sessionStorage；24h/7d；随时清除”。
- [ ] 明确 P3 已实现不等于长期记忆、登录、跨设备恢复或完整聊天记录。
- [ ] 记录 PostgreSQL Context Store 生产必需、IN_MEMORY 仅 test/local、无文件/H2/SQLite fallback。
- [ ] 更新当前实现状态时使用真实验证结果，不把未跑的 PostgreSQL/生产环境写成已通过。
- [ ] 更新演进日志只记录能力与边界变化，不写逐步实现流水账。
- [ ] 隐私脚本新增对日志/DTO/DDL/前端制品中的问题、答案、Token、内部 ID 持久化检查。
- [ ] 若前端 Agent 尚未完成 P3 接入，后端只能停在可合并但不可生产切流状态；不得发布不兼容组合。

全量验证：

```powershell
mvn.cmd -f backend/pom.xml test
powershell -ExecutionPolicy Bypass -File scripts/code-quality-check.test.ps1
powershell -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1
powershell -ExecutionPolicy Bypass -File scripts/architecture-check.test.ps1
powershell -ExecutionPolicy Bypass -File scripts/architecture-check.ps1
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.test.ps1
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
```

前端 Agent 合入后再运行原子发布门禁：

```powershell
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
mvn.cmd -f backend/pom.xml package
powershell -ExecutionPolicy Bypass -File scripts/verify-release.ps1
```

**完成条件：** 后端、前端、数据库、隐私和文档契约一致；完整门禁 fresh PASS；才可把 P3 标为完成。

## 26. 必须具备的测试矩阵

### 26.1 领域与 Planner

- Fact/Compare/Recommend/Refine 各一组正常、partial、insufficient、rejected。
- Exact scope 永不扩大；all-published 绑定 contentVersion。
- 排除项、unsupported facet/dimension、stale context、expired allowance。
- 同输入确定性相等；Plan/Bundle/Contribution 不可变。

### 26.2 Retrieval 与 Evidence

- Bundle/PostgreSQL 语义等价。
- Primary success/empty/unavailable/timeout/integrity 的六条状态路径。
- Fallback attempt 原子性和相同 Promotion。
- 未批准 Evidence、孤儿 link、错误版本、非法公开 route、重复 reference code。
- 64/128/16/96/4000 上限的等于、减一、加一测试。

### 26.3 推荐

- GENERAL baseline OR。
- CAPABILITY_MATCH baseline + 每能力必需。
- careerTrack hard filter、audienceRole 不改排序。
- 每 criterion 2 unit 上限。
- 缺 Evidence 不产生负面文案。
- 稳定 tie-break 和 Refine 只收缩。

### 26.4 Context 与安全

- AES-GCM tamper、AAD substitution、key rotation N/N-1、N-2 拒绝。
- Token HMAC rotation、Token/Handle 交叉会话攻击。
- 24h idle、7d absolute、合法访问续期、非法访问不续期。
- 32 Context deterministic prune、16KiB 拒绝。
- 并发 Refine branch + Active CAS。
- clear/expire/prune/read unavailable/write unavailable。
- 数据库 schema 中不存在问题/答案/Evidence 正文字段。

### 26.5 HTTP 与幂等

- ANSWER/COMPLETION_RECEIPT 联合类型。
- 首轮响应丢失和 Token 重签。
- REQUEST_IN_PROGRESS/ALREADY_COMPLETED/KEY_CONFLICT。
- GET available/expired/invalid；DELETE 204 幂等。
- no-store、Header/body/URL/Cookie 边界。
- sourceReferences 有效且内部 ID 消失。
- FINAL DisplayPlan 不含运行中状态或内部信息。

## 27. 切片提交建议

只有用户明确授权提交时才执行，且每次只 stage 当前切片：

```text
docs(p3): 准入阶段三后端实施边界
feat(p3): 建立受限执行领域与规划契约
feat(p3): 建立候选集与证据晋升闭环
feat(p3): 接入唯一作品集检索能力
feat(p3): 建立加密会话业务上下文
feat(p3): 接入阶段三回答与公共契约
refactor(p3): 删除旧工具规划与智能决策链
docs(p3): 同步阶段三实现与隐私边界
```

不要把所有任务压成一个提交，也不要在未获授权时自行提交。

## 28. 暂停与回退条件

遇到以下任一情况停止当前切片，不要自行扩大范围：

- 正式 Bundle 超过冻结安全上限。
- 需要保存问题、答案或 Evidence 正文才能实现某功能。
- 需要任意查询文本、动态工具、第三方网络或第二 capability。
- Context Store 无法在同一事务提交 receipt/Context/Active CAS。
- 前端提出改变公共语义但主 Spec 未修订。
- 旧链仍有未知生产消费者，无法安全删除。
- 完整发布必须长期双轨或按请求 feature flag 才能切换。

回退只通过发布版本回滚；数据库迁移必须向前兼容旧版本读取失败关闭，不能执行 destructive down migration。

## 29. Definition of Done

P3 后端只有同时满足以下条件才算完成：

1. P2→P3 新 seam 是唯一生产 Portfolio 路径。
2. Catalog 只有一个真实 capability，Planner/Promotion/Support/Result Policy 均为确定性闭集。
3. Fact/Compare/Recommend/Refine 全部使用 ValidatedEvidenceBundle 和统一 Composer Facade。
4. `sourceReferences` 完全替代公共 Claim/Evidence ID。
5. Context PostgreSQL、加密、TTL、容量、CAS、clear、receipt 和首轮丢响应恢复通过集成测试。
6. Context 故障的不对称降级符合 Spec，不从聊天文本重建授权。
7. `agentTurn.plan` 与 `agentTurn.execution` 同时正确，后者只有 FINAL snapshot。
8. 旧 Tool Planning 和 PortfolioIntelligence 决策入口物理删除，生产/Eval 均无引用。
9. 权威隐私、当前状态、演进日志和配置文档同步。
10. 后端全量、代码质量、架构、隐私、数据库集成和原子发布门禁 fresh PASS。

本文是执行顺序，不代表任一任务已经完成。每个新开发上下文应从 Git 状态、权威文档和最近一个已通过切片重新确认进度，不能只依据计划中的 checkbox 猜测仓库状态。
