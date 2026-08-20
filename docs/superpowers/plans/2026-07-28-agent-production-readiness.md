# Agent Production Readiness Implementation Plan
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `/api/v2/answers` 加固为具有匿名限流、并发保护、幂等、超时、取消、明确降级、严格输出校验和安全状态展示的 V1 单次 JSON 回答链路。

**Architecture:** 在 answer 模块新增生产请求门面，控制来源哈希、准入、幂等和 12 秒预算；现有 `ConversationalAgentRuntime` 继续负责回答业务，并通过统一输出预算和明确 generation metadata 形成合法结果。前端在共享请求层支持外部 AbortSignal 和 15 秒预算，AgentWorkspace 负责 token、取消、迟到响应隔离与状态提示。

**Tech Stack:** Java 21、Spring Boot 3.5、JUnit 5、MockMvc、Mockito、Java `Clock`/`CompletableFuture`、Vue 3、TypeScript、Vitest、Playwright、PowerShell 5.1。

## Global Constraints

- 不实现 SSE、WebSocket 或 token streaming。
- 不增加访客许可弹窗或 consent 请求字段。
- 每来源 60 秒最多 10 个新 token，并发最多 2 个。
- Provider、后端、前端预算分别为 8 秒、12 秒、15 秒。
- 幂等键为来源短期哈希与规范 UUID `requestToken`，结果保留 2 分钟。
- 原始 IP、访客问题、完整回答、Provider payload、Authorization 和 API Key 不写日志。
- 普通 CI 不得调用真实 Provider；不自动重试或跨 Provider 故障转移。
- 输出预算固定为 title 120 字符、最多 8 blocks、单 block 2,000 字符、总 block 内容 8,000 字符。
- 当前工作树已有另一个前端 AI 的 Case 路由与页面改动；执行前必须在独立 worktree 或基于已提交的 Case 前端提交工作，不得覆盖这些改动。

---

### Task 1: 固定 v2 请求 token、回答元数据和错误契约

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/dto/request/ConversationAnswerRequest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/ConversationAnswerResult.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/dto/response/ConversationAnswerResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerSource.java`
- Modify: `backend/src/main/java/com/portfolio/agent/common/web/ApiErrorResponse.java`
- Modify: `backend/src/main/java/com/portfolio/agent/common/web/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/exception/AnswerErrorCode.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/dto/request/ConversationAnswerRequestTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/dto/response/ConversationAnswerResponseTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/common/web/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: existing v2 request/response and `GenerationMode`.
- Produces: `UUID requestToken`, `GenerationMode generationMode`, `AnswerSource answerSource`, optional `String noticeCode`, and nullable `Integer retryAfterSeconds`.

- [ ] **Step 1: Write failing request contract tests**

Add tests proving:

```java
assertThat(validRequest.getRequestToken())
        .isEqualTo(UUID.fromString("6b2d8895-4108-4b4d-aee0-21f6e7c4f333"));
```

and Bean Validation rejects missing, blank or non-UUID token. Update every existing request fixture to provide a UUID.

- [ ] **Step 2: Run RED**

Run:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml `
  -Dtest=ConversationAnswerRequestTest test
```

Expected: compilation or assertion failure because `requestToken` does not exist.

- [ ] **Step 3: Implement the request field**

Use a non-null `UUID` constructor property:

```java
@NotNull(message = "requestToken is required")
private final UUID requestToken;
```

Do not include it in `toString()`.

- [ ] **Step 4: Write failing response metadata tests**

Construct deterministic, model and fallback results and assert JSON-visible getters:

```java
assertThat(response.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
assertThat(response.getAnswerSource()).isEqualTo(AnswerSource.PRESET);
assertThat(response.getNoticeCode()).isEqualTo("MODEL_UNAVAILABLE_FALLBACK");
```

Extend `AnswerSource` with `TOOL` so v2 can represent all approved origins.

- [ ] **Step 5: Run RED, then minimally add metadata**

`ConversationAnswerResult` and `ConversationAnswerResponse` must carry:

```java
GenerationMode generationMode;
AnswerSource answerSource; // nullable only when no content origin applies
String noticeCode;         // nullable, stable enum-like public code
```

All successful model paths use `MODEL`; normal local answers use `DETERMINISTIC`;
Provider failure with a usable local answer uses `FALLBACK`.

