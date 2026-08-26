# Agent 2.0 稳定化与仓库治理设计
<!-- DOCUMENT_STATUS: APPROVED -->

> **日期：** 2026-08-19
> **状态：** 已由用户批准，作为当前稳定化与仓库治理实施的设计权威；提交与发布仍需单独授权
> **评审修订：** 2026-08-19 吸收第一轮事实核验意见，补齐 429 双通道、工程规则原子同步、前端 25 秒迁移、input anchor 隐私优先级、脱困入口 E2E、预算余量观测与目标状态标注
> **目标路径：** `docs/superpowers/specs/2026-08-19-agent-stabilization-and-repository-governance-design.md`
> **适用仓库：** `D:\code\agent`
> **范围：** Agent 2.0 真实交互收口、生产保护、本地 PostgreSQL、短期状态、工程规则、文档与注释治理、`turn` 模块最终收敛

## 1. 文档目的

Agent 2.0 已经完成 Command、Goal、Plan、Execution、Projection、State、无版本 API 与前端 closed contract 的首次整体替换，但当前代码、运行配置、发布门和文档仍存在四类未闭合问题：

1. 真实交互暴露澄清目标丢失、跨会话状态串扰、deadline 与结果回收失配；
2. 新 `/api/agent/turns` 入口尚未完整接回来源限流、系统并发和全链 absolute deadline；
3. 本地默认状态模式、PostgreSQL 同构环境、TTL 与 ResumeToken 规则未形成一致运行合同；
4. README、当前状态文档、工程规则、演进日志、注释和模块依赖已经与实现发生明显漂移。

本文把已逐项确认的决定收敛为一个可审核设计。本文只定义目标状态、职责边界、不变量、迁移原则和验收门；文件级任务、提交拆分和命令顺序留给用户审核通过后的 Implementation Plan。

## 2. 当前事实基线

### 2.1 已成立的生产权威

当前 Agent 主链为：

```text
AgentTurnRequest
→ AgentTurnCommand
→ GoalResolver
→ SemanticPlanCompiler / SemanticPlanValidator
→ SemanticTurnEngine
→ Portfolio / General / Cross-domain Capability
→ PublicAgentTurnProjector
→ AgentStateStore settlement
→ /api/agent/turns
→ Frontend closed PublicAgentTurn parser
```

以下架构决定继续有效，不在本轮重新设计：

- 唯一 `AgentTurnLifecycleService`；
- `ASK | CONTINUE | RESOLVE_CLARIFICATION` closed commands；
- 模型只提出 Goal，不产生可信 Task、DAG、Provider 或工具调用；
- `SemanticPlanCompiler` 与 `SemanticTurnEngine` 是唯一计划和执行权威；
- `PublicAgentTurnProjector` 是公开正文、Goal coverage、来源和恢复动作的唯一权威；
- 无版本 `/api/agent/turns` 与 `/api/agent/conversations/current` 四条资源；
- 不恢复 `/api/v2/answers`、stp-v1/v2/v3、旧 Router、Confirmation、ConversationAnswer DTO 或兼容桥；
- 回退只使用 Git、JAR 或整体部署版本，不保留运行时双栈。

### 2.2 当前公开内容事实

运行时内容事实只以随包 `public-data/bundle/manifest.json` 为准。设计基线为 schema `4.0`、内容版本 `2026-08-05.1`。长期文档不得复制多套人工维护的内容数量；需要展示当前数量的文档必须由文档门禁与 manifest 逐项核对。

### 2.3 当前缺陷与前端状态

`docs/15-Agent 2.0真实交互问题清单与修复边界.md` 是当前未关闭 Agent 2.0 缺陷的唯一动态账本。

前端责任区已经完成本地修复，包括：

- pending、failure、draft、notice 按 session 归属；
- 每个会话一个 pending、当前标签页合计最多两个 pending；
- 澄清答案进入 USER 轮次，历史 Challenge 只读；
- 失败或取消的 USER 轮次保留展示但排除出 ConversationWindow；
- client timeout 与用户主动取消分离；
- 同 requestId 显式重试；
- 当前/最近回答来源切换、来源定位、滚动恢复；
- 澄清脱困入口只消费已发布 QuestionPreset 或后端 SuggestedAction，不在叶子组件硬编码业务问题。

这些前端条目在 packaged-JAR、PostgreSQL、慢响应和真实 Provider Exit Gate 完成前继续保持“修复后待验收”，不能提前从 docs/15 删除。

## 3. 目标与非目标

