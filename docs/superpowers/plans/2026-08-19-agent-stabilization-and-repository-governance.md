# Agent 2.0 Stabilization and Repository Governance Implementation Plan
<!-- DOCUMENT_STATUS: ACTIVE -->

> **状态：** 用户已批准，正在按任务与 Replacement Slice 实施；未通过对应 Exit Gate 的项目不得标记完成。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Agent 2.0 production-bounded, clarification-safe, PostgreSQL-first in local development, documentation-truthful, and physically converged on the `turn` module.

**Architecture:** Preserve the existing closed Command → Goal → Plan → Engine → PublicAgentTurn → Settlement authority. First repair governance and runtime behavior, then prove the cross-end contract, and only afterward move remaining production capabilities out of `answer` through deletion-complete Replacement Slices.

**Tech Stack:** Java 21, Spring Boot 3.5, Maven, PostgreSQL 16/pgvector, Flyway, Java HttpClient, Vue 3, TypeScript, Vite, Vitest, Playwright, PowerShell.

## Global Constraints

- The approved design is `docs/superpowers/specs/2026-08-19-agent-stabilization-and-repository-governance-design.md`.
- Preserve the unique `/api/agent/turns` and `/api/agent/conversations/current` resources; do not restore `/api/v2`, stp-v1/v2/v3, Router, Confirmation, or compatibility bridges.
- Preserve user-owned and frontend-agent working-tree changes. Do not restore, stage, commit, or push without explicit user authorization.
- All future Git commit subjects and bodies are Chinese; conventional `type(scope):` prefixes may remain English.
- Production and test Java prohibit `var` and Lombok. `record` is allowed only for pure immutable carriers with defensive-copy/value-semantics tests.
- Source admission defaults: 10 RPM and 2 concurrent turns per anonymous source.
- Runtime concurrency defaults: 8 active turns per instance and 4 tasks per turn.
- Time defaults: Goal 8s, General 10s, Fact Expression 4s, retrieval/DB I/O 3s, Turn 20s, settlement reserve 2s, frontend 25s, gateway at least 30s, lease 35s.
- TTL defaults: Clarification 5m; Session, Context, replay, and terminal records 30m absolute with no access extension.
- Browser persistence is limited to one ResumeToken in sessionStorage. Questions, answers, handles, request history, prompts, and raw model output are never persisted.
- Every Level 3 task must wire the target authority, migrate consumers, delete the retired authority/config/tests, and pass a zero-reference gate in the same task.
- A task's commit step is conditional on explicit user authorization. Without it, stop after verification and report the exact diff.

---

## File and Ownership Map

| Unit | Primary files | Responsibility |
|---|---|---|
| Governance | `AGENTS.md`, `docs/04-项目代码约束.md`, `scripts/code-quality-check*.ps1`, `docs/agent-architecture-status.json` | Make written rules and release gates agree |
| Admission | `turn/api`, `turn/lifecycle`, `common/web` | Source RPM/concurrency, global active-turn capacity, 429 wire contract |
| Deadline | `turn/lifecycle`, `turn/execution`, `infrastructure/model` | One absolute deadline, cancellation, body-stall termination |
| Clarification | `turn/planning`, `turn/continuation`, `turn/lifecycle` | Typed blocked Goal and bounded recovery |
| State | `turn/state`, `db/context`, PostgreSQL configuration | Atomic encrypted short-lived state and cleanup |
| Local runtime | `application*.yml`, `start-local*.ps1`, `postgres-local*.ps1`, env examples | PostgreSQL-first local mode and explicit IN_MEMORY mode |
| Frontend integration | `frontend/src/features/agent`, `frontend/e2e` | 25s wait cap and cross-end acceptance only; preserve completed frontend work |
| Documentation | README/current docs/docs11/docs15/docs16, `documentation-check*.ps1` | Current truth, concise history, drift prevention |
| Final convergence | `answer`, `selection`, `turn`, `evaluation` | Remove production `turn ↔ answer` dependencies |

---

### Task 1: Make Engineering Rules and Architecture Status Truthful

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/04-项目代码约束.md`
- Modify: `docs/agent-architecture-status.json`
- Modify: `scripts/code-quality-check.ps1`
- Modify: `scripts/code-quality-check.test.ps1`
- Modify: the 16 test files currently reported by `rg -n '\bvar\s+' backend/src/test/java`

**Interfaces:**
- Consumes: approved §11 record/var policy.
- Produces: a code-quality gate that blocks `var` and Lombok without blocking valid `record` declarations or `Record record` variables; architecture status `IN_PROGRESS`.

- [ ] **Step 1: Add checker regression fixtures before changing the checker**

Add positive fixtures containing `public record Pair(String left, String right) {}` and `ClarificationStore.Record record = value;`, plus a negative fixture containing `var result = call();`.

```powershell
$validRecord = 'public record Pair(String left, String right) {}'
$validNamedRecord = 'ClarificationStore.Record record = value;'
$invalidVar = 'var result = call();'
```

- [ ] **Step 2: Run the checker self-test and verify RED**

Run: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.test.ps1`

Expected: FAIL because the current `record-type` pattern rejects allowed records and/or the new positive fixture.

- [ ] **Step 3: Remove the universal record ban and retain exact var/Lombok checks**

The production rule set becomes:

```powershell
$rules = @(
    @{ Name = 'var-local'; Pattern = '\bvar\s+[A-Za-z_$][A-Za-z0-9_$]*\s*(?:=|:)' },
    @{ Name = 'lombok-import'; Pattern = '^\s*import\s+lombok\.' },
    @{ Name = 'lombok-qualified-annotation'; Pattern = '@\s*lombok\.(Data|Getter|Setter|Value|Builder|RequiredArgsConstructor|AllArgsConstructor|NoArgsConstructor|Slf4j)\b' }
)
```

Document that record suitability is enforced through code review and immutable-value tests, not a path-blind regex.

- [ ] **Step 4: Replace every test `var` with its explicit static type**

Run before editing: `rg -n '\bvar\s+[A-Za-z_$][A-Za-z0-9_$]*\s*=' backend/src/test/java`

Expected after editing: zero matches.

- [ ] **Step 5: Atomically update both authority documents**

`AGENTS.md` and `docs/04` must both say:

```text
record 只允许纯不可变数据载体；复杂领域对象继续使用显式不可变类。
生产和测试 Java 均禁止 var 与 Lombok。
```