- [ ] **Step 6: Add retry-aware error body**

Extend `ApiErrorResponse` with nullable `retryAfterSeconds`. Add overloads in
`GlobalExceptionHandler` without changing existing error messages. Add error codes:

```java
ANSWER_RATE_LIMITED(429)
ANSWER_CONCURRENCY_LIMITED(429)
ANSWER_REQUEST_TIMEOUT(503)
```

Tests must assert error JSON contains no token, IP, question or Provider detail.

- [ ] **Step 7: Run GREEN and commit**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml `
  -Dtest=ConversationAnswerRequestTest,ConversationAnswerResponseTest,GlobalExceptionHandlerTest test
git add backend/src/main backend/src/test
git commit -m "契约：增加回答幂等标识与生产状态"
```

---

### Task 2: 实现可信来源解析和短期匿名哈希

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/adapter/web/ClientAddressResolver.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/service/AnonymousSourceHasher.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/adapter/web/AnswerProductionProperties.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/adapter/web/AnswerProductionConfiguration.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/adapter/web/ClientAddressResolverTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/AnonymousSourceHasherTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/adapter/web/AnswerProductionPropertiesTest.java`

**Interfaces:**
- Produces: `String ClientAddressResolver.resolve(HttpServletRequest)` and `String AnonymousSourceHasher.hash(String address)`.
- Configuration prefix: `portfolio.answer-production`.

- [ ] **Step 1: Write source resolution RED tests**

Cover:

```java
assertThat(resolver.resolve(requestWithRemote("203.0.113.7")))
        .isEqualTo("203.0.113.7");
```

When `trustProxy=false`, forged `X-Forwarded-For` must be ignored. When explicitly
enabled with a trusted proxy CIDR, only the first validated client address is used.
Malformed headers fall back to remote address.

- [ ] **Step 2: Implement conservative resolver**

Default `trustProxy=false`. Do not log either resolved or header address.

- [ ] **Step 3: Write hasher RED tests**

Use fixed secret bytes in tests and assert:

- same address/salt gives same 64-char lowercase hex;
- different addresses differ;
- output does not contain address;
- different process salts differ.

- [ ] **Step 4: Implement HMAC source hashing**

Use `HmacSHA256` with 32 random bytes created once at process startup. Expose no salt getter.
The UI/log layer may use at most the first 8 hex characters, never the raw address.

- [ ] **Step 5: Add validated properties**

Defaults:

```text
requestsPerMinute=10
maxConcurrent=2
requestTimeout=12s
idempotencyTtl=2m
trustProxy=false
```

Reject zero/negative or widened invalid values during bean creation.

- [ ] **Step 6: Run tests and commit**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml `
  -Dtest=ClientAddressResolverTest,AnonymousSourceHasherTest,AnswerProductionPropertiesTest test
git add backend/src/main backend/src/test
git commit -m "安全：增加匿名来源识别与生产预算配置"
```

---

### Task 3: 实现每分钟限流和并发准入

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/service/AnswerAdmissionGate.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/AnswerAdmission.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/AnswerAdmissionGateTest.java`

**Interfaces:**
- Consumes: source hash, `UUID requestToken`, injected `Clock`, 10/minute and 2 concurrent.
- Produces: closeable `AnswerAdmission` or `AnswerRateLimitException` carrying stable code and retry seconds.

- [ ] **Step 1: Write deterministic RED tests**

With `Clock.fixed`/mutable test clock, prove:

- first 10 distinct tokens pass;
- 11th returns `ANSWER_RATE_LIMITED` and `retryAfterSeconds` in `1..60`;
- after 60 seconds a new token passes;
- third concurrent new token returns `ANSWER_CONCURRENCY_LIMITED`;
- closing an admission releases concurrency;
- different source hashes are isolated;
- duplicate token is classified before consuming another rate slot.

- [ ] **Step 2: Run RED**

Expected: missing `AnswerAdmissionGate`.

- [ ] **Step 3: Implement synchronized per-source state**

Use bounded `ConcurrentHashMap<String, SourceWindow>`. `AnswerAdmission` implements
`AutoCloseable` and releases exactly once. Add stale-entry cleanup based on the newest
window/active request time; never create an unbounded permanent visitor registry.

