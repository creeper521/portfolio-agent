# Agent 后端确定性路由闭环设计
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

> **日期：** 2026-08-17
> **状态：** 已批准设计，待实施计划
> **范围：** 后端功能、协议与运行验收闭环；不修改前端代码

## 1. 目标

修复 Agent 在噪声输入、项目推荐、重复任务、推荐数量完整性、正式预设投影和运行版本一致性上的缺陷。服务端必须先确定任务类型和上下文使用规则，再生成和执行计划；大模型不拥有任务类型、候选范围、数量完成条件或证据边界的最终决定权。

成功后的核心行为：

- `1`、纯数字、纯符号和无语义输入一律请求澄清，即使页面携带活动项目；
- `给我推荐两个项目` 默认从全部公开 Project 中选择两个，不受页面默认 SQL 项目影响；
- 用户明确点名项目或携带可信续接句柄时，推荐范围才允许收窄；
- 相同语义任务不能重复执行；
- 推荐数量不足时返回部分完成及结构化原因，不能显示为完全完成；
- 正式预设正文、任务结果、证据引用和公开状态来自同一权威投影；
- packaged JAR 的运行行为必须能够证明与当前源码和内容版本一致。

## 2. 非目标

- 本轮不让大模型接管任务类型判断；
- 本轮不开放 Case 推荐，推荐候选域继续保持公开 Project-only；
- 本轮不修改前端组件或视觉样式；
- 本轮不实现 `stp-v3`、模型主导编排或新的通用知识能力；
- 本轮不扩大公开数据、证据或隐私边界。

## 3. 当前缺陷与根因

### 3.1 噪声继承页面主体

`SemanticSignalCollector` 在没有识别出目标、但解析上下文中存在主体时，会回退生成 `PORTFOLIO_FACT`。当前自然语言检查位于该回退之后，因此 `1` 携带活动 SQL 项目时仍会被解释为项目介绍。

### 3.2 通用推荐被活动主体收窄

推荐目标当前直接携带 `ResolvedRoutingContext.subjects`。`SemanticPlanCompiler` 将这些主体写入 `PortfolioRecommend.candidateSubjects`，执行规划随后使用 exact-subject scope，导致通用推荐只在活动 SQL 项目内选择。

### 3.3 数量识别正确，但完成判定不完整

服务端已经能够从中文数字和阿拉伯数字中解析一至五项推荐数量。三项目失败或只返回一个项目发生在计划已经识别为 `PORTFOLIO_RECOMMEND` 之后，根因位于候选范围、证据支持和结果策略，而不是问题类型分类。

### 3.4 计划只校验 ID 重复

`SemanticPlanValidator` 能拒绝重复 taskId，却没有拒绝语义完全相同的任务。相同类型、参数、主体、输出和履约角色的任务可同时进入执行阶段。

### 3.5 正文和语义任务存在双权威

正式预设的顶层 Blocks 由预设合同投影，`agentTurn.completedTasks` 又由语义执行独立投影。两条路径可能产生不同章节名、Evidence ID、来源引用和状态。

### 3.6 运行行为与当前源码可能漂移

当前源码的确定性编译器对单一 SQL 预设只应生成一个事实任务，但已观察到的运行响应包含两个相同 PRIMARY 任务。实施前必须用当前工作树重新构建 packaged JAR，并在已知端口启动，禁止用来源不明的既有实例作为验收依据。

## 4. 权威处理流水线

请求按以下顺序处理，后续阶段不得反转前序结论：

1. **全局边界检查**：拒绝越过公开数据、安全和隐私边界的请求。
2. **输入形成检查**：判断输入是否含有足够的自然语言语义；噪声直接澄清，禁止读取活动主体生成事实任务。
3. **确定性目标识别**：识别事实、比较、推荐、推荐调整、通用解释、通用比较和综合任务，并解析明确数量。
4. **按目标使用上下文**：根据目标类型决定哪些主体来源可以参与任务。
5. **目标去重和计划编译**：对语义目标稳定去重，再编译为闭集任务。
6. **计划防御性校验**：拒绝语义重复任务、非法依赖、越界参数和内容版本冲突。
7. **确定性执行**：执行器不得新增任务、扩大主体范围或修改请求数量。
8. **结果完整性判断**：以任务目标和请求数量判断 ANSWERED、PARTIALLY_ANSWERED 或 NOT_SUPPORTED。
9. **统一公开投影**：正文、推荐、证据、状态和兼容字段由同一 Canonical Answer Projection 派生。

## 5. 输入形成规则

新增一个明确的输入形成判断，先于任何主体回退运行：

- `null`、空白、纯数字、纯标点、纯表情和不包含自然语言字母/汉字的输入为 `UNFORMED`；
- `UNFORMED` 返回 `CLARIFICATION_REQUIRED`，提示用户说明要了解、比较或推荐什么；
- `UNFORMED` 响应不生成 Plan、Task、Blocks、Evidence、Source 或推荐；
- 页面主体、activeSubjects、page hint 和历史主体都不能覆盖该结论；
- 有自然语言字符不等于一定可执行，仍需继续通过目标和主体规则。

