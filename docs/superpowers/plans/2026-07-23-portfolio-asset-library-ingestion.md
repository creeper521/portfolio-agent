# Portfolio Asset Library Ingestion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 将本地知识库的 68 项已盘点资产登记到仓库外私有治理区，完成首批四项脱敏候选包，并在公开项目中同步安全的资产准备状态。

**Architecture:** 原始 Obsidian 内容只作为人工发现来源；结构化候选、审核决定和扫描报告保存在 `PORTFOLIO_GOVERNANCE_HOME`。公开项目只接收新写的状态文档和契约交接信息，当前 schema 2.0 bundle、API、Agent 和前端保持不变。

**Tech Stack:** Markdown、JSON、PowerShell 5.1+、Node.js、现有 `scripts/portfolio-governance.ps1`、Git。

## Global Constraints

- 公开仓库和运行时不得读取私有 Obsidian 知识库或治理候选目录。
- 不复制原始日报、截图、代码、SQL、日志、内部标识、IP、URL、主机、路径或凭据到公开项目。
- 资产登记不等于公开批准；脚本和模型不得升级人工贡献、完成状态或发布状态。
- 所有治理命令通过 `scripts/portfolio-governance.ps1` 执行；不得手工编辑 Approval、Audit、Publish 或 Rollback 状态。
- 本阶段不修改 `backend/src/main/resources/public-data/`、Java、TypeScript、Agent、前端页面或部署配置。
- 保留并避开当前工作区已有的前端视觉改动。
- 不暂存、提交或推送任何文件，除非用户另行明确授权。

---

### Task 1: Initialize the Private Governance Workspace

**Files:**
- Create privately: `$PORTFOLIO_GOVERNANCE_HOME/candidates/.gitkeep`
- Create privately: `$PORTFOLIO_GOVERNANCE_HOME/reviews/.gitkeep`
- Create privately: `$PORTFOLIO_GOVERNANCE_HOME/reports/.gitkeep`
- Read: `scripts/portfolio-governance.ps1`

**Interfaces:**
- Consumes: a process-local `PORTFOLIO_GOVERNANCE_HOME` outside the public Git worktree.
- Produces: an inspectable private workspace with no approval, audit or publication state.

- [x] **Step 1: Create the private directory skeleton**

Create `candidates`, `reviews`, and `reports` below the private workspace. Do not create `approvals`, `audit`, `published`, or release-state files.

- [x] **Step 2: Prove the workspace is outside the project**

Run:

```powershell
$repo = (Resolve-Path -LiteralPath '.').Path
$workspace = (Resolve-Path -LiteralPath $env:PORTFOLIO_GOVERNANCE_HOME).Path
if ($workspace.StartsWith($repo, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Private governance workspace is inside the public repository.'
}
'WORKSPACE_BOUNDARY_PASS'
```

Expected: `WORKSPACE_BOUNDARY_PASS`; the command must not print the workspace value.

- [x] **Step 3: Execute the governance read-only inspection**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio-governance.ps1 `
  -Command inspect `
  -Workspace $env:PORTFOLIO_GOVERNANCE_HOME
```

Expected: JSON with `command = "inspect"` and `status = "PASS"`.

### Task 2: Build and Validate the Full Asset Inventory

**Files:**
- Read privately: `docs/项目任务全量盘点-2026-07-23.md`
- Create privately: `$PORTFOLIO_GOVERNANCE_HOME/candidates/asset-library-2026-07-23.json`
- Create privately: `$PORTFOLIO_GOVERNANCE_HOME/reports/asset-library-structure.json`

**Interfaces:**
- Consumes: IDs `L-01..L-07`, `T-01..T-19`, `A-01..A-25`, and `K-01..K-17`.
- Produces: exactly 68 unique inventory records.

- [x] **Step 1: Write the inventory envelope**

Use this top-level structure:

```json
{
  "inventoryVersion": "2026-07-23.1",
  "reviewState": "PRIVATE_CANDIDATE",
  "counts": {
    "MAINLINE": 7,
    "TASK": 19,
    "INCIDENT": 25,
    "KNOWLEDGE_ASSET": 17,
    "TOTAL": 68
  },
  "assets": []
}
```

- [x] **Step 2: Map every source row into a conservative record**

Each `assets[]` element must contain exactly:

```json
{
  "id": "L-01",
  "contentType": "MAINLINE",
  "title": "脱敏标题",
  "achievementStatus": "DELIVERED",
  "contributionType": "PRIMARY",
  "publicPriority": "P0",
  "evidenceStatus": "PARTIALLY_VERIFIED",
  "reviewState": "PUBLIC_REVIEW_REQUIRED",
  "summary": "不包含内部标识的最小说明"
}
```

Allowed values:

```text
contentType: MAINLINE | TASK | INCIDENT | KNOWLEDGE_ASSET
achievementStatus: DELIVERED | IMPLEMENTED_TESTED | VALIDATED_PROTOTYPE |
                   INVESTIGATED | DOCUMENTED_OUTPUT | OBSERVED_LEARNING |
                   LEARNING_ONLY | INCOMPLETE
