# Security Policy

> **Status (2026-07-22):** A/B security and governance baseline implemented. Remaining C scope is listed in `docs/00-文档状态索引.md`.

## Public data boundary

The deployed application reads only the reviewed public snapshot packaged with the backend. The private knowledge base, raw daily reports, candidate snapshots, internal screenshots, credentials, and privacy reports must never be included in runtime artifacts.

## Reporting a problem

Do not open a public issue containing private internship information. Report suspected data exposure directly to the repository owner through a private channel.

## Required checks

- Evidence must be marked `APPROVED` before it can be returned.
- Raw evidence is not publicly accessible in V0.
- Visitor questions must not be stored or logged by the server.
- Visitor questions, answers, and sessions exist only in current-page memory and disappear on refresh or close. They must not enter URLs, browser history, localStorage, sessionStorage, IndexedDB, or service-side storage.
- Homepage-to-Agent transfer uses a random, short-lived, in-memory, one-time `handoffId` and never puts the question or answer in the URL.
- Public content and packaged artifacts must pass `scripts/privacy-check.ps1`.
- Release candidates should use `scripts/verify-release.ps1`, not bare `mvn package`, when claiming full verification.
- API errors must not include stack traces, local paths, internal hosts, or credentials.
