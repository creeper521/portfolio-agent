# Agent 作品集智能检索与确定性推荐设计

日期：2026-07-31  
状态：已批准，等待实施计划

## 1. 背景

项目当前存在两条相互割裂的能力：

1. Agent 普通作品集问答通过本地运行时语料、关键词检索、向量检索和 RRF 完成。
2. Selection 通过独立的 `/api/portfolio-selections` 接口调用 PostgreSQL/pgvector 混合召回与确定性选择算法。

这使 Selection 容易被理解为独立产品或独立界面，也使 PostgreSQL/pgvector 没有真正成为 Agent 作品集能力的统一数据基础。

目标形态是：用户只与 Agent 对话；Agent 识别用户意图；代码内部的确定性硬路由引擎决定事实检索、比较、推荐或推荐调整的执行路径。Selection 不再是产品概念，而是推荐路径内部的一组算法。

## 2. 目标

- 所有作品集问答和推荐都通过 `/api/v2/answers` 完成。
- 建立统一的 `PortfolioIntelligence` 深模块，隐藏意图校验、硬路由、检索、证据门禁、推荐和降级复杂度。
- PostgreSQL/pgvector 成为主检索 Adapter，现有 Bundle/内存能力成为降级 Adapter。
- 确定性规则优先识别意图；规则不足时，模型只做受约束分类补充。
- 推荐结果由确定性算法产生，模型只能解释结果，不能更改最终作品集合。
- 删除面向产品公开的 Selection Controller、HTTP DTO 和独立接口。
- 保留现有候选召回、Top-K、穷举策略及 Benchmark，并将其内化为推荐实现。
- 支持基于最近一次有效推荐进行自然语言调整。

## 3. 非目标

- 不建设 Selection 页面或第二套用户流程。
- 不允许模型生成或选择 SQL、检索器、路由策略及选择算法。
- 不允许模型绕过 Evidence、公开 Release、隐私或数量约束。
- 不在本次工作中重新设计通用知识、时效性和安全意图的全部处理逻辑。
- 不把 PostgreSQL、pgvector 或内部策略名暴露给最终用户。
- 不为内部 Selection 保留兼容性的公开 HTTP 接口。

## 4. 核心架构

### 4.1 外部入口

系统只保留 Agent 回答入口：

```text
POST /api/v2/answers
```

推荐是回答的一种结构化增强，不是独立资源或独立产品入口。

### 4.2 深模块

Agent 运行时只依赖一个接口：

```java
PortfolioIntelligenceResult resolve(PortfolioTask task)
```

`PortfolioIntelligence` 是一个深模块：调用方只需要提交经过会话解析的任务，模块内部负责校验、路由、检索、推荐、证据组装、降级和诊断。调用方不得直接选择内部 Adapter 或策略。

建议的领域输入：

```text
PortfolioTask
  turnId
  conversationId
  query
  intentCandidate
  extractedConditions
  previousRecommendationContext?
```

建议的领域输出：

```text
PortfolioIntelligenceResult
  resolvedIntent
  resolution
  evidence
  answerMaterial
  portfolioRecommendation?
  clarification?
  degraded
  noticeCode?
  diagnostics
```

`diagnostics` 仅供内部日志和测试使用，不直接序列化给客户端。

### 4.3 内部模块

- `IntentResolver`：执行确定性规则和受约束模型分类。
- `TaskValidator`：校验意图、条件、上下文、公开状态和权限。
- `HardRouter`：根据已验证任务确定唯一执行模式。
- `PortfolioRetriever` seam：统一事实、比较和推荐的召回接口。
- `RecommendationPolicy`：封装现有候选选择、Top-K、穷举及硬约束。
- `EvidenceAssembler`：组装可引用证据并执行公开 Release、Evidence 和隐私门禁。
- `RecommendationContextStore`：保存最近一次有效推荐的条件、结果和批次标识。

`PortfolioRetriever` seam 有两个真实 Adapter：

1. `PostgresPgvectorPortfolioRetriever`：主 Adapter，执行 PostgreSQL 全文与 pgvector 混合检索。
2. `BundlePortfolioRetriever`：降级 Adapter，复用现有 Bundle/内存关键词、向量和 RRF 能力。

普通问答与推荐必须共享这个 seam，不能继续各自维护一套作品集召回入口。

## 5. 意图识别和硬路由

### 5.1 作品集任务模式

硬路由引擎内部使用以下枚举：

```text
FACT_LOOKUP
COMPARISON
RECOMMENDATION
REFINE_RECOMMENDATION
CLARIFICATION_REQUIRED
```

现有通用会话、安全、时效性等意图仍可保留在外层会话意图中；一旦任务被识别为作品集领域请求，就转换为上述 `PortfolioTask` 模式。

### 5.2 识别顺序

