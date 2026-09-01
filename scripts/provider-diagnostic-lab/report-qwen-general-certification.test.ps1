$ErrorActionPreference = 'Stop'

$evidenceTool = Join-Path $PSScriptRoot `
    'new-qwen-general-certification-evidence.ps1'
$reporter = Join-Path $PSScriptRoot `
    'report-qwen-general-certification.ps1'
$reviewSchemaPath = Join-Path $PSScriptRoot `
    'qwen-general-blind-review.schema.json'
$corpusPath = Join-Path $PSScriptRoot `
    'qwen-general-explanation-corpus.v1.json'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('qwen-general-certification-test-' + [guid]::NewGuid().ToString('N'))
$rawRoot = Join-Path $fixtureRoot 'raw'
$evidenceDirectory = ''
$reportPath = Join-Path $fixtureRoot 'report.json'
. (Join-Path $PSScriptRoot 'certification-evidence-common.ps1')

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Write-Json([string]$Path, [object]$Value) {
    [System.IO.File]::WriteAllText(
        $Path,
        ($Value | ConvertTo-Json -Depth 30 -Compress),
        [System.Text.UTF8Encoding]::new($false))
}

function New-Draft([string]$Depth) {
    $period = [char]0x3002
    $definitionSentence = (-join (@([char]0x4E2D) * 6)) + $period
    $definitionDetail = (-join (@([char]0x6587) * 7)) + $period
    $mechanismSentence = (-join (@([char]0x673A) * 8)) + $period
    $mechanismDetail = (-join (@([char]0x5236) * 9)) + $period
    if ($Depth -ceq 'DETAILED') {
        return [ordered]@{
            definition = @(($definitionSentence + $definitionDetail +
                $definitionSentence + $definitionDetail))
            mechanism = @(($mechanismSentence + $mechanismDetail +
                $mechanismSentence + $mechanismDetail))
            caveats = @()
        }
    }
    return [ordered]@{
        definition = @($definitionSentence)
        mechanism = @($mechanismSentence)
        caveats = @()
    }
}

function Write-Artifact(
    [object]$Case,
    [string]$Depth,
    [bool]$TransportFailure
) {
    $created = [datetimeoffset]::UtcNow.AddHours(-1)
    $artifactId = ('capture-{0}-{1}-{2}-{3}' -f
        [string]$Case.caseId,
        $Depth.ToLowerInvariant(),
        $created.ToString('yyyyMMddTHHmmssZ'),
        [guid]::NewGuid().ToString('N'))
    $directory = Join-Path $rawRoot $artifactId
    New-Item -ItemType Directory -Path $directory | Out-Null
    Write-Json (Join-Path $directory 'metadata.json') ([ordered]@{
        schemaVersion = 'qwen-general-lab-artifact.v2'
        artifactId = $artifactId
        caseId = [string]$Case.caseId
        depth = $Depth
        createdAtUtc = $created.ToString('o')
        expiresAtUtc = $created.AddHours(24).ToString('o')
        operatorIdentitySha256 = ('a' * 64)
        provider = 'QWEN'
        model = 'qwen3.7-flash'
        selectionVersion = 'qwen-3-7-flash-v8'
        providerContract = 'general.provider-draft.v4'
        compilerProfile = 'general-provider-draft-compiler.v4'
        captureSource = 'TEST_LOOPBACK'
        status = if ($TransportFailure) {
            'TRANSPORT_FAILED'
        } else {
            'CAPTURED'
        }
        httpClass = if ($TransportFailure) {
            'TRANSPORT_UNAVAILABLE'
        } else {
            'SUCCESS'
        }
        latencyBucket = 'FROM_500_TO_1999_MS'
        latencyMs = if ($Depth -ceq 'DETAILED' -and
                [string]$Case.caseId -match '^java-spring-00[1-6]$') {
            1500
        } else { 500 }
        attemptCount = if ($TransportFailure) { 2 } else { 1 }
    })
    if ($TransportFailure) { return }
    $draft = if ($Depth -ceq 'STANDARD' -and
            [string]$Case.caseId -match '^java-spring-00[1-3]$') {
        [ordered]@{
            mechanism = 'Only mechanism is present.'
            caveats = @()
        }
    }
    else { New-Draft $Depth }
    $arguments = $draft | ConvertTo-Json -Depth 12 -Compress
    Write-Json (Join-Path $directory 'response.raw.json') ([ordered]@{
        model = 'qwen3.7-flash'
        choices = @([ordered]@{
            finish_reason = 'stop'
            message = [ordered]@{
                content = $null
                refusal = $null
                tool_calls = @([ordered]@{
                    type = 'function'
                    function = [ordered]@{
                        name = 'emit_general_provider_draft_v4'
                        arguments = $arguments
                    }
                })
            }
        })
    })
}

function Invoke-Script([string]$Path, [string[]]$Arguments) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $Path @Arguments 2>&1 | Out-String)
        return @{ ExitCode = $LASTEXITCODE; Output = $output }
    }
    finally { $ErrorActionPreference = $previous }
}

