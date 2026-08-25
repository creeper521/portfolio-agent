$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$runnerPath = Join-Path $PSScriptRoot 'run-agent-behavior-audit.ps1'
$runnerSource = Get-Content -LiteralPath $runnerPath -Raw

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

$package = Get-Content -LiteralPath (Join-Path $root 'frontend\package.json') -Raw |
    ConvertFrom-Json
$playwright = Get-Content -LiteralPath (Join-Path $root 'frontend\playwright.config.ts') -Raw

Assert-True ($package.scripts.PSObject.Properties.Name -contains 'test:e2e') `
    'L1/L2/L4 require the existing test:e2e npm script.'
Assert-True ($runnerSource -match "playwrightScript = 'test:e2e'") `
    'Behavior runner must call the existing Playwright script.'
Assert-True ($runnerSource -notmatch 'test:e2e:behavior|--project=api-l0|--project=runtime') `
    'Behavior runner must not reference deleted npm scripts or Playwright projects.'
foreach ($scope in @(
        'CONTRACT_MANIFEST_ONLY',
        'PACKAGED_BROWSER_CONTRACT',
        'PACKAGED_BROWSER_POSTGRESQL',
        'PROVIDER_CODEC_ADVERSARIAL',
        'LIVE_PROVIDER_CANARY'
    )) {
    Assert-True ($runnerSource -match [regex]::Escape($scope)) `
        "Behavior lane must report its actual evidence scope: $scope"
}

$javaTests = @(
    'backend\src\test\java\com\portfolio\agent\turn\contract\AgentTurnScenarioManifestTest.java',
    'backend\src\test\java\com\portfolio\agent\turn\api\AgentTurnClosedContractIntegrationTest.java',
    'backend\src\test\java\com\portfolio\agent\turn\planning\GoalProposalCodecTest.java',
    'backend\src\test\java\com\portfolio\agent\turn\capability\general\GeneralDraftCodecAdversarialTest.java',
    'backend\src\test\java\com\portfolio\agent\infrastructure\model\OpenAiCompatibleStructuredModelTransportDeadlineTest.java'
)
foreach ($relativePath in $javaTests) {
    Assert-True (Test-Path -LiteralPath (Join-Path $root $relativePath) -PathType Leaf) `
        "Behavior runner asset is missing: $relativePath"
}
Assert-True ($playwright -match [regex]::Escape('agent-final-contract\.spec\.ts')) `
    'Playwright default lane must discover the final contract spec.'
Assert-True ($playwright -notmatch '\*\*/behavior/\*\*') `
    'Playwright config must not keep the retired behavior testIgnore.'
Assert-True (-not (Test-Path -LiteralPath (Join-Path $root 'frontend\vitest.behavior.config.ts') -PathType Leaf)) `
    'Retired behavior vitest config must stay deleted.'
Assert-True (-not (Test-Path -LiteralPath (Join-Path $root 'frontend\e2e\behavior'))) `
    'Retired empty behavior spec directory must stay deleted.'

$runnerCommand = Get-Command $runnerPath
Assert-True ($runnerCommand.Parameters['ContextMode'].Attributes.ValidValues -contains `
        'IN_MEMORY') 'Behavior runner must support the current in-memory state mode.'
Assert-True ($runnerCommand.Parameters['ContextMode'].Attributes.ValidValues -contains `
        'POSTGRESQL') 'Behavior runner must support the current PostgreSQL state mode.'

Write-Output 'Agent behavior audit assets tests passed'
