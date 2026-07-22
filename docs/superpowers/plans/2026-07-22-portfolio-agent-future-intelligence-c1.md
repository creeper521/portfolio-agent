# Portfolio Agent Future Intelligence C1 Implementation Plan

> **Implementation status (2026-07-22):** Implemented and verified, disabled by default. C2 and the C3 built-in Model Provider Registry were implemented later. The unchecked checklist below is retained as the original implementation record, not current outstanding work.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional, provider-neutral, policy-gated model expression for approved public facts while preserving deterministic answers, strict visitor privacy, the four-dimensional answer contract, and whole-draft fallback.

**Architecture:** The answer core builds one immutable `AnswerPlan` from the already-resolved `QuestionPreset`, `AnswerTurnSnapshot`, projected public Claims, approved Evidence, and a closed audience expression policy. A single configured `ModelExpressionPort` may send only a dedicated whitelist payload to either DeepSeek V4 Flash or GLM-4.7; the core validates the complete draft before it can become a `MODEL` answer, otherwise the same plan is rendered deterministically with `FALLBACK`. Provider configuration, credentials, HTTP DTOs, and JSON parsing stay in `answer.adapter.model`; no Registry, provider failover, retry framework, retrieval, tools, hooks, durable task, or multi-Agent capability is introduced.

**Tech Stack:** Java 21, Spring Boot 3.5.3, Spring `RestClient`, Jackson, JUnit 5, AssertJ, Mockito, MockRestServiceServer, Vue 3/Vitest/Playwright (regression verification only).

**Execution status (2026-07-22):** Completed. The release gate passed with 118 backend tests, 73 frontend tests, packaged-JAR verification, and 16 real-API Playwright scenarios. The C1 fake-provider conformance suite passed 33 tests, and the frontend fake-provider browser acceptance passed 18 desktop/mobile scenarios including explicit `MODEL` and whole-answer `FALLBACK` rendering. No live Provider call was made.

## Global Constraints

- Work only in `D:\code\agent`; preserve unrelated and ignored local files.
- Do not place API keys in source, YAML literals, tests, logs, URLs, commits, or generated artifacts. Runtime keys come only from `DEEPSEEK_API_KEY` and `GLM_API_KEY`.
- `resolution`, `answerSource`, `generationMode`, and `verification` remain orthogonal. Model failure never changes `ANSWERED` to `BOUNDARY`.
- The provider never receives the visitor question, aliases, conversation/history, `turnId`, `requestId`, `handoffId`, IP, User-Agent, Cookie, private content, review notes, or the whole bundle.
- Model output cannot set `resolution`, `answerSource`, `generationMode`, `verification`, Claim governance fields, or Evidence relationships.
- One deployment activates at most one provider. There is one explicit adapter and a closed provider enum, with no registry and no automatic cross-provider resend.
- One non-streaming call, `maxModelAttempts=1`, explicit timeout, no retry, and no raw request/response/prompt logging.
- Model execution is disabled unless `enabled=true`, the selected key exists, and `externalDataPolicyApproved=true`. Defaults remain deterministic.
- Any provider, parsing, schema, reference, label, length, or forbidden-content failure discards the entire draft and renders the same `AnswerPlan` deterministically.
- Do not add C2/C3 retrieval, Embedding, public tools, generic hooks, Registry, Orchestrator, DurableTask, server-side conversation persistence, or model SDK abstractions.
- Do not use Java records, `var`, or Lombok.
- No automatic `git add`, commit, push, or PR during C1 implementation.

## File Structure

### Answer core domain

