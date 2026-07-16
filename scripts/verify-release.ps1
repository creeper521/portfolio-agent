param(
    [switch]$SkipInstall,
    [switch]$SkipDockerCheck
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $root 'backend\target\portfolio-agent.jar'
$checker = Join-Path $root 'scripts\privacy-check.ps1'
$scanRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('portfolio-release-' + [guid]::NewGuid())

function Assert-ExitCode([string]$Label) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Label failed with exit code $LASTEXITCODE."
    }
}

Push-Location $root
try {
    $javaVersion = (& java --version | Out-String)
    Assert-ExitCode 'Java version check'
    if ($javaVersion -notmatch '(?m)^(?:openjdk|java)\s+21(?:\.|\s)') {
        throw 'Java 21 is required for release verification.'
    }

    $codeChecker = Join-Path $root 'scripts\code-quality-check.ps1'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\code-quality-check.test.ps1')
    Assert-ExitCode 'Code quality checker tests'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $codeChecker `
        -Path (Join-Path $root 'backend\src')
    Assert-ExitCode 'Java code quality check'

    if (-not $SkipInstall) {
        & npm.cmd --prefix frontend ci
        Assert-ExitCode 'Frontend dependency installation'
    }

    & npm.cmd --prefix frontend test -- --run
    Assert-ExitCode 'Frontend tests'
    & npm.cmd --prefix frontend run build
    Assert-ExitCode 'Frontend build'

    & mvn.cmd -f backend/pom.xml clean package
    Assert-ExitCode 'Backend clean package'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\privacy-check.test.ps1')
    Assert-ExitCode 'Privacy checker tests'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker `
        -Path (Join-Path $root 'backend\src\main\resources\public-data')
    Assert-ExitCode 'Public snapshot privacy scan'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker `
        -Path (Join-Path $root 'frontend\dist')
    Assert-ExitCode 'Frontend dist privacy scan'

    $entries = @(& jar.exe tf $jarPath)
    Assert-ExitCode 'JAR listing'
    $staticFiles = @($entries | Where-Object {
        $_ -like 'BOOT-INF/classes/static/*' -and -not $_.EndsWith('/')
    })
    if ($staticFiles.Count -ne 3 -or 'BOOT-INF/classes/static/index.html' -notin $staticFiles) {
        throw 'JAR static resources do not match the expected V0 frontend artifact.'
    }
    $forbiddenEntries = @($entries | Where-Object {
        $_ -match '(?i)(private-kb|candidate-snapshot|raw-evidence|unreviewed-screenshot|privacy-report)'
    })
    if ($forbiddenEntries.Count -gt 0) {
        throw "JAR contains forbidden private entries: $($forbiddenEntries -join ', ')"
    }

    New-Item -ItemType Directory -Path $scanRoot -Force | Out-Null
    Push-Location $scanRoot
    try {
        & jar.exe xf $jarPath
        Assert-ExitCode 'JAR extraction'
    }
    finally {
        Pop-Location
    }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker `
        -Path (Join-Path $scanRoot 'BOOT-INF\classes\public-data')
    Assert-ExitCode 'Packaged public data privacy scan'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker `
        -Path (Join-Path $scanRoot 'BOOT-INF\classes\static')
    Assert-ExitCode 'Packaged static resources privacy scan'

    & npm.cmd --prefix frontend run test:e2e
    Assert-ExitCode 'Playwright end-to-end tests'

    if (-not $SkipDockerCheck) {
        if (Get-Command docker -ErrorAction SilentlyContinue) {
            & docker build --check .
            Assert-ExitCode 'Docker build check'
        }
        else {
            Write-Warning 'Docker CLI is unavailable; Docker build check was not run.'
        }
    }

    Write-Output 'Release verification passed.'
}
finally {
    Pop-Location
    if (Test-Path -LiteralPath $scanRoot) {
        Remove-Item -LiteralPath $scanRoot -Recurse -Force
    }
}