- [ ] **Step 6: Mark architecture convergence as incomplete**

Set `overallStatus` to `IN_PROGRESS`, then audit every hard-invariant evidence sentence against the current tree. Do not leave a PASS sentence claiming a gate that currently fails.

Apply these exact corrections:

```text
SINGLE_RUNTIME_AUTHORITY = PASS
evidence = The unversioned controllers, AgentTurnLifecycleService and
PublicAgentTurnProjector are the only callable Agent runtime authorities;
physical turn↔answer package convergence remains unfinished and is tracked
by overallStatus=IN_PROGRESS.

EVIDENCE_BEFORE_COMPLETION = FAILED
evidence = The previous COMPLETE declaration preceded the current code-quality
and zero-import gates; Task 12 must replace this with fresh final evidence.
```

Keep other invariant statuses only when their evidence remains factually true. Add no waived/deferred item for required module work: Tasks 9–11 are mandatory convergence work, not a soft environmental gate.

- [ ] **Step 7: Run focused and repository-wide gates**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1 -Path backend/src
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/agent-architecture-status.ps1
```

Expected: all exit 0; architecture output reports `overall=IN_PROGRESS`.

- [ ] **Step 8: Commit only if explicitly authorized**

```powershell
git add AGENTS.md docs/04-项目代码约束.md docs/agent-architecture-status.json scripts/code-quality-check.ps1 scripts/code-quality-check.test.ps1 backend/src/test/java
git commit -m "chore: 统一 Java 值对象规则与架构状态"
```

### Task 2: Replace the Legacy Answer Admission Gate with Turn Admission

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/turn/api/AgentRequestAdmissionGate.java`
- Create: `backend/src/main/java/com/portfolio/agent/turn/api/AgentRequestAdmission.java`
- Create: `backend/src/main/java/com/portfolio/agent/turn/api/AgentAdmissionRejectedException.java`
- Create: `backend/src/main/java/com/portfolio/agent/turn/lifecycle/ActiveTurnCapacity.java`
- Create: `backend/src/main/java/com/portfolio/agent/turn/infrastructure/AgentRuntimeProperties.java`
- Modify: `backend/src/main/java/com/portfolio/agent/turn/api/AgentTurnController.java`
- Modify: `backend/src/main/java/com/portfolio/agent/turn/infrastructure/AgentCapabilityConfiguration.java`
- Modify: `backend/src/main/resources/application.yml`
- Delete after migration: `backend/src/main/java/com/portfolio/agent/answer/service/AnswerAdmissionGate.java`
- Delete after migration: `backend/src/main/java/com/portfolio/agent/answer/service/AnswerAdmission.java`
- Delete/migrate: corresponding old admission tests and exceptions under `answer/service` and `answer/exception`
- Test: `backend/src/test/java/com/portfolio/agent/turn/api/AgentRequestAdmissionGateTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/turn/api/AgentTurnAdmissionControllerTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/turn/lifecycle/ActiveTurnCapacityTest.java`

**Interfaces:**
- Consumes: `ClientAddressResolver`, `AnonymousSourceHasher`, requestId, `AgentRuntimeProperties`.
- Produces: `AgentRequestAdmission acquire(String sourceHash, UUID requestId)` and `ActiveTurnCapacity.Lease tryAcquire()`; both are AutoCloseable leases released in `finally`.

- [ ] **Step 1: Write RED tests for 10 RPM, source concurrency 2, global capacity 8, and release-on-failure**

```java
@Test
void thirdConcurrentRequestFromOneSourceReturnsOneSecondRetry() {
    AgentRequestAdmission first = gate.acquire("source-a", UUID.randomUUID());
    AgentRequestAdmission second = gate.acquire("source-a", UUID.randomUUID());
    assertThatThrownBy(() -> gate.acquire("source-a", UUID.randomUUID()))
            .isInstanceOf(AgentAdmissionRejectedException.class);
    first.close();
    second.close();
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn.cmd -f backend/pom.xml -Dtest=AgentRequestAdmissionGateTest,ActiveTurnCapacityTest test`

Expected: FAIL because the turn-owned types do not exist.

- [ ] **Step 3: Implement turn-owned admission leases**

Use closed rejection reasons internally:

```java
public enum RejectionReason {
    SOURCE_RPM_LIMIT,
    SOURCE_CONCURRENCY_LIMIT,
    GLOBAL_ACTIVE_TURN_LIMIT
}
```

All public rejection paths map to HTTP 429 `RATE_LIMITED`; internal reason never enters the response.

- [ ] **Step 4: Wire Controller admission without changing request fingerprint**

Controller flow:

```java
String address = clientAddressResolver.resolve(httpRequest);
String sourceHash = anonymousSourceHasher.hash(address);
try (AgentRequestAdmission sourceLease = admission.acquire(sourceHash, request.getRequestId());
     ActiveTurnCapacity.Lease activeLease = activeTurnCapacity.acquire()) {
    return map(lifecycle.execute(bearer.token(), mapper.toCommand(request)));
}
```

- [ ] **Step 5: Return both Retry-After representations**

Derive the expected value from the same configured gate and injected Clock used by the request. For the RPM case, configure one request per minute, acquire/release the first request at `t=0`, advance the Clock by 55 seconds, then assert that the real gate rejection reports 5 seconds:

```java
MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"));
AgentRequestAdmissionGate gate = new AgentRequestAdmissionGate(clock, 1, 2, 10_000);
gate.acquire("source-a", UUID.randomUUID()).close();
clock.advance(Duration.ofSeconds(55));
AgentAdmissionRejectedException rejection = catchThrowableOfType(
        () -> gate.acquire("source-a", UUID.randomUUID()),
        AgentAdmissionRejectedException.class);
assertThat(rejection.getRetryAfterSeconds()).isEqualTo(5);
```

Define the test clock in `AgentRequestAdmissionGateTest` so the test has no wall-clock dependency:

```java
private static final class MutableClock extends Clock {
    private Instant current;
    private MutableClock(Instant current) { this.current = current; }
    private void advance(Duration duration) { current = current.plus(duration); }
    @Override public Instant instant() { return current; }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
}
```

Drive the controller with that rejection and assert both wire representations use the derived value:

```java
.andExpect(header().string("Retry-After", "5"))
.andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
.andExpect(jsonPath("$.error.retryAfterSeconds").value(5));
```

- [ ] **Step 6: Delete the retired answer-owned admission authority**

