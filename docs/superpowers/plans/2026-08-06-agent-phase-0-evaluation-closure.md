# Agent 阶段 0：基线与评测收口 Implementation Plan
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 0A–0D，使普通工程门禁和离线 Eval 为 `PASS`、Provider 执行链通过 Mock 验证、真实 Provider 未授权时诚实返回 `INCOMPLETE`，从而满足阶段 1 启动门槛。

**Architecture:** 保留现有统一 Case Schema、Loader、Coverage、Planner、Oracle 隔离和检索执行器，在 `com.portfolio.agent.evaluation` 内补齐 `application -> execution -> grading -> reporting -> cli` 单向控制流。唯一高层入口为 `EvalHarness.run(EvalSuite, EvalRunConfig) -> EvalRunReport`；Executor 只接收无 Oracle 的 `EvalExecutionInput`，Grader 才能同时看到 Observation 和 Oracle。

**Tech Stack:** Java 21、Spring Boot 3、Jackson、Maven、JUnit 5、AssertJ、Vue 3、TypeScript、Vitest、Playwright、PowerShell。

## Global Constraints

- 严格按 `0A -> 0B -> 0C -> 0D` 执行；0A 未全绿时不得用 Eval 兼容分支掩盖失败。
- 每个功能或缺陷修复执行 RED、GREEN、REFACTOR；遇到非预期失败先使用 `systematic-debugging`。
- Java 生产和测试代码禁止 `var`、`record` 和 Lombok；值对象使用显式不可变类。
- 运行时代码只能读取 `backend/src/main/resources/public-data/` 中已审核公开快照；不得记录问题原文、完整回答、私有路径、凭据、原始异常或未公开 Evidence。
- Evaluation 包不得被 Spring 自动扫描；只能由显式 CLI 启动，依赖方向固定为 `evaluation -> production ports/services/domain`。
- 不修改 `PortfolioIntelligence`、`/api/v2/answers`、Provider Registry、Public Bundle 和前端生产契约。
- 不实现阶段 1 的 `PortfolioAnswerPlan`、Composer、v2 章节字段和前端章节渲染，也不实现 `TurnRouter`、工具规划、`MODEL_GROUNDED` 改造、PostgreSQL/ONNX 生产容量验收。
- 真实 Provider 只能在显式传入 `--authorize-real-provider` 且离线同身份结果为 `PASS` 时运行；缺少授权或未运行时为 `INCOMPLETE`/退出码 `3`，不能写成发布 `PASS`。
- 阶段基线保存到 `governance/portfolio-governance/evaluation/baselines/phase-0-answer-composition.json`，不得写入 `release-baselines`。
- 保留用户现有 Git 改动；未经显式授权不得 restore、stage、commit 或 push。计划中的每个“审阅点”只检查 diff，不执行 Git 写操作。

---

## File Structure

### Existing files to modify

| Path | Responsibility |
|---|---|
| `frontend/e2e/portfolio.spec.ts` | 修复后保留的桌面/移动验收断言，不删除场景 |
| `frontend/e2e/support/publicApiMocks.ts` | E2E 公开 API Mock 与请求隔离 |
| `frontend/src/shared/diagnostics/diagnosticTransport.ts` | 每个失败只上传一次、上传失败不重试 |
| `frontend/src/features/portfolio/api/portfolioApi.ts` | 请求错误与 client/server correlation 的唯一诊断发布点 |
| `frontend/src/features/agent/model/mapAnswerResponse.ts` | v2 响应到当前前端语义字段的兼容映射 |
| `frontend/src/features/agent/model/answerLabels.ts` | `constructionMode/intentSource/evidenceState` 标签语义 |
| `backend/src/main/java/com/portfolio/agent/evaluation/domain/EvalLayer.java` | 新增 `PROVIDER` 层 |
| `backend/src/main/java/com/portfolio/agent/evaluation/domain/EvalObservation.java` | 增加脱敏回答结构与 Provider 结果字段 |
| `backend/src/main/java/com/portfolio/agent/evaluation/execution/EvalRunContext.java` | 执行器可见的脱敏运行身份 |
| `backend/src/main/java/com/portfolio/agent/evaluation/execution/EvalRunPlanner.java` | Provider 场景和三次 Trial 计划 |
| `governance/portfolio-governance/schemas/eval-suite.schema.json` | 接受 `PROVIDER` 层并约束评分器类型 |
| `governance/portfolio-governance/evaluation/manifest.v1.json` | 追踪正式阶段 0 数据集和策略 |
| `backend/pom.xml` | 仅在现有依赖不足时补充测试/CLI 所需依赖；不得拆 Maven 模块 |
| `docs/00-文档状态索引.md`、`docs/08-当前实现状态.md`、`docs/11-项目演进日志.md`、`docs/13-Agent对话体验与智能编排改造路线图.md` | 完成后同步真实状态 |

### New production files

```text
backend/src/main/java/com/portfolio/agent/evaluation/
├─ application/
│  ├─ EvalHarness.java
│  ├─ EvalRunConfig.java
│  └─ EvalRunException.java
├─ domain/
│  ├─ EvalAnswerShape.java
│  ├─ EvalPolicy.java
│  ├─ EvalProviderAuthorization.java
│  ├─ EvalRunIdentity.java
│  └─ EvalVerdict.java
├─ dataset/
│  ├─ EvalManifestLoader.java
│  └─ EvalPolicyLoader.java
├─ execution/
│  ├─ EvalExecutionEngine.java
│  ├─ BundleContractEvalExecutor.java
│  ├─ SubjectInternalRetrievalExecutor.java
│  ├─ IntelligenceEvalExecutor.java
│  ├─ HttpEvalExecutor.java
│  ├─ EvalAnswerClient.java
│  ├─ EvalHttpRequest.java
│  ├─ EvalHttpResult.java
│  ├─ JdkEvalAnswerClient.java
│  └─ ProviderEvalExecutor.java
├─ grading/
│  ├─ EvalGrade.java
│  ├─ EvalGrader.java
│  ├─ DeterministicEvalGrader.java
│  └─ EvalReasonCode.java
├─ reporting/
│  ├─ EvalBaseline.java
│  ├─ EvalBaselineComparator.java
│  ├─ EvalComparison.java
│  ├─ EvalGateResult.java
│  ├─ EvalMetricAggregator.java
│  ├─ EvalMetrics.java
│  ├─ EvalReportJsonWriter.java
│  ├─ EvalReportMarkdownRenderer.java
│  ├─ EvalRunReport.java
│  └─ EvalVerdictPolicy.java
└─ cli/
   ├─ EvalCli.java
   ├─ EvalCliArguments.java
   └─ EvalCliBootstrap.java
```

