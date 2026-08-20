# Agent 架构收敛设计
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

> **日期：** 2026-08-17  
> **状态：** D-01～D-47 已确认并完成独立代码证据评审修订；不构成源码实施授权  
> **范围：** Agent Turn 全链、Goal/DAG、Capabilities、Projection、State/API、Frontend、Privacy/Security/Eval 与结构减负  
> **性质：** 持续更新的架构设计；不是实施计划、提交授权或完成声明
> **评审修订：** 2026-08-18 关闭答案快照隐私边界、并发/Store命名、Settlement失败重试、死代码归属和陈旧交叉引用；实施任务见 `2026-08-18-agent-architecture-convergence-implementation-plan.md`

## 1. 文档目的

本文用于在逐项架构讨论过程中即时保存已经确认的设计决策，避免长对话中的上下文丢失，也避免把评审建议、讨论选项和用户决策混为一谈。

本文遵循以下记录规则：

1. 只有状态为 `CONFIRMED` 的条目是已经确认的设计约束；
2. `OPEN` 表示仍在讨论，文中的倾向或推荐不代表用户已经批准；
3. `REJECTED` 表示已经明确不采用，并记录原因；
4. 每个新增模块必须同时说明替代和删除什么，禁止只增加新层；
5. 协议兼容必须有真实消费者、退出条件和删除时间点，禁止把迁移态写成永久架构；
6. 本文与当前代码不一致时，必须区分“目标设计”和“已实现事实”，不得把目标误写成现状；
7. 设计已经用户逐项确认并完成独立评审，但仍不自动授权源码实施；只有用户明确要求开始某个 Replacement Slice 后才执行对应代码变更。

相关材料：

- `docs/14-Agent架构债与防御性设计评审.md`：问题证据、风险和候选治理方向；
- `docs/superpowers/specs/2026-08-10-semantic-turn-routing-design.md`：当前 Semantic Turn 与 DAG 基线；
- `docs/superpowers/specs/2026-08-16-model-led-agent-orchestration-design.md`：Model-led、v3 与后续阶段目标；
- `docs/superpowers/specs/2026-08-17-agent-backend-deterministic-routing-closure-design.md`：当前确定性路由和公开投影收口背景。

## 2. 当前事实基线

截至本文创建时，当前工作树具有以下事实：

1. `stp-v1` 是兼容合同；请求不声明 `agentTurnContract` 时默认解析为 v1；
2. `stp-v2` 是当前合同，前端生产工作区默认请求 v2；
3. 当前未提交工作树已经开始增加 `stp-v3`：后端接受 v3、`AgentTurnResponse` 增加 `interaction`，前端类型和显式 fallback 也已出现，但前端默认 writer 仍是 v2；
4. `AgentTurnResult` 同时保存 `requestUsesStpV1` 与 `requestContract`，说明网络合同版本已经泄漏进内部结果模型；
5. `ConversationAnswerResponseMapper` 会根据 v1/v2 重新决定公开 resolution、正文、来源、支持、上下文和任务字段；
6. `ConversationAnswerResult` 已经具备承载最终公开答案的多数信息，不一定需要再新增一个大型 Canonical Answer DTO；
7. 当前文档状态仍表明项目尚未生产部署，尚未确认存在必须长期兼容的第三方 Semantic Turn 客户端。

本文后续设计必须以重新核实的部署事实为准。如果已经存在未记录的真实外部消费者，需要重新评估合同删除策略。

### 2.1 Semantic Turn 合同历史

#### `stp-v1`：首版语义轮次合同

- **设计时间：** 2026-08-10，见 `2026-08-10-semantic-turn-routing-design.md`；
- **首次生产实现：** 2026-08-11，提交 `b41e614`；
- **解决的问题：** 在既有 `/api/v2/answers` 上增加明确的 Semantic Turn 表达，使多任务计划、确认、澄清、计划变化、执行结果和已完成任务不再只能压缩进旧顶层 Answer 字段。

首版公开结构以 `agentTurn.disposition` 为核心，主要包括：

```text
agentTurn
├── contractVersion = stp-v1
├── disposition
├── displayPlan
├── planConfirmation
├── clarification
├── planChange
├── outcome
└── completedTasks
```

v1 从设计之初就保留旧顶层 Answer 字段，并要求这些旧字段由 `agentTurn` 单向投影，不能反向修改语义轮次。它是从旧单回答合同迁往 Semantic Turn 的第一座桥，不是独立执行内核。

#### `stp-v2`：P5 多源履约与可信上下文合同

- **正式设计时间：** 2026-08-13，见 `2026-08-13-agent-context-and-runtime-modes-design.md`；
- **生产实现与前端切换：** 2026-08-14，提交 `de7a6b4`、`ad143e5`；
- **说明：** `stp-v2` 字符串曾在 2026-08-11 的 v1 测试中作为未来/不兼容计划 schema 样例出现，但当时不是已启用公共合同；正式合同升级发生在 P5。

v2 解决的是 v1 无法安全表达的 P5 语义：

- Fulfillment Role；
- Synthesis 参数与跨域结果；
- 更精确的依赖语义；
- 主体绑定与 Task Status；
- Block/Task 来源域和支持摘要；
- Context/Version 规则；
- 有序结果项身份与 continuation context；
- source composition 与 public source catalog。

v2 设计已经明确：v1 只做短期输入兼容，不保留旧执行主链，建议保留一个发布窗口后删除。当前实现未完成该删除，v1 仍是请求缺省合同和用户主动基础模式回退。

#### `stp-v3`：Model-led 交互状态与结果集续接目标合同

- **设计提出时间：** 2026-08-16，提交 `85dc71a` 及后续三轮评审修订；
- **当前实现状态：** 已提交设计把 v3 定为 P7 目标合同；当前本地未提交工作树开始增加后端/前端 v3 解析与映射，但前端默认 writer 仍是 v2，尚不能表述为已完成迁移。

v3 主要解决两个问题：

1. 交流恢复不能继续伪装成 `GENERAL_EXPLANATION` 或 `NOT_SUPPORTED`；
2. 推荐/比较结果需要通过 `RecentResultSet` 支持“第二个”“继续比较这些结果”等安全续接。

v3 使用唯一公开交互状态：

```text
interaction.kind
├── ANSWER
├── CONVERSATIONAL
├── CLARIFICATION
├── CONFIRMATION
├── BOUNDARY
└── CAPABILITY_UNAVAILABLE
```

目标是让内部 disposition 继续服务路由与执行，但不再作为第二个公共 UI 状态机。v3 同时计划传输短时、无状态、可验证的 RecentResultSet。

#### 版本号与 HTTP 路径不是同一件事

`stp-v1/v2/v3` 是当前 `/api/v2/answers` 请求和响应内部的 Semantic Turn Contract 版本，不是三个 HTTP endpoint。现有代码入口是：

```text
POST /api/v2/answers
```

因此删除 stp-v1/v2 不意味着机械地增加 `/api/v3/answers` 路由；它收敛的是 endpoint 内部的语义轮次 wire contract。HTTP 路径本身也已经决定在首次生产发布前改名，见 D-05，具体新路径仍待讨论。

## 3. 统一术语

### 3.1 Turn State

描述系统本轮处于什么流程阶段，例如：

- 正在形成或执行答案；
- 等待用户确认；
- 等待用户澄清；
- 进入边界响应；
- 能力不可用。

Turn State 不描述问题回答得是否完整。

### 3.2 Answer Resolution

描述用户目标最终得到何种程度的满足，例如：

- `COMPLETE`：全部必要用户目标已由可信公开结果满足；
- `PARTIAL`：至少一个必要用户目标得到满足，但仍有必要目标未完成；
- `NO_RESULT`：执行了回答尝试，但没有任何必要用户目标形成可公开结果。

`NOT_PRODUCED` 不是 Answer Resolution，而是描述确认或澄清轮次没有创建 `answer` 的概念状态，不进入 wire enum。

当前代码中的 enum 名称可以在实施设计中映射或调整；本节先冻结概念，不提前批准具体 Java/JSON 名称。

### 3.3 User Goal

用户本轮希望完成的可判定目标。一个问题可能包含事实、比较、推荐和综合等多个目标。Answer Resolution 按目标覆盖度计算，不按成功任务数量计算。

### 3.4 Task 与 DAG

Task 是实现一个或多个 User Goal 的执行节点。依赖边表示材料、成功或顺序约束。Task 成功不自动等于 User Goal 完成；辅助 Task 失败也不自动使 Answer Resolution 降级。

### 3.5 Answer Material

经过公开主体、证据、内容版本和安全规则验证，允许参与最终答案投影的材料。Preset 与普通任务可以使用不同材料入口，但进入最终答案前必须形成统一可验证的 Answer Material 语义。

### 3.6 Canonical Answer Projection

指“只进行一次最终公开答案决策”的职责，不预设必须新增一个名为 `CanonicalPublicAnswer` 的类型。

优先评估让现有 `ConversationAnswerResult` 成为 canonical result：

```text
AgentTurnResult
→ 唯一答案投影
→ ConversationAnswerResult
→ 机械 HTTP 映射
```

如果现有类型无法在不污染 interface 的情况下承担该职责，才讨论替代类型；禁止默认再叠加一个大 DTO。

## 4. 已确认决策

### D-01 DAG 节点失败采用局部传播与目标覆盖语义

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

一个 Task 节点失败时：

1. 直接阻断依赖该节点且依赖条件不再满足的后代节点；
2. 不影响与该节点无依赖关系的其他分支；
3. 不因单个 Task 失败自动判定整轮失败；
4. 最终 Answer Resolution 根据 User Goal 覆盖度计算，而不是按成功/失败 Task 数量计算；
5. 只要至少一个必要 User Goal 形成可信、可公开结果，整轮可以是 `PARTIAL`；
6. 如果没有任何必要 User Goal 形成结果，则为 `NO_RESULT`，不能伪装成部分回答；
7. 如果失败的只是非必要辅助 Task，而所有必要 User Goal 均已满足，仍可为 `COMPLETE`。

#### 示例

用户要求“比较 A、B 并给出推荐”：

- A 成功、B 失败：A 的可信材料可以展示，但比较与推荐不完整，结果为 `PARTIAL`；
- A、B 成功，非必要表达增强失败，但确定性输出仍满足全部目标：可以为 `COMPLETE`；
- A、B 均失败：`NO_RESULT`；
- A 失败导致依赖 A、B 的比较节点无法执行，但独立的 B 事实节点继续执行。

#### 对实现的约束

- 最终 resolution 不能仅从 `PlanOutcome` 或失败任务数量机械映射；
- Task 必须能够关联其服务的 User Goal 或 fulfillment role；
- DAG 依赖传播与最终用户目标覆盖属于两个不同计算阶段；
- 前端不得根据 completedTasks 自行重新计算 Answer Resolution。

### D-02 Confirmation 是流程状态，不是回答结果

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17
- **范围修订：** D-18 删除首次生产通用 Confirmation；本条只保留“等待用户操作不等于 Answer”的语义原则

#### 决策

当系统要求用户确认计划时：

```text
Turn State = WAITING_FOR_CONFIRMATION
Interaction = CONFIRM_PLAN
Answer = NOT_PRODUCED（概念状态；wire contract 中不创建 answer 对象）
```

此时系统已经理解用户请求，但尚未执行产生答案，因此：

- 不是 `NEEDS_CLARIFICATION`；
- 不是完整或部分回答；
- 不应携带普通回答正文、Evidence 或推荐结果；
- 可以携带待确认计划、确认挑战和允许的确认/调整操作。

#### 当前代码差异

`ConversationAnswerResponseMapper.publicResolution()` 当前将：

```text
stp-v1 confirmation → AWAITING_CONFIRMATION
非 stp-v1 confirmation → NEEDS_CLARIFICATION
```

该映射与本决策不一致。后续设计必须消除这种“协议版本改变业务事实”的行为。

### D-03 Preset 可使用独立材料入口，但必须进入统一答案投影

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

Preset 可以直接使用已审核、版本绑定的公开 claim/material，不要求伪装成普通自由问题检索任务。

但 Preset 不能绕过最终答案规则：

1. Preset 材料必须转换成统一 Answer Material 语义；
2. Preset 与普通 Task 结果必须进入同一个 Canonical Answer Projection；
3. 最终 blocks、resolution、evidence、source catalog、recommendation 和 continuation 只允许由该投影产生一次；
4. Response Mapper 和前端不得再为 Preset 建立独立正文权威；
5. Preset 的版本、审核和主体绑定继续在材料入口处验证，不因统一投影而削弱。

#### 对重构的约束

当前 Mapper 中的 Preset 顶层 blocks 特判只能作为迁移代码，必须有删除项。统一投影不要求统一材料获取方式，只统一最终公开语义。

### D-04 v3 是首次生产发布的唯一 Semantic Turn Contract

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

`stp-v1`、`stp-v2` 和 `stp-v3` 是同一系统在本次未发布重构中的三个演进阶段，不是要长期并行维护的三个产品版本：

1. `stp-v3` 是首次生产发布时唯一允许存在的 Semantic Turn Contract；
2. `stp-v1`、`stp-v2` 只允许作为本次未发布重构过程中的临时迁移脚手架；
3. 不发布 v1/v2/v3 三版本并存的生产状态，也不为尚不存在的生产消费者保留永久 fallback；
4. v3 设计中尚未开发完成、但属于已确认目标的能力继续完成，不能为了减少版本数量而把 v3 退化成 v2 的改名版；
5. v3 后端、前端、完整打包产物和关键行为全链验证通过后，删除 v1/v2，而不是把它们标记 deprecated 后无限保留；
6. 生产回滚采用应用/JAR/提交级整体回滚，不依赖 Runtime 内永久保留旧协议分支。
7. 稳态代码按业务语义命名，不把 `V3` 后缀扩散到 Runtime、领域类型和前端视图模型；`stp-v3` 只可作为 wire contract 标识留在协议边界。

#### “继续完成 v3”与“删除旧版本”的边界

v3 收敛包括两类工作，不能混为一谈：

- **补齐目标能力：** 完成 v3 已确认但尚未实现完整的交互状态、conversation/recovery、RecentResultSet 等能力；
- **清理迁移结构：** 在 v3 全链成立后删除仅服务 v1/v2 的 reader、writer、fallback、条件分支和测试矩阵。

只有前一类能力确实属于首发需求或已经确认的设计目标时才继续开发。不能借“补齐 v3”之名再增加一套与 v3 并行的状态机、答案 DTO 或 Mapper。

#### 必须删除的旧版本表面

v3 验证完成后，至少删除或改写以下内容：

- Runtime 和领域核心中的 `requestUsesStpV1`、`requestContract` 及按版本改变业务事实的分支；
- 后端 v1/v2 请求解析、响应 writer、compatibility policy 和 fallback；
- 前端 v1/v2 parser、fallback、旧类型联合和协议选择入口；
- v1/v2 fixture、按协议版本相乘的测试矩阵，以及把 v1/v2 描述为活跃合同的文档；
- 仅为旧合同保留的重复顶层 Answer 投影字段和兼容映射。

迁移期可以短暂存在编译性脚手架，但每个脚手架必须绑定删除条件，不得进入首次生产发布。

### D-05 首次生产发布前重命名回答入口

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17
- **路径定稿：** D-46

#### 决策

当前入口 `POST /api/v2/answers` 命名不再作为目标 API：

1. 首次生产发布前必须改成能表达“处理一个 Agent 轮次/交互”的新路径；
2. 新路径不应继续把 HTTP 路径版本 `v2` 与内部 Semantic Turn Contract 版本混在一起；
3. 因项目尚未生产部署，默认不保留旧 `/api/v2/answers` 路由兼容；
4. 后端 controller、前端调用方、契约测试、集成测试和文档在同一迁移中原子切换；
5. 如果实施前发现真实外部消费者，再单独评估临时网关别名，不因此污染 Runtime 或领域核心。

具体路径已由 D-46 定稿为 `POST /api/agent/turns`，并同步定义取消与 Conversation 资源。

### D-06 Agent Turn 使用闭合交互变体，只有回答变体拥有 answer

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

外层 Agent Turn 始终存在且必须可独立渲染，但不再强迫每一轮都伪装成 Answer：

1. `interaction.kind` 是公开交互变体的唯一判别字段；
2. `ANSWER` 变体必须拥有 `answer`；
3. `CLARIFICATION`、`CONVERSATIONAL`、`BOUNDARY` 和 `CAPABILITY_UNAVAILABLE` 从结构上禁止拥有 `answer`，但必须拥有各自可展示的专属 payload；本决策最初讨论过的 `CONFIRMATION` 已由 D-18 从首次生产范围删除，最终变体集以 D-38 为准；
4. `answer.resolution` 的闭集为 `COMPLETE / PARTIAL / NO_RESULT`；
5. `NOT_PRODUCED` 只作为设计语义描述“本轮未产生回答”，不进入 wire contract 的 Answer Resolution enum；
6. loading、streaming、网络失败属于客户端传输状态，不能通过 `answer` 是否存在推断。

#### 体验约束

- 没有 `answer` 不等于空响应；确认轮次必须返回说明、待确认计划和操作，澄清轮次必须返回问题或选项；
- `BOUNDARY` 和 `CAPABILITY_UNAVAILABLE` 必须返回原因和可恢复动作，不能只返回枚举；
- 用户多个目标中已有目标获得可信结果、其他目标仍需澄清时，根交互仍为 `ANSWER`，使用 `answer.resolution = PARTIAL`，并允许携带针对未满足目标的 clarification request；
- 执行后没有可信公开结果属于一次回答尝试，可返回 `ANSWER + NO_RESULT`，并展示尝试范围、原因和下一步；它不同于尚未执行的 confirmation/clarification。

#### 结构约束

最终 interface 应表达成闭合变体，而不是“一个大响应 + 大量 optional/null 字段”。每个变体只暴露合法 payload，使后端构造、序列化验证和前端类型收窄共享同一组不变量。

这项设计替换当前“顶层 Answer 永远存在，同时再附加 Agent Turn 状态”的双重权威；实施后应删除 `AWAITING_CONFIRMATION`、`NEEDS_CLARIFICATION`、`BOUNDARY`、`CAPABILITY_UNAVAILABLE` 等非回答值在 Answer Resolution 中的用途，而不是另加一套兼容枚举。

### D-07 以单一 Public Agent Turn Projection 替换现有回答迁移壳

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

`ConversationAnswerResult` 不作为长期 canonical model 继续扩充，而只视为迁移壳。目标架构使用一个版本无关的 Public Agent Turn Projection：

```text
版本无关的内部 Turn 执行结果
→ 单一 Public Agent Turn Projection
→ 闭合 PublicAgentTurn
→ 机械 HTTP 序列化
→ 前端按 kind 机械渲染
```

Projection 模块的外部 interface 只表达一次投影：

```text
project(internalTurn) -> publicTurn
```

它可以在 implementation 内部拆分目标覆盖、材料校验、来源聚合和展示构造等私有逻辑，但不得把这些策略重新暴露给 Runtime、HTTP Adapter 或前端调用方。

#### 唯一权威

以下公开事实只允许在该 Projection 中决定一次：

- 根 `interaction.kind`；
- `answer.resolution`；
- 公开 blocks/message；
- evidence、source catalog 与 verification；
- recommendation；
- degradation/caveat；
- continuation 与 RecentResultSet；
- 部分回答附带的 clarification request。

HTTP Adapter 只能做字段名、枚举值和 JSON 的机械映射；前端只能做协议校验、类型收窄和展示映射，不能依据 completed tasks、evidence 或 Preset 再计算业务结论。

#### 替换与删除

这不是在现有链路上新增一个 `CanonicalAnswer`：

1. `ConversationAnswerResult` 通过原位收紧/改名或一次性迁移被 `PublicAgentTurn` 替换，迁移完成后删除旧类型；
2. 删除它内部的 `contractVersion`、嵌套 `AgentTurnResult` 以及“每轮都必须是 Answer”带来的字段；
3. 删除 `ConversationAnswerResponseMapper` 中 `publicResolution()`、Preset 顶层 blocks 特判、推荐重建、来源重建和 task status 再解释等业务投影；
4. Mapper 若仍存在，只能作为很薄的序列化 Adapter；若不再提供隔离价值，则直接删除；
5. 删除顶层旧 Answer 重复字段和前端基于执行快照修正后端成功状态的防御逻辑。

`PublicAgentTurn` 是描述目标语义的暂定名，不要求最终 Java 类型必须使用该名字；不可改变的是“一个版本无关的闭合输出”和“只投影一次”。

### D-08 DAG 首发采用 ready-set 分批、有界并行

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

DAG 是 Agent Runtime 的核心执行模型，不能继续等价为“稳定拓扑排序后用 `for` 串行执行”。首发采用 ready-set 分批、有界并行：

1. 每一批只包含所有依赖已经结算、按依赖语义允许执行的节点；
2. 同批可并发节点在受控并发上限内执行；
3. 一批节点全部结算后，再计算下一批 ready set；
4. worker 只返回 Task Outcome，不并发修改共享 outcome map；Coordinator 按稳定 task order 提交一批结果；
5. 节点普通异常只结算该节点为失败，不取消无依赖分支；
6. 最终公开结果顺序不受线程完成顺序影响；
7. 不在首发实现“节点完成即唤醒下游”的动态 scheduler。

#### 未来演进

完成即调度的动态 DAG scheduler 作为明确的未来演进方向保留，但不是当前实现的预埋分支。只有观测数据证明 ready-set 批次屏障是主要延迟来源，并且当前取消、资源限流和确定性测试已经稳定，才提出替换设计。

未来升级应替换批次调度 implementation，保持 DAG 计划、Task Outcome 和 Public Agent Turn interface 不变；当前不增加双 scheduler、feature flag 或永久兼容层。

#### 本地参考项目的直接证据

对 `D:\code\hermes-agent-main`、`D:\code\opencode-dev`、`D:\code\DeepSeek-Reasonix`、`D:\code\deepseek-harness` 的源码核对得到以下可借鉴结论：

- **Hermes Agent：** 不是预计算 DAG。它对同一模型轮次的 tool-call batch 做保守并发：交互工具禁止并行，读工具进入白名单，路径工具只有目标不重叠时才并行；并发结果仍按原 tool-call 顺序写回。可借鉴“默认不安全、显式准入、稳定提交顺序”，不照搬工具名/path 启发式。
- **OpenCode：** 核心是流式会话循环而非 DAG scheduler。AI SDK 可以在事件流中执行工具，`SessionProcessor` 为每个 tool call 保存 pending/running/completed/error，并传递 AbortSignal。可借鉴“每节点拥有独立生命周期事实并实时投影”，不能把它当作 DAG 调度先例。
- **DeepSeek-Reasonix：** 同轮连续 tool calls 只有显式 `parallelSafe` 才进入并发块，默认上限为 3，可退回 serial；使用 `Promise.allSettled` 隔离单调用异常，并按声明顺序持久化结果。它最接近首发 ready-set 的并发纪律。
- **deepseek-harness：** workflow engine 使用 FIFO concurrency slots、总量/单次上限和统一取消；`parallel()` 明确是批次屏障，普通 child 失败转成局部 `null`，调度参数错误、基础设施故障和取消作为 fatal error 传播；另有无跨阶段屏障的 `pipeline()`。可借鉴“普通节点失败与致命 Runtime 失败分层”，并把 `pipeline()` 视为未来动态调度的概念参考，而不是搬入 worker/vm、插件和事件基础设施。

这些项目共同支持的是小而明确的并发纪律，不支持现在建设通用工作流平台。

### D-09 Executor 统一支持并发调用，不增加并发模式矩阵

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

