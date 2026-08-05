param()
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$governance = Join-Path $root 'governance\portfolio-governance\scripts\portfolio-governance.ps1'
$policyPath = Join-Path $root 'governance\portfolio-governance\policies\preset-contract-policy.v1.json'
$schemaPath = Join-Path $root 'governance\portfolio-governance\schemas\preset-contract-policy.schema.json'
$bundlePortfolio = Join-Path $root 'backend\src\main\resources\public-data\bundle\portfolio.json'
$closurePatch = Join-Path $root 'governance\portfolio-governance\candidates\preset-contract-closure-public-patch.json'
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('portfolio-policy-test-' + [guid]::NewGuid().ToString('N'))
$workspaces = @($tempRoot)

function Assert-True([bool]$Value, [string]$Message) {
    if (-not $Value) { throw $Message }
}
if (-not (Test-Path -LiteralPath $policyPath -PathType Leaf)) {
    throw 'Preset contract policy is required.'
}
if (-not (Test-Path -LiteralPath $schemaPath -PathType Leaf)) {
    throw 'Preset contract policy schema is required.'
}
$policy = Get-Content -LiteralPath $policyPath -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-True ([string]$policy.schemaVersion -eq '1.0') 'Policy schemaVersion must be 1.0.'
Assert-True (@($policy.activeContracts).Count -eq 18) 'Policy must declare exactly 18 active contracts.'
Assert-True (@($policy.nonPublicDraftPresetIds).Count -eq 1 -and
    [string]$policy.nonPublicDraftPresetIds[0] -eq 'question-public-assets-overview') 'Policy draft list must be the cross-subject preset.'
$expectedActiveIds = @(
    'sql-audit-overview','question-sql-audit-negative-input','question-sql-audit-partial-success',
    'question-case-multilingual-overview','question-case-role-reset-overview','question-case-codegraph-overview',
    'question-sql-audit-async-and-recovery','question-sql-audit-progress-fallback',
    'question-sql-audit-archive-and-truncation','question-case-multilingual-verification-sequence',
    'question-case-multilingual-recovery-boundary','question-case-role-reset-acceptance-result',
    'question-case-role-reset-safety-boundary','question-case-codegraph-method',
    'question-case-codegraph-quality-boundary','question-abtest-overview',
    'question-abtest-stratification-bucketing','question-abtest-stable-assignment-and-rollback'
)
Assert-True ((Compare-Object @($policy.activeContracts.presetId) $expectedActiveIds).Count -eq 0) 'Policy active allowlist is not exact.'
$policyJson = $policy | ConvertTo-Json -Depth 30

$runner = @'
param(
    [string]$Scenario,
    [string]$PortfolioPath,
    [string]$PolicyPath,
    [string]$RepositoryRoot,
    [string]$GovernanceScript
)
$ErrorActionPreference = 'Stop'
$source = Get-Content -LiteralPath $GovernanceScript -Raw
$marker = '$resolvedWorkspace = Resolve-SafePath'
$index = $source.IndexOf($marker)
if ($index -lt 0) { throw 'Unable to isolate governance function definitions.' }
$functions = $source.Substring(0, $index)
. ([scriptblock]::Create($functions)) -Command 'preset-contract-policy-test' -Workspace (Join-Path $RepositoryRoot 'policy-test-workspace')
$script:Command = 'preset-contract-policy-test'
$script:repositoryRoot = $RepositoryRoot
$policy = Get-Content -LiteralPath $PolicyPath -Raw -Encoding UTF8 | ConvertFrom-Json
$scenarioPolicyPath = Join-Path ([IO.Path]::GetTempPath()) ('preset-policy-scenario-' + [guid]::NewGuid().ToString('N') + '.json')
$portfolio = Get-Content -LiteralPath $PortfolioPath -Raw -Encoding UTF8 | ConvertFrom-Json
$patch = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'governance\portfolio-governance\candidates\preset-contract-closure-public-patch.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$portfolio.claims = @($portfolio.claims) + @($patch.claims)
$portfolio.claimEvidenceLinks = @($portfolio.claimEvidenceLinks) + @($patch.links)
$abtestProject = @($portfolio.projects | Where-Object id -eq 'weekend-login-abtest-project')
if ($abtestProject.Count -ne 1) { throw 'ABTest Project is missing from the portfolio fixture.' }
$abtestProject[0].claimIds = @($abtestProject[0].claimIds) + @($patch.projectUpdates[0].addClaimIds)

