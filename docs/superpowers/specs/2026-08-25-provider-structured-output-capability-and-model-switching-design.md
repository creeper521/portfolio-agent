# Provider 结构化输出能力与模型切换原子绑定设计

<!-- DOCUMENT_STATUS: APPROVED -->

> 日期：2026-08-25
> 状态：用户已批准；按独立 Level 3 实施计划执行
> 分级：`ARCHITECTURE_REVIEW`；批准后按 Level 3 Replacement Slice 实施
> 直接证据：Qwen3.7-Flash 已完成 HTTP 调用，但 Goal Interpretation 返回被拒绝为 `SCHEMA/UNSUPPORTED_ROOT_KIND`
> 关联事项：A2-87、A2-88、A2-116、A2-117
> 关联权威：`docs/16-Agent单权威持续收敛范式.md`、`docs/superpowers/specs/2026-08-21-configured-user-selectable-model-catalog-design.md`、`docs/superpowers/specs/2026-08-25-low-information-goal-interpretation-stabilization-design.md`
> 自审修订：已对照当前 `ModelOperationPolicy/AgentRuntimeReadiness`、Catalog/Binding/Snapshot、Goal/General Codec 以及 Gala/Hermes/OpenCode 源码完成第二轮架构自审；本版已删除可配置生产降级等级、独立 carrier 开关与请求侧 contractRef 三处冗余权威

## 1. 审核摘要

### 1.1 2026-08-26 v4 增量授权与设计冻结

用户已明确授权实施 `Provider Draft -> canonical goal.proposal.v5` 确定性编译层，并将 Qwen/GLM 的 `selectionVersion` 同步提升到 v4。本增量不启动 `goal.proposal.v6`，而是在 Provider 边界增加一个更小的传输合同；`goal.proposal.v5` 仍是应用层唯一 canonical Goal 合同和领域准入权威。

v4 唯一生产链冻结为：

```text
frozen OperationBinding
  -> providerContractRef = goal.provider-draft.v1
  -> applicationContractRef = goal.proposal.v5
  -> compilerProfile = goal-provider-draft-compiler.v1
  -> exactly one Provider call
  -> strict parse-once
  -> Provider Draft Schema validation
  -> verify frozen compiler profile
  -> deterministic compilation
  -> canonical goal.proposal.v5 Schema validation
  -> GoalProposalCodec + existing semantic validator
```

`OperationBinding` 必须原子冻结 Provider 合同、Application 合同、两份 fingerprint 与 compiler profile；binding fingerprint 必须覆盖这些字段。两个合同相同只允许 identity compiler；两个合同不同时只允许代码批准的 Goal Draft compiler。任何缺失、未知或不匹配必须在传输前或对应层 fail-closed，不能绕过编译层直接把 Draft 交给 canonical Codec。

`goal.provider-draft.v1` 只让 Provider 表达语义选择和用户可推断字段。以下 canonical 字段由服务端从可信输入与闭集规则确定性派生：`goalKey`、`anchors`、`requestedOutputs`、`knowledge`、缺省 `parameters`、canonical nullable branches，以及澄清时的 `blockedGoal`。编译器只能做闭集映射、可信目录匹配、缺省派生和一致性检查；不得猜测未知 subject、移动错误 anchor、扩大 candidate/route/goalKind、改变 recommendation size，或把非法 Draft 修成合法 Goal。

v4 继续严格禁止：自动 repair、第二次 Provider 调用、同 Provider 自动 retry、运行时 strategy fallback、跨模型降级、放宽 canonical v5、记录 Provider 原始输出。Provider Draft Schema、确定性 Compiler 和 canonical Schema 任一层失败都形成独立的安全诊断层，且终止当前 Turn。

Qwen 与 GLM 的生产 `selectionVersion` 分别冻结为 `qwen-3-7-flash-v4`、`glm-4-7-flash-v4`。旧 v3 选择按既有 stale-selection 语义拒绝，不做静默迁移。真实矩阵必须两家独立执行；每家至少包含 5 次直接明确推荐和 5 次同一 conversation 的 `1 -> 给我推荐两个项目`，后者第一轮必须是确定性 Conversational 且 Provider 调用为 0，10 个 Goal 成功样本必须合计恰好 10 次 Goal Provider 终端调用。任何额外调用都视为隐藏 retry/repair，任何一家失败都不能被另一家结果覆盖。

### 1.2 2026-08-26 Qwen v6 General 增量授权

在 v4 Goal 设计之上，用户进一步明确授权仅将 Qwen 提升为 `qwen-3-7-flash-v6`，为 General 注册并绑定 `general.provider-draft.v2`，再由确定性 Compiler 生成现有 `general.draft.v2`。GLM 保持 `glm-4-7-flash-v4`；Goal 继续绑定 `goal.provider-draft.v1 -> goal.proposal.v5`，未授权的 `goal.provider-draft.v2` 不进入生产绑定。

本增量继续禁止自动 repair、同请求重试、运行时 strategy fallback 和跨模型降级。Qwen v6 的 General Draft、canonical General 合同、Prompt、Catalog、公开合同、前端选择版本和真实矩阵脚本必须原子同步；旧 Qwen v4/v5 选择按 stale-selection 语义拒绝。

当前问题不是“Qwen 没接通”，也不是严格 Codec 本身错误，而是 Provider 接入层把三件不同的事情合并成了一个过度宽泛的 `JSON_OBJECT` 声明：

1. Provider 能否返回语法合法的 JSON；
2. Provider 能否被请求约束到指定 JSON Schema 或指定 Tool Arguments；
3. 返回对象能否通过本项目的领域结构与语义合同。

现有实现只在请求中发送 `response_format={"type":"json_object"}`，然后依赖 Prompt 与后端 Codec 让模型“自行猜中” `goal.proposal.v5`。这种设计是安全的，因为不可信结果会 fail-closed；但可靠性不足，因为 JSON Object 只保证 JSON 语法，不保证 root kind、字段集合、字段类型或业务组合。真实 Qwen 样本已经证明：Provider 调用成功与应用结构成功是两回事。

本设计建议保留现有模型选择、单 Turn 单模型、无自动跨 Provider fallback、新 requestId 重试和后端严格语义校验；替换 Provider 协议能力层，使每个模型、每个 Operation 在 Turn 开始时冻结一份明确的结构化输出绑定：

```text
ModelSelection
  -> ModelExecutionResolver
  -> frozen ModelExecutionSnapshot
       -> operation binding
            -> contractRef
            -> structuredOutputStrategy
            -> provider protocol profile
            -> token field policy
  -> StructuredOutputGateway
       -> provider request compiler
       -> one HTTP call
       -> provider response extractor
       -> local canonical-schema validation
       -> domain semantic decoder / validator
```

第一版策略闭集：

- `NATIVE_JSON_SCHEMA`：Provider 原生严格 JSON Schema；
- `REQUIRED_TOOL_CALL`：单个合成输出 Tool，强制返回该 Tool 的 arguments。

现有 `JSON_OBJECT` 只保留为迁移期旧权威和隔离 canary/对照工具，不进入新的生产 `OperationBinding`，也不形成第三种生产策略或可配置降级等级。生产 binding 的存在本身就表示该 Operation 已获得 Provider 侧结构约束；不存在 binding 时必须调用前失败关闭。

本文不新增模型 root kind，不启动 `goal.proposal.v6`，不放宽任何服务端语义校验，也不增加自动 repair、同 Provider 重试或跨模型重发。

## 2. 已确认事实与证据边界

### 2.1 已确认的运行事实

