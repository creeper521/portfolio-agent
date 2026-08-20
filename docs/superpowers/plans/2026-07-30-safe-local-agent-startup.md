# Safe Local Agent Startup Implementation Plan
<!-- DOCUMENT_STATUS: NON_AUTHORITATIVE -->

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide one PowerShell command that safely loads repository-external model secrets, starts the Spring Boot backend and Vite frontend, and proves whether the conversational Provider is actually serving `MODEL` answers.

**Architecture:** Add a fail-closed `scripts/start-local.ps1` orchestrator with independently testable secret parsing, preflight, child-process ownership, readiness, and Provider probing. Reuse and strengthen the existing live-Provider response assertion instead of creating a second model-verification contract.

**Tech Stack:** PowerShell 5.1+, Java 21, Maven, Node.js, Vite, Spring Boot HTTP API.

## Global Constraints

- Never read `D:\code\agent\.env`; `-SecretsFile` must resolve to an absolute file outside the repository.
- Never print, persist, or pass through API keys in output, errors, logs, command-line arguments, or probe artifacts.
- The four approval flags, selected built-in Provider, and matching Provider key are all mandatory.
- Only a valid response with `generationMode=MODEL`, `degraded=false`, `resolution=ANSWERED`, and at least one block may be reported as “AI connected”.
- A failed Provider probe leaves the local frontend/backend running in an explicitly reported degraded state.
- Only processes created by this invocation may be stopped.
- Do not add dependencies or change the existing Provider registry.
- Preserve all user-owned worktree changes. Do not stage or commit without explicit user authorization.

## File Structure

- Create `scripts/start-local.ps1`: public local-development entry point; owns parsing, preflight, process lifecycle, readiness, and probe orchestration.
- Create `scripts/start-local.test.ps1`: isolated PowerShell contract tests with temporary secret files, fake child processes, and a local fake HTTP listener.
- Modify `scripts/assert-live-provider-response.ps1`: require `generationMode=MODEL`.
- Modify `scripts/assert-live-provider-response.test.ps1`: cover missing and non-`MODEL` generation mode.
- Modify `README.md`: replace implicit `.env` expectations with the explicit local launcher.
- Modify `docs/08-当前实现状态.md`: record the new local startup behavior and its production boundary.
- Modify `docs/11-项目演进日志.md`: record the developer-experience and fail-closed configuration change.

---

### Task 1: Make live-Provider verification prove model generation

**Files:**
- Modify: `scripts/assert-live-provider-response.test.ps1`
- Modify: `scripts/assert-live-provider-response.ps1`

**Interfaces:**
- Consumes: response JSON written by a real `/api/v2/answers` call.
- Produces: exit `0` only for `generationMode=MODEL`; all deterministic/fallback responses exit nonzero without leaking response content.

- [ ] **Step 1: Extend the fixture and add failing assertions**

Change `Write-ResponseFixture` to accept generation mode:

```powershell
function Write-ResponseFixture(
    [string]$ContentVersion = $expectedContentVersion,
    [bool]$Degraded = $false,
    [string]$Resolution = 'ANSWERED',
    [string]$GenerationMode = 'MODEL',
    [object[]]$Blocks = @([pscustomobject]@{ content = $contentSentinel })
) {
    $response = [pscustomobject]@{
        contentVersion = $ContentVersion
        degraded = $Degraded
        resolution = $Resolution
        generationMode = $GenerationMode
        blocks = $Blocks
    }
    [System.IO.File]::WriteAllText(
        $responsePath,
        ($response | ConvertTo-Json -Depth 8 -Compress),
        [System.Text.UTF8Encoding]::new($false)
    )
}
```

Add these cases before the final success output:

```powershell
foreach ($mode in @($null, '', 'DETERMINISTIC', 'FALLBACK')) {
    Set-ApprovedEnvironment 'DEEPSEEK_V4_FLASH'
    Write-ResponseFixture -GenerationMode $mode
    $result = Invoke-Checker
    Assert-True ($result.ExitCode -ne 0) "generationMode=$mode must fail."
    Assert-NoSensitiveOutput $result "generationMode=$mode"
}
```

