# Conversation Guidance and Semantic Title Implementation Plan
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every completed Agent turn return exactly three grounded, stage-aware follow-up questions and upgrade the session title from a deterministic ellipsized question to a validated model summary when available.

**Architecture:** Carry a small non-persistent set of covered topic enums through the v2 request/response contract. A deterministic progress classifier owns `3+0`, `2+1`, `1+2`, and `0+3`; a unified suggestion service runs after every model or fallback result. The frontend keeps progress only in the in-memory session and upgrades a title at most once.

**Tech Stack:** Java 21, Spring Boot, Jackson, JUnit 5, Vue 3, TypeScript, Vitest, Vue Test Utils, Playwright.

## Global Constraints

- Every completed `ANSWERED`, `BOUNDARY`, `REJECTED`, deterministic, model, or fallback result contains exactly three `suggestedQuestions`.
- `OPENING=3+0`, `DEEPENING=2+1`, `WRAP_UP=1+2`, and explicit other-project exploration=`0+3`.
- Progress uses stable enum values only and remains page/request memory; it is never persisted or logged.
- Suggestions must pass the existing public-subject and grounding checks and must exclude the current and last six user questions.
- Provider/model suggestion failure must never remove deterministic suggestions.
- A session title may auto-upgrade once only when `generationMode=MODEL` and the validated response title is 4–24 Unicode code points and non-generic.
- Fallback titles retain the full normalized first question in memory and use visual ellipsis; never `slice(0, 24)`.
- Preserve public-data, privacy, modular-monolith, explicit-Java-type, no-`record`, no-Lombok, and no-new-dependency constraints.
- Preserve user changes. Do not stage or commit without explicit user authorization.

## File Structure

- Create `answer/domain/ConversationTopic.java`: seven stable coverage dimensions.
- Create `answer/domain/ConversationGuidanceStage.java`: four stage values.
- Create `answer/domain/ConversationProgress.java`: immutable covered-topic snapshot and current stage.
- Create `answer/service/ConversationProgressClassifier.java`: deterministic thresholds and explicit exploration markers.
- Modify request/response/result DTOs to carry `coveredTopics`.
- Modify `AnswerQuestion` and the portfolio adapter to retain preferred claim-category metadata needed for deterministic candidates.
- Modify `DynamicQuestionService` to select, validate, deduplicate, distribute, and fill exactly three candidates.
- Modify `ConversationalAgentRuntime` and fallback flow so suggestion generation always runs.
- Modify `PortfolioSnapshotValidator` to reject a bundle with fewer than three deterministic global entries.
- Modify frontend API/session/workspace/thread/rail files for progress, fallback suggestions, and title upgrade.
- Add focused Java/Vitest/Playwright tests and update README/current-status/evolution docs.

---

### Task 1: Add the progress contract to the v2 API

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/ConversationTopic.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/ConversationGuidanceStage.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/ConversationProgress.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/dto/request/ConversationAnswerContextRequest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/ConversationAnswerResult.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/dto/response/ConversationAnswerResponse.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/controller/ConversationAnswerControllerTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/domain/ConversationProgressTest.java`

**Interfaces:**
- Consumes: optional request JSON `context.coveredTopics`.
- Produces: response JSON `coveredTopics` and `guidanceStage`; immutable `ConversationProgress`.

- [ ] **Step 1: Write failing immutable-value and API-contract tests**

Add:

```java
@Test
void defensivelyCopiesCoveredTopics() {
    List<ConversationTopic> topics = new ArrayList<>();
    topics.add(ConversationTopic.BACKGROUND);
    ConversationProgress progress = new ConversationProgress(
            topics, ConversationGuidanceStage.OPENING);
    topics.add(ConversationTopic.SOLUTION);
    assertThat(progress.getCoveredTopics())
            .containsExactly(ConversationTopic.BACKGROUND);
    assertThatThrownBy(() -> progress.getCoveredTopics().add(
            ConversationTopic.SOLUTION))
            .isInstanceOf(UnsupportedOperationException.class);
}
```

Add a controller request containing:

```json
"coveredTopics":["BACKGROUND","SOLUTION"]
```

and assert the response contains:

```json
"coveredTopics":["BACKGROUND","SOLUTION","VERIFICATION"],
"guidanceStage":"DEEPENING"
```

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ConversationProgressTest,ConversationAnswerControllerTest test
```