### New tests, governance data, scripts, and reports

```text
backend/src/test/java/com/portfolio/agent/evaluation/{application,dataset,execution,grading,reporting,cli}/
backend/src/test/resources/evaluation/{policy,baseline,http,provider}/
governance/portfolio-governance/evaluation/
├─ policies/phase-0.v1.json
├─ cases/calibration/core.v1.json
├─ cases/holdout/routing.v1.json
├─ cases/holdout/answer.v1.json
├─ cases/regression/legacy.v1.json
└─ baselines/phase-0-answer-composition.json
scripts/run-eval.ps1
scripts/run-eval.test.ps1
scripts/run-eval-offline.ps1
scripts/run-eval-offline.test.ps1
docs/reports/phase-0-e2e-triage-2026-08-06.md
docs/reports/phase-0-engineering-baseline-2026-08-06.md
```

---

## 0A：普通工程基线收口

### Task 1: 冻结并定性当前 10 个逻辑 E2E 失败

**Files:**
- Create: `docs/reports/phase-0-e2e-triage-2026-08-06.md`
- Inspect: `frontend/e2e/portfolio.spec.ts`
- Inspect: `frontend/test-results/**/error-context.md`

**Interfaces:**
- Consumes: 当前 Playwright 双项目配置 `chromium`、`mobile-chromium`。
- Produces: 每个逻辑失败唯一归类为 `PRODUCT_REGRESSION`、`ASSERTION_DRIFT` 或 `TEST_ISOLATION`，并记录失败证据与预期修复面。

- [ ] **Step 1: 复现 RED 基线**

Run:

```powershell
npm.cmd --prefix frontend run test:e2e -- --reporter=line
```

Expected: 62 项中 42 项通过、20 项失败；失败在桌面/移动各出现一次，归并为以下 10 个逻辑场景：

```text
429 renders a countdown and uploads only a closed correlated diagnostic
503 timeout offers retry and preserves the returned request correlation
PROJECT_NOT_FOUND offers safe navigation without exposing the server body
caller cancellation appends no failure answer
one slow answer emits one diagnostic and an upload failure stays invisible without retry
a pre-response network failure reports only client correlation
home preserves the four-layer experience and hands a role question to Agent
Agent renders unsupported and rejected dimensions without a verified label
Agent distinguishes retrieval provenance from verification
Agent renders MODEL and whole-answer FALLBACK as distinct generation modes
```

- [ ] **Step 2: 为每个失败保存最小证据**

在报告中为每个场景写入：测试名、两个项目的实际错误摘要、相关 trace 路径、权威契约、分类、允许修改的文件。诊断六题的权威契约是“一个失败至多一个封闭诊断、取消不产生失败回答、上传失败不重试、响应前只有 client correlation、响应后可同时有 server correlation”；回答标签三题的权威字段是 `constructionMode`、`intentSource`、`evidenceState`；首页题的权威契约是四层体验和内存内 handoff。

- [ ] **Step 3: 确认没有用例被静默跳过**

Run:

```powershell
rg -n "test\.(skip|fixme)|describe\.(skip|fixme)|test\.only|describe\.only" frontend/e2e
```

Expected: 无匹配。

- [ ] **Step 4: 审阅点**

仅审阅报告和 traces；不得修改断言、产品代码或 Playwright 重试次数。

### Task 2: 收口浏览器诊断重复与隔离问题

**Files:**
- Modify: `frontend/src/shared/diagnostics/diagnosticTransport.ts`
- Modify: `frontend/src/features/portfolio/api/portfolioApi.ts`
- Modify if evidence points here: `frontend/src/features/agent/components/AgentWorkspace.vue`
- Test: `frontend/src/shared/diagnostics/diagnosticTransport.test.ts`
- Test: `frontend/src/features/portfolio/api/portfolioApi.test.ts`
- Test: `frontend/src/features/agent/components/AgentWorkspace.test.ts`
- Test: `frontend/e2e/portfolio.spec.ts:65`

**Interfaces:**
- Consumes: `frontendDiagnostics.report(event)` 和 `DiagnosticTransport.enqueue(event)`。
- Produces: 一个请求失败只有一个责任方发布诊断；`DiagnosticTransport` 对同一事件最多尝试一次且不持久化。

- [ ] **Step 1: 写诊断单元 RED 用例**

在 `portfolioApi.test.ts` 增加参数化用例，分别模拟 `429`、`503`、业务 404、AbortError、响应前网络失败，断言每次 `frontendDiagnostics.report` 调用数为 `1/1/1/0/1`，并断言 payload 不含 URL、request/response body、问题、消息或 stack。

