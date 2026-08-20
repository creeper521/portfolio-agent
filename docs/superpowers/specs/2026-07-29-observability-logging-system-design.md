# 前后端可观测日志系统设计
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

> **状态：** 已确认，待实施  
> **日期：** 2026-07-29  
> **基线：** `master@d8c162c`  
> **范围：** 本地详细诊断、生产结构化日志、前后端请求关联、稳定错误码、受限前端错误上报  
> **明确不包含：** OpenTelemetry SDK、集中日志平台、数据库审计、用户行为分析、动态日志管理接口

## 1. 背景

当前项目已经存在部分可观测性基础：

- v1 回答链路生成服务端 `requestId`；
- `AnswerDecision` 已包含回答结果、生成方式、验证状态和耗时桶；
- `AnswerDecisionPublisher` 提供了领域事件 seam；
- 模型、检索、Tool 和输出校验均有封闭的结果或失败枚举；
- HTTP 错误响应已经包含 `requestId`、错误码、公开消息和时间。

但 master 上的运行时日志几乎为空：

- 后端只有全局异常处理器的一条意外错误日志；
- 该日志没有记录异常堆栈；
- 正常请求、异常响应和 Agent 回答使用不同来源的 `requestId`；
- v2 回答链路没有服务端请求 ID 或决策事件；
- 检索、Tool、Provider 和决策发布中的异常经常被静默降级；
- 前端把超时、断网、HTTP 错误和非法响应压缩成相同提示；
- 前端不能读取或展示服务端错误码与 `requestId`；
- 内容包、本地模型和安全配置缺少启动生命周期日志；
- 没有结构化格式、字段规范、级别规范或日志隐私门禁。

本设计建立一套同时服务本地开发和生产排障的日志系统。它记录完整的执行事件与状态，
但不记录访客内容。

## 2. 目标

1. 本地 DEBUG 模式可以查看一次 Agent 请求经过的完整安全阶段链路；
2. 生产环境中每个请求都有开始和结束记录；
3. 失败、超时、拒绝和降级具有稳定的阶段事件与原因码；
4. 浏览器错误可以与后端请求、Agent 轮次和内部阶段关联；
5. 日志输出默认是标准输出，格式和目标可替换；
6. 第一版字段兼容 OpenTelemetry 和 Elastic Common Schema 语义；
7. 日志与遥测失败不能改变访客响应；
8. 测试和发布扫描能够阻止敏感数据进入日志。

## 3. 不可违反的隐私规则

以下数据永久禁止进入本地和生产日志：

- 访客问题、消息、完整回答和会话内容；
- Prompt、Provider 请求、Provider 响应和原始异常正文；
- 检索词项、向量、分数、候选文本和检索上下文；
- 原始 IP、完整来源哈希；
- Authorization、Cookie、API Key 和其他凭据；
- 全部请求头、请求体或响应体；
- 本地绝对路径和外部 Provider URL。

本设计扩展现有隐私边界，不放宽访客问题、回答和 Provider 数据的限制。

## 4. 已确认决策

- 同时支持本地开发诊断和生产故障排查；
- 输出目标保持可替换，第一版默认标准输出；
- 前端只上报错误和关键性能事件；
- 使用仅存在于当前标签页内存的随机 `clientSessionId`；
- 第一版不引入 OpenTelemetry SDK，但字段与事件语义保持兼容；
- 生产正常请求只记录开始、领域结果和结束；
- 失败、超时和降级补充对应阶段事件；
- 前端根据稳定错误码选择重试、倒计时、修正输入或安全导航；
- 前端诊断接口是现有“公共接口只读”规则的唯一受限例外。

## 5. 总体架构

系统由四个深模块组成。

### 5.1 `RequestContext`

`RequestContext` 是请求关联的唯一 seam，负责：

- 创建服务端 `traceId` 和 `requestId`；
- 校验并接收客户端关联 ID；
- 将上下文放入 MDC；
- 向响应写入关联头；
- 请求结束时清理 MDC。

### 5.2 领域决策发布接口

业务模块通过类型化领域接口发布结果：

- 保留现有 `AnswerDecisionPublisher`；
- 为 v2 增加 `ConversationDecisionPublisher`；
- 检索、Tool、Provider 和校验使用各自的类型化事件。

领域代码不依赖 SLF4J 格式、JSON 格式或具体日志平台。

### 5.3 `DiagnosticEventPublisher`

领域事件由 Adapter 转换成统一 `DiagnosticEvent`，再交给
`DiagnosticEventPublisher`。该接口只接收封闭事件类型，不接受任意
`Map<String, Object>`。

