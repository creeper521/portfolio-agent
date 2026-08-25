# 四角色会话切换前端实施计划
<!-- DOCUMENT_STATUS: ACTIVE -->

> **For agentic workers:** 按任务逐项执行，步骤使用 checkbox（`- [ ]`）跟踪；行为变更一律 TDD（先 RED 后 GREEN）。

**Goal:** 在已批准的行为基础计划 Task 4/5（前端状态接缝与首页角色冻结）与已批准的前端 UI 设计（布局 A）基础上，完成四角色会话切换的全部前端实现：角色新会话原子操作、推荐问题角色过滤、首页 pending 角色锁定、composer 角色行 + 切换浮层、会话行角色标签，以及组件测试与当前环境可执行的门禁。

**Architecture:** `AgentSession.role` 是会话角色的唯一权威；切换角色 = 经 `useLocalSessions.switchAudienceRole()` 创建全新本地会话，绝不原位改写 `role`。UI 只调用 `AgentWorkspace.switchAudienceRole()` 包装器（解析 projectSlug、成功后清空活跃 ResumeToken 槽位）。角色展示映射归 agent feature 自有（复制首页文案，不跨 feature 引用）。不新增状态通道、API 调用、浏览器存储或 URL 参数。

**Tech Stack:** Vue 3、TypeScript 5.8、Vitest、Vue Test Utils；门禁含 `npm test`、`run check`、`build`。

## Global Constraints

- 权威设计：`../specs/2026-08-25-audience-role-session-switching-design.md`（APPROVED，LEVEL_3）。
- 行为基础：`2026-08-25-audience-role-behavior-foundation.md` 的 Task 4/5（本计划执行其前端部分；后端 Task 1–3 与收尾 Task 6 不在本计划内）。
- UI 设计：`../specs/2026-08-25-audience-role-switching-frontend-ui-design.md`（APPROVED，布局 A，D-AR-1..9）。
- 生产源码任何位置不得出现对 `AgentSession.role` 的赋值（UI 设计 §9.2 禁止清单）；一次用户动作恰好一次接缝调用。
- 新角色会话只继承当前 Project/Case 上下文；消息、草稿、模型选择、Conversation、ResumeToken、Discussion、pending、失败、通知、seed 全部留在旧会话。
- 同角色选择是 no-op；浮层内不渲染可点的当前角色项（D-AR-3）。
- 四角色共用同一视觉编码（无角色配色/图标，D-AR-7）；不新增 `AgentThreadNotice` kind、不弹 toast、不产生聊天消息（D-AR-5）。
- 首页 pending 期间角色按钮带真实 `disabled`；答案渲染与 handoff 使用提交时冻结的角色快照。
- 本计划不关闭 A2-53：真实 Provider 16 样本矩阵与桌面/移动 packaged-JAR Browser Exit Gate 仍为延期 Exit Gate。

## File Map

- `frontend/src/features/agent/composables/useLocalSessions.ts`：`isMeaningfulSession` 保留规则（USER 消息或非空草稿）+ `switchAudienceRole`。
- `frontend/src/features/agent/components/AgentWorkspace.vue`：`switchAudienceRole` 包装器、Preset 角色过滤、角色行 + 浮层 + aria-live 宣布 + `handleRoleSwitch`、向 rail 传 `pendingIds`。
- `frontend/src/features/agent/model/audienceRolePresentation.ts`：新建，闭合展示映射（shortLabel/label/description）。
- `frontend/src/features/agent/components/LocalSessionRail.vue`：`pendingIds` prop、角色短标签、「· 生成中 / · 草稿」后缀、aria-label 扩展。
- `frontend/src/features/audience/model/audienceTypes.ts`：`HomeAnswerState.role`。
- `frontend/src/features/audience/components/AudienceDialogue.vue`：pending 锁定 + 快照渲染。
- 测试：`useLocalSessions.test.ts`、`AgentWorkspace.test.ts`、`audienceRolePresentation.test.ts`（新建）、`LocalSessionRail.test.ts`、`AudienceDialogue.test.ts`。
- 文档：`docs/11-项目演进日志.md`（本计划只动演进日志；`docs/08` 的四角色条目由行为基础计划 Task 6 在后端任务完成后统一记录，避免与并行未提交工作混叠）。

---

### Task 1: 会话保留规则与 switchAudienceRole 接缝（行为基础 Task 4 Step 1–3）

**Files:**
- Modify: `frontend/src/features/agent/composables/useLocalSessions.ts`
- Modify: `frontend/src/features/agent/composables/useLocalSessions.test.ts`

