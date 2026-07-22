# Portfolio Agent C2b Read-only Tools and Referential Multi-turn Implementation Plan

> **执行状态（2026-07-22）：** 已按本计划完成并通过 `scripts/verify-release.ps1 -SkipInstall`。C1/C2 已实现；随后 C3 仅内置 Model Provider Registry 已实现，其余 C3 未准入。下方清单保留实施时的 TDD 迁移记录。

> **For agentic workers:** Execute inline in the current workspace with strict RED -> GREEN -> REFACTOR cycles. The user-owned dirty worktree must be preserved. Do not stage, commit, push, reset, restore, checkout, or delete existing files.

**Goal:** Complete C2 with a fixed, bounded, read-only ToolPlan and page-memory referential follow-ups without sending history, visitor questions, retrieval data, or tool internals to an external model.

**Architecture:** Every request reads one `RuntimeAnswerContent`. A client-held `ContextEnvelope` contains only stable public IDs, the previous content version, selected section, and one closed `FollowUpIntent`. `ToolPlanBuilder` deterministically maps that intent to a closed `ToolKind` list before execution; `PublicKnowledgeTools` executes only against the captured in-memory snapshot, and `ToolResultValidator` rejects cross-snapshot, unknown-ID, over-budget, or non-public results. The resulting claims/evidence are converted into the existing `ResolvedAnswerContext` and `AnswerPlan`, so C1 model privacy and deterministic fallback remain unchanged.

**Tech Stack:** Java 21, Spring Boot 3.5.3, JUnit 5, AssertJ, Mockito, Vue 3, TypeScript, Vitest, Vue Test Utils, Playwright, PowerShell.

## Global Constraints

- Preserve the existing four dimensions: `resolution`, `answerSource`, `generationMode`, and `verification`.
- Keep `RuntimeContentSnapshot -> AnswerTurnSnapshot -> AgentExecutionSnapshot`; create no additional Snapshot type.
- Do not add a Registry, generic Hook, Agent loop, Orchestrator, DurableTask, server conversation, database, external tool provider, or model-driven tool call.
- The six closed tool kinds are `GET_PROJECT`, `GET_CLAIMS`, `GET_EVIDENCE_FOR_CLAIMS`, `GET_TIMELINE`, `SEARCH_PUBLIC_CONTENT`, and `COMPARE_PROJECTS`.
- Tool parameters accept only stable IDs, enums, and bounded filters. They never accept paths, URLs, commands, expressions, or arbitrary query languages.
- All calls in one plan receive the identical `RuntimeAnswerContent` object and its `contentVersion`/`runtimeBundleHash`.
- `ContextEnvelope` never contains historical questions, answer text, complete conversation, user identity, client verification, Provider thread IDs, retrieval terms/vectors/scores, ToolResult, AnswerPlan, Prompt, or Draft.
- Follow-up intents are exactly `EXPAND_SECTION`, `SHOW_EVIDENCE`, `EXPLAIN_DECISION`, `COMPARE_PROJECTS`, `CURRENT_STATUS`, and `RELATED_QUESTION`.
- The browser stores envelopes only in Vue page memory. Refresh/close removes them; no URL/history/localStorage/sessionStorage/IndexedDB/server persistence is allowed.
- The external model continues to receive only the existing provider-safe `AnswerPlan`.
- Production and test Java use no `var`, records, or Lombok.

---

## Task 1: Lock the ContextEnvelope API contract

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/FollowUpIntent.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/dto/request/ContextEnvelopeRequest.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/dto/response/ContextEnvelopeResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/dto/request/AnswerRequest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/dto/response/AnswerResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/mapper/AnswerResponseMapper.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/controller/AnswerControllerTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/dto/request/AnswerRequestTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/dto/request/ContextEnvelopeRequestTest.java`

**Interfaces:**
- Request envelope fields: `previousContentVersion`, `projectSlugs`, nullable `questionPresetId`, `referencedClaimIds`, nullable `selectedSectionType`, and non-null `followUpIntent`.
- Response envelope has the same stable-reference fields; `followUpIntent` is nullable for an initial answer and reflects the resolved intent for a follow-up answer.
- Lists are defensively copied, project/claim counts are bounded, IDs use existing stable-ID patterns, and `toString()` exposes only counts/enums/version—not IDs or visitor text.

- [ ] Add controller and value-object tests that accept a valid envelope and reject unknown fields, missing intent, malformed IDs, duplicate IDs, more than 4 projects, more than 8 claims, and historical-body fields such as `history`, `messages`, `previousQuestion`, and `previousAnswer`.
- [ ] Run RED:
  `C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=AnswerControllerTest,AnswerRequestTest,ContextEnvelopeRequestTest test`
- [ ] Implement the immutable enum/DTOs and optional `contextEnvelope` properties.
- [ ] Run the same command and require GREEN.

## Task 2: Project timeline and capability data into the existing runtime content

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerTimelineEvent.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/RuntimeCapabilities.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/RuntimeAnswerContent.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/portfolio/LocalPortfolioKnowledgeAdapter.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/AgentExecutionSnapshot.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/AgentExecutionSnapshotFactory.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/adapter/portfolio/LocalPortfolioKnowledgeAdapterTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/AgentExecutionSnapshotFactoryTest.java`

