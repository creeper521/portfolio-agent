# Agent 架构债与防御性设计评审
<!-- DOCUMENT_STATUS: SUPERSEDED -->

> **评审日期：** 2026-08-17
> **评审对象：** 当前本地工作树中的 Agent 后端、前端消费者、协议兼容层与工程门禁
> **文档性质：** 非权威架构评审与治理建议，不构成实施授权、版本承诺或验收结论
> **当前定位：** 已被 Agent 2.0 单权威收敛与 2026-08-19 稳定化设计取代；仅保留历史评审证据
> **证据边界：** 本文先独立形成评审结论；外部 AI 建议在正文冻结后另行复核，避免反向污染原始判断

## 1. 文档目的

本文回答以下问题：

1. 当前代码是否已经出现明显的“堆屎山”或架构失控；
2. 哪些复杂度来自合理的领域、安全和隐私约束；
3. 哪些防御性编程只在下游缓解症状，不能消除根因；
4. 哪些实现可能造成真实的性能或交互体验下降；
5. 后续应按照什么顺序治理，才能避免以一次大重构制造新的风险。

本文不替代：

- `AGENTS.md` 和 `docs/04-项目代码约束.md` 中的产品、安全与工程边界；
- `docs/08-当前实现状态.md` 对当前能力状态的权威描述；
- `docs/13-Agent对话体验与智能编排改造路线图.md` 的产品级改造总账；
- `docs/superpowers/specs/` 下已经确认的设计；
- 针对具体重构另行形成的设计和实施计划。

如果本文与当前源码、测试、发布门禁或权威设计冲突，应重新复现证据并修订本文，而不是直接按本文改代码。

## 2. 评审基线与方法

### 2.1 当前规模

本轮静态盘点基于提交 `9980068` 上方的 2026-08-17 15:31（Asia/Shanghai）本地未提交工作树。Java LOC 按包含空行的物理行统计，得到：

- 后端主代码 801 个 Java 文件、68,521 行；
- 后端测试 285 个 Java 文件、38,252 行；
- 前端全目录 144 个 TypeScript 文件、36 个 Vue 文件；其中 `frontend/src` 下有 119 个 TypeScript 文件；
- 后端主代码中 285 个文件不超过 30 行、459 个文件不超过 60 行；
- 40 个后端主代码文件超过 300 行；
- 当前主要复杂度热点集中于少数 Runtime、Mapper、ViewModel 与 Vue 容器，而不是均匀散落在整个仓库。

这些数字只描述评审时点，且当前工作树包含大量未提交修改。数字不能单独证明架构好坏，只用于识别复杂度是否集中、测试成本是否与实现同步膨胀。

### 2.2 判断标准

本文不把“大文件”“接口多”“校验多”直接判定为坏设计，而采用以下标准：

1. **单一权威：** 同一业务事实是否只由一个模块决定；
2. **模块深度：** 一个稳定、简单的接口能否隐藏足够多的实现复杂度；
3. **变化局部性：** 修改一种状态、来源或协议字段时，需要同时修改多少层；
4. **依赖方向：** 领域核心是否依赖上层服务或适配细节；
5. **防御收益：** 校验是否位于不可信边界，能否真正阻断风险；
6. **运行成本：** 防御是否位于默认热路径，数据规模是否足以形成可感知成本；
7. **删除测试：** 删除某个抽象、兼容分支或二次校验后，是否会减少系统能力或安全保证。

### 2.3 证据等级

- **已复现：** 由当前源码、脚本输出或测试结果直接确认；
- **静态推断：** 调用结构能够支持该判断，但尚未通过运行时指标验证；
- **待运行验证：** 需要真实 Provider、浏览器、压力场景或生产数据才能确认。

## 3. 总体判断

当前项目已经进入明显的架构债阶段，但尚未达到“传统屎山、只能重写”的程度。

它具备很多健康基础：

- 模块化单体方向明确；
- 公开数据、隐私和失败关闭边界清晰；
- 大量值对象保持不可变；
- 核心行为有较密集的单元测试和集成测试；
- 模型能力默认关闭，外部 Provider 不能自动扩大公开声明；
- 路由、Provider、执行器等关键位置已经存在真实可替换 seam。

当前最危险的问题不是代码随意，而是：

> 分层、类型、校验和兼容逻辑不断增加，但同一个“最终答案语义”仍由多个层分别解释。

因此更准确的描述是“过度结构化的多权威系统”或“兼容层驱动的结构化泥球”。继续叠加功能时，一个状态、正文、证据或上下文规则可能要同时修改后端运行时、响应 Mapper、前端协议映射、前端 ViewModel 和最终组件。局部遗漏不会总是编译失败，而可能表现为静默降级或用户看到互相矛盾的状态。

## 4. 当前回答链路与主要复杂度传播

当前主要控制和投影关系可以概括为：

```text
请求与上下文
  ↓
ConversationalAgentRuntime
  - 授权上下文
  - 处理 preset / confirm / clarify
  - 路由并协调任务
  - 计算 scope / intent / resolution / blocks
  ↓
ConversationAnswerResponseMapper
  - 重新组合顶层 blocks
  - 映射公开状态
  - 决定来源、证据、支持摘要和续接句柄
  - 处理 stp-v1 / stp-v2 差异
  ↓
mapAnswerResponse + semanticTurnView
  - 再次验证协议形状
  - 在不一致时 fail-closed 或回退
  - 合并顶层正文与任务结果
  ↓
AgentWorkspace + ConversationThread
  - 管理请求生命周期与操作状态
  - 根据最终状态继续做展示防御
```

这条链路有多层安全价值，但“答案是什么意思”没有集中在一个深模块中。

## 5. 高优先级架构问题

### A-01 最终答案投影尚未形成单一权威

- **影响等级：** 高
- **证据状态：** 已复现
- **问题类型：** 多权威、重复业务决策、兼容性沉积

`ConversationalAgentRuntime.result()` 已经计算：

- `answerScope`；
- `blocks`；
- `intent`；
- `resolution`；
- `generationState`、`construction`、`evidenceState` 等派生状态。

相关实现集中在：

- `backend/src/main/java/com/portfolio/agent/answer/service/ConversationalAgentRuntime.java:281-304`；
- `projectedScope()`：约 377 行；
- `projectedIntent()`：约 393 行；
- `projectedResolution()`：约 418 行；
- `projectedBlocks()`：约 475 行。

随后 `ConversationAnswerResponseMapper` 并未只做机械 DTO 转换，而是再次：

- 根据 `completedTasks` 重建顶层正文；
- 在重建结果为空时回退到 Runtime 的 `result.blocks`；
- 根据 stp-v1/stp-v2 重新决定任务状态和公开 resolution；
- 构造来源目录、支持摘要、稳定 ID、续接上下文和推荐结果。