Expected: compilation failure because the new types and fields do not exist.

- [ ] **Step 3: Add the domain values**

Create:

```java
public enum ConversationTopic {
    BACKGROUND,
    RESPONSIBILITY,
    SOLUTION,
    TRADEOFF,
    FAILURE,
    VERIFICATION,
    OUTCOME
}
```

```java
public enum ConversationGuidanceStage {
    OPENING,
    DEEPENING,
    WRAP_UP,
    EXPLORE_OTHERS
}
```

Create `ConversationProgress` as a final immutable class:

```java
public final class ConversationProgress {
    private final List<ConversationTopic> coveredTopics;
    private final ConversationGuidanceStage stage;

    public ConversationProgress(
            List<ConversationTopic> coveredTopics,
            ConversationGuidanceStage stage
    ) {
        this.coveredTopics = List.copyOf(coveredTopics);
        this.stage = Objects.requireNonNull(stage, "stage");
    }

    public List<ConversationTopic> getCoveredTopics() { return coveredTopics; }
    public ConversationGuidanceStage getStage() { return stage; }
}
```

Implement `equals`, `hashCode`, and `toString` over both fields, matching the
existing immutable domain-value conventions.

- [ ] **Step 4: Extend request, result, and response**

Add to `ConversationAnswerContextRequest`:

```java
@Size(max = 7, message = "coveredTopics must contain at most 7 items")
private final List<ConversationTopic> coveredTopics;
```

Add a `@JsonProperty("coveredTopics") List<ConversationTopic> coveredTopics` constructor parameter, normalize null to `List.of()`, reject duplicates with an `@AssertTrue`, and expose `getCoveredTopics()`.

Keep a four-argument convenience constructor that delegates with
`List.of()` so existing controller and service tests compile while callers
migrate:

```java
public ConversationAnswerContextRequest(
        String projectSlug,
        String caseSlug,
        AudienceRole audienceRole,
        ConversationSource source
) {
    this(projectSlug, caseSlug, audienceRole, source, List.of());
}
```

Add `ConversationProgress progress` to the full `ConversationAnswerResult` constructor and expose `getProgress()`. Map to response fields:

```java
this.coveredTopics = result.getProgress().getCoveredTopics();
this.guidanceStage = result.getProgress().getStage();
```

Every existing convenience constructor in `ConversationAnswerResult` delegates
with `new ConversationProgress(List.of(), ConversationGuidanceStage.OPENING)`
until Task 4 replaces that default through `withGuidance(...)`. This keeps the
intermediate commit compiling and prevents nullable progress.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run the Step 2 command.

Expected: both test classes pass.

- [ ] **Step 6: Commit only after explicit authorization**

Suggested commit:

```powershell
git add -- backend/src/main/java/com/portfolio/agent/answer/domain backend/src/main/java/com/portfolio/agent/answer/dto backend/src/test/java/com/portfolio/agent/answer
git commit -m "功能：增加对话覆盖进度契约"
```

---

### Task 2: Classify deterministic guidance stages

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/service/ConversationProgressClassifier.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/ConversationProgressClassifierTest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/PortfolioKnowledgeFacet.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ConversationalPromptFactory.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/PortfolioGroundingAssembler.java`

**Interfaces:**
- Consumes: prior topics, current question, and an optional
  `ConversationRoute.getFacet()`.
- Produces:
  `ConversationProgress classify(List<ConversationTopic>, String, PortfolioKnowledgeFacet)`
  plus deterministic `inferFacet(String)` for pre-Provider fallback paths.

- [ ] **Step 1: Write the failing stage matrix**

Test:

```java
@ParameterizedTest
@MethodSource("stages")
void selectsStageByCoveredTopicCount(
        List<ConversationTopic> prior,
        PortfolioKnowledgeFacet facet,
        ConversationGuidanceStage expected
) {
    ConversationProgress result = classifier.classify(
            prior, "继续介绍这个项目", facet);
    assertThat(result.getStage()).isEqualTo(expected);
}

