# Agent Responsive Framed Workspace Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn `warm-cream-compare.html` into a responsive, rounded Agent “work displayed on a dark stage” while retaining palette and CTA comparisons.

**Architecture:** Keep the prototype self-contained. CSS media queries own the three presentation modes and the outer `.frame` owns the only large-radius clipping boundary. A temporary Playwright script under `C:\tmp` verifies geometry and screenshots without adding another repository artifact. The Demo does not reproduce production drawer focus management or resizable panes.

**Tech Stack:** Static HTML/CSS/JavaScript, Playwright Chromium 1.61.1, Node.js ESM.

## Global Constraints

- Only the Agent Demo is implemented in this plan; formal Vue migration requires a later user review and plan.
- Desktop `>=1440px`: `#2a2620` stage, `clamp(24px, 2vw, 32px)` gap, `1600px` maximum shell width, `16px` shell radius, and `0 30px 80px rgba(0, 0, 0, 0.5)` shadow.
- Mid-size `981–1439px`: `16px` stage gap and `12px` shell radius.
- Mobile `<=980px`: zero stage gap, zero shell radius, and no shell shadow.
- Desktop columns are `250px minmax(0, 1fr) 340px`.
- At `<=1220px`, the static Demo hides the evidence pane; at `<=980px`, it also hides the session pane. Production drawers are out of scope.
- The shell owns all four outer corners with `overflow: hidden`; internal panes remain rectangular.
- The Demo Header stays `60px` high and uses `#efe7d8` in warm variants.
- Default view is the approved `v-warm l1` palette with red CTA; A/B/C/D and ink/red controls must continue to work.
- Do not introduce gradients, glassmorphism, repeated panel radii, green status colors, or new dependencies.
- Preserve all user-owned working-tree changes.
- Repository `AGENTS.md` forbids stage, commit, or push without explicit authorization; this plan uses diff review checkpoints instead of commit steps.

---

## File Structure

- Modify `design-exploration/warm-cream-compare.html`: define the responsive shell, stage, columns, density, and narrow-view pane visibility.
- Create temporary `C:\tmp\verify-agent-framed-workspace.mjs`: verify responsive geometry, active defaults, switch behavior, horizontal overflow, console errors, and save five reference screenshots under `C:\tmp\agent-framed-workspace\`.

No new production files or dependencies are required.

### Task 1: Lock the Responsive Visual Contract

**Files:**
- Create temporary test: `C:\tmp\verify-agent-framed-workspace.mjs`
- Test target: `design-exploration/warm-cream-compare.html`

**Interfaces:**
- Consumes: `#frame`, `.stage`, `.workspace`, `.app-header`, `.rail`, `.desk`, `#seg-variant`, and `#seg-cta` from the Demo HTML.
- Produces: a zero exit code only when all responsive geometry and interaction checks pass; PNG files under `C:\tmp\agent-framed-workspace\`.

- [ ] **Step 1: Replace the screenshot-only script with failing responsive assertions**

Use this complete script:

```js
import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

import { chromium } from 'file:///D:/code/agent/frontend/node_modules/playwright/index.mjs'

const demoUrl = pathToFileURL(
  'D:\\code\\agent\\design-exploration\\warm-cream-compare.html',
).href
const outputDir = 'C:\\tmp\\agent-framed-workspace'
await mkdir(outputDir, { recursive: true })

const cases = [
  {
    name: 'desktop-2048',
    width: 2048,
    height: 1080,
    radius: 16,
    rail: true,
    desk: true,
    desktopColumns: true,
  },
  {
    name: 'desktop-1440',
    width: 1440,
    height: 900,
    radius: 16,
    rail: true,
    desk: true,
    desktopColumns: true,
  },
  {
    name: 'compact-1219',
    width: 1219,
    height: 900,
    radius: 12,
    rail: true,
    desk: false,
    desktopColumns: false,
  },
  {
    name: 'mobile-980',
    width: 980,
    height: 800,
    radius: 0,
    rail: false,
    desk: false,
    desktopColumns: false,
  },
  {
    name: 'mobile-390',
    width: 390,
    height: 844,
    radius: 0,
    rail: false,
    desk: false,
    desktopColumns: false,
  },
]

