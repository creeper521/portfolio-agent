# Project Recommendation 主体类型契约与讨论续接修复设计
<!-- DOCUMENT_STATUS: APPROVED -->

> **日期：** 2026-08-28
> **状态：** APPROVED，用户已批准进入独立计划与实现；设计批准不等于实现完成
> **适用仓库：** `D:\code\agent`
> **范围：** `PORTFOLIO_RECOMMEND` 主体类型约束、Bundle/PostgreSQL 检索一致性、Evidence 类型传播、Recommendation → Project Discussion 续接、内部诊断与确定性回归
> **外部调用：** 用户已明确授权在确定性门通过后执行真实 Qwen API 与 Project Discussion 原路径；凭据必须来自仓库外 secret file

## 1. 文档目的

当前 Agent 可以生成 Recommendation 卡片及 backend-owned `ENTER_RESULT` action，但在真实本地交互中，点击“与我讨论”可能返回：

```text
DISCUSSION_CONTEXT_UNAVAILABLE
```

初始现象容易被理解为 Recommendation Context 没有写入、Store 丢失或前端没有正确回传 action。代码追踪、状态链路核对与确定性人工探针已经排除这些方向，并将根因收敛为一个跨层契约错误：

> `PORTFOLIO_RECOMMEND` 在产品和续接层表示“项目推荐”，但检索层把候选范围扩大成了全部公开 Portfolio 主体，即 `Project + Case`。

本文冻结该问题的产品语义、领域类型、检索约束、纵深校验、兼容边界、错误分类、测试矩阵和 Exit Gate。经用户批准后，应另行编写独立实施计划，并按 TDD 顺序执行；不得把本文直接当作实现完成证明。

## 2. 结论摘要

故障链路如下：

```text
PORTFOLIO_RECOMMEND
→ 无显式主体
→ AuthorizedSubjectScope.ALL_PUBLISHED
→ Bundle/PostgreSQL 同时产生 PROJECT 与 CASE 候选
→ Recommendation 排名不检查主体类型
→ 混合主体进入 Recommendation Context
→ ProjectDiscussionCoordinator 要求全部候选属于 currentPublicProjectIds
→ 任一 CASE 导致整个 Context 被拒绝
→ AgentTurnLifecycleService 对外折叠为 DISCUSSION_CONTEXT_UNAVAILABLE
```

产品合同必须修正为：

```text
PORTFOLIO_RECOMMEND
→ allowedSubjectKinds = {PROJECT}
→ 检索后端在评分、候选窗口和 limit 前过滤
→ 只对 PROJECT 排名并执行 requestedSize 截断
→ CandidateSubject 与 ValidatedEvidenceUnit 保留正式 subjectKind
→ ResultFactory 验证合同，不静默修补
→ Recommendation Context 天然只包含 Project
→ ProjectDiscussionCoordinator 继续严格校验
```

这不是 Store、前端或 Qwen 的根因。Qwen 可能影响 Goal 与推荐约束，但候选主体、Recommendation item、Context handle 和 discussion action 均由服务端权威生成。

## 3. 证据状态与认识边界

### 3.1 已确认事实

1. `PORTFOLIO_RECOMMEND` 当前不要求显式主体。
2. `PortfolioInvocationFactory` 在无主体时构造 `ALL_PUBLISHED`。
3. Bundle 当前合并 `content.getProjects()` 与 `content.getCases()` 后共同检索。
4. PostgreSQL selection 当前保留 `PROJECT/CASE` 两种主体，FTS 与 Vector 查询均在候选窗口内截断。
5. `PortfolioSemanticResultFactory` 只按约束命中、Evidence 多样性与 subjectId 排名，不检查主体类型。
6. 当前 `CandidateSubject` 与 `ValidatedEvidenceUnit` 均不保留正式主体类型；类型在中游丢失。
7. `ProjectDiscussionCoordinator` 要求 Recommendation Context 中的全部候选均为当前公开 Project。
8. 生命周期层将 coordinator 的内部拒绝统一映射为 `DISCUSSION_CONTEXT_UNAVAILABLE`。
9. 确定性人工探针已经证明：

```text
Project + Project → ENTER_RESULT 成功，activeDiscussion=ACTIVE
Case + Project    → ENTER_RESULT 失败，DISCUSSION_CONTEXT_UNAVAILABLE
```

10. Recommendation Context 在 IN_MEMORY 生命周期中能够被保存并按返回 handle 找到；静态代码链也表明 PostgreSQL Store 在 settlement 事务中写入 Context。

### 3.2 尚未拥有的历史证据

原始截图对应的那一次失败响应没有保存非敏感 `selectedKinds` trace，因此无法事后百分之百证明该历史响应一定是 `Case + Project`。实现前可以通过真实当前发布数据的只读适配器探针证明“当前代码与当前数据能够复现混合选择”，但该证据严格表示可复现事实，不应伪装成对历史单次响应的追溯证明。

允许输出的探针结构仅限：

```text
candidateKinds=[CASE, PROJECT, ...]
selectedKinds=[CASE, PROJECT]
rejection=RECOMMENDATION_CANDIDATE_NOT_CURRENT_PUBLIC_PROJECT
```

不得输出标题、stable ID、Context handle、Token、用户文本、Prompt、Evidence 内容或 Provider 原始响应。

## 4. 设计目标

本设计必须实现：

