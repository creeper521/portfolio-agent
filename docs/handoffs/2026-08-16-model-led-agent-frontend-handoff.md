# 模型主导 Agent 前端交接与执行提示词

> **日期：** 2026-08-16
> **状态：** 前端 Agent 交接已整理；必须等待用户明确实施授权和后端阶段门禁
> **协作设计：** `../superpowers/specs/2026-08-16-model-led-agent-frontend-backend-coordination-design.md`
> **第四版设计：** `../superpowers/specs/2026-08-16-model-led-agent-orchestration-design.md`
> **总实施计划：** `../superpowers/plans/2026-08-16-model-led-agent-orchestration-implementation-plan.md`

## 1. 使用方式

本文件同时承担：

1. 前端 Agent 的完整契约交接；
2. 可复制到新上下文的执行提示词；
3. 前端 Agent 开始各阶段前的门禁清单。

当前文档本身不授权修改生产代码。用户必须单独批准 P-1、第四版整体实施或明确阶段。P7 前端接线还必须等待后端 stp-v3 公共合同与 JSON fixtures 冻结。

## 2. 可复制提示词

```text
你将负责 portfolio-agent 项目“模型主导语义理解、受控编排”重构中全部前端工作。后端由另一个 Agent 全量负责。请严格遵守以下交接。

交流语言：中文。
Git commit：中文；未经用户明确授权不得 stage、commit 或 push。
工作方式：使用独立 worktree 和独立分支；开始前运行 git status --short、git log --oneline -12、git rev-parse --show-toplevel、git branch --show-current。保留用户已有修改，不得 reset、restore、checkout 或覆盖。

一、必须先完整阅读

1. AGENTS.md
2. docs/00-文档状态索引.md
3. docs/04-项目代码约束.md
4. docs/08-当前实现状态.md
5. docs/superpowers/specs/2026-08-10-semantic-turn-routing-design.md
6. docs/superpowers/specs/2026-08-16-model-led-agent-orchestration-design.md
7. docs/superpowers/specs/2026-08-16-model-led-agent-frontend-backend-coordination-design.md
8. docs/superpowers/plans/2026-08-16-model-led-agent-orchestration-implementation-plan.md
9. docs/handoffs/2026-08-16-model-led-agent-frontend-handoff.md
10. docs/handoffs/2026-08-13-agent-p5-frontend-public-contract.md
11. docs/reports/agent-behavior-full-path-audit-2026-08-16.md
12. 当前 frontend/src/features/agent、frontend/src/pages/AgentPage.vue、frontend/e2e 和相关测试

如果进入 P7，还必须完整阅读届时由后端 Agent 提供的：

- docs/handoffs/2026-08-16-agent-stp-v3-public-contract.md
- docs/handoffs/fixtures/model-led-agent-stp-v3/manifest.json
- 该目录全部 JSON fixtures

上述 stp-v3 精确合同或 fixtures 不存在时，不得开始 P7 接线；只报告 BLOCKED。

二、责任边界

你负责：

- frontend/src/**
- frontend/e2e/**
- 本功能需要的 frontend/playwright*.config.ts、frontend/vitest*.config.ts
- 确有必要时的 frontend/package.json
- 前端交互/视觉设计、TypeScript 合同消费、mapper、页面内存状态、组件、测试和浏览器验收
- 经用户批准后新建前端体验设计文档和前端实施计划

你不负责且不得修改：

- backend/**
- Provider、SHADOW、MODEL_LED、Codec、Compiler、Validator、Evidence、签名或服务端路由
- 后端 DTO、原因码和共享 JSON fixtures
- 既有第四版设计、总实施计划、docs/00、docs/08、docs/11、docs/13
- scripts/run-jar-e2e.ps1 与后端发布脚本
- 公开 Bundle、内容资产或治理数据

发现后端 DTO、公共合同和 fixture 不一致时，不要在前端猜测或建立兼容字符串。提供最小复现和差异报告，等待后端 Agent 修订。

三、设计权

涉及交互变化或视觉效果变化时，你拥有前端方案设计权。先按项目设计流程探索当前体验，提出方案并取得用户批准，再写体验设计和实施计划，之后才能改代码。

后端合同只规定业务语义和安全不变量，不规定：

- 用卡片、气泡、行内提示、抽屉或其它具体形态；
- 组件数量和组件树；
- 颜色、间距、图标、动效和信息密度；
- 具体按钮文案和说明文案的排版；
- 桌面、平板、移动的具体重排方式。

你的设计必须继续遵守既有暖调工作区视觉权威、颜色禁区、响应式、键盘、焦点、读屏和 reduced-motion 约束。不得用视觉设计改变合同语义，不得展示内部 Token、taskId、Provider、Prompt 或原因码。

四、阶段门禁

P-1（可在用户单独批准后实施）：

- 修复 AgentWorkspace 中 preparedContext.semanticContext 首轮合并与发送。
- Project/Case handoff 构造的 active subject 必须进入真实 POST /api/v2/answers 请求。
- 既有 Case 清除后不得继续发送旧 Case Hint。
- 本轮显式主体覆盖页面 Hint。
- 增加实际请求 JSON 捕获、取消、迟到响应和清除回归。
- 不新增 Project Hint 清除 UI；它属于 P6。

P0：

- 扩充前端行为 fixture、Oracle、API/UI 场景。
- 保留“裸代词澄清”和“推荐只含 Project”为目标行为，不把当前错误写成基线。

P1–P5：

- 后端 Agent 主导；你不提前把后端内部 CONVERSATIONAL、Provider 或模型步骤暴露到 stp-v2 UI。
- 不新增 AI/Provider 徽标、模型进度、SSE 或逐字展示。

P6（后端领域实现完成后）：

- 自主设计并实现 Project Hint 清除体验。
- 只定义未接线 RecentResultSet TypeScript 类型和纯内存 store 接口。
- 不把 recentResultSets 加入 stp-v2 HTTP。
- 不改变当前默认合同。

P7（后端 stp-v3 合同与 fixtures 冻结后）：

- 先以共享 fixtures 写 TypeScript、mapper、state 和 API RED 测试。
- 独立完成交互/视觉设计并取得用户批准。
- 消费六类 interaction.kind。
- 接入 recentResultSets、confirmedSubjects、pageHint、pendingInteraction 等冻结字段。
- 默认合同从 stp-v2 切到 stp-v3。
- 只允许普通只读 ASK 在明确 409 + AGENT_TURN_CONTRACT_UNSUPPORTED 后自动投影为 stp-v1 重试一次。
- CONFIRM_PLAN、澄清提交、取消和其它 continuation 禁止跨合同自动重放。
- 移除由多个 legacy 字段组合猜测 UI 状态的分支，但保留冻结合同要求的兼容读取。
- 完成 Mock E2E、桌面/平板/移动、键盘、焦点、读屏和 reduced-motion 验收。

P8：

- 提供前端 Vitest、build、Mock E2E 和目标浏览器结果。
- 配合后端 Agent 执行 packaged-JAR 无 mock E2E。
- Mock 通过不得描述成真实 Provider PASS。
- P8 通过不代表 MODEL_LED 已默认启用。

五、stp-v3 稳定语义

interaction.kind 是唯一 UI 权威，闭集为：

ANSWER
CONVERSATIONAL
CLARIFICATION
CONFIRMATION
BOUNDARY
CAPABILITY_UNAVAILABLE

不要从 legacy resolution、内部 disposition、Evidence 是否为空或文案猜测 kind。

- ANSWER：允许安全回答载荷和合法 Evidence；PARTIAL 的合法 Evidence必须保留。
- CONVERSATIONAL：只含短交流、澄清引导和闭集 action；不得显示 Evidence。
- CLARIFICATION：只消费后端签发的问题、字段、选项和 continuation；不得显示 Evidence。
- CONFIRMATION：只消费展示计划和 opaque 确认信封；不得显示 Evidence。
- BOUNDARY：安全边界与允许动作；不得显示 Evidence。
- CAPABILITY_UNAVAILABLE：当前能力不可用与允许动作；不得显示 Evidence。

任何非 ANSWER 响应均不得显示 Evidence/Public Source。合法载荷交叉、未知 enum 或不变量冲突时 fail closed；不得把两类 UI 混合展示。

六、RecentResultSet

后端签发 envelope：

resultSetId
sourceKind = RECOMMENDATION | COMPARISON
issuedAt
contentVersion
expiresAt
supersedesResultSetId?
items[] { position, subjectType, subjectId }
integrityToken

前端规则：

- 只在当前标签页内存保存；刷新/关闭后消失。
- 最多 3 组、合计最多 10 项；超限按服务端 issuedAt 最旧集合整组逐出。
- 不信任或重写 Token，不生成 resultSetId，不从回答正文或数组下标重建。
- 通过 stp-v3 semanticContext 原样回传。
- REFINE 成功时原子移除被引用旧 RECOMMENDATION 集并保存带 supersedesResultSetId 的新集。
- 前端重排不得改变服务端最近集合语义。
- 不写 URL、localStorage、sessionStorage、IndexedDB、日志或诊断正文。

七、Subject Context

- pageHint 只能来自受控 Project/Case handoff。
- 本轮显式主体高于 pageHint。
- 不从 Assistant 正文、标题或 DOM 推导 confirmedSubjects。
- confirmedSubjects 只消费后端冻结合同返回的结构化状态。
- 清除 Hint 不能误删其它已确认主体或 RecentResultSet。
- 首轮裸“它/这个/那个/it/this/that”不能在前端静默替换为 pageHint。
- 问题和回答不得进入 URL 或持久化存储。

八、预计主要代码落点

- frontend/src/features/agent/api/answerApi.ts
- frontend/src/features/agent/api/answerApi.test.ts
- frontend/src/features/agent/model/answerTypes.ts
- frontend/src/features/agent/model/mapAnswerResponse.ts
- frontend/src/features/agent/model/semanticTurnView.ts
- frontend/src/features/agent/model/sessionTypes.ts
- frontend/src/features/agent/model/handoffStore.ts
- frontend/src/features/agent/components/AgentWorkspace.vue
- frontend/src/features/agent/components/ConversationThread.vue
- 由你的体验设计决定的新建或拆分组件
- frontend/src/pages/AgentPage.vue
- frontend/e2e/support/publicApiMocks.ts
- frontend/e2e/behavior/**
- 相关 Vitest、Vue Test Utils 和 Playwright 用例

此清单不是强制组件结构；可以根据批准的前端设计调整，但不得扩大到无关页面或改动后端。

九、TDD 与测试

所有行为变更严格 RED → GREEN → REFACTOR。至少覆盖：

1. Project/Case 首轮 semanticContext 实际发送。
2. Case Hint 清除后旧 Hint 不发送。
3. 本轮显式主体覆盖页面 Hint。
4. 六类 interaction 合法载荷。
5. 六类 interaction 非法交叉载荷 fail closed。
6. 非 ANSWER 不渲染 Evidence/Public Source。
7. ANSWER/PARTIAL 保留合法 Evidence。
8. RecentResultSet 3 组/10 项、整组逐出和 issuedAt 排序。
9. REFINE 原子替换。
10. recentResultSets 不进入浏览器持久化、URL 或日志。
11. v3 成功、普通 ASK 的一次 v1 回退、continuation 不重放。
12. 未知版本/enum 不猜测。
13. Contract fixture 与前端 Mock 一致。
14. 桌面、平板、移动、键盘、焦点、读屏和 reduced-motion。

验证命令：

npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
npm.cmd --prefix frontend run test:e2e

packaged-JAR 无 mock E2E 由后端 Agent 最终统一执行；你需要配合定位前端消费者问题。

十、禁止事项

- 禁止修改 backend/** 或共享 stp-v3 fixture。
- 禁止在前端重新实现语义路由、主体绑定、Evidence 校验、Token 校验或 supersession 安全判断。
- 禁止从用户文本、回答正文或 UI 文案推断主体/任务/结果集。
- 禁止展示 Provider、Prompt、内部原因码、完整性 Token、plan/task 内部 ID。
- 禁止把非 ANSWER 渲染成带来源的回答。
- 禁止把能力不可用伪装成澄清，把澄清伪装成交流，或把 fallback 伪装成失败。
- 禁止写入浏览器持久会话或让问题进入 URL/history。
- 禁止引入 SSE、Streaming、动态 Tool UI、多 Agent UI 或范围外状态库。
- 禁止用 Mock 结果冒充真实 Provider、PostgreSQL 或 packaged-JAR 验收。

十一、交付汇报

每个阶段汇报：

- 实际修改文件；
- 使用的合同与 fixture 版本；
- 体验设计文档及用户批准状态；
- RED/GREEN/REFACTOR 证据；
- Vitest/build/E2E 结果；
- PASS/FAIL/BLOCKED/INCOMPLETE；
- 合同差异或需要后端处理的问题；
- 未执行的真实 Provider、packaged-JAR 或浏览器路径必须如实标记。

开始时只做状态核对、文档阅读和前端设计梳理。没有用户明确实施授权时，不修改生产代码。
```

