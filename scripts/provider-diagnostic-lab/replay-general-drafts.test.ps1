$ErrorActionPreference = 'Stop'

$runner = Join-Path $PSScriptRoot 'replay-general-drafts.ps1'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('general-dual-replay-test-' + [guid]::NewGuid().ToString('N'))
$rawRoot = Join-Path $fixtureRoot 'raw'
$outputPath = Join-Path $fixtureRoot 'aggregate.json'
$rootMarker = '.qwen-general-provider-lab-root.v1.json'

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function ConvertFrom-CodePoints([int[]]$Values) {
    return -join @($Values | ForEach-Object { [char]$_ })
}

function Write-Json([string]$Path, [object]$Value) {
    [System.IO.File]::WriteAllText(
        $Path,
        ($Value | ConvertTo-Json -Depth 30 -Compress),
        [System.Text.UTF8Encoding]::new($false))
}

function Write-Artifact(
    [string]$Name,
    [string]$CaseId,
    [string]$Depth,
    [object]$Arguments
) {
    $directory = Join-Path $rawRoot $Name
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $created = (Get-Date).ToUniversalTime().AddHours(-1)
    Write-Json (Join-Path $directory 'metadata.json') ([ordered]@{
        schemaVersion = 'qwen-general-lab-artifact.v2'
        artifactId = $Name
        caseId = $CaseId
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
        status = 'CAPTURED'
        httpClass = 'SUCCESS'
        latencyBucket = 'LT_100_MS'
        latencyMs = 50
        attemptCount = 1
    })
    $argumentJson = $Arguments | ConvertTo-Json -Depth 20 -Compress
    Write-Json (Join-Path $directory 'response.raw.json') ([ordered]@{
        id = 'synthetic-envelope'
        model = 'qwen3.7-flash'
        choices = @(@{
            index = 0
            message = @{
                role = 'assistant'
                content = $null
                refusal = $null
                tool_calls = @(@{
                    id = 'synthetic-call'
                    type = 'function'
                    function = @{
                        name = 'emit_general_provider_draft_v4'
                        arguments = $argumentJson
                    }
                })
            }
            finish_reason = 'stop'
        })
    })
}

function Write-TransportFailure(
    [string]$Name,
    [string]$CaseId,
    [string]$Depth,
    [string]$Root = $rawRoot
) {
    $directory = Join-Path $Root $Name
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $created = (Get-Date).ToUniversalTime().AddHours(-1)
    Write-Json (Join-Path $directory 'metadata.json') ([ordered]@{
        schemaVersion = 'qwen-general-lab-artifact.v2'
        artifactId = $Name
        caseId = $CaseId
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
        status = 'TRANSPORT_FAILED'
        httpClass = 'TRANSPORT_UNAVAILABLE'
        latencyBucket = 'FROM_500_TO_1999_MS'
        latencyMs = 1500
        attemptCount = 2
    })
}

function Invoke-Runner(
    [string[]]$Arguments,
    [bool]$AllowTestLoopback = $true
) {
    $previous = $ErrorActionPreference
    $previousTestMode = $env:QWEN_GENERAL_REPLAY_TEST_MODE
    $ErrorActionPreference = 'Continue'
    try {
        if ($AllowTestLoopback) {
            $env:QWEN_GENERAL_REPLAY_TEST_MODE = `
                'AUTHORIZED_LOOPBACK_REPLAY_TEST_ONLY'
        }
        else {
            Remove-Item Env:QWEN_GENERAL_REPLAY_TEST_MODE `
                -ErrorAction SilentlyContinue
        }
        $output = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $runner @Arguments 2>&1 | Out-String)
        return @{ ExitCode = $LASTEXITCODE; Output = $output }
    }
    finally {
        $ErrorActionPreference = $previous
        $env:QWEN_GENERAL_REPLAY_TEST_MODE = $previousTestMode
    }
}

