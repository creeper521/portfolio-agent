# Project Recommendation 主体类型契约与讨论续接修复实施计划
<!-- DOCUMENT_STATUS: ACTIVE -->

> 日期：2026-08-28
> 当前分支：`master`（用户未授权创建、切换或提交分支）
> 批准设计：`docs/superpowers/specs/2026-08-28-project-recommendation-subject-kind-contract-design.md`
> 动态账本：`A2-121`
> Guardian 授权：`APPROVED_LEVEL_3_REPLACEMENT`
> 基线提交：`d5666b2`
> 外部调用：用户已明确授权在确定性门通过后执行真实 Qwen API 与 Project Discussion 原路径；凭据必须来自仓库外 secret file

## 1. 交付目标

修复 Project Recommendation 在检索层被扩大成全部 Portfolio Subject 的问题，使下列链路成为唯一生产行为：

```text
PORTFOLIO_RECOMMEND
→ allowedSubjectKinds={PROJECT}
→ Bundle/PostgreSQL 在评分与 limit 前过滤
→ CandidateSubject(PROJECT)
→ ValidatedEvidenceUnit(PROJECT)
→ Project-only Recommendation
→ 响应真实 discussionAction
→ CONTINUE ENTER_RESULT
→ ANSWER + activeDiscussion=ACTIVE
```

同时完成：

- 提升现有 `PortfolioSubjectKind`，不新增第四套枚举；
- `GoalSubjectReference.Kind.RESULT` 在 Invocation 前解析，未解析时 typed fail-closed；
- Bundle 按 eligible Project documents 重算 BM25 N、DF、avgdl；
- PostgreSQL FTS、Vector、EXACT 和 broad fallback 保持同一 kind 合同；
- selection benchmark 内部 JSON 显式迁移；
- 0 Project 与 PARTIAL 终态分离；
- Promotion/ResultFactory 整批拒绝越界类型；
- IN_MEMORY/PostgreSQL 使用公开响应的真实 action 完成生命周期回归；
- 保持 coordinator、公开错误、Context schema、隐私与资源隐藏边界不变；
- 完成已授权真实 Qwen L4 与 `PROJECT_DISCUSSION` packaged lane。

## 2. 冻结决策

1. `PORTFOLIO_RECOMMEND` 永久是 Project-only；Case 推荐未来另立 capability/context。
2. `AuthorizedSubjectScope` 与 `allowedSubjectKinds` 分离；前者授权具体主体，后者限制本 operation 可消费类别。
3. 现有 `PortfolioSubjectKind` 从 PostgreSQL selection 包提升到 capability 层。
4. `RESULT` 没有 Portfolio kind 映射；continuation 必须先解析。
5. 类型过滤发生在所有评分、有限候选窗口、RRF 与 requestedSize 截断前。
6. Bundle BM25 使用 eligible corpus 的 N、DF、avgdl，Case 不影响 Project 分数与排序。
7. ResultFactory 只验证，不静默丢弃 Case。
8. 0 个合法且有 Evidence 的 Project 返回 `NO_RESULT/NO_SUPPORTED_RESULT`；1..N-1 才返回 `PARTIAL/REQUESTED_SIZE`。
9. 首轮不修改 Recommendation Context 持久化 shape；历史混合 Context 继续 fail-closed。
10. 外部 continuation 错误继续统一，内部可增加闭集原因。
11. 不修改前端公开合同；前端继续原样转发 backend-owned action。
12. 回退只使用整体 Git/JAR 版本，不新增 feature flag、双栈或兼容桥。

## 3. 并行执行纪律

所有 Agent 共享同一工作树。文件所有权必须互斥，不提交 Git；主 Agent 负责接口冻结、集成、冲突处理、全量门、真实 API 和最终文档。