Run: `rg -n 'AnswerAdmissionGate|AnswerAdmissionRejectedException|ANSWER_RATE_LIMITED|ANSWER_CONCURRENCY_LIMITED' backend/src`

Expected after deletion/migration: zero production references; only historical docs may mention old names.

- [ ] **Step 7: Run controller, admission, and architecture tests**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=AgentRequestAdmissionGateTest,AgentTurnAdmissionControllerTest,ActiveTurnCapacityTest,AgentTurnControllerContractTest test
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1 -Path backend/src
```

Expected: PASS.

- [ ] **Step 8: Commit only if explicitly authorized**

```powershell
git add backend/src/main/java/com/portfolio/agent/turn backend/src/main/java/com/portfolio/agent/answer backend/src/test/java backend/src/main/resources/application.yml
git commit -m "feat: 为 Agent Turn 接入生产准入保护"
```

### Task 3: Establish One Absolute Turn Deadline and Real Transport Cancellation

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/turn/execution/TurnDeadline.java`
- Modify: `backend/src/main/java/com/portfolio/agent/turn/lifecycle/AgentTurnLifecycleService.java`
- Modify: `backend/src/main/java/com/portfolio/agent/turn/planning/GoalResolver.java`
- Modify: `backend/src/main/java/com/portfolio/agent/turn/planning/GoalInterpretationPort.java`
- Modify: `backend/src/main/java/com/portfolio/agent/turn/infrastructure/model/GoalInterpretationAdapter.java`
- Modify: `backend/src/main/java/com/portfolio/agent/infrastructure/model/OpenAiCompatibleStructuredModelTransport.java`
- Modify: `backend/src/main/java/com/portfolio/agent/turn/infrastructure/AgentCapabilityConfiguration.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/portfolio/agent/turn/lifecycle/AgentTurnLifecycleDeadlineTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/infrastructure/model/OpenAiCompatibleStructuredModelTransportDeadlineTest.java`
- Extend: existing Engine deadline/cancel/late-result tests.

**Interfaces:**
- Consumes: `AgentRuntimeProperties.turnTimeout`, `settlementReserve`, operation caps.
- Produces: one `TurnDeadline` passed into Goal Interpretation and Execution; transport cancellation that includes response-body time.

- [ ] **Step 1: Write RED lifecycle tests proving Goal Interpretation consumes the same deadline**

```java
@Test
void slowGoalInterpretationCannotOutliveTurnDeadline() {
    Duration testTurnTimeout = Duration.ofMillis(200);
    Duration testSettlementReserve = Duration.ofMillis(20);
    GoalInterpretationPort slow = (input, deadline) -> {
        while (!deadline.isExpired()) {
            LockSupport.parkNanos(Duration.ofMillis(5).toNanos());
        }
        throw new GoalInterpretationUnavailableException();
    };
    LifecycleTestFixture fixture = fixture(
            slow, testTurnTimeout, testSettlementReserve);
    long startedAt = System.nanoTime();
    AgentTurnLifecycleService.Result result = fixture.execute(
            null, fixture.ask("解释幂等"));
    assertThat(result.turn()).isInstanceOf(PublicAgentTurn.CapabilityUnavailable.class);
    assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
            .isLessThan(Duration.ofSeconds(1));
}
```

- [ ] **Step 2: Write a deterministic body-stall transport test**

Use a local fake `HttpServer` that sends headers and never completes the JSON body. Inject a 150ms operation cap and a 300ms TurnDeadline; assert the adapter terminates in under one second and reports `DEADLINE_EXCEEDED`, not success. No test in this task may wait for the production 20-second default.

- [ ] **Step 3: Run focused tests and verify RED**

Run: `mvn.cmd -f backend/pom.xml -Dtest=AgentTurnLifecycleDeadlineTest,OpenAiCompatibleStructuredModelTransportDeadlineTest test`

Expected: FAIL because Goal Interpretation starts a new deadline and synchronous body reading can exceed the outer budget.

- [ ] **Step 4: Change the Goal Interpretation port signature**

```java
public interface GoalInterpretationPort {
    GoalInterpretationResult interpret(
            GoalInterpretationInput input,
            TurnDeadline deadline);
}
```

`GoalResolver.resolve` accepts the same deadline. No adapter creates a new wall-clock budget.

- [ ] **Step 5: Create the deadline at Lifecycle admission and reserve settlement time**

```java
TurnDeadline turnDeadline = TurnDeadline.after(properties.getTurnTimeout(), clock);
TurnDeadline executionDeadline = turnDeadline.minus(properties.getSettlementReserve());
```

Use `turnDeadline` for Goal/Planning and `executionDeadline` for Task scheduling; settlement receives the original remaining Turn budget.

- [ ] **Step 6: Replace blocking body handling with bounded async completion**

```java
CompletableFuture<HttpResponse<String>> future = client.sendAsync(
        httpRequest, HttpResponse.BodyHandlers.ofString());
try {
    return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
} catch (TimeoutException timeoutFailure) {
    future.cancel(true);
    throw new StructuredModelFailure(StructuredModelFailure.Code.DEADLINE_EXCEEDED, timeoutFailure);
}
```

Map interruption/cancellation separately from provider rejection. Do not retry or switch provider.

- [ ] **Step 7: Add startup validation for the approved budget relation**

Assert Turn 20s > reserve 2s; operation caps positive; frontend/gateway values remain documentation/deployment checks; lease 35s exceeds Turn 20s plus recovery margin.

