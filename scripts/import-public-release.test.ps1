$ErrorActionPreference = 'Stop'
$script = Join-Path $PSScriptRoot 'import-public-release.ps1'
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) `
    ('import-public-release-' + [guid]::NewGuid().ToString('N'))
$releaseRoot = Join-Path $fixtureRoot 'release'
$source = Join-Path $releaseRoot 'versions\2026-07-24.1'
$workspace = Join-Path $fixtureRoot 'workspace'
$ledger = Join-Path $workspace 'decision-ledger.json'
$target = Join-Path $fixtureRoot 'runtime-parent\bundle'
$fakeGovernance = Join-Path $fixtureRoot 'fake-governance.ps1'
$fakeJava = Join-Path $fixtureRoot 'fake-java.cmd'
$fakeJavaScript = Join-Path $fixtureRoot 'fake-java.ps1'
$fileNames = @(
    'checksums.json',
    'keyword-index.json',
    'manifest.json',
    'portfolio.json',
    'presentation.json',
    'rag-documents.jsonl',
    'vector-index.bin'
)
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$realRuntimeBundle = Join-Path $repositoryRoot `
    'backend\src\main\resources\public-data\bundle'
$repositoryAttackTarget = Join-Path $repositoryRoot `
    '.superpowers\sdd\f4-repository-root-attack-target'

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

function Get-DirectoryDigest([string]$Directory) {
    $result = [ordered]@{}
    foreach ($name in $fileNames) {
        $result[$name] = [BitConverter]::ToString(
            [IO.File]::ReadAllBytes((Join-Path $Directory $name))) -replace '-', ''
    }
    return ($result | ConvertTo-Json -Compress)
}

function Write-Bundle([string]$Directory, [string]$Marker) {
    New-Item -ItemType Directory -Force -Path $Directory | Out-Null
    foreach ($name in $fileNames) {
        if ($name -eq 'manifest.json') {
            [ordered]@{
                schemaVersion = '3.0'
                contentVersion = if ($Marker -eq 'new') {
                    '2026-07-24.1'
                } else {
                    '2026-07-23.1'
                }
                approvalId = 'APR-0123456789abcdef0123456789abcdef'
                candidatePayloadHash = 'sha256:' + ('2' * 64)
                ledgerHash = 'sha256:' + ('1' * 64)
            } | ConvertTo-Json -Compress |
                Set-Content -LiteralPath (Join-Path $Directory $name) -Encoding UTF8
        }
        else {
            Set-Content -LiteralPath (Join-Path $Directory $name) `
                -Value "$Marker-$name" -Encoding UTF8
        }
    }
}

function Assert-TestFixtureIsolation {
    $temporaryParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar)
    Assert-True ((Split-Path -Parent $fixtureRoot).Equals(
            $temporaryParent, [StringComparison]::OrdinalIgnoreCase)) `
        'Test fixture root must be an immediate child of the system temp directory.'
    Assert-True ((Split-Path -Leaf $fixtureRoot) -match
            '^import-public-release-[0-9a-f]{32}$') `
        'Test fixture root must use the dedicated random prefix.'
    Assert-True (-not [IO.Path]::GetFullPath($target).StartsWith(
            [IO.Path]::GetFullPath($repositoryRoot),
            [StringComparison]::OrdinalIgnoreCase)) `
        'Default synthetic target must not be inside the repository.'
    Assert-True (-not [IO.Path]::GetFullPath($target).Equals(
            [IO.Path]::GetFullPath($realRuntimeBundle),
            [StringComparison]::OrdinalIgnoreCase)) `
        'Default synthetic target must never be the real runtime Bundle.'
}

