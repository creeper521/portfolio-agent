# 四角色会话切换前端 UI 与交互设计
<!-- DOCUMENT_STATUS: APPROVED -->

> **日期：** 2026-08-25
> **状态：** 已批准（布局 A 与整体交互方向经用户确认；批准后另立实施计划执行）
> **交互原型：** [prototypes/audience-role/role-switch-demo.html](prototypes/audience-role/role-switch-demo.html)（布局 A 定稿版，含 6 个可操作场景与已否决的布局 D 对照、手机 392px 模式；场景可用 URL 参数 `?s=switch|draft|pending|chain|home&mobile=1` 直达）
> **上级设计：** [Agent 四角色会话切换与回答适配设计](2026-08-25-audience-role-session-switching-design.md)（APPROVED，LEVEL_3）
> **行为基础：** [四角色行为基础实施计划](../plans/2026-08-25-audience-role-behavior-foundation.md)（ACTIVE；其 Task 4 冻结 `switchAudienceRole` 状态接缝，Task 5 冻结首页 pending 锁定）
> **本文职责：** 上级设计 §6.5/§17 与行为基础计划 Task 6 Step 4 指定"角色入口、标签位置、图标、颜色、文案与移动端交互由 Frontend Agent 设计"。本文只冻结前端 UI、交互状态机、文案与对 `switchAudienceRole` 的调用方式；不定义任何共享合同字段，不改变后端语义。
> **不在范围：** 后端实现、`AudienceRole` 合同、`AgentSession` 字段、自然语言角色切换识别、角色化颜色数据合同、统一 AudiencePolicy、首页布局改版。

## 0. 事实基线（本文依赖的现状）

以下均已由代码核实，是本设计的落点：

- `AgentWorkspace.vue` 的 composer（`.workspace-composer`）自上而下为：标签页 pending 上限提示 → 讨论摘要卡 → `.workspace-composer__top`（ModelSelector + 建议 chips）→ 输入表单 → 隐私提示。
- `ModelSelector.vue` 已确立"胶囊触发钮 + 向上弹出浮层"交互先例（D-MS-1）：`aria-haspopup`、方向键漫游、Esc 关闭并还焦、document 点击外关、pending 锁定附文字说明。
- `LocalSessionRail.vue` 会话行为 `article`（标题按钮 + `···` 管理菜单），无角色信息；组件只接收 `sessions / activeId`。
- `useLocalSessions.historySessions`（经行为基础计划 Task 4 后）= 有 USER 消息或非空草稿的会话；**全新空会话在产生消息/草稿前不出现在会话列表**。
- 断点：≤959.98px 会话栏变抽屉、≤1279.98px 来源栏变抽屉；composer 顶栏 `flex-wrap` 换行。
- 首页 `AudienceDialogue.vue` 角色按钮组为 2×2 大按钮（`aria-pressed`）；pending 锁定与角色快照渲染由行为基础计划 Task 5 冻结，本文不复述其实现，只在 §6 对齐交互口径。
- 设计令牌全部来自 `frontend/src/app/styles/tokens.css`（暖纸底、`--ink` 墨色、`--red` 牛血红点缀、mono 眉标、serif 展示字），本设计不新增颜色。

## 1. 决策表

