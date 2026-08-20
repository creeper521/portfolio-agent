# P1/P2 模型提议合同与编译闭环规格
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

## 目标与边界

完成隔离的 P1/P2 提议合同闭环，使严格 Provider JSON 能无损进入受控的领域提议、主体绑定和计划校验。此变更**不**启用 `MODEL_LED`，不改变当前确定性 `/api/v2/answers` 主链，不引入 stp-v3、SHADOW、Recovery 或真实 Provider 发布门禁。

## 验收行为

1. `EXPLICIT_INPUT` 主体候选必须同时满足 type/id 命中公开目录，且其 `evidenceAnchor` 精确命中该主体 reviewed alias；锚点命中另一主体、无命中或多命中均不得编译执行。
2. alias、fallback 和 PAGE_HINT 使用唯一 `ReferenceMatchPolicy`。它执行 NFKC、trim、`Locale.ROOT` 折叠以及 ASCII 字母/数字/下划线邻接边界检查；不得把 `SQL` 误命中 `MySQL`。
3. `TurnInterpretationInput` 向隔离 P1/P2 路径提供经审核的公开主体元数据（主体、contentVersion、aliases），不能只提供裸 `SubjectReference`。
4. Codec 严格解码并保留 `topicAnchors` 与 `sourceTaskKeys`。`GENERAL_COMPARISON` 的 2–3 topic anchors、`SYNTHESIS` 的 2–6 source keys 能从真实 JSON 到达领域对象与 Compiler。
5. `TaskProposal` 按 task type fail-closed：不属于该类型的 facets/dimensions/careerTrack/capabilityFilters/requestedSize/constraints/topicAnchors/sourceTaskKeys 必须导致整个提议拒绝，不能静默忽略。
6. `ProposalCompilationResult` 区分 compiled、clarification-required、rejected；主体依据不充分进入受控澄清，结构/安全非法进入拒绝。
7. `SemanticPlanValidator` 作为第二道防线：推荐 candidateSubjects 必须全为 `PROJECT`，并维持既有依赖、排除、内容版本校验。
8. fallback 仅支持精确 reviewed alias 的唯一项目概览与既有受控动作；不能回退到另一套自然语言关键词路由。

## 数据与约束

- `PublicSubjectDescriptor`：`SubjectReference subject`、非空唯一 `reviewedAliases`。同类型 alias 在发布时唯一；短中文别名需要显式审核。
- `ReferenceMatchPolicy` 输入原始 currentInput、`TextAnchor` 和候选 alias；只对该 anchor 的位置作边界检查。
- `WireTask` 新增 `topicAnchors`、`sourceTaskKeys`，未知字段和重复字段仍由 Codec 拒绝。
- 字段矩阵由 taskType 定义：
  - FACT：subjectCandidates、facets；
  - COMPARE：2–3 subjectCandidates、dimensions；
  - RECOMMEND：PROJECT candidates（可空代表全部公开项目）、careerTrack/capabilityFilters/requestedSize/constraints；
  - REFINE：RESULT candidate、constraints；
  - GENERAL_EXPLANATION：无 portfolio subjects；
  - GENERAL_COMPARISON：2–3 topicAnchors、dimensions；
  - SYNTHESIS：2–6 sourceTaskKeys、dimensions。

## 非目标

- 不将 Provider 提议接入 `DefaultTurnRouter` 的生产默认路径。
- 不修改前端，不改变公开 HTTP 版本。
- 不实现 RecentResultSet、完整 confirmed/pending recovery 编排；这些依据必须在后续获得授权的编排阶段完成。

## 测试证据

- Codec JSON 全链覆盖七类任务，特别覆盖 GENERAL_COMPARISON/SYNTHESIS。
- hostile case：合法 `subjectCandidates` 字段携带公开但与 anchor alias 不一致的主体，必须进入 clarification/rejected，不能仅依赖未知字段失败。
- `SQL`/`MySQL`、page marker 邻接、fallback alias 的统一匹配回归。
- Validator 对非 PROJECT recommendation candidate 的 fail-closed 回归。

## 实施状态（2026-08-17）

上述隔离 P1/P2 行为已实现并通过自动化回归：三态编译结果、主体依据澄清、reviewed alias fallback、严格字段矩阵、Codec 透传和值语义均已闭环。`MODEL_LED` 未启用，`DefaultTurnRouter` 生产默认路径、stp-v3、SHADOW、Recovery 与 P3-P8 均未因本次实施改变。