**Interfaces:**
- `RuntimeAnswerContent` gains immutable `List<AnswerTimelineEvent>` and `RuntimeCapabilities` without creating another snapshot.
- `RuntimeCapabilities` exposes `presetAnswers`, `modelExpression`, `groundedQuestions`, `readOnlyTools`, and `multiTurnReferences`; the last two are enabled only when the C2b fixed pipeline is configured and its required public projection is present.
- `AgentExecutionSnapshot` binds `readOnlyToolsEnabled`, `multiTurnReferencesEnabled`, tool policy version, and `maxToolCalls=4` for the turn.

- [ ] Add failing projection tests proving only timeline events whose project/claim/evidence references resolve to the same approved public snapshot are exposed.
- [ ] Add failing capability/snapshot tests proving tool and multi-turn flags are fixed per turn and do not copy content.
- [ ] Run RED:
  `C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=LocalPortfolioKnowledgeAdapterTest,AgentExecutionSnapshotFactoryTest test`
- [ ] Implement the minimal immutable projections and bindings.
- [ ] Run the same command and require GREEN.

## Task 3: Define the closed ToolPlan and deterministic intent mapping

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/ToolKind.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/ToolCall.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/ToolPlan.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/QueryIntent.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/service/ToolPlanBuilder.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/domain/ToolModelContractTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/ToolPlanBuilderTest.java`

**Interfaces:**
- `ToolCall` contains only `ToolKind`, stable project IDs/slugs, stable claim IDs, optional `AnswerSectionType`, and bounded topic/category enums.
- `ToolPlan` contains `toolPolicyVersion`, `contentVersion`, `runtimeBundleHash`, `QueryIntent`, and at most four immutable calls.
- Mapping is fixed: `SHOW_EVIDENCE -> GET_EVIDENCE_FOR_CLAIMS`; `EXPAND_SECTION -> GET_PROJECT + GET_CLAIMS + GET_EVIDENCE_FOR_CLAIMS`; `EXPLAIN_DECISION -> GET_CLAIMS + GET_EVIDENCE_FOR_CLAIMS`; `CURRENT_STATUS -> GET_PROJECT + GET_CLAIMS + GET_TIMELINE`; `RELATED_QUESTION -> SEARCH_PUBLIC_CONTENT`; `COMPARE_PROJECTS -> COMPARE_PROJECTS`.

- [ ] Add reflection and behavior tests proving no arbitrary query/path/URL/command/tool-name fields, no custom `toString()` that leaks IDs, no model-selected calls, exact ordering, exact maximum of four calls, and no calls for a request without an envelope.
- [ ] Run RED:
  `C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=ToolModelContractTest,ToolPlanBuilderTest test`
- [ ] Implement the closed models and deterministic builder.
- [ ] Run the same command and require GREEN.

## Task 4: Implement six bounded read-only public knowledge tools

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/gateway/PublicKnowledgeTools.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/PublicToolResult.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/ToolExecutionOutcome.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/adapter/tool/LocalPublicKnowledgeTools.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/service/ToolResultValidator.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/service/ToolPlanExecutor.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/adapter/tool/LocalPublicKnowledgeToolsTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/ToolResultValidatorTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/ToolPlanExecutorTest.java`

**Interfaces:**
- `PublicKnowledgeTools.execute(RuntimeAnswerContent, ToolCall)` performs no I/O and returns only Answer-owned project, claim, evidence, timeline, and supported preset projections.
- Search consumes only the plan's stable project/claim/topic/category filters; it does not consume the visitor question or an arbitrary expression.
- Compare requires 2-4 distinct valid projects. With fewer than two currently published projects it returns a standardized insufficient outcome, never fabricates a comparison.
- Executor stops at four calls, preserves order, shares the exact content object, and enforces result-count, elapsed-time, claim, evidence, and context-character budgets.

