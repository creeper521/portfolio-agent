# PortfolioIntelligence 单一内核收敛设计评审
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

日期：2026-08-03

状态：外部 AI 评审输出，待设计作者关闭问题

评审对象：`2026-08-03-portfolio-intelligence-convergence-design.md`

评审方式：阅读设计文档并对照当前仓库代码逐条复核，所有引用均带文件与行号

---

## 0. 已验证的设计判断（与代码一致）

| 设计章节 | 主张 | 代码证据 |
|---|---|---|
| §2.1 路由权力分散 | 决策散在 7 个组件 | `ConversationalAgentRuntime.java:139-339`、`ConversationIntentRouter.java:45-135`、`PortfolioTaskResolver.java:63-119`、`DeterministicConversationFallback.java:42-172`、`QuestionResolver.java:26-38`、`PortfolioAgentRuntime.java:116-159`、`PortfolioIntelligenceAnswerAssembler.java:36-79` |
| §2.2 v2 缺 `questionPresetId` | DTO 与 JSON 序列化均不带 | `ConversationAnswerRequest.java:15-50`（无该字段）、`frontend/src/features/agent/api/answerApi.ts:39-63`（`body` 构造不含 `questionPresetId`）、`frontend/src/features/agent/components/AgentWorkspace.vue:350` 把 `questionPresetId` 传给 `askQuestion` 但被丢在线上 |
| §2.3 resolve/route 优先级不一致 | 模型 boundary 可越过规则 | `PortfolioTaskResolver.java:91-99`：`route()` 在评估 ruleMode **之前** 先用 `classification.getBoundaryIntent()` 短路；`resolve()` 第 67-79 行则先查规则再回落分类 |
| §2.4 精确主体绕过相关性 | exactPassages 不再排序 | `BundlePortfolioRetriever.java:96-99` 与 `126-132`：`isExactPortfolioLookup()` 时直接 `exactPassages()`，**没有调用 `retrievalCoordinator.retrieve()`**，因此丢失 query/topic/claim category/relevance/充分性 |
| §2.5 字段语义混合 | BOUNDARY 表达多义 | `PortfolioIntelligenceAnswerAssembler.java:67`：clarification → BOUNDARY；`DeterministicConversationFallback.java:73-86` 时间敏感与"模型不可用"也都返回 BOUNDARY；`ConversationAnswerResult.java:37` `degraded ? FALLBACK : DETERMINISTIC` 把"是否走 fallback"和"是否用模型"压成单一字段 |
| §2.6 本地日志依赖启动脚本 | Logback 不写文件 | `backend/src/main/resources/application-local.yml:1-4` 只配置 level；仓库无 `logback*.xml`；`scripts/start-local.ps1:493-516` 是创建 `logs/` 的唯一入口 |

设计文档的"问题证据"一节基本经得起代码复核。

---

## 1. 职责边界

### 1.1 严重：`tryResolve(PortfolioTurn)` 是能力扩张而非"机械搬家"

设计 §1、§3 把这次工作描述为"收进 PortfolioIntelligence"，但对照现状：

- 现 `PortfolioIntelligence.java:6-9` 接口只接受**已分类好的** `PortfolioTask`。
- 现 `DefaultPortfolioIntelligence.java:45-59` 只做 taskValidator + retrieve/recommend/refine，**不做** preset 匹配、规则解析、模型分类、主体解析、混合编排、自由文本表达。

设计 §5.3、§7 提议把以下职责搬进 PI：

1. 预设 ID/canonical/alias 匹配（当前在 `DeterministicConversationFallback.presetAnswer:130-172` 与 `QuestionResolver.findPreset:79-91`）
2. 高精度规则（当前在 `PortfolioTaskResolver.resolveRule:186-201`）
3. 受限模型分类（当前在 `PortfolioTaskResolver.classify:155-162` 与 `ConversationIntentRouter.route:45-80`）
4. 主体解析与校验（当前分散在 `ConversationSubjectGuard`、`ConversationIntentRouter.routeHint:157-179`、`ConversationalAgentRuntime.withSubjectConstraint:492-522`）
5. 混合编排（当前在 `ConversationalAgentRuntime.answerHybridWithPortfolioIntelligence:396-467`）
6. 自由文本的受约束模型表达（**当前不存在**，是新增能力）

