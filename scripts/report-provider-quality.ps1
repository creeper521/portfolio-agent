param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('glm-4-7-flash', 'qwen-3-7-flash')]
    [string]$ModelRef,
    [Parameter(Mandatory = $true)]
    [string]$SelectionVersion,
    [ValidateSet('PASS', 'FAILED', 'BLOCKED', 'NOT_READY', 'IN_PROGRESS')]
    [string]$Status = 'IN_PROGRESS',
    [Parameter(Mandatory = $true)]
    [string]$StdoutLog,
    [string]$LatencySamplesFile = '',
    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)

# GATE-21/GATE-23 聚合口径：只消费结构化诊断的闭集字段与
# "operation,milliseconds" 客户端耗时样本。Prompt、正文、访客文本、
# Provider payload 与凭据都不会进入本报告。
$ErrorActionPreference = 'Stop'
$approvedOperations = @('TURN_INTERPRETATION', 'GENERAL_KNOWLEDGE')

function Normalize-Operation([string]$Value) {
    if ($Value -eq 'GOAL_INTERPRETATION') {
        return 'TURN_INTERPRETATION'
    }
    if ($Value -in $approvedOperations) {
        return $Value
    }
    return ''
}

function Read-ClosedField(
    [object]$Entry,
    [string]$Literal,
    [string]$Group,
    [string]$Name
) {
    $value = [string]$Entry.$Literal
    if (-not [string]::IsNullOrWhiteSpace($value)) { return $value }
    $nested = $Entry.$Group
    if ($null -ne $nested) { return [string]$nested.$Name }
    return ''
}

function New-OperationStats {
    return @{
        calls                  = 0
        completed              = 0
        failureByLayer         = @{}
        failureByCode          = @{}
        rejectedByLayer        = @{}
        rejectedLayerAndReason = @{}
        durationBucketCounts   = @{}
        latencySamples         = [System.Collections.Generic.List[long]]::new()
    }
}

function Get-OperationStats([hashtable]$Operations, [string]$Operation) {
    if (-not $Operations.ContainsKey($Operation)) {
        $Operations[$Operation] = New-OperationStats
    }
    return $Operations[$Operation]
}

function Add-Count([hashtable]$Counts, [string]$Key) {
    if (-not $Counts.ContainsKey($Key)) { $Counts[$Key] = 0 }
    $Counts[$Key]++
}

function Convert-ToSortedHashtable([hashtable]$Source) {
    $result = [ordered]@{}
    foreach ($key in ($Source.Keys | Sort-Object)) {
        $result[$key] = $Source[$key]
    }
    return $result
}

function Get-Percentile(
    [System.Collections.Generic.List[long]]$Values,
    [double]$Ratio
) {
    if ($Values.Count -eq 0) { return $null }
    $sorted = @($Values | Sort-Object)
    $index = [Math]::Min(
        $sorted.Count - 1,
        [Math]::Max(0, [int][Math]::Ceiling($Ratio * $sorted.Count) - 1))
    return [long]$sorted[$index]
}

if (-not (Test-Path -LiteralPath $StdoutLog -PathType Leaf)) {
    throw 'Provider quality stdout log is missing.'
}

