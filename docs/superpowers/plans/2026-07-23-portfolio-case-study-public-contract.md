# Portfolio CaseStudy Public Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a first-class public `CaseStudy` contract, expose three verified standalone cases and the SQL-audit July expansion through schema `3.0`, while preserving schema `2.0` runtime compatibility and leaving the frontend and Agent execution behavior unchanged.

**Architecture:** Extend the existing immutable portfolio domain instead of representing cases as projects. Centralize schema-aware JSON normalization before strict Jackson deserialization, then add Case-specific service results, DTOs, endpoints, and public-content indexes. Prepare and publish content only through the existing evidence-governance workflow, with an explicit human approval gate over the exact candidate bytes.

**Tech Stack:** Java 21, Spring Boot 3.5, Jackson 2.19, JUnit 5, AssertJ, MockMvc, Maven, PowerShell governance scripts, JSON release bundles.

## Global Constraints

- Public schema becomes exactly `3.0`; runtime loading must continue to accept exactly `2.0` and `3.0`.
- Schema `2.0` normalizes to `cases: []`, `caseIds: []`, and `cases: 0`; schema `3.0` requires those fields explicitly and rejects missing or unknown top-level fields.
- `CaseStudy` is an independent domain type. Do not fake it as `ProjectProfile` and do not introduce a unified `WorkItem` abstraction.
- Initial publication includes the SQL-audit July expansion plus exactly three cases: multilingual image preservation, test role reset, and CodeGraph evaluation.
- CodeGraph percentages and token figures remain excluded; only approved qualitative findings may be public.
- CodeGraph public `achievementStatus` is `PROTOTYPE`; do not add `VALIDATED_PROTOTYPE` to the public enum.
- Case question presets are public metadata only. Do not make them executable through the Agent or retrieval orchestration.
- Do not modify anything under `frontend/`; the final `git diff -- frontend` must be empty.
- Do not add Case context to `answer`, `chat`, retrieval, prompt assembly, or Agent routes.
- Existing `/api/v1/portfolio` and `/api/v1/projects/{slug}` response meaning must remain unchanged.
- New endpoints are `GET /api/v1/cases` and `GET /api/v1/cases/{slug}`; an unknown slug returns HTTP 404 with code `CASE_NOT_FOUND`.
- `/api/v1/public-content` adds `cases` and `caseSlugsByEvidenceId`.
- Only `APPROVED` evidence with `rawContentPublic == false` may be returned.
- All Java domain and DTO classes remain explicit immutable classes: no Java records, Lombok, or `var`.
- Follow red-green-refactor TDD. Each task runs its focused tests before the broader suite.
- The private knowledge base is an authoring source only; runtime code must never read it.
- Governance order is inspect → validate → benchmark → build-review-pack → human approve → publish → verify. Never auto-approve.
- Before approval or publish, show the exact public diff and candidate hash to the user and stop for explicit confirmation.
- No deployment is part of this plan.

---

## File Structure

### New production files

- `backend/src/main/java/com/portfolio/agent/portfolio/domain/CaseType.java` — public case classification (`FEATURE`, `EVALUATION`, reserved `INCIDENT`).
- `backend/src/main/java/com/portfolio/agent/portfolio/domain/CaseStudy.java` — immutable standalone case contract.
- `backend/src/main/java/com/portfolio/agent/portfolio/repository/file/PortfolioSnapshotJsonReader.java` — schema-aware `2.0` normalization and strict `3.0` deserialization.
- `backend/src/main/java/com/portfolio/agent/portfolio/service/result/CaseDetails.java` — Case plus approved evidence and suggested public questions.
- `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/CaseSummaryResponse.java` — collection response item.
- `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/CaseDetailResponse.java` — detailed Case API/public-content response.
- `backend/src/main/java/com/portfolio/agent/portfolio/exception/CaseNotFoundException.java` — maps unknown Case slug to `CASE_NOT_FOUND`.

### New test files

- `backend/src/test/java/com/portfolio/agent/portfolio/domain/CaseStudyModelContractTest.java`
- `backend/src/test/java/com/portfolio/agent/portfolio/repository/file/PortfolioSnapshotJsonReaderTest.java`

### Existing files modified

- Domain: `PortfolioSnapshot.java`, `RuntimeContentSnapshot.java`, `QuestionDefinition.java`, `TimelineEvent.java`, `ClaimSubjectType.java`, `BundleCounts.java`.
- Validation/loading: `PortfolioSnapshotValidator.java`, `PublicBundleLoader.java`, `JsonPublicPortfolioRepository.java`.
- Service/API: `PortfolioService.java`, `PublicContent.java`, `PortfolioResponseMapper.java`, `PortfolioController.java`, `PublicContentResponse.java`, `QuestionPresetResponse.java`, `TimelineEventResponse.java`, `PortfolioErrorCode.java`.
- Tests: the corresponding model, validator, loader, repository, service, mapper/controller, and release-contract tests.
- Governance tests/tooling: tracked `scripts/portfolio-governance.test.ps1`; installed private `.agents/skills/portfolio-governance` implementation is updated locally but is never staged.
- Public bundle after human approval only: the four files in `backend/src/main/resources/public-data/bundle/`.
- Status documentation after successful verification: `docs/00-文档状态索引.md`, `docs/08-current-implementation-status.md`, `docs/09-portfolio-asset-library-status.md`, the approved design spec status, and `README.md`.

---

### Task 1: Introduce the immutable CaseStudy domain

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/domain/CaseType.java`
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/domain/CaseStudy.java`
- Create: `backend/src/test/java/com/portfolio/agent/portfolio/domain/CaseStudyModelContractTest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/domain/ClaimSubjectType.java`

**Interfaces:**
- Produces: `CaseStudy` with constructor parameters matching the schema fields in their JSON order.
- Produces: `CaseType.FEATURE`, `CaseType.EVALUATION`, `CaseType.INCIDENT`.
- Produces: `ClaimSubjectType.CASE`.

- [ ] **Step 1: Write the failing model-contract test**

```java
@Test
void caseStudyIsImmutableAndDefensivelyCopiesCollections() {
    List<String> actions = new ArrayList<>(List.of("保留已存在语言映射"));
    CaseStudy value = new CaseStudy(
            "case-multilingual-upload", "CASE-01", "multilingual-image-preservation",
            CaseType.FEATURE, "多语言图片上传结果保留修复", "连续上传不会覆盖既有语言",
            "后续上传会丢失先前语言映射", actions, List.of("合并持久化与本次上传映射"),
            List.of("先上传德语，再上传法语并查询"), "德语与法语映射同时保留",
            List.of("未公开内部地址"), AchievementStatus.DELIVERED,
            ContributionType.PRIMARY, null,
            List.of("claim-case-multilingual-preserve-existing"),
            List.of("evidence-case-multilingual-implementation-and-regression"),
            List.of("timeline-case-multilingual-delivery"),
            List.of("question-case-multilingual-overview")
    );

    actions.add("mutated");

    assertThat(value.getActions()).containsExactly("保留已存在语言映射");
    assertThat(value.getType()).isEqualTo(CaseType.FEATURE);
    assertThat(value.getProjectId()).isNull();
    assertThat(CaseStudy.class.isRecord()).isFalse();
    assertThat(ClaimSubjectType.valueOf("CASE")).isEqualTo(ClaimSubjectType.CASE);
}
```

- [ ] **Step 2: Run the focused test and verify the red state**

Run: `mvn -f backend/pom.xml -Dtest=CaseStudyModelContractTest test`

