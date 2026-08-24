# 配置化用户可选模型目录与跨模型上下文实施计划
<!-- DOCUMENT_STATUS: ACTIVE -->

> **批准设计：** `docs/superpowers/specs/2026-08-21-configured-user-selectable-model-catalog-design.md`
> **Guardian：** 已批准 LEVEL_3；按两个闭合 Replacement Slice 实施
> **后端责任：** 当前 Agent 负责 Backend、配置、脚本、共享合同权威、Provider 验收、文档和联合门
> **前端责任：** 前端 Agent 负责 UI/交互及前端合同消费；不得单方面修改 Backend 或共享合同
> **提交边界：** 未经用户明确授权不暂存、不提交、不推送；保留所有无关工作树修改

## 当前进展（2026-08-24）

- Backend A1—A6 与 A8 已完成离线实现：配置化 GLM/Qwen Catalog、两个闭合 Profile、根级 MODEL/NONE 选择与指纹、Claim 后执行快照、单 Turn 单模型、五种 settled 模型错误、安全公开 Catalog 和 `PublicAgentTurn.modelExecution` 已进入唯一生产链。
- 旧 global Provider、visitor access、built-in Registry、DeepSeek、`conversational-model`、旧 env 与 Operation `provider-ref` 已退出生产路径；当前只保留 model-runtime、每 Provider credential/data-policy 和每 Operation 三层准入。
- Backend clean package、生产源码 privacy、architecture checker，以及 Eval、offline Eval、packaged-JAR runner 自测已完成；architecture 状态仍诚实保持 `IN_PROGRESS`。这些结果只关闭后端 Slice A 的离线实现与对应离线门，不代表 A9 外部门或 Slice A 整体完成。
- 真实 GLM-4.7-Flash/Qwen3.7-Flash 矩阵均未执行，Qwen 调用配置等待用户提供；未运行不得记为 PASS。
- A7 前端目录消费、会话内选择、pending 锁定和换模型交互不在本轮后端范围；Slice B 也未在本轮启动。当前 documentation gate 仍只被范围外的前端 UI 设计文档状态标记阻塞；整体状态继续为 `IN_PROGRESS`。

## 0. 开始前固定边界

- [ ] 运行 `git status --short`，记录并保护现有用户修改；特别检查 `scripts/run-agent-behavior-audit.test.ps1` 等可能重叠脚本。
- [ ] 运行 `scripts/agent-architecture-status.ps1`，确认 Guardian 状态和 deferred items。
- [ ] 运行 Backend focused/full 基线与 documentation/privacy 门；基线失败先诊断，不把历史失败归因于本变更。
- [ ] 冻结 Slice A 共享合同字段名：`ModelSelection(MODEL|NONE)`、条目 `selectionVersion`、公开 Catalog、`modelExecution` 和五个 settled code。
- [ ] 向前端 Agent 发送本计划末尾的单段提示词；前端在共享 fixtures 冻结前不得自行发明字段。

## Replacement Slice A：配置化目录、Turn 选择与双 Provider

### Task A1：RED——配置目录与 Operation 门

测试先行：

- [ ] 扩展/替换 `ModelProviderDescriptorTest`、`ModelProviderRegistrySnapshotTest`，证明目录来自配置而非 `builtIn()`。
- [ ] 新增 Model Catalog configuration binding tests，覆盖 GLM/Qwen 两个配置、enabled=false 空 endpoint、enabled=true 非 HTTPS、重复/非法 ref、selectionVersion 与默认选择。
- [ ] 扩展 `ModelOperationPolicyTest`，证明 `TURN_INTERPRETATION` 与 `GENERAL_KNOWLEDGE` 的 mode/schema/max-output/timeout 保留且不再携带 provider-ref。
- [ ] RED：空目录投影 `defaultModelSelection=NONE`，确定性 Preset 仍可执行。

目标实现：

