param(
    [Parameter(Mandatory = $true)]
    [string]$ReleaseRoot,
    [Parameter(Mandatory = $true)]
    [string]$TargetVersion,
    [Parameter(Mandatory = $true)]
    [string]$Workspace,
    [Parameter(Mandatory = $true)]
    [string]$DecisionLedger
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$fileNames = @(
    'checksums.json',
    'keyword-index.json',
    'manifest.json',
    'portfolio.json',
    'presentation.json',
    'rag-documents.jsonl',
    'vector-index.bin'
)
$ownedTemporaryPrefix = '.public-release-import-temp-'
$ownedBackupPrefix = '.public-release-import-backup-'
$knownFaults = @(
    'Copy', 'Readback', 'TempVerify', 'RenameCurrent',
    'RenameTemp', 'FinalVerify', 'BackupDelete'
)

function Test-Contained([string]$Child, [string]$Parent) {
    $separator = [IO.Path]::DirectorySeparatorChar
    $prefix = $Parent.TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar) + $separator
    return $Child.Equals($Parent, [StringComparison]::OrdinalIgnoreCase) -or
        $Child.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)
}

function Resolve-SafeExistingPath(
    [string]$Value,
    [string]$Label,
    [bool]$RequireDirectory
) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "$Label is required."
    }
    if ($Value -match '(^|[\\/])\.\.([\\/]|$)') {
        throw "$Label contains traversal."
    }
    $resolved = (Resolve-Path -LiteralPath $Value -ErrorAction Stop).Path
    $item = Get-Item -LiteralPath $resolved -Force
    if ($RequireDirectory -and -not $item.PSIsContainer) {
        throw "$Label must be a directory."
    }
    if (-not $RequireDirectory -and $item.PSIsContainer) {
        throw "$Label must be a file."
    }
    $current = $item
    while ($null -ne $current) {
        if (($current.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "$Label must not use a symlink or junction."
        }
        $current = $current.Parent
    }
    return [IO.Path]::GetFullPath($resolved)
}

function Assert-ClosedSevenFileDirectory([string]$Directory) {
    $entries = @(Get-ChildItem -LiteralPath $Directory -Force)
    $actualNames = @($entries | ForEach-Object { $_.Name } | Sort-Object)
    $expectedNames = @($fileNames | Sort-Object)
    if ($entries.Count -ne $fileNames.Count -or
            ($actualNames -join "`n") -ne ($expectedNames -join "`n") -or
            @($entries | Where-Object {
                    $_.PSIsContainer -or
                    ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0
                }).Count -ne 0) {
        throw 'External release must contain exactly seven public files.'
    }
}

function Get-FileHashValue([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Test-OwnedSibling(
    [string]$Path,
    [string]$ExpectedParent,
    [string]$Prefix
) {
    $absolute = [IO.Path]::GetFullPath($Path)
    $parent = Split-Path -Parent $absolute
    $leaf = Split-Path -Leaf $absolute
    return $parent.Equals(
            [IO.Path]::GetFullPath($ExpectedParent),
            [StringComparison]::OrdinalIgnoreCase) -and
        $leaf.StartsWith($Prefix, [StringComparison]::Ordinal)
}

function Remove-OwnedDirectory(
    [string]$Path,
    [string]$ExpectedParent,
    [string]$Prefix
) {
    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }
    if (-not (Test-OwnedSibling $Path $ExpectedParent $Prefix)) {
        throw 'Refusing to clean an unowned path.'
    }
    $item = Get-Item -LiteralPath $Path -Force
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'Refusing to clean a reparse point.'
    }
    Remove-Item -LiteralPath $Path -Recurse -Force
}

function Invoke-TestFault([string]$Stage) {
    if (-not $script:testMode) {
        return
    }
    if ($script:testFault -eq $Stage) {
        throw "Injected import failure at $Stage."
    }
}

function Invoke-PublicVerifier([string]$Directory) {
    $arguments = @(
        '-Dloader.main=com.portfolio.agent.release.PublicBundleVerificationCli',
        '-cp', $script:jarPath,
        'org.springframework.boot.loader.launch.PropertiesLauncher',
        $Directory
    )
    $lines = @(& $script:javaExecutable @arguments 2>&1 |
        ForEach-Object { "$_" })
    if ($LASTEXITCODE -ne 0) {
        throw 'External public Bundle verification failed.'
    }
    $jsonLine = @($lines | Where-Object {
            -not [string]::IsNullOrWhiteSpace($_)
        } | Select-Object -Last 1)
    if ($jsonLine.Count -ne 1) {
        throw 'External public Bundle verifier returned no identity.'
    }
    try {
        return $jsonLine[0] | ConvertFrom-Json
    }
    catch {
        throw 'External public Bundle verifier returned an invalid identity.'
    }
}

