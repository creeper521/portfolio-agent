# Portfolio V1 Case and Release Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 固定独立 Case 后端契约，阻止未知公开主体进入 Provider，增加真实 Provider 与打包后 Case 冒烟门禁，并同步项目文档。

**Architecture:** 保留现有 `/api/v1/cases`、`/api/v1/cases/{slug}` 和 `/api/v2/answers`，不增加重复接口。在对话运行时最前端增加公开主体 Guard，并用独立 PowerShell 响应断言器把真实 Provider 验收接入现有 JAR 与发布脚本。

**Tech Stack:** Java 21、Spring Boot 3.5、JUnit 5、Mockito、MockMvc、PowerShell 5.1、Maven、现有 Vue/Vitest/Playwright 发布门禁。

## Global Constraints

- 不修改 Vue 页面、路由、组件、CSS、动画或具体视觉实现。
- 只使用当前公开 Bundle；不得读取或搜索私有 Obsidian 内容。
- `PORTFOLIO_MODEL_ENABLED` 和对话 Agent 默认保持 `false`。
- API Key 只来自既有项目专用环境变量，不进入源码、日志、URL、测试夹具或构建产物。
- 只调用显式选择的一个 Provider；不重试到另一个 Provider。
- Project 与 Case 不能同时进入一个 v2 请求上下文。
- 未知 Project/Case 必须在 Provider 调用前 fail-closed。
- 普通 CI 不产生真实 Provider 请求；生产候选显式使用 `-RequireLiveProvider`。
- 保持单 JAR、Docker 和现有隐私扫描边界。

## File Map

- Modify `backend/src/main/java/com/portfolio/agent/answer/dto/request/AnswerRequestSource.java`: 增加 `CASE` 来源。
- Create `backend/src/main/java/com/portfolio/agent/answer/service/ConversationSubjectGuard.java`: 验证请求主体存在于当前公开快照。
- Modify `backend/src/main/java/com/portfolio/agent/answer/service/ConversationalAgentRuntime.java`: Provider 准入前执行 Guard。
- Modify `backend/src/main/java/com/portfolio/agent/answer/service/DeterministicConversationFallback.java`: 为未知主体生成稳定边界响应。
- Modify `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ConversationalAgentConfiguration.java`: 注入 Guard。
- Modify `backend/src/test/java/com/portfolio/agent/answer/dto/request/ConversationAnswerRequestTest.java`: 覆盖 `source=CASE`。
- Create `backend/src/test/java/com/portfolio/agent/answer/service/ConversationSubjectGuardTest.java`: 覆盖公开主体校验。
- Modify `backend/src/test/java/com/portfolio/agent/answer/service/ConversationalAgentRuntimeTest.java`: 证明未知 Case 不调用 Provider。
- Create `backend/src/test/java/com/portfolio/agent/answer/CaseAgentBundleIntegrationTest.java`: 使用真实随包 Bundle 验证 Case API 与 v2。
- Create `scripts/assert-live-provider-response.ps1`: 验证真实 Provider v2 响应且只输出安全摘要。
- Create `scripts/assert-live-provider-response.test.ps1`: 覆盖批准、Key、降级响应和日志脱敏。
- Modify `scripts/run-jar-e2e.ps1`: 增加 Case API、v2 Case 冒烟与可选真实 Provider 冒烟。
- Modify `scripts/run-jar-e2e.test.ps1`: 覆盖新增参数、环境恢复和进程清理。
- Modify `scripts/verify-release.ps1`: 接入脚本测试和 `-RequireLiveProvider`。
- Modify `README.md`, `docs/00-文档状态索引.md`, `docs/05-public-release-bundle-contract.md`, `docs/06-content-publishing-runbook.md`, `docs/08-current-implementation-status.md`: 同步真实状态与交接契约。

---

### Task 1: 固定 `source=CASE` 请求契约

**Files:**
- Modify: `backend/src/main/java/com/portfolio/agent/answer/dto/request/AnswerRequestSource.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/dto/request/ConversationAnswerRequestTest.java`

**Interfaces:**
- Consumes: 现有 Jackson 严格反序列化和 `ConversationAnswerContextRequest.source`。
- Produces: `AnswerRequestSource.CASE`，供 Case 页面和打包后冒烟请求使用。