Expected: compilation fails because `CaseStudy`, `CaseType`, and `ClaimSubjectType.CASE` do not exist.

- [ ] **Step 3: Implement the enum and immutable CaseStudy**

```java
public enum CaseType {
    FEATURE,
    EVALUATION,
    INCIDENT
}
```

Implement `CaseStudy` as an explicit final class with the exact fields:

```java
private final String id;
private final String code;
private final String slug;
private final CaseType type;
private final String title;
private final String summary;
private final String problem;
private final List<String> actions;
private final List<String> decisions;
private final List<String> verification;
private final String outcome;
private final List<String> limitations;
private final AchievementStatus achievementStatus;
private final ContributionType contributionType;
private final String projectId;
private final List<String> claimIds;
private final List<String> evidenceIds;
private final List<String> timelineEventIds;
private final List<String> questionPresetIds;
```

Annotate its full constructor with `@JsonCreator` and every argument with its exact `@JsonProperty`; copy every list with `List.copyOf`. Add getters, value-based `equals`, `hashCode`, and `toString`. Add `CASE` to `ClaimSubjectType`.

- [ ] **Step 4: Run the model tests**

Run: `mvn -f backend/pom.xml -Dtest=CaseStudyModelContractTest,ClaimModelContractTest test`

Expected: PASS.

- [ ] **Step 5: Commit the domain unit**

```powershell
git add backend/src/main/java/com/portfolio/agent/portfolio/domain/CaseType.java backend/src/main/java/com/portfolio/agent/portfolio/domain/CaseStudy.java backend/src/main/java/com/portfolio/agent/portfolio/domain/ClaimSubjectType.java backend/src/test/java/com/portfolio/agent/portfolio/domain/CaseStudyModelContractTest.java
git commit -m "feat: add CaseStudy domain contract"
```

### Task 2: Extend snapshot relations and preserve schema 2.0 compatibility

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/repository/file/PortfolioSnapshotJsonReader.java`
- Create: `backend/src/test/java/com/portfolio/agent/portfolio/repository/file/PortfolioSnapshotJsonReaderTest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/domain/PortfolioSnapshot.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/domain/RuntimeContentSnapshot.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/domain/QuestionDefinition.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/domain/TimelineEvent.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/domain/PortfolioModelContractTest.java`

**Interfaces:**
- Produces: `PortfolioSnapshotJsonReader(ObjectMapper)`, `PortfolioSnapshot readBundle(byte[])`, and `PortfolioSnapshot readLegacyResource(byte[])`.
- Produces: `PortfolioSnapshot.getCases()`, `RuntimeContentSnapshot.getCases()`.
- Produces: `QuestionDefinition.getCaseIds()`, `TimelineEvent.getCaseIds()`.

- [ ] **Step 1: Write failing normalization and strictness tests**

```java
@Test
void schemaTwoNormalizesMissingCaseFieldsToEmptyLists() {
    PortfolioSnapshot snapshot = reader.readBundle(schemaTwoPortfolioBytes());

    assertThat(snapshot.getSchemaVersion()).isEqualTo("2.0");
    assertThat(snapshot.getCases()).isEmpty();
    assertThat(snapshot.getQuestions()).allSatisfy(question ->
            assertThat(question.getCaseIds()).isEmpty());
    assertThat(snapshot.getTimeline()).allSatisfy(event ->
            assertThat(event.getCaseIds()).isEmpty());
}

@Test
void schemaThreeRequiresCasesAtTheTopLevel() {
    assertThatThrownBy(() -> reader.readBundle(schemaThreeWithoutCasesBytes()))
            .isInstanceOf(InvalidPortfolioSnapshotException.class)
            .hasMessageContaining("cases is required for schemaVersion 3.0");
}

@Test
void schemaThreeRejectsUnknownTopLevelFields() {
    assertThatThrownBy(() -> reader.readBundle(schemaThreeWithField("internalNotes", "secret")))
            .isInstanceOf(InvalidPortfolioSnapshotException.class)
            .hasMessageContaining("portfolio.json field set is not canonical");
}

@Test
void rejectsUnknownSchemaVersion() {
    assertThatThrownBy(() -> reader.readBundle(portfolioBytesWithSchema("4.0")))
            .isInstanceOf(InvalidPortfolioSnapshotException.class)
            .hasMessageContaining("unsupported schemaVersion: 4.0");
}
```

- [ ] **Step 2: Run and verify the red state**

Run: `mvn -f backend/pom.xml -Dtest=PortfolioSnapshotJsonReaderTest,PortfolioModelContractTest test`

Expected: compilation fails for the new reader and accessors.

- [ ] **Step 3: Add relation fields to the immutable models**

Add `List<CaseStudy> cases` immediately after projects in `PortfolioSnapshot`; propagate it through the constructor, `getCases`, `withPublishedAt`, equality, hash code, and string output. Copy it into `RuntimeContentSnapshot`.

Add `List<String> caseIds` immediately after `projectIds` in both `QuestionDefinition` and `TimelineEvent`; use `List.copyOf`, expose `getCaseIds`, and include the field in equality/hash/string methods. The constructor never accepts `null`; the reader performs legacy normalization first.

- [ ] **Step 4: Implement schema-aware JSON normalization**

`PortfolioSnapshotJsonReader.readBundle` must:

```java
JsonNode parsed = objectMapper.readTree(bytes);
require(parsed instanceof ObjectNode, "portfolio.json must contain a JSON object");
ObjectNode root = (ObjectNode) parsed;
String schemaVersion = requiredText(root, "schemaVersion");
if ("2.0".equals(schemaVersion)) {
    root.putArray("cases");
    normalizeMissingArray(root.withArray("questionPresets"), "caseIds");
    normalizeMissingArray(root.withArray("timelineEvents"), "caseIds");
} else if ("3.0".equals(schemaVersion)) {
    require(root.has("cases"), "cases is required for schemaVersion 3.0");
    requireNestedArrayPresent(root.withArray("questionPresets"), "caseIds");
    requireNestedArrayPresent(root.withArray("timelineEvents"), "caseIds");
} else {
    throw invalid("unsupported schemaVersion: " + schemaVersion);
}
requireExactFields(root, Set.of(
        "schemaVersion", "contentVersion", "owner", "projects",
        "cases", "claims", "evidence", "claimEvidenceLinks", "timelineEvents",
        "questionPresets"));
return strictMapper.treeToValue(root, PortfolioSnapshot.class);
```

`normalizeMissingArray` iterates array objects and calls `putArray(field)` only when absent. `requireNestedArrayPresent` rejects a missing or non-array field with the item index in the message. Configure the copied mapper with `FAIL_ON_UNKNOWN_PROPERTIES = true`. Wrap Jackson failures in `InvalidPortfolioSnapshotException`.

`readLegacyResource` accepts exactly the legacy single-file field set
`schemaVersion, contentVersion, publishedAt, owner, projects, claims, evidence, claimEvidenceLinks, questions, timeline`, renames `questions` to `questionPresets` and `timeline` to `timelineEvents`, injects legacy Case fields, and then maps strictly. It is not used for release-bundle bytes.

- [ ] **Step 5: Update model fixtures and run focused tests**

Update every direct constructor in `PortfolioModelContractTest` with explicit `cases`/`caseIds`; assert defensive copying for all three new collections.

Run: `mvn -f backend/pom.xml -Dtest=PortfolioSnapshotJsonReaderTest,PortfolioModelContractTest test`

Expected: PASS; schema `2.0` produces empty Case relations and schema `3.0` is strict.

- [ ] **Step 6: Commit the snapshot compatibility unit**

```powershell
git add backend/src/main/java/com/portfolio/agent/portfolio/domain/PortfolioSnapshot.java backend/src/main/java/com/portfolio/agent/portfolio/domain/RuntimeContentSnapshot.java backend/src/main/java/com/portfolio/agent/portfolio/domain/QuestionDefinition.java backend/src/main/java/com/portfolio/agent/portfolio/domain/TimelineEvent.java backend/src/main/java/com/portfolio/agent/portfolio/repository/file/PortfolioSnapshotJsonReader.java backend/src/test/java/com/portfolio/agent/portfolio/domain/PortfolioModelContractTest.java backend/src/test/java/com/portfolio/agent/portfolio/repository/file/PortfolioSnapshotJsonReaderTest.java
git commit -m "feat: normalize portfolio schema versions"
```

### Task 3: Enforce CaseStudy reference integrity

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidator.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidatorTest.java`

