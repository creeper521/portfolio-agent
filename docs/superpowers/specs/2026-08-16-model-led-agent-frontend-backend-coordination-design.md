# 模型主导 Agent 前后端协作与契约冻结设计

> **日期：** 2026-08-16
> **状态：** 协作方式已由用户确认；用于拆分后端 Agent 与前端 Agent 的责任，不构成第四版整体实施授权
> **上游设计：** `2026-08-16-model-led-agent-orchestration-design.md`
> **上游总计划：** `../plans/2026-08-16-model-led-agent-orchestration-implementation-plan.md`
> **前端交接：** `../../handoffs/2026-08-16-model-led-agent-frontend-handoff.md`

## 1. 决策结论

本轮采用“后端契约先行、前端分阶段消费”的协作方式：

1. 后端 Agent 全量负责 Java 领域、Provider、编译校验、HTTP DTO、兼容策略、签名与复验、后端测试、共享契约 fixture 和发布门禁；
2. 前端 Agent 全量负责本功能涉及的 Vue/TypeScript、页面内存上下文、公共契约消费、交互与视觉设计、前端测试和浏览器验收；
3. P-1 的前端 semanticContext 修复可以在当前 stp-v2 合同上独立进行；
4. P6 只允许前端定义未接线的类型/纯内存接口和设计 Project Hint 清除体验，不得提前把 RecentResultSet 接入 v2 HTTP；
5. P7 必须等待后端发布 stp-v3 精确公共合同和冻结 JSON fixtures 后再接线；
6. 前端默认合同从 v2 切到 v3 之前，新后端必须已同时接受 v1/v2/v3；
7. 两个 Agent 不同时修改同一生产文件，最终通过 packaged-JAR 无 mock E2E 汇合。

本设计只冻结业务语义、公共数据合同、文件所有权、顺序和验收门禁。组件拆分、布局、视觉层级、信息密度、动效、微文案和响应式呈现由前端 Agent 在既有视觉与无障碍边界内独立设计，经用户批准后实施。

## 2. 目标与非目标

### 2.1 目标

- 后端和前端可以在不同 worktree 中推进，且不会形成两套语义权威；
- 前端无需阅读后端内部实现即可准确消费 stp-v3；
- 后端可以独立演进模型语义理解，而不把 Provider 或内部状态泄露到 UI；
- 任何合同漂移都在 merge 前被 fixture、类型测试或 packaged-JAR E2E 阻断；
- 保留旧 v2 客户端、新 v3 客户端和二进制回滚的安全过渡路径；
- 明确前端体验设计自由与后端事实/安全权威的边界。

### 2.2 非目标

- 本文不设计具体卡片、按钮、颜色、间距、动效或文案排版；
- 不预先确定 Vue 组件数量、组件树或 CSS 结构；
- 不把前端 Agent 变成后端 DTO、路由或签名规则的共同所有者；
- 不允许前端通过字符串、旧 resolution 或回答正文重新推断后端语义；
- 不引入独立前端状态库、浏览器持久会话、SSE 或新的部署单元；
- 不批准第四版整体实施、MODEL_LED 默认启用或 Conversation Recovery 启用。

## 3. 权威边界

### 3.1 后端权威

后端是以下内容的唯一权威：

- `agentTurnContract` 支持矩阵与协商错误；
- stp-v3 请求/响应 DTO、字段闭集和跨字段不变量；
- `interaction.kind` 及每种 kind 的合法载荷；
- 任务、主体、依赖、Evidence、Public Source 和成功状态；
- `confirmedSubjects` 的入列、MRU、失效和版本规则；
- RecentResultSet 的签发、`issuedAt`、完整性 Token、supersession 和复验；
- 澄清/确认选项的身份、合法动作和 opaque 信封；
- Provider、SHADOW、MODEL_LED、fallback、预算、熔断和诊断语义；
- v1/v2/v3 兼容 Mapper 和公开原因码；
- 公共事实、Evidence 和隐私边界。

前端不能修复或覆盖上述决定；遇到 DTO、fixture 和文档不一致时必须 fail closed 并向后端报告。

### 3.2 前端权威

前端 Agent 是以下内容的设计与实现权威：

- 六类 interaction 的信息架构和呈现方式；
- 页面 Hint 的可见、清除和反馈体验；
- RecentResultSet 对访客可见的选择/续接方式；
- 澄清、确认、边界、能力不可用和交流恢复的交互层级；
- 组件拆分、布局、响应式、键盘路径、焦点、读屏、动效和微文案；
- stp-v3 TypeScript 类型、严格 mapper、页面内存 store 和 UI 状态；
- 前端单测、Mock E2E、桌面/平板/移动验收。

