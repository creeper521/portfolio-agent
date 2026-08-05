# Live Provider Probe and Structured Subject Routing Correction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让本地启动与发布门禁可靠证明真实 Provider 已被调用，并把结构化 Project/Case 主体从“隐式意图”纠正为模型或规则任务上的不可突破检索范围。

**Architecture:** 新增一个 PowerShell Live Provider 探针入口，统一构造无主体 canary、调用回答 API、执行类型化响应断言并输出安全分类；`start-local.ps1` 与 `run-jar-e2e.ps1` 只消费该入口。Java 侧把结构化主体解析结果从预构造 `FACT_LOOKUP` 改为 `MATCHED(subjectId)`，先完成主体校验，再让规则或模型决定任务，最后由服务端附加主体约束；模型关闭时保留确定性 scoped fallback。

**Tech Stack:** PowerShell 5.1、Java 21、Spring Boot 3.5、JUnit 5、AssertJ、Mockito、Maven、Vue 3/TypeScript、Vitest、Playwright

## Global Constraints

- 设计权威为 `docs/superpowers/specs/2026-08-05-live-provider-probe-and-structured-subject-routing-design.md`。
- 不修改 `/api/v2/answers` 的公开 JSON Schema，不新增公开诊断端点。
- 不修改 Provider Registry、Key 名称、模型默认开关、检索默认 Profile 或公开 Bundle 内容。
- `projectSlug` / `caseSlug` 只产生已验证 `subjectId` scope，模型无权扩大或清除该 scope。
- `PRESET_CONTRACT` 与 `exactPassages` 不得用于任意自由问题兜底。
- 未知或不唯一 slug 必须在普通确定性规则、模型和检索之前返回 `INVALID_INPUT`。
- 普通 CI 不得调用真实 Provider；真实调用只允许在显式 Secret 文件或 `-RequireLiveProvider` 路径执行。
- PowerShell 输出不得包含 Key、访客问题、响应正文、Authorization Header 或仓库外 Secret 路径。
- Java 生产与测试代码不得使用 `var`、`record` 或 Lombok。
- 保留用户现有工作树；不得 reset、restore、stash、覆盖或提交无关修改。
- 所有 Git 提交步骤仅在用户明确授权后执行，提交信息必须使用中文。
- 每个行为修复先写失败测试，再做最小实现，再运行相关回归。

---

## 文件结构

### 新增文件

- `scripts/provider-probe/invoke-live-provider-probe.ps1`：唯一 Live Provider HTTP 探针入口，负责无主体请求、断言调用、安全分类和临时文件清理。
- `scripts/provider-probe/invoke-live-provider-probe.test.ps1`：覆盖 canary 请求契约、成功、路由绕过、Provider notice、传输失败、退出码和脱敏。
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectResolver.java`：只校验结构化 Project/Case slug 并返回稳定 `subjectId`。
- `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectResolverTest.java`：锁定 NONE/MATCHED/INVALID 三态。

### 删除文件

- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectTaskResolver.java`：职责被 `StructuredSubjectResolver` 替代。
- `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectTaskResolverTest.java`：测试迁移到新 resolver。

### 修改文件

- `scripts/assert-live-provider-response.ps1`
- `scripts/assert-live-provider-response.test.ps1`
- `scripts/test-fixtures/start-local-fake-server.ps1`
- `scripts/start-local.ps1`
- `scripts/start-local.test.ps1`
- `scripts/run-jar-e2e.ps1`
- `scripts/run-jar-e2e.test.ps1`
- `scripts/verify-release.ps1`
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/StructuredSubjectResolution.java`
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligence.java`
- `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioIntelligenceConfiguration.java`
- `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligenceRoutingTest.java`
- `backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioIntelligenceConfigurationTest.java`
- `backend/src/test/java/com/portfolio/agent/answer/controller/PresetContractBundleIntegrationTest.java`
- `backend/src/test/java/com/portfolio/agent/answer/controller/CaseConversationBundleIntegrationTest.java`
- `README.md`
- `docs/00-文档状态索引.md`
- `docs/08-当前实现状态.md`
- `docs/11-项目演进日志.md`
- `docs/12-工程质量与未来优化评审备忘录.md`

---

### Task 1: 为 Live Provider 响应断言增加封闭失败码

**Files:**

- Modify: `scripts/assert-live-provider-response.ps1`
- Modify: `scripts/assert-live-provider-response.test.ps1`

**Interfaces:**

- Consumes: `-ResponsePath <absolute file>`、`-ExpectedContentVersion <string>` 和现有六项 Provider 环境配置。
- Produces: 成功退出 `0` 并输出 `Live Provider verification passed: ...`；失败退出 `1`，stderr 只含一个 `LIVE_PROVIDER_*` 封闭代码。

- [ ] **Step 1: 为每个断言原因补 RED 测试**

在 `assert-live-provider-response.test.ps1` 的 fixture helper 中保留一份完全合法响应，然后逐字段变异并断言精确失败码：

```powershell
$cases = @(
    @{ Name = 'route'; Patch = @{ intentSource = 'RULE' };
       Code = 'LIVE_PROVIDER_ROUTE_BYPASSED' },
    @{ Name = 'construction'; Patch = @{ constructionMode = 'TEMPLATE' };
       Code = 'LIVE_PROVIDER_CONSTRUCTION_INVALID' },
    @{ Name = 'evidence'; Patch = @{ evidenceState = 'INSUFFICIENT' };
       Code = 'LIVE_PROVIDER_EVIDENCE_UNVERIFIED' },
    @{ Name = 'resolution'; Patch = @{ resolution = 'NOT_SUPPORTED' };
       Code = 'LIVE_PROVIDER_RESOLUTION_INVALID' },
    @{ Name = 'blocks'; Patch = @{ blocks = @() };
       Code = 'LIVE_PROVIDER_BLOCKS_MISSING' }
)

foreach ($case in $cases) {
    $result = Invoke-CheckerFixture -Patch $case.Patch
    Assert-True ($result.ExitCode -eq 1) "$($case.Name) must fail"
    Assert-True ($result.Output -match [regex]::Escape($case.Code)) `
        "$($case.Name) returned the wrong safe code"
}
```

同时覆盖：配置非法、响应文件缺失、JSON 非法、contentVersion 不同、`degraded=true`。每个失败输出都断言不包含 Key sentinel、问题正文和响应正文。

- [ ] **Step 2: 运行断言测试确认 RED**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/assert-live-provider-response.test.ps1
```