`SemanticTaskExecutor.execute(context)` 的 interface 统一约定：同一 Executor 实例可以被不同、相互独立的 context 并发调用。

首发不增加：

- `SERIAL / PARALLEL_SAFE` 枚举；
- Executor 或 source-domain 并发白名单；
- serial barrier；
- 按 provider/domain 分层的线程池或 semaphore；
- 为假想串行实现预留的调度分支。

Coordinator 只拥有一个并发上限，即 D-42 的每 Turn `maxParallelTasks`；不再增加跨 Turn 的系统级 Task 配额，系统级资源只由 `maxActiveTurns` 保护。Executor 必须使用不可变依赖和调用内局部状态；底层 provider/retrieval Adapter 若有连接池、线程安全或速率限制要求，由拥有该资源的 Adapter 内部处理，不能让 DAG Coordinator 知道具体模型、数据库或 Provider 配额。

如果未来出现无法在自身 Adapter 内解决的真实串行资源，再用该实现和负载证据提出最小扩展；不能现在预埋模式矩阵。

#### 必要验证

该约束不是新增抽象，而是有界并行能够正确工作的前提。实施时需要并发调用现有 Executor 的测试，验证：

- Task 状态不跨 context 串扰；
- 一个调用异常不改变其他调用结果；
- 结果内容与提交顺序不依赖完成顺序；
- Adapter 自身的限流不泄漏成 Coordinator 业务分支。

### D-10 Turn deadline 必须真实停止等待并保留已完成分支

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

Turn absolute deadline 是用户可感知的真实总执行上限，不再只是“节点启动前至少剩 250ms”的准入检查：

1. 所有并行 Task 共享同一个 absolute turn deadline；
2. Executor 通过现有 `SemanticTaskExecutionContext` / allowance 获取剩余时间，不新增重复的 task timeout 参数；
3. 阻塞的 provider/retrieval Adapter 必须把剩余时间落实为真实 I/O timeout；
4. Coordinator 到 deadline 后停止等待，把未完成节点结算为 `TIMED_OUT`；
5. 依赖超时节点且条件不满足的后代按依赖语义阻断，无依赖的已完成分支保留；
6. 最终继续按 User Goal 覆盖计算 `COMPLETE / PARTIAL / NO_RESULT`；
7. deadline 之后返回的迟到结果不得提交到 Turn Outcome，也不得修改已经公开的结果；
8. Future cancel/线程中断只作为清理 backstop，不能替代 Adapter 的真实 I/O timeout。

所有 Task 共享 absolute deadline，不按任务数量平均切割固定墙钟时间。默认总时长尚未确认；现有 10 秒只是待通过实际模型、检索和体验数据验证的候选值。

### D-11 现有“取消回答”必须成为端到端 Turn 取消

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 现状证据

这不是为架构完整性新造的能力，而是补齐已经公开给用户的功能：

- `AgentWorkspace.vue` 已为每轮创建 `AbortController`，`cancelAnswer()` 会调用 `abort()`；
- `ConversationThread.vue` 已公开展示“取消回答”按钮；
- `answerApi.ts` 会把该 `AbortSignal` 传给 `fetch`；
- 单元与 E2E 已固定“取消后立即停止 loading、不显示错误、不追加 Agent 回答、迟到响应不得覆盖后续轮次”的体验；
- 后端当前是同步 Servlet 调用，浏览器中止 `fetch` 并不可靠地停止 `ProductionConversationService`、DAG Task 或下游 I/O；
- `AnswerIdempotencyCoordinator` 当前只有同请求 single-flight/结果缓存，没有正在执行的 `Future` 或 cancellation handle；
- 带会话请求在执行前领取 `RequestReceipt` lease，但现有 Store 只有 `complete`，取消后会残留最长约 30 秒的 `IN_PROGRESS` lease。

因此当前按钮实际表达的是“我不再等这个 HTTP 响应”，不是“停止这轮 Agent 工作”。在 DAG 并发后继续维持这种语义，会让已经不可见的模型/检索任务继续消耗资源，也可能产生迟到的 Context/Receipt 副作用。

#### 决策

1. 用户点击“取消回答”后，前端立即结束当前 loading，并使用独立、短时的请求向后端发送幂等取消命令；原回答请求随后中止，UI 不等待取消确认才恢复可操作；
2. 取消命令复用 D-30 统一后的 `requestId`，不再发明第二套 public execution id；具体路径由 D-46 定稿为 `DELETE /api/agent/turns/{requestId}`；
3. 后端只保留一个进程内 Active Turn 归属点。不得在 `AnswerIdempotencyCoordinator` 旁边再叠一个长期并行的 active registry；实施时应将现有 single-flight 协调职责重塑为一个更深的 Turn execution module，由它统一持有 in-flight entry、取消句柄和一次性终局结算；
4. `AnswerAdmissionGate` 继续只负责来源级频率/并发准入，`RequestReceiptStore` 继续只负责可恢复的持久化幂等边界，不把调度状态、Task 图或线程句柄塞进这两个组件；
5. Active Turn 只有一次原子终局：若取消先结算，则 Coordinator 停止接纳新节点，等待中节点与运行中 Adapter 接收取消信号，迟到结果被终局门丢弃；若公开完成先结算，后到的取消是幂等 no-op；
6. 取消信号与 D-10 的 absolute deadline 进入同一个 execution context。Adapter 必须能观察二者；线程中断和 `Future.cancel(true)` 仍只是 best-effort 清理，不冒充真实的下游取消；
7. 用户主动取消整轮时不产生 Public Agent Turn、不追加 Answer、不提交 Context、不写完成 Receipt；已领取的 receipt lease 必须显式放弃，admission slot 与 active entry 必须释放；
8. Task 内部允许记录 `CANCELLED` 以支持诊断，但不得为了公开显示它而伪造一个 `ANSWER/NO_RESULT`。用户已经主动终止这一轮，当前既有的“无错误气泡、无 Agent 回答”体验保持不变；
9. 被取消的 attempt 不用同一个 `requestToken` 自动复活；用户重试是新 attempt，使用新 token。

#### 明确不建设

首发不引入 SSE/WebSocket、事件溯源、持久化 DAG 调度器、分布式 worker 撤销协议或第二套取消状态机。当前 Runtime 是进程内执行，先用一个终局门补齐现有按钮的真实语义；未来若实际部署变成多实例且同一 Turn 能跨实例执行，再以部署证据设计共享执行归属，不能现在预埋。

### D-12 Task 只产生候选结果，Turn 只在唯一边界正式结算

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

重构目标是解耦、明确模块职责并让调用链可顺序阅读，而不是在现有调用链外再包一层抽象：

1. `SemanticTurnCoordinator` 只负责 DAG 调度、依赖判断和收集不可变 `TaskOutcome`，不得写 Conversation Context、Completion Receipt 或发布 Turn 终局事件；
2. 并行 Task 可以乱序完成，但 Outcome 必须按 Plan 的稳定顺序归并，公开结果不能依赖线程完成顺序；
3. D-07 的版本无关 `PublicAgentTurn` 直接充当待结算候选事实，不新增同构的 `TurnCandidate` DTO；
4. D-11 重塑后的唯一 Active Turn execution module 同时提供一次性终局门，不再另建独立 settlement framework；
5. 用户取消先通过终局门时，候选结果作废，不提交 Context、Receipt 或 Turn 完成诊断；
6. deadline 与用户取消不同：deadline 到达后用截止前已完成分支生成 `PARTIAL / NO_RESULT` 候选，该候选仍可正常结算；
7. 候选完成先进入正式结算时，后到取消为幂等 no-op；Context、Receipt、终局诊断与 HTTP 响应都读取同一份已结算 `PublicAgentTurn`，不得分别重算成功状态；
8. `semantic.turn.completed` 只能在正式结算后发布。Task/Adapter 级实时诊断允许在执行过程中产生，但必须表达 attempt/node 事实，不能冒充 Turn 已公开完成；
9. Conversation Context 继续按完成 Task 局部降级：某个 Context 保存失败不撤销其他独立成功 Task，已成功 handle 必须如实返回；只有一个 handle 都未保存时才是 `PERSISTENCE_UNAVAILABLE`；
10. 必须消除当前“前几个 Context 已写入、后一个写失败、外层却把全部 handles 清空”的隐藏部分提交。

#### 明确不建设

不引入事件总线、Saga、两阶段提交、事务编排框架或第二套公开结果模型。现有应用服务负责把候选结果送过唯一终局门；Coordinator、Context Adapter 和诊断 Publisher 保持单向被调用模块。

### D-13 首次生产只保留一条可顺读的 Agent Turn 调用链

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

稳态调用链固定为：

`HTTP Adapter → Application Lifecycle → Session/Input Resolution → Semantic Engine → Public Projection → Settlement → Mechanical Response Mapping`

各模块职责如下：

1. `AgentTurnController` 只负责 DTO 结构校验、读取 HTTP metadata、调用应用服务、应用错误到 HTTP 的映射和响应头；不得创建/查询 Conversation、授权 Context Reference 或决定 Receipt 恢复；
2. 唯一 `AgentTurnApplicationService` 负责 request single-flight、admission、absolute deadline、用户取消、一次性终局门及 Context/Receipt/终局诊断的结算顺序；主流程必须可以从上到下直接阅读；
3. `ConversationSessionResolver` 集中 ResumeToken 校验、新会话创建、已有会话恢复、Context Reference 会话归属和 Receipt 恢复身份；Controller 不再直接依赖 `ConversationBusinessContextStore`；
4. `TurnInputResolver` 集中 Request 到 `SemanticTurnInput` 的准备、Content Snapshot 绑定以及 Preset 查找、合同验证和主体绑定；不把同一 Preset 规则拆成多个只转发一次调用的薄类；
5. `SemanticTurnEngine` 只执行由 `GoalResolver -> SemanticPlanCompiler` 产生并验证的 Plan，通过唯一 ready-set execution kernel 调用 Task Executors，返回内部 Outcomes/Artifacts；不接触 HTTP DTO、Context Store、Receipt 或诊断 Publisher；
6. `PublicAgentTurnProjector` 是 interaction kind、answer existence、`COMPLETE/PARTIAL/NO_RESULT`、blocks、evidence/source、degradation、clarification 与公开 continuation 语义的唯一所有者；
7. `AgentTurnResponseMapper` 只做 `PublicAgentTurn → HTTP DTO` 的机械字段转换，不得再根据 confirmation、TaskOutcome 或协议版本修改 resolution、scope、正文和来源；
8. 只有真正拥有一组业务规则或不变量的模块才能独立存在；只包裹同名调用、没有决策权的 Facade/Manager/Service 不得创建。

#### 替换与删除

这次重构不是把新链路包在旧链路外。首次生产稳态删除：

- `ConversationalAgentRuntime`；
- `ConversationAnswerResult`；
- `ConversationAnswerResponseMapper`；
- Runtime 中全部 `projectedXxx()` 公开投影分支；
- Mapper 中全部 `publicXxx()`、fallback 正文选择和协议版本业务分支；
- Controller 对 `ConversationBusinessContextStore` 的直接依赖；
- `ProductionConversationService` 当前形态。

`ProductionConversationService` 的生命周期职责重塑为唯一 `AgentTurnLifecycleService`，不得同时保留旧 Service 兼容壳。稳态直接使用 `GoalResolver`、唯一 `SemanticPlanCompiler`、`SemanticTurnEngine`、`PublicAgentTurnProjector` 与 `TurnSettlement`；被 D-18/D-19/D-08 替换的 PlanConfirmationService、TurnRouter 和旧 Coordinator 不保留转发层。

### D-14 TaskOutcome 改为身份外壳与互斥终态

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

当前 `TaskOutcome` 的 `executionStatus + resolution + evidenceState + nullable payload/contribution/composition` 形成不完整的交叉状态机。稳态改为一个 Task identity 外壳和一个封闭终态，终态至少区分：

- `Produced`：产生一个可用 `TaskArtifact`，并标记该 Task 对自身目标的 `FULL / PARTIAL` coverage；
- `NoResult`：正常结束但证据不足、无候选、不支持或不适用；
- `Rejected`：输入或执行计划未通过可信边界；
- `Failed`：非预期执行失败；
- `Blocked`：依赖条件不满足；
- `Skipped`：未选择、预算不足或截止前未启动；
- `Cancelled`：用户取消；
- `TimedOut`：Turn deadline 到达时仍未完成。

这些终态可作为 `TaskOutcome` 同一文件内的 nested sealed variants，不拆成独立包、工厂体系或状态机框架。

#### 数据归属

1. `RUNNING` 是 Scheduler 的瞬时状态，不进入 immutable final outcome；
2. 删除 `TaskExecutionStatus + TaskResolution` 的交叉组合，调用方按一个终态分支处理；
3. 成功 Task 只携带一个 `TaskArtifact`，集中 result payload、provenance、composition、evidence 和 degradation；
4. `GroundedAnswerContribution` 不能继续作为 Outcome 上与 payload 竞争的候选正文；其“经过证据验证、尚未经过展示措辞”的有效职责按 D-15 归位为 `TaskSemanticResult`，与只负责公开表达的 Presentation 分开；
5. `TaskFulfillmentRole` 和 `TaskSourceDomain` 继续由 Plan 中的 `SemanticTask` 持有，不复制进 Outcome；Outcome 通过 `taskId` 与 Plan 稳定关联；
6. 删除无真实生产/消费职责的 `resultReference`；
7. reason code 保持内部稳定代码即可，不因旧 Mapper 的公开兼容要求扩大为第二套公共合同；
8. `SemanticTurnOutcome` 不再从 Task 状态计数推导公开 Answer Resolution；最终 `COMPLETE / PARTIAL / NO_RESULT` 继续按 D-01 的 User Goal Coverage 计算。

#### 删除目标

删除公共万能 `TaskOutcome.create(...)`、大量组合工厂、交叉字段 `validate()`、`hasRenderablePayload()` 的重复组合判断，以及 Mapper 中针对 `executionStatus × resolution` 的双层 switch。D-10 所需的真实 `TimedOut` 直接成为终态，不再错误映射成 `SUCCEEDED + CAPABILITY_UNAVAILABLE`。

### D-15 Produced TaskArtifact 明确分离语义结果与公开表达

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

一个 Produced Task 可以同时具有两种不同职责的数据，但二者不再作为可互相 fallback 的正文表示：

1. `TaskSemanticResult` 是 DAG 数据面，保存经过验证、与展示措辞无关的强类型事实、关系、候选、约束与缺口；只有它可以被下游 Task 消费；
2. `TaskPresentation` 是公开呈现面，保存 typed sections、summary、推荐呈现和用户可见内容；只有 `PublicAgentTurnProjector` 可以把它投影为公开回答；
3. Presentation 必须来自同一个 SemanticResult，不能反向覆盖或重新定义语义结果；
4. `TaskArtifact` 统一持有 SemanticResult、Presentation、唯一 provenance 与 composition/degradation metadata；允许某些 supporting Task 没有公开 Presentation，但不允许用渲染字符串冒充下游语义输入；
5. Portfolio fact 的 grounded statements/source/caveats/omitted topics 归入 SemanticResult；typed section blocks 归入 Presentation；
6. Recommendation 的完整 items、顺序、candidate scope、约束满足、requested/actual size 与 reason codes 是唯一 SemanticResult；推荐文案和 supporting sections 是 Presentation；
7. Synthesis 只消费 dependencies 的 SemanticResult，禁止继续读取 `getBlocks()`、`getRecommendation()` 或 `getSupportingBlocks()` 拼接展示字符串；
8. Synthesis provenance 只保留在 TaskArtifact，不在具体 result payload 内复制。

#### 删除目标

删除 `SectionResultPayload(List<String>)`、untyped section、Recommendation 的纯字符串/半结构化构造器、nullable `RecommendationProjection`、缓存的拼接 recommendation 字符串，以及 payload 内重复 provenance。旧测试 fixture 必须迁移到真实强类型构造，不以测试兼容为理由保留旧生产 API。

当前 `RecommendationProjection.equals/hashCode` 遗漏 `actualSize`、`candidateScope` 和 `reasonCodes` 是字段袋已经失控的直接证据；若该类型被新 SemanticResult 替换则随旧类型删除，否则实施迁移时必须先修复值语义。

### D-16 User Goal 成为 Plan 一等成员，Turn 按 Goal Coverage 结算

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

`SemanticTurnPlan` 显式保存按用户表达顺序排列的 `UserGoal`。每个 Goal 至少包含稳定 `goalId`、公开安全 label、语义 kind/subjects/requested outputs，以及唯一的 `fulfillmentTaskId`。

1. 每个 Goal 只有一个最终负责交付它的 Task；若完成该 Goal 需要多个节点，由一个合成/聚合 Task 作为 fulfillment task，上游支持关系继续由 DAG dependency 表达；
2. 一个上游事实 Task 可以同时是某个独立 Goal 的 fulfillment task，以及另一个合成 Goal 的 supporting dependency；合成失败时已完成的独立 Goal 因此仍能形成部分回答；
3. 不引入 Goal 权重、百分比、ANY/ALL policy DSL、多层 Goal 树或独立规则引擎；复杂组合由现有 Task DAG 表达；
4. 当前编译期 `GoalCandidate` 提升/替换为稳定 `UserGoal`，deterministic compiler 与 model-assisted proposal compiler 必须输出同一 Plan Goal 结构；
5. Plan 验证后，Runtime/Projector 不得再根据 task label、task type 或 PRIMARY 数量重新猜 Goal；
6. Goal Coverage 由其 fulfillment Task 的终态产生：`Produced(FULL) → FULL`、`Produced(PARTIAL) → PARTIAL`，其他非产出终态为 `NONE`；
7. 所有 Goal 为 `FULL` 时 Answer Resolution 为 `COMPLETE`；至少一个 Goal 为 `FULL/PARTIAL` 但并非全部 `FULL` 时为 `PARTIAL`；所有 Goal 为 `NONE` 时为 `NO_RESULT`；
8. 用户主动取消整轮仍按 D-11 不产生公开 Turn，也不计算公开 Goal Coverage；
9. Goal Coverage 由 D-13 的 `PublicAgentTurnProjector` 一次性计算，不新增第二个公开 resolution service。

#### 模型收敛

- 删除 `SemanticTurnOutcome.PlanOutcome`、`derivePlanOutcome()` 和 PRIMARY Task 计数驱动的公开成功判断；`SemanticTurnOutcome` 只保存稳定顺序的 Task Outcomes；
- `TaskFulfillmentRole` 不再是领域权威：是否为 fulfillment/supporting 可由 Goal mapping 与 DAG dependencies 推导；当前没有真实生产者的 `OPTIONAL` 一并删除；
- `goalLabel` 属于 `UserGoal`，不再复制到每个 Task。若执行计划需要人类可读 Task 名称，可保留纯展示 `taskLabel`，但不得用 label 建立业务关联；
- 前端若仍展示 `fulfillmentRole`，由 Public Projector 根据 Goal mapping 派生，不在 Plan、Outcome 和 DTO 三处重复存储；
- Task 成功/失败数量仅用于诊断，不进入 `COMPLETE / PARTIAL / NO_RESULT` 计算。

### D-17 首发 DAG 只表达真实数据输入依赖

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

首发执行图中的边只表达一件事：下游 Task 等待上游进入终态，并在上游 Produced 时接收其 `TaskSemanticResult`。不再用边同时表达展示顺序、成功策略或通用输入数量规则。

1. 删除 `REQUIRES_SUCCESS / USES_AVAILABLE_RESULTS / ORDER_AFTER` 三类交叉语义；首发只有数据输入边时，`TaskDependency` 本身即代表 data input，`TaskDependencyType` 可以整体删除；
2. 用户“先 A、再 B”的顺序只影响 User Goal、Plan Task 与 Public Presentation 的稳定顺序，不产生执行边，不把可并行独立任务串行化；
3. Coordinator 只等待 inbound Task 全部进入终态，并按 Plan 稳定顺序收集其中 Produced 的 SemanticResult；不得检查 Presentation 或 `hasRenderablePayload()`；
4. 没有 inbound dependency 的 Task 直接进入 ready set；inbound 尚未全部终结时继续等待；全部终结且至少一个 Produced 时把可用输入交给 Executor；一个 Produced 都没有时直接结算为 `Blocked(INPUT_UNAVAILABLE)`；
5. 具体 Task 自己判断可用输入是否足够产生 `FULL / PARTIAL / NoResult`。不在通用边上增加 minimum count、ALL/ANY 或 policy DSL；
6. Synthesis 的来源以 inbound data edges 为唯一权威，删除 `SemanticTaskParameters.Synthesis.sourceTaskIds`、两份来源一致性校验及 fingerprint/加密结构中的重复来源；
7. `DependencyOrigin` 可保留用于计划审计和确认策略，说明依赖来自用户明确表达还是 Compiler 推导；它不改变运行时数据传递语义；
8. 保留缺失 Task 引用、自环、重复边和 DAG 环检测。

#### 直接删除

删除 `hasAvailableResultsDependency`、`PLAN_SYNTHESIS_DEPENDENCY_MISMATCH`、用户顺序生成 `ORDER_AFTER` 链的代码，以及基于 renderable payload 判断数据依赖成功的分支。未来只有出现真实第二种执行边能力时才重新讨论 edge type，不为假设场景保留单值枚举。

### D-18 当前只读 v3 删除通用 Plan Confirmation

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 风险判断

当前 Semantic Tasks 都是读取、检索、组织和表达答案，没有写业务数据、发送消息、创建付费资源、删除内容或其他不可逆副作用。任务数量、混合来源、Compiler 推导依赖、宽主体范围和输出规模是系统自身的复杂度/资源问题，不是需要用户授权的行为。

当前自动确认让内部 Plan 离开可信服务端，经客户端回传后再执行，因此又建设签名、加密封装、TTL、版本绑定、过期重签和防篡改验证。该安全实现本身合理，但它保护的是一个可以通过删除无必要 Plan 往返而整体消除的威胁面；用户也无法通过简化任务卡有效审核内部依赖或证据正确性。

#### 决策

1. 合法、目标明确且在硬上限内的只读 Plan 在同一 Turn 内直接执行；
2. 主体、Goal 或关键约束有实质歧义时返回 `CLARIFICATION`，不让用户确认内部 Task Plan；
3. 超过硬 Goal 上限的请求提示拆分；仍在上限内的复杂请求使用有界并行、absolute deadline、输出预算与局部降级；
4. 节点部分失败按 Goal Coverage 返回 `PARTIAL`，全部无结果返回 `NO_RESULT`；
5. Task 数量、medium confidence、mixed source domains、inferred dependency、broad subject scope、large output scope、partial execution、order adjusted 和 node capability boundary 不再自动触发 Confirmation；
6. medium confidence 若会改变 Goal/主体则澄清，否则在安全边界内执行并保留降级/诊断；
7. 当前只读 v3 不生产 `CONFIRMATION` Turn variant。D-02“Confirmation 不是 Answer”的语义判断仍正确，但首次生产范围被本决策收窄；D-06 的闭合 variants 相应删除当前无生产者的 `CONFIRMATION`；
8. 未来出现真实有副作用 Action 时，只对具体 Action 做能力级批准，明确展示目标、影响、可撤销性和成本；不恢复“批准整个 DAG”的通用确认。

#### 删除目标

删除 `PlanConfirmationPolicy`、九类 `ConfirmationTrigger`、`PlanConfirmationService`、`PlanConfirmation` domain、`PlanCryptographyPort`、`JdkPlanCryptographyAdapter`、Confirmation Request/Response DTO、`CONFIRM_PLAN` action、过期重签与版本失效分支、仅为确认完整性存在的 fingerprint 结构、前端 `PlanConfirmation.vue` 及 plan confirmation/adjustment 测试矩阵。

