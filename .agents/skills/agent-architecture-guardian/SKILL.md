---
name: agent-architecture-guardian
description: Govern architecture changes in this Portfolio Agent repository. Use when classifying an Agent change, changing Command/Goal/Plan/SemanticResult/PublicAgentTurn/API/state/frontend contracts, executing or resuming a Replacement Slice, recording a waived gate, or deciding whether the Agent architecture is complete.
metadata:
  project-only: portfolio-agent
---

# Agent Architecture Guardian

This skill applies only to this repository. It guides work; it does not reopen an approved architecture or silently expand scope.

Before acting, read completely:

1. `docs/16-Agent单权威持续收敛范式.md`;
2. `docs/agent-architecture-status.json`;
3. the current authoritative design or implementation plan relevant to the request;
4. `docs/15-Agent 2.0真实交互问题清单与修复边界.md` when the request concerns Agent 2.0 behavior.

Run `scripts/agent-architecture-status.ps1` before architecture work and before claiming completion.

## Classify

Classify the requested change before implementation:

- Level 1: no authority, public-contract, persistence, entry-point or capability-boundary change;
- Level 2: one deep module changes internally while its external contract remains stable;
- Level 3: any Command, Goal, Plan, SemanticResult, PublicAgentTurn, API, state, shared frontend contract, production authority or migration change.

Use the smallest level that honestly covers the change. Do not start a redesign for Level 1 or Level 2 work.

## Protect hard invariants

Never waive:

- single production authority per concept;
- no runtime compatibility bridge or old-chain fallback;
- version-level rollback only;
- one shared public-contract source;
- evidence before completion;
- repository privacy boundaries.

If a request conflicts with a hard invariant, report the exact conflict and stop only the conflicting expansion. Do not preserve the old implementation as a precautionary runtime bridge.

## Execute Level 3 as Replacement Slices

For each slice: create the target authority, wire the unique production entry, migrate consumers, prove replacement safeguards, delete the retired authority/config/tests, run zero-reference and affected full gates, update status, then create a small Chinese commit.

An implementation that is not in the unique production path is not complete. A slice that leaves its retired authority callable is not complete.

## Defer soft gates without losing them

Environment, authorization or parallel-ownership gates may be `WAIVED`. Copy the structure from `docs/templates/agent-architecture-deferred-item.json` into `deferredItems` and fill every field. `WAIVED` is not `PASS`.

On every later architecture turn, reevaluate `resumeWhen`. If resumption is now independent and in scope, close it before or alongside current work. If repayment would materially expand the user's current request, report it and preserve the ledger. Never inherit an old environment failure without a fresh check.

## Completion

Overall `COMPLETE` requires all hard invariants `PASS` and no unresolved deferred items. Tests at one layer do not substitute for higher-risk gates. Only report Testcontainers, packaged-JAR Browser E2E or a real Provider as passed when that exact gate ran successfully.

Keep secrets, visitor text, prompts, raw model output, tokens, handles and private paths out of the status ledger and reports.
