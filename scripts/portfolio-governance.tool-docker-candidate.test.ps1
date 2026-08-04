param(
    [string]$PatchPathOverride,
    [string]$RoutesPathOverride
)

$ErrorActionPreference = 'Stop'

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Assert-ExactSet {
    param([object[]]$Actual, [object[]]$Expected, [string]$Message)
    Assert-True ((Compare-Object @($Actual) @($Expected) -CaseSensitive).Count -eq 0) $Message
}

function Get-ObjectHash {
    param($Value)
    $bytes = [Text.Encoding]::UTF8.GetBytes(($Value | ConvertTo-Json -Compress -Depth 100))
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString('x2') }) -join '' }
    finally { $sha.Dispose() }
}

function Assert-ExactHash {
    param($Value, [string]$ExpectedHash, [string]$Message)
    Assert-True ((Get-ObjectHash $Value) -ceq $ExpectedHash) $Message
}

function Assert-Link {
    param([object[]]$Links, [string]$Id, [string]$ClaimId, [string]$EvidenceId, [string]$ExpectedScope)
    $link = @($Links | Where-Object { $_.id -ceq $Id })
    Assert-True ($link.Count -eq 1 -and $link[0].claimId -ceq $ClaimId -and $link[0].evidenceId -ceq $EvidenceId -and
        $link[0].scope -ceq $ExpectedScope -and $link[0].supportType -ceq 'DIRECT' -and $link[0].reviewStatus -ceq 'APPROVED') "Link contract mismatch for $Id."
}

$root = Split-Path -Parent $PSScriptRoot
$patchPath = if ($PatchPathOverride) { $PatchPathOverride } else {
    Join-Path $root 'governance\portfolio-governance\candidates\tool-docker-public-patch.json'
}
$routesPath = if ($RoutesPathOverride) { $RoutesPathOverride } else {
    Join-Path $root 'governance\portfolio-governance\candidates\tool-docker-public-routes.json'
}
$benchmarkPath = Join-Path $root 'governance\portfolio-governance\benchmark\tool-docker-benchmarks.v1.json'
$governancePath = Join-Path $root 'governance\portfolio-governance\scripts\portfolio-governance.ps1'

Assert-True (Test-Path -LiteralPath $patchPath -PathType Leaf) 'tool_docker patch is required.'
Assert-True (Test-Path -LiteralPath $routesPath -PathType Leaf) 'tool_docker routes are required.'

