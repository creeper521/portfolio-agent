Set-StrictMode -Version Latest

function Get-LocalLogLevel {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [string]$Stream,
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Line
    )

    foreach ($level in @('ERROR', 'WARN', 'DEBUG', 'INFO', 'TRACE')) {
        if ($Line -match "(?i)(?:^|[\s\[\]])$level(?:[\s\[\]:-]|$)") {
            if ($level -eq 'TRACE') {
                return 'DEBUG'
            }
            return $level
        }
    }

    if ($Stream.EndsWith('_STDERR')) {
        return 'ERROR'
    }
    return 'INFO'
}

function Protect-LocalLogText {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Line,
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,
        [Parameter(Mandatory = $true)]
        [string]$HomeDirectory
    )

    $credentialPattern = '(?ix)' +
        '(authorization\s*:\s*(?:bearer|basic)\b|' +
        '(?:api[_-]?key|access[_-]?token|secret|password)\s*[:=]|' +
        '-----BEGIN\s+(?:RSA\s+|EC\s+|OPENSSH\s+)?PRIVATE\s+KEY-----)'
    if ($Line -match $credentialPattern) {
        return [pscustomobject]@{
            Text = 'OUTPUT_REDACTED reason=CREDENTIAL_PATTERN'
            Redacted = $true
        }
    }

    $text = $Line
    $redacted = $false

    $withoutAnsi = [regex]::Replace($text, "$([char]27)\[[0-?]*[ -/]*[@-~]", '')
    if ($withoutAnsi -ne $text) {
        $text = $withoutAnsi
        $redacted = $true
    }

    $withoutControls = [regex]::Replace($text, '[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]', '')
    if ($withoutControls -ne $text) {
        $text = $withoutControls
        $redacted = $true
    }

    foreach ($path in @($RepositoryRoot, $HomeDirectory)) {
        if (-not [string]::IsNullOrWhiteSpace($path)) {
            $replacement = if ($path -eq $RepositoryRoot) { '<REPOSITORY>' } else { '<HOME>' }
            $replaced = [regex]::Replace(
                $text,
                [regex]::Escape($path.TrimEnd('\', '/')),
                $replacement,
                [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
            )
            if ($replaced -ne $text) {
                $text = $replaced
                $redacted = $true
            }
        }
    }

    $withoutUrlDetails = [regex]::Replace(
        $text,
        '(?i)(https?://[^\s?#]+)[?#][^\s]*',
        '$1'
    )
    if ($withoutUrlDetails -ne $text) {
        $text = $withoutUrlDetails
        $redacted = $true
    }

    $maximumLength = 8192
    $marker = '...[TRUNCATED]'
    if ($text.Length -gt $maximumLength) {
        $text = $text.Substring(0, $maximumLength - $marker.Length) + $marker
        $redacted = $true
    }

    return [pscustomobject]@{
        Text = $text
        Redacted = $redacted
    }
}

function ConvertTo-LocalLogRecord {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet(
            'BACKEND_STDOUT',
            'BACKEND_STDERR',
            'VITE_STDOUT',
            'VITE_STDERR',
            'LAUNCHER'
        )]
        [string]$Stream,
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Line,
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,
        [Parameter(Mandatory = $true)]
        [string]$HomeDirectory,
        [Parameter(Mandatory = $true)]
        [DateTimeOffset]$Now
    )

    $domain = if ($Stream.StartsWith('VITE_')) { 'FRONTEND' } else { 'BACKEND' }
    $source = if ($Stream.StartsWith('VITE_')) {
        'VITE'
    } elseif ($Stream -eq 'LAUNCHER') {
        'LAUNCHER'
    } else {
        'SPRING'
    }
    if ($Line -match '(?:^|\s)event\.origin=browser(?:\s|$)') {
        $domain = 'FRONTEND'
        $source = 'BROWSER'
    }

    $level = Get-LocalLogLevel -Stream $Stream -Line $Line
    $sanitized = Protect-LocalLogText `
        -Line $Line `
        -RepositoryRoot $RepositoryRoot `
        -HomeDirectory $HomeDirectory

    return [pscustomobject]@{
        Timestamp = $Now
        Domain = $domain
        Level = $level
        Source = $source
        Text = $sanitized.Text
        Redacted = $sanitized.Redacted
    }
}

function Format-LocalLogRecord {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Record
    )

    return '{0} [{1}][{2}][{3}] {4}' -f `
        $Record.Timestamp.ToString('yyyy-MM-ddTHH:mm:ss.fffzzz'), `
        $Record.Domain, `
        $Record.Level, `
        $Record.Source, `
        $Record.Text
}

