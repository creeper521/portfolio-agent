# Project Agent Instructions

## Product boundary

This repository builds a public internship portfolio Agent. Runtime code may read only the reviewed public snapshot under `backend/src/main/resources/public-data/`. It must never read the private Obsidian knowledge base, candidate snapshots, raw daily reports, credentials, or unreviewed screenshots.

V0 contains one SQL audit project and one deterministic question. Do not add DeepSeek, Spring AI runtime calls, SSE, a database, embeddings, authentication, or private search unless the authoritative design is updated and approved.

## Source of truth

Read these before changing behavior:

1. `docs/01-项目背景.md`
2. `docs/02-需求探索文档.md`
3. `docs/03-可能技术选型.md`
4. `docs/04-项目代码约束.md`
5. `docs/superpowers/specs/2026-07-14-internship-portfolio-v0-design.md`
6. `docs/superpowers/specs/2026-07-14-codebase-architecture-refactor-design.md`
7. The active plan under `docs/superpowers/plans/`

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
- Do not log visitor questions or persist them in V0.
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