若部分 Plan fingerprint 同时承担独立于 Confirmation 的审计或 Context 完整性职责，实施时只保留该最小用途并改名，不能把完整确认壳以“以后可能有用”为理由留下。

### D-19 自由文本只有一个解释权威，所有意图进入同一个 Plan Compiler

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

v3 首次生产时，自由文本由 model-led interpretation 作为唯一语义解释权威。模型只产生不可信的规范化 `TurnProposal`，不能直接产生可信可执行 Plan；确定性代码负责公开主体重绑定、参数规范化、Plan 编译和最终校验。

1. 自由文本只调用一个 Turn Interpretation Port，不再同时经过 deterministic router、semantic classifier 和 model-led router 多次解释；
2. 已审核 Preset、结构化 Context 和 provider 不可用时命中精确公开主体别名的 minimal fallback 可以绕过模型，但它们只负责产生同一种规范化 Proposal，必须进入同一个 Plan Compiler；
3. Preset 不再以“deterministic fast path”为名调用完整 Legacy Router；它应在 D-13 的 Input Resolution 阶段解析为 reviewed intent/proposal；
4. Global Boundary Gate 在解释前只执行一次，不在 Model-led Router 与 Legacy fast path 内重复判断；
5. 唯一 Plan Compiler 负责把 Proposal 重新绑定到服务端公开目录，验证 subject basis/evidence anchor、允许的 Task type 和参数约束；模型声明的主体、依赖和参数都不直接受信任；
6. Plan Compiler 与 Plan Validator 职责分开：Compiler 返回规范化 Plan 或明确的编译拒绝/澄清结果，随后只执行一次最终 Plan Validation；删除 Compiler 内部自建 Validator 与 Router 外部 Validator 的重复校验；
7. provider 失败的 deterministic fallback 严格限制在无需猜测的输入：Reviewed Preset、结构化已确认输入、精确公开主体别名。Provider/模型能力不可用且没有精确 fallback 依据时返回 `CAPABILITY_UNAVAILABLE`（可带重试动作）；只有主体、Goal 或约束存在可由用户补充消除的语义歧义时才返回 `CLARIFICATION`，不恢复第二套通用规则 Router；
8. 当前系统尚未首次生产，不建设永久在线 Shadow 迁移架构。路由替换通过离线语料/契约用例验证 Turn variant、User Goal、主体、Task 类型、依赖和拒绝边界；
9. 若重构期间短暂保留 Shadow，它必须产生可比较的规范化结果和明确删除门，且不得进入首次生产稳态。当前只调用 provider 后丢弃结果的 Shadow 没有迁移证据价值，直接删除；
10. 稳态不保留 `LEGACY / SHADOW / MODEL_LED` 运行模式矩阵。Model-led 是自由文本解释实现，不是可与旧 Router 永久切换的产品模式。

#### 删除与收敛目标

- 删除 `ShadowTurnRouter`、Shadow executor、线程数和队列容量配置；
- 删除 `TurnInterpretationMode` 及 `LEGACY / SHADOW / MODEL_LED` 分支；
- 删除 `DefaultTurnRouter` 作为完整并行 Plan 生产链，以及仅为它存在的 `SemanticSignalCollector -> SemanticPlanCompiler` 旧编译链；
- 删除独立的 `SemanticClassifierPort` 路由补丁和 `semantic-classifier-enabled` 旧配置别名；
- 将当前 `ProposalCompiler` 收敛/改名为唯一 Plan Compiler，禁止再出现第二套按输入来源区分的 Task/Dependency/UserGoal 创建逻辑；
- 保留 Turn Interpretation Port、受约束的 `TurnProposal`、公开主体重新绑定、唯一 Plan Validator 与 minimal safe fallback；
- 离线路由用例替代在线模式矩阵，至少覆盖自由文本、Preset、上下文指代、精确别名降级、无效主体、模型无效输出和 provider timeout。

### D-20 模型只解释 User Goal，确定性代码拥有 DAG 编排权

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

Turn Interpretation 的输出抽象停在用户语义层。模型提出闭合、受约束的 `UserGoalProposal`，不直接提出 `SemanticTask`、Task dependency 或执行 DAG。唯一确定性 Planner 根据 Goal、主体、约束和当前能力目录生成 `SemanticTask`、Goal fulfillment mapping 与真实 data input edges。

1. 执行型 Proposal 保存一到有界数量的 Goal proposal；每个 Goal 至少包含本轮局部 `goalKey`、goal kind、用户原文 anchor、subject candidates、requested outputs/表达深度和该 goal kind 的强类型约束；
2. `goalKind` 描述用户目标，例如作品概况、作品比较、岗位推荐、调整推荐、通用解释、通用比较和综合已有目标；它不是改名后的 `SemanticTaskType`，一个 Goal 可以由一个或多个内部 Task 交付；
3. 模型不得输出 `SemanticTaskType`、client task key、`TaskDependency`、`TaskDependencyType`、source task keys、fulfillment role、source domain、Plan ID、执行状态、工具、Provider 或调度策略；
4. Goal 之间若存在“综合前述目标”等真实用户语义关系，Proposal 可以使用 goal-local reference 表达语义关系；Planner 再将其编译为 Task data edge，模型不声明内部 Task edge；
5. reviewed Preset、结构化输入和 minimal fallback 同样产生 `UserGoalProposal`，与自由文本共用唯一 Planner；
6. 当前万能 `TaskProposal` 的十余个可空/空集合字段和 `validateTaskFieldMatrix()` 改为按 goal kind 闭合的强类型参数 variants；可使用同一文件内 nested sealed variants，不拆成大量类和工厂；
7. 严格 JSON 解码、未知字段拒绝、重复字段检测、长度/数量上限、原文 anchor 验证、公开主体重新绑定继续保留；这些是有效的非信任边界；
8. Proposal 外壳无法解析、未知 goal kind、数量越界或跨 Goal 关系损坏时，整次 interpretation 安全失败并进入 D-19 的有限 fallback；首发不建设从损坏 JSON 中逐项抢救 Goal 的半解析 Codec；
9. 主体或关键 Goal 存在真实歧义时返回 Clarification，不静默删除 Goal；
10. D-01 的节点局部失败从可信 Plan 形成后开始生效。模型合同损坏属于 interpretation failure，不伪装成 DAG Task failure；
11. “结构合法但语义错误”属于 interpretation 质量问题，由离线路由语料验证 Goal、主体、约束和关系，不以继续堆字段矩阵/Plan Validator 假装解决。

#### 删除与收敛目标

- 以 `UserGoalProposal` 取代当前暴露内部任务结构的 `TurnProposal.TaskProposal`；
- 删除模型合同中的 task/dependency/sourceTaskKeys，以及 Prompt 中七类内部 Task 字段矩阵；
- 删除 `validateTaskFieldMatrix()`、多级万能构造器和对应的空字段组合测试矩阵；
- 唯一 Planner 成为 `UserGoalProposal -> SemanticTurnPlan` 的编排权威；
- Plan Validator 只验证 Planner 输出的不变量，不再承担判断模型 Goal 是否语义正确的职责。

### D-21 首发 DAG 只保留三类有业务意义的拓扑

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

首发 Planner 不追求生成任意 DAG，只生成三类由当前真实能力支持的拓扑：单节点深模块、多个独立 Goal 节点、以及下游确实消费上游 `TaskSemanticResult` 的 fan-in。图的复杂度必须来自真实数据流，不来自用户措辞顺序、展示需要或“更像 Agent”的形式追求。

1. 单个作品概况、作品比较、作品推荐、推荐调整、通用解释和通用比较默认各自编译为一个深模块 Task；
2. `PORTFOLIO_COMPARE` 自己负责多主体检索与比较，`PORTFOLIO_RECOMMEND` 自己负责候选检索、约束排序和结果构造；不把模块内部步骤拆成 Fact/Retrieve/Rank/Compare 子 DAG；
3. 多个相互独立的 User Goal 编译为无边节点集合，进入同一 ready set 有界并行；稳定 Goal/Task 顺序只用于结果与展示排序；
4. 只有下游 Task 真正读取上游 SemanticResult 并产生新的语义结果时才创建 data input edge；首发唯一需要保留的 fan-in 场景是明确的跨领域关系合成；
5. 当前泛化 `SYNTHESIS` 收窄/改名为 `CROSS_DOMAIN_SYNTHESIS`。只有用户明确要求把通用概念与作品集事实建立关系、并且 Planner 已生成至少一个 GENERAL 与一个 PORTFOLIO 上游时才能创建；
6. Planner 必须按 Executor 的真实 capability 检查图形。禁止继续仅以“至少两个上游”生成执行器必然返回 `NO_SUPPORTED_CROSS_DOMAIN_RELATION` 的 Synthesis；
7. “总结一下”“给出结论”等只要求把多个已完成 Goal 组织成连贯答案时，由 `PublicAgentTurnProjector`/统一 Composer 完成，不创建新的 Task；只有需要推导跨输入的新语义关系才是 DAG 节点；
8. 推荐调整读取已授权的前轮 Context Reference，不把跨 Turn Context 伪装为当前 Turn 的 DAG edge；
9. Model-led 不再生成任意 1–6 节点拓扑；所有 Goal 均由 Planner 应用上述闭合规则；
10. 未来新增第四类拓扑必须同时给出真实数据消费者、Executor 能力、局部失败语义和它替换/扩展的现有规则，不能只增加 edge type 或通用编排 DSL。

#### 当前实现的收敛影响

- 删除用户“先/再/然后/最后”产生的 `ORDER_AFTER` 链；
- 删除模型 Proposal 中任意 dependency 生成能力；
- 删除泛化 Synthesis 对任意两个 Task 的编译规则；
- 将 `DeterministicSynthesisTaskExecutor` 的真实跨领域职责显式化并改名，或将其能力提升到新的明确需求后再保留泛化名称；首次重构按当前真实能力选择前者；
- Preset 仍按 Goal 编译为普通单节点深模块，不再通过 `presetRequest` 改变 DAG 执行/表达规则；
- 保留 Validator 的缺失引用、自环、重复边和环检测，防止 Planner bug，不建设任意图模板注册中心。

### D-22 Portfolio 节点删除单 Invocation 的二级 Plan 壳

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

Portfolio Task 内部当前只有一个固定、只读的 Evidence Retrieval capability 和一次 typed invocation，不构成第二级 DAG 或多能力计划。删除 `Planner -> ExecutionPlan -> Validator -> TrustedPlan -> unwrap` 的 plan-shaped wrapper，真实能力边界直接接收经过服务端授权和构造约束的 `PortfolioEvidenceInvocation`。

1. 当前 `PortfolioExecutionPlanner` 收敛/改名为 `PortfolioInvocationFactory`，职责仅为把已验证的 Portfolio SemanticTask 与 ExecutionContext 转换为一个强类型 Invocation；
2. Factory 负责 Task type/parameter 匹配、AuthorizedSubjectScope、facet/dimension profiles、推荐调整 Context 授权范围不扩张，以及 EffectiveRetrievalPlan 的确定性构造；
3. 删除永远要求 `invocations.size() == 1` 的 `PortfolioExecutionPlan`、`PlannedInvocation`、`PortfolioPlanValidator` 与 `TrustedPortfolioExecutionPlan`；
4. `TrustedPortfolioExecutionPlan` 当前不守在 Capability API 上，且创建后立即被拆开，因此不构成信任边界；可信约束落在 immutable `PortfolioEvidenceInvocation`、AuthorizedSubjectScope 和 Capability 实现；
5. 删除当前只保存一个硬编码 descriptor、且生产代码不按 descriptor 选择能力的 `PortfolioCapabilityCatalog`。只有真实出现多个可配置/可选择 capability 时才重新引入 registry/catalog；
6. 保留 `PortfolioEvidenceCapability` Port，封装 primary/fallback retrieval、Evidence Promotion、内容版本完整性和原子 Candidate/Evidence 结果；
7. 保留 `EvidencePromotionValidator`，检索候选不得绕过验证直接成为 Task SemanticResult；
8. Coordinator 是 Task 是否允许启动的唯一权威；未启动结算 `Skipped`，Turn deadline 到点由 D-10 的终局门处理；
9. Capability/Adapter 负责执行期 remaining deadline、backend attempt limit 和 I/O timeout；Invocation Factory 与静态 Validator 不读取 `Instant.now()`，不把运行期过期误报为 `PORTFOLIO_EXECUTION_REJECTED`；
10. 删除仅包装一个 `TaskExecutionAllowance`、没有独立语义的 `CapabilityExecutionConstraints`，Capability 直接接收 allowance/deadline budget；若未来出现独立的 capability constraints 再按真实字段引入；
11. 稳态类名删除 P2/P3/P4 实施阶段编号：例如 `P3PortfolioSemanticTaskExecutor` 改为 `PortfolioSemanticTaskExecutor`。阶段编号只存在于历史文档和迁移记录。

#### 目标调用链

`PortfolioSemanticTaskExecutor -> PortfolioInvocationFactory -> PortfolioEvidenceCapability -> ResultPolicy/TaskArtifact`。

这条链中每层都完成一次实质转换：Task 到授权 Invocation、Invocation 到验证后的 Evidence Result、Evidence Result 到 TaskArtifact；禁止再插入只复制/包裹同一对象的 Plan、Trusted 或 Constraints 类型。

### D-23 Retrieval 只保留一次按失败分类的有效 Fallback

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

Portfolio Retrieval 保留最多一次 backend fallback，但它必须是按明确失败类型选择的替代能力，不是“任何失败都再试一次”。Fallback 与 primary 共用 Turn absolute deadline，不得靠二次调用突破 D-10 的终局语义。

1. `HYBRID` 检索遇到 `VECTOR_UNAVAILABLE` 时，允许在同一后端降级为 `KEYWORD`；这是搜索能力降级，不是重复同一调用；
2. PostgreSQL 遇到连接不可用或 backend timeout 时，若配置了独立 Bundle snapshot 且 remaining deadline 允许，允许退回 Bundle；
3. `BUSINESS_EMPTY`、`EVIDENCE_INSUFFICIENT`、`CONTENT_VERSION_MISMATCH`、`INTEGRITY_FAILURE` 和 `BUDGET_EXHAUSTED` 不得 fallback；业务空结果不伪装成基础设施失败，证据/版本完整性标准不因 fallback 降低；
4. 每个 Portfolio Task 最多一个 primary attempt 和一个 fallback attempt。Fallback 开始前检查 remaining deadline，并把同一个 absolute deadline 传到真实 I/O timeout；
5. 删除数据库关闭时同时创建、实际永不使用且包装同一个 Retriever 的第二个 Bundle fallback Bean；Bundle 为 primary 时不存在 alternate backend；
6. Retrieval Adapter 只返回 `RetrievalAttemptResult`：原始 CandidateSet 或 closed attempt failure。它不执行 Evidence Promotion，不产生整个 Capability 的最终结果；
7. `PortfolioEvidenceCapability` 是唯一 Evidence Promotion owner：对最终采用的 CandidateSet 验证一次，随后产生最终 `CapabilityExecutionResult`；删除 Adapter 与 Capability 的双重 Promotion；
8. `CapabilityExecutionResult` 只表达整个 capability 的最终互斥结果，不再同时充当某次 backend attempt result；attempt failure 在 fallback 决策结束后不作为第二套状态轴残留；
9. Invocation/Route 只描述当前要执行的 retrieval intent、backend、strategy 和 content version。Fallback Route 在失败发生后由 Capability Policy 选择，不在 `EffectiveRetrievalPlan` 与 Spring primary/fallback Port 中重复声明两份拓扑；
10. 当前 `BundlePortfolioCandidateRetrievalAdapter` 实际同时包装 Bundle 与 PostgreSQL Retriever，改为中性的 `PortfolioRetrieverCandidateAdapter`；
11. Adapter 必须消费 remaining deadline/取消信号并约束真实数据库/检索 I/O。仅在调用前 `isExpired()`、但调用中忽略 constraints，不算 deadline 支持；
12. 最低 fallback 启动窗口先保留一个简单有界规则，精确阈值由真实耗时数据调整；不建设耗时预测器或自适应重试框架。

#### 保留的有效边界

保留 primary/alternate Retriever ports、失败分类、一次 fallback policy、AuthorizedSubjectScope、CandidateSet 原子性、Evidence Promotion、ValidatedEvidenceBundle 及 degraded 标记。删除的是重复验证和双权威，不是删除真实可用性降级。

### D-24 Portfolio 成功链只构造一次语义结果与一次公开表达

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

Portfolio Task 从验证后证据到 Produced TaskArtifact 只允许两次实质转换：一次构造与展示措辞无关的强类型 SemanticResult，一次从该 SemanticResult 构造公开 Presentation。不得继续把同一结果依次复制为 Material、Contribution、AnswerPlan 和 ResultPayload，并让不同消费者任选其中一种作为正文。

1. 保留 `EvidenceSupportAssessor` 的有效领域职责：按 Fact facet、Comparison dimension、Recommendation constraint 判断 `SUFFICIENT / PARTIAL / INSUFFICIENT`，选择受支持的 evidence units 并记录 omitted topics；可改为更准确的 `PortfolioSupportEvaluator`，但不并入 Executor；
2. 用一个公开入口 `PortfolioSemanticResultFactory` 替换 Executor 持有多个 `PortfolioResultPolicy` 并自行 switch 的形态。Fact、Comparison、Recommendation 的主体归属、受控谓词、排序和支持映射算法继续保留为 Factory 内部/package-private 的真实构造算法；不把它们拆成可运行期插拔的策略体系；
3. 当前 sealed `composition.domain.PortfolioAnswerMaterial` 的有效职责直接归位/重命名为 `PortfolioSemanticResult`，并成为 `TaskSemanticResult` 的 Portfolio 变体；不得在它外面再增加一层同义 wrapper；
4. 删除 `GroundedAnswerContribution`。其字符串 statements/source/caveats/omitted topics 是 `PortfolioAnswerMaterial` 的有损副本，不能继续作为 Outcome 上与 Presentation 并列的第二正文，也不能作为 Synthesis 的 Portfolio 专用输入协议；
5. General Task 同样产生强类型 `GeneralSemanticResult`。Cross-domain Synthesis 只消费依赖的 `TaskSemanticResult`，不得在某个来源缺少 semantic result 时回退读取 `getBlocks()`、`getRecommendation()` 或 `getSupportingBlocks()`；
6. Recommendation 的完整 items、稳定顺序、candidate scope、requested/actual size、content version、满足/未满足约束、partial reason codes 与 supporting grounded facts 全部由唯一 `PortfolioRecommendationResult` 持有；Executor 不得在 Composition 后重新从 CandidateSet 与 Assessment 构造第二个 `RecommendationProjection`；
7. `PortfolioAnswerComposition` 只把同一个 `PortfolioSemanticResult` 转换为 `PortfolioPresentation`。当前 `PortfolioAnswerPlan` 的有效 typed sections、summary 和公开内容职责归入该 Presentation；删除 Executor 中 `PortfolioAnswerPlan -> TaskResultPayload.SectionResultPayload` 的逐字段机械复制；
8. `TaskArtifact` 一次性持有 SemanticResult、可选 Presentation、唯一 provenance 及 composition/degradation metadata。Public Projector 只消费 Presentation；DAG downstream 只消费 SemanticResult；
9. Provenance 从被选中的 validated evidence/support units 一次构造。内部 claim/evidence identity 与公开 `PublicSourceReference` 明确分界：不得保留一套长期为空的公共 `claimIds/evidenceIds` 假装链路完整，也不得用 `referenceKey` 冒充 raw `evidenceId`；若内部 ID 不属于公开合同则从公开 DTO 删除，只投影稳定安全的 public references；
10. 删除无独立语义、完整委托给 Recommendation 的 `RefineResultPolicy`。Refine 的差异由 Proposal/Context 和 Recommendation SemanticResult 的约束表达，不用空策略类表示；
11. 删除旧 `answer.domain.PortfolioAnswerMaterial`、旧 `answer.service.PortfolioAnswerComposer`、旧 `answer.service.DeterministicPortfolioAnswerComposer` 及只为其保留的 Spring Bean/测试；它们是当前成功链之外的平行代际，不迁移到 v3；
12. 不以“减少类数量”为目标吞并真实算法。允许 Fact/Comparison/Recommendation 构造逻辑分文件，但对 Executor 只暴露一个 SemanticResult Factory，对外不存在多套结果权威。

#### 目标调用链

`ValidatedEvidenceBundle -> PortfolioSupportEvaluator -> PortfolioSemanticResultFactory -> PortfolioSemanticResult -> PortfolioAnswerComposition -> PortfolioPresentation -> TaskArtifact`。

其中 SemanticResult 同时保留给 DAG 数据面，Presentation 只进入 Public Projector；两者由 TaskArtifact 关联，但不得互相 fallback。该收敛是替换现有多代结果表示，不是在旧链上新增 SemanticResult/Presentation wrapper。

### D-25 确定性 Presentation 必达，模型表达只是一次可选原子替换

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

Portfolio SemanticResult 的确定性 Presentation 是 Production 必达的 canonical 表达，不是只有模型失败时才启用的次等 fallback。模型表达只在当前端到端支持的 Fact 场景中尝试一次原子替换；它不得改变 SemanticResult、Goal Coverage 或证据边界。

1. 先从同一个 `PortfolioSemanticResult` 构造完整 canonical deterministic Presentation，再判断是否允许模型表达；合法 SemanticResult 与合法系统预算必须总能得到 canonical Presentation；
2. 模型表达当前只支持中文、单主体、非 Preset 的 Portfolio Fact。删除尚无对应 Prompt、Draft schema、Validator、Assembler 与 Eval 的 Comparison/Recommendation 输入投影及 `allowedMaterialKinds` 伪泛化配置；未来按完整纵切能力一次加入；
3. 每个 Task 最多一次 expression provider 调用，不重试、不切换 provider，并把 Turn absolute deadline 的 remaining budget 传到真实 HTTP request timeout；保留当前 one-shot transport 的有效语义；
4. 保留严格 draft schema decode、statement alias grounding、section scope、public reference、固定 boundary 和字符预算校验。模型文本是非可信输入，只有整份 draft 编译成功才能原子替换 canonical Presentation；不得部分采纳损坏 draft；
5. 将重复的 `FactDraftValidator + ModelDraftPlanAssembler` 责任收敛为一个清晰的 grounded presentation compiler 边界：codec 只负责严格解码，compiler 一次完成 scope/grounding 验证和 Presentation 构造；不为“validated draft”再增加只包装同一对象的层；
6. Eligibility 只做两段判断：投影前判断功能/Task/attempt allowance，投影后一次判断输入大小与 remaining deadline 并立即调用。删除对同一条件的三次重复 evaluate；Adapter 仍以同一个 absolute deadline 约束 I/O；
7. 删除 Composition 私有、进程内硬编码阈值的 `ExpressionCircuitBreaker`。当前 canonical Presentation、单次调用、无重试和短 timeout 已提供局部降级；若真实生产数据证明需要熔断，应由统一 Model Provider Gateway 提供，并且只按连接、timeout、限流、5xx 等 provider failure 判定，schema/grounding failure 不得污染 provider 健康状态；
8. 模型调用、空响应、schema、grounding 或 presentation 编译失败时，返回 canonical Presentation 并记录内部诊断，但不设置 Task/Turn degraded、不降低 Goal Coverage、不向前端制造“回答能力下降”的信号；检索能力下降、证据缺失等真实语义降级仍按各自规则保留；
9. TaskArtifact 的公开稳定 composition metadata 只需区分 `DETERMINISTIC / MODEL_GROUNDED`。当前 `FALLBACK` 与十二种 `ExpressionDisposition` 不作为公共领域状态；失败原因只进入内容安全的内部 diagnostics；
10. 统一一个 Presentation character-count 口径。Canonical builder 超预算时按固定优先级移除完整的可选 detail/statement/section，并明确 omitted boundary，不从中间截断事实句；低于系统最小可表达预算的配置在 Turn/Task allowance 创建边界拒绝或归一化，不能在 Composition 中突然让 Produced Task 变成 Failed；
11. 模型操作启用、provider 与 timeout 只由唯一 `ModelOperationPolicyRegistry`/统一 provider 配置决定。删除 legacy `portfolio.model-expression.enabled` 冲突桥、重复 `expressionEnabled` 权威、仅允许一个固定值却仍暴露的 schema-version 配置，以及无状态空 `PortfolioExpressionStartupGuard`；
12. Composition 对外保持一个深接口：输入 SemanticResult 与执行 allowance，输出 Presentation 和最小 composition metadata。内部 projector、codec、grounded compiler 可以分文件，但不把诊断枚举、Provider 健康或旧 Plan/Payload 代际泄露给 Executor。

