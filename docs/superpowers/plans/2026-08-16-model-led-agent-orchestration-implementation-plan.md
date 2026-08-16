# 模型主导 Agent 编排重构实施计划

> **日期：** 2026-08-16
> **状态：** 待设计审阅通过后执行；当前禁止修改生产代码
> **设计依据：** `docs/superpowers/specs/2026-08-16-model-led-agent-orchestration-design.md`
> **实施方式：** 隔离 worktree、TDD、分阶段切换、中文提交、最终统一审核

## 1. 实施原则

1. 每个行为变更严格遵循 RED → GREEN → REFACTOR；
2. 先建立行为与安全门禁，再接真实 Provider；
3. 新旧链路不得同时拥有最终决策权；
4. `SHADOW` 只比较结构结果，不改变用户响应；
5. 每个阶段都必须可通过配置回滚；
6. 不记录问题、回答、模型原文或自由文本上下文；
7. 普通 CI 使用 Fake/Mock Provider，真实 Provider 仅显式授权运行；
8. 独立任务完成后使用中文 commit，最终由用户统一审核；
9. 设计未批准前本计划只用于审阅，不执行以下生产改动。

## 2. 阶段与依赖

```text
P0 冻结行为基线
  ↓
P1 提议领域契约与严格 Codec
  ↓
P2 后端编译、校验与最小 fallback
  ↓
P3 Provider Adapter 与 SHADOW
  ↓
P4 CONVERSATIONAL / 澄清交互
  ↓
P5 单任务 MODEL_LED
  ↓
P6 多任务、依赖与 Context v2
  ↓
P7 stp-v3 前后端联调
  ↓
P8 真实 Provider、发布门禁与旧规则清理
```

## 3. P0：冻结行为基线

### 目标

把本次审计发现转化成实现前必然失败的自动化案例，防止重构只改变类结构而不改善体验。

### RED

- `112233` 预期 `CONVERSATIONAL` 且无 Evidence；
- 单 Emoji、随机符号、寒暄、致谢和结束语预期自然交流；
- 相似 Preset 文案不得误走精确 Preset；
- Project/Case Hint 必须进入首轮请求；
- 本轮显式主体必须覆盖页面 Hint；
- 澄清、边界和能力不可用响应不得带来源；
- Provider 故障不得产生错误执行；
- Prompt 注入不得创建主体、工具或 Evidence。

### 交付

- 扩充行为 fixture、Oracle 和统一 Verdict；
- 场景标签覆盖输入类型、上下文类型、Provider 状态和 UI 路径；
- 固化旧链路观察结果，不把当前错误写成期望。

### 建议提交

`test: 冻结模型主导编排行为与安全基线`

## 4. P1：提议领域契约与严格 Codec

### RED

- 未知字段、未知枚举、重复字段、超限任务、Kind/字段混用全部拒绝；
- 模型不能输出工具、Evidence、Provider、执行状态或可信 ID；
- `CONVERSE`、`ASK_CLARIFICATION`、`PROPOSE_EXECUTION` 互斥；
- 自由文本有严格长度和字符预算；
- 输入投影不包含私有、签名或无关证据字段。

### GREEN

- 新建不可变 `TurnInterpretationInput`、`TurnProposal` 和强类型子对象；
- 新建 `TurnInterpretationPort`；
- 实现严格 `TurnProposalCodec`；
- 明确 `model-turn-proposal-v1` Schema；
- 增加捕获型 Adapter 测试验证 Provider 输入最小化。

### REFACTOR

- 保持 Gateway 厂商无关；
- 禁止 `Map<String, Object>` 进入核心域；
- 普通 Java 类显式不可变，不使用 `var`、`record` 或 Lombok。

### 建议提交

`feat: 建立受控模型轮次提议契约`

## 5. P2：后端编译、校验与最小 fallback

### RED

- 模型候选不存在或未公开时不能执行；
- 依赖自环、有环、缺引用或 Synthesis 不一致时不能执行；
- 非法任务不能被静默删除后部分执行；
- 主体显式表达、已确认主体、结果引用和页面 Hint 的优先级必须符合设计；
- Provider 不可用时旧大词典不得成为默认第二路由器。

