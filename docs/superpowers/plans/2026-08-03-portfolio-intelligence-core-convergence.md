# PortfolioIntelligence Core Convergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `PortfolioIntelligence` the only authority for portfolio preset, rule, classification, subject, retrieval, evidence and result semantics, then remove `/api/v1/answers` and its independent runtime.

**Architecture:** `ConversationalAgentRuntime` keeps request/global conversation duties and calls one `PortfolioIntelligence.tryResolve(PortfolioTurn)` seam. The deep module owns portfolio interpretation and returns a closed `PortfolioDecision`; `NOT_PORTFOLIO` alone transfers control to general conversation. Existing task execution is retained behind the new facade while duplicate outer routing and fallback paths are removed.

**Tech Stack:** Java 21, Spring Boot 3.5, JUnit 5, AssertJ, Mockito, Maven.

## Global Constraints

- Production and test Java must not use `var`, `record`, or Lombok.
- Visitor questions, messages, answers, prompts and evidence bodies must not be logged.
- Only reviewed public snapshot data and APPROVED Evidence may be returned.
- No server-side conversation persistence.
- TDD is mandatory: every behavior change starts with a failing test.
- Do not commit without explicit user authorization.

---

### Task 1: Closed portfolio decision contract

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioTurn.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioDecision.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioDisposition.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerConstructionMode.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerIntentSource.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerEvidenceState.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerResolution.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/ConversationAnswerScope.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/domain/PortfolioDecisionTest.java`

**Interfaces:**
- Produces: immutable `PortfolioTurn`, `PortfolioDecision`, `PortfolioDisposition` and public result dimensions used by later tasks.
- `PortfolioDecision` must not contain diagnostics or adapter names.

- [ ] **Step 1: Write failing contract tests**

```java
@Test
void notPortfolioDecisionCannotCarryAnswerMaterial() {
    assertThatThrownBy(() -> PortfolioDecision.notPortfolio("turn-1")
            .withAnswer("forbidden"))
            .isInstanceOf(IllegalStateException.class);
}

@Test
void publicResolutionSeparatesClarificationSupportAndCapability() {
    assertThat(AnswerResolution.values()).contains(
            AnswerResolution.ANSWERED,
            AnswerResolution.NEEDS_CLARIFICATION,
            AnswerResolution.NOT_SUPPORTED,
            AnswerResolution.CAPABILITY_UNAVAILABLE,
            AnswerResolution.REJECTED);
}
```

- [ ] **Step 2: Run RED**

Run: `C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=PortfolioDecisionTest test`

Expected: compilation fails because the new contract does not exist.

- [ ] **Step 3: Implement explicit immutable types**

`PortfolioDisposition`:

```java
public enum PortfolioDisposition {
    ANSWERED,
    NEEDS_CLARIFICATION,
    NOT_SUPPORTED,
    NOT_PORTFOLIO
}
```

Use constructors/factories that reject invalid combinations. Add `GLOBAL` and `MIXED` scope values and migrate old `CONVERSATION/HYBRID` call sites in later tasks.

- [ ] **Step 4: Run GREEN**

Run the Task 1 test and the existing answer domain tests.

Expected: all selected tests pass.

### Task 2: v2 preset and reference request contract

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/dto/request/PortfolioReferenceContextRequest.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioReferenceContext.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioFollowUpAction.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioReferenceContextValidator.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/dto/request/ConversationAnswerRequest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/dto/request/ConversationAnswerContextRequest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/dto/request/ConversationAnswerRequestTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/PortfolioReferenceContextValidatorTest.java`

**Interfaces:**
- Consumes: public runtime content snapshot.
- Produces: validated `PortfolioReferenceContext` for Task 3.

- [ ] **Step 1: Add failing JSON tests**

```java
@Test
void readsPresetIdAndExplicitReferenceContext() throws Exception {
    ConversationAnswerRequest request = objectMapper.readValue(json, ConversationAnswerRequest.class);
    assertThat(request.getQuestionPresetId())
            .isEqualTo("question-sql-audit-async-and-recovery");
    assertThat(request.getContext().getReferenceContext().getFollowUpAction())
            .isEqualTo(PortfolioFollowUpAction.SHOW_EVIDENCE);
}
```

Add rejection tests for duplicate claim IDs, empty subjects, illegal preset ID, mutually inconsistent subject and reference context, and oversized lists.

- [ ] **Step 2: Run RED**