### 3.1 目标

1. 让每个 Turn 在明确的来源准入、系统并发和 absolute deadline 内形成唯一终局；
2. 让澄清恢复同一个用户 Goal，而不是把答案重新解释成新问题；
3. 使用短期、加密、typed PostgreSQL Agent State 验证生产同构行为；
4. 统一本地、测试、生产的 State Adapter 合同与 TTL；
5. 恢复真实可执行的发布质量门；
6. 重建少量当前权威文档，并让可机械判断的事实受发布门保护；
7. 用中文注释解释权威、原因、安全和失败边界；
8. 在行为稳定后完成 `turn` 对旧 `answer` 包的最终纵向替换。

### 3.2 非目标

- 不增加 SSE、WebSocket、流式 Token 或过程动画；
- 不增加多 Agent、动态工具、工作流 DSL、DurableTask 或长期记忆；
- 不保存访客原始问题、ConversationWindow、Prompt 或模型原始响应；
- 不增加 GET polling endpoint、第二个结果缓存或新幂等权威；
- 不恢复旧 Answer API 或版本兼容层；
- 不在行为修复期间同时进行 `answer → turn` 大搬迁；
- 不给所有 getter、DTO 和显然代码机械补注释；
- 不创建 docs/11 旧版归档副本。

## 4. 变更分级与硬不变量

### 4.1 Level 1

- 项目演进日志排序与简化；
- 当前/历史文档定位；
- 不改变行为的中文注释；
- 文档事实检查器；
- 质量检查器正则误报修复。

### 4.2 Level 2

- 来源限流与单实例 ActiveTurn 准入；
- 在现有 Turn/Task/Store 合同内落实 absolute deadline；
- 本地启动器、运行 profile 和 PostgreSQL readiness；
- 前端在现有 closed contract 内的会话状态、超时和来源体验修复。

### 4.3 Level 3 Replacement Slice

- Clarification State 改为可恢复原 Goal 的 typed 状态；
- PostgreSQL Agent State schema 与 Codec 的最终迁移；
- `answer` 中仍被生产链使用的 State、Model、Portfolio、Projection、Eval 能力纵向迁入 `turn`；
- 任何会改变 PublicAgentTurn、closed command、状态持久化语义或共享前后端合同的后续变更。

### 4.4 不可放弃的硬不变量

- 每个概念只有一个生产权威；
- 不保留运行时兼容桥和旧链 fallback；
- PublicAgentTurn 只投影一次；
- 取消、完成、超时只允许一个终局；
- 前端不重新计算 Goal coverage、来源或业务成功；
- 访客问题、Prompt、模型原始输出、凭据和私有资产不落盘；
- 未运行的验证不能记录为 PASS；
- `turn → answer` 生产依赖归零前，架构整体状态不得标为 COMPLETE。

## 5. Turn 准入、并发与错误边界

### 5.1 三层并发保护

首发初始值：

| 边界 | 初始值 | 所有者 | 当前状态 |
|---|---:|---|---|
| 单匿名来源请求频率 | 10 RPM | HTTP/Admission 边界 | 待后端实施 |
| 单匿名来源 Active Turn | 2 | HTTP/Admission 边界 | 待后端实施 |
| 单实例全局 Active Turn | 8 | Lifecycle | 待后端实施 |
| 单 Turn 并行 Task | 4 | SemanticTurnEngine | 已存在，待回归验收 |
| 单标签页 pending Turn | 2 | Frontend session controller | 前端已实现，待联合验收 |
| 单前端 session pending Turn | 1 | Frontend session controller | 前端已实现，待联合验收 |

状态列区分现状与目标：已实现项只进入联合验收排程，不重复立项；待实施项才进入后端实施计划。

来源地址只用于进程内 HMAC 匿名标识，不记录原始 IP。来源身份不进入 request fingerprint、Goal、Plan、Answer Resolution、Context 或 replay identity。

多实例部署时，应用内来源限制只声明为 per-instance best effort；真实全局限流交给部署网关，不把分布式限流塞进 Agent 领域模型。

### 5.2 准入顺序

```text
HTTP shape / credential validation
→ source RPM and source concurrency
→ global ActiveTurn slot
→ TurnExecutionStore claim
→ ActiveTurn owner registration
→ Resolve / Plan / Execute / Project / Settle
→ finally release source and global slots
```

Replay 也经过来源级准入，防止反复读取短期结果；它只短暂占用全局槽位，不进入 Engine。

### 5.3 公开错误