前端设计必须继续遵守现有暖调工作区视觉基线、无绿色/紫色/装饰渐变禁区、只在当前标签页内存保存会话、问题不进 URL/持久化存储等既有约束。本文不进一步指定视觉方案。

### 3.3 共享权威

只有以下后端产物可作为两个 Agent 的共享合同：

1. stp-v3 公共合同文档；
2. 版本化 JSON fixtures 与 manifest；
3. 后端 HTTP contract tests 的通过结果；
4. packaged-JAR 的真实 API 响应；
5. 当前第四版设计中的安全和迁移不变量。

TypeScript interface、Mock 工厂和前端 view model 都是合同消费者，不得反过来定义后端语义。

## 4. 文件所有权

### 4.1 后端 Agent 独占

- `backend/**`
- `governance/portfolio-governance/evaluation/**` 中本次模型编排案例与 policy
- `scripts/run-agent-behavior-audit.ps1`
- `scripts/run-jar-e2e.ps1`
- 本设计、第四版设计、总实施计划、stp-v3 公共合同、共享 fixture 与状态文档
- `docs/00-文档状态索引.md`
- `docs/08-当前实现状态.md`
- `docs/11-项目演进日志.md`
- `docs/13-Agent对话体验与智能编排改造路线图.md`

### 4.2 前端 Agent 独占

- `frontend/src/**`
- `frontend/e2e/**`
- `frontend/playwright*.config.ts`
- `frontend/vitest*.config.ts`
- `frontend/package.json`（仅在本功能确有命令或依赖需求时）
- 前端 Agent 经用户批准后新建的体验设计与前端实施计划

前端 Agent 不修改既有后端/总设计/状态文档。需要调整合同或权威状态时提交差异报告，由后端 Agent 统一处理。

### 4.3 共享文件规则

- 两个 Agent 使用不同 worktree 和不同分支；
- 不允许双方同时编辑相同文件；
- 共享 fixture 由后端 Agent 写入并冻结，前端 Agent 只读；
- 前端 Agent 可以在 `frontend/**` 内建立消费 fixture 的测试适配器，但不得复制后再改变语义；
- 合并顺序固定为后端合同提交在前、前端消费者提交在后；
- 若需要修订已冻结合同，先由后端更新合同版本和 fixture，再由前端适配，禁止双方在 merge 时手工折中字段。

## 5. 阶段顺序

### 5.1 D0：文档与授权门禁

- 第四版整体设计、P-1 或具体实施阶段必须由用户明确授权；
- 本协作设计和前端 handoff 先完成用户审核；
- 未获实施授权时两个 Agent 都不得修改生产代码。

### 5.2 D1：P-1 可并行热修

后端 Agent：

- 修复噪声输入误编译；
- 强制非回答无 Evidence/Public Source；
- 保留 ANSWER/PARTIAL 的合法 Evidence；
- 为 `/api/v1/public-content` 增加 `Cache-Control: no-store`。

前端 Agent：

- 修复 `preparedContext.semanticContext` 首轮合并和发送；
- 保证 Case 清除后不发送旧 Case Hint；
- 增加 Project/Case 首轮请求体、显式主体覆盖、清除和竞态回归；
- 不在 P-1 新增 Project Hint 清除 UI。

双方分别完成 TDD，随后由后端 Agent运行当前合同的 packaged-JAR 行为回归。

### 5.3 D2：P0–P5 后端主导

- P0 的后端行为基线、P1 提议合同、P2 编译验证、P3 Adapter/SHADOW、P4 后端交流恢复和 P5 单任务 MODEL_LED 由后端 Agent 完成；
- 前端 Agent 不消费 P4/P5 的后端内部中间状态，不展示 Provider 或模型步骤；
- P4/P5 只在后端和测试的非默认环境运行，不提前改变 stp-v2 UI。

### 5.4 D3：P6 前端预备，不接线

后端 Agent先完成：

- confirmedSubjects、RecentResultSet、supersession 和上下文优先级领域实现；
- P6 测试传输闭环；
- stp-v2 请求/响应固定 fixture 字节级不变。

前端 Agent随后可以：

- 设计 Project Hint 清除交互；
- 定义未接线的 RecentResultSet TypeScript 类型和纯内存 store 接口；
- 编写不触发真实 v2 HTTP 的纯函数/类型测试；
- 不把新字段加入 v2 request/response，不改默认合同。

### 5.5 D4：P7 后端合同冻结

前端 P7 接线前，后端 Agent 必须同时交付：

1. 新后端接受 v1/v2/v3；
2. stp-v3 公共合同文档；
3. 版本化请求/响应 JSON fixtures；
4. fixture manifest（合同版本、schema/content/capability 版本和 SHA-256）；
5. 六类 interaction、RecentResultSet、409 和非法载荷的 HTTP tests；
6. v2 固定 fixture 仍字节级一致；
7. 尚未把生产默认切到 MODEL_LED；
8. 尚未删除 v2 接受路径。

