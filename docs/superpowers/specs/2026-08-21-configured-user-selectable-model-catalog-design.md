# 配置化用户可选模型目录与跨模型上下文设计
<!-- DOCUMENT_STATUS: APPROVED -->

> **日期：** 2026-08-21
> **状态：** 用户已批准按本设计进入 LEVEL_3 实施计划
> **范围：** Model Provider 配置权威、用户逐会话选择、Turn 执行快照、公开合同、跨模型上下文、DeepSeek 退役
> **不在范围：** 前端视觉与交互细节、自动故障转移、负载均衡、双模型并行回答、长期聊天存储

## 1. 背景与问题

当前运行时使用 `ModelProviderKind` 和 `ModelProviderRegistrySnapshot.builtIn()` 硬编码 DeepSeek V4 Flash 与 GLM-4.7，并在 Spring 启动时从 `ModelExpressionProperties.provider` 选择唯一 Provider、唯一凭据和唯一 `StructuredModelTransport`。这使 Provider 目录、模型名称、凭据分支和部署选择散落在 Java、YAML、脚本与测试中；前端也无法让访客在受审模型之间选择。

本次目标是退役 DeepSeek，将生产模型目录收敛为：

```text
glm-4-7-flash  -> glm-4.7-flash
qwen-3-7-flash -> qwen3.7-flash
```

访客在每个前端本地会话中选择模型。每个 Agent Turn 必须显式携带该选择；同一个模型完成该 Turn 内全部需要模型参与的阶段。作品集事实、权限、公开状态、证据和最终语义验证仍由后端确定性权威控制。

这不是通用聊天 Provider 插件系统，也不是把 Agent 退化为模型聊天套壳。模型仍只能参与现有受约束 Port；开放式 ReAct、动态 Tool、任意 Provider 安装和跨 Provider 自动重发继续禁止。

## 2. 分级与批准边界

本变更属于 LEVEL_3：

- 修改 `AgentTurnRequest`、`AgentTurnCommand` 与请求指纹；
- 修改 `/api/portfolio` 的公开 Agent 可用性投影；
- 修改 `PublicAgentTurn` 与前端共享合同；
- 替换生产 Provider 目录权威；
- 删除 DeepSeek 生产配置和调用路径；
- 改变模型选择从部署级单例到 Turn 级显式输入。

评审和批准前不得修改生产权威。批准后按两个闭合 Replacement Slice 推进：Slice A 替换模型目录与 Turn 选择权威，Slice B 替换旧 Assistant 标签摘要为中性 ConversationProjection。每个 Slice 都必须完成目标权威接入、调用方迁移、旧权威删除和验证；两个 Slice 全部完成前不得宣称本需求整体完成，也不保留运行时兼容桥。

## 3. 已确认产品决策

1. 生产目录只保留 GLM-4.7-Flash 与 Qwen3.7-Flash。
2. DeepSeek 从生产源码、当前配置、测试矩阵和部署脚本中退役。
3. 模型目录来自服务端启动配置，Java 不硬编码厂商模型枚举。
4. 前端模型列表来自后端安全投影，不静态复制 Provider 目录。
5. 模型选择属于前端本地会话偏好，但每个 Turn 请求是执行权威。
6. 页面刷新后消息和模型偏好一起丢失，重新使用当前 Catalog 默认选择；不新增浏览器持久化。
7. 每个 Turn 显式携带闭合 `ModelSelection`：`MODEL(modelRef + selectionVersion)` 或 `NONE`，不依赖后端隐式默认。
8. 一个 Turn 内的 Goal Interpretation、General Knowledge 与未来 Portfolio Expression 必须使用同一个模型选择。
9. 确定性 Capability 可以完全不调用模型；目录为空时使用显式 `NONE`，目录非空时仍可携带 `MODEL` 并公开投影 `participation=NONE`。
10. 任意终局 Turn 后允许切换模型，包括 Clarification；Pending Turn 内禁止切换。
11. Provider 失败时不自动调用另一 Provider；用户明确换模型后使用新 requestId 重试。
12. 跨模型上下文只传用户公开输入、后端确定性中性摘要和 typed 状态，不传隐藏思维链或原始 Provider 输出。
13. 前端 Agent 负责 UI 设计与实现；本设计只冻结共享合同和行为语义。

## 4. 核心不变量

### 4.1 Turn 单模型不变量

Turn 开始时冻结唯一 `ModelExecutionSnapshot`。任何模型阶段不得重新读取全局默认、前端当前选择或可变配置。

允许：

```text
Goal Interpretation = GLM
General Knowledge   = GLM
```

```text
Goal Interpretation = Qwen
General Knowledge   = Qwen
```

禁止：

```text
Goal Interpretation = GLM
General Knowledge   = Qwen
```

### 4.2 无隐式 fallback

所选 Provider 超时、429、5xx、非法 JSON 或不可用时：

- 不调用另一 Provider；
- 不重发访客内容；
- 返回闭合 `CAPABILITY_UNAVAILABLE`；
- 用户确认切换后使用新 requestId 发起新 Turn。

确定性 Capability 自身的公开 fallback 不属于跨 Provider fallback，继续允许。

### 4.3 上下文厂商无关

跨模型上下文不得包含：