static Stream<Arguments> stages() {
    return Stream.of(
            arguments(List.of(), PortfolioKnowledgeFacet.OVERVIEW,
                    ConversationGuidanceStage.OPENING),
            arguments(List.of(BACKGROUND, RESPONSIBILITY), IMPLEMENTATION,
                    ConversationGuidanceStage.DEEPENING),
            arguments(List.of(BACKGROUND, RESPONSIBILITY, SOLUTION, TRADEOFF),
                    VERIFICATION, ConversationGuidanceStage.WRAP_UP));
}
```

Also assert each marker `"推荐其他项目"`, `"还有什么项目"`, and `"换个项目看看"` produces `EXPLORE_OTHERS` without adding an artificial topic.
Add keyword cases proving that `inferFacet` recognizes responsibility,
implementation, tradeoff, failure, verification, and outcome without invoking
the model; unmatched text maps to `OVERVIEW`.

- [ ] **Step 2: Run classifier test and verify RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ConversationProgressClassifierTest test
```

Expected: compilation failure because the classifier does not exist.

- [ ] **Step 3: Extend facets needed by the seven topics**

Add `RESPONSIBILITY` and `OUTCOME` to `PortfolioKnowledgeFacet`. Add both literal values to the model classification prompt. Extend the grounding map:

```java
map.put(PortfolioKnowledgeFacet.RESPONSIBILITY,
        Set.of(AnswerClaimCategory.RESPONSIBILITY));
map.put(PortfolioKnowledgeFacet.OUTCOME,
        Set.of(AnswerClaimCategory.OUTCOME,
                AnswerClaimCategory.LIMITATION));
```

- [ ] **Step 4: Implement deterministic classification**

Create a final service with:

```java
private static final List<String> EXPLORE_MARKERS = List.of(
        "推荐其他项目", "还有什么项目", "换个项目", "别的项目", "其他作品");

public ConversationProgress classify(
        List<ConversationTopic> priorTopics,
        String question,
        PortfolioKnowledgeFacet facet
) {
    LinkedHashSet<ConversationTopic> covered =
            new LinkedHashSet<>(priorTopics);
    if (isExploreOthers(question)) {
        return new ConversationProgress(
                List.copyOf(covered),
                ConversationGuidanceStage.EXPLORE_OTHERS);
    }
    covered.add(toTopic(facet == null ? inferFacet(question) : facet));
    int size = covered.size();
    ConversationGuidanceStage stage = size <= 2
            ? ConversationGuidanceStage.OPENING
            : size <= 4
                    ? ConversationGuidanceStage.DEEPENING
                    : ConversationGuidanceStage.WRAP_UP;
    return new ConversationProgress(List.copyOf(covered), stage);
}
```

`toTopic` maps `OVERVIEW→BACKGROUND`, `RESPONSIBILITY→RESPONSIBILITY`, `IMPLEMENTATION→SOLUTION`, `DECISION→TRADEOFF`, `CHALLENGE/INCIDENT→FAILURE`, `VERIFICATION→VERIFICATION`, and `OUTCOME/LIMITATION/LEARNING→OUTCOME`.
`inferFacet` uses a closed, ordered keyword table such as
`职责/负责/贡献→RESPONSIBILITY`,
`实现/方案/架构→IMPLEMENTATION`,
`取舍/为什么/替代→DECISION`,
`失败/故障/困难/排查→INCIDENT`,
`验证/测试/证据→VERIFICATION`, and
`结果/效果/状态/局限→OUTCOME`; it never calls a Provider.

- [ ] **Step 5: Run classifier and grounding tests**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ConversationProgressClassifierTest,PortfolioGroundingAssemblerTest,ConversationalPromptFactoryTest test
```

Expected: PASS.

- [ ] **Step 6: Commit only after explicit authorization**

Suggested commit:

```powershell
git add -- backend/src/main/java/com/portfolio/agent/answer backend/src/test/java/com/portfolio/agent/answer
git commit -m "功能：按主题覆盖判定引导阶段"
```

---

### Task 3: Preserve question metadata and generate exactly three grounded suggestions

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerQuestion.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/portfolio/LocalPortfolioKnowledgeAdapter.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/DynamicQuestionService.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/DynamicQuestionServiceTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/adapter/portfolio/LocalPortfolioKnowledgeAdapterTest.java`

**Interfaces:**
- Consumes: model candidates, all public question definitions, progress, current route, current question, and six recent user messages.
- Produces: exactly three `ConversationSuggestedQuestion` values with the stage distribution.