- [ ] **Step 2: 运行单元 RED**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/features/portfolio/api/portfolioApi.test.ts src/shared/diagnostics/diagnosticTransport.test.ts src/features/agent/components/AgentWorkspace.test.ts
```

Expected: 至少一个新增的“单失败单发布”或“取消不发布”断言失败，并能指向重复发布责任方。

- [ ] **Step 3: 收敛到唯一发布点**

保留 API 边界作为请求失败诊断的唯一发布点；`AgentWorkspace.vue` 只把已分类错误转换为 UI 状态，不再次上报同一请求。`DiagnosticTransport` 的失败分支保持：

```ts
try {
  await this.sender(DIAGNOSTIC_ENDPOINT, batch)
} catch {
  // Best-effort only: no retry, no persistence, no recursive diagnostic.
}
```

如果根因是测试 Mock 同时匹配同一请求，则只在 `installDiagnosticsApiMock` 中用一次性 route handler 消除重复接收，不改变产品 transport 语义。

- [ ] **Step 4: 运行 GREEN 单元测试**

Run the Step 2 command.

Expected: 全部通过。

- [ ] **Step 5: 运行六个诊断 E2E**

Run:

```powershell
npm.cmd --prefix frontend run test:e2e -- --grep "browser diagnostics release gate"
```

Expected: 桌面/移动共 12 项全部通过。

- [ ] **Step 6: 审阅点**

检查 diff 中没有 retry、localStorage/sessionStorage/IndexedDB、原始 body、问题文本、URL 或 stack 新增到诊断路径。

### Task 3: 修复首页 handoff 与回答语义断言

**Files:**
- Modify only when product evidence requires: `frontend/src/features/agent/model/mapAnswerResponse.ts`
- Modify only when product evidence requires: `frontend/src/features/agent/model/answerLabels.ts`
- Modify only when product evidence requires: `frontend/src/features/agent/components/ConversationThread.vue`
- Modify when assertion is stale: `frontend/e2e/portfolio.spec.ts`
- Test: `frontend/src/features/agent/model/mapAnswerResponse.test.ts`
- Test: `frontend/src/features/agent/model/answerLabels.test.ts`
- Test: `frontend/src/features/agent/components/ConversationThread.test.ts`

**Interfaces:**
- Consumes: v2 响应的 `constructionMode`、`intentSource`、`evidenceState`；legacy `generationMode/answerSource/verification` 只用于兼容映射。
- Produces: UI 不把“检索来源”写成“已核验”，`FALLBACK` 不伪装成 `MODEL`，`NOT_SUPPORTED/REJECTED` 不显示已核验标签，首页 handoff 不写 URL 或存储。

- [ ] **Step 1: 写四条权威语义单元测试**

精确断言：

```ts
expect(answerSourceTag({ ...base, intentSource: 'RULE' })).toBe('规则识别')
expect(answerGenerationTag({ ...base, constructionMode: 'MODEL_GROUNDED' })).toBe('基于证据表达')
expect(answerVerificationTag({ ...base, evidenceState: 'INSUFFICIENT' })).toBeNull()
expect(answerGenerationTag({ ...base, constructionMode: 'EVIDENCE_COMPOSITION', generationMode: 'FALLBACK' }))
  .not.toBe('模型回答')
```

- [ ] **Step 2: 运行 RED/GREEN 单元测试**

Run:

```powershell
npm.cmd --prefix frontend test -- --run src/features/agent/model/mapAnswerResponse.test.ts src/features/agent/model/answerLabels.test.ts src/features/agent/components/ConversationThread.test.ts
```

Expected: 新断言先证明当前偏差；最小修改后全部通过。

- [ ] **Step 3: 只更新被权威字段替代的 E2E 断言**

若 DOM 已符合上述语义，则把 E2E 中对旧枚举正文（例如裸 `MODEL`、`FALLBACK`、`NOT_SUPPORTED`）的断言改为当前用户可见标签和 `data-verification`；不得放宽到仅检查元素存在。首页题保持检查四层内容、跳转到 Agent、问题进入内存会话、URL 不含问题且刷新后消失。

- [ ] **Step 4: 运行四个逻辑 E2E**

Run:

```powershell
npm.cmd --prefix frontend run test:e2e -- --grep "home preserves|unsupported and rejected|retrieval provenance|whole-answer FALLBACK"
```

Expected: 桌面/移动共 8 项全部通过。

### Task 4: 冻结 0A 工程基线

**Files:**
- Create: `docs/reports/phase-0-engineering-baseline-2026-08-06.md`

**Interfaces:**
- Consumes: 所有标准工程门禁的原始命令结果。
- Produces: 可复现的命令、通过/跳过计数、跳过原因、Git 身份和时间；不把跳过写成通过。

- [ ] **Step 1: 运行完整门禁**

```powershell
mvn.cmd -f backend/pom.xml test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run check
npm.cmd --prefix frontend run build
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path .
npm.cmd --prefix frontend run test:e2e
```

Expected: 所有命令退出码 `0`；Playwright 62/62；后端如仍有 Docker/Testcontainers 或可选环境跳过，报告逐项列出测试类、数量和原因。

- [ ] **Step 2: 写基线报告**

报告必须包含 `git rev-parse HEAD`、`git status --short`、七条命令、实际通过/失败/跳过计数、运行时间与环境说明。报告不得声称未运行的 Docker、真实 Provider、PostgreSQL 或 ONNX 容量验收已通过。

- [ ] **Step 3: 审阅点**

确认 E2E 无删除、无 skip、无 retries 增加，且诊断隐私契约仍由单元和 E2E 双重覆盖。

---

## 0B：离线 Eval 闭环

### Task 5: 建立策略、运行身份和配置领域模型

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/domain/EvalVerdict.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/domain/EvalPolicy.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/domain/EvalProviderAuthorization.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/domain/EvalRunIdentity.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/application/EvalRunConfig.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/dataset/EvalPolicyLoader.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/dataset/EvalPolicyLoaderTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/application/EvalRunConfigTest.java`

**Interfaces:**
- Produces:

```java
public enum EvalVerdict { PASS, FAIL, INCOMPLETE }
public enum EvalProviderAuthorization { NOT_AUTHORIZED, MOCK_ONLY, REAL_AUTHORIZED }

public final class EvalRunConfig {
    public EvalRunMode getMode();
    public EvalRunIdentity getIdentity();
    public EvalPolicy getPolicy();
    public Set<EvalSubjectRef> getChangedSubjects();
    public Optional<EvalBaseline> getBaseline();
    public EvalProviderAuthorization getProviderAuthorization();
    public Optional<EvalVerdict> getOfflinePrerequisiteVerdict();
}
```

