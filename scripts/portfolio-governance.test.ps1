$ErrorActionPreference = 'Stop'

$cli = Join-Path $PSScriptRoot 'portfolio-governance.ps1'
$repositoryRoot = Split-Path $PSScriptRoot -Parent
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('portfolio-governance-' + [guid]::NewGuid())
$workspace = Join-Path $fixtureRoot 'workspace'
$candidate = Join-Path $workspace 'candidates\candidate-1'
$currentBundleVersion = '2026-07-23.1'
$nextBundleVersion = '2026-07-23.2'

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

function Save-Json([object]$Value, [string]$Path) {
    $Value | ConvertTo-Json -Depth 30 |
        Set-Content -LiteralPath $Path -Encoding UTF8
}

function ConvertTo-SchemaThree([string]$CandidatePath) {
    $portfolioPath = Join-Path $CandidatePath 'portfolio.json'
    $presentationPath = Join-Path $CandidatePath 'presentation.json'
    $data = Get-Content -LiteralPath $portfolioPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $data.schemaVersion = '3.0'
    $data.contentVersion = '2026-07-21.1'
    if (-not ($data.PSObject.Properties.Name -contains 'cases')) {
        $data | Add-Member -NotePropertyName cases -NotePropertyValue @()
    }
    $data.questionPresets | ForEach-Object {
        if (-not ($_.PSObject.Properties.Name -contains 'caseIds')) {
            $_ | Add-Member -NotePropertyName caseIds -NotePropertyValue @()
        }
    }
    $data.timelineEvents | ForEach-Object {
        if (-not ($_.PSObject.Properties.Name -contains 'caseIds')) {
            $_ | Add-Member -NotePropertyName caseIds -NotePropertyValue @()
        }
    }
    Save-Json $data $portfolioPath
    $presentation = Get-Content -LiteralPath $presentationPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $presentation.schemaVersion = '3.0'
    $presentation.contentVersion = '2026-07-21.1'
    Save-Json $presentation $presentationPath
    return $data
}

function ConvertTo-SchemaTwo([string]$CandidatePath) {
    $portfolioPath = Join-Path $CandidatePath 'portfolio.json'
    $presentationPath = Join-Path $CandidatePath 'presentation.json'
    $data = Get-Content -LiteralPath $portfolioPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $data.schemaVersion = '2.0'
    $data.contentVersion = '2026-07-21.1'
    $data.PSObject.Properties.Remove('cases')
    $data.claims = @($data.claims | Where-Object { $_.subjectType -ne 'CASE' })
    $legacyClaimIds = @($data.claims | ForEach-Object { [string]$_.id })
    $data.claimEvidenceLinks = @($data.claimEvidenceLinks |
        Where-Object { $legacyClaimIds -contains [string]$_.claimId })
    $data.questionPresets = @($data.questionPresets |
        Where-Object { @($_.projectIds).Count -gt 0 })
    $data.timelineEvents = @($data.timelineEvents |
        Where-Object { @($_.projectIds).Count -gt 0 })
    $data.questionPresets | ForEach-Object { $_.PSObject.Properties.Remove('caseIds') }
    $data.timelineEvents | ForEach-Object { $_.PSObject.Properties.Remove('caseIds') }
    Save-Json $data $portfolioPath
    $presentation = Get-Content -LiteralPath $presentationPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $presentation.schemaVersion = '2.0'
    $presentation.contentVersion = '2026-07-21.1'
    Save-Json $presentation $presentationPath
}

function New-PublicCase([object]$PortfolioData) {
    return [pscustomobject][ordered]@{
        id = 'case-one'
        code = 'CASE-01'
        slug = 'case-one'
        type = 'FEATURE'
        title = 'Case one'
        summary = 'A focused public case study'
        problem = 'The behavior needed an explicit public contract'
        actions = @('Implement validation')
        decisions = @('Keep compatibility')
        verification = @('Focused tests')
        outcome = 'The case contract is explicit'
        limitations = @('Public data only')
        achievementStatus = 'DELIVERED'
        contributionType = 'PRIMARY'
        projectId = $null
        claimIds = @()
        evidenceIds = @()
        timelineEventIds = @()
        questionPresetIds = @()
    }
}