**Interfaces:**
- Consumes: `PortfolioSnapshot.getCases()`, `QuestionDefinition.getCaseIds()`, `TimelineEvent.getCaseIds()`, `ClaimSubjectType.CASE`.
- Produces: validation for schema `2.0` and `3.0`, unique Case identities, Case-owned claims, and mixed project/case relations.

- [ ] **Step 1: Add failing validator scenarios**

Add tests that build valid schema `3.0` JSON and mutate one condition per test:

```java
@Test
void acceptsSchemaThreeCaseOwnedContent() {
    assertThatCode(() -> validate(validSchemaThreeJson())).doesNotThrowAnyException();
}

@Test
void rejectsQuestionWithoutProjectOrCase() {
    assertInvalid(questionWithEmptyProjectAndCaseIds(),
            "question must reference at least one project or case");
}

@Test
void rejectsCaseClaimOwnedByAnotherSubject() {
    assertInvalid(caseReferencingProjectClaim(),
            "claim reference belongs to a different case");
}

@Test
void rejectsTimelineWithUnknownCase() {
    assertInvalid(timelineWithCaseId("case-missing"),
            "timeline case reference does not exist: case-missing");
}
```

Also change the old unsupported-version test to reject `4.0`, not `3.0`.

Add one mutation test for each remaining Case rule, with these exact method names and expected message fragments:

```text
rejectsDuplicateCaseId                 -> duplicate case id
rejectsDuplicateCaseCode               -> duplicate case code
rejectsDuplicateCaseSlug               -> duplicate case slug
rejectsIllegalCaseSlug                  -> case slug format is invalid
rejectsBlankCaseRequiredField           -> case title is required
acceptsCaseWithEmptyDecisions           -> no exception
rejectsMissingCaseProject               -> case project reference does not exist
rejectsMissingCaseEvidence              -> case evidence reference does not exist
rejectsMissingCaseTimeline              -> case timeline reference does not exist
rejectsMissingCaseQuestionPreset        -> case question reference does not exist
rejectsCaseAchievementWithoutDirectLink -> achievement claim requires an APPROVED DIRECT link
```

- [ ] **Step 2: Run the validator test and verify failures**

Run: `mvn -f backend/pom.xml -Dtest=PortfolioSnapshotValidatorTest test`

Expected: FAIL because `3.0` is rejected and Case references are not checked.

- [ ] **Step 3: Implement exact validation rules**

Replace the single version constant with:

```java
private static final Set<String> SUPPORTED_SCHEMA_VERSIONS = Set.of("2.0", "3.0");
```

Build `casesById`, `casesBySlug`, and unique code maps. For every Case require nonblank code/slug/title/summary/problem/outcome, valid slug, nonempty actions/verification/limitations/claimIds/evidenceIds/timelineEventIds/questionPresetIds, a nonnull (possibly empty) decisions list, nonnull type/status/contribution, and an existing optional projectId.

For `ClaimSubjectType.CASE`, require `casesById.containsKey(subjectId)`. Every Case claim reference must point to a claim whose `subjectType == CASE` and `subjectId == case.id`.

For questions and timeline events:

```java
private static void requireAssociation(
        List<String> projectIds,
        List<String> caseIds,
        String type
) {
    List<String> requiredProjectIds = requiredList(projectIds, type + " projectIds");
    List<String> requiredCaseIds = requiredList(caseIds, type + " caseIds");
    require(!requiredProjectIds.isEmpty() || !requiredCaseIds.isEmpty(),
            type + " must reference at least one project or case");
    validateNonBlankValues(requiredProjectIds, type + " projectIds");
    validateNonBlankValues(requiredCaseIds, type + " caseIds");
}
```

Call `requireAssociation(question.getProjectIds(), question.getCaseIds(), "question")` and `requireAssociation(event.getProjectIds(), event.getCaseIds(), "timeline")` before checking the referenced IDs.

Validate every referenced Case, evidence, claim, timeline event, and question preset. Keep the existing DIRECT/APPROVED evidence rules unchanged.

- [ ] **Step 4: Run validation and domain suites**

Run: `mvn -f backend/pom.xml -Dtest=PortfolioSnapshotValidatorTest,CaseStudyModelContractTest,PortfolioModelContractTest test`

Expected: PASS.

- [ ] **Step 5: Commit validation**

```powershell
git add backend/src/main/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidator.java backend/src/test/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidatorTest.java
git commit -m "feat: validate CaseStudy references"
```

### Task 4: Load schema 2.0 and 3.0 release bundles

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/domain/BundleCounts.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/repository/file/PublicBundleLoader.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/repository/file/JsonPublicPortfolioRepository.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/domain/ReleaseBundleModelContractTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/repository/file/PublicBundleLoaderTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/repository/file/JsonPublicPortfolioRepositoryTest.java`

**Interfaces:**
- Consumes: `PortfolioSnapshotJsonReader.readBundle(byte[])` and `readLegacyResource(byte[])`.
- Produces: `BundleCounts.cases`, version-aware count matching, and bundle-wide `2.0`/`3.0` consistency.

- [ ] **Step 1: Add failing loader contract tests**

```java
@Test
void loadsLegacySchemaTwoBundleWithZeroCases() {
    RuntimeContentSnapshot loaded = loader.load(validLegacyBundle());
    assertThat(loaded.getSchemaVersion()).isEqualTo("2.0");
    assertThat(loaded.getCases()).isEmpty();
}

@Test
void loadsSchemaThreeBundleWithCasesCount() {
    RuntimeContentSnapshot loaded = loader.load(validSchemaThreeBundle());
    assertThat(loaded.getSchemaVersion()).isEqualTo("3.0");
    assertThat(loaded.getCases()).extracting(CaseStudy::getSlug)
            .containsExactly("multilingual-image-preservation");
}

@Test
void rejectsSchemaThreeManifestWithWrongCasesCount() {
    assertThatThrownBy(() -> loader.load(schemaThreeBundleWithCasesCount(0)))
            .hasMessageContaining("manifest counts mismatch");
}