第一版提供：

- 本地可读文本 Adapter；
- 生产 ECS JSON Adapter；
- 测试捕获 Adapter。

未来可以增加 OpenTelemetry Adapter，而不修改调用方。

### 5.4 `FrontendDiagnostics`

前端通过统一接口记录安全事件：

```ts
interface FrontendDiagnostics {
  debug(event: SafeFrontendEvent): void
  report(event: ReportableFrontendEvent): void
}
```

本地输出安全控制台事件；生产只上传错误和关键性能事件。

## 6. 请求关联标识

| 字段 | 生成方 | 生命周期 | 用途 |
| --- | --- | --- | --- |
| `traceId` | 后端 | 一次 HTTP 请求 | 兼容未来 Trace |
| `requestId` | 后端 | 一次 HTTP 请求 | 服务端日志与用户报错查询 |
| `clientSessionId` | 前端 | 当前标签页 | 关联同一页面的连续故障 |
| `clientRequestId` | 前端 | 一次网络请求 | 关联未收到响应的请求 |
| `turnId` | 前端 Agent 领域 | 一次对话轮次 | 关联回答 UI 与 Agent 执行 |

### 6.1 前端请求头

```text
X-Client-Session-Id
X-Client-Request-Id
```

Agent 请求继续在请求体中携带 `turnId`。

### 6.2 后端响应头

```text
X-Request-Id
X-Trace-Id
```

错误正文中的 `requestId` 必须与 `X-Request-Id` 相同。

### 6.3 MDC 字段

```text
trace.id
request.id
client.session.id
client.request.id
turn.id
http.method
http.route
```

客户端 ID 只接受规范 UUID。非法、超长或含控制字符的值直接丢弃。客户端不能指定
服务端 `requestId` 或 `traceId`。第一版不接受外部 `traceparent`。

MDC 必须在 `finally` 中清理，防止 Servlet 线程复用导致上下文串线。

`turnId` 在请求体通过 DTO 校验后才补充到当前 `RequestContext`。HTTP Filter 不解析或记录
请求体。

当前 v2 生产链路通过虚拟线程执行回答。提交任务前必须复制当前 `RequestContext`，在虚拟
线程中显式安装对应 ThreadLocal 与 MDC，并在任务结束后清理；不能假设 Servlet 线程的
MDC 会自动传播。

## 7. 诊断事件契约

统一事件信封包含：

```text
event.schema_version
timestamp
service.name
deployment.environment
event.name
event.outcome
log.level
trace.id
request.id
client.session.id
client.request.id
turn.id
http.method
http.route
http.status_code
duration.ms
duration.bucket
error.code
failure.code
```

事件只能添加其类型批准的可选字段。

### 7.1 稳定事件名

```text
application.started
application.startup.failed
content.bundle.loaded
embedding.model.loaded
embedding.model.failed

http.request.started
http.request.completed
http.request.rejected
http.request.failed

agent.route.decided
retrieval.completed
retrieval.degraded
tool.plan.completed
tool.call.completed
provider.call.completed
provider.call.failed
answer.validation.completed
answer.fallback.selected
agent.request.completed

frontend.content.load.failed
frontend.agent.request.failed
frontend.agent.request.slow
frontend.agent.request.cancelled
frontend.response.invalid
frontend.runtime.failed
```

### 7.2 日志级别

| 情况 | 级别 |
| --- | --- |
| 请求开始、正常结束 | `INFO` |
| 本地阶段成功事件 | `DEBUG` |
| 限流、超时、可预期拒绝和降级 | `WARN` |
| 未预期异常、启动失败和发布包损坏 | `ERROR` |
| 用户主动取消 | `INFO` |
| 参数校验失败和普通 404 | `INFO` |

同一未预期异常只在最外层记录一次堆栈。

## 8. 字段分级

### 8.1 生产允许

- 随机关联 ID；
- HTTP 方法、路由模板和状态码；
- 稳定错误码和失败码；
- 封闭枚举结果；
- 耗时、数量和预算；
- content version 和 schema version；
- 异常类型。

### 8.2 仅本地 DEBUG

- 已公开项目或案例 slug；
- Tool 类型；
- 检索命中数量；
- 校验失败规则；
- 非敏感配置开关与预算值。

### 8.3 永久禁止

永久禁止字段遵循第 3 节，不存在通过 DEBUG 级别绕过隐私规则的例外。

## 9. 错误码与失败码

### 9.1 `ApiErrorCode`

`ApiErrorCode` 只描述会形成非成功 HTTP 响应、且前端需要恢复动作的错误。

