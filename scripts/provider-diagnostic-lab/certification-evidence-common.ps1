$script:EvidenceRepoRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot '..\..'))
. (Join-Path $PSScriptRoot 'raw-root-common.ps1')
$script:EvidenceCorpusPath = Join-Path $PSScriptRoot `
    'qwen-general-explanation-corpus.v1.json'
$script:EvidenceDepths = @('CONCISE', 'STANDARD', 'DETAILED')
$script:EvidenceCapturePattern = `
    '^capture-[a-z]+(?:-[a-z]+)*-[0-9]{3}-(?:concise|standard|detailed)-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{32}$'
$script:EvidenceMetadataFields = @(
    'schemaVersion', 'artifactId', 'caseId', 'depth', 'createdAtUtc',
    'expiresAtUtc', 'operatorIdentitySha256', 'provider', 'model',
    'selectionVersion', 'providerContract', 'compilerProfile', 'status',
    'httpClass', 'latencyBucket', 'latencyMs', 'attemptCount',
    'captureSource')
$script:EvidenceRules = @(
    'TRIM_TEXT', 'COLLAPSE_MEANINGLESS_WHITESPACE',
    'UNICODE_NORMALIZE_NFC', 'WRAP_STRING_AS_ARRAY',
    'JOIN_ROLE_SENTENCES', 'NORMALIZE_TERMINAL_PUNCTUATION',
    'MISSING_CAVEATS_AS_EMPTY', 'DROPPED_INVALID_OPTIONAL_CAVEATS',
    'UNKNOWN_FIELD_COUNT')
$script:EvidenceChainNodeNames = [ordered]@{
    replayAggregate = 'replay-aggregate.json'
    guardArtifact = 'guard-artifact.json'
    guardProducerClosure = 'guard-producer-closure.json'
    blindPackage = 'blind-package.json'
    unblindMap = 'unblind-map.json'
    reviewInput = 'review-input.json'
    certificationManifest = 'certification-manifest.json'
    sealedReviewEvidence = 'sealed-review-evidence.json'
}
$script:CandidateBundleFiles = @(
    'backend\src\main\resources\model-contracts\general.provider-draft.v4.schema.json',
    'backend\src\main\resources\model-contracts\general.draft.v3.schema.json',
    'backend\src\main\resources\prompts\general-provider-draft-system.txt',
    'backend\src\main\java\com\portfolio\agent\turn\infrastructure\model\GeneralProviderDraftCompiler.java',
    'backend\src\main\java\com\portfolio\agent\turn\capability\general\GeneralDraftCodec.java',
    'backend\src\main\java\com\portfolio\agent\turn\capability\general\GeneralDraftRules.java',
    'backend\src\main\java\com\portfolio\agent\turn\capability\general\GeneralDraftValidator.java',
    'backend\src\main\java\com\portfolio\agent\infrastructure\model\structured\OperationBinding.java',
    'backend\src\main\java\com\portfolio\agent\infrastructure\model\configuration\ApprovedModelExecutionProfile.java',
    'backend\src\main\resources\application.yml')
$script:CompilerProfileFiles = @(
    'backend\src\main\resources\model-contracts\general.provider-draft.v4.schema.json',
    'backend\src\main\resources\model-contracts\general.draft.v3.schema.json',
    'backend\src\main\resources\prompts\general-provider-draft-system.txt',
    'backend\src\main\java\com\portfolio\agent\turn\infrastructure\model\GeneralProviderDraftCompiler.java',
    'backend\src\main\java\com\portfolio\agent\turn\capability\general\GeneralDraftCodec.java',
    'backend\src\main\java\com\portfolio\agent\turn\capability\general\GeneralDraftRules.java',
    'backend\src\main\java\com\portfolio\agent\turn\capability\general\GeneralDraftValidator.java')
$script:LegacyBaselineSnapshotFiles = @(
    'backend\src\test\resources\provider-diagnostic-lab\legacy-v3\b5cf941\GeneralProviderDraftCompiler.java.snapshot',
    'backend\src\test\resources\provider-diagnostic-lab\legacy-v3\b5cf941\GeneralDraftCodec.java.snapshot',
    'backend\src\test\resources\provider-diagnostic-lab\legacy-v3\b5cf941\GeneralDraftValidator.java.snapshot',
    'backend\src\test\resources\provider-diagnostic-lab\legacy-v3\b5cf941\GeneralDraftRules.java.snapshot')
$script:LegacyBaselineExecutableFile =
    'backend\src\test\java\com\portfolio\agent\turn\infrastructure\model\LegacyGeneralV3Baseline.java'
$script:GuardTestOnlyOverrideRelative =
    'backend\src\test\java\com\portfolio\agent\infrastructure\model\QwenGeneralCertificationGuardSupport.java'
$script:GuardTestOnlyOverrideMarker =
    '.qwen-general-guard-source-override-test-only.v1'

function Test-EvidenceExactKeys([object]$Value, [string[]]$Expected) {
    if ($null -eq $Value) { return $false }
    $actual = @($Value.PSObject.Properties.Name | Sort-Object)
    $required = @($Expected | Sort-Object)
    return ($actual -join '|') -ceq ($required -join '|')
}

