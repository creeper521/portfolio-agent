# Provider 结构化输出能力与模型切换原子绑定实施计划

<!-- DOCUMENT_STATUS: ACTIVE -->

> 日期：2026-08-25
> 状态：ACTIVE；按已批准 Level 3 Replacement Slice 执行
> 对应设计：`docs/superpowers/specs/2026-08-25-provider-structured-output-capability-and-model-switching-design.md`
> 对应事项：A2-87、A2-88、A2-116、A2-117
> 实施原则：TDD、单生产权威、无自动 repair/retry/fallback、两家 Provider 独立报告
> 外部调用：用户已明确授权 Qwen/GLM 真实校验；只允许读取 `C:\secrets\portfolio-agent-model.env`，不得输出凭据、Prompt 或模型原始响应

## 0. 目标与完成定义

本计划把当前“JSON Object + Prompt 猜合同”的生产链替换为“冻结 Operation Binding + Provider 侧结构约束 + 本地 Canonical Schema + 领域语义校验”的单链路。完成不以编译通过或一次 Provider 偶发成功为准，必须同时满足：

1. Goal 与 General 都由仓库内 canonical contract 唯一描述 wire shape；
2. 生产策略闭集只有 `NATIVE_JSON_SCHEMA` 与 `REQUIRED_TOOL_CALL`；
3. 选择模型时冻结 model、operation、contract、strategy、compiler、extractor 与 token policy；
4. request 只能由冻结 binding 编译，response 只能由同一 binding 提取；
5. Provider 返回先经过严格 envelope/JSON/schema 校验，再进入领域 Codec/Validator；
6. 模型不具备某 Operation binding 时在调用前 fail-closed，并同步收缩公开 capability；
7. Qwen 与 GLM 分别通过真实 canary/matrix；任何一家失败都不得由另一家成功覆盖；
8. 全部本地门、packaged-JAR、Browser、隐私与文档门通过。

## 1. 变更边界

### 保留

- 现有 `ModelOperationPolicy` 对 Operation 启停、schemaVersion、预算与 timeout 的权威；
- 单 Turn 单模型、显式换模型产生新 requestId；
- Settlement/replay 不重新调用 Provider；
- Goal/General 领域语义校验与 fail-closed；
- 低信息确定性策略，不把零目标输入交给模型。

### 替换

- `ModelProviderProtocolProfile` 中固定 JSON Object 注入；
- `StructuredModelResponse(String json)` 原始内容合同；
- Catalog 对两家模型固定同能力声明；
- Snapshot 只冻结 model descriptor、未冻结 operation binding；
- Goal/General 各自直接调用 raw transport 并自行解析字符串。

### 非目标

- 不新增模型 root kind；
- 不启动 `goal.proposal.v6`；
- 不增加同 Provider 自动重试、模型修复轮或跨模型 fallback；
- 不把 required output tool 接入真实工具执行器；
- 不把 strategy、tool name、envelope 或 token 字段开放成环境自由组合；
- 不记录 Provider 原始输出。

## 2. Replacement Manifest

| 旧生产权威 | 新生产权威 | 删除时点 |
|---|---|---|
| `JSON_OBJECT` profile 注入 | `ApprovedModelExecutionProfile` + `OperationBinding` | Slice D |
| `StructuredModelTransport` raw JSON 响应 | `StructuredOutputGateway` typed result | Slice D |
| request 自带 operation string | binding-owned typed `ModelOperation` | Slice B |
| Catalog 固定 capabilities | 由可解析 Operation binding 派生能力 | Slice C |
| Snapshot 只冻结 descriptor | Snapshot 冻结 operation binding fingerprints | Slice C |
| Goal/General 各自 parse raw string | 严格 parser + canonical schema 后消费同一 tree | Slice B/D |
| Prompt 内完整 JSON wire shape | Provider constraint + 精简语义规则 | Slice D；不得在 B 前删除 |

每个 Slice 合入前只能存在一个生产调用权威。迁移 adapter 只允许测试范围或同一提交内短生命周期，不保留 runtime 开关。

## 3. Slice A：Canonical Contract 与严格解析（RED → GREEN）

### A1 RED

- [ ] Goal canonical schema fixture 覆盖 `CONVERSATIONAL`、全部合法 `SEMANTIC_ROUTE` 路由、澄清三形态和拒绝样本；
- [ ] General canonical schema fixture 覆盖合法 draft、未知字段、缺失字段、错误类型；
- [ ] parser 拒绝 duplicate key、trailing token、非 object root、超尺寸；
- [ ] registry 拒绝未知 contractRef、operation/schemaVersion 错配与资源 hash 漂移；
- [ ] schema validator 返回安全闭集 reason，不包含原始输出或字段值。