- Create `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerAchievementStatus.java`: Answer-owned projection of Claim lifecycle.
- Create `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerContributionType.java`: Answer-owned projection of contribution boundary.
- Modify `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerClaimProjection.java`: carry approved statement/detail/lifecycle/contribution in addition to governance and direct Evidence IDs.
- Create `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerPlan.java`: immutable provider-safe plan; deliberately has no visitor/request identity fields.
- Create `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerPlanClaim.java`: whitelist Claim representation.
- Create `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerPlanEvidence.java`: whitelist Evidence representation.
- Create `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerPlanSection.java`: required section and allowed references.
- Create `backend/src/main/java/com/portfolio/agent/answer/domain/ExpressionPolicy.java`: tone, limits, labels, and audience emphasis.
- Create `backend/src/main/java/com/portfolio/agent/answer/domain/ExpressionTone.java`: closed tone enum.
- Create `backend/src/main/java/com/portfolio/agent/answer/domain/ModelAnswerDraft.java`: untrusted model draft.
- Create `backend/src/main/java/com/portfolio/agent/answer/domain/ModelDraftSection.java`: untrusted structured section.
- Create `backend/src/main/java/com/portfolio/agent/answer/domain/ModelExpressionRequest.java`: sanitized port request containing only `AnswerPlan` and schema version.
- Create `backend/src/main/java/com/portfolio/agent/answer/domain/ModelExpressionResult.java`: typed success/failure result without raw provider content.
- Create `backend/src/main/java/com/portfolio/agent/answer/domain/ModelExpressionFailureCode.java`: bounded failure codes.
- Create `backend/src/main/java/com/portfolio/agent/answer/domain/ModelProviderKind.java`: `DEEPSEEK_V4_FLASH | GLM_4_7`.
- Create `backend/src/main/java/com/portfolio/agent/answer/domain/ModelPolicy.java`: immutable independently versioned activation policy.
- Create `backend/src/main/java/com/portfolio/agent/answer/domain/ExecutionBudgets.java`: C1 deadline and one-attempt budget.
- Create `backend/src/main/java/com/portfolio/agent/answer/domain/AgentExecutionSnapshot.java`: combines the existing `AnswerTurnSnapshot` with C1 policy/budget versions without copying content.

### Answer core application and port

- Create `backend/src/main/java/com/portfolio/agent/answer/gateway/ModelExpressionPort.java`: `express(ModelExpressionRequest)`.
- Create `backend/src/main/java/com/portfolio/agent/answer/service/AnswerPlanBuilder.java`: maps resolved public context to the immutable whitelist plan.
- Create `backend/src/main/java/com/portfolio/agent/answer/service/AnswerOutputValidator.java`: validates structure, references, governance labels, lengths, numeric facts, and forbidden content.
- Create `backend/src/main/java/com/portfolio/agent/answer/service/AnswerValidationResult.java`: typed accepted/rejected validation result.
- Create `backend/src/main/java/com/portfolio/agent/answer/service/AgentExecutionSnapshotFactory.java`: fixes the C1 policy and budgets once per turn.
- Create `backend/src/main/java/com/portfolio/agent/answer/service/ModelAnswerCoordinator.java`: selects deterministic/model/fallback result without changing verification.
- Create `backend/src/main/java/com/portfolio/agent/answer/service/ModelAnswerOutcome.java`: selected `GeneratedAnswer`, `GenerationMode`, and standardized failure code.
- Modify `backend/src/main/java/com/portfolio/agent/answer/service/PortfolioAgentRuntime.java`: build the plan once for `ANSWERED`, create the execution snapshot once, invoke the coordinator, then apply `VerificationPolicy` to the selected answer.
- Modify `backend/src/main/java/com/portfolio/agent/answer/engine/AnswerEngine.java`: consume `AnswerPlan` so deterministic fallback uses exactly the provider plan.
- Modify `backend/src/main/java/com/portfolio/agent/answer/engine/deterministic/DeterministicAnswerEngine.java`: render the immutable plan and retain structured Claim/Evidence references.

### Portfolio projection boundary

- Modify `backend/src/main/java/com/portfolio/agent/answer/adapter/portfolio/LocalPortfolioKnowledgeAdapter.java`: project `statement`, `detail`, `achievementStatus`, and `contributionType` into Answer-owned types; keep Portfolio imports confined to this adapter.
- Modify associated adapter/domain fixture tests for the richer projection.

### Provider adapter and configuration

- Create `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ModelExpressionProperties.java`: environment-backed mutable Spring configuration with no `toString`.
- Create `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ModelExpressionConfiguration.java`: builds one `ModelPolicy`, one fixed-endpoint `RestClient`, and one `ModelExpressionPort` for the selected provider.
- Create `backend/src/main/java/com/portfolio/agent/answer/adapter/model/OpenAiCompatibleModelExpressionAdapter.java`: one-call DeepSeek/GLM HTTP adapter, strict draft parsing, no raw logging.
- Create `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ModelPromptFactory.java`: serializes the dedicated whitelist payload and fixed JSON schema instructions.
- Create `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ProviderAnswerPlanPayload.java`: explicit outbound DTO that cannot serialize runtime identity.
- Modify `backend/src/main/resources/application.yml`: add disabled-by-default model properties and environment-variable references only.