- [ ] 创建配置生成的只读 `ConfiguredModelCatalog`/Snapshot 和安全公共条目投影。
- [ ] Provider ID 改为受验证字符串/值对象；删除厂商枚举作为目录权威。
- [ ] 增加显式条目 `selectionVersion` 与内部不公开 Descriptor fingerprint。
- [ ] 保留 Operation Schema/Codec/Validator 权威，删除 Descriptor 的应用层 policy/schema `supports()` 假耦合。
- [ ] `ModelOperationProperties` 删除 provider-ref，保留独立 Operation 门。

聚焦验证：

```powershell
mvn.cmd -f backend/pom.xml '-Dtest=*ModelProvider*,*ModelCatalog*,ModelOperationPolicyTest' test
```

### Task A2：RED——GLM/Qwen 闭合协议 Profile

测试先行：

- [ ] 新增请求 body 捕获测试：GLM 只发送 `thinking.type=disabled`。
- [ ] 新增请求 body 捕获测试：Qwen 只发送 `enable_thinking=false`。
- [ ] 两者均断言 JSON Object、非流式、正确 model、Header credential、有界 max tokens。
- [ ] 覆盖未知 Profile、429、5xx、timeout、body stall、中断、空 content、非法 JSON、多 choices、过大响应和诊断失败。
- [ ] RED：GLM 失败调用 Qwen 次数为 0；反向同理。

目标实现：

- [ ] 创建闭合 `ZHIPU_CHAT_COMPLETIONS` 与 `DASHSCOPE_CHAT_COMPLETIONS` Profile。
- [ ] 将共享 Transport 改为按已解析 Binding 执行，不理解 Goal、Evidence 或 PublicTurn。
- [ ] Credential 只存在服务端 Binding，Descriptor/Snapshot/public projection 无 Secret 字段或敏感 `toString()`。
- [ ] 不实现任意 body/header 配置、动态 Provider 注册、自动 retry 或跨 Provider fallback。

聚焦验证：

```powershell
mvn.cmd -f backend/pom.xml '-Dtest=OpenAiCompatibleStructuredModelTransport*' test
```

### Task A3：RED——ModelSelection 请求合同与指纹

测试先行：

- [ ] `AgentTurnRequestValidationTest` 覆盖 `MODEL`、`NONE`、缺 kind、MODEL 缺 ref/version、NONE 携带多余字段和未知字段。
- [ ] `AgentTurnRequestMapperTest` 证明根级选择进入全部 Command 子类。
- [ ] `RequestFingerprintFactoryTest` 覆盖 kind/ref/selectionVersion；同 requestId 换模型、换版本、MODEL/NONE 切换均冲突。
- [ ] 新增共享请求 fixtures：GLM、Qwen、NONE 和非法选择。

目标实现：

- [ ] 在 `AgentTurnRequest` 墖加闭合 ModelSelection DTO 并保持 fail-on-unknown。
- [ ] 在 `AgentTurnCommand` 增加不可变 ModelSelection 值；`toString()` 不输出访客文本或敏感配置。
- [ ] 更新 Mapper、fingerprint canonical 和 handoff replay snapshot。
- [ ] 不提供缺字段时读取后端默认模型的兼容路径。

聚焦验证：

```powershell
mvn.cmd -f backend/pom.xml '-Dtest=AgentTurnRequestValidationTest,AgentTurnRequestMapperTest,RequestFingerprintFactoryTest' test
```

### Task A4：RED——请求期解析与单 Turn 单模型

测试先行：

- [ ] 新增 `ModelExecutionResolver` 测试：不可选优先于 stale、selectionVersion 仅作用于所选条目、NONE 不解析 Provider。
- [ ] 新增 Turn 测试：Goal + General 全部使用同一 modelRef；不存在 GLM/Qwen 混用。
- [ ] 新增并行/虚拟线程测试，证明选择作为显式不可变输入传播，不依赖 ThreadLocal。
- [ ] Operation disabled 时不调用对应阶段，并正确计算 participation。

目标实现：

- [ ] 创建内部 `ModelExecutionSnapshot`，冻结 modelRef、selectionVersion、Descriptor fingerprint、Profile、能力和有效预算。
- [ ] 在 Claim 后、新执行前解析选择；已完成 replay 不受当前 Catalog 变化影响。
- [ ] 将 Snapshot 显式传入 Goal Interpretation、General Knowledge 和未来 Portfolio Expression；各 Port 不重新读取默认 Provider。
- [ ] NONE 只允许确定性阶段；模型必需阶段使用现有 operation-specific unavailable 语义失败关闭。