$operations = @{}
foreach ($line in @(Get-Content -LiteralPath $StdoutLog -Encoding UTF8)) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    try { $entry = $line | ConvertFrom-Json } catch { continue }
    $eventName = Read-ClosedField $entry 'event.name' 'event' 'name'
    if ($eventName -notin @(
            'provider.call.completed', 'provider.call.failed',
            'provider.output.rejected')) { continue }
    $rawOperation = Read-ClosedField `
        $entry 'provider.operation' 'provider' 'operation'
    $operation = Normalize-Operation $rawOperation
    if ([string]::IsNullOrWhiteSpace($operation)) { continue }
    $stats = Get-OperationStats $operations $operation

    if ($eventName -eq 'provider.output.rejected') {
        $layer = Read-ClosedField $entry 'failure.layer' 'failure' 'layer'
        if ($layer -notmatch '^[A-Z_]{1,64}$') { $layer = 'UNKNOWN_LAYER' }
        $reason = Read-ClosedField $entry 'failure.reason' 'failure' 'reason'
        if ($reason -notmatch '^[A-Z0-9_]{1,96}$') { $reason = 'UNKNOWN_REASON' }
        Add-Count $stats.rejectedByLayer $layer
        Add-Count $stats.rejectedLayerAndReason "$layer/$reason"
        continue
    }

    $stats.calls++
    $bucket = Read-ClosedField $entry 'duration.bucket' 'duration' 'bucket'
    if ($bucket -notmatch '^[A-Z0-9_]{1,64}$') { $bucket = 'UNKNOWN_BUCKET' }
    Add-Count $stats.durationBucketCounts $bucket
    if ($eventName -eq 'provider.call.completed') {
        $stats.completed++
        continue
    }

    $layer = Read-ClosedField $entry 'failure.layer' 'failure' 'layer'
    if ($layer -notmatch '^[A-Z_]{1,64}$') { $layer = 'UNKNOWN_LAYER' }
    $code = Read-ClosedField $entry 'failure.code' 'failure' 'code'
    if ($code -notmatch '^[A-Z0-9_]{1,64}$') { $code = 'UNKNOWN_CODE' }
    Add-Count $stats.failureByLayer $layer
    Add-Count $stats.failureByCode $code
}

if (-not [string]::IsNullOrWhiteSpace($LatencySamplesFile)) {
    if (-not (Test-Path -LiteralPath $LatencySamplesFile -PathType Leaf)) {
        throw 'Provider quality latency samples file is missing.'
    }
    foreach ($line in @(Get-Content -LiteralPath $LatencySamplesFile -Encoding UTF8)) {
        if ($line -notmatch '^([A-Z_]{1,64}),([0-9]{1,9})$') { continue }
        $operation = Normalize-Operation $Matches[1]
        if ([string]::IsNullOrWhiteSpace($operation)) { continue }
        $stats = Get-OperationStats $operations $operation
        $stats.latencySamples.Add([long]$Matches[2])
    }
}

$reportOperations = [ordered]@{}
foreach ($operation in ($operations.Keys | Sort-Object)) {
    $stats = $operations[$operation]
    $failed = $stats.calls - $stats.completed
    $rejected = 0
    foreach ($count in $stats.rejectedByLayer.Values) { $rejected += $count }
    $timeoutCount = 0
    foreach ($timeoutCode in @('DEADLINE_EXCEEDED', 'TIMEOUT')) {
        if ($stats.failureByCode.ContainsKey($timeoutCode)) {
            $timeoutCount += $stats.failureByCode[$timeoutCode]
        }
    }
    $schemaRejected = 0
    foreach ($schemaLayer in @(
            'PROVIDER_DRAFT_SCHEMA', 'DETERMINISTIC_COMPILER',
            'CANONICAL_SCHEMA', 'SCHEMA')) {
        if ($stats.rejectedByLayer.ContainsKey($schemaLayer)) {
            $schemaRejected += $stats.rejectedByLayer[$schemaLayer]
        }
    }
    $reportOperations[$operation] = [ordered]@{
        calls                     = $stats.calls
        completed                 = $stats.completed
        failed                    = $failed
        rejected                  = $rejected
        rejected_by_layer         = Convert-ToSortedHashtable `
            $stats.rejectedByLayer
        failure_by_layer          = Convert-ToSortedHashtable `
            $stats.failureByLayer
        failure_by_code           = Convert-ToSortedHashtable `
            $stats.failureByCode
        rejected_layer_and_reason = Convert-ToSortedHashtable `
            $stats.rejectedLayerAndReason
        duration_bucket_counts    = Convert-ToSortedHashtable `
            $stats.durationBucketCounts
        latency_sample_count      = $stats.latencySamples.Count
        p50_ms                    = Get-Percentile $stats.latencySamples 0.50
        p95_ms                    = Get-Percentile $stats.latencySamples 0.95
        timeout_rate              = if ($stats.calls -gt 0) {
            [math]::Round($timeoutCount / [double]$stats.calls, 4)
        } else { $null }
        rejection_rate            = if ($stats.completed -gt 0) {
            [math]::Round(
                $rejected / [double]$stats.completed, 4)
        } else { $null }
        schema_rejection_rate     = if ($stats.completed -gt 0) {
            [math]::Round(
                $schemaRejected / [double]$stats.completed, 4)
        } else { $null }
    }
}

$report = [ordered]@{
    schemaVersion     = 'provider-quality-report.v2'
    generatedAtUtc    = (Get-Date).ToUniversalTime().ToString('o')
    modelRef          = $ModelRef
    selectionVersion = $SelectionVersion
    status            = $Status
    operations        = $reportOperations
}

$outputDirectory = Split-Path -Parent $OutputPath
if (-not [string]::IsNullOrWhiteSpace($outputDirectory) -and
        -not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}
$report | ConvertTo-Json -Depth 8 | Set-Content `
    -LiteralPath $OutputPath -Encoding UTF8

Write-Output ('PROVIDER_QUALITY_REPORT written=' + $OutputPath)
foreach ($operation in $reportOperations.Keys) {
    $item = $reportOperations[$operation]
    Write-Output (('PROVIDER_QUALITY_OPERATION operation={0} calls={1} ' +
        'completed={2} failed={3} rejected={4} p50Ms={5} p95Ms={6} ' +
        'timeoutRate={7} rejectionRate={8}') -f `
            $operation, $item.calls, $item.completed, $item.failed,
            $item.rejected, $item.p50_ms, $item.p95_ms,
            $item.timeout_rate, $item.rejection_rate)
}
