$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'write-agent-verification-summary.ps1'

function Invoke-Summary([string[]]$Arguments) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $scriptPath @Arguments 2>&1
        return [pscustomobject]@{
            ExitCode = $LASTEXITCODE
            Text = ($output -join [Environment]::NewLine)
        }
    }
    finally {
        $ErrorActionPreference = $previous
    }
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

$allPass = Invoke-Summary @(
    '-Deterministic', 'PASS',
    '-ScenarioRuntime', 'PASS',
    '-BrowserContract', 'PASS',
    '-BrowserBody', 'PASS',
    '-PostgreSqlState', 'PASS',
    '-PostgreSqlJvmRestart', 'PASS',
    '-ProviderQuality', 'PASS',
    '-RequireComplete'
)
Assert-True ($allPass.ExitCode -eq 0) 'all required layers must complete.'
$allPassJson = $allPass.Text | ConvertFrom-Json
Assert-True ($allPassJson.overall -eq 'PASS') 'all PASS layers must report PASS.'

$partial = Invoke-Summary @(
    '-Deterministic', 'PASS',
    '-ScenarioRuntime', 'NOT_RUN',
    '-BrowserContract', 'PASS',
    '-BrowserBody', 'IN_PROGRESS',
    '-PostgreSqlState', 'PASS',
    '-PostgreSqlJvmRestart', 'NOT_RUN',
    '-ProviderQuality', 'NOT_RUN'
)
Assert-True ($partial.ExitCode -eq 0) 'partial report mode must remain inspectable.'
$partialJson = $partial.Text | ConvertFrom-Json
Assert-True ($partialJson.overall -eq 'IN_PROGRESS') `
    'missing required layers must not report PASS.'
Assert-True ($partialJson.layers.scenarioRuntime -eq 'NOT_RUN') `
    'scenario runtime status must remain separate.'
Assert-True ($partialJson.layers.browserContract -eq 'PASS' -and
        $partialJson.layers.browserBody -eq 'IN_PROGRESS') `
    'browser transport/contract and body quality must remain separate.'

$requiredPartial = Invoke-Summary @(
    '-Deterministic', 'PASS',
    '-ScenarioRuntime', 'NOT_RUN',
    '-BrowserContract', 'PASS',
    '-BrowserBody', 'IN_PROGRESS',
    '-PostgreSqlState', 'PASS',
    '-PostgreSqlJvmRestart', 'NOT_RUN',
    '-ProviderQuality', 'NOT_RUN',
    '-RequireComplete'
)
Assert-True ($requiredPartial.ExitCode -eq 1) `
    'complete mode must reject an incomplete layer matrix.'
Assert-True ($requiredPartial.Text -match 'AGENT_VERIFICATION_INCOMPLETE') `
    'incomplete failure must use a stable code.'

$failed = Invoke-Summary @(
    '-Deterministic', 'PASS',
    '-ScenarioRuntime', 'FAILED',
    '-BrowserContract', 'PASS',
    '-BrowserBody', 'PASS',
    '-PostgreSqlState', 'PASS',
    '-PostgreSqlJvmRestart', 'PASS',
    '-ProviderQuality', 'PASS'
)
$failedJson = $failed.Text | ConvertFrom-Json
Assert-True ($failedJson.overall -eq 'FAILED') `
    'any failed required layer must report FAILED.'

Write-Output 'write-agent-verification-summary tests passed'
