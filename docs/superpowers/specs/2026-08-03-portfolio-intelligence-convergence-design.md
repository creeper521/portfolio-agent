# PortfolioIntelligence 单一内核收敛设计

日期：2026-08-03

状态：待外部 AI 评审，尚未进入实施

关联设计：`2026-07-31-portfolio-intelligence-hard-routing-design.md`

## 1. 执行摘要

当前系统虽然已经引入 `PortfolioIntelligence`，但作品集预设匹配、确定性规则、模型分类、主体判断、检索入口和最终结果语义仍分散在多条运行时路径中。相同问题可能因为 API 入口、页面上下文、模型开关或执行顺序不同而得到不同的 `ANSWERED`、`BOUNDARY`、`PRESET`、`RETRIEVAL` 或 `DETERMINISTIC` 组合。

本设计采用“单一内核”方案：

- 删除 `POST /api/v1/answers` 及其独立问答内核，不保留兼容适配器。
- 把作品集预设、确定性规则、模型分类、领域上下文、检索规划、证据策略、推荐、混合回答和结果语义全部收进 `PortfolioIntelligence`。
- 外层仅保留全局请求保护、安全、时间、明确问候和完全通用对话路由。
- 除明确全局问题外，`PortfolioIntelligence` 对输入拥有一次优先解释权；只有返回 `NOT_PORTFOLIO` 时才转交通用对话。
- 给 v2 增加可选 `questionPresetId`，正式预设按稳定 ID 优先解析。
- 修正精确主体检索绕过问题相关性的行为。
- 删除模糊的 `BOUNDARY` 和 `generationMode` 对外语义。
- 统一本地文件日志：IntelliJ、Maven 和官方启动脚本使用 local profile 时都创建仓库根目录 `logs/`。

这是一项后端高影响、API 高影响、前端中等影响、数据结构低影响的架构收敛。目标不是只修复某一个 SQL 预设，而是消除产生不一致结果的结构性原因。

## 2. 背景与问题证据

### 2.1 当前路由权力分散

当前作品集问答相关决策分布在：

- `ConversationalAgentRuntime`
- `ConversationIntentRouter`
- `PortfolioTaskResolver`
- `DeterministicConversationFallback`
- `QuestionResolver`
- `PortfolioAgentRuntime`
- `PortfolioIntelligenceAnswerAssembler`

这些组件分别掌握部分预设、关键词、模型分类、主体提示、fallback 和状态映射逻辑，导致系统没有唯一作品集语义权威。

### 2.2 v2 无法稳定消费正式预设身份

公开 bundle 中已经存在带有稳定 ID、canonical question、alias、topic、claim category 和 `deterministicEntry` 的 QuestionPreset。

但是：

- v1 请求原生支持 `questionPresetId`。
- v2 `ConversationAnswerRequest` 当前没有该字段。
- 前端请求类型虽然出现过 `questionPresetId`，实际 JSON 没有序列化它。
- v2 常见 Project/Case 上下文会在 fallback 前触发作品集硬路由，使后置预设匹配不可达。

因此正式预设目前更像数据描述，而不是 v2 的强执行契约。

### 2.3 规则与模型优先级不唯一

`PortfolioTaskResolver` 同时存在 `resolve()` 和 `route()` 两类入口。不同入口对确定性规则和模型分类的调用顺序不同，模型高置信结果在部分路径中可以先于或覆盖规则。

这意味着 provider 是否启用可能改变本应确定的作品集任务行为。

### 2.4 精确主体可能绕过相关性

Bundle 检索在精确 Project/Case 约束下可能返回该主体的全部已验证材料，而不是继续按问题、topic 和 claim category 排序。

“属于同一项目”不能证明“支持当前问题的结论”。主体只能限制候选范围，不能替代相关性和充分性判断。

### 2.5 当前结果字段混合了不同概念

当前状态组合存在以下歧义：

- `BOUNDARY` 同时表达需要澄清、证据不足或能力不支持。
- `DETERMINISTIC` 可能表示路由确定、未使用模型生成正文或结果可复现。
- `RETRIEVAL` 可能表示数据库、bundle、fallback bundle，甚至澄清结果。
- 空证据场景可能仍返回 `ANSWERED`，正文再说明没有足够信息。

这些字段不能准确解释系统为何产生当前结果。

### 2.6 本地文件日志依赖启动脚本

