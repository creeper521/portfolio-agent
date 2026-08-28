param(
    [Parameter(Mandatory = $true)]
    [string]$RawArtifactRoot,
    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)

# Offline only: the historical v3 and candidate v4 results are compared in one
# test JVM. This script is not a production fallback and never emits raw content.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'raw-root-common.ps1')
$script:ReplayStage = 'BOOTSTRAP'
trap {
    Write-Output ('DUAL_REPLAY_INTERNAL_ERROR stage=' + $script:ReplayStage +
        ' type=' + $_.Exception.GetType().Name)
    exit 1
}

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$corpusPath = Join-Path $PSScriptRoot `
    'qwen-general-explanation-corpus.v1.json'
$maven = 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd'
$replayNow = [datetimeoffset]::UtcNow
$captureNamePattern = `
    '^capture-[a-z]+(?:-[a-z]+)*-[0-9]{3}-(?:concise|standard|detailed)-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{32}$'
$metadataFields = @(
    'schemaVersion', 'artifactId', 'caseId', 'depth', 'createdAtUtc',
    'expiresAtUtc', 'operatorIdentitySha256', 'provider', 'model',
    'selectionVersion', 'providerContract', 'compilerProfile', 'status',
    'httpClass', 'latencyBucket', 'latencyMs', 'attemptCount',
    'captureSource')
$script:ReplayTestMode = $env:QWEN_GENERAL_REPLAY_TEST_MODE -ceq `
    'AUTHORIZED_LOOPBACK_REPLAY_TEST_ONLY'
$script:ReplayCaptureSource = if ($script:ReplayTestMode) {
    'TEST_LOOPBACK'
}
else {
    'REAL_PROVIDER'
}
$script:SemanticFixtureCaseKey =
    [string]$env:QWEN_GENERAL_REPLAY_SEMANTIC_FIXTURE_CASE_KEY
$script:SemanticFixtureAuthorization =
    [string]$env:QWEN_GENERAL_REPLAY_SEMANTIC_FIXTURE_AUTHORIZATION
$script:SemanticFixtureAuthorizationValue =
    'AUTHORIZED_POST_CANONICAL_VALIDATOR_FIXTURE_ONLY'
$closedRules = @(
    'TRIM_TEXT', 'COLLAPSE_MEANINGLESS_WHITESPACE',
    'UNICODE_NORMALIZE_NFC', 'WRAP_STRING_AS_ARRAY',
    'JOIN_ROLE_SENTENCES', 'NORMALIZE_TERMINAL_PUNCTUATION',
    'MISSING_CAVEATS_AS_EMPTY', 'DROPPED_INVALID_OPTIONAL_CAVEATS',
    'UNKNOWN_FIELD_COUNT')

function Stop-Replay([string]$Code) {
    Write-Output $Code
    exit 1
}

function Get-NormalizedPath([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) { return '' }
    return [System.IO.Path]::GetFullPath($Path).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar)
}

function Test-SameOrChild([string]$Candidate, [string]$Parent) {
    $candidatePath = Get-NormalizedPath $Candidate
    $parentPath = Get-NormalizedPath $Parent
    if ($candidatePath.Equals(
            $parentPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $true
    }
    return $candidatePath.StartsWith(
        $parentPath + [System.IO.Path]::DirectorySeparatorChar,
        [System.StringComparison]::OrdinalIgnoreCase)
}

function Test-PathContainsReparsePoint([string]$Path) {
    $currentPath = Get-NormalizedPath $Path
    while (-not [string]::IsNullOrWhiteSpace($currentPath)) {
        if (Test-Path -LiteralPath $currentPath) {
            $current = Get-Item -LiteralPath $currentPath -Force
            if (($current.Attributes -band `
                [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                return $true
            }
        }
        $parent = Split-Path -Parent $currentPath
        if ([string]::IsNullOrWhiteSpace($parent) -or
                $parent -ceq $currentPath) { break }
        $currentPath = $parent
    }
    return $false
}

function Test-ExactKeys([object]$Value, [string[]]$Expected) {
    if ($null -eq $Value) { return $false }
    $actual = @($Value.PSObject.Properties.Name | Sort-Object)
    $required = @($Expected | Sort-Object)
    return ($actual -join '|') -ceq ($required -join '|')
}