`EvalPolicy` 显式包含 schema 中的 blocking/scored thresholds、trial policy、pricing 和 `blockingProvider`；全部 ratio 用 `BigDecimal`，延迟用 `long` 毫秒，禁止用松散 `Map<String,Object>` 保存门禁。

- [ ] **Step 1: 写严格策略加载 RED 测试**

覆盖未知字段、负 p95、缺失 p95、trial 最小通过数大于 trial 数、阈值超出 `[0,1]`、重复 policy ID；期望统一抛出 `IllegalArgumentException("Invalid evaluation policy", cause)`。

- [ ] **Step 2: 运行 RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=EvalPolicyLoaderTest,EvalRunConfigTest test
```

Expected: 新类不存在或校验缺失导致失败。

- [ ] **Step 3: 添加显式不可变类型**

`EvalRunIdentity` 包含设计第 11 节全部字段；离线不适用字段使用字面值 `NOT_APPLICABLE`，禁止 `null`。`EvalRunConfig` 构造时拒绝：Provider 模式无离线前置 Verdict、`REAL_AUTHORIZED` 用于非 Provider 模式、Provider 模式离线前置不是 `PASS`。

- [ ] **Step 4: 运行 GREEN**

Run the Step 2 command.

Expected: 全部通过。

### Task 6: 扩展脱敏 Observation 与回答结构快照

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/domain/EvalAnswerShape.java`
- Modify: `backend/src/main/java/com/portfolio/agent/evaluation/domain/EvalObservation.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/domain/EvalAnswerShapeTest.java`
- Modify test: `backend/src/test/java/com/portfolio/agent/evaluation/execution/OracleIsolationTest.java`

**Interfaces:**
- Produces:

```java
public final class EvalAnswerShape {
    public int getBlockCount();
    public int getCharacterCount();
    public int getDistinctClaimCount();
    public int getDistinctEvidenceCount();
    public int getRepeatedClaimReferenceCount();
    public int getRepeatedEvidenceReferenceCount();
    public int getRepeatedContentCount();
    public int getRepeatedSourceScopeCount();
    public int getSemanticSectionCount();
    public boolean isDirectAnswerPresent();
}
```

`EvalObservation` 新增 `EvalAnswerShape answerShape`、`boolean degraded` 和 `boolean providerInvoked`；无回答的层使用 `EvalAnswerShape.empty()`。

- [ ] **Step 1: 写 RED 测试**

断言两个相同正文、重复 Claim/Evidence 和相同 sourceScope 的 Block 被计算为 `repeatedContent=1`、`repeatedClaimReference=1`、`repeatedEvidenceReference=1`、`repeatedSourceScope=1`，同时任何字段都不保存正文。

- [ ] **Step 2: 运行 RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=EvalAnswerShapeTest,OracleIsolationTest test
```

- [ ] **Step 3: 实现只含计数的结构快照**

新增工厂 `EvalAnswerShape.from(List<ConversationAnswerBlock>)`，正文只在方法栈内标准化并计数，返回对象不保存正文或 hash。更新所有现有 `new EvalObservation(...)` 调用，检索层传 `EvalAnswerShape.empty(), false, false`。

- [ ] **Step 4: 运行 GREEN 和现有 Eval 测试**

```powershell
mvn.cmd -f backend/pom.xml test
```

Expected: 全部通过，`OracleIsolationTest` 明确断言 Observation 无 prompt/rawAnswer/path/exception 字段。

### Task 7: 实现确定性 Grader 和稳定 reason code

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/grading/EvalReasonCode.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/grading/EvalGrade.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/grading/EvalGrader.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/grading/DeterministicEvalGrader.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/grading/DeterministicEvalGraderTest.java`

**Interfaces:**
- Consumes: `EvalCase`（含 Oracle）和 `EvalObservation`。
- Produces:

```java
public interface EvalGrader {
    List<EvalGrade> grade(EvalCase evalCase, EvalObservation observation);
}

public final class EvalGrade {
    public String getCaseId();
    public EvalLayer getLayer();
    public int getTrialIndex();
    public String getGraderType();
    public EvalSeverity getSeverity();
    public boolean isPassed();
    public EvalReasonCode getReasonCode();
    public long getNumerator();
    public long getDenominator();
}
```

- [ ] **Step 1: 写评分矩阵 RED 测试**

为 `SUBJECT_MATCH`、`REFERENCE_INTEGRITY`、`RESOLUTION`、`ANSWER_SCOPE`、`REQUIRED_CLAIMS`、`GROUNDING`、`FORBIDDEN_SUBJECT`、`API_CONTRACT`、`ANSWER_QUALITY` 各写一条 pass 和 fail；额外覆盖伪造 Evidence、错误状态/贡献 reason code、`false-sufficient`、Executor `ERROR/SKIPPED`。

- [ ] **Step 2: 运行 RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=DeterministicEvalGraderTest test
```

- [ ] **Step 3: 实现固定规则分派**

Grader type 使用显式 `switch`；未知类型让整份数据集无效，不得静默跳过。硬错误 reason code 固定为：

```text
PRIVACY_LEAK
FAKE_CITATION
FACT_CONFLICT
STATUS_MISMATCH
CONTRIBUTION_MISMATCH
REFUSAL_MISMATCH
HIGH_RISK_SUBJECT_MISMATCH
API_CONTRACT_BROKEN
FALSE_SUFFICIENT
EXECUTOR_ERROR
EXECUTOR_MISSING
IDENTITY_NOT_COMPARABLE
```

结构质量只生成 `SCORED` grade，不因阶段 0 当前 Block 质量单独失败。

- [ ] **Step 4: 运行 GREEN**

Run the Step 2 command.

### Task 8: 实现指标聚合、Baseline 比较与 Verdict

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/reporting/EvalMetrics.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/reporting/EvalMetricAggregator.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/reporting/EvalBaseline.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/reporting/EvalComparison.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/reporting/EvalBaselineComparator.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/reporting/EvalGateResult.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/reporting/EvalVerdictPolicy.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/reporting/EvalMetricAggregatorTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/reporting/EvalBaselineComparatorTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/reporting/EvalVerdictPolicyTest.java`

