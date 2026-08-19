---
name: agent-architecture-guardian
description: Use when changing or extending this Portfolio Agent's Command, Goal, Plan, SemanticResult, PublicAgentTurn, API, state, frontend contract, production authority, or when repeated workarounds make the current architecture difficult to extend.
---

# Agent Architecture Guardian

Apply only in this repository. Protect constraints, not incumbent implementations. Do not treat an approved architecture as immutable.

Before acting, read completely:

1. `docs/16-Agent单权威持续收敛范式.md`;
2. `docs/agent-architecture-status.json`;
3. the current authoritative design or implementation plan relevant to the request;
4. `docs/15-Agent 2.0真实交互问题清单与修复边界.md` when the request concerns Agent 2.0 behavior.

Run `scripts/agent-architecture-status.ps1` before architecture work and before completion claims.

## Classify

Classify the requested change before implementation:

- Level 1: no authority, public-contract, persistence, entry-point or capability-boundary change;
- Level 2: one deep module changes internally while its external contract remains stable;
- Level 3: any Command, Goal, Plan, SemanticResult, PublicAgentTurn, API, state, shared frontend contract, production authority or migration change.

Use the smallest level that honestly covers the change. Do not start a redesign for Level 1 or Level 2 work.

## Architecture Review

Enter `ARCHITECTURE_REVIEW` when concrete evidence shows the current design is the constraint: repeated workarounds, recurring waivers, cross-layer branches, duplicated translations, leaking internals, or unmet reliability, privacy, performance, or extension requirements.

Continue diagnosis, option analysis, and an isolated prototype that is not registered or callable as a production authority. Do not mutate the production authority before approval.

Produce a review containing: current limitation, code-path evidence, patching cost, candidate authority, affected consumers, migration/deletion scope, exit gates, and version-level rollback. After user approval, record the target architecture and execute it as Level 3. Without approval, preserve findings and continue other in-scope work.

## Protect Steady-State Constraints

Require:

- one production authority per concept after replacement;
- no permanent runtime compatibility bridge or old-chain fallback;
- version-level rollback only;
- one shared public-contract source;
- evidence before completion;
- repository privacy boundaries.

These constraints govern the migration outcome; they do not freeze the current authority. Pause only an unauthorized production mutation or a privacy violation. Continue architecture review and safe isolated experiments.

## Execute Level 3

For each slice: create the target authority, wire the unique production entry, migrate consumers, prove replacement safeguards, delete the retired authority/config/tests, run zero-reference and affected full gates, update status, then create a small Chinese commit.

An implementation that is not in the unique production path is not complete. A slice that leaves its retired authority callable is not complete.

## Preserve Deferred Work

Environment, authorization or parallel-ownership gates may be `WAIVED`. Copy the structure from `docs/templates/agent-architecture-deferred-item.json` into `deferredItems` and fill every field. `WAIVED` is not `PASS`.

On every later architecture turn, reevaluate `resumeWhen`. If resumption is now independent and in scope, close it before or alongside current work. If repayment would materially expand the user's current request, report it and preserve the ledger. Never inherit an old environment failure without a fresh check.

## Complete Honestly

Overall `COMPLETE` requires all hard invariants `PASS` and no unresolved deferred items. Tests at one layer do not substitute for higher-risk gates. Only report Testcontainers, packaged-JAR Browser E2E or a real Provider as passed when that exact gate ran successfully.

Keep secrets, visitor text, prompts, raw model output, tokens, handles and private paths out of the status ledger and reports.
