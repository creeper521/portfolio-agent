param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('PREPARE', 'SEAL')]
    [string]$Mode,
    [Parameter(Mandatory = $true)]
    [string]$EvidenceDirectory,
    [string]$RawArtifactRoot = '',
    [ValidateRange(1, 120000)]
    [int]$TurnDeadlineMs = 10000,
    [string]$CompletedReviewFile = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$script:EvidenceStage = 'BOOTSTRAP'
. (Join-Path $PSScriptRoot 'certification-evidence-common.ps1')

function Stop-EvidenceTool([string]$Code) {
    Write-Output $Code
    exit 1
}

function Assert-EvidenceDirectory(
    [string]$Directory,
    [string]$RawRoot,
    [bool]$MustExist
) {
    $path = Get-EvidenceNormalizedPath $Directory
    $owner = Get-EvidenceNormalizedPath (Split-Path -Parent $path)
    if ((Get-EvidenceNormalizedPath (Split-Path -Parent $owner)) -cne
            $RawRoot -or
            [System.IO.Path]::GetFileName($owner) -cnotmatch
                $script:EvidenceCapturePattern -or
            -not (Test-Path -LiteralPath $owner -PathType Container) -or
            (Test-EvidenceReparse $owner) -or
            [System.IO.Path]::GetFileName($path) -cnotmatch `
                '^evidence-[0-9a-f]{32}$' -or
            (Test-EvidenceReparse $path)) {
        throw 'EVIDENCE_DIRECTORY_REJECTED'
    }
    if ($MustExist -and
            -not (Test-Path -LiteralPath $path -PathType Container)) {
        throw 'EVIDENCE_DIRECTORY_REJECTED'
    }
    if (-not $MustExist -and (Test-Path -LiteralPath $path)) {
        throw 'EVIDENCE_DIRECTORY_REJECTED'
    }
    return $path
}

function Invoke-GuardSuite([string]$Directory) {
    $labFence = Join-Path $PSScriptRoot 'invoke-qwen-general-lab.test.ps1'
    $replayFence = Join-Path $PSScriptRoot 'replay-general-drafts.test.ps1'
    $maven = 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd'
    $guardPath = Get-EvidenceChainNodePath $Directory 'guardArtifact'
    $closurePath = Get-EvidenceChainNodePath `
        $Directory 'guardProducerClosure'
    $logs = @(
        (Join-Path $Directory '.lab-fence.log'),
        (Join-Path $Directory '.replay-fence.log'),
        (Join-Path $Directory '.java-guard.log'))
    $fixtureKey = $env:QWEN_GENERAL_REPLAY_SEMANTIC_FIXTURE_CASE_KEY
    $fixtureAuthorization =
        $env:QWEN_GENERAL_REPLAY_SEMANTIC_FIXTURE_AUTHORIZATION
    try {
        Remove-Item Env:QWEN_GENERAL_REPLAY_SEMANTIC_FIXTURE_CASE_KEY `
            -ErrorAction SilentlyContinue
        Remove-Item Env:QWEN_GENERAL_REPLAY_SEMANTIC_FIXTURE_AUTHORIZATION `
            -ErrorAction SilentlyContinue
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
            $labFence *> $logs[0]
        if ($LASTEXITCODE -ne 0 -or
                (Get-Content -LiteralPath $logs[0] -Raw -Encoding UTF8) `
                    -cnotmatch 'QWEN_GENERAL_LAB_FENCE_TESTS_OK\s*$') {
            throw 'EVIDENCE_GUARD_SUITE_FAILED'
        }
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
            $replayFence *> $logs[1]
        if ($LASTEXITCODE -ne 0 -or
                (Get-Content -LiteralPath $logs[1] -Raw -Encoding UTF8) `
                    -cnotmatch 'GENERAL_DUAL_REPLAY_TESTS_OK\s*$') {
            throw 'EVIDENCE_GUARD_SUITE_FAILED'
        }
        & $maven -q -f (Join-Path $script:EvidenceRepoRoot 'backend\pom.xml') `
            -DskipFrontend=true `
            -Dtest=QwenGeneralCertificationGuardTest `
            ("-DcertificationGuard.output=$guardPath") test *> $logs[2]
        if ($LASTEXITCODE -ne 0 -or
                -not (Test-Path -LiteralPath $guardPath -PathType Leaf)) {
            throw 'EVIDENCE_GUARD_SUITE_FAILED'
        }
        [void](Read-EvidenceGuardArtifact $guardPath $Directory)
        $closure = Get-EvidenceGuardProducerClosure
        Write-EvidenceJson $closurePath $closure
        [void](Read-EvidenceGuardProducerClosure $closurePath $Directory)
    }
    finally {
        $env:QWEN_GENERAL_REPLAY_SEMANTIC_FIXTURE_CASE_KEY = $fixtureKey
        $env:QWEN_GENERAL_REPLAY_SEMANTIC_FIXTURE_AUTHORIZATION =
            $fixtureAuthorization
        foreach ($log in $logs) {
            if (Test-Path -LiteralPath $log -PathType Leaf) {
                Remove-Item -LiteralPath $log -Force
            }
        }
    }
    return [ordered]@{
        artifactSha256 = Get-EvidenceFileSha256 $guardPath
        producerClosureSha256 = Get-EvidenceFileSha256 $closurePath
        producerSourceSha256 = [string]$closure.closureSha256
    }
}

function Invoke-MachineReplay([string]$RawRoot, [string]$OutputPath) {
    $runner = Join-Path $PSScriptRoot 'replay-general-drafts.ps1'
    $log = Join-Path (Split-Path -Parent $OutputPath) '.replay.log'
    $temporaryOutput = Join-Path ([System.IO.Path]::GetTempPath()) `
        ('qwen-general-replay-' + [guid]::NewGuid().ToString('N') + '.json')
    try {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $runner `
            -RawArtifactRoot $RawRoot -OutputPath $temporaryOutput *> $log
        if ($LASTEXITCODE -ne 0) {
            throw 'EVIDENCE_REPLAY_REJECTED'
        }
        [System.IO.File]::Copy($temporaryOutput, $OutputPath, $false)
    }
    finally {
        if (Test-Path -LiteralPath $log -PathType Leaf) {
            Remove-Item -LiteralPath $log -Force
        }
        if (Test-Path -LiteralPath $temporaryOutput -PathType Leaf) {
            Remove-Item -LiteralPath $temporaryOutput -Force
        }
    }
}

function New-ReviewEntry([string]$ReviewId) {
    return [ordered]@{
        reviewId = $ReviewId
        answersQuestion = $null
        definitionAccurateInformative = $null
        mechanismAccurate = $null
        noObviousErrorsOrContradictions = $null
        clearReadable = $null
        depthCriterion = $null
        decision = $null
    }
}

function Invoke-Prepare {
    $script:EvidenceStage = 'RAW_ROOT'
    $rawRoot = Assert-EvidenceRawRoot $RawArtifactRoot
    $evidence = Assert-EvidenceDirectory `
        $EvidenceDirectory $rawRoot $false
    $stagedReplay = Join-Path (Split-Path -Parent $rawRoot) `
        ('.qwen-general-replay-' + [guid]::NewGuid().ToString('N') + '.json')
    try {
        $script:EvidenceStage = 'CORPUS'
        $corpusMap = Get-EvidenceCorpusMap
        $script:EvidenceStage = 'ARTIFACTS'
        $directories = @(Get-ChildItem -LiteralPath $rawRoot -Directory -Force |
            Where-Object { $_.Name -cmatch $script:EvidenceCapturePattern })
        if ($directories.Count -ne 300) {
            throw 'EVIDENCE_ARTIFACT_SET_REJECTED'
        }
        $records = @{}
        foreach ($directory in $directories) {
            $record = Read-EvidenceArtifact $directory $rawRoot
            $key = $record.caseId + '|' + $record.depth
            if (-not $corpusMap.ContainsKey($key) -or
                    $records.ContainsKey($key)) {
                throw 'EVIDENCE_ARTIFACT_SET_REJECTED'
            }
            $records[$key] = $record
        }
        if ($records.Count -ne 300) {
            throw 'EVIDENCE_ARTIFACT_SET_REJECTED'
        }

        $script:EvidenceStage = 'REPLAY'
        Invoke-MachineReplay $rawRoot $stagedReplay
        New-Item -ItemType Directory -Path $evidence | Out-Null
        Protect-EvidenceAcl $evidence
        $replaySnapshot = Get-EvidenceChainNodePath `
            $evidence 'replayAggregate'
        [System.IO.File]::Copy($stagedReplay, $replaySnapshot, $false)
        $replay = Read-EvidenceReplay $replaySnapshot $evidence
        if ($replay.map.Count -ne 300) {
            throw 'EVIDENCE_ARTIFACT_SET_REJECTED'
        }
        foreach ($key in $records.Keys) {
            if (-not $replay.map.ContainsKey($key)) {
                throw 'EVIDENCE_ARTIFACT_SET_REJECTED'
            }
        }

        $script:EvidenceStage = 'GUARD_SUITE'
        $guardEvidence = Invoke-GuardSuite $evidence
        $script:EvidenceStage = 'BLIND_PACKAGE'
        $orderedKeys = @($corpusMap.Keys | Sort-Object)
        $reviewKeys = @($orderedKeys | Where-Object {
            $records[$_].responseObtained -and
                $replay.map[$_].sample.v4.outcome -cin @(
                    'EXACT', 'NORMALIZED', 'DEGRADED')
        })
        if ($reviewKeys.Count -lt 2) {
            throw 'EVIDENCE_REVIEW_SET_REJECTED'
        }
        $orders = @(
            @(Get-EvidenceShuffled $reviewKeys),
            @(Get-EvidenceShuffled $reviewKeys))
        if (($orders[0] -join '|') -ceq ($orders[1] -join '|')) {
            $first = $orders[1][0]
            $orders[1][0] = $orders[1][1]
            $orders[1][1] = $first
        }
        $packagePasses = [System.Collections.Generic.List[object]]::new()
        $mapPasses = [System.Collections.Generic.List[object]]::new()
        $reviewPasses = [System.Collections.Generic.List[object]]::new()
        for ($passIndex = 0; $passIndex -lt 2; $passIndex++) {
            $passId = [guid]::NewGuid().ToString('N')
            $entries = [System.Collections.Generic.List[object]]::new()
            $mappings = [System.Collections.Generic.List[object]]::new()
            $reviews = [System.Collections.Generic.List[object]]::new()
            foreach ($key in $orders[$passIndex]) {
                $reviewId = [guid]::NewGuid().ToString('N')
                $entries.Add([ordered]@{
                    reviewId = $reviewId
                    question = $corpusMap[$key].question
                    answer = $records[$key].answerText
                })
                $mappings.Add([ordered]@{
                    reviewId = $reviewId
                    caseId = $corpusMap[$key].caseId
                    depth = $corpusMap[$key].depth
                })
                $reviews.Add((New-ReviewEntry $reviewId))
            }
            $packagePasses.Add([ordered]@{
                reviewPassId = $passId
                entries = @($entries)
            })
            $mapPasses.Add([ordered]@{
                reviewPassId = $passId
                pass = if ($passIndex -eq 0) {
                    'FIRST'
                } else {
                    'BLINDED_SECOND'
                }
                mappings = @($mappings)
            })
            $reviewPasses.Add([ordered]@{
                reviewPassId = $passId
                reviews = @($reviews)
            })
        }
        $blindPackage = [ordered]@{
            schemaVersion = 'qwen-general-blind-package.v2'
            reviewMode = 'SINGLE_REVIEWER_BLINDED_SECOND_PASS'
            passes = @($packagePasses)
        }
        $unblindMap = [ordered]@{
            schemaVersion = 'qwen-general-unblind-map.v2'
            passes = @($mapPasses)
        }
        $reviewTemplate = [ordered]@{
            schemaVersion = 'qwen-general-blind-review-input.v2'
            reviewMode = 'SINGLE_REVIEWER_BLINDED_SECOND_PASS'
            passes = @($reviewPasses)
        }
        $packagePath = Get-EvidenceChainNodePath $evidence 'blindPackage'
        $mapPath = Get-EvidenceChainNodePath $evidence 'unblindMap'
        $templatePath = Get-EvidenceChainNodePath $evidence 'reviewInput'
        Write-EvidenceJson $packagePath $blindPackage
        Write-EvidenceJson $mapPath $unblindMap
        Write-EvidenceJson $templatePath $reviewTemplate

        $script:EvidenceStage = 'MANIFEST'
        $manifestSamples = [System.Collections.Generic.List[object]]::new()
        foreach ($key in $orderedKeys) {
            $record = $records[$key]
            $replaySample = $replay.map[$key]
            $rules = [ordered]@{}
            foreach ($property in @(
                $replaySample.sample.v4.normalizationRuleCounts.PSObject.Properties |
                    Sort-Object Name)) {
                $rules[$property.Name] = [long]$property.Value
            }
            $outcome = [string]$replaySample.sample.v4.outcome
            $failureLayer = [string]$replaySample.sample.v4.layer
            $semanticAccepted = $outcome -cin @(
                'EXACT', 'NORMALIZED', 'DEGRADED')
            $shapeAccepted = [bool]$record.responseObtained -and
                ($semanticAccepted -or $failureLayer -ceq 'SEMANTIC')
            if (($record.responseObtained -and
                        $outcome -ceq 'NOT_APPLICABLE') -or
                    (-not $record.responseObtained -and
                        $outcome -cne 'NOT_APPLICABLE')) {
                throw 'EVIDENCE_REPLAY_REJECTED'
            }
            $manifestSamples.Add([ordered]@{
                caseId = $record.caseId
                depth = $record.depth
                artifactId = $record.artifactId
                metadataSha256 = $record.metadataSha256
                responseSha256 = $record.responseSha256
                replaySampleSha256 = $replaySample.sha256
                transportOutcome = [string]$record.transportOutcome
                responseObtained = [bool]$record.responseObtained
                replayOutcome = $outcome
                failureLayer = $failureLayer
                shapeAccepted = $shapeAccepted
                semanticAccepted = $semanticAccepted
                latencyMs = [long]$record.latencyMs
                attemptCount = [long]$record.attemptCount
                normalizationRuleCounts = $rules
            })
        }
        $now = [datetimeoffset]::UtcNow
        $manifest = [ordered]@{
            schemaVersion = 'qwen-general-certification-manifest.v4'
            manifestId = [guid]::NewGuid().ToString('N')
            generatedAtUtc = $now.ToString('o')
            expiresAtUtc = $now.AddHours(24).ToString('o')
            certificationVersion = `
                'qwen-general-explanation-certification.v1'
            corpusVersion = 'qwen-general-explanation-corpus.v1'
            corpusSha256 = Get-EvidenceFileSha256 `
                $script:EvidenceCorpusPath
            selectionVersion = 'qwen-3-7-flash-v7'
            providerContract = 'general.provider-draft.v4'
            applicationContract = 'general.draft.v3'
            compilerProfile = 'general-provider-draft-compiler.v4'
            replayFixtureMode = [string]$replay.aggregate.fixtureMode
            replayFixtureCaseKey = if (
                $null -eq $replay.aggregate.fixtureCaseKey) {
                $null
            } else { [string]$replay.aggregate.fixtureCaseKey }
            candidateBundleSha256 = Get-EvidenceBundleSha256 `
                $script:CandidateBundleFiles
            compilerProfileSha256 = Get-EvidenceBundleSha256 `
                $script:CompilerProfileFiles
            legacyBaselineSourceBundleSha256 = Get-EvidenceBundleSha256 `
                $script:LegacyBaselineSnapshotFiles
            legacyBaselineExecutableSha256 = Get-EvidenceFileSha256 `
                (Join-Path $script:EvidenceRepoRoot `
                    $script:LegacyBaselineExecutableFile)
            generatorSha256 = Get-EvidenceFileSha256 $PSCommandPath
            replayAggregateSha256 = Get-EvidenceFileSha256 $replaySnapshot
            guardArtifactSha256 = $guardEvidence.artifactSha256
            guardProducerClosureSha256 = `
                $guardEvidence.producerClosureSha256
            guardProducerSourceSha256 = `
                $guardEvidence.producerSourceSha256
            blindPackageSha256 = Get-EvidenceFileSha256 $packagePath
            unblindMapSha256 = Get-EvidenceFileSha256 $mapPath
            turnDeadlineMs = $TurnDeadlineMs
            samples = @($manifestSamples)
        }
        Write-EvidenceJson (Get-EvidenceChainNodePath `
            $evidence 'certificationManifest') `
            $manifest
        Write-Output 'QWEN_GENERAL_EVIDENCE_PREPARED samples=300 passes=2'
    }
    catch {
        if (Test-Path -LiteralPath $evidence -PathType Container) {
            Remove-Item -LiteralPath $evidence -Recurse -Force
        }
        throw
    }
    finally {
        if (Test-Path -LiteralPath $stagedReplay -PathType Leaf) {
            Remove-Item -LiteralPath $stagedReplay -Force
        }
    }
}

function Get-ReviewDecision([object]$Review) {
    foreach ($field in @(
            'answersQuestion', 'definitionAccurateInformative',
            'mechanismAccurate', 'noObviousErrorsOrContradictions',
            'clearReadable', 'depthCriterion')) {
        if ($Review.$field -isnot [bool]) {
            throw 'EVIDENCE_REVIEW_REJECTED'
        }
    }
    $passed = [bool]$Review.answersQuestion -and
        [bool]$Review.definitionAccurateInformative -and
        [bool]$Review.mechanismAccurate -and
        [bool]$Review.noObviousErrorsOrContradictions -and
        [bool]$Review.clearReadable -and
        [bool]$Review.depthCriterion
    $expected = if ($passed) { 'PASS' } else { 'FAIL' }
    if ([string]$Review.decision -cne $expected) {
        throw 'EVIDENCE_REVIEW_REJECTED'
    }
    return $expected
}

function Invoke-Seal {
    $script:EvidenceStage = 'SEAL_DIRECTORY'
    $evidence = Get-EvidenceNormalizedPath $EvidenceDirectory
    $owner = Get-EvidenceNormalizedPath (Split-Path -Parent $evidence)
    $rawRoot = Get-EvidenceNormalizedPath (Split-Path -Parent $owner)
    [void](Assert-EvidenceRawRoot $rawRoot)
    [void](Assert-EvidenceDirectory $evidence $rawRoot $true)
    $manifestPath = Assert-EvidenceContainedLeaf `
        (Get-EvidenceChainNodePath $evidence 'certificationManifest') $evidence
    $packagePath = Assert-EvidenceContainedLeaf `
        (Get-EvidenceChainNodePath $evidence 'blindPackage') $evidence
    $mapPath = Assert-EvidenceContainedLeaf `
        (Get-EvidenceChainNodePath $evidence 'unblindMap') $evidence
    if ((Get-EvidenceNormalizedPath $CompletedReviewFile) -cne
            (Get-EvidenceNormalizedPath (Get-EvidenceChainNodePath `
                $evidence 'reviewInput'))) {
        throw 'EVIDENCE_REVIEW_REJECTED'
    }
    $reviewPath = Assert-EvidenceContainedLeaf $CompletedReviewFile $evidence
    $manifest = Read-EvidenceJson $manifestPath $evidence
    $package = Read-EvidenceJson $packagePath $evidence
    $map = Read-EvidenceJson $mapPath $evidence
    $reviewInput = Read-EvidenceJson $reviewPath $evidence
    if ($manifest.schemaVersion -cne `
            'qwen-general-certification-manifest.v4' -or
            $manifest.blindPackageSha256 -cne `
                (Get-EvidenceFileSha256 $packagePath) -or
            $manifest.unblindMapSha256 -cne `
                (Get-EvidenceFileSha256 $mapPath) -or
            -not (Test-EvidenceExactKeys $package @(
                'schemaVersion', 'reviewMode', 'passes')) -or
            $package.schemaVersion -cne 'qwen-general-blind-package.v2' -or
            -not (Test-EvidenceExactKeys $map @(
                'schemaVersion', 'passes')) -or
            $map.schemaVersion -cne 'qwen-general-unblind-map.v2' -or
            -not (Test-EvidenceExactKeys $reviewInput @(
                'schemaVersion', 'reviewMode', 'passes')) -or
            $reviewInput.schemaVersion -cne `
                'qwen-general-blind-review-input.v2') {
        throw 'EVIDENCE_CHAIN_REJECTED'
    }
    $packagePasses = @($package.passes)
    $mapPasses = @($map.passes)
    $reviewPasses = @($reviewInput.passes)
    if ($packagePasses.Count -ne 2 -or $mapPasses.Count -ne 2 -or
            $reviewPasses.Count -ne 2) {
        throw 'EVIDENCE_CHAIN_REJECTED'
    }
    $expectedReviewKeys = @{}
    foreach ($sample in @($manifest.samples | Where-Object {
        $_.semanticAccepted -eq $true
    })) {
        $expectedReviewKeys[([string]$sample.caseId + '|' +
            [string]$sample.depth)] = $true
    }
    $reviewCountPerPass = $expectedReviewKeys.Count
    if ($reviewCountPerPass -lt 2 -or $reviewCountPerPass -gt 300) {
        throw 'EVIDENCE_REVIEW_SET_REJECTED'
    }
    $sealedReviews = [System.Collections.Generic.List[object]]::new()
    $orderKeys = [System.Collections.Generic.List[string]]::new()
    $allReviewIds = @{}
    foreach ($mapPass in $mapPasses) {
        if (-not (Test-EvidenceExactKeys $mapPass @(
                    'reviewPassId', 'pass', 'mappings')) -or
                [string]$mapPass.reviewPassId -cnotmatch '^[0-9a-f]{32}$' -or
                [string]$mapPass.pass -cnotin @(
                    'FIRST', 'BLINDED_SECOND')) {
            throw 'EVIDENCE_CHAIN_REJECTED'
        }
        $packagePass = @($packagePasses | Where-Object {
            $_.reviewPassId -ceq $mapPass.reviewPassId
        })
        $reviewPass = @($reviewPasses | Where-Object {
            $_.reviewPassId -ceq $mapPass.reviewPassId
        })
        if ($packagePass.Count -ne 1 -or $reviewPass.Count -ne 1 -or
                @($packagePass[0].entries).Count -ne $reviewCountPerPass -or
                @($mapPass.mappings).Count -ne $reviewCountPerPass -or
                @($reviewPass[0].reviews).Count -ne $reviewCountPerPass) {
            throw 'EVIDENCE_CHAIN_REJECTED'
        }
        $packageById = @{}
        foreach ($entry in @($packagePass[0].entries)) {
            if (-not (Test-EvidenceExactKeys $entry @(
                        'reviewId', 'question', 'answer')) -or
                    [string]$entry.reviewId -cnotmatch '^[0-9a-f]{32}$' -or
                    [string]::IsNullOrWhiteSpace([string]$entry.question) -or
                    [string]::IsNullOrWhiteSpace([string]$entry.answer) -or
                    $packageById.ContainsKey([string]$entry.reviewId)) {
                throw 'EVIDENCE_CHAIN_REJECTED'
            }
            $packageById[[string]$entry.reviewId] = $entry
        }
        $reviewById = @{}
        foreach ($review in @($reviewPass[0].reviews)) {
            if (-not (Test-EvidenceExactKeys $review @(
                        'reviewId', 'answersQuestion',
                        'definitionAccurateInformative', 'mechanismAccurate',
                        'noObviousErrorsOrContradictions', 'clearReadable',
                        'depthCriterion', 'decision')) -or
                    [string]$review.reviewId -cnotmatch '^[0-9a-f]{32}$' -or
                    $reviewById.ContainsKey([string]$review.reviewId)) {
                throw 'EVIDENCE_REVIEW_REJECTED'
            }
            $reviewById[[string]$review.reviewId] = $review
        }
        $passOrder = [System.Collections.Generic.List[string]]::new()
        $passKeys = @{}
        foreach ($mapping in @($mapPass.mappings)) {
            $sampleKey = [string]$mapping.caseId + '|' +
                [string]$mapping.depth
            if (-not (Test-EvidenceExactKeys $mapping @(
                        'reviewId', 'caseId', 'depth')) -or
                    -not $packageById.ContainsKey([string]$mapping.reviewId) -or
                    -not $reviewById.ContainsKey([string]$mapping.reviewId) -or
                    $allReviewIds.ContainsKey([string]$mapping.reviewId) -or
                    [string]$mapping.depth -cnotin $script:EvidenceDepths -or
                    -not $expectedReviewKeys.ContainsKey($sampleKey) -or
                    $passKeys.ContainsKey($sampleKey)) {
                throw 'EVIDENCE_CHAIN_REJECTED'
            }
            $passKeys[$sampleKey] = $true
            $allReviewIds[[string]$mapping.reviewId] = $true
            $decision = Get-ReviewDecision `
                $reviewById[[string]$mapping.reviewId]
            $passOrder.Add(
                [string]$mapping.caseId + '|' + [string]$mapping.depth)
            $review = $reviewById[[string]$mapping.reviewId]
            $sealedReviews.Add([ordered]@{
                caseId = [string]$mapping.caseId
                depth = [string]$mapping.depth
                pass = [string]$mapPass.pass
                answersQuestion = [bool]$review.answersQuestion
                definitionAccurateInformative = `
                    [bool]$review.definitionAccurateInformative
                mechanismAccurate = [bool]$review.mechanismAccurate
                noObviousErrorsOrContradictions = `
                    [bool]$review.noObviousErrorsOrContradictions
                clearReadable = [bool]$review.clearReadable
                depthCriterion = [bool]$review.depthCriterion
                decision = $decision
            })
        }
        if ($passKeys.Count -ne $reviewCountPerPass) {
            throw 'EVIDENCE_CHAIN_REJECTED'
        }
        $orderKeys.Add(($passOrder -join '|'))
    }
    $sealedReviewCount = [int]$reviewCountPerPass * 2
    if ($allReviewIds.Count -ne $sealedReviewCount -or
            $orderKeys.Count -ne 2 -or
            $orderKeys[0] -ceq $orderKeys[1]) {
        throw 'EVIDENCE_BLINDING_REJECTED'
    }
    $sealed = [ordered]@{
        schemaVersion = 'qwen-general-sealed-review-evidence.v2'
        reviewMode = 'SINGLE_REVIEWER_BLINDED_SECOND_PASS'
        reviewLimitation = 'SINGLE_REVIEWER_BLINDED_SECOND_PASS'
        manifestSha256 = Get-EvidenceFileSha256 $manifestPath
        blindPackageSha256 = Get-EvidenceFileSha256 $packagePath
        unblindMapSha256 = Get-EvidenceFileSha256 $mapPath
        completedReviewSha256 = Get-EvidenceFileSha256 $reviewPath
        reviews = @($sealedReviews)
    }
    Write-EvidenceJson (Get-EvidenceChainNodePath `
        $evidence 'sealedReviewEvidence') `
        $sealed
    Write-Output (('QWEN_GENERAL_EVIDENCE_SEALED reviews={0} passes=2') -f
        $sealedReviewCount)
}

try {
    if ($Mode -ceq 'PREPARE') {
        Invoke-Prepare
    }
    else {
        Invoke-Seal
    }
}
catch {
    $code = [string]$_.Exception.Message
    if ($code -cnotmatch '^EVIDENCE_[A-Z_]{1,80}$') {
        $code = 'EVIDENCE_INTERNAL_ERROR_' + $script:EvidenceStage
    }
    Stop-EvidenceTool $code
}