1. 将 `PORTFOLIO_RECOMMEND` 冻结为 Project Recommendation，而不是任意 Portfolio Subject Recommendation。
2. 让主体类型约束在候选评分与所有有限窗口、SQL `LIMIT`、RRF 融合和最终 `requestedSize` 截断之前生效。
3. 让 Bundle 与 PostgreSQL 遵守同一 capability 合同。
4. 使用已有 `PortfolioSubjectKind` 作为 capability 层正式类型，不再依赖公开路由猜测类型，也不新增第四套主体枚举。
5. 让类型从 Invocation 贯穿 `CandidateSubject → ValidatedEvidenceUnit`，晋级过程不得丢失或改变类型。
6. 让 ResultFactory 与 ProjectDiscussionCoordinator 保持纵深防御，不以静默过滤掩盖上游合同退化。
7. 保持现有公开 API、Recommendation Context 持久化 shape 和 Store 安全边界不变。
8. 使用响应中实际返回的 action 完成端到端 `ENTER_RESULT` 回归。
9. 对 IN_MEMORY 与 PostgreSQL 状态后端均建立可重复证据。
10. 保持未知 handle、跨会话、错误 item、release 不一致、未发布项目和历史混合 Context 的 fail-closed 行为。

## 5. 非目标

本批次明确不做：

- 不让 Project Discussion 接受 Case；
- 不新增 Case Recommendation 或 Case Discussion Context；
- 不修改前端卡片合同或让前端判断主体类型；
- 不在 `ENTER_RESULT` 时删除 Case 或只校验当前点击项；
- 不依赖 Prompt 告诉模型“只推荐项目”；
- 不通过 `/projects/`、`/cases/` 路由前缀建立新的正式类型权威；
- 不修改或迁移 `ContinuationContext.Recommendation` 持久化结构；
- 不修复历史混合 Context；历史错误 Context 继续 fail-closed 并按 TTL 自然过期；
- 不改变外部 `DISCUSSION_CONTEXT_UNAVAILABLE` 的资源隐藏语义；
- 不在本 spec 阶段运行真实 Qwen、提交 Git commit 或推送远端。

## 6. 领域语义与不变量

### 6.1 正式主体类型

仓库已存在：

```java
enum PortfolioSubjectKind {
    PROJECT,
    CASE
}
```

但它目前位于 PostgreSQL selection 实现包。实现时应将该现有类型提升到 capability 层，例如：

```text
com.portfolio.agent.turn.capability.portfolio.PortfolioSubjectKind
```

这属于移动并提升现有类型，不是再创建一个新枚举。以下边界类型继续存在，但必须显式转换：

```text
GoalSubjectReference.Kind.PROJECT → PortfolioSubjectKind.PROJECT
GoalSubjectReference.Kind.CASE    → PortfolioSubjectKind.CASE
AnswerSubjectType                  → PortfolioSubjectKind
PostgreSQL subject_kind text       → PortfolioSubjectKind
```

禁止跨层使用 `Enum.valueOf` 假设未来名称永远一致；应在边界使用穷尽 `switch`，新增枚举值时编译失败并要求显式决定。

`GoalSubjectReference.Kind.RESULT` 是服务端续接路径内部对既有结果项的间接引用，不是 Portfolio 聚合主体，因此没有 `PortfolioSubjectKind` 映射。它必须在进入 `PortfolioEvidenceInvocation` 前由可信 continuation 路径解析为实际 `PROJECT` 或 `CASE` 引用；如果未完成解析，planning/invocation 边界必须以 typed internal contract rejection fail-closed。禁止把 `RESULT` 默认映射为 Project、Case 或加入 `allowedSubjectKinds`，也禁止依赖 route 推断。模型提案继续不得产生 `RESULT`。

### 6.2 授权与业务候选约束

两个概念必须保持分离：

```text
AuthorizedSubjectScope
回答：本次调用可以访问哪些具体主体？

allowedSubjectKinds
回答：本次 operation 可以把哪些类别作为候选？
```

例如：

```text
scope = ALL_PUBLISHED
allowedSubjectKinds = {PROJECT}
```

含义是“可以读取当前发布中获准的公开内容，但本次推荐候选只能是 Project”。`allowedSubjectKinds` 不得塞入 `AuthorizedSubjectScope`，也不得被当作授权范围替代品。

### 6.3 Invocation 不变量

`PortfolioEvidenceInvocation` 增加不可变 `Set<PortfolioSubjectKind> allowedSubjectKinds`，构造期必须满足：

1. 集合非 null、非空并执行防御性复制；
2. `PORTFOLIO_RECOMMEND` 必须恰好为 `{PROJECT}`；调用方不得放宽为 `{PROJECT, CASE}`；
3. EXACT scope 中每个声明主体类型必须属于 `allowedSubjectKinds`；
4. `PORTFOLIO_FACT` 与 `PORTFOLIO_COMPARE` 的允许类型由其 typed subject references 推导；是否允许混合比较继续服从现有 Goal 合同，本批次不扩义；
5. 主 backend、fallback backend、约束放宽重试和检索策略降级必须保留同一个 `allowedSubjectKinds`；
6. 空集合、未知类型或 Recommendation 非 Project 集合均在构造期 fail-closed。
7. `GoalSubjectReference.Kind.RESULT` 不可进入 Invocation；续接路径必须先完成实际主体解析，未解析的 RESULT 属于上游 typed contract violation。

`PortfolioInvocationFactory` 是从 SemanticTask 生成该集合的唯一生产入口。测试便利构造器不得通过默认 `{PROJECT, CASE}` 掩盖缺失约束；如保留重载，只能按 task type 和 typed scope 安全推导。

## 7. 目标数据流

```text
SemanticTask(PORTFOLIO_RECOMMEND)
→ PortfolioInvocationFactory
→ PortfolioEvidenceInvocation(
      subjectScope=ALL_PUBLISHED,
      allowedSubjectKinds={PROJECT})
→ Bundle or PostgreSQL Retriever
→ CandidateSubject(subjectKind=PROJECT)
→ EvidencePromotionValidator
→ ValidatedEvidenceUnit(subjectKind=PROJECT)
→ PortfolioSemanticResultFactory contract assertion
→ Recommendation(selected PROJECT units)
→ ContinuationContext.Recommendation(selectedResults)
→ backend-owned discussionAction
→ CONTINUE ENTER_RESULT
→ ProjectDiscussionCoordinator strict public-project validation
→ ProjectDiscussionContext
→ activeDiscussion=ACTIVE
```

