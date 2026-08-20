# Project Evolution Log and Chinese Document Naming Implementation Plan
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the five numbered project documents to Chinese names, create a detailed `2026-07-14`–`2026-07-30` Agent project evolution log, and make future log maintenance a repository rule.

**Architecture:** Keep the existing `docs` directory topology unchanged. Perform five path-only renames, update every exact old-path reference, then create one chronological living history document whose claims are calibrated against the authority index, current implementation inventory, design documents, reports, decisions, and the `master` timeline.

**Tech Stack:** Markdown, Git path history, PowerShell/Ripgrep for read-only reference checks, repository documentation conventions.

## Global Constraints

- Preserve all user-owned uncommitted changes; modify overlapping untracked plans only at the exact old path literals required by the rename.
- Do not stage, commit, push, reset, restore, or discard changes without explicit user authorization.
- Keep `docs/decisions`, `docs/handoffs`, `docs/reports`, `docs/superpowers/specs`, and `docs/superpowers/plans` in their current locations.
- Keep document numbers `05–11` and use the exact Chinese names approved in the design. `10` is the local PostgreSQL / pgvector runbook and is named `10-本地PostgreSQL与pgvector运行手册.md`.
- The evolution log records capabilities, important behavior fixes, product decisions, and technology evolution; it does not record code steps, test procedures, commit hashes, or commit ranges.
- Distinguish design from implementation and use the fixed states `提出`, `选定`, `已实现`, `受限启用`, `默认关闭`, `已取代`, and `未部署`.
- Use relative Markdown links to real design, decision, handoff, or report files.
- Do not claim production deployment or live Provider verification where the authoritative status documents say those remain incomplete.

---

## File Structure

### Rename

- `docs/05-public-release-bundle-contract.md` → `docs/05-公开发布包契约.md`
- `docs/06-content-publishing-runbook.md` → `docs/06-公开内容发布运行手册.md`
- `docs/07-modular-monolith-backend-review.md` → `docs/07-模块化单体后端审核记录.md`
- `docs/08-current-implementation-status.md` → `docs/08-当前实现状态.md`
- `docs/09-portfolio-asset-library-status.md` → `docs/09-作品集资产库状态.md`

### Create

- `docs/11-项目演进日志.md`: chronological capability and decision history.

### Modify

- `AGENTS.md`: document routing and evolution-log maintenance rules.
- `README.md`: current documentation links.
- `docs/00-文档状态索引.md`: renamed paths and evolution-log authority row.
- `docs/04-项目代码约束.md`: renamed current-status reference.
- The exact design and plan files returned by the old-path reference scan in Task 2.
- `docs/superpowers/specs/2026-07-30-project-evolution-log-and-document-naming-design.md`: final path references and implemented status after completion.
- `docs/superpowers/plans/2026-07-30-project-evolution-log-and-document-naming.md`: checkbox/status updates only while executing.

---

### Task 1: Rename the Five Numbered Project Documents

**Files:**

- Rename the five files listed under “File Structure”.

**Interfaces:**

- Consumes: existing document contents without semantic changes.
- Produces: the five approved Chinese paths used by Tasks 2–5.

- [ ] **Step 1: Recheck the five source and target paths**

Run:

```powershell
$pairs = @(
  @('docs/05-public-release-bundle-contract.md', 'docs/05-公开发布包契约.md'),
  @('docs/06-content-publishing-runbook.md', 'docs/06-公开内容发布运行手册.md'),
  @('docs/07-modular-monolith-backend-review.md', 'docs/07-模块化单体后端审核记录.md'),
  @('docs/08-current-implementation-status.md', 'docs/08-当前实现状态.md'),
  @('docs/09-portfolio-asset-library-status.md', 'docs/09-作品集资产库状态.md')
)
$pairs | ForEach-Object {
  [pscustomobject]@{
    Source = $_[0]
    SourceExists = Test-Path -LiteralPath $_[0]
    Target = $_[1]
    TargetExists = Test-Path -LiteralPath $_[1]
  }
}
```

