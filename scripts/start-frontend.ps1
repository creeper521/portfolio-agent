param(
    [string]$LogDirectory = '',
    [string]$ViteCommand = '',
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ViteArguments
)

$ErrorActionPreference = 'Stop'
$script:repositoryRoot = Split-Path -Parent $PSScriptRoot
$script:processReaders = @{}

function Stop-WithCode([string]$Code) {
    throw $Code
}

function Resolve-CommandPath([string]$Command, [string]$FailureCode) {
    $resolved = Get-Command $Command -ErrorAction SilentlyContinue
    if ($null -eq $resolved) {
        Stop-WithCode $FailureCode
    }
    return $resolved.Source
}

function Test-RepositoryMarkers {
    foreach ($marker in @('.git', 'backend\pom.xml', 'frontend\package.json')) {
        if (-not (Test-Path -LiteralPath (Join-Path $script:repositoryRoot $marker))) {
            return $false
        }
    }
    return $true
}

function Start-OwnedProcess(
    [string]$Executable,
    [string[]]$Arguments,
    [string]$WorkingDirectory,
    [pscustomobject]$LogRouter
) {
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $Executable
    $startInfo.Arguments = (($Arguments | ForEach-Object {
        '"' + ([string]$_).Replace('"', '\"') + '"'
    }) -join ' ')
    $startInfo.WorkingDirectory = $WorkingDirectory
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        Stop-WithCode 'LOCAL_CHILD_START_FAILED'
    }

    $readerScript = {
        param($Reader, $Router, $Stream, $ModulePath, $RepositoryRoot, $HomeDirectory)
        Import-Module $ModulePath -Force -DisableNameChecking
        while ($null -ne ($line = $Reader.ReadLine())) {
            $record = ConvertTo-LocalLogRecord `
                -Stream $Stream `
                -Line $line `
                -RepositoryRoot $RepositoryRoot `
                -HomeDirectory $HomeDirectory `
                -Now ([DateTimeOffset]::Now)
            if ($Stream.EndsWith('_STDERR')) {
                [Console]::Error.WriteLine($record.Text)
            }
            else {
                [Console]::Out.WriteLine($record.Text)
            }
            if ($null -ne $Router) {
                Submit-LocalLogLine -Router $Router -Stream $Stream -Line $line
            }
        }
    }
    $modulePath = Join-Path $PSScriptRoot 'logging\LocalLogRouter.psm1'
    $homeDirectory = [Environment]::GetFolderPath('UserProfile')
    $readers = @()
    foreach ($readerDefinition in @(
        @{ Reader = $process.StandardOutput; Stream = 'VITE_STDOUT' },
        @{ Reader = $process.StandardError; Stream = 'VITE_STDERR' }
    )) {
        $readerPowerShell = [powershell]::Create()
        [void]$readerPowerShell.AddScript($readerScript).
            AddArgument($readerDefinition.Reader).
            AddArgument($LogRouter).
            AddArgument($readerDefinition.Stream).
            AddArgument($modulePath).
            AddArgument($script:repositoryRoot).
            AddArgument($homeDirectory)
        $readers += [pscustomobject]@{
            PowerShell = $readerPowerShell
            AsyncResult = $readerPowerShell.BeginInvoke()
        }
    }
    $script:processReaders[$process.Id] = $readers
    return $process
}

function Stop-OwnedProcess([System.Diagnostics.Process]$Process) {
    if ($null -eq $Process) {
        return
    }
    $Process.Refresh()
    if (-not $Process.HasExited) {
        $taskkill = Get-Command 'taskkill.exe' -ErrorAction SilentlyContinue
        if ($null -ne $taskkill) {
            $previousErrorActionPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                & $taskkill.Source /PID $Process.Id /T /F 2>&1 | Out-Null
            }
            finally {
                $ErrorActionPreference = $previousErrorActionPreference
            }
        }
        $Process.Refresh()
        if (-not $Process.HasExited) {
            Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
        }
    }
    [void]$Process.WaitForExit(5000)
    $Process.Refresh()
    if (-not $Process.HasExited) {
        Stop-WithCode "LOCAL_CHILD_CLEANUP_FAILED:$($Process.Id)"
    }
    if ($script:processReaders.ContainsKey($Process.Id)) {
        foreach ($reader in @($script:processReaders[$Process.Id])) {
            if (-not $reader.AsyncResult.AsyncWaitHandle.WaitOne(5000)) {
                $reader.PowerShell.Stop()
            }
            try {
                [void]$reader.PowerShell.EndInvoke($reader.AsyncResult)
            }
            finally {
                $reader.PowerShell.Dispose()
            }
        }
        $script:processReaders.Remove($Process.Id)
    }
}

try {
    $testMode = [string]::Equals(
        [Environment]::GetEnvironmentVariable(
            'PORTFOLIO_START_FRONTEND_TEST_MODE',
            [EnvironmentVariableTarget]::Process
        ),
        'true',
        [System.StringComparison]::OrdinalIgnoreCase
    )
    if ($ViteCommand -ne '' -and -not $testMode) {
        Stop-WithCode 'LOCAL_TEST_SEAM_FORBIDDEN'
    }
    $delegated = [string]::Equals(
        [Environment]::GetEnvironmentVariable(
            'PORTFOLIO_FRONTEND_LOG_OWNER',
            [EnvironmentVariableTarget]::Process
        ),
        'UNIFIED',
        [System.StringComparison]::OrdinalIgnoreCase
    )

    if ([string]::IsNullOrWhiteSpace($LogDirectory)) {
        $LogDirectory = Join-Path $script:repositoryRoot 'logs'
    }
    $logDirectory = [System.IO.Path]::GetFullPath($LogDirectory)

    $router = $null
    if (-not $delegated) {
        if (-not (Test-RepositoryMarkers)) {
            Write-Output 'LOG_LAYOUT_UNRESOLVED reason=REPOSITORY_MARKERS_NOT_FOUND'
        }
        else {
            try {
                Import-Module (Join-Path $PSScriptRoot 'logging\LocalLogRouter.psm1') `
                    -Force `
                    -DisableNameChecking
                $router = New-LocalLogRouter `
                    -RepositoryRoot $script:repositoryRoot `
                    -LogDirectory $logDirectory
                Write-Output "LOG_DIRECTORY path=$logDirectory"
                Write-Output (
                    'LOG_WATCH_COMMAND command=powershell.exe -NoProfile ' +
                    '-ExecutionPolicy Bypass -File scripts\watch-local-logs.ps1'
                )
            }
            catch {
                $router = $null
                Write-Output 'LOG_ROUTER_DEGRADED:INITIALIZATION_FAILED'
            }
        }
    }

    $viteArguments = @($ViteArguments) | Where-Object {
        -not [string]::IsNullOrWhiteSpace([string]$_)
    }
    $hasHostArgument = $false
    foreach ($argument in $viteArguments) {
        if ($argument -eq '--host') {
            $hasHostArgument = $true
            break
        }
    }
    if (-not $hasHostArgument) {
        $viteArguments = @('--host', '127.0.0.1') + @($viteArguments)
    }

    if ($ViteCommand -ne '') {
        $executable = 'powershell.exe'
        $arguments = @(
            '-NoProfile',
            '-ExecutionPolicy', 'Bypass',
            '-File', $ViteCommand
        ) + @($viteArguments) + @('--strictPort')
        $workingDirectory = $script:repositoryRoot
    }
    else {
        $node = Resolve-CommandPath 'node.exe' 'LOCAL_NODE_MISSING'
        $viteScript = Join-Path $script:repositoryRoot `
            'frontend\node_modules\vite\bin\vite.js'
        if (-not (Test-Path -LiteralPath $viteScript -PathType Leaf)) {
            Stop-WithCode 'LOCAL_FRONTEND_DEPENDENCIES_MISSING'
        }
        $executable = $node
        $arguments = @($viteScript) + @($viteArguments) + @('--strictPort')
        $workingDirectory = Join-Path $script:repositoryRoot 'frontend'
    }

    $exitCode = 1
    $process = $null
    try {
        $process = Start-OwnedProcess `
            $executable `
            $arguments `
            $workingDirectory `
            $router
        $process.WaitForExit()
        $exitCode = $process.ExitCode
    }
    finally {
        Stop-OwnedProcess $process
        if ($null -ne $router) {
            try {
                Stop-LocalLogRouter -Router $router
            }
            catch {
                [Console]::Error.WriteLine('LOG_ROUTER_DEGRADED:CLEANUP_FAILED')
            }
        }
    }
    exit $exitCode
}
catch {
    [Console]::Error.WriteLine([string]$_.Exception.Message)
    if ($testMode) {
        [Console]::Error.WriteLine(
            "LOCAL_TEST_FAILURE_POSITION line=$($_.InvocationInfo.ScriptLineNumber)"
        )
    }
    exit 1
}
