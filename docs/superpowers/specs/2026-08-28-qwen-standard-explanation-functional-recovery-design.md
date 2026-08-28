# Qwen General Explanation 松契约与功能性认证设计

<!-- DOCUMENT_STATUS: APPROVED -->

> 日期：2026-08-28
> 状态：用户已批准；本文件是当前实施的设计权威
> Guardian 分级：`APPROVED_LEVEL_3_REPLACEMENT`；允许按本文件和对应实施计划替换生产权威
> 目标文件：`docs/superpowers/specs/2026-08-28-qwen-standard-explanation-functional-recovery-design.md`
> 直接关联：A2-80、A2-85、A2-86、A2-117、GATE-19，以及 Qwen General 真实 Provider 质量报告
> 关联权威：`docs/16-Agent单权威持续收敛范式.md`、`docs/agent-architecture-status.json`、`docs/superpowers/specs/2026-08-21-configured-user-selectable-model-catalog-design.md`、`docs/superpowers/specs/2026-08-25-provider-structured-output-capability-and-model-switching-design.md`、`docs/superpowers/specs/2026-08-27-goal-v2-promotion-and-comparison-pair-identity-design.md`

## 1. 审核结论摘要

本设计不接受“先使用真实用户敏感数据跑通，之后再补隐私”的解释。它采用的原则是：

> 先用固定合成数据证明产品闭环和模型链路可用，并用隔离、短期、可回收的诊断能力定位 Provider 表达问题。诊断实验室如需接触任何非合成数据，必须另行完成设计、门禁与明确批准；本设计不预设一个无验收标准的“以后再补隐私”承诺。

当前功能性问题不是系统“太严格”这一句可以概括，而是不同风险被压成了同一种 Turn 失败：低风险格式差异、可选内容缺失、关键语义缺失、临时传输故障、模型身份不一致和未授权副作用没有分层处理。

本设计把 **Qwen 的 `GENERAL_KNOWLEDGE / EXPLANATION`** 作为不可拆分的首个可发布能力，统一覆盖 `CONCISE / STANDARD / DETAILED` 三个 depth：

1. Provider 只提交语义草稿，不再直接生成 canonical 领域对象；
2. 对已批准的无损表达差异执行确定性归一化；
3. 可选 caveats 损坏时隔离丢弃，不拖垮有效核心答案；
4. definition 或 mechanism 缺失、越权、安全问题和 canonical 失败仍严格拒绝；
5. 临时传输失败允许同 Qwen、同模型、共享 deadline 的最多一次重试；
6. 用固定合成评测集和盲审证明 L3 用户任务完成率；
7. canonical 永远精确包含两条 statement：一条 `DEFINITION` 与一条 `MECHANISM`；depth 只改变两条文本内部的自然句数量与离线质量门；
8. 100 个固定主题分别以三档问法形成 300 条认证样本；三档各自达标后，Qwen General Explanation 才能整体标为 READY；
9. Comparison 在独立认证前由 Goal 识别，但在 General Provider 调用前固定返回不可重试的 `BOUNDARY`，不得进入 v4 wire；
10. 平台首版可发布不等于全部 Provider 能力已完成，项目总状态继续为 `IN_PROGRESS`。

本设计明确不引入 Spring AI，不做跨模型 fallback，不做模型修复调用，不放宽 required-tool envelope，不改变 Goal Provider Draft v2，也不允许模型取得任何工具执行权。

## 2. 背景与问题定义

### 2.1 当前链路的职责错位

当前 General 路径近似为：

```text
LLM 输出
  -> Provider Draft Schema（精确字段、精确数组长度、精确标点）
  -> deterministic Compiler
  -> general.draft.v2
  -> Codec
  -> semantic Validator
```

`general.provider-draft.v3` 同时要求模型完成：

- 语义回答；
- 分支与 depth 回显；
- 精确句子数组长度；
- 固定字段包装；
- 固定标点形式；
- caveats 的完整两层结构；
- Comparison 的 pair 身份；
- 与 canonical 下游结构相配合的表示。

这使不确定的生成模型同时扮演领域解释器、Schema 组装器和序列化器。任何一层写法偏差都在进入业务语义判断以前失败。

### 2.2 风险必须分级

本设计把异常分为五类，而不是继续使用单一 `ACCEPT / REJECT` 思维：

| 内部等级 | 含义 | 示例 | 单 Turn 处理 |
|---|---|---|---|
| `EXACT` | Provider 表达已满足 v4，无需任何内容归一化 | 两个核心字段均为合法字符串数组 | 编译并继续 |
| `NORMALIZED` | 仅发生已批准、无损、确定性的表达归一化 | string→string[]、空白或终止标点归一化、忽略未知字段 | 编译并继续，记录规则计数 |
| `DEGRADED` | 可选增强内容损坏或缺失，核心语义仍完整 | caveats 缺失、null 或整体结构损坏 | 丢弃可选部分，核心答案继续 |
| `INCOMPLETE` | 关键业务语义缺失或无法安全映射 | definition/mechanism 缺失、为空或明显跑题 | 拒绝当前结果 |
| `UNSAFE` | 违反安全、身份、权限或资源硬边界 | secret、错模型、未授权工具、重复键、超限 | 严格拒绝，阻塞 READY |

这些等级是内部诊断和认证维度，不直接扩充 `PublicAgentTurn.kind`。成功的 `EXACT`、`NORMALIZED` 和 caveats-only `DEGRADED` 均可投影为 `ANSWER / COMPLETE`；`INCOMPLETE` 与 `UNSAFE` 按现有失败合同投影，不把内部细节泄露给用户。

### 2.3 功能性不是 HTTP 200

功能性按四层定义：

```text
L0：Provider 成功返回
L1：服务端能解析、归一化并编译
L2：关键业务语义完整且通过运行时准入
L3：用户能够读懂回答并完成实际提问任务
```

本设计的 READY 是 L3 认证，不是只统计 HTTP 2xx，也不是只追求 Provider Draft Schema 通过率。

## 3. 目标、首发范围与非目标

### 3.1 首发能力切片

首版能力矩阵冻结为：

| Provider | Operation / Goal | Depth | 首发状态 | 是否阻塞首版发布 |
|---|---|---|---|---|
| Qwen | `TURN_INTERPRETATION` / Goal v2 | 既有范围 | 沿用既有认证 | 是 |
| Qwen | `GENERAL_KNOWLEDGE / EXPLANATION` | `CONCISE / STANDARD / DETAILED` | 三档全部通过本设计门后整体 `READY` | 是，不允许拆档发布 |
| Qwen | `GENERAL_KNOWLEDGE / COMPARISON` | 不适用 | Provider 调用前固定 `BOUNDARY`；独立认证前运行时不可达 | 否 |
| GLM | Goal / General | 全部 | `BLOCKED`，首版不可选择 | 否 |

首版产品承诺是“Qwen 可完成 Goal 解释与三档 General Explanation”，不是“双 Provider 任意切换已经可用”。depth 由用户问法推导而非独立可选入口，所以三档不能以“隐藏未认证入口”的方式拆开发布。

### 3.2 L3 成功定义

一个 Qwen Explanation Turn 只有同时满足以下条件，才算 L3 成功：

1. 用户提出普通技术知识问题；
2. Goal 链路识别为 `GENERAL_KNOWLEDGE / EXPLANATION`，并从问法可靠派生预期 depth；
3. 冻结选择中的 Qwen 被调用，未发生跨模型替换；
4. definition 非空、围绕问题且具有信息量；
5. mechanism 非空、围绕问题且说明主要机制；
6. 正文以中文为主，技术名词可保留英文；
7. 无明显事实错误、自相矛盾或答非所问；
8. 普通目标用户能够读懂；
9. 页面最终显示 `ANSWER / COMPLETE`；
10. caveats 可以为空，缺失 caveats 不降低为 `PARTIAL`；
11. `CONCISE` 直接、无明显重复，`STANDARD` 信息量均衡且足够，`DETAILED` 明显深于 Standard，并能自然覆盖场景、边界、取舍或常见误区等进阶内容；这些质量由分 depth 盲审证明，不伪装成运行时固定 aspects。