| 决策 | 内容 | 状态 |
|---|---|---|
| D-AR-1 | 角色入口采用**布局 A：composer 首行"会话视角"行**（角色标识 + 「切换视角」触发钮 + 向上浮层）；布局 B（会话轨顶部）、布局 C（对话区顶部通栏）、布局 D（常显四按钮分段控件）否决 | 已批准（2026-08-25 布局 A 经用户确认） |
| D-AR-2 | 切换为不同角色 = **立即执行、无确认对话框**：单击浮层内角色项即调用 `switchAudienceRole(target)`；只有执行失败才在浮层内提示 | 已批准（2026-08-25 布局 A 经用户确认） |
| D-AR-3 | 当前角色在浮层中渲染为**非动作行**（`aria-current="true"` + 「当前」徽标，不可点击），并用提示行引导"同视角重新开始请用『新对话』"；满足上级 §6.4 的"隐藏/置灰/提示"三选一 | 已批准（2026-08-25 布局 A 经用户确认） |
| D-AR-4 | 历史会话行以**标题前 mono 短标签**识别创建角色（面试官/导师/HR/访客）；无消息但非空草稿的行追加「· 草稿」，有 pending 的行追加「· 生成中」；角色纳入行按钮的 aria-label | 已批准（2026-08-25 布局 A 经用户确认） |
| D-AR-5 | 切换反馈 = **aria-live 宣布 + 焦点回到输入框**；不产生任何聊天消息、不新增 `AgentThreadNotice` kind、不弹出 toast | 已批准（2026-08-25 布局 A 经用户确认） |
| D-AR-6 | 浮层语义用 **dialog + 普通按钮**（每个动作 = "以 X 视角开启新会话"），不用 listbox/option：选择动作创建新会话，不是在本会话内"选中"一个值，listbox 语义会误导辅助技术 | 已批准（2026-08-25 布局 A 经用户确认） |
| D-AR-7 | 四角色**共用同一视觉编码**（同一墨色标签/徽标样式），不做角色专属颜色或图标；角色区分只靠文字 | 已批准（2026-08-25 布局 A 经用户确认） |
| D-AR-8 | 角色文案由 Agent feature 自有的展示映射承载（`audienceRolePresentation.ts`），**不跨 feature 引用**首页 `audienceProfiles`；标签文案允许两处独立演化 | 已批准（2026-08-25 布局 A 经用户确认） |
| D-AR-9 | UI 只调用 `AgentWorkspace.switchAudienceRole(targetRole)` 包装器（行为基础计划 Task 4 Step 5）；**任何 UI 代码不得读写 `session.role` 赋值**，不做乐观本地标记 | 已批准（2026-08-25 布局 A 经用户确认） |

## 2. 当前会话区域的角色入口（布局 A）

### 2.1 位置与结构

角色行是 `.workspace-composer` 的**第一个元素**（位于标签页 pending 提示、讨论卡与 `.workspace-composer__top` 之前），作为活跃会话的"身份行"：

```text
桌面（≥720px，单行）：
┌────────────────────────────────────────────────────────────────┐
│ AUDIENCE·会话视角  技术面试官 — 侧重技术方案、取舍和实现细节…  [切换视角 ▾] │
└────────────────────────────────────────────────────────────────┘
  mono 10px faint    serif 15px ink   mono 10.5px muted（省略号截断）  mono 11px 胶囊钮
```

```text
窄屏（<720px，两行）：
┌──────────────────────────────────┐
│ AUDIENCE·会话视角      [切换视角 ▾] │
│ 技术面试官 — 侧重技术方案、取舍…    │
└──────────────────────────────────┘
```

- 左块 `flex: 1; min-width: 0`，描述单行省略号截断（完整文案在浮层当前角色行可见）；
- 触发钮与 ModelSelector 触发钮同规格：`min-height: 40px`、`padding 6px 14px`、`border 1px --workspace-rule`、圆角 999px、底 `--paper-hi`、字 11px `--mono`，hover 边框转 `--workspace-accent`，`focus-visible` 2px 描边；
- 角色名用 `--serif`（15px/600）：整个工作台是 mono/sans 密度型界面，会话视角这一行以衬线字发声，与首页「选择你的视角。」的衬线主视觉同源——这是本设计唯一一处刻意的视觉签名，其余全部沿用既有控件语言；
- 角色行下方以 `--workspace-rule` 细分割线与 `.workspace-composer__top` 分隔；
- 桌面新增垂直预算约 40px，窄屏约 60px；建议 chips 本就 `flex-wrap`，不挤占输入区。

### 2.2 位置理由与被拒布局