### Tests and documentation

- Create focused domain/service tests under `backend/src/test/java/com/portfolio/agent/answer/domain` and `.../service` for plan privacy, validation, generation modes, snapshot composition, and fallback.
- Create adapter tests under `backend/src/test/java/com/portfolio/agent/answer/adapter/model` for both endpoints/models, authorization header, JSON mode, thinking disabled, no retry, strict parsing, and outbound privacy.
- Modify `backend/src/test/java/com/portfolio/agent/answer/service/PortfolioAgentRuntimeTest.java` and deterministic engine tests for the new pipeline.
- Modify `scripts/privacy-check.ps1` and `scripts/privacy-check.test.ps1` so provider configuration, prompts, draft DTOs, and packaged artifacts are scanned before release completion.
- Modify `README.md`, `SECURITY.md`, `docs/00-文档状态索引.md`, and the C design implementation-status section with exact C1 enablement and rollback instructions. C2/C3 remain pending.

---

### Task 1: Provider-safe AnswerPlan contract

**Files:**
- Create/modify the Answer core domain files listed above.
- Modify `LocalPortfolioKnowledgeAdapter.java`.
- Test `AnswerPlanBuilderTest.java`, `AnswerModelContractTest.java`, and `LocalPortfolioKnowledgeAdapterTest.java`.

**Interfaces:**
- Consumes: `AnswerTurnSnapshot`, `ResolvedAnswerContext`, `AnswerQuestion`, `AnswerClaimProjection`, `AnswerEvidence`.
- Produces: `AnswerPlan AnswerPlanBuilder.build(AnswerTurnSnapshot turn, ResolvedAnswerContext context)`.

- [ ] **Step 1: Write failing privacy and projection tests**

```java
assertThat(AnswerPlan.class.getDeclaredFields())
        .extracting(Field::getName)
        .doesNotContain("question", "aliases", "turnId", "requestId", "handoffId");
assertThat(plan.getCanonicalIntent()).isEqualTo("Describe the approved project");
assertThat(plan.getClaims()).extracting(AnswerPlanClaim::getStatement)
        .contains("The approved release is deployed.");
```

- [ ] **Step 2: Run the focused tests and confirm RED**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=AnswerPlanBuilderTest,AnswerModelContractTest,LocalPortfolioKnowledgeAdapterTest test
```

Expected: compilation failures because the plan types and richer projection do not exist.

- [ ] **Step 3: Implement the minimal immutable contract and builder**

```java
public final class AnswerPlan {
    private final String contentVersion;
    private final String questionPresetId;
    private final String canonicalIntent;
    private final AudienceRole audienceRole;
    private final List<AnswerPlanSection> requiredSections;
    private final List<AnswerPlanClaim> claims;
    private final List<AnswerPlanEvidence> evidence;
    private final ExpressionPolicy expressionPolicy;
}
```

The builder maps the fixed five current public answer sections and only the Claim/Evidence projections already authorized in the current `ResolvedAnswerContext`. It never receives `AnswerRequest`.

- [ ] **Step 4: Run the focused tests and confirm GREEN**

Run the Task 1 Maven command again. Expected: all selected tests pass.

### Task 2: Deterministic renderer and complete draft validator

**Files:**
- Modify `AnswerEngine.java` and `DeterministicAnswerEngine.java`.
- Create model draft and validator classes.
- Test `DeterministicAnswerEngineTest.java` and `AnswerOutputValidatorTest.java`.

**Interfaces:**
- Consumes: `AnswerPlan` and untrusted `ModelAnswerDraft`.
- Produces: `GeneratedAnswer AnswerEngine.answer(AnswerPlan plan)` and `AnswerValidationResult validate(AnswerPlan plan, ModelAnswerDraft draft)`.

- [ ] **Step 1: Write failing validator matrix tests**

```java
assertThat(validator.validate(plan, validDraft).isAccepted()).isTrue();
assertThat(validator.validate(plan, draftWithUnknownClaim).isAccepted()).isFalse();
assertThat(validator.validate(plan, draftMissingRequiredSection).isAccepted()).isFalse();
assertThat(validator.validate(plan, draftWithUnlinkedEvidence).isAccepted()).isFalse();
assertThat(validator.validate(plan, draftWithInventedNumber).isAccepted()).isFalse();
assertThat(validator.validate(plan, draftWithExternalUrl).isAccepted()).isFalse();
```

- [ ] **Step 2: Run focused tests and confirm RED**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=DeterministicAnswerEngineTest,AnswerOutputValidatorTest test
```

