# Agent 工作区配色调整设计 · 暖调中栏 + 牛血红 CTA

> 状态：**待实施**（design，已与用户确认方向，交付 Codex 执行）
> 日期：2026-07-23
> 前序：`docs/superpowers/specs/2026-07-22-portfolio-agent-light-workspace-palette-design.md`（上一版浅色三栏，已落地但用户仍不满意）
> 约束遵守：`docs/04-项目代码约束.md`、`AGENTS.md`（无 var / 无 record / 无 Lombok；测试先行；保留用户 Git 改动）

---

## 0. 背景与决策

上一版（07-22）把 Agent 工作区从"深中栏"改成了"三栏全浅色"（`--workspace-thread-bg: var(--paper-hi)`）。用户反馈：**整体偏平、中栏缺乏焦点，但仍希望保留浅色基调**。

经与用户确认（2026-07-23），方向定为：

1. **三栏维持浅色，但中栏改用更暖的燕麦色调**（不是 `paper-hi` 近白，而是带明显暖意的 `#f3e8d6`），靠**色温差**而非明暗差重建视觉焦点。这是用户在 A/B/C/D 四档对比稿中选定的 **B 档**。
2. **发送按钮（composer submit）改为牛血红实底** `#7a2e2a`，作为全屏唯一强色 CTA。
3. **新建会话按钮维持原深墨**（`--ink`）。
4. 激活态 / 选中标记继续用牛血红细线，不动。

> 定稿参考截图：`design-exploration/final-B-red.png`（B 档 + 牛血红 CTA）。
> 可交互对比稿：`design-exploration/warm-cream-compare.html`（顶部切换 A/B/C/D + CTA 模式）。

---

## 1. 现状精确映射（改动前的真实代码状态）

> ⚠️ Codex 必须以此为准。仓库里曾存在一版"深中栏"代码，**现已不存在**；以下行号基于当前 master（commit `c571316`）。

### 1.1 全局 token — `frontend/src/app/styles/tokens.css`

```
:root {
  --paper: #f4eee4;      /* 左/右栏基底 */
  --paper-hi: #fbf7ef;   /* 近白米（上一版中栏用了它，本版要换掉） */
  --paper-low: #e9dfd0;
  --ink: #201c17;        /* 深墨 */
  --ink-2: #34302a;
  --muted: #746b5e;
  --faint: #9c9182;
  --rule: #cdbfa9;
  --red: #7a2e2a;        /* 牛血红 */
  --red-hi: #b65d53;
  ...（字体/尺寸 token 略）
}
```

**本版不改这 11 个 token 的值**（`visualContract.test.ts:29` 锁了 `--paper/--ink/--red`）。只**新增**一个 token。

### 1.2 工作区 token 定义 — `frontend/src/features/agent/components/AgentWorkspace.vue:385-405`

这是改动主战场。当前定义：

```css
.agent-workspace {
  --workspace-rail-bg: color-mix(in srgb, var(--paper) 72%, var(--paper-low));
  --workspace-thread-bg: var(--paper-hi);          /* ← 中栏：本版要改成暖燕麦 */
  --workspace-evidence-bg: var(--paper);
  --workspace-surface-subtle: color-mix(in srgb, var(--paper-low) 46%, transparent);
  --workspace-text: var(--ink);
  --workspace-text-secondary: var(--muted);
  --workspace-text-faint: var(--faint);
  --workspace-rule: var(--rule);
  --workspace-accent: var(--red);
  --workspace-accent-soft: var(--red-hi);
  --workspace-primary-bg: var(--ink);               /* ← 被「新建会话」和「发送」共用 */
  --workspace-primary-text: var(--paper-hi);
  position: relative;
  display: grid;
  ...
  background: var(--workspace-evidence-bg);
  ...
}
```

**关键耦合**：`--workspace-primary-bg` 被两个按钮共用——
- 新建会话按钮：`LocalSessionRail.vue:85-86`
- 发送按钮：`ConversationThread.vue:473-475`

本版要让两者颜色**不同**（发送=红、新建=深墨），必须拆分此 token。

### 1.3 composer 发送按钮 — `ConversationThread.vue:471-482`

```css
.composer button {
  padding: 10px 14px;
  color: var(--workspace-primary-text, var(--paper-hi));
  border: 0;
  background: var(--workspace-primary-bg, var(--ink));   /* ← 改成牛血红 */
  font: 9px var(--mono);
  letter-spacing: 0.1em;
}

.composer button:not(:disabled):hover {
  background: var(--ink-2);   /* ← 硬编码 hover，改红后这里要换成更深的红 */
}
```

