# Live Provider 探针与结构化主体自由问题路由纠偏设计
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

**日期：** 2026-08-05  
**状态：** 已完成诊断与方案确认，待实施  
**适用范围：** 本地启动探针、打包发布 Live Provider 门禁、结构化 Project/Case 主体校验、自由问题任务分类、Bundle 相关性检索与对应测试  
**替代范围：** 本文仅替代 `2026-08-05-preset-contract-end-to-end-closure-design.md` 中“结构化主体成功后直接生成 `FACT_LOOKUP`”以及其运行时顺序；Preset Contract 的治理、版本、证据和 fail-closed 契约保持不变

## 1. 背景

提交 `21dba32` 引入 `StructuredSubjectTaskResolver` 后，任何携带且唯一命中当前公开快照的 `projectSlug` 或 `caseSlug`，都会在模型任务分类前被转换为：

```text
FACT_LOOKUP + subjectId + intentSource=RULE
```

这一变化解决了结构化主体被忽略、未知主体可能进入 GENERAL 的问题，但同时产生两类未被原验收覆盖的耦合：

1. `scripts/start-local.ps1` 的 Live Provider 探针固定携带 `projectSlug=sql-audit`，因此必然被结构化主体路由截获；`assert-live-provider-response.ps1` 又硬性要求 `intentSource=MODEL`，导致真实 Provider 可用时仍输出 `AI_DEGRADED:PROVIDER_RESPONSE_INVALID`。
2. Project/Case 页面和页面内存 handoff 会给自由问题携带 slug。结构化主体被同时当作“合法主体”和“已经完成意图分类”的信号，模型启用时也无法解释通用、跨语言或弱关键词问题。

`scripts/run-jar-e2e.ps1 -RequireLiveProvider` 还有同源问题：它复用带 `caseSlug` 的 Case 隐私 smoke 响应执行 Live Provider 断言，因此发布门禁也无法证明 Provider 已被调用。

当前脚本测试没有发现漂移，因为 `BACKEND_MODEL` fake server 不解析请求体，只要收到 `/api/v2/answers` 就静态返回 `MODEL + VERIFIED`。

## 2. 已确认事实

标准探针请求在当前代码上的真实响应为：

```text
resolution=NOT_SUPPORTED
intentSource=RULE
constructionMode=EVIDENCE_COMPOSITION
evidenceState=INSUFFICIENT
degraded=false
blocks=1
```

将该响应交给现有 Live Provider 断言器会得到退出码 `1`。该复现不需要启用模型，也不会发出 Provider 请求。

另一方面，默认 `portfolio.retrieval.profile=DISABLED` 并不表示完全不检索。运行时会因本地 Embedding 不可用而进入 `KEYWORD_FALLBACK`；关键词证据充分的结构化 Case 问题仍能返回 `ANSWERED + RULE + VERIFIED`。因此本次纠偏不得把所有 `INSUFFICIENT` 误判为检索基础设施故障。

## 3. 目标

1. Live Provider 探针只验证 Provider 链路，不依赖 Project、Case、Preset、Reference 或 Recommendation 的产品路由。
2. `start-local.ps1` 和 `run-jar-e2e.ps1 -RequireLiveProvider` 使用同一份探针请求契约，避免再次漂移。
3. 探针失败必须区分 Provider 故障、响应结构非法和探针被非模型路由截获，不能再把全部断言失败映射为 `PROVIDER_RESPONSE_INVALID`。
4. 结构化 slug 只负责主体合法性和检索范围，不再自动等价于 `FACT_LOOKUP` 意图。
5. 模型能力允许时，普通结构化主体自由问题仍进入模型任务分类；分类结果执行时必须保留已验证的 `subjectId`。
6. 模型能力关闭时，已验证主体仍可走确定性的 `FACT_LOOKUP` fallback，保持默认部署可用和 fail-closed。
7. 未知、重复或互斥错误的结构化主体必须在确定性规则、模型和检索之前失败关闭。
8. Preset Contract、Reference Context、Evidence Policy、公开内容边界和默认开关保持不变。