function Invoke-EvidenceSuccess([hashtable]$Parameters) {
    $output = @(& $evidenceTool @Parameters 2>&1)
    return @{ ExitCode = 0; Output = ($output -join [Environment]::NewLine) }
}

function Set-TamperedChainNode([string]$Name, [string]$Path) {
    $value = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 |
        ConvertFrom-Json
    switch ($Name) {
        'replayAggregate' {
            $value.samples[0].v4.reason = 'TAMPERED'
        }
        'guardArtifact' {
            $value.gates.canonical.falseAcceptance =
                [long]$value.gates.canonical.falseAcceptance + 1
        }
        'guardProducerClosure' {
            $value.classes[0].sourceSha256 = '0' * 64
        }
        'blindPackage' {
            $value.passes[0].entries[0].question += ' tampered'
        }
        'unblindMap' {
            $value.passes[0].mappings[0].caseId = 'tampered-999'
        }
        'reviewInput' {
            $value.passes[0].reviews[0].answersQuestion = $false
        }
        'certificationManifest' {
            $value.samples[0].metadataSha256 = '0' * 64
        }
        'sealedReviewEvidence' {
            $value.reviews[0].decision = 'FAIL'
        }
        default { throw "Unknown evidence-chain node: $Name" }
    }
    Write-Json $Path $value
}