- [ ] **Step 1: Write failing distribution and fallback tests**

Add parameterized cases asserting:

```java
assertDistribution(OPENING, 3, 0);
assertDistribution(DEEPENING, 2, 1);
assertDistribution(WRAP_UP, 1, 2);
assertDistribution(EXPLORE_OTHERS, 0, 3);
```

For each stage, make `modelPort.suggest(...)` return a failed result and assert the deterministic pool still returns exactly three. Add filters for the current question, the last six user messages, duplicates, unknown subjects, and `canAnswer=false`.

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=DynamicQuestionServiceTest,LocalPortfolioKnowledgeAdapterTest test
```

Expected: tests fail because current fallback candidates do not distribute, preserve facets, or guarantee three.

- [ ] **Step 3: Preserve preferred categories on `AnswerQuestion`**

Add:

```java
private final List<AnswerClaimCategory> preferredClaimCategories;
```

Update constructors, equality, hash code, and getter. In `LocalPortfolioKnowledgeAdapter.toQuestion`, map:

```java
question.getPreferredClaimCategories().stream()
        .map(category -> AnswerClaimCategory.valueOf(category.name()))
        .toList()
```

Add a helper in `DynamicQuestionService` that maps the first preferred category to the closest `PortfolioKnowledgeFacet`, defaulting to `OVERVIEW`.

- [ ] **Step 4: Replace the generate signature and selection algorithm**

Use:

```java
public List<ConversationSuggestedQuestion> generate(
        RuntimeAnswerContent content,
        ConversationRoute route,
        ConversationWindow window,
        List<ConversationAnswerBlock> acceptedBlocks,
        ConversationProgress progress,
        String currentQuestion
)
```

Build up to three model candidates, then all deterministic candidates. Normalize text with the existing punctuation/whitespace normalization. Reject candidates that are null, outside 5–120 characters, duplicate, current/recent, subject-invalid, current-subject when the slot is cross-project, other-subject when the slot is current-project, or fail `groundingAssembler.canAnswer`.

Target counts:

```java
private int currentSlots(ConversationGuidanceStage stage) {
    return switch (stage) {
        case OPENING -> 3;
        case DEEPENING -> 2;
        case WRAP_UP -> 1;
        case EXPLORE_OTHERS -> 0;
    };
}
```

Fill current slots first from the current subject, then other slots from different subjects, preferring distinct project slugs. If a partition is short, continue through the deterministic global pool without violating “other means not current”. Throw `IllegalStateException("public suggestion pool cannot supply three grounded questions")` only when the validated public snapshot cannot satisfy the invariant.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run the Step 2 command.

Expected: PASS, including failed model suggestion cases.

- [ ] **Step 6: Commit only after explicit authorization**

Suggested commit:

```powershell
git add -- backend/src/main/java/com/portfolio/agent/answer backend/src/test/java/com/portfolio/agent/answer
git commit -m "功能：生成三个分阶段可回答问题"
```

---

### Task 4: Run suggestion generation after every model or fallback result

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/ConversationalAgentRuntime.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/ConversationAnswerResult.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ConversationalAgentConfiguration.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/ConversationalAgentRuntimeTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/PortfolioAgentRuntimeModelPrivacyTest.java`

**Interfaces:**
- Consumes: a base answer from any path.
- Produces: the same answer semantics plus progress and exactly three suggestions.

- [ ] **Step 1: Write failing fallback-path tests**

For provider access denied, provider failure, validation exception, validation rejection, unsafe request, time-sensitive request, greeting, matched preset, and unknown subject, assert:

```java
assertThat(result.getSuggestedQuestions()).hasSize(3);
assertThat(result.getProgress()).isNotNull();
```

Keep existing resolution, degraded, notice, privacy, and diagnostic assertions unchanged.