### Task A5：RED——错误载体、Settlement 与回放

测试先行：

- [ ] 结构非法/认证/指纹冲突/入口限流分别保持 400/401/409/429 非 settled 信封。
- [ ] `MODEL_SELECTION_STALE`、`SELECTED_MODEL_UNAVAILABLE`、`SELECTED_MODEL_TEMPORARILY_UNAVAILABLE`、`SELECTED_MODEL_RATE_LIMITED`、`SELECTED_MODEL_INVALID_RESPONSE` 全部形成 HTTP 200 settled `CAPABILITY_UNAVAILABLE`。
- [ ] retryable=true 的同 requestId 只回放错误终局；新 requestId 才能重新执行。
- [ ] 新 Claim stale/unavailable 原子 Settlement，不遗留 Active receipt。
- [ ] 模型下架后，历史 requestId 仍回放原终局与原 modelExecution。

目标实现：

- [ ] 固定 Claim/Replay -> current selection validation -> Snapshot -> execution -> Settlement 顺序。
- [ ] 五个 code 使用闭合公共消息，不泄露 Key、endpoint、Provider body 或内部原因。
- [ ] retryAfterSeconds 继续使用现有有界公共合同。

### Task A6：RED——公开 Catalog 与 modelExecution

测试先行：

- [ ] 扩展 Public Portfolio snapshot contract，覆盖 Catalog 全量、单模型、空目录和默认 NONE。
- [ ] `PublicAgentTurnInvariantTest`/Projector tests 覆盖 selectionKind、requestedModelRef、selectionVersion 和五种 participation。
- [ ] `AgentStatePayloadCodecTest` 证明 modelExecution 与 PublicTurn 原子加密回放，不读取当前配置临时拼接。
- [ ] 共享 Golden fixtures 覆盖 goal-only、answer-generation、goal-and-answer、NONE 和 attempted-unavailable。

目标实现：

- [ ] 扩展 `AgentAvailabilityResponse` 和 `/api/portfolio` 原子快照；只投影安全字段。
- [ ] 扩展 `PublicAgentTurn` 公共 modelExecution；根据实际成功采纳阶段计算 participation。
- [ ] 更新 State payload codec/version；当前未生产部署，回退可按设计清空短期 State。

### Task A7：前端合同交接检查点

后端在此检查点冻结并交付：

- [ ] `/api/portfolio.agentAvailability` Catalog fixture。
- [ ] MODEL/NONE Turn request fixtures。
- [ ] modelExecution 与五个 unavailable fixtures。
- [ ] stale/unavailable/retryable 的新 requestId 语义说明。
- [ ] 明确前端 Agent 只消费合同，不修改 Backend、Provider config 或共享字段命名。

前端 Agent 独立负责 UI/交互、会话内选择、pending 锁定、普通回放与换模型新请求、首页默认选择、错误引导及回答标识。后端 Agent 只审核共享合同消费和联合测试结果，不替前端决定视觉方案。

### Task A8：旧权威与配置全量删除

- [ ] 删除 DeepSeek enum/Descriptor/endpoint/key 分支和所有生产调用路径。
- [ ] 将 GLM `glm-4.7` 真实 model 改为 `glm-4.7-flash`。
- [ ] 删除 `portfolio.conversational-model.*`、`PORTFOLIO_MODEL_PROVIDER`、`PORTFOLIO_MODEL_ENABLED`、`PORTFOLIO_MODEL_DATA_POLICY_APPROVED`、`PORTFOLIO_AGENT_DEEPSEEK_API_KEY`、`PORTFOLIO_AGENT_GLM_API_KEY` 和 operation provider-ref。
- [ ] 新增 GLM/Qwen enabled/key/policy/endpoint 配置并更新 `.env.example`。
- [ ] 原子迁移 Eval CLI、`start-local.ps1`、`run-jar-e2e.ps1`、Provider probe、live quality、公用环境恢复断言与对应测试；合并而不覆盖用户已有脚本修改。
- [ ] 删除只证明旧单 Provider/built-in Registry/旧 supports() 的测试和配置。
- [ ] 更新 privacy/documentation scanners 与负例。
- [ ] 更新 `AGENTS.md` 中 “fixed DeepSeek/GLM adapters” 的失真声明。
- [ ] 对 Backend 生产源码、当前配置、脚本和当前文档运行 DeepSeek/旧 namespace/env 零引用门；历史 NON_AUTHORITATIVE/HISTORICAL 文档不改写。

