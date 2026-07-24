# Public Content Waves 1–3 Implementation Plan

> **Status:** ready for implementation after Wave 0  
> **Approved design:** `docs/superpowers/specs/2026-07-24-full-public-assets-and-hybrid-retrieval-evaluation-design.md`  
> **Wave 0 base:** branch `codex/retrieval-baseline-comparison`, HEAD `e6143f7`  
> **Runtime policy:** retrieval remains `DISABLED`; no deployment is part of this plan.

## 1. Goal and non-negotiable boundaries

This plan turns the approved 68-item private inventory into audited public
Project, Case, Claim, Evidence, Link, Preset, Timeline and RAG content across
three independently versioned waves. Every wave must be reviewable, approvable,
publishable, benchmarkable and reversible without changing production retrieval
profiles.

Private governance source:

`agent_docs_staging/portfolio-governance`

Runtime public source:

`backend/src/main/resources/public-data/bundle`

The application must never read the private governance workspace. Screenshots,
PDFs, daily notes, SQL, logs, source code, internal names, hosts, paths, accounts
and raw Evidence remain private. Public Evidence uses reviewed summaries with
`rawContentPublic=false`.

The repository governance skill is authoritative. Run only:

`scripts/portfolio-governance.ps1`

Before the first domain-tool invocation in a session:

```powershell
cli-find portfolio-governance
```

Every wave follows:

`inspect → validate → benchmark → build-review-pack → explicit human approve → publish → verify`

Never auto-approve. Candidate text or RAG changes produce a new
`candidatePayloadHash`. Decision-ledger changes do not alter that payload hash,
but they do alter the review `inputFingerprint`, Approval projection/digest and
therefore make the old Approval stale.

## 2. Fixed wave inventory

### Wave 1 — existing achievements, 9 assets

`L-01, T-01, T-02, T-03, T-04, T-05, T-06, T-17, K-01`

Public subjects remain:

- Project `sql-audit-project`
- Case `case-multilingual-upload`
- Case `case-role-reset`
- Case `case-codegraph-evaluation`

### Wave 2 — new real projects, 6 primary assets

`L-02, L-04, L-05, T-08, T-09, T-10`

Conditional re-review of assets that are already HOLD and are not part of the
29-item denominator:

- `T-07`: upload-audit implementation is tested, but logging is not released.
- `K-02`: context-compression experiment may publish only with its quality and
  reproducibility limits.

Neither asset may leave HOLD merely because a limitation sentence exists. A
promotion requires new public-safe `DIRECT + APPROVED` Evidence, an explicit
content-owner route decision, a changed decision-ledger hash, a new review
packet and a new Approval. Otherwise all public Project/Case/Evidence references
stay empty. Track promoted-original-HOLD count separately; the 29-item
`PUBLIC_REVIEW_REQUIRED` denominator never changes.

### Wave 3 — engineering and knowledge delivery, 14 assets

`A-01, A-02, A-03, A-04, A-05, A-06, T-12, T-13, T-14, T-15, T-18, T-19, K-14, K-17`

Initial route:

- Publish candidates: `A-01, A-03, A-04, A-05, A-06, T-12, T-13, T-14,
  T-18, T-19, K-14`
- Reviewed HOLD unless new direct acceptance Evidence appears: `A-02, T-15`
- Evidence-only stable public writing link: `K-17`

The final decision ledger, not this initial route, is authoritative.

---

## 3. Foundation tasks before Wave 1

### Task F1 — Bind releases to the 68-item publication decision ledger

**Files**

- Create:
  `.agents/skills/portfolio-governance/schemas/asset-publication-decision-ledger.schema.json`
- Modify:
  `.agents/skills/portfolio-governance/scripts/portfolio-governance.ps1`
- Modify:
  `scripts/portfolio-governance.ps1`
- Modify:
  `scripts/portfolio-governance.test.ps1`
