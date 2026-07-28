$ErrorActionPreference = 'Stop'

$runner = Join-Path $PSScriptRoot 'run-jar-e2e.ps1'
$releaseVerifier = Join-Path $PSScriptRoot 'verify-release.ps1'
$root = Split-Path -Parent $PSScriptRoot
$sourceJar = Join-Path $root 'backend\target\portfolio-agent.jar'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('portfolio runner with spaces ' + [guid]::NewGuid())
$spacedJar = Join-Path $fixtureRoot 'packaged app\portfolio agent.jar'
$fakeNpm = Join-Path $fixtureRoot 'fake npm\npm with spaces.cmd'
$fakeJava = Join-Path $fixtureRoot 'fake java\java with spaces.cmd'
$javaArgumentCapture = Join-Path $fixtureRoot 'java-arguments.txt'
$cleanupProbe = Join-Path $fixtureRoot 'cleanup-probe.json'
$cleanupRunner = Join-Path $fixtureRoot 'run-jar-e2e-cleanup-failure.ps1'
$port = 43173

function Get-EnvironmentSnapshot([string]$Name) {
    $value = [System.Environment]::GetEnvironmentVariable(
        $Name,
        [System.EnvironmentVariableTarget]::Process
    )
    return @{
        Exists = $null -ne $value
        Value = $value
    }
}

function Restore-EnvironmentVariable([string]$Name, [hashtable]$Snapshot) {
    $value = if ($Snapshot.Exists) { [string]$Snapshot.Value } else { $null }
    [System.Environment]::SetEnvironmentVariable(
        $Name,
        $value,
        [System.EnvironmentVariableTarget]::Process
    )
}

$environment = @{
    PLAYWRIGHT_EXTERNAL_SERVER = Get-EnvironmentSnapshot 'PLAYWRIGHT_EXTERNAL_SERVER'
    PLAYWRIGHT_REAL_API = Get-EnvironmentSnapshot 'PLAYWRIGHT_REAL_API'
    PLAYWRIGHT_BASE_URL = Get-EnvironmentSnapshot 'PLAYWRIGHT_BASE_URL'
    PLAYWRIGHT_REAL_RETRIEVAL = Get-EnvironmentSnapshot 'PLAYWRIGHT_REAL_RETRIEVAL'
    PORTFOLIO_MODEL_ENABLED = Get-EnvironmentSnapshot 'PORTFOLIO_MODEL_ENABLED'
    PORTFOLIO_MODEL_DATA_POLICY_APPROVED = Get-EnvironmentSnapshot `
        'PORTFOLIO_MODEL_DATA_POLICY_APPROVED'
    PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED = Get-EnvironmentSnapshot `
        'PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED'
    PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED = Get-EnvironmentSnapshot `
        'PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED'
    PORTFOLIO_AGENT_DEEPSEEK_API_KEY = Get-EnvironmentSnapshot `
        'PORTFOLIO_AGENT_DEEPSEEK_API_KEY'
    PORTFOLIO_MODEL_TIMEOUT = Get-EnvironmentSnapshot 'PORTFOLIO_MODEL_TIMEOUT'
}