相关实现集中在：

- `backend/src/main/java/com/portfolio/agent/answer/mapper/ConversationAnswerResponseMapper.java:79-117`；
- `topLevelBlocks()`：约 168-214 行；
- `publicTaskStatus()`：约 810 行；
- `publicResolution()`：约 897 行。

前端继续执行第三轮语义解释：

- `frontend/src/features/agent/model/mapAnswerResponse.ts:47-153`；
- `frontend/src/features/agent/model/semanticTurnView.ts:270-375`；
- preset、`blocks/sections`、stp-v1/stp-v2 和 context handle 都有兼容或失败关闭分支。

最直接的症状是 `semanticTurnView.ts:235-253` 中的 `hasExecutionAnswerConflict()`：当顶层宣称 `ANSWERED + VERIFIED`，但执行快照所有阶段均为 `FAILED` 时，前端只能提示“执行能力降级”。注释也明确承认真正修复必须发生在后端。

#### 风险

1. 修改状态语义时，需要跨至少四层同步；
2. 后端不同投影视角可能同时“各自正确”，但组合后互相矛盾；
3. 前端容错会把后端契约错误转化为静默降级，降低问题暴露速度；
4. 新协议版本会继续复制相同决策逻辑；
5. 测试需要为每一层重复冻结相似语义，维护成本随功能组合增长。

#### 建议方向

完成已经在设计中提出的 `Canonical Answer Projection`：

```text
CanonicalAnswerProjection.project(
    requestContext,
    publicContentVersion,
    agentTurnResult,
    authorizedContext
) -> CanonicalPublicAnswer
```

该模块应一次性决定：

- 公开 disposition 与 resolution；
- 正文和推荐；
- 任务完成状态；
- 证据状态、来源目录与支持摘要；
- 上下文失效与续接能力；
- 可以公开的诊断元数据。

Runtime 只负责协调，Response Mapper 只做字段转换，stp-v1/stp-v2 只做协议适配，前端不再推导后端业务成功状态。

### A-02 后端 Runtime 与 Mapper 已形成双热点

- **影响等级：** 高
- **证据状态：** 已复现
- **问题类型：** God Module、发散式变化、接口过浅

当前主要文件规模：

- `ConversationalAgentRuntime.java`：930 行；
- `ConversationAnswerResponseMapper.java`：916 行；
- `ProposalCompiler.java`：405 行；
- `SemanticTurnCoordinator.java`：355 行。

Runtime 同时承担：

- 输入映射和上下文授权；
- preset 校验与绑定；
- 路由、确认和澄清；
- 任务执行协调；
- 回答状态和正文投影；
- fallback 和诊断。

Mapper 同时承担：

- DTO 转换；
- 任务结果到公开正文的编排；
- 公开状态策略；
- v1/v2 协议策略；
- 证据与来源策略；
- 续接上下文生成；
- 稳定公开身份生成。

这并不是“930 行一定错误”，而是两个类都拥有多个独立变化原因。例如新增一种来源支持规则，本应只修改投影模块，却可能进入 Mapper；新增确认状态可能同时修改 Runtime 和 Mapper。

#### 建议方向

优先抽取业务语义，而不是按行数拆 Helper：

1. `CanonicalAnswerProjection`：唯一公开语义投影；
2. `TurnExecutionOrchestrator`：只负责 route/confirm/execute；
3. `PresetAnswerMaterialResolver`：只负责审核过的 preset 材料；
4. `ConversationAnswerResponseMapper`：最终退化为无业务分支的 DTO Mapper。

不建议把每个私有方法都变成一个接口或 Spring Bean，否则只会把一个大类变成一组浅模块。

### A-03 前端工作区与会话线程承担过多状态所有权

- **影响等级：** 高
- **证据状态：** 已复现
- **问题类型：** God Component、业务状态与视图耦合

当前主要热点：

- `AgentWorkspace.vue`：1715 行；
- `ConversationThread.vue`：2555 行；
- `semanticTurnView.ts`：976 行；
- `answerTypes.ts`：911 行；
- `mapAnswerResponse.ts`：675 行。

`AgentWorkspace.vue` 同时管理：

- 响应式布局、抽屉和拖拽尺寸；
- 会话消息和本地请求状态；
- AbortController、超时、重试和恢复；
- 上下文授权、续接和失效；
- confirm、adjust、clarify、regenerate 等动作；
- evidence focus 和诊断上报。

`ConversationThread.vue` 同时承担：

- 多种消息和执行状态渲染；
- 来源、证据、推荐和上下文卡片组合；
- 大量业务 guard；
- 三十多个交互事件；
- 体量较大的样式与模板。

相关测试文件也接近或超过两千行，说明测试复杂度正在镜像实现结构，而不是围绕更小、更稳定的状态接口组织。

#### 建议方向

按状态所有权拆分，不按视觉碎片拆分：

1. `useConversationController`：发送、取消、超时、重试、恢复；
2. `useTurnActions`：确认、调整、澄清、续接、失效；
3. `useWorkspaceLayout`：抽屉、宽度、响应式；
4. `toConversationMessageView`：Canonical Answer 到只读 ViewModel；
5. `ConversationThread`：只负责列表组合和事件转发；
6. 现有状态卡片继续作为有明确输入输出的叶子组件。

不建议为了减少单文件行数而引入通用 `Card`、`Manager`、`Handler` 等无领域含义抽象。

### A-04 answer 内部包边界过粗，存在多组双向依赖

- **影响等级：** 中高
- **证据状态：** 已复现
- **问题类型：** 依赖方向不稳定、架构门禁盲区

按当前 Java import 静态扫描，存在以下双向包依赖：

- `answer.domain ↔ answer.intelligence`；
- `answer.intelligence ↔ answer.routing`；
- `answer.domain ↔ answer.routing`；
- `answer.context ↔ answer.routing/service`。

例如：

`backend/src/main/java/com/portfolio/agent/answer/domain/AgentTurnResult.java`

虽然位于 `answer.domain`，却直接依赖：

- `routing.domain.PlanConfirmation`；
- `routing.domain.SemanticTurnOutcome`；
- `routing.domain.SemanticTurnPlan`；
- `routing.service.ClarificationRequest`；
- `routing.service.SemanticTurnDecision`。

这说明 `answer.domain` 目前不是稳定的领域核心，而是用于汇总多层结果的共享包。尤其 domain 依赖 service 类型，会使依赖方向难以解释。

当前 `architecture-check.ps1` 主要约束 common/portfolio/answer 等粗粒度边界，没有对 answer 内部 routing/intelligence/composition/context 的循环形成约束。

#### 建议方向