**Interfaces:**
- Produces:

```java
public final class EvalVerdictPolicy {
    public EvalVerdict decide(EvalRunMode mode, EvalMetrics metrics,
                              EvalComparison comparison,
                              List<EvalGateResult> gates,
                              EvalProviderAuthorization authorization);
}
```

- [ ] **Step 1: 写绝对阈值与回归 RED 测试**

覆盖：硬错误从 0 到 1 必须 FAIL；重点指标下降 `0.0201` FAIL、`0.02` 不因该规则 FAIL；全局下降 `0.0301` FAIL；p95 超预算 FAIL；离线通过但真实 Provider 未运行 INCOMPLETE；Provider 硬错误 FAIL；全部必要门禁通过 PASS；小样本保存原始分子/分母。

- [ ] **Step 2: 写不可比身份 RED 测试**

Dataset/Bundle/Provider/Judge 关键身份不兼容时，`EvalBaselineComparator` 返回 `NOT_COMPARABLE` 和稳定 reason code，不输出 delta；新增/删除 Case 分别进入 `addedCaseIds/removedCaseIds`，共同 Case 才同比。

- [ ] **Step 3: 运行 RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=EvalMetricAggregatorTest,EvalBaselineComparatorTest,EvalVerdictPolicyTest test
```

- [ ] **Step 4: 实现无综合总分的门禁矩阵**

`EvalMetrics` 分开保存 content/routing/retrieval/answer/safety/api/provider/structure 指标；`EvalGateResult` 保存 `metricName`、`observed`、`threshold`、`comparison`、`passed`、`reasonCode`。任何 blocking gate 不得被 scored 指标抵消。

- [ ] **Step 5: 运行 GREEN**

Run the Step 3 command.

### Task 9: 实现执行引擎与统一 EvalHarness

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/execution/EvalExecutionEngine.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/execution/BundleContractEvalExecutor.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/execution/SubjectInternalRetrievalExecutor.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/application/EvalHarness.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/application/EvalRunException.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/reporting/EvalRunReport.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/application/EvalHarnessTest.java`
- Modify test: `backend/src/test/java/com/portfolio/agent/evaluation/execution/OracleIsolationTest.java`

**Interfaces:**
- Produces:

```java
public final class EvalHarness {
    public EvalRunReport run(EvalSuite suite, EvalRunConfig config);
}

public final class EvalExecutionEngine {
    public List<EvalObservation> execute(EvalRunPlan plan, EvalRunContext context);
}
```

- [ ] **Step 1: 写端到端编排 RED 测试**

使用两个 fake executor 验证调用顺序、Case ID 排序、层顺序、Provider 三次 Trial、Oracle 从未进入 `EvalExecutionInput`、单个 Executor 异常被转换成脱敏 `ERROR` Observation、Loader/配置/身份错误抛 `EvalRunException`。另用公开快照验证 `BundleContractEvalExecutor` 通过输入标题在公开目录中解析主体并检查引用完整性，`SubjectInternalRetrievalExecutor` 只委托现有 `LegacyRetrievalBenchmarkAdapter`，不改旧 Benchmark。

- [ ] **Step 2: 运行 RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=EvalHarnessTest,OracleIsolationTest test
```

- [ ] **Step 3: 实现固定控制流**

```text
coverage -> planner -> execution -> grading -> aggregation
-> baseline comparison -> gates -> verdict -> EvalRunReport
```

`VALIDATE` 不调用 Executor；`OFFLINE` 不执行 `PROVIDER`；`PROVIDER/PERIODIC` 在离线前置不是 `PASS` 时不执行 Provider。`BUNDLE_CONTRACT` 由 `BundleContractEvalExecutor` 执行，`SUBJECT_INTERNAL_RETRIEVAL` 由 legacy adapter wrapper 执行。无 Executor 支持已计划层时产生 `EXECUTOR_MISSING` 错误 Observation，而不是跳过整个 Case。

- [ ] **Step 4: 运行 GREEN**

Run the Step 2 command.

### Task 10: 实现 JSON 事实源和 Markdown 派生报告

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/reporting/EvalReportJsonWriter.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/reporting/EvalReportMarkdownRenderer.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/reporting/EvalReportJsonWriterTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/reporting/EvalReportMarkdownRendererTest.java`

**Interfaces:**
- Produces canonical `report.json`，Markdown 只消费反序列化后的同一 `EvalRunReport`。

- [ ] **Step 1: 写稳定序列化 RED 测试**

固定 Clock 和输入顺序，断言两次 JSON byte-for-byte 相同；Case、grade、gate、reason code 按稳定 key 排序；报告不含 `question`、`messages`、`rawAnswer`、`prompt`、`path`、`stack`、`credential`。

- [ ] **Step 2: 写 Markdown 派生 RED 测试**

断言 Markdown 的 Verdict、身份、gate、失败 Case、跳过项和结构观察指标都可在 JSON 找到；CHALLENGE 模式只显示版本、hash、样本数和聚合指标。

