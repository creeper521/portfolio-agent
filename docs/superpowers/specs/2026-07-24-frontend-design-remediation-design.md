# 前端设计审查整改设计（功能 Bug / 无障碍 / 令牌治理）

> **状态：** 已实施并验证（173 项单元测试 + 构建全绿；视觉验收按用户指示以静态评审与契约测试代替截图）
> **日期：** 2026-07-24
> **适用项目：** `D:\code\agent`
> **前置：** 2026-07-24 全量前端代码审查（审查范围：`frontend/src` 全部 24 个 .vue、4 个全局 CSS、路由、构建配置、视觉契约测试；另对照 `docs/04-项目代码约束.md` 与 2026-07-23 响应式作品窗口 spec）
> **审查方式：** 纯代码审查 + 一次性浏览器实测（composer 尺寸、textarea outline 计算值）
> **实施偏离回写：** ① A4 输入字号由宽度断点（620px/959.98px）改为统一的 `@media (hover: none)`，覆盖 iPad 等触屏宽屏设备，宽度断点会漏判；② C2 暗色治理文件清单补入 `PageLead.vue` 与 `NotFoundPage.vue`（同一组 `#a99f91`/`#4a433b` 硬编码，不纳入会留下漂移源）；③ C2 吸收清单补 `#c8bcad` → `--ink-text-hi`（LightAnswerPanel 按钮文字，§5.2 原表遗漏）

## 1. 整改结论

当前前端视觉语言（纸色档案 + 牛血红单 accent + 衬线标题 + mono 编号）统一且已验收，**本次不触碰视觉方向**。整改针对审查发现的三类问题：

1. **功能性 Bug（批次 A，4 项）**：长输入时 composer 遮挡最新消息、键盘焦点不可见、移动端会话管理不可达、iOS 输入自动缩放。
2. **无障碍与语义（批次 B，4 项）**：证据卡片键盘不可达且嵌套交互、`aria-pressed` 误用、`color-scheme` 声明错误、`scrollIntoView` 在受限容器内的脆弱用法。
3. **令牌与一致性治理（批次 C，7 项）**：深底浅红六值漂移、暗色面板色未令牌化、冗余 token、双 Header 高度魔法数字、首页回答面板与 Agent 页两套元信息语言、字体栈声明与加载不符、spec/约束文档与实现漂移。

审查中确认的设计优点（档案语言、hover 统一、meta 分层、reduced-motion 三重覆盖、视觉契约机制）保持不变。

## 2. 审查证据摘要

| # | 问题 | 证据位置 |
|---|---|---|
| A1 | composer absolute + `scroll padding-bottom: 104px` 固定补偿；textarea `max-height: 110px` 时 composer 占位 ≈136px > 104px，遮挡最后消息；`jump-latest bottom: 96px` 同样被覆盖 | `ConversationThread.vue` L617、L827、L839-861；实测 computed：padding 104px / composer 62px / textarea max 110px |
| A2 | scoped `textarea { outline: 0 }`（优先级 0,1,1）压过全局 `:focus-visible`（0,1,0），焦点框永不出现 | `ConversationThread.vue` L865、`AudienceDialogue.vue` L329、`base.css` L40；实测 outline = none |
| A3 | `.session-menu-trigger { opacity: 0 }` 仅 hover/focus-within 显示，触屏不可见，重命名/删除不可达 | `LocalSessionRail.vue` L224-234 |
| A4 | composer textarea 13px、question-form input 13px，iOS Safari focus 时 <16px 输入触发自动缩放 | `ConversationThread.vue` L868、`AudienceDialogue.vue` L331 |
| B1 | `<article @click>` 无 role/tabindex/键盘事件，且内嵌 RouterLink 形成嵌套交互 | `EvidenceDesk.vue` L73-93 |
| B2 | 单选列表误用 toggle 语义 `aria-pressed` | `EvidencePage.vue` L48-49 |
| B3 | 全站无暗色主题却声明 `color-scheme: light dark`，暗色系统下 UA 原生控件变深色 | `frontend/index.html` L7 |
| B4 | `scrollIntoView({block:'center'})` 滚动所有可滚动祖先，当前恰好可用但结构脆弱 | `ConversationThread.vue` L149 |
| C1 | 深底浅红一个语义六个色值：`--red-hi #b65d53`、`#d27d74`、`#c7776e`、`#cb756c`、`#c9675d`、`#e1948b` | tokens.css、`LightAnswerPanel.vue` L157/176/193/200、`AudienceDialogue.vue` L257 |
| C2 | 暗色面板 9+ 硬编码色与 base.css ink 主题、ProjectPage dark section 部分重复，未令牌化 | `LightAnswerPanel.vue` L139-228、`base.css` ink 主题、`ProjectPage.vue` L356-378 |
| C3 | `--agent-accent`=`--red`、`--agent-dark-control`=`--ink`、`--agent-header`=`--agent-shell-paper`、`--warm`≈`--agent-thread-paper` | tokens.css L5-22 |
| C4 | 普通路由 header `--header-height: 66px` vs 工作区硬编码 `70px`（3 处），`project-toc` 按 66px 计算 sticky | tokens.css L32、`base.css` L133-139/172/305 |
| C5 | Agent 页已做人话化 meta 分层（commit f231e81），首页 LightAnswerPanel 仍展示原始枚举 RESOLUTION/GENERATION/VERIFICATION | `LightAnswerPanel.vue` L101-104 |
| C6 | 字体栈声明 'Noto Serif SC'、'Noto Sans SC'、'IBM Plex Mono'，但 index.html 无任何字体加载；中文衬线 Windows 落中易宋体、macOS 落 Songti SC | tokens.css L29-31、`frontend/index.html` |
| C7 | 2026-07-23 spec：shell max 1600px / radius 16px / stage `#2a2620`；实现：1680px / 20px / `#1e1b17`（视觉契约锁定实现值）。spec 与 docs/04 写 `<=980px` 双抽屉，实现与契约为 `959.98px` | tokens.css、visualContract.test.ts L86-103、docs/04 L279 |