- [ ] **Step 2: Run runtime tests and verify RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ConversationalAgentRuntimeTest,PortfolioAgentRuntimeModelPrivacyTest test
```

Expected: FAIL because fallback results still contain an empty list.

- [ ] **Step 3: Add a copy method to the immutable result**

Add:

```java
public ConversationAnswerResult withGuidance(
        List<ConversationSuggestedQuestion> questions,
        ConversationProgress newProgress
) {
    return new ConversationAnswerResult(
            turnId, contentVersion, intent, answerScope, resolution, title,
            blocks, questions, degraded, generationMode, answerSource,
            noticeCode, newProgress);
}
```

- [ ] **Step 4: Centralize finalization in the runtime**

Inject `ConversationProgressClassifier`. Before any Provider access, prepare the
conversation window and a fallback-safe route from the validated request
context plus `progressClassifier.inferFacet(request.getQuestion())`; do not call
`ConversationIntentRouter` on this path. When Provider access is allowed,
replace the safe route with the existing routed result. All model/fallback
branches produce a base result and then call:

```java
private ConversationAnswerResult finalizeTurn(
        ConversationAnswerResult base,
        RuntimeAnswerContent content,
        ConversationRoute route,
        ConversationWindow window,
        ConversationAnswerRequest request
) {
    ConversationProgress progress = progressClassifier.classify(
            request.getContext().getCoveredTopics(),
            request.getQuestion(),
            route.getFacet());
    List<ConversationSuggestedQuestion> suggestions = questionService.generate(
            content, route, window, base.getBlocks(), progress,
            request.getQuestion());
    return base.withGuidance(suggestions, progress);
}
```

Construct the fallback-safe route as:

```java
new ConversationRoute(
        ConversationIntent.PORTFOLIO_GROUNDED,
        ConversationAnswerScope.PORTFOLIO,
        1.0d,
        request.getContext().getProjectSlug(),
        request.getContext().getCaseSlug(),
        progressClassifier.inferFacet(request.getQuestion()),
        false)
```

For a subject rejected by `ConversationSubjectGuard`, replace its stage with
`EXPLORE_OTHERS` and clear the untrusted project/case slugs before invoking the
question service. All three entries then come only from the global validated
pool.

- [ ] **Step 5: Wire the classifier bean**

Add:

```java
@Bean
ConversationProgressClassifier conversationProgressClassifier() {
    return new ConversationProgressClassifier();
}
```

and pass it to `ConversationalAgentRuntime`.

- [ ] **Step 6: Run runtime tests and verify GREEN**

Run the Step 2 command.

Expected: PASS with existing privacy assertions intact.

- [ ] **Step 7: Commit only after explicit authorization**

Suggested commit:

```powershell
git add -- backend/src/main/java/com/portfolio/agent/answer backend/src/test/java/com/portfolio/agent/answer
git commit -m "修复：降级回答继续提供引导问题"
```

---

### Task 5: Enforce the global deterministic suggestion floor at content activation

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidator.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidatorTest.java`
- Modify: `scripts/verify-static-bundle.ps1`
- Modify: `scripts/verify-static-bundle.test.ps1`

**Interfaces:**
- Consumes: published question definitions.
- Produces: activation failure unless at least three distinct `deterministicEntry=true` questions target existing public subjects.

- [ ] **Step 1: Add failing validator tests**

Create snapshots with zero, two, and three valid deterministic entries. Assert:

```java
assertThatThrownBy(() -> validator.validate(twoQuestionSnapshot))
        .isInstanceOf(InvalidPortfolioSnapshotException.class)
        .hasMessageContaining("at least 3 deterministic questions");
validator.validate(threeQuestionSnapshot);
```

Mirror the same fixture boundary in `verify-static-bundle.test.ps1`.

- [ ] **Step 2: Run validator tests and verify RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioSnapshotValidatorTest test
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-static-bundle.test.ps1
```

Expected: the two-question fixtures currently pass and the tests fail.

- [ ] **Step 3: Add the activation invariant**

After question subject-reference validation, count distinct deterministic entries whose project/case targets exist:

```java
long deterministicQuestionCount = questions.stream()
        .filter(QuestionDefinition::isDeterministicEntry)
        .map(QuestionDefinition::getId)
        .distinct()
        .count();
require(deterministicQuestionCount >= 3,
        "at least 3 deterministic questions are required");
```

Add the equivalent static-bundle check so CLI validation and runtime loading agree.

- [ ] **Step 4: Run validator tests and current bundle validation**

Run Step 2 plus:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-static-bundle.ps1 `
  -BundleRoot backend/src/main/resources/public-data/bundle