## 4. 非目标

- 不新增 Provider，不修改 DeepSeek/GLM Registry、密钥名称、超时或 token 上限。
- 不把本地检索默认值从 `DISABLED` 改为 `HYBRID`。
- 不为任意自由问题开放 `PRESET_CONTRACT` 或 `exactPassages`。
- 不新增公开诊断 HTTP 端点。
- 不修改 `/api/v2/answers` 请求或响应 JSON Schema。
- 不让模型决定 Claim/Evidence 是否可发布，也不让模型移除调用方给定的主体约束。
- 不以本次纠偏为由重构无关的回答装配、会话、日志或发布治理模块。

## 5. 方案比较

### 5.1 方案 A：只从启动探针删除 slug

优点是改动最小，能立即恢复 `AI_CONNECTED`。缺点是 `RequireLiveProvider` 仍可能复用错误响应，结构化主体自由问题仍跳过模型，fake server 仍无法发现下一次契约漂移。

**结论：** 仅可作为紧急热修，不作为完整方案。

### 5.2 方案 B：所有结构化主体请求直接使用 Contract/exactPassages

优点是无需向量检索，常见概览问题容易得到稳定证据。缺点是任意自由问题都会绕过相关性判断；用户询问无关内容时也可能收到整包主体事实，违反“结构化主体只缩小候选、不能绕过相关性和 Evidence Policy”的既有约束。

**结论：** 拒绝。

### 5.3 方案 C：探针独立化，主体校验与意图分类解耦

探针使用无主体、无 Preset 的固定 canary；结构化主体先解析成不可变 scope，再由确定性规则或模型决定任务类型；执行前把 scope 附加到任务。模型关闭时才使用确定性的主体 `FACT_LOOKUP` fallback。

优点是职责清晰、保留安全边界、兼容默认关闭模型的部署，并能真正验证 Provider。代价是需要同时修改 PowerShell 编排、Java 路由和回归测试。

**结论：** 采用。

## 6. 核心设计原则

### 6.1 主体是 Scope，不是 Intent

`projectSlug` 和 `caseSlug` 只回答两个问题：

1. 请求引用的公开主体是否合法且唯一？
2. 后续检索允许访问哪个主体？

它们不能单独决定用户是在查事实、比较、推荐、追问还是询问非作品集内容。

### 6.2 探针是运维契约，不是产品页面回放

Live Provider 探针必须刻意避开所有可能优先于模型的产品入口：

- 不带 `projectSlug` 或 `caseSlug`；
- 不带 `questionPresetId` 或 `contractVersion`；
- 不带 `referenceContext` 或 `recommendationContext`；
- 问题不命中受控确定性规则；
- 固定使用当前公开 Bundle 能形成 VERIFIED 证据的、明确指向作品集事实的问题。

探针问题固定为：

```text
Please introduce the SQL audit and troubleshooting project in detail.
```

它在无 slug 时必须经过模型分类；若未来新增 canonical/alias 或规则使其绕过模型，契约测试必须先失败，迫使维护者同步更新 canary。

### 6.3 fail-closed 不等于跳过语义理解

模型分类只能产生受控 `PortfolioTaskClassification`。已验证 `subjectId` 在执行前由服务端附加，模型没有权限扩大或清除范围。检索和 Evidence Gate 仍可返回 `INSUFFICIENT`。

### 6.4 Contract 不作为自由问题兜底

只有以下请求能进入 `PRESET_CONTRACT`：

- 有效的 `questionPresetId + contractVersion`；
- 当前 `PortfolioPresetResolver` 已明确允许的 Active canonical/alias 命中。

普通自由问题即使语义类似“介绍项目”，也不得自动借用某个 Contract 的 Required Claims，除非后续另行扩展并审批受控 alias。

## 7. 运行时路由顺序

新的固定顺序为：

```text
Reference Context
→ Preset ID + Contract Version
→ Active canonical/alias
→ Structured Subject validation
→ Controlled deterministic rule
→ Model task classification（仅 Provider gate 允许时）
→ Scoped deterministic FACT_LOOKUP fallback（仅有合法主体且模型不允许时）
→ Non-portfolio / general capability
```