第 6 项尤其值得点名：设计中"自由作品集事实问题可以使用受约束的模型表达"（§5.8）需要新建通用知识 gateway + 受限表达 gateway + 校验回退链。**这不是收敛既有路径，而是在收敛的同时新增一条模型表达生产线**。设计 §16 把"后端架构"列为高影响是对的，但 §1 与 §3 的措辞把它淡化为"集中编排"，会让实施者低估工作量与失败面。

### 1.2 严重：`DeterministicConversationFallback` 与 PI 的预设职责重叠未澄清

设计 §3.4 说"外层不再维护作品集关键词、任务类型、检索和结果映射"，但 §5.3 列出的外层职责并未明确删除 `DeterministicConversationFallback.presetAnswer()`（`DeterministicConversationFallback.java:130-172`）。

如果保留这条 fallback，预设匹配就有 **PI 与外层 fallback 两条入口**——这正是设计想消除的双权威。如果删除，则设计 §14.1"明确删除"清单应列出它。当前文档既没有把它列入 §14.1，也没有在 §14.2 审计清单中点名。

### 1.3 `ConversationIntentRouter` 的命运不明

`ConversationIntentRouter.java:45-135` 同时承担两类职责：

- 全局 boundary（unsafe/time-sensitive），`routeBoundary()` 第 82-96 行
- 通用意图分类（模型驱动），`route()` 第 45-80 行

设计 §5.3 把"全局安全"和"时间边界"留在外层，把"通用问题 / 作品集"分类搬进 PI。但 `ConversationIntentRouter` 类本身是删除、保留 `routeBoundary` 还是整体保留，文档没说。结合 §14 删除清单完全没提这个类，实施时大概率会被保留然后变成第二个分类中心。

### 1.4 `PortfolioTaskResolver` 与 `PortfolioTask` 的删除未声明

设计 §5.6 规定了固定优先级，§7.4 列出新的内部组件 `PortfolioPresetResolver / PortfolioRuleResolver / PortfolioIntentClassifier`，但 `PortfolioTaskResolver.java` 这个类是否删除、`PortfolioTask.java` 是否降级为内部类型，§14 删除/审计清单都没列。这些是实施者最先会犹豫的点。

---

## 2. 遗漏影响

### 2.1 严重：C2b 固定只读工具与引用式多轮没有去处

`docs/00-文档状态索引.md:19` 把"C2b 固定只读工具与引用式多轮"列为已实施基线，由 `ContextEnvelopeValidator.java`、`ToolPlanBuilder.java`、`ToolPlanExecutor.java` 实现。这三个类**只被** `PortfolioAgentRuntime.java:60-62, 96-98` 使用。

v2 侧的 `ConversationToolService.java:60-139` 是**模型驱动**的工具调用——依赖 `modelPort.planTools()`，模型不可用时整个 enrich 跳过。它和 C2b 的"确定性固定工具"不是同一能力。

设计 §14.2 把 "ContextEnvelope 相关对象" 列入"审计后删除或迁入"，但**没有说明 C2b 是放弃还是迁移**。两种后果：

- **若放弃**：需要在 §14.1 明确删除、在 §16 影响评估加入"C2b 能力下线"、并在 `docs/00-文档状态索引.md:19` 与 `docs/08-当前实现状态.md` 同步删除该基线声明。当前文档完全未提。
- **若迁移**：需要在 §7.4 内部组件中列出对应的 deterministic tool planner，并在 §5 决策中说明它替代现有 `ConversationToolService` 还是并存。

### 2.2 权威文档影响清单不全

设计 §14.1 只提"README 和当前状态文档"。实际涉及 v1 问答的权威文档至少还有：

- `docs/04-项目代码约束.md:329`（**当前权威**约束文档，状态索引第 2 节把它排在第 2 位）
- `docs/08-当前实现状态.md:44`（按 AGENTS.md 必须在能力变化时同步）
- `docs/00-文档状态索引.md:15,19`（C2b / `/api/v2/answers` 描述都要改）

§14.1 应明确列出这几份；AGENTS.md 的"Documentation maintenance"条要求能力变化时同步 `docs/08` 与 `docs/09`，这是硬性工作流。

### 2.3 前端影响范围被低估