- [ ] **Step 2: Run the checker test and verify RED**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/assert-live-provider-response.test.ps1
```

Expected: FAIL because the current checker accepts at least `DETERMINISTIC` or `FALLBACK`.

- [ ] **Step 3: Require exact model generation**

Add after the `degraded` assertion in `assert-live-provider-response.ps1`:

```powershell
if ($response.generationMode -cne 'MODEL') {
    throw 'generation mode'
}
```

Change the success message to:

```powershell
Write-Output "Live Provider verification passed: provider=$provider; contentVersion=$ExpectedContentVersion; generationMode=MODEL; resolution=ANSWERED; blocks=$($response.blocks.Count)."
```

- [ ] **Step 4: Run the checker test and verify GREEN**

Run the Step 2 command.

Expected: `assert-live-provider-response tests passed`.

- [ ] **Step 5: Commit only after explicit authorization**

Suggested commit:

```powershell
git add -- scripts/assert-live-provider-response.ps1 scripts/assert-live-provider-response.test.ps1
git commit -m "测试：严格验证真实模型回答"
```

---

### Task 2: Add repository-external secret parsing and fail-closed preflight

**Files:**
- Create: `scripts/start-local.ps1`
- Create: `scripts/start-local.test.ps1`

**Interfaces:**
- Consumes: `-SecretsFile <absolute path outside repository>`.
- Produces: `Read-LocalSecrets([string]) -> hashtable`, `Assert-LocalConfiguration([hashtable])`, and a public `-CheckOnly` mode that exits `0` without starting processes.

- [ ] **Step 1: Write parser and preflight contract tests**

Create `scripts/start-local.test.ps1` with a temporary root and helpers:

```powershell
$ErrorActionPreference = 'Stop'
$launcher = Join-Path $PSScriptRoot 'start-local.ps1'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('portfolio-start-local-' + [guid]::NewGuid().ToString('N'))
$keySentinel = 'key-' + [guid]::NewGuid().ToString('N')

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Write-Secrets([string]$Path, [string[]]$Lines) {
    [System.IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) | Out-Null
    [System.IO.File]::WriteAllLines(
        $Path, $Lines, [System.Text.UTF8Encoding]::new($false))
}

function Invoke-Launcher([string]$SecretsFile) {
    $output = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File $launcher -SecretsFile $SecretsFile -CheckOnly 2>&1 | Out-String)
    return @{ ExitCode = $LASTEXITCODE; Output = $output }
}