### 3.3 非目标

本设计不包含：

- 引入 Spring AI 或重写通用 Provider Client；
- 建立任意 Provider 的通用容错抽象；
- 模型 repair Prompt 或第二次语义生成调用；
- 跨模型 fallback、自动切换到 GLM 或隐藏模型替换；
- 放宽 Goal Provider Draft v2、Goal canonical 合同或 Goal 语义 validator；
- 放宽 General Comparison pair 身份与容量边界；
- 重构整个 Agent Lifecycle；
- 让模型 tool call 获得外部执行授权；
- 在生产环境记录 Provider 原始请求或响应；
- 使用真实用户数据、私有项目数据或高风险专业建议建立评测集；
- 证明 Qwen Comparison 或 GLM 已达到 READY；
- 宣称 GATE-19 或项目总体完成。

## 4. 已审阅方案与取舍

### 4.1 方案 A：Qwen Explanation 三档整体切片（采纳）

只在 General Explanation 的 Provider Draft 边界放宽表达，canonical 继续严格；三档共享一条生产权威和一组安全边界，用分 depth Prompt 与盲审建立深度质量证据；发布与 GLM/Comparison 认证解耦，但 Explanation 三档彼此不解耦。

优点：改变面最小，能直接验证“合同设计问题”和“模型质量问题”各占多少；失败可以按版本级回滚；不会把试验性策略扩散到 Goal、Comparison 或 GLM。

代价：短期会存在能力矩阵，产品不能声称所有模型和所有 General 模式都可用。

### 4.2 方案 B：全 Provider、全 Operation 一次性松契约（拒绝）

该方案会同时改变 Goal、Explanation、Comparison、Qwen 和 GLM，无法分离模型随机性、合同问题和 Provider 可用性问题，也会一次性扩大安全审计面。

### 4.3 方案 C：先引入 Spring AI（拒绝）

框架可以统一 transport、retry 和 structured output API，但不能决定哪些格式差异可恢复、哪些语义缺失必须拒绝、何时可 fallback、哪些数据可出境。当前根因是边界策略，不是 Client API 缺失。

### 4.4 方案 D：生产同时保留 v3/v4 双解析器并动态切换（拒绝）

双解析器会形成第二套运行时权威、增加不可解释的分流和回滚状态。v3/v4 双跑只允许在离线实验室针对同一原始响应执行；生产提升必须原子替换，回滚使用 Git/JAR 版本，不增加 `parser.version` 运行时开关。

## 5. 权威、版本与替代范围

### 5.1 提升后的权威表

| 概念 | 当前权威 | 本设计批准并实施后的权威 |
|---|---|---|
| Qwen General Provider wire-shape | `general.provider-draft.v3` | `general.provider-draft.v4` |
| Qwen General canonical application contract | `general.draft.v2` | `general.draft.v3` |
| General deterministic compiler profile | 当前 v3 profile | 新的 `general-provider-draft-compiler.v4` |
| Qwen selection | `qwen-3-7-flash-v6` | `qwen-3-7-flash-v7` |
| Goal Provider/canonical contract | Goal Draft v2 / 既有 canonical | 不变 |
| required-tool response carrier | 当前 strict envelope | 不变 |
| public temporary-unavailable code | `SELECTED_MODEL_TEMPORARILY_UNAVAILABLE` | 不变，复用 |
| 模型身份与 Operation binding | frozen snapshot | 不变，且 fingerprint 必须覆盖 v4/v3/compiler profile |

`general.draft.v3` 即使与 v2 的 JSON 外形高度相似也必须升版，因为三个 depth 的合法语义集合都已改变：canonical 统一只保留 `DEFINITION / MECHANISM` 两种可信 role aspects，句数按 depth 使用不同自然范围，caveats 可为空且不影响完成度。合同版本表达的不只是字段外形，也包括 accepted state space。

### 5.2 对既有批准 Spec 的定向替代

本文件已获用户批准；在对应 Replacement Slice 实施完成并取得新鲜证据后，只替代 `2026-08-25-provider-structured-output-capability-and-model-switching-design.md` 中以下 Qwen General 条款：

1. “同请求永不重试”替换为本设计的同模型、有界、共享 deadline 的 transport retry；
2. General v3 Provider Draft 与 exact sentence formatting 替换为 v4 松表达合同；
3. General canonical v2 替换为 canonical v3；
4. “任何 Provider Draft schema 差异均终止 Turn”替换为闭集归一化和 optional caveat 隔离；
5. “任何原始 Provider 输出不得保存”增加严格限定的合成数据诊断实验室例外；生产与真实数据路径的禁止仍不变；
6. 100% 单次结构命中门替换为分母分离的 L1/L2/L3 认证门；
7. 所有 Provider/能力共同通过的完成门改为能力级认证与平台发布门分离；Qwen Explanation 内部三个 depth 仍作为不可拆分能力；
8. General Comparison 在独立认证前不得进入 Provider：Goal 可识别它，但运行时固定结算为不可重试 `BOUNDARY`。

以下条款不被替代：

- frozen `modelRef / provider / operation binding / selectionVersion / protocol profile`；
- required-tool carrier 严格性；
- strict JSON parse-once；
- duplicate key、trailing token、multi-root 拒绝；
- 请求/响应大小、深度、数组、并发和 deadline 限制；
- 无跨模型 fallback；
- 工具 arguments 不构成执行授权；
- secret 隔离；
- canonical Schema 与领域 validator 的 fail-closed；
- 旧 selection stale，不静默迁移。

本设计不替代 `2026-08-27-goal-v2-promotion-and-comparison-pair-identity-design.md` 的 Goal v2 和 Comparison pair 身份规则。

### 5.3 文档状态与批准流程

当前文件使用 `DOCUMENT_STATUS: APPROVED`，并与 `docs/00-文档状态索引.md`、`scripts/documentation-check.ps1` 同批注册。用户已明确批准本 Level 3 方向并要求在实施计划完成后并行实施；对应计划必须列出精确文件、测试、原子提升顺序和证据账本义务。

批准不等于生产事实已经改变：在候选 JAR 完成 300 条封存认证和全部安全门以前，当前 production binding、`docs/agent-architecture-status.json` 与 GATE 状态保持原样。只有通过 Gate F4/F5 后才能写入 READY/生产提升事实。

## 6. 目标架构

### 6.1 唯一生产链

```text
Immutable User Submission
  -> frozen ModelExecutionSnapshot
  -> Qwen TURN_INTERPRETATION（Goal v2，不变）
  -> trusted General Goal(kind=EXPLANATION, depth=CONCISE|STANDARD|DETAILED, topic)
  -> frozen Qwen GENERAL_KNOWLEDGE binding
  -> strict required-tool request/response envelope
  -> strict JSON parse-once + resource guards
  -> general.provider-draft.v4 admission
  -> general-provider-draft-compiler.v4
       -> allowlist projection
       -> deterministic normalization
       -> optional caveat isolation
       -> trusted-field derivation
  -> general.draft.v3 canonical Schema
  -> GeneralDraftCodec
  -> runtime semantic admission
  -> Answer composition
  -> PublicAgentTurn ANSWER / COMPLETE
```

生产只存在这条 authority path。v3/v4 双解析结果不得在生产请求中竞争、投票或 fallback。

### 6.2 深模块边界

本设计引入的是一个收敛复杂度的内部深模块，而不是新的公共大接口：

