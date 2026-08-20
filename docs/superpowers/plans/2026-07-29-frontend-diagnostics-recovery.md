# Frontend Diagnostics and Recovery Implementation Plan
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correlate browser failures with backend requests, give the UI stable recovery actions, and upload only allowlisted browser errors and slow-request events.

**Architecture:** The shared request module owns client correlation and typed API failures. A separate best-effort diagnostics Transport has no dependency on the normal request module, preventing recursive failure. The backend accepts a small closed DTO, validates and rate-limits it, then translates it into safe diagnostic events without persistence.

**Tech Stack:** Vue 3.5, TypeScript 5.8, Vite 7, Vitest 3, Playwright 1.53, Java 21, Spring Boot 3.5.3, MockMvc

## Global Constraints

- Implement `2026-07-29-observability-core-error-contract.md` before this plan.
- The Agent execution diagnostics plan may run before or after Tasks 1–3, but must finish before the final release gate.
- Never upload or log visitor questions, messages, answers, session content, URLs, headers, request bodies, response bodies, stack text, exception messages, Provider data, retrieval data, raw IP addresses, or credentials.
- `clientSessionId` exists only in module memory and changes on refresh.
- `clientRequestId` changes for every network request.
- Do not write diagnostic data to LocalStorage, SessionStorage, IndexedDB, cookies, URLs, or browser history.
- Only errors, cancellation, invalid responses, runtime failures, and requests slower than 5 seconds are reportable.
- Diagnostics upload failure is silent, is never retried, and must not create another diagnostic event.
- Production and test Java must not use `var`, record types, or Lombok.
- Follow RED, GREEN, REFACTOR for every task.

---

### Task 1: Add client correlation and typed request failures

**Files:**
- Create: `frontend/src/shared/diagnostics/clientCorrelation.ts`
- Create: `frontend/src/shared/diagnostics/clientCorrelation.test.ts`
- Create: `frontend/src/features/portfolio/api/apiErrorActions.ts`
- Create: `frontend/src/features/portfolio/api/apiErrorActions.test.ts`
- Modify: `frontend/src/features/portfolio/api/portfolioApi.ts`
- Modify: `frontend/src/features/portfolio/api/portfolioApi.test.ts`

**Interfaces:**
- Produces: `getClientSessionId(): string`, `createClientRequestId(): string`.
- Produces: `PortfolioApiError.kind`, `.code`, `.status`, `.requestId`,
  `.retryAfterSeconds`, `.action`, and `.clientRequestId`.
- Produces request headers `X-Client-Session-Id` and `X-Client-Request-Id`.

- [ ] **Step 1: Write failing ID lifetime tests**

```ts
it('keeps one session id in module memory and creates a request id per call', () => {
  const sessionA = getClientSessionId()
  const sessionB = getClientSessionId()
  const requestA = createClientRequestId()
  const requestB = createClientRequestId()
  const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

  expect(sessionA).toBe(sessionB)
  expect(requestA).not.toBe(requestB)
  expect(sessionA).toMatch(UUID)
  expect(requestA).toMatch(UUID)
})
```

Also spy on all browser persistence functions and assert they are never called.

- [ ] **Step 2: Write failing error action tests**

```ts
expect(actionForApiError('ANSWER_RATE_LIMITED')).toBe('RETRY_AFTER')
expect(actionForApiError('ANSWER_REQUEST_TIMEOUT')).toBe('RETRY')
expect(actionForApiError('VALIDATION_ERROR')).toBe('CORRECT_INPUT')
expect(actionForApiError('PROJECT_NOT_FOUND')).toBe('NAVIGATE_BACK')
expect(actionForApiError('UNKNOWN_BACKEND_CODE')).toBe('RETRY')
```

- [ ] **Step 3: Run RED**

```powershell
npm.cmd --prefix frontend test -- --run src/shared/diagnostics/clientCorrelation.test.ts src/features/portfolio/api/apiErrorActions.test.ts src/features/portfolio/api/portfolioApi.test.ts
```

