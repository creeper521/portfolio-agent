# 作品集前端全量重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `D:\code\agent\frontend` 全量重建为目标原型定义的工程卷宗式多页面作品集，同时在后端重构期间通过本地公开数据预览完成独立前端验证。

**Architecture:** 使用 Vue Router 管理六个正式页面，以 `publicContentRepository` 隔离页面与数据来源。第一轮由 `previewPublicContentRepository` 返回与公开 API 契约一致的真实脱敏内容；后续联调只需替换 repository，不改页面结构。全局设计令牌、页面骨架和领域组件全部按目标原型重建，旧前端仅保留无视觉属性的技术能力。

**Tech Stack:** Vue 3.5、TypeScript 5.8、Vue Router 4.5、Vite 7、Vitest 3、Vue Test Utils、Playwright

## Global Constraints

- 唯一视觉参考为 `C:\Users\WIN10\Documents\杂项\实习学习-Obsidian\.superpowers\brainstorm\homepage-flow-20260715\content\portfolio-home-prototype-v1.html`。
- 禁止墨绿色、青绿色、绿色状态色、装饰性渐变、玻璃拟态、大圆角卡片和通用 SaaS 面板语言。
- 正式色盘只包含纸张色、墨色、灰褐色、规则线和暗红。
- 首页只包含 Hero、可信度摘要、身份选择与轻对话、探索入口四层。
- 路由固定为 `/`、`/projects`、`/projects/:slug`、`/timeline`、`/evidence`、`/agent`。
- 缺失事实隐藏或显示“待公开 / 资料准备中”，不得虚构项目、数量、经历、结果或联系方式。
- 只展示 `APPROVED` 公开证据。
- 本地会话只保存在浏览器，7 天过期，支持手动清除。
- 第一轮不启动后端、不做前后端联调；Vitest 和 Playwright 必须在纯前端预览模式运行。
- 每项功能先写失败测试，再写实现。
- 完成前同步更新 `docs/04-项目代码约束.md`。

---

## File Map

### Application shell and styles

- Modify `frontend/src/app/App.vue`: 全局 App Shell、导航和移动目录。
- Modify `frontend/src/app/router.ts`: 六个正式路由及 404。
- Create `frontend/src/app/styles/tokens.css`: 目标原型唯一色盘、字体、间距和层级。
- Create `frontend/src/app/styles/base.css`: reset、排版、焦点、通用页面骨架。
- Create `frontend/src/app/styles/motion.css`: 进入动效与 reduced-motion。
- Modify `frontend/src/app/styles/main.css`: 只负责聚合样式文件。

### Public content boundary

- Create `frontend/src/features/public-content/model/publicContentTypes.ts`: 页面使用的完整公开视图模型。
- Create `frontend/src/features/public-content/data/previewPublicContent.ts`: 从现有公开 JSON 整理出的前端预览内容，不含假事实。
- Create `frontend/src/features/public-content/repository/publicContentRepository.ts`: repository 接口与当前实现选择。
- Create `frontend/src/features/public-content/repository/previewPublicContentRepository.ts`: 无后端预览实现。
- Create `frontend/src/features/public-content/composables/usePublicContent.ts`: 页面加载、错误和查询状态。

### Shared dossier components

- Create `frontend/src/shared/components/DossierHeader.vue`: 全局导航。
- Create `frontend/src/shared/components/PageLead.vue`: 页面卷宗标题。
- Create `frontend/src/shared/components/EmptyDossier.vue`: 诚实空状态。
- Create `frontend/src/shared/components/StatusMark.vue`: 无绿色状态表达。

### Home

- Replace `frontend/src/pages/HomePage.vue`.
- Create `frontend/src/features/home/components/PortfolioHero.vue`.
- Create `frontend/src/features/home/components/CredibilitySummary.vue`.
- Create `frontend/src/features/audience/model/audienceTypes.ts`.
- Create `frontend/src/features/audience/data/audienceProfiles.ts`.
- Create `frontend/src/features/audience/components/AudienceDialogue.vue`.
- Create `frontend/src/features/home/components/ExploreIndex.vue`.

### Projects, timeline, evidence

