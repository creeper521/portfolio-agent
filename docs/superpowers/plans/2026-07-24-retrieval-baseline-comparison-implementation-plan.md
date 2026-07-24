# Retrieval Baseline Comparison Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a reproducible Wave 0 benchmark that compares Keyword, Vector, and Hybrid retrieval on one immutable public snapshot, emits stable JSON/Markdown reports, and cannot claim a real benchmark passed without the verified local BGE model.

**Architecture:** Add release-only benchmark types under `com.portfolio.agent.release.benchmark`; keep production retrieval Profiles unchanged. The comparison runner loads the current public portfolio, deterministically builds one shared corpus, embeds each query once, evaluates three offline routes through the same filtering and grounding rules, and renders version-bound reports. The PowerShell entry point separates unit gates from real-model comparison and stores machine-local output under ignored `output/`.

**Tech Stack:** Java 21, Jackson, JUnit 5, AssertJ, Spring Boot executable JAR, ONNX Runtime Java CPU, DJL HuggingFace Tokenizers, PowerShell.

## Global Constraints

- Implement in an isolated Git worktree created from commit `70a3787`; do not edit, stage, reset, restore, or delete user-owned changes in `D:\code\agent`.
- Runtime public content may read only `backend/src/main/resources/public-data/`; the private Obsidian knowledge base is not a runtime source.
- Keep `portfolio.retrieval.profile` default `DISABLED`; do not add production `VECTOR_ONLY`.
- Use the existing pinned `BAAI/bge-small-zh-v1.5` INT8 ONNX artifact, 512 dimensions, L2-normalized cosine, query batch 1, maximum 256 tokens, intra-op 2, inter-op 1.
- Never log or persist visitor questions. Benchmark queries are fixed reviewed public fixtures, not captured visitor traffic.
- Production and test Java must not use `var`, `record`, or Lombok.
- Every behavior change follows RED → GREEN → REFACTOR with fresh command output.
- Generated reports go under ignored `output/retrieval-benchmark/`; reviewed benchmark fixtures and schemas remain tracked.
- Ranking and performance reports remain separate. A repeated-query embedding performance test is not described as ranking quality or 100 distinct queries.
- This plan covers Wave 0 only. Asset decisions and content Waves 1–3 use separate implementation plans after the baseline report is frozen.

## File Structure

### Benchmark contract

- Create `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkCategory.java`: fixed reviewed query categories.
- Create `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkSplit.java`: `CALIBRATION` or `HOLDOUT`.
- Create `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkRoute.java`: offline `KEYWORD`, `VECTOR`, `HYBRID`.
- Create `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkCase.java`: immutable case contract.
- Create `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkSuite.java`: immutable versioned fixture set.
- Create `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkCaseLoader.java`: strict JSON loading and fixture validation.

### Evaluation and reports

- Create `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalRouteEvaluation.java`: one route/case rank and decision result.
- Create `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkMetrics.java`: immutable aggregate metrics.
- Create `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkReport.java`: version-bound machine report.
- Create `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkEvaluator.java`: Hit@1, Hit@5, MRR@5, positive success, and false-sufficient calculations.
- Create `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalComparisonRunner.java`: shared-corpus three-route evaluation.
- Create `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkMarkdownRenderer.java`: deterministic human report generated from the JSON model.
- Create `backend/src/main/java/com/portfolio/agent/release/RetrievalComparisonCli.java`: strict CLI and atomic report writes.

### Fixtures, scripts, and tests

- Replace `backend/src/test/resources/retrieval-benchmark/cases.json` with schema `retrieval-benchmark-v2`.
- Replace `backend/src/test/java/com/portfolio/agent/release/RetrievalBenchmarkTest.java` with a real-model acceptance test over the v2 suite.
- Create focused tests under `backend/src/test/java/com/portfolio/agent/release/benchmark/`.
- Modify `scripts/run-local-retrieval-benchmark.ps1`.
- Modify `scripts/verify-release.ps1` so unit gates and optional real-model reports are named accurately.
- Create `scripts/run-local-retrieval-benchmark.test.ps1`.
- Create `docs/reports/retrieval-baseline-2026-07-24.md` only after the real model run succeeds; the file records the immutable report hash and summarized results, not raw private input.