- [ ] **Step 4: Run GREEN and commit**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml `
  -Dtest=AnswerAdmissionGateTest test
git add backend/src/main backend/src/test
git commit -m "安全：增加匿名回答限流与并发保护"
```

---

### Task 4: 实现 2 分钟幂等协调和 12 秒后端预算

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/service/AnswerIdempotencyCoordinator.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/service/ProductionConversationService.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/exception/AnswerRateLimitException.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/exception/AnswerRequestTimeoutException.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/controller/ConversationAnswerController.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/web/AnswerProductionConfiguration.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/AnswerIdempotencyCoordinatorTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/ProductionConversationServiceTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/controller/ConversationAnswerControllerTest.java`

**Interfaces:**
- Key: `sourceHash + ":" + requestToken`.
- `ProductionConversationService.answer(request, rawAddress)` returns `ConversationAnswerResult`.
- Cached unit: completed result or stable public exception category; never Provider exception/detail.

- [ ] **Step 1: Write idempotency RED tests**

Use latches, not sleeps:

```java
assertThat(executionCount.get()).isEqualTo(1);
assertThat(first.join()).isSameAs(second.join());
```

Cover concurrent merge, completed reuse, stable error reuse, TTL expiry and cleanup.

- [ ] **Step 2: Implement coordinator with `CompletableFuture`**

Only the map winner executes the supplier. All callers observe the same future. Remove cancelled
or internal-bug futures; retain valid result/stable public error for 2 minutes.

- [ ] **Step 3: Write production service RED tests**

Prove order:

1. hash source;
2. check existing idempotency entry;
3. acquire admission only for a new execution;
4. execute runtime under 12 seconds;
5. always release admission;
6. timeout maps to stable timeout or legal fallback, never leaks exception.

Use an injected `Executor` and controllable future; do not wait 12 real seconds.

- [ ] **Step 4: Implement service and controller wiring**

Controller accepts `HttpServletRequest`, resolves remote address, then calls production service.
Do not pass servlet types into domain/runtime classes.

- [ ] **Step 5: Add MockMvc tests**

Assert:

- missing/invalid token is `400`;
- 11th request is `429` with `Retry-After`;
- third concurrent request is `429`;
- duplicate token calls runtime once;
- errors contain no IP/token/question.

- [ ] **Step 6: Run GREEN and commit**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml `
  -Dtest=AnswerIdempotencyCoordinatorTest,ProductionConversationServiceTest,ConversationAnswerControllerTest test
git add backend/src/main backend/src/test
git commit -m "功能：增加回答幂等与服务端超时预算"
```

---

### Task 5: 加固 Provider 输出并形成明确 fallback

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/domain/ConversationOutputBudget.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/ConversationDraftValidator.java`
- Create: `backend/src/main/java/com/portfolio/agent/answer/service/ConversationResponseValidator.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/ConversationalAgentRuntime.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/DeterministicConversationFallback.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ConversationalAgentConfiguration.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/ConversationDraftValidatorTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/ConversationResponseValidatorTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/answer/service/ConversationalAgentRuntimeTest.java`

**Interfaces:**
- One immutable budget: title 120, blocks 8, block chars 2,000, total chars 8,000.
- Fallback notice: `MODEL_UNAVAILABLE_FALLBACK`.

- [ ] **Step 1: Write output budget RED tests**

Cover null/blank title, title 121, zero/9 blocks, blank block, block 2,001, total 8,001,
missing portfolio references, unknown reference, non-direct evidence and cross-subject reference.

- [ ] **Step 2: Implement one shared budget**

Both Draft and final response validator receive the same `ConversationOutputBudget` bean.
Count Unicode code points rather than UTF-16 code units.

- [ ] **Step 3: Write fallback metadata RED tests**

For Provider timeout/failure/invalid Draft:

```java
assertThat(result.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
assertThat(result.isDegraded()).isTrue();
assertThat(result.getNoticeCode()).isEqualTo("MODEL_UNAVAILABLE_FALLBACK");
```

Normal local greeting/preset uses `DETERMINISTIC`; accepted Provider result uses `MODEL`.
Rejected and boundary responses retain honest `resolution`.

- [ ] **Step 4: Implement metadata and final validation**

Discard the whole invalid model Draft. Never partially retain model blocks. Run final response
validation before returning from runtime. If the fallback itself violates the fixed budget,
return a minimal legal `BOUNDARY` without model content.

