param(
    [Parameter(Mandatory = $true)]
    [string]$JarPath,
    [Parameter(Mandatory = $true)]
    [string]$ContextDatabaseUrl,
    [Parameter(Mandatory = $true)]
    [string]$ContextDatabaseUsername,
    [Parameter(Mandatory = $true)]
    [string]$ContextDatabasePassword,
    [Parameter(Mandatory = $true)]
    [string]$CurrentTokenKeyId,
    [Parameter(Mandatory = $true)]
    [string]$CurrentTokenKey,
    [Parameter(Mandatory = $true)]
    [string]$CurrentPayloadKeyId,
    [Parameter(Mandatory = $true)]
    [string]$CurrentPayloadKey,
    [ValidateRange(1, 65535)]
    [int]$Port = 4173,
    [string]$JavaExecutable = 'java.exe',
    [string]$NpmExecutable = 'npm.cmd',
    [ValidateRange(1, 120)]
    [int]$ReadinessTimeoutSeconds = 60,
    [ValidateRange(30, 300)]
    [int]$BrowserTimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = [System.IO.Path]::GetFullPath($JarPath)
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw 'Packaged JAR is missing; JVM restart browser gate cannot start.'
}
if ($jar.Contains('"')) {
    throw 'Packaged JAR path contains an unsupported quote character.'
}
if ($ContextDatabaseUrl -notmatch '^jdbc:postgresql://') {
    throw 'JVM restart browser gate requires an existing PostgreSQL JDBC URL.'
}
if ([string]::IsNullOrWhiteSpace($ContextDatabaseUsername) -or
        [string]::IsNullOrWhiteSpace($ContextDatabasePassword)) {
    throw 'JVM restart browser gate requires PostgreSQL credentials.'
}
foreach ($keyId in @($CurrentTokenKeyId, $CurrentPayloadKeyId)) {
    if ($keyId -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$') {
        throw 'JVM restart browser gate received an invalid key identity.'
    }
}
foreach ($encodedKey in @($CurrentTokenKey, $CurrentPayloadKey)) {
    try {
        $decoded = [Convert]::FromBase64String($encodedKey)
    }
    catch {
        throw 'JVM restart browser gate keys must be base64 encoded.'
    }
    if ($decoded.Length -ne 32) {
        throw 'JVM restart browser gate keys must decode to exactly 32 bytes.'
    }
}

$javaCommand = Get-Command $JavaExecutable -ErrorAction SilentlyContinue
if ($null -eq $javaCommand) {
    throw 'Java is unavailable; JVM restart browser gate cannot start.'
}
$npmCommand = Get-Command $NpmExecutable -ErrorAction SilentlyContinue
if ($null -eq $npmCommand) {
    throw 'npm is unavailable; JVM restart browser gate cannot start Playwright.'
}

$frontend = Join-Path $root 'frontend'
$suffix = [guid]::NewGuid().ToString('N')
$temporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$coordinationDirectory = Join-Path $temporaryRoot `
    "portfolio-jvm-restart-browser-$suffix"
$browserReadyPath = Join-Path $coordinationDirectory 'browser-ready.signal'
$serverRestartedPath = Join-Path $coordinationDirectory 'server-restarted.signal'
$signalTemporaryPath = Join-Path $temporaryRoot `
    "portfolio-jvm-restart-signal-$suffix.tmp"
$jvm1StdOut = Join-Path $temporaryRoot "portfolio-jvm-restart-jvm1-$suffix.stdout.log"
$jvm1StdErr = Join-Path $temporaryRoot "portfolio-jvm-restart-jvm1-$suffix.stderr.log"
$jvm2StdOut = Join-Path $temporaryRoot "portfolio-jvm-restart-jvm2-$suffix.stdout.log"
$jvm2StdErr = Join-Path $temporaryRoot "portfolio-jvm-restart-jvm2-$suffix.stderr.log"
$browserStdOut = Join-Path $temporaryRoot "portfolio-jvm-restart-browser-$suffix.stdout.log"
$browserStdErr = Join-Path $temporaryRoot "portfolio-jvm-restart-browser-$suffix.stderr.log"
$controlledFiles = @(
    $signalTemporaryPath,
    $jvm1StdOut, $jvm1StdErr,
    $jvm2StdOut, $jvm2StdErr,
    $browserStdOut, $browserStdErr
)

function Get-EnvironmentSnapshot([string]$Name) {
    return [Environment]::GetEnvironmentVariable($Name, 'Process')
}

function Restore-Environment([hashtable]$Snapshot) {
    foreach ($entry in $Snapshot.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable(
            [string]$entry.Key,
            $entry.Value,
            'Process'
        )
    }
}

function Stop-ControlledProcess($Process, [string]$Label) {
    if ($null -eq $Process) { return }
    $Process.Refresh()
    if (-not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
        if (-not $Process.WaitForExit(10000)) {
            throw "$Label did not stop within the controlled deadline."
        }
    }
}