可以选择以下一种稳定方向，而不是继续双向扩展：

1. 将计划、任务、执行结果集中为稳定的 `routing.core`；
2. intelligence 只依赖任务输入与公开材料 port；
3. routing core 不反向依赖 intelligence/composition 的实现结果；
4. `AgentTurnResult` 移到 orchestration result 包，或改成不依赖 service 类型的 canonical result；
5. 通过 ArchUnit 或编译级 dependency test 固化方向。

无需为此拆微服务。模块化单体内部建立可解释的单向依赖已经足够。

### A-05 stp-v1/stp-v2 兼容逻辑已从边界渗入核心

- **影响等级：** 中高
- **证据状态：** 已复现
- **问题类型：** 迁移税、条件分支扩散

当前后端和前端均广泛出现：

- `stpV1` / `stp-v1`；
- `stp-v2`；
- `legacy`；
- `fallback`；
- compatibility 分支。

`SemanticTurnContractPolicy` 将未声明版本的请求默认解释为 stp-v1；前端默认请求 stp-v2，但仍保留经显式同意后退回 stp-v1 的路径。Mapper、ViewModel、上下文句柄、推荐结果、任务状态和来源字段都在识别协议版本。

兼容本身不是错误。问题是兼容规则已经渗入业务投影核心，使临时迁移成本变成永久维护成本。

#### 建议方向

1. 为 stp-v1 设置可检查的退役条件，例如调用占比、客户端版本或明确日期；
2. 将 v1/v2 映射集中到协议 Adapter；
3. 核心业务模型只暴露 canonical semantics，不接收 `boolean stpV1`；
4. 在 v1 退役前，禁止新增业务规则直接分叉 v1/v2；
5. 为兼容 Adapter 建立合同 fixture，而不是在所有业务测试中重复 v1/v2 矩阵。

## 6. 防御性编程评审

### 6.1 应当保留的高价值防御

以下设计与项目的公开作品集、隐私和模型边界直接相关，不应因“代码多”而删除：

1. 对模型或 Provider JSON 的严格字段矩阵和枚举校验；
2. `TurnProposal`、任务、引用、推荐等对象的不可变快照；
3. 任务数量、推荐数量、引用数量和上下文数量上限；
4. 模型提议重新绑定到后端公开目录，而不是信任模型提供的身份；
5. ContextHandle、确认令牌和公开内容版本的一致性验证；
6. 来源、证据和公开主体的 fail-closed；
7. 日志不记录访客原始问题和私有内容；
8. 所有模型能力默认关闭，真实 Provider 必须显式授权。

当前集合通常只有 1—6 个任务、最多少量推荐和引用。`copyOf`、`requireNonNull` 和小列表遍历的 CPU 成本相对网络、模型、检索和渲染可以忽略。把这些防御当成主要性能问题，结论不成立。

### 6.2 重复验证导致政策拥有者不唯一

- **影响等级：** 中
- **证据状态：** 已复现

`ProposalCompiler` 在构造函数中自行创建 `SemanticPlanValidator`，并在编译后执行验证；`ModelLedTurnRouter` 对编译结果又调用一次注入的 `planValidator.validate()`。

双重验证当前数据规模下不会造成明显性能问题，但有两个设计风险：

1. Compiler 内部验证器不是由统一配置注入，未来可能与 Router 使用的验证器规则不同；
2. “Compiler 保证什么、Router 还要防什么”没有形成明确合同。

建议让 `ProposalCompiler.compile()` 的成功类型只可能携带已经验证的计划，Router 不重复执行同一政策。若 Router 必须防御第三方 Compiler，则应在接口和测试中明确这是独立 trust boundary。

### 6.3 Shadow 隔离保护了延迟，但静默吞掉数据质量问题

- **影响等级：** 中
- **证据状态：** 已复现

`ShadowTurnRouter` 使用有界线程池和 `AbortPolicy`，先返回 authoritative legacy 结果，再异步调用模型解释。这是正确的延迟隔离设计。

但当前代码：

- 队列饱和时捕获 `RejectedExecutionException` 并忽略；
- shadow 执行出现任意 `RuntimeException` 时忽略；
- 没有直接记录 submitted、dropped、completed、failed 等无内容指标。

这不会伤害用户主请求，却会使 shadow 评估样本发生未知偏差：高负载时最容易被丢弃的请求，可能恰恰是最需要评估的请求。

建议只增加不含用户文本的计数和耗时指标，不把异常重新传播到主请求。

### 6.4 前端失败关闭只能保护展示，不能修复协议

- **影响等级：** 中
- **证据状态：** 已复现

前端对以下情况执行了额外防御：

- `CONTEXT_INVALIDATED` 与正文同时存在；
- 新旧 context handle 不一致；
- 顶层成功与执行快照全部失败；
- stp-v2 字段缺失或格式不合法；
- completedTasks 与 blocks/sections 组合不完整。

这些防御应在迁移期保留，但必须同时：

1. 上报脱敏诊断；
2. 有后端合同测试复现；
3. 有删除条件。

否则前端会永久承担后端契约修复责任。

### 6.5 可能属于低收益过度设计的局部信号

以下尚不足以单独构成重构理由，但值得在后续修改相关代码时顺手消除：

1. 部分 public interface 只有单一实现和极少引用，如果既不是外部边界、测试 seam，也没有第二个真实 Adapter，可考虑内联；
2. `OpenAiCompatibleTurnInterpretationAdapter` 构造函数接收并只做 null-check、但未实际使用 `ObjectMapper`，属于推测性依赖；
3. `ConversationalAgentConfiguration` 约 472 行，集中手工装配多个默认关闭能力；可按能力模块建立少量配置单元，但不必为每个 Bean 建 Factory；
4. Runtime 中存在疑似历史沉积的私有方法或数据投影，应通过引用检查和测试确认后删除，而不是继续保留备用路径。

## 7. 性能与用户体验风险

### P-01 当前默认配置不支持“模型防御已经拖慢默认请求”的结论

- **证据状态：** 已复现

`backend/src/main/resources/application.yml` 当前默认：

- turn interpretation operation：`DISABLED`；
- turn interpretation mode：`LEGACY`；
- routing semantic assist：`DISABLED`；
- general answer material：`DISABLED`；
- portfolio/cross-domain expression：`DISABLED`；
- public database：`false`。

因此模型提议解析、模型表达和 shadow 等能力默认不进入普通热路径。不能把它们的潜在成本描述成当前默认体验已经发生的性能回归。

### P-02 任务严格串行执行是更真实的潜在延迟来源

- **影响等级：** 中高
- **证据状态：** 静态推断，待指标验证

`SemanticTurnCoordinator`：