- Provider session ID；
- `reasoning_content`；
- 隐藏思维链；
- Provider 原始 JSON；
- Prompt；
- API Key；
- Endpoint；
- 未验证或未公开 Evidence。

### 4.4 不新增持久模型偏好

不在 `conversation_session`、浏览器 `sessionStorage`、URL 或其他持久化位置保存 `selectedModelRef`。本变更不新增 `preferred_model_ref`、模型偏好 revision 或 ConversationSession 数据库迁移。

## 5. 目标架构

```text
启动配置
  -> ConfiguredModelCatalog
  -> 不可变 ModelCatalogSnapshot
  -> /api/portfolio 安全投影
  -> 前端本地 AgentSession.selectedModelRef
  -> POST /api/agent/turns ModelSelection
  -> RequestFingerprint
  -> ModelExecutionResolver
  -> ModelExecutionSnapshot
  -> Goal / General / Portfolio Expression
  -> PublicAgentTurn.modelExecution
  -> PublicAgentTurn.conversationProjection
  -> 原子 Settlement 与幂等回放
```

目标模块：

| 模块 | 责任 | 明确不负责 |
| --- | --- | --- |
| `ConfiguredModelCatalog` | 配置绑定、结构校验、版本、公开投影 | HTTP 调用、Goal、状态持久化 |
| `ModelExecutionResolver` | 校验请求选择并冻结执行快照 | 自动 fallback、用户偏好存储 |
| `ConfiguredStructuredModelTransport` | modelRef 到 Binding、Profile 请求、错误分类 | Goal、Evidence、PublicTurn |
| `ModelExecutionTracker` | 记录成功/失败的模型阶段并生成公开参与投影 | Provider 选择 |
| `ConversationProjectionFactory` | 从已验证公开结果确定性生成中性摘要 | 调用模型、读取原始响应 |

## 6. 配置化 Model Catalog

### 6.1 建议配置

```yaml
portfolio:
  model-runtime:
    enabled: ${PORTFOLIO_MODEL_RUNTIME_ENABLED:false}
    default-model-ref: glm-4-7-flash

    models:
      glm-4-7-flash:
        enabled: ${PORTFOLIO_GLM_ENABLED:false}
        selectable: true
        display-name: GLM-4.7-Flash
        display-order: 10
        selection-version: glm-4-7-flash-v1
        endpoint: https://open.bigmodel.cn/api/paas/v4/chat/completions
        model: glm-4.7-flash
        api-key: ${PORTFOLIO_GLM_API_KEY:}
        protocol-profile: ZHIPU_CHAT_COMPLETIONS
        data-policy-approved: ${PORTFOLIO_GLM_DATA_POLICY_APPROVED:false}
        structured-output: JSON_OBJECT
        thinking-mode: DISABLED
        streaming: false
        max-context-tokens: 200000
        max-output-tokens: 128000

      qwen-3-7-flash:
        enabled: ${PORTFOLIO_QWEN_ENABLED:false}
        selectable: true
        display-name: Qwen3.7-Flash
        display-order: 20
        selection-version: qwen-3-7-flash-v1
        endpoint: ${PORTFOLIO_QWEN_ENDPOINT:}
        model: qwen3.7-flash
        api-key: ${PORTFOLIO_QWEN_API_KEY:}
        protocol-profile: DASHSCOPE_CHAT_COMPLETIONS
        data-policy-approved: ${PORTFOLIO_QWEN_DATA_POLICY_APPROVED:false}
        structured-output: JSON_OBJECT
        thinking-mode: DISABLED
        streaming: false
        max-context-tokens: 1000000
        max-output-tokens: 128000

  model-operations:
    turn-interpretation:
      mode: ${PORTFOLIO_MODEL_OP_TURN_INTERPRETATION_MODE:DISABLED}
      schema-version: ${PORTFOLIO_MODEL_OP_TURN_INTERPRETATION_SCHEMA_VERSION:}
      max-output-tokens: ${PORTFOLIO_GOAL_INTERPRETATION_MAX_OUTPUT_TOKENS:1600}
      timeout: ${PORTFOLIO_AGENT_GOAL_INTERPRETATION_TIMEOUT:8s}
    general-knowledge:
      mode: ${PORTFOLIO_MODEL_OP_GENERAL_MODE:DISABLED}
      schema-version: ${PORTFOLIO_MODEL_OP_GENERAL_SCHEMA_VERSION:}
      max-output-tokens: ${PORTFOLIO_MODEL_MAX_OUTPUT_TOKENS:1200}
      timeout: ${PORTFOLIO_AGENT_GENERAL_KNOWLEDGE_TIMEOUT:10s}
```

### 6.2 配置语义

- `enabled=false`：允许 endpoint、Key 为空；不创建 Binding、不进入运行目录、不可调用。
- `enabled=true, selectable=false`：允许受控 canary，但不公开给访客。
- `enabled=true, selectable=true`：满足全部运行准入后进入公开目录。
- `default-model-ref` 只用于前端初始预选；目录为空时公开默认选择为 `NONE`，后端请求始终要求显式 `ModelSelection`。
- Provider Catalog 决定哪些模型可运行和公开选择；`model-operations` 保留部署级能力授权，决定 Goal Interpretation 与 General Knowledge 是否启用。
- 旧 `portfolio.model-operations.*.provider-ref` 被删除；具体 Provider 只由当前 Turn 的 `ModelSelection` 决定。
- Provider 的 context/output 值是厂商能力上限；Operation 的 max-output/timeout 是本项目授权上限，执行时取 Provider、Operation 与 Turn 剩余预算的最小值。