### GREEN

- 将 `SemanticPlanCompiler` 收敛为 `ProposalCompiler` 职责；
- 扩展 `SemanticPlanValidator` 的主体目录、能力与交互验证；
- 服务端生成 taskId、planId、fingerprint、确认策略和最终 disposition；
- 新建仅处理结构化动作的 `MinimalTurnFallback`；
- 为非法提议建立可公开的闭集原因码。

### REFACTOR

- 不让 Compiler 重新扫描问题文本猜测意图；
- 把主体绑定、图验证、交互文案限制拆成职责明确的内部对象；
- 保持 `TurnRouter` 为唯一公共 seam。

### 建议提交

`refactor: 收口模型提议编译与确定性验证边界`

## 6. P3：Provider Adapter 与 SHADOW 模式

### RED

- `LEGACY / SHADOW / MODEL_LED` 配置必须互斥且有安全默认值；
- Key 存在不能自动启用模型路由；
- 超时、429、5xx、截断、非法 JSON 和未知 Schema 必须映射到稳定失败码；
- SHADOW 不能改变响应、计划或执行次数；
- 诊断不得包含问题、模型 JSON 或回复文案。

### GREEN

- 在现有固定 OpenAI-compatible Adapter 中加入独立 turn-interpretation operation；
- 独立配置超时、输入预算、输出预算和开关；
- SHADOW 同时产生旧决定与新提议，但只输出无文本差异指标；
- 增加 Fake Provider 和故障注入测试；
- 增加 readiness/diagnostics 的 operation 状态。

### REFACTOR

- 复用 Provider Registry 与故障码，不新增动态注册层；
- 不自动故障转移到另一 Provider；
- 同一 ASK 路由模型调用不超过一次。

### 建议提交

`feat: 接入模型轮次解释与影子评测模式`

## 7. P4：交流恢复与澄清

### RED

- `112233`、Emoji、寒暄等返回 `CONVERSATIONAL`；
- 交流恢复不得携带 Evidence、Claim、来源目录或任务成功状态；
- 通用事实问题不能误走 `CONVERSE`；
- 超长、越权、带工具自述的回复草稿必须回退服务端模板；
- 澄清文案失败不能阻塞确定性澄清选项。

### GREEN

- 增加 `CONVERSATIONAL` 交互领域类型；
- 校验短回复长度、禁止字段和能力表述；
- 服务端提供少量自然但无事实的安全 fallback 文案；
- 澄清仍由后端生成 ID、代码、选项和影响范围；
- Response Mapper 强制非 ANSWER 无 Evidence。

### REFACTOR

- 删除 `GENERAL_EXPLANATION` 作为纯噪声兜底的行为；
- 不用随机数字、Emoji 或寒暄词典扩充规则；
- 将交流恢复和通用知识执行保持为两条清楚的语义路径。

### 建议提交

`feat: 增加自然交流恢复与无来源澄清`

## 8. P5：单任务 MODEL_LED

### RED

- 普通事实、比较、推荐、通用解释的任务和主体必须来自模型提议并经后端验证；
- 明确主体冲突必须澄清；
- 规则不得在模型后改变 taskType；
- 作品集回答仍必须通过公开检索、Composer 和 Grounding；
- Preset 与确认路径模型调用为零。

### GREEN

- `DefaultTurnRouter` 在普通 ASK 中调用 `TurnInterpretationPort`；
- 提议通过 Compiler/Validator 后进入现有 Coordinator；
- 先只允许一个任务自动执行；
- 加入 `MODEL_LED` 单任务开关和紧急回滚；
- 扩充路由来源和降级结构指标。

### REFACTOR

- 移除模型只在 unresolved subject 时进入的旧条件；
- 确保旧 `SemanticSignalCollector` 不再拥有 MODEL_LED 最终决策权；
- 保持执行器完全不依赖模型 Adapter。

### 建议提交

`refactor: 切换单任务语义理解到受控模型`

## 9. P6：多任务、依赖与 Context v2

### RED