function Get-EvidenceChainNodePath([string]$Directory, [string]$Name) {
    if (-not $script:EvidenceChainNodeNames.Contains($Name)) {
        throw 'EVIDENCE_CHAIN_NODE_REJECTED'
    }
    return Join-Path $Directory `
        ([string]$script:EvidenceChainNodeNames[$Name])
}

function Get-EvidenceNormalizedPath([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) { return '' }
    return [System.IO.Path]::GetFullPath($Path).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar)
}

function Test-EvidenceSameOrChild([string]$Candidate, [string]$Parent) {
    $candidatePath = Get-EvidenceNormalizedPath $Candidate
    $parentPath = Get-EvidenceNormalizedPath $Parent
    if ($candidatePath.Equals(
            $parentPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $true
    }
    return $candidatePath.StartsWith(
        $parentPath + [System.IO.Path]::DirectorySeparatorChar,
        [System.StringComparison]::OrdinalIgnoreCase)
}

function Test-EvidenceReparse([string]$Path) {
    $currentPath = Get-EvidenceNormalizedPath $Path
    while (-not [string]::IsNullOrWhiteSpace($currentPath)) {
        if (Test-Path -LiteralPath $currentPath) {
            $item = Get-Item -LiteralPath $currentPath -Force
            if (($item.Attributes -band `
                    [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                return $true
            }
        }
        $parent = Split-Path -Parent $currentPath
        if ([string]::IsNullOrWhiteSpace($parent) -or
                $parent -ceq $currentPath) { break }
        $currentPath = $parent
    }
    return $false
}

function Protect-EvidenceAcl([string]$Path) {
    try {
        $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
        $current = Get-Acl -LiteralPath $Path
        $allowed = @($current.Access | Where-Object {
            $_.AccessControlType -eq `
                [System.Security.AccessControl.AccessControlType]::Allow
        })
        $exclusive = $current.AreAccessRulesProtected -and
            $current.Owner.EndsWith(
                $identity.Name,
                [System.StringComparison]::OrdinalIgnoreCase) -and
            @($allowed | Where-Object {
                $_.IdentityReference.Value -cne $identity.User.Value -and
                $_.IdentityReference.Value -cne $identity.Name
            }).Count -eq 0
        if (-not $exclusive) {
            $security = New-Object `
                System.Security.AccessControl.DirectorySecurity
            $security.SetOwner($identity.User)
            $security.SetAccessRuleProtection($true, $false)
            $rule = New-Object `
                System.Security.AccessControl.FileSystemAccessRule(
                $identity.User,
                [System.Security.AccessControl.FileSystemRights]::FullControl,
                [System.Security.AccessControl.InheritanceFlags]'ContainerInherit, ObjectInherit',
                [System.Security.AccessControl.PropagationFlags]::None,
                [System.Security.AccessControl.AccessControlType]::Allow)
            $security.AddAccessRule($rule)
            Set-Acl -LiteralPath $Path -AclObject $security
        }
        $verified = Get-Acl -LiteralPath $Path
        $verifiedAllowed = @($verified.Access | Where-Object {
            $_.AccessControlType -eq `
                [System.Security.AccessControl.AccessControlType]::Allow
        })
        if (-not $verified.AreAccessRulesProtected -or
                -not $verified.Owner.EndsWith(
                    $identity.Name,
                    [System.StringComparison]::OrdinalIgnoreCase) -or
                @($verifiedAllowed | Where-Object {
                    $_.IdentityReference.Value -cne $identity.User.Value -and
                    $_.IdentityReference.Value -cne $identity.Name
                }).Count -gt 0) {
            throw 'EVIDENCE_OS_ACCESS_BOUNDARY_FAILED'
        }
    }
    catch {
        if ($_.Exception.Message -ceq
                'EVIDENCE_OS_ACCESS_BOUNDARY_FAILED') { throw }
        throw 'EVIDENCE_OS_ACCESS_BOUNDARY_FAILED'
    }
}

function Assert-EvidenceContainedLeaf(
    [string]$Path,
    [string]$Root
) {
    $full = Get-EvidenceNormalizedPath $Path
    if (-not (Test-EvidenceSameOrChild $full $Root) -or
            -not (Test-Path -LiteralPath $full -PathType Leaf) -or
            (Test-EvidenceReparse $full)) {
        throw 'EVIDENCE_PATH_REJECTED'
    }
    $resolved = Get-EvidenceNormalizedPath (Resolve-Path -LiteralPath $full).Path
    if (-not (Test-EvidenceSameOrChild $resolved $Root)) {
        throw 'EVIDENCE_PATH_REJECTED'
    }
    return $resolved
}

function Get-EvidenceFileSha256([string]$Path) {
    $stream = [System.IO.File]::OpenRead($Path)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([System.BitConverter]::ToString(
            $sha.ComputeHash($stream))).Replace('-', '').ToLowerInvariant()
    }
    finally {
        $sha.Dispose()
        $stream.Dispose()
    }
}

function Get-EvidenceTextSha256([string]$Value) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([System.BitConverter]::ToString(
            $sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant()
    }
    finally { $sha.Dispose() }
}