Expected: new modules are missing and `PortfolioApiError` lacks the fields.

- [ ] **Step 4: Implement the closed frontend types**

```ts
export type ErrorAction =
  | 'NONE'
  | 'RETRY'
  | 'RETRY_AFTER'
  | 'CORRECT_INPUT'
  | 'NAVIGATE_BACK'

export type ApiFailureKind = 'HTTP' | 'TIMEOUT' | 'NETWORK' | 'INVALID_RESPONSE' | 'CANCELLED'
```

Create one module-level session ID:

```ts
const clientSessionId = globalThis.crypto.randomUUID()

export function getClientSessionId(): string {
  return clientSessionId
}

export function createClientRequestId(): string {
  return globalThis.crypto.randomUUID()
}
```

`actionForApiError` uses an explicit switch and returns `RETRY` for unknown codes.

- [ ] **Step 5: Extend the request module**

Before `fetch`, create a client request ID and merge only the two correlation headers:

```ts
const clientRequestId = createClientRequestId()
const headers = new Headers(init.headers)
headers.set('X-Client-Session-Id', getClientSessionId())
headers.set('X-Client-Request-Id', clientRequestId)
```

Read the response header:

```ts
const requestId = response.headers.get('X-Request-Id') ?? undefined
```

Classify failures precisely:

```text
caller abort -> CANCELLED / REQUEST_CANCELLED / NONE
local timeout -> TIMEOUT / CLIENT_REQUEST_TIMEOUT / RETRY
fetch rejection -> NETWORK / CLIENT_NETWORK_ERROR / RETRY
non-2xx -> HTTP / server code / mapped action
JSON parse on 2xx -> INVALID_RESPONSE / CLIENT_INVALID_RESPONSE / RETRY
```

Do not place response text, URL, init, body, or cause on `PortfolioApiError`.

- [ ] **Step 6: Run focused tests and build**

```powershell
npm.cmd --prefix frontend test -- --run src/shared/diagnostics/clientCorrelation.test.ts src/features/portfolio/api/apiErrorActions.test.ts src/features/portfolio/api/portfolioApi.test.ts
npm.cmd --prefix frontend run build
```

Expected: tests and TypeScript build pass.

- [ ] **Step 7: Commit**

```powershell
git add frontend/src/shared/diagnostics frontend/src/features/portfolio/api
git commit -m "前端：增加请求关联与稳定错误动作"
```

### Task 2: Apply recovery actions to Agent and public-content UI