---

### Task 1: Lock the v2 benchmark fixture contract

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkCategory.java`
- Create: `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkSplit.java`
- Create: `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkCase.java`
- Create: `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkSuite.java`
- Create: `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkCaseLoader.java`
- Test: `backend/src/test/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkCaseLoaderTest.java`

**Interfaces:**
- Consumes: Jackson `ObjectMapper`.
- Produces: `RetrievalBenchmarkSuite RetrievalBenchmarkCaseLoader.load(byte[] source)`.
- Produces: immutable getters for `suiteVersion`, `contentVersion`, `cases`, and every case field.

- [ ] **Step 1: Write the failing loader tests**

Add tests that require:

```java
RetrievalBenchmarkSuite suite = new RetrievalBenchmarkCaseLoader(mapper)
        .load(validSuiteBytes());

assertThat(suite.getSuiteVersion()).isEqualTo("retrieval-benchmark-v2");
assertThat(suite.getContentVersion()).isEqualTo("2026-07-23.1");
assertThat(suite.getCases()).extracting(RetrievalBenchmarkCase::getCaseId)
        .containsExactly("sql-background-exact-01", "sql-background-paraphrase-01");
```

Add one assertion per invalid condition: unknown JSON field, duplicate `caseId`, blank query, empty expected Claim IDs for an expected `SUFFICIENT`, unsupported category, unsupported split, invalid subject type, blank subject slug, and content version not matching `YYYY-MM-DD.N`.

- [ ] **Step 2: Run the loader test and verify RED**

Run:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=RetrievalBenchmarkCaseLoaderTest test
```

Expected: compilation failure because the benchmark contract classes do not exist.

- [ ] **Step 3: Implement the minimal immutable contract**

Use explicit final classes and enums. The case constructor must defensively copy:

```java
this.expectedClaimIds = List.copyOf(expectedClaimIds);
this.expectedChunkIds = List.copyOf(expectedChunkIds);
```

Configure the loader with strict unknown-property handling, validate the full suite after deserialization, sort cases by `caseId`, and reject duplicate IDs before returning an immutable suite.

- [ ] **Step 4: Run the loader test and verify GREEN**

Run the command from Step 2.

Expected: `RetrievalBenchmarkCaseLoaderTest` passes with zero failures and zero errors.

- [ ] **Step 5: Commit the contract**

```powershell
git add backend/src/main/java/com/portfolio/agent/release/benchmark backend/src/test/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkCaseLoaderTest.java
git commit -m "feat: define retrieval benchmark contract"
```

### Task 2: Implement deterministic metric calculation

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkRoute.java`
- Create: `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalRouteEvaluation.java`
- Create: `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkMetrics.java`
- Create: `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkEvaluator.java`
- Test: `backend/src/test/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkEvaluatorTest.java`

**Interfaces:**
- Consumes: ordered `List<RetrievalRouteEvaluation>`.
- Produces: `Map<RetrievalBenchmarkRoute, RetrievalBenchmarkMetrics> evaluate(...)`.
- `RetrievalRouteEvaluation` exposes route, case ID, split, category, expected decision, actual decision, expected rank, selected Claim IDs, and selected Chunk IDs.

- [ ] **Step 1: Write failing metric examples**

Use four positive cases with expected ranks `1`, `2`, `5`, and absent, plus two negative cases where one incorrectly returns `SUFFICIENT`.

Assert:

```java
assertThat(metrics.getPositiveCount()).isEqualTo(4);
assertThat(metrics.getHitAt1()).isEqualTo(0.25);
assertThat(metrics.getHitAt5()).isEqualTo(0.75);
assertThat(metrics.getMrrAt5()).isEqualTo((1.0 + 0.5 + 0.2) / 4.0);
assertThat(metrics.getFalseSufficientCount()).isEqualTo(1);
```

Also assert that output route order is `KEYWORD`, `VECTOR`, `HYBRID`, independent of input order, and that an empty evaluation set is rejected.

- [ ] **Step 2: Run the evaluator test and verify RED**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=RetrievalBenchmarkEvaluatorTest test
```

