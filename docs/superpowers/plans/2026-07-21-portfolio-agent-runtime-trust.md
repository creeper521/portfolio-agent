# Portfolio Agent Runtime Trust Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved A-stage runtime trust design without starting B/C work, while preserving every pre-existing user change in the dirty worktree.

**Architecture:** Keep the modular monolith and deterministic answer engine. The Portfolio repository owns one immutable `RuntimeContentSnapshot`; the Answer adapter projects it once per request into Answer-owned read models, and `PortfolioAgentRuntime` creates one immutable `AnswerTurnSnapshot` before resolve/context/engine/verification/finalize/observe. The Vue app stores `EphemeralConversation -> ConversationTurn -> StructuredAnswerTurn` only in module memory and transfers completed home turns through a five-minute, one-use in-memory handoff store.

**Tech Stack:** Java 21, Spring Boot 3.5, Maven, Vue 3, TypeScript, Vite, Vitest, Vue Test Utils, Playwright, PowerShell release gates.

## Global Constraints

- Never reset, restore, checkout, delete, stage, commit, push, or create a PR for user-owned changes.
- Runtime reads only `backend/src/main/resources/public-data/`; no model, RAG, embedding, database, SSE, registry, hook, plugin, DurableTask, or multi-agent framework.
- Java production and test code use explicit types; no `var`, `record`, Lombok, field injection, or mutable DTOs.
- Answer contract is exactly the orthogonal dimensions `resolution`, `answerSource`, `generationMode`, and `verification`; `MATCHED`/`GROUNDED` are not resolutions.
- Visitor questions/answers never enter URL/history, localStorage, sessionStorage, IndexedDB, server storage, ordinary logs, or long-lived telemetry.
- The only Snapshot names are `RuntimeContentSnapshot`, `AnswerTurnSnapshot`, `GovernanceRunSnapshot`, and `AgentExecutionSnapshot`; this plan creates only the first two.
- Existing dirty documentation and `AnswerProjectNotFoundException.java` are user-owned; edit overlapping files minimally and preserve unrelated hunks.
- Each behavioral change follows RED -> GREEN -> REFACTOR and records the focused command used at each gate.
- No Git commit steps: the user explicitly prohibited Git writes.

---

### Task 1: Freeze the Active Runtime Content and Publish Executable Presets

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/domain/RuntimeContentSnapshot.java`
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/domain/QuestionStatus.java`
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/domain/ReviewStatus.java`
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/QuestionPresetResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/domain/QuestionDefinition.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/repository/PublicPortfolioRepository.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/repository/file/JsonPublicPortfolioRepository.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/service/PortfolioService.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/service/result/PublicContent.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/PublicContentResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/ProjectDetailResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/mapper/PortfolioResponseMapper.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidator.java`
- Modify: `backend/src/main/resources/public-data/public-portfolio.v1.json`
- Test: repository, validator, service, mapper, controller, and model contract tests under `backend/src/test/java/com/portfolio/agent/portfolio/`

**Interfaces:**
- Produces: `PublicPortfolioRepository#getSnapshot(): RuntimeContentSnapshot`.
- Produces: immutable snapshot metadata `schemaVersion`, `contentVersion`, `runtimeBundleHash`, `loadedAt`, and the validated `PortfolioSnapshot` content.
- Produces: public `questionPresets[]` with stable `id`, display `text`, `projectSlug`, `audiences`, `placements`, and executable status already filtered to `ACTIVE + APPROVED`.

- [ ] Write tests proving one repository instance returns the same immutable snapshot, the runtime hash is stable, inactive/unapproved/non-executable presets are excluded, and every published preset maps to a public project.
- [ ] Run `mvn ... -Dtest=JsonPublicPortfolioRepositoryTest,PortfolioSnapshotValidatorTest,PortfolioServiceTest,PortfolioControllerTest test`; expect new contract assertions to fail.
- [ ] Implement the smallest snapshot wrapper, status fields, validator rules, DTO mapping, and one current preset available to all four roles at HOME/AGENT placements.
- [ ] Re-run the focused suite and then `mvn ... test`; expect 0 failures.

### Task 2: Replace the Answer Contract and Introduce the Fixed Runtime Pipeline

**Files:**
- Create: Answer domain enums/classes for `AnswerResolution`, `AnswerSource`, `GenerationMode`, `VerificationStatus`, request context, resolved content, `AnswerTurnSnapshot`, structured result, verification result, and decision DTO.
- Create: `answer/service/PortfolioAgentRuntime.java`, `answer/service/QuestionResolver.java`, `answer/service/AnswerContextFactory.java`, `answer/service/VerificationPolicy.java`.
- Create: `answer/gateway/AnswerDecisionPublisher.java` and `answer/adapter/observability/NoopAnswerDecisionPublisher.java`.
- Modify: `AnswerRequest`, `AnswerController`, `AnswerService` (replace with or delegate to runtime), `PortfolioKnowledgeGateway`, `LocalPortfolioKnowledgeAdapter`, `AnswerEngine`, `DeterministicAnswerEngine`, response DTOs, and `AnswerResponseMapper`.
- Remove after references reach zero: legacy `AnswerMode` and legacy matched/fallback fields.
- Test: all Answer tests plus new runtime, resolver, context, verification, publisher-failure, snapshot consistency, request redaction, and controller contract tests.

