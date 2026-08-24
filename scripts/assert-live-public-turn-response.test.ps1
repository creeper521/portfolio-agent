$ErrorActionPreference = 'Stop'
$checker = Join-Path $PSScriptRoot 'assert-live-public-turn-response.ps1'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('assert-live-public-turn-' + [guid]::NewGuid().ToString('N'))
$responsePath = Join-Path $fixtureRoot 'response.json'
$names = @(
    'PORTFOLIO_MODEL_RUNTIME_ENABLED', 'PORTFOLIO_GLM_ENABLED',
    'PORTFOLIO_GLM_DATA_POLICY_APPROVED', 'PORTFOLIO_GLM_API_KEY'
)

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Invoke-Checker([string]$Kind = 'ANSWER') {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker `
            -ResponsePath $responsePath -ExpectedContentVersion 'test-v1' `
            -ExpectedModelRef 'glm-4-7-flash' `
            -ExpectedKind $Kind 2>&1 | Out-String)
        return @{ ExitCode = $LASTEXITCODE; Output = $output }
    }
    finally {
        $ErrorActionPreference = $previous
    }
}

$snapshots = @{}
foreach ($name in $names) {
    $snapshots[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
try {
    New-Item -ItemType Directory -Force -Path $fixtureRoot | Out-Null
    foreach ($name in $names) {
        if ($name -eq 'PORTFOLIO_GLM_API_KEY') { continue }
        [Environment]::SetEnvironmentVariable($name, 'true', 'Process')
    }
    [Environment]::SetEnvironmentVariable('PORTFOLIO_GLM_API_KEY', 'fixture-key', 'Process')

    $fixture = @{
        requestId = [guid]::NewGuid().ToString()
        kind = 'ANSWER'
        conversation = @{ conversationId = 'conversation-fixture' }
        modelExecution = @{
            selectionKind = 'MODEL'
            requestedModelRef = 'glm-4-7-flash'
            selectionVersion = 'glm-4-7-flash-v1'
            participation = 'GOAL_AND_ANSWER'
        }
        answer = @{
            resolution = 'COMPLETE'
            contentReleaseId = 'test-v1'
            goalResults = @(@{ goalId = 'goal-1'; label = 'general'; coverage = 'FULL'; notices = @() })
            sourceCatalog = @{ sources = @() }
            sourceComposition = @('GENERAL_KNOWLEDGE')
        }
    }
    [IO.File]::WriteAllText($responsePath, ($fixture | ConvertTo-Json -Depth 8 -Compress))
    $result = Invoke-Checker
    Assert-True ($result.ExitCode -eq 0) "final ANSWER fixture must pass: $($result.Output)"

    $fixture.answer.sourceComposition = @('DERIVED')
    [IO.File]::WriteAllText($responsePath, ($fixture | ConvertTo-Json -Depth 8 -Compress))
    $result = Invoke-Checker
    Assert-True ($result.ExitCode -ne 0 -and $result.Output -match 'LIVE_PROVIDER_GENERAL_SUPPORT_MISSING') `
        'ANSWER without GENERAL_KNOWLEDGE must fail closed.'

    $social = @{
        requestId = [guid]::NewGuid().ToString()
        kind = 'CONVERSATIONAL'
        message = 'hello'
        conversation = @{ conversationId = 'conversation-fixture' }
        modelExecution = @{
            selectionKind = 'MODEL'
            requestedModelRef = 'glm-4-7-flash'
            selectionVersion = 'glm-4-7-flash-v1'
            participation = 'GOAL_INTERPRETATION_ONLY'
        }
    }
    [IO.File]::WriteAllText($responsePath, ($social | ConvertTo-Json -Depth 4 -Compress))
    $result = Invoke-Checker 'CONVERSATIONAL'
    Assert-True ($result.ExitCode -eq 0) "final CONVERSATIONAL fixture must pass: $($result.Output)"

    Write-Output 'assert-live-public-turn-response tests passed'
}
finally {
    foreach ($name in $names) {
        [Environment]::SetEnvironmentVariable($name, $snapshots[$name], 'Process')
    }
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
}