### 7.1 Reference 与 Preset

Reference 和 Preset 的现有优先级及失败语义不变。Contract 失败禁止降级到模型或普通检索。

### 7.2 Structured Subject validation

解析结果继续使用三态：

```text
NONE
MATCHED(subjectId)
INVALID
```

`MATCHED` 不再携带预构造的 `PortfolioTask`。`INVALID` 立即映射为：

```text
resolution=INVALID_INPUT
noticeCode=STRUCTURED_SUBJECT_INVALID
intentSource=RULE
```

校验必须发生在普通确定性规则之前，关闭当前“未知 slug 命中确定性短语后绕过主体校验”的缺口。

### 7.3 确定性规则

问题命中受控规则时，沿用规则生成的 mode、conditions、recommendationContext 和 refinement；若存在 `MATCHED(subjectId)`，执行前附加该 scope，`intentSource=RULE`。

### 7.4 模型分类

Provider gate 允许时，无论是否存在结构化主体，未命中前序路径的自由问题都调用 `PortfolioTaskResolver.route(..., true)`。

- 模型判定为非作品集或 boundary intent：沿用现有非作品集处理。
- 模型分类失败、低置信度或需要澄清：沿用现有 clarification 语义。
- 模型返回任务：若存在主体 scope，服务端附加 `subjectId` 后执行，`intentSource=MODEL`。

### 7.5 模型关闭 fallback

Provider gate 不允许且存在合法主体时，构造：

```text
mode=FACT_LOOKUP
confidence=1.0
conditions=empty
subjectId=<validated stable id>
intentSource=RULE
```

该 fallback 保留默认关闭模型时的 Project/Case 页面能力，但仍经过 `SUBJECT_SCOPED_RELEVANCE` 和 Evidence Gate。

无主体且模型不允许时，沿用 `NOT_PORTFOLIO`。

## 8. 检索语义

本次不修改 `PortfolioRetrievalStrategy`：

- 带 `subjectId` 的普通事实任务仍使用 `SUBJECT_SCOPED_RELEVANCE`；
- Contract 仍使用 `PRESET_CONTRACT`；
- Reference 仍使用 `REFERENCE_SCOPED`；
- `exactPassages` 仍只服务 Contract、Reference 和 Context Validation。

`DISABLED` 的公开说明统一为：

```text
本地向量查询关闭；运行时允许关键词 fallback；最终仍由 Grounding Gate 判断 SUFFICIENT/INSUFFICIENT。
```

不得把 `RETRIEVAL_EMBEDDING_DISABLED` 自动转换为 API `degraded=true`，也不得把 `INSUFFICIENT` 解释为 Provider 故障。

## 9. Live Provider 探针模块

新增 `scripts/provider-probe/invoke-live-provider-probe.ps1`，成为两个调用方的唯一探针入口。

输入：

```text
BackendBaseUrl: string
ExpectedContentVersion: string
TimeoutSeconds: int
FailOnDegraded: switch
```

行为：

1. 生成随机 UUID `turnId` 和 `requestToken`。
2. 构造第 6.2 节定义的无主体请求。
3. 调用 `/api/v2/answers`。
4. 将响应写入系统临时目录中的随机文件。
5. 调用 `assert-live-provider-response.ps1`。
6. 输出一行安全状态，不输出问题、Key、响应正文或本地敏感路径。
7. 在 `finally` 中删除临时响应。

成功输出：

```text
LIVE_PROVIDER_CONNECTED
```

失败输出：

```text
LIVE_PROVIDER_DEGRADED:<CATEGORY>
```

当指定 `-FailOnDegraded` 时，失败退出 `1`；否则退出 `0`，由 `start-local.ps1` 保持服务运行并展示降级状态。

## 10. 类型化断言失败

`assert-live-provider-response.ps1` 保持成功输出不含秘密；失败时输出以下封闭代码之一：