const browser = await chromium.launch()
const issues = []

try {
  for (const current of cases) {
    const page = await browser.newPage({
      viewport: { width: current.width, height: current.height },
    })
    page.on('console', (message) => {
      if (message.type() === 'error') issues.push(`${current.name}: ${message.text()}`)
    })
    page.on('pageerror', (error) => issues.push(`${current.name}: ${error.message}`))

    await page.goto(demoUrl)
    await page.waitForLoadState('domcontentloaded')

    const metrics = await page.evaluate(() => {
      const frame = document.querySelector('#frame')
      const stage = document.querySelector('.stage')
      const workspace = document.querySelector('.workspace')
      const header = document.querySelector('.app-header')
      const rail = document.querySelector('.rail')
      const desk = document.querySelector('.desk')
      if (!frame || !stage || !workspace || !header || !rail || !desk) {
        throw new Error('Required Demo elements are missing')
      }
      const frameStyle = getComputedStyle(frame)
      const stageStyle = getComputedStyle(stage)
      const workspaceStyle = getComputedStyle(workspace)
      return {
        frameClass: frame.className,
        frameWidth: frame.getBoundingClientRect().width,
        frameRadius: Number.parseFloat(frameStyle.borderTopLeftRadius),
        frameShadow: frameStyle.boxShadow,
        stagePaddingLeft: Number.parseFloat(stageStyle.paddingLeft),
        columns: workspaceStyle.gridTemplateColumns
          .split(' ')
          .map((value) => Number.parseFloat(value)),
        headerBackground: getComputedStyle(header).backgroundColor,
        railDisplay: getComputedStyle(rail).display,
        deskDisplay: getComputedStyle(desk).display,
        documentScrollWidth: document.documentElement.scrollWidth,
        documentClientWidth: document.documentElement.clientWidth,
        selectedVariant: document.querySelector('#seg-variant .on')?.dataset.v ?? '',
        selectedCta: document.querySelector('#seg-cta .on')?.dataset.cta ?? '',
      }
    })

    assert.equal(metrics.frameRadius, current.radius, `${current.name} shell radius`)
    assert.equal(metrics.railDisplay !== 'none', current.rail, `${current.name} rail visibility`)
    assert.equal(metrics.deskDisplay !== 'none', current.desk, `${current.name} desk visibility`)
    assert.ok(
      metrics.documentScrollWidth <= metrics.documentClientWidth,
      `${current.name} must not overflow horizontally`,
    )
    assert.equal(metrics.headerBackground, 'rgb(239, 231, 216)', `${current.name} warm header`)
    assert.equal(metrics.selectedVariant, 'v-warm l1', `${current.name} default palette`)
    assert.equal(metrics.selectedCta, 'red', `${current.name} default CTA`)

    if (current.width >= 1440) {
      assert.ok(metrics.stagePaddingLeft >= 24 && metrics.stagePaddingLeft <= 32)
      assert.ok(metrics.frameWidth <= 1600)
      assert.match(metrics.frameShadow, /rgba\(0, 0, 0, 0\.5\)/)
    } else if (current.width > 980) {
      assert.equal(metrics.stagePaddingLeft, 16)
    } else {
      assert.equal(metrics.stagePaddingLeft, 0)
      assert.equal(metrics.frameShadow, 'none')
    }

    if (current.desktopColumns) {
      assert.equal(Math.round(metrics.columns[0]), 250, `${current.name} rail column`)
      assert.equal(
        Math.round(metrics.columns.at(-1)),
        340,
        `${current.name} evidence column`,
      )
    }

    await page.click('#seg-variant button[data-v="v-warm l2"]')
    await page.click('#seg-cta button[data-cta="ink"]')
    assert.match(await page.locator('#frame').getAttribute('class'), /v-warm l2/)
    await page.screenshot({
      path: path.join(outputDir, `${current.name}.png`),
      fullPage: true,
    })
    await page.close()
  }

  assert.deepEqual(issues, [], `Browser issues:\n${issues.join('\n')}`)
  console.log(`PASS: responsive framed Demo verified; screenshots: ${outputDir}`)
} finally {
  await browser.close()
}
```

- [ ] **Step 2: Run the verifier and confirm the new contract fails**

Run:

```powershell
node C:\tmp\verify-agent-framed-workspace.mjs
```

Expected: non-zero exit with the first mismatch, such as the default frame not being `v-warm l1`, desktop column width not being `250px`, or the `1219px` shell radius not being `12px`.

- [ ] **Step 3: Review the isolated test change**

Run `git status --short`.

Expected: the temporary verifier does not appear in repository status. Only pre-existing user changes and the approved design/plan documents are present. Do not stage or commit.

### Task 2: Implement the Rounded Responsive Shell

**Files:**
- Modify: `design-exploration/warm-cream-compare.html`
- Test: `C:\tmp\verify-agent-framed-workspace.mjs`

**Interfaces:**
- Consumes: the selectors and responsive expectations fixed in Task 1.
- Produces: the approved warm/red default Demo and CSS behavior at desktop, compact, and mobile widths.

- [ ] **Step 1: Add exact shell tokens and replace stage/frame/workspace geometry**

Add these tokens to `:root`:

```css
--agent-stage:#2a2620;
--agent-stage-gap:clamp(24px,2vw,32px);
--agent-shell-max:1600px;
--agent-shell-radius:16px;
--agent-shell-shadow:0 30px 80px rgba(0,0,0,.5);
```

Replace the stage, frame, and workspace geometry with:

```css
.stage{
  min-height:calc(100vh - 66px);
  padding:var(--agent-stage-gap);
  display:flex;
  justify-content:center;
  align-items:flex-start;
  background:var(--agent-stage);
}
.frame{
  width:min(100%,var(--agent-shell-max));
  border-radius:var(--agent-shell-radius);
  overflow:hidden;
  background:#fff;
  box-shadow:var(--agent-shell-shadow);
}
.workspace{
  display:grid;
  height:min(760px,calc(100vh - 190px));
  min-height:620px;
  grid-template-columns:250px minmax(0,1fr) 340px;
}
```

Keep `.frame` as the only large-radius container. Do not add radius to `.app-header`, `.rail`, `.conv`, or `.desk`.

- [ ] **Step 2: Tighten the horizontal density**

Apply these exact desktop values:

```css
.app-header{padding-inline:24px;}
.rail{padding-block:18px;}
.rail .new{margin:0 18px 18px;padding:10px 12px;}
.rail li{padding:10px 18px 10px 20px;}
.rail .rail-foot{padding:12px 20px;}
.conv__head{padding:18px 28px 16px;}
.conv__scroll{padding:20px 28px;}
.composer{margin:16px 28px 20px;}
.desk{padding-block:18px;}
.desk .tabs{padding:0 20px 12px;}
.desk .cards{padding:16px 20px;}
.privacy{padding:10px 20px;}
```

Remove or replace the earlier declarations for the same properties so computed styles remain unambiguous.

- [ ] **Step 3: Add compact and mobile media queries**

Append:

```css
@media (max-width:1439px){
  :root{
    --agent-stage-gap:16px;
    --agent-shell-radius:12px;
  }
  .workspace{
    grid-template-columns:clamp(220px,20vw,250px) minmax(0,1fr) clamp(300px,28vw,340px);
  }
}

