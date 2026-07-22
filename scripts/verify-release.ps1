param(
    [switch]$SkipInstall,
    [switch]$SkipDockerCheck
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $root 'backend\target\portfolio-agent.jar'
$checker = Join-Path $root 'scripts\privacy-check.ps1'
$scanRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('portfolio-release-' + [guid]::NewGuid())
$maven = if (Test-Path -LiteralPath 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd') {
    'C:\tools\apache-maven-3.9.9\bin\mvn.cmd'
} else {
    (Get-Command mvn.cmd -ErrorAction Stop).Source
}

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
    $architectureChecker = Join-Path $root 'scripts\architecture-check.ps1'
    $staticBundleChecker = Join-Path $root 'scripts\verify-static-bundle.ps1'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\code-quality-check.test.ps1')
    Assert-ExitCode 'Code quality checker tests'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $codeChecker `
        -Path (Join-Path $root 'backend\src')
    Assert-ExitCode 'Java code quality check'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\architecture-check.test.ps1')
    Assert-ExitCode 'Architecture checker tests'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $architectureChecker `
        -Path (Join-Path $root 'backend\src')
    Assert-ExitCode 'Backend architecture check'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\verify-static-bundle.test.ps1')
    Assert-ExitCode 'Static bundle checker tests'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\privacy-check.test.ps1')
    Assert-ExitCode 'Privacy checker tests'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker `
        -Path (Join-Path $root 'backend\src\main')
    Assert-ExitCode 'Pre-package production source and configuration privacy scan'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker `
        -Path (Join-Path $root '.agents\skills\portfolio-governance')
    Assert-ExitCode 'Public governance skill privacy scan'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker `
        -Path (Join-Path $root 'backend\src\main\resources\public-data')
    Assert-ExitCode 'Pre-package public snapshot privacy scan'

    if (-not $SkipInstall) {
        & npm.cmd --prefix frontend ci
        Assert-ExitCode 'Frontend dependency installation'
    }

    & npm.cmd --prefix frontend run check
    Assert-ExitCode 'Frontend type check'
    & npm.cmd --prefix frontend run lint
    Assert-ExitCode 'Frontend lint'
    & npm.cmd --prefix frontend test -- --run
    Assert-ExitCode 'Frontend tests'
    & npm.cmd --prefix frontend run build
    Assert-ExitCode 'Frontend build'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker `
        -Path (Join-Path $root 'frontend\dist')
    Assert-ExitCode 'Pre-package frontend dist privacy scan'

    & $maven -f backend/pom.xml clean package
    Assert-ExitCode 'Backend clean package'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\build-retrieval-bundle.test.ps1')
    Assert-ExitCode 'Canonical retrieval candidate builder tests'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\portfolio-governance.test.ps1')
    Assert-ExitCode 'Portfolio governance B and C2 CLI tests'

    $localModel = Join-Path $root 'runtime-models\bge-small-zh-v1.5'
    if (Test-Path -LiteralPath (Join-Path $localModel 'onnx\model_quantized.onnx') -PathType Leaf) {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File (Join-Path $root 'scripts\run-local-retrieval-benchmark.ps1') `
            -ModelDirectory $localModel
        Assert-ExitCode 'Local retrieval correctness and performance gates'
    }

    $entries = @(& jar.exe tf $jarPath)
    Assert-ExitCode 'JAR listing'
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
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $staticBundleChecker `
        -DistPath (Join-Path $root 'frontend\dist') `
        -PackagedStaticPath (Join-Path $scanRoot 'BOOT-INF\classes\static')
    Assert-ExitCode 'Packaged static bundle verification'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker `
        -Path (Join-Path $scanRoot 'BOOT-INF\classes')
    Assert-ExitCode 'Packaged classpath privacy scan'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker `
        -Path (Join-Path $scanRoot 'BOOT-INF\classes\public-data')
    Assert-ExitCode 'Packaged public data privacy scan'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker `
        -Path (Join-Path $scanRoot 'BOOT-INF\classes\static')
    Assert-ExitCode 'Packaged static resources privacy scan'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker -Path $root
    Assert-ExitCode 'Final repository risk-artifact privacy scan'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\run-jar-e2e.ps1')
    Assert-ExitCode 'Packaged JAR Playwright integration tests'

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