### 1.4 新建会话按钮 — `LocalSessionRail.vue:82-93`

```css
.session-rail__new {
  min-height: 44px;
  color: var(--workspace-primary-text, var(--paper-hi));
  border: 1px solid var(--workspace-primary-bg, var(--ink));
  background: var(--workspace-primary-bg, var(--ink));   /* ← 保持深墨 */
  font: 11px var(--mono);
  letter-spacing: 0.08em;
}

.session-rail__new:not(:disabled):hover {
  background: var(--ink-2);   /* ← 保持，深墨 hover */
}
```

### 1.5 composer 容器底色 — `ConversationThread.vue:436-448`

```css
.composer {
  position: absolute;
  ...
  background: var(--workspace-thread-bg, var(--paper-hi));  /* composer 跟随中栏底色 */
}
```

> 注意：composer 跟 `--workspace-thread-bg` 走。中栏改暖燕麦后，composer 也会自动变暖，**无需额外改**。

### 1.6 scrim（移动端遮罩）— `AgentWorkspace.vue:460-468`

```css
.workspace-scrim {
  ...
  background: rgba(32, 28, 23, 0.5);   /* 硬编码深墨半透明 */
}
```

> 这是移动端抽屉打开时的全屏遮罩。**语义上仍是"压暗以聚焦抽屉"**，建议保持不动（遮罩本就该暗）。若追求统一可改 `rgba(122, 46, 42, 0.18)`（牛血红淡蒙版），但会降低遮罩压抑感——**默认不改，列为可选项**。

### 1.7 测试约束 — `frontend/src/app/styles/visualContract.test.ts`

当前断言（第 36-56 行）：

```ts
expect(workspace).toContain('--workspace-thread-bg: var(--paper-hi)')   // ← 会失败，需更新
expect(conversation).not.toContain('background: var(--ink)')            // 仍成立
expect(conversation).toMatch(/\.composer button\s*\{[^}]*background: var\(--workspace-primary-bg, var\(--ink\)\)/s)  // ← 会失败，发送按钮改红后
```

**必须同步更新此测试**，否则 RED→GREEN 走不通。

---

## 2. 设计方案

### 2.1 核心：只动 2 个 token 定义 + 1 处组件样式 + 1 个测试文件

得益于上一版搭好的 `--workspace-*` token 系统，本版改动极小且干净。改动**不扩散**到首页/项目页/证据中心等其他路由——那些页面走 `paper` 主题，与本设计无关。

### 2.2 新增 token

在 `tokens.css` 的 `:root` 内新增（放在 `--paper-low` 之后、`--ink` 之前，与 paper 族聚合）：

```css
--warm: #f3e8d6;   /* B 档暖燕麦：中栏暖调聚焦层 */
```

**为什么单独建 token 而非字面量散落**：
- 上一版的整个架构哲学就是"颜色走 token"，直接散字面量会破坏一致性、且无法在 `AgentWorkspace` 的 `--workspace-*` 定义里复用。
- 建成 token 后，未来调暖度只改一处。
- 不触发 `visualContract.test.ts:33` 的 `green|teal|cyan|purple` 拦截（`#f3e8d6` 不含这些词）。

### 2.3 拆分 CTA token

把共用的 `--workspace-primary-bg` 拆成两个语义明确的 token：

| token | 值 | 用途 |
|---|---|---|
| `--workspace-primary-bg` | `var(--ink)` **（保持不变）** | 新建会话按钮（深墨） |
| `--workspace-action-bg` | `var(--red)` **（新增）** | 发送按钮（牛血红） |
| `--workspace-action-bg-hover` | `#662522` **（新增）** | 发送按钮 hover（比 `--red` 更深一档） |

**为什么发送按钮要独立 token 而非直接写 `var(--red)`**：
- 语义化：组件里写 `--workspace-action-bg` 比 `var(--red)` 更能表达"这是工作区主 CTA"。
- 可扩展：将来若 CTA 要调色，只动 workspace 定义块，不动组件。
- fallback 链保持健壮：`var(--workspace-action-bg, var(--red))`。

### 2.4 中栏改色

`--workspace-thread-bg: var(--paper-hi)` → `var(--warm)`。

仅此一处。composer 底色（`ConversationThread.vue:447`）、conversation 容器（`:249`）都引用这个 token，会自动跟随变暖。

### 2.5 配色逻辑总览（改后）