## 3. 后端必须提供给前端的合同包

P7 开始前，后端 Agent 负责创建并冻结：

```text
docs/handoffs/2026-08-16-agent-stp-v3-public-contract.md
docs/handoffs/fixtures/model-led-agent-stp-v3/manifest.json
docs/handoffs/fixtures/model-led-agent-stp-v3/*.json
```

合同包必须说明：

- 完整 request/response DTO；
- action 与必填字段矩阵；
- `interaction.kind` 与合法载荷矩阵；
- semanticContext 全字段、null/缺省语义和预算；
- RecentResultSet 生命周期；
- confirmedSubjects 更新来源；
- 澄清、确认、取消和 plan invalidation 提交；
- v1/v2/v3 协商和 409 错误；
- Evidence/Public Source 不变量；
- 所有枚举和公开原因码；
- 合法与非法 fixture；
- fixture manifest 和 SHA-256。

前端 Agent 不以本 handoff 中的概念示意替代届时冻结的精确 JSON。

## 4. 前端体验设计自由边界

前端 Agent 必须设计“如何呈现”，但不能改写“呈现什么语义”。以下内容不在本 handoff 中定死：

- 六类 interaction 使用何种具体视觉容器；
- 提示、动作、状态摘要和结果之间的视觉层级；
- Hint 清除入口的位置和反馈形式；
- RecentResultSet 的选择、续接和替换反馈；
- 组件拆分、移动端重排、动画和微文案；
- 是否复用或重构现有 PlanConfirmation、TurnClarification、ContextInvalidatedNotice 等组件。

以下结果约束仍然强制：

- 用户能区分回答、交流、澄清、确认、边界和能力不可用；
- 不只依赖颜色；
- 键盘、焦点、读屏和 reduced-motion 可用；
- 不暴露内部字段；
- 不虚构来源、能力或成功状态；
- 不改变当前隐私契约。

## 5. 当前阶段状态

- 第四版整体设计仍待用户整体批准；
- P-1 可由用户单独批准；
- 本文件已完成责任与提示词整理，但没有启动前端实施；
- stp-v3 精确公共合同和 fixtures 尚未由后端实现冻结；
- 前端 P7 当前状态应为 `BLOCKED_BY_BACKEND_CONTRACT_FREEZE`；
- 任何前端 Agent 都应先向用户确认本次获批阶段。
