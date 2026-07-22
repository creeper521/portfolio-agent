# Portfolio Public Content API Integration Implementation Plan

> **执行状态（2026-07-20）：** 已完成并通过当时的完整发布验证，对应提交 `574d6f0` 至 `d696511`。检查项同步为已执行。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Replace the rebuilt frontend's preview runtime with one validated Spring Boot public-content aggregate and the existing deterministic Answer API, then prove the integration through the packaged JAR.

**Architecture:** Extend the reviewed `public-portfolio.v1.json` snapshot with stable display codes and timeline entries, validate every cross-reference at startup, and expose the result through additive `GET /api/v1/public-content`. The Vue application keeps its existing `PublicContentRepository` boundary, but the production implementation becomes an API-backed cached repository with shared loading state; preview content remains an explicitly injected test fixture. Existing API URLs and the single-JAR delivery model remain intact.

**Tech Stack:** Java 21, Spring Boot 3.5.3, Maven 3.9+, Jackson, JUnit 5, MockMvc, Vue 3.5, TypeScript 5.8, Vite 7, Vitest 3, Vue Test Utils, Playwright 1.53, PowerShell

## Global Constraints

- Runtime code reads only `backend/src/main/resources/public-data/`; it must never read the private Obsidian vault, candidate snapshots, raw daily reports, credentials, or unreviewed screenshots.
- V0 does not add DeepSeek, Spring AI, RAG, SSE, a database, authentication, server-side visitor sessions, or visitor-question logging.
- Production and test Java must use explicit types; `var`, `record`, and Lombok are prohibited.
- Java value objects use explicit immutable classes, defensive collection copies, getters, and value semantics.
- Existing `GET /api/v1/portfolio`, `GET /api/v1/projects/{slug}`, and `POST /api/v1/answers` URLs and meanings remain compatible.
- Only Evidence with `publicStatus = APPROVED` and `rawContentPublic = false` may appear in public API responses.
- The existing routes, visual system, local-session key `portfolio.agent.sessions.v1`, seven-day expiry, and workspace split behavior remain unchanged.
- New features and bug fixes use TDD: RED, GREEN, REFACTOR.
- Preserve all user-owned untracked files and unrelated changes. Do not reset, restore, stage, commit, or push without explicit user authorization.
- Every conditional commit step below is executed only after explicit authorization; otherwise record the suggested commit and continue without changing Git state.

---

## File and Responsibility Map

### Backend

- `portfolio/domain/TimelineEvent.java`: immutable reviewed timeline fact and JSON shape.
- `portfolio/domain/ProjectProfile.java`: add the stable public project `code`.
- `portfolio/domain/EvidenceRecord.java`: add the stable public evidence `code`.
- `portfolio/domain/PortfolioSnapshot.java`: own the reviewed `timeline` collection.
- `portfolio/validation/PortfolioSnapshotValidator.java`: validate codes, timeline contents, and cross-references.
- `portfolio/service/result/PublicContent.java`: one application result for the aggregate use case.
- `portfolio/service/PortfolioService.java`: assemble complete projects, approved Evidence, reverse project links, and timeline from one snapshot read.
- `portfolio/dto/response/PublicContentResponse.java`: aggregate HTTP response.
- `portfolio/dto/response/TimelineEventResponse.java`: timeline HTTP response.
- `portfolio/dto/response/ProjectDetailResponse.java`: add `code` and `evidenceIds` so the type can represent aggregate projects without a second project DTO.
- `portfolio/dto/response/EvidenceResponse.java`: add `code` and `projectSlugs` for the evidence center.
- `portfolio/mapper/PortfolioResponseMapper.java`: map application results to both existing and aggregate DTOs.
- `portfolio/controller/PublicContentController.java`: expose `GET /api/v1/public-content`.

### Frontend

- `features/public-content/repository/apiPublicContentRepository.ts`: API-backed cached repository.
- `features/public-content/composables/usePublicContent.ts`: framework-light shared loading/error/retry state with an injection seam for tests.
- `shared/components/PublicContentFeedback.vue`: consistent loading and retry UI.
- `features/agent/model/mapAnswerResponse.ts`: turn structured Answer API responses into stored/displayed Agent messages.
- Existing pages: consume shared state instead of calling the preview repository directly.
- `AudienceDialogue.vue`, `AgentPage.vue`, `AgentWorkspace.vue`, and `ConversationThread.vue`: async Answer API interaction and retry state.
- `scripts/run-jar-e2e.ps1`: start the packaged JAR, wait for HTTP readiness, run Playwright, and stop the process.

---

### Task 1: Extend the reviewed snapshot model with public codes and timeline facts

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/domain/TimelineEvent.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/domain/ProjectProfile.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/domain/EvidenceRecord.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/domain/PortfolioSnapshot.java`
- Modify: `backend/src/main/resources/public-data/public-portfolio.v1.json`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/domain/PortfolioModelContractTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/repository/file/JsonPublicPortfolioRepositoryTest.java`
- Test fixture: `backend/src/test/java/com/portfolio/agent/portfolio/service/PortfolioServiceTest.java`
- Test fixture: `backend/src/test/java/com/portfolio/agent/answer/adapter/portfolio/LocalPortfolioKnowledgeAdapterTest.java`

**Interfaces:**
- Produces: `ProjectProfile#getCode(): String`
- Produces: `EvidenceRecord#getCode(): String`
- Produces: `PortfolioSnapshot#getTimeline(): List<TimelineEvent>`
- Produces: `TimelineEvent(String id, String dateLabel, String title, String problem, String action, String impact, List<String> projectSlugs, List<String> evidenceIds)` with getters and value semantics.

- [x] **Step 1: Add failing domain contract tests**

Add these tests and update existing constructor calls with `"P-01"`, `"E-01"`, and `List.of(timeline)`:

```java
@Test
void timelineEventDefensivelyCopiesReferencesAndKeepsValueSemantics() {
    List<String> projectSlugs = new ArrayList<>(List.of("sql-audit"));
    List<String> evidenceIds = new ArrayList<>(List.of("evidence-1"));
    TimelineEvent first = new TimelineEvent(
            "timeline-1", "2026.06–07", "交付闭环", "路径硬编码",
            "完成多目标路由", "形成可交付版本", projectSlugs, evidenceIds
    );
    TimelineEvent second = new TimelineEvent(
            "timeline-1", "2026.06–07", "交付闭环", "路径硬编码",
            "完成多目标路由", "形成可交付版本",
            List.of("sql-audit"), List.of("evidence-1")
    );

    projectSlugs.add("private-project");
    evidenceIds.add("private-evidence");

    assertThat(first.getProjectSlugs()).containsExactly("sql-audit");
    assertThat(first.getEvidenceIds()).containsExactly("evidence-1");
    assertThatThrownBy(() -> first.getProjectSlugs().add("blocked"))
            .isInstanceOf(UnsupportedOperationException.class);
    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
    assertThat(first.toString()).contains("timeline-1", "交付闭环");
}

@Test
void snapshotDefensivelyCopiesTimeline() {
    List<TimelineEvent> timeline = new ArrayList<>(List.of(new TimelineEvent(
            "timeline-1", "2026.06–07", "交付闭环", "问题", "行动", "影响",
            List.of("sql-audit"), List.of("evidence-1")
    )));
    PortfolioSnapshot snapshot = new PortfolioSnapshot(
            "1.0", "version-1", OffsetDateTime.parse("2026-07-17T00:00:00+08:00"),
            null, List.of(), List.of(), List.of(), timeline
    );

    timeline.clear();

    assertThat(snapshot.getTimeline()).hasSize(1);
    assertThatThrownBy(() -> snapshot.getTimeline().clear())
            .isInstanceOf(UnsupportedOperationException.class);
}
```

- [x] **Step 2: Run the domain test and verify RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioModelContractTest test
```

Expected: compilation fails because `TimelineEvent`, the `code` constructor parameters, and `PortfolioSnapshot#getTimeline()` do not exist.

- [x] **Step 3: Implement the immutable model additions**

Create `TimelineEvent.java` with the exact constructor shown in Interfaces, Jackson `@JsonCreator`/`@JsonProperty` annotations, `List.copyOf` for both reference lists, getters, and explicit `equals`, `hashCode`, and `toString`.

Update constructor signatures exactly:

```java
public ProjectProfile(
        @JsonProperty("id") String id,
        @JsonProperty("code") String code,
        @JsonProperty("slug") String slug,
        @JsonProperty("title") String title,
        @JsonProperty("summary") String summary,
        @JsonProperty("background") String background,
        @JsonProperty("responsibilities") List<String> responsibilities,
        @JsonProperty("solution") String solution,
        @JsonProperty("keyDecisions") List<String> keyDecisions,
        @JsonProperty("technologies") List<String> technologies,
        @JsonProperty("verification") List<String> verification,
        @JsonProperty("outcome") String outcome,
        @JsonProperty("handoff") String handoff,
        @JsonProperty("status") ProjectStatus status,
        @JsonProperty("contributionType") ContributionType contributionType,
        @JsonProperty("questionIds") List<String> questionIds,
        @JsonProperty("evidenceIds") List<String> evidenceIds
)

public EvidenceRecord(
        @JsonProperty("id") String id,
        @JsonProperty("code") String code,
        @JsonProperty("title") String title,
        @JsonProperty("type") EvidenceType type,
        @JsonProperty("periodStart") LocalDate periodStart,
        @JsonProperty("periodEnd") LocalDate periodEnd,
        @JsonProperty("sourceCount") int sourceCount,
        @JsonProperty("summary") String summary,
        @JsonProperty("supportedClaims") List<String> supportedClaims,
        @JsonProperty("publicStatus") EvidenceStatus publicStatus,
        @JsonProperty("rawContentPublic") Boolean rawContentPublic
)

public PortfolioSnapshot(
        @JsonProperty("schemaVersion") String schemaVersion,
        @JsonProperty("contentVersion") String contentVersion,
        @JsonProperty("publishedAt") OffsetDateTime publishedAt,
        @JsonProperty("owner") OwnerProfile owner,
        @JsonProperty("projects") List<ProjectProfile> projects,
        @JsonProperty("questions") List<QuestionDefinition> questions,
        @JsonProperty("evidence") List<EvidenceRecord> evidence,
        @JsonProperty("timeline") List<TimelineEvent> timeline
)
```

Include the new fields in getters, equality, hash code, and string output.

Update every constructor call reported by this command before running Maven:

```powershell
rg -n "new (ProjectProfile|EvidenceRecord|PortfolioSnapshot)\(" backend/src
```

Use stable fixture codes (`P-01`, `E-01`) and pass an explicit timeline list. `PortfolioServiceTest` uses one `TimelineEvent`; `LocalPortfolioKnowledgeAdapterTest` may use `List.of()` because that test does not exercise timeline behavior.

- [x] **Step 4: Add reviewed JSON fields and repository assertions**

Add `"code": "P-01"` to the existing project, `"code": "E-01"` to the existing Evidence, and this reviewed top-level timeline entry:

```json
"timeline": [
  {
    "id": "timeline-sql-audit-delivery",
    "dateLabel": "2026.06–07",
    "title": "从固定路径查询到可交付工具",
    "problem": "查询目标和目录被写死，任务过程、结果复用和交付说明缺少完整闭环。",
    "action": "围绕多目标路由、异步任务、进度降级、动态结果、导出归档和安全边界完成连续迭代与验证。",
    "impact": "形成已部署的核心版本、使用文档和一组经过脱敏审核的交付证据。",
    "projectSlugs": ["sql-audit"],
    "evidenceIds": ["sql-audit-delivery-set"]
  }
]
```

Add repository assertions:

```java
assertThat(project.getCode()).isEqualTo("P-01");
assertThat(snapshot.getEvidence().getFirst().getCode()).isEqualTo("E-01");
assertThat(snapshot.getTimeline()).singleElement()
        .extracting(TimelineEvent::getId)
        .isEqualTo("timeline-sql-audit-delivery");
```

- [x] **Step 5: Run focused tests and verify GREEN**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioModelContractTest,JsonPublicPortfolioRepositoryTest test
```

Expected: both classes pass with zero failures.

- [x] **Step 6: Conditional commit after explicit authorization**

```powershell
git add backend/src/main/java/com/portfolio/agent/portfolio/domain backend/src/main/resources/public-data/public-portfolio.v1.json backend/src/test/java/com/portfolio/agent/portfolio/domain/PortfolioModelContractTest.java backend/src/test/java/com/portfolio/agent/portfolio/repository/file/JsonPublicPortfolioRepositoryTest.java backend/src/test/java/com/portfolio/agent/portfolio/service/PortfolioServiceTest.java backend/src/test/java/com/portfolio/agent/answer/adapter/portfolio/LocalPortfolioKnowledgeAdapterTest.java
git commit -m "feat: add reviewed public timeline facts"
```

### Task 2: Validate public codes and every timeline reference

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidator.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidatorTest.java`