switch ($Scenario) {
    'legal' {
        $hash = Invoke-PresetContractProjection $portfolio $policy
        if ([string]::IsNullOrWhiteSpace([string]$hash)) { throw 'Legal projection returned an empty set hash.' }
        $active = @($portfolio.questionPresets | Where-Object contractStatus -eq 'ACTIVE')
        if ($active.Count -ne 18) { throw 'Legal projection must activate exactly 18 presets.' }
        $draft = @($portfolio.questionPresets | Where-Object id -eq 'question-public-assets-overview')
        if ($draft.Count -ne 1 -or [string]$draft[0].contractStatus -ne 'DRAFT') { throw 'question-public-assets-overview must remain DRAFT.' }
        if (@($portfolio.questionPresets | Where-Object { $_.contractStatus -eq 'ACTIVE' -and [string]::IsNullOrWhiteSpace([string]$_.contractSubjectId) }).Count -gt 0) { throw 'Every active preset requires contractSubjectId.' }
        $expectedHash = $hash
        $second = Invoke-PresetContractProjection $portfolio $policy
        if ($second -ne $expectedHash) { throw 'Projection must be deterministic.' }
        Assert-PresetContractProjection $portfolio $policy $expectedHash
        $activeQuestion = @($portfolio.questionPresets | Where-Object id -eq 'sql-audit-overview')[0]
        $versionA = Get-PresetContractVersion $activeQuestion
        $versionB = Get-PresetContractVersion $activeQuestion
        if ($versionA -ne $versionB -or $versionA -notmatch '^pcv1-[a-f0-9]{16}$') { throw 'Contract version must be deterministic and formatted pcv1-[a-f0-9]{16}.' }
        $changedSubject = $activeQuestion | ConvertTo-Json -Depth 20 | ConvertFrom-Json
        $changedSubject.contractSubjectId = 'subject-other'
        if ((Get-PresetContractVersion $changedSubject) -eq $versionA) { throw 'Changing contractSubjectId must change the contract version.' }
        $aliasChanged = $activeQuestion | ConvertTo-Json -Depth 20 | ConvertFrom-Json
        $aliasChanged.aliases = @([string]$activeQuestion.aliases[1], [string]$activeQuestion.aliases[0])
        if ((Get-PresetContractVersion $aliasChanged) -eq $versionA) { throw 'Reordering aliases must change the contract version.' }
        $listHash = Get-PresetContractSetHash @($portfolio.questionPresets | Where-Object contractStatus -eq 'ACTIVE')
        if ($listHash -ne $hash) { throw 'Set hash must equal the projected hash.' }
        $reversed = @($portfolio.questionPresets | Where-Object contractStatus -eq 'ACTIVE')
        [Array]::Reverse($reversed)
        $reversedHash = Get-PresetContractSetHash $reversed
        if ($reversedHash -ne $hash) { throw 'Set hash must be order-independent.' }
        Write-Output ('SCENARIO_OK ' + $hash)
    }
    'duplicate-active' {
        $policy.activeContracts = @($policy.activeContracts) + @($policy.activeContracts[0])
        $policy | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $scenarioPolicyPath -Encoding UTF8
        $null = Read-PresetContractPolicy $scenarioPolicyPath
        Write-Output 'SCENARIO_OK'
    }
    'active-draft-overlap' {
        $policy.nonPublicDraftPresetIds = @($policy.nonPublicDraftPresetIds) + @('sql-audit-overview')
        $policy | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $scenarioPolicyPath -Encoding UTF8
        $null = Read-PresetContractPolicy $scenarioPolicyPath
        Write-Output 'SCENARIO_OK'
    }
    'unknown-preset' {
        $policy.activeContracts[0].presetId = 'question-unknown'
        $null = Invoke-PresetContractProjection $portfolio $policy
        Write-Output 'SCENARIO_OK'
    }
    'missing-preset' {
        $portfolio.questionPresets = @($portfolio.questionPresets | Where-Object { [string]$_.id -ne 'question-abtest-overview' })
        $null = Invoke-PresetContractProjection $portfolio $policy
        Write-Output 'SCENARIO_OK'
    }
    'unknown-subject' {
        $policy.activeContracts[0].contractSubjectId = 'unknown-subject'
        $null = Invoke-PresetContractProjection $portfolio $policy
        Write-Output 'SCENARIO_OK'
    }
    'cross-claim' {
        $policy.activeContracts[0].requiredClaimIds = @($policy.activeContracts[0].requiredClaimIds) + @('claim-case-role-reset-cache-interference-problem')
        $null = Invoke-PresetContractProjection $portfolio $policy
        Write-Output 'SCENARIO_OK'
    }
    'min-evidence' {
        $policy.activeContracts[0].evidenceRequirement.minimumApprovedEvidencePerRequiredClaim = 2
        $null = Invoke-PresetContractProjection $portfolio $policy
        Write-Output 'SCENARIO_OK'
    }
    'undeclared-active' {
        $portfolio.questionPresets = @($portfolio.questionPresets | ForEach-Object {
            if ([string]$_.id -eq 'question-public-assets-overview') {
                $_.contractStatus = 'ACTIVE'
            }
            $_
        })
        $policy.nonPublicDraftPresetIds = @()
        $null = Invoke-PresetContractProjection $portfolio $policy
        Write-Output 'SCENARIO_OK'
    }
    'hash-tamper' {
        $null = Invoke-PresetContractProjection $portfolio $policy
        Assert-PresetContractProjection $portfolio $policy ('sha256:' + ('0' * 64))
        Write-Output 'SCENARIO_OK'
    }
    default { throw "Unknown scenario: $Scenario" }
}
'@