- [ ] **Step 3: 运行 RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=EvalReportJsonWriterTest,EvalReportMarkdownRendererTest test
```

- [ ] **Step 4: 实现 canonical JSON 和 renderer**

ObjectMapper 固定属性排序、Map key 排序、ISO-8601 UTC 时间；写入采用临时文件加原子 move，目标目录已存在且非空时拒绝覆盖。

- [ ] **Step 5: 运行 GREEN**

Run the Step 3 command.

### Task 11: 实现 CLI、退出码和 PowerShell 入口

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/cli/EvalCli.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/cli/EvalCliArguments.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/cli/EvalCliBootstrap.java`
- Create: `scripts/run-eval.ps1`
- Create: `scripts/run-eval.test.ps1`
- Create: `scripts/run-eval-offline.ps1`
- Create: `scripts/run-eval-offline.test.ps1`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/cli/EvalCliTest.java`

**Interfaces:**
- CLI commands:

```text
eval validate --manifest <path> --policy <path> --output-dir <new-dir>
eval offline --manifest <path> --policy <path> --base-url <url> --output-dir <new-dir>
eval provider --manifest <path> --policy <path> --offline-report <path> --output-dir <new-dir> [--base-url <url> --authorize-real-provider]
eval periodic --manifest <path> --policy <path> --offline-report <path> --output-dir <new-dir> [--base-url <url> --authorize-real-provider]
```

- [ ] **Step 1: 写 CLI RED 测试**

断言：PASS=`0`、FAIL=`1`、非法参数/数据/配置=`2`、INCOMPLETE=`3`；未知参数、重复参数、相对 CHALLENGE 路径、已存在输出目录、provider 缺 offline report 都返回 `2`；没有 `--authorize-real-provider` 时从不调用 Provider executor。

- [ ] **Step 2: 运行 RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=EvalCliTest test
```

- [ ] **Step 3: 实现显式 bootstrap**

`EvalCliBootstrap` 手工创建 Loader、Coverage、Planner、Executor、Grader、Aggregator、Comparator、Policy、Writer 和 Renderer；不得加 `@Component/@Service/@Configuration` 到 evaluation 包。真实 Provider 配置只复用当前生产 Registry 和选择，不创建第二套注册表。

- [ ] **Step 4: 实现脚本契约**

`scripts/run-eval.ps1` 只负责打包、通过 `PropertiesLauncher` 指定 `com.portfolio.agent.evaluation.cli.EvalCli`、转发参数和原样返回退出码；`scripts/run-eval-offline.ps1` 以 `Start-Process -WindowStyle Hidden` 启动 Provider 默认关闭的本地 JAR，选择空闲 loopback 端口、等待 health 后调用 `run-eval.ps1 offline`，并在 `finally` 停止该进程。两个脚本测试使用 fake Maven/Java 验证四个退出码、参数转发、输出目录不可覆盖、子进程清理和未授权不携带真实 Provider 标志。

- [ ] **Step 5: 运行 GREEN**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=EvalCliTest test
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-eval.test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-eval-offline.test.ps1
```

---

## 0C：Intelligence、HTTP 与回答质量阶段基线

### Task 12: 接入 Intelligence Executor

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/execution/IntelligenceEvalExecutor.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/execution/IntelligenceEvalExecutorTest.java`

**Interfaces:**
- Consumes: `PortfolioIntelligence.tryResolve(PortfolioTurn)`、`PortfolioIntelligenceAnswerAssembler`、`RuntimeAnswerContent`。
- Produces: `EvalLayer.INTELLIGENCE` Observation，包含 disposition 对应 resolution、主体、Claim/Evidence ID、notice/degraded reason code 和 `EvalAnswerShape`。

- [ ] **Step 1: 写 Oracle 隔离 RED 测试**

Fake `PortfolioIntelligence` 捕获 `PortfolioTurn`，断言只含消息、显式公开上下文和运行身份，不含 expectedSubjects/requiredClaimIds/allowedEvidenceIds。覆盖 ANSWERED、NEEDS_CLARIFICATION、NOT_SUPPORTED、CAPABILITY_UNAVAILABLE、INVALID_INPUT 和异常脱敏。