## 3. 批次 A：功能性 Bug（P0）

### 3.1 A1 · Composer 布局重构（消除魔法数字耦合）

**现状**：`.conversation` 为 `grid-template-rows: auto 1fr`；composer `position: absolute; bottom: 24px`；遮挡问题靠 `.conversation__scroll { padding-bottom: 104px }` 手工补偿，textarea 长高后补偿失效。

**方案**（改为流内布局，让 grid 自动分配空间）：

- `.conversation` → `grid-template-rows: auto minmax(0, 1fr) auto`。
- `.composer` 删除 absolute 定位，改为流内第三行：`margin: 0 28px 24px`，保留现有边框、`--agent-radius-md` 圆角、背景色，视觉不变。
- `.conversation__scroll` 删除 `padding-bottom: 104px`（改为常规底部间距）。
- `.jump-latest` 当前是 `.conversation` 的 absolute 子元素（`bottom: 96px` 依赖 composer 固定高度）。改为：在 scroll 容器外包一层 `position: relative` 的 `.conversation__body`，jump-latest 置于其中 `bottom: 16px; right: 28px`，不再依赖 composer 高度。
- 移动端断点内 composer 的 `right/left: 18px` 改为 margin 等价表达。

**收益**：textarea 长至 110px 时 composer 自动占位，永不遮挡消息；62/96/104 三个手工对齐的魔法数字全部消除。

**契约影响**：`visualContract.test.ts` 当前锁 `conversation` 含 `right: 28px` / `left: 28px`（absolute 时代的断言），需先改为锁定流内布局断言（RED）。

### 3.2 A2 · 键盘焦点可见性

- `ConversationThread.vue`：删除 scoped `textarea` 的 `outline: 0`；新增 `.composer:focus-within { border-color: var(--workspace-accent) }`，整条 composer 作为焦点指示。
- `AudienceDialogue.vue`：删除 `.question-form input` 的 `outline: 0`；新增 `.question-form:focus-within { border-color: var(--red) }`（form 已有 bottom border）。
- 全局 `:focus-visible` 红色描边保持为最终兜底，不削弱。

### 3.3 A3 · 触屏会话菜单可见

- `LocalSessionRail.vue` 新增 `@media (hover: none) { .session-menu-trigger { opacity: 1 } }`。
- 桌面 hover 行为不变；`focus-within` 键盘可达性不变。
- `visualContract.test.ts` 增加对该媒体查询的锁定。

### 3.4 A4 · iOS 输入缩放

- 三处输入统一采用 `@media (hover: none)`（触屏主指针）而非宽度断点：`ConversationThread.vue` composer `textarea { font-size: 16px }`、`AudienceDialogue.vue` `.question-form input { font-size: 16px }`、`LocalSessionRail.vue` 重命名 `input { font-size: 16px }`。
- 决策修正：原方案的宽度断点（620px/959.98px）会漏判 iPad 等触屏宽屏设备（竖屏 768–834px、横屏 1024–1366px 均超过断点仍触发 iOS 缩放）；`hover: none` 精确覆盖触屏主指针设备。
- **不**采用 `maximum-scale=1`  viewport 方案（损害页面缩放可达性）。
- 桌面端 13px 与消息气泡 16px 的字号差异记录为已知差异，不在本轮统一。