- Create `frontend/src/pages/ProjectsPage.vue`.
- Replace `frontend/src/pages/ProjectPage.vue`.
- Create `frontend/src/pages/TimelinePage.vue`.
- Create `frontend/src/pages/EvidencePage.vue`.
- Create `frontend/src/features/projects/components/ProjectIndex.vue`.
- Create `frontend/src/features/projects/components/ProjectDossier.vue`.
- Create `frontend/src/features/timeline/components/TimelineLedger.vue`.
- Create `frontend/src/features/evidence/components/EvidenceCatalog.vue`.
- Create `frontend/src/features/evidence/components/EvidencePreview.vue`.

### Agent

- Create `frontend/src/pages/AgentPage.vue`.
- Create `frontend/src/features/agent/model/sessionTypes.ts`.
- Create `frontend/src/features/agent/data/previewAnswers.ts`.
- Create `frontend/src/features/agent/composables/useLocalSessions.ts`.
- Create `frontend/src/features/agent/composables/useWorkspaceSplit.ts`.
- Create `frontend/src/features/agent/components/AgentWorkspace.vue`.
- Create `frontend/src/features/agent/components/LocalSessionRail.vue`.
- Create `frontend/src/features/agent/components/ConversationThread.vue`.
- Create `frontend/src/features/agent/components/EvidenceDesk.vue`.
- Create `frontend/src/features/agent/components/PaneResizer.vue`.

### Tests and tooling

- Modify `frontend/src/app/router.test.ts`.
- Replace `frontend/src/pages/HomePage.test.ts`.
- Replace `frontend/src/pages/ProjectPage.test.ts`.
- Create page/component/composable tests beside new files.
- Modify `frontend/playwright.config.ts`: Vite-only web server.
- Replace `frontend/e2e/portfolio.spec.ts`: pure frontend flows.
- Modify `docs/04-项目代码约束.md`.

---

### Task 1: Public preview content boundary

**Files:**
- Create: `frontend/src/features/public-content/model/publicContentTypes.ts`
- Create: `frontend/src/features/public-content/data/previewPublicContent.ts`
- Create: `frontend/src/features/public-content/repository/publicContentRepository.ts`
- Create: `frontend/src/features/public-content/repository/previewPublicContentRepository.ts`
- Test: `frontend/src/features/public-content/repository/previewPublicContentRepository.test.ts`

**Interfaces:**
- Produces: `PublicPortfolio`, `AudienceRole`, `PublicContentRepository`, `publicContentRepository`.
- `PublicContentRepository` exposes `getPortfolio()`, `getProjects()`, `getProject(slug)`, `getTimeline()`, and `getEvidence()`.

- [ ] **Step 1: Write repository contract tests**

```ts
it('returns only approved public evidence', async () => {
  const evidence = await previewPublicContentRepository.getEvidence()
  expect(evidence.length).toBeGreaterThan(0)
  expect(evidence.every((item) => item.publicStatus === 'APPROVED')).toBe(true)
})

it('does not invent a missing owner name', async () => {
  const portfolio = await previewPublicContentRepository.getPortfolio()
  expect(portfolio.owner.name).toBe('')
})
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `npm.cmd --prefix frontend test -- --run src/features/public-content/repository/previewPublicContentRepository.test.ts`

Expected: FAIL because the repository does not exist.

- [ ] **Step 3: Define public view models**

```ts
export type AudienceRole = 'INTERVIEWER' | 'MENTOR' | 'HR' | 'GUEST'
export type PublicStatus = 'APPROVED'

export interface PublicEvidence {
  id: string
  code: string
  title: string
  type: string
  periodStart: string
  periodEnd: string
  sourceCount: number
  summary: string
  supportedClaims: string[]
  publicStatus: PublicStatus
  projectSlugs: string[]
}

export interface PublicProject {
  slug: string
  code: string
  title: string
  summary: string
  background: string
  responsibilities: string[]
  solution: string
  keyDecisions: string[]
  technologies: string[]
  verification: string[]
  outcome: string
  handoff: string
  status: 'DELIVERED' | 'IN_PROGRESS' | 'PROTOTYPE' | 'LEARNING_ONLY'
  contributionType: 'INDEPENDENT' | 'PRIMARY' | 'COLLABORATIVE' | 'OBSERVED_LEARNING'
  evidenceIds: string[]
  suggestedQuestions: string[]
}

