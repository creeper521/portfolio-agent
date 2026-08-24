$ErrorActionPreference = 'Stop'

$retiredProperty = 'portfolio.model-' + 'expression'
$retiredEnvironment = 'PORTFOLIO_MODEL_' + 'EXPRESSION'
$productionScripts = Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1' -File |
    Where-Object { $_.Name -notlike '*.test.ps1' }

foreach ($script in $productionScripts) {
    $source = Get-Content -LiteralPath $script.FullName -Raw
    if ($source -match [regex]::Escape($retiredProperty) -or
            $source -match [regex]::Escape($retiredEnvironment)) {
        throw "Retired model configuration remains in $($script.Name)."
    }
}

$required = @{
    'run-eval.ps1' = 'portfolio.conversational-model.enabled=false'
    'run-eval-offline.ps1' = 'portfolio.conversational-model.enabled=false'
    'run-jar-e2e.ps1' = 'portfolio.conversational-model.enabled=false'
    'run-agent-behavior-audit.ps1' = 'PORTFOLIO_MODEL_ENABLED'
}
foreach ($entry in $required.GetEnumerator()) {
    $source = Get-Content -LiteralPath (Join-Path $PSScriptRoot $entry.Key) -Raw
    if ($source -notmatch [regex]::Escape($entry.Value)) {
        throw "$($entry.Key) does not use the current model configuration authority."
    }
}

Write-Output 'current model config surface tests passed'