function Valid-Lines {
    return @(
        'PORTFOLIO_MODEL_ENABLED=true',
        'PORTFOLIO_MODEL_DATA_POLICY_APPROVED=true',
        'PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED=true',
        'PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED=true',
        'PORTFOLIO_MODEL_PROVIDER=DEEPSEEK_V4_FLASH',
        "PORTFOLIO_AGENT_DEEPSEEK_API_KEY=$keySentinel"
    )
}
```

Test the following table:

```powershell
try {
    $valid = Join-Path $fixtureRoot 'valid.env'
    Write-Secrets $valid (Valid-Lines)
    $result = Invoke-Launcher $valid
    Assert-True ($result.ExitCode -eq 0) 'Valid external secrets must pass.'
    Assert-True ($result.Output -notmatch [regex]::Escape($keySentinel)) `
        'Launcher leaked the key.'

    foreach ($invalidLines in @(
        @('PORTFOLIO_MODEL_ENABLED=true', 'PORTFOLIO_MODEL_ENABLED=true'),
        @('NOT_ALLOWED=value'),
        @('PORTFOLIO_MODEL_ENABLED=$(Get-ChildItem)'),
        @('missing-separator')
    )) {
        $path = Join-Path $fixtureRoot ([guid]::NewGuid().ToString('N') + '.env')
        Write-Secrets $path $invalidLines
        $result = Invoke-Launcher $path
        Assert-True ($result.ExitCode -ne 0) 'Invalid secret input must fail.'
    }

    $repositorySecret = Join-Path (Split-Path -Parent $PSScriptRoot) 'forbidden.env'
    Write-Secrets $repositorySecret (Valid-Lines)
    try {
        $result = Invoke-Launcher $repositorySecret
        Assert-True ($result.ExitCode -ne 0) 'Repository-local secrets must fail.'
    }
    finally {
        Remove-Item -LiteralPath $repositorySecret -Force
    }
    Write-Output 'start-local preflight tests passed'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
}
```

- [ ] **Step 2: Run the new test and verify RED**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.test.ps1
```

Expected: FAIL because `scripts/start-local.ps1` does not exist.

- [ ] **Step 3: Implement parameters, parser, and validation**

Create `scripts/start-local.ps1` with:

```powershell
param(
    [Parameter(Mandatory = $true)]
    [string]$SecretsFile,
    [switch]$CheckOnly,
    [ValidateRange(1, 65535)][int]$BackendPort = 8080,
    [ValidateRange(1, 65535)][int]$FrontendPort = 5173,
    [string]$MavenExecutable = 'mvn.cmd',
    [string]$NpmExecutable = 'npm.cmd'
)

> **补充说明（2026-08-04）：** 该计划已执行；前端默认端口此后由 5174 统一为 5173 并强制
> `--strictPort`，直接前端启动日志落盘由 [Direct Vite Local Logging Design](../specs/2026-08-04-direct-vite-local-logging-design.md) 接替。

$ErrorActionPreference = 'Stop'
$script:repositoryRoot = Split-Path -Parent $PSScriptRoot
$script:allowedNames = @(
    'PORTFOLIO_MODEL_ENABLED',
    'PORTFOLIO_MODEL_DATA_POLICY_APPROVED',
    'PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED',
    'PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED',
    'PORTFOLIO_MODEL_PROVIDER',
    'PORTFOLIO_AGENT_DEEPSEEK_API_KEY',
    'PORTFOLIO_AGENT_GLM_API_KEY',
    'PORTFOLIO_MODEL_TIMEOUT',
    'PORTFOLIO_MODEL_MAX_TOKENS'
)

function Stop-WithCode([string]$Code) { throw $Code }

function Test-IsChildPath([string]$Parent, [string]$Candidate) {
    $prefix = [System.IO.Path]::GetFullPath($Parent).TrimEnd('\') + '\'
    return [System.IO.Path]::GetFullPath($Candidate).StartsWith(
        $prefix, [System.StringComparison]::OrdinalIgnoreCase)
}

function Read-LocalSecrets([string]$Path) {
    if (-not [System.IO.Path]::IsPathRooted($Path) -or
            -not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Stop-WithCode 'LOCAL_CONFIG_FILE_INVALID'
    }
    $resolved = (Resolve-Path -LiteralPath $Path).Path
    if (Test-IsChildPath $script:repositoryRoot $resolved) {
        Stop-WithCode 'LOCAL_CONFIG_MUST_BE_OUTSIDE_REPOSITORY'
    }
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $resolved -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -le 0) { Stop-WithCode 'LOCAL_CONFIG_FORMAT_INVALID' }
        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1)
        if ($name -notin $script:allowedNames -or $values.ContainsKey($name)) {
            Stop-WithCode 'LOCAL_CONFIG_FIELD_INVALID'
        }
        if ($value -match '(`|\$\(|\$\{|;&|\|\||&&)') {
            Stop-WithCode 'LOCAL_CONFIG_VALUE_INVALID'
        }
        $values[$name] = $value
    }
    return $values
}

function Assert-TrueFlag([hashtable]$Values, [string]$Name) {
    if (-not $Values.ContainsKey($Name) -or
            -not [string]::Equals(
                [string]$Values[$Name], 'true',
                [System.StringComparison]::OrdinalIgnoreCase)) {
        Stop-WithCode "LOCAL_CONFIG_REQUIRED_FLAG_MISSING:$Name"
    }
}

function Assert-LocalConfiguration([hashtable]$Values) {
    foreach ($name in @(
        'PORTFOLIO_MODEL_ENABLED',
        'PORTFOLIO_MODEL_DATA_POLICY_APPROVED',
        'PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED',
        'PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED')) {
        Assert-TrueFlag $Values $name
    }
    $provider = [string]$Values.PORTFOLIO_MODEL_PROVIDER
    $keyName = switch ($provider) {
        'DEEPSEEK_V4_FLASH' { 'PORTFOLIO_AGENT_DEEPSEEK_API_KEY' }
        'GLM_4_7' { 'PORTFOLIO_AGENT_GLM_API_KEY' }
        default { Stop-WithCode 'LOCAL_CONFIG_PROVIDER_INVALID' }
    }
    if (-not $Values.ContainsKey($keyName) -or
            [string]::IsNullOrWhiteSpace([string]$Values[$keyName])) {
        Stop-WithCode "LOCAL_CONFIG_PROVIDER_KEY_MISSING:$keyName"
    }
}

