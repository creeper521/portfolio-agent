param(
    [switch]$SkipInstall,
    [switch]$SkipDockerCheck,
    [switch]$RequireLiveProvider,
    [string]$BundleDirectory = ''
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
    $documentationChecker = Join-Path $root 'scripts\documentation-check.ps1'
    $architectureChecker = Join-Path $root 'scripts\architecture-check.ps1'
    $staticBundleChecker = Join-Path $root 'scripts\verify-static-bundle.ps1'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\code-quality-check.test.ps1')
    Assert-ExitCode 'Code quality checker tests'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $codeChecker `
        -Path (Join-Path $root 'backend\src')
    Assert-ExitCode 'Java code quality check'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\documentation-check.test.ps1')
    Assert-ExitCode 'Documentation checker tests'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $documentationChecker
    Assert-ExitCode 'Current documentation facts check'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\persistence-safe-replay-docs-check.test.ps1')
    Assert-ExitCode 'Persistence-safe replay documentation checker tests'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\persistence-safe-replay-docs-check.ps1')
    Assert-ExitCode 'Persistence-safe replay documentation check'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\public-api-surface-check.test.ps1')
    Assert-ExitCode 'Public API surface checker tests'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\public-api-surface-check.ps1')
    Assert-ExitCode 'Retired public API zero-reference check'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\architecture-check.test.ps1')
    Assert-ExitCode 'Architecture checker tests'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $architectureChecker `
        -Path (Join-Path $root 'backend\src')
    Assert-ExitCode 'Backend architecture check'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\agent-architecture-guardian.test.ps1')
    Assert-ExitCode 'Agent architecture guardian tests'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\agent-architecture-status.test.ps1')
    Assert-ExitCode 'Agent architecture status checker tests'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\agent-architecture-status.ps1')
    Assert-ExitCode 'Agent architecture status check'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\write-agent-verification-summary.test.ps1')
    Assert-ExitCode 'Agent verification layer summary tests'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\run-agent-behavior-audit-assets.test.ps1')
    Assert-ExitCode 'Agent behavior audit asset discovery tests'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\verify-static-bundle.test.ps1')
    Assert-ExitCode 'Static bundle checker tests'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\privacy-check.test.ps1')
    Assert-ExitCode 'Privacy checker tests'
    if (-not $SkipDockerCheck) {
        & docker info --format '{{.ServerVersion}}'
        Assert-ExitCode 'Runtime privacy Docker readiness'
        & $maven -f backend/pom.xml `
            '-Dtest=AgentStatePayloadCodecTest#decodedCompleteSettlementDoesNotContainVisitorOrProviderSentinel,JdbcAgentStateStoreIntegrationTest#postgresCompleteSettlementPlaintextExcludesVisitorAndProviderSentinel' `
            test
        Assert-ExitCode 'Complete settlement runtime privacy tests'
    }
    else {
        Write-Warning 'Complete settlement PostgreSQL privacy test was explicitly skipped.'
    }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\assert-live-public-turn-response.test.ps1')
    Assert-ExitCode 'Final PublicAgentTurn Live Provider checker tests'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\assert-live-general-answer-quality.test.ps1')
    Assert-ExitCode 'General answer quality checker tests'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\assert-live-project-discussion-context.test.ps1')
    Assert-ExitCode 'Project discussion live checker tests'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\provider-probe\invoke-live-provider-probe.test.ps1')
    Assert-ExitCode 'Live Provider probe contract tests'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker `
        -Path (Join-Path $root 'backend\src\main')
    Assert-ExitCode 'Pre-package production source and configuration privacy scan'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker `
        -Path (Join-Path $root 'governance\portfolio-governance')
    Assert-ExitCode 'Tracked governance package privacy scan'
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

    if (-not [string]::IsNullOrWhiteSpace($BundleDirectory)) {
        $resolvedBundleDirectory = (Resolve-Path -LiteralPath `
            $BundleDirectory -ErrorAction Stop).Path
        & java.exe `
            '-Dloader.main=com.portfolio.agent.release.PublicBundleVerificationCli' `
            -cp $jarPath `
            org.springframework.boot.loader.launch.PropertiesLauncher `
            $resolvedBundleDirectory
        Assert-ExitCode 'External seven-file public Bundle verification'
    }

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\build-retrieval-bundle.test.ps1')
    Assert-ExitCode 'Canonical retrieval candidate builder tests'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\portfolio-governance.test.ps1')
    Assert-ExitCode 'Portfolio governance B and C2 CLI tests'

    $entries = @(& jar.exe tf $jarPath)
    Assert-ExitCode 'JAR listing'
    $requiredPromptEntries = @(
        'BOOT-INF/classes/prompts/goal-interpretation-system.txt',
        'BOOT-INF/classes/prompts/general-knowledge-system.txt'
    )
    $missingPromptEntries = @($requiredPromptEntries | Where-Object {
        $_ -notin $entries
    })
    if ($missingPromptEntries.Count -gt 0) {
        throw "JAR is missing required system prompts: $($missingPromptEntries -join ', ')"
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

    $jarE2eArguments = @(
        '-NoProfile',
        '-ExecutionPolicy',
        'Bypass',
        '-File',
        (Join-Path $root 'scripts\run-jar-e2e.ps1')
    )
    if ($RequireLiveProvider) {
        $jarE2eArguments += '-RequireLiveProvider'
    }
    & powershell.exe @jarE2eArguments
    Assert-ExitCode 'Packaged JAR Playwright integration tests'

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\run-jar-e2e.ps1') `
        -Lane PROJECT_DISCUSSION_EXPIRY
    Assert-ExitCode 'Packaged short-TTL project discussion integration tests'

    if ($RequireLiveProvider) {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File (Join-Path $root 'scripts\run-jar-e2e.ps1') `
            -Lane PROJECT_DISCUSSION -RequireLiveProvider
        Assert-ExitCode 'Packaged project discussion Provider integration tests'
    }

    if (-not $SkipDockerCheck) {
        if (Get-Command docker -ErrorAction SilentlyContinue) {
            & docker build --check .
            Assert-ExitCode 'Docker build check'
        }
        else {
            Write-Warning 'Docker CLI is unavailable; Docker build check was not run.'
        }
    }

    $postgreSqlState = if ($SkipDockerCheck) { 'NOT_RUN' } else { 'PASS' }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root 'scripts\write-agent-verification-summary.ps1') `
        -Deterministic PASS `
        -ScenarioRuntime NOT_RUN `
        -BrowserContract PASS `
        -BrowserBody IN_PROGRESS `
        -PostgreSqlState $postgreSqlState `
        -PostgreSqlJvmRestart NOT_RUN `
        -ProviderQuality NOT_RUN
    Assert-ExitCode 'Agent verification layer summary'
}
finally {
    Pop-Location
    if (Test-Path -LiteralPath $scanRoot) {
        Remove-Item -LiteralPath $scanRoot -Recurse -Force
    }
}