function Reset-Fixture {
    foreach ($path in @($source, $target)) {
        if (Test-Path -LiteralPath $path) {
            Remove-Item -LiteralPath $path -Recurse -Force
        }
    }
    Get-ChildItem -LiteralPath (Split-Path $target -Parent) -Force `
        -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like '.public-release-import-*' } |
        Remove-Item -Recurse -Force
    if (Test-Path -LiteralPath (Join-Path $workspace 'audit')) {
        Remove-Item -LiteralPath (Join-Path $workspace 'audit') -Recurse -Force
    }
    Write-Bundle $source 'new'
    Write-Bundle $target 'old'
    Set-Content -LiteralPath (Join-Path (Split-Path $target -Parent) `
        'unrelated.keep') -Value 'preserve'
    $env:PUBLIC_RELEASE_IMPORT_FAULT = ''
    $env:PUBLIC_RELEASE_IMPORT_TEST_MODE = '1'
    $env:PUBLIC_RELEASE_IMPORT_TEST_ROOT = $fixtureRoot
    $env:PUBLIC_RELEASE_IMPORT_TARGET = $target
    $env:PUBLIC_RELEASE_IMPORT_GOVERNANCE = $fakeGovernance
    $env:PUBLIC_RELEASE_IMPORT_JAVA = $fakeJava
    $env:PUBLIC_RELEASE_IMPORT_JAR = Join-Path $fixtureRoot 'fake.jar'
    $env:PUBLIC_RELEASE_IMPORT_GOVERNANCE_MODE = 'MATCH'
    $env:PUBLIC_RELEASE_IMPORT_VERIFIER_MODE = 'MATCH'
    Assert-TestFixtureIsolation
}

function Invoke-Import([string[]]$Arguments) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $lines = @(& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $script @Arguments 2>&1 | ForEach-Object { "$_" })
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previous
    }
    return @{
        ExitCode = $exitCode
        Output = ($lines -join [Environment]::NewLine)
    }
}

function Valid-Arguments {
    return @(
        '-ReleaseRoot', $releaseRoot,
        '-TargetVersion', '2026-07-24.1',
        '-Workspace', $workspace,
        '-DecisionLedger', $ledger
    )
}