```text
VALIDATION_ERROR                 400
NOT_FOUND                        404
METHOD_NOT_ALLOWED               405
UNSUPPORTED_MEDIA_TYPE           415

PROJECT_NOT_FOUND                404
CASE_NOT_FOUND                   404

INVALID_ANSWER_CONTEXT           400
ANSWER_REQUEST_INVALID           400
ANSWER_RATE_LIMITED              429
ANSWER_CONCURRENCY_LIMITED       429
ANSWER_REQUEST_TIMEOUT           503
INTERNAL_ERROR                   500
```

上述无领域前缀的 wire code 已经由当前 API、测试和前端页面使用，实施时必须保持兼容。
公开项目和案例错误在 Java 中统一到一个共享的错误码所有者，移除 answer 与 portfolio
模块中的重复定义，但 JSON 中继续返回 `PROJECT_NOT_FOUND` 和 `CASE_NOT_FOUND`。
未来新增错误码必须使用领域前缀。

错误码发布后视为 HTTP 契约，不随 Java 类型名称变化。

### 9.2 `FailureCode`

内部失败不携带 HTTP 状态，不直接决定用户响应。各模块拥有自己的枚举，并实现小型
`DiagnosticCode` 接口：

```text
ProviderFailureCode
AnswerValidationFailureCode
RetrievalFailureCode
ToolFailureCode
ContentFailureCode
```

示例：

```text
PROVIDER_TIMEOUT
PROVIDER_INVALID_RESPONSE
ANSWER_INVALID_REFERENCE
RETRIEVAL_INFERENCE_FAILED
TOOL_RESULT_INVALID
CONTENT_CHECKSUM_MISMATCH
```

### 9.3 Exception 使用规则

```text
立即结束 HTTP 请求
→ ApplicationException(ApiErrorCode)

可以安全降级
→ Result/Outcome + FailureCode

启动配置或发布包非法
→ 启动异常并阻止应用启动

未预期代码缺陷
→ 原始 RuntimeException 交给最外层
```

不为每个错误码建立只有构造器的浅异常模块。只有需要单独捕获、类型化上下文或特殊行为时，
才增加专用异常类型。

### 9.4 前端恢复动作

```text
NONE
RETRY
RETRY_AFTER
CORRECT_INPUT
NAVIGATE_BACK
```

前端按稳定错误码映射动作，未知错误码进入安全默认动作。

## 10. 后端埋点位置

### 10.1 HTTP seam

新增 `RequestDiagnosticsFilter`：

- 进入时建立上下文并发布 `http.request.started`；
- 退出时发布 `http.request.completed` 或 `http.request.failed`；
- 只记录路由模板，不记录实际 URL 或 query string；
- 写响应关联头并清理 MDC。

Filter 进入时 Spring MVC 尚未完成 Handler 匹配，因此开始事件中的 `http.route` 固定为
`UNRESOLVED`，不能退回记录真实请求路径。请求结束时从
`HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE` 读取路由模板；无法解析时使用
`UNMATCHED`。

异常处理器只把稳定错误码或经过安全渲染的失败信息附加到当前 request attribute，不直接
输出第二条异常日志。Filter 在 `finally` 中统一选择 `http.request.completed`、
`http.request.rejected` 或 `http.request.failed`，保证一次 HTTP 请求只有一个结束事件，
未预期异常堆栈只出现一次。

Controller、Mapper 和普通领域对象不重复打印入口、出口日志。

### 10.2 Agent seam

v1 通过现有 `AnswerDecisionPublisher` 输出回答结果。v2 增加
`ConversationDecisionPublisher`。完成事件至少包含：

```text
resolution
generationMode
degraded
durationBucket
contentVersion
answerSource
```

### 10.3 意图路由

`agent.route.decided` 只记录：

```text
intent
answerScope
routeSource
durationBucket
```

不记录分类输入、模型输出或问题文本。

### 10.4 检索

`retrieval.completed` 可以记录：

```text
requestedMode
actualMode
decision
keywordHitCount
vectorHitCount
fusedCandidateCount
acceptedChunkCount
durationBucket
```

向量能力失败并回到关键词检索时发布 `retrieval.degraded`。不记录查询、词项、分数、
文本、候选 ID 或向量。

### 10.5 Tool

本地 DEBUG 可以记录规划轮次和单次调用。生产正常情况只记录聚合结果。允许字段：

```text
round
toolKind
resultStatus
claimCount
evidenceCount
durationBucket
failureCode
```

### 10.6 Provider

Provider Adapter 的公共执行方法发布：

```text
provider.call.completed
provider.call.failed
```

允许字段：

