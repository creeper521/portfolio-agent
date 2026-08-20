# Agent Answer Composition 阶段一收口报告（2026-08-10）
<!-- DOCUMENT_STATUS: HISTORICAL -->

## 结论

阶段一核心运行链路已完成修复并通过单元、后端全量测试和 Mock E2E；正式发布门禁暂不判定为完成。原因是本机没有 Docker，PostgreSQL Testcontainers 集成测试只能跳过；全量离线评测仍受既有 Phase 0 数据集与当前 deterministic routing 的不匹配影响而失败。

## 已完成

- 统一答案 section 顺序、标题和 gap 文案来源，消除重复分类定义。
- Focused 请求按精确 Claim Category 过滤，缺少目标类别时返回 `NOT_SUPPORTED`，不再用相邻类别冒充。
- STATUS/BOUNDARY 缺口显式输出，Boundary 事实纳入独立预算；`NOT_SUPPORTED` 不泄露无关证据。
- degraded 元数据从 intelligence 结果传递到最终答案。
- 不可变答案对象增加集合防御性复制和值语义测试。
- 前端 `blocks: []` 作为显式 V2 权威值，不回退到 legacy sections。
- 新增推荐 legacy 路径和 unsupported 路径的桌面/移动 Mock E2E。
- 离线 EvalExecutionEngine 按层跳过 HTTP_E2E，混合 case 不再误执行 HTTP 层。
- 新增 PostgreSQL fact-passage 查询集成测试（Docker 可用时执行）。

## 验证证据

- 后端 Maven 全量：1014 tests，0 failures/errors，18 skipped（Docker 集成测试）。
- 前端 Mock Playwright：72 passed。
- Eval manifest validate：通过。
- code-quality、architecture、production privacy：通过。
- 离线 Eval：`FAIL`。报告位于 `output/evaluation/phase1-final-offline-2/report.md`，主要表现为既有 smoke/routing 案例与 deterministic portfolio intent 不匹配，不能作为阶段一正式 PASS 证据。

## 阻塞项与后续计划

1. 在具备 Docker/pgvector 的环境重新运行 PostgreSQL 集成测试，并确认真实数据库检索链路。
2. 单独建立与阶段一答案编排契约对齐的 eval manifest/profile，避免把 Phase 0 全量 smoke/routing 失败混入本阶段门禁。
3. 在该 profile 上补齐 STATUS、BOUNDARY、Focused category、degraded 和 evidence budget 的 blocking graders，取得离线 PASS 后再更新路线图为“阶段一完成”。
