# Project Agent Instructions
<!-- DOCUMENT_STATUS: CURRENT_AUTHORITY -->

> **Status:** Current repository authority. Current behavior and open limitations are indexed by `docs/00-文档状态索引.md` and `docs/08-当前实现状态.md`.

## Product boundary

This repository builds a public internship portfolio Agent. Runtime code may read only the reviewed public snapshot under `backend/src/main/resources/public-data/` or its controlled public-database projection. It must never read the private Obsidian knowledge base, candidate review packages, raw daily reports, credentials, or unreviewed screenshots.

Exact runtime release versions and counts are owned only by the packaged manifest and the canonical checked snapshot block in `docs/08-当前实现状态.md`. Do not duplicate those facts in this file or other maintained documents.

Agent 2.0 is the only runtime authority: Command → Goal → Plan → Execution → PublicAgentTurn → Settlement. The four unversioned `/api/agent` resources are the only public Agent HTTP surface. The old `answer` package may contain transitional dependencies while the approved Replacement Slices execute, but it is not a second runtime authority and must not receive new behavior.

Standard local development and production use PostgreSQL Agent State. `IN_MEMORY` is limited to fast tests and targeted diagnosis; `DISABLED` is explicit read-only portfolio mode. The State boundary permits only encrypted, short-lived typed context, challenge state, request receipts, and final public replay. It must not persist visitor questions, ConversationWindow, Prompt, raw model output, internal diagnostics, private data, or raw Evidence.

Optional model operations and local public retrieval remain disabled unless explicitly configured. The fixed DeepSeek/GLM adapters and local BGE path are admitted only under their fail-closed privacy and configuration gates. Public PostgreSQL projection and private governance import remain separate, explicitly operated capabilities.

Do not add Spring AI runtime calls, SSE, authentication, dynamic external publication, private search, open-ended ReAct, multi-Agent orchestration, durable tasks, long-term chat storage, or further provider abstractions unless an authoritative design is approved.

`Project.status` and `contributionType` remain authoritative. Never expand a plan, prototype, observation, or collaborative task into an independently delivered result.

## Source of truth

Read these before changing current behavior:

1. The user's latest explicit decision and this file.
2. The approved active design `docs/superpowers/specs/2026-08-19-agent-stabilization-and-repository-governance-design.md` and active plan `docs/superpowers/plans/2026-08-19-agent-stabilization-and-repository-governance.md`.
3. Production code, configuration, automated tests, and fresh reproducible evidence.
4. Maintained current documents, whose complete authoritative set is owned by the map in `docs/00-文档状态索引.md`; do not copy that list here.
5. Historical designs, plans, reports, handoffs, docs/01-03, docs/07, and docs/11-14. They provide context only and do not prove current behavior.

## Workflow

### Agent architecture guardian bootstrap

- At the start of every task, classify whether it affects Agent behavior, contracts, state, API, or production authority.
- `NOT_APPLICABLE`, `LEVEL_1`, and `LEVEL_2` work continues without repeated approval.
- The approved `LEVEL_3` stabilization and convergence plan continues through its Replacement Slices.
- Pause only before an unauthorized production-authority change, privacy violation, destructive action, or external operation requiring new authority.
- If a Guardian rule conflicts with newer code, passing tests, or an approved design, treat the conflicting rule as advisory, preserve privacy boundaries, and record one `GUARDIAN_DRIFT` item in the existing architecture status ledger.

### Engineering discipline

- Use test-driven development for behavior changes and bug fixes.
- Diagnose unexpected behavior before proposing a fix.
- Production and test Java must not use `var` or Lombok.
- `record` is allowed only for pure immutable data carriers. Objects with non-trivial invariants, lifecycle, behavior, or expected evolution use explicit immutable classes.
- Run fresh verification before claiming completion.
- Preserve user-owned changes. Do not reset, restore, stage, commit, or push without explicit authorization.
- Commit subjects and bodies must be Chinese; a conventional English `type(scope):` prefix is allowed.
- Prefix shell commands with `rtk` when installed; otherwise use the documented raw-command exception.

## Documentation maintenance

- Update `docs/11-项目演进日志.md` after an independent feature, important behavior fix, product-boundary change, or technology decision is complete.
- The log records what changed, its relation to the previous direction, current boundary, and links. It does not contain implementation steps, test procedures, test counts, hashes, or commit metadata.
- Update `docs/08-当前实现状态.md` when a capability, default, limitation, or deployment state changes.
- Update `docs/09-作品集资产库状态.md` when public assets or publication state change.
- Content changes must follow `docs/05-公开发布包契约.md` and `docs/06-公开内容发布运行手册.md`.
- Current documents use `CURRENT_AUTHORITY`; approved in-flight design and plan use their active markers; historical material must identify itself formally.

### Agent 2.0 dynamic bug ledger

- `docs/15-Agent 2.0真实交互问题清单与修复边界.md` is the single ledger for open Agent 2.0 bugs.
- Add a reproducible production-path, API, packaged-JAR, browser, database, or real-Provider bug as soon as its evidence is understood.
- Keep facts separate from hypotheses; update an entry when new evidence changes cause, severity, scope, or required Exit Gate.
- Remove a bug only after the production fix, targeted regressions, affected suites/builds, risk-appropriate integration gates, and the original user-visible path all pass.
- When removing a bug, remove its overview row, detailed section, dedicated test-gap text, and dedicated Exit Gate. Record important completed behavior in docs/11; do not create an archive inside docs/15.
- Bug IDs increase monotonically and are never reused.

## Technology

- Java 21, Spring Boot, Maven
- Vue 3, TypeScript, Vite
- JUnit, Vitest, Vue Test Utils, Playwright
- PostgreSQL 16/pgvector and Flyway
- One executable JAR and one Docker image for production delivery

## Security

- Public browsing APIs are read-only. Agent mutation resources are limited to creating/cancelling a Turn and clearing the current anonymous conversation.
- Only `publicStatus = APPROVED` Evidence may be returned.
- Do not log or persist visitor questions.
- PostgreSQL may retain only the approved encrypted typed State and fixed 30-minute public replay; Clarification challenges use the approved shorter TTL.
- A browser may store one short-lived ResumeToken in current-tab `sessionStorage`. It must not persist questions, answers, history, Context, challenges, request history, or Evidence.
- Tokens and Handles must not enter URLs or browser history. Homepage-to-Agent handoff remains random, memory-only, one-time, and short-lived.
- Do not expose stack traces, paths, internal hosts, source addresses, credentials, or raw Evidence.
- Run privacy, documentation, quality, architecture, and release gates before packaging claims.

## Verification commands

```powershell
mvn.cmd -f backend/pom.xml test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run check
npm.cmd --prefix frontend run build
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/documentation-check.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path backend/src/main
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-release.ps1
```