| 代码 | 含义 |
|---|---|
| `LIVE_PROVIDER_CONFIG_INVALID` | 四个批准开关、Provider 或对应 Key 不合法 |
| `LIVE_PROVIDER_RESPONSE_UNREADABLE` | 文件缺失或 JSON 无法解析 |
| `LIVE_PROVIDER_CONTENT_VERSION_MISMATCH` | 响应版本不一致 |
| `LIVE_PROVIDER_REPORTED_DEGRADED` | API 明确返回 `degraded=true` |
| `LIVE_PROVIDER_ROUTE_BYPASSED` | `intentSource` 不是 `MODEL` |
| `LIVE_PROVIDER_CONSTRUCTION_INVALID` | 构造方式不是 `EVIDENCE_COMPOSITION` |
| `LIVE_PROVIDER_EVIDENCE_UNVERIFIED` | 证据状态不是 `VERIFIED` |
| `LIVE_PROVIDER_RESOLUTION_INVALID` | 结果不是 `ANSWERED` |
| `LIVE_PROVIDER_BLOCKS_MISSING` | 没有回答 block |

断言器仍统一退出 `1`，调用方通过安全代码分类。若响应含已有 Provider `noticeCode`，探针模块优先映射为现有 Provider 分类；否则：

- `LIVE_PROVIDER_ROUTE_BYPASSED` → `PROBE_ROUTE_BYPASSED`
- 其他结构断言失败 → `PROVIDER_RESPONSE_INVALID`
- HTTP/连接失败 → `PROVIDER_UNAVAILABLE`

因此 `start-local.ps1` 可输出：

```text
AI_DEGRADED:PROBE_ROUTE_BYPASSED
```

而不再谎报 Provider 响应非法。

## 11. 调用方改造

### 11.1 start-local.ps1

- 删除内嵌的探针请求体和重复 HTTP/临时文件逻辑。
- 在现有 `Set-TemporaryProcessEnvironment` 范围内调用共享探针脚本。
- `LIVE_PROVIDER_CONNECTED` 映射为现有 `AI_CONNECTED provider=...`。
- `LIVE_PROVIDER_DEGRADED:<CATEGORY>` 映射为现有 `AI_DEGRADED:<CATEGORY>`。
- 不改变前后端生命周期、日志跟随或 `-ExitAfterProbe` 行为。

### 11.2 run-jar-e2e.ps1

- Case smoke 继续验证 Case API、隐私 sentinel、主体边界和 blocks，不再承担 Provider 证明。
- `-RequireLiveProvider` 时额外调用共享探针脚本并传 `-FailOnDegraded`。
- Provider 探针失败时终止发布门禁；Case smoke 成功不能替代 Provider 探针成功。

## 12. 测试设计

### 12.1 PowerShell 单元与契约测试

fake backend 必须读取 JSON 请求体并按请求语义响应：

- 无 slug、无 Preset、固定 canary → `MODEL + VERIFIED`；
- 携带 project/case slug → `RULE + INSUFFICIENT`；
- Provider notice fixture → 对应安全降级分类；
- 非 MODEL 且无 Provider notice → `PROBE_ROUTE_BYPASSED`。

这样旧版探针请求会使测试变 RED，而不是继续被静态 fixture 掩盖。

### 12.2 Java 路由测试矩阵

| 场景 | Provider | 预期 |
|---|---:|---|
| 已知 projectSlug，普通自由问题 | 开 | 调 classifier，`MODEL`，保留 project stable ID |
| 已知 caseSlug，普通自由问题 | 开 | 调 classifier，`MODEL`，保留 case stable ID |
| 已知 slug，确定性规则 | 任意 | 不调 classifier，`RULE`，保留主体范围 |
| 已知 slug，普通自由问题 | 关 | 不调 classifier，`RULE` scoped fallback |
| 未知 slug，确定性短语 | 任意 | `INVALID_INPUT`，不调 classifier/retriever |
| 已知 slug，模型判非作品集 | 开 | `NOT_PORTFOLIO`，不检索 |
| 无 slug，canary | 开 | 进入模型分类 |