- **布局 B（会话轨顶部）否决**：角色属于"这场对话"而非"会话列表"；且 ≤959.98px 会话轨收进抽屉，角色入口将随抽屉隐藏。
- **布局 C（对话区顶部通栏）否决**：三栏布局下对话区顶部已有通知流/失败投影的动态内容，再加常驻通栏会持续下压消息流；角色与输入动作相邻更利于理解"视角影响接下来的回答"。
- **布局 D（常显四按钮分段控件）否决**：与 ModelSelector、3 条建议 chips 同行在 360px 宽度下必然溢出换行；且每个按钮都是"新建会话"级动作，常显四钮大幅提高误触率。首页的 2×2 大按钮网格是"首次选择"心智，工作台是"运行中切换"心智，不复用。

## 3. 角色浮层（RoleSwitchPopover）

### 3.1 结构

点击「切换视角」在角色行上方弹出浮层（`position: absolute; bottom: calc(100% + 8px)`，左对齐，宽 `min(420px, 100%)`，最大高 `60vh` 内滚动；视觉规格与 ModelSelector 浮层一致：`--paper-hi` 底、`--workspace-rule` 边框、`--agent-radius-md` 圆角、`0 18px 44px` 投影、z-index 30）：

```text
╔══════════════════════════════════════════════════════╗
║ （提示行，见 3.2，10px mono --muted）                    ║
║ ● 当前  技术面试官                                     ║
║         侧重技术方案、取舍和实现细节，每个结论标注状态与证据 ║   ← 非动作行（aria-current）
║ ──────────────────────────────────────────────────── ║
║ 未来导师                                    [新会话 ›]  ║   ← 动作按钮（其余三角色）
║         回答侧重工作过程、复盘质量和能力如何在连续迭代中形成  ║
║ HR / 招聘者                                 [新会话 ›]  ║
║         回答侧重经历概况、职责范围、交付状态和贡献边界       ║
║ 普通访客                                    [新会话 ›]  ║
║         使用更通俗的语言解释项目做了什么，同时保留事实边界    ║
╚══════════════════════════════════════════════════════╝
```

- 「当前」徽标复用 `model-selector__option-badge` 的样式语言（9px mono、红描边胶囊）；
- 「新会话 ›」尾标是动作语义的关键提示，9px mono `--faint`，`aria-hidden`（语义并入按钮可访问名）；
- 动作行主名 13px/600 sans、描述 10px mono `--faint`，行最小高 52px（触屏双行可点）；
- 动作按钮的可访问名 = **「以 {角色名} 视角开启新会话」**（`aria-label`），可见文本保持角色名 + 描述。

### 3.2 提示行（随当前会话状态计算，只读既有状态）

| 当前会话状态 | 追加提示（显示在浮层首行，可多行并存） |
|---|---|
| 始终 | 切换视角会开启新会话；当前会话自动保留在列表。同视角重新开始请用「新对话」。 |
| `draft` 非空 | 当前会话有未发送草稿，草稿将保留在原会话。 |
| 该会话存在 pending Turn | 当前会话的回答仍在生成，结果只写回原会话。 |

提示行是纯展示文本（`role="note"` 或普通段落），由 `activeSession.draft` 与 `activePending` 计算，不新增任何状态通道。

### 3.3 同角色（no-op）与失败

- 当前角色只出现在非动作行，浮层内**不存在**可点的同角色按钮——同角色 no-op 由"不可点 + 提示行引导『新对话』"表达（上级 §6.4 三选一取"置灰 + 提示"）；
- `switchAudienceRole` 返回 `false` 或抛错时（菜单项已排除同角色，实际只剩创建失败）：浮层保持打开，底部出现 `role="alert"` 错误行「未能开启新会话，请稍后重试。」（10.5px mono `--red`），当前会话与角色保持不变（上级 §15）；
- 触发钮本身**没有任何禁用态**：完整 Agent 允许在 pending、失败、澄清卡打开时切换角色（上级 §7.1）；这与 ModelSelector 的 pending 锁定形成有意的对照——模型是"本轮设置"，pending 中不可换；角色是"会话身份"，切换即新会话，不触碰本轮。

### 3.4 键盘、焦点与 ARIA（具体规格）