- [ ] **Step 8: Run deadline, cancel, late-result, lifecycle, and transport suites**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=AgentTurnLifecycleDeadlineTest,AgentTurnLifecycleCancellationTest,SemanticTurnEngineDeadlineTest,SemanticTurnEngineLateResultTest,OpenAiCompatibleStructuredModelTransportDeadlineTest test
```

Expected: PASS with no real network call.

- [ ] **Step 9: Commit only if explicitly authorized**

```powershell
git add backend/src/main/java backend/src/test/java backend/src/main/resources/application.yml
git commit -m "fix: 统一 Agent 全链绝对截止时间"
```

### Task 4: Replace Clarification Reinterpretation with Typed Goal Recovery

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/turn/continuation/BlockedGoalTemplate.java`
- Create: `backend/src/main/java/com/portfolio/agent/turn/continuation/ClarificationAnswerNormalizer.java`
- Modify: `backend/src/main/java/com/portfolio/agent/turn/planning/ClarificationProposal.java`
- Modify: `backend/src/main/java/com/portfolio/agent/turn/planning/GoalInterpretationResult.java`
- Modify: `backend/src/main/java/com/portfolio/agent/turn/planning/GoalProposalCodec.java`
- Modify: `backend/src/main/java/com/portfolio/agent/turn/planning/MinimalGoalFallback.java`
- Modify: `backend/src/main/java/com/portfolio/agent/turn/planning/GoalResolver.java`
- Modify: `backend/src/main/java/com/portfolio/agent/turn/continuation/ClarificationStore.java`
- Modify: `backend/src/main/java/com/portfolio/agent/turn/lifecycle/AgentTurnLifecycleService.java`
- Modify: `docs/15-Agent 2.0真实交互问题清单与修复边界.md` (replace raw input-anchor recovery wording; keep issues open until Exit Gates)
- Test: `backend/src/test/java/com/portfolio/agent/turn/continuation/BlockedGoalTemplateTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/turn/lifecycle/AgentTurnLifecycleClarificationRecoveryTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/turn/planning/DeterministicConversationBoundaryTest.java`

**Interfaces:**
- Consumes: typed `UserGoalProposal.ProposedGoal`, public subject catalog, CHOICE/TEXT answer.
- Produces: privacy-safe `BlockedGoalTemplate.resolve(...) -> Resolution`, preserving goal kind/subjects/outputs/parameters without retaining raw input.

- [ ] **Step 1: Write RED tests for the three user-visible failures**

```java
@Test void recommendationWithoutCountDefaultsToTwoAndNeedsNoSubject() { }
@Test void greetingProducesConversationalWithoutCallingProvider() { }
@Test void clarificationAnswerRestoresRecommendationSizeAndConstraints() { }
```

Each test must assert provider call count and the final Goal kind/parameters, not only response text.

- [ ] **Step 2: Write privacy and termination tests**

Assert serialized `BlockedGoalTemplate` contains no original question/input anchor, the same field cannot be asked twice, and a third clarification cannot be created.

- [ ] **Step 3: Run focused tests and verify RED**

Run: `mvn.cmd -f backend/pom.xml -Dtest=BlockedGoalTemplateTest,AgentTurnLifecycleClarificationRecoveryTest,DeterministicConversationBoundaryTest test`

Expected: FAIL because current Resolve wraps TEXT as a fresh ASK.

- [ ] **Step 4: Implement a closed typed template**

The public shape is equivalent to:

```java
public final class BlockedGoalTemplate {
    private final GoalKind goalKind;
    private final List<GoalSubjectReference> subjects;
    private final Set<GoalRequestedOutput> requestedOutputs;
    private final UserGoalProposal.GoalParameters parameters;
    private final ClarificationProposal.Field unresolvedField;
    private final Set<ClarificationProposal.Field> resolvedFields;
    private final int depth;
}
```

Use defensive copies. Do not store `InputAnchor`, raw question, ConversationWindow, prompt, or model output.

- [ ] **Step 5: Make ClarificationProposal carry the template**

Model and deterministic clarification producers must provide a typed partial Goal. Extend the strict provider schema so CLARIFICATION includes `blockedGoal`; reject unknown/missing fields atomically.

- [ ] **Step 6: Add deterministic social and recommendation boundaries before provider interpretation**

Recognize safe greeting/thanks and recommendation count expressions. “推荐项目” creates requestedSize=2; 1–5 is accepted; invalid/conflicting count creates one bounded clarification; lack of a named candidate never creates subject clarification.

- [ ] **Step 7: Replace fresh-ASK recovery in Lifecycle**

Delete the branch that constructs `new AgentTurnCommand.Ask(...text...)`. Consume the challenge, normalize the answer against the current public catalog, resolve the template, and send the resulting proposal through the existing `SemanticPlanCompiler`.

- [ ] **Step 8: Enforce bounded clarification**

No repeated field, no no-information answer, maximum depth two across distinct fields. Failure returns a closed terminal with a new-ASK action, not another same-field challenge.

- [ ] **Step 9: Run Goal, Codec, Lifecycle, Projection and API contract tests**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=BlockedGoalTemplateTest,AgentTurnLifecycleClarificationRecoveryTest,GoalProposalCodecTest,AgentTurnClosedContractIntegrationTest test
```

Expected: PASS; serialized state scans contain none of the supplied visitor sentinel.

- [ ] **Step 10: Commit only if explicitly authorized**

```powershell
git add backend/src/main/java/com/portfolio/agent/turn backend/src/test/java/com/portfolio/agent/turn
git commit -m "fix: 以强类型状态恢复澄清前目标"
```

### Task 5: Finalize PostgreSQL Agent State Schema, TTL, and Cleanup

**Files:**
- Create: `backend/src/main/resources/db/context/V3__final_agent_state.sql`
- Modify: `backend/src/main/java/com/portfolio/agent/turn/state/postgres/AgentStatePayloadCodec.java`
- Modify: `backend/src/main/java/com/portfolio/agent/turn/state/postgres/JdbcAgentStateStore.java`
- Modify: `backend/src/main/java/com/portfolio/agent/turn/state/postgres/JdbcConversationSessionStore.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/context/adapter/postgres/ConversationContextProperties.java` (temporary owner; moved in Task 9)
- Modify: `backend/src/main/java/com/portfolio/agent/answer/context/adapter/postgres/ConversationContextDatabaseConfiguration.java` (temporary owner; moved in Task 9)
- Test: `backend/src/test/java/com/portfolio/agent/turn/state/postgres/JdbcAgentStateStoreIntegrationTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/turn/state/postgres/AgentStatePayloadCodecTest.java`
- Create: `backend/src/test/java/com/portfolio/agent/turn/state/postgres/AgentStateCleanupIntegrationTest.java`

**Interfaces:**
- Consumes: final `ClarificationStore.Record` containing BlockedGoalTemplate; TTL properties.
- Produces: one PostgreSQL state authority with atomic settlement, absolute expiry, cleanup, and no legacy P3/P5 tables/readers.

- [ ] **Step 1: Extend Codec tests with privacy sentinels and typed blocked Goal round-trip**

Assert ciphertext round-trips typed state and serialized plaintext test probes do not contain the original visitor question or ConversationWindow.

- [ ] **Step 2: Add RED integration tests for exact TTL and cleanup behavior**

Use a mutable Clock or explicit timestamps; do not sleep. Prove 5m Challenge expiry, 30m Context/replay/session expiry, no read extension, and bounded cleanup.

- [ ] **Step 3: Run PostgreSQL tests and verify RED**

Run: `mvn.cmd -f backend/pom.xml -Dtest=JdbcAgentStateStoreIntegrationTest,AgentStatePayloadCodecTest,AgentStateCleanupIntegrationTest test`

Expected: FAIL on missing final schema/cleanup behavior.

- [ ] **Step 4: Write the final Flyway migration**

Because the project is not deployed and State is disposable, `V3` removes obsolete V1 receipt/context tables after migrating to the V2 turn-owned tables, adds any missing expiry/cleanup indexes, and does not retain compatibility views.

```sql
DROP TABLE IF EXISTS agent_context.conversation_request_receipt CASCADE;
DROP TABLE IF EXISTS agent_context.conversation_active_context CASCADE;
DROP TABLE IF EXISTS agent_context.conversation_context CASCADE;
-- conversation_session remains the final table used by JdbcConversationSessionStore.
```

`conversation_session` remains because `JdbcConversationSessionStore` is the final session authority. `V3` must not recreate, rename, or dual-write it; it only removes the retired V1 context/receipt surface and completes indexes/constraints for the V2 turn-owned tables.

- [ ] **Step 5: Make TTL absolute and non-extending**

Session token rotation preserves the original absolute expiry. Reads and replay do not update expiry. Store lease is 35s; replay/context/session/terminal retention is 30m; challenge is 5m.

- [ ] **Step 6: Implement bounded cleanup**

Cleanup deletes expired executions, contexts, challenges, revoked sessions, orphan rows, and payloads whose key is no longer supported. Limit each transaction by configured batch size; expose only low-cardinality counts.

- [ ] **Step 7: Run Memory/PostgreSQL contract parity**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=TurnExecutionStoreContractTest,JdbcAgentStateStoreIntegrationTest,AgentStatePayloadCodecTest,AgentStateCleanupIntegrationTest test
```

