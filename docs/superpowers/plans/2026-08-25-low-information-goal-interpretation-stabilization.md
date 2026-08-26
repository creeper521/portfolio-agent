# 低信息输入与 Goal Interpretation 稳定化实施计划

<!-- DOCUMENT_STATUS: ACTIVE -->

> 日期：2026-08-25
> 状态：ACTIVE；按已批准 Level 2 设计实施
> 对应设计：`docs/superpowers/specs/2026-08-25-low-information-goal-interpretation-stabilization-design.md`
> 对应缺陷：A2-116
> 实施边界：不新增模型 root kind，不放宽 Codec，不增加 repair/retry/fallback，不启动 v6
> 外部调用：真实 Provider gate 为 REQUIRED，但必须另获外部调用授权

## 0. 当前事实

- 设计已经两轮评审并由用户批准；
- A2-116 已进入动态缺陷账本；
- Qwen 已接通；历史样本中直接推荐曾成功，裸 `1` 曾真实触发 `blockedGoal=null` 的 SCHEMA 拒绝；当前修复后的新鲜真实样本见 0.1，历史成功不得覆盖当前失败；
- DRAFT/APPROVED 文档定位和过期 Provider 描述已经同步；
- 工作区存在与本批次无关的用户在途修改，本计划只修改下列责任区，不重置、覆盖、暂存或提交其他文件。

### 0.1 2026-08-25 实施进度

- Track A/B/C 已按责任区并行完成并通过目标测试；
- Backend 全量、clean package、code-quality、architecture、privacy、documentation 与 checker tests 已通过；
- 模型关闭 packaged/browser L1 已通过，覆盖桌面和移动合同；
- 本批没有修改 Frontend 源码或公开 Turn 合同；为 packaged-JAR 验收仅执行当前工作树的 Frontend type-check/build，均通过；
- 真实 Provider gate 已获用户明确授权并执行：Qwen 低信息首轮稳定返回 server-fixed `CONVERSATIONAL`，公开 `modelExecution.participation=NONE`，证明 Provider 调用为 0；随后明确推荐请求以及独立直接推荐对照均结算为 `SELECTED_MODEL_INVALID_RESPONSE`，安全诊断定位为 Goal Interpretation `SCHEMA/UNSUPPORTED_ROOT_KIND`，不是 recentReference 或 blockedGoal；该闭集 reason 已固化到生产诊断与回归测试，不记录 Provider 原始输出；
- Qwen 直接对照与两次额外两轮样本复现相同 root-kind 合同拒绝，当前证据不再支持“历史污染是唯一或主要剩余原因”；
- 在不改 Codec/v5 语义的前提下，曾以目标测试约束一次“顶层 kind 只能为既有两种 root variant”的 Prompt 消歧；相关测试通过后真实 Qwen 仍产生同一失败，实验已撤销，禁止继续用无数据的 Prompt 叠加冒充修复；
- GLM 同构首轮同样不调用 Provider，第二轮结算为 `SELECTED_MODEL_RATE_LIMITED`，属于既有独立 Provider 限流，未进入 schema 校验；
- 原始输出、Prompt、输入、凭据和会话 token 均未记录；真实门状态为 `BLOCKED`，A2-116 不关闭，完成态 docs/11 不写入。

## 1. 并行责任区

### Track A：低信息确定性策略

责任文件：

- 新增 `backend/src/main/java/com/portfolio/agent/turn/planning/UnresolvedIntentPolicy.java`
- `backend/src/main/java/com/portfolio/agent/turn/planning/GoalResolver.java`
- `backend/src/main/java/com/portfolio/agent/turn/infrastructure/AgentCapabilityConfiguration.java`
- 对应 planning/configuration tests

禁止修改 Track B/C 文件。

### Track B：Goal schema、prompt 与 closed diagnostics

责任文件：

- `backend/src/main/java/com/portfolio/agent/turn/planning/GoalProposalCodec.java`
- 必要的 planning closed reason / typed decode exception
- `backend/src/main/java/com/portfolio/agent/turn/infrastructure/model/GoalInterpretationAdapter.java`
- `backend/src/main/resources/prompts/goal-interpretation-system.txt`
- 对应 codec/adapter tests

禁止修改 Track A/C 文件。

### Track C：日志键值可观测性

责任文件：

- `backend/src/main/resources/logback-spring.xml`
- 对应日志配置、捕获和隐私负向测试