@media (max-width:1220px){
  .workspace{
    grid-template-columns:220px minmax(0,1fr);
  }
  .desk{display:none;}
  .app-header .right{display:none;}
}

@media (max-width:980px){
  :root{
    --agent-stage-gap:0px;
    --agent-shell-radius:0px;
    --agent-shell-shadow:none;
  }
  .stage{min-height:0;}
  .frame{width:100%;}
  .workspace{
    min-height:680px;
    grid-template-columns:minmax(0,1fr);
  }
  .rail{display:none;}
  .app-header{padding-inline:18px;}
  .app-header nav{gap:14px;}
  .conv__head{padding-inline:20px;}
  .conv__scroll{padding-inline:20px;}
  .composer{margin-inline:20px;}
}

@media (max-width:620px){
  .toolbar{position:relative;padding:12px;}
  .toolbar h1{width:100%;}
  .toolbar .hint{display:none;}
  .seg{max-width:100%;overflow-x:auto;}
  .seg button{flex:0 0 auto;padding-inline:10px;}
  .app-header nav a:not(.on){display:none;}
  .conv__tools .chip{display:none;}
  .msg .bubble,
  .msg--user .bubble{max-width:92%;}
}
```

- [ ] **Step 4: Make the approved warm/red combination the default**

Change:

```html
<div class="frame v-original" id="frame">
```

to:

```html
<div class="frame v-warm l1" id="frame">
```

Move the `on` class from the A variant button to the B variant button, and move it from the ink CTA button to the red CTA button. Change the composer submit class from `send ink` to `send red`.

Delete the unused line:

```js
const baseTemplate = frame.className;
```

The URL query behavior remains unchanged.

- [ ] **Step 5: Run the verifier and confirm all assertions pass**

Run:

```powershell
node C:\tmp\verify-agent-framed-workspace.mjs
```

Expected:

```text
PASS: responsive framed Demo verified; screenshots: C:\tmp\agent-framed-workspace
```

- [ ] **Step 6: Review the implementation diff**

Run:

```powershell
git diff -- design-exploration/warm-cream-compare.html
git status --short
```

Expected: only the planned Demo and verifier edits plus pre-existing user changes. Do not stage or commit.

### Task 3: Visual QA and Handoff

**Files:**
- Verify: `design-exploration/warm-cream-compare.html`
- Verify: `C:\tmp\agent-framed-workspace\*.png`

**Interfaces:**
- Consumes: the five screenshots and successful assertions from Tasks 1–2.
- Produces: an evidence-backed user review checkpoint before any Vue migration.

- [ ] **Step 1: Inspect the five generated screenshots**

Open or inspect:

```text
C:\tmp\agent-framed-workspace\desktop-2048.png
C:\tmp\agent-framed-workspace\desktop-1440.png
C:\tmp\agent-framed-workspace\compact-1219.png
C:\tmp\agent-framed-workspace\mobile-980.png
C:\tmp\agent-framed-workspace\mobile-390.png
```

Confirm visually:

- the shell has one continuous rounded boundary;
- no internal pane has a duplicate outer radius;
- the warm Header and three-column shell read as one object;
- the center column is dominant at `2048` and `1440`;
- hidden auxiliary panes leave no empty columns at `1219`, `980`, or `390`;
- the toolbar remains usable and does not cause horizontal overflow;
- text, CTA, and selected-state contrast remain legible.

- [ ] **Step 2: Re-run the verifier after visual inspection**

Run:

```powershell
node C:\tmp\verify-agent-framed-workspace.mjs
```

Expected: the same PASS result with no console or page errors.

- [ ] **Step 3: Check final working-tree scope**

Run:

```powershell
git status --short
git diff --check
git diff --stat -- design-exploration/warm-cream-compare.html
```

Expected:

- no whitespace errors from the planned files;
- no unplanned production Vue changes from this Demo task;
- existing unrelated user changes remain untouched.

- [ ] **Step 4: Ask for visual approval**

Present the desktop screenshot and summarize only:

- Demo path;
- responsive modes implemented;
- verifier result;
- any remaining visual caveat.

Do not migrate to Vue until the user approves the rendered Demo.