### A2 GREEN

- [ ] 新增 typed `ModelOperation`、`StructuredContractRef` 与 `StructuredContractRegistry`；
- [ ] 添加 Goal/General canonical JSON Schema 资源；
- [ ] 引入唯一 JSON Schema validator，并锁定 dialect；
- [ ] 实现严格 parse-once，schema validator 与 Codec 消费同一 `JsonNode`；
- [ ] 让 Goal/General Codec 提供 tree 入口，保留领域组合与语义责任；
- [ ] 保持安全 reason：`UNSUPPORTED_ROOT_KIND`、`CLARIFICATION_BLOCKED_GOAL_REQUIRED`、`LOCAL_SCHEMA_REJECTED`。

### A3 REFACTOR/GATE

- [ ] 删除重复 shape 判断时必须先有等价 fixture；
- [ ] 目标测试、package boundary 与隐私负向测试通过；
- [ ] Prompt wire shape 暂时保留。

## 4. Slice B：Operation 权威与 Provider Strategy（RED → GREEN）

### B1 RED

- [ ] Native JSON Schema 编译器生成 exact provider envelope；
- [ ] Required Tool 编译器只声明一个合成 output tool、强制选择它且不执行工具；
- [ ] extractor 拒绝多 choice、refusal、错误 finish reason、缺失 content/tool call、未知 tool、多 tool、混合 carrier 与超尺寸；
- [ ] compiler 不得删除影响本地验证的 canonical schema 关键字；Provider 子集不支持时启动前失败；
- [ ] 不存在 binding 时必须在 HTTP 调用前失败，transport invocation count 为 0。

### B2 GREEN

- [ ] 生产枚举只含 `NATIVE_JSON_SCHEMA`、`REQUIRED_TOOL_CALL`；
- [ ] 实现 strategy-specific compiler/extractor；
- [ ] 新增 `StructuredOutputGateway`：compile → one HTTP call → extract → parse → schema validate；
- [ ] required tool arguments 只作为输出载体返回，不进入 Tool executor；
- [ ] deadline、maxOutputTokens 与 token field policy 由 binding 决定；
- [ ] 移除 raw `StructuredModelResponse(String json)` 的生产暴露。

## 5. Slice C：Code-owned Execution Profile 与原子 Binding（RED → GREEN）

### C1 RED

- [ ] profile 拒绝 model identity、selectionVersion、endpoint capability 或 operation contract 不匹配；
- [ ] 环境只能选择批准 profile ID，不能覆盖 strategy/tool/envelope/token 组合；
- [ ] binding fingerprint 覆盖 contract、strategy、compiler、extractor、token policy；
- [ ] Snapshot 与服务端 binding 任一 fingerprint 不同均在调用前 stale/fail-closed；
- [ ] selectable model 缺 Goal binding 时 startup/readiness 失败；
- [ ] 缺 General binding 时 capability 与 trusted allowedGoalKinds 同步收缩。

### C2 GREEN

- [ ] 新增 code-owned `ApprovedModelExecutionProfile` 注册表；
- [ ] `OperationBinding` 冻结 operation、contractRef、strategy、token policy 与实现版本；
- [ ] Catalog 从 profile + runtime secret/endpoint 解析 descriptor/binding；
- [ ] Snapshot 冻结 operation binding fingerprints；
- [ ] resolver/transport 交叉验证 modelRef、descriptorFingerprint 与 operation binding fingerprint；
- [ ] selectionVersion 随生产策略变化显式升级。

## 6. Slice D：Goal/General 消费链切换与旧权威删除

- [ ] Goal Interpretation 通过 gateway 获取 schema-validated tree，再做领域 decode/semantic validation；
- [ ] General Answer 通过同一 gateway，随后执行现有 `GeneralDraftValidator`；
- [ ] `GoalResolutionContext.allowedGoalKinds` 根据同一 Snapshot 的 General binding 收缩；
- [ ] 精简 Prompt，只保留语义规则和 variant 选择，不再让 Prompt 单独拥有 wire shape；
- [ ] 删除 production `JSON_OBJECT`、固定 capabilities、raw content transport 与自由组合协议配置；
- [ ] JSON Object 只可存在于隔离 provider probe/canary runner，不进入 Spring production graph；
- [ ] 删除后的 `rg` 门证明旧权威零生产引用。

## 7. 本地验证矩阵

