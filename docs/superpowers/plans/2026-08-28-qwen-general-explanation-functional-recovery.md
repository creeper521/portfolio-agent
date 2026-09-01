# Qwen General Explanation 功能恢复实施计划

<!-- DOCUMENT_STATUS: HISTORICAL -->

> 日期：2026-08-28
> 分支：`codex/qwen-general-functional-recovery`
> 批准设计：`docs/superpowers/specs/2026-08-28-qwen-standard-explanation-functional-recovery-design.md`
> Guardian 授权：`APPROVED_LEVEL_3_REPLACEMENT`
> 基线提交：`73806f4`；更早的 Goal、目录指纹、General 规则与证据门基线为 `fad7421..740d6fb`
> 收口状态：2026-08-28 已完成候选实现、离线认证工具与确定性全门；真实 Qwen 300 条采样、生产提升和公开目录切换均未运行，继续记为 `NOT_RUN/NOT_READY`。

## 1. 交付目标

把当前 Qwen General Explanation 从严格的 Provider Draft v3 链迁移为候选 v7 链：

```text
Qwen Goal v2（不变）
  -> EXPLANATION(CONCISE|STANDARD|DETAILED)
  -> general.provider-draft.v4
  -> general-provider-draft-compiler.v4
  -> general.draft.v3（恒为两条 statement）
  -> strict Codec / Validator
  -> ANSWER / COMPLETE
```

同时交付：

- 三档不可拆分的合同与验证；
- Provider 前固定拒绝 Comparison，General Provider 调用为 0；
- 仅限 Qwen v7 General 的一次 transport retry；
- 固定合成数据的 Provider 诊断实验室、v3/v4 离线回放和 300 条认证计算器；
- 候选先认证、通过后才允许提升的发布门；
- A2-85/A2-117 与 GATE-19 的诚实账本同步。

本计划不授权真实 Provider 外呼。代码、固定 corpus 和离线门可以完成；F2/F4 的真实 Qwen 采样仍需独立、逐次明确授权。没有新鲜 300 条证据时，不把生产部署或 READY 写成已完成。

## 2. 深模块与唯一权威

### 2.1 General Draft Admission 深模块

外部接口继续是现有 `StructuredOutputCompiler`：

```java
String profileVersion();
JsonNode compile(JsonNode providerDraft);
```

`GeneralProviderDraftCompiler` 的实现隐藏：unknown root 投影、闭集文本归一化、同 role 数组连接、caveats 整体隔离、可信 topic/depth/role/aspects 派生和非敏感归一化报告。调用方不学习每条规则，也不能配置新规则。

精确不变量：

- 输出 statement 恒为 2 条：index 0 是 `DEFINITION`，index 1 是 `MECHANISM`；
- 两条 aspects 分别精确为 `[DEFINITION]`、`[MECHANISM]`；
- CONCISE 自然句 1+1；STANDARD 每 role 1..3、总 2..6；DETAILED 每 role 4..6、总 8..12；
- string 与 string[] 只在同一 role 内机械连接，不能跨 role 搬运；
- caveats 任一 item 损坏则整组丢弃；core 损坏仍拒绝；
- canonical v3 继续 unknown-key closed。

### 2.2 General Transport Retry 深模块

新增 package-private `GeneralTransportRetryExecutor`，外部接口只有：

```java
StructurallyValidatedOutput execute(
    ModelTransportBinding binding,
    StructuredModelRequest request,
    StructuredOutputCompiler compiler);
```

实现隐藏 attempt 编排、UUID attempt identity、抖动、等待、deadline 复算和失败分类。生产使用真实 sleeper/jitter，测试使用确定性 adapter；这是模块内部 seam，不新增通用 Provider retry 配置或跨 Operation 端口。

精确不变量：