Expected: compilation failure because metric classes do not exist.

- [ ] **Step 3: Implement exact formulas**

Use:

```java
double hitAt1 = positiveCount == 0 ? 0.0 : hitAt1Count / (double) positiveCount;
double hitAt5 = positiveCount == 0 ? 0.0 : hitAt5Count / (double) positiveCount;
double mrrAt5 = positiveCount == 0 ? 0.0 : reciprocalRankSum / positiveCount;
```

Count false-sufficient only when `expectedDecision != SUFFICIENT` and `actualDecision == SUFFICIENT`. Do not round inside the domain object; formatting belongs to the Markdown renderer.

- [ ] **Step 4: Run the evaluator test and verify GREEN**

Run the Step 2 command.

Expected: all evaluator tests pass.

- [ ] **Step 5: Commit the evaluator**

```powershell
git add backend/src/main/java/com/portfolio/agent/release/benchmark backend/src/test/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkEvaluatorTest.java
git commit -m "feat: calculate retrieval comparison metrics"
```

### Task 3: Evaluate three offline routes over one shared corpus

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalComparisonRunner.java`
- Test: `backend/src/test/java/com/portfolio/agent/release/benchmark/RetrievalComparisonRunnerTest.java`
- Modify only if required by the testable interface: `backend/src/main/java/com/portfolio/agent/answer/service/ReciprocalRankFusion.java`

**Interfaces:**
- Consumes: `RetrievalBenchmarkSuite`, `RuntimeContentSnapshot`, `AnswerRetrievalCorpus`, `RetrievalPolicy`, and `LocalEmbeddingPort`.
- Produces: `List<RetrievalRouteEvaluation> run(...)`.
- Uses existing `RetrievalQueryNormalizer`, `KeywordRetriever`, `VectorRetriever`, `ReciprocalRankFusion`, and `RetrievalContextValidator`.

- [ ] **Step 1: Write a small-corpus RED test**

Create two projects and three chunks in test memory:

- an exact SQL term chunk that Keyword ranks first;
- a semantic Agent/MCP chunk that Vector ranks first;
- a distractor in another subject.

Use a counting `LocalEmbeddingPort` and assert:

```java
assertThat(embeddingCalls.get()).isEqualTo(suite.getCases().size());
assertThat(evaluations).hasSize(suite.getCases().size() * 3);
assertThat(evaluations).extracting(RetrievalRouteEvaluation::getRoute)
        .contains(RetrievalBenchmarkRoute.KEYWORD,
                RetrievalBenchmarkRoute.VECTOR,
                RetrievalBenchmarkRoute.HYBRID);
```

Assert subject filtering removes the distractor before scoring. Assert Keyword uses `fuse(keywordHits, List.of(), rrfK)`, Vector uses `fuse(List.of(), vectorHits, rrfK)`, and Hybrid uses both lists. All three routes must pass the resulting candidates through the same `RetrievalContextValidator`.

- [ ] **Step 2: Run the runner test and verify RED**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=RetrievalComparisonRunnerTest test
```

Expected: compilation failure because `RetrievalComparisonRunner` does not exist.

- [ ] **Step 3: Implement one-query/one-embedding execution**

For every case:

```java
NormalizedRetrievalQuery query = normalizer.normalize(item.getQuery());
EmbeddingVector queryVector = embeddingPort.embedQuery(query.getLocalText());
List<RankedRetrievalHit> keyword = keywordRetriever.retrieve(
        corpus.getKeywordIndex(), query.getTerms(), allowedChunkIds,
        policy.getKeywordTopK());
List<RankedRetrievalHit> vector = vectorRetriever.retrieve(
        queryVector, corpus.copyVectors(), allowedChunkIds,
        policy.getVectorTopK(), policy.getVectorCandidateThreshold());
```