export interface TimelineEvent {
  id: string
  dateLabel: string
  title: string
  problem: string
  action: string
  impact: string
  projectSlugs: string[]
  evidenceIds: string[]
}

export interface PublicPortfolio {
  contentVersion: string
  publishedAt: string
  owner: { name: string; role: string; summary: string }
  projects: PublicProject[]
  evidence: PublicEvidence[]
  timeline: TimelineEvent[]
}
```

- [ ] **Step 4: Add the preview repository using only current approved public content**

```ts
export interface PublicContentRepository {
  getPortfolio(): Promise<PublicPortfolio>
  getProjects(): Promise<PublicProject[]>
  getProject(slug: string): Promise<PublicProject | null>
  getTimeline(): Promise<TimelineEvent[]>
  getEvidence(): Promise<PublicEvidence[]>
}

export const publicContentRepository: PublicContentRepository =
  previewPublicContentRepository
```

The preview dataset must copy factual fields from `backend/src/main/resources/public-data/public-portfolio.v1.json`. Timeline content may only summarize the approved evidence period and published project facts; it must not create new achievements.

- [ ] **Step 5: Run repository tests**

Run: `npm.cmd --prefix frontend test -- --run src/features/public-content/repository/previewPublicContentRepository.test.ts`

Expected: PASS.

---

### Task 2: App shell, design tokens, and routes

**Files:**
- Modify: `frontend/src/app/App.vue`
- Modify: `frontend/src/app/router.ts`
- Modify: `frontend/src/app/router.test.ts`
- Create: `frontend/src/shared/components/DossierHeader.vue`
- Create: `frontend/src/app/styles/tokens.css`
- Create: `frontend/src/app/styles/base.css`
- Create: `frontend/src/app/styles/motion.css`
- Modify: `frontend/src/app/styles/main.css`

**Interfaces:**
- Consumes: route names and global content shell.
- Produces: `DossierHeader` and named routes `home`, `projects`, `project`, `timeline`, `evidence`, `agent`, `not-found`.

- [ ] **Step 1: Expand router tests**

```ts
it.each([
  ['/', 'home'],
  ['/projects', 'projects'],
  ['/projects/sql-audit', 'project'],
  ['/timeline', 'timeline'],
  ['/evidence', 'evidence'],
  ['/agent', 'agent'],
])('maps %s to %s', async (path, name) => {
  const router = createAppRouter(createMemoryHistory())
  await router.push(path)
  await router.isReady()
  expect(router.currentRoute.value.name).toBe(name)
})
```

- [ ] **Step 2: Run router tests and verify RED**

Run: `npm.cmd --prefix frontend test -- --run src/app/router.test.ts`

Expected: FAIL for the four missing routes.

- [ ] **Step 3: Add route-level lazy imports and App Shell**

```ts
const ProjectsPage = () => import('../pages/ProjectsPage.vue')
const ProjectPage = () => import('../pages/ProjectPage.vue')
const TimelinePage = () => import('../pages/TimelinePage.vue')
const EvidencePage = () => import('../pages/EvidencePage.vue')
const AgentPage = () => import('../pages/AgentPage.vue')
```

`App.vue` renders `DossierHeader` plus `RouterView`, except the Agent route may request the compact header variant through route meta.

- [ ] **Step 4: Add the only approved color tokens**

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
}
```

Do not define any green, teal, cyan, purple, glow, or gradient token.

