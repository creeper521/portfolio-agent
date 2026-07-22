# Portfolio Agent Content Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved B-stage Claim/Evidence governance, immutable public release bundle, offline governance CLI, approval, benchmark, audit, publish, rollback, and controlled learning loop without starting C.

**Architecture:** Replace the legacy schema-1 classpath snapshot with a schema-2 published bundle whose canonical payload is `portfolio.json` plus `presentation.json`. The Spring runtime validates manifest/checksums and all cross-file invariants, derives Claim/Evidence indexes from `ClaimEvidenceLink`, constructs one immutable `RuntimeContentSnapshot`, and atomically exposes only complete published versions. An explicit PowerShell governance CLI under the project skill operates only on a private workspace outside the repository and implements the fixed Gate sequence; approval binds only canonical payload bytes, while manifest/checksums/runtime hashes are deterministic downstream artifacts.

**Tech Stack:** Java 21, Spring Boot 3.5, Jackson, Maven, PowerShell 5.1+, JSON Schema documents, Vue 3/TypeScript, Vitest, Playwright.

## Global Constraints

- Preserve every existing modified and untracked file. Never reset, restore, checkout, delete, stage, commit, push, or create a PR.
- B does not add a database, CMS, model, RAG, embedding, SSE, external Provider, Registry, Orchestrator, DurableTask, generic Hook, or multi-agent runtime.
- Private sources and private review packets remain outside the Git worktree; tests use synthetic fixtures only.
- Public Claim identity is `subjectType + subjectId`; business classification is `category`; lifecycle is `achievementStatus`; basis is `verificationBasis`; computed trust is `verificationStatus`; importance is `materiality`.
- `ClaimEvidenceLink` is the only Claim/Evidence support relationship. Do not retain or introduce `supportedClaims`, `claimType`, or manually maintained reverse support indexes.
- Hash dependency is strictly object contentHash -> candidatePayloadHash -> Approval/approvalDigest -> manifestHash and checksumsHash -> runtimeBundleHash.
- Approval covers canonical public payload bytes only. Publishing copies those bytes exactly and cannot normalize, reorder, default, or migrate them.
- Only `RuntimeContentSnapshot`, `AnswerTurnSnapshot`, `GovernanceRunSnapshot`, and future `AgentExecutionSnapshot` are valid Snapshot names; this phase creates only `GovernanceRunSnapshot` in addition to A's types.
- Java production and tests use explicit types, immutable final classes, no `var`, no records, no Lombok, and no field injection.
- All behavior follows RED -> verify failure -> GREEN -> verify pass -> REFACTOR. No commit steps are included because the user prohibited commits.

---