### 7.1 目标测试

- [ ] Contract registry/schema/parser tests；
- [ ] native/tool compiler/extractor/gateway tests；
- [ ] profile/catalog/resolver/snapshot/binding tests；
- [ ] Goal/General adapter/codec/validator tests；
- [ ] model switching、stale selection、replay/idempotency tests；
- [ ] low-information regression：`1 -> 明确推荐`；
- [ ] capability projection 与 allowedGoalKinds 收缩 tests；
- [ ] 日志隐私负向 tests。

### 7.2 全量与治理门

- [ ] Backend `mvn test`；
- [ ] clean package；
- [ ] code-quality、architecture、privacy、documentation；
- [ ] architecture/doc checker tests；
- [ ] Frontend type-check/build（仅因 packaged/browser 依赖，不扩大 UI 变更）；
- [ ] packaged-JAR 模型关闭与模型启用场景；
- [ ] Browser 桌面/移动模型切换、新 requestId、无失败结果复用。

## 8. 真实 Provider 验证

### 8.0 2026-08-25 执行检查点

- 本地 v3 replacement 已完成：canonical contract、严格 parse-once、结构化 gateway、代码所有的 execution profile、冻结 operation binding/fingerprint、Provider 专属 compiler/extractor、Goal/General 原子切换与旧 JSON Object 生产权威删除；
- Qwen native JSON Schema 被 exact endpoint 拒绝；批准的 required-tool v3 路径中，10/10 Provider 请求完成且 envelope 提取成功，10/10 在本地 canonical `goal.proposal.v5` Schema 层拒绝；
- Qwen 的安全字段聚合 reason 为 `FIELD_TYPE_INVALID_RECENT_REFERENCE` 8 次、`FIELD_TYPE_INVALID_GOAL` 2 次；由于 JSON Schema `oneOf` 会产生候选分支噪声，这只能证明 canonical v5 直接生成失败，不能作为模型原始字段值的断言；
- GLM required-tool v3 最新同构批次为 6/10 `RATE_LIMITED`、4/10 `DEADLINE_EXCEEDED`，无成功 Provider 响应进入 envelope/schema 层；
- Backend 全量 `1017 tests / 0 failures / 0 errors / 4 skipped`，Frontend check/build、code-quality、architecture、privacy、documentation 均通过；
- 当前计划保持 `ACTIVE`。Provider Draft → canonical v5 确定性编译层属于本计划原“不得启动 v6”边界之外的重大合同迁移，取得明确授权并冻结增量设计之前停止实施；不得用自动修复、重试、fallback 或放宽 canonical v5 绕过。

### 8.0.1 2026-08-26 v4 授权实施检查点

- [x] 用户已明确授权 `goal.provider-draft.v1 -> goal.proposal.v5` 确定性编译层，以及 Qwen/GLM `selectionVersion` 提升到 v4；
- [x] `OperationBinding` 已改为同时冻结 Provider/Application 两份合同、fingerprint 与 compiler profile；Gateway 在单次传输后按 `Provider Draft Schema -> compiler profile verification -> deterministic compiler -> canonical Schema` 固定顺序执行；
- [x] Goal Draft Schema、`GoalProviderDraftCompiler` 和全六类 Goal 编译测试已接入；编译器只从 trusted input/闭集表派生 canonical 字段，拒绝未知、歧义、越界与跨分支载荷；
- [x] Qwen/GLM Catalog、共享合同、请求 fixture 与前端目录已提升到 `qwen-3-7-flash-v4` / `glm-4-7-flash-v4`；旧版本继续走 stale selection；
- [x] 本地 Backend 全量为 `1032 tests / 0 failures / 0 errors / 43 skipped`；Frontend 为 `56 files / 557 tests`，type-check 与 build 通过；
- [x] 新增两家独立 Goal Draft 真实矩阵：每家 5 次直接推荐 + 5 次同 conversation 的低信息两轮序列，响应必须精确回显 v4 selection，且聚合日志必须证明恰好 10 次 Goal Provider 调用；
- [x] frontend-inclusive packaged-JAR、L0/L1/L3、桌面/移动 Browser、code-quality、architecture、documentation、生产源码隐私与两家独立真实 Provider 矩阵已执行；
- [x] v4 阶段证据已记录：Qwen Goal 直接 5/5 + 两轮 5/5，GLM exact 10-call 门通过但 5/10 限流；当时的 Qwen General 缺字段样本由后续 8.0.2 的 v6 增量继续处理。计划仍因 Provider 全能力证据不足保持 `ACTIVE`，A2-87/88/117 不关闭。

