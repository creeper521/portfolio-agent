# Portfolio Agent Light Workspace Palette Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Agent conversation area's full-height dark stage with the approved A “balanced paper hierarchy” while keeping the dark header, existing layout, interactions, privacy contract, and exactly two solid dark primary buttons.

**Architecture:** Define Agent-only semantic color properties on `.agent-workspace` so scoped child components inherit one approved palette without expanding the global color family. Drive the change with a static visual-contract test and a real-browser acceptance test, then update only CSS presentation in the existing workspace components; no DOM, API, session, retrieval, tool, or answer behavior changes.

**Tech Stack:** Vue 3 scoped CSS, TypeScript, Vitest, Vue Test Utils, Playwright, existing portfolio design tokens.

## Global Constraints

- Implement the approved A “balanced paper hierarchy” from `docs/superpowers/specs/2026-07-22-portfolio-agent-light-workspace-palette-design.md`.
- Keep `--paper`, `--paper-hi`, `--paper-low`, `--ink`, `--ink-2`, `--muted`, `--faint`, `--rule`, `--red`, and `--red-hi` unchanged.
- Do not add blue, green, cyan, purple, gradients, a new color family, shadow cards, message bubbles, avatars, routes, UI libraries, or animation libraries.
- Keep the top navigation dark. Inside the workspace, only “新对话” and “发送” may use a solid `--ink` background.
- Do not change DOM order, request/response data, sessions, handoff, split-pane behavior, drawers, privacy, retrieval, tools, C1/C2/C3, or backend code.
- Preserve drawer breakpoints: evidence drawer below `1220px`; session drawer at `980px` and below.
- Keep visitor questions and answers in page memory only; do not introduce browser or server persistence.
- Use TDD: RED, GREEN, REFACTOR. Run fresh verification before completion.
- Do not stage, commit, push, reset, restore, or delete unrelated user-owned changes.

---

## File Map

- Modify `frontend/src/app/styles/visualContract.test.ts`: static contract for Agent semantic colors and prohibited dark-stage regressions.
- Modify `frontend/src/features/agent/components/AgentWorkspace.vue`: declare semantic workspace properties, root paper background, privacy text, and retain the temporary drawer scrim.
- Modify `frontend/src/features/agent/components/LocalSessionRail.vue`: warm paper rail, subtle active session, and the first solid dark primary button.
- Modify `frontend/src/features/agent/components/ConversationThread.vue`: light conversation, message hierarchy, state colors, composer, and the second solid dark primary button.
- Modify `frontend/src/features/agent/components/EvidenceDesk.vue`: paper evidence rail and subtle active evidence.
- Modify `frontend/src/features/agent/components/PaneResizer.vue`: semantic rule/accent colors without changing its 12px hit target.
- Modify `frontend/src/pages/AgentPage.vue`: light invalid-handoff/loading-adjacent route feedback presentation.
- Modify `frontend/e2e/portfolio.spec.ts`: browser proof for surfaces, button count, message treatment, drawers, and viewport screenshots.
- Modify `docs/superpowers/specs/2026-07-22-portfolio-agent-light-workspace-palette-design.md`: mark the approved design implemented only after all gates pass.
- Modify `docs/00-文档状态索引.md`: register the design and implementation record with their final status.
- Modify `docs/08-current-implementation-status.md`: record the light paper Agent workspace in the current frontend inventory.
- Modify `AGENTS.md`: add the approved light-workspace design to the source-of-truth list.

---

### Task 1: Implement the balanced paper hierarchy under failing visual contracts

