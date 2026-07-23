# Portfolio Content Asset Preparation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the private knowledge-base inventory into a human-reviewed, privacy-safe first batch of portfolio content candidates without publishing private material or changing the runtime schema prematurely.

**Architecture:** The private Obsidian inventory is the discovery source, while `PORTFOLIO_GOVERNANCE_HOME` remains the only governance workspace. This plan produces reviewable Project/Case candidate packets and explicit human decisions first; the later CaseStudy contract plan will add runtime schema support, and only then may the existing governance CLI build, approve, and publish a public bundle.

**Tech Stack:** Markdown, JSON, PowerShell 5.1+, existing `scripts/portfolio-governance.ps1`, schema-2 Claim/Evidence governance, Git for public documentation only.

## Global Constraints

- Runtime code and the public repository must never read the private Obsidian knowledge base, raw daily reports, credentials, internal screenshots, or unreviewed candidate files.
- Knowledge-base synchronization does not grant publication approval.
- Do not copy private source text into this repository; public wording must be newly written, minimized, anonymized, and reviewed.
- Never include company/product/person names, internal domains, IP addresses, ports, hosts, filesystem paths, credentials, player/account/team identifiers, raw logs, unredacted SQL, or unreviewed screenshots.
- Use `AchievementStatus` for completion, `ContributionType` for personal contribution, and explicit limitation Claims for work that was implemented but not confirmed released.
- A Candidate, Claim, Evidence, Link, QuestionPreset, or Bundle cannot become `APPROVED` through test success or agent judgment. Human approval remains mandatory.
- Operate governance state only through `scripts/portfolio-governance.ps1`; never edit Approval, publish state, or audit state directly.
- This plan prepares content only. It does not modify Java/TypeScript runtime types, build a CaseStudy API, redesign pages, enable models, enable retrieval, deploy, approve, publish, commit, or push.

---

### Task 1: Freeze the Private Candidate Inventory and Source Boundary

**Files:**
- Private source of truth: `docs/作品集内容候选清单-2026-07-23.md` in the user-owned Obsidian knowledge base.
- Read-only public constraints: `AGENTS.md`, `SECURITY.md`, `docs/04-项目代码约束.md`.
- Private governance output: `$env:PORTFOLIO_GOVERNANCE_HOME\candidates\2026-07-23-inventory-boundary.md`.

**Interfaces:**
- Consumes: candidate IDs `M-01` through `M-06`, `F-01` through `F-04`, `I-01` through `I-08`, `E-01` through `E-05`, and the knowledge-output inventory.
- Produces: a private boundary record containing only candidate ID, content type, proposed public priority, source category, and `PUBLIC_REVIEW_REQUIRED`.

- [ ] **Step 1: Confirm the governance workspace is configured outside the repository**

Run:

```powershell
$repoRoot = (git rev-parse --show-toplevel).Trim()
$workspaceRoot = (Resolve-Path -LiteralPath $env:PORTFOLIO_GOVERNANCE_HOME).Path
if ($workspaceRoot.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Governance workspace must be outside the Git worktree.' }
```

Expected: the command exits successfully without printing the private workspace path. If the environment variable is missing or resolves inside the repository, stop before reading or writing candidate files.

- [ ] **Step 2: Run the existing read-only governance inspection**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/portfolio-governance.ps1 inspect
```

Expected: machine-readable output has `command = "inspect"`; no publish, approval, or public-file write occurs.

- [ ] **Step 3: Build the private boundary record from the approved inventory IDs**

Write the following headings to the private boundary record, without copying source prose:

```markdown
# Candidate Boundary Record