- 只在 compiler profile 为 `general-provider-draft-compiler.v4` 时启用；Goal v2 与 GLM 路径仍单次；
- attempt 最大值为 2；同一 binding、request、Prompt、modelRef 与 absolute deadline；
- eligible：connect/reset、HTTP 502/503/504、无可用响应体且仍有预算的 timeout、429 的批准等待分支；
- 无 `Retry-After`：100..250ms；整数秒 `Retry-After<=1`：按值等待；`>1`、非法值或 HTTP-date：不重试并保持 RATE_LIMITED；
- 等待后剩余 General deadline 必须 >=3000ms；
- 任何 2xx 后 envelope/JSON/provider schema/compiler/canonical/semantic 失败不重试。

### 2.3 Provider 诊断实验室

实验室是 repo 内脚本与固定 corpus，加 repo 外 raw artifact root。它没有 Spring Bean、HTTP endpoint、自由文本参数或生产自动启动入口。外部调用脚本只接受 corpus case ID；双回放和认证报告只永久保存 case ID、版本、闭集结果与计数。

## 3. 并行执行纪律

三个实现 Agent 只编辑各自文件集合，不提交 Git；主 Agent 负责集成、逐批测试和中文提交。共享依赖通过冻结名称协作：

| Lane | 所有者 | 独占文件面 |
|---|---|---|
| A 合同/编译 | Agent A | schemas、registry、OperationBinding、Qwen profile/config、General compiler/rules/codec/validator、General prompt 及对应测试 |
| B retry/Comparison | Agent B | General adapter、transport/failure、retry executor、GoalBoundaryPolicy、生命周期/transport/adapter 测试 |
| C 实验室/认证 | Agent C | 新增 corpus、实验室/回放/认证脚本及测试、必要的新 test-only replay 文件 |

禁止两个 Agent 同时修改同一文件。若发现计划遗漏导致文件冲突，先停下并通知主 Agent，不自行扩大所有权。

## 4. Task A1：先用失败测试冻结 v4 wire 与 canonical v3

**Agent A 独占。**

新增：

- `backend/src/main/resources/model-contracts/general.provider-draft.v4.schema.json`
- `backend/src/main/resources/model-contracts/general.draft.v3.schema.json`
- `backend/src/test/java/com/portfolio/agent/infrastructure/model/structured/GeneralProviderDraftV4SchemaTest.java`
- `backend/src/test/java/com/portfolio/agent/infrastructure/model/structured/GeneralDraftV3SchemaTest.java`

修改：

- `backend/src/main/java/com/portfolio/agent/infrastructure/model/structured/StructuredContractRef.java`
- `backend/src/main/java/com/portfolio/agent/infrastructure/model/structured/StructurallyValidatedOutput.java`
- `backend/src/main/java/com/portfolio/agent/infrastructure/model/structured/StructuredOutputContractRegistry.java`
- `backend/src/main/java/com/portfolio/agent/infrastructure/model/structured/StructuredOutputGateway.java`
- `backend/src/test/java/com/portfolio/agent/infrastructure/model/structured/StructuredOutputContractRegistryTest.java`
- `backend/src/test/java/com/portfolio/agent/infrastructure/model/structured/StructuredOutputGatewayTest.java`

步骤：