$governanceSource = Get-Content -LiteralPath $governancePath -Raw -Encoding UTF8
Assert-True ($governanceSource -match "'4\.0\|2026-08-04\.1'\s*\{\s*'tool-docker-benchmarks\.v1\.json';\s*break\s*\}") `
    'Tool Docker benchmark selection must be explicit for schema 4 / 2026-08-04.1.'
Assert-True (Test-Path -LiteralPath $benchmarkPath -PathType Leaf) 'Tool Docker benchmark fixture is required.'
$benchmark = Get-Content -LiteralPath $benchmarkPath -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-True ($benchmark.schemaVersion -ceq '1.0') 'Tool Docker benchmark schema version mismatch.'
$waveTwoBenchmark = Get-Content -LiteralPath (Join-Path $root 'governance\portfolio-governance\benchmark\wave-2-benchmarks.v1.json') -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-True (@($benchmark.cases).Count -eq 95) 'Tool Docker benchmark must retain 80 Wave 2 cases and add fifteen Tool Docker cases.'
Assert-ExactHash @($benchmark.cases[0..79]) (Get-ObjectHash @($waveTwoBenchmark.cases)) 'Wave 2 benchmark cases must remain byte-for-byte equivalent as JSON objects.'
$toolDockerBenchmarkCases = @($benchmark.cases | Where-Object { $_.questionPresetId -like 'question-tool-docker-*' })
Assert-True ($toolDockerBenchmarkCases.Count -eq 15 -and @($toolDockerBenchmarkCases | Where-Object { $_.severity -cne 'ERROR' }).Count -eq 0) 'Tool Docker benchmark cases must be fifteen ERROR cases.'
foreach ($presetId in @('question-tool-docker-overview', 'question-tool-docker-runtime-routing', 'question-tool-docker-restart-and-time')) {
    $caseTypes = @($toolDockerBenchmarkCases | Where-Object { $_.questionPresetId -ceq $presetId } | ForEach-Object caseType)
    Assert-ExactSet -Actual $caseTypes -Expected @('SUPPORTED_QUESTION', 'ALIAS', 'BOUNDARY', 'CLAIM_EVIDENCE', 'SAFETY') -Message "Tool Docker benchmark coverage mismatch for $presetId."
}
$expectedClaimEvidenceCoverage = [ordered]@{
    'question-tool-docker-overview' = @('claim-tool-docker-responsibility', 'evidence-tool-docker-cross-repository-review')
    'question-tool-docker-runtime-routing' = @('claim-tool-docker-exact-routing', 'evidence-tool-docker-tool-implementation-tests')
    'question-tool-docker-restart-and-time' = @('claim-tool-docker-restart-state-machine', 'evidence-tool-docker-tool-implementation-tests')
}
foreach ($presetId in @($expectedClaimEvidenceCoverage.Keys)) {
    $claimEvidenceCase = @($toolDockerBenchmarkCases | Where-Object { $_.questionPresetId -ceq $presetId -and $_.caseType -ceq 'CLAIM_EVIDENCE' })
    Assert-True ($claimEvidenceCase.Count -eq 1) "Tool Docker claim/evidence benchmark coverage mismatch for $presetId."
    Assert-ExactSet -Actual @($claimEvidenceCase[0].requiredClaimIds) -Expected @($expectedClaimEvidenceCoverage[$presetId][0]) -Message "Tool Docker required Claim mismatch for $presetId."
    Assert-ExactSet -Actual @($claimEvidenceCase[0].requiredEvidenceIds) -Expected @($expectedClaimEvidenceCoverage[$presetId][1]) -Message "Tool Docker required Evidence mismatch for $presetId."
}

$patch = Get-Content -Raw -Encoding UTF8 $patchPath | ConvertFrom-Json
$routes = Get-Content -Raw -Encoding UTF8 $routesPath | ConvertFrom-Json

$toolDockerProjectId = 'tool-docker-transformation-project'
$toolDockerRoutingCaseId = 'case-tool-docker-runtime-routing'
$toolDockerRestartCaseIds = @('case-tool-docker-sequential-restart', 'case-tool-docker-virtual-time')
$overviewPreset = @($patch.presets | Where-Object { $_.id -ceq 'question-tool-docker-overview' })
$routingPreset = @($patch.presets | Where-Object { $_.id -ceq 'question-tool-docker-runtime-routing' })
$restartPreset = @($patch.presets | Where-Object { $_.id -ceq 'question-tool-docker-restart-and-time' })
Assert-True ($overviewPreset.Count -eq 1 -and @($overviewPreset[0].projectIds).Count -eq 1 -and $overviewPreset[0].projectIds[0] -ceq $toolDockerProjectId -and @($overviewPreset[0].caseIds).Count -eq 0) 'Tool Docker overview retrieval boundary mismatch.'
Assert-True ($routingPreset.Count -eq 1 -and @($routingPreset[0].projectIds).Count -eq 1 -and $routingPreset[0].projectIds[0] -ceq $toolDockerProjectId) 'Tool Docker routing project boundary mismatch.'
Assert-ExactSet -Actual @($routingPreset[0].caseIds) -Expected @($toolDockerRoutingCaseId) -Message 'Tool Docker routing Case boundary mismatch.'
Assert-True ($restartPreset.Count -eq 1 -and @($restartPreset[0].projectIds).Count -eq 1 -and $restartPreset[0].projectIds[0] -ceq $toolDockerProjectId) 'Tool Docker restart project boundary mismatch.'
Assert-ExactSet -Actual @($restartPreset[0].caseIds) -Expected $toolDockerRestartCaseIds -Message 'Tool Docker restart-and-time Case boundary mismatch.'
$runtimePortfolio = Get-Content -LiteralPath (Join-Path $root 'backend\src\main\resources\public-data\bundle\portfolio.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$learningManualCase = @($runtimePortfolio.cases | Where-Object { $_.id -ceq 'case-public-k-06' })
Assert-True ($learningManualCase.Count -eq 1 -and $learningManualCase[0].slug -ceq 'k-06-knowledge') 'Containerization learning-manual retrieval boundary must remain k-06-knowledge.'
Assert-True (@($learningManualCase | Where-Object { $_.slug -like 'tool-docker-*' }).Count -eq 0) 'Learning-manual and Tool Docker retrieval boundaries must not swap.'

Assert-True ($patch.schemaVersion -ceq '3.0') 'Incremental patch schema must be 3.0.'
Assert-True ($patch.baseContentVersion -ceq '2026-07-29.1') 'Base version mismatch.'
Assert-True ($patch.targetContentVersion -ceq '2026-08-04.1') 'Target version mismatch.'
Assert-True (@($patch.projects).Count -eq 1) 'Expected one Project.'
Assert-True (@($patch.cases).Count -eq 3) 'Expected three Cases.'
Assert-True (@($patch.timelineEvents).Count -eq 1) 'Expected one TimelineEvent.'
Assert-True (@($patch.claims).Count -eq 9) 'Expected nine Claims.'
Assert-True (@($patch.evidence).Count -eq 3) 'Expected three Evidence summaries.'
Assert-True (@($patch.links).Count -eq 9) 'Expected nine direct links.'
Assert-True (@($patch.presets).Count -eq 3) 'Expected three presets.'
Assert-True (@($patch.projectUpdates).Count -eq 0 -and @($patch.caseUpdates).Count -eq 0) 'Updates must be empty.'

$project = $patch.projects[0]
$featuredCaseIds = @('case-tool-docker-runtime-routing', 'case-tool-docker-sequential-restart', 'case-tool-docker-virtual-time')
Assert-True ($project.id -ceq 'tool-docker-transformation-project' -and $project.code -ceq 'P-06' -and
    $project.slug -ceq 'tool-docker-transformation' -and $project.status -ceq 'DELIVERED' -and
    $project.contributionType -ceq 'COLLABORATIVE' -and $project.careerTrack -ceq 'JAVA_BACKEND' -and
    $project.projectNature -ceq 'TOOL' -and $project.displayTier -ceq 'PRIMARY') 'Project identity mismatch.'
Assert-ExactSet -Actual @($project.featuredCaseIds) -Expected $featuredCaseIds -Message 'Project featured Case IDs mismatch.'
Assert-ExactHash @($patch.projects) 'e014c9ecf308a03127593e5a09e4ac2ca85940db594cdf28ed1bd97bdb92e830' `
    'Project content contract mismatch.'

