# Agent 全路径行为审计报告（2026-08-16）
<!-- DOCUMENT_STATUS: HISTORICAL -->

## 结论摘要

本次审计在隔离 worktree `codex/agent-behavior-audit` 中完成，未修改主分支，也未修改生产运行时代码。审计新增了可重复的合成场景目录、统一 Oracle、隐私安全 API/UI 驱动器、Fake Provider 对抗测试、分层运行器和报告。

结论不是“全部通过”：真实 Provider 的接入链路本身通过，但行为审计暴露了两个确认的产品行为缺陷，以及一个运行时契约疑点。Context Store、Hybrid 检索和真实浏览器 UI 的部分路径因环境/配置未启用，必须标记为 BLOCKED 或 INCOMPLETE，不能当作通过。

## 执行范围与覆盖

| 范围 | 结果 |
|---|---|
| 行为 Context 状态 | 13 类已建模并由 corpus 校验覆盖 |
| 输入类别 | 10 类已建模；包含 `112233`、空白、Emoji、Unicode 长度、未知主体、私有来源、凭据、Prompt Injection、伪造状态/贡献、歧义引用、失败恢复 |
| ACTIVE preset | 运行时从 `/api/v1/public-content` 动态读取并展开；不复制固定 ID。当前基线为 18 个的契约已建模 |
| 统一 Oracle | 22 个 Vitest 用例通过；可捕获主体错配、Evidence 越界、失败历史污染、上下文泄漏、旧响应覆盖、噪声未澄清等硬失败 |
| Fake Provider | 5/5 通过，覆盖 timeout、unavailable、malformed draft、取消传输、恢复请求及 semantic routing closed contract |
| 真实 Provider | `DEEPSEEK_V4_FLASH` live-probe：readiness、request correlation、diagnostics、Case API、Case Agent、Provider verification 均通过 |
| 真实浏览器行为 | 当前环境缺少 Playwright 所需 `chrome-headless-shell-1228`，UI 实际执行不可复现；行为 Playwright 可发现 39 条测试 |

## 验证门禁

- 后端：使用本机 JBR 25 并显式注入已缓存 Byte Buddy agent 后，Maven 全量 `1197 tests, 0 failures, 0 errors, 26 skipped` 通过。未注入 agent 时产生的 149 个 Mockito error 是运行环境假失败，不是产品回归。
- 前端：全量 Vitest `62 files / 675 tests` 通过；行为专用 Vitest `3 files / 22 tests` 通过；`vue-tsc` 和 Vite production build 通过。
- 隐私：`privacy-check.ps1 -Path backend/src/main` 扫描 791 个文件通过；`-Path frontend/dist` 扫描 25 个文件通过；`git diff --check` 无空白错误。
- Eval：新增行为案例保留为审计 corpus，未加入既有 PhaseZero manifest。将它直接登记会破坏现有“4 个 tracked case files”基线并触发 `Invalid evaluation suite`，故该失败归类为测试/治理集成问题，不改动发布基线。

## 真实 Provider 接入结果

Provider 只通过仓库外临时环境注入，未写入仓库、日志、报告、URL、浏览器存储或请求历史。真实链路证明“Provider 能启动并返回符合当前 operation policy 的响应”，不能外推为所有行为规则均正确。

由于 API Key 已在对话中暴露，必须立即在 DeepSeek 控制台撤销并轮换；本报告不记录 Key，也不记录任何原始问题或回答。

## 发布级 Playwright 结果的拆分解释

已有一次完整发布级 Playwright 记录共 186 条：110 passed、40 failed、36 skipped。这个总数不能直接当作“产品 40 个 Bug”：

- failed 主要集中在本次新增行为审计场景（噪声/澄清、Project/Case semanticContext）以及 Context Store 未启用时仍要求 `AVAILABLE`、resume token、reload recovery 的场景。
- skipped 主要是显式依赖 PostgreSQL、Hybrid 模型或 live lane 的门控用例。
- 本报告将可稳定复现且由生产运行时直接观察到的两类行为归为产品缺陷；把 Context Store/模型/浏览器缺失归为环境阻塞；把错误请求体、manifest 注册方式和运行器未选择行为项目归为测试基础设施问题，并已修正后两项。
- 当前 worktree 可稳定重跑的行为发现数为 39 条（4 个 Playwright project）；浏览器项目仍因缺少 `chrome-headless-shell-1228` 不能在本环境重新执行，API 项目可通过外部 JAR 单独运行。

## 确认的产品缺陷

### P1：无意义输入未进入澄清态，并返回 Evidence

- 场景：`112233`，无主体、无 preset、无历史。
- 规则：必须返回 `NEEDS_CLARIFICATION`，不得继承主体，不得返回 Evidence。
- 实测 JAR：HTTP 200，但 `resolution=NOT_SUPPORTED`，`evidenceIds` 数量为 1，`publicSourceCatalog` 数量为 1。
- 体感：访客输入无法判断含义的数字后，系统展示了带公开来源的“不支持”结果，而不是明确告诉访客“请说明想了解哪个项目/案例”。这会让用户误以为数字被理解过，并降低证据可信度。
- 根因方向：`SemanticSignalCollector` 对无意图、无主体文本的兜底会构造 `GENERAL_EXPLANATION`，没有把纯噪声与澄清策略绑定；运行时 mapper 又把边界结果投影成可带来源的结构。
- 建议修复：增加无意义/短文本确定性分类；无主体噪声只允许澄清 DTO，强制 Evidence、主体和 continuation 为空；补 API、UI、Eval 三层回归。