```text
General Provider Draft Admission
  输入：strictly parsed JSON tree + trusted Goal + frozen binding
  输出：canonical general.draft.v3 tree 或 closed failure
  隐藏：字段投影、归一化、caveat 隔离、句子展开、可信字段派生、诊断计数
```

模块的公共语义保持小而稳定：

- `compile(rawDraft, trustedGoal, binding)`；
- 成功返回 canonical tree 与非敏感诊断摘要；
- 失败返回闭集 layer/reason；
- 不接触网络、不发起 Provider 调用、不持久化、不执行工具；
- 同输入必须产生字节语义等价的同输出与同诊断。

`StructuredOutputGateway` 仍是 Provider structured output 的唯一外部入口。实现可以在现有 compiler 家族内深化模块，不建立第二个绕过 Gateway 的 General 调用入口。

### 6.3 为什么 Schema 先宽、canonical 后严

Provider Draft v4 是不可信 wire admission，不是领域真相。它只回答“这些值是否属于允许归一化的表达集合”。canonical v3 才回答“应用可以接受什么确定性领域状态”。

```text
Provider 表达：string 或 string[]、可选 caveats、未知字段可投影忽略
                       ↓
Compiler：只做闭集、无损、确定性转换
                       ↓
Canonical：固定 topic、role、aspect、非空数组和业务上限
```

这不是把 canonical 变松，而是把“模型如何表达”和“系统最终接受什么”拆成两个权威。

## 7. `general.provider-draft.v4` wire contract

### 7.1 Explanation 分支

Qwen Explanation 只需表达：

```json
{
  "definition": "依赖注入是由容器负责提供对象依赖的一种设计方式。",
  "mechanism": [
    "对象声明所需依赖，容器在创建对象时解析并注入这些依赖。",
    "调用方因此不必在业务代码中直接构造具体实现。"
  ],
  "caveats": [
    {
      "kind": "APPLICABILITY_BOUNDARY",
      "sentences": "它改善的是依赖管理，不会自动保证模块设计合理。"
    }
  ]
}
```

规则：

- required：`definition`、`mechanism`；
- 两者均只接受 `string | string[]`；
- 字符串或数组 item 必须在既有字符与长度资源上限内；
- `caveats` optional，wire admission 对其值保持宽容，由 Compiler 按 §8.4 整体验证和隔离；
- root `additionalProperties` 允许存在，以便 Compiler 做 allowlist projection；
- Provider 不再回显 `topic`、`kind`、`depth`、statement `role`、`aspects`、canonical ID、selection 或 binding 身份；
- Provider 即使输出这些冗余字段，也只能作为 unknown field 被忽略和计数，不能覆盖可信请求。

### 7.2 Comparison 在 Provider 前不可达

v4 wire contract 只描述 Explanation，不保留 Comparison 兼容分支。Goal v2 仍可识别 `GENERAL_KNOWLEDGE / COMPARISON`，但 Lifecycle 必须在 General Provider 调用前固定结算：

```text
PublicAgentTurn.BOUNDARY
code = OUT_OF_SCOPE
retryable = false
message = 当前暂不支持直接比较；请分别询问这些概念。
GENERAL_KNOWLEDGE Provider calls = 0
```

不得把 Comparison 降级成 Explanation、不得拆成多次 Provider 调用、不得回退旧 v3 parser，也不得静默调用 GLM。Comparison 的 pair 身份和容量规则继续保留，供未来独立设计与认证使用；本设计不以它们已存在为由让 Comparison 进入生产 Provider 路径。

### 7.3 Schema 的有意宽点

v4 wire Schema 必须准确表达以下边界：

1. root 仍必须是 object；
2. Explanation 的 core 字段类型只允许 string 或 string array；
3. unknown root fields 可被 admission 接受，但只由 Compiler 计数并丢弃；只累计数量，不记录字段名和值；
4. `caveats` optional，允许缺失、null 或任意受总体资源限制的 JSON 值进入 Compiler，以便 optional 字段损坏不先杀死 core；
5. required-tool envelope、JSON parser 与总体资源守卫在 v4 Schema 以前执行，因此宽 `caveats` 不允许绕过 duplicate key、trailing token、multi-root、body size、depth 和 array 上限；
6. Schema 不是“随便接受 JSON”；core 类型、字段存在性和总资源边界仍是硬门。

General 的 unknown-key 策略不得泛化到 Goal：Goal 字段承载主体、约束、指代与路由安全语义，因此继续 `UNKNOWN_KEY` 封闭拒绝；General Explanation 只消费纯 prose 的 definition/mechanism/caveats，未知根字段既不能影响 canonical，也不能触发工具，才允许忽略并计数。该不对称不是 Goal、Comparison、tool 或其他 Operation 的先例。

## 8. 确定性归一化与编译规则

### 8.1 归一化规则必须闭集且版本化

`general-provider-draft-compiler.v4` 只允许以下规则：

| Rule ID | 行为 | 等级 |
|---|---|---|
| `TRIM_TEXT` | 删除字符串首尾空白 | `NORMALIZED` |
| `COLLAPSE_MEANINGLESS_WHITESPACE` | 折叠不承载语义的连续空白；代码/技术符号内部不得盲改 | `NORMALIZED` |
| `UNICODE_NORMALIZE_NFC` | 使用 Unicode NFC；不使用会折叠兼容字符的 NFKC | `NORMALIZED` |
| `WRAP_STRING_AS_ARRAY` | core/caveat sentence 的 string 转单元素 string[] | `NORMALIZED` |
| `JOIN_ROLE_SENTENCES` | 同一 core role 的归一化数组按单个 ASCII 空格连接为一条 canonical statement text | 确定性编译，不改变语义等级 |
| `NORMALIZE_TERMINAL_PUNCTUATION` | 统一已有句末标点或在缺失时补 `。` | `NORMALIZED` |
| `ALLOW_INTERNAL_PUNCTUATION` | 接受分号、冒号、括号、引号和技术符号，不作为拒绝理由 | 不单独计内容修改 |
| `MISSING_CAVEATS_AS_EMPTY` | missing/null caveats 转 `[]` | `DEGRADED` 或独立 optional 计数 |
| `DROP_INVALID_OPTIONAL_CAVEATS` | caveats 集合或任一 item 不合法时整体丢弃 | `DEGRADED` |
| `PROJECT_KNOWN_FIELDS` | 忽略 unknown root fields，仅累计 `UNKNOWN_FIELD_COUNT` | `NORMALIZED` |

规则顺序固定为：root allowlist projection 与 unknown 计数 -> core 类型检查 -> string 包装为数组 -> NFC -> trim -> 连续 Unicode whitespace 折叠为一个 ASCII 空格 -> 句末标点归一化 -> 空值/单项长度/分 depth 自然句数校验 -> 同 role 数组确定性连接 -> optional caveats 整体验证与隔离。不能通过配置动态添加新规则。相同输入重复执行必须幂等：`normalize(normalize(x)) == normalize(x)`。

句末标点归一化采用闭集算法：保留尾部闭合符号 `”’」』）》】`；若闭合符号前的最后一个字符为 `.。!?！？`，统一替换为 `。`，否则在闭合符号前补 `。`。正文内部的分号、冒号、括号、引号和技术符号不改写。连续 whitespace 折叠只改变空白码点，不解析或重写普通字符、代码 token 与技术符号。

### 8.2 明确禁止的“归一化”

以下行为不是容错，而是在猜测或改写语义，必须拒绝：

- number、boolean、object 自动转 string；
- 从 prose 中用正则提取一段疑似 JSON；
- 递归或多次 JSON decode；
- 在 definition 与 mechanism 之间搬运或拼接内容；
- 为满足句数上限裁剪句子；
- 自动补写 definition、mechanism 或 caveat 正文；
- 根据文本猜测 EXPLANATION/COMPARISON 分支；
- 根据关键词给 statement 伪造 `TYPICAL_USAGE` 或 `APPLICABILITY_BOUNDARY`；
- 改写主体、维度、模型身份、Goal、depth 或用户问题；
- 用另一模型或另一 Prompt 修复当前响应。