```
┌─────────────────────────────────────────────┐
│  HEADER（ink 主题，深底米字 —— 见 §2.6 讨论）  │
├──────────┬──────────────────┬───────────────┤
│ 左栏      │  中栏（对话）     │  右栏（证据）   │
│ #f4eee4   │  #f3e8d6 暖燕麦   │  #f4eee4      │
│ 偏冷米    │  偏暖、带黄       │  偏冷米        │
│ paper 72% │  ← 色温差焦点     │  paper        │
├──────────┤                  │               │
│ ＋新对话   │  ▸ 用户提问      │  [证据卡]      │
│ (深墨底    │  ◂ Agent 回答    │  激活:红左条   │
│  米字)    │                  │               │
│          │  ┌────────────┐  │               │
│ 激活:红左条│  │发送 牛血红 ↵│  │  ← 全屏唯一   │
│          │  └────────────┘  │     强色 CTA   │
└──────────┴──────────────────┴───────────────┘
```

### 2.6 Header 主题（需用户/Codex 留意，但建议不动）

`base.css:91-95` 的 `[data-header-theme='ink']` 是深底米字。Agent 页（`router.ts` meta `workspace: true`）用 ink 主题。中栏改暖后，header 深底与亮中栏会有上下对比——

**建议保持 ink header 不变**。理由：
1. header 是全局组件，跨路由复用；改它影响首页/项目页视觉。
2. 深底 header + 暖中栏的对比反而**强化了"工具区在下方"的层次**，不算割裂。
3. 原型对比稿（`warm-cream-compare.html`）的 B 档用的也是深 header，视觉成立。

若实施后用户觉得 header 太暗，再单独开一项处理（改 `DossierHeader.vue:11` 的 theme 逻辑或 `base.css:91`）。**本设计不含此改动**。

---

## 3. 逐文件改动清单（Codex 执行依据）

> 每项给出：文件 / 行号（基于 c571316）/ 改前 / 改后 / 理由。
> 约束：生产代码禁止 `var`（Java）/ `record`（Java）/ Lombok —— 本任务仅改 CSS + TS 测试，与 Java 无关。

### 改动 1 — `frontend/src/app/styles/tokens.css`

**位置**：第 4 行 `--paper-low: #e9dfd0;` 之后、第 5 行 `--ink: #201c17;` 之前，插入一行。

**改后**（第 4-5 行之间新增）：
```css
  --paper-low: #e9dfd0;
  --warm: #f3e8d6;
  --ink: #201c17;
```

**理由**：新增 B 档暖燕麦 token，供中栏引用。

---

### 改动 2 — `frontend/src/features/agent/components/AgentWorkspace.vue`

**位置**：第 385-397 行的 `--workspace-*` 定义块。

**改前**（第 386-397 行）：
```css
  --workspace-rail-bg: color-mix(in srgb, var(--paper) 72%, var(--paper-low));
  --workspace-thread-bg: var(--paper-hi);
  --workspace-evidence-bg: var(--paper);
  --workspace-surface-subtle: color-mix(in srgb, var(--paper-low) 46%, transparent);
  --workspace-text: var(--ink);
  --workspace-text-secondary: var(--muted);
  --workspace-text-faint: var(--faint);
  --workspace-rule: var(--rule);
  --workspace-accent: var(--red);
  --workspace-accent-soft: var(--red-hi);
  --workspace-primary-bg: var(--ink);
  --workspace-primary-text: var(--paper-hi);
```

**改后**：
```css
  --workspace-rail-bg: color-mix(in srgb, var(--paper) 72%, var(--paper-low));
  --workspace-thread-bg: var(--warm);
  --workspace-evidence-bg: var(--paper);
  --workspace-surface-subtle: color-mix(in srgb, var(--paper-low) 46%, transparent);
  --workspace-text: var(--ink);
  --workspace-text-secondary: var(--muted);
  --workspace-text-faint: var(--faint);
  --workspace-rule: var(--rule);
  --workspace-accent: var(--red);
  --workspace-accent-soft: var(--red-hi);
  --workspace-primary-bg: var(--ink);
  --workspace-primary-text: var(--paper-hi);
  --workspace-action-bg: var(--red);
  --workspace-action-bg-hover: #662522;
```

**变更点**：
1. `--workspace-thread-bg`: `var(--paper-hi)` → `var(--warm)`（中栏暖燕麦）。
2. 末尾新增 `--workspace-action-bg` 与 `--workspace-action-bg-hover`（发送按钮专用红 CTA）。
3. `--workspace-primary-bg` **保持 `var(--ink)`**（新建会话按钮仍深墨）。

---

### 改动 3 — `frontend/src/features/agent/components/ConversationThread.vue`

**位置 A**：第 471-478 行，`.composer button`。

