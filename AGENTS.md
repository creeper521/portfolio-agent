# Project Agent Instructions

> **Documentation status (2026-07-22):** Current repository authority. See `docs/00-文档状态索引.md` for the status of every design and plan, and `docs/08-current-implementation-status.md` for the feature inventory.

## Product boundary

This repository builds a public internship portfolio Agent. Runtime code may read only the reviewed public snapshot under `backend/src/main/resources/public-data/`. It must never read the private Obsidian knowledge base, candidate snapshots, raw daily reports, credentials, or unreviewed screenshots.

The current public content still contains one SQL audit project and one executable preset. The runtime now also contains the implemented A/B/C1/C2 capabilities and the C3 built-in Model Provider Registry documented in `docs/08-current-implementation-status.md`; optional model expression and local retrieval remain disabled by default. These runtime additions do not expand the reviewed public factual scope.

Do not add Spring AI runtime calls, SSE, a database, authentication, dynamic external publication, private search, or further C3 abstractions unless the authoritative design is updated and approved. The existing fixed DeepSeek/GLM expression adapters and local BGE embedding path are admitted only under their documented fail-closed configuration and privacy boundaries.

## Source of truth

Read these before changing behavior:

1. `docs/00-文档状态索引.md`
2. `docs/04-项目代码约束.md`
3. `docs/superpowers/specs/2026-07-14-internship-portfolio-v0-design.md`
4. `docs/superpowers/specs/2026-07-16-modular-monolith-package-design.md`
5. `docs/superpowers/specs/2026-07-16-portfolio-frontend-full-rebuild-design.md`
6. `docs/superpowers/specs/2026-07-17-public-content-api-integration-design.md`
7. `docs/01-项目背景.md`, `docs/02-需求探索文档.md`, and `docs/03-可能技术选型.md` for the longer-term roadmap

Do not treat a historical or superseded plan as active work. Dynamic publication, Claim/RAG/model work, and its release contract remain pending until explicitly approved.

`Project.status` and `contributionType` are authoritative. Never expand a plan, prototype, observation, or collaborative task into an independently delivered result.

## Workflow

- Use Superpowers discovery and design gates for new behavior.
- Use test-driven development for every feature and bug fix: RED, GREEN, REFACTOR.
- Use systematic debugging before proposing a fix for unexpected behavior.
- Production and test Java must not use `var`, declare `record` types, or use Lombok.
- Use explicit immutable classes for value objects.
- Run fresh verification before claiming completion.
- Preserve user-owned Git changes. Do not reset, restore, stage, commit, or push without explicit authorization.
- Prefix shell commands with `rtk` when it is installed. If unavailable, use the documented raw-command debugging exception.

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
- Do not log visitor questions or persist them on the server or in browser storage. Visitor conversations are tab-memory only and disappear on refresh or close; the page must state this clearly without intercepting refresh. Questions and answers must not enter URLs or browser history. Homepage-to-Agent handoff uses a random, memory-only, one-time ID with a short expiry.
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
