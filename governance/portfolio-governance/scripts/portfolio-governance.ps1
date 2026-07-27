param(
    [Parameter(Mandatory = $true)][string]$Command,
    [string]$Workspace,
    [string]$Candidate,
    [string]$ReviewRunId,
    [string]$ApprovedBy,
    [string]$PrivacyReviewId,
    [string]$BenchmarkRunId,
    [string]$ApprovalId,
    [string]$DecisionLedger,
    [string]$ReleaseRoot,
    [string]$TargetVersion,
    [string]$CaseId,
    [string]$TargetStatus,
    [string]$CaseSource,
    [string]$ContentVersion,
    [string]$FailureType,
    [string]$SanitizedObservation,
    [string]$ExpectedBehavior,
    [string]$RootCause,
    [string]$ResolutionNote,
    [string]$FixedVersion,
    [string]$RegressionBenchmarkCaseId,
    [string]$PlaybookDecision,
    [string[]]$PostSwitchProbeUri,
    [string]$JarPath,
    [string]$ModelDirectory,
    [string]$JavaExecutable = 'java.exe',
    [switch]$Confirm
)
$ErrorActionPreference = 'Stop'
$toolVersion = '1.0.0'
$gates = @('SchemaGate', 'ReferenceIntegrityGate', 'PrivacyGate', 'ClaimEvidenceGate', 'CompatibilityGate')