设计 §13 列出 3 个组件文件 + "相关类型"。实际共享枚举与标签的传播面更广：

- `frontend/src/features/agent/model/answerTypes.ts:1-3`（`BOUNDARY` / `DETERMINISTIC`/`MODEL`/`FALLBACK` 类型源头）
- `frontend/src/features/agent/model/answerLabels.ts:22,45,60-70`（产生 "ANSWERED · DETERMINISTIC" 尾注的 `answerTechTail`，设计 §13.4 要删的就是它）
- `frontend/src/features/agent/model/mapAnswerResponse.ts:44-46`
- `frontend/src/features/agent/model/evidenceDeskModel.ts` 与对应测试
- `frontend/src/features/agent/composables/useLocalSessions.test.ts:14-15`
- `frontend/src/shared/diagnostics/frontendDiagnosticTypes.ts:17`（`FrontendGenerationMode`，**这是遥测枚举**，不只展示）
- `frontend/src/pages/AgentPage.test.ts:32-33`、`features/audience/components/AudienceDialogue.test.ts:22-23`

把 `BOUNDARY` 移除、把 `generationMode` 删除/迁移，至少要改 6 处生产源码与多份测试，并影响前端诊断事件 schema。设计 §16"前端 = 中"在删除 `generationMode` 后偏低，建议改为"中高"。

---

## 3. 兼容风险

### 3.1 v1 删除本身是安全的，但能力降级矩阵不完整

代码层面确认无前端、脚本调用 `/api/v1/answers`（仅 `AnswerController.java` 自身、`AnswerControllerTest.java`、文档与历史 plan 残留），删除是可行的。

但 §5.1 给的回滚清单只有 3 条：

- 通用模型关闭 → 预设/规则/本地检索可执行
- PostgreSQL 失败 → 降级 bundle
- 检索最终失败 → 明确失败

少了两条现有路径：

- `PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED=false` 时的整体 v2 fail-closed（`README.md:117` 提到这是当前回滚策略，删除 v1 后这条开关的语义需要重新定义）
- 模型 classify 失败但 preset/rule 命中时的行为（设计 §8.3 提及但不在回滚清单里）

### 3.2 严重：FailoverPortfolioRetriever 的"可恢复失败"判定过宽

设计 §10.3 区分"PostgreSQL 可恢复失败"和"最终失败"。但 `FailoverPortfolioRetriever.java:24-38` 当前实现：

```java
try {
    return primaryRetriever.retrieve(request);
} catch (PortfolioRetrievalException exception) {
    PortfolioRetrievalResult fallbackResult = fallbackRetriever.retrieve(request);
    ...
}
```

问题：

1. **只捕获 `PortfolioRetrievalException`**。`NullPointerException`、`IllegalStateException` 等其他 `RuntimeException` 不会触发 failover，会直接冒泡。这意味着"可恢复失败"的实际边界由 adapter 自己声明，缺乏统一分类。
2. **fallback 失败时**异常原样抛出，没有标准 HTTP 错误的转换。设计 §10.3"PostgreSQL 与 bundle 均失败 → 类型化技术异常 → 标准 HTTP 错误"在 `ConversationAnswerController` 层目前没有任何 `@ExceptionHandler` 把 `PortfolioRetrievalException` 映射成类型化错误。设计需要新增 controller 异常映射或全局 `@RestControllerAdvice`，但 §14 没列。
3. **任何 `PortfolioRetrievalException` 都触发 failover**——包括程序 bug 导致的（如 SQL 写错）。这会把 bug Mask 成"bundle 正常"。需要至少区分连接失败/超时（可降级）与查询/SQL 错误（不可降级）。

### 3.3 v2 响应契约变更需要文档级 breaking-change 公告

`ConversationAnswerResponse.java:19-70` 当前暴露 `intent / answerScope / resolution / degraded / generationMode / answerSource / noticeCode`。设计 §12.2 删除 `BOUNDARY`、删除/迁移 `generationMode`，但：

