param()
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$patchPath = Join-Path $root 'governance\portfolio-governance\candidates\preset-contract-closure-public-patch.json'
$workspaces = @()

function Assert-True([bool]$Value, [string]$Message) {
    if (-not $Value) { throw $Message }
}
if (-not (Test-Path -LiteralPath $patchPath -PathType Leaf)) {
    throw 'Preset contract closure patch is required.'
}
$patch = Get-Content -LiteralPath $patchPath -Raw -Encoding UTF8 | ConvertFrom-Json

Assert-True ([string]$patch.baseContentVersion -eq '2026-08-04.2') 'Closure patch base version mismatch.'
Assert-True ([string]$patch.targetContentVersion -eq '2026-08-05.1') 'Closure patch target version mismatch.'
Assert-True (@($patch.claims).Count -eq 5) 'Closure patch must add exactly five Claims.'
Assert-True (@($patch.links).Count -eq 5) 'Closure patch must add exactly five links.'
Assert-True (@($patch.projectUpdates).Count -eq 1) 'Closure patch must contain exactly one Project update.'
Assert-True (@($patch.projectUpdates[0].addClaimIds).Count -eq 5) 'The closure Project update must add five Claim IDs.'
Assert-True (@($patch.projects).Count -eq 0 -and @($patch.cases).Count -eq 0 -and
    @($patch.timelineEvents).Count -eq 0 -and @($patch.evidence).Count -eq 0 -and
    @($patch.presets).Count -eq 0 -and @($patch.caseUpdates).Count -eq 0) 'Closure patch must not add other collections.'
$expectedClaimIds = @(
    'claim-abtest-project-background','claim-abtest-project-responsibility',
    'claim-abtest-project-stratification-bucketing','claim-abtest-project-stable-assignment',
    'claim-abtest-project-validation-rollback'
)
$actualClaimIds = @($patch.claims | ForEach-Object { [string]$_.id })
Assert-True ((Compare-Object $actualClaimIds $expectedClaimIds).Count -eq 0) 'Closure Claim ID set mismatch.'
foreach ($claim in @($patch.claims)) {
    Assert-True ([string]$claim.subjectType -eq 'PROJECT' -and
        [string]$claim.subjectId -eq 'weekend-login-abtest-project') 'Every closure Claim must belong to the ABTest Project.'
    Assert-True ([string]$claim.verificationBasis -eq 'EVIDENCE_SUPPORTED' -and
        [string]$claim.verificationStatus -eq 'VERIFIED') 'Every closure Claim must be Evidence-supported and VERIFIED.'
    Assert-True ([string]$claim.achievementStatus -eq 'DELIVERED' -and
        [string]$claim.contributionType -eq 'PRIMARY' -and
        [string]$claim.materiality -eq 'KEY') 'Every closure Claim must be a delivered PRIMARY KEY statement.'
    Assert-True ([string]::IsNullOrWhiteSpace([string]$claim.statement) -eq $false) 'Every closure Claim must state public facts only.'
}
foreach ($link in @($patch.links)) {
    Assert-True ([string]$link.supportType -eq 'DIRECT' -and
        [string]$link.reviewStatus -eq 'APPROVED') 'Every closure link must be DIRECT and APPROVED.'
    Assert-True ($expectedClaimIds -contains [string]$link.claimId) 'A closure link references an unknown Claim.'
}
Assert-True ((Compare-Object @($patch.projectUpdates[0].addClaimIds) $expectedClaimIds).Count -eq 0) 'The closure Project update must add the exact new Claim IDs.'

$pipelineSkipped = $true
if (-not [string]::IsNullOrWhiteSpace($env:PORTFOLIO_TEST_ASSET_INVENTORY) -and
        (Test-Path -LiteralPath (Join-Path $root 'backend\target\portfolio-agent.jar'))) {
    $pipelineSkipped = $false
    $workspace = Join-Path ([IO.Path]::GetTempPath()) ('portfolio-closure-test-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $workspace | Out-Null
    $workspaces += $workspace
    $governance = Join-Path $root 'governance\portfolio-governance\scripts\portfolio-governance.ps1'
    $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $governance `
        -Command prepare-candidate -Workspace $workspace `
        -RuntimeBundle (Join-Path $root 'backend\src\main\resources\public-data\bundle') `
        -PatchManifest $patchPath `
        -RouteManifest (Join-Path $root 'governance\portfolio-governance\candidates\preset-contract-closure-public-routes.json') `
        -AssetInventory $env:PORTFOLIO_TEST_ASSET_INVENTORY `
        -TargetVersion '2026-08-05.1' 2>&1
    $exitCode = $LASTEXITCODE
    $result = $output | Select-Object -Last 1 | ConvertFrom-Json
    Assert-True ($exitCode -eq 0 -and $result.status -eq 'PASS') `
        "Preset contract closure prepare should pass (code=$($result.blockingFindings[0].code), message=$($result.blockingFindings[0].message))."
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$result.presetContractSetHash)) `
        'Preset contract closure prepare must output a non-empty set hash.'
    $candidate = Join-Path $workspace 'prepared-candidates\2026-08-05.1\candidate'
    $portfolio = Get-Content (Join-Path $candidate 'portfolio.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-True ([string]$portfolio.contentVersion -eq '2026-08-05.1') 'Prepared candidate version mismatch.'
    $active = @($portfolio.questionPresets | Where-Object contractStatus -eq 'ACTIVE')
    $draft = @($portfolio.questionPresets | Where-Object contractStatus -eq 'DRAFT')
    Assert-True ($active.Count -eq 18) 'Prepared candidate must contain exactly 18 ACTIVE contracts.'
    Assert-True ($draft.Count -eq 1 -and [string]$draft[0].id -eq 'question-public-assets-overview') `
        'Prepared candidate must keep exactly one DRAFT preset.'
    Assert-True (@($portfolio.claims).Count -eq 88 -and @($portfolio.claimEvidenceLinks).Count -eq 88 -and
        @($portfolio.projects).Count -eq 6 -and @($portfolio.cases).Count -eq 52 -and
        @($portfolio.evidence).Count -eq 63 -and @($portfolio.timelineEvents).Count -eq 12 -and
        @($portfolio.questionPresets).Count -eq 19 -and @($portfolio.collections).Count -eq 3) `
        'Prepared candidate counts differ from the reviewed target.'
    Assert-True (Test-Path -LiteralPath (Join-Path $candidate 'rag-documents.jsonl')) `
        'Prepared candidate must contain canonical RAG documents.'
}

Write-Output ("Preset contract closure candidate tests passed" +
    $(if ($pipelineSkipped) { ' (pipeline skipped: PORTFOLIO_TEST_ASSET_INVENTORY or packaged jar missing).' } else { '.' }))