```

Expected: tests pass and the current bundle passes.

- [ ] **Step 5: Commit only after explicit authorization**

Suggested commit:

```powershell
git add -- backend/src/main/java/com/portfolio/agent/portfolio/validation backend/src/test/java/com/portfolio/agent/portfolio/validation scripts/verify-static-bundle.ps1 scripts/verify-static-bundle.test.ps1
git commit -m "内容：保证全局引导问题下限"
```

---

### Task 6: Keep progress in page memory and upgrade the title once

**Files:**
- Modify: `frontend/src/features/agent/model/answerTypes.ts`
- Modify: `frontend/src/features/agent/model/sessionTypes.ts`
- Modify: `frontend/src/features/agent/model/mapAnswerResponse.ts`
- Modify: `frontend/src/features/agent/api/answerApi.ts`
- Modify: `frontend/src/features/agent/composables/useLocalSessions.ts`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.vue`
- Test: `frontend/src/features/agent/api/answerApi.test.ts`
- Test: `frontend/src/features/agent/model/mapAnswerResponse.test.ts`
- Test: `frontend/src/features/agent/composables/useLocalSessions.test.ts`
- Test: `frontend/src/features/agent/components/AgentWorkspace.test.ts`

**Interfaces:**
- Consumes: response `coveredTopics`, `guidanceStage`, title, and generation mode.
- Produces: next request `context.coveredTopics`; one-time semantic title upgrade.

- [ ] **Step 1: Write failing frontend contract tests**

Assert `answerApi` sends:

```ts
context: {
  projectSlug: 'sql-audit',
  caseSlug: null,
  audienceRole: 'INTERVIEWER',
  source: 'AGENT_PAGE',
  coveredTopics: ['BACKGROUND', 'SOLUTION'],
}
```

Assert `mapAnswerResponse` preserves the two fields. In `useLocalSessions.test.ts`, assert:

```ts
session.title === fullLongQuestion
store.upgradeSessionTitle(session.id, '多来源查询的故障隔离', 'MODEL')
session.title === '多来源查询的故障隔离'
store.upgradeSessionTitle(session.id, '第二个标题', 'MODEL')
session.title === '多来源查询的故障隔离'
```

Also assert fallback, generic titles, titles outside 4–24 code points, and manually renamed sessions do not upgrade.

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```powershell
npm.cmd --prefix frontend test -- --run `
  src/features/agent/api/answerApi.test.ts `
  src/features/agent/model/mapAnswerResponse.test.ts `
  src/features/agent/composables/useLocalSessions.test.ts `
  src/features/agent/components/AgentWorkspace.test.ts
```

Expected: FAIL because progress fields and title upgrade do not exist.

- [ ] **Step 3: Extend TypeScript contracts**

Add:

```ts
export type ConversationTopic =
  | 'BACKGROUND' | 'RESPONSIBILITY' | 'SOLUTION' | 'TRADEOFF'
  | 'FAILURE' | 'VERIFICATION' | 'OUTCOME'
export type ConversationGuidanceStage =
  | 'OPENING' | 'DEEPENING' | 'WRAP_UP' | 'EXPLORE_OTHERS'
```

Add `coveredTopics` and `guidanceStage` to `AnswerResponse` and `MappedAnswer`; add `coveredTopics: ConversationTopic[]` to `AgentSession` and initialize it to `[]`.

Extend the existing TypeScript `PortfolioKnowledgeFacet` union with
`'RESPONSIBILITY' | 'OUTCOME'` so frontend decoding remains exhaustive after
the backend enum change in Task 2.

- [ ] **Step 4: Send and update page-memory progress**

Add `coveredTopics?: ConversationTopic[]` to `AnswerApiRequest` and include it under `context`. In `AgentWorkspace.requestAnswer`, pass `session.coveredTopics`. After mapping a successful response:

```ts
sessions.updateCoveredTopics(session.id, mapped.coveredTopics)
sessions.upgradeSessionTitle(
  session.id, mapped.title, mapped.generationMode,
)
```

`updateCoveredTopics` copies and de-duplicates the seven allowed enum values.

- [ ] **Step 5: Replace title slicing with full normalization and one-time upgrade**

In `useLocalSessions`, replace:

```ts
session.title = session.messages[0].content.slice(0, 24)
```

with:

```ts
session.title = session.messages[0].content.trim().replace(/\s+/g, ' ')
```