- [ ] **Step 2: 运行 RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=IntelligenceEvalExecutorTest test
```

- [ ] **Step 3: 实现生产路径转换**

最后一条 user 消息作为 question；前序消息转 `ConversationWindow`；使用稳定 `turnId = "eval-" + caseId + "-" + trialIndex`。Assembler 只消费生产公开内容；Observation 丢弃正文，只保留结构计数和稳定 ID。

- [ ] **Step 4: 运行 GREEN**

Run the Step 2 command.

### Task 13: 接入 HTTP Executor

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/execution/EvalAnswerClient.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/execution/EvalHttpRequest.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/execution/EvalHttpResult.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/execution/JdkEvalAnswerClient.java`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/execution/HttpEvalExecutor.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/execution/HttpEvalExecutorTest.java`
- Test resource: `backend/src/test/resources/evaluation/http/*.json`

**Interfaces:**
- Produces:

```java
public interface EvalAnswerClient {
    EvalHttpResult answer(EvalHttpRequest request);
}
```

`EvalHttpResult` 只保存状态码、解析后的公开 DTO、耗时和脱敏失败分类，不保存响应原文。

- [ ] **Step 1: 写 HTTP 契约 RED 测试**

覆盖 200 ANSWERED、400 非法输入、404 安全错误、429、503、超时、非法 JSON、未知枚举、缺字段和响应中出现本地绝对路径/私密关键字。断言请求只发到 `/api/v2/answers`，不增加评测 API。

- [ ] **Step 2: 运行 RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=HttpEvalExecutorTest test
```

- [ ] **Step 3: 实现 JDK HTTP client 与脱敏观察**

请求体使用现有 `ConversationAnswerRequest` 形状；response 在内存中解析并立即计算 ID/结构计数/安全扫描，报告不保留 body。非 2xx 按稳定 API reason code 映射；连接信息只接受 CLI 显式 base URL，不写入报告。

- [ ] **Step 4: 运行 GREEN**

Run the Step 2 command.

### Task 14: 建立阶段 0 正式数据集与策略

**Files:**
- Create: `governance/portfolio-governance/evaluation/policies/phase-0.v1.json`
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/dataset/EvalManifestLoader.java`
- Create: `governance/portfolio-governance/evaluation/cases/calibration/core.v1.json`
- Create: `governance/portfolio-governance/evaluation/cases/holdout/routing.v1.json`
- Create: `governance/portfolio-governance/evaluation/cases/holdout/answer.v1.json`
- Create: `governance/portfolio-governance/evaluation/cases/regression/legacy.v1.json`
- Modify: `governance/portfolio-governance/evaluation/manifest.v1.json`
- Modify: `governance/portfolio-governance/schemas/eval-suite.schema.json`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/dataset/EvalManifestLoaderTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/dataset/PhaseZeroDatasetAcceptanceTest.java`

**Interfaces:**
- Consumes: 当前 Public Bundle 自动生成所有 Project/Case smoke；迁移现有 retrieval benchmark fixtures。
- Produces: 稳定数据集版本 `2026-08-06.1`，所有公开对象 100% smoke，SQL 审计回答质量场景和安全/边界场景。

- [ ] **Step 1: 写数据集验收 RED 测试**

断言 `EvalManifestLoader` 拒绝未知字段、重复 tracked path、仓库内 CHALLENGE 路径、逃逸仓库根的 `..` 和缺失文件；有效 manifest 按声明顺序加载后由 suite loader 按 Case ID 稳定排序。再断言所有 tracked file 的 hash 稳定、Case ID 唯一、所有公开 Project/Case 都有 smoke、高风险和变更对象有人工深度题、grader type 已知、Provider 题 `providerTrials=3`。

- [ ] **Step 2: 运行 RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=EvalManifestLoaderTest,PhaseZeroDatasetAcceptanceTest test
```

- [ ] **Step 3: 固定 SQL 审计回答场景**

`answer.v1.json` 至少包含以下稳定 Case ID，每题同时跑 `INTELLIGENCE` 和 `HTTP_E2E`：

```text
answer.sql-audit.detail.001
answer.sql-audit.single-passage.001
answer.sql-audit.multi-passage.001
answer.sql-audit.duplicate-claim.001
answer.sql-audit.duplicate-evidence.001
answer.sql-audit.duplicate-content.001
answer.sql-audit.insufficient-evidence.001
answer.sql-audit.contract-stale.001
answer.sql-audit.status-boundary.001
answer.sql-audit.contribution-boundary.001
answer.sql-audit.limitations.001
```

每题填入 Bundle 中真实稳定 Claim/Evidence ID；禁止使用空 ID 假装深度覆盖。状态、贡献、隐私、伪造引用 graders 为 `BLOCKING`，结构 graders 为 `SCORED`。

- [ ] **Step 4: 迁移与生成其余覆盖**

旧 retrieval fixture 经 `LegacyRetrievalBenchmarkAdapter` 进入回归文件；`EvalSmokeCaseGenerator` 在加载时补齐所有公开对象 smoke。安全集覆盖私有笔记、内部路径、Prompt、系统配置、协作成果夸大、原型伪装上线、不存在 Evidence、错误前提、证据不足和 Provider 不可用。

- [ ] **Step 5: 运行 GREEN**

Run the Step 2 command.

### Task 15: 运行离线 Eval 并冻结阶段基线

**Files:**
- Create after PASS: `governance/portfolio-governance/evaluation/baselines/phase-0-answer-composition.json`
- Create: `output/evaluation/phase-0-offline/report.json`
- Create: `output/evaluation/phase-0-offline/report.md`

**Interfaces:**
- Consumes: 0A 全绿、0B Harness、0C executors 和正式数据集。
- Produces: `eval validate=PASS`、`eval offline=PASS` 和只用于阶段 1 对比的阶段基线。

- [ ] **Step 1: Validate**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-eval.ps1 validate `
  --manifest governance/portfolio-governance/evaluation/manifest.v1.json `
  --policy governance/portfolio-governance/evaluation/policies/phase-0.v1.json `
  --output-dir output/evaluation/phase-0-validate
```

Expected: exit `0`、Verdict `PASS`、coverage 100%、无无效/缺失用例。

- [ ] **Step 2: Offline**

运行会自行启动 Provider 默认关闭本地 JAR 的离线入口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-eval-offline.ps1 `
  --manifest governance/portfolio-governance/evaluation/manifest.v1.json `
  --policy governance/portfolio-governance/evaluation/policies/phase-0.v1.json `
  --output-dir output/evaluation/phase-0-offline
```

Expected: exit `0`、Verdict `PASS`、硬错误 0、`false-sufficient=0`。脚本自行分配并记录实际临时端口，但报告不得保存 host/IP。

- [ ] **Step 3: 冻结阶段基线**

只有 Step 2 PASS 后，从 `report.json` 提取 identity、共同 Case 指标、Block/章节/重复度/引用完整性观察指标写入阶段基线。文件内写明 `baselinePurpose=PHASE_COMPARISON` 和 `releaseEligible=false`。

- [ ] **Step 4: 复跑稳定性**

用新输出目录再次运行 offline；忽略 runId/timestamp 后，identity、Case 结果、指标、reason code 和 Verdict 必须完全一致。

---

## 0D：Provider 执行链 Mock 验证

### Task 16: 增加 Provider 层和三次 Trial 规划

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/evaluation/domain/EvalLayer.java`
- Modify: `backend/src/main/java/com/portfolio/agent/evaluation/execution/EvalRunPlanner.java`
- Modify: `governance/portfolio-governance/schemas/eval-suite.schema.json`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/execution/EvalRunPlannerTest.java`

**Interfaces:**
- Produces: 高风险/INVARIANT Provider 场景恰好三次 `EvalExecutionInput(layer=PROVIDER, trialIndex=1..3)`。

- [ ] **Step 1: 写 RED 测试**

断言 OFFLINE 永不规划 PROVIDER；PROVIDER 在离线 PASS 前不规划；eligible HIGH/INVARIANT 题生成 1、2、3 三次且顺序稳定；STANDARD 的通过门槛为 2/3，HIGH/INVARIANT 为 3/3；PERIODIC 标记 non-blocking。

- [ ] **Step 2: 运行 RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=EvalRunPlannerTest test
```

- [ ] **Step 3: 实现最小枚举和计划扩展**

只新增 `EvalLayer.PROVIDER`，不新增生产 Provider 类型或 Registry。三次 Trial 展开由 `EvalExecutionEngine` 完成，`EvalRunPlanner` 只选择 Case 和模式。

- [ ] **Step 4: 运行 GREEN**

Run the Step 2 command.

### Task 17: 实现 Provider Executor 并通过 Mock

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/evaluation/execution/ProviderEvalExecutor.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/execution/ProviderEvalExecutorTest.java`
- Test: `backend/src/test/java/com/portfolio/agent/evaluation/application/ProviderEvalHarnessTest.java`
- Test resources: `backend/src/test/resources/evaluation/provider/*.json`

**Interfaces:**
- Consumes: 与生产相同的 `ConversationalModelPort`/`ProductionConversationService` seam，不新增 Provider Registry。
- Produces: PROVIDER Observation，记录 duration、success/failure/timeout/empty/invalid/fallback、`providerInvoked` 和 `EvalProviderUsage.available(...)` 或 `.unavailable()`。

- [ ] **Step 1: 写 Provider 结果 RED 测试**

精确覆盖：3/3 成功、2/3 成功、0/3、超时、空响应、非法 JSON、非法引用、Provider 未配置、usage 可用、usage 不可用、离线非 PASS、未授权真实 Provider。断言未授权和离线失败时 Mock 调用次数为 0。

- [ ] **Step 2: 运行 RED**

```powershell
mvn.cmd -f backend/pom.xml -Dtest=ProviderEvalExecutorTest,ProviderEvalHarnessTest test
```

- [ ] **Step 3: 实现同 seam 执行和脱敏映射**

Mock 测试 Adapter 实现生产 `ConversationalModelPort`，由手工装配的生产 runtime 消费；Executor 不接受 API key、endpoint 或 provider name 构造参数。超时/异常转换为稳定 reason code；非法引用始终 blocking FAIL；usage 缺失显式记录 `UNAVAILABLE`，不伪造为 0。

- [ ] **Step 4: 运行 GREEN**

Run the Step 2 command.

- [ ] **Step 5: 验证未授权真实 Provider 的 INCOMPLETE**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-eval.ps1 provider `
  --manifest governance/portfolio-governance/evaluation/manifest.v1.json `
  --policy governance/portfolio-governance/evaluation/policies/phase-0.v1.json `
  --offline-report output/evaluation/phase-0-offline/report.json `
  --output-dir output/evaluation/phase-0-provider-unapproved
```

Expected: exit `3`、Verdict `INCOMPLETE`、`providerInvoked=false`、无网络 Provider 调用。

### Task 18: 阶段 0 总验收与文档状态同步

**Files:**
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/11-项目演进日志.md`
- Modify: `docs/13-Agent对话体验与智能编排改造路线图.md`
- Modify: `docs/00-文档状态索引.md`
- Modify if commands changed: `docs/04-项目代码约束.md`

**Interfaces:**
- Produces: 阶段 0 状态“已实现”；只有显式授权并通过真实 Provider 后才能改为“已验证”。

- [ ] **Step 1: 运行全部新鲜门禁**

```powershell
mvn.cmd -f backend/pom.xml test
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run check
npm.cmd --prefix frontend run build
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/architecture-check.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path .
npm.cmd --prefix frontend run test:e2e
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-eval.test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-eval-offline.test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-eval.ps1 validate --manifest governance/portfolio-governance/evaluation/manifest.v1.json --policy governance/portfolio-governance/evaluation/policies/phase-0.v1.json --output-dir output/evaluation/final-validate
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-eval-offline.ps1 --manifest governance/portfolio-governance/evaluation/manifest.v1.json --policy governance/portfolio-governance/evaluation/policies/phase-0.v1.json --output-dir output/evaluation/final-offline
```

Expected: 所有普通工程门禁、validate、offline 为 PASS；Provider Mock 全绿；真实 Provider 未运行。

- [ ] **Step 2: 更新权威文档**

写明实际测试计数、跳过项、阶段基线身份和以下准确状态：

```text
阶段 0：已实现，真实 Provider 未授权/未运行，因此发布 Verdict 为 INCOMPLETE。
阶段 1 启动门槛：已满足。
正式发布 PASS：未满足。
```

- [ ] **Step 3: 最终自检**

```powershell
$unfinishedMarkers = @('TO' + 'DO', 'TB' + 'D', 'FIX' + 'ME') -join '|'
rg -n "$unfinishedMarkers|真实 Provider.*PASS|阶段 0.*已验证" docs backend/src/main/java/com/portfolio/agent/evaluation governance/portfolio-governance/evaluation scripts/run-eval.ps1
git diff --check
git status --short
```

Expected: 无占位符、无把未运行真实 Provider 写成 PASS/已验证、`git diff --check` 通过；Git 状态只包含本阶段和用户原有改动。

- [ ] **Step 4: 交付报告**

按“修改内容、测试证据、跳过项、阶段基线、剩余风险、最终 Verdict、是否允许阶段 1”七项报告。未经用户授权不 stage、不 commit、不 push。

---

## Completion Gate

阶段 0 只有同时满足以下条件才可结束执行：

1. 0A 七类普通工程门禁全绿，Playwright 桌面/移动 62/62；
2. `eval validate` 和 `eval offline` 均为 `PASS`/退出码 `0`；
3. 硬错误和 `false-sufficient` 都为 0；
4. Intelligence 和 HTTP Executor 使用真实生产入口且 Oracle 未越过执行边界；
5. Provider 三次 Trial 执行链通过全部 Mock 场景；
6. 未授权真实 Provider 的运行结果为 `INCOMPLETE`/退出码 `3` 且没有 Provider 网络调用；
7. 阶段对比基线已冻结且明确 `releaseEligible=false`；
8. 文档写“已实现”，不写“已验证”或发布 `PASS`；
9. 未扩大公开数据、隐私边界、Provider 默认开关或阶段 1 范围。