- [x] **Step 1: RED — 保留规则测试**：经公开 composable 断言（不导出谓词）：有 USER 消息或非空草稿的旧会话在 `createSession()` 后保留于 `historySessions` 且可再选中；空白草稿（`'   '`）会话被清理；纯草稿会话不因切换而丢失草稿。
- [x] **Step 2: RED — switchAudienceRole 测试**：不同角色创建新会话（角色 = 目标值、仅继承传入 projectSlug、无消息/草稿/模型偏好/凭证）并激活；同角色返回 `null` 不创建；旧会话消息与草稿原样保留。
- [x] **Step 3: 运行确认 RED**：`npm.cmd --prefix frontend test -- useLocalSessions.test.ts --run`。
- [x] **Step 4: GREEN — 实现**：集中 `isMeaningfulSession` 谓词并用于 `historySessions` 与 `createSession()` 清理过滤；新增 `switchAudienceRole(targetRole, projectSlug)`（当前会话为空或同角色 → `null`；否则 `createSession({ role, projectSlug })`）。
- [x] **Step 5: 测试通过后提交**：`feat(frontend): 建立角色新会话状态语义`（仅暂存本任务两文件）。

### Task 2: Workspace 包装器、Preset 角色过滤与晚到响应回归（行为基础 Task 4 Step 4–7）

**Files:**
- Modify: `frontend/src/features/agent/components/AgentWorkspace.vue`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.test.ts`

- [x] **Step 1: RED — 状态语义测试**：经组件挂载断言：不同角色切换后新会话只带当前 Case/Project 上下文与目标角色（消息/草稿/模型偏好/凭证/通知为空）；旧会话草稿、pending、失败与角色保留；pending 中切换不取消旧请求、旧请求结果只写回旧会话；标签页两路 pending 上限不被切换破坏；无 handoff 直接进入默认 INTERVIEWER、home seed 另立会话且角色冻结。
- [x] **Step 2: RED — Preset 角色过滤测试（合成 fixtures）**：合成 `placements`/`audiences` 不同的预设，断言 fallback chips 要求 `AGENT` placement + 当前会话角色、保持快照顺序、最多 3 条、不足不跨角色补足；Case `suggestedQuestions` 优先且角色中立。
- [x] **Step 3: RED — 晚到响应 ResumeToken 回归**：会话 A pending → 切到 HR 得新会话 B → A 迟到返回 `{conversationId, resumeToken}` → 断言 A 持有 token、B 无 token、`sessionStorage` 唯一槽位为空；选回 A 后槽位镜像该 token（不新增第二个存储键）。
- [x] **Step 4: GREEN — 实现**：Workspace 包装器 `switchAudienceRole(targetRole)`（projectSlug 解析顺序 activeCase → 会话 → initialProject；创建成功后 `resume.clearActiveToken()`；不取消旧 pending、不改写 `current.role`）；`suggestionChips` fallback 过滤加 `preset.audiences.includes(activeSession.role ?? props.initialRole)`。
- [x] **Step 5: 运行测试与类型检查通过**：`npm.cmd --prefix frontend test -- AgentWorkspace.test.ts --run` + `run check`。

### Task 3: 首页 pending 角色冻结（行为基础 Task 5）

**Files:**
- Modify: `frontend/src/features/audience/model/audienceTypes.ts`
- Modify: `frontend/src/features/audience/components/AudienceDialogue.vue`
- Modify: `frontend/src/features/audience/components/AudienceDialogue.test.ts`

- [x] **Step 1: RED**：延迟 `submitAgentTurn`：选 MENTOR 提交 → 四个角色按钮均有真实 `disabled`，点 HR 无效；resolve 后 LightAnswerPanel 角色与 handoff 种子角色仍为 MENTOR（不读响应式 `selectedRole`）。
- [x] **Step 2: GREEN**：`HomeAnswerState` 增加 `role`（取 `surfaceContext.audienceRole`）；`chooseRole` 在 `pending` 时立即返回；角色按钮 `:disabled="pending"`；`<LightAnswerPanel :role="answer.role">`。
- [x] **Step 3: 测试与类型检查通过后提交**：`fix(frontend): 冻结首页提交角色快照`（仅暂存本任务三文件）。

### Task 4: 角色展示映射（UI 设计 §8）

**Files:**
- Create: `frontend/src/features/agent/model/audienceRolePresentation.ts`
- Create: `frontend/src/features/agent/model/audienceRolePresentation.test.ts`

- [x] **Step 1: RED**：断言闭合四项、顺序 = 枚举顺序、`presentationOf` 覆盖全部角色；文案取自原型定稿（shortLabel 面试官/导师/HR/访客；label 技术面试官/未来导师/HR / 招聘者/普通访客）。
- [x] **Step 2: GREEN**：实现纯常量 + 查找函数；不含颜色、图标或排序权重；不引用首页 `audienceProfiles`（D-AR-8）。
- [x] **Step 3: 测试通过后提交**：`feat(frontend): 增加会话视角展示映射`。

### Task 5: 角色行、切换浮层与宣布区（UI 设计 §2/§3/§5/§9）

**Files:**
- Modify: `frontend/src/features/agent/components/AgentWorkspace.vue`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.test.ts`