- **`noticeCode` 是否保留**没说明。当前 `MODEL_UNAVAILABLE_FALLBACK`、`BUNDLE_RETRIEVAL_UNAVAILABLE`、`POSTGRES_RETRIEVAL_UNAVAILABLE` 这些是前端/脚本（如 `start-local.ps1:377-389` 的 `Get-DegradedCategory`）依赖的诊断信号。
- **`answerScope` 重命名映射不完整**：当前 `ConversationAnswerScope` 是 `CONVERSATION / GENERAL / PORTFOLIO / HYBRID`；§12.2 改成 `PORTFOLIO / GENERAL / MIXED / GLOBAL`。`MIXED` 顶替 `HYBRID` 还合理，但 `GLOBAL` 与 `CONVERSATION` 的关系、`GENERAL` 与 `GLOBAL` 的边界都没定义。今天 `GENERAL` 表示"通用知识问答"，`CONVERSATION` 表示"问候/边界"。前端 `answerLabels.ts` 是基于现有枚举写的，重命名需要逐处对齐。

---

## 4. 状态语义

### 4.1 `NEEDS_CLARIFICATION` / `NOT_SUPPORTED` 在 free-text 路径的归属不清

§5.4 定义了 4 个内部 disposition；§8.2 第 6 步说"不能形成合法作品集任务时，根据可澄清性返回 NEEDS_CLARIFICATION 或 NOT_PORTFOLIO"。

但**作品集证据不足**这种情况，free-text 路径在 §8.2 没有落到 `NOT_SUPPORTED` 的出口：

- 当前 `BundlePortfolioRetriever.retrieveSubject:123-125` 在 `verifiedClaims.isEmpty()` 时返回 `null`，`retrievePublishedSubjects:69-84` 最终给空列表。
- 当前 `PortfolioIntelligenceAnswerAssembler.materialBlocks:146-168` 把空证据降级为"当前公开内容中没有足够的已验证材料"文本块，**resolution 仍是 ANSWERED**——这就是设计 §2.5 批评的"空证据仍 ANSWERED"。

设计需要在 §8.2 明确：当 task 形成但 retrieval 返回空证据集时，free-text 走 `NOT_SUPPORTED` 还是 `NEEDS_CLARIFICATION`？目前两处文字（§8.2 第 6 步与 §10.2）覆盖不到这条路径。

### 4.2 BOUNDARY 删除后，"provider 不可用"对外语义空缺

当前 `DeterministicConversationFallback.answer:42-89` 在 provider 不可用时返回 BOUNDARY + 一段文案；v2 在 provider 不可用且无预设命中时，对外也是 BOUNDARY。

设计删除 `BOUNDARY` 后，§12.2 留下的 4 个 resolution（`ANSWERED / NEEDS_CLARIFICATION / NOT_SUPPORTED / REJECTED`）**没有专门的"能力暂不可用"语义**。可能的映射：

- `REJECTED`：当前语义是"请求涉及私密/越权"（`DeterministicConversationFallback.java:64-67, 96-104`），不适合"provider 暂时不可用"。
- `NOT_SUPPORTED`：语义是"问题明确但材料不支持"，也不适合"模型暂不可用"。

设计 §8.3 末尾说"外层通用模型也不可用：由全局能力边界响应处理"，但**没有给出对应的对外 resolution**。这是一个真实的契约缺口。

### 4.3 `internalDiagnostics` 嵌入返回类型是泄漏抽象

§7.3 把 `internalDiagnostics` 放在 `PortfolioDecision` 上同时说"不直接序列化"。但：

- 序列化边界由 mapper 控制，未来新增 mapper 时容易误暴露。
- 现有更干净的模式是 `ConversationalAgentRuntime.publishPortfolioIntelligence:524-553`：在 PI 内部直接通过 `DiagnosticEventPublisher` 发布事件，返回值不带诊断字段。建议沿用这个模式，PI 内部注入 publisher，`PortfolioDecision` 不再携带诊断。

---

## 5. 检索策略

### 5.1 严重：Postgres 的 exact 路径同样绕过相关性，设计只点了 Bundle

设计 §2.4 把问题限定在"Bundle 检索"。但 `JdbcPostgresKnowledgeQuery.java:59-60, 77-96` 的 `retrieveExact()` 也走 `selectionQuery.findByIds()` + `passageQuery.findPassages()`，**没有调用 `candidateRetriever.retrieve()`**（即没有 hybrid 相关性排序）。