- [ ] **Step 1: 写失败测试**

在 `ConversationAnswerRequestTest` 增加：

```java
@Test
void acceptsCaseAsAFirstClassRequestSource() throws Exception {
    ConversationAnswerRequest request = new ObjectMapper().readValue("""
            {
              "turnId":"case-turn",
              "question":"这个案例如何验证？",
              "messages":[],
              "context":{
                "caseSlug":"multilingual-image-preservation",
                "audienceRole":"INTERVIEWER",
                "source":"CASE"
              }
            }
            """, ConversationAnswerRequest.class);

    assertThat(request.getContext().getSource())
            .isEqualTo(AnswerRequestSource.CASE);
    assertThat(request.getContext().getCaseSlug())
            .isEqualTo("multilingual-image-preservation");
}
```

- [ ] **Step 2: 验证测试先失败**

Run:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml `
  -Dtest=ConversationAnswerRequestTest test
```

Expected: FAIL，Jackson 报告 `CASE` 不属于 `AnswerRequestSource`。

- [ ] **Step 3: 增加最小实现**

将枚举改为：

```java
public enum AnswerRequestSource {
    HOME,
    AGENT_PAGE,
    PROJECT,
    CASE,
    EVIDENCE
}
```

- [ ] **Step 4: 验证测试通过**

Run: 与 Step 2 相同。  
Expected: `BUILD SUCCESS`，该测试类 0 failure。

- [ ] **Step 5: 提交**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/dto/request/AnswerRequestSource.java `
  backend/src/test/java/com/portfolio/agent/answer/dto/request/ConversationAnswerRequestTest.java
git commit -m "功能：增加案例Agent请求来源"
```

### Task 2: 在 Provider 调用前拒绝未知公开主体

**Files:**
- Create: `backend/src/main/java/com/portfolio/agent/answer/service/ConversationSubjectGuard.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/service/ConversationSubjectGuardTest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/ConversationalAgentRuntime.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/service/DeterministicConversationFallback.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/adapter/model/ConversationalAgentConfiguration.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/service/ConversationalAgentRuntimeTest.java`

**Interfaces:**
- Consumes: `RuntimeAnswerContent`, `ConversationAnswerContextRequest`。
- Produces: `ConversationSubjectGuard.isPublishedSubject(...)`；未知主体返回稳定 `BOUNDARY`，且不调用 `ConversationalModelPort`。

- [ ] **Step 1: 写 Guard 失败测试**

创建 `ConversationSubjectGuardTest`，覆盖：

```java
@Test
void acceptsPublishedCaseAndRejectsUnknownCase() {
    ConversationSubjectGuard guard = new ConversationSubjectGuard();
    RuntimeAnswerContent content = contentWithCase("multilingual-image-preservation");

    assertThat(guard.isPublishedSubject(
            content,
            context(null, "multilingual-image-preservation"))).isTrue();
    assertThat(guard.isPublishedSubject(
            content,
            context(null, "missing-case"))).isFalse();
}

@Test
void acceptsNoHintAndPublishedProject() {
    ConversationSubjectGuard guard = new ConversationSubjectGuard();
    RuntimeAnswerContent content = contentWithProject("sql-audit");

    assertThat(guard.isPublishedSubject(content, context(null, null))).isTrue();
    assertThat(guard.isPublishedSubject(
            content, context("sql-audit", null))).isTrue();
}
```

- [ ] **Step 2: 运行测试并确认缺少类**

Run:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml `
  -Dtest=ConversationSubjectGuardTest test
```

Expected: test compilation FAIL，`ConversationSubjectGuard` 不存在。

- [ ] **Step 3: 实现 Guard**

```java
public final class ConversationSubjectGuard {

    public boolean isPublishedSubject(
            RuntimeAnswerContent content,
            ConversationAnswerContextRequest context
    ) {
        if (context == null) {
            return false;
        }
        if (hasText(context.getProjectSlug())) {
            return content.getProjects().stream()
                    .anyMatch(item -> item.getSlug().equals(context.getProjectSlug()));
        }
        if (hasText(context.getCaseSlug())) {
            return content.getCases().stream()
                    .anyMatch(item -> item.getSlug().equals(context.getCaseSlug()));
        }
        return true;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
```

