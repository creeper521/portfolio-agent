# Observability Core and Error Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build request correlation, safe structured logging, and one stable HTTP error contract without changing published wire codes.

**Architecture:** A `RequestDiagnosticsFilter` owns HTTP correlation and MDC lifecycle. Typed `DiagnosticEvent` values cross a small `DiagnosticEventPublisher` seam; a single SLF4J Adapter renders local text or production ECS JSON. Expected API failures keep the current response codes, while unexpected failures are mapped once at the outer HTTP seam.

**Tech Stack:** Java 21, Spring Boot 3.5.3, SLF4J 2, Logback, Spring MVC, JUnit 5, AssertJ, MockMvc

## Global Constraints

- Production and test Java must not use `var`, record types, or Lombok.
- Preserve published wire codes: `VALIDATION_ERROR`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`, `PROJECT_NOT_FOUND`, `CASE_NOT_FOUND`, `INVALID_ANSWER_CONTEXT`, `ANSWER_RATE_LIMITED`, `ANSWER_CONCURRENCY_LIMITED`, `ANSWER_REQUEST_TIMEOUT`, and `INTERNAL_ERROR`.
- Preserve `ANSWER_REQUEST_TIMEOUT` as HTTP 503.
- Never log visitor questions, messages, answers, prompts, Provider payloads, retrieval text, raw IP addresses, credentials, raw headers, request bodies, response bodies, local absolute paths, or Provider URLs.
- Client IDs are accepted only when they are canonical UUID strings.
- Normal application behavior must not depend on successful diagnostic publication.
- Production output is console-only ECS JSON; local output is readable text.
- Follow RED, GREEN, REFACTOR for every task.

---

### Task 1: Define the typed diagnostic event seam

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/common/observability/DiagnosticCode.java`
- Create: `backend/src/main/java/com/portfolio/agent/common/observability/DiagnosticLevel.java`
- Create: `backend/src/main/java/com/portfolio/agent/common/observability/DiagnosticEvent.java`
- Create: `backend/src/main/java/com/portfolio/agent/common/observability/DiagnosticEventPublisher.java`
- Create: `backend/src/main/java/com/portfolio/agent/common/observability/DroppedDiagnosticCounter.java`
- Create: `backend/src/main/java/com/portfolio/agent/common/observability/Slf4jDiagnosticEventPublisher.java`
- Test: `backend/src/test/java/com/portfolio/agent/common/observability/DiagnosticEventTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/common/observability/Slf4jDiagnosticEventPublisherTest.java`

**Interfaces:**
- Produces: `DiagnosticCode.code()`, `DiagnosticEvent.builder(String, DiagnosticLevel)`, `DiagnosticEventPublisher.publish(DiagnosticEvent)`.
- Produces allowed value types: `String`, `Number`, `Boolean`, and `Enum<?>`.
- Rejects forbidden keys containing `question`, `message`, `answer`, `prompt`, `payload`, `authorization`, `cookie`, `raw_ip`, `request_body`, or `response_body`.
- Produces `DroppedDiagnosticCounter.count()` for in-process loss inspection in tests and future
  metrics Adapters.

- [ ] **Step 1: Write failing contract tests**

```java
@Test
void rejectsForbiddenFieldNames() {
    assertThatThrownBy(() -> DiagnosticEvent.builder(
            "http.request.failed", DiagnosticLevel.ERROR)
            .field("visitor.question", "secret")
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("diagnostic field is forbidden: visitor.question");
}

@Test
void eventCopiesOnlyApprovedScalarValues() {
    DiagnosticEvent event = DiagnosticEvent.builder(
            "http.request.completed", DiagnosticLevel.INFO)
            .field("http.status_code", 200)
            .field("event.outcome", "success")
            .field("answer.degraded", false)
            .build();

    assertThat(event.getSchemaVersion()).isEqualTo(1);
    assertThat(event.getName()).isEqualTo("http.request.completed");
    assertThat(event.getFields()).containsEntry("http.status_code", 200);
    assertThat(event.getFields()).doesNotContainKey("message");
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=DiagnosticEventTest,Slf4jDiagnosticEventPublisherTest test
```

Expected: compilation fails because the observability types do not exist.

- [ ] **Step 3: Implement the immutable event model**

```java
public interface DiagnosticCode {
    String code();
}
```

```java
public enum DiagnosticLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}
```

`DiagnosticEvent` must:

- expose `getSchemaVersion()`, `getName()`, `getLevel()`, and `getFields()`;
- validate event names with `[a-z][a-z0-9]*(\\.[a-z][a-z0-9_]*)+`;
- copy fields into an unmodifiable `LinkedHashMap`;
- reject null values, collections, arrays, maps, arbitrary objects, control characters in keys, and forbidden key fragments;
- convert enum values to `name()` while building.

The publisher interface is:

```java
public interface DiagnosticEventPublisher {
    void publish(DiagnosticEvent event);
}
```

`Slf4jDiagnosticEventPublisher` must use the SLF4J fluent interface:

```java
LoggingEventBuilder builder = switch (event.getLevel()) {
    case DEBUG -> logger.atDebug();
    case INFO -> logger.atInfo();
    case WARN -> logger.atWarn();
    case ERROR -> logger.atError();
};
builder.addKeyValue("event.schema_version", event.getSchemaVersion());
builder.addKeyValue("event.name", event.getName());
event.getFields().forEach(builder::addKeyValue);
builder.log(event.getName());
```

It must catch `RuntimeException` from the logging Adapter and return without recursively logging.
On every caught publication failure it increments an injected `DroppedDiagnosticCounter` backed
by `AtomicLong`. It does not publish another event.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=DiagnosticEventTest,Slf4jDiagnosticEventPublisherTest test
```

Expected: both test classes pass.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/portfolio/agent/common/observability backend/src/test/java/com/portfolio/agent/common/observability
git commit -m "可观测性：建立类型化诊断事件接口"
```

### Task 2: Add request context, validated client IDs, and MDC cleanup

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/common/web/RequestContext.java`
- Create: `backend/src/main/java/com/portfolio/agent/common/web/RequestContextHolder.java`
- Create: `backend/src/main/java/com/portfolio/agent/common/web/RequestFailure.java`
- Create: `backend/src/main/java/com/portfolio/agent/common/web/RequestDiagnosticsFilter.java`
- Create: `backend/src/main/java/com/portfolio/agent/common/web/RequestDiagnosticsConfiguration.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/controller/ConversationAnswerController.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/ProductionConversationService.java`
- Test: `backend/src/test/java/com/portfolio/agent/common/web/RequestContextTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/common/web/RequestDiagnosticsFilterTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/service/ProductionConversationServiceTest.java`

**Interfaces:**
- Consumes: `DiagnosticEventPublisher.publish(DiagnosticEvent)`.
- Produces: `RequestContextHolder.current()`, `RequestContextHolder.requireCurrent()`, and `RequestContextHolder.enrichTurnId(String)`.
- Produces response headers `X-Request-Id` and `X-Trace-Id`.
- Produces explicit context propagation into the existing virtual-thread request executor.

- [ ] **Step 1: Write failing context and filter tests**

```java
@Test
void acceptsOnlyCanonicalClientUuids() {
    RequestContext context = RequestContext.create(
            "550e8400-e29b-41d4-a716-446655440000",
            "not-a-uuid");

    assertThat(context.getClientSessionId())
            .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    assertThat(context.getClientRequestId()).isNull();
}

@Test
void clearsMdcAndThreadLocalAfterRequest() throws Exception {
    filter.doFilter(request, response, chain);

    assertThat(RequestContextHolder.current()).isEmpty();
    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
}
```

The filter test must also assert:

- generated IDs are canonical UUIDs;
- both response headers equal the IDs recorded in the completion event;
- start event uses `http.route=UNRESOLVED`;
- completion event uses the value in
  `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`;
- an unmatched request uses `http.route=UNMATCHED`;
- request URI and query string never occur in captured events.

Add a `ProductionConversationServiceTest` case whose fake runtime captures
`RequestContextHolder.requireCurrent()` inside the executor thread and asserts its request ID,
client IDs, and turn ID equal the servlet-thread snapshot.

- [ ] **Step 2: Run the tests and verify RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=RequestContextTest,RequestDiagnosticsFilterTest test
```

Expected: compilation fails because request context classes do not exist.

- [ ] **Step 3: Implement request context ownership**

`RequestContext` is an explicit immutable class except for a nullable validated `turnId`.
It owns:

```java
private final String traceId;
private final String requestId;
private final String clientSessionId;
private final String clientRequestId;
private String turnId;
```

Create server IDs with `UUID.randomUUID().toString()`. Validate client values by parsing
with `UUID.fromString(value)` and requiring `parsed.toString().equals(value)`.

`RequestContextHolder` uses one `ThreadLocal<RequestContext>` and exposes:

```java
public static Optional<RequestContext> current()
public static RequestContext requireCurrent()
public static void set(RequestContext context)
public static void enrichTurnId(String turnId)
public static <T> T callWith(RequestContext context, Callable<T> action)
public static void clear()
```

`enrichTurnId` uses the same canonical UUID validation. It does not read the HTTP body.
`RequestContext.copy()` returns a distinct object with the same validated fields.

`callWith` installs the copied context and its MDC fields, invokes the action, and clears both
MDC and ThreadLocal in `finally`. This is required because the existing
`ProductionConversationService` executes the Agent on a virtual-thread executor.

- [ ] **Step 4: Implement the once-per-request filter**

At entry:

```java
RequestContext context = RequestContext.create(
        request.getHeader("X-Client-Session-Id"),
        request.getHeader("X-Client-Request-Id"));
RequestContextHolder.set(context);
putMdc(context);
response.setHeader("X-Request-Id", context.getRequestId());
response.setHeader("X-Trace-Id", context.getTraceId());
publishStarted(request.getMethod(), context);
```

Define request attributes:

```java
public static final String FAILURE_ATTRIBUTE =
        RequestDiagnosticsFilter.class.getName() + ".failure";
public static final String ERROR_CODE_ATTRIBUTE =
        RequestDiagnosticsFilter.class.getName() + ".errorCode";
```

`RequestFailure` contains only `errorCode`, `exceptionType`, and safe rendered frames.

In `finally`:

```java
String route = Optional.ofNullable(request.getAttribute(
        HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
        .map(Object::toString)
        .orElse("UNMATCHED");
RequestFailure failure = (RequestFailure) request.getAttribute(FAILURE_ATTRIBUTE);
if (failure != null || response.getStatus() >= 500) {
    publishFailed(request.getMethod(), route, response.getStatus(), startedAt, context, failure);
} else if (response.getStatus() >= 400) {
    publishRejected(request.getMethod(), route, response.getStatus(), startedAt, context,
            (String) request.getAttribute(ERROR_CODE_ATTRIBUTE));
} else {
    publishCompleted(request.getMethod(), route, response.getStatus(), startedAt, context);
}
MDC.clear();
RequestContextHolder.clear();
```

Register the filter at `Ordered.HIGHEST_PRECEDENCE + 20`. Do not log raw paths.

After `@Valid` succeeds, `ConversationAnswerController` calls:

```java
RequestContextHolder.enrichTurnId(request.getTurnId());
```

Before submitting to the executor, `ProductionConversationService` captures a copy:

```java
RequestContext context = RequestContextHolder.requireCurrent().copy();
Future<ConversationAnswerResult> future = executor.submit(
        () -> RequestContextHolder.callWith(context, () -> runtime.answer(request)));
```

- [ ] **Step 5: Run focused tests and verify GREEN**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=RequestContextTest,RequestDiagnosticsFilterTest,ProductionConversationServiceTest test
```

Expected: both test classes pass and MDC cleanup assertions succeed.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/portfolio/agent/common/web backend/src/main/java/com/portfolio/agent/answer/controller/ConversationAnswerController.java backend/src/main/java/com/portfolio/agent/answer/service/ProductionConversationService.java backend/src/test/java/com/portfolio/agent/common/web backend/src/test/java/com/portfolio/agent/answer/service/ProductionConversationServiceTest.java
git commit -m "可观测性：贯通请求上下文与关联标识"
```