### Task 1: Migrate Public Facts to the Claim/Link Schema

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/portfolio/domain/Claim.java`
- Create: `ClaimCategory.java`, `ClaimSubjectType.java`, `AchievementStatus.java`, `VerificationBasis.java`, `ClaimVerificationStatus.java`, `Materiality.java`, `ClaimEvidenceLink.java`, and `SupportType.java` in the same package.
- Modify: `PortfolioSnapshot.java`, `ProjectProfile.java`, `EvidenceRecord.java`, `TimelineEvent.java`, `QuestionDefinition.java`, and `RuntimeContentSnapshot.java`.
- Modify: response DTOs, `PortfolioResponseMapper.java`, and frontend public-content types/fixtures/renderers.
- Replace runtime resource use with schema-2 facts/presentation files under `backend/src/main/resources/public-data/bootstrap/` while retaining the legacy file only as an unused historical artifact.
- Test: portfolio model, repository, validator, service, controller, frontend repository and page tests.

**Interfaces:**
- `PortfolioSnapshot` consumes `schemaVersion`, `contentVersion`, `owner`, optional `internshipPeriod`, `projects`, `claims`, `evidence`, `claimEvidenceLinks`, `timelineEvents`, and `questionPresets`.
- `Claim` exposes exactly `id`, `subjectType`, `subjectId`, `category`, `statement`, `detail`, `achievementStatus`, `contributionType`, `verificationBasis`, computed `verificationStatus`, `materiality`, `topics`, and `audiencePriorities`.
- `EvidenceRecord` contains only public evidence fields and never `supportedClaims`.
- `QuestionDefinition` uses `projectIds`, `preferredClaimCategories`, placements, audiences, aliases, and `deterministicEntry`; it does not use `claimType`.
- Runtime builds `claimsByEvidenceId` and `evidenceByClaimId` only from approved links.

- [ ] Add model-contract tests constructing Claim and Link values, proving defensive copies and absence of `supportedClaims`/`claimType` properties.
- [ ] Add validator tests for duplicate IDs, missing subjects, dangling links, non-DIRECT achievement support, invalid verification elevation, and invalid timeline/question references.
- [ ] Run focused tests and verify failures are caused by missing schema-2 types and rules.
- [ ] Implement the immutable types and schema-2 Jackson mapping with unknown fields rejected.
- [ ] Migrate the one approved SQL project into explicit Claims and Links without strengthening any factual statement.
- [ ] Re-run focused backend and frontend tests until green.

### Task 2: Validate and Load an Immutable Published Bundle

**Files:**
- Create: `portfolio/domain/ReleaseManifest.java`, `BundleCounts.java`, `PresentationSnapshot.java`, and explicit presentation value types.
- Create: `portfolio/repository/file/PublicBundleLoader.java`, `BundleHashCalculator.java`, and `ActiveBundleLocator.java`.
- Modify: `JsonPublicPortfolioRepository.java`, `application.yml`, `PortfolioSnapshotValidator.java`, and repository tests.
- Create bootstrap `manifest.json`, `portfolio.json`, `presentation.json`, and `checksums.json`.

**Interfaces:**
- `PublicBundleLoader#load(Resource root): RuntimeContentSnapshot` reads an allowlisted four-file B bundle, verifies exact SHA-256 checksums, schema/application compatibility, counts, contentVersion equality, privacy-safe paths, and cross-file invariants before returning.
- `candidatePayloadHash = SHA256(length-prefixed UTF-8 filename + exact file bytes for portfolio.json and presentation.json in fixed filename order)`.
- `manifestHash = SHA256(exact manifest bytes)`, `checksumsHash = SHA256(exact checksums bytes)`, and `runtimeBundleHash = SHA256(length-prefixed manifestHash + checksumsHash)`.
- `RuntimeContentSnapshot` contains immutable facts, presentation, derived Link indexes, hashes, and loaded time.

- [ ] Write failing loader tests for valid bundle, byte mutation, undeclared file, bad count, mismatched version, unsupported schema, missing checksum, symlink/path traversal, and hash determinism.
- [ ] Run the focused repository/loader suite and record expected failures.
- [ ] Implement allowlisted byte loading and the one-way hash calculator without canonicalizing approved payload.
- [ ] Make the repository load only the complete bundle; no fallback to the legacy file when a configured active bundle is invalid.
- [ ] Re-run loader, repository, architecture, and full backend tests.

### Task 3: Make Claim/Link the Runtime Verification Authority

**Files:**
- Modify: `LocalPortfolioKnowledgeAdapter.java`, Answer-owned knowledge types, `VerificationPolicy.java`, deterministic engine result metadata, Answer response DTOs, and frontend mappings.
- Test: Answer adapter, runtime, verification, controller contract, frontend answer mapping and rendering.

**Interfaces:**
- Answer projection includes referenced Claim IDs, each Claim's basis/status/materiality, and Link-derived approved Evidence IDs.
- `VERIFIED` requires every referenced KEY Claim to be `EVIDENCE_SUPPORTED` with at least one DIRECT link to current public Evidence.
- `SELF_DECLARED` or `INFERRED` KEY Claims cap at `PARTIALLY_VERIFIED`; an unsupported KEY Claim yields `UNVERIFIED`; BOUNDARY/REJECTED remain `NOT_APPLICABLE`.
- Response sections expose `claimIds` and `evidenceIds`; verification is calculated and cannot be copied from candidate input.

- [ ] Add failing tests for all KEY/SUPPORTING and basis/support combinations, including a supporting Claim that cannot mask an unsupported KEY Claim.
- [ ] Verify the new tests fail against A's Evidence-presence policy.
- [ ] Implement the minimal Claim-aware Answer projection and policy.
- [ ] Update DTO/frontend mapping and verify structured sections retain both Claim and Evidence references.
- [ ] Re-run Answer backend tests and focused Vitest suites.

### Task 4: Build the Project Governance Skill and Read-Only Gates

