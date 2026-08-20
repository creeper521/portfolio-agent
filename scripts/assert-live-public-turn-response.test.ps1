$ErrorActionPreference = 'Stop'
$checker = Join-Path $PSScriptRoot 'assert-live-public-turn-response.ps1'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('assert-live-public-turn-' + [guid]::NewGuid().ToString('N'))
$responsePath = Join-Path $fixtureRoot 'response.json'
$names = @(
    'PORTFOLIO_MODEL_ENABLED', 'PORTFOLIO_MODEL_DATA_POLICY_APPROVED',
    'PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED',
    'PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED', 'PORTFOLIO_MODEL_PROVIDER',
    'PORTFOLIO_AGENT_DEEPSEEK_API_KEY'
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
    foreach ($name in $names[0..3]) {
        [Environment]::SetEnvironmentVariable($name, 'true', 'Process')
    }
    [Environment]::SetEnvironmentVariable('PORTFOLIO_MODEL_PROVIDER', 'DEEPSEEK_V4_FLASH', 'Process')
    [Environment]::SetEnvironmentVariable('PORTFOLIO_AGENT_DEEPSEEK_API_KEY', 'fixture-key', 'Process')

    $fixture = @{
        requestId = [guid]::NewGuid().ToString()
        kind = 'ANSWER'
        conversation = @{ conversationId = 'conversation-fixture' }
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
