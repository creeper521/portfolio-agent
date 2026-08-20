# P1/P2 提议闭环实施计划
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

## 前提

在 `codex/p1-p2-proposal-closure` 隔离工作树实施；保持模型主链开关关闭，不改前端。

1. **公开主体描述与 alias 硬绑定**
   - 先写 `ReferenceMatchPolicy`、`ProposalCompiler` hostile tests。
   - 引入 `PublicSubjectDescriptor`，扩展 `TurnInterpretationInput`，让 `ProposalCompiler` 用 anchor + reviewed alias 验证 EXPLICIT_INPUT。
   - 替换 PAGE_HINT 和 `MinimalTurnFallback` 的私有规范化逻辑。

2. **Codec 七类任务 JSON 全链**
   - 先写 JSON Codec tests：GENERAL_COMPARISON 的 2 个 anchors、SYNTHESIS 的 source keys、缺失/越界/重复字段。
   - 扩展 `WireTask` 与 `task()` 映射；所有 anchors 用 currentInput 校验。

3. **TaskProposal 字段矩阵**
   - 先写每一类的非法混入字段测试。
   - 在 `TaskProposal` 构造校验中按 task type 封闭字段集合；Compiler 不再依赖“忽略无关字段”。

4. **编译结果与 Validator 防线**
   - 增加 `clarificationRequired` 结果分支和 `SUBJECT_BASIS_INVALID`。
   - 对主体依据不足返回澄清；结构非法返回拒绝。
   - 在 `SemanticPlanValidator` 增加 recommendation candidates 均为 PROJECT 的校验及测试。

5. **最小 fallback 收敛**
   - 用统一 alias policy 替换 subjectId 精确比较。
   - 保留精确 alias 概览与结构化动作；删除/禁止任何新增关键词猜测。

6. **隔离验证与交付**
   - 定向运行 Codec、Compiler、Reference policy、fallback、Validator 测试。
   - 全量 `mvn test`；`mvn package -DskipTests -DskipFrontend=true`。
   - 更新状态索引，明确 P1/P2“隔离闭环完成、尚未接入生产模型主链”。