Expected: PASS.

- [ ] **Step 8: Commit only if explicitly authorized**

```powershell
git add backend/src/main/resources/db/context backend/src/main/java/com/portfolio/agent/turn/state backend/src/main/java/com/portfolio/agent/answer/context backend/src/test/java/com/portfolio/agent/turn/state
git commit -m "feat: 收口短期加密 Agent State"
```

### Task 6: Make Local PostgreSQL the Standard Development Mode

**Files:**
- Modify: `backend/src/main/resources/application-local.yml`
- Modify: `backend/src/main/resources/application-prod.yml`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `scripts/start-local.ps1`
- Modify: `scripts/start-local.test.ps1`
- Modify: `scripts/postgres-local.ps1`
- Modify: `scripts/postgres-local.test.ps1`
- Modify: `.env.postgres.example`
- Modify: `.env.example`
- Create: `backend/src/test/java/com/portfolio/agent/answer/context/adapter/postgres/ConversationContextPropertiesTest.java` (moved with production package in Task 9)
- Create: `backend/src/test/java/com/portfolio/agent/answer/context/adapter/postgres/ConversationContextDatabaseConfigurationTest.java` (moved with production package in Task 9)

**Interfaces:**
- Consumes: existing `postgres-local start/bootstrap/status/verify`; approved modes.
- Produces: standard local PostgreSQL State, explicit IN_MEMORY, explicit DISABLED content-only mode, optional Provider secret.

- [ ] **Step 1: Add RED script tests for standard, memory, and content-only modes**

Assert standard local refuses to launch when PostgreSQL readiness fails and prints the exact `postgres-local.ps1 start` recovery command. Assert IN_MEMORY never invokes Docker. Assert no-model local launch does not require `SecretsFile`.

- [ ] **Step 2: Run script tests and verify RED**

Run: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.test.ps1`

Expected: FAIL because current local default is DISABLED and SecretsFile is mandatory.

- [ ] **Step 3: Replace stale model environment keys**

Delete `PORTFOLIO_MODEL_OP_ROUTING_*`, old semantic-classifier and model-expression aliases from the current launcher. Set only current Goal Interpretation, General Knowledge and optional Fact Expression operation properties.

- [ ] **Step 4: Make secrets conditional**

`-SecretsFile` is optional unless a real model operation is enabled. `-PostgresEnvFile` defaults to the Git-ignored `.env.postgres.local`. Derive the context JDBC URL from port/database name and import stable token/payload keys.

- [ ] **Step 5: Keep Docker lifecycle explicit**

`start-local.ps1` calls readiness/status checks only. It never starts, stops, bootstraps, resets, imports, or activates PostgreSQL. Error output names the exact explicit command.

- [ ] **Step 6: Update application profiles**

Standard local and prod use POSTGRESQL; explicit CLI/profile selects IN_MEMORY; DISABLED provides public content while Agent readiness/UI report unavailable. No automatic PostgreSQL→Memory fallback.

- [ ] **Step 7: Run script and configuration tests**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/postgres-local.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.test.ps1
mvn.cmd -f backend/pom.xml -Dtest=ConversationContextPropertiesTest,ConversationContextDatabaseConfigurationTest test
```

Expected: PASS.

- [ ] **Step 8: Commit only if explicitly authorized**

```powershell
git add backend/src/main/resources backend/src/test/java scripts/start-local.ps1 scripts/start-local.test.ps1 scripts/postgres-local.ps1 scripts/postgres-local.test.ps1 .env.example .env.postgres.example
git commit -m "feat: 统一本地 PostgreSQL Agent State 入口"
```

### Task 7: Close the Cross-End Contract and Browser Gates

**Files:**
- Modify only after frontend ownership is released: `frontend/src/features/agent/api/agentTurnApi.ts`
- Modify: `frontend/src/features/agent/api/agentTurnApi.test.ts`
- Modify: `frontend/e2e/agent-final-contract.spec.ts`
- Modify: `frontend/playwright.config.ts` to register the explicit slow-provider fixture project
- Modify: `scripts/run-jar-e2e.ps1`
- Modify: `scripts/run-jar-e2e.test.ps1`
- Modify: `docs/15-Agent 2.0真实交互问题清单与修复边界.md`

**Interfaces:**
- Consumes: backend 20s TurnDeadline, 429 dual-channel response, final clarification state, completed frontend session work.
- Produces: 25s frontend wait cap and complete packaged-JAR acceptance evidence.