#### 降级语义

`MODEL_GROUNDED` 是可选的表达增强，未尝试或尝试失败都不改变回答语义。只有 canonical Presentation 自身因真实 SemanticResult 部分覆盖而缺少内容时，才由 SemanticResult 的 Goal coverage/omitted data 表达 `PARTIAL`；不能用“模型没有润色成功”推导公共 degraded。

### D-26 General 直接产生最小 SemanticResult，不照搬 Portfolio 重型模型

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

当前 `GeneralAnswerMaterial` 已经表达经过严格模型输出校验、尚未投影为公共 section 的 General 语义结果；它直接归位/重命名为 `GeneralSemanticResult`，成为 `TaskSemanticResult` 的 General 变体。不得在它外面再新增同义结果 wrapper，也不得生成后丢弃 Material、只保存渲染字符串。

1. `GeneralSemanticResult` 首发只保留真实消费者需要的 topic、稳定顺序 statements（role + text）、caveats 与 content version；General 不复制 Portfolio 的 subject ownership、claim/evidence/public-reference、candidate 或 recommendation constraint 模型；
2. 当前 `statementAlias` 只在 Draft 解码/校验阶段使用且无生产消费者，`conceptTags`、`discourseAliases` 无消费者，`publicSourceKeys` 被强制永远为空，逐 statement `supportKind` 又与 Task 的 GENERAL source domain 重复；这些字段从稳态模型和模型输出合同删除。未来只有在真实下游能力消费时才重新引入；
3. 用专用 `GeneralKnowledgeModelPort` 替换 General 对宽泛 `ConversationalModelPort` 的依赖。Port 接收 typed `GeneralKnowledgeRequest` 与 absolute deadline；Request 直接表达 explanation topic 或 comparison subjects/dimensions、depth、audience、expected content version；
4. 删除把结构化 Task 参数拼成 `"Depth: ...\nAudience: ..."` 的自由文本协议、永远为空的 `ConversationWindow`、带 Portfolio facet 的 legacy `ConversationRoute`，以及 audience 在文本和独立参数中的重复表达；
5. `GeneralKnowledgeGenerator` 是一个深模块：一次 provider 调用、strict draft decode、metadata/role/content validation，并返回闭合的 generation result。它不渲染 Presentation，也不返回 Material + Payload nullable 字段袋；
6. Generator/Model Adapter 必须消费 D-10 的 Turn absolute deadline，以 `min(configured operation timeout, remaining turn time)` 约束真实 HTTP I/O；不重试、不切换 Provider、不新增本地熔断；
7. Provider access、operation enabled 与 remaining deadline 只在 Generator/Capability 调用边界检查一次。删除 Executor 与 Pipeline 调用前后对同一 immutable operation policy/provider access 的重复判断；
8. Strict codec 与 semantic validator 保留；Validator 对所有 Draft 字段返回闭合 Valid/Invalid，非法 caveat 不得从 Stream 抛出未捕获私有异常。模型 Draft 是非可信输入，但防御结果必须稳定；
9. `GeneralPresentationRenderer` 只从 `GeneralSemanticResult` 产生 typed `GeneralPresentation`。TaskArtifact 同时保存 SemanticResult 与 Presentation；Cross-domain Synthesis 只读取前者，不再读取 `SectionResultPayload` 展示字符串；
10. 删除 Executor 中只 `requireNonNull` 但从未使用的 legacy `ConversationDraftValidator` 依赖，以及使用伪 content version `compatibility-general-v1` 的 compatibility overload；
11. Planner/Plan Validator 应保证只把支持的 General Task type 交给 Executor。运行期若仍收到不支持 type，按 plan invariant 处理，不映射成普通用户语义 NoResult；
12. Provider unavailable/timeout、非法模型输出或 metadata mismatch 属于该 General Task 的局部执行失败，不伪装成“用户问题不受支持”。其他独立 Goal 继续结算并可形成 PARTIAL；General 没有可靠本地知识源，不新增低质量 canned fallback；
13. General Task provenance 由 source domain GENERAL 与必要的生成 metadata 表达，不创建长期为空的 claim/evidence ID 列表，也不伪造 public sources。

#### 目标调用链

`SemanticTask -> GeneralKnowledgeRequest -> GeneralKnowledgeGenerator -> GeneralSemanticResult -> GeneralPresentationRenderer -> GeneralPresentation -> TaskArtifact`。

`GeneralSemanticResult` 的 statements/caveats 是 DAG 数据面；`GeneralPresentation` 是公开呈现面。模型生成 General 语义事实与 Portfolio 的“验证证据后可选润色”不同，因此模型失败允许该节点局部 Failed，但绝不通过读取旧 payload 或生成模板文字假装成功。

### D-27 Cross-domain Synthesis 只保留一个由 Goal 锚定的真实 Fan-in

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

首发 Cross-domain Synthesis 只支持一种已经有明确产品语义的能力：用户明确要求“解释一个 General 概念，并用验证后的 Portfolio 事实说明它在指定作品/场景中的应用”。关系意图来自 User Goal Proposal，并由确定性 Planner 把同一个受控 concept anchor 传入两个上游 Task；Synthesis 不在生成后的中英文文本中用 substring 重新猜关系。

1. User Goal Proposal 对该能力显式表达 `APPLY_GENERAL_CONCEPT_TO_PORTFOLIO`、公开安全的 concept anchor、目标 Portfolio subject 与所需 facet/output；模型只提出 Goal 语义，不生成 Task、edge 或关系事实；
2. Planner 为该 Goal 生成恰好一个 General supporting Task、一个 Portfolio supporting Task 和一个 fulfillment Synthesis Task。两个上游通过真实 data-input edges 指向 Synthesis，并围绕同一 concept anchor 构造请求；
3. Synthesis 的 dependency 来源只由 DAG 入边表达。删除 `SemanticTaskParameters.Synthesis.sourceTaskIds` 及其与 dependencies 的一致性校验、fingerprint/crypto/codec 复制；参数只保存 Synthesis 自己的 concept anchor 与目标约束；
4. 首发不接受 2～6 个任意 source 的伪泛化，也不使用 `first/last` 作为两个 alias、同时把中间输入混入全文/provenance。若未来出现多证据 fan-in，按真实消费规则扩展；
5. `GeneralSemanticResult` 提供与 concept anchor 对应的 definition/mechanism statements；`PortfolioSemanticResult` 提供经验证、与同一 anchor/subject/facet 对应的 grounded statements。Synthesis 只选择匹配 statements；任一侧没有匹配输入时产生 `NoResult`，不得拿其他内容硬拼；
6. 删除用 `ARCHITECTURE / IMPLEMENTATION` 等英文枚举名在中文输出中做字面 contains 的 `CrossDomainRelationPolicy`。当前它既不能证明语义关系，也会因语言不同产生大量假阴性；
7. 首发关系本身固定为“用 Portfolio 事实说明 General 概念”，不保留只有 `ILLUSTRATES` 有生产规则的六值 `RelationType` 预埋。出现第二种经过定义和验证的关系能力时再引入 closed relation kind；
8. Synthesis 产生独立 `CrossDomainSemanticResult`，至少保存 concept anchor、实际选中的 General statements、Portfolio grounded statements、必要 caveats 与 selected public support references；不直接产生渲染字符串 payload；
9. `CrossDomainPresentationRenderer` 从该 SemanticResult 构造 bounded typed Presentation，分清通用原理、项目实例与二者关系；删除把两边全文用破折号拼接并向用户显示 `(ILLUSTRATES)` 的 `DeterministicCrossDomainComposer` 当前形态；
10. Provenance 只聚合实际被 relation 选中的 Portfolio statement 支持与两个 source Task identities，不并集复制上游 Task 的所有 claim/evidence；General 来源继续表达为 GENERAL knowledge，不伪造 evidence；
11. Synthesis coverage 按自身 concept anchor/required relation 是否完整满足计算，不因任意上游 Task 对无关 facet 为 PARTIAL 就机械传播 PARTIAL；若所需 relation statements 完整存在，该 Synthesis Goal 可以 FULL；
12. 删除当前 `CrossDomainExpressionPipeline`、`CrossDomainDraftCodec`、`CrossDomainCompositionValidator`、`CROSS_DOMAIN_EXPRESSION` operation 及对应 Prompt/配置/测试。Validator 要求模型文本逐字等于 deterministic 拼接串，模型调用没有表达收益，只增加延迟和费用；
13. 未来若确需 Cross-domain 模型表达，必须遵守 D-25：canonical deterministic Presentation 先存在、一次有 deadline 的可选调用、真实 alias-grounded compiler、原子替换、失败只记内部诊断；不能恢复 exact-string echo 调用；
14. 删除 Executor 私有 `relationsEnabled` 双权威和 compatibility overload。Planner/Capability Profile 决定是否能够生成该 Task；依赖未 Produced 时 Task `Blocked`，依赖均 Produced 但关系 anchor 无支持时 Task `NoResult`，其他独立 Goals 继续形成 PARTIAL。

#### 目标调用链

`CrossDomain UserGoal -> General Task + Portfolio Task -> selected GeneralSemanticResult statements + selected PortfolioSemanticResult statements -> CrossDomainSemanticResult -> CrossDomainPresentation -> TaskArtifact`。

这个 fan-in 的复杂度只来自真实语义输入：同一 concept anchor、一个 General 原理和一个受证据支持的 Portfolio 实例。它不承担任意文本总结、任意多 Task 聚合或关系发现平台职责。

### D-28 Public Projector 按 Goal 输出唯一正文，不公开复制 DAG 内部结构

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

唯一 `PublicAgentTurnProjector` 按 `SemanticTurnPlan.userGoals` 的稳定顺序生成公共 Turn。每个 Goal 只读取其唯一 `fulfillmentTaskId` 对应的 final TaskOutcome/TaskArtifact；Supporting Task 不因自身拥有 Presentation 就自动进入公共回答。公共结果以用户 Goal 为单位，不以内部 Task 列表为正文单位。

1. `ANSWER` 变体只拥有一份 `answer`，其中包含 D-16 计算出的 `COMPLETE / PARTIAL / NO_RESULT`、有序 `AnswerGoalResult`、规范化公开来源、source composition 与后续动作；删除顶层旧 blocks、`completedTasks[].resultPayload` 与 Preset 特殊 blocks 之间的多正文权威；
2. 每个 `AnswerGoalResult` 至少表达稳定 goalId、公开 label、`FULL / PARTIAL / NONE` coverage、可选 Presentation、用户安全 notice 与可选 continuation；Presentation 为闭合变体，例如 Section、Recommendation、CrossDomain，而不是 `kind + 多个 nullable 字段` 的万能 payload；
3. Compound Goal 只公开 fulfillment Synthesis Presentation。其 General/Portfolio supporting Tasks 默认不公开；若某个上游 Task 同时是另一个独立 UserGoal 的 fulfillment task，则只在那个独立 Goal 下按 Goal 顺序公开一次；
4. Projector 不遍历“所有 hasRenderablePayload 的 Task”拼正文，也不在 Presentation 缺失时回退 Contribution、旧 top-level blocks 或 legacy result。Produced supporting artifact 可以没有公共 Presentation，不能因此改变 Goal 结果；
5. 推荐完整公开结果只存在于所属 Goal 的 `RecommendationPresentation`，包含 ordered items、requested/actual size、constraints/reason、supporting sections 与一次 continuation。删除顶层 `portfolioRecommendation`、completed task recommendation 和重复 context handle 的三份投影；
6. Public source 采用一个规范化权威：`answer.sourceCatalog` 保存 `referenceKey -> PublicSourceReference`，Presentation block/item 只携带 `publicSourceKeys`。删除 block 内完整 reference 与顶层 catalog 的重复对象、raw claim/evidence IDs 及 Mapper/前端再次去重；
7. `sourceComposition` 由 Projector 根据实际公开 Goal results 的 Artifact origin domains 一次计算；前端不从 blocks/sourceDomain 重新推导；Supporting 但未公开的 Task 不改变公共 source composition；
8. Goal 缺口只映射为有限、用户安全的 public notice，例如 evidence/capability/dependency unavailable、timed out、out of scope。内部 Task reason codes 进入 diagnostics，不直接排序透传给公共 DTO；
9. 默认 UI 显示 Goal coverage，而不是 answered/empty/blocked/failed/cancelled/degraded 的内部 Task 计数。单 Goal FULL 直接显示回答；多 Goal 或 PARTIAL/NO_RESULT 才显示简洁目标覆盖摘要；
10. 删除当前根据 final TaskOutcome 伪造“范围已确认/材料已取得/证据已核验/结果已整理”四阶段的 `ExecutionDisplayPlanProjector`。这些阶段没有真实事件支撑，并会错误地给 General Task 显示 Portfolio evidence validation；
11. 首次生产不把执行详情加入 PublicAgentTurn wire/UI；D-40 metrics/structured events 负责观察 Task terminal 与 blocked dependencies。未来若真实用户需求证明需要透明度，再以 additive field 提出最小 `ExecutionSummary`，且不得恢复 DisplayPlan/TaskSummary/CompletedTasks/Execution Snapshot 多份视图；
12. 删除前端 `hasExecutionAnswerConflict` 等根据执行视图重新判断答案成功状态的逻辑；未来任何执行摘要也不得覆盖 Goal Coverage 或 Answer Resolution；
13. 后端不再做 `Task status -> PlanOutcome -> Disposition -> Interaction Kind` 的多次公共翻译，前端也不把 v3 Kind 重新翻译回 legacy Disposition。Closed `PublicAgentTurn` variant 是前后端唯一 UI 状态 discriminant；
14. 前端 mapper 只做 closed variant、必填字段和公开引用结构校验；删除 Preset/top-level blocks/semantic sections/compatibility projection 的优先级选择、v1/v2/v3 分支和旧 task status 集合；未知或损坏 variant 进入一个明确 contract error，不回退到另一代正文；
15. 建议问题/动作只基于实际公开的 Goal results 产生；失败 supporting Task、内部 Plan label 或未公开 artifact 不生成公共 follow-up；
16. `NO_RESULT` 不伪造正文；其 Answer envelope 可以包含零 Presentation 的 Goal results 和用户安全 notice。Clarification、Conversational、Boundary、CapabilityUnavailable 继续作为与 ANSWER 互斥的 closed Turn variants，不借用 Answer 字段。

#### 目标公共结构

`PublicAgentTurn = AnswerTurn | ClarificationTurn | ConversationalTurn | BoundaryTurn | CapabilityUnavailableTurn`。

`AnswerTurn.answer = resolution + ordered goalResults + sourceCatalog + sourceComposition + suggestedActions`。可选 `executionSummary` 仅解释真实终态，不能成为第二个 answer/result authority。

### D-29 续接只保留会话凭证与服务端 Context 引用两级权威

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

`ResumeToken` 与 `ContextHandle` 职责不同并同时保留：ResumeToken 是访问一个 Conversation 的会话级不透明凭证，ContextHandle 是该 Conversation 内某个可续接 Goal Result 的不透明资源 ID。服务端 Context 是唯一业务状态权威；客户端不回传完整 Recommendation/Fact/Compare 状态。

1. 保留 ResumeToken 的高熵随机值、服务端 hash、Conversation 绑定与轮换；它只作为 credential 传输，不进入 diagnostics、Prompt、Task、Public source 或正文；
2. 保留 ContextHandle 的高熵不透明 ID、Conversation + ResumeToken 双重授权、TTL、content-version revalidation、result-item membership 校验和服务端 typed Context；持有 Handle 本身不构成跨会话授权；
3. 只为 D-28 实际公开、并声明 continuation capability 的 fulfillment `AnswerGoalResult` 在 D-12 唯一 Settlement Gate 提交 Context。Supporting Task、未公开 Artifact 和没有 continuation 的 Goal Result 不创建隐式 Active Context；
4. Context mutation 只从 TaskSemanticResult、授权 scope、selected result identities 与 Plan/Goal metadata 构造，绝不从 Presentation/rendered text 反向解析；Context persistence 失败不丢弃已经成立的 Answer，只令对应 continuation unavailable 并进入内部诊断；
5. 每个公开 Goal Result 只携带一个 `continuationRef`/bounded continuation action，核心为 `contextHandle` 与可选 `resultItemId`。删除 `CompletedTask.contextHandle + continuationContext.contextHandle` 双表示、公共 sourceTaskId 和前端二者不一致时的选择逻辑；
6. 删除客户端 `expectedContextType` 声明。服务端通过 Handle 查得真实 Context type，并根据请求 action/Planner intent 判断是否兼容；客户端声明不能成为授权权威，也无需与服务端类型重复比较；
7. 删除完整 `RecommendationContext` 客户端回传 fallback、`recommendationBatchId` 续接权威和 selectedPortfolioIds/capabilityCodes 的客户端状态回送。推荐 ContextHandle 已唯一定位 authorized scope、constraints/preferences/exclusions、ordered selected results 与 content version；
8. `resultItemId` 继续作为某个 Context 内有序公开结果项的显式选择，但必须由服务端验证属于该 Context；它不单独授权，也不携带 raw Portfolio/storage identity；
9. 明确 UI 操作（继续此结果、换掉第 N 个、解释此推荐项）总是发送 ResumeToken + ContextHandle + optional resultItemId + 新指令；普通新问题只发送 ResumeToken + 用户输入；
10. 无显式 Handle 时，服务端仅在恰好一个 compatible Active Context 时自动绑定。多个兼容 Context 时局部 Clarification；删除按 createdAt 选择 `MOST_RECENT_ACTIVE` 的静默猜测，避免用户不知情地继续隐藏/错误结果；
11. Recommendation refinement 的执行 Artifact 必须携带已经验证的 parent Context identity、不可扩张的 authorized parent scope、新约束与新 selected results。Settlement 据此创建 child Context；删除当前无论 parent 是否存在都返回 null、导致后续 refinement 永远落回原始 Context 的行为；
12. Child Context 只能缩小或保持 parent authorized scope，不得扩大到 all-published；连续 refinement 形成可审计的 parent chain，但不把整条链返回客户端；
13. 删除 Completion Receipt 的 singular top-level first ContextHandle。多 Goal 可分别产生 continuation；Receipt 要么按 goalId 返回 continuation refs，要么只通知完成并由客户端获取最终 PublicAgentTurn，不能从 Map 中任取一个 handle 代表整轮；
14. 当前 Fact/Compare/Recommendation active slots 的有效 typed selection 职责可以保留，但 slot 是服务端索引，不进入公共合同。稳态按 continuation capability/Goal result 建立，不依赖隐藏 supporting Task；
15. Context envelope 加密、token hashing 与数据库实现留在 persistence adapter；它们不扩散为 Planner/Task/Public DTO 的 crypto/version字段。是否启用 at-rest encryption 属于部署策略，不产生第二套业务模型。

#### 请求语义

- 普通新问题：`ResumeToken + UserInput`；
- 继续某个结果：`ResumeToken + ContextHandle + UserInput`；
- 继续某个推荐项：`ResumeToken + ContextHandle + ResultItemId + UserInput`。

ResumeToken 回答“能否访问该 Conversation”，ContextHandle 回答“继续哪个公开 Goal Result”，ResultItemId 回答“该 Result 内选择哪一项”。三者不互相替代，也不要求客户端回送服务端已经保存的 Context 状态。

### D-30 新 Turn 请求使用 Closed Command，不再靠 Optional 字段组合表达动作

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

新 Turn 请求使用一个公共 envelope 和三个 closed command：`ASK`、`CONTINUE`、`RESOLVE_CLARIFICATION`。`ASK` 的用户输入再闭合为 `FREE_TEXT / PRESET`。HTTP DTO 在反序列化/validation 后已经确定命令类型；Request Mapper 只做结构转换，不再推理 action 与十余个 nullable 字段能否共存。

1. 合并客户端 `turnId + requestToken` 为一个 UUID `requestId`，同时作为 Turn identity、幂等键、Active Turn 取消目标与日志 correlation ID；同一请求重试复用同一 ID，新问题/续接/澄清提交使用新 ID；
2. `AskCommand.FreeText` 只携带用户文本；`AskCommand.Preset` 只携带 presetId 与必要的 expected public content version。Preset version 是内容一致性约束，不是 Semantic Turn API 版本；
3. `ContinueCommand` 必须携带 D-29 的 ContextHandle、可选 resultItemId 与新的用户文本；它不回传 Context type、Recommendation scope、selected IDs、constraints 或任何服务端状态副本；
4. `ResolveClarificationCommand` 只携带 clarificationId 与 closed answer（choiceId 或 bounded text）。服务端在 Conversation 下保存短期 Clarification Challenge 并恢复 field、allowed choices、subject binding 与 blocked Goals；这是新增的短期State种类，首发只实现实际需要的单选与bounded text两种输入，不建设通用动态表单/规则引擎；删除客户端回显 promptCode、fieldKey 和 subjectReference 的 stateless 协议；
5. 删除 `CONFIRM_PLAN / REGENERATE_PLAN`、PlanConfirmationRequest、InvalidatedPlanReferenceRequest、PlanAdjustmentRequest、pendingPlanReference 与对应 action branches。D-18 后用户的“调整/换一个/只看某部分”是新的 Ask/Continue 文本，不提交 Plan DSL；
6. 删除请求级 `agentTurnContract`、`SemanticTurnContractPolicy` 和 `SemanticTurnInput` 的 v1/v2/v3 常量/兼容构造器。D-04 的唯一首次生产合同由部署/API schema 决定，不由每个请求选择；
7. 删除 `SemanticContextRequest + ConversationAnswerContextRequest + LegacySemanticContextAdapter` 双输入。服务端语义上下文由 authorized continuation、resolved clarification、surface hints 与 bounded conversation window 构造；
8. 只保留一个小型 `SurfaceContext`：可选 page subject hint、audience role 与 request source。所有 subject hint 必须重新对照当前 public catalog 解析；客户端 page/project/case ID 不是授权来源；
9. 删除客户端 `coveredTopics` 作为业务权威。当前 Goal coverage、历史 Context 与 Public Projector 由服务端维护；客户端可以保存纯 UI 展示状态，但不把它回送为 Planner 决策输入；
10. 请求可携带 bounded conversation window 帮助 model-led Goal interpretation 解析“它、第二个、继续”等指代。Messages 是不可信会话文本，只能用于意图/指代理解；不得授权 subject、产生 Portfolio fact/evidence、恢复 Recommendation scope 或覆盖服务端 Context；
11. Conversation window 保持条数/字符上限和基本 role shape 校验，但不要求它复制服务端业务状态；Portfolio 事实仍只能来自 authorized scope + validated evidence；
12. 稳态 application command 可以在同一文件使用 nested sealed variants，避免为每个三字段命令建设包/工厂体系；但每个 variant 构造后必须自身有效，不允许再出现 `kind=ASK` 同时携带 confirmation/clarification 的状态；
13. Cancellation 属于 D-11 的 Active Turn lifecycle command，按 requestId 定位，不作为 Ask request 中又一个 optional action；路径/方法由 D-46 定稿；
14. 简化请求 fingerprint：由 requestId 之外的 canonical closed command、surface hints、conversation window 与 continuation identity 构成。同 requestId + 同 fingerprint 为重试，不同 fingerprint 为 idempotency conflict；
15. 删除 `SemanticTurnRequestMapper` 中 legacy/new Context merge、contract policy、confirmation/adjustment conversions 与 compatibility aliases；Mapper 输出一个已闭合 `AgentTurnCommand`，Turn Lifecycle 按 variant 一次分派。

