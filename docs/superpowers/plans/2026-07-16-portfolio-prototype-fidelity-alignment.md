# Portfolio Prototype Fidelity Alignment Implementation Plan

> **执行状态（2026-07-20）：** 已执行。纸张/墨色/暗红视觉和 Agent 呈现已落地；reduced-motion、焦点可见性、动态播报和抽屉焦点仍需后续修复。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Align the existing Vue portfolio frontend with the approved HTML prototype while preserving its public-data boundary, multi-route architecture, local sessions, and responsive Agent workspace.

**Architecture:** Keep repository, routing, session, and split-state layers intact. Replace the presentation layer with prototype-derived layout primitives, introduce a typed homepage-to-Agent seed boundary, and validate fidelity with component tests plus same-viewport Playwright screenshots.

**Tech Stack:** Vue 3.5, TypeScript 5.8, Vue Router 4.5, Vite 7, Vitest, Vue Test Utils, Playwright.

## Global Constraints

- Target prototype: `C:\Users\WIN10\Documents\杂项\实习学习-Obsidian\.superpowers\brainstorm\homepage-flow-20260715\content\portfolio-home-prototype-v1.html`.
- Public runtime data may come only from the approved public content repository.
- Do not invent owner name, contact details, project counts, evidence, dates, outcomes, or contribution claims.
- Keep routes `/`, `/projects`, `/projects/:slug`, `/timeline`, `/evidence`, and `/agent`.
- Keep local Agent sessions browser-only with a seven-day expiry.
- Keep Agent split limits: sessions `220–380px`, evidence `380–760px`, chat minimum `600px`.
- Use only the approved paper, ink, muted, rule, and red palette; do not add green, teal, cyan, blue, purple, glow, or decorative gradients.
- Do not add Pinia, a UI framework, an animation library, or new production dependencies.
- Use TDD for each task: failing test, focused implementation, passing test, refactor.
- Preserve user-owned working-tree changes.
- Do not stage or commit unless the user separately authorizes Git writes.

---

## File Structure

### Global shell and style responsibilities

- `frontend/src/app/styles/tokens.css`: exact prototype color, type, width, spacing, and motion tokens.
- `frontend/src/app/styles/base.css`: reset, page shell, header themes, navigation, focus, and mobile menu.
- `frontend/src/app/styles/motion.css`: reveal, scan, answer cursor, evidence emphasis, and reduced-motion behavior.
- `frontend/src/shared/components/DossierHeader.vue`: homepage anchor navigation, route navigation, Agent dark mode, and mobile directory.
- `frontend/src/shared/components/DossierFooter.vue`: public About, optional Contact, Privacy, and edition metadata.
- `frontend/src/shared/components/PageLead.vue`: compact paper-first route header with optional local dark emphasis.

### Homepage responsibilities

- `frontend/src/features/home/components/PortfolioHero.vue`: prototype-faithful paper Hero.
- `frontend/src/features/home/components/CredibilitySummary.vue`: section introduction and factual metrics.
- `frontend/src/features/home/components/ExploreIndex.vue`: paper index with per-item dark hover.
- `frontend/src/features/audience/components/AudienceDialogue.vue`: role selection, questions, free-form input, round state, and answer composition.
- `frontend/src/features/audience/components/LightAnswerPanel.vue`: answer metadata, typing state, citations, and handoff actions.
- `frontend/src/features/audience/model/audienceTypes.ts`: role profile and homepage answer state types.
- `frontend/src/features/audience/data/audienceProfiles.ts`: four roles with four factual questions each.
- `frontend/src/pages/HomePage.vue`: four-layer orchestration and footer.

### Agent responsibilities

- `frontend/src/features/agent/model/sessionTypes.ts`: session messages and route-seed types.
- `frontend/src/features/agent/data/previewAnswers.ts`: deterministic public answer composition.
- `frontend/src/features/agent/composables/useLocalSessions.ts`: idempotent seed initialization.
- `frontend/src/features/agent/components/AgentWorkspace.vue`: split layout, drawers, evidence focus, and seed orchestration.
- `frontend/src/features/agent/components/LocalSessionRail.vue`: compact prototype session rail.
- `frontend/src/features/agent/components/ConversationThread.vue`: linear message flow and composer.
- `frontend/src/features/agent/components/EvidenceDesk.vue`: tabbed evidence context list.
- `frontend/src/features/agent/components/PaneResizer.vue`: invisible hit target with one-pixel rule.

### Route page responsibilities

- `frontend/src/pages/ProjectsPage.vue`: compact paper project index.
- `frontend/src/pages/ProjectPage.vue`: dossier detail using the shared visual grammar.
- `frontend/src/pages/TimelinePage.vue`: paper-first growth ledger.
- `frontend/src/pages/EvidencePage.vue`: rule-based evidence catalog and preview.

### Verification responsibilities

- `frontend/e2e/portfolio.spec.ts`: user flows, responsive drawers, accessibility behavior, and overflow.
- `frontend/scripts/visual-audit.mjs`: same-viewport screenshots and computed-style checks.

---

### Task 1: Lock the prototype visual contract in tests and tokens

**Files:**
- Modify: `frontend/src/app/styles/tokens.css`
- Modify: `frontend/src/app/styles/base.css`
- Modify: `frontend/src/app/styles/motion.css`
- Modify: `frontend/src/pages/HomePage.test.ts`
- Create: `frontend/src/app/styles/visualContract.test.ts`