**Interfaces:**
- Consumes: `ProjectProfile#getCode`, `EvidenceRecord#getCode`, `PortfolioSnapshot#getTimeline`.
- Produces: startup rejection through `InvalidPortfolioSnapshotException` for blank/duplicate codes, blank timeline content, duplicate timeline IDs, dangling project slugs, dangling Evidence IDs, and non-public Evidence references.

- [x] **Step 1: Update the valid JSON fixture and add failing validation cases**

Add project/evidence codes to the existing fixture. Extract the timeline JSON into this exact helper and interpolate it into `validJson()` as `"timeline": [%s]` so timeline-only mutations cannot accidentally change Project references:

```java
private String timelineJson() {
    return """
            {
              "id": "timeline-sql-audit-delivery",
              "dateLabel": "2026.06–07",
              "title": "从固定路径查询到可交付工具",
              "problem": "查询路径被写死",
              "action": "完成多目标路由和验证",
              "impact": "形成公开交付闭环",
              "projectSlugs": ["sql-audit"],
              "evidenceIds": ["sql-audit-delivery-set"]
            }
            """;
}
```

Add these tests:

```java
@Test
void rejectsDuplicateProjectCodes() {
    String second = projectJson()
            .replace("\"id\": \"sql-audit-project\"", "\"id\": \"copy-project\"")
            .replace("\"slug\": \"sql-audit\"", "\"slug\": \"sql-audit-copy\"");
    assertInvalid(validJson().replace(projectJson(), projectJson() + "," + second),
            "duplicate project code");
}

@Test
void rejectsBlankEvidenceCode() {
    assertInvalid(validJson().replace("\"code\": \"E-01\"", "\"code\": \" \""),
            "evidence code");
}

@Test
void rejectsTimelineWithMissingProject() {
    assertInvalid(validJson().replace("\"projectSlugs\": [\"sql-audit\"]",
            "\"projectSlugs\": [\"missing-project\"]"), "timeline project reference");
}

@Test
void rejectsTimelineWithMissingEvidence() {
    String invalidTimeline = timelineJson().replace(
            "\"evidenceIds\": [\"sql-audit-delivery-set\"]",
            "\"evidenceIds\": [\"missing-evidence\"]"
    );
    assertInvalid(validJson().replace(timelineJson(), invalidTimeline),
            "timeline evidence reference");
}

@Test
void rejectsBlankTimelineNarrative() {
    assertInvalid(validJson().replace("\"impact\": \"形成公开交付闭环\"",
            "\"impact\": \" \""), "timeline impact");
}
```

- [x] **Step 2: Run the validator test and verify RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioSnapshotValidatorTest test
```

Expected: new cases fail because code and timeline validation are absent.

- [x] **Step 3: Add the minimal validation rules**

After building the existing ID maps, add unique code maps and timeline validation:

```java
List<TimelineEvent> timeline = requiredList(snapshot.getTimeline(), "timeline");
uniqueById(projects, ProjectProfile::getCode, "project code");
uniqueById(evidence, EvidenceRecord::getCode, "evidence code");
uniqueById(timeline, TimelineEvent::getId, "timeline");

for (TimelineEvent event : timeline) {
    require(hasText(event.getDateLabel()), "timeline dateLabel is required: " + event.getId());
    require(hasText(event.getTitle()), "timeline title is required: " + event.getId());
    require(hasText(event.getProblem()), "timeline problem is required: " + event.getId());
    require(hasText(event.getAction()), "timeline action is required: " + event.getId());
    require(hasText(event.getImpact()), "timeline impact is required: " + event.getId());
    for (String slug : requiredNonBlankList(event.getProjectSlugs(), "timeline projectSlugs")) {
        require(projectsBySlug.containsKey(slug),
                "timeline project reference does not exist: " + slug);
    }
    for (String evidenceId : requiredNonBlankList(event.getEvidenceIds(), "timeline evidenceIds")) {
        EvidenceRecord referenced = evidenceById.get(evidenceId);
        require(referenced != null,
                "timeline evidence reference does not exist: " + evidenceId);
        require(referenced.getPublicStatus() == EvidenceStatus.APPROVED,
                "timeline evidence must be APPROVED: " + evidenceId);
    }
}
```

Also explicitly validate `hasText(project.getCode())` and `hasText(item.getCode())` before their remaining field rules so error messages are stable.

- [x] **Step 4: Run validator and repository tests and verify GREEN**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioSnapshotValidatorTest,JsonPublicPortfolioRepositoryTest test
```

Expected: all cases pass.

- [x] **Step 5: Run backend quality boundaries**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1 -Path backend/src
powershell -ExecutionPolicy Bypass -File scripts/architecture-check.ps1 -Path backend/src
```

Expected: both scripts exit 0.

- [x] **Step 6: Conditional commit after explicit authorization**

```powershell
git add backend/src/main/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidator.java backend/src/test/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidatorTest.java
git commit -m "feat: validate public timeline references"
```

### Task 3: Expose the additive public-content aggregate API

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/service/result/PublicContent.java`
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/PublicContentResponse.java`
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/TimelineEventResponse.java`
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/controller/PublicContentController.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/service/PortfolioService.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/ProjectDetailResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/EvidenceResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/mapper/PortfolioResponseMapper.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/service/PortfolioServiceTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/controller/PortfolioControllerTest.java`

**Interfaces:**
- Produces: `PortfolioService#getPublicContent(): PublicContent`.
- Produces: `PortfolioResponseMapper#toPublicContentResponse(PublicContent): PublicContentResponse`.
- Produces: `GET /api/v1/public-content` with `contentVersion`, `publishedAt`, `owner`, complete `projects`, approved `evidence`, and `timeline` arrays.
- Preserves: existing controller methods and response assertions.

- [x] **Step 1: Add failing Service and MockMvc tests**

Add to `PortfolioServiceTest`:

```java
@Test
void getPublicContentReadsOneSnapshotAndBuildsReverseEvidenceLinks() {
    CountingRepository repository = new CountingRepository(snapshot());
    PortfolioService service = new PortfolioService(repository);

    PublicContent content = service.getPublicContent();

    assertThat(repository.reads).isEqualTo(1);
    assertThat(content.getProjects()).singleElement()
            .satisfies(project -> assertThat(project.getSuggestedQuestions())
                    .containsExactly("What did you build?"));
    assertThat(content.getEvidence()).extracting(EvidenceRecord::getId)
            .containsExactly("evidence-1");
    assertThat(content.getProjectSlugsByEvidenceId().get("evidence-1"))
            .containsExactly("sql-audit");
    assertThat(content.getTimeline()).extracting(TimelineEvent::getId)
            .containsExactly("timeline-1");
}
```

Add to `PortfolioControllerTest`:

```java
@Test
void returnsCompleteReviewedPublicContent() throws Exception {
    mockMvc.perform(get("/api/v1/public-content"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contentVersion").value("2026-07-14.1"))
            .andExpect(jsonPath("$.projects[0].code").value("P-01"))
            .andExpect(jsonPath("$.projects[0].evidenceIds[0]")
                    .value("sql-audit-delivery-set"))
            .andExpect(jsonPath("$.projects[0].suggestedQuestions.length()").value(1))
            .andExpect(jsonPath("$.evidence[0].code").value("E-01"))
            .andExpect(jsonPath("$.evidence[0].publicStatus").value("APPROVED"))
            .andExpect(jsonPath("$.evidence[0].projectSlugs[0]").value("sql-audit"))
            .andExpect(jsonPath("$.timeline[0].id")
                    .value("timeline-sql-audit-delivery"));
}
```

- [x] **Step 2: Run focused tests and verify RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioServiceTest,PortfolioControllerTest test
```

Expected: compilation fails because `PublicContent` and the endpoint do not exist.

- [x] **Step 3: Implement the application result and one-snapshot assembly**

Implement `PublicContent` with these immutable fields and getters:

```java
private final String contentVersion;
private final OffsetDateTime publishedAt;
private final OwnerProfile owner;
private final List<ProjectDetails> projects;
private final List<EvidenceRecord> evidence;
private final List<TimelineEvent> timeline;
private final Map<String, List<String>> projectSlugsByEvidenceId;
```

The constructor must use `List.copyOf` and deep-copy the map values before `Map.copyOf`.

Refactor `PortfolioService` so `getProject` and `getPublicContent` share this private method:

```java
private ProjectDetails toProjectDetails(PortfolioSnapshot snapshot, ProjectProfile project) {
    Set<String> evidenceIds = Set.copyOf(project.getEvidenceIds());
    List<EvidenceRecord> evidence = snapshot.getEvidence().stream()
            .filter(item -> evidenceIds.contains(item.getId()))
            .filter(item -> item.getPublicStatus() == EvidenceStatus.APPROVED)
            .filter(item -> Boolean.FALSE.equals(item.getRawContentPublic()))
            .toList();
    List<String> suggestedQuestions = snapshot.getQuestions().stream()
            .filter(question -> project.getId().equals(question.getProjectId()))
            .filter(question -> project.getQuestionIds().contains(question.getId()))
            .map(QuestionDefinition::getSuggestion)
            .toList();
    return new ProjectDetails(project, evidence, suggestedQuestions);
}
```

`getPublicContent()` must read the Repository once, map every project with this method, filter approved/non-raw Evidence, build a deterministic `LinkedHashMap<String, List<String>>` by project order, and return the snapshot timeline unchanged.

- [x] **Step 4: Implement additive response DTO fields and aggregate mapping**

Add `code` and `evidenceIds` to `ProjectDetailResponse`. Add `code` and `projectSlugs` to `EvidenceResponse`, with this factory:

```java
public static EvidenceResponse from(EvidenceRecord evidence, List<String> projectSlugs) {
    return new EvidenceResponse(
            evidence.getId(), evidence.getCode(), evidence.getTitle(), evidence.getType().name(),
            evidence.getPeriodStart(), evidence.getPeriodEnd(), evidence.getSourceCount(),
            evidence.getSummary(), evidence.getSupportedClaims(),
            evidence.getPublicStatus().name(), evidence.getRawContentPublic(), projectSlugs
    );
}
```

Keep `EvidenceResponse.from(EvidenceRecord)` for existing call sites and delegate with `List.of()`.

`TimelineEventResponse` must contain the same eight fields as `TimelineEvent`, use defensive list copies, expose getters/value semantics, and provide `from(TimelineEvent)`.

`PublicContentResponse` must contain:

```java
private final String contentVersion;
private final OffsetDateTime publishedAt;
private final OwnerResponse owner;
private final List<ProjectDetailResponse> projects;
private final List<EvidenceResponse> evidence;
private final List<TimelineEventResponse> timeline;
```

Add mapper logic:

```java
public PublicContentResponse toPublicContentResponse(PublicContent content) {
    return new PublicContentResponse(
            content.getContentVersion(),
            content.getPublishedAt(),
            OwnerResponse.from(content.getOwner()),
            content.getProjects().stream().map(this::toProjectResponse).toList(),
            content.getEvidence().stream()
                    .map(item -> EvidenceResponse.from(
                            item,
                            content.getProjectSlugsByEvidenceId()
                                    .getOrDefault(item.getId(), List.of())
                    ))
                    .toList(),
            content.getTimeline().stream().map(TimelineEventResponse::from).toList()
    );
}
```

- [x] **Step 5: Add the controller and verify GREEN**

```java
@RestController
@RequestMapping("/api/v1/public-content")
public class PublicContentController {

    private final PortfolioService portfolioService;
    private final PortfolioResponseMapper responseMapper;

    public PublicContentController(
            PortfolioService portfolioService,
            PortfolioResponseMapper responseMapper
    ) {
        this.portfolioService = portfolioService;
        this.responseMapper = responseMapper;
    }

    @GetMapping
    public PublicContentResponse getPublicContent() {
        return responseMapper.toPublicContentResponse(portfolioService.getPublicContent());
    }
}
```

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioServiceTest,PortfolioControllerTest test
```

Expected: both test classes pass, including all old endpoint assertions.

- [x] **Step 6: Run the complete backend suite and boundaries**

```powershell
mvn.cmd -f backend/pom.xml test
powershell -ExecutionPolicy Bypass -File scripts/code-quality-check.ps1 -Path backend/src
powershell -ExecutionPolicy Bypass -File scripts/architecture-check.ps1 -Path backend/src
```

Expected: Maven reports `BUILD SUCCESS`; both scripts exit 0.

- [x] **Step 7: Conditional commit after explicit authorization**

