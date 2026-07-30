# Agent Execution Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make v1 and v2 Agent routing, Provider, retrieval, Tool, validation, fallback, and startup outcomes diagnosable without recording visitor content.

**Architecture:** Domain decision publishers describe final outcomes, while Adapter seams publish safe stage events. The implementation reuses the request context and diagnostic publisher created by the core plan, so every event automatically carries the same request correlation fields.

**Tech Stack:** Java 21, Spring Boot 3.5.3, SLF4J 2, JUnit 5, AssertJ, Mockito

## Global Constraints

- Implement `2026-07-29-observability-core-error-contract.md` first.
- Production and test Java must not use `var`, record types, or Lombok.
- Do not change answer response content, routing policy, retrieval ranking, Tool budgets, Provider prompts, or fallback behavior.
- Never log questions, messages, answers, Prompt content, Provider payloads, retrieval terms, scores, vectors, chunk IDs, evidence IDs, raw IP addresses, credentials, paths, or URLs.
- Normal successful stage events are DEBUG; failures and degradations are WARN; final request decisions are INFO.
- Expected failures carry stable `DiagnosticCode`; they do not log throwable messages or stack traces.
- Follow RED, GREEN, REFACTOR for every task.

---

### Task 1: Replace the v1 no-op decision Adapter

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/adapter/observability/LoggingAnswerDecisionPublisher.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/service/DurationBuckets.java`
- Delete: `backend/src/main/java/com/portfolio/agent/answer/adapter/observability/NoopAnswerDecisionPublisher.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/adapter/observability/LoggingAnswerDecisionPublisherTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/DurationBucketsTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/service/PortfolioAgentRuntimeTest.java`

**Interfaces:**
- Consumes: `AnswerDecisionPublisher`, `DiagnosticEventPublisher`.
- Produces: `agent.request.completed` with v1 decision fields.
- Produces shared `DurationBuckets.fromElapsedMillis(long)` for all Agent stage events.

- [ ] **Step 1: Write the failing Adapter test**

```java
@Test
void publishesSafeV1DecisionFields() {
    publisher.publish(decision);

    assertThat(events).singleElement().satisfies(event -> {
        assertThat(event.getName()).isEqualTo("agent.request.completed");
        assertThat(event.getFields())
                .containsEntry("answer.resolution", "ANSWERED")
                .containsEntry("generation.mode", "DETERMINISTIC")
                .containsEntry("duration.bucket", "LT_100_MS")
                .doesNotContainKeys("question", "evidence.ids");
    });
}
```

- [ ] **Step 2: Run the test and verify RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=LoggingAnswerDecisionPublisherTest test
```

Expected: class does not exist.

- [ ] **Step 3: Implement the Adapter**

Build one INFO event containing:

```text
content.version
question.kind
audience.role
request.source
answer.resolution
answer.source
generation.mode
verification.status
duration.bucket
error.code
```

Do not include `projectSlug`, `questionPresetId`, or `evidenceIds` in production. Remove the
no-op bean so there is exactly one `AnswerDecisionPublisher`.

Move the current duration thresholds out of `PortfolioAgentRuntime`:

```java
public static DurationBucket fromElapsedMillis(long elapsedMillis) {
    if (elapsedMillis < 100) {
        return DurationBucket.LT_100_MS;
    }
    if (elapsedMillis < 500) {
        return DurationBucket.FROM_100_TO_499_MS;
    }
    if (elapsedMillis < 2000) {
        return DurationBucket.FROM_500_TO_1999_MS;
    }
    return DurationBucket.GE_2000_MS;
}
```

Replace the private v1 method with the shared function.

- [ ] **Step 4: Run focused tests**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=LoggingAnswerDecisionPublisherTest,PortfolioAgentRuntimeTest test
```

Expected: tests pass; a publisher exception still does not change the answer.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/adapter/observability backend/src/test/java/com/portfolio/agent/answer
git commit -m "可观测性：发布v1回答决策事件"
```