- Qwen 的 API Key、Base URL、模型名和网络调用已生效；存在 HTTP 200 与可采用成功样本；
- 低信息输入已由独立 `UnresolvedIntentPolicy` 在确定性闭集内拦截，`1` 第一轮不再调用 Provider；
- 后续明确推荐请求仍出现 `provider.call.completed -> provider.output.rejected`；
- 最新闭集诊断把该样本定位为 `SCHEMA/UNSUPPORTED_ROOT_KIND`；
- GLM 当前主要暴露 `SELECTED_MODEL_RATE_LIMITED`，是另一条 Provider 可用性问题；
- 用户切换模型后使用新 requestId，新 Turn 不复用失败结果；
- 失败时不自动调用另一 Provider，不发送 repair Prompt。

### 2.2 当前源码事实

当前生产路径具有以下结构性限制：

1. `ConfiguredModelCatalog` 对所有获准模型硬编码同一组 `TURN_INTERPRETATION + GENERAL_KNOWLEDGE` 能力；
2. `ModelRuntimeProperties.ModelSettings.structuredOutput` 只有 `JSON_OBJECT` 一个受理值；
3. `ConfiguredModelCatalog.descriptor()` 强制 `structured-output=JSON_OBJECT`；
4. `ModelProviderProtocolProfile.common()` 对 Qwen 与 GLM 都注入 `response_format.type=json_object`；
5. `StructuredModelRequest` 只携带 operation、prompt、token、temperature、deadline，不携带合同引用或 Canonical Schema；
6. `OpenAiCompatibleStructuredModelTransport` 只读取 `choices[0].message.content`，不能消费 `tool_calls[].function.arguments`；
7. Transport 无论策略都发送 `max_tokens`；
8. `GoalProposalCodec` 同时承担 JSON 结构检查、上下文相关约束和领域语义构造，Provider 请求端无法复用同一结构权威；
9. `ModelExecutionSnapshot` 冻结 model/profile/能力/预算，但没有冻结“某 Operation 使用哪种结构化策略与哪个合同”。

### 2.3 官方能力事实

截至本文编写时，阿里云百炼官方“结构化输出”文档明确区分：

- JSON Object 只保证合法 JSON，不保证固定结构；
- JSON Schema 可以约束字段与类型；
- 当前支持列表包含 Qwen3.7-Flash 系列；
- 开启结构化输出时不建议设置 `max_tokens`，因为可能截断 JSON。

官方 Function Calling 文档同时列出 Qwen3.7-Flash 系列，并提供强制指定单个 function 或 `tool_choice=required` 的请求语义。上述文档事实只形成候选策略依据，不替代对本项目真实 endpoint、model ID、非思考模式、Schema 子集和响应 envelope 的 canary。

GLM-4.7-Flash 的直连智谱 endpoint 在本文阶段尚未取得同等强度的“严格 JSON Schema 或强制 Tool Choice”官方与真实双证据，因此本文不猜测其最终策略。GLM 必须先经过隔离能力探针，再冻结为 `NATIVE_JSON_SCHEMA`、`REQUIRED_TOOL_CALL` 或不具备该 Operation 的生产资格。

### 2.4 尚未确认，禁止写成结论

- 不能仅凭一次 `UNSUPPORTED_ROOT_KIND` 断言所有 Qwen 输出都会失败；
- 不能仅凭官方“支持结构化输出”断言本项目复杂 `goal.proposal.v5` 全部关键字受支持；
- 不能仅凭 OpenAI Compatible 断言 GLM/Qwen 的请求字段与 envelope 完全相同；
- 不能把 HTTP 200、JSON 可解析或一次成功升级为 schema/semantic/quality PASS；
- 不能把 Provider 严格 Schema 当成可信边界，服务端本地校验仍必须存在。

## 3. 为什么属于 Architecture Review / Level 3

本设计替换的是当前生产 Provider 结构化调用权威，不是一个 Adapter 内部小改：

- `StructuredModelRequest/Response/Transport` 合同改变；
- `ModelProviderProtocolProfile` 的责任改变；
- `ModelProviderDescriptor`、`ModelTransportBinding`、`ModelExecutionSnapshot` 增加按 Operation 的策略绑定；
- Goal/General 的结构权威从“Prompt + 手写 Codec 重复表达”收敛为 Canonical Contract；
- 旧 `JSON_OBJECT` 单策略生产路径必须同期删除；
- Provider 响应 envelope 从只支持 content 扩展为 content/tool arguments 两种闭合 carrier；
- Catalog 能力不再由固定集合推断，而由可执行 Operation Binding 派生。

因此本文批准前只允许分析、静态 fixture 和不接入生产的隔离探针设计；不得修改生产权威。批准后必须按 Replacement Slice 实施，不保留配置式新旧双栈。

## 4. 必须保留不变的设计

以下设计已经基本正确，任何实现不得以“提高成功率”为由削弱：

1. 每个 Turn 显式携带 `MODEL(modelRef + selectionVersion)` 或 `NONE`；
2. Claim 后冻结唯一 `ModelExecutionSnapshot`；
3. 一个 Turn 内 Goal 与 General 使用同一模型；
4. Pending Turn 内不能切换模型；任意终局后才能切换；
5. 换模型重试使用新 requestId；同 requestId 只做既有幂等回放；
6. Provider 失败不自动跨模型降级；
7. 每个 Operation 每 Turn 仍只有一次 Provider 调用；
8. 不发送 repair Prompt，不把被拒正文再次交给模型；
9. Provider 输出始终不可信，必须本地结构与语义校验；
10. `recentReference` 必须命中 typed recent state；
11. 公开事实、权限、Evidence、route、subject 与 recommendation size 继续由服务端验证；
12. 不记录用户原文、Prompt、Provider 原始响应、reasoning 或 Credential；
13. 低信息闭集继续由服务端确定性处理，不交给 Provider；
14. 前端不修复、不猜测 Provider 结构。

## 5. 问题定义

### 5.1 当前抽象把“请求形态”误当成“模型能力”

`JSON_OBJECT_REQUEST` 只能说明 Adapter 发送了某个字段，不能说明模型遵循应用 Schema。当前文档已经有这种证据分层意识，但生产类型仍只有 `structuredOutput=JSON_OBJECT`，导致协议画像、能力声明、Operation 准入和结构合同之间缺少可执行连接。

### 5.2 Profile 同时承担过多责任

当前 `ModelProviderProtocolProfile` 同时负责：

- Provider 身份差异；
- thinking 关闭字段；
- structured output 方式；
- stream 字段。

当 Qwen 使用 JSON Schema、GLM 使用 required tool，或同一 Provider 的不同 model 需要不同参数时，继续给 Profile 堆分支会把 Provider、model、Operation 与合同四个维度耦合到一个枚举中。

目标不是把这些字段再塞进一个更大的枚举：`ModelProviderProtocolProfile` 只保留 Provider envelope/thinking/stream 的闭集差异；`ApprovedModelExecutionProfile` 是代码所有的不可变组合记录，把 protocol profile 与各 OperationBinding 原子配对，自身不实现请求编译分支。Compiler/Extractor 按组合中的闭集版本路由，环境不能修改组合内部字段。

### 5.3 Codec 既是结构权威又是语义权威，但无法投影给 Provider

当前 Codec 的严格性是安全优点；问题在于结构规则只存在于 Java 分支中，Provider 只能从自然语言 Prompt 猜测。继续增加 Prompt 示例会产生更多重复表达，并不能消除 Schema 漂移。

### 5.4 模型切换冻结了模型，却没有冻结 Operation 协议策略

当前 Snapshot 可以证明“这一 Turn 是 Qwen”，但不能完整证明：