Track `autoUpgradedSessionIds` alongside `manuallyRenamedSessionIds`. Implement `upgradeSessionTitle(sessionId, title, mode)` using `Array.from(title.trim()).length`, reject non-`MODEL`, length outside 4–24, and generic titles matching `/^(能力边界|无法处理|回答|你好)$/`. Mark the session upgraded only after a valid replacement.

- [ ] **Step 6: Run focused tests and verify GREEN**

Run the Step 2 command.

Expected: PASS.

- [ ] **Step 7: Commit only after explicit authorization**

Suggested commit:

```powershell
git add -- frontend/src/features/agent
git commit -m "功能：同步对话进度并升级会话标题"
```

---

### Task 7: Render accessible title ellipsis and three failure-safe local entries

**Files:**
- Modify: `frontend/src/features/agent/components/ConversationThread.vue`
- Modify: `frontend/src/features/agent/components/LocalSessionRail.vue`
- Modify: `frontend/src/features/agent/components/ConversationThread.test.ts`
- Modify: `frontend/src/features/agent/components/LocalSessionRail.test.ts`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.vue`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.test.ts`
- Create: `frontend/src/features/agent/model/completeSuggestedQuestions.ts`
- Create: `frontend/src/features/agent/model/completeSuggestedQuestions.test.ts`
- Modify: `frontend/src/shared/diagnostics/frontendDiagnosticTypes.ts`
- Modify: `frontend/src/shared/diagnostics/frontendDiagnostics.test.ts`
- Modify: `frontend/e2e/agent-workspace.spec.ts`

**Interfaces:**
- Consumes: server suggestions for completed turns and public starter questions for request failures.
- Produces: exactly three buttons per completed answer; retry plus three clearly local safe entries on transport failure; accessible full titles.

- [ ] **Step 1: Write failing rendering tests**

Assert each completed answer has exactly:

```ts
expect(wrapper.findAll('[data-suggested-follow-up]')).toHaveLength(3)
```

For request failure, assert retry remains and three `[data-local-safe-entry]`
buttons appear. Add a malformed-success fixture with one server suggestion and
assert it is deterministically completed to three from public
`questionPresets`, with no duplicate text or current question. Assert the
recovery diagnostic contains no question text. Assert header and rail title
buttons expose the full string via `title` and `aria-label`.

- [ ] **Step 2: Run component tests and verify RED**

Run:

```powershell
npm.cmd --prefix frontend test -- --run `
  src/features/agent/components/ConversationThread.test.ts `
  src/features/agent/components/LocalSessionRail.test.ts `
  src/features/agent/components/AgentWorkspace.test.ts
