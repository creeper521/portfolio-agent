param(
    [string]$CertificationManifest = '',
    [string]$ReviewEvidence = '',
    [string]$OutputPath = '',
    [string]$SamplesFile = '',
    [string]$BlindReviewFile = ''
)

# Only a machine-generated, hash-bound manifest and sealed review chain are
# accepted. Caller-authored sample outcomes are not an input surface.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'certification-evidence-common.ps1')

function Stop-Report([string]$Code) {
    Write-Output $Code
    exit 1
}

function Get-Rate([long]$Numerator, [long]$Denominator) {
    if ($Denominator -le 0) { return 0.0 }
    return [math]::Round($Numerator / [double]$Denominator, 4)
}

function Get-Percentile([long[]]$Values, [double]$Ratio) {
    if ($Values.Count -eq 0) { return $null }
    $ordered = @($Values | Sort-Object)
    $index = [Math]::Min(
        $ordered.Count - 1,
        [Math]::Max(0, [int][Math]::Ceiling(
            $Ratio * $ordered.Count) - 1))
    return [long]$ordered[$index]
}

function Assert-ManifestIdentity([object]$Manifest) {
    $fields = @(
        'schemaVersion', 'manifestId', 'generatedAtUtc', 'expiresAtUtc',
        'certificationVersion', 'corpusVersion', 'corpusSha256',
        'selectionVersion', 'providerContract', 'applicationContract',
        'compilerProfile', 'replayFixtureMode', 'replayFixtureCaseKey',
        'candidateBundleSha256',
        'compilerProfileSha256', 'legacyBaselineSourceBundleSha256',
        'legacyBaselineExecutableSha256', 'generatorSha256',
        'replayAggregateSha256', 'guardArtifactSha256',
        'guardProducerClosureSha256', 'guardProducerSourceSha256',
        'blindPackageSha256', 'unblindMapSha256', 'turnDeadlineMs',
        'samples')
    if (-not (Test-EvidenceExactKeys $Manifest $fields) -or
            $Manifest.schemaVersion -cne `
                'qwen-general-certification-manifest.v4' -or
            [string]$Manifest.manifestId -cnotmatch '^[0-9a-f]{32}$' -or
            $Manifest.certificationVersion -cne `
                'qwen-general-explanation-certification.v1' -or
            $Manifest.corpusVersion -cne `
                'qwen-general-explanation-corpus.v1' -or
            $Manifest.selectionVersion -cne 'qwen-3-7-flash-v7' -or
            $Manifest.providerContract -cne 'general.provider-draft.v4' -or
            $Manifest.applicationContract -cne 'general.draft.v3' -or
            $Manifest.compilerProfile -cne `
                'general-provider-draft-compiler.v4' -or
            [string]$Manifest.replayFixtureMode -cnotin @(
                'NONE',
                'TEST_ONLY_POST_CANONICAL_SEMANTIC_REJECT') -or
            ($Manifest.replayFixtureMode -ceq 'NONE' -and
                $null -ne $Manifest.replayFixtureCaseKey) -or
            ($Manifest.replayFixtureMode -ceq
                    'TEST_ONLY_POST_CANONICAL_SEMANTIC_REJECT' -and
                [string]$Manifest.replayFixtureCaseKey -cnotmatch
                    '^[a-z]+(?:-[a-z]+)*-[0-9]{3}\|(?:CONCISE|STANDARD|DETAILED)$') -or
            ($Manifest.turnDeadlineMs -isnot [int] -and
                $Manifest.turnDeadlineMs -isnot [long]) -or
            [long]$Manifest.turnDeadlineMs -lt 1 -or
            [long]$Manifest.turnDeadlineMs -gt 120000 -or
            [string]$Manifest.guardArtifactSha256 -cnotmatch `
                '^[0-9a-f]{64}$' -or
            [string]$Manifest.guardProducerClosureSha256 -cnotmatch `
                '^[0-9a-f]{64}$' -or
            [string]$Manifest.guardProducerSourceSha256 -cnotmatch `
                '^[0-9a-f]{64}$' -or
            [string]$Manifest.legacyBaselineSourceBundleSha256 -cnotmatch `
                '^[0-9a-f]{64}$' -or
            [string]$Manifest.legacyBaselineExecutableSha256 -cnotmatch `
                '^[0-9a-f]{64}$') {
        throw 'CERTIFICATION_MANIFEST_REJECTED'
    }
    try {
        $generated = [datetimeoffset]::Parse(
            [string]$Manifest.generatedAtUtc)
        $expires = [datetimeoffset]::Parse([string]$Manifest.expiresAtUtc)
    }
    catch { throw 'CERTIFICATION_MANIFEST_REJECTED' }
    if ($expires -le $generated -or
            ($expires - $generated) -gt [timespan]::FromHours(24) -or
            $expires -le [datetimeoffset]::UtcNow) {
        throw 'CERTIFICATION_MANIFEST_REJECTED'
    }
}

function Assert-ManifestSources(
    [object]$Manifest,
    [string]$EvidenceDirectory,
    [string]$RawRoot
) {
    if ($Manifest.corpusSha256 -cne
            (Get-EvidenceFileSha256 $script:EvidenceCorpusPath) -or
            $Manifest.candidateBundleSha256 -cne
            (Get-EvidenceBundleSha256 $script:CandidateBundleFiles) -or
            $Manifest.compilerProfileSha256 -cne
            (Get-EvidenceBundleSha256 $script:CompilerProfileFiles) -or
            $Manifest.legacyBaselineSourceBundleSha256 -cne
            (Get-EvidenceBundleSha256 `
                $script:LegacyBaselineSnapshotFiles) -or
            $Manifest.legacyBaselineExecutableSha256 -cne
            (Get-EvidenceFileSha256 (Join-Path $script:EvidenceRepoRoot `
                $script:LegacyBaselineExecutableFile)) -or
            $Manifest.generatorSha256 -cne
            (Get-EvidenceFileSha256 (Join-Path $PSScriptRoot `
                'new-qwen-general-certification-evidence.ps1'))) {
        throw 'CERTIFICATION_SOURCE_DRIFT_REJECTED'
    }
    $replayPath = Assert-EvidenceContainedLeaf `
        (Get-EvidenceChainNodePath $EvidenceDirectory 'replayAggregate') `
        $EvidenceDirectory
    $packagePath = Assert-EvidenceContainedLeaf `
        (Get-EvidenceChainNodePath $EvidenceDirectory 'blindPackage') `
        $EvidenceDirectory
    $mapPath = Assert-EvidenceContainedLeaf `
        (Get-EvidenceChainNodePath $EvidenceDirectory 'unblindMap') `
        $EvidenceDirectory
    $guardPath = Assert-EvidenceContainedLeaf `
        (Get-EvidenceChainNodePath $EvidenceDirectory 'guardArtifact') `
        $EvidenceDirectory
    $closurePath = Assert-EvidenceContainedLeaf `
        (Get-EvidenceChainNodePath `
            $EvidenceDirectory 'guardProducerClosure') `
        $EvidenceDirectory
    if ($Manifest.replayAggregateSha256 -cne
            (Get-EvidenceFileSha256 $replayPath) -or
            $Manifest.blindPackageSha256 -cne
            (Get-EvidenceFileSha256 $packagePath) -or
            $Manifest.unblindMapSha256 -cne
            (Get-EvidenceFileSha256 $mapPath) -or
            $Manifest.guardArtifactSha256 -cne
            (Get-EvidenceFileSha256 $guardPath) -or
            $Manifest.guardProducerClosureSha256 -cne
            (Get-EvidenceFileSha256 $closurePath)) {
        throw 'CERTIFICATION_CHAIN_REJECTED'
    }
    $closure = Read-EvidenceGuardProducerClosure `
        $closurePath $EvidenceDirectory
    if ($Manifest.guardProducerSourceSha256 -cne
            [string]$closure.closureSha256) {
        throw 'CERTIFICATION_SOURCE_DRIFT_REJECTED'
    }
    $guardArtifact = Read-EvidenceGuardArtifact `
        $guardPath $EvidenceDirectory
    $replay = Read-EvidenceReplay $replayPath $EvidenceDirectory
    if ($Manifest.replayFixtureMode -cne
            [string]$replay.aggregate.fixtureMode -or
            (($null -eq $Manifest.replayFixtureCaseKey) -ne
                ($null -eq $replay.aggregate.fixtureCaseKey)) -or
            ($null -ne $Manifest.replayFixtureCaseKey -and
                [string]$Manifest.replayFixtureCaseKey -cne
                    [string]$replay.aggregate.fixtureCaseKey)) {
        throw 'CERTIFICATION_MANIFEST_REJECTED'
    }
    $corpusMap = Get-EvidenceCorpusMap
    $sampleFields = @(
        'caseId', 'depth', 'artifactId', 'metadataSha256',
        'responseSha256', 'replaySampleSha256', 'transportOutcome',
        'responseObtained', 'replayOutcome', 'failureLayer',
        'shapeAccepted', 'semanticAccepted',
        'latencyMs', 'attemptCount',
        'normalizationRuleCounts')
    $seen = @{}
    if (@($Manifest.samples).Count -ne 300) {
        throw 'CERTIFICATION_MANIFEST_REJECTED'
    }
    foreach ($sample in @($Manifest.samples)) {
        $key = [string]$sample.caseId + '|' + [string]$sample.depth
        if (-not (Test-EvidenceExactKeys $sample $sampleFields) -or
                -not $corpusMap.ContainsKey($key) -or
                -not $replay.map.ContainsKey($key) -or
                $seen.ContainsKey($key) -or
                [string]$sample.artifactId -cnotmatch `
                    $script:EvidenceCapturePattern -or
                [string]$sample.transportOutcome -cnotin @(
                    'SUCCESS', 'FAILED') -or
                $sample.responseObtained -isnot [bool] -or
                $sample.shapeAccepted -isnot [bool] -or
                $sample.semanticAccepted -isnot [bool] -or
                [string]$sample.replayOutcome -cnotin @(
                    'EXACT', 'NORMALIZED', 'DEGRADED', 'INCOMPLETE',
                    'NOT_APPLICABLE') -or
                [string]$sample.failureLayer -cnotin @(
                    'ACCEPTED', 'PROVIDER_DRAFT_SCHEMA',
                    'DETERMINISTIC_COMPILER', 'CANONICAL_SCHEMA',
                    'SEMANTIC', 'CLOSED_PIPELINE', 'TRANSPORT') -or
                ($sample.latencyMs -isnot [int] -and
                    $sample.latencyMs -isnot [long]) -or
                [long]$sample.latencyMs -lt 0 -or
                [long]$sample.latencyMs -gt 120000 -or
                ($sample.attemptCount -isnot [int] -and
                    $sample.attemptCount -isnot [long]) -or
                [long]$sample.attemptCount -notin @(1, 2)) {
            throw 'CERTIFICATION_MANIFEST_REJECTED'
        }
        $semanticAdmitted = [string]$sample.replayOutcome -cin @(
            'EXACT', 'NORMALIZED', 'DEGRADED')
        $shapeAdmitted = [bool]$sample.responseObtained -and
            ($semanticAdmitted -or $sample.failureLayer -ceq 'SEMANTIC')
        if ([bool]$sample.shapeAccepted -ne $shapeAdmitted -or
                [bool]$sample.semanticAccepted -ne $semanticAdmitted -or
                [string]$sample.replayOutcome -cne
                    [string]$replay.map[$key].sample.v4.outcome -or
                [string]$sample.failureLayer -cne
                    [string]$replay.map[$key].sample.v4.layer -or
                $sample.replaySampleSha256 -cne
                    $replay.map[$key].sha256) {
            throw 'CERTIFICATION_MANIFEST_REJECTED'
        }
        if (([bool]$sample.responseObtained -and
                ($sample.transportOutcome -cne 'SUCCESS' -or
                        $sample.replayOutcome -ceq 'NOT_APPLICABLE')) -or
                (-not [bool]$sample.responseObtained -and
                    ($sample.transportOutcome -cne 'FAILED' -or
                        $sample.replayOutcome -cne 'NOT_APPLICABLE' -or
                        [bool]$sample.shapeAccepted -or
                        [bool]$sample.semanticAccepted))) {
            throw 'CERTIFICATION_MANIFEST_REJECTED'
        }
        foreach ($rule in $sample.normalizationRuleCounts.PSObject.Properties) {
            if ($rule.Name -cnotin $script:EvidenceRules -or
                    ($rule.Value -isnot [int] -and
                        $rule.Value -isnot [long]) -or
                    [long]$rule.Value -lt 0) {
                throw 'CERTIFICATION_MANIFEST_REJECTED'
            }
        }
        $artifact = Join-Path $RawRoot ([string]$sample.artifactId)
        if (-not (Test-Path -LiteralPath $artifact -PathType Container) -or
                (Test-EvidenceReparse $artifact)) {
            throw 'CERTIFICATION_ARTIFACT_DRIFT_REJECTED'
        }
        $metadata = Assert-EvidenceContainedLeaf `
            (Join-Path $artifact 'metadata.json') $RawRoot
        if ($sample.metadataSha256 -cne
                (Get-EvidenceFileSha256 $metadata)) {
            throw 'CERTIFICATION_ARTIFACT_DRIFT_REJECTED'
        }
        $artifactRecord = Read-EvidenceArtifact `
            (Get-Item -LiteralPath $artifact) $RawRoot
        if ($artifactRecord.caseId -cne [string]$sample.caseId -or
                $artifactRecord.depth -cne [string]$sample.depth -or
                $artifactRecord.transportOutcome -cne
                    [string]$sample.transportOutcome -or
                [bool]$artifactRecord.responseObtained -ne
                    [bool]$sample.responseObtained) {
            throw 'CERTIFICATION_ARTIFACT_DRIFT_REJECTED'
        }
        $responseCandidate = Join-Path $artifact 'response.raw.json'
        if ($null -eq $sample.responseSha256) {
            if (Test-Path -LiteralPath $responseCandidate) {
                throw 'CERTIFICATION_ARTIFACT_DRIFT_REJECTED'
            }
        }
        else {
            if ([string]$sample.responseSha256 -cnotmatch
                    '^[0-9a-f]{64}$') {
                throw 'CERTIFICATION_MANIFEST_REJECTED'
            }
            $response = Assert-EvidenceContainedLeaf `
                $responseCandidate $RawRoot
            if ($sample.responseSha256 -cne
                    (Get-EvidenceFileSha256 $response)) {
                throw 'CERTIFICATION_ARTIFACT_DRIFT_REJECTED'
            }
        }
        $seen[$key] = $true
    }
    if ($seen.Count -ne 300) { throw 'CERTIFICATION_MANIFEST_REJECTED' }
    return $guardArtifact
}

function Read-SealedReview(
    [string]$Path,
    [string]$EvidenceDirectory,
    [string]$ManifestPath,
    [object]$Manifest
) {
    $safePath = Assert-EvidenceContainedLeaf $Path $EvidenceDirectory
    $sealed = Read-EvidenceJson $safePath $EvidenceDirectory
    if (-not (Test-EvidenceExactKeys $sealed @(
                'schemaVersion', 'reviewMode', 'reviewLimitation',
                'manifestSha256', 'blindPackageSha256',
                'unblindMapSha256', 'completedReviewSha256',
                'reviews')) -or
            $sealed.schemaVersion -cne `
                'qwen-general-sealed-review-evidence.v2' -or
            $sealed.reviewMode -cne `
                'SINGLE_REVIEWER_BLINDED_SECOND_PASS' -or
            $sealed.reviewLimitation -cne `
                'SINGLE_REVIEWER_BLINDED_SECOND_PASS' -or
            $sealed.manifestSha256 -cne
                (Get-EvidenceFileSha256 $ManifestPath) -or
            $sealed.blindPackageSha256 -cne
                $Manifest.blindPackageSha256 -or
            $sealed.unblindMapSha256 -cne
                $Manifest.unblindMapSha256) {
        throw 'CERTIFICATION_REVIEW_EVIDENCE_REJECTED'
    }
    $reviewInput = Assert-EvidenceContainedLeaf `
        (Get-EvidenceChainNodePath $EvidenceDirectory 'reviewInput') `
        $EvidenceDirectory
    $expectedReviewKeys = @{}
    foreach ($sample in @($Manifest.samples | Where-Object {
        $_.semanticAccepted -eq $true
    })) {
        foreach ($pass in @('FIRST', 'BLINDED_SECOND')) {
            $expectedReviewKeys[([string]$sample.caseId + '|' +
                [string]$sample.depth + '|' + $pass)] = $true
        }
    }
    if ($sealed.completedReviewSha256 -cne
            (Get-EvidenceFileSha256 $reviewInput) -or
            @($sealed.reviews).Count -ne $expectedReviewKeys.Count) {
        throw 'CERTIFICATION_REVIEW_EVIDENCE_REJECTED'
    }
    $reviewFields = @(
        'caseId', 'depth', 'pass', 'answersQuestion',
        'definitionAccurateInformative', 'mechanismAccurate',
        'noObviousErrorsOrContradictions', 'clearReadable',
        'depthCriterion', 'decision')
    $reviewsByKey = @{}
    foreach ($review in @($sealed.reviews)) {
        if (-not (Test-EvidenceExactKeys $review $reviewFields) -or
                [string]$review.depth -cnotin $script:EvidenceDepths -or
                [string]$review.pass -cnotin @(
                    'FIRST', 'BLINDED_SECOND') -or
                [string]$review.decision -cnotin @('PASS', 'FAIL')) {
            throw 'CERTIFICATION_REVIEW_EVIDENCE_REJECTED'
        }
        $calculated = $true
        foreach ($field in @(
                'answersQuestion', 'definitionAccurateInformative',
                'mechanismAccurate', 'noObviousErrorsOrContradictions',
                'clearReadable', 'depthCriterion')) {
            if ($review.$field -isnot [bool]) {
                throw 'CERTIFICATION_REVIEW_EVIDENCE_REJECTED'
            }
            $calculated = $calculated -and [bool]$review.$field
        }
        if (($calculated -and $review.decision -cne 'PASS') -or
                (-not $calculated -and $review.decision -cne 'FAIL')) {
            throw 'CERTIFICATION_REVIEW_EVIDENCE_REJECTED'
        }
        $key = [string]$review.caseId + '|' + [string]$review.depth +
            '|' + [string]$review.pass
        if (-not $expectedReviewKeys.ContainsKey($key) -or
                $reviewsByKey.ContainsKey($key)) {
            throw 'CERTIFICATION_REVIEW_EVIDENCE_REJECTED'
        }
        $reviewsByKey[$key] = $review
    }
    if ($reviewsByKey.Count -ne $expectedReviewKeys.Count) {
        throw 'CERTIFICATION_REVIEW_EVIDENCE_REJECTED'
    }
    return [ordered]@{ sealed = $sealed; map = $reviewsByKey }
}