- Goal 使用的是 JSON Schema 还是 JSON Object；
- General 是否使用相同或另一结构策略；
- max token 字段是否应发送；
- response 应从 content 还是 tool arguments 提取；
- 使用的是哪个 Canonical Contract fingerprint。

这使切换模型后虽然 modelRef 正确，实际协议行为仍依赖共享 Transport 的全局实现。

## 6. 目标与非目标

### 6.1 目标

- 为每个 model + operation 冻结闭合结构化输出策略；
- 让 Provider 请求与本地结构校验消费同一个 Canonical Contract；
- 让 Qwen/GLM 的协议差异集中在基础设施层，不复制 Goal/General Adapter；
- 让切换模型同时原子切换 endpoint、model、protocol、thinking、output strategy、response extraction profile 与 token policy；
- 在 Provider 不满足某 Operation 最低结构等级时，调用前失败关闭；
- 保留本地严格结构/语义验证；
- 通过真实独立矩阵决定具体模型是否可选，而不是用“OpenAI compatible”推断；
- 删除当前硬编码 JSON Object 单策略与重复结构权威。

同时要求生产协议组合来自代码所有的闭集 `ApprovedModelExecutionProfile`；环境变量只选择已批准 Profile，不能自由拼装 strategy、Tool 名、carrier 或 token policy。

### 6.2 非目标

- 不新增或修改公开 Goal root kind；
- 不启动 `goal.proposal.v6` 或 `general.draft.v3`；
- 不引入动态 Provider 插件、任意 YAML body fragment，亦不允许前端/Turn 请求提交 endpoint；现有服务端部署配置的 HTTPS endpoint 与整体重启语义保留；
- 不建设 ReAct、真实 Tool 执行循环或 MCP；
- `REQUIRED_TOOL_CALL` 只把 Tool Arguments 当作结构化载体，不执行工具、不发第二次模型请求；
- 不增加自动 retry、repair、fallback、负载均衡或双模型并行；
- 不改变前端模型切换交互与本地会话偏好语义；
- 不把真实 Provider 的暂时限流当作代码修复；
- 不以本设计关闭 A2-87/A2-88/A2-116。

## 7. 开源参考项目的采纳边界

### 7.1 Gala Agent：采纳 per-model compatibility

可采纳：

- Model 对象同时携带 provider、api、baseUrl、contextWindow、maxTokens、reasoning 与 compat；
- compat 负责模型级差异，例如 token 字段、strict 支持和 thinking 格式；
- 切换模型后重新夹取 thinking level，并保存完整 provider/model 组合。

不采纳：

- 不把本项目做成任意模型注册器；
- 不把用户设置持久化语义搬入后端会话；
- 不让 compat 绕过本项目 Operation/Contract 准入。

### 7.2 Hermes Agent：只采纳原子重建思想

可采纳：切换模型时把 provider、base URL、API mode、client、compressor、context 与 system prompt cache 视为一个整体重建，而不是只改一个 model 字符串。

不采纳：Hermes 的自动 fallback/下一轮恢复 primary 与本项目冻结的“无隐式跨 Provider 重发”冲突。Portfolio Agent 仍由用户显式切换、创建新 requestId。

### 7.3 OpenCode：采纳 required structured-output tool 模式

可采纳：

- 以单个合成 `StructuredOutput` Tool 携带 JSON Schema；
- `toolChoice=required` 或强制指定函数；
- 未产生结构化 Tool Call 时形成闭合错误；
- arguments 在进入业务前由 Schema 校验。

不采纳：

- 不引入开放工具注册表；
- 不执行 Tool；
- 不把结构化输出放进多步 Agent Loop；
- 不增加 retry 次数。

## 8. 目标架构与深模块边界

### 8.1 总体链路

```text
Domain Adapter
  -> StructuredOperationRequest(operation, prompt, logicalBudget, deadline)
  -> StructuredOutputGateway.execute(resolvedExecution, request)
       -> require frozen OperationBinding
       -> resolve contractRef only from frozen OperationBinding
       -> ProviderRequestCompiler.compile(approvedProfile, binding, contract)
       -> OpenAiCompatibleHttpTransport.executeExactlyOnce(...)
       -> ProviderResponseExtractor.extract(approvedProfile, binding, envelope)
       -> strict JSON parse (duplicate-key/trailing-token closed)
       -> CanonicalSchemaValidator.validate(contract.schema, parsedTree)
       -> return StructurallyValidatedOutput
  -> Domain Codec.decodeSemantic(validatedOutput, trustedInput)
  -> Domain Validator
```

Adapter 不得传 `contractRef`。Operation 与 Contract 的映射已经冻结在 Snapshot/Binding 中；若 Adapter 再传一份 contractRef，就会形成“请求说 v5、绑定说 v6”的新双权威。Prompt 投影中的 schema 字面量同样退出，Provider 请求由 Gateway 使用冻结合同生成。

### 8.2 模块职责

| 模块 | 负责 | 明确不负责 |
| --- | --- | --- |
| `StructuredOutputContractRegistry` | Canonical Schema、版本、fingerprint、结构边界 | Provider、Prompt、领域上下文语义 |
| `StructuredOutputGateway` | 选择已冻结策略、单次传输、提取、Schema 本地校验 | 业务 route、subject、Evidence |
| `ProviderRequestCompiler` | 把 Canonical Schema 编译为获准 Provider 请求形态 | 动态 fallback、读取前端配置 |
| `ProviderResponseExtractor` | 按 carrier 提取恰好一个 JSON payload | 领域解码、自动修复 |
| `ModelExecutionResolver` | 冻结 model 与全部 OperationBinding | 运行期重新协商策略 |
| Goal/General Codec | 结构通过后的领域构造与上下文语义校验 | 重复实现未知字段/必填字段 Schema |

`StructuredOutputGateway` 是 Domain Adapter 唯一依赖的生产入口。Compiler、Extractor、严格 Parser 与 Schema Validator 默认保持 infrastructure package-private；除非测试替身确有需要，不为每个步骤建立公开接口或 Spring Bean，避免把一个深模块拆成可任意重组的浅模块网络。

### 8.3 新核心类型

```text
StructuredOutputContract
  contractId
  schemaVersion
  schemaDialect
  canonicalSchema
  contractFingerprint
  outputName

StructuredOutputStrategy
  NATIVE_JSON_SCHEMA
  REQUIRED_TOOL_CALL

ApprovedModelExecutionProfile
  profileId/profileVersion
  requiredSelectionVersion
  expectedModelIdentity
  providerProtocolProfile
  operationBindings

OperationBinding
  operation
  contractRef
  strategy
  tokenFieldPolicy
  requestCompilerProfileVersion
  responseExtractorProfileVersion
  bindingFingerprint
```

`OperationBinding` 必须进入 Descriptor fingerprint 与 ModelExecutionSnapshot。响应载体和 finish-reason 规则由 `strategy + responseExtractorProfileVersion` 唯一导出，不允许成为另一项环境配置。任何 strategy、token policy、contract version、compiler/extractor profile 或其导出行为改变都必须提升条目 `selectionVersion`。

## 9. Canonical Contract 单一 wire-shape 权威

### 9.1 权威形式

每个模型输出合同新增一个仓库内、不可网络引用的 Canonical JSON Schema：

```text
backend/src/main/resources/model-contracts/goal.proposal.v5.schema.json
backend/src/main/resources/model-contracts/general.draft.v2.schema.json
```

Schema 必须：

- 使用冻结的 dialect/subset；
- root 与所有 object 默认 `additionalProperties=false`；
- 使用 `oneOf`/`const` 表达闭合 variant；
- 为 string、array、object 层级提供明确上限；
- 不允许远程 `$ref`；
- 不依赖 Provider 未证明支持的 `format` 语义；
- schemaVersion 与资源名、Operation Policy 完全一致；
- 由启动测试加载并计算稳定 fingerprint。