- [ ] **Step 3: Implement exact-section/reference validation and same-plan rendering**

```java
AnswerValidationResult validation = validator.validate(plan, draft);
if (!validation.isAccepted()) {
    return AnswerValidationResult.rejected(validation.getFailureCode());
}
return AnswerValidationResult.accepted(new GeneratedAnswer(
        draft.getTitle(), draft.getSummary(), validatedSections));
```

Reject duplicate/extra/missing sections, plan-external Claim/Evidence IDs, broken Claim-Evidence links, missing KEY Claims, required labels, over-limit fields, URLs/HTML/Markdown/tool-call markers/control characters, and numeric tokens absent from allowed plan text.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run the Task 2 command again. Expected: all selected tests pass.

### Task 3: Model policy and AgentExecutionSnapshot

**Files:**
- Create `ModelPolicy`, `ExecutionBudgets`, `AgentExecutionSnapshot`, and factory.
- Test `ModelPolicyTest.java` and `AgentExecutionSnapshotFactoryTest.java`.

**Interfaces:**
- Consumes: selected provider configuration and `AnswerTurnSnapshot`.
- Produces: immutable `ModelPolicy` and one `AgentExecutionSnapshot` per turn.

- [ ] **Step 1: Write failing activation/snapshot tests**

```java
assertThat(policyWithoutKey.isModelEnabled()).isFalse();
assertThat(policyWithoutDataApproval.isModelEnabled()).isFalse();
assertThat(enabledPolicy.getMaxModelAttempts()).isEqualTo(1);
assertThat(snapshot.getTurnSnapshot()).isSameAs(turnSnapshot);
assertThat(AgentExecutionSnapshot.class.getDeclaredFields())
        .extracting(Field::getName).doesNotContain("question", "conversation", "prompt");
```

- [ ] **Step 2: Run focused tests and confirm RED**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=ModelPolicyTest,AgentExecutionSnapshotFactoryTest test
```

- [ ] **Step 3: Implement the independent policy gate**

```java
boolean active = configuredEnabled
        && externalDataPolicyApproved
        && selectedApiKeyPresent
        && maxModelAttempts == 1;
```

The execution snapshot references the existing turn snapshot and carries only C1 policy/schema/audience versions and bounded C1 budgets.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run the Task 3 command again. Expected: all selected tests pass.

### Task 4: DeepSeek/GLM one-provider HTTP adapter

**Files:**
- Create all `answer/adapter/model` files.
- Modify `application.yml`.
- Test `OpenAiCompatibleModelExpressionAdapterTest.java`, `ModelPromptFactoryTest.java`, and `ModelExpressionConfigurationTest.java`.

**Interfaces:**
- Consumes: `ModelExpressionRequest` and one selected `ModelProviderKind`.
- Produces: `ModelExpressionResult ModelExpressionPort.express(ModelExpressionRequest request)`.

- [ ] **Step 1: Write failing outbound contract tests for both providers**

```java
server.expect(requestTo("https://api.deepseek.com/chat/completions"))
        .andExpect(header("Authorization", "Bearer test-key"))
        .andExpect(content().string(not(containsString("private visitor wording"))));
assertThat(capturedBody).contains("deepseek-v4-flash", "json_object", "disabled");
assertThat(glmCapturedBody).contains("glm-4.7", "json_object", "disabled");
```

Also assert one HTTP exchange on provider failure, strict rejection of unknown draft fields, empty content, truncated/invalid JSON, and no raw body in result/errors.

- [ ] **Step 2: Run adapter tests and confirm RED**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=OpenAiCompatibleModelExpressionAdapterTest,ModelPromptFactoryTest,ModelExpressionConfigurationTest test
```

- [ ] **Step 3: Implement one fixed adapter without SDK/Registry/retry**

```java
ModelProviderKind.DEEPSEEK_V4_FLASH -> endpoint https://api.deepseek.com/chat/completions, model deepseek-v4-flash
ModelProviderKind.GLM_4_7 -> endpoint https://open.bigmodel.cn/api/paas/v4/chat/completions, model glm-4.7
```