共享 fixture 目录固定为：

```text
docs/handoffs/fixtures/model-led-agent-stp-v3/
├── manifest.json
├── request-ask-page-hint.json
├── request-ask-recent-result-sets.json
├── request-confirm-plan.json
├── request-submit-clarification.json
├── response-answer.json
├── response-conversational.json
├── response-clarification.json
├── response-confirmation.json
├── response-boundary.json
├── response-capability-unavailable.json
├── response-recommendation-result-set.json
├── response-refined-result-set.json
└── error-contract-unsupported.json
```

fixture 的精确 JSON 只由届时实现完成的后端 DTO 序列化产生，不在本协作设计中手写假定字段。

### 5.6 D5：P7 前端设计与实现

前端 Agent按顺序：

1. 阅读冻结公共合同和全部 fixtures；
2. 对交互/视觉变化独立进行体验设计，并取得用户批准；
3. 以 fixture 建立 TypeScript/mapper/状态机 RED 测试；
4. 实现页面内存 Context、六类 interaction 和 v3 请求；
5. 完成 v2→v3 默认切换与受控 v1 回退；
6. 完成单元、Mock E2E、响应式和无障碍验收；
7. 不修改后端或共享 fixture。

### 5.7 D6：集成与发布候选

- 后端合同分支先合入集成分支；
- 前端消费者分支再合入；
- 构建一个包含新前端和新后端的可执行 JAR；
- 运行无 mock API/浏览器路径和双向回滚矩阵；
- 任一合同、Evidence、隐私、409 或 Context 门禁失败都不得进入 P8；
- P8 通过仍不自动启用 MODEL_LED。

## 6. 公共语义合同

### 6.1 interaction.kind

stp-v3 只公开以下 UI 状态：

```text
ANSWER
CONVERSATIONAL
CLARIFICATION
CONFIRMATION
BOUNDARY
CAPABILITY_UNAVAILABLE
```

`interaction.kind` 是唯一 UI 权威。前端不得从 legacy `resolution`、内部 disposition、Evidence 是否为空或文案内容推断另一种 kind。

| kind | 允许的语义 | Evidence/Public Source |
|---|---|---|
| `ANSWER` | 安全完成或部分完成的任务结果 | 允许且只能来自后端合法回答 |
| `CONVERSATIONAL` | 短交流回复、澄清引导、闭集 action | 禁止 |
| `CLARIFICATION` | 后端签发的问题、选项、待补字段 | 禁止 |
| `CONFIRMATION` | 展示计划与 opaque 确认信封 | 禁止 |
| `BOUNDARY` | 安全边界说明和允许动作 | 禁止 |
| `CAPABILITY_UNAVAILABLE` | 当前能力不可用和允许动作 | 禁止 |

非法交叉载荷必须 fail closed，不得为了“尽量显示”混合渲染。

### 6.2 非回答无 Evidence

所有合同版本都必须满足：

```text
interaction.kind != ANSWER
→ evidenceIds = []
→ publicSourceCatalog = []
→ answerSections 不得包含 EVIDENCE
```

`ANSWER/PARTIAL` 的合法 Evidence 不能因清理逻辑被误删。

### 6.3 RecentResultSet

后端签发结构固定包含：

```text
RecentResultSetEnvelope
├── resultSetId
├── sourceKind: RECOMMENDATION | COMPARISON
├── issuedAt
├── contentVersion
├── expiresAt
├── supersedesResultSetId?
├── items[]
│   ├── position
│   ├── subjectType
│   └── subjectId
└── integrityToken
```

前端职责只包括：

- 在当前标签页内存原样保存；
- 最多保留 3 组、合计最多 10 项，整组逐出；
- 使用服务端 `issuedAt` 维护次序，不用数组顺序伪造最近性；
- REFINE 成功时原子移除被引用旧推荐集并保存新集；
- 通过 stp-v3 `semanticContext` 原样回传；
- 不修改 Token、不生成 ID、不从回答正文重建集合；
- 不写 URL、localStorage、sessionStorage、IndexedDB 或日志。

服务端仍会复验全部字段，前端校验不能成为安全权威。

### 6.4 Subject Context

- 页面 Hint 可以由前端从受控 Project/Case handoff 构造；
- 本轮显式主体高于 pageHint；
- 前端不得从 Assistant 正文、标题或 DOM 推断 confirmedSubjects；
- confirmedSubjects 只保存后端明确返回的结构化状态；
- contentVersion 变化或后端返回失效时按合同清除；
- 清除 Hint 只清除对应页面来源，不得误删后端确认的其它主体或结果集；
- 首轮裸代词不能由前端静默替换成 pageHint 主体。