**Interfaces:**
- Consumes: existing CSS imports from `frontend/src/app/styles/main.css`.
- Produces: CSS variables `--content-width`, `--header-height`, `--ease`, and shared classes `.page-shell`, `.eyebrow`, `.paper-surface`.

- [x] **Step 1: Write a failing visual-token test**

```ts
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

describe('prototype visual contract', () => {
  const tokens = readFileSync(
    new URL('./tokens.css', import.meta.url),
    'utf8',
  )

  it('uses the prototype content width and palette', () => {
    expect(tokens).toContain('--content-width: 1180px')
    expect(tokens).toContain('--paper: #f4eee4')
    expect(tokens).toContain('--ink: #201c17')
    expect(tokens).toContain('--red: #7a2e2a')
  })

  it('does not define unapproved color families', () => {
    expect(tokens).not.toMatch(/green|teal|cyan|purple/i)
  })
})
```

- [x] **Step 2: Extend the homepage structure test**

```ts
expect(wrapper.get('[data-home-layer="hero"]').classes()).toContain('paper-surface')
expect(wrapper.get('[data-home-layer="explore"]').classes()).toContain('paper-surface')
expect(wrapper.find('[data-dossier-footer]').exists()).toBe(true)
```

- [x] **Step 3: Run the focused tests and verify RED**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/app/styles/visualContract.test.ts src/pages/HomePage.test.ts
```

Expected: FAIL because `--content-width`, `paper-surface`, and the footer do not exist.

- [x] **Step 4: Replace layout tokens with the prototype contract**

```css
:root {
  --paper: #f4eee4;
  --paper-hi: #fbf7ef;
  --paper-low: #e9dfd0;
  --ink: #201c17;
  --ink-2: #34302a;
  --muted: #746b5e;
  --faint: #9c9182;
  --rule: #cdbfa9;
  --red: #7a2e2a;
  --red-hi: #b65d53;
  --serif: Georgia, 'Noto Serif SC', 'Songti SC', serif;
  --sans: 'Noto Sans SC', 'Microsoft YaHei', sans-serif;
  --mono: 'IBM Plex Mono', 'SFMono-Regular', Consolas, monospace;
  --header-height: 66px;
  --content-width: 1180px;
  --page-gutter: clamp(22px, 4vw, 48px);
  --ease: cubic-bezier(0.22, 0.8, 0.22, 1);
}
```

- [x] **Step 5: Add shared paper and focus primitives**

```css
.page-shell {
  width: min(var(--content-width), calc(100% - (var(--page-gutter) * 2)));
  margin-inline: auto;
}

.paper-surface {
  color: var(--ink);
  background: var(--paper);
}