Use `stream=false`, `response_format.type=json_object`, `thinking.type=disabled`, a bounded token limit, and a request factory with explicit connect/read timeout. The prompt contains fixed policy/schema text plus `ProviderAnswerPlanPayload`; it contains no runtime request object.

- [ ] **Step 4: Run adapter tests and confirm GREEN**

Run the Task 4 command again. Expected: all selected tests pass with exactly one request per attempt.

### Task 5: Runtime integration and generation-mode semantics

**Files:**
- Create coordinator/outcome classes.
- Modify `PortfolioAgentRuntime.java` and its tests.
- Modify constructor fixtures affected by the new explicit dependencies.

**Interfaces:**
- Consumes: plan, execution snapshot, deterministic engine, model port, validator.
- Produces: one selected `GeneratedAnswer` plus exact `DETERMINISTIC | MODEL | FALLBACK`.

- [ ] **Step 1: Write failing orchestration tests**

```java
assertThat(disabledResult.getGenerationMode()).isEqualTo(GenerationMode.DETERMINISTIC);
verifyNoInteractions(modelPort);
assertThat(validModelResult.getGenerationMode()).isEqualTo(GenerationMode.MODEL);
assertThat(invalidDraftResult.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
assertThat(providerFailureResult.getResolution()).isEqualTo(AnswerResolution.ANSWERED);
assertThat(providerFailureResult.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
verify(verificationPolicy).verify(AnswerResolution.ANSWERED, context, selectedAnswer);
```

Capture the port request from a request containing a unique visitor sentinel and prove the sentinel is absent from every outbound-plan string field.

- [ ] **Step 2: Run runtime tests and confirm RED**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=ModelAnswerCoordinatorTest,PortfolioAgentRuntimeTest test
```

- [ ] **Step 3: Implement the fixed C1 pipeline**

```text
RESOLVE -> PLAN -> optional EXPRESS -> VALIDATE -> VERIFY -> FINALIZE -> OBSERVE
```

Boundary/rejected turns and disabled/ineligible policies do not call the port. Failed attempts use the already-created plan; no partial draft survives. Verification is computed only after selection by the existing core policy.

- [ ] **Step 4: Run runtime tests and confirm GREEN**

Run the Task 5 command again. Expected: all selected tests pass.

### Task 6: Privacy gates, configuration documentation, and status

**Files:**
- Modify privacy scripts, `README.md`, `SECURITY.md`, `docs/00-文档状态索引.md`, and the C design status section.
- Test `scripts/privacy-check.test.ps1` plus backend configuration tests.

**Interfaces:**
- Consumes: final source/config/build/JAR artifacts.
- Produces: release-blocking detection and operator instructions with placeholders only.

- [ ] **Step 1: Add failing privacy fixtures**

```powershell
'provider-key-literal' = 'DEEPSEEK_API_KEY=literal-secret-value'
'raw-model-prompt-log' = 'logger.info("prompt={}", prompt)'
'visitor-question-provider-field' = 'providerRequest.question = request.getQuestion()'
```

- [ ] **Step 2: Run privacy tests and confirm RED**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.test.ps1
```

- [ ] **Step 3: Expand scanning and document safe enablement**

```yaml
portfolio:
  model-expression:
    enabled: ${PORTFOLIO_MODEL_ENABLED:false}
    provider: ${PORTFOLIO_MODEL_PROVIDER:DEEPSEEK_V4_FLASH}
    external-data-policy-approved: ${PORTFOLIO_MODEL_DATA_POLICY_APPROVED:false}
    deepseek-api-key: ${DEEPSEEK_API_KEY:}
    glm-api-key: ${GLM_API_KEY:}
```

Document separate DeepSeek and GLM environment examples without values, default-disabled behavior, one-provider selection, timeout/no-retry behavior, and rollback by setting `PORTFOLIO_MODEL_ENABLED=false`. Mark only C1 implemented; C2/C3 remain pending.

- [ ] **Step 4: Run privacy tests and confirm GREEN**

Run the Task 6 command again. Expected: privacy fixture tests and final source scan pass.

### Task 7: Full verification and browser acceptance

**Files:**
- No new implementation files unless a failing verification exposes a C1 regression.

- [ ] **Step 1: Run the complete backend and release gates**