### 6.3 结构失败与能力失败

仅对 `enabled=true` 的 Provider 执行完整结构校验。启动失败：

- 重复或非法 modelRef；
- enabled Provider 的 endpoint 为空或非 HTTPS；
- model/label/Profile/能力字段非法；
- 未知协议 Profile；
- `default-model-ref` 指向不存在的配置条目；
- 配置声明 streaming，但共享 Transport 只支持非流式；
- 目录定义冲突。

模型能力失败关闭但应用可启动：

- 模型运行总开关关闭；
- Provider `enabled=false`，其空 endpoint 不参与校验；
- API Key 缺失；
- Provider 数据策略未批准；
- Provider 未设置为 selectable；
- 运行时 Provider 暂时不可用。

模型能力失败不得破坏公开内容浏览和确定性 Agent 路径。

### 6.4 全局 Catalog 版本与条目选择版本

全局 `modelCatalogVersion` 只用于 `/api/portfolio` 目录整体缓存和诊断，不进入 Turn 请求或请求指纹。它根据公开目录成员、display metadata、可用性与各条目的 `selectionVersion` 计算；给 Qwen 补 Key 导致目录成员变化时可以改变全局版本，但不会使仍选择 GLM 的请求失效。

每个可选条目公开显式配置的 `selectionVersion`。Turn 请求只携带所选条目的版本。以下变化必须提升 `selectionVersion`：

- 实际 model ID 改变；
- Provider 身份改变；
- protocol profile 改变；
- structured output、thinking 或 streaming 语义改变；
- 用户选择所代表的模型能力发生实质改变。

API Key 轮换、同一 Provider 内 endpoint 迁移、timeout、显示名称或顺序变化通常不提升 `selectionVersion`。内部 Descriptor fingerprint 可以包含 endpoint 和全部非 Secret 执行配置，但不得公开；公开版本不由 endpoint 哈希生成，避免把公开版本变成内部 endpoint 的哈希预言机。

Key 内容永不进入任何版本。Key 从存在变为缺失会使条目不可选并改变全局目录版本；如果条目仍存在但不可选，请求优先得到 `SELECTED_MODEL_UNAVAILABLE`，不会同时得到 stale。

目录不支持热更新。修改配置后重启并形成新 Snapshot；回退使用整体部署版本。

### 6.5 Operation 门与旧 `supports()` 替代

`TURN_INTERPRETATION` 与 `GENERAL_KNOWLEDGE` 的 `mode + schema-version + max-output-tokens + timeout` 继续是独立启动期授权。Catalog 的 enabled/selectable/data-policy 不取代 Operation 门；`provider-ref` 则被请求级选择替代并删除。

旧 `ModelProviderDescriptor.supports(modelPolicyVersion, answerSchemaVersion)` 及 `supportedModelPolicyVersions/supportedAnswerSchemaVersions` 被以下机制替代：

```text
ModelOperationPolicy.schemaVersion
-> 对应领域 Adapter / Codec / Validator 合同

Provider transport capabilities
-> JSON Object / 非流式 / 可关闭思考 / context-output 上限

Protocol Profile version
-> HTTP 参数与响应合同

真实 Provider canary
-> 证明具体模型实际满足应用 Schema
```

Provider Descriptor 不再声明应用层 Policy/Schema 版本。`participation` 根据 Operation 是否启用、模型阶段是否实际执行并成功采纳共同计算。

## 7. 协议 Profile

配置驱动不等于允许 YAML 构造任意 JSON。第一版只实现两个闭合 Profile：

### 7.1 `ZHIPU_CHAT_COMPLETIONS`

```json
{
  "response_format": { "type": "json_object" },
  "thinking": { "type": "disabled" },
  "stream": false
}
```

### 7.2 `DASHSCOPE_CHAT_COMPLETIONS`

```json
{
  "response_format": { "type": "json_object" },
  "enable_thinking": false,
  "stream": false
}
```

禁止配置任意 header、任意 body fragment、动态脚本或前端提交 endpoint/modelName。新增 Profile 仍需代码、测试、真实 Provider 证据和设计准入。

## 8. 安全公开目录

不新增 `/api/agent/models`。现有 `GET /api/portfolio` 的 `agentAvailability` 增加：

```json
{
  "status": "AVAILABLE",
  "freeTextSemanticRouting": "AVAILABLE",
  "modelCatalogVersion": "catalog-public-v3",
  "defaultModelSelection": {
    "kind": "MODEL",
    "modelRef": "glm-4-7-flash",
    "selectionVersion": "glm-4-7-flash-v1"
  },
  "selectableModels": [
    {
      "modelRef": "glm-4-7-flash",
      "selectionVersion": "glm-4-7-flash-v1",
      "displayName": "GLM-4.7-Flash"
    },
    {
      "modelRef": "qwen-3-7-flash",
      "selectionVersion": "qwen-3-7-flash-v1",
      "displayName": "Qwen3.7-Flash"
    }
  ]
}
```