- Private generated file, never committed:
  `agent_docs_staging/portfolio-governance/decisions/asset-publication-decisions-2026-07-24.json`

**Contract**

Each of the 68 assets has exactly one record:

```text
assetId
contentType
achievementStatus
contributionType
publicPriority
evidenceStatus
originalReviewState
finalRoute
decisionReason
projectSlugs
caseSlugs
evidenceIds
privacyReview
routeDecision
targetContentVersion
targetWave
```

`finalRoute` is one of:

```text
PROJECT
CASE
ENRICH_EXISTING_PROJECT
EVIDENCE_ONLY
TIMELINE_ONLY
HOLD
EXCLUDE
```

`routeDecision` is immutable for the lifetime of one review/Approval chain and
is one of:

```text
PUBLISH_CANDIDATE
REVIEWED_HOLD
EXCLUDED
```

Rules:

- `PUBLISH_CANDIDATE` requires a non-HOLD/non-EXCLUDE `finalRoute`, valid public
  references and the exact non-null target content version.
- `REVIEWED_HOLD` requires `finalRoute=HOLD`, null public references and null
  target content version.
- `EXCLUDED` requires `finalRoute=EXCLUDE`, null public references and null
  target content version.
- The ledger is never mutated from pending to approved or published. Approval,
  publish and verify outcomes are append-only governance audit records.
- A wave closure report derives terminal outcome by joining the immutable
  `ledgerHash`, Approval ID, verified release/runtime Bundle hash and audit
  receipts. `PUBLISHED` is a derived closure outcome, not a ledger field.
- The final closure report proves the 29 original `PUBLIC_REVIEW_REQUIRED`
  assets ended as derived `PUBLISHED` or immutable `REVIEWED_HOLD`; original
  HOLD promotions are counted separately.

**TDD**

1. Add RED tests for missing/duplicate IDs, invalid routes, automatic status or
   contribution upgrades, unresolved public references, HOLD/EXCLUDE leakage and
   missing reverse references.
2. Validate exact coverage of `L-01..07`, `T-01..19`, `A-01..25`, `K-01..17`.
3. Bind the ledger hash into review snapshot, Approval projection and approval
   digest.
4. Prove any ledger mutation invalidates the old Approval.

**CLI interface and hash chain**

Both wrappers gain mandatory `-DecisionLedger` for content-changing governance:

- `.agents/skills/portfolio-governance/scripts/portfolio-governance.ps1`
- `scripts/portfolio-governance.ps1`

The `validate`, `benchmark`, `build-review-pack`, `approve`, `publish` and
`verify` commands all load the same immutable ledger bytes and recompute
`ledgerHash`.
Review snapshots bind `ledgerHash` into `inputFingerprint`; Approval projection
and digest bind the same value; publish and verify reject any recomputed value
that differs from the approved value. `candidatePayloadHash` remains the hash of
the exact canonical `portfolio.json`, `presentation.json` and
`rag-documents.jsonl` bytes and never absorbs the ledger.

Mutation tests change one ledger byte after Approval and prove publish and
verify fail stale without changing `candidatePayloadHash`. Successful publish
and verify append audit receipts without rewriting the ledger; closure-report
tests prove the receipts join to the exact approved `ledgerHash`.