| Lane | 所有者 | 独占生产文件面 | 独占主要测试面 |
|---|---|---|---|
| A Core contract | Agent A | `PortfolioSubjectKind` 移动、`PortfolioEvidenceInvocation`、`PortfolioInvocationFactory`、`CandidateSubject`、`PortfolioCandidateSet`、`ValidatedEvidenceUnit`、`EvidencePromotionValidator`、`PortfolioEvidenceCapability` | Invocation、candidate、promotion、capability tests 与通用 fixture call sites |
| B PostgreSQL | Agent B | `SelectionTarget`、selection row/candidate/imports、`JdbcPostgresSelectionQuery`、`PostgresHybridCandidateRetriever`、`JdbcPostgresKnowledgeQuery`、`PostgresPortfolioRetrieverAdapter`、benchmark DTO/fixture | PostgreSQL selection/query/adapter integration 与 benchmark loader/CLI/evaluator tests |
| C Bundle | Agent C | `BundlePortfolioRetrieverAdapter` 及仅为 eligible BM25 所需的 Bundle 内部 helper | `PortfolioRetrieverAdapterTest` 的 Bundle、BM25 corpus isolation 与 fallback tests |
| Integration | 主 Agent | `PortfolioSemanticResultFactory`、必要的 typed failure 映射整合、lifecycle/store/diagnostic integration、文档与 scripts | ResultFactory、真实 action lifecycle、IN_MEMORY/PostgreSQL round-trip、安全负例与全量门 |

规则：

- Agent 不得修改其他 Lane 的独占文件；发现遗漏先通知主 Agent。
- 共享构造器签名由 Lane A 冻结；B/C 可在各自 adapter 中同步调用，但不得改 Core 类型。
- 主 Agent 在 PRE_FIX 探针完成或明确记为 NOT_CAPTURED 前，不允许任何生产 Java 修改。
- 每个 Lane 先提交 RED 测试证据，再写 GREEN；不能先改生产代码后补测试。
- 不使用 `var`、Lombok、route 类型推断或异常 message 解析。
- 不记录用户文本、Prompt、Provider body、密钥、Token、handle、主体 ID 或 Evidence 内容。
- 任一 Agent 遇到现有用户修改、文件冲突或超出 spec 的公开合同需求时立即停下。

## 4. Task 0：文档、账本与 PRE_FIX 证据

### 4.1 文档治理

修改/新增：

- `docs/superpowers/specs/2026-08-28-project-recommendation-subject-kind-contract-design.md`
- 本计划
- `docs/00-文档状态索引.md`
- `docs/15-Agent 2.0真实交互问题清单与修复边界.md`
- `scripts/documentation-check.ps1`

- [x] 用户已批准设计、并行实施与确定性测试后的真实 Qwen 外呼。
- [x] spec 审查的 B1—B3、M1—M3 已吸收。
- [x] spec 标记 `APPROVED`，plan 标记 `ACTIVE`，checker 与索引双向登记。
- [x] A2 水位提升为 121，A2-121 事实/推断/修复边界/专属门完整。
- [x] `scripts/documentation-check.test.ps1` 与生产 documentation check 通过。
- [x] untracked 新文档使用独立 whitespace 检查，不用普通 `git diff --check` 冒充覆盖。

### 4.2 PRE_FIX 当前发布数据探针

生产代码修改前，以真实当前发布数据运行 production-shaped PostgreSQL selection/query adapter。Provider 不参与；HYBRID 如需 embedding，使用现有本地实现或确定性 stub。

仅允许输出：

```text
candidateKinds=<closed kind counts/order>
selectedKinds=<closed kind order>
rejection=<closed reason>
```

禁止输出标题、stable ID、release ID、route、handle、Token、查询文本或 Evidence。

- [x] 若环境可用，记录 `PRE_FIX_CURRENT_DATA_CAPTURED` 与匿名类型证据。
- [ ] 若环境不可用或不可比较，记录 `PRE_FIX_CURRENT_DATA_NOT_CAPTURED` 和闭集原因。
- [x] 不把近似 SQL、固定 fixture 或先前人工 probe 伪装成当前 production-shaped 证据。

2026-08-28 PRE_FIX 结果（生产代码修改前）：

```text
PRE_FIX_CURRENT_DATA_CAPTURED
candidateKinds=PROJECT:3,CASE:18;order=CASE,PROJECT,CASE,PROJECT,PROJECT,CASE,CASE,CASE,CASE,CASE,CASE,CASE,CASE,CASE,CASE,CASE,CASE,CASE,CASE,CASE,CASE
selectedKinds=PROJECT,CASE
rejection=RECOMMENDATION_SUBJECT_NOT_PUBLIC_PROJECT
strategy=HYBRID;embedding=DETERMINISTIC_STUB_512
```

探针使用当前真实公开 PostgreSQL 数据、`JdbcPostgresKnowledgeQuery`、
`PostgresPortfolioRetrieverAdapter`、Evidence 晋级与
`PortfolioSemanticResultFactory`；Provider 未参与。一次性测试源已删除。输出仅含闭集主体类型、
计数、顺序、策略和拒绝原因，不含标题、主体/发布标识、route、handle 或 Evidence 内容。