Expected: FAIL，至少 `intentSource=RULE` 场景仍只得到通用 `Live Provider response assertion failed.`。

- [ ] **Step 3: 用显式异常代码替换匿名断言**

在 `assert-live-provider-response.ps1` 中增加：

```powershell
$script:allowedFailureCodes = @(
    'LIVE_PROVIDER_CONFIG_INVALID',
    'LIVE_PROVIDER_RESPONSE_UNREADABLE',
    'LIVE_PROVIDER_CONTENT_VERSION_MISMATCH',
    'LIVE_PROVIDER_REPORTED_DEGRADED',
    'LIVE_PROVIDER_ROUTE_BYPASSED',
    'LIVE_PROVIDER_CONSTRUCTION_INVALID',
    'LIVE_PROVIDER_EVIDENCE_UNVERIFIED',
    'LIVE_PROVIDER_RESOLUTION_INVALID',
    'LIVE_PROVIDER_BLOCKS_MISSING'
)

function Stop-Assertion([string]$Code) {
    if ($Code -notin $script:allowedFailureCodes) {
        throw 'LIVE_PROVIDER_RESPONSE_UNREADABLE'
    }
    throw $Code
}
```

把现有断言逐项改成精确代码，例如：

```powershell
if ($response.intentSource -cne 'MODEL') {
    Stop-Assertion 'LIVE_PROVIDER_ROUTE_BYPASSED'
}
if ($response.constructionMode -cne 'EVIDENCE_COMPOSITION') {
    Stop-Assertion 'LIVE_PROVIDER_CONSTRUCTION_INVALID'
}
if ($response.evidenceState -cne 'VERIFIED') {
    Stop-Assertion 'LIVE_PROVIDER_EVIDENCE_UNVERIFIED'
}
```

catch 只输出封闭代码：

```powershell
catch {
    $code = [string]$_.Exception.Message
    if ($code -notin $script:allowedFailureCodes) {
        $code = 'LIVE_PROVIDER_RESPONSE_UNREADABLE'
    }
    [Console]::Error.WriteLine($code)
    exit 1
}
```

- [ ] **Step 4: 运行断言测试确认 GREEN**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/assert-live-provider-response.test.ps1
```

Expected: `assert-live-provider-response tests passed`，exit `0`。

- [ ] **Step 5: 授权后提交检查点**

仅在获得用户明确 Git 授权后运行：

```powershell
git add scripts/assert-live-provider-response.ps1 scripts/assert-live-provider-response.test.ps1
git commit -m "修复：细分真实模型响应断言失败语义"
```

---

### Task 2: 建立唯一的无主体 Live Provider 探针入口

**Files:**

- Create: `scripts/provider-probe/invoke-live-provider-probe.ps1`
- Create: `scripts/provider-probe/invoke-live-provider-probe.test.ps1`
- Modify: `scripts/test-fixtures/start-local-fake-server.ps1`
- Modify: `scripts/verify-release.ps1`

**Interfaces:**

- Consumes: `-BackendBaseUrl string`、`-ExpectedContentVersion string`、`-TimeoutSeconds int`、可选 `-FailOnDegraded`，以及继承到当前进程的 Provider 环境。
- Produces: `LIVE_PROVIDER_CONNECTED` 或 `LIVE_PROVIDER_DEGRADED:<CATEGORY>`；`-FailOnDegraded` 下失败退出 `1`，否则安全降级退出 `0`。

- [ ] **Step 1: 让 fake server 返回请求体并按路由语义响应**

先修改 `Read-Request` 的测试期契约，使其返回请求行和 UTF-8 body：

```powershell
function Read-Request([System.Net.Sockets.NetworkStream]$Stream) {
    $reader = [System.IO.StreamReader]::new(
        $Stream, [System.Text.Encoding]::UTF8, $false, 1024, $true)
    $requestLine = $reader.ReadLine()
    $contentLength = 0
    while ($true) {
        $line = $reader.ReadLine()
        if ([string]::IsNullOrEmpty($line)) { break }
        if ($line.StartsWith('Content-Length:',
                [System.StringComparison]::OrdinalIgnoreCase)) {
            $contentLength = [int]$line.Substring('Content-Length:'.Length).Trim()
        }
    }
    $body = ''
    if ($contentLength -gt 0) {
        $buffer = [char[]]::new($contentLength)
        $read = $reader.ReadBlock($buffer, 0, $contentLength)
        $body = -join $buffer[0..($read - 1)]
    }
    return [pscustomobject]@{ RequestLine = $requestLine; Body = $body }
}
```

`BACKEND_MODEL` 对 `/api/v2/answers` 解析 JSON：只有请求不含 `projectSlug`、`caseSlug`、`questionPresetId`、`contractVersion`、`referenceContext` 和 `recommendationContext` 时返回 `MODEL + VERIFIED`；否则返回本次真实回归响应：

```json
{"contentVersion":"test-v1","intentSource":"RULE","constructionMode":"EVIDENCE_COMPOSITION","evidenceState":"INSUFFICIENT","degraded":false,"resolution":"NOT_SUPPORTED","blocks":[{"content":"fixture"}]}
```

- [ ] **Step 2: 写共享探针 RED 测试**

新测试必须覆盖：

```powershell
$connected = Invoke-ProbeFixture -Mode 'BACKEND_MODEL'
Assert-True ($connected.Output -match '^LIVE_PROVIDER_CONNECTED$') `
    'subject-free canary must connect'

$source = Get-Content -LiteralPath $probeScript -Raw
foreach ($forbidden in @(
    'projectSlug', 'caseSlug', 'questionPresetId', 'contractVersion',
    'referenceContext', 'recommendationContext'
)) {
    Assert-True ($source -notmatch "(?m)^\s*$forbidden\s*=") `
        "probe must not construct $forbidden"
}
```

再覆盖 `BACKEND_FALLBACK`、传输失败、`-FailOnDegraded` 退出码，以及输出不包含 Key sentinel。

- [ ] **Step 3: 运行新测试确认 RED**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provider-probe/invoke-live-provider-probe.test.ps1
```