1. 先写 v4 schema 失败测试：root 非 object、缺 definition/mechanism、core 为 number/bool/object、数组含非 string 必须失败；string/string[]、caveats missing/null/任意受资源限制 JSON、unknown root 必须进入 Compiler。
2. 先写 v3 schema 失败测试：Explanation canonical 必须恰好两条 statement，role/aspects/subject/dimension/unknown key 精确；caveats 必须 non-null array。
3. 运行测试并确认因资源未注册而 RED：

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml test '-Dtest=GeneralProviderDraftV4SchemaTest,GeneralDraftV3SchemaTest,StructuredOutputContractRegistryTest'
```

4. 创建两个 schema 并注册，同时保留 v3/v2 历史资源供离线回放；新 Qwen binding 不得回退旧资源。
5. Provider v4 Admission 在 Schema 前先执行 JSON 最大深度 16、数组元素总量 64 的资源门；测试必须精确覆盖 16/17 与 64/65 边界。
6. `StructurallyValidatedOutput` 收敛为不透明 carrier，外部不能绕过 Registry/Gateway 自建“已验证”对象；Codec 只消费该 carrier。
7. 再运行相同命令，预期全部 PASS。

## 5. Task A2：实现 General Draft Admission 深模块

**依赖 A1；Agent A 独占。**

修改：

- `backend/src/main/java/com/portfolio/agent/turn/infrastructure/model/GeneralProviderDraftCompiler.java`
- `backend/src/main/java/com/portfolio/agent/turn/capability/general/GeneralDraftRules.java`
- `backend/src/main/java/com/portfolio/agent/turn/capability/general/GeneralDraftCodec.java`
- `backend/src/main/java/com/portfolio/agent/turn/capability/general/GeneralDraftValidator.java`
- `backend/src/main/java/com/portfolio/agent/turn/capability/general/GeneralKnowledgeGenerator.java`
- `backend/src/main/java/com/portfolio/agent/common/observability/DiagnosticEvent.java`
- `backend/src/main/java/com/portfolio/agent/common/observability/ModelOutputDiagnostics.java`
- `backend/src/test/java/com/portfolio/agent/common/observability/DiagnosticEventTest.java`
- `backend/src/test/java/com/portfolio/agent/turn/infrastructure/model/GeneralProviderDraftCompilerTest.java`
- `backend/src/test/java/com/portfolio/agent/turn/capability/general/GeneralDraftCodecAdversarialTest.java`
- `backend/src/test/java/com/portfolio/agent/turn/capability/general/GeneralDraftValidatorTest.java`
- `backend/src/test/java/com/portfolio/agent/turn/capability/general/GeneralKnowledgeGeneratorTest.java`
- `backend/src/test/java/com/portfolio/agent/turn/capability/general/GeneralModelOutputDiagnosticsTest.java`

要求：

1. `GeneralDraftRules.ExplanationRule` 改为 role 内自然句下限/上限和总下限/上限，不再携带 `TYPICAL_USAGE` 等 coverage；三个 depth aspects 始终是 role 本身。
2. `GeneralProviderDraftCompiler` 只消费 definition/mechanism/caveats；unknown root 只累计数量，不记录 name/value。
3. 文本规则顺序精确采用 Spec §8.1；NFC、trim、whitespace、terminal punctuation 均做表驱动测试和幂等 property test。
4. 同 role 数组通过校验后按单空格连接成一个 statement text；不得排序、裁剪、去重或跨 role 拼接。
5. malformed caveats 整组变 `[]`，发布 `DEGRADED` 与闭集 reason；missing/null caveats 也能 COMPLETE。
6. `GeneralDraftCodec` 新增 `decode(StructurallyValidatedOutput)`，只接受 `GENERAL_KNOWLEDGE` 的 `general.draft.v2|v3`，再解码同一棵已验证 tree；`GeneralKnowledgeGenerator` 必须改用该入口。Qwen v3 由 canonical schema 先保证精确两条；不能把 Codec 变成宽松 Provider parser。
7. `GeneralDraftValidator` 只检查确定性 topic/role/aspects/自然句数/主要语言；删除 Explanation 的细粒度 coverage 并集要求，Comparison v2 历史校验保留供 GLM/离线回归。
8. 主要语言规则冻结为 `Han > 0 && Han >= Latin`；v2 与 v3 的 caveat 重复语义保持版本隔离，不能因 v3 放宽而改变历史合同。

验证：

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml test '-Dtest=GeneralProviderDraftCompilerTest,GeneralDraftCodecAdversarialTest,GeneralDraftValidatorTest,GeneralKnowledgeGeneratorTest,GeneralModelOutputDiagnosticsTest'
```

## 6. Task A3：冻结候选 v7 binding、Prompt 与不可拆分能力

**依赖 A1/A2；Agent A 独占。**

