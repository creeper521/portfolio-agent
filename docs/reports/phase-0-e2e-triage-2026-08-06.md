# 阶段 0 E2E 失败定性报告（2026-08-06）
<!-- DOCUMENT_STATUS: HISTORICAL -->

> **基线：** `git rev-parse HEAD = 13b27ff`（复现时）；桌面 `chromium` 与移动 `mobile-chromium` 双项目
> **结果：** 62 项中 42 项通过、20 项失败；20 个失败 = 10 个逻辑场景 × 2 项目，桌面与移动错误逐字一致（与布局/平台无关）
> **结论：** 无 `PRODUCT_REGRESSION`；6 个诊断场景为 `TEST_ISOLATION`，4 个标签/首页场景为 `ASSERTION_DRIFT`

## 1. 复现命令

```powershell
npm.cmd --prefix frontend run test:e2e -- --reporter=line
```

复现结果：`42 passed`，20 failed（`frontend/test-results/.last-run.json` 中的失败 ID 与下述 10 场景 × 2 完全对应）。

## 2. 静默跳过扫描

```powershell
rg -n "test\.(skip|fixme)|describe\.(skip|fixme)|test\.only|describe\.only" frontend/e2e
```

仅 3 处既有条件跳过（`portfolio.spec.ts:66/706/730`），均为 `PLAYWRIGHT_REAL_API === '1'` 时的真实 API 模式分支，属于设计内条件跳过；无静默 `test.skip/fixme/only`。

## 3. 诊断六场景（TEST_ISOLATION）

权威契约：一个请求失败至多一个封闭诊断；取消不产生失败回答；上传失败不重试；响应前只有 client correlation；响应后可同时有 server correlation。

| 测试（spec 行号） | 桌面/移动错误摘要 | trace |
|---|---|---|
| 429 renders a countdown and uploads only a closed correlated diagnostic（68） | `expect.poll(() => diagnostics.events.length).toBe(1)` → `Expected: 1, Received: 3` | `test-results\portfolio-browser-diagnost-47f11-losed-correlated-diagnostic-{chromium,mobile-chromium}\trace.zip` |
| 503 timeout offers retry and preserves the returned request correlation（92） | 同上 `Received: 3` | `-fedc4-eturned-request-correlation-*` |
| PROJECT_NOT_FOUND offers safe navigation without exposing the server body（113） | 同上 `Received: 3` | `-f0f2f-ut-exposing-the-server-body-*` |
| caller cancellation appends no failure answer（135） | 同上 `Received: 3` | `-0c82f-n-appends-no-failure-answer-*` |
| one slow answer emits one diagnostic and an upload failure stays invisible without retry（154） | `attempts` → `Expected: 1, Received: 2` | `-845c2-ays-invisible-without-retry-*` |
| a pre-response network failure reports only client correlation（174） | `Received: 3` | `-f7144-rts-only-client-correlation-*` |

- **根因**：产品每次失败仅上报 1 个 closed 诊断且无敏感字段（单元测试 `portfolioApi.test.ts` 已佐证）；测试捕获设施 `publicApiMocks.ts` 的共享 capture 累积了页面全生命周期合法事件——`frontend.application.started`（`frontendDiagnostics.ts:43-45`）、`frontend.content.load.completed`（`usePublicContent.ts:65-68`）、失败/取消事件（`portfolioApi.ts:277-292`）以及 slow 场景的 `frontend.agent.request.completed`（`AgentWorkspace.vue:426-440`）。旧断言"每页仅 1 事件"已不成立。
- **允许修改**：`frontend/e2e/support/publicApiMocks.ts`（捕获设施按 `eventName` 过滤或按上传批次隔离/reset）+ `frontend/e2e/portfolio.spec.ts` 诊断断言（按 `eventName` 计数，slow 场景断言事件而非 attempts）。

## 4. 回答语义四场景（ASSERTION_DRIFT）

权威契约：UI 不把"检索来源"写成"已核验"；`FALLBACK` 不伪装成 `MODEL`；`NOT_SUPPORTED/REJECTED` 不显示已核验标签；首页 handoff 不写 URL 或存储。权威字段为 `constructionMode`、`intentSource`、`evidenceState`。

| 测试（spec 行号） | 桌面/移动错误摘要 | trace |
|---|---|---|
| home preserves the four-layer experience and hands a role question to Agent（212） | `Expected substring: "DETERMINISTIC"` 未命中；实际 `"预设问题 ANSWERED · EVIDENCE_COMPOSITION … 已验证回答"`；249 行 `'已核验'` 同样过时 | `-b3963-ds-a-role-question-to-Agent-*` |
| Agent renders unsupported and rejected dimensions without a verified label（652） | `Expected: "当前公开证据不足"` 未命中；实际 `"需要补充信息 确定性模板 BOUNDARY · TEMPLATE"`；658-670 行 `'NOT_SUPPORTED'`、`'DETERMINISTIC'` 过时 | `-4b1bd-ns-without-a-verified-label-*` |
| Agent distinguishes retrieval provenance from verification（705） | `Expected: "资料检索"` 未命中；实际 `"规则识别 已验证证据"`；719-723 行 `'已核验'/'部分核验'` 过时 | `-3dc4e-rovenance-from-verification-*` |
| Agent renders MODEL and whole-answer FALLBACK as distinct generation modes（727） | `Expected: "已核验"` 未命中；实际 `"已验证回答 已验证证据 预设问题 基于证据表达 ANSWERED · MODEL_GROUNDED"`；775 行 `'FALLBACK'` 过时（techTail 只渲染 `ANSWERED · EVIDENCE_COMPOSITION`） | `-53ec7-s-distinct-generation-modes-*` |

- **根因**：`answerLabels.ts` 已采用新语义标签（`规则识别`/`已验证证据`/`已验证回答`/`需要补充信息`/`基于证据表达`/`拒答`，行 20-22/27/49/56/63/72-75），`mapAnswerResponse.ts:93-101` 按 `evidenceState` 渲染；mock 场景数据（`publicApiMocks.ts:53/65/66`）与 E2E 断言仍使用旧枚举正文（`DETERMINISTIC`、`NOT_SUPPORTED`、`已核验`、`资料检索`、`FALLBACK`）。这正是评审项 Q-05「回答状态、生成模式和验证文案存在新旧契约漂移」的 E2E 面。
- **允许修改**：`frontend/e2e/portfolio.spec.ts` 断言（改为当前用户可见标签与 `data-verification`）；仅在产品证据确需时修改 `mapAnswerResponse.ts`/`answerLabels.ts`（本批证据表明无需修改产品代码）。

## 5. 分类汇总

| 分类 | 场景 | 修复面 |
|---|---|---|
| TEST_ISOLATION | 诊断 6 题（429/503/404/取消/慢回答/网络失败） | `publicApiMocks.ts` 捕获设施 + `portfolio.spec.ts` 诊断断言 |
| ASSERTION_DRIFT | 首页 handoff、unsupported/rejected、provenance、generation modes | `portfolio.spec.ts` 断言（+ 语义单元测试锁定） |
| PRODUCT_REGRESSION | 无 | — |

## 6. 权威契约引用

- 诊断契约：`frontend/src/shared/diagnostics/diagnosticTransport.ts`（失败分支 best-effort、不重试、不持久化）、`frontend/src/features/portfolio/api/portfolioApi.ts`（唯一发布点）
- 回答标签契约：`frontend/src/features/agent/model/answerLabels.ts`、`mapAnswerResponse.ts`
- 首页契约：四层体验 + 内存内 handoff（`frontend/e2e/portfolio.spec.ts:212` 场景）