Expected: FAIL，探针脚本尚不存在。

- [ ] **Step 4: 实现共享探针脚本**

文件头与请求体固定为：

```powershell
param(
    [Parameter(Mandatory = $true)][string]$BackendBaseUrl,
    [Parameter(Mandatory = $true)][string]$ExpectedContentVersion,
    [ValidateRange(1, 300)][int]$TimeoutSeconds = 60,
    [switch]$FailOnDegraded
)

$ErrorActionPreference = 'Stop'
$checker = Join-Path (Split-Path -Parent $PSScriptRoot) `
    'assert-live-provider-response.ps1'

$requestBody = @{
    turnId = [guid]::NewGuid()
    requestToken = [guid]::NewGuid()
    question = 'Please introduce the SQL audit and troubleshooting project in detail.'
    messages = @()
    context = @{
        audienceRole = 'INTERVIEWER'
        source = 'AGENT_PAGE'
    }
} | ConvertTo-Json -Depth 6 -Compress
```

HTTP、断言和清理骨架：

```powershell
$responsePath = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('portfolio-provider-probe-' + [guid]::NewGuid().ToString('N') + '.json')
try {
    try {
        $http = Invoke-WebRequest -UseBasicParsing `
            -Uri "$BackendBaseUrl/api/v2/answers" -Method Post `
            -ContentType 'application/json; charset=utf-8' `
            -Body ([System.Text.Encoding]::UTF8.GetBytes($requestBody)) `
            -TimeoutSec $TimeoutSeconds
    }
    catch {
        Write-Output 'LIVE_PROVIDER_DEGRADED:PROVIDER_UNAVAILABLE'
        if ($FailOnDegraded) { exit 1 }
        exit 0
    }
    [System.IO.File]::WriteAllText(
        $responsePath, [string]$http.Content,
        [System.Text.UTF8Encoding]::new($false))
    $assertionOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File $checker -ResponsePath $responsePath `
        -ExpectedContentVersion $ExpectedContentVersion 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -eq 0) {
        Write-Output 'LIVE_PROVIDER_CONNECTED'
        exit 0
    }
    $response = $http.Content | ConvertFrom-Json
    $category = Resolve-ProbeCategory $response $assertionOutput
    Write-Output "LIVE_PROVIDER_DEGRADED:$category"
    if ($FailOnDegraded) { exit 1 }
    exit 0
}
finally {
    if (Test-Path -LiteralPath $responsePath) {
        Remove-Item -LiteralPath $responsePath -Force
    }
}
```

`Resolve-ProbeCategory` 使用完整封闭映射：

```powershell
function Resolve-ProbeCategory(
    [object]$Response,
    [string]$AssertionOutput
) {
    $providerCategory = switch ([string]$Response.noticeCode) {
        'PROVIDER_AUTH_FAILED' { 'PROVIDER_AUTH_FAILED' }
        'PROVIDER_TIMEOUT' { 'PROVIDER_TIMEOUT' }
        'PROVIDER_CONNECTION_FAILED' { 'PROVIDER_UNAVAILABLE' }
        'PROVIDER_EMPTY_RESPONSE' { 'PROVIDER_RESPONSE_INVALID' }
        'PROVIDER_INVALID_RESPONSE' { 'PROVIDER_RESPONSE_INVALID' }
        'PROVIDER_DRAFT_REJECTED' { 'PROVIDER_DRAFT_REJECTED' }
        'PROVIDER_DISABLED' { 'PROVIDER_POLICY_INCOMPATIBLE' }
        default { $null }
    }
    if ($null -ne $providerCategory) {
        return $providerCategory
    }
    if ($AssertionOutput -match 'LIVE_PROVIDER_ROUTE_BYPASSED') {
        return 'PROBE_ROUTE_BYPASSED'
    }
    return 'PROVIDER_RESPONSE_INVALID'
}
```

- [ ] **Step 5: 将新测试接入发布脚本的确定性测试阶段**

在 `verify-release.ps1` 紧接现有 response checker tests 后加入：

```powershell
& powershell.exe -NoProfile -ExecutionPolicy Bypass `
    -File (Join-Path $root `
        'scripts\provider-probe\invoke-live-provider-probe.test.ps1')
Assert-ExitCode 'Live Provider probe contract tests'
```

- [ ] **Step 6: 运行脚本测试确认 GREEN**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/assert-live-provider-response.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provider-probe/invoke-live-provider-probe.test.ps1
```

Expected: 两个脚本均输出 `tests passed`，exit `0`；测试进程和端口均被清理。

- [ ] **Step 7: 授权后提交检查点**

仅在获得用户明确 Git 授权后运行：

```powershell
git add scripts/provider-probe scripts/test-fixtures/start-local-fake-server.ps1 scripts/verify-release.ps1
git commit -m "修复：建立无主体真实模型探针契约"
```

---

### Task 3: 让 start-local 使用共享探针并保留降级启动语义

**Files:**

- Modify: `scripts/start-local.ps1`
- Modify: `scripts/start-local.test.ps1`

**Interfaces:**

- Consumes: Task 2 的 `invoke-live-provider-probe.ps1` 输出。
- Produces: 成功保持 `AI_CONNECTED provider=... backend=... frontend=...`；失败保持进程运行并输出 `AI_DEGRADED:<CATEGORY>`。

- [ ] **Step 1: 增加会抓住旧探针的 RED 测试**

在 `start-local.test.ps1` 的 `BACKEND_MODEL` 场景中，request-aware fake 已会对携带 slug 的旧请求返回 `RULE + INSUFFICIENT`。保留原 `AI_CONNECTED` 断言，并新增：

```powershell
Assert-True ($modelResult.Output -notmatch 'PROBE_ROUTE_BYPASSED') `
    'start-local sent a product-scoped request instead of the Provider canary.'
```

为 `BACKEND_FALLBACK` 场景断言现有 Provider notice 仍映射到 `PROVIDER_DRAFT_REJECTED`，而非 `PROBE_ROUTE_BYPASSED`。

- [ ] **Step 2: 运行 start-local 测试确认 RED**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.test.ps1
```

Expected: FAIL，旧 `Invoke-ProviderProbe` 携带 `projectSlug=sql-audit`，fake 返回非 MODEL。

- [ ] **Step 3: 删除内嵌请求与重复分类，调用共享探针**

用以下实现替换 `Invoke-ProviderProbe` 的 HTTP/临时文件逻辑：

```powershell
function Invoke-ProviderProbe(
    [string]$BackendBaseUrl,
    [string]$ContentVersion,
    [hashtable]$Settings
) {
    $environmentSnapshot = Set-TemporaryProcessEnvironment $Settings
    try {
        $probeOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File (Join-Path $PSScriptRoot `
                'provider-probe\invoke-live-provider-probe.ps1') `
            -BackendBaseUrl $BackendBaseUrl `
            -ExpectedContentVersion $ContentVersion `
            -TimeoutSeconds $ReadinessTimeoutSeconds 2>&1 | Out-String).Trim()
    }
    finally {
        Restore-ProcessEnvironment $environmentSnapshot
    }
    if ($probeOutput -eq 'LIVE_PROVIDER_CONNECTED') {
        return 'CONNECTED'
    }
    if ($probeOutput -match '^LIVE_PROVIDER_DEGRADED:(?<category>[A-Z0-9_]+)$') {
        return $Matches.category
    }
    return 'PROVIDER_RESPONSE_INVALID'
}
```

删除 `Get-DegradedCategory`，因为分类权威已经进入共享探针脚本。

- [ ] **Step 4: 运行 start-local 与探针测试确认 GREEN**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provider-probe/invoke-live-provider-probe.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.test.ps1
```

Expected: 均 PASS；MODEL fixture 输出 `AI_CONNECTED`；fallback fixture 输出精确降级码；测试端口关闭。

- [ ] **Step 5: 授权后提交检查点**

仅在获得用户明确 Git 授权后运行：

```powershell
git add scripts/start-local.ps1 scripts/start-local.test.ps1
git commit -m "修复：让本地启动复用真实模型探针"
```

---

### Task 4: 将 packaged Case smoke 与 Live Provider 门禁拆开

**Files:**

- Modify: `scripts/run-jar-e2e.ps1`
- Modify: `scripts/run-jar-e2e.test.ps1`

**Interfaces:**

- Consumes: Task 2 的共享探针入口。
- Produces: Case smoke 独立输出 `Packaged Case Agent smoke passed.`；Live 模式额外输出 `Packaged Live Provider verification passed.`。

- [ ] **Step 1: 写 source-contract RED 测试**

在 `run-jar-e2e.test.ps1` 读取 runner source 并断言：

```powershell
if ($runnerSource -notmatch
        'provider-probe\\invoke-live-provider-probe\.ps1') {
    throw 'Packaged runner must call the shared Live Provider probe.'
}
if ($runnerSource -match
        '\$caseAgentResponse\s*\|\s*ConvertTo-Json[\s\S]+assert-live-provider-response') {
    throw 'Case smoke response must not be reused as Live Provider evidence.'
}
```

保留已有普通模式 Provider 禁用、进程清理、stdout JSON、隐私 sentinel 和 Playwright 环境恢复测试。

- [ ] **Step 2: 运行 runner 测试确认 RED**

前置：

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml package -DskipTests
```

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-jar-e2e.test.ps1
```

Expected: FAIL，runner 仍把 `$caseAgentResponse` 写入 Live Provider response file。

- [ ] **Step 3: 用共享探针替换 Case 响应断言**

删除 `$liveProviderResponsePath` 及其写入、断言、清理逻辑。`-RequireLiveProvider` 分支改为：

```powershell
if ($RequireLiveProvider) {
    $probeOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root `
            'scripts\provider-probe\invoke-live-provider-probe.ps1') `
        -BackendBaseUrl $baseUrl `
        -ExpectedContentVersion ([string]$publicContent.contentVersion) `
        -TimeoutSeconds $ReadinessTimeoutSeconds `
        -FailOnDegraded 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $probeOutput -ne 'LIVE_PROVIDER_CONNECTED') {
        throw "Live Provider verification failed: $probeOutput"
    }
    Write-Output 'Packaged Live Provider verification passed.'
}
```

异常中只能包含共享探针的封闭状态行。

- [ ] **Step 4: 更新清理故障测试**

删除针对 `$liveProviderResponsePath` 的源码替换测试，因为临时响应现在由共享探针自身 `finally` 管理。把清理覆盖移动到 `invoke-live-provider-probe.test.ps1`：在断言失败后验证 `portfolio-provider-probe-*` 临时文件数量没有增加。

- [ ] **Step 5: 运行 runner 回归确认 GREEN**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provider-probe/invoke-live-provider-probe.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-jar-e2e.test.ps1
```