@Test
void caseByteChangeInvalidatesChecksumAndCandidateHash() {
    Map<String, byte[]> files = new LinkedHashMap<>(validSchemaThreeBundle());
    files.put("portfolio.json", changeCaseOutcome(files.get("portfolio.json")));

    assertThatThrownBy(() -> loader.load(files))
            .hasMessageContaining("checksum mismatch: portfolio.json");
}
```

Add a repository test proving the legacy single-file resource still loads as `2.0` with empty cases.

- [ ] **Step 2: Run and verify failures**

Run: `mvn -f backend/pom.xml -Dtest=PublicBundleLoaderTest,JsonPublicPortfolioRepositoryTest,ReleaseBundleModelContractTest test`

Expected: FAIL because only manifest schema `2.0` is accepted and counts omit cases.

- [ ] **Step 3: Extend counts without weakening legacy validation**

Add `int cases` to `BundleCounts` and include it in equality/hash/matching. Jackson must distinguish missing legacy value from explicit schema `3.0`; therefore deserialize it as `Integer cases`, store `0` when null, and expose:

```java
public int getCases() { return cases; }
```

The loader must additionally require `manifest.counts.cases` to be present for schema `3.0`. Check that presence from the manifest JSON tree before mapping; schema `2.0` may omit it and normalizes to zero.

- [ ] **Step 4: Route all portfolio bytes through the central reader**

Construct one `PortfolioSnapshotJsonReader` from the strict mapper. In `PublicBundleLoader`, accept only `Set.of("2.0", "3.0")`, replace direct `PortfolioSnapshot` mapping and top-level checks with `snapshotReader.readBundle(files.get("portfolio.json"))`, and retain cross-file schema/content/checksum/hash validation.

In `JsonPublicPortfolioRepository`, replace its direct `objectMapper.readValue(inputStream, PortfolioSnapshot.class)` call with `snapshotReader.readLegacyResource(inputStream.readAllBytes())`. `PresentationSnapshot` remains structurally unchanged; loader cross-file equality allows both known schema strings.

- [ ] **Step 5: Run loader and release tests**

Run: `mvn -f backend/pom.xml -Dtest=PublicBundleLoaderTest,JsonPublicPortfolioRepositoryTest,ReleaseBundleModelContractTest,ActiveBundleLocatorTest test`

Expected: PASS for both schema versions; a `3.0` bundle without explicit cases/counts fails.

- [ ] **Step 6: Commit bundle compatibility**

```powershell
git add backend/src/main/java/com/portfolio/agent/portfolio/domain/BundleCounts.java backend/src/main/java/com/portfolio/agent/portfolio/repository/file/PublicBundleLoader.java backend/src/main/java/com/portfolio/agent/portfolio/repository/file/JsonPublicPortfolioRepository.java backend/src/test/java/com/portfolio/agent/portfolio/domain/ReleaseBundleModelContractTest.java backend/src/test/java/com/portfolio/agent/portfolio/repository/file/PublicBundleLoaderTest.java backend/src/test/java/com/portfolio/agent/portfolio/repository/file/JsonPublicPortfolioRepositoryTest.java
git commit -m "feat: load portfolio schema 3 bundles"
```

### Task 5: Add Case service results and approved-evidence aggregation

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/service/result/CaseDetails.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/service/result/PublicContent.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/service/PortfolioService.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/service/PortfolioServiceTest.java`
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/exception/CaseNotFoundException.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/exception/PortfolioErrorCode.java`

**Interfaces:**
- Produces: `List<CaseDetails> getCases()`, `CaseDetails getCase(String slug)`.
- Produces: `PublicContent.getCases()`, `PublicContent.getCaseSlugsByEvidenceId()`.
- Produces: `CaseNotFoundException` with error code `CASE_NOT_FOUND`.

- [ ] **Step 1: Write failing service tests**

```java
@Test
void returnsCaseDetailsWithOnlyApprovedNonRawEvidence() {
    CaseDetails result = service.getCase("multilingual-image-preservation");

    assertThat(result.getCaseStudy().getCode()).isEqualTo("CASE-01");
    assertThat(result.getEvidence()).extracting(EvidenceRecord::getId)
            .containsExactly("evidence-case-multilingual-implementation-and-regression");
    assertThat(result.getSuggestedQuestions())
            .containsExactly("多语言图片上传修复解决了什么问题？");
}

@Test
void unknownCaseUsesStableErrorCode() {
    assertThatThrownBy(() -> service.getCase("missing"))
            .isInstanceOf(CaseNotFoundException.class)
            .extracting("errorCode")
            .isEqualTo(PortfolioErrorCode.CASE_NOT_FOUND);
}

@Test
void publicContentIndexesCaseSlugsByEvidenceId() {
    assertThat(service.getPublicContent().getCaseSlugsByEvidenceId())
            .containsEntry("evidence-case-multilingual-implementation-and-regression",
                    List.of("multilingual-image-preservation"));
}
```

- [ ] **Step 2: Run and verify failures**

Run: `mvn -f backend/pom.xml -Dtest=PortfolioServiceTest test`

Expected: compilation fails for Case service APIs.

- [ ] **Step 3: Implement CaseDetails and service behavior**

`CaseDetails` contains `CaseStudy caseStudy`, immutable `List<EvidenceRecord> evidence`, and immutable `List<String> suggestedQuestions`.

`toCaseDetails` mirrors project filtering but uses `caseStudy.evidenceIds` and matches questions through `question.caseIds`. `getCases` preserves bundle order. `getCase` matches exact slug and throws `new CaseNotFoundException(slug)`.

Extend `getPublicContent` with all Case details and build:

```java
Map<String, List<String>> caseSlugsByEvidenceId = new LinkedHashMap<>();
for (CaseDetails details : cases) {
    for (EvidenceRecord evidenceRecord : details.getEvidence()) {
        caseSlugsByEvidenceId
                .computeIfAbsent(evidenceRecord.getId(), ignored -> new ArrayList<>())
                .add(details.getCaseStudy().getSlug());
    }
}
```

Keep the global evidence filter and Claim–Evidence link authority unchanged.

- [ ] **Step 4: Run service tests**

Run: `mvn -f backend/pom.xml -Dtest=PortfolioServiceTest test`

Expected: PASS, including filtering of non-approved and raw-public evidence.

- [ ] **Step 5: Commit the service unit**

```powershell
git add backend/src/main/java/com/portfolio/agent/portfolio/service/result/CaseDetails.java backend/src/main/java/com/portfolio/agent/portfolio/service/result/PublicContent.java backend/src/main/java/com/portfolio/agent/portfolio/service/PortfolioService.java backend/src/main/java/com/portfolio/agent/portfolio/exception/CaseNotFoundException.java backend/src/main/java/com/portfolio/agent/portfolio/exception/PortfolioErrorCode.java backend/src/test/java/com/portfolio/agent/portfolio/service/PortfolioServiceTest.java
git commit -m "feat: expose CaseStudy service results"
```

### Task 6: Expose Case APIs and public-content relations

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/CaseSummaryResponse.java`
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/CaseDetailResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/PublicContentResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/QuestionPresetResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/TimelineEventResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/mapper/PortfolioResponseMapper.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/controller/PortfolioController.java`
- Create: `backend/src/test/java/com/portfolio/agent/portfolio/controller/CaseControllerTest.java`
- Create: `backend/src/test/java/com/portfolio/agent/portfolio/mapper/PortfolioResponseMapperTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/controller/PortfolioControllerTest.java`

**Interfaces:**
- Produces: `GET /api/v1/cases`, `GET /api/v1/cases/{slug}`.
- Produces: question keeps nullable `projectSlug` and adds `caseSlugs[]`; timeline keeps `projectSlugs[]` and adds `caseSlugs[]`.
- Produces: public-content `cases[]` and `caseSlugsByEvidenceId`.

- [ ] **Step 1: Add failing controller-slice and mapper contract tests**

`CaseControllerTest` uses `@WebMvcTest(PortfolioController.class)`, a mocked `PortfolioService`, and the real `PortfolioResponseMapper`; it does not depend on the still-schema-`2.0` active bundle.

```java
mockMvc.perform(get("/api/v1/cases"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].slug")
                .value("multilingual-image-preservation"));