- [x] **Step 1: RED — 浮层结构测试**：触发钮（`role-switch-trigger`，`aria-haspopup="dialog"` + `aria-expanded`）打开浮层；浮层恰 3 个 `role-option` 且不含当前角色；当前行为 `role-current` + `aria-current="true"` 非动作；动作项可访问名「以 {角色名} 视角开启新会话」；「新会话 ›」尾标 `aria-hidden`。
- [x] **Step 2: RED — 交互测试**：点动作项 → 活跃会话角色 = 目标（经接缝）、status 区宣布「已切换到{角色名}视角，开始新会话」、焦点到输入框；切换失败（如令 id 生成抛错）→ 浮层保持打开、`role-switch-error` alert 出现、活跃会话不变；Esc 关闭并还焦触发钮；提示行随草稿/pending 增减对应句子；pending 满两路时触发钮仍可点（切换不占配额）。
- [x] **Step 3: GREEN — 实现**：composer 首行角色行（eyebrow + serif 角色名 + 截断描述 + 触发钮）；向上浮层（`role="dialog"`、提示行 `role="note"`、当前行、3 动作钮、错误行 `role="alert"`）；方向键循环、Esc/外点/再点触发钮关闭还焦、focusout 离开即关闭；常驻 sr-only `role="status"` 宣布区；`handleRoleSwitch` 恰好一次接缝调用。窄屏 <720px 两行布局。
- [x] **Step 4: 运行测试与类型检查通过。**

### Task 6: 会话行角色标签（UI 设计 §4）

**Files:**
- Modify: `frontend/src/features/agent/components/LocalSessionRail.vue`
- Modify: `frontend/src/features/agent/components/LocalSessionRail.test.ts`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.test.ts`（pendingIds 集成断言）

- [x] **Step 1: RED**：行内标题前 9px mono 角色短标签（aria-hidden，`data-session-role="{ROLE}"`）；aria-label = `会话（{短名}视角{，回答生成中|，含草稿}）：{标题}`（`titleDetail` 的「（问题：…）」后缀拼接在标题后）；pending 行加「· 生成中」+ `data-session-pending`；无 USER 消息但非空草稿的行加「· 草稿」；重命名/删除/清空交互不变。
- [x] **Step 2: GREEN**：`LocalSessionRail` 增加 `pendingIds?: readonly string[]`；Workspace 传 `[...pendingTurns.keys()]`；不放宽 `historySessions` 过滤（新空会话不入列，§4.3）。
- [x] **Step 3: 测试与类型检查通过后提交**：`feat(frontend): 落地角色切换入口与会话角色标签`（含 Task 5 文件）。

### Task 7: 门禁、零写路径扫描与文档

- [x] **Step 1: 全量前端门禁**：`npm.cmd --prefix frontend test -- --run`、`npm.cmd --prefix frontend run check`、`npm.cmd --prefix frontend run build`。
- [x] **Step 2: role 零写路径扫描**：`rg -n "\.role\s*=[^=]" frontend/src` 并人工复核无生产赋值路径（对象字面量初始化除外）；确认无 `session.role =` / `activeSession.value.role =` / role 字段 `v-model`。
- [x] **Step 3: 文档**：`docs/11-项目演进日志.md` 增加一条有日期事件（链接本计划与 UI 设计），只记录已证明行为；不写 A2-53 完成。
- [x] **Step 4: 提交**：`docs(frontend): 记录四角色会话切换界面证据`。

## Deferred Exit Gate（不在本计划内）

1. 桌面/移动 packaged-JAR Browser 验收（UI 设计 §12.4：角色行、切换隔离、同角色 no-op、pending 写回、首页锁定、console 无 warning）；
2. 真实 Provider 16 样本矩阵（上级设计 §16.3）；
3. A2-53 关闭与 `docs/agent-architecture-status.json` 证据更新。

环境或授权不满足时按 `WAIVED`/`NOT_RUN` 记录原因，保持 `IN_PROGRESS`；不得以组件测试冒充 Provider/Browser 证据。
