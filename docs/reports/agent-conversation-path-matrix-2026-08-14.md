# Agent 对话路径全量盘点与实测报告（2026-08-14）
<!-- DOCUMENT_STATUS: HISTORICAL -->

## 1. 结论

本轮沿着真实入口 `POST /api/v2/answers`，从请求准入、主体解析、语义路由、计划确认、任务执行、证据组合、上下文持久化，一直验证到前端桌面端与移动端渲染。当前可达的对话路径均已落入下方矩阵，并由单元/集成/打包 JAR/浏览器测试中的至少一层覆盖；关键主路径同时经过真实打包进程与 PostgreSQL。

最终回归结果：

- 后端：1191 个测试，0 failure，0 error，5 skipped。
- 前端单测：62 个测试文件、674 个测试全部通过。
- Mock 浏览器 E2E：桌面端与移动端共 118 项，96 passed、22 skipped（22 项是 real-api lane，在 mock lane 按配置跳过）。
- 打包 JAR + PostgreSQL + real-api 浏览器 E2E：118 项中 82 passed、36 skipped（36 项是 mock-only lane），并通过启动、迁移、请求关联、诊断、Case API、Case Agent、日志隐私和进程清理门禁。
- 类型检查、生产构建、JAR runner 自测全部通过。

## 2. 实际运行链路

```text
HTTP 请求
  -> DTO/大小/字段/契约校验
  -> 限流、并发、超时、幂等与会话授权
  -> preset/结构化主体/显式文本/上下文主体解析
  -> 7 类语义任务编译为 DAG
  -> READY / PARTIAL_READY / 确认 / 澄清 / 边界 / 拒绝
  -> Portfolio / General / Synthesis 执行器
  -> 检索、证据核验、确定性或模型组合
  -> stp-v1 / stp-v2 响应投影
  -> 前端安全映射、状态展示、证据导航和续接
```

公共内容快照为 `2026-08-05.1`，包含 6 个项目、52 个 Case、18 个已发布问题 preset。

## 3. 全量路径矩阵

“实测”表示本轮实际执行；其中“后端全量”包含单元、Spring/MockMvc、Testcontainers PostgreSQL 和各 P5 evaluation suite，“浏览器双端”表示 Chromium desktop + mobile。

### 3.1 请求准入与错误语义

| 路径 | 预期 | 实测证据 | 结果 |
|---|---|---|---|
| 合法 ASK | 进入语义路由 | MockMvc、真实 JAR HTTP、浏览器双端 | 通过 |
| 缺失/非法 `turnId`、`requestToken`、`question` | 400 | DTO validation tests | 通过 |
| 未知 JSON 字段 | 400、fail closed | controller/JAR diagnostics smoke | 通过 |
| 请求体超限 | 413 | JAR body-limit smoke | 通过 |
| 消息窗口、角色、长度非法 | 400 | request validation tests | 通过 |
| 速率限制 | 429 + 可重试提示 | production policy tests、浏览器 diagnostics gate | 通过；并修正测试配置键 |
| 并发/超时/调用方取消 | 受控失败且不追加伪答案 | backend production tests、浏览器 diagnostics gate | 通过 |
| 相同 token + 相同 payload | 返回同一结果/完成回执 | memory/PostgreSQL receipt tests、HTTP replay smoke | 通过 |
| 相同 token + 不同 payload | 409 conflict | MockMvc + PostgreSQL 真实复跑 | 通过；本轮修复 |
| 受控 `ResponseStatusException` | 保留原始 4xx/409，不变成 500 | exception handler tests | 通过；本轮修复 |

### 3.2 动作与协议

| 路径 | 预期 | 实测证据 | 结果 |
|---|---|---|---|
| `ASK` | 新计划或直接执行 | backend full + real API | 通过 |
| `CONFIRM_PLAN` | 校验确认信封后执行 | confirmation service/controller/E2E A-H | 通过 |
| `REGENERATE_PLAN` | 基于 pending plan 调整 | backend routing + E2E A-H | 通过 |
| 缺省兼容协议 | `stp-v1` | mapper/runtime/JAR Case smoke | 通过 |
| 显式新协议 | `stp-v2` | contract serialization、前端 real-api | 通过 |
| 语法合法但不支持的 `stp-v9` | 409 `AGENT_TURN_CONTRACT_UNSUPPORTED` | MockMvc integration | 通过；本轮修复（原为 DTO 400） |
| v1 请求携带仅 v2 支持语义 | fail closed | contract policy tests | 通过 |
| 篡改、过期、版本漂移的确认计划 | `PLAN_INVALIDATED`/拒绝 | crypto/confirmation/controller tests | 通过 |