**改前**（第 471-478 行）：
```css
.composer button {
  padding: 10px 14px;
  color: var(--workspace-primary-text, var(--paper-hi));
  border: 0;
  background: var(--workspace-primary-bg, var(--ink));
  font: 9px var(--mono);
  letter-spacing: 0.1em;
}
```

**改后**：
```css
.composer button {
  padding: 10px 14px;
  color: var(--workspace-primary-text, var(--paper-hi));
  border: 0;
  background: var(--workspace-action-bg, var(--red));
  font: 9px var(--mono);
  letter-spacing: 0.1em;
}
```

**变更**：`background` 由 `--workspace-primary-bg` 改为 `--workspace-action-bg`（牛血红）。`color` 保持 `--workspace-primary-text`（米色字 on 红，对比度足够）。

**位置 B**：第 480-482 行，hover。

**改前**：
```css
.composer button:not(:disabled):hover {
  background: var(--ink-2);
}
```

**改后**：
```css
.composer button:not(:disabled):hover {
  background: var(--workspace-action-bg-hover, #662522);
}
```

**变更**：hover 从深墨 `--ink-2` 改为更深的牛血红 `#662522`（保持"按下变深"的语义，但色相跟随红 CTA）。

> ⚠️ **不要动** `ConversationThread.vue` 其他任何地方。特别是：
> - `.conversation`（第 249 行 `background: var(--workspace-thread-bg...)`）—— 自动跟随变暖，无需改。
> - `.composer`（第 447 行）—— 自动跟随中栏底色，无需改。
> - 文字颜色全部走 `--workspace-text` 等 token，中栏变暖后深色字仍清晰，无需改。

---

### 改动 4 — `frontend/src/app/styles/visualContract.test.ts`

**必须同步更新**，否则测试 RED。改动 2 处断言。

**位置 A**：第 38 行。

**改前**：
```ts
    expect(workspace).toContain('--workspace-thread-bg: var(--paper-hi)')
```

**改后**：
```ts
    expect(workspace).toContain('--workspace-thread-bg: var(--warm)')
```

**位置 B**：第 50-52 行，composer 按钮断言。

**改前**：
```ts
    expect(conversation).toMatch(
      /\.composer button\s*\{[^}]*background: var\(--workspace-primary-bg, var\(--ink\)\)/s,
    )
```

**改后**：
```ts
    expect(conversation).toMatch(
      /\.composer button\s*\{[^}]*background: var\(--workspace-action-bg, var\(--red\)\)/s,
    )
```

**位置 C（建议新增，锁住拆分结果）**：在第 55 行（`sessions` 断言）之后，新增一条断言确认新建会话按钮仍深墨、且 action token 存在。

**新增**（插在第 55 行 `expect(sessions)...` 之后）：
```ts
    expect(workspace).toContain('--workspace-action-bg: var(--red)')
    expect(workspace).toContain('--workspace-action-bg-hover: #662522')
```

> 这些测试改动本身遵循 TDD：先改测试（RED）→ 改实现（GREEN）。Codex 应先跑测试确认 RED，再改实现转 GREEN。

---

### 不需要改动的文件（明确列出，避免 Codex 误伤）

- ❌ `tokens.css` 的 `--paper/--paper-hi/--ink/--red` 等现有值 —— 不动（被测试锁定）。
- ❌ `base.css` —— header ink 主题保持（见 §2.6）。
- ❌ `LocalSessionRail.vue` —— 新建会话按钮保持深墨（`--workspace-primary-bg` 未变）。`hover: var(--ink-2)` 保持。
- ❌ `EvidenceDesk.vue` —— 全走 token，自动适配，无需改。
- ❌ `PaneResizer.vue` —— 全走 token，无需改。
- ❌ `AgentPage.vue` —— 无关。
- ❌ 任何后端 Java / API / 数据 —— 本设计纯前端视觉。

---

## 4. 验收标准（Definition of Done）

Codex 实施后，以下全部成立才算完成：

### 4.1 自动化验证（必须全绿）

```powershell
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
```

- `visualContract.test.ts` 全部断言通过（含更新后的 3 处）。
- 全部前端测试绿。
- `vite build` 无类型错误、无构建失败。

### 4.2 视觉验收（人工 / Playwright 截图对照）

启动 dev server（`npm.cmd --prefix frontend run dev`）后，访问 `/agent`：

