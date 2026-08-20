# Project–Case Backend Implementation Plan
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

> **For Codex:** Execute this plan inline with the `executing-plans` and `test-driven-development` skills. Do not stage or commit changes.

**Goal:** Upgrade the public portfolio backend to schema 4.0 so five real Projects form the overview layer, 49 Cases form the drill-down/search layer, and three former theme Projects become non-subject Case Collections.

**Architecture:** Extend the immutable portfolio domain, normalize schema 2.0/3.0 at the JSON boundary, validate schema 4.0 fail-closed, and derive Project case projections in `PortfolioService`. Keep existing read-only endpoints; add fields to their DTOs rather than introducing server-side search. Migrate the reviewed public bundle and rebuild its retrieval/checksum artifacts so startup remains deterministic.

**Tech Stack:** Java 21, Spring Boot, Jackson, JUnit 5, MockMvc, Maven, PowerShell release scripts.

---

## Task 1: Add schema 4.0 domain vocabulary

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/domain/CareerTrack.java`
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/domain/ProjectNature.java`
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/domain/ProjectDisplayTier.java`
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/domain/CaseCollection.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/domain/AchievementStatus.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/domain/ProjectProfile.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/domain/CaseStudy.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/domain/PortfolioSnapshot.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/domain/RuntimeContentSnapshot.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/domain/PortfolioModelContractTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/domain/CaseStudyModelContractTest.java`

**Step 1: Write failing model contract tests**

Add assertions that:

```java
assertEquals(CareerTrack.JAVA_BACKEND, project.getCareerTrack());
assertEquals(ProjectNature.TOOL, project.getProjectNature());
assertEquals(ProjectDisplayTier.PRIMARY, project.getDisplayTier());
assertEquals(List.of("case-1"), project.getFeaturedCaseIds());
assertEquals(List.of("collection-1"), caseStudy.getCollectionIds());
assertEquals(List.of(collection), snapshot.getCollections());
assertEquals(AchievementStatus.INVESTIGATED,
        AchievementStatus.valueOf("INVESTIGATED"));
```

Also prove all new lists are defensive immutable copies.

**Step 2: Run the focused tests and confirm RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioModelContractTest,CaseStudyModelContractTest test
```

Expected: compilation fails because the new types and accessors do not exist.

**Step 3: Implement the immutable model**

- Add enums:
  - `CareerTrack`: `JAVA_BACKEND`, `AGENT`, `UNCLASSIFIED`
  - `ProjectNature`: `TOOL`, `WORKSTREAM`, `INTEGRATION_PROTOTYPE`, `UNCLASSIFIED`
  - `ProjectDisplayTier`: `PRIMARY`, `SECONDARY`
- Add `INVESTIGATED` to `AchievementStatus`.
- Add `CaseCollection` with `id`, `slug`, `title`, `summary`, `displayOrder`, explicit constructor/getters/equality.
- Add Project classification fields and ordered `featuredCaseIds`.
- Add Case `collectionIds`.
- Add Portfolio/Runtime Snapshot `collections`.
- Keep package-private/public compatibility constructors for existing test fixtures, defaulting old Projects to `UNCLASSIFIED`, old Case collections to empty, and old Snapshot collections to empty. Jackson’s `@JsonCreator` constructor must expose all schema 4.0 fields.

**Step 4: Run focused tests and confirm GREEN**

Run the same Maven command. Expected: PASS.

## Task 2: Normalize schema 2.0/3.0 and read schema 4.0

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/repository/file/PortfolioSnapshotJsonReader.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/repository/file/PortfolioSnapshotJsonReaderTest.java`

**Step 1: Write failing reader tests**

Cover:

1. schema 2.0 → empty `cases`, empty `collections`, Project classification `UNCLASSIFIED`
2. schema 3.0 → existing Cases, each with empty `collectionIds`, empty `collections`, Project classification `UNCLASSIFIED`
3. schema 4.0 → explicit `collections`, Project metadata, featured cases, Case collections
4. schema 4.0 missing a canonical top-level field → rejected
5. unknown schema 5.0 → rejected

**Step 2: Run and confirm RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioSnapshotJsonReaderTest test
```

**Step 3: Implement boundary normalization**

- Canonical schema 4.0 fields include `collections`.
- For schema 2.0/3.0 mutate the parsed tree before strict binding:
  - `collections: []`
  - Project `careerTrack: UNCLASSIFIED`
  - Project `projectNature: UNCLASSIFIED`
  - Project `displayTier: PRIMARY`
  - Project `featuredCaseIds: []`
  - Case `collectionIds: []`
- Schema 4.0 receives no defaults; required/missing fields fail strict/canonical checks.
- Preserve the legacy-resource path as schema 2.0 normalization.

**Step 4: Run and confirm GREEN**

Run the focused reader test.

## Task 3: Enforce schema 4.0 integrity fail-closed

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidator.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidatorTest.java`