每一层只收紧或验证约束，不得放宽、重新猜测或丢失类型。

## 8. Bundle 检索合同

Bundle 当前先把 Project 与 Case 合并进 `knowledge`，再建立 eligible claims/chunks、执行 BM25/Vector 排名和 RRF。目标实现必须在任何检索评分前执行：

```text
RuntimeAnswerContent
→ 合并公开主体
→ AnswerSubjectType 映射为 PortfolioSubjectKind
→ subjectKind ∈ invocation.allowedSubjectKinds
→ eligible claims/chunks
→ keyword/vector ranking
→ RRF
→ CandidateSubject
```

强制要求：

1. Case chunk 不得参与 Project Recommendation 的 BM25 统计、向量候选或 RRF 排名；
2. `authorized(scope, subject)` 与 `allowedKind(invocation, subject)` 是两个独立谓词，候选必须同时满足；
3. EXACT scope 需要核对 `(subjectId, expectedKind)`，不能只比较 subjectId；
4. `CandidateSubject.subjectKind` 来自 `AnswerKnowledge.subjectType` 的显式映射，不能从 route 推导；
5. backend fallback 从 PostgreSQL 切到 Bundle 时必须复用原 Invocation，因此类型约束不变。

### 8.1 Eligible corpus BM25 统计

当前 `AnswerKeywordIndex` 的 `documentCount`、document frequency 与 average document length 是按完整 Bundle 预计算的。仅过滤 `eligibleChunks` 仍会让 Case 改变 Project 的 IDF 和长度归一，因此不满足“类型过滤早于评分”。

Project Recommendation 的 BM25 必须从本次 eligible Project documents 重新派生评分统计：

```text
eligibleDocuments = index.documents where chunkId ∈ eligibleChunks
N = eligibleDocuments.size
avgdl = eligibleDocuments.documentLength 的平均值
df(term) = eligibleDocuments 中 termFrequency(term) > 0 的文档数
tf/docLength = 继续复用每个 eligible document 的预计算值
```

当 eligible documents 为空时返回空排名，不使用完整语料统计兜底。添加、删除或重排任意 Case 文档不得改变同一批 Project 的 BM25 分数、顺序或稳定 tie-break。该要求只重算轻量聚合统计，不要求运行期重新分词、重建 embedding 或改写发布包格式。

## 9. PostgreSQL 检索合同

### 9.1 SelectionTarget

`SelectionTarget` 增加不可变、非空的 `allowedSubjectKinds`。`JdbcPostgresKnowledgeQuery` 从 Invocation 原样复制该集合，任何内部重建 target 的路径都必须保留它。

特别是推荐约束不足时的 `broadTarget`：

```text
允许放宽：careerTrack、capabilityCodes
禁止放宽：allowedSubjectKinds、content release、公开 Evidence/Claim 边界
```

`SelectionTarget` 同时是 selection benchmark 的内部 JSON DTO。新增字段属于受影响的内部 JSON 合同，必须同期迁移：

- `@JsonCreator` 中 `allowedSubjectKinds` 为必填、非空字段；
- `portfolio-selection-cases.json` 中每个 target 必须显式声明该字段；
- 现有通用 selection benchmark 可以显式使用 `["PROJECT", "CASE"]`，因为它评估通用公开候选选择；
- Project Recommendation 专属回归必须使用 `["PROJECT"]`，acceptable sets 不得包含 Case；
- benchmark fixture loader、CLI、evaluator 和 round-trip/解析测试必须通过；
- 缺失、null、空集合或未知枚举值必须 fail-closed；
- 不提供隐式 `{PROJECT, CASE}` 默认值，也不根据 acceptable sets 推导允许类型。

### 9.2 FTS 与 Vector SQL

FTS 和 Vector 的 `eligible` CTE 均必须使用参数化数组条件：

```sql
AND ps.subject_kind = ANY(CAST(? AS text[]))
```

该条件必须位于 `ranked` CTE 和 `LIMIT ?` 之前。禁止字符串拼接枚举字面量；绑定数组必须按枚举名稳定排序，空数组在 Java 构造期已被拒绝。

正确顺序：

```text
release/public/verified/approved 边界
→ allowed subject kinds
→ career/capability 软约束
→ score
→ 单路 limit
→ FTS/Vector RRF fuse
→ fuse limit
```

因为 FTS 与 Vector 各自先执行有限查询，再融合，所以两条 SQL 都必须在各自 limit 前过滤；只在 RRF 后过滤仍然违反合同。

### 9.3 EXACT 查询

`findByIds` 继续以获准 ID 为访问边界，同时必须：

- 在 SQL 或紧邻 adapter 的完整性边界校验实际 `subject_kind ∈ allowedSubjectKinds`；
- 校验实际 `(subjectId, subjectKind)` 与 EXACT scope 中声明的 pair 一致；
- 类型不一致按内部完整性失败处理，不按“没有结果”或用户输入不足处理。

### 9.4 候选窗口

生产混合查询当前存在 `MAX_SUBJECTS=50`。Project 类型过滤必须早于这 50 个候选窗口，避免大量高排名 Case 把合法 Project 挤出窗口。最终 `requestedSize` 仍由 Recommendation 语义排名处理；两种 limit 职责不得混淆：

```text
MAX_SUBJECTS：受类型约束后的检索候选预算
requestedSize：最终推荐项目数量
```

## 10. Candidate 与 Evidence 类型传播

### 10.1 CandidateSubject

`CandidateSubject` 增加必填：