- [ ] **Step 4: 写运行时失败测试**

在 `ConversationalAgentRuntimeTest` 构建 `providerAccess.isAllowed() == true` 的 fixture，传入 `caseSlug=missing-case`，断言：

```java
ConversationAnswerResult result = runtime.answer(request);

assertThat(result.getResolution()).isEqualTo(AnswerResolution.BOUNDARY);
assertThat(result.isDegraded()).isFalse();
verifyNoInteractions(modelPort);
verifyNoInteractions(toolService);
```

- [ ] **Step 5: 实现运行时边界**

在读取一次内容快照后、检查 Provider Access 前加入：

```java
RuntimeAnswerContent content = knowledgeGateway.getContent();
if (!subjectGuard.isPublishedSubject(content, request.getContext())) {
    return fallback.unknownSubject(request, content);
}
```

`unknownSubject` 固定返回：

```java
return result(
        request,
        content,
        ConversationIntent.PORTFOLIO_GROUNDED,
        ConversationAnswerScope.PORTFOLIO,
        AnswerResolution.BOUNDARY,
        "未找到公开主体",
        "当前公开作品集中不存在该项目或案例。",
        List.of(),
        List.of(),
        false);
```

同时在 `ConversationalAgentConfiguration` 创建并注入 `ConversationSubjectGuard`。更新所有测试 fixture 的构造参数。

- [ ] **Step 6: 运行聚焦测试**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml `
  -Dtest=ConversationSubjectGuardTest,ConversationalAgentRuntimeTest test
```

Expected: `BUILD SUCCESS`，未知主体测试证明模型和工具均未调用。

- [ ] **Step 7: 提交**

```powershell
git add backend/src/main/java/com/portfolio/agent/answer `
  backend/src/test/java/com/portfolio/agent/answer/service
git commit -m "安全：阻止未知公开主体进入对话模型"
```

### Task 3: 增加真实随包 Case Agent 集成测试

**Files:**
- Create: `backend/src/test/java/com/portfolio/agent/answer/CaseAgentBundleIntegrationTest.java`

**Interfaces:**
- Consumes: 随包 `2026-07-27.1` Bundle、Case REST API、v2 REST API。
- Produces: 对真实公开 Case、确定性降级和未知 Case fail-closed 的回归门禁。

- [ ] **Step 1: 创建真实 Bundle 集成测试**

```java
@SpringBootTest(properties = {
        "portfolio.model-expression.enabled=false",
        "portfolio.conversational-agent.enabled=false"
})
@AutoConfigureMockMvc
class CaseAgentBundleIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void packagedBundleExposesCaseAndAcceptsCaseAgentContext() throws Exception {
        mvc.perform(get("/api/v1/cases/multilingual-image-preservation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug")
                        .value("multilingual-image-preservation"))
                .andExpect(jsonPath("$.suggestedQuestions").isArray())
                .andExpect(jsonPath("$.evidence").isArray());

        mvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "turnId":"case-bundle",
                                  "question":"这个案例如何验证？",
                                  "messages":[],
                                  "context":{
                                    "caseSlug":"multilingual-image-preservation",
                                    "audienceRole":"INTERVIEWER",
                                    "source":"CASE"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentVersion").value("2026-07-27.1"))
                .andExpect(jsonPath("$.blocks").isNotEmpty())
                .andExpect(jsonPath("$.degraded").value(true));
    }

    @Test
    void unknownCaseFailsClosedWithoutInventingAPortfolioAnswer() throws Exception {
        mvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "turnId":"missing-case",
                                  "question":"介绍这个案例",
                                  "messages":[],
                                  "context":{
                                    "caseSlug":"missing-public-case",
                                    "audienceRole":"INTERVIEWER",
                                    "source":"CASE"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("BOUNDARY"))
                .andExpect(jsonPath("$.answerScope").value("PORTFOLIO"))
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.blocks[0].claimIds").isEmpty())
                .andExpect(jsonPath("$.blocks[0].evidenceIds").isEmpty());
    }
}
```

- [ ] **Step 2: 运行集成测试**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml `
  -Dtest=CaseAgentBundleIntegrationTest test