function Get-EvidenceBundleSha256([string[]]$Files) {
    $lines = [System.Collections.Generic.List[string]]::new()
    foreach ($relative in @($Files | Sort-Object)) {
        $path = Join-Path $script:EvidenceRepoRoot $relative
        if (-not (Test-Path -LiteralPath $path -PathType Leaf) -or
                (Test-EvidenceReparse $path)) {
            throw 'EVIDENCE_CANDIDATE_BUNDLE_REJECTED'
        }
        $lines.Add(($relative.Replace('\', '/') + ':' +
            (Get-EvidenceFileSha256 $path)))
    }
    return Get-EvidenceTextSha256 ($lines -join "`n")
}

function Get-EvidenceGuardTestOnlySourceOverride([string]$Root) {
    if ([string]::IsNullOrWhiteSpace($Root)) { return '' }
    $resolvedRoot = Get-EvidenceNormalizedPath $Root
    $temporaryRoot = Get-EvidenceNormalizedPath `
        ([System.IO.Path]::GetTempPath())
    if (-not (Test-EvidenceSameOrChild $resolvedRoot $temporaryRoot) -or
            $resolvedRoot -ceq $temporaryRoot -or
            [System.IO.Path]::GetFileName($resolvedRoot) -cnotmatch
                '^guard-source-override-test-[0-9a-f]{32}$' -or
            -not (Test-Path -LiteralPath $resolvedRoot -PathType Container) -or
            (Test-EvidenceReparse $resolvedRoot)) {
        throw 'EVIDENCE_GUARD_TEST_SOURCE_OVERRIDE_REJECTED'
    }
    $marker = Join-Path $resolvedRoot $script:GuardTestOnlyOverrideMarker
    $override = Join-Path $resolvedRoot `
        $script:GuardTestOnlyOverrideRelative
    if (-not (Test-Path -LiteralPath $marker -PathType Leaf) -or
            (Get-Content -LiteralPath $marker -Raw -Encoding UTF8) -cne
                'QWEN_GENERAL_GUARD_SOURCE_OVERRIDE_TEST_ONLY_V1' -or
            -not (Test-Path -LiteralPath $override -PathType Leaf) -or
            (Test-EvidenceReparse $marker) -or
            (Test-EvidenceReparse $override)) {
        throw 'EVIDENCE_GUARD_TEST_SOURCE_OVERRIDE_REJECTED'
    }
    $allowed = @(
        (Get-EvidenceNormalizedPath $marker),
        (Get-EvidenceNormalizedPath $override)) | Sort-Object
    $actual = @(Get-ChildItem -LiteralPath $resolvedRoot -File -Recurse `
        -Force | ForEach-Object {
            Get-EvidenceNormalizedPath $_.FullName
        } | Sort-Object)
    if (($actual -join '|') -cne ($allowed -join '|')) {
        throw 'EVIDENCE_GUARD_TEST_SOURCE_OVERRIDE_REJECTED'
    }
    return Get-EvidenceNormalizedPath $override
}

function Get-EvidenceGuardProducerClosure(
    [string]$TestOnlySourceOverrideRoot = ''
) {
    $testOnlySourceOverride = Get-EvidenceGuardTestOnlySourceOverride `
        $TestOnlySourceOverrideRoot
    $backend = Join-Path $script:EvidenceRepoRoot 'backend'
    $mainClasses = Join-Path $backend 'target\classes'
    $testClasses = Join-Path $backend 'target\test-classes'
    $rootClassName =
        'com.portfolio.agent.infrastructure.model.QwenGeneralCertificationGuardTest'
    $rootClass = Join-Path $testClasses `
        ($rootClassName.Replace('.', '\') + '.class')
    $jdeps = Get-Command jdeps.exe -ErrorAction SilentlyContinue
    if ($null -eq $jdeps -or
            -not (Test-Path -LiteralPath $rootClass -PathType Leaf)) {
        throw 'EVIDENCE_GUARD_CLOSURE_REJECTED'
    }
    $classPath = $mainClasses + [System.IO.Path]::PathSeparator + $testClasses
    $output = @(& $jdeps.Source -R -verbose:class -cp $classPath $rootClass 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw 'EVIDENCE_GUARD_CLOSURE_REJECTED'
    }
    $classNames = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::Ordinal)
    [void]$classNames.Add($rootClassName)
    foreach ($line in $output) {
        if ([string]$line -match
                '^\s+(com\.portfolio\.agent(?:\.[A-Za-z0-9_$]+)+)\s+->\s+(com\.portfolio\.agent(?:\.[A-Za-z0-9_$]+)+)\s+') {
            [void]$classNames.Add($Matches[1])
            [void]$classNames.Add($Matches[2])
        }
    }
    if ($classNames.Count -lt 2) {
        throw 'EVIDENCE_GUARD_CLOSURE_REJECTED'
    }
    $classes = [System.Collections.Generic.List[object]]::new()
    $sourcePaths = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::OrdinalIgnoreCase)
    $closureLines = [System.Collections.Generic.List[string]]::new()
    foreach ($className in @($classNames | Sort-Object)) {
        $classRelative = $className.Replace('.', '\') + '.class'
        $mainClass = Join-Path $mainClasses $classRelative
        $testClass = Join-Path $testClasses $classRelative
        $classFile = if (Test-Path -LiteralPath $mainClass -PathType Leaf) {
            $mainClass
        }
        elseif (Test-Path -LiteralPath $testClass -PathType Leaf) {
            $testClass
        }
        else { throw 'EVIDENCE_GUARD_CLOSURE_REJECTED' }
        $outerClass = $className.Split('$')[0]
        $sourceRelative = $outerClass.Replace('.', '\') + '.java'
        $mainSource = Join-Path $backend ('src\main\java\' + $sourceRelative)
        $testSource = Join-Path $backend ('src\test\java\' + $sourceRelative)
        $sourceFile = if (Test-Path -LiteralPath $mainSource -PathType Leaf) {
            $mainSource
        }
        elseif (Test-Path -LiteralPath $testSource -PathType Leaf) {
            $testSource
        }
        else { throw 'EVIDENCE_GUARD_CLOSURE_REJECTED' }
        $sourcePath = $sourceFile.Substring(
            $script:EvidenceRepoRoot.Length + 1).Replace('\', '/')
        $sourceHashFile = if (-not [string]::IsNullOrWhiteSpace(
                $testOnlySourceOverride) -and
                $sourcePath -ceq
                    $script:GuardTestOnlyOverrideRelative.Replace('\', '/')) {
            $testOnlySourceOverride
        }
        else { $sourceFile }
        if ((Test-EvidenceReparse $classFile) -or
                (Test-EvidenceReparse $sourceFile) -or
                (Test-EvidenceReparse $sourceHashFile)) {
            throw 'EVIDENCE_GUARD_CLOSURE_REJECTED'
        }
        $classArtifact = $classFile.Substring(
            $script:EvidenceRepoRoot.Length + 1).Replace('\', '/')
        [void]$sourcePaths.Add($sourceHashFile)
        $entry = [ordered]@{
            className = $className
            classArtifact = $classArtifact
            classSha256 = Get-EvidenceFileSha256 $classFile
            sourcePath = $sourcePath
            sourceSha256 = Get-EvidenceFileSha256 $sourceHashFile
        }
        $classes.Add($entry)
        $closureLines.Add(($entry.className + '|' + $entry.classArtifact + '|' +
            $entry.classSha256 + '|' + $entry.sourcePath + '|' +
            $entry.sourceSha256))
    }
    $resourcePaths = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::OrdinalIgnoreCase)
    foreach ($sourceFile in $sourcePaths) {
        $sourceText = Get-Content -LiteralPath $sourceFile -Raw -Encoding UTF8
        foreach ($match in [regex]::Matches(
                $sourceText, '(?:model-contracts|prompts)/[A-Za-z0-9._/-]+')) {
            $resource = Join-Path $backend `
                ('src\main\resources\' + $match.Value.Replace('/', '\'))
            if (Test-Path -LiteralPath $resource -PathType Leaf) {
                [void]$resourcePaths.Add($resource)
            }
        }
    }
    $resources = [System.Collections.Generic.List[object]]::new()
    foreach ($resource in @($resourcePaths | Sort-Object)) {
        if (Test-EvidenceReparse $resource) {
            throw 'EVIDENCE_GUARD_CLOSURE_REJECTED'
        }
        $relative = $resource.Substring(
            $script:EvidenceRepoRoot.Length + 1).Replace('\', '/')
        $hash = Get-EvidenceFileSha256 $resource
        $resources.Add([ordered]@{ path = $relative; sha256 = $hash })
        $closureLines.Add(('RESOURCE|' + $relative + '|' + $hash))
    }
    $closureSha256 = Get-EvidenceTextSha256 `
        (@($closureLines | Sort-Object) -join "`n")
    return [ordered]@{
        schemaVersion = 'qwen-general-guard-producer-closure.v1'
        rootClass = $rootClassName
        classes = @($classes)
        resources = @($resources)
        closureSha256 = $closureSha256
    }
}

function Read-EvidenceGuardProducerClosure(
    [string]$Path,
    [string]$AllowedRoot,
    [string]$TestOnlySourceOverrideRoot = ''
) {
    $artifact = Read-EvidenceJson $Path $AllowedRoot
    if (-not (Test-EvidenceExactKeys $artifact @(
                'schemaVersion', 'rootClass', 'classes', 'resources',
                'closureSha256')) -or
            $artifact.schemaVersion -cne
                'qwen-general-guard-producer-closure.v1' -or
            [string]$artifact.closureSha256 -cnotmatch '^[0-9a-f]{64}$' -or
            @($artifact.classes).Count -lt 2) {
        throw 'EVIDENCE_GUARD_CLOSURE_REJECTED'
    }
    $current = Get-EvidenceGuardProducerClosure `
        $TestOnlySourceOverrideRoot
    $actualJson = $artifact | ConvertTo-Json -Depth 12 -Compress
    $currentJson = $current | ConvertTo-Json -Depth 12 -Compress
    if ($actualJson -cne $currentJson) {
        throw 'CERTIFICATION_SOURCE_DRIFT_REJECTED'
    }
    return $artifact
}

function Read-EvidenceJson([string]$Path, [string]$Root) {
    $safePath = Assert-EvidenceContainedLeaf $Path $Root
    if ((Get-Item -LiteralPath $safePath).Length -gt 10485760) {
        throw 'EVIDENCE_JSON_REJECTED'
    }
    try {
        return Get-Content -LiteralPath $safePath -Raw -Encoding UTF8 |
            ConvertFrom-Json
    }
    catch { throw 'EVIDENCE_JSON_REJECTED' }
}

function Write-EvidenceJson([string]$Path, [object]$Value) {
    [System.IO.File]::WriteAllText(
        $Path,
        ($Value | ConvertTo-Json -Depth 30 -Compress),
        [System.Text.UTF8Encoding]::new($false))
}

function Assert-EvidenceRawRoot([string]$RawArtifactRoot) {
    try {
        $root = Assert-DedicatedRawRoot `
            $RawArtifactRoot $script:EvidenceRepoRoot `
            'EVIDENCE_RAW_ROOT_REJECTED'
    }
    catch {
        throw 'EVIDENCE_RAW_ROOT_REJECTED'
    }
    if (Test-Path -LiteralPath (Join-Path $root '.loopback-test-trace')) {
        throw 'EVIDENCE_LOOPBACK_TRACE_REJECTED'
    }
    try {
        Protect-DedicatedRawRootAcl `
            $root 'EVIDENCE_OS_ACCESS_BOUNDARY_FAILED'
    }
    catch { throw 'EVIDENCE_OS_ACCESS_BOUNDARY_FAILED' }
    return $root
}

function Get-EvidenceCorpusMap {
    $corpus = Get-Content -LiteralPath $script:EvidenceCorpusPath `
        -Raw -Encoding UTF8 | ConvertFrom-Json
    $map = @{}
    foreach ($case in @($corpus.cases)) {
        foreach ($depth in $script:EvidenceDepths) {
            $key = [string]$case.caseId + '|' + $depth
            if ($map.ContainsKey($key)) { throw 'EVIDENCE_CORPUS_REJECTED' }
            $map[$key] = [ordered]@{
                caseId = [string]$case.caseId
                depth = $depth
                question = [string]$case.prompts.$depth
            }
        }
    }
    if ($map.Count -ne 300) { throw 'EVIDENCE_CORPUS_REJECTED' }
    return $map
}

function Read-EvidenceArtifact(
    [System.IO.DirectoryInfo]$Directory,
    [string]$RawRoot
) {
    if ($Directory.Name -cnotmatch $script:EvidenceCapturePattern -or
            ($Directory.Attributes -band `
                [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'EVIDENCE_ARTIFACT_REJECTED'
    }
    $metadataPath = Assert-EvidenceContainedLeaf `
        (Join-Path $Directory.FullName 'metadata.json') $RawRoot
    $metadata = Read-EvidenceJson $metadataPath $RawRoot
    if (-not (Test-EvidenceExactKeys $metadata $script:EvidenceMetadataFields) -or
            $metadata.schemaVersion -cne 'qwen-general-lab-artifact.v2' -or
            $metadata.artifactId -cne $Directory.Name -or
            [string]$metadata.caseId -cnotmatch `
                '^[a-z]+(?:-[a-z]+)*-[0-9]{3}$' -or
            [string]$metadata.depth -cnotin $script:EvidenceDepths -or
            [string]$metadata.operatorIdentitySha256 -cnotmatch `
                '^[0-9a-f]{64}$' -or
            $metadata.provider -cne 'QWEN' -or
            $metadata.model -cne 'qwen3.7-flash' -or
            $metadata.selectionVersion -cne 'qwen-3-7-flash-v7' -or
            $metadata.providerContract -cne 'general.provider-draft.v4' -or
            $metadata.compilerProfile -cne `
                'general-provider-draft-compiler.v4' -or
            [string]$metadata.status -cnotin @(
                'CAPTURED', 'RATE_LIMITED', 'SERVER_ERROR', 'CLIENT_ERROR',
                'TRANSPORT_FAILED', 'RESPONSE_REJECTED') -or
            [string]$metadata.httpClass -cnotin @(
                'SUCCESS', 'RATE_LIMITED', 'SERVER_ERROR', 'CLIENT_ERROR',
                'TRANSPORT_UNAVAILABLE') -or
            [string]$metadata.latencyBucket -cnotin @(
                'LT_100_MS', 'FROM_100_TO_499_MS',
                'FROM_500_TO_1999_MS', 'FROM_2000_TO_9999_MS',
                'GTE_10000_MS') -or
            ($metadata.latencyMs -isnot [int] -and
                $metadata.latencyMs -isnot [long]) -or
            [long]$metadata.latencyMs -lt 0 -or
            [long]$metadata.latencyMs -gt 120000 -or
            ($metadata.attemptCount -isnot [int] -and
                $metadata.attemptCount -isnot [long]) -or
            [long]$metadata.attemptCount -notin @(1, 2)) {
        throw 'EVIDENCE_METADATA_REJECTED'
    }
    # Provenance prevents accidental promotion of loopback fixtures. It is not
    # a signature and does not claim resistance to a malicious local writer.
    if ([string]$metadata.captureSource -cne 'REAL_PROVIDER') {
        throw 'EVIDENCE_CAPTURE_SOURCE_REJECTED'
    }
    $validPair = switch ([string]$metadata.status) {
        'CAPTURED' { $metadata.httpClass -ceq 'SUCCESS' }
        'RATE_LIMITED' { $metadata.httpClass -ceq 'RATE_LIMITED' }
        'SERVER_ERROR' { $metadata.httpClass -ceq 'SERVER_ERROR' }
        'CLIENT_ERROR' { $metadata.httpClass -ceq 'CLIENT_ERROR' }
        'TRANSPORT_FAILED' {
            $metadata.httpClass -ceq 'TRANSPORT_UNAVAILABLE'
        }
        'RESPONSE_REJECTED' { $metadata.httpClass -ceq 'SUCCESS' }
        default { $false }
    }
    if (-not $validPair) { throw 'EVIDENCE_METADATA_REJECTED' }
    try {
        $created = [datetimeoffset]::Parse([string]$metadata.createdAtUtc)
        $expires = [datetimeoffset]::Parse([string]$metadata.expiresAtUtc)
    }
    catch { throw 'EVIDENCE_METADATA_REJECTED' }
    if ($expires -le $created -or
            ($expires - $created) -gt [timespan]::FromHours(24) -or
            $expires -le [datetimeoffset]::UtcNow) {
        throw 'EVIDENCE_METADATA_REJECTED'
    }
    $responsePath = Join-Path $Directory.FullName 'response.raw.json'
    $responseHash = $null
    $answer = $null
    if (Test-Path -LiteralPath $responsePath) {
        $responsePath = Assert-EvidenceContainedLeaf $responsePath $RawRoot
        if ((Get-Item -LiteralPath $responsePath).Length -gt 131072) {
            throw 'EVIDENCE_ARTIFACT_REJECTED'
        }
        $responseHash = Get-EvidenceFileSha256 $responsePath
    }
    if ($metadata.status -ceq 'CAPTURED') {
        if ($null -eq $responseHash) { throw 'EVIDENCE_ARTIFACT_REJECTED' }
        $response = Read-EvidenceJson $responsePath $RawRoot
        $answer = Get-EvidenceAnswerText $response
    }
    return [ordered]@{
        artifactId = $Directory.Name
        caseId = [string]$metadata.caseId
        depth = [string]$metadata.depth
        metadataSha256 = Get-EvidenceFileSha256 $metadataPath
        responseSha256 = $responseHash
        transportOutcome = if ($metadata.status -ceq 'CAPTURED') {
            'SUCCESS'
        } else {
            'FAILED'
        }
        responseObtained = $metadata.status -ceq 'CAPTURED'
        latencyMs = [long]$metadata.latencyMs
        attemptCount = [long]$metadata.attemptCount
        answerText = $answer
    }
}