## 5. Task 1：RED 测试

### 5.1 Core RED

- Recommendation 只能构造 `{PROJECT}`；空集或 `{PROJECT,CASE}` 拒绝。
- Fact/Compare 从 typed EXACT scope 推导 kinds。
- unresolved `RESULT` 不能进入 Invocation。
- Candidate kind 必填，EXACT `(id,kind)` 必须一致。
- Candidate→ValidatedEvidence kind 原样传播；越界整批拒绝。
- typed integrity failure 不映射为 `INPUT_REJECTED`。

### 5.2 Bundle RED

固定 Case 高分、Project 次高的竞争语料：

```text
Case A=100, Case B=90, Project C=80, Project D=70, requestedSize=2
```

断言：

- CandidateSet 只有 C、D；
- Case chunks 不进入 eligible ranking；
- N、DF、avgdl 只来自 eligible Project documents；
- 添加任意数量、词频和长度的 Case 后，Project BM25 分数与排序不变；
- PostgreSQL fallback 到 Bundle 仍保持 Project-only。

### 5.3 PostgreSQL RED

- FTS `limit=2`：Case 高分也返回两个 Project。
- Vector `limit=2`：确定性 embedding 得到同样结果。
- HYBRID/RRF 结果只有 Project。
- 超过 `MAX_SUBJECTS` 的 Case 不挤出 Project。
- broadTarget 放宽 career/capability 时保留 `{PROJECT}`。
- EXACT 实际 kind 与 scope pair 不一致 fail-closed。
- SQL 仍锁定 release、VERIFIED claim 和 APPROVED Evidence。

### 5.4 Benchmark JSON RED

- 每个 `SelectionTarget` fixture 必填非空 `allowedSubjectKinds`。
- 原通用 benchmark 显式 `[PROJECT,CASE]`，原 acceptable sets 继续合法。
- Project Recommendation benchmark 显式 `[PROJECT]`，acceptable sets 只有 Project。
- 缺失/null/空/未知 kind 反序列化失败。
- loader、CLI、evaluator、round-trip 保留字段。

### 5.5 Result/lifecycle RED

- 混合 Validated bundle 由 ResultFactory 合同失败，不静默过滤。
- 0 Project 返回 `Optional.empty → NO_RESULT/NO_SUPPORTED_RESULT`，无 Context/action。
- 1 Project/请求 2 返回 `PARTIAL/REQUESTED_SIZE`。
- 使用公开 Recommendation 响应里的实际 action 执行 `ENTER_RESULT`；旧实现复现失败。
- 历史混合 Context 仍被 coordinator 拒绝。

## 6. Task 2：GREEN 实现

### 6.1 Core contract

- 将现有 `PortfolioSubjectKind` 移至 capability 包并修正 imports。
- Invocation 保存 immutable non-empty `allowedSubjectKinds`。
- Factory 对 Recommendation 固定 `{PROJECT}`，其他 Portfolio task 从 typed scope 推导。
- `PROJECT/CASE` 穷尽映射；`RESULT` 无映射且在 Invocation 前 fail-closed。
- CandidateSubject/ValidatedEvidenceUnit 增加必填 kind。
- PortfolioCandidateSet 以 `(id,kind)` 验证 EXACT，不再以 route 作为类型权威。
- Promotion 校验 allowed set 并原样传播 kind。
- Promotion 合同错误进入 typed integrity/capability failure。

### 6.2 PostgreSQL

- SelectionTarget 增加必填 allowed kinds。
- FTS/Vector `eligible` CTE 通过参数化 text array 在 `LIMIT` 前过滤。
- EXACT 查询或紧邻完整性边界校验 `(id,kind)`。
- broadTarget、vector fallback、backend fallback 不放宽 kinds。
- SQL 参数绑定稳定排序且不接受空数组。
- Adapter 将 SelectionCandidate kind 传入 CandidateSubject。
- 同期迁移 benchmark fixture/schema/CLI/evaluator。

### 6.3 Bundle

- `AnswerSubjectType` 显式映射到 capability kind。
- 合并公开主体后、eligible claims/chunks 与任意评分前过滤 allowed kinds。
- keyword ranking 从 eligible documents 重算 N、DF、avgdl；复用每文档 TF/length。
- Vector/RRF 只处理 eligible chunks。
- ALL_PUBLISHED authorization 与 allowed kind 分别校验。