```text
providerOperation
result
failureCode
durationBucket
responsePresent
```

`providerOperation` 的封闭值为 `CLASSIFY`、`PLAN_TOOLS`、`GENERATE`、
`REVIEW`、`SUMMARIZE`、`SUGGEST` 和 `EXPRESS`。不记录 Provider 名称、URL、输入、
输出、token 或原始异常 message。

### 10.7 校验与 fallback

输出拒绝发布 `answer.validation.completed`，降级发布
`answer.fallback.selected`。事件必须能够回答降级触发原因和最终 fallback 结果。

### 10.8 启动生命周期

记录公开内容包、本地 Embedding 模型和安全配置的启动结果。只输出 schema、版本、
数量、维度、安全开关和非敏感预算，不输出文件路径、密钥或 Provider URL。

## 11. 生产日志数量

正常 Agent 请求：

```text
INFO http.request.started
INFO agent.request.completed
INFO http.request.completed
```

Provider 超时并成功降级：

```text
INFO http.request.started
WARN provider.call.failed
WARN answer.fallback.selected
INFO agent.request.completed
INFO http.request.completed
```

未预期异常：

```text
INFO  http.request.started
ERROR http.request.failed
```

阶段成功事件在生产 INFO 模式下不输出。

## 12. 前端诊断上报

### 12.1 受限写入接口

新增：

```http
POST /api/v1/client-diagnostics
```

这是现有“公共接口只读”规则的唯一例外。它只接受诊断事件，不修改公开内容、不写数据库、
不创建访客档案。

服务端返回 `202 Accepted`。每批最多 10 条，请求体上限 16 KB，拒绝未知字段，并对接口
单独限流。

客户端报告的服务端请求 ID 必须保存为 `client.reported_request_id`，不能覆盖当前请求
MDC，也不能作为安全审计事实。

### 12.2 允许上报的事件

```text
frontend.content.load.failed
frontend.agent.request.failed
frontend.agent.request.slow
frontend.agent.request.cancelled
frontend.response.invalid
frontend.runtime.failed
```

正常点击、浏览、问题提交、回答展示和证据查看不上传。

### 12.3 JavaScript 异常

生产环境不上传 `Error.message` 或 `Error.stack`。允许上传：

```text
error.kind
error.fingerprint
```

`error.fingerprint` 是浏览器对规范化堆栈计算的 SHA-256，只用于聚合同类错误。
规范化过程只保留本站脚本模块名、函数名和行列号，先删除协议、主机、query string、
fragment 和动态文本，再计算摘要。无法安全规范化时不生成 fingerprint。

### 12.4 故障隔离

诊断 Transport 独立于普通请求模块：

- 内存队列最多 20 条；
- 单批最多 10 条；
- 单次 best-effort `fetch`；
- 2 秒超时；
- 不自动重试；
- 页面关闭时可以使用 `keepalive`；
- 不写 LocalStorage 或 IndexedDB；
- 上报失败静默丢弃；
- 不上报“诊断上报失败”。

## 13. 前端错误模型

`PortfolioApiError` 扩展为：

```ts
class PortfolioApiError extends Error {
  code: ApiErrorCode | 'UNKNOWN'
  status?: number
  requestId?: string
  retryAfterSeconds?: number
  action: ErrorAction
  kind: 'HTTP' | 'TIMEOUT' | 'NETWORK' | 'INVALID_RESPONSE'
}
```

统一请求模块负责生成客户端 ID、解析错误响应、读取响应关联头、校验响应结构和发布安全
诊断事件。页面模块只根据 `action` 决定恢复 UI。

## 14. 日志格式与配置

项目使用 Spring Boot 3.5 原生结构化日志能力。

能力依据：
<https://docs.spring.io/spring-boot/reference/features/logging.html#features.logging.structured>

本地环境：

- `com.portfolio.agent=DEBUG`；
- 使用紧凑可读文本；
- 输出完整安全阶段事件；
- 前端诊断上传默认关闭。

生产环境：

- 使用 ECS JSON；
- `root=INFO`、应用包 `INFO`、Spring Web `WARN`；
- 只写标准输出；
- 前端诊断上传需要明确开启。

第一版不提供 Actuator 日志管理端点，不允许浏览器或公共接口动态开启 DEBUG。

## 15. 脱敏防线

### 15.1 类型限制

事件是类型化不可变对象，禁止任意字段 Map。

### 15.2 入口验证

前端诊断 DTO 限制事件名、枚举、UUID、数量和请求体大小，并拒绝未知字段。

### 15.3 Adapter 白名单

