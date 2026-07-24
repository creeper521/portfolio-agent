# Codex 开发任务 · Agent 工作区暖调中栏 + 牛血红 CTA

> 把下面整段（从「---BEGIN PROMPT---」到「---END PROMPT---」）复制给 Codex 即可。
> 下列路径均相对于仓库根目录（Codex 在仓库根执行）。

---BEGIN PROMPT---

## 任务

按已批准的设计文档，把 Agent 工作区的中栏从「偏白米色」改成「暖燕麦色调」，并把发送按钮改成牛血红实底 CTA。设计方向已与用户确认，无需再做方向探索，**严格按文档执行**。

## 必读的 3 个参考文件（按此顺序读）

1. **设计实现文档（权威规格，逐行照做）**：
   `docs/superpowers/specs/2026-07-23-agent-warm-cream-palette-and-red-cta-design.md`
2. **原始 demo 设计稿（B 档 + 牛血红 CTA 的视觉基准）**：
   `design-exploration/warm-cream-compare.html`
   —— 在浏览器打开，顶部切到 `B · 暖调 1档` + `CTA · 牛血红按钮` 即为定稿效果。这是**视觉验收对照基准**。
3. **定稿参考截图**：
   `design-exploration/final-B-red.png`
   —— B 档 + 牛血红 CTA 的渲染结果，对照此图验收。

> ⚠️ 设计文档 §1「现状精确映射」基于 commit `c571316`。**所有行号以此为准**。若你读到的代码行号有偏差（因为后续有人改过），以**「改前原文」字符串**为锚点定位，不要只认行号。

## 工作流（TDD，严格遵守）

项目要求测试先行（见 `AGENTS.md` 的 Workflow 节）。按 RED → GREEN → REFACTOR：

### Step 1 · RED（先改测试，确认失败）
改 `frontend/src/app/styles/visualContract.test.ts`（设计文档 §3 改动 4）：
- 第 38 行：`--workspace-thread-bg: var(--paper-hi)` → `--workspace-thread-bg: var(--warm)`
- 第 50-52 行正则：`--workspace-primary-bg, var\(--ink\)` → `--workspace-action-bg, var\(--red\)`
- 第 55 行后新增两条断言（锁 action token）：
  ```ts
  expect(workspace).toContain('--workspace-action-bg: var(--red)')
  expect(workspace).toContain('--workspace-action-bg-hover: #662522')
  ```

跑测试，确认这 4 处断言失败（RED 成立）：
```powershell
npm.cmd --prefix frontend test -- --run
```

### Step 2 · GREEN（改实现，转绿）
共 4 个文件改动，完整改前/改后见设计文档 §3。这里只列清单：

1. `frontend/src/app/styles/tokens.css` —— 在 `--paper-low` 后新增 `--warm: #f3e8d6;`
2. `frontend/src/features/agent/components/AgentWorkspace.vue` —— `--workspace-thread-bg` 改 `var(--warm)`；末尾新增 `--workspace-action-bg: var(--red);` 和 `--workspace-action-bg-hover: #662522;`（`--workspace-primary-bg` **保持 `var(--ink)` 不变**）
3. `frontend/src/features/agent/components/ConversationThread.vue` —— `.composer button` 的 `background` 由 `--workspace-primary-bg` 改为 `--workspace-action-bg`；其 `:hover` 由 `var(--ink-2)` 改为 `var(--workspace-action-bg-hover, #662522)`
4. （测试文件已在 Step 1 改过）

跑测试确认全绿：
```powershell
npm.cmd --prefix frontend test -- --run
```

### Step 3 · 构建
```powershell
npm.cmd --prefix frontend run build
```
必须通过，无类型错误、无构建失败。

### Step 4 · 视觉验收
启动 dev server（`npm.cmd --prefix frontend run dev`），访问 `/agent`，按设计文档 §4.2 逐条对照 `design-exploration/final-B-red.png`：
- 中栏 `#f3e8d6` 暖燕麦（比左右栏 `#f4eee4` 更暖）
- 发送按钮牛血红 `#7a2e2a` 实底，hover 变 `#662522`
- 新建会话按钮**仍是深墨** `#201c17`
- 激活会话/选中证据仍为牛血红细线
- 文字、边线、composer 容器无残破

## 硬约束（来自 AGENTS.md，违反即返工）

- **生产 Java 禁用 `var` / `record` / Lombok**——本任务只改 CSS + TS 测试，与 Java 无关，但别顺手碰后端。
- **不要动 git**：不 `git add` / `commit` / `push` / `reset` / `restore`。用户未授权任何 git 操作。改完留在工作区即可。
- **不要动设计文档 §3 末尾「不需要改动的文件」清单里的文件**——特别是 `base.css`、`LocalSessionRail.vue`、`EvidenceDesk.vue`、`PaneResizer.vue`、`AgentPage.vue`。它们全走 token，会自动适配。
- **不要新增依赖**。
- **不要扩散到其他路由**（首页/项目页/证据中心走 `paper` 主题，与本任务无关）。

## 一个关键的拆分逻辑（最容易出错的地方）

`--workspace-primary-bg` 这个 token 原来被「新建会话按钮」和「发送按钮」**共用**（都是深墨）。本任务要的是：
- 发送按钮 → 牛血红（用新 token `--workspace-action-bg`）
- 新建会话按钮 → **保持深墨**（继续用 `--workspace-primary-bg`）

所以是**拆分**，不是「改 `--workspace-primary-bg` 的值」。如果把 `--workspace-primary-bg` 直接改成 `var(--red)`，新建会话按钮也会变红——**这是错的**。正确做法见设计文档 §2.3 和 §3 改动 2/3。

## 验收完成后

报告：
1. 跑过的命令 + 结果（test 全绿、build 通过）。
2. 哪些文件改了（应正好是设计文档列的 4 个）。
3. 视觉验收 8 项的对照结论（与 `final-B-red.png` 是否一致）。
4. 任何偏离设计文档的地方及原因（理想情况：无偏离）。

---END PROMPT---