- [ ] **Step 5: Run router tests and build**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/app/router.test.ts
npm.cmd --prefix frontend run build
```

Expected: PASS and successful Vite build.

---

### Task 3: Home four-layer experience

**Files:**
- Replace: `frontend/src/pages/HomePage.vue`
- Replace: `frontend/src/pages/HomePage.test.ts`
- Create: `frontend/src/features/home/components/PortfolioHero.vue`
- Create: `frontend/src/features/home/components/CredibilitySummary.vue`
- Create: `frontend/src/features/home/components/ExploreIndex.vue`
- Create: `frontend/src/features/audience/model/audienceTypes.ts`
- Create: `frontend/src/features/audience/data/audienceProfiles.ts`
- Create: `frontend/src/features/audience/components/AudienceDialogue.vue`
- Test: `frontend/src/features/audience/components/AudienceDialogue.test.ts`

**Interfaces:**
- Consumes: `PublicPortfolio`, `AudienceRole`.
- Produces: selected role and optional question route query for `/agent`.

- [ ] **Step 1: Write the home structure test**

```ts
expect(wrapper.findAll('[data-home-layer]')).toHaveLength(4)
expect(wrapper.get('[data-home-layer="hero"]').exists()).toBe(true)
expect(wrapper.get('[data-home-layer="credibility"]').exists()).toBe(true)
expect(wrapper.get('[data-home-layer="dialogue"]').exists()).toBe(true)
expect(wrapper.get('[data-home-layer="explore"]').exists()).toBe(true)
```

- [ ] **Step 2: Write the audience interaction test**

```ts
await wrapper.get('[data-role="MENTOR"]').trigger('click')
expect(wrapper.get('[data-current-role]').attributes('data-current-role')).toBe('MENTOR')
expect(wrapper.text()).toContain('你如何复盘这个项目')
```

- [ ] **Step 3: Run tests and verify RED**

Run: `npm.cmd --prefix frontend test -- --run src/pages/HomePage.test.ts src/features/audience/components/AudienceDialogue.test.ts`

Expected: FAIL because the new layers and audience component do not exist.

- [ ] **Step 4: Implement the four sections**

Hero uses the real owner role and summary; blank owner name is not rendered. Credibility shows only confirmed counts:

```ts
const metrics = computed(() => [
  { value: portfolio.projects.length, label: '个公开项目', to: '/projects' },
  { value: portfolio.evidence.length, label: '项脱敏证据', to: '/evidence' },
].filter((metric) => metric.value > 0))
```

Audience questions are phrased from existing public fields. Summary answers are local deterministic compositions and always link to the matching project/evidence.

- [ ] **Step 5: Implement query handoff**

```ts
const agentTarget = computed(() => ({
  path: '/agent',
  query: { role: selectedRole.value, question: selectedQuestion.value },
}))
```

- [ ] **Step 6: Run focused tests**

Run: `npm.cmd --prefix frontend test -- --run src/pages/HomePage.test.ts src/features/audience/components/AudienceDialogue.test.ts`

Expected: PASS.

---

### Task 4: Project, timeline, and evidence routes

**Files:**
- Create: `frontend/src/pages/ProjectsPage.vue`
- Replace: `frontend/src/pages/ProjectPage.vue`
- Replace: `frontend/src/pages/ProjectPage.test.ts`
- Create: `frontend/src/pages/TimelinePage.vue`
- Create: `frontend/src/pages/EvidencePage.vue`
- Create: `frontend/src/features/projects/components/ProjectIndex.vue`
- Create: `frontend/src/features/projects/components/ProjectDossier.vue`
- Create: `frontend/src/features/timeline/components/TimelineLedger.vue`
- Create: `frontend/src/features/evidence/components/EvidenceCatalog.vue`
- Create: `frontend/src/features/evidence/components/EvidencePreview.vue`

**Interfaces:**
- Consumes: `publicContentRepository`.
- Produces: routable project, timeline, and evidence views with query-based focus.

- [ ] **Step 1: Write page tests**

```ts
expect(projectsWrapper.text()).toContain('SQL 审计与故障排查工具')
expect(projectWrapper.text()).toContain('为什么做')
expect(projectWrapper.text()).toContain('如何证明')
expect(timelineWrapper.text()).toContain('公开成长时间线')
expect(evidenceWrapper.text()).toContain('SQL 审计工具交付证据集')
```

- [ ] **Step 2: Verify RED**

Run: `npm.cmd --prefix frontend test -- --run src/pages/ProjectPage.test.ts src/pages/ProjectsPage.test.ts src/pages/TimelinePage.test.ts src/pages/EvidencePage.test.ts`

Expected: FAIL because three pages and the new dossier structure do not exist.

- [ ] **Step 3: Implement project index and dossier**

Project detail renders background, responsibilities, solution, decisions, verification, outcome, handoff, evidence references, timeline link, and an Agent link. Missing sections are omitted rather than rendered empty.

- [ ] **Step 4: Implement timeline and evidence**

`TimelinePage` renders available factual events or `EmptyDossier`. `EvidencePage` only receives approved evidence and honors `?evidence=<id>` with a selected preview.

- [ ] **Step 5: Run page tests**

Run: `npm.cmd --prefix frontend test -- --run src/pages/ProjectPage.test.ts src/pages/ProjectsPage.test.ts src/pages/TimelinePage.test.ts src/pages/EvidencePage.test.ts`

Expected: PASS.

---

### Task 5: Local Agent sessions

**Files:**
- Create: `frontend/src/features/agent/model/sessionTypes.ts`
- Create: `frontend/src/features/agent/data/previewAnswers.ts`
- Create: `frontend/src/features/agent/composables/useLocalSessions.ts`
- Test: `frontend/src/features/agent/composables/useLocalSessions.test.ts`

**Interfaces:**
- Produces:

```ts
interface LocalSessionStore {
  sessions: Ref<AgentSession[]>
  activeSession: ComputedRef<AgentSession | null>
  createSession(input?: SessionSeed): AgentSession
  appendMessage(sessionId: string, message: AgentMessage): void
  removeSession(sessionId: string): void
  clearSessions(): void
}
```

- [ ] **Step 1: Write expiry and persistence tests**

```ts
it('removes sessions older than seven days', () => {
  localStorage.setItem(SESSION_KEY, JSON.stringify([expiredSession, currentSession]))
  const store = useLocalSessions()
  expect(store.sessions.value.map((item) => item.id)).toEqual([currentSession.id])
})