```powershell
git add backend/src/main/java/com/portfolio/agent/portfolio backend/src/test/java/com/portfolio/agent/portfolio
git commit -m "feat: expose reviewed public content aggregate"
```

### Task 4: Replace the production preview Repository with a cached API Repository

**Files:**
- Modify: `frontend/src/features/portfolio/api/portfolioApi.ts`
- Modify: `frontend/src/features/public-content/repository/publicContentRepository.ts`
- Modify: `frontend/src/features/public-content/repository/previewPublicContentRepository.ts`
- Create: `frontend/src/features/public-content/repository/apiPublicContentRepository.ts`
- Create: `frontend/src/features/public-content/repository/apiPublicContentRepository.test.ts`
- Create: `frontend/src/features/public-content/composables/usePublicContent.ts`
- Create: `frontend/src/features/public-content/composables/usePublicContent.test.ts`

**Interfaces:**
- Produces: `getPublicContent(): Promise<PublicPortfolio>`.
- Produces: `PublicContentRepository#invalidate(): void`.
- Produces: `ApiPublicContentRepository` with one in-flight/resolved Promise per load cycle.
- Produces: `createPublicContentState(repository)` and `usePublicContent()` exposing `portfolio`, `status`, `error`, `load()`, and `retry()`.

- [x] **Step 1: Add failing API Repository tests**

```ts
import { describe, expect, it, vi } from 'vitest'
import { previewPublicContent } from '../data/previewPublicContent'
import { ApiPublicContentRepository } from './apiPublicContentRepository'

describe('ApiPublicContentRepository', () => {
  it('shares one aggregate request across every selector', async () => {
    const loader = vi.fn().mockResolvedValue(previewPublicContent)
    const repository = new ApiPublicContentRepository(loader)

    const [portfolio, projects, project, timeline, evidence] = await Promise.all([
      repository.getPortfolio(), repository.getProjects(), repository.getProject('sql-audit'),
      repository.getTimeline(), repository.getEvidence(),
    ])

    expect(loader).toHaveBeenCalledTimes(1)
    expect(portfolio.contentVersion).toBe('2026-07-14.1')
    expect(projects).toHaveLength(1)
    expect(project?.code).toBe('P-01')
    expect(timeline).toHaveLength(1)
    expect(evidence.every((item) => item.publicStatus === 'APPROVED')).toBe(true)
  })

  it('loads again after invalidation', async () => {
    const loader = vi.fn().mockResolvedValue(previewPublicContent)
    const repository = new ApiPublicContentRepository(loader)
    await repository.getPortfolio()
    repository.invalidate()
    await repository.getPortfolio()
    expect(loader).toHaveBeenCalledTimes(2)
  })
})
```

- [x] **Step 2: Run the Repository test and verify RED**

```powershell
npm.cmd --prefix frontend test -- --run src/features/public-content/repository/apiPublicContentRepository.test.ts
```

Expected: test compilation fails because the API Repository does not exist.

- [x] **Step 3: Implement the aggregate API and Repository**

Add to `portfolioApi.ts`:

```ts
import type { PublicPortfolio } from '../../public-content/model/publicContentTypes'

export function getPublicContent(): Promise<PublicPortfolio> {
  return request<PublicPortfolio>('/api/v1/public-content', { method: 'GET' })
}
```

Add `invalidate(): void` to the Repository interface; preview implementation uses an empty method. Implement:

```ts
export class ApiPublicContentRepository implements PublicContentRepository {
  private cached: Promise<PublicPortfolio> | null = null
  private readonly loader: () => Promise<PublicPortfolio>

  constructor(loader: () => Promise<PublicPortfolio> = getPublicContent) {
    this.loader = loader
  }

  invalidate() { this.cached = null }
  getPortfolio() { return this.cached ?? (this.cached = this.loader()) }
  async getProjects() { return (await this.getPortfolio()).projects }
  async getProject(slug: string) {
    return (await this.getPortfolio()).projects.find((item) => item.slug === slug) ?? null
  }
  async getTimeline() { return (await this.getPortfolio()).timeline }
  async getEvidence() {
    return (await this.getPortfolio()).evidence.filter((item) => item.publicStatus === 'APPROVED')
  }
}

export const apiPublicContentRepository = new ApiPublicContentRepository()
```

Change the production export to:

```ts
export const publicContentRepository: PublicContentRepository = apiPublicContentRepository
```

- [x] **Step 4: Add failing shared-state tests**

```ts
it('moves from loading to ready', async () => {
  const repository = new ApiPublicContentRepository(
    vi.fn().mockResolvedValue(previewPublicContent),
  )
  const state = createPublicContentState(repository)
  const request = state.load()
  expect(state.status.value).toBe('loading')
  await request
  expect(state.status.value).toBe('ready')
  expect(state.portfolio.value?.projects[0]?.slug).toBe('sql-audit')
})

it('clears a failed cache before retrying', async () => {
  const loader = vi.fn()
    .mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValueOnce(previewPublicContent)
  const state = createPublicContentState(new ApiPublicContentRepository(loader))
  await state.load()
  expect(state.status.value).toBe('error')
  await state.retry()
  expect(loader).toHaveBeenCalledTimes(2)
  expect(state.status.value).toBe('ready')
})
```

- [x] **Step 5: Implement shared state with a test injection key**

```ts
export type PublicContentStatus = 'idle' | 'loading' | 'ready' | 'error'

export function createPublicContentState(repository: PublicContentRepository) {
  const portfolio = ref<PublicPortfolio | null>(null)
  const status = ref<PublicContentStatus>('idle')
  const error = ref('')

  async function load() {
    if (status.value === 'loading' || status.value === 'ready') return
    status.value = 'loading'
    error.value = ''
    try {
      portfolio.value = await repository.getPortfolio()
      status.value = 'ready'
    } catch (cause) {
      status.value = 'error'
      error.value = cause instanceof Error ? cause.message : '公开内容暂时无法加载，请稍后重试'
    }
  }

  async function retry() {
    repository.invalidate()
    status.value = 'idle'
    await load()
  }

  return { portfolio, status, error, load, retry }
}

export type PublicContentState = ReturnType<typeof createPublicContentState>
export const publicContentStateKey: InjectionKey<PublicContentState> = Symbol('public-content-state')
const productionState = createPublicContentState(publicContentRepository)

export function usePublicContent(): PublicContentState {
  const state = inject(publicContentStateKey, productionState)
  void state.load()
  return state
}
```

- [x] **Step 6: Run focused and existing API tests**