Expected: 两者 PASS；普通模式不触发 Provider；live source contract 使用独立 canary；所有进程和端口清理完成。

- [ ] **Step 6: 授权后提交检查点**

仅在获得用户明确 Git 授权后运行：

```powershell
git add scripts/run-jar-e2e.ps1 scripts/run-jar-e2e.test.ps1 scripts/provider-probe/invoke-live-provider-probe.test.ps1
git commit -m "修复：拆分案例冒烟与真实模型发布门禁"
```

---

### Task 5: 将结构化主体解析结果改为纯 Scope

**Files:**

- Create: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectResolver.java`
- Create: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectResolverTest.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/StructuredSubjectResolution.java`
- Delete: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectTaskResolver.java`
- Delete: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectTaskResolverTest.java`

**Interfaces:**

- Consumes: `PortfolioTurn` 与当前 `RuntimeAnswerContent`。
- Produces: `StructuredSubjectResolution.none()`、`matched(String subjectId)`、`invalid()`；MATCHED 不创建任务。

- [ ] **Step 1: 写 resolver RED 测试**

新测试锁定：

```java
@Test
void knownProjectReturnsStableSubjectIdWithoutCreatingTask() {
    StructuredSubjectResolution resolution = resolver.resolve(
            PortfolioTurn.builder("turn-1", "question")
                    .projectSlug("project-a")
                    .build(),
            content());

    assertThat(resolution.getType())
            .isEqualTo(StructuredSubjectResolutionType.MATCHED);
    assertThat(resolution.getSubjectId()).isEqualTo("project-a-id");
}

@Test
void noSlugReturnsNoneAndUnknownOrDuplicateSlugReturnsInvalid() {
    assertThat(resolver.resolve(
            PortfolioTurn.builder("turn-none", "question").build(), content())
            .getType()).isEqualTo(StructuredSubjectResolutionType.NONE);
    assertThat(resolver.resolve(
            PortfolioTurn.builder("turn-missing", "question")
                    .projectSlug("missing").build(), content())
            .getType()).isEqualTo(StructuredSubjectResolutionType.INVALID);
}
```