不得公开 API Key、endpoint、Profile、内部 timeout、余额、Provider 原始健康错误或内部策略原因。

若没有可选模型，`selectableModels=[]`、`defaultModelSelection={"kind":"NONE"}` 且 `freeTextSemanticRouting=DISABLED`；公开内容和确定性 Preset 通过显式 `NONE` 选择继续可用。

若存在可选模型但配置的 `default-model-ref` 当前未通过运行准入，公开默认仍为 `NONE`，不得静默改选其他条目；`freeTextSemanticRouting` 可以保持 AVAILABLE，但调用方必须先显式选择公开条目，首页无选择路径不得自动发起自由文本 Turn。

前端 Agent 负责选择器、状态、无障碍和视觉表现，但模型列表、默认选择和可用性只能消费该投影。

## 9. Agent Turn 请求合同

`AgentTurnRequest` 根级新增必填的闭合联合类型。模型选择：

```json
{
  "modelSelection": {
    "kind": "MODEL",
    "modelRef": "qwen-3-7-flash",
    "selectionVersion": "qwen-3-7-flash-v1"
  }
}
```

显式无模型选择：

```json
{
  "modelSelection": {
    "kind": "NONE"
  }
}
```

规则：

- `kind` 只允许 `MODEL | NONE`；
- `MODEL.modelRef` 使用小写 kebab-case，长度 1—64；
- `MODEL.selectionVersion` 使用闭合、有界公共版本，长度不超过 128；
- `NONE` 不允许携带 modelRef、selectionVersion 或 Provider 字段；
- 拒绝未知字段；
- 请求不得携带 endpoint、modelName、Profile、API Key 或任意 Provider 参数；
- 所有 Turn 均携带闭合选择，即使最终 `participation=NONE`；
- `NONE` 明确禁止该 Turn 调用外部模型；确定性 Preset/Continuation 可继续执行，如果路径需要模型则返回现有 operation-specific capability unavailable；
- 前端可以使用 Catalog 的 `defaultModelSelection` 初始化，但不得省略后让后端猜测。

`AgentTurnRequestMapper` 将其映射为不可变 `AgentTurnCommand.ModelSelection`。`AgentTurnCommand` 的全部 Command 子类共享同一个根级选择。

## 10. 请求指纹、Claim 与回放

`RequestFingerprintFactory.canonical()` 增加：

```text
modelSelection.kind
modelSelection.modelRef（MODEL 时）
modelSelection.selectionVersion（MODEL 时）
```

行为：

| 场景 | 结果 |
| --- | --- |
| 同 requestId、同命令、同上下文、同模型 | 原终局幂等回放 |
| 同 requestId、不同 modelRef | `IDEMPOTENCY_KEY_CONFLICT` |
| 同 requestId、不同 selectionVersion | `IDEMPOTENCY_KEY_CONFLICT` |
| 同 requestId、`MODEL` 与 `NONE` 不同 | `IDEMPOTENCY_KEY_CONFLICT` |
| 换模型并使用新 requestId | 新 Turn |

生命周期顺序固定：

```text
解析和结构校验
-> 计算含 ModelSelection 的指纹
-> Claim
-> 已完成：直接回放，忽略当前 Catalog 变化
-> 新 Claim：校验当前 Catalog
-> 冻结 ModelExecutionSnapshot
-> 执行与 Settlement
```

已完成 Turn 即使原模型后来下架，也必须回放原加密公开终局；不得先以当前 Catalog 拒绝历史回放。

新 Claim 的 Catalog 校验失败必须形成可 Settlement 的闭合终局，不能遗留 `IN_PROGRESS` receipt。

## 11. ModelExecutionSnapshot

新请求校验通过后冻结内部快照：

```text
modelRef
selectionVersion
descriptorFingerprint
protocolProfile
structuredOutputMode
thinkingControl
maxInputTokens
maxOutputTokens
operationTimeout
```

快照不包含可持久化 API Key。Credential 只存在对应 Transport Binding 内。

Snapshot 必须作为显式不可变输入贯穿执行链；禁止 ThreadLocal、全局 currentProvider、可变 Spring Bean 或各 Port 重新查询默认 Provider。当前虚拟线程和任务并行不得改变选择语义。

## 12. 同一模型贯穿全部模型阶段

所选 Snapshot 必须进入：

- Goal Interpretation；
- General Knowledge；
- 未来受批准的 Portfolio Fact Expression。

确定性 Fast Path、Preset、Portfolio Evidence 和 Cross-domain deterministic composition 可以不调用模型。

业务 Model Port 仍保持领域专用；Provider 选择与 HTTP 差异集中在共享 infrastructure model transport。不得为 GLM/Qwen 复制 Goal/General Adapter。

## 13. 公开模型执行投影

`PublicAgentTurn` 增加公共 `modelExecution`：

```json
{
  "selectionKind": "MODEL",
  "requestedModelRef": "glm-4-7-flash",
  "selectionVersion": "glm-4-7-flash-v1",
  "participation": "GOAL_AND_ANSWER"
}
```

`NONE` 请求公开为 `selectionKind=NONE`、无 `requestedModelRef/selectionVersion` 且 `participation=NONE`。

`participation` 闭合为：

```text
NONE
GOAL_INTERPRETATION_ONLY
ANSWER_GENERATION
GOAL_AND_ANSWER
ATTEMPTED_UNAVAILABLE
```