## 6. 上下文使用策略

### 6.1 主体来源分级

可信来源按强到弱分为：

1. 当前问题中的明确项目名称或结构化显式主体；
2. 已授权的 Context Handle 或结果项选择；
3. 已确认的交互结果；
4. 指代型问题使用的活动主体；
5. 页面展示 Hint。

页面 Hint 只能帮助解释“这个项目”“该案例”等明确指代表达，不能自行创建任务范围。

### 6.2 各任务的主体规则

| 任务类型 | 默认主体范围 | 允许收窄的条件 |
|---|---|---|
| PORTFOLIO_FACT | 必须有一个明确或可信指代主体 | 明确输入、正式预设、可信 Context、明确指代 + 唯一活动主体 |
| PORTFOLIO_COMPARE | 必须有至少两个明确主体 | 明确输入、受控选择或可信结果集合 |
| PORTFOLIO_RECOMMEND | 全部公开 Project | 当前问题明确点名候选范围，或可信 Context 明确限定候选集合 |
| PORTFOLIO_REFINE_RECOMMENDATION | 上一次可信推荐集合 | 必须携带并通过授权的推荐 Context Handle |
| GENERAL_* | 不使用作品集主体 | 不允许页面主体污染 |
| SYNTHESIS | 仅消费已完成上游任务 | 不重新解析页面主体 |

对于 `给我推荐两个项目`，即使请求携带 `activeSubjects=[sql-audit]`，推荐参数中的 candidateSubjects 也必须为空，执行阶段据此选择全部公开 Project。

## 7. 大模型边界

本轮保持确定性目标识别。现有模型语义辅助仅可在主体无法解析时提供候选主体，并满足：

- 输入必须已通过形成检查且目标已经由服务端确定；
- 候选必须来自当前公开 Subject Catalog；
- 单一高置信候选经过服务端校验后才能采用；
- 多候选、未知候选、超时或模型失败统一回到澄清；
- 模型不得决定 taskType、requestedSize、candidate scope、Evidence 范围、完成状态或是否跳过澄清；
- 默认配置继续允许关闭该能力；本轮不变更 Provider 需求。

未来若要让模型参与任务类型识别，必须先以 SHADOW 模式经过独立数据集门禁，不属于本次闭环。

## 8. 语义去重

### 8.1 目标层去重

在计划编译前，为每个目标生成稳定键：

`intent + normalizedSubjects + normalizedTopics + normalizedFacets`

相同键只保留首次出现的目标，保持用户顺序。

### 8.2 计划层防御

`SemanticPlanValidator` 额外生成任务语义键：

`taskType + sourceDomain + normalizedParameters + subjectReferences + requestedOutputs + fulfillmentRole`

发现重复时返回 `PLAN_SEMANTIC_TASK_DUPLICATE`。编译器正常路径必须在验证前消除重复；校验器作为 fail-closed 防线，不负责猜测如何合并依赖。

正式预设必须附加测试，断言 SQL 概览预设恰好产生一个 PRIMARY 事实任务。

## 9. 推荐数量完整性

推荐结果公开以下结构化字段：

- `requestedSize`：用户请求数量；
- `actualSize`：最终可公开、证据充分且去重后的数量；
- `candidateScope`：`ALL_PUBLISHED_PROJECTS` 或 `EXPLICIT_PROJECT_SET`；
- `selectedPortfolioIds`：按权威排序输出的项目 ID；
- `unsatisfiedConstraints`：未满足约束；
- `reasonCodes`：安全、稳定的公开原因码。

状态规则：

| 条件 | TaskResolution | 执行展示状态 |
|---|---|---|
| actualSize == requestedSize | ANSWERED | COMPLETED |
| 0 < actualSize < requestedSize | PARTIALLY_ANSWERED | PARTIAL |
| actualSize == 0 | NOT_SUPPORTED 或 EMPTY | SKIPPED |

数量不足原因至少区分：

- `INSUFFICIENT_ELIGIBLE_PROJECTS`：公开候选本身不足；
- `INSUFFICIENT_EVIDENCE_SUPPORTED_PROJECTS`：候选存在，但证据门禁后不足；
- `CAPABILITY_COVERAGE_INCOMPLETE`：能力约束无法完全覆盖。

不得通过缩小 `requestedSize`、静默裁剪或仍返回 COMPLETED 来隐藏不足。

## 10. 正式预设统一投影

合法正式预设继续由服务端合同控制主体、Required Claims、Supporting Claims 和 Evidence Requirements。调整为：

1. 预设合同生成唯一 Canonical Answer Material；
2. Semantic Task 的 Section Result 从该 Material 构建；
3. 顶层兼容 Blocks 从同一 Material 构建；
4. 两者的正文、Claim、Evidence 和 Public Source Reference 必须一致；
5. 顶层兼容字段仅是迁移投影，不得重新检索或重新组织正文；
6. 后端永远不把内部 Evidence ID 当作用户文案。