当前仓库根目录日志由 `scripts/start-local.ps1` 中的 PowerShell 日志路由器创建。直接通过 IntelliJ 或 Maven 启动 Spring Boot 不会创建 `logs/`。

此外，日志路由器初始化异常只输出通用降级码，缺少安全且可操作的失败类型。

## 3. 设计目标

1. `PortfolioIntelligence` 成为作品集语义的唯一权威。
2. 所有正式预设通过 v2 获得稳定、可复验的结果。
3. 模型开关、超时和冲突分类不改变确定性入口的业务语义。
4. 外层不再维护作品集关键词、任务类型、检索和结果映射。
5. 主体上下文只负责指代解析和检索范围，不单独强制作品集路由。
6. 证据不足、需要澄清、非作品集问题和技术失败具有不同语义。
7. 作品集检索失败时禁止通用模型补写项目事实。
8. 混合问题中的作品集事实仍受统一证据约束。
9. 删除不再使用的 v1 问答入口和旧运行时。
10. 所有受支持的 local profile 启动方式都创建可诊断的本地文件日志。

## 4. 非目标

- 不删除 `/api/v1/public-content`、Project、Case 或客户端诊断等非问答 v1 资源。
- 不在本次设计中重做数据库 schema。
- 不允许模型生成 SQL、选择检索适配器或决定最终证据 ID。
- 不把 PostgreSQL、pgvector、bundle 等内部适配器名称展示给普通用户。
- 不保存服务端会话状态；推荐和多轮上下文继续随请求传递并可复验。
- 不扩大公开数据范围，不降低 Claim/Evidence 和发布状态门禁。
- 不在本设计文档中拆解逐文件编码任务；实施计划在外部评审通过后另行编写。

## 5. 已批准的架构决策

### 5.1 删除问答 v1

本次改造一次完成，直接删除：

```text
POST /api/v1/answers
```

不保留长期兼容 Controller，也不保留 v1 到新内核的生产适配器。

删除 v1 后，回滚单位从“退回旧版本问答系统”改为“关闭或降级单项能力”：

- 通用模型关闭时，正式预设、规则和本地公开检索仍可执行。
- PostgreSQL 失败时降级到 bundle。
- 作品集检索最终失败时明确失败，不能转模型猜测。

### 5.2 v2 增加稳定预设身份

`ConversationAnswerRequest` 增加可选：

```json
{
  "questionPresetId": "question-sql-audit-async-and-recovery",
  "question": "SQL 审计工具如何管理异步查询任务，并在页面刷新后恢复任务状态？"
}
```

规则：

- 点击正式预设时，前端必须同时发送 ID 和显示文本。
- 自由输入只发送文本。
- ID 与文本冲突时不得静默选择，应返回领域输入错误并记录安全诊断。

### 5.3 作品集优先解释协议

外层只处理：

- 请求大小、格式、限流、并发、超时和幂等。
- 全局安全。
- 时间与实时性边界。
- 明确问候等无须作品集解释的全局问题。

除明确全局问题外，输入先进入：

```java
PortfolioDecision tryResolve(PortfolioTurn turn);
```

只有 `NOT_PORTFOLIO` 才转交通用对话。

### 5.4 结果语义

内部作品集 disposition：

```text
ANSWERED
NEEDS_CLARIFICATION
NOT_SUPPORTED
NOT_PORTFOLIO
```

定义：

- `ANSWERED`：公开证据充分，已形成受约束回答。
- `NEEDS_CLARIFICATION`：用户做一个明确选择或补充后可以继续。
- `NOT_SUPPORTED`：问题明确，但当前公开材料不支持该事实。
- `NOT_PORTFOLIO`：问题完全不需要作品集事实，转交通用对话。

技术故障不伪装成业务 disposition，使用类型化异常和标准 HTTP 错误。

### 5.5 主体上下文不构成路由证明

`projectSlug`、`caseSlug`、collection 和 recommendation context 的领域合法性全部由 `PortfolioIntelligence` 校验。

主体上下文只能：

- 解析“这个项目”“这个案例”等指代。
- 限制检索候选范围。
- 校验预设与推荐上下文的一致性。

合法页面主体不能单独把通用问题强制路由为作品集任务。

### 5.6 模型分类权限受限

优先级固定为：

```text
preset ID
-> canonical/alias
-> 高精度确定性规则
-> 受限模型分类
-> NOT_PORTFOLIO 或 NEEDS_CLARIFICATION
```