function Assert-Command([string]$Command, [string]$Code) {
    $resolved = Get-Command $Command -ErrorAction SilentlyContinue
    if ($null -eq $resolved) { Stop-WithCode $Code }
    return $resolved.Source
}

function Assert-Toolchain {
    $java = Assert-Command 'java.exe' 'LOCAL_JAVA_MISSING'
    $maven = Assert-Command $MavenExecutable 'LOCAL_MAVEN_MISSING'
    $node = Assert-Command 'node.exe' 'LOCAL_NODE_MISSING'
    $npm = Assert-Command $NpmExecutable 'LOCAL_NPM_MISSING'
    $javaVersion = (& $java -version 2>&1 | Select-Object -First 1 | Out-String)
    if ($javaVersion -notmatch 'version "(?:1\.)?21(?:[.\-_"]|$)') {
        Stop-WithCode 'LOCAL_JAVA_21_REQUIRED'
    }
    if (-not (Test-Path -LiteralPath
            (Join-Path $script:repositoryRoot 'frontend\node_modules') -PathType Container)) {
        Stop-WithCode 'LOCAL_FRONTEND_DEPENDENCIES_MISSING'
    }
    return @{ Maven = $maven; Node = $node; Npm = $npm }
}

try {
    $settings = Read-LocalSecrets $SecretsFile
    Assert-LocalConfiguration $settings
    $toolchain = Assert-Toolchain
    Write-Output "Local AI configuration valid: provider=$($settings.PORTFOLIO_MODEL_PROVIDER)."
    if ($CheckOnly) { exit 0 }
    Stop-WithCode 'LOCAL_ORCHESTRATION_NOT_AVAILABLE'
}
catch {
    Write-Error ([string]$_.Exception.Message)
    exit 1
}
```

- [ ] **Step 4: Run the new test and verify GREEN**

Run the Step 2 command.

Expected: `start-local preflight tests passed`.

The test harness supplies tiny fake `java.exe`, Maven, Node, and npm commands through
a temporary `PATH`, plus a temporary repository fixture with
`frontend/node_modules`. Add negative cases for missing Java, Java 17, missing
Maven, missing npm, and missing `frontend/node_modules`; assert the exact
non-sensitive codes shown in `Assert-Toolchain`.

- [ ] **Step 5: Commit only after explicit authorization**

Suggested commit:

```powershell
git add -- scripts/start-local.ps1 scripts/start-local.test.ps1
git commit -m "工具：增加本地模型配置预检"
```

---

### Task 3: Orchestrate owned child processes, readiness, and Provider probe

**Files:**
- Modify: `scripts/start-local.ps1`
- Modify: `scripts/start-local.test.ps1`
- Create: `scripts/test-fixtures/start-local-fake-server.ps1`

**Interfaces:**
- Consumes: validated settings from Task 2.
- Produces: owned backend/frontend processes; `Wait-ForHttp([string],[System.Diagnostics.Process],int)`; `Invoke-ProviderProbe([string],[string])`; deterministic cleanup in `finally`.

- [ ] **Step 1: Add a deterministic fake server and failing orchestration tests**

Create `scripts/test-fixtures/start-local-fake-server.ps1`:

```powershell
param(
    [Parameter(Mandatory = $true)][int]$Port,
    [ValidateSet('BACKEND_MODEL', 'BACKEND_FALLBACK', 'FRONTEND')]
    [string]$Mode
)
$ErrorActionPreference = 'Stop'
$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add("http://127.0.0.1:$Port/")
$listener.Start()
try {
    while ($listener.IsListening) {
        $context = $listener.GetContext()
        $path = $context.Request.Url.AbsolutePath
        if ($Mode -eq 'FRONTEND') {
            $body = '<!doctype html><title>fake vite</title>'
            $contentType = 'text/html; charset=utf-8'
        }
        elseif ($path -eq '/api/v1/public-content') {
            $body = '{"contentVersion":"test-v1"}'
            $contentType = 'application/json; charset=utf-8'
        }
        elseif ($path -eq '/api/v2/answers') {
            $generationMode = if ($Mode -eq 'BACKEND_MODEL') {
                'MODEL'
            } else {
                'FALLBACK'
            }
            $degraded = if ($generationMode -eq 'MODEL') { 'false' } else { 'true' }
            $body = '{"contentVersion":"test-v1","generationMode":"' `
                + $generationMode + '","degraded":' + $degraded `
                + ',"resolution":"ANSWERED","blocks":[{"content":"fixture"}]}'
            $contentType = 'application/json; charset=utf-8'
        }
        else {
            $context.Response.StatusCode = 404
            $body = '{}'
            $contentType = 'application/json; charset=utf-8'
        }
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
        $context.Response.ContentType = $contentType
        $context.Response.ContentLength64 = $bytes.Length
        $context.Response.OutputStream.Write($bytes, 0, $bytes.Length)
        $context.Response.Close()
    }
}
finally {
    $listener.Close()
}
```

Add internal test-seam parameters to the launcher invocation:

```powershell
-BackendFixtureMode BACKEND_MODEL `
-FrontendFixture `
-ExitAfterProbe
```

These parameters are permitted only when the process environment contains
`PORTFOLIO_START_LOCAL_TEST_MODE=true`; otherwise the launcher fails with
`LOCAL_TEST_SEAM_FORBIDDEN`. The launcher starts the fixture script through
`powershell.exe` instead of Maven/npm when that flag is present.

Invoke:

```powershell
$env:PORTFOLIO_START_LOCAL_TEST_MODE = 'true'
$result = & powershell.exe -NoProfile -ExecutionPolicy Bypass `
    -File $launcher `
    -SecretsFile $valid `
    -BackendPort $backendPort `
    -FrontendPort $frontendPort `
    -BackendFixtureMode BACKEND_MODEL `
    -FrontendFixture `
    -ExitAfterProbe 2>&1 | Out-String
$env:PORTFOLIO_START_LOCAL_TEST_MODE = $null
```