$expectedCases = [ordered]@{
    'case-tool-docker-runtime-routing' = 'CASE-50'
    'case-tool-docker-sequential-restart' = 'CASE-51'
    'case-tool-docker-virtual-time' = 'CASE-52'
}
Assert-ExactSet -Actual @($patch.cases.id) -Expected @($expectedCases.Keys) -Message 'Case ID set mismatch.'
foreach ($case in @($patch.cases)) {
    Assert-True ($case.code -ceq $expectedCases[$case.id] -and $case.type -ceq 'FEATURE' -and
        $case.achievementStatus -ceq 'IMPLEMENTED_TESTED' -and $case.contributionType -ceq 'COLLABORATIVE' -and
        $case.projectId -ceq $project.id -and @($case.collectionIds).Count -eq 0) "Case contract mismatch for $($case.id)."
}
Assert-ExactHash @($patch.cases) '0520fc3a306006fa4a4b6a0372479ddf7460f107150f443b7a6bdfd8e53c5681' `
    'Case content contract mismatch.'

$expectedClaimContract = [ordered]@{
    'claim-tool-docker-background' = @('UNKNOWN', 'COLLABORATIVE', 'EVIDENCE_SUPPORTED', 'VERIFIED')
    'claim-tool-docker-responsibility' = @('DELIVERED', 'COLLABORATIVE', 'EVIDENCE_SUPPORTED', 'VERIFIED')
    'claim-tool-docker-dual-runtime' = @('IMPLEMENTED_TESTED', 'COLLABORATIVE', 'EVIDENCE_SUPPORTED', 'VERIFIED')
    'claim-tool-docker-exact-routing' = @('IMPLEMENTED_TESTED', 'COLLABORATIVE', 'EVIDENCE_SUPPORTED', 'VERIFIED')
    'claim-tool-docker-lifecycle' = @('IMPLEMENTED_TESTED', 'COLLABORATIVE', 'EVIDENCE_SUPPORTED', 'VERIFIED')
    'claim-tool-docker-restart-state-machine' = @('IMPLEMENTED_TESTED', 'COLLABORATIVE', 'EVIDENCE_SUPPORTED', 'VERIFIED')
    'claim-tool-docker-virtual-time' = @('IMPLEMENTED_TESTED', 'COLLABORATIVE', 'EVIDENCE_SUPPORTED', 'VERIFIED')
    'claim-tool-docker-verification' = @('IMPLEMENTED_TESTED', 'COLLABORATIVE', 'EVIDENCE_SUPPORTED', 'VERIFIED')
    'claim-tool-docker-boundary' = @('INVESTIGATED', 'COLLABORATIVE', 'SELF_DECLARED', 'PARTIALLY_VERIFIED')
}
Assert-ExactSet -Actual @($patch.claims.id) -Expected @($expectedClaimContract.Keys) -Message 'Claim ID set mismatch.'
foreach ($claim in @($patch.claims)) {
    $contract = $expectedClaimContract[$claim.id]
    Assert-True ($claim.achievementStatus -ceq $contract[0] -and $claim.contributionType -ceq $contract[1] -and
        $claim.verificationBasis -ceq $contract[2] -and $claim.verificationStatus -ceq $contract[3]) "Claim status contract mismatch for $($claim.id)."
    $links = @($patch.links | Where-Object { $_.claimId -ceq $claim.id })
    Assert-True ($links.Count -eq 1 -and $links[0].supportType -ceq 'DIRECT' -and $links[0].reviewStatus -ceq 'APPROVED' -and
        @($patch.evidence | Where-Object { $_.id -ceq $links[0].evidenceId }).Count -eq 1) "Claim link contract mismatch for $($claim.id)."
}
Assert-ExactHash @($patch.claims) 'cec0752e89ccebcf607554dc4bc3ca6d6ef54070ee8925ec261013410bdc09fb' `
    'Claim content contract mismatch.'