function Get-LocalLogBaseName {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Record
    )

    if ($Record.Source -eq 'LAUNCHER') {
        return 'launcher'
    }
    if ($Record.Level -eq 'ERROR') {
        return 'frontend-error'
    }
    return 'frontend-info'
}

function Assert-SafeLocalLogDirectory {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [string]$LogDirectory
    )

    $resolved = [System.IO.Path]::GetFullPath($LogDirectory).TrimEnd('\', '/')
    $root = [System.IO.Path]::GetPathRoot($resolved).TrimEnd('\', '/')
    if ([string]::IsNullOrWhiteSpace($resolved) -or $resolved -eq $root) {
        throw 'LOCAL_LOG_DIRECTORY_UNSAFE'
    }
    return $resolved
}

function New-LocalLogRouter {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,
        [Parameter(Mandatory = $true)]
        [string]$LogDirectory,
        [scriptblock]$Clock = { [DateTimeOffset]::Now },
        [ValidateRange(256, [long]::MaxValue)]
        [long]$MaxFileBytes = 20MB,
        [ValidateRange(2, 100)]
        [int]$MaxSegments = 5,
        [ValidateRange(1, 1000000)]
        [int]$QueueCapacity = 4096
    )

    $resolvedRepositoryRoot = [System.IO.Path]::GetFullPath($RepositoryRoot).TrimEnd('\', '/')
    $resolvedLogDirectory = Assert-SafeLocalLogDirectory -LogDirectory $LogDirectory
    $currentDirectory = Join-Path $resolvedLogDirectory 'current'
    [System.IO.Directory]::CreateDirectory($currentDirectory) | Out-Null
    $initialNow = & $Clock
    $activeDatePath = Join-Path $currentDirectory '.active-date'
    $activeDate = if ([System.IO.File]::Exists($activeDatePath)) {
        [System.IO.File]::ReadAllText($activeDatePath).Trim()
    } else {
        $initialNow.ToString('yyyy-MM-dd')
    }
    if ($activeDate -notmatch '^\d{4}-\d{2}-\d{2}$') {
        throw 'LOCAL_LOG_ACTIVE_DATE_INVALID'
    }
    [System.IO.File]::WriteAllText(
        $activeDatePath,
        $activeDate,
        [System.Text.UTF8Encoding]::new($false)
    )
    foreach ($name in @('frontend-info', 'frontend-error', 'launcher')) {
        $path = Join-Path $currentDirectory "$name.log"
        if (-not [System.IO.File]::Exists($path)) {
            [System.IO.File]::WriteAllText($path, '', [System.Text.UTF8Encoding]::new($false))
        }
    }

    $router = [pscustomobject]@{
        RepositoryRoot = $resolvedRepositoryRoot
        LogDirectory = $resolvedLogDirectory
        CurrentDirectory = $currentDirectory
        Queue = [System.Collections.Concurrent.ConcurrentQueue[object]]::new()
        QueueSignal = [System.Threading.AutoResetEvent]::new($false)
        QueueCapacity = $QueueCapacity
        MaxFileBytes = $MaxFileBytes
        MaxSegments = $MaxSegments
        Clock = $Clock
        SyncRoot = [object]::new()
        MaintenanceRoot = [object]::new()
        ActiveDate = $activeDate
        StopRequested = $false
        IsWriting = $false
        StatusCode = 'STARTING'
        DroppedDebug = 0L
        DroppedInfo = 0L
        DroppedWarn = 0L
        DroppedError = 0L
        Truncated = $false
        DiscardedSegmentCount = 0L
        WriterPowerShell = $null
        WriterAsyncResult = $null
    }

    $writerScript = {
        param($State)

        $encoding = [System.Text.UTF8Encoding]::new($false)

        function Invoke-RotateFile {
            param($State, [string]$Path)

            $directory = [System.IO.Path]::GetDirectoryName($Path)
            $baseName = [System.IO.Path]::GetFileNameWithoutExtension($Path)
            function Get-SegmentPath {
                param([int]$Index)
                return Join-Path $directory "$baseName.$Index.log"
            }

            $lastIndex = $State.MaxSegments - 1
            $oldest = Get-SegmentPath -Index $lastIndex
            if ([System.IO.File]::Exists($oldest)) {
                [System.IO.File]::Delete($oldest)
                $State.Truncated = $true
                $State.DiscardedSegmentCount++
            }
            for ($index = $lastIndex - 1; $index -ge 1; $index--) {
                $source = Get-SegmentPath -Index $index
                if ([System.IO.File]::Exists($source)) {
                    [System.IO.File]::Move($source, (Get-SegmentPath -Index ($index + 1)))
                }
            }
            if ([System.IO.File]::Exists($Path)) {
                [System.IO.File]::Move($Path, (Get-SegmentPath -Index 1))
            }
            [System.IO.File]::WriteAllText($Path, '', $encoding)
        }

        function Write-QueuedRecord {
            param($State, $Item)

            $path = Join-Path $State.CurrentDirectory "$($Item.BaseName).log"
            $bytes = $encoding.GetBytes($Item.Formatted + [Environment]::NewLine)
            $length = if ([System.IO.File]::Exists($path)) {
                ([System.IO.FileInfo]::new($path)).Length
            } else {
                0L
            }
            if ($length -gt 0 -and ($length + $bytes.Length) -gt $State.MaxFileBytes) {
                Invoke-RotateFile -State $State -Path $path
            }

            $stream = [System.IO.FileStream]::new(
                $path,
                [System.IO.FileMode]::Append,
                [System.IO.FileAccess]::Write,
                [System.IO.FileShare]::ReadWrite
            )
            try {
                $stream.Write($bytes, 0, $bytes.Length)
                $stream.Flush()
            } finally {
                $stream.Dispose()
            }
        }

        try {
            $State.StatusCode = 'READY'
            while (-not $State.StopRequested -or -not $State.Queue.IsEmpty) {
                $item = $null
                [System.Threading.Monitor]::Enter($State.SyncRoot)
                try {
                    $found = $State.Queue.TryDequeue([ref]$item)
                } finally {
                    [System.Threading.Monitor]::Exit($State.SyncRoot)
                }

                if ($found) {
                    $State.IsWriting = $true
                    try {
                        Write-QueuedRecord -State $State -Item $item
                    } finally {
                        $State.IsWriting = $false
                    }
                } else {
                    [void]$State.QueueSignal.WaitOne(100)
                }
            }
            $State.StatusCode = 'STOPPED'
        } catch {
            $State.IsWriting = $false
            $State.StatusCode = 'WRITER_FAILED'
        }
    }

    $writer = [powershell]::Create()
    [void]$writer.AddScript($writerScript).AddArgument($router)
    $router.WriterPowerShell = $writer
    $router.WriterAsyncResult = $writer.BeginInvoke()

    $deadline = [DateTime]::UtcNow.AddSeconds(5)
    while ($router.StatusCode -eq 'STARTING' -and [DateTime]::UtcNow -lt $deadline) {
        Start-Sleep -Milliseconds 10
    }
    if ($router.StatusCode -ne 'READY') {
        throw "LOCAL_LOG_ROUTER_START_FAILED status=$($router.StatusCode)"
    }
    return $router
}

function Add-DroppedLocalLogRecord {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Router,
        [Parameter(Mandatory = $true)]
        [string]$Level
    )

    switch ($Level) {
        'DEBUG' { $Router.DroppedDebug++ }
        'INFO' { $Router.DroppedInfo++ }
        'WARN' { $Router.DroppedWarn++ }
        'ERROR' { $Router.DroppedError++ }
    }
}