**Files:**
- Modify: `frontend/src/app/styles/visualContract.test.ts`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.vue`
- Modify: `frontend/src/features/agent/components/LocalSessionRail.vue`
- Modify: `frontend/src/features/agent/components/ConversationThread.vue`
- Modify: `frontend/src/features/agent/components/EvidenceDesk.vue`
- Modify: `frontend/src/features/agent/components/PaneResizer.vue`
- Modify: `frontend/src/pages/AgentPage.vue`

**Interfaces:**
- Consumes: existing global design tokens from `frontend/src/app/styles/tokens.css`.
- Produces: inheritable Agent properties `--workspace-rail-bg`, `--workspace-thread-bg`, `--workspace-evidence-bg`, `--workspace-surface-subtle`, `--workspace-text`, `--workspace-text-secondary`, `--workspace-text-faint`, `--workspace-rule`, `--workspace-accent`, `--workspace-accent-soft`, `--workspace-primary-bg`, and `--workspace-primary-text`.
- Preserves: all Vue props, emits, DOM order, classes used by behavior tests, breakpoints, and API behavior.

- [ ] **Step 1: Extend the static visual contract with the approved palette rules**

Replace `frontend/src/app/styles/visualContract.test.ts` with the following complete content:

```ts
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('prototype visual contract', () => {
  const tokens = readFileSync(resolve('src/app/styles/tokens.css'), 'utf8')
  const workspace = readFileSync(
    resolve('src/features/agent/components/AgentWorkspace.vue'),
    'utf8',
  )
  const sessions = readFileSync(
    resolve('src/features/agent/components/LocalSessionRail.vue'),
    'utf8',
  )
  const conversation = readFileSync(
    resolve('src/features/agent/components/ConversationThread.vue'),
    'utf8',
  )
  const evidence = readFileSync(
    resolve('src/features/agent/components/EvidenceDesk.vue'),
    'utf8',
  )
  const agentPage = readFileSync(resolve('src/pages/AgentPage.vue'), 'utf8')

  it('uses the prototype content width and palette', () => {
    expect(tokens).toContain('--content-width: 1180px')
    expect(tokens).toContain('--paper: #f4eee4')
    expect(tokens).toContain('--ink: #201c17')
    expect(tokens).toContain('--red: #7a2e2a')
  })

  it('does not define unapproved color families', () => {
    expect(tokens).not.toMatch(/green|teal|cyan|purple/i)
  })

  it('defines the approved balanced-paper Agent hierarchy', () => {
    expect(workspace).toContain('--workspace-rail-bg:')
    expect(workspace).toContain('--workspace-thread-bg: var(--paper-hi)')
    expect(workspace).toContain('--workspace-evidence-bg: var(--paper)')
    expect(workspace).toContain('--workspace-primary-bg: var(--ink)')
    expect(sessions).toContain('background: var(--workspace-rail-bg, var(--paper))')
    expect(conversation).toContain('background: var(--workspace-thread-bg, var(--paper-hi))')
    expect(evidence).toContain('background: var(--workspace-evidence-bg, var(--paper))')
    expect(agentPage).toContain('background: var(--paper-hi)')
  })

  it('removes the dark conversation stage and keeps only the two primary controls solid', () => {
    expect(conversation).not.toContain('background: #29241f')
    expect(conversation).not.toContain('background: var(--ink)')
    expect(conversation).toMatch(
      /\.composer button\s*\{[^}]*background: var\(--workspace-primary-bg, var\(--ink\)\)/s,
    )
    expect(sessions).toMatch(
      /\.session-rail__new\s*\{[^}]*background: var\(--workspace-primary-bg, var\(--ink\)\)/s,
    )
  })
})
```

- [ ] **Step 2: Run the visual contract and confirm RED**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/app/styles/visualContract.test.ts
```

Expected: FAIL in `defines the approved balanced-paper Agent hierarchy` because the workspace properties do not exist; FAIL in `removes the dark conversation stage...` because `ConversationThread.vue` still contains `background: #29241f` and `background: var(--ink)`.

- [ ] **Step 3: Add Agent-only semantic properties and the paper root**

In `frontend/src/features/agent/components/AgentWorkspace.vue`, add the following declarations at the start of `.agent-workspace` and replace its root background:

```css
.agent-workspace {
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
  position: relative;
  display: grid;
  width: 100%;
  height: calc(100vh - var(--header-height));
  grid-template-columns: var(--sessions-width) minmax(600px, 1fr) var(--evidence-width);
  background: var(--workspace-evidence-bg);
  overflow: hidden;
}

.session-privacy {
  position: absolute;
  z-index: 20;
  right: 18px;
  bottom: 4px;
  margin: 0;
  color: var(--workspace-text-faint);
  font: 9px/1.5 var(--mono);
  pointer-events: none;
}
```