修改：

- `backend/src/main/java/com/portfolio/agent/infrastructure/model/structured/OperationBinding.java`
- `backend/src/main/java/com/portfolio/agent/infrastructure/model/configuration/ApprovedModelExecutionProfile.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/prompts/general-provider-draft-system.txt`
- `backend/src/test/java/com/portfolio/agent/infrastructure/model/configuration/ApprovedModelExecutionProfileTest.java`
- `backend/src/test/java/com/portfolio/agent/infrastructure/model/provider/ConfiguredModelCatalogTest.java`
- `backend/src/test/java/com/portfolio/agent/infrastructure/model/provider/ModelProviderDescriptorTest.java`
- `backend/src/test/java/com/portfolio/agent/infrastructure/model/ModelExecutionResolverTest.java`
- `backend/src/test/java/com/portfolio/agent/infrastructure/model/structured/StructuredModelTestFixtures.java`
- `backend/src/test/java/com/portfolio/agent/turn/infrastructure/AgentCapabilityConfigurationTest.java`
- `backend/src/test/java/com/portfolio/agent/turn/contract/PortfolioModelCatalogGoldenFixtureTest.java`
- `backend/src/test/java/com/portfolio/agent/turn/lifecycle/AgentTurnLifecycleBoundaryTest.java`

要求：

- compiler profile 常量改为 `general-provider-draft-compiler.v4`；
- Qwen profile/selection 改为 `QWEN_3_7_FLASH_STRUCTURED_V7` / `qwen-3-7-flash-v7`，General binding 精确为 provider v4 + application v3 + compiler v4；
- GLM profile、Goal v2 与既有已部署/公开合同基线不变；候选 JAR 只配置硬编码为 `selectable:false` 的 Qwen v7，因此自身不把 Qwen 暴露到公开目录，现网与公开合同 fixture 仍保持 v6。认证前不得部署候选、泄漏 v7 或用 Qwen 结果冒充 GLM READY；
- Prompt 按 trusted depth 请求 CONCISE 1+1、STANDARD 目标 2+2 且合同允许 1..3、DETAILED 每 role 4..6；只输出 definition/mechanism/optional caveats；temperature 由 Lane B 在 Adapter 冻结为 0.0；
- catalog/descriptor fingerprint 变化使旧 selection stale。

验证：

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml test '-Dtest=ApprovedModelExecutionProfileTest,ConfiguredModelCatalogTest,ModelProviderDescriptorTest,PortfolioModelCatalogGoldenFixtureTest'
```

## 7. Task B1：Comparison 在 Provider 前固定 BOUNDARY

**Agent B 独占，可与 A1-A3 并行。**

修改：

- `backend/src/main/java/com/portfolio/agent/turn/planning/GoalBoundaryPolicy.java`
- `backend/src/test/java/com/portfolio/agent/turn/planning/GoalBoundaryPolicyTest.java`
- `backend/src/test/java/com/portfolio/agent/turn/lifecycle/AgentTurnLifecycleBoundaryTest.java`
- `backend/src/test/java/com/portfolio/agent/turn/lifecycle/AgentTurnLifecycleSelectedModelFailureTest.java`（只在需要证明不可重试语义时修改）

先写 RED 测试：任何合法 `GENERAL_COMPARISON`，不论 pair 数量，均返回固定文案“当前暂不支持直接比较；请分别询问这些概念。”；Lifecycle 结算 `PublicAgentTurn.Boundary(code=OUT_OF_SCOPE)`，SemanticPlanCompiler、SemanticTurnEngine 与 General model port 均零交互。

实现只改 Goal 后、Plan 前的确定性裁决；Goal v2 仍识别 Comparison，容量常量可保留为未来历史规则，但当前运行时不能到达 General Provider。

验证：

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml test '-Dtest=GoalBoundaryPolicyTest,AgentTurnLifecycleBoundaryTest,AgentTurnLifecycleSelectedModelFailureTest'
```

