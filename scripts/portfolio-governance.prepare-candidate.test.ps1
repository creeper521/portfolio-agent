param()
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$governance = Join-Path $root 'scripts\portfolio-governance.ps1'
$runtime = Join-Path $root 'backend\src\main\resources\public-data\bundle'
$patch = Join-Path $root 'governance\portfolio-governance\candidates\wave-1-public-patch.json'
$routes = Join-Path $root 'governance\portfolio-governance\candidates\wave-1-public-routes.json'
$inventorySource = $null
$workspaces = @()

function Assert-True([bool]$Value, [string]$Message) {
    if (-not $Value) { throw $Message }
}
$runtimeFiles = @(Get-ChildItem -LiteralPath $runtime -File |
    ForEach-Object Name | Sort-Object)
$runtimeManifest = Get-Content -LiteralPath (Join-Path $runtime 'manifest.json') `
    -Raw -Encoding UTF8 | ConvertFrom-Json
if (($runtimeFiles -join ',') -ne `
        'checksums.json,manifest.json,portfolio.json,presentation.json' -or
        [string]$runtimeManifest.contentVersion -ne '2026-07-23.1') {
    Write-Output ('SKIP: historical Wave 1 four-file runtime fixture is not current; ' +
        'prepare scenarios remain covered by portfolio-governance.test.ps1.')
    exit 0
}
function New-SyntheticInventory {
    $publishIds = @('L-01','T-01','T-02','T-03','T-04','T-05','T-06','T-17','K-01')
    $assets = @()
    foreach ($prefixAndCount in @(
        @('L', 7, 'MAINLINE'),
        @('T', 19, 'TASK'),
        @('A', 25, 'INCIDENT'),
        @('K', 17, 'KNOWLEDGE_ASSET')
    )) {
        for ($index = 1; $index -le [int]$prefixAndCount[1]; $index++) {
            $id = '{0}-{1:d2}' -f $prefixAndCount[0], $index
            $asset = [ordered]@{
                id = $id
                contentType = [string]$prefixAndCount[2]
                title = "Synthetic private fixture $id"
                achievementStatus = if ($id -eq 'K-01') { 'VALIDATED_PROTOTYPE' } else { 'DELIVERED' }
                contributionType = 'PRIMARY'
                publicPriority = 'P1'
                evidenceStatus = 'VERIFIED'
                reviewState = if ($publishIds -contains $id) { 'PUBLIC_REVIEW_REQUIRED' } else { 'HOLD' }
                summary = 'Synthetic private fixture summary.'
            }
            if ($id -eq 'A-22') {
                $asset.achievementStatus = 'LEARNING_ONLY'
                $asset.contributionType = 'OBSERVED_LEARNING'
                $asset.publicPriority = 'EXCLUDE'
                $asset.reviewState = 'EXCLUDE'
            }
            elseif ($id -eq 'K-16') {
                $asset.achievementStatus = 'DOCUMENTED_OUTPUT'
            }
            elseif ($id -eq 'A-25') {
                $asset.achievementStatus = 'INCOMPLETE'
                $asset.contributionType = 'UNRESOLVED'
                $asset.evidenceStatus = 'INSUFFICIENT'
            }
            elseif ($id -eq 'A-15') {
                $asset.achievementStatus = 'INVESTIGATED'
                $asset.contributionType = 'ASSISTED'
            }
            elseif ($id -eq 'T-19') {
                $asset.contributionType = 'COLLABORATIVE'
            }
            $assets += $asset
        }
    }
    $path = Join-Path ([IO.Path]::GetTempPath()) ('portfolio-w1-inventory-' + [guid]::NewGuid().ToString('N') + '.json')
    $document = [ordered]@{
        inventoryVersion = 'test-fixture-1'
        reviewState = 'PRIVATE_CANDIDATE'
        counts = [ordered]@{ MAINLINE=7; TASK=19; INCIDENT=25; KNOWLEDGE_ASSET=17; TOTAL=68 }
        assets = $assets
    }
    $json = $document | ConvertTo-Json -Depth 20
    [IO.File]::WriteAllText($path, $json + [Environment]::NewLine, (New-Object Text.UTF8Encoding($false)))
    $script:workspaces += $path
    return $path
}
function New-WaveOneRuntimeFixture {
    $fixture = Join-Path ([IO.Path]::GetTempPath()) ('portfolio-w1-runtime-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $fixture | Out-Null
    foreach ($name in @('checksums.json', 'manifest.json', 'portfolio.json', 'presentation.json')) {
        $processInfo = New-Object Diagnostics.ProcessStartInfo
        $processInfo.FileName = 'git'
        $processInfo.Arguments = "-C `"$root`" show c0a8823:backend/src/main/resources/public-data/bundle/$name"
        $processInfo.UseShellExecute = $false
        $processInfo.RedirectStandardOutput = $true
        $processInfo.RedirectStandardError = $true
        $process = [Diagnostics.Process]::Start($processInfo)
        $stream = [IO.File]::Open((Join-Path $fixture $name), [IO.FileMode]::CreateNew)
        try {
            $process.StandardOutput.BaseStream.CopyTo($stream)
        }
        finally {
            $stream.Dispose()
        }
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) {
            throw "Unable to materialize the archived Wave 1 runtime fixture: $($process.StandardError.ReadToEnd())"
        }
    }
    $script:workspaces += $fixture
    return $fixture
}
function Invoke-Prepare([string]$WorkspaceValue, [string]$RuntimeValue = $runtime,
        [string]$PatchValue = $patch, [string]$RoutesValue = $routes,
        [string]$InventoryValue = $inventorySource,
        [string]$FailureStage = 'NONE') {
    $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $governance `
        -Command prepare-candidate -Workspace $WorkspaceValue `
        -RuntimeBundle $RuntimeValue -PatchManifest $PatchValue `
        -RouteManifest $RoutesValue -AssetInventory $InventoryValue `
        -TargetVersion '2026-07-24.1' -PrepareFailureStage $FailureStage
    return [ordered]@{ ExitCode = $LASTEXITCODE; Json = ($output | Select-Object -Last 1 | ConvertFrom-Json) }
}
function New-FakeRepository {
    $fakeRoot = Join-Path ([IO.Path]::GetTempPath()) ('portfolio-w1-repo-' + [guid]::NewGuid().ToString('N'))
    $script:workspaces += $fakeRoot
    foreach ($relative in @(
        'backend\src\main\resources\public-data\bundle',
        'governance\portfolio-governance\scripts',
        'governance\portfolio-governance\benchmark',
        'governance\portfolio-governance\policies',
        'governance\portfolio-governance\schemas',
        'governance\portfolio-governance\candidates',
        'scripts'
    )) {
        New-Item -ItemType Directory -Path (Join-Path $fakeRoot $relative) -Force | Out-Null
    }
    Copy-Item -LiteralPath (Join-Path $root 'governance\portfolio-governance\scripts\portfolio-governance.ps1') `
        -Destination (Join-Path $fakeRoot 'governance\portfolio-governance\scripts\portfolio-governance.ps1')
    Copy-Item -LiteralPath (Join-Path $root 'governance\portfolio-governance\schemas\asset-publication-decision-ledger.schema.json') `
        -Destination (Join-Path $fakeRoot 'governance\portfolio-governance\schemas\asset-publication-decision-ledger.schema.json')
    Get-ChildItem -LiteralPath (Join-Path $root 'governance\portfolio-governance\benchmark') -File | Copy-Item `
        -Destination (Join-Path $fakeRoot 'governance\portfolio-governance\benchmark')
    Get-ChildItem -LiteralPath (Join-Path $root 'governance\portfolio-governance\policies') -File | Copy-Item `
        -Destination (Join-Path $fakeRoot 'governance\portfolio-governance\policies')
    Copy-Item -LiteralPath (Join-Path $root 'scripts\privacy-check.ps1') `
        -Destination (Join-Path $fakeRoot 'scripts\privacy-check.ps1')
    Get-ChildItem -LiteralPath $runtime -File | Copy-Item `
        -Destination (Join-Path $fakeRoot 'backend\src\main\resources\public-data\bundle')
    Copy-Item -LiteralPath $patch `
        -Destination (Join-Path $fakeRoot 'governance\portfolio-governance\candidates\wave-1-public-patch.json')
    Copy-Item -LiteralPath $routes `
        -Destination (Join-Path $fakeRoot 'governance\portfolio-governance\candidates\wave-1-public-routes.json')
    return $fakeRoot
}
function Invoke-FakePrepare([string]$FakeRoot, [string]$InventoryValue, [string]$FailureStage = 'NONE') {
    $workspace = Join-Path ([IO.Path]::GetTempPath()) ('portfolio-w1-test-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $workspace | Out-Null
    $script:workspaces += $workspace
    $script = Join-Path $FakeRoot 'governance\portfolio-governance\scripts\portfolio-governance.ps1'
    $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $script `
        -Command prepare-candidate -Workspace $workspace `
        -RuntimeBundle (Join-Path $FakeRoot 'backend\src\main\resources\public-data\bundle') `
        -PatchManifest (Join-Path $FakeRoot 'governance\portfolio-governance\candidates\wave-1-public-patch.json') `
        -RouteManifest (Join-Path $FakeRoot 'governance\portfolio-governance\candidates\wave-1-public-routes.json') `
        -AssetInventory $InventoryValue -TargetVersion '2026-07-24.1' `
        -PrepareFailureStage $FailureStage 2>&1
    return [ordered]@{
        ExitCode = $LASTEXITCODE
        Json = ($output | Select-Object -Last 1 | ConvertFrom-Json)
        OutputText = (@($output) -join [Environment]::NewLine)
        Workspace = $workspace
    }
}

try {
    $inventorySource = New-SyntheticInventory
    $currentRuntime = $runtime
    $currentRuntimeWorkspace = Join-Path ([IO.Path]::GetTempPath()) ('portfolio-w1-test-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $currentRuntimeWorkspace | Out-Null
    $workspaces += $currentRuntimeWorkspace
    $currentRuntimeResult = Invoke-Prepare $currentRuntimeWorkspace -RuntimeValue $currentRuntime `
        -PatchValue $patch -RoutesValue $routes -InventoryValue $inventorySource
    Assert-True ($currentRuntimeResult.ExitCode -ne 0 -and
        $currentRuntimeResult.Json.blockingFindings[0].code -eq 'PREPARE_RUNTIME_BUNDLE_INVALID') `
        'Wave 1 must reject the current schema 4.0 retrieval Bundle.'
    $runtime = New-WaveOneRuntimeFixture
    $waveOneRepository = New-FakeRepository
    $governance = Join-Path $waveOneRepository 'governance\portfolio-governance\scripts\portfolio-governance.ps1'
    $runtime = Join-Path $waveOneRepository 'backend\src\main\resources\public-data\bundle'
    $patch = Join-Path $waveOneRepository 'governance\portfolio-governance\candidates\wave-1-public-patch.json'
    $routes = Join-Path $waveOneRepository 'governance\portfolio-governance\candidates\wave-1-public-routes.json'
    $workspace = Join-Path ([IO.Path]::GetTempPath()) ('portfolio-w1-test-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $workspace | Out-Null
    $workspaces += $workspace
    $result = Invoke-Prepare $workspace -RuntimeValue $runtime -PatchValue $patch `
        -RoutesValue $routes -InventoryValue $inventorySource
    Assert-True ($result.ExitCode -ne 0 -and
        $result.Json.blockingFindings[0].code -eq 'PRESET_CONTRACT_PROJECTION_MISMATCH') `
        'Historical Wave 1 regeneration must fail closed on missing declared contract presets.'
    Assert-True (-not (Test-Path (Join-Path $workspace 'prepared-candidates\2026-07-24.1'))) `
        'A failed contract projection must not create a candidate package.'
    $package = Join-Path $workspace 'prepared-candidates\2026-07-24.1'
    Assert-True (-not (Test-Path -LiteralPath $package)) 'No Wave 1 candidate package may exist after a failed projection.'
    Assert-True (@($result.Json.artifacts | Where-Object { [IO.Path]::IsPathRooted($_) }).Count -eq 0) 'Artifacts must be relative paths.'

    $second = Join-Path ([IO.Path]::GetTempPath()) ('portfolio-w1-test-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $second | Out-Null
    $workspaces += $second
    $secondResult = Invoke-Prepare $second -RuntimeValue $runtime -PatchValue $patch `
        -RoutesValue $routes -InventoryValue $inventorySource
    Assert-True ($secondResult.ExitCode -ne 0 -and
        $secondResult.Json.blockingFindings[0].code -eq 'PRESET_CONTRACT_PROJECTION_MISMATCH') `
        'Historical regeneration must fail closed deterministically.'
    Assert-True (-not (Test-Path (Join-Path $second 'prepared-candidates\2026-07-24.1'))) `
        'A failed projection must not create a target.'

    $existing = Join-Path ([IO.Path]::GetTempPath()) ('portfolio-w1-test-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path (Join-Path $existing 'prepared-candidates\2026-07-24.1') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $existing 'prepared-candidates\2026-07-24.1\sentinel.txt') -Value 'keep'
    $workspaces += $existing
    $existingResult = Invoke-Prepare $existing -RuntimeValue $runtime -PatchValue $patch `
        -RoutesValue $routes -InventoryValue $inventorySource
    Assert-True ($existingResult.ExitCode -ne 0) 'Existing package must be rejected.'
    Assert-True ((Get-Content (Join-Path $existing 'prepared-candidates\2026-07-24.1\sentinel.txt') -Raw).Trim() -eq 'keep') 'Existing output must remain unchanged.'

    $missingInventoryWorkspace = Join-Path ([IO.Path]::GetTempPath()) ('portfolio-w1-test-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $missingInventoryWorkspace | Out-Null
    $workspaces += $missingInventoryWorkspace
    $missingInventoryOutput = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $governance `
        -Command prepare-candidate -Workspace $missingInventoryWorkspace `
        -RuntimeBundle $runtime -PatchManifest $patch -RouteManifest $routes `
        -TargetVersion '2026-07-24.1'
    $missingInventoryExit = $LASTEXITCODE
    $missingInventoryJson = $missingInventoryOutput | Select-Object -Last 1 | ConvertFrom-Json
    Assert-True ($missingInventoryExit -ne 0 -and
        $missingInventoryJson.blockingFindings[0].code -eq 'WORKSPACE_REQUIRED') `
        'AssetInventory must be explicit.'

    $fake = New-FakeRepository
    $inventoryCopy = Join-Path ([IO.Path]::GetTempPath()) ('portfolio-w1-inventory-' + [guid]::NewGuid().ToString('N') + '.json')
    Copy-Item -LiteralPath $inventorySource -Destination $inventoryCopy
    $workspaces += $inventoryCopy

    $runtimeManifest = Join-Path $fake 'backend\src\main\resources\public-data\bundle\manifest.json'
    $manifestData = Get-Content $runtimeManifest -Raw -Encoding UTF8 | ConvertFrom-Json
    $manifestData.candidatePayloadHash = 'sha256:' + ('0' * 64)
    $manifestData | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $runtimeManifest -Encoding UTF8
    $tamperedRuntime = Invoke-FakePrepare $fake $inventoryCopy
    Assert-True ($tamperedRuntime.ExitCode -ne 0) 'Runtime candidatePayloadHash mismatch must fail.'
    Assert-True (-not (Test-Path (Join-Path $tamperedRuntime.Workspace 'prepared-candidates\2026-07-24.1'))) `
        'Runtime verification failure must not create a target.'

    $fakeUnknown = New-FakeRepository
    $patchPath = Join-Path $fakeUnknown 'governance\portfolio-governance\candidates\wave-1-public-patch.json'
    $patchData = Get-Content $patchPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $patchData.claims[0] | Add-Member -NotePropertyName unknownField -NotePropertyValue 'forbidden'
    $patchData | ConvertTo-Json -Depth 50 | Set-Content -LiteralPath $patchPath -Encoding UTF8
    $unknownField = Invoke-FakePrepare $fakeUnknown $inventoryCopy
    Assert-True ($unknownField.ExitCode -ne 0) 'Unknown patch item field must fail.'

    $fakeEnum = New-FakeRepository
    $enumPatchPath = Join-Path $fakeEnum 'governance\portfolio-governance\candidates\wave-1-public-patch.json'
    $enumPatch = Get-Content $enumPatchPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $enumPatch.claims[0].materiality = 'CRITICAL'
    $enumPatch | ConvertTo-Json -Depth 50 | Set-Content -LiteralPath $enumPatchPath -Encoding UTF8
    $unknownEnum = Invoke-FakePrepare $fakeEnum $inventoryCopy
    Assert-True ($unknownEnum.ExitCode -ne 0) 'Unknown patch enum must fail.'

    $fakeReference = New-FakeRepository
    $referencePatchPath = Join-Path $fakeReference 'governance\portfolio-governance\candidates\wave-1-public-patch.json'
    $referencePatch = Get-Content $referencePatchPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $referencePatch.projectUpdates[0].claimIds[0] = 'claim-case-multilingual-no-backfill'
    $referencePatch | ConvertTo-Json -Depth 50 | Set-Content -LiteralPath $referencePatchPath -Encoding UTF8
    $foreignReference = Invoke-FakePrepare $fakeReference $inventoryCopy
    Assert-True ($foreignReference.ExitCode -ne 0) `
        'A Project update must reject a foreign CASE Claim reference.'

    $fakeCollision = New-FakeRepository
    $collisionPatchPath = Join-Path $fakeCollision 'governance\portfolio-governance\candidates\wave-1-public-patch.json'
    $collisionPatch = Get-Content $collisionPatchPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $basePortfolio = Get-Content (Join-Path $fakeCollision 'backend\src\main\resources\public-data\bundle\portfolio.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $collisionPatch.links[0].id = [string]$basePortfolio.claimEvidenceLinks[0].id
    $collisionPatch | ConvertTo-Json -Depth 50 | Set-Content -LiteralPath $collisionPatchPath -Encoding UTF8
    $collision = Invoke-FakePrepare $fakeCollision $inventoryCopy
    Assert-True ($collision.ExitCode -ne 0) 'ClaimEvidenceLink ID collision must fail.'

    $fakeClaimMapping = New-FakeRepository
    $claimMappingPatchPath = Join-Path $fakeClaimMapping 'governance\portfolio-governance\candidates\wave-1-public-patch.json'
    $claimMappingPatch = Get-Content $claimMappingPatchPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $asyncClaim = $claimMappingPatch.claims | Where-Object id -eq 'claim-sql-audit-async-task-lifecycle'
    $asyncLink = $claimMappingPatch.links | Where-Object claimId -eq $asyncClaim.id
    $asyncLink.evidenceId = 'evidence-sql-audit-result-lifecycle-docs'
    $claimMappingPatch | ConvertTo-Json -Depth 50 | Set-Content -LiteralPath $claimMappingPatchPath -Encoding UTF8
    $invalidClaimMapping = Invoke-FakePrepare $fakeClaimMapping $inventoryCopy
    Assert-True ($invalidClaimMapping.ExitCode -ne 0) 'A reviewed Claim-to-Evidence mapping swap must fail.'
    Assert-True ($invalidClaimMapping.Json.blockingFindings[0].code -eq 'PREPARE_PATCH_CLAIM_CONTRACT_INVALID') `
        'A Claim-to-Evidence mapping swap must report the stable Claim contract code.'

    $fakeHold = New-FakeRepository
    $holdInventory = Get-Content $inventoryCopy -Raw -Encoding UTF8 | ConvertFrom-Json
    ($holdInventory.assets | Where-Object id -eq 'T-01').reviewState = 'HOLD'
    $holdInventoryPath = Join-Path ([IO.Path]::GetTempPath()) ('portfolio-w1-inventory-' + [guid]::NewGuid().ToString('N') + '.json')
    $holdInventory | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $holdInventoryPath -Encoding UTF8
    $workspaces += $holdInventoryPath
    $holdPublished = Invoke-FakePrepare $fakeHold $holdInventoryPath
    Assert-True ($holdPublished.ExitCode -ne 0) 'A source HOLD asset must not be published by a route manifest.'

    $fakeMissing = New-FakeRepository
    $missingInventoryData = Get-Content $inventoryCopy -Raw -Encoding UTF8 | ConvertFrom-Json
    $missingInventoryData.assets = @($missingInventoryData.assets | Where-Object id -ne 'A-25')
    $missingInventoryPath = Join-Path ([IO.Path]::GetTempPath()) ('portfolio-w1-inventory-' + [guid]::NewGuid().ToString('N') + '.json')
    $missingInventoryData | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $missingInventoryPath -Encoding UTF8
    $workspaces += $missingInventoryPath
    $missingAsset = Invoke-FakePrepare $fakeMissing $missingInventoryPath
    Assert-True ($missingAsset.ExitCode -ne 0) 'A missing inventory asset must fail.'

    $fakeDuplicate = New-FakeRepository
    $duplicateInventoryData = Get-Content $inventoryCopy -Raw -Encoding UTF8 | ConvertFrom-Json
    $duplicateInventoryData.assets[67].id = 'A-25'
    $duplicateInventoryPath = Join-Path ([IO.Path]::GetTempPath()) ('portfolio-w1-inventory-' + [guid]::NewGuid().ToString('N') + '.json')
    $duplicateInventoryData | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $duplicateInventoryPath -Encoding UTF8
    $workspaces += $duplicateInventoryPath
    $duplicateAsset = Invoke-FakePrepare $fakeDuplicate $duplicateInventoryPath
    Assert-True ($duplicateAsset.ExitCode -ne 0) 'A duplicate inventory asset must fail.'

    $fakeRoute = New-FakeRepository
    $routePath = Join-Path $fakeRoute 'governance\portfolio-governance\candidates\wave-1-public-routes.json'
    $routeData = Get-Content $routePath -Raw -Encoding UTF8 | ConvertFrom-Json
    $routeData.publishRoutes[0].finalRoute = 'HOLD'
    $routeData | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $routePath -Encoding UTF8
    $illegalRoute = Invoke-FakePrepare $fakeRoute $inventoryCopy
    Assert-True ($illegalRoute.ExitCode -ne 0) 'A HOLD publish route must fail.'

    $fakeRouteMapping = New-FakeRepository
    $routeMappingPath = Join-Path $fakeRouteMapping 'governance\portfolio-governance\candidates\wave-1-public-routes.json'
    $routeMappingData = Get-Content $routeMappingPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $t05Route = $routeMappingData.publishRoutes | Where-Object assetId -eq 'T-05'
    $t06Route = $routeMappingData.publishRoutes | Where-Object assetId -eq 'T-06'
    $t05CaseSlugs = @($t05Route.caseSlugs)
    $t05EvidenceIds = @($t05Route.evidenceIds)
    $t05Route.caseSlugs = @($t06Route.caseSlugs)
    $t05Route.evidenceIds = @($t06Route.evidenceIds)
    $t06Route.caseSlugs = $t05CaseSlugs
    $t06Route.evidenceIds = $t05EvidenceIds
    $routeMappingData | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $routeMappingPath -Encoding UTF8
    $invalidRouteMapping = Invoke-FakePrepare $fakeRouteMapping $inventoryCopy
    Assert-True ($invalidRouteMapping.ExitCode -ne 0) 'A reviewed asset route mapping swap must fail.'
    Assert-True ($invalidRouteMapping.Json.blockingFindings[0].code -eq 'PREPARE_ROUTE_CONTRACT_INVALID') `
        'An asset route mapping swap must report the stable route contract code.'

    $fakeOriginalHold = New-FakeRepository
    $originalHoldRoutePath = Join-Path $fakeOriginalHold 'governance\portfolio-governance\candidates\wave-1-public-routes.json'
    $originalHoldRoute = Get-Content $originalHoldRoutePath -Raw -Encoding UTF8 | ConvertFrom-Json
    $originalHoldRoute.publishRoutes[1].assetId = 'T-07'
    $originalHoldRoute | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $originalHoldRoutePath -Encoding UTF8
    $originalHoldPublished = Invoke-FakePrepare $fakeOriginalHold $inventoryCopy
    Assert-True ($originalHoldPublished.ExitCode -ne 0) 'T-07 must not be publishable in Wave 1.'

    foreach ($failureStage in @('WRITE','MOVE','CLEANUP')) {
        $fakeFailure = New-FakeRepository
        $failure = Invoke-FakePrepare $fakeFailure $inventoryCopy $failureStage
        Assert-True ($failure.ExitCode -ne 0 -and
            $failure.Json.blockingFindings[0].code -eq 'PRESET_CONTRACT_PROJECTION_MISMATCH') `
            "$failureStage injection must fail closed on the historical contract projection."
        Assert-True (-not (Test-Path (Join-Path $failure.Workspace 'prepared-candidates\2026-07-24.1'))) `
            "$failureStage failure must not leave a target."
        $stages = @(Get-ChildItem (Join-Path $failure.Workspace 'prepared-candidates') -Force -ErrorAction SilentlyContinue |
            Where-Object Name -Like '.prepare-*')
        Assert-True ($stages.Count -eq 0) "$failureStage failure must not enter the staging write phase."
    }

    if (-not [string]::IsNullOrWhiteSpace($env:PORTFOLIO_TEST_ASSET_INVENTORY)) {
        $integrationWorkspace = Join-Path ([IO.Path]::GetTempPath()) ('portfolio-w1-test-' + [guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $integrationWorkspace | Out-Null
        $workspaces += $integrationWorkspace
        $integration = Invoke-Prepare $integrationWorkspace -RuntimeValue $runtime `
            -PatchValue $patch -RoutesValue $routes `
            -InventoryValue $env:PORTFOLIO_TEST_ASSET_INVENTORY
        Assert-True ($integration.ExitCode -ne 0 -and
            $integration.Json.blockingFindings[0].code -eq 'PRESET_CONTRACT_PROJECTION_MISMATCH') `
            'The explicitly supplied local asset inventory integration check must fail closed on missing contract presets.'
    }

    Write-Output 'portfolio-governance prepare-candidate tests passed.'
}
finally {
    foreach ($path in $workspaces) {
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Recurse -Force }
    }
}