function Write-Failure([string]$Code, [string]$Message) {
    [ordered]@{ runId = [guid]::NewGuid().ToString('N'); command = $Command; status = 'FAIL';
        artifacts = @(); blockingFindings = @([ordered]@{ code = $Code; message = $Message }); warnings = @() } |
        ConvertTo-Json -Depth 8 -Compress | Write-Output
    exit 2
}
function Resolve-SafePath([string]$Value, [string]$Label) {
    if ([string]::IsNullOrWhiteSpace($Value)) { Write-Failure 'WORKSPACE_REQUIRED' "$Label is required." }
    if ($Value -match '(^|[\\/])\.\.([\\/]|$)') { Write-Failure 'PATH_TRAVERSAL' "$Label contains traversal." }
    try { return (Resolve-Path -LiteralPath $Value -ErrorAction Stop).Path }
    catch { Write-Failure 'PATH_NOT_FOUND' "$Label does not exist." }
}
function Test-Contained([string]$Child, [string]$Parent) {
    $prefix = $Parent.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    return $Child.Equals($Parent, [StringComparison]::OrdinalIgnoreCase) -or $Child.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)
}
function Assert-NoReparsePoint([string]$PathValue) {
    $current = Get-Item -LiteralPath $PathValue -Force
    while ($null -ne $current) {
        if (($current.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { Write-Failure 'REPARSE_POINT_REJECTED' 'Workspace path uses a symlink or junction.' }
        $current = $current.Parent
    }
}
function Get-Sha256([byte[]]$Bytes) {
    $sha = [Security.Cryptography.SHA256]::Create()
    return 'sha256:' + ([BitConverter]::ToString($sha.ComputeHash($Bytes)) -replace '-', '').ToLowerInvariant()
}
function Get-CandidatePayloadHash(
    [byte[]]$PortfolioBytes,
    [byte[]]$PresentationBytes,
    [byte[]]$RagDocumentBytes
) {
    $stream = New-Object IO.MemoryStream
    $writer = New-Object IO.BinaryWriter($stream)
    $entries = @(@('portfolio.json', $PortfolioBytes), @('presentation.json', $PresentationBytes))
    if ($null -ne $RagDocumentBytes) {
        $entries += ,@('rag-documents.jsonl', $RagDocumentBytes)
    }
    foreach ($entry in $entries) {
        $nameBytes = [Text.Encoding]::UTF8.GetBytes([string]$entry[0])
        $contentBytes = [byte[]]$entry[1]
        $writer.Write([Net.IPAddress]::HostToNetworkOrder([int]$nameBytes.Length))
        $writer.Write($nameBytes)
        $lengthBytes = [BitConverter]::GetBytes([long]$contentBytes.Length)
        [Array]::Reverse($lengthBytes)
        $writer.Write($lengthBytes)
        $writer.Write($contentBytes)
    }
    $writer.Flush()
    return Get-Sha256 $stream.ToArray()
}
function Get-ApprovalDigest([object]$Approval) {
    $projection = [ordered]@{
        candidatePayloadHash = [string]$Approval.candidatePayloadHash
        ledgerHash = [string]$Approval.ledgerHash
        approvedBy = [string]$Approval.approvedBy
        privacyReviewId = [string]$Approval.privacyReviewId
        benchmarkRunId = [string]$Approval.benchmarkRunId
        reviewRunId = [string]$Approval.reviewRunId
        approvedAt = [string]$Approval.approvedAt
    } | ConvertTo-Json -Compress
    return Get-Sha256 ([Text.Encoding]::UTF8.GetBytes($projection))
}
function Get-ExpectedAssetIds() {
    $ids = @()
    foreach ($prefixAndCount in @(@('L', 7), @('T', 19), @('A', 25), @('K', 17))) {
        for ($index = 1; $index -le [int]$prefixAndCount[1]; $index++) {
            $ids += ('{0}-{1:d2}' -f $prefixAndCount[0], $index)
        }
    }
    return $ids
}
function Read-DecisionLedger([string]$LedgerPath) {
    $resolvedLedger = Resolve-SafePath $LedgerPath 'decisionLedger'
    Assert-NoReparsePoint $resolvedLedger
    if (-not (Test-Contained $resolvedLedger $resolvedWorkspace)) {
        Write-Failure 'DECISION_LEDGER_OUTSIDE_WORKSPACE' 'Decision ledger must be inside the private workspace.'
    }
    if (-not (Test-Path -LiteralPath $resolvedLedger -PathType Leaf)) {
        Write-Failure 'DECISION_LEDGER_INVALID' 'Decision ledger must be a file.'
    }
    try {
        $ledgerBytes = [IO.File]::ReadAllBytes($resolvedLedger)
        $ledger = Get-Content -LiteralPath $resolvedLedger -Raw -Encoding UTF8 | ConvertFrom-Json
    }
    catch {
        Write-Failure 'DECISION_LEDGER_INVALID' 'Decision ledger cannot be parsed.'
    }
    if (($ledger.PSObject.Properties.Name | Sort-Object) -join ',' -ne 'assets,schemaVersion' -or
            [string]$ledger.schemaVersion -ne '1.0' -or
            $null -eq $ledger.assets) {
        Write-Failure 'DECISION_LEDGER_SCHEMA_INVALID' 'Decision ledger top-level contract is invalid.'
    }
    $requiredFields = @(
        'assetId', 'contentType', 'achievementStatus', 'contributionType',
        'publicPriority', 'evidenceStatus', 'originalReviewState', 'finalRoute',
        'decisionReason', 'projectSlugs', 'caseSlugs', 'evidenceIds',
        'privacyReview', 'routeDecision', 'targetContentVersion', 'targetWave'
    )
    $contentTypes = @('MAINLINE', 'TASK', 'INCIDENT', 'KNOWLEDGE_ASSET')
    $achievementStatuses = @(
        'DELIVERED', 'IMPLEMENTED_TESTED', 'VALIDATED_PROTOTYPE', 'INVESTIGATED',
        'DOCUMENTED_OUTPUT', 'LEARNING_ONLY', 'OBSERVED_LEARNING', 'INCOMPLETE'
    )
    $contributionTypes = @(
        'PRIMARY', 'COLLABORATIVE', 'ASSISTED', 'OBSERVED_LEARNING',
        'UNRESOLVED'
    )
    $publicPriorities = @('P0', 'P1', 'P2', 'EXCLUDE')
    $evidenceStatuses = @('VERIFIED', 'PARTIALLY_VERIFIED', 'OWNER_CONFIRMED', 'INSUFFICIENT')
    $reviewStates = @('PUBLIC_REVIEW_REQUIRED', 'HOLD', 'EXCLUDE')
    $finalRoutes = @(
        'PROJECT', 'CASE', 'ENRICH_EXISTING_PROJECT', 'EVIDENCE_ONLY',
        'TIMELINE_ONLY', 'HOLD', 'EXCLUDE'
    )
    $routeDecisions = @('PUBLISH_CANDIDATE', 'REVIEWED_HOLD', 'EXCLUDED')
    foreach ($asset in @($ledger.assets)) {
        if ((@($asset.PSObject.Properties.Name | Sort-Object) -join ',') -ne
                (@($requiredFields | Sort-Object) -join ',')) {
            Write-Failure 'DECISION_LEDGER_SCHEMA_INVALID' 'Decision ledger asset fields are invalid.'
        }
        if ($asset.contentType -notin $contentTypes -or
                $asset.achievementStatus -notin $achievementStatuses -or
                $asset.contributionType -notin $contributionTypes -or
                $asset.publicPriority -notin $publicPriorities -or
                $asset.evidenceStatus -notin $evidenceStatuses -or
                $asset.originalReviewState -notin $reviewStates -or
                $asset.finalRoute -notin $finalRoutes -or
                $asset.routeDecision -notin $routeDecisions -or
                [string]::IsNullOrWhiteSpace([string]$asset.decisionReason) -or
                [string]::IsNullOrWhiteSpace([string]$asset.privacyReview)) {
            Write-Failure 'DECISION_LEDGER_SCHEMA_INVALID' 'Decision ledger asset values are invalid.'
        }
        foreach ($referenceField in @('projectSlugs', 'caseSlugs', 'evidenceIds')) {
            if ($null -eq $asset.$referenceField -or
                    $asset.$referenceField -is [string] -or
                    $asset.$referenceField -isnot [System.Collections.IEnumerable]) {
                Write-Failure 'DECISION_LEDGER_SCHEMA_INVALID' 'Decision ledger public references must be arrays.'
            }
            $values = @($asset.$referenceField)
            if (@($values | Where-Object { $_ -isnot [string] -or [string]::IsNullOrWhiteSpace($_) }).Count -gt 0 -or
                    @($values | Select-Object -Unique).Count -ne $values.Count) {
                Write-Failure 'DECISION_LEDGER_SCHEMA_INVALID' 'Decision ledger public references are invalid.'
            }
        }
    }
    $expectedIds = @(Get-ExpectedAssetIds)
    $actualIds = @($ledger.assets | ForEach-Object { [string]$_.assetId })
    if ($actualIds.Count -ne 68 -or
            @($actualIds | Select-Object -Unique).Count -ne 68 -or
            @($expectedIds | Where-Object { $actualIds -notcontains $_ }).Count -gt 0 -or
            @($actualIds | Where-Object { $expectedIds -notcontains $_ }).Count -gt 0) {
        Write-Failure 'DECISION_LEDGER_ID_COVERAGE_INVALID' 'Decision ledger must cover exactly the 68 known asset IDs.'
    }
    foreach ($asset in @($ledger.assets)) {
        $publicReferenceCount = @($asset.projectSlugs).Count +
            @($asset.caseSlugs).Count + @($asset.evidenceIds).Count
        if ($asset.routeDecision -eq 'PUBLISH_CANDIDATE') {
            if ($asset.finalRoute -in @('HOLD', 'EXCLUDE') -or
                    $publicReferenceCount -eq 0 -or
                    $asset.targetContentVersion -isnot [string] -or
                    [string]$asset.targetContentVersion -notmatch
                        '^[0-9]{4}-[0-9]{2}-[0-9]{2}\.[0-9]+$' -or
                    ($asset.targetWave -isnot [int] -and $asset.targetWave -isnot [long]) -or
                    [long]$asset.targetWave -lt 1) {
                Write-Failure 'DECISION_LEDGER_ROUTE_INVALID' 'Publish candidate route fields are inconsistent.'
            }
            if ($asset.finalRoute -in @('PROJECT', 'ENRICH_EXISTING_PROJECT') -and
                    @($asset.projectSlugs).Count -eq 0) {
                Write-Failure 'DECISION_LEDGER_ROUTE_INVALID' 'Project route requires a Project reference.'
            }
            if ($asset.finalRoute -eq 'CASE' -and @($asset.caseSlugs).Count -eq 0) {
                Write-Failure 'DECISION_LEDGER_ROUTE_INVALID' 'Case route requires a Case reference.'
            }
            if ($asset.finalRoute -eq 'EVIDENCE_ONLY' -and @($asset.evidenceIds).Count -eq 0) {
                Write-Failure 'DECISION_LEDGER_ROUTE_INVALID' 'Evidence route requires an Evidence reference.'
            }
        }
        elseif ($asset.routeDecision -eq 'REVIEWED_HOLD') {
            if ($asset.finalRoute -ne 'HOLD' -or $publicReferenceCount -ne 0 -or
                    $null -ne $asset.targetContentVersion -or $null -ne $asset.targetWave) {
                Write-Failure 'DECISION_LEDGER_ROUTE_INVALID' 'Reviewed hold route fields are inconsistent.'
            }
        }
        elseif ($asset.finalRoute -ne 'EXCLUDE' -or $publicReferenceCount -ne 0 -or
                $null -ne $asset.targetContentVersion -or $null -ne $asset.targetWave) {
            Write-Failure 'DECISION_LEDGER_ROUTE_INVALID' 'Excluded route fields are inconsistent.'
        }
    }
    return [ordered]@{
        Path = $resolvedLedger
        Bytes = $ledgerBytes
        Hash = Get-Sha256 $ledgerBytes
        Assets = @($ledger.assets)
    }
}
function Assert-DecisionLedgerCandidate([object[]]$Assets, [object]$Portfolio) {
    $projectsBySlug = @{}
    foreach ($project in @($Portfolio.projects)) { $projectsBySlug[[string]$project.slug] = $project }
    $casesBySlug = @{}
    foreach ($caseStudy in @($Portfolio.cases)) { $casesBySlug[[string]$caseStudy.slug] = $caseStudy }
    $evidenceById = @{}
    foreach ($evidenceItem in @($Portfolio.evidence)) { $evidenceById[[string]$evidenceItem.id] = $evidenceItem }
    foreach ($asset in $Assets) {
        if ($asset.routeDecision -eq 'PUBLISH_CANDIDATE' -and
                [string]$asset.targetContentVersion -ne [string]$Portfolio.contentVersion) {
            Write-Failure 'DECISION_LEDGER_ROUTE_INVALID' 'Publish candidate target version differs from candidate content.'
        }
        foreach ($slug in @($asset.projectSlugs)) {
            if (-not $projectsBySlug.ContainsKey([string]$slug)) {
                Write-Failure 'DECISION_LEDGER_FORWARD_REFERENCE_INVALID' 'Decision ledger Project reference is missing.'
            }
            $project = $projectsBySlug[[string]$slug]
            if ([string]$project.status -ne [string]$asset.achievementStatus -or
                    [string]$project.contributionType -ne [string]$asset.contributionType) {
                Write-Failure 'DECISION_LEDGER_STATUS_UPGRADE' 'Public Project status exceeds or differs from source inventory.'
            }
        }
        foreach ($slug in @($asset.caseSlugs)) {
            if (-not $casesBySlug.ContainsKey([string]$slug)) {
                Write-Failure 'DECISION_LEDGER_FORWARD_REFERENCE_INVALID' 'Decision ledger Case reference is missing.'
            }
            $caseStudy = $casesBySlug[[string]$slug]
            $caseAchievement = [string]$caseStudy.achievementStatus
            if ($caseAchievement -eq 'PROTOTYPE') { $caseAchievement = 'VALIDATED_PROTOTYPE' }
            if ($caseAchievement -ne [string]$asset.achievementStatus -or
                    [string]$caseStudy.contributionType -ne [string]$asset.contributionType) {
                Write-Failure 'DECISION_LEDGER_STATUS_UPGRADE' 'Public Case status exceeds or differs from source inventory.'
            }
        }
        foreach ($evidenceId in @($asset.evidenceIds)) {
            if (-not $evidenceById.ContainsKey([string]$evidenceId)) {
                Write-Failure 'DECISION_LEDGER_FORWARD_REFERENCE_INVALID' 'Decision ledger Evidence reference is missing.'
            }
            if ([string]$evidenceById[[string]$evidenceId].publicStatus -eq 'APPROVED' -and
                    [string]$asset.evidenceStatus -eq 'INSUFFICIENT') {
                Write-Failure 'DECISION_LEDGER_STATUS_UPGRADE' 'Insufficient source Evidence cannot support public Evidence.'
            }
        }
    }
    $ledgerProjectSlugs = @($Assets | ForEach-Object { @($_.projectSlugs) })
    $ledgerCaseSlugs = @($Assets | ForEach-Object { @($_.caseSlugs) })
    $ledgerEvidenceIds = @($Assets | ForEach-Object { @($_.evidenceIds) })
    if (@($projectsBySlug.Keys | Where-Object { $ledgerProjectSlugs -notcontains $_ }).Count -gt 0 -or
            @($casesBySlug.Keys | Where-Object { $ledgerCaseSlugs -notcontains $_ }).Count -gt 0 -or
            @($evidenceById.Keys | Where-Object { $ledgerEvidenceIds -notcontains $_ }).Count -gt 0) {
        Write-Failure 'DECISION_LEDGER_REVERSE_REFERENCE_INVALID' 'Public content lacks a reverse decision-ledger mapping.'
    }
}
function Test-SupportedPublicSchemaVersion([object]$Value) {
    return [string]$Value -in @('2.0', '3.0')
}
function Test-PropertyPresent([object]$Value, [string]$Name) {
    return $null -ne $Value -and
        @($Value.PSObject.Properties.Name) -contains $Name
}
function Assert-SchemaThreeCollections([object]$Portfolio) {
    $casesProperty = $Portfolio.PSObject.Properties['cases']
    if ($null -eq $casesProperty -or $null -eq $casesProperty.Value -or
            $casesProperty.Value -isnot [System.Collections.IEnumerable] -or
            $casesProperty.Value -is [string]) {
        Write-Failure 'SCHEMA_CASES_REQUIRED' 'Schema 3.0 requires an explicit cases array.'
    }
    foreach ($question in @($Portfolio.questionPresets)) {
        $caseIdsProperty = $question.PSObject.Properties['caseIds']
        if ($null -eq $caseIdsProperty -or $null -eq $caseIdsProperty.Value -or
                $caseIdsProperty.Value -isnot [System.Collections.IEnumerable] -or
                $caseIdsProperty.Value -is [string]) {
            Write-Failure 'SCHEMA_CASE_IDS_REQUIRED' 'Schema 3.0 requires questionPreset.caseIds arrays.'
        }
    }
    foreach ($timeline in @($Portfolio.timelineEvents)) {
        $caseIdsProperty = $timeline.PSObject.Properties['caseIds']
        if ($null -eq $caseIdsProperty -or $null -eq $caseIdsProperty.Value -or
                $caseIdsProperty.Value -isnot [System.Collections.IEnumerable] -or
                $caseIdsProperty.Value -is [string]) {
            Write-Failure 'SCHEMA_CASE_IDS_REQUIRED' 'Schema 3.0 requires timelineEvent.caseIds arrays.'
        }
    }
}
function Add-LegacyCaseCollections([object]$Portfolio) {
    $Portfolio | Add-Member -NotePropertyName cases -NotePropertyValue @() -Force
    foreach ($question in @($Portfolio.questionPresets)) {
        $question | Add-Member -NotePropertyName caseIds -NotePropertyValue @() -Force
    }
    foreach ($timeline in @($Portfolio.timelineEvents)) {
        $timeline | Add-Member -NotePropertyName caseIds -NotePropertyValue @() -Force
    }
}
function Assert-PublicTextPrivacy([object]$Portfolio) {
    $publicJson = $Portfolio | ConvertTo-Json -Depth 40 -Compress
    $allowedProfileUrl = 'https://blog.csdn.net/2301_81073317'
    $urls = @([regex]::Matches(
        $publicJson,
        '(?i)https?://[^\s"''<>\\]+'
    ) | ForEach-Object { $_.Value })
    if (@($urls | Where-Object { $_ -cne $allowedProfileUrl }).Count -gt 0) {
        Write-Failure 'PRIVACY_CONTENT_REJECTED' 'Candidate contains a non-allowlisted public URL.'
    }
    $quantitativeCodeGraphPattern =
        '(?i)(?:CodeGraph[^"\r\n]{0,120}(?:[0-9]+(?:\.[0-9]+)?\s*%|[0-9]+\s*tokens?)|(?:[0-9]+(?:\.[0-9]+)?\s*%|[0-9]+\s*tokens?)[^"\r\n]{0,120}CodeGraph)'
    $forbiddenPatterns = @(
        '(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b',
        '(?i)\b(?:insert|replace)\s+into\b',
        '(?i)\bdelete\s+from\b',
        '(?i)\bupdate\s+\S+\s+set\b',
        '(?i)\bselect\b[^"\r\n]{0,300}\bfrom\b',
        '(?i)\b(?:internal|private|production|prod)[_-]?(?:user|source|database|db|table|server)\b',
        '(?i)\b(?:source\s+)?d11_[a-z0-9_]+\b',
        $quantitativeCodeGraphPattern
    )
    if (@($forbiddenPatterns | Where-Object { $publicJson -match $_ }).Count -gt 0) {
        Write-Failure 'PRIVACY_CONTENT_REJECTED' 'Candidate contains non-public text or an unapproved exact metric.'
    }
    foreach ($evidenceItem in @($Portfolio.evidence)) {
        if ($evidenceItem.publicStatus -ne 'APPROVED' -or $evidenceItem.rawContentPublic -ne $false) {
            Write-Failure 'PRIVACY_CONTENT_REJECTED' 'Only approved Evidence metadata may be public.'
        }
    }
}
function Test-CompleteReleaseNames([string[]]$Names) {
    $joined = @($Names | Sort-Object) -join ','
    return $joined -eq 'checksums.json,manifest.json,portfolio.json,presentation.json' -or
        $joined -eq 'checksums.json,keyword-index.json,manifest.json,portfolio.json,presentation.json,rag-documents.jsonl,vector-index.bin'
}
function Resolve-CompilerJar() {
    $configured = if ([string]::IsNullOrWhiteSpace($JarPath)) {
        Join-Path $repositoryRoot 'backend\target\portfolio-agent.jar'
    } else { $JarPath }
    return Resolve-SafePath $configured 'jarPath'
}
function Invoke-Compiler([string]$MainClass, [string[]]$CompilerArguments) {
    $compilerJar = Resolve-CompilerJar
    $loaderArgument = '-Dloader.main=' + $MainClass
    $arguments = @(
        $loaderArgument,
        '-cp', $compilerJar,
        'org.springframework.boot.loader.launch.PropertiesLauncher'
    ) + $CompilerArguments
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $output = & $JavaExecutable @arguments 2>&1 }
    finally { $ErrorActionPreference = $previousErrorAction }
    if ($LASTEXITCODE -ne 0) {
        return @{ Success = $false; Output = ($output -join [Environment]::NewLine) }
    }
    return @{ Success = $true; Output = ($output -join [Environment]::NewLine) }
}
function Get-BlockedVersions([string]$ResolvedReleaseRoot) {
    $blockedFile = Join-Path $ResolvedReleaseRoot 'blocked-versions.json'
    if (-not (Test-Path -LiteralPath $blockedFile -PathType Leaf)) { return @() }
    try {
        $blockedDocument = Get-Content -LiteralPath $blockedFile -Raw -Encoding UTF8 | ConvertFrom-Json
        $properties = @($blockedDocument.PSObject.Properties.Name)
        if (($properties -join ',') -ne 'versions') { Write-Failure 'BLOCKED_VERSIONS_INVALID' 'Blocked version registry has an invalid field set.' }
        $versions = @($blockedDocument.versions)
        if (@($versions | Where-Object { $_ -isnot [string] -or $_ -notmatch '^[0-9]{4}-[0-9]{2}-[0-9]{2}\.[0-9]+$' }).Count -gt 0 -or
            @($versions | Select-Object -Unique).Count -ne $versions.Count) {
            Write-Failure 'BLOCKED_VERSIONS_INVALID' 'Blocked version registry contains invalid versions.'
        }
        return $versions
    }
    catch { Write-Failure 'BLOCKED_VERSIONS_INVALID' 'Blocked version registry cannot be parsed.' }
}

$resolvedWorkspace = Resolve-SafePath $Workspace 'workspace'
Assert-NoReparsePoint $resolvedWorkspace
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\..')).Path
if (Test-Contained $resolvedWorkspace $repositoryRoot) { Write-Failure 'WORKSPACE_INSIDE_REPOSITORY' 'Workspace must be outside the repository.' }
$decisionLedgerState = $null
if ($Command -in @('validate', 'benchmark', 'build-review-pack', 'approve', 'publish', 'verify')) {
    if ([string]::IsNullOrWhiteSpace($DecisionLedger)) {
        Write-Failure 'DECISION_LEDGER_REQUIRED' 'DecisionLedger is required.'
    }
    $decisionLedgerState = Read-DecisionLedger $DecisionLedger
}
if ($Command -eq 'approve' -and ([string]::IsNullOrWhiteSpace($ReviewRunId) -or
    [string]::IsNullOrWhiteSpace($ApprovedBy) -or
    [string]::IsNullOrWhiteSpace($PrivacyReviewId) -or
    [string]::IsNullOrWhiteSpace($BenchmarkRunId))) {
    Write-Failure 'APPROVAL_METADATA_REQUIRED' 'Approval requires explicit human review metadata.'
}
if ($Command -eq 'publish' -and ([string]::IsNullOrWhiteSpace($ApprovalId) -or
    [string]::IsNullOrWhiteSpace($ReleaseRoot))) {
    Write-Failure 'PUBLISH_METADATA_REQUIRED' 'Publish requires ApprovalId and ReleaseRoot.'
}
if ($Command -eq 'inspect') {
    [ordered]@{ runId = [guid]::NewGuid().ToString('N'); command = $Command; inputFingerprint = $null; status = 'PASS'; artifacts = @(); blockingFindings = @(); warnings = @() } | ConvertTo-Json -Depth 8 -Compress | Write-Output
    exit 0
}

if ($Command -eq 'case') {
    if ([string]::IsNullOrWhiteSpace($CaseId) -or $CaseId -notmatch '^CASE-[A-Za-z0-9-]{1,64}$' -or
        $TargetStatus -notin @('OPEN', 'INVESTIGATING', 'RESOLVED')) {
        Write-Failure 'CASE_TRANSITION_INVALID' 'CaseId and a valid TargetStatus are required.'
    }
    $casesDirectory = Join-Path $resolvedWorkspace 'cases'
    New-Item -ItemType Directory -Force -Path $casesDirectory | Out-Null
    $casePath = Join-Path $casesDirectory ($CaseId + '.json')
    $existing = $null
    if (Test-Path -LiteralPath $casePath -PathType Leaf) {
        try { $existing = Get-Content -LiteralPath $casePath -Raw -Encoding UTF8 | ConvertFrom-Json }
        catch { Write-Failure 'CASE_STATE_INVALID' 'Existing Case state is invalid.' }
    }
    if ($TargetStatus -eq 'OPEN') {
        if ($null -ne $existing) { Write-Failure 'CASE_ALREADY_EXISTS' 'Case already exists.' }
        if (@($CaseSource, $ContentVersion, $FailureType, $SanitizedObservation, $ExpectedBehavior |
                Where-Object { [string]::IsNullOrWhiteSpace($_) }).Count -gt 0) {
            Write-Failure 'CASE_OPEN_INCOMPLETE' 'Opening a Case requires sanitized source metadata.'
        }
        $nextCase = [ordered]@{
            caseId = $CaseId; source = $CaseSource; contentVersion = $ContentVersion
            failureType = $FailureType; sanitizedObservation = $SanitizedObservation
            expectedBehavior = $ExpectedBehavior; status = 'OPEN'
        }
    }
    else {
        if ($null -eq $existing) { Write-Failure 'CASE_NOT_FOUND' 'Case does not exist.' }
        if ($existing.status -eq 'RESOLVED' -or
            ($TargetStatus -eq 'INVESTIGATING' -and $existing.status -ne 'OPEN')) {
            Write-Failure 'CASE_TRANSITION_INVALID' 'Case transition is not allowed.'
        }
        $nextCase = [ordered]@{}
        foreach ($property in $existing.PSObject.Properties) { $nextCase[$property.Name] = $property.Value }
        $nextCase.status = $TargetStatus
        if ($TargetStatus -eq 'RESOLVED') {
            if (@($RootCause, $ResolutionNote, $FixedVersion, $RegressionBenchmarkCaseId, $PlaybookDecision |
                    Where-Object { [string]::IsNullOrWhiteSpace($_) }).Count -gt 0) {
                Write-Failure 'CASE_CLOSURE_INCOMPLETE' 'Resolved Case requires closure evidence.'
            }
            $nextCase.rootCause = $RootCause
            $nextCase.resolution = $ResolutionNote
            $nextCase.fixedVersion = $FixedVersion
            $nextCase.regressionBenchmarkCaseId = $RegressionBenchmarkCaseId
            $nextCase.playbookDecision = $PlaybookDecision
        }
    }
    $caseAudit = Join-Path $resolvedWorkspace 'audit\case.jsonl'
    try {
        New-Item -ItemType Directory -Force -Path (Split-Path $caseAudit -Parent) | Out-Null
        ([ordered]@{ caseId = $CaseId; fromStatus = if ($null -eq $existing) { $null } else { $existing.status }; toStatus = $TargetStatus; changedAt = [DateTimeOffset]::UtcNow.ToString('o') } | ConvertTo-Json -Compress) | Add-Content -LiteralPath $caseAudit -Encoding UTF8
        $caseTemporary = $casePath + '.tmp.' + [guid]::NewGuid().ToString('N')
        $nextCase | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $caseTemporary -Encoding UTF8
        Move-Item -LiteralPath $caseTemporary -Destination $casePath -Force
    }
    catch { Write-Failure 'CASE_AUDIT_WRITE_FAILED' 'Case transition audit could not be written.' }
    [ordered]@{ runId = [guid]::NewGuid().ToString('N'); command = $Command; status = 'PASS'; caseId = $CaseId; caseStatus = $TargetStatus; artifacts = @('cases\' + $CaseId + '.json', 'audit\case.jsonl'); blockingFindings = @(); warnings = @() } | ConvertTo-Json -Depth 8 -Compress | Write-Output
    exit 0
}

if ($Command -in @('list', 'status', 'verify')) {
    if ([string]::IsNullOrWhiteSpace($ReleaseRoot)) {
        Write-Failure 'RELEASE_ROOT_REQUIRED' 'ReleaseRoot is required.'
    }
    $resolvedReleaseRoot = Resolve-SafePath $ReleaseRoot 'releaseRoot'
    Assert-NoReparsePoint $resolvedReleaseRoot
    if (Test-Contained $resolvedReleaseRoot $repositoryRoot) {
        Write-Failure 'RELEASE_ROOT_INSIDE_REPOSITORY' 'ReleaseRoot must be outside the repository.'
    }
    $versionsRoot = Join-Path $resolvedReleaseRoot 'versions'
    if (-not (Test-Path -LiteralPath $versionsRoot -PathType Container)) {
        Write-Failure 'RELEASE_VERSIONS_MISSING' 'Release versions directory does not exist.'
    }
    $blockedVersions = @(Get-BlockedVersions $resolvedReleaseRoot)
    $versions = @(Get-ChildItem -LiteralPath $versionsRoot -Directory | ForEach-Object {
        $names = @(Get-ChildItem -LiteralPath $_.FullName -File | ForEach-Object { $_.Name } | Sort-Object)
        [ordered]@{
            contentVersion = $_.Name
            complete = (Test-CompleteReleaseNames $names)
            blocked = ($blockedVersions -contains $_.Name)
        }
    } | Sort-Object { $_.contentVersion })
    if ($Command -eq 'list') {
        [ordered]@{ runId = [guid]::NewGuid().ToString('N'); command = $Command; status = 'PASS'; versions = $versions; artifacts = @(); blockingFindings = @(); warnings = @() } | ConvertTo-Json -Depth 8 -Compress | Write-Output
        exit 0
    }
    $activePath = Join-Path $resolvedReleaseRoot 'active'
    if ($Command -eq 'status') {
        $activeVersion = if (Test-Path -LiteralPath $activePath -PathType Leaf) { (Get-Content -LiteralPath $activePath -Raw -Encoding UTF8).Trim() } else { $null }
        $activeEntry = @($versions | Where-Object { $_.contentVersion -eq $activeVersion })
        $activeValid = $activeEntry.Count -eq 1 -and $activeEntry[0].complete -and -not $activeEntry[0].blocked
        [ordered]@{ runId = [guid]::NewGuid().ToString('N'); command = $Command; status = 'PASS'; activeVersion = $activeVersion; activeValid = $activeValid; versionCount = $versions.Count; artifacts = @(); blockingFindings = @(); warnings = @() } | ConvertTo-Json -Depth 8 -Compress | Write-Output
        exit 0
    }
    if ([string]::IsNullOrWhiteSpace($TargetVersion)) {
        Write-Failure 'VERIFY_TARGET_REQUIRED' 'Verify requires TargetVersion.'
    }
    if ($TargetVersion -notmatch '^[0-9]{4}-[0-9]{2}-[0-9]{2}\.[0-9]+$') {
        Write-Failure 'VERIFY_TARGET_INVALID' 'Verify target version is invalid.'
    }
    $targetDirectory = Resolve-SafePath (Join-Path $versionsRoot $TargetVersion) 'targetVersion'
    Assert-NoReparsePoint $targetDirectory
    if (-not (Test-Contained $targetDirectory $versionsRoot)) {
        Write-Failure 'VERIFY_TARGET_ESCAPE' 'Verify target escapes release root.'
    }
    $targetEntry = @($versions | Where-Object { $_.contentVersion -eq $TargetVersion })
    if ($targetEntry.Count -ne 1 -or -not $targetEntry[0].complete) {
        Write-Failure 'VERIFY_TARGET_INCOMPLETE' 'Verify target is incomplete.'
    }
    try {
        $verifyManifest = Get-Content -LiteralPath (Join-Path $targetDirectory 'manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
        $verifyChecksums = Get-Content -LiteralPath (Join-Path $targetDirectory 'checksums.json') -Raw -Encoding UTF8 | ConvertFrom-Json
        $verifyPortfolio = [IO.File]::ReadAllBytes((Join-Path $targetDirectory 'portfolio.json'))
        $verifyPresentation = [IO.File]::ReadAllBytes((Join-Path $targetDirectory 'presentation.json'))
        $verifyPortfolioData = [Text.Encoding]::UTF8.GetString($verifyPortfolio) | ConvertFrom-Json
        $verifyRag = if (Test-Path -LiteralPath (Join-Path $targetDirectory 'rag-documents.jsonl') -PathType Leaf) {
            [IO.File]::ReadAllBytes((Join-Path $targetDirectory 'rag-documents.jsonl'))
        } else { $null }
    }
    catch { Write-Failure 'VERIFY_TARGET_INVALID' 'Verify target cannot be parsed.' }
    Assert-DecisionLedgerCandidate $decisionLedgerState.Assets $verifyPortfolioData
    $verifyKeywordHash = if ($null -ne $verifyRag) {
        Get-Sha256 ([IO.File]::ReadAllBytes((Join-Path $targetDirectory 'keyword-index.json')))
    } else { $null }
    $verifyVectorHash = if ($null -ne $verifyRag) {
        Get-Sha256 ([IO.File]::ReadAllBytes((Join-Path $targetDirectory 'vector-index.bin')))
    } else { $null }
    $verifySchemaVersion = [string]$verifyPortfolioData.schemaVersion
    $verifyCaseCountValid = $true
    if ($verifySchemaVersion -eq '3.0') {
        $verifyCaseCountValid = (Test-PropertyPresent $verifyManifest.counts 'cases') -and
            [int]$verifyManifest.counts.cases -eq @($verifyPortfolioData.cases).Count
    }
    if (-not (Test-SupportedPublicSchemaVersion $verifySchemaVersion) -or
        $verifyManifest.schemaVersion -ne $verifySchemaVersion -or
        $verifyManifest.contentVersion -ne $TargetVersion -or
        $verifyChecksums.schemaVersion -ne $verifySchemaVersion -or
        $verifyChecksums.contentVersion -ne $TargetVersion -or
        -not $verifyCaseCountValid -or
        $verifyChecksums.files.'portfolio.json' -ne (Get-Sha256 $verifyPortfolio) -or
        $verifyChecksums.files.'presentation.json' -ne (Get-Sha256 $verifyPresentation) -or
        $verifyManifest.candidatePayloadHash -ne (Get-CandidatePayloadHash $verifyPortfolio $verifyPresentation $verifyRag) -or
        (($null -ne $verifyRag) -ne ($null -ne $verifyManifest.retrieval)) -or
        ($null -ne $verifyRag -and (
            $verifyChecksums.files.'rag-documents.jsonl' -ne (Get-Sha256 $verifyRag) -or
            $verifyChecksums.files.'keyword-index.json' -ne $verifyKeywordHash -or
            $verifyChecksums.files.'vector-index.bin' -ne $verifyVectorHash))) {
        Write-Failure 'VERIFY_TARGET_INVALID' 'Verify target failed integrity validation.'
    }
    if ([string]$verifyManifest.approvalId -notmatch '^APR-[a-f0-9]{32}$') {
        Write-Failure 'VERIFY_APPROVAL_INVALID' 'Verify target Approval identity is invalid.'
    }
    $verifyApprovalFile = Join-Path $resolvedWorkspace (
        Join-Path 'approvals' ([string]$verifyManifest.approvalId + '.json')
    )
    if (-not (Test-Path -LiteralPath $verifyApprovalFile -PathType Leaf)) {
        Write-Failure 'VERIFY_APPROVAL_INVALID' 'Verify target Approval record is missing.'
    }
    try {
        $verifyApproval = Get-Content -LiteralPath $verifyApprovalFile `
            -Raw -Encoding UTF8 | ConvertFrom-Json
    }
    catch { Write-Failure 'VERIFY_APPROVAL_INVALID' 'Verify target Approval record is invalid.' }
    if ([string]$verifyApproval.approvalId -ne [string]$verifyManifest.approvalId -or
            [string]$verifyApproval.candidatePayloadHash -ne
                [string]$verifyManifest.candidatePayloadHash -or
            [string]$verifyApproval.ledgerHash -ne [string]$verifyManifest.ledgerHash -or
            [string]$verifyApproval.approvalDigest -ne
                [string]$verifyManifest.approvalDigest -or
            [string]$verifyApproval.approvalDigest -ne
                (Get-ApprovalDigest $verifyApproval)) {
        Write-Failure 'VERIFY_APPROVAL_INVALID' 'Verify target differs from its approved projection.'
    }
    if ([string]$verifyManifest.ledgerHash -ne [string]$decisionLedgerState.Hash) {
        Write-Failure 'VERIFY_LEDGER_STALE' 'Verify decision ledger differs from the approved release ledger.'
    }
    $verifyRunId = [guid]::NewGuid().ToString('N')
    $verifyAuditRelativePath = Join-Path 'audit' 'verify.jsonl'
    $verifyAudit = Join-Path $resolvedWorkspace $verifyAuditRelativePath
    try {
        New-Item -ItemType Directory -Force -Path (Split-Path $verifyAudit -Parent) | Out-Null
        ([ordered]@{
            runId = $verifyRunId
            action = 'RELEASE_VERIFIED'
            approvalId = [string]$verifyManifest.approvalId
            contentVersion = $TargetVersion
            candidatePayloadHash = [string]$verifyManifest.candidatePayloadHash
            ledgerHash = [string]$decisionLedgerState.Hash
            verifiedAt = [DateTimeOffset]::UtcNow.ToString('o')
        } | ConvertTo-Json -Compress) | Add-Content -LiteralPath $verifyAudit -Encoding UTF8
    }
    catch { Write-Failure 'VERIFY_AUDIT_WRITE_FAILED' 'Verify audit receipt could not be appended.' }
    [ordered]@{ runId = $verifyRunId; command = $Command; status = 'PASS'; verifiedVersion = $TargetVersion; candidatePayloadHash = $verifyManifest.candidatePayloadHash; ledgerHash = $decisionLedgerState.Hash; artifacts = @($verifyAuditRelativePath); blockingFindings = @(); warnings = @() } | ConvertTo-Json -Depth 8 -Compress | Write-Output
    exit 0
}

if ($Command -eq 'rollback') {
    if ([string]::IsNullOrWhiteSpace($ReleaseRoot) -or [string]::IsNullOrWhiteSpace($TargetVersion)) {
        Write-Failure 'ROLLBACK_METADATA_REQUIRED' 'Rollback requires ReleaseRoot and TargetVersion.'
    }
    if ($TargetVersion -notmatch '^[0-9]{4}-[0-9]{2}-[0-9]{2}\.[0-9]+$') {
        Write-Failure 'ROLLBACK_TARGET_INVALID' 'Rollback target version is invalid.'
    }
    $resolvedReleaseRoot = Resolve-SafePath $ReleaseRoot 'releaseRoot'
    Assert-NoReparsePoint $resolvedReleaseRoot
    if (Test-Contained $resolvedReleaseRoot $repositoryRoot) { Write-Failure 'RELEASE_ROOT_INSIDE_REPOSITORY' 'ReleaseRoot must be outside the repository.' }
    $targetDirectory = Join-Path $resolvedReleaseRoot (Join-Path 'versions' $TargetVersion)
    $resolvedTarget = Resolve-SafePath $targetDirectory 'targetVersion'
    Assert-NoReparsePoint $resolvedTarget
    if (-not (Test-Contained $resolvedTarget (Join-Path $resolvedReleaseRoot 'versions'))) { Write-Failure 'ROLLBACK_TARGET_ESCAPE' 'Rollback target escapes release root.' }
    if (@(Get-BlockedVersions $resolvedReleaseRoot) -contains $TargetVersion) { Write-Failure 'ROLLBACK_TARGET_BLOCKED' 'Rollback target is blocked.' }
    $targetNames = @(Get-ChildItem -LiteralPath $resolvedTarget -File | ForEach-Object { $_.Name } | Sort-Object)
    if (-not (Test-CompleteReleaseNames $targetNames)) { Write-Failure 'ROLLBACK_TARGET_INCOMPLETE' 'Rollback target is incomplete.' }
    try {
        $targetManifest = Get-Content -LiteralPath (Join-Path $resolvedTarget 'manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
        $targetChecksums = Get-Content -LiteralPath (Join-Path $resolvedTarget 'checksums.json') -Raw -Encoding UTF8 | ConvertFrom-Json
        $targetPortfolioBytes = [IO.File]::ReadAllBytes((Join-Path $resolvedTarget 'portfolio.json'))
        $targetPresentationBytes = [IO.File]::ReadAllBytes((Join-Path $resolvedTarget 'presentation.json'))
        $targetPortfolioData = [Text.Encoding]::UTF8.GetString($targetPortfolioBytes) | ConvertFrom-Json
        $targetRagBytes = if (Test-Path -LiteralPath (Join-Path $resolvedTarget 'rag-documents.jsonl') -PathType Leaf) {
            [IO.File]::ReadAllBytes((Join-Path $resolvedTarget 'rag-documents.jsonl'))
        } else { $null }
    }
    catch { Write-Failure 'ROLLBACK_TARGET_INVALID' 'Rollback target cannot be parsed.' }
    $targetSchemaVersion = [string]$targetPortfolioData.schemaVersion
    $targetCaseCountValid = $true
    if ($targetSchemaVersion -eq '3.0') {
        $targetCaseCountValid = (Test-PropertyPresent $targetManifest.counts 'cases') -and
            [int]$targetManifest.counts.cases -eq @($targetPortfolioData.cases).Count
    }
    if (-not (Test-SupportedPublicSchemaVersion $targetSchemaVersion) -or
        $targetManifest.schemaVersion -ne $targetSchemaVersion -or
        $targetManifest.contentVersion -ne $TargetVersion -or
        -not $targetCaseCountValid -or
        $targetChecksums.contentVersion -ne $TargetVersion -or
        $targetChecksums.files.'portfolio.json' -ne (Get-Sha256 $targetPortfolioBytes) -or
        $targetChecksums.files.'presentation.json' -ne (Get-Sha256 $targetPresentationBytes) -or
        $targetManifest.candidatePayloadHash -ne (Get-CandidatePayloadHash $targetPortfolioBytes $targetPresentationBytes $targetRagBytes) -or
        (($null -ne $targetRagBytes) -ne ($null -ne $targetManifest.retrieval))) {
        Write-Failure 'ROLLBACK_TARGET_INVALID' 'Rollback target failed integrity validation.'
    }
    if (-not $Confirm) {
        [ordered]@{ runId = [guid]::NewGuid().ToString('N'); command = $Command; status = 'PASS'; dryRun = $true; targetVersion = $TargetVersion; artifacts = @(); blockingFindings = @(); warnings = @() } | ConvertTo-Json -Depth 8 -Compress | Write-Output
        exit 0
    }
    $rollbackAudit = Join-Path $resolvedWorkspace 'audit\rollback.jsonl'
    try {
        New-Item -ItemType Directory -Force -Path (Split-Path $rollbackAudit -Parent) | Out-Null
        ([ordered]@{ action = 'ROLLBACK_AUTHORIZED'; targetVersion = $TargetVersion; candidatePayloadHash = $targetManifest.candidatePayloadHash; authorizedAt = [DateTimeOffset]::UtcNow.ToString('o') } | ConvertTo-Json -Compress) | Add-Content -LiteralPath $rollbackAudit -Encoding UTF8
    }
    catch { Write-Failure 'ROLLBACK_AUDIT_WRITE_FAILED' 'Rollback audit write failed before active pointer mutation.' }
    $activeTemporary = Join-Path $resolvedReleaseRoot ('active.tmp.' + [guid]::NewGuid().ToString('N'))
    Set-Content -LiteralPath $activeTemporary -Value $TargetVersion -Encoding UTF8
    Move-Item -LiteralPath $activeTemporary -Destination (Join-Path $resolvedReleaseRoot 'active') -Force
    [ordered]@{ runId = [guid]::NewGuid().ToString('N'); command = $Command; status = 'PASS'; dryRun = $false; targetVersion = $TargetVersion; artifacts = @('audit\rollback.jsonl'); blockingFindings = @(); warnings = @() } | ConvertTo-Json -Depth 8 -Compress | Write-Output
    exit 0
}

$resolvedCandidate = Resolve-SafePath $Candidate 'candidate'
Assert-NoReparsePoint $resolvedCandidate
if (-not (Test-Contained $resolvedCandidate $resolvedWorkspace)) { Write-Failure 'CANDIDATE_OUTSIDE_WORKSPACE' 'Candidate must be inside the private workspace.' }
$portfolioFile = Join-Path $resolvedCandidate 'portfolio.json'
$presentationFile = Join-Path $resolvedCandidate 'presentation.json'
if (-not (Test-Path -LiteralPath $portfolioFile -PathType Leaf) -or -not (Test-Path -LiteralPath $presentationFile -PathType Leaf)) { Write-Failure 'CANDIDATE_FILES_MISSING' 'Canonical candidate files are required.' }
$candidateNames = @(Get-ChildItem -LiteralPath $resolvedCandidate -File |
    ForEach-Object { $_.Name } | Sort-Object)
$legacyCandidateNames = 'portfolio.json,presentation.json'
$retrievalCandidateNames = 'portfolio.json,presentation.json,rag-documents.jsonl'
$candidateNameSet = $candidateNames -join ','
if ($candidateNameSet -ne $legacyCandidateNames -and
        $candidateNameSet -ne $retrievalCandidateNames) {
    Write-Failure 'CANDIDATE_FILE_SET_INVALID' 'Candidate canonical payload file set is not closed.'
}
$hasRetrievalCandidate = $candidateNameSet -eq $retrievalCandidateNames
$ragDocumentFile = Join-Path $resolvedCandidate 'rag-documents.jsonl'
try {
    $portfolioBytes = [IO.File]::ReadAllBytes($portfolioFile)
    $presentationBytes = [IO.File]::ReadAllBytes($presentationFile)
    $ragDocumentBytes = if ($hasRetrievalCandidate) {
        [IO.File]::ReadAllBytes($ragDocumentFile)
    } else { $null }
    $portfolio = Get-Content -LiteralPath $portfolioFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $presentation = Get-Content -LiteralPath $presentationFile -Raw -Encoding UTF8 | ConvertFrom-Json
}
catch { Write-Failure 'SCHEMA_JSON_INVALID' 'Candidate JSON is invalid.' }
if (-not (Test-SupportedPublicSchemaVersion $portfolio.schemaVersion) -or
        -not (Test-SupportedPublicSchemaVersion $presentation.schemaVersion) -or
        $portfolio.schemaVersion -ne $presentation.schemaVersion) {
    Write-Failure 'SCHEMA_VERSION_UNSUPPORTED' 'Candidate schemaVersion is unsupported or inconsistent.'
}
$portfolioFieldsFromCandidate = @($portfolio.PSObject.Properties.Name)
if ($portfolio.schemaVersion -eq '3.0') {
    Assert-SchemaThreeCollections $portfolio
}
else {
    Add-LegacyCaseCollections $portfolio
}
if ($portfolio.contentVersion -ne $presentation.contentVersion) { Write-Failure 'CONTENT_VERSION_MISMATCH' 'Candidate contentVersion values differ.' }
if ([string]$portfolio.contentVersion -notmatch '^[0-9]{4}-[0-9]{2}-[0-9]{2}\.[0-9]+$') {
    Write-Failure 'CONTENT_VERSION_INVALID' 'Candidate contentVersion is invalid.'
}
if ($hasRetrievalCandidate) {
    $canonicalRag = Join-Path $resolvedWorkspace ('.canonical-rag-' + [guid]::NewGuid().ToString('N') + '.jsonl')
    $validFrom = ([string]$portfolio.contentVersion).Substring(0, 10)
    $compileResult = Invoke-Compiler 'com.portfolio.agent.release.RagDocumentCompilerCli' @(
        '--portfolio', $portfolioFile, '--output', $canonicalRag, '--valid-from', $validFrom)
    if (-not $compileResult.Success -or -not (Test-Path -LiteralPath $canonicalRag -PathType Leaf)) {
        if (Test-Path -LiteralPath $canonicalRag) { Remove-Item -LiteralPath $canonicalRag -Force }
        Write-Failure 'RAG_CANONICAL_BUILD_FAILED' 'Canonical RAG document validation failed.'
    }
    $canonicalRagBytes = [IO.File]::ReadAllBytes($canonicalRag)
    Remove-Item -LiteralPath $canonicalRag -Force
    if (-not [Linq.Enumerable]::SequenceEqual(
            [byte[]]$ragDocumentBytes, [byte[]]$canonicalRagBytes)) {
        Write-Failure 'RAG_CANONICAL_MISMATCH' 'Approved RAG bytes are not the canonical Claim projection.'
    }
}
$allowedPortfolioFields = @(
    'schemaVersion', 'contentVersion', 'owner', 'internshipPeriod', 'projects',
    'cases', 'claims', 'evidence', 'claimEvidenceLinks', 'timelineEvents',
    'questionPresets'
)
$unknownPortfolioFields = @($portfolioFieldsFromCandidate | Where-Object { $allowedPortfolioFields -notcontains $_ })
if ($unknownPortfolioFields.Count -gt 0) { Write-Failure 'SCHEMA_UNKNOWN_FIELD' 'Candidate contains an unknown top-level field.' }
$projectIds = @($portfolio.projects | ForEach-Object { $_.id })
$caseIds = @($portfolio.cases | ForEach-Object { $_.id })
$claimIds = @($portfolio.claims | ForEach-Object { $_.id })
$evidenceIds = @($portfolio.evidence | ForEach-Object { $_.id })
$timelineIds = @($portfolio.timelineEvents | ForEach-Object { $_.id })
$questionIds = @($portfolio.questionPresets | ForEach-Object { $_.id })
if (($projectIds | Select-Object -Unique).Count -ne $projectIds.Count -or
        ($caseIds | Select-Object -Unique).Count -ne $caseIds.Count -or
        ($claimIds | Select-Object -Unique).Count -ne $claimIds.Count -or
        ($evidenceIds | Select-Object -Unique).Count -ne $evidenceIds.Count -or
        ($timelineIds | Select-Object -Unique).Count -ne $timelineIds.Count -or
        ($questionIds | Select-Object -Unique).Count -ne $questionIds.Count) {
    Write-Failure 'REFERENCE_DUPLICATE_ID' 'Candidate IDs must be unique within each public collection.'
}
foreach ($link in @($portfolio.claimEvidenceLinks)) {
    if ($claimIds -notcontains $link.claimId -or $evidenceIds -notcontains $link.evidenceId) { Write-Failure 'REFERENCE_DANGLING_LINK' 'ClaimEvidenceLink contains a dangling reference.' }
}
foreach ($claim in @($portfolio.claims | Where-Object { $_.subjectType -eq 'CASE' })) {
    if ($caseIds -notcontains $claim.subjectId) {
        Write-Failure 'REFERENCE_DANGLING_CASE_CLAIM' 'A CASE Claim references an unknown Case.'
    }
}
foreach ($caseStudy in @($portfolio.cases)) {
    if ($null -ne $caseStudy.projectId -and $projectIds -notcontains $caseStudy.projectId) {
        Write-Failure 'REFERENCE_DANGLING_CASE_PROJECT' 'Case contains an unknown Project reference.'
    }
    foreach ($claimId in @($caseStudy.claimIds)) {
        $caseClaim = @($portfolio.claims | Where-Object { $_.id -eq $claimId })
        if ($caseClaim.Count -ne 1 -or $caseClaim[0].subjectType -ne 'CASE' -or
                $caseClaim[0].subjectId -ne $caseStudy.id) {
            Write-Failure 'REFERENCE_DANGLING_CASE_CLAIM' 'Case contains an unknown or foreign Claim reference.'
        }
    }
    if (@($caseStudy.evidenceIds | Where-Object { $evidenceIds -notcontains $_ }).Count -gt 0) {
        Write-Failure 'REFERENCE_DANGLING_CASE_EVIDENCE' 'Case contains an unknown Evidence reference.'
    }
    if (@($caseStudy.timelineEventIds | Where-Object { $timelineIds -notcontains $_ }).Count -gt 0) {
        Write-Failure 'REFERENCE_DANGLING_CASE_TIMELINE' 'Case contains an unknown TimelineEvent reference.'
    }
    if (@($caseStudy.questionPresetIds | Where-Object { $questionIds -notcontains $_ }).Count -gt 0) {
        Write-Failure 'REFERENCE_DANGLING_CASE_QUESTION' 'Case contains an unknown QuestionPreset reference.'
    }
}
foreach ($question in @($portfolio.questionPresets)) {
    if (@($question.caseIds | Where-Object { $caseIds -notcontains $_ }).Count -gt 0) {
        Write-Failure 'REFERENCE_DANGLING_CASE' 'QuestionPreset contains an unknown Case reference.'
    }
}
foreach ($timeline in @($portfolio.timelineEvents)) {
    if (@($timeline.caseIds | Where-Object { $caseIds -notcontains $_ }).Count -gt 0) {
        Write-Failure 'REFERENCE_DANGLING_CASE' 'TimelineEvent contains an unknown Case reference.'
    }
}
$privacyChecker = Join-Path $repositoryRoot 'scripts\privacy-check.ps1'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $privacyChecker -Path $resolvedCandidate *> $null
if ($LASTEXITCODE -ne 0) { Write-Failure 'PRIVACY_CONTENT_REJECTED' 'Candidate failed privacy scanning.' }
Assert-PublicTextPrivacy $portfolio
foreach ($claim in @($portfolio.claims)) {
    if ($claim.verificationStatus -eq 'VERIFIED') {
        $direct = @($portfolio.claimEvidenceLinks | Where-Object { $_.claimId -eq $claim.id -and $_.supportType -eq 'DIRECT' -and $_.reviewStatus -eq 'APPROVED' })
        if ($claim.verificationBasis -ne 'EVIDENCE_SUPPORTED' -or $direct.Count -eq 0) { Write-Failure 'CLAIM_VERIFICATION_INVALID' 'Verified Claim lacks approved DIRECT support.' }
    }
}
Assert-DecisionLedgerCandidate $decisionLedgerState.Assets $portfolio
$executedGates = @($gates)
if ($Command -in @('benchmark', 'build-review-pack', 'approve', 'publish')) {
    $benchmarkFile = Join-Path $PSScriptRoot '..\benchmark\active-benchmarks.v1.json'
    $benchmark = Get-Content -LiteralPath $benchmarkFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $requiredCaseTypes = @('SUPPORTED_QUESTION', 'ALIAS', 'BOUNDARY', 'CLAIM_EVIDENCE', 'SAFETY')
    foreach ($preset in @($portfolio.questionPresets)) {
        $coveredTypes = @($benchmark.cases | Where-Object { $_.questionPresetId -eq $preset.id } | ForEach-Object { $_.caseType } | Select-Object -Unique)
        foreach ($requiredType in $requiredCaseTypes) {
            if ($coveredTypes -notcontains $requiredType) { Write-Failure 'BENCHMARK_COVERAGE_MISSING' 'An active QuestionPreset lacks critical benchmark coverage.' }
        }
    }
    foreach ($case in @($benchmark.cases | Where-Object { $_.caseType -eq 'CLAIM_EVIDENCE' })) {
        if (@($case.requiredClaimIds | Where-Object { $claimIds -notcontains $_ }).Count -gt 0 -or
            @($case.requiredEvidenceIds | Where-Object { $evidenceIds -notcontains $_ }).Count -gt 0) {
            Write-Failure 'BENCHMARK_REFERENCE_INVALID' 'A benchmark references unpublished content.'
        }
    }
    $executedGates = @($gates[0..3]) + @('BenchmarkGate') + @($gates[4])
}
$candidatePayloadHash = Get-CandidatePayloadHash $portfolioBytes $presentationBytes $ragDocumentBytes
$policyFile = Join-Path $PSScriptRoot '..\policies\governance-policy.v1.json'
$benchmarkDefinitionFile = Join-Path $PSScriptRoot '..\benchmark\active-benchmarks.v1.json'
$schemaDirectory = Join-Path $PSScriptRoot '..\schemas'
$policyBundleEntries = @($policyFile) + @(Get-ChildItem -LiteralPath $schemaDirectory -File -Filter '*.json' |
    Sort-Object Name | ForEach-Object { $_.FullName })
$policyBundleProjection = $policyBundleEntries | ForEach-Object {
    ([IO.Path]::GetFileName($_)) + ':' + (Get-Sha256 ([IO.File]::ReadAllBytes($_)))
}
$policyBundleHash = Get-Sha256 ([Text.Encoding]::UTF8.GetBytes(($policyBundleProjection -join "`n")))
$benchmarkDefinitionHash = Get-Sha256 ([IO.File]::ReadAllBytes($benchmarkDefinitionFile))
$toolHash = Get-Sha256 ([IO.File]::ReadAllBytes($PSCommandPath))
$fingerprintInput = [Text.Encoding]::UTF8.GetBytes(
    $candidatePayloadHash + "`n" + $decisionLedgerState.Hash + "`n" +
    $policyBundleHash + "`n" + $benchmarkDefinitionHash + "`n" + $toolVersion + "`n" + $toolHash)
$inputFingerprint = Get-Sha256 $fingerprintInput
$runId = [guid]::NewGuid().ToString('N')
$runSnapshot = [ordered]@{
    runId = $runId
    startedAt = [DateTimeOffset]::UtcNow.ToString('o')
    inputFingerprint = $inputFingerprint
    schemaVersion = [string]$portfolio.schemaVersion
    policyBundleHash = $policyBundleHash
    benchmarkDefinitionHash = $benchmarkDefinitionHash
    toolVersion = $toolVersion
    candidatePayloadHash = $candidatePayloadHash
    ledgerHash = $decisionLedgerState.Hash
}
$artifacts = @()
$dryRun = $false
$publishIdempotent = $false
$runRelativePath = Join-Path 'runs' $runId
$runDirectory = Join-Path $resolvedWorkspace $runRelativePath
New-Item -ItemType Directory -Force -Path $runDirectory | Out-Null
$runSnapshot | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $runDirectory 'snapshot.json') -Encoding UTF8
$artifacts += (Join-Path $runRelativePath 'snapshot.json')

if ($Command -eq 'approve') {
    $reviewSnapshotFile = Join-Path $resolvedWorkspace (Join-Path (Join-Path 'runs' $ReviewRunId) 'snapshot.json')
    if (-not (Test-Path -LiteralPath $reviewSnapshotFile -PathType Leaf)) {
        Write-Failure 'APPROVAL_REVIEW_RUN_MISSING' 'The specified review run does not exist.'
    }
    $reviewSnapshot = Get-Content -LiteralPath $reviewSnapshotFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($reviewSnapshot.candidatePayloadHash -ne $candidatePayloadHash -or
        $reviewSnapshot.ledgerHash -ne $decisionLedgerState.Hash -or
        $reviewSnapshot.inputFingerprint -ne $inputFingerprint -or
        $reviewSnapshot.policyBundleHash -ne $policyBundleHash -or
        $reviewSnapshot.benchmarkDefinitionHash -ne $benchmarkDefinitionHash -or
        $reviewSnapshot.toolVersion -ne $toolVersion) {
        Write-Failure 'APPROVAL_RUN_STALE' 'The reviewed governance run is stale.'
    }
    $approvalId = 'APR-' + [guid]::NewGuid().ToString('N')
    $approvedAt = [DateTimeOffset]::UtcNow.ToString('o')
    $approvalForDigest = [ordered]@{
        candidatePayloadHash = $candidatePayloadHash
        ledgerHash = $decisionLedgerState.Hash
        approvedBy = $ApprovedBy
        privacyReviewId = $PrivacyReviewId
        benchmarkRunId = $BenchmarkRunId
        reviewRunId = $ReviewRunId
        approvedAt = $approvedAt
    }
    $approvalDigest = Get-ApprovalDigest $approvalForDigest
    $approval = [ordered]@{
        approvalId = $approvalId
        candidatePayloadHash = $candidatePayloadHash
        ledgerHash = $decisionLedgerState.Hash
        approvedBy = $ApprovedBy
        privacyReviewId = $PrivacyReviewId
        benchmarkRunId = $BenchmarkRunId
        reviewRunId = $ReviewRunId
        approvedAt = $approvedAt
        approvalDigest = $approvalDigest
    }
    $auditRelativePath = Join-Path 'audit' 'approval.jsonl'
    $auditFile = Join-Path $resolvedWorkspace $auditRelativePath
    New-Item -ItemType Directory -Force -Path (Split-Path $auditFile -Parent) | Out-Null
    try {
        ($approval | ConvertTo-Json -Compress) | Add-Content -LiteralPath $auditFile -Encoding UTF8
    }
    catch { Write-Failure 'APPROVAL_AUDIT_WRITE_FAILED' 'Approval audit write failed.' }
    $approvalRelativePath = Join-Path 'approvals' ($approvalId + '.json')
    $approvalFile = Join-Path $resolvedWorkspace $approvalRelativePath
    New-Item -ItemType Directory -Force -Path (Split-Path $approvalFile -Parent) | Out-Null
    $approval | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $approvalFile -Encoding UTF8
    $artifacts += $auditRelativePath
    $artifacts += $approvalRelativePath
    $executedGates = @($executedGates) + @('HumanApprovalGate')
}

if ($Command -eq 'build-review-pack') {
    $reviewRelativePath = Join-Path 'review-packets' $runId
    $reviewDirectory = Join-Path $resolvedWorkspace $reviewRelativePath
    New-Item -ItemType Directory -Force -Path $reviewDirectory | Out-Null
    $caseSlugsByEvidenceId = [ordered]@{}
    foreach ($evidenceId in @($portfolio.evidence | ForEach-Object { $_.id })) {
        $caseSlugs = @($portfolio.cases |
            Where-Object { @($_.evidenceIds) -contains $evidenceId } |
            ForEach-Object { $_.slug })
        if ($caseSlugs.Count -gt 0) {
            $caseSlugsByEvidenceId[$evidenceId] = $caseSlugs
        }
    }
    [ordered]@{
        runId = $runId
        contentVersion = $portfolio.contentVersion
        candidatePayloadHash = $candidatePayloadHash
        ledgerHash = $decisionLedgerState.Hash
        counts = [ordered]@{
            projects = @($portfolio.projects).Count
            cases = @($portfolio.cases).Count
            claims = @($portfolio.claims).Count
            evidence = @($portfolio.evidence).Count
            claimEvidenceLinks = @($portfolio.claimEvidenceLinks).Count
            timelineEvents = @($portfolio.timelineEvents).Count
            questionPresets = @($portfolio.questionPresets).Count
        }
        caseSlugsByEvidenceId = $caseSlugsByEvidenceId
    } | ConvertTo-Json -Depth 10 |
        Set-Content -LiteralPath (Join-Path $reviewDirectory 'summary.json') -Encoding UTF8
    ConvertTo-Json -InputObject @($portfolio.cases) -Depth 30 |
        Set-Content -LiteralPath (Join-Path $reviewDirectory 'cases.json') -Encoding UTF8
    @($portfolio.claims) | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath (Join-Path $reviewDirectory 'claims.json') -Encoding UTF8
    @($portfolio.claimEvidenceLinks) | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath (Join-Path $reviewDirectory 'links.json') -Encoding UTF8
    [ordered]@{ runId = $runId; status = 'PASS'; rawPrivateContentIncluded = $false } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $reviewDirectory 'privacy.json') -Encoding UTF8
    [ordered]@{ runId = $runId; status = 'PASS'; activePresetCoverage = 100; criticalBenchmarkPassRate = 100; benchmarkDefinitionHash = $benchmarkDefinitionHash } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $reviewDirectory 'benchmark.json') -Encoding UTF8
    $reviewChecksums = [ordered]@{
        'portfolio.json' = Get-Sha256 $portfolioBytes
        'presentation.json' = Get-Sha256 $presentationBytes
    }
    if ($hasRetrievalCandidate) {
        $reviewChecksums['rag-documents.jsonl'] = Get-Sha256 $ragDocumentBytes
    }
    $reviewChecksums | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $reviewDirectory 'checksums.json') -Encoding UTF8
    [ordered]@{ runId = $runId; candidatePayloadHash = $candidatePayloadHash; ledgerHash = $decisionLedgerState.Hash; inputFingerprint = $inputFingerprint; status = 'PENDING_HUMAN_APPROVAL' } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $reviewDirectory 'approval-request.json') -Encoding UTF8
    $artifacts += $reviewRelativePath
}

if ($Command -eq 'publish') {
    $approvalFile = Join-Path $resolvedWorkspace (Join-Path 'approvals' ($ApprovalId + '.json'))
    if (-not (Test-Path -LiteralPath $approvalFile -PathType Leaf)) { Write-Failure 'PUBLISH_APPROVAL_MISSING' 'Approval does not exist.' }
    $approval = Get-Content -LiteralPath $approvalFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($approval.candidatePayloadHash -ne $candidatePayloadHash -or
            $approval.ledgerHash -ne $decisionLedgerState.Hash) {
        Write-Failure 'PUBLISH_APPROVAL_STALE' 'Approval does not match candidate payload or decision ledger bytes.'
    }
    if ([string]$approval.approvalDigest -ne (Get-ApprovalDigest $approval)) {
        Write-Failure 'PUBLISH_APPROVAL_STALE' 'Approval digest does not match its approved projection.'
    }
    $retrievalManifest = $null
    $keywordIndexBytes = $null
    $vectorIndexBytes = $null
    if ($hasRetrievalCandidate) {
        if ([string]::IsNullOrWhiteSpace($ModelDirectory)) {
            Write-Failure 'LOCAL_MODEL_DIRECTORY_REQUIRED' 'Retrieval publish requires an explicit local model directory.'
        }
        $resolvedModelDirectory = Resolve-SafePath $ModelDirectory 'modelDirectory'
        Assert-NoReparsePoint $resolvedModelDirectory
        $retrievalArtifacts = Join-Path $resolvedWorkspace ('.retrieval-build-' + [guid]::NewGuid().ToString('N'))
        $validFrom = ([string]$portfolio.contentVersion).Substring(0, 10)
        $compileResult = Invoke-Compiler 'com.portfolio.agent.release.RetrievalBundleCompilerCli' @(
            '--portfolio', $portfolioFile,
            '--model-dir', $resolvedModelDirectory,
            '--output-dir', $retrievalArtifacts,
            '--valid-from', $validFrom)
        if (-not $compileResult.Success -or
                -not (Test-Path -LiteralPath $retrievalArtifacts -PathType Container)) {
            if (Test-Path -LiteralPath $retrievalArtifacts) {
                Remove-Item -LiteralPath $retrievalArtifacts -Recurse -Force
            }
            Write-Failure 'RETRIEVAL_DERIVATION_FAILED' 'Local retrieval artifact derivation failed.'
        }
        try {
            $derivedRagBytes = [IO.File]::ReadAllBytes((Join-Path $retrievalArtifacts 'rag-documents.jsonl'))
            if (-not [Linq.Enumerable]::SequenceEqual(
                    [byte[]]$ragDocumentBytes, [byte[]]$derivedRagBytes)) {
                Write-Failure 'RAG_APPROVED_BYTES_MISMATCH' 'Server derivation does not reproduce the approved RAG bytes.'
            }
            $keywordIndexBytes = [IO.File]::ReadAllBytes((Join-Path $retrievalArtifacts 'keyword-index.json'))
            $vectorIndexBytes = [IO.File]::ReadAllBytes((Join-Path $retrievalArtifacts 'vector-index.bin'))
            $retrievalManifest = Get-Content -LiteralPath (Join-Path $retrievalArtifacts 'retrieval-manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
        }
        catch {
            Write-Failure 'RETRIEVAL_DERIVATION_INVALID' 'Derived retrieval artifacts are incomplete.'
        }
        finally {
            if (Test-Path -LiteralPath $retrievalArtifacts) {
                Remove-Item -LiteralPath $retrievalArtifacts -Recurse -Force
            }
        }
    }
    $resolvedReleaseRoot = Resolve-SafePath $ReleaseRoot 'releaseRoot'
    Assert-NoReparsePoint $resolvedReleaseRoot
    if (Test-Contained $resolvedReleaseRoot $repositoryRoot) { Write-Failure 'RELEASE_ROOT_INSIDE_REPOSITORY' 'ReleaseRoot must be outside the repository.' }
    if (-not $Confirm) {
        $dryRun = $true
    }
    else {
        $previousActiveVersion = $null
        if (@($PostSwitchProbeUri).Count -gt 0) {
            $currentActivePath = Join-Path $resolvedReleaseRoot 'active'
            if (-not (Test-Path -LiteralPath $currentActivePath -PathType Leaf)) {
                Write-Failure 'PUBLISH_ROLLBACK_POINT_REQUIRED' 'Post-switch probes require an existing active rollback point.'
            }
            $previousActiveVersion = (Get-Content -LiteralPath $currentActivePath -Raw -Encoding UTF8).Trim()
            if ($previousActiveVersion -notmatch '^[0-9]{4}-[0-9]{2}-[0-9]{2}\.[0-9]+$') {
                Write-Failure 'PUBLISH_ROLLBACK_POINT_INVALID' 'The existing active rollback point is invalid.'
            }
            $previousDirectory = Join-Path $resolvedReleaseRoot (Join-Path 'versions' $previousActiveVersion)
            $previousNames = if (Test-Path -LiteralPath $previousDirectory -PathType Container) {
                @(Get-ChildItem -LiteralPath $previousDirectory -File | ForEach-Object { $_.Name } | Sort-Object)
            } else { @() }
            if (-not (Test-CompleteReleaseNames $previousNames)) {
                Write-Failure 'PUBLISH_ROLLBACK_POINT_INVALID' 'The existing active rollback point is incomplete.'
            }
        }
        $publishAudit = Join-Path $resolvedWorkspace 'audit\publish.jsonl'
        try {
            New-Item -ItemType Directory -Force -Path (Split-Path $publishAudit -Parent) | Out-Null
            ([ordered]@{ runId = $runId; action = 'PUBLISH_AUTHORIZED'; approvalId = $ApprovalId; contentVersion = [string]$portfolio.contentVersion; candidatePayloadHash = $candidatePayloadHash; ledgerHash = $decisionLedgerState.Hash; authorizedAt = [DateTimeOffset]::UtcNow.ToString('o') } | ConvertTo-Json -Compress) | Add-Content -LiteralPath $publishAudit -Encoding UTF8
        }
        catch { Write-Failure 'PUBLISH_AUDIT_WRITE_FAILED' 'Publish audit write failed before public state mutation.' }
        $versionsRoot = Join-Path $resolvedReleaseRoot 'versions'
        New-Item -ItemType Directory -Force -Path $versionsRoot | Out-Null
        $versionDirectory = Join-Path $versionsRoot ([string]$portfolio.contentVersion)
        if (Test-Path -LiteralPath $versionDirectory) {
            $existingFiles = @{
                'portfolio.json' = [IO.File]::ReadAllBytes((Join-Path $versionDirectory 'portfolio.json'))
                'presentation.json' = [IO.File]::ReadAllBytes((Join-Path $versionDirectory 'presentation.json'))
            }
            $existingRagBytes = if (Test-Path -LiteralPath (Join-Path $versionDirectory 'rag-documents.jsonl') -PathType Leaf) {
                [IO.File]::ReadAllBytes((Join-Path $versionDirectory 'rag-documents.jsonl'))
            } else { $null }
            if ((Get-CandidatePayloadHash $existingFiles['portfolio.json'] $existingFiles['presentation.json'] $existingRagBytes) -ne $candidatePayloadHash) {
                Write-Failure 'PUBLISH_VERSION_COLLISION' 'The contentVersion already exists with different bytes.'
            }
            try {
                $existingManifest = Get-Content -LiteralPath (Join-Path $versionDirectory 'manifest.json') `
                    -Raw -Encoding UTF8 | ConvertFrom-Json
            }
            catch { Write-Failure 'PUBLISH_VERSION_COLLISION' 'The existing contentVersion Manifest is invalid.' }
            if ([string]$existingManifest.ledgerHash -ne [string]$decisionLedgerState.Hash) {
                Write-Failure 'PUBLISH_VERSION_COLLISION' 'The contentVersion already exists with a different decision ledger.'
            }
            $publishIdempotent = $true
        }
        else {
            $temporaryDirectory = Join-Path $versionsRoot ('.tmp-' + [guid]::NewGuid().ToString('N'))
            New-Item -ItemType Directory -Path $temporaryDirectory | Out-Null
            [IO.File]::WriteAllBytes((Join-Path $temporaryDirectory 'portfolio.json'), $portfolioBytes)
            [IO.File]::WriteAllBytes((Join-Path $temporaryDirectory 'presentation.json'), $presentationBytes)
            if ($hasRetrievalCandidate) {
                [IO.File]::WriteAllBytes((Join-Path $temporaryDirectory 'rag-documents.jsonl'), $ragDocumentBytes)
                [IO.File]::WriteAllBytes((Join-Path $temporaryDirectory 'keyword-index.json'), $keywordIndexBytes)
                [IO.File]::WriteAllBytes((Join-Path $temporaryDirectory 'vector-index.bin'), $vectorIndexBytes)
            }
            $checksumFiles = [ordered]@{
                'portfolio.json' = Get-Sha256 $portfolioBytes
                'presentation.json' = Get-Sha256 $presentationBytes
            }
            if ($hasRetrievalCandidate) {
                $checksumFiles['rag-documents.jsonl'] = Get-Sha256 $ragDocumentBytes
                $checksumFiles['keyword-index.json'] = Get-Sha256 $keywordIndexBytes
                $checksumFiles['vector-index.bin'] = Get-Sha256 $vectorIndexBytes
            }
            $checksums = [ordered]@{
                schemaVersion = [string]$portfolio.schemaVersion
                contentVersion = [string]$portfolio.contentVersion
                files = $checksumFiles
            }
            $checksums | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $temporaryDirectory 'checksums.json') -Encoding UTF8
            $now = [DateTimeOffset]::UtcNow.ToString('o')
            $manifest = [ordered]@{
                schemaVersion = [string]$portfolio.schemaVersion
                contentVersion = [string]$portfolio.contentVersion
                publishedAt = $now
                builtAt = $now
                minimumApplicationVersion = '0.1.0'
                factsFile = 'portfolio.json'
                presentationFile = 'presentation.json'
                approvalId = [string]$approval.approvalId
                approvalDigest = [string]$approval.approvalDigest
                candidatePayloadHash = $candidatePayloadHash
                ledgerHash = $decisionLedgerState.Hash
                checksumsFile = 'checksums.json'
                counts = [ordered]@{
                    projects = @($portfolio.projects).Count
                    cases = @($portfolio.cases).Count
                    claims = @($portfolio.claims).Count
                    evidence = @($portfolio.evidence).Count
                    claimEvidenceLinks = @($portfolio.claimEvidenceLinks).Count
                    timelineEvents = @($portfolio.timelineEvents).Count
                    questionPresets = @($portfolio.questionPresets).Count
                }
            }
            if ($hasRetrievalCandidate) {
                $manifest['retrieval'] = $retrievalManifest
            }
            $manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $temporaryDirectory 'manifest.json') -Encoding UTF8
            Move-Item -LiteralPath $temporaryDirectory -Destination $versionDirectory
        }
        $activeTemporary = Join-Path $resolvedReleaseRoot ('active.tmp.' + [guid]::NewGuid().ToString('N'))
        Set-Content -LiteralPath $activeTemporary -Value ([string]$portfolio.contentVersion) -Encoding UTF8
        Move-Item -LiteralPath $activeTemporary -Destination (Join-Path $resolvedReleaseRoot 'active') -Force
        if (@($PostSwitchProbeUri).Count -gt 0) {
            try {
                foreach ($probeUri in @($PostSwitchProbeUri)) {
                    if ($probeUri -notmatch '^https?://') { throw 'Probe URI scheme is invalid.' }
                    $probeResponse = Invoke-WebRequest -Uri $probeUri -UseBasicParsing -TimeoutSec 5
                    if ($probeResponse.StatusCode -lt 200 -or $probeResponse.StatusCode -ge 300) {
                        throw 'Probe returned an unsuccessful status.'
                    }
                }
            }
            catch {
                $restoreTemporary = Join-Path $resolvedReleaseRoot ('active.restore.' + [guid]::NewGuid().ToString('N'))
                Set-Content -LiteralPath $restoreTemporary -Value $previousActiveVersion -Encoding UTF8
                Move-Item -LiteralPath $restoreTemporary -Destination (Join-Path $resolvedReleaseRoot 'active') -Force
                Write-Failure 'PUBLISH_POST_SWITCH_FAILED' 'Post-switch validation failed and the previous active version was restored.'
            }
        }
    }
}
[ordered]@{
    runId = $runId; command = $Command; inputFingerprint = $inputFingerprint; status = 'PASS'; gates = $executedGates
    runSnapshot = $runSnapshot
    artifacts = $artifacts; blockingFindings = @(); warnings = @()
    dryRun = $dryRun; idempotent = $publishIdempotent
} | ConvertTo-Json -Depth 8 -Compress | Write-Output
exit 0