### P1：Project/Case hint 构造的 semanticContext 没有进入首轮请求

- 位置：`frontend/src/features/agent/components/AgentWorkspace.vue` 的 `requestAnswer()`。
- 现象：函数先用 `buildSemanticContext()` 写入 `semanticContinuations`，但 `preparedContext` 仍直接沿用原始 `context`；实际 `askWithPresetContractRetry()` 读取的是 `preparedContext.semanticContext`。
- 影响：Project hint、Case handoff 以及后续“继续 / 它 / 第二个”请求可能缺少 `semanticContext.activeSubjects`。页面看起来保留了主体，但网络请求没有携带同等强度的主体上下文，容易造成上下文丢失或错误续接。
- 建议修复：在构造 `preparedContext` 时合并一次性 semantic context，并为 Project/Case 首轮、显式主体切换、单主体续接分别增加请求体断言；修复后再执行 UI 双视口测试。

### P2：公开内容响应缺少 `Cache-Control: no-store`

- 实测：打包 JAR `GET /api/v1/public-content` 返回 200，`X-Request-Id` 与 `X-Trace-Id` 存在，但 `Cache-Control` 为空。
- 影响：与现有 API/UI 驱动器和发布契约的 no-store 预期不一致；公共内容本身不是私密问答，但缓存策略漂移会使发布版本、审计边界和恢复行为难以预测。
- 状态：已复核为真实运行时观察，尚未修改生产代码；需由后续修复任务确认是否应在 Controller/全局响应策略统一补齐。

## 设计缺陷与交互体感问题

1. “不支持”与“请澄清”的视觉语义边界不够清楚。当前 `BOUNDARY/NOT_SUPPORTED` 仍可能带来源目录，访客难以区分“问题不在范围内”和“系统没有理解问题”。
2. 页面 hint 是可见状态，但没有在首轮 wire contract 中保证同等优先级；这是可观察状态与真实请求状态不一致的体感问题。
3. 失败、超时、取消虽然在新增内存态 driver 中被过滤出 history，但真实 UI 的刷新恢复依赖 Context Store；Context Store 关闭时用户只能得到页内降级，恢复卡与“清除已确认”的路径无法形成完整闭环。
4. 动态 ACTIVE preset 的真实调用覆盖依赖运行时返回的公共快照；当前测试目录能展开它们，但既有 Eval manifest 不允许直接追加行为 holdout，治理层需要独立的 audit manifest 或兼容扩展点。
5. L4 真实 Provider 验证目前证明的是接入、策略和基本响应契约，不能把单个 Provider 的可用性宣传为“所有多任务、澄清、恢复和上下文路径已被模型验证”。

## 阻塞与未执行路径

| 路径 | 状态 | 原因 |
|---|---|---|
| L1 PostgreSQL Context Store | BLOCKED/INCOMPLETE | 本轮以 `ContextMode=DISABLED` 运行；未宣称 AVAILABLE、resume token、reload recovery 或 server clear 通过 |
| L2 Hybrid retrieval | INCOMPLETE | 未提供并启用批准的本地 BGE 模型目录 |
| L3 Fake Provider | PASS | 5/5 本地对抗测试通过，无外网调用 |
| L4 DeepSeek Provider | PROVIDER PASS / BEHAVIOR FAIL | 接入与 live-probe 通过；`112233` 等行为 Oracle 失败仍按产品缺陷记录 |
| Desktop/Mobile UI 真实执行 | BLOCKED | 当前环境缺少 `chrome-headless-shell-1228`；仅完成配置解析、测试发现与静态类型检查 |

## 后续修复路线

### 第一批：澄清与来源边界（P1）

1. 先为 `112233`、空白、Emoji、未知主体建立最小后端失败用例。
2. 调整噪声分类与 response mapper，保证 `NEEDS_CLARIFICATION + 无主体 + 无 Evidence` 的闭集契约。
3. 重新执行 L0/L4 API corpus，再开启 UI 双视口回归。

### 第二批：前端语义上下文（P1）

1. 修复 `preparedContext.semanticContext` 合并点。
2. 断言 Project hint、Case hint、显式切换和单主体续接的实际请求 JSON。
3. 验证取消/竞态/刷新后旧 continuation 不再泄漏。

### 第三批：发布契约与环境（P2）

1. 确认 `/api/v1/public-content` 的 `no-store` 责任层并补运行时回归。
2. 用独立 audit manifest 承载行为 holdout，避免破坏 PhaseZero 4-file 基线。
3. 提供 Java 21、PostgreSQL、Hybrid 模型目录和 Playwright 浏览器后，再补跑 L1/L2/UI 未执行路径。

## 重跑入口

```powershell
# 本地确定性测试
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
mvn.cmd -f backend/pom.xml test

# 行为纯函数测试
npm.cmd --prefix frontend exec vitest -- --config vitest.behavior.config.ts --run

# 分层行为运行器；L4 必须显式授权并使用仓库外 Secret 文件
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-agent-behavior-audit.ps1 -Lane L0,L1,L2,L3
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-agent-behavior-audit.ps1 -Lane L4 -RequireLiveProvider -ProviderSecretFile <仓库外临时文件>
```