```java
private final PortfolioSubjectKind subjectKind;
```

构造器、getter、`equals`、`hashCode` 和安全 `toString` 必须同步更新。`toString` 不得输出 subjectId、title 或 route，可输出闭集 kind 与计数。

来源要求：

- PostgreSQL adapter 使用已有 `SelectionCandidate.subjectKind`；
- Bundle adapter 使用 `AnswerSubjectType → PortfolioSubjectKind` 显式映射；
- 不允许用 `/projects/` 或 `/cases/` 推导；
- route 与 kind 明显冲突时可作为完整性告警/失败，但 route 不是权威。

### 10.2 EvidencePromotionValidator

晋级边界接收 Invocation 的 `allowedSubjectKinds` 或等价已冻结合同，并执行整批 fail-closed 校验：

```text
CandidateSubject.subjectKind ∈ allowedSubjectKinds
CandidateSubject.subjectKind 原样传播
CandidateSubject.subjectKind == ValidatedEvidenceUnit.subjectKind
```

任何主体越界，整个 CandidateSet 不得部分晋级。不能删除 Case 后继续生成 Project 结果。

### 10.3 ValidatedEvidenceUnit

`ValidatedEvidenceUnit` 增加必填 `subjectKind`。所有正式生产构造器必须显式传入类型。测试便利构造器如保留默认值，只能明确命名为 Project fixture，并不得用于混合类型合同测试。

该字段暂不写入 `PortfolioSemanticResult` 的公开结构或 `ContinuationContext` 持久化结构；它服务于本次执行管线的类型校验与确定性测试。

## 11. ResultFactory 语义与 PARTIAL

`PortfolioSemanticResultFactory` 对 `PORTFOLIO_RECOMMEND` 执行纵深校验：

```text
invocation.allowedSubjectKinds == {PROJECT}
bundle 中每个 unit.subjectKind == PROJECT
同一 subjectId 下所有 unit.subjectKind 一致
```

若任一条件失败：

- 这是内部合同/完整性失败；
- 不返回混合 Recommendation；
- 不静默删除 Case；
- 不将违规伪装成 `PARTIAL`；
- 不创建 Recommendation Context；
- 不映射成用户输入错误。

Recommendation 必须至少包含一个有已验证 Evidence 的合法 Project；空 Recommendation 不是 `PARTIAL`。终态矩阵冻结为：

```text
0 个合法且有 Evidence 的 Project
→ PortfolioSemanticResultFactory 返回 Optional.empty()
→ TaskTerminalException(NO_RESULT, NO_SUPPORTED_RESULT)
→ 不创建 Recommendation、不创建 Recommendation Context、不产生 discussionAction

1..requestedSize-1 个合法且有 Evidence 的 Project
→ coverage=PARTIAL
→ omissions 包含 REQUESTED_SIZE

requestedSize 个合法且有 Evidence 的 Project，但仍有推荐约束未满足
→ coverage=PARTIAL
→ 沿用现有 unsatisfiedConstraints 语义

requestedSize 个合法且有 Evidence 的 Project，且约束全部满足
→ coverage=FULL
```

因此 `PARTIAL` 只表示系统至少有一个可安全返回的合法 Project，但规模或约束覆盖不完整；它不能承载空结果。

示例：

```text
10 个 Case + 1 个 Project，requestedSize=2
→ 检索域只看 Project
→ 返回 1 个 Project
→ coverage=PARTIAL
→ omissions 包含 REQUESTED_SIZE
```

Case 绝不能用于补齐第二项。

## 12. 能力失败与内部诊断

### 12.1 检索/晋级合同错误

当前裸 `IllegalArgumentException` 会被 `PortfolioTaskExecutor` 映射为 `INPUT_REJECTED`。主体类型越界属于服务端能力合同错误，不能误报为用户输入错误。

实现必须使用 typed 内部路径，例如：

```text
Evidence contract violation
→ PortfolioCapabilityException(INTEGRITY_FAILURE)
→ Task FAILED / CAPABILITY_UNAVAILABLE
```

具体类名可在实施计划中依现有异常体系确定，但禁止解析 exception message 识别原因。

建议内部闭集原因：

```text
SUBJECT_KIND_NOT_ALLOWED
SUBJECT_KIND_CHANGED_DURING_PROMOTION
RECOMMENDATION_SUBJECT_KIND_CONTRACT_VIOLATION
EXACT_SCOPE_SUBJECT_KIND_MISMATCH
```

### 12.2 讨论续接诊断

Continuation 对外继续统一：

```text
DISCUSSION_CONTEXT_UNAVAILABLE
```

内部至少区分：

```text
CONTEXT_NOT_FOUND
CONTEXT_RELEASE_MISMATCH
CONTEXT_TYPE_MISMATCH
RESULT_ITEM_NOT_IN_CONTEXT
RECOMMENDATION_CANDIDATE_NOT_CURRENT_PUBLIC_PROJECT
```

因为首轮不在持久化 Context 中保存 `subjectKind`，运行时仅凭“不在 currentPublicProjectIds”不能断言它一定是 Case；它还可能是已删除、已取消发布或伪造 ID。只有同时查询并确认 Case catalog 时，才允许记录：

```text
CONTEXT_SUBJECT_KIND_MISMATCH expected=PROJECT actual=CASE
```

不得通过异常消息字符串分类。若需要修改 coordinator，应使用 typed internal rejection reason，同时保持公开错误不变。

## 13. ProjectDiscussionCoordinator 边界

coordinator 的严格策略保持不变：

1. Context 必须属于当前 Conversation；
2. ContentRelease 必须一致且未过期；
3. resultItemId 必须属于 Recommendation selected results；
4. Recommendation 中全部候选必须属于 current public projects；
5. 选中项目必须仍公开；
6. 成功后才能创建 ProjectDiscussionContext 并原子更新 active pointer。