这里的“单一权威”严格指 Provider wire shape。Java 的领域枚举和构造器仍是领域语义权威；Schema 中不可避免的 enum 投影必须由防漂移测试与 Java 闭集精确比对，不宣称通过手写两份文件天然消除了重复。

### 9.2 与现有 Operation 第三重准入合并

Canonical Contract 不新增一条平行于 `ModelOperationPolicyRegistry` 的启停权威。现有链路调整为：

```text
ModelOperationPolicy(mode, schemaVersion, budget, timeout)
  -> StructuredOutputContractRegistry.resolve(operation, schemaVersion)
  -> ApprovedModelExecutionProfile.requireBinding(operation, contractRef)
  -> AgentRuntimeReadiness / ConfiguredModelCatalog admission
```

- `ModelOperationProperties` 继续拥有全局 mode、schemaVersion、预算和 timeout；
- `StructuredOutputContractRegistry` 拥有 schemaVersion 到 Canonical Schema 的闭集解析；
- `AgentRuntimeReadiness` 不再从 `GoalProposalCodec.SCHEMA_VERSION` / `GeneralDraftCodec.SCHEMA_VERSION` 读取结构权威，而是验证 Operation Policy 能被 Registry 精确解析；
- `ConfiguredModelCatalog` 只有在全局 Operation 已启用、Profile 含对应 binding、contractRef 精确一致且编译预检通过时，才派生业务 capability；
- Codec 常量可保留为过渡兼容标识，但迁移完成后不得继续作为启动准入权威。

### 9.3 本地 Schema 校验永远执行

无论 Provider 使用 native JSON Schema、Tool Arguments 还是 JSON Object，提取出的 payload 都必须经过同一 Canonical Schema 本地验证。Provider 侧严格模式只能降低畸形输出概率，不能成为信任边界。

在 Schema validator 之前必须使用与当前安全性等价的严格 Parser：拒绝重复键、尾随 token、非对象 root 和超出 Operation 字节/字符边界的 payload。Validator 与 Codec 共享已经解析且校验通过的不可变 JSON tree，禁止各自使用不同 ObjectMapper 再解析一次。

### 9.4 Codec 收窄

迁移后的 Codec 分工：

- Canonical Schema 负责 JSON 语法后的 shape：root kind、字段集合、required、nullability、基础类型、数组/字符串上限；
- Codec/Validator 负责跨字段与可信输入相关语义：public subject 是否存在、recentReference 是否命中 typed state、recommendation constraints 是否允许、澄清字段与 blocked goal 是否一致等；
- Codec 不再复制已经由 Canonical Schema 保证的未知字段、必填字段和基础类型判断；
- 所有结构失败映射为 `failure.layer=SCHEMA`，所有上下文/领域失败映射为 `failure.layer=SEMANTIC`。

### 9.5 防漂移门

必须建立双向 fixture：

1. 所有合法 Codec fixture 必须通过 Canonical Schema；
2. 所有结构负例必须被 Canonical Schema 拒绝；
3. Schema 通过但语义非法的 fixture 必须只在 semantic 层被拒绝；
4. 编译结果携带的 source contract fingerprint 必须等于本地校验使用的 fingerprint；
5. Prompt 不携带 `schema`/`contractRef` 字面量，也不把 allowed/required/nullability 重新定义为第二份 wire contract；语义、语言、安全、动态 trusted-input 约束以及确有必要的语义示例可以保留，但示例不得扩张或覆盖 Canonical Schema，并需通过 fixture 防漂移。

Canonical fingerprint 通过解析后的规范 JSON 计算：object key 递归排序、数值与字符串按 JSON 规范序列化，忽略源文件换行与缩进，禁止把纯格式变化误判为合同升级。

若 Provider 只支持 Canonical Schema 的子集，Compiler 不得静默删除任何影响验证语义的关键字。`$schema`、`$id`、`description` 等非验证元数据只有在闭集 compiler profile 明确声明、projection manifest 可测试且本地 contract fingerprint 仍被保留时才可投影移除；`oneOf`、`const`、`required`、`additionalProperties`、上下限等验证关键字不得降级或删除。编译失败必须在启动/准入阶段失败关闭；要缩小 wire contract 必须升级合同版本并重新评审。

### 9.6 Schema 失败原因保持闭集

Codec 收窄后不能丢失本次事故建立的安全诊断。Schema validator 的原始 message、instance value 和 Provider payload 均不得进入日志；内部 classifier 只能消费 validator 的 keyword、instance pointer 和 schema pointer，把已批准模式映射为闭集 reason：

- root `/kind` 的 enum/const/oneOf 不匹配可映射为 `UNSUPPORTED_ROOT_KIND`；
- `clarification.blockedGoal` 缺失/null/类型错误可继续映射为 `CLARIFICATION_BLOCKED_GOAL_REQUIRED`；
- 其他结构失败统一为 `LOCAL_SCHEMA_REJECTED`。

Classifier 不解析自然语言异常消息，不记录实际字段值；未识别模式必须落入通用闭集 reason，而不是扩张高基数字符串。

## 10. 结构化输出策略

### 10.1 `NATIVE_JSON_SCHEMA`

请求编译：

```json
{
  "response_format": {
    "type": "json_schema",
    "json_schema": {
      "name": "goal_proposal",
      "strict": true,
      "schema": { "...": "canonical schema" }
    }
  }
}
```

响应要求：

- 恰好一个 choice；
- `message.content` 为恰好一个 JSON 对象；
- 无 Markdown fence、前后文本或第二个 JSON value；
- finish reason 必须属于该闭集 Profile 对本策略批准的完成值，`length`、`content_filter`、未知值或 Provider refusal 一律拒绝；
- 仍经过本地 Canonical Schema 与领域语义校验。

### 10.2 `REQUIRED_TOOL_CALL`

请求只声明一个合成输出 Tool：

```json
{
  "tools": [{
    "type": "function",
    "function": {
      "name": "emit_goal_proposal",
      "description": "Return the final typed goal proposal.",
      "parameters": { "...": "canonical schema" }
    }
  }],
  "tool_choice": {
    "type": "function",
    "function": { "name": "emit_goal_proposal" }
  }
}
```

闭合约束：

- Tool 名由服务端合同固定，不来自配置或用户输入；
- 只允许一个 Tool 定义；
- 若 Provider 支持，发送 `parallel_tool_calls=false`；
- 响应必须恰好一个 tool call、名称完全匹配、arguments 为恰好一个 JSON 对象；
- content 即使存在也不进入业务；
- arguments 是 JSON 字符串还是对象、允许的 finish reason 以及 Tool envelope 路径均由闭集 extractor profile 固定，不从配置 JSONPath 猜测；
- 不执行 Tool，不产生 tool result，不发第二次模型请求；
- 0 个、多个、错误名称、空 arguments 或非法 JSON 一律 fail-closed。

### 10.3 旧 `JSON_OBJECT` 对照路径

现有 `response_format.type=json_object` 在 Slice B 完成前仍是唯一旧生产路径；Slice B 完成后必须退出生产 Catalog、Snapshot 和 Gateway，只允许存在于隔离 canary/fixture runner 中作为历史对照，不加入 `StructuredOutputStrategy` 枚举。

对照路径规则：

- 只能由不进入公开 Catalog 的专用 runner 调用；
- 结果仍必须经过同一严格 Parser、本地 Canonical Schema 与 semantic validator，以便得到可比较的拒绝分布；
- 不得通过 application properties、环境变量或 Feature Flag 恢复为生产 binding；
- 不允许运行时从 native/tool 自动降级到 JSON Object。

