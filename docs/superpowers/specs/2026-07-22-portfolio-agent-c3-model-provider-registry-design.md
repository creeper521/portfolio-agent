# Portfolio Agent C3 内置 Model Provider Registry 设计与 ADR

> 状态：已实施并验证
> 日期：2026-07-22
> 范围：仅 Model Provider Registry
> 不在范围：Tool Registry、通用 Hook、Orchestrator、多 Agent、DurableTask、持久会话、动态插件、自动故障转移

## 1. 决策摘要

Portfolio Agent 在 C1 已拥有两个经过真实 API 调用验证的 Provider：DeepSeek V4 Flash 与 GLM-4.7。部署必须显式选择其中一个 Provider；当前 endpoint、model name 与密钥选择分别存在条件分支。该运行证据满足 C3 正式设计中 Model Provider Registry 的准入条件。

本 ADR 决定引入一个只包含两个内置 Provider 的不可变 `ModelProviderRegistrySnapshot`。Registry 负责描述、校验和选择已配置实现，不负责动态发现、运行时安装、自动重试或跨 Provider 故障转移。每个进程和每轮执行仍只绑定一个 Provider。

其他 C3 能力没有满足各自准入条件，继续保持未实现状态。

## 2. 背景与准入证据

权威 C 设计要求：Model Provider Registry 至少需要两个真实 Provider，并存在部署时选择或故障隔离需求；同时必须具备运行证据、稳定类型化契约、失败/超时/取消语义、可审计权限和 Benchmark，且需要说明固定 Pipeline 已不足。

当前证据如下：

- DeepSeek V4 Flash 与 GLM-4.7 已通过 packaged JAR 真实调用，均能产生通过完整 Draft 校验的 `MODEL` 回答。
- 部署通过 `PORTFOLIO_MODEL_PROVIDER` 显式选择一个 Provider，禁止跨 Provider 自动重发。
- 两个 Provider 共用稳定的 `ModelExpressionPort`、`ModelExpressionRequest`、`ModelExpressionResult` 与完整 Draft Validator。
- Provider 错误、超时、非法 JSON、未知字段和 Draft 拒绝均具有类型化失败语义，并整轮回到同一 `AnswerPlan` 的 `FALLBACK`。
- 当前实现已在密钥选择、endpoint 选择和 model name 选择处重复 Provider 分支；Provider 元数据没有形成可独立校验的不可变快照。
- 完整发布门禁、真实 Provider 延迟记录和 C1 Conformance 测试提供当前 Benchmark 与运行证据。

因此固定单 Adapter 内的 Provider 条件分支已不足以明确表达版本、能力、Policy 兼容性和 fail-closed 选择规则，但尚不足以支持通用插件系统。

## 3. 目标与非目标

### 3.1 目标

- 用类型化、不可变 Descriptor 描述两个内置 Provider。
- 在应用启动时一次性校验 ID、版本、能力和 Policy 兼容性。
- 由 `ModelPolicy` 显式绑定唯一 Provider。
- 消除 HTTP Adapter 内部的 Provider 枚举条件分支。
- 将 Registry Snapshot 版本固定到 `AgentExecutionSnapshot`。
- 保持现有隐私、验证、单次调用和整轮 fallback 语义不变。

### 3.2 非目标

- 不动态扫描 classpath、目录、网络或配置中心。
- 不允许运行时注册、删除、替换或下载 Provider。
- 不引入 Spring AI 或 Provider SDK Registry。
- 不自动重试，不在 Provider 之间故障转移。
- 不将密钥、base URL 覆盖值或私人配置放入 Descriptor、公开 Bundle 或响应。
- 不建设 Tool Registry、通用 Hook、Orchestrator、多 Agent、DurableTask 或持久会话。

## 4. 架构

```text
受审配置
→ 两个内置 ModelProviderDescriptor
→ 冲突、版本与能力校验
→ ModelProviderRegistrySnapshot
→ ModelPolicy 显式选择
→ ModelProviderAdapterFactory
→ 唯一 ModelExpressionPort
→ MODEL 或同 Plan 整轮 FALLBACK
```