### 6.5 v2→v3 协商

- 新后端先接受 v1/v2/v3；
- 前端在该条件满足后默认请求 v3；
- 只有普通只读 `ASK` 在明确 `409 + AGENT_TURN_CONTRACT_UNSUPPORTED` 后可以把同一内存输入投影成 v1 并自动重试一次；
- `CONFIRM_PLAN`、澄清提交、取消和其它 continuation 不跨合同自动重放；
- v3 响应不能按 v2 解析，未知版本不能猜测；
- v2 删除晚于旧前端缓存窗口和真实版本指标归零。

## 7. Merge 门禁

### G0：文档门禁

- 用户批准第四版整体或明确阶段；
- 用户审核本设计和 frontend handoff；
- 前端 Agent 的交互/视觉设计单独获用户批准。

### G1：文件所有权门禁

- diff 不含越权目录；
- 没有同文件并行修改；
- 共享 fixture 只由后端改动；
- 前端不修改后端原因码或合同文档来自行适配。

### G2：合同门禁

- 后端 fixture 均由生产 DTO mapper 序列化；
- manifest hash 与文件一致；
- 六类 interaction 的合法/非法组合有测试；
- v2 fixture 字节级不变；
- 前端 mapper 对未知版本/枚举 fail closed；
- 前端 mock 与共享 fixture 一致。

### G3：安全与隐私门禁

- 非 ANSWER Evidence 比例为 0；
- 问题、回答、Token、信封和 Context 不进 URL/持久化/日志；
- 前端不展示 Provider、Prompt、内部 reason、taskId 或完整性 Token；
- RecentResultSet 仅标签页内存；
- `scripts/privacy-check.ps1` 无新增问题。

### G4：发布拓扑门禁

- 旧 v2 前端访问新后端成功；
- 新 v3 前端访问旧后端的普通 ASK 只回退一次 v1；
- continuation 不自动重放；
- 不存在全站 409 中间态；
- LEGACY 运行模式下 v3 获得明确兼容结果；
- packaged-JAR 无 mock E2E 通过。

### G5：体验门禁

体验形式由前端 Agent 设计，但结果必须满足：

- 六类 interaction 可被用户理解和操作；
- 状态不只依赖颜色；
- 键盘、焦点、读屏和 reduced-motion 有覆盖；
- 桌面、平板、移动无横向溢出；
- 非回答不出现 Evidence Desk 的虚假来源；
- fallback/能力不可用/边界/澄清不会互相伪装。

## 8. 测试责任

### 后端 Agent

- Java domain、Codec、Compiler、Validator 和 Mapper 单测；
- Provider/Fake/SHADOW/预算/熔断测试；
- HTTP contract 和 JSON fixture 测试；
- v1/v2/v3 过渡矩阵；
- RecentResultSet 签发/篡改/过期/版本/supersession；
- Maven、代码质量、架构、隐私、package 和 packaged-JAR 编排。

### 前端 Agent

- TypeScript 闭集与 mapper 测试；
- pageHint/confirmedSubjects/recentResultSets 纯内存测试；
- P-1 实际请求 JSON 捕获；
- 六类 interaction 组件/页面测试；
- 409 回退与 continuation 禁止重放；
- Mock E2E、响应式、键盘、读屏和 reduced-motion；
- 前端 Vitest 和 production build。

### 联合验收

- packaged-JAR API 与浏览器全路径；
- 新旧合同组合与二进制回滚；
- 真实 Provider 状态必须如实区分 PASS/FAIL/BLOCKED/INCOMPLETE；
- Mock 结果不能冒充真实 Provider 验收。

## 9. 冲突与变更流程

1. 前端发现合同不完整或不一致时停止该路径，不在 mapper 中猜测；
2. 前端提交最小复现、期望语义和受影响 fixture 名；
3. 后端 Agent 判断是实现缺陷还是合同变更；
4. 若是合同变更，先更新设计/合同版本、后端 DTO 和共享 fixture；
5. 后端门禁通过后，前端再更新消费者；
6. 禁止以“临时兼容”为由长期保留未版本化字段或双状态机。

## 10. 文档维护

- 本设计只记录协作与契约冻结方式；
- 前端 Agent 的交互/视觉方案使用独立 frontend experience design 文档；
- stp-v3 精确字段使用独立 public contract handoff；
- 行为、默认合同或能力边界真实改变后由后端 Agent 更新 `docs/08`、`docs/11` 和必要的 `docs/13`；
- 未实际改变公开 Bundle/资产时不修改 `docs/09`；
- 设计和 handoff 完成不等于生产代码已实施。
