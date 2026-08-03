$ErrorActionPreference = 'Stop'

$cli = Join-Path $PSScriptRoot 'portfolio-governance.ps1'
$repositoryRoot = Split-Path $PSScriptRoot -Parent
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('portfolio-governance-' + [guid]::NewGuid())
$workspace = Join-Path $fixtureRoot 'workspace'
$candidate = Join-Path $workspace 'candidates\candidate-1'
$decisionLedger = $null
$governanceInvocationCount = 0
$currentBundleVersion = [string](
    Get-Content -LiteralPath (Join-Path $repositoryRoot `
        'backend\src\main\resources\public-data\bundle\portfolio.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
).contentVersion

function Invoke-Governance([string[]]$Arguments) {
    $script:governanceInvocationCount++
    $ledgerBytesToRestore = $null
    $commandIndex = [Array]::IndexOf($Arguments, '-Command')
    $usesDefaultLedger = $false
    if ($commandIndex -ge 0 -and $commandIndex + 1 -lt $Arguments.Count -and
            $Arguments[$commandIndex + 1] -in @(
                'validate', 'benchmark', 'build-review-pack', 'approve', 'publish', 'verify'
            ) -and
            [Array]::IndexOf($Arguments, '-DecisionLedger') -lt 0 -and
            -not [string]::IsNullOrWhiteSpace($decisionLedger)) {
        $Arguments = @($Arguments) + @('-DecisionLedger', $decisionLedger)
        $usesDefaultLedger = $true
    }
    $candidateIndex = [Array]::IndexOf($Arguments, '-Candidate')
    if ($usesDefaultLedger -and $candidateIndex -ge 0 -and
            $candidateIndex + 1 -lt $Arguments.Count) {
        $candidatePortfolioPath = Join-Path $Arguments[$candidateIndex + 1] 'portfolio.json'
        if (Test-Path -LiteralPath $candidatePortfolioPath -PathType Leaf) {
            $ledgerBytesToRestore = [IO.File]::ReadAllBytes($decisionLedger)
            $candidateContentVersion = [string](
                Get-Content -LiteralPath $candidatePortfolioPath -Raw -Encoding UTF8 |
                    ConvertFrom-Json
            ).contentVersion
            $ledgerForCandidate = Get-Content -LiteralPath $decisionLedger `
                -Raw -Encoding UTF8 | ConvertFrom-Json
            foreach ($dynamicAsset in @($ledgerForCandidate.assets |
                    Where-Object { $_.decisionReason -eq 'Synthetic dynamic Case mapping' })) {
                $dynamicAsset.achievementStatus = 'INCOMPLETE'
                $dynamicAsset.contributionType = 'ASSISTED'
                $dynamicAsset.finalRoute = 'HOLD'
                $dynamicAsset.decisionReason = 'Synthetic reviewed hold'
                $dynamicAsset.projectSlugs = @()
                $dynamicAsset.caseSlugs = @()
                $dynamicAsset.evidenceIds = @()
                $dynamicAsset.routeDecision = 'REVIEWED_HOLD'
                $dynamicAsset.targetContentVersion = $null
                $dynamicAsset.targetWave = $null
            }
            foreach ($asset in @($ledgerForCandidate.assets |
                    Where-Object { $_.routeDecision -eq 'PUBLISH_CANDIDATE' })) {
                $asset.targetContentVersion = $candidateContentVersion
            }
            foreach ($caseAssetMapping in @(
                @{ AssetId = 'T-01'; CaseSlug = 'multilingual-image-preservation' },
                @{ AssetId = 'T-02'; CaseSlug = 'test-role-reset' },
                @{ AssetId = 'K-01'; CaseSlug = 'codegraph-evaluation' }
            )) {
                $caseAsset = @($ledgerForCandidate.assets |
                    Where-Object { $_.assetId -eq $caseAssetMapping.AssetId })[0]
                if ([string]$(
                        Get-Content -LiteralPath $candidatePortfolioPath -Raw -Encoding UTF8 |
                            ConvertFrom-Json
                    ).schemaVersion -eq '2.0') {
                    $caseAsset.finalRoute = 'EVIDENCE_ONLY'
                    $caseAsset.caseSlugs = @()
                }
                else {
                    $caseAsset.finalRoute = 'CASE'
                    $caseAsset.caseSlugs = @($caseAssetMapping.CaseSlug)
                }
            }
            $candidatePortfolio = Get-Content -LiteralPath $candidatePortfolioPath `
                -Raw -Encoding UTF8 | ConvertFrom-Json
            if ([string]$candidatePortfolio.schemaVersion -eq '2.0') {
                foreach ($caseAsset in @($ledgerForCandidate.assets | Where-Object {
                        $_.routeDecision -eq 'PUBLISH_CANDIDATE' -and
                        @($_.caseSlugs).Count -gt 0
                    })) {
                    $caseAsset.finalRoute = 'EVIDENCE_ONLY'
                    $caseAsset.caseSlugs = @()
                }
            }
            $unmappedCases = @()
            if ([string]$candidatePortfolio.schemaVersion -eq '3.0') {
                $ledgerMappedCaseSlugs = @($ledgerForCandidate.assets |
                    ForEach-Object { @($_.caseSlugs) })
                $unmappedCases = @($candidatePortfolio.cases | Where-Object {
                        $null -ne $_ -and
                        -not [string]::IsNullOrWhiteSpace([string]$_.slug) -and
                        $_.PSObject.Properties.Name -contains 'achievementStatus' -and
                        $_.PSObject.Properties.Name -contains 'contributionType' -and
                        $ledgerMappedCaseSlugs -notcontains [string]$_.slug
                    })
            }
            foreach ($unmappedCase in $unmappedCases) {
                if ($unmappedCase.PSObject.Properties.Name -notcontains 'achievementStatus' -or
                        $unmappedCase.PSObject.Properties.Name -notcontains 'contributionType') {
                    continue
                }
                $caseAchievementStatus = [string](
                    $unmappedCase.PSObject.Properties['achievementStatus'].Value
                )
                $caseContributionType = [string](
                    $unmappedCase.PSObject.Properties['contributionType'].Value
                )
                $availableAsset = @($ledgerForCandidate.assets |
                    Where-Object { $_.routeDecision -eq 'REVIEWED_HOLD' })[0]
                if ($null -eq $availableAsset) {
                    throw "Synthetic dynamic mapping exhausted inventory for " +
                        "$candidateContentVersion/$($unmappedCase.slug); " +
                        "unmapped=$($unmappedCases.Count), mapped=$($ledgerMappedCaseSlugs.Count)"
                }
                $availableAsset.achievementStatus = if (
                    $caseAchievementStatus -eq 'PROTOTYPE'
                ) { 'VALIDATED_PROTOTYPE' } else { $caseAchievementStatus }
                $availableAsset.contributionType = $caseContributionType
                $availableAsset.evidenceStatus = 'VERIFIED'
                $availableAsset.finalRoute = 'CASE'
                $availableAsset.decisionReason = 'Synthetic dynamic Case mapping'
                $availableAsset.caseSlugs = @([string]$unmappedCase.slug)
                $availableAsset.evidenceIds = @([string]$candidatePortfolio.evidence[0].id)
                $availableAsset.routeDecision = 'PUBLISH_CANDIDATE'
                $availableAsset.targetContentVersion = $candidateContentVersion
                $availableAsset.targetWave = 1
            }
            Save-Json $ledgerForCandidate $decisionLedger
        }
    }
    try {
        $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $cli @Arguments 2>&1
        $exitCode = $LASTEXITCODE
        return @{ ExitCode = $exitCode; Output = ($output -join [Environment]::NewLine) }
    }
    finally {
        if ($null -ne $ledgerBytesToRestore) {
            [IO.File]::WriteAllBytes($decisionLedger, $ledgerBytesToRestore)
        }
    }
}

function New-Candidate([string]$Name) {
    $path = Join-Path $workspace ('candidates\' + $Name)
    New-Item -ItemType Directory -Force -Path $path | Out-Null
    Copy-Item -LiteralPath (Join-Path $repositoryRoot 'backend\src\main\resources\public-data\bundle\portfolio.json') -Destination $path
    Copy-Item -LiteralPath (Join-Path $repositoryRoot 'backend\src\main\resources\public-data\bundle\presentation.json') -Destination $path
    return $path
}

function Save-Json([object]$Value, [string]$Path) {
    $Value | ConvertTo-Json -Depth 30 |
        Set-Content -LiteralPath $Path -Encoding UTF8
}

function New-DecisionLedger([string]$Path, [string]$CandidatePath) {
    $portfolio = Get-Content -LiteralPath (Join-Path $CandidatePath 'portfolio.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $assets = @()
    foreach ($prefixAndCount in @(
        @('L', 7, 'MAINLINE'),
        @('T', 19, 'TASK'),
        @('A', 25, 'INCIDENT'),
        @('K', 17, 'KNOWLEDGE_ASSET')
    )) {
        for ($index = 1; $index -le [int]$prefixAndCount[1]; $index++) {
            $assets += [pscustomobject][ordered]@{
                assetId = ('{0}-{1:d2}' -f $prefixAndCount[0], $index)
                contentType = [string]$prefixAndCount[2]
                achievementStatus = 'INCOMPLETE'
                contributionType = 'ASSISTED'
                publicPriority = 'P2'
                evidenceStatus = 'INSUFFICIENT'
                originalReviewState = 'HOLD'
                finalRoute = 'HOLD'
                decisionReason = 'Synthetic reviewed hold'
                projectSlugs = @()
                caseSlugs = @()
                evidenceIds = @()
                privacyReview = 'REVIEWED'
                routeDecision = 'REVIEWED_HOLD'
                targetContentVersion = $null
                targetWave = $null
            }
        }
    }
    $published = @{
        'L-01' = @{
            contentType = 'MAINLINE'; achievementStatus = 'DELIVERED'
            projectSlugs = @('sql-audit')
            caseSlugs = @()
            evidenceIds = @(
                'sql-audit-delivery-set',
                'sql-audit-july-iteration-set',
                'evidence-sql-audit-async-progress-validation',
                'evidence-sql-audit-result-lifecycle-docs'
            )
        }
        'T-01' = @{
            contentType = 'TASK'; achievementStatus = 'DELIVERED'
            projectSlugs = @()
            caseSlugs = @('multilingual-image-preservation')
            evidenceIds = @('evidence-case-multilingual-implementation-and-regression')
        }
        'T-02' = @{
            contentType = 'TASK'; achievementStatus = 'DELIVERED'
            projectSlugs = @()
            caseSlugs = @('test-role-reset')
            evidenceIds = @('evidence-case-role-reset-guide-and-acceptance')
        }
        'K-01' = @{
            contentType = 'KNOWLEDGE_ASSET'; achievementStatus = 'VALIDATED_PROTOTYPE'
            projectSlugs = @()
            caseSlugs = @('codegraph-evaluation')
            evidenceIds = @('evidence-case-codegraph-report-collection')
        }
    }
    foreach ($asset in $assets) {
        if ($published.ContainsKey($asset.assetId)) {
            $source = $published[$asset.assetId]
            $asset.contentType = $source.contentType
            $asset.achievementStatus = $source.achievementStatus
            $asset.contributionType = 'PRIMARY'
            $asset.publicPriority = 'P0'
            $asset.evidenceStatus = 'VERIFIED'
            $asset.originalReviewState = 'PUBLIC_REVIEW_REQUIRED'
            $asset.finalRoute = if ($source.caseSlugs.Count -gt 0) { 'CASE' } else { 'PROJECT' }
            $asset.decisionReason = 'Synthetic published mapping'
            $asset.projectSlugs = $source.projectSlugs
            $asset.caseSlugs = $source.caseSlugs
            $asset.evidenceIds = $source.evidenceIds
            $asset.routeDecision = 'PUBLISH_CANDIDATE'
            $asset.targetContentVersion = [string]$portfolio.contentVersion
            $asset.targetWave = 1
        }
    }
    $reservedAssetIds = @('L-02', 'L-03', 'T-08', 'T-17', 'K-03', 'K-05', 'K-17')
    $mappedProjectSlugs = @($assets | ForEach-Object { @($_.projectSlugs) })
    $mappedCaseSlugs = @($assets | ForEach-Object { @($_.caseSlugs) })
    $availableAssets = @($assets | Where-Object {
        $_.routeDecision -eq 'REVIEWED_HOLD' -and
        $reservedAssetIds -notcontains $_.assetId
    })
    $availableAssetIndex = 0
    $publicSubjects = @($portfolio.projects | Where-Object {
            $mappedProjectSlugs -notcontains [string]$_.slug
        } | ForEach-Object {
            [pscustomobject]@{
                Kind = 'PROJECT'
                Slug = [string]$_.slug
                AchievementStatus = switch ([string]$_.status) {
                    'PROTOTYPE' { 'VALIDATED_PROTOTYPE' }
                    'IN_PROGRESS' { 'INVESTIGATED' }
                    default { [string]$_ }
                }
                ContributionType = [string]$_.contributionType
                EvidenceIds = @($_.evidenceIds)
            }
        })
    $publicSubjects += @($portfolio.cases | Where-Object {
            $mappedCaseSlugs -notcontains [string]$_.slug
        } | ForEach-Object {
            [pscustomobject]@{
                Kind = 'CASE'
                Slug = [string]$_.slug
                AchievementStatus = switch ([string]$_.achievementStatus) {
                    'PROTOTYPE' { 'VALIDATED_PROTOTYPE' }
                    'LEARNING' { 'LEARNING_ONLY' }
                    default { [string]$_ }
                }
                ContributionType = [string]$_.contributionType
                EvidenceIds = @($_.evidenceIds)
            }
        })
    foreach ($publicSubject in $publicSubjects) {
        if ($availableAssetIndex -ge $availableAssets.Count) {
            throw 'Synthetic decision ledger does not have enough reserved inventory rows.'
        }
        $asset = $availableAssets[$availableAssetIndex++]
        $asset.achievementStatus = $publicSubject.AchievementStatus
        $asset.contributionType = $publicSubject.ContributionType
        $asset.publicPriority = 'P1'
        $asset.evidenceStatus = 'VERIFIED'
        $asset.originalReviewState = 'PUBLIC_REVIEW_REQUIRED'
        $asset.finalRoute = $publicSubject.Kind
        $asset.decisionReason = 'Synthetic current-bundle mapping'
        if ($publicSubject.Kind -eq 'PROJECT') {
            $asset.projectSlugs = @($publicSubject.Slug)
            $asset.caseSlugs = @()
        }
        else {
            $asset.projectSlugs = @()
            $asset.caseSlugs = @($publicSubject.Slug)
        }
        $asset.evidenceIds = @($publicSubject.EvidenceIds)
        $asset.routeDecision = 'PUBLISH_CANDIDATE'
        $asset.targetContentVersion = [string]$portfolio.contentVersion
        $asset.targetWave = 1
        if ($asset.achievementStatus -notin @(
                'DELIVERED', 'IMPLEMENTED_TESTED', 'VALIDATED_PROTOTYPE',
                'INVESTIGATED', 'DOCUMENTED_OUTPUT', 'LEARNING_ONLY'
            ) -or $asset.contributionType -notin @(
                'PRIMARY', 'COLLABORATIVE', 'ASSISTED', 'UNRESOLVED'
            )) {
            throw "Synthetic mapping produced invalid values for $($asset.assetId): " +
                "$($asset.achievementStatus)/$($asset.contributionType)"
        }
    }
    $mappedEvidenceIds = @($assets | ForEach-Object { @($_.evidenceIds) })
    $unmappedEvidenceIds = @($portfolio.evidence | Where-Object {
        $mappedEvidenceIds -notcontains [string]$_.id
    } | ForEach-Object { [string]$_.id })
    if ($unmappedEvidenceIds.Count -gt 0) {
        $evidenceOnlyAsset = @($assets |
            Where-Object { $_.assetId -eq 'K-17' })[0]
        $evidenceOnlyAsset.evidenceStatus = 'VERIFIED'
        $evidenceOnlyAsset.originalReviewState = 'PUBLIC_REVIEW_REQUIRED'
        $evidenceOnlyAsset.finalRoute = 'EVIDENCE_ONLY'
        $evidenceOnlyAsset.decisionReason = 'Synthetic standalone Evidence mapping'
        $evidenceOnlyAsset.evidenceIds = $unmappedEvidenceIds
        $evidenceOnlyAsset.routeDecision = 'PUBLISH_CANDIDATE'
        $evidenceOnlyAsset.targetContentVersion = [string]$portfolio.contentVersion
        $evidenceOnlyAsset.targetWave = 1
    }
    $unmappedGeneratedCaseSlugs = @($portfolio.cases | Where-Object {
        $generatedSlug = [string]$_.slug
        @($assets | Where-Object {
            @($_.caseSlugs) -contains $generatedSlug
        }).Count -eq 0
    } | ForEach-Object { [string]$_.slug })
    if ($unmappedGeneratedCaseSlugs.Count -gt 0) {
        throw "Synthetic ledger generation missed Case mappings: " +
            ($unmappedGeneratedCaseSlugs -join ',')
    }
    Save-Json ([pscustomobject][ordered]@{
        schemaVersion = '1.0'
        assets = $assets
    }) $Path
}

function Copy-Ledger([string]$Name) {
    $path = Join-Path $workspace ('decisions\' + $Name + '.json')
    Copy-Item -LiteralPath $decisionLedger -Destination $path
    return $path
}

function Invoke-LedgerValidation([string]$LedgerPath) {
    return Invoke-Governance @(
        '-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $candidate, '-DecisionLedger', $LedgerPath
    )
}

function ConvertTo-SchemaThree([string]$CandidatePath) {
    $schemaThreeContentVersion = '2026-07-27.1'
    $portfolioPath = Join-Path $CandidatePath 'portfolio.json'
    $presentationPath = Join-Path $CandidatePath 'presentation.json'
    $data = Get-Content -LiteralPath $portfolioPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $data.schemaVersion = '3.0'
    $data.contentVersion = $schemaThreeContentVersion
    if (-not ($data.PSObject.Properties.Name -contains 'cases')) {
        $data | Add-Member -NotePropertyName cases -NotePropertyValue @()
    }
    $data.questionPresets | ForEach-Object {
        if (-not ($_.PSObject.Properties.Name -contains 'caseIds')) {
            $_ | Add-Member -NotePropertyName caseIds -NotePropertyValue @()
        }
    }
    $data.timelineEvents | ForEach-Object {
        if (-not ($_.PSObject.Properties.Name -contains 'caseIds')) {
            $_ | Add-Member -NotePropertyName caseIds -NotePropertyValue @()
        }
    }
    Save-Json $data $portfolioPath
    $presentation = Get-Content -LiteralPath $presentationPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $presentation.schemaVersion = '3.0'
    $presentation.contentVersion = $schemaThreeContentVersion
    Save-Json $presentation $presentationPath
    return $data
}

function ConvertTo-SchemaTwo([string]$CandidatePath) {
    $portfolioPath = Join-Path $CandidatePath 'portfolio.json'
    $presentationPath = Join-Path $CandidatePath 'presentation.json'
    $data = Get-Content -LiteralPath $portfolioPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $data.schemaVersion = '2.0'
    $data.contentVersion = '2026-07-21.1'
    $data.PSObject.Properties.Remove('cases')
    $data.claims = @($data.claims | Where-Object { $_.subjectType -ne 'CASE' })
    $legacyClaimIds = @($data.claims | ForEach-Object { [string]$_.id })
    $data.claimEvidenceLinks = @($data.claimEvidenceLinks |
        Where-Object { $legacyClaimIds -contains [string]$_.claimId })
    $data.questionPresets = @($data.questionPresets |
        Where-Object { @($_.projectIds).Count -gt 0 })
    $data.timelineEvents = @($data.timelineEvents |
        Where-Object { @($_.projectIds).Count -gt 0 })
    $data.questionPresets | ForEach-Object { $_.PSObject.Properties.Remove('caseIds') }
    $data.timelineEvents | ForEach-Object { $_.PSObject.Properties.Remove('caseIds') }
    Save-Json $data $portfolioPath
    $presentation = Get-Content -LiteralPath $presentationPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $presentation.schemaVersion = '2.0'
    $presentation.contentVersion = '2026-07-21.1'
    Save-Json $presentation $presentationPath
}

function New-PublicCase([object]$PortfolioData) {
    return [pscustomobject][ordered]@{
        id = 'case-one'
        code = 'CASE-01'
        slug = 'case-one'
        type = 'FEATURE'
        title = 'Case one'
        summary = 'A focused public case study'
        problem = 'The behavior needed an explicit public contract'
        actions = @('Implement validation')
        decisions = @('Keep compatibility')
        verification = @('Focused tests')
        outcome = 'The case contract is explicit'
        limitations = @('Public data only')
        achievementStatus = 'DELIVERED'
        contributionType = 'PRIMARY'
        projectId = $null
        claimIds = @()
        evidenceIds = @()
        timelineEventIds = @()
        questionPresetIds = @()
    }
}

function Invoke-LegacyReviewPack(
    [string]$CandidatePath,
    [object]$PortfolioData
) {
    $benchmarkPath = Join-Path $repositoryRoot `
        'governance\portfolio-governance\benchmark\active-benchmarks.v1.json'
    $originalBenchmarkBytes = [IO.File]::ReadAllBytes($benchmarkPath)
    try {
        $benchmarkCases = @()
        foreach ($preset in @($PortfolioData.questionPresets)) {
            foreach ($caseType in @(
                'SUPPORTED_QUESTION', 'ALIAS', 'BOUNDARY', 'CLAIM_EVIDENCE', 'SAFETY'
            )) {
                $benchmarkCase = [ordered]@{
                    caseId = 'LEGACY-' + $preset.id + '-' + $caseType
                    category = 'CONTRACT'
                    caseType = $caseType
                    questionPresetId = $preset.id
                    severity = 'ERROR'
                }
                if ($caseType -eq 'CLAIM_EVIDENCE') {
                    $benchmarkCase.requiredClaimIds = @()
                    $benchmarkCase.requiredEvidenceIds = @()
                }
                $benchmarkCases += [pscustomobject]$benchmarkCase
            }
        }
        Save-Json ([pscustomobject]@{
            schemaVersion = '1.0'
            cases = $benchmarkCases
        }) $benchmarkPath
        return Invoke-Governance @(
            '-Command', 'build-review-pack',
            '-Workspace', $workspace,
            '-Candidate', $CandidatePath
        )
    }
    finally {
        [IO.File]::WriteAllBytes($benchmarkPath, $originalBenchmarkBytes)
    }
}

function Invoke-CompilerMain(
    [string]$MainClass,
    [string]$Jar,
    [string[]]$Arguments
) {
    $output = & java.exe ("-Dloader.main=" + $MainClass) -cp $Jar `
        org.springframework.boot.loader.launch.PropertiesLauncher @Arguments 2>&1
    return @{ ExitCode = $LASTEXITCODE; Output = ($output -join [Environment]::NewLine) }
}

try {
    $missing = Invoke-Governance @('-Command', 'inspect')
    if ($missing.ExitCode -eq 0) { throw 'Missing workspace must fail.' }

    $inside = Invoke-Governance @('-Command', 'inspect', '-Workspace', $repositoryRoot)
    if ($inside.ExitCode -eq 0) { throw 'Repository-contained workspace must fail.' }

    $ignoredRuntimeReferences = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'scripts') `
        -File -Filter '*.ps1' | Where-Object {
            (Get-Content -LiteralPath $_.FullName -Raw) -match
                '\.agents[\\/]skills[\\/]portfolio-governance'
        })
    if ($ignoredRuntimeReferences.Count -gt 0) {
        throw 'Repository scripts must not reference the ignored local governance skill.'
    }
    $canonicalPackage = Join-Path $repositoryRoot 'governance\portfolio-governance'
    foreach ($canonicalRelativePath in @(
        'SKILL.md',
        'scripts\portfolio-governance.ps1',
        'policies\governance-policy.v1.json',
        'benchmark\active-benchmarks.v1.json',
        'benchmark\wave-1-benchmarks.v1.json',
        'schemas\asset-publication-decision-ledger.schema.json',
        'schemas\benchmark-case.schema.json',
        'schemas\feedback-signal.schema.json',
        'schemas\governance-case.schema.json',
        'schemas\playbook-rule.schema.json'
    )) {
        if (-not (Test-Path -LiteralPath (Join-Path $canonicalPackage $canonicalRelativePath) `
                -PathType Leaf)) {
            throw "Tracked canonical governance package is missing $canonicalRelativePath."
        }
    }
    $cleanCheckout = Join-Path $fixtureRoot 'clean-checkout'
    $cleanScripts = Join-Path $cleanCheckout 'scripts'
    $cleanGovernanceParent = Join-Path $cleanCheckout 'governance'
    New-Item -ItemType Directory -Force -Path $cleanScripts, $cleanGovernanceParent | Out-Null
    Copy-Item -LiteralPath $cli -Destination $cleanScripts
    Copy-Item -LiteralPath $canonicalPackage -Destination $cleanGovernanceParent -Recurse
    if (Test-Path -LiteralPath (Join-Path $cleanCheckout '.agents')) {
        throw 'Synthetic clean checkout must not contain .agents.'
    }
    $cleanWorkspace = Join-Path $fixtureRoot 'clean-workspace'
    New-Item -ItemType Directory -Force -Path $cleanWorkspace | Out-Null
    $script:governanceInvocationCount++
    $cleanOutput = & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $cleanScripts 'portfolio-governance.ps1') `
        -Command inspect -Workspace $cleanWorkspace 2>&1
    if ($LASTEXITCODE -ne 0 -or
            -not (($cleanOutput -join [Environment]::NewLine).Contains('"status":"PASS"'))) {
        throw 'Tracked governance package must operate in a clean checkout without .agents.'
    }

    $candidate = New-Candidate 'candidate-1'
    New-Item -ItemType Directory -Force -Path (Join-Path $workspace 'decisions') | Out-Null
    $decisionLedger = Join-Path $workspace 'decisions\asset-publication-decisions.json'
    New-DecisionLedger $decisionLedger $candidate

    foreach ($ledgerRequiredCommand in @(
        'validate', 'benchmark', 'build-review-pack', 'approve', 'publish', 'verify'
    )) {
        $script:governanceInvocationCount++
        $missingLedgerOutput = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $cli `
            -Command $ledgerRequiredCommand -Workspace $workspace -Candidate $candidate 2>&1
        $missingLedgerExitCode = $LASTEXITCODE
        if ($missingLedgerExitCode -eq 0 -or
                -not (($missingLedgerOutput -join [Environment]::NewLine).Contains('DECISION_LEDGER_REQUIRED'))) {
            throw "$ledgerRequiredCommand must require -DecisionLedger."
        }
    }

    $outsideLedger = Join-Path $fixtureRoot 'outside-ledger.json'
    Copy-Item -LiteralPath $decisionLedger -Destination $outsideLedger
    $outsideLedgerResult = Invoke-LedgerValidation $outsideLedger
    if ($outsideLedgerResult.ExitCode -eq 0 -or
            -not $outsideLedgerResult.Output.Contains('DECISION_LEDGER_OUTSIDE_WORKSPACE')) {
        throw 'Decision ledger outside the private workspace must fail closed.'
    }

    $missingIdLedger = Copy-Ledger 'missing-id'
    $missingIdData = Get-Content -LiteralPath $missingIdLedger -Raw -Encoding UTF8 | ConvertFrom-Json
    $missingIdData.assets = @($missingIdData.assets | Where-Object { $_.assetId -ne 'K-17' })
    Save-Json $missingIdData $missingIdLedger
    $missingIdResult = Invoke-LedgerValidation $missingIdLedger
    if ($missingIdResult.ExitCode -eq 0 -or
            -not $missingIdResult.Output.Contains('DECISION_LEDGER_ID_COVERAGE_INVALID')) {
        throw "Decision ledger must reject a missing asset ID: $($missingIdResult.Output)"
    }

    $duplicateIdLedger = Copy-Ledger 'duplicate-id'
    $duplicateIdData = Get-Content -LiteralPath $duplicateIdLedger -Raw -Encoding UTF8 | ConvertFrom-Json
    $duplicateIdData.assets[-1].assetId = 'L-01'
    Save-Json $duplicateIdData $duplicateIdLedger
    $duplicateIdResult = Invoke-LedgerValidation $duplicateIdLedger
    if ($duplicateIdResult.ExitCode -eq 0 -or
            -not $duplicateIdResult.Output.Contains('DECISION_LEDGER_ID_COVERAGE_INVALID')) {
        throw 'Decision ledger must reject duplicate asset IDs.'
    }

    $unknownIdLedger = Copy-Ledger 'unknown-id'
    $unknownIdData = Get-Content -LiteralPath $unknownIdLedger -Raw -Encoding UTF8 | ConvertFrom-Json
    $unknownIdData.assets[-1].assetId = 'X-01'
    Save-Json $unknownIdData $unknownIdLedger
    $unknownIdResult = Invoke-LedgerValidation $unknownIdLedger
    if ($unknownIdResult.ExitCode -eq 0 -or
            -not $unknownIdResult.Output.Contains('DECISION_LEDGER_ID_COVERAGE_INVALID')) {
        throw 'Decision ledger must reject unknown asset IDs.'
    }

    $realInventoryLedger = Copy-Ledger 'real-inventory-values'
    $realInventoryData = Get-Content -LiteralPath $realInventoryLedger -Raw -Encoding UTF8 |
        ConvertFrom-Json
    $realInventoryMutations = @(
        @{ AssetId = 'T-08'; ContentType = 'TASK'; AchievementStatus = 'VALIDATED_PROTOTYPE';
            ContributionType = 'PRIMARY' },
        @{ AssetId = 'K-05'; ContentType = 'KNOWLEDGE_ASSET'; AchievementStatus = 'DOCUMENTED_OUTPUT';
            ContributionType = 'PRIMARY' },
        @{ AssetId = 'K-03'; ContentType = 'KNOWLEDGE_ASSET'; AchievementStatus = 'LEARNING_ONLY';
            ContributionType = 'OBSERVED_LEARNING' },
        @{ AssetId = 'L-03'; ContentType = 'MAINLINE'; AchievementStatus = 'INVESTIGATED';
            ContributionType = 'UNRESOLVED' }
    )
    foreach ($mutation in $realInventoryMutations) {
        $asset = @($realInventoryData.assets |
            Where-Object { $_.assetId -eq $mutation.AssetId })[0]
        $asset.contentType = $mutation.ContentType
        $asset.achievementStatus = $mutation.AchievementStatus
        $asset.contributionType = $mutation.ContributionType
    }
    Save-Json $realInventoryData $realInventoryLedger
    $realInventoryResult = Invoke-LedgerValidation $realInventoryLedger
    if ($realInventoryResult.ExitCode -ne 0) {
        throw "Decision ledger must preserve known real inventory values: $($realInventoryResult.Output)"
    }

    $unknownInventoryValueLedger = Copy-Ledger 'unknown-inventory-value'
    $unknownInventoryValueData = Get-Content -LiteralPath $unknownInventoryValueLedger `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $unknownInventoryValueData.assets[1].achievementStatus = 'UNREVIEWED_MAGIC'
    Save-Json $unknownInventoryValueData $unknownInventoryValueLedger
    $unknownInventoryValueResult = Invoke-LedgerValidation $unknownInventoryValueLedger
    if ($unknownInventoryValueResult.ExitCode -eq 0 -or
            -not $unknownInventoryValueResult.Output.Contains('DECISION_LEDGER_SCHEMA_INVALID')) {
        throw 'Decision ledger must still reject unknown inventory values.'
    }

    $invalidRouteLedger = Copy-Ledger 'invalid-route'
    $invalidRouteData = Get-Content -LiteralPath $invalidRouteLedger -Raw -Encoding UTF8 | ConvertFrom-Json
    $invalidRouteData.assets[0].routeDecision = 'REVIEWED_HOLD'
    Save-Json $invalidRouteData $invalidRouteLedger
    $invalidRouteResult = Invoke-LedgerValidation $invalidRouteLedger
    if ($invalidRouteResult.ExitCode -eq 0 -or
            -not $invalidRouteResult.Output.Contains('DECISION_LEDGER_ROUTE_INVALID')) {
        throw 'Decision ledger must reject invalid finalRoute/routeDecision combinations.'
    }

    $holdLeakLedger = Copy-Ledger 'hold-leak'
    $holdLeakData = Get-Content -LiteralPath $holdLeakLedger -Raw -Encoding UTF8 | ConvertFrom-Json
    $holdLeakData.assets[1].projectSlugs = @('sql-audit')
    Save-Json $holdLeakData $holdLeakLedger
    $holdLeakResult = Invoke-LedgerValidation $holdLeakLedger
    if ($holdLeakResult.ExitCode -eq 0 -or
            -not $holdLeakResult.Output.Contains('DECISION_LEDGER_ROUTE_INVALID')) {
        throw 'HOLD and EXCLUDE records must not leak public references.'
    }

    $forwardReferenceLedger = Copy-Ledger 'forward-reference'
    $forwardReferenceData = Get-Content -LiteralPath $forwardReferenceLedger -Raw -Encoding UTF8 | ConvertFrom-Json
    $forwardReferenceData.assets[0].projectSlugs = @('missing-project')
    Save-Json $forwardReferenceData $forwardReferenceLedger
    $forwardReferenceResult = Invoke-LedgerValidation $forwardReferenceLedger
    if ($forwardReferenceResult.ExitCode -eq 0 -or
            -not $forwardReferenceResult.Output.Contains('DECISION_LEDGER_FORWARD_REFERENCE_INVALID')) {
        throw 'Decision ledger public references must resolve in candidate content.'
    }

    $reverseReferenceLedger = Copy-Ledger 'reverse-reference'
    $reverseReferenceData = Get-Content -LiteralPath $reverseReferenceLedger -Raw -Encoding UTF8 | ConvertFrom-Json
    $reverseReferenceData.assets |
        Where-Object { $_.assetId -eq 'K-01' } |
        ForEach-Object { $_.evidenceIds = @('sql-audit-delivery-set') }
    Save-Json $reverseReferenceData $reverseReferenceLedger
    $reverseReferenceResult = Invoke-LedgerValidation $reverseReferenceLedger
    if ($reverseReferenceResult.ExitCode -eq 0 -or
            -not $reverseReferenceResult.Output.Contains('DECISION_LEDGER_REVERSE_REFERENCE_INVALID')) {
        throw 'Every public Evidence must reverse-map to a ledger item.'
    }

    foreach ($statusMutation in @(
        @{ Name = 'achievement-upgrade'; Field = 'achievementStatus'; Value = 'IMPLEMENTED_TESTED' },
        @{ Name = 'contribution-upgrade'; Field = 'contributionType'; Value = 'COLLABORATIVE' }
    )) {
        $statusLedger = Copy-Ledger $statusMutation.Name
        $statusData = Get-Content -LiteralPath $statusLedger -Raw -Encoding UTF8 | ConvertFrom-Json
        $statusData.assets[0].($statusMutation.Field) = $statusMutation.Value
        Save-Json $statusData $statusLedger
        $statusResult = Invoke-LedgerValidation $statusLedger
        if ($statusResult.ExitCode -eq 0 -or
                -not $statusResult.Output.Contains('DECISION_LEDGER_STATUS_UPGRADE')) {
            throw "Candidate must not automatically upgrade $($statusMutation.Field)."
        }
    }

    $partialEvidenceLedger = Copy-Ledger 'partial-evidence-narrow-public-scope'
    $partialEvidenceData = Get-Content -LiteralPath $partialEvidenceLedger -Raw -Encoding UTF8 |
        ConvertFrom-Json
    $partialEvidenceData.assets |
        Where-Object { $_.assetId -eq 'L-01' } |
        ForEach-Object { $_.evidenceStatus = 'PARTIALLY_VERIFIED' }
    Save-Json $partialEvidenceData $partialEvidenceLedger
    $partialEvidenceResult = Invoke-LedgerValidation $partialEvidenceLedger
    if ($partialEvidenceResult.ExitCode -ne 0) {
        throw "A partially verified asset may cite narrow APPROVED public Evidence: $($partialEvidenceResult.Output)"
    }

    $insufficientEvidenceLedger = Copy-Ledger 'insufficient-evidence-public-reference'
    $insufficientEvidenceData = Get-Content -LiteralPath $insufficientEvidenceLedger `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $insufficientEvidenceData.assets |
        Where-Object { $_.assetId -eq 'L-01' } |
        ForEach-Object { $_.evidenceStatus = 'INSUFFICIENT' }
    Save-Json $insufficientEvidenceData $insufficientEvidenceLedger
    $insufficientEvidenceResult = Invoke-LedgerValidation $insufficientEvidenceLedger
    if ($insufficientEvidenceResult.ExitCode -eq 0 -or
            -not $insufficientEvidenceResult.Output.Contains('DECISION_LEDGER_STATUS_UPGRADE')) {
        throw 'An insufficient asset must not cite public Evidence.'
    }

    $ownerConfirmedEvidenceLedger = Copy-Ledger 'owner-confirmed-public-reference'
    $ownerConfirmedEvidenceData = Get-Content -LiteralPath $ownerConfirmedEvidenceLedger `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $ownerConfirmedEvidenceData.assets |
        Where-Object { $_.assetId -eq 'L-01' } |
        ForEach-Object { $_.evidenceStatus = 'OWNER_CONFIRMED' }
    Save-Json $ownerConfirmedEvidenceData $ownerConfirmedEvidenceLedger
    $ownerConfirmedEvidenceResult = Invoke-LedgerValidation $ownerConfirmedEvidenceLedger
    if ($ownerConfirmedEvidenceResult.ExitCode -ne 0) {
        throw "Owner-confirmed source Evidence may support a reviewed narrow public summary: $($ownerConfirmedEvidenceResult.Output)"
    }

    $ownerConfirmedWithoutEvidenceLedger = Copy-Ledger 'owner-confirmed-without-evidence-reference'
    $ownerConfirmedWithoutEvidenceData = Get-Content -LiteralPath $ownerConfirmedWithoutEvidenceLedger `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $ownerConfirmedWithoutEvidenceData.assets |
        Where-Object { $_.assetId -eq 'L-01' } |
        ForEach-Object {
            $_.evidenceStatus = 'OWNER_CONFIRMED'
            $_.evidenceIds = @()
        }
    Save-Json $ownerConfirmedWithoutEvidenceData $ownerConfirmedWithoutEvidenceLedger
    $ownerConfirmedWithoutEvidenceResult = Invoke-LedgerValidation $ownerConfirmedWithoutEvidenceLedger
    if ($ownerConfirmedWithoutEvidenceResult.ExitCode -eq 0 -or
            -not $ownerConfirmedWithoutEvidenceResult.Output.Contains('DECISION_LEDGER_ROUTE_INVALID')) {
        throw 'Every publish candidate must retain its own direct Evidence reference.'
    }

    $documentedEvidenceLedger = Copy-Ledger 'documented-evidence-only'
    $documentedEvidenceData = Get-Content -LiteralPath $documentedEvidenceLedger `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $documentedAsset = @($documentedEvidenceData.assets |
        Where-Object { $_.assetId -eq 'T-17' })[0]
    $documentedAsset.contentType = 'TASK'
    $documentedAsset.achievementStatus = 'DOCUMENTED_OUTPUT'
    $documentedAsset.contributionType = 'PRIMARY'
    $documentedAsset.publicPriority = 'P1'
    $documentedAsset.evidenceStatus = 'VERIFIED'
    $documentedAsset.originalReviewState = 'PUBLIC_REVIEW_REQUIRED'
    $documentedAsset.finalRoute = 'EVIDENCE_ONLY'
    $documentedAsset.decisionReason = 'Documentation enriches the existing project without changing its status.'
    $documentedAsset.evidenceIds = @('sql-audit-delivery-set')
    $documentedAsset.routeDecision = 'PUBLISH_CANDIDATE'
    $documentedAsset.targetContentVersion = $currentBundleVersion
    $documentedAsset.targetWave = 1
    Save-Json $documentedEvidenceData $documentedEvidenceLedger
    $documentedEvidenceResult = Invoke-LedgerValidation $documentedEvidenceLedger
    if ($documentedEvidenceResult.ExitCode -ne 0) {
        throw "Evidence-only documentation must not inherit or upgrade Project status: $($documentedEvidenceResult.Output)"
    }

    $openCase = Invoke-Governance @('-Command', 'case', '-Workspace', $workspace,
        '-CaseId', 'CASE-001', '-TargetStatus', 'OPEN', '-CaseSource', 'BENCHMARK',
        '-ContentVersion', '2026-07-21.1', '-FailureType', 'CONTENT_MISMATCH',
        '-SanitizedObservation', 'Synthetic benchmark mismatch',
        '-ExpectedBehavior', 'Deterministic answer matches public claim')
    if ($openCase.ExitCode -ne 0) { throw "Opening a governance case failed: $($openCase.Output)" }
    $incompleteClosure = Invoke-Governance @('-Command', 'case', '-Workspace', $workspace,
        '-CaseId', 'CASE-001', '-TargetStatus', 'RESOLVED')
    if ($incompleteClosure.ExitCode -eq 0 -or -not $incompleteClosure.Output.Contains('CASE_CLOSURE_INCOMPLETE')) {
        throw 'Resolved Case must bind root cause, fixed version, regression benchmark, and playbook decision.'
    }
    $resolvedCase = Invoke-Governance @('-Command', 'case', '-Workspace', $workspace,
        '-CaseId', 'CASE-001', '-TargetStatus', 'RESOLVED', '-RootCause', 'Fixture mapping drift',
        '-ResolutionNote', 'Updated deterministic projection', '-FixedVersion', '2026-07-21.2',
        '-RegressionBenchmarkCaseId', 'BENCH-REG-001', '-PlaybookDecision', 'NO_RULE')
    if ($resolvedCase.ExitCode -ne 0) { throw "Resolving a complete governance case failed: $($resolvedCase.Output)" }

    $valid = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $candidate)
    if ($valid.ExitCode -ne 0) { throw "Expected valid candidate to pass: $($valid.Output)" }
    $result = $valid.Output | ConvertFrom-Json
    if ($result.status -ne 'PASS') { throw 'Expected PASS machine status.' }
    if ($valid.Output.Contains($workspace)) { throw 'Machine output leaked private absolute path.' }
    if ($result.runSnapshot.candidatePayloadHash -ne 'sha256:8512518d0a2ea4a2e3c0eed9cdb086bc1689d04d11bb5bb60890113b552a4c00') {
        throw 'PowerShell candidatePayloadHash does not match the approved public Bundle test vector.'
    }
    if (-not $result.runSnapshot.ledgerHash.StartsWith('sha256:')) {
        throw 'GovernanceRunSnapshot must bind the exact decision ledger hash.'
    }
    $ledgerBytesBeforeMutation = [IO.File]::ReadAllBytes($decisionLedger)
    $ledgerMutationData = Get-Content -LiteralPath $decisionLedger -Raw -Encoding UTF8 | ConvertFrom-Json
    $ledgerMutationData.assets[0].decisionReason = 'Synthetic published mapping with mutation'
    Save-Json $ledgerMutationData $decisionLedger
    $mutatedLedgerValidation = Invoke-Governance @(
        '-Command', 'validate', '-Workspace', $workspace, '-Candidate', $candidate
    )
    if ($mutatedLedgerValidation.ExitCode -ne 0) {
        throw "A structurally valid ledger mutation should validate as a new chain: $($mutatedLedgerValidation.Output)"
    }
    $mutatedLedgerResult = $mutatedLedgerValidation.Output | ConvertFrom-Json
    if ($mutatedLedgerResult.runSnapshot.candidatePayloadHash -ne
            $result.runSnapshot.candidatePayloadHash -or
            $mutatedLedgerResult.runSnapshot.ledgerHash -eq $result.runSnapshot.ledgerHash) {
        throw 'Ledger mutation must change ledgerHash without changing candidatePayloadHash.'
    }
    [IO.File]::WriteAllBytes($decisionLedger, $ledgerBytesBeforeMutation)
    if ($result.runSnapshot.policyBundleHash.Contains('pending') -or
        $result.runSnapshot.benchmarkDefinitionHash.Contains('pending')) {
        throw 'GovernanceRunSnapshot must bind exact policy and benchmark definitions.'
    }
    if ($result.gates -join ',' -ne 'SchemaGate,ReferenceIntegrityGate,PrivacyGate,ClaimEvidenceGate,CompatibilityGate') {
        throw 'Read-only gates did not run in fixed order.'
    }

    $unsupportedBenchmarkVersion = '2026-07-25.1'
    $unsupportedBenchmarkCandidate = New-Candidate 'unsupported-benchmark-version'
    foreach ($name in @('portfolio.json', 'presentation.json')) {
        $path = Join-Path $unsupportedBenchmarkCandidate $name
        (Get-Content -LiteralPath $path -Raw -Encoding UTF8).Replace(
            $currentBundleVersion, $unsupportedBenchmarkVersion) |
            Set-Content -LiteralPath $path -Encoding UTF8
    }
    $unsupportedBenchmarkLedger = Copy-Ledger 'unsupported-benchmark-version'
    $unsupportedBenchmarkLedgerData = Get-Content -LiteralPath $unsupportedBenchmarkLedger `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $unsupportedBenchmarkLedgerData.assets | Where-Object {
        $_.routeDecision -eq 'PUBLISH_CANDIDATE'
    } | ForEach-Object { $_.targetContentVersion = $unsupportedBenchmarkVersion }
    Save-Json $unsupportedBenchmarkLedgerData $unsupportedBenchmarkLedger
    $unsupportedBenchmark = Invoke-Governance @(
        '-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $unsupportedBenchmarkCandidate,
        '-DecisionLedger', $unsupportedBenchmarkLedger
    )
    if ($unsupportedBenchmark.ExitCode -eq 0 -or
            -not $unsupportedBenchmark.Output.Contains('BENCHMARK_VERSION_UNSUPPORTED')) {
        throw 'Unknown schema 3.0 content versions must fail closed before governance review.'
    }

    $downgradedWaveOneCandidate = New-Candidate 'downgraded-wave-one-version'
    ConvertTo-SchemaTwo $downgradedWaveOneCandidate
    foreach ($name in @('portfolio.json', 'presentation.json')) {
        $path = Join-Path $downgradedWaveOneCandidate $name
        (Get-Content -LiteralPath $path -Raw -Encoding UTF8).Replace(
            '2026-07-21.1', '2026-07-24.1') |
            Set-Content -LiteralPath $path -Encoding UTF8
    }
    $downgradedWaveOne = Invoke-Governance @(
        '-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $downgradedWaveOneCandidate
    )
    if ($downgradedWaveOne.ExitCode -eq 0 -or
            -not $downgradedWaveOne.Output.Contains('BENCHMARK_VERSION_UNSUPPORTED')) {
        throw "Schema downgrade must not bypass the benchmark suite bound to Wave 1: $($downgradedWaveOne.Output)"
    }

    $unknownCandidate = New-Candidate 'unknown-field'
    $unknownData = Get-Content -LiteralPath (Join-Path $unknownCandidate 'portfolio.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $unknownData | Add-Member -NotePropertyName internalNotes -NotePropertyValue 'must-not-pass'
    $unknownData | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath (Join-Path $unknownCandidate 'portfolio.json') -Encoding UTF8
    $unknown = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace, '-Candidate', $unknownCandidate)
    if ($unknown.ExitCode -eq 0 -or -not $unknown.Output.Contains('SCHEMA_UNKNOWN_FIELD')) { throw "Unknown field must fail SchemaGate: $($unknown.Output)" }

    $danglingCandidate = New-Candidate 'dangling-link'
    $danglingData = Get-Content -LiteralPath (Join-Path $danglingCandidate 'portfolio.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $danglingData.claimEvidenceLinks[0].evidenceId = 'missing-evidence'
    $danglingData | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath (Join-Path $danglingCandidate 'portfolio.json') -Encoding UTF8
    $dangling = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace, '-Candidate', $danglingCandidate)
    if ($dangling.ExitCode -eq 0 -or -not $dangling.Output.Contains('REFERENCE_DANGLING_LINK')) { throw 'Dangling Link must fail ReferenceIntegrityGate.' }

    $invalidClaimCandidate = New-Candidate 'invalid-claim'
    $invalidClaimData = Get-Content -LiteralPath (Join-Path $invalidClaimCandidate 'portfolio.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $invalidClaimData.claims[0].verificationBasis = 'SELF_DECLARED'
    $invalidClaimData | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath (Join-Path $invalidClaimCandidate 'portfolio.json') -Encoding UTF8
    $invalidClaim = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace, '-Candidate', $invalidClaimCandidate)
    if ($invalidClaim.ExitCode -eq 0 -or -not $invalidClaim.Output.Contains('CLAIM_VERIFICATION_INVALID')) { throw 'Invalid Claim elevation must fail ClaimEvidenceGate.' }

    $privateCandidate = New-Candidate 'private-content'
    $privateData = Get-Content -LiteralPath (Join-Path $privateCandidate 'portfolio.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $privateData.owner.summary = 'host=192.168.10.24'
    $privateData | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath (Join-Path $privateCandidate 'portfolio.json') -Encoding UTF8
    $private = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace, '-Candidate', $privateCandidate)
    if ($private.ExitCode -eq 0 -or -not $private.Output.Contains('PRIVACY_CONTENT_REJECTED')) { throw 'Private content must fail PrivacyGate.' }

    $legacy = New-Candidate 'schema-two-legacy'
    ConvertTo-SchemaTwo $legacy
    $legacyResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $legacy)
    if ($legacyResult.ExitCode -ne 0) {
        throw "Schema 2.0 candidate must normalize to zero cases: $($legacyResult.Output)"
    }
    $legacyData = Get-Content -LiteralPath (Join-Path $legacy 'portfolio.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($legacyData.schemaVersion -ne '2.0' -or
            $legacyData.PSObject.Properties.Name -contains 'cases') {
        throw 'Schema 2.0 compatibility fixture must omit the Case collection.'
    }
    $legacyData | Add-Member -NotePropertyName cases -NotePropertyValue @(
        (New-PublicCase $legacyData)
    )
    $legacyData.questionPresets | ForEach-Object {
        $_ | Add-Member -NotePropertyName caseIds -NotePropertyValue @('case-hostile')
    }
    $legacyData.timelineEvents | ForEach-Object {
        $_ | Add-Member -NotePropertyName caseIds -NotePropertyValue @('case-hostile')
    }
    Save-Json $legacyData (Join-Path $legacy 'portfolio.json')
    $hostileLegacyResult = Invoke-Governance @(
        '-Command', 'validate', '-Workspace', $workspace, '-Candidate', $legacy
    )
    if ($hostileLegacyResult.ExitCode -ne 0) {
        throw "Schema 2.0 hostile Case fields must normalize away: $($hostileLegacyResult.Output)"
    }
    $legacyReview = Invoke-LegacyReviewPack $legacy $legacyData
    if ($legacyReview.ExitCode -ne 0) {
        throw "Schema 2.0 review pack failed: $($legacyReview.Output)"
    }
    $legacyReviewResult = $legacyReview.Output | ConvertFrom-Json
    $legacyReviewPack = Join-Path $workspace $legacyReviewResult.artifacts[1]
    $legacySummary = Get-Content -LiteralPath (Join-Path $legacyReviewPack 'summary.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($legacySummary.counts.cases -ne 0) {
        throw 'Schema 2.0 review summary must report zero cases.'
    }
    $legacyCasesJson = Get-Content -LiteralPath (Join-Path $legacyReviewPack 'cases.json') `
        -Raw -Encoding UTF8
    if ($legacyCasesJson -notmatch '^\s*\[\s*\]\s*$') {
        throw 'Schema 2.0 review cases.json must be an empty array.'
    }

    $schemaThree = New-Candidate 'schema-three'
    $schemaThreeData = ConvertTo-SchemaThree $schemaThree
    Save-Json $schemaThreeData (Join-Path $schemaThree 'portfolio.json')
    $schemaThreeResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $schemaThree)
    if ($schemaThreeResult.ExitCode -ne 0) {
        throw "Schema 3.0 candidate with explicit cases must pass: $($schemaThreeResult.Output)"
    }

    $missingCases = New-Candidate 'schema-three-missing-cases'
    $missingCasesData = ConvertTo-SchemaThree $missingCases
    $missingCasesData.PSObject.Properties.Remove('cases')
    Save-Json $missingCasesData (Join-Path $missingCases 'portfolio.json')
    $missingCasesResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $missingCases)
    if ($missingCasesResult.ExitCode -eq 0 -or
            -not $missingCasesResult.Output.Contains('SCHEMA_CASES_REQUIRED')) {
        throw 'Schema 3.0 must require cases.'
    }

    $missingQuestionCaseIds = New-Candidate 'schema-three-missing-question-case-ids'
    $missingQuestionData = ConvertTo-SchemaThree $missingQuestionCaseIds
    $missingQuestionData.questionPresets[0].PSObject.Properties.Remove('caseIds')
    Save-Json $missingQuestionData (Join-Path $missingQuestionCaseIds 'portfolio.json')
    $missingQuestionResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $missingQuestionCaseIds)
    if ($missingQuestionResult.ExitCode -eq 0 -or
            -not $missingQuestionResult.Output.Contains('SCHEMA_CASE_IDS_REQUIRED')) {
        throw 'Schema 3.0 must require questionPreset.caseIds.'
    }

    $missingTimelineCaseIds = New-Candidate 'schema-three-missing-timeline-case-ids'
    $missingTimelineData = ConvertTo-SchemaThree $missingTimelineCaseIds
    $missingTimelineData.timelineEvents[0].PSObject.Properties.Remove('caseIds')
    Save-Json $missingTimelineData (Join-Path $missingTimelineCaseIds 'portfolio.json')
    $missingTimelineResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $missingTimelineCaseIds)
    if ($missingTimelineResult.ExitCode -eq 0 -or
            -not $missingTimelineResult.Output.Contains('SCHEMA_CASE_IDS_REQUIRED')) {
        throw 'Schema 3.0 must require timelineEvent.caseIds.'
    }

    foreach ($referenceCase in @(
        @{ Name = 'claim'; Property = 'claimIds'; Missing = 'claim-missing'; Code = 'REFERENCE_DANGLING_CASE_CLAIM' },
        @{ Name = 'evidence'; Property = 'evidenceIds'; Missing = 'evidence-missing'; Code = 'REFERENCE_DANGLING_CASE_EVIDENCE' },
        @{ Name = 'timeline'; Property = 'timelineEventIds'; Missing = 'timeline-missing'; Code = 'REFERENCE_DANGLING_CASE_TIMELINE' },
        @{ Name = 'question'; Property = 'questionPresetIds'; Missing = 'question-missing'; Code = 'REFERENCE_DANGLING_CASE_QUESTION' }
    )) {
        $caseCandidate = New-Candidate ('dangling-case-' + $referenceCase.Name)
        $caseData = ConvertTo-SchemaThree $caseCandidate
        $publicCase = New-PublicCase $caseData
        $publicCase.($referenceCase.Property) = @($referenceCase.Missing)
        $caseData.cases = @($caseData.cases) + @($publicCase)
        Save-Json $caseData (Join-Path $caseCandidate 'portfolio.json')
        $caseResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
            '-Candidate', $caseCandidate)
        if ($caseResult.ExitCode -eq 0 -or
                -not $caseResult.Output.Contains($referenceCase.Code)) {
            throw "Unknown Case $($referenceCase.Name) reference must fail ReferenceIntegrityGate."
        }
    }

    $danglingQuestionCase = New-Candidate 'dangling-question-case'
    $danglingQuestionData = ConvertTo-SchemaThree $danglingQuestionCase
    $danglingQuestionData.questionPresets[0].caseIds = @('case-missing')
    Save-Json $danglingQuestionData (Join-Path $danglingQuestionCase 'portfolio.json')
    $danglingQuestionResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $danglingQuestionCase)
    if ($danglingQuestionResult.ExitCode -eq 0 -or
            -not $danglingQuestionResult.Output.Contains('REFERENCE_DANGLING_CASE')) {
        throw 'Unknown QuestionPreset Case reference must fail ReferenceIntegrityGate.'
    }

    $casePrivacyCandidate = New-Candidate 'case-private-content'
    $casePrivacyData = ConvertTo-SchemaThree $casePrivacyCandidate
    $privateCase = New-PublicCase $casePrivacyData
    $privateCase.summary = 'Internal host 192.168.1.24'
    $casePrivacyData.cases = @($casePrivacyData.cases) + @($privateCase)
    Save-Json $casePrivacyData (Join-Path $casePrivacyCandidate 'portfolio.json')
    $casePrivacy = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $casePrivacyCandidate)
    if ($casePrivacy.ExitCode -eq 0 -or
            -not $casePrivacy.Output.Contains('PRIVACY_CONTENT_REJECTED')) {
        throw 'Every Case text field must be privacy-scanned.'
    }

    $metricLeak = New-Candidate 'codegraph-metric-leak'
    $metricLeakData = Get-Content -LiteralPath (Join-Path $metricLeak 'portfolio.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $metricLeakData.owner.summary = [Text.Encoding]::UTF8.GetString(
        [Convert]::FromBase64String('Q29kZUdyYXBoIOWkp+WcuuaZr+iKguecgSAyOC4yJQ=='))
    Save-Json $metricLeakData (Join-Path $metricLeak 'portfolio.json')
    $metricLeakResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $metricLeak)
    if ($metricLeakResult.ExitCode -eq 0 -or
            -not $metricLeakResult.Output.Contains('PRIVACY_CONTENT_REJECTED')) {
        throw 'Forbidden exact CodeGraph metrics must fail PrivacyGate.'
    }

    $metricBeforeCodeGraph = New-Candidate 'codegraph-metric-before-name'
    $metricBeforeCodeGraphData = Get-Content -LiteralPath `
        (Join-Path $metricBeforeCodeGraph 'portfolio.json') -Raw -Encoding UTF8 |
        ConvertFrom-Json
    $metricBeforeCodeGraphData.owner.summary = '28.2% measured in CodeGraph'
    Save-Json $metricBeforeCodeGraphData (Join-Path $metricBeforeCodeGraph 'portfolio.json')
    $metricBeforeCodeGraphResult = Invoke-Governance @('-Command', 'validate',
        '-Workspace', $workspace, '-Candidate', $metricBeforeCodeGraph)
    if ($metricBeforeCodeGraphResult.ExitCode -eq 0 -or
            -not $metricBeforeCodeGraphResult.Output.Contains('PRIVACY_CONTENT_REJECTED')) {
        throw 'Forbidden exact CodeGraph metrics must fail regardless of text order.'
    }

    $qualitativeCodeGraph = New-Candidate 'codegraph-qualitative'
    $qualitativeData = Get-Content -LiteralPath (Join-Path $qualitativeCodeGraph 'portfolio.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $qualitativeData.owner.summary = [Text.Encoding]::UTF8.GetString(
        [Convert]::FromBase64String(
            'Q29kZUdyYXBoIOWcqOWkp+WcuuaZr+S4reWHj+WwkeaXoOWFs+S4iuS4i+aWh++8jOS9humcgOimgeS6uuW3peWkjeaguOetlOahiOi0qOmHjw=='))
    Save-Json $qualitativeData (Join-Path $qualitativeCodeGraph 'portfolio.json')
    $qualitativeResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $qualitativeCodeGraph)
    if ($qualitativeResult.ExitCode -ne 0) {
        throw "Approved qualitative CodeGraph wording must pass: $($qualitativeResult.Output)"
    }

    $allowedProfile = New-Candidate 'allowed-csdn-profile'
    $allowedProfileData = Get-Content -LiteralPath (Join-Path $allowedProfile 'portfolio.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $allowedProfileData.owner.githubUrl = 'https://blog.csdn.net/2301_81073317'
    Save-Json $allowedProfileData (Join-Path $allowedProfile 'portfolio.json')
    $allowedProfileResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $allowedProfile)
    if ($allowedProfileResult.ExitCode -ne 0) {
        throw "The sole CSDN profile allowlist URL must pass: $($allowedProfileResult.Output)"
    }

    foreach ($urlCase in @(
        @{ Name = 'unapproved-url-host'; Url = 'https://example.com/private-profile' },
        @{ Name = 'unapproved-url-prefix'; Url = 'https://blog.csdn.net/2301_81073317.evil.example' },
        @{ Name = 'unapproved-url-query'; Url = 'https://blog.csdn.net/2301_81073317?next=evil' },
        @{ Name = 'unapproved-url-fragment'; Url = 'https://blog.csdn.net/2301_81073317#private' },
        @{ Name = 'unapproved-url-path'; Url = 'https://blog.csdn.net/2301_81073317/private' }
    )) {
        $unapprovedUrl = New-Candidate $urlCase.Name
        $unapprovedUrlData = Get-Content -LiteralPath (Join-Path $unapprovedUrl 'portfolio.json') `
            -Raw -Encoding UTF8 | ConvertFrom-Json
        $unapprovedUrlData.owner.githubUrl = $urlCase.Url
        Save-Json $unapprovedUrlData (Join-Path $unapprovedUrl 'portfolio.json')
        $unapprovedUrlResult = Invoke-Governance @('-Command', 'validate',
            '-Workspace', $workspace, '-Candidate', $unapprovedUrl)
        if ($unapprovedUrlResult.ExitCode -eq 0 -or
                -not $unapprovedUrlResult.Output.Contains('PRIVACY_CONTENT_REJECTED')) {
            throw "Non-allowlisted URL must fail PrivacyGate: $($urlCase.Name)."
        }
    }

    $emailLeak = New-Candidate 'email-leak'
    $emailLeakData = Get-Content -LiteralPath (Join-Path $emailLeak 'portfolio.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $emailLeakData.owner.email = 'owner@example.com'
    Save-Json $emailLeakData (Join-Path $emailLeak 'portfolio.json')
    $emailLeakResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $emailLeak)
    if ($emailLeakResult.ExitCode -eq 0 -or
            -not $emailLeakResult.Output.Contains('PRIVACY_CONTENT_REJECTED')) {
        throw 'Email addresses must fail PrivacyGate.'
    }

    foreach ($sqlCase in @(
        @{ Name = 'raw-sql-insert'; Text = 'INSERT INTO accounts VALUES (1)' },
        @{ Name = 'raw-sql-delete'; Text = 'DELETE FROM accounts WHERE id = 1' },
        @{ Name = 'raw-sql-replace'; Text = 'REPLACE INTO accounts VALUES (1)' }
    )) {
        $sqlLeak = New-Candidate $sqlCase.Name
        $sqlLeakData = Get-Content -LiteralPath (Join-Path $sqlLeak 'portfolio.json') `
            -Raw -Encoding UTF8 | ConvertFrom-Json
        $sqlLeakData.owner.summary = $sqlCase.Text
        Save-Json $sqlLeakData (Join-Path $sqlLeak 'portfolio.json')
        $sqlLeakResult = Invoke-Governance @('-Command', 'validate',
            '-Workspace', $workspace, '-Candidate', $sqlLeak)
        if ($sqlLeakResult.ExitCode -eq 0 -or
                -not $sqlLeakResult.Output.Contains('PRIVACY_CONTENT_REJECTED')) {
            throw "Raw SQL fragment must fail PrivacyGate: $($sqlCase.Name)."
        }
    }

    $privateSource = New-Candidate 'private-source-name'
    $privateSourceData = Get-Content -LiteralPath (Join-Path $privateSource 'portfolio.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $privateSourceData.owner.summary = 'source d11_manager_test'
    Save-Json $privateSourceData (Join-Path $privateSource 'portfolio.json')
    $privateSourceResult = Invoke-Governance @('-Command', 'validate',
        '-Workspace', $workspace, '-Candidate', $privateSource)
    if ($privateSourceResult.ExitCode -eq 0 -or
            -not $privateSourceResult.Output.Contains('PRIVACY_CONTENT_REJECTED')) {
        throw 'Private source names must fail PrivacyGate independently.'
    }

    $schemaThreeReview = Invoke-Governance @('-Command', 'build-review-pack', '-Workspace', $workspace,
        '-Candidate', $schemaThree)
    if ($schemaThreeReview.ExitCode -ne 0) {
        throw "Schema 3.0 review failed: $($schemaThreeReview.Output)"
    }
    $schemaThreeReviewResult = $schemaThreeReview.Output | ConvertFrom-Json
    $schemaThreeReviewPack = Join-Path $workspace $schemaThreeReviewResult.artifacts[1]
    if (-not (Test-Path -LiteralPath (Join-Path $schemaThreeReviewPack 'cases.json') -PathType Leaf)) {
        throw 'Review output must include the public Case changes.'
    }
    $schemaThreeSummary = Get-Content -LiteralPath (Join-Path $schemaThreeReviewPack 'summary.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($schemaThreeSummary.counts.cases -ne @($schemaThreeData.cases).Count) {
        throw 'Review output must include the Case count.'
    }
    $caseReviewCandidate = New-Candidate 'schema-three-case-review'
    $caseReviewData = ConvertTo-SchemaThree $caseReviewCandidate
    $reviewCase = New-PublicCase $caseReviewData
    $reviewCase.evidenceIds = @($caseReviewData.evidence[0].id)
    $caseReviewData.cases = @($caseReviewData.cases) + @($reviewCase)
    Save-Json $caseReviewData (Join-Path $caseReviewCandidate 'portfolio.json')
    $caseReview = Invoke-Governance @('-Command', 'build-review-pack', '-Workspace', $workspace,
        '-Candidate', $caseReviewCandidate)
    if ($caseReview.ExitCode -ne 0) { throw "Case review failed: $($caseReview.Output)" }
    $caseReviewResult = $caseReview.Output | ConvertFrom-Json
    $caseReviewSummary = Get-Content -LiteralPath `
        (Join-Path (Join-Path $workspace $caseReviewResult.artifacts[1]) 'summary.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $evidenceId = [string]$caseReviewData.evidence[0].id
    if ($caseReviewSummary.counts.cases -ne (@($caseReviewData.cases).Count) -or
            @($caseReviewSummary.caseSlugsByEvidenceId.$evidenceId) -notcontains 'case-one') {
        throw 'Review output must expose Evidence-to-Case slug changes.'
    }
    if (Test-Path -LiteralPath (Join-Path $workspace 'approvals') -PathType Container) {
        throw 'Review-pack generation must never auto-approve a candidate.'
    }
    $schemaThreeApproval = Invoke-Governance @('-Command', 'approve', '-Workspace', $workspace,
        '-Candidate', $schemaThree, '-ReviewRunId', $schemaThreeReviewResult.runId,
        '-ApprovedBy', 'owner-alias', '-PrivacyReviewId', 'PRIV-SCHEMA-3',
        '-BenchmarkRunId', 'BENCH-SCHEMA-3')
    if ($schemaThreeApproval.ExitCode -ne 0) {
        throw "Schema 3.0 approval failed: $($schemaThreeApproval.Output)"
    }
    $schemaThreeApprovalResult = $schemaThreeApproval.Output | ConvertFrom-Json
    $schemaThreeApprovalData = Get-Content -LiteralPath `
        (Join-Path $workspace $schemaThreeApprovalResult.artifacts[-1]) -Raw -Encoding UTF8 |
        ConvertFrom-Json
    $schemaThreeReleaseRoot = Join-Path $fixtureRoot 'schema-three-releases'
    New-Item -ItemType Directory -Force -Path $schemaThreeReleaseRoot | Out-Null
    $schemaThreePublish = Invoke-Governance @('-Command', 'publish', '-Workspace', $workspace,
        '-Candidate', $schemaThree, '-ApprovalId', $schemaThreeApprovalData.approvalId,
        '-ReleaseRoot', $schemaThreeReleaseRoot, '-Confirm')
    if ($schemaThreePublish.ExitCode -ne 0) {
        throw "Schema 3.0 publish fixture failed: $($schemaThreePublish.Output)"
    }
    $schemaThreePublishedVersion = Join-Path $schemaThreeReleaseRoot (
        'versions\' + [string]$schemaThreeData.contentVersion
    )
    $schemaThreeManifestPath = Join-Path $schemaThreePublishedVersion 'manifest.json'
    $schemaThreeManifest = Get-Content -LiteralPath $schemaThreeManifestPath `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($schemaThreeManifest.schemaVersion -ne '3.0' -or
            -not ($schemaThreeManifest.counts.PSObject.Properties.Name -contains 'cases') -or
            $schemaThreeManifest.counts.cases -ne @($schemaThreeData.cases).Count) {
        throw 'Schema 3.0 Manifest must explicitly bind counts.cases.'
    }
    $schemaThreeManifest.counts.PSObject.Properties.Remove('cases')
    Save-Json $schemaThreeManifest $schemaThreeManifestPath
    $missingManifestCases = Invoke-Governance @('-Command', 'verify', '-Workspace', $workspace,
        '-ReleaseRoot', $schemaThreeReleaseRoot,
        '-TargetVersion', [string]$schemaThreeData.contentVersion)
    if ($missingManifestCases.ExitCode -eq 0 -or
            -not $missingManifestCases.Output.Contains('VERIFY_TARGET_INVALID')) {
        throw 'Schema 3.0 verify must reject a Manifest without counts.cases.'
    }
    Remove-Item -LiteralPath (Join-Path $workspace 'audit\publish.jsonl') -Force

    $uncoveredCandidate = New-Candidate 'uncovered-preset'
    $uncoveredData = Get-Content -LiteralPath (Join-Path $uncoveredCandidate 'portfolio.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $extraPreset = $uncoveredData.questionPresets[0].PSObject.Copy()
    $extraPreset.id = 'uncovered-preset'
    $extraPreset.text = 'A newly supported question without regression coverage'
    $uncoveredData.questionPresets = @($uncoveredData.questionPresets) + @($extraPreset)
    $uncoveredData | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath (Join-Path $uncoveredCandidate 'portfolio.json') -Encoding UTF8
    $uncovered = Invoke-Governance @('-Command', 'benchmark', '-Workspace', $workspace, '-Candidate', $uncoveredCandidate)
    if ($uncovered.ExitCode -eq 0 -or -not $uncovered.Output.Contains('BENCHMARK_COVERAGE_MISSING')) { throw 'Every active preset must have complete benchmark coverage.' }

    $review = Invoke-Governance @('-Command', 'build-review-pack', '-Workspace', $workspace, '-Candidate', $candidate)
    if ($review.ExitCode -ne 0) { throw "Review pack failed: $($review.Output)" }
    $reviewResult = $review.Output | ConvertFrom-Json
    if ($reviewResult.artifacts.Count -ne 2) { throw 'Review run must expose snapshot and review-pack artifact IDs.' }
    $reviewPack = Join-Path $workspace $reviewResult.artifacts[1]
    foreach ($name in @('summary.json', 'claims.json', 'links.json', 'privacy.json', 'benchmark.json', 'checksums.json', 'approval-request.json')) {
        if (-not (Test-Path -LiteralPath (Join-Path $reviewPack $name) -PathType Leaf)) { throw "Review pack is missing $name." }
    }

    $missingIdentity = Invoke-Governance @('-Command', 'approve', '-Workspace', $workspace,
        '-Candidate', $candidate, '-ReviewRunId', $reviewResult.runId)
    if ($missingIdentity.ExitCode -eq 0 -or -not $missingIdentity.Output.Contains('APPROVAL_METADATA_REQUIRED')) { throw 'Approval requires explicit human metadata.' }

    $staleCandidate = New-Candidate 'stale-approval'
    $staleReview = Invoke-Governance @('-Command', 'build-review-pack', '-Workspace', $workspace, '-Candidate', $staleCandidate)
    $staleReviewResult = $staleReview.Output | ConvertFrom-Json
    Add-Content -LiteralPath (Join-Path $staleCandidate 'presentation.json') -Value ' '
    $staleApproval = Invoke-Governance @('-Command', 'approve', '-Workspace', $workspace,
        '-Candidate', $staleCandidate, '-ReviewRunId', $staleReviewResult.runId,
        '-ApprovedBy', 'owner-alias', '-PrivacyReviewId', 'PRIV-001', '-BenchmarkRunId', 'BENCH-001')
    if ($staleApproval.ExitCode -eq 0 -or -not $staleApproval.Output.Contains('APPROVAL_RUN_STALE')) { throw 'Changed candidate bytes must invalidate approval.' }

    $approval = Invoke-Governance @('-Command', 'approve', '-Workspace', $workspace,
        '-Candidate', $candidate, '-ReviewRunId', $reviewResult.runId,
        '-ApprovedBy', 'owner-alias', '-PrivacyReviewId', 'PRIV-001', '-BenchmarkRunId', 'BENCH-001')
    if ($approval.ExitCode -ne 0) { throw "Approval failed: $($approval.Output)" }
    $approvalResult = $approval.Output | ConvertFrom-Json
    if ($approvalResult.artifacts.Count -lt 2) { throw 'Approval and audit artifacts were not recorded.' }
    $approvalFile = Join-Path $workspace $approvalResult.artifacts[-1]
    $approvalData = Get-Content -LiteralPath $approvalFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($approvalData.candidatePayloadHash -ne $reviewResult.runSnapshot.candidatePayloadHash -or
        $approvalData.ledgerHash -ne $reviewResult.runSnapshot.ledgerHash -or
        $approvalData.inputFingerprint -ne $reviewResult.runSnapshot.inputFingerprint -or
        $null -ne $approvalData.compilerJarHash -or
        -not $approvalData.approvalDigest.StartsWith('sha256:')) {
        throw 'Approval did not bind the canonical payload, decision ledger, input fingerprint, and compiler identity.'
    }

    $approvalBytesBeforeTamper = [IO.File]::ReadAllBytes($approvalFile)
    $approvalTamperData = Get-Content -LiteralPath $approvalFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $approvalTamperData.approvedBy = 'tampered-owner'
    Save-Json $approvalTamperData $approvalFile
    $approvalDigestTamperPublish = Invoke-Governance @(
        '-Command', 'publish', '-Workspace', $workspace, '-Candidate', $candidate,
        '-ApprovalId', $approvalData.approvalId,
        '-ReleaseRoot', (Join-Path $fixtureRoot 'approval-digest-tamper-releases')
    )
    if ($approvalDigestTamperPublish.ExitCode -eq 0 -or
            -not $approvalDigestTamperPublish.Output.Contains('PUBLISH_APPROVAL_STALE')) {
        throw 'Publish must reject a tampered Approval projection or digest.'
    }
    [IO.File]::WriteAllBytes($approvalFile, $approvalBytesBeforeTamper)

    $approvedLedgerBytes = [IO.File]::ReadAllBytes($decisionLedger)
    $approvedLedgerData = Get-Content -LiteralPath $decisionLedger -Raw -Encoding UTF8 | ConvertFrom-Json
    $approvedLedgerData.assets[0].decisionReason = 'Mutation after Approval'
    Save-Json $approvedLedgerData $decisionLedger
    $staleLedgerPublish = Invoke-Governance @('-Command', 'publish', '-Workspace', $workspace,
        '-Candidate', $candidate, '-ApprovalId', $approvalData.approvalId,
        '-ReleaseRoot', (Join-Path $fixtureRoot 'stale-ledger-releases'))
    if ($staleLedgerPublish.ExitCode -eq 0 -or
            -not $staleLedgerPublish.Output.Contains('PUBLISH_APPROVAL_STALE')) {
        throw 'Ledger mutation after Approval must make publish stale.'
    }
    [IO.File]::WriteAllBytes($decisionLedger, $approvedLedgerBytes)

    $releaseRoot = Join-Path $fixtureRoot 'public-releases'
    New-Item -ItemType Directory -Force -Path $releaseRoot | Out-Null
    $publishHashMismatch = New-Candidate 'publish-hash-mismatch'
    $publishHashReview = Invoke-Governance @('-Command', 'build-review-pack', '-Workspace', $workspace,
        '-Candidate', $publishHashMismatch)
    $publishHashReviewResult = $publishHashReview.Output | ConvertFrom-Json
    $publishHashApproval = Invoke-Governance @('-Command', 'approve', '-Workspace', $workspace,
        '-Candidate', $publishHashMismatch, '-ReviewRunId', $publishHashReviewResult.runId,
        '-ApprovedBy', 'owner-alias', '-PrivacyReviewId', 'PRIV-HASH', '-BenchmarkRunId', 'BENCH-HASH')
    $publishHashApprovalResult = $publishHashApproval.Output | ConvertFrom-Json
    $publishHashApprovalData = Get-Content -LiteralPath `
        (Join-Path $workspace $publishHashApprovalResult.artifacts[-1]) -Raw -Encoding UTF8 |
        ConvertFrom-Json
    Add-Content -LiteralPath (Join-Path $publishHashMismatch 'portfolio.json') -Value ' '
    $publishHashMismatchResult = Invoke-Governance @('-Command', 'publish', '-Workspace', $workspace,
        '-Candidate', $publishHashMismatch, '-ApprovalId', $publishHashApprovalData.approvalId,
        '-ReleaseRoot', $releaseRoot)
    if ($publishHashMismatchResult.ExitCode -eq 0 -or
            -not $publishHashMismatchResult.Output.Contains('PUBLISH_APPROVAL_STALE')) {
        throw 'Publish must reject candidate bytes that differ from the approved hash.'
    }

    $dryRun = Invoke-Governance @('-Command', 'publish', '-Workspace', $workspace,
        '-Candidate', $candidate, '-ApprovalId', $approvalData.approvalId, '-ReleaseRoot', $releaseRoot)
    if ($dryRun.ExitCode -ne 0 -or -not (($dryRun.Output | ConvertFrom-Json).dryRun)) { throw 'Publish must default to dry-run.' }
    if (Test-Path -LiteralPath (Join-Path $releaseRoot ('versions\' + $currentBundleVersion))) { throw 'Dry-run changed public release state.' }

    $blockedPublishAudit = Join-Path $workspace 'audit\publish.jsonl'
    New-Item -ItemType Directory -Force -Path $blockedPublishAudit | Out-Null
    $auditFailure = Invoke-Governance @('-Command', 'publish', '-Workspace', $workspace,
        '-Candidate', $candidate, '-ApprovalId', $approvalData.approvalId,
        '-ReleaseRoot', $releaseRoot, '-Confirm')
    if ($auditFailure.ExitCode -eq 0 -or -not $auditFailure.Output.Contains('PUBLISH_AUDIT_WRITE_FAILED')) {
        throw "Publish must fail closed when its security audit is unavailable: $($auditFailure.Output)"
    }
    if (Test-Path -LiteralPath (Join-Path $releaseRoot ('versions\' + $currentBundleVersion))) {
        throw 'Audit failure must happen before any public release state is written.'
    }
    Remove-Item -LiteralPath $blockedPublishAudit -Recurse -Force

    $probeWithoutRollbackPoint = Invoke-Governance @(
        '-Command', 'publish', '-Workspace', $workspace,
        '-Candidate', $candidate, '-ApprovalId', $approvalData.approvalId,
        '-ReleaseRoot', $releaseRoot,
        '-PostSwitchProbeUri', 'http://127.0.0.1:1/health', '-Confirm'
    )
    if ($probeWithoutRollbackPoint.ExitCode -eq 0 -or
            -not $probeWithoutRollbackPoint.Output.Contains(
                'PUBLISH_ROLLBACK_POINT_REQUIRED'
            )) {
        throw "A post-switch probe must require an active rollback point: $($probeWithoutRollbackPoint.Output)"
    }
    if ((Test-Path -LiteralPath (Join-Path $releaseRoot 'active')) -or
            (Test-Path -LiteralPath (
                Join-Path $releaseRoot ('versions\' + $currentBundleVersion)
            ))) {
        throw 'A rejected first-publication probe must not create public release state.'
    }

    $publish = Invoke-Governance @('-Command', 'publish', '-Workspace', $workspace,
        '-Candidate', $candidate, '-ApprovalId', $approvalData.approvalId, '-ReleaseRoot', $releaseRoot, '-Confirm')
    if ($publish.ExitCode -ne 0) { throw "Publish failed: $($publish.Output)" }
    if (-not [Linq.Enumerable]::SequenceEqual(
            [byte[]]$approvedLedgerBytes,
            [byte[]][IO.File]::ReadAllBytes($decisionLedger))) {
        throw 'Successful publish must not rewrite decision ledger bytes.'
    }
    $publishReceipt = (Get-Content -LiteralPath (Join-Path $workspace 'audit\publish.jsonl') `
        -Encoding UTF8 | Select-Object -Last 1) | ConvertFrom-Json
    if ($publishReceipt.ledgerHash -ne $approvalData.ledgerHash) {
        throw 'Publish receipt must join to the exact approved ledgerHash.'
    }
    $publishedVersion = Join-Path $releaseRoot ('versions\' + $currentBundleVersion)
    if (-not (Test-Path -LiteralPath (Join-Path $publishedVersion 'manifest.json') -PathType Leaf) -or
        -not (Test-Path -LiteralPath (Join-Path $publishedVersion 'checksums.json') -PathType Leaf)) { throw 'Published bundle is incomplete.' }
    if (-not [Linq.Enumerable]::SequenceEqual([byte[]][IO.File]::ReadAllBytes((Join-Path $candidate 'portfolio.json')),
        [byte[]][IO.File]::ReadAllBytes((Join-Path $publishedVersion 'portfolio.json')))) { throw 'Publish changed approved portfolio bytes.' }
    if ((Get-Content -LiteralPath (Join-Path $releaseRoot 'active') -Raw).Trim() -ne $currentBundleVersion) { throw 'Active pointer was not switched.' }

    $listed = Invoke-Governance @('-Command', 'list', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot)
    if ($listed.ExitCode -ne 0 -or @((($listed.Output | ConvertFrom-Json).versions)).Count -ne 1) {
        throw "List must report complete published versions: $($listed.Output)"
    }
    $status = Invoke-Governance @('-Command', 'status', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot)
    $statusResult = $status.Output | ConvertFrom-Json
    if ($status.ExitCode -ne 0 -or $statusResult.activeVersion -ne $currentBundleVersion) {
        throw "Status must report the active version: $($status.Output)"
    }
    $publishedManifestPath = Join-Path $publishedVersion 'manifest.json'
    $publishedManifestBytes = [IO.File]::ReadAllBytes($publishedManifestPath)
    $publishedManifestData = Get-Content -LiteralPath $publishedManifestPath `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $publishedManifestData.ledgerHash = 'sha256:' + ('0' * 64)
    Save-Json $publishedManifestData $publishedManifestPath
    $tamperedManifestVerify = Invoke-Governance @(
        '-Command', 'verify', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot, '-TargetVersion', $currentBundleVersion
    )
    if ($tamperedManifestVerify.ExitCode -eq 0 -or
            -not $tamperedManifestVerify.Output.Contains('VERIFY_APPROVAL_INVALID')) {
        throw 'Verify must reject a Manifest ledger identity that differs from Approval.'
    }
    [IO.File]::WriteAllBytes($publishedManifestPath, $publishedManifestBytes)
    $verifyMutationBytes = [IO.File]::ReadAllBytes($decisionLedger)
    $verifyMutationData = Get-Content -LiteralPath $decisionLedger -Raw -Encoding UTF8 | ConvertFrom-Json
    $verifyMutationData.assets[0].decisionReason = 'Mutation before verify'
    Save-Json $verifyMutationData $decisionLedger
    $staleVerify = Invoke-Governance @('-Command', 'verify', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot, '-TargetVersion', $currentBundleVersion)
    if ($staleVerify.ExitCode -eq 0 -or
            -not $staleVerify.Output.Contains('VERIFY_LEDGER_STALE')) {
        throw 'Ledger mutation after Approval must make verify stale.'
    }
    [IO.File]::WriteAllBytes($decisionLedger, $verifyMutationBytes)
    $verified = Invoke-Governance @('-Command', 'verify', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot, '-TargetVersion', $currentBundleVersion)
    if ($verified.ExitCode -ne 0 -or ($verified.Output | ConvertFrom-Json).verifiedVersion -ne $currentBundleVersion) {
        throw "Verify must validate an immutable published bundle: $($verified.Output)"
    }
    if (-not [Linq.Enumerable]::SequenceEqual(
            [byte[]]$approvedLedgerBytes,
            [byte[]][IO.File]::ReadAllBytes($decisionLedger))) {
        throw 'Successful verify must not rewrite decision ledger bytes.'
    }
    $verifyReceipt = (Get-Content -LiteralPath (Join-Path $workspace 'audit\verify.jsonl') `
        -Encoding UTF8 | Select-Object -Last 1) | ConvertFrom-Json
    if ($verifyReceipt.ledgerHash -ne $approvalData.ledgerHash -or
            $verifyReceipt.approvalId -ne $approvalData.approvalId) {
        throw 'Verify receipt and closure join must use the exact approved ledgerHash.'
    }

    $repeat = Invoke-Governance @('-Command', 'publish', '-Workspace', $workspace,
        '-Candidate', $candidate, '-ApprovalId', $approvalData.approvalId, '-ReleaseRoot', $releaseRoot, '-Confirm')
    if ($repeat.ExitCode -ne 0) { throw 'Identical repeat publish must be idempotent.' }

    Set-Content -LiteralPath (Join-Path $releaseRoot 'active') -Value 'broken-active' -Encoding UTF8
    $rollbackDryRun = Invoke-Governance @('-Command', 'rollback', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot, '-TargetVersion', $currentBundleVersion)
    if ($rollbackDryRun.ExitCode -ne 0 -or -not (($rollbackDryRun.Output | ConvertFrom-Json).dryRun)) { throw 'Rollback must default to dry-run.' }
    if ((Get-Content -LiteralPath (Join-Path $releaseRoot 'active') -Raw).Trim() -ne 'broken-active') { throw 'Rollback dry-run changed active.' }
    $blockedRollbackAudit = Join-Path $workspace 'audit\rollback.jsonl'
    New-Item -ItemType Directory -Force -Path $blockedRollbackAudit | Out-Null
    $rollbackAuditFailure = Invoke-Governance @('-Command', 'rollback', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot, '-TargetVersion', $currentBundleVersion, '-Confirm')
    if ($rollbackAuditFailure.ExitCode -eq 0 -or -not $rollbackAuditFailure.Output.Contains('ROLLBACK_AUDIT_WRITE_FAILED')) {
        throw "Rollback must fail closed when its security audit is unavailable: $($rollbackAuditFailure.Output)"
    }
    if ((Get-Content -LiteralPath (Join-Path $releaseRoot 'active') -Raw).Trim() -ne 'broken-active') {
        throw 'Rollback audit failure must preserve the old active pointer.'
    }
    Remove-Item -LiteralPath $blockedRollbackAudit -Recurse -Force
    @{ versions = @($currentBundleVersion) } | ConvertTo-Json |
        Set-Content -LiteralPath (Join-Path $releaseRoot 'blocked-versions.json') -Encoding UTF8
    $blockedRollback = Invoke-Governance @('-Command', 'rollback', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot, '-TargetVersion', $currentBundleVersion, '-Confirm')
    if ($blockedRollback.ExitCode -eq 0 -or -not $blockedRollback.Output.Contains('ROLLBACK_TARGET_BLOCKED')) {
        throw 'Rollback must reject versions in blocked-versions.json.'
    }
    Remove-Item -LiteralPath (Join-Path $releaseRoot 'blocked-versions.json') -Force
    $rollback = Invoke-Governance @('-Command', 'rollback', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot, '-TargetVersion', $currentBundleVersion, '-Confirm')
    if ($rollback.ExitCode -ne 0) { throw "Rollback failed: $($rollback.Output)" }
    if ((Get-Content -LiteralPath (Join-Path $releaseRoot 'active') -Raw).Trim() -ne $currentBundleVersion) { throw 'Rollback did not restore verified target.' }

    $compilerJar = Join-Path $repositoryRoot 'backend\target\portfolio-agent.jar'
    $localModel = Join-Path $repositoryRoot 'runtime-models\bge-small-zh-v1.5'
    if ((Test-Path -LiteralPath $compilerJar -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $localModel 'onnx\model_quantized.onnx') -PathType Leaf)) {
        $retrievalCandidate = New-Candidate 'retrieval-candidate'
        $ragFile = Join-Path $retrievalCandidate 'rag-documents.jsonl'
        $ragBuild = Invoke-CompilerMain 'com.portfolio.agent.release.RagDocumentCompilerCli' `
            $compilerJar @('--portfolio', (Join-Path $retrievalCandidate 'portfolio.json'),
                '--output', $ragFile, '--valid-from', $currentBundleVersion.Substring(0, 10))
        if ($ragBuild.ExitCode -ne 0) { throw "RAG candidate build failed: $($ragBuild.Output)" }

        $retrievalValidation = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
            '-Candidate', $retrievalCandidate, '-JarPath', $compilerJar)
        if ($retrievalValidation.ExitCode -ne 0) {
            throw "Canonical retrieval candidate failed validation: $($retrievalValidation.Output)"
        }
        $retrievalValidationResult = $retrievalValidation.Output | ConvertFrom-Json
        if ($retrievalValidationResult.runSnapshot.candidatePayloadHash -eq
                $result.runSnapshot.candidatePayloadHash) {
            throw 'Retrieval candidate hash must bind canonical RAG bytes.'
        }

        $tamperedRetrievalCandidate = New-Candidate 'retrieval-tampered'
        Copy-Item -LiteralPath $ragFile -Destination $tamperedRetrievalCandidate
        Add-Content -LiteralPath (Join-Path $tamperedRetrievalCandidate 'rag-documents.jsonl') -Value ' '
        $tamperedRetrieval = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
            '-Candidate', $tamperedRetrievalCandidate, '-JarPath', $compilerJar)
        if ($tamperedRetrieval.ExitCode -eq 0 -or
                -not $tamperedRetrieval.Output.Contains('RAG_CANONICAL_MISMATCH')) {
            throw 'Non-canonical RAG bytes must fail before Approval.'
        }

        $retrievalReview = Invoke-Governance @('-Command', 'build-review-pack',
            '-Workspace', $workspace, '-Candidate', $retrievalCandidate, '-JarPath', $compilerJar)
        if ($retrievalReview.ExitCode -ne 0) { throw "Retrieval review failed: $($retrievalReview.Output)" }
        $retrievalReviewResult = $retrievalReview.Output | ConvertFrom-Json
        $retrievalApproval = Invoke-Governance @('-Command', 'approve', '-Workspace', $workspace,
            '-Candidate', $retrievalCandidate, '-ReviewRunId', $retrievalReviewResult.runId,
            '-ApprovedBy', 'owner-alias', '-PrivacyReviewId', 'PRIV-C2',
            '-BenchmarkRunId', 'BENCH-C2', '-JarPath', $compilerJar)
        if ($retrievalApproval.ExitCode -ne 0) { throw "Retrieval approval failed: $($retrievalApproval.Output)" }
        $retrievalApprovalResult = $retrievalApproval.Output | ConvertFrom-Json
        $retrievalApprovalData = Get-Content -LiteralPath `
            (Join-Path $workspace $retrievalApprovalResult.artifacts[-1]) -Raw -Encoding UTF8 |
            ConvertFrom-Json
        if ($retrievalApprovalData.inputFingerprint -ne
                $retrievalReviewResult.runSnapshot.inputFingerprint -or
                -not $retrievalApprovalData.compilerJarHash.StartsWith('sha256:') -or
                $retrievalApprovalData.compilerJarHash -ne
                $retrievalReviewResult.runSnapshot.compilerJarHash) {
            throw 'Retrieval Approval must bind the reviewed input fingerprint and compiler JAR hash.'
        }
        $changedCompilerJar = Join-Path $fixtureRoot 'changed-portfolio-agent.jar'
        Copy-Item -LiteralPath $compilerJar -Destination $changedCompilerJar
        Add-Type -AssemblyName System.IO.Compression
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $changedCompilerArchive = [IO.Compression.ZipFile]::Open(
            $changedCompilerJar,
            [IO.Compression.ZipArchiveMode]::Update)
        try {
            $markerEntry = $changedCompilerArchive.CreateEntry(
                'META-INF/governance-test-marker.txt')
            $markerStream = $markerEntry.Open()
            try {
                $markerBytes = [Text.Encoding]::UTF8.GetBytes('changed compiler identity')
                $markerStream.Write($markerBytes, 0, $markerBytes.Length)
            }
            finally {
                $markerStream.Dispose()
            }
        }
        finally {
            $changedCompilerArchive.Dispose()
        }
        $changedCompilerReleaseRoot = Join-Path $fixtureRoot 'changed-compiler-public-releases'
        New-Item -ItemType Directory -Force -Path $changedCompilerReleaseRoot | Out-Null
        $changedCompilerPublish = Invoke-Governance @(
            '-Command', 'publish', '-Workspace', $workspace,
            '-Candidate', $retrievalCandidate,
            '-ApprovalId', $retrievalApprovalData.approvalId,
            '-ReleaseRoot', $changedCompilerReleaseRoot,
            '-JarPath', $changedCompilerJar,
            '-ModelDirectory', $localModel)
        if ($changedCompilerPublish.ExitCode -eq 0 -or
                -not $changedCompilerPublish.Output.Contains('PUBLISH_APPROVAL_STALE')) {
            throw 'Changing the compiler JAR after Approval must make retrieval publish stale.'
        }
        $retrievalReleaseRoot = Join-Path $fixtureRoot 'retrieval-public-releases'
        New-Item -ItemType Directory -Force -Path $retrievalReleaseRoot | Out-Null
        $retrievalPublish = Invoke-Governance @('-Command', 'publish', '-Workspace', $workspace,
            '-Candidate', $retrievalCandidate, '-ApprovalId', $retrievalApprovalData.approvalId,
            '-ReleaseRoot', $retrievalReleaseRoot, '-JarPath', $compilerJar,
            '-ModelDirectory', $localModel, '-Confirm')
        if ($retrievalPublish.ExitCode -ne 0) { throw "Retrieval publish failed: $($retrievalPublish.Output)" }
        $retrievalVersion = Join-Path $retrievalReleaseRoot ('versions\' + $currentBundleVersion)
        $retrievalNames = @(Get-ChildItem -LiteralPath $retrievalVersion -File |
            ForEach-Object { $_.Name } | Sort-Object)
        if (($retrievalNames -join ',') -ne
                'checksums.json,keyword-index.json,manifest.json,portfolio.json,presentation.json,rag-documents.jsonl,vector-index.bin') {
            throw 'Retrieval publish did not produce the closed seven-file runtime bundle.'
        }
        if (-not [Linq.Enumerable]::SequenceEqual(
                [byte[]][IO.File]::ReadAllBytes($ragFile),
                [byte[]][IO.File]::ReadAllBytes((Join-Path $retrievalVersion 'rag-documents.jsonl')))) {
            throw 'Retrieval publish changed approved canonical RAG bytes.'
        }
        $retrievalManifest = Get-Content -LiteralPath (Join-Path $retrievalVersion 'manifest.json') `
            -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($null -eq $retrievalManifest.retrieval -or
                $retrievalManifest.candidatePayloadHash -ne
                $retrievalApprovalData.candidatePayloadHash) {
            throw 'Runtime Manifest did not bind Approval and retrieval metadata.'
        }
        $retrievalVerify = Invoke-Governance @('-Command', 'verify', '-Workspace', $workspace,
            '-ReleaseRoot', $retrievalReleaseRoot, '-TargetVersion', $currentBundleVersion)
        if ($retrievalVerify.ExitCode -ne 0) {
            throw "Seven-file retrieval release failed verify: $($retrievalVerify.Output)"
        }
    }

    Write-Output "portfolio-governance tests passed; governance command scenarios=$governanceInvocationCount"
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
}