#### 目标请求结构

`AgentTurnRequest = requestId + command + optional SurfaceContext + optional bounded ConversationWindow`。

`command = Ask(FreeText|Preset) | Continue(contextHandle, optional resultItemId, text) | ResolveClarification(clarificationId, Choice|Text)`。请求入口不再表示已由 D-18/D-04 删除的 Plan Confirmation 与协议迁移状态。

### D-31 Turn Lifecycle 只有一个幂等权威与一个原子 Settlement

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

建立唯一 `AgentTurnLifecycleService` 与唯一 `TurnExecutionStore`。TurnExecutionStore 以 authorized Conversation + D-30 requestId + canonical command fingerprint 作为幂等/lease/terminal record 权威；Memory 与 PostgreSQL 是可替换 Adapter，不在一次请求中先 claim Postgres Receipt、再进入第二套内存幂等状态机。

1. 幂等 key 不包含 IP/source hash。Source/IP 只属于 transport abuse protection；同一 requestId 在客户端网络变化后仍必须保持同一业务身份；
2. TurnExecutionStore 使用少量 closed 状态：`CLAIMED/RUNNING/COMPLETED/CANCELLED`，以及可恢复的 lease expiry。它不是通用 workflow framework；只负责一个 Turn 的 claim、completion、cancel 与 replay；
3. 同 requestId + 同 fingerprint：RUNNING 返回 bounded retry-after，COMPLETED 精确重放同一个最终 `PublicAgentTurn`，CANCELLED 返回稳定取消结果；lease 到期后允许安全重新 claim；同 requestId + 不同 fingerprint 返回 idempotency conflict；
   - Settlement 失败但 Answer 已经交付时，State transaction 回滚，记录保持可恢复 lease 状态；同 requestId 在 lease 存续期返回 retry-after，lease 过期后重新执行并产生新的 Answer，不保证与先前已交付版本逐字一致。该例外只因当前 Agent 无外部副作用而成立；
4. 完成记录短期保存已经通过 D-28 Projector 校验、准备发送给客户端的 bounded PublicAgentTurn snapshot 与 continuation refs；不保存原始 Prompt、ConversationWindow、Task internal diagnostics、SemanticResult、raw evidence 或内部数据库 ID；
   - **边界修订声明：** 本条的分钟级、认证加密 PublicAgentTurn replay snapshot 是对 AGENTS.md 既有“不得持久化 answers”规则的定向扩展，只允许最终公开答案、不包含问题/ConversationWindow/Prompt/内部诊断，使用固定 absolute TTL且访问不续期。该扩展已获用户批准，并须同步更新 AGENTS.md、docs/08 与 docs/11；
5. 删除公共 `ANSWER | COMPLETION_RECEIPT` 联合成功响应。网络丢失后的同请求重试必须拿回原 PublicAgentTurn，而不是只得到“完成过但正文丢失”的 Receipt；Receipt/lease 可以作为 Store 内部实现，不成为第二种业务响应；
6. `AgentTurnLifecycleService` 的稳定顺序为：验证/授权 closed command -> claim Turn -> 注册 Active Turn owner -> Resolve/Engine 产生 immutable candidate -> Public Projector 产生 contract-valid candidate -> Settlement -> DTO；
7. Engine、Task Executor 与 Public Projector 不执行 Context/Receipt/Conversation side effects。所有 side-effect candidates 在进入终局门前保持 immutable；
8. Settlement 先从公开 fulfillment Goal artifacts 规划全部 typed Context mutations、分配 ContextHandles 并形成最终 continuation refs；同一 persistence backend 一次事务提交全部 Context mutations、final PublicAgentTurn snapshot 与 COMPLETED record；
9. 删除 `ConversationContextCommitter` 逐 Task/逐 handle save 的部分提交。任一 Context mutation 失败时不得留下前几个隐藏 Active Context；若 Context persistence 整体不可用，Answer 仍可返回，但不携带未提交 continuation，并记录内部 persistence diagnostic；
10. New ConversationId/ResumeToken 可以在请求开始时生成 tentative identity，但 session creation 必须纳入 claim/settlement 或有明确回收；限流、失败、取消的请求不提前留下无有效 Turn 的空 Conversation session；
11. D-11 Cancel 与 Complete 竞争同一个 TurnExecutionStore 终局门：Complete 先成功则取消报告已完成；Cancel 先成功则迟到结果不能提交 Context/PublicTurn/Completed record，且该 requestId 不重新执行；
12. 当前进程内 `AnswerIdempotencyCoordinator` 若用于 Memory Store，应成为该 Store 实现；Postgres Store 不再外套第二套业务幂等。可以有不改变 Store 状态语义的本地 single-flight 性能优化，但它不是另一个 claim/complete 权威；
13. `AnswerAdmissionGate`/IP rate limit 属于 HTTP/部署保护，不参与 request fingerprint、Answer Resolution、idempotency 或 cancellation。多实例部署需要 Gateway/共享限流；应用内实现只能声明为 per-instance best effort；
14. 应用核心保留 D-42 的 system-wide `maxActiveTurns` 与 D-09 每 Turn `maxParallelTasks`，分别保护请求并发和单图fan-out；不存在第三个跨Turn Task全局配额，也不与按来源RPM的transport rate limit混成业务Gate；
15. 删除 Controller 的 direct-call/source-compatible 多入口、`ProductionConversationService.answer/execute/findCompleted` 分叉、singular Receipt ContextHandle、先 complete Receipt 后 ResponseMapper 可能失败的窗口；
16. 旧 900+ 行 `ConversationalAgentRuntime` 不再被新 Lifecycle 外包一层继续保留。D-04/D-07/D-18/D-19/D-28 删除其版本、Preset 双投影、Confirmation、旧 blocks/resolution/degraded 映射职责，剩余 Resolve/Engine 能力进入已命名的深模块。

#### 目标生命周期

`HTTP -> AgentTurnLifecycleService -> TurnExecutionStore.claim -> ActiveTurn -> Resolver -> SemanticTurnEngine -> PublicAgentTurnProjector -> TurnSettlement.completeAtomically -> DTO`。

PublicAgentTurn 在 Settlement 前已通过结构/语义投影验证；Settlement 只决定 side effects 与 continuation refs 是否成功落地，不能重新计算 Goal Coverage、正文或来源。

### D-32 模型能力使用领域专用 Ports，共享基础 Structured Transport

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

稳态首发只保留三个真实模型能力及对应领域专用 Port：`GoalInterpretationPort`、`GeneralKnowledgeModelPort`、可选 `PortfolioFactExpressionPort`。领域模块依赖自己的 typed Port；OpenAI-compatible HTTP、Provider auth、JSON response mode、deadline timeout、错误分类与内容安全 diagnostics 由基础设施层一个共享 `StructuredModelTransport` 实现。

1. `GoalInterpretationPort` 输入当前用户输入、bounded conversation window、authorized continuation summary、resolved surface hint、public subject descriptors 与 allowed Goal kinds；输出 `UserGoalProposal` 或 Clarification Proposal，不再暴露 allowed Task types/TurnProposal/Task/DAG；
2. `GeneralKnowledgeModelPort` 输入 D-26 typed `GeneralKnowledgeRequest + absoluteDeadline`，输出 strict General draft/result；它是 GeneralSemanticResult 的内容来源，不携带 ConversationRoute/Portfolio facet；
3. `PortfolioFactExpressionPort` 只接收 D-25 approved Portfolio Fact semantic projection 与 absolute deadline，输出 strict grounded expression draft；它是可选 Presentation enhancement，不能读取 raw retrieval、Conversation history 或改变 SemanticResult；
4. 删除万能 `ConversationalModelPort` 及其 Legacy classify、free-form generate、review、cross-domain expression、suggest 等方法；模块不再因需要一种模型能力而获得全部模型操作；
5. 删除 `ROUTING_SEMANTIC_ASSIST` 与旧 semantic classifier/model route operations。自由文本只有 D-19/D-20 的 Goal Interpretation；不保留 Legacy/Shadow 辅助分类；
6. 删除 D-27 的 `CROSS_DOMAIN_EXPRESSION` operation、Prompt、Codec 和 Adapter；
7. 首发 Suggested Actions 由 D-28 Projector 根据公开 Goal results、当前 subject 与 reviewed manifest/preset 确定性产生；删除回答完成后的 model suggestion 调用及“必须凑满三个，否则抛异常”的耦合；
8. D-30 已提供 bounded conversation window，首发删除独立 Conversation Summary model operation/Port；直接按字符/token budget 选择最近消息。只有真实长会话数据证明需要时再作为独立能力加入；
9. `StructuredModelTransport` 是 infrastructure-only 边界，接收 provider、已经投影的 system/user messages、temperature/max tokens、structured JSON flag 与 absolute deadline，返回 raw content 或 closed transport failure；它不理解 Goal、GeneralStatement、PortfolioEvidence、Prompt schema、Draft 或 Public Answer；
10. 三个 operation Adapter 各自拥有 prompt projector、代码固定的 schema version、strict codec 与领域失败映射，然后调用共享 Transport；不得把领域重新收敛为 `modelGateway.execute(operation, Map)` 万能接口；
11. 所有模型调用统一以 `min(operation configured timeout, remaining Turn deadline)` 约束真实 HTTP request；remaining 不足不发调用。首发无 retry、provider switching、hedged request、私有 circuit breaker 或预测器；
12. Provider 配置只描述 providerId、HTTPS endpoint、model、credential 与 transport capabilities（structured JSON、thinking control、streaming 等）。删除 provider descriptor 中 `supportedModelPolicyVersions/supportedAnswerSchemaVersions`；应用 schema 是 Adapter/Codec 合同，不是 Provider 能力；
13. Operation 配置收敛为三项 `GOAL_INTERPRETATION / GENERAL_KNOWLEDGE / PORTFOLIO_FACT_EXPRESSION`，每项只保存 enabled/required、统一 typed providerId、timeout 与 maxOutputTokens；只有一个固定 schema 时不把 schemaVersion 暴露为可配置字符串再验证等值；
14. Production 自由文本能力启用时 Goal Interpretation 必须有可用 Port，否则启动失败或明确部署为 preset-only，不注册永远返回 DISABLED 的假 Port Bean；General availability 进入 Planner capability profile；Portfolio Fact Expression 缺失时直接使用 canonical deterministic Presentation；
15. Provider Registry 有多个真实 Provider 时可以保留，但 String providerRef 与 `ModelProviderKind` 合并为一个 provider identity；Operation 允许静态选择不同 Provider，单次失败不自动切换；
16. Transport 只能发送领域 Adapter 已投影的数据，不能自行读取 Conversation Store、Portfolio Store 或 TaskArtifact。共享基础设施不扩大任一模型 operation 的数据权限；
17. 统一 Transport diagnostics 按 operation/provider/failure class/latency bucket 记录，不包含 Prompt、用户文本、模型输出或 credential；schema/grounding failure由领域 Adapter 记录，不误报成 Provider transport failure。

#### 目标依赖

`Goal Resolver -> GoalInterpretationPort -> GoalInterpretationAdapter -> StructuredModelTransport`；

`General Executor -> GeneralKnowledgeModelPort -> GeneralKnowledgeAdapter -> StructuredModelTransport`；

`Portfolio Composition -> PortfolioFactExpressionPort -> PortfolioFactExpressionAdapter -> StructuredModelTransport`。

共享的是 Transport 与 Provider infrastructure，不共享领域输入合同、Prompt 权限或结果语义。

### D-33 按 Agent Turn 能力所有权组织深模块，不再按通用技术角色散包

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

稳态 Java 根模块从语义过窄的 `com.portfolio.agent.answer` 收敛为 `com.portfolio.agent.turn`，按 `lifecycle / planning / execution / capability / projection / continuation / api` 的业务所有权组织。删除顶层通用 `domain/service/adapter/gateway/intelligence/composition/runtime/mapper` 文件分类，避免一条能力横跨十余个包才能阅读。

1. `turn.lifecycle` 对外公开 `AgentTurnLifecycleService` 与真实持久化Port `TurnExecutionStore`；`ActiveTurnRegistry`、`TurnSettlement` 是Lifecycle内部/package-private实现。该模块负责 D-31 claim/active/cancel/complete 调用链，不理解 Portfolio evidence、Prompt schema 或 Presentation 细节；
2. `turn.planning` 公开 `GoalResolver`、`SemanticPlanCompiler`、`SemanticPlanValidator`，拥有 UserGoalProposal/UserGoal/SemanticTurnPlan/SemanticTask/TaskDependency；不依赖 Spring/HTTP DTO、Retriever 或 Capability internal implementation；
3. `turn.execution` 公开 `SemanticTurnEngine`、`SemanticTaskExecutor` SPI、TaskExecutionContext、TaskOutcome、TaskArtifact、TaskSemanticResult、TaskPresentation；负责 D-08～D-17 scheduler/deadline/cancel/terminal outcomes，不保存 Context、不生成 HTTP DTO；
4. `turn.capability.portfolio` 公开面尽量只有 `PortfolioTaskExecutor` 与真实外部 Port `PortfolioEvidenceCapability/Retriever`；retrieval/evidence/semantic/presentation/adapter 是模块内部结构，形成一条可顺读的深链，不再散落于全局 intelligence/composition/routing；
5. `turn.capability.general` 公开 `GeneralTaskExecutor` 与 `GeneralKnowledgeModelPort`，内部拥有 request/prompt/codec/validation/semantic result/presentation；不依赖 legacy ConversationRoute；
6. `turn.capability.synthesis` 公开 `CrossDomainTaskExecutor`，允许单向依赖 GeneralSemanticResult 与 PortfolioSemanticResult；General/Portfolio 不反向依赖 Synthesis；
7. `turn.projection` 公开唯一 `PublicAgentTurnProjector`，只依赖 Plan/Outcome/Artifact/Public model；不读取 Adapter、Prompt、retrieval candidates 或旧 ResultPayload；
8. `turn.continuation` 公开 Context Store/Resolver/Mutation Planner 及 ResumeToken/ContextHandle/typed Context；hash/encryption/postgres 是 adapter implementation，不把 crypto/schema/storage 类型泄漏给 Planning/Execution/Public DTO；
9. `turn.api` 只包含 Controller、D-30 closed request、D-06/D-28 closed response、结构 DTO mapping 与 HTTP error mapping；依赖方向仅 `api -> lifecycle`，核心模块不反向依赖 DTO；
10. 共享 `StructuredModelTransport`/provider infrastructure 放在清晰的 infrastructure model package，由三个 operation adapters 使用；数据库 Adapter 明确靠近其实现 Port 的模块归属，不再有同时容纳 web/model/retrieval/portfolio/observability 的全局 `answer.adapter`；
11. 不给每个内部类建立接口。Port 只用于外部模型、Retriever、Store、TaskExecutor SPI 和确有替换价值的系统边界；deterministic builder/codec/validator/ranking/projector helper 默认 final/package-private；
12. 每个深模块只公开少量入口、跨模块 immutable model 与真实 Ports。内部 Builder/Assembler/Codec/Validator/Policy 默认 package-private；Spring Configuration 组装模块入口，不允许调用者任意组合内部零件；
13. 配置按深模块拆分为少量 module configuration，例如 Lifecycle、Planning、Execution、Portfolio、General、Continuation、StructuredModel Transport；不按每个小类建 Configuration，也不保留 400+ 行中央配置；
14. 稳态类/包名删除 P1/P2/P3/P4/P5、stp-v1/v2/v3、Legacy、Compatibility、Shadow 与万能 Runtime/Facade 前缀。阶段号只存在于历史文档/迁移记录；新类必须替换并删除旧职责，不作为旧链外壳；
15. 允许 Synthesis 对 General/Portfolio semantic model 的真实聚合依赖，但禁止 planning→api、execution→api、capability→lifecycle、projection→adapter、context→routing internals、domain→service 等反向依赖；
16. 可增加一条简单模块依赖测试阻止明显反向 import；不建设自定义架构 DSL/plugin 或为目录美观引入独立 framework；
17. 实施按垂直能力迁移：新模块进入生产调用链的同一阶段，旧 `answer.*` 对应实现与测试必须删除；禁止创建 `turn -> answer` 永久转发层或把整个旧 Runtime 暂时包在新 Lifecycle 中宣称完成。

#### 目标模块依赖

`api -> lifecycle -> planning/execution/projection/continuation`；Capability 模块实现 execution SPI；Synthesis 单向消费 General/Portfolio semantic results；Infrastructure 实现 Model/Retriever/Store Ports。

包结构的目标不是文件数量最少，而是一个功能的主要代码物理靠近、入口少、内部细节不可随意跨包组合、依赖方向能从目录直接读出。

### D-34 重构按目标场景与垂直 Replacement Slice 推进

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

重构测试保护最终用户语义，不保护准备删除的迁移架构。先建立一组覆盖 closed Turn、Goal/DAG、Capability、Lifecycle 与 Public Contract 的目标场景和 backend/frontend 共享 PublicAgentTurn Golden Fixtures；随后按垂直 Replacement Slice 实施。旧 Characterization Test 只临时保护迁移，所属生产类型删除时同步删除。

1. 首发目标场景覆盖：六类 closed interaction、单/多 Goal、独立分支部分成功、真实 dependency blocking、Cross-domain fan-in、Portfolio evidence/fallback、General failure、canonical expression fallback、recommendation refinement、deadline/cancel race、idempotent replay、atomic context settlement、Goal order/supporting hiding/source catalog/NO_RESULT；
2. 公共合同只保留一套经评审 Golden Fixtures，例如 answer-complete/partial/no-result、clarification、conversational、boundary、capability-unavailable；Backend serialization、Frontend mapping/component 与 API 文档共享这些 fixtures，删除 Current/Target 与 v1/v2/v3 多套手写 fixture；
3. Characterization Tests 区分“长期产品行为”与“临时实现形态”。TaskResultPayload、Contribution fallback、CompletionReceipt response、PlanConfirmation crypto、阶段类名、Mapper instanceof、前端 compatibility projection 等实现测试随生产类型删除；
4. Planning 单元测试只覆盖 Goal Proposal→Plan、fulfillment mapping、真实 data edges、三类 topology 与 invalid Plan；不混入 HTTP/Spring/Presentation；
5. Execution 测试使用 fake clock、controlled executor/future 覆盖 ready-set、bounded concurrency、deadline、cancel、terminal variants、blocking 与稳定顺序；不靠真实 sleep 建脆弱并发测试；
6. Capability 测试分别覆盖 Portfolio retrieval/evidence/support/semantic/presentation、General strict decode/total validation/semantic/presentation、Synthesis anchor/selected provenance/no relation；
7. Projection 测试以固定 Plan + Outcomes + Artifacts 验证 Goal order/coverage/fulfillment-only presentation/source catalog/public notice/closed variant，不 mock Retriever/Model；
8. Integration Tests 只放在真实边界：StructuredModelTransport、PostgreSQL retrieval、Context/Turn settlement transaction、API serialization 和 Frontend contract mapping；
9. 每个 slice 开始前必须写 Replacement Manifest：新增/替换入口与模型、删除的旧生产类/配置/DTO/前端分支/测试/fixture；不能只列新增项；
10. Slice 完成门：新入口已进入唯一生产链；`rg` 无旧生产引用；旧测试/fixture 删除；无 Legacy/Compatibility forwarding adapter；公共合同不保留新旧状态；相关模块与目标场景通过；
11. 推荐阶段顺序由 D-47 细化为 Slice 0 基线 + 六个 Replacement Slices；具体分组、外部边界原子切换和 Exit Gate 以 D-47 及实施计划为准；
12. 阶段 5 不长期维护新旧 endpoint。按 D-46，Backend/Frontend/Test/Docs 同一切片切换到无版本 Agent 资源并删除 `/api/v2/answers`；
13. 测试不得成为保留旧生产 API 的理由。禁止 test-only compatibility constructors/aliases；fixture 应通过目标公共入口构造，而不是继续引用旧内部类型；
14. 记录迁移前后的 Java 文件数/LOC/public type/Bean/DTO 字段/双向 package imports/前端 fallback branches 作为复杂度护栏；不以数字为 KPI，不把多个职责粗暴塞进巨型文件或 Map；
15. 总体完成必须同时满足行为场景保留与权威/转换/依赖减少。若文件数下降但 God Class、万能 JSON 或前端业务推导增加，不算收敛；
16. 不在重构期间继续叠加新产品能力。未属于 Replacement Manifest 的功能进入后续 backlog，避免架构收敛与范围扩张同时进行。

#### Replacement Slice 原则

一个 slice 只有在“新权威进入生产 + 对应旧权威及其测试已删除”时完成。临时双结构可以存在于未完成分支内，但不得跨阶段、合并点或首次生产发布成为长期双栈。

### D-35 General Knowledge 只覆盖稳定低风险解释，不宣称证据验证

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

General Knowledge 首发只支持稳定、低时效、低风险的通用技术/工程解释；不建设外部 Web Retrieval，不回答必须依赖实时来源的信息和高风险建议。Strict JSON/Codec/Validator 只能证明模型输出结构与范围合法，不能证明 General statement 事实已验证。