### Task 2: Add a typed v2 conversation decision

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/ConversationDecision.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/gateway/ConversationDecisionPublisher.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/adapter/observability/LoggingConversationDecisionPublisher.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/ConversationalAgentRuntime.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/ConversationIntentRouter.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ConversationalAgentConfiguration.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/adapter/observability/LoggingConversationDecisionPublisherTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/service/ConversationalAgentRuntimeTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/service/ConversationIntentRouterTest.java`

**Interfaces:**
- Produces: `ConversationDecisionPublisher.publish(ConversationDecision)`.
- Produces one `agent.request.completed` event for every successful, boundary, rejected, or degraded v2 result.

- [ ] **Step 1: Write failing final-decision tests**

Add cases for model success, provider-disabled fallback, unknown subject, unsafe intent,
Provider failure fallback, and validation failure fallback. Each invocation must capture exactly
one decision with:

```java
assertThat(decision.getResolution()).isEqualTo(result.getResolution());
assertThat(decision.isDegraded()).isEqualTo(result.isDegraded());
assertThat(decision.getDurationBucket()).isNotNull();
```

- [ ] **Step 2: Run RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ConversationalAgentRuntimeTest,LoggingConversationDecisionPublisherTest test
```

Expected: publisher and decision types do not exist.

- [ ] **Step 3: Implement the decision and runtime wrapper**

`ConversationDecision` contains only:

```java
private final Instant occurredAt;
private final String contentVersion;
private final ConversationIntent intent;
private final ConversationAnswerScope answerScope;
private final AnswerResolution resolution;
private final boolean degraded;
private final DurationBucket durationBucket;
```

Refactor `answer` without changing branch behavior:

```java
public ConversationAnswerResult answer(ConversationAnswerRequest request) {
    long startedAt = System.nanoTime();
    ConversationAnswerResult result = answerInternal(request);
    publishBestEffort(result, startedAt);
    return result;
}
```

`publishBestEffort` catches publisher failures. `answerInternal` contains the current method body.
The logging Adapter maps the decision to an INFO `agent.request.completed` event.

Inject `DiagnosticEventPublisher` into `ConversationIntentRouter`. After selecting the final
route, publish DEBUG `agent.route.decided` with only:

```text
conversation.intent
answer.scope
route.source=DETERMINISTIC|MODEL
duration.bucket
```

The router already knows whether it accepted the classified model result or used a deterministic
route, so it must set `route.source` at that decision point without changing `ConversationRoute`.

- [ ] **Step 4: Run focused and privacy tests**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ConversationalAgentRuntimeTest,LoggingConversationDecisionPublisherTest,PortfolioAgentRuntimeModelPrivacyTest test
```

Expected: all pass and captured events contain no request question or answer blocks.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer backend/src/test/java/com/portfolio/agent/answer
git commit -m "可观测性：发布v2对话决策事件"
```

### Task 3: Instrument Provider operations and fallback causes

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ProviderOperation.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/OpenAiCompatibleConversationalModelAdapter.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/OpenAiCompatibleModelExpressionAdapter.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ConversationalAgentConfiguration.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ModelExpressionConfiguration.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/ConversationalAgentRuntime.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/ModelAnswerCoordinator.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/adapter/model/OpenAiCompatibleConversationalModelAdapterTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/adapter/model/OpenAiCompatibleModelExpressionAdapterTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/service/ModelAnswerCoordinatorTest.java`

**Interfaces:**
- Produces: `provider.call.completed`, `provider.call.failed`, and
  `answer.fallback.selected`.
- Uses existing model failure enums as stable diagnostic codes through explicit mapping.

- [ ] **Step 1: Add failing event tests**

For each operation assert the Adapter publishes one of:

```text
CLASSIFY
PLAN_TOOLS
GENERATE
REVIEW
SUGGEST
SUMMARIZE
EXPRESS
```

Timeout test:

```java
assertThat(event.getName()).isEqualTo("provider.call.failed");
assertThat(event.getFields())
        .containsEntry("provider.operation", "GENERATE")
        .containsEntry("failure.code", "PROVIDER_TIMEOUT")
        .doesNotContainKeys("provider.name", "provider.url", "provider.payload");