**Verification**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.test.ps1
```

Every real governance command later in this plan passes:

```powershell
-DecisionLedger <private-decision-ledger-path>
```

**Commit**

`feat: bind public releases to asset decisions`

**Stop**

- An incomplete 68-item ledger blocks every wave.
- A single unsupported asset becomes HOLD without blocking unrelated assets.
- A broken public reverse reference blocks the current wave.

### Task F2 — Make the comparison CLI consume verified seven-file artifacts

The final retrieval Bundle is exactly:

```text
manifest.json
portfolio.json
presentation.json
rag-documents.jsonl
keyword-index.json
vector-index.bin
checksums.json
```

**Files**

- Modify:
  `backend/src/main/java/com/portfolio/agent/release/RetrievalComparisonCli.java`
- Modify:
  `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalComparisonRunner.java`
- Modify:
  `backend/src/test/java/com/portfolio/agent/release/RetrievalComparisonCliTest.java`
- Modify:
  `backend/src/test/java/com/portfolio/agent/release/benchmark/RetrievalComparisonRunnerTest.java`
- Modify:
  `backend/src/test/java/com/portfolio/agent/portfolio/repository/file/PublicBundleLoaderTest.java`

**TDD**

1. RED: a four-file Bundle is rejected for a real comparison.
2. RED: tampered RAG, keyword or vector artifacts are rejected.
3. RED: model ID, descriptor hash, dimension, policy version or chunk-set
   mismatch is rejected.
4. RED: document embedding calls must be zero; query embedding calls must equal
   the number of benchmark cases.
5. GREEN: read `RagDocument`, `RuntimeKeywordIndex`, `RuntimeVectorIndex` and
   `RetrievalManifest` only from `RuntimeContentSnapshot.getRetrievalContent()`.
6. Use the pinned local model only for query embeddings.

**Verification**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -DskipFrontend=true -Dtest=RetrievalComparisonCliTest,RetrievalComparisonRunnerTest,PublicBundleLoaderTest test
```

**Commit**

`fix: benchmark verified retrieval bundle artifacts`

**Stop**

Never rebuild a mismatched published index and continue. Any identity mismatch
fails the real comparison.

### Task F3 — Complete the machine report evidence

**Files**

- Create:
  `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalExpectedRank.java`
- Create:
  `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkRunMetadata.java`
- Create:
  `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkGroupMetrics.java`
- Create:
  `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalDecisionCount.java`
- Modify:
  `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalRouteEvaluation.java`
- Modify:
  `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalComparisonRunner.java`
- Modify:
  `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkEvaluator.java`
- Modify:
  `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkReport.java`
- Modify:
  `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkMarkdownRenderer.java`
- Modify:
  `backend/src/main/java/com/portfolio/agent/release/RetrievalComparisonCli.java`
- Modify:
  `backend/src/test/java/com/portfolio/agent/release/benchmark/RetrievalComparisonRunnerTest.java`
- Modify:
  `backend/src/test/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkEvaluatorTest.java`
- Modify:
  `backend/src/test/java/com/portfolio/agent/release/benchmark/RetrievalBenchmarkReportTest.java`
- Modify:
  `backend/src/test/java/com/portfolio/agent/release/RetrievalComparisonCliTest.java`
- Modify:
  `scripts/run-local-retrieval-benchmark.test.ps1`

**Machine report additions**

Per evaluation:

- `subjectType`, `subjectSlug`
- one rank for every expected Claim
- one rank for every expected Chunk
- compatibility `expectedRank`, defined as the best expected-item rank
- explicit `null` for misses

Aggregates:

- `split + route`
- `split + category + route`
- `split + subject + route`
- `split + route + actualDecision`

Run metadata:

- JDK/runtime/vendor
- OS/version/arch
- CPU identifier or processor count
- started/completed timestamps and duration
- suite/content/runtime Bundle/policy/model identities

Use injected clocks/timers in deterministic tests. Holdout is the primary
Markdown table; Calibration is separate.

**Close remaining fault-path gaps**

- Inject JSON write, readback and atomic-move failures; prove safe cleanup.
- Assert exact real-mode stage order in the PowerShell contract test.
- Truly reverse route input in the determinism test.

**Commit**

`feat: add grouped retrieval evaluation evidence`

### Task F4 — Import an external verified release atomically

**Files**

- Modify:
  `scripts/run-local-retrieval-benchmark.ps1`
- Modify:
  `scripts/run-local-retrieval-benchmark.test.ps1`
- Create:
  `scripts/import-public-release.ps1`
- Create:
  `scripts/import-public-release.test.ps1`