同一 role 内把已分别归一化并验证的数组 item 按单个 ASCII 空格连接，不属于语义拼接：不改变 item 顺序，不跨 role 搬运，不增加、删除或改写正文。该机械步骤只把 Provider 的包装差异收敛为 canonical 的精确两条 statement。

### 8.3 分 depth core 句子策略

归一化后按中文自然句计数器统计三个 depth；数组 item 数不替代自然句数：

| Depth | definition | mechanism | 总数 | Prompt 目标 |
|---|---:|---:|---:|---|
| `CONCISE` | 精确 1 句 | 精确 1 句 | 精确 2 句 | 1 + 1，直接且无重复 |
| `STANDARD` | 1..3 句 | 1..3 句 | 2..6 句 | 2 + 2，约 4 句的均衡答案 |
| `DETAILED` | 4..6 句 | 4..6 句 | 8..12 句 | 自然展开进阶场景、边界、取舍或误区 |

三个 depth 共同遵守：

- 内部分号、冒号、括号、引号、英文缩写、版本号和技术符号允许存在；
- 单项仍受现有最大字符数约束；
- 超过上限时拒绝，不裁剪；
- 空白归一化后为空时拒绝；
- 数组含非字符串 item 时 core 拒绝，不丢掉坏 item 后继续。

通过自然句数校验后，definition 数组只编译成一条 `DEFINITION` statement，mechanism 数组只编译成一条 `MECHANISM` statement；canonical statement 总数永远精确为 2。自然句范围统计 statement text 内的句子，不等于 statement 数量。

Prompt 目标不是 canonical 的额外下限：例如 Standard 合法的 1+1 仍可进入 canonical，但若信息量不足会在 Standard L3 盲审失败。服务端不为达到 Prompt 目标补写、拼接或裁剪正文。

### 8.4 caveats 隔离

`caveats` 是可选增强，不是任一 Explanation depth 的完成条件：

1. missing 或 null -> canonical `[]`；
2. 合法 array 的每项必须是 `{kind, sentences}`；
3. `kind` 仍限于既有闭集 `APPLICABILITY_BOUNDARY | RISK | EXCEPTION`；
4. `sentences` 允许 string 或 string[]，应用同一文本归一化和资源上限；
5. 集合不是 array、任一 item 不是 object、缺字段、kind 非法、sentences 类型非法或归一化后为空时，**整组 caveats 丢弃**；
6. 不做部分 salvage，避免“为什么保留这一条、丢另一条”的第二套隐性策略；
7. 记录 `DROPPED_INVALID_OPTIONAL_CAVEATS`，但不记录原始值；
8. definition/mechanism 有效时，caveats-only 损坏仍返回 `ANSWER / COMPLETE`；
9. 安全、大小、深度、数组总量等硬边界先于本规则，超限不能以“可选字段”名义绕过。

若该 reason 在认证样本中的发生率超过 §14 预算，触发合同漂移审核，但单个 Turn 不失败。

### 8.5 可信字段派生

Compiler 必须只从 frozen request context 派生：

- `topic`：来自已通过 Goal 准入的可信 topic；
- `kind`：来自 trusted Goal；
- `depth`：来自 trusted Goal；
- statement `role`：definition 编译为唯一 `DEFINITION`，mechanism 编译为唯一 `MECHANISM`；
- `aspects`：三个 depth 的 definition 都只能是 `[DEFINITION]`，mechanism 都只能是 `[MECHANISM]`；
- subject/dimension：Explanation 为 null 或缺省，按 canonical shape 固定；
- caveats：由 v4 optional 内容机械映射。

Provider 输出不能覆盖这些字段。Compiler 也不能因为一段文字“看起来谈到了适用场景”就标注 `APPLICABILITY_BOUNDARY`。

## 9. `general.draft.v3` canonical contract

### 9.1 不变量

canonical v3 继续是严格应用合同：

- root `additionalProperties:false`；
- required：`topic`、`statements`、`caveats`；
- `topic` 非空且来自可信 Goal；
- `statements` 非空、受总量上限约束；
- Explanation 的 `statements` 精确为 2；
- `caveats` 永远是 non-null array，可为空；
- 每个 statement 字段集合、role、text、subject、dimension、aspects 严格；
- Codec 与 semantic Validator 只接收 canonical v3，不读取 Provider 原始树。

### 9.2 三档 Explanation 的 canonical 合法集合

- 精确一条 `DEFINITION` 与一条 `MECHANISM` statement，总数恒为 2；
- `CONCISE`、`STANDARD`、`DETAILED` 的自然句范围精确采用 §8.3；
- DEFINITION 的 aspects 精确为 `[DEFINITION]`；
- MECHANISM 的 aspects 精确为 `[MECHANISM]`；
- 不再由服务端给任何 depth 强行标注 `TYPICAL_USAGE`、`APPLICABILITY_BOUNDARY`、`TRADE_OFF` 等未被模型明确结构化声明的语义；
- caveats 为空不影响 `COMPLETE`；
- canonical Schema 或 Codec/Validator 任一失败仍为 fail-closed，不能再回退到 v3 Provider parser 或直接返回文本。

### 9.3 运行时语义准入的职责上限

运行时确定性 Validator 只验证机器能够可靠判断的条件：

- core 是否存在、非空且数量合法；
- trusted role/aspect/branch 是否一致；
- 中文是否为主要表达语言；
- canonical `topic` 是否与 trusted Goal 精确一致；若增加正文 topic gross-mismatch 守卫，只允许基于版本化的可信 topic anchor 做保守确定性拒绝，不能用关键词覆盖率假装证明答案相关；
- 是否违反 closed scope、资源、安全和权限边界。

运行时 Validator 不通过关键词、句式或服务端打标签假装证明：

- 定义一定事实正确；
- 机制一定充分完整；
- 文本一定覆盖 typical usage；
- 文本一定覆盖 applicability boundary；
- 用户一定读得懂。

这些属于离线语义评测与人工盲审职责。

## 10. required-tool carrier 与安全边界

required-tool 继续只作为只读结构化输出载体，不是生产工具执行入口。

以下条件保持严格拒绝：

- tool call 缺失；
- tool 名不是 frozen binding 批准的 carrier；
- 多 choice、多 tool call 或重复 carrier；
- tool arguments 与 assistant content 混合；
- refusal 与成功 tool call 混合；
- finish reason 非批准值；
- arguments 不是单一 JSON root；
- duplicate key、trailing token 或 multi-root；
- tool arguments 请求或暗示执行外部操作；
- 模型尝试调用 `executeInvestment` 等任何未授权工具；
- Provider/modelRef/selectionVersion/binding/profile 不一致。

本设计只放宽批准 carrier 的 arguments 内 General Draft 表达，不放宽 envelope。

## 11. 有界 transport retry

### 11.1 适用条件

一次 Turn 的 General Provider 调用最多两次 attempt：initial + 1 retry。仅以下临时基础设施失败可重试：

- 连接建立失败或连接 reset；
- Provider 429：有 `Retry-After` 且值 `<=1s` 时按该值等待；无 `Retry-After` 时使用 `100..250ms` 抖动；值 `>1s` 时不重试；
- 明确批准的 502、503、504；
- 在没有取得可用响应体时发生的 eligible timeout，且第二次调用仍能在原 deadline 内完成。

### 11.2 不可重试条件

以下情况不能重试：