三类准入失败统一为以下双通道冻结合同，响应必须同时提供标准 HTTP Header 与 JSON error envelope：

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 5
```

```json
{
  "error": {
    "code": "RATE_LIMITED",
    "retryable": true,
    "retryAfterSeconds": 5
  }
}
```

`Retry-After` 与 `error.retryAfterSeconds` 必须来自同一次计算并保持相等。Header 服务标准 HTTP 客户端、网关与代理；JSON 字段服务当前前端错误投影。不得只提供其中一个，也不得让二者使用不同等待时间。

内部诊断可区分 `SOURCE_RPM_LIMIT`、`SOURCE_CONCURRENCY_LIMIT` 和 `GLOBAL_ACTIVE_TURN_LIMIT`，但不得把原始来源、问题、Token 或内部容量细节暴露给前端。

## 6. 单一 TurnDeadline

### 6.1 初始预算

| 预算 | 初始值 |
|---|---:|
| Goal Interpretation operation cap | 8 秒 |
| General Knowledge operation cap | 10 秒 |
| Portfolio Fact Expression operation cap | 4 秒 |
| PostgreSQL/Retrieval 单次 I/O cap | 3 秒 |
| Turn absolute deadline | 20 秒 |
| Settlement reserve | 2 秒 |
| Frontend request wait cap | 25 秒 |
| Gateway/proxy timeout | 不小于 30 秒 |
| TurnExecutionStore lease | 35 秒 |

这些值是首轮生产安全基线，不是永久常量。后续只能根据不含内容的 p95/p99、timeout、late-result、fallback 和 settlement 指标调整，并保持以下关系：

```text
operation cap
< execution deadline
< turn absolute deadline
< frontend wait cap
< gateway timeout
< store lease
```

当前前端 20 秒等待上限是后端 absolute deadline 落地前的临时值，不是目标合同。后端 20 秒 TurnDeadline 通过验证后，前端必须原子切换为 25 秒，并同步更新 docs/15 的修复进展、deadline 决策和相关测试/注释。

### 6.2 时间所有权

- TurnDeadline 在 claim/接纳时创建一次；
- execution deadline 为 TurnDeadline 减去 2 秒 settlement reserve；
- Goal Interpretation、Planning、Execution、Projection、Settlement 和响应准备共享同一绝对时间轴；
- 所有模型、检索和数据库调用使用 `min(operation cap, remaining time)`；
- 到 execution deadline 后不启动新 Task 或 fallback；
- 已完成的独立 Goal 保留，可形成 PARTIAL；
- 没有可信产出时形成 NO_RESULT 或明确 CapabilityUnavailable；
- deadline 后到达的结果不得进入 Outcome、Context、Replay 或响应；
- Future cancel 和线程中断只是清理 backstop，不能替代真实 I/O deadline；
- Provider 已返回响应头但响应体停滞时，也必须在 operation cap 内终止。

### 6.3 客户端超时、取消与回收

- 用户主动取消：前端立即结束本地等待，发送 `DELETE /api/agent/turns/{requestId}`，随后 abort fetch；
- 内部等待超时：前端显示明确 TIMEOUT 与同 requestId 重试入口，不把它伪装成用户取消；
- 正常情况下服务端 20 秒终局先于前端 25 秒等待上限；
- 网络断开或客户端兜底超时后，同 requestId POST 复用现有 replay 权威；
- 不自动轮询，不新增结果查询 endpoint；
- Cancel 与 Complete 竞争同一个 Store 终局；Cancel 先成功时迟到结果不得提交。

## 7. Clarification Goal 恢复

### 7.1 推荐与社交输入规则

- “推荐项目”未给数量时默认 2 个；
- 明确请求 1～5 个时直接执行；
- 未点名候选不是歧义，候选选择属于 Recommendation Capability；
- 0、超过 5 或数量冲突时要求用户改为 1～5；
- 未提供岗位、技术栈或偏好时不强迫澄清，按公开项目综合代表性推荐；
- 合法约束只有在用户明确提供时应用；
- 只有 Project/Case 候选域、指代主体或必要约束真的无法确定时才澄清；
- 结果不足返回 PARTIAL 与缺口，不让用户替推荐器挑候选；
- 问候、致谢等安全社交输入在模型前确定性投影为 CONVERSATIONAL。

### 7.2 `BlockedGoalTemplate`

Clarification State 不保存完整 `UserGoalProposal`，避免 `inputAnchor` 和原始问题进入持久化。服务端保存最小 typed template：

- goal kind；
- 稳定公开主体 ID；
- requested outputs；
- requested size、Facet、comparison dimensions、closed constraints 等强类型参数；
- unresolved field 与 resolved field set；
- contentReleaseId；
- Conversation/ResumeToken hash 绑定；
- clarification depth、expiry 与一次性消费状态。

禁止保存：

- 原始问题或完整 input anchor；
- ConversationWindow；
- Prompt 或模型输出；
- 未归一化的长文本；
- raw Evidence、内部 Task/Plan 或私有 ID。

本设计显式取代历史缺陷 ID A2-02（已从动态账本移除）中“恢复原始输入锚点”的字面要求。澄清恢复的是同一 Goal 的 typed identity、主体、requested size、requested outputs 与约束，不恢复或持久化原始问题片段。不得据旧措辞保存完整或部分访客原文。

### 7.3 Resolve 流程

```text
consume challenge once
→ validate Conversation / Token / ContentRelease / expiry
→ normalize CHOICE or bounded TEXT into typed value
→ merge into BlockedGoalTemplate
→ verify information gain and goal completeness
→ compile through the same SemanticPlanCompiler
```

TEXT 原文只在当前 Resolve 请求内参与归一化；能转成公开主体、闭合枚举或有界参数后，只保留 typed 结果。

### 7.4 级联终止

- 同一个字段不得澄清第二次；
- 无信息增益的回答不得产生同字段 Challenge；
- 每个 Goal 最多两次澄清，且必须针对两个不同字段；
- 第二次后仍无法形成安全 Goal 时，返回明确终局和新 ASK 动作；
- Challenge TTL 为 5 分钟、一次消费；
- 过期、重复、错误 Token、跨 Conversation 和内容版本变化均 fail-closed；
- 不建设通用表单、状态机或 workflow framework。

### 7.5 State schema 迁移

该变化属于 Level 3 State Replacement Slice。项目尚未生产，State 是短期可丢失数据，因此迁移直接切换到最终 schema/codec：

- 新 schema 与 Codec 同一 Slice 进入生产；
- 旧本地 Challenge/Context 可以失效；
- 不保留双 reader、兼容字段或旧恢复路径；
- Memory 与 PostgreSQL Adapter 必须通过同一 Store contract scenarios。

## 8. Agent State、TTL 与浏览器凭证

### 8.1 TTL

| 状态 | Absolute TTL |
|---|---:|
| Turn claim lease | 35 秒 |
| Clarification Challenge | 5 分钟 |
| ResumeToken / Conversation Session | 30 分钟 |
| Continuation Context | 30 分钟 |
| Completed PublicAgentTurn replay | 30 分钟 |
| Cancelled/terminal record | 30 分钟 |

所有 TTL 为 absolute TTL，不因读取、刷新、轮换或重放续期。Previous encryption key 的保留窗口必须大于最长 State TTL 并覆盖 cleanup 延迟。

Cleanup 小批量、幂等、有上限地处理 expired lease、replay、challenge、context、session、orphan record、revoked token 和无法再解密的旧 key payload。

### 8.2 可持久化数据

允许短期、认证加密保存：

- 最终公开 PublicAgentTurn snapshot；
- requestId 与 keyed fingerprint；
- typed Continuation Context；
- typed Clarification Challenge/BlockedGoalTemplate；
- ResumeToken hash、terminal 和 cleanup metadata。

禁止持久化：

- 访客问题；
- ConversationWindow；
- Prompt；
- raw model output；
- Task SemanticResult 或内部诊断；
- raw Evidence、凭据或私有内容。

### 8.3 ResumeToken

浏览器只允许在当前标签页 `sessionStorage` 保存一个短期 ResumeToken：

- 不保存问题、回答、ContextHandle、requestId 历史或 PublicAgentTurn；
- 只通过 `Authorization: Bearer` 发送；
- 不进入 URL、history、日志、诊断或请求正文；
- 401 后立即删除；
- clear conversation 成功后删除；
- 页面刷新只恢复匿名会话身份，不恢复历史消息；
- 页面提示历史消息不会恢复；
- CSP、无动态 HTML 和第三方脚本控制是主要浏览器防线。

## 9. 本地与生产运行模式

### 9.1 模式语义

| 环境/模式 | Agent State | 用途 |
|---|---|---|
| 标准 local | PostgreSQL | 日常完整开发、重启恢复、生产同构 |
| 显式 IN_MEMORY | 进程内 | 单元测试、快速开发、专项排障 |
| prod | PostgreSQL | 生产与正式验收 |
| DISABLED | 不执行 Turn State | 只读作品集浏览模式 |

Production 不允许 PostgreSQL 故障后自动退回 Memory。State readiness 与公开站点 liveness 分离：State 不可用时公开页面继续工作，Agent 明确不可用。

### 9.2 本地数据库拓扑

沿用现有三个逻辑隔离数据库：

- Public Content DB：只读公开投影，可从同 release Bundle 重建；
- Governance DB：显式内容治理和导入；
- Context/Agent State DB：短期加密 Turn、Context、Challenge、Replay。

标准本地 Agent 只强制 Agent State DB ready；Public DB 可继续使用随包 Bundle，Governance DB 只在治理命令中启用。

### 9.3 生命周期与配置

- `postgres-local.ps1` 显式负责 start/bootstrap/status/verify/stop/reset；
- `start-local.ps1` 只检查 readiness，不自动启动、停止或 reset Docker；
- 数据库未就绪时输出稳定错误与正确命令；
- 应用退出不停止 PostgreSQL；
- `SecretsFile` 只在启用真实模型时必填；
- 普通确定性本地开发不要求 Provider Secret；
- PostgreSQL 本地配置默认来自 Git 忽略文件；
- Context token/payload key 必须跨本地进程稳定，才能验证重启恢复；
- `.env.postgres.example` 只记录变量名和安全说明；
- IN_MEMORY 必须显式启用，不能成为 PostgreSQL 失败后的静默 fallback；
- `start-local.ps1` 删除旧 routing/model-expression 配置键，改用当前 Goal Interpretation、General 与 Portfolio Fact Expression operation 配置。

## 10. 前端已确认行为

以下行为属于现有 PublicAgentTurn/Command 合同内的消费修复，不建立第二业务权威：

1. pending 可以跨会话并存；每 session 一个、每标签页最多两个；
2. 超过标签页上限时其他会话仍可浏览和编辑草稿，但所有新 Turn 入口被阻止并显示状态提示；
3. 结果、取消和重试只归属原 session，不自动切换当前会话；
4. 删除 pending session 时先 best-effort cancel，再释放本地槽位；
5. 失败或取消的 USER 轮次保留、弱化并排除出 ConversationWindow；同 requestId 成功重试后恢复；
6. CHOICE 显示公开选项标签，TEXT 在当前页面内存显示原文；
7. Challenge 使用 ACTIVE/CONSUMED/SUPERSEDED 本地生命周期，只有最新 ACTIVE 可操作；
8. timeout 显示明确恢复入口，用户主动取消保持静默；
9. 当前 Turn 为 ANSWER 时显示“当前回答来源”，否则显示弱化的“最近回答来源”；
10. 来源定位只依据 sectionId 与 publicSourceKeys，不引入前端事实判断；
11. 澄清脱困入口优先消费后端 SuggestedAction；缺少时只使用父组件传入的已发布 QuestionPreset/Case 建议，叶子组件不硬编码业务问题；
12. 滚动、定位、高亮和 reduced-motion 只属于 UI 状态，不进入 Agent 合同。

前端本地单元测试和构建报告不替代 packaged-JAR、PostgreSQL、body-stall 和真实 Provider Exit Gate。

## 11. Java 工程规则

本节的 `record`、`var` 与 Lombok 决策必须原子同步到 `AGENTS.md`、`docs/04`、质量检查器及其正反例自测。任一权威或门禁未同步时，工程规则变更不成立，也不能据此声明发布验证通过。

### 11.1 record

有限允许 Java `record`，仅用于纯不可变数据载体，例如：

- API envelope/response metadata；
- 内部 closed result；
- 数据库 Row projection；
- 小型公开引用与无生命周期 tuple。

以下对象继续使用显式不可变类：

- 有复杂不变量或生命周期的领域对象；
- 预期持续演化的 Command/Goal/Plan/Context 聚合；
- 需要隐藏内部状态或提供深接口的模块入口；
- record component 中包含可变集合且无法明确防御性复制的类型。

Record 政策通过代码审查与针对值语义/防御性复制的测试治理，不再用“禁止所有 record”的粗粒度正则。

### 11.2 var 与 Lombok

- 生产和测试 Java 继续全面禁止 `var`；
- 继续禁止 Lombok；
- 当前测试中的 `var` 改为显式类型；
- 质量检查器只匹配真实局部变量声明，不把 `ClarificationStore.Record record` 误报成 record 声明；

## 12. 文档治理

### 12.1 当前权威文档

- `README.md`：安装、启动、运行模式、当前 API 和验证入口；
- `AGENTS.md`：最高级产品、安全和工程规则；
- `docs/00`：简短权威导航；
- `docs/04-06`：工程约束、发布包契约和发布手册；
- `docs/08-10`：当前实现、资产状态和本地 PostgreSQL；
- `docs/15`：动态缺陷账本；
- `docs/16` 与 architecture status JSON：Agent 架构治理。

### 12.2 历史文档

`docs/01-03`、`docs/07`、`docs/11-14`、specs、plans、reports 和 handoffs 可以保留历史内容，但必须在文件头明确“历史、已取代或非权威”。历史文档可以保留当时的旧 API 和旧状态，不为通过当前事实门而篡改历史。

### 12.3 README、docs/00 与 docs/08

- README 从当前运行方式重写，不在旧正文上替换几个 endpoint；
- docs/00 只保留权威顺序、当前文档入口和历史规则，不维护百行历史状态表；
- docs/08 只描述当前能力、默认开关、限制和部署状态；
- 历史阶段、测试数量和中间协议状态移入 docs/11 或链接文档；
- README 不复制完整内容数量；docs/08 的当前快照必须与 manifest 一致；
- SECURITY.md 按 PostgreSQL Agent State、30 分钟 replay、typed Context 和 sessionStorage 单 Token 重写。

### 12.4 项目演进日志

`docs/11-项目演进日志.md` 统一为：

- 日期严格正序；
- 同一日期只有一个二级标题；
- 事件标题使用业务含义，不使用“阶段一/阶段二”；
- 每个事件只保留核心实现、相对前一方向的变化、当前边界和 1～3 个文档链接；
- 删除测试数量、JAR hash、提交信息、Task 编号、字段枚举和实施步骤；
- 同一能力的前端、后端和联调记录合并；
- Git 历史、设计、计划和专项报告是详细档案，不创建旧日志归档副本；
- 后续新事件追加到文件末尾。

### 12.5 文档事实门

新增只检查当前权威文档的 `documentation-check`：

- 阻断旧 `/api/v2`、stp-v1/v2/v3 和旧 Runtime 被描述为当前能力；
- 从 manifest 核对 schema、contentVersion 和必要数量；
- 验证 docs/11 日期正序、日期唯一、事件标题不编号、链接存在；
- 验证 README 当前环境变量能在配置或启动脚本找到；
- 验证四条无版本 Agent 资源；
- 验证当前/历史文档定位；
- 提供正反例自测并接入 verify-release；
- 不用正则评价文档质量、语气或是否“像人写的”。

## 13. 中文注释治理

### 13.1 语言与内容

- 项目业务注释默认中文；
- 标识符保持英文；HTTP、JSON、Provider、deadline、fallback 等标准术语可以保留；
- 注释解释权威、原因、约束、失败语义和安全边界，不逐行复述代码；
- D-38、P3、S5-01 等历史编号可以作为末尾依据，但正文必须脱离历史文档独立可懂；
- TODO 不代替 docs/15、Issue 或设计决策。

### 13.2 必须优先覆盖的模块

1. Lifecycle、Planning、Execution、State、Projection；
2. Provider、Retrieval、Bundle Loader、Importer 与安全诊断；
3. Frontend API、PublicTurn Mapper、Workspace 与 Session 只做统一复查，不覆盖前端 Agent 已完成的文件所有权；
4. 其他大文件只补真正非直观的业务规则。

### 13.3 不建立注释覆盖率 KPI

不要求每个 DTO/getter 有注释，不设置注释百分比或中文字符比例门禁。注释质量由模块设计、审查清单和过时注释删除负责。

## 14. `turn` 模块最终收敛

### 14.1 当前判断

运行时权威已经切换到 `turn`，但 `turn → answer` 与 `answer → turn` 生产依赖仍存在。`answer` 中一部分代码是仍有价值但物理位置未迁移的 State、Model、Portfolio 和 Projection 能力；另一部分是旧服务、死 Bean 或迁移遗留。

因此 architecture status 在生产双向依赖归零前保持 `IN_PROGRESS`，不能用“入口已经切换”替代完整模块收敛。

### 14.2 迁移方向

行为缺陷和生产保护稳定后，再按独立 Replacement Slice 迁移：

1. **State：** PostgreSQL Agent State、Conversation Session 和配置进入 `turn.state`/对应 infrastructure；
2. **Model：** Provider Registry、Structured Transport 和 operation 配置进入明确 model infrastructure，领域只依赖 typed ports；
3. **Portfolio：** Retriever、候选、检索计划和 PostgreSQL Adapter 纵向进入 `turn.capability.portfolio`；
4. **Projection：** 真正属于 PublicTurn 的 Section、Source、Support 类型进入 `turn.projection` 或能力边界；
5. **Cleanup：** 删除没有新入口调用的旧 answer routing/service/domain/config Bean；
6. **Eval：** Eval 只调用新的 typed seam，删除旧 P3/P4/P5/legacy 执行入口。

### 14.3 Replacement 规则

- 新实现进入唯一生产入口时，同 Slice 删除旧实现、配置、测试和 fixture；
- 不创建 `turn → answer` 转发壳；
- 不保留新旧 Bean 配置开关；
- 每个 Slice 有新增、迁移、删除和零引用清单；
- 最终门要求 `turn` 生产代码不 import `answer`，`answer` 不 import `turn`；
- 模块迁移不得与澄清或 deadline 行为修复混在同一 Slice；
- 最终全量门通过后，architecture status 才能恢复为 COMPLETE。

## 15. 实施顺序

本文审核通过后的 Plan 必须遵守以下顺序：

1. 解除发布门自相矛盾：architecture status、record/var 规则、检查器误报和测试 var；
2. 后端生产安全：admission、ActiveTurns、deadline、I/O timeout、cancel/late-result；
3. 后端澄清权威：BlockedGoalTemplate、State schema、Goal 恢复、推荐和社交边界；
4. 本地 PostgreSQL 与 TTL：profile、readiness、密钥、cleanup、启动器配置；
5. 前后端联合验收：后端 20 秒 TurnDeadline 通过后，把 `agentTurnApi.ts` 等待常量从临时 20 秒原子调整为 25 秒；同步修改该常量附近注释、docs/15 §10.5 前端修复状态、docs/15 §11.4 deadline 决策、前端 timeout 测试，再执行 Testcontainers、packaged-JAR、body-stall 和真实 Provider 验收；
6. 文档与日志重建：README、当前文档、docs/11、docs/15、documentation-check；
7. 定向中文注释治理；
8. State、Model、Portfolio、Projection、Eval 的 `answer → turn` Replacement Slices；
9. 全部门禁重新通过后，将 architecture status 恢复为 COMPLETE。

阶段 2～5 关闭真实行为缺陷；阶段 6～7 修复知识与可维护性；阶段 8 只在行为基线稳定后执行，避免同时改变行为和物理结构。

## 16. 验证与 Exit Gates

### 16.1 工程与静态门

- Java 质量检查器正反例；
- 禁止 var、Lombok，有限 record 政策与值语义测试；
- architecture check 与最终 turn/answer 零引用门；
- documentation-check 正反例；
- privacy check 覆盖 production source、Bundle、frontend dist 和最终 JAR；
- 前端 typecheck、unit/component tests、production build；
- 后端全量 Maven tests/package。

### 16.2 Deadline 与取消

- Goal、General、Expression、Retrieval 与 State I/O 都受同一 TurnDeadline；
- Provider response body stall 在 cap 内终止；
- execution deadline 后不启动 Task/fallback；
- late result 不进入 Outcome/Settlement；
- cancel/complete race 只有一个终局；
- 来源和全局 ActiveTurn slot 在成功、失败、取消、超时和异常路径均释放；
- 429 具有稳定 error envelope 与 Retry-After。

### 16.3 Clarification

- 推荐 1～5 直接执行，缺省数量为 2；
- Greeting 稳定产生 CONVERSATIONAL；
- CHOICE/TEXT 恢复同一个 goal kind、requested size、subjects 与 constraints；
- Challenge 一次消费、5 分钟过期、Token/Conversation/ContentRelease 绑定；
- 同字段不得重复、最多两次不同字段；
- 无信息增益进入明确终局；
- PostgreSQL 与 Memory 通过同一 Store scenarios；
- State payload 不含原问题、ConversationWindow、Prompt 和模型原始输出。

### 16.4 Frontend 与浏览器

- 两个 session pending 后第三个新 Turn 被阻止，槽位释放后可提交；
- 结果、failure、cancel 和 retry 不跨 session；
- failed/cancelled USER 轮次不进入 ConversationWindow；
- Challenge ACTIVE/CONSUMED/SUPERSEDED 正确；
- timeout 与 cancel UI 语义分离；
- 同 requestId 重试取得 replay 或明确终局；
- 当前/最近来源和定位语义一致；
- 澄清脱困入口只渲染后端 SuggestedAction 或父组件传入的已发布 QuestionPreset/Case 建议；带 presetId 时发送 PRESET，缺少 presetId 时才发送 FREE_TEXT；CONSUMED/SUPERSEDED Challenge 不显示入口；叶子组件不得硬编码业务问题；点击产生的提问不进入 URL、浏览器 storage 或历史；
- 桌面与移动端 packaged-JAR E2E；
- reduced-motion、键盘和状态可访问性无回归。

### 16.5 PostgreSQL 与真实 Provider

- 本地标准模式使用 PostgreSQL State；
- 服务重启后在 TTL 内可 replay/continue；
- TTL 不因读取续期，cleanup 可复验；
- PostgreSQL State 不可用不拖垮公开内容站点；
- Fake Provider 覆盖 body stall、invalid JSON、timeout 和 late result；
- 真实 Provider 显式授权验证 conversational、general、recommendation、clarification/resolve；
- `Goal Interpretation p95 + Planning overhead p95 + General p95` 必须小于 18 秒 execution window 并保留明确余量；如果没有余量，能力保持未晋级并依据观测调整 operation cap 或 TurnDeadline；
- 验收输出不含问题、回答、Prompt、Token、Provider raw payload 或凭据。

### 16.6 缺陷账本关闭规则

docs/15 中某条缺陷只有在以下条件同时满足后才能删除：

- 生产代码修复；
- 针对性测试；
- 责任区全量测试/build；
- 风险对应的 PostgreSQL、packaged-JAR、browser 或 Provider gate；
- 原始用户路径复验；
- 未通过兼容桥、吞错或放松安全约束掩盖问题。

## 17. Rollback 与发布状态

- 所有行为和架构迁移按小型中文提交组织，但提交需要用户单独授权；
- Level 3 Slice 的运行回退只通过 Git/JAR/部署版本；
- PostgreSQL State 是短期可丢失数据，schema 回退不读取新版本 payload；必要时清空本地/短期 State，而不影响 Public Content；
- 新旧 API、State Codec、Router 或模块实现不在运行时并存；
- 任一 hard invariant、发布门或必需 Exit Gate 未通过时，不得标记 release-ready；
- architecture COMPLETE 与“无已知产品缺陷/已生产部署”是不同状态，但模块 Replacement 未完成时 architecture 本身也保持 IN_PROGRESS。

## 18. 风险与控制

### 18.1 同时修改范围过大

控制：严格遵守第 15 节顺序；行为修复、文档治理和包迁移不混在同一 Slice。

### 18.2 PostgreSQL 本地门槛提高

控制：保留显式 IN_MEMORY 快速模式；标准入口提供 readiness、status 和清晰错误，不自动代管 Docker。

### 18.3 Deadline 过紧导致能力不可用

控制：首轮值可配置；用真实 Provider p95/p99 调整；不允许客户端比服务端先静默放弃。Goal 8 秒与 General 10 秒的 operation cap 之和恰好等于 18 秒 execution window，但二者只是上限、不是预留预算，实际第二段调用仍会被 remaining time 裁剪。首次晋级必须重点观察两段串行路径的 p95/p99 和 Planning 开销；不能把“总和刚好等于窗口”当作长期可接受的容量证明。

### 18.4 Clarification State 泄露用户文本

控制：只持久化 BlockedGoalTemplate typed values；TEXT 原文只在当前 Resolve 请求内归一化；Codec/数据库集成测试扫描禁止字段。

### 18.5 文档门误伤历史文档

控制：documentation-check 只扫描当前权威集合；历史文件只验证定位头和链接，不禁止旧事实。

### 18.6 注释治理制造噪声

控制：不设置覆盖率，不注释显然代码；优先模块边界、安全原因和非直观不变量。

### 18.7 模块迁移形成第二套实现

控制：每个 Slice 必须同时列出删除目标和零引用门；新实现接入生产后同 Slice 删除旧实现。

## 19. 审核结论边界

用户批准本文只表示设计方向与约束被接受。批准后下一步是单独创建 Implementation Plan，列出文件级任务、依赖、测试、删除清单、验证命令和检查点。

在用户审核本文并明确批准前：

- 不修改生产代码；
- 不修改前端 Agent 已完成的责任区；
- 不创建 Implementation Plan；
- 不提交 Git；
- 不将 architecture status、docs/15 或 release 状态标记为完成。