### 10.4 无策略 fallback

一个 OperationBinding 在启动时只能冻结一种受约束策略。若该策略请求被 Provider 拒绝、返回错误 envelope 或 Schema 不通过，本 Turn 直接结束为现有 selected-model unavailable/invalid response；不得改用第二策略或旧 JSON Object 再请求一次。

## 11. Provider/Model/Operation 能力建模

### 11.1 业务能力与协议能力分离

`ModelCapability.TURN_INTERPRETATION/GENERAL_KNOWLEDGE` 继续表示应用能力。新增的 structured strategy、carrier、schema compiler、thinking 与 token field policy 属于协议执行能力，不得塞回同一枚举。

### 11.2 能力不再硬编码给所有模型

删除 `ConfiguredModelCatalog.CAPABILITIES` 固定全集。模型的公开业务能力必须从已通过以下门的 OperationBinding 派生：

```text
model runtime enabled
  + provider enabled / credential / data policy
  + closed protocol profile
  + operation globally enabled
  + schema version exact
  + configured execution profile is code-approved
  + profile expected model identity matches configured wire model
  + approved binding exists for operation + contract
  + Canonical Schema compiler success
```

真实 Provider canary 仍是独立证据层。Catalog 只能声明“已配置且获准发送该请求形态”，不得把配置声明成 schema/quality verified。

### 11.3 可选择模型与可生成 Goal 的一致性

当前 `AgentTurnLifecycleService.resolutionContext()` 把 `GoalKind.values()` 全部交给 Goal Interpretation，尚未按所选模型 capability 过滤。一旦新目录允许模型只拥有部分 Operation，如果不修正这里，只有 Goal binding、没有 General binding 的模型仍可能合法地产生 `GENERAL_EXPLANATION`，随后在执行阶段失败。

目标规则：

- 公开 `selectable=true` 的模型必须至少拥有合法 `TURN_INTERPRETATION` binding；只有 General binding、没有 Goal binding 的模型不得出现在可选目录；
- `PORTFOLIO_FACT`、`PORTFOLIO_COMPARE`、`PORTFOLIO_RECOMMEND` 只要求 Turn Interpretation；
- `GENERAL_EXPLANATION`、`GENERAL_COMPARISON`、`APPLY_GENERAL_CONCEPT_TO_PORTFOLIO` 只有在同一 frozen Snapshot 同时支持 `GENERAL_KNOWLEDGE` 时才进入 `allowedGoalKinds`；
- `GoalProposalCodec` 与 `SemanticRouteValidator` 继续校验模型输出只能使用该动态闭集；
- 过滤发生在构造 trusted GoalInterpretationInput 之前，不由 Prompt 自行判断。

### 11.4 Operation 生产准入

第一版建议冻结：

| Operation | 合同 | 生产要求 |
| --- | --- | --- |
| `TURN_INTERPRETATION` | `goal.proposal.v5` | 必须存在 `NATIVE_JSON_SCHEMA` 或 `REQUIRED_TOOL_CALL` binding |
| `GENERAL_KNOWLEDGE` | `general.draft.v2` | 必须存在 `NATIVE_JSON_SCHEMA` 或 `REQUIRED_TOOL_CALL` binding |

第一版不预埋 General 的 `LOCAL_VALIDATED` 例外。未来若确有数据证明需要，必须另立设计，而不是启用本设计中的隐藏开关。

### 11.5 配置建议

```yaml
portfolio:
  model-runtime:
    models:
      qwen-3-7-flash:
        execution-profile: QWEN_3_7_FLASH_STRUCTURED_V2

      glm-4-7-flash:
        execution-profile: GLM_4_7_FLASH_STRUCTURED_V2
```

`execution-profile` 只接受代码注册表中的闭集 ID。Profile 原子拥有 requiredSelectionVersion、protocol、thinking/streaming、按 Operation 的 strategy、contract、token policy、request compiler 与 response extractor 版本；环境配置不能覆盖其中单项。配置中的 endpoint、wire model name、selectionVersion 必须与 Profile 的准入约束精确一致，否则启动失败。这样 strategy/contract 改变而忘记提升 selectionVersion 会在启动期被拒绝，而不是只靠文档提醒。

以上 GLM Profile 名只是待批准目标示例，不是已验证结论。未通过探针前不得把该 Profile 加入生产 Approved Registry；GLM 保持不可选或只由隔离 canary runner 调用，不得凭 YAML 字符串进入生产。

禁止配置任意 header、body fragment、Schema 路径、Tool 名、response JSONPath 或脚本。新增策略/Profile 仍需代码、fixture、真实证据与评审。

## 12. Qwen 与 GLM 的目标策略

### 12.1 Qwen3.7-Flash

推荐首选 `NATIVE_JSON_SCHEMA`，理由：官方当前文档明确列出 Qwen3.7-Flash JSON Schema 支持；本项目的失败正发生在 JSON Object 不约束 root kind。

进入生产前必须证明：

- 精确 model ID `qwen3.7-flash` 与实际 endpoint 接受 strict JSON Schema；
- 非思考模式参数与 JSON Schema 同时可用；
- Canonical Schema 全部使用的关键字被接受，没有静默忽略；
- response envelope 仍是唯一 `message.content`；
- 不设置 `max_tokens` 时 deadline、响应字节上限和 Schema 内容上限足以控制风险；
- 直接推荐、合法澄清、recent reference、discussion 与 General 均通过。

若 native JSON Schema canary 失败，可以另开证据分支验证 `REQUIRED_TOOL_CALL`。两者不能在同一生产 Turn 内 fallback；最终只能冻结一种策略并提升 selectionVersion。

### 12.2 GLM-4.7-Flash

GLM 的最终策略保持待证据状态。隔离探针顺序：

1. native JSON Schema 请求是否被精确 endpoint 接受；
2. 若不接受，强制单 Tool Call 是否可用；
3. tool envelope、arguments、thinking disabled 与 non-streaming 是否兼容；
4. 两者均不满足时，GLM 不获得相应 Operation 的生产业务能力；
5. JSON Object 仅作为本地验证对照，不自动成为生产 fallback。

GLM 的 429/服务繁忙只影响外部可用性证据，不允许通过放宽结构策略“修复”。

## 13. Request、Response 与错误合同

### 13.1 Request

`StructuredModelRequest` 替换为 Operation 语义请求，不再假设所有输出都是 content JSON，也不允许 Adapter 指定合同：

```text
operation: ModelOperation
systemPrompt
userPrompt
logicalOutputBudget
temperature
deadline
```

具体 contract、strategy、token field name/presence、Tool 名和响应提取规则必须从 frozen OperationBinding/ExecutionProfile 取得，Adapter 不传 Provider 参数或 contractRef。

### 13.2 Response

Transport/Extractor 返回规范化 `ExtractedStructuredPayload`：

```text
contractRef
strategy
parsedJsonTree
```

该对象不得持久化或记录原文；通过 Schema 后转为 `StructurallyValidatedOutput`。未经严格 Parser 与本地 Schema 验证的 payload 类型不得暴露给领域 Adapter；Goal/General Codec 消费同一个校验后 tree，不再次解析原始字符串。

### 13.3 失败层

内部诊断闭集建议为：

```text
REQUEST_COMPILE
TRANSPORT
RESPONSE_ENVELOPE
SCHEMA
SEMANTIC
```

至少增加安全原因：

```text
STRATEGY_NOT_AVAILABLE
CONTRACT_NOT_AVAILABLE
SCHEMA_COMPILATION_REJECTED
EXPECTED_MESSAGE_CONTENT
EXPECTED_SINGLE_TOOL_CALL
TOOL_NAME_MISMATCH
TOOL_ARGUMENTS_INVALID_JSON
LOCAL_SCHEMA_REJECTED
OUTPUT_SEMANTIC_REJECTED
```