## 8. Task B2：实现同 Qwen、共享 deadline 的 transport retry

**Agent B 独占，可与 A1-A3 并行；使用 A3 保持原常量名但值升级后的 compiler profile。**

新增：

- `backend/src/main/java/com/portfolio/agent/turn/infrastructure/model/GeneralTransportRetryExecutor.java`
- `backend/src/main/java/com/portfolio/agent/infrastructure/model/ProviderAttemptContext.java`
- `backend/src/main/java/com/portfolio/agent/infrastructure/model/OutboundModelSecretBoundary.java`
- `backend/src/test/java/com/portfolio/agent/turn/infrastructure/model/GeneralTransportRetryExecutorTest.java`
- `backend/src/test/java/com/portfolio/agent/infrastructure/model/ProviderAttemptContextTest.java`

修改：

- `backend/src/main/java/com/portfolio/agent/turn/infrastructure/model/OpenAiCompatibleGeneralKnowledgeAdapter.java`
- `backend/src/main/java/com/portfolio/agent/infrastructure/model/OpenAiCompatibleStructuredModelTransport.java`
- `backend/src/main/java/com/portfolio/agent/infrastructure/model/StructuredModelTransport.java`
- `backend/src/main/java/com/portfolio/agent/infrastructure/model/StructuredModelFailure.java`
- `backend/src/main/java/com/portfolio/agent/infrastructure/model/SelectedModelFailureException.java`
- `backend/src/main/java/com/portfolio/agent/infrastructure/model/structured/StructuredOutputGateway.java`
- `backend/src/main/java/com/portfolio/agent/common/observability/DiagnosticEvent.java`
- `backend/src/main/java/com/portfolio/agent/common/observability/ModelOutputDiagnostics.java`
- `backend/src/test/java/com/portfolio/agent/turn/infrastructure/model/OpenAiCompatibleGeneralKnowledgeAdapterTest.java`
- `backend/src/test/java/com/portfolio/agent/infrastructure/model/OpenAiCompatibleStructuredModelTransportProtocolTest.java`
- `backend/src/test/java/com/portfolio/agent/infrastructure/model/OpenAiCompatibleStructuredModelTransportDeadlineTest.java`

先写 RED 矩阵：connect/reset、502/503/504、无 Retry-After 429、Retry-After=1、Retry-After>1、非法 Retry-After、400/401/403、2xx invalid JSON/envelope/provider schema/compiler/canonical、等待后 deadline 2999/3000ms、第二次失败、线程 interrupt。

实现要求：

- `StructuredModelFailure` 保留精确 HTTP status 和“Retry-After 缺失/合法/非法”闭集状态，不保留 response body；
- transport 仍只执行一次 HTTP attempt，不在通用 transport 内循环；
- `GeneralTransportRetryExecutor` 是唯一循环所有者；每次 attempt 生成不同 UUID，但日志只发 attempt index、count、失败闭集和耗时桶，不发高基数 UUID；
- 唯一 Transport seam 在 HTTP 与 `provider.call.*` 事件前扫描最终 payload 的文本值：拒绝高置信 secret 赋值、standalone API key 与 PEM 私钥标记，忽略字段名并放行普通 API-key 技术讨论；命中只投影 `SAFETY/SECRET_LIKE_CONTENT` 闭集、HTTP 调用为 0、不可重试且不记录正文；
- 第一次 Qwen v4 General attempt 仅在剩余预算大于 3250ms 时设置 `remaining - 3000ms - 250ms` 的 attempt cap，为第二次调用保留最小预算；第二次 attempt 取消该 cap，但二者始终共享同一 absolute deadline；Goal、GLM 与非 v4 General 不使用该 cap；
- Adapter 只对 v4 compiler 使用 executor，`markAttempted(ANSWER_GENERATION)` 仍只表示该阶段参与过，不因第二 attempt 重复改变生命周期；
- `StructuredModelRequest` 对两次 attempt 语义相同，temperature 冻结为 `0.0d`；
- sleeper 被中断时恢复 interrupt flag 并立即失败，不继续调用。