function Assert-IdentityAgreement(
    [object]$Identity,
    [object]$Manifest,
    [object]$Governance,
    [object]$Receipt,
    [string]$ExpectedRuntimeBundleHash
) {
    if ([string]$Identity.runtimeBundleHash -notmatch '^sha256:[0-9a-f]{64}$' -or
            (-not [string]::IsNullOrWhiteSpace($ExpectedRuntimeBundleHash) -and
                [string]$Identity.runtimeBundleHash -ne
                    $ExpectedRuntimeBundleHash) -or
            [string]$Identity.contentVersion -ne $TargetVersion -or
            [string]$Manifest.contentVersion -ne $TargetVersion -or
            [string]$Governance.verifiedVersion -ne $TargetVersion -or
            [string]$Receipt.contentVersion -ne $TargetVersion -or
            [string]$Identity.candidatePayloadHash -ne
                [string]$Manifest.candidatePayloadHash -or
            [string]$Governance.candidatePayloadHash -ne
                [string]$Manifest.candidatePayloadHash -or
            [string]$Receipt.candidatePayloadHash -ne
                [string]$Manifest.candidatePayloadHash -or
            [string]$Identity.ledgerHash -ne [string]$Manifest.ledgerHash -or
            [string]$Governance.ledgerHash -ne [string]$Manifest.ledgerHash -or
            [string]$Receipt.ledgerHash -ne [string]$Manifest.ledgerHash -or
            [string]$Receipt.approvalId -ne [string]$Manifest.approvalId -or
            [string]$Receipt.runId -ne [string]$Governance.runId -or
            [string]$Receipt.action -ne 'RELEASE_VERIFIED' -or
            [string]$Governance.status -ne 'PASS' -or
            [string]$Governance.command -ne 'verify') {
        throw 'Governance receipt and public Bundle identities do not agree.'
    }
}

$testVariables = @(
    $env:PUBLIC_RELEASE_IMPORT_TEST_ROOT,
    $env:PUBLIC_RELEASE_IMPORT_TARGET,
    $env:PUBLIC_RELEASE_IMPORT_GOVERNANCE,
    $env:PUBLIC_RELEASE_IMPORT_JAVA,
    $env:PUBLIC_RELEASE_IMPORT_JAR,
    $env:PUBLIC_RELEASE_IMPORT_FAULT
)
$testMode = $env:PUBLIC_RELEASE_IMPORT_TEST_MODE -eq '1'
if (-not $testMode -and
        @($testVariables | Where-Object {
                -not [string]::IsNullOrWhiteSpace($_)
            }).Count -gt 0) {
    throw 'Import test controls require explicit test mode.'
}
$testFault = ''
if ($testMode) {
    $testRoot = Resolve-SafeExistingPath `
        $env:PUBLIC_RELEASE_IMPORT_TEST_ROOT 'testRoot' $true
    $testFault = [string]$env:PUBLIC_RELEASE_IMPORT_FAULT
    if (-not [string]::IsNullOrWhiteSpace($testFault) -and
            $testFault -notin $knownFaults) {
        throw 'Unknown import test fault.'
    }
}

if ($TargetVersion -notmatch '^[0-9]{4}-[0-9]{2}-[0-9]{2}\.[0-9]+$') {
    throw 'TargetVersion is invalid.'
}
$resolvedReleaseRoot = Resolve-SafeExistingPath $ReleaseRoot 'ReleaseRoot' $true
$resolvedWorkspace = Resolve-SafeExistingPath $Workspace 'Workspace' $true
$resolvedLedger = Resolve-SafeExistingPath $DecisionLedger 'DecisionLedger' $false
if (-not (Test-Contained $resolvedLedger $resolvedWorkspace)) {
    throw 'DecisionLedger must be inside Workspace.'
}
$versionsRoot = Resolve-SafeExistingPath `
    (Join-Path $resolvedReleaseRoot 'versions') 'versions' $true
$sourceDirectory = Resolve-SafeExistingPath `
    (Join-Path $versionsRoot $TargetVersion) 'TargetVersion' $true
if (-not (Test-Contained $sourceDirectory $versionsRoot)) {
    throw 'TargetVersion escapes ReleaseRoot.'
}
Assert-ClosedSevenFileDirectory $sourceDirectory

$targetDirectory = if ($testMode) {
    $candidate = [IO.Path]::GetFullPath($env:PUBLIC_RELEASE_IMPORT_TARGET)
    if (-not (Test-Contained $candidate $testRoot)) {
        throw 'Test target escapes test root.'
    }
    Resolve-SafeExistingPath $candidate 'testTarget' $true
}
else {
    Resolve-SafeExistingPath (
        Join-Path $repositoryRoot `
            'backend\src\main\resources\public-data\bundle'
    ) 'runtimeBundle' $true
}
$targetParent = Split-Path -Parent $targetDirectory