触发钮与浮层的属性按"非模态 dialog"给出（D-AR-6）：

```html
<button aria-haspopup="dialog" aria-expanded="…" aria-controls="role-switch-popover"
        data-testid="role-switch-trigger">切换视角 ▾</button>
<div role="dialog" aria-label="切换会话视角" id="role-switch-popover" …>
  <p role="note">…提示行…</p>
  <p aria-current="true" data-testid="role-current">● 当前 技术面试官 …</p>
  <button data-testid="role-option" data-role="MENTOR"
          aria-label="以未来导师视角开启新会话">未来导师 …</button>
  <!-- HR / GUEST 同构 -->
</div>
```

- **打开**：触发钮 Enter/Space/单击打开；焦点移到浮层内**第一个动作按钮**（非动作的当前行不聚焦）；
- **方向键**：ArrowUp/ArrowDown 在动作按钮间循环移动（与 ModelSelector 相同的手感），preventDefault；
- **确认**：聚焦按钮的 Enter/Space 原生触发 click → `handleRoleSwitch`；
- **关闭**：Esc、点击浮层外（document click，同 ModelSelector `onDocumentClick` 模式）、再次点击触发钮；三种路径都把焦点还给触发钮；
- **Tab**：非模态浮层不做焦点陷阱，Tab 沿 3 个动作按钮自然流动后离开浮层（离开视作关闭，焦点还触发钮）；Shift+Tab 同理；
- **宣布区**：`role="status"`（`aria-live="polite"`）的视觉隐藏段落**常驻 DOM**（`.sr-only` 风格），宣布时只改文本——动态插入的 live 区在部分屏幕阅读器下不播报；
- 视觉隐藏当前行的 `aria-current="true"` 与「当前」徽标并用；「新会话 ›」尾标 `aria-hidden`（语义已在按钮可访问名中）。

## 4. 历史会话行的角色识别（LocalSessionRail）

### 4.1 角色短标签

```text
┌────────────────────────────────────┐
│ [面试官] SQL 审计工具的完整迭代      ··· │   ← 标题前 9px mono 胶囊标签
│ [导师]   如何复盘这个项目 · 生成中   ··· │   ← pending 行追加「· 生成中」
│ [HR]     用一分钟介绍这段实习 · 草稿  ··· │   ← 无消息但有草稿的行追加「· 草稿」
└────────────────────────────────────┘
```

- 标签取自 §8 的展示映射短名（面试官/导师/HR/访客），样式同「当前」/「新会话」徽标语言（9px mono、发丝描边、`--workspace-text-secondary` 墨色、内边距 `1px 7px`），**四角色同款**，不做角色配色（D-AR-7）；
- 标签固定最小宽（约 40px）保证标题列左对齐；标签本身 `aria-hidden`，角色信息并入行按钮的可访问名；
- 行按钮 aria-label 扩展为：`会话（{短名}视角{，回答生成中|，含草稿}）：{标题}`；现有 `titleDetail` 的"（问题：…）"后缀逻辑维持不变，拼接在标题之后；`title` 提示维持现状；
- 会话行重命名、删除、清空等既有交互不变。

### 4.2 组件接口变化

`LocalSessionRail` 仅新增一个可选 prop（渲染纯展示，不改变任何事件）：

```ts
defineProps<{
  sessions: AgentSession[]     // 现有
  activeId: string             // 现有
  pendingIds?: readonly string[]  // 新增：存在 pending Turn 的 sessionId 列表
}>()
```

Workspace 侧以 computed `[...pendingTurns.value.keys()]` 传入。「生成中」后缀的必要性：切换角色后旧会话的进行中回答不再出现在当前视图，会话列表是用户找回它的唯一线索，必须可见其仍在生成（上级 §7.1"返回旧会话时可以看到该请求结果"）。

### 4.3 新空会话不在列表（现状确认，非遗留缺陷）

