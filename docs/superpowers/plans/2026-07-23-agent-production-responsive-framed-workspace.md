# Agent Production Responsive Framed Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the approved B-warm plus red-CTA framed Demo into the production Vue `/agent` route without regressing resizable panes, responsive drawers, focus behavior, or visitor privacy.

**Architecture:** Reuse the existing `.site-frame--workspace` as the single rounded shell. `#app` provides the dark stage, the Agent header becomes an absolutely positioned warm surface inside the shell, and the workspace consumes the remaining padded content box. Pane widths keep their existing persistence key but clamp to the new fixed defaults and limits.

**Tech Stack:** Vue 3, TypeScript 5.8, scoped CSS, Vitest 3, Vue Test Utils, Playwright 1.53+.

## Global Constraints

- Visual baseline is `design-exploration/warm-cream-compare.html`, default `v-warm l1` plus red CTA.
- Desktop `>=1440px`: stage gap `clamp(24px, 2vw, 32px)`, shell max `1600px`, radius `16px`, shadow `0 30px 80px rgba(0, 0, 0, 0.5)`.
- Mid-size `981–1439px`: stage gap `16px`, radius `12px`.
- Mobile `<=980px`: gap `0`, radius `0`, shadow `none`, full viewport.
- Complete three-column layout starts at `1280px`; evidence becomes a drawer at `<=1279px`; sessions also become a drawer at `<=980px`.
- Pane contract: sessions default `250px`, limits `220–320px`; evidence default `340px`, limits `300–420px`; chat minimum `640px`.
- Preserve persistence key `portfolio.workspace.split.v1`; old values must clamp to the new bounds.
- Preserve current user-owned warm-thread and red-CTA working-tree changes.
- Preserve Pointer Events, double-click reset, Arrow keys, Shift acceleration, Home reset, Escape close, scrim close, focus restore, `aria-expanded`, `aria-hidden`, and `inert`.
- Do not change backend APIs, public content, answer contracts, session privacy, or non-Agent route layout.
- Do not add dependencies, UI frameworks, gradients, repeated pane radii, glassmorphism, or green status colors.
- Do not stage, commit, or push without a new explicit user authorization.

---

## File Structure

- `frontend/src/app/styles/tokens.css`: stage, warm Header, shell geometry tokens.
- `frontend/src/app/styles/base.css`: `#app` stage, normal-route paper coverage, responsive Agent shell, workspace-only Header positioning and warm theme.
- `frontend/src/shared/components/DossierHeader.vue`: select `warm` theme on workspace routes.
- `frontend/src/shared/components/DossierHeader.test.ts`: route-specific Header theme contract.
- `frontend/src/features/agent/composables/useWorkspaceSplit.ts`: fixed defaults and new pane limits.
- `frontend/src/features/agent/composables/useWorkspaceSplit.test.ts`: new bounds, reset values, and legacy clamp.
- `frontend/src/features/agent/components/AgentWorkspace.vue`: height ownership, evidence drawer breakpoint, fixed-drawer geometry, workspace density variables.
- `frontend/src/features/agent/components/AgentWorkspace.test.ts`: evidence drawer media-query contract plus existing interaction regressions.
- `frontend/src/features/agent/components/ConversationThread.vue`: shell-relative height and `28px` horizontal rhythm.
- `frontend/src/features/agent/components/LocalSessionRail.vue`: shell-relative height and `20px` horizontal rhythm.
- `frontend/src/features/agent/components/EvidenceDesk.vue`: shell-relative height and `20px` horizontal rhythm.
- `frontend/src/pages/AgentPage.vue`: shell-relative loading/error/invalid-handoff height.
- `frontend/src/app/styles/visualContract.test.ts`: static design tokens and shell/component contracts.
- `frontend/e2e/portfolio.spec.ts`: real browser geometry, colors, drawers, overflow, and screenshots.
- `docs/04-项目代码约束.md`: authoritative pane and breakpoint contract.
- `docs/00-文档状态索引.md`: current visual baseline status.
- `docs/superpowers/specs/2026-07-23-agent-production-responsive-framed-workspace-design.md`: implementation status.

### Task 1: Lock the Warm Header and Framed Shell Contract