### 6.4 ResultFactory 与 continuation

- Recommendation 前断言 invocation 为 `{PROJECT}`、所有 unit 为 Project、同 subject kind 一致。
- 类型违规整批失败；0 unit 保留既有 Optional.empty。
- PARTIAL 只覆盖非空合法 Project 集合。
- coordinator 不放宽完整 Context 校验。
- 如增加内部 reason，使用 typed enum；外部仍统一 `DISCUSSION_CONTEXT_UNAVAILABLE`。

## 7. Task 3：生命周期与 State parity

新增独立生命周期回归，不能只调用 coordinator：

```text
deterministic Goal/Plan/Outcome
→ real PortfolioSemanticResultFactory
→ real ContextMutationPlanner/PublicAgentTurnProjector
→ settle
→ read actual response discussionAction
→ send CONTINUE ENTER_RESULT
→ ANSWER
→ activeDiscussion=ACTIVE
```

IN_MEMORY：完整生命周期、Context 可查、item membership 与 action 一致。

PostgreSQL/Testcontainers：加密 settlement、conversation+handle+TTL 回读、实际 action round-trip。

安全负例：unknown/cross-conversation/expired handle、release mismatch、wrong context type、item 不属于 Context、历史混合 Context、unpublished project、伪造 subject、pointer generation race。

## 8. Focused 验证

Core/semantic：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioInvocationFactoryTest,PortfolioEvidenceCapabilityTest,EvidencePromotionValidatorTest,PortfolioSemanticResultFactoryTest,PortfolioSemanticSubjectKindContractTest test
```

Retriever/benchmark：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioRetrieverAdapterTest,BundlePortfolioRetrieverSubjectKindTest,JdbcPostgresSelectionQueryTest,JdbcPostgresSelectionQueryIntegrationTest,PostgresHybridCandidateRetrieverTest,JdbcPostgresKnowledgeQueryTest,PortfolioSelectionBenchmarkFixtureTest,PortfolioSelectionBenchmarkCliTest,PortfolioSelectionBenchmarkCliBoundaryTest,PortfolioSelectionBenchmarkEvaluatorTest test
```

Lifecycle/State：

```powershell
mvn.cmd -f backend/pom.xml -Dtest=AgentTurnLifecycleContinuationTest,ProjectDiscussionCoordinatorTest,DiscussionContinuationDiagnosticsTest,ProjectRecommendationDiscussionLifecycleTest,JdbcAgentStateStoreIntegrationTest test
```

若新增测试类名不同，实施时更新计划，不能以命令漏跑代替通过。

## 9. POST_FIX 当前发布数据探针

在与 PRE_FIX 相同 release、Invocation、strategy 和数据源上重放：

```text
candidateKinds=[PROJECT...]
selectedKinds=[PROJECT...]
```

- [x] 条件按同一连续运行的本地 PostgreSQL 活动数据源可比，已记录 `POST_FIX_CURRENT_DATA_CAPTURED` 并完成匿名对照；PRE_FIX 未保存 release ID，因此不把该连续性表述成密码学级 release 身份证明。
- [ ] 若不可比，记录 `POST_FIX_CURRENT_DATA_NOT_COMPARABLE`；固定 RED/GREEN 仍是长期回归证据。
- [x] 一次性探针源码、class 与 classpath 已删除，没有形成长期 fixture 或公开内容快照副本。

## 10. 全量与发布前门

```powershell
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
```

门必须按实际结果报告。现有无关开放项可能使 canonical release/architecture gate 保持非 PASS；不得为关闭 A2-121 篡改其他账本或机器状态。

## 11. 已授权真实 Qwen API 验证

前置条件：

- 确定性 focused、backend full、privacy、package 与 PostgreSQL State 门通过；
- 仓库外 Qwen secret file 存在且只含批准环境变量；
- 不打印 secret 路径、内容、Prompt、用户文本、Provider body、Token、handle 或主体 ID；
- 使用固定脱敏场景和闭集聚合报告。

### 11.1 L4 canary

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-agent-behavior-audit.ps1 `
  -Lane L4 -RequireLiveProvider -LiveModelRef qwen-3-7-flash `
  -ProviderSecretFile <outside-repository-secret-file> `
  -ContextMode POSTGRESQL
```