Assert:

```powershell
Assert-True ($LASTEXITCODE -eq 0) 'MODEL probe fixture must pass.'
Assert-True ($result -match 'AI_CONNECTED') 'Connected status was not printed.'
Assert-True ($result -notmatch [regex]::Escape($keySentinel)) 'Key leaked.'
Assert-True (-not (Test-NetConnection 127.0.0.1 -Port $backendPort `
    -InformationLevel Quiet)) 'Owned backend port survived ExitAfterProbe.'
Assert-True (-not (Test-NetConnection 127.0.0.1 -Port $frontendPort `
    -InformationLevel Quiet)) 'Owned frontend port survived ExitAfterProbe.'
```

Add a second invocation with `-BackendFixtureMode BACKEND_FALLBACK`; expect exit
`0`, output `AI_DEGRADED:PROVIDER_RESPONSE_INVALID`, and both ports closed by
`-ExitAfterProbe`. Restore `PORTFOLIO_START_LOCAL_TEST_MODE` in a `finally`
block. For the occupied-port case, start the fixture server before invoking the
launcher, expect `LOCAL_PORT_OCCUPIED:<port>`, then issue a request to the
fixture and assert it still returns `200` before the test stops its own fixture
process.

- [ ] **Step 2: Run orchestration tests and verify RED**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.test.ps1
```

Expected: FAIL because orchestration parameters and functions do not exist.

- [ ] **Step 3: Add process and readiness helpers**

Add parameters:

```powershell
[switch]$ExitAfterProbe,
[ValidateRange(1, 300)][int]$ReadinessTimeoutSeconds = 60,
[ValidateSet('', 'BACKEND_MODEL', 'BACKEND_FALLBACK')]
[string]$BackendFixtureMode = '',
[switch]$FrontendFixture
```

Before using either fixture parameter:

```powershell
$testMode = [string]::Equals(
    $env:PORTFOLIO_START_LOCAL_TEST_MODE, 'true',
    [System.StringComparison]::OrdinalIgnoreCase)
if (($BackendFixtureMode -ne '' -or $FrontendFixture) -and -not $testMode) {
    Stop-WithCode 'LOCAL_TEST_SEAM_FORBIDDEN'
}
```

Reuse `Assert-Command` from Task 2 and add:

```powershell
function Assert-PortAvailable([int]$Port) {
    $listener = [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().
        GetActiveTcpListeners() | Where-Object { $_.Port -eq $Port }
    if ($null -ne $listener) { Stop-WithCode "LOCAL_PORT_OCCUPIED:$Port" }
}

function Wait-ForHttp(
    [string]$Uri,
    [System.Diagnostics.Process]$Process,
    [int]$TimeoutSeconds
) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $Process.Refresh()
        if ($Process.HasExited) { Stop-WithCode 'LOCAL_CHILD_EXITED_BEFORE_READY' }
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 2
            if ($response.StatusCode -eq 200) { return $response }
        }
        catch {}
        Start-Sleep -Milliseconds 200
    }
    Stop-WithCode 'LOCAL_READINESS_TIMEOUT'
}