分别覆盖 project、case、unknown 和 duplicate。

- [ ] **Step 2: 运行 resolver 测试确认 RED**

Run:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=StructuredSubjectResolverTest test
```

Expected: FAIL，新类和 `getSubjectId()` 尚不存在。

- [ ] **Step 3: 修改领域结果对象**

`StructuredSubjectResolution` 改为：

```java
public final class StructuredSubjectResolution {

    private final StructuredSubjectResolutionType type;
    private final String subjectId;

    private StructuredSubjectResolution(
            StructuredSubjectResolutionType type,
            String subjectId) {
        this.type = Objects.requireNonNull(type, "type");
        this.subjectId = subjectId == null || subjectId.isBlank()
                ? null : subjectId.trim();
    }

    public static StructuredSubjectResolution none() {
        return new StructuredSubjectResolution(
                StructuredSubjectResolutionType.NONE, null);
    }

    public static StructuredSubjectResolution matched(String subjectId) {
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId is required");
        }
        return new StructuredSubjectResolution(
                StructuredSubjectResolutionType.MATCHED, subjectId);
    }

    public static StructuredSubjectResolution invalid() {
        return new StructuredSubjectResolution(
                StructuredSubjectResolutionType.INVALID, null);
    }

    public StructuredSubjectResolutionType getType() { return type; }
    public String getSubjectId() { return subjectId; }
}
```

- [ ] **Step 4: 实现只解析 Scope 的 resolver**

`StructuredSubjectResolver.resolve` 保留现有 Project/Case 唯一匹配逻辑，成功分支只返回：

```java
return StructuredSubjectResolution.matched(matches.getFirst().getStableId());
```

不得 import 或构造 `PortfolioTask`、`PortfolioTaskMode`、`PortfolioConditions`。

- [ ] **Step 5: 删除旧 resolver 并运行测试**

Run:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=StructuredSubjectResolverTest test
```

Expected: PASS。

- [ ] **Step 6: 授权后提交检查点**

仅在获得用户明确 Git 授权后运行：

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/StructuredSubjectResolution.java backend/src/main/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectResolver.java backend/src/test/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectResolverTest.java
git add -u backend/src/main/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectTaskResolver.java backend/src/test/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectTaskResolverTest.java
git commit -m "重构：将结构化主体解析收敛为检索范围"
```

---

### Task 6: 调整 Portfolio Intelligence 的校验、规则和模型顺序

**Files:**

- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligence.java`
- Modify: `backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioIntelligenceConfiguration.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligenceRoutingTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioIntelligenceConfigurationTest.java`

**Interfaces:**

- Consumes: Task 5 的 `StructuredSubjectResolver` 与 `StructuredSubjectResolution.getSubjectId()`。
- Produces: validation → deterministic rule → model classification → model-off scoped fallback 的固定顺序。

- [ ] **Step 1: 把旧路由测试改成新行为 RED 测试**

至少新增/修改以下测试：

```java
@Test
void knownProjectSlugScopesModelClassifiedQuestion() {
    PortfolioTaskClassifierPort classifier = mock(PortfolioTaskClassifierPort.class);
    when(classifier.classifyPortfolioTask(
            eq("turn-1"), anyString(), isNull()))
            .thenReturn(ConversationModelResult.success(
                    new PortfolioTaskClassification(
                            PortfolioTaskMode.FACT_LOOKUP,
                            PortfolioConditions.empty(),
                            null,
                            0.95d)));
    AtomicReference<PortfolioRetrievalRequest> request = new AtomicReference<>();
    DefaultPortfolioIntelligence intelligence = intelligence(
            classifier, true, retrievalRequest -> {
                request.set(retrievalRequest);
                return retrieval();
            });

    PortfolioDecision decision = intelligence.tryResolve(
            PortfolioTurn.builder("turn-1", "Please explain it in detail")
                    .projectSlug("project-a")
                    .build());

    assertThat(decision.getMaterial()).get().satisfies(material ->
            assertThat(material.getIntentSource())
                    .isEqualTo(AnswerIntentSource.MODEL));
    assertThat(request.get().getStrategy())
            .isEqualTo(PortfolioRetrievalStrategy.SUBJECT_SCOPED_RELEVANCE);
    assertThat(request.get().getRequiredPortfolioIds())
            .containsExactly("project-a");
    verify(classifier).classifyPortfolioTask(
            eq("turn-1"), anyString(), isNull());
}
```