```

- [ ] **Step 2: Run RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=OpenAiCompatibleConversationalModelAdapterTest,OpenAiCompatibleModelExpressionAdapterTest,ModelAnswerCoordinatorTest test
```

Expected: event assertions fail because no Publisher is injected.

- [ ] **Step 3: Instrument the common post/express seams**

Inject `DiagnosticEventPublisher` into both Adapters. Replace free-form operation strings at the
logging seam with `ProviderOperation`.

On success publish DEBUG:

```java
provider.call.completed
provider.operation
event.outcome=success
duration.bucket
response.present=true
```

On expected failure publish WARN with an explicit mapping:

```text
TIMEOUT              -> PROVIDER_TIMEOUT
PROVIDER_ERROR       -> PROVIDER_CONNECTION_FAILED
EMPTY_RESPONSE       -> PROVIDER_EMPTY_RESPONSE
INVALID_RESPONSE     -> PROVIDER_INVALID_RESPONSE
REQUEST_BUILD_FAILED -> PROVIDER_REQUEST_BUILD_FAILED
DRAFT_REJECTED       -> PROVIDER_DRAFT_REJECTED
```

Never pass the caught throwable to the publisher.

Publish `answer.fallback.selected` in both coordinators when a failed Provider or rejected draft
selects deterministic fallback. Include only `fallback.trigger` and `failure.code`.

Whenever either coordinator receives a validation result, publish:

```text
answer.validation.completed
validation.accepted
failure.code
duration.bucket
```

Accepted validation is DEBUG. Rejected validation is WARN. For v2 use
`ConversationDraftValidationResult.getFailureCode()`; for v1 use
`AnswerValidationResult.getFailureCode().name()`.

- [ ] **Step 4: Run focused tests**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=OpenAiCompatibleConversationalModelAdapterTest,OpenAiCompatibleModelExpressionAdapterTest,ModelAnswerCoordinatorTest,ConversationalAgentRuntimeTest test
```

Expected: success, timeout, invalid JSON, empty response, and draft rejection tests pass.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/adapter/model backend/src/main/java/com/portfolio/agent/answer/service backend/src/test/java/com/portfolio/agent/answer
git commit -m "可观测性：记录Provider阶段与降级原因"
```

### Task 4: Instrument retrieval and Tool execution

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/service/RetrievalFailureCode.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/service/ToolFailureCode.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/LocalRetrievalCoordinator.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/LocalEmbeddingFailureException.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/ToolPlanExecutor.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/ConversationToolService.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/retrieval/RetrievalConfiguration.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/service/LocalRetrievalCoordinatorTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/service/ToolPlanExecutorTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/service/ConversationToolServiceTest.java`

**Interfaces:**
- Produces: `retrieval.completed`, `retrieval.degraded`, and `tool.call.completed`.
- Converts local embedding string codes into `RetrievalFailureCode`.

- [ ] **Step 1: Write failing degradation and Tool event tests**

Assert hybrid embedding failure publishes:

```java
assertThat(event.getName()).isEqualTo("retrieval.degraded");
assertThat(event.getFields())
        .containsEntry("retrieval.requested_mode", "HYBRID_ENABLED")
        .containsEntry("retrieval.actual_mode", "KEYWORD_FALLBACK")
        .containsEntry("failure.code", "RETRIEVAL_INFERENCE_FAILED");
```

Assert a Tool result event contains kind, status, result counts, and duration, but no claim IDs,
evidence IDs, arguments, or content.

- [ ] **Step 2: Run RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=LocalRetrievalCoordinatorTest,ToolPlanExecutorTest test
```

Expected: no stage events are captured.

- [ ] **Step 3: Implement typed retrieval failures**