| Candidate ID | Type | Proposed priority | Source category | Review state |
|---|---|---|---|---|
| M-01 | MAINLINE | P0 | delivery records + design + verification | PUBLIC_REVIEW_REQUIRED |
| F-02 | FEATURE | P0 | design + regression verification | PUBLIC_REVIEW_REQUIRED |
| F-01 | FEATURE | P0 | usage guide + acceptance flow | PUBLIC_REVIEW_REQUIRED |
| E-01..E-04 | EVALUATION | P0 | benchmark reports | PUBLIC_REVIEW_REQUIRED |
| I-01 | INCIDENT | P1 | investigation notes | CONTRIBUTION_REVIEW_REQUIRED |
| F-03 | FEATURE | P1 | design + self-test | RELEASE_STATUS_REVIEW_REQUIRED |
```

Expected: the file contains no company names, internal identifiers, absolute source paths, credentials, raw logs, SQL, or screenshots.

- [ ] **Step 4: Scan the private boundary record for prohibited patterns**

Run a private-workspace scan for IPv4 addresses, Windows absolute paths, URLs, emails, `token`, `password`, `secret`, `teamId`, and raw SQL verbs. Do not print matching line content; report only pattern name and match count.

Expected: zero matches. Any match blocks Task 2 until removed or explicitly classified as a false positive without exposing the value.

### Task 2: Record Human Decisions for Contribution, Completion, and Publication Eligibility

**Files:**
- Read: private candidate inventory and boundary record.
- Create through the private review workflow: `$env:PORTFOLIO_GOVERNANCE_HOME\reviews\2026-07-23-candidate-decisions.md`.

**Interfaces:**
- Consumes: the six unresolved decision groups below.
- Produces: one explicit decision per candidate with `contentType`, `achievementStatus`, `contributionType`, `publicationDecision`, and `decisionReason`.

- [ ] **Step 1: Present the unresolved decisions to the human owner**

Request explicit answers for:

```text
M-02 / I-01..I-08: PRIMARY, COLLABORATIVE, ASSISTED, or OBSERVED_LEARNING?
F-03: was the implementation ever committed or released after the recorded self-test?
M-04: is any source code or only screenshots/notes available for public evidence?
E-01..E-04: may anonymized benchmark numbers be published?
Knowledge outputs: which complete articles may be shown publicly?
I-01: may the incident mechanism be described after replacing all product/activity names?
```

Expected: no inferred answer. An unanswered item remains `HOLD`.

- [ ] **Step 2: Record decisions with the fixed vocabulary**

Use this exact structure for every candidate:

```markdown
## F-03