**Files:**
- Create: `.agents/skills/portfolio-governance/SKILL.md` with `disable-model-invocation: true`.
- Create: `policies/governance-policy.v1.json`, `schemas/*.schema.json`, `benchmark/active-benchmarks.v1.json`, and review templates.
- Create: `scripts/portfolio-governance.ps1` and root wrapper `scripts/portfolio-governance.ps1`.
- Create: `scripts/portfolio-governance.test.ps1` using synthetic temporary workspaces.

**Interfaces:**
- Commands: `inspect`, `validate`, `benchmark`, and `build-review-pack` are safe candidate operations; all default to dry-run where public state could change.
- Workspace comes from `PORTFOLIO_GOVERNANCE_HOME` or `--workspace`; its resolved path must be outside the repository and must reject `..`, symlinks, and junctions resolving back into the worktree.
- Every run freezes `GovernanceRunSnapshot { runId, startedAt, inputFingerprint, schemaVersion, policyBundleHash, benchmarkDefinitionHash, toolVersion, candidatePayloadHash }`.
- Gates run in exact order: SchemaGate, ReferenceIntegrityGate, PrivacyGate, ClaimEvidenceGate, BenchmarkGate, CompatibilityGate, HumanApprovalGate.
- Machine output has `runId`, `command`, `inputFingerprint`, `status`, `artifacts`, `blockingFindings`, and `warnings`; no private absolute path is printed.

- [ ] Add failing script tests for missing workspace, in-repository path, traversal/junction, unknown fields, private-field leakage, dangling Link, invalid Claim verification, and stale fingerprints.
- [ ] Implement workspace containment and synthetic-fixture parsing.
- [ ] Implement fixed explicit gates and stable finding codes; no plugin/hook or `--force` bypass.
- [ ] Implement deterministic review-pack creation in the private sandbox with summary, changes, claims, links, privacy, benchmark, checksums, and approval request.
- [ ] Run the governance script tests and privacy checker against every public skill artifact.

### Task 5: Implement Benchmarks, Structured Feedback, Cases, and Playbooks

**Files:**
- Extend governance policy/benchmark fixtures and CLI script.
- Create immutable public `FeedbackSignal`, `GovernanceCase`, `BenchmarkCase`, and `PlaybookRule` schema documents/templates; do not add visitor free-text storage.
- Modify `AnswerDecision` only if needed to emit the already-approved anonymous B feedback dimensions; do not capture question or answer text.
- Test script and runtime whitelist behavior.

**Interfaces:**
- Feedback accepts only contentVersion, questionPresetId, resolution, answerSource, helpful, and fixed reason enum.
- Benchmark categories are CONTRACT, CONTENT, SAFETY; BLOCKER/ERROR fail validation, WARNING requires explicit human acknowledgement, INFO is non-blocking.
- Every active preset must have 100% critical coverage and pass rate for answer, alias, boundary, Claim/Evidence, and safety assertions.
- Case closure requires rootCause, resolution, fixedVersion, regressionBenchmarkCaseId, and playbook decision.

- [ ] Write failing schema/CLI tests rejecting free text, unknown reason, missing preset coverage, incomplete Case closure, and unapproved Playbook activation.
- [ ] Implement structural Benchmark evaluation against canonical candidate payload and deterministic Answer contract fixtures.
- [ ] Implement `case` command state transitions only through the CLI.
- [ ] Re-run script tests and runtime privacy tests.

### Task 6: Bind Human Approval to Canonical Payload

**Files:**
- Extend governance CLI, approval schema/template, and tests.
- Create explicit hash helpers in the skill script; reuse the same published test vectors as the Java hash calculator.

**Interfaces:**
- `approve` requires explicit operator invocation, an exact candidatePayloadHash, approvedBy alias, privacyReviewId, and benchmarkRunId.
- `approvalDigest` hashes a fixed-order canonical approval projection containing the candidate hash and review metadata; it never contains manifest/checksum/runtime hashes.
- Any candidate, policy, benchmark definition, schema, tool version, or input fingerprint change makes the run STALE and invalidates approval.
- Approval history is append-only in the private workspace; audit failure prevents approval completion.