Run: `C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=ConversationAnswerRequestTest,PortfolioReferenceContextValidatorTest test`

Expected: new properties/types are missing.

- [ ] **Step 3: Implement request and validation types**

The request shape is:

```json
{
  "questionPresetId": "optional-stable-id",
  "context": {
    "referenceContext": {
      "contentVersion": "2026-07-29.1",
      "subjectIds": ["subject-id"],
      "questionPresetId": "optional-stable-id",
      "referencedClaimIds": ["claim-id"],
      "selectedSection": "VERIFICATION",
      "followUpAction": "SHOW_EVIDENCE"
    }
  }
}
```

The validator must normalize against one immutable public snapshot, reject unpublished IDs, set `contextVersionUpdated` only when all required references remain valid, and return a clarification outcome when every reference disappeared.

- [ ] **Step 4: Run GREEN**

Run the tests from Step 2.

Expected: pass.

### Task 3: Deep-module routing facade

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioIntelligence.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligence.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioPresetResolver.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioRuleResolver.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioSubjectResolver.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioRetrievalPlanner.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/PortfolioTaskResolver.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioIntelligenceConfiguration.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligenceTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/PortfolioPresetResolverTest.java`

**Interfaces:**
- Consumes: `PortfolioTurn`, public content snapshot, classifier gateway, retriever and recommendation policy.
- Produces: one `PortfolioDecision` for runtime integration.

- [ ] **Step 1: Write failing precedence tests**

Cover:

```java
@Test
void presetIdWinsWithoutCallingClassifier() { }

@Test
void canonicalAliasWinsWithoutCallingClassifier() { }

@Test
void deterministicRuleWinsWithoutCallingClassifier() { }

@Test
void projectHintDoesNotTurnGeneralQuestionIntoPortfolioTask() { }

@Test
void explicitReferenceWinsWithoutCallingClassifier() { }
```

Use Mockito only to assert the classifier/retriever ports were not called; assert the real returned decision fields.

- [ ] **Step 2: Run RED**

Run: `C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=DefaultPortfolioIntelligenceTest,PortfolioPresetResolverTest test`

Expected: current interface only accepts `PortfolioTask` and cannot satisfy precedence.

- [ ] **Step 3: Implement one entry point**

Change the public seam to:

```java
public interface PortfolioIntelligence {
    PortfolioDecision tryResolve(PortfolioTurn turn);
}
```

Inside `DefaultPortfolioIntelligence` execute:

```text
reference -> preset ID -> canonical/alias -> deterministic rule
-> constrained classifier -> NOT_PORTFOLIO/clarification
-> task validation -> retrieval/recommendation -> evidence policy -> decision
```

Keep `PortfolioTask` internal. Remove `route()`/`resolve()` precedence divergence by exposing only internal deterministic helpers and one classifier fallback.

- [ ] **Step 4: Run GREEN and refactor**

Run the Task 3 tests plus `PortfolioTaskResolverTest` and `PortfolioTaskValidatorTest`.

Expected: pass; deterministic cases verify zero classifier calls.

### Task 4: Retrieval relevance and typed failover

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/PortfolioRetrievalRequest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/bundle/BundlePortfolioRetriever.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/postgres/JdbcPostgresKnowledgeQuery.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/FailoverPortfolioRetriever.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/gateway/PortfolioRetrievalException.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/gateway/PortfolioRetrievalFailureKind.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/exception/PortfolioRetrievalFailedException.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/exception/AnswerErrorCode.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/bundle/BundlePortfolioRetrieverTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/postgres/JdbcPostgresKnowledgeQueryTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/FailoverPortfolioRetrieverTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/common/web/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Produces identical scoped-relevance behavior across adapters and safe typed terminal errors.

- [ ] **Step 1: Write failing adapter contract tests**

```java
@Test
void exactSubjectStillRanksPassagesByQueryAndClaimCategory() { }

@Test
void timeoutFallsBackButInvalidQueryDoesNot() { }

