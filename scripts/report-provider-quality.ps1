param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('glm-4-7-flash', 'qwen-3-7-flash')]
    [string]$ModelRef,
    [Parameter(Mandatory = $true)]
    [string]$SelectionVersion,
    [Parameter(Mandatory = $true)]
    [string]$StdoutLog,
    [string]$LatencySamplesFile = '',
    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)

# GATE-21/GATE-23 聚合口径：只消费结构化诊断的闭集字段（operation、
# outcome、failure.code、failure.layer、failure.reason、duration.bucket）
# 与可选的客户端毫秒样本；任何 Prompt、正文或访客文本都不进入本报告。
$ErrorActionPreference = 'Stop'

$bucketMidpointMs = @{
    LT_100_MS          = 50
    FROM_100_TO_499_MS = 300
    FROM_500_TO_1999_MS = 1250
    GTE_2000_MS        = 2000
}

$operations = @{}
foreach ($line in @(Get-Content -LiteralPath $StdoutLog -Encoding UTF8)) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    try { $entry = $line | ConvertFrom-Json } catch { continue }
    $eventName = [string]$entry.'event.name'
    if ([string]::IsNullOrWhiteSpace($eventName) -and $null -ne $entry.event) {
        $eventName = [string]$entry.event.name
    }
    if ($eventName -notin @(
            'provider.call.completed', 'provider.call.failed',
            'provider.output.rejected')) { continue }
    $providerOperation = [string]$entry.'provider.operation'
    if ([string]::IsNullOrWhiteSpace($providerOperation) -and
            $null -ne $entry.provider) {
        $providerOperation = [string]$entry.provider.operation
    }
    if ($providerOperation -notmatch '^[A-Z_]{1,64}$') { continue }
    if (-not $operations.ContainsKey($providerOperation)) {
        $operations[$providerOperation] = @{
            calls           = 0
            completed       = 0
            failedByCode    = @{}
            rejectedByLayer = @{}
            rejectedReasons = @{}
            bucketCounts    = @{}
        }
    }
    $stats = $operations[$providerOperation]
    if ($eventName -eq 'provider.output.rejected') {
        $layer = [string]$entry.'failure.layer'
        if ($layer -notmatch '^[A-Z_]{1,64}$') { $layer = 'UNKNOWN_LAYER' }
        $reason = [string]$entry.'failure.reason'
        if ($reason -notmatch '^[A-Z0-9_]{1,96}$') { $reason = 'UNKNOWN_REASON' }
        if (-not $stats.rejectedByLayer.ContainsKey($layer)) {
            $stats.rejectedByLayer[$layer] = 0
        }
        $stats.rejectedByLayer[$layer]++
        if (-not $stats.rejectedReasons.ContainsKey("$layer/$reason")) {
            $stats.rejectedReasons["$layer/$reason"] = 0
        }
        $stats.rejectedReasons["$layer/$reason"]++
        continue
    }
    $stats.calls++
    if ($eventName -eq 'provider.call.completed') {
        $stats.completed++
        $bucket = [string]$entry.'duration.bucket'
        if ($bucket -notin $bucketMidpointMs.Keys) { $bucket = 'UNKNOWN_BUCKET' }
        if (-not $stats.bucketCounts.ContainsKey($bucket)) {
            $stats.bucketCounts[$bucket] = 0
        }
        $stats.bucketCounts[$bucket]++
        continue
    }
    $code = [string]$entry.'failure.code'
    if ($code -notmatch '^[A-Z0-9_]{1,64}$') { $code = 'UNKNOWN_CODE' }
    if (-not $stats.failedByCode.ContainsKey($code)) {
        $stats.failedByCode[$code] = 0
    }
    $stats.failedByCode[$code]++
}

$latencySamples = [System.Collections.Generic.List[int]]::new()
if ([string]::IsNullOrWhiteSpace($LatencySamplesFile) -eq $false) {
    foreach ($line in @(Get-Content -LiteralPath $LatencySamplesFile -Encoding UTF8)) {
        if ($line -match '^[0-9]{1,7}$') {
            $latencySamples.Add([int]$line)
        }
    }
}
$latencySamples.Sort()
function Get-Percentile([System.Collections.Generic.List[int]]$values, [double]$ratio) {
    if ($values.Count -eq 0) { return $null }
    $index = [Math]::Min(
        $values.Count - 1,
        [Math]::Max(0, [int][Math]::Ceiling($ratio * $values.Count) - 1))
    return $values[$index]
}

