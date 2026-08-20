$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$runner = Join-Path $PSScriptRoot 'run-agent-behavior-audit.ps1'
$runnerSource = Get-Content -LiteralPath $runner -Raw
if ($runnerSource -notmatch "test:e2e:behavior") {
    throw 'Behavior runner must invoke the dedicated behavior Playwright script.'
}
if ($runnerSource -notmatch "--project=api-l0" -or
        $runnerSource -notmatch "--project=runtime") {
    throw 'Behavior runner must select explicit L0 and runtime Playwright projects.'
}
foreach ($retiredSetting in @(
        'P3_REAL_API',
        'PORTFOLIO_SEMANTIC_CLASSIFIER_ENABLED',
        'PORTFOLIO_MODEL_OP_ROUTING'
    )) {
    if ($runnerSource -match [regex]::Escape($retiredSetting)) {
        throw "Behavior runner must not use retired setting $retiredSetting."
    }
}
foreach ($turnInterpretationSetting in @(
        'PORTFOLIO_MODEL_OP_TURN_INTERPRETATION_MODE',
        'PORTFOLIO_MODEL_OP_TURN_INTERPRETATION_PROVIDER_REF',
        'PORTFOLIO_MODEL_OP_TURN_INTERPRETATION_SCHEMA_VERSION'
    )) {
    if ($runnerSource -notmatch [regex]::Escape($turnInterpretationSetting)) {
        throw "Behavior runner must configure current setting $turnInterpretationSetting."
    }
}

$fakeServer = Join-Path $PSScriptRoot 'test-fixtures\start-local-fake-server.ps1'
$fakeServerSource = Get-Content -LiteralPath $fakeServer -Raw
if ($fakeServerSource -match '/api/v2(?:/answers)?') {
    throw 'Local fake server must not expose the retired /api/v2 branch.'
}
if ($fakeServerSource -notmatch [regex]::Escape('/api/agent/turns')) {
    throw 'Local fake server must preserve the final /api/agent/turns branch.'
}

function Assert-Throws([scriptblock]$Action, [string]$Message) {
    $thrown = $false
    try { & $Action } catch { $thrown = $true }
    if (-not $thrown) { throw $Message }
}

Assert-Throws {
    & $runner -Lane L4
} 'L4 without explicit authorization must fail before startup.'

Assert-Throws {
    & $runner -Lane L4 -RequireLiveProvider -ProviderSecretFile (Join-Path $env:TEMP 'missing-provider-secret')
} 'L4 with a missing secret file must fail closed.'

$original = Get-Item -LiteralPath 'Env:PORTFOLIO_AGENT_MODEL_PROVIDER' -ErrorAction SilentlyContinue
$originalValue = if ($null -ne $original) { $original.Value } else { $null }
try {
    $env:PORTFOLIO_AGENT_MODEL_PROVIDER = 'TEST_SENTINEL'
    Assert-Throws {
        & $runner -Lane L0 -JarPath (Join-Path $env:TEMP 'missing-agent.jar')
    } 'Missing JAR must be rejected before starting a process.'
    $after = Get-Item -LiteralPath 'Env:PORTFOLIO_AGENT_MODEL_PROVIDER' -ErrorAction SilentlyContinue
    if ($null -eq $after -or $after.Value -ne 'TEST_SENTINEL') {
        throw 'Runner did not preserve the caller environment after preflight failure.'
    }
} finally {
    if ($null -eq $original) { Remove-Item -LiteralPath 'Env:PORTFOLIO_AGENT_MODEL_PROVIDER' -ErrorAction SilentlyContinue }
    else { Set-Item -LiteralPath 'Env:PORTFOLIO_AGENT_MODEL_PROVIDER' -Value $originalValue }
}

Write-Output 'Agent behavior audit runner contract passed.'
