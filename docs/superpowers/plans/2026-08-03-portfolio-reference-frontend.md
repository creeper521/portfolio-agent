# Portfolio Reference Frontend Implementation Plan
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Send stable preset and explicit reference context through v2, adopt the precise response semantics, and preserve the existing visual layout.

**Architecture:** Existing answer cards and follow-up controls stay visually intact. Explicit button actions carry `PortfolioReferenceContext`; free text never inherits it automatically. Technical labels are replaced with restrained business-language labels driven by the new response dimensions.

**Tech Stack:** Vue 3, TypeScript, Vite, Vitest, Vue Test Utils, Playwright.

## Global Constraints

- No page-layout redesign and no new color system.
- Conversations and reference context remain tab-memory only.
- Do not persist or log questions, answers, Claim content or Evidence content.
- TDD is mandatory.
- Do not commit without explicit authorization.

---

### Task 1: Request serialization

**Files:**
- Modify: `frontend/src/features/agent/model/answerTypes.ts`
- Modify: `frontend/src/features/agent/api/answerApi.ts`
- Test: `frontend/src/features/agent/api/answerApi.test.ts`

**Interfaces:**
- Consumes: backend Task 2 JSON contract.
- Produces: `questionPresetId` and `context.referenceContext` serialization.

- [ ] **Step 1: Replace the existing negative serialization test**

```ts
it('sends preset id and explicit reference context', async () => {
  await askQuestion({
    ...baseInput,
    questionPresetId: 'question-sql-audit-async-and-recovery',
    referenceContext: {
      contentVersion: '2026-07-29.1',
      subjectIds: ['sql-audit'],
      referencedClaimIds: ['claim-async'],
      followUpAction: 'SHOW_EVIDENCE',
    },
  })
  expect(body.questionPresetId).toBe('question-sql-audit-async-and-recovery')
  expect(body.context.referenceContext.followUpAction).toBe('SHOW_EVIDENCE')
})
```

Add a separate test proving free text without explicit action omits `referenceContext`.

- [ ] **Step 2: Run RED**

Run: `npm.cmd --prefix frontend test -- --run src/features/agent/api/answerApi.test.ts`

Expected: current serializer drops both fields.

- [ ] **Step 3: Implement serialization**

Clone arrays when building JSON and never serialize `undefined` reference context.

- [ ] **Step 4: Run GREEN**

Run the Task 1 test file.

### Task 2: Response model and labels

**Files:**
- Modify: `frontend/src/features/agent/model/answerTypes.ts`
- Modify: `frontend/src/features/agent/model/mapAnswerResponse.ts`
- Modify: `frontend/src/features/agent/model/answerLabels.ts`
- Modify: `frontend/src/shared/diagnostics/frontendDiagnosticTypes.ts`
- Test: `frontend/src/features/agent/model/mapAnswerResponse.test.ts`
- Test: `frontend/src/features/agent/model/answerLabels.test.ts`
- Test: `frontend/src/shared/diagnostics/frontendDiagnostics.test.ts`

- [ ] **Step 1: Write failing mapping and label tests**

Assert exact labels for:

```text
PORTFOLIO + VERIFIED + EVIDENCE_COMPOSITION
  -> 作品集资料 · 已验证证据 · 确定性组装

PORTFOLIO + NEEDS_CLARIFICATION
  -> 作品集问题 · 需要补充信息

PORTFOLIO + NOT_SUPPORTED
  -> 作品集资料 · 当前公开证据不足

GENERAL + GENERAL_MODEL
  -> 通用对话 · 模型回答
```

- [ ] **Step 2: Run RED**

Run the three Task 2 test files.

Expected: types only understand `BOUNDARY/generationMode/answerSource`.

- [ ] **Step 3: Replace old dimensions**

Map `resolution`, `answerScope`, `constructionMode`, `intentSource`, `evidenceState`, `noticeCode`, `degraded`, and `referenceContext`. Remove frontend diagnostic `generationMode` and replace it with allowed construction/intent fields without adding user text.

- [ ] **Step 4: Run GREEN**

Run Task 2 tests.

### Task 3: Explicit reference actions with unchanged layout

**Files:**
- Modify: `frontend/src/features/agent/components/ConversationThread.vue`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.vue`
- Modify: `frontend/src/features/agent/composables/useLocalSessions.ts`
- Test: `frontend/src/features/agent/components/ConversationThread.test.ts`
- Test: `frontend/src/features/agent/components/AgentWorkspace.test.ts`
- Test: `frontend/src/features/agent/composables/useLocalSessions.test.ts`

- [ ] **Step 1: Write failing interaction tests**

```ts
it('emits reference context only from an explicit evidence action', async () => {
  await wrapper.get('[data-follow-up="show-evidence"]').trigger('click')
  expect(wrapper.emitted('follow-up')?.[0]?.[0].referenceContext.followUpAction)
    .toBe('SHOW_EVIDENCE')
})

it('does not attach prior reference context to free text', async () => {
  await submitFreeText('顺便解释 Java 虚拟线程')
  expect(lastRequest().context.referenceContext).toBeUndefined()
})
```

- [ ] **Step 2: Run RED**

Run the three Task 3 test files.

Expected: current API path drops the envelope and action shape does not use the new type.

- [ ] **Step 3: Implement explicit action payloads**

Keep existing DOM hierarchy and CSS. Rename internal envelope data to reference context, map buttons to closed actions, snapshot reference context for retry, and do not attach it to composer free text.

- [ ] **Step 4: Run GREEN**

Run Task 3 tests.

### Task 4: Frontend integration verification

**Files:**
- Modify: `frontend/e2e/portfolio.spec.ts`
- Modify: `frontend/e2e/support/publicApiMocks.ts`

- [ ] **Step 1: Add an E2E request assertion**

Assert a preset click sends `questionPresetId`, an evidence follow-up sends `context.referenceContext`, and a subsequent free-text question omits it.

- [ ] **Step 2: Run targeted E2E RED/GREEN during implementation**

Run the documented Playwright project that covers the Agent page.

- [ ] **Step 3: Run full frontend verification**

```powershell
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
```

Expected: 0 failed tests and successful production build.