不能改成“只要当前点击项是 Project 就放行”，因为 ProjectDiscussionContext 的 switch candidates 来自完整 Recommendation；混合集合会把问题推迟到讨论内切换。也不能在此处删除 Case，否则响应卡片、resultItemId、Context 和可切换范围将不一致。

## 14. Context 与 Store 兼容性

首轮不修改 `ContinuationContext.Recommendation` 的持久化 shape，理由是类型已经在进入 Context 前被强校验，且 coordinator 仍以当前公开 Project 集合执行最终授权验证。

兼容行为：

- 新产生的 Recommendation Context 只包含 Project；
- 旧的纯 Project Context 继续可用；
- 旧的混合 Context 继续被 coordinator 拒绝；
- 不做数据迁移、不复活或清洗旧 Context；
- 历史 Context 按现有 absolute TTL 自然过期；
- IN_MEMORY 与 PostgreSQL codec/schema 无新增字段；
- PostgreSQL 加密、Conversation 绑定、release 绑定和 TTL 条件保持不变。

如果后续确需在 Context 中直接展示或恢复多主体类型，应另立 Level 3 设计，不得借本次缺陷静默扩展持久化合同。

## 15. 公开 API 与前端

本批次预期不修改公开 API：

- Recommendation item shape 不变；
- backend-owned `discussionAction` 不变；
- `CONTINUE ENTER_RESULT` command shape 不变；
- `activeDiscussion` shape 不变；
- 外部错误码不变；
- 前端继续只渲染并原样转发 action，不解析 route 或主体类型。

本批次会修改 `SelectionTarget` 对应的内部 benchmark JSON 合同：所有 fixture 必须显式携带 `allowedSubjectKinds`。该变化不属于浏览器/Agent 公开 API，但必须按内部 schema 迁移并通过 loader、CLI、evaluator 门，不能在“公开 API 不变”名义下遗漏。

若实施时发现必须修改公开合同，应暂停实现、更新本文范围并重新取得批准，不能在测试修复中顺带扩展 API。

## 16. 安全与隐私

必须保持：

1. 未知、跨会话或过期 handle 不泄露存在性；
2. resultItemId 不属于 Context 时不泄露合法成员；
3. 对外不区分 Case、已删除 Project、未发布 Project 或伪造主体；
4. Provider 无权提供 subjectId、Context handle、resultItemId 或扩大候选集合；
5. SQL 只使用参数化 kind 数组，不拼接访问者输入；
6. 受控查询文本继续不包含用户自由文本；
7. 日志只允许闭集类型、数量、阶段和原因；
8. 不记录标题、ID、route、handle、Token、用户文本、Prompt、模型原始输出或 Evidence 内容；
9. 真实发布数据探针只读，不更改 release、公开内容或 Agent State；
10. 真实 Provider 验证必须单独获授权，并遵守现有 live-provider gate。

## 17. 确定性测试规格

实现必须遵循 RED → GREEN。第一批回归在生产代码修改前加入，并证明旧实现失败。

### 17.1 Invocation 合同

- Recommendation 自动得到且只能得到 `{PROJECT}`；
- Recommendation 传 `{PROJECT, CASE}` 被拒绝；
- allowed kinds 为空被拒绝；
- EXACT scope 声明类型不属于 allowed kinds 被拒绝；
- fallback backend/strategy 获得同一 immutable set；
- `GoalSubjectReference.Kind.RESULT` 在 Invocation 前已解析为实际主体；未解析 RESULT 被拒绝且不存在默认映射；
- 测试不能依赖 route 推导类型。

### 17.2 Bundle 过滤顺序

固定语料：

```text
Case A      score=100
Case B      score=90
Project C   score=80
Project D   score=70
requestedSize=2
```

断言：

- Case chunks 不进入 eligible/ranking；
- BM25 的 N、DF 与 average document length 仅从 eligible Project documents 计算；
- 向同一 Bundle 加入任意数量、任意词频和任意长度的 Case 后，Project 的 BM25 分数与排序不变；
- CandidateSet 只包含 Project C、Project D；
- 最终返回两个 Project；
- Fallback 到 Bundle 时结果类型不变；
- EXACT `(id, kind)` 不一致 fail-closed。

### 17.3 PostgreSQL filter-before-limit

至少覆盖：

1. FTS query 以 `limit=2` 执行时，高分 Case 不占窗口，返回两个 Project；
2. Vector query 使用确定性 embedding fixture，得到同样结果；
3. HYBRID/RRF 融合后仍只有 Project；
4. 生产窗口回归：超过 `MAX_SUBJECTS` 数量的高排名 Case 与至少两个 Project 共存，Project 不被挤出；
5. broadTarget 清空 career/capability 后仍保留 `{PROJECT}`；
6. `findByIds` 校验实际 kind 与 EXACT scope pair；
7. SQL 仍锁定单一 release、VERIFIED claim 与 APPROVED evidence；
8. Bundle 与 PostgreSQL 对同一 fixture 返回相同主体类型集合。

### 17.4 Benchmark 内部 JSON 合同

- 现有每个 `SelectionTarget` fixture 显式声明非空 `allowedSubjectKinds`；
- 通用 selection benchmark 显式使用 `[PROJECT, CASE]`，原有包含 Case 的 acceptable sets 继续合法；
- Project Recommendation 专属 benchmark 使用 `[PROJECT]`，acceptable sets 只包含 Project；
- 缺失、null、空数组和未知 kind 的 fixture 反序列化失败；
- loader、CLI 与 evaluator 对迁移后 fixture 运行通过；
- JSON round-trip 保留 allowed kinds，不存在隐式全类型默认值。

### 17.5 类型传播与晋级