**Files:**
- Modify: `frontend/src/shared/components/DossierHeader.test.ts`
- Modify: `frontend/src/app/styles/visualContract.test.ts`
- Test: the two files above

**Interfaces:**
- Consumes: route meta `workspace: true`, `.site-frame--workspace`, `.dossier-header`, and root CSS tokens.
- Produces: failing tests for the `warm` Header theme and exact shell token/selector contract.

- [ ] **Step 1: Add the failing Agent Header test**

Append inside `describe('DossierHeader', ...)`:

```ts
it('uses the warm theme on the Agent workspace route', async () => {
  const router = createAppRouter(createMemoryHistory())
  await router.push('/agent')
  await router.isReady()

  const wrapper = mount(DossierHeader, {
    global: { plugins: [router] },
  })

  expect(wrapper.get('[data-header-theme]').attributes('data-header-theme')).toBe('warm')
})
```

- [ ] **Step 2: Extend the failing static visual contract**

Add file reads:

```ts
const base = readFileSync(resolve('src/app/styles/base.css'), 'utf8')
const header = readFileSync(
  resolve('src/shared/components/DossierHeader.vue'),
  'utf8',
)
```

Add this test:

```ts
it('defines the approved responsive framed Agent shell', () => {
  expect(tokens).toContain('--agent-stage: #2a2620')
  expect(tokens).toContain('--agent-header: #efe7d8')
  expect(tokens).toContain('--agent-shell-max: 1600px')
  expect(tokens).toContain('--agent-shell-radius: 16px')
  expect(base).toContain('.site-frame--workspace')
  expect(base).toContain('border-radius: var(--agent-shell-radius)')
  expect(base).toContain('box-shadow: var(--agent-shell-shadow)')
  expect(base).toContain(\"[data-header-theme='warm']\")
  expect(header).toContain(\"route.meta.workspace === true ? 'warm' : 'paper'\")
})
```

- [ ] **Step 3: Run the focused tests and verify RED**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/shared/components/DossierHeader.test.ts src/app/styles/visualContract.test.ts
```

Expected: failures showing Agent theme is `ink` and `--agent-stage`/framed-shell selectors are absent.

- [ ] **Step 4: Review test-only scope**

Run:

```powershell
git diff -- frontend/src/shared/components/DossierHeader.test.ts frontend/src/app/styles/visualContract.test.ts
```

Expected: only the new assertions plus pre-existing warm/red visual-contract edits. Do not stage or commit.

### Task 2: Implement the App Stage, Shell, and Warm Header

**Files:**
- Modify: `frontend/src/app/styles/tokens.css`
- Modify: `frontend/src/app/styles/base.css`
- Modify: `frontend/src/shared/components/DossierHeader.vue`
- Test: `frontend/src/shared/components/DossierHeader.test.ts`
- Test: `frontend/src/app/styles/visualContract.test.ts`

**Interfaces:**
- Consumes: Task 1 assertions and the existing `.site-frame--workspace` class from `App.vue`.
- Produces: responsive shell geometry and `data-header-theme="warm"` for `/agent`.

- [ ] **Step 1: Add exact Agent shell tokens**

Append after `--warm` in `tokens.css`:

```css
--agent-stage: #2a2620;
--agent-header: #efe7d8;
--agent-shell-max: 1600px;
--agent-shell-radius: 16px;
--agent-shell-shadow: 0 30px 80px rgba(0, 0, 0, 0.5);
```

- [ ] **Step 2: Change only the workspace Header theme selector**

Replace:

```ts
const theme = computed(() => (route.meta.workspace === true ? 'ink' : 'paper'))
```

with:

```ts
const theme = computed(() => (route.meta.workspace === true ? 'warm' : 'paper'))
```

- [ ] **Step 3: Add the stage and desktop shell styles**

In `base.css`, ensure the app root and ordinary page remain covered:

```css
#app {
  min-height: 100vh;
  background: var(--agent-stage);
}