为该测试显式增加 `ConversationModelResult`、`PortfolioConditions`、`PortfolioTaskClassification`、`PortfolioTaskMode`、`PortfolioRetrievalRequest` import，以及 Mockito 的 `anyString`、`eq`、`isNull`、`verify`、`when` static import。

模型关闭 fallback：

```java
@Test
void knownSubjectFallsBackToScopedFactLookupWhenProviderIsDisabled() {
    PortfolioTaskClassifierPort classifier = mock(PortfolioTaskClassifierPort.class);
    DefaultPortfolioIntelligence intelligence = intelligence(classifier, false);

    PortfolioDecision decision = intelligence.tryResolve(
            PortfolioTurn.builder("turn-2", "Please explain it in detail")
                    .projectSlug("project-a").build());

    assertThat(decision.getMaterial()).get().satisfies(material ->
            assertThat(material.getIntentSource())
                    .isEqualTo(AnswerIntentSource.RULE));
    verifyNoInteractions(classifier);
}
```

未知主体必须早于规则：

```java
@Test
void unknownSubjectFailsBeforeDeterministicRuleClassifierAndRetriever() {
    PortfolioTaskClassifierPort classifier = mock(PortfolioTaskClassifierPort.class);
    PortfolioRetriever retriever = mock(PortfolioRetriever.class);
    DefaultPortfolioIntelligence intelligence = intelligence(
            classifier, true, retriever);

    PortfolioDecision decision = intelligence.tryResolve(
            PortfolioTurn.builder("turn-3", "怎么实现这个项目？")
                    .projectSlug("missing-project").build());

    assertThat(decision.getDisposition())
            .isEqualTo(PortfolioDisposition.INVALID_INPUT);
    verifyNoInteractions(classifier, retriever);
}
```

另加：已知 slug + 确定性规则不调模型；已知 slug + 模型判非作品集不检索；caseSlug 与 projectSlug 对称。

- [ ] **Step 2: 运行路由测试确认 RED**

Run:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=DefaultPortfolioIntelligenceRoutingTest,PortfolioIntelligenceConfigurationTest test
```

Expected: FAIL，当前 MATCHED 会直接执行 `RULE`，且未知 slug 仍位于确定性规则之后。

- [ ] **Step 3: 注入新 resolver**

配置类提供：

```java
@Bean
StructuredSubjectResolver structuredSubjectResolver() {
    return new StructuredSubjectResolver();
}
```

`DefaultPortfolioIntelligence` 构造器字段和测试 fixture 全部替换为 `StructuredSubjectResolver`。

- [ ] **Step 4: 实现 validation-first 路由**

在 Preset 分支之后、普通确定性规则之前解析 scope：

```java
StructuredSubjectResolution structured = structuredSubjectResolver.resolve(
        turn, content);
if (structured.getType() == StructuredSubjectResolutionType.INVALID) {
    return invalidInput(content, "STRUCTURED_SUBJECT_INVALID");
}
String subjectId = structured.getType() == StructuredSubjectResolutionType.MATCHED
        ? structured.getSubjectId()
        : null;

if (taskResolver.matchesDeterministicRule(turn.getQuestion())) {
    PortfolioTask task = taskResolver.resolve(
            turn.getTurnId(), turn.getQuestion(),
            turn.getRecommendationContext());
    return execute(withSubjectConstraint(task, subjectId),
            AnswerIntentSource.RULE, false);
}

if (providerAccess.isAllowed()) {
    PortfolioTaskRoutingDecision routed = taskResolver.route(
            turn.getTurnId(), turn.getQuestion(),
            turn.getRecommendationContext(), true);
    if (routed.isNotPortfolio() || routed.getBoundaryIntent() != null) {
        return new PortfolioDecision(PortfolioDisposition.NOT_PORTFOLIO, null);
    }
    return execute(withSubjectConstraint(routed.getTask(), subjectId),
            AnswerIntentSource.MODEL, false);
}

if (subjectId != null) {
    PortfolioTask fallback = new PortfolioTask(
            turn.getTurnId(), turn.getQuestion(),
            PortfolioTaskMode.FACT_LOOKUP, 1.0d,
            PortfolioConditions.empty(),
            turn.getRecommendationContext(), null);
    return execute(withSubjectConstraint(fallback, subjectId),
            AnswerIntentSource.RULE, false);
}
return new PortfolioDecision(PortfolioDisposition.NOT_PORTFOLIO, null);
```

把 `withSubjectConstraint` 改为只接受已经验证的 stable ID：

```java
private PortfolioTask withSubjectConstraint(
        PortfolioTask task,
        String subjectId) {
    if (subjectId == null) {
        return task;
    }
    return new PortfolioTask(
            task.getTurnId(), task.getQuestion(), task.getMode(),
            task.getConfidence(), task.getConditions(),
            task.getRecommendationContext(), task.getRefinement(),
            subjectId, task.getPreferredClaimCategories());
}
```

删除已失去调用方的 `referencesExplicitSubject`、`containsAny` 和基于 slug 二次查找主体的旧 helper。

- [ ] **Step 5: 运行路由与配置测试确认 GREEN**

Run:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=StructuredSubjectResolverTest,DefaultPortfolioIntelligenceRoutingTest,PortfolioIntelligenceConfigurationTest test
```

Expected: PASS；Mockito 交互断言证明 scope 校验不调用模型，意图分类只在 Provider gate 允许时发生。

- [ ] **Step 6: 授权后提交检查点**

仅在获得用户明确 Git 授权后运行：

```powershell
git add backend/src/main/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligence.java backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioIntelligenceConfiguration.java backend/src/test/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligenceRoutingTest.java backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioIntelligenceConfigurationTest.java
git commit -m "修复：解耦结构化主体校验与模型任务分类"
```

---

### Task 7: 用真实 Bundle 锁定默认 fallback、Contract 与主体边界

**Files:**

- Modify: `backend/src/test/java/com/portfolio/agent/answer/controller/PresetContractBundleIntegrationTest.java`
- Modify: `backend/src/test/java/com/portfolio/agent/answer/controller/CaseConversationBundleIntegrationTest.java`