contributionType: PRIMARY | COLLABORATIVE | ASSISTED |
                  OBSERVED_LEARNING | UNRESOLVED
publicPriority: P0 | P1 | P2 | EXCLUDE
evidenceStatus: VERIFIED | PARTIALLY_VERIFIED | OWNER_CONFIRMED | INSUFFICIENT
reviewState: PUBLIC_REVIEW_REQUIRED | HOLD | EXCLUDE
```

Mapping rules:

- `IMPLEMENTED_NOT_RELEASED` becomes `IMPLEMENTED_TESTED` and the summary must state “未确认发布”。
- `INVESTIGATION_ONLY` becomes `INVESTIGATED`。
- `DELIVERED_OPERATION` becomes `DELIVERED`。
- `PRIVATE_KB_COMPLETE` becomes `DOCUMENTED_OUTPUT` with `reviewState = HOLD`。
- `PUBLIC_EVIDENCE` becomes `DOCUMENTED_OUTPUT`; time-sensitive numbers stay out of the summary。
- uncertain contribution becomes `UNRESOLVED` and `reviewState = HOLD`。

- [x] **Step 3: Validate identifiers, counts and enums**

Run a Node.js validation script that:

```javascript
const expected = {
  L: Array.from({ length: 7 }, (_, i) => `L-${String(i + 1).padStart(2, "0")}`),
  T: Array.from({ length: 19 }, (_, i) => `T-${String(i + 1).padStart(2, "0")}`),
  A: Array.from({ length: 25 }, (_, i) => `A-${String(i + 1).padStart(2, "0")}`),
  K: Array.from({ length: 17 }, (_, i) => `K-${String(i + 1).padStart(2, "0")}`),
};
```

Expected report:

```json
{
  "status": "PASS",
  "total": 68,
  "duplicates": [],
  "missing": [],
  "unknown": [],
  "invalidEnums": []
}
```

### Task 3: Record First-Batch Human Decisions

**Files:**
- Create privately: `$PORTFOLIO_GOVERNANCE_HOME/reviews/2026-07-23-first-batch-decisions.md`

**Interfaces:**
- Consumes: facts already confirmed by the owner in this task history.
- Produces: explicit non-Bundle decisions for `L-01`, `T-05`, `T-06`, and `K-01`.

- [x] **Step 1: Record the four candidate decisions**

Use:

```markdown
## L-01
- candidateType: PROJECT_EXPANSION
- achievementStatus: DELIVERED
- contributionType: PRIMARY
- publicationDecision: PROCEED_TO_PUBLIC_COPY_REVIEW
- limitation: Failed-source retry has no direct screenshot.

## T-06
- candidateType: CASE
- achievementStatus: DELIVERED
- contributionType: PRIMARY
- publicationDecision: PROCEED_TO_PUBLIC_COPY_REVIEW
- limitation: Internal module, channel, region and storage identifiers must be removed.

## T-05
- candidateType: CASE
- achievementStatus: DELIVERED
- contributionType: PRIMARY
- publicationDecision: PROCEED_TO_PUBLIC_COPY_REVIEW
- limitation: Usage frequency is owner-confirmed; no fabricated usage count is allowed.