function Stop-ControlledBrowserTree($Process) {
    if ($null -eq $Process) { return }
    $Process.Refresh()
    if ($Process.HasExited) { return }
    $controlledId = [int]$Process.Id
    if ($controlledId -lt 1) {
        throw 'Refusing to stop an invalid Playwright process identity.'
    }
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & taskkill.exe /PID $controlledId /T /F 2>&1 | Out-Null
        $taskkillExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }
    $Process.Refresh()
    if ($taskkillExitCode -ne 0 -and -not $Process.HasExited) {
        throw 'Playwright process tree could not be stopped safely.'
    }
    if (-not $Process.WaitForExit(10000)) {
        throw 'Playwright process tree did not stop within the controlled deadline.'
    }
}

function Start-PackagedJvm([string]$StdOut, [string]$StdErr) {
    $quotedJar = '"' + $jar + '"'
    $arguments = @(
        '-jar',
        $quotedJar,
        "--server.port=$Port",
        '--spring.profiles.active=prod',
        '--spring.main.banner-mode=off',
        '--portfolio.conversation-context.mode=POSTGRESQL',
        '--portfolio.model-runtime.enabled=false',
        '--portfolio.diagnostics.frontend-ingest-enabled=true',
        '--portfolio.agent-runtime.requests-per-minute=1000',
        '--portfolio.agent-runtime.max-concurrent-per-source=1000',
        '--portfolio.agent-runtime.max-active-turns=1000'
    )
    return Start-Process -FilePath $javaCommand.Source `
        -ArgumentList $arguments `
        -RedirectStandardOutput $StdOut `
        -RedirectStandardError $StdErr `
        -WindowStyle Hidden `
        -PassThru
}

function Wait-PackagedReadiness($Process, [string]$ExpectedContentVersion) {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($ReadinessTimeoutSeconds)
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        $Process.Refresh()
        if ($Process.HasExited) {
            throw 'Packaged JVM exited before readiness.'
        }
        try {
            $portfolio = Invoke-RestMethod `
                -Uri "http://127.0.0.1:$Port/api/portfolio" `
                -Method Get `
                -TimeoutSec 2
            $contentVersion = [string]$portfolio.contentVersion
            if (-not [string]::IsNullOrWhiteSpace($contentVersion)) {
                if (-not [string]::IsNullOrWhiteSpace($ExpectedContentVersion) -and
                        $contentVersion -ne $ExpectedContentVersion) {
                    throw 'Packaged JVM contentVersion changed across restart.'
                }
                return $contentVersion
            }
        }
        catch {
            if ($_.Exception.Message -match 'contentVersion changed') { throw }
        }
        Start-Sleep -Milliseconds 200
    }
    throw 'Packaged JVM did not become ready within the controlled deadline.'
}

function Wait-ClosedSignal($BrowserProcess) {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($BrowserTimeoutSeconds)
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        $BrowserProcess.Refresh()
        if ($BrowserProcess.HasExited) {
            throw "Playwright exited before BROWSER_READY with code $($BrowserProcess.ExitCode)."
        }
        if (Test-Path -LiteralPath $browserReadyPath -PathType Leaf) {
            $actual = [System.IO.File]::ReadAllText($browserReadyPath).Trim()
            if ($actual -eq 'BROWSER_READY') { return }
            if (-not [string]::IsNullOrWhiteSpace($actual)) {
                throw 'Browser coordination file contained an unexpected state.'
            }
        }
        Start-Sleep -Milliseconds 100
    }
    throw 'Timed out waiting for BROWSER_READY.'
}

function Assert-ClosedCoordinationDirectory {
    $entries = @(Get-ChildItem -LiteralPath $coordinationDirectory -Force)
    $names = @($entries | ForEach-Object { $_.Name } | Sort-Object)
    $expected = @('browser-ready.signal', 'server-restarted.signal')
    if ([string]::Join('|', $names) -ne [string]::Join('|', $expected)) {
        throw 'Browser coordination directory escaped its closed state set.'
    }
    if ([System.IO.File]::ReadAllText($browserReadyPath).Trim() -ne
            'BROWSER_READY' -or
            [System.IO.File]::ReadAllText($serverRestartedPath).Trim() -ne
            'SERVER_RESTARTED') {
        throw 'Browser coordination directory contained an unexpected state.'
    }
}