禁止修改 Track A/B 文件。

### Root 集成责任

- 权威文档和实施计划；
- 审查三个 Track 的实际 diff；
- 解决构造器/Bean wiring 等跨 Track 集成问题；
- 全量门、原始路径、最终文档和完成状态；
- 不替并行 Track 越权修改其责任文件，除非集成失败且已先说明。

## 2. Task A：低信息策略（RED → GREEN）

### A1. RED：策略闭集测试

- [x] `1`、`？`、`...` 命中；
- [x] `1?`、`12。。`、`1 ...` 按 code point 并集命中；
- [x] `~`、货币符号、emoji 不命中；
- [x] `嗯`、`继续`、字母和汉字不命中；
- [x] `recentSemanticState != null + 1` 不命中；
- [x] `routeCandidates + 1` 不命中；
- [x] DISCUSSION typed action、clarification resolve 不命中；
- [x] `defaultSubject + 1` 在其他门均为空时命中；
- [x] 问候由 `SafeConversationalFastPath` 优先处理，新策略即使单独收到汉字也不命中。

### A2. GREEN：实现策略

- [x] 使用 Java code point 遍历；
- [x] 闭集仅为 `Character.isWhitespace`、`Character.isDigit` 和七种 punctuation type；
- [x] 不使用词表、正则 NLU 或短文本长度猜测；
- [x] 返回 `ResolvedGoalSet.conversational(...)`；
- [x] Provider attempt 保持 false，不标记 adopted；
- [x] 策略无状态、不可变，不持有 Provider 或 State 依赖。

### A3. GREEN：接入现有顺序

- [x] 保持 `SafeConversationalFastPath` 第一顺位；
- [x] 在 `GoalInterpretationInputFactory.create(...)` 之后调用新策略；
- [x] 未命中才调用 `interpretTyped(...)`；
- [x] 更新唯一生产 Bean wiring；
- [x] 构造器测试和生产配置测试同步。

## 3. Task B：prompt、schema 标识与拒绝原因

### B1. RED：当前真实拒绝 fixture

- [x] 增加 `NEEDS_CLARIFICATION + blockedGoal=null` fixture；
- [x] 断言继续 fail-closed；
- [x] 断言 layer 为 SCHEMA；
- [x] 断言 closed reason 为 `CLARIFICATION_BLOCKED_GOAL_REQUIRED`；
- [x] 断言 Provider body、用户 sentinel 和 prompt 不进入 diagnostics。

### B2. GREEN：最小 typed rejection

- [x] 只类型化当前已取证的 blockedGoal required 失败；
- [x] 不批量重写整个 Codec 异常体系；
- [x] 其他 `IllegalArgumentException` 继续按既有 SCHEMA 拒绝；
- [x] 不解析异常 message 推导 reason；
- [x] reason 为闭集 enum/typed value，不携带原始字段值。

### B3. Schema 标识单一来源

- [x] 删除运行期 `semantic-route-proposal-v1` 硬编码；
- [x] 删除相邻 Javadoc 的字面版本；
- [x] projection 使用 `GoalProposalCodec.SCHEMA_VERSION`；
- [x] 测试断言 projection schema 为 `goal.proposal.v5`；
- [x] 不修改 v5 字段或语义。

### B4. Prompt 零目标出口

- [x] 无可持久化目标时允许现有 `CONVERSATIONAL`；
- [x] 标准 `NEEDS_CLARIFICATION` 只在可生成完整 blockedGoal 时使用；
- [x] routeCandidates 与 DISCUSSION 例外保持；
- [x] 明确独立当前目标优先于 recentConversation；
- [x] recentReference 只在显式历史依赖且 typed state 命中时生成；
- [x] 删除“无 recent state 时无条件澄清”的绝对规则；
- [x] 不新增 root kind 或关键词闸门。

## 4. Task C：local/prod 日志可观测性

### C1. RED：日志输出测试

- [x] local `BACKEND_INFO` 能输出 structured key-value；
- [x] local `BACKEND_ERROR` 能输出 structured key-value；
- [x] local/non-prod Console 能输出 structured key-value；
- [x] prod structured Console 保持 JSON/structured 行为，不重复拼接 `%kvp`；
- [x] 日志不包含用户文本、prompt、Provider 原始输出、Authorization 或密钥 sentinel。

### C2. GREEN：配置修改