- [ ] **Step 5: Verify the 8-second Provider timeout**

Extend adapter configuration tests to assert connect/read timeout equals model policy 8 seconds.
Do not add retries.

- [ ] **Step 6: Run GREEN and commit**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml `
  -Dtest=ConversationDraftValidatorTest,ConversationResponseValidatorTest,ConversationalAgentRuntimeTest,ConversationalAgentConfigurationTest test
git add backend/src/main backend/src/test
git commit -m "安全：校验模型输出并明确确定性降级"
```

---

### Task 6: 增加前端 15 秒预算、主动取消和安全错误类型

**Files:**
- Modify: `frontend/src/features/portfolio/api/portfolioApi.ts`
- Modify: `frontend/src/features/agent/api/answerApi.ts`
- Modify: `frontend/src/features/agent/api/answerApi.test.ts`
- Modify: `frontend/src/features/agent/model/answerTypes.ts`
- Create: `frontend/src/features/agent/model/requestToken.ts`
- Test: `frontend/src/features/agent/model/requestToken.test.ts`

**Interfaces:**
- `request<T>(url, init, options?: { signal?: AbortSignal; timeoutMs?: number })`.
- `askQuestion(input, options?: { signal?: AbortSignal })`.
- Agent timeout exactly `15_000`; other portfolio GET defaults remain unchanged unless explicitly passed.

- [ ] **Step 1: Write transport RED tests**

Prove external abort and timeout are distinct:

```ts
await expect(askQuestion(input, { signal: controller.signal }))
  .rejects.toMatchObject({ code: 'REQUEST_CANCELLED' })
await expect(timedRequest)
  .rejects.toMatchObject({ code: 'REQUEST_TIMEOUT' })
```

Also parse `code` and `retryAfterSeconds` from `429`.

- [ ] **Step 2: Implement composed cancellation**

Compose caller signal and a 15-second timeout controller without leaking listeners. A user abort
must not be rewritten as timeout. Keep timeout active through `response.json()`.

- [ ] **Step 3: Add UUID token**

`createRequestToken()` uses `crypto.randomUUID()`. Provide a deterministic injected/fallback
implementation only for tests and browsers lacking it; output must still be UUID-shaped.
`askQuestion` includes `requestToken` in JSON.

- [ ] **Step 4: Extend response/error types**

Add v2 `generationMode`, `answerSource`, `noticeCode`, and typed API error fields:

```ts
code?: string
retryAfterSeconds?: number
```

Reject structurally invalid successful responses before mapping.

- [ ] **Step 5: Run tests and commit**

```powershell
npm.cmd --prefix frontend test -- --run `
  src/features/agent/api/answerApi.test.ts `
  src/features/agent/model/requestToken.test.ts
git add frontend/src/features/portfolio/api frontend/src/features/agent/api frontend/src/features/agent/model
git commit -m "功能：增加回答超时取消与请求标识"
```

---

### Task 7: 实现前端重复保护、停止等待和生产状态展示

**Files:**
- Modify: `frontend/src/features/agent/components/AgentWorkspace.vue`
- Modify: `frontend/src/features/agent/components/AgentWorkspace.test.ts`
- Modify: `frontend/src/features/agent/components/ConversationThread.vue`
- Modify: `frontend/src/features/agent/components/ConversationThread.test.ts`
- Modify: `frontend/src/features/agent/model/answerLabels.ts`
- Modify: `frontend/src/features/agent/model/answerLabels.test.ts`
- Modify: `frontend/src/features/agent/model/mapAnswerResponse.ts`
- Modify: `frontend/src/features/agent/model/mapAnswerResponse.test.ts`

**Interfaces:**
- One request token per user submission; retries reuse token.
- `ConversationThread` emits `cancel`.
- Stable fallback copy: `模型服务暂时不可用，当前展示基于已发布内容的确定性回答。`

- [ ] **Step 1: Write duplicate/cancel RED tests**

Assert double click and Enter while pending call `askQuestion` once. Assert retry reuses the
failed request token, while a new question creates another token.

- [ ] **Step 2: Write cancellation/late response RED tests**

Click `[data-agent-cancel]`, assert signal aborted, pending false, no error answer appended, and a
later resolved Promise cannot mutate the session.