- 建立共享绝对截止时间；
- 按稳定拓扑顺序遍历任务；
- 在单个 `for` 循环中同步调用 `executeSafely()`；
- 前序任务消耗预算后，后续任务可能被标记为 `NOT_EXECUTED_BUDGET`。

如果未来启用慢检索、模型表达或多任务计划，彼此独立的任务也会串行争夺同一个截止时间。这比不可变对象复制更可能造成用户可感知等待。

但不建议立即并行化。应先采集：

- 每种任务的排队时间与执行时间；
- 每轮预算耗尽位置；
- 独立任务数量；
- P50/P95/P99 请求耗时；
- Provider、数据库和本地检索各自占比。

只有数据证明独立任务互相拖累，再按 DAG 的同一拓扑层使用有界并行执行，并继续共享绝对截止时间。

### P-03 超大前端组件首先是维护风险，尚无证据证明是运行时瓶颈

- **证据状态：** 静态推断

组件过大可能导致：

- 响应式依赖范围扩大；
- 无关状态改变触发更广的计算或渲染；
- 测试 fixture 和交互矩阵膨胀；
- 修改局部交互时引发难以定位的回归。

但没有浏览器 profile、长会话基准或组件更新时间数据时，不能直接宣称 2555 行组件已经造成明显掉帧。拆分的首要理由是状态所有权和变化局部性，性能收益需要单独验证。

## 8. 工程门禁与“测试全绿”错觉

### Q-01 当前源码没有通过已声明的完整架构/代码质量门禁

- **影响等级：** 高
- **证据状态：** 已复现

本轮重新执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1 -Path backend/src
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1 -Path backend/src
```

得到三个当前失败：

1. `backend/src/test/java/com/portfolio/agent/answer/behavior/AgentBehaviorAdversarialProviderIntegrationTest.java` 位于 `behavior` 路径，却声明 `package com.portfolio.agent.answer.adapter.model`；
2. `SemanticGoalDeduplicator.java:39` 使用 `private record GoalKey`；
3. `SemanticPlanValidator.java:155` 使用 `private record SemanticTaskKey`。

后两项违反 `AGENTS.md` 与代码质量脚本中“不使用 record”的明确约束。

门禁脚本自身测试已通过，说明当前失败不是脚本测试已知失效造成的假阳性。

### Q-02 普通 Maven 测试没有覆盖完整发布门禁

- **影响等级：** 中高
- **证据状态：** 已复现

本轮聚焦执行以下测试类：

- `ModelLedTurnRouterTest`；
- `ShadowTurnRouterTest`；
- `ProposalCompilerTest`；
- `ConversationalAgentRuntimeTest`；
- `ConversationAnswerResponseMapperTest`。

结果为 5 个报告、50 项测试、0 failure、0 error、0 skipped。

但 `backend/pom.xml` 没有绑定 `architecture-check.ps1` 或 `code-quality-check.ps1`；它们只由 `scripts/verify-release.ps1` 显式调用。仓库当前也未见 `.github` 工作流。

因此：

> `mvn test` 通过只能证明 Maven 测试通过，不能证明当前源码符合完整项目门禁。

建议将不依赖外部 Provider、浏览器和生产环境的静态门禁接入 Maven `validate` 或实际 CI 必需 Job。真实 Provider 和完整发布验收仍保持显式授权，不应混入普通 CI。

## 9. 哪些设计不应被误判为过度设计

### 9.1 `TurnRouter` seam 已经存在多个真实实现

当前存在 legacy、shadow、model-led 等不同路由行为。该接口不是纯假设抽象，已经隔离真实策略变化，应保留。

### 9.2 Provider、公开检索和执行器 Adapter 有明确边界价值

这些 Adapter 隔离外部服务、公开数据源、模型和任务执行，并支持 Fake、禁用配置和失败关闭。只要接口保持小而稳定，它们属于深模块入口，而不是无意义层级。

### 9.3 严格领域对象是安全协议的一部分

`TurnProposal`、`SemanticTurnPlan`、证据与上下文对象字段较多，是因为它们承载：

- 身份；
- 作用域；
- 公开来源；
- 任务依赖；
- 失败和澄清原因；
- 续接边界。

只要不存在多套对象表达同一语义，强类型本身不是问题。真正要减少的是重复投影和重复政策，而不是把领域对象退化成字符串 Map。

## 10. 目标架构建议

### 10.1 后端目标关系

```text
HTTP Request
  ↓ mechanical mapping
ConversationCommand
  ↓
TurnExecutionOrchestrator
  ├─ ContextAuthorizer
  ├─ PresetResolver
  ├─ TurnRouter
  └─ SemanticTurnCoordinator
  ↓
ExecutedTurn
  ↓
CanonicalAnswerProjection   ← 唯一公开语义权威
  ↓
CanonicalPublicAnswer
  ├─ StpV2ResponseAdapter
  └─ StpV1CompatibilityAdapter
```

关键约束：

1. `ExecutedTurn` 只描述发生了什么，不提前伪装成公开回答；
2. `CanonicalAnswerProjection` 决定用户能看到什么；
3. 协议 Adapter 不能重新决定业务成功状态；
4. 前端不能根据执行阶段重新推导后端 resolution；
5. 每个状态转换应能在一个后端合同测试中被冻结。

### 10.2 前端目标关系

```text
AnswerResponse
  ↓ strict protocol decoder
CanonicalAnswerView
  ↓
ConversationController
  ├─ request lifecycle
  ├─ retry / cancel / recover
  └─ context continuation

WorkspaceShell
  ├─ ConversationThread
  ├─ EvidencePanel
  └─ Context/Plan drawers