function Stop-OwnedProcess([System.Diagnostics.Process]$Process) {
    if ($null -eq $Process) { return }
    $Process.Refresh()
    if (-not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force
        $Process.WaitForExit(5000) | Out-Null
    }
}
```

- [ ] **Step 4: Start backend/frontend with child-only environment**

Snapshot each allowed process environment variable, set the validated values
only around the backend `Start-Process`, and restore the launcher process
environment immediately afterward. Use these helpers:

```powershell
function Set-TemporaryProcessEnvironment([hashtable]$Values) {
    $snapshot = @{}
    foreach ($name in $script:allowedNames) {
        $snapshot[$name] = [Environment]::GetEnvironmentVariable(
            $name, [EnvironmentVariableTarget]::Process)
        [Environment]::SetEnvironmentVariable(
            $name,
            $(if ($Values.ContainsKey($name)) { [string]$Values[$name] } else { $null }),
            [EnvironmentVariableTarget]::Process)
    }
    return $snapshot
}

function Restore-ProcessEnvironment([hashtable]$Snapshot) {
    foreach ($name in $script:allowedNames) {
        [Environment]::SetEnvironmentVariable(
            $name, $Snapshot[$name], [EnvironmentVariableTarget]::Process)
    }
}
```

Wrap only the backend `Start-Process` call in `try/finally`:

```powershell
$environmentSnapshot = Set-TemporaryProcessEnvironment $settings
try {
    $backend = Start-Process -FilePath $backendExecutable `
        -ArgumentList $backendArguments -WorkingDirectory $script:repositoryRoot `
        -PassThru -WindowStyle Hidden
}
finally {
    Restore-ProcessEnvironment $environmentSnapshot
}
```

Use:

```powershell
$backendExecutable = if ($BackendFixtureMode -ne '') {
    'powershell.exe'
} else {
    $maven
}
$backendArguments = if ($BackendFixtureMode -ne '') {
    @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
      (Join-Path $PSScriptRoot 'test-fixtures\start-local-fake-server.ps1'),
      '-Port', "$BackendPort", '-Mode', $BackendFixtureMode)
} else {
    @('-f', (Join-Path $script:repositoryRoot 'backend\pom.xml'),
      'spring-boot:run', '-Dspring-boot.run.profiles=local',
      "-Dspring-boot.run.arguments=--server.port=$BackendPort")
}
$backend = Start-Process -FilePath $backendExecutable `
    -ArgumentList $backendArguments -WorkingDirectory $script:repositoryRoot `
    -PassThru -WindowStyle Hidden

$frontendExecutable = if ($FrontendFixture) { 'powershell.exe' } else { $npm }
$frontendArguments = if ($FrontendFixture) {
    @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
      (Join-Path $PSScriptRoot 'test-fixtures\start-local-fake-server.ps1'),
      '-Port', "$FrontendPort", '-Mode', 'FRONTEND')
} else {
    @('--prefix', (Join-Path $script:repositoryRoot 'frontend'),
      'run', 'dev', '--', '--host', '127.0.0.1',
      '--port', "$FrontendPort")
}
$frontend = Start-Process -FilePath $frontendExecutable `
    -ArgumentList $frontendArguments -WorkingDirectory $script:repositoryRoot `
    -PassThru -WindowStyle Hidden