it('clears all local sessions', () => {
  const store = useLocalSessions()
  store.createSession()
  store.clearSessions()
  expect(store.sessions.value).toEqual([])
})
```

- [ ] **Step 2: Verify RED**

Run: `npm.cmd --prefix frontend test -- --run src/features/agent/composables/useLocalSessions.test.ts`

Expected: FAIL because the composable does not exist.

- [ ] **Step 3: Implement versioned seven-day storage**

```ts
export const SESSION_KEY = 'portfolio.agent.sessions.v1'
export const SESSION_TTL_MS = 7 * 24 * 60 * 60 * 1000
```

Store complete messages only. On parse or quota failure, keep the active in-memory session and expose a non-blocking `storageWarning`.

- [ ] **Step 4: Add deterministic preview answers**

Preview answers are assembled only from `PublicProject` and `PublicEvidence`. Unsupported questions return a clear boundary message and never invent measurements.

- [ ] **Step 5: Run composable tests**

Run: `npm.cmd --prefix frontend test -- --run src/features/agent/composables/useLocalSessions.test.ts`

Expected: PASS.

---

### Task 6: Agent workspace and dynamic split

**Files:**
- Create: `frontend/src/pages/AgentPage.vue`
- Create: `frontend/src/features/agent/components/AgentWorkspace.vue`
- Create: `frontend/src/features/agent/components/LocalSessionRail.vue`
- Create: `frontend/src/features/agent/components/ConversationThread.vue`
- Create: `frontend/src/features/agent/components/EvidenceDesk.vue`
- Create: `frontend/src/features/agent/components/PaneResizer.vue`
- Create: `frontend/src/features/agent/composables/useWorkspaceSplit.ts`
- Test: `frontend/src/features/agent/composables/useWorkspaceSplit.test.ts`
- Test: `frontend/src/features/agent/components/AgentWorkspace.test.ts`

**Interfaces:**
- Consumes: `useLocalSessions`, preview answers, route query.
- Produces: `workspaceSplit`, `applyDelta`, `reset`, drawer controls, active evidence.

- [ ] **Step 1: Write split-boundary tests**

```ts
expect(clampWorkspaceSplit({ sessions: 100, evidence: 900 })).toEqual({
  sessions: 220,
  evidence: 760,
})
```

Keyboard expectations:

```ts
await handle.trigger('keydown', { key: 'ArrowRight' })
expect(handle.attributes('aria-valuenow')).toBe('256')
await handle.trigger('keydown', { key: 'Home' })
expect(handle.attributes('aria-valuenow')).toBe(defaultSessions.toString())
```

- [ ] **Step 2: Verify RED**

Run: `npm.cmd --prefix frontend test -- --run src/features/agent/composables/useWorkspaceSplit.test.ts src/features/agent/components/AgentWorkspace.test.ts`

Expected: FAIL because the workspace does not exist.

- [ ] **Step 3: Implement split state**

```ts
export const WORKSPACE_SPLIT_KEY = 'portfolio.workspace.split.v1'
export const WORKSPACE_LIMITS = {
  sessions: [220, 380] as const,
  evidence: [380, 760] as const,
  chatMin: 600,
}
```

Use Pointer Events and pointer capture. Persist only after pointerup, keyboard changes, or reset.

- [ ] **Step 4: Implement the workspace**

Desktop columns:

```css
grid-template-columns:
  var(--sessions-width)
  12px
  minmax(600px, 1fr)
  12px
  var(--evidence-width);