### Task A9：Slice A 验证与真实 Provider 门

离线/联合：

```powershell
mvn.cmd -f backend/pom.xml test
mvn.cmd -f backend/pom.xml clean package
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run check
npm.cmd --prefix frontend run build
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/documentation-check.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path backend/src/main
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/agent-architecture-status.ps1
```

真实调用需用户单独授权：

- [ ] GLM Goal/General 固定矩阵与运行质量通过，记录 `GLM_4_7_FLASH_LIVE_PASS`。
- [ ] Qwen Goal/General 固定矩阵与运行质量通过，记录 `QWEN_3_7_FLASH_LIVE_PASS`。
- [ ] 两者分别验证 JSON、thinking disabled、non-streaming、deadline、429、5xx、invalid response 和隐私日志。
- [ ] 未运行的真实门保持 pending，不记 PASS，不据此宣称 Slice A 完成。

## Replacement Slice B：中性 ConversationProjection

### Task B1：RED——确定性投影规则

- [ ] 为通用解释/比较、作品集事实、推荐、Clarification、Boundary、Unavailable 写失败测试。
- [ ] 覆盖最多 5 条、单条 140 字符、总计 800 字符和稳定编号。
- [ ] 证明投影只来自验证后 SemanticResult/PublicPresentation/闭合 MessageTurn。
- [ ] 证明不含 Prompt、Provider 原始 JSON、reasoning、endpoint、私有 Evidence、内部诊断或失败/取消输入。

目标实现：

- [ ] 创建 `ConversationProjectionFactory`，不调用模型。
- [ ] 使用 Provider-neutral 保守输入预算；不依赖厂商 tokenizer 作为生产路由依赖。

### Task B2：PublicTurn、回放与共享合同

- [ ] 扩展 `PublicAgentTurn.conversationProjection` 与 `assistant-window-v1` 合同。
- [ ] 更新 State codec 和 Golden fixtures，证明回放 projection 完全一致。
- [ ] Slice B 共享 fixtures 冻结后交付前端 Agent。

### Task B3：前端消费与旧摘要删除检查点

前端 Agent 负责：

- [ ] ConversationWindow 改为消费后端 projection。
- [ ] 按最近 6 组完整 USER/ASSISTANT 对截断。
- [ ] 失败/取消且无 PublicTurn 的输入不进入窗口。
- [ ] 删除旧 `turnWindowSummary()` Goal 标签摘要权威。
- [ ] 完成 “GLM 三点 -> 切 Qwen -> 第二点展开” UI/合同测试。

### Task B4：Slice B 完整门

- [ ] Backend focused/full/package。
- [ ] Frontend focused/full/check/build。
- [ ] Memory/PostgreSQL replay parity。
- [ ] packaged-JAR desktop/mobile 跨模型语义场景。
- [ ] privacy/documentation/architecture/release gates。
- [ ] Slice A+B 全部通过后才更新 docs/08、docs/11 和架构状态；任何未获授权真实门保持 pending。

## 最终完成与回退

- [ ] Slice A、Slice B 分别接入唯一生产路径并删除各自旧权威。
- [ ] 整体完成必须满足批准设计 §23 全部条件。
- [ ] 回退只使用 Git/JAR/整体部署版本；不保留新旧 ModelSelection、旧 Provider 或双摘要运行时开关。
- [ ] 获得明确提交授权后才按 Slice 创建中文小提交；不得暂存或提交无关文件。