### 12.3 Bundle 集成测试

- 现有 Case `KEYWORD_FALLBACK + SUFFICIENT` 场景必须继续通过。
- Preset Contract 的 `PRESET + VERIFIED` 场景必须继续通过。
- 结构化主体模型关闭 fallback 继续返回受主体约束的公开证据。
- 不以普通 CI 发起真实 Provider 请求。

### 12.4 Live 验收

只有显式提供仓库外 Secret 文件或运行 `-RequireLiveProvider` 时才执行真实 Provider 验收：

```text
start-local → AI_CONNECTED
packaged JAR -RequireLiveProvider → LIVE_PROVIDER_CONNECTED
```

额外手工验证三个项目页自由问题时，应检查：

```text
intentSource=MODEL
subject scope 未扩大
evidenceState=VERIFIED 或按真实证据 fail-closed
degraded=false（无 Provider/基础设施故障时）
```

`VERIFIED` 不是对所有自由问题的无条件承诺；本次承诺是恢复模型分类机会并保持主体/Evidence 边界。

## 13. 兼容性与迁移

- API JSON 无变化，前端无需迁移。
- Active Contract、Contract Version 和 Bundle 内容无需重发。
- `intentSource` 会发生有意变化：模型启用时，普通结构化主体自由问题由 `RULE` 恢复为 `MODEL`。
- 模型关闭时保持 `RULE` fallback，默认部署行为仍确定性且不触发外部 Provider。
- 历史设计文档不回写；在当前实现状态和演进日志中记录新顺序及替代关系。

## 14. 可观测性与隐私

- 不记录访客问题、请求正文、响应正文、Key 或 Authorization Header。
- 探针日志只允许 Provider 名称、contentVersion、封闭状态码和 blocks 数量。
- `PROBE_ROUTE_BYPASSED` 属于契约漂移，不应计入 Provider 可用性故障。
- 临时响应文件必须位于系统临时目录，使用随机名，并在所有路径的 `finally` 中删除。

## 15. 风险与控制

- **模型把页面内问题判为非作品集：** 允许返回现有 GENERAL/边界语义；slug 不能强迫无关问题成为作品集事实。
- **模型试图扩大主体范围：** `subjectId` 由服务端在分类后附加，分类 DTO 不接收主体写权限。
- **模型关闭导致页面能力下降：** 保留 scoped `FACT_LOOKUP` fallback。
- **通用问法仍因证据不足失败：** 保留 fail-closed；通过检索基准或受控 Preset alias 单独演进，不在本次绕过相关性。
- **探针问题未来命中 Preset/规则：** request-aware fake 和 Java canary 路由测试先失败。
- **两个编排入口再次漂移：** 两者只调用同一个共享探针脚本。
- **错误码泄漏内部细节：** 只输出封闭代码，不透传异常、路径或响应正文。

## 16. 完成标准

本设计完成实施后必须同时满足：

1. 标准本地启动在真实 Provider 成功时输出 `AI_CONNECTED`。
2. `verify-release.ps1 -RequireLiveProvider` 使用独立无主体 canary，并能通过真实 Provider 门禁。
3. 任一探针重新携带 slug 时，PowerShell 契约测试稳定失败。
4. 非 MODEL 路由不再映射为 `PROVIDER_RESPONSE_INVALID`，而是 `PROBE_ROUTE_BYPASSED`。
5. 已知 slug 在模型启用、未命中规则时调用 classifier，并以 `MODEL` 执行受主体约束的任务。
6. 已知 slug 在模型关闭时继续走 `RULE` scoped fallback。
7. 未知 slug 即使命中确定性短语也返回 `INVALID_INPUT`，且不调用 classifier/retriever。
8. Preset、Reference、Case smoke、隐私检查和默认 fail-closed 测试继续通过。
9. `DISABLED` 的文档表述明确为向量关闭、关键词 fallback 可用。
10. 全量后端、前端、脚本、打包和隐私门禁通过；真实 Provider 仅在显式授权路径执行。
