$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$validator = Join-Path $PSScriptRoot 'agent-architecture-status.ps1'
$canonical = Join-Path $root 'docs\agent-architecture-status.json'
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('agent-architecture-status-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporaryRoot | Out-Null

function Invoke-Validator([string]$path) {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & pwsh.exe -NoProfile -File $validator -StatusPath $path 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = ($output -join [Environment]::NewLine)
    }
}

function Write-Fixture([string]$name, $value) {
    $path = Join-Path $temporaryRoot $name
    $value | ConvertTo-Json -Depth 32 | Set-Content -LiteralPath $path -Encoding UTF8
    return $path
}

function Assert-True([bool]$condition, [string]$message) {
    if (-not $condition) { throw $message }
}

try {
    $canonicalResult = Invoke-Validator $canonical
    Assert-True ($canonicalResult.ExitCode -eq 0) 'canonical status must pass'
    Assert-True ($canonicalResult.Output -match 'deferred.open=0') `
        'canonical status must report no open deferred items'

    $architectureReview = Get-Content -LiteralPath $canonical -Raw | ConvertFrom-Json
    $architectureReview.overallStatus = 'ARCHITECTURE_REVIEW'
    $architectureReviewResult = Invoke-Validator `
        (Write-Fixture 'architecture-review.json' $architectureReview)
    Assert-True ($architectureReviewResult.ExitCode -eq 0) `
        'evidence-driven architecture review must be a valid overall status'

    $waived = Get-Content -LiteralPath $canonical -Raw | ConvertFrom-Json
    $waived.overallStatus = 'VERIFICATION_IN_PROGRESS'
    $waived.deferredItems = @(
        Get-Content -LiteralPath (Join-Path $root `
            'docs\templates\agent-architecture-deferred-item.json') -Raw |
            ConvertFrom-Json
    )
    $waivedResult = Invoke-Validator (Write-Fixture 'waived.json' $waived)
    Assert-True ($waivedResult.ExitCode -eq 0) 'complete waived item must pass'
    Assert-True ($waivedResult.Output -match 'deferred.open=1') `
        'waived status must remain visible as open'

    $falseComplete = Get-Content -LiteralPath $canonical -Raw | ConvertFrom-Json
    $falseComplete.overallStatus = 'COMPLETE'
    foreach ($invariant in $falseComplete.hardInvariants) {
        $invariant.status = 'PASS'
    }
    $falseComplete.deferredItems = $waived.deferredItems
    $falseCompleteResult = Invoke-Validator (Write-Fixture 'false-complete.json' $falseComplete)
    Assert-True ($falseCompleteResult.ExitCode -ne 0) `
        'COMPLETE with a waived item must fail'
    Assert-True ($falseCompleteResult.Output -match 'forbids unresolved deferred items') `
        'false completion failure must identify the unresolved item rule'

    $waivedInvariant = Get-Content -LiteralPath $canonical -Raw | ConvertFrom-Json
    $waivedInvariant.hardInvariants[0].status = 'WAIVED'
    $waivedInvariantResult = Invoke-Validator (Write-Fixture 'waived-invariant.json' $waivedInvariant)
    Assert-True ($waivedInvariantResult.ExitCode -ne 0) `
        'hard invariant waiver must fail'
    Assert-True ($waivedInvariantResult.Output -match 'cannot be waived') `
        'hard invariant failure must explain the waiver prohibition'

    $unsupportedPrivacyPass = Get-Content -LiteralPath $canonical -Raw | ConvertFrom-Json
    $privacyInvariant = @($unsupportedPrivacyPass.hardInvariants | Where-Object {
        $_.id -eq 'PRIVACY_BOUNDARY'
    })[0]
    $privacyInvariant.status = 'PASS'
    $privacyInvariant.evidence = 'Static privacy scan passed.'
    $unsupportedPrivacyResult = Invoke-Validator `
        (Write-Fixture 'unsupported-privacy-pass.json' $unsupportedPrivacyPass)
    Assert-True ($unsupportedPrivacyResult.ExitCode -ne 0) `
        'privacy PASS without complete settlement evidence must fail'
    Assert-True ($unsupportedPrivacyResult.Output -match `
        'PRIVACY_BOUNDARY PASS requires fresh') `
        'privacy PASS failure must identify the missing runtime evidence'

    $incompleteDeferred = Get-Content -LiteralPath $canonical -Raw | ConvertFrom-Json
    $incompleteDeferred.overallStatus = 'VERIFICATION_IN_PROGRESS'
    $template = Get-Content -LiteralPath (Join-Path $root `
        'docs\templates\agent-architecture-deferred-item.json') -Raw |
        ConvertFrom-Json
    $template.nextAction.command = ''
    $incompleteDeferred.deferredItems = @($template)
    $incompleteResult = Invoke-Validator (Write-Fixture 'incomplete.json' $incompleteDeferred)
    Assert-True ($incompleteResult.ExitCode -ne 0) `
        'deferred item without a next command must fail'

    Write-Output 'AGENT_ARCHITECTURE_STATUS_TESTS_OK tests=7'
} finally {
    Remove-Item -LiteralPath $temporaryRoot -Recurse -Force -ErrorAction SilentlyContinue
}