```powershell
$env:Path = 'C:\tools\apache-maven-3.9.9\bin;' + $env:Path
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-release.ps1
```

Expected: code quality, architecture, privacy, governance, frontend check/lint/Vitest/build, Maven 96+ tests, package, static bundle verification, packaged-JAR verification, and Playwright 16+ tests all pass.

- [ ] **Step 2: Run C1 conformance suite with fake providers**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest='*Model*Test,*AnswerPlan*Test,*AnswerOutputValidatorTest,*PortfolioAgentRuntimeTest' test
```

Expected: all active preset fixtures, both provider wire formats, invalid JSON, unknown fields, invented IDs/numbers, forbidden content, timeout, no retry, and fallback pass without external network calls.

- [ ] **Step 3: Browser acceptance against packaged JAR with model disabled**

```text
Verify existing homepage handoff, page-memory privacy, URL/storage absence, ANSWERED/BOUNDARY/REJECTED, PRESET, DETERMINISTIC, verification, Evidence/Timeline filtering, keyboard/focus/reduced-motion/drawers.
```

- [ ] **Step 4: Browser/API acceptance with stubbed valid and invalid model responses**

```text
Valid draft -> ANSWERED + PRESET + MODEL + core-computed verification.
Invalid/provider-failed draft -> ANSWERED + PRESET + FALLBACK + deterministic sections.
Disabled/unconfigured/not-approved -> ANSWERED + PRESET + DETERMINISTIC and zero provider calls.
```

- [ ] **Step 5: Final repository audit**

```powershell
git status --short --branch
git diff --check
```

Expected: only intentional C1 changes are present; no secrets, build outputs, staging, commits, pushes, or PRs exist.

## Compatibility Strategy

- Keep the existing Answer API response shape and current public section enum so the frontend requires no model-specific branch. `MODEL` and `FALLBACK` already exist in the four-dimensional contract.
- Default-disabled policy preserves byte-for-byte deterministic behavior for normal runtime operation except where plan refactoring is proven equivalent by tests.
- Environment selection is closed and restart-bound. Changing provider or policy version affects only new turns.
- No provider conversation/thread IDs are created, and no server session state is added.
- `AnswerClaimProjection` is enriched only inside the Answer-owned projection; Portfolio remains authoritative and imports remain adapter-confined.

## Browser Acceptance

- Existing A browser scenarios remain mandatory under the default-disabled model policy.
- C1 adds API/packaged-JAR checks for exact generation-mode transitions and verifies model responses never affect resolution or verification authority.
- Live calls using real keys are not part of automated acceptance. Provider wire compatibility is verified with local fake responses; any later live smoke test requires freshly rotated keys supplied through process environment and explicit operator approval.

## Risks and Rollback Boundary

- **Provider retention ambiguity:** fail closed unless the independent data-policy approval flag is true. Roll back by disabling `PORTFOLIO_MODEL_ENABLED` without changing the content bundle.
- **Provider schema drift:** strict response parsing converts drift into `FALLBACK`; no partial content is shown.
- **Semantic invention not fully machine-decidable:** constrain outbound facts, references, numeric tokens, prohibited markup/actions, required labels, and run a blocker conformance suite. Keep MODEL disabled if expression quality is not approved.
- **Latency/availability:** one bounded call with no retry; failure uses deterministic rendering from the same plan.
- **Provider selection:** a closed two-value switch is intentional and not a Registry. Adding dynamic discovery or failover is outside C1.
- **Core regression:** default-disabled release verification must remain green. Reverting C1 application/config/adapter files restores the A/B deterministic baseline without reverting content or governance data.
- **Secrets:** `.gitignore` and privacy checks are defense in depth; credentials remain environment-only and are never used in automated tests.

## Self-Review

- Spec coverage: C1 items 1-6 are mapped to Tasks 1-7; C2/C3 are explicitly excluded.
- Placeholder scan: every implementation and test step is concrete and executable.
- Type consistency: `AnswerPlanBuilder -> AnswerPlan -> ModelExpressionRequest -> ModelExpressionResult -> AnswerOutputValidator -> ModelAnswerOutcome -> VerificationPolicy` signatures are consistent across tasks.
- Snapshot naming: only `RuntimeContentSnapshot`, `AnswerTurnSnapshot`, `GovernanceRunSnapshot`, and new `AgentExecutionSnapshot` retain their specified meanings.
