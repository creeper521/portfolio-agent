# P4 后端开发上下文提示词

> P4 Spec 已由用户批准；按已冻结 Plan 逐任务执行。

请在仓库 `D:\code\agent` 中实施 Agent P4 后端。开始前完整阅读：

1. `AGENTS.md`
2. `docs/superpowers/specs/2026-08-13-model-grounded-answer-design.md`
3. `docs/superpowers/plans/2026-08-13-agent-p4-backend-implementation.md`
4. `docs/superpowers/specs/2026-08-11-bounded-tool-orchestration-design.md`
5. `docs/superpowers/specs/2026-08-06-agent-answer-composition-design.md`
6. `docs/13-Agent对话体验与智能编排改造路线图.md`
7. `docs/handoffs/2026-08-13-agent-p4-frontend-contract-handoff.md`

前提：P0–P3 前后端已经完整实现，且用户已经书面批准 P4 Spec。不要重新讨论或扩张设计；严格按 P4 Spec 与后端 Plan 实施。不要启用 `using-superpowers` 或其他 Superpowers 技能。先检查 dirty worktree；若 P3 仍未形成可识别基线 commit，先停止并取得用户对提交/隔离方式的授权，禁止把现有 P3、前端或无关改动混进 P4 commit。

你的范围仅是后端及后端负责的公共 DTO/契约、测试、配置、Eval、脚本和权威文档。不要实现前端组件、视觉或交互；前端由独立 Agent 按 handoff 实施。若公共契约必须调整，先更新 P4 Spec 和 handoff，不能让前后端自行产生两套语义。

实施顺序固定为：

1. P4-A：把 `GroundedStatement` 和 `PortfolioAnswerMaterial` 深化为 Spec 中的强类型结构；建立 `toGroundedContribution()`；引入 `PortfolioAnswerComposition`、`PortfolioCompositionContext`、`PortfolioCompositionResult`。先只接确定性 Composer，确保 P3 行为与测试不回归。P2 Synthesis 必须继续只消费模型前的 Grounded Contribution。
2. P4-B：实现 `ExpressionIntent`、`ExpressionAllowance`、请求内 Alias Registry、最小 Input Projector、三种 Draft 类型和严格 Codec；build-supported kind 只允许 FACT。实现公共 Validator 和 Fact 专项 Validator，补齐全部对抗测试；先使用 Fake Port，不接真实 Provider。
3. P4-C：建立独立 `PortfolioExpressionPort`、Prompt Factory、OpenAI-compatible Adapter、ModelPolicy/配置启动校验、共享 deadline、预构造 fallback、原子回退、Circuit Breaker 和脱敏诊断。默认关闭；一次非流式调用；不重试、不跨 Provider、不使用第二 Judge。
4. P4-D：实现 task composition、`GenerationMode.MIXED`、`AnswerConstructionMode.MIXED_COMPOSITION`、顶层聚合、DTO/Mapper、Eval 与隐私门禁。不要修改前端文件；只保证契约和后端测试完备。
5. P4-E：完成 Mock/隐私/packaged-JAR 验证；真实 Provider 只有获得当前用户显式授权后才能运行。未授权必须报告 INCOMPLETE，不得伪装 PASS。

关键不变量：

- Provider 不得接收问题、questionSpan、goalLabel、历史、Context/Token、内部 ID、route、reference key、Evidence/Chunk 正文。
- 模型只能编辑 P3 已批准 Statement；REQUIRED 必达，OPTIONAL 可省略，CONTEXT 不得独立结论。
- Caveat、omitted topic、Resolution、EvidenceState、比较关系、推荐候选/Tier/排序全部由核心代码固定。
- Draft 只返回请求内 Alias；PublicSourceReference 必须由服务端派生。
- 确定性 Plan 必须在 Provider 前成功构造；任何 Provider/Codec/Validator/Plan 错误整轮返回同一预构造 Plan。
- 正常未尝试不是 fallback，不设置 expression degraded。
- 普通 CI 零真实外发，日志与报告不含问题、答案、Prompt、Draft、Alias、reference key 或异常消息。

开发要求：

- 测试先行，按切片保持小步可验证；接口测试覆盖深模块可观察行为，不测试穿透内部实现。
- Java 禁止 `record`、`var`、Lombok；所有集合防御复制，`toString()` 脱敏。
- 不建立通用 Agent/Tool/Hook Registry，不引入动态 Map 参数或自由 Prompt。
- 使用现有模式和依赖，不新增库，除非先证明现有能力无法满足并取得用户同意。
- 每个切片运行相称的单测、架构、隐私、Eval、构建和后端集成门禁；失败必须定位根因，不能降低断言绕过。
- 完成后同步 `docs/08-当前实现状态.md`、`docs/11-项目演进日志.md`、路线图、环境变量示例和发布说明，准确区分 implemented-disabled、mock-verified、real-provider-incomplete/verified、deployment-enabled。

开始时先给出你对当前代码 seam 的核对结果和分切片实施计划，不要直接大规模修改。若实际 P3 接口与 Spec 有冲突，只报告具体冲突和最小修订方案，等待确认，不得自行扩大范围。