try {
    $semanticReject = [pscustomobject]@{
        responseObtained = $true
        replayOutcome = 'INCOMPLETE'
        failureLayer = 'SEMANTIC'
        shapeAccepted = $true
        semanticAccepted = $false
    }
    $semanticAdmission = Get-EvidenceAdmissionSummary @($semanticReject) 0
    Assert-True ($semanticAdmission.responseCount -eq 1 -and
            $semanticAdmission.shapeAccepted -eq 1 -and
            $semanticAdmission.semanticDenominator -eq 1 -and
            $semanticAdmission.semanticAccepted -eq 0 -and
            $semanticAdmission.blindReviewDenominator -eq 0 -and
            $semanticAdmission.l3TaskDenominator -eq 1 -and
            $semanticAdmission.l3Success -eq 0) `
        ('A semantic-layer rejection must preserve shape admission, stay ' +
            'outside blind review, and remain a failed L3 task.')

    Assert-True (Test-Path -LiteralPath $evidenceTool -PathType Leaf) `
        'The certification evidence tool must exist.'
    Assert-True (Test-Path -LiteralPath $reporter -PathType Leaf) `
        'The certification reporter must exist.'
    New-Item -ItemType Directory -Path $rawRoot -Force | Out-Null
    Write-Json (Join-Path $rawRoot `
        '.qwen-general-provider-lab-root.v1.json') ([ordered]@{
        schemaVersion = 'qwen-general-provider-lab-root.v1'
        rootId = [guid]::NewGuid().ToString('N')
        createdAtUtc = [datetimeoffset]::UtcNow.ToString('o')
    })
    $corpus = Get-Content -LiteralPath $corpusPath -Raw -Encoding UTF8 |
        ConvertFrom-Json
    $transportFailureCount = 0
    foreach ($case in @($corpus.cases)) {
        foreach ($depth in @('CONCISE', 'STANDARD', 'DETAILED')) {
            $transportFailure = $depth -ceq 'CONCISE' -and
                $transportFailureCount -lt 6
            Write-Artifact $case $depth $transportFailure
            if ($transportFailure) { $transportFailureCount++ }
        }
    }
    $evidenceOwner = Get-ChildItem -LiteralPath $rawRoot -Directory `
        -Filter 'capture-*' | Where-Object {
            Test-Path -LiteralPath (Join-Path $_.FullName 'response.raw.json')
        } | Sort-Object Name | Select-Object -First 1
    $evidenceDirectory = Join-Path $evidenceOwner.FullName `
        ('evidence-' + [guid]::NewGuid().ToString('N'))

    $wideTempRoot = [System.IO.Path]::GetFullPath(
        [System.IO.Path]::GetTempPath()).TrimEnd('\')
    $wideOwner = Join-Path $wideTempRoot `
        ('capture-java-spring-001-concise-20260828T000000Z-' +
            [guid]::NewGuid().ToString('N'))
    $wideEvidence = Join-Path $wideOwner `
        ('evidence-' + [guid]::NewGuid().ToString('N'))
    $wideAcl = (Get-Acl -LiteralPath $wideTempRoot).Sddl
    foreach ($entry in @(
        @{ Name = 'PREPARE'; Arguments = @(
            '-Mode', 'PREPARE', '-RawArtifactRoot', $wideTempRoot,
            '-EvidenceDirectory', $wideEvidence) },
        @{ Name = 'SEAL'; Arguments = @(
            '-Mode', 'SEAL', '-EvidenceDirectory', $wideEvidence,
            '-CompletedReviewFile', (Join-Path $wideEvidence 'review.json')) },
        @{ Name = 'REPORT'; Path = $reporter; Arguments = @(
            '-CertificationManifest', (Join-Path $wideEvidence 'manifest.json'),
            '-ReviewEvidence', (Join-Path $wideEvidence 'review.json'),
            '-OutputPath', $reportPath) })) {
        $entryPath = if ($entry.Path) { $entry.Path } else { $evidenceTool }
        $wideResult = Invoke-Script $entryPath $entry.Arguments
        Assert-True ($wideResult.ExitCode -eq 1 -and
                $wideResult.Output -match `
                    '^(?:EVIDENCE|CERTIFICATION)_RAW_ROOT_REJECTED\s*$' -and
                (Get-Acl -LiteralPath $wideTempRoot).Sddl -ceq $wideAcl) `
            "$($entry.Name) must reject a broad temp root before ACL mutation."
    }

    $unmarkedRoot = Join-Path $fixtureRoot 'unmarked-certification-root'
    $unmarkedOwner = Join-Path $unmarkedRoot `
        ('capture-java-spring-001-concise-20260828T000000Z-' +
            [guid]::NewGuid().ToString('N'))
    $unmarkedEvidence = Join-Path $unmarkedOwner `
        ('evidence-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $unmarkedEvidence -Force | Out-Null
    $unmarkedAcl = (Get-Acl -LiteralPath $unmarkedRoot).Sddl
    foreach ($entry in @(
        @{ Name = 'PREPARE'; Arguments = @(
            '-Mode', 'PREPARE', '-RawArtifactRoot', $unmarkedRoot,
            '-EvidenceDirectory', $unmarkedEvidence) },
        @{ Name = 'SEAL'; Arguments = @(
            '-Mode', 'SEAL', '-EvidenceDirectory', $unmarkedEvidence,
            '-CompletedReviewFile', (Join-Path $unmarkedEvidence 'review.json')) },
        @{ Name = 'REPORT'; Path = $reporter; Arguments = @(
            '-CertificationManifest', (Join-Path $unmarkedEvidence 'manifest.json'),
            '-ReviewEvidence', (Join-Path $unmarkedEvidence 'review.json'),
            '-OutputPath', $reportPath) })) {
        $entryPath = if ($entry.Path) { $entry.Path } else { $evidenceTool }
        $unmarkedResult = Invoke-Script $entryPath $entry.Arguments
        Assert-True ($unmarkedResult.ExitCode -eq 1 -and
                $unmarkedResult.Output -match `
                    '^(?:EVIDENCE|CERTIFICATION)_RAW_ROOT_REJECTED\s*$' -and
                (Get-Acl -LiteralPath $unmarkedRoot).Sddl -ceq $unmarkedAcl) `
            "$($entry.Name) must reject a missing marker before ACL mutation."
    }

    Set-Content -LiteralPath (Join-Path $rawRoot '.loopback-test-trace') `
        -Value 'TEST_ONLY' -Encoding ASCII -NoNewline
    $traceRejected = Invoke-Script $evidenceTool @(
        '-Mode', 'PREPARE', '-RawArtifactRoot', $rawRoot,
        '-EvidenceDirectory', $evidenceDirectory)
    Assert-True ($traceRejected.ExitCode -eq 1 -and
            $traceRejected.Output -match `
                '^EVIDENCE_LOOPBACK_TRACE_REJECTED\s*$') `
        'Formal evidence preparation must reject a loopback trace marker.'
    Remove-Item -LiteralPath (Join-Path $rawRoot '.loopback-test-trace') -Force

    $fakeRejected = Invoke-Script $evidenceTool @(
        '-Mode', 'PREPARE', '-RawArtifactRoot', $rawRoot,
        '-EvidenceDirectory', $evidenceDirectory)
    Assert-True ($fakeRejected.ExitCode -eq 1 -and
            $fakeRejected.Output -match `
                '^EVIDENCE_CAPTURE_SOURCE_REJECTED\s*$' -and
            $fakeRejected.Output -notmatch 'CANDIDATE_PASSES') `
        'Three hundred TEST_LOOPBACK artifacts must not enter certification.'
    foreach ($metadataPath in @(Get-ChildItem -LiteralPath $rawRoot `
            -Directory -Filter 'capture-*' | ForEach-Object {
                Join-Path $_.FullName 'metadata.json'
            })) {
        $metadata = Get-Content -LiteralPath $metadataPath -Raw -Encoding UTF8 |
            ConvertFrom-Json
        $metadata.captureSource = 'REAL_PROVIDER'
        Write-Json $metadataPath $metadata
    }

    $ttlMetadataPath = @(Get-ChildItem -LiteralPath $rawRoot `
        -Directory -Filter 'capture-*' | Sort-Object Name | ForEach-Object {
            Join-Path $_.FullName 'metadata.json'
        })[0]
    $exactTtlMetadataText = Get-Content -LiteralPath $ttlMetadataPath `
        -Raw -Encoding UTF8
    $overlongTtlMetadata = $exactTtlMetadataText | ConvertFrom-Json
    $ttlCreated = [datetimeoffset]::Parse(
        [string]$overlongTtlMetadata.createdAtUtc)
    $ttlExpires = $ttlCreated.AddHours(24).AddMilliseconds(1)
    $overlongTtlMetadata.expiresAtUtc = $ttlExpires.ToString('o')
    Write-Json $ttlMetadataPath $overlongTtlMetadata
    $overlongEvidenceTtl = Invoke-Script $evidenceTool @(
        '-Mode', 'PREPARE', '-RawArtifactRoot', $rawRoot,
        '-EvidenceDirectory', $evidenceDirectory,
        '-TurnDeadlineMs', '1000')
    Assert-True ($overlongEvidenceTtl.ExitCode -eq 1 -and
            $overlongEvidenceTtl.Output -match
                '^EVIDENCE_METADATA_REJECTED\s*$') `
        'Evidence preparation must reject a TTL of 24 hours plus one millisecond.'
    [System.IO.File]::WriteAllText(
        $ttlMetadataPath, $exactTtlMetadataText,
        [System.Text.UTF8Encoding]::new($false))

    $priorFixtureKey = $env:QWEN_GENERAL_REPLAY_SEMANTIC_FIXTURE_CASE_KEY
    $priorFixtureAuthorization =
        $env:QWEN_GENERAL_REPLAY_SEMANTIC_FIXTURE_AUTHORIZATION
    try {
        $env:QWEN_GENERAL_REPLAY_SEMANTIC_FIXTURE_CASE_KEY =
            'java-spring-010|STANDARD'
        $env:QWEN_GENERAL_REPLAY_SEMANTIC_FIXTURE_AUTHORIZATION =
            'AUTHORIZED_POST_CANONICAL_VALIDATOR_FIXTURE_ONLY'
        $prepared = Invoke-EvidenceSuccess @{
            Mode = 'PREPARE'
            RawArtifactRoot = $rawRoot
            EvidenceDirectory = $evidenceDirectory
            TurnDeadlineMs = 1000
        }
    }
    finally {
        $env:QWEN_GENERAL_REPLAY_SEMANTIC_FIXTURE_CASE_KEY = $priorFixtureKey
        $env:QWEN_GENERAL_REPLAY_SEMANTIC_FIXTURE_AUTHORIZATION =
            $priorFixtureAuthorization
    }
    Assert-True ($prepared.ExitCode -eq 0 -and
            $prepared.Output -match
                '^QWEN_GENERAL_EVIDENCE_PREPARED samples=300 passes=2\s*$') `
        "Machine evidence preparation failed: $($prepared.Output)"

    $manifestPath = Join-Path $evidenceDirectory `
        $script:EvidenceChainNodeNames.certificationManifest
    $packagePath = Join-Path $evidenceDirectory `
        $script:EvidenceChainNodeNames.blindPackage
    $mapPath = Join-Path $evidenceDirectory `
        $script:EvidenceChainNodeNames.unblindMap
    $reviewInputPath = Join-Path $evidenceDirectory `
        $script:EvidenceChainNodeNames.reviewInput
    $replayPath = Join-Path $evidenceDirectory `
        $script:EvidenceChainNodeNames.replayAggregate
    $sealedPath = Join-Path $evidenceDirectory `
        $script:EvidenceChainNodeNames.sealedReviewEvidence
    $guardPath = Join-Path $evidenceDirectory `
        $script:EvidenceChainNodeNames.guardArtifact
    $guardClosurePath = Join-Path $evidenceDirectory `
        $script:EvidenceChainNodeNames.guardProducerClosure
    $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 |
        ConvertFrom-Json
    Assert-True ($manifest.schemaVersion -ceq
            'qwen-general-certification-manifest.v4' -and
            @($manifest.samples).Count -eq 300 -and
            $manifest.replayFixtureMode -ceq
                'TEST_ONLY_POST_CANONICAL_SEMANTIC_REJECT' -and
            $manifest.replayFixtureCaseKey -ceq
                'java-spring-010|STANDARD' -and
            [string]$manifest.guardArtifactSha256 -match '^[0-9a-f]{64}$' -and
            [string]$manifest.guardProducerClosureSha256 -match
                '^[0-9a-f]{64}$' -and
            [string]$manifest.guardProducerSourceSha256 -match '^[0-9a-f]{64}$' -and
            [string]$manifest.legacyBaselineSourceBundleSha256 -match `
                '^[0-9a-f]{64}$' -and
            [string]$manifest.legacyBaselineExecutableSha256 -match `
                '^[0-9a-f]{64}$') `
        'The manifest must machine-bind all 300 captures and guard evidence.'
    $manifestGenerated = [datetimeoffset]::Parse(
        [string]$manifest.generatedAtUtc)
    $manifestExpires = [datetimeoffset]::Parse(
        [string]$manifest.expiresAtUtc)
    Assert-True (($manifestExpires - $manifestGenerated) -eq
            [timespan]::FromHours(24)) `
        'The machine-generated certification manifest must use an exact 24-hour TTL.'
    $guard = Get-Content -LiteralPath $guardPath -Raw -Encoding UTF8 |
        ConvertFrom-Json
    $classifications = $guard.gates.safetyIdentityPermission.classifications
    Assert-True ($guard.schemaVersion -ceq
            'qwen-general-guard-artifact.v3' -and
            $guard.gates.safetyIdentityPermission.cases -gt 0 -and
            $guard.gates.safetyIdentityPermission.falseAcceptance -eq 0 -and
            $classifications.PROVIDER_MODEL_REF.cases -gt 0 -and
            $classifications.PROVIDER_MODEL_REF.falseAcceptance -eq 0 -and
            $classifications.SELECTION_VERSION.cases -gt 0 -and
            $classifications.SELECTION_VERSION.falseAcceptance -eq 0 -and
            $classifications.OPERATION_BINDING.cases -gt 0 -and
            $classifications.OPERATION_BINDING.falseAcceptance -eq 0 -and
            $classifications.PROTOCOL_PROFILE.cases -gt 0 -and
            $classifications.PROTOCOL_PROFILE.falseAcceptance -eq 0 -and
            $classifications.RESPONSE_MODEL_IDENTITY.cases -gt 0 -and
            $classifications.RESPONSE_MODEL_IDENTITY.falseAcceptance -eq 0 -and
            $classifications.REQUIRED_TOOL_ENVELOPE.cases -gt 0 -and
            $classifications.REQUIRED_TOOL_ENVELOPE.falseAcceptance -eq 0 -and
            $classifications.TOOL_ARGUMENTS_NOT_AUTHORIZATION.cases -gt 0 -and
            $classifications.TOOL_ARGUMENTS_NOT_AUTHORIZATION.
                falseAcceptance -eq 0 -and
            $classifications.SECRET_LIKE_OUTBOUND.cases -gt 0 -and
            $classifications.SECRET_LIKE_OUTBOUND.falseAcceptance -eq 0 -and
            $guard.gates.missingCore.cases -gt 0 -and
            $guard.gates.missingCore.acceptedMissingCore -eq 0 -and
            $guard.gates.canonical.cases -gt 0 -and
            $guard.gates.canonical.falseAcceptance -eq 0) `
        'Guard evidence must contain executed non-empty zero-tolerance matrices.'
    foreach ($sample in @($manifest.samples)) {
        $responseHashValid = if ($sample.responseObtained) {
            [string]$sample.responseSha256 -match '^[0-9a-f]{64}$'
        } else {
            $null -eq $sample.responseSha256
        }
        Assert-True ([string]$sample.metadataSha256 -match '^[0-9a-f]{64}$' -and
                $responseHashValid -and
                [string]$sample.replaySampleSha256 -match '^[0-9a-f]{64}$') `
            'Every sample must bind metadata, response, and replay hashes.'
    }

    $packageText = Get-Content -LiteralPath $packagePath -Raw -Encoding UTF8
    $package = $packageText | ConvertFrom-Json
    $unblind = Get-Content -LiteralPath $mapPath -Raw -Encoding UTF8 |
        ConvertFrom-Json
    Assert-True (@($package.passes).Count -eq 2 -and
            @($package.passes[0].entries).Count -eq 290 -and
            @($package.passes[1].entries).Count -eq 290) `
        'The tool must create two full blind passes.'
    Assert-True ($packageText -notmatch '"caseId"\s*:' -and
            $packageText -notmatch '"depth"\s*:' -and
            $packageText -notmatch '"pass"\s*:') `
        'The blind package must not disclose case, depth, or pass identity.'
    $firstOrder = @($unblind.passes[0].mappings | ForEach-Object {
        $_.caseId + '|' + $_.depth
    }) -join ','
    $secondOrder = @($unblind.passes[1].mappings | ForEach-Object {
        $_.caseId + '|' + $_.depth
    }) -join ','
    Assert-True ($firstOrder -cne $secondOrder) `
        'The two blind passes must use independently shuffled order.'

    $reviewInput = Get-Content -LiteralPath $reviewInputPath `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $failedReviewIds = @{}
    foreach ($pass in @($unblind.passes)) {
        foreach ($mapping in @($pass.mappings | Where-Object {
            $_.depth -ceq 'DETAILED' -and
                [string]$_.caseId -match '^java-spring-00[1-6]$'
        })) {
            $failedReviewIds[[string]$mapping.reviewId] = $true
        }
    }
    Assert-True ($failedReviewIds.Count -eq 12) `
        'Both blind passes must map the six detailed L3 failure fixtures.'
    foreach ($pass in @($reviewInput.passes)) {
        foreach ($review in @($pass.reviews)) {
            $passesReview = -not $failedReviewIds.ContainsKey(
                [string]$review.reviewId)
            $review.answersQuestion = $passesReview
            $review.definitionAccurateInformative = $true
            $review.mechanismAccurate = $true
            $review.noObviousErrorsOrContradictions = $true
            $review.clearReadable = $true
            $review.depthCriterion = $true
            $review.decision = if ($passesReview) { 'PASS' } else { 'FAIL' }
        }
    }
    Write-Json $reviewInputPath $reviewInput
    $sealed = Invoke-EvidenceSuccess @{
        Mode = 'SEAL'
        EvidenceDirectory = $evidenceDirectory
        CompletedReviewFile = $reviewInputPath
    }
    Assert-True ($sealed.ExitCode -eq 0 -and
            $sealed.Output -match
                '^QWEN_GENERAL_EVIDENCE_SEALED reviews=580 passes=2\s*$') `
        "Blind review sealing failed: $($sealed.Output)"

    $notReady = Invoke-Script $reporter @(
        '-CertificationManifest', $manifestPath,
        '-ReviewEvidence', $sealedPath,
        '-OutputPath', $reportPath)
    Assert-True ($notReady.ExitCode -eq 1 -and
            $notReady.Output -match
                '^QWEN_GENERAL_CERTIFICATION status=NOT_READY samples=300\s*$' -and
            $notReady.Output -notmatch 'CANDIDATE_PASSES') `
        "Independent threshold failures must block READY: $($notReady.Output)"
    $report = Get-Content -LiteralPath $reportPath -Raw -Encoding UTF8 |
        ConvertFrom-Json
    Assert-True ($report.schemaVersion -ceq
            'qwen-general-certification-report.v4' -and
            $report.status -ceq 'NOT_READY' -and
            $report.replayFixtureMode -ceq
                'TEST_ONLY_POST_CANONICAL_SEMANTIC_REJECT' -and
            $report.replayFixtureCaseKey -ceq
                'java-spring-010|STANDARD' -and
            $report.reviewLimitation -ceq
                'SINGLE_REVIEWER_BLINDED_SECOND_PASS') `
        'The report must retain candidate-only and review-limitation semantics.'
    foreach ($depth in @('CONCISE', 'STANDARD', 'DETAILED')) {
        Assert-True ($report.depths.$depth.sampleCount -eq 100 -and
                $report.depths.$depth.status -ceq 'FAIL' -and
                $report.depths.$depth.gates.
                    safetyIdentityPermissionFalseAcceptanceZero) `
            "$depth must retain a passing safety gate while another threshold fails."
    }
    Assert-True ($report.depths.CONCISE.responseCount -eq 94 -and
            $report.depths.CONCISE.availabilityRate -eq 0.94 -and
            $report.depths.CONCISE.l3TaskDenominator -eq 100 -and
            $report.depths.CONCISE.l3SuccessRate -eq 0.94 -and
            -not $report.depths.CONCISE.gates.
                availabilityAtLeast95Percent) `
        'A 94 percent availability fixture must cross the threshold and stay in the L3 task denominator.'
    Assert-True ($report.depths.STANDARD.responseCount -eq 100 -and
            $report.depths.STANDARD.shapeDenominator -eq 100 -and
            $report.depths.STANDARD.parseCompileAccepted -eq 97 -and
            $report.depths.STANDARD.parseCompileRate -eq 0.97 -and
            -not $report.depths.STANDARD.gates.parseCompileAtLeast98Percent -and
            $report.depths.STANDARD.semanticDenominator -eq 97 -and
            $report.depths.STANDARD.semanticAccepted -eq 96 -and
            $report.depths.STANDARD.blindReviewDenominator -eq 96 -and
            $report.depths.STANDARD.l3TaskDenominator -eq 100 -and
            $report.depths.STANDARD.l3Success -eq 96) `
        ('A 97 percent parse/compile fixture must retain its own denominator ' +
            'and the fixed L3 task denominator. actual=' +
            ($report.depths.STANDARD | ConvertTo-Json -Depth 8 -Compress))
    $semanticSample = @($manifest.samples | Where-Object {
        $_.caseId -ceq 'java-spring-010' -and $_.depth -ceq 'STANDARD'
    })[0]
    Assert-True ($semanticSample.responseObtained -eq $true -and
            $semanticSample.shapeAccepted -eq $true -and
            $semanticSample.semanticAccepted -eq $false -and
            $semanticSample.failureLayer -ceq 'SEMANTIC' -and
            $semanticSample.replayOutcome -ceq 'INCOMPLETE') `
        ('The complete chain must bind the test-only post-canonical real ' +
            'validator rejection without misclassifying shape admission.')
    Assert-True ($report.depths.DETAILED.responseCount -eq 100 -and
            $report.depths.DETAILED.parseCompileAccepted -eq 100 -and
            $report.depths.DETAILED.semanticAccepted -eq 100 -and
            $report.depths.DETAILED.blindReviewDenominator -eq 100 -and
            $report.depths.DETAILED.l3TaskDenominator -eq 100 -and
            $report.depths.DETAILED.l3Success -eq 94 -and
            $report.depths.DETAILED.l3SuccessRate -eq 0.94 -and
            -not $report.depths.DETAILED.gates.l3AtLeast95Percent -and
            $report.depths.DETAILED.p95LatencyMs -eq 1500 -and
            -not $report.depths.DETAILED.gates.p95WithinTurnDeadline -and
            $report.depths.CONCISE.p95LatencyMs -eq 500 -and
            $report.depths.CONCISE.gates.p95WithinTurnDeadline) `
        'The fixture must independently cross 94 percent L3 and the deadline threshold.'

    $manifestText = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8
    $overlongManifest = $manifestText | ConvertFrom-Json
    $overlongGenerated = [datetimeoffset]::Parse(
        [string]$overlongManifest.generatedAtUtc)
    $overlongManifestExpires =
        ($overlongGenerated.AddHours(24)).AddMilliseconds(1)
    $overlongManifest.expiresAtUtc = $overlongManifestExpires.ToString('o')
    Write-Json $manifestPath $overlongManifest
    $overlongManifestTtl = Invoke-Script $reporter @(
        '-CertificationManifest', $manifestPath,
        '-ReviewEvidence', $sealedPath,
        '-OutputPath', $reportPath)
    Assert-True ($overlongManifestTtl.ExitCode -eq 1 -and
            $overlongManifestTtl.Output -match
                '^CERTIFICATION_MANIFEST_REJECTED\s*$') `
        'Reporter must reject a manifest TTL of 24 hours plus one millisecond.'
    [System.IO.File]::WriteAllText(
        $manifestPath, $manifestText,
        [System.Text.UTF8Encoding]::new($false))

    $directDependency = Join-Path $script:EvidenceRepoRoot `
        'backend\src\test\java\com\portfolio\agent\infrastructure\model\QwenGeneralCertificationGuardSupport.java'
    $directDependencyText = Get-Content -LiteralPath $directDependency `
        -Raw -Encoding UTF8
    $directDependencyHash = Get-EvidenceFileSha256 $directDependency
    $directDependencyMtime = (Get-Item -LiteralPath $directDependency).
        LastWriteTimeUtc
    $overrideRoot = Join-Path $fixtureRoot `
        ('guard-source-override-test-' + [guid]::NewGuid().ToString('N'))
    $overrideSource = Join-Path $overrideRoot `
        $script:GuardTestOnlyOverrideRelative
    New-Item -ItemType Directory -Path (Split-Path -Parent $overrideSource) `
        -Force | Out-Null
    [System.IO.File]::WriteAllText(
        (Join-Path $overrideRoot $script:GuardTestOnlyOverrideMarker),
        'QWEN_GENERAL_GUARD_SOURCE_OVERRIDE_TEST_ONLY_V1',
        [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText(
        $overrideSource,
        $directDependencyText + "`n// isolated-source-drift-fixture`n",
        [System.Text.UTF8Encoding]::new($false))
    $sourceDriftCode = ''
    try {
        [void](Read-EvidenceGuardProducerClosure `
            $guardClosurePath $evidenceDirectory $overrideRoot)
    }
    catch {
        $sourceDriftCode = [string]$_.Exception.Message
    }
    Assert-True ($sourceDriftCode -ceq
            'CERTIFICATION_SOURCE_DRIFT_REJECTED') `
        ('A changed real direct dependency in an isolated test-only source ' +
            'root must invalidate the previous mechanical closure.')
    Assert-True ((Get-EvidenceFileSha256 $directDependency) -ceq
            $directDependencyHash -and
            (Get-Content -LiteralPath $directDependency -Raw -Encoding UTF8) `
                -ceq $directDependencyText -and
            (Get-Item -LiteralPath $directDependency).LastWriteTimeUtc -eq
                $directDependencyMtime) `
        'The source-drift test must not change main-worktree hash, content, or mtime.'

    $tamperedManifest = $manifestText | ConvertFrom-Json
    $tamperedManifest.samples[0].responseSha256 = '0' * 64
    Write-Json $manifestPath $tamperedManifest
    $tampered = Invoke-Script $reporter @(
        '-CertificationManifest', $manifestPath,
        '-ReviewEvidence', $sealedPath,
        '-OutputPath', $reportPath)
    Assert-True ($tampered.ExitCode -eq 1 -and
            $tampered.Output -match '^CERTIFICATION_') `
        'A tampered manifest must fail closed.'
    [System.IO.File]::WriteAllText(
        $manifestPath, $manifestText,
        [System.Text.UTF8Encoding]::new($false))

    $sealedText = Get-Content -LiteralPath $sealedPath -Raw -Encoding UTF8
    $tamperedReview = $sealedText | ConvertFrom-Json
    $tamperedReview.reviews[0].decision = 'FAIL'
    Write-Json $sealedPath $tamperedReview
    $badReview = Invoke-Script $reporter @(
        '-CertificationManifest', $manifestPath,
        '-ReviewEvidence', $sealedPath,
        '-OutputPath', $reportPath)
    Assert-True ($badReview.ExitCode -eq 1 -and
            $badReview.Output -match
                '^CERTIFICATION_REVIEW_EVIDENCE_REJECTED\s*$') `
        'A hand-edited sealed review must fail its closed evidence check.'
    [System.IO.File]::WriteAllText(
        $sealedPath, $sealedText,
        [System.Text.UTF8Encoding]::new($false))

    $guardText = Get-Content -LiteralPath $guardPath -Raw -Encoding UTF8
    $tamperedGuard = $guardText | ConvertFrom-Json
    $tamperedGuard.gates.canonical.falseAcceptance = 1
    Write-Json $guardPath $tamperedGuard
    $badGuard = Invoke-Script $reporter @(
        '-CertificationManifest', $manifestPath,
        '-ReviewEvidence', $sealedPath,
        '-OutputPath', $reportPath)
    Assert-True ($badGuard.ExitCode -eq 1 -and
            $badGuard.Output -match '^CERTIFICATION_') `
        'A tampered machine guard artifact must fail closed.'
    [System.IO.File]::WriteAllText(
        $guardPath, $guardText,
        [System.Text.UTF8Encoding]::new($false))

    $chainNodes = @($script:EvidenceChainNodeNames.GetEnumerator() |
        ForEach-Object {
            $nodePath = Join-Path $evidenceDirectory ([string]$_.Value)
            [ordered]@{
                Name = [string]$_.Key
                Path = $nodePath
                Restore = Get-Content -LiteralPath $nodePath -Raw -Encoding UTF8
            }
        })
    Assert-True ($chainNodes.Count -eq 8 -and
            @($chainNodes | Where-Object {
                $_.Name -ceq 'replayAggregate'
            }).Count -eq 1) `
        'One authoritative chain list must include replay-aggregate.json.'
    foreach ($node in $chainNodes) {
        Remove-Item -LiteralPath $node.Path -Force
        $missingNode = Invoke-Script $reporter @(
            '-CertificationManifest', $manifestPath,
            '-ReviewEvidence', $sealedPath,
            '-OutputPath', $reportPath)
        Assert-True ($missingNode.ExitCode -eq 1 -and
                $missingNode.Output -match `
                    '^(?:CERTIFICATION|EVIDENCE)_[A-Z_]+\s*$' -and
                $missingNode.Output -notmatch 'CANDIDATE_PASSES') `
            'Deleting any evidence-chain node must fail closed.'
        [System.IO.File]::WriteAllText(
            $node.Path, [string]$node.Restore,
            [System.Text.UTF8Encoding]::new($false))
    }
    foreach ($node in $chainNodes) {
        Set-TamperedChainNode $node.Name $node.Path
        $tamperedNode = Invoke-Script $reporter @(
            '-CertificationManifest', $manifestPath,
            '-ReviewEvidence', $sealedPath,
            '-OutputPath', $reportPath)
        Assert-True ($tamperedNode.ExitCode -eq 1 -and
                $tamperedNode.Output -match
                    '^(?:CERTIFICATION|EVIDENCE)_[A-Z_]+\s*$' -and
                $tamperedNode.Output -notmatch `
                    'QWEN_GENERAL_CERTIFICATION|CANDIDATE_PASSES') `
            "Tampering evidence-chain node $($node.Name) must fail closed."
        [System.IO.File]::WriteAllText(
            $node.Path, [string]$node.Restore,
            [System.Text.UTF8Encoding]::new($false))
    }

    $oldSamples = Join-Path $fixtureRoot 'handwritten-samples.json'
    $oldReviews = Join-Path $fixtureRoot 'handwritten-reviews.json'
    Write-Json $oldSamples ([ordered]@{ samples = @() })
    Write-Json $oldReviews ([ordered]@{ reviews = @() })
    $handwritten = Invoke-Script $reporter @(
        '-SamplesFile', $oldSamples,
        '-BlindReviewFile', $oldReviews,
        '-OutputPath', $reportPath)
    Assert-True ($handwritten.ExitCode -ne 0 -and
            $handwritten.Output -match
                '^CERTIFICATION_LEGACY_INPUT_REJECTED\s*$' -and
            $handwritten.Output -notmatch 'CANDIDATE_PASSES') `
        'The removed arbitrary all-green samples/reviews input must stay rejected.'

    $schema = Get-Content -LiteralPath $reviewSchemaPath `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $reviewItemSchema = $schema.properties.passes.items.properties.reviews.items
    Assert-True ($schema.additionalProperties -eq $false -and
            $schema.properties.passes.items.additionalProperties -eq $false -and
            $reviewItemSchema.additionalProperties -eq $false) `
        'The opaque review schema must remain a closed object graph.'

    Write-Output 'QWEN_GENERAL_CERTIFICATION_REPORT_TESTS_OK'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        Assert-True ([System.IO.Path]::GetFileName($resolved).StartsWith(
            'qwen-general-certification-test-')) `
            'Refusing to remove an unverified certification fixture root.'
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