它根据实际成功进入公开结果的模型阶段计算，不能只根据计划推断。

- `NONE`：没有外部模型内容参与；
- `GOAL_INTERPRETATION_ONLY`：模型成功解析，答案为确定性公开事实；
- `ANSWER_GENERATION`：Goal 为确定性来源，回答生成由模型参与；
- `GOAL_AND_ANSWER`：解析和生成均由所选模型参与；
- `ATTEMPTED_UNAVAILABLE`：尝试过所选模型，但未采纳 Provider 结果。

不得公开 endpoint、Credential、Provider request ID、Prompt、原始响应、reasoning 或内部延迟明细。

`modelExecution` 必须与 `PublicAgentTurn` 原子 Settlement 并进入加密短期回放，不能在 HTTP 返回时读取当前配置临时拼接。

## 14. 跨模型 ConversationProjection

### 14.1 目的

当前前端 Assistant 窗口对 `ANSWER` 主要保留 Goal 标签，难以支持“第二点展开”一类追问。跨模型并不需要迁移 Provider Session，但需要比标签更完整的厂商无关语义摘要。

### 14.2 生成来源

`ConversationProjectionFactory` 只从已验证的 `SemanticResult`、`PublicPresentation`、Clarification 或闭合 MessageTurn 确定性生成；不得调用模型总结或读取 Provider 原始输出。

`PublicAgentTurn` 增加：

```json
{
  "conversationProjection": {
    "schemaVersion": "assistant-window-v1",
    "text": "1. ... 2. ... 3. ..."
  }
}
```

### 14.3 边界

```text
单个 Assistant 摘要最多 800 字符
关键点最多 5 条
单条最多 140 字符
窗口最多最近 6 组完整 USER/ASSISTANT
总输入继续受 12K Token 上限约束
```

这里的 12K 是项目级 Provider-neutral 保守预算，不依赖 GLM 或 Qwen 的厂商 tokenizer。实现使用规范化 Unicode 文本、固定消息封装和序列化开销的保守估算，并同时满足 Provider 声明上限；厂商 tokenizer 只用于真实验收和成本观测，不成为生产路由依赖。

截断必须以完整 USER/ASSISTANT 对为单位。失败或取消且未形成 PublicAgentTurn 的本地输入不进入窗口。

摘要只存在于 PublicTurn 短期加密回放和前端页面内存；不单独进入长期状态。浏览器回传的 ConversationWindow 仍视为不可信提示，最终 Goal、主体、权限、证据和公开状态由后端重新验证。

### 14.4 禁止内容

摘要不得包含 Prompt、原始 Provider JSON、reasoning、API 错误正文、endpoint、Key、内部诊断、未公开 Evidence、Source 地址或失败/取消的访客输入。

## 15. 模型切换语义

- 前端每个本地会话维护闭合 `ModelSelection`，MODEL 时包含 `selectedModelRef + selectedSelectionVersion`，只存在页面内存。
- 新会话使用公开 Catalog 的 `defaultModelSelection` 初始化；空目录初始化为 `NONE`。
- Pending Turn 内冻结提交快照并禁止当前会话切换。
- 任意终局 Turn 后可以切换，包括 ANSWER、CLARIFICATION、BOUNDARY 和 CAPABILITY_UNAVAILABLE。
- Clarification 保存 typed blocked Goal，不绑定 Provider 隐藏状态；Resolve 使用新 requestId 和当前显式 ModelSelection。
- 普通幂等重试继续使用原 requestId 和原 ModelSelection。
- 换模型重试必须由用户确认、复用中性上下文并创建新 requestId。
- 首页交接首轮使用公开 Catalog 默认选择，并把 ModelSelection 纳入 handoff replay snapshot。
- 页面刷新后选择恢复当前 Catalog 默认，不单独恢复历史模型偏好。

前端 UI 位置、控件、动画、具体文案和响应式设计由前端 Agent 决定，但不得改变上述共享语义。

## 16. 错误与失败语义

### 16.1 HTTP 错误信封：不 Settlement

| HTTP | code | 条件 | 同 requestId 行为 |
| ---: | --- | --- | --- |
| 400 | `REQUEST_INVALID` | ModelSelection 结构非法、未知字段或字段形状不匹配 | 修正后必须使用新 requestId |
| 401 | 现有会话认证错误 | ResumeToken 非法 | 不进入 Turn |
| 409 | `IDEMPOTENCY_KEY_CONFLICT` | 同 requestId 的命令、上下文、kind、modelRef 或 selectionVersion 不同 | 原 requestId 不可重用 |
| 429 | 现有入口级限流 | 尚未 Claim/尚未执行 Provider | 按现有入口限流语义处理 |

这些错误使用 `AgentApiErrorResponse`，不产生 `PublicAgentTurn`，也不创建 Provider 执行终局。

### 16.2 HTTP 200 settled `CAPABILITY_UNAVAILABLE`

