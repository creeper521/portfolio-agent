# Goal v2 生产提升与 Comparison Pair 身份实施计划

<!-- DOCUMENT_STATUS: ACTIVE -->

> 日期：2026-08-27
> 状态：执行中
> 对应设计：[同日设计](../specs/2026-08-27-goal-v2-promotion-and-comparison-pair-identity-design.md)
> Guardian 级别：LEVEL_3（已获用户批准）
> 账本锚点：A2-80、A2-117、A2-119、A2-66、GATE-21—GATE-23

## 纪律

- 每个 Slice 内 TDD：先写失败测试，再改生产代码；
- canonical 与公开合同零改动是硬约束，任务 3/7 的验证专门守护；
- 真实 Provider 门（L4 / PROJECT_DISCUSSION / Comparison 样本）在确定性门全绿之后运行；GLM 限流按账本规则记 BLOCKED，不得以重试刷绿。

## 任务清单

1. **[ ] 注册 Goal v2 契约**（registry + SAFE_SCHEMA_FIELDS 增补）。验证：`mvn.cmd -f backend/pom.xml test "-Dtest=StructuredOutputContractRegistryTest"`。
2. **[ ] Profile 切换绑定**：Qwen/GLM TURN_INTERPRETATION → `goal.provider-draft.v2`。验证：`ApprovedModelExecutionProfileTest`、`ConfiguredModelCatalogTest`、`ModelProviderDescriptorTest`、新增"Profile 不含 goal.provider-draft.v1 字面量"断言。
3. **[ ] Adapter 全链正反例**：v2 金样输入经 `GoalInterpretationAdapter` 得到 proposal.v5 正例；`UNSUPPORTED_ROOT_KIND`/澄清 blockedGoal 缺失负例诊断串不回归。验证：`GoalInterpreterationAdapterTest`、`GoalProposalCodecTest`、`UnresolvedIntentPolicyTest`。
4. **[ ] Goal 容量对齐**：GENERAL_COMPARISON pairs>20 → `BOUNDARY` 固定终局，无模型调用。验证：新单测矩阵（19/20/21 组合）+ `AgentTurnLifecycleService` 层终局类型断言。
5. **[ ] general.provider-draft.v3 契约与注册**：对象化 comparisonSequence.items；`OperationBinding.GENERAL_DRAFT_OUTPUT_COMPILER_VERSION=v3`。验证：schema 正反例单测 + `StructuredOutputGatewayTest`。
6. **[ ] 编译器双射配对**：认领制归属；乱序达标成功样例 + 重复/错标/越界 subjectIndex/缺漏四类负例；EXPLANATION 不受影响。验证：`GeneralProviderDraftCompilerTest` 全量翻新 + `OpenAiCompatibleGeneralKnowledgeAdapterTest`、`StructuredModelTestFixtures` 同步。
7. **[ ] Prompt 重写 + 公开合同零改动证明**：删除排序指令段、改为逐句标注要求。验证：`git diff --stat -- contracts frontend/src/features/agent/model frontend/e2e` 为空。
8. **[ ] 确定性全门**：后端 `mvn.cmd -f backend/pom.xml test`；前端 `npm.cmd --prefix frontend test -- --run && npm.cmd --prefix frontend run check && npm.cmd --prefix frontend run build`；四专项门 documentation/architecture/code-quality/privacy；`git diff --check`。
9. **[ ] 打包 JAR 并跑免 Provider 验收**（联动独立浏览器/负例批次）：JVM_RESTART API 门 Browser 化前置准备、GATE-19 runner env 元测试。
10. **[ ] 真实矩阵与统计**（Qwen 优先，Comparison 就绪后执行）：每家 L4 + PROJECT_DISCUSSION + COMPARISON 固定样本两轮采样；`report-provider-quality.ps1` 输出 per-op 拒绝率与 P50/P95 JSON。
11. **[ ] 入账**：docs/08 表述更新为当前绑定事实；docs/15 按 Exit Gate 满足情况删行/改写；docs/11 行为摘要；机器状态保持 IN_PROGRESS；historicalize 本计划。

## 批次顺序

Slice 1 = 任务 1–3(+8 部分门)；Slice 2 = 任务 4–7(重复 8)；随后 9–10(联动授权批次)；11 收尾。每个 Slice 独立提交组：
`feat(agent): 切换Goal草稿契约至v2生产绑定`、`feat(agent): 引入比较句pair身份契约v3`、`test(agent): ...`、`docs(agent): ...`。

## 回退

revert 对应提交即可回到 v1/v2 绑定组合与位置赋值编译器；打包产物回退到最近已验证 JAR；公开合同未动故前端无需回退。