1. 确定性规则先判断明确措辞和会话状态。
2. 规则产生唯一高置信结果时直接使用，不调用模型分类。
3. 规则无法唯一判断时，调用受约束的分类 Adapter。
4. 模型只能返回规定枚举、结构化条件和 `0..1` 置信度。
5. 默认置信度阈值为 `0.80`；低于阈值、返回非法枚举或条件相互矛盾时进入 `CLARIFICATION_REQUIRED`。
6. 分类失败、超时或不可用时回到规则结果；规则仍不明确则追问。

置信度阈值可以配置，但任何配置都不得允许模型绕过任务校验和硬路由。

### 5.3 职责限制

Agent 或分类模型可以：

- 判断用户希望查询、比较、推荐还是调整推荐。
- 从自然语言提取职业方向、受众角色、能力偏好、目标和期望数量。
- 生成面向用户的解释与追问。

Agent 或分类模型不可以：

- 指定 SQL、表、索引或检索 Adapter。
- 指定 Top-K、穷举或其他选择策略。
- 直接提供最终入选作品 ID。
- 恢复已被公开状态、Evidence、隐私或硬约束排除的候选。
- 在解释阶段增删或替换确定性结果中的作品。

## 6. 推荐条件和上下文

推荐条件沿用现有领域含义：

```text
careerTrack
audienceRole
capabilityCodes
goal
requestedSize
```

规则如下：

- `requestedSize` 默认是 3，有效范围是 2 到 5。
- `audienceRole` 是当前推荐算法的关键条件；缺失且无法从对话可靠推断时必须追问。
- 其他条件可作为硬约束或软偏好进入领域任务，由 `TaskValidator` 和 `RecommendationPolicy` 明确处理，不能由提示词临时解释。
- 追问只询问当前缺失的一个最关键条件，已经获得的条件继续保留。

每次成功推荐后保存：

```text
RecommendationContext
  conversationId
  recommendationBatchId
  normalizedConditions
  selectedPortfolioIds
  createdAt
  contentVersion
```

“第二个不错，换掉第一个”“再偏后端一点”等后续输入默认继承最近一次有效上下文，进入 `REFINE_RECOMMENDATION`。

以下情况不直接继承：

- 没有该会话的成功推荐记录。
- 用户指代无法映射到现有结果。
- 内容版本变化使原候选不可继续使用。
- 新旧条件冲突且无法按确定性规则消解。

此时进入 `CLARIFICATION_REQUIRED`，不静默覆盖旧条件。

## 7. 数据流

```text
用户自然语言
  -> ConversationalAgentRuntime
  -> 规则优先的意图解析与条件提取
  -> PortfolioTask
  -> PortfolioIntelligence.resolve
  -> TaskValidator
  -> HardRouter
       -> FACT_LOOKUP
       -> COMPARISON
       -> RECOMMENDATION
       -> REFINE_RECOMMENDATION
  -> PortfolioRetriever
       -> PostgreSQL/pgvector 主 Adapter
       -> Bundle/内存降级 Adapter
  -> EvidenceAssembler / RecommendationPolicy
  -> PortfolioIntelligenceResult
  -> Agent 组织解释
  -> /api/v2/answers
```

外部模型 Provider 被关闭时，规则明确的作品集任务仍必须能够执行。解释模型不可用时使用确定性模板渲染领域结果。

## 8. 回答契约

现有 `/api/v2/answers` 响应继续保留：

- `blocks`
- `suggestedQuestions`
- `progress`
- `degraded`
- `generationMode`
- `answerSource`
- `noticeCode`

增加可选的顶层字段：

```json
{
  "portfolioRecommendation": {
    "recommendationBatchId": "rec-...",
    "items": [
      {
        "portfolioId": "portfolio-...",
        "title": "项目名称",
        "matchReasons": ["匹配后端能力要求"],
        "evidenceIds": ["evidence-..."]
      }
    ],
    "satisfiedConstraints": ["audienceRole", "requestedSize"],
    "unsatisfiedConstraints": []
  }
}
```

契约规则：

- 非推荐回答省略该字段，不序列化为 `null`。
- 推荐和推荐调整回答必须返回该字段，即使 `items` 为空。
- `items` 的作品集合与顺序来自确定性引擎，语言模型不能修改。
- `blocks` 负责自然语言解释，`portfolioRecommendation` 负责稳定、可渲染、可继续调整的领域结果。
- `ConversationAnswerResult.withGuidance()` 等重建结果的方法必须完整保留该字段。
- 公共字段名使用 `portfolioRecommendation`，不得使用 `selection`。

省略该字段可以保持向后兼容，并避免无意义的 `null`。

## 9. 删除与迁移

删除面向产品公开的：

- `PortfolioSelectionController`
- `/api/portfolio-selections`
- 只服务于该接口的请求、响应 DTO 和 Mapper
- 对应 Controller 与公开映射测试
- 任何暗示独立 Selection 页面的交接文档

保留并迁移到 `PortfolioIntelligence` 实现内部的：

- 候选召回逻辑
- `TopKSelectionStrategy`
- `ExhaustiveSelectionStrategy`
- 选择领域约束
- Benchmark fixture、evaluator 和 CLI

Benchmark 可以继续作为开发和回归工具存在，但不是线上产品接口。