```powershell
npm.cmd --prefix frontend test -- --run src/features/public-content src/features/portfolio/api/portfolioApi.test.ts
```

Expected: all selected tests pass.

- [x] **Step 7: Conditional commit after explicit authorization**

```powershell
git add frontend/src/features/portfolio/api frontend/src/features/public-content
git commit -m "feat: load public content through api repository"
```

### Task 5: Connect every public-content page to shared loading and retry state

**Files:**
- Create: `frontend/src/shared/components/PublicContentFeedback.vue`
- Create: `frontend/src/test/publicContentStateFixture.ts`
- Modify: `frontend/src/pages/HomePage.vue`
- Modify: `frontend/src/pages/ProjectsPage.vue`
- Modify: `frontend/src/pages/ProjectPage.vue`
- Modify: `frontend/src/pages/TimelinePage.vue`
- Modify: `frontend/src/pages/EvidencePage.vue`
- Modify: `frontend/src/pages/AgentPage.vue`
- Modify tests beside all six pages; create `frontend/src/pages/AgentPage.test.ts`

**Interfaces:**
- Consumes: `usePublicContent()` from Task 4.
- Produces: `PublicContentFeedback` props `status: 'loading' | 'error'`, `message?: string`; emits `retry`.
- Preserves: existing page success and unpublished/empty views.

- [x] **Step 1: Create a deterministic ready-state fixture and update page tests to inject it**

```ts
import { ref } from 'vue'
import { vi } from 'vitest'

import { previewPublicContent } from '../features/public-content/data/previewPublicContent'
import type { PublicContentState } from '../features/public-content/composables/usePublicContent'

export function readyPublicContentState(): PublicContentState {
  return {
    portfolio: ref(previewPublicContent),
    status: ref('ready'),
    error: ref(''),
    load: vi.fn().mockResolvedValue(undefined),
    retry: vi.fn().mockResolvedValue(undefined),
  }
}
```

For every page mount, add:

```ts
global: {
  provide: { [publicContentStateKey as symbol]: readyPublicContentState() },
  stubs: { RouterLink: RouterLinkStub },
}
```

Add an error/retry test to `HomePage.test.ts`:

```ts
it('shows a safe retry action when public content fails', async () => {
  const state = readyPublicContentState()
  state.portfolio.value = null
  state.status.value = 'error'
  state.error.value = '公开内容暂时无法加载，请稍后重试'
  const wrapper = mount(HomePage, {
    global: { provide: { [publicContentStateKey as symbol]: state } },
  })
  await wrapper.get('[data-public-content-retry]').trigger('click')
  expect(state.retry).toHaveBeenCalledOnce()
  expect(wrapper.text()).not.toContain('/api/v1/public-content')
})
```

- [x] **Step 2: Run page tests and verify RED**

```powershell
npm.cmd --prefix frontend test -- --run src/pages
```

Expected: compilation/rendering fails because the shared feedback and page integration do not exist.

- [x] **Step 3: Implement the feedback component**

```vue
<script setup lang="ts">
defineProps<{ status: 'loading' | 'error'; message?: string }>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <main class="public-content-feedback" :aria-busy="status === 'loading'">
    <p v-if="status === 'loading'">正在装订公开档案…</p>
    <template v-else>
      <p role="alert">{{ message || '公开内容暂时无法加载，请稍后重试' }}</p>
      <button data-public-content-retry type="button" @click="$emit('retry')">重新加载</button>
    </template>
  </main>
</template>
```

Style it only with existing paper, ink, rule, muted, red, serif, and mono tokens; do not introduce a new color or generic card treatment.

- [x] **Step 4: Replace direct Repository calls in all six pages**

Each page uses:

```ts
const { portfolio, status, error, retry } = usePublicContent()
```

Derive page values with `computed`:

```ts
const projects = computed(() => portfolio.value?.projects ?? [])
const events = computed(() => portfolio.value?.timeline ?? [])
const evidence = computed(() => portfolio.value?.evidence ?? [])
const project = computed(() =>
  portfolio.value?.projects.find((item) => item.slug === props.slug) ?? null,
)
```

At the end of each template branch, use:

```vue
<PublicContentFeedback
  v-else-if="status === 'loading' || status === 'error'"
  :status="status"
  :message="error"
  @retry="retry"
/>
```

For `ProjectPage`, render the unpublished view only when `status === 'ready' && !project`. For list pages, render their existing honest empty state only when `status === 'ready'` and the selected collection is empty.

For `AgentPage`, wait for public content readiness before rendering `AgentWorkspace`; Task 6 adds route-seed Answer loading.

- [x] **Step 5: Run page tests and the production build**

```powershell
npm.cmd --prefix frontend test -- --run src/pages src/shared/components
npm.cmd --prefix frontend run build
```

Expected: page tests pass; TypeScript and Vite build succeed.

- [x] **Step 6: Conditional commit after explicit authorization**

```powershell
git add frontend/src/pages frontend/src/shared/components/PublicContentFeedback.vue frontend/src/test/publicContentStateFixture.ts
git commit -m "feat: add public content loading and retry states"
```

### Task 6: Route homepage and Agent conversations through the real Answer API

**Files:**
- Create: `frontend/src/features/agent/model/mapAnswerResponse.ts`
- Create: `frontend/src/features/agent/model/mapAnswerResponse.test.ts`
- Modify: `frontend/src/features/audience/components/AudienceDialogue.vue`
- Modify: `frontend/src/features/audience/components/AudienceDialogue.test.ts`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.vue`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.test.ts`
- Modify: `frontend/src/features/agent/components/ConversationThread.vue`
- Modify: `frontend/src/pages/AgentPage.vue`
- Modify: `frontend/src/pages/AgentPage.test.ts`
- Delete after references reach zero: `frontend/src/features/agent/data/previewAnswers.ts`

**Interfaces:**
- Produces: `mapAnswerResponse(response: AnswerResponse): { content: string; evidenceIds: string[] }`.
- Produces: `ConversationThread` props `pending: boolean`, `error: string`; emits `retry`.
- Preserves: only completed Agent answers are persisted in local sessions.

- [x] **Step 1: Add the failing Answer mapping test**