- HTTP 2xx 后的 envelope、finish reason、tool carrier、JSON、Provider Draft、Compiler、canonical Schema、Codec 或 semantic failure；
- 400/401/403/404 等配置、身份或调用错误；
- 模型身份或 binding 不一致；
- secret、安全、权限、资源边界失败；
- deadline 已无足够预算；
- 等待完成后 General absolute deadline 的剩余时间少于 3 秒；
- 429 的 `Retry-After >1s`；
- 已经执行过一次 retry；
- 为改变答案质量而重新生成；
- 切换到 GLM 或其他模型；
- repair Prompt 或不同 Prompt。

### 11.3 身份、deadline 与计量

- 两次 attempt 必须使用同一 `modelRef`、Provider、selectionVersion、Operation binding、protocol profile、Prompt 语义和请求内容；
- Turn `requestId` 不变；
- 每次 Provider 尝试使用唯一 `providerAttemptId`；
- 两次调用共享原始 absolute deadline，不重置预算；
- 无 `Retry-After` 时使用含端点的均匀抖动 `100..250ms`；服务端 `Retry-After` 只接受 `<=1s`，不得为分钟级配额等待吞掉共享 deadline；
- retry 等待结束后必须重新计算 deadline；剩余时间少于 3 秒时不发起第二次 attempt；
- 记录 attempt count、失败类别、总延迟、token/cost 元数据与“请求可能已到达但响应丢失”的潜在重复计费；
- 不记录 Prompt、原始用户问题或 Provider 原始响应。

纯文本生成在响应丢失时可能发生两次计费。本设计接受这一有限风险，但要求按 attempt 计量并在可用性报告中单列。

## 12. 失败投影、幂等与用户重试

### 12.1 公开失败码

本设计不新增 `PROVIDER_TEMPORARILY_UNAVAILABLE` 这一平行 public code。内部 transport 分类最终复用既有：

```text
CAPABILITY_UNAVAILABLE
  code = SELECTED_MODEL_TEMPORARILY_UNAVAILABLE
  retryable = true
```

429 在 `Retry-After >1s`、deadline 不足或第二次仍被限流时，继续按既有 `SELECTED_MODEL_RATE_LIMITED` 表达；connect/reset/502/503/504 在有界重试耗尽后复用 `SELECTED_MODEL_TEMPORARILY_UNAVAILABLE`。结构/语义不可采纳继续投影到 `SELECTED_MODEL_INVALID_RESPONSE`。内部必须保留 `TRANSPORT / PROVIDER_DRAFT / COMPILER / CANONICAL_SCHEMA / SEMANTIC / SAFETY` 层级，公开响应不泄露具体字段内容。

### 12.2 requestId 语义

必须区分两种重试：

1. **客户端不知道服务端是否结算**：例如响应在返回途中丢失。客户端复用同一 `requestId`，只用于查询/重放原 settlement；服务端不得再次调用 Provider。
2. **服务端已明确结算临时不可用**：用户点击“重试 Qwen”，创建新 `requestId`，但复制同一 immutable submission 与同一模型选择意图，并基于当前可用目录生成合法快照。

Provider 派生正文仍遵守现有不持久化边界：同 requestId 的已结算重放按现有 `REPLAY_BODY_NOT_RETAINED` 语义处理，不因本设计引入新的正文持久化。

### 12.3 UI 行为

临时不可用时：

- 保留用户原提交的可见内容；
- 明确显示“所选 Qwen 暂时不可用”；
- 提供“重试 Qwen”动作；
- 不自动切换 GLM；
- 不把 transport failure 描述为“模型回答格式错误”；
- 不显示 Provider HTTP 状态、endpoint、raw body 或内部 reason path。

## 13. Provider 诊断实验室

### 13.1 它是什么

Provider 诊断实验室是一个与生产运行时隔离的 CLI/测试 runner，用固定合成问题调用 Provider、短期保存原始响应，并把同一响应离线交给 v3 与 v4 解析链比较。它用来回答：

- 模型实际返回了什么形状；
- 哪些失败只是格式偏差；
- v4 能无损恢复多少 v3 拒绝样本；
- 哪些样本仍缺失关键语义；
- 每条归一化规则发生多频繁；
- 不同 temperature 对 L3 质量与失败率的影响。

它不是生产日志系统，也不等同于完整 Agent 评测集。实验室是**采样、回放与诊断基础设施**；固定问题集、盲审标签和门槛共同构成评测体系。

### 13.2 隔离边界

实验室必须满足：

- 独立 CLI 或 test runner；
- 不注册为生产 Spring Bean；
- 不提供 HTTP endpoint；
- 只读取版本固定的合成 corpus case ID；
- 禁止传入任意访客文本、真实姓名、账号、资产、Cookie、token、私有项目内容或生产 conversation；
- raw artifact 写到仓库外独立目录；
- 不进入应用普通日志、Git、测试报告、长期质量报告或 settlement；
- repo 外路径、OS 访问限制、TTL 24 小时、无普通日志、无生产入口是硬门；磁盘加密在开发机能力允许时 best-effort 启用并报告，不作为无法证明的 READY 硬门；
- TTL 固定为 24 小时，到期自动删除；
- 每次启用具有显式标记和操作者审计元数据；
- 默认关闭；
- 真实外部 Provider 调用仍是独立、逐次明确授权的动作，不能由普通单测暗中触发。

### 13.3 可永久保留的数据

允许永久保留：

- corpus case ID；
- provider/model/selection/contract/compiler 版本；
- attempt count；
- HTTP 分类、latency、token/cost 元数据；
- EXACT/NORMALIZED/DEGRADED/INCOMPLETE/UNSAFE；
- normalization Rule ID 计数；
- closed layer/reason；
- 人工盲审的二元标签和裁决结果；
- 经最小化、确认不含敏感信息的合成回归 fixture。

不得永久保留：原始 Prompt、完整请求、原始响应、任意真实用户文本、Provider endpoint query、Authorization header 或 secret。

### 13.4 v3/v4 双回放

对同一份实验室 raw response：

```text
raw response
  ├─ v3 strict chain -> outcome A
  └─ v4 lenient chain -> outcome B
```

比较至少输出：

- v3 拒绝、v4 EXACT；
- v3 拒绝、v4 NORMALIZED；
- v3 拒绝、v4 DEGRADED；
- v3/v4 都 INCOMPLETE；
- v4 新增 false acceptance；
- 每个 Rule ID 的发生率；
- 结构恢复后的人审 L3 结果。

离线 dual replay 是实验工具，不进入生产 Turn。

## 14. 评测集与 READY 认证

### 14.1 评测集分层

使用两个互不替代的数据集：

1. **开发诊断集**：30..50 个固定合成主题，每个主题带三档问法，用于 Prompt、temperature、v3/v4 dual replay 和规则调试；允许在开发周期内迭代，但必须版本化并保留失败样本。
2. **封存认证集**：100 个固定合成主题，每个主题冻结 `CONCISE / STANDARD / DETAILED` 三种问法，共 300 条样本；设计冻结后才能运行，不得因失败删除或替换样本。若 corpus 或任一问法必须修订，整体升版并重新跑完整 300 条门。

100 个认证主题平均覆盖 10 类普通技术知识：

1. Java / Spring；
2. 数据库 / 事务；
3. Redis / 缓存；
4. 网络；
5. 分布式系统；
6. 前端；
7. DevOps / 容器；
8. AI / LLM / Agent；
9. 架构 / 设计模式；
10. 安全 / 性能。

排除：真实用户数据、私有项目数据、依赖当前互联网事实的问题、高风险医疗/法律/投资建议、Comparison、Portfolio 专属问题。

### 14.2 temperature 实验

在同一开发诊断集上对 `temperature=0.0` 与当前 `0.2` 配对测试：

- 相同 corpus；
- 相同 modelRef、Prompt 语义、max tokens、deadline 与合同；
- 记录结构成功、normalization、L3、人审 false acceptance、延迟和成本；
- 最终按 L3 与安全 false acceptance 选择，不以“JSON 看起来更整齐”单独决定；
- 选择后冻结到 production profile，认证集只运行冻结值。

