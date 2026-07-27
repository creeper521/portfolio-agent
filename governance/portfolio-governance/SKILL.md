---
name: portfolio-governance
description: Validate, benchmark, review, approve, publish, verify, list, and roll back audited Portfolio Agent public-content bundles. Use only when a user explicitly requests portfolio content governance or publication work.
metadata:
  disable-model-invocation: true
---

# Portfolio Governance

Operate only through `scripts/portfolio-governance.ps1`; never edit private governance state directly.

Require `PORTFOLIO_GOVERNANCE_HOME` or `-Workspace`. Resolve it outside the Git worktree and reject traversal, symlinks, and junctions. Never print its absolute path.

Run stages in order: inspect, validate, benchmark, build-review-pack, explicit human approve, explicit publish, verify. Publishing and rollback default to dry-run and require the command's confirmation switch. Never auto-approve or bypass BLOCKER/ERROR findings.

Legacy B Approval binds the exact canonical `portfolio.json` and `presentation.json` bytes. A C2 retrieval candidate must first be prepared explicitly with `scripts/build-retrieval-bundle.ps1`; its Approval binds the exact canonical `portfolio.json`, `presentation.json`, and `rag-documents.jsonl` bytes. Publishing must reproduce the approved RAG bytes exactly, copy all approved payload bytes without semantic normalization, and only then derive the keyword/vector indexes, Manifest, and checksums with the pinned local model.

Treat schema `2.0` as a legacy read with empty Case collections. Schema `3.0` must explicitly provide `cases`, every QuestionPreset and TimelineEvent `caseIds`, and release `counts.cases`. Validate every Case reference and privacy-scan every Case text value. Public URLs are denied except `https://blog.csdn.net/2301_81073317`; exact CodeGraph token or percentage claims are denied, while reviewed qualitative limitations are allowed.

Do not invoke a generative model, external Embedding provider, or upload private content. C2 index generation may use only the pinned local ONNX embedding artifact after hash verification. Never store visitor question/answer text, normalized queries, query vectors, scores, or candidates. Never auto-prepare a candidate, auto-approve, or publish a prebuilt index supplied by the candidate.