- Modify:
  `scripts/verify-release.ps1`
- Create:
  `backend/src/main/java/com/portfolio/agent/release/PublicBundleVerificationCli.java`
- Create:
  `backend/src/test/java/com/portfolio/agent/release/PublicBundleVerificationCliTest.java`

**Contract**

`run-local-retrieval-benchmark.ps1` accepts `-BundleDirectory`.

`PublicBundleVerificationCli` accepts one external Bundle directory, loads it
through `PublicBundleLoader`, verifies the exact seven-file set, checksums,
manifest, candidate hash, references, retrieval manifest and index identities,
and prints only a stable verified identity summary. `verify-release.ps1` gains
`-BundleDirectory` and calls this verifier instead of silently checking only the
classpath Bundle.

`import-public-release.ps1`:

1. accepts `-ReleaseRoot`, `-TargetVersion`, `-Workspace` and
   `-DecisionLedger`;
2. accepts exactly the seven public files;
3. calls governance verification and `PublicBundleVerificationCli`;
4. copies to a sibling temporary directory;
5. rereads and rehashes every file;
6. performs a Windows-safe failure-atomic transaction on the same volume:
   - rename current target to a unique sibling backup;
   - rename verified temp to target;
   - verify the new target again;
   - delete the backup only after verification;
   - if either rename or final verification fails, restore the backup before
     returning nonzero;
7. preserves byte-for-byte old Bundle contents on every pre-commit failure and
   never leaves a mixed-version directory;
8. treats leftover verified-backup cleanup failure as an explicit warning while
   keeping the new verified target active;
9. never copies Approval, review packets or raw Evidence.

**TDD**

Cover extra/missing files, invalid checksums, every copy/readback/rename/final-
verify fault point, byte-identical old Bundle restoration, no mixed version,
backup-cleanup warning and successful exact import. Also prove missing
`-Workspace`/`-DecisionLedger` fails before copy, and a ledger whose recomputed
hash differs from the publish/verify receipt is rejected.

**Commit**

`feat: import verified seven-file releases`

---

## 4. Wave 1 — deepen current public achievements

Target content version: `2026-07-24.1`  
Benchmark suite: `retrieval-benchmark-v3-wave1`

### Task W1.1 — Prepare reviewed public Claim and Evidence additions

Private governance changes only:

#### SQL Project additions

Claims:

- `claim-sql-audit-async-task-lifecycle`
- `claim-sql-audit-progress-fallback`
- `claim-sql-audit-result-lifecycle`
- `claim-sql-audit-truncation-disclosure`
- `claim-sql-audit-documented-handoff`

Evidence:

- `evidence-sql-audit-async-progress-validation`
- `evidence-sql-audit-result-lifecycle-docs`

Presets:

- `question-sql-audit-async-and-recovery`
- `question-sql-audit-progress-fallback`
- `question-sql-audit-archive-and-truncation`

Do not publish failed-source retry as a KEY Claim without direct public
acceptance Evidence.

#### Existing Case additions

Multilingual:

- problem/replacement Claim
- no-historical-backfill limitation
- verification-sequence and recovery-boundary presets

Role reset:

- cache-interference background
- confirmation-safety decision
- documented-delivery outcome
- never quantify usage volume

CodeGraph:

- evaluation-method Claim
- manual-quality-review limitation
- qualitative-publication limitation
- never publish unsupported productivity percentages

Every KEY Claim requires an `APPROVED + DIRECT` Evidence Link.

### Task W1.2 — Expand and freeze Wave 1 benchmark before Approval

**Files**

- Modify:
  `.agents/skills/portfolio-governance/benchmark/active-benchmarks.v1.json`
- Modify:
  `backend/src/test/resources/retrieval-benchmark/cases.json`
- Modify strict fixture/coverage tests.

Keep all Wave 0 cases. Add at minimum:

- SQL async lifecycle semantic paraphrase
- WebSocket/polling fallback acronym question
- archive/truncation exact-term question
- unsupported failed-source retry negative
- two additional natural questions for each existing Case
- multilingual backfill false claim
- role-reset arbitrary batch-delete false claim
- CodeGraph universal-productivity false claim

Every active subject has at least three natural questions. Every KEY Claim has a
Holdout. Calibration and Holdout text/intent variants are distinct.

**Commit**

`test: expand wave one retrieval cases`

### Task W1.3 — Build review packet and pause for explicit Approval

Run the governance sequence through `build-review-pack`. Record:

- decision-ledger hash
- candidate payload hash
- benchmark hash
- privacy result
- all BLOCKER/ERROR findings

Pause and present the exact candidate hash to the user. Do not approve or publish
without the user's explicit authorization.

### Task W1.4 — Publish, compare, import and freeze Wave 1

After explicit Approval:

1. publish an external seven-file release;
2. verify it;
3. run real Keyword/Vector/Hybrid comparison against that directory;
4. inspect every negative and regression;
5. import atomically only after all gates pass;
6. update runtime content and reports.

**Commits**

- `content: deepen verified public achievements`
- `docs: record wave one retrieval results`

**Stop**

- Any historical or Wave 1 active Holdout Hit@5 or critical-subject coverage
  regression blocks import.
- Every safety, privacy, injection, unsupported and similar-but-false negative
  must have false `SUFFICIENT = 0`.
- Missing KEY Claim/direct approved Evidence/Holdout coverage blocks import.
- Any privacy, Bundle hash, reference, index, model or policy identity failure
  blocks import.
- A failed release is never imported; the current repository Bundle remains
  byte-identical.
- Tuning may use Calibration only; any behavior change requires a new policy
  version, review packet and Approval.
- Candidate edits require a new review packet and Approval.

---

## 5. Wave 2 — publish real projects

Target content version: `2026-07-24.2`  
Benchmark suite: `retrieval-benchmark-v4-wave2`

### Task W2.1 — Preserve authoritative publication statuses

**Files**

- Modify:
  `backend/src/main/java/com/portfolio/agent/portfolio/domain/ProjectStatus.java`
- Modify:
  `backend/src/main/java/com/portfolio/agent/portfolio/domain/AchievementStatus.java`
- Modify:
  `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerAchievementStatus.java`
- Modify:
  `backend/src/main/java/com/portfolio/agent/answer/adapter/portfolio/LocalPortfolioKnowledgeAdapter.java`
- Modify:
  `backend/src/main/java/com/portfolio/agent/answer/service/RetrievalContextValidator.java`
- Modify:
  `backend/src/main/java/com/portfolio/agent/portfolio/validation/PortfolioSnapshotValidator.java`
- Modify:
  `frontend/src/features/portfolio/model/portfolioTypes.ts`
- Modify:
  `frontend/src/features/public-content/model/publicContentTypes.ts`
- Modify:
  `frontend/src/features/portfolio/model/projectLabels.ts`
- Modify:
  `frontend/src/shared/components/StatusMark.vue`
- Modify their backend mapping/grounding/validator and frontend model/component
  tests.

Add:

- Project: `IMPLEMENTED_TESTED`, `VALIDATED_PROTOTYPE`
- Achievement: `VALIDATED_PROTOTYPE`

Keep old `PROTOTYPE` readable. Never silently upgrade or downgrade private
authoritative status. `LocalPortfolioKnowledgeAdapter` must map the new value
explicitly; `RetrievalContextValidator` and
`PortfolioSnapshotValidator.isAchievement` must define its lifecycle semantics
instead of relying on a failing or accidental `valueOf`.

**Commit**

`feat: preserve authoritative publication statuses`

### Task W2.2 — Prepare L-04 Agent platform Project and Cases

Project:

- `personal-agent-platform-project`
- slug `personal-agent-platform`
- status `VALIDATED_PROTOTYPE`

Cases:

- `case-streaming-chat-history` (`T-08`)
- `case-spring-ai-tool-calling` (`T-09`)
- `case-mcp-dual-transport` (`T-10`)

Claims cover only demonstrated RAG, tool, session/memory and language-preference
chains. The public text must state that full engineering delivery, persistence,
versions, concurrency and stability are not verified. Raw screenshots remain
private.

### Task W2.3 — Prepare L-05 developer-tooling Project and Cases

Project:

- `developer-tools-context-evaluation-project`
- slug `developer-tools-context-engineering`
- status `VALIDATED_PROTOTYPE`

Attach existing CodeGraph Case. Conditionally add
`case-context-compression-evaluation` for `K-02`.

Context-compression claims may state fixed-input offline token estimates and
quality-risk review. `K-02` currently has original `reviewState=HOLD` and may
become a Case only after new public-safe direct Evidence, an explicit
content-owner route change, a changed ledger hash, a new review packet and a new
Approval. Otherwise it remains HOLD with no public references. It may not state
API billing savings, online Agent efficiency, lossless quality or complete
reproducibility.

### Task W2.4 — Resolve L-02 image upload/audit route

Preferred Project:

- `image-upload-audit-project`
- slug `image-upload-and-audit`
- status `IMPLEMENTED_TESTED`

Attach the existing multilingual Case and conditionally add
`case-image-upload-audit`.

The audit Case must state “implemented and tested; logging not released.” `T-07`
currently has original `reviewState=HOLD` and follows the same new direct
Evidence, content-owner decision, ledger-hash, review-packet and Approval
requirements as `K-02`. If any condition is absent, keep
`T-07 finalRoute=HOLD`,
`routeDecision=REVIEWED_HOLD`, keep its public references empty and
retain only the existing multilingual Case.

Never infer the full audit workstream from the multilingual-fix Evidence.

### Task W2.5 — Add cross-project benchmark coverage

At least three natural questions per new Project/Case and one Holdout per KEY
Claim. Add confusion and false-claim cases:

- Agent tool calling vs CodeGraph tooling
- session memory vs context compression
- SQL log retrieval vs Agent RAG
- multilingual upload repair vs upload audit
- code navigation vs conversation compression
- “billing saved” and “quality lossless” false claims

### Task W2.6 — Review, explicit Approval, publish and freeze

Build the review packet and pause for user Approval. After Approval, publish,
verify, compare, inspect, import and document exactly as Wave 1.

**Commits**

- `test: add wave two project retrieval coverage`
- `content: publish reviewed agent and tooling projects`
- `docs: record wave two retrieval results`

**Stop**

- A Project missing any core direct Evidence is held as a whole.
- A non-core incomplete Case may HOLD independently.
- Any historical or Wave 2 active Holdout Hit@5 or critical-subject coverage
  regression blocks import.
- Every safety, privacy, injection, unsupported, similar-but-false and
  cross-subject negative must have false `SUFFICIENT = 0`.
- Missing KEY Claim/direct approved Evidence/Holdout coverage blocks import.
- Any privacy, Bundle hash, reference, index, model or policy identity failure
  blocks import.
- A failed release is never imported; the current repository Bundle remains
  byte-identical.
- Algorithm tuning may use Calibration only and requires a new policy version,
  review packet and Approval.

---

## 6. Wave 3 — engineering cases, knowledge delivery and final assessment

Target content version: `2026-07-24.3`  
Benchmark suite: `retrieval-benchmark-v5-wave3`

### Task W3.1 — Route the remaining 14 public-review assets

Create narrow, independently reviewable Cases for:

- `A-01, A-03, A-04, A-05, A-06`
- `T-12, T-13, T-14, T-18, T-19`
- `K-14`

Each Case must contain:

```text
problem
actions
decisions
verification
outcome
limitations
KEY Claim
DIRECT APPROVED Evidence
Preset
Timeline
```

Required boundaries:

- `T-12`: local CI only; no production pipeline/deployment claim.
- `T-13`: no internal class, interface, account, environment or invocation count.
- `T-14`: build/static replacement/old-hash cleanup/commit only; no deployment.
- `T-18`: completed internal sharing and retrospective only; no audience or
  impact metrics.
- `T-19`: contribution remains `COLLABORATIVE`; no private knowledge content or
  paths.
- `K-14`: generic merge-conflict method; no real people/branches/config names.

Route to reviewed HOLD unless new direct acceptance Evidence exists:

- `A-02`
- `T-15`

Publish `K-17` as Evidence-only or Timeline-only with the stable link:

`https://blog.csdn.net/2301_81073317`

Do not freeze dynamic article/view counts without a dated snapshot.

### Task W3.2 — Complete final Holdout and safety coverage

Cover every active Project/Case with at least three natural questions and every
KEY Claim with a Holdout.

Add:

- build/package/deployment/version-conflict confusion
- configuration/cache/display-filter confusion
- local CI/load-test/runtime-trace confusion
- PRIVACY, INJECTION and UNSUPPORTED_OR_WITHDRAWN negatives

Do not delete hard cases or rewrite Holdout expectations after observing a
failure.

**Commit**

`test: complete public retrieval holdout coverage`

### Task W3.3 — Review, explicit Approval, publish and import

The immutable final ledger plus append-only closure report must prove:

- all 68 assets have immutable route decisions;
- all 29 original `PUBLIC_REVIEW_REQUIRED` assets have derived closure outcome
  `PUBLISHED` or immutable `routeDecision=REVIEWED_HOLD`;
- no dangling Project/Case/Evidence references.

Build the review packet and pause for user Approval. After Approval, publish,
verify, compare and atomically import.

**Commit**

`content: publish reviewed engineering cases`

**Stop**

- All 68 assets must have immutable route decisions; the closure report must
  derive `PUBLISHED` or `REVIEWED_HOLD` for all 29 original public-review assets.
- Any historical or Wave 3 active Holdout Hit@5 or critical-subject coverage
  regression blocks import.
- Every safety, privacy, injection, unsupported, similar-but-false and
  cross-subject negative must have false `SUFFICIENT = 0`.
- Missing KEY Claim/direct approved Evidence/Holdout coverage blocks import.
- Any privacy, Bundle hash, reference, index, model or policy identity failure
  blocks import.
- A failed release is never imported; the previous repository Bundle remains
  byte-identical.
- Tuning may use Calibration only; any behavior change requires a new policy
  version, review packet and Approval.

### Task W3.4 — Run the final value and performance assessment

Ranking report:

- Holdout-only primary conclusion
- split, category and Project/Case groups
- Decision distribution
- every expected Claim/Chunk rank
- all failures, not only Hybrid wins

Separate performance report:

- model load and warmup
- query embedding latency
- end-to-end retrieval latency
- JDK/OS/CPU/thread configuration
- sample count and repetition method

**Performance artifact implementation**

Create:

- `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalPerformanceReport.java`
- `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalPerformanceJsonWriter.java`
- `backend/src/main/java/com/portfolio/agent/release/benchmark/RetrievalPerformanceMarkdownRenderer.java`
- `backend/src/main/java/com/portfolio/agent/release/RetrievalPerformanceCli.java`
- `backend/src/test/java/com/portfolio/agent/release/benchmark/RetrievalPerformanceReportTest.java`
- `backend/src/test/java/com/portfolio/agent/release/RetrievalPerformanceCliTest.java`
- `scripts/run-retrieval-performance-benchmark.ps1`
- `scripts/run-retrieval-performance-benchmark.test.ps1`

The CLI consumes the same verified seven-file Bundle, policy and model identity
as the ranking run. An injected stage timer records model load, warmup, each
query embedding and end-to-end retrieval without mixing ranking metrics.
Deterministic tests use fixed stage durations and verify stable JSON/Markdown,
identity fields, sample count, warmup count and repetition method.

