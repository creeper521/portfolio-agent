param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$patchPath = Join-Path $root `
    'governance\portfolio-governance\candidates\weekend-login-abtest-public-patch.json'
$routesPath = Join-Path $root `
    'governance\portfolio-governance\candidates\weekend-login-abtest-public-routes.json'
$baselineLedgerPath = Join-Path $root `
    'governance\portfolio-governance\baselines\schema-4-publication-decisions.json'
$basePortfolioPath = Join-Path $root `
    'backend\src\main\resources\public-data\bundle\portfolio.json'
$runtimeBundlePath = Split-Path $basePortfolioPath -Parent
$governancePath = Join-Path $root 'scripts\portfolio-governance.ps1'
$temporaryPaths = @()

function Assert-True([bool]$Value, [string]$Message) {
    if (-not $Value) { throw $Message }
}

Assert-True (Test-Path -LiteralPath $patchPath -PathType Leaf) `
    'ABTest patch is required.'
Assert-True (Test-Path -LiteralPath $routesPath -PathType Leaf) `
    'ABTest routes are required.'
Assert-True (Test-Path -LiteralPath $baselineLedgerPath -PathType Leaf) `
    'Schema 4 publication baseline is required.'

$patch = Get-Content -LiteralPath $patchPath -Raw -Encoding UTF8 | ConvertFrom-Json
$routes = Get-Content -LiteralPath $routesPath -Raw -Encoding UTF8 | ConvertFrom-Json
$baselineLedger = Get-Content -LiteralPath $baselineLedgerPath -Raw -Encoding UTF8 |
    ConvertFrom-Json
$basePortfolio = Get-Content -LiteralPath $basePortfolioPath -Raw -Encoding UTF8 |
    ConvertFrom-Json

Assert-True (@($patch.claims | Where-Object {
            $_.verificationBasis -eq 'EVIDENCE_SUPPORTED' -and
            $_.verificationStatus -ne 'VERIFIED'
        }).Count -eq 0) `
    'Evidence-supported ABTest Claims must be VERIFIED for the runtime snapshot contract.'