全新角色会话在产生首条消息或草稿前**不出现在** `historySessions`（与现有「新对话」按钮行为一致）。切换后用户看到的是：对话区已进入新视角的空会话，列表里旧会话保留。行为基础计划已把"非空草稿会话保留"纳入 `historySessions`，因此一旦在新会话输入草稿它即入列。实现时**不得**为让空会话入列而放宽该过滤。

## 5. 相同角色与不同角色的交互反馈

| 场景 | 反馈 |
|---|---|
| 打开浮层 | 焦点移入浮层首个可聚焦元素；触发钮 `aria-expanded="true"` |
| 点击其他角色（成功） | 浮层关闭；新会话成为活跃会话；`role="status"` 视觉隐藏区宣布「已切换到{角色名}视角，开始新会话」；焦点移到问题输入框（复用 `focusComposer()`，保持打字流） |
| 点击其他角色（失败） | 浮层保持打开；`role="alert"` 错误行提示重试；无任何状态改变 |
| 同角色 | 浮层内无可点的同角色项（§3.3）；提示行引导「新对话」 |
| Esc / 点击浮层外 / 再次点触发钮 | 关闭浮层，焦点还给触发钮 |
| 切换后旧会话 | 消息、草稿、pending、失败、凭证全部保留在旧会话；旧 pending 完成后只写回旧会话，列表行「生成中」消失 |

**明确不做**（上级 §6.3/§6.4 + 反做重约束）：

- 不在对话流插入"已切换角色"消息（聊天消息被上级设计禁止）；
- 不新增 `AgentThreadNotice` kind（通知流是给模型/请求事件的，角色切换结果用 aria-live 已足够，避免第五种通知类型）；
- 不弹 toast/模态庆祝；不做角色切换动效（`prefers-reduced-motion` 下浮层本就无过渡）。

## 6. 草稿与 pending 下的切换表现

### 6.1 完整 Agent（允许随时切换）

| 当前会话状态 | 切换表现 |
|---|---|
| 空会话（无消息无草稿） | 直接切换；旧空会话被 `createSession` 清理规则丢弃（上级 §6.3.7），不产生空壳历史 |
| 有非空草稿 | 允许切换；草稿留在旧会话不复制（上级 §6.3.6）；浮层提示行明示（§3.2）；旧会话行出现「· 草稿」 |
| 有 pending Turn | 允许切换，不取消不重发（行为基础计划 Task 4 明确"不 cancel 旧 pending"）；提示行明示"结果只写回原会话"；旧会话行出现「· 生成中」 |
| 有失败/澄清卡 | 允许切换；失败视图与澄清卡归属旧会话，返回旧会话时原样可见 |
| 标签页 pending 已满（两条） | 仍允许切换：切换不发送 Turn，不占用并发配额（上级 §7.1） |

### 6.2 首页轻对话（pending 锁定，行为基础计划 Task 5 已冻结，此处只对齐口径）

- pending 期间四个角色按钮带**真实 `disabled` 属性**（非仅视觉置灰），可访问名不变；
- 答案面板与「进入 Agent」handoff 一律使用**提交时冻结的角色快照**（`answer.role`），不读响应式 `selectedRole`；
- 首页与完整 Agent 的锁定差异是产品语义差异（上级 §7.1 vs §7.2），UI 通过两处一致的"pending + 文字说明"模式表达，不再造第三种模式。

## 7. 桌面端与移动端布局

### 7.1 断点行为

| 宽度 | 角色行 | 浮层 | 会话列表 |
|---|---|---|---|
| ≥960px | 单行（eyebrow + serif 角色名 + 截断描述 + 触发钮） | 宽 `min(420px, 100%)`，向上弹出 | 侧栏常显，行内角色标签 |
| 720–959.98px | 同上（描述截断更短） | 同上 | 侧栏为抽屉（现状） |
| <720px | 两行：首行 eyebrow + 触发钮，次行角色名 + 截断描述 | 同上（宽度受 composer 内边距约束，向上弹出） | 抽屉内行加「· 生成中/草稿」后缀 |

### 7.2 移动端要点