- contentType: FEATURE
- achievementStatus: IMPLEMENTED_TESTED
- contributionType: PRIMARY
- publicationDecision: HOLD
- decisionReason: Release status is not confirmed by the owner.
- approvedBy: human-owner
- approvedAt: 2026-07-23T00:00:00+08:00
```

Replace the timestamp with the actual decision time. `publicationDecision` is one of `PROCEED`, `HOLD`, or `EXCLUDE`; it is not Bundle Approval.

- [ ] **Step 3: Verify decision completeness**

Expected: every P0/P1 candidate has exactly one decision block. Items without a human answer remain `HOLD`; no agent upgrades status or contribution.

### Task 3: Prepare the SQL Audit Mainline Expansion Packet

**Files:**
- Read-only current public facts: `backend/src/main/resources/public-data/bundle/portfolio.json`.
- Private candidate output: `$env:PORTFOLIO_GOVERNANCE_HOME\candidates\sql-audit-2026-07-expansion.json`.
- Private review output: `$env:PORTFOLIO_GOVERNANCE_HOME\reviews\sql-audit-2026-07-expansion.md`.

**Interfaces:**
- Consumes: existing project ID `sql-audit-project`, existing Claims/Evidence, and the approved July iteration facts.
- Produces: additive Claim/Evidence/Timeline/QuestionPreset candidates; it must not rewrite the already approved June facts or claim long-term production impact.

- [ ] **Step 1: Draft four minimal additive Claims**

Use these candidate meanings, rewritten as privacy-safe public Chinese copy:

```json
[
  {
    "id": "claim-sql-audit-fixed-string-search",
    "category": "TECHNICAL_DECISION",
    "achievementStatus": "IMPLEMENTED_TESTED",
    "contributionType": "PRIMARY",
    "materiality": "KEY",
    "meaning": "Negative-leading keywords are treated as fixed search text rather than command options."
  },
  {
    "id": "claim-sql-audit-source-selection",
    "category": "IMPLEMENTATION",
    "achievementStatus": "IMPLEMENTED_TESTED",
    "contributionType": "PRIMARY",
    "materiality": "KEY",
    "meaning": "The UI supports one server target and multiple approved log sources."
  },
  {
    "id": "claim-sql-audit-partial-success",
    "category": "IMPLEMENTATION",
    "achievementStatus": "IMPLEMENTED_TESTED",
    "contributionType": "PRIMARY",
    "materiality": "KEY",
    "meaning": "Successful source results are preserved while failed sources can be retried independently."
  },
  {
    "id": "claim-sql-audit-selected-target-check",
    "category": "VERIFICATION",
    "achievementStatus": "IMPLEMENTED_TESTED",
    "contributionType": "PRIMARY",
    "materiality": "SUPPORTING",
    "meaning": "Connection checks are limited to the targets selected for the current task."
  }
]
```

Expected: final statements contain no shell command, host, path, product name, internal table, real environment, or raw screenshot reference.

- [ ] **Step 2: Define one new Evidence collection candidate**

Use ID `sql-audit-july-iteration-set`, type `COLLECTION`, a July 2026 period, and a summary limited to the four Claims above. `sourceCount` must equal the number of private source items the human reviewer actually accepts; do not infer the number from daily-note count.

- [ ] **Step 3: Add DIRECT links with narrow support scopes**

Each KEY Claim must have at least one DIRECT link to `sql-audit-july-iteration-set`. Link scope must state exactly which implementation or verification fact is supported and explicitly exclude production-scale or long-term-effect claims.

- [ ] **Step 4: Draft one July TimelineEvent and two QuestionPresets**

QuestionPreset topics:

```text
1. Why was negative-leading keyword input unsafe or broken, and how was it handled without accepting arbitrary commands?
2. How does partial success preserve completed sources and retry only failed sources?
```

Expected: aliases remain semantically equivalent; they do not add deployment, scale, latency, or ownership claims.

- [ ] **Step 5: Build a human-readable private diff**

Expected: the review file shows old public facts, proposed additive facts, privacy findings, unsupported inferences, and the exact source-count decision. It does not contain raw private source content.

### Task 4: Prepare Three Non-Project Case Candidate Packets

**Files:**
- Private candidate outputs:
  - `$env:PORTFOLIO_GOVERNANCE_HOME\candidates\case-multilingual-upload.json`
  - `$env:PORTFOLIO_GOVERNANCE_HOME\candidates\case-role-reset.json`
  - `$env:PORTFOLIO_GOVERNANCE_HOME\candidates\case-codegraph-evaluation.json`
- Private review outputs under `$env:PORTFOLIO_GOVERNANCE_HOME\reviews\` with matching filenames.

**Interfaces:**
- Consumes: human decisions from Task 2.
- Produces: CaseStudy-shaped candidate data for the later public contract plan. These files are intentionally not fed into the current schema-2 bundle validator.

- [ ] **Step 1: Draft the multilingual upload Feature case**

Use this exact semantic contract:

```json
{
  "id": "case-multilingual-upload",
  "type": "FEATURE",
  "title": "多语言图片上传结果保留修复",
  "achievementStatus": "DELIVERED",
  "contributionType": "PRIMARY",
  "problemMeaning": "A later language upload replaced the visible language set from earlier uploads.",
  "actionMeaning": "Persisted languages became the union of existing stored languages and languages actually uploaded in the current request.",
  "verificationMeaning": "Sequential uploads in two different languages remained visible together."
}
```

Expected: do not name the internal Manager module, table, channel, region, CDN, or original language identifiers unless the human reviewer approves the generic term.

- [ ] **Step 2: Draft the role-reset Feature case**

Use title `测试角色重置工具`, type `FEATURE`, and a four-step acceptance flow: select approved environment, query by supplied identifier, confirm destructive reset, verify the old role is unavailable and a new role is created after login. Replace all example identifiers with abstract labels; do not publish deletion SQL or table names.

- [ ] **Step 3: Draft the CodeGraph Evaluation case**

Use type `EVALUATION`, status `PROTOTYPE`, contribution `PRIMARY`, and preserve both positive and negative findings:

```text
Method: symbol lookup benchmark plus two MCP task suites.
Positive finding: exact/FTS symbol lookup materially reduced lookup latency in the selected samples.
Negative finding: method references, Lambda, dependency injection, event dispatch, and duplicate symbol names caused missed or noisy relationships.
Decision: use CodeGraph for narrowing and batch navigation; use grep/Read for precision verification.
Conservative estimate: 15%-25% overall improvement in the tested project, not a universal claim.
```

Expected: raw project name, repository size details, internal symbols, and source code samples are excluded unless separately approved.

- [ ] **Step 4: Add one overview QuestionPreset and one Evidence collection per Case**

Each overview asks for problem/method, personal contribution, verification, result, and limitation. Each Evidence collection supports only its own Case Claims; no Evidence is reused merely to increase verification status.

- [ ] **Step 5: Mark all three packets `AWAITING_CASESTUDY_CONTRACT`**

Expected: no attempt is made to insert Case data into `projects`, no fake Project is created, and no current public bundle file changes.

### Task 5: Prepare the First Public-Contract Handoff

**Files:**
- Modify after separate design approval: `docs/superpowers/specs/2026-07-23-portfolio-case-study-public-contract-design.md`.
- Create after that spec is approved: `docs/superpowers/plans/2026-07-23-portfolio-case-study-public-contract.md`.
- Read: the four candidate packets from Tasks 3 and 4.

**Interfaces:**
- Consumes: one Project expansion packet and three Case candidate packets.
- Produces: a schema/API implementation specification covering `CaseStudy`, `CaseType`, Claim subject `CASE`, optional parent project, public API DTOs, frontend types, validator rules, release compatibility, and migration tests.

- [ ] **Step 1: Confirm the content packets are sufficient to drive the contract**

Expected fields across the Case packets:

```text
id, slug, code, type, title, summary, problem, actions[], decisions[], verification[], outcome,
limitations[], achievementStatus, contributionType, optional projectId, claimIds, evidenceIds,
timelineEventIds, questionPresetIds
```

If a field cannot be populated truthfully for a small Case, the design must make it optional or omit it from the contract; do not invent filler text.

- [ ] **Step 2: Freeze compatibility requirements for the later design**

Required compatibility behavior:

```text
- Existing schema-2 Project bundle remains loadable during migration tests.
- New bundles use a new schema version and cannot be silently read as schema 2.0.
- Project API routes keep their current meaning.
- New Case routes and combined work-library projections are additive.
- Claim/Evidence links remain the only verification authority.
- Unknown Case IDs fail closed and never fall back to the first item.
```

- [ ] **Step 3: Stop before implementation and request design approval**

Expected: the next plan is not written until the CaseStudy public contract design is reviewed and approved. Agent, frontend, publication, commit, and push remain out of scope for this content-preparation plan.

## Self-Review Results

- Spec coverage: covers full candidate inventory, contribution/status decisions, privacy boundary, first Project expansion, three non-project Cases, and the handoff to a separate CaseStudy contract design.
- Placeholder scan: no unresolved placeholder marker, generic error-handling instruction, or unbounded “write tests” step remains.
- Type consistency: content types use `MAINLINE`, `FEATURE`, `INCIDENT`, `EVALUATION`, `KNOWLEDGE_OUTPUT`; public completion uses existing `AchievementStatus`; personal contribution uses existing `ContributionType`; Case publication waits for an explicit new contract.