$expectedEvidenceTypes = [ordered]@{
    'evidence-tool-docker-tool-implementation-tests' = 'COLLECTION'
    'evidence-tool-docker-deployment-contract' = 'CODE'
    'evidence-tool-docker-cross-repository-review' = 'COLLECTION'
}
Assert-ExactSet -Actual @($patch.evidence.id) -Expected @($expectedEvidenceTypes.Keys) -Message 'Evidence ID set mismatch.'
foreach ($evidence in @($patch.evidence)) {
    Assert-True ($evidence.type -ceq $expectedEvidenceTypes[$evidence.id] -and $evidence.publicStatus -ceq 'APPROVED' -and
        $evidence.rawContentPublic -eq $false) "Evidence publication contract mismatch for $($evidence.id)."
}
Assert-ExactHash @($patch.evidence) '2d6ef198addb8a35e46089a303fcc72cd1afe07d7ce7f60c68d504c7d7a3d2fb' `
    'Evidence content contract mismatch.'

$expectedPresetIds = @('question-tool-docker-overview', 'question-tool-docker-runtime-routing', 'question-tool-docker-restart-and-time')
Assert-ExactSet -Actual @($patch.presets.id) -Expected $expectedPresetIds -Message 'QuestionPreset ID set mismatch.'
Assert-ExactHash @($patch.presets) '787d335fa98a8213341316cce18c32a86e830c22098d68a8c644025ba6953c01' `
    'QuestionPreset content contract mismatch.'