$governanceScript = if ($testMode) {
    Resolve-SafeExistingPath `
        $env:PUBLIC_RELEASE_IMPORT_GOVERNANCE 'testGovernance' $false
}
else {
    Join-Path $repositoryRoot 'scripts\portfolio-governance.ps1'
}
$javaExecutable = if ($testMode) {
    Resolve-SafeExistingPath $env:PUBLIC_RELEASE_IMPORT_JAVA 'testJava' $false
}
else {
    (Get-Command java.exe -ErrorAction Stop).Source
}
$jarPath = if ($testMode) {
    Resolve-SafeExistingPath $env:PUBLIC_RELEASE_IMPORT_JAR 'testJar' $false
}
else {
    Resolve-SafeExistingPath (
        Join-Path $repositoryRoot 'backend\target\portfolio-agent.jar'
    ) 'portfolioAgentJar' $false
}

$governanceLines = @(& powershell.exe -NoProfile -ExecutionPolicy Bypass `
    -File $governanceScript `
    -Command verify `
    -Workspace $resolvedWorkspace `
    -DecisionLedger $resolvedLedger `
    -ReleaseRoot $resolvedReleaseRoot `
    -TargetVersion $TargetVersion 2>&1 | ForEach-Object { "$_" })
if ($LASTEXITCODE -ne 0) {
    throw 'Tracked governance verification failed.'
}
try {
    $governance = @($governanceLines | Where-Object {
            -not [string]::IsNullOrWhiteSpace($_)
        } | Select-Object -Last 1)[0] | ConvertFrom-Json
}
catch {
    throw 'Tracked governance verification returned an invalid receipt identity.'
}
$receiptPath = Join-Path $resolvedWorkspace 'audit\verify.jsonl'
if (-not (Test-Path -LiteralPath $receiptPath -PathType Leaf)) {
    throw 'Governance verify receipt is missing.'
}
try {
    $receipt = @(Get-Content -LiteralPath $receiptPath -Encoding UTF8 |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Select-Object -Last 1)[0] | ConvertFrom-Json
    $manifest = Get-Content -LiteralPath (
        Join-Path $sourceDirectory 'manifest.json') -Raw -Encoding UTF8 |
        ConvertFrom-Json
}
catch {
    throw 'Governance verify receipt or Manifest is invalid.'
}
$sourceIdentity = Invoke-PublicVerifier $sourceDirectory
Assert-IdentityAgreement $sourceIdentity $manifest $governance $receipt ''

$temporary = Join-Path $targetParent (
    $ownedTemporaryPrefix + [guid]::NewGuid().ToString('N'))
$backup = Join-Path $targetParent (
    $ownedBackupPrefix + [guid]::NewGuid().ToString('N'))
$backupCreated = $false
$newInstalled = $false
$finalVerified = $false
try {
    New-Item -ItemType Directory -Path $temporary | Out-Null
    foreach ($name in $fileNames) {
        Invoke-TestFault 'Copy'
        Copy-Item -LiteralPath (Join-Path $sourceDirectory $name) `
            -Destination (Join-Path $temporary $name)
    }
    Assert-ClosedSevenFileDirectory $temporary
    foreach ($name in $fileNames) {
        Invoke-TestFault 'Readback'
        if ((Get-FileHashValue (Join-Path $sourceDirectory $name)) -ne
                (Get-FileHashValue (Join-Path $temporary $name))) {
            throw 'Temporary Bundle readback hash mismatch.'
        }
    }
    Invoke-TestFault 'TempVerify'
    $temporaryIdentity = Invoke-PublicVerifier $temporary
    Assert-IdentityAgreement $temporaryIdentity $manifest $governance $receipt `
        ([string]$sourceIdentity.runtimeBundleHash)

    Invoke-TestFault 'RenameCurrent'
    Move-Item -LiteralPath $targetDirectory -Destination $backup
    $backupCreated = $true
    Invoke-TestFault 'RenameTemp'
    Move-Item -LiteralPath $temporary -Destination $targetDirectory
    $newInstalled = $true
    Invoke-TestFault 'FinalVerify'
    $targetIdentity = Invoke-PublicVerifier $targetDirectory
    Assert-IdentityAgreement $targetIdentity $manifest $governance $receipt `
        ([string]$sourceIdentity.runtimeBundleHash)
    $finalVerified = $true
}
catch {
    if ($backupCreated -and (Test-Path -LiteralPath $backup)) {
        if ($newInstalled -and (Test-Path -LiteralPath $targetDirectory)) {
            Remove-Item -LiteralPath $targetDirectory -Recurse -Force
        }
        Move-Item -LiteralPath $backup -Destination $targetDirectory
        $backupCreated = $false
    }
    if (Test-Path -LiteralPath $temporary) {
        Remove-OwnedDirectory $temporary $targetParent $ownedTemporaryPrefix
    }
    throw 'Public release import failed; the previous Bundle was restored.'
}

if (-not $finalVerified) {
    throw 'Public release import did not reach final verification.'
}
try {
    Invoke-TestFault 'BackupDelete'
    Remove-OwnedDirectory $backup $targetParent $ownedBackupPrefix
    $backupCreated = $false
}
catch {
    Write-Warning (
        'Public release imported; recoverable backup cleanup warning: ' +
        (Split-Path -Leaf $backup))
}
Write-Output 'Verified public release imported.'