### 11.2 Project Discussion 原路径

在同一受控 PowerShell 进程中加载仓库外 secret 环境后执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-jar-e2e.ps1 `
  -Lane PROJECT_DISCUSSION -RequireLiveProvider `
  -LiveModelRef qwen-3-7-flash -ContextMode POSTGRESQL
```

必须实际覆盖：

```text
Qwen 形成 Project Recommendation Goal
→ 服务端只返回 Project items
→ 浏览器读取真实 discussionAction
→ ENTER_RESULT
→ ANSWER + activeDiscussion=ACTIVE
→ discussion 内再执行一个 locked-project 问题
```

真实调用失败、超时、限流、schema/semantic 拒绝或凭据缺失均按 `FAIL/BLOCKED` 报告，不重试到另一模型，不把 deterministic PASS 冒充 live PASS。

## 12. 文档收口

只有生产修复、原失败路径、focused/full、Memory/PostgreSQL、packaged 与真实 Qwen 门全部满足后：

- 从 docs/15 的 overview/body/专属门删除 A2-121，不留完成归档；水位保持 121；
- 更新 `docs/08-当前实现状态.md` 的实际能力/限制；
- 在 `docs/11-项目演进日志.md` 只记录行为、边界与链接，不写测试数量、哈希或提交元数据；
- 本计划切换为 `HISTORICAL`，从 active checker 移除；批准 spec 保持设计依据或按后续治理决定；
- `docs/agent-architecture-status.json` 只写新鲜闭集证据，overallStatus 因其他开放项继续诚实保持 `IN_PROGRESS`；
- 再运行 documentation、architecture、privacy 与 release 门。

若任一必需门未通过，A2-121 与本计划保持 OPEN/ACTIVE，文档只记录实际阻塞状态。

## 13. 固定禁止项

实施者不得：

- 在 ResultFactory 或 coordinator 静默删除 Case；
- 只修 PostgreSQL 或只修 Bundle；
- 在 SQL/RRF/requestedSize limit 后才过滤 kind；
- 用 route 前缀、subjectId 前缀或 Prompt 作为类型权威；
- 为 `SelectionTarget` 提供隐式全类型默认；
- 把 0 Project 伪装成 PARTIAL；
- 让 Case 进入 ProjectDiscussionContext 或 switch candidates；
- 修改公开 action/Context/API shape 来绕过根因；
- 持久化 subject kind 之外的新访客/Provider 数据；本批连 Context kind 也不新增；
- 用裸异常 message 分类合同错误；
- 运行未授权的其他 Provider、跨模型 fallback 或开放式用户输入；
- 修改、删除、reset、restore、stage、commit 或 push 用户无关变更。

## 14. 完成定义

本计划只有同时满足以下条件才可完成：

1. Spec/plan/索引/checker/账本状态一致且文档门通过。
2. PRE_FIX 在生产修改前取得或诚实记录 NOT_CAPTURED。
3. Project-only kind 约束在 Bundle/PostgreSQL 全部有限窗口前执行。
4. Bundle eligible BM25 corpus isolation 与 PostgreSQL filter-before-limit 负例通过。
5. Benchmark JSON 显式迁移，无隐式默认。
6. Candidate→Evidence 类型不可变，合同越界整批 fail-closed。
7. 0-result、PARTIAL、FULL 终态矩阵通过。
8. 真实响应 action 在 IN_MEMORY/PostgreSQL 均进入 active discussion。
9. 所有安全负例保持拒绝且外部不泄露原因。
10. Focused、full、package、privacy、documentation、architecture/release 门按实际状态记录。
11. 已授权 Qwen L4 与 PROJECT_DISCUSSION 原路径取得新鲜 PASS。
12. A2-121 按删除规则关闭，docs/08/docs/11/机器状态诚实同步。
13. Git diff 只有本问题范围；未 commit、未 push，除非用户另行明确授权。

## 15. 执行记录（2026-08-28）

### 15.1 实现与匿名探针

- Core、Bundle、PostgreSQL 三条并行 lane 已完成 RED/GREEN；未 commit、未 push。
- PRE_FIX 当前数据：`candidateKinds=PROJECT:3,CASE:18`，入选类型为 `PROJECT,CASE`，下游拒绝闭集为 `RECOMMENDATION_SUBJECT_NOT_PUBLIC_PROJECT`。
- POST_FIX 当前数据：候选类型为 `PROJECT,PROJECT,PROJECT`，入选类型为 `PROJECT,PROJECT`，`rejection=NONE`；前后均为 `HYBRID + DETERMINISTIC_STUB_512`。
- Bundle 在 eligible corpus 上重算 BM25；PostgreSQL FTS/Vector 在各自 limit 前过滤 kind；EXACT 保留实际 kind，并在相邻授权边界校验 `(subjectId, kind)`。
- 真实响应 action 的 IN_MEMORY 生命周期测试已进入 `ANSWER + activeDiscussion=ACTIVE`；PostgreSQL State 由 focused Testcontainers 与 packaged E2E 覆盖。

### 15.2 已通过门

- 原主体类型合并 focused：97 tests，0 failure/error/skip；Qwen v8 fixed-flat Goal v3 的 schema/compiler/transport/Prompt focused 为 118 tests，0 failure/error/skip。
- backend full 与 clean package：1327 tests，0 failure/error，4 skipped；可执行 JAR 生成成功。
- frontend：567 tests 通过，type check 与 production build 通过。
- code quality、documentation、生产源码 privacy 及其 checker 自测均通过。
- 本地 PostgreSQL public/governance/context verify 通过；packaged PostgreSQL API/browser E2E 通过，浏览器 lane 为 8 passed、8 expected skipped，进程与环境恢复。
- 已授权 Qwen `PROJECT_DISCUSSION` 专用聚合门通过：Recommendation 为 COMPLETE、2 个 Project、`ENTER_RESULT/ROUTE_IN_CONTEXT/EXIT_CONTEXT` 全部成功、active discussion 创建并清除、锁定主体与候选范围保持不变；三次 Provider 路由延迟均为 `LT_5S`。
- 同一 JAR 的项目讨论桌面/移动 Playwright 为 6 passed；真实 Provider、PostgreSQL、浏览器进程与环境均已恢复。

### 15.3 如实保留的未完成项

- `agent-architecture-status.ps1` 自身通过，但 canonical `overall=IN_PROGRESS`，不因 A2-121 篡改其他开放状态。
- `verify-release.ps1` 的改动相关构建、测试与源码/JAR privacy 子门均通过；最终 repository risk-artifact scan 因既有 `rag-glm-stdout.log` 的 3 条 internal-hostname 命中失败。该本地日志未被删除或改写。
- 用户已提供仓库外 Provider secret file，并明确批准真实 Qwen 外呼以及公开项目材料/合成提示的数据边界；凭据、Prompt、Provider body、Token、handle 与主体 ID 均未写入文档或永久产物。
- Qwen L4 如实失败：第 1 轮 Goal direct/two-turn 分别为 2/5、2/5，第 2 轮为 0/5、2/5；第 1 轮 General 的 CONCISE/STANDARD/DETAILED 均为 0/3，CONVERSATIONAL 为 3/3。第二轮 General 在门结论已确定后停止，未以重复调用刷绿。该 FAIL 属于 Goal/General 整体稳定性，不能被专用 `PROJECT_DISCUSSION` PASS 替代。
- Qwen v8 按用户明确指令进入 selectable/default catalog；Goal 使用无 `$ref/oneOf/allOf` 的固定扁平 `goal.provider-draft.v3`，服务端只投影 decision/goalKind 所属槽位，并对数组/整数 carrier 严格一次解码后继续执行既有 `goal.proposal.v5` canonical。General 仍为 provider v4/application v3，未取得 F4/F5 或 READY 证据。
- 因 L4 必需门未通过，本计划保持 `ACTIVE`，A2-121 保持 `OPEN`；docs/08、docs/11 与机器状态只记录当前事实，不执行完成态删除。

## 16. 提交与回退

2026-09-01，用户明确授权在完成验证后整理全部改动、使用中文提交并 push。提交必须按逻辑拆分，任何未通过外门仍在文档中保持开放，不得通过提交文案改写结果。

建议中文提交主题：

```text
fix(agent): 收紧项目推荐主体类型并恢复讨论续接
fix(model): 发布 Qwen v8 固定扁平 Goal 合同
test(agent): 补齐真实项目讨论浏览器验收
docs(agent): 同步项目推荐与 Provider 验证状态
```

回退只通过整个 Git commit、已验证 JAR 或部署版本完成，不保留运行时双栈或 feature flag。
