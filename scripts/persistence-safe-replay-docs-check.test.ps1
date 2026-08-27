$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$checker = Join-Path $PSScriptRoot 'persistence-safe-replay-docs-check.ps1'
function Invoke-Checker([string]$executable, [string]$path) {
    $processInfo = New-Object System.Diagnostics.ProcessStartInfo
    $processInfo.FileName = $executable
    $processInfo.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$checker`" -RootPath `"$path`""
    $processInfo.UseShellExecute = $false
    $processInfo.RedirectStandardOutput = $true
    $processInfo.RedirectStandardError = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $processInfo
    $null = $process.Start()
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    $exitCode = $process.ExitCode
    $process.Dispose()
    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = ($stdout + $stderr).Trim()
    }
}

$hosts = @(
    [pscustomobject]@{
        Name = 'WindowsPowerShell5.1'
        Executable = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    },
    [pscustomobject]@{
        Name = 'PowerShell7'
        Executable = (Get-Command pwsh.exe -ErrorAction Stop).Source
    }
)

foreach ($hostLane in $hosts) {
    $temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
        ('persistence-safe-replay-docs-' + [guid]::NewGuid().ToString('N'))
    try {
        New-Item -ItemType Directory -Path (Join-Path $temporaryRoot 'docs') -Force | Out-Null
        foreach ($relativePath in @(
            'AGENTS.md', 'SECURITY.md', 'docs/08-当前实现状态.md',
            'docs/15-Agent 2.0真实交互问题清单与修复边界.md',
            'docs/agent-architecture-status.json')) {
            Copy-Item -LiteralPath (Join-Path $root $relativePath) `
                -Destination (Join-Path $temporaryRoot $relativePath)
        }

        $passing = Invoke-Checker $hostLane.Executable $temporaryRoot
        if ($passing.ExitCode -ne 0) {
            throw "$($hostLane.Name) canonical replay documentation fixture must pass: $($passing.Output)"
        }
        Write-Output "PERSISTENCE_SAFE_REPLAY_DOCS_TEST host=$($hostLane.Name) scenario=positive exit=0"

        $driftedPath = Join-Path $temporaryRoot 'docs/08-当前实现状态.md'
        $driftedContent = Get-Content -LiteralPath $driftedPath -Raw -Encoding UTF8
        $driftedContent = $driftedContent.Replace(
            'REPLAY_BODY_NOT_RETAINED',
            'REPLAY_BODY_REMOVED_FOR_TEST')
        [IO.File]::WriteAllText(
            $driftedPath,
            $driftedContent,
            (New-Object Text.UTF8Encoding($false)))

        $drifted = Invoke-Checker $hostLane.Executable $temporaryRoot
        $normalizedDriftOutput = $drifted.Output -replace '\s', ''
        if ($drifted.ExitCode -eq 0 -or
                $normalizedDriftOutput -notmatch [regex]::Escape('REPLAY_BODY_NOT_RETAINED')) {
            throw "$($hostLane.Name) replay documentation drift must fail with the missing contract token: $($drifted.Output)"
        }
        Write-Output "PERSISTENCE_SAFE_REPLAY_DOCS_TEST host=$($hostLane.Name) scenario=missing-token exit=$($drifted.ExitCode)"
    } finally {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
Write-Output 'PERSISTENCE_SAFE_REPLAY_DOCS_TESTS_OK hosts=2 scenarios=4'