mockMvc.perform(get("/api/v1/cases/multilingual-image-preservation"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verification[0]")
                .value("先上传德语，再上传法语并查询"))
        .andExpect(jsonPath("$.evidence[0].rawContent").doesNotExist());

mockMvc.perform(get("/api/v1/cases/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
```

In `PortfolioResponseMapperTest`, map a `PublicContent` fixture:

```java
PublicContentResponse response = mapper.toPublicContentResponse(publicContentWithThreeCases());

assertThat(response.getCases()).hasSize(3);
assertThat(response.getCaseSlugsByEvidenceId())
        .containsEntry("evidence-case-role-reset-guide-and-acceptance",
                List.of("test-role-reset"));
assertThat(response.getQuestionPresets())
        .filteredOn(item -> item.getId().equals("question-case-role-reset-overview"))
        .singleElement()
        .satisfies(item -> {
            assertThat(item.getProjectSlug()).isNull();
            assertThat(item.getCaseSlugs()).containsExactly("test-role-reset");
        });
```

Retain existing assertions for `/portfolio` and `/projects/{slug}` to prove no semantic regression.

- [ ] **Step 2: Run and verify failures**

Run: `mvn -f backend/pom.xml -Dtest=CaseControllerTest,PortfolioResponseMapperTest,PortfolioControllerTest test`

Expected: FAIL because Case routes, DTOs, and mapping methods do not exist.

- [ ] **Step 3: Implement Case DTOs and relation-safe mapping**

`CaseSummaryResponse` exposes exactly `slug, code, type, title, summary, achievementStatus, contributionType`.

`CaseDetailResponse` adds exactly `problem, actions, decisions, verification, outcome, limitations, projectSlug, evidence, suggestedQuestions`. Resolve optional `CaseStudy.projectId` to an optional public project slug; do not expose Case/Claim/Evidence relation IDs beyond the already public Evidence DTO.

Keep the existing `QuestionPresetResponse.projectSlug` property and add:

```java
private final List<String> caseSlugs;
```

For a Case-only preset, return `projectSlug: null`; for an existing project preset, preserve the current first matching project slug. Change `TimelineEventResponse` to add `List<String> caseSlugs`. Mapper lookup helpers map every Case ID and every timeline Project ID; validation guarantees all IDs exist.

Add cases and `Map<String, List<String>> caseSlugsByEvidenceId` to `PublicContentResponse`, with defensive copies and value methods.

- [ ] **Step 4: Add controller routes**

```java
@GetMapping("/cases")
public List<CaseSummaryResponse> getCases() {
    return responseMapper.toCaseResponses(portfolioService.getCases());
}

@GetMapping("/cases/{slug}")
public CaseDetailResponse getCase(@PathVariable String slug) {
    return responseMapper.toCaseResponse(portfolioService.getCase(slug));
}
```

- [ ] **Step 5: Run API and service tests**

Run: `mvn -f backend/pom.xml -Dtest=CaseControllerTest,PortfolioResponseMapperTest,PortfolioControllerTest,PortfolioServiceTest test`

Expected: PASS; existing Project endpoints retain their previous payload contract.

- [ ] **Step 6: Commit the API unit**

```powershell
git add backend/src/main/java/com/portfolio/agent/portfolio/dto/response/CaseSummaryResponse.java backend/src/main/java/com/portfolio/agent/portfolio/dto/response/CaseDetailResponse.java backend/src/main/java/com/portfolio/agent/portfolio/dto/response/PublicContentResponse.java backend/src/main/java/com/portfolio/agent/portfolio/dto/response/QuestionPresetResponse.java backend/src/main/java/com/portfolio/agent/portfolio/dto/response/TimelineEventResponse.java backend/src/main/java/com/portfolio/agent/portfolio/mapper/PortfolioResponseMapper.java backend/src/main/java/com/portfolio/agent/portfolio/controller/PortfolioController.java backend/src/test/java/com/portfolio/agent/portfolio/controller/CaseControllerTest.java backend/src/test/java/com/portfolio/agent/portfolio/mapper/PortfolioResponseMapperTest.java backend/src/test/java/com/portfolio/agent/portfolio/controller/PortfolioControllerTest.java
git commit -m "feat: add public CaseStudy APIs"
```

### Task 7: Protect the deferred frontend and Agent boundary

**Files:**
- Modify only where constructor compilation requires it:
  - `backend/src/test/java/com/portfolio/agent/answer/adapter/portfolio/LocalPortfolioKnowledgeAdapterTest.java`
  - other backend tests returned by the compiler
- Do not modify: `backend/src/main/java/com/portfolio/agent/answer/**`
- Do not modify: `frontend/**`

**Interfaces:**
- Consumes: new constructor parameters from Tasks 1–4.
- Produces: proof that Case data is not added to Agent/retrieval behavior.

- [ ] **Step 1: Run the complete backend test compilation**

Run: `mvn -f backend/pom.xml test`

Expected: compilation failures only at direct constructor fixtures that now require `cases` or `caseIds`; no production Answer code should require changes.

- [ ] **Step 2: Update fixtures with explicit empty Case collections**

For every existing Agent/retrieval test fixture, pass `List.of()` for `cases` and `caseIds`. Do not add Case claims, questions, or content to any Answer fixture.

Add one assertion to `LocalPortfolioKnowledgeAdapterTest` proving its existing project question catalog remains unchanged when the snapshot contains a Case-only question:

```java
assertThat(adapter.listSuggestedQuestions())
        .doesNotContain("测试角色清理功能解决了什么问题？");
```

- [ ] **Step 3: Run backend and frontend boundary verification**

Run: `mvn -f backend/pom.xml test`

Expected: all backend tests pass.

Run: `git diff --exit-code -- frontend`

Expected: exit code 0 and no output.

- [ ] **Step 4: Commit only fixture compatibility changes**

```powershell
git add backend/src/test/java
git diff --cached --name-only
git commit -m "test: preserve Agent boundary for CaseStudy"
```

Before committing, expected staged names contain only backend test files from this task; if unrelated test files appear, unstage them.

### Task 8: Upgrade the private governance pipeline for schema 3.0

**Files:**
- Modify: `scripts/portfolio-governance.test.ps1`
- Modify locally but never stage: `.agents/skills/portfolio-governance/scripts/portfolio-governance.ps1`
- Modify locally but never stage: the schema/validation helpers used by that installed skill

**Interfaces:**
- Produces: governance commands that accept legacy `2.0`, generate strict `3.0`, count cases, include Case relations in privacy/reference validation, and bind approval to exact bytes.
- Preserves: tracked entry point `scripts/portfolio-governance.ps1`.

- [ ] **Step 1: Confirm the installed governance dependency and record its resolved location**

Run: `cli-find portfolio-governance`

Expected: if the internal registry has no entry, use the existing tracked wrapper and read the resolved script path from its error/output. Confirm the local implementation is under `.agents/skills/portfolio-governance` and ignored by Git:

```powershell
$sourceSkill = 'D:\code\agent\.agents\skills\portfolio-governance'
$targetSkill = Join-Path (Resolve-Path '.').Path '.agents\skills\portfolio-governance'
if (-not (Test-Path -LiteralPath $targetSkill)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $targetSkill -Parent) | Out-Null
    Copy-Item -LiteralPath $sourceSkill -Destination $targetSkill -Recurse
}
git check-ignore .agents/skills/portfolio-governance/scripts/portfolio-governance.ps1
```

Expected: the path is printed.

- [ ] **Step 2: Write failing tracked governance tests**

Extend `scripts/portfolio-governance.test.ps1` with isolated temporary workspaces that assert:

```powershell
$legacy = New-Candidate 'schema-two-legacy'
$legacyResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
    '-Candidate', $legacy)
if ($legacyResult.ExitCode -ne 0) {
    throw "Schema 2.0 candidate must normalize to zero cases: $($legacyResult.Output)"
}

$schemaThree = New-Candidate 'schema-three'
$schemaThreeData = Get-Content -LiteralPath (Join-Path $schemaThree 'portfolio.json') `
    -Raw -Encoding UTF8 | ConvertFrom-Json
$schemaThreeData.schemaVersion = '3.0'
$schemaThreeData | Add-Member -NotePropertyName cases -NotePropertyValue @()
$schemaThreeData.questionPresets | ForEach-Object {
    $_ | Add-Member -NotePropertyName caseIds -NotePropertyValue @()
}
$schemaThreeData.timelineEvents | ForEach-Object {
    $_ | Add-Member -NotePropertyName caseIds -NotePropertyValue @()
}
$schemaThreeData | ConvertTo-Json -Depth 30 |
    Set-Content -LiteralPath (Join-Path $schemaThree 'portfolio.json') -Encoding UTF8
$schemaThreeResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
    '-Candidate', $schemaThree)
if ($schemaThreeResult.ExitCode -ne 0) {
    throw "Schema 3.0 candidate with explicit empty cases must pass: $($schemaThreeResult.Output)"
}

$missingCases = New-Candidate 'schema-three-missing-cases'
$missingCasesData = Get-Content -LiteralPath (Join-Path $missingCases 'portfolio.json') `
    -Raw -Encoding UTF8 | ConvertFrom-Json
$missingCasesData.schemaVersion = '3.0'
$missingCasesData | ConvertTo-Json -Depth 30 |
    Set-Content -LiteralPath (Join-Path $missingCases 'portfolio.json') -Encoding UTF8
$missingCasesResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
    '-Candidate', $missingCases)
if ($missingCasesResult.ExitCode -eq 0 -or
        -not $missingCasesResult.Output.Contains('SCHEMA_CASES_REQUIRED')) {
    throw 'Schema 3.0 must require cases.'
}

$danglingCase = New-Candidate 'dangling-case'
$danglingCaseData = Get-Content -LiteralPath (Join-Path $danglingCase 'portfolio.json') `
    -Raw -Encoding UTF8 | ConvertFrom-Json
$danglingCaseData.schemaVersion = '3.0'
$danglingCaseData | Add-Member -NotePropertyName cases -NotePropertyValue @()
$danglingCaseData.questionPresets | ForEach-Object {
    $_ | Add-Member -NotePropertyName caseIds -NotePropertyValue @('case-missing')
}
$danglingCaseData.timelineEvents | ForEach-Object {
    $_ | Add-Member -NotePropertyName caseIds -NotePropertyValue @()
}
$danglingCaseData | ConvertTo-Json -Depth 30 |
    Set-Content -LiteralPath (Join-Path $danglingCase 'portfolio.json') -Encoding UTF8
$danglingCaseResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
    '-Candidate', $danglingCase)
if ($danglingCaseResult.ExitCode -eq 0 -or
        -not $danglingCaseResult.Output.Contains('REFERENCE_DANGLING_CASE')) {
    throw 'Unknown Case reference must fail ReferenceIntegrityGate.'
}

$metricLeak = New-Candidate 'codegraph-metric-leak'
$metricLeakData = Get-Content -LiteralPath (Join-Path $metricLeak 'portfolio.json') `
    -Raw -Encoding UTF8 | ConvertFrom-Json
$metricLeakData.owner.summary = 'CodeGraph 大场景节省 28.2%'
$metricLeakData | ConvertTo-Json -Depth 30 |
    Set-Content -LiteralPath (Join-Path $metricLeak 'portfolio.json') -Encoding UTF8
$metricLeakResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
    '-Candidate', $metricLeak)
if ($metricLeakResult.ExitCode -eq 0 -or
        -not $metricLeakResult.Output.Contains('PRIVACY_CONTENT_REJECTED')) {
    throw 'Forbidden exact CodeGraph metrics must fail PrivacyGate.'
}

$review = Invoke-Governance @('-Command', 'build-review-pack', '-Workspace', $workspace,
    '-Candidate', $schemaThree)
if ($review.ExitCode -ne 0) { throw "Schema 3.0 review failed: $($review.Output)" }
$reviewResult = $review.Output | ConvertFrom-Json
$reviewPack = Join-Path $workspace $reviewResult.artifacts[1]
$reviewSummary = Get-Content -LiteralPath (Join-Path $reviewPack 'summary.json') `
    -Raw -Encoding UTF8 | ConvertFrom-Json
if ($reviewSummary.counts.cases -ne 0) {
    throw 'Review output must include the Case count.'
}
```

The CodeGraph fixture must contain `28.2%` and expect validation failure. The accepted fixture uses “在大场景中减少无关上下文，但需要人工复核答案质量”.

- [ ] **Step 3: Run governance tests and verify the red state**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.test.ps1`

Expected: FAIL on missing schema `3.0`/Case handling.

- [ ] **Step 4: Implement the minimum private-tool changes**

Update the installed skill implementation to:

- accept schema versions `2.0` and `3.0`;
- inject empty `cases`/`caseIds` for legacy reads only;
- require explicit `cases`, `caseIds`, and `counts.cases` for `3.0`;
- validate all Case claim/evidence/timeline/question references;
- include cases and `caseSlugsByEvidenceId`-relevant public changes in review output;
- scan every Case text field for private IPs, local paths, credentials, internal hostnames, raw evidence, and forbidden exact CodeGraph metrics;
- reject email addresses, non-allowlisted URLs, raw SQL fragments, and private source names; the only explicitly approved external profile URL is `https://blog.csdn.net/2301_81073317`;
- compute candidate hash after canonical bytes are finalized;
- reject approval/publish when approval hash differs from candidate hash.

Do not add an auto-approval branch.

- [ ] **Step 5: Run governance regression tests**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.test.ps1`

Expected: PASS.

- [ ] **Step 6: Stage only the tracked test**

```powershell
git add scripts/portfolio-governance.test.ps1
git status --short
git commit -m "test: cover CaseStudy governance rules"
```

Expected: `.agents/` does not appear in staged or committed files.

### Task 9: Build the exact schema 3.0 candidate from approved knowledge

**Files:**
- Create only in the private governance workspace: candidate authoring inputs and review-pack output.
- Do not modify tracked bundle files in this task.

**Interfaces:**
- Consumes: approved local knowledge assets and governance tool from Task 8.
- Produces: an unapproved exact candidate payload and review pack under the private workspace `C:\Users\WIN10\Documents\杂项\实习学习-Obsidian\agent_docs_staging\portfolio-governance`.

- [ ] **Step 1: Inspect the active private workspace**

Run:

```powershell
$workspace = 'C:\Users\WIN10\Documents\杂项\实习学习-Obsidian\agent_docs_staging\portfolio-governance'
$candidate = Join-Path $workspace 'candidates\2026-07-23-case-study-v1'
New-Item -ItemType Directory -Force -Path $candidate | Out-Null
Copy-Item -LiteralPath 'backend\src\main\resources\public-data\bundle\portfolio.json' `
    -Destination $candidate
Copy-Item -LiteralPath 'backend\src\main\resources\public-data\bundle\presentation.json' `
    -Destination $candidate
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.ps1 `
    -Command inspect -Workspace $workspace
```

Expected: command reports workspace, active content version, schema, approval status, and tracked public bundle target without exposing private raw content.

- [ ] **Step 2: Author the SQL-audit July expansion**

Set both candidate files to `schemaVersion: "3.0"` and `contentVersion: "2026-07-23.1"`. Add `cases: []` before populating the Case records. Add explicit `caseIds: []` to every existing project question and timeline event; Case-only questions use their Case ID, `placements: ["CASE"]`, and remain absent from Agent fixtures and retrieval inputs.

Merge the already verified expansion with these stable IDs:

```text
Claims:
claim-sql-audit-fixed-string-search
claim-sql-audit-source-selection
claim-sql-audit-partial-success
claim-sql-audit-selected-target-check
Evidence:
sql-audit-july-iteration-set
Timeline:
timeline-sql-audit-july-hardening
Questions:
question-sql-audit-negative-input
question-sql-audit-partial-success
```

Keep `sourceCount: 2`, `SupportType: DIRECT`, `ReviewStatus: APPROVED`, and the existing SQL-audit project subject ID. Do not include internal IPs, server paths, operators, or raw SQL content.

- [ ] **Step 3: Author the three CaseStudy records**

Use these exact public identities:

```text
CASE-01 / case-multilingual-upload / multilingual-image-preservation / FEATURE / DELIVERED
CASE-02 / case-role-reset / test-role-reset / FEATURE / DELIVERED
CASE-03 / case-codegraph-evaluation / codegraph-evaluation / EVALUATION / PROTOTYPE
```

Bind these exact references:

```text
case-multilingual-upload
  claims: claim-case-multilingual-preserve-existing, claim-case-multilingual-sequential-verification
  evidence: evidence-case-multilingual-implementation-and-regression
  timeline: timeline-case-multilingual-delivery
  question: question-case-multilingual-overview

case-role-reset
  claims: claim-case-role-reset-controlled-flow, claim-case-role-reset-acceptance
  evidence: evidence-case-role-reset-guide-and-acceptance
  timeline: timeline-case-role-reset-delivery
  question: question-case-role-reset-overview

case-codegraph-evaluation
  claims: claim-case-codegraph-narrowing, claim-case-codegraph-failure-boundary, claim-case-codegraph-combined-workflow
  evidence: evidence-case-codegraph-report-collection
  timeline: timeline-case-codegraph-evaluation
  question: question-case-codegraph-overview
```

The multilingual verification states the sequence DE upload → DE query → FR upload → DE+FR query. The role-reset outcome states frequent tester use for fresh-account creation without identifying people or systems. CodeGraph text is qualitative only and its limitations explicitly require manual answer-quality review.

- [ ] **Step 4: Validate and benchmark the candidate**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.ps1 `
    -Command validate -Workspace $workspace -Candidate $candidate
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.ps1 `
    -Command benchmark -Workspace $workspace -Candidate $candidate
```

Expected: validation passes schema/reference/privacy rules; benchmark passes its existing quality threshold and prints no forbidden quantitative CodeGraph result.

- [ ] **Step 5: Build the review pack**

Run:

```powershell
$reviewJson = powershell -NoProfile -ExecutionPolicy Bypass `
    -File scripts/portfolio-governance.ps1 -Command build-review-pack `
    -Workspace $workspace -Candidate $candidate
if ($LASTEXITCODE -ne 0) { throw 'build-review-pack failed' }
$review = $reviewJson | ConvertFrom-Json
$review | ConvertTo-Json -Depth 20 |
    Set-Content -LiteralPath (Join-Path $workspace 'case-study-review-handoff.json') `
        -Encoding UTF8
```

Expected: review pack reports schema `3.0`, one SQL-audit expansion, three Cases, all added claim/evidence/timeline/question IDs, candidate payload hash, and no approval.

- [ ] **Step 6: Present the exact public diff and stop**

Show the user:

- candidate content version and payload hash;
- exact added/changed JSON paths;
- all public claim statements, evidence summaries, outcomes, and limitations;
- the absence of exact CodeGraph metrics, internal locations, and raw evidence;
- the privacy scan result;
- the fact that frontend, Agent, and deployment are unchanged.

Do not run `approve` or `publish` until the user explicitly approves this exact candidate.

### Task 10: Approve, publish, and install the exact bundle

**Files:**
- Modify after explicit approval:
  - `backend/src/main/resources/public-data/bundle/portfolio.json`
  - `backend/src/main/resources/public-data/bundle/presentation.json`
  - `backend/src/main/resources/public-data/bundle/manifest.json`
  - `backend/src/main/resources/public-data/bundle/checksums.json`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/controller/PortfolioControllerTest.java`

**Interfaces:**
- Consumes: exact candidate hash accepted by the user in Task 9 and private `case-study-review-handoff.json`.
- Produces: approved schema `3.0` runtime bundle.

- [ ] **Step 1: Bind human approval to the exact candidate**

After explicit user confirmation, run the governance `approve` command with the candidate identifier/hash reported by the tool.

```powershell
$workspace = 'C:\Users\WIN10\Documents\杂项\实习学习-Obsidian\agent_docs_staging\portfolio-governance'
$candidate = Join-Path $workspace 'candidates\2026-07-23-case-study-v1'
$releaseRoot = Join-Path $workspace 'public-releases'
$review = Get-Content -LiteralPath (Join-Path $workspace 'case-study-review-handoff.json') `
    -Raw -Encoding UTF8 | ConvertFrom-Json
$approvalJson = powershell -NoProfile -ExecutionPolicy Bypass `
    -File scripts/portfolio-governance.ps1 -Command approve `
    -Workspace $workspace -Candidate $candidate -ReviewRunId $review.runId `
    -ApprovedBy 'portfolio-owner' -PrivacyReviewId ('PRIV-' + $review.runId) `
    -BenchmarkRunId ('BENCH-' + $review.runId)
if ($LASTEXITCODE -ne 0) { throw 'approve failed' }
$approval = $approvalJson | ConvertFrom-Json
```

Expected: approval record contains the same candidate payload hash. If the candidate bytes changed, approval must fail and Task 9 review repeats.

- [ ] **Step 2: Dry-run publication**

Run the tool’s publish command in dry-run mode.

```powershell
$approvalData = Get-Content -LiteralPath (Join-Path $workspace $approval.artifacts[-1]) `
    -Raw -Encoding UTF8 | ConvertFrom-Json
powershell -NoProfile -ExecutionPolicy Bypass `
    -File scripts/portfolio-governance.ps1 -Command publish `
    -Workspace $workspace -Candidate $candidate `
    -ApprovalId $approvalData.approvalId -ReleaseRoot $releaseRoot
```

Expected: output lists only the four bundle files, exact destination, content version, candidate hash, and approval ID; no files change.

- [ ] **Step 3: Publish with explicit confirmation**

Run the same publish command with its required confirmation flag.

```powershell
$publishJson = powershell -NoProfile -ExecutionPolicy Bypass `
    -File scripts/portfolio-governance.ps1 -Command publish `
    -Workspace $workspace -Candidate $candidate `
    -ApprovalId $approvalData.approvalId -ReleaseRoot $releaseRoot -Confirm
if ($LASTEXITCODE -ne 0) { throw 'publish failed' }
$publish = $publishJson | ConvertFrom-Json
$contentVersion = $publish.contentVersion
```

Expected: private release workspace marks the version published and reports immutable hashes.

- [ ] **Step 4: Copy the exact published bytes into the tracked bundle**

Use `Copy-Item -LiteralPath` for each of the four published files. Do not reserialize or hand-edit the JSON after approval.

```powershell
$publishedRoot = Join-Path $releaseRoot ('versions\' + $contentVersion)
$trackedRoot = Join-Path (Resolve-Path '.').Path 'backend\src\main\resources\public-data\bundle'
foreach ($name in @('portfolio.json', 'presentation.json', 'manifest.json', 'checksums.json')) {
    Copy-Item -LiteralPath (Join-Path $publishedRoot $name) `
        -Destination (Join-Path $trackedRoot $name)
}
```

- [ ] **Step 5: Verify byte identity**

Compare SHA-256 for each private published file and tracked bundle file:

```powershell
$repositoryRoot = (Resolve-Path '.').Path
$trackedRoot = Join-Path $repositoryRoot 'backend\src\main\resources\public-data\bundle'
$publishedRoot = Join-Path $releaseRoot ('versions\' + $contentVersion)
foreach ($name in @('portfolio.json', 'presentation.json', 'manifest.json', 'checksums.json')) {
    $publishedHash = (Get-FileHash (Join-Path $publishedRoot $name) -Algorithm SHA256).Hash
    $trackedHash = (Get-FileHash (Join-Path $trackedRoot $name) -Algorithm SHA256).Hash
    if ($publishedHash -ne $trackedHash) {
        throw "Published and tracked bytes differ: $name"
    }
}
```

Expected: every pair matches exactly.

- [ ] **Step 6: Run governance and loader verification**

Update the full-context integration test now that the active bundle is schema `3.0`:

```java
mockMvc.perform(get("/api/v1/public-content"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contentVersion").value("2026-07-23.1"))
        .andExpect(jsonPath("$.cases.length()").value(3))
        .andExpect(jsonPath("$.claims.length()").value(16))
        .andExpect(jsonPath("$.claimEvidenceLinks.length()").value(16))
        .andExpect(jsonPath("$.evidence.length()").value(5))
        .andExpect(jsonPath("$.timeline.length()").value(5))
        .andExpect(jsonPath("$.questionPresets.length()").value(6))
        .andExpect(jsonPath("$.caseSlugsByEvidenceId['evidence-case-role-reset-guide-and-acceptance'][0]")
                .value("test-role-reset"))
        .andExpect(jsonPath("$.questionPresets[?(@.id=='question-case-role-reset-overview')].caseSlugs[0]")
                .value("test-role-reset"));
```

Update the existing SQL Project expectations to two approved evidence records and three suggested questions. Preserve all prior IDs and assertions; only counts changed by this approved content expansion should move.

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.ps1 `
    -Command verify -Workspace $workspace -ReleaseRoot $releaseRoot `
    -TargetVersion $contentVersion
mvn -f backend/pom.xml -Dtest=PublicBundleLoaderTest,PortfolioControllerTest,PortfolioServiceTest test
```

Expected: PASS; loaded schema is `3.0`, Case count is `3`, and runtime bundle hash matches manifest/checksum bytes.

- [ ] **Step 7: Commit the approved bundle**

```powershell
git add backend/src/main/resources/public-data/bundle/portfolio.json backend/src/main/resources/public-data/bundle/presentation.json backend/src/main/resources/public-data/bundle/manifest.json backend/src/main/resources/public-data/bundle/checksums.json backend/src/test/java/com/portfolio/agent/portfolio/controller/PortfolioControllerTest.java
git commit -m "content: publish verified CaseStudy portfolio"
```

### Task 11: Update project status documentation

**Files:**
- Modify: `docs/00-文档状态索引.md`
- Modify: `docs/08-current-implementation-status.md`
- Modify: `docs/09-portfolio-asset-library-status.md`
- Modify: `docs/superpowers/specs/2026-07-23-portfolio-case-study-public-contract-design.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: verified implementation and approved bundle.
- Produces: accurate current/remaining status without claiming frontend, Agent Case execution, or deployment.

- [ ] **Step 1: Add precise status statements**

Document:

```text
已完成：
- Portfolio schema 3.0 与 2.0 兼容加载
- 独立 CaseStudy 领域模型、校验、服务与公开 API
- 多语言图片保留、测试角色清理、CodeGraph 评测三个案例
- SQL 政审排查 2026-07 能力扩展

尚未完成：
- CaseStudy 前端列表/详情页与视觉设计
- Agent 对 CaseStudy 的检索、上下文组装与可执行问题预设
- 生产部署与线上验收
```

README API section must list `/api/v1/cases`, `/api/v1/cases/{slug}`, and the new public-content fields without suggesting a frontend route exists.

- [ ] **Step 2: Verify documentation claims against code**

Run:

```powershell
rg -n "CaseStudy|/api/v1/cases|schema 3.0|尚未完成" README.md docs/00-文档状态索引.md docs/08-current-implementation-status.md docs/09-portfolio-asset-library-status.md docs/superpowers/specs/2026-07-23-portfolio-case-study-public-contract-design.md
git diff -- frontend
```

Expected: all status claims have matching implementation; frontend diff is empty.

- [ ] **Step 3: Commit documentation**

```powershell
git add README.md docs/00-文档状态索引.md docs/08-current-implementation-status.md docs/09-portfolio-asset-library-status.md docs/superpowers/specs/2026-07-23-portfolio-case-study-public-contract-design.md
git commit -m "docs: update CaseStudy implementation status"
```

### Task 12: Full verification and merge-readiness audit

**Files:**
- No new files.
- Inspect all changed files and Git state.

**Interfaces:**
- Produces: evidence that the branch is safe to merge; does not merge, push, or deploy.

- [ ] **Step 1: Run the tracked governance and static-bundle suites**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-static-bundle.test.ps1
```

Expected: both PASS.

- [ ] **Step 2: Run the full backend suite**

Run: `mvn -f backend/pom.xml clean test`

Expected: all tests PASS with zero failures and zero errors.

- [ ] **Step 3: Run the mandatory privacy and packaged-release gates**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 `
    -Path backend/src/main
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-release.ps1 `
    -SkipInstall -SkipDockerCheck
```

Expected: privacy scan passes; clean package, JAR content scan, static-bundle verification, packaged-JAR startup, API Playwright integration, and all release checks pass. `-SkipDockerCheck` does not deploy or build a container.

- [ ] **Step 4: Run the untouched frontend suite**

```powershell
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
```

Expected: existing frontend tests and build PASS.

- [ ] **Step 5: Prove the scope boundary**

```powershell
git diff --exit-code master...HEAD -- frontend
git diff --name-only master...HEAD
git status --short
```

Expected:

- no frontend changes;
- no `.agents`, private knowledge-base, review-pack, internal address, raw evidence, or credential files;
- only Case domain/API/tests, governance test, approved public bundle, and status docs;
- no unrelated user-owned files.

- [ ] **Step 6: Run forbidden-metric scans**

```powershell
rg -n "192\\.168\\.|localhost:9207|D:\\\\code|C:\\\\Users|28\\.2%|4\\.3%|44608|158318|113710" backend/src/main/resources/public-data/bundle README.md docs/00-文档状态索引.md docs/08-current-implementation-status.md docs/09-portfolio-asset-library-status.md
```

Expected: no matches. If a match appears, do not amend the approved bytes directly; rebuild and reapprove the candidate.

- [ ] **Step 7: Record final evidence**

Capture commit IDs, test counts, schema/content version, runtime bundle hash, approval ID, and the exact statement that merge/push/deploy have not been performed. Hand this evidence to the user for the next explicit merge/push decision.