try {
    Assert-True (Test-Path -LiteralPath $runner -PathType Leaf) `
        'The dual replay runner must exist.'
    New-Item -ItemType Directory -Path $rawRoot -Force | Out-Null
    Write-Json (Join-Path $rawRoot $rootMarker) ([ordered]@{
        schemaVersion = 'qwen-general-provider-lab-root.v1'
        rootId = [guid]::NewGuid().ToString('N')
        createdAtUtc = (Get-Date).ToUniversalTime().ToString('o')
    })
    $wideTempRoot = [System.IO.Path]::GetFullPath(
        [System.IO.Path]::GetTempPath()).TrimEnd('\')
    $wideTempAcl = (Get-Acl -LiteralPath $wideTempRoot).Sddl
    $wideReplay = Invoke-Runner @(
        '-RawArtifactRoot', $wideTempRoot,
        '-OutputPath', $outputPath) $false
    Assert-True ($wideReplay.ExitCode -eq 1 -and
            $wideReplay.Output -match '^DUAL_REPLAY_RAW_ROOT_REJECTED\s*$' -and
            (Get-Acl -LiteralPath $wideTempRoot).Sddl -ceq $wideTempAcl) `
        'Formal replay must reject a broad temp root before ACL mutation.'
    $rawFieldName = 'RAW_PROVIDER_FIELD_NAME_MUST_NOT_ENTER_AGGREGATE'
    $rawFieldValue = 'RAW_PROVIDER_BODY_MUST_NOT_ENTER_AGGREGATE'
    $zhText = ConvertFrom-CodePoints @(0x4E2D, 0x6587, 0x8BF4, 0x660E)
    $v4Draft = [ordered]@{
        definition = $zhText
        mechanism = @($zhText, $zhText)
    }
    $v4Draft[$rawFieldName] = $rawFieldValue
    $v4Name = 'capture-java-spring-001-standard-20260828T000000Z-' +
        [guid]::NewGuid().ToString('N')
    $v3Name = 'capture-java-spring-002-standard-20260828T000000Z-' +
        [guid]::NewGuid().ToString('N')
    $incompleteName = 'capture-java-spring-003-concise-20260828T000000Z-' +
        [guid]::NewGuid().ToString('N')
    $transportName = 'capture-java-spring-004-concise-20260828T000000Z-' +
        [guid]::NewGuid().ToString('N')
    Write-Artifact $v4Name 'java-spring-001' 'STANDARD' $v4Draft
    Write-Artifact $v3Name 'java-spring-002' 'STANDARD' ([ordered]@{
        kind = 'EXPLANATION'
        depth = 'STANDARD'
        definitionSentences = @($zhText, $zhText)
        mechanismSentences = @($zhText, $zhText)
        caveats = @()
    })
    Write-Artifact $incompleteName 'java-spring-003' 'CONCISE' `
        ([ordered]@{ caveats = $null })
    Write-TransportFailure $transportName 'java-spring-004' 'CONCISE'

    $formalLoopback = Invoke-Runner @(
        '-RawArtifactRoot', $rawRoot,
        '-OutputPath', $outputPath
    ) $false
    Assert-True ($formalLoopback.ExitCode -eq 1 -and
            $formalLoopback.Output -match `
                '^DUAL_REPLAY_CAPTURE_SOURCE_REJECTED\s*$') `
        'Formal replay must reject TEST_LOOPBACK artifacts.'

    $result = Invoke-Runner @(
        '-RawArtifactRoot', $rawRoot,
        '-OutputPath', $outputPath
    )
    Assert-True ($result.ExitCode -eq 0) `
        "Valid raw artifacts must replay successfully: $($result.Output)"
    Assert-True ($result.Output -match `
        '^GENERAL_DUAL_REPLAY_COMPLETE samples=4 v3Accepted=1 v4Accepted=1\s*$') `
        'Replay stdout must contain only closed aggregate counts.'
    Assert-True ($result.Output -notmatch [regex]::Escape($rawFieldName) -and
            $result.Output -notmatch [regex]::Escape($rawFieldValue)) `
        'Replay stdout leaked raw Provider content.'

    $aggregateText = Get-Content -LiteralPath $outputPath -Raw -Encoding UTF8
    Assert-True ($aggregateText -notmatch [regex]::Escape($rawFieldName) -and
            $aggregateText -notmatch [regex]::Escape($rawFieldValue)) `
        'Permanent replay aggregate leaked unknown field name or value.'
    $aggregate = $aggregateText | ConvertFrom-Json
    Assert-True ($aggregate.schemaVersion -ceq `
            'qwen-general-dual-replay.v1' -and
            $aggregate.fixtureMode -ceq 'NONE' -and
            $null -eq $aggregate.fixtureCaseKey -and
            @($aggregate.samples).Count -eq 4) `
        'Dual replay aggregate schema or sample count mismatch.'
    $normalized = @($aggregate.samples | Where-Object {
        $_.caseId -eq 'java-spring-001'
    })[0]
    Assert-True ($normalized.v3.outcome -eq 'INCOMPLETE' -and
            $normalized.v4.outcome -eq 'DEGRADED' -and
            $normalized.v4.normalizationRuleCounts.UNKNOWN_FIELD_COUNT -eq 1 -and
            $normalized.v4.normalizationRuleCounts.MISSING_CAVEATS_AS_EMPTY -eq 1) `
        'v3 rejection and v4 deterministic recovery were not separated.'
    $legacy = @($aggregate.samples | Where-Object {
        $_.caseId -eq 'java-spring-002'
    })[0]
    Assert-True ($legacy.v3.outcome -eq 'EXACT' -and
            $legacy.v4.outcome -eq 'INCOMPLETE') `
        'Historical v3 acceptance must not become a production fallback.'
    $incomplete = @($aggregate.samples | Where-Object {
        $_.caseId -eq 'java-spring-003'
    })[0]
    Assert-True ($incomplete.v3.outcome -eq 'INCOMPLETE' -and
            $incomplete.v4.outcome -eq 'INCOMPLETE') `
        'Missing core must remain rejected by both chains.'
    $transport = @($aggregate.samples | Where-Object {
        $_.caseId -eq 'java-spring-004'
    })[0]
    Assert-True ($transport.v3.outcome -eq 'NOT_APPLICABLE' -and
            $transport.v4.outcome -eq 'NOT_APPLICABLE' -and
            $transport.v4.layer -eq 'TRANSPORT') `
        'Transport failure must remain in the 300 denominator without replay.'

    $priorFixtureKey = $env:QWEN_GENERAL_REPLAY_SEMANTIC_FIXTURE_CASE_KEY
    $priorFixtureAuthorization =
        $env:QWEN_GENERAL_REPLAY_SEMANTIC_FIXTURE_AUTHORIZATION
    try {
        $env:QWEN_GENERAL_REPLAY_SEMANTIC_FIXTURE_CASE_KEY =
            'java-spring-001|STANDARD'
        $env:QWEN_GENERAL_REPLAY_SEMANTIC_FIXTURE_AUTHORIZATION =
            'AUTHORIZED_POST_CANONICAL_VALIDATOR_FIXTURE_ONLY'
        $semanticFixtureResult = Invoke-Runner @(
            '-RawArtifactRoot', $rawRoot,
            '-OutputPath', $outputPath)
    }
    finally {
        $env:QWEN_GENERAL_REPLAY_SEMANTIC_FIXTURE_CASE_KEY = $priorFixtureKey
        $env:QWEN_GENERAL_REPLAY_SEMANTIC_FIXTURE_AUTHORIZATION =
            $priorFixtureAuthorization
    }
    Assert-True ($semanticFixtureResult.ExitCode -eq 0) `
        "Authorized semantic fixture replay failed: $($semanticFixtureResult.Output)"
    $semanticAggregate = Get-Content -LiteralPath $outputPath `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $semanticFixtureSample = @($semanticAggregate.samples | Where-Object {
        $_.caseId -ceq 'java-spring-001' -and $_.depth -ceq 'STANDARD'
    })[0]
    Assert-True ($semanticAggregate.fixtureMode -ceq
            'TEST_ONLY_POST_CANONICAL_SEMANTIC_REJECT' -and
            $semanticAggregate.fixtureCaseKey -ceq
                'java-spring-001|STANDARD' -and
            $semanticFixtureSample.v4.outcome -ceq 'INCOMPLETE' -and
            $semanticFixtureSample.v4.layer -ceq 'SEMANTIC') `
        ('The authorized test-only fixture must pass the real compiler and ' +
            'be rejected by the real semantic validator.')

    $metadataPath = Join-Path (Join-Path $rawRoot $v4Name) 'metadata.json'
    $validMetadata = Get-Content -LiteralPath $metadataPath -Raw -Encoding UTF8
    $overlongMetadata = $validMetadata | ConvertFrom-Json
    $overlongCreated = [datetimeoffset]::Parse(
        [string]$overlongMetadata.createdAtUtc)
    $overlongExpires = $overlongCreated.AddHours(24).AddMilliseconds(1)
    $overlongMetadata.expiresAtUtc = $overlongExpires.ToString('o')
    Write-Json $metadataPath $overlongMetadata
    $overlongTtl = Invoke-Runner @(
        '-RawArtifactRoot', $rawRoot, '-OutputPath', $outputPath)
    Assert-True ($overlongTtl.ExitCode -eq 1 -and
            $overlongTtl.Output -match
                '^DUAL_REPLAY_ARTIFACT_METADATA_REJECTED\s*$') `
        'Replay must reject a TTL of 24 hours plus one millisecond.'
    [System.IO.File]::WriteAllText(
        $metadataPath, $validMetadata,
        [System.Text.UTF8Encoding]::new($false))

    $tamperedMetadata = $validMetadata | ConvertFrom-Json
    $tamperedMetadata.model = 'wrong-model'
    Write-Json $metadataPath $tamperedMetadata
    $wrongIdentity = Invoke-Runner @(
        '-RawArtifactRoot', $rawRoot, '-OutputPath', $outputPath)
    Assert-True ($wrongIdentity.ExitCode -eq 1 -and
            $wrongIdentity.Output -match `
                '^DUAL_REPLAY_ARTIFACT_METADATA_REJECTED\s*$') `
        'Replay must reject a capture whose frozen model metadata changed.'
    [System.IO.File]::WriteAllText(
        $metadataPath, $validMetadata,
        [System.Text.UTF8Encoding]::new($false))

    $responsePath = Join-Path (Join-Path $rawRoot $v4Name) 'response.raw.json'
    $validEnvelope = Get-Content -LiteralPath $responsePath -Raw -Encoding UTF8
    $tamperedEnvelope = $validEnvelope | ConvertFrom-Json
    $tamperedEnvelope.choices[0].finish_reason = 'tool_calls'
    Write-Json $responsePath $tamperedEnvelope
    $badEnvelope = Invoke-Runner @(
        '-RawArtifactRoot', $rawRoot, '-OutputPath', $outputPath)
    Assert-True ($badEnvelope.ExitCode -eq 1 -and
            $badEnvelope.Output -match '^DUAL_REPLAY_EXECUTION_FAILED\s*$') `
        'Replay must reject a non-tool finish_reason.'
    [System.IO.File]::WriteAllText(
        $responsePath, $validEnvelope,
        [System.Text.UTF8Encoding]::new($false))

    $unmarkedRoot = Join-Path $fixtureRoot 'unmarked-replay-root'
    New-Item -ItemType Directory -Path $unmarkedRoot -Force | Out-Null
    $unmarkedAcl = (Get-Acl -LiteralPath $unmarkedRoot).Sddl
    $unmarkedReplay = Invoke-Runner @(
        '-RawArtifactRoot', $unmarkedRoot,
        '-OutputPath', $outputPath) $false
    Assert-True ($unmarkedReplay.ExitCode -eq 1 -and
            $unmarkedReplay.Output -match '^DUAL_REPLAY_RAW_ROOT_REJECTED\s*$' -and
            (Get-Acl -LiteralPath $unmarkedRoot).Sddl -ceq $unmarkedAcl) `
        'Replay must reject a missing marker before ACL mutation.'

    $expiredRoot = Join-Path $fixtureRoot 'expired-replay-root'
    New-Item -ItemType Directory -Path $expiredRoot -Force | Out-Null
    Write-Json (Join-Path $expiredRoot $rootMarker) ([ordered]@{
        schemaVersion = 'qwen-general-provider-lab-root.v1'
        rootId = [guid]::NewGuid().ToString('N')
        createdAtUtc = (Get-Date).ToUniversalTime().ToString('o')
    })
    $expiredName = 'capture-java-spring-005-concise-20260828T000000Z-' +
        [guid]::NewGuid().ToString('N')
    Write-TransportFailure $expiredName 'java-spring-005' 'CONCISE' $expiredRoot
    $expiredMetadataPath = Join-Path (Join-Path $expiredRoot $expiredName) `
        'metadata.json'
    $expiredMetadata = Get-Content -LiteralPath $expiredMetadataPath `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $expiredMetadata.expiresAtUtc = `
        [datetimeoffset]::UtcNow.AddSeconds(-1).ToString('o')
    $expiredMetadata.createdAtUtc = `
        [datetimeoffset]::UtcNow.AddHours(-23).ToString('o')
    Write-Json $expiredMetadataPath $expiredMetadata
    $expiredReplay = Invoke-Runner @(
        '-RawArtifactRoot', $expiredRoot,
        '-OutputPath', $outputPath)
    Assert-True ($expiredReplay.ExitCode -eq 1 -and
            $expiredReplay.Output -match `
                '^DUAL_REPLAY_ARTIFACT_METADATA_REJECTED\s*$') `
        'Replay must reject a capture expired by one second.'

    $outsideLeafTarget = Join-Path $fixtureRoot 'outside-leaf-target'
    New-Item -ItemType Directory -Path $outsideLeafTarget -Force | Out-Null
    Remove-Item -LiteralPath $metadataPath -Force
    New-Item -ItemType Junction -Path $metadataPath `
        -Target $outsideLeafTarget | Out-Null
    $metadataReparse = Invoke-Runner @(
        '-RawArtifactRoot', $rawRoot, '-OutputPath', $outputPath)
    Assert-True ($metadataReparse.ExitCode -eq 1 -and
            $metadataReparse.Output -match
                '^DUAL_REPLAY_ARTIFACT_METADATA_REJECTED\s*$') `
        'Replay must reject metadata.json when its path is a reparse point.'
    [System.IO.Directory]::Delete($metadataPath)
    [System.IO.File]::WriteAllText(
        $metadataPath, $validMetadata,
        [System.Text.UTF8Encoding]::new($false))

    Remove-Item -LiteralPath $responsePath -Force
    New-Item -ItemType Junction -Path $responsePath `
        -Target $outsideLeafTarget | Out-Null
    $responseReparse = Invoke-Runner @(
        '-RawArtifactRoot', $rawRoot, '-OutputPath', $outputPath)
    Assert-True ($responseReparse.ExitCode -eq 1 -and
            $responseReparse.Output -match
                '^DUAL_REPLAY_ARTIFACT_SET_REJECTED\s*$') `
        'Replay must reject response.raw.json when its path is a reparse point.'
    [System.IO.Directory]::Delete($responsePath)
    [System.IO.File]::WriteAllText(
        $responsePath, $validEnvelope,
        [System.Text.UTF8Encoding]::new($false))

    $repoPath = Invoke-Runner @(
        '-RawArtifactRoot', (Join-Path $repoRoot 'scripts'),
        '-OutputPath', $outputPath
    )
    Assert-True ($repoPath.ExitCode -eq 1 -and
            $repoPath.Output -match '^DUAL_REPLAY_RAW_ROOT_REJECTED\s*$') `
        'Dual replay must reject raw artifacts inside the repository.'

    $freeTextMarker = 'REPLAY_FREE_TEXT_MUST_NOT_BE_ACCEPTED'
    $freeText = Invoke-Runner @(
        '-RawArtifactRoot', $rawRoot,
        '-OutputPath', $outputPath,
        '-Prompt', $freeTextMarker
    )
    Assert-True ($freeText.ExitCode -ne 0 -and
            $freeText.Output -notmatch [regex]::Escape($freeTextMarker)) `
        'Dual replay must not expose or echo an arbitrary Prompt parameter.'

    Write-Output 'GENERAL_DUAL_REPLAY_TESTS_OK'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        Assert-True ([System.IO.Path]::GetFileName($resolved).StartsWith(
            'general-dual-replay-test-')) `
            'Refusing to remove an unverified replay fixture root.'
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