function Test-NoDuplicateJsonKeys([string]$Json) {
    $matches = [regex]::Matches($Json, '"([A-Za-z][A-Za-z0-9]*)"\s*:')
    $seen = @{}
    foreach ($match in $matches) {
        $name = $match.Groups[1].Value
        if ($seen.ContainsKey($name)) { return $false }
        $seen[$name] = $true
    }
    return $true
}

function Assert-ContainedLeaf(
    [string]$Path,
    [string]$Root,
    [string]$FailureCode
) {
    $fullPath = Get-NormalizedPath $Path
    if (-not (Test-SameOrChild $fullPath $Root) -or
            -not (Test-Path -LiteralPath $fullPath -PathType Leaf) -or
            (Test-PathContainsReparsePoint $fullPath)) {
        Stop-Replay $FailureCode
    }
    $resolved = Get-NormalizedPath (Resolve-Path -LiteralPath $fullPath).Path
    if (-not (Test-SameOrChild $resolved $Root)) {
        Stop-Replay $FailureCode
    }
    return $resolved
}

function Read-ArtifactMetadata([string]$Artifact, [string]$Root) {
    $path = Assert-ContainedLeaf (Join-Path $Artifact 'metadata.json') `
        $Root 'DUAL_REPLAY_ARTIFACT_METADATA_REJECTED'
    try {
        $raw = Get-Content -LiteralPath $path -Raw -Encoding UTF8
        if (-not (Test-NoDuplicateJsonKeys $raw)) {
            Stop-Replay 'DUAL_REPLAY_ARTIFACT_METADATA_REJECTED'
        }
        $metadata = $raw | ConvertFrom-Json
    }
    catch { Stop-Replay 'DUAL_REPLAY_ARTIFACT_METADATA_REJECTED' }
    if (-not (Test-ExactKeys $metadata $metadataFields) -or
            $metadata.schemaVersion -cne 'qwen-general-lab-artifact.v2' -or
            $metadata.artifactId -cne `
                [System.IO.Path]::GetFileName($Artifact) -or
            [string]$metadata.caseId -cnotmatch `
                '^[a-z]+(?:-[a-z]+)*-[0-9]{3}$' -or
            [string]$metadata.depth -cnotin @(
                'CONCISE', 'STANDARD', 'DETAILED') -or
            [string]$metadata.operatorIdentitySha256 -cnotmatch `
                '^[0-9a-f]{64}$' -or
            $metadata.provider -cne 'QWEN' -or
            $metadata.model -cne 'qwen3.7-flash' -or
            $metadata.selectionVersion -cne 'qwen-3-7-flash-v7' -or
            $metadata.providerContract -cne 'general.provider-draft.v4' -or
            $metadata.compilerProfile -cne `
                'general-provider-draft-compiler.v4' -or
            [string]$metadata.status -cnotin @(
                'CAPTURED', 'RATE_LIMITED', 'SERVER_ERROR', 'CLIENT_ERROR',
                'TRANSPORT_FAILED', 'RESPONSE_REJECTED') -or
            [string]$metadata.httpClass -cnotin @(
                'SUCCESS', 'RATE_LIMITED', 'SERVER_ERROR', 'CLIENT_ERROR',
                'TRANSPORT_UNAVAILABLE') -or
            [string]$metadata.latencyBucket -cnotin @(
                'LT_100_MS', 'FROM_100_TO_499_MS',
                'FROM_500_TO_1999_MS', 'FROM_2000_TO_9999_MS',
                'GTE_10000_MS') -or
            ($metadata.latencyMs -isnot [int] -and
                $metadata.latencyMs -isnot [long]) -or
            [long]$metadata.latencyMs -lt 0 -or
            [long]$metadata.latencyMs -gt 120000 -or
            ($metadata.attemptCount -isnot [int] -and
                $metadata.attemptCount -isnot [long]) -or
            [long]$metadata.attemptCount -notin @(1, 2)) {
        Stop-Replay 'DUAL_REPLAY_ARTIFACT_METADATA_REJECTED'
    }
    if ([string]$metadata.captureSource -cne $script:ReplayCaptureSource) {
        Stop-Replay 'DUAL_REPLAY_CAPTURE_SOURCE_REJECTED'
    }
    $validPair = switch ([string]$metadata.status) {
        'CAPTURED' { $metadata.httpClass -ceq 'SUCCESS' }
        'RATE_LIMITED' { $metadata.httpClass -ceq 'RATE_LIMITED' }
        'SERVER_ERROR' { $metadata.httpClass -ceq 'SERVER_ERROR' }
        'CLIENT_ERROR' { $metadata.httpClass -ceq 'CLIENT_ERROR' }
        'TRANSPORT_FAILED' {
            $metadata.httpClass -ceq 'TRANSPORT_UNAVAILABLE'
        }
        'RESPONSE_REJECTED' { $metadata.httpClass -ceq 'SUCCESS' }
        default { $false }
    }
    if (-not $validPair) {
        Stop-Replay 'DUAL_REPLAY_ARTIFACT_METADATA_REJECTED'
    }
    try {
        $created = [datetimeoffset]::Parse([string]$metadata.createdAtUtc)
        $expires = [datetimeoffset]::Parse([string]$metadata.expiresAtUtc)
    }
    catch { Stop-Replay 'DUAL_REPLAY_ARTIFACT_METADATA_REJECTED' }
    if ($expires -le $created -or $expires -le $replayNow -or
            ($expires - $created) -gt [timespan]::FromHours(24)) {
        Stop-Replay 'DUAL_REPLAY_ARTIFACT_METADATA_REJECTED'
    }
    return $metadata
}

function Test-ClosedOutcome([object]$Value, [switch]$WithRules) {
    $expected = @('outcome', 'layer', 'reason')
    if ($WithRules) { $expected += 'normalizationRuleCounts' }
    if (-not (Test-ExactKeys $Value $expected) -or
            [string]$Value.outcome -cnotin @(
                'EXACT', 'NORMALIZED', 'DEGRADED', 'INCOMPLETE',
                'NOT_APPLICABLE') -or
            [string]$Value.layer -cnotmatch '^[A-Z_]{1,64}$' -or
            [string]$Value.reason -cnotmatch '^[A-Z0-9_]{1,96}$') {
        return $false
    }
    if ($WithRules) {
        foreach ($property in $Value.normalizationRuleCounts.PSObject.Properties) {
            if ($property.Name -cnotin $closedRules -or
                    $property.Value -isnot [int] -and
                    $property.Value -isnot [long] -or
                    [long]$property.Value -lt 0) {
                return $false
            }
        }
    }
    return $true
}

$script:ReplayStage = 'RAW_ROOT'
try {
    $rawRoot = Assert-DedicatedRawRoot `
        $RawArtifactRoot $repoRoot 'DUAL_REPLAY_RAW_ROOT_REJECTED'
}
catch {
    Stop-Replay 'DUAL_REPLAY_RAW_ROOT_REJECTED'
}
$script:ReplayStage = 'RAW_ROOT_ACL'
try {
    Protect-DedicatedRawRootAcl `
        $rawRoot 'DUAL_REPLAY_RAW_ROOT_REJECTED'
}
catch { Stop-Replay 'DUAL_REPLAY_RAW_ROOT_REJECTED' }
if (-not $script:ReplayTestMode -and
        (Test-Path -LiteralPath (Join-Path $rawRoot '.loopback-test-trace'))) {
    Stop-Replay 'DUAL_REPLAY_LOOPBACK_TRACE_REJECTED'
}
if ((-not [string]::IsNullOrWhiteSpace(
            $script:SemanticFixtureCaseKey) -and
        $script:SemanticFixtureAuthorization -cne
            $script:SemanticFixtureAuthorizationValue) -or
        ([string]::IsNullOrWhiteSpace(
            $script:SemanticFixtureCaseKey) -and
        -not [string]::IsNullOrWhiteSpace(
            $script:SemanticFixtureAuthorization)) -or
        (-not [string]::IsNullOrWhiteSpace(
            $script:SemanticFixtureCaseKey) -and
        $script:SemanticFixtureCaseKey -cnotmatch
            '^[a-z]+(?:-[a-z]+)*-[0-9]{3}\|(?:CONCISE|STANDARD|DETAILED)$')) {
    Stop-Replay 'DUAL_REPLAY_SEMANTIC_FIXTURE_REJECTED'
}
$script:ReplayStage = 'OUTPUT_PATH'
$output = Get-NormalizedPath $OutputPath
if ([string]::IsNullOrWhiteSpace($output) -or
        (Test-SameOrChild $output $rawRoot)) {
    Stop-Replay 'DUAL_REPLAY_OUTPUT_REJECTED'
}

$script:ReplayStage = 'ARTIFACT_DISCOVERY'
$artifacts = @(Get-ChildItem -LiteralPath $rawRoot -Directory -Force |
    Where-Object { $_.Name -cmatch $captureNamePattern })
if ($artifacts.Count -lt 1 -or $artifacts.Count -gt 300) {
    Stop-Replay 'DUAL_REPLAY_ARTIFACT_SET_REJECTED'
}
foreach ($artifact in $artifacts) {
    $script:ReplayStage = 'ARTIFACT_VALIDATION'
    if (($artifact.Attributes -band `
            [System.IO.FileAttributes]::ReparsePoint) -ne 0 -or
            -not (Test-SameOrChild `
                (Get-NormalizedPath $artifact.FullName) $rawRoot)) {
        Stop-Replay 'DUAL_REPLAY_ARTIFACT_SET_REJECTED'
    }
    $metadata = Read-ArtifactMetadata $artifact.FullName $rawRoot
    $candidateResponse = Join-Path $artifact.FullName 'response.raw.json'
    if (Test-Path -LiteralPath $candidateResponse) {
        $responsePath = Assert-ContainedLeaf `
            $candidateResponse $rawRoot 'DUAL_REPLAY_ARTIFACT_SET_REJECTED'
        if ((Get-Item -LiteralPath $responsePath).Length -gt 131072) {
            Stop-Replay 'DUAL_REPLAY_ARTIFACT_SET_REJECTED'
        }
    }
    if ($metadata.status -ceq 'CAPTURED') {
        if (-not (Test-Path -LiteralPath $candidateResponse -PathType Leaf)) {
            Stop-Replay 'DUAL_REPLAY_ARTIFACT_SET_REJECTED'
        }
    }
}
if (-not (Test-Path -LiteralPath $corpusPath -PathType Leaf) -or
        -not (Test-Path -LiteralPath $maven -PathType Leaf)) {
    Stop-Replay 'DUAL_REPLAY_RUNTIME_MISSING'
}