**Step 1: Add failing validation tests**

Add one focused test per invariant:

- accepts schema 4.0 with complete data
- rejects duplicate Collection id/slug
- rejects blank Collection fields and invalid display order
- rejects dangling/duplicate Case `collectionIds`
- rejects `UNCLASSIFIED` Project metadata in schema 4.0
- rejects missing Project display tier
- rejects duplicate featured cases
- rejects more than six featured cases
- rejects a featured Case assigned to a different Project
- permits zero featured cases and zero Project cases
- permits `INVESTIGATED` without upgrading verification status

**Step 2: Run and confirm RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioSnapshotValidatorTest test
```

**Step 3: Implement validation**

- Accept `2.0`, `3.0`, `4.0`.
- Validate Collection uniqueness and slug format.
- Validate every Case collection reference and reject duplicates.
- Validate featured case count `<= 6`, uniqueness, existence, and ownership.
- For schema 4.0 require non-null, non-`UNCLASSIFIED` Project classifications.
- Leave `INVESTIGATED` outside achievement statuses that require direct evidence.
- Preserve all existing claim/evidence/privacy constraints.

**Step 4: Run and confirm GREEN**

Run the focused validator test.

## Task 4: Derive Project and Case relations in the service

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/service/result/ProjectDetails.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/service/result/CaseDetails.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/service/result/PortfolioOverview.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/service/result/PublicContent.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/service/PortfolioService.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/service/PortfolioServiceTest.java`

**Step 1: Write failing service tests**

Assert:

```java
assertEquals(24, details.getCaseCount());
assertEquals(
        List.of("k-10-knowledge", "a-01-incident", "a-05-incident"),
        details.getFeaturedCases().stream().map(CaseStudy::getSlug).toList());
assertNull(standalone.getProjectSlug());
assertEquals(List.of("engineering-operations"), standalone.getCollectionSlugs());
assertEquals(3, overview.getCollections().size());
```

Also test that a Project with zero cases returns `caseCount == 0` and no featured cases.

**Step 2: Run and confirm RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioServiceTest test
```

**Step 3: Implement projections**

- `ProjectDetails` carries derived `caseCount` and ordered featured `CaseStudy` values.
- `CaseDetails` carries `projectSlug` and ordered `collectionSlugs`.
- Overview/PublicContent carry Collections.
- Resolve relations only from the already validated snapshot. Missing relations are impossible after validation and may throw `IllegalStateException` if violated internally.
- Do not add filtering endpoints.

**Step 4: Run and confirm GREEN**

Run the focused service test.

## Task 5: Extend public API DTOs without removing fields

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/CaseCollectionResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/ProjectSummaryResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/ProjectDetailResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/CaseSummaryResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/CaseDetailResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/PortfolioHomeResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/dto/response/PublicContentResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/mapper/PortfolioResponseMapper.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/mapper/PortfolioResponseMapperTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/controller/PortfolioControllerTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/controller/CaseControllerTest.java`

**Step 1: Write failing mapper/controller tests**

Verify JSON contracts:

```json
{
  "careerTrack": "JAVA_BACKEND",
  "projectNature": "TOOL",
  "displayTier": "PRIMARY",
  "caseCount": 2,
  "featuredCases": [],
  "projectSlug": null,
  "collectionSlugs": ["engineering-operations"],
  "collections": [{"slug": "engineering-operations"}]
}
```

Confirm removed Project slugs still receive the existing unified 404 response.

**Step 2: Run and confirm RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PortfolioResponseMapperTest,PortfolioControllerTest,CaseControllerTest test
```

**Step 3: Implement DTO/mapper changes**

- Project summary adds classification plus derived `caseCount`.
- Project detail adds the same classification, `caseCount`, and `featuredCases`.
- Case summary/detail add `projectSlug` with `ALWAYS` null inclusion and `collectionSlugs`.
- Portfolio home and public content add ordered Collections.
- Keep every existing response field.

**Step 4: Run and confirm GREEN**

Run the focused tests.

## Task 6: Include Collection terms in Case retrieval, never as subjects

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/release/ClaimRagDocumentBuilder.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/release/RetrievalBundleCompiler.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/portfolio/LocalPortfolioKnowledgeAdapter.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/release/ClaimRagDocumentBuilderTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/release/RetrievalBundleCompilerTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/adapter/portfolio/LocalPortfolioKnowledgeAdapterTest.java`

**Step 1: Add failing retrieval tests**

