$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Assert-Equal {
    param($Expected, $Actual, [string]$Message)
    if ($Expected -ne $Actual) {
        throw "$Message. Expected=[$Expected] Actual=[$Actual]"
    }
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$modulePath = Join-Path $PSScriptRoot 'logging\LocalLogRouter.psm1'
Import-Module $modulePath -Force -DisableNameChecking

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('local-log-archive-' + [guid]::NewGuid())
$router = $null
try {
    $logRoot = Join-Path $tempRoot 'logs'
    $clockValue = [DateTimeOffset]::Parse('2026-07-31T15:23:01+08:00')
    $router = New-LocalLogRouter `
        -RepositoryRoot $repositoryRoot `
        -LogDirectory $logRoot `
        -Clock { $clockValue } `
        -MaxFileBytes 1024 `
        -MaxSegments 3 `
        -QueueCapacity 128
    Submit-LocalLogLine -Router $router -Stream VITE_STDOUT -Line 'INFO daily-archive-sentinel'
    Invoke-LocalLogDateRollover -Router $router -NewDate '2026-08-01'

    $dailyZip = Join-Path $logRoot 'archive\portfolio-agent-2026-07-31.zip'
    Assert-True (Test-Path -LiteralPath $dailyZip) 'Daily ZIP must exist'
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $logRoot 'staging\2026-07-31'))) `
        'Verified staging must be removed'
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($dailyZip)
    try {
        $names = @($zip.Entries | ForEach-Object FullName)
        Assert-True ($names -contains 'manifest.json') 'ZIP must contain manifest'
        Assert-True ($names -contains 'frontend-info-2026-07-31.log') `
            'ZIP must contain dated frontend log'
    } finally {
        $zip.Dispose()
    }

    $snapshot = New-LocalLogSnapshot `
        -RepositoryRoot $repositoryRoot `
        -LogDirectory $logRoot `
        -Now ([DateTimeOffset]::Parse('2026-08-01T09:08:07+08:00'))
    Assert-True (Test-Path -LiteralPath $snapshot) 'Current-day snapshot must exist'
    Assert-True $snapshot.EndsWith('portfolio-agent-2026-08-01-090807.zip') 'Snapshot name'

    $recoveryStage = Join-Path $logRoot 'staging\2026-07-29'
    [System.IO.Directory]::CreateDirectory($recoveryStage) | Out-Null
    [System.IO.File]::WriteAllText(
        (Join-Path $recoveryStage 'backend-info-2026-07-29.log'),
        'recovery-sentinel'
    )
    $corruptTemporary = Join-Path $logRoot 'archive\portfolio-agent-2026-07-29.zip.tmp'
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($corruptTemporary)) | Out-Null
    [System.IO.File]::WriteAllText($corruptTemporary, 'not-a-zip')
    Invoke-LocalLogMaintenance -Router $router
    $recoveredArchive = Join-Path $logRoot 'archive\portfolio-agent-2026-07-29.zip'
    Assert-True (Test-Path $recoveredArchive) 'Corrupt temporary ZIP must be rebuilt from staging'
    Assert-True (-not (Test-Path $corruptTemporary)) 'Corrupt temporary ZIP must not remain'

    [System.IO.Directory]::CreateDirectory($recoveryStage) | Out-Null
    [System.IO.File]::WriteAllText(
        (Join-Path $recoveryStage 'backend-info-2026-07-29.log'),
        'recovery-sentinel'
    )
    Invoke-LocalLogMaintenance -Router $router
    Assert-True (-not (Test-Path $recoveryStage)) 'Matching final ZIP must clean duplicate staging'

    [System.IO.Directory]::CreateDirectory($recoveryStage) | Out-Null
    [System.IO.File]::WriteAllText(
        (Join-Path $recoveryStage 'backend-info-2026-07-29.log'),
        'conflicting-sentinel'
    )
    $conflictCode = ''
    try {
        Invoke-LocalLogMaintenance -Router $router
    } catch {
        $conflictCode = $_.Exception.Message
    }
    Assert-Equal 'LOG_ARCHIVE_CONFLICT' $conflictCode 'Conflicting final ZIP must never be overwritten'
    Assert-True (Test-Path $recoveryStage) 'Conflicting staging must remain for inspection'
    Remove-Item -LiteralPath $recoveryStage -Recurse -Force

    foreach ($age in @(8, 7, 6, 1)) {
        $date = ([datetime]'2026-08-01').AddDays(-$age).ToString('yyyy-MM-dd')
        $path = Join-Path $logRoot "archive\portfolio-agent-$date.zip"
        [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($path)) | Out-Null
        [System.IO.File]::WriteAllBytes($path, ([byte[]](1..20)))
    }
    $unknown = Join-Path $logRoot 'archive\keep-me.bin'
    [System.IO.File]::WriteAllText($unknown, 'unknown')

    Invoke-LocalLogRetention `
        -LogDirectory $logRoot `
        -Today '2026-08-01' `
        -RetentionDays 7 `
        -TotalArchiveBytes 1MB
    Assert-True (-not (Test-Path (Join-Path $logRoot 'archive\portfolio-agent-2026-07-24.zip'))) `
        'Eight-day archive must be removed'
    Assert-True (Test-Path (Join-Path $logRoot 'archive\portfolio-agent-2026-07-25.zip')) `
        'Seven-day archive must remain'
    Assert-True (Test-Path $unknown) 'Unknown archive file must remain'

    $protectedStaging = Join-Path $logRoot 'staging\manual-review'
    [System.IO.Directory]::CreateDirectory($protectedStaging) | Out-Null
    [System.IO.File]::WriteAllText((Join-Path $protectedStaging 'keep.txt'), 'keep')
    Invoke-LocalLogRetention `
        -LogDirectory $logRoot `
        -Today '2026-08-01' `
        -RetentionDays 7 `
        -TotalArchiveBytes 1
    Assert-True (Test-Path $protectedStaging) 'Retention must never delete staging'
    Assert-True (Test-Path $unknown) 'Size cleanup must preserve unknown files'

    Stop-LocalLogRouter -Router $router
    $router = $null

    $cliOutput = & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $PSScriptRoot 'archive-local-logs.ps1') `
        -LogDirectory $logRoot `
        -IncludeCurrentDay 2>&1
    Assert-Equal 0 $LASTEXITCODE "Archive CLI must pass. Output: $($cliOutput -join ' ')"
    Assert-True (($cliOutput -join "`n") -match 'LOG_SNAPSHOT_CREATED') 'CLI snapshot marker'
} finally {
    if ($null -ne $router) {
        Stop-LocalLogRouter -Router $router
    }
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}

Write-Output 'archive-local-logs tests passed'