模型分类器只能返回封闭任务类型、主体引用、置信区间和 reason code。它不能：

- 覆盖预设或高精度规则。
- 选择数据库或检索 adapter。
- 选择证据 ID。
- 决定最终 disposition。
- 直接生成作品集答案正文。

### 5.7 检索范围与相关性正交

`projectSlug/caseSlug` 只定义搜索范围。检索仍必须应用：

- 原始与归一化查询。
- task mode。
- topic。
- claim category。
- 相关性排序。
- 公开状态和主体一致性。
- 证据充分性。

推荐的检索策略类型：

```text
SUBJECT_OVERVIEW
TARGETED_FACT
COMPARISON
RECOMMENDATION_SUPPORT
PRESET_CONTRACT
```

### 5.8 答案表达采用混合策略

- 正式 `deterministicEntry` 预设：确定性证据组装。
- 比较、推荐和调整推荐：确定性结构。
- 高风险精确事实：确定性证据组装。
- 自由作品集事实问题：可以使用受约束的模型表达。
- 模型表达失败或校验失败：回退到同一证据集的确定性组装。
- 表达方式不得改变 disposition、证据集合或推荐结果。

### 5.9 混合问题由 PortfolioIntelligence 负责

凡是需要作品集事实的问题，包括同时包含通用知识的混合问题，都由 `PortfolioIntelligence` 端到端负责。

混合回答必须区分：

- 作品集事实：必须来自已选公开 Claim/Evidence。
- 通用知识：由受限通用知识 gateway 提供，并明确其性质。
- 比较结论：由统一 composer 在两类材料边界内组装。

完全不需要作品集事实时才返回 `NOT_PORTFOLIO`。

### 5.10 local profile 统一文件日志

只要使用受支持的 local profile 启动方式，以下入口都必须创建仓库根目录 `logs/`：

- IntelliJ 直接运行。
- Maven 启动。
- `scripts/start-local.ps1`。

日志所有权调整为：

```text
Logback
  backend-info.log
  backend-error.log

PowerShell LocalLogRouter
  frontend-info.log
  frontend-error.log
  launcher.log
  archive / cleanup / snapshot
```

Logback 与 PowerShell 不得同时写同一个后端日志文件。

## 6. 目标运行时架构

```text
POST /api/v2/answers
  -> ProductionConversationService
       rate limit / concurrency / timeout / idempotency
  -> ConversationalAgentRuntime
       global safety / time / greeting
  -> PortfolioIntelligence.tryResolve
       PRESET / RULE / MODEL classification
       subject resolution and validation
       task validation
       retrieval planning
       retrieval and failover
       evidence policy
       deterministic or grounded composition
       result semantics
       safe diagnostics
  -> PortfolioDecision
       ANSWERED / NEEDS_CLARIFICATION / NOT_SUPPORTED
       or NOT_PORTFOLIO
  -> general conversation only after NOT_PORTFOLIO
```

`ConversationalAgentRuntime` 不得在 `PortfolioDecision` 返回后重新解释作品集 task、证据充分性或结果状态。

## 7. PortfolioIntelligence 深模块契约

### 7.1 外部接口

建议接口：

```java
public interface PortfolioIntelligence {
    PortfolioDecision tryResolve(PortfolioTurn turn);
}
```

接口使用 `tryResolve` 而不是 `resolve`，明确该模块可以正常返回 `NOT_PORTFOLIO`，将问题交还外层。

### 7.2 输入

概念模型：

```text
PortfolioTurn
  turnId
  question
  questionPresetId?
  messages
  subjectHint?
  recommendationContext?
  audienceRole
  source
```

模块自行依赖公开内容快照 gateway、检索 adapter、模型分类 gateway 和受限表达 gateway。调用方不能传入 adapter 名称或检索策略选择。

### 7.3 输出

概念模型：

```text
PortfolioDecision
  disposition
  answer?
  answerScope
  intentSource
  constructionMode
  evidenceState
  evidenceIds
  claimIds
  degraded
  recommendation?
  suggestions
  internalDiagnostics
```

`internalDiagnostics` 仅供日志和测试使用，不直接序列化。

### 7.4 内部组件

推荐内部边界：

```text
PortfolioPresetResolver
PortfolioRuleResolver
PortfolioIntentClassifier
PortfolioSubjectResolver
PortfolioTaskValidator
PortfolioRetrievalPlanner
PortfolioRetriever
PortfolioEvidencePolicy
PortfolioAnswerComposer
GeneralKnowledgeGateway
PortfolioDiagnosticsPublisher
```