**Files:**
- Modify: `frontend/src/features/agent/components/AgentWorkspace.vue`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.test.ts`
- Modify: `frontend/src/features/agent/components/ConversationThread.vue`
- Modify: `frontend/src/features/agent/components/ConversationThread.test.ts`
- Modify: `frontend/src/features/public-content/composables/usePublicContent.ts`
- Modify: `frontend/src/features/public-content/composables/usePublicContent.test.ts`

**Interfaces:**
- Consumes: typed `PortfolioApiError.action`.
- Produces: distinct retry, retry-after, correction, navigation, cancellation, and generic states.

- [ ] **Step 1: Write failing UI recovery tests**

Add tests that assert:

```text
ANSWER_RATE_LIMITED + retryAfterSeconds=12 -> countdown text and disabled retry
ANSWER_REQUEST_TIMEOUT -> retry button
VALIDATION_ERROR -> input remains and correction message appears
PROJECT_NOT_FOUND -> safe back-navigation action
REQUEST_CANCELLED -> no failure answer is appended
unknown code -> generic retry action
```

Use fake `PortfolioApiError` instances; never use real question text as diagnostic fixtures.

- [ ] **Step 2: Run RED**

```powershell
npm.cmd --prefix frontend test -- --run src/features/agent/components/AgentWorkspace.test.ts src/features/agent/components/ConversationThread.test.ts src/features/public-content/composables/usePublicContent.test.ts
```

Expected: all errors still collapse to the current generic message.

- [ ] **Step 3: Implement a view-safe failure model**

Inside `AgentWorkspace.vue`, store:

```ts
interface AnswerFailureView {
  message: string
  action: ErrorAction
  requestId?: string
  retryAfterSeconds?: number
}
```

Convert only `PortfolioApiError` fields. Never retain the original Error or cause in Vue state.
`REQUEST_CANCELLED` clears pending state without setting a failure. Retry reuses the current
production `requestToken` behavior.

`ConversationThread.vue` renders exactly one action matching the model. The request ID is shown
as a copyable short support reference, not placed in a URL.

- [ ] **Step 4: Run UI tests and full frontend suite**

```powershell
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
```

Expected: all tests and build pass.

- [ ] **Step 5: Commit**

```powershell
git add frontend/src/features/agent frontend/src/features/public-content
git commit -m "前端：按错误码提供安全恢复动作"
```

### Task 3: Build the isolated best-effort frontend diagnostics Transport

**Files:**
- Create: `frontend/src/shared/diagnostics/frontendDiagnosticTypes.ts`
- Create: `frontend/src/shared/diagnostics/frontendDiagnostics.ts`
- Create: `frontend/src/shared/diagnostics/frontendDiagnostics.test.ts`
- Create: `frontend/src/shared/diagnostics/diagnosticTransport.ts`
- Create: `frontend/src/shared/diagnostics/diagnosticTransport.test.ts`
- Modify: `frontend/src/features/portfolio/api/portfolioApi.ts`
- Modify: `frontend/src/features/portfolio/api/portfolioApi.test.ts`
- Modify: `frontend/src/main.ts`

**Interfaces:**
- Produces: `frontendDiagnostics.debug(event)` and `frontendDiagnostics.report(event)`.
- Produces only six reportable event names from the approved design.
- Transport posts directly to `/api/v1/client-diagnostics`.

- [ ] **Step 1: Write failing event whitelist tests**

```ts
const event: ReportableFrontendEvent = {
  schemaVersion: 1,
  eventName: 'frontend.agent.request.failed',
  occurredAt: '2026-07-29T00:00:00.000Z',
  clientSessionId: getClientSessionId(),
  clientRequestId: crypto.randomUUID(),
  errorCode: 'CLIENT_NETWORK_ERROR',
  durationBucket: 'FROM_1000_TO_4999_MS',
}

expect(serializeFrontendEvent(event)).not.toContain('question')
```

Use `// @ts-expect-error` tests to prove `message`, `stack`, `url`, `headers`, `requestBody`,
and `responseBody` are not accepted properties.

- [ ] **Step 2: Write failing Transport isolation tests**

Assert:

- queue length never exceeds 20;
- flush sends at most 10;
- timeout is 2 seconds;
- no retry occurs;
- a failed upload does not call `report`;
- queue is memory-only;
- `pagehide` uses `fetch` with `keepalive: true`;
- normal request success under 5 seconds is not reported.

- [ ] **Step 3: Run RED**

```powershell
npm.cmd --prefix frontend test -- --run src/shared/diagnostics/frontendDiagnostics.test.ts src/shared/diagnostics/diagnosticTransport.test.ts src/features/portfolio/api/portfolioApi.test.ts
```

Expected: modules are missing.

- [ ] **Step 4: Implement closed event types and Transport**

The reportable union contains:

```ts
type FrontendDiagnosticEventName =
  | 'frontend.content.load.failed'
  | 'frontend.agent.request.failed'
  | 'frontend.agent.request.slow'
  | 'frontend.agent.request.cancelled'
  | 'frontend.response.invalid'
  | 'frontend.runtime.failed'
```

Allowed optional fields are only:

```text
serverRequestId
turnId
errorCode
errorKind
errorFingerprint
durationBucket
```

The Transport uses raw `fetch`, not `request()`:

```ts
await fetch('/api/v1/client-diagnostics', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(batch),
  keepalive,
  signal: controller.signal,
})
```