.eyebrow {
  color: var(--ink-2);
  font: 12px/1.4 var(--mono);
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

:focus-visible {
  outline: 2px solid var(--red);
  outline-offset: 4px;
}
```

- [x] **Step 6: Add prototype motion and reduced-motion fallbacks**

```css
@keyframes hero-shift {
  from { opacity: 0; transform: translateY(18px); }
  to { opacity: 1; transform: translate(8%, 0); }
}

@keyframes dossier-scan {
  0%, 100% { transform: translateX(-120%); }
  50% { transform: translateX(260%); }
}

@media (prefers-reduced-motion: reduce) {
  html { scroll-behavior: auto; }
  *, *::before, *::after {
    animation: none !important;
    transition: none !important;
  }
}
```

- [x] **Step 7: Run focused tests**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/app/styles/visualContract.test.ts
```

Expected: PASS.

---

### Task 2: Restore the prototype Header, Hero, and footer

**Files:**
- Modify: `frontend/src/shared/components/DossierHeader.vue`
- Create: `frontend/src/shared/components/DossierHeader.test.ts`
- Create: `frontend/src/shared/components/DossierFooter.vue`
- Modify: `frontend/src/features/home/components/PortfolioHero.vue`
- Modify: `frontend/src/pages/HomePage.vue`
- Modify: `frontend/src/pages/HomePage.test.ts`
- Modify: `frontend/src/app/App.vue`

**Interfaces:**
- Consumes: current route meta, `PublicOwner`, content version, published date.
- Produces: `DossierHeader` variants `home`, `route`, `workspace`; `DossierFooter` with optional contact fields.

- [x] **Step 1: Write failing Header and Hero assertions**

```ts
expect(wrapper.get('[data-hero-index]').text()).toContain('FACTS BEFORE WORDS')
expect(wrapper.get('[data-hero-primary-action]').attributes('href')).toBe('#credibility')
expect(wrapper.get('[data-hero-question-action]').attributes('href')).toBe('#dialogue')
expect(wrapper.find('[data-owner-placeholder]').exists()).toBe(false)
```

Create `DossierHeader.test.ts` with route-aware Header coverage:

```ts
import { mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { describe, expect, it } from 'vitest'

import { createAppRouter } from '../../app/router'
import DossierHeader from './DossierHeader.vue'

describe('DossierHeader', () => {
  it('uses the paper theme and homepage anchors on the homepage', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/')
    await router.isReady()
    const wrapper = mount(DossierHeader, {
      global: { plugins: [router] },
    })
    expect(wrapper.get('[data-header-theme]').attributes('data-header-theme')).toBe('paper')
    expect(wrapper.findAll('[data-home-anchor]')).toHaveLength(3)
  })
})
```

- [x] **Step 2: Run focused tests and verify RED**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/pages/HomePage.test.ts src/shared/components/DossierHeader.test.ts
```

Expected: FAIL because the prototype index, actions, and footer are absent.

- [x] **Step 3: Implement route-aware Header semantics**

```ts
const theme = computed(() => {
  if (route.meta.workspace === true) return 'ink'
  return 'paper'
})

const homeAnchors = [
  { label: '概览', href: '#credibility' },
  { label: '对话', href: '#dialogue' },
  { label: '探索', href: '#explore' },
]
```

The template must render homepage anchors only on `/`; other routes keep RouterLinks.

- [x] **Step 4: Rebuild `PortfolioHero.vue` around the source structure**

```vue
<section class="portfolio-hero paper-surface" data-home-layer="hero">
  <div class="portfolio-hero__paper">
    <div class="portfolio-hero__index" data-hero-index>
      <span>VOL. 01 · {{ version }}</span>
      <span>FACTS BEFORE WORDS</span>
    </div>
    <div class="portfolio-hero__copy">
      <p class="portfolio-hero__chapter">ENGINEERING DOSSIER / 00</p>
      <h1>
        <span v-if="owner.name">{{ owner.name }}</span>
        <span v-else>{{ owner.role }}</span>
        <em>工程实践档案</em>
      </h1>
      <p class="portfolio-hero__lead">{{ owner.summary }}</p>
      <div class="portfolio-hero__actions">
        <a data-hero-primary-action href="#credibility">开始浏览 ↓</a>
        <a data-hero-question-action href="#dialogue">直接提问 →</a>
      </div>
    </div>
    <div class="portfolio-hero__scroll"><i></i><span>SCROLL TO INSPECT</span></div>
  </div>
</section>
```

- [x] **Step 5: Use prototype Hero dimensions**

```css
.portfolio-hero {
  height: clamp(680px, 76svh, 780px);
  overflow: hidden;
  border-bottom: 1px solid var(--rule);
}

.portfolio-hero__paper {
  position: relative;
  height: 100%;
  padding: 104px max(48px, calc((100vw - var(--content-width)) / 2)) 48px;
}

.portfolio-hero h1 {
  max-width: 10ch;
  margin: 22px 0 23px;
  font: 700 clamp(58px, 6.5vw, 92px) / 0.94 var(--serif);
  letter-spacing: -0.045em;
}

.portfolio-hero h1 em {
  display: block;
  color: var(--red);
  font-weight: 400;
  animation: hero-shift 1.1s 0.35s var(--ease) forwards;
}
```

- [x] **Step 6: Add the shared footer without placeholders**

```vue
<footer data-dossier-footer>
  <div class="page-shell dossier-footer__grid">
    <section>
      <h2>ABOUT THIS DOSSIER</h2>
      <p>内容来自经过公开审查的个人工程记录。Agent 负责查询与表达，不修改事实。</p>
    </section>
    <section v-if="contacts.length">
      <h2>CONTACT</h2>
      <a v-for="contact in contacts" :key="contact.href" :href="contact.href">
        {{ contact.label }}
      </a>
    </section>
    <section>
      <h2>PRIVACY</h2>
      <p>无需登录；会话只保存在当前浏览器，并在 7 天后自动过期。</p>
    </section>
  </div>
</footer>
```

- [x] **Step 7: Run focused tests and build**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/pages/HomePage.test.ts src/shared/components/DossierHeader.test.ts
npm.cmd --prefix frontend run build
```

Expected: PASS and successful build.

---

### Task 3: Restore credibility and exploration sections

**Files:**
- Modify: `frontend/src/features/home/components/CredibilitySummary.vue`
- Modify: `frontend/src/features/home/components/ExploreIndex.vue`
- Modify: `frontend/src/pages/HomePage.test.ts`

**Interfaces:**
- Consumes: `PublicPortfolio`.
- Produces: factual `CredibilityMetric[]` and four fixed route entries.

- [x] **Step 1: Write failing metric and exploration tests**

```ts
expect(wrapper.get('[data-home-layer="credibility"]').text()).toContain('先建立可信度')
expect(wrapper.findAll('[data-credibility-metric]')).toHaveLength(3)
expect(wrapper.get('[data-home-layer="explore"]').classes()).toContain('paper-surface')
expect(wrapper.findAll('[data-explore-entry]')).toHaveLength(4)
```

- [x] **Step 2: Verify RED**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/pages/HomePage.test.ts
```

Expected: FAIL because the expected data attributes and paper exploration style are absent.

- [x] **Step 3: Build factual metric view models**

```ts
const metrics = computed(() =>
  [
    {
      value: props.portfolio.projects.length,
      code: 'PROJECTS',
      label: '核心项目',
      description: '项目状态与个人贡献按独立标记。',
      to: '/projects',
    },
    {
      value: props.portfolio.evidence.length,
      code: 'RECORDS',
      label: '脱敏证据',
      description: '只展示通过公开审查的索引。',
      to: '/evidence',
    },
    {
      value: props.portfolio.timeline.length,
      code: 'MILESTONES',
      label: '成长记录',
      description: '记录问题、行动与能力影响。',
      to: '/timeline',
    },
  ].filter((metric) => metric.value > 0),
)
```

- [x] **Step 4: Restore section introduction and metric grid**

```vue
<section id="credibility" class="credibility paper-surface" data-home-layer="credibility">
  <div class="page-shell">
    <div class="section-intro">
      <p class="eyebrow">01 · VERIFIED OVERVIEW</p>
      <div>
        <h2>先建立可信度，再进入细节。</h2>
        <p>首页只展示能够由公开内容核对的规模摘要。</p>
      </div>
    </div>
    <div class="credibility__grid">
      <RouterLink v-for="metric in metrics" :key="metric.code" data-credibility-metric :to="metric.to">
        <strong>{{ String(metric.value).padStart(2, '0') }}</strong>
        <span>{{ metric.code }} · {{ metric.label }}</span>
        <p>{{ metric.description }}</p>
        <i>↗</i>
      </RouterLink>
    </div>
  </div>
</section>
```

- [x] **Step 5: Restore paper exploration with local dark hover**

```css
.explore-index {
  padding: 86px 0 96px;
  color: var(--ink);
  background: var(--paper);
}

.explore-index__grid a {
  display: flex;
  min-height: 260px;
  padding: 24px 22px 25px;
  flex-direction: column;
  border-right: 1px solid var(--rule);
  transition: 0.35s var(--ease);
}

.explore-index__grid a:hover {
  color: var(--paper);
  background: var(--ink);
  transform: translateY(-8px);
}
```

- [x] **Step 6: Run focused tests**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/pages/HomePage.test.ts
```

Expected: PASS.

---

### Task 4: Rebuild homepage light conversation and answer handoff

**Files:**
- Modify: `frontend/src/features/audience/model/audienceTypes.ts`
- Modify: `frontend/src/features/audience/data/audienceProfiles.ts`
- Create: `frontend/src/features/audience/components/LightAnswerPanel.vue`
- Modify: `frontend/src/features/audience/components/AudienceDialogue.vue`
- Modify: `frontend/src/features/audience/components/AudienceDialogue.test.ts`
- Modify: `frontend/src/features/agent/model/sessionTypes.ts`
- Modify: `frontend/src/features/agent/data/previewAnswers.ts`

**Interfaces:**
- Consumes: `PublicPortfolio`, `AudienceProfile`, `createPreviewAnswer()`.
- Produces:

```ts
export interface HomeAnswerState {
  round: number
  question: string
  answer: string
  projectSlug: string | null
  evidenceIds: string[]
}

export interface AgentRouteSeed {
  role: AudienceRole
  question: string
  answer: string
  projectSlug: string | null
  evidenceIds: string[]
  source: 'HOME' | 'PROJECT' | 'EVIDENCE'
}
```

- [x] **Step 1: Write failing role, free-form, and answer tests**

```ts
expect(wrapper.findAll('[data-role]')).toHaveLength(4)
expect(wrapper.findAll('[data-question]')).toHaveLength(4)
expect(wrapper.get('[data-role="INTERVIEWER"]').attributes('aria-pressed')).toBe('true')
expect(wrapper.get('[data-role="INTERVIEWER"]').classes()).toContain('role-button--active')

await wrapper.get('[data-custom-question]').setValue('如何处理连接异常？')
await wrapper.get('[data-question-form]').trigger('submit')

expect(wrapper.get('[data-light-answer]').text()).toContain('如何处理连接异常？')
expect(wrapper.findAll('[data-answer-action]')).toHaveLength(3)
```

- [x] **Step 2: Verify RED**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/features/audience/components/AudienceDialogue.test.ts
```

Expected: FAIL because there are two questions, no custom form, and no separate answer panel.

- [x] **Step 3: Expand each role to four factual questions**

```ts
{
  id: 'INTERVIEWER',
  code: 'DEFAULT',
  label: '技术面试官',
  description: '回答侧重技术方案、取舍和实现细节，每个结论标注状态与证据。',
  questions: [
    '介绍 SQL 审计工具的完整迭代。',
    '查询为什么需要设计成异步？',
    '这个项目中最关键的技术决策是什么？',
    '哪些是完成交付，哪些只是学习？',
  ],
}
```

Apply equivalent four-question arrays for `MENTOR`, `HR`, and `GUEST`, using only public project fields.

- [x] **Step 4: Use one deterministic answer function for Home and Agent**

```ts
export function createPreviewAnswer(
  question: string,
  role: AudienceRole,
  project: PublicProject,
  evidence: PublicEvidence[],
): PreviewAnswer {
  const citedEvidence = evidence.filter((item) =>
    project.evidenceIds.includes(item.id),
  )
  return {
    content: composeAnswerFromPublicProject(question, role, project),
    evidenceIds: citedEvidence.map((item) => item.id),
  }
}
```

`composeAnswerFromPublicProject()` must derive every sentence from `summary`, `solution`, `keyDecisions`, `verification`, `outcome`, or `handoff`.

- [x] **Step 5: Implement compact role and question columns**

```vue
<div class="audience-console">
  <section class="role-side">
    <p class="eyebrow">SELECT YOUR ROLE</p>
    <h3>你是谁？</h3>
    <div class="role-grid">
      <button
        v-for="profile in audienceProfiles"
        :key="profile.id"
        :class="{ 'role-button--active': profile.id === selectedRole.id }"
        :aria-pressed="profile.id === selectedRole.id"
        :data-role="profile.id"
        @click="chooseRole(profile)"
      >
        <small>{{ profile.code }}</small>
        {{ profile.label }}
      </button>
    </div>
    <p class="role-description">{{ selectedRole.description }}</p>
  </section>
  <section class="question-side">
    <p class="eyebrow">SUGGESTED QUESTIONS</p>
    <h3>推荐问题</h3>
    <button v-for="(item, index) in selectedRole.questions" :key="item" data-question>
      <b>Q·{{ String(index + 1).padStart(2, '0') }}</b>
      <span>{{ item }}</span>
      <i>→</i>
    </button>
    <form data-question-form @submit.prevent="ask(customQuestion)">
      <input v-model="customQuestion" data-custom-question />
      <button type="submit">发送 ↵</button>
    </form>
  </section>
</div>
```

- [x] **Step 6: Implement `LightAnswerPanel.vue`**

```vue
<section data-light-answer class="light-answer">
  <aside>
    <b>{{ role }}</b>
    <span>ROUND {{ String(answer.round).padStart(2, '0') }} / 03</span>
    <span>MODE<br />VERIFIED SUMMARY</span>
  </aside>
  <div>
    <p>YOU · {{ answer.question }}</p>
    <div class="light-answer__text">{{ visibleAnswer }}</div>
    <p v-if="complete" class="light-answer__status">◆ STRUCTURED FACTS CHECKED</p>
    <div v-if="complete" class="light-answer__actions">
      <button data-answer-action @click="$emit('follow-up')">继续追问</button>
      <RouterLink data-answer-action :to="projectTarget">查看相关项目</RouterLink>
      <RouterLink data-answer-action :to="agentTarget">带着上下文进入 Agent →</RouterLink>
    </div>
  </div>
</section>
```

Typing animation must reveal the existing complete answer string and skip animation under reduced motion.

- [x] **Step 7: Run focused tests**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/features/audience/components/AudienceDialogue.test.ts src/pages/HomePage.test.ts
```

Expected: PASS.

---

### Task 5: Make homepage-to-Agent initialization idempotent

**Files:**
- Modify: `frontend/src/pages/AgentPage.vue`
- Modify: `frontend/src/features/agent/composables/useLocalSessions.ts`
- Modify: `frontend/src/features/agent/composables/useLocalSessions.test.ts`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.vue`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.test.ts`

**Interfaces:**
- Consumes: `AgentRouteSeed`.
- Produces:

```ts
seedSession(input: AgentRouteSeed): AgentSession
```

The seed fingerprint is:

```ts
`${input.source}:${input.role}:${input.projectSlug ?? ''}:${input.question}`
```

- [x] **Step 1: Write failing seed tests**

```ts
const homeSeed: AgentRouteSeed = {
  role: 'INTERVIEWER',
  question: '介绍 SQL 审计工具的完整迭代。',
  answer: '该项目从固定路径查询演进为可配置、可恢复、可追溯的交付工具。',
  projectSlug: 'sql-audit',
  evidenceIds: ['sql-audit-delivery-set'],
  source: 'HOME',
}

it('creates user and agent messages from a homepage seed', () => {
  const store = useLocalSessions()
  const session = store.seedSession(homeSeed)
  expect(session.messages.map((item) => item.role)).toEqual(['USER', 'AGENT'])
  expect(session.messages[1].evidenceIds).toEqual(homeSeed.evidenceIds)
})

it('does not insert the same seed twice', () => {
  const store = useLocalSessions()
  store.seedSession(homeSeed)
  const session = store.seedSession(homeSeed)
  expect(session.messages).toHaveLength(2)
})
```

- [x] **Step 2: Verify RED**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/features/agent/composables/useLocalSessions.test.ts
```

Expected: FAIL because `seedSession()` does not exist.

- [x] **Step 3: Add seed metadata to sessions**

```ts
export interface AgentSession {
  id: string
  title: string
  role: AudienceRole
  projectSlug: string | null
  evidenceId: string | null
  seedFingerprint: string | null
  createdAt: number
  updatedAt: number
  messages: AgentMessage[]
}
```

- [x] **Step 4: Implement idempotent `seedSession()`**

```ts
function seedSession(input: AgentRouteSeed): AgentSession {
  const fingerprint =
    `${input.source}:${input.role}:${input.projectSlug ?? ''}:${input.question}`
  const existing = sessions.value.find(
    (session) => session.seedFingerprint === fingerprint,
  )
  if (existing) {
    activeSessionId.value = existing.id
    return existing
  }

  const session = createSession({
    role: input.role,
    projectSlug: input.projectSlug,
    evidenceId: input.evidenceIds[0] ?? null,
  })
  session.seedFingerprint = fingerprint
  appendMessage(session.id, {
    role: 'USER',
    content: input.question,
    evidenceIds: [],
  })
  appendMessage(session.id, {
    role: 'AGENT',
    content: input.answer,
    evidenceIds: input.evidenceIds,
  })
  return session
}
```

- [x] **Step 5: Let `AgentPage.vue` rebuild missing answer state**

```ts
const seed = computed<AgentRouteSeed | null>(() => {
  const question = queryString('question')
  const project = selectedProject.value
  if (!question || !project) return null
  const answer = createPreviewAnswer(
    question,
    initialRole(),
    project,
    portfolio.value?.evidence ?? [],
  )
  return {
    role: initialRole(),
    question,
    answer: answer.content,
    projectSlug: project.slug,
    evidenceIds: answer.evidenceIds,
    source: 'HOME',
  }
})
```

- [x] **Step 6: Seed the workspace once**

```ts
onMounted(() => {
  if (props.initialSeed) {
    sessions.seedSession(props.initialSeed)
    activeEvidenceId.value = props.initialSeed.evidenceIds[0] ?? ''
  } else if (!sessions.activeSession.value) {
    createSession()
  }
})
```

- [x] **Step 7: Run Agent tests**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/features/agent/composables/useLocalSessions.test.ts src/features/agent/components/AgentWorkspace.test.ts
```

Expected: PASS.

---

### Task 6: Align the Agent workspace presentation and drawer accessibility

**Files:**
- Modify: `frontend/src/features/agent/components/AgentWorkspace.vue`
- Modify: `frontend/src/features/agent/components/LocalSessionRail.vue`
- Modify: `frontend/src/features/agent/components/ConversationThread.vue`
- Modify: `frontend/src/features/agent/components/EvidenceDesk.vue`
- Modify: `frontend/src/features/agent/components/PaneResizer.vue`
- Create: `frontend/src/shared/composables/useMediaQuery.ts`
- Create: `frontend/src/shared/composables/useMediaQuery.test.ts`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.test.ts`

**Interfaces:**
- Consumes: existing split state, sessions, project, evidence, and active evidence ID.
- Produces: tab type `EvidenceDeskTab = 'EVIDENCE' | 'PROJECT' | 'TIMELINE' | 'CONTEXT'`.

- [x] **Step 1: Write failing presentation and accessibility tests**

```ts
expect(wrapper.get('.agent-workspace').classes()).toContain('agent-workspace--prototype')
expect(wrapper.find('.thread-empty-card').exists()).toBe(false)
expect(wrapper.find('.message--user-card').exists()).toBe(false)

await wrapper.get('.evidence-toggle').trigger('click')
expect(wrapper.get('#local-session-rail').attributes('inert')).toBeDefined()
expect(wrapper.get('#agent-evidence-desk').attributes('aria-hidden')).toBe('false')
```

- [x] **Step 2: Verify RED**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/features/agent/components/AgentWorkspace.test.ts
```

Expected: FAIL because the new presentation marker and hidden-panel attributes are absent.

- [x] **Step 3: Remove visible splitter gutters**

```css
.agent-workspace {
  grid-template-columns:
    var(--sessions-width)
    minmax(600px, 1fr)
    var(--evidence-width);
}

.pane-resizer {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 12px;
  transform: translateX(-6px);
}

.pane-resizer::after {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 5px;
  width: 1px;
  content: '';
  background: var(--rule);
}
```

Place one handle at `left: var(--sessions-width)` and one at `right: var(--evidence-width)`.

- [x] **Step 4: Compact the session rail**

```vue
<aside class="session-rail">
  <button class="session-rail__new" type="button" @click="$emit('create')">
    ＋ 新对话
  </button>
  <div class="session-list">
    <article v-for="session in sessions" :key="session.id">
      <button class="session-select">{{ session.title }}</button>
      <small>{{ session.role }} · {{ timeLabel(session.updatedAt) }}</small>
    </article>
  </div>
  <footer>
    <p>会话保存在当前浏览器<br />7 天后自动过期</p>
    <button type="button" @click="$emit('clear')">清除本地记录</button>
  </footer>
</aside>
```

- [x] **Step 5: Replace the empty card and user card with linear message flow**

```vue
<div class="thread">
  <section v-if="!session.messages.length" class="thread-empty">
    <p>YOU · FROM DOSSIER</p>
    <h2>从一个可核验的问题开始。</h2>
    <button v-for="item in project.suggestedQuestions" :key="item">
      <span>↳</span>{{ item }}
    </button>
  </section>

  <article
    v-for="message in session.messages"
    :key="message.id"
    class="message"
    :class="`message--${message.role.toLowerCase()}`"
  >
    <p>{{ message.role === 'AGENT' ? 'AGENT · VERIFIED ANSWER' : 'YOU' }}</p>
    <div>{{ message.content }}</div>
  </article>
</div>
```

Neither `.thread-empty` nor `.message--user` receives a filled rectangular background.

- [x] **Step 6: Add evidence tabs and compact evidence cards**

```ts
export type EvidenceDeskTab =
  | 'EVIDENCE'
  | 'PROJECT'
  | 'TIMELINE'
  | 'CONTEXT'

const activeTab = ref<EvidenceDeskTab>('EVIDENCE')
```

```vue
<nav class="evidence-tabs">
  <button v-for="tab in tabs" :key="tab.id" :class="{ active: activeTab === tab.id }">
    {{ tab.label }}
  </button>
</nav>
<article
  v-for="item in evidence"
  :key="item.id"
  :data-evidence-id="item.id"
  :class="{ 'evidence-card--active': item.id === activeEvidenceId }"
>
  <span>{{ item.code }} · {{ item.type }}</span>
  <h3>{{ item.title }}</h3>
  <p>{{ item.summary }}</p>
  <small>{{ item.periodStart }} — {{ item.periodEnd }}</small>
</article>
```

- [x] **Step 7: Add a focused media-query composable**

```ts
import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

export function useMediaQuery(query: string): Ref<boolean> {
  const matches = ref(false)
  let media: MediaQueryList | null = null

  const update = (event?: MediaQueryListEvent) => {
    matches.value = event ? event.matches : Boolean(media?.matches)
  }

  onMounted(() => {
    media = window.matchMedia(query)
    update()
    media.addEventListener('change', update)
  })

  onBeforeUnmount(() => {
    media?.removeEventListener('change', update)
  })

  return matches
}
```

Test:

```ts
it('tracks the current matchMedia result', () => {
  vi.stubGlobal('matchMedia', vi.fn(() => ({
    matches: true,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  })))
  const wrapper = mount(defineComponent({
    setup() {
      return { matches: useMediaQuery('(max-width: 980px)') }
    },
    template: '<span>{{ matches }}</span>',
  }))
  expect(wrapper.text()).toBe('true')
})
```

- [x] **Step 8: Apply `inert` and `aria-hidden` by breakpoint**

```ts
const sessionsIsDrawer = useMediaQuery('(max-width: 980px)')
const evidenceIsDrawer = useMediaQuery('(max-width: 1219.98px)')
```

The template must bind:

```vue
:inert="sessionsIsDrawer && !sessionDrawerOpen ? '' : undefined"
:aria-hidden="sessionsIsDrawer ? String(!sessionDrawerOpen) : undefined"
```

Apply the equivalent bindings to the evidence desk.

- [x] **Step 9: Run Agent and media-query tests**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/features/agent src/shared/composables/useMediaQuery.test.ts
```

Expected: PASS.

---

### Task 7: Bring project, timeline, and evidence routes into the paper dossier system

**Files:**
- Modify: `frontend/src/shared/components/PageLead.vue`
- Modify: `frontend/src/pages/ProjectsPage.vue`
- Modify: `frontend/src/pages/ProjectPage.vue`
- Modify: `frontend/src/pages/TimelinePage.vue`
- Modify: `frontend/src/pages/EvidencePage.vue`
- Modify: `frontend/src/pages/ProjectsPage.test.ts`
- Modify: `frontend/src/pages/ProjectPage.test.ts`
- Modify: `frontend/src/pages/TimelinePage.test.ts`
- Modify: `frontend/src/pages/EvidencePage.test.ts`

**Interfaces:**
- Consumes: existing page data and query parameters.
- Produces: shared `PageLead` variants `paper` and `ink-section`, with paper as the default.

- [x] **Step 1: Write failing route presentation tests**

```ts
expect(projects.get('[data-page-lead]').attributes('data-theme')).toBe('paper')
expect(timeline.get('[data-page-lead]').attributes('data-theme')).toBe('paper')
expect(evidence.get('[data-selected-evidence]').classes()).not.toContain(
  'evidence-catalog__item--red',
)
```

Project mobile semantics:

```ts
expect(project.get('h1').attributes('data-mobile-balanced')).toBeDefined()
```

- [x] **Step 2: Verify RED**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/pages/ProjectsPage.test.ts src/pages/ProjectPage.test.ts src/pages/TimelinePage.test.ts src/pages/EvidencePage.test.ts
```

Expected: FAIL because the new page-lead and selection markers are absent.

- [x] **Step 3: Make `PageLead` paper-first and more compact**

```vue
<header data-page-lead :data-theme="theme" class="page-lead">
  <div class="page-shell page-lead__inner">
    <p>{{ code }}</p>
    <h1>{{ title }}</h1>
    <div>{{ description }}</div>
  </div>
</header>
```

```css
.page-lead {
  padding: clamp(74px, 8vw, 112px) 0 58px;
  color: var(--ink);
  background: var(--paper-hi);
  border-bottom: 1px solid var(--rule);
}

.page-lead h1 {
  font-size: clamp(44px, 6vw, 78px);
}
```

- [x] **Step 4: Tighten project index hierarchy**

Use the existing project information, but keep technology tags secondary:

```css
.project-index__item {
  grid-template-columns: 140px minmax(0, 1fr) 320px;
}

.project-index__item:hover {
  background: var(--paper-hi);
  transform: translateY(-4px);
}
```

- [x] **Step 5: Preserve the project dossier with mobile title control**

```vue
<h1 data-mobile-balanced>{{ project.title }}</h1>
```

```css
@media (max-width: 620px) {
  .project-cover h1 {
    font-size: clamp(42px, 13vw, 58px);
    line-height: 1.04;
    overflow-wrap: normal;
    word-break: keep-all;
  }
}
```

- [x] **Step 6: Make the timeline page header paper-first**

Remove the `dark` prop from the timeline `PageLead`. Keep local dark emphasis only if a timeline event is explicitly marked as a major milestone; current data has no such field, so all current events remain paper-based.

- [x] **Step 7: Replace red evidence selection with ink and rule emphasis**

```css
.evidence-catalog__list button[aria-pressed='true'] {
  padding-inline: 18px;
  color: var(--paper);
  background: var(--ink);
  box-shadow: inset 3px 0 var(--red);
}

.evidence-preview {
  border: 1px solid var(--rule);
  background: var(--paper-hi);
  box-shadow: none;
}
```

Ensure the mobile public-status mark remains horizontal:

```css
.evidence-preview header {
  align-items: flex-start;
}

.evidence-preview :deep(.status-mark) {
  flex: 0 0 auto;
  white-space: nowrap;
}
```

- [x] **Step 8: Run route tests**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/pages/ProjectsPage.test.ts src/pages/ProjectPage.test.ts src/pages/TimelinePage.test.ts src/pages/EvidencePage.test.ts
```

Expected: PASS.

---

### Task 8: Add same-viewport visual regression and complete verification

**Files:**
- Modify: `frontend/e2e/portfolio.spec.ts`
- Modify: `frontend/scripts/visual-audit.mjs`
- Modify: `frontend/playwright.config.ts` only if screenshot output configuration is needed.

**Interfaces:**
- Consumes: completed application and target prototype path.
- Produces: deterministic screenshots under `frontend/output/playwright/fidelity/` and a zero-issue console/overflow report.

- [x] **Step 1: Add failing visual-semantic E2E assertions**

```ts
test('home uses paper hero and paper exploration', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator('[data-home-layer="hero"]')).toHaveCSS(
    'background-color',
    'rgb(244, 238, 228)',
  )
  await expect(page.locator('[data-home-layer="explore"]')).toHaveCSS(
    'background-color',
    'rgb(244, 238, 228)',
  )
  await expect(page.locator('[data-role="INTERVIEWER"]')).toHaveCSS(
    'background-color',
    'rgb(32, 28, 23)',
  )
})
```

- [x] **Step 2: Add homepage-to-Agent context coverage**

```ts
await page.locator('[data-question]').first().click()
await page.getByRole('link', { name: /带着上下文进入 Agent/ }).click()
await expect(page.locator('.message--user')).toContainText('SQL 审计工具')
await expect(page.locator('.message--agent')).toBeVisible()
await expect(page.locator('[data-evidence-id].evidence-card--active')).toBeVisible()
```

- [x] **Step 3: Add drawer accessibility coverage**

```ts
await page.setViewportSize({ width: 980, height: 800 })
await page.goto('/agent')
await page.getByRole('button', { name: '会话', exact: true }).click()
await expect(page.locator('#local-session-rail')).toHaveAttribute('aria-hidden', 'false')
await expect(page.locator('#agent-evidence-desk')).toHaveAttribute('aria-hidden', 'true')
await page.keyboard.press('Escape')
await expect(page.getByRole('button', { name: '会话', exact: true })).toBeFocused()
```

- [x] **Step 4: Run E2E and verify RED before final implementation fixes**

Run:

```powershell
npm.cmd --prefix frontend run test:e2e
```

Expected: the new fidelity assertions fail until Tasks 1–7 are complete; after completion all tests pass.

- [x] **Step 5: Update visual audit routes and states**

Capture:

```ts
const viewports = [
  { name: '2048x1080', width: 2048, height: 1080 },
  { name: '1440x900', width: 1440, height: 900 },
  { name: '1219x900', width: 1219, height: 900 },
  { name: '980x800', width: 980, height: 800 },
  { name: '390x844', width: 390, height: 844 },
]