Expected: every source exists and every target does not exist.

- [ ] **Step 2: Rename each file without changing its contents**

Use patch move operations for the five exact source/target pairs. Do not rewrite file bodies during this step.

- [ ] **Step 3: Verify the path-only rename**

Run:

```powershell
Get-ChildItem -LiteralPath docs -File |
  Where-Object { $_.Name -match '^(05|06|07|08|09)-' } |
  Sort-Object Name |
  Select-Object Name,Length
```

Expected: exactly the five Chinese filenames appear, with non-zero lengths.

---

### Task 2: Replace Every Effective Old-Path Reference

**Files:**

- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `docs/00-文档状态索引.md`
- Modify: `docs/04-项目代码约束.md`
- Modify:
  - `docs/superpowers/plans/2026-07-16-portfolio-result-validation-boundary-refactor.md`
  - `docs/superpowers/plans/2026-07-22-portfolio-agent-c2a-local-public-retrieval.md`
  - `docs/superpowers/plans/2026-07-22-portfolio-agent-light-workspace-palette.md`
  - `docs/superpowers/plans/2026-07-23-portfolio-asset-library-ingestion.md`
  - `docs/superpowers/plans/2026-07-23-portfolio-case-study-public-contract.md`
  - `docs/superpowers/plans/2026-07-24-portfolio-agent-conversational-backend.md`
  - `docs/superpowers/plans/2026-07-24-public-content-waves-1-3-implementation-plan.md`
  - `docs/superpowers/plans/2026-07-24-retrieval-baseline-comparison-implementation-plan.md`
  - `docs/superpowers/plans/2026-07-28-agent-production-readiness.md`
  - `docs/superpowers/plans/2026-07-28-portfolio-v1-case-and-release-closure.md`
  - `docs/superpowers/plans/2026-07-29-frontend-diagnostics-recovery.md`
  - `docs/superpowers/plans/2026-07-29-observability-core-error-contract.md`
  - `docs/superpowers/plans/2026-07-30-postgresql-portfolio-composition-backend.md`
- Modify:
  - `docs/superpowers/specs/2026-07-21-portfolio-agent-content-governance-design.md`
  - `docs/superpowers/specs/2026-07-22-job-seeking-portfolio-completion-roadmap-design.md`
  - `docs/superpowers/specs/2026-07-23-portfolio-asset-library-ingestion-design.md`
  - `docs/superpowers/specs/2026-07-24-full-public-assets-and-hybrid-retrieval-evaluation-design.md`
  - `docs/superpowers/specs/2026-07-28-portfolio-v1-case-and-release-closure-design.md`
  - `docs/superpowers/specs/2026-07-30-project-evolution-log-and-document-naming-design.md`

**Interfaces:**

- Consumes: the five Chinese target paths from Task 1.
- Produces: a repository in which no effective instruction points at a removed path.

- [ ] **Step 1: Capture the exact replacement map**

Use only these literal substitutions:

```text
docs/05-public-release-bundle-contract.md
→ docs/05-公开发布包契约.md

docs/06-content-publishing-runbook.md
→ docs/06-公开内容发布运行手册.md

docs/07-modular-monolith-backend-review.md
→ docs/07-模块化单体后端审核记录.md

docs/08-current-implementation-status.md
→ docs/08-当前实现状态.md

docs/09-portfolio-asset-library-status.md
→ docs/09-作品集资产库状态.md
```

- [ ] **Step 2: Apply exact literal path replacements**

Update only the five exact path strings. Do not reformat surrounding historical plans or change their original completion statements.

- [ ] **Step 3: Scan for stale old paths**

Run:

```powershell
rg -n --encoding utf-8 `
  "docs/(05-public-release-bundle-contract|06-content-publishing-runbook|07-modular-monolith-backend-review|08-current-implementation-status|09-portfolio-asset-library-status)\.md" `
  . `
  -g '!node_modules/**' `
  -g '!.git/**' `
  -g '!frontend/dist/**' `
  -g '!backend/target/**'
```