- A Collection title/slug is indexed as metadata/search text for its Cases.
- Searching a Collection term can surface a Case/Case Claim.
- No RAG document has `subjectType = COLLECTION`.
- No Agent tool accepts a Collection as the primary subject.

**Step 2: Run and confirm RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ClaimRagDocumentBuilderTest,RetrievalBundleCompilerTest,LocalPortfolioKnowledgeAdapterTest test
```

**Step 3: Implement Collection enrichment**

Resolve each Case’s Collection titles/slugs from the validated snapshot and append them to Case-oriented retrieval metadata/text only. Do not add a Claim subject enum or Collection lookup tool.

**Step 4: Run and confirm GREEN**

Run the focused tests.

## Task 7: Migrate the reviewed public portfolio to schema 4.0

**Files:**
- Modify: `backend/src/main/resources/public-data/bundle/portfolio.json`
- Modify/regenerate: `backend/src/main/resources/public-data/bundle/presentation.json`
- Modify/regenerate: `backend/src/main/resources/public-data/bundle/rag-documents.jsonl`
- Modify/regenerate: `backend/src/main/resources/public-data/bundle/keyword-index.json`
- Modify/regenerate: `backend/src/main/resources/public-data/bundle/vector-index.bin`
- Modify/regenerate: `backend/src/main/resources/public-data/bundle/manifest.json`
- Modify/regenerate: `backend/src/main/resources/public-data/bundle/checksums.json`
- Modify as required: `backend/src/test/resources/retrieval-benchmark/cases.json`
- Test: `backend/src/test/java/com/portfolio/agent/portfolio/repository/file/PublicBundleLoaderTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/release/RetrievalBenchmarkTest.java`

**Step 1: Add failing bundle assertions**

Assert the released bundle has:

- schema `4.0`
- exactly five Projects in approved order
- exactly three Collections
- exactly 49 Cases
- exactly three independent Cases after relation migration
- correct Project classification and featured IDs
- CASE-22…CASE-36 and their reviewed Claims use `INVESTIGATED`
- CASE-45 stays `LEARNING`
- no references to the three removed Project ids
- no Collection Claim subject

**Step 2: Run and confirm RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=PublicBundleLoaderTest,RetrievalBenchmarkTest test
```

**Step 3: Perform the content migration**

1. Preserve:
   - `sql-audit-project`
   - `activity-engineering-project`
   - `image-audit-project`
   - `personal-agent-platform-project` (rename public title to `Agent能力集成MVP`)
2. Add `role-reset-tool-project` / `role-reset-tool`.
3. Add the three Collections:
   - `open-source-evaluation`
   - `engineering-operations`
   - `technical-writing`
4. Reassign:
   - `case-role-reset` → role reset Project
   - `case-multilingual-upload` → image Project
   - Cases formerly under removed theme Projects receive the appropriate Collection; only relations supported by the approved design remain Projects.
5. Review each removed Project Claim/Timeline/Question relation and move it to a specific Case or existing valid Project relation. Do not delete a public statement solely to make validation pass.
6. Review each CASE-22…CASE-36 Claim’s wording/basis/status, then change its work status to `INVESTIGATED` without claiming a final fix.

Before writing, produce a relation audit listing the final independent Case ids and ensure it matches the accepted count of three. If the data cannot satisfy that accepted count without inventing Project ownership, stop and bring the exact contradiction back to the user rather than guessing.

**Step 4: Regenerate release artifacts**

Use the repository’s existing compiler/governance scripts; do not hand-author checksums or binary indexes. The exact invocation must be taken from `scripts/build-retrieval-bundle.ps1`, `scripts/portfolio-governance.ps1`, and the current release runbook after reading them. Re-run the bundle loader after regeneration.

**Step 5: Run and confirm GREEN**

Run focused loader and retrieval tests.

## Task 8: Full verification and handoff

**Files:**
- Verify all changed backend and public bundle files.

**Step 1: Run architecture and privacy gates**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
```

Expected: both pass without exposing private paths or raw content.

**Step 2: Run the complete backend suite**

```powershell
mvn.cmd -f backend/pom.xml test
```

Expected: PASS.

**Step 3: Package the application**

```powershell
mvn.cmd -f backend/pom.xml package
```

Expected: executable JAR builds and packaged public bundle validation passes.

**Step 4: Inspect Git diff without modifying Git state**

```powershell
git status --short
git diff --check
git diff --stat
```

Confirm only intended backend/data/plan files plus pre-existing user changes are present. Do not stage or commit.

**Step 5: Report**

Provide:

- implemented API/model/data behavior
- exact verification commands and results
- any remaining frontend integration work
- explicit statement that nothing was committed