公开错误合同继续使用现有 `SELECTED_MODEL_*`，本设计不扩张前端 error variant。内部 reason 不得包含 Provider body、字段值或异常原文。

失败与 `attempted` 语义必须精确冻结：

- Contract 缺失、Profile/Binding 不合法、Schema compiler 不支持等属于启动或 Catalog 准入失败，正常运行的 Turn 不应遇到；若选择因此不可执行，映射为调用前 `SELECTED_MODEL_UNAVAILABLE`，`attempted=false`；
- deadline 在调用前耗尽保持现有 temporarily unavailable、`attempted=false`；
- HTTP 已发出后的 Transport/429/5xx 沿用现有映射，`attempted=true`；
- HTTP 2xx 后的 finish reason、envelope、Tool 名、JSON parse、Canonical Schema 或 semantic 拒绝统一公开为 `SELECTED_MODEL_INVALID_RESPONSE`，`attempted=true`，内部 layer/reason 保持区分；
- `SCHEMA_COMPILATION_REJECTED` 不得被误报成“模型返回无效”，更不得在每个 Turn 临时编译后才发现。

## 14. 模型切换的原子语义

### 14.1 保留现有 Turn 级显式切换

切换仍由前端在终局后选择新模型，并以新 requestId 创建新 Turn。后端不提供“切换当前进行中的 Provider”能力。

### 14.2 Snapshot 扩展

新的 `ModelExecutionSnapshot` 对 MODEL 形态至少冻结：

```text
modelRef
selectionVersion
descriptorFingerprint
protocolProfileVersion
operationBindings {
  operation -> contractRef + strategy + tokenPolicy
               + requestCompilerProfileVersion
               + responseExtractorProfileVersion
               + bindingFingerprint
}
context/output budgets
```

Credential 与 endpoint 继续只存在服务端 Binding；公开投影不显示 strategy、Schema 或 endpoint。

### 14.3 并发不变量

- Qwen Turn 与 GLM Turn 并行时，各自只读取自己的 frozen binding；
- 不允许全局 `currentStrategy`、可变 Profile Bean、ThreadLocal Provider 或共享 payload template；
- 切换模型后，旧 Turn 的 callback 不能改变新 Turn 的策略或 tracker；
- Snapshot 与服务端 Binding 必须共享同一 `modelRef + descriptorFingerprint + operationBindingFingerprint`；当前只比较 modelRef 的 `ResolvedModelExecution` 必须补齐该一致性校验，否则调用前拒绝。

### 14.4 selectionVersion

以下变化必须提升条目 selectionVersion：

- output strategy；
- protocol/request-compiler/response-extractor profile version；
- token field policy；
- Canonical Contract version；
- thinking/streaming 语义；
- 实际 model ID 或 Provider 身份。

Key 轮换、显示名和短期限流仍不提升 selectionVersion。

## 15. `max_tokens` 与有界性

当前 Transport 无条件发送 `max_tokens`；Qwen 官方结构化输出文档警告该字段可能截断 JSON。不能把这一点简单改成“所有 Provider 都不发”，也不能忽略成本与输出上限。

新增闭合 `TokenFieldPolicy`：

```text
OMIT_FOR_STRUCTURED_OUTPUT
SEND_MAX_TOKENS
SEND_MAX_COMPLETION_TOKENS
```

策略由 model/profile/operation binding 冻结，不由 Prompt 或运行期猜测。Qwen native JSON Schema 第一候选为 `OMIT_FOR_STRUCTURED_OUTPUT`；其他值必须由官方文档与真实 canary 证明。

即使不发送 token 字段，仍必须保持：

- Canonical Schema 的 maxLength/maxItems/maxProperties；
- 256 KiB 响应硬上限或更严格 Operation 上限；
- Turn/Operation deadline；
- 单 choice、单 payload；
- Prompt 和输入窗口预算；
- Provider 账单与 usage 的低基数监控，不记录正文。

若成本评审认为省略 token 字段不可接受，应优先选 Tool Strategy 或另一经过证明的模型，而不是恢复不可靠 JSON Object 并宣称问题解决。

## 16. 可观测性、隐私与安全

允许记录：

- operation；
- modelRef；
- strategy；
- contractId/schemaVersion；
- carrier；
- failure.layer/code/reason；
- duration bucket、response size bucket；
- requestId/conversationId 既有安全标识。

事件顺序保持可解释：HTTP 2xx 后先发布 `provider.call.completed`；Gateway 的 envelope/parser/schema 拒绝再发布 `provider.output.rejected`；Domain semantic 拒绝由现有 ModelOutputDiagnostics 发布。一次失败只由其责任层发布一个 rejection reason，不在 Adapter 与 Gateway 重复计数。

禁止记录：

- Canonical Schema 全文；
- Prompt、用户原文、ConversationWindow；
- message.content、tool arguments、Provider body；
- Tool call id、Provider request id（除非另经安全评审）；
- API Key、Authorization、endpoint；
- Java exception message 中的模型字段值。

`REQUIRED_TOOL_CALL` 的 Tool 只是一种 response carrier，不得接入真实 Tool executor、权限系统或外部副作用。

## 17. Replacement Manifest

### 17.1 新权威

| 概念 | 新权威 |
| --- | --- |
| 输出结构 | `StructuredOutputContractRegistry` + Canonical Schema resources |
| Operation 启停/预算/合同选择 | 现有 `ModelOperationPolicyRegistry` + Contract Registry 精确解析 |
| 获准模型协议组合 | code-owned `ApprovedModelExecutionProfileRegistry` |
| Operation 结构策略 | frozen `OperationBinding` |
| Provider 请求形态 | `ProviderRequestCompiler` + closed Profile/Strategy |
| Provider 响应提取 | `ProviderResponseExtractor` |
| 本地结构校验 | `CanonicalSchemaValidator` |
| 领域语义 | 收窄后的 Goal/General Codec + Validator |

### 17.2 迁移调用方

- `GoalInterpretationAdapter`；
- `OpenAiCompatibleGeneralKnowledgeAdapter`；
- `AgentCapabilityConfiguration`；
- `ModelOperationProperties` / `ModelOperationPolicyRegistry`；
- `AgentRuntimeReadiness`；
- `ConfiguredModelCatalog`；
- `ModelExecutionResolver` / `ResolvedModelExecution`；
- `ModelExecutionSnapshot` / `ModelTransportBinding`；
- `AgentTurnLifecycleService.resolutionContext()` 的 model-capability GoalKind 过滤；
- Provider protocol/transport tests；
- live canary、quality runner、packaged-JAR runner；
- application configuration、`.env.example` 与本地启动文档；
- docs/08、docs/15、架构机器状态与演进日志。

### 17.3 同期删除

- `ModelRuntimeProperties.ModelSettings.structuredOutput` 的单值 `JSON_OBJECT` 语义；
- 可由环境分别覆盖的 `protocolProfile/structuredOutput/thinkingMode/streaming` 组合，替换为单个闭集 `executionProfile` 引用；
- `ConfiguredModelCatalog.requireExact(... JSON_OBJECT ...)`；
- `ModelProviderProtocolProfile.common()` 中无条件注入 JSON Object；
- `ModelProviderRequestFeature.JSON_OBJECT_REQUEST` 作为统一能力声明；
- `ConfiguredModelCatalog.CAPABILITIES` 固定全集；
- `StructuredModelTransport` 只返回 `.json()` content 的旧合同；
- Transport 无条件 `max_tokens`；
- `AgentRuntimeReadiness` 对 Codec `SCHEMA_VERSION` 常量的启动权威依赖；
- Goal Adapter 请求投影中的 `schema` 字面量；
- Codec 中与 Canonical Schema 重复的纯结构字段检查；
- `GoalProposalDecodeException` 中纯结构 reason 的判定位置迁移到安全 SchemaFailureClassifier；reason 名可以保留，不能靠 Codec 二次解析维持；
- Prompt 内复制 wire shape 的重复权威；语义、语言、安全、动态 trusted-input 约束必须保留；
- 只证明 JSON Object payload 的过期 fixture。