```ts
it('maps structured sections and evidence ids without inventing content', () => {
  const mapped = mapAnswerResponse({
    requestId: 'request-1', answerMode: 'DETERMINISTIC', matched: true, fallback: false,
    answer: {
      title: '项目说明',
      sections: [
        { type: 'BACKGROUND', content: '背景内容' },
        { type: 'VERIFICATION', content: '验证内容' },
      ],
    },
    evidence: [{
      id: 'evidence-1', title: '证据', type: 'DOCUMENT', periodStart: '2026-07-01',
      periodEnd: '2026-07-10', sourceCount: 1, summary: '摘要',
      supportedClaims: ['已验证'], publicStatus: 'APPROVED', rawContentPublic: false,
    }],
    suggestedQuestions: [],
  })

  expect(mapped.content).toBe('项目说明\n\n背景内容\n\n验证内容')
  expect(mapped.evidenceIds).toEqual(['evidence-1'])
})
```

- [x] **Step 2: Run the mapping test and verify RED**

```powershell
npm.cmd --prefix frontend test -- --run src/features/agent/model/mapAnswerResponse.test.ts
```

Expected: module-not-found failure.

- [x] **Step 3: Implement the pure mapper**

```ts
export function mapAnswerResponse(response: AnswerResponse) {
  return {
    content: [response.answer.title]
      .concat(response.answer.sections.map((section) => section.content))
      .filter((item) => item.trim().length > 0)
      .join('\n\n'),
    evidenceIds: response.evidence.map((item) => item.id),
  }
}
```

- [x] **Step 4: Change homepage dialogue tests to mock `askQuestion` and verify pending/error behavior**

Use `vi.hoisted` for `askQuestionMock`, return a complete `AnswerResponse`, then assert:

```ts
expect(askQuestionMock).toHaveBeenCalledWith('sql-audit', '如何处理连接异常？')
expect(wrapper.get('[data-light-answer]').text()).toContain('项目说明')
```

Add a deferred Promise test that asserts the submit button is disabled while pending, and a rejected Promise test that asserts `[data-answer-retry]` appears and invokes the same question. Because `round` changes only after a successful response, the retry uses the normal `ask(failedQuestion)` path and increments exactly once on success.

- [x] **Step 5: Implement async homepage answers**

Replace `createPreviewAnswer` with `askQuestion` and `mapAnswerResponse`. Track:

```ts
const pending = ref(false)
const answerError = ref('')
const failedQuestion = ref('')

async function ask(question: string) {
  const normalized = question.trim()
  const project = primaryProject.value
  if (!normalized || !project || pending.value) return
  pending.value = true
  answerError.value = ''
  failedQuestion.value = normalized
  try {
    const mapped = mapAnswerResponse(await askQuestion(project.slug, normalized))
    round.value = Math.min(round.value + 1, 3)
    answer.value = {
      round: round.value, question: normalized, answer: mapped.content,
      projectSlug: project.slug, evidenceIds: mapped.evidenceIds,
    }
    customQuestion.value = ''
  } catch (cause) {
    answerError.value = cause instanceof Error ? cause.message : 'Agent 暂时无法回答，请稍后重试'
  } finally {
    pending.value = false
  }
}
```

Render a polite pending state, a safe error, and `<button data-answer-retry @click="ask(failedQuestion)">重新回答</button>`. Disable question buttons, input, and submit while pending.

- [x] **Step 6: Add failing Agent workspace tests for real API success and retry**

Mock `askQuestion`. Submit through `ConversationThread`, then assert the user message appears immediately, the composer is disabled while deferred, the mapped Agent message appears after resolution, and a rejection shows a retry action without appending a second user message.

- [x] **Step 7: Implement async Agent submission and ConversationThread state**

In `AgentWorkspace.vue`, replace the synchronous generator with:

```ts
const pending = ref(false)
const answerError = ref('')
const failedQuestion = ref('')

async function requestAnswer(question: string, appendUser: boolean) {
  const session = sessions.activeSession.value
  const project = activeProject.value
  if (!session || !project || pending.value) return
  if (appendUser) {
    sessions.appendMessage(session.id, { role: 'USER', content: question, evidenceIds: [] })
  }
  pending.value = true
  answerError.value = ''
  failedQuestion.value = question
  try {
    const mapped = mapAnswerResponse(await askQuestion(project.slug, question))
    sessions.appendMessage(session.id, {
      role: 'AGENT', content: mapped.content, evidenceIds: mapped.evidenceIds,
    })
  } catch (cause) {
    answerError.value = cause instanceof Error ? cause.message : 'Agent 暂时无法回答，请稍后重试'
  } finally {
    pending.value = false
  }
}
```

Pass `:pending="pending"`, `:error="answerError"`, and `@retry="requestAnswer(failedQuestion, false)"` to `ConversationThread`.

In `ConversationThread`, disable textarea/submit during pending, render `role="status"` for “正在核对公开事实…”, render `role="alert"` plus `data-answer-retry` for errors, and never append transient states to `AgentSession.messages`.

- [x] **Step 8: Load route-seed answers through the API in `AgentPage`**

After public content becomes ready, when `queryString('question')` is nonblank, call `askQuestion(selectedProject.slug, question)`, map the response, and construct `AgentRouteSeed`. Do not mount `AgentWorkspace` until the seed request succeeds or no seed question exists. On failure, show the same safe retry feedback; retry must not change the URL or create a server session.

- [x] **Step 9: Remove preview answer runtime and run focused tests**

```powershell
rg -n "createPreviewAnswer|previewAnswers" frontend/src
npm.cmd --prefix frontend test -- --run src/features/audience src/features/agent src/pages/AgentPage.test.ts
npm.cmd --prefix frontend run build
```

Expected: `rg` has no production references; tests and build pass. Delete `previewAnswers.ts` only after the reference search is empty.

- [x] **Step 10: Conditional commit after explicit authorization**

```powershell
git add frontend/src/features/agent frontend/src/features/audience frontend/src/pages/AgentPage.vue frontend/src/pages/AgentPage.test.ts
git commit -m "feat: connect portfolio conversations to answer api"
```

### Task 7: Separate mock visual E2E from packaged-JAR integration E2E

**Files:**
- Create: `frontend/e2e/support/publicApiMocks.ts`
- Modify: `frontend/e2e/portfolio.spec.ts`
- Modify: `frontend/playwright.config.ts`
- Modify: `frontend/package.json`
- Create: `scripts/run-jar-e2e.ps1`
- Modify: `scripts/verify-release.ps1`
- Modify: `README.md`
- Modify: `docs/04-项目代码约束.md`