验证：

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml test '-Dtest=GeneralTransportRetryExecutorTest,OpenAiCompatibleGeneralKnowledgeAdapterTest,OpenAiCompatibleStructuredModelTransportProtocolTest,OpenAiCompatibleStructuredModelTransportDeadlineTest'
```

## 9. Task C1：建立固定 100 主题 × 3 depth corpus

**Agent C 独占，可与 A/B 并行。**

新增：

- `scripts/provider-diagnostic-lab/qwen-general-explanation-corpus.v1.json`
- `scripts/provider-diagnostic-lab/qwen-general-explanation-corpus.schema.json`
- `scripts/provider-diagnostic-lab/assert-corpus.test.ps1`

每个 case 精确字段：

```json
{
  "caseId": "java-001",
  "category": "JAVA_SPRING",
  "topic": "依赖注入",
  "prompts": {
    "CONCISE": "用两句话简要解释依赖注入。",
    "STANDARD": "解释依赖注入是什么，以及它如何工作。",
    "DETAILED": "详细解释依赖注入的原理、适用场景、边界和常见误区。"
  }
}
```

要求：100 个唯一 topic、10 类各 10 个、300 个非空问法；不含姓名、账号、资产、Cookie/token、私有项目、实时事实或医疗/法律/投资建议。脚本计算 corpus SHA-256 并输出，不把正文写入长期报告。

验证：

```powershell
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provider-diagnostic-lab/assert-corpus.test.ps1
```

## 10. Task C2：实现 raw 实验室硬围栏与 v3/v4 双回放

**依赖 A2 的 v4 compiler；Agent C 独占新文件。**

新增：

- `scripts/provider-diagnostic-lab/raw-root-common.ps1`
- `scripts/provider-diagnostic-lab/invoke-qwen-general-lab.ps1`
- `scripts/provider-diagnostic-lab/invoke-qwen-general-lab.test.ps1`
- `scripts/provider-diagnostic-lab/replay-general-drafts.ps1`
- `scripts/provider-diagnostic-lab/replay-general-drafts.test.ps1`
- `backend/src/test/java/com/portfolio/agent/turn/infrastructure/model/GeneralProviderDraftDualReplayTest.java`
- `backend/src/test/java/com/portfolio/agent/turn/infrastructure/model/LegacyGeneralV3Baseline.java`
- `backend/src/test/resources/provider-diagnostic-lab/legacy-v3/b5cf941/*.java.snapshot`

硬门：

- 只接受 `-CaseId` 和枚举 depth，不接受 `-Prompt`/任意文本；
- 所有入口复用唯一 raw-root 校验；`-RawArtifactRoot` 必须解析到 repo 根之外，拒绝卷根、用户目录、Desktop/Documents/Temp 宽根、repo 双向包含和 reparse point，并在 ACL、枚举或写入前验证 marker；
- TTL 精确固定 24h，任何 `24h + epsilon` 配置或已到期 artifact 均失败关闭；启动先清理到期 artifact；
- `-AuthorizeRealProvider` 与 repo 外 secret file 缺一不可；普通测试不外呼；
- `captureSource` 由实际 endpoint 内部派生；正式回放、证据准备与报告只接受 `REAL_PROVIDER`，`TEST_LOOPBACK` 只能进入隔离测试，不能形成候选通过结论；
- stdout/stderr/aggregate 只含 caseId、版本、状态、Rule ID/count、latency/token；raw request/response 只进短期 artifact；
- unknown field 只计数，不保存 name/value 到 aggregate；
- dual replay 针对同一 raw response 分别运行 v3 strict chain 与 v4 candidate chain，不形成生产 fallback。

验证：

```powershell
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provider-diagnostic-lab/invoke-qwen-general-lab.test.ps1
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provider-diagnostic-lab/replay-general-drafts.test.ps1
```

## 11. Task C3：实现 300 条认证报告与盲审导出

新增：

- `scripts/provider-diagnostic-lab/report-qwen-general-certification.ps1`
- `scripts/provider-diagnostic-lab/report-qwen-general-certification.test.ps1`
- `scripts/provider-diagnostic-lab/new-qwen-general-certification-evidence.ps1`
- `scripts/provider-diagnostic-lab/certification-evidence-common.ps1`
- `scripts/provider-diagnostic-lab/qwen-general-blind-review.schema.json`
- `backend/src/test/java/com/portfolio/agent/infrastructure/model/QwenGeneralCertificationGuardTest.java`
- `backend/src/test/java/com/portfolio/agent/infrastructure/model/QwenGeneralCertificationGuardSupport.java`

报告按 depth 分别输出 transport、shape、semantic、L3 denominator 和总体 300 条安全 false acceptance；阈值精确为安全/身份/权限 0、缺 core 被接受 0、parse+compile >=98%、L3 >=95%、availability >=95%、P95 不超 deadline、canonical false acceptance 0。任一 depth 失败则总状态 `NOT_READY`。

零容忍门必须来自实际执行当前 production boundary 的 hash-bound guard artifact，且每个门 `cases > 0`；报告重算并校验 manifest、盲审包、unblind map、review input、guard artifact 与 sealed review 的完整证据链。旧 v3 基线同时绑定 `b5cf941` 四个 Git blob snapshot、可执行基线 SHA-256 与 golden behavior matrix，不能由候选实现自我模拟历史行为。

Guard producer 使用 `jdeps -R` 从实际编译产物机械形成 class/resource 闭包，并绑定闭包清单、源码与资源哈希；source-drift 负例只能在带严格 marker 的临时源根执行，不得修改主工作树源码。测试必须断言主工作树 GuardSupport 的内容、哈希与 mtime 全程不变。

盲审字段为五个基础 boolean，加对应 depth boolean 与裁决状态；不包含 raw Prompt/response。单评审者模式必须输出 `reviewLimitation=SINGLE_REVIEWER_BLINDED_SECOND_PASS`，不能伪装双评审。

验证：

```powershell
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provider-diagnostic-lab/report-qwen-general-certification.test.ps1
```

## 12. Task I1：主 Agent 集成与冲突审查

等待 A/B/C 全部返回后，主 Agent：

1. 检查 `git diff --name-only` 与三 Lane 所有权；发现同文件修改先人工合并，不接受后到者覆盖。
2. 审查三个深模块的接口：caller 只知道 compile/execute/caseId，不暴露规则表、sleep 算法或 raw path 细节。
3. 运行各 Lane 定向测试；失败按根因修复，不通过删测试、放宽安全门或增加 runtime fallback。
4. 按原子责任创建中文提交，建议边界：
   - `feat(agent): 建立Qwen三档解释v4编译链`
   - `fix(agent): 前置拒绝Comparison并限定传输重试`
   - `feat(eval): 建立Qwen解释诊断实验室与认证门`

## 13. Task I2：候选 JAR 的确定性全门

执行：

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run check
npm.cmd --prefix frontend run build
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.test.ps1
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1 -Path backend/src
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.test.ps1
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1 -Path backend/src
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/documentation-check.test.ps1
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/documentation-check.ps1
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.test.ps1
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path backend/src/main
git diff --check
```

随后构建候选但不部署：

```powershell
& 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml clean package
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-jar-e2e.test.ps1
```

必须证明 JAR 中 v7 binding、v4/v3 schema、compiler v4 与 Prompt 同时存在，旧 selection stale，Goal v2/GLM/public contract 不回归。

## 14. Task I3：账本与候选状态收口

修改：

- `docs/08-当前实现状态.md`
- `docs/11-项目演进日志.md`
- `docs/15-Agent 2.0真实交互问题清单与修复边界.md`
- `docs/agent-architecture-status.json`
- 本计划

规则：

- A2-85：内容/合同错误仍单次失败；仅 General v4 批准 transport 闭集 retry；
- A2-117：Goal v2 仍单次；General v4/v3/compiler v4 最多两 attempt；
- A2-80：Comparison 的原 Goal 阻断虽已解除，但本 Slice 明确在 Goal 后、Provider 前返回固定 `OUT_OF_SCOPE`；独立认证前运行时不可达，不能再以“应到达 General”作为当前目标；
- GATE-19 继续 OPEN；overall 继续 `IN_PROGRESS`；
- 只写已取得的新鲜测试/候选证据；F2/F4 未真实外呼时明确 `NOT_RUN`，不得写 READY；
- 本计划只有在实现与离线门完成后才从 ACTIVE 改为 HISTORICAL；真实 300 条认证和生产部署留作受控后续 Slice。

## 15. 终止条件

本轮“实现完成”只在以下全部成立时成立：

- 三 Lane 代码与测试已集成，无工作区遗失或跨 Lane 覆盖；
- 后端、前端、脚本、架构、文档、隐私和候选 JAR 确定性门全部通过；
- Comparison 零 Provider 调用、Goal v2/secret/tool/identity/resource 边界无回归；
- 候选 v7 未被描述为已生产部署；
- 没有真实 300 条证据时，能力状态保持 `NOT_READY/IN_PROGRESS`。

真实 Provider 认证与生产提升属于后续受控执行：取得明确外呼授权，运行 300 条封存集，三档分别过门后，才可执行 Spec Gate F5 的原子部署和 catalog 更新。

## 16. 离线实施收口记录

- 合同/编译线独立评审结论为 `READY`；资源边界精确覆盖 JSON 深度 16/17 与数组元素 64/65，canonical Codec 只接受不透明的已验证 carrier。
- Comparison/retry 线独立评审结论为 `READY_WITH_MINOR`，两处诊断/温度 Javadoc 已同步；默认 10 秒 General deadline 的环回测试证明第一次 attempt cap 后仍可发生第二次调用，总耗时不越界。
- 诊断实验室经三轮独立阻塞复审收紧 raw-root、来源证明、机器 guard、过期判定、历史基线与证据链；最终复审通过后才允许本计划历史化。
- 新鲜离线证据：Backend `1298 tests / 0 failures / 0 errors / 4 skipped`；100 主题、300 prompts corpus SHA-256 为 `c58844d3b43ee96d9aa009cdd5fc797b0eaea569ed47300270b2e9fd9814b5a7`；机械 producer 闭包为 103 classes / 8 resources；实验室围栏、双回放、认证报告、质量/架构/隐私/文档门与候选 JAR 门均通过。
- 本记录不包含真实 Provider 成功样本。Qwen v7 仍为不可选择候选，候选 JAR 不公开 Qwen；现网与公开合同 fixture 仍是 v6。`EVIDENCE_BEFORE_COMPLETION`、A2-80/A2-117 与 GATE-19 继续开放。

## 17. 后续用户指令与当前偏差记录（2026-09-01）

本计划上述内容仍是 2026-08-28 离线候选的历史收口事实。后续任务中，用户明确要求把 Qwen 配置为 `selectable:true`、接入真实 API、完成项目讨论路径并提交推送；当前实现因此提升为 Qwen v8 selectable/default catalog。Goal wire 同步改为 fixed-flat `goal.provider-draft.v3`，General 仍为 provider v4/application v3/compiler v4。

该后续目录变化不回写为本历史计划已完成 F4/F5：真实 `PROJECT_DISCUSSION` 专用 API 与桌面/移动浏览器门已通过，但 L4 Goal 草案矩阵和 General 三档质量失败，300 条封存认证未运行。Qwen General 仍为 `NOT_READY/IN_PROGRESS`，`EVIDENCE_BEFORE_COMPLETION`、A2-80/A2-117 与 GATE-19 继续开放。