Generated, ignored output:

- `output/retrieval-benchmark/wave-3/performance.json`
- `output/retrieval-benchmark/wave-3/performance.md`

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-retrieval-performance-benchmark.ps1 -BundleDirectory <wave-3-version-directory> -ModelDirectory D:\code\agent\runtime-models\bge-small-zh-v1.5 -CasesPath backend/src/test/resources/retrieval-benchmark/cases.json -OutputDirectory output/retrieval-benchmark/wave-3
```

Performance results are descriptive and never replace the Holdout-only ranking
value conclusion.

Final value classification is exactly one:

- clearly valuable
- complementary with limited gain
- value not demonstrated
- regression exists

**Commits**

- `docs: record final hybrid retrieval assessment`
- `docs: close full asset publication ledger`

---

## 7. Fixed commands per wave

After `cli-find portfolio-governance`, run the repository wrapper according to
its discovered interface. The intended sequence is:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml clean package
```

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/build-retrieval-bundle.ps1 -CandidateDirectory <candidate> -JarPath backend/target/portfolio-agent.jar
```

After discovery confirms the wrapper syntax, every state-bearing command must
include the same private ledger path:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.ps1 -Command validate -Workspace <private-workspace> -Candidate <candidate> -DecisionLedger <private-ledger>
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.ps1 -Command benchmark -Workspace <private-workspace> -Candidate <candidate> -DecisionLedger <private-ledger>
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.ps1 -Command build-review-pack -Workspace <private-workspace> -Candidate <candidate> -DecisionLedger <private-ledger>
```

Pause for explicit human authorization, then run the discovered `approve`
interface with the same `-DecisionLedger`. Publish and verify also receive the
same path and independently recompute `ledgerHash`; they never trust the hash
stored in the review packet without rereading the ledger.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-local-retrieval-benchmark.ps1 -BundleDirectory <wave-version-directory> -ModelDirectory D:\code\agent\runtime-models\bge-small-zh-v1.5 -CasesPath backend/src/test/resources/retrieval-benchmark/cases.json -OutputDirectory output/retrieval-benchmark/wave-N
```

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/import-public-release.ps1 -ReleaseRoot <wave-release-root> -TargetVersion <content-version> -Workspace <private-workspace> -DecisionLedger <private-ledger>
```

Full verification:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run check
npm.cmd --prefix frontend run lint
npm.cmd --prefix frontend run build
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-release.ps1 -SkipInstall -ModelDirectory D:\code\agent\runtime-models\bge-small-zh-v1.5
```

## 8. Documentation updated after every accepted wave

- `README.md`
- `docs/00-文档状态索引.md`
- `docs/08-current-implementation-status.md`
- `docs/09-portfolio-asset-library-status.md`
- `docs/reports/retrieval-wave-1-2026-07-24.md`
- `docs/reports/retrieval-wave-2-2026-07-24.md`
- `docs/reports/retrieval-wave-3-2026-07-24.md`

Every document states:

- local verified Bundle;
- real local-model evaluation status;
- retrieval still default-disabled;
- no deployment;
- no online acceptance unless separately evidenced.

## 9. Final completion gates

The work is complete only when:

1. all 68 assets have one terminal decision;
2. all 29 public-review candidates are published or reviewed HOLD;
3. every active Project/Case has KEY Claim, direct approved Evidence and Holdout
   coverage;
4. no private governance material exists in tracked runtime files;
5. the imported seven-file Bundle passes hash, reference, model and index checks;
6. all three routes use the same Bundle, policy and model;
7. every safety negative has false `SUFFICIENT = 0`;
8. real model comparison ran successfully;
9. full backend/frontend/privacy/architecture/release gates pass;
10. the final report uses Holdout only for the Hybrid-value conclusion;
11. runtime Profile remains unchanged and deployment remains deferred.