.site-frame {
  min-height: 100vh;
  background: var(--paper);
}
```

Replace the current minimal `.site-frame--workspace` rule with:

```css
.site-frame--workspace {
  --agent-shell-gap: clamp(24px, 2vw, 32px);
  position: relative;
  width: min(
    var(--agent-shell-max),
    calc(100% - (var(--agent-shell-gap) * 2))
  );
  height: calc(100dvh - (var(--agent-shell-gap) * 2));
  min-height: 640px;
  margin: var(--agent-shell-gap) auto;
  padding-top: var(--header-height);
  border-radius: var(--agent-shell-radius);
  background: var(--paper);
  box-shadow: var(--agent-shell-shadow);
  overflow: hidden;
}

.site-frame--workspace .dossier-header {
  position: absolute;
  inset: 0 0 auto;
  width: 100%;
}
```

Add the warm theme:

```css
.dossier-header[data-header-theme='warm'] {
  color: var(--ink);
  border-color: color-mix(in srgb, var(--rule), transparent 18%);
  background: var(--agent-header);
}
```

Existing `ink` styles may remain for historical compatibility but are no longer selected by `/agent`.

- [ ] **Step 4: Add mid-size and mobile shell overrides**

Inside the existing `@media (max-width: 980px)` block, append:

```css
.site-frame--workspace {
  --agent-shell-gap: 0px;
  width: 100%;
  height: 100dvh;
  min-height: 0;
  margin: 0;
  border-radius: 0;
  box-shadow: none;
}
```

Add before that mobile block:

```css
@media (max-width: 1439px) and (min-width: 981px) {
  .site-frame--workspace {
    --agent-shell-gap: 16px;
    border-radius: 12px;
  }
}
```

- [ ] **Step 5: Run focused tests and verify GREEN**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/shared/components/DossierHeader.test.ts src/app/styles/visualContract.test.ts
```

Expected: both test files pass.

- [ ] **Step 6: Review implementation scope**

Run:

```powershell
git diff -- frontend/src/app/styles/tokens.css frontend/src/app/styles/base.css frontend/src/shared/components/DossierHeader.vue frontend/src/shared/components/DossierHeader.test.ts frontend/src/app/styles/visualContract.test.ts
```

Expected: shell/Header work only plus the user-owned warm/red changes. Do not stage or commit.

### Task 3: Lock New Pane Defaults, Limits, and Drawer Breakpoint

**Files:**
- Modify: `frontend/src/features/agent/composables/useWorkspaceSplit.test.ts`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.test.ts`
- Test: the two files above

**Interfaces:**
- Consumes: persistence key `portfolio.workspace.split.v1`.
- Produces: failing tests for `{ sessions: 250, evidence: 340 }`, new limits, old-value clamp, and evidence media query `(max-width: 1279.98px)`.

- [ ] **Step 1: Replace the pane-bound assertion**

Use:

```ts
it('clamps sessions and evidence widths to the approved framed-shell bounds', () => {
  expect(clampWorkspaceSplit({ sessions: 100, evidence: 900 })).toEqual({
    sessions: 220,
    evidence: 420,
  })
})
```

- [ ] **Step 2: Replace the responsive-default assertion and add legacy compatibility**

Use:

```ts
it('persists keyboard adjustments and resets to the approved fixed defaults', () => {
  const split = useWorkspaceSplit()

  split.adjust('sessions', 16)
  const persisted = JSON.parse(localStorage.getItem(WORKSPACE_SPLIT_KEY) ?? '{}')
  expect(persisted.sessions).toBe(split.state.value.sessions)

  split.reset()
  expect(split.state.value).toEqual({ sessions: 250, evidence: 340 })
})

it('clamps legacy persisted widths without changing the storage key', () => {
  localStorage.setItem(
    WORKSPACE_SPLIT_KEY,
    JSON.stringify({ sessions: 380, evidence: 760 }),
  )

  expect(useWorkspaceSplit().state.value).toEqual({
    sessions: 320,
    evidence: 420,
  })
})
```

- [ ] **Step 3: Lock the new evidence drawer query**

In `AgentWorkspace.test.ts`, change the default `matchMedia` stub to:

```ts
vi.fn((query: string) => ({
  matches: query.includes('1279'),
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
}))
```

Add to the drawer test before mounting:

```ts
const matchMedia = vi.mocked(window.matchMedia)
```

After `const wrapper = mountWorkspace()`, assert:

```ts
expect(matchMedia).toHaveBeenCalledWith('(max-width: 1279.98px)')
expect(matchMedia).toHaveBeenCalledWith('(max-width: 980px)')
```

- [ ] **Step 4: Run focused tests and verify RED**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/features/agent/composables/useWorkspaceSplit.test.ts src/features/agent/components/AgentWorkspace.test.ts
```