```

Place all waits and monitoring inside:

```powershell
try {
    # readiness and probe
}
finally {
    Stop-OwnedProcess $frontend
    Stop-OwnedProcess $backend
}
```

- [ ] **Step 5: Implement the fixed Provider probe**

After `GET /api/v1/public-content` is ready, extract `contentVersion`. Post a fixed approved project question:

```powershell
$probe = @{
    turnId = 'local-provider-probe'
    requestToken = [guid]::NewGuid()
    question = '请详细介绍 SQL 审计与故障排查工具项目。'
    messages = @()
    context = @{
        projectSlug = 'sql-audit'
        caseSlug = $null
        audienceRole = 'INTERVIEWER'
        source = 'AGENT_PAGE'
    }
} | ConvertTo-Json -Depth 6 -Compress
```

Write only the JSON response to a GUID-named temp file, invoke:

```powershell
& powershell.exe -NoProfile -ExecutionPolicy Bypass `
    -File (Join-Path $PSScriptRoot 'assert-live-provider-response.ps1') `
    -ResponsePath $probePath `
    -ExpectedContentVersion $contentVersion
```

Always delete the temp response in `finally`. On success output:

```text
AI_CONNECTED provider=<provider> backend=http://127.0.0.1:<port> frontend=http://127.0.0.1:<port>
```

On assertion failure, inspect only the safe `noticeCode` and map it to one of
`PROVIDER_AUTH_FAILED`, `PROVIDER_TIMEOUT`, `PROVIDER_UNAVAILABLE`,
`PROVIDER_RESPONSE_INVALID`, `PROVIDER_DRAFT_REJECTED`, or
`PROVIDER_POLICY_INCOMPATIBLE`; unknown/missing codes map to
`PROVIDER_RESPONSE_INVALID`. Output `AI_DEGRADED:<category>`, keep both
processes running, and do not echo the response. If `-ExitAfterProbe` is
present, return after status output so tests can verify cleanup.

- [ ] **Step 6: Monitor both processes until Ctrl+C or child exit**

Poll both owned `Process` objects every 250ms. If one exits, report `LOCAL_CHILD_EXITED:<BACKEND|FRONTEND>` and let `finally` stop the other. PowerShell interruption must also enter `finally`.

- [ ] **Step 7: Run orchestration tests and verify GREEN**

Run the Step 2 command.

Expected: `start-local tests passed`.

- [ ] **Step 8: Run existing script regression tests**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/assert-live-provider-response.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-jar-e2e.test.ps1
```

Expected: both scripts print their pass message and exit `0`.

- [ ] **Step 9: Commit only after explicit authorization**

Suggested commit:

```powershell
git add -- scripts/start-local.ps1 scripts/start-local.test.ps1 scripts/test-fixtures/start-local-fake-server.ps1
git commit -m "工具：一键启动并验证本地 Agent"
```

---

### Task 4: Document the secure startup contract and run final verification

**Files:**
- Modify: `README.md`
- Modify: `docs/08-当前实现状态.md`
- Modify: `docs/11-项目演进日志.md`

**Interfaces:**
- Consumes: final launcher behavior.
- Produces: copy-pasteable external-secret setup and authoritative capability status.

- [ ] **Step 1: Add the documented command and external Secret example**

Add to README:

```powershell
scripts/start-local.ps1 `
  -SecretsFile C:\Users\<you>\.portfolio-agent\local-model.env
```

Document the six mandatory values, state that root `.env` is never loaded, and state that `AI_CONNECTED` requires a successful live `MODEL` probe. Do not include a real key or repository-local example path.

- [ ] **Step 2: Update current status and evolution log**

In `docs/08-当前实现状态.md`, record that model code remains fail-closed by default and local AI startup now has an explicit verified launcher. In `docs/11-项目演进日志.md`, add a 2026-07-30 entry describing the replacement of implicit `.env` expectations with repository-external secrets and a live probe.

- [ ] **Step 3: Run focused tests**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.test.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/assert-live-provider-response.test.ps1
```

Expected: both pass.

- [ ] **Step 4: Run static safety checks**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/privacy-check.ps1
git diff --check
```

Expected: privacy check passes and `git diff --check` exits `0`.

- [ ] **Step 5: Perform an explicit local live smoke**

With a repository-external Secret file:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts/start-local.ps1 `
  -SecretsFile C:\Users\<you>\.portfolio-agent\local-model.env
```

Expected: both URLs become ready and output includes either `AI_CONNECTED` or a non-secret `AI_DEGRADED:<category>`. Only `AI_CONNECTED` satisfies the live-Provider acceptance criterion.

- [ ] **Step 6: Commit only after explicit authorization**

Suggested commit:

```powershell
git add -- README.md docs/08-当前实现状态.md docs/11-项目演进日志.md
git commit -m "文档：说明安全本地 Agent 启动流程"
```