### 3.3 主体与上下文解析

| 路径 | 预期 | 实测证据 | 结果 |
|---|---|---|---|
| `projectSlug` / `caseSlug` | 绑定已发布主体 | Project/Case bundle integration、JAR Case smoke | 通过 |
| 未知结构化 slug | `INVALID_INPUT`，不降级为通用知识 | PresetContractBundleIntegrationTest | 通过 |
| preset 所属主体 | preset 合同同时绑定其发布主体 | 18 preset 全量循环测试 | 通过；本轮修复 |
| 文本唯一命中项目/Case | 绑定唯一主体 | routing tests + HTTP smoke | 通过 |
| 文本明确命中两个主体并要求比较 | 同时绑定两者并生成比较任务 | integration + HTTP smoke | 通过；本轮修复 |
| 文本裸提及多个主体、无比较意图 | `CLARIFICATION_REQUIRED` | routing + HTTP smoke | 通过 |
| 主动主体/page hint/代词续接 | 作为 hint 解析 | routing context tests | 通过 |
| result reference/pending plan reference | result-bound 解析 | P5 context tests、前端 continuation tests | 通过 |
| 显式语义上下文与 legacy context 冲突 | 澄清或拒绝 | LegacySemanticContextAdapter/RoutingContextResolver tests | 通过 |
| 无主体的作品集介绍/比较 | critical clarification | routing/E2E A-H | 通过 |
| 失效、过期、不兼容 context handle | `CONTEXT_INVALIDATED` + recovery action | PostgreSQL context tests、real-api E2E | 通过 |

### 3.4 七类语义任务

| 任务类型 | 典型入口 | 实测结果 |
|---|---|---|
| `PORTFOLIO_FACT` | 项目/Case 介绍、实现、验证、结果、边界 | 通过：确定性证据回答、部分回答、无材料边界均覆盖 |
| `PORTFOLIO_COMPARE` | 明确比较 2–3 个公开主体 | 通过：双主体真实 HTTP 生成两个任务并可渲染 |
| `PORTFOLIO_RECOMMEND` | 岗位/项目推荐 | 通过：原子 `RECOMMENDATION_RESULT`，真实 API 与双端 UI 覆盖 |
| `PORTFOLIO_REFINE_RECOMMENDATION` | 基于推荐结果换一个/排除 | 通过：result context、续接与失效路径覆盖 |
| `GENERAL_EXPLANATION` | 通用概念解释 | 通过：Provider 开启时实际回答；关闭/超时返回 NOT_SUPPORTED |
| `GENERAL_COMPARISON` | 通用主题比较 | 通过：规则、Provider 不可用与 fake provider lane 覆盖 |
| `SYNTHESIS` | 两个以上上游任务后综合 | 通过：DAG、来源域、部分失败传播与 mixed rendering 覆盖 |

### 3.5 计划决策与执行状态

| 分支 | 实测结果 |
|---|---|
| `READY` | 通过：全部任务可执行 |
| `PARTIAL_READY` | 通过：可执行任务继续，局部缺口带 clarification |
| `CONFIRMATION_REQUIRED` | 通过：高影响/策略触发，未确认不执行 |
| `CLARIFICATION_REQUIRED`（critical/local） | 通过：缺主体、比较主体不足、局部缺口 |
| `BOUNDARY` | 通过：敏感凭据/内部访问请求不携带公开证据 |
| `REJECTED` | 通过：非法引用、协议/输入不可信 |
| `PLAN_INVALIDATED` | 通过：指纹、版本、完整性或能力集变化 |
| `CONTEXT_INVALIDATED` | 通过：句柄过期、无效、结果陈旧、主体不可用 |
| 请求任务数 > 6 | 通过：要求拆分，不执行超预算计划 |
| DAG 顺序与依赖 | 通过：上游失败时下游 blocked/cancelled，不伪造正文 |
| 任务结果 `ANSWERED/PARTIALLY_ANSWERED/NOT_SUPPORTED/EMPTY/BLOCKED/FAILED/CANCELLED/DEGRADED` | 全部由 coordinator、executor、evaluation 与前端状态测试覆盖 |