function Convert-ToSortedHashtable([hashtable]$source) {
    $result = [ordered]@{}
    foreach ($key in ($source.Keys | Sort-Object)) {
        $result[$key] = $source[$key]
    }
    return $result
}

$reportOperations = [ordered]@{}
$totalProviderLatencyBuckets = @{}
foreach ($name in ($operations.Keys | Sort-Object)) {
    $stats = $operations[$name]
    $approxWeighted = [long]0
    $bucketTotal = 0
    foreach ($bucket in $stats.bucketCounts.Keys) {
        if ($bucketMidpointMs.ContainsKey($bucket)) {
            $approxWeighted += [long]$bucketMidpointMs[$bucket] *
                $stats.bucketCounts[$bucket]
            $bucketTotal += $stats.bucketCounts[$bucket]
            if (-not $totalProviderLatencyBuckets.ContainsKey($bucket)) {
                $totalProviderLatencyBuckets[$bucket] = 0
            }
            $totalProviderLatencyBuckets[$bucket] +=
                $stats.bucketCounts[$bucket]
        }
    }
    $rejected = 0
    foreach ($count in $stats.rejectedByLayer.Values) { $rejected += $count }
    $failed = 0
    foreach ($count in $stats.failedByCode.Values) { $failed += $count }
    $reportOperations[$name] = [ordered]@{
        calls                 = $stats.calls
        completed             = $stats.completed
        failed                = $failed
        failedByCode          = Convert-ToSortedHashtable $stats.failedByCode
        rejected              = $rejected
        rejectedByLayer       = Convert-ToSortedHashtable $stats.rejectedByLayer
        rejectedLayerAndReason = Convert-ToSortedHashtable $stats.rejectedReasons
        schemaRejectionRate   = if (($stats.completed + $rejected) -gt 0) {
            [math]::Round(($stats.rejectedByLayer['PROVIDER_DRAFT_SCHEMA'] +
                $stats.rejectedByLayer['DETERMINISTIC_COMPILER'] +
                $stats.rejectedByLayer['CANONICAL_SCHEMA'] +
                $stats.rejectedByLayer['SCHEMA']) /
                [double]($stats.completed + $rejected), 4)
        } else { $null }
        latencyBucketCounts   = Convert-ToSortedHashtable $stats.bucketCounts
        latencyP50ApproxMs    = if ($bucketTotal -gt 0) {
            [int][Math]::Round($approxWeighted / [double]$bucketTotal)
        } else { $null }
        timeoutRateFromCodes  = if ($stats.calls -gt 0) {
            $timeoutCount = 0
            if ($stats.failedByCode.ContainsKey('DEADLINE_EXCEEDED')) {
                $timeoutCount += $stats.failedByCode['DEADLINE_EXCEEDED']
            }
            if ($stats.failedByCode.ContainsKey('TIMEOUT')) {
                $timeoutCount += $stats.failedByCode['TIMEOUT']
            }
            [math]::Round($timeoutCount / [double]$stats.calls, 4)
        } else { $null }
    }
}

$report = [ordered]@{
    schemaVersion      = 'provider-quality-report.v1'
    generatedAtUtc     = (Get-Date).ToUniversalTime().ToString('o')
    modelRef           = $ModelRef
    selectionVersion   = $SelectionVersion
    sourceLogPresent   = (Test-Path -LiteralPath $StdoutLog)
    operations         = $reportOperations
    totalLatencyBucketCounts = Convert-ToSortedHashtable $totalProviderLatencyBuckets
    clientSamples      = [ordered]@{
        count     = $latencySamples.Count
        p50Ms     = Get-Percentile $latencySamples 0.5
        p95Ms     = Get-Percentile $latencySamples 0.95
    }
}

$outputDirectory = Split-Path -Parent $OutputPath
if (-not [string]::IsNullOrWhiteSpace($outputDirectory) -and
        -not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}
$report | ConvertTo-Json -Depth 8 | Set-Content `
    -LiteralPath $OutputPath -Encoding UTF8

Write-Output ("PROVIDER_QUALITY_REPORT written=" + $OutputPath)
foreach ($name in $reportOperations.Keys) {
    $item = $reportOperations[$name]
    Write-Output ("PROVIDER_QUALITY_OPERATION operation={0} calls={1} completed={2} failed={3} rejected={4} schemaRejectionRate={5} latencyP50ApproxMs={6} clientP50Ms={7} clientP95Ms={8}" -f `
            $name, $item.calls, $item.completed, $item.failed, $item.rejected,
            $item.schemaRejectionRate, $item.latencyP50ApproxMs,
            $report.clientSamples.p50Ms, $report.clientSamples.p95Ms)
}