- 一到六个任务、超过六个拆分、依赖 DAG、排除和 Synthesis 均有覆盖；
- pageHint 不能覆盖本轮显式主体；
- 清除 Hint 后不得继续发送；
- recentResults 和 confirmedSubjects 必须重新验证 contentVersion；
- 历史自由文本不能直接成为事实或主体；
- 首轮 Case/Project Hint 必须真实到达后端。

### GREEN

- 实现 `TurnContext` 新字段及 legacy adapter；
- 前端按页面内存维护 Hint、确认主体、近期结果、待交互和最近目标；
- 最多四轮临时对话窗口进入模型输入投影；
- 开放多任务提议、依赖编译、确认和局部澄清；
- 修复现有 `preparedContext.semanticContext` 漏传。

### REFACTOR

- 移除 `activeSubjects` 同时表示页面来源和确认主体的歧义；
- 上下文适配集中在单一边界，组件不得各自猜测优先级；
- 保持所有上下文只在标签页内存。

### 建议提交

`feat: 重构临时上下文与多任务模型编排`

## 10. P7：stp-v3 前后端联调

### RED

- 六类 interaction.kind 的后端契约、Mapper 和前端状态测试；
- v1/v2/v3 协商、兼容投影和不支持版本处理；
- 非 ANSWER 响应存在来源时测试必须失败；
- 桌面、平板和移动端均能操作澄清、确认、交流恢复和清除 Hint；
- 读屏文本不能把内部计划或原因码直接读给用户。

### GREEN

- 增加 stp-v3 DTO 与 Mapper；
- 前端按交互类型渲染，不从旧 resolution 猜测 UI；
- 交流恢复显示为正常 Assistant 消息，不显示证据工作台；
- 澄清、能力不可用和边界采用不同且自然的文案层级；
- 完成 packaged-JAR 无 mock E2E。

### REFACTOR

- `agentTurn` 保持唯一权威；
- 移除前端由多个 legacy 字段组合推断状态的分支；
- 控制兼容窗口，避免 v2/v3 长期双写。

### 建议提交

`feat: 交付模型主导编排第三版交互契约`

## 11. P8：真实 Provider 验收与旧规则清理

### 验收顺序

1. 后端全量测试；
2. 前端全量 Vitest；
3. 前端生产构建；
4. 架构、代码质量和隐私检查；
5. Fake Provider 全故障矩阵；
6. DeepSeek/当前显式选择 Provider 的 readiness 与 diagnostics；
7. 冻结场景集多上下文真实 Provider 运行；
8. packaged-JAR API 与浏览器全路径；
9. 对比 SHADOW 和 MODEL_LED Verdict；
10. 完整发布验证。

### 清理条件

只有在 MODEL_LED 门禁通过、回滚演练成功、真实 Provider 和浏览器结果留证后，才可以：

- 删除 `SemanticClassifierPort` 旧语义；
- 删除或极小化 `SemanticSignalCollector`；
- 移除 `SemanticPlanCompiler` 的关键词推断；
- 删除只为旧 `activeSubjects` 语义存在的适配分支；
- 默认启用 MODEL_LED；
- 将文档状态从“待审阅/实施中”切换为“已实现/已验证”。

### 建议提交

`refactor: 移除旧规则语义决策链`

`docs: 同步模型主导编排实现与验收状态`

## 12. 最终验证命令

```powershell
mvn.cmd -f backend/pom.xml test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
powershell -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1
powershell -ExecutionPolicy Bypass -File scripts/architecture-check.ps1
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
mvn.cmd -f backend/pom.xml package
powershell -ExecutionPolicy Bypass -File scripts/run-jar-e2e.ps1
```

真实 Provider、PostgreSQL、完整发布脚本和任何外部依赖路径必须单独记录 `PASS / FAIL / BLOCKED / INCOMPLETE`，不得由 Mock 或单元测试替代。

## 13. 文档维护

设计批准后开始实施时：

- 将本设计和计划登记到 `docs/00-文档状态索引.md`；
- 行为、默认开关或能力边界真实改变后更新 `docs/08-当前实现状态.md`；
- 每个独立重要行为交付更新 `docs/11-项目演进日志.md`；
- 更新 `docs/13-Agent对话体验与智能编排改造路线图.md` 中阶段 2/5/6 的真实状态；
- 审计报告保留为改造前证据，不回写成实施结果。