Keep `.workspace-scrim` exactly as the temporary drawer overlay:

```css
.workspace-scrim {
  background: rgba(32, 28, 23, 0.5);
}
```

- [ ] **Step 4: Apply the rail, evidence, and resizer surfaces**

In `frontend/src/features/agent/components/LocalSessionRail.vue`, make these exact CSS changes:

```css
.session-rail {
  color: var(--workspace-text, var(--ink));
  border-right: 1px solid var(--workspace-rule, var(--rule));
  background: var(--workspace-rail-bg, var(--paper));
}

.session-rail__new {
  color: var(--workspace-primary-text, var(--paper-hi));
  border: 1px solid var(--workspace-primary-bg, var(--ink));
  background: var(--workspace-primary-bg, var(--ink));
}

.session-rail__new:not(:disabled):hover {
  background: var(--ink-2);
}

article {
  border-top: 1px solid var(--workspace-rule, var(--rule));
}

article.active {
  background: var(--workspace-surface-subtle, var(--paper-low));
  box-shadow: inset 2px 0 var(--workspace-accent, var(--red));
}

footer {
  border-top: 1px solid var(--workspace-rule, var(--rule));
}
```

In `frontend/src/features/agent/components/EvidenceDesk.vue`, make these exact CSS changes:

```css
.evidence-desk {
  color: var(--workspace-text, var(--ink));
  border-left: 1px solid var(--workspace-rule, var(--rule));
  background: var(--workspace-evidence-bg, var(--paper));
}

.evidence-tabs {
  border-bottom: 1px solid var(--workspace-rule, var(--rule));
}

.evidence-list article,
.context-panel {
  border-top: 1px solid var(--workspace-rule, var(--rule));
}

.evidence-list .evidence-card--active {
  border-left: 2px solid var(--workspace-accent, var(--red));
  background: var(--workspace-surface-subtle, var(--paper-low));
}
```

In `frontend/src/features/agent/components/PaneResizer.vue`, replace the two color declarations:

```css
.pane-resizer::after {
  background: var(--workspace-rule, var(--rule));
}

.pane-resizer:hover::after,
.pane-resizer:focus-visible::after,
.pane-resizer.dragging::after {
  background: var(--workspace-accent-soft, var(--red-hi));
}
```

Do not change `.pane-resizer` width, transform, pointer events, ARIA, keyboard handling, or breakpoints.

- [ ] **Step 5: Replace the dark conversation declarations with the approved light mapping**

In `frontend/src/features/agent/components/ConversationThread.vue`, preserve every layout declaration and replace only the following presentation declarations:

```css
.conversation {
  color: var(--workspace-text, var(--ink));
  background: var(--workspace-thread-bg, var(--paper-hi));
}

.conversation__head {
  border-bottom: 1px solid var(--workspace-rule, var(--rule));
}

.conversation__head p,
.thread-empty > p,
.message > p {
  color: var(--workspace-accent-soft, var(--red-hi));
}

.conversation__tools span,
.conversation__tools button {
  color: var(--workspace-text-secondary, var(--muted));
  border: 1px solid var(--workspace-rule, var(--rule));
  background: transparent;
}

.thread-empty {
  border-left: 1px solid var(--workspace-rule, var(--rule));
}

.thread-empty h2 {
  color: var(--workspace-text, var(--ink));
}

.thread-empty button {
  color: var(--workspace-text-secondary, var(--muted));
  border-top: 1px solid var(--workspace-rule, var(--rule));
  background: transparent;
}

.thread-empty button:last-child {
  border-bottom: 1px solid var(--workspace-rule, var(--rule));
}

.thread-empty button span {
  color: var(--workspace-accent-soft, var(--red-hi));
}

.message {
  border-left: 1px solid var(--workspace-rule, var(--rule));
}

.message--user {
  color: var(--workspace-text-secondary, var(--muted));
  border-left: 2px solid var(--workspace-accent, var(--red));
}

.message--agent {
  color: var(--workspace-text, var(--ink));
}

.message footer button,
.follow-up-actions button {
  color: var(--workspace-text-secondary, var(--muted));
  border: 1px solid var(--workspace-rule, var(--rule));
  background: transparent;
}

.context-version-updated {
  color: var(--ink-2);
  border-left: 2px solid var(--workspace-accent-soft, var(--red-hi));
  background: var(--workspace-surface-subtle, var(--paper-low));
}

.answer-state {
  color: var(--workspace-text-secondary, var(--muted));
  border-left: 1px solid var(--workspace-rule, var(--rule));
}

.answer-state--error {
  color: var(--workspace-accent, var(--red));
}

.composer {
  border: 1px solid var(--workspace-rule, var(--rule));
  background: var(--workspace-thread-bg, var(--paper-hi));
}

.composer > span {
  color: var(--workspace-accent-soft, var(--red-hi));
}

textarea {
  color: var(--workspace-text, var(--ink));
  background: transparent;
}

textarea::placeholder {
  color: var(--workspace-text-faint, var(--faint));
}

.composer button {
  color: var(--workspace-primary-text, var(--paper-hi));
  border: 0;
  background: var(--workspace-primary-bg, var(--ink));
}

.composer button:not(:disabled):hover {
  background: var(--ink-2);
}
```