@Test
void dualRetrieverFailureMapsToSafeServiceError() { }
```

- [ ] **Step 2: Run RED**

Run the four Task 4 test classes.

Expected: exact paths return all passages and failure kinds do not exist.

- [ ] **Step 3: Implement scoped relevance and failure taxonomy**

`PortfolioRetrievalException` carries `PortfolioRetrievalFailureKind`. `FailoverPortfolioRetriever` only falls back for `CONNECTION_UNAVAILABLE` and `TIMEOUT`; it publishes/propagates contract/query/data faults. Convert terminal retrieval failure to an `ApplicationException` subtype handled by the existing global advice.

- [ ] **Step 4: Run GREEN**

Run the Task 4 tests and all intelligence adapter tests.

Expected: pass.

### Task 5: Runtime integration and result semantics

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/ConversationalAgentRuntime.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/ConversationIntentRouter.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/DeterministicConversationFallback.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/PortfolioIntelligenceAnswerAssembler.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/ConversationAnswerResult.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/dto/response/ConversationAnswerResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/mapper/ConversationAnswerResponseMapper.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/ConversationalAgentRuntimeTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/dto/response/ConversationAnswerResponseTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/mapper/ConversationAnswerResponseMapperTest.java`

**Interfaces:**
- Consumes: `PortfolioDecision` from Task 3.
- Produces: v2 public response with exact result dimensions and retained `noticeCode`.

- [ ] **Step 1: Write failing runtime tests**

Cover same preset consistency, project-page general question handoff, empty evidence `NOT_SUPPORTED`, provider-disabled `CAPABILITY_UNAVAILABLE`, explicit reference `REFERENCE`, and technical retrieval failure propagation.

- [ ] **Step 2: Run RED**

Run the three Task 5 test classes.

Expected: old hard-routing and `BOUNDARY/DETERMINISTIC` assertions fail.

- [ ] **Step 3: Integrate one PI call**

Remove `usesPortfolioIntelligence`, outer portfolio rule checks, `withSubjectConstraint`, outer preset fallback and outer portfolio result assembly. Keep global safety/time/greeting. Map `NOT_PORTFOLIO` to general routing and other decisions directly to the v2 response.

Retain `noticeCode` as a closed operational field. Replace response `generationMode/answerSource` with `constructionMode/intentSource/evidenceState`.

- [ ] **Step 4: Run GREEN**

Run Task 5 tests and `ConversationAnswerControllerTest`.

Expected: pass.

### Task 6: Remove v1 and obsolete C2b runtime

**Files:**
- Delete: `backend/src/main/java/com/portfolio/agent/answer/controller/AnswerController.java`
- Delete: `backend/src/main/java/com/portfolio/agent/answer/dto/request/AnswerRequest.java`
- Delete: `backend/src/main/java/com/portfolio/agent/answer/service/PortfolioAgentRuntime.java`
- Delete: `backend/src/main/java/com/portfolio/agent/answer/service/QuestionResolver.java`
- Delete after reference audit: old v1 response/mapper/runtime-only plan and C2b types
- Delete/migrate tests that only assert v1 behavior
- Test: `backend/src/test/java/com/portfolio/agent/answer/controller/ConversationAnswerControllerTest.java`

**Interfaces:**
- Consumes: completed v2 behavior from Tasks 1-5.
- Produces: one public answer endpoint and no old C2b execution framework.

- [ ] **Step 1: Add failing endpoint invariant**

```java
@Test
void legacyAnswerEndpointIsNotRegistered() throws Exception {
    mvc.perform(post("/api/v1/answers")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isNotFound());
}
```

- [ ] **Step 2: Run RED**

Expected: current v1 endpoint returns validation error rather than 404.

- [ ] **Step 3: Delete old entry and audit references**

Run `rg -n "/api/v1/answers|PortfolioAgentRuntime|QuestionResolver|ToolPlanBuilder|ToolPlanExecutor" backend frontend scripts README.md docs` and delete or migrate every current-authority reference. Historical specs/plans remain historical unless the status index needs clarification.

- [ ] **Step 4: Run GREEN and backend suite**

Run: `C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml test`

Expected: build success and v1 invariant passes.

### Task 7: Authoritative documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/00-文档状态索引.md`
- Modify: `docs/04-项目代码约束.md`
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/11-项目演进日志.md`

- [ ] **Step 1: Update current authority**

Document v2-only answers, PI single authority, reference-context semantics, capability-level degradation, new result fields and removed v1/C2b framework. Do not rewrite historical plans as if they had always described the new architecture.

- [ ] **Step 2: Verify references**

Run: `rg -n "/api/v1/answers|BOUNDARY · DETERMINISTIC|PortfolioAgentRuntime" README.md docs/00-文档状态索引.md docs/04-项目代码约束.md docs/08-当前实现状态.md docs/11-项目演进日志.md`

Expected: no current-authority statement presents removed behavior as active.