**Interfaces:**
- Consumes request: `turnId`, optional `questionPresetId`, optional compatibility display `question`, and `context { projectSlug, audienceRole, focusEvidenceIds, source }`.
- Produces response: `requestId`, echoed `turnId`, `contentVersion`, `questionPresetId`, four trust dimensions, `title`, `summary`, typed titled `sections[*].evidenceIds`, top-level `evidenceIds`, and `suggestedQuestionPresetIds`.
- `BOUNDARY`/`REJECTED` have `answerSource=null`, `generationMode=DETERMINISTIC`, `verification=NOT_APPLICABLE`, and no evidence.
- `AnswerDecisionPublisher#publish(AnswerDecision)` receives only whitelisted anonymous fields and is called after the final response is fixed; every exception is swallowed without changing response or HTTP status.

- [ ] Replace controller/engine tests with failing JSON and domain assertions for ANSWERED, BOUNDARY, REJECTED, PRESET, DETERMINISTIC, VERIFIED, NOT_APPLICABLE, section-local evidence, preset-ID resolution, alias resolution, invalid context, echoed turn ID, and absent legacy fields.
- [ ] Add failing tests proving the repository is fetched exactly once per request and publisher failure cannot alter the response.
- [ ] Add a failing `AnswerRequest.toString()` test proving a unique visitor question is absent and `<redacted>` is present.
- [ ] Implement the minimal fixed pipeline and new immutable contract, retaining only deterministic PRESET answers.
- [ ] Re-run focused Answer tests, architecture check, and full backend tests.

### Task 3: Replace Browser Persistence with Ephemeral Turns and One-Time Handoff

**Files:**
- Create: `frontend/src/features/agent/composables/useEphemeralConversations.ts`.
- Create: `frontend/src/features/agent/composables/useAnswerHandoff.ts`.
- Replace types in `answerTypes.ts` and `sessionTypes.ts` with `EphemeralConversation`, `ConversationTurn`, `StructuredAnswerTurn`, `SystemNotice`, and handoff envelope types.
- Modify: `answerApi.ts`, `mapAnswerResponse.ts`, `AudienceDialogue.vue`, `LightAnswerPanel.vue`, `AgentPage.vue`, `AgentWorkspace.vue`, `ConversationThread.vue`, and `LocalSessionRail.vue`.
- Remove after references reach zero: `useLocalSessions.ts` and its 7-day persistence tests.
- Test: answer API/mapping, ephemeral conversation, handoff TTL/one-use, home dialogue, Agent page/workspace, and router tests.

**Interfaces:**
- `askQuestion` submits a preset ID or free text with a random memory-only turn ID and typed context.
- `createHandoff(completedTurn, context)` returns a random ID expiring after five minutes; `consumeHandoff(id)` deletes before returning and never persists.
- Home navigates only to `/agent?handoff=<random-id>`; question, answer, role, project, and evidence content never enter the route.
- Agent consumes the completed structured turn without issuing another Answer request.

- [ ] Write failing tests that spy on all Web Storage APIs, assert no conversation persistence, assert reload/new module state starts empty, and verify one-use/expiry behavior.
- [ ] Write failing component/router tests asserting the URL contains only `handoff`, the completed answer is not re-requested, and invalid/consumed handoff produces a separate privacy notice.
- [ ] Implement module-memory state and handoff with no persistence adapter.
- [ ] Re-run focused Vitest suites and assert the old storage key and 7-day copy are absent from production source.

### Task 4: Preserve Structured Presentation and Fix Route/Reference Semantics

**Files:**
- Modify: `publicContentTypes.ts`, public repository fixtures/mocks, `LightAnswerPanel.vue`, `ConversationThread.vue`, `EvidenceDesk.vue`, `EvidencePage.vue`, `TimelinePage.vue`, relevant page/component tests, and Playwright mocks/specs.

**Interfaces:**
- Frontend stores the full structured response; renderers choose summary vs. sections without flattening.
- Trust labels derive from all four dimensions; BOUNDARY/REJECTED never show verified copy.
- Recommended questions are filtered from backend `questionPresets` by current audience and placement and submitted by stable ID.
- `/evidence?project=` and `/timeline?project=` filter by real relationships; invalid explicit evidence/project values show an empty/invalid-reference state, never the first item.