1. `UserGoalProposal` 对 General 知识要求使用小型闭集：`STABLE_GENERAL_EXPLANATION / CURRENT_EXTERNAL_INFORMATION / HIGH_RISK_ADVICE`；这是 Goal interpretation，不是让模型决定事实真假；
2. Deterministic Planner 只为 `STABLE_GENERAL_EXPLANATION` 创建 General Task；实时版本/价格/新闻/法规等 external-current 请求和医疗/法律/投资等高风险建议进入 Boundary/CapabilityUnavailable，不偷偷降级为模型常识回答；
3. 首发 General 范围包括稳定技术概念、工程原则、架构模式基础解释及与 Portfolio Goal 相关的不依赖当前版本的背景；用户/Portfolio 个人事实永远不能由 General 模型产生；
4. `GeneralSemanticResult` 只表示 `STRUCTURE_VALIDATED + SCOPE_VALIDATED`，不使用 `FACT_VERIFIED/EVIDENCE_VERIFIED` 语义；内部可记录 `MODEL_GENERAL_KNOWLEDGE` origin，但不为每条 statement 重复 supportKind；
5. 删除 Prompt 只允许 GENERAL_KNOWLEDGE、而 Codec/Validator 又接受 NOT_PUBLICLY_VERIFIED 的双口径；D-26 后 General 模块本身就是统一 epistemic boundary；
6. General statement 不携带 PublicSourceKey，不允许模型生成 URL/文献/引用 ID，不借用 Portfolio source catalog 装饰通用陈述；没有来源比伪来源更可信；
7. Public support 明确区分 `GENERAL_KNOWLEDGE / VERIFIED_PUBLIC_EVIDENCE / DERIVED_FROM_TASKS`。General 不使用“已核验/证据充分”措辞；
8. `CrossDomainSemanticResult` 保留 statement 级支持：General statement 仍是 GENERAL_KNOWLEDGE，Portfolio statement 是 VERIFIED_PUBLIC_EVIDENCE，relation 是 DERIVED_FROM_TASKS；Portfolio citation 不得扩散为整个 General 段落的支持；
9. Cross-domain Presentation 分清“通用解释/项目中的已验证实例/二者关系”，source catalog 只支持实际带 public references 的 Portfolio statements/derived relation；
10. General Provider failure、timeout、invalid draft 或 scope violation 使该 Task 局部 Failed；不调用 legacy generate、不从上一轮 assistant text 或 Portfolio facts 生成模板 fallback。其他 Goals 按 D-01 继续形成 PARTIAL；
11. General eval 除 JSON 结构外，覆盖核心概念正确性、绝对化表述、经验判断伪事实、时效越界、高风险越界、Portfolio 个人事实、伪引用、合理 caveat 与 Cross-domain 支持类型隔离；
12. 未来若产品需要实时、有公开来源的 General Answer，作为独立 `ExternalKnowledgeCapability` 建设 retrieval/source/freshness/citation 边界；不把它隐藏在 `GeneralKnowledgeModelPort` 内作为自动 fallback。

#### 首发范围

允许“解释什么是 RAG/DAG/幂等/事件驱动”等稳定知识；不直接回答“今天最新版本/最近新闻/当前价格/法律医疗投资建议”。General 的可信承诺是受范围约束的模型解释，不是外部事实核验。

### D-36 删除公共 Degraded 轴，错误只分 API、Task Terminal 与 Goal Notice 三层

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

删除跨层和公共 `degraded` 布尔轴。检索替代路线、可选模型表达失败、证据部分覆盖、推荐数量不足和 Context 持久化失败是不同语义，不能汇总为一个“已切换到基础回答”。完整 Goal 即使内部使用允许的 fallback，也不产生公共降级提示。

1. 第一层 `HTTP/API Error` 表示未形成有效 Turn，例如 invalid request/credential、idempotency conflict、rate limit、body limit、admission reject 或 Turn ownership 前的 unexpected failure；使用 HTTP status + stable API error code，不伪造 PublicAgentTurn；
2. 第二层 `Task Terminal` 使用 D-14 closed variants：Produced/NoResult/Rejected/Failed/Blocked/Skipped/Cancelled/TimedOut，每个非 Produced 终态最多携带一个 typed internal reason，不使用任意 `Set<String>`；
3. 第三层 `Public Goal Notice` 由 D-28 Projector 根据 Goal coverage、SemanticResult omissions、Task terminal 与 Settlement capability 一次映射；内部 reason/provider/backend/taskId 不直接公开；
4. 首发 Goal Notice 闭集包括 evidence incomplete/no supported evidence、result set incomplete、capability/dependency unavailable、timed out、continuation unavailable、out of scope；Notice 使用 goalId，不使用内部 taskId；
5. HYBRID→KEYWORD、PostgreSQL→通过完整性检查的 Bundle 等 D-23 fallback 若最终产生完整 SemanticResult，只记录内部 retrieval path diagnostic，Goal 仍 FULL；fallback 导致证据缺口时公开的是 EVIDENCE_INCOMPLETE，不是 backend 路线；
6. D-25 Portfolio Fact Expression 未尝试或失败只选择 canonical deterministic Presentation，记录内部 expression diagnostic，不产生公共 Notice/degraded；
7. D-27 已删除 Cross-domain Expression，因此删除 `CROSS_DOMAIN_EXPRESSION_FALLBACK` 等预建公共 degradation kind；
8. Recommendation requested/actual size、unsatisfied constraints 与 incomplete reasons 属于 `PortfolioRecommendationResult` 领域数据，只形成一个 RESULT_SET_INCOMPLETE notice/限定说明，不再同时设置 reasonCodes、evidenceState、degraded 与 noticeCode；
9. Caveat 表达内容本身的适用边界并附着 Goal/block；Notice 表达执行/覆盖状态。二者不互相替代，也不把失败说明混进业务正文；
10. Internal diagnostics 可以保留 provider timeout/schema/grounding、DB/vector、fallback used、content mismatch、context commit、late result 等细粒度 code，用于 metrics/tracing/eval，不进入公共 reasonCodes；
11. 可预期业务/能力结果返回 closed value，不以 Exception 控制：business empty、evidence insufficient、provider unavailable、deadline、dependency blocked、invalid model draft；Exception 只用于不变量破坏和未分类基础设施故障；
12. Capability/Lifecycle 捕获未分类异常后记录 `UNEXPECTED_FAILURE` diagnostic 并映射 Task Failed；不得 catch-all 后伪装成正常 unavailable，从而隐藏程序 bug；
13. 删除 `TaskOutcome.degraded`、`SemanticTurnOutcome.degraded/degradedCount`、`TaskComposition.degraded`、`ConversationAnswerResult/Decision/Response.degraded`、PublicDegradationSummary/Kind/affectedTaskIds、`MODEL_UNAVAILABLE_FALLBACK` 及前端“已切换到基础回答”逻辑；
14. Public result 规则：全部 Goals FULL→COMPLETE 且无缺口 notice；部分 Goals 有产出→PARTIAL 并在对应 Goal 给具体 notice；全部 NONE→NO_RESULT；内部 fallback 但 Goals FULL→仍 COMPLETE；
15. Public Projector 是 internal reason→Goal Notice 唯一映射点；前端只渲染 closed notice，不根据 construction mode、fallback kind、Task count 或 execution summary重新判断“是否降级”。

#### 三层边界

`API Error` 决定请求是否形成 Turn；`Task Terminal` 决定 DAG 数据流和 Goal coverage；`Goal Notice` 向用户解释公开结果缺口。Fallback/adapter/provider 细节属于 diagnostics，不成为第四套公共状态。

### D-37 版本只分 Content Release、Preset Revision、Internal Schema 与 Deployment Identity

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

删除通用字符串 `version/contractVersion/schemaVersion` 在业务层的混用。稳态只承认四种不同语义：公开事实 `ContentReleaseId`、Preset 定义 `PresetRevision`、具体 Codec/Store 的 Internal Schema、配置/兼容/诊断用 Deployment Identity。仅跨模块且参与业务一致性的前两者建立 typed value；不建设万能 Version 类型。

1. `ContentReleaseId` 表示作品、案例、claims、evidence、Preset registry 的原子公开发布快照，沿用当前日期+revision 的 release identity；它回答“本轮使用哪版公开事实”；
2. AgentTurnLifecycle 在 Turn 开始时取得一个不可变 `TurnContentSnapshot`，包含 ContentReleaseId、public subject catalog、preset registry、capability-visible metadata 与 retrieval release binding；Goal/Plan/Execution/Portfolio/Synthesis/Public Projection 全程使用同一 snapshot；
3. Turn 运行中即使系统 hot reload，新旧 release 不得混用。固定 release 的 retrieval/backend 不可用或版本不匹配时该 Task 失败；不能切换到新 release 后继续同一 Turn；
4. `SemanticTurnPlan`/ExecutionContext 持有一次 ContentReleaseId/snapshot，PublicAgentTurn Answer 顶层公开一次 ContentReleaseId；删除 General statement、每个 Task parameter、Presentation block、Recommendation item、PublicSourceReference 等层层复制；
5. 当前 Preset `contractVersion` 改名 `PresetRevision`。保留其对 preset id/text/aliases/subject/claim requirements/evidence requirement/status 的 canonical hash；它只验证用户点击的 Preset 定义未变化，不证明 claim/evidence 内容或整个 release 未变化；
6. Preset Ask 输入为 presetId + presetRevision；服务端在当前 TurnContentSnapshot 中验证 revision 后，仍以当前 ContentReleaseId 重新执行 retrieval/evidence validation；`PresetContractSetHash` 只作为 release build/manifest 内部完整性检查；
7. General Draft 删除复制 Portfolio contentVersion 的 metadata echo。General Task 属于绑定 release 的 Turn，但 General 知识本身不来自 Portfolio release；Cross-domain 一致性由同一个 Turn snapshot 保证；
8. 同一 Portfolio Task 的 validated evidence 必须属于同一个 ContentReleaseId。D-28 source catalog 不在每条 reference 重复 publishedVersion；若未来 External Knowledge 有独立发布日期，使用明确 sourcePublishedAt/sourceRevision，不借用 Portfolio ContentReleaseId；
9. Context 保存 original ContentReleaseId 仅用于续接解释与 rebind。新 release 下 Fact/Compare 必须重新解析 subject并重新 retrieval/evidence validation；Recommendation item 先验证属于旧 result，再将稳定 subject ID 解析到当前 release；Refinement 使用当前 release 重新排名，不复用旧 evidence/排名；
10. 删除仅因字符串不等就标记 `REVALIDATED` 并 touch 的表面策略。Revalidation 必须由具体 Continuation Kind 真正执行；subject removed/incompatible 时产生 stale/clarification，不把 stored version 字符串直接改成 current；
11. 首次生产只保留一套 Context storage schemas，去掉 p3/p5 命名与双 Codec，例如 recent-context.v1/recommendation-context.v1/clarification-context.v1/completed-turn.v1；这些常量只存在于 Codec/Store；
12. Public bundle schema 仍是 loader/persistence 格式。当前资源迁移到最终 schema 后，是否保留旧 2.0/3.0 loader 只由真实历史 bundle 恢复需求决定，不与 PublicAgentTurn v1/v2/v3 绑定；
13. Goal Proposal/General Draft/Portfolio Expression schema 固定在对应 Adapter/Codec 代码；不进入 Provider Registry、Plan、Task 或 Public DTO；schema mismatch 是领域 decode failure，不是 provider capability；
14. Recommendation policy/profile identity 可以内部保存在 Context 用于兼容判断；同 policy 正常继续，可安全重建则用当前 policy 重跑，否则 stale。它不公开、不由客户端回传；
15. 删除 request `agentTurnContract`、response 内用于业务分支的 stp-v3 contractVersion、SemanticTurnContractPolicy 和前端版本 mapper。API schema 由一次发布的 wire contract/OpenAPI 管理，不在每个 payload 固定值上 switch；
16. Provider adapter/model/prompt/config identities只用于 startup compatibility、metrics 与 eval，不影响 Goal coverage/Public Answer，也不与 ContentReleaseId 比较。

#### 版本归属

- Public Answer：一个 ContentReleaseId；
- Preset Input：presetId + PresetRevision；
- Context：original ContentReleaseId + internal context schema；
- Model/Storage：各自 Codec 内部 schema；
- Deployment：provider/policy/adapter identity，仅配置与诊断。

### D-38 PublicAgentTurn Wire Contract 使用顶层 Kind 与 Goal Result 唯一正文

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

PublicAgentTurn wire body 使用顶层 `kind` 作为唯一 UI/business discriminant，闭合为 `ANSWER / CLARIFICATION / CONVERSATIONAL / BOUNDARY / CAPABILITY_UNAVAILABLE`。删除嵌套 interaction.kind、legacy disposition、CONFIRMATION 和 plan/outcome/completedTasks/clarification 全 optional 的大字段袋。

1. 所有已形成合法 Turn 的 closed variants 属于业务响应；malformed/auth/idempotency/rate-limit 等仍按 D-36 返回 API Error。具体 HTTP path/status 由 D-46 定稿；
2. `AnswerTurn` 只拥有一份 `answer`：`resolution + contentReleaseId + ordered goalResults + sourceCatalog + sourceComposition + suggestedActions`；删除旧顶层 title/blocks/sections/recommendation/evidence/generation/construction/degraded/agentTurn 双结构；
3. `AnswerGoalResult` 是唯一公共内容单位，包含 goalId、label、coverage、可选 Presentation、Goal notices、可选 continuation；顺序严格使用 Plan UserGoal 顺序；
4. 结构不变量：FULL 必须有 Presentation且不得有覆盖缺口 notice；PARTIAL 必须有 Presentation和至少一个缺口 notice；NONE 不得有 Presentation且必须有 notice；continuation unavailable 等不影响内容 coverage 的 notice 可附着 FULL；
5. 公共 Presentation 首发只保留 `SECTIONED / RECOMMENDATION` 两种。Portfolio Fact/Compare、General、Cross-domain 都投影为 typed sections；不把内部 capability 类型一比一复制为更多 wire variants；
6. Section 至少包含 sectionId、closed section kind、title/content 与 support。Cross-domain 使用 GENERAL_PRINCIPLE/PORTFOLIO_EXAMPLE/RELATION 等 section kinds 清晰分区；
7. Recommendation Presentation 唯一持有 requested/actual size、ordered items、unsatisfied constraints、incomplete reasons 与可选 supporting sections；删除顶层 Recommendation/CompletedTask duplicate；
8. Public Support 闭合为 `GENERAL_KNOWLEDGE / VERIFIED_PUBLIC_EVIDENCE / DERIVED`，只携带 publicSourceKeys；删除 sourceTaskIds、raw claim/evidence IDs 和 `DERIVED_FROM_TASKS` 内部措辞；
9. `answer.sourceCatalog` 是完整公开来源唯一位置；section/item 只引用 keys。ContentReleaseId 已在 Answer 顶层，不在每条 Portfolio reference 重复同一 publishedVersion；
10. Goal Notice 公开 stable code + 用户安全 message，由 Projector生成；前端可据 code 选择图标/样式，但不翻译 internal reason。message 不含 provider/backend/taskId/exception/prompt/schema；
11. 每个 Goal Result 最多一个 continuationRef，核心为 contextHandle；Recommendation item 可有 resultItemId。删除 contextHandle/continuationContext/batch/context/type/sourceTaskId 多表示；
12. SuggestedAction 可包含 actionId、label、inputText 与可选 continuationRef；无 continuation 走 D-30 ASK，有 continuation 走 CONTINUE。前端不按 Task/位置自行重建协议；
13. Critical Clarification 使用独立 `CLARIFICATION` variant 且无 answer；部分 Goals 已产出时仍为 `ANSWER(PARTIAL)`，允许附带一个 local clarification challenge。前端直接渲染 Answer + form，不再推导 PARTIAL_READY；
14. Clarification Challenge 公开 clarificationId、prompt、fields；field 使用 opaque fieldId、SINGLE_CHOICE/TEXT、label/required/limit 与 opaque choiceId/label。客户端不接触 promptCode、subject binding、blockedTaskCount 或内部 Goal/Task IDs；
15. Conversational/Boundary/CapabilityUnavailable variants 只保留 requestId、message、stable public code/可重试性与 suggestedActions 等自身必需字段，不携带 answer/goal/source；
16. 首次生产 wire contract 不包含 `ExecutionSummary`。Task/Goal执行情况只进入D-40内部observability和Goal Notice；未来若有真实产品需求，再作为不参与resolution的additive field单独评审；
17. 公共 envelope 可携带新签发/轮换 ResumeToken 的 conversation metadata；Goal continuation 仍只在对应 AnswerGoalResult，不放一个全局 first handle；
18. Frontend 直接使用 discriminated union `switch(turn.kind)`；删除 mapV3→legacy disposition、semantic sections 重建、Preset blocks 优先级、compatibility projection、execution conflict、degraded fallback、旧 task status；
19. Frontend 对已知必填字段/closed enum fail closed，损坏 variant 进入明确 Contract Error；未知附加字段可忽略以允许 additive evolution，不用 payload contractVersion switch；
20. D-34 Golden Fixtures 按五种 variants + Answer complete/partial/no-result/local clarification 覆盖此唯一 wire contract。

#### 目标顶层

`{ requestId, kind, answer? | clarification? | message/code?, conversation? }`，其中具体 variant 的字段由 kind 静态互斥。只有 ANSWER 有 answer；Critical Clarification 和其他非 Answer variants 不含任何伪正文。

### D-39 按数据用途分别保留，幂等与续接不成为长期保存对话的理由

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

ConversationWindow、Model Prompt/Raw Output、Completed PublicAgentTurn、typed Continuation Context、Clarification Challenge、ResumeToken 与 diagnostics 按不同用途分别确定落盘和 TTL；不使用一个“Conversation retention”覆盖所有数据。幂等重放与续接只保存完成各自职责的最小数据。

1. Bounded ConversationWindow 只存在于当前 Turn 内存，用于 Goal Interpretation 指代理解；Turn 完成/失败/取消后释放，不进入 TurnExecutionStore、Context、Replay Snapshot、logs 或 exception messages；
2. Frontend 保持消息仅在当前页面内存并设置条数上限，不写 localStorage/IndexedDB；ResumeToken 继续只占当前活跃会话的 sessionStorage 槽位，存储不可用时本页回答仍可工作但刷新续接不可用；
3. 外部模型调用是数据披露。Goal Interpretation 只发送必要最近消息和公开 subject descriptors/安全 Context summary；General 只发送 typed request；Portfolio Expression 只发送 approved public statement aliases；任何 operation 不发送 Token/Handle/internal IDs/raw evidence/完整历史 Answer JSON；
4. Production 启用外部模型 operation 必须有明确 data-policy 配置；未批准时该 Port 不可用，不静默发送。Provider retention/training 政策属于部署审查并写入运维文档；
5. Model Prompt 与 Raw Output 默认不落盘；调用结束/strict decode 后释放。失败 diagnostics 只记 operation/provider/failure/latency和size bucket，不记 raw response；生产数据不自动进入 eval corpus；
6. D-31 TurnExecutionStore 可以短期、加密保存已经通过 D-38 校验的最终 PublicAgentTurn 业务快照，以精确重放；使用固定分钟级 absolute TTL且访问不续期，只保存公共结果，不保存 Request/Prompt/Window/SemanticResult/raw evidence；
   - **已批准的产品边界扩展：** 该快照属于对原“答案不持久化”规则的定向例外，只允许最终公开答案、分钟级fixed TTL、认证加密、无问题/Prompt/内部状态；权威边界同步见 AGENTS.md、docs/08 与 docs/11；
7. Replay snapshot 不保存 raw ResumeToken。业务 PublicAgentTurn 精确重放；credential 在 delivery envelope 中按当前会话状态省略、签发或轮换，因此 Token 字节不属于幂等业务快照；
8. CompletedTurn snapshot 设置严格大小上限和批量 cleanup；超限是实现/投影不变量错误，不静默截断 Answer。具体 2/5 分钟等默认值由真实重试数据决定；
9. Continuation Context 只保存 stable subject identities、authorized scope、facets/dimensions、constraints/preferences/exclusions、selected result identities/order、parent handle、original ContentReleaseId 与 internal policy/schema identity；禁止保存用户原文、Prompt、Model raw output、Answer/Presentation正文或 raw evidence；
10. Context payload 使用 authenticated encryption、count/size bound 与独立 idle/absolute TTL。当前 24h/7d 只是配置基线，不作为架构定值；首次生产按真实续接率采用保守默认并可调；
11. Clarification Challenge 短期、一次性、绑定 Conversation/ResumeToken/ContentReleaseId，只保存 fields、opaque choices及服务端 subject/Goal binding；成功后 consumed，过期/重复稳定拒绝，不保存完整问题/旧 Answer；
12. ResumeToken 不进入 URL/body/log/analytics/frontend diagnostics；通过专用 Header（或未来明确 cookie）传输，服务端只保存 keyed hash，恢复时轮换，clear 时撤销。当前公开 Portfolio Context 不提前引入账号认证；若未来保存敏感账号数据再迁移正式身份/HttpOnly策略；
13. 持久化 RequestFingerprint 使用 keyed HMAC(server secret, canonical closed command)，不保存普通 SHA-256 用户文本指纹；fingerprint 不公开、不进普通日志、不作为 credential；
14. Clear Conversation 经 ResumeToken 授权后删除/撤销 Conversation session、所有 Context/active slots、Clarification Challenges、CompletedTurn snapshots、可取消的 Active Turns 与 Token hash；客户端同步清内存消息、sessionStorage Token、handles/actions；
15. Diagnostics/metrics 不因 clear 逐条删除，因为其设计上不含用户内容/credential。允许 request/trace UUID、operation/kind/count/terminal/failure enum/content release、latency/size bucket；禁止 Goal label、subject title、question/message/answer/prompt/output/source label/route、Handle/Token/resultItemId、raw exception message/body/header；
16. Exception diagnostics 可以记录 exception type 与有限 stack fingerprint，不记录可能携带用户/Provider内容的 message；
17. Store unavailable 按 D-43 分层：claim 前不可用则拒绝 Turn（Agent 暂不可用、公开站点不受影响）；claim 后不可用则继续当前只读执行，放弃未提交 continuation/replay side effects，有效 Answer 仍可返回。这里“可选”指 continuation/replay 对回答本身是增强，不表示 Production stateful 可无 Store 运行；
18. 各类 TTL/容量为模块配置且有启动校验/cleanup，不建设自适应 retention 或跨数据统一 TTL；运行参数在后续按观测数据调整。

#### 数据生命周期

- Turn 内存：ConversationWindow、Prompt、Raw Model Output；
- 分钟级加密：Completed PublicAgentTurn replay、Clarification Challenge；
- 小时/天级加密：typed Continuation Context；
- 客户端页签：消息内存 + active ResumeToken sessionStorage；
- 长期运维：只含无内容枚举/计数/bucket 的 metrics/logs。

### D-40 Observability 只读投影业务状态，不建设第二个监控状态机

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

Observability 只观察已经由 Lifecycle/Planning/Execution/Capability/Projection/Settlement 确定的状态，不参与 Goal Coverage、Task Terminal、fallback、Public Notice 或 Turn Resolution 决策。首发使用低基数 metrics + 内容安全 structured events；不以日志事件反推业务状态。