Catch and discard every Transport failure. Do not use `console.error` in production.

- [ ] **Step 5: Integrate request failure and slow-event reporting**

Measure `performance.now()` around the normal request. On failure report the typed safe fields.
If duration is at least 5,000 ms, report `frontend.agent.request.slow` after completion. Do not
include URL; classify the operation as `PUBLIC_CONTENT`, `PROJECT`, or `ANSWER` through an
explicit `RequestOptions.operation` enum.

Install runtime `error` and `unhandledrejection` listeners in `main.ts`. They map only to
`errorKind` and a safely normalized first-party stack fingerprint; if normalization fails, omit
the fingerprint.

- [ ] **Step 6: Run full frontend verification**

```powershell
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
```

Expected: tests and build pass.

- [ ] **Step 7: Commit**

```powershell
git add frontend/src/shared/diagnostics frontend/src/features/portfolio/api frontend/src/main.ts
git commit -m "前端：增加受限错误与性能诊断"
```

### Task 4: Add the closed backend diagnostics ingest seam

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/common/observability/FrontendDiagnosticEventName.java`
- Create: `backend/src/main/java/com/portfolio/agent/common/observability/FrontendDiagnosticProperties.java`
- Create: `backend/src/main/java/com/portfolio/agent/common/observability/FrontendDiagnosticAdmissionGate.java`
- Create: `backend/src/main/java/com/portfolio/agent/common/web/FrontendDiagnosticEventRequest.java`
- Create: `backend/src/main/java/com/portfolio/agent/common/web/FrontendDiagnosticBatchRequest.java`
- Create: `backend/src/main/java/com/portfolio/agent/common/web/FrontendDiagnosticsController.java`
- Create: `backend/src/main/java/com/portfolio/agent/common/web/FrontendDiagnosticsBodyLimitFilter.java`
- Test: `backend/src/test/java/com/portfolio/agent/common/observability/FrontendDiagnosticAdmissionGateTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/common/web/FrontendDiagnosticsControllerTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/common/web/FrontendDiagnosticsBodyLimitFilterTest.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-prod.yml`

**Interfaces:**
- Consumes: closed frontend DTO and `DiagnosticEventPublisher`.
- Produces: HTTP 202 without persistence.
- Produces `event.origin=browser` and `client.reported_request_id`; never overwrites MDC.

- [ ] **Step 1: Write failing DTO and endpoint tests**

Cover:

```text
valid one-event batch -> 202
11 events -> 400
unknown field -> 400
unknown eventName -> 400
invalid UUID -> 400
message/stack/url field -> 400
body over 16 KiB -> 413
disabled ingest -> 404
rate exceeded -> 429
valid event -> one safe DiagnosticEvent
```

- [ ] **Step 2: Run RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=FrontendDiagnosticAdmissionGateTest,FrontendDiagnosticsControllerTest,FrontendDiagnosticsBodyLimitFilterTest test
```

Expected: endpoint types do not exist.

- [ ] **Step 3: Implement the closed DTO**

Use constructor validation annotations:

```java
@NotNull
@Size(min = 1, max = 10)
private final List<@Valid FrontendDiagnosticEventRequest> events;
```

Every string has a fixed maximum and pattern. The event DTO contains no generic metadata,
message, stack, URL, headers, body, or arbitrary map. Jackson's existing unknown-property
failure remains enabled.

- [ ] **Step 4: Implement admission and body limits**

Configure:

```yaml
portfolio:
  diagnostics:
    frontend-ingest-enabled: false
    frontend-max-batch-size: 10
    frontend-max-body-bytes: 16384
    frontend-events-per-minute: 30
```

`application-prod.yml` explicitly sets ingest enabled from
`${PORTFOLIO_FRONTEND_DIAGNOSTICS_ENABLED:false}`.

The body-limit filter applies only to `/api/v1/client-diagnostics`, rejects a known oversized
`Content-Length`, and wraps the input stream with a 16 KiB counting stream to handle chunked
bodies.

