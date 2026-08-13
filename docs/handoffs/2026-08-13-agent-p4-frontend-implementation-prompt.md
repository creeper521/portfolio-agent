# P4 前端 Agent 开发提示词

> 仅在用户书面批准 P4 Spec 后使用。

请在仓库 `D:\code\agent` 中实施 Agent P4 前端契约与交互适配。开始前完整阅读：

1. `AGENTS.md`
2. `docs/handoffs/2026-08-13-agent-p4-frontend-contract-handoff.md`
3. `docs/superpowers/specs/2026-08-13-model-grounded-answer-design.md` 的第 11、14、16、18 节
4. 当前 `frontend/src/features/agent/` 的 types、mapper、ConversationThread、EvidenceDesk、ExecutionSnapshot 与 E2E

前提：P0–P3 前后端已完整实现，P4 后端公共语义已在上述文档冻结。不要重新设计后端模型表达、Provider、Validator、fallback、Evidence 或任务状态；不要修改后端业务代码。若后端实际 DTO 与 handoff 不一致，只报告差异并提出契约修订建议，等待后端统一，不能在前端猜测异常字符串或建立第二套语义。

实现目标：

- 扩展 `GenerationMode` 支持 `MIXED`。
- 扩展 `AnswerConstructionMode` 支持 `MIXED_COMPOSITION`。
- 为 completed task 增加可选 `composition { mode: DETERMINISTIC | MODEL_GROUNDED | FALLBACK, degraded: boolean }` 的严格类型和映射。
- 非法 composition metadata 只做脱敏诊断并忽略 metadata，不能丢失已经通过既有契约校验的可信正文与 sourceReferences。
- `MODEL_GROUNDED`、`DETERMINISTIC`、`FALLBACK` 使用完全相同的章节、推荐与 Evidence Desk 组件。
- Fallback 仍是成功回答：不显示错误 Toast、重试按钮、警告卡或失败状态。
- 不展示 AI/Provider 徽标，不展示模型耗时、Validator reason 或 breaker 状态。
- ExecutionSnapshot 继续只有 P3 四阶段，不新增“调用模型/验证模型”拟真阶段。
- 不引入 Streaming、逐字动画、SSE 或新的等待流程。
- sourceReferences 继续是权威引用；不得根据 composition mode 过滤、重排或改写正文与引用。

重点文件预计包括：

- `frontend/src/features/agent/model/answerTypes.ts`
- `frontend/src/features/agent/model/semanticTurnView.ts`
- `frontend/src/features/agent/model/mapAnswerResponse.ts`
- 对应 mapper、ConversationThread、EvidenceDesk、ExecutionSnapshot、AgentWorkspace 测试
- `frontend/e2e/support/publicApiMocks.ts` 与目标 E2E

必须覆盖：

1. 单任务 MODEL_GROUNDED 正常展示章节和引用。
2. 单任务 FALLBACK 无错误 UI，Evidence 可打开。
3. 多任务 MIXED/MIXED_COMPOSITION 不触发未知 enum或内容丢失。
4. task composition 缺省时兼容。
5. 非法 composition 只丢 metadata并产生不含正文/Token/reference key 的诊断。
6. MODEL_GROUNDED 的非法 sourceReferences 沿用 P3 既有校验，不因模型模式放宽。
7. 桌面/移动无溢出、重复来源标签或 Evidence Desk 回归。
8. Context 恢复、Recommendation、Plan Confirmation、Clarification 与 ExecutionSnapshot 无回归。

先检查 dirty worktree并保留全部用户现有改动。先汇报契约落点和实施计划，再修改。完成后运行 TypeScript、单测、Lint、构建与目标桌面/移动 E2E；不要把 Mock 前端通过描述成真实 Provider 验收。
