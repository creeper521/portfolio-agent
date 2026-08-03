# Single-Pass Portfolio Hard Route Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route every portfolio hard request through at most one constrained portfolio-task classification that may return either an approved safety/time boundary or a task, without guessing `FACT_LOOKUP` from a subject hint.

**Architecture:** Extend the existing portfolio-task structured response with a mutually exclusive `boundaryIntent`, and introduce `PortfolioTaskRoutingDecision` as the resolver/runtime handoff. `PortfolioTaskResolver` owns precedence: model boundary first, deterministic task rule second, model task third, clarification on disabled/failed/low-confidence classification. Runtime performs deterministic conversation boundary and subject guard before asking the resolver for one decision.

**Tech Stack:** Java 21, Jackson, JUnit 5, AssertJ, Mockito, Maven, Spring `MockRestServiceServer`.

## Global Constraints

- Follow strict red-green-refactor TDD; observe the intended failure before production edits.
- Do not use `var`, Java records, or Lombok.
- Only `TIME_SENSITIVE` and `UNSUPPORTED_OR_UNSAFE` are legal model boundary intents.
- Deterministic task rules override ordinary model task modes, but never override a legal model boundary.
- Provider-disabled hint-only requests clarify and make zero model calls.
- Diagnostics must contain only closed enums/counts/status and never the original question.
- Finish with focused tests, full Maven tests, privacy scan, diff review, and one Chinese commit; do not merge or push.

---

### Task 1: Structured classification and routing decision

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioTaskRoutingDecision.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioTaskClassification.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/domain/PortfolioTaskClassificationTest.java`

**Interfaces:**
- Produces: `PortfolioTaskClassification.getBoundaryIntent()` and `PortfolioTaskRoutingDecision.boundary(ConversationIntent)`, `.task(PortfolioTask)`, `.getBoundaryIntent()`, `.getTask()`.

- [ ] **Step 1: Write failing domain tests** for legal unsafe/time boundary values, rejection of all other boundary intents, rejection of boundary+mode and boundary-with-refinement combinations, and exactly-one-value routing decisions.
- [ ] **Step 2: Run** `mvn.cmd -Dtest=PortfolioTaskClassificationTest test`; expect compile/test failure because the new API is absent.
- [ ] **Step 3: Implement** a Jackson constructor with fields `boundaryIntent`, `mode`, `conditions`, `refinement`, `confidence`; retain the existing four-argument constructor as a task-only convenience. Enforce exactly one of boundary intent or task mode and the existing refinement rules. Implement an immutable final routing decision class with mutually exclusive values.
- [ ] **Step 4: Re-run** the domain test and expect PASS.

### Task 2: Single-pass resolver precedence

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioTaskResolver.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/PortfolioTaskResolverTest.java`

**Interfaces:**
- Produces: `PortfolioTaskRoutingDecision route(String turnId, String question, PortfolioRecommendationContext context, boolean providerAllowed)`.
- Consumes: the domain types from Task 1 and the existing `PortfolioTaskClassifierPort`.

- [ ] **Step 1: Write failing resolver tests** proving: a legal model boundary preempts a deterministic recommendation; deterministic recommendation beats an ordinary model comparison; English Recommend/Compare with a hint-presence-independent call retain model modes; Replace without context clarifies; provider-disabled deterministic recommendation succeeds; provider-disabled ambiguous/hint-only input clarifies; every provider-enabled route invokes the classifier at most once.
- [ ] **Step 2: Run** `mvn.cmd -Dtest=PortfolioTaskResolverTest test`; expect failures because `route` is absent.
- [ ] **Step 3: Implement** `route`: optionally classify once; return a high-confidence legal boundary; otherwise resolve deterministic rules first; when no rule, accept a high-confidence model task; return `CLARIFICATION_REQUIRED` on disabled provider, failure, invalid/low-confidence output, or refinement without context. Keep `resolve` delegating to the task branch for compatibility.
- [ ] **Step 4: Re-run** resolver tests and expect PASS with exact invocation counts.

### Task 3: Prompt, Jackson, and adapter contract

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ConversationalPromptFactory.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/adapter/model/ConversationalPromptFactoryTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/adapter/model/OpenAiCompatibleConversationalModelAdapterTest.java`

**Interfaces:**
- The `portfolio_task` response contains exactly `boundaryIntent`, `mode`, `conditions`, `refinement`, `confidence`; boundary intent is `TIME_SENSITIVE`, `UNSUPPORTED_OR_UNSAFE`, or null.

- [ ] **Step 1: Write failing prompt/adapter tests** for the declared boundary field, successful unsafe JSON decoding, illegal general boundary rejection, and no question text in diagnostic event fields.
- [ ] **Step 2: Run** `mvn.cmd -Dtest=ConversationalPromptFactoryTest,OpenAiCompatibleConversationalModelAdapterTest test`; expect contract/assertion failures.
- [ ] **Step 3: Update** the prompt contract to require mutually exclusive boundary/task output and forbid undeclared values; rely on the domain constructor for Jackson validation. Keep diagnostics restricted to operation/failure/status metadata.
- [ ] **Step 4: Re-run** prompt/adapter tests and expect PASS.

### Task 4: Runtime consumes one routing decision

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/ConversationalAgentRuntime.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/service/ConversationalAgentRuntimeTest.java`

**Interfaces:**
- Consumes: `PortfolioTaskResolver.route(...)` and `PortfolioTaskRoutingDecision`.
- Runtime no longer calls the model-capable `ConversationIntentRouter.routeBoundary(content, window, request, true)` for hard routes.

- [ ] **Step 1: Write failing runtime tests** for English Recommend/Compare/Replace plus hint, semantic unsafe preemption, deterministic recommendation precedence, provider-disabled deterministic success, provider-disabled hint-only clarification with zero classifier calls, and one classifier invocation per hard route.
- [ ] **Step 2: Run** `mvn.cmd -Dtest=ConversationalAgentRuntimeTest test`; expect failures from the old hint-to-fact and dual-classifier flow.
- [ ] **Step 3: Implement** the sequence `content/window -> deterministic boundary -> subjectGuard -> hard-route detection -> resolver.route -> boundary fallback or intelligence task`; pass the decided task directly into intelligence resolution and remove direct hint `FACT_LOOKUP` construction and hard-route model boundary calls.
- [ ] **Step 4: Re-run** runtime tests and expect PASS.

### Task 5: Documentation and verification

**Files:**
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/11-项目演进日志.md`

- [ ] **Step 1: Update docs** to describe the single-pass decision, precedence, provider-disabled clarification, and zero-question diagnostics.
- [ ] **Step 2: Run focused tests** for domain, resolver, prompt, adapter, runtime, intelligence, Bundle/PostgreSQL, controller, and mapper; expect zero failures.
- [ ] **Step 3: Run** `mvn.cmd test`; expect zero failures/errors.
- [ ] **Step 4: Run** `scripts/privacy-check.ps1 -Path backend/src/main`; expect PASS.
- [ ] **Step 5: Run** `git diff --check` and scan changed Java for `var`, `record`, and Lombok; expect no findings.
- [ ] **Step 6: Stage exact files and create one Chinese commit** describing the single-pass hard-route classification.
