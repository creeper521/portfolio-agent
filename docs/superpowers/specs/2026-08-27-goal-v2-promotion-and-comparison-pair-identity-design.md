# Goal Provider Draft v2 生产提升与 Comparison Pair 身份设计

<!-- DOCUMENT_STATUS: APPROVED -->

> 日期：2026-08-27
> Guardian 级别：LEVEL_3（用户已于本轮明确批准 Goal v2 生产绑定）
> 关联开放项：A2-80、A2-117、A2-119、A2-66、GATE-21、GATE-22、GATE-23
> 对应实施计划：[同日实施计划](../plans/2026-08-27-goal-v2-promotion-and-comparison-pair-identity.md)

## 1. 目标与授权边界

1. 把 `goal.provider-draft.v2` 从"已批准未接线"提升为两家 Provider 的唯一生产 Goal provider 契约，解除 Qwen Comparison 被上游 `goal.provider-draft.v1` 缺字段阻断的问题（A2-80 上游部分）。
2. 为 General Comparison 引入 pair 身份：provider 草稿的每句比较正文必须自我声明其主体×维度归属，服务端以多重集合双射校验取代位置赋值，使乱序、重复、错标在编译层确定性失败（A2-119 下半、A2-66 的运行时闭环）。
3. 在 Goal 边界对齐容量：`subjectTexts × dimensions > 20` 的合法 schema 组合不得再到达 General 侧，需在服务端产生零额外调用的确定性终局（A2-119 上半）。

canonical 公开合同不变：`goal.proposal.v5`、`general.draft.v2`、全部 `contracts/agent-turn/*` 公开响应 fixture、前端合同解析均不改动。本设计不改变重试/fallback 冻结决策，不新增修复式容错。

## 2. 权威表（Level 3 变更面）

| 概念 | 提升前权威 | 提升后权威 | 说明 |
|---|---|---|---|
| Goal provider wire-shape | `goal.provider-draft.v1.schema.json` | `goal.provider-draft.v2.schema.json` | 两家 Provider 同步切换；v1 资源保留于 jar 但生产绑定移除；仅根级 object carrier 有一次性严格解码边界 |
| General provider wire-shape | `general.provider-draft.v2.schema.json` | `general.provider-draft.v3.schema.json`（新增） | 仅 Qwen 双合同路径；GLM General 保持 identity 直通 `general.draft.v2` 不变 |
| Comparison 归属语义 | 编译器按 subject 序 × 排序维度位置赋值 | draft 逐句携带 `{dimension, subjectIndex}`，编译器双射校验后落 canonical | canonical statement shape 不变 |
| 容量守卫 | 无（schema 允许 5×10=50 组合越过 General 20 上限） | Goal 解析后、Provider 调用前的确定性边界裁决 | 见 §5 |

保持不动：selectionVersion（glm-4-7-flash-v4 / qwen-3-7-flash-v6 之外的版本不再新设）、protocol profile、token field policy、operation 预算、公开目录结构。`descriptorFingerprint` 覆盖全部 Operation binding fingerprint，`ConfiguredModelCatalog` 将该 descriptor fingerprint 明确纳入 `catalogVersion` 派生；因此合同或 Compiler binding 变化即使不改变公开 JSON shape，也会使旧目录快照失效，无需人肉 bump。

### 2.1 已审阅并接受的取舍

- Goal v1 与 v2 的差异是 provider 友好形状（decision+扁平语义字段），canonical 仍由同一 `GoalProviderDraftCompiler` 家族机械派生；切换只改变 wire 解析与锚点来源（v2 要求 provider 提供 `inputText` 子串，锚点 start 由服务端计算，与现状一致）。
- GLM 同步切 v2 使两家共享一份 Goal 草稿契约；GLM 此前产出的 v1 形状由真实矩阵重新验证（允许 BLOCKED 如实入账）。
- `comparisonSentences` 升级为对象数组属于 provider 契约破坏性演进，因此按仓库纪律采用新版本号 v3 注册，不原地改 v2。

### 2.2 Transport carrier 解码边界