- [ ] Add failing tests for all six tools, invalid/unknown IDs, duplicate IDs, unapproved evidence exclusion, cross-project claims, timeline filtering, one-project comparison insufficiency, over-budget results, and a fake tool attempting to return a result from another content version/hash.
- [ ] Run RED:
  `C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=LocalPublicKnowledgeToolsTest,ToolResultValidatorTest,ToolPlanExecutorTest test`
- [ ] Implement the port, local adapter, validator, and executor with no filesystem/network/write access.
- [ ] Run the same command and require GREEN.

## Task 5: Revalidate reference context against the current snapshot

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/ValidatedContextEnvelope.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/ContextResolutionType.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/ContextResolution.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/service/ContextEnvelopeValidator.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/ContextEnvelopeValidatorTest.java`

**Interfaces:**
- `ContextResolutionType` is `VALID`, `VERSION_UPDATED`, or `INVALID`.
- Same-version references must resolve exactly. A changed version may resolve the same stable IDs and marks `VERSION_UPDATED`; missing, moved, semantically incompatible, expired/revoked, or conflicting references return `INVALID` and never fall back to a similarly named object.
- Validation never accepts client verification status and never loads content a second time.

- [ ] Add failing tests for same-version validity, benign version update, removed project, removed claim, claim now belonging to another project, selected section no longer represented, unknown preset, duplicate reference, and client-supplied history/verification rejection at the request boundary.
- [ ] Run RED:
  `C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=ContextEnvelopeValidatorTest test`
- [ ] Implement exact stable-ID revalidation against the captured `RuntimeAnswerContent`.
- [ ] Run the same command and require GREEN.

## Task 6: Integrate ToolPlan before AnswerPlan and preserve C1 privacy

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/ResolvedAnswerContext.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerTurnSnapshot.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerResult.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/AnswerContextFactory.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/AnswerPlanBuilder.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/PortfolioAgentRuntime.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/mapper/AnswerResponseMapper.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/PortfolioAgentRuntimeToolTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/PortfolioAgentRuntimeModelPrivacyTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/AnswerPlanBuilderTest.java`

**Interfaces:**
- Runtime order is `RESOLVE -> RETRIEVE when needed -> PLAN_TOOLS -> EXECUTE_TOOLS -> BUILD_ANSWER_PLAN -> EXPRESS -> VALIDATE -> VERIFY -> FINALIZE -> OBSERVE`.
- Preset and initial C2a retrieval behavior remain unchanged. A valid reference follow-up uses the fixed ToolPlan; invalid context returns `BOUNDARY`, one-project comparison returns an honest capability boundary, and privacy/policy violations return `REJECTED`.
- Follow-up facts become a normal provider-safe `AnswerPlan`; raw question, envelope, tool calls/results, history, retrieval data, and version-update internals are absent from provider payloads.
- Final response emits a new envelope derived only from the final `AnswerTurnSnapshot`, selected section, and actually referenced claim IDs.

- [ ] Add failing runtime tests for every follow-up intent, tool order, same snapshot identity, version-update notice, invalid-context boundary, compare insufficiency, optional-tool partial result, critical-tool failure, max four calls, and unchanged Preset/Retrieval behavior.
- [ ] Extend the provider privacy test to assert that serialized outbound payload excludes every ContextEnvelope and ToolPlan/ToolResult field and all current/previous visitor text.
- [ ] Run RED:
  `C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=PortfolioAgentRuntimeToolTest,PortfolioAgentRuntimeModelPrivacyTest,AnswerPlanBuilderTest test`
- [ ] Implement minimal runtime integration and deterministic boundary messages.
- [ ] Run the same command and require GREEN, then run all `answer` tests.

## Task 7: Keep referential multi-turn entirely in page memory