### 3.6 检索、证据与组合

| 路径 | 实测结果 |
|---|---|
| 关键词检索 | 通过：索引、排序、subject scope、风险门禁 |
| 向量/本地 embedding | 通过：ONNX smoke、向量 codec、PostgreSQL pgvector 集成 |
| hybrid 与 fallback | 通过：候选合并、无向量/不可用回退、真实性约束 |
| 查询风险归一化与拦截 | 通过：不可把敏感输入带入检索 |
| claim/evidence 完整性 | 通过：只允许已验证公开 claim，未知引用 fail closed |
| 确定性组合 | 通过：Portfolio 主路径 |
| `MODEL_GROUNDED` | 通过：fake provider 成功与 draft validation |
| `FALLBACK` | 通过：模型失败保留安全确定性正文 |
| `MIXED` | 通过：不同任务组合模式并存，正文不丢失 |
| Provider timeout/unavailable/invalid draft/integrity failure | 通过：adapter、circuit breaker、composition、evaluation lanes |
| Live Provider | 本轮在正在运行的 8080 实例实际调用通用解释成功；全量失败注入使用 fake-provider lane，未向外部 Provider 人为制造故障 |

### 3.7 会话持久化与续接

| 路径 | 实测结果 |
|---|---|
| 首轮创建 conversation + resume token | 通过：真实 PostgreSQL + real-api E2E |
| 二轮仅通过 header 携带 token | 通过 |
| 刷新只恢复安全摘要，不恢复聊天正文 | 通过 |
| context handle 续接 | 通过：只有可续接 completed task 暴露入口 |
| token 轮换/过期/非法 | 通过：丢弃旧 token，安全开始新会话 |
| DELETE 清理及重复 DELETE | 通过：幂等；服务端未确认时前端不声称已清理 |
| CURRENT / REVALIDATED / STALE 内容版本 | 通过：version policy 与 context invalidation tests |
| completed / in-progress / conflicting receipt | 通过：内存与 JDBC store tests |
| persistence unavailable | 通过：回答保留，状态明确降级 |
| token/payload AES-GCM、密钥 ID 与日志隐私 | 通过：crypto tests + JAR sentinel 日志边界 |

### 3.8 响应投影与前端状态

| 路径 | 实测结果 |
|---|---|
| `ANSWER` 与 `COMPLETION_RECEIPT` | 通过：mapper、API、前端双读 |
| stp-v1 顶层 blocks | 通过 |
| stp-v2 `completedTasks[].resultPayload` | 通过；JAR 门禁已修正为识别真实承载位置 |
| `SECTION_RESULT` / `RECOMMENDATION_RESULT` / `SYNTHESIS_RESULT` | 通过 |
| source catalog、public source reference、站内证据跳转 | 通过 |
| 未知 enum/未知响应形状 | fail closed，不渲染虚假成功 | 通过 |
| A–H 语义状态、错误重试、429 倒计时、503、404 | 浏览器双端通过 |
| desktop/mobile/reduced-motion/无横向溢出 | 浏览器双端通过 |
| 会话仅页面内保存；resume token 仅 sessionStorage | 通过 |

## 4. 18 个已发布 preset 逐项实测

下列每项均从 `/api/v1/public-content` 读取当前合同版本，再实际 POST 到 `/api/v2/answers`，断言作品集作用域、可执行结果、已验证证据和非空正文：

1. `sql-audit-overview`
2. `question-sql-audit-negative-input`
3. `question-sql-audit-partial-success`
4. `question-case-multilingual-overview`
5. `question-case-role-reset-overview`
6. `question-case-codegraph-overview`
7. `question-sql-audit-async-and-recovery`
8. `question-sql-audit-progress-fallback`
9. `question-sql-audit-archive-and-truncation`
10. `question-case-multilingual-verification-sequence`
11. `question-case-multilingual-recovery-boundary`
12. `question-case-role-reset-acceptance-result`
13. `question-case-role-reset-safety-boundary`
14. `question-case-codegraph-method`
15. `question-case-codegraph-quality-boundary`
16. `question-abtest-overview`
17. `question-abtest-stratification-bucketing`
18. `question-abtest-stable-assignment-and-rollback`

另行实测 stale contract 和未知 preset：均 fail closed，不搜索猜测替代答案。