Registry 在 Spring 容器创建期间构建，构造后不可变。在途请求只读取当前 Snapshot；第一版不支持 Registry 热更新。

## 5. 类型化组件

### 5.1 ModelProviderDescriptor

普通不可变类，至少包含：

- `providerId`
- `adapterVersion`
- `endpoint`
- `modelName`
- `supportedModelPolicyRange`
- `supportedAnswerSchemaVersions`
- `capabilities`

第一版 capability 使用封闭类型表达结构化 JSON 输出、thinking 控制和非流式调用要求。Descriptor 不包含 API Key、Prompt、请求正文、响应正文或可变运行状态。

Descriptor 必须拒绝空 ID、空版本、非 HTTPS endpoint、空 model name、空兼容范围和未知 capability。它不得实现会输出全部字段的敏感 `toString()`；即使当前字段非敏感，也避免未来误扩展造成日志泄露。

### 5.2 ModelProviderRegistrySnapshot

构造函数接收完整 Descriptor 集合，防御性复制并完成全部校验：

- 同 ID 不允许出现两个 Descriptor。
- 同 ID 的实现或版本冲突必须启动失败，不能 first-wins。
- Snapshot version 必须稳定、非空并进入执行快照。
- 查找未知 Provider 时返回明确的未绑定结果，不选择默认替代项。
- 构造后不提供 register、remove、replace 或 mutable collection。

### 5.3 ModelProviderAdapterFactory

Factory 接收已经由 Registry 校验并由 Policy 选择的 Descriptor，以及所选 Provider 的运行时密钥。它创建唯一 `ModelExpressionPort`。

DeepSeek 与 GLM 第一版继续共享 OpenAI-compatible HTTP Adapter。Adapter 从 Descriptor 获取 endpoint、model name 和 capability，不再读取 `ModelProviderKind` 或包含 Provider 分支。

密钥只在配置边界选择并注入 Adapter，不进入 Descriptor 或 Registry Snapshot。Factory 不允许读取非选中 Provider 的密钥。

### 5.4 ModelExpressionConfiguration

配置层负责：

1. 构建两个内置 Descriptor。
2. 构建不可变 Registry Snapshot。
3. 根据配置构建 `ModelPolicy`。
4. 校验 Policy 选择与 Descriptor 的版本、Schema 和 capability 兼容性。
5. 通过 Factory 创建唯一 `ModelExpressionPort`。

模型默认关闭。Registry 可以在模型关闭时完成结构校验，但不会因此发起网络调用。

## 6. Policy 与 Snapshot 绑定

`ModelPolicy` 继续保存显式选择的 `ModelProviderKind`，不接受任意字符串或请求级 Provider 覆盖。Policy 必须与 Registry 中唯一兼容 Descriptor 绑定。

`AgentExecutionSnapshot` 增加非空 `registrySnapshotVersion`。每轮开始时固定 Content、Model、Retrieval 与 Registry 版本；在途请求不得重新查询或切换 Registry。Snapshot 不复制 Descriptor、endpoint 或密钥。

第一版 Registry version 使用受审常量，并在 Descriptor 集合、adapter version 或兼容性声明变化时显式升级。

## 7. 失败语义

| 场景 | 行为 |
| --- | --- |
| Descriptor 为空或非法 | 应用启动失败 |
| Provider ID 或版本冲突 | 应用启动失败 |
| Policy 选择未知 Provider | 模型能力 fail-closed，不选择其他 Provider |
| Policy/Schema/capability 不兼容 | 模型能力 fail-closed |
| 所选 Provider 密钥缺失 | 保持 `DETERMINISTIC` |
| 外部数据策略未批准 | 保持 `DETERMINISTIC` |
| HTTP、超时、解析或 Draft 校验失败 | 同一 `AnswerPlan` 整轮 `FALLBACK` |
| DeepSeek 失败 | 不调用 GLM |
| GLM 失败 | 不调用 DeepSeek |

Registry 配置冲突属于部署错误并阻止启动；可选模型能力的运行时不可用不能破坏 ContentBundle readiness 或确定性回答路径。

## 8. 隐私与安全