## K-01
- candidateType: CASE
- achievementStatus: VALIDATED_PROTOTYPE
- contributionType: PRIMARY
- publicationDecision: PROCEED_TO_PUBLIC_COPY_REVIEW
- limitation: Exact benchmark numbers remain HOLD until a separate public-number review.
```

- [x] **Step 2: Verify decision completeness**

Expected: four unique headings, four contribution types, four limitations, and no Bundle `APPROVED` state.

### Task 4: Prepare the SQL Audit Expansion Candidate

**Files:**
- Read: `backend/src/main/resources/public-data/bundle/portfolio.json`
- Create privately: `$PORTFOLIO_GOVERNANCE_HOME/candidates/sql-audit-2026-07-expansion.json`
- Create privately: `$PORTFOLIO_GOVERNANCE_HOME/reviews/sql-audit-2026-07-expansion.md`

**Interfaces:**
- Consumes: the current public `sql-audit-project`.
- Produces: four additive Claims, one Evidence candidate, four DIRECT links, one TimelineEvent, and two QuestionPresets.

- [x] **Step 1: Add four privacy-safe Claim candidates**

Use fixed meanings:

```text
1. Negative-leading input is treated as fixed search text, not as a command option.
2. One approved server target can query multiple approved log sources.
3. Completed source results remain available when another source fails.
4. Connection checks are restricted to targets selected for the current task.
```

All Claims use `IMPLEMENTED_TESTED / PRIMARY`. The partial-success Claim limitation must state that retry behavior lacks a direct screenshot.

- [x] **Step 2: Add one Evidence collection and four DIRECT links**

Evidence ID: `sql-audit-july-iteration-set`.

The accepted source count must be copied from the private evidence decision, never inferred from the number of daily notes. Each DIRECT link scope names one supported behavior and explicitly excludes production scale, latency, adoption and long-term impact.

- [x] **Step 3: Add timeline and question candidates**

Timeline meaning: July iteration strengthened input safety and multi-source fault isolation.

Questions:

```text
为什么负号开头的关键词会导致查询异常，最终如何避免把输入当作命令选项？
多来源查询如何保留成功结果，并把失败影响限制在单个来源？
```

- [x] **Step 4: Write the private human-readable diff**

The review document must show existing public facts, additive candidate facts, limitations and privacy findings without copying raw private material.

### Task 5: Prepare Three Case Candidate Packets

**Files:**
- Create privately: `$PORTFOLIO_GOVERNANCE_HOME/candidates/case-multilingual-upload.json`
- Create privately: `$PORTFOLIO_GOVERNANCE_HOME/candidates/case-role-reset.json`
- Create privately: `$PORTFOLIO_GOVERNANCE_HOME/candidates/case-codegraph-evaluation.json`
- Create privately: matching Markdown files under `$PORTFOLIO_GOVERNANCE_HOME/reviews/`

**Interfaces:**
- Consumes: `T-06`, `T-05`, and `K-01`.
- Produces: three `AWAITING_CASESTUDY_CONTRACT` packets.

- [x] **Step 1: Write the multilingual upload case**

Required semantics:

```text
Problem: a later language upload replaced languages already visible.
Decision: merge persisted language mappings with languages actually uploaded now.
Verification: two sequential uploads in different languages remained queryable together.
Limitation: internal module, table, channel, region and storage identifiers are excluded.
```

- [x] **Step 2: Write the role reset case**

Required acceptance flow:

```text
1. Select an approved environment.
2. Query using the supplied abstract identifier.
3. Confirm the destructive reset.
4. Verify the old role is unavailable and a new role can be created after login.
```

Do not include deletion SQL, table names, example identifiers or internal environments.

- [x] **Step 3: Write the CodeGraph evaluation case**

Required semantics:

```text
Method: symbol lookup benchmark plus two MCP task suites.
Positive finding: exact and full-text symbol lookup reduced lookup time in selected samples.
Negative finding: method references, Lambda, dependency injection, event dispatch and duplicate names caused missed or noisy relations.
Decision: use CodeGraph for narrowing and batch navigation; use text search and source reading for precision verification.
Limitation: exact percentages and universal productivity claims remain excluded.
```

- [x] **Step 4: Validate all three packet shapes**

Each packet must contain:

```text
id, slug, code, type, title, summary, problem, actions[], decisions[],
verification[], outcome, limitations[], achievementStatus,
contributionType, optionalProjectId, claimIds, evidenceIds,
timelineEventIds, questionPresetIds, contractState
```

Expected: `contractState = "AWAITING_CASESTUDY_CONTRACT"` and no current public Project insertion.

### Task 6: Run Private Privacy and Consistency Gates

**Files:**
- Create privately: `$PORTFOLIO_GOVERNANCE_HOME/reports/privacy-scan.json`
- Create privately: `$PORTFOLIO_GOVERNANCE_HOME/reports/candidate-consistency.json`

**Interfaces:**
- Consumes: all private JSON and Markdown candidate/review files.
- Produces: bounded reports that never echo matching private values.

- [x] **Step 1: Scan prohibited patterns**

Scan for:

```text
IPv4, http/https URL, Windows absolute path, email,
token, password, secret, cookie, authorization,
internal host/port markers, raw SQL verbs and known internal identifiers.
```

The report contains only:

```json
{
  "status": "PASS",
  "rules": {
    "IPV4": 0,
    "URL": 0,
    "WINDOWS_PATH": 0,
    "EMAIL": 0,
    "CREDENTIAL_TERM": 0,
    "RAW_SQL": 0,
    "INTERNAL_IDENTIFIER": 0
  }
}
```

- [x] **Step 2: Check cross-file consistency**

Verify:

- first-batch IDs exist in the 68-item inventory;
- titles, statuses and contribution types match across decision and candidate files;
- every Case has at least one limitation;
- SQL Claims have DIRECT Evidence links;
- no candidate uses `APPROVED`, `PUBLISHED` or `RELEASED` as a governance state.

Expected: `status = "PASS"` and empty mismatch arrays.

### Task 7: Update the Public Project Documentation

**Files:**
- Create: `docs/09-portfolio-asset-library-status.md`
- Modify: `docs/00-文档状态索引.md`
- Modify: `docs/08-current-implementation-status.md`

**Interfaces:**
- Consumes: private validation summaries only.
- Produces: a public, privacy-safe handoff with no private path or source filename.

- [x] **Step 1: Write the public asset status document**

Required sections:

```text
1. Current scope: 7 mainlines, 19 tasks, 25 incidents, 17 knowledge assets.
2. First batch: SQL expansion, multilingual upload, role reset, CodeGraph evaluation.
3. Current state: private candidates prepared; none approved or published.
4. Runtime boundary: schema 2.0 still exposes one SQL project and one executable preset.
5. Next contract: CaseStudy model/API/frontend work requires separate approval.
6. Privacy boundary: no raw private evidence enters the repository.
```

- [x] **Step 2: Update the document status index**

Add the ingestion design, this plan and `09-portfolio-asset-library-status.md`. Mark the plan `已执行` only after every private and public check passes.

- [x] **Step 3: Update current implementation status**

Add a content-assets subsection that distinguishes:

```text
Private candidates prepared ≠ public bundle expanded.
Public runtime remains unchanged.
CaseStudy contract remains unimplemented.
```

### Task 8: Verify Scope, Privacy and Git Isolation

**Files:**
- Verify: all files from Tasks 1–7.

**Interfaces:**
- Consumes: final working tree and private reports.
- Produces: an evidence-backed completion result.

- [x] **Step 1: Re-run governance inspect**

Expected: `status = "PASS"`.

- [x] **Step 2: Verify private reports**

Expected:

```text
asset-library-structure: PASS, total 68
privacy-scan: PASS, every count 0
candidate-consistency: PASS, no mismatches
```

- [x] **Step 3: Verify the public runtime is unchanged**

Run:

```powershell
git diff --exit-code -- backend/src/main/resources/public-data
git diff --exit-code -- backend/src/main/java frontend/src
```

Because the repository already contains user-owned frontend modifications, compare the final frontend diff against the captured pre-task status instead of expecting a clean directory. Expected: no new content-task changes under runtime source paths.

- [x] **Step 4: Verify public documentation privacy**

Scan the three public documentation files for IPv4, URL, Windows absolute path, email, credential terms, raw SQL and private source filenames.

Expected: zero prohibited matches.

- [x] **Step 5: Review Git scope**

Expected content-task changes:

```text
docs/superpowers/specs/2026-07-23-portfolio-asset-library-ingestion-design.md
docs/superpowers/plans/2026-07-23-portfolio-asset-library-ingestion.md
docs/09-portfolio-asset-library-status.md
docs/00-文档状态索引.md
docs/08-current-implementation-status.md
```

Existing frontend and design-exploration changes remain unstaged and unmodified by this plan.

## Self-Review Results

- Spec coverage: full 68-item inventory, first-batch decisions, four candidate packets, privacy scanning, public handoff and runtime boundary are covered.
- Placeholder scan: 未发现占位标记、泛化测试指令或缺失的实施内容。
- Type consistency: inventory enums, first-batch IDs, Case packet fields and SQL Claim/Evidence relationships are consistent across tasks.
- Scope check: public runtime, CaseStudy implementation, publication, deployment, commit and push remain outside this plan.