## 4. 批次 B：无障碍与语义（P1）

### 4.1 B1 · 证据卡片 button 化

- `EvidenceDesk.vue` evidence-card：`<article @click>` 改为 `<button type="button" class="evidence-card">`，天然获得 Enter/Space 键盘触发。
- 卡片内的「查看证据 →」RouterLink 移出按钮：卡片外包一层无语义容器，按钮与链接成为兄弟节点，消除嵌套交互。
- 保留 `evidence-card` / `evidence-card--active` / `evidence-card--focused` 类名与全部视觉属性（button 需补 `font: inherit; text-align: left; cursor: pointer` 重置），AgentWorkspace 的 `:deep(.evidence-card)` reduced-motion 引用与契约测试不受影响。
- citation-card 已是 button，本轮统一了两种卡片的交互语义。

### 4.2 B2 · `aria-pressed` → `aria-current`

- `EvidencePage.vue` L48-49：`:aria-pressed` 改为 `:aria-current="selected?.id === item.id ? 'true' : undefined"`。
- `data-selected-evidence` 属性保留（现有测试/e2e 锚点）。
- `EvidencePage.test.ts` 同步更新（先 RED）。

### 4.3 B3 · `color-scheme` 修正

- `frontend/index.html`：`content="light dark"` → `content="only light"`。
- 未来若引入暗色主题，需单独设计评审后改回。

### 4.4 B4 · 容器内滚动替代 `scrollIntoView`

- `ConversationThread.vue` focusTarget watcher：`element.scrollIntoView({ block: 'center' })` 改为对 `.conversation__scroll` 容器的显式 `scrollTo`：

```ts
const container = scrollArea.value
const rect = element.getBoundingClientRect()
const containerRect = container.getBoundingClientRect()
const top = container.scrollTop + (rect.top - containerRect.top)
  - (container.clientHeight - rect.height) / 2
container.scrollTo({ top, behavior: reducedMotion ? 'auto' : 'smooth' })
```

- 只滚动对话容器，不触碰任何外层祖先。
- 现有测试 mock `scrollIntoView` 的用例改为断言容器 `scrollTo` 参数（jsdom 下 mock `scrollTo`）。

## 5. 批次 C：令牌与一致性治理（P2）

### 5.1 C1 · 深底浅红收敛为两个 token

- tokens.css 新增 `--red-on-ink: #d27d74`（深底文字级，对 `--ink` 对比度约 5.5:1，满足小字 AA）。
- 映射：`#d27d74`、`#c7776e`、`#cb756c`、`#c9675d`、`#e1948b` 五处全部改用 `--red-on-ink`。
- `--red-hi: #b65d53` 保留，仅限大字/大色块场景（ProjectPage cover 的 10px mono 文字改 `--red-on-ink`，其余保留）。
- 视觉差异在肉眼不可分辨量级；若 e2e 截图对比发现可见偏差，以 `--red-on-ink` 为准。

### 5.2 C2 · 暗色系列令牌化

tokens.css 新增四个 token，替换 LightAnswerPanel、ProjectPage dark section、base.css ink header 主题中的硬编码值：

| 新 token | 取值 | 吸收的硬编码 |
|---|---|---|
| `--ink-line` | `#4a433b` | `#4a433b`、`#51493f`、`#5b5349`、`#60574d` |
| `--ink-text` | `#a99f91` | `#a99f91` |
| `--ink-text-hi` | `#e8ddce` | `#e8ddce`、`#d2c8bb`、`#cfc5b7`、`#c8bcad` |
| `--ink-text-faint` | `#94897c` | `#94897c` |

线色四处近似值统一为 `--ink-line`（#4a433b，与现有 ink header 边线一致）；文字亮色以 LightAnswerPanel 正文 `#e8ddce` 为准。视觉差异同样在不可分辨量级。覆盖文件：`LightAnswerPanel.vue`、`ProjectPage.vue`（dark section）、`base.css`（ink header 主题）、`PageLead.vue`、`NotFoundPage.vue`（后两者为实施时补充纳入，与前三者同一组硬编码）。

### 5.3 C3 · 冗余 token 别名化与清理

- `--agent-accent: #7a2e2a` → `--agent-accent: var(--red)`。保留别名层（视觉契约锁定 `--workspace-accent: var(--agent-accent)` 的间接层是有意的），但色值单一来源到 `--red`。
- `--agent-header` → `var(--agent-shell-paper)`。
- `--agent-dark-control`：实施时全局 grep 确认无引用后删除；有引用则改为 `var(--ink)`。
- `--warm`：grep 确认无引用后删除（2026-07-23 spec §9 记录的中栏暖色已被契约锁定的 `--agent-thread-paper` 取代）；仍有引用则改为别名。

