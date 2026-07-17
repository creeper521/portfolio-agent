$ErrorActionPreference = 'Stop'

$runner = Join-Path $PSScriptRoot 'run-jar-e2e.ps1'
$root = Split-Path -Parent $PSScriptRoot
$sourceJar = Join-Path $root 'backend\target\portfolio-agent.jar'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('portfolio runner with spaces ' + [guid]::NewGuid())
$spacedJar = Join-Path $fixtureRoot 'packaged app\portfolio agent.jar'
$fakeNpm = Join-Path $fixtureRoot 'fake npm\npm with spaces.cmd'
$port = 43173

function Get-EnvironmentSnapshot([string]$Name) {
    $item = Get-Item -LiteralPath "Env:$Name" -ErrorAction SilentlyContinue
    return @{
        Exists = $null -ne $item
        Value = if ($null -ne $item) { $item.Value } else { $null }
    }
}

function Restore-EnvironmentVariable([string]$Name, [hashtable]$Snapshot) {
    if ($Snapshot.Exists) {
        Set-Item -LiteralPath "Env:$Name" -Value $Snapshot.Value
    }
    else {
        Remove-Item -LiteralPath "Env:$Name" -ErrorAction SilentlyContinue
    }
}

$environment = @{
    PLAYWRIGHT_EXTERNAL_SERVER = Get-EnvironmentSnapshot 'PLAYWRIGHT_EXTERNAL_SERVER'
    PLAYWRIGHT_REAL_API = Get-EnvironmentSnapshot 'PLAYWRIGHT_REAL_API'
    PLAYWRIGHT_BASE_URL = Get-EnvironmentSnapshot 'PLAYWRIGHT_BASE_URL'
}

try {
    if (-not (Test-Path -LiteralPath $sourceJar -PathType Leaf)) {
        throw 'Packaged JAR is required before running run-jar-e2e tests.'
    }

    $runnerCommand = Get-Command $runner
    foreach ($parameterName in @('JarPath', 'NpmExecutable', 'Port')) {
        if (-not $runnerCommand.Parameters.ContainsKey($parameterName)) {
            throw "Runner is missing testable parameter seam '$parameterName'."
        }
    }

    New-Item -ItemType Directory -Path (Split-Path -Parent $spacedJar) -Force | Out-Null
    New-Item -ItemType Directory -Path (Split-Path -Parent $fakeNpm) -Force | Out-Null
    Copy-Item -LiteralPath $sourceJar -Destination $spacedJar
    [System.IO.File]::WriteAllText($fakeNpm, "@exit /b 23`r`n", [System.Text.Encoding]::ASCII)

    $env:PLAYWRIGHT_EXTERNAL_SERVER = 'original-external'
    $env:PLAYWRIGHT_REAL_API = 'original-real'
    $env:PLAYWRIGHT_BASE_URL = 'original-base'

    $output = (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $runner `
        -JarPath $spacedJar -NpmExecutable $fakeNpm -Port $port 2>&1 | Out-String)
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 23) {
        throw "Expected runner to preserve Playwright exit code 23, got $exitCode. Output: $output"
    }
    if ($output -notmatch 'owns port 43173') {
        throw "Expected spaced JAR path to start and own the test port. Output: $output"
    }
    if ($output -notmatch 'Playwright environment restored\.') {
        throw "Expected runner to self-verify environment restoration. Output: $output"
    }
    if ($output -notmatch 'Packaged application process (?<pid>\d+) is stopped\.') {
        throw "Expected runner cleanup evidence. Output: $output"
    }

    $processId = [int]$Matches.pid
    if (Get-Process -Id $processId -ErrorAction SilentlyContinue) {
        throw "Runner left Java process $processId alive."
    }
    $listeners = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
    if ($listeners.Count -ne 0) {
        throw "Runner left port $port occupied."
    }

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $missingJarOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $runner `
            -JarPath (Join-Path $fixtureRoot 'missing.jar') -Port ($port + 1) 2>&1 | Out-String)
        $missingJarExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($missingJarExitCode -eq 0) {
        throw "Expected a missing-JAR startup failure to remain nonzero. Output: $missingJarOutput"
    }

    Write-Output 'run-jar-e2e tests passed'
}
finally {
    Restore-EnvironmentVariable 'PLAYWRIGHT_EXTERNAL_SERVER' $environment.PLAYWRIGHT_EXTERNAL_SERVER
    Restore-EnvironmentVariable 'PLAYWRIGHT_REAL_API' $environment.PLAYWRIGHT_REAL_API
    Restore-EnvironmentVariable 'PLAYWRIGHT_BASE_URL' $environment.PLAYWRIGHT_BASE_URL

    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolvedFixtureRoot = (Resolve-Path -LiteralPath $fixtureRoot).Path
        if (-not ([System.IO.Path]::GetFileName($resolvedFixtureRoot)).StartsWith(
            'portfolio runner with spaces '
        )) {
            throw "Refusing to remove unverified fixture path: $resolvedFixtureRoot"
        }
        Remove-Item -LiteralPath $resolvedFixtureRoot -Recurse -Force
    }
}