### Task 3: Consolidate shared resource codes without breaking wire contracts

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/common/exception/PublicResourceErrorCode.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/exception/AnswerErrorCode.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/exception/AnswerProjectNotFoundException.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/exception/AnswerCaseNotFoundException.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/exception/ProjectNotFoundException.java`
- Modify: `backend/src/main/java/com/portfolio/agent/portfolio/exception/CaseNotFoundException.java`
- Delete: `backend/src/main/java/com/portfolio/agent/portfolio/exception/PortfolioErrorCode.java`
- Create: `backend/src/test/java/com/portfolio/agent/common/exception/ApiErrorCodeContractTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/portfolio/service/PortfolioServiceTest.java`

**Interfaces:**
- Produces: shared `PublicResourceErrorCode.PROJECT_NOT_FOUND` and
  `PublicResourceErrorCode.CASE_NOT_FOUND`.
- Preserves every existing JSON code string and HTTP status.

- [ ] **Step 1: Write the failing uniqueness and compatibility test**

```java
@Test
void publishedCodesRemainStableAndUniqueByOwner() {
    assertThat(PublicResourceErrorCode.PROJECT_NOT_FOUND.getCode())
            .isEqualTo("PROJECT_NOT_FOUND");
    assertThat(PublicResourceErrorCode.CASE_NOT_FOUND.getCode())
            .isEqualTo("CASE_NOT_FOUND");
    assertThat(AnswerErrorCode.ANSWER_REQUEST_TIMEOUT.getHttpStatus())
            .isEqualTo(503);
    assertThat(Arrays.stream(AnswerErrorCode.values())
            .map(AnswerErrorCode::getCode))
            .doesNotContain("PROJECT_NOT_FOUND", "CASE_NOT_FOUND");
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ApiErrorCodeContractTest test
```

Expected: compilation fails because `PublicResourceErrorCode` does not exist.

- [ ] **Step 3: Implement the shared owner and migrate exceptions**

Create:

```java
public enum PublicResourceErrorCode implements ErrorCode {
    PROJECT_NOT_FOUND("PROJECT_NOT_FOUND", "公开项目不存在", 404),
    CASE_NOT_FOUND("CASE_NOT_FOUND", "公开案例不存在", 404);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    PublicResourceErrorCode(String code, String defaultMessage, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public String getCode() { return code; }
    public String getDefaultMessage() { return defaultMessage; }
    public int getHttpStatus() { return httpStatus; }
}
```

Remove `PROJECT_NOT_FOUND` and `CASE_NOT_FOUND` from `AnswerErrorCode`. Change all four
resource exceptions to pass the shared enum to `ApplicationException`. Delete
`PortfolioErrorCode` and update the one service test to assert the shared enum.

- [ ] **Step 4: Run all affected controller and service tests**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ApiErrorCodeContractTest,AnswerControllerTest,PortfolioControllerTest,CaseControllerTest,PortfolioServiceTest test
```

Expected: all tests pass and existing JSON assertions remain unchanged.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/portfolio/agent/common/exception backend/src/main/java/com/portfolio/agent/answer/exception backend/src/main/java/com/portfolio/agent/portfolio/exception backend/src/test/java/com/portfolio/agent
git commit -m "异常契约：统一公开资源错误码所有权"
```

### Task 4: Make the global exception response use the active request context

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/common/observability/SafeExceptionRenderer.java`
- Modify: `backend/src/main/java/com/portfolio/agent/common/web/GlobalExceptionHandler.java`
- Modify: `backend/src/test/java/com/portfolio/agent/common/web/GlobalExceptionHandlerTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/common/observability/SafeExceptionRendererTest.java`

**Interfaces:**
- Consumes: active `RequestContext`, `DiagnosticEventPublisher`.
- Produces: one safe `RequestFailure` attribute consumed by the Filter as
  `http.request.failed`.
- Produces: stable error responses whose body and `X-Request-Id` header agree.

- [ ] **Step 1: Write failing exception correlation and privacy tests**

```java
@Test
void unexpectedErrorUsesActiveRequestIdWithoutLeakingMessage() throws Exception {
    mockMvc.perform(get("/test/unexpected")
                    .header("X-Client-Request-Id",
                            "550e8400-e29b-41d4-a716-446655440000"))
            .andExpect(status().isInternalServerError())
            .andExpect(header().exists("X-Request-Id"))
            .andExpect(result -> assertThat(
                    JsonPath.read(result.getResponse().getContentAsString(), "$.requestId"))
                    .isEqualTo(result.getResponse().getHeader("X-Request-Id")))
            .andExpect(content().string(not(containsString("SECRET_EXCEPTION_MESSAGE"))));
}
```

`SafeExceptionRendererTest` must verify that rendered frames contain exception class,
application class, method, file name, and line number, but not exception messages,
absolute paths, suppressed exception text, or cause messages.

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=GlobalExceptionHandlerTest,SafeExceptionRendererTest test
```

Expected: request IDs differ or renderer class is missing.

- [ ] **Step 3: Implement safe outer-seam mapping**

Change all response helpers to obtain:

```java
String requestId = RequestContextHolder.current()
        .map(RequestContext::getRequestId)
        .orElseGet(() -> UUID.randomUUID().toString());
```

For expected `ApplicationException` and validation errors, set
`RequestDiagnosticsFilter.ERROR_CODE_ATTRIBUTE` to the stable code. The Filter publishes the
single rejected event.

For unexpected exceptions, attach exactly one safe failure:

```java
request.setAttribute(
        RequestDiagnosticsFilter.FAILURE_ATTRIBUTE,
        new RequestFailure(
                CommonErrorCode.INTERNAL_ERROR.getCode(),
                exception.getClass().getName(),
                renderer.render(exception)));
```

`SafeExceptionRenderer.render(Throwable)` returns a single string containing at most
20 frames from packages beginning `com.portfolio.agent.`. It never calls
`Throwable.getMessage()`.

The Handler does not publish a failure event. `RequestDiagnosticsFilter` reads the attribute and
publishes exactly one `http.request.failed`, preventing duplicate 500 logs.

- [ ] **Step 4: Run focused and full backend tests**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=GlobalExceptionHandlerTest,SafeExceptionRendererTest test
mvn.cmd -f backend/pom.xml test
```

Expected: focused tests and full backend suite pass.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/portfolio/agent/common backend/src/test/java/com/portfolio/agent/common
git commit -m "异常契约：统一请求关联与安全异常日志"
```

### Task 5: Configure local text and production ECS output

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-local.yml`
- Create: `backend/src/main/resources/application-prod.yml`
- Create: `backend/src/test/java/com/portfolio/agent/common/observability/StructuredLoggingConfigurationTest.java`
- Modify: `scripts/privacy-check.ps1`
- Modify: `scripts/privacy-check.test.ps1`

**Interfaces:**
- Consumes: SLF4J key/value pairs and MDC.
- Produces: readable local logs and console-only ECS JSON in `prod`.

- [ ] **Step 1: Add failing configuration and scanner tests**

The Java test loads the `prod` profile and asserts:

```java
assertThat(environment.getProperty("logging.structured.format.console"))
        .isEqualTo("ecs");
assertThat(environment.getProperty("logging.file.name")).isNull();
assertThat(environment.getProperty("logging.file.path")).isNull();
assertThat(environment.getProperty("logging.level.com.portfolio.agent"))
        .isEqualTo("INFO");
```

Add privacy-check fixtures that must fail when source contains:

```java
LOGGER.info("question={}", request.getQuestion());
LOGGER.warn("payload={}", providerResponse);
```

and:

```ts
console.error('messages', session.messages)
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=StructuredLoggingConfigurationTest test
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.test.ps1
```

Expected: missing profile assertions or new unsafe fixtures fail.

- [ ] **Step 3: Add exact profile configuration**

`application-local.yml`:

```yaml
logging:
  level:
    com.portfolio.agent: DEBUG
    org.springframework.web: INFO
```

`application-prod.yml`:

```yaml
logging:
  structured:
    format:
      console: ecs
    json:
      add:
        deployment.environment: production
  level:
    root: INFO
    com.portfolio.agent: INFO
    org.springframework.web: WARN
    org.springframework.web.client: WARN
```

Do not configure `logging.file.name` or `logging.file.path`.

Extend the scanner with source rules for logger/console calls that mention the
forbidden field names. Keep the existing credential and Provider payload rules.

- [ ] **Step 4: Run configuration, scanner, and backend verification**

Run:

```powershell
mvn.cmd -f backend/pom.xml -Dtest=StructuredLoggingConfigurationTest test
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.test.ps1
mvn.cmd -f backend/pom.xml test
```

Expected: all commands pass.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/resources backend/src/test/java/com/portfolio/agent/common/observability scripts/privacy-check.ps1 scripts/privacy-check.test.ps1
git commit -m "可观测性：配置本地文本与生产结构化日志"
```

### Task 6: Verify the core as an independently releasable slice

**Files:**
- Modify: `README.md`
- Modify: `docs/08-当前实现状态.md`

**Interfaces:**
- Produces documented local and production run commands and request-ID troubleshooting steps.

- [ ] **Step 1: Add the exact operator documentation**

Document:

```powershell
mvn.cmd -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
$env:SPRING_PROFILES_ACTIVE='prod'
java -jar backend/target/portfolio-agent.jar
```

Include one troubleshooting sequence:

```text
1. Copy X-Request-Id from the browser Network response.
2. Query request.id=<value>.
3. Find http.request.started and http.request.completed/http.request.failed.
4. Use trace.id to inspect diagnostic events emitted inside the request.
```

- [ ] **Step 2: Run the complete core gate**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/privacy-check.test.ps1
mvn.cmd -f backend/pom.xml test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
mvn.cmd -f backend/pom.xml package
```

Expected: every command exits 0.

- [ ] **Step 3: Commit**

```powershell
git add README.md docs/08-当前实现状态.md
git commit -m "文档：补充结构化日志运行与排障说明"
```