```

前端仍可防御无效协议，但应只做：

- 拒绝或降级不可信字段；
- 保留可信正文；
- 上报脱敏诊断；
- 显示后端明确提供的 canonical 状态。

前端不应组合出一个后端从未明确给出的“成功事实”。

## 11. 推荐治理顺序

### 阶段 0：修复当前门禁事实

1. 修复 package/path 不一致；
2. 将两个 `record` 改成符合项目约束的显式不可变类，或经正式决策修改约束；
3. 把静态代码质量和架构检查接入日常必需门禁；
4. 明确“测试通过”和“发布门禁通过”的不同措辞；
5. 修订根目录 `AGENTS.md` 中“one SQL audit project and one executable preset”的过时内容规模描述，或明确标注其历史快照时点；在修订前，当前规模以发布 Bundle manifest 和 `docs/00`、`docs/08` 为准。

这一步应尽快完成，因为它影响所有后续架构判断的可信度。

### 阶段 1：冻结 Canonical Answer 合同

1. 列出当前所有公开状态和正文来源；
2. 为冲突场景建立后端合同 fixture；
3. 定义 Canonical Answer，不先改前端视觉；
4. 明确 Runtime、Projection、Mapper 各自拥有和禁止拥有的规则。

### 阶段 2：迁移后端投影

1. 先让 Canonical Projection 覆盖 stp-v2；
2. Mapper 改成机械映射；
3. 删除 Runtime 和 Mapper 之间的正文回退双权威；
4. 为每种 disposition/resolution/evidence 组合建立参数化合同测试。

### 阶段 3：隔离兼容层

1. 将 stp-v1 逻辑移动到 Compatibility Adapter；
2. 建立 v1 使用指标和退役条件；
3. 禁止核心服务新增 `stpV1` 分支；
4. 退役后删除 v1 Adapter 和对应 fixture。

### 阶段 4：拆分前端状态所有权

1. 先提取纯函数 ViewModel；
2. 再提取请求和操作 composable；
3. 最后缩小 Workspace/Thread 组件；
4. 每一步保持用户体验和 API 契约不变。

### 阶段 5：依赖方向和性能治理

1. 调整 answer 内部包依赖并建立依赖测试；
2. 增加任务耗时、预算耗尽和 shadow 丢弃指标；
3. 根据数据决定是否对独立 DAG 层做有界并行；
4. 删除已经没有调用量的兼容和 fallback 分支。

## 12. 明确不建议做的事情

1. 不建议重写整个 Agent；
2. 不建议拆微服务；
3. 不建议删除不可变对象、模型校验和隐私失败关闭；
4. 不建议为了减少文件行数制造大量一方法接口；
5. 不建议在没有耗时指标前直接并行执行所有任务；
6. 不建议让前端继续吸收新的后端语义兼容责任；
7. 不建议把 stp-v3 作为逃避 v1/v2 收口的新层，而应先明确 Canonical Answer。

## 13. 可用于后续讨论的决策问题

在形成正式设计前，需要共同确认：

1. stp-v1 当前是否还有真实消费者，退役判据是什么；
2. preset 正文和普通任务正文是否允许使用不同材料来源，但最终必须由同一投影输出；
3. `CONFIRMATION_REQUIRED` 在公开顶层 resolution 中的唯一语义是什么；
4. 前端遇到顶层状态与执行快照矛盾时，是隐藏正文、显示降级，还是将整轮视为协议错误；
5. Canonical Answer 是否同时服务 HTTP、评测和 packaged-JAR E2E；
6. 任务并行化的业务顺序、证据顺序和稳定输出顺序如何保持；
7. 当前 package 结构中，routing、intelligence、composition 哪一层应拥有稳定任务结果模型。

## 14. 独立评审结论

综合判断：

- **存在明显架构债：** 是；
- **已经不可维护：** 否；
- **主要问题是传统无结构屎山：** 否；
- **主要问题是多权威和兼容层扩散：** 是；
- **防御性复制和空值校验正在造成主要性能下降：** 没有证据；
- **存在低收益重复验证和静默防御：** 是；
- **真实潜在体验风险主要来自任务串行和多层协议解释：** 是，但任务串行仍需运行指标确认；
- **需要微服务或整体重写：** 否；
- **最优先治理点：** Canonical Answer Projection、门禁可信度、协议兼容隔离和前端状态所有权。

当前项目最应该停止的不是所有抽象或所有防御，而是继续让新的业务规则同时进入 Runtime、Mapper、前端映射和组件。只要先建立唯一公开语义权威，再逐层删除迁移性防御，现有测试、类型和安全边界都可以成为重构资产，而不是阻碍。

## 15. 外部 AI 建议复核

本节在第 1—14 节独立结论写入后，才开始阅读用户提供的其他 AI 评审。这样可以区分真正的独立共识与后见之明。

### 15.1 外部评审总体评价

外部评审的核心比喻是“分层投机的宫殿”：每个阶段都形成完整、自洽、有测试的新栈，但旧阶段没有同步退出。这个总体观察有较强解释力，与本文的“过度结构化的多权威系统”和“兼容层驱动的结构化泥球”基本一致。

外部评审尤其有价值的部分是：

1. 把部分历史 Bean、死参数和无调用方法具体点名；
2. 区分真正的隐私防御与默认拓扑下收益有限的容错机制；
3. 指出工程工具代码与线上发布物边界不清；
4. 指出异常归一化可能同时抹掉可观测性；
5. 提醒配置迁移采用“旧开关 + 新开关 + 冲突检测”，而不是完成收口。

但该评审也存在明显的强断言和时点漂移：

- 将已确认死代码与“线上无调用、但由脚本/CLI 消费的工程工具基建”合并估算为约三分之一 main；该比例缺少一致的 reachability 和分类口径；
- 对工具代码的“零外部生产引用”观察成立，但将其与死代码共同放入同一体量结论，容易掩盖“应删除”和“应拆制品”是两种不同治理动作；
- 把当前 Bundle 的同后端策略降级与真正的后端故障转移混为一谈；
- 未结合已确认设计，建议 Model-led 失败后恢复完整旧关键词路由；
- 个别结论已经被当前工作树中的后续实现改变。

因此，本节按“采纳、有条件采纳、纠正、不采纳”逐项记录。

### 15.2 与独立评审形成共识的建议

#### E-01 多阶段对象和平行 Block 模型过多

- **复核结论：** 采纳
- **证据状态：** 已复现

当前请求到响应之间确实存在较长的领域对象链，例如：

```text
ConversationAnswerRequest
→ SemanticTurnInput
→ TurnProposal
→ SemanticTurnPlan / ValidatedSemanticTurnPlan
→ SemanticTask
→ CapabilityExecutionResult / ValidatedEvidenceBundle
→ PortfolioAnswerMaterial / PortfolioAnswerPlan
→ TaskOutcome / SemanticTurnOutcome
→ AgentTurnResult / ConversationAnswerResult
→ ConversationAnswerResponse
```

这条链不应被简单归结为“14 个壳都没用”：输入合同、模型提议、验证后计划、执行结果和公开响应本来就属于不同信任阶段。

但“答案章节块”至少存在以下四种相近表达：

- `PortfolioAnswerSection`；
- `TaskResultPayload.SectionBlock`；
- `ConversationAnswerBlock`；
- `ConversationAnswerBlockResponse`。

此外，按同名 Java 文件和顶层类型复核，当前主代码中可以确认 7 组同名类型分布在不同包，包括：

- `DeterministicPortfolioAnswerComposer`；
- `PortfolioAnswerMaterial`；
- `GroundedStatement`；
- `SubjectReference`；
- `RetrievalMode`；
- `AudienceRole`；
- `DocumentEmbeddingPort`。

因此应保留“不同信任阶段使用不同类型”，但减少同一阶段内的同构复制。Canonical Answer 应成为执行结果到公开协议之间的稳定中间表示。

#### E-02 字符串 reason code 参与核心控制流

- **复核结论：** 采纳，但优先级低于单一投影
- **证据状态：** 已复现

`SemanticTurnDecision`、`AgentTurnResult`、`TaskOutcome` 都以 `Set<String>` 保存 reason code。Runtime 的多个 `projectedXxx()` 方法通过：

```java
hasReason(agentTurn, "ROUTING_SUBJECT_INVALID_REFERENCE")
hasReason(agentTurn, "PRESET_CONTRACT_UNAVAILABLE")
hasReason(agentTurn, "PRESET_CONTRACT_STALE")
```

重新推导公开语义。

外部评审建议“全部枚举化”方向基本合理，但不建议先建立一个覆盖全系统的巨大 ReasonCode enum。更稳妥的顺序是：

1. 先建立 Canonical Answer Projection；
2. 将会影响控制流的内部原因改为按领域分组的封闭类型；
3. 仅在公开协议 Adapter 中转成稳定字符串；
4. 纯诊断性 code 可以继续使用受控字符串，不必全部进入领域枚举。

否则可能只是把“字符串耦合”替换成“全局枚举耦合”。

#### E-03 存在可验证的旧 Bean、死方法和死参数

- **复核结论：** 采纳
- **证据状态：** 已复现

下列生产类型当前只在配置中注册或只被自身测试引用，没有主运行链消费者：

- `DynamicQuestionService`：455 行；
- `ConversationSubjectGuard`：34 行；
- `ConversationProgressClassifier`：99 行；
- `ConversationWindowManager`：101 行；
- `DeterministicConversationFallback`：288 行；
- `answer.service.DeterministicPortfolioAnswerComposer`：37 行，仅注册 Bean；实际 P4 组合使用 `answer.composition.service` 下的同名类型。

`ConversationIntentRouter` 当前甚至没有生产构造调用；Runtime 中的 `structuredSubjectBlocks()` 和 `intent()` 也只有声明，没有调用点。

`GeneralSemanticTaskExecutor` 构造函数接收 `ConversationDraftValidator`，但仅做非空检查，没有保存或使用。

这些是高可信的删除候选，但实施前仍需：

1. 运行 Spring context 测试确认没有按类型动态查找；
2. 搜索脚本、反射配置和测试 fixture；
3. 按小批次删除并运行完整门禁；
4. 不把仍被 `LocalRetrievalCoordinator`、evaluation 和 release CLI 使用的 `KeywordRetriever`、`VectorRetriever` 误删为旧栈。

外部评审将 `KeywordRetriever` 和 `VectorRetriever` 一并列入“语义运行时从不调用”的旧栈并不准确。它们仍由 `RetrievalConfiguration` 和 `LocalRetrievalCoordinator` 使用。

#### E-04 selection service 策略层是独立删除候选

- **复核结论：** 采纳
- **证据状态：** 已复现

`selection.service` 下的：

- `PortfolioSelectionService`；
- `SelectionStrategy`；
- `TopKSelectionStrategy`；
- `ExhaustiveSelectionStrategy`；

当前只被本包测试和 benchmark smoke test 使用，没有生产主链或 CLI main 消费者。

但不能据此删除整个 `selection` 包。`answer.intelligence.adapter.postgres` 仍实际依赖：

- `selection.domain`；
- `selection.gateway`；
- `selection.adapter.postgres`。

应删除或迁移的是孤立的 service 策略层，而不是仍服务 PostgreSQL 检索的 domain/adapter。

#### E-05 配置存在迁移性双轨

- **复核结论：** 采纳
- **证据状态：** 已复现

当前至少存在三层相关配置：

1. `portfolio.model-operations.turn-interpretation.*`：operation 是否允许；
2. `portfolio.turn-interpretation.*`：LEGACY/SHADOW/MODEL_LED 运行模式与专用超时；
3. `portfolio.conversational-agent.semantic-classifier-enabled`：旧 routing semantic assist alias。

`ConversationalAgentConfiguration:228-234` 会比较旧 `semantic-classifier-enabled` 与新 `ModelOperationPolicyRegistry`，不一致则启动失败。

运行模式与 operation policy 是两个不同维度，前两组并非完全重复；但旧 semantic-classifier alias 与新 operation policy 的确形成了迁移双轨。建议：

1. 标记 legacy alias deprecated；
2. 启动时只记录一次迁移提示，而不是永久保留双向冲突规则；
3. 设定删除版本；
4. 模型 provider/key 等共享字段最终统一由 Provider Registry 管理，operation 只保留 mode、timeout、schema 和 provider-ref。

#### E-06 异常归一化缺少可观测性

- **复核结论：** 采纳，并补入治理范围
- **证据状态：** 已复现

`SemanticTurnCoordinator.executeSafely()` 捕获所有 `RuntimeException` 后，只返回：

```text
EXECUTION_UNEXPECTED_FAILURE
```

当前没有在 Coordinator 层记录异常类型或发布诊断事件。`ShadowTurnRouter` 对队列饱和和运行异常同样静默忽略。

这保护了主请求和访客隐私，但会丢失区分以下问题的能力：

- Provider/数据库已知故障；
- executor 合同违规；
- NullPointerException 等代码缺陷；
- 队列饱和导致 shadow 缺样。

建议记录不含访客问题、模型原文和异常 message 的脱敏信息：

- executor/source domain；
- task type，不记录 task 参数；
- exception class 的受控名称；
- stable failure code；
- shadow submitted/dropped/failed/completed 计数；
- 当前剩余预算区间。

是否记录完整 stack trace 应按日志访问边界单独决定；不能直接记录可能携带 Provider 响应或用户文本的异常 message。

#### E-07 EvidenceSupportAssessment 对超限数据硬失败

- **复核结论：** 有条件采纳
- **证据状态：** 已复现

`EvidenceSupportAssessment` 对普通 criterion 最多允许 2 个证据单元，对 `PUBLIC_DELIVERY_EVIDENCE` 最多允许 5 个，超限直接抛 `IllegalArgumentException`。

这不一定是错误。如果对象代表“已经经过选择的封闭公开投影”，构造器拒绝超限结果能够暴露上游遗漏，不能静默截断并改变证据选择。

真正需要确认的是：

1. 上游是否在进入该构造器前稳定执行排序和限额；
2. 超限是否只可能是编程错误，还是公开内容正常增长也会触发；
3. 异常是否会被转换成可诊断的 task failure；
4. 限额是否属于领域合同并有集中常量，而不是只埋在构造器中。

因此建议不是简单改成 `stream().limit()`，而是把“候选可多、公开选择有上限”的边界显式化。

### 15.3 有价值但必须修正的建议

#### C-01 “默认主备是同一个对象，所以整个 fallback 都是空气”只对了一半

- **复核结论：** 部分采纳
- **证据状态：** 已复现

在 public database 默认关闭时：

- `primaryPortfolioCandidateRetrievalPort`；
- `fallbackPortfolioCandidateRetrievalPort`；

确实是两个 Adapter 包装同一个 `bundlePortfolioRetriever` Bean。

但 `RetrievalFallbackPolicy` 不只做 backend failover。对于 `HYBRID + VECTOR_UNAVAILABLE`，它会在同一个 Bundle backend 上把策略降级成 `KEYWORD`，该路径具有真实意义。

准确结论应是：

- 默认 Bundle 模式下没有第二个独立 backend，因此连接失败/超时意义上的后端主备不存在；
- 同 backend 内的 HYBRID → KEYWORD 策略降级仍然有效；
- 当前 Bean 命名把“策略降级端口”和“后端故障转移端口”混在一起，容易让读者误认为存在真正主备。

建议让默认 Bundle 配置显式表达单 backend 策略降级，不构造伪装成独立 backend 的 fallback Bean；PostgreSQL 模式再装配真正的 Postgres → Bundle failover。

#### C-02 evaluation/release/benchmark 位于 src/main，问题是制品边界而非死代码

- **复核结论：** 有条件采纳
- **证据状态：** 已复现

当前主源集规模约为：

- `evaluation`：79 个文件、7,271 个物理行；
- `release`：22 个文件、3,118 个物理行；
- `selection.benchmark`：22 个文件、1,311 个物理行。

它们没有 Spring 注解，也没有线上请求链路引用，但由 `run-eval.ps1`、发布和 benchmark CLI 使用。因此它们不是死代码，而是与线上 Runtime 同处主源集和默认发布物的工程工具代码。

由于位于 `src/main/java`，默认会进入 Spring Boot 发布物。是否拆 module 应用以下标准判断：

1. 线上 JAR 是否需要直接执行这些 CLI；
2. 发布/评测是否需要复用 package-private 或 main-only 实现；
3. 独立 tooling module 是否会引入不可接受的构建复杂度；
4. 发布物缩小、依赖隔离和启动扫描收益是否可量化。

更合理的目标是 `backend-runtime` 与 `backend-tooling` 两个 Maven module，或至少通过单独 classifier 生成工具 JAR。不能简单移入 `src/test`，因为这些 CLI 是可执行工程能力，不只是测试 fixture。

#### C-03 DAG 协调器对最多 6 个任务偏重，但不是可以立即删除的无效引擎

- **复核结论：** 有条件采纳
- **证据状态：** 已复现 + 静态推断

`SemanticTurnCoordinator` 确实实现：

- 稳定拓扑排序；
- 三种依赖语义；
- 绝对截止时间；
- 任务执行许可；
- 部分成功和预算耗尽。

计划最多 6 个任务，当前多数回合可能只有 1—2 个任务，因此实现复杂度与平均规模看起来不匹配。

但它并不等价于 Temporal：没有持久化、恢复、分布式调度、重试状态机和外部 worker。更重要的是，比较、综合和跨域任务已经需要依赖顺序与部分成功语义。

正确治理方式是：

1. 保留 DAG 领域语义；
2. 缩小 Coordinator 的执行与验证重复；
3. 用真实任务分布确认 3—6 节点计划是否存在；
4. 若长期只有线性 1—2 节点，再考虑将通用拓扑引擎收敛为固定阶段流水线。

不能只根据 `MAX_TASKS = 6` 直接删除依赖模型。

#### C-04 计划确认加密体量大，但性能不是主要问题

- **复核结论：** 保留机制，评审实现深度
- **证据状态：** 已复现

`JdkPlanCryptographyAdapter` 为 641 行，负责把完整计划序列化进 AES-GCM 信封，并附加 HMAC binding。外部评审正确指出，在“服务端无状态 + 计划不可持久化 + 防篡改”的约束下，该模式有明确安全价值。

但以下表述需要修正：

- 不是每轮回答都需要确认；只有 confirmation policy 要求确认的计划才增加交互往返；
- AES-GCM/HMAC 对这种小载荷的 CPU 开销远小于模型、检索和用户等待；
- 真正需要评审的是 641 行自定义序列化与版本兼容是否可由更小的 canonical plan codec 承担，而不是加密算法本身。

建议保留认证加密和无状态令牌，评估是否把计划 canonical serialization 从 crypto adapter 中抽出为独立、可版本化 Codec。

#### C-05 前端诊断准入链偏重，但不能只按个人网站规模删除

- **复核结论：** 待运行与威胁模型验证

`common.observability` 与 `common.web` 当前合计约 2,083 行，其中不仅包含前端诊断接收，还包含请求诊断、异常处理和 Web 过滤器。前端诊断入口默认关闭，并且对批次、body、速率和字段做限制。

它确实可能是超出当前流量规模的建设，但一旦公开开放匿名 POST 诊断接口，body 限额、速率限制和字段白名单并不是 SaaS 专属，而是最低限度的滥用与隐私边界。

应先决定：

1. 生产是否真的需要浏览器主动上报；
2. 若不需要，是否可以完全不注册 Controller 和 Filter；
3. 若需要，是否可以用 CDN/反向代理限流替代部分应用内机制；
4. 当前字段白名单是否可以由更小的 schema 驱动。

### 15.4 当前代码已经使部分外部结论过期或不成立

#### R-01 “几乎没有循环依赖”不成立

当前 answer 内部存在多组双向 package import，详见 A-04。粗粒度 common/portfolio/answer 边界相对清晰，不等于 answer 内部依赖单向。

#### R-02 “项目只有 1 个 SQL 审计项目和 1 个预置问题”已经过时

当前随包 `manifest.json` 明确记录：

- projects：6；
- questionPresets：19。

因此不能用“单项目、单问题”直接推导当前架构完全与业务规模失配。复杂度是否合理仍应根据真实请求种类、计划分布和公开能力判断。

#### R-03 “约三分之一 main 是死代码或无调用方基建”没有统一口径支持

当前可以确认约千余行旧 Bean、selection service 和局部方法/参数属于高可信删除候选；可以确认约 1.17 万物理行 evaluation/release/benchmark 属于线上请求链不调用、但被脚本或 CLI 消费的工具代码。

但工具代码不是死代码，且仅靠 import 搜索不能覆盖：

- CLI 入口；
- Spring 配置和反射；
- 脚本直接调用；
- JSON/Jackson 构造；
- 测试与发布工具的真实用途。

所以本文不采纳“三分之一”这个比例，也不把上述两类代码放进同一个“删除量”指标。后续应生成带分类的 reachability inventory，至少区分“可删除”“仅测试消费”“CLI/发布消费”“线上 Runtime 消费”，再决定删除或拆制品。

#### R-04 “7 组同名类”成立，前一版 6 组结论来自扫描遗漏

按同名 Java 文件复核，当前确有 7 组。前一版清单已经包含 `PortfolioAnswerMaterial`，实际遗漏的是分别位于 `ingestion.gateway` 与 `portfolio.release` 的两个 `DocumentEmbeddingPort`；补入该组后修正为 7 组。

同名不自动等于同一概念或错误抽象，例如 ingestion 与 portfolio release 的 `DocumentEmbeddingPort` 可能位于不同 seam；后续仍应逐组执行删除测试和依赖分析，不能仅凭名称合并。

#### R-05 “ModelLedTurnRouter 超过 1 个任务就要求拆分”已过期

当前 `ModelLedTurnRouter` 会把整个 proposal 交给 `ProposalCompiler`，`TurnProposal` 和 Validator 允许最多 6 个任务；当前聚焦测试也覆盖多任务计划。旧评审所述的单任务限制不再存在。

#### R-06 “GeneralSemanticTaskExecutor 调用 LLM 后才检查 operation 开关”不成立

表面上 Executor 在 `materialPipeline.generate()` 之后才再次检查 operation policy，但 `GeneralMaterialPipeline.generate()` 自身在调用 `modelPort.generateGeneralMaterial()` 之前已经检查：

- provider access；
- `GENERAL_ANSWER_MATERIAL` 是否 `ENABLED`。

因此禁用状态下不会先调用模型。这里存在的是重复检查和返回码语义不够清晰，而不是禁用开关失效导致额外 LLM 调用。

#### R-07 “模型失败后应回退完整 deterministicFastPath”与已确认设计冲突

`ModelLedTurnRouter` 当前在 Provider 失败后只允许 `MinimalTurnFallback` 的 reviewed alias 唯一主体概览，其余问题诚实澄清。

这不是遗漏，而是 `2026-08-16-model-led-agent-orchestration-design.md` 与 `2026-08-17-p1-p2-proposal-closure-design.md` 明确确认的安全策略：不保留第二套大型关键词语义引擎作为静默 fallback。

完整旧路由器作为回滚模式仍有价值，但它不应在单次 Model-led 请求失败时静默接管，否则：

- 同一句话在 Provider 正常和异常时可能被解释为不同计划；
- 旧关键词路由继续永久拥有隐性生产权威；
- 无法区分模型降级与正常确定性结果；
- 延长双路由栈寿命。

除非重新做产品决策，否则不采纳外部评审的这一建议。

#### R-08 根目录 AGENTS.md 的内容规模描述已经过时

- **复核结论：** 采纳二次评审的新发现
- **证据状态：** 已复现

根目录 `AGENTS.md:9` 仍写着：

```text
The current public content still contains one SQL audit project and one executable preset.
```

但当前发布 Bundle manifest 记录 6 个 Project、52 个 Case、19 个 QuestionPreset。由于 `AGENTS.md` 同时声明自己是 “Current repository authority”，这不是普通统计数字漂移，而是权威说明与发布事实冲突。

本轮不直接修改 `AGENTS.md`，因为需要先确认该句原本表达的是“最小事实范围”“初始 V0 快照”还是“当前内容规模”。阶段 0 应将其改成准确的当前边界，或明确标注为历史 V0 描述，并继续强调公开事实范围不能因模型能力扩大。

### 15.5 外部评审提出但应谨慎处理的“自家代码防御”观点

外部评审认为，校验 executor 返回的 `taskId/sourceDomain`、对内部参数做非空检查等只是“防自己写错”的噪声。

本文只部分同意。

`SemanticTaskExecutor` 是一个多实现 port。Coordinator 验证返回结果仍属于调用者保护接口不变量，能够防止：

- 新 executor 复制错误 taskId；
- source domain 装配错误；
- test fake 或未来插件返回不合法结果；
- 错误结果被归到另一个任务并参与依赖计算。

这类比较成本极低，删除收益很小，应保留。

可以清理的是：

- 同一私有调用链已经验证过的不变量反复再验；
- 仅为构造函数占位但从未使用的依赖；
- 重载互相转发时重复执行完整验证；
- 没有信任边界、没有第二实现、没有测试价值的一方法接口。

判断标准应是“是否跨越 seam 或不可信边界”，而不是“是否来自自家代码”。

### 15.6 外部评审对治理顺序的影响

吸收外部评审后，本文建议在原治理顺序中增加一个独立的“结构减负”工作包，但不改变 Canonical Answer 的最高架构优先级。

#### 可立即设计的低耦合清理

1. 删除已确认无消费者的旧 Bean、死方法和死构造参数；
2. 删除孤立的 selection service 策略层，保留 Postgres 仍依赖的 domain/adapter；
3. 清理未使用的旧 `DeterministicPortfolioAnswerComposer` Bean；
4. 修复配置 alias 双轨并设退役条件；
5. 为 Coordinator 和 Shadow 增加脱敏异常/丢弃指标；
6. 让默认 Bundle 拓扑明确表达“同 backend 策略降级”，不要伪装成独立主备。

#### 需要正式设计后实施的结构治理

1. Canonical Answer Projection；
2. reason code 领域类型化；
3. Runtime/Mapper/前端 ViewModel 的职责迁移；
4. stp-v1 Compatibility Adapter 与退役；
5. backend-runtime/backend-tooling Maven 边界；
6. answer 内部包依赖收口。

#### 需要数据后再决定的优化

1. DAG 是否简化；
2. 独立任务是否并行；
3. 前端诊断入口是否保留；
4. 计划确认是否在真实用户路径中过于频繁；
5. 大组件是否存在可感知渲染瓶颈。

### 15.7 综合复核结论

外部评审最值得采纳的不是“删掉三分之一代码”这个口号，而是以下三个补充视角：

1. **迁移必须包含删除预算。** 每个阶段不能只声明新增能力，还要列出被替代类型、Bean、配置和合同的退出条件；
2. **防御必须有拓扑语义。** 有 fallback 类不等于当前部署真的存在独立 fallback backend；
3. **失败关闭不能等于不可观测。** 可以不记录问题文本和 Provider 原文，但必须保留安全、聚合、可诊断的失败类别。

结合独立评审与外部建议，当前最准确的判断仍是：项目不需要推倒重写，但已经需要一次有明确删除目标的结构收口。收口的核心不是减少所有类型和校验，而是删除已失去消费者的旧栈、建立唯一公开答案投影，并让兼容、工具和容错机制回到各自清晰的边界内。
