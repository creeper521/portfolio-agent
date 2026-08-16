# 模型主导 Agent 编排重构实施计划

> **日期：** 2026-08-16
> **状态：** 第三版已吸收两轮独立架构评审，待用户批准后执行；当前禁止修改生产代码
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
P-1 审计 P1 缺陷热修
  ↓
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
P8 真实 Provider 与发布门禁（默认启用及旧规则清理由后续批准触发）
```

## 3. P-1：审计 P1 缺陷热修

### 目标

不等待长周期重构，先在当前权威设计允许范围内修复两个已经确认的 P1 产品缺陷，并补齐同次审计发现的公开内容缓存头 P2 缺口。P-1 与核心重构设计解耦，可由用户单独批准立即执行；本计划当前状态本身仍不构成实施授权。

### RED

- `112233`、纯噪声和无法形成任务的输入不得映射成 `GENERAL_EXPLANATION`；
- 澄清、边界、能力不可用和噪声结果的 evidenceIds/publicSourceCatalog 必须为空；
- `ANSWER/PARTIAL` 的安全回答仍保留合法 evidenceIds/publicSourceCatalog，不得被清空修复误伤；
- Project/Case 构造的 semanticContext 必须真实进入首轮 API 请求；
- 清除页面 Hint 后的首轮请求不得继续携带旧上下文；
- `GET /api/v1/public-content` 必须返回 `Cache-Control: no-store`。

### GREEN

- 删除无主体噪声兜底成通用解释的行为，先映射为现有安全澄清；
- 在当前响应 Mapper 回补非回答无 Evidence 不变量；
- 修复前端 `preparedContext.semanticContext` 合并与发送路径；
- 为公开内容聚合响应增加 `Cache-Control: no-store`；
- 增加行为纯函数、HTTP DTO、缓存头和前端请求捕获测试。

### 建议提交

`fix: 修复模糊输入错误携带公开来源`

`fix: 修复首轮页面语义上下文漏传`

## 4. P0：冻结行为基线

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

## 5. P1：提议领域契约与严格 Codec

### RED

- 未知字段、未知枚举、重复字段、超限任务、Kind/字段混用全部拒绝；
- 模型不能输出工具、Evidence、Provider、执行状态或可信 ID；
- `CONVERSE`、`ASK_CLARIFICATION`、`PROPOSE_EXECUTION` 互斥；
- `PROPOSE_EXECUTION` 空任务必须拒绝；
- inputAnchor/topicAnchors/主体 evidenceAnchor 必须用 `TextAnchor(verbatimText, occurrence)` 精确锚定 currentInput，由后端生成 Java UTF-16 TextSpan；
- 重复原文、occurrence 越界、Emoji/代理对、空锚点和不存在原文必须确定性拒绝；
- 多主体任务中的每个 SubjectCandidate 必须分别携带并验证 basis；
- 七类任务的强类型参数必须全部可由提议字段与服务端派生规则构造；
- 自由文本有严格长度和字符预算；
- 输入投影不包含私有、签名或无关证据字段。

### GREEN

- 新建不可变 `TurnInterpretationInput`、`TurnProposal` 和强类型子对象；
- 新建 `TurnInterpretationPort`；
- 实现严格 `TurnProposalCodec`；
- 明确 `model-turn-proposal-v1` Schema；
- 补齐 facets、topicAnchors、careerTrack、capabilityFilters、requestedSize；
- 固化 `CONCISE → BRIEF` 映射，并由后端固定派生 recommend.goal 与 synthesis.synthesisGoal；
- 模型不输出 fulfillmentRole，由后端从依赖与任务位置推导；
- 增加捕获型 Adapter 测试验证 Provider 输入最小化。

### REFACTOR

- 保持 Gateway 厂商无关；
- 禁止 `Map<String, Object>` 进入核心域；
- 普通 Java 类显式不可变，不使用 `var`、`record` 或 Lombok。

### 建议提交

`feat: 建立受控模型轮次提议契约`

## 6. P2：后端编译、校验与最小 fallback

### RED

- 模型候选不存在或未公开时不能执行；
- 依赖自环、有环、缺引用或 Synthesis 不一致时不能执行；
- 非法任务不能被静默删除后部分执行；
- 主体显式表达、已确认主体、结果引用和页面 Hint 的优先级必须符合设计；
- 五种 subjectBasis 必须逐项匹配对应服务端证据源，UNKNOWN 不得绑定；
- 显式别名 X 与模型候选 Y 冲突时不得执行；
- PAGE_HINT 只能由随应用版本审核的 `page-reference-markers` 配置目录证明，不得由 Java 散落正则自行扩展；该目录不改变公开 Bundle 契约；
- reviewedAlias 必须按 NFKC、trim、Locale.ROOT 大小写折叠匹配；同类型跨主体冲突使目录加载/发布 fail-closed，跨类型冲突必须澄清；
- Provider 不可用时旧大词典不得成为默认第二路由器。

### GREEN

- 将 `SemanticPlanCompiler` 收敛为 `ProposalCompiler` 职责；
- 扩展 `SemanticPlanValidator` 的主体目录、能力与交互验证；
- 服务端生成 taskId、planId、fingerprint、确认策略和最终 disposition；
- 新建只处理合同动作、精确唯一别名概览的 `MinimalTurnFallback`；
- 为非法提议建立可公开的闭集原因码。

### REFACTOR

- 不让 Compiler 重新扫描问题文本猜测意图；
- 把主体绑定、图验证、交互文案限制拆成职责明确的内部对象；
- 保持 `TurnRouter` 为唯一公共 seam。

### 建议提交

`refactor: 收口模型提议编译与确定性验证边界`

## 7. P3：Provider Adapter 与 SHADOW 模式

### RED

- `LEGACY / SHADOW / MODEL_LED` 配置必须互斥且有安全默认值；
- Key 存在不能自动启用模型路由；
- 超时、429、5xx、截断、非法 JSON 和未知 Schema 必须映射到稳定失败码；
- SHADOW 不能改变响应、计划或执行次数；
- SHADOW 响应必须与 LEGACY 字节级一致，旁路队列饱和和失败不得影响主链路；
- Interpretation 2.5 秒、共享 10 秒和请求 12 秒预算必须可验证；
- Interpretation 2.4 秒慢成功与后续表达调用组合仍不得突破共享/请求预算；
- Interpretation 超时不得沿用 conversational operation 的单一 8 秒 RestClient 超时；
- 应用关停时 SHADOW 队列安全丢弃或排水，不泄漏问题文本、不阻塞正式请求；
- 诊断不得包含问题、模型 JSON 或回复文案。

### GREEN

- 在现有固定 OpenAI-compatible Adapter 中加入独立 turn-interpretation operation；
- 按 operation 拆分超时、请求执行持有者和进程内熔断（独立 RestClient 工厂或等价请求级超时），同时保持单一 Provider Registry；
- 独立配置超时、输入预算、输出预算和开关；
- SHADOW 使用独立有界内存执行器异步产生新提议，只输出无文本差异指标；
- 增加 Fake Provider 和故障注入测试；
- 增加 readiness/diagnostics 的 operation 状态。

### REFACTOR

- 复用 Provider Registry 与故障码，不新增动态注册层；
- 不自动故障转移到另一 Provider；
- 同一 ASK 路由模型调用不超过一次。

### 建议提交

`feat: 接入模型轮次解释与影子评测模式`

## 8. P4：交流恢复与澄清

### RED

- `112233`、Emoji、寒暄等返回 `CONVERSATIONAL`；
- 交流恢复不得携带 Evidence、Claim、来源目录或任务成功状态；
- 通用事实问题不能误走 `CONVERSE`；
- 规划提议不得包含 conversationReply 或自由文本能力建议；
- Recovery 的超长、URL、未知 action ID、ID 模式、越权和工具自述必须回退服务端模板；
- Recovery 输出命中任何 reviewedAlias 时必须回退服务端模板；
- allowedActionIds 首版只能来自当前 ACTIVE QuestionPreset ID；
- 澄清文案失败不能阻塞确定性澄清选项。

### GREEN

- 增加 `CONVERSATIONAL` 交互领域类型；
- 新建独立 `ConversationRecoveryPort` 及严格 Draft Codec，但配置默认关闭；
- action ID 由后端闭集提供并渲染，模型不得创造能力名称；
- 服务端提供少量自然但无事实的安全 fallback 文案；
- 澄清仍由后端生成 ID、代码、选项和影响范围；
- Response Mapper 强制非 ANSWER 无 Evidence。

### REFACTOR

- 删除 `GENERAL_EXPLANATION` 作为纯噪声兜底的行为；
- 不用随机数字、Emoji 或寒暄词典扩充规则；
- 将交流恢复和通用知识执行保持为两条清楚的语义路径。
- 本阶段只在后端与测试中可见，不增加第四种运行模式，不向当前 stp-v2 客户端开放。
- 首个 MODEL_LED 发布继续使用服务端模板；模型 Recovery 只有在真实评测证明自然度收益并取得独立发布批准后才启用。

### 建议提交

`feat: 增加自然交流恢复与无来源澄清`

## 9. P5：单任务 MODEL_LED

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
- 仅在测试或显式非默认环境开放，不改变正式客户端合同；
- 扩充路由来源和降级结构指标。

### REFACTOR

- 移除模型只在 unresolved subject 时进入的旧条件；
- 确保旧 `SemanticSignalCollector` 不再拥有 MODEL_LED 最终决策权；
- 保持执行器完全不依赖模型 Adapter。

### 建议提交

`refactor: 切换单任务语义理解到受控模型`

## 10. P6：多任务、依赖与 Context v2

### RED

- 一到六个任务、超过六个拆分、依赖 DAG、排除和 Synthesis 均有覆盖；
- pageHint 不能覆盖本轮显式主体；
- 清除 Hint 后不得继续发送；
- recentResults 和 confirmedSubjects 必须重新验证 contentVersion；
- recentResults 必须完成签发→标签页内存→回传→复验全链，覆盖篡改、过期、重复位置和版本变化；
- recentResults 必须携带 position、subjectId、contentVersion、expiresAt 与 integrityToken，以支持受控序数指代；
- confirmedSubjects 只由显式主体、受控澄清选择或成功任务更新，按 MRU 最多保留 3 个；最近一轮含多个并列主体时单数“它”必须澄清；
- pendingInteraction、confirmedSubjects、页面 Hint 和临时窗口使用完整优先级；
- 历史自由文本不能直接成为事实或主体；
- 首轮 Case/Project Hint 必须真实到达后端。

### GREEN

- 实现 `TurnContext` 新字段及 legacy adapter；
- 后端复用现有完整性机制签发/复验短时无状态 recentResults 引用；
- 前端按页面内存维护 Hint、确认主体、带签名位置的近期结果、待交互和最近目标；
- 最多四轮临时对话窗口进入模型输入投影；
- 保留 audienceRole、requestSource 和 coveredTopics；
- 开放多任务提议、依赖编译、确认和局部澄清；

### REFACTOR

- 移除 `activeSubjects` 同时表示页面来源和确认主体的歧义；
- 上下文适配集中在单一边界，组件不得各自猜测优先级；
- 保持所有上下文只在标签页内存。

### 建议提交

`feat: 重构临时上下文与多任务模型编排`

## 11. P7：stp-v3 前后端联调

### RED

- 六类 interaction.kind 的后端契约、Mapper 和前端状态测试；
- 当前前端默认 v2 → 目标 v3 的协商、v1 显式基础回退、v2 限时接受路径和不支持版本处理；
- 过渡矩阵覆盖旧 v2 客户端/新后端、新 v3 客户端/旧后端、LEGACY 模式回滚和二进制整体回滚；
- 自动化必须禁止会造成全站 409 的发布中间态；
- interaction.kind 是唯一公共 UI 权威，v3 不暴露第二套 disposition 状态机；
- 非 ANSWER 响应存在来源时测试必须失败；
- 桌面、平板和移动端均能操作澄清、确认、交流恢复和清除 Hint；
- 读屏文本不能把内部计划或原因码直接读给用户。

### GREEN

- 增加 stp-v3 DTO 与 Mapper；
- 新后端先同时接受 v1/v2/v3，前端默认合同再从 v2 切到 v3；普通只读 ASK 可在明确的 v3 不支持 409 后投影为 v1 兼容请求并自动重试一次，continuation 不跨合同自动重放；
- 前端按交互类型渲染，不从旧 resolution 猜测 UI；
- 交流恢复显示为正常 Assistant 消息，不显示证据工作台；
- 澄清、能力不可用和边界采用不同且自然的文案层级；
- 完成 packaged-JAR 无 mock E2E。

### REFACTOR

- `agentTurn` 保持唯一权威；
- 移除前端由多个 legacy 字段组合推断状态的分支；
- v2 只保留到旧前端缓存/部署窗口结束且请求版本指标归零；此后删除其兼容代码，避免 v1/v2/v3 长期并列演化。

### 建议提交

`feat: 交付模型主导编排第三版交互契约`

## 12. P8：真实 Provider 验收与发布门禁

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
10. 验证冻结集数字门禁、三次关键场景重复和时间预算；
11. 修订依赖 `intentSource=RULE` 的 canary 契约；
12. 完整发布验证。

### 发布候选条件

只有在 MODEL_LED 门禁通过、回滚演练成功、真实 Provider 和浏览器结果留证后，才可以将本实现标记为“具备发布候选资格”。P8 阶段可以：

- 将新链路文档状态从“待审阅/实施中”切换为“已实现/已验证但未生产默认启用”；
- 标记 `SemanticClassifierPort`、`SemanticSignalCollector`、旧 `SemanticPlanCompiler` 关键词推断和旧 `activeSubjects` 适配分支为待稳定期清理；
- 保留 LEGACY 回滚链，不在生产默认仍为旧模式时先删除它。

P8 完成只代表代码、真实 Provider、浏览器、回滚和量化门禁已具备发布条件，不自动改变生产默认模式。`MODEL_LED` 的生产默认启用必须形成独立发布决策，由用户审阅 P8 留证后明确批准；批准前保持当前安全默认值，并保留经过演练的回滚入口。

用户明确批准生产默认启用并通过稳定观察窗口后，才执行旧规则清理：删除 `SemanticClassifierPort` 旧语义，删除或极小化 `SemanticSignalCollector`，移除 `SemanticPlanCompiler` 的关键词推断，以及删除只为旧 `activeSubjects` 语义存在的适配分支。清理仍需单独中文提交和全量回归，不得作为 P8 验收的隐含副作用。

### 建议提交

`refactor: 移除旧规则语义决策链`

`docs: 同步模型主导编排实现与验收状态`

## 13. 最终验证命令

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

## 14. 文档维护

设计批准后开始实施时：

- 将本设计和计划登记到 `docs/00-文档状态索引.md`；
- 行为、默认开关或能力边界真实改变后更新 `docs/08-当前实现状态.md`；
- 每个独立重要行为交付更新 `docs/11-项目演进日志.md`；
- 更新 `docs/13-Agent对话体验与智能编排改造路线图.md` 中阶段 2/5/6 的真实状态；
- 审计报告保留为改造前证据，不回写成实施结果。