这些组件是深模块的内部实现细节，不应被 `ConversationalAgentRuntime` 分别注入和编排。

## 8. 请求决策流程

### 8.1 预设

1. 校验 `questionPresetId` 格式。
2. 在同一不可变公开内容快照中查找预设。
3. 校验预设是否公开、是否允许当前主体。
4. 如果同时携带显示文本，校验其是否与 canonical/alias 一致。
5. 构造带有 topic、claim category 和证据最低要求的 task。
6. 模型分类不得参与或覆盖该 task。

### 8.2 自由文本

1. canonical/alias 归一化匹配。
2. 高精度规则匹配。
3. 利用唯一合法主体解析明确指代。
4. 仍存在歧义时调用受限模型分类。
5. 对模型分类结果执行领域校验。
6. 不能形成合法作品集任务时，根据可澄清性返回 `NEEDS_CLARIFICATION` 或 `NOT_PORTFOLIO`。

### 8.3 模型不可用

- 预设或规则已命中：正常执行作品集任务。
- 未命中确定性入口：返回 `NOT_PORTFOLIO` 交还外层。
- 外层通用模型也不可用：由全局能力边界响应处理。

分类器故障不能被表示为作品集证据不足。

## 9. 领域主体与上下文

建议内部模型：

```text
PortfolioSubjectContext
  project?
  case?
  collection?
  origin
```

`origin`：

```text
REQUEST_CONTEXT
PRESET_CONSTRAINT
QUESTION_TEXT
CONVERSATION_CONTEXT
```

规则：

- slug 格式非法：请求校验错误。
- 格式合法但不存在：领域输入错误。
- 未公开主体：按不存在处理，不泄露状态。
- Project/Case 冲突：领域输入错误。
- 预设与主体冲突：领域输入错误。
- recommendation context 指纹或内容版本非法：领域输入错误。
- 无明确主体但补充选择即可执行：`NEEDS_CLARIFICATION`。
- 通用问题携带合法页面主体：`NOT_PORTFOLIO`。

## 10. 检索与证据策略

### 10.1 统一检索请求

检索请求至少包含：

```text
normalizedQuery
taskMode
retrievalStrategy
subjectScope
topics
preferredClaimCategories
minimumEvidenceRequirement
resultLimit
```

### 10.2 正式预设契约

正式预设可以声明：

```json
{
  "topics": ["ASYNC_TASK", "STATE_RECOVERY"],
  "preferredClaimCategories": ["IMPLEMENTATION", "VERIFICATION"],
  "minimumEvidenceCount": 1
}
```

未满足最低公开证据要求时返回 `NOT_SUPPORTED`，不能使用同一项目中的无关材料填充。

### 10.3 失败与降级

```text
PostgreSQL 成功
  -> 使用 PostgreSQL 结果

PostgreSQL 可恢复失败
  -> bundle
  -> 成功则 ANSWERED + degraded=true

PostgreSQL 与 bundle 均失败
  -> 类型化技术异常
  -> 标准 HTTP 错误
```

adapter 切换只改变内部 evidence source 和 `degraded`，不能改变 task mode 或证据充分性标准。

## 11. 答案构造与混合回答

### 11.1 三个独立来源维度

内部保留：

```text
IntentSource
  PRESET_ID / PRESET_TEXT / RULE / MODEL

EvidenceSource
  POSTGRES / BUNDLE / FALLBACK_BUNDLE / NONE

ConstructionMode
  TEMPLATE / EVIDENCE_COMPOSITION / MODEL_GROUNDED / GENERAL_MODEL
```

它们不能再压缩成单一 `DETERMINISTIC`。

### 11.2 受约束模型表达

模型只接收已选择并脱敏的 Claim/Evidence 投影。输出必须经过：

- 封闭结构校验。
- claim/evidence 引用校验。
- 禁止新增事实校验。
- 主体边界校验。
- 公开状态校验。

校验失败时使用同一证据集进行确定性组装。

### 11.3 混合问题降级

- 两部分均成功：`MIXED + ANSWERED`。
- 作品集证据不足：`MIXED + NOT_SUPPORTED`。
- 比较对象不明确：`MIXED + NEEDS_CLARIFICATION`。
- 通用模型不可用但作品集部分仍构成完整回答：允许降级为 `PORTFOLIO + ANSWERED + degraded`。
- 如果缺失通用部分会改变问题核心含义：返回通用能力暂不可用，不输出半个比较答案。
- 作品集检索技术失败：技术异常，不允许通用模型接管作品集事实。