| code | 条件 | retryable | 重试含义 |
| --- | --- | ---: | --- |
| `MODEL_SELECTION_STALE` | 所选 ref 当前可选，但 selectionVersion 已变化 | false | 刷新目录、确认选择并使用新 requestId |
| `SELECTED_MODEL_UNAVAILABLE` | ref 不存在、不可选或运行准入不满足 | false | 重新选择并使用新 requestId |
| `SELECTED_MODEL_TEMPORARILY_UNAVAILABLE` | timeout、5xx 或临时传输失败 | true | 保持模型，稍后使用新 requestId |
| `SELECTED_MODEL_RATE_LIMITED` | Provider 429 | true | 等待有界 retryAfter 后使用新 requestId |
| `SELECTED_MODEL_INVALID_RESPONSE` | 空响应、非法 JSON 或结构校验失败 | false | 当前结果不可采纳，可换模型并使用新 requestId |

以上五个 code 全部形成原子 Settlement 的 `CAPABILITY_UNAVAILABLE` PublicTurn。settled PublicTurn 的同 requestId 永远只回放原终局；`retryable=true` 只表示前端可以基于冻结问题和中性上下文创建一个新的 Turn，不表示同 requestId 会重新执行 Provider。

错误不得说明“缺少某家 API Key”等内部原因，不得携带原始 Provider body。

### 16.3 选择校验优先级

MODEL 选择的校验顺序固定：

```text
1. 查找 modelRef，验证当前 enabled/selectable/credential/policy 准入
2. 不存在或不可选 -> SELECTED_MODEL_UNAVAILABLE
3. 当前可选，再比较 selectionVersion
4. 版本不同 -> MODEL_SELECTION_STALE
5. 版本一致 -> 冻结 ModelExecutionSnapshot
```

因此 Key 被移除时只返回 unavailable，不会同时返回 stale。别的目录条目新增、下架或 Key 变化不会使当前所选条目 stale。

### 16.4 显式 NONE 与确定性路径

完全不需要模型的已审 Preset/确定性路径在 `ModelSelection.kind=NONE` 下正常执行并返回 `participation=NONE`。如果执行路径实际需要 Goal Interpretation 或 General Knowledge，则沿用对应 operation-specific capability unavailable 终局；不得临时猜默认模型或切换到 Catalog 条目。

MODEL 选择本身 stale/unavailable 时仍按 16.2 Settlement，不能因为最终任务可能确定性执行而绕过显式选择合同。

## 17. 隐私与安全

- API Key 只从服务端环境配置注入 Transport Binding；
- modelRef 可公开，但 endpoint、Credential 与内部 Profile 不公开；
- 访客文本只发送给其显式选择的单一 Provider；
- 不自动向第二 Provider 重发；
- 不记录问题、ConversationWindow、Prompt、原始 Provider 输出或 reasoning；
- 公开 catalog/selection 版本不含 Key 或 endpoint；内部 Descriptor fingerprint 可含 endpoint，但不公开；
- `ModelExecutionSnapshot` 不进入公开响应或持久化 Secret；
- PublicTurn 只保存安全 `modelExecution` 与确定性 `conversationProjection`；
- 不扩张浏览器持久化和 ConversationSession 状态类别。

## 18. 旧 Provider 与配置权威退役

### 18.1 DeepSeek 删除

Slice A 删除：

- `ModelProviderKind.DEEPSEEK_V4_FLASH`；
- DeepSeek built-in Descriptor、endpoint 和 credential 分支；
- `PORTFOLIO_AGENT_DEEPSEEK_API_KEY`；
- 启动脚本、Provider probe、live quality 和 packaged-JAR 中的 DeepSeek 选择分支；
- 当前权威文档中的 DeepSeek 默认/可选运行声明；
- 只证明旧单 Provider 结构的测试。

### 18.2 GLM 与 namespace 迁移

当前 GLM Descriptor 的 `glm-4.7` 必须替换为 `glm-4.7-flash`，不能只修改显示名。同步完成以下无兼容桥迁移：

```text
portfolio.conversational-model.*
-> portfolio.model-runtime.* / portfolio.model-runtime.models.*

PORTFOLIO_MODEL_ENABLED
-> PORTFOLIO_MODEL_RUNTIME_ENABLED

PORTFOLIO_MODEL_DATA_POLICY_APPROVED
-> 删除，改为每 Provider data-policy-approved

PORTFOLIO_MODEL_PROVIDER
-> 删除

PORTFOLIO_AGENT_GLM_API_KEY
-> PORTFOLIO_GLM_API_KEY

PORTFOLIO_MODEL_OP_*_PROVIDER_REF
-> 删除
```

新增：

```text
PORTFOLIO_GLM_ENABLED
PORTFOLIO_GLM_API_KEY
PORTFOLIO_GLM_DATA_POLICY_APPROVED
PORTFOLIO_QWEN_ENABLED
PORTFOLIO_QWEN_ENDPOINT
PORTFOLIO_QWEN_API_KEY
PORTFOLIO_QWEN_DATA_POLICY_APPROVED
```

迁移清单覆盖 `application.yml`、`.env.example`、Model properties/configuration、Eval CLI 与测试、`start-local.ps1`、`run-jar-e2e.ps1`、Provider probe、live public/general quality scripts、环境恢复断言、privacy/documentation scanners 和当前权威文档。

不得保留旧环境变量或旧 namespace 到新 Catalog/ModelSelection 的运行时翻译。回退只使用旧 Git/JAR/整体部署版本。