日志 Adapter 只读取明确批准字段。事件类新增字段不会自动进入生产日志。

### 15.4 安全异常渲染

可预期失败只记录 FailureCode。未预期异常只记录异常类型和经过处理的应用堆栈帧。
生产环境默认不记录异常 message。

### 15.5 发布扫描

扩展 `privacy-check.ps1`，扫描 Java、TypeScript、前端 dist、最终 JAR 和日志调用，
阻止问题、消息、回答、Prompt、响应正文和凭据进入日志。

## 16. 测试设计

### 16.1 RequestContext

- ID 生成与校验；
- 响应头与错误正文一致；
- MDC 请求后清理；
- 同一线程连续请求不串上下文；
- 非法客户端 ID 不进入日志。

### 16.2 事件契约

- 每类事件只序列化白名单字段；
- ApiErrorCode 全局唯一；
- 前端错误码均有恢复动作；
- FailureCode 不携带 HTTP 状态；
- Adapter 故障不改变业务结果；
- 同一异常只记录一次堆栈。

### 16.3 隐私测试

将访客问题、回答、会话、Provider payload、Authorization、API Key、原始 IP、
检索词项和本地路径设置为唯一哨兵值。执行完整链路后断言所有日志均不包含哨兵。

异常 message 也使用秘密哨兵，生产日志不得输出。

### 16.4 Agent 链路

覆盖正常回答、Provider 超时、非法模型输出、检索降级、Tool 越权、输出校验失败、
fallback 成功和未预期异常。验证事件顺序、关联 ID、最终状态和无重复日志。

### 16.5 HTTP 集成

MockMvc 验证响应头、错误正文、路由模板、429 `Retry-After`、安全 500 和 ECS JSON。

### 16.6 前端

Vitest 验证 ID 生命周期、错误分类、未知码降级、Transport 隔离、队列上限、上报 DTO
白名单和无浏览器持久化。

### 16.7 浏览器

Playwright 覆盖 429、504、404、Provider 降级、浏览器超时、客户端请求关联和页面关闭
不持久化诊断数据。

### 16.8 发布门禁

```text
backend tests
frontend tests
frontend build
browser E2E
structured-log contract test
privacy log scan
frontend dist scan
final JAR scan
```

日志隐私测试失败必须阻止发布。

## 17. 分阶段实施

### 第一阶段：公共基础

- 整理 ApiErrorCode 与 FailureCode；
- 建立 RequestContext；
- 增加请求过滤器；
- 启用本地文本和生产 ECS；
- 修复全局异常关联。

### 第二阶段：后端 Agent 事件

- 接入 v1 AnswerDecisionPublisher；
- 为 v2 增加类型化决策事件；
- 接入检索、Tool、Provider、输出校验和 fallback；
- 增加启动生命周期事件。

### 第三阶段：前端错误恢复

- 扩展统一请求模块；
- 引入错误码动作映射；
- 传播客户端 ID；
- 区分超时、网络、HTTP、非法响应和取消。

### 第四阶段：受限前端诊断

- 增加独立诊断 Transport；
- 增加受限写入接口；
- 配置批量、限流和请求体上限；
- 更新公共接口只读约束。

### 第五阶段：隐私与发布门禁

- 完成日志捕获测试；
- 扩展 privacy-check；
- 验证 dist、JAR 和生产 JSON；
- 编写日志查询与故障排查手册。

## 18. 第一版明确不做

- 不引入 OpenTelemetry SDK；
- 不部署 ELK、Loki、Jaeger 或云日志平台；
- 不写数据库或审计表；
- 不持久化前端诊断队列；
- 不记录正常用户行为；
- 不提供动态日志级别管理接口；
- 不记录请求体、响应体、Prompt 或检索内容；
- 不建立业务监控大盘和告警规则；
- 不把本模块扩张成通用企业日志框架。

## 19. 完成标准

- 本地可以按一次请求查看完整安全阶段链路；
- 生产正常请求具有开始、领域结果和结束日志；
- 所有失败与降级具有稳定 FailureCode；
- 所有非成功 HTTP 响应具有稳定 ApiErrorCode；
- 前端能够按错误码提供正确恢复动作；
- 前端错误能够通过客户端和服务端 ID 与后端链路关联；
- 日志输出默认标准输出 ECS JSON，并可通过 Adapter 替换；
- Provider、检索、Tool 和校验失败不会被静默吞掉；
- 日志或遥测故障不改变业务结果；
- 访客内容、凭据、原始 IP、Provider payload 和检索内容不进入日志；
- 隐私测试、结构化契约测试、dist 扫描和 JAR 扫描全部通过。