也就是说：**只要走 subjectScope 路径，无论用哪个 adapter，都会绕过 query relevance**。设计的不变量 §17.3"精确主体不会无差别返回全部主体材料"隐含覆盖了双 adapter，但 §2.4 的"问题证据"和 §5.7 的"设计原则"应明确点名两个 adapter，避免实施者只改 Bundle。

### 5.2 主体作为"路由证明"的数据流未被点名

触发 exact lookup 的链路是：

```text
前端 hard-coded projectSlug (AgentWorkspace.vue:454,459)
  → v2 request.context.projectSlug
  → ConversationIntentRouter.routeHint:164-170 (或 usesPortfolioIntelligence:347-355)
  → 硬路由判定 true
  → ConversationalAgentRuntime.withSubjectConstraint:492-522 把 subjectStableId 注入 PortfolioTask
  → DefaultPortfolioIntelligence.retrieve:117-124 选择 subjectScope()
  → PortfolioRetrievalRequest.isExactPortfolioLookup() = true
  → adapter exact 路径
```

设计 §5.5"主体上下文不构成路由证明"原则正确，但**没有指出 `withSubjectConstraint` 这个具体方法以及它和 `usesPortfolioIntelligence` 的合谋**。如果实施者只改 adapter 而不拆掉 `withSubjectConstraint` 的注入，问题不会消失。建议 §5.5 或 §10 明确："取消 `ConversationalAgentRuntime.withSubjectConstraint` 把主体塞进 `PortfolioTask.subjectId` 的行为；主体只通过 `subjectScope` 检索参数表达，不参与 task 类型选择。"

### 5.3 检索策略类型不全

§5.7 列了 5 类策略，缺：

- **澄清消解**：候选主体不止一个时的二次定位（当前 `ConversationIntentRouter.clarificationRoute:244-253` 直接放弃）。
- **通用知识**：§11 引入的 `GeneralKnowledgeGateway` 是否走检索请求？如果走，缺 `GENERAL_KNOWLEDGE` 策略；如果不走，需要说明它走什么通道。
- **混合问题**：混合问题通常需要 portfolio retrieval + general knowledge 并行，§10.1 的"统一检索请求"是否支持多 strategy 复合？

---

## 6. 混合问题

### 6.1 `GeneralKnowledgeGateway` 的依赖注入与可用性传递未定义

§7.2 `PortfolioTurn` 输入字段没有 `providerAvailable` 或 `generalKnowledgeAllowed`。但 §11.3 第 4 条要求"通用模型不可用但作品集部分仍构成完整回答：允许降级为 PORTFOLIO + ANSWERED + degraded"——PI 如何知道通用模型是否可用？

现有判定在 `ConversationalAgentRuntime`：`providerAccess.isAllowed()`（第 200, 232 行）。如果把混合编排搬进 PI，要么：

- 把 `ConversationProviderAccess` 注入 PI（形成对外层状态的依赖）
- 把 provider 可用性作为 `PortfolioTurn` 字段（请求级，但每次请求都要重新计算）

文档需要选一个，并解释与现有 `ConversationProviderAccess` 的关系。

### 6.2 "核心含义改变"判定不可机械执行

§11.3 第 5 条"如果缺失通用部分会改变问题核心含义：返回通用能力暂不可用，不输出半个比较答案"。§17.5.4 不变量沿用同一句话。但"核心含义"是主观判断：

- 由谁判定？规则、模型还是 whitelisted 任务类型？
- 对于 `COMPARISON` 任务，"两边都无证据时返回 NOT_SUPPORTED"是可机械判定的；但"作品集 + 通用比较，通用缺失"是否核心，需要更明确的规则。

建议改为可判定的客观规则，例如：

- COMPARISON：任一侧无证据 → `NOT_SUPPORTED`
- RECOMMENDATION_SUPPORT：通用理由缺失 → 允许 PORTFOLIO + degraded（推荐主体不依赖通用）
- 其他任务：默认 → 返回能力不可用

### 6.3 混合编排的实施规模被低估

§18 第 7 步"支持混合问题和 recommendation 路径"是一步。但实际上当前 `answerHybridWithPortfolioIntelligence`（`ConversationalAgentRuntime.java:396-467`）的 hybrid 路径牵涉 deterministic 基线 + 模型生成 + draft 校验 + 三重 fallback（PROVIDER_FAILURE / VALIDATION_EXCEPTION / VALIDATION_REJECTED）。把这套搬进 PI 同时维护 fallback 一致性，工作量与第 4-6 步等量。建议拆为两步。