- [ ] **Step 3: Implement active request handle**

Store `{ version, token, controller, context }`. `invalidatePendingRequest()` aborts before
incrementing version. Component disposal and session switching also abort safely.

- [ ] **Step 4: Write state-label RED tests**

Cover exact user-visible mappings:

- deterministic;
- model;
- retrieval enhanced;
- fallback/degraded with fixed notice;
- rejected;
- boundary;
- 429 with retry seconds;
- timeout;
- user cancelled.

- [ ] **Step 5: Implement accessible UI**

Add a visible stop button while pending. Use `role="status"` for progress/degraded notice and
`role="alert"` only for actionable errors. Do not expose technical error bodies or Provider names.

- [ ] **Step 6: Run tests and commit**

```powershell
npm.cmd --prefix frontend test -- --run `
  src/features/agent/components/AgentWorkspace.test.ts `
  src/features/agent/components/ConversationThread.test.ts `
  src/features/agent/model/answerLabels.test.ts `
  src/features/agent/model/mapAnswerResponse.test.ts
git add frontend/src/features/agent
git commit -m "功能：增加回答取消重复保护与状态提示"
```

---

### Task 8: 加入隐私、JAR、浏览器和文档发布门禁

**Files:**
- Modify: `scripts/privacy-check.ps1`
- Modify: `scripts/privacy-check.test.ps1`
- Modify: `scripts/run-jar-e2e.ps1`
- Modify: `scripts/run-jar-e2e.test.ps1`
- Modify: `frontend/e2e/portfolio.spec.ts`
- Modify: `frontend/e2e/support/publicApiMocks.ts`
- Modify: `README.md`
- Modify: `docs/00-文档状态索引.md`
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/superpowers/specs/2026-07-28-agent-production-readiness-design.md`

**Interfaces:**
- Produces auditable gates for 429, duplicate token, fallback, cancel/late response and secret safety.

- [ ] **Step 1: Add privacy RED fixtures**

Privacy tests must reject:

- frontend `VITE_*API_KEY`;
- logging raw remote address, question, token or response body;
- error responses containing Authorization/Provider body;
- Java literal Key and built frontend credential assignment.

Safe fixtures may log request ID, source hash prefix, duration bucket and stable error code.

- [ ] **Step 2: Add JAR runner RED assertions**

Use deterministic/fake seams, not real external calls, to prove:

- same token returns same result and executes once;
- 11th new token receives 429/Retry-After;
- Provider failure returns 200 fallback with `degraded=true`;
- no output contains question, token, raw IP, Key or response body.

- [ ] **Step 3: Add browser E2E**

Mock delayed responses and cover double submit, stop waiting, ignored late response, 15-second
timeout, 429 and fallback notice. Keep single JSON; do not add EventSource.

- [ ] **Step 4: Update documentation**

Document exact defaults, single-instance limitation, trusted proxy default false, rollback,
server-only secrets, no consent UI, and streaming deferred.

- [ ] **Step 5: Run complete verification**

Before running, ensure real secrets are injected from outside the repository and no credential
file exists under the repo root.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/verify-release.ps1 `
  -SkipInstall
```

Expected:

- frontend typecheck/lint/build and all Vitest pass;
- backend all tests pass;
- privacy and static bundle checks pass;
- packaged Case API/v2 smoke passes;
- Playwright passes;
- Docker check either passes or is explicitly reported unavailable;
- ordinary verification makes no live Provider request.

- [ ] **Step 6: Commit**

```powershell
git add scripts frontend/e2e README.md docs
git commit -m "测试：接入Agent生产可用性发布门禁"
```

---

## Final Review Checklist

- [ ] Compare implementation against every completion criterion in the design spec.
- [ ] Confirm no tracked or untracked Case frontend work was overwritten.
- [ ] Confirm `git diff <base>...HEAD -- .env` is empty and no secret file is staged.
- [ ] Run backend full Maven tests, frontend full Vitest, build and Playwright.
- [ ] Run `scripts/verify-release.ps1 -SkipInstall`.
- [ ] Review logs and response fixtures for question, token, IP, Key and Provider-body leakage.
- [ ] Record that live Provider evidence is separate and only runs with explicit approval.
- [ ] Request independent code review and fix all Critical/Important findings.