function Assert-LogPrivacy(
        [string[]]$Paths,
        [string[]]$Secrets,
        [string]$Label,
        [bool]$RequireStructuredJson) {
    foreach ($path in $Paths) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
        $content = [System.IO.File]::ReadAllText($path)
        foreach ($secret in $Secrets) {
            if (-not [string]::IsNullOrEmpty($secret) -and
                    $content.Contains($secret)) {
                throw "$Label log exposed controlled secret material."
            }
        }
        foreach ($pattern in @(
                '(?i)Bearer\s+[A-Za-z0-9._~-]+',
                '(?i)"(?:authorization|resumeToken|question|prompt|requestBody|responseBody|conversationWindow)"\s*:',
                '(?i)portfolio\.agent\.resume-token\.v1'
            )) {
            if ($content -match $pattern) {
                throw "$Label log exposed a private browser or visitor field."
            }
        }
        if ($RequireStructuredJson) {
            foreach ($line in ($content -split "`r?`n")) {
                if ([string]::IsNullOrWhiteSpace($line)) { continue }
                try { $null = $line | ConvertFrom-Json }
                catch { throw "$Label stdout contained an unstructured log line." }
            }
        }
    }
}

function Remove-ControlledArtifacts {
    $resolvedCoordination = [System.IO.Path]::GetFullPath($coordinationDirectory)
    $resolvedParent = [System.IO.Path]::GetFullPath(
        (Split-Path -Parent $resolvedCoordination)
    )
    $coordinationName = Split-Path -Leaf $resolvedCoordination
    if ($resolvedParent -ne $temporaryRoot.TrimEnd('\') -and
            $resolvedParent.TrimEnd('\') -ne $temporaryRoot.TrimEnd('\')) {
        throw 'Refusing to clean coordination directory outside the temp root.'
    }
    if ($coordinationName -notmatch
            '^portfolio-jvm-restart-browser-[a-f0-9]{32}$') {
        throw 'Refusing to clean an uncontrolled coordination directory.'
    }
    if (Test-Path -LiteralPath $resolvedCoordination) {
        Remove-Item -LiteralPath $resolvedCoordination -Recurse -Force
    }
    foreach ($path in $controlledFiles) {
        $resolved = [System.IO.Path]::GetFullPath($path)
        if ((Split-Path -Parent $resolved).TrimEnd('\') -ne
                $temporaryRoot.TrimEnd('\') -or
                (Split-Path -Leaf $resolved) -notmatch
                '^portfolio-jvm-restart-(?:signal|jvm1|jvm2|browser)-[a-f0-9]{32}\.(?:tmp|stdout\.log|stderr\.log)$') {
            throw 'Refusing to clean an uncontrolled JVM restart artifact.'
        }
        if (Test-Path -LiteralPath $resolved -PathType Leaf) {
            Remove-Item -LiteralPath $resolved -Force
        }
    }
}

$environmentValues = [ordered]@{
    PORTFOLIO_CONVERSATION_CONTEXT_MODE = 'POSTGRESQL'
    PORTFOLIO_CONTEXT_DATABASE_URL = $ContextDatabaseUrl
    PORTFOLIO_CONTEXT_DATABASE_USERNAME = $ContextDatabaseUsername
    PORTFOLIO_CONTEXT_DATABASE_PASSWORD = $ContextDatabasePassword
    PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID = $CurrentTokenKeyId
    PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY = $CurrentTokenKey
    PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID = $CurrentPayloadKeyId
    PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY = $CurrentPayloadKey
    PORTFOLIO_MODEL_RUNTIME_ENABLED = 'false'
    PORTFOLIO_GLM_ENABLED = 'false'
    PORTFOLIO_GLM_DATA_POLICY_APPROVED = 'false'
    PORTFOLIO_GLM_API_KEY = $null
    PORTFOLIO_QWEN_ENABLED = 'false'
    PORTFOLIO_QWEN_DATA_POLICY_APPROVED = 'false'
    PORTFOLIO_QWEN_ENDPOINT = $null
    PORTFOLIO_QWEN_API_KEY = $null
    PLAYWRIGHT_EXTERNAL_SERVER = '1'
    PLAYWRIGHT_REAL_API = '1'
    PLAYWRIGHT_BASE_URL = "http://127.0.0.1:$Port"
    PLAYWRIGHT_REAL_RETRIEVAL = '0'
    PLAYWRIGHT_ADMISSION = '0'
    PLAYWRIGHT_CONTENT_ONLY = '0'
    PLAYWRIGHT_SLOW_PROVIDER = '0'
    PLAYWRIGHT_DEPTH_TWO = '0'
    PLAYWRIGHT_PROJECT_DISCUSSION = '0'
    PLAYWRIGHT_PROJECT_DISCUSSION_EXPIRY = '0'
    PLAYWRIGHT_PROVIDER_STALL_COORDINATION_URL = $null
    PLAYWRIGHT_MODEL_SELECTION = '0'
    PLAYWRIGHT_PUBLIC_TURN_NEGATIVE = '0'
    PLAYWRIGHT_JVM_RESTART_BROWSER = '1'
    PLAYWRIGHT_JVM_RESTART_COORDINATION_DIR = $coordinationDirectory
}
$environmentSnapshot = @{}
foreach ($name in $environmentValues.Keys) {
    $environmentSnapshot[$name] = Get-EnvironmentSnapshot $name
}

$jvm1 = $null
$jvm2 = $null
$browser = $null
$passMessage = $null
try {
    [System.IO.Directory]::CreateDirectory($coordinationDirectory) | Out-Null
    foreach ($entry in $environmentValues.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable(
            [string]$entry.Key,
            $entry.Value,
            'Process'
        )
    }

    $jvm1 = Start-PackagedJvm $jvm1StdOut $jvm1StdErr
    $contentVersion = Wait-PackagedReadiness $jvm1 ''
    $quotedFrontend = '"' + $frontend + '"'
    $browser = Start-Process -FilePath $npmCommand.Source `
        -ArgumentList @(
            '--prefix', $quotedFrontend,
            'run', 'test:e2e', '--', '--workers=1'
        ) `
        -RedirectStandardOutput $browserStdOut `
        -RedirectStandardError $browserStdErr `
        -WindowStyle Hidden `
        -PassThru

    Wait-ClosedSignal $browser
    $jvm1Id = $jvm1.Id
    Stop-ControlledProcess $jvm1 'Packaged JVM #1'

    $jvm2 = Start-PackagedJvm $jvm2StdOut $jvm2StdErr
    if ($jvm2.Id -eq $jvm1Id) {
        throw 'Packaged JVM restart reused the first process identity.'
    }
    $null = Wait-PackagedReadiness $jvm2 $contentVersion

    if (Test-Path -LiteralPath $serverRestartedPath) {
        throw 'SERVER_RESTARTED already existed before JVM #2 readiness.'
    }
    [System.IO.File]::WriteAllText(
        $signalTemporaryPath,
        "SERVER_RESTARTED`n",
        [System.Text.UTF8Encoding]::new($false)
    )
    Move-Item -LiteralPath $signalTemporaryPath `
        -Destination $serverRestartedPath

    if (-not $browser.WaitForExit($BrowserTimeoutSeconds * 1000)) {
        throw 'Playwright did not exit within the controlled deadline.'
    }
    $browser.Refresh()
    if ($browser.ExitCode -ne 0) {
        throw "Playwright JVM restart browser lane failed with exit code $($browser.ExitCode)."
    }
    Assert-ClosedCoordinationDirectory

    Stop-ControlledProcess $jvm2 'Packaged JVM #2'
    $passMessage = ('PACKAGED_JVM_RESTART_BROWSER_PASS state=POSTGRESQL; ' +
        'jvmCount=2; browser=PASS; processIdentity=CHANGED; ' +
        'conversation=RECOVERED; replay=EXACT_PUBLIC_TURN')
}
finally {
    $cleanupFailure = $null
    try { Stop-ControlledBrowserTree $browser }
    catch { $cleanupFailure = $_ }
    try { Stop-ControlledProcess $jvm2 'Packaged JVM #2' }
    catch { if ($null -eq $cleanupFailure) { $cleanupFailure = $_ } }
    try { Stop-ControlledProcess $jvm1 'Packaged JVM #1' }
    catch { if ($null -eq $cleanupFailure) { $cleanupFailure = $_ } }
    try {
        Assert-LogPrivacy `
            @($jvm1StdOut, $jvm2StdOut) `
            @($ContextDatabasePassword, $CurrentTokenKey, $CurrentPayloadKey) `
            'Packaged JVM' `
            $true
        Assert-LogPrivacy `
            @($jvm1StdErr, $jvm2StdErr, $browserStdOut, $browserStdErr) `
            @($ContextDatabasePassword, $CurrentTokenKey, $CurrentPayloadKey) `
            'JVM restart browser gate' `
            $false
    }
    catch { if ($null -eq $cleanupFailure) { $cleanupFailure = $_ } }
    try { Restore-Environment $environmentSnapshot }
    catch { if ($null -eq $cleanupFailure) { $cleanupFailure = $_ } }
    try { Remove-ControlledArtifacts }
    catch { if ($null -eq $cleanupFailure) { $cleanupFailure = $_ } }
    foreach ($name in $environmentSnapshot.Keys) {
        $restoredValue = [string](Get-EnvironmentSnapshot $name)
        $expectedValue = [string]$environmentSnapshot[$name]
        if (-not [string]::Equals(
                $restoredValue,
                $expectedValue,
                [StringComparison]::Ordinal
            ) -and
                $null -eq $cleanupFailure) {
            $cleanupFailure = [InvalidOperationException]::new(
                "JVM restart browser gate failed to restore process environment variable $name."
            )
        }
    }
    if ($null -ne $cleanupFailure) {
        throw $cleanupFailure
    }
}

Write-Output $passMessage
