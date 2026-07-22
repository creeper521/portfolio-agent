# Security Policy

> **Status (2026-07-22):** A/B, the default-disabled C1 model-expression boundary, C2a local public retrieval, C2 tools/cited multi-turn, and only the C3 Model Provider Registry are implemented. Tool Registry, Hook, Orchestrator, multi-Agent, DurableTask, and persistent sessions remain unadmitted in `docs/00-文档状态索引.md`.

## Public data boundary

The deployed application reads only the reviewed public snapshot packaged with the backend. The private knowledge base, raw daily reports, candidate snapshots, internal screenshots, credentials, and privacy reports must never be included in runtime artifacts.

## Reporting a problem

Do not open a public issue containing private internship information. Report suspected data exposure directly to the repository owner through a private channel.

## Required checks

- Evidence must be marked `APPROVED` before it can be returned.
- Raw evidence is not publicly accessible in V0.
- Visitor questions must not be stored or logged by the server.
- External model providers may receive only the dedicated public `AnswerPlan` whitelist payload. Visitor wording, aliases, history, `turnId`, `requestId`, `handoffId`, cookies, headers, private material, raw prompts, and raw provider responses must not be sent or logged.
- Model expression stays disabled unless the selected project-scoped environment key (`PORTFOLIO_AGENT_DEEPSEEK_API_KEY` or `PORTFOLIO_AGENT_GLM_API_KEY`) exists and `PORTFOLIO_MODEL_DATA_POLICY_APPROVED=true`. Provider retention/privacy approval is an operator decision, not inferred by the application.
- Each process uses at most one selected Provider, one non-streaming attempt, an explicit timeout, no automatic retry, and no cross-Provider resend. Any failure or invalid draft discards the whole draft and uses the same public plan for deterministic fallback.
- The immutable Model Provider Registry snapshot is `c3-model-registry-v1` and contains only the two reviewed built-ins. It holds no credentials, offers no mutable registration/removal/replacement API, performs no dynamic classpath/file/network discovery, and must not log raw Provider requests or responses. Existing `PORTFOLIO_AGENT_DEEPSEEK_API_KEY` and `PORTFOLIO_AGENT_GLM_API_KEY` ownership remains in `ModelExpressionProperties`.
- Retrieval uses only the pinned local BGE INT8 ONNX artifact after exact descriptor/file hash verification. Visitor queries, normalized terms, query vectors, scores, candidates, and retrieval context must never leave the process or enter logs/storage.
- C2 Approval binds exact canonical `portfolio.json`, `presentation.json`, and `rag-documents.jsonl` bytes. Candidates cannot supply indexes; the publish tool must reproduce approved RAG bytes before deriving indexes locally.
- Visitor questions, answers, and sessions exist only in current-page memory and disappear on refresh or close. They must not enter URLs, browser history, localStorage, sessionStorage, IndexedDB, or service-side storage.
- Homepage-to-Agent transfer uses a random, short-lived, in-memory, one-time `handoffId` and never puts the question or answer in the URL.
- Public content and packaged artifacts must pass `scripts/privacy-check.ps1`.
- Production source/config must also pass `scripts/privacy-check.ps1 -Path backend/src/main` before packaging.
- Release candidates should use `scripts/verify-release.ps1`, not bare `mvn package`, when claiming full verification.
- API errors must not include stack traces, local paths, internal hosts, or credentials.