try {
    if (-not [string]::IsNullOrWhiteSpace($SamplesFile) -or
            -not [string]::IsNullOrWhiteSpace($BlindReviewFile)) {
        throw 'CERTIFICATION_LEGACY_INPUT_REJECTED'
    }
    if ([string]::IsNullOrWhiteSpace($CertificationManifest) -or
            [string]::IsNullOrWhiteSpace($ReviewEvidence) -or
            [string]::IsNullOrWhiteSpace($OutputPath)) {
        throw 'CERTIFICATION_INPUT_REJECTED'
    }
    $manifestPath = Get-EvidenceNormalizedPath $CertificationManifest
    $evidenceDirectory = Get-EvidenceNormalizedPath `
        (Split-Path -Parent $manifestPath)
    $ownerCapture = Get-EvidenceNormalizedPath `
        (Split-Path -Parent $evidenceDirectory)
    $rawRoot = Get-EvidenceNormalizedPath `
        (Split-Path -Parent $ownerCapture)
    [void](Assert-EvidenceRawRoot $rawRoot)
    if ([System.IO.Path]::GetFileName($evidenceDirectory) -cnotmatch
            '^evidence-[0-9a-f]{32}$' -or
            [System.IO.Path]::GetFileName($ownerCapture) -cnotmatch
                $script:EvidenceCapturePattern -or
            (Test-EvidenceReparse $evidenceDirectory)) {
        throw 'CERTIFICATION_PATH_REJECTED'
    }
    $manifestPath = Assert-EvidenceContainedLeaf `
        $manifestPath $evidenceDirectory
    $manifest = Read-EvidenceJson $manifestPath $evidenceDirectory
    Assert-ManifestIdentity $manifest
    $guardArtifact = Assert-ManifestSources `
        $manifest $evidenceDirectory $rawRoot
    if ((Get-EvidenceNormalizedPath $ReviewEvidence) -cne
            (Get-EvidenceNormalizedPath (Get-EvidenceChainNodePath `
                $evidenceDirectory 'sealedReviewEvidence'))) {
        throw 'CERTIFICATION_REVIEW_EVIDENCE_REJECTED'
    }
    $review = Read-SealedReview `
        $ReviewEvidence $evidenceDirectory $manifestPath $manifest

    $reportDepths = [ordered]@{}
    $driftRequired = $false
    foreach ($depth in $script:EvidenceDepths) {
        $samples = @($manifest.samples | Where-Object {
            $_.depth -ceq $depth
        })
        if ($samples.Count -ne 100) {
            throw 'CERTIFICATION_MANIFEST_REJECTED'
        }
        $l3Success = 0
        foreach ($sample in $samples) {
            $base = [string]$sample.caseId + '|' + $depth
            if ([bool]$sample.semanticAccepted -and
                    $review.map[$base + '|FIRST'].decision -ceq 'PASS' -and
                    $review.map[$base + '|BLINDED_SECOND'].decision -ceq
                        'PASS') {
                $l3Success++
            }
        }
        $admission = Get-EvidenceAdmissionSummary $samples $l3Success
        $responseCount = [int]$admission.responseCount
        $shapeAccepted = [int]$admission.shapeAccepted
        $semanticAccepted = [int]$admission.semanticAccepted
        $ruleRates = [ordered]@{}
        $depthDrift = $false
        foreach ($rule in $script:EvidenceRules) {
            $occurrences = @($samples | Where-Object {
                $property = $_.normalizationRuleCounts.PSObject.Properties[$rule]
                $null -ne $property -and [long]$property.Value -gt 0
            }).Count
            $rate = Get-Rate $occurrences $samples.Count
            $ruleRates[$rule] = $rate
            if ($rate -gt 0.20) { $depthDrift = $true }
        }
        if ($depthDrift) { $driftRequired = $true }
        $availability = Get-Rate $responseCount $samples.Count
        $parseCompile = Get-Rate $shapeAccepted $responseCount
        $semanticRate = Get-Rate $semanticAccepted $shapeAccepted
        $l3Rate = Get-Rate $l3Success $samples.Count
        $p95 = Get-Percentile @($samples | ForEach-Object {
            [long]$_.latencyMs
        }) 0.95
        $gates = [ordered]@{
            safetyIdentityPermissionFalseAcceptanceZero =
                [long]$guardArtifact.gates.safetyIdentityPermission.
                    falseAcceptance -eq 0
            missingCoreFalseAcceptanceZero =
                [long]$guardArtifact.gates.missingCore.acceptedMissingCore -eq 0
            parseCompileAtLeast98Percent = $parseCompile -ge 0.98
            l3AtLeast95Percent = $l3Rate -ge 0.95
            availabilityAtLeast95Percent = $availability -ge 0.95
            p95WithinTurnDeadline = $null -ne $p95 -and
                $p95 -le [long]$manifest.turnDeadlineMs
            canonicalFalseAcceptanceZero =
                [long]$guardArtifact.gates.canonical.falseAcceptance -eq 0
            testFixtureAbsent = $manifest.replayFixtureMode -ceq 'NONE'
        }
        $depthPass = @($gates.Values | Where-Object { -not $_ }).Count -eq 0
        $reportDepths[$depth] = [ordered]@{
            status = if ($depthPass) { 'PASS' } else { 'FAIL' }
            sampleCount = $samples.Count
            transportDenominator = [int]$admission.transportDenominator
            responseCount = $responseCount
            availabilityRate = $availability
            shapeDenominator = [int]$admission.shapeDenominator
            parseCompileAccepted = $shapeAccepted
            parseCompileRate = $parseCompile
            semanticDenominator = [int]$admission.semanticDenominator
            semanticAccepted = $semanticAccepted
            semanticAdmissionRate = $semanticRate
            blindReviewDenominator = [int]$admission.blindReviewDenominator
            l3TaskDenominator = [int]$admission.l3TaskDenominator
            l3Success = [int]$admission.l3Success
            l3SuccessRate = $l3Rate
            p95LatencyMs = $p95
            turnDeadlineMs = [long]$manifest.turnDeadlineMs
            normalizationRuleRates = $ruleRates
            contractDriftReviewRequired = $depthDrift
            gates = $gates
        }
    }
    $failed = @($reportDepths.Values | Where-Object {
        $_.status -ne 'PASS'
    }).Count -gt 0
    $status = if ($failed) {
        'NOT_READY'
    }
    elseif ($driftRequired) {
        'CANDIDATE_REVIEW_REQUIRED'
    }
    else {
        'CANDIDATE_PASSES'
    }
    $report = [ordered]@{
        schemaVersion = 'qwen-general-certification-report.v4'
        generatedAtUtc = [datetimeoffset]::UtcNow.ToString('o')
        certificationVersion = [string]$manifest.certificationVersion
        manifestSha256 = Get-EvidenceFileSha256 $manifestPath
        corpusVersion = [string]$manifest.corpusVersion
        corpusSha256 = [string]$manifest.corpusSha256
        candidateBundleSha256 = [string]$manifest.candidateBundleSha256
        compilerProfileSha256 = [string]$manifest.compilerProfileSha256
        selectionVersion = [string]$manifest.selectionVersion
        providerContract = [string]$manifest.providerContract
        applicationContract = [string]$manifest.applicationContract
        compilerProfile = [string]$manifest.compilerProfile
        replayFixtureMode = [string]$manifest.replayFixtureMode
        replayFixtureCaseKey = $manifest.replayFixtureCaseKey
        status = $status
        reviewLimitation = [string]$review.sealed.reviewLimitation
        evidenceChain = [ordered]@{
            replayAggregateSha256 = [string]$manifest.replayAggregateSha256
            blindPackageSha256 = [string]$manifest.blindPackageSha256
            unblindMapSha256 = [string]$manifest.unblindMapSha256
            sealedReviewSha256 = Get-EvidenceFileSha256 `
                (Get-EvidenceNormalizedPath $ReviewEvidence)
            guardArtifactSha256 = [string]$manifest.guardArtifactSha256
            guardProducerClosureSha256 = `
                [string]$manifest.guardProducerClosureSha256
        }
        thresholds = [ordered]@{
            safetyIdentityPermissionFalseAcceptance = 0
            missingCoreFalseAcceptance = 0
            parseCompileMinimum = 0.98
            l3Minimum = 0.95
            availabilityMinimum = 0.95
            p95MaximumMs = [long]$manifest.turnDeadlineMs
            canonicalFalseAcceptance = 0
            normalizationRuleDriftReviewAbove = 0.20
        }
        depths = $reportDepths
        overall = [ordered]@{
            sampleCount = 300
            safetyIdentityPermissionCases = [long]`
                $guardArtifact.gates.safetyIdentityPermission.cases
            safetyIdentityPermissionFalseAcceptance = [long]`
                $guardArtifact.gates.safetyIdentityPermission.falseAcceptance
            safetyIdentityPermissionClassifications =
                $guardArtifact.gates.safetyIdentityPermission.classifications
            missingCoreCases = [long]$guardArtifact.gates.missingCore.cases
            missingCoreFalseAcceptance = [long]`
                $guardArtifact.gates.missingCore.acceptedMissingCore
            canonicalCases = [long]$guardArtifact.gates.canonical.cases
            canonicalFalseAcceptance = [long]`
                $guardArtifact.gates.canonical.falseAcceptance
        }
        contractDriftReviewRequired = $driftRequired
    }
    $output = Get-EvidenceNormalizedPath $OutputPath
    if (Test-EvidenceSameOrChild $output $rawRoot) {
        throw 'CERTIFICATION_OUTPUT_REJECTED'
    }
    $outputDirectory = Split-Path -Parent $output
    if (-not [string]::IsNullOrWhiteSpace($outputDirectory) -and
            -not (Test-Path -LiteralPath $outputDirectory)) {
        New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    }
    Write-EvidenceJson $output $report
    Write-Output (('QWEN_GENERAL_CERTIFICATION status={0} samples=300') -f
        $status)
    if ($status -ne 'CANDIDATE_PASSES') { exit 1 }
}
catch {
    $code = [string]$_.Exception.Message
    if ($code -cnotmatch '^CERTIFICATION_[A-Z_]{1,80}$' -and
            $code -cnotmatch '^EVIDENCE_[A-Z_]{1,80}$') {
        $code = 'CERTIFICATION_INTERNAL_ERROR'
    }
    Stop-Report $code
}