## 5. 本轮发现并统一修复的问题

1. **Preset 合同未绑定发布主体**：仅校验 ID/版本，导致部分 preset 被路由到 GENERAL 或澄清。现从当前发布快照反查所属项目/Case，并注入显式主体。
2. **安全 fallback 已生成但 resolution 仍是 NOT_SUPPORTED**：主任务无 payload 时未考虑 runtime 投影的确定性 fallback。现统一按可投影结果计算 `ANSWERED`。
3. **明确双主体比较被误判歧义**：现只有带比较标记的精确多主体文本会绑定全部主体；裸多主体仍要求澄清。
4. **限流测试配置键拼错**：从 `portfolio.answer.requests-per-minute` 修正为实际生产键 `portfolio.answer-production.requests-per-minute`。
5. **无持久化模式的幂等键不校验 payload**：现保存规范化请求指纹，相同 token 不同 payload 返回 409。
6. **无上下文 controller 未映射幂等冲突**：现统一映射为 409。
7. **全局异常处理把受控 4xx/409 转为 500**：现专门处理 `ResponseStatusException`，保留状态并只公开稳定安全错误码。
8. **协议 DTO 正则过早拒绝未知版本**：现只校验 `stp-vN` 语法，是否受支持由领域策略返回明确 409。
9. **前端 mock/real E2E 固定期待 stp-v1**：已与真实请求的 stp-v2 对齐。
10. **推荐组件单测未 stub RouterLink**：已补 stub，消除无效组件告警。
11. **JAR Case smoke 使用无语义随机字符串**：路由正确返回通用能力不可用，脚本却误报 Case 无正文。现使用有效 Case 问题并附带隐私哨兵。
12. **JAR smoke 只认旧顶层 blocks**：现同时校验 legacy 顶层和 semantic-task payload。
13. **JAR smoke 跨运行复用固定 requestToken**：持久化模式会产生合法 409 冲突。现每次运行生成唯一 UUID。

## 6. 跳过项说明

后端 5 个 skipped 均由显式环境前提控制，不是对话正确性失败：

- 本地 INT8 embedding 的 4 核/4GB 性能准入基准（未提供正式模型目录/性能 lane）。
- Windows 当前权限下不支持的 3 个符号链接逃逸测试中的相应平台分支。
- 固定公开检索 benchmark（需要专门 benchmark 开关/环境）。

这些能力的功能正确性已有 codec、ONNX smoke、检索单元测试、风险门禁和 PostgreSQL 集成覆盖；性能与平台特有分支仍应在对应 CI runner 单独执行。

## 7. 交互合同增量补测与修复

针对页面实际操作中尚未成功的路径，本轮只补测此前缺失或失败的交互，不重复执行已经通过的后端全套与旧 E2E：

1. **正式建议问题点击丢失 Preset 身份**：动态建议文本现在会与当前发布快照中的 preset 做主体内精确匹配；唯一匹配时携带 `questionPresetId` 与 `contractVersion`，真实接口返回 `ANSWERED / PORTFOLIO`。
2. **回答卡追问沿用已废弃的 `context.referenceContext`**：展开章节、说明判断、查看当前状态、查看相关问题均改为发送显式 `semanticContext.activeSubjects`，真实接口不再返回 400。
3. **明确多主体比较缺少主体上下文**：比较操作现在发送全部精确项目主体；单元测试确认不会退化为单主体或裸文本路由。
4. **推荐结果展示不可执行按钮**：当前运行时若未返回 `contextHandle`/continuation context，则不再展示“换掉”“解释”“继续”按钮；若返回句柄，则仍通过 `contextReference` 发起推荐细化。
5. **推荐数量意图未落实到不同项目**：路由层虽已解析 `requestedSize`，证据选择层原先却全局只取前两条证据，可能都属于同一项目。现按请求数量优先选择不同项目的公开证据；“推荐两个”真实返回两项，“推荐一个”返回一项。
6. **单项推荐留下半列空白**：推荐网格唯一卡片现在跨满整行，不再显示无意义的灰色空列。

增量验证结果：

- 前端定向单元测试：3 个文件、157 项全部通过。
- Vue/TypeScript 类型检查：通过。
- 真实前后端 Chromium E2E：4 条新增或收紧路径全部通过，覆盖正式 preset、4 类回答追问、推荐上下文能力门控、推荐数量合同和单项卡片布局。