Build three candidate lists with the existing RRF implementation. Calculate expected rank from the first candidate whose chunk contains any expected Claim or whose ID is in `expectedChunkIds`. Preserve the raw route ranking in the evaluation model, but never print scores or query vectors.

- [ ] **Step 4: Run runner and existing retrieval tests**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=RetrievalComparisonRunnerTest,KeywordRetrieverTest,VectorRetrieverTest,ReciprocalRankFusionTest,RetrievalContextValidatorTest,LocalRetrievalCoordinatorTest test
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit the runner**

```powershell
git add backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalComparisonRunner.java backend/src/test/java/com/portfolio/agent/release/benchmark/RetrievalComparisonRunnerTest.java
git commit -m "feat: compare offline retrieval routes"
```

### Task 4: Produce stable JSON and Markdown reports

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkReport.java`
- Create: `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkMarkdownRenderer.java`
- Test: `backend/src/test/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkReportTest.java`

**Interfaces:**
- Consumes: suite metadata, content version, Bundle hash, Policy version, model descriptor hash, evaluations, and metrics.
- Produces: Jackson-serializable immutable report and `String render(RetrievalBenchmarkReport report)`.

- [ ] **Step 1: Write deterministic report RED tests**

Build the same report from reversed input lists and assert identical JSON bytes after enabling alphabetic property and map-entry ordering. Assert evaluations sort by route then case ID. Assert Markdown includes a neutral title, immutable identities, a route metrics table, category breakdown, and every failing case ID.

Assert report objects have no generated timestamp, absolute path, hostname, original private evidence, vector, or raw score field.

- [ ] **Step 2: Run report tests and verify RED**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=RetrievalBenchmarkReportTest test
```

Expected: compilation failure because report types do not exist.

- [ ] **Step 3: Implement stable rendering**

Use fixed route order and four-decimal display only in Markdown:

```java
private String metric(double value) {
    return String.format(Locale.ROOT, "%.4f", value);
}
```

The report value object retains full doubles. The Markdown title must be `# Retrieval Baseline Comparison`; it must not declare Hybrid valuable. Value classification is added only after the holdout result is reviewed in a later content-wave plan.

- [ ] **Step 4: Verify GREEN and byte stability**

Run the Step 2 command twice.

Expected: both runs pass and the byte-stability assertion succeeds.

- [ ] **Step 5: Commit report rendering**

```powershell
git add backend/src/main/java/com/portfolio/agent/release/benchmark backend/src/test/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkReportTest.java
git commit -m "feat: render retrieval benchmark reports"
```

### Task 5: Add a strict real-model comparison CLI

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/release/RetrievalComparisonCli.java`
- Test: `backend/src/test/java/com/portfolio/agent/release/RetrievalComparisonCliTest.java`

**Interfaces:**
- CLI arguments: `--portfolio`, `--cases`, `--model-dir`, `--output-dir`, `--valid-from`.
- Output files: `comparison.json` and `comparison.md`.
- Success output: `Retrieval comparison completed.`
- Failure output: `RETRIEVAL_COMPARISON_FAILED`, exit code 1.
- Test seam: package-private `static int run(String[] args, BenchmarkExecutor executor, PrintStream out, PrintStream err)`.
- `BenchmarkExecutor` is a package-private nested functional interface whose `execute(ComparisonRequest request)` method returns `RetrievalBenchmarkReport`; production `main` supplies the real corpus/model executor.

- [ ] **Step 1: Write CLI RED tests**

Test duplicate/missing options, nonexistent portfolio/cases/model directory, pre-existing output directory, invalid valid-from date, content-version mismatch, and output parent missing. Add a success test by calling `run(...)` with a `BenchmarkExecutor` lambda returning a fixed report, so it does not need ONNX.

Assert failed execution leaves no partial output directory.

- [ ] **Step 2: Run CLI tests and verify RED**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=RetrievalComparisonCliTest test
```

Expected: compilation failure because the CLI does not exist.

- [ ] **Step 3: Implement strict loading and atomic output**