- 触发钮 `min-height: 40px`，配合角色行垂直内边距形成 ≥44px 的连续触达高度；浮层动作行 `min-height: 52px`（双行内容）；
- 浮层在窄屏不使用底部工作表/scrim——工作台的 scrim+抽屉模式保留给两个大侧栏，4 项浮层不值得模态重量（被放弃方案 §11）；
- `@media (hover: none)` 下不依赖 hover 呈现任何信息（提示行、徽标常显）；
- 角色行位于输入区顶部，移动端输入法弹起时浮层向上弹出不会被键盘遮挡。

## 8. 角色展示映射（agent feature 自有）

新建 `frontend/src/features/agent/model/audienceRolePresentation.ts`（纯常量 + 查找函数，闭合四项，顺序 = 枚举顺序）：

```ts
export interface AudienceRolePresentation {
  role: AudienceRole
  shortLabel: string   // 面试官 / 导师 / HR / 访客 —— 会话列表标签
  label: string        // 技术面试官 / 未来导师 / HR / 招聘者 / 普通访客 —— 角色行与浮层主名
  description: string  // 一句话侧重描述 —— 角色行截断显示、浮层完整显示
}
export const audienceRolePresentations: readonly AudienceRolePresentation[]
export function presentationOf(role: AudienceRole): AudienceRolePresentation
```

- 首发文案直接沿用首页 `audienceProfiles` 的既有中文文案（已随首页上线，用户已见过），但**复制而非引用**（D-AR-8）：两个 feature 的文案节奏允许独立演化，避免为四个字符串制造跨 feature 合同；
- 不含颜色、图标、排序权重——那些分别由既有令牌、无图标决策与后端 Facet 权威拥有。

## 9. 对 `switchAudienceRole` 的调用契约（D-AR-9）

### 9.1 依赖与调用点

行为基础计划 Task 4 Step 5 冻结两个函数，UI 全部经它们行动：

```ts
// useLocalSessions.ts —— 状态接缝（Task 4 提供）
switchAudienceRole(targetRole: AudienceRole, projectSlug: string | null): AgentSession | null
  // 当前会话为空或同角色 → null；否则 createSession({ role, projectSlug }) 并激活

// AgentWorkspace.vue —— Workspace 包装器（Task 4 提供）
switchAudienceRole(targetRole: AudienceRole): boolean
  // 解析 projectSlug（activeCase → 会话项目 → initialProject）、
  // 创建成功后清空活跃 ResumeToken 槽位、返回新会话是否已激活
```

本设计新增的 UI 处理器（实现时落在 `AgentWorkspace.vue`）：

```ts
function handleRoleSwitch(target: AudienceRole): void {
  let ok = false
  try {
    ok = switchAudienceRole(target)   // 唯一动作入口，一次点击恰好一次调用
  } catch {
    ok = false                        // createSession 异常按失败处理（上级 §15）
  }
  if (!ok) {
    roleMenuError.value = '未能开启新会话，请稍后重试。'
    return
  }
  roleMenuOpen.value = false
  roleSwitchStatus.value = `已切换到${presentationOf(target).label}视角，开始新会话`
  void focusComposer()
}
```

### 9.2 禁止清单（实现与评审对照）

- **任何位置不得出现 `session.role = …`、`activeSession.value.role = …` 或对 role 字段的 `v-model`**；角色行、浮层、列表标签一律只读渲染 `activeSession.role` / `session.role`；
- 不得做乐观 UI：在包装器返回前不得先把触发钮或角色行标记成目标角色；
- 不得绕过包装器直接调 `sessions.switchAudienceRole(...)` 或 `sessions.createSession({ role: target, ... })`（projectSlug 解析与 Token 清空逻辑只在包装器里有一份）；
- 一次用户动作恰好一次调用；浮层关闭期间不残留待执行动作；
- 不新增 API 调用、不写任何浏览器存储、不把角色放进 URL。

## 10. 状态图与交互流程

### 10.1 浮层状态机