---

## 7. 日志方案

### 7.1 仓库根目录识别规则不严谨

§15.2 "从 `user.dir` 向上查找仓库标记"——但没有定义"仓库标记"：

- `.git`？根 `pom.xml`？根 `package.json`？`.gitignore`？
- 多模块下 `backend/pom.xml` 与根 `pom.xml` 都存在时如何识别根？
- 在 IDE 把 working dir 设为 `backend/`、`mvn spring-boot:run` 把 user.dir 设为 `backend/`、`start-local.ps1` 设为根 三种情况下如何收敛到同一目录？

这些直接决定 §15.2"未知布局不得静默写入不确定目录"的触发频率。建议明确："以**包含 `.git` 目录**且**同时包含 `backend/pom.xml` 与 `frontend/package.json`** 的目录为仓库根"。

### 7.2 Logback 与 PowerShell 的 stdout 关系没说清

§15.3 "PowerShell 不再把后端 stdout/stderr 重复写入相同文件"。但当前 `start-local.ps1:552-557` 把 backend stdout/stderr 通过 `Start-OwnedProcess` 路由到 `LocalLogRouter`，后者写入 `backend-info.log` / `backend-error.log`（`LocalLogRouter.psm1:180-188`）。

如果 Logback 接管这两个文件：

- PowerShell 还需要 backend stdout 用于 `launcher.log` 显示启动关键事件吗？（当前 `LocalLogRouter.psm1:128-138` 把 backend 行分类到 BACKEND 域）
- 如果 PowerShell 完全不读 backend stdout，`launcher.log` 是否失去后端实时输出？
- 如果 PowerShell 仍读但不写 `backend-info.log`，那读出来的内容去哪？

设计需要明确："PowerShell 可读 backend stdout 但只用于控制台转发和 launcher 关键事件，不再写 backend-info/backend-error"，或"PowerShell 完全不读 backend stdout"。

### 7.3 Logback 初始化失败的 fail-closed 机制不具体

§15.2 "未知布局不得静默写入不确定目录；应给出安全、可操作的诊断，要求显式配置日志目录"。但 Logback 初始化发生在 Spring ApplicationContext 之前：

- 类型化异常如何抛出到启动脚本？（System.exit 配合 stderr？还是 Spring Boot `LoggingSystem` 自定义？）
- 启动失败时 `start-local.ps1` 当前期望从 stdout 读取 readiness（`Wait-ForHttp`），如果 Logback 失败导致进程退出，当前 `Stop-WithCode 'LOCAL_CHILD_EXITED_BEFORE_READY'` 只能给出通用码，无法传达"日志布局未识别"。
- IntelliJ 直接运行时如何向开发者展示这个失败？

建议明确："布局未识别时 Logback 退化为 console-only，**不阻塞启动**；并在 console 第一行输出 `LOG_LAYOUT_UNRESOLVED reason=...` 类型化诊断"。

---

## 8. 验收不变量

### 8.1 缺 C2b 工具能力的不变量

无论 C2b 是删除还是迁移，§17 都需要一个不变量：删除则断言"仓库不再存在 ToolPlan/ContextEnvelope 类型"，迁移则断言"v2 在该路径仍能调用固定只读工具"。当前文档完全没提。

### 8.2 缺性能预算不变量

§5.3 给 PI "一次优先解释权"，意味着所有问题（包括"什么是 Java"这种纯通用问题）都先走 PI 的 preset/rule/classifier 链。建议加入：

- 通用问题的 PI 快速路径预算（如 `NOT_PORTFOLIO` 在 50ms 内返回）
- 模型分类不在 preset/rule 命中时被调用（已有 §17.1.3 覆盖，但应同时断言**分类端口未被调用**，而不只是结果不变）

### 8.3 §17.5.4 主观不可机械测试

如 §6.2 所述，"缺失一半内容会改变核心语义"必须转为客观规则才能写测试。

### 8.4 缺幂等性与 presetId 的交互不变量

`ProductionConversationService.java:47` 用 `requestToken` 做幂等。设计 §5.2 新增 `questionPresetId` 后，以下场景需要不变量：

