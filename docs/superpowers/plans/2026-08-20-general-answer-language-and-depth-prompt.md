# 通用回答语言与深度提示词实施计划
<!-- DOCUMENT_STATUS: ACTIVE -->

> **状态：** 用户已批准并授权直接实施；真实 Provider 行为门仍需单独授权。
> **批准设计：** `docs/superpowers/specs/2026-08-20-general-answer-language-and-depth-prompt-design.md`
> **Guardian：** LEVEL_2。公开合同、State、API、Role 枚举、时间轴和模型参数保持不变。

## 目标

把 Goal Interpretation 与 General Knowledge 的 system prompt 收敛为两个随 JAR 发布、启动时无条件加载的 UTF-8 资源；固定生成文案为简体中文；让 Goal 选择并让 General 执行 CONCISE/STANDARD/DETAILED；以严格草稿校验阻止重复/乱序区块；补齐脱敏 canary 与发布门。

## 执行约束

- 按 TDD 先写失败测试，再写生产实现。
- 不修改 `max_tokens`、温度、10 秒 General 上限或其他冻结时间轴。
- 不新增模型调用、重试、运行时 prompt 覆盖、生产日志字段或兼容链。
- 未获真实 Provider 授权时按主动质量改进实施，不写入 `docs/15`，不宣称真实缺陷已修复。
- 不提交、不推送；完成后交付工作树差异与验证证据。

## Task 1：批准设计并冻结计划

**文件：**
- 修改：`docs/superpowers/specs/2026-08-20-general-answer-language-and-depth-prompt-design.md`
- 新增：本计划

- [x] 将设计状态改为 `APPROVED`，保留真实 Provider 单独授权边界。
- [x] 写入本计划并冻结文件、接口、测试和门禁顺序。

## Task 2：资源与无条件 Catalog

**文件：**
- 新增：`backend/src/main/resources/prompts/goal-interpretation-system.txt`
- 新增：`backend/src/main/resources/prompts/general-knowledge-system.txt`
- 删除：`backend/src/main/resources/prompts/portfolio-agent-system.zh-CN.txt`
- 新增：`backend/src/main/java/com/portfolio/agent/infrastructure/model/SystemPromptCatalog.java`
- 新增：`backend/src/test/java/com/portfolio/agent/infrastructure/model/SystemPromptCatalogTest.java`
- 修改：`backend/src/test/java/com/portfolio/agent/turn/infrastructure/AgentCapabilityConfigurationTest.java`

- [x] 先写生产资源可加载、缺失/空白/malformed UTF-8 失败的测试并验证 RED。
- [x] 实现严格 UTF-8、trim、稳定安全异常的 final Catalog。
- [x] 在 Spring 装配中无条件创建 Catalog，证明 DISABLED 模式仍加载资源。

## Task 3：适配器改为 prompt 注入

**文件：**
- 修改：`backend/src/main/java/com/portfolio/agent/turn/infrastructure/model/GoalInterpretationAdapter.java`
- 修改：`backend/src/main/java/com/portfolio/agent/turn/infrastructure/model/OpenAiCompatibleGeneralKnowledgeAdapter.java`
- 修改：`backend/src/main/java/com/portfolio/agent/turn/infrastructure/AgentCapabilityConfiguration.java`
- 修改：`backend/src/test/java/com/portfolio/agent/turn/infrastructure/model/GoalInterpretationAdapterTest.java`
- 新增：`backend/src/test/java/com/portfolio/agent/turn/infrastructure/model/OpenAiCompatibleGeneralKnowledgeAdapterTest.java`
- 修改：`backend/src/test/java/com/portfolio/agent/turn/planning/GoalProposalCodecTest.java`

- [x] 先写请求捕获和三档 depth 解码/传递测试并验证 RED。
- [x] 删除两个 Java prompt 常量，经构造器注入 Catalog 内容。
- [x] 证明 user JSON、token、temperature 与 deadline 投影保持不变。

## Task 4：EXPLANATION 草稿收口

**文件：**
- 修改：`backend/src/main/java/com/portfolio/agent/turn/capability/general/GeneralDraftValidator.java`
- 修改：`backend/src/test/java/com/portfolio/agent/turn/capability/general/GeneralDraftValidatorTest.java`
- 修改/新增：General presentation 相关测试 fixture

- [x] 先补重复角色、COMPARISON 混入、MECHANISM 在前的失败测试并验证 RED。
- [x] 强制恰好 `DEFINITION -> MECHANISM`，保持 COMPARISON 覆盖校验不变。
- [x] 覆盖三档代表输出的两个主区块、标题、顺序与 caveats 投影。

## Task 5：脱敏 canary 与 JAR 门

**文件：**
- 新增：`scripts/assert-live-general-answer-quality.ps1`
- 新增：`scripts/assert-live-general-answer-quality.test.ps1`
- 修改：`scripts/verify-release.ps1`
- 按需修改：`scripts/verify-release.test.ps1`

- [x] 先用 fixture 覆盖结构、句数桶、句段级中文判定、稳定失败码和聚合输出，验证 RED。
- [x] 实现完全非交互、任何路径不打印正文的 baseline/验收两模式。
- [x] 验证 URL、反引号代码、明确整段代码、技术词和中英混排正例，以及 3–5 词英文、括号/分号英文反例。
- [x] 为最终 JAR 增加两个 prompt 资源必含断言。

## Task 6：全量验证与文档收尾

- [x] 运行受影响 Java 测试与 PowerShell 脚本自测。
- [ ] 运行 Backend 全量测试、Frontend check/build、documentation/privacy/release/architecture 门。
- [x] 已获 Provider 授权；修改前基线完成，修复后自动行为矩阵全部通过，无需创建 `AUTHORIZATION` WAIVED；独立浏览器语义覆盖仍作为关闭 A2-20/A2-21 的剩余门。
- [x] 主动质量改进完成后更新 `docs/11-项目演进日志.md`；只有基线真实复现后才按规则维护 `docs/15`。
- [ ] 输出最终差异、验证结果与仍待授权的行为门，不作超证据完成声明。