`RetrievalFailureCode` implements `DiagnosticCode` and includes every current local embedding
string code. Change `LocalEmbeddingFailureException` to carry the enum rather than a free string.
Map operational failures visible to the coordinator to:

```text
RETRIEVAL_INFERENCE_FAILED
RETRIEVAL_VECTOR_DIMENSION_MISMATCH
RETRIEVAL_MODEL_LOAD_FAILED
RETRIEVAL_EMBEDDING_DISABLED
```

Publish DEBUG `retrieval.completed` with requested/actual mode and counts only. Publish WARN
`retrieval.degraded` on keyword fallback.

- [ ] **Step 4: Instrument Tool aggregation**

Inject the publisher into `ToolPlanExecutor`. Measure each call and publish:

```text
tool.kind
tool.result_status
tool.claim_count
tool.evidence_count
duration.bucket
```

If validation throws, publish WARN `tool.call.completed` with
`failure.code=TOOL_RESULT_INVALID`, then rethrow the original exception so existing fallback
behavior remains unchanged.

`ConversationToolService` publishes DEBUG `tool.plan.completed` after each Provider plan result
with only round number, allowed Tool count, planned call count, result status, and duration
bucket. A failed plan publishes WARN with the mapped Provider FailureCode and no plan content.

- [ ] **Step 5: Run retrieval, Tool, and privacy suites**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=LocalRetrievalCoordinatorTest,ToolPlanExecutorTest,PortfolioAgentRuntimeRetrievalTest,PortfolioAgentRuntimeToolTest,PortfolioAgentRuntimeModelPrivacyTest test
```

Expected: all pass; captured fields contain no query, score, vector, claim ID, or evidence ID.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer backend/src/test/java/com/portfolio/agent/answer
git commit -m "可观测性：记录检索与Tool执行结果"
```

### Task 5: Add safe startup lifecycle events and complete the Agent gate

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/common/observability/ApplicationStartupDiagnostics.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/repository/file/JsonPublicPortfolioRepository.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/retrieval/RetrievalConfiguration.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/web/AnswerProductionConfiguration.java`
- Test: `backend/src/test/java/com/portfolio/agent/common/observability/ApplicationStartupDiagnosticsTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/repository/file/JsonPublicPortfolioRepositoryTest.java`
- Modify: `scripts/privacy-check.test.ps1`

**Interfaces:**
- Produces: `content.bundle.loaded`, `embedding.model.loaded`,
  `embedding.model.failed`, and `application.started`.

- [ ] **Step 1: Write failing startup event tests**

Verify successful bundle load records schema version, content version, retrieval-enabled flag,
document count, vector dimension, and duration bucket. Verify failures record only
`CONTENT_BUNDLE_INVALID` or `RETRIEVAL_MODEL_LOAD_FAILED` and never the release root or model
directory.

- [ ] **Step 2: Run RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ApplicationStartupDiagnosticsTest,JsonPublicPortfolioRepositoryTest test
```

Expected: startup events do not exist.

- [ ] **Step 3: Implement startup publication**

Use an `ApplicationReadyEvent` listener for `application.started`. Emit only:

```text
model_expression.enabled
conversation.enabled
retrieval.profile
answer.request_timeout_ms
answer.requests_per_minute
answer.max_concurrent
```

At content/model loading seams, publish success after validation. On failure publish the stable
code once and rethrow so startup still fails closed.

- [ ] **Step 4: Add a complete privacy fixture**

Run a request with unique sentinels in the visitor question, history, Provider response,
retrieval query, API key, raw IP, and exception message. Capture all diagnostic events and
rendered logs; assert none of the sentinels appears.

- [ ] **Step 5: Run the complete Agent diagnostic gate**

```powershell
mvn.cmd -f backend/pom.xml test
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.test.ps1
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path backend/src
```

Expected: all commands pass.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java backend/src/test/java scripts/privacy-check.test.ps1
git commit -m "可观测性：补齐启动事件与Agent隐私门禁"
```