不保留旧 Selection HTTP 接口的弃用期。前提是代码库内调用点和文档全部迁移，并通过端到端测试证明 Agent 已覆盖原能力。

## 10. 错误与降级

| 场景 | 确定性行为 | 用户侧表现 |
|---|---|---|
| 意图不明确 | `CLARIFICATION_REQUIRED` | 追问一个关键问题 |
| 推荐关键条件不足 | 保留已有条件，不执行推荐 | 只询问缺失条件 |
| PostgreSQL/pgvector 不可用 | 切换 Bundle Adapter | `degraded=true`，给出简洁提示 |
| 降级数据不足 | 停止生成领域事实 | 明确说明数据不足 |
| 无作品满足硬约束 | 返回空推荐及未满足约束 | 建议放宽一个条件 |
| Evidence、公开状态或隐私校验失败 | 排除候选 | 不展示被排除作品 |
| 推荐调整条件冲突 | 保留旧上下文 | 请求用户确认冲突项 |
| 模型分类失败或非法 | 回到规则；仍不明确则追问 | 不暴露内部异常 |
| 模型解释失败 | 使用确定性模板 | 保留结构化结果 |

响应不得包含 SQL、内部策略名、异常堆栈或敏感诊断。完整原因进入结构化日志。

## 11. 可观测性

每次 `PortfolioIntelligence.resolve` 至少记录：

- `turnId`、`conversationId`
- 规则命中的意图及是否调用模型补充
- 模型分类结果、置信度和是否被拒绝
- 最终硬路由模式
- 使用的检索 Adapter 和是否降级
- 候选数量、门禁排除数量、最终数量
- `recommendationBatchId`
- 内容版本和耗时
- 对外 `noticeCode`

日志不得记录原始敏感内容、数据库凭据或未公开作品正文。

## 12. 测试策略

### 12.1 意图识别契约测试

- 明确规则命中时不调用模型。
- 模糊输入才调用受约束分类 Adapter。
- 非法枚举、低于 `0.80`、超时和异常均按设计处理。
- 模型返回作品 ID、SQL 或策略名时被拒绝。

### 12.2 硬路由测试

- 同一规范化 `PortfolioTask` 稳定进入同一模式。
- 调整任务在有效上下文中继承条件和结果。
- 无效或冲突上下文进入追问。
- 模型无法绕过门禁或指定内部策略。

### 12.3 检索 Adapter 契约测试

- PostgreSQL/pgvector 与 Bundle Adapter 返回相同领域形状。
- 两个 Adapter 都遵守公开 Release、Evidence 和内容版本约束。
- 主 Adapter 不可用时自动降级。
- 降级结果不足时不生成无证据事实。

### 12.4 推荐回归测试

- 保留 Top-K、穷举和现有 Benchmark。
- 覆盖数量范围、硬约束、空结果和候选不足。
- 覆盖推荐调整、替换指定位置和新增偏好。
- 相同数据、内容版本和规范化条件产生相同作品集合与顺序。

### 12.5 Agent 端到端测试

- 普通作品问答经统一检索 seam 返回证据化回答。
- 比较请求返回对比内容但不返回 `portfolioRecommendation`。
- 推荐请求返回自然语言解释和结构化推荐。
- “换掉第一个”等后续请求继承推荐上下文。
- 普通回答省略 `portfolioRecommendation`。
- `/api/portfolio-selections` 不再存在。
- PostgreSQL 不可用时正确降级。
- 外部模型关闭时，规则明确的任务仍能工作。
- 解释模型失败时仍返回确定性结构化结果。

## 13. 验收标准

实现完成必须同时满足：

1. 用户只能通过 Agent 完成作品集查询、比较、推荐和推荐调整。
2. 代码库不存在公开 Selection Controller、接口或独立页面入口。
3. 普通作品集 RAG 和推荐共享 `PortfolioRetriever` seam。
4. PostgreSQL/pgvector 是健康状态下的主 Adapter。
5. 模型不能选择技术路径或改变最终推荐集合。
6. 同一数据版本和规范化条件产生稳定结果。
7. 数据库或模型故障有可测试的确定性降级路径。
8. 推荐响应使用可选 `portfolioRecommendation`，非推荐响应省略该字段。
9. 原选择算法和 Benchmark 的有效能力得到保留。
10. 全部契约、回归和端到端测试通过。

## 14. 实施顺序约束

实施计划应按以下依赖顺序展开：

1. 建立领域任务、结果和统一检索 seam。
2. 用契约测试固定 PostgreSQL 与 Bundle Adapter 行为。
3. 引入意图解析、任务校验和硬路由。
4. 将现有 Selection 算法迁入推荐实现。
5. 接入 Agent 运行时和统一回答契约。
6. 完成推荐上下文与调整路径。
7. 删除旧公开 Selection 接口和专用 DTO。
8. 更新文档、Benchmark 和端到端测试。

每一步都应保持可验证，避免先删除旧路径再补齐 Agent 能力。