Follow `RetrievalBundleCompilerCli` conventions:

- strict two-token options;
- verify local model with `LocalEmbeddingArtifactVerifier`;
- build canonical RAG documents with `ClaimRagDocumentBuilder`;
- build keyword index and document vectors once;
- create one process-local query adapter;
- write both reports to a sibling temporary directory;
- atomically move the directory only after both files are written and reread successfully;
- delete only the verified temporary directory on failure.

- [ ] **Step 4: Run CLI, compiler, and loader tests**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=RetrievalComparisonCliTest,RetrievalBundleCompilerCliTest,PublicBundleLoaderTest test
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit the CLI**

```powershell
git add backend/src/main/java/com/portfolio/agent/release/RetrievalComparisonCli.java backend/src/test/java/com/portfolio/agent/release/RetrievalComparisonCliTest.java
git commit -m "feat: add retrieval comparison cli"
```

### Task 6: Replace the fixture and correct script semantics

**Files:**
- Modify: `backend/src/test/resources/retrieval-benchmark/cases.json`
- Modify: `backend/src/test/java/com/portfolio/agent/release/RetrievalBenchmarkTest.java`
- Modify: `scripts/run-local-retrieval-benchmark.ps1`
- Create: `scripts/run-local-retrieval-benchmark.test.ps1`
- Modify: `scripts/verify-release.ps1`

**Interfaces:**
- Script parameters: `-UnitOnly`, `-ModelDirectory`, `-CasesPath`, `-OutputDirectory`.
- Exactly one of `-UnitOnly` or nonblank `-ModelDirectory` is required.
- Unit-only success: `Local retrieval unit gates passed; real-model comparison was not run.`
- Real success: `Local retrieval real-model comparison passed.`
- `scripts/verify-release.ps1` gains optional `-ModelDirectory`; blank invokes the benchmark script with `-UnitOnly`, nonblank forwards the verified directory to the real-model mode.

- [ ] **Step 1: Write PowerShell RED tests**

Use a temporary fake Maven/Java executable and assert:

- no mode fails;
- `-UnitOnly` plus `-ModelDirectory` fails;
- `-UnitOnly` never invokes `RetrievalBenchmarkTest`, performance test, or the CLI;
- real mode requires an existing model directory;
- real mode invokes correctness tests, real-model acceptance, performance test, and `RetrievalComparisonCli`;
- a failed stage returns nonzero and does not print the real-success message.

- [ ] **Step 2: Run the script test and verify RED**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-local-retrieval-benchmark.test.ps1
```

Expected: failure because the existing script accepts an empty model directory and prints an ambiguous success message.

- [ ] **Step 3: Replace `cases.json` with v2 baseline cases**

Keep the existing 5 SQL positives and 4 negatives, assign stable IDs, split them into calibration/holdout, add `subjectType`, `subjectSlug`, expected Claim/Chunk IDs, expected Decision, and categories. Add at least one reviewed natural paraphrase for each current Case subject so baseline covers all 1 Project + 3 Case subjects.

Use only public statements already present in `bundle/portfolio.json`. Do not add facts from the private knowledge base in Wave 0.

- [ ] **Step 4: Implement explicit script modes**

In real mode:

1. run component gates;
2. run `OnnxLocalEmbeddingAdapterSmokeTest,RetrievalBenchmarkTest`;
3. run `LocalEmbeddingPerformanceTest`;
4. package the executable JAR with `-DskipFrontend=true`;
5. invoke `RetrievalComparisonCli` through `PropertiesLauncher`;
6. require `comparison.json` and `comparison.md`.

Update `verify-release.ps1` to call `-UnitOnly` when no model directory was explicitly supplied and to print the same accurate wording.

- [ ] **Step 5: Run RED fixture acceptance, then GREEN**

First run `RetrievalBenchmarkTest` before adapting it and confirm it fails on the v2 schema. Then update it to load the strict suite, resolve both Project and Case subjects, and assert all expected Decisions/Claims.

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-local-retrieval-benchmark.test.ps1
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=RetrievalBenchmarkCaseLoaderTest,RetrievalBenchmarkEvaluatorTest,RetrievalComparisonRunnerTest,RetrievalBenchmarkReportTest,RetrievalComparisonCliTest test
```

