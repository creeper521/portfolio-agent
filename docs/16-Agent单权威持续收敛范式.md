# Agent 单权威持续收敛范式

> **适用范围：** 仅适用于当前 Portfolio Agent 仓库。
> **目标：** 防止 Command、Goal、Plan、Execution、Projection、State、API 与 Frontend 再次形成长期多权威和新旧双栈。
> **机器状态：** `docs/agent-architecture-status.json`。

## 1. 核心原则

本项目的架构演进遵循四个动作：**单一权威、纵向替换、同期删除、证据闭环**。这些规则保护演进质量，不保护既有实现；充分证据可以推翻当前架构。

新权威进入生产路径时，旧权威必须在同一个 Replacement Slice 内退出生产路径并删除。运行时不保留旧架构、兼容桥或配置式双栈；回退只使用 Git commit、已验证 JAR 或整体部署版本。

## 2. 变更分级

### Level 1：直接修改

同时满足下列条件：

- 不改变 Agent 核心权威；
- 不改变公开合同、持久化语义或生产入口；
- 不跨 Capability 边界；
- 不要求新旧实现并存。

执行聚焦测试、受影响的全量门和中文小提交即可。

### Level 2：模块变更

改变一个深模块的内部结构，但外部合同和生产权威不变。必须声明模块入口、不变量、调用方和验证范围，不启动完整 Replacement Slice。

### Level 3：架构替换

出现下列任一情况即属于 Level 3：

- 新增或替换 Command、Goal、SemanticPlan、SemanticResult 或 PublicAgentTurn；
- 改变 Agent API、会话状态、幂等或持久化语义；
- 改变 Frontend/Backend 共享合同；
- 同一概念将出现第二个生产实现；
- 需要创建临时兼容桥或迁移新旧链。

Level 3 必须先冻结权威表、Replacement Manifest、删除清单、Golden Fixtures、Exit Gates 与版本级回退方案。

### Architecture Review：证据驱动的架构重评

出现重复 workaround、反复延期、跨层分支、重复翻译、内部细节泄漏，或者当前设计无法满足扩展、可靠性、性能与隐私要求时，进入 `ARCHITECTURE_REVIEW`。此状态允许继续诊断、比较候选方案和构建不接入生产权威的隔离原型。

评审必须说明当前限制、代码路径证据、继续修补成本、候选新权威、受影响调用方、迁移与删除范围、Exit Gates 和版本级回退。用户批准后将候选方案冻结为新的目标权威，再按 Level 3 执行。批准前只暂停未经授权的生产权威变更，不暂停分析、验证和其他范围内工作。

## 3. 不可绕过的硬规则

以下规则不能标记为 `WAIVED`。它们约束迁移结果与最终稳态，不代表当前权威不可替换：

1. 一个业务概念只能有一个生产权威；
2. 不保留运行时兼容桥、旧链 fallback 或配置式双栈；
3. 回退只依赖 Git、JAR 或整体部署版本；
4. Frontend 与 Backend 共同消费同一公开合同源；
5. 未接入唯一生产路径的代码不能宣称已实现；
6. 未运行的验证不能记录为 `PASS`；
7. 不泄露或持久化访客问题、Prompt、原始模型响应、凭据和私有资产；
8. 存在未关闭必需项时，整体状态不能为 `COMPLETE`。

硬规则状态只能是 `PASS`、`FAILED` 或 `BLOCKED`。

## 4. 允许延期的软门禁

环境、授权或跨责任区条件暂不满足时，可以记录 `WAIVED` 并继续不受影响的工作，例如：

- Docker/Testcontainers 暂不可用；
- Playwright Browser 或 packaged JAR 环境暂不可用；
- 真实 Provider 调用尚未授权；
- Frontend/Backend 责任项由另一 Agent 并行实施；
- 非关键性能门缺少本地模型或外部发布目录。

`WAIVED` 不等于 `PASS`，也不关闭事项。每个延期项必须记录：

- 稳定 ID、原因、责任区和受影响 Exit Gate；
- 最近一次检查时间与安全证据摘要；
- 重新检查条件和可直接执行的检查命令；
- 恢复后的第一条动作及成功条件；
- 延期期间禁止作出的完成声明；
- `recheckBy`，到期后必须重新检查，不能原样继承历史判断。

## 5. 后续 Agent 的恢复协议

开始 Agent 架构相关工作时：

1. 读取 `docs/agent-architecture-status.json`；
2. 运行 `scripts/agent-architecture-status.ps1`；
3. 检查所有非 `CLOSED` 延期项的 `resumeWhen`；
4. 恢复条件已经满足时，优先顺手或并行偿还；
5. 若偿还会显著扩大用户当前需求，只提示影响并保留账本，不擅自改变当前优先级；
6. 新证据出现后更新 `checkedAt`、`summary` 和状态；
7. 只有硬规则全部 `PASS`、延期项全部 `CLOSED` 或为空时，才能标记整体 `COMPLETE`。

发布门 `scripts/verify-release.ps1` 会自动运行校验器及其正反例测试，因此错误的完成状态不能进入发布候选。

真正阻断必须说明阻断对象、已尝试检查、所需决定、解除条件以及解除后的下一条动作。测试失败、命令错误和可本地诊断的问题本身不构成阻断。

## 6. Replacement Slice 固定流程

每个 Level 3 Slice 按下列顺序执行：

1. 新增目标权威；
2. 接入唯一生产入口；
3. 迁移全部生产调用方；
4. 运行替代安全测试；
5. 删除旧权威、配置、测试和兼容桥；
6. 运行生产源码零引用门；
7. 运行合同消费、全量回归和风险对应的联合门；
8. 更新机器状态与实施文档；
9. 使用中文小提交记录单一责任变化。

“类已创建”或“单元测试通过”不构成 Slice 完成。完成必须证明新权威已进入生产路径且旧权威已经退出。

## 7. 验证层级

验证不能相互替代：

1. 目标行为测试；
2. 模块和架构依赖测试；
3. Backend/Frontend 全量测试与 build；
4. 生产源码零引用门与 privacy check；
5. PostgreSQL/Testcontainers；
6. packaged-JAR Browser E2E；
7. 获得明确授权后的真实 Provider canary。

只记录实际运行得到的当前证据。环境恢复后必须重新运行此前延期的门。

## 8. 当前生产权威

当前权威的机器可读清单位于 `docs/agent-architecture-status.json`。架构设计与实现细节仍以以下文档为准：

- `docs/superpowers/specs/2026-08-17-agent-architecture-convergence-design.md`；
- `docs/superpowers/specs/2026-08-18-agent-architecture-convergence-implementation-plan.md`；
- `docs/handoffs/2026-08-18-agent-architecture-convergence-frontend-handoff.md`；
- `contracts/agent-turn/`。

历史计划只用于解释演进，不得重新成为生产权威。