- Registry、Descriptor 和 Snapshot 不包含密钥字段。
- API Key 继续只从项目专用环境变量读取。
- 不记录 Descriptor 全量、密钥、Prompt、AnswerPlan、Provider 请求或响应。
- Provider 仍只接收白名单公开 `AnswerPlan`，不接收访客问题、会话、稳定引用之外的上下文、`turnId` 或 `requestId`。
- endpoint 必须是内置 HTTPS 常量，第一版不允许请求或公开 Bundle 覆盖。
- Registry 不能扩大 ModelPolicy、VerificationPolicy 或 Content Snapshot 权限。

## 9. 兼容与迁移

- `PORTFOLIO_MODEL_PROVIDER`、两个项目专用 API Key 环境变量和现有 Provider 枚举值不变。
- Answer API、四维回答契约、结构化 sections 与前端映射不变。
- 默认 Provider 仍是 DeepSeek V4 Flash，模型能力仍默认关闭。
- 现有部署配置无需迁移。
- endpoint 和 model name 从 Adapter 条件分支迁移到内置 Descriptor，不改变实际请求线路。
- ContentBundle、RAG Bundle、ToolPlan、ContextEnvelope 和页面内存会话不受影响。

## 10. 测试策略

测试必须按 TDD 推进，并覆盖：

1. Registry 正常包含且只包含两个内置 Provider。
2. 重复 ID、空版本、非法 endpoint、空 model name 和未知 capability 构建失败。
3. Registry Snapshot 构造后不可变。
4. Policy 只能绑定存在且兼容的 Provider。
5. DeepSeek 与 GLM 分别选择正确 endpoint、model 和密钥。
6. Factory 不能读取或回退到非选中 Provider 密钥。
7. HTTP Adapter 不再包含 `ModelProviderKind` 分支。
8. 不自动重试、不跨 Provider 重发。
9. Descriptor、Registry 与执行快照不含密钥字段或敏感字符串输出。
10. 现有非法 JSON、未知字段、虚构 ID、禁止内容、超时和整轮 fallback 测试保持通过。
11. `AgentExecutionSnapshot` 固定 Registry Snapshot 版本。
12. 架构、隐私、package 和 packaged-JAR Playwright 门禁通过。

真实 DeepSeek/GLM 冒烟测试继续作为受控人工部署验证，不进入默认离线测试套件，也不在日志中输出模型正文。

## 11. 实施顺序

1. 为 Descriptor、Registry 冲突、不可变性和 Policy 绑定增加失败测试。
2. 实现 `ModelProviderDescriptor` 与 `ModelProviderRegistrySnapshot`。
3. 为 Factory 选择、密钥隔离和兼容性增加失败测试。
4. 实现 `ModelProviderAdapterFactory`。
5. 将 endpoint、model name 与 capability 从 Adapter 分支迁入 Descriptor。
6. 将 Registry Snapshot version 加入 `AgentExecutionSnapshot` 与对应测试。
7. 更新配置、文档状态、README、安全说明和隐私/架构扫描规则。
8. 运行完整发布验证和两个受控真实 Provider 冒烟测试。

## 12. 回滚边界

回滚只涉及 Registry、Descriptor、Factory、配置装配和执行快照版本字段。可以恢复为当前固定 Adapter 选择逻辑，不需要回滚 ContentBundle、RAG Bundle、前端会话或公开 API。

模型能力仍可通过 `PORTFOLIO_MODEL_ENABLED=false` 独立关闭。任何 Registry 回滚都不得引入跨 Provider 自动重发或改变确定性 fallback。

## 13. 后续准入

本 ADR 不为其他 C3 能力提供自动授权：

- Tool Registry 仍需多组独立 Tool Provider 及重复权限/注册代码证据。
- 通用 Hook 仍需至少两个独立消费者共享同一稳定事件。
- Orchestrator、多 Agent 和 DurableTask 仍需单请求、单 Pipeline 无法承载的真实任务证据。
- 持久会话仍需独立产品需求和隐私模型重新审批。

这些条件满足前，不创建相关接口、目录、配置或空实现。