function Invoke-LegacyReviewPack(
    [string]$CandidatePath,
    [object]$PortfolioData
) {
    $benchmarkPath = Join-Path $repositoryRoot `
        '.agents\skills\portfolio-governance\benchmark\active-benchmarks.v1.json'
    $originalBenchmarkBytes = [IO.File]::ReadAllBytes($benchmarkPath)
    try {
        $benchmarkCases = @()
        foreach ($preset in @($PortfolioData.questionPresets)) {
            foreach ($caseType in @(
                'SUPPORTED_QUESTION', 'ALIAS', 'BOUNDARY', 'CLAIM_EVIDENCE', 'SAFETY'
            )) {
                $benchmarkCase = [ordered]@{
                    caseId = 'LEGACY-' + $preset.id + '-' + $caseType
                    category = 'CONTRACT'
                    caseType = $caseType
                    questionPresetId = $preset.id
                    severity = 'ERROR'
                }
                if ($caseType -eq 'CLAIM_EVIDENCE') {
                    $benchmarkCase.requiredClaimIds = @()
                    $benchmarkCase.requiredEvidenceIds = @()
                }
                $benchmarkCases += [pscustomobject]$benchmarkCase
            }
        }
        Save-Json ([pscustomobject]@{
            schemaVersion = '1.0'
            cases = $benchmarkCases
        }) $benchmarkPath
        return Invoke-Governance @(
            '-Command', 'build-review-pack',
            '-Workspace', $workspace,
            '-Candidate', $CandidatePath
        )
    }
    finally {
        [IO.File]::WriteAllBytes($benchmarkPath, $originalBenchmarkBytes)
    }
}

function Invoke-CompilerMain(
    [string]$MainClass,
    [string]$Jar,
    [string[]]$Arguments
) {
    $output = & java.exe ("-Dloader.main=" + $MainClass) -cp $Jar `
        org.springframework.boot.loader.launch.PropertiesLauncher @Arguments 2>&1
    return @{ ExitCode = $LASTEXITCODE; Output = ($output -join [Environment]::NewLine) }
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
    if ($result.runSnapshot.candidatePayloadHash -ne 'sha256:564330cbdf98693e16a4f80a96c55716c244295ae10f7df8a33480f3b1716f48') {
        throw 'PowerShell candidatePayloadHash does not match the approved public Bundle test vector.'
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

    $legacy = New-Candidate 'schema-two-legacy'
    ConvertTo-SchemaTwo $legacy
    $legacyResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $legacy)
    if ($legacyResult.ExitCode -ne 0) {
        throw "Schema 2.0 candidate must normalize to zero cases: $($legacyResult.Output)"
    }
    $legacyData = Get-Content -LiteralPath (Join-Path $legacy 'portfolio.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($legacyData.schemaVersion -ne '2.0' -or
            $legacyData.PSObject.Properties.Name -contains 'cases') {
        throw 'Schema 2.0 compatibility fixture must omit the Case collection.'
    }
    $legacyData | Add-Member -NotePropertyName cases -NotePropertyValue @(
        (New-PublicCase $legacyData)
    )
    $legacyData.questionPresets | ForEach-Object {
        $_ | Add-Member -NotePropertyName caseIds -NotePropertyValue @('case-hostile')
    }
    $legacyData.timelineEvents | ForEach-Object {
        $_ | Add-Member -NotePropertyName caseIds -NotePropertyValue @('case-hostile')
    }
    Save-Json $legacyData (Join-Path $legacy 'portfolio.json')
    $hostileLegacyResult = Invoke-Governance @(
        '-Command', 'validate', '-Workspace', $workspace, '-Candidate', $legacy
    )
    if ($hostileLegacyResult.ExitCode -ne 0) {
        throw "Schema 2.0 hostile Case fields must normalize away: $($hostileLegacyResult.Output)"
    }
    $legacyReview = Invoke-LegacyReviewPack $legacy $legacyData
    if ($legacyReview.ExitCode -ne 0) {
        throw "Schema 2.0 review pack failed: $($legacyReview.Output)"
    }
    $legacyReviewResult = $legacyReview.Output | ConvertFrom-Json
    $legacyReviewPack = Join-Path $workspace $legacyReviewResult.artifacts[1]
    $legacySummary = Get-Content -LiteralPath (Join-Path $legacyReviewPack 'summary.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($legacySummary.counts.cases -ne 0) {
        throw 'Schema 2.0 review summary must report zero cases.'
    }
    $legacyCasesJson = Get-Content -LiteralPath (Join-Path $legacyReviewPack 'cases.json') `
        -Raw -Encoding UTF8
    if ($legacyCasesJson -notmatch '^\s*\[\s*\]\s*$') {
        throw 'Schema 2.0 review cases.json must be an empty array.'
    }

    $schemaThree = New-Candidate 'schema-three'
    $schemaThreeData = ConvertTo-SchemaThree $schemaThree
    Save-Json $schemaThreeData (Join-Path $schemaThree 'portfolio.json')
    $schemaThreeResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $schemaThree)
    if ($schemaThreeResult.ExitCode -ne 0) {
        throw "Schema 3.0 candidate with explicit cases must pass: $($schemaThreeResult.Output)"
    }

    $missingCases = New-Candidate 'schema-three-missing-cases'
    $missingCasesData = ConvertTo-SchemaThree $missingCases
    $missingCasesData.PSObject.Properties.Remove('cases')
    Save-Json $missingCasesData (Join-Path $missingCases 'portfolio.json')
    $missingCasesResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $missingCases)
    if ($missingCasesResult.ExitCode -eq 0 -or
            -not $missingCasesResult.Output.Contains('SCHEMA_CASES_REQUIRED')) {
        throw 'Schema 3.0 must require cases.'
    }

    $missingQuestionCaseIds = New-Candidate 'schema-three-missing-question-case-ids'
    $missingQuestionData = ConvertTo-SchemaThree $missingQuestionCaseIds
    $missingQuestionData.questionPresets[0].PSObject.Properties.Remove('caseIds')
    Save-Json $missingQuestionData (Join-Path $missingQuestionCaseIds 'portfolio.json')
    $missingQuestionResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $missingQuestionCaseIds)
    if ($missingQuestionResult.ExitCode -eq 0 -or
            -not $missingQuestionResult.Output.Contains('SCHEMA_CASE_IDS_REQUIRED')) {
        throw 'Schema 3.0 must require questionPreset.caseIds.'
    }

    $missingTimelineCaseIds = New-Candidate 'schema-three-missing-timeline-case-ids'
    $missingTimelineData = ConvertTo-SchemaThree $missingTimelineCaseIds
    $missingTimelineData.timelineEvents[0].PSObject.Properties.Remove('caseIds')
    Save-Json $missingTimelineData (Join-Path $missingTimelineCaseIds 'portfolio.json')
    $missingTimelineResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $missingTimelineCaseIds)
    if ($missingTimelineResult.ExitCode -eq 0 -or
            -not $missingTimelineResult.Output.Contains('SCHEMA_CASE_IDS_REQUIRED')) {
        throw 'Schema 3.0 must require timelineEvent.caseIds.'
    }

    foreach ($referenceCase in @(
        @{ Name = 'claim'; Property = 'claimIds'; Missing = 'claim-missing'; Code = 'REFERENCE_DANGLING_CASE_CLAIM' },
        @{ Name = 'evidence'; Property = 'evidenceIds'; Missing = 'evidence-missing'; Code = 'REFERENCE_DANGLING_CASE_EVIDENCE' },
        @{ Name = 'timeline'; Property = 'timelineEventIds'; Missing = 'timeline-missing'; Code = 'REFERENCE_DANGLING_CASE_TIMELINE' },
        @{ Name = 'question'; Property = 'questionPresetIds'; Missing = 'question-missing'; Code = 'REFERENCE_DANGLING_CASE_QUESTION' }
    )) {
        $caseCandidate = New-Candidate ('dangling-case-' + $referenceCase.Name)
        $caseData = ConvertTo-SchemaThree $caseCandidate
        $publicCase = New-PublicCase $caseData
        $publicCase.($referenceCase.Property) = @($referenceCase.Missing)
        $caseData.cases = @($caseData.cases) + @($publicCase)
        Save-Json $caseData (Join-Path $caseCandidate 'portfolio.json')
        $caseResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
            '-Candidate', $caseCandidate)
        if ($caseResult.ExitCode -eq 0 -or
                -not $caseResult.Output.Contains($referenceCase.Code)) {
            throw "Unknown Case $($referenceCase.Name) reference must fail ReferenceIntegrityGate."
        }
    }

    $danglingQuestionCase = New-Candidate 'dangling-question-case'
    $danglingQuestionData = ConvertTo-SchemaThree $danglingQuestionCase
    $danglingQuestionData.questionPresets[0].caseIds = @('case-missing')
    Save-Json $danglingQuestionData (Join-Path $danglingQuestionCase 'portfolio.json')
    $danglingQuestionResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $danglingQuestionCase)
    if ($danglingQuestionResult.ExitCode -eq 0 -or
            -not $danglingQuestionResult.Output.Contains('REFERENCE_DANGLING_CASE')) {
        throw 'Unknown QuestionPreset Case reference must fail ReferenceIntegrityGate.'
    }

    $casePrivacyCandidate = New-Candidate 'case-private-content'
    $casePrivacyData = ConvertTo-SchemaThree $casePrivacyCandidate
    $privateCase = New-PublicCase $casePrivacyData
    $privateCase.summary = 'Internal host 192.168.1.24'
    $casePrivacyData.cases = @($casePrivacyData.cases) + @($privateCase)
    Save-Json $casePrivacyData (Join-Path $casePrivacyCandidate 'portfolio.json')
    $casePrivacy = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $casePrivacyCandidate)
    if ($casePrivacy.ExitCode -eq 0 -or
            -not $casePrivacy.Output.Contains('PRIVACY_CONTENT_REJECTED')) {
        throw 'Every Case text field must be privacy-scanned.'
    }

    $metricLeak = New-Candidate 'codegraph-metric-leak'
    $metricLeakData = Get-Content -LiteralPath (Join-Path $metricLeak 'portfolio.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $metricLeakData.owner.summary = [Text.Encoding]::UTF8.GetString(
        [Convert]::FromBase64String('Q29kZUdyYXBoIOWkp+WcuuaZr+iKguecgSAyOC4yJQ=='))
    Save-Json $metricLeakData (Join-Path $metricLeak 'portfolio.json')
    $metricLeakResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $metricLeak)
    if ($metricLeakResult.ExitCode -eq 0 -or
            -not $metricLeakResult.Output.Contains('PRIVACY_CONTENT_REJECTED')) {
        throw 'Forbidden exact CodeGraph metrics must fail PrivacyGate.'
    }

    $metricBeforeCodeGraph = New-Candidate 'codegraph-metric-before-name'
    $metricBeforeCodeGraphData = Get-Content -LiteralPath `
        (Join-Path $metricBeforeCodeGraph 'portfolio.json') -Raw -Encoding UTF8 |
        ConvertFrom-Json
    $metricBeforeCodeGraphData.owner.summary = '28.2% measured in CodeGraph'
    Save-Json $metricBeforeCodeGraphData (Join-Path $metricBeforeCodeGraph 'portfolio.json')
    $metricBeforeCodeGraphResult = Invoke-Governance @('-Command', 'validate',
        '-Workspace', $workspace, '-Candidate', $metricBeforeCodeGraph)
    if ($metricBeforeCodeGraphResult.ExitCode -eq 0 -or
            -not $metricBeforeCodeGraphResult.Output.Contains('PRIVACY_CONTENT_REJECTED')) {
        throw 'Forbidden exact CodeGraph metrics must fail regardless of text order.'
    }

    $qualitativeCodeGraph = New-Candidate 'codegraph-qualitative'
    $qualitativeData = Get-Content -LiteralPath (Join-Path $qualitativeCodeGraph 'portfolio.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $qualitativeData.owner.summary = [Text.Encoding]::UTF8.GetString(
        [Convert]::FromBase64String(
            'Q29kZUdyYXBoIOWcqOWkp+WcuuaZr+S4reWHj+WwkeaXoOWFs+S4iuS4i+aWh++8jOS9humcgOimgeS6uuW3peWkjeaguOetlOahiOi0qOmHjw=='))
    Save-Json $qualitativeData (Join-Path $qualitativeCodeGraph 'portfolio.json')
    $qualitativeResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $qualitativeCodeGraph)
    if ($qualitativeResult.ExitCode -ne 0) {
        throw "Approved qualitative CodeGraph wording must pass: $($qualitativeResult.Output)"
    }

    $allowedProfile = New-Candidate 'allowed-csdn-profile'
    $allowedProfileData = Get-Content -LiteralPath (Join-Path $allowedProfile 'portfolio.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $allowedProfileData.owner.githubUrl = 'https://blog.csdn.net/2301_81073317'
    Save-Json $allowedProfileData (Join-Path $allowedProfile 'portfolio.json')
    $allowedProfileResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $allowedProfile)
    if ($allowedProfileResult.ExitCode -ne 0) {
        throw "The sole CSDN profile allowlist URL must pass: $($allowedProfileResult.Output)"
    }

    foreach ($urlCase in @(
        @{ Name = 'unapproved-url-host'; Url = 'https://example.com/private-profile' },
        @{ Name = 'unapproved-url-prefix'; Url = 'https://blog.csdn.net/2301_81073317.evil.example' },
        @{ Name = 'unapproved-url-query'; Url = 'https://blog.csdn.net/2301_81073317?next=evil' },
        @{ Name = 'unapproved-url-fragment'; Url = 'https://blog.csdn.net/2301_81073317#private' },
        @{ Name = 'unapproved-url-path'; Url = 'https://blog.csdn.net/2301_81073317/private' }
    )) {
        $unapprovedUrl = New-Candidate $urlCase.Name
        $unapprovedUrlData = Get-Content -LiteralPath (Join-Path $unapprovedUrl 'portfolio.json') `
            -Raw -Encoding UTF8 | ConvertFrom-Json
        $unapprovedUrlData.owner.githubUrl = $urlCase.Url
        Save-Json $unapprovedUrlData (Join-Path $unapprovedUrl 'portfolio.json')
        $unapprovedUrlResult = Invoke-Governance @('-Command', 'validate',
            '-Workspace', $workspace, '-Candidate', $unapprovedUrl)
        if ($unapprovedUrlResult.ExitCode -eq 0 -or
                -not $unapprovedUrlResult.Output.Contains('PRIVACY_CONTENT_REJECTED')) {
            throw "Non-allowlisted URL must fail PrivacyGate: $($urlCase.Name)."
        }
    }

    $emailLeak = New-Candidate 'email-leak'
    $emailLeakData = Get-Content -LiteralPath (Join-Path $emailLeak 'portfolio.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $emailLeakData.owner.email = 'owner@example.com'
    Save-Json $emailLeakData (Join-Path $emailLeak 'portfolio.json')
    $emailLeakResult = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
        '-Candidate', $emailLeak)
    if ($emailLeakResult.ExitCode -eq 0 -or
            -not $emailLeakResult.Output.Contains('PRIVACY_CONTENT_REJECTED')) {
        throw 'Email addresses must fail PrivacyGate.'
    }

    foreach ($sqlCase in @(
        @{ Name = 'raw-sql-insert'; Text = 'INSERT INTO accounts VALUES (1)' },
        @{ Name = 'raw-sql-delete'; Text = 'DELETE FROM accounts WHERE id = 1' },
        @{ Name = 'raw-sql-replace'; Text = 'REPLACE INTO accounts VALUES (1)' }
    )) {
        $sqlLeak = New-Candidate $sqlCase.Name
        $sqlLeakData = Get-Content -LiteralPath (Join-Path $sqlLeak 'portfolio.json') `
            -Raw -Encoding UTF8 | ConvertFrom-Json
        $sqlLeakData.owner.summary = $sqlCase.Text
        Save-Json $sqlLeakData (Join-Path $sqlLeak 'portfolio.json')
        $sqlLeakResult = Invoke-Governance @('-Command', 'validate',
            '-Workspace', $workspace, '-Candidate', $sqlLeak)
        if ($sqlLeakResult.ExitCode -eq 0 -or
                -not $sqlLeakResult.Output.Contains('PRIVACY_CONTENT_REJECTED')) {
            throw "Raw SQL fragment must fail PrivacyGate: $($sqlCase.Name)."
        }
    }

    $privateSource = New-Candidate 'private-source-name'
    $privateSourceData = Get-Content -LiteralPath (Join-Path $privateSource 'portfolio.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $privateSourceData.owner.summary = 'source d11_manager_test'
    Save-Json $privateSourceData (Join-Path $privateSource 'portfolio.json')
    $privateSourceResult = Invoke-Governance @('-Command', 'validate',
        '-Workspace', $workspace, '-Candidate', $privateSource)
    if ($privateSourceResult.ExitCode -eq 0 -or
            -not $privateSourceResult.Output.Contains('PRIVACY_CONTENT_REJECTED')) {
        throw 'Private source names must fail PrivacyGate independently.'
    }

    $schemaThreeReview = Invoke-Governance @('-Command', 'build-review-pack', '-Workspace', $workspace,
        '-Candidate', $schemaThree)
    if ($schemaThreeReview.ExitCode -ne 0) {
        throw "Schema 3.0 review failed: $($schemaThreeReview.Output)"
    }
    $schemaThreeReviewResult = $schemaThreeReview.Output | ConvertFrom-Json
    $schemaThreeReviewPack = Join-Path $workspace $schemaThreeReviewResult.artifacts[1]
    if (-not (Test-Path -LiteralPath (Join-Path $schemaThreeReviewPack 'cases.json') -PathType Leaf)) {
        throw 'Review output must include the public Case changes.'
    }
    $schemaThreeSummary = Get-Content -LiteralPath (Join-Path $schemaThreeReviewPack 'summary.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($schemaThreeSummary.counts.cases -ne 3) {
        throw 'Review output must include the Case count.'
    }
    $caseReviewCandidate = New-Candidate 'schema-three-case-review'
    $caseReviewData = ConvertTo-SchemaThree $caseReviewCandidate
    $reviewCase = New-PublicCase $caseReviewData
    $reviewCase.evidenceIds = @($caseReviewData.evidence[0].id)
    $caseReviewData.cases = @($caseReviewData.cases) + @($reviewCase)
    Save-Json $caseReviewData (Join-Path $caseReviewCandidate 'portfolio.json')
    $caseReview = Invoke-Governance @('-Command', 'build-review-pack', '-Workspace', $workspace,
        '-Candidate', $caseReviewCandidate)
    if ($caseReview.ExitCode -ne 0) { throw "Case review failed: $($caseReview.Output)" }
    $caseReviewResult = $caseReview.Output | ConvertFrom-Json
    $caseReviewSummary = Get-Content -LiteralPath `
        (Join-Path (Join-Path $workspace $caseReviewResult.artifacts[1]) 'summary.json') `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $evidenceId = [string]$caseReviewData.evidence[0].id
    if ($caseReviewSummary.counts.cases -ne 4 -or
            @($caseReviewSummary.caseSlugsByEvidenceId.$evidenceId) -notcontains 'case-one') {
        throw 'Review output must expose Evidence-to-Case slug changes.'
    }
    if (Test-Path -LiteralPath (Join-Path $workspace 'approvals') -PathType Container) {
        throw 'Review-pack generation must never auto-approve a candidate.'
    }
    $schemaThreeApproval = Invoke-Governance @('-Command', 'approve', '-Workspace', $workspace,
        '-Candidate', $schemaThree, '-ReviewRunId', $schemaThreeReviewResult.runId,
        '-ApprovedBy', 'owner-alias', '-PrivacyReviewId', 'PRIV-SCHEMA-3',
        '-BenchmarkRunId', 'BENCH-SCHEMA-3')
    if ($schemaThreeApproval.ExitCode -ne 0) {
        throw "Schema 3.0 approval failed: $($schemaThreeApproval.Output)"
    }
    $schemaThreeApprovalResult = $schemaThreeApproval.Output | ConvertFrom-Json
    $schemaThreeApprovalData = Get-Content -LiteralPath `
        (Join-Path $workspace $schemaThreeApprovalResult.artifacts[-1]) -Raw -Encoding UTF8 |
        ConvertFrom-Json
    $schemaThreeReleaseRoot = Join-Path $fixtureRoot 'schema-three-releases'
    New-Item -ItemType Directory -Force -Path $schemaThreeReleaseRoot | Out-Null
    $schemaThreePublish = Invoke-Governance @('-Command', 'publish', '-Workspace', $workspace,
        '-Candidate', $schemaThree, '-ApprovalId', $schemaThreeApprovalData.approvalId,
        '-ReleaseRoot', $schemaThreeReleaseRoot, '-Confirm')
    if ($schemaThreePublish.ExitCode -ne 0) {
        throw "Schema 3.0 publish fixture failed: $($schemaThreePublish.Output)"
    }
    $schemaThreePublishedVersion = Join-Path $schemaThreeReleaseRoot 'versions\2026-07-21.1'
    $schemaThreeManifestPath = Join-Path $schemaThreePublishedVersion 'manifest.json'
    $schemaThreeManifest = Get-Content -LiteralPath $schemaThreeManifestPath `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($schemaThreeManifest.schemaVersion -ne '3.0' -or
            -not ($schemaThreeManifest.counts.PSObject.Properties.Name -contains 'cases') -or
            $schemaThreeManifest.counts.cases -ne 3) {
        throw 'Schema 3.0 Manifest must explicitly bind counts.cases.'
    }
    $schemaThreeManifest.counts.PSObject.Properties.Remove('cases')
    Save-Json $schemaThreeManifest $schemaThreeManifestPath
    $missingManifestCases = Invoke-Governance @('-Command', 'verify', '-Workspace', $workspace,
        '-ReleaseRoot', $schemaThreeReleaseRoot, '-TargetVersion', '2026-07-21.1')
    if ($missingManifestCases.ExitCode -eq 0 -or
            -not $missingManifestCases.Output.Contains('VERIFY_TARGET_INVALID')) {
        throw 'Schema 3.0 verify must reject a Manifest without counts.cases.'
    }
    Remove-Item -LiteralPath (Join-Path $workspace 'audit\publish.jsonl') -Force

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
    $publishHashMismatch = New-Candidate 'publish-hash-mismatch'
    $publishHashReview = Invoke-Governance @('-Command', 'build-review-pack', '-Workspace', $workspace,
        '-Candidate', $publishHashMismatch)
    $publishHashReviewResult = $publishHashReview.Output | ConvertFrom-Json
    $publishHashApproval = Invoke-Governance @('-Command', 'approve', '-Workspace', $workspace,
        '-Candidate', $publishHashMismatch, '-ReviewRunId', $publishHashReviewResult.runId,
        '-ApprovedBy', 'owner-alias', '-PrivacyReviewId', 'PRIV-HASH', '-BenchmarkRunId', 'BENCH-HASH')
    $publishHashApprovalResult = $publishHashApproval.Output | ConvertFrom-Json
    $publishHashApprovalData = Get-Content -LiteralPath `
        (Join-Path $workspace $publishHashApprovalResult.artifacts[-1]) -Raw -Encoding UTF8 |
        ConvertFrom-Json
    Add-Content -LiteralPath (Join-Path $publishHashMismatch 'portfolio.json') -Value ' '
    $publishHashMismatchResult = Invoke-Governance @('-Command', 'publish', '-Workspace', $workspace,
        '-Candidate', $publishHashMismatch, '-ApprovalId', $publishHashApprovalData.approvalId,
        '-ReleaseRoot', $releaseRoot)
    if ($publishHashMismatchResult.ExitCode -eq 0 -or
            -not $publishHashMismatchResult.Output.Contains('PUBLISH_APPROVAL_STALE')) {
        throw 'Publish must reject candidate bytes that differ from the approved hash.'
    }

    $dryRun = Invoke-Governance @('-Command', 'publish', '-Workspace', $workspace,
        '-Candidate', $candidate, '-ApprovalId', $approvalData.approvalId, '-ReleaseRoot', $releaseRoot)
    if ($dryRun.ExitCode -ne 0 -or -not (($dryRun.Output | ConvertFrom-Json).dryRun)) { throw 'Publish must default to dry-run.' }
    if (Test-Path -LiteralPath (Join-Path $releaseRoot ('versions\' + $currentBundleVersion))) { throw 'Dry-run changed public release state.' }

    $blockedPublishAudit = Join-Path $workspace 'audit\publish.jsonl'
    New-Item -ItemType Directory -Force -Path $blockedPublishAudit | Out-Null
    $auditFailure = Invoke-Governance @('-Command', 'publish', '-Workspace', $workspace,
        '-Candidate', $candidate, '-ApprovalId', $approvalData.approvalId,
        '-ReleaseRoot', $releaseRoot, '-Confirm')
    if ($auditFailure.ExitCode -eq 0 -or -not $auditFailure.Output.Contains('PUBLISH_AUDIT_WRITE_FAILED')) {
        throw "Publish must fail closed when its security audit is unavailable: $($auditFailure.Output)"
    }
    if (Test-Path -LiteralPath (Join-Path $releaseRoot ('versions\' + $currentBundleVersion))) {
        throw 'Audit failure must happen before any public release state is written.'
    }
    Remove-Item -LiteralPath $blockedPublishAudit -Recurse -Force

    $publish = Invoke-Governance @('-Command', 'publish', '-Workspace', $workspace,
        '-Candidate', $candidate, '-ApprovalId', $approvalData.approvalId, '-ReleaseRoot', $releaseRoot, '-Confirm')
    if ($publish.ExitCode -ne 0) { throw "Publish failed: $($publish.Output)" }
    $publishedVersion = Join-Path $releaseRoot ('versions\' + $currentBundleVersion)
    if (-not (Test-Path -LiteralPath (Join-Path $publishedVersion 'manifest.json') -PathType Leaf) -or
        -not (Test-Path -LiteralPath (Join-Path $publishedVersion 'checksums.json') -PathType Leaf)) { throw 'Published bundle is incomplete.' }
    if (-not [Linq.Enumerable]::SequenceEqual([byte[]][IO.File]::ReadAllBytes((Join-Path $candidate 'portfolio.json')),
        [byte[]][IO.File]::ReadAllBytes((Join-Path $publishedVersion 'portfolio.json')))) { throw 'Publish changed approved portfolio bytes.' }
    if ((Get-Content -LiteralPath (Join-Path $releaseRoot 'active') -Raw).Trim() -ne $currentBundleVersion) { throw 'Active pointer was not switched.' }

    $listed = Invoke-Governance @('-Command', 'list', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot)
    if ($listed.ExitCode -ne 0 -or @((($listed.Output | ConvertFrom-Json).versions)).Count -ne 1) {
        throw "List must report complete published versions: $($listed.Output)"
    }
    $status = Invoke-Governance @('-Command', 'status', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot)
    $statusResult = $status.Output | ConvertFrom-Json
    if ($status.ExitCode -ne 0 -or $statusResult.activeVersion -ne $currentBundleVersion) {
        throw "Status must report the active version: $($status.Output)"
    }
    $verified = Invoke-Governance @('-Command', 'verify', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot, '-TargetVersion', $currentBundleVersion)
    if ($verified.ExitCode -ne 0 -or ($verified.Output | ConvertFrom-Json).verifiedVersion -ne $currentBundleVersion) {
        throw "Verify must validate an immutable published bundle: $($verified.Output)"
    }

    $repeat = Invoke-Governance @('-Command', 'publish', '-Workspace', $workspace,
        '-Candidate', $candidate, '-ApprovalId', $approvalData.approvalId, '-ReleaseRoot', $releaseRoot, '-Confirm')
    if ($repeat.ExitCode -ne 0) { throw 'Identical repeat publish must be idempotent.' }

    $candidate2 = New-Candidate 'candidate-2'
    foreach ($name in @('portfolio.json', 'presentation.json')) {
        $path = Join-Path $candidate2 $name
        (Get-Content -LiteralPath $path -Raw -Encoding UTF8).Replace($currentBundleVersion, $nextBundleVersion) |
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
    if ((Get-Content -LiteralPath (Join-Path $releaseRoot 'active') -Raw).Trim() -ne $currentBundleVersion) {
        throw 'Post-switch failure must atomically restore the verified old active version.'
    }

    Set-Content -LiteralPath (Join-Path $releaseRoot 'active') -Value 'broken-active' -Encoding UTF8
    $rollbackDryRun = Invoke-Governance @('-Command', 'rollback', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot, '-TargetVersion', $currentBundleVersion)
    if ($rollbackDryRun.ExitCode -ne 0 -or -not (($rollbackDryRun.Output | ConvertFrom-Json).dryRun)) { throw 'Rollback must default to dry-run.' }
    if ((Get-Content -LiteralPath (Join-Path $releaseRoot 'active') -Raw).Trim() -ne 'broken-active') { throw 'Rollback dry-run changed active.' }
    $blockedRollbackAudit = Join-Path $workspace 'audit\rollback.jsonl'
    New-Item -ItemType Directory -Force -Path $blockedRollbackAudit | Out-Null
    $rollbackAuditFailure = Invoke-Governance @('-Command', 'rollback', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot, '-TargetVersion', $currentBundleVersion, '-Confirm')
    if ($rollbackAuditFailure.ExitCode -eq 0 -or -not $rollbackAuditFailure.Output.Contains('ROLLBACK_AUDIT_WRITE_FAILED')) {
        throw "Rollback must fail closed when its security audit is unavailable: $($rollbackAuditFailure.Output)"
    }
    if ((Get-Content -LiteralPath (Join-Path $releaseRoot 'active') -Raw).Trim() -ne 'broken-active') {
        throw 'Rollback audit failure must preserve the old active pointer.'
    }
    Remove-Item -LiteralPath $blockedRollbackAudit -Recurse -Force
    @{ versions = @($currentBundleVersion) } | ConvertTo-Json |
        Set-Content -LiteralPath (Join-Path $releaseRoot 'blocked-versions.json') -Encoding UTF8
    $blockedRollback = Invoke-Governance @('-Command', 'rollback', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot, '-TargetVersion', $currentBundleVersion, '-Confirm')
    if ($blockedRollback.ExitCode -eq 0 -or -not $blockedRollback.Output.Contains('ROLLBACK_TARGET_BLOCKED')) {
        throw 'Rollback must reject versions in blocked-versions.json.'
    }
    Remove-Item -LiteralPath (Join-Path $releaseRoot 'blocked-versions.json') -Force
    $rollback = Invoke-Governance @('-Command', 'rollback', '-Workspace', $workspace,
        '-ReleaseRoot', $releaseRoot, '-TargetVersion', $currentBundleVersion, '-Confirm')
    if ($rollback.ExitCode -ne 0) { throw "Rollback failed: $($rollback.Output)" }
    if ((Get-Content -LiteralPath (Join-Path $releaseRoot 'active') -Raw).Trim() -ne $currentBundleVersion) { throw 'Rollback did not restore verified target.' }

    $compilerJar = Join-Path $repositoryRoot 'backend\target\portfolio-agent.jar'
    $localModel = Join-Path $repositoryRoot 'runtime-models\bge-small-zh-v1.5'
    if ((Test-Path -LiteralPath $compilerJar -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $localModel 'onnx\model_quantized.onnx') -PathType Leaf)) {
        $retrievalCandidate = New-Candidate 'retrieval-candidate'
        $ragFile = Join-Path $retrievalCandidate 'rag-documents.jsonl'
        $ragBuild = Invoke-CompilerMain 'com.portfolio.agent.release.RagDocumentCompilerCli' `
            $compilerJar @('--portfolio', (Join-Path $retrievalCandidate 'portfolio.json'),
                '--output', $ragFile, '--valid-from', $currentBundleVersion.Substring(0, 10))
        if ($ragBuild.ExitCode -ne 0) { throw "RAG candidate build failed: $($ragBuild.Output)" }

        $retrievalValidation = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
            '-Candidate', $retrievalCandidate, '-JarPath', $compilerJar)
        if ($retrievalValidation.ExitCode -ne 0) {
            throw "Canonical retrieval candidate failed validation: $($retrievalValidation.Output)"
        }
        $retrievalValidationResult = $retrievalValidation.Output | ConvertFrom-Json
        if ($retrievalValidationResult.runSnapshot.candidatePayloadHash -eq
                $result.runSnapshot.candidatePayloadHash) {
            throw 'Retrieval candidate hash must bind canonical RAG bytes.'
        }

        $tamperedRetrievalCandidate = New-Candidate 'retrieval-tampered'
        Copy-Item -LiteralPath $ragFile -Destination $tamperedRetrievalCandidate
        Add-Content -LiteralPath (Join-Path $tamperedRetrievalCandidate 'rag-documents.jsonl') -Value ' '
        $tamperedRetrieval = Invoke-Governance @('-Command', 'validate', '-Workspace', $workspace,
            '-Candidate', $tamperedRetrievalCandidate, '-JarPath', $compilerJar)
        if ($tamperedRetrieval.ExitCode -eq 0 -or
                -not $tamperedRetrieval.Output.Contains('RAG_CANONICAL_MISMATCH')) {
            throw 'Non-canonical RAG bytes must fail before Approval.'
        }

        $retrievalReview = Invoke-Governance @('-Command', 'build-review-pack',
            '-Workspace', $workspace, '-Candidate', $retrievalCandidate, '-JarPath', $compilerJar)
        if ($retrievalReview.ExitCode -ne 0) { throw "Retrieval review failed: $($retrievalReview.Output)" }
        $retrievalReviewResult = $retrievalReview.Output | ConvertFrom-Json
        $retrievalApproval = Invoke-Governance @('-Command', 'approve', '-Workspace', $workspace,
            '-Candidate', $retrievalCandidate, '-ReviewRunId', $retrievalReviewResult.runId,
            '-ApprovedBy', 'owner-alias', '-PrivacyReviewId', 'PRIV-C2',
            '-BenchmarkRunId', 'BENCH-C2', '-JarPath', $compilerJar)
        if ($retrievalApproval.ExitCode -ne 0) { throw "Retrieval approval failed: $($retrievalApproval.Output)" }
        $retrievalApprovalResult = $retrievalApproval.Output | ConvertFrom-Json
        $retrievalApprovalData = Get-Content -LiteralPath `
            (Join-Path $workspace $retrievalApprovalResult.artifacts[-1]) -Raw -Encoding UTF8 |
            ConvertFrom-Json
        $retrievalReleaseRoot = Join-Path $fixtureRoot 'retrieval-public-releases'
        New-Item -ItemType Directory -Force -Path $retrievalReleaseRoot | Out-Null
        $retrievalPublish = Invoke-Governance @('-Command', 'publish', '-Workspace', $workspace,
            '-Candidate', $retrievalCandidate, '-ApprovalId', $retrievalApprovalData.approvalId,
            '-ReleaseRoot', $retrievalReleaseRoot, '-JarPath', $compilerJar,
            '-ModelDirectory', $localModel, '-Confirm')
        if ($retrievalPublish.ExitCode -ne 0) { throw "Retrieval publish failed: $($retrievalPublish.Output)" }
        $retrievalVersion = Join-Path $retrievalReleaseRoot ('versions\' + $currentBundleVersion)
        $retrievalNames = @(Get-ChildItem -LiteralPath $retrievalVersion -File |
            ForEach-Object { $_.Name } | Sort-Object)
        if (($retrievalNames -join ',') -ne
                'checksums.json,keyword-index.json,manifest.json,portfolio.json,presentation.json,rag-documents.jsonl,vector-index.bin') {
            throw 'Retrieval publish did not produce the closed seven-file runtime bundle.'
        }
        if (-not [Linq.Enumerable]::SequenceEqual(
                [byte[]][IO.File]::ReadAllBytes($ragFile),
                [byte[]][IO.File]::ReadAllBytes((Join-Path $retrievalVersion 'rag-documents.jsonl')))) {
            throw 'Retrieval publish changed approved canonical RAG bytes.'
        }
        $retrievalManifest = Get-Content -LiteralPath (Join-Path $retrievalVersion 'manifest.json') `
            -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($null -eq $retrievalManifest.retrieval -or
                $retrievalManifest.candidatePayloadHash -ne
                $retrievalApprovalData.candidatePayloadHash) {
            throw 'Runtime Manifest did not bind Approval and retrieval metadata.'
        }
        $retrievalVerify = Invoke-Governance @('-Command', 'verify', '-Workspace', $workspace,
            '-ReleaseRoot', $retrievalReleaseRoot, '-TargetVersion', $currentBundleVersion)
        if ($retrievalVerify.ExitCode -ne 0) {
            throw "Seven-file retrieval release failed verify: $($retrievalVerify.Output)"
        }
    }

    Write-Output 'portfolio-governance tests passed'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
}
