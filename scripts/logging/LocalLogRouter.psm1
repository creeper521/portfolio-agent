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

    if ($Record.Domain -eq 'BACKEND' -and $Record.Level -eq 'ERROR') {
        return 'backend-error'
    }
    if ($Record.Domain -eq 'BACKEND') {
        return 'backend-info'
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
    foreach ($name in @('backend-info', 'backend-error', 'frontend-info', 'frontend-error')) {
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
        StopRequested = $false
        IsWriting = $false
        StatusCode = 'STARTING'
        DroppedDebug = 0L
        DroppedInfo = 0L
        DroppedWarn = 0L
        DroppedError = 0L
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

    if ($Router.StopRequested -or $Router.StatusCode -notin @('READY', 'STARTING')) {
        return
    }

    $record = ConvertTo-LocalLogRecord `
        -Stream $Stream `
        -Line $Line `
        -RepositoryRoot $Router.RepositoryRoot `
        -HomeDirectory ([Environment]::GetFolderPath('UserProfile')) `
        -Now (& $Router.Clock)
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

Export-ModuleMember -Function `
    ConvertTo-LocalLogRecord, `
    Format-LocalLogRecord, `
    New-LocalLogRouter, `
    Submit-LocalLogLine, `
    Flush-LocalLogRouter, `
    Stop-LocalLogRouter