**Interfaces:**
- Preserves: `npm.cmd --prefix frontend run test:e2e` as Vite + mocked public API, with no Java process.
- Produces: `powershell -ExecutionPolicy Bypass -File scripts/run-jar-e2e.ps1` as real packaged-JAR integration.
- Produces: `PLAYWRIGHT_EXTERNAL_SERVER=1` and `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4173` configuration seam.

- [x] **Step 1: Add request assertions to Playwright before changing the topology**

For the homepage flow, wait for `/api/v1/public-content` and `/api/v1/answers`, then assert both responses succeed. Add timeline-to-project/evidence navigation. Keep existing local-session, separator, and responsive tests.

Mock mode must register handlers before navigation:

```ts
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => localStorage.clear())
  if (process.env.PLAYWRIGHT_REAL_API !== '1') await installPublicApiMocks(page)
})
```

- [x] **Step 2: Implement public API mocks with the real response shapes**

`installPublicApiMocks(page)` must route only `/api/v1/public-content` and `/api/v1/answers`, returning `previewPublicContent` and a structured Answer response. It must not intercept navigation, static assets, or unknown `/api` URLs.

- [x] **Step 3: Make Playwright accept an external packaged server**

```ts
const externalServer = process.env.PLAYWRIGHT_EXTERNAL_SERVER === '1'
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:4173'

export default defineConfig({
  // keep existing testDir, projects, retries, and reporter
  use: { baseURL, trace: 'retain-on-failure' },
  webServer: externalServer ? undefined : {
    command: 'npm.cmd run dev -- --host 127.0.0.1 --port 4173',
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})
```

- [x] **Step 4: Create the packaged-JAR runner**

`scripts/run-jar-e2e.ps1` must:

```powershell
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root 'backend\target\portfolio-agent.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Packaged JAR is missing.' }

$process = Start-Process -FilePath 'java.exe' `
    -ArgumentList @('-jar', $jar, '--server.port=4173') `
    -PassThru -WindowStyle Hidden
try {
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:4173/api/v1/public-content'
            if ($response.StatusCode -eq 200) { $ready = $true; break }
        } catch { Start-Sleep -Milliseconds 500 }
    }
    if (-not $ready) { throw 'Packaged application did not become ready.' }
    $env:PLAYWRIGHT_EXTERNAL_SERVER = '1'
    $env:PLAYWRIGHT_REAL_API = '1'
    $env:PLAYWRIGHT_BASE_URL = 'http://127.0.0.1:4173'
    & npm.cmd --prefix (Join-Path $root 'frontend') run test:e2e
    if ($LASTEXITCODE -ne 0) { throw "Playwright failed with exit code $LASTEXITCODE." }
}
finally {
    Remove-Item Env:PLAYWRIGHT_EXTERNAL_SERVER -ErrorAction SilentlyContinue
    Remove-Item Env:PLAYWRIGHT_REAL_API -ErrorAction SilentlyContinue
    Remove-Item Env:PLAYWRIGHT_BASE_URL -ErrorAction SilentlyContinue
    if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
}
```

- [x] **Step 5: Wire release verification and documentation**

In `verify-release.ps1`, run both `architecture-check.ps1` and `run-jar-e2e.ps1`; replace the current direct `npm.cmd --prefix frontend run test:e2e` release step with the JAR runner. Preserve the frontend-only mock E2E command as a separate stage when desired.

Update README and `docs/04-项目代码约束.md` to state exactly:

```powershell
# Frontend-only visual/interaction acceptance with API mocks
npm.cmd --prefix frontend run test:e2e

# Full packaged-JAR frontend/backend integration
powershell -ExecutionPolicy Bypass -File scripts/run-jar-e2e.ps1
```

Document that the integration command requires a fresh frontend build and Maven package.

- [x] **Step 6: Run mock E2E, package, and real integration E2E**

```powershell
npm.cmd --prefix frontend run test:e2e
npm.cmd --prefix frontend run build
mvn.cmd -f backend/pom.xml clean package
powershell -ExecutionPolicy Bypass -File scripts/run-jar-e2e.ps1
```

Expected: mock E2E passes without Java; package succeeds; real E2E observes successful public-content and Answer API responses from the JAR.

- [x] **Step 7: Conditional commit after explicit authorization**

```powershell
git add frontend/e2e frontend/playwright.config.ts frontend/package.json scripts/run-jar-e2e.ps1 scripts/verify-release.ps1 README.md docs/04-项目代码约束.md
git commit -m "test: verify packaged portfolio integration"
```

### Task 8: Run the complete release gate and inspect the final diff

**Files:**
- Verify all files listed in Tasks 1–7.
- Do not modify unrelated `.agents/`, `.claude/`, `.playwright-cli/`, `frontend/output/`, or `skills-lock.json` files.

**Interfaces:**
- Confirms: production frontend has no preview runtime dependencies.
- Confirms: package/privacy/architecture contracts and real browser integration all pass.

- [x] **Step 1: Run static reference and diff checks**

```powershell
rg -n "previewPublicContentRepository|createPreviewAnswer|previewAnswers" frontend/src --glob "!**/*.test.ts" --glob "!**/test/**"
rg -n "publicStatus|rawContentPublic|timeline|projectSlugs" backend/src/main frontend/src/features/public-content
git diff --check
```

Expected: first search has no production matches; second shows the intended public-boundary implementation; diff check exits 0.

- [x] **Step 2: Run the atomic release verifier**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-release.ps1 -SkipInstall -SkipDockerCheck
```

Expected: code-quality, architecture, frontend tests/build, backend clean package, privacy checks, JAR inspection, packaged-JAR HTTP readiness, and Playwright integration all pass.

- [x] **Step 3: Inspect Git scope without changing it**

```powershell
git status --short
git diff --stat
Get-Content -Raw docs/superpowers/specs/2026-07-17-public-content-api-integration-design.md
Get-Content -Raw docs/superpowers/plans/2026-07-17-public-content-api-integration.md
```

Expected: only intentional integration files plus the user's pre-existing untracked local-tool files appear.

- [x] **Step 4: Conditional final commit after explicit authorization**

If Tasks 1–7 were not committed individually and the user explicitly authorizes one final commit:

```powershell
git add backend frontend scripts README.md docs/04-项目代码约束.md docs/superpowers/specs/2026-07-17-public-content-api-integration-design.md docs/superpowers/plans/2026-07-17-public-content-api-integration.md
git commit -m "feat: complete portfolio frontend backend integration"
```

If authorization is absent, do not stage or commit; report the verified working tree and suggested commit message instead.