Remove the superseded hard-coded dark-stage values `#29241f`, `#554d44`, `#5a5248`, `#51493f`, `#62594e`, `#b1a596`, and `#ded3c4` from this component. Retain the existing font sizes, spacing, overflow, widths, and media queries.

- [ ] **Step 6: Make invalid-handoff route feedback use the light thread surface**

In `frontend/src/pages/AgentPage.vue`, replace its scoped style with:

```css
.route-seed-feedback {
  display: grid;
  min-height: calc(100vh - var(--header-height));
  place-content: center;
  gap: 18px;
  color: var(--ink);
  background: var(--paper-hi);
  font: 12px/1.7 var(--mono);
}

.route-seed-feedback p {
  margin: 0;
}

.route-seed-feedback a {
  justify-self: center;
  padding: 9px 13px;
  color: var(--red);
  border: 1px solid currentcolor;
  background: transparent;
  font: 10px var(--mono);
}
```

The selector must target the existing `RouterLink` output (`a`), not a nonexistent button.

- [ ] **Step 7: Run focused GREEN verification**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/app/styles/visualContract.test.ts src/features/agent/components/AgentWorkspace.test.ts src/pages/AgentPage.test.ts
```

Expected: all selected test files PASS; the visual contract reports four passing tests.

- [ ] **Step 8: Review the diff for scope containment**

Run:

```powershell
git diff --check
git diff -- frontend/src/app/styles/visualContract.test.ts frontend/src/features/agent/components/AgentWorkspace.vue frontend/src/features/agent/components/LocalSessionRail.vue frontend/src/features/agent/components/ConversationThread.vue frontend/src/features/agent/components/EvidenceDesk.vue frontend/src/features/agent/components/PaneResizer.vue frontend/src/pages/AgentPage.vue
```

Expected: no whitespace errors; no changes to scripts, templates, TypeScript behavior, props, emits, API calls, breakpoints, or privacy text.

- [ ] **Step 9: Commit the tested light workspace implementation**

```powershell
git add frontend/src/app/styles/visualContract.test.ts frontend/src/features/agent/components/AgentWorkspace.vue frontend/src/features/agent/components/LocalSessionRail.vue frontend/src/features/agent/components/ConversationThread.vue frontend/src/features/agent/components/EvidenceDesk.vue frontend/src/features/agent/components/PaneResizer.vue frontend/src/pages/AgentPage.vue
git commit -m "style: lighten agent workspace"
```

---

### Task 2: Add real-browser acceptance and multi-viewport visual evidence

**Files:**
- Modify: `frontend/e2e/portfolio.spec.ts`
- Generated but not committed: `frontend/test-results/**/agent-balanced-paper-*.png`

**Interfaces:**
- Consumes: Task 1 semantic properties and existing Playwright API mocks.
- Produces: browser assertions for the dark header, three light surfaces, exactly two solid dark buttons, non-bubble messages, drawer behavior, and four review screenshots.
- Preserves: existing privacy, handoff, retrieval, model/fallback, responsive, and reduced-motion acceptance tests.

- [ ] **Step 1: Add the balanced-paper browser acceptance test**

Append the following test after `workspace separators support keyboard adjustment and reset` in `frontend/e2e/portfolio.spec.ts`:

```ts
test('Agent uses the approved balanced-paper hierarchy at every review viewport', async ({
  page,
}, testInfo) => {
  const viewports = [
    { name: '1440x900', width: 1440, height: 900 },
    { name: '1219x900', width: 1219, height: 900 },
    { name: '980x800', width: 980, height: 800 },
    { name: '390x844', width: 390, height: 844 },
  ]

  for (const viewport of viewports) {
    await page.setViewportSize({ width: viewport.width, height: viewport.height })
    await openAgentDeepLink(page)

    await expect(page.locator('.dossier-header')).toHaveCSS(
      'background-color',
      'rgba(32, 28, 23, 0.94)',
    )
    await expect(page.locator('.conversation')).toHaveCSS(
      'background-color',
      'rgb(251, 247, 239)',
    )
    await expect(page.locator('.conversation')).toHaveCSS('color', 'rgb(32, 28, 23)')
    await expect(page.locator('.evidence-desk')).toHaveCSS(
      'background-color',
      'rgb(244, 238, 228)',
    )

    const solidInkButtons = await page.locator('.agent-workspace button').evaluateAll(
      (buttons) => buttons
        .filter((button) => getComputedStyle(button).backgroundColor === 'rgb(32, 28, 23)')
        .map((button) => button.textContent?.trim()),
    )
    expect(solidInkButtons).toEqual(expect.arrayContaining(['＋ 新对话', '发送 ↵']))
    expect(solidInkButtons).toHaveLength(2)

    await page.screenshot({
      path: testInfo.outputPath(`agent-balanced-paper-${viewport.name}.png`),
      fullPage: false,
    })
  }
})
```

- [ ] **Step 2: Prove messages remain natural text flows rather than bubbles**

Add the following assertions inside the existing `Agent conversation is page-memory only and disappears on reload` test, immediately after the answer becomes visible and before storage assertions:

```ts
  const userMessage = page.locator('.message--user').last()
  const agentMessage = page.locator('.message--agent').last()
  await expect(userMessage).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)')
  await expect(userMessage).toHaveCSS('border-left-color', 'rgb(122, 46, 42)')
  await expect(agentMessage).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)')
  await expect(agentMessage).toHaveCSS('border-left-color', 'rgb(205, 191, 169)')
