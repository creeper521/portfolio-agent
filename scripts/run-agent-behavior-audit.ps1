param(
    [ValidateSet('L0', 'L1', 'L2', 'L3', 'L4')]
    [string[]]$Lane = @('L0', 'L1', 'L2', 'L3'),
    [switch]$RequireLiveProvider,
    [ValidateSet('glm-4-7-flash', 'qwen-3-7-flash')]
    [string]$LiveModelRef = 'glm-4-7-flash',
    [ValidateRange(1, 4)]
    [int]$ProviderSamplingRounds = 2,
    [ValidateRange(0, 60000)]
    [int]$ProviderInterRoundDelayMilliseconds = 10000,
    [string]$JarPath = '',
    [string]$ProviderSecretFile = '',
    [ValidateSet('IN_MEMORY', 'POSTGRESQL')]
    [string]$ContextMode = 'IN_MEMORY',
    [ValidateRange(1, 65535)]
    [int]$Port = 4173,
    [string]$MavenExecutable = 'mvn.cmd',
    [string]$JavaExecutable = 'java.exe'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = if ([string]::IsNullOrWhiteSpace($JarPath)) {
    Join-Path $root 'backend\target\portfolio-agent.jar'
} else {
    [System.IO.Path]::GetFullPath($JarPath)
}

if ($Lane -contains 'L4' -and -not $RequireLiveProvider) {
    throw 'L4 requires explicit -RequireLiveProvider authorization.'
}
if ($Lane -contains 'L4' -and [string]::IsNullOrWhiteSpace($ProviderSecretFile)) {
    throw 'L4 requires an outside-repository provider secret file.'
}
if ($Lane -contains 'L4' -and -not (Test-Path -LiteralPath $ProviderSecretFile -PathType Leaf)) {
    throw 'L4 provider secret file is missing.'
}
if (($Lane | Select-Object -Unique).Count -ne $Lane.Count) {
    throw 'Duplicate behavior lanes are not allowed.'
}

function Get-EnvSnapshot([string]$Name) {
    $item = Get-Item -LiteralPath "Env:$Name" -ErrorAction SilentlyContinue
    return @{
        Exists = $null -ne $item
        Value = if ($null -ne $item) { [string]$item.Value } else { $null }
    }
}

function Restore-Env([string]$Name, [hashtable]$Snapshot) {
    if ($Snapshot.Exists) {
        Set-Item -LiteralPath "Env:$Name" -Value $Snapshot.Value
    } else {
        Remove-Item -LiteralPath "Env:$Name" -ErrorAction SilentlyContinue
    }
}

function Assert-Command([string]$Command, [string]$Label) {
    if ($Command -match '[\\/]') {
        if (-not (Test-Path -LiteralPath $Command -PathType Leaf)) {
            throw "$Label executable is missing."
        }
        return
    }
    if ($null -eq (Get-Command $Command -ErrorAction SilentlyContinue)) {
        throw "$Label executable is unavailable."
    }
}

if (($Lane | Where-Object { $_ -in @('L1', 'L2', 'L4') }).Count -gt 0) {
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
        throw 'Packaged JAR is missing; behavior lanes cannot start.'
    }
    Assert-Command $JavaExecutable 'Java'
}
if (($Lane | Where-Object { $_ -in @('L0', 'L3') }).Count -gt 0) {
    Assert-Command $MavenExecutable 'Maven'
}