- [ ] Write failing tests for all trust-label combinations, section retention, one supported preset across roles, project filters, and invalid-reference non-fallback.
- [ ] Implement minimal view-model helpers and component changes without adding a registry or generic renderer framework.
- [ ] Update Playwright API mocks to the new contract and run focused Vitest tests.

### Task 5: Close Accessibility and Reduced-Motion Gaps

**Files:**
- Modify: `AgentWorkspace.vue`, `ConversationThread.vue`, `EvidenceDesk.vue`, `LightAnswerPanel.vue`, `main.css`, `motion.css`, and component/E2E tests.

**Interfaces:**
- Completed answer announcements use one polite live-region update, not character-by-character output.
- Evidence selectors are native buttons.
- Opening a drawer transfers focus inside, traps Tab, makes background inert, supports Escape/scrim close, and restores the exact trigger focus.
- Reduced motion disables typing/translation while making all final content immediately visible.

- [ ] Write failing DOM tests for live region, native evidence buttons, focus transfer/restore, inert background, Tab wrap, visible focus, and reduced-motion completion.
- [ ] Implement the smallest focus manager and CSS corrections; do not add a UI library.
- [ ] Run focused Vitest and Playwright keyboard/reduced-motion cases at 1219, 980, and 390 widths.

### Task 6: Move and Expand Privacy Gates Before Risk Artifacts

**Files:**
- Modify: `scripts/privacy-check.ps1`, `scripts/privacy-check.test.ps1`, `scripts/verify-release.ps1`, `scripts/run-jar-e2e.ps1`, and associated tests.
- Modify: `frontend/package.json` and lockfile only as needed to expose `check` and `lint` commands using existing/local tooling.

**Interfaces:**
- Privacy checker detects quoted/compact JSON credentials, doubled/escaped Windows paths, encoded variants covered by the approved tests, and scans all allowed text resources under final `BOOT-INF/classes`.
- Source/public-data/frontend-dist privacy checks execute before standard JAR creation; post-package scan remains defense in depth.
- Packaged-JAR readiness validates the new public questionPreset and Answer response fields.

- [ ] Add failing checker fixtures and release-order assertions before changing scripts.
- [ ] Move pre-package scans ahead of Maven package and widen packaged scan to `BOOT-INF/classes`.
- [ ] Add/repair `npm run check` and `npm run lint`, keeping dependency changes minimal and reproducible.
- [ ] Run checker self-tests, architecture check, privacy scan, and script tests.

### Task 7: Full Acceptance, Documentation Status, and Handoff

**Files:**
- Modify only implementation-status sections in `docs/00-文档状态索引.md`, A design, `README.md`, `SECURITY.md`, and `docs/04-项目代码约束.md` where current behavior/commands changed.
- Do not mark B, C, docs/05, or docs/06 implemented.

**Verification:**
- Backend: Maven test, code-quality check, architecture checker self-test + scan, privacy checker self-test + scans, package, JAR content verification, packaged-JAR run.
- Frontend: `npm run check`, `npm run lint`, `npm test -- --run`, `npm run build`, mocked Playwright.
- Browser: supported home preset; one-time handoff; no question in URL/history/storage; refresh loss + privacy notice; ANSWERED/BOUNDARY/REJECTED; PRESET/generation/verification labels; filters/no fallback; keyboard/focus/reduced motion/drawers; real packaged JAR integration.

- [ ] Run every command fresh and save exact counts/exit codes in the final report.
- [ ] Re-read the A acceptance criteria line by line and record any residual risk instead of claiming completion.
- [ ] Update documentation status truthfully, inspect `git diff --check`, `git status --short --branch`, and verify no staging occurred.
- [ ] Stop after A and wait for explicit approval before B.

## Compatibility and Rollback Boundaries

- Compatibility: preserve REST endpoint paths, project/evidence public DTO facts, deterministic canonical/alias matching, static route list, workspace-width preference key, and single-JAR packaging. Deliberately break/remove legacy answer trust fields and persistent conversation behavior because retaining them violates the approved design.
- Data migration: schema 1.0 classpath data receives only A runtime metadata for executable presets; it is not migrated to B Claim/Link schema 2.0.
- Rollback boundary: each task is isolated to named files and focused tests. Because the worktree is dirty and Git restore is prohibited, rollback means manually reversing only this plan's hunks after comparing against the captured initial `git diff --name-only`; never delete or overwrite pre-existing files.
- Main risks: a snapshot projection accidentally re-reads the repository; browser singleton state leaking between tests; router tests confusing page reload with same-tab navigation; focus trapping across responsive breakpoint changes; privacy regex false positives; packaged JAR tests using stale frontend dist. Each has an explicit focused regression test above.
