# Project Agent Instructions

> **Documentation status (2026-07-22):** Current repository authority. See `docs/00-文档状态索引.md` for the status of every design and plan, and `docs/08-当前实现状态.md` for the feature inventory.

## Product boundary

This repository builds a public internship portfolio Agent. Runtime code may read only the reviewed public snapshot under `backend/src/main/resources/public-data/`. It must never read the private Obsidian knowledge base, candidate snapshots, raw daily reports, credentials, or unreviewed screenshots.

The current public content still contains one SQL audit project and one executable preset. The runtime now also contains the implemented A/B/C1/C2 capabilities and the C3 built-in Model Provider Registry documented in `docs/08-当前实现状态.md`; optional model expression and local retrieval remain disabled by default. These runtime additions do not expand the reviewed public factual scope.

Do not add Spring AI runtime calls, SSE, authentication, dynamic external publication, private search, or further C3 abstractions unless the authoritative design is updated and approved. P3's explicitly approved PostgreSQL Context Store exception permits only encrypted, short-lived, typed business Context and minimal request receipts; it must not persist questions, answers, Evidence text, credentials, private data, or long-term memory. The existing fixed DeepSeek/GLM expression adapters and local BGE embedding path are admitted only under their documented fail-closed configuration and privacy boundaries.

## Source of truth

Read these before changing behavior:

1. `docs/00-文档状态索引.md`
2. `docs/04-项目代码约束.md`
3. `docs/superpowers/specs/2026-07-14-internship-portfolio-v0-design.md`
4. `docs/superpowers/specs/2026-07-16-modular-monolith-package-design.md`
5. `docs/superpowers/specs/2026-07-16-portfolio-frontend-full-rebuild-design.md`
6. `docs/superpowers/specs/2026-07-17-public-content-api-integration-design.md`
7. `docs/superpowers/specs/2026-07-22-portfolio-agent-light-workspace-palette-design.md`
8. `docs/01-项目背景.md`, `docs/02-需求探索文档.md`, and `docs/03-可能技术选型.md` for the longer-term roadmap

Do not treat a historical or superseded plan as active work. Dynamic publication, Claim/RAG/model work, and its release contract remain pending until explicitly approved.

`Project.status` and `contributionType` are authoritative. Never expand a plan, prototype, observation, or collaborative task into an independently delivered result.

## Workflow

### Default Agent architecture guardian bootstrap

- At the start of every task in this repository, use `agent-architecture-guardian` for a lightweight classification before substantive action.
- `NOT_APPLICABLE` tasks continue immediately without loading architecture documents or running the architecture status checker.
- `LEVEL_1` and `LEVEL_2` tasks continue without waiting for repeated architecture approval; an already approved `LEVEL_3` continues through its Replacement Slices.
- Load the full architecture workflow only for applicable or uncertain Agent boundaries. Pause only before an unauthorized production-authority mutation or a privacy violation; continue diagnosis, safe experiments, and other in-scope work.

- Use Superpowers discovery and design gates for new behavior.
- Use test-driven development for every feature and bug fix: RED, GREEN, REFACTOR.
- Use systematic debugging before proposing a fix for unexpected behavior.
- Production and test Java must not use `var`, declare `record` types, or use Lombok.
- Use explicit immutable classes for value objects.
- Run fresh verification before claiming completion.
- Preserve user-owned Git changes. Do not reset, restore, stage, commit, or push without explicit authorization.
- All future Git commit messages must be written in Chinese. When using Conventional Commits, the `type`/`scope` prefix may retain its conventional English identifier, but the subject and body after the prefix must be in Chinese.
- Prefix shell commands with `rtk` when it is installed. If unavailable, use the documented raw-command debugging exception.

## Documentation maintenance

- Complete each independent feature, important behavior fix, product-boundary change, or technology-selection change by updating `docs/11-项目演进日志.md` before ending the task.
- Record what changed, how it relates to the previous direction, and its current state. Do not record implementation steps, test procedures, or commit metadata.
- Pure formatting changes, test-only additions, and behavior-preserving mechanical refactors do not need a separate evolution-log entry.
- When a capability, default switch, or product boundary changes, also update `docs/08-当前实现状态.md`.
- When public assets, governance waves, or publication status change, also update `docs/09-作品集资产库状态.md`.
- Changes to release bundles or content publication must read and follow `docs/05-公开发布包契约.md` and `docs/06-公开内容发布运行手册.md`.

## Technology

- Java 21, Spring Boot, Maven
- Production and test Java must use explicit types; `var`, `record`, and Lombok are prohibited.
- Value objects use explicit immutable classes.
- Vue 3, TypeScript, Vite
- Vitest and Vue Test Utils
- Playwright for browser acceptance
- One executable JAR and one Docker image for production delivery

## Security

- Public APIs are read-only and must return DTOs, not private source objects.
- Do not log visitor questions or persist them on the server or in browser storage. P3 may persist only the approved encrypted typed business Context and minimal request receipt; visitor questions and answers remain non-persistent. Questions and answers must not enter URLs or browser history. Homepage-to-Agent handoff uses a random, memory-only, one-time ID with a short expiry.
- Do not expose stack traces, local paths, internal hosts, IP addresses, credentials, or raw evidence.
- Only Evidence with `publicStatus = APPROVED` may be returned.
- Run `scripts/privacy-check.ps1` before packaging.

## Verification commands

Backend:

```powershell
mvn.cmd -f backend/pom.xml test
```

Frontend:

```powershell
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
```

Package:

```powershell
mvn.cmd -f backend/pom.xml package
```
