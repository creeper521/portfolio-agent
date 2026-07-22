# Project Agent Instructions

> **Documentation status (2026-07-20):** Current repository authority. See `docs/00-文档状态索引.md` for the status of every design and plan.

## Product boundary

This repository builds a public internship portfolio Agent. Runtime code may read only the reviewed public snapshot under `backend/src/main/resources/public-data/`. It must never read the private Obsidian knowledge base, candidate snapshots, raw daily reports, credentials, or unreviewed screenshots.

The current V0 contains one SQL audit project and one deterministic question, plus a six-route Vue portfolio shell, a public-content aggregate API, a responsive Agent workspace, and browser-local sessions. These UI additions do not expand the answer engine's factual scope.

Do not add DeepSeek, Spring AI runtime calls, SSE, a database, embeddings, authentication, dynamic external publication, or private search unless the authoritative design is updated and approved.

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