历史 specs、plans 和 reports 可以保留 DeepSeek 当时事实，但继续标明 NON_AUTHORITATIVE/HISTORICAL，不得重新成为生产配置来源。

## 19. 共享合同原子更新

Slice A 内以下内容必须原子更新：

- `AgentAvailabilityResponse` 与 Public Portfolio contract；
- `AgentTurnRequest`、Mapper、Command；
- `RequestFingerprintFactory`；
- `PublicAgentTurn.modelExecution` serializer；
- Agent State public replay payload codec/version；
- `contracts/agent-turn` fixtures；
- 前端 TypeScript 请求类型、modelExecution parser 和 fixture consumers；
- 首页 handoff replay snapshot；
- packaged-JAR Browser fixtures。

Slice B 内 `PublicAgentTurn.conversationProjection`、回放 payload、前端 ConversationWindow consumer 和 fixtures 原子更新，并删除旧 Goal 标签摘要路径。不得保留新旧请求字段、双 parser、双摘要权威或兼容 fallback。

## 20. 测试策略

### 20.1 Catalog

- 模型总开关关闭时应用正常启动且公开目录为空；
- Provider `enabled=false` 时允许 endpoint/Key 为空且不参与结构校验；
- Provider `enabled=true` 且 endpoint 为空/非 HTTPS 时启动失败；
- 两个有效配置生成稳定 Catalog；
- 重复/非法 ID、非 HTTPS、未知 Profile 和结构冲突启动失败；
- 缺 Key/未批准策略的模型不进入公开目录；
- 默认 ref 不存在启动失败；
- 公开版本不含 Key 或 endpoint；内部 Descriptor fingerprint 不公开；
- model/Profile/执行语义变化要求 selectionVersion 提升；
- 非空 Key 轮换不改变 selectionVersion；Key 可用性变化只改变公开目录成员/全局版本；
- Qwen 可用性变化不会使 GLM selectionVersion stale；
- defaultModelSelection 在空目录时为 NONE；
- 默认 ref 未就绪但其他模型可选时不静默替换，公开默认为 NONE；
- Operation mode/schema/max-output/timeout 保持独立，旧 provider-ref 不存在；
- 旧 provider policy/schema `supports()` 不存在，Operation Schema + Profile/Capability + live gate 成为唯一替代；
- 生产目录无 DeepSeek。

### 20.2 Transport

GLM 请求必须包含 `thinking.type=disabled`，不得包含 `enable_thinking`。Qwen 请求必须包含 `enable_thinking=false`，不得包含 GLM thinking object。两者均包含 JSON response format、`stream=false`、正确 model、Authorization Header 和有界 max tokens。

共同覆盖 429、5xx、timeout、body stall、中断、空 content、非法 JSON、多 choices、响应过大和诊断失败。不得输出原始 body。

### 20.3 Turn 与幂等

- 同一 Turn 所有模型调用使用同 modelRef；
- 同 requestId 同模型回放；
- 同 requestId 换模型、换 selectionVersion 或在 MODEL/NONE 间切换均冲突；
- 模型下架后历史终局仍可回放；
- 五个模型错误码均使用 200 settled PublicTurn；结构/认证/指纹/入口限流继续使用非 settled HTTP 信封；
- `retryable=true` 只能以新 requestId 发起新 Turn，同 requestId 只回放错误终局；
- 新 Claim stale/unavailable 能 Settlement，不遗留 Active receipt；
- 不可选优先于 stale；别的 Catalog 条目变化不使当前选择 stale；
- NONE 下确定性 Preset 可用，模型必需路径失败关闭且零 Provider 调用；
- GLM 失败不调用 Qwen，反向同理；
- GLM Turn 后 Qwen Turn 可延续同一中性上下文和 typed discussion；
- Clarification 后换模型使用新 requestId；
- participation 根据实际成功阶段投影。

### 20.4 ConversationProjection

覆盖通用解释、比较、作品集事实、推荐、Clarification、Boundary、Unavailable、空/超长 Presentation、五条关键点、800 字符上限、完整对截断和回放一致性。

必须包含真实语义场景：

```text
Turn 1 GLM：列出三个机制
Turn 2 Qwen：第二点展开
```

同时证明摘要不含 Prompt、Provider 原始输出、reasoning、私有 Evidence 或内部地址。

### 20.5 共享合同 fixtures

至少新增：

```text
portfolio-model-catalog.json
turn-request-glm.json
turn-request-qwen.json
answer-model-goal-only.json
answer-model-goal-and-answer.json
answer-deterministic.json
model-selection-stale.json
selected-model-unavailable.json
turn-request-none.json
cross-model-follow-up.json
```

后端 serializer 与前端 parser 消费同一合同源。

## 21. 真实 Provider 验收

GLM-4.7-Flash 与 Qwen3.7-Flash 分别通过同一套受控真实 Provider 矩阵，不能用“OpenAI compatible”声明替代证据。

### 21.1 Goal Interpretation

作品集事实、通用知识、比较、推荐数量、约束、指代、项目讨论、澄清、安全社交和超范围输入均产生合法 closed SemanticRoute，并通过后端验证。

### 21.2 General Knowledge

简体中文、JSON 合同、三种深度、概念/机制顺序、比较维度、禁止额外字段、长度和内容安全全部通过。

### 21.3 运行质量