```

At `<1220px`, evidence becomes a right drawer. At `<=980px`, sessions also becomes a drawer. Drawers are mutually exclusive and support Escape/focus restoration.

- [ ] **Step 5: Implement route query seed**

`/agent?role=INTERVIEWER&question=...` creates or reuses an empty session, selects the role, fills the first question, and preserves the source context.

- [ ] **Step 6: Run Agent tests**

Run: `npm.cmd --prefix frontend test -- --run src/features/agent`

Expected: PASS.

---

### Task 7: Vite-only browser testing and visual QA

**Files:**
- Modify: `frontend/playwright.config.ts`
- Replace: `frontend/e2e/portfolio.spec.ts`
- Modify tests as required.

**Interfaces:**
- Consumes: completed frontend.
- Produces: backend-independent end-to-end verification.

- [ ] **Step 1: Switch Playwright web server**

```ts
use: {
  baseURL: 'http://127.0.0.1:4173',
  trace: 'retain-on-failure',
},
webServer: {
  command: 'npm.cmd run dev -- --host 127.0.0.1 --port 4173',
  url: 'http://127.0.0.1:4173',
  reuseExistingServer: false,
  timeout: 120_000,
},
```

- [ ] **Step 2: Write pure frontend flows**

Cover:

1. Home has four layers and no owner placeholder.
2. Role change updates recommended questions.
3. Home summary opens Agent with query context.
4. Project detail links to evidence.
5. Evidence query selects the correct item.
6. Session survives reload and clears manually.
7. Split handles drag, keyboard, reset, and persistence.
8. 1219px evidence drawer and 980px dual drawer.
9. No horizontal overflow at specified viewports.
10. No green/teal computed color or legacy selectors in rendered UI.

- [ ] **Step 3: Run all unit tests**

Run: `npm.cmd --prefix frontend test -- --run`

Expected: PASS.

- [ ] **Step 4: Run build**

Run: `npm.cmd --prefix frontend run build`

Expected: PASS.

- [ ] **Step 5: Run Playwright**

Run: `npm.cmd --prefix frontend run test:e2e`

Expected: PASS without starting Java or calling `/api`.

- [ ] **Step 6: Inspect target viewports**

Use browser screenshots at `2048×1080`, `1440×900`, `1219×900`, `980×800`, and `390×844`. Confirm typography, left-biased chat content, drawers, no horizontal overflow, and no console errors.

---

### Task 8: Remove old visual system and update code constraints

**Files:**
- Delete or replace: obsolete old-style Vue components and CSS after confirming no imports.
- Modify: `docs/04-项目代码约束.md`

**Interfaces:**
- Consumes: final implemented file structure.
- Produces: authoritative project constraints matching reality.

- [ ] **Step 1: Find old imports and forbidden colors**

Run:

```powershell
rg "ProjectCard|EvidenceCard|AgentPanel|#0f766e|#14b8a6|teal|green|gradient" frontend/src
```

Expected: no runtime imports or forbidden legacy colors. Test descriptions may mention forbidden values only when asserting absence.

- [ ] **Step 2: Remove obsolete files**

Delete only components and styles with zero remaining imports. Preserve API/type code still needed for future backend adaptation.

- [ ] **Step 3: Update code constraints**

Document exact routes, dependency direction, target-prototype authority, no-green color rule, public-data boundary, repository boundary, local-session expiry, accessibility, responsive behavior, testing commands, and the prohibition on reviving old visual components.

- [ ] **Step 4: Run final verification**

Run:

```powershell
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
npm.cmd --prefix frontend run test:e2e
git diff --check
```

Expected: all commands pass; no backend process is required.

- [ ] **Step 5: Review working tree**

Run: `git status --short`

Expected: only intentional frontend, design, plan, and code-constraint changes. Do not stage or commit without explicit user authorization.