- [ ] **Step 1: Confirm frontend file ownership is released and capture a fresh diff**

Run: `git status --short` and `git diff -- frontend/src/features/agent frontend/e2e`.

Expected: no other agent actively editing these files; preserve all existing modifications.

- [ ] **Step 2: Change the temporary wait cap with its tests and comments atomically**

```ts
const REQUEST_TIMEOUT_MS = 25_000
```

Update docs/15 §10.5 and §11.4 in the same task. Do not change user-cancel semantics.

- [ ] **Step 3: Add browser cases for the approved recovery matrix**

Cover two pending sessions blocking a third request, slot release, clarification→resolve→answer, read-only consumed card, preset-driven escape entry, 429 countdown, timeout same-request replay, current/recent sources, and desktop/mobile.

- [ ] **Step 4: Add a Fake Provider body-stall packaged lane**

The fake sends headers and stalls the body beyond the operation cap. Assert the server settles before 20s, the frontend does not silently disappear, and the same requestId reaches one terminal outcome.

- [ ] **Step 5: Run frontend and packaged gates**

```powershell
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run check
npm.cmd --prefix frontend run build
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-jar-e2e.ps1 -ContextMode POSTGRESQL
```

Expected: all PASS; no Vue warnings; no API mocks in packaged-JAR lane.

- [ ] **Step 6: Run explicitly authorized live-provider scenarios**

Run only with user-provided authorization and repository-external secrets. Verify conversational, general, two-project recommendation, clarification/resolve, and latency summary without outputting prompts or content.

- [ ] **Step 7: Update docs/15 conservatively**

Delete only issues whose complete §12 gates passed. Keep backend or real-provider items open when their exact gate was not run.

- [ ] **Step 8: Commit only if explicitly authorized**

```powershell
git add frontend/src/features/agent frontend/e2e frontend/playwright.config.ts scripts/run-jar-e2e.ps1 scripts/run-jar-e2e.test.ps1 docs/15-Agent* docs/11-项目演进日志.md
git commit -m "test: 关闭 Agent 跨端真实交互验收"
```

### Task 8: Rebuild Current Documentation, Evolution Log, and Comment Boundaries

**Files:**
- Rewrite: `README.md`
- Rewrite: `SECURITY.md`
- Rewrite: `docs/00-文档状态索引.md`
- Modify: `docs/04-项目代码约束.md`
- Rewrite: `docs/08-当前实现状态.md`
- Modify: `docs/09-作品集资产库状态.md`
- Modify: `docs/10-本地PostgreSQL与pgvector运行手册.md`
- Rewrite/reorder: `docs/11-项目演进日志.md`
- Mark historical: `docs/12-工程质量与未来优化评审备忘录.md`, `docs/13-Agent对话体验与智能编排改造路线图.md`, `docs/14-Agent架构债与防御性设计评审.md`
- Create: `scripts/documentation-check.ps1`
- Create: `scripts/documentation-check.test.ps1`
- Modify: `scripts/verify-release.ps1`
- Modify targeted comments in backend core files changed by Tasks 2–6.

**Interfaces:**
- Consumes: final manifest, current controllers/configuration, docs/15 outcome, approved comment policy.
- Produces: concise current authority set, monotonic docs/11, and a release-blocking mechanical facts gate.

- [ ] **Step 1: Write documentation-check negative and positive fixtures**

Negative fixtures include old `/api/v2/answers`, duplicate docs/11 dates, a decreasing date, a missing link, a stale manifest count, and an unknown current environment key. Positive fixtures include historical old API text with a historical header.

- [ ] **Step 2: Run documentation checker tests and verify RED**

Run: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/documentation-check.test.ps1`

Expected: FAIL because the checker does not exist.

- [ ] **Step 3: Implement the scoped checker**

The checker scans only the approved current-authority list, parses manifest JSON, validates four unversioned resources, validates docs/11 unique ascending dates, and verifies Markdown link targets. It does not ban old facts inside historical specs/plans/reports.

- [ ] **Step 4: Rewrite current docs from current facts**

README contains setup/modes/API/verification only. docs/00 is a short authority map. docs/08 contains current capability/default/limitation/deployment state only. SECURITY documents PostgreSQL State, 30m replay and one sessionStorage ResumeToken.

- [ ] **Step 5: Rebuild docs/11 mechanically before summarizing**

First sort dates ascending and merge duplicate dates without changing text. Then reduce each event to core implementation, direction/current boundary, and 1–3 links. Remove test counts, hashes, commit metadata, Task/FE numbering, and duplicated FE/BE/integration narratives. Create no archive file.

- [ ] **Step 6: Apply targeted Chinese comment governance**

Add or translate comments only at Lifecycle, Planning, Execution, State, Projection, Provider, Retrieval, Bundle and security boundaries. Comments state authority/reason/failure behavior; they do not narrate getters or obvious control flow.

- [ ] **Step 7: Wire the checker into release verification**

Run the checker self-test and current-doc scan before builds in `verify-release.ps1`.

- [ ] **Step 8: Run all documentation and privacy gates**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/documentation-check.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/documentation-check.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path backend/src/main
```

Expected: PASS; current docs contain no obsolete API/config/current-status claims.

- [ ] **Step 9: Commit only if explicitly authorized**

```powershell
git add README.md SECURITY.md AGENTS.md docs scripts/documentation-check.ps1 scripts/documentation-check.test.ps1 scripts/verify-release.ps1 backend/src/main/java
git commit -m "docs: 重建当前事实与项目演进记录"
```

### Task 9: Replacement Slice — Move State and Model Infrastructure out of `answer`

**Files:**
- Move: `answer/context/adapter/postgres/*` → `turn/state/postgres/configuration/*`
- Move: `ConversationalAgentProperties`, `GoalInterpretationProperties`, `ModelExpressionProperties`, `ModelOperationProperties`, `ModelProviderRegistrySnapshot` and their directly required provider descriptors from `answer/adapter/model` → `infrastructure/model/configuration`
- Move: `ConversationProviderAccess`, `ModelPolicy`, `ModelOperation`, `ModelOperationPolicy`, `ModelOperationPolicyRegistry`, `OperationMode` → the owning typed model infrastructure/configuration package
- Modify: `turn/infrastructure/AgentCapabilityConfiguration.java`
- Modify: all tests following moved packages
- Delete: retired source files/configuration in the old paths
- Extend: `backend/src/test/java/com/portfolio/agent/turn/architecture/TurnModuleDependencyTest.java`