$environmentNames = @(
    'PORTFOLIO_MODEL_RUNTIME_ENABLED',
    'PORTFOLIO_GLM_ENABLED',
    'PORTFOLIO_GLM_API_KEY',
    'PORTFOLIO_GLM_DATA_POLICY_APPROVED',
    'PORTFOLIO_QWEN_ENABLED',
    'PORTFOLIO_QWEN_ENDPOINT',
    'PORTFOLIO_QWEN_API_KEY',
    'PORTFOLIO_QWEN_DATA_POLICY_APPROVED',
    'PLAYWRIGHT_EXTERNAL_SERVER',
    'PLAYWRIGHT_REAL_API',
    'PLAYWRIGHT_BASE_URL',
    'PLAYWRIGHT_REAL_RETRIEVAL',
    'PORTFOLIO_MODEL_OP_TURN_INTERPRETATION_MODE',
    'PORTFOLIO_MODEL_OP_TURN_INTERPRETATION_SCHEMA_VERSION',
    'PORTFOLIO_MODEL_OP_GENERAL_MODE',
    'PORTFOLIO_MODEL_OP_GENERAL_SCHEMA_VERSION',
    'PORTFOLIO_MODEL_OP_GENERAL_TIMEOUT'
)
$providerEnvironmentNames = @()
if ($Lane -contains 'L4') {
    foreach ($line in (Get-Content -LiteralPath $ProviderSecretFile)) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
        if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
            throw 'L4 provider secret file contains an invalid environment entry.'
        }
        $providerEnvironmentNames += $Matches[1]
    }
    $requiredModelEnvironment = @('PORTFOLIO_MODEL_RUNTIME_ENABLED') + $(if ($LiveModelRef -eq 'glm-4-7-flash') {
        @('PORTFOLIO_GLM_ENABLED', 'PORTFOLIO_GLM_DATA_POLICY_APPROVED',
            'PORTFOLIO_GLM_API_KEY')
    } else {
        @('PORTFOLIO_QWEN_ENABLED', 'PORTFOLIO_QWEN_DATA_POLICY_APPROVED',
            'PORTFOLIO_QWEN_API_KEY')
    })
    foreach ($requiredName in $requiredModelEnvironment) {
        if ($requiredName -notin $providerEnvironmentNames) {
            throw "L4 provider secret file does not define $requiredName."
        }
    }
    $environmentNames = @($environmentNames + $providerEnvironmentNames | Select-Object -Unique)
}
$environment = @{}
foreach ($name in $environmentNames) { $environment[$name] = Get-EnvSnapshot $name }
$results = [System.Collections.Generic.List[object]]::new()
try {
    foreach ($currentLane in $Lane) {
        $startedAt = Get-Date
        $status = 'PASS'
        $exitCode = 0
        try {
            if ($currentLane -eq 'L0') {
                $env:PORTFOLIO_MODEL_RUNTIME_ENABLED = 'false'
                & $MavenExecutable -f (Join-Path $root 'backend\pom.xml') `
                    '-Dtest=AgentTurnScenarioManifestTest,AgentTurnClosedContractIntegrationTest' test
                $exitCode = $LASTEXITCODE
                if ($exitCode -ne 0) { throw "L0 test process exited with $exitCode." }
            } elseif ($currentLane -eq 'L3') {
                $env:PORTFOLIO_MODEL_RUNTIME_ENABLED = 'false'
                & $MavenExecutable -f (Join-Path $root 'backend\pom.xml') `
                    '-Dtest=GoalProposalCodecTest,GeneralDraftCodecAdversarialTest,OpenAiCompatibleStructuredModelTransportDeadlineTest' test
                $exitCode = $LASTEXITCODE
                if ($exitCode -ne 0) { throw "L3 test process exited with $exitCode." }
            } else {
                $env:PLAYWRIGHT_EXTERNAL_SERVER = '1'
                $env:PLAYWRIGHT_REAL_API = '1'
                $env:PLAYWRIGHT_BASE_URL = "http://127.0.0.1:$Port"
                $playwrightScript = 'test:e2e'
                $playwrightArguments = @()
                if ($currentLane -eq 'L4') {
                    foreach ($line in (Get-Content -LiteralPath $ProviderSecretFile)) {
                        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
                        $null = $line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$'
                        Set-Item -LiteralPath "Env:$($Matches[1])" -Value $Matches[2].Trim()
                    }
                    if ($LiveModelRef -eq 'glm-4-7-flash') {
                        $env:PORTFOLIO_QWEN_ENABLED = 'false'
                        Remove-Item -LiteralPath 'Env:PORTFOLIO_QWEN_API_KEY' `
                            -ErrorAction SilentlyContinue
                    }
                    else {
                        $env:PORTFOLIO_GLM_ENABLED = 'false'
                        Remove-Item -LiteralPath 'Env:PORTFOLIO_GLM_API_KEY' `
                            -ErrorAction SilentlyContinue
                    }
                    $env:PORTFOLIO_MODEL_OP_TURN_INTERPRETATION_MODE = 'ENABLED'
                    $env:PORTFOLIO_MODEL_OP_TURN_INTERPRETATION_SCHEMA_VERSION = 'goal.proposal.v5'
                    $env:PORTFOLIO_MODEL_OP_GENERAL_MODE = 'ENABLED'
                    $generalSchemaVersion = if ($LiveModelRef -eq 'qwen-3-7-flash') {
                        'general.draft.v3'
                    } else {
                        'general.draft.v2'
                    }
                    $env:PORTFOLIO_MODEL_OP_GENERAL_SCHEMA_VERSION = $generalSchemaVersion
                    $env:PORTFOLIO_MODEL_OP_GENERAL_TIMEOUT = '8s'
                    & (Join-Path $root 'scripts\run-jar-e2e.ps1') -JarPath $jar `
                        -Port $Port -ContextMode $ContextMode -Lane LIVE `
                        -RequireLiveProvider `
                        -LiveModelRef $LiveModelRef `
                        -ProviderSamplingRounds $ProviderSamplingRounds `
                        -ProviderInterRoundDelayMilliseconds `
                            $ProviderInterRoundDelayMilliseconds `
                        -PlaywrightScript $playwrightScript -PlaywrightArguments $playwrightArguments
                } else {
                    $env:PORTFOLIO_MODEL_RUNTIME_ENABLED = 'false'
                    $laneContextMode = if ($currentLane -eq 'L2') {
                        'POSTGRESQL'
                    } else {
                        'IN_MEMORY'
                    }
                    & (Join-Path $root 'scripts\run-jar-e2e.ps1') -JarPath $jar `
                        -Port $Port -ContextMode $laneContextMode `
                        -PlaywrightScript $playwrightScript -PlaywrightArguments $playwrightArguments
                }
                $exitCode = $LASTEXITCODE
                if ($exitCode -ne 0) { throw "$currentLane process exited with $exitCode." }
            }
        } catch {
            $status = 'FAIL'
            if ($_.Exception.Message -match 'missing|unavailable|requires|empty') { $status = 'BLOCKED' }
            $exitCode = if ($LASTEXITCODE -is [int] -and $LASTEXITCODE -ne 0) { $LASTEXITCODE } else { 1 }
        }
        $results.Add([pscustomobject]@{
            lane = $currentLane
            status = $status
            exitCode = $exitCode
            evidenceScope = switch ($currentLane) {
                'L0' { 'CONTRACT_MANIFEST_ONLY' }
                'L1' { 'PACKAGED_BROWSER_CONTRACT' }
                'L2' { 'PACKAGED_BROWSER_POSTGRESQL' }
                'L3' { 'PROVIDER_CODEC_ADVERSARIAL' }
                'L4' { 'LIVE_PROVIDER_CANARY' }
            }
            durationBucket = if (((Get-Date) - $startedAt).TotalSeconds -lt 5) { 'LT_5_S' } else { 'GTE_5_S' }
        })
        if ($status -eq 'BLOCKED' -or $status -eq 'FAIL') { break }
    }
} finally {
    foreach ($name in $environmentNames) { Restore-Env $name $environment[$name] }
}

$results | ConvertTo-Json -Depth 4 -Compress
if (@($results | Where-Object { $_.status -ne 'PASS' }).Count -gt 0) { exit 1 }