## 12. v2 对外契约

### 12.1 请求

新增可选 `questionPresetId`，其余多轮消息、主体上下文、audience、recommendation context、request token 和幂等语义保持。

### 12.2 响应

对外字段：

```text
resolution
  ANSWERED / NEEDS_CLARIFICATION / NOT_SUPPORTED / REJECTED

answerScope
  PORTFOLIO / GENERAL / MIXED / GLOBAL

constructionMode
  TEMPLATE / EVIDENCE_COMPOSITION / MODEL_GROUNDED / GENERAL_MODEL

intentSource
  PRESET / RULE / MODEL / GLOBAL

evidenceState
  VERIFIED / NOT_REQUIRED / INSUFFICIENT

degraded
```

规则：

- `NOT_PORTFOLIO` 不序列化，由外层继续通用对话。
- `FAILED` 不作为成功响应枚举，使用标准错误响应。
- 删除 `BOUNDARY`。
- 删除或迁移含义模糊的 `generationMode`。
- 不向普通用户暴露 PostgreSQL、bundle 或 fallback adapter 名称。

## 13. 前端影响

主要文件：

- `frontend/src/features/agent/api/answerApi.ts`
- `frontend/src/features/agent/components/AgentWorkspace.vue`
- `frontend/src/features/agent/components/ConversationThread.vue`
- 相关类型、单元测试、E2E fixture 和响应校验。

主要改动：

1. 点击正式预设时序列化 `questionPresetId`。
2. 自由输入不伪造预设 ID。
3. 更新响应枚举和非法响应校验。
4. 删除 `BOUNDARY · DETERMINISTIC` 展示。
5. 使用面向用户的业务标签：
   - 作品集资料 · 已验证证据 · 确定性组装
   - 作品集资料 · 已验证证据 · 基于证据表达
   - 作品集问题 · 需要补充信息
   - 作品集资料 · 当前公开证据不足
   - 通用对话 · 模型回答

## 14. 删除与迁移范围

### 14.1 明确删除

- `AnswerController`
- `/api/v1/answers`
- v1 问答请求、响应和 mapper
- `QuestionResolver`
- `PortfolioAgentRuntime` 的独立决策链
- v1 Controller、Resolver、Runtime 专属契约测试
- README 和当前状态文档中的 v1 问答说明
- “关闭 v2 回退 v1”的旧运行策略

### 14.2 引用审计后决定删除或迁入

- `AnswerContextFactory`
- `VerificationPolicy`
- `AnswerPlanBuilder`
- `DeterministicAnswerEngine`
- 旧 model coordinator
- 旧 retrieval、tool、privacy 和 execution snapshot 组件
- `ContextEnvelope` 相关对象

判定原则：

- 仅服务 v1 且新内核不需要：删除。
- 能表达仍然有效的隐私、证据、预算或校验不变量：迁入新内核或共享基础设施。
- 不允许为了让旧测试继续通过而保留重复业务权威。

## 15. 本地文件日志设计

### 15.1 目录

默认：

```text
<repository>/logs/
  current/
  archive/
  snapshots/
  staging/
```

仓库继续忽略 `/logs/`。

### 15.2 路径解析

优先级：

1. 显式安全的 `PORTFOLIO_LOG_DIRECTORY` 或等价启动参数。
2. 从 `user.dir` 向上查找仓库标记，解析仓库根目录。
3. 对受支持的 IntelliJ、Maven 和脚本启动布局保证得到同一根目录。

未知布局不得静默写入不确定目录；应给出安全、可操作的诊断，要求显式配置日志目录。

### 15.3 所有权与轮转

- Logback 独占后端活动日志。
- PowerShell 不再把后端 stdout/stderr 重复写入相同文件。
- PowerShell 继续独占前端和 launcher 日志。
- 归档器不得移动或压缩 Logback 正在写入的活动文件。
- 日志生产失败不得改变健康业务请求的响应；但启动输出必须给出安全失败类型。

### 15.4 PortfolioIntelligence 聚合诊断

正常请求输出一条聚合 INFO；可恢复降级输出 WARN；最终技术失败输出 ERROR；中间过程仅 DEBUG。

允许字段示例：