### 5.4 C4 · 工作区 Header 高度令牌化

- tokens.css 新增 `--header-height-workspace: 70px`。
- base.css 中 3 处硬编码 `70px`（`.site-frame--workspace` padding-top、workspace 内 `.dossier-header` height、workspace 内 `.primary-nav` inset）全部引用新 token。
- `--header-height: 66px` 保留为普通路由高度；两个 token 并存但各自单一来源。

### 5.5 C5 · 回答元信息标签共用模块

- 新建 `frontend/src/features/agent/model/answerLabels.ts`，导出纯函数：`answerStatusLabel`、`answerScopeTag`、`answerSourceTag`、`answerVerificationTag`、`answerTechTail`（逻辑从 ConversationThread 现有实现原样提取，不改文案）。
- `ConversationThread.vue` 改为引用该模块（删除本地重复实现与已无模板引用的死代码 `answerSourceLabel`）。
- `LightAnswerPanel.vue` aside 的 RESOLUTION/GENERATION/VERIFICATION 三行原始枚举改用人话标签 + 技术枚举尾注（沿用 Agent 页的「人话为主、枚举降级」分层），视觉保持暗色面板风格，仅换文字。
- `answerLabels.ts` 配套单元测试。

### 5.6 C6 · 字体栈决策记录

- **决策：保持 Georgia + 系统回退，不加载网络字体。** 理由：当前视觉已按系统字体渲染验收；加载中文衬线需子集化流水线且改变字重/字宽 metrics，存在布局回归风险；不加载也符合访客隐私（无第三方字体请求）。
- tokens.css 字体栈加注释：`'Noto Serif SC'` 等仅为「用户本机已装则更佳」的可选增强，不构成依赖。
- 未来若决定加载中文衬线，须单独 ADR + 子集化方案 + 视觉回归验收。

### 5.7 C7 · 文档漂移回写

- 本 spec 批准后，在 `docs/00-文档状态索引.md` 新增本行并声明：Agent 壳层 token 以实现值（`--agent-shell-max: 1680px`、`--agent-radius-shell: 20px`、`--agent-stage: #1e1b17`、双抽屉断点 `959.98px`）为当前权威，2026-07-23 spec 的对应描述视为已被取代。
- `docs/04-项目代码约束.md` L279 的 `<=980px` 双抽屉描述同步修正为 `<=959.98px`。
- 2026-07-23 spec 原文不回改（保留历史），由 00 索引完成状态转移。

## 6. 非目标

以下审查发现本轮**不处理**，记录备查：

1. base.css 上帝文件拆分、DossierHeader 样式 scoped 化（结构重构，需单独评估回归面）。
2. 全站断点刻度统一（959.98/980/900/820/760/700/620/520，涉及全站视觉回归）。
3. 6 处 `!important` 专项清理（批次涉及的除外）。
4. header `backdrop-filter` 与 92% 不透明背景的取舍。
5. PaneResizer、home 三组件补单元测试。
6. DossierHeader「技术面试官」硬编码与 AudienceDialogue 角色联动（属产品决策）。
7. Case 前端列表/详情、Agent v2 前端联调等既定路线工作（见 `docs/08` 下一步优先级）。
8. 任何视觉方向、色板家族、字体方向的重新设计。

不修改后端、API、公开内容、会话隐私、路由 seed、C1/C2/C3 能力。不 stage、commit 或 push，除非用户另行明确授权。

## 7. 文件修改边界

预计修改（按批次）：

**批次 A**
- `frontend/src/features/agent/components/ConversationThread.vue`（composer 流内化、focus-within、移动字号）
- `frontend/src/features/agent/components/LocalSessionRail.vue`（hover:none、rename input 字号）
- `frontend/src/features/audience/components/AudienceDialogue.vue`（focus-within、移动字号）
- `frontend/src/app/styles/visualContract.test.ts`（先 RED）
- `frontend/src/features/agent/components/ConversationThread.test.ts`
- `frontend/src/features/agent/components/LocalSessionRail.test.ts`
- `frontend/src/features/audience/components/AudienceDialogue.test.ts`