Expected: failures showing evidence limit `760`, reset values based on viewport, and the old `1219.98px` query.

### Task 4: Implement Pane Contract, Shell-Relative Heights, and Density

**Files:**
- Modify: `frontend/src/features/agent/composables/useWorkspaceSplit.ts`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.vue`
- Modify: `frontend/src/features/agent/components/ConversationThread.vue`
- Modify: `frontend/src/features/agent/components/LocalSessionRail.vue`
- Modify: `frontend/src/features/agent/components/EvidenceDesk.vue`
- Modify: `frontend/src/pages/AgentPage.vue`
- Modify: `frontend/src/app/styles/visualContract.test.ts`
- Test: `frontend/src/features/agent/composables/useWorkspaceSplit.test.ts`
- Test: `frontend/src/features/agent/components/AgentWorkspace.test.ts`
- Test: `frontend/src/app/styles/visualContract.test.ts`

**Interfaces:**
- Consumes: Task 3 tests and Task 2 shell content box.
- Produces: shell-relative workspace height, exact pane defaults/limits, `1279` evidence drawer, and approved horizontal density.

- [ ] **Step 1: Implement the new split contract**

Replace:

```ts
export const WORKSPACE_LIMITS = {
  sessions: [220, 380] as const,
  evidence: [380, 760] as const,
  chatMin: 600,
}
```

with:

```ts
export const WORKSPACE_LIMITS = {
  sessions: [220, 320] as const,
  evidence: [300, 420] as const,
  chatMin: 640,
}
```

Replace `workspaceDefaults` with:

```ts
export function workspaceDefaults(): WorkspaceSplit {
  return {
    sessions: 250,
    evidence: 340,
  }
}
```

Keep `readWorkspaceSplit()`, `clampWorkspaceSplit()`, storage key, adjust, reset, and persist behavior unchanged.

- [ ] **Step 2: Update the drawer query and workspace height**

In `AgentWorkspace.vue`, replace:

```ts
const evidenceIsDrawer = useMediaQuery('(max-width: 1219.98px)')
```

with:

```ts
const evidenceIsDrawer = useMediaQuery('(max-width: 1279.98px)')
```

Change workspace geometry to:

```css
height: 100%;
grid-template-columns: var(--sessions-width) minmax(640px, 1fr) var(--evidence-width);
```

Delete the entire old block:

```css
@media (max-width: 1279px) and (min-width: 1221px) {
  .agent-workspace {
    --sessions-width: clamp(220px, 18vw, 240px) !important;
    --evidence-width: 380px !important;
  }
}
```

Change the evidence drawer media query from `max-width: 1220px` to `max-width: 1279px`.

For fixed drawers, use:

```css
top: var(--header-height);
bottom: 0;
height: auto;
```

Keep existing transforms, transitions, z-index, scrim, and open classes.

- [ ] **Step 3: Make the three panes shell-relative and tighten density**

In `ConversationThread.vue`:

```css
.conversation {
  height: 100%;
}

.conversation__head {
  padding: 20px 28px 18px;
}

.composer {
  right: 28px;
  left: 28px;
}