### 14.3 人工盲审

每条结构成功样本先按五个基础二元标准评审：

1. 是否回答了原问题；
2. definition 是否准确且有信息量；
3. mechanism 是否准确说明主要机制；
4. 是否不存在明显事实错误或内部矛盾；
5. 是否清晰可读。

还必须按 depth 增加一个二元标准：

- `CONCISE`：是否直接、紧凑且无明显重复；
- `STANDARD`：是否在定义与机制之间均衡，并具有足够信息量；
- `DETAILED`：是否明显深于 Standard，并自然覆盖至少一类进阶内容，例如场景、边界、取舍或常见误区；不要求固定标签或固定关键词。

五项基础标准与对应 depth 标准全部为 true 才计 L3 成功。推荐两名独立评审者，隐藏 v3/v4、temperature、归一化等级和同主题其他 depth 输出；分歧由第三次裁决解决。若资源只允许一名评审者，至少进行打乱顺序、隐藏来源的第二遍复审，并单独报告这一限制。

不使用第二个模型充当最终语义裁判。模型辅助可以用于整理样本，但不能替代人类 READY 签字。

### 14.4 分母必须分离

报告至少分开计算：

- transport denominator：是否最终取得 Provider 响应；
- shape denominator：取得响应后是否能 parse + compile；
- semantic denominator：canonical 后是否通过 runtime semantic admission；
- L3 denominator：按 depth 分开的各 100 条，以及完整 300 条是否通过盲审任务完成。

不得用“只统计成功返回的样本”掩盖 Provider 不可用，也不得用 transport failure 拉低后声称 schema 失败。

### 14.5 Qwen Explanation READY 门

三个 depth 分别以自己的 100 条样本计算下表；每一档都必须同时满足，且 300 条总体安全 false acceptance 为 0。任一 depth 不达标，整个 Qwen General Explanation 保持非 READY，不能只发布通过的一档：

| 门 | 阈值 |
|---|---:|
| 安全/身份/权限 false acceptance | 0 |
| 缺失 definition 或 mechanism 却被接受 | 0 |
| parse + compile（EXACT + NORMALIZED + caveats-only DEGRADED） | >= 98% |
| 人工 L3 成功 | >= 95% |
| 最终 Provider 可用性（含最多一次 retry） | >= 95% |
| P95 端到端耗时 | 不超过 Turn deadline |
| canonical Schema / Codec false acceptance | 0 |

normalization 不等于失败，但每条 Rule ID 必须报告发生率。任一单条规则发生率 >20% 时触发 contract drift review：能力可以在其他门全部通过时保持候选，但在 review 结论前不得无说明地宣称稳定；单个用户 Turn 不因此失败。

`DROPPED_INVALID_OPTIONAL_CAVEATS` 也按规则发生率报告。它不影响单 Turn COMPLETE，但高频意味着 Prompt 或 Provider 合同正在漂移。

## 15. 能力认证、平台发布与项目完成

三个状态必须分开：

```text
PLATFORM_RELEASABLE
  != CAPABILITY_READY
  != PROJECT_COMPLETE
```

### 15.1 CAPABILITY_READY

认证键为 `Provider × Operation × Goal kind`；depth 是这个能力内部不可拆分的证据维度，不是独立发布开关。只有 `CONCISE / STANDARD / DETAILED` 各自通过 §14 全部门后，Qwen General Explanation 才可整体标为 READY。

### 15.2 PLATFORM_RELEASABLE

首版平台可发布的最低组合：

- Qwen Goal 路径满足既有门；
- Qwen General Explanation 三档整体为 READY；
- 模型目录只向用户暴露已满足当前请求能力的可选项；
- GLM 不可选择；
- Comparison 在 Provider 前固定返回用户可见 `BOUNDARY`，不冒充 Explanation，也不进入 Provider；
- 失败恢复、stale selection 和 public contract 门通过。

因此，GLM 被限流、Comparison 尚未认证、GATE-19 未完成，不再阻塞这个单模型首版；任一 Explanation depth 未通过仍会阻塞 Qwen Explanation 首版。

### 15.3 PROJECT_COMPLETE

项目总体状态继续 `IN_PROGRESS`。GATE-19 只阻塞“双模型切换已完成”的声明，不阻塞 Qwen 单模型首版；GLM、Comparison 和其他开放项必须继续在架构账本中如实保留，不能因为平台可发布而关闭。

## 16. 不可协商的 fail-closed 边界

以下任一 false acceptance 都阻塞 READY，没有 normalization/degradation 预算：

- provider/modelRef/selectionVersion/binding/protocol profile 不一致；
- secret、API key、Cookie、数据库密码或内部 token 进入 Provider 请求或诊断 artifact；
- 模型 tool arguments 被当作执行授权；
- 未授权外部副作用；
- silent cross-model fallback；
- duplicate key、trailing token、multi-root 或递归 JSON 提取；
- request/response size、JSON depth、array length、concurrency 或 deadline 超限；
- Goal/operation/depth/scope 被 Compiler 扩大或猜测；
- canonical v3 Schema、Codec 或领域不变量失败后仍返回答案；
- Comparison pair 身份被猜测、裁剪或按位置偷偷修复；
- 真实用户或私有项目数据进入诊断实验室；
- raw Provider artifact 进入仓库、日志、长期报告或超过 TTL。

## 17. 观测与诊断合同

生产只记录非敏感、低基数、闭集元数据：

- provider、modelRef、selectionVersion；
- operation、goal kind、depth；
- providerContractRef、applicationContractRef、compiler profile fingerprint；
- attempt count、transport class、latency bucket、token/cost；
- `EXACT | NORMALIZED | DEGRADED | INCOMPLETE | UNSAFE`；
- normalization Rule ID count；
- closed validation layer/reason；
- public settlement code；
- capability certification version。

不得记录：

- 原始用户问题；
- ConversationWindow；
- Prompt；
- Provider 原始响应；
- definition、mechanism 或 caveat 正文；
- 私有 Evidence、来源地址、header、endpoint query 或 credentials。

unknown field 只记录计数，不记录字段名和值，避免模型生成内容进入低基数日志。

## 18. 原子迁移与回滚

### 18.1 实施顺序

1. 建立隔离诊断实验室和开发诊断集；
2. 固定当前 v3 baseline 指标；
3. 经单独授权采集固定合成 Qwen 响应；
4. 离线实现/验证 v4 admission、Compiler 与 canonical v3；
5. 对同一 raw response 执行 v3/v4 dual replay；
6. 完成 temperature 配对实验与人工盲审；
7. 冻结 v4/v3/Compiler/Prompt/profile 版本；
8. 构建包含 `qwen-3-7-flash-v7` 的候选 JAR；候选只在隔离、不可选择的认证环境运行，不能先部署到生产目录；
9. 对候选 JAR 运行封存 100 主题 × 3 depth = 300 条认证集及全部安全、Comparison 零调用和回归门；
10. 任一门失败则丢弃候选，production binding、selectionVersion 与 selectable catalog 不变；
11. 全部门通过后，才在一个 Replacement Slice 中原子部署 v7，并同步更新 selectable catalog 与证据账本。

### 18.2 原子提升

候选认证通过后的生产提升必须同一 Replacement Slice 覆盖：

- `general.provider-draft.v4` resource 与 registry；
- `general.draft.v3` resource 与 registry；
- Compiler profile 与 fingerprint；
- Qwen General Operation binding；
- Qwen selectionVersion/catalogVersion；
- Prompt 与 temperature；
- Adapter/Gateway/Codec/Validator 测试；
- public failure/重试 UI 回归；
- 架构状态与文档证据。

