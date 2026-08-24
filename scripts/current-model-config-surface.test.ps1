$ErrorActionPreference = 'Stop'

$retiredProperties = @(
    'portfolio.model-' + 'expression',
    'portfolio.conversational-' + 'model',
    'provider-' + 'ref'
)
$retiredEnvironments = @(
    'PORTFOLIO_MODEL_' + 'EXPRESSION',
    'PORTFOLIO_MODEL_' + 'PROVIDER',
    'PORTFOLIO_MODEL_' + 'ENABLED',
    'PORTFOLIO_MODEL_DATA_POLICY_' + 'APPROVED',
    'PORTFOLIO_AGENT_DEEPSEEK_' + 'API_KEY',
    'PORTFOLIO_AGENT_GLM_' + 'API_KEY',
    'PORTFOLIO_MODEL_OP_TURN_INTERPRETATION_PROVIDER_' + 'REF',
    'PORTFOLIO_MODEL_OP_GENERAL_PROVIDER_' + 'REF'
)
$productionScripts = Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1' -File -Recurse |
    Where-Object { $_.Name -notlike '*.test.ps1' }

foreach ($script in $productionScripts) {
    $source = Get-Content -LiteralPath $script.FullName -Raw
    foreach ($retired in @($retiredProperties + $retiredEnvironments)) {
        if ($source -match [regex]::Escape($retired)) {
            throw "Retired model configuration '$retired' remains in $($script.Name)."
        }
    }
}

$modelExample = Get-Content -LiteralPath (Join-Path (Split-Path -Parent $PSScriptRoot) `
    '.env.example') -Raw
foreach ($retired in @($retiredProperties + $retiredEnvironments)) {
    if ($modelExample -match [regex]::Escape($retired)) {
        throw "Retired model configuration '$retired' remains in .env.example."
    }
}

$required = @{
    'run-eval.ps1' = 'portfolio.model-runtime.enabled=false'
    'run-eval-offline.ps1' = 'portfolio.model-runtime.enabled=false'
    'run-jar-e2e.ps1' = 'portfolio.model-runtime.enabled=false'
    'run-agent-behavior-audit.ps1' = 'PORTFOLIO_MODEL_RUNTIME_ENABLED'
    'start-local.ps1' = 'PORTFOLIO_GLM_ENABLED'
}
foreach ($entry in $required.GetEnumerator()) {
    $source = Get-Content -LiteralPath (Join-Path $PSScriptRoot $entry.Key) -Raw
    if ($source -notmatch [regex]::Escape($entry.Value)) {
        throw "$($entry.Key) does not use the current model configuration authority."
    }
}

Write-Output 'current model config surface tests passed'
