param(
    [string]$RootPath = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$reporter = Join-Path $PSScriptRoot 'report-provider-quality.ps1'
$tempDirectory = Join-Path ([IO.Path]::GetTempPath()) (
    'provider-quality-report-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tempDirectory -Force | Out-Null

try {
    $stdoutLog = Join-Path $tempDirectory 'structured-stdout.log'
    @(
        '{"event.name":"provider.call.completed","provider.operation":"TURN_INTERPRETATION","duration.bucket":"FROM_100_TO_499_MS"}',
        '{"event":{"name":"provider.call.completed"},"provider":{"operation":"TURN_INTERPRETATION"},"duration":{"bucket":"GTE_2000_MS"}}',
        '{"event.name":"provider.call.failed","provider.operation":"GOAL_INTERPRETATION","failure.code":"DEADLINE_EXCEEDED","failure.layer":"TRANSPORT","duration.bucket":"GTE_2000_MS"}',
        '{"event.name":"provider.output.rejected","provider.operation":"GOAL_INTERPRETATION","failure.layer":"PROVIDER_DRAFT_SCHEMA","failure.reason":"MISSING_REQUIRED_FIELD"}',
        '{"event.name":"provider.output.rejected","provider.operation":"GENERAL_KNOWLEDGE","failure.layer":"PROVIDER_DRAFT_SCHEMA","failure.reason":"FIELD_TYPE_INVALID_DIMENSIONS"}',
        '{"event.name":"provider.output.rejected","provider.operation":"GENERAL_KNOWLEDGE","failure.layer":"DETERMINISTIC_COMPILER","failure.reason":"DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_DIMENSION"}',
        '{"event.name":"provider.output.rejected","provider.operation":"GENERAL_KNOWLEDGE","failure.layer":"CANONICAL_SCHEMA","failure.reason":"MISSING_REQUIRED_FIELD"}',
        '{"event.name":"provider.call.completed","provider.operation":"GENERAL_KNOWLEDGE","duration.bucket":"FROM_500_TO_1999_MS"}',
        '{"event.name":"provider.call.completed","provider.operation":"GENERAL_KNOWLEDGE","duration.bucket":"FROM_500_TO_1999_MS"}',
        '{"event.name":"provider.call.completed","provider.operation":"GENERAL_KNOWLEDGE","duration.bucket":"FROM_500_TO_1999_MS"}',
        '{"event.name":"provider.call.completed","provider.operation":"GENERAL_KNOWLEDGE","duration.bucket":"FROM_500_TO_1999_MS"}',
        '{"event.name":"provider.call.failed","provider.operation":"GENERAL_KNOWLEDGE","failure.code":"RATE_LIMITED","failure.layer":"TRANSPORT","duration.bucket":"FROM_100_TO_499_MS"}',
        '{"event.name":"provider.call.completed","provider.operation":"UNAPPROVED_OPERATION","duration.bucket":"LT_100_MS"}',
        '{"event.name":"unrelated.event","value":"sanitized-out"}'
    ) | Set-Content -LiteralPath $stdoutLog -Encoding UTF8
    $samples = Join-Path $tempDirectory 'client-samples.csv'
    @(
        'TURN_INTERPRETATION,730',
        'GOAL_INTERPRETATION,1500',
        'TURN_INTERPRETATION,480',
        'GENERAL_KNOWLEDGE,1250',
        'GENERAL_KNOWLEDGE,420',
        'UNAPPROVED_OPERATION,1',
        'GENERAL_KNOWLEDGE,not-a-number',
        'visitor text must never be a latency sample'
    ) |
        Set-Content -LiteralPath $samples -Encoding UTF8
    $output = Join-Path $tempDirectory 'report.json'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File $reporter `
        -ModelRef 'qwen-3-7-flash' `
        -SelectionVersion 'qwen-3-7-flash-v6' `
        -Status 'NOT_READY' `
        -StdoutLog $stdoutLog `
        -LatencySamplesFile $samples `
        -OutputPath $output | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Reporter exited non-zero on fixture input.'
    }
    $report = Get-Content -LiteralPath $output -Raw -Encoding UTF8 |
        ConvertFrom-Json

    if ($report.schemaVersion -ne 'provider-quality-report.v2') {
        throw 'Report schema version mismatch.'
    }
    if ($report.status -ne 'NOT_READY') {
        throw 'Report must preserve the runner quality status.'
    }
    $turn = $report.operations.TURN_INTERPRETATION
    if ($turn.calls -ne 3 -or $turn.completed -ne 2 -or
            $turn.failure_by_code.DEADLINE_EXCEEDED -ne 1 -or
            $turn.failure_by_layer.TRANSPORT -ne 1) {
        throw 'TURN_INTERPRETATION call accounting mismatch.'
    }
    if ($turn.rejected_by_layer.PROVIDER_DRAFT_SCHEMA -ne 1 -or
            $turn.p50_ms -ne 730 -or $turn.p95_ms -ne 1500 -or
            $turn.timeout_rate -ne 0.3333) {
        throw 'TURN normalization, percentile or timeout accounting mismatch.'
    }
    if ($null -ne $report.operations.GOAL_INTERPRETATION) {
        throw 'GOAL_INTERPRETATION must normalize into TURN_INTERPRETATION.'
    }
    $general = $report.operations.GENERAL_KNOWLEDGE
    if ($general.calls -ne 5 -or $general.completed -ne 4 -or
            $general.rejected -ne 3 -or
            $general.failure_by_layer.TRANSPORT -ne 1) {
        throw 'GENERAL_KNOWLEDGE rejection accounting mismatch.'
    }
    if ($general.schema_rejection_rate -ne 0.75 -or
            $general.rejection_rate -ne 0.75) {
        throw ('Schema rejection rate must be rejected/completed: ' +
            $general.schema_rejection_rate)
    }
    if ($general.rejected_layer_and_reason.'PROVIDER_DRAFT_SCHEMA/FIELD_TYPE_INVALID_DIMENSIONS' -ne 1 -or
            $general.rejected_layer_and_reason.'DETERMINISTIC_COMPILER/DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_DIMENSION' -ne 1) {
        throw 'Closed layer/reason histogram mismatch.'
    }
    if ($general.latency_sample_count -ne 2 -or
            $general.p50_ms -ne 420 -or $general.p95_ms -ne 1250 -or
            $general.timeout_rate -ne 0) {
        throw 'Per-operation client percentile computation mismatch.'
    }
    if ($null -ne $report.operations.UNAPPROVED_OPERATION) {
        throw 'Unknown operation must not enter the closed report.'
    }

    Write-Output 'PROVIDER_QUALITY_REPORT_TESTS_OK'
}
finally {
    if (Test-Path -LiteralPath $tempDirectory) {
        Remove-Item -LiteralPath $tempDirectory -Recurse -Force
    }
}