required-tool 只承担只读 response carrier，不执行任何工具。部分 Provider 会把 Schema 中的 object 作为 tool arguments 内的 JSON 字符串返回；为兼容该 wire 差异，Compiler 只允许 Goal Draft 根级 `goal`、`recentReference`、`clarification` 三个字段在值为字符串时解码一次：

1. 使用开启 duplicate-key detection 与 trailing-token failure 的严格 JSON reader；重复键、合法 JSON 后尾随 token、不可解析文本全部拒绝；
2. 解码结果必须是 JSON object，数组、标量与 `null` 均拒绝；其他字符串字段不递归解析，也不进行第二次解码；
3. 当前 route 未选择的根级 sibling 若显式为 `null`，仅视同缺省；被选择分支的必填字段、精确字段集合与领域语义仍按原规则失败关闭；
4. 该边界只消除 Transport carrier 表达差异，不补字段、不猜语义、不改写值，不触发 repair Prompt、同请求 retry、runtime fallback 或跨模型重发。

上述行为属于确定性 Compiler 边界，不改变 `goal.provider-draft.v2`、`goal.proposal.v5` 或公开 PublicTurn shape。

## 3. Replacement Manifest

### Slice 1：Goal v2 生产绑定

新增/修改：
- `StructuredOutputContractRegistry.standard()`：注册 `(TURN_INTERPRETATION, "goal.provider-draft.v2")`；
- `ApprovedModelExecutionProfile`：Qwen/GLM 的 TURN_INTERPRETATION binding 参数 `"goal.provider-draft.v1"` → `"goal.provider-draft.v2"`；
- `StructuredOutputContractRegistry.SAFE_SCHEMA_FIELDS`：增补 v2 引入的新字段名（`decision`、`inputText`、`topicText`、`subjectTexts`、`conceptText` 等，按最终 diff 补全）；
- `GoalProviderDraftCompiler`：只对根级 `goal/recentReference/clarification` 增加 §2.2 的严格一次性 object carrier 解码；未选择 sibling 的显式 `null` 视同缺省，其他 branch/semantic 规则不放宽；
- `ConfiguredModelCatalog.snapshotVersion()`：把 descriptor fingerprint 纳入 `catalogVersion`，确保 Operation contract/compiler fingerprint 变化向目录快照传播；
- 测试：`ApprovedModelExecutionProfileTest`、`ConfiguredModelCatalogTest`、`ModelProviderDescriptorTest` 期望同步；`GoalProviderDraftV2SchemaTest` 样本升级为编译器金样输入的正反例；`GoalInterpretationAdapterTest` 增加 v2→proposal.v5 全链正例与至少一个错标负例；`UnresolvedIntentPolicyTest` 回归不受影响。

删除：
- 生产绑定中对 `goal.provider-draft.v1` 的两处引用（Profile 内）。资源文件与注册表中 v1 契约本身保留（历史样本回归仍可复现），但生产路径零引用，由架构测试断言 Profile 不再含该字面量。

### Slice 2：Comparison pair 身份 + 容量对齐

新增：
- `backend/src/main/resources/model-contracts/general.provider-draft.v3.schema.json`：`$defs.comparisonSequence.items` 改为对象 `{text(继承现有字符串约束), dimension(pattern ^[A-Z_]{1,64}$), subjectIndex(minimum 1, maximum 5)}`，`additionalProperties:false`；数组保持 1..20；
- registry 注册 `(GENERAL_KNOWLEDGE, "general.provider-draft.v3")`；`OperationBinding.GENERAL_DRAFT_OUTPUT_COMPILER_VERSION` 更新为 `general-provider-draft-compiler.v3`。