try {
    New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
    $runnerPath = Join-Path $tempRoot 'scenario-runner.ps1'
    [IO.File]::WriteAllText($runnerPath, $runner, (New-Object Text.UTF8Encoding($false)))
    $policyFile = Join-Path $tempRoot 'policy.json'
    [IO.File]::WriteAllText($policyFile, $policyJson, (New-Object Text.UTF8Encoding($false)))

    function Invoke-Scenario([string]$Name, [string]$ExpectedCode) {
        $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $runnerPath `
            -Scenario $Name -PortfolioPath $bundlePortfolio -PolicyPath $policyFile `
            -RepositoryRoot $root -GovernanceScript $governance 2>&1
        $exitCode = $LASTEXITCODE
        $json = $null
        $tail = @($output | Where-Object { $_.Trim().StartsWith('{') -or $_.Trim().StartsWith('SCENARIO_OK') }) |
            Select-Object -Last 1
        try { $json = $tail | ConvertFrom-Json } catch { }
        if ($null -eq $json) {
            if ($exitCode -eq 0 -and [string]::IsNullOrWhiteSpace($ExpectedCode) -and
                    $tail -match '^SCENARIO_OK\s+') {
                return ($tail -replace '^SCENARIO_OK\s*', '')
            }
            throw "Scenario $Name failed without a typed result (exit=$exitCode)."
        }
        if ($exitCode -ne 2 -or [string]$json.status -ne 'FAIL') {
            throw "Scenario $Name must fail closed with a typed result."
        }
        $actualCode = [string]$json.blockingFindings[0].code
        if ($actualCode -ne $ExpectedCode) {
            throw "Scenario $Name expected $ExpectedCode but got $actualCode."
        }
        return $null
    }

    $legalHash = Invoke-Scenario 'legal' $null
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$legalHash)) 'Legal projection must return a set hash.'
    Assert-True ($legalHash -match '^sha256:[a-f0-9]{64}$') 'Set hash format must be sha256:[a-f0-9]{64}.'
    Invoke-Scenario 'duplicate-active' 'PRESET_CONTRACT_POLICY_INVALID'
    Invoke-Scenario 'active-draft-overlap' 'PRESET_CONTRACT_POLICY_INVALID'
    Invoke-Scenario 'unknown-preset' 'PRESET_CONTRACT_PROJECTION_MISMATCH'
    Invoke-Scenario 'missing-preset' 'PRESET_CONTRACT_PROJECTION_MISMATCH'
    Invoke-Scenario 'unknown-subject' 'PRESET_CONTRACT_SUBJECT_INVALID'
    Invoke-Scenario 'cross-claim' 'PRESET_CONTRACT_CLAIM_INVALID'
    Invoke-Scenario 'min-evidence' 'PRESET_CONTRACT_EVIDENCE_INSUFFICIENT'
    Invoke-Scenario 'undeclared-active' 'PRESET_CONTRACT_ACTIVE_SET_DRIFT'
    Invoke-Scenario 'hash-tamper' 'PRESET_CONTRACT_SET_HASH_MISMATCH'

    Write-Output 'portfolio-governance preset contract policy tests passed.'
}
finally {
    foreach ($path in $workspaces) {
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Recurse -Force }
    }
}
