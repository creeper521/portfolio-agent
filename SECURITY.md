# Security Policy

> **Status (2026-07-24):** A/B、默认关闭的 C1、C2 本地公开检索/固定只读工具，以及独立的对话式 Agent v2 后端已经实现。Tool Registry、Hook、Orchestrator、多 Agent、DurableTask 和持久会话仍未准入。

Project and Case answer contexts are explicit and mutually exclusive. Case retrieval
chunks, tool calls, and context envelopes carry stable `caseSlugs`; they never widen
implicitly into a related Project or accept mixed Project/Case claims.

## Public data boundary

The deployed application reads only the reviewed public snapshot packaged with the backend. The private knowledge base, raw daily reports, candidate snapshots, internal screenshots, credentials, and privacy reports must never be included in runtime artifacts.

## Reporting a problem

Do not open a public issue containing private internship information. Report suspected data exposure directly to the repository owner through a private channel.

## Required checks

- Evidence must be marked `APPROVED` before it can be returned.
- Raw evidence is not publicly accessible in V0.
- Visitor questions must not be stored or logged by the server.
- v1 外部模型只能接收公开 `AnswerPlan` 白名单载荷，仍不得接收访客原话或历史。
- v2 只有在 `PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED=true`、公开数据审批、`PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED=true`、Registry 兼容且所选 Provider 密钥存在时，才允许把当前问题和经过预算裁剪的临时历史发送给该 Provider。`turnId`、请求元数据、Cookie、Header 和任何私有资料始终不得发送。
- Model expression stays disabled unless the selected project-scoped environment key (`PORTFOLIO_AGENT_DEEPSEEK_API_KEY` or `PORTFOLIO_AGENT_GLM_API_KEY`) exists and `PORTFOLIO_MODEL_DATA_POLICY_APPROVED=true`. Provider retention/privacy approval is an operator decision, not inferred by the application.
- Each process uses at most one selected Provider, one non-streaming attempt, an explicit timeout, no automatic retry, and no cross-Provider resend. Any failure or invalid draft discards the whole draft and uses the same public plan for deterministic fallback.
- v2 最多接受当前页面内 20 轮用户—Agent 历史；服务端无会话状态。超出输入预算时，仅临时摘要较早轮次并保留最近 6 轮原文，摘要和正文都不得记录或持久化。
- v2 每轮作品集事实都从当前已审核公开快照重新组装。公网运行时永不读取 Obsidian、候选审核包、原始日报或未发布 Evidence；RAG Chunk 文本必须来自通过 Release Loader 校验的公开 Bundle。
- v2 工具固定为只读白名单，最多 2 轮、4 次调用；没有 Web Search、动态工具发现或跨 Provider 重发。
- 回滚 v2 只需设置 `PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED=false` 并重启；v1 能力保持不变。
- The immutable Model Provider Registry snapshot is `c3-model-registry-v1` and contains only the two reviewed built-ins. It holds no credentials, offers no mutable registration/removal/replacement API, performs no dynamic classpath/file/network discovery, and must not log raw Provider requests or responses. Existing `PORTFOLIO_AGENT_DEEPSEEK_API_KEY` and `PORTFOLIO_AGENT_GLM_API_KEY` ownership remains in `ModelExpressionProperties`.
- Retrieval uses only the pinned local BGE INT8 ONNX artifact after exact descriptor/file hash verification. Visitor queries, normalized terms, query vectors, scores, candidates, and retrieval context must never leave the process or enter logs/storage.
- C2 Approval binds exact canonical `portfolio.json`, `presentation.json`, and `rag-documents.jsonl` bytes. Candidates cannot supply indexes; the publish tool must reproduce approved RAG bytes before deriving indexes locally.
- Visitor questions, answers, and sessions exist only in current-page memory and disappear on refresh or close. They must not enter URLs, browser history, localStorage, sessionStorage, IndexedDB, or service-side storage.
- Homepage-to-Agent transfer uses a random, short-lived, in-memory, one-time `handoffId` and never puts the question or answer in the URL.
- Public content and packaged artifacts must pass `scripts/privacy-check.ps1`.
- Production source/config must also pass `scripts/privacy-check.ps1 -Path backend/src/main` before packaging.
- Release candidates should use `scripts/verify-release.ps1`, not bare `mvn package`, when claiming full verification.
- API errors must not include stack traces, local paths, internal hosts, or credentials.