**Files:**
- Modify: `frontend/src/features/agent/model/answerTypes.ts`
- Modify: `frontend/src/features/agent/model/mapAnswerResponse.ts`
- Modify: `frontend/src/features/agent/model/sessionTypes.ts`
- Modify: `frontend/src/features/agent/composables/useLocalSessions.ts`
- Modify: `frontend/src/features/agent/api/answerApi.ts`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.vue`
- Modify: `frontend/src/features/agent/components/ConversationThread.vue`
- Test: `frontend/src/features/agent/api/answerApi.test.ts`
- Test: `frontend/src/features/agent/model/mapAnswerResponse.test.ts`
- Test: `frontend/src/features/agent/composables/useLocalSessions.test.ts`
- Test: `frontend/src/features/agent/components/AgentWorkspace.test.ts`

**Interfaces:**
- `MappedAnswer.contextEnvelope` is immutable page-memory state. `AgentSession` derives the next follow-up request from its most recent Agent answer; no separate persistence layer is added.
- Each answered message exposes keyboard-accessible follow-up actions only when an envelope exists: expand selected section, show evidence, explain decision, current status, related question, and compare projects when at least two public project slugs are available.
- The API sends only the envelope and the current visible follow-up question; it never serializes prior messages or answer bodies.
- Reload starts with no sessions/envelopes and keeps the existing privacy notice.

- [ ] Add failing tests proving the exact request JSON, no history/body fields, per-session envelope isolation, refresh-empty behavior, disabled compare action for one project, retry reuses only the same sanitized envelope, and keyboard focus/accessible labels.
- [ ] Run RED:
  `npm.cmd --prefix frontend test -- --run src/features/agent/api/answerApi.test.ts src/features/agent/model/mapAnswerResponse.test.ts src/features/agent/composables/useLocalSessions.test.ts src/features/agent/components/AgentWorkspace.test.ts`
- [ ] Implement types, mapping, page-memory storage, and follow-up actions without a new global store.
- [ ] Run the same command and require GREEN.

## Task 8: Add C2b injection, architecture, privacy, benchmark, and browser gates

**Files:**
- Modify: `scripts/architecture-check.ps1`
- Modify: `scripts/architecture-check.test.ps1`
- Modify: `scripts/privacy-check.ps1`
- Modify: `scripts/privacy-check.test.ps1`
- Create: `backend/src/test/java/com/portfolio/agent/release/ToolAndMultiturnBenchmarkTest.java`
- Create: `backend/src/test/resources/tool-multiturn-benchmark/cases.json`
- Modify: `frontend/e2e/support/publicApiMocks.ts`
- Modify: `frontend/e2e/portfolio.spec.ts`
- Modify: `scripts/run-jar-e2e.ps1`
- Modify: `scripts/run-jar-e2e.test.ps1`

**Interfaces:**
- Architecture blocks dynamic tool registries, model tool-calling SDK types, tool filesystem/network/write adapters, server conversation stores, and Answer core imports of Portfolio.
- Privacy scan blocks ContextEnvelope, ToolPlan, ToolResult, current/previous questions, message arrays, prompts, and IDs from logs/telemetry/provider fixtures and scans final frontend/JAR artifacts.
- Benchmark covers all six intents, invalid/stale references, injection-like public text, one-project comparison, optional/critical tool failure, model fallback, and zero false answered results for invalid context.

- [ ] Add failing checker self-tests and benchmark/browser cases before changing the checkers/runtime fixtures.
- [ ] Run RED checker/benchmark commands and confirm each fails for the intended missing rule or behavior.
- [ ] Implement the minimal checks and E2E fixtures.
- [ ] Run architecture/privacy self-tests and scans, benchmark, frontend Playwright, and packaged-JAR Playwright; require GREEN.

## Task 9: Full verification and documentation closeout

**Files:**
- Modify: `README.md`
- Modify: `SECURITY.md`
- Modify: `docs/00-文档状态索引.md`
- Modify: `docs/superpowers/specs/2026-07-21-portfolio-agent-future-intelligence-design.md`
- Modify: `scripts/verify-release.ps1`
- Modify: this plan with the final evidence record

- [x] Update authoritative status to C1+C2 complete only after fresh verification. Keep C3 unimplemented and do not add readiness claims for Registry/Hook/Orchestrator/multi-Agent/DurableTask.
- [x] Run backend test, code-quality, architecture, privacy, package, governance, benchmark, unpacked-JAR, and packaged-JAR verification through `scripts/verify-release.ps1 -SkipInstall`.
- [x] Run frontend check, lint, 75+ Vitest tests, build, mock Playwright, and real packaged-JAR Playwright.
- [x] Browser-verify no question/history in URL, no visitor state in browser storage/IndexedDB, reload clears references, every follow-up intent displays correct four-dimensional semantics, stale context is explicit, evidence IDs remain exact, focus and reduced-motion remain correct.
- [x] Run `git diff --check`, confirm zero staged files, capture `git status --short --branch`, and report all modified/untracked files without staging or committing.

## Risk and rollback boundaries

- A C2b runtime configuration switch disables `readOnlyTools` and `multiTurnReferences` together while preserving Preset, C1 expression, and C2a retrieval.
- A tool failure is request-local. It never retries externally, writes state, changes the active bundle, or bypasses verification.
- An invalid or changed reference returns an explicit safe boundary; it never guesses a replacement object.
- Removing frontend follow-up actions removes only page-memory references; ordinary Preset/free-text questions remain available.
- C3 remains a separate approval boundary. This plan creates no Registry, Hook, Orchestrator, DurableTask, or multi-Agent scaffolding.
