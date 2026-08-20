# Agent 稳定化最终验证报告
<!-- DOCUMENT_STATUS: HISTORICAL -->

> 验证日期：2026-08-20
> 范围：`2026-08-19-agent-stabilization-and-repository-governance` 设计与实施计划
> 结论：架构与本地发布硬门通过；真实 Provider LIVE 门未获授权，未执行。
> 定位：一次性验证证据；当前事实以 docs/08 与架构状态 JSON 为准。

## 最终结果

- Agent 生产运行权威统一为 `turn`；`answer` 与 `selection` 生产包已物理删除；
- Bundle 与 PostgreSQL 直接实现最终 Portfolio Port，不保留旧检索桥、旧 Eval 业务复制或兼容 Bean；
- PostgreSQL Agent State、absolute deadline、准入、澄清恢复、回放、取消和密钥轮换边界均由当前合同负责；
- 前端只消费无版本 Agent 资源与根级 `PublicAgentTurn`；内容只读模式失败关闭；
- 架构状态恢复为 `COMPLETE`。

## 新鲜证据

| 门禁 | 结果 |
|---|---|
| Code Quality self-test / current scan | PASS |
| Architecture self-test / current scan | PASS |
| Documentation self-test / 12 份当前权威文档 | PASS |
| Privacy self-test / backend scan | PASS，492 files，0 archives |
| Frontend Vitest | PASS，449 tests |
| Frontend `vue-tsc` / Vite build | PASS |
| Backend `mvn clean package` | PASS，828 tests，0 failures/errors，4 skipped；Testcontainers PostgreSQL 执行通过 |
| Eval 定向套件 | PASS，125 tests |
| Body-stall transport 稳定复跑 | PASS，连续 3 轮 |
| PostgreSQL migration / verify | PASS，public=5，governance=1，context=3 |
| PostgreSQL packaged Browser E2E | PASS，desktop/mobile 14 passed，2 lane-isolation skipped |
| Packaged active cancel | PASS，DELETE 204、连接关闭、无迟到 Turn |

后端全量在 Docker 就绪后执行了 Testcontainers PostgreSQL 集成测试；4 个跳过项均为显式条件测试。随后又使用独立本地验证库完成 migration/verify 与 packaged-JAR 矩阵。验证使用仓库外临时 EnvFile 和临时密钥，文件在 `finally` 中删除，未改写用户 `.env.postgres.local`。

## 零旧表面证据

- `backend/src/main/java/com/portfolio/agent/answer`：不存在；
- `backend/src/main/java/com/portfolio/agent/selection`：不存在；
- 生产源码对 `com.portfolio.agent.answer.*` / `com.portfolio.agent.selection.*` 的完整 FQCN 扫描：0；
- 旧 `/api/v2/answers` 与 STP 合同不在当前运行脚本中；仅检查器的负例夹具保留字符串样本；
- 旧 model-led packaged canary 已删除。

## 未执行边界

真实 Provider LIVE 门需要单独授权。本轮没有发出真实模型请求，因此不声明真实 Provider 的回答质量、延迟分布或生产可用性。确定性 Provider body-stall、取消、deadline、replay 和 PostgreSQL 联合路径已经通过。

## 本地数据库说明

为避免删除已有本地 Context 数据，最终验证创建并保留独立测试库 `portfolio_context_final_verify` 与专用应用角色。该库只用于本地验证，不属于生产数据；运行时密钥未写入仓库。