```

- [ ] **Step 3: Run the focused browser test**

Run:

```powershell
npm.cmd --prefix frontend run test:e2e -- --project=chromium --grep "balanced-paper hierarchy|page-memory only"
```

Expected: both matching tests PASS. The balanced-paper test writes four screenshots under Playwright's ignored `test-results` output.

- [ ] **Step 4: Inspect all four screenshots**

Open the four `agent-balanced-paper-*.png` files and verify:

```text
1440×900: three visible paper columns; thread is brightest; header stays dark.
1219×900: evidence becomes a paper-colored right drawer; scrim appears only while open.
980×800: session and evidence drawers retain their respective paper colors.
390×844: composer and send button fit without horizontal overflow; text remains readable.
```

If any screenshot violates one of these exact conditions, do not weaken the acceptance test. Correct the scoped CSS in the owning component, rerun Step 3, and regenerate all four screenshots.

- [ ] **Step 5: Run the complete frontend verification**

Run:

```powershell
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
npm.cmd --prefix frontend run test:e2e
```

Expected: Vitest PASS, TypeScript/Vite production build PASS, and both Playwright projects PASS. Conditional retrieval behavior may skip only under its existing documented real-API condition.

- [ ] **Step 6: Commit the browser acceptance test**

```powershell
git add frontend/e2e/portfolio.spec.ts
git commit -m "test: verify light agent workspace"
```

Do not add `frontend/test-results` screenshots to Git.

---

### Task 3: Synchronize authoritative documentation after verified implementation

**Files:**
- Modify: `docs/superpowers/specs/2026-07-22-portfolio-agent-light-workspace-palette-design.md`
- Modify: `docs/superpowers/plans/2026-07-22-portfolio-agent-light-workspace-palette.md`
- Modify: `docs/00-文档状态索引.md`
- Modify: `docs/08-current-implementation-status.md`
- Modify: `AGENTS.md`

**Interfaces:**
- Consumes: fresh passing Task 2 verification output and reviewed screenshots.
- Produces: one unambiguous authority chain showing the light workspace as implemented, without changing product capability claims.

- [ ] **Step 1: Mark the design implemented only after all gates pass**

In `docs/superpowers/specs/2026-07-22-portfolio-agent-light-workspace-palette-design.md`, replace the status line with:

```markdown
> **状态：** 已实施并通过前端测试、构建、Playwright 与多视口视觉验收
```

- [ ] **Step 2: Add the design and plan to the status index**

Add these rows to the current-design area of `docs/00-文档状态索引.md`:

```markdown
| `docs/superpowers/specs/2026-07-22-portfolio-agent-light-workspace-palette-design.md` | 已实施并验证 | Agent 三栏采用 A「均衡纸阶」；顶部保持深色，工作台统一浅色，仅“新对话”和“发送”使用实心深色 |
| `docs/superpowers/plans/2026-07-22-portfolio-agent-light-workspace-palette.md` | 已执行 | 浅色工作台的测试驱动实施、浏览器验收与多视口视觉记录 |
```

- [ ] **Step 3: Update the current frontend inventory**

In `docs/08-current-implementation-status.md`, extend the “公开作品集前端” list with this exact bullet:

```markdown
- Agent 工作台采用 A「均衡纸阶」：顶部导航保持深墨色，三栏使用分级米白纸色，中间对话区最亮；仅“新对话”和“发送”保留实心深色主按钮。
```

- [ ] **Step 4: Register the new authority in `AGENTS.md`**

After the existing source-of-truth item for the public-content integration design, add:

```markdown
7. `docs/superpowers/specs/2026-07-22-portfolio-agent-light-workspace-palette-design.md`
```

Renumber the longer-term roadmap item from 7 to 8. Do not change any product, security, privacy, Java, or verification constraint.

- [ ] **Step 5: Add the execution record to the plan header**

Immediately below the title of this plan, add:

```markdown
> **执行状态（2026-07-22）：** 已按 A「均衡纸阶」实施并通过前端测试、生产构建、Playwright 与 `1440×900 / 1219×900 / 980×800 / 390×844` 视觉验收；下方清单保留测试驱动实施记录。
```

- [ ] **Step 6: Run documentation and working-tree checks**

Run:

```powershell
rg -n "待用户审核|待实现|深色对话舞台" docs/superpowers/specs/2026-07-22-portfolio-agent-light-workspace-palette-design.md docs/00-文档状态索引.md docs/08-current-implementation-status.md AGENTS.md
git diff --check
git status --short
```

Expected: no stale “待用户审核” or “待实现” status for this feature; references to the old deep stage may appear only in historical background explaining what was replaced; no whitespace errors; only the intended documentation files are uncommitted.

- [ ] **Step 7: Commit the verified documentation state**

```powershell
git add AGENTS.md docs/00-文档状态索引.md docs/08-current-implementation-status.md docs/superpowers/specs/2026-07-22-portfolio-agent-light-workspace-palette-design.md docs/superpowers/plans/2026-07-22-portfolio-agent-light-workspace-palette.md
git commit -m "docs: mark light agent workspace implemented"
```

---

## Final Verification Gate

- [ ] Run fresh final verification from the repository root:

```powershell
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
npm.cmd --prefix frontend run test:e2e
git diff --check HEAD~3..HEAD
git status -sb
```

- [ ] Confirm and report all of the following without inference:

```text
1. Exact Vitest file/test totals and zero failures.
2. Production build exit code 0.
3. Exact Playwright pass/skip totals and zero failures.
4. Four reviewed viewport screenshots and their paths.
5. The only persistent large dark surface is the ink header.
6. Exactly two solid ink buttons exist inside the Agent workspace.
7. No backend, API, privacy, persistence, session, retrieval, tool, or model files changed.
8. Clean working tree and no staged files.
```

- [ ] Do not push unless the user explicitly requests it.