```

Expected: FAIL because recovery/failure entries and header
ellipsis/accessibility behavior do not exist.

- [ ] **Step 3: Add deterministic frontend contract recovery**

Create:

```ts
export function completeSuggestedQuestions(
  serverQuestions: ConversationSuggestedQuestion[],
  presets: QuestionPreset[],
  currentQuestion: string,
): { questions: ConversationSuggestedQuestion[]; recovered: boolean }
```

Keep valid server questions in order, normalize whitespace and punctuation for
deduplication, then add `AGENT`-placement public presets in stable bundle order
until there are exactly three. Map each preset to its `projectSlug`, `caseSlug:
null`, and `facet: null`. Exclude the current question and duplicates. Throw
`Error('PUBLIC_SUGGESTION_POOL_TOO_SMALL')` only if the already-validated public
bundle cannot supply three.

Call the helper immediately after `mapAnswerResponse`. When `recovered` is
true, report:

```ts
frontendDiagnostics.report(createFrontendDiagnosticEvent({
  eventName: 'frontend.response.invalid',
  errorCode: 'SUGGESTION_CONTRACT_RECOVERED',
  errorKind: 'INVALID_RESPONSE',
  turnId: mapped.turnId,
}))
```

Add `turnId` to `MappedAnswer` if it is not already preserved. Do not include
question text, suggestion text, URLs, response bodies, or raw counts in the
diagnostic.

- [ ] **Step 4: Add title accessibility and correct flex truncation**

Render:

```vue
<h1 :title="session.title" :aria-label="session.title">{{ session.title }}</h1>
```

Add:

```css
.conversation__head > div:first-child { min-width: 0; }
.conversation__head h1 {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
```

Add `:title="session.title"` and `:aria-label="session.title"` to `.session-select`.

- [ ] **Step 5: Add local safe entries for transport errors**

Compute exactly three local entries in `AgentWorkspace` from the active case/project public questions, then other project questions, de-duplicated and excluding the failed question. Pass them to `ConversationThread` as `failureSuggestions`.

Render under the failure actions with a label that distinguishes them from a server answer:

```vue
<div v-if="failureSuggestions.length" class="failure-suggestions">
  <p>也可以从这些已公开问题继续：</p>
  <button
    v-for="item in failureSuggestions"
    :key="item.text"
    data-local-safe-entry
    type="button"
    @click="submitSuggested(item)"
  >{{ item.text }}</button>
</div>
```

The frontend fallback must not synthesize facts; it may only reuse questions already present in `PublicContent`.

- [ ] **Step 6: Run component tests and verify GREEN**

Run:

```powershell
npm.cmd --prefix frontend test -- --run `
  src/features/agent/components/ConversationThread.test.ts `
  src/features/agent/components/LocalSessionRail.test.ts `
  src/features/agent/components/AgentWorkspace.test.ts `
  src/features/agent/model/completeSuggestedQuestions.test.ts `
  src/shared/diagnostics/frontendDiagnostics.test.ts
```

Expected: PASS.

- [ ] **Step 7: Add browser regression coverage**

In Playwright, submit the long question from the screenshot, mock a fallback response with three questions, and assert:

```ts
await expect(page.locator('.conversation__head h1'))
  .toHaveAttribute('title', longQuestion)
await expect(page.locator('[data-suggested-follow-up]')).toHaveCount(3)
```

Check computed `textOverflow` is `ellipsis`, no horizontal overflow exists, and keyboard activation of a suggested question sends the next request.

- [ ] **Step 8: Run frontend E2E**

Run:

```powershell
npm.cmd --prefix frontend run test:e2e
```

Expected: PASS.

- [ ] **Step 9: Commit only after explicit authorization**

Suggested commit:

```powershell
git add -- frontend/src/features/agent frontend/e2e/agent-workspace.spec.ts
git commit -m "修复：保持降级对话入口与完整标题"
```

---

### Task 8: Update authority docs and run release verification

**Files:**
- Modify: `README.md`
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/11-项目演进日志.md`

**Interfaces:**
- Consumes: completed backend/frontend behavior.
- Produces: authoritative documentation and full verification evidence.

- [ ] **Step 1: Update documentation**

Document:

- every completed v2 turn returns exactly three grounded questions;
- the four stage distributions and page-memory-only covered topics;
- Provider fallback still produces deterministic questions;
- semantic title upgrade and deterministic ellipsis fallback;
- no conversation persistence or expanded public-data boundary.

- [ ] **Step 2: Run focused backend tests**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ConversationProgressTest,ConversationProgressClassifierTest,DynamicQuestionServiceTest,ConversationalAgentRuntimeTest,PortfolioSnapshotValidatorTest test
```

Expected: PASS.

- [ ] **Step 3: Run focused frontend tests**

Run:

```powershell
npm.cmd --prefix frontend test -- --run `
  src/features/agent/api/answerApi.test.ts `
  src/features/agent/model/mapAnswerResponse.test.ts `
  src/features/agent/model/completeSuggestedQuestions.test.ts `
  src/features/agent/composables/useLocalSessions.test.ts `
  src/features/agent/components/ConversationThread.test.ts `
  src/features/agent/components/AgentWorkspace.test.ts `
  src/shared/diagnostics/frontendDiagnostics.test.ts
```

Expected: PASS.

- [ ] **Step 4: Run complete project verification**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/verify-release.ps1 -SkipInstall
```

Expected: all code-quality, architecture, bundle, frontend, backend, privacy, JAR, and packaged-browser gates pass. Normal verification must not call a real Provider.

- [ ] **Step 5: Check diff hygiene**

Run:

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; status contains only intended files plus pre-existing user changes.

- [ ] **Step 6: Commit only after explicit authorization**

Suggested commit:

```powershell
git add -- README.md docs/08-当前实现状态.md docs/11-项目演进日志.md
git commit -m "文档：记录持续对话与标题策略"
```