Expected: no matches, except the old-path column inside the approved rename design and implementation plan where the mapping is intentionally documented.

- [ ] **Step 4: Confirm all new paths are referenced and exist**

Run:

```powershell
$targets = @(
  'docs/05-公开发布包契约.md',
  'docs/06-公开内容发布运行手册.md',
  'docs/07-模块化单体后端审核记录.md',
  'docs/08-当前实现状态.md',
  'docs/09-作品集资产库状态.md'
)
$targets | ForEach-Object {
  [pscustomobject]@{
    Path = $_
    Exists = Test-Path -LiteralPath $_
    ReferenceFiles = @(rg -l --fixed-strings --encoding utf-8 $_ . -g '!node_modules/**' -g '!.git/**').Count
  }
}
```

Expected: every file exists and has at least one reference.

---

### Task 3: Create and Backfill the Agent Project Evolution Log

**Files:**

- Create: `docs/11-项目演进日志.md`

**Interfaces:**

- Consumes:
  - `AGENTS.md`
  - `docs/00-文档状态索引.md`
  - `docs/08-当前实现状态.md`
  - dated specs/plans under `docs/superpowers`
  - `docs/decisions`, `docs/handoffs`, and `docs/reports`
  - `master` commit dates and subjects only as sequencing evidence
- Produces: one chronological, reader-facing project history through 2026-07-30.

- [ ] **Step 1: Create the document header and status vocabulary**

Create:

```markdown
# Agent 项目演进日志

> 本文按日期和开发阶段记录 Agent 项目功能、重要修复、产品决策与技术选型的演进。
> 本文不记录具体代码步骤、测试过程或 Git 提交信息；当前能力边界仍以《当前实现状态》和《文档状态索引》为准。

## 状态说明

- **提出**：形成想法或待确认方向。
- **选定**：完成方案选择，但尚未实现。
- **已实现**：能力已经进入当前项目。
- **受限启用**：已经实现，但受审批、配置或环境限制。
- **默认关闭**：已经实现，但默认运行路径不启用。
- **已取代**：曾经采用，后来被新方案替换。
- **未部署**：本地能力已经完成，尚未进入生产环境。
```

- [ ] **Step 2: Backfill 2026-07-14 through 2026-07-17**

Add dated sections and separate stages for:

- `2026-07-14`: public internship portfolio Agent positioning; reviewed-public-data boundary; deterministic V0 vertical slice; first architecture-refactor direction.
- `2026-07-15`: benchmark card overlap correction; dynamic-public-content design; final backend-remediation direction; clearly mark designs later superseded by narrower authoritative designs.
- `2026-07-16`: backend baseline; modular-monolith `common / portfolio / answer` boundaries; architecture gates; frontend six-route rebuild; result/validation boundary clarification.
- `2026-07-17`: reviewed Timeline facts; aggregate public-content API; shared frontend loading/retry state; answer API connection; stale-request protection; packaged single-JAR integration baseline.

Link each stage to the matching dated specs and plans. Do not include commit IDs.

- [ ] **Step 3: Backfill 2026-07-20 through 2026-07-23**

Add dated sections and separate stages for:

- `2026-07-20`: runtime-trust design—four-dimensional answer contract, snapshot consistency, in-memory visitor privacy, fail-closed behavior.
- `2026-07-21`: Claim/Evidence governance, approval/publication boundaries, future-intelligence roadmap, and staged C1/C2/C3 capability separation.
- `2026-07-22`: runtime trust and content governance implementation; C1 constrained model expression; C2a local retrieval; C2b fixed read-only tools and citation-based multiturn; C3 built-in two-provider Registry only; all required default-off/approval boundaries; light Agent workspace palette; job-seeking portfolio completion roadmap.
- `2026-07-23`: asset-library governance; CaseStudy schema 3.0 and public APIs; first verified Case publication; warm floating responsive Agent workspace; session rail; immediate conversation state; evidence/citation workspace and bidirectional focus; release and privacy boundary hardening.