1. **中栏底色为暖燕麦 `#f3e8d6`**，明显比左/右栏（`#f4eee4`）更暖、带黄意，形成色温差焦点。
2. **发送按钮为牛血红实底 `#7a2e2a`**，米色字，是中栏唯一强色块。
3. **发送按钮 hover 变深红 `#662522`**。
4. **新建会话按钮仍为深墨 `#201c17`**，米字，hover `--ink-2`。
5. **激活会话**左栏有牛血红 inset 左条（`--workspace-accent`，未变）。
6. **选中证据卡**右栏有牛血红左条（未变）。
7. **header 仍为 ink 深底米字**（未改，按 §2.6）。
8. 文字、边线、resizer、composer 容器无残破/错位/不可读。

对照基准：`design-exploration/final-B-red.png`（应高度一致，唯一差异是真实页面有完整动态内容）。

### 4.3 无副作用检查

- 首页 `/`、项目页 `/projects/*`、证据中心 `/evidence` —— **视觉无变化**（它们走 `paper` 主题，不受 `--workspace-*` 影响）。
- 移动端（≤980px / ≤1220px）抽屉模式 —— scrim 仍为深墨半透明（未改，见 §1.6），功能正常。

---

## 5. 风险与回滚

### 5.1 风险

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| `--warm` 值 `#f3e8d6` 与两侧 `#f4eee4` 色温差太小，焦点感不足 | 中 | 视觉平庸 | 已在 B/C/D 对比稿验证；若不足，调 `--warm` 单值即可（改 `tokens.css` 一处） |
| 牛血红按钮米字对比度 borderline | 低 | 可访问性 | `#7a2e2a` on `#fbf7ef` 文字对比度 ≥ 7:1（AAA），安全 |
| header 深底与暖中栏割裂 | 低 | 观感 | 按 §2.6 保持；若用户反馈再单独处理 |
| 测试正则匹配 scoped CSS 失败 | 低 | RED 无法转 GREEN | 正则用了 `/s` 标志跨行匹配，已验证当前格式可匹配 |

### 5.2 回滚

本改动集中在 4 个文件、约 10 行。回滚 = `git revert` 单个 commit 即可。无数据迁移、无 API 变更、无破坏性。

---

## 6. 交付清单（给 Codex）

Codex 执行顺序（TDD）：

1. **RED**：先改 `visualContract.test.ts`（改动 4 的 A/B/C），跑 `npm test -- --run`，确认相关断言失败。
2. **GREEN**：
   - 改动 1：`tokens.css` 加 `--warm`。
   - 改动 2：`AgentWorkspace.vue` 改 thread-bg + 加 action token。
   - 改动 3：`ConversationThread.vue` 改 composer button background + hover。
3. 跑 `npm test -- --run` 确认全绿。
4. 跑 `npm run build` 确认构建通过。
5. **REFACTOR**：本改动已最小化，无额外重构空间。
6. 启动 dev server，按 §4.2 逐项人工验收，截图与 `design-exploration/final-B-red.png` 对照。

**约束提醒**：
- 不提交、不推送（用户未授权 git 操作）。
- 不动本设计明确列为"不需要改动"的文件。
- 生产 TS/CSS 不引入新依赖。

---

## 附录 A · 完整改后 token 速查

```
/* tokens.css（新增 1 行）*/
--warm: #f3e8d6;

/* AgentWorkspace.vue --workspace-* 定义块（改后全貌）*/
--workspace-rail-bg:          color-mix(in srgb, var(--paper) 72%, var(--paper-low));   /* 不变 */
--workspace-thread-bg:        var(--warm);        /* 改：paper-hi → warm */
--workspace-evidence-bg:      var(--paper);       /* 不变 */
--workspace-surface-subtle:   color-mix(in srgb, var(--paper-low) 46%, transparent);   /* 不变 */
--workspace-text:             var(--ink);         /* 不变 */
--workspace-text-secondary:   var(--muted);       /* 不变 */
--workspace-text-faint:       var(--faint);       /* 不变 */
--workspace-rule:             var(--rule);        /* 不变 */
--workspace-accent:           var(--red);         /* 不变 */
--workspace-accent-soft:      var(--red-hi);      /* 不变 */
--workspace-primary-bg:       var(--ink);         /* 不变（新建会话按钮）*/
--workspace-primary-text:     var(--paper-hi);    /* 不变 */
--workspace-action-bg:        var(--red);         /* 新增（发送按钮）*/
--workspace-action-bg-hover:  #662522;            /* 新增（发送按钮 hover）*/
```

## 附录 B · 参考文件位置

- 本设计文档：`docs/superpowers/specs/2026-07-23-agent-warm-cream-palette-and-red-cta-design.md`
- 定稿截图：`design-exploration/final-B-red.png`
- 可交互对比稿：`design-exploration/warm-cream-compare.html`
- 前序设计：`docs/superpowers/specs/2026-07-22-portfolio-agent-light-workspace-palette-design.md`