**Interfaces:**
- Consumes: stable AgentStateStore, ConversationSessionStore, three typed model ports, StructuredModelTransport.
- Produces: no `turn` dependency on `answer.context` or `answer.adapter.model`; no reverse answer→turn configuration imports.

- [ ] **Step 1: Freeze exact production references**

```powershell
rg -l '^import com\.portfolio\.agent\.answer\.(context|adapter\.model)' backend/src/main/java/com/portfolio/agent/turn
rg -l '^import com\.portfolio\.agent\.turn\.' backend/src/main/java/com/portfolio/agent/answer
```

Save the list in the task notes; every listed reference must be migrated or deleted in this Slice.

- [ ] **Step 2: Add RED dependency tests**

```java
assertThat(sources(Path.of("src/main/java/com/portfolio/agent/turn")))
        .noneMatch(source -> source.contains("com.portfolio.agent.answer.context")
                || source.contains("com.portfolio.agent.answer.adapter.model"));
```

- [ ] **Step 3: Move State configuration without a forwarding class**

Change packages and imports directly. Keep one DataSource/transaction boundary and one AgentStateStore Bean. Delete the old configuration class in the same change.

- [ ] **Step 4: Move shared model infrastructure and keep domain-specific ports**

Provider identity/transport/configuration move to infrastructure. Goal, General and Fact Expression continue consuming only their typed ports. Do not create `turn -> answer` adapters.

- [ ] **Step 5: Run State, model, Spring-context and architecture tests**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=JdbcAgentStateStoreIntegrationTest,AgentStatePayloadCodecTest,ModelProviderRegistrySnapshotTest,TurnModuleDependencyTest test
```

Expected: PASS.

- [ ] **Step 6: Prove old paths are gone**

Run the two `rg` commands from Step 1.

Expected: zero matches for the migrated package families.

- [ ] **Step 7: Commit only if explicitly authorized**

```powershell
git add backend/src/main/java backend/src/test/java
git commit -m "refactor: 迁移 Agent State 与模型基础设施"
```

### Task 10: Replacement Slice — Move Portfolio and Projection Types into `turn`

**Files:**
- Move only the live retrieval leaf types `CorpusBackend` and `SearchStrategy` into `turn/capability/portfolio/retrieval`.
- Replace and delete the old bridge contracts `PortfolioConditions`, `PortfolioRetrievalRequest`, `PortfolioRetrievalResult`, `PortfolioRetrievedPassage`, `PortfolioTaskMode`, `PortfolioRetrievalException`, `PortfolioRetrievalFailureKind`, and `PortfolioRetriever`; do not move them into `turn`.
- Delete `turn/capability/portfolio/retrieval/PortfolioRetrieverAdapterSupport.java` after Bundle/PostgreSQL implement the final `PortfolioRetrieverPort` directly.
- Move PostgreSQL query/row/failure-classification implementation needed by the final Port into `turn/capability/portfolio/retrieval/postgres`; delete the old Bundle/Failover/Postgres Retriever implementations and retired companion contracts after consumers migrate.
- Move `answer/intelligence/adapter/PortfolioExecutionConfiguration.java` to `turn/infrastructure/PortfolioCapabilityConfiguration.java` and delete the old configuration in the same Slice.
- Move the production knowledge/projection types currently imported by `turn`: `AnswerClaimCategory`, `AnswerClaimProjection`, `AnswerKnowledge`, `AnswerQuestion`, `AnswerSectionType`, `AnswerSubjectType`, `PublicSourceReferenceValue`, `RuntimeAnswerContent`, `PortfolioKnowledgeGateway` into their final `turn.capability.portfolio` or `turn.projection` owners.
- Modify the 24 turn files currently importing `answer`.
- Delete retired answer/selection implementations and their duplicate tests.
- Extend: `TurnModuleDependencyTest` and architecture checker fixtures.

**Interfaces:**
- Consumes: PortfolioKnowledgeGateway/public repository boundary and PostgreSQL/Bundle retrieval ports.
- Produces: direct Bundle/PostgreSQL implementations of the final `PortfolioRetrieverPort`, one candidate/result model, and `turn` capability/projection production source with zero references to `com.portfolio.agent.answer.*`.

- [ ] **Step 1: Generate a file-level Replacement Manifest**

Run:

```powershell
rg -n 'com\.portfolio\.agent\.answer\.' backend/src/main/java/com/portfolio/agent/turn
rg -n 'com\.portfolio\.agent\.turn\.' backend/src/main/java/com/portfolio/agent/answer
```

This full-FQCN scan is mandatory because the baseline contains 65 imports plus three fully-qualified references. For each hit, record the target owner, owning test, and old file to delete. Reject the Slice if any entry proposes moving one of the eight bridge contracts or creating a forwarding wrapper.

- [ ] **Step 2: Add the final RED zero-import test**

```java
assertThat(sources(Path.of("src/main/java/com/portfolio/agent/turn")))
        .noneMatch(source -> source.contains("com.portfolio.agent.answer."));
```

- [ ] **Step 3: Move semantic/projection leaf values first**

Move section kind, source reference, support and grounded-statement types to their final owner; update constructors/tests; delete the old types once zero-referenced.

- [ ] **Step 4: Replace the retrieval bridge with direct final-Port adapters**

Make `BundlePortfolioRetrieverAdapter` and `PostgresPortfolioRetrieverAdapter` build `RetrievalAttemptResult` directly from their final query/Bundle primitives. Keep primary→classified fallback→single promotion semantics. Delete `PortfolioRetrieverAdapterSupport` and the eight old request/result/gateway contracts in this same step; do not reintroduce a global intelligence/service layer or a second candidate model.

- [ ] **Step 5: Delete duplicate selection/answer seams**

Delete only after production configuration and Eval consumers use the new typed ports. No deprecated alias, bridge Bean, compatibility constructor, or dual package remains.

- [ ] **Step 6: Run Portfolio, Projection, retrieval and architecture suites**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioTaskExecutorTest,PortfolioEvidenceCapabilityTest,PortfolioPresentationComposerTest,PublicAgentTurnProjectorTest,TurnModuleDependencyTest test
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1 -Path backend/src
```