Assert-True ($routes.schemaVersion -ceq '3.0' -and $routes.targetContentVersion -ceq '2026-08-04.1') 'Route manifest version mismatch.'
$expectedRoutes = @(
    @{ assetId = 'TD-01'; finalRoute = 'PROJECT'; projectSlugs = @('tool-docker-transformation'); caseSlugs = @(); evidenceIds = @('evidence-tool-docker-cross-repository-review') },
    @{ assetId = 'TD-02'; finalRoute = 'CASE'; projectSlugs = @('tool-docker-transformation'); caseSlugs = @('tool-docker-runtime-routing'); evidenceIds = @('evidence-tool-docker-tool-implementation-tests') },
    @{ assetId = 'TD-03'; finalRoute = 'CASE'; projectSlugs = @('tool-docker-transformation'); caseSlugs = @('tool-docker-sequential-restart'); evidenceIds = @('evidence-tool-docker-tool-implementation-tests') },
    @{ assetId = 'TD-04'; finalRoute = 'CASE'; projectSlugs = @('tool-docker-transformation'); caseSlugs = @('tool-docker-virtual-time'); evidenceIds = @('evidence-tool-docker-deployment-contract') }
)
Assert-True (@($routes.publishRoutes).Count -eq 4) 'Expected exactly four publish routes.'
foreach ($expected in $expectedRoutes) {
    $route = @($routes.publishRoutes | Where-Object { $_.assetId -ceq $expected.assetId })
    Assert-True ($route.Count -eq 1 -and $route[0].finalRoute -ceq $expected.finalRoute) "Route identity mismatch for $($expected.assetId)."
    Assert-ExactSet -Actual @($route[0].projectSlugs) -Expected $expected.projectSlugs -Message "Route Project mapping mismatch for $($expected.assetId)."
    Assert-ExactSet -Actual @($route[0].caseSlugs) -Expected $expected.caseSlugs -Message "Route Case mapping mismatch for $($expected.assetId)."
    Assert-ExactSet -Actual @($route[0].evidenceIds) -Expected $expected.evidenceIds -Message "Route Evidence mapping mismatch for $($expected.assetId)."
}