1. Metrics/Events 只回答 Turn outcome/latency、Goal coverage、DAG 调度、Model、Retrieval、Settlement/Context、Public Contract 七类问题；删除已淘汰 Legacy route/tool/free-form answer/fallback/degraded/generation mode 事件；
2. Turn metrics 至少覆盖 kind/resolution count、duration、cancel/timeout/replay；Goal metrics 覆盖 goal kind + FULL/PARTIAL/NONE 与 public notice code，不使用 goalId/label；
3. Task/Scheduler metrics 覆盖 task type + terminal、duration、ready batch size、inflight、queue duration、blocked dependency、late result dropped；不把 taskId 作为 metrics label；
4. Model metrics 按 operation/provider/outcome 统计 transport latency/failure，领域 Adapter 单独统计 schema/grounding reject；schema invalid 不误算 Provider outage；
5. Retrieval metrics 按 backend/strategy/outcome、duration、fallback route/reason 与 evidence support status 统计。Fallback 即使最终 Goal FULL 也保留内部信号，用于发现 vector/DB/config 异常，但不产生公共 degraded；
6. Settlement/Context metrics 覆盖 claim result、replay/conflict、complete/cancel、atomic settlement、context mutation/resolve/cleanup；Public Contract metrics 覆盖 projection invariant 与 frontend response invalid；
7. Structured Events 只在真实边界发一次，例如 turn completed/cancelled、task completed、model call/draft validation、retrieval attempt/fallback、settlement completed/failed、context resolved、public projection failed；Transport/Adapter/Capability/Engine/Lifecycle 各拥有自己的事实；
8. 同一故障可以通过 requestId/traceId 关联不同边界事件，但不得让每层都发布同义“answer failed”。事件发布 best-effort，失败不能改变业务结果；
9. 正常 PARTIAL/NO_RESULT/business empty/evidence insufficient/推荐不足使用 INFO/metrics，不作为系统 WARN；Provider/DB/Context/timeout 等基础设施故障 WARN；不变量、malformed Artifact/PublicTurn、atomic consistency、终局门穿透与未分类异常 ERROR；
10. Metrics 只使用低基数 enum/状态，不包含 request/turn/task/goal identity、subject、route。Structured Event 可以包含随机 requestId/traceId、ContentReleaseId、task ordinal/type、goal kind、operation/provider/failure enum，但仍禁止用户内容/Handle/Token/resultItem；
11. Logs 使用 duration/size bucket 降低内容和基数风险；metrics histogram 可以记录数值 duration/count。Exception 只记录 type 与有限 stack fingerprint，不记录 message；
12. 保留字段 allowlist/forbidden content token 的有效隐私防线，但 event factory 按深模块拥有，删除中央表中的下线事件；不允许 arbitrary Map 直接写 structured logger，也不为每个事件建设复杂 class hierarchy；
13. Frontend diagnostics 只报告 request completed/failed/cancelled/slow、response invalid、UI runtime/content load；允许 requestId/Turn kind/resolution/duration/http/error code，删除 answer.degraded/generation/guidance/Task重算和完整 response；
14. Operational metrics 不能替代 D-34/D-35 quality eval。COMPLETE rate/低延迟不能证明 General 事实正确、Portfolio 表达自然或推荐有帮助；
15. 首次生产先收集 p50/p95/p99 latency、coverage、timeout、model transport/validation、retrieval fallback、settlement failure、replay/conflict、late result baseline，再决定告警阈值和 D-08/D-10 运行参数；
16. 当前单体优先复用 structured logging/Micrometer 等现有能力；可给外部 Model/Retrieval/Settlement 建少量 child spans，但不建设自定义 tracing framework、每方法 span 或从 final outcome 伪造 stage timeline；
17. 成功事件可采样、聚合 metrics 不采样；严重失败/不变量事件保留。具体日志保留周期属于部署策略，但必须继续满足 D-39 无内容要求。

#### 最小观测边界

Turn/Goal 告诉产品结果，Task/Scheduler 告诉 DAG 行为，Model/Retrieval 告诉外部能力，Settlement/Context 告诉状态可靠性，Public Contract 告诉前后端兼容性。它们共享 correlation，不共享状态权威。

### D-41 Frontend 以 Goal Answer 为主，内部执行信息默认退居二级

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

Frontend 只按 D-38 PublicAgentTurn variant 和有序 GoalResults 渲染。信息层级固定为：Goal 正文 -> Goal Notice/Caveat -> 来源与支持 -> SuggestedAction/Continuation。首次生产不渲染公共执行详情；Agent 内部 Task/阶段/构成不能压过用户答案。

1. 单 Goal COMPLETE 使用极简 Answer：Goal/内容、必要来源与操作；不默认显示 COMPLETE/READY、scope、generation/construction、degraded、主辅 Task、四阶段或 source composition 技术标签；
2. 多 Goal 按 UserGoal 顺序分组，每个 Goal 独立显示 label、非 FULL 时的 coverage、Presentation、notice/caveat、sources、actions；不按 Task topology/完成时间排序，不公开 supporting Task；
3. PARTIAL 顶部最多显示“已完成 N/M 个目标”的简短摘要，具体缺口只在对应 Goal 中说明；一个 Goal 失败不把其他 FULL Goal 整体染成错误态；
4. NO_RESULT 不渲染空 section/占位正文/模板解释，只显示目标、用户安全原因和可执行恢复操作；
5. AnswerTurn 的 local clarification 公开 affectedGoalIds 并贴在首个受影响 Goal 下；若影响多个 Goals，可说明“补充后将继续 N 个目标”。Critical Clarification 独立显示，无 Answer/meta/source/execution；
6. Goal Notice 放在 Goal heading/Presentation 边界，Section Caveat 紧跟对应 Section；不把所有限定语只汇总成“共 N 条”藏在另一个面板；
7. Section 根据 publicSourceKeys 从唯一 SourceCatalog 解析，显示直接相关来源；Answer 底部可有“查看全部来源”入口。删除 EvidenceId fallback；无 PublicSourceKey 时不展示内部引用；
8. Support 使用克制文本“已审核公开证据/通用知识/基于上述内容归纳”，不靠高饱和颜色或对勾暗示所有内容同等验证；ContentReleaseId 放在来源详情，不占正文 header；
9. Recommendation Presentation 嵌入所属 Goal，数量缺口只说明一次；卡片顺序、reasons、sources、route、resultItemId 和业务 SuggestedActions 均以后端为权威；删除前端硬编码的换掉/解释/偏后端/改数量等业务请求构造；
10. SuggestedAction 无 continuation 发送 D-30 ASK，有 continuation 发送 CONTINUE；Frontend 只转发 actionId/inputText/ref，不根据 label/Task/position猜协议；纯 UI 的复制/打开链接可以本地处理；
11. 删除当前执行摘要/快照公共UI；Goal Notice解释用户缺口，D-40 observability解释内部执行。未来若重新提出ExecutionSummary，必须基于真实terminal、有明确用户价值且不参与业务结算；
12. Pending 首发只显示“正在处理”与取消，不模拟检索/验证百分比、思维链、工具日志或 DAG阶段；未来 streaming 只显示真实服务端事件；
13. Message header 普通情况只显示 AGENT，PARTIAL/NO_RESULT 可有简短状态；删除 scope/verification/source/generation/tech-tail badge 堆叠，真实性由 section support/source表达；
14. 组件按稳定 wire variant/presentation 拆分：PublicAgentTurnMessage、AnswerTurnView、GoalResultView、Sectioned/Recommendation Presentation、Clarification、SourceDrawer；ConversationThread 只负责列表、scroll/focus与事件转发；
15. Frontend 只维护视觉/交互状态：折叠、选中、form、pending、scroll、local session。禁止维护/重算 Goal coverage、resolution、source composition、degraded/fallback、Task success、Recommendation order、Context compatibility 或 choice→subject binding；
16. API Error 作为请求错误/重试体验处理，不伪造 Agent message/PublicTurn；Boundary/CapabilityUnavailable 等合法 variants 作为对话消息显示；
17. 可访问性：状态不只靠颜色；Clarification 有 field labels/errors；Goal heading 层级稳定；仅对最终/需要用户操作状态使用 aria-live；SourceDrawer focus return；Execution details 可键盘展开；Recommendation 窄屏单列；reduced-motion 禁用非必要动画；
18. Frontend tests 按 closed variant/GoalResult/Presentation 组件拆分，删除 2k 行组件中保护旧协议组合的巨型矩阵；D-34 Golden Fixtures 驱动 contract mapping 与主要渲染场景。

#### 默认展示原则

正常答案尽量像一条清晰回答；只有多目标、缺口、来源或用户主动展开时才显示 Agent 架构细节。透明度来自准确的 Goal/来源/真实终态，不来自默认展示全部内部结构。

### D-42 一个 TurnDeadline 约束全链，预算字段回归真实所有者

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-17

#### 决策

每个 Turn 在 Lifecycle claim/接纳时只生成一个 absolute `TurnDeadline`，覆盖 Goal Interpretation、Planning、DAG Execution、Public Projection、Settlement 与响应准备；所有子模块只消费 remaining time，不能在阶段/Task/fallback 重新开始计时。执行阶段为 Settlement 预留一个固定 reserve，不建设耗时预测器。

1. `turnDeadline = turnStartedAt + configuredTurnTimeout`；`executionDeadline = turnDeadline - settlementReserve`。Scheduler 到 executionDeadline 停止等待/启动，未完成 Task TimedOut，已 Produced 独立 Goals 保留，随后进入 Projection/Settlement；
2. Model/Retrieval/DB I/O effective timeout 一律为 `min(operationConfiguredCap, remainingToRelevantDeadline)`；operation cap 只能更短，不能突破 TurnDeadline；
3. Settlement DB 超时/不可用时按 D-31/D-39 放弃未提交 continuation/replay side effects并返回已成立 Answer，不能一直等到 HTTP层超时；
4. 不为每个 Task 预分固定毫秒。所有 ready Tasks 共享 executionDeadline，ready-set + bounded concurrency 决定启动；Task 外部 I/O 使用自身 cap；不建设动态耗时预测/按历史分配；
5. 只保留一个很小的统一 Task dispatch floor，避免最后几毫秒创建 Future；Retrieval fallback 只保留 D-23 一个简单 fallback start floor。它们不是每 Task/Provider 的耗时预测窗口；
6. 尝试次数由能力合同固定：Goal Interpretation 1、General 1、Portfolio Fact Expression 最多1、Retrieval 1 primary+最多1 fallback、Cross-domain expression 0、Provider switching 0；删除通用 maxAttempts/requestLocalAttemptOrdinal/logicalRetrievalLimit；
7. 删除混合 retrieval attempt/evidence/reference/character/deadline 的 `TaskExecutionAllowance` 字段袋。ExecutionContext 只传 execution deadline/cancellation；Portfolio policy 拥有 candidate/evidence/reference bounds；Model config 拥有 timeout/token；Presentation policy 拥有字符/section/item limits；
8. 删除按 executable Task count 平均分 TOTAL_CHARACTER_LIMIT 的逻辑。Supporting Task 无公共字符预算；每个公开 Goal Presentation 按其类型/depth使用 bounded policy，PublicAgentTurn 有最终 response-size cap；超限删除完整可选 detail/statement，不截断事实句；
9. 并发只保留两个真实边界：system-wide max ActiveTurns 与每 Turn maxParallelTasks。前者保护整体请求资源，后者限制 DAG fan-out；不再按 General/Portfolio/Expression/Task type 建多级 semaphore/白名单；
10. Virtual Thread executor 可以保留，但 ActiveTurn/Scheduler cap 必须限制实际外部并发；DB/HTTP connection pool 是基础设施容量，不成为第三套业务 DAG quota；
11. Source/IP RPM 等 transport rate limit 与 ActiveTurns 分离，部署网关/单实例保护不进入 TurnRuntimePolicy 或 Goal/Task结果；
12. TurnExecutionStore lease 必须满足 `lease > turnTimeout + settlementReserve + recoveryMargin`，启动时校验。当前短 Turn 不建设 lease heartbeat；未来只有 Turn 时长超过安全 lease 时再讨论续租；
13. Client request timeout 应略大于 server TurnDeadline + network margin；用户取消通过 D-11 requestId cancel command，不靠客户端更短 timeout 模拟；连接断开可 best-effort cancel但不是终局权威；
14. Replay/Clarification/Context TTL、cleanup interval、Token 生命周期属于 D-39 retention，不进入 execution budget；
15. 现有 12s Turn/2.5s Interpretation/8s General/4s Expression/30s lease 只作为首轮 benchmark 起点，不是架构定值。根据 D-40 p95/p99、timeout、late result、fallback/settlement数据调整；
16. 配置按少量关系启动校验：Turn timeout>reserve；operation cap>0；lease覆盖完整Turn；client/网关timeout不早于server；fallback floor<execution window。禁止在多个 Properties 中分别硬编码同一数值上限；
17. 不建设统一 Budget Framework/DSL。`TurnDeadline`、两个 concurrency settings、operation caps、能力/Presentation bounds 保持各自明确类型与模块所有权。

#### 预算关系

时间只有一条 absolute deadline；并发只有 ActiveTurns 与 per-Turn task fan-out；attempt 由能力固定；size由数据生产者/Presentation所有。统一的是约束关系，不是把所有数值塞进一个 Budget 对象。

### D-43 Public Content 与 Agent State 分库分责，只有 State 内部需要原子事务

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-18

#### 决策

数据存储只分两类：不可变、可重建的 Public Content Read Model；短期、可写、加密的 Agent State。二者可位于同一 PostgreSQL 集群，但逻辑 schema/pool/权限/事务独立；不建设 Public DB + State DB 分布式事务。

1. Release Bundle 是公开内容长期可恢复源；PostgreSQL public schema 是只读查询/向量索引，可从审核后的同 ContentReleaseId Bundle 重建；Agent runtime 不写 Public DB；
2. PostgreSQL public retrieval 连接/timeout时可按 D-23 回退同 release Bundle；content/integrity/version mismatch 不 fallback。Public DB损坏时停用该 release DB并从 Bundle 重建；
3. Agent State 包含 TurnExecutionRecord、短期 PublicTurn replay、ResumeToken hash、Continuation Context/active slots、Clarification Challenge、cancel/completed terminal与cleanup metadata；它们共享一个 State DataSource/schema/transaction manager；
4. D-31 Settlement 通过 Agent State transaction 原子提交 Context mutations、final PublicAgentTurn snapshot、Clarification mutation 与 COMPLETED record；这里的 `AgentStateStore` 只表示 TurnExecutionStore 与 Context/Challenge repositories 共享的同一 DataSource/事务边界，不是第二个 claim/complete/cancel/replay 业务权威；不让各repository自行开事务后在Service层拼最终一致性；
5. Agent State 与 Public Content 只通过 ContentReleaseId 引用，不在 Settlement 更新 Public数据，因此不需要 XA、2PC、outbox或saga；
6. Production stateful 只使用 PostgreSQL 一个状态权威，不在运行时故障后自动切换 Memory。InMemory Adapter仅local/test/明确single-process ephemeral mode，重启丢失且Production默认禁止；
7. 配置语义可收敛为 `POSTGRESQL / EPHEMERAL`；若保留 DISABLED，则不签发 ResumeToken、不提供Continuation/可恢复Clarification/跨请求Replay，UI不得显示这些能力；
8. State DB在claim前不可用：不执行无法结算的Turn，Agent API临时不可用；Public Portfolio页面/Bundle读取保持liveness。Agent readiness与站点liveness分开，不能因独立State DB/Flyway故障拖垮整个公开站点，也不能静默Memory fallback；
9. claim后但Engine前State失效：停止执行并让lease恢复；Engine已生成当前只读PublicAgentTurn、Settlement连接故障时允许transaction rollback后无continuation返回Answer，并记录settlement failure。精确replay只在成功Settlement时保证；
10. 上述post-engine availability例外只适用于当前无外部副作用的只读Agent。未来Action产生写操作时必须关闭：Settlement失败不能声称Action成功；
11. State transaction中Context mutation要么全提交要么全回滚；Context业务编码/授权不合法可在事务前排除对应continuation并完成Turn，但连接/commit故障不得留下隐藏部分Context；
12. Public Content pool以读查询为主，Agent State pool服务短claim/settlement/resolve事务；不复用一个pool让retrieval长查询耗尽Settlement连接。当前pool size只是benchmark起点；
13. Agent State是短期可丢失体验状态，不承诺长期备份/PITR/跨区复制/event sourcing。最低要求为transaction、encryption、TTL cleanup、schema migration、health和可整库清空；清空只使Token/Context/Replay失效，不损害Public Content；
14. 首次生产前State schema和Context Codecs迁到最终单版本，删除P3/P5兼容。生产后升级才使用明确migration或等待旧Context TTL过期，不为测试数据保留reader；
15. Cleanup覆盖expired leases/replay/challenges/contexts/orphan active slots/revoked sessions/无法解密旧key记录，小批量、有上限、幂等、不阻塞前台并有D-40 metrics；不建设通用Job Framework；
16. State encryption key丢失时短期payload视为过期并清理，不绕过加密；Public Content不受影响。Token/payload key轮换最多保留完成实际TTL窗口所需的previous key；
17. Memory/Postgres adapters必须通过同一Store contract/transactional scenario tests；Memory只验证语义，不成为Production高可用fallback；
18. Health语义：public content release readiness、agent state readiness、model/retrieval capability分别报告，Frontend可让公开页面工作同时明确Agent暂不可用，不把所有依赖折叠为application down。

#### 故障边界

Public DB故障优先同release Bundle；State DB故障不影响公开内容站点。State claim前故障阻止新Turn，已生成只读Answer可在settlement失败时无续接交付；此例外不扩展到未来有副作用Action。

### D-44 Eval 按最终能力独立晋级，不按 P3/P4/P5 阶段堆矩阵

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-18

#### 决策

Eval/Release Gate 按最终生产能力组织：Goal Interpretation、Turn Planning/Execution、Portfolio Retrieval/Evidence、General Knowledge、Portfolio Fact Expression、Cross-domain Synthesis、Public Contract、Continuation/Settlement。删除 P3/P4/P5/Legacy suite/executor命名与旧生产seam绑定；代码存在或配置可enabled不等于能力production-ready。

1. Goal Interpretation gate覆盖Goal kind/subject/output/clarification/stable-current-high-risk分类、closed schema、Proposal→Plan compile、latency/provider failure；输出Task/DAG、非公开subject、boundary bypass、损坏JSON部分救活、越权Context等hard errors零容忍；
2. Goal Interpretation未PASS时自由文本入口不得生产启用；Preset/明确结构化入口可独立工作，不fallback Legacy/Shadow Router；
3. General Knowledge gate除结构外评估稳定概念事实正确性、definition completeness、overclaim/caveat、时效/高风险/Portfolio个人事实/伪引用越界、重复运行核心语义和Cross-domain支持隔离；未PASS保持disabled；
4. Portfolio Fact Expression safety零容忍protected atom修改、新事实、caveat/source/scope丢失、alias越界和非原子fallback；此外必须相对deterministic baseline证明可读性/重复/长度/覆盖的稳定质量收益与可接受latency。安全但无收益仍disabled；
5. Portfolio Retrieval/Evidence deterministic gates覆盖scope、ContentRelease、approval/pairing/public route、fallback分类/次数、integrity/version、fake citation/false sufficient；安全不变量100%，召回/hit/latency使用baseline regression；
6. Planner/Engine/Projector/Settlement是确定性模块，Goal fulfillment/topology/edge/cycle、ready-set/order/deadline/cancel/late result、closed contract/supporting hiding/source、idempotency/atomic context等矩阵必须100%，不使用概率阈值；
7. Cross-domain只评估D-27真实能力：明确Goal、一个General+Portfolio、anchor匹配、selected statements、无匹配NoResult、support隔离、selected provenance、relation coverage；删除substring/任意多输入/model echo相关suite；
8. 全局hard errors至少包括 fakeCitation、falseSufficient、scopeExpansion、contentVersionMix、rawInternalIdLeak、General personal fact/high-risk bypass、lateResultCommitted、cancelledTurnPublished、partialContextCommit、supportingPresentationLeak、publicContractInvalid；任一发生即FAIL，不能被平均分抵消；
9. 非零容忍质量/召回/latency指标同时满足reviewed绝对最低阈值与baseline regression limit；阈值由固定数据集/当前baseline确定，不在架构文档凭感觉写百分比；priority metrics回归限制更严；
10. Eval lanes收敛为 OFFLINE（CI deterministic/fake ports）、CONTROLLED_PROVIDER（固定真实provider/model/config，发布前/定期）、PRODUCTION_OBSERVATION（D-40无内容聚合metrics，只发现漂移，不能给质量PASS）；不是每suite强制三lane；
11. `PASS`允许能力晋级，`FAIL`与`INCOMPLETE`均禁止。缺Provider凭证/运行、dataset/baseline、blocking suite、ContentRelease mismatch、0 denominator不能静默skip成绿色；
12. Dataset按train/dev/holdout/adversarial/smoke用途版本化，记录hash；不自动导入生产对话。Portfolio oracle只来自审核Bundle，模型输入不含expected answer/grader rule，保留Oracle Isolation；
13. Eval Report记录dataset hash、code commit、ContentReleaseId、operation、provider/model、Prompt/Codec identity、runtime config、run mode、verdict与missing prerequisites；Report本身不含credential/生产Prompt；
14. 能力独立晋级：Goal Interpretation是自由文本前提；General/Portfolio Expression默认关闭直到各自PASS；Retrieval/Evidence是Portfolio前提；Cross-domain和Continuation可独立关闭。一个可选能力失败不阻止其他已通过能力；
15. Runtime只读取明确operation/capability enabled config；Release pipeline/人工审核决定开启。删除运行时读取Eval JSON、自动根据线上指标开关、自动Provider回滚等第二状态机；异常用kill switch/redeploy；
16. Eval executors调用与Production相同typed entry/ports，不建mock-only业务链，不依赖准备删除的P3/P4类；Fake ports只替代外部I/O；
17. Eval Harness/CLI/Report从Production runtime打包隔离到tools/build module/source set且不注册Spring beans；若拆module成本暂高，至少profile隔离。隔离工具不复制Production domain model；
18. D-34 shared PublicAgentTurn Golden Fixtures、deterministic module tests与Eval相互补充：tests验证不变量，Eval验证数据集质量/回归，observability验证运行健康；三者不互相冒充。

#### 晋级原则

确定性安全/合同必须全对；模型能力还必须证明真实质量。缺少真实Provider评估不等于失败测试，但对应能力状态是INCOMPLETE，不能生产开启。

### D-45 安全依赖服务端闭合合同与最小权限，不依赖 Prompt/客户端自律

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-18

#### 决策

当前首发安全资产是Public Content/证据完整性、Goal→Task→Scope授权、ResumeToken/Context与短期State、Provider/DB密钥、Public support/source真实性和服务可用性。用户文本、ConversationWindow、客户端引用、Model output、Bundle/DB数据、Provider response、SuggestedAction回传和配置动态标识符均视为不可信；校验集中在真实信任边界且每项一个Owner。

1. Prompt Injection不能赋予模型执行权。Goal Model只输出closed UserGoal/Clarification Proposal；代码决定Task/DAG/Capability/SQL/backend/scope。模型不能生成工具调用、内部ID、Context、Route或扩大subject；
2. Public Content文本同样作为data而非instruction，结构化放入user/data payload，不拼system instruction，不执行其中URL/命令；Portfolio Expression只见approved aliases，General不见Portfolio raw内容，Interpretation不见raw evidence；
3. 所有Model output严格JSON/duplicate-key/unknown-field/enum/size/schema校验，禁止polymorphic type、Markdown抽取、损坏JSON修复和partial adoption。Proposal再经catalog/Goal/boundary/Context/Planner验证；Draft绝不直接成为PublicTurn；
4. Surface subject、ContextHandle、resultItemId、choiceId均为客户端引用而非授权。服务端在当前ContentRelease/Conversation重新验证公开subject、token+handle归属/expiry/action compatibility、item membership、choice membership和parent scope不扩张；
5. SQL只由typed Adapter与parameter binding构造；user/model不提供SQL、column/order/table/schema。动态标识符来自代码闭集或启动allowlist；Public DB只读credential、State DB独立读写credential，IN/list/query/vector有bounds/finite/dimension校验；
6. Release file root只来自部署配置，保留ContentRelease格式、realpath+NOFOLLOW_LINKS、root containment、regular-file、file/count/JSON size、checksum、原子activation；checksum只保证损坏检测，真实性依赖受控发布/权限，未来不可信分发时才引入签名；
7. Public source/subject routes只允许站内相对路径，禁止scheme/colon、`//`、`..`、反斜杠和换行。Provider endpoint只来自HTTPS配置/registry并限制host/redirect，不接受user/model URL，防止SSRF/open redirect；
8. Frontend对Model/Public文本只使用escaped interpolation，不使用v-html/innerHTML/eval/dynamic code；links只用validated relative route，SuggestedAction只构造closed command，不执行代码/动态组件；部署保持same-origin CORS、CSP、nosniff、frame-ancestors和Referrer-Policy；
9. ResumeToken主要浏览器风险是XSS：只存active sessionStorage、短TTL/轮换/clear，不进URL/log；CSP/无HTML渲染/第三方脚本控制。当前Context仅公开作品选择/偏好，不提前建账号Auth；未来敏感账号数据迁移HttpOnly Secure SameSite/正式身份；
10. Cryptography只使用标准SecureRandom、keyed hash/HMAC、authenticated encryption、keyId/current+TTL所需previous key和AAD绑定；密钥仅secret配置且用途分离。删除Plan Confirmation crypto、自制业务canonical token和客户端内部Plan验证；
11. 资源在真实边界有界：HTTP body/message、Plan Goal/Task/topology、Execution concurrency/deadline、Model size/tokens/attempt、Retrieval candidates/evidence/fallback、State count/payload/TTL、Public goal/section/item/source/action/response size；不靠一个timeout解决DoS；
12. Public SuggestedAction不是签名授权。客户端可篡改actionId/text/handle/item，服务端仍按D-30/D-29完整校验/授权/重新Plan；actionId只用于UI/diagnostic；
13. API Error/Goal Notice不泄露exception/SQL/table/provider endpoint/DB/backend/filesystem/model output/Token hash/internal Task/claim/evidence/Prompt schema；详细分类只进D-40无内容diagnostics；
14. 保留真正边界：HTTP Command Codec、Context Authorization、Model Draft Codec、Deterministic Planner、Capability Scope/Evidence Promotion、Public Projector、State Settlement；删除客户端expected type、Plan crypto、contract version、provider-app-schema、echo validator、私有breaker、重复wrapper校验、raw reasonCodes、JSON修复等表演性层；
15. Security tests覆盖Prompt injection、unknown/duplicate/oversized model JSON、非公开subject、cross-conversation handle、forged item/choice、stale/parent scope、SQL字符串、route/path traversal/symlink/checksum、script纯文本、action篡改、DoS bounds、cancel-late race、diagnostic leakage、provider redirect/config、key rotation；hard failures进入D-44零容忍；
16. 当前无账号、私有文档和外部副作用工具。未来引入任一项必须重新做threat model/authorization/approval/idempotency，不复用当前“匿名只读公开Portfolio”假设。