- `CandidateSubject(PROJECT) → ValidatedEvidenceUnit(PROJECT)`；
- CASE 不在 allowed set 时整批晋级失败；
- unknown/null kind 构造失败；
- 晋级不得修改 kind；
- 多 Evidence 单元属于同一 subject 时 kind 必须一致；
- typed violation 映射为内部完整性/能力失败，而不是 `INPUT_REJECTED`。

### 17.6 ResultFactory、NO_RESULT 与 PARTIAL

- `[PROJECT, CASE]` bundle 被拒绝，不静默过滤；
- 0 个合法且有 Evidence 的 Project 返回 `Optional.empty → NO_RESULT/NO_SUPPORTED_RESULT`，不创建 Recommendation Context；
- 两个合法 Project 存在时 `requestedSize=2` 返回两个；
- 只有一个合法 Project 时返回一个且 `PARTIAL/REQUESTED_SIZE`；
- 任意数量 Case 都不能补足 requestedSize；
- 约束不满足与数量不足沿用现有 PARTIAL 语义；
- 类型合同失败不产生 Recommendation Context。

### 17.7 真实 action 生命周期

使用真实生命周期组件、真实 `PortfolioSemanticResultFactory`、真实 ContextMutationPlanner、真实 PublicAgentTurnProjector 和确定性 Goal/Plan/Outcome stub：

```text
ASK PORTFOLIO_RECOMMEND
→ 获取公开 Recommendation 响应
→ 从响应读取实际 discussionAction
→ 原样发送 CONTINUE ENTER_RESULT
→ kind=ANSWER
→ activeDiscussion.status=ACTIVE
→ active project 等于所选 Context item
```

禁止在测试中手工重新组合 handle、item ID 或 subjectId 替代真实 action。

### 17.8 State backend

IN_MEMORY：

- 完整执行 Recommendation → action → ENTER_RESULT；
- settlement 后能按 conversation + handle 找到 Context；
- selected item membership 与公开 action 一致。

PostgreSQL：

- 使用 Testcontainers/固定 fixture；
- Context settlement、加密写入、按 conversation + handle 读取与 TTL 条件通过；
- 使用读回 Context 和实际 action 完成 ENTER_RESULT round-trip；
- 不允许仅用 mocked Store 代替生产状态合同。

### 17.9 安全回归

必须继续拒绝：

- 未知 handle；
- 跨 Conversation handle；
- release 不一致；
- Context 类型不符；
- action 引用不在 Recommendation 中的 item；
- 历史混合 Recommendation Context；
- 已取消发布或已删除 Project；
- 伪造 subjectId；
- 过期 Context；
- 切换/退出并发后旧 generation 的 mutation。

所有外部响应不得泄露具体内部原因。

## 18. 当前发布数据探针

一次性根因探针应与长期回归分离，并按生产实现前后拆成两个明确阶段。

探针应调用真实当前发布数据上的 `JdbcPostgresKnowledgeQuery`/selection adapter，而不是使用近似排序 SQL；Provider 使用 stub 或完全不参与。若需 HYBRID，Embedding 使用现有本地实现或确定性 stub，不调用付费模型。

阶段与目标：

```text
PRE_FIX（任何生产代码修改前）
→ 在可用的当前发布数据上运行 production-shaped pipeline
→ 记录匿名 candidate/selected kinds
→ 证明旧实现是否在当前数据上产生混合类型

POST_FIX（生产实现与确定性测试完成后）
→ 在同一 release、同一 Invocation、同一 strategy 条件下重放
→ 只允许产生 PROJECT
```

输出必须经过匿名聚合。该探针不能替代固定 Testcontainers fixture，也不能把当前数据内容变成长久测试依赖。

PRE_FIX 只有在实现前的对应 release、配置和只读数据源仍可用时才可声称取得。如果环境不可用、release 已变化或匿名探针无法运行，证据状态必须明确记为 `PRE_FIX_CURRENT_DATA_NOT_CAPTURED`，不得把确定性 RED fixture 冒充为当前数据前后对照。此时仍可依靠“旧实现失败、新实现通过”的固定 fixture 证明回归修复，但 Exit Gate 只能声明确定性证据完成，不能声明已拥有当前发布数据的 pre/post 对照。

POST_FIX 若无法在与 PRE_FIX 可比较的条件下运行，同样记录 `POST_FIX_CURRENT_DATA_NOT_COMPARABLE`。所有报告必须写明 release/strategy 是否相同，但不得输出 release ID、主体 ID 或其他受限值到公开响应。

## 19. 真实 Provider 与浏览器 Smoke

确定性测试和受影响全量门通过后，真实 Qwen smoke 分类为 `REQUIRED_BUT_SEPARATELY_AUTHORIZED`：

1. 使用当前配置的 Qwen 模型生成合法 Project Recommendation Goal；
2. 服务端返回 Project-only Recommendation；
3. 从浏览器或 packaged E2E 读取真实 action；
4. 点击“与我讨论”；
5. 返回 `ANSWER` 且 `activeDiscussion=ACTIVE`；
6. 讨论内再执行一个受限项目问题，确认未退出 typed context。

真实调用涉及外部网络、凭据、费用与限流。本 spec 和普通测试授权都不等于 live-provider 授权。未获授权或凭据不可用时，状态必须记为 `BLOCKED`/`NOT_RUN`，不得宣称 Provider gate 通过。

## 20. 可观测性

允许增加闭集、安全的结构化诊断：

```text
operation=PORTFOLIO_RECOMMEND | ENTER_RESULT
stage=INVOCATION | RETRIEVAL | PROMOTION | RESULT_FACTORY | CONTINUATION
expectedSubjectKinds=[PROJECT]
actualSubjectKind=PROJECT | CASE | UNKNOWN
failureReason=<closed enum>
candidateCount=<number>
```

