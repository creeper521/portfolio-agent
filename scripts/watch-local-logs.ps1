param(
    [string]$LogDirectory = '',
    [ValidateSet('DEBUG', 'INFO', 'WARN', 'ERROR')]
    [string]$Level = '',
    [ValidateSet('BACKEND', 'FRONTEND', 'SPRING', 'BROWSER', 'VITE', 'LAUNCHER')]
    [string]$Source = '',
    [ValidateRange(0, 100000)]
    [int]$Tail = 100,
    [switch]$NoColor,
    [ValidatePattern('^$|^\d{4}-\d{2}-\d{2}$')]
    [string]$ArchiveDate = '',
    [switch]$Once,
    [ValidateRange(10, 60000)]
    [int]$PollIntervalMilliseconds = 250
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function New-LocalLogWatchState {
    param([Parameter(Mandatory = $true)][string]$Path)

    return [pscustomobject]@{
        Path = [System.IO.Path]::GetFullPath($Path)
        Identity = $null
        Offset = 0L
        PendingBytes = [byte[]]@()
        PrefixLength = 0
        PrefixFingerprint = ''
    }
}

function ConvertFrom-LocalLogLine {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Line,
        [Parameter(Mandatory = $true)]
        [int]$StableOrder
    )

    if ($Line -notmatch '^(?<timestamp>\S+) \[(?<domain>BACKEND|FRONTEND)\]\[(?<level>DEBUG|INFO|WARN|ERROR)\]\[(?<source>SPRING|BROWSER|VITE|LAUNCHER)\] ') {
        return $null
    }
    $timestamp = [DateTimeOffset]::MinValue
    [void][DateTimeOffset]::TryParse($Matches.timestamp, [ref]$timestamp)
    return [pscustomobject]@{
        Timestamp = $timestamp
        Domain = $Matches.domain
        Level = $Matches.level
        Source = $Matches.source
        StableOrder = $StableOrder
        Line = $Line
    }
}

function Test-LocalLogWatchFilter {
    param(
        [Parameter(Mandatory = $true)]$Record,
        [string]$Level,
        [string]$Source
    )

    if (-not [string]::IsNullOrWhiteSpace($Level) -and $Record.Level -ne $Level) {
        return $false
    }
    if (-not [string]::IsNullOrWhiteSpace($Source) -and
        $Record.Domain -ne $Source -and
        $Record.Source -ne $Source) {
        return $false
    }
    return $true
}