不得保留 `legacy-json-object-enabled`、双 Transport Bean、旧/new request 双字段或运行时策略 fallback 开关。

## 18. Replacement Slices

### 18.1 Slice A：Canonical Contract 与本地结构权威

固定顺序：

1. 增加 Goal/General Canonical Schema 与 Registry；
2. 增加严格 Parser 与本地 Schema validator，禁止 remote ref，保留重复键/尾随 token 拒绝；
3. 建立合法/结构非法/语义非法三层 fixtures；
4. 让现有 `ModelOperationPolicy/AgentRuntimeReadiness` 通过 Registry 解析合同，删除 Codec 常量的启动权威；
5. Adapter 在现有 Provider content 返回后先通过严格 Parser 与 Canonical Schema；
6. 收窄 Codec，改为消费已校验 tree，删除重复纯结构检查与二次解析；
7. 保留旧 JSON Object 路径当前所需的 Prompt wire-shape 描述，但用 fixture 明确标记为 Slice B 必删的迁移重复；不得在 Provider 约束接管前先删；
8. 运行结构/语义、全量、privacy、architecture、documentation 门；
9. 证明 Canonical Schema 已成为本地结构校验权威，旧 Prompt 复制只承担迁移期模型提示、不承担服务端准入。

Slice A 不改变 Provider 请求策略，不能据此关闭 Qwen 可靠性问题；它只为 Slice B 建立单一合同基础。

### 18.2 Slice B：Operation-aware Strategy 与原子绑定

固定顺序：

1. 在隔离 fixture 中实现两种生产 strategy compiler/extractor；旧 JSON Object 仅留 canary 对照；
2. 增加 code-owned Approved Execution Profile Registry；环境配置只引用闭集 Profile；
3. 扩展 Descriptor/Binding/Snapshot/Resolver，冻结并交叉校验 OperationBinding fingerprint；
4. 按 Snapshot capabilities 过滤 trusted `allowedGoalKinds`；
5. 用新的 `StructuredOutputGateway` 替换生产 Transport 合同并原子迁移 Goal/General Adapter；
6. 接入 Qwen 已验证策略；
7. 接入 GLM 已验证策略，或明确不授予未验证 Operation 能力；
8. 在受约束 Provider 请求成为唯一生产路径的同一 Replacement Slice 内，删除 Prompt wire-shape/schema 字面量、旧 JSON Object 单策略、旧 response `.json()`、自由组合协议配置与固定 capabilities；
9. 运行零引用、并发切换、全量与 packaged-JAR 门；
10. 经单独授权运行真实 Qwen/GLM canary 和矩阵；
11. 更新 selectionVersion、当前权威与机器状态。

Slice B 失败时不得保留双生产路径；使用 Git/JAR 整体版本回退。

## 19. 测试与验证

### 19.1 Contract

- Schema 资源存在、版本与文件名一致、无 remote ref；
- root variant、required、unknown fields、nullability、array/string bounds；
- duplicate key、trailing token、非对象 root 与 canonical JSON fingerprint 稳定性；
- 合法 fixture 全通过；
- `UNSUPPORTED_ROOT_KIND` 在本地 Schema 层稳定拒绝；
- root kind 与 blockedGoal 两个已批准 reason 由 keyword/pointer 安全分类，未知 schema 错误归入 `LOCAL_SCHEMA_REJECTED`，诊断不含实际值；
- Schema 通过的 recentReference 无状态样本在 semantic 层拒绝；
- Schema fingerprint 稳定且变更可检测；
- Java 闭集 enum 与 Schema enum 投影一致。

### 19.2 Provider request fixtures

Qwen/GLM × Goal/General 分别断言：

- model、messages、thinking、stream；
- native JSON Schema 完整 payload；
- required tool 的 name/parameters/tool_choice；
- parallel tool 规则；
- token field policy；
- executionProfile 不能被环境变量按字段拆开覆盖；wire model/profile 不匹配时启动失败；
- compiler 只执行 projection manifest 允许的非语义变换，验证关键字零删除；
- 不出现另一 Provider 私有字段；
- 不出现任意配置 body fragment。

### 19.3 Response extractor

- native：单 content 成功；空、多 choice、Markdown、trailing JSON、重复键、`length`/refusal/未知 finish reason 失败；
- tool：恰好一个匹配 call 成功；0/多 call、错误名、空/非法 arguments、错误 arguments 编码、`length`/refusal 失败；
- content + tool 并存时只按 frozen strategy/extractor profile 读取，另一载荷不进入业务；
- 256 KiB + 1、body stall、timeout、cancel 仍失败关闭；
- diagnostics 不含 response sentinel。

### 19.4 Catalog 与切换

- 不同 model 可绑定不同 strategy；
- 同一 model 的 Goal/General 可独立绑定但必须在同一 Snapshot 冻结；
- 未获批准的 execution profile 或缺失 OperationBinding 时能力不进入目录；
- 没有 Turn Interpretation binding 的模型不得公开 selectable；
- 没有 General binding 的模型仍可处理 Portfolio Goal，但三个 General/跨域 GoalKind 不进入 trusted allowedGoalKinds；
- strategy/contract/token policy 变化要求 selectionVersion 更新；
- Qwen Turn 后 GLM Turn 使用新 requestId 与新完整 binding；
- GLM 失败不调用 Qwen，反向同理；
- 并行 Qwen/GLM Turn 不串 profile、schema、tool name 或 token policy；
- replay 使用原终局，不按当前 Catalog 重跑 Provider。

### 19.5 真实 Provider Gate

真实调用必须单独授权并对每个 model/strategy 独立报告：

- request accepted rate；
- response carrier correct rate；
- local schema rejection rate；
- semantic rejection rate；
- timeout/rate-limit/5xx；
- P50/P95；
- 用户可见终局分布。

第一阶段结构 canary 至少覆盖：

```text
直接推荐两个项目
合法部分目标澄清
有 typed state 的 recent reference
无 typed state 的 recent reference
discussion facet
General CONCISE / STANDARD / DETAILED
```

低信息 `1` 应继续证明 Provider 调用为 0，不纳入 Provider 结构成功率。

在实现计划冻结具体样本量与阈值前不得运行付费矩阵；一次通过不能把 Provider 标为稳定。Qwen 与 GLM 必须分别得到独立 Gate，不互相替代。

### 19.6 自审风险登记