```text
event.name
turn.id
intent.source
preset.id / rule.id
task.mode
subject.type / safe subject id
retrieval.strategy
evidence.source
evidence.count
construction.mode
disposition
degraded
duration.bucket
failure.kind
```

禁止记录：

- 用户问题原文。
- messages。
- 回答正文。
- prompt。
- evidence 正文。
- 模型原始输出。
- 内部路径、凭据、header 或请求体。

## 16. 影响评估

| 范围 | 影响 | 说明 |
|---|---|---|
| 数据库 schema | 低 | 预计不改表，主要调整读取契约 |
| 公开 bundle | 中 | 预设检索契约和验证增强 |
| 后端架构 | 高 | 作品集决策权收进单一深模块 |
| HTTP API | 高 | 删除问答 v1，调整 v2 请求和响应 |
| 前端 | 中 | 预设 ID、响应类型、标签与测试 |
| 检索 | 中高 | 修正精确主体语义并统一 adapter 契约 |
| 模型能力 | 中 | 分类和表达权限收紧，增加校验与降级 |
| 日志系统 | 中高 | Logback/PowerShell 所有权重构 |
| 测试 | 高 | 删除旧路径测试，建立入口级不变量 |
| 生产兼容 | 低 | 当前尚未生产部署，现有前端使用 v2 |
| 回滚方式 | 中 | 从 v1 回滚改为能力级降级 |

## 17. 验收不变量

### 17.1 预设与路由

1. 所有 `deterministicEntry=true` 的公开预设都能通过 v2 回答。
2. preset ID、canonical question 和 alias 得到相同 task、证据和 disposition。
3. 模型开启、关闭、超时和冲突分类不影响确定性入口。
4. 非法或不公开预设 fail-closed。
5. 预设与页面主体冲突不能静默回答。

### 17.2 通用与主体边界

1. 项目页面中的通用问题可以返回 `NOT_PORTFOLIO` 并转交通用对话。
2. “这个项目”只在存在唯一合法主体时确定解析。
3. Project/Case 上下文不能单独触发作品集路由。
4. 未公开主体按不存在处理。

### 17.3 检索与证据

1. 精确主体不会无差别返回全部主体材料。
2. 定向问题必须应用 topic、claim category 和相关性。
3. 空证据不能返回 `ANSWERED`。
4. 数据库降级到 bundle 不改变业务 disposition。
5. 数据库和 bundle 都失败时不得调用通用模型补写项目事实。
6. 每个作品集事实块都能回溯到允许的 Claim/Evidence。

### 17.4 表达与推荐

1. 正式预设、比较和推荐不依赖模型表达。
2. 模型表达失败回退到同一证据集。
3. 回退不能改变证据、推荐候选或 disposition。
4. 推荐调整继续使用可验证、无服务端会话状态的 recommendation context。

### 17.5 混合问题

1. 混合问题中的作品集事实全部经过证据策略。
2. 通用知识不得被标记为作品集证据。
3. 通用模型不得改写作品集事实。
4. 缺失一半内容会改变核心语义时，不能输出误导性的半个比较答案。

### 17.6 API 与前端

1. `/api/v1/answers` 不再注册。
2. 仓库当前前端、脚本和测试不存在 v1 问答调用。
3. 点击正式预设实际发送 `questionPresetId`。
4. 前端准确区分 `NEEDS_CLARIFICATION` 和 `NOT_SUPPORTED`。
5. 前端不再展示 `BOUNDARY · DETERMINISTIC`。

### 17.7 日志

1. IntelliJ、Maven、`start-local.ps1` 使用 local profile 时都创建仓库根目录日志。
2. 后端日志不存在 Logback 与 PowerShell 双写。
3. 归档不处理活动日志文件。
4. 路由诊断可以回答命中 preset、rule、model 或 general 的原因。
5. 日志不包含问题、消息、答案、prompt、证据正文或凭据。
6. 现有隐私扫描和日志轮转测试通过。

## 18. 设计级实施顺序

本次最终以一次合并交付，但内部按以下顺序推进：

1. 建立新的 `PortfolioTurn`、`PortfolioDecision` 和结果枚举契约。
2. 先写入口级不变量测试。
3. 迁入预设、规则和主体解析。
4. 迁入受限模型分类与 task 校验。
5. 修正检索规划、adapter 一致性和证据策略。
6. 迁入确定性与模型受限答案构造。
7. 支持混合问题和 recommendation 路径。
8. 将 v2 切换为唯一新内核入口。
9. 更新 v2 请求、响应和前端。
10. 删除 v1 和旧运行时。
11. 重构 local 文件日志所有权。
12. 更新当前文档并完成全量验证。