生产日志默认不输出主体 ID 或 route。测试必须包含负向断言，确认日志不含用户输入、标题、Context handle、Token、Prompt、Provider JSON 或 Evidence 文本。

不要求为本问题建立新的 metrics 平台；如仓库已有安全计数器，可记录合同违规计数，但不得引入高基数字段。

## 21. 预期改动面

经批准后的预计生产改动包括，最终以实施前代码检索为准：

- 移动/提升 `PortfolioSubjectKind` 到 capability 层；
- `PortfolioEvidenceInvocation`；
- `PortfolioInvocationFactory`；
- `CandidateSubject`；
- `BundlePortfolioRetrieverAdapter`；
- `PostgresPortfolioRetrieverAdapter`；
- `SelectionTarget`；
- `JdbcPostgresKnowledgeQuery`；
- `JdbcPostgresSelectionQuery`；
- PostgreSQL selection row/candidate/imports；
- `PortfolioSelectionBenchmarkCase` 及 selection benchmark loader/CLI/evaluator 的受影响解析链；
- `backend/src/test/resources/retrieval-benchmark/portfolio-selection-cases.json` 与 Project-only benchmark fixture；
- `EvidencePromotionValidator`；
- `ValidatedEvidenceUnit`；
- `PortfolioEvidenceCapability` 的 typed promotion failure 映射；
- `PortfolioSemanticResultFactory`；
- 如本批次纳入 typed continuation reason：`ProjectDiscussionCoordinator` 与 `AgentTurnLifecycleService`；
- 相应 Unit、Integration、Lifecycle、Testcontainers、privacy 与 contract tests。

预期不修改：

- 前端公开模型与组件；
- `ContinuationContext.Recommendation` schema；
- IN_MEMORY/PostgreSQL Context codec/schema；
- Provider prompt/schema；
- ProjectDiscussionContext 公开 shape。

文档义务：

- 实现前按仓库规则在 `docs/15-Agent 2.0真实交互问题清单与修复边界.md` 登记问题与专属门；
- spec 获批后更新 `docs/00-文档状态索引.md` 并创建独立实施计划；
- 完成并通过真实门后更新 `docs/08-当前实现状态.md`；
- 重要行为修复完成后更新 `docs/11-项目演进日志.md`；
- 不得在验证前把设计或实现状态写成已完成。

## 22. 实施切片约束

本文不替代实施计划，但实施顺序必须满足以下依赖：

```text
1. RED：filter-before-limit 与真实 action 生命周期失败测试
2. PRE_FIX：生产改动前运行当前发布数据匿名探针；不可用则立即记录 NOT_CAPTURED
3. 提升 PortfolioSubjectKind，冻结 Invocation 合同并迁移 benchmark JSON
4. PostgreSQL FTS/Vector/EXACT/broad fallback 过滤
5. Bundle 排名前过滤并按 eligible corpus 重算 BM25 N/DF/avgdl
6. CandidateSubject → ValidatedEvidenceUnit 类型传播
7. Promotion 与 ResultFactory 纵深校验及 typed failure
8. NO_RESULT、PARTIAL 与安全回归
9. IN_MEMORY/PostgreSQL State round-trip
10. POST_FIX：以相同 Invocation/strategy 执行匿名探针；不可比较则明确记录状态
11. benchmark loader/CLI/evaluator、受影响全量、隐私、文档和架构门
12. 另行授权的真实 Qwen/browser smoke
```

生产代码不得先于 RED 测试修改。不得为了让测试通过而弱化 coordinator、公开错误或 Store 查询边界。

## 23. 风险与控制

| 风险 | 控制 |
| --- | --- |
| 只在 ResultFactory 过滤，Case 已占用有限候选窗口 | FTS、Vector 与 Bundle 均在评分/limit 前过滤；结果工厂只验证 |
| broad fallback 重新放入 Case | `allowedSubjectKinds` 为不可放宽合同，内部 target 重建必须复制 |
| 新增第四套主体枚举 | 提升现有 `PortfolioSubjectKind`，其他模型只在边界显式转换 |
| route 变成类型权威 | route 仅用于公开导航/兼容性检查，正式 kind 来自数据模型 |
| 晋级异常被映射为用户输入错误 | typed integrity/capability failure，不抛裸合同 `IllegalArgumentException` |
| 静默过滤掩盖后端退化 | Promotion/ResultFactory 整批 fail-closed |
| Context schema 扩大引入迁移风险 | 首轮不持久化 kind，旧混合 Context 继续严格拒绝并自然过期 |
| 外部错误细化泄露资源存在性 | 内部闭集原因，对外保持统一错误 |
| PostgreSQL 测试只覆盖小样本，未证明 MAX_SUBJECTS | 查询级小 limit 测试 + 超过生产窗口的 fixture |
| 当前数据探针成为脆弱长期测试 | 探针只用于证据；长期门使用固定 fixture |
| 生产改动后才尝试取得 pre-fix 证据 | PRE_FIX 探针固定在任何生产改动前；环境不可用时记录 NOT_CAPTURED，不伪造前后对照 |
| Bundle 只过滤 eligible chunks，但 Case 仍改变全局 IDF/avgdl | BM25 的 N、DF、avgdl 按本次 eligible documents 重算，并增加 Case 扰动不变性测试 |
| SelectionTarget 新必填字段破坏 benchmark fixture | 同批迁移内部 JSON；通用 benchmark 显式双类型，Recommendation benchmark 显式 Project-only |
| RESULT 被随意映射为 Portfolio 主体 | continuation 在 Invocation 前解析；未解析 RESULT typed fail-closed，绝无默认映射 |
| 将问题错误归因给 Qwen | 核心回归完全 deterministic；Qwen 只做末端 conformance smoke |
| 改动过宽影响 Fact/Compare | allowed kinds 从 typed exact scope 推导，并执行现有行为回归 |