```

Expected: `BUILD SUCCESS`，2 tests，0 failure。

- [ ] **Step 3: 运行全部后端测试**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml test
```

Expected: 0 failures、0 errors；仅环境条件基准测试可以 skip。

- [ ] **Step 4: 提交**

```powershell
git add backend/src/test/java/com/portfolio/agent/answer/CaseAgentBundleIntegrationTest.java
git commit -m "测试：覆盖真实内容包案例Agent链路"
```

### Task 4: 实现真实 Provider 响应断言器

**Files:**
- Create: `scripts/assert-live-provider-response.ps1`
- Create: `scripts/assert-live-provider-response.test.ps1`

**Interfaces:**
- Consumes: `-ResponsePath`、`-ExpectedContentVersion`、既有审批和 Key 环境变量。
- Produces: 非零失败码或只含 Provider、版本、resolution、block count 的安全成功摘要。

- [ ] **Step 1: 写失败夹具测试**

测试脚本创建临时 JSON：

```json
{
  "contentVersion": "2026-07-27.1",
  "resolution": "ANSWERED",
  "degraded": false,
  "blocks": [{"sourceScope":"PORTFOLIO","content":"sentinel-answer","claimIds":[],"evidenceIds":[]}],
  "suggestedQuestions": []
}
```

设置四个批准开关、`PORTFOLIO_MODEL_PROVIDER=DEEPSEEK_V4_FLASH` 和临时 sentinel Key，执行断言器并验证：

```powershell
$secretSentinel = 'sentinel-secret-' + [guid]::NewGuid().ToString('N')
$env:PORTFOLIO_MODEL_ENABLED = 'true'
$env:PORTFOLIO_MODEL_DATA_POLICY_APPROVED = 'true'
$env:PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED = 'true'
$env:PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED = 'true'
$env:PORTFOLIO_MODEL_PROVIDER = 'DEEPSEEK_V4_FLASH'
$env:PORTFOLIO_AGENT_DEEPSEEK_API_KEY = $secretSentinel

if ($LASTEXITCODE -ne 0) { throw 'Expected approved response to pass.' }
if ($output -match 'sentinel-answer' -or $output.Contains($secretSentinel)) {
    throw 'Live Provider assertion leaked content or key.'
}
```

再分别验证缺少批准、缺少所选 Key、`degraded=true`、错误版本和空 blocks 均返回非零。

- [ ] **Step 2: 验证测试先失败**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/assert-live-provider-response.test.ps1
```

Expected: FAIL，断言器脚本不存在。

- [ ] **Step 3: 实现断言器**

断言器必须：

```powershell
param(
    [Parameter(Mandatory = $true)][string]$ResponsePath,
    [Parameter(Mandatory = $true)][string]$ExpectedContentVersion
)

$requiredTrue = @(
    'PORTFOLIO_MODEL_ENABLED',
    'PORTFOLIO_MODEL_DATA_POLICY_APPROVED',
    'PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED',
    'PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED'
)
foreach ($name in $requiredTrue) {
    if ([Environment]::GetEnvironmentVariable($name) -ne 'true') {
        throw "$name must be true for live Provider verification."
    }
}
```

根据 `PORTFOLIO_MODEL_PROVIDER` 只检查对应项目专用 Key。解析 JSON 后要求版本一致、`degraded` 为 false、`resolution=ANSWERED`、`blocks.Count -gt 0`。成功只输出：

```text
Live Provider verification passed: provider=<enum>; contentVersion=<version>; resolution=ANSWERED; blocks=<count>.
```

- [ ] **Step 4: 运行脚本测试**

Run: 与 Step 2 相同。  
Expected: `assert-live-provider-response tests passed`，exit 0。

- [ ] **Step 5: 运行隐私检查**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/privacy-check.ps1 -Path scripts
```

Expected: exit 0。

- [ ] **Step 6: 提交**

```powershell
git add scripts/assert-live-provider-response.ps1 `
  scripts/assert-live-provider-response.test.ps1