禁止出现“新 Prompt + 旧 Schema”“新 Provider Draft + 旧 Compiler”“新 canonical + 旧 selectionVersion”等半升级组合。禁止“先部署、再认证”：F4 发生在非 selectable 候选上，F5 才改变生产目录。旧 selection 一律 stale，不做静默迁移。

### 18.3 回滚

回滚单位是已验证的 Git/JAR 版本及其完整 contract bundle：

- 不增加运行时 `parser.version` 开关；
- 不在同一 selectionVersion 下切回 v3；
- 回滚后目录版本与 selectionVersion 必须与旧 binding 一致；
- 已由新 selection 产生的请求不迁移到旧 binding；
- 诊断与认证报告保留版本关联，不能混合计算。

## 19. Replacement Manifest（设计级）

下列是对应实施计划必须精确化的责任面；本批准设计不直接修改这些生产文件。

### Slice A：合同与编译层

新增：

- `backend/src/main/resources/model-contracts/general.provider-draft.v4.schema.json`；
- `backend/src/main/resources/model-contracts/general.draft.v3.schema.json`；
- v4 Compiler/Normalizer 所需的内聚实现与单测；
- v4/v3 schema golden fixtures。

修改：

- `StructuredOutputContractRegistry`：注册 v4/v3；
- `OperationBinding` / approved Qwen profile：原子绑定 provider v4、application v3、compiler v4；
- `GeneralProviderDraftCompiler` 家族：实现闭集归一化、optional caveat 隔离和可信字段派生；
- `GeneralDraftCodec` / `GeneralDraftValidator`：识别 canonical v3 的三档合法集合和统一 role aspects；
- Prompt：按 trusted depth 请求 §8.3 的 definition、mechanism 和可选 caveats，不要求 topic/kind/depth/role/aspects 回显；
- Qwen selectionVersion 与 catalog fingerprint 期望；
- 相邻 Gateway/Adapter/contract tests。

删除：

- Qwen 生产 binding 对 `general.provider-draft.v3` 和 `general.draft.v2` 的引用；
- 三档 Explanation 对固定数组槽位、禁分号和服务端伪造细粒度 aspects 的生产依赖。

v3/v2 资源可暂留用于离线历史回放，但不得被新 Qwen production binding 引用。是否物理删除由实施计划按仓库历史 fixture 需求决定，不能形成运行时 fallback。

### Slice B：transport retry 与失败投影

修改：

- Qwen transport/Gateway 的 attempt orchestration；
- exception classification：只允许 §11 闭集重试；
- deadline 与 providerAttemptId 计量；
- 复用 `SELECTED_MODEL_TEMPORARILY_UNAVAILABLE`；
- lifecycle idempotency 与前端“重试 Qwen”回归测试；
- Comparison 的 Provider 前 `BOUNDARY` 裁决与 General Provider 零调用断言。

不得修改：

- 单 Turn 单模型身份；
- settled requestId 不重新执行；
- 公开 `PublicAgentTurn` variant 集；
- Provider 正文不持久化规则。

### Slice C：诊断实验室与评测

新增：

- repo 内的固定合成 corpus、manifest 和 aggregate report schema；
- repo 外 raw artifact path 的显式配置与 24h 清理器；
- v3/v4 dual replay CLI/test runner；
- temperature 配对 runner；
- 人审盲化导出与裁决结果格式；
- 100 主题 × 3 depth 的 300 条认证门计算器。

不得新增：

- 生产 HTTP 诊断 endpoint；
- 生产 Bean 自动采集；
- 任意文本输入参数；
- raw body 的日志 sink；
- 自动外部调用的普通单元测试。

### Slice D：能力目录与治理

修改：

- capability/readiness 证据，使 Qwen General Explanation 只在三档全部通过后整体 READY；
- selectable catalog 过滤 GLM 和不满足当前 operation 的能力；
- release gate：平台首版与双模型 GATE-19 解耦；
- `docs/agent-architecture-status.json` 只在证据实际通过后更新，整体仍 `IN_PROGRESS`；
- 对应质量报告与 exit gate 引用。
- `docs/15-Agent 2.0真实交互问题清单与修复边界.md`：A2-85 改写为“内容/合同错误仍单次失败，只有批准的 transport 闭集可重试”；A2-117 改写为“General v4/v3/compiler v4 允许本设计 transport retry，Goal v2 仍单次调用”；A2-80 保持不变；GATE-19 继续 OPEN，overall 保持 `IN_PROGRESS`。

上述账本修改与生产 Replacement Slice 同批，不预先把设计目标写成已实现事实。

## 20. 测试矩阵

### 20.1 Provider Draft v4 Schema

正例：

- definition/mechanism 均为 string；
- 一个 string、一个 string[]；
- 按三档最小/最大边界构造的 string[]；
- caveats missing/null/[]/合法数组；
- unknown root fields；
- 内部分号、冒号、括号、引号、英文缩写与技术符号；

负例：

- root array/string/null；
- core missing；
- core 为 number/bool/object；
- core array 含非 string；
- core 归一化后为空；
- Explanation core 缺失或出现错误类型；
- duplicate key、trailing token、multi-root；
- body/depth/array/item length 超限。

### 20.2 Normalizer/Compiler

必须覆盖：

- 每条 Rule ID 单独正例；
- 多条规则组合的固定顺序；
- 幂等 property test；
- string 与 string[] 在同 role 内确定性连接，均得到精确两条 canonical statement；
- unknown field count，不泄露 name/value；
- invalid caveats 任一损坏导致整组丢弃；
- invalid core 不能被 caveat 策略掩盖；
- 不裁剪、不拼接、不补写、不猜 branch；
- trusted topic/kind/depth 覆盖模型冗余回显；
- 三档 role/aspect 均精确派生为 DEFINITION/MECHANISM；
- Comparison Goal 在 Compiler 以前不可达，Compiler 不接收 Comparison raw tree。

### 20.3 Canonical v3 / Codec / Validator

必须覆盖：

- canonical 始终精确两条 statement；其文本自然句数为 CONCISE 1+1/总 2，STANDARD 各 1..3/总 2..6，DETAILED 各 4..6/总 8..12；
- 缺任一角色失败；
- 每档下限/上限的邻接越界失败；
- aspects 与 role 不一致失败；
- caveats `[]` 合法且 COMPLETE；
- unknown canonical field 失败；
- 任一 depth 出现 COMPARISON role 失败；
- 明显跑题、非中文主要表达、空正文失败；
- Provider Draft 不能绕过 canonical Schema。

### 20.4 required-tool 与安全

必须覆盖：

- missing/unknown/multiple tool calls；
- multiple choices；
- content + tool 混合；
- refusal + tool 混合；
- bad finish reason；
- wrong model/binding/profile；
- tool arguments 包含未授权操作但不执行；
- secret-like test fixture 在出站前被拒绝；
- duplicate/trailing/multi-root；
- resource limit 与 deadline。

### 20.5 Retry

必须覆盖：

- connect/reset、429、502/503/504 的一次成功 retry；
- 无 `Retry-After` 的抖动严格落在 100..250ms；
- `Retry-After <=1s` 等待后可重试，`Retry-After >1s` 不重试并投影 RATE_LIMITED；
- retry 再失败后 settled temporarily unavailable；
- 2xx invalid response 不 retry；
- 400/401/403 不 retry；
- deadline 不足不 retry；
- 等待后剩余 General deadline <3s 不 retry；
- attempt 最大值严格为 2；
- 同 requestId、不同 providerAttemptId；
- 两次 frozen identity/Prompt 语义一致；
- 无 GLM 调用；
- settled same-request replay 不再次调用；
- 用户显式 retry 使用新 requestId。

### 20.6 诊断实验室隐私

必须覆盖：