## 24. Exit Gate

只有以下条件全部满足，才可声明本问题关闭：

- [ ] 本 spec 已由用户明确批准并切换为 `APPROVED`；
- [ ] 已创建并批准独立实施计划；
- [ ] 动态问题账本已在实现前登记，未提前关闭；
- [ ] `PORTFOLIO_RECOMMEND` 的 `allowedSubjectKinds` 被构造期冻结为 `{PROJECT}`；
- [ ] 现有 `PortfolioSubjectKind` 已提升为 capability 层权威，未新增第四套枚举；
- [ ] `GoalSubjectReference.Kind.RESULT` 在 Invocation 前完成可信解析；未解析 RESULT 无默认映射并 fail-closed；
- [ ] Bundle 在 eligible/ranking 前过滤主体类型；
- [ ] Bundle BM25 的 N、DF、avgdl 从 eligible documents 重算，Case 扰动不改变 Project 分数或排序；
- [ ] PostgreSQL FTS 与 Vector 均在各自 SQL limit 前过滤；
- [ ] EXACT 与 broad fallback 保持类型合同；
- [ ] `SelectionTarget` 内部 JSON schema 已迁移，每个 benchmark fixture 显式声明非空 allowed kinds；
- [ ] 通用 selection benchmark 的双类型语义与 Project Recommendation 的单类型语义由不同 fixture 明确表达；
- [ ] benchmark fixture loader、CLI、evaluator 与解析负向测试通过；
- [ ] `CandidateSubject → ValidatedEvidenceUnit` 完整传播 subjectKind；
- [ ] Promotion 与 ResultFactory 对越界类型整批 fail-closed，不静默过滤；
- [ ] 0 个合法且有 Evidence 的 Project 返回 `NO_RESULT/NO_SUPPORTED_RESULT`，不创建 Recommendation Context；
- [ ] 1..requestedSize-1 个合法 Project 返回 PARTIAL/REQUESTED_SIZE，不使用 Case 补数；
- [ ] Case 高分且超过候选窗口时仍不会挤出 Project；
- [ ] Bundle/PostgreSQL 类型约束一致性测试通过；
- [ ] 使用公开响应真实 action 的生命周期回归通过；
- [ ] `ENTER_RESULT` 返回 `ANSWER` 且 `activeDiscussion=ACTIVE`；
- [ ] IN_MEMORY 完整生命周期通过；
- [ ] PostgreSQL Context round-trip 与生命周期通过；
- [ ] 未知 handle、跨会话、错误 item、release mismatch、历史混合 Context、未发布项目继续拒绝；
- [ ] 对外错误仍不泄露具体内部原因；
- [ ] typed 合同错误不会映射为 `INPUT_REJECTED`；
- [ ] PRE_FIX 当前发布数据探针在生产改动前执行；若不可用则记录 `PRE_FIX_CURRENT_DATA_NOT_CAPTURED`，不得宣称拥有当前数据前后证据；
- [ ] POST_FIX 探针使用可比较的 release/Invocation/strategy；若不可比较则记录 `POST_FIX_CURRENT_DATA_NOT_COMPARABLE`；
- [ ] 当前发布数据探针只输出匿名类型/数量/原因；固定 fixture 的 RED/GREEN 证据与当前数据探针证据分开报告；
- [ ] backend 受影响测试与全量测试通过；
- [ ] privacy、documentation、architecture/release 等仓库要求的门通过；
- [ ] 未修改公开前端/API/Context schema，或任何新增范围均已重新审批；
- [ ] 真实 Qwen 与原始用户可见路径已在单独授权后通过；未授权时明确保持未运行，不虚报；
- [ ] docs/08、docs/11 与问题账本只在对应事实成立后更新；
- [ ] Git diff 只包含本问题范围，未覆盖用户既有修改；
- [ ] 未经明确授权不 commit、不 push。

## 25. 批准决策

用户已确认以下冻结决定：

1. `PORTFOLIO_RECOMMEND` 永久定义为 Project-only；未来 Case 推荐必须建立独立 capability 与 Case Discussion Context。
2. 提升现有 `PortfolioSubjectKind`，不新建枚举；Bundle/Goal/PostgreSQL 在边界转换。
3. `RESULT` 不映射为 Portfolio 主体，必须在 Invocation 前解析；未解析时 fail-closed。
4. PostgreSQL 类型过滤进入 FTS、Vector、EXACT，并在所有 limit 前执行；broad fallback 不得放宽类型。
5. Bundle 选择严格 BM25 方案：按 eligible Project documents 重算 N、DF 与 avgdl，使 Case 不影响 Project 评分。
6. `SelectionTarget` benchmark JSON 同期强制迁移，不提供全类型默认值。
7. 0 Project 返回 NO_RESULT；只有 1..requestedSize-1 才返回 PARTIAL/REQUESTED_SIZE。
8. ResultFactory 遇到 Case 时合同失败，不静默过滤成 PARTIAL。
9. 首轮不修改持久化 Recommendation Context；旧混合 Context 继续 fail-closed。
10. 外部错误继续统一，内部增加闭集诊断。
11. 真实 Qwen smoke 必须另行授权，不能由设计批准自动触发。

2026-08-28，用户在完成两轮根因与设计审查后，明确要求完成 spec、创建独立 plan、按互斥文件面并行实现，并在确定性测试后接入真实 API 实测。本文据此切换为 `APPROVED`；真实 Provider 外呼已获得本任务授权，但仍必须使用仓库外 secret file、在确定性门通过后执行，并按实际结果记录 PASS/FAIL/BLOCKED。实现进度与验证结果由独立 ACTIVE plan 和 A2-121 动态账本负责，本文不提前声明修复完成。