Use the status index to avoid describing unadmitted C3 Tool Registry, Hook, Orchestrator, multi-Agent, DurableTask, persistent sessions, database, or authentication as implemented.

- [ ] **Step 4: Backfill 2026-07-24**

Create distinct stages for:

- Case-aware answer tooling, public case dossiers, Case-aware RAG content, and dossier navigation.
- Real local Keyword/Vector/Hybrid retrieval benchmark contract, metrics, CLI, immutable report binding, and grouped evaluation evidence.
- Conversational Agent v2: intent routing, 20-turn ephemeral window, constrained Provider port, approved visitor-data gate, fine-grained public context, read-only tool budget, factual/citation validation, dynamic answerable suggestions, and frontend/backend integration.
- Structured-output contract repair and dynamic suggested-question parsing.
- Asset decision binding, reproducible governance runtime, verified seven-file release import, and rollback hardening.

Mark Provider use, local retrieval, and production deployment according to their current restricted/default-off/not-deployed states.

- [ ] **Step 5: Backfill 2026-07-27 through 2026-07-30**

Create dated sections and separate stages for:

- `2026-07-27`: Wave 1 candidate governance; holdout isolation; governance bypass closure; retrieval policy v2.1 safety correction; first-wave public asset import; frontend tokens, self-hosted fonts, accessibility, answer-label and E2E contract closure.
- `2026-07-28`: full public asset expansion and 89-case comparison; schema 3.0 full runtime bundle; Case/release closure and unknown-subject fail-closed guard; live-provider release gate wiring; production-readiness design; request token, production state, anonymous source budget, and admission controls.
- `2026-07-29`: idempotency, total timeout, rate/concurrency controls, bounded cache cleanup, frontend cancellation/retry/status; independent Case list/detail and privacy-preserving Case-to-Agent handoff; homepage Case entry; structured backend/frontend observability; unified navigation; Project/Case/Collection domain model and schema 4.0 migration.
- `2026-07-30`: Project mainline and Case collection/filter information architecture; canonical route redirect evolution; PostgreSQL public runtime database and private governance database; Markdown incremental scanning/import; hybrid recall and deterministic complementary portfolio composition; R0–R4 evaluation and migration completeness gates; recursive release-artifact privacy scanning.

Link to the relevant specs, reports, decision, and handoff documents. Mark PostgreSQL paths as default-off and the project as not production-deployed.

- [ ] **Step 6: Review the log for forbidden implementation detail and state inflation**

Run:

```powershell
rg -n --encoding utf-8 `
  "git commit|commit [0-9a-f]{7,}|mvn|npm|pytest|PASS|FAIL|提交哈希|提交范围|已生产部署|C3.*(多 Agent|Orchestrator|DurableTask).*已实现" `
  docs/11-项目演进日志.md
```

Expected: no command/test/commit-detail matches and no inflated production/C3 claim.

- [ ] **Step 7: Check every Markdown link in the log**

For each relative link target in `docs/11-项目演进日志.md`, resolve it relative to `docs` and require `Test-Path -LiteralPath` to return `True`.

Expected: zero missing targets.

---

### Task 4: Add Repository Maintenance Rules and Navigation

**Files:**

- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `docs/00-文档状态索引.md`
- Modify: `docs/superpowers/specs/2026-07-30-project-evolution-log-and-document-naming-design.md`

**Interfaces:**

- Consumes: final paths and the completed evolution log.
- Produces: future-agent routing rules and discoverable project documentation.

- [ ] **Step 1: Update AGENTS.md source-of-truth paths**

Replace the old `08` path with `docs/08-当前实现状态.md` and retain the existing authority order.

- [ ] **Step 2: Add scoped document-routing rules**

Add a concise `## Documentation maintenance` section containing these exact requirements in natural project language:

```markdown
## Documentation maintenance

- Complete each independent feature, important behavior fix, product-boundary change, or technology-selection change by updating `docs/11-项目演进日志.md` before ending the task.
- Record what changed, how it relates to the previous direction, and its current state. Do not record implementation steps, test procedures, or commit metadata.
- Pure formatting changes, test-only additions, and behavior-preserving mechanical refactors do not need a separate evolution-log entry.
- When a capability, default switch, or product boundary changes, also update `docs/08-当前实现状态.md`.
- When public assets, governance waves, or publication status change, also update `docs/09-作品集资产库状态.md`.
- Changes to release bundles or content publication must read and follow `docs/05-公开发布包契约.md` and `docs/06-公开内容发布运行手册.md`.
```

- [ ] **Step 3: Register the evolution log in the authority index**

Add `docs/11-项目演进日志.md` as the chronological history entry. State that it does not override `AGENTS.md`, `04`, `05–06`, or `08–09`.

- [ ] **Step 4: Add the evolution log to README documentation entry points**

Add one reader-facing link describing it as the chronological record of functions, decisions, and technology evolution.

- [ ] **Step 5: Mark the design implemented**

Change the design status from:

```text
状态：设计已确认，待实施
```

to:

```text
状态：已实施
```

only after Tasks 1–4 are complete.

---

### Task 5: Repository-Wide Documentation Verification

**Files:**

- Inspect all files changed by Tasks 1–4.

**Interfaces:**

- Consumes: renamed files, updated references, completed log, and maintenance rules.
- Produces: evidence that documentation is internally navigable and user changes remain preserved.

- [ ] **Step 1: Verify the final numbered documentation layout**

Run:

```powershell
Get-ChildItem -LiteralPath docs -File |
  Sort-Object Name |
  Select-Object -ExpandProperty Name
```

Expected: `00` through `11` appear in order; `10` remains the local PostgreSQL / pgvector runbook, and `05–09` plus `11` use their approved Chinese names.

- [ ] **Step 2: Verify no stale effective paths remain**

Run the Task 2 stale-path scan and manually confirm any remaining matches appear only in the intentional old→new mapping tables of the approved design and implementation plan.

- [ ] **Step 3: Verify required maintenance rules**

Run:

```powershell
rg -n --encoding utf-8 `
  "11-项目演进日志|08-当前实现状态|09-作品集资产库状态|05-公开发布包契约|06-公开内容发布运行手册" `
  AGENTS.md README.md docs/00-文档状态索引.md
```

Expected: the required paths appear in their intended routing or navigation roles.

- [ ] **Step 4: Review the working-tree delta without altering it**

Run:

```powershell
git status --short
git diff --stat
git diff -- AGENTS.md README.md docs
```

Expected:

- the pre-existing modified observability design remains present;
- pre-existing untracked files remain present;
- five old files appear as renames or delete/add pairs with content preserved;
- the new design, implementation plan, and evolution log appear;
- no production source code, configuration, public data, credentials, build outputs, or unrelated user files are changed by this task.

- [ ] **Step 5: Stop without staging or committing**

Report the exact changed-document scope and ask separately if the user wants these documentation changes staged or committed.

## Self-Review

- Spec coverage: Tasks 1–5 cover the five Chinese renames, reference migration, historical backfill, state vocabulary, `AGENTS.md` maintenance rules, index/README navigation, and final path verification.
- Placeholder scan: the plan contains no `TBD`, `TODO`, deferred implementation placeholder, or unspecified error-handling step.
- Naming consistency: every task uses the same approved paths `05-公开发布包契约.md`, `06-公开内容发布运行手册.md`, `07-模块化单体后端审核记录.md`, `08-当前实现状态.md`, `09-作品集资产库状态.md`, and `11-项目演进日志.md`.
- Safety: the plan explicitly preserves current uncommitted work and forbids staging, committing, resetting, restoring, or pushing without separate authorization.