修改：
- `GeneralProviderDraftCompiler.comparison()`：期望 pair 多重集合 = `subjects × orderedDimensions()`；每个 item 按 `(subjects[subjectIndex-1], dimension)` 认领声明身份，重复认领、未知 dimension、越界 subjectIndex、缺漏 pair 一律以封闭 reason（`DRAFT_FIELD_CONFLICT` / `DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_*`）失败；canonical statement 的 `subject`/`dimension` 取自声明而非循环计数；文本内容校验规则不变；
- `OpenAiCompatibleGeneralKnowledgeAdapter` 输入投影不再承担配对说明义务（排序提示改为逐句标注指令）；
- `prompts/general-provider-draft-system.txt`：删除"按 supplied order 输出"段落，改为"每句必须携带 dimension 标识与 1-based subjectIndex"；
- EXPLANATION 分支读取逻辑不变（对象数组仅 COMPARISON 使用）；
- 容量对齐：Goal 解析产物中 `GENERAL_COMPARISON` 若 `subjectTexts.size() * dimensions.size() > 20`，在进入 Plan/Provider 前确定性收敛为 `BOUNDARY` 投影终局（封闭中文文案），不发起模型调用；
- SAFE_SCHEMA_FIELDS 增补 `dimension`、`subjectIndex`、`text`（已有）等；
- 测试：`GeneralProviderDraftCompilerTest` COMPARISON 金样全部翻新为对象数组，新增乱序成功样例（证明顺序无关性）与重复/错标/越界/缺漏四类负例；`StructuredOutputGatewayTest`/`OpenAiCompatibleGeneralKnowledgeAdapterTest`/`StructuredModelTestFixtures` 同步；容量对齐规则单测（19=通过、20=通过、21=BOUNDARY）。

删除：无生产代码删除（v2 general 契约注册与 GLM identity 绑定保留）。

## 4. Golden Fixtures 清单

- `GoalProviderDraftCompilerTest`：六类 complete goal + 三类 partial clarification + decision-only 澄清（来源：GoalProviderDraftV2SchemaTest 样本）；
- `GeneralProviderDraftCompilerTest`：COMPARISON 对象数组的乱序达标样例、EXPLANATION 三档样例、caveat 边界样例；
- `StructuredModelTestFixtures`：v3 provider payload 构造器；
- 公开侧：`contracts/agent-turn/**` 与 `frontend/src/features/agent/model/*` 金样零改动（验证命令见计划）。

## 5. 容量对齐裁决

上限取 General 编译器的确定性 20（5 主体 × 4 维度即可达 20；schema 维度上限 10 是历史放行）。超容量输入不是澄清可表达的缺口（封闭澄清字段只有 SUBJECT/OUTPUT/REQUESTED_SIZE/CONSTRAINT），故选择服务端 `BOUNDARY` 固定终局而非降维或截断——截断会静默改变用户请求语义。文案与现有 BOUNDARY 家族一致、不含内部细节。

## 6. Exit Gates（对应账本 ID）

- A2-80 / GATE-22 / GATE-23：提升后由获授权 Qwen/GLM 独立矩阵分别证明 Comparison 可达、schema/semantic canary 通过；任何一家 NOT_READY 或 BLOCKED 只关闭其自身责任，不得替代另一家。
- A2-117：生产绑定后由同一 fingerprint 驱动请求与本地校验的断言测试 + 真实 Comparison 端到端终局。
- A2-119：Goal 层超容量确定性终局单测 + 编译层双射负例矩阵 + Browser Comparison 对齐正确（Browser 正体依赖获授权 lane）。
- A2-82/84/85、ARCH-09/21：随 Phase C 真实矩阵一并取证，不由本设计单独声称完成。

机器状态保持 `IN_PROGRESS`；本设计的完成不等于上述任一全局门 PASS。

## 7. 回退方案

版本级回退：revert 绑定提交即回到 v1/v2 组合；打包产物回退到最近已验证 JAR。无兼容桥、无双栈并存窗口——registry 同时登记新旧契约仅为历史样本回归保留，非运行时 fallback。canonical/public contract 未动，前端无需回退动作。

## 8. 同步义务

1. 实施批次内更新 `docs/08-当前实现状态.md` 中"`goal.provider-draft.v1` 阻断 Comparison"的表述为当前绑定事实；
2. docs/15 相应行仅在各自 Exit Gate 满足时删除/改写，禁止提前关闭；
3. `docs/00` 与 `documentation-check.ps1` 本设计/计划的双向注册在同批完成（DOC-03 规则）；
4. 提交为小步中文 conventional commits（feat/test/docs 分离）。
