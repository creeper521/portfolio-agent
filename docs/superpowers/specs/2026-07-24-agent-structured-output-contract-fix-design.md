# Agent 结构化输出契约修复设计

## 1. 背景与问题

对话式 Agent 已完成意图路由、公开知识检索、工具调用、回答生成、事实校验和动态追问的后端链路，但真实模型调用会在生成阶段进入确定性降级。

DeepSeek 与 GLM 的端点、密钥和模型名均已通过最小请求验证。两家模型在未获得明确输出字段约束时，分别返回了类似 `answer/sourceScope/recommendedQuestions` 和 `answer/sourceScope/followUpQuestions` 的合法 JSON。后端则严格反序列化为 `ConversationDraft`，要求 `title/resolution/blocks`，因此返回 `INVALID_RESPONSE` 并触发降级。

根因是固定 Prompt 只要求“输出合法 JSON”，却没有按当前 operation 注入对应的 JSON 输出契约。

## 2. 目标

- 为六类模型 operation 提供明确、稳定且可测试的 JSON 输出契约。
- 保持后端严格反序列化与失败关闭策略，不接受字段猜测或宽松映射。
- 使 DeepSeek V4 Flash 与 GLM 4.7 均能完成真实通用知识对话。
- 保持作品集事实、引用和工具边界不变。
- 保持前端 API 响应结构不变。

## 3. 非目标

- 不修改前端。
- 不修改作品集公开内容或私有知识发布流程。
- 不增加跨 Provider 自动重试。
- 不把供应商返回的任意字段兼容为内部 DTO。
- 不在仓库中保存真实模型密钥。

## 4. 方案

采用 operation 级 JSON 契约提示。

`ConversationalPromptFactory.systemPrompt(operation)` 在固定行为 Prompt 后追加当前任务及其输出契约。每份契约声明：

- 顶层 JSON 类型；
- 必填字段；
- 字段值类型；
- 允许的枚举值；
- 空值与空数组规则；
- 禁止额外字段；
- 一个最小合法示例。

各 operation 的返回结构如下：

### 4.1 intent

返回 `ConversationRoute` 对象：

- `intent`
- `answerScope`
- `confidence`
- `projectSlug`
- `caseSlug`
- `facet`
- `clarificationRequired`

### 4.2 tool_plan

返回 `ConversationToolPlan` 对象：

- `calls`

每个调用包含：

- `kind`
- `projectSlugs`
- `caseSlugs`
- `claimIds`
- `sectionType`

### 4.3 generation

返回 `ConversationDraft` 对象：

- `title`
- `resolution`
- `blocks`

每个回答块包含：

- `sourceScope`
- `content`
- `claimIds`
- `evidenceIds`

通用回答块不得包含 Claim 或 Evidence；作品集回答块只能引用注入的公开 ID。

### 4.4 review

返回 `GroundingReview` 对象：

- `unsupportedBlockIndexes`
- `reasonCodes`

### 4.5 suggestion

由于请求使用 `response_format=json_object`，顶层必须是对象。模型返回：

- `questions`

每个问题包含：

- `text`
- `projectSlug`
- `caseSlug`
- `facet`

适配器从包装对象中读取 `questions`，再转换为现有领域列表。前端响应不变。

### 4.6 summary

返回对象：

- `summary`

## 5. 数据流

```text
operation
→ 固定行为 Prompt
→ operation 对应的 JSON 契约
→ 不可信对话与已批准上下文
→ Provider 的 json_object 输出
→ 严格 DTO 反序列化
→ 现有领域校验
→ API 响应或确定性降级
```

结构契约只描述格式，不包含作品集事实。公开事实仍只通过 `approved_portfolio_context` 注入。

## 6. 错误处理

- 返回非 JSON：`INVALID_RESPONSE`。
- 缺少必填字段、枚举非法或存在额外字段：严格反序列化或领域构造失败，返回 `INVALID_RESPONSE`。
- suggestion 缺少 `questions` 时视为无效响应，不静默接受任意顶层数组或别名字段。
- Provider 超时或网络错误沿用现有错误码和确定性降级。
- 不记录 Prompt、访客正文、密钥或供应商原始响应。

## 7. 测试策略

遵循测试驱动修复：

1. 先添加失败测试，证明 generation 请求缺少 `title/resolution/blocks` 契约。
2. 添加六类 operation 的 Prompt 契约测试。
3. 添加 suggestion 对象包装的适配器解析测试。
4. 运行对话模型适配器相关测试。
5. 运行后端全量测试。
6. 使用不含私有资料的问题分别完成 DeepSeek 与 GLM 的真实端到端冒烟测试。

验收条件：

- 两家 Provider 均返回 `degraded=false` 的通用知识回答。
- 回答至少包含一个 `GENERAL` 块。
- generation 不再因字段结构不匹配降级。
- 全量自动化测试无失败。