```text
                 点击「切换视角」/ Enter
  ┌──────────┐ ─────────────────────────▶ ┌──────────┐
  │ 浮层关闭  │                            │ 浮层打开  │
  │ closed   │ ◀───────────────────────── │  open    │
  └──────────┘   Esc/外点/再点触发钮        └────┬─────┘
       ▲           （焦点还触发钮）              │ 点击非当前角色
       │                                        ▼
       │                              switchAudienceRole(target)（包装器）
       │                                        │
       │            ┌─────── true ───────┐──────┴──── false/throw ──────┐
       │            ▼                    ▼                              ▼
       │    关闭浮层 + status 宣布    （不会发生：浮层不含         浮层保持打开
       └──── 焦点 → 输入框           同角色动作项）            alert 行提示重试
```

### 10.2 会话切换时序（不同角色）

```text
[旧会话 S（role=A）]                     createSession({ role:B, projectSlug })
  保留：消息/草稿/pending/失败/凭证/讨论   ─────────────▶ [新会话 S'（role=B）]
  S 的 pending 结果只写回 S                     激活；无消息/草稿/凭证；
  S 行：「短名A」+（· 生成中/· 草稿）            模型=目录默认；ResumeToken 槽位清空
  （首问/草稿前 S' 不入会话列表）                 角色行显示 B；宣布 + 焦点→输入框
```

### 10.3 相同角色（no-op）

```text
浮层内当前角色 = 非动作行（aria-current）→ 无可点路径
提示行：同视角重新开始请用「新对话」（既有 createSession 路径，与本设计无交集）
```

## 11. 方案比较：采用与被放弃

| 方案 | 结论 | 理由 |
|---|---|---|
| A. composer 首行"会话视角"行 + 浮层 | **采用** | 角色邻近输入动作、不挤压消息流、与 ModelSelector 浮层交互一致；身份行样式（serif + eyebrow）与"输入设置"视觉区分 |
| B. 会话轨顶部入口 | 否决 | 角色属于会话内容而非列表；≤960px 轨道收进抽屉后入口消失 |
| C. 对话区顶部常驻通栏 | 否决 | 持续下压消息流；与通知/失败流叠加增加常驻噪音 |
| D. 常显四按钮分段控件 | 否决 | 360px 下溢出；误触即建会话；"每轮设置"误读风险最高 |
| E. 切换前确认对话框 | 否决 | 动作可逆且旧会话完整保留，空会话清理规则防堆积；确认框与"不要做重"冲突 |
| F. listbox/option 选择语义 | 否决 | 动作是"开新会话"而非"本会话内改值"，选择语义误导辅助技术（D-AR-6） |
| G. 每角色专属颜色/图标 | 否决 | 上级禁止角色化颜色数据合同；与来源域色码（通用/作品集/跨域）形成第二套色码系统 |
| H. 切换通知卡（AgentThreadNotice 新 kind）| 否决 | 上级禁止聊天消息；aria-live 宣布已覆盖可感知性；避免第五种通知类型 |
| I. 移动端底部工作表 + scrim | 否决 | scrim/抽屉权重保留给两个大侧栏；4 项浮层不值得模态 |
| J. 跨 feature 引用首页 `audienceProfiles` | 否决 | 为四个字符串建立跨 feature 耦合；文案独立演化更便宜（D-AR-8） |

## 12. 实现落点与测试钩子

### 12.1 文件清单（另立实施计划执行，本文不改动生产代码）

| 文件 | 变更 |
|---|---|
| `frontend/src/features/agent/model/audienceRolePresentation.ts` | 新建：闭合展示映射（§8） |
| `frontend/src/features/agent/components/AgentWorkspace.vue` | 新增：角色行 + 浮层 + 视觉隐藏 status 区 + `handleRoleSwitch`；向 LocalSessionRail 传 `pendingIds` |
| `frontend/src/features/agent/components/LocalSessionRail.vue` | 新增：`pendingIds` prop、角色短标签、草稿/pending 后缀、aria-label 扩展 |
| `frontend/src/features/agent/components/AgentWorkspace.test.ts` | UI 层测试（见 12.3） |
| `frontend/src/features/agent/components/LocalSessionRail.test.ts` | 行标签/后缀/aria 测试（如无此文件则并入 AgentWorkspace.test.ts 挂载断言） |

