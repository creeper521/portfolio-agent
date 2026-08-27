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
        '{"event":{"name":"provider.call.completed"},"provider":{"operation":"TURN_INTERPRETATION"},"duration.bucket":"GTE_2000_MS"}',
        '{"event.name":"provider.call.failed","provider.operation":"TURN_INTERPRETATION","failure.code":"RATE_LIMITED","failure.layer":"TRANSPORT"}',
        '{"event.name":"provider.output.rejected","provider.operation":"GENERAL_KNOWLEDGE","failure.layer":"PROVIDER_DRAFT_SCHEMA","failure.reason":"FIELD_TYPE_INVALID_DIMENSIONS"}',
        '{"event.name":"provider.output.rejected","provider.operation":"GENERAL_KNOWLEDGE","failure.layer":"DETERMINISTIC_COMPILER","failure.reason":"DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_DIMENSION"}',
        '{"event.name":"provider.output.rejected","provider.operation":"GENERAL_KNOWLEDGE","failure.layer":"CANONICAL_SCHEMA","failure.reason":"MISSING_REQUIRED_FIELD"}',
        '{"event.name":"provider.call.completed","provider.operation":"GENERAL_KNOWLEDGE","duration.bucket":"FROM_500_TO_1999_MS"}',
        '{"event.name":"unrelated.event","value":"sanitized-out"}'
    ) | Set-Content -LiteralPath $stdoutLog -Encoding UTF8
    $samples = Join-Path $tempDirectory 'client-samples.csv'
    @('', '730', '1500', 'not-a-number', '480') |
        Set-Content -LiteralPath $samples -Encoding UTF8
    $output = Join-Path $tempDirectory 'report.json'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File $reporter `
        -ModelRef 'qwen-3-7-flash' `
        -SelectionVersion 'qwen-3-7-flash-v6' `
        -StdoutLog $stdoutLog `
        -LatencySamplesFile $samples `
        -OutputPath $output | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Reporter exited non-zero on fixture input.'
    }
    $report = Get-Content -LiteralPath $output -Raw -Encoding UTF8 |
        ConvertFrom-Json

    if ($report.schemaVersion -ne 'provider-quality-report.v1') {
        throw 'Report schema version mismatch.'
    }
    $turn = $report.operations.TURN_INTERPRETATION
    if ($turn.calls -ne 3 -or $turn.completed -ne 2 -or
            $turn.failedByCode.RATE_LIMITED -ne 1) {
        throw 'TURN_INTERPRETATION call accounting mismatch.'
    }
    if ($turn.latencyP50ApproxMs -ne 1150) {
        throw ('TURN bucket approximation mismatch: ' +
            $turn.latencyP50ApproxMs)
    }
    $general = $report.operations.GENERAL_KNOWLEDGE
    if ($general.calls -ne 1 -or $general.rejected -ne 3) {
        throw 'GENERAL_KNOWLEDGE rejection accounting mismatch.'
    }
    if ($general.schemaRejectionRate -ne 0.75) {
        throw ('Schema rejection rate must be rejected/(calls+rejected): ' +
            $general.schemaRejectionRate)
    }
    if ($general.rejectedLayerAndReason.'PROVIDER_DRAFT_SCHEMA/FIELD_TYPE_INVALID_DIMENSIONS' -ne 1 -or
            $general.rejectedLayerAndReason.'DETERMINISTIC_COMPILER/DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_DIMENSION' -ne 1) {
        throw 'Closed layer/reason histogram mismatch.'
    }
    if ($report.clientSamples.count -ne 3 -or
            $report.clientSamples.p50Ms -ne 730 -or
            $report.clientSamples.p95Ms -ne 1500) {
        throw 'Client percentile computation mismatch.'
    }

    Write-Output 'PROVIDER_QUALITY_REPORT_TESTS_OK'
}
finally {
    if (Test-Path -LiteralPath $tempDirectory) {
        Remove-Item -LiteralPath $tempDirectory -Recurse -Force
    }
}