const routes = [
  { name: 'home', path: '/' },
  { name: 'projects', path: '/projects' },
  { name: 'project', path: '/projects/sql-audit' },
  { name: 'timeline', path: '/timeline' },
  { name: 'evidence', path: '/evidence' },
  { name: 'agent', path: '/agent' },
]
```

Also capture the homepage dialogue before and after selecting a question, plus Agent drawers at `1219px` and `980px`.

- [x] **Step 6: Run full unit tests**

Run:

```powershell
npm.cmd --prefix frontend test -- --run
```

Expected: all test files pass.

- [x] **Step 7: Run production build**

Run:

```powershell
npm.cmd --prefix frontend run build
```

Expected: successful Vue TypeScript and Vite build.

- [x] **Step 8: Run Playwright**

Run:

```powershell
npm.cmd --prefix frontend run test:e2e
```

Expected: all desktop and mobile Chromium projects pass.

- [x] **Step 9: Run visual audit**

Start Vite:

```powershell
npm.cmd --prefix frontend run dev -- --host 127.0.0.1 --port 4173
```

In another terminal:

```powershell
node frontend/scripts/visual-audit.mjs
```

Expected: `NO ISSUES DETECTED`.

- [x] **Step 10: Inspect screenshots against the prototype**

At minimum compare:

```text
prototype home 1440×900 ↔ current home 1440×900
prototype home 390×844 ↔ current home 390×844
prototype dialogue 1440×900 ↔ current dialogue 1440×900
prototype Agent 1440×900 ↔ current Agent 1440×900
prototype Agent 1219×900 ↔ current Agent 1219×900
prototype Agent 390×844 ↔ current Agent 390×844
```

Acceptance requires matching color roles, section order, visual density, title scale, control placement, responsive reflow, and absence of cropped or overflowing content. Text and counts may differ only where the public dataset differs from prototype examples.

- [x] **Step 11: Check diffs without staging**

Run:

```powershell
git -C D:\code\agent diff --check
git -C D:\code\agent status --short
```

Expected: no whitespace errors; only intentional frontend and documentation changes. Do not stage or commit without separate user authorization.
