param(
    [ValidateSet('PASS', 'IN_PROGRESS', 'NOT_RUN', 'FAILED')]
    [string]$Deterministic,
    [ValidateSet('PASS', 'IN_PROGRESS', 'NOT_RUN', 'FAILED')]
    [string]$ScenarioRuntime,
    [ValidateSet('PASS', 'IN_PROGRESS', 'NOT_RUN', 'FAILED')]
    [string]$BrowserContract,
    [ValidateSet('PASS', 'IN_PROGRESS', 'NOT_RUN', 'FAILED')]
    [string]$BrowserBody,
    [ValidateSet('PASS', 'IN_PROGRESS', 'NOT_RUN', 'FAILED')]
    [string]$PostgreSqlState,
    [ValidateSet('PASS', 'IN_PROGRESS', 'NOT_RUN', 'FAILED')]
    [string]$PostgreSqlJvmRestartApi,
    [ValidateSet('PASS', 'IN_PROGRESS', 'NOT_RUN', 'FAILED')]
    [string]$BrowserJvmRestart,
    [ValidateSet(
        'PASS', 'IN_PROGRESS', 'NOT_RUN', 'FAILED', 'BLOCKED', 'NOT_READY')]
    [string]$ProviderQuality,
    [switch]$RequireComplete
)

$ErrorActionPreference = 'Stop'
$layers = [ordered]@{
    deterministic = $Deterministic
    scenarioRuntime = $ScenarioRuntime
    browserContract = $BrowserContract
    browserBody = $BrowserBody
    postgreSqlState = $PostgreSqlState
    postgreSqlJvmRestartApi = $PostgreSqlJvmRestartApi
    browserJvmRestart = $BrowserJvmRestart
    providerQuality = $ProviderQuality
}
$values = @($layers.Values)
$overall = if ('FAILED' -in $values) {
    'FAILED'
}
elseif (@($values | Where-Object { $_ -ne 'PASS' }).Count -eq 0) {
    'PASS'
}
else {
    'IN_PROGRESS'
}

[pscustomobject]@{
    schemaVersion = 1
    overall = $overall
    layers = [pscustomobject]$layers
} | ConvertTo-Json -Depth 4 -Compress | Write-Output

if ($RequireComplete -and $overall -ne 'PASS') {
    [Console]::Error.WriteLine('AGENT_VERIFICATION_INCOMPLETE')
    exit 1
}