Expected: script contract and focused Java suite pass.

- [ ] **Step 6: Commit fixture and script behavior**

```powershell
git add backend/src/test/resources/retrieval-benchmark/cases.json backend/src/test/java/com/portfolio/agent/release/RetrievalBenchmarkTest.java scripts/run-local-retrieval-benchmark.ps1 scripts/run-local-retrieval-benchmark.test.ps1 scripts/verify-release.ps1
git commit -m "test: add explicit retrieval comparison gate"
```

### Task 7: Run and freeze the real Wave 0 baseline

**Files:**
- Generated, ignored: `output/retrieval-benchmark/wave-0/comparison.json`
- Generated, ignored: `output/retrieval-benchmark/wave-0/comparison.md`
- Create after successful run: `docs/reports/retrieval-baseline-2026-07-24.md`
- Modify: `docs/00-文档状态索引.md`
- Modify: `docs/08-current-implementation-status.md`

**Interfaces:**
- Consumes verified model directory `runtime-models/bge-small-zh-v1.5`.
- Produces reviewed baseline summary bound to content version, benchmark suite version, Bundle hash, Policy version, model descriptor hash, and comparison JSON SHA-256.

- [ ] **Step 1: Run the full real-model comparison**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-local-retrieval-benchmark.ps1 -ModelDirectory runtime-models/bge-small-zh-v1.5 -CasesPath backend/src/test/resources/retrieval-benchmark/cases.json -OutputDirectory output/retrieval-benchmark/wave-0
```

Expected: exit code 0, explicit real-model success message, and both report files present.

- [ ] **Step 2: Inspect every failure and metric**

Read `comparison.json` and verify:

- content version equals `2026-07-23.1`;
- all three routes are present for every case;
- no negative case has false `SUFFICIENT`;
- every result is attached to the intended Project or Case;
- JSON contains no absolute path, hostname, private source, vector, raw score, or credential.

If a safety negative fails, stop Wave 0 and fix the behavior through a new RED test before proceeding. Do not weaken the fixture.

- [ ] **Step 3: Freeze the reviewed baseline summary**

Create `docs/reports/retrieval-baseline-2026-07-24.md` containing:

- exact immutable identities;
- Keyword/Vector/Hybrid Hit@1, Hit@5, MRR@5;
- positive success and false-sufficient counts;
- per-category comparison;
- all failed case IDs;
- a statement that Wave 0 is too small to make the final Hybrid value claim;
- SHA-256 of ignored `comparison.json`;
- local environment details from the report.

Update docs 00 and 08 to state that three-route offline baseline exists, runtime Profiles remain unchanged, retrieval remains default-disabled, and no deployment occurred.

- [ ] **Step 4: Run full verification**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-release.ps1 -ModelDirectory runtime-models/bge-small-zh-v1.5
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml package
```

Expected: every command exits 0. Record exact test counts and any expected skips in the baseline document.

- [ ] **Step 5: Commit the reviewed baseline**

```powershell
git add docs/reports/retrieval-baseline-2026-07-24.md docs/00-文档状态索引.md docs/08-current-implementation-status.md
git commit -m "docs: record retrieval baseline comparison"
```

## Plan Self-Review

- Spec coverage: Wave 0 baseline, three offline routes, stable reports, truthful script semantics, real model requirement, calibration/holdout fields, default-disabled runtime, and no deployment are covered.
- Scope boundary: 68-item governance and content Waves 1–3 are intentionally excluded from this implementation plan and remain governed by the approved design.
- Placeholder scan: no task depends on an unspecified file, type, command, or acceptance statement.
- Type consistency: loader returns `RetrievalBenchmarkSuite`; runner returns `List<RetrievalRouteEvaluation>`; evaluator consumes that list; report and renderer consume the resulting metrics; CLI composes the same interfaces.