function Get-EvidenceAnswerText([object]$Envelope) {
    if ($null -eq $Envelope -or $Envelope.model -cne 'qwen3.7-flash') {
        throw 'EVIDENCE_ENVELOPE_REJECTED'
    }
    $choices = @($Envelope.choices)
    if ($choices.Count -ne 1 -or
            $choices[0].finish_reason -cne 'stop') {
        throw 'EVIDENCE_ENVELOPE_REJECTED'
    }
    $message = $choices[0].message
    if ($null -eq $message -or
            ($null -ne $message.refusal) -or
            ($null -ne $message.content -and
                -not [string]::IsNullOrWhiteSpace([string]$message.content))) {
        throw 'EVIDENCE_ENVELOPE_REJECTED'
    }
    $calls = @($message.tool_calls)
    if ($calls.Count -ne 1 -or $calls[0].type -cne 'function' -or
            $calls[0].function.name -cne `
                'emit_general_provider_draft_v4' -or
            [string]::IsNullOrWhiteSpace(
                [string]$calls[0].function.arguments)) {
        throw 'EVIDENCE_ENVELOPE_REJECTED'
    }
    try {
        $draft = [string]$calls[0].function.arguments | ConvertFrom-Json
    }
    catch { throw 'EVIDENCE_ENVELOPE_REJECTED' }
    $definitionProperty = $draft.PSObject.Properties['definition']
    $mechanismProperty = $draft.PSObject.Properties['mechanism']
    if ($null -eq $definitionProperty -or $null -eq $mechanismProperty) {
        return $null
    }
    $definition = @($definitionProperty.Value)
    $mechanism = @($mechanismProperty.Value)
    if ($definition.Count -lt 1 -or $mechanism.Count -lt 1) { return $null }
    $parts = @($definition + $mechanism | ForEach-Object {
        ([string]$_).Trim()
    } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($parts.Count -lt 2) { return $null }
    return $parts -join "`n"
}

function Read-EvidenceReplay([string]$Path, [string]$AllowedRoot) {
    $aggregate = Read-EvidenceJson $Path $AllowedRoot
    if (-not (Test-EvidenceExactKeys $aggregate @(
                'schemaVersion', 'corpusVersion', 'fixtureMode',
                'fixtureCaseKey', 'samples')) -or
            $aggregate.schemaVersion -cne 'qwen-general-dual-replay.v1' -or
            $aggregate.corpusVersion -cne `
                'qwen-general-explanation-corpus.v1' -or
            [string]$aggregate.fixtureMode -cnotin @(
                'NONE',
                'TEST_ONLY_POST_CANONICAL_SEMANTIC_REJECT') -or
            ($aggregate.fixtureMode -ceq 'NONE' -and
                $null -ne $aggregate.fixtureCaseKey) -or
            ($aggregate.fixtureMode -ceq
                    'TEST_ONLY_POST_CANONICAL_SEMANTIC_REJECT' -and
                [string]$aggregate.fixtureCaseKey -cnotmatch
                    '^[a-z]+(?:-[a-z]+)*-[0-9]{3}\|(?:CONCISE|STANDARD|DETAILED)$') -or
            @($aggregate.samples).Count -ne 300) {
        throw 'EVIDENCE_REPLAY_REJECTED'
    }
    $map = @{}
    foreach ($sample in @($aggregate.samples)) {
        if (-not (Test-EvidenceExactKeys $sample @(
                    'caseId', 'depth', 'v3', 'v4')) -or
                [string]$sample.depth -cnotin $script:EvidenceDepths -or
                -not (Test-EvidenceExactKeys $sample.v3 @(
                    'outcome', 'layer', 'reason')) -or
                -not (Test-EvidenceExactKeys $sample.v4 @(
                    'outcome', 'layer', 'reason',
                    'normalizationRuleCounts')) -or
                [string]$sample.v3.outcome -cnotin @(
                    'EXACT', 'NORMALIZED', 'DEGRADED', 'INCOMPLETE',
                    'NOT_APPLICABLE') -or
                [string]$sample.v4.outcome -cnotin @(
                    'EXACT', 'NORMALIZED', 'DEGRADED', 'INCOMPLETE',
                    'NOT_APPLICABLE')) {
            throw 'EVIDENCE_REPLAY_REJECTED'
        }
        if (($sample.v3.outcome -ceq 'NOT_APPLICABLE') -ne
                ($sample.v4.outcome -ceq 'NOT_APPLICABLE')) {
            throw 'EVIDENCE_REPLAY_REJECTED'
        }
        $acceptedV4 = [string]$sample.v4.outcome -cin @(
            'EXACT', 'NORMALIZED', 'DEGRADED')
        if (($acceptedV4 -and $sample.v4.layer -cne 'ACCEPTED') -or
                ($sample.v4.outcome -ceq 'NOT_APPLICABLE' -and
                    $sample.v4.layer -cne 'TRANSPORT') -or
                ($sample.v4.outcome -ceq 'INCOMPLETE' -and
                    [string]$sample.v4.layer -cnotin @(
                        'PROVIDER_DRAFT_SCHEMA', 'DETERMINISTIC_COMPILER',
                        'CANONICAL_SCHEMA', 'SEMANTIC',
                        'CLOSED_PIPELINE'))) {
            throw 'EVIDENCE_REPLAY_REJECTED'
        }
        foreach ($rule in $sample.v4.normalizationRuleCounts.PSObject.Properties) {
            if ($rule.Name -cnotin $script:EvidenceRules -or
                    ($rule.Value -isnot [int] -and
                        $rule.Value -isnot [long]) -or
                    [long]$rule.Value -lt 0) {
                throw 'EVIDENCE_REPLAY_REJECTED'
            }
        }
        $key = [string]$sample.caseId + '|' + [string]$sample.depth
        if ($map.ContainsKey($key)) { throw 'EVIDENCE_REPLAY_REJECTED' }
        $sampleJson = $sample | ConvertTo-Json -Depth 12 -Compress
        $map[$key] = [ordered]@{
            sample = $sample
            sha256 = Get-EvidenceTextSha256 $sampleJson
        }
    }
    if ($aggregate.fixtureMode -ceq
            'TEST_ONLY_POST_CANONICAL_SEMANTIC_REJECT') {
        $fixtureSample = $map[[string]$aggregate.fixtureCaseKey]
        if ($null -eq $fixtureSample -or
                $fixtureSample.sample.v4.outcome -cne 'INCOMPLETE' -or
                $fixtureSample.sample.v4.layer -cne 'SEMANTIC') {
            throw 'EVIDENCE_REPLAY_REJECTED'
        }
    }
    return [ordered]@{ aggregate = $aggregate; map = $map }
}

function Read-EvidenceGuardArtifact([string]$Path, [string]$AllowedRoot) {
    $artifact = Read-EvidenceJson $Path $AllowedRoot
    if (-not (Test-EvidenceExactKeys $artifact @(
                'schemaVersion', 'gates')) -or
            $artifact.schemaVersion -cne 'qwen-general-guard-artifact.v3' -or
            -not (Test-EvidenceExactKeys $artifact.gates @(
                'safetyIdentityPermission', 'missingCore', 'canonical'))) {
        throw 'EVIDENCE_GUARD_ARTIFACT_REJECTED'
    }
    $definitions = @(
        @{ Name = 'missingCore'; Failure = 'acceptedMissingCore' },
        @{ Name = 'canonical'; Failure = 'falseAcceptance' })
    foreach ($definition in $definitions) {
        $gate = $artifact.gates.($definition.Name)
        if (-not (Test-EvidenceExactKeys $gate @(
                    'cases', $definition.Failure)) -or
                ($gate.cases -isnot [int] -and $gate.cases -isnot [long]) -or
                [long]$gate.cases -le 0 -or
                ($gate.($definition.Failure) -isnot [int] -and
                    $gate.($definition.Failure) -isnot [long]) -or
                [long]$gate.($definition.Failure) -lt 0) {
            throw 'EVIDENCE_GUARD_ARTIFACT_REJECTED'
        }
    }
    $safety = $artifact.gates.safetyIdentityPermission
    if (-not (Test-EvidenceExactKeys $safety @(
                'cases', 'falseAcceptance', 'classifications')) -or
            ($safety.cases -isnot [int] -and
                $safety.cases -isnot [long]) -or
            [long]$safety.cases -le 0 -or
            ($safety.falseAcceptance -isnot [int] -and
                $safety.falseAcceptance -isnot [long]) -or
            [long]$safety.falseAcceptance -lt 0 -or
            -not (Test-EvidenceExactKeys $safety.classifications @(
                'PROVIDER_MODEL_REF', 'SELECTION_VERSION',
                'OPERATION_BINDING', 'PROTOCOL_PROFILE',
                'RESPONSE_MODEL_IDENTITY', 'REQUIRED_TOOL_ENVELOPE',
                'TOOL_ARGUMENTS_NOT_AUTHORIZATION',
                'SECRET_LIKE_OUTBOUND'))) {
        throw 'EVIDENCE_GUARD_ARTIFACT_REJECTED'
    }
    $caseTotal = 0L
    $failureTotal = 0L
    foreach ($name in @(
            'PROVIDER_MODEL_REF', 'SELECTION_VERSION',
            'OPERATION_BINDING', 'PROTOCOL_PROFILE',
            'RESPONSE_MODEL_IDENTITY', 'REQUIRED_TOOL_ENVELOPE',
            'TOOL_ARGUMENTS_NOT_AUTHORIZATION',
            'SECRET_LIKE_OUTBOUND')) {
        $classification = $safety.classifications.$name
        if (-not (Test-EvidenceExactKeys $classification @(
                    'cases', 'falseAcceptance')) -or
                ($classification.cases -isnot [int] -and
                    $classification.cases -isnot [long]) -or
                [long]$classification.cases -le 0 -or
                ($classification.falseAcceptance -isnot [int] -and
                    $classification.falseAcceptance -isnot [long]) -or
                [long]$classification.falseAcceptance -lt 0) {
            throw 'EVIDENCE_GUARD_ARTIFACT_REJECTED'
        }
        $caseTotal += [long]$classification.cases
        $failureTotal += [long]$classification.falseAcceptance
    }
    if ($caseTotal -ne [long]$safety.cases -or
            $failureTotal -ne [long]$safety.falseAcceptance) {
        throw 'EVIDENCE_GUARD_ARTIFACT_REJECTED'
    }
    return $artifact
}

function Get-EvidenceShuffled([object[]]$Values) {
    $result = [System.Collections.Generic.List[object]]::new()
    foreach ($value in $Values) { $result.Add($value) }
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        for ($index = $result.Count - 1; $index -gt 0; $index--) {
            $bytes = New-Object byte[] 4
            $rng.GetBytes($bytes)
            $swap = [System.BitConverter]::ToUInt32($bytes, 0) % ($index + 1)
            $current = $result[$index]
            $result[$index] = $result[$swap]
            $result[$swap] = $current
        }
    }
    finally { $rng.Dispose() }
    return @($result)
}

function Get-EvidenceAdmissionSummary(
    [object[]]$Samples,
    [int]$L3Success
) {
    $sampleValues = @($Samples)
    $responseCount = @($sampleValues | Where-Object {
        $_.responseObtained -eq $true
    }).Count
    $shapeAccepted = @($sampleValues | Where-Object {
        $_.shapeAccepted -eq $true
    }).Count
    $semanticAccepted = @($sampleValues | Where-Object {
        $_.semanticAccepted -eq $true
    }).Count
    if ($shapeAccepted -gt $responseCount -or
            $semanticAccepted -gt $shapeAccepted -or
            $L3Success -lt 0 -or $L3Success -gt $semanticAccepted) {
        throw 'EVIDENCE_ADMISSION_SUMMARY_REJECTED'
    }
    return [ordered]@{
        transportDenominator = $sampleValues.Count
        responseCount = $responseCount
        shapeDenominator = $responseCount
        shapeAccepted = $shapeAccepted
        semanticDenominator = $shapeAccepted
        semanticAccepted = $semanticAccepted
        blindReviewDenominator = $semanticAccepted
        l3TaskDenominator = $sampleValues.Count
        l3Success = $L3Success
    }
}