- 同一 `requestToken`、不同 `questionPresetId`：返回缓存还是拒绝？（建议：拒绝，因为语义不同）
- 同一 `requestToken`、`questionPresetId` 相同但 `question` 不同：返回缓存还是领域错误？（设计 §5.2 "ID 与文本冲突"已覆盖，但与幂等的交互需明确）

### 8.5 缺"作品集硬路由"废弃的不变量

当前 `usesPortfolioIntelligence()`（`ConversationalAgentRuntime.java:341-350`）由 subject/recommendation/deterministic rule 决定硬路由。设计废弃后，应有不变量："前端在 Project 页面发通用问题，应答的 `answerScope=GENERAL/GLOBAL` 而非 `PORTFOLIO`"——这条 §17.2.1 已隐含，但建议显式断言 `intentSource != RULE` 当问题与项目无关。

---

## 9. 其他建议

| 编号 | 问题 | 文件/位置 |
|---|---|---|
| 9.1 | §18 第 8 步"切换 v2 为唯一新内核入口"实际是对 `ConversationalAgentRuntime` 的大规模重构，建议拆为 2-3 步：先搬 preset/rule，再搬 subject，再搬 hybrid | `ConversationalAgentRuntime.java:139-467` |
| 9.2 | §14 删除/审计清单建议明确列出：`ConversationIntentRouter`、`PortfolioTaskResolver`、`DeterministicConversationFallback`、`ConversationSubjectGuard`、`PortfolioTask` 各自的处置 | §14 |
| 9.3 | §12.2 需补充 `noticeCode` 是否保留及其允许值集合 | §12.2 |
| 9.4 | §12.2 需补充 answerScope `MIXED/GLOBAL` 与现有 `HYBRID/CONVERSATION/GENERAL` 的精确映射 | §12.2、§13.4 |
| 9.5 | `PortfolioIntelligenceAnswerAssembler.grounding:81-127` 当前硬编码 `AnswerClaimCategory.IMPLEMENTATION` 等字段，混合编排搬进 PI 后需要修复这个数据保真问题 | `PortfolioIntelligenceAnswerAssembler.java:91-99` |
| 9.6 | `AnswerSource.PRESET` 与 `AnswerSource.RETRIEVAL` 当前在 v2 路径不一致（`DeterministicConversationFallback.java:238` vs `PortfolioIntelligenceAnswerAssembler.java:73`），设计 §11.1 三个维度拆解正确，但需明确废除现有 `AnswerSource` 枚举 | `AnswerSource.java`、`ConversationAnswerResult.java:19` |

---

## 10. 总评

**值得肯定**：设计对现有不一致的诊断（§2）经代码复核基本属实；优先级固定（§5.6）、主体仅限定范围（§5.7）、三维度拆解（§11.1）、删除 v1（§5.1）这些原则方向正确；§17 大部分不变量可机械执行。

**必须关闭的缺口（建议评审不通过直到修复）**：

1. **§2.1 C2b 能力去向**（兼容性，必须明示删除或迁移）
2. **§4.2 删除 BOUNDARY 后"provider 暂不可用"的对外 resolution**（契约完整性）
3. **§5.1 Postgres exact 路径同步修复**（检索正确性）
4. **§1.1 把"自由文本受约束模型表达"明示为新增能力而非收敛**（实施规模诚实性）
5. **§3.2 FailoverPortfolioRetriever 的"可恢复失败"分类与 controller 异常映射**（运行时正确性）

**建议改进（可在实施计划中处理）**：

- §14 删除/审计清单逐一列出关键类的处置（`ConversationIntentRouter`、`PortfolioTaskResolver`、`DeterministicConversationFallback` 等）
- §13 前端影响清单扩到 `answerLabels.ts` / `mapAnswerResponse.ts` / `frontendDiagnosticTypes.ts` / 各测试文件
- §15 仓库根识别规则、PowerShell stdout 关系、Logback fail-closed 机制具体化
- §17 增加幂等交互、性能预算、C2b、answerScope 映射等不变量

整体而言，**问题证据扎实、设计方向正确、但实施契约与影响评估存在多处必须关闭的缺口**。建议在修复 §10 列出的 5 个必须关闭项后再进入实施计划编写。