- [ ] Add failing tests for hash reuse across candidates, changed payload bytes, changed policy/benchmark/tool, missing human identity, and audit-write failure.
- [ ] Implement approval-request verification and immutable approval/audit writes.
- [ ] Cross-check PowerShell and Java hash test vectors byte-for-byte.
- [ ] Re-run approval and stale-run tests.

### Task 7: Publish, Atomically Activate, Verify, and Roll Back

**Files:**
- Extend governance CLI publish/rollback/list/status/verify commands and tests.
- Create repository-local synthetic publication fixtures only; real publication targets remain user-specified external roots.
- Modify runtime active-bundle locator/readiness output and packaged-JAR tests.

**Interfaces:**
- Publish exact-copies approved payload to a temporary sibling directory, builds deterministic manifest/checksums, validates it into a `RuntimeContentSnapshot`, then atomically moves the complete version and replaces `active`.
- Publish defaults to dry-run and requires explicit per-command confirmation for state changes; no command auto-approves.
- Repeat publish of identical complete version is idempotent; same version with different bytes fails.
- Rollback targets only complete, compatible, previously published, unblocked versions and appends an audit record.
- Active-switch failure preserves old active; post-switch health failure restores the verified old active; invalid cold-start active fails closed.

- [ ] Add failing tests for dry-run, exact-byte preservation, idempotency, version collision, partial write, switch failure, post-switch rollback, blocked target, invalid cold start, and audit failure.
- [ ] Implement deterministic manifest/checksum construction after Approval with no retrieval fields or index directories.
- [ ] Implement safe resolved-path validation and atomic same-filesystem pointer replacement for Windows test fixtures; document Linux symlink semantics in the runbook.
- [ ] Add real packaged-JAR tests loading a generated B bundle and exposing the expected contentVersion/runtimeBundleHash.
- [ ] Re-run CLI, backend, privacy, and packaged-JAR suites.

### Task 8: Integrate Release Gates and Document B Truthfully

**Files:**
- Modify: `scripts/verify-release.ps1`, privacy/architecture checks and their self-tests as required.
- Modify: `README.md`, `SECURITY.md`, `docs/00-文档状态索引.md`, `docs/04-项目代码约束.md`, B design, docs/05, and docs/06 implementation-status sections.
- Do not mark C or retrieval sections implemented.

**Verification:**
- Governance: CLI self-tests, path isolation, schema/reference/privacy/Claim/Benchmark gates, hash vectors, Approval invalidation, dry-run, publish, verify, rollback, and audit failure.
- Backend: Maven tests, code quality, architecture, privacy, clean package, JAR content/hash checks.
- Frontend: check, lint, Vitest, build, mocked Playwright.
- Packaged JAR: schema-2 public content, structured Claim/Evidence response, active version, invalid bundle fail-closed, and all A privacy/accessibility flows.

- [ ] Add governance tests to the release script before any package is claimed releasable.
- [ ] Run all focused tests and then the complete release verification fresh.
- [ ] Re-read all 34 B acceptance criteria and record any unmet item as incomplete rather than weakening the design.
- [ ] Update only implemented status; keep C1/C2/C3, RAG, indexes, embeddings, models, tools, and multi-round behavior pending.
- [ ] Inspect `git diff --check`, staged diff, status, and all new public artifacts for privacy; do not stage or commit.

## Compatibility and Rollback Boundaries

- Deliberate contract migration: runtime source schema 1.0 and `supportedClaims` are no longer authoritative. API paths and A's four-dimensional Answer contract remain stable; Claim/Link references are additive to response content while legacy free-text support relationships disappear.
- Bootstrap compatibility: the JAR contains one fully validated schema-2 published bundle. External active content is optional only for first installation; once configured, a missing/invalid active bundle fails closed and cannot fall back silently.
- Public payload stability: approved bytes are immutable. Any correction creates a new contentVersion and new Approval.
- Rollback is whole-bundle activation only. No object-level rollback and no deletion of historical versions.
- Worktree rollback remains manual, file-scoped reversal of only B hunks because Git restore/delete is prohibited and the worktree contains user-owned changes.
- Principal risks: PowerShell/Java hash divergence, filesystem atomicity differences, accidentally serializing private review fields, computing verification from candidate status instead of Links, stale Approval reuse, and tests passing against bootstrap while external active loading is broken. Each risk has a cross-language or end-to-end regression test above.