Assert-True ((@($baselineLedger.PSObject.Properties.Name | Sort-Object) -join ',') -eq `
        'assets,contentVersion,schemaVersion') `
    'Schema 4 publication baseline top-level fields are invalid.'
Assert-True ($baselineLedger.schemaVersion -eq '1.0') `
    'Schema 4 publication baseline schema is invalid.'
Assert-True ($baselineLedger.contentVersion -eq '2026-07-29.1') `
    'Schema 4 publication baseline content version is invalid.'
Assert-True (@($baselineLedger.assets).Count -eq 68) `
    'Schema 4 publication baseline must contain 68 assets.'
Assert-True (@($baselineLedger.assets.assetId | Select-Object -Unique).Count -eq 68) `
    'Schema 4 publication baseline asset ids must be unique.'

$baselineAssetFields = @(
    'assetId', 'caseSlugs', 'evidenceIds', 'finalRoute', 'projectSlugs',
    'routeDecision'
) | Sort-Object
$allowedBaselineRoutes = @(
    'PROJECT', 'CASE', 'ENRICH_EXISTING_PROJECT', 'EVIDENCE_ONLY', 'EXCLUDE'
)
foreach ($asset in @($baselineLedger.assets)) {
    Assert-True ((@($asset.PSObject.Properties.Name | Sort-Object) -join ',') -eq `
            ($baselineAssetFields -join ',')) `
        "Schema 4 publication baseline fields are invalid for $($asset.assetId)."
    Assert-True ($asset.finalRoute -in $allowedBaselineRoutes) `
        "Schema 4 publication baseline route is invalid for $($asset.assetId)."
    Assert-True ($asset.routeDecision -in @('PUBLISH_CANDIDATE', 'EXCLUDED')) `
        "Schema 4 publication baseline decision is invalid for $($asset.assetId)."
    foreach ($referenceField in @('projectSlugs', 'caseSlugs', 'evidenceIds')) {
        $references = @($asset.$referenceField)
        Assert-True (@($references | Select-Object -Unique).Count -eq $references.Count) `
            "Schema 4 publication baseline references must be unique for $($asset.assetId)."
    }
    if ($asset.routeDecision -eq 'EXCLUDED') {
        Assert-True ($asset.finalRoute -eq 'EXCLUDE' -and
                @($asset.projectSlugs).Count -eq 0 -and
                @($asset.caseSlugs).Count -eq 0 -and
                @($asset.evidenceIds).Count -eq 0) `
            "Excluded Schema 4 assets must not publish references: $($asset.assetId)."
    }
}

$baselineProjectSlugs = @($baselineLedger.assets.projectSlugs | Select-Object -Unique)
$baselineCaseSlugs = @($baselineLedger.assets.caseSlugs | Select-Object -Unique)
$baselineEvidenceIds = @($baselineLedger.assets.evidenceIds | Select-Object -Unique)
$runtimeBaselineProjects = @($basePortfolio.projects | Where-Object {
        @($patch.projects.id) -notcontains $_.id
    })
$runtimeBaselineCases = @($basePortfolio.cases | Where-Object {
        @($patch.cases.id) -notcontains $_.id
    })
$runtimeBaselineEvidence = @($basePortfolio.evidence | Where-Object {
        @($patch.evidence.id) -notcontains $_.id
    })
Assert-True ((@($baselineProjectSlugs | Sort-Object) -join ',') -eq `
        (@($runtimeBaselineProjects.slug | Sort-Object) -join ',')) `
    'Schema 4 publication baseline must reverse-map every Project.'
Assert-True ((@($baselineCaseSlugs | Sort-Object) -join ',') -eq `
        (@($runtimeBaselineCases.slug | Sort-Object) -join ',')) `
    'Schema 4 publication baseline must reverse-map every Case.'
Assert-True ((@($baselineEvidenceIds | Sort-Object) -join ',') -eq `
        (@($runtimeBaselineEvidence.id | Sort-Object) -join ',')) `
    'Schema 4 publication baseline must reverse-map every Evidence summary.'

Assert-True ($patch.schemaVersion -eq '3.0') `
    'Incremental patch schema must be 3.0.'
Assert-True ($patch.baseContentVersion -eq '2026-07-29.1') `
    'ABTest base content version is invalid.'
Assert-True ($patch.targetContentVersion -eq '2026-08-04.2') `
    'ABTest target content version is invalid.'
Assert-True (@($patch.projects).Count -eq 1) 'Expected one ABTest Project.'
Assert-True (@($patch.cases).Count -eq 3) 'Expected three ABTest Cases.'
Assert-True (@($patch.timelineEvents).Count -eq 1) 'Expected one ABTest timeline event.'
Assert-True (@($patch.claims).Count -eq 4) 'Expected four ABTest Claims.'
Assert-True (@($patch.evidence).Count -eq 4) 'Expected four ABTest Evidence summaries.'
Assert-True (@($patch.links).Count -eq 4) 'Expected four ABTest Claim-Evidence links.'
Assert-True (@($patch.presets).Count -eq 3) 'Expected three ABTest presets.'
Assert-True (@($patch.projectUpdates).Count -eq 0) 'ABTest patch must not update existing Projects.'
Assert-True (@($patch.caseUpdates).Count -eq 0) 'ABTest patch must not update existing Cases.'

$project = @($patch.projects)[0]
Assert-True ($project.id -eq 'weekend-login-abtest-project') 'ABTest Project id is invalid.'
Assert-True ($project.code -eq 'P-07') 'ABTest Project code is invalid.'
Assert-True ($project.slug -eq 'weekend-login-abtest') 'ABTest Project slug is invalid.'
Assert-True ($project.status -eq 'DELIVERED') 'ABTest Project status is invalid.'
Assert-True ($project.contributionType -eq 'PRIMARY') 'ABTest contribution must be PRIMARY.'
Assert-True ($project.careerTrack -eq 'JAVA_BACKEND') 'ABTest career track is invalid.'
Assert-True ($project.projectNature -eq 'WORKSTREAM') 'ABTest project nature is invalid.'
Assert-True ($project.displayTier -eq 'PRIMARY') 'ABTest display tier is invalid.'

$expectedCaseIds = @(
    'case-abtest-experiment-design',
    'case-abtest-service-sql',
    'case-abtest-validation-risk-control'
)
$expectedCaseCodes = @('CASE-53', 'CASE-54', 'CASE-55')
Assert-True ((@($patch.cases.id) -join ',') -eq ($expectedCaseIds -join ',')) `
    'ABTest Case ids are invalid.'
Assert-True ((@($patch.cases.code) -join ',') -eq ($expectedCaseCodes -join ',')) `
    'ABTest Case codes are invalid.'
Assert-True (@($patch.cases | Where-Object {
            $_.projectId -ne 'weekend-login-abtest-project' -or
            $_.achievementStatus -ne 'DELIVERED' -or
            $_.contributionType -ne 'PRIMARY' -or
            $_.type -ne 'FEATURE' -or
            @($_.collectionIds).Count -ne 0
        }).Count -eq 0) 'ABTest Case classification is invalid.'

$expectedClaimIds = @(
    'claim-abtest-project-delivered',
    'claim-abtest-experiment-design',
    'claim-abtest-service-sql',
    'claim-abtest-validation-risk-control'
)
$expectedEvidenceIds = @(
    'evidence-abtest-delivery-history',
    'evidence-abtest-experiment-design-notes',
    'evidence-abtest-service-sql-evolution',
    'evidence-abtest-validation-risk-notes'
)
$expectedPresetIds = @(
    'question-abtest-overview',
    'question-abtest-stratification-bucketing',
    'question-abtest-stable-assignment-and-rollback'
)
Assert-True ((@($patch.claims.id) -join ',') -eq ($expectedClaimIds -join ',')) `
    'ABTest Claim ids are invalid.'
Assert-True ((@($patch.evidence.id) -join ',') -eq ($expectedEvidenceIds -join ',')) `
    'ABTest Evidence ids are invalid.'
Assert-True ((@($patch.presets.id) -join ',') -eq ($expectedPresetIds -join ',')) `
    'ABTest preset ids are invalid.'
Assert-True (@($patch.evidence | Where-Object {
            $_.publicStatus -ne 'APPROVED' -or $_.rawContentPublic -ne $false
        }).Count -eq 0) 'ABTest Evidence must be approved summaries with private raw content.'
Assert-True (@($patch.links | Where-Object {
            $_.supportType -ne 'DIRECT' -or $_.reviewStatus -ne 'APPROVED'
        }).Count -eq 0) 'ABTest links must be direct and approved.'
foreach ($claimId in $expectedClaimIds) {
    Assert-True (@($patch.links | Where-Object { $_.claimId -eq $claimId }).Count -eq 1) `
        "Claim $claimId must have exactly one Evidence link."
}

Assert-True ($routes.schemaVersion -eq '3.0') 'ABTest route schema must be 3.0.'
Assert-True ($routes.targetContentVersion -eq '2026-08-04.2') `
    'ABTest route target version is invalid.'
Assert-True (@($routes.publishRoutes).Count -eq 4) 'Expected four ABTest public routes.'
Assert-True ((@($routes.publishRoutes.assetId) -join ',') -eq 'AB-01,AB-02,AB-03,AB-04') `
    'ABTest route asset ids are invalid.'

$serializedPublicText = @($patch, $routes) | ConvertTo-Json -Depth 100 -Compress
foreach ($deniedPattern in @(
    'TF-[0-9]+',
    'weekend_login_abtest',
    't_extend_activation_condition_rule',
    't_ab_test_group_condition_rule',
    'galasports',
    'd11_server_dev',
    '[0-9a-f]{7,40}'
)) {
    Assert-True ($serializedPublicText -notmatch $deniedPattern) `
        "Public ABTest content contains denied pattern: $deniedPattern"
}

if ([string]$basePortfolio.contentVersion -eq [string]$patch.targetContentVersion) {
    Assert-True (@($basePortfolio.projects).Count -eq 6 -and
            @($basePortfolio.cases).Count -eq 52 -and
            @($basePortfolio.claims).Count -eq 83 -and
            @($basePortfolio.evidence).Count -eq 63 -and
            @($basePortfolio.claimEvidenceLinks).Count -eq 83 -and
            @($basePortfolio.timelineEvents).Count -eq 12 -and
            @($basePortfolio.questionPresets).Count -eq 19) `
        'Published ABTest runtime counts are invalid.'
    $publishedRiskClaim = @($basePortfolio.claims | Where-Object {
            $_.id -eq 'claim-abtest-validation-risk-control'
        })
    Assert-True ($publishedRiskClaim.Count -eq 1 -and
            $publishedRiskClaim[0].verificationStatus -eq 'VERIFIED') `
        'Published ABTest runtime violates the Claim verification contract.'
    Write-Output 'ABTest public patch and published runtime contract passed.'
    exit 0
}
Assert-True ([string]$basePortfolio.contentVersion -eq [string]$patch.baseContentVersion) `
    'ABTest test runtime must be either the incremental base or published target.'

function New-SyntheticInventory {
    $assets = @()
    foreach ($prefixAndCount in @(
        @('L', 7, 'MAINLINE'),
        @('T', 19, 'TASK'),
        @('A', 25, 'INCIDENT'),
        @('K', 17, 'KNOWLEDGE_ASSET')
    )) {
        for ($index = 1; $index -le [int]$prefixAndCount[1]; $index++) {
            $id = '{0}-{1:d2}' -f $prefixAndCount[0], $index
            $baseline = @($baselineLedger.assets | Where-Object assetId -eq $id)[0]
            $excluded = $baseline.routeDecision -eq 'EXCLUDED'
            $achievementStatus = 'DELIVERED'
            $contributionType = 'PRIMARY'
            if (-not $excluded -and @($baseline.caseSlugs).Count -gt 0) {
                $publicCase = @($basePortfolio.cases | Where-Object {
                        $_.slug -eq $baseline.caseSlugs[0]
                    })[0]
                $achievementStatus = switch ([string]$publicCase.achievementStatus) {
                    'PROTOTYPE' { 'VALIDATED_PROTOTYPE' }
                    'LEARNING' { 'LEARNING_ONLY' }
                    default { [string]$publicCase.achievementStatus }
                }
                $contributionType = [string]$publicCase.contributionType
            }
            elseif (-not $excluded -and @($baseline.projectSlugs).Count -gt 0) {
                $publicProject = @($basePortfolio.projects | Where-Object {
                        $_.slug -eq $baseline.projectSlugs[0]
                    })[0]
                $achievementStatus = switch ([string]$publicProject.status) {
                    'PROTOTYPE' { 'VALIDATED_PROTOTYPE' }
                    'IN_PROGRESS' { 'INVESTIGATED' }
                    default { [string]$publicProject.status }
                }
                $contributionType = [string]$publicProject.contributionType
            }
            $assets += [ordered]@{
                id = $id
                contentType = [string]$prefixAndCount[2]
                title = "Synthetic private fixture $id"
                achievementStatus = if ($excluded) { 'LEARNING_ONLY' } else { $achievementStatus }
                contributionType = if ($excluded) { 'OBSERVED_LEARNING' } else { $contributionType }
                publicPriority = if ($excluded) { 'EXCLUDE' } else { 'P1' }
                evidenceStatus = if ($excluded) { 'PARTIALLY_VERIFIED' } else { 'VERIFIED' }
                reviewState = if ($excluded) { 'EXCLUDE' } else { 'PUBLIC_REVIEW_REQUIRED' }
                summary = 'Synthetic private fixture summary.'
            }
        }
    }
    foreach ($abAsset in @(
        @('AB-01', 'MAINLINE', 'P0', 'VERIFIED'),
        @('AB-02', 'TASK', 'P0', 'VERIFIED'),
        @('AB-03', 'TASK', 'P0', 'VERIFIED'),
        @('AB-04', 'TASK', 'P1', 'PARTIALLY_VERIFIED')
    )) {
        $assets += [ordered]@{
            id = [string]$abAsset[0]
            contentType = [string]$abAsset[1]
            title = "Synthetic private fixture $($abAsset[0])"
            achievementStatus = 'DELIVERED'
            contributionType = 'PRIMARY'
            publicPriority = [string]$abAsset[2]
            evidenceStatus = [string]$abAsset[3]
            reviewState = 'PUBLIC_REVIEW_REQUIRED'
            summary = 'Synthetic private fixture summary.'
        }
    }
    $path = Join-Path ([IO.Path]::GetTempPath()) `
        ('portfolio-abtest-inventory-' + [guid]::NewGuid().ToString('N') + '.json')
    $document = [ordered]@{
        inventoryVersion = 'test-fixture-abtest-1'
        reviewState = 'PRIVATE_CANDIDATE'
        counts = [ordered]@{
            MAINLINE = 8; TASK = 22; INCIDENT = 25; KNOWLEDGE_ASSET = 17; TOTAL = 72
        }
        assets = $assets
    }
    $json = $document | ConvertTo-Json -Depth 20
    [IO.File]::WriteAllText(
        $path,
        $json + [Environment]::NewLine,
        (New-Object Text.UTF8Encoding($false))
    )
    $script:temporaryPaths += $path
    return $path
}

try {
    $inventoryPath = New-SyntheticInventory
    $workspace = Join-Path ([IO.Path]::GetTempPath()) `
        ('portfolio-abtest-test-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $workspace | Out-Null
    $temporaryPaths += $workspace
    $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $governancePath `
        -Command prepare-candidate -Workspace $workspace `
        -RuntimeBundle $runtimeBundlePath -PatchManifest $patchPath `
        -RouteManifest $routesPath -AssetInventory $inventoryPath `
        -TargetVersion '2026-08-04.2' 2>&1
    $prepareExit = $LASTEXITCODE
    $prepareResult = $output | Select-Object -Last 1 | ConvertFrom-Json
    Assert-True ($prepareExit -eq 0 -and $prepareResult.status -eq 'PASS') `
        "ABTest incremental prepare must pass (exit=$prepareExit, code=$($prepareResult.blockingFindings[0].code), message=$($prepareResult.blockingFindings[0].message))."

    $package = Join-Path $workspace 'prepared-candidates\2026-08-04.2'
    $candidate = Join-Path $package 'candidate'
    $candidatePortfolio = Get-Content (Join-Path $candidate 'portfolio.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-True ($candidatePortfolio.contentVersion -eq '2026-08-04.2') `
        'ABTest candidate content version is invalid.'
    Assert-True (@($candidatePortfolio.projects).Count -eq 6) `
        'ABTest candidate must contain 6 Projects.'
    Assert-True (@($candidatePortfolio.cases).Count -eq 52) `
        'ABTest candidate must contain 52 Cases.'
    Assert-True (@($candidatePortfolio.collections).Count -eq 3) `
        'ABTest candidate must preserve 3 Collections.'
    Assert-True (@($candidatePortfolio.claims).Count -eq 83) `
        'ABTest candidate must contain 83 Claims.'
    Assert-True (@($candidatePortfolio.evidence).Count -eq 63) `
        'ABTest candidate must contain 63 Evidence summaries.'
    Assert-True (@($candidatePortfolio.claimEvidenceLinks).Count -eq 83) `
        'ABTest candidate must contain 83 Claim-Evidence links.'
    Assert-True (@($candidatePortfolio.timelineEvents).Count -eq 12) `
        'ABTest candidate must contain 12 timeline events.'
    Assert-True (@($candidatePortfolio.questionPresets).Count -eq 19) `
        'ABTest candidate must contain 19 presets.'

    $candidateLedger = Get-Content `
        (Join-Path $package 'asset-publication-decisions.json') -Raw -Encoding UTF8 |
        ConvertFrom-Json
    Assert-True (@($candidateLedger.assets).Count -eq 72) `
        'ABTest candidate ledger must contain 72 assets.'
    Assert-True ((@($candidateLedger.assets | Where-Object {
                    $_.assetId -like 'AB-*' -and
                    $_.routeDecision -eq 'PUBLISH_CANDIDATE' -and
                    $_.contributionType -eq 'PRIMARY'
                })).Count -eq 4) `
        'All four ABTest assets must publish as PRIMARY contributions.'

    $validationOutput = & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File $governancePath -Command validate -Workspace $workspace `
        -Candidate $candidate `
        -DecisionLedger (Join-Path $package 'asset-publication-decisions.json') 2>&1
    $validationExit = $LASTEXITCODE
    $validationResult = $validationOutput | Select-Object -Last 1 | ConvertFrom-Json
    Assert-True ($validationExit -eq 0 -and $validationResult.status -eq 'PASS') `
        "ABTest candidate validation must pass (exit=$validationExit, code=$($validationResult.blockingFindings[0].code), message=$($validationResult.blockingFindings[0].message))."

    Write-Output 'ABTest public patch and incremental candidate contract passed.'
}
finally {
    foreach ($path in $temporaryPaths) {
        if (Test-Path -LiteralPath $path) {
            Remove-Item -LiteralPath $path -Recurse -Force
        }
    }
}