**Interfaces:**

- Consumes: Task 6 的运行时顺序和当前 `2026-08-05.1` Bundle。
- Produces: 不调用真实 Provider的集成回归证据。

- [ ] **Step 1: 增加未知 slug 命中规则仍失败关闭的集成测试**

在 `CaseConversationBundleIntegrationTest` 增加一个带确定性短语的未知主体请求：

```java
mockMvc.perform(post("/api/v2/answers")
        .contentType(MediaType.APPLICATION_JSON)
        .content(request(
                "turn-unknown-rule",
                "unknown-case",
                "这个案例怎么实现？")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resolution").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.noticeCode")
                .value("STRUCTURED_SUBJECT_INVALID"))
        .andExpect(jsonPath("$.intentSource").value("RULE"));
```

调整 test helper 让 question 成为显式参数，不改变 UUID 生成方式。

- [ ] **Step 2: 保留模型关闭时的成功反例**

现有真实 Case：

```text
caseSlug=multilingual-image-preservation
question=这个案例如何验证？
```

继续断言：

```text
ANSWERED / RULE / EVIDENCE_COMPOSITION / VERIFIED / degraded=false
```

它证明 `DISABLED` 下关键词 fallback 可以成功，防止实现者误把默认配置改成“完全不检索”。

- [ ] **Step 3: 保留 Contract 与自由 suggestion 的边界**

`PresetContractBundleIntegrationTest` 必须继续覆盖：

- SQL Active Contract → `PRESET + VERIFIED`，不调用相关性检索或模型；
- role-reset 结构化 suggestion 在模型关闭时 → `RULE + VERIFIED`；
- stale Contract → `CAPABILITY_UNAVAILABLE`，禁止搜索 fallback。

不得为了让任意自由问题成功而把 `SUBJECT_SCOPED_RELEVANCE` 改成 `PRESET_CONTRACT`。

- [ ] **Step 4: 运行真实 Bundle 集成测试**

Run:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest=PresetContractBundleIntegrationTest,CaseConversationBundleIntegrationTest test
```

Expected: PASS；日志允许出现 `RETRIEVAL_EMBEDDING_DISABLED + KEYWORD_FALLBACK`，但成功场景必须为 `retrieval.decision=SUFFICIENT`。

- [ ] **Step 5: 运行回答模块回归**

Run:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml -Dtest='com.portfolio.agent.answer.**' test
```

Expected: PASS，无真实网络调用。

- [ ] **Step 6: 授权后提交检查点**

仅在获得用户明确 Git 授权后运行：

```powershell
git add backend/src/test/java/com/portfolio/agent/answer/controller/PresetContractBundleIntegrationTest.java backend/src/test/java/com/portfolio/agent/answer/controller/CaseConversationBundleIntegrationTest.java
git commit -m "测试：锁定结构化主体分类与证据边界"
```

---

### Task 8: 更新运行文档与架构状态

**Files:**

- Modify: `README.md`
- Modify: `docs/00-文档状态索引.md`
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/11-项目演进日志.md`
- Modify: `docs/12-工程质量与未来优化评审备忘录.md`

**Interfaces:**

- Consumes: Tasks 1–7 已通过的实际行为。
- Produces: 当前权威状态、运维语义和设计替代关系。

- [ ] **Step 1: 更新本地启动和发布门禁说明**

README 与 `docs/08-当前实现状态.md` 明确：

```text
Live Provider canary 不携带 Project/Case/Preset/Reference 上下文；
AI_CONNECTED 只在 MODEL + EVIDENCE_COMPOSITION + VERIFIED + ANSWERED 时输出；
PROBE_ROUTE_BYPASSED 表示探针契约漂移，不表示 Provider 返回非法。
```

`-RequireLiveProvider` 必须说明它运行独立 canary，不复用 Case smoke。

- [ ] **Step 2: 更新结构化主体与检索说明**

记录新的顺序：

```text
主体先校验为 scope；规则或模型再决定任务；模型关闭时才使用 scoped FACT_LOOKUP fallback。
```

把 `DISABLED` 统一解释为：

```text
本地向量查询关闭，关键词 fallback 仍可运行，最终由 Grounding Gate fail-closed。
```

- [ ] **Step 3: 更新状态索引、评审项和演进日志**

- 在 `docs/00-文档状态索引.md` 登记本设计与计划。
- 在 `docs/12-工程质量与未来优化评审备忘录.md` 仅在测试通过后把 Q-01 标记为已关闭，并写明“主体校验前移到普通规则之前”。
- 在 `docs/11-项目演进日志.md` 追加行为结果、与 `21dba32` 的关系和当前状态；不记录逐步命令或提交元数据。

- [ ] **Step 4: 执行文档占位符与秘密扫描**

Run:

```powershell
rg -n "[T]BD|[T]ODO|[待]补|稍后[补]充|实现见[上]文" README.md docs/00-文档状态索引.md docs/08-当前实现状态.md docs/11-项目演进日志.md docs/12-工程质量与未来优化评审备忘录.md docs/superpowers/specs/2026-08-05-live-provider-probe-and-structured-subject-routing-design.md docs/superpowers/plans/2026-08-05-live-provider-probe-and-structured-subject-routing.md
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1 -Path .
```

Expected: placeholder 搜索无输出；隐私检查 PASS。

- [ ] **Step 5: 授权后提交检查点**

仅在获得用户明确 Git 授权后运行：

```powershell
git add README.md docs/00-文档状态索引.md docs/08-当前实现状态.md docs/11-项目演进日志.md docs/12-工程质量与未来优化评审备忘录.md docs/superpowers/specs/2026-08-05-live-provider-probe-and-structured-subject-routing-design.md docs/superpowers/plans/2026-08-05-live-provider-probe-and-structured-subject-routing.md
git commit -m "文档：记录真实模型探针与主体路由纠偏"
```

---

### Task 9: 全量验证与真实 Provider 验收

**Files:**

- Verify only: Tasks 1–8 的全部变更。

**Interfaces:**

- Consumes: 完整修复、仓库外 Secret 文件和现有工具链。
- Produces: 确定性 CI 证据，以及授权时的真实 Provider 证据。

- [ ] **Step 1: 运行全部 PowerShell 契约测试**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/assert-live-provider-response.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provider-probe/invoke-live-provider-probe.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-jar-e2e.test.ps1
```