- [x] 两个文件 appender pattern 增加 `%kvp`；
- [x] 覆盖安全的 `CONSOLE_LOG_PATTERN` 或定义等价 Console appender；
- [x] prod structured appender 不追加文本 `%kvp`；
- [x] 保持现有过滤器、滚动策略、级别和 charset。

## 5. 集成回归

### 5.1 Goal 路径

- [x] 裸 `1` 返回 server-fixed `CONVERSATIONAL`；
- [x] 第一轮 Provider 调用严格为 0；
- [x] 确定性 fixture 中 `1 → 给我推荐两个项目` 第二轮形成 recommendation size 2；
- [x] 确定性 fixture 中第二轮使用独立 standard goal，不生成 recentReference；
- [x] 直接推荐路径不回退；
- [x] modelExecution 不把固定引导归因给所选模型。

### 5.2 既有澄清和引用

- [x] 合法完整 blockedGoal；
- [x] routeCandidates 的 bounded challenge；
- [x] DISCUSSION facet clarification；
- [x] 无 typed state recentReference 继续拒绝；
- [x] 有 typed state recentReference 继续工作；
- [x] 有 recent state 的裸 `1` 不被确定性策略劫持。

### 5.3 无回退边界

- [x] Provider schema/semantic 失败仍单次调用；
- [x] 不自动 repair；
- [x] 不自动 retry；
- [x] 不跨 Provider fallback；
- [x] 换模型仍创建新 requestId。

## 6. 确定性验证门

- [x] Track A/B/C 目标测试分别通过；
- [x] `mvn.cmd -f backend/pom.xml test` 全量通过；
- [x] `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path backend/src/main` 通过；
- [x] `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/documentation-check.ps1` 通过；
- [x] 受影响 packaged/browser 原路径在不调用真实 Provider 的 fixture lane 通过；
- [x] 本批未修改 Frontend 源码或公开合同；Frontend 单元测试为 `NOT_APPLICABLE`，为 packaged 验收执行的 type-check/build 与 Browser L1 均通过。

## 7. 真实 Provider 门

分类：`REQUIRED`。

执行前置：用户另行明确授权外部调用，且使用仓库外 secret file。

- [ ] Qwen：同一会话 `1 → 给我推荐两个项目`；
- [x] 第一轮 Provider 调用 0；
- [ ] 第二轮为 Qwen 可采用 recommendation size 2；
- [ ] 无 recentReference 误判；
- [x] 若共享 prompt/codec 影响 GLM 且 GLM 凭据可用，执行同构 lane；
- [x] 只记录 closed diagnostics、公开终局和耗时桶，不记录输入、回答或原始模型内容。

2026-08-25 新鲜结果：`BLOCKED`。Qwen 第二轮及直接推荐对照均在
Goal Interpretation 以 `UNSUPPORTED_ROOT_KIND` 被 SCHEMA fail-closed；GLM 第二轮被
Provider 限流。一次不改变 v5 合同的 root-kind Prompt 消歧复跑仍失败并已撤销。
该结果不满足 recommendation size 2 与可采用终局要求，也不能据失败前的历史
成功样本关闭本门。

未获授权、凭据不可用、限流、timeout 或矩阵不足时只能标记 `BLOCKED`/`IN_PROGRESS`，不得关闭 A2-116。

## 8. 完成文档

- [x] `docs/08-当前实现状态.md` 记录实际完成行为和仍开放的 Provider 门；
- [ ] 完成后更新 `docs/11-项目演进日志.md`，不写测试数量、hash 或提交元数据；
- [ ] A2-116 只有在原始路径和全部专属门通过后才从 docs/15 删除；
- [x] A2-80/81/87/88 只记录新增证据，不因本批越权关闭；
- [x] 计划状态只在所有授权范围内任务和门完成后变更；外部门未完成时保持 ACTIVE/IN_PROGRESS。

## 9. 停线条件

出现任一情况立即停止当前 Level 2 实施并回到设计审批：

- 需要新增公开 Turn variant 或模型 root kind；
- 需要允许 `blockedGoal=null`；
- 需要改变 v5 字段语义或启动 v6；
- 需要关键词 ReferenceIntentGate；
- 需要模型 repair/retry 或跨 Provider fallback；
- 需要修改持久化状态语义、公开 API 或前端共享合同；
- 与用户在途修改发生不可安全绕开的文件冲突。