| 风险 | 触发方式 | 本设计的处理 |
| --- | --- | --- |
| 新增 contractRef 双权威 | Adapter 与 Snapshot 各指定一次合同 | Request 不携带 contractRef，只从 frozen binding 解析 |
| 绕开现有第三重准入 | Contract Registry 自己管理 enable/schema | 保留 ModelOperationPolicy，Registry 只解析 wire contract |
| 环境拼装未经批准协议 | 分别配置 strategy/carrier/token/tool | 只配置 code-owned executionProfile ID，内部字段不可覆盖 |
| 部分能力模型产生不可执行 Goal | 有 Goal binding、无 General binding仍允许 General GoalKind | trusted allowedGoalKinds 按同一 Snapshot capability 收缩 |
| Provider 切换只换 modelRef | Snapshot 与服务端 Binding 只比较 modelRef | 同时校验 descriptor 与每个 operation binding fingerprint |
| Slice A 先删 Prompt 导致旧 JSON Object 退化 | Provider 约束尚未接管 | Prompt wire-shape 到 Slice B 原子切换时才删除 |
| Schema validator 降低现有 Parser 安全性 | 重复键/尾随 token 在 tree 化时丢失 | 先严格单次解析，再校验并把同一 tree 交给 Codec |
| 收窄 Codec 后诊断退化 | 丢失 `UNSUPPORTED_ROOT_KIND` | 以 keyword/pointer 安全分类，不解析异常正文或记录值 |
| `max_tokens` 省略导致成本扩大 | Provider 使用过大默认输出 | 字节/deadline/schema 上限 + usage/cost canary；不达门则不准入 |
| 类和 Bean 数量膨胀 | 每一步都公开接口并可组合 | Adapter 只依赖 Gateway，其余默认 package-private 深模块成员 |
| Provider Schema 子集不兼容 | compiler 删除关键约束求通过 | 验证关键字零删除；编译/真实 canary 失败则该 binding 不存在 |

## 20. Exit Gates

只有以下全部满足，才能声明本设计完成：

1. Canonical Schema 成为 Goal/General 唯一 Provider wire-shape 权威；
2. 现有 Operation Policy 通过 Contract Registry 精确解析，不存在平行启停/schema 权威；
3. Provider 请求投影与本地校验使用同一 source contract fingerprint；
4. 严格 Parser 保留重复键、尾随 token、root 与大小边界；Codec 消费同一校验后 tree；
5. Goal/General Codec 已收窄为结构后领域构造与语义职责；
6. 每个可选模型的每个业务能力来自代码批准的 OperationBinding，而非固定全集或环境自由组合；
7. selectable 模型必有 Goal binding，General GoalKind 与同一 Snapshot 的 General capability 一致；
8. Snapshot 冻结 strategy/token policy/contract/compiler/extractor profile 与 binding fingerprint；
9. Snapshot/服务端 Binding 的 descriptor 与 operation fingerprint 强一致；
10. 模型切换原子更换完整 binding，新 requestId 语义不变；
11. 无 repair、retry、runtime strategy fallback 或跨 Provider fallback；
12. Qwen 原始失败路径在批准策略下通过，且不再依赖 Prompt 猜 root kind；
13. GLM 只获得真实证明过的 Operation 能力；未证明则诚实不可用；
14. 旧 JSON Object 单策略、固定 capabilities、旧 raw content response 合同与自由组合协议配置零生产引用；
15. Backend 全量测试、code-quality、architecture、privacy、documentation 通过；
16. PostgreSQL/幂等/replay 不受影响；
17. packaged-JAR 与 Browser 模型切换场景通过；
18. 经授权的两家 Provider 独立 canary/matrix 达到实施计划阈值；
19. docs/08、docs/15、docs/11、docs/00、机器状态与环境配置反映真实结果；
20. 存在 BLOCKED 外部门时整体保持 `IN_PROGRESS`，不得用离线 fixture 代替。

## 21. 回退与发布

- 回退只使用 Git commit、已验证 JAR 或整体部署版本；
- 不保留旧 JSON Object 生产链作为开关；
- selectionVersion 随策略变化升级，旧前端选择收到既有 stale 语义；
- 当前没有长期模型偏好迁移；页面刷新仍取当前 Catalog 默认；
- Settlement/replay 不重新调用 Provider，因此回退后历史已结算 Turn 继续按既有安全回放合同处理；
- 若真实 Provider 在发布后改变协议，先把对应模型/Operation 置为不可用，再以新 Profile/selectionVersion 走独立评审，不运行时猜测兼容方式。

## 22. 治理义务

本文进入评审期前已完成：

1. 以 `DRAFT` 加入 `scripts/documentation-check.ps1` active work artifact；
2. 在 docs/15 登记独立 Provider 协议能力条目 A2-117，把事实与方案分开；
3. 不修改 docs/00 当前权威索引；
4. 不创建实施计划，不修改生产配置。

用户已于 2026-08-25 明确批准设计、实施以及 Qwen/GLM 真实 Provider 校验。批准后治理动作：

1. [x] `DRAFT -> APPROVED`；
2. [x] checker 状态同步；
3. [x] docs/00 收录；
4. [x] 创建独立 Level 3 实施计划，冻结具体依赖、fixture、样本量、阈值与提交顺序；
5. [x] 真实 Provider 调用已获本次实施范围内的明确授权；调用仍必须使用仓库外 secret 文件，且不得输出凭据、Prompt 或模型原始响应。

## 23. 独立评审问题

请评审者重点判断：

1. 生产 `StructuredOutputStrategy` 是否应严格只含 native schema 与 required tool，旧 JSON Object 是否已被彻底限制在隔离 runner；
2. Canonical Schema、现有 Operation Policy 与 Codec 的 wire-shape/启停/语义分界是否足够清晰，是否仍有双权威；
3. Qwen 首选 native JSON Schema 是否有官方 + exact endpoint canary 双证据；
4. GLM 在未证明 strict strategy 前是否会被错误公开为可用；
5. required Tool 是否被严格限制为“输出载体”，是否存在误接真实 Tool executor 的风险；
6. code-owned Execution Profile 是否阻止环境变量自由拼装未批准 strategy/envelope/token 组合；
7. Snapshot 与服务端 Binding 是否冻结并交叉校验切换模型时必须原子变化的全部协议字段；
8. 没有 General binding 时 trusted allowedGoalKinds 是否同步收缩，避免合法 Goal 在执行阶段才失败；
9. token field policy 是否同时处理 JSON 截断、成本和 deadline；
10. Provider compiler 是否可能静默删除 Canonical Schema 验证关键字；
11. 是否仍存在 runtime strategy fallback、双 Transport 或旧 JSON Object 兼容桥；
12. selectionVersion 与 descriptor/binding fingerprint 是否覆盖 strategy/contract/compiler/extractor/token policy；
13. 真实矩阵是否对两家模型独立报告，而不是把 Qwen 成功替代 GLM 或反之；
14. Replacement Slice 是否能在每一阶段保持一个生产权威并整体回退。

## 24. 待用户拍板的设计选择

本文推荐默认值如下：

1. Goal 与 General 的生产 binding 均必须使用 Provider 约束策略，不设计 `LOCAL_VALIDATED` 生产例外；
2. Qwen3.7-Flash 优先验证 `NATIVE_JSON_SCHEMA`，失败后另行验证 `REQUIRED_TOOL_CALL`；
3. GLM 不预设最终策略，以隔离探针裁决；
4. 旧 JSON Object 仅存在于隔离 canary/对照 runner，不进入生产策略枚举或 Catalog；
5. required Tool 只执行一次 Provider 调用，不执行 Tool、不做第二轮；
6. Canonical Schema 使用仓库资源 + 严格 Parser + 本地 validator，Codec 消费校验后 tree 并收窄为领域构造/语义层；
7. 现有 Operation Policy 继续拥有启停/预算/schemaVersion，Contract Registry 解析 wire contract，不新增平行权威；
8. 模型协议组合由 code-owned Execution Profile 批准，环境只引用 Profile；
9. selectable 模型必须有 Goal binding；General GoalKind 与同一 Snapshot 的 General binding 同步收缩；
10. 本设计独立于 `goal.proposal.v6`，不借机修改领域合同。

若以上任一项被修改，应在批准前同步更新 Replacement Manifest、风险、测试和 Exit Gate，不能把未决定项留给实现者现场判断。