“一次合并”不表示先删除旧代码再开发新路径。旧路径只作为迁移期间的行为参照，不作为最终生产兼容层保留。

## 19. 风险与缓解

### 19.1 通用问题误入作品集模块

风险：`tryResolve` 获得优先解释权后可能增加分类成本或误判。

缓解：先执行 ID、alias 和高精度规则；模型只处理歧义；`NOT_PORTFOLIO` 是正常快速返回；项目上下文不构成单独路由证明。

### 19.2 新内核过度膨胀

风险：把所有职责收进模块可能形成巨型类。

缓解：深模块只要求外部接口小，不要求内部只有一个类；使用内部 resolver、planner、policy、composer 和 gateway 分离变化原因。

### 19.3 v2 响应契约破坏

风险：前端 fixture、E2E 和旧构建物失效。

缓解：当前未生产；后端 DTO、前端类型、fixture 和 E2E 在同一合并内原子更新；发布前全仓扫描旧枚举。

### 19.4 删除 v1 后失去回滚入口

风险：v2 故障导致问答整体不可用。

缓解：新 v2 必须在模型关闭时保持正式预设、规则和本地检索；数据库具备 bundle 降级；回滚改为能力级，而不是保留双内核。

### 19.5 日志双写或归档竞争

风险：Logback 和 PowerShell 同时写入或移动活动文件。

缓解：定义单文件单生产者；归档器只处理已关闭分段；增加真实启动与轮转测试。

### 19.6 旧组件误删

风险：v1 旧运行时中存在仍然有效的隐私、预算或证据不变量。

缓解：删除前执行生产与测试调用图审计；按“迁移不变量，删除重复编排”处理，而不是按包名批量删除。

## 20. 被拒绝的替代方案

### 20.1 保留 v1 旧实现

拒绝原因：继续保留 `QuestionResolver` 和新内核两套作品集语义权威，无法根治同题不一致。

### 20.2 长期保留 v1 兼容适配器

拒绝原因：项目尚未生产且当前前端已使用 v2，长期维护有损结果映射收益低。本次选择直接删除。

### 20.3 外层先判断 Portfolio 或 General

拒绝原因：外层关键词或主体提示仍可能阻止内部预设和规则执行，路由权继续分散。

### 20.4 外层拼接混合回答

拒绝原因：外层会重新获得比较、引用和最终语义权，通用模型可能越过作品集证据边界。

### 20.5 所有答案都由模型表达

拒绝原因：正式预设、比较和推荐仍会漂移，模型关闭时业务结构变化过大。

### 20.6 所有答案都使用固定模板

拒绝原因：自由作品集问题表达僵化，模块退化为 FAQ；采用受约束模型表达与确定性回退更合适。

## 21. 外部 AI 评审重点

评审应重点检查以下问题：

1. `PortfolioIntelligence.tryResolve` 是否形成真正的深模块，还是只把现有编排机械搬家。
2. `NOT_PORTFOLIO` 优先解释协议是否会产生通用问题误判或不必要的模型调用。
3. 混合问题由 PortfolioIntelligence 端到端负责是否保持了清晰的通用知识边界。
4. `NEEDS_CLARIFICATION`、`NOT_SUPPORTED` 和技术失败的定义是否完整、互斥。
5. v2 新响应字段是否足以替代 `BOUNDARY` 和 `generationMode`，是否存在前端无法表达的状态组合。
6. 删除 v1 后，能力级降级是否足以承担原回滚职责。
7. 检索请求是否真正分离了 subject scope 与 query relevance。
8. 旧 v1 运行时中有哪些安全、隐私、预算和证据组件必须迁入而不能删除。
9. local profile 日志路径解析、文件所有权和轮转边界是否会产生平台相关问题。
10. 验收不变量是否覆盖正式预设、自由输入、混合问题、模型故障和双检索源故障。

## 22. 评审后续

外部评审通过或问题关闭后，再编写独立实施计划。实施计划应：

- 基于真实调用图列出删除、迁移和新增文件。
- 按测试驱动顺序拆分任务。
- 明确每个阶段的可运行验证命令。
- 保留用户现有未提交改动。
- 不在未获得明确授权时创建提交。

