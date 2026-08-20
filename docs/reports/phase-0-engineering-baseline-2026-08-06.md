# 阶段 0 工程基线报告（2026-08-06）
<!-- DOCUMENT_STATUS: HISTORICAL -->

> **Git 身份：** `13b27ffb324f2136daa046f070c88a704c3f9d19`（`master`）
> **工作区：** 仅包含本阶段 0A 改动与用户既有文档改动（见下），无未跟踪源码
> **运行环境：** Windows 11、Java 21.0.11（Temurin）、Maven 3.9.9、Node 22.21.1、Playwright 1.53

## 1. 门禁结果汇总

| # | 命令 | 结果 | 通过/跳过说明 |
|---|---|---|---|
| 1 | `mvn.cmd -f backend/pom.xml test` | ✅ 退出码 0 | 852 通过、0 失败、17 跳过（既有 Docker/Testcontainers 与可选环境场景，见第 3 节） |
| 2 | `npm.cmd --prefix frontend test -- --run` | ✅ 退出码 0 | 全部前端单元/组件测试通过（含本阶段新增参数化诊断与语义断言） |
| 3 | `npm.cmd --prefix frontend run check` | ✅ 退出码 0 | `vue-tsc -b` 类型检查通过 |
| 4 | `npm.cmd --prefix frontend run lint` | ✅ 退出码 0 | lint 通过 |
| 5 | `npm.cmd --prefix frontend run build` | ✅ 退出码 0 | 生产构建成功（`dist/` 生成） |
| 6 | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1 -Path backend/src` | ✅ 退出码 0 | 全部架构边界规则通过 |
| 7 | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path backend/src/main` | ✅ 退出码 0 | 472 个文件扫描通过，0 敏感项 |
| 8 | `npm.cmd --prefix frontend run test:e2e` | ✅ 62/62 | 桌面 `chromium` 31 + 移动 `mobile-chromium` 31，全部通过（基线为 42/62） |

运行时间：后端测试约 3 分钟、前端单测约 55 秒、E2E 约 45 秒（本机）。

## 2. 0A 改动清单（相对 `13b27ff`）

- `frontend/src/features/portfolio/api/portfolioApi.test.ts`：新增"单失败单发布"参数化用例（429/503/404/取消/网络失败 → 恰好 1 次 report + 封闭 payload）。
- `frontend/e2e/support/publicApiMocks.ts`：`DiagnosticsCapture.eventsNamed` 按事件名过滤；`installAnswerScenarioMock` 的 delay 支持 abort 感知（`waitForAbortOrTimeout`）。
- `frontend/e2e/portfolio.spec.ts`：诊断断言按 `eventName` 计数（不再假设"每页仅 1 事件"）；取消场景验证 UI 契约与"无 failed 诊断"（Playwright 挂起 handler 时页面 fetch abort 不传播，cancelled 发布由单测锁定）；四个回答语义场景断言更新为当前用户可见标签（`规则识别`、`已验证`、`需要补充信息`、`BOUNDARY · TEMPLATE`、`EVIDENCE_COMPOSITION`、`拒答`）。
- `frontend/src/features/agent/model/answerLabels.ts`：`answerVerificationTag` 对 `INSUFFICIENT` 返回 `null`（证据不足仅由状态标签表达，验证标签不显示）。
- `frontend/src/features/agent/model/answerLabels.test.ts`：新增四条权威语义断言。
- `docs/reports/phase-0-e2e-triage-2026-08-06.md`：20 个 E2E 失败的三类定性（6 诊断=TEST_ISOLATION、4 语义=ASSERTION_DRIFT、无 PRODUCT_REGRESSION）。

产品行为零回归：诊断发布点、DiagnosticTransport 语义、回答标签语义（除 INSUFFICIENT 验证标签为 null 外）均未改变；E2E 无删除、无 skip 增加、无 retries 增加。

## 3. 跳过项与原因

- 后端 17 个跳过：Testcontainers/Docker 不可用场景（`Could not find a valid Docker environment`）与按运行条件设计的条件跳过，均为既有行为，非本阶段引入。
- 真实 Provider、真实 PostgreSQL/pgvector、ONNX 容量验收：未运行（无显式授权/默认关闭），本报告不声称其通过。
- `verify-release.ps1` 完整发布门禁：本阶段只冻结七类工程门禁；完整发布验证在 0B-0D 完成后再执行。

## 4. 权威契约锁定

- 诊断：一个请求失败至多一个 closed 诊断；取消不产生失败回答；上传失败不重试；响应前只有 client correlation。
- 回答语义：UI 不把检索来源写成"已核验"；`FALLBACK` 不伪装成 `MODEL`；`NOT_SUPPORTED/BOUNDARY/REJECTED` 不显示验证标签；首页 handoff 不写 URL 或存储。