Expected: 全部 PASS，无残留 `portfolio-provider-probe-*` 文件、子进程或监听端口。

- [ ] **Step 2: 运行后端全量测试**

Run:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml test
```

Expected: `BUILD SUCCESS`，0 failures，0 errors。

- [ ] **Step 3: 运行前端回归与构建**

虽然 API Schema 不变，仍验证页面 handoff 和 slug 发送链：

```powershell
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run check
npm.cmd --prefix frontend run build
```

Expected: 全部 PASS。

- [ ] **Step 4: 运行打包与确定性发布验证**

Run:

```powershell
C:\tools\apache-maven-3.9.9\bin\mvn.cmd -f backend/pom.xml package
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-release.ps1 -SkipInstall
```

Expected: package 与 release verification PASS；普通路径明确不调用真实 Provider。

- [ ] **Step 5: 在显式授权后运行真实本地启动探针**

从操作者预先设置的进程环境变量取得仓库外绝对 Secret 文件；变量本身只保存路径，不保存 Key：

```powershell
$secretsFile = [Environment]::GetEnvironmentVariable(
    'PORTFOLIO_LOCAL_SECRETS_FILE',
    [EnvironmentVariableTarget]::Process)
if ([string]::IsNullOrWhiteSpace($secretsFile) -or
        -not [System.IO.Path]::IsPathRooted($secretsFile) -or
        -not (Test-Path -LiteralPath $secretsFile -PathType Leaf)) {
    throw 'PORTFOLIO_LOCAL_SECRETS_FILE must point to an existing absolute file.'
}
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1 `
    -SecretsFile $secretsFile `
    -ExitAfterProbe
```

Expected:

```text
LOCAL_CONFIG_VALID ... checks=6
AI_CONNECTED provider=<configured provider> ...
```

不得出现 `AI_DEGRADED:PROBE_ROUTE_BYPASSED` 或 Key 内容。

- [ ] **Step 6: 在显式授权后运行 packaged Live Provider 门禁**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-release.ps1 `
    -SkipInstall `
    -RequireLiveProvider
```

Expected:

```text
Packaged Case Agent smoke passed.
Packaged Live Provider verification passed.
```

两条证据必须分别出现。

- [ ] **Step 7: 复核工作树和临时资源**

Run:

```powershell
git status --short
Get-NetTCPConnection -State Listen | Where-Object LocalPort -In 8080,5173
Get-ChildItem -LiteralPath ([System.IO.Path]::GetTempPath()) `
    -Filter 'portfolio-provider-probe-*' -File
```

Expected: 只有本计划授权范围内的源码/文档差异；无测试残留进程、端口或 probe 文件。不得删除或改写用户原有差异。

- [ ] **Step 8: 授权后最终提交检查点**

只有用户已授权提交且前述全部门禁通过时运行：

```powershell
git add scripts/assert-live-provider-response.ps1 scripts/assert-live-provider-response.test.ps1 scripts/provider-probe/invoke-live-provider-probe.ps1 scripts/provider-probe/invoke-live-provider-probe.test.ps1 scripts/test-fixtures/start-local-fake-server.ps1 scripts/start-local.ps1 scripts/start-local.test.ps1 scripts/run-jar-e2e.ps1 scripts/run-jar-e2e.test.ps1 scripts/verify-release.ps1
git add backend/src/main/java/com/portfolio/agent/answer/intelligence/domain/StructuredSubjectResolution.java backend/src/main/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectResolver.java backend/src/main/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligence.java backend/src/main/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioIntelligenceConfiguration.java backend/src/test/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectResolverTest.java backend/src/test/java/com/portfolio/agent/answer/intelligence/service/DefaultPortfolioIntelligenceRoutingTest.java backend/src/test/java/com/portfolio/agent/answer/intelligence/adapter/PortfolioIntelligenceConfigurationTest.java backend/src/test/java/com/portfolio/agent/answer/controller/PresetContractBundleIntegrationTest.java backend/src/test/java/com/portfolio/agent/answer/controller/CaseConversationBundleIntegrationTest.java
git add -u backend/src/main/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectTaskResolver.java backend/src/test/java/com/portfolio/agent/answer/intelligence/service/StructuredSubjectTaskResolverTest.java
git add README.md docs/00-文档状态索引.md docs/08-当前实现状态.md docs/11-项目演进日志.md docs/12-工程质量与未来优化评审备忘录.md docs/superpowers/specs/2026-08-05-live-provider-probe-and-structured-subject-routing-design.md docs/superpowers/plans/2026-08-05-live-provider-probe-and-structured-subject-routing.md
git commit -m "修复：恢复真实模型探针与结构化主体自由问答"
```

提交前用 `git diff --cached --stat` 确认不包含用户无关文件；若各 Task 已分别提交，则本步骤不再创建重复提交。

---

## 执行检查表

- [ ] Task 1：断言失败码可区分且不泄漏秘密。
- [ ] Task 2：唯一 canary 不含任何产品主体或会话上下文。
- [ ] Task 3：本地启动真实连接与安全降级语义正确。
- [ ] Task 4：Case smoke 与 Live Provider 门禁完全拆分。
- [ ] Task 5：结构化主体解析只返回 stable ID scope。
- [ ] Task 6：主体先校验，规则/模型后分类，模型关闭有 fallback。
- [ ] Task 7：真实 Bundle、Contract、Case 和未知主体边界回归通过。
- [ ] Task 8：当前状态、评审项和运维说明同步。
- [ ] Task 9：全量门禁与显式 Live 验收通过。