- 任意自由文本输入被拒绝；
- corpus 外 case ID 被拒绝；
- raw path 位于 repo 时启动失败；
- TTL 超 24h 配置失败；
- raw 内容不进入 stdout/log/report；
- aggregate report 不含正文和 unknown field name/value；
- 外部调用缺显式授权时不执行；
- 到期 artifact 删除验证；
- 生产 profile 中实验室 Bean/endpoint 为零。

### 20.7 架构回归

必须覆盖：

- Qwen 新 selection 只绑定 v4/v3/compiler v4；
- 旧 selection stale；
- Goal v2 binding 不变；
- GLM binding 不被偷改；
- Comparison Goal 结算固定不可重试 `BOUNDARY`，General Provider 调用为 0；
- public contract fixtures 不因内部等级扩充；
- `StructuredOutputGateway` 仍为唯一 Provider structured-output 入口；
- 无 `parser.version` runtime switch；
- 无 Spring AI、repair、cross-model fallback 新路径；
- Goal unknown key 继续封闭拒绝；General unknown root 只计数且不记录 name/value，两者策略不得串线。

## 21. Exit Gates

### Gate F0：设计批准

- 本文件已由用户明确批准；
- DOCUMENT_STATUS、权威索引和 checker allowlist 在本批准变更中同步；
- 所有替代条款和非目标无歧义；
- Level 3 生产替换授权已记录；精确执行文件和验证命令由对应实施计划冻结。

### Gate F1：实验室隐私边界

- 固定合成 corpus、repo 外路径、24h TTL、无生产 Bean/HTTP、无 raw log 全部由自动化测试证明；
- 外部 Provider 调用仍需明确授权；
- 任何真实/私有数据进入路径都 fail-closed。

### Gate F2：离线合同证据

- v3 baseline 与 v4 dual replay 在同一 raw corpus 上完成；
- 每条 normalization rule 可归因；
- v4 无安全/identity false acceptance；
- caveat degradation 不掩盖 core failure；
- temperature 选择有配对证据。

### Gate F3：生产替换完整性

- 候选 JAR 内 v4/v3/compiler/profile/Prompt/selection 一致，但候选在 F4 前不可选择、不可部署为生产权威；
- Goal v2、GLM、Comparison 安全规则和 public contract 回归通过；
- v3 不再是 Qwen production fallback；
- retry 只有批准闭集且 attempt<=2；
- Comparison 固定 `BOUNDARY` 且 General Provider calls=0。

### Gate F4：候选 JAR 封存 300 条认证

- 100 个主题的三档问法共 300 条全部运行，三个 depth 分别满足 §14.5；
- 两次独立人审或受限情况下的盲化复审完成；
- 分母分离，失败样本未删除；
- 报告不包含 raw Provider 内容；
- 任一门失败时丢弃候选，生产目录和 selectable catalog 未发生变化。

### Gate F5：原子生产提升与能力暴露

- 只有三个 depth 全部通过后，Qwen General Explanation 整体标为 READY；
- GLM 不可选择；
- Comparison 保持 Provider 前 `BOUNDARY`，不冒充 READY；
- v7 JAR、Operation binding、selectionVersion、catalogVersion 与 selectable catalog 同批原子部署；
- UI 临时不可用与“重试 Qwen”行为通过；
- `PLATFORM_RELEASABLE` 不关闭 GATE-19 或项目整体开放项。

## 22. 风险与控制

| 风险 | 控制 |
|---|---|
| 松 Schema 吞掉真正错误 | core 类型/存在性、trusted branch、canonical 和 safety 继续严格；只允许闭集归一化 |
| unknown fields 掩盖 contract drift | 只计数不使用；单规则/unknown 高频触发 >20% drift review |
| caveats 损坏长期被忽略 | 单 Turn 隔离，认证报告持续统计，超过预算审核 Prompt/contract |
| retry 导致重复计费 | 最多一次、共享 deadline、attempt 独立计量、报告潜在重复计费 |
| retry 变成隐藏重新生成 | 只允许 transport class；2xx 后任何内容失败不 retry |
| 诊断实验室演变成生产日志 | 无 Bean/HTTP、固定 corpus、repo 外、TTL 24h、默认关闭、测试与审计 |
| 人工盲审主观 | 五项二元 rubric、双评审/裁决、隐藏版本与策略 |
| 单能力发布掩盖项目未完成 | 三层状态分离，Explanation 三档不可拆分，overall 保持 IN_PROGRESS，GATE-19 继续开放 |
| v3/v4 形成双权威 | 双跑仅离线；生产原子提升；版本级回滚 |
| 服务端 aspects 伪造语义完整性 | 三档都只派生 DEFINITION/MECHANISM，深度质量交给分 depth 盲审 |

## 23. 已批准裁决

以下决策已成为后续实施权威：

1. Qwen General Explanation 是一个不可拆分能力，统一覆盖 `CONCISE / STANDARD / DETAILED`；不等待 GLM、Comparison 或 GATE-19，但任一 depth 未通过都会阻塞该能力 READY；
2. 三档 canonical 都精确包含两条 statement，并只保留可信 `[DEFINITION] / [MECHANISM]` role aspects；深度质量由 statement text 内的自然句数、Prompt 和分 depth 盲审证明；
3. 句数冻结为 CONCISE 1+1、STANDARD 每角色 1..3 且 Prompt 目标 2+2、DETAILED 每角色 4..6；不裁剪、不补写；
4. `general.provider-draft.v4` 对 core 严格，对 caveats/unknown root 宽 admission；malformed caveats 整组丢弃仍可 `ANSWER / COMPLETE`；
5. Goal unknown key 继续严格拒绝，General unknown root 只计数且不记录 name/value；该不对称不得泛化；
6. Comparison 在独立认证前由 Goal 识别，但在 General Provider 前固定结算不可重试 `BOUNDARY`，Provider calls=0；
7. 同 Qwen transport 最多一次 retry，共享 absolute deadline；无 Retry-After 抖动 100..250ms，Retry-After 上限 1s，等待后剩余时间至少 3s；
8. 固定合成数据、repo 外、TTL 24h、无日志/生产入口是 raw response 诊断例外的硬边界；非合成数据必须另行设计和批准；
9. READY 使用 100 个固定主题 × 3 depth = 300 条样本；每档分别要求 L3>=95%、parse+compile>=98%、availability>=95%、安全 false acceptance=0；
10. 运行时 validator 只做可确定判断，准确性、信息量、可读性和深度质量由离线盲审负责；
11. 先构建不可选择的 v7 候选 JAR 并完成 300 条认证；失败丢弃候选，成功后才原子部署和更新 catalog；
12. 生产保持单权威，v3/v4 dual replay 仅离线，回滚以 Git/JAR 完整版本为单位；
13. Goal v2、required-tool envelope、模型身份、secret、外部副作用、资源边界和 canonical fail-closed 全部不放宽；
14. `docs/15` 必须同步 A2-85/A2-117 的 retry 例外，A2-80 不变，GATE-19 与 overall `IN_PROGRESS` 不关闭。

## 24. 最终工程原则

本设计把工程原则冻结为：

> 对模型表达宽容，对业务语义审慎，对外部副作用严格；功能实验阶段只以固定合成数据和短期隔离诊断换取可观察性，任何非合成数据进入诊断实验室前必须另行取得明确、可验证的数据边界与批准。

对应到实现：

```text
Provider Transport
  -> Strict Envelope / Strict JSON / Resource Guards
  -> Lenient Provider Draft Admission
  -> Deterministic Normalizer + Compiler
  -> Strict Canonical Contract
  -> Bounded Runtime Semantic Admission
  -> Offline L3 Evaluation
```

这条链路允许分号、字符串包装和缺失 caveats 被合理恢复，但不会允许关键解释缺失、模型身份漂移、secret 泄露、未授权工具执行、silent fallback 或 canonical 损坏被包装成成功。