try {
    if (-not (Test-Path -LiteralPath $sourceJar -PathType Leaf)) {
        throw 'Packaged JAR is required before running run-jar-e2e tests.'
    }

    $runnerCommand = Get-Command $runner
    foreach ($parameterName in @(
        'JarPath',
        'NpmExecutable',
        'Port',
        'RequireLiveProvider'
    )) {
        if (-not $runnerCommand.Parameters.ContainsKey($parameterName)) {
            throw "Runner is missing testable parameter seam '$parameterName'."
        }
    }
    if ($runnerCommand.Parameters.ContainsKey('LiveProviderResponseCleanup')) {
        throw 'Production runner must not expose a replaceable cleanup scriptblock.'
    }
    $releaseCommand = Get-Command $releaseVerifier
    if (-not $releaseCommand.Parameters.ContainsKey('RequireLiveProvider')) {
        throw "Release verifier is missing parameter seam 'RequireLiveProvider'."
    }
    $releaseSource = Get-Content -LiteralPath $releaseVerifier -Raw
    if ($releaseSource -notmatch 'assert-live-provider-response\.test\.ps1') {
        throw 'Release verifier does not run the live Provider assertion tests.'
    }

    New-Item -ItemType Directory -Path (Split-Path -Parent $spacedJar) -Force | Out-Null
    New-Item -ItemType Directory -Path (Split-Path -Parent $fakeNpm) -Force | Out-Null
    New-Item -ItemType Directory -Path (Split-Path -Parent $fakeJava) -Force | Out-Null
    Copy-Item -LiteralPath $sourceJar -Destination $spacedJar
    [System.IO.File]::WriteAllText($fakeNpm, "@exit /b 23`r`n", [System.Text.Encoding]::ASCII)
    $escapedJavaArgumentCapture = $javaArgumentCapture.Replace('%', '%%')
    [System.IO.File]::WriteAllText(
        $fakeJava,
        "@echo %* > `"$escapedJavaArgumentCapture`"`r`n@exit /b 31`r`n",
        [System.Text.Encoding]::ASCII
    )

    $previousArgumentCaptureErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $normalCaptureOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $runner -JarPath $spacedJar -JavaExecutable $fakeJava `
            -NpmExecutable $fakeNpm -Port ($port + 10) 2>&1 | Out-String)
        $normalCaptureExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousArgumentCaptureErrorActionPreference
    }
    if ($normalCaptureExitCode -eq 0) {
        throw "Expected capture-only Java wrapper to stop before readiness. Output: $normalCaptureOutput"
    }
    $normalJavaArguments = Get-Content -LiteralPath $javaArgumentCapture -Raw
    foreach ($disabledArgument in @(
        '--portfolio.model-expression.enabled=false',
        '--portfolio.conversational-agent.enabled=false'
    )) {
        $matchCount = ([regex]::Matches(
            $normalJavaArguments,
            '(?<!\S)' + [regex]::Escape($disabledArgument) + '(?!\S)'
        )).Count
        if ($matchCount -ne 1) {
            throw "Expected normal mode to pass '$disabledArgument' exactly once."
        }
    }

    Remove-Item -LiteralPath $javaArgumentCapture -Force
    $previousLiveCaptureErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $liveCaptureOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $runner -JarPath $spacedJar -JavaExecutable $fakeJava `
            -NpmExecutable $fakeNpm -Port ($port + 11) -RequireLiveProvider `
            2>&1 | Out-String)
        $liveCaptureExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousLiveCaptureErrorActionPreference
    }
    if ($liveCaptureExitCode -eq 0) {
        throw "Expected capture-only Java wrapper to stop before readiness. Output: $liveCaptureOutput"
    }
    $liveJavaArguments = Get-Content -LiteralPath $javaArgumentCapture -Raw
    foreach ($disabledArgument in @(
        '--portfolio.model-expression.enabled=false',
        '--portfolio.conversational-agent.enabled=false'
    )) {
        if ($liveJavaArguments -match [regex]::Escape($disabledArgument)) {
            throw "Live Provider mode unexpectedly passed '$disabledArgument'."
        }
    }

    $env:PLAYWRIGHT_EXTERNAL_SERVER = 'original-external'
    $env:PLAYWRIGHT_REAL_API = 'original-real'
    $env:PLAYWRIGHT_BASE_URL = 'original-base'
    $env:PLAYWRIGHT_REAL_RETRIEVAL = 'original-retrieval'
    $env:PORTFOLIO_MODEL_ENABLED = 'true'
    $env:PORTFOLIO_MODEL_DATA_POLICY_APPROVED = 'true'
    $env:PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED = 'true'
    $env:PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED = 'true'
    $env:PORTFOLIO_AGENT_DEEPSEEK_API_KEY = 'provider-key-must-not-leak'
    $env:PORTFOLIO_MODEL_TIMEOUT = '1ms'

    $output = (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $runner `
        -JarPath $spacedJar -NpmExecutable $fakeNpm -Port $port 2>&1 | Out-String)
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 23) {
        throw "Expected runner to preserve Playwright exit code 23, got $exitCode. Output: $output"
    }
    if ($output -notmatch 'owns port 43173') {
        throw "Expected spaced JAR path to start and own the test port. Output: $output"
    }

    if ($output -notmatch 'Packaged Case API smoke passed\.') {
        throw "Expected packaged Case API smoke evidence. Output: $output"
    }
    if ($output -notmatch 'Packaged Case Agent smoke passed\.') {
        throw "Expected packaged Case Agent smoke evidence. Output: $output"
    }
    if ($output -match 'provider-key-must-not-leak') {
        throw 'Runner output leaked the Provider key sentinel.'
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

    [System.IO.File]::WriteAllText(
        $cleanupProbe,
        '{}',
        [System.Text.UTF8Encoding]::new($false)
    )
    $cleanupRunnerSource = Get-Content -LiteralPath $runner -Raw
    $cleanupPathInitialization = '$liveProviderResponsePath = $null'
    $cleanupCommand = 'Remove-Item -LiteralPath $liveProviderResponsePath -Force'
    if (([regex]::Matches(
        $cleanupRunnerSource,
        [regex]::Escape($cleanupPathInitialization)
    )).Count -ne 1) {
        throw 'Cleanup test copy requires exactly one response-path initialization.'
    }
    if (([regex]::Matches(
        $cleanupRunnerSource,
        [regex]::Escape($cleanupCommand)
    )).Count -ne 1) {
        throw 'Cleanup test copy requires exactly one hard-coded cleanup command.'
    }
    $escapedCleanupProbe = $cleanupProbe.Replace("'", "''")
    $cleanupRunnerSource = $cleanupRunnerSource.Replace(
        $cleanupPathInitialization,
        "`$liveProviderResponsePath = '$escapedCleanupProbe'"
    )
    $cleanupRunnerSource = $cleanupRunnerSource.Replace(
        $cleanupCommand,
        "throw 'simulated live response cleanup failure'"
    )
    [System.IO.File]::WriteAllText(
        $cleanupRunner,
        $cleanupRunnerSource,
        [System.Text.UTF8Encoding]::new($false)
    )

    $previousCleanupErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $cleanupOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $cleanupRunner -JarPath $spacedJar -NpmExecutable $fakeNpm `
            -Port ($port + 2) 2>&1 | Out-String)
        $cleanupExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousCleanupErrorActionPreference
    }
    if ($cleanupExitCode -eq 0) {
        throw "Expected injected cleanup failure to remain nonzero. Output: $cleanupOutput"
    }
    if ($cleanupOutput -notmatch 'simulated live response cleanup failure') {
        throw "Expected the injected cleanup failure to execute. Output: $cleanupOutput"
    }
    if ($cleanupOutput -notmatch 'Playwright environment restored\.') {
        throw "Cleanup failure skipped Playwright environment restoration. Output: $cleanupOutput"
    }
    if ($cleanupOutput -notmatch 'Packaged application process (?<pid>\d+) is stopped\.') {
        throw "Cleanup failure skipped packaged process termination. Output: $cleanupOutput"
    }
    $cleanupProcessId = [int]$Matches.pid
    if (Get-Process -Id $cleanupProcessId -ErrorAction SilentlyContinue) {
        throw "Cleanup failure left Java process $cleanupProcessId alive."
    }
    $cleanupListeners = @(Get-NetTCPConnection -LocalPort ($port + 2) -State Listen `
        -ErrorAction SilentlyContinue)
    if ($cleanupListeners.Count -ne 0) {
        throw "Cleanup failure left port $($port + 2) occupied."
    }
    if ($output -notmatch 'Provider calls disabled for deterministic smoke\.') {
        throw "Expected normal mode to override inherited Provider enablement. Output: $output"
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
    Restore-EnvironmentVariable 'PLAYWRIGHT_REAL_RETRIEVAL' `
        $environment.PLAYWRIGHT_REAL_RETRIEVAL
    Restore-EnvironmentVariable 'PORTFOLIO_MODEL_ENABLED' `
        $environment.PORTFOLIO_MODEL_ENABLED
    Restore-EnvironmentVariable 'PORTFOLIO_MODEL_DATA_POLICY_APPROVED' `
        $environment.PORTFOLIO_MODEL_DATA_POLICY_APPROVED
    Restore-EnvironmentVariable 'PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED' `
        $environment.PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED
    Restore-EnvironmentVariable 'PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED' `
        $environment.PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED
    Restore-EnvironmentVariable 'PORTFOLIO_AGENT_DEEPSEEK_API_KEY' `
        $environment.PORTFOLIO_AGENT_DEEPSEEK_API_KEY
    Restore-EnvironmentVariable 'PORTFOLIO_MODEL_TIMEOUT' `
        $environment.PORTFOLIO_MODEL_TIMEOUT

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