try {
    New-Item -ItemType Directory -Force `
        -Path $source, $workspace, (Split-Path $target -Parent) | Out-Null
    Set-Content -LiteralPath $ledger -Value '{"schemaVersion":"1.0","assets":[]}' `
        -Encoding UTF8
    @'
param(
    [string]$Command,
    [string]$Workspace,
    [string]$DecisionLedger,
    [string]$ReleaseRoot,
    [string]$TargetVersion
)
$manifest = Get-Content -LiteralPath (
    Join-Path $ReleaseRoot (Join-Path 'versions' (
        Join-Path $TargetVersion 'manifest.json'))) -Raw -Encoding UTF8 |
    ConvertFrom-Json
$ledgerHash = [string]$manifest.ledgerHash
if ($env:PUBLIC_RELEASE_IMPORT_GOVERNANCE_MODE -eq 'MISMATCH') {
    $ledgerHash = 'sha256:' + ('9' * 64)
}
$runId = 'verify-test-run'
$receipt = [ordered]@{
    runId = $runId
    action = 'RELEASE_VERIFIED'
    approvalId = [string]$manifest.approvalId
    contentVersion = $TargetVersion
    candidatePayloadHash = [string]$manifest.candidatePayloadHash
    ledgerHash = $ledgerHash
    verifiedAt = '2026-07-24T00:00:00Z'
}
New-Item -ItemType Directory -Force -Path (Join-Path $Workspace 'audit') | Out-Null
$receipt | ConvertTo-Json -Compress |
    Add-Content -LiteralPath (Join-Path $Workspace 'audit\verify.jsonl') -Encoding UTF8
[ordered]@{
    runId = $runId
    command = 'verify'
    status = 'PASS'
    verifiedVersion = $TargetVersion
    candidatePayloadHash = [string]$manifest.candidatePayloadHash
    ledgerHash = $ledgerHash
    artifacts = @('audit\verify.jsonl')
} | ConvertTo-Json -Compress | Write-Output
exit 0
'@ | Set-Content -LiteralPath $fakeGovernance -Encoding UTF8
    @'
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
$directory = $Arguments[$Arguments.Count - 1]
$manifest = Get-Content -LiteralPath (Join-Path $directory 'manifest.json') `
    -Raw -Encoding UTF8 | ConvertFrom-Json
$runtimeBundleHash = 'sha256:' + ('3' * 64)
if ($env:PUBLIC_RELEASE_IMPORT_VERIFIER_MODE -eq 'FINAL_RUNTIME_MISMATCH' -and
        (Split-Path -Leaf $directory) -eq 'bundle') {
    $runtimeBundleHash = 'sha256:' + ('8' * 64)
}
[ordered]@{
    schemaVersion = [string]$manifest.schemaVersion
    contentVersion = [string]$manifest.contentVersion
    candidatePayloadHash = [string]$manifest.candidatePayloadHash
    ledgerHash = [string]$manifest.ledgerHash
    runtimeBundleHash = $runtimeBundleHash
    chunkCount = 1
} | ConvertTo-Json -Compress | Write-Output
exit 0
'@ | Set-Content -LiteralPath $fakeJavaScript -Encoding UTF8
    @"
@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$fakeJavaScript" %*
exit /b %ERRORLEVEL%
"@ | Set-Content -LiteralPath $fakeJava -Encoding ASCII

    $env:PUBLIC_RELEASE_IMPORT_TEST_MODE = '1'
    $env:PUBLIC_RELEASE_IMPORT_TEST_ROOT = $fixtureRoot
    $env:PUBLIC_RELEASE_IMPORT_TARGET = $target
    $env:PUBLIC_RELEASE_IMPORT_GOVERNANCE = $fakeGovernance
    $env:PUBLIC_RELEASE_IMPORT_JAVA = $fakeJava
    $env:PUBLIC_RELEASE_IMPORT_JAR = Join-Path $fixtureRoot 'fake.jar'
    Set-Content -LiteralPath $env:PUBLIC_RELEASE_IMPORT_JAR -Value 'fake'

    foreach ($missing in @(
            'ReleaseRoot', 'TargetVersion', 'Workspace', 'DecisionLedger')) {
        Reset-Fixture
        $arguments = @(Valid-Arguments)
        $index = [Array]::IndexOf($arguments, "-$missing")
        $filtered = New-Object System.Collections.Generic.List[string]
        for ($argumentIndex = 0;
                $argumentIndex -lt $arguments.Count;
                $argumentIndex++) {
            if ($argumentIndex -ne $index -and
                    $argumentIndex -ne ($index + 1)) {
                $filtered.Add([string]$arguments[$argumentIndex])
            }
        }
        $arguments = $filtered.ToArray()
        $oldDigest = Get-DirectoryDigest $target
        $result = Invoke-Import $arguments
        Assert-True ($result.ExitCode -ne 0) "$missing must be required."
        Assert-True ((Get-DirectoryDigest $target) -eq $oldDigest) `
            "$missing failure must not mutate the old Bundle."
    }

    Reset-Fixture
    $oldDigest = Get-DirectoryDigest $target
    $result = Invoke-Import @(
        '-ReleaseRoot', (Join-Path $releaseRoot '..\release'),
        '-TargetVersion', '2026-07-24.1',
        '-Workspace', $workspace,
        '-DecisionLedger', $ledger)
    Assert-True ($result.ExitCode -ne 0) 'Traversal syntax must be rejected.'
    Assert-True ((Get-DirectoryDigest $target) -eq $oldDigest) `
        'Unsafe path rejection must preserve the old Bundle.'

    Reset-Fixture
    try {
        if (Test-Path -LiteralPath $repositoryAttackTarget) {
            Remove-Item -LiteralPath $repositoryAttackTarget -Recurse -Force
        }
        Write-Bundle $repositoryAttackTarget 'old'
        $env:PUBLIC_RELEASE_IMPORT_TEST_ROOT = $repositoryRoot
        $env:PUBLIC_RELEASE_IMPORT_TARGET = $repositoryAttackTarget
        $oldDigest = Get-DirectoryDigest $repositoryAttackTarget
        $result = Invoke-Import (Valid-Arguments)
        Assert-True ($result.ExitCode -ne 0) `
            'Test mode must reject the repository root as TEST_ROOT.'
        Assert-True ((Get-DirectoryDigest $repositoryAttackTarget) -eq $oldDigest) `
            'Repository-root test injection must fail before target mutation.'
        Assert-True (-not (Test-Path -LiteralPath (Join-Path $workspace 'audit'))) `
            'Repository-root test injection must fail before governance invocation.'
    }
    finally {
        if (Test-Path -LiteralPath $repositoryAttackTarget) {
            Remove-Item -LiteralPath $repositoryAttackTarget -Recurse -Force
        }
    }

    Reset-Fixture
    try {
        Write-Bundle $repositoryAttackTarget 'old'
        $env:PUBLIC_RELEASE_IMPORT_TARGET = $repositoryAttackTarget
        $oldDigest = Get-DirectoryDigest $repositoryAttackTarget
        $result = Invoke-Import (Valid-Arguments)
        Assert-True ($result.ExitCode -ne 0) `
            'Test mode must reject every repository target.'
        Assert-True ((Get-DirectoryDigest $repositoryAttackTarget) -eq $oldDigest) `
            'Repository target injection must fail before mutation.'
        Assert-True (-not (Test-Path -LiteralPath (Join-Path $workspace 'audit'))) `
            'Repository target injection must fail before governance invocation.'
    }
    finally {
        if (Test-Path -LiteralPath $repositoryAttackTarget) {
            Remove-Item -LiteralPath $repositoryAttackTarget -Recurse -Force
        }
    }

    Reset-Fixture
    foreach ($linkType in @('Junction', 'SymbolicLink')) {
        $link = Join-Path $fixtureRoot ('release-' + $linkType.ToLowerInvariant())
        $linkCreated = $false
        try {
            New-Item -ItemType $linkType -Path $link -Target $releaseRoot `
                -ErrorAction Stop | Out-Null
            $linkCreated = $true
        }
        catch {
            Write-Warning "SKIP: $linkType rejection scenario unavailable on this platform."
        }
        if ($linkCreated) {
            try {
                $oldDigest = Get-DirectoryDigest $target
                $result = Invoke-Import @(
                    '-ReleaseRoot', $link,
                    '-TargetVersion', '2026-07-24.1',
                    '-Workspace', $workspace,
                    '-DecisionLedger', $ledger)
                Assert-True ($result.ExitCode -ne 0) `
                    "$linkType release paths must be rejected."
                Assert-True ((Get-DirectoryDigest $target) -eq $oldDigest) `
                    "$linkType rejection must preserve the old Bundle."
            }
            finally {
                if (Test-Path -LiteralPath $link) {
                    [IO.Directory]::Delete($link)
                }
            }
        }
    }

    foreach ($invalidName in @('missing', 'extra')) {
        Reset-Fixture
        if ($invalidName -eq 'missing') {
            Remove-Item -LiteralPath (Join-Path $source 'vector-index.bin')
        }
        else {
            Set-Content -LiteralPath (Join-Path $source 'approval.json') -Value '{}'
        }
        $oldDigest = Get-DirectoryDigest $target
        $result = Invoke-Import (Valid-Arguments)
        Assert-True ($result.ExitCode -ne 0) `
            "$invalidName external file set must fail."
        Assert-True ((Get-DirectoryDigest $target) -eq $oldDigest) `
            "$invalidName external file set must preserve the old Bundle."
    }

    Reset-Fixture
    $env:PUBLIC_RELEASE_IMPORT_GOVERNANCE_MODE = 'MISMATCH'
    $oldDigest = Get-DirectoryDigest $target
    $result = Invoke-Import (Valid-Arguments)
    Assert-True ($result.ExitCode -ne 0) `
        'Mismatched governance result and receipt must fail.'
    Assert-True ((Get-DirectoryDigest $target) -eq $oldDigest) `
        'Governance mismatch must preserve the old Bundle.'

    foreach ($fault in @(
            'Copy', 'Readback', 'TempVerify', 'RenameCurrent',
            'RenameTemp', 'FinalVerify')) {
        Reset-Fixture
        $env:PUBLIC_RELEASE_IMPORT_FAULT = $fault
        $oldDigest = Get-DirectoryDigest $target
        $result = Invoke-Import (Valid-Arguments)
        Assert-True ($result.ExitCode -ne 0) "$fault must return nonzero."
        Assert-True ((Get-DirectoryDigest $target) -eq $oldDigest) `
            "$fault must restore the byte-identical old Bundle."
        $names = @(Get-ChildItem -LiteralPath (Split-Path $target -Parent) -Force |
            ForEach-Object { $_.Name })
        Assert-True (($names | Where-Object {
                    $_ -like '.public-release-import-temp-*'
                }).Count -eq 0) "$fault must clean task-owned temp paths."
        Assert-True (Test-Path -LiteralPath $target -PathType Container) `
            "$fault must not leave the target missing."
        Assert-True (Test-Path -LiteralPath (
                Join-Path (Split-Path $target -Parent) 'unrelated.keep')) `
            "$fault must not delete unrelated sibling content."
    }

    foreach ($rollbackFault in @(
            'QuarantineRename', 'BackupRestore', 'QuarantineCleanup')) {
        Reset-Fixture
        $env:PUBLIC_RELEASE_IMPORT_FAULT = $rollbackFault
        $oldDigest = Get-DirectoryDigest $target
        $result = Invoke-Import (Valid-Arguments)
        Assert-True ($result.ExitCode -ne 0) `
            "$rollbackFault must fail closed."
        Assert-True ($result.Output -match
                'PUBLIC_RELEASE_IMPORT_MANUAL_RECOVERY_REQUIRED') `
            "$rollbackFault must print the stable manual-recovery error."
        Assert-True ($result.Output -notmatch [regex]::Escape($fixtureRoot)) `
            "$rollbackFault output must not expose an absolute path."
        Assert-True (Test-Path -LiteralPath (
                Join-Path (Split-Path $target -Parent) 'unrelated.keep')) `
            "$rollbackFault must preserve unrelated sibling content."
        $siblings = @(Get-ChildItem -LiteralPath (Split-Path $target -Parent) -Force)
        $backups = @($siblings | Where-Object {
                $_.Name -like '.public-release-import-backup-*'
            })
        $quarantines = @($siblings | Where-Object {
                $_.Name -like '.public-release-import-quarantine-*'
            })
        if ($rollbackFault -eq 'QuarantineRename') {
            Assert-True ((Get-DirectoryDigest $target) -eq
                    (Get-DirectoryDigest $source)) `
                'Quarantine rename failure must keep the whole new Bundle intact.'
            Assert-True ($backups.Count -eq 1 -and $quarantines.Count -eq 0) `
                'Quarantine rename failure must preserve the backup beside the target.'
        }
        elseif ($rollbackFault -eq 'BackupRestore') {
            Assert-True (-not (Test-Path -LiteralPath $target)) `
                'Backup restore failure must leave target absent, never mixed.'
            Assert-True ($backups.Count -eq 1 -and $quarantines.Count -eq 1) `
                'Backup restore failure must preserve backup and quarantine.'
        }
        else {
            Assert-True ((Get-DirectoryDigest $target) -eq $oldDigest) `
                'Quarantine cleanup failure must leave the restored old Bundle active.'
            Assert-True ($backups.Count -eq 0 -and $quarantines.Count -eq 1) `
                'Quarantine cleanup failure must preserve only quarantine for recovery.'
        }
    }

    Reset-Fixture
    $env:PUBLIC_RELEASE_IMPORT_VERIFIER_MODE = 'FINAL_RUNTIME_MISMATCH'
    $oldDigest = Get-DirectoryDigest $target
    $result = Invoke-Import (Valid-Arguments)
    Assert-True ($result.ExitCode -ne 0) `
        'Final verifier runtime Bundle identity mismatch must fail.'
    Assert-True ((Get-DirectoryDigest $target) -eq $oldDigest) `
        'Final verifier identity mismatch must restore the old Bundle.'

    Reset-Fixture
    $env:PUBLIC_RELEASE_IMPORT_FAULT = 'UnknownFault'
    $oldDigest = Get-DirectoryDigest $target
    $result = Invoke-Import (Valid-Arguments)
    Assert-True ($result.ExitCode -ne 0) `
        'Unknown test fault value must fail before mutation.'
    Assert-True ((Get-DirectoryDigest $target) -eq $oldDigest) `
        'Unknown test fault must preserve the old Bundle.'

    Reset-Fixture
    $env:PUBLIC_RELEASE_IMPORT_TEST_MODE = ''
    $env:PUBLIC_RELEASE_IMPORT_FAULT = 'RenameTemp'
    $result = Invoke-Import (Valid-Arguments)
    Assert-True ($result.ExitCode -ne 0 -or
        (Get-Content -LiteralPath (Join-Path $target 'portfolio.json') -Raw) -match '^new') `
        'A lone fault variable must never inject the requested failure.'

    Reset-Fixture
    $result = Invoke-Import (Valid-Arguments)
    Assert-True ($result.ExitCode -eq 0) 'Exact seven-file import must succeed.'
    Assert-True ((Get-DirectoryDigest $target) -eq (Get-DirectoryDigest $source)) `
        'Successful import must preserve all exact seven source bytes.'
    Assert-True (@(Get-ChildItem -LiteralPath $target -File).Count -eq 7) `
        'Successful import must contain exactly seven public files.'
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $target 'approval.json'))) `
        'Import must never copy private governance records.'
    Assert-True (Test-Path -LiteralPath (
            Join-Path (Split-Path $target -Parent) 'unrelated.keep')) `
        'Successful import must not delete unrelated sibling content.'

    Reset-Fixture
    $env:PUBLIC_RELEASE_IMPORT_FAULT = 'BackupDelete'
    $result = Invoke-Import (Valid-Arguments)
    Assert-True ($result.ExitCode -eq 0) `
        'Backup cleanup failure after final verification must remain successful.'
    Assert-True ($result.Output -match 'recoverable backup cleanup warning') `
        'Backup cleanup failure must print an explicit recoverable warning.'
    Assert-True ((Get-DirectoryDigest $target) -eq (Get-DirectoryDigest $source)) `
        'Backup cleanup warning must keep the verified new Bundle active.'

    Write-Output 'import-public-release tests passed (26 scenarios; platform skips explicit)'
}
finally {
    foreach ($name in @(
            'PUBLIC_RELEASE_IMPORT_TEST_MODE',
            'PUBLIC_RELEASE_IMPORT_TEST_ROOT',
            'PUBLIC_RELEASE_IMPORT_TARGET',
            'PUBLIC_RELEASE_IMPORT_GOVERNANCE',
            'PUBLIC_RELEASE_IMPORT_JAVA',
            'PUBLIC_RELEASE_IMPORT_JAR',
            'PUBLIC_RELEASE_IMPORT_FAULT',
            'PUBLIC_RELEASE_IMPORT_GOVERNANCE_MODE',
            'PUBLIC_RELEASE_IMPORT_VERIFIER_MODE')) {
        Remove-Item "Env:$name" -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
    if (Test-Path -LiteralPath $repositoryAttackTarget) {
        Remove-Item -LiteralPath $repositoryAttackTarget -Recurse -Force
    }
}
