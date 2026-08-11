# P2 Semantic Turn Routing Backend Closure Design

> Date: 2026-08-11  
> Status: implemented and backend-gated  
> Baseline: `c128002`  
> Scope: close the reviewed P2 backend gaps without implementing P3 tool orchestration

## 1. Decision

P2 keeps the existing one-turn `SemanticTurnPlan` and `stp-v1` authority, and this revision closes six production gaps:

1. clarification becomes a real stateless request/response loop;
2. plan adjustment becomes a bounded ASK bound to an original question and pending plan reference;
3. task summaries expose per-task public reason codes and safe dependency references;
4. P2 Portfolio fact execution reuses the P1 deterministic answer composer;
5. all seven declared task types become reachable through deterministic or optional closed-set classification;
6. expiry-only confirmation reissues the same validated plan instead of forcing regeneration.

The work does not add durable server-side conversations, editable task graphs, open ReAct loops, new model providers, private data, or P3 tools.

## 2. Authority and compatibility

This document amends the production-reachability details of:

- `2026-08-10-semantic-turn-routing-design.md`;
- `2026-08-06-agent-answer-composition-design.md`;
- `13-Agent对话体验与智能编排改造路线图.md`.

`stp-v1` remains the wire-contract identifier. New request fields are optional and new response fields are additive. Existing ASK, CONFIRM_PLAN, and REGENERATE_PLAN clients remain valid.

## 3. Request contract

### 3.1 ASK variants

An ASK is exactly one of:

```text
ordinary ASK
plan-adjustment ASK
clarification-resolution ASK
```

`planAdjustment` and `clarificationResolution` are mutually exclusive.

### 3.2 Plan adjustment

```text
planAdjustment
├── instruction: 1..500 characters
└── pendingPlanReference
    ├── planId
    └── planFingerprint
```

Rules:

- `action` must be ASK;
- `question` is the original user question, not only the incremental instruction;
- `semanticContext` is required;
- the request-level and semantic-context pending plan references must match when both are present;
- the frontend cannot submit tasks, dependencies, exclusions, confidence, or a decoded confirmation envelope;
- the instruction is redacted from logs and `toString()` output;
- high-precision deterministic additions/removals are applied locally;
- uncertain adjustments may use the optional classifier once, then still pass the compiler and validator;
- invalid or unsupported adjustments clarify or reject; they never silently execute the old plan.

### 3.3 Clarification resolution

```text
clarificationResolution
├── clarificationId
├── promptCode
├── fieldKey
├── selectedOption?
│   ├── value
│   └── subjectReference?
│       ├── subjectType
│       └── subjectId
└── textValue?
```

Rules:

- `action` must be ASK;
- exactly one of `selectedOption` and `textValue` is present;
- the promptCode/fieldKey/input shape is a closed matrix;
- subject references are revalidated against the current public catalog;
- `comparisonSubject` accepts a PROJECT subject reference;
- `subject` accepts a PROJECT or CASE subject reference;
- `taskSplit` accepts SHORT_TEXT and treats it as a revised complete question;
- the clarification id is opaque correlation data, not a trusted server session;
- invalid values fail closed and never become arbitrary active subjects.

## 4. Clarification response

The response includes the existing prompt, scope, fields and counts plus:

```text
clarificationId
continuingGoalLabels[]
blockedGoals[]
  ├── goalLabel
  └── reasonCode
```

Each SINGLE_CHOICE option includes a closed resolution:

```text
option
├── value
├── label
└── resolution
    ├── kind: SUBJECT_REFERENCE
    ├── subjectType
    └── subjectId
```

Option labels come from reviewed public subject metadata. Options are stable, distinct, bounded, and exclude already resolved comparison subjects.

SHORT_TEXT fields legitimately contain no options. The frontend must render by `inputMode`, not by option presence.

## 5. Task summary reasons

`TaskOutcome.reasonCodes` is already the authoritative public-safe source. The response mapper must preserve it per task:

```text
TaskSummaryItem
├── displayIndex
├── goalLabel
├── status
├── sourceDomain
├── reasonCodes[]
└── blockedByDisplayIndexes[]
```

Only uppercase public-safe codes created by closed backend outcome factories may leave the backend. Internal exception messages remain absent. `blockedByDisplayIndexes` contains stable display indexes, never task ids.

The frontend maps known codes to local copy and uses a safe generic fallback for unknown codes.

## 6. Display plan summary

`DisplayPlanResponse` gains optional `summaryLabel` generated deterministically from closed task types. It is length-bounded and contains no model prose or internal identifiers.

Examples:

```text
了解公开项目
从项目了解，到比较与推荐
比较公开项目并形成综合结论
```

## 7. P1/P2 answer-composition seam

For `ANSWERED + FACT_LOOKUP + one subject`, `PortfolioSemanticTaskExecutor` must call `DeterministicPortfolioAnswerComposer`.

The task payload carries typed section blocks:

```text
SectionBlock
├── sectionType
├── title
├── content
├── claimIds[]
└── evidenceIds[]
```

The response mapper preserves each section's own provenance. It must not apply the task-wide aggregate claim/evidence set to every block.

Legacy string block constructors remain only as compatibility adapters for General and existing tests; a Portfolio fact task must use typed sections in production.

Composition failure returns a safe non-renderable task outcome. It must not fall back to passage-per-block output.

## 8. Seven task types and classifier

### 8.1 Deterministic reachability

The signal collector and compiler must support:

- PORTFOLIO_FACT;
- PORTFOLIO_COMPARE;
- PORTFOLIO_RECOMMEND;
- PORTFOLIO_REFINE_RECOMMENDATION;
- GENERAL_EXPLANATION;
- GENERAL_COMPARISON;
- SYNTHESIS.

High-precision deterministic phrases remain the zero-model fast path.

### 8.2 Optional classifier

`DefaultTurnRouter` gains an optional `SemanticClassifierPort` dependency.

Rules:

- zero calls for boundary, CONFIRM_PLAN, deterministic-complete input, and trusted clarification resolutions;
- at most one call for an unresolved ASK or adjustment;
- candidates remain untrusted;
- public subjects are catalog-validated again;
- invalid candidates are not sent back to the model for repair;
- provider unavailable preserves deterministic goals and clarifies unknown dimensions;
- the adapter reuses the existing model provider registry and is disabled by default until eval gates pass.

No new provider is introduced and no external model call becomes default behavior.

## 9. Confirmation expiry

Verification order remains:

1. integrity;
2. schema;
3. decoded plan validation and fingerprint consistency;
4. content version;
5. subject references;
6. capability version;
7. expiry.

Version invalidation deliberately wins over expiry so an old envelope cannot retain a stale plan merely by also being expired.

If and only if expiry is the sole invalidation reason:

```text
open original envelope
→ validate the same plan without a model
→ keep planId and fingerprint
→ issue a new confirmationId, token and expiry
→ return CONFIRMATION_REQUIRED with the same validated display plan
```

Content, subject, schema, capability, or integrity changes continue to replan or reject according to the formal P2 design.

## 10. Runtime flow

```text
ASK
→ validate request variant
→ apply trusted clarification resolution or bounded adjustment context
→ boundary
→ canonical context resolution
→ deterministic signals
→ optional classifier when necessary
→ compile
→ validate
→ decision
→ confirm / clarify / coordinate
→ typed completed-task response
```

```text
CONFIRM_PLAN
→ cryptographic open and integrity validation
→ expiry/content/subject/capability validation
→ expiry-only: reissue
→ otherwise execute exact plan or invalidate
```

## 11. Privacy and logging

- raw questions, adjustment instructions and SHORT_TEXT clarification values are redacted from `toString()` and diagnostics;
- no server-side visitor question persistence is introduced;
- clarification options contain public subject metadata only;
- no model output, prompt, provider error, task id, path, stack trace, or token is returned;
- continuation state remains tab-memory responsibility on the frontend.

## 12. Tests

Required backend coverage:

1. DTO validation matrix for ordinary ASK, adjustment ASK and clarification ASK;
2. request mapping preserves plan fingerprint and trusted resolution shape;
3. comparison options are populated, stable and exclude current subjects;
4. SHORT_TEXT task split reroutes the revised question;
5. invalid prompt/field/value/type combinations fail closed;
6. task summary preserves reason codes and maps blocked dependencies to display indexes;
7. DisplayPlan summary is deterministic and contains no task id;
8. Portfolio fact output uses typed P1 sections with per-section provenance;
9. composer failure produces no raw-passage fallback body;
10. all seven task types have reachable routing tests;
11. classifier is called at most once and unavailable/invalid output falls back safely;
12. expiry-only confirmation reissues the same plan without routing/model calls;
13. content/subject/capability/integrity invalidation behavior remains unchanged;
14. architecture and privacy tests prevent bypasses and secret/free-text logging.

## 13. Migration order

1. Add additive request/response DTOs and domain values.
2. Add mapper and validation tests.
3. Populate clarification options and reason projections.
4. Add bounded adjustment handling.
5. Introduce typed task section payloads and reconnect P1 composer.
6. Add the two missing deterministic task paths.
7. Wire the optional classifier behind disabled-by-default configuration.
8. Connect expiry-only reissue.
9. Run focused tests, full backend tests, architecture/privacy tests, and update status documents.

## 14. Acceptance

P2 backend closure is complete only when:

- a real client can finish choice and text clarification without mock-only fields;
- incremental plan adjustment is bound to the original question and pending plan identity;
- every non-success task has safe task-level diagnostic data;
- P2 Portfolio facts preserve P1 typed sections and section-level provenance;
- all seven declared task types are reachable;
- the optional classifier has a real runtime seam and safe unavailable fallback;
- expiry-only confirmation reissues the same plan;
- focused and full backend gates pass;
- status documents describe runtime reachability accurately.

## 15. Implementation result

Implemented on branch `codex/p2-backend-closure` from baseline `c128002`.

The production path now includes:

- additive adjustment and clarification-resolution request DTOs with fail-closed domain validation;
- populated public clarification options, opaque clarification correlation, continuing/blocked goal labels;
- task reason codes, safe dependency display indexes and deterministic plan summaries;
- typed P1 section composition for P2 Portfolio facts with section-level provenance;
- deterministic reachability for all seven declared task types;
- an optional provider-backed classifier, disabled by default and additionally gated by provider authorization;
- same-plan confirmation reissue for expiry-only submissions.

Verification on 2026-08-11:

- full backend suite: 1151 tests, 0 failures, 0 errors, 9 environment-dependent skips;
- additive request-contract tests: 9 tests, 0 failures;
- no frontend production files were changed in this backend worktree.

Operational opt-in is `PORTFOLIO_SEMANTIC_CLASSIFIER_ENABLED=true`; it is ineffective unless the existing conversational-agent enablement, visitor data-policy approval, model policy and provider registry checks also allow the call.