#### 安全所有权

Prompt帮助模型服从，closed codecs/allowlists决定模型能说什么，deterministic code决定能做什么，Context/Scope决定能访问什么，Evidence Promotion/Public Projector决定能公开什么。安全不依赖客户端或模型自报。

### D-46 首次生产 API 使用无版本 Agent Turn/Conversation 资源

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-18
- **关闭：** O-04

#### 决策

首次生产 API 使用无版本资源路径：`POST /api/agent/turns` 统一创建/重试 ASK(FREE_TEXT/PRESET)、CONTINUE、RESOLVE_CLARIFICATION；`DELETE /api/agent/turns/{requestId}` 取消 Active Turn；`GET/DELETE /api/agent/conversations/current` 查询安全摘要/清除当前会话。删除 `/api/v2/answers` 与 `/api/v2/conversation-context`，不创建 recommendation/clarification/context 专用回答入口。

1. `POST /api/agent/turns` 请求使用 D-30 requestId + closed command + optional SurfaceContext/ConversationWindow；同 requestId/fingerprint POST 负责 D-31 幂等重放，不增加 GET result/polling endpoint；
2. 合法业务交互统一 HTTP 200 + D-38 PublicAgentTurn，包括 ANSWER COMPLETE/PARTIAL/NO_RESULT、CLARIFICATION、CONVERSATIONAL、BOUNDARY、CAPABILITY_UNAVAILABLE；业务NoResult/Turn内部timeout不伪装500；
3. 未形成有效Turn使用统一API Error envelope `{requestId?, error:{code,message,retryable,retryAfterSeconds?}}`；malformed 400、invalid/expired ResumeToken 401、fingerprint/stale preset/in-progress 409、rate 429、State claim unavailable 503、unexpected Lifecycle 500；Retry-After同时使用Header；
4. ContextHandle invalid/expired/cross-conversation在有效CONTINUE中不泄露存在性，可形成 `CAPABILITY_UNAVAILABLE/CONTINUATION_UNAVAILABLE` PublicTurn和重新提问action；credential本身非法仍401；
5. ResumeToken推荐通过标准 `Authorization: Bearer <ResumeToken>`发送，首Turn省略；首次签发/恢复轮换只在response conversation metadata返回。所有Agent响应 `Cache-Control: no-store`；
6. `DELETE /api/agent/turns/{requestId}` 无body、幂等取消：cancel wins/already cancelled 204；已完成409 TURN_ALREADY_COMPLETED；未知/过期404（若后续安全审查选择防枚举可统一204）；
7. 已有Conversation的取消同时验证Bearer Token；首Turn尚未获得Token时允许高熵UUID requestId作为临时cancel capability，只能取消该ActiveTurn、不能读取结果/Context。requestId不进入可分享URL/第三方analytics；
8. Frontend取消先停止本地等待，再best-effort发送DELETE；AbortController/断连不是服务端取消权威。DELETE失败不能本地伪造Cancelled，Turn可能完成/可重放；
9. `GET /api/agent/conversations/current` 必须Bearer Token，只返回D-39安全Context summary/status与必要时轮换Token，不返回消息/答案/handles/Task/Plan/internal version/selected IDs；无效过期401；
10. `DELETE /api/agent/conversations/current` 通过Bearer授权并执行D-39 clear：取消可取消Turns、删除Context/Challenge/Replay/active slots并撤销Token；成功204。已不存在Token可选择204幂等，格式非法400；
11. 不开放Context CRUD。Context只作为CONTINUE reference使用，整体状态通过Conversation clear管理；Preset/Recommendation/Clarification仍进入同一Turn endpoint；
12. URL不含v2/v3。首次生产只有一个OpenAPI/Golden Fixture合同；未来真实breaking change再通过明确新media type/资源版本处理，不在payload固定contractVersion上switch；
13. Controller收敛为 `AgentTurnController(POST/DELETE turns)` 与 `AgentConversationController(GET/DELETE current)`，只负责HTTP/credential/Lifecycle调用/DTO-error/no-store，不直接访问Store/Receipt/Context/Projector；
14. 前端同一切片替换answerApi/contextApi路径、request/response types与取消逻辑，删除v3 fallback、CompletionReceipt union、旧ContextHeader/endpoint测试；不保留旧路由redirect/fallback，因为尚未首次生产；
15. Standard Authorization未来若与账号Auth冲突，届时按新threat model迁移Conversation credential；当前匿名只读Agent不提前引入双Auth/Header体系。

#### 最终资源

- `POST /api/agent/turns`
- `DELETE /api/agent/turns/{requestId}`
- `GET /api/agent/conversations/current`
- `DELETE /api/agent/conversations/current`

ASK/PRESET/CONTINUE/RESOLVE_CLARIFICATION是Turn commands，不是新的HTTP资源。

### D-47 实施采用基线加六个 Replacement Slices，外部边界一次原子切换

- **状态：** `CONFIRMED`
- **确认时间：** 2026-08-18
- **实施计划：** `2026-08-18-agent-architecture-convergence-implementation-plan.md`

#### 决策

按 Slice 0 行为基线加六个 Replacement Slices 实施：Command/Goal/Plan、Execution Kernel、Portfolio、General/Synthesis、外部边界原子切换、Infrastructure/Eval清理。所有工作在独立 convergence branch/worktree完成，中间状态不部署为首次生产架构。

1. Slice 1～4、6分别形成可审查的commit组，但不把仍依赖旧外部合同的半成品单独部署；
2. Slice 5包含Public Projection、Continuation、Lifecycle、State Settlement、新API与Frontend，是一个外部边界原子切换单元；内部允许多个commit，但只有Backend/Frontend/State/Routes全部满足Exit Gate后整体合并/部署；
3. 不把Public Projector、Lifecycle、新API和Frontend拆成跨Slice兼容链；禁止长期Request/Response bridge、新旧endpoint并存或新PublicTurn回投旧DTO；
4. 每个Slice必须有文件级Replacement Manifest、明确删除目标、验证命令和Exit Gate；新入口进入生产链时同步删除旧生产类/配置/测试/fixture；
   - 无生产调用方且不会被新链“替换”触发的遗留必须在依赖它们的 Slice 中提前删除、最迟 Slice 6 零引用清扫：`ConversationIntentRouter`、`DynamicQuestionService`、`DeterministicConversationFallback`、`ConversationWindowManager`、`ConversationProgressClassifier`、`ConversationSubjectGuard`、旧 `answer.service.DeterministicPortfolioAnswerComposer`及死Bean、`selection.service`策略层、前端`degradationSummary`死轴；这些代码不迁移到`turn.*`，且每个类型只有一个明确删除Owner；
5. 临时migration bridge只能存在于同一未完成Slice/branch内，并在该Slice exit时为零；不使用Legacy/Compatibility转发壳跨commit组掩盖未完成删除；
6. Slice 0先建立D-34目标场景/Golden Fixtures/复杂度基线/feature freeze；后续不边重构边加产品能力；
7. timeout、并发、TTL、pool和非零容忍eval阈值沿用benchmark起点完成结构替换，最终根据D-40/D-44数据调整，不阻塞模块收敛；
8. Definition of Done以实施计划第11节为准：唯一调用链/合同/API、旧版本阶段兼容生产引用为零、目标场景和能力Eval通过、依赖/复杂度下降且无新God Class；
9. 下一步只展开文件级任务、删除清单、验证命令和执行依赖，不继续添加无实现需求驱动的新架构层。

#### 实施序列

`Slice 0 Baseline -> Slice 1 Command/Goal/Plan -> Slice 2 Execution -> Slice 3 Portfolio -> Slice 4 General/Synthesis -> Slice 5 Projection/State/API/Frontend Atomic Cutover -> Slice 6 Infrastructure/Eval/Cleanup`。

## 5. 已关闭的开放项

### O-04 新 Agent 轮次入口的最终路径

- **状态：** `CLOSED_BY_D-46`

最终采用 `POST /api/agent/turns`；取消与 Conversation 资源一并见 D-46。以下候选仅保留为历史决策依据：

当前候选：

1. `POST /api/agent/turns`：推荐。准确表达“一次用户输入驱动的一轮 Agent 交互”，可以自然覆盖回答、确认、澄清、边界和会话恢复；
2. `POST /api/agent/interactions`：与 `interaction.kind` 术语一致，但一个 turn 内可能既有交互状态又有回答结果，语义略宽；
3. `POST /api/answers`：最短，但会继续误导，因为 confirmation、clarification 和 conversational recovery 并不都是 answer。

不建议仅改成 `/api/v3/answers`：这会继续把 HTTP 路由版本、Semantic Turn Contract 版本和业务资源名绑在一起，并且仍错误暗示每次调用都会产出答案。

## 6. 明确的反叠层规则

任何后续设计提案都必须通过以下检查：

1. **替换检查：** 新类型、新 Mapper、新 Adapter 替换哪个旧实现；
2. **删除检查：** 同一阶段结束时实际删除哪些类、字段、分支和 fixture；
3. **版本检查：** 稳态是否仍要求维护两个以上公开合同；
4. **权威检查：** resolution、blocks、evidence、source、continuation 是否只计算一次；
5. **核心纯度检查：** routing/domain/execution 是否仍能看到 `stp-v1/v2/v3`；
6. **前端职责检查：** 前端是否仍根据任务结果重算后端业务成功状态；
7. **测试检查：** 新测试是否替代旧矩阵，还是在旧矩阵上继续乘版本数量。

如果一个方案只能说明“新增什么”，不能说明“删除什么”，则不进入实施计划。

## 7. 决策记录

| 编号 | 状态 | 决策摘要 | 后续影响 |
|---|---|---|---|
| D-01 | `CONFIRMED` | DAG 节点失败局部传播，最终按 User Goal 覆盖计算 Answer Resolution | 需要目标覆盖投影，不能按任务计数映射 |
| D-02 | `CONFIRMED` | Confirmation 是等待用户操作的 Turn State，Answer 尚未产生 | 移除 confirmation → clarification 的错误映射 |
| D-03 | `CONFIRMED` | Preset 可有独立材料入口，但必须进入统一答案投影 | 删除 Mapper/前端 Preset 正文双权威 |
| D-04 | `CONFIRMED` | v3 是首次生产唯一 Semantic Turn Contract；全链验证后删除 v1/v2 | 不保留永久协议 fallback，补齐 v3 目标能力与删除旧版本同步规划 |
| D-05 | `CONFIRMED` | 首次生产发布前重命名 `/api/v2/answers`，默认不保留旧路由 | 后端、前端、测试和文档原子切换 |
| D-06 | `CONFIRMED` | Agent Turn 是闭合交互变体；仅 ANSWER 拥有 answer，resolution 为 COMPLETE/PARTIAL/NO_RESULT | 删除非回答 resolution 和大 optional 字段袋，保留部分回答 + 澄清体验 |
| D-07 | `CONFIRMED` | 单一、版本无关的 Public Agent Turn Projection 替换 ConversationAnswerResult 迁移壳 | Mapper/前端不再重复决定公开语义；新模型必须替换旧模型而非叠加 |
| D-08 | `CONFIRMED` | DAG 首发使用 ready-set 分批、有界并行；动态完成即调度留作数据驱动的未来替换 | 稳定顺序提交、普通节点失败隔离，不建设双 scheduler |
| D-09 | `CONFIRMED` | 所有 SemanticTaskExecutor 支持并发调用，Coordinator 只保留每 Turn maxParallelTasks | 系统级只用maxActiveTurns；不增加Task全局配额、SERIAL/PARALLEL_SAFE或白名单 |
| D-10 | `CONFIRMED` | Turn deadline 到点停止等待，超时/迟到节点不得污染结果，已完成独立分支保留 | Adapter 落实真实 I/O timeout；默认时长待数据决定 |
| D-11 | `CONFIRMED` | 现有“取消回答”升级为端到端 Turn 取消，取消先结算则不产出公开 Turn | 重塑现有 single-flight 为唯一 Active Turn 归属点；补 receipt lease 放弃语义 |
| D-12 | `CONFIRMED` | Task 只产出不可变候选；唯一 Turn 终局门统一 Context、Receipt、诊断和响应语义 | 不新增 settlement framework；修复隐藏部分 Context 提交 |
| D-13 | `CONFIRMED` | 稳态只保留 HTTP→Lifecycle→Resolve→Engine→Projection→Settlement→DTO 一条主链 | 删除 Runtime、旧 Result、业务 Mapper、旧 Production Service，不留转发壳 |
| D-14 | `CONFIRMED` | TaskOutcome 只保留 identity + 互斥终态；成功数据统一为单一 TaskArtifact | 删除交叉状态枚举、nullable 结果袋和 payload/contribution 双权威 |
| D-15 | `CONFIRMED` | TaskArtifact 分离 DAG 使用的 SemanticResult 与公开使用的 Presentation | 下游禁止消费渲染字符串；删除 payload 内多代表示与重复 provenance |
| D-16 | `CONFIRMED` | User Goal 成为 Plan 一等成员；每个 Goal 指定唯一 fulfillment Task，按 Goal Coverage 结算 | 删除 PRIMARY Task 计数、TaskFulfillmentRole 权威与 PlanOutcome |
| D-17 | `CONFIRMED` | 首发 DAG 只保留真实 data input edge；展示顺序不产生执行依赖 | 删除三类 edge、Synthesis 来源双权威和通用输入策略预埋 |
| D-18 | `CONFIRMED` | 当前只读 v3 删除通用 Plan Confirmation；歧义澄清，复杂度由系统预算/降级处理 | 删除确认签名回传基础设施；未来仅审批具体有副作用 Action |
| D-19 | `CONFIRMED` | 自由文本只有 model-led 解释权威；Preset/fallback 产生同一种 Proposal 并进入唯一 Plan Compiler | 删除 Legacy/Shadow/mode matrix、独立 classifier 和双 Plan 编译链 |
| D-20 | `CONFIRMED` | 模型只提出 User Goal Proposal；确定性 Planner 生成 Task、fulfillment mapping 和 data edges | 删除模型 Task/DAG 合同、万能 TaskProposal 字段袋与半解析容错冲动 |
| D-21 | `CONFIRMED` | 首发只保留单节点深模块、独立 Goal 集合和真实 fan-in；Synthesis 收窄为跨领域关系合成 | 删除顺序链、任意模型 DAG 和泛化但不可执行的 Synthesis |
| D-22 | `CONFIRMED` | Portfolio 节点直接构造一个授权 Invocation 调用 Capability，不建立单 Invocation 二级 Plan | 删除 ExecutionPlan/Validator/TrustedPlan/Catalog/Constraints 壳与阶段编号 |
| D-23 | `CONFIRMED` | Retrieval 最多一次按失败类型选择的有效 fallback，并共享 absolute deadline | Adapter 返回 raw attempt；Promotion 只做一次；删除双 fallback 权威和重复 Bundle Bean |
| D-24 | `CONFIRMED` | Portfolio 成功链只构造一次 SemanticResult 与一次 Presentation | 删除 Contribution、Plan→Payload 复制、推荐双权威、Refine 空策略与旧 Material/Composer 平行代际 |
| D-25 | `CONFIRMED` | 确定性 Presentation 是必达 canonical；模型表达仅作一次可选原子替换 | 保留严格 grounding，删除私有熔断、重复 eligibility、伪泛化和公开假降级 |
| D-26 | `CONFIRMED` | GeneralAnswerMaterial 直接归位为最小 GeneralSemanticResult，并使用专用 typed Model Port | 删除 Material→Payload 丢失、无消费者字段、legacy Route/Window、重复开关和兼容入口 |
| D-27 | `CONFIRMED` | Cross-domain Synthesis 只保留 Goal 锚定的一般概念→Portfolio 实例 fan-in | 删除 substring 关系猜测、任意多输入、全文拼接、关系枚举预埋和零收益模型 echo |
| D-28 | `CONFIRMED` | Public Projector 按 UserGoal 顺序只投影 fulfillment Presentation，形成唯一公共正文 | 删除 supporting 正文泄漏、blocks/completedTasks 多权威、虚构阶段快照和前端重算成功状态 |
| D-29 | `CONFIRMED` | ResumeToken 只授权 Conversation，ContextHandle 只定位可续接 Goal Result，服务端 Context 唯一权威 | 删除双 handle 表示、客户端 Context 回传、最近上下文猜测、refinement 断链和 singular receipt handle |
| D-30 | `CONFIRMED` | 新 Turn 请求为 ASK/CONTINUE/RESOLVE_CLARIFICATION closed commands，并统一 requestId | 删除 optional action 字段袋、Plan commands、请求协议选择、双 Semantic Context 与客户端 coveredTopics 权威 |
| D-31 | `CONFIRMED` | Turn Lifecycle 只有一个 TurnExecutionStore 幂等权威，并原子结算 Context + PublicTurn + completed record | Memory/Postgres 不叠加；完成重试精确回放 Answer；删除公共 CompletionReceipt 与部分 Context 提交 |
| D-32 | `CONFIRMED` | 仅保留 Goal/General/Portfolio Fact Expression 三个 typed Model Ports，共享 infrastructure StructuredModelTransport | 删除万能 ModelPort、旧 operations、重复 HTTP adapters/config、schema-provider 假耦合与模型 Suggest/Summary |
| D-33 | `CONFIRMED` | 根模块收敛为 turn，按 lifecycle/planning/execution/capability/projection/continuation/api 深模块组织 | 删除通用技术散包、阶段/迁移命名和跨包随意组合；能力垂直迁移并同步删除旧包 |
| D-34 | `CONFIRMED` | 目标场景 + 共享 Golden Fixtures + 垂直 Replacement Slice，旧生产与测试同步删除 | 防止新架构叠在旧链上；按 Command/Plan→Execution→Capabilities→Lifecycle/API 原子切换推进 |
| D-35 | `CONFIRMED` | General 只回答稳定低风险通用知识，结构验证不宣称事实/证据验证 | 实时/高风险进入边界；General 无伪来源；Synthesis 不把 Portfolio VERIFIED 传播给 General |
| D-36 | `CONFIRMED` | 删除公共 degraded；错误只分 API Error、Task Terminal、Goal Notice | 完整 fallback 不提示降级；详细 failure 只进 diagnostics；缺口按 Goal 精确说明 |
| D-37 | `CONFIRMED` | 版本分为 ContentReleaseId、PresetRevision、Internal Schema、Deployment Identity | 每 Turn 固定 release；Preset revision 不替代事实版本；Context 真重验；删除业务层通用版本 switch |
| D-38 | `CONFIRMED` | PublicAgentTurn 顶层 kind 闭合五种 variants，Answer 按 GoalResult 提供唯一正文 | SECTIONED/RECOMMENDATION 两种公开 Presentation；局部澄清附 Answer；前端只按 kind 渲染 |
| D-39 | `CONFIRMED` | Conversation/Prompt 不落盘；PublicTurn 短期加密重放；Context typed 加密保留；Token/hash/clear 严格分层 | 幂等/续接不保存完整对话；fingerprint 用 HMAC；diagnostics 无内容；Store failure 不破坏 Answer |
| D-40 | `CONFIRMED` | Observability 只读投影；保留 Turn/Goal/Task/Model/Retrieval/Settlement/Contract 低基数指标 | 正常部分结果不告警；fallback 内部可见不公开；删除旧事件/前端冲突监控；先基线后阈值 |
| D-41 | `CONFIRMED` | Frontend Goal-first：正文/缺口/来源/操作优先，Execution 默认折叠 | 单 Goal 极简；PARTIAL 按 Goal 说明；local clarification就地；推荐内嵌；前端不重算业务语义 |
| D-42 | `CONFIRMED` | 一个 TurnDeadline + SettlementReserve；两个并发边界；attempt/size预算归各能力 | 删除 TaskExecutionAllowance 字段袋和按Task均分字符；所有I/O取min cap/remaining；参数由指标调整 |
| D-43 | `CONFIRMED` | Public Content是可重建只读模型，Agent State是短期事务状态；不跨库事务、不生产Memory fallback | State claim前故障只影响Agent；同release Bundle保护内容读取；只读Answer可在settlement故障时无续接交付 |
| D-44 | `CONFIRMED` | Eval按最终能力组织；hard errors零容忍；模型能力安全+质量独立晋级；INCOMPLETE禁止启用 | 删除P3/P4/P5矩阵与旧seam；OFFLINE/CONTROLLED_PROVIDER/OBSERVATION分工；Eval工具隔离生产包 |
| D-45 | `CONFIRMED` | 服务端closed contracts+最小权限是安全边界；所有输入/模型/内容/引用均不可信并在唯一Owner校验 | 模型无执行权；typed SQL/relative route/plain text/Token加密/分层DoS；删除Prompt式和重复表演性安全 |
| D-46 | `CONFIRMED` | 无版本Agent资源：POST turns、DELETE active turn、GET/DELETE current conversation；Bearer ResumeToken | 关闭O-04；删除/v2/answers和conversation-context；所有commands单入口；Frontend/Backend原子切换 |
| D-47 | `CONFIRMED` | Slice 0基线+六个Replacement Slices；Slice 5外部边界只整体合并/部署 | 每Slice文件级Manifest/删除/验证；同Slice临时bridge exit为零；不部署中间双栈 |
| O-04 | `CLOSED` | 新 Agent 轮次入口的最终路径 | 由D-46定稿为 `POST /api/agent/turns` |

## 8. 下一项讨论

API 新路径、取消与Conversation资源已由D-46定稿；当前没有遗留的API命名开放项。

架构Spec与Slice 0～6文件级实施计划均已完成并通过独立评审修订；下一步只有在用户明确授权后才从Slice 0开始执行源码/测试改造。