Expected: PASS and the full-FQCN scan reports zero turn→answer references, including imports and inline qualified names.

- [ ] **Step 7: Commit only if explicitly authorized**

```powershell
git add backend/src/main/java backend/src/test/java scripts/architecture-check.ps1 scripts/architecture-check.test.ps1
git commit -m "refactor: 完成 Portfolio 与公开投影纵向迁移"
```

### Task 11: Replacement Slice — Migrate Eval and Delete the Remaining Legacy `answer` Surface

**Files:**
- Modify/move Eval executors that import old answer types under `backend/src/main/java/com/portfolio/agent/evaluation`.
- Delete unused `answer/service`, `answer/routing`, `answer/runtime`, legacy DTO/config/error types after reference audit.
- Delete obsolete `selection` service/benchmark production dependencies when tooling has moved to final ports.
- Extend architecture and code-quality tests.

**Interfaces:**
- Consumes: final typed Goal/Execution/Portfolio/General/PublicTurn seams.
- Produces: Eval using production seams; zero production `answer ↔ turn` imports; no dead Spring Bean.

- [ ] **Step 1: Classify every remaining answer type by live production consumer**

Run:

```powershell
rg -n '^import com\.portfolio\.agent\.answer\.' backend/src/main/java
rg -n '^import com\.portfolio\.agent\.turn\.' backend/src/main/java/com/portfolio/agent/answer
```

Every remaining type must be moved to a named final owner or deleted. “Keep for compatibility” is not allowed because the product has not been deployed.

- [ ] **Step 2: Add RED bidirectional zero-reference tests**

```java
assertThat(turnSources).noneMatch(source -> source.contains("com.portfolio.agent.answer."));
assertThat(answerSources).noneMatch(source -> source.contains("com.portfolio.agent.turn."));
```

- [ ] **Step 3: Migrate Eval adapters to final typed entry points**

Fake ports may replace external I/O only; they may not recreate old business behavior. Remove P3/P4/P5/legacy suite names and imports when their target capability suite exists.

- [ ] **Step 4: Delete dead Beans and retired packages**

Use Spring-context tests to prove required Beans still resolve. Delete configuration that only creates unused old services. Do not leave empty packages or forwarding classes.

- [ ] **Step 5: Run backend full tests and zero-reference gates**

```powershell
mvn.cmd -f backend/pom.xml test
rg -n '^import com\.portfolio\.agent\.answer\.' backend/src/main/java/com/portfolio/agent/turn
rg -n '^import com\.portfolio\.agent\.turn\.' backend/src/main/java/com/portfolio/agent/answer
```

Expected: Maven PASS and both rg commands produce no matches.

- [ ] **Step 6: Commit only if explicitly authorized**

```powershell
git add backend/pom.xml backend/src/main/java backend/src/test/java
git commit -m "refactor: 删除旧 Answer 生产表面与评测依赖"
```

### Task 12: Run Final Release Evidence and Restore Architecture COMPLETE

**Files:**
- Modify: `docs/agent-architecture-status.json`
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/11-项目演进日志.md`
- Modify: `docs/15-Agent 2.0真实交互问题清单与修复边界.md`
- Create: `docs/reports/agent-stabilization-final-verification-2026-08-19.md`

**Interfaces:**
- Consumes: all prior verified tasks and exact current artifacts.
- Produces: release-verifiable current docs and architecture `COMPLETE` only when every required gate actually passes.

- [ ] **Step 1: Run fresh static and documentation gates**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1 -Path backend/src
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1 -Path backend/src
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/documentation-check.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/documentation-check.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.test.ps1
```

Expected: all exit 0.

- [ ] **Step 2: Run fresh frontend and backend suites**

```powershell
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run check
npm.cmd --prefix frontend run build
mvn.cmd -f backend/pom.xml clean package
```

Expected: all PASS; record exact current counts only in transient evidence, not long-lived README prose.

- [ ] **Step 3: Run PostgreSQL and packaged-JAR gates**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/postgres-local.ps1 verify
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-jar-e2e.ps1 -ContextMode POSTGRESQL
```

Expected: PASS with desktop/mobile and no API mock.

- [ ] **Step 4: Run live-provider gates only with explicit authorization**

Record only operation, requestId equality, public Turn kind/resolution, latency bucket, replay/cancel terminal and pass/fail. Do not record questions, answers, prompts, raw provider payload, Token, handle, or credentials.

- [ ] **Step 5: Reconcile docs/15**

Remove only issues whose targeted, full-suite, packaged/PostgreSQL and original-path gates passed. If any remain, architecture may be structurally complete but release readiness remains false.

- [ ] **Step 6: Set architecture status to COMPLETE only after zero imports and all hard gates**

Required evidence includes:

```text
turn -> answer imports = 0
answer -> turn imports = 0
code quality = PASS
architecture = PASS
documentation = PASS
privacy = PASS
backend/frontend/build = PASS
PostgreSQL = PASS
packaged-JAR browser = PASS
no unresolved required deferred item
```

- [ ] **Step 7: Run the architecture-status checker after the status edit**

Run: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/agent-architecture-status.ps1`

Expected: `overall=COMPLETE deferred.open=0` only when Step 6 is true.

- [ ] **Step 8: Commit only if explicitly authorized**

```powershell
git add docs/agent-architecture-status.json docs/08-当前实现状态.md docs/11-项目演进日志.md docs/15-Agent*
git commit -m "docs: 记录 Agent 稳定化最终验收"
```

---

## Plan Self-Review Matrix

| Spec requirement | Implemented by |
|---|---|
| record/var rules and truthful status | Task 1 |
| 10 RPM / source 2 / global 8 / task 4 | Task 2 |
| 20s Turn, operation caps, 25s client, body stall | Tasks 3 and 7 |
| typed blocked Goal, recommendation/greeting, max two clarifications | Task 4 |
| 5m/30m TTL, encrypted atomic PostgreSQL State, cleanup | Task 5 |
| PostgreSQL-first local, explicit Memory/Disabled, optional provider secret | Task 6 |
| frontend completed behavior and full browser/live gates | Task 7 |
| README/current docs/docs11/comments/documentation gate | Task 8 |
| State/Model/Portfolio/Projection/Eval physical convergence | Tasks 9–11 |
| final evidence and architecture COMPLETE | Task 12 |

No task may be reordered across the behavior-stabilization boundary: Tasks 9–11 start only after Tasks 2–7 pass their required gates.
