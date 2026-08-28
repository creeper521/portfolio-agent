$ErrorActionPreference = 'Stop'
$checker = Join-Path $PSScriptRoot 'assert-live-goal-draft-matrix.ps1'
$source = Get-Content -LiteralPath $checker -Raw

foreach ($requiredContract in @(
        'DirectTrials = 5',
        'TwoTurnTrials = 5',
        'ValidateRange(5, 20)',
        'qwen-3-7-flash-v6',
        'glm-4-7-flash-v4',
        'GOAL_INTERPRETATION_ONLY',
        "Test-ExecutionIdentity `$first.Body 'NONE'",
        'requestedSize -eq 2',
        'actualSize -eq 2',
        'sameConversation',
        'Test-ResponseRequestId',
        'uniqueRequestIds=',
        'TURN_INTERPRETATION,',
        'LatencySamplesFile',
        'GOAL_DRAFT_MATRIX_AUTHORIZATION_REQUIRED'
)) {
    if ($source -notmatch [regex]::Escape($requiredContract)) {
        throw "Goal Draft matrix is missing contract: $requiredContract"
    }
}
foreach ($forbiddenBehavior in @(
        'Start-Job',
        'fallback',
        'repair',
        'Retry',
        'selectionVersion = ''qwen',
        'selectionVersion = ''glm'
)) {
    if ($source -match [regex]::Escape($forbiddenBehavior)) {
        throw "Goal Draft matrix contains forbidden behavior: $forbiddenBehavior"
    }
}

$previous = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $output = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File $checker `
        -BackendBaseUrl 'http://127.0.0.1:1' `
        -ExpectedContentVersion 'fixture-v1' `
        -ModelRef 'qwen-3-7-flash' `
        -SelectionVersion 'qwen-3-7-flash-v6' 2>&1 | Out-String)
    $exitCode = $LASTEXITCODE
}
finally {
    $ErrorActionPreference = $previous
}
if ($exitCode -eq 0 -or
        $output -notmatch 'GOAL_DRAFT_MATRIX_AUTHORIZATION_REQUIRED') {
    throw 'Goal Draft matrix must fail before I/O without explicit authorization.'
}

Write-Output 'Goal Draft live matrix contract passed.'