完整非流式响应在 operation deadline 内；429/timeout/5xx/非法 JSON 映射正确；固定矩阵达到批准成功率；日志和报告不含问题或模型正文。

必须独立获得：

```text
GLM_4_7_FLASH_LIVE_PASS
QWEN_3_7_FLASH_LIVE_PASS
```

真实调用需要单独授权；未运行不得记为 PASS。

## 22. Replacement Slices 与回退

### 22.1 Slice A：模型目录与 Turn 选择

固定顺序：

1. 创建配置化 Catalog、条目 selectionVersion、Operation 门和两个闭合 Profile；
2. 增加联合 `ModelSelection`、请求指纹和执行解析；
3. 接入唯一生产 Turn 路径并保证单 Turn 单模型；
4. 增加 `/api/portfolio` 公开目录和 `PublicAgentTurn.modelExecution`；
5. 前端 Agent 原子迁移共享合同与 UI；
6. 运行离线、Memory、PostgreSQL、合同和 packaged-JAR 门；
7. 经授权运行 GLM/Qwen 真实 Provider 门；
8. 删除 DeepSeek、built-in Registry、全局 Provider 选择、旧 namespace/env、operation provider-ref 和旧测试；
9. 运行生产源码零引用和受影响全量门；
10. 更新 `AGENTS.md` 中失真的 “fixed DeepSeek/GLM adapters” 声明，以及当前权威文档、架构状态和项目演进日志；
11. 打包单一 JAR。

### 22.2 Slice B：中性 ConversationProjection

固定顺序：

1. 创建确定性 `ConversationProjectionFactory`；
2. 将 projection 接入 `PublicAgentTurn`、短期回放和共享 fixtures；
3. 前端 Agent 将 ConversationWindow 改为消费 projection；
4. 通过有界、隐私、幂等和跨模型指代门；
5. 删除旧 Goal 标签摘要路径；
6. 运行受影响全量和 packaged Browser 跨模型场景。

Slice A 可以独立证明模型选择和 Provider 路由，但不得据此宣称完整跨模型上下文功能完成。Slice B 失败时不得保留新旧摘要双权威；使用提交/JAR 版本回退。

两个 Slice 都完成前本需求整体状态保持 `IN_PROGRESS`。不得形成包含旧 `PORTFOLIO_MODEL_PROVIDER` 与新 `ModelSelection` 的稳态版本。回退只使用 Git/JAR/整体部署；当前尚未生产部署，必要时可清空短期 Agent State 后回退。

## 23. 完成条件

只有以下全部满足才可声明完成：

- DeepSeek 在生产源码、当前配置和部署脚本零引用；
- GLM 实际调用 `glm-4.7-flash`；
- Qwen 实际调用 `qwen3.7-flash`；
- 前端目录来自 `/api/portfolio` 安全投影；
- 每个 Turn 显式携带并指纹化 MODEL/NONE ModelSelection；
- 空目录下 NONE Preset/确定性路径可用，模型必需路径失败关闭；
- 条目级 selectionVersion 只使所选条目语义变化 stale；
- 五个模型错误码的载体、Settlement 和新 requestId 重试语义通过；
- 同一 Turn 全部模型阶段使用同一 Snapshot；
- 跨模型追问使用中性摘要和相同 typed 状态成功；
- 无自动跨 Provider 重发；
- modelExecution 与 conversationProjection 可幂等回放；
- 两个 Provider 真实矩阵均通过；
- Backend、Frontend、共享合同、PostgreSQL、privacy、architecture、documentation、packaged-JAR 和 Browser 门全部通过；
- 旧权威已删除而非仅禁用；
- `portfolio.conversational-model.*`、旧 env 和 operation provider-ref 零引用；
- 当前权威文档和架构状态反映真实结果。

## 24. 独立评审清单

评审 Agent 应重点检查：

1. Turn 单模型不变量能否在并行执行中显式保持；
2. Claim 前后 Catalog 校验与历史回放顺序是否完整；
3. 五个模型 code 与 HTTP 信封/settled Turn 的载体是否唯一，retryable 是否只创建新 requestId；
4. 条目 selectionVersion、全局 Catalog 版本和不可用优先级是否避免无关条目 stale；
5. Provider 凭据是否可能进入 Descriptor、Snapshot、日志或公开投影；
6. `participation` 是否准确区分成功采纳与仅尝试失败；
7. ConversationProjection 是否足以支持跨模型指代，同时保持有界和不可信输入定位；
8. 前端静态列表、隐式默认或浏览器持久模型偏好是否被意外重新引入；
9. DeepSeek、旧 GLM 命名、旧 namespace/env/provider-ref 删除清单是否覆盖 Java、YAML、脚本、真实探针、fixtures、Eval 和当前文档；
10. GLM/Qwen Profile 是否与真实官方 API 参数一致；
11. 公共合同和短期回放 payload 升级是否需要额外版本/清理措施，Slice A/B 边界是否能独立回退；
12. NONE 是否只允许确定性执行，Operation 门与旧 supports() 替代是否闭合，且不存在动态 Provider、自动 fallback 或运行时兼容桥。

评审通过后，应由用户明确批准，将本文改为 `DOCUMENT_STATUS: APPROVED` 并加入 `docs/00-文档状态索引.md`；随后再创建独立实施计划。