### 8.0.2 2026-08-26 Qwen v6 General 增量检查点

- [x] 用户明确授权 Qwen 从 v5 提升到 `qwen-3-7-flash-v6`，注册并绑定 `general.provider-draft.v2`；GLM 保持 `glm-4-7-flash-v4`；
- [x] General Provider Draft Schema、确定性 Compiler、canonical `general.draft.v2`、Prompt、Catalog、公开合同、前端测试与矩阵脚本已同步；
- [x] Backend `1092 tests / 0 failures / 0 errors / 43 skipped`，Frontend `56 files / 557 tests`、check/build 与最终 JAR 隐私门通过；
- [x] Qwen v6 最新最小真实样本中 CONCISE、STANDARD、DETAILED、CONVERSATIONAL 各 1/1 成功；
- [ ] Comparison 两次均在到达 General 前被 Goal Interpretation 的 `goal.provider-draft.v1` 缺字段拒绝，仍需上游 Goal 合同证据；GLM 不在本增量修改范围且继续保持 v4；
- [x] 未增加自动 repair、同请求 retry、runtime fallback 或跨模型降级。

本增量仍不允许自动修复、同 Provider 自动重试、第二轮模型调用、跨模型降级或 runtime fallback。真实矩阵中的每个样本都是独立新 requestId，不是失败样本的自动重试。

### 8.1 安全条件

- 只从 `C:\secrets\portfolio-agent-model.env` 注入；
- 禁止 shell 回显、日志打印或测试报告保存 secret 值；
- 不保存 Prompt、用户全文、原始 completion/tool arguments；
- 诊断只保留 modelRef、operation、strategy、contractRef、failure layer/reason、HTTP class、latency bucket 与 request correlation；
- 每次重试使用新 requestId；不自动跨模型。

### 8.2 每家独立矩阵

对 Qwen 与 GLM 分别运行：

1. 首轮低信息 `1`：Provider 调用计数为 0，server-fixed conversation；
2. 首轮明确“给我推荐两个项目”：Goal 合法、推荐数量为 2；
3. 两轮 `1 -> 给我推荐两个项目`：第二轮为独立明确目标，不被历史污染；
4. 合法 recent reference：有 typed state 时可引用；无 state 时 fail-closed；
5. General explanation；
6. General comparison；
7. 项目 discussion enter/continue/switch/exit；
8. 同模型重新提问与换模型重新提问：均为新 requestId，不复用失败 Turn；
9. malformed/refusal/truncation 仅通过离线 stub 注入，不诱导真实 Provider 输出敏感失败样本。

### 8.3 通过阈值

- 每个 model × operation 至少 10 个合法样本；核心 Goal 路径至少包含 5 次原问题两轮序列；
- 结构采用率必须为 100%；任何 schema/envelope 失败都阻塞该 model × operation binding 上线；
- 领域语义拒绝必须逐项归类，不能由重试隐藏；
- HTTP 429/5xx 单独报告 Provider 可用性，不归因于 schema；
- Qwen 成功不能关闭 GLM；GLM 限流不能否定 Qwen；
- 若 exact endpoint 不支持计划策略，模型/Operation 保持不可用，回到设计评审选择另一批准策略，不运行时降级。

## 9. 文档与完成纪律

- [ ] docs/08 记录真实生产 profile、能力和限制；
- [ ] docs/15 更新 A2-87/88/116/117 的证据状态，不越过 Exit Gate；
- [ ] docs/11 只在授权范围内完成且证据新鲜时写入；
- [ ] docs/00、checker、机器状态与配置说明一致；
- [ ] 本计划的提交范围不包含与本批次无关文件；
- [ ] 任一 REQUIRED 外部门失败时保持 `ACTIVE/IN_PROGRESS`；
- [ ] 不以 fixture、单次成功或另一 Provider 的结果代替真实矩阵。

## 10. 执行顺序与停线点

1. A RED/GREEN 完成并评审 canonical contract；
2. B RED/GREEN 完成 provider gateway；
3. C RED/GREEN 完成 profile/binding/snapshot；
4. D 原子切换消费者并删除旧权威；
5. 本地全量与治理门；
6. Qwen 真实矩阵；
7. GLM 真实矩阵；
8. packaged/browser 与最终文档。

以下任一情况立即停线并回到设计，不现场猜测：canonical contract 无法保持 v5/v2 语义、Provider 约束需要第二轮工具执行、需要 runtime fallback、需要把策略组合开放给环境、或任何公开合同必须新增 variant。