$temporaryToken = [guid]::NewGuid().ToString('N')
$temporaryLog = Join-Path $artifacts[0].FullName `
    ('.dual-replay-' + $temporaryToken + '.log')
$temporaryAggregate = Join-Path $artifacts[0].FullName `
    ('.dual-replay-' + $temporaryToken + '.json')
try {
    $script:ReplayStage = 'MAVEN_EXECUTION'
    $arguments = @(
        '-q',
        '-f', (Join-Path $repoRoot 'backend\pom.xml'),
        '-DskipFrontend=true',
        '-Dtest=GeneralProviderDraftDualReplayTest',
        ("-DdualReplay.rawRoot=$rawRoot"),
        ("-DdualReplay.output=$temporaryAggregate"),
        ("-DdualReplay.corpus=$corpusPath"),
        ("-DdualReplay.captureSource=$script:ReplayCaptureSource"),
        'test'
    )
    if (-not [string]::IsNullOrWhiteSpace(
            $script:SemanticFixtureCaseKey)) {
        $fixtureParts = $script:SemanticFixtureCaseKey.Split('|')
        $arguments = @($arguments[0..($arguments.Count - 2)]) + @(
            ("-DdualReplay.semanticFixtureCaseId=" + $fixtureParts[0]),
            ("-DdualReplay.semanticFixtureDepth=" + $fixtureParts[1]),
            ("-DdualReplay.semanticFixtureAuthorization=" +
                $script:SemanticFixtureAuthorization),
            'test')
    }
    & $maven @arguments *> $temporaryLog
    if ($LASTEXITCODE -ne 0) {
        Stop-Replay 'DUAL_REPLAY_EXECUTION_FAILED'
    }
    try {
        $script:ReplayStage = 'AGGREGATE_PARSE'
        $aggregate = Get-Content -LiteralPath $temporaryAggregate `
            -Raw -Encoding UTF8 | ConvertFrom-Json
    }
    catch {
        Stop-Replay 'DUAL_REPLAY_AGGREGATE_REJECTED'
    }
        if (-not (Test-ExactKeys $aggregate @(
                'schemaVersion', 'corpusVersion', 'fixtureMode',
                'fixtureCaseKey', 'samples')) -or
            $aggregate.schemaVersion -cne 'qwen-general-dual-replay.v1' -or
            $aggregate.corpusVersion -cne `
                'qwen-general-explanation-corpus.v1' -or
            [string]$aggregate.fixtureMode -cnotin @(
                'NONE',
                'TEST_ONLY_POST_CANONICAL_SEMANTIC_REJECT') -or
            ($aggregate.fixtureMode -ceq 'NONE' -and
                $null -ne $aggregate.fixtureCaseKey) -or
            ($aggregate.fixtureMode -ceq
                    'TEST_ONLY_POST_CANONICAL_SEMANTIC_REJECT' -and
                [string]$aggregate.fixtureCaseKey -cnotmatch
                    '^[a-z]+(?:-[a-z]+)*-[0-9]{3}\|(?:CONCISE|STANDARD|DETAILED)$')) {
            Stop-Replay 'DUAL_REPLAY_AGGREGATE_REJECTED'
        }
    $samples = @($aggregate.samples)
    if ($samples.Count -ne $artifacts.Count) {
        Stop-Replay 'DUAL_REPLAY_AGGREGATE_REJECTED'
    }
    $safeSamples = [System.Collections.Generic.List[object]]::new()
    $script:ReplayStage = 'AGGREGATE_VALIDATE'
    foreach ($sample in $samples) {
        if (-not (Test-ExactKeys $sample @('caseId', 'depth', 'v3', 'v4')) -or
                [string]$sample.caseId -cnotmatch `
                    '^[a-z]+(?:-[a-z]+)*-[0-9]{3}$' -or
                [string]$sample.depth -cnotin @(
                    'CONCISE', 'STANDARD', 'DETAILED') -or
                -not (Test-ClosedOutcome $sample.v3) -or
                -not (Test-ClosedOutcome $sample.v4 -WithRules)) {
            Stop-Replay 'DUAL_REPLAY_AGGREGATE_REJECTED'
        }
        $rules = [ordered]@{}
        foreach ($property in @(
            $sample.v4.normalizationRuleCounts.PSObject.Properties |
                Sort-Object Name)) {
            $rules[$property.Name] = [long]$property.Value
        }
        $safeSamples.Add([ordered]@{
            caseId = [string]$sample.caseId
            depth = [string]$sample.depth
            v3 = [ordered]@{
                outcome = [string]$sample.v3.outcome
                layer = [string]$sample.v3.layer
                reason = [string]$sample.v3.reason
            }
            v4 = [ordered]@{
                outcome = [string]$sample.v4.outcome
                layer = [string]$sample.v4.layer
                reason = [string]$sample.v4.reason
                normalizationRuleCounts = $rules
            }
        })
    }
    $safeAggregate = [ordered]@{
        schemaVersion = 'qwen-general-dual-replay.v1'
        corpusVersion = 'qwen-general-explanation-corpus.v1'
        fixtureMode = [string]$aggregate.fixtureMode
        fixtureCaseKey = if ($null -eq $aggregate.fixtureCaseKey) {
            $null
        } else { [string]$aggregate.fixtureCaseKey }
        samples = @($safeSamples)
    }
    $outputDirectory = Split-Path -Parent $output
    if (-not [string]::IsNullOrWhiteSpace($outputDirectory) -and
            -not (Test-Path -LiteralPath $outputDirectory)) {
        New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    }
    [System.IO.File]::WriteAllText(
        $output,
        ($safeAggregate | ConvertTo-Json -Depth 10),
        [System.Text.UTF8Encoding]::new($false))
    $script:ReplayStage = 'COMPLETE'

    $v3Accepted = @($safeSamples | Where-Object {
        $_.v3.outcome -cin @('EXACT', 'NORMALIZED', 'DEGRADED')
    }).Count
    $v4Accepted = @($safeSamples | Where-Object {
        $_.v4.outcome -cin @('EXACT', 'NORMALIZED', 'DEGRADED')
    }).Count
    Write-Output (('GENERAL_DUAL_REPLAY_COMPLETE samples={0} ' +
        'v3Accepted={1} v4Accepted={2}') -f `
        $safeSamples.Count, $v3Accepted, $v4Accepted)
}
finally {
    foreach ($temporary in @($temporaryLog, $temporaryAggregate)) {
        if (Test-Path -LiteralPath $temporary -PathType Leaf) {
            Remove-Item -LiteralPath $temporary -Force
        }
    }
}