function Submit-LocalLogLine {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Router,
        [Parameter(Mandatory = $true)]
        [ValidateSet(
            'BACKEND_STDOUT',
            'BACKEND_STDERR',
            'VITE_STDOUT',
            'VITE_STDERR',
            'LAUNCHER'
        )]
        [string]$Stream,
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Line
    )

    if ($Stream -in @('BACKEND_STDOUT', 'BACKEND_STDERR') -and
        $Line -notmatch '(?:^|\s)event\.origin=browser(?:\s|$)') {
        return
    }

    [System.Threading.Monitor]::Enter($Router.MaintenanceRoot)
    try {
        if ($Router.StopRequested -or $Router.StatusCode -notin @('READY', 'STARTING')) {
            return
        }

        $now = & $Router.Clock
        $currentDate = $now.ToString('yyyy-MM-dd')
        if ($currentDate -gt $Router.ActiveDate) {
            Invoke-LocalLogDateRollover -Router $Router -NewDate $currentDate
        }

        $record = ConvertTo-LocalLogRecord `
            -Stream $Stream `
            -Line $Line `
            -RepositoryRoot $Router.RepositoryRoot `
            -HomeDirectory ([Environment]::GetFolderPath('UserProfile')) `
            -Now $now
        $item = [pscustomobject]@{
            Level = $record.Level
            BaseName = Get-LocalLogBaseName -Record $record
            Formatted = Format-LocalLogRecord -Record $record
        }

        $accepted = $false
        [System.Threading.Monitor]::Enter($Router.SyncRoot)
        try {
            if ($Router.Queue.Count -lt $Router.QueueCapacity) {
                $Router.Queue.Enqueue($item)
                $accepted = $true
            } elseif ($record.Level -in @('DEBUG', 'INFO')) {
                Add-DroppedLocalLogRecord -Router $Router -Level $record.Level
            } else {
                $preserved = [System.Collections.Generic.List[object]]::new()
                $candidate = $null
                $removedLowerPriority = $false
                while ($Router.Queue.TryDequeue([ref]$candidate)) {
                    if (-not $removedLowerPriority -and $candidate.Level -in @('DEBUG', 'INFO')) {
                        Add-DroppedLocalLogRecord -Router $Router -Level $candidate.Level
                        $removedLowerPriority = $true
                    } else {
                        $preserved.Add($candidate)
                    }
                    $candidate = $null
                }
                foreach ($existing in $preserved) {
                    $Router.Queue.Enqueue($existing)
                }
                if ($removedLowerPriority) {
                    $Router.Queue.Enqueue($item)
                    $accepted = $true
                } else {
                    Add-DroppedLocalLogRecord -Router $Router -Level $record.Level
                }
            }
        } finally {
            [System.Threading.Monitor]::Exit($Router.SyncRoot)
        }

        if ($accepted) {
            [void]$Router.QueueSignal.Set()
        }
    } finally {
        [System.Threading.Monitor]::Exit($Router.MaintenanceRoot)
    }
}

function Flush-LocalLogRouter {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Router,
        [ValidateRange(1, 60000)]
        [int]$TimeoutMilliseconds = 10000
    )

    $deadline = [DateTime]::UtcNow.AddMilliseconds($TimeoutMilliseconds)
    [void]$Router.QueueSignal.Set()
    while ((-not $Router.Queue.IsEmpty -or $Router.IsWriting) -and [DateTime]::UtcNow -lt $deadline) {
        if ($Router.StatusCode -eq 'WRITER_FAILED') {
            throw 'LOCAL_LOG_ROUTER_WRITER_FAILED'
        }
        Start-Sleep -Milliseconds 10
    }
    if (-not $Router.Queue.IsEmpty -or $Router.IsWriting) {
        throw 'LOCAL_LOG_ROUTER_FLUSH_TIMEOUT'
    }
}

function Stop-LocalLogRouter {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Router,
        [ValidateRange(1, 60000)]
        [int]$TimeoutMilliseconds = 10000
    )

    if ($Router.StatusCode -eq 'STOPPED') {
        return
    }
    Flush-LocalLogRouter -Router $Router -TimeoutMilliseconds $TimeoutMilliseconds
    $Router.StopRequested = $true
    [void]$Router.QueueSignal.Set()
    if (-not $Router.WriterAsyncResult.AsyncWaitHandle.WaitOne($TimeoutMilliseconds)) {
        throw 'LOCAL_LOG_ROUTER_STOP_TIMEOUT'
    }
    try {
        [void]$Router.WriterPowerShell.EndInvoke($Router.WriterAsyncResult)
    } finally {
        $Router.WriterPowerShell.Dispose()
        $Router.QueueSignal.Dispose()
    }
    if ($Router.StatusCode -ne 'STOPPED') {
        throw "LOCAL_LOG_ROUTER_STOP_FAILED status=$($Router.StatusCode)"
    }
}

function Get-LocalLogSha256 {
    param([Parameter(Mandatory = $true)][string]$Path)

    $stream = [System.IO.File]::OpenRead($Path)
    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-', '').ToLowerInvariant()
    } finally {
        $algorithm.Dispose()
        $stream.Dispose()
    }
}

function Assert-LocalLogChildPath {
    param(
        [Parameter(Mandatory = $true)][string]$LogDirectory,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $root = [System.IO.Path]::GetFullPath($LogDirectory).TrimEnd('\', '/')
    $resolved = [System.IO.Path]::GetFullPath($Path)
    if (-not $resolved.StartsWith($root + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'LOCAL_LOG_PATH_ESCAPE'
    }
    $cursor = $resolved
    while (-not [string]::IsNullOrWhiteSpace($cursor) -and $cursor.Length -ge $root.Length) {
        if ([System.IO.Directory]::Exists($cursor)) {
            $attributes = [System.IO.File]::GetAttributes($cursor)
            if (($attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw 'LOCAL_LOG_REPARSE_POINT_REJECTED'
            }
        }
        if ($cursor -eq $root) { break }
        $cursor = [System.IO.Path]::GetDirectoryName($cursor)
    }
    return $resolved
}

function New-LocalLogManifest {
    param(
        [Parameter(Mandatory = $true)][string]$SourceDirectory,
        [Parameter(Mandatory = $true)][string]$ArchiveDate,
        [Parameter(Mandatory = $true)][DateTimeOffset]$CreatedAt,
        [pscustomobject]$Router
    )

    $files = @()
    foreach ($file in @(Get-ChildItem -LiteralPath $SourceDirectory -File -Filter '*.log' | Sort-Object Name)) {
        $lineCount = 0L
        foreach ($unused in [System.IO.File]::ReadLines($file.FullName)) { $lineCount++ }
        $files += [ordered]@{
            name = $file.Name
            bytes = $file.Length
            sha256 = Get-LocalLogSha256 -Path $file.FullName
            lineCount = $lineCount
        }
    }
    return [ordered]@{
        schemaVersion = 1
        archiveDate = $ArchiveDate
        timezone = $CreatedAt.ToString('zzz')
        createdAt = $CreatedAt.ToString('o')
        files = $files
        truncated = if ($null -eq $Router) { $false } else { $Router.Truncated }
        discardedSegmentCount = if ($null -eq $Router) { 0 } else { $Router.DiscardedSegmentCount }
        dropped = [ordered]@{
            debug = if ($null -eq $Router) { 0 } else { $Router.DroppedDebug }
            info = if ($null -eq $Router) { 0 } else { $Router.DroppedInfo }
            warn = if ($null -eq $Router) { 0 } else { $Router.DroppedWarn }
            error = if ($null -eq $Router) { 0 } else { $Router.DroppedError }
        }
    }
}

function Test-VerifiedLocalLogArchive {
    param(
        [Parameter(Mandatory = $true)][string]$ArchivePath,
        [Parameter(Mandatory = $true)][string]$SourceDirectory
    )

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    try {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($ArchivePath)
        try {
            $manifestEntry = $zip.GetEntry('manifest.json')
            if ($null -eq $manifestEntry) { return $false }
            $reader = [System.IO.StreamReader]::new($manifestEntry.Open(), [System.Text.Encoding]::UTF8)
            try {
                $manifest = $reader.ReadToEnd() | ConvertFrom-Json
            } finally {
                $reader.Dispose()
            }
            foreach ($file in @($manifest.files)) {
                if ($file.name -match '[/\\]' -or $file.name -eq '..') { return $false }
                $entry = $zip.GetEntry([string]$file.name)
                if ($null -eq $entry -or $entry.Length -ne [long]$file.bytes) { return $false }
                $algorithm = [System.Security.Cryptography.SHA256]::Create()
                $stream = $entry.Open()
                try {
                    $hash = ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-', '').ToLowerInvariant()
                } finally {
                    $stream.Dispose()
                    $algorithm.Dispose()
                }
                if ($hash -ne [string]$file.sha256) { return $false }
                $sourcePath = Join-Path $SourceDirectory ([string]$file.name)
                if ([System.IO.File]::Exists($sourcePath) -and
                    (Get-LocalLogSha256 -Path $sourcePath) -ne [string]$file.sha256) {
                    return $false
                }
            }
            return $true
        } finally {
            $zip.Dispose()
        }
    } catch {
        return $false
    }
}

function New-VerifiedLocalLogArchive {
    param(
        [Parameter(Mandatory = $true)][string]$LogDirectory,
        [Parameter(Mandatory = $true)][string]$SourceDirectory,
        [Parameter(Mandatory = $true)][string]$FinalPath,
        [Parameter(Mandatory = $true)][string]$ArchiveDate,
        [Parameter(Mandatory = $true)][DateTimeOffset]$CreatedAt,
        [pscustomobject]$Router
    )

    $safeSource = Assert-LocalLogChildPath -LogDirectory $LogDirectory -Path $SourceDirectory
    $safeFinal = Assert-LocalLogChildPath -LogDirectory $LogDirectory -Path $FinalPath
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($safeFinal)) | Out-Null
    $manifest = New-LocalLogManifest `
        -SourceDirectory $safeSource `
        -ArchiveDate $ArchiveDate `
        -CreatedAt $CreatedAt `
        -Router $Router
    $manifestPath = Join-Path $safeSource 'manifest.json'
    [System.IO.File]::WriteAllText(
        $manifestPath,
        ($manifest | ConvertTo-Json -Depth 8),
        [System.Text.UTF8Encoding]::new($false)
    )

    if ([System.IO.File]::Exists($safeFinal)) {
        if (Test-VerifiedLocalLogArchive -ArchivePath $safeFinal -SourceDirectory $safeSource) {
            Remove-Item -LiteralPath $safeSource -Recurse -Force
            return $safeFinal
        }
        throw 'LOG_ARCHIVE_CONFLICT'
    }

    $temporaryPath = "$safeFinal.tmp"
    if ([System.IO.File]::Exists($temporaryPath)) {
        [System.IO.File]::Delete($temporaryPath)
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::CreateFromDirectory(
        $safeSource,
        $temporaryPath,
        [System.IO.Compression.CompressionLevel]::Optimal,
        $false
    )
    if (-not (Test-VerifiedLocalLogArchive -ArchivePath $temporaryPath -SourceDirectory $safeSource)) {
        throw 'LOG_ARCHIVE_VERIFICATION_FAILED'
    }
    [System.IO.File]::Move($temporaryPath, $safeFinal)
    Remove-Item -LiteralPath $safeSource -Recurse -Force
    return $safeFinal
}

function Invoke-LocalLogDateRollover {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][pscustomobject]$Router,
        [Parameter(Mandatory = $true)]
        [ValidatePattern('^\d{4}-\d{2}-\d{2}$')]
        [string]$NewDate
    )

    [System.Threading.Monitor]::Enter($Router.MaintenanceRoot)
    try {
        if ($NewDate -le $Router.ActiveDate) { return }
        Flush-LocalLogRouter -Router $Router
        $oldDate = $Router.ActiveDate
        $logFiles = @(Get-ChildItem -LiteralPath $Router.CurrentDirectory -File -Filter '*.log' |
            Where-Object {
                $_.Name -match '^(frontend-info|frontend-error|launcher)(?:\.\d+)?\.log$'
            })
        $hasContent = @($logFiles | Where-Object Length -gt 0).Count -gt 0
        if ($hasContent) {
            $stagingDirectory = Join-Path $Router.LogDirectory "staging\$oldDate"
            $safeStaging = Assert-LocalLogChildPath -LogDirectory $Router.LogDirectory -Path $stagingDirectory
            if ([System.IO.Directory]::Exists($safeStaging)) {
                throw 'LOG_ARCHIVE_STAGING_CONFLICT'
            }
            [System.IO.Directory]::CreateDirectory($safeStaging) | Out-Null
            foreach ($file in $logFiles) {
                if ($file.Name -notmatch '^(frontend-info|frontend-error|launcher)(?:\.(\d+))?\.log$') {
                    continue
                }
                $segment = if ([string]::IsNullOrWhiteSpace($Matches[2])) { '' } else { ".$($Matches[2])" }
                $destination = Join-Path $safeStaging "$($Matches[1])-$oldDate$segment.log"
                [System.IO.File]::Move($file.FullName, $destination)
            }
        }

        foreach ($name in @('frontend-info', 'frontend-error', 'launcher')) {
            [System.IO.File]::WriteAllText(
                (Join-Path $Router.CurrentDirectory "$name.log"),
                '',
                [System.Text.UTF8Encoding]::new($false)
            )
        }
        $Router.ActiveDate = $NewDate
        [System.IO.File]::WriteAllText(
            (Join-Path $Router.CurrentDirectory '.active-date'),
            $NewDate,
            [System.Text.UTF8Encoding]::new($false)
        )

        if ($hasContent) {
            $finalPath = Join-Path $Router.LogDirectory "archive\portfolio-agent-$oldDate.zip"
            [void](New-VerifiedLocalLogArchive `
                -LogDirectory $Router.LogDirectory `
                -SourceDirectory $safeStaging `
                -FinalPath $finalPath `
                -ArchiveDate $oldDate `
                -CreatedAt (& $Router.Clock) `
                -Router $Router)
        }
    } finally {
        [System.Threading.Monitor]::Exit($Router.MaintenanceRoot)
    }
}

function Invoke-LocalLogRetention {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$LogDirectory,
        [Parameter(Mandatory = $true)]
        [ValidatePattern('^\d{4}-\d{2}-\d{2}$')]
        [string]$Today,
        [ValidateRange(1, 365)][int]$RetentionDays = 7,
        [ValidateRange(1, [long]::MaxValue)][long]$TotalArchiveBytes = 2GB
    )

    $todayDate = [datetime]::ParseExact($Today, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture)
    $oldestKept = $todayDate.AddDays(-$RetentionDays)
    $archiveDirectory = Join-Path $LogDirectory 'archive'
    $snapshotDirectory = Join-Path $LogDirectory 'snapshots'
    $daily = @()
    if ([System.IO.Directory]::Exists($archiveDirectory)) {
        $daily = @(Get-ChildItem -LiteralPath $archiveDirectory -File | Where-Object {
            $_.Name -match '^portfolio-agent-(\d{4}-\d{2}-\d{2})\.zip$'
        } | ForEach-Object {
            [pscustomobject]@{
                File = $_
                Date = [datetime]::ParseExact(
                    ([regex]::Match($_.Name, '\d{4}-\d{2}-\d{2}').Value),
                    'yyyy-MM-dd',
                    [Globalization.CultureInfo]::InvariantCulture
                )
            }
        })
    }
    foreach ($item in @($daily | Where-Object Date -lt $oldestKept)) {
        $safe = Assert-LocalLogChildPath -LogDirectory $LogDirectory -Path $item.File.FullName
        [System.IO.File]::Delete($safe)
    }

    $managed = @()
    if ([System.IO.Directory]::Exists($archiveDirectory)) {
        $managed += @(Get-ChildItem -LiteralPath $archiveDirectory -File | Where-Object {
            $_.Name -match '^portfolio-agent-\d{4}-\d{2}-\d{2}\.zip$'
        })
    }
    if ([System.IO.Directory]::Exists($snapshotDirectory)) {
        $managed += @(Get-ChildItem -LiteralPath $snapshotDirectory -File | Where-Object {
            $_.Name -match '^portfolio-agent-\d{4}-\d{2}-\d{2}-\d{6}\.zip$'
        })
    }
    $total = if ($managed.Count -eq 0) {
        0L
    } else {
        [long](($managed | Measure-Object -Property Length -Sum).Sum)
    }
    $ordered = @($managed | Sort-Object @{
        Expression = { if ($_.DirectoryName -eq $archiveDirectory) { 0 } else { 1 } }
    }, LastWriteTimeUtc, Name)
    foreach ($file in $ordered) {
        if ($total -le $TotalArchiveBytes) { break }
        $safe = Assert-LocalLogChildPath -LogDirectory $LogDirectory -Path $file.FullName
        $length = $file.Length
        [System.IO.File]::Delete($safe)
        $total -= $length
    }
}

function Invoke-LocalLogMaintenance {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][pscustomobject]$Router,
        [ValidateRange(1, 365)][int]$RetentionDays = 7,
        [ValidateRange(1, [long]::MaxValue)][long]$TotalArchiveBytes = 2GB
    )

    $now = & $Router.Clock
    $today = $now.ToString('yyyy-MM-dd')
    if ($Router.ActiveDate -lt $today) {
        Invoke-LocalLogDateRollover -Router $Router -NewDate $today
    }
    $stagingRoot = Join-Path $Router.LogDirectory 'staging'
    if ([System.IO.Directory]::Exists($stagingRoot)) {
        foreach ($directory in @(Get-ChildItem -LiteralPath $stagingRoot -Directory | Where-Object {
            $_.Name -match '^\d{4}-\d{2}-\d{2}$'
        })) {
            $finalPath = Join-Path $Router.LogDirectory "archive\portfolio-agent-$($directory.Name).zip"
            [void](New-VerifiedLocalLogArchive `
                -LogDirectory $Router.LogDirectory `
                -SourceDirectory $directory.FullName `
                -FinalPath $finalPath `
                -ArchiveDate $directory.Name `
                -CreatedAt $now `
                -Router $Router)
        }
    }
    Invoke-LocalLogRetention `
        -LogDirectory $Router.LogDirectory `
        -Today $today `
        -RetentionDays $RetentionDays `
        -TotalArchiveBytes $TotalArchiveBytes
}

function New-LocalLogSnapshot {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string]$LogDirectory,
        [Parameter(Mandatory = $true)][DateTimeOffset]$Now
    )

    $resolvedLogDirectory = Assert-SafeLocalLogDirectory -LogDirectory $LogDirectory
    $currentDirectory = Join-Path $resolvedLogDirectory 'current'
    $snapshotName = "portfolio-agent-$($Now.ToString('yyyy-MM-dd-HHmmss')).zip"
    $sourceDirectory = Join-Path $resolvedLogDirectory "staging\snapshot-$([guid]::NewGuid().ToString('N'))"
    [System.IO.Directory]::CreateDirectory($sourceDirectory) | Out-Null
    try {
        foreach ($file in @(Get-ChildItem -LiteralPath $currentDirectory -File -Filter '*.log' |
                Where-Object {
                    $_.Name -match '^(frontend-info|frontend-error|launcher)(?:\.\d+)?\.log$'
                })) {
            [System.IO.File]::Copy($file.FullName, (Join-Path $sourceDirectory $file.Name))
        }
        $finalPath = Join-Path $resolvedLogDirectory "snapshots\$snapshotName"
        return New-VerifiedLocalLogArchive `
            -LogDirectory $resolvedLogDirectory `
            -SourceDirectory $sourceDirectory `
            -FinalPath $finalPath `
            -ArchiveDate $Now.ToString('yyyy-MM-dd') `
            -CreatedAt $Now `
            -Router $null
    } catch {
        if ([System.IO.Directory]::Exists($sourceDirectory)) {
            Remove-Item -LiteralPath $sourceDirectory -Recurse -Force
        }
        throw
    }
}

Export-ModuleMember -Function `
    ConvertTo-LocalLogRecord, `
    Format-LocalLogRecord, `
    New-LocalLogRouter, `
    Submit-LocalLogLine, `
    Flush-LocalLogRouter, `
    Stop-LocalLogRouter, `
    Invoke-LocalLogMaintenance, `
    Invoke-LocalLogDateRollover, `
    Invoke-LocalLogRetention, `
    New-LocalLogSnapshot