function Read-LocalLogState {
    param(
        [Parameter(Mandatory = $true)]$State,
        [Parameter(Mandatory = $true)][int]$StableOrder
    )

    if (-not [System.IO.File]::Exists($State.Path)) {
        $State.Identity = $null
        $State.Offset = 0L
        $State.PendingBytes = [byte[]]@()
        $State.PrefixLength = 0
        $State.PrefixFingerprint = ''
        return @()
    }

    $info = [System.IO.FileInfo]::new($State.Path)
    $identity = "$($info.CreationTimeUtc.Ticks):$($info.LastWriteTimeUtc.Ticks)"
    $prefixChanged = $false
    if ($State.PrefixLength -gt 0 -and $info.Length -ge $State.PrefixLength) {
        $prefixStream = [System.IO.File]::Open(
            $State.Path,
            [System.IO.FileMode]::Open,
            [System.IO.FileAccess]::Read,
            [System.IO.FileShare]::ReadWrite
        )
        try {
            $prefixBytes = [byte[]]::new($State.PrefixLength)
            [void]$prefixStream.Read($prefixBytes, 0, $State.PrefixLength)
            $prefixChanged = [Convert]::ToBase64String($prefixBytes) -ne $State.PrefixFingerprint
        } finally {
            $prefixStream.Dispose()
        }
    }
    if ($null -ne $State.Identity -and
        ($info.Length -lt $State.Offset -or
         $prefixChanged -or
         ($identity -ne $State.Identity -and $info.Length -le $State.Offset))) {
        $State.Offset = 0L
        $State.PendingBytes = [byte[]]@()
        $State.PrefixLength = 0
        $State.PrefixFingerprint = ''
    }
    $State.Identity = $identity

    $stream = [System.IO.FileStream]::new(
        $State.Path,
        [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read,
        [System.IO.FileShare]::ReadWrite
    )
    try {
        [void]$stream.Seek($State.Offset, [System.IO.SeekOrigin]::Begin)
        $remaining = [int]($stream.Length - $State.Offset)
        $newBytes = [byte[]]::new([Math]::Max(0, $remaining))
        if ($remaining -gt 0) {
            $read = $stream.Read($newBytes, 0, $remaining)
            if ($read -lt $remaining) {
                $newBytes = $newBytes[0..($read - 1)]
            }
            $State.Offset += $read
        }
    } finally {
        $stream.Dispose()
    }

    $combined = [byte[]]::new($State.PendingBytes.Length + $newBytes.Length)
    [Array]::Copy($State.PendingBytes, 0, $combined, 0, $State.PendingBytes.Length)
    [Array]::Copy($newBytes, 0, $combined, $State.PendingBytes.Length, $newBytes.Length)
    $records = @()
    $lineStart = 0
    for ($index = 0; $index -lt $combined.Length; $index++) {
        if ($combined[$index] -ne 10) { continue }
        $length = $index - $lineStart
        if ($length -gt 0 -and $combined[$index - 1] -eq 13) { $length-- }
        $line = [System.Text.UTF8Encoding]::new($false, $true).GetString($combined, $lineStart, $length)
        $record = ConvertFrom-LocalLogLine -Line $line -StableOrder $StableOrder
        if ($null -ne $record) { $records += $record }
        $lineStart = $index + 1
    }
    if ($lineStart -lt $combined.Length) {
        $State.PendingBytes = $combined[$lineStart..($combined.Length - 1)]
    } else {
        $State.PendingBytes = [byte[]]@()
    }
    if ($State.PrefixLength -eq 0 -and $info.Length -gt 0) {
        $State.PrefixLength = [Math]::Min(64, [int]$info.Length)
        $prefixStream = [System.IO.File]::Open(
            $State.Path,
            [System.IO.FileMode]::Open,
            [System.IO.FileAccess]::Read,
            [System.IO.FileShare]::ReadWrite
        )
        try {
            $prefixBytes = [byte[]]::new($State.PrefixLength)
            [void]$prefixStream.Read($prefixBytes, 0, $State.PrefixLength)
            $State.PrefixFingerprint = [Convert]::ToBase64String($prefixBytes)
        } finally {
            $prefixStream.Dispose()
        }
    }
    return $records
}

function Read-LocalLogActivePoll {
    param(
        [Parameter(Mandatory = $true)][hashtable]$States,
        [Parameter(Mandatory = $true)][string]$CurrentDirectory,
        [string]$Level = '',
        [string]$Source = ''
    )

    $paths = @(Get-ChildItem -LiteralPath $CurrentDirectory -File -Filter '*.log' |
        Sort-Object Name |
        Select-Object -ExpandProperty FullName)
    $records = @()
    for ($index = 0; $index -lt $paths.Count; $index++) {
        $path = $paths[$index]
        if (-not $States.ContainsKey($path)) {
            $States[$path] = New-LocalLogWatchState -Path $path
        }
        $records += @(Read-LocalLogState -State $States[$path] -StableOrder $index)
    }
    return @($records |
        Where-Object { Test-LocalLogWatchFilter -Record $_ -Level $Level -Source $Source } |
        Sort-Object Timestamp, StableOrder |
        Select-Object -ExpandProperty Line)
}

function Read-LocalLogArchive {
    param(
        [Parameter(Mandatory = $true)][string]$ArchivePath,
        [string]$Level = '',
        [string]$Source = ''
    )

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($ArchivePath)
    try {
        $records = @()
        $order = 0
        foreach ($entry in @($zip.Entries | Where-Object FullName -like '*.log' | Sort-Object FullName)) {
            if ($entry.FullName -match '[/\\]\.\.|\.\.[/\\]') {
                throw 'LOCAL_LOG_ARCHIVE_ENTRY_UNSAFE'
            }
            $reader = [System.IO.StreamReader]::new(
                $entry.Open(),
                [System.Text.UTF8Encoding]::new($false, $true)
            )
            try {
                while (-not $reader.EndOfStream) {
                    $record = ConvertFrom-LocalLogLine -Line $reader.ReadLine() -StableOrder $order
                    if ($null -ne $record -and
                        (Test-LocalLogWatchFilter -Record $record -Level $Level -Source $Source)) {
                        $records += $record
                    }
                }
            } finally {
                $reader.Dispose()
            }
            $order++
        }
        return @($records |
            Sort-Object Timestamp, StableOrder |
            Select-Object -ExpandProperty Line)
    } finally {
        $zip.Dispose()
    }
}

function Write-LocalLogWatchLines {
    param(
        [string[]]$Lines,
        [switch]$NoColor
    )

    foreach ($line in $Lines) {
        if ($NoColor -or [Console]::IsOutputRedirected) {
            Write-Output $line
        } elseif ($line -match '\[ERROR\]') {
            Write-Host $line -ForegroundColor Red
        } elseif ($line -match '\[WARN\]') {
            Write-Host $line -ForegroundColor Yellow
        } else {
            Write-Host $line
        }
    }
}

function Invoke-LocalLogWatcher {
    param(
        [Parameter(Mandatory = $true)][string]$LogDirectory,
        [string]$Level = '',
        [string]$Source = '',
        [int]$Tail = 100,
        [switch]$NoColor,
        [string]$ArchiveDate = '',
        [switch]$Once,
        [int]$PollIntervalMilliseconds = 250
    )

    $resolved = [System.IO.Path]::GetFullPath($LogDirectory)
    if (-not [string]::IsNullOrWhiteSpace($ArchiveDate)) {
        $archivePath = Join-Path $resolved "archive\portfolio-agent-$ArchiveDate.zip"
        if (-not [System.IO.File]::Exists($archivePath)) {
            throw "LOCAL_LOG_ARCHIVE_NOT_FOUND date=$ArchiveDate"
        }
        $lines = @(Read-LocalLogArchive -ArchivePath $archivePath -Level $Level -Source $Source)
        if ($Tail -gt 0) { $lines = @($lines | Select-Object -Last $Tail) }
        Write-LocalLogWatchLines -Lines $lines -NoColor:$NoColor
        return
    }

    $currentDirectory = Join-Path $resolved 'current'
    if (-not [System.IO.Directory]::Exists($currentDirectory)) {
        throw "LOCAL_LOG_CURRENT_NOT_FOUND path=$currentDirectory"
    }
    $states = @{}
    $initial = $true
    do {
        $lines = @(Read-LocalLogActivePoll `
            -States $states `
            -CurrentDirectory $currentDirectory `
            -Level $Level `
            -Source $Source)
        if ($initial -and $Tail -gt 0) {
            $lines = @($lines | Select-Object -Last $Tail)
        }
        Write-LocalLogWatchLines -Lines $lines -NoColor:$NoColor
        $initial = $false
        if (-not $Once) { Start-Sleep -Milliseconds $PollIntervalMilliseconds }
    } while (-not $Once)
}

if ($MyInvocation.InvocationName -ne '.') {
    $repositoryRoot = Split-Path -Parent $PSScriptRoot
    if ([string]::IsNullOrWhiteSpace($LogDirectory)) {
        $LogDirectory = Join-Path $repositoryRoot 'logs'
    }
    Invoke-LocalLogWatcher `
        -LogDirectory $LogDirectory `
        -Level $Level `
        -Source $Source `
        -Tail $Tail `
        -NoColor:$NoColor `
        -ArchiveDate $ArchiveDate `
        -Once:$Once `
        -PollIntervalMilliseconds $PollIntervalMilliseconds
}