Assert-ExactHash @($patch.links) '69b5c30792881d0b9c0f23510ce0fb032d395859904dfa267224f389a949fe66' `
    'Link content contract mismatch.'

$serialized = (Get-Content -Raw -Encoding UTF8 $patchPath) + (Get-Content -Raw -Encoding UTF8 $routesPath)
$forbiddenTextPattern = '(?im)(?:[a-z]:\\|\\\\|/home/|/var/|https?://|\b(?:host|hostname|env|environment(?:[-_ ]?id)?|credential|password|secret|token|readiness)\b|自动回滚|生产级鉴权|(?:生产|production)(?:级|\s)*(?:验收|acceptance)|(?:量化|百分比|效率|收益)|内部(?:主机|地址|环境(?:ID|标识|代号|编号)?)|环境(?:ID|标识|代号|编号)|原始(?:代码|日志|截图)|raw\s*(?:code|log|screenshot))'
Assert-True ($serialized -notmatch $forbiddenTextPattern) 'Sensitive, raw-material, production, or quantitative wording detected.'

# Compiler integration: legacy inventory coverage plus the four reviewed Tool Docker assets.
$governance = Join-Path $root 'governance\portfolio-governance\scripts\portfolio-governance.ps1'
$workspace = Join-Path ([IO.Path]::GetTempPath()) ('tool-docker-candidate-' + [guid]::NewGuid().ToString('N'))
$inventoryPath = Join-Path ([IO.Path]::GetTempPath()) ('tool-docker-inventory-' + [guid]::NewGuid().ToString('N') + '.json')
try {
    New-Item -ItemType Directory -Path $workspace | Out-Null
    $ids = @(); foreach ($group in @(@('L',7),@('T',19),@('A',25),@('K',17))) { for ($index=1; $index -le $group[1]; $index++) { $ids += ('{0}-{1:d2}' -f $group[0], $index) } }; $ids += @('TD-01','TD-02','TD-03','TD-04')
    $assets = foreach ($id in $ids) {
        $status = if ($id -eq 'TD-01') {'DELIVERED'} elseif ($id -like 'TD-*') {'IMPLEMENTED_TESTED'} else {'INVESTIGATED'}
        [ordered]@{ id=$id; contentType='TASK'; title=('reviewed asset ' + $id); achievementStatus=$status; contributionType='COLLABORATIVE'; publicPriority='P1'; evidenceStatus='VERIFIED'; reviewState='PUBLIC_REVIEW_REQUIRED'; summary='Reviewed private source summary.' }
    }
    [ordered]@{inventoryVersion='1.0';reviewState='REVIEWED';counts=[ordered]@{};assets=@($assets)} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $inventoryPath -Encoding UTF8
    $prepare = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $governance -Command prepare-candidate -Workspace $workspace -RuntimeBundle (Join-Path $root 'backend\src\main\resources\public-data\bundle') -PatchManifest $patchPath -RouteManifest $routesPath -AssetInventory $inventoryPath -TargetVersion '2026-08-04.1'
    $prepareExit = $LASTEXITCODE
    $prepareJson = $prepare | Select-Object -Last 1 | ConvertFrom-Json
    Assert-True ($prepareExit -eq 0 -and $prepareJson.status -eq 'PASS') 'Tool Docker schema-4 incremental prepare must pass.'
    $package = Join-Path $workspace 'prepared-candidates\2026-08-04.1'
    Assert-ExactSet -Actual @(Get-ChildItem -LiteralPath (Join-Path $package 'candidate') -File | ForEach-Object Name) -Expected @('portfolio.json','presentation.json') -Message 'Prepared candidate must contain exactly two canonical files.'
    $preparedPortfolio = Get-Content -Raw -Encoding UTF8 (Join-Path $package 'candidate\portfolio.json') | ConvertFrom-Json
    Assert-True (@($preparedPortfolio.projects).Count -eq 6 -and @($preparedPortfolio.cases).Count -eq 52 -and @($preparedPortfolio.claims).Count -eq 88 -and @($preparedPortfolio.evidence).Count -eq 62 -and @($preparedPortfolio.claimEvidenceLinks).Count -eq 88 -and @($preparedPortfolio.timelineEvents).Count -eq 12 -and @($preparedPortfolio.questionPresets).Count -eq 19 -and @($preparedPortfolio.collections).Count -eq 3) 'Prepared content counts mismatch.'
    $ledger = Get-Content -Raw -Encoding UTF8 (Join-Path $package 'asset-publication-decisions.json') | ConvertFrom-Json
    Assert-True (@($ledger.assets).Count -eq 72 -and @($ledger.assets | Where-Object routeDecision -eq 'PUBLISH_CANDIDATE').Count -eq 4) 'Incremental ledger coverage mismatch.'

    function Assert-PrepareMutationFails {
        param([scriptblock]$Mutate, [string]$Message)
        $patchBytes = [IO.File]::ReadAllBytes($patchPath); $routeBytes = [IO.File]::ReadAllBytes($routesPath)
        $mutationWorkspace = Join-Path ([IO.Path]::GetTempPath()) ('tool-docker-mutation-' + [guid]::NewGuid().ToString('N'))
        try {
            & $Mutate
            New-Item -ItemType Directory -Path $mutationWorkspace | Out-Null
            $mutationOutput = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $governance -Command prepare-candidate -Workspace $mutationWorkspace -RuntimeBundle (Join-Path $root 'backend\src\main\resources\public-data\bundle') -PatchManifest $patchPath -RouteManifest $routesPath -AssetInventory $inventoryPath -TargetVersion '2026-08-04.1'
            Assert-True ($LASTEXITCODE -ne 0) $Message
        }
        finally {
            [IO.File]::WriteAllBytes($patchPath, $patchBytes); [IO.File]::WriteAllBytes($routesPath, $routeBytes)
            if (Test-Path -LiteralPath $mutationWorkspace) { Remove-Item -LiteralPath $mutationWorkspace -Recurse -Force }
        }
    }
    Assert-PrepareMutationFails -Message 'Empty Project slug must fail incremental preparation.' -Mutate {
        $mutation = Get-Content -Raw -Encoding UTF8 $patchPath | ConvertFrom-Json; $mutation.projects[0].slug = ''
        $mutation | ConvertTo-Json -Depth 50 | Set-Content -LiteralPath $patchPath -Encoding UTF8
    }
    Assert-PrepareMutationFails -Message 'Duplicate Tool Docker publish route must fail incremental preparation.' -Mutate {
        $mutation = Get-Content -Raw -Encoding UTF8 $routesPath | ConvertFrom-Json; $mutation.publishRoutes[3].assetId = 'TD-03'
        $mutation | ConvertTo-Json -Depth 50 | Set-Content -LiteralPath $routesPath -Encoding UTF8
    }
}
finally {
    if (Test-Path -LiteralPath $workspace) { Remove-Item -LiteralPath $workspace -Recurse -Force }
    if (Test-Path -LiteralPath $inventoryPath) { Remove-Item -LiteralPath $inventoryPath -Force }
}

Write-Output 'tool_docker public patch contract passed.'
