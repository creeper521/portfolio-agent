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

$scriptRoot = $PSScriptRoot
$watcherPath = Join-Path $scriptRoot 'watch-local-logs.ps1'
. $watcherPath

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('local-log-watch-' + [guid]::NewGuid())
try {
    $current = Join-Path $tempRoot 'current'
    [System.IO.Directory]::CreateDirectory($current) | Out-Null
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllLines(
        (Join-Path $current 'backend-error.log'),
        @(
            '2026-07-31T10:00:01.000+08:00 [BACKEND][ERROR][SPRING] first',
            '2026-07-31T10:00:03.000+08:00 [BACKEND][ERROR][SPRING] third'
        ),
        $utf8
    )
    [System.IO.File]::WriteAllLines(
        (Join-Path $current 'frontend-error.log'),
        @('2026-07-31T10:00:02.000+08:00 [FRONTEND][ERROR][VITE] second'),
        $utf8
    )
    [System.IO.File]::WriteAllLines(
        (Join-Path $current 'backend-info.log'),
        @('2026-07-31T10:00:04.000+08:00 [BACKEND][INFO][SPRING] info'),
        $utf8
    )
    [System.IO.File]::WriteAllText((Join-Path $current 'frontend-info.log'), '', $utf8)

    $result = @(& powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File $watcherPath `
        -LogDirectory $tempRoot `
        -Level ERROR `
        -Source BACKEND `
        -Tail 2 `
        -NoColor `
        -Once)
    Assert-Equal 2 $result.Count 'Tail count'
    Assert-True (@($result | Where-Object { $_ -notmatch '\[BACKEND\]\[ERROR\]' }).Count -eq 0) `
        'All lines must match backend errors'
    Assert-True (-not (($result -join "`n").Contains([char]27))) 'No-color output must contain no ANSI'

    $states = @{}
    $firstPoll = @(Read-LocalLogActivePoll -States $states -CurrentDirectory $current)
    Assert-Equal 4 $firstPoll.Count 'Initial poll count'
    [System.IO.File]::WriteAllText(
        (Join-Path $current 'backend-info.log'),
        "2026-07-31T10:00:05.000+08:00 [BACKEND][INFO][SPRING] replacement`r`n",
        $utf8
    )
    $replacementPoll = @(Read-LocalLogActivePoll -States $states -CurrentDirectory $current)
    Assert-Equal 1 $replacementPoll.Count 'Replacement must resume once from zero'
    Assert-True $replacementPoll[0].Contains('replacement') 'Replacement line'
    $repeatedReplacement = @(Read-LocalLogActivePoll -States $states -CurrentDirectory $current)
    Assert-Equal 0 $repeatedReplacement.Count 'Replacement must not repeat'

    $partialPath = Join-Path $current 'frontend-info.log'
    $firstChineseCharacter = [char]0x534A
    $secondChineseCharacter = [char]0x884C
    [System.IO.File]::WriteAllText(
        $partialPath,
        ('2026-07-31T10:00:06.000+08:00 [FRONTEND][INFO][BROWSER] ' + $firstChineseCharacter),
        $utf8
    )
    $partialPoll = @(Read-LocalLogActivePoll -States $states -CurrentDirectory $current)
    Assert-Equal 0 $partialPoll.Count 'Partial UTF-8 line must wait'
    [System.IO.File]::AppendAllText($partialPath, "$secondChineseCharacter`r`n", $utf8)
    $completed = @(Read-LocalLogActivePoll -States $states -CurrentDirectory $current)
    Assert-Equal 1 $completed.Count 'Completed UTF-8 line'
    Assert-True $completed[0].Contains("$firstChineseCharacter$secondChineseCharacter") `
        'UTF-8 text must be intact'

    $archiveDirectory = Join-Path $tempRoot 'archive'
    $sourceDirectory = Join-Path $tempRoot 'zip-source'
    [System.IO.Directory]::CreateDirectory($archiveDirectory) | Out-Null
    [System.IO.Directory]::CreateDirectory($sourceDirectory) | Out-Null
    [System.IO.File]::WriteAllLines(
        (Join-Path $sourceDirectory 'backend-error-2026-07-30.log'),
        @('2026-07-30T10:00:00.000+08:00 [BACKEND][ERROR][SPRING] archived'),
        $utf8
    )
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zipPath = Join-Path $archiveDirectory 'portfolio-agent-2026-07-30.zip'
    [System.IO.Compression.ZipFile]::CreateFromDirectory($sourceDirectory, $zipPath)
    $archiveOutput = @(& powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File $watcherPath `
        -LogDirectory $tempRoot `
        -ArchiveDate '2026-07-30' `
        -NoColor)
    Assert-Equal 1 $archiveOutput.Count 'Archive stream count'
    Assert-True $archiveOutput[0].Contains('archived') 'Archive stream content'
    Assert-Equal 1 @(Get-ChildItem -LiteralPath $sourceDirectory -File).Count `
        'Archive viewing must not extract files'
} finally {
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}

Write-Output 'watch-local-logs tests passed'