前置依赖：行为基础计划 Task 4 的 `switchAudienceRole` 接缝已合入（本文 §9 的两个函数）。

### 12.2 测试选择器（沿用仓库既有风格）

- 触发钮 `data-testid="role-switch-trigger"`（含 `aria-expanded`）；
- 浮层 `data-testid="role-switch-popover"`；动作项 `data-testid="role-option"` + `data-role="{ROLE}"`；当前行 `data-testid="role-current"`；
- 宣布区 `data-testid="role-switch-status"`（`role="status"`）；错误行 `data-testid="role-switch-error"`（`role="alert"`）；
- 会话行标签 `data-session-role="{ROLE}"`（rail 沿用 `data-session-*` 风格）；pending 行由 `data-session-pending` 标记。

### 12.3 UI 层断言要点（组件测试，不替代 Browser Exit Gate）

1. 触发钮打开浮层；浮层恰好 3 个动作项且不含当前角色；
2. 动作项可访问名为「以 {角色名} 视角开启新会话」；
3. 点击动作项后：活跃会话角色 = 目标角色（经 `switchAudienceRole`，非直接断言赋值路径）、status 区出现宣布文本、焦点位于输入框；
4. 包装器返回 false/抛错时浮层保持打开、出现 alert 行、活跃会话不变；
5. 草稿/pending 提示行随 `activeSession.draft` / `activePending` 出现；
6. 会话行：短标签 + aria-label 含视角；pending 行含「生成中」，草稿行含「草稿」；
7. Esc 关闭浮层且焦点还触发钮；
8. 不存在绕过接缝的 `role` 写路径：以"生产源码扫描不到对会话对象 `role` 字段的赋值 + 接缝调用 spy"双确认。

### 12.4 Browser Exit Gate（对齐上级 §16.5，本设计贡献的验收面）

桌面与移动各覆盖：角色行可见且正确；切换后新会话正确（无旧消息/草稿/凭证，上下文保留）；同角色无可点路径；pending 旧会话行「生成中」且结果只回旧会话；首页 pending 角色锁定与 handoff 角色一致；console 无 Vue warning。

## 13. 边界情况

| 情况 | 表现 |
|---|---|
| 连续试选角色（A→B→C） | 每次都是新会话；中间未使用的空会话被 `createSession` 清理规则丢弃，不堆积（上级 §6.3.7） |
| 切换后立即删除新会话 | 沿用 `removeSession` 回落到剩余列表首个；角色行随之更新 |
| 新会话目录默认模型未就绪（`modelSelectionRequired`） | 角色行正常；输入禁用与提示沿用现有模型逻辑，与本设计无交集 |
| 旧会话澄清卡未答复时切换 | 澄清卡留在旧会话；返回后原样可提交；新会话不受影响 |
| 晚到的旧会话响应 | 只写回旧会话（既有 generation 校验）；「生成中」后缀消失；活跃 ResumeToken 槽位不受污染（行为基础计划 Task 4 Step 6 已有回归） |
| handoff 种子进入时 | 浮层默认关闭；种子会话角色即角色行初始值（上级 §6.2） |
| `prefers-reduced-motion` | 浮层无过渡（现状全局规则已覆盖） |

## 14. 完成定义

本设计的实现只有同时满足以下条件才可宣称完成：

1. 角色入口、浮层、会话行标签按本文落地，且只经 `switchAudienceRole` 接缝改变会话角色；
2. 生产源码中不存在对 `AgentSession.role` 的赋值路径（评审 + 测试双确认）；
3. §12.3 组件断言全绿，Frontend 全量测试、`run check`、build 通过；
4. 桌面与移动 packaged-JAR Browser 验收覆盖 §12.4 且 console 无 Vue warning；
5. 上级设计的真实 Provider 16 样本矩阵与 A2-53 关闭不在本设计范围内，本设计完成不改变其 `IN_PROGRESS` 状态。