git commit -m "测试：增加真实模型响应发布门禁"
```

### Task 5: 接入 JAR Case 冒烟和统一发布门禁

**Files:**
- Modify: `scripts/run-jar-e2e.ps1`
- Modify: `scripts/run-jar-e2e.test.ps1`
- Modify: `scripts/verify-release.ps1`

**Interfaces:**
- Consumes: Task 1 的 `source=CASE`、Task 4 的响应断言器。
- Produces: `run-jar-e2e.ps1 -RequireLiveProvider` 和 `verify-release.ps1 -RequireLiveProvider`。

- [ ] **Step 1: 扩展 runner 参数契约测试**

在 `run-jar-e2e.test.ps1` 的参数检查加入：

```powershell
foreach ($parameterName in @(
    'JarPath', 'NpmExecutable', 'Port', 'RequireLiveProvider'
)) {
    if (-not $runnerCommand.Parameters.ContainsKey($parameterName)) {
        throw "Runner is missing testable parameter seam '$parameterName'."
    }
}
```

并要求 runner 输出：

```text
Packaged Case API smoke passed.
Packaged Case Agent smoke passed.
```

- [ ] **Step 2: 验证 runner 测试失败**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/run-jar-e2e.test.ps1
```

Expected: FAIL，缺少参数或 Case 冒烟输出。

- [ ] **Step 3: 在 JAR 就绪后增加 Case API 冒烟**

请求：

```powershell
$caseResponse = Invoke-RestMethod `
    "$baseUrl/api/v1/cases/multilingual-image-preservation"
if ($caseResponse.slug -ne 'multilingual-image-preservation') {
    throw 'Packaged Case API returned the wrong subject.'
}
if (@($caseResponse.evidence).Count -eq 0) {
    throw 'Packaged Case API returned no public evidence.'
}
Write-Output 'Packaged Case API smoke passed.'
```

- [ ] **Step 4: 增加 v2 Case Agent 冒烟**

以 `source=CASE` POST 已发布公开问题，普通模式要求 HTTP 200、正确内容版本、非空 blocks。输出固定为：

```text
Packaged Case Agent smoke passed.
```

不得输出请求正文或完整响应。

- [ ] **Step 5: 接入真实 Provider 断言**

新增 `[switch]$RequireLiveProvider`。开启时把 v2 响应写入临时文件，调用：

```powershell
& powershell.exe -NoProfile -ExecutionPolicy Bypass `
    -File (Join-Path $root 'scripts\assert-live-provider-response.ps1') `
    -ResponsePath $responsePath `
    -ExpectedContentVersion ([string]$publicContent.contentVersion)
Assert-ExitCode 'Live Provider response verification'
```

在 `finally` 删除经过精确验证的临时响应文件，并继续恢复 Playwright 环境和停止 Java 进程。

- [ ] **Step 6: 接入统一发布脚本**

`verify-release.ps1` 增加 `[switch]$RequireLiveProvider`，先执行断言器测试，再把开关传给 `run-jar-e2e.ps1`：

```powershell
& powershell.exe -NoProfile -ExecutionPolicy Bypass `
    -File (Join-Path $root 'scripts\assert-live-provider-response.test.ps1')
Assert-ExitCode 'Live Provider response checker tests'

$jarE2eArguments = @(
    '-NoProfile', '-ExecutionPolicy', 'Bypass',
    '-File', (Join-Path $root 'scripts\run-jar-e2e.ps1')
)
if ($RequireLiveProvider) {
    $jarE2eArguments += '-RequireLiveProvider'
}
& powershell.exe @jarE2eArguments
Assert-ExitCode 'Packaged JAR Playwright integration tests'
```

- [ ] **Step 7: 运行脚本测试和打包后 E2E**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/assert-live-provider-response.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/run-jar-e2e.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/run-jar-e2e.ps1
```

Expected: 两个脚本测试通过；JAR Case API、Case Agent 和 Playwright 通过；Java 进程停止；环境恢复。

- [ ] **Step 8: 提交**

```powershell
git add scripts/run-jar-e2e.ps1 scripts/run-jar-e2e.test.ps1 `
  scripts/verify-release.ps1