Hash the resolved source address with the existing `AnonymousSourceHasher` before admission.
Do not put the address or hash in the event.

- [ ] **Step 5: Implement the Controller**

When disabled, return 404. When admitted, translate each request into a WARN or INFO event:

```text
event.origin=browser
client.session.id
client.request.id
client.reported_request_id
turn.id
error.code
error.kind
error.fingerprint
duration.bucket
```

Return `ResponseEntity.accepted().build()`. Never persist the batch.

- [ ] **Step 6: Run focused and backend suites**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=FrontendDiagnosticAdmissionGateTest,FrontendDiagnosticsControllerTest,FrontendDiagnosticsBodyLimitFilterTest test
mvn.cmd -f backend/pom.xml test
```

Expected: all pass.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/portfolio/agent/common backend/src/test/java/com/portfolio/agent/common backend/src/main/resources
git commit -m "可观测性：接收受限前端诊断事件"
```

### Task 5: Add browser, privacy, and packaged-JAR gates

**Files:**
- Modify: `frontend/e2e/portfolio.spec.ts`
- Modify: `frontend/e2e/support/publicApiMocks.ts`
- Modify: `scripts/privacy-check.ps1`
- Modify: `scripts/privacy-check.test.ps1`
- Modify: `scripts/run-jar-e2e.ps1`
- Modify: `scripts/run-jar-e2e.test.ps1`
- Modify: `README.md`
- Modify: `SECURITY.md`
- Modify: `docs/04-项目代码约束.md`
- Modify: `docs/08-当前实现状态.md`

**Interfaces:**
- Produces a release gate proving correlation, recovery, ingest isolation, and privacy.

- [ ] **Step 1: Add failing Playwright scenarios**

Cover:

```text
429 -> countdown
503 ANSWER_REQUEST_TIMEOUT -> retry
PROJECT_NOT_FOUND -> safe navigation
cancel -> no failure answer
slow answer -> one slow diagnostic
backend error -> diagnostic contains returned X-Request-Id
network failure before response -> diagnostic contains clientRequestId without serverRequestId
diagnostics upload failure -> no visible error and no retry
refresh -> new clientSessionId
```

Assert every diagnostics request body lacks:

```text
question
messages
answer
stack
url
headers
requestBody
responseBody
```

- [ ] **Step 2: Extend privacy fixtures**

Add unsafe Java and TypeScript fixtures for diagnostic DTOs or log calls that contain forbidden
fields. Add safe fixtures for ID, error code, duration bucket, and fingerprint.

- [ ] **Step 3: Extend packaged-JAR smoke**

After starting the JAR:

1. call one successful endpoint and assert `X-Request-Id` and `X-Trace-Id`;
2. post one valid diagnostic batch and expect 202 when explicitly enabled;
3. post an unknown field and expect 400;
4. post an oversized body and expect 413;
5. scan captured stdout for JSON and sentinel absence.

- [ ] **Step 4: Update the authority documents**

Document `/api/v1/client-diagnostics` as the only exception to read-only public endpoints:

```text
It accepts a closed, rate-limited, non-persistent diagnostic event contract.
It never accepts visitor content, arbitrary metadata, raw stack traces, URLs, headers,
request bodies, response bodies, raw addresses, or credentials.
```

- [ ] **Step 5: Run the complete release gate**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.test.ps1
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
mvn.cmd -f backend/pom.xml test
mvn.cmd -f backend/pom.xml clean package
powershell -ExecutionPolicy Bypass -File scripts/run-jar-e2e.test.ps1
npm.cmd --prefix frontend run test:e2e
```

Expected: all commands exit 0.

- [ ] **Step 6: Commit**

```powershell
git add frontend/e2e scripts README.md SECURITY.md docs/04-项目代码约束.md docs/08-当前实现状态.md
git commit -m "质量：加入前后端诊断与隐私发布门禁"
```