兼容期测试逐字段比较顶层 Blocks 与 Completed Task Blocks 的公开内容。前端迁移完成后，再单独计划删除旧投影。

## 11. 运行版本一致性

实施和验收必须：

- 从当前 Git 工作树执行干净构建；
- 记录 commit、构建时间、内容版本和 JAR 哈希；
- 只针对该构建启动的已知进程执行真实 API 测试；
- 在报告中记录健康检查返回的内容版本与预期版本；
- 不复用无法证明来源的 8080 实例；
- 不在日志中打印问题正文、回答正文、ResumeToken、Prompt 或模型原始响应。

如果项目已有安全的 build metadata 入口则复用；没有时，实施计划只在测试编排层绑定 JAR 哈希和进程 PID，不为了调试新增公开管理接口。

## 12. 错误与降级

- 输入未形成：澄清，不调用模型和检索；
- 主体不明确：澄清并返回公开候选选项；
- 推荐部分满足：返回部分结果和结构化原因；
- 计划语义重复：拒绝执行并记录脱敏诊断，正常编译路径必须由回归测试确保不会触发；
- 证据不足：不得用模型补齐作品集事实；
- 模型主体兜底失败：回到确定性澄清，不改变任务类型；
- 兼容投影不一致：fail closed，不发布互相矛盾的正文和状态。

## 13. 测试与验收

### 13.1 单元测试

- `1`、`112233`、`!!!`、表情在有/无活动主体时均澄清；
- `给我推荐两个项目` 在有/无活动主体时均编译为 requestedSize=2、全公开 Project 范围；
- `给我推荐三个项目` 解析 requestedSize=3；
- `介绍这个项目` 仅在唯一可信活动主体存在时生成事实任务；
- 明确点名项目和可信 Context Handle 可以合法收窄；
- 相同 Goal 被稳定去重；
- 相同语义 Task 被 Validator 拒绝；
- SQL 正式预设只产生一个 PRIMARY Task；
- 推荐实际数量不足时为 PARTIALLY_ANSWERED；
- 顶层 Blocks 与 Completed Task Blocks 公共字段一致。

### 13.2 集成测试

- `/api/v2/answers` 噪声输入无 Blocks、Evidence 和 Source；
- 两项目推荐返回两项或结构化部分完成，不能返回一项 COMPLETED；
- 三项目推荐返回三项或结构化部分完成，不能静默裁剪；
- 活动 SQL 项目不污染通用推荐范围；
- 正式 SQL 预设只有一个执行任务和一组执行阶段；
- Evidence 引用只来自公开目录，且 Public Source Reference 完整。

### 13.3 Packaged JAR 验收

使用新构建 JAR 执行上述真实 API 场景，并记录：Git commit、JAR SHA-256、contentVersion、HTTP 状态、resolution、disposition、taskCount、requestedSize、actualSize、reasonCodes。禁止记录问题和回答正文。

## 14. 实施顺序

1. 建立运行版本一致性基线；
2. 修复输入形成和活动主体污染；
3. 修复推荐候选范围；
4. 增加 Goal 去重和 Plan 语义重复防线；
5. 增加推荐数量完整性与部分完成；
6. 统一正式预设公开投影；
7. 完成单元、集成和 packaged-JAR 验收。

## 15. 实施验收记录（2026-08-17）

本次验收针对当前工作树构建产物执行；未创建包含本次变更的新提交，因此下列 commit 仅表示工作树基线，不表示变更已经提交。

- 工作树基线 commit：`9980068dec8fa33b06ce59fa27b0de1427b54603`；
- JAR SHA-256：`9f8212fa005f721ecf8693fa48987d164aee1196095ec4a9622b44b3ad23798c`；
- JAR 构建时间（UTC）：`2026-08-17T06:35:11.3215799Z`；
- 运行内容版本：`2026-08-05.1`；
- 后端全量测试：1256 项，0 failure，0 error，21 项因本机 Docker/Testcontainers 不可用按环境条件跳过；
- 前端全量测试：65 个文件、723 项通过；`vue-tsc -b` 与 Vite 生产构建通过；
- 随包运行器自测：通过；
- 确定性 packaged-JAR 冒烟：通过，运行时 PID/端口/contentVersion 与目标进程一致；噪声输入为 `NEEDS_CLARIFICATION`，三项目推荐为 `ANSWERED`，`taskCount=1`、`requestedSize=3`、`actualSize=3`、`reasonCodeCount=0`；
- DeepSeek V4 Flash 真实外部 Provider canary：针对同一 JAR 哈希通过；
- 隐私门禁：结构化 stdout 检查通过，验收输出未记录问题正文、回答正文、Prompt、ResumeToken、密钥或模型原始响应。

结论为：本设计范围内的后端确定性闭环、随包验收与真实 DeepSeek 可调用性已经完成本地验证，尚未生产部署。该证据不启用 `MODEL_LED`，也不外推为 P3-P8、完整 L4 Provider Eval、Docker/PostgreSQL 或真实浏览器 real-api lane 已完成。