**批次 B**
- `frontend/src/features/agent/components/EvidenceDesk.vue`
- `frontend/src/features/agent/components/EvidenceDesk.test.ts`
- `frontend/src/pages/EvidencePage.vue`
- `frontend/src/pages/EvidencePage.test.ts`
- `frontend/index.html`
- `frontend/src/features/agent/components/ConversationThread.vue`（scrollTo）
- `frontend/src/features/agent/components/ConversationThread.test.ts`
- `frontend/src/features/agent/components/AgentWorkspace.test.ts`（scrollIntoView mock 用例）

**批次 C**
- `frontend/src/app/styles/tokens.css`
- `frontend/src/app/styles/base.css`（ink 主题、70px 三处）
- `frontend/src/features/audience/components/LightAnswerPanel.vue`
- `frontend/src/features/audience/components/AudienceDialogue.vue`（`#e1948b`）
- `frontend/src/pages/ProjectPage.vue`（dark section 硬编码）
- `frontend/src/shared/components/PageLead.vue`（dark 主题硬编码，实施时补充纳入）
- `frontend/src/pages/NotFoundPage.vue`（暗底文字硬编码，实施时补充纳入）
- `frontend/src/features/agent/model/answerLabels.ts`（新建）
- `frontend/src/features/agent/model/answerLabels.test.ts`（新建）
- `frontend/src/features/agent/components/ConversationThread.vue`（改用 answerLabels）
- `frontend/src/app/styles/visualContract.test.ts`
- `docs/04-项目代码约束.md`
- `docs/00-文档状态索引.md`

**跨批次回归**：`frontend/e2e/portfolio.spec.ts`（多视口截图、无横向溢出、抽屉行为断言按需更新）。

## 8. 测试策略

严格按 RED→GREEN，逐批次执行，每批次独立全绿后再进入下一批次：

**批次 A**
1. `visualContract.test.ts` 先改：锁定 composer 流内布局（`grid-template-rows: auto minmax(0, 1fr) auto`、无 `padding-bottom: 104px`）、`@media (hover: none)` 的 trigger 常显、移动端 16px 输入字号。
2. 组件测试：ConversationThread 断言 `.composer:focus-within` 规则存在；LocalSessionRail/AudienceDialogue 同步补断言。
3. 实现至全绿；e2e 跑现有对话流 + 长文本输入后最后一条消息可见（新增用例）。

**批次 B**
1. `EvidenceDesk.test.ts` 先要求 evidence-card 为 button、可 Enter 触发 select、RouterLink 不在按钮内。
2. `EvidencePage.test.ts` 先要求 `aria-current`。
3. ConversationThread/AgentWorkspace 测试改 mock `scrollTo` 并断言居中计算。
4. 实现至全绿；e2e 验证证据选择、引用定位仍工作。

**批次 C**
1. `visualContract.test.ts` 先锁新 token（`--red-on-ink`、四个 `--ink-*`、`--header-height-workspace`、别名关系）。
2. `answerLabels.test.ts` 先写标签映射用例；LightAnswerPanel 测试要求不再出现原始枚举字符串。
3. 实现至全绿；e2e 多视口截图肉眼核对无明显色差。

**每批次收尾验证命令**：

```powershell
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
npm.cmd --prefix frontend run test:e2e
```

全部批次完成后按 AGENTS.md 跑 `scripts/privacy-check.ps1`；后端无改动，不要求 `mvn test`（若执行发布门禁则按其要求）。

## 9. 文档治理

整改完成并通过后：

1. 本设计状态改为「已实施并验证」。
2. 按 §5.7 更新 `docs/00-文档状态索引.md`（新增本行 + 完成 2026-07-23 spec 相关 token 值的状态转移）。
3. 按 §5.7 修正 `docs/04-项目代码约束.md` 的抽屉断点描述。
4. 若实施中任何决策偏离本 spec（如 token 取值调整），先回写本文件再继续。

## 10. 验收结论

整改完成必须同时满足：

1. 长文本输入时 composer 不遮挡最后一条消息，`jump-latest` 不被覆盖；62/96/104 魔法数字消除。
2. Tab 键遍历 composer、首页提问输入框、证据卡片均有可见焦点指示。
3. 触屏断点下会话「···」菜单常显可达。
4. 移动端 focus 输入框不触发页面自动缩放。
5. 证据卡片可键盘选择；EvidencePage 列表为 `aria-current` 语义。
6. 引用定位只滚动对话容器。
7. tokens.css 中深底浅红、暗色系列、header 高度各有单一来源；硬编码六红值与暗色值消除。
8. 首页与 Agent 页回答元信息使用同一套人话标签。
9. 单元测试、构建、前端 E2E 全绿；多视口截图肉眼核对无视觉回归。
10. `docs/00-文档状态索引.md` 与 `docs/04-项目代码约束.md` 完成同步。