@media (max-width: 1279px) {
  .evidence-toggle {
    display: block !important;
  }
}
```

Replace the old `.evidence-toggle` media query at `1220px`; do not keep both breakpoints.

In `LocalSessionRail.vue`:

```css
.session-rail {
  height: 100%;
  padding: 25px 20px;
}
```

In `EvidenceDesk.vue`:

```css
.evidence-desk {
  height: 100%;
  padding: 28px 20px;
}
```

Do not add pane radii.

- [ ] **Step 4: Make route feedback states fill the shell content box**

In `AgentPage.vue`, replace:

```css
min-height: calc(100vh - var(--header-height));
```

with:

```css
min-height: 100%;
```

Add:

```css
:deep(.public-content-feedback) {
  min-height: 100%;
}
```

- [ ] **Step 5: Extend the static contract for heights and density**

Add reads for `base`, `header`, `split`, and assert:

```ts
expect(workspace).toContain('height: 100%')
expect(workspace).toContain('minmax(640px, 1fr)')
expect(workspace).toContain('@media (max-width: 1279px)')
expect(conversation).toContain('right: 28px')
expect(conversation).toContain('left: 28px')
expect(sessions).toContain('padding: 25px 20px')
expect(evidence).toContain('padding: 28px 20px')
expect(agentPage).toContain('min-height: 100%')
```

- [ ] **Step 6: Run the focused tests and verify GREEN**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/features/agent/composables/useWorkspaceSplit.test.ts src/features/agent/components/AgentWorkspace.test.ts src/app/styles/visualContract.test.ts
```

Expected: all focused tests pass.

- [ ] **Step 7: Run the complete unit suite**

Run:

```powershell
npm.cmd --prefix frontend test -- --run
```

Expected: all test files pass with zero failures.

### Task 5: Update Browser Acceptance for the Framed Production UI

**Files:**
- Modify: `frontend/e2e/portfolio.spec.ts`
- Test: `frontend/e2e/portfolio.spec.ts`

**Interfaces:**
- Consumes: the production shell and pane behavior from Tasks 2 and 4.
- Produces: screenshots and browser assertions at `2048`, `1440`, `1279`, `1219`, `980`, and `390`.

- [ ] **Step 1: Replace the review viewport list**

Use:

```ts
const viewports = [
  { name: '2048x1080', width: 2048, height: 1080 },
  { name: '1440x900', width: 1440, height: 900 },
  { name: '1279x900', width: 1279, height: 900 },
  { name: '1219x900', width: 1219, height: 900 },
  { name: '980x800', width: 980, height: 800 },
  { name: '390x844', width: 390, height: 844 },
]
```

- [ ] **Step 2: Replace outdated Header, thread, and primary-button assertions**

Use:

```ts
await expect(page.locator('.dossier-header')).toHaveCSS(
  'background-color',
  'rgb(239, 231, 216)',
)
await expect(page.locator('.conversation')).toHaveCSS(
  'background-color',
  'rgb(243, 232, 214)',
)

const solidInkButtons = await page.locator('.agent-workspace button').evaluateAll(
  (buttons) => buttons
    .filter((button) => getComputedStyle(button).backgroundColor === 'rgb(32, 28, 23)')
    .map((button) => button.textContent?.trim()),
)
expect(solidInkButtons).toEqual(['＋ 新对话'])

const solidRedButtons = await page.locator('.agent-workspace button').evaluateAll(
  (buttons) => buttons
    .filter((button) => getComputedStyle(button).backgroundColor === 'rgb(122, 46, 42)')
    .map((button) => button.textContent?.trim()),
)
expect(solidRedButtons).toEqual(['发送 ↵'])
```

- [ ] **Step 3: Add shell geometry and overflow assertions**

Use:

```ts
const shell = page.locator('.site-frame--workspace')
const shellBox = await shell.boundingBox()
expect(shellBox).not.toBeNull()
expect(
  await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
).toBe(true)

if (viewport.width >= 1440) {
  await expect(shell).toHaveCSS('border-radius', '16px')
  expect(shellBox?.width).toBeLessThanOrEqual(1600)
  expect(shellBox?.x).toBeGreaterThanOrEqual(24)
} else if (viewport.width > 980) {
  await expect(shell).toHaveCSS('border-radius', '12px')
  expect(Math.round(shellBox?.x ?? 0)).toBe(16)
} else {
  await expect(shell).toHaveCSS('border-radius', '0px')
  expect(Math.round(shellBox?.x ?? -1)).toBe(0)
}
```

- [ ] **Step 4: Update drawer checks**

For `1279` and `1219`, open and verify the evidence drawer:

```ts
if (viewport.width === 1279 || viewport.width === 1219) {
  await page.getByRole('button', { name: '证据', exact: true }).click()
  await expect(page.locator('#agent-evidence-desk')).toHaveAttribute('aria-hidden', 'false')
  await expect(page.locator('#agent-evidence-desk')).toHaveCSS(
    'transform',
    'matrix(1, 0, 0, 1, 0, 0)',
  )
}
```

Keep the `980` session-drawer check and add the same session-drawer check for `390`.

- [ ] **Step 5: Rename screenshot output**

Use:

```ts
path: testInfo.outputPath(`agent-framed-workspace-${viewport.name}.png`)
```

- [ ] **Step 6: Run the focused E2E test**

Run:

```powershell
npm.cmd --prefix frontend run test:e2e -- --grep "Agent uses the approved"
```

Expected: the framed-workspace viewport test passes and writes six screenshots under the Playwright test output.

- [ ] **Step 7: Inspect all six screenshots**

Confirm:

- one continuous outer radius;
- no repeated pane radii;
- warm Header belongs to the shell;
- center pane dominates at desktop widths;
- evidence is a drawer at `1279` and `1219`;
- sessions and evidence remain operable at `980` and `390`;
- no horizontal overflow or clipped Composer.

### Task 6: Build, Full E2E, and Documentation Governance

**Files:**
- Modify: `docs/04-项目代码约束.md`
- Modify: `docs/00-文档状态索引.md`
- Modify: `docs/superpowers/specs/2026-07-23-agent-production-responsive-framed-workspace-design.md`
- Verify: all modified frontend files

**Interfaces:**
- Consumes: verified implementation and screenshots.
- Produces: authoritative current documentation plus fresh unit/build/E2E evidence.

- [ ] **Step 1: Update the authoritative pane and breakpoint rules**

In `docs/04-项目代码约束.md`, replace the old Agent layout bullets with:

```markdown
- 面板宽度使用 `portfolio.workspace.split.v1` 保存；旧值按当前边界自动收敛；
- 会话栏默认 `250px`、边界 `220–320px`，证据栏默认 `340px`、边界 `300–420px`，对话栏最低 `640px`；
- `>=1440px` 使用最大 `1600px`、`16px` 圆角的作品窗口；`981–1439px` 使用 `16px` 留边与 `12px` 圆角；`<=980px` 恢复全屏无圆角；
- `>=1280px` 使用完整三栏，`<=1279px` 证据转抽屉，`<=980px` 会话也转抽屉；
```

- [ ] **Step 2: Update the document status index**

Add the production framed-workspace design to the table with status:

```markdown
| `docs/superpowers/specs/2026-07-23-agent-production-responsive-framed-workspace-design.md` | 已实施并验证 | Agent 使用 B 暖调中栏、牛血红 CTA 与响应式圆角作品窗口；1280 起三栏，1279 以下证据抽屉，980 以下双抽屉 |
```

Update the earlier light-workspace entry so it remains a historical visual predecessor rather than the latest layout authority.

- [ ] **Step 3: Mark the design implemented only after verification**

Change the production design status to:

```markdown
> **状态：** 已实施并通过单元测试、构建、Playwright 与多视口视觉验收
```

- [ ] **Step 4: Run fresh unit tests**

Run:

```powershell
npm.cmd --prefix frontend test -- --run
```

Expected: all test files and tests pass.

- [ ] **Step 5: Run the production build**

Run:

```powershell
npm.cmd --prefix frontend run build
```

Expected: `vue-tsc -b && vite build` exits `0`.

- [ ] **Step 6: Run the full frontend E2E suite**

Run:

```powershell
npm.cmd --prefix frontend run test:e2e
```

Expected: all Playwright tests pass.

- [ ] **Step 7: Verify final scope and whitespace**

Run:

```powershell
git diff --check
git status --short
git diff --stat
```

Expected:

- no whitespace errors;
- no backend or public-data files modified;
- pre-existing user changes preserved;
- no staged files and no commit.

- [ ] **Step 8: Present the rendered result for user approval**

Show the `1440×900` production screenshot and report:

- unit test count;
- build result;
- E2E result;
- changed visual behavior;
- no Git stage/commit.

Do not perform any branch integration action without explicit authorization.
