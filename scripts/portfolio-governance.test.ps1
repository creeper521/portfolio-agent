$ErrorActionPreference = 'Stop'

$cli = Join-Path $PSScriptRoot 'portfolio-governance.ps1'
$repositoryRoot = Split-Path $PSScriptRoot -Parent
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('portfolio-governance-' + [guid]::NewGuid())
$workspace = Join-Path $fixtureRoot 'workspace'
$candidate = Join-Path $workspace 'candidates\candidate-1'

function Invoke-Governance([string[]]$Arguments) {
    $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $cli @Arguments 2>&1
    return @{ ExitCode = $LASTEXITCODE; Output = ($output -join [Environment]::NewLine) }
}

function New-Candidate([string]$Name) {
    $path = Join-Path $workspace ('candidates\' + $Name)
    New-Item -ItemType Directory -Force -Path $path | Out-Null
    Copy-Item -LiteralPath (Join-Path $repositoryRoot 'backend\src\main\resources\public-data\bundle\portfolio.json') -Destination $path
    Copy-Item -LiteralPath (Join-Path $repositoryRoot 'backend\src\main\resources\public-data\bundle\presentation.json') -Destination $path
    return $path
}

try {
    $missing = Invoke-Governance @('-Command', 'inspect')
    if ($missing.ExitCode -eq 0) { throw 'Missing workspace must fail.' }

    $inside = Invoke-Governance @('-Command', 'inspect', '-Workspace', $repositoryRoot)
    if ($inside.ExitCode -eq 0) { throw 'Repository-contained workspace must fail.' }

    $candidate = New-Candidate 'candidate-1'

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
    if ($result.runSnapshot.candidatePayloadHash -ne 'sha256:c73a8169a7c0148eb0f32721f95c0d63dc8c8ad1f1f89b3890d5417a1c3478a1') {
        throw 'PowerShell candidatePayloadHash does not match the Java test vector.'
    }
    if ($result.runSnapshot.policyBundleHash.Contains('pending') -or
        $result.runSnapshot.benchmarkDefinitionHash.Contains('pending')) {
        throw 'GovernanceRunSnapshot must bind exact policy and benchmark definitions.'
    }
    if ($result.gates -join ',' -ne 'SchemaGate,ReferenceIntegrityGate,PrivacyGate,ClaimEvidenceGate,CompatibilityGate') {
        throw 'Read-only gates did not run in fixed order.'
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
        -not $approvalData.approvalDigest.StartsWith('sha256:')) { throw 'Approval did not bind the canonical payload.' }

    $releaseRoot = Join-Path $fixtureRoot 'public-releases'
    New-Item -ItemType Directory -Force -Path $releaseRoot | Out-Null
    $dryRun = Invoke-Governance @('-Command', 'publish', '-Workspace', $workspace,
        '-Candidate', $candidate, '-ApprovalId', $approvalData.approvalId, '-ReleaseRoot', $releaseRoot)
    if ($dryRun.ExitCode -ne 0 -or -not (($dryRun.Output | ConvertFrom-Json).dryRun)) { throw 'Publish must default to dry-run.' }
    if (Test-Path -LiteralPath (Join-Path $releaseRoot 'versions\2026-07-21.1')) { throw 'Dry-run changed public release state.' }

    $blockedPublishAudit = Join-Path $workspace 'audit\publish.jsonl'
    New-Item -ItemType Directory -Force -Path $blockedPublishAudit | Out-Null
    $auditFailure = Invoke-Governance @('-Command', 'publish', '-Workspace', $workspace,
        '-Candidate', $candidate, '-ApprovalId', $approvalData.approvalId,
        '-ReleaseRoot', $releaseRoot, '-Confirm')
    if ($auditFailure.ExitCode -eq 0 -or -not $auditFailure.Output.Contains('PUBLISH_AUDIT_WRITE_FAILED')) {
        throw "Publish must fail closed when its security audit is unavailable: $($auditFailure.Output)"
    }
    if (Test-Path -LiteralPath (Join-Path $releaseRoot 'versions\2026-07-21.1')) {
        throw 'Audit failure must happen before any public release state is written.'
    }
    Remove-Item -LiteralPath $blockedPublishAudit -Recurse -Force

    $publish = Invoke-Governance @('-Command', 'publish', '-Workspace', $workspace,
        '-Candidate', $candidate, '-ApprovalId', $approvalData.approvalId, '-ReleaseRoot', $releaseRoot, '-Confirm')
    if ($publish.ExitCode -ne 0) { throw "Publish failed: $($publish.Output)" }
    $publishedVersion = Join-Path $releaseRoot 'versions\2026-07-21.1'
    if (-not (Test-Path -LiteralPath (Join-Path $publishedVersion 'manifest.json') -PathType Leaf) -or
        -not (Test-Path -LiteralPath (Join-Path $publishedVersion 'checksums.json') -PathType Leaf)) { throw 'Published bundle is incomplete.' }
    if (-not [Linq.Enumerable]::SequenceEqual([byte[]][IO.File]::ReadAllBytes((Join-Path $candidate 'portfolio.json')),
        [byte[]][IO.File]::ReadAllBytes((Join-Path $publishedVersion 'portfolio.json')))) { throw 'Publish changed approved portfolio bytes.' }
    if ((Get-Content -LiteralPath (Join-Path $releaseRoot 'active') -Raw).Trim() -ne '2026-07-21.1') { throw 'Active pointer was not switched.' }

    $listed = Invoke-Governance @('-Command', 'list', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot)
    if ($listed.ExitCode -ne 0 -or @((($listed.Output | ConvertFrom-Json).versions)).Count -ne 1) {
        throw "List must report complete published versions: $($listed.Output)"
    }
    $status = Invoke-Governance @('-Command', 'status', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot)
    $statusResult = $status.Output | ConvertFrom-Json
    if ($status.ExitCode -ne 0 -or $statusResult.activeVersion -ne '2026-07-21.1') {
        throw "Status must report the active version: $($status.Output)"
    }
    $verified = Invoke-Governance @('-Command', 'verify', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot, '-TargetVersion', '2026-07-21.1')
    if ($verified.ExitCode -ne 0 -or ($verified.Output | ConvertFrom-Json).verifiedVersion -ne '2026-07-21.1') {
        throw "Verify must validate an immutable published bundle: $($verified.Output)"
    }

    $repeat = Invoke-Governance @('-Command', 'publish', '-Workspace', $workspace,
        '-Candidate', $candidate, '-ApprovalId', $approvalData.approvalId, '-ReleaseRoot', $releaseRoot, '-Confirm')
    if ($repeat.ExitCode -ne 0) { throw 'Identical repeat publish must be idempotent.' }

    $candidate2 = New-Candidate 'candidate-2'
    foreach ($name in @('portfolio.json', 'presentation.json')) {
        $path = Join-Path $candidate2 $name
        (Get-Content -LiteralPath $path -Raw -Encoding UTF8).Replace('2026-07-21.1', '2026-07-21.2') |
            Set-Content -LiteralPath $path -Encoding UTF8
    }
    $review2 = Invoke-Governance @('-Command', 'build-review-pack', '-Workspace', $workspace, '-Candidate', $candidate2)
    $review2Result = $review2.Output | ConvertFrom-Json
    $approval2 = Invoke-Governance @('-Command', 'approve', '-Workspace', $workspace,
        '-Candidate', $candidate2, '-ReviewRunId', $review2Result.runId,
        '-ApprovedBy', 'owner-alias', '-PrivacyReviewId', 'PRIV-002', '-BenchmarkRunId', 'BENCH-002')
    $approval2Result = $approval2.Output | ConvertFrom-Json
    $approval2Data = Get-Content -LiteralPath (Join-Path $workspace $approval2Result.artifacts[-1]) -Raw -Encoding UTF8 | ConvertFrom-Json
    $postSwitchFailure = Invoke-Governance @('-Command', 'publish', '-Workspace', $workspace,
        '-Candidate', $candidate2, '-ApprovalId', $approval2Data.approvalId, '-ReleaseRoot', $releaseRoot,
        '-PostSwitchProbeUri', 'http://127.0.0.1:1/health', '-Confirm')
    if ($postSwitchFailure.ExitCode -eq 0 -or -not $postSwitchFailure.Output.Contains('PUBLISH_POST_SWITCH_FAILED')) {
        throw "A failed post-switch probe must fail publication: $($postSwitchFailure.Output)"
    }
    if ((Get-Content -LiteralPath (Join-Path $releaseRoot 'active') -Raw).Trim() -ne '2026-07-21.1') {
        throw 'Post-switch failure must atomically restore the verified old active version.'
    }

    Set-Content -LiteralPath (Join-Path $releaseRoot 'active') -Value 'broken-active' -Encoding UTF8
    $rollbackDryRun = Invoke-Governance @('-Command', 'rollback', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot, '-TargetVersion', '2026-07-21.1')
    if ($rollbackDryRun.ExitCode -ne 0 -or -not (($rollbackDryRun.Output | ConvertFrom-Json).dryRun)) { throw 'Rollback must default to dry-run.' }
    if ((Get-Content -LiteralPath (Join-Path $releaseRoot 'active') -Raw).Trim() -ne 'broken-active') { throw 'Rollback dry-run changed active.' }
    $blockedRollbackAudit = Join-Path $workspace 'audit\rollback.jsonl'
    New-Item -ItemType Directory -Force -Path $blockedRollbackAudit | Out-Null
    $rollbackAuditFailure = Invoke-Governance @('-Command', 'rollback', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot, '-TargetVersion', '2026-07-21.1', '-Confirm')
    if ($rollbackAuditFailure.ExitCode -eq 0 -or -not $rollbackAuditFailure.Output.Contains('ROLLBACK_AUDIT_WRITE_FAILED')) {
        throw "Rollback must fail closed when its security audit is unavailable: $($rollbackAuditFailure.Output)"
    }
    if ((Get-Content -LiteralPath (Join-Path $releaseRoot 'active') -Raw).Trim() -ne 'broken-active') {
        throw 'Rollback audit failure must preserve the old active pointer.'
    }
    Remove-Item -LiteralPath $blockedRollbackAudit -Recurse -Force
    @{ versions = @('2026-07-21.1') } | ConvertTo-Json |
        Set-Content -LiteralPath (Join-Path $releaseRoot 'blocked-versions.json') -Encoding UTF8
    $blockedRollback = Invoke-Governance @('-Command', 'rollback', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot, '-TargetVersion', '2026-07-21.1', '-Confirm')
    if ($blockedRollback.ExitCode -eq 0 -or -not $blockedRollback.Output.Contains('ROLLBACK_TARGET_BLOCKED')) {
        throw 'Rollback must reject versions in blocked-versions.json.'
    }
    Remove-Item -LiteralPath (Join-Path $releaseRoot 'blocked-versions.json') -Force
    $rollback = Invoke-Governance @('-Command', 'rollback', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot, '-TargetVersion', '2026-07-21.1', '-Confirm')
    if ($rollback.ExitCode -ne 0) { throw "Rollback failed: $($rollback.Output)" }
    if ((Get-Content -LiteralPath (Join-Path $releaseRoot 'active') -Raw).Trim() -ne '2026-07-21.1') { throw 'Rollback did not restore verified target.' }

    Write-Output 'portfolio-governance tests passed'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
}