git commit -m "发布：接入案例与真实模型验收门禁"
```

### Task 6: 同步项目状态、运行手册和前端交接

**Files:**
- Modify: `README.md`
- Modify: `docs/00-文档状态索引.md`
- Modify: `docs/05-public-release-bundle-contract.md`
- Modify: `docs/06-content-publishing-runbook.md`
- Modify: `docs/08-current-implementation-status.md`

**Interfaces:**
- Consumes: Tasks 1–5 的最终参数名、接口和测试证据。
- Produces: 与代码一致的当前状态、真实 Provider 命令和前端 AI 交接边界。

- [ ] **Step 1: 修正过期状态**

统一为：

```text
schema 3.0 / 2026-07-27.1
7 Project / 49 Case / 81 Claim / 59 Evidence
81 Claim–Evidence links / 11 TimelineEvent / 16 QuestionPreset
61/68 public assets; 7 EXCLUDE remain private
```

删除“Case 前端完全未实现”和“v2 前端尚未接入”的错误结论，改为：

```text
共享 Case 目录模型、详情投影与 v2 调用已经存在；
独立 /cases 路由和具体前端体验由后续前端工作实现。
```

- [ ] **Step 2: 写明 Case Agent 契约**

文档加入：

```json
{
  "context": {
    "projectSlug": null,
    "caseSlug": "multilingual-image-preservation",
    "audienceRole": "INTERVIEWER",
    "source": "CASE"
  }
}
```

写明 Project/Case 互斥、未知主体 fail-closed、Case 不自动扩大到关联 Project。

- [ ] **Step 3: 写明真实 Provider 命令**

在不包含任何 Key 值的前提下记录：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/verify-release.ps1 `
  -SkipInstall `
  -RequireLiveProvider
```

明确普通 CI 默认不发起外部请求，生产候选需要单独保留一次成功证据。

- [ ] **Step 4: 增加前端 AI 交接引用**

从 README 和状态文档链接：

```text
docs/superpowers/specs/2026-07-28-portfolio-v1-case-and-release-closure-design.md
```

明确本轮未修改前端页面、组件和视觉。

- [ ] **Step 5: 执行文档一致性检查**

```powershell
rg -n "2026-07-23\.1|1 个 SQL 审计 Project|3 个 CaseStudy|前端尚无 Case|前端尚未接入" `
  README.md docs/00-文档状态索引.md docs/05-public-release-bundle-contract.md `
  docs/06-content-publishing-runbook.md docs/08-current-implementation-status.md
```

Expected: 对当前状态的过期断言为 0；历史记录如保留，必须明确标注为历史快照。

- [ ] **Step 6: 提交**

```powershell
git add README.md docs/00-文档状态索引.md `
  docs/05-public-release-bundle-contract.md `
  docs/06-content-publishing-runbook.md `
  docs/08-current-implementation-status.md
git commit -m "文档：同步案例与发布收尾状态"
```

### Task 7: 完整验证与最终审查

**Files:**
- Verify only: all files changed in Tasks 1–6

**Interfaces:**
- Consumes: 所有实现任务。
- Produces: 可审计的本地发布验证结果和明确的真实 Provider 外部阻塞状态。

- [ ] **Step 1: 检查修改范围**

```powershell
git status --short
git diff c9d6548...HEAD --stat
git diff c9d6548...HEAD -- frontend/src frontend/e2e
```

Expected: 前端源码和 E2E 无本轮修改；`.zcode/`、根级 `node_modules/` 不进入提交。

- [ ] **Step 2: 运行后端全量测试**

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml test
```

Expected: 0 failures，0 errors。

- [ ] **Step 3: 运行普通完整发布门禁**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/verify-release.ps1 `
  -SkipInstall
```

Expected: `Release verification passed.`；若 Docker CLI 不存在，输出必须明确记录 Docker check 未执行。

- [ ] **Step 4: 条件运行真实 Provider 门禁**

仅当四项批准和所选 Provider Key 已由用户环境提供时：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/verify-release.ps1 `
  -SkipInstall `
  -RequireLiveProvider
```

Expected: 安全摘要显示 `degraded=false`、正确内容版本和非零 blocks。若环境未提供审批或 Key，最终报告明确写为“门禁已实现并测试，外部真实调用未执行”，不得声称通过。

- [ ] **Step 5: 检查提交与工作区**

```powershell
git log --oneline -8
git status --short --branch
```

Expected: 仅计划内提交；已跟踪工作区干净；用户原有未跟踪目录保持不变。
