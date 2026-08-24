param(
    [string]$JarPath,
    [string]$JavaExecutable = 'java.exe',
    [string]$KeytoolExecutable = '',
    [string]$NpmExecutable = 'npm.cmd',
    [string]$ReleaseRoot = '',
    [string]$RetrievalProfile = '',
    [string]$ModelDirectory = '',
    [string]$ContextDatabaseUrl = $env:PORTFOLIO_CONTEXT_DATABASE_URL,
    [string]$ContextDatabaseUsername = $env:PORTFOLIO_CONTEXT_DATABASE_USERNAME,
    [string]$ContextDatabasePassword = $env:PORTFOLIO_CONTEXT_DATABASE_PASSWORD,
    [string]$CurrentTokenKeyId = $env:PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID,
    [string]$CurrentTokenKey = $env:PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY,
    [string]$CurrentPayloadKeyId = $env:PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID,
    [string]$CurrentPayloadKey = $env:PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY,
    [string]$ContextMode = $env:PORTFOLIO_CONVERSATION_CONTEXT_MODE,
    [switch]$RequireLiveProvider,
    [ValidateSet('DEFAULT', 'ADMISSION', 'BODY_STALL', 'DEPTH_TWO', 'CONTENT_ONLY', 'LIVE', 'PROJECT_DISCUSSION', 'PROJECT_DISCUSSION_EXPIRY', 'JVM_RESTART')]
    [string]$Lane = 'DEFAULT',
    [switch]$SkipPlaywright,
    [string]$PlaywrightScript = 'test:e2e',
    [string[]]$PlaywrightArguments = @(),
    [ValidateRange(1, 65535)]
    [int]$Port = 4173,
    [ValidateRange(1, 300)]
    [int]$ReadinessTimeoutSeconds = 60
)

$ErrorActionPreference = 'Stop'
$Lane = $Lane.Trim().ToUpperInvariant()
if ($RequireLiveProvider) {
    if ($Lane -notin @('DEFAULT', 'LIVE', 'PROJECT_DISCUSSION')) {
        throw 'RequireLiveProvider cannot be combined with a non-LIVE lane.'
    }
    if ($Lane -eq 'DEFAULT') { $Lane = 'LIVE' }
}
if ($Lane -eq 'PROJECT_DISCUSSION' -and -not $RequireLiveProvider) {
    throw 'PROJECT_DISCUSSION requires explicit RequireLiveProvider authorization.'
}
$ContextMode = if ([string]::IsNullOrWhiteSpace($ContextMode)) {
    if ($Lane -eq 'CONTENT_ONLY') { 'DISABLED' } else { 'POSTGRESQL' }
} else {
    $ContextMode.Trim().ToUpperInvariant()
}
if ($Lane -eq 'CONTENT_ONLY') {
    $ContextMode = 'DISABLED'
}
$root = Split-Path -Parent $PSScriptRoot
$jar = if ([string]::IsNullOrWhiteSpace($JarPath)) {
    Join-Path $root 'backend\target\portfolio-agent.jar'
}
else {
    [System.IO.Path]::GetFullPath($JarPath)
}
$baseUrl = "http://127.0.0.1:$Port"
$logCaptureId = [guid]::NewGuid().ToString('N')
$stdoutPath = Join-Path ([System.IO.Path]::GetTempPath()) `
    "portfolio-jar-e2e-$logCaptureId.stdout.log"
$stderrPath = Join-Path ([System.IO.Path]::GetTempPath()) `
    "portfolio-jar-e2e-$logCaptureId.stderr.log"
$privacySentinel = 'visitor-content-sentinel-must-not-leak'

if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw 'Packaged JAR is missing. Build the frontend and run Maven clean package first.'
}
$jar = (Resolve-Path -LiteralPath $jar).Path
if ($jar.Contains('"')) {
    throw 'Packaged JAR path contains an unsupported quote character.'
}
$jarSha256 = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
$jarBuiltAtUtc = (Get-Item -LiteralPath $jar).LastWriteTimeUtc.ToString('O')
$workspaceCommitSha = if (Test-Path -LiteralPath (Join-Path $root '.git')) {
    (& git -C $root rev-parse HEAD 2>$null | Out-String).Trim()
}
else {
    '0000000000000000000000000000000000000000'
}
if ($workspaceCommitSha -notmatch '^[a-f0-9]{40}$' -or
        $jarSha256 -notmatch '^[a-f0-9]{64}$') {
    throw 'Packaged build identity is invalid.'
}
Write-Output "JAR SHA-256: $jarSha256"
Write-Output "JAR builtAt UTC: $jarBuiltAtUtc"
Write-Output "Workspace commit (not JAR identity): $workspaceCommitSha"

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

function Assert-EnvironmentRestored([string]$Name, [hashtable]$Snapshot) {
    $current = Get-EnvironmentSnapshot $Name
    if (
        $current.Exists -ne $Snapshot.Exists -or
        (
            $current.Exists -and
            -not [System.StringComparer]::Ordinal.Equals(
                [string]$current.Value,
                [string]$Snapshot.Value
            )
        )
    ) {
        throw "Environment variable $Name was not restored."
    }
}

function Assert-CurrentContextKey([string]$KeyId, [string]$Key, [string]$Label) {
    if ([string]::IsNullOrWhiteSpace($KeyId) -or
            $KeyId -notmatch '^[A-Za-z0-9._-]{1,64}$' -or
            [string]::IsNullOrWhiteSpace($Key)) {
        throw "Packaged Context $Label key configuration is incomplete."
    }
    try {
        $decoded = [Convert]::FromBase64String($Key)
    }
    catch {
        throw "Packaged Context $Label key configuration is invalid."
    }
    if ($decoded.Length -ne 32) {
        throw "Packaged Context $Label key configuration is invalid."
    }
}

function Get-FreeLoopbackPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try {
        return ([Net.IPEndPoint]$listener.LocalEndpoint).Port
    }
    finally {
        $listener.Stop()
    }
}

function Invoke-FixtureKeytool(
    [string]$Executable,
    [string[]]$Arguments,
    [string]$FailureMessage
) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $Executable @Arguments 2>&1 | Out-Null
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previous
    }
    if ($exitCode -ne 0) { throw $FailureMessage }
}

function Assert-PackagedLogBoundary(
    [string]$stdoutPath,
    [string]$stderrPath,
    [string]$privacySentinel
) {
    $capturedStdout = Get-Content -LiteralPath $stdoutPath -Raw
    $capturedStderr = Get-Content -LiteralPath $stderrPath -Raw
    if (($capturedStdout + $capturedStderr) -match
            [regex]::Escape($privacySentinel)) {
        throw 'Packaged application logs leaked the visitor-content sentinel.'
    }
    $applicationStdoutLines = @(
        $capturedStdout -split '\r?\n' |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
    if ($applicationStdoutLines.Count -eq 0) {
        throw 'Packaged application stdout did not contain structured JSON logs.'
    }
    foreach ($line in $applicationStdoutLines) {
        try {
            $null = $line | ConvertFrom-Json
        }
        catch {
            throw 'Packaged application stdout contained a non-JSON application stdout line.'
        }
    }
    Write-Output 'Packaged structured stdout privacy smoke passed.'
}

$environment = @{
    PORTFOLIO_CONVERSATION_CONTEXT_MODE = Get-EnvironmentSnapshot `
        'PORTFOLIO_CONVERSATION_CONTEXT_MODE'
    PLAYWRIGHT_EXTERNAL_SERVER = Get-EnvironmentSnapshot 'PLAYWRIGHT_EXTERNAL_SERVER'
    PLAYWRIGHT_REAL_API = Get-EnvironmentSnapshot 'PLAYWRIGHT_REAL_API'
    PLAYWRIGHT_BASE_URL = Get-EnvironmentSnapshot 'PLAYWRIGHT_BASE_URL'
    PLAYWRIGHT_REAL_RETRIEVAL = Get-EnvironmentSnapshot 'PLAYWRIGHT_REAL_RETRIEVAL'
    PLAYWRIGHT_ADMISSION = Get-EnvironmentSnapshot 'PLAYWRIGHT_ADMISSION'
    PLAYWRIGHT_CONTENT_ONLY = Get-EnvironmentSnapshot 'PLAYWRIGHT_CONTENT_ONLY'
    PLAYWRIGHT_SLOW_PROVIDER = Get-EnvironmentSnapshot 'PLAYWRIGHT_SLOW_PROVIDER'
    PLAYWRIGHT_DEPTH_TWO = Get-EnvironmentSnapshot 'PLAYWRIGHT_DEPTH_TWO'
    PLAYWRIGHT_PROJECT_DISCUSSION = Get-EnvironmentSnapshot `
        'PLAYWRIGHT_PROJECT_DISCUSSION'
    PLAYWRIGHT_PROJECT_DISCUSSION_EXPIRY = Get-EnvironmentSnapshot `
        'PLAYWRIGHT_PROJECT_DISCUSSION_EXPIRY'
    PLAYWRIGHT_PROVIDER_STALL_COORDINATION_URL = Get-EnvironmentSnapshot `
        'PLAYWRIGHT_PROVIDER_STALL_COORDINATION_URL'
    PORTFOLIO_CONTEXT_DATABASE_URL = Get-EnvironmentSnapshot `
        'PORTFOLIO_CONTEXT_DATABASE_URL'
    PORTFOLIO_CONTEXT_DATABASE_USERNAME = Get-EnvironmentSnapshot `
        'PORTFOLIO_CONTEXT_DATABASE_USERNAME'
    PORTFOLIO_CONTEXT_DATABASE_PASSWORD = Get-EnvironmentSnapshot `
        'PORTFOLIO_CONTEXT_DATABASE_PASSWORD'
    PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID = Get-EnvironmentSnapshot `
        'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID'
    PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY = Get-EnvironmentSnapshot `
        'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY'
    PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID = Get-EnvironmentSnapshot `
        'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID'
    PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY = Get-EnvironmentSnapshot `
        'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY'
    PORTFOLIO_MODEL_PROVIDER = Get-EnvironmentSnapshot 'PORTFOLIO_MODEL_PROVIDER'
    PORTFOLIO_AGENT_DEEPSEEK_API_KEY = Get-EnvironmentSnapshot `
        'PORTFOLIO_AGENT_DEEPSEEK_API_KEY'
    PORTFOLIO_AGENT_GLM_API_KEY = Get-EnvironmentSnapshot 'PORTFOLIO_AGENT_GLM_API_KEY'
}

function Assert-EarlyRunnerEnvironmentRestored {
    foreach ($name in @(
        'PORTFOLIO_CONVERSATION_CONTEXT_MODE',
        'PORTFOLIO_CONTEXT_DATABASE_URL',
        'PORTFOLIO_CONTEXT_DATABASE_USERNAME',
        'PORTFOLIO_CONTEXT_DATABASE_PASSWORD',
        'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID',
        'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY',
        'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID',
        'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY',
        'PORTFOLIO_MODEL_PROVIDER',
        'PORTFOLIO_AGENT_DEEPSEEK_API_KEY',
        'PORTFOLIO_AGENT_GLM_API_KEY',
        'PLAYWRIGHT_PROVIDER_STALL_COORDINATION_URL'
    )) {
        Assert-EnvironmentRestored -Name $name -Snapshot ($environment[$name])
    }
    Write-Output 'BODY_STALL early-failure environment restored.'
}

if ($ContextMode -notin @('POSTGRESQL', 'IN_MEMORY', 'DISABLED')) {
    throw 'Packaged Context mode is invalid.'
}
if ($ContextMode -eq 'POSTGRESQL') {
    if ([string]::IsNullOrWhiteSpace($ContextDatabaseUrl) -or
            $ContextDatabaseUrl -notmatch '^jdbc:postgresql://') {
        throw 'Packaged Context database URL is missing or invalid.'
    }
    if ([string]::IsNullOrWhiteSpace($ContextDatabaseUsername) -or
            [string]::IsNullOrWhiteSpace($ContextDatabasePassword)) {
        throw 'Packaged Context database credentials are incomplete.'
    }
    Assert-CurrentContextKey $CurrentTokenKeyId $CurrentTokenKey 'token'
    Assert-CurrentContextKey $CurrentPayloadKeyId $CurrentPayloadKey 'payload'
}

foreach ($entry in @{
    PORTFOLIO_CONVERSATION_CONTEXT_MODE = $ContextMode
    PORTFOLIO_CONTEXT_DATABASE_URL = $ContextDatabaseUrl
    PORTFOLIO_CONTEXT_DATABASE_USERNAME = $ContextDatabaseUsername
    PORTFOLIO_CONTEXT_DATABASE_PASSWORD = $ContextDatabasePassword
    PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID = $CurrentTokenKeyId
    PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY = $CurrentTokenKey
    PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID = $CurrentPayloadKeyId
    PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY = $CurrentPayloadKey
}.GetEnumerator()) {
    Set-Item -LiteralPath "Env:$($entry.Key)" -Value ([string]$entry.Value)
}

$bodyStallAuthorizationValue = 'body-stall-fixture-key'
if ($Lane -eq 'BODY_STALL') {
    # BODY_STALL 只允许固定的假凭据进入本地 fixture，先清除可能继承的真实 key。
    $env:PORTFOLIO_MODEL_PROVIDER = 'DEEPSEEK_V4_FLASH'
    $env:PORTFOLIO_AGENT_DEEPSEEK_API_KEY = $bodyStallAuthorizationValue
    Remove-Item -LiteralPath 'Env:PORTFOLIO_AGENT_GLM_API_KEY' -ErrorAction SilentlyContinue
}

$bodyStallFixtureRoot = $null
$bodyStallFixtureProcess = $null
$javaVmArguments = @()

function Remove-BodyStallFixture {
    if ($null -ne $script:bodyStallFixtureProcess) {
        $script:bodyStallFixtureProcess.Refresh()
        if (-not $script:bodyStallFixtureProcess.HasExited) {
            Stop-Process -Id $script:bodyStallFixtureProcess.Id -Force
            $script:bodyStallFixtureProcess.WaitForExit(5000) | Out-Null
        }
    }
    if (-not [string]::IsNullOrWhiteSpace([string]$script:bodyStallFixtureRoot) -and
            (Test-Path -LiteralPath $script:bodyStallFixtureRoot)) {
        $resolved = (Resolve-Path -LiteralPath $script:bodyStallFixtureRoot).Path
        if (-not ([IO.Path]::GetFileName($resolved)).StartsWith(
                'portfolio-body-stall-', [StringComparison]::Ordinal)) {
            throw "Refusing to remove unverified BODY_STALL fixture path: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}

function Complete-BodyStallEarlyFailure {
    try {
        Remove-BodyStallFixture
    }
    finally {
        # Environment restoration must run even when fixture cleanup itself fails.
        foreach ($name in @(
            'PORTFOLIO_CONVERSATION_CONTEXT_MODE',
            'PORTFOLIO_CONTEXT_DATABASE_URL',
            'PORTFOLIO_CONTEXT_DATABASE_USERNAME',
            'PORTFOLIO_CONTEXT_DATABASE_PASSWORD',
            'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID',
            'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY',
            'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID',
            'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY',
            'PORTFOLIO_MODEL_PROVIDER',
            'PORTFOLIO_AGENT_DEEPSEEK_API_KEY',
            'PORTFOLIO_AGENT_GLM_API_KEY',
            'PLAYWRIGHT_PROVIDER_STALL_COORDINATION_URL'
        )) {
            $snapshot = $environment[$name]
            $value = if ($snapshot.Exists) { [string]$snapshot.Value } else { $null }
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
        Assert-EarlyRunnerEnvironmentRestored
    }
}

if ($Lane -eq 'BODY_STALL') {
    try {
        $bodyStallFixtureRoot = Join-Path ([IO.Path]::GetTempPath()) `
            ('portfolio-body-stall-' + [guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $bodyStallFixtureRoot | Out-Null
        $certificatePath = Join-Path $bodyStallFixtureRoot 'provider.p12'
        $certificateExportPath = Join-Path $bodyStallFixtureRoot 'provider.cer'
        $trustStorePath = Join-Path $bodyStallFixtureRoot 'truststore.p12'
        $hostsPath = Join-Path $bodyStallFixtureRoot 'hosts.txt'
        $activeSignalPath = Join-Path $bodyStallFixtureRoot 'active.signal'
        $closedSignalPath = Join-Path $bodyStallFixtureRoot 'closed.signal'
        $fixtureStdout = Join-Path $bodyStallFixtureRoot 'fixture.stdout.log'
        $fixtureStderr = Join-Path $bodyStallFixtureRoot 'fixture.stderr.log'
        $certificatePassword = [guid]::NewGuid().ToString('N')
        $coordinationPort = Get-FreeLoopbackPort

        $keytool = if ([string]::IsNullOrWhiteSpace($KeytoolExecutable)) {
            $resolvedJava = (Get-Command $JavaExecutable -ErrorAction Stop).Source
            Join-Path (Split-Path -Parent $resolvedJava) 'keytool.exe'
        } else {
            [IO.Path]::GetFullPath($KeytoolExecutable)
        }
        if (-not (Test-Path -LiteralPath $keytool -PathType Leaf)) {
            throw 'BODY_STALL requires keytool from the selected JDK.'
        }
        Invoke-FixtureKeytool $keytool @(
            '-genkeypair', '-noprompt', '-alias', 'body-stall-provider',
            '-keyalg', 'RSA', '-keysize', '2048', '-validity', '1',
            '-storetype', 'PKCS12', '-keystore', $certificatePath,
            '-storepass', $certificatePassword, '-keypass', $certificatePassword,
            '-dname', 'CN=api.deepseek.com', '-ext', 'SAN=dns:api.deepseek.com'
        ) 'BODY_STALL certificate generation failed.'
        Invoke-FixtureKeytool $keytool @(
            '-exportcert', '-noprompt', '-alias', 'body-stall-provider',
            '-keystore', $certificatePath, '-storepass', $certificatePassword,
            '-file', $certificateExportPath
        ) 'BODY_STALL certificate export failed.'
        Invoke-FixtureKeytool $keytool @(
            '-importcert', '-noprompt', '-alias', 'body-stall-provider',
            '-file', $certificateExportPath, '-storetype', 'PKCS12',
            '-keystore', $trustStorePath, '-storepass', $certificatePassword
        ) 'BODY_STALL truststore generation failed.'
        [IO.File]::WriteAllText(
            $hostsPath, "127.0.0.1 api.deepseek.com`n", [Text.UTF8Encoding]::new($false))

        $fixtureScript = Join-Path $root `
            'scripts\test-fixtures\start-provider-body-stall-https.ps1'
        $bodyStallFixtureProcess = Start-Process -FilePath 'powershell.exe' `
            -ArgumentList @(
                '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
                ('"' + $fixtureScript + '"'),
                '-CertificatePath', ('"' + $certificatePath + '"'),
                '-CertificatePassword', $certificatePassword,
                '-CoordinationPort', [string]$coordinationPort,
                '-ActiveSignalPath', ('"' + $activeSignalPath + '"'),
                '-ClosedSignalPath', ('"' + $closedSignalPath + '"')
            ) `
            -RedirectStandardOutput $fixtureStdout `
            -RedirectStandardError $fixtureStderr `
            -PassThru -WindowStyle Hidden
        $coordinationUrl = "http://127.0.0.1:$coordinationPort/status"
        $fixtureReady = $false
        for ($attempt = 0; $attempt -lt 50; $attempt++) {
            $bodyStallFixtureProcess.Refresh()
            if ($bodyStallFixtureProcess.HasExited) {
                throw 'BODY_STALL HTTPS fixture exited before readiness.'
            }
            try {
                $fixtureState = Invoke-RestMethod -UseBasicParsing `
                    -Uri $coordinationUrl -TimeoutSec 1
                if ($fixtureState.ready -eq $true -and
                        [int]$fixtureState.providerRequestCount -eq 0) {
                    $fixtureReady = $true
                    break
                }
            }
            catch { }
            Start-Sleep -Milliseconds 100
        }
        if (-not $fixtureReady) { throw 'BODY_STALL HTTPS fixture readiness timed out.' }
        $env:PLAYWRIGHT_PROVIDER_STALL_COORDINATION_URL = $coordinationUrl
        $javaVmArguments = @(
            ('"-Djdk.net.hosts.file=' + $hostsPath + '"'),
            ('"-Djavax.net.ssl.trustStore=' + $trustStorePath + '"'),
            '-Djavax.net.ssl.trustStoreType=PKCS12',
            ("-Djavax.net.ssl.trustStorePassword=$certificatePassword")
        )
    }
    catch {
        Complete-BodyStallEarlyFailure
        throw
    }
}

$quotedJar = '"' + $jar + '"'
$requestsPerMinute = if ($Lane -eq 'ADMISSION') { 2 } else { 1000 }
$applicationArguments = @(
    '-jar',
    $quotedJar,
    "--server.port=$Port",
    '--spring.profiles.active=prod',
    '--spring.main.banner-mode=off',
    "--portfolio.conversation-context.mode=$ContextMode",
    '--portfolio.diagnostics.frontend-ingest-enabled=true',
    "--portfolio.agent-runtime.requests-per-minute=$requestsPerMinute",
    '--portfolio.agent-runtime.max-concurrent-per-source=1000',
    '--portfolio.agent-runtime.max-active-turns=1000'
)
if ($Lane -notin @('LIVE', 'PROJECT_DISCUSSION', 'BODY_STALL')) {
    $applicationArguments += '--portfolio.model-expression.enabled=false'
    $applicationArguments += '--portfolio.conversational-agent.enabled=false'
}
if ($Lane -eq 'PROJECT_DISCUSSION_EXPIRY') {
    $applicationArguments += '--portfolio.conversation-context.discussion-ttl=3s'
}
if ($Lane -eq 'BODY_STALL') {
    $applicationArguments += @(
        '--portfolio.conversational-model.enabled=true',
        '--portfolio.conversational-model.external-data-policy-approved=true',
        '--portfolio.conversational-model.provider=DEEPSEEK_V4_FLASH',
        "--portfolio.conversational-model.deepseek-api-key=$bodyStallAuthorizationValue",
        '--portfolio.conversational-agent.enabled=true',
        '--portfolio.conversational-agent.visitor-data-policy-approved=true',
        '--portfolio.model-operations.turn-interpretation.mode=ENABLED',
        '--portfolio.model-operations.turn-interpretation.provider-ref=DEEPSEEK_V4_FLASH',
        '--portfolio.model-operations.turn-interpretation.schema-version=goal.proposal.v4',
        '--portfolio.model-operations.general-knowledge.mode=DISABLED'
    )
}
if (-not [string]::IsNullOrWhiteSpace($ReleaseRoot)) {
    $resolvedReleaseRoot = (Resolve-Path -LiteralPath $ReleaseRoot).Path
    $applicationArguments += '"--portfolio.content.release-root=' + $resolvedReleaseRoot + '"'
}
if (-not [string]::IsNullOrWhiteSpace($RetrievalProfile)) {
    if ($RetrievalProfile -notin @('DISABLED', 'KEYWORD_ONLY', 'HYBRID')) {
        throw 'RetrievalProfile is invalid.'
    }
    $applicationArguments += "--portfolio.retrieval.profile=$RetrievalProfile"
}
if (-not [string]::IsNullOrWhiteSpace($ModelDirectory)) {
    $resolvedModelDirectory = (Resolve-Path -LiteralPath $ModelDirectory).Path
    $applicationArguments += '"--portfolio.retrieval.model-directory=' `
        + $resolvedModelDirectory + '"'
}
try {
    $process = Start-Process -FilePath $JavaExecutable `
        -ArgumentList ($javaVmArguments + $applicationArguments) `
        -RedirectStandardOutput $stdoutPath `
        -RedirectStandardError $stderrPath `
        -PassThru -WindowStyle Hidden
}
catch {
    Complete-BodyStallEarlyFailure
    throw
}

Write-Output "Started packaged application process $($process.Id)."
if ($Lane -notin @('LIVE', 'PROJECT_DISCUSSION', 'BODY_STALL')) {
    Write-Output 'Provider calls disabled for deterministic smoke.'
}

$playwrightExitCode = 0
try {
    $ready = $false
    $readinessDeadline = [DateTimeOffset]::UtcNow.AddSeconds(
        $ReadinessTimeoutSeconds)
    for ($attempt = 0; [DateTimeOffset]::UtcNow -lt $readinessDeadline; $attempt++) {
        $process.Refresh()
        if ($process.HasExited) {
            throw "Packaged application exited before readiness with exit code $($process.ExitCode)."
        }

        $response = $null
        try {
            $response = Invoke-WebRequest -UseBasicParsing `
                -TimeoutSec ([Math]::Max(1, [Math]::Min(
                        2, $ReadinessTimeoutSeconds))) `
                "$baseUrl/api/portfolio"
        }
        catch {
            $process.Refresh()
            if ($process.HasExited) {
                throw "Packaged application exited before readiness with exit code $($process.ExitCode)."
            }
        }

        if ($null -ne $response -and $response.StatusCode -eq 200) {
            $process.Refresh()
            if ($process.HasExited) {
                throw "Packaged application exited during readiness with exit code $($process.ExitCode)."
            }

            $contentType = [string]$response.Headers['Content-Type']
            if ($contentType -notmatch '^application/json(?:;|$)') {
                throw "Readiness endpoint returned unexpected Content-Type '$contentType'."
            }

            try {
                $publicContent = $response.Content | ConvertFrom-Json
            }
            catch {
                throw 'Readiness endpoint did not return valid public-content JSON.'
            }
            $requiredFields = @(
                'contentVersion', 'owner', 'projects', 'evidence', 'timeline', 'agentAvailability'
            )
            foreach ($field in $requiredFields) {
                $property = $publicContent.PSObject.Properties[$field]
                if ($null -eq $property -or $null -eq $property.Value) {
                    throw "Readiness public-content JSON is missing required field '$field'."
                }
            }
            if ([string]::IsNullOrWhiteSpace([string]$publicContent.contentVersion)) {
                throw "Readiness public-content JSON has a blank contentVersion."
            }
            $expectedAvailability = if ($Lane -eq 'CONTENT_ONLY') {
                'UNAVAILABLE'
            } else {
                'AVAILABLE'
            }
            if ([string]$publicContent.agentAvailability.status -cne $expectedAvailability) {
                throw "Readiness returned unexpected Agent availability for lane $Lane."
            }

            $ownedListeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen `
                -ErrorAction SilentlyContinue | Where-Object { $_.OwningProcess -eq $process.Id })
            if ($ownedListeners.Count -eq 0) {
                throw "Port $Port is not owned by packaged application process $($process.Id)."
            }

            $ready = $true
            break
        }

        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) {
        throw 'Packaged application did not become ready.'
    }

    Write-Output ("Runtime identity: pid={0} port={1} contentVersion={2}" -f `
            $process.Id, $Port, [string]$publicContent.contentVersion)
    Write-Output "Packaged application process $($process.Id) owns port $Port; readiness returned validated public-content JSON."

    $correlationResponse = Invoke-WebRequest -UseBasicParsing `
        -TimeoutSec $ReadinessTimeoutSeconds `
        "$baseUrl/api/portfolio"
    if (
        [string]::IsNullOrWhiteSpace(
            [string]$correlationResponse.Headers['X-Request-Id']
        ) -or
        [string]::IsNullOrWhiteSpace(
            [string]$correlationResponse.Headers['X-Trace-Id']
        )
    ) {
        throw 'Packaged successful endpoint did not return request and trace correlation.'
    }
    Write-Output 'Packaged request correlation smoke passed.'

    $diagnosticBatch = @{
        events = @(
            @{
                schemaVersion = 1
                eventName = 'frontend.agent.request.failed'
                occurredAt = '2026-07-29T00:00:00.000Z'
                clientSessionId = '22222222-2222-4222-8222-222222222222'
                clientRequestId = '33333333-3333-4333-8333-333333333333'
                errorCode = 'CLIENT_NETWORK_ERROR'
                errorKind = 'NETWORK'
                durationBucket = 'LT_1000_MS'
            }
        )
    } | ConvertTo-Json -Depth 5 -Compress
    $diagnosticResponse = Invoke-WebRequest -UseBasicParsing `
        -Method Post `
        -Uri "$baseUrl/api/client-diagnostics" `
        -TimeoutSec $ReadinessTimeoutSeconds `
        -ContentType 'application/json; charset=utf-8' `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($diagnosticBatch))
    if ($diagnosticResponse.StatusCode -ne 202) {
        throw "Valid diagnostic batch returned $($diagnosticResponse.StatusCode), expected 202."
    }
    Write-Output 'Packaged client diagnostics acceptance smoke passed.'

    $unknownFieldBatch = @{
        events = @(
            @{
                schemaVersion = 1
                eventName = 'frontend.agent.request.failed'
                occurredAt = '2026-07-29T00:00:00.000Z'
                clientSessionId = '22222222-2222-4222-8222-222222222222'
                clientRequestId = '33333333-3333-4333-8333-333333333333'
                question = 'diagnostic-unknown-field-must-be-rejected'
            }
        )
    } | ConvertTo-Json -Depth 5 -Compress
    try {
        $unknownFieldStatus = (Invoke-WebRequest -UseBasicParsing `
            -Method Post `
            -Uri "$baseUrl/api/client-diagnostics" `
            -TimeoutSec $ReadinessTimeoutSeconds `
            -ContentType 'application/json; charset=utf-8' `
            -Body ([System.Text.Encoding]::UTF8.GetBytes($unknownFieldBatch))).StatusCode
    }
    catch {
        $unknownFieldStatus = [int]$_.Exception.Response.StatusCode
    }
    if ($unknownFieldStatus -ne 400) {
        throw "Unknown diagnostic field returned $unknownFieldStatus, expected 400."
    }
    Write-Output 'Packaged client diagnostics unknown-field rejection smoke passed.'

    $oversizedBody = '{"events":[],"padding":"' + ('x' * 17000) + '"}'
    try {
        $oversizedStatus = (Invoke-WebRequest -UseBasicParsing `
            -Method Post `
            -Uri "$baseUrl/api/client-diagnostics" `
            -TimeoutSec $ReadinessTimeoutSeconds `
            -ContentType 'application/json; charset=utf-8' `
            -Body ([System.Text.Encoding]::UTF8.GetBytes($oversizedBody))).StatusCode
    }
    catch {
        $oversizedStatus = [int]$_.Exception.Response.StatusCode
    }
    if ($oversizedStatus -ne 413) {
        throw "Oversized diagnostic body returned $oversizedStatus, expected 413."
    }
    Write-Output 'Packaged client diagnostics body-limit smoke passed.'

    $caseResponse = @($publicContent.cases) | Where-Object {
        [string]$_.slug -eq 'multilingual-image-preservation'
    } | Select-Object -First 1
    if ($null -eq $caseResponse) {
        throw 'Packaged portfolio snapshot omitted the expected Case.'
    }
    if (@($caseResponse.evidence).Count -eq 0) {
        throw 'Packaged portfolio snapshot Case returned no public evidence.'
    }
    Write-Output 'Packaged portfolio snapshot Case smoke passed.'

    if ($Lane -notin @('CONTENT_ONLY', 'BODY_STALL', 'PROJECT_DISCUSSION')) {
    $smokePreset = @($publicContent.questionPresets)[0]
    if ($null -eq $smokePreset -or
            [string]::IsNullOrWhiteSpace([string]$smokePreset.id) -or
            [string]::IsNullOrWhiteSpace([string]$smokePreset.contractVersion)) {
        throw 'Packaged public-content returned no active preset for Agent smoke.'
    }
    $caseAgentRequestId = [guid]::NewGuid().ToString()
    $caseAgentRequest = @{
        requestId = $caseAgentRequestId
        command = @{
            kind = 'ASK'
            input = @{
                kind = 'PRESET'
                presetId = [string]$smokePreset.id
                presetRevision = [string]$smokePreset.contractVersion
            }
        }
        surfaceContext = @{
            audienceRole = 'INTERVIEWER'
            requestSource = 'AGENT_PAGE'
        }
        conversationWindow = @(
            @{ role = 'USER'; content = $privacySentinel }
        )
    } | ConvertTo-Json -Depth 8 -Compress
    $caseAgentResponse = Invoke-RestMethod -UseBasicParsing `
        -Method Post `
        -Uri "$baseUrl/api/agent/turns" `
        -TimeoutSec $ReadinessTimeoutSeconds `
        -ContentType 'application/json; charset=utf-8' `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($caseAgentRequest))
    if ([string]$caseAgentResponse.requestId -ne $caseAgentRequestId `
            -or [string]$caseAgentResponse.kind -ne 'ANSWER' `
            -or [string]$caseAgentResponse.answer.contentReleaseId `
                -ne [string]$publicContent.contentVersion) {
        throw ("Packaged Case Agent returned an invalid final projection: kind={0}; contentReleaseId={1}." -f `
                [string]$caseAgentResponse.kind,
                [string]$caseAgentResponse.answer.contentReleaseId)
    }
    $caseAgentSections = @(
        @($caseAgentResponse.answer.goalResults) |
            ForEach-Object { @($_.presentation.sections) }
    )
    if ($caseAgentSections.Count -eq 0) {
        throw 'Packaged Case Agent returned no sections in the final PublicAgentTurn projection.'
    }
    $serializedCaseAgentResponse =
            $caseAgentResponse | ConvertTo-Json -Depth 12 -Compress
    if ($serializedCaseAgentResponse -match [regex]::Escape($privacySentinel)) {
        throw 'Packaged Case Agent response leaked the visitor-content sentinel.'
    }
    Write-Output 'Packaged final Agent resource smoke passed.'

    if ($Lane -in @('DEFAULT', 'JVM_RESTART')) {
        $scenarioJson = & powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File (Join-Path $root 'scripts\run-agent-scenario-runtime.ps1') `
            -BackendBaseUrl $baseUrl `
            -TimeoutSeconds $ReadinessTimeoutSeconds
        if ($LASTEXITCODE -ne 0) {
            throw 'Agent scenario runtime reporter failed to produce evidence.'
        }
        try {
            $scenarioReport = ($scenarioJson -join [Environment]::NewLine) |
                ConvertFrom-Json
        }
        catch {
            throw 'Agent scenario runtime reporter returned invalid JSON.'
        }
        if ([int]$scenarioReport.total -ne 35 -or
                @($scenarioReport.results).Count -ne 35) {
            throw 'Agent scenario runtime reporter did not execute every registered case.'
        }
        $scenarioSummaryFormat = `
            'AGENT_SCENARIO_RUNTIME_BASELINE overall={0}; total={1}; ' +
            'pass={2}; inProgress={3}; failed={4}; executionMode={5}'
        Write-Output ($scenarioSummaryFormat -f `
            [string]$scenarioReport.overall,
            [int]$scenarioReport.total,
            [int]$scenarioReport.passed,
            [int]$scenarioReport.inProgress,
            [int]$scenarioReport.failed,
            [string]$scenarioReport.executionMode)
    }

    if ($Lane -eq 'JVM_RESTART') {
        if ($ContextMode -ne 'POSTGRESQL') {
            throw 'JVM_RESTART requires PostgreSQL context mode.'
        }
        $resumeToken = [string]$caseAgentResponse.conversation.resumeToken
        $conversationId = [string]$caseAgentResponse.conversation.conversationId
        if ([string]::IsNullOrWhiteSpace($resumeToken) -or
                [string]::IsNullOrWhiteSpace($conversationId)) {
            throw 'JVM_RESTART initial settlement omitted conversation authority.'
        }
        $expectedAnswer = $caseAgentResponse.answer |
            ConvertTo-Json -Depth 20 -Compress

        $process.Refresh()
        if (-not $process.HasExited) {
            Stop-Process -Id $process.Id -Force
            if (-not $process.WaitForExit(10000)) {
                throw 'JVM_RESTART first packaged process did not stop.'
            }
        }
        Assert-PackagedLogBoundary -stdoutPath $stdoutPath `
            -stderrPath $stderrPath -privacySentinel $privacySentinel
        foreach ($firstLogPath in @($stdoutPath, $stderrPath)) {
            if (Test-Path -LiteralPath $firstLogPath -PathType Leaf) {
                Remove-Item -LiteralPath $firstLogPath -Force
            }
        }
        $restartCaptureId = [guid]::NewGuid().ToString('N')
        $stdoutPath = Join-Path ([System.IO.Path]::GetTempPath()) `
            "portfolio-jar-e2e-restart-$restartCaptureId.stdout.log"
        $stderrPath = Join-Path ([System.IO.Path]::GetTempPath()) `
            "portfolio-jar-e2e-restart-$restartCaptureId.stderr.log"
        $process = Start-Process -FilePath $JavaExecutable `
            -ArgumentList ($javaVmArguments + $applicationArguments) `
            -RedirectStandardOutput $stdoutPath `
            -RedirectStandardError $stderrPath `
            -PassThru -WindowStyle Hidden

        $restartReady = $false
        $restartDeadline = [DateTimeOffset]::UtcNow.AddSeconds(
            $ReadinessTimeoutSeconds)
        while ([DateTimeOffset]::UtcNow -lt $restartDeadline) {
            $process.Refresh()
            if ($process.HasExited) {
                throw "JVM_RESTART second process exited with $($process.ExitCode)."
            }
            try {
                $restartPortfolio = Invoke-RestMethod -UseBasicParsing `
                    -Uri "$baseUrl/api/portfolio" -TimeoutSec 2
                if ([string]$restartPortfolio.contentVersion -eq
                        [string]$publicContent.contentVersion) {
                    $restartReady = $true
                    break
                }
            }
            catch { }
            Start-Sleep -Milliseconds 100
        }
        if (-not $restartReady) {
            throw 'JVM_RESTART second process readiness timed out.'
        }

        $restartHeaders = @{ Authorization = "Bearer $resumeToken" }
        $currentAfterRestart = Invoke-RestMethod -UseBasicParsing `
            -Method Get -Uri "$baseUrl/api/agent/conversations/current" `
            -Headers $restartHeaders -TimeoutSec $ReadinessTimeoutSeconds
        if ([string]$currentAfterRestart.conversationId -ne $conversationId) {
            throw 'JVM_RESTART did not recover the persisted conversation.'
        }
        $replayAfterRestart = Invoke-RestMethod -UseBasicParsing `
            -Method Post -Uri "$baseUrl/api/agent/turns" `
            -Headers $restartHeaders `
            -TimeoutSec $ReadinessTimeoutSeconds `
            -ContentType 'application/json; charset=utf-8' `
            -Body ([System.Text.Encoding]::UTF8.GetBytes($caseAgentRequest))
        $actualAnswer = $replayAfterRestart.answer |
            ConvertTo-Json -Depth 20 -Compress
        if ([string]$replayAfterRestart.kind -ne 'ANSWER' -or
                [string]$replayAfterRestart.requestId -ne $caseAgentRequestId -or
                [string]$replayAfterRestart.conversation.conversationId -ne $conversationId -or
                $actualAnswer -cne $expectedAnswer) {
            throw 'JVM_RESTART did not replay the exact persisted Portfolio terminal.'
        }
        Write-Output ('PACKAGED_JVM_RESTART_API_PASS ' +
            'state=POSTGRESQL; processIdentity=CHANGED; conversation=RECOVERED; ' +
            'replay=EXACT_PUBLIC_TURN')
    }

    if ($SkipPlaywright -and $Lane -ne 'JVM_RESTART') {
        function Invoke-AgentClosureRequest([string]$Question) {
            $body = @{
                requestId = [guid]::NewGuid().ToString()
                command = @{
                    kind = 'ASK'
                    input = @{ kind = 'FREE_TEXT'; text = $Question }
                }
                surfaceContext = @{
                    audienceRole = 'INTERVIEWER'
                    requestSource = 'AGENT_PAGE'
                }
                conversationWindow = @()
            } | ConvertTo-Json -Depth 8 -Compress
            return Invoke-RestMethod -UseBasicParsing -Method Post `
                -Uri "$baseUrl/api/agent/turns" `
                -TimeoutSec $ReadinessTimeoutSeconds `
                -ContentType 'application/json; charset=utf-8' `
                -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
        }

        $noiseResponse = Invoke-AgentClosureRequest '1'
        if ([string]$noiseResponse.kind -notin @('BOUNDARY', 'CAPABILITY_UNAVAILABLE')) {
            throw "Packaged noise closure returned unexpected PublicAgentTurn kind '$($noiseResponse.kind)'."
        }
        $recommendationQuestion = [Text.Encoding]::UTF8.GetString(
            [Convert]::FromBase64String('5o6o6I2QIDMg5Liq6aG555uu'))
        $recommendationResponse = Invoke-AgentClosureRequest $recommendationQuestion
        if ($RequireLiveProvider) {
            $recommendation = @($recommendationResponse.answer.goalResults |
                Where-Object { $_.presentation.kind -eq 'RECOMMENDATION' } |
                Select-Object -First 1).presentation
            if ($null -eq $recommendation `
                    -or [int]$recommendation.requestedSize -ne 3 `
                    -or [int]$recommendation.actualSize -gt 3) {
                throw ("Packaged recommendation closure returned invalid final projection: kind={0} code={1} requestedSize={2} actualSize={3}." -f `
                        [string]$recommendationResponse.kind,
                        [string]$recommendationResponse.code,
                        [string]$recommendation.requestedSize,
                        [string]$recommendation.actualSize)
            }
            if ([int]$recommendation.actualSize -lt 3 `
                    -and @($recommendation.incompleteReasons).Count -eq 0) {
                throw 'Packaged partial recommendation omitted incompleteReasons.'
            }
            Write-Output ("Agent backend closure summary: noiseKind={0} recommendationKind={1} requestedSize={2} actualSize={3}" -f `
                    [string]$noiseResponse.kind,
                    [string]$recommendationResponse.kind,
                    [int]$recommendation.requestedSize,
                    [int]$recommendation.actualSize)
        }
        else {
            if ([string]$recommendationResponse.kind -ne 'CAPABILITY_UNAVAILABLE' `
                    -or [string]$recommendationResponse.code -ne 'SEMANTIC_ROUTING_UNAVAILABLE') {
                throw ("Provider-disabled recommendation did not fail closed: kind={0} code={1}." -f `
                        [string]$recommendationResponse.kind,
                        [string]$recommendationResponse.code)
            }
            Write-Output ("Agent backend closure summary: noiseKind={0} recommendationKind={1} code={2} provider=DISABLED" -f `
                    [string]$noiseResponse.kind,
                    [string]$recommendationResponse.kind,
                    [string]$recommendationResponse.code)
        }
        Write-Output 'Agent backend closure smoke passed.'
    }

    }
    elseif ($Lane -eq 'CONTENT_ONLY') {
        $disabledRequest = @{
            requestId = [guid]::NewGuid().ToString()
            command = @{ kind = 'ASK'; input = @{ kind = 'FREE_TEXT'; text = 'hello' } }
            conversationWindow = @()
        } | ConvertTo-Json -Depth 6 -Compress
        try {
            $disabledHttp = Invoke-WebRequest -UseBasicParsing -Method Post `
                -Uri "$baseUrl/api/agent/turns" `
                -TimeoutSec $ReadinessTimeoutSeconds `
                -ContentType 'application/json; charset=utf-8' `
                -Body ([Text.Encoding]::UTF8.GetBytes($disabledRequest))
            $disabledStatus = [int]$disabledHttp.StatusCode
            $disabledBody = $disabledHttp.Content | ConvertFrom-Json
        }
        catch {
            $disabledStatus = [int]$_.Exception.Response.StatusCode
            $disabledBody = if ([string]::IsNullOrWhiteSpace([string]$_.ErrorDetails.Message)) {
                $null
            } else {
                $_.ErrorDetails.Message | ConvertFrom-Json
            }
        }
        if ($disabledStatus -ne 503 -or
                ($null -ne $disabledBody -and
                [string]$disabledBody.error.code -ne 'AGENT_STATE_UNAVAILABLE')) {
            throw 'CONTENT_ONLY direct Agent POST did not fail closed.'
        }
        Write-Output 'Packaged content-only API fail-closed smoke passed.'
    }

    if ($Lane -eq 'LIVE') {
        $latencyBuckets = @()
        # GENERAL provider behavior is owned by the stricter multi-scenario
        # quality gate below; do not add a second stochastic one-shot gate.
        foreach ($scenario in @('SOCIAL')) {
            $stopwatch = [Diagnostics.Stopwatch]::StartNew()
            $probeOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
                -File (Join-Path $root 'scripts\provider-probe\invoke-live-provider-probe.ps1') `
                -BackendBaseUrl $baseUrl `
                -ExpectedContentVersion ([string]$publicContent.contentVersion) `
                -Scenario $scenario `
                -TimeoutSeconds $ReadinessTimeoutSeconds `
                -FailOnDegraded 2>&1 | Out-String).Trim()
            $stopwatch.Stop()
            if ($LASTEXITCODE -ne 0 -or $probeOutput -ne 'LIVE_PROVIDER_CONNECTED') {
                throw "Live Provider $scenario verification failed: $probeOutput"
            }
            $bucket = if ($stopwatch.Elapsed.TotalSeconds -lt 5) {
                'LT_5S'
            } elseif ($stopwatch.Elapsed.TotalSeconds -lt 10) {
                'LT_10S'
            } elseif ($stopwatch.Elapsed.TotalSeconds -lt 20) {
                'LT_20S'
            } else {
                'GE_20S'
            }
            $latencyBuckets += "$scenario=$bucket"
        }
        Write-Output ("Packaged Live Provider verification passed; latency=" + `
                ($latencyBuckets -join ','))
        $qualityOutput = ''
        $qualityExitCode = 1
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $qualityOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
                -File (Join-Path $root 'scripts\assert-live-general-answer-quality.ps1') `
                -BackendBaseUrl $baseUrl `
                -ExpectedContentVersion ([string]$publicContent.contentVersion) `
                -TimeoutSeconds $ReadinessTimeoutSeconds 2>&1 | Out-String).Trim()
            $qualityExitCode = $LASTEXITCODE
        }
        finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }
        if ($qualityExitCode -ne 0 -or
                $qualityOutput -notmatch '(?m)^GENERAL_QUALITY_PASS$') {
            throw "Live Provider general quality verification failed: $qualityOutput"
        }
        Write-Output $qualityOutput
    }

    if ($Lane -eq 'PROJECT_DISCUSSION') {
        $discussionOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File (Join-Path $root 'scripts\assert-live-project-discussion-context.ps1') `
            -BackendBaseUrl $baseUrl `
            -ExpectedContentVersion ([string]$publicContent.contentVersion) `
            -TimeoutSeconds $ReadinessTimeoutSeconds `
            -AuthorizeRealProvider 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or
                $discussionOutput -notmatch '^PROJECT_DISCUSSION_PASS operation=') {
            throw "Project discussion live verification failed: $discussionOutput"
        }
        Write-Output $discussionOutput
    }

    if ($SkipPlaywright) {
        Write-Output 'Packaged backend/API smoke passed; Playwright skipped by request.'
    }
    else {
        $env:PLAYWRIGHT_EXTERNAL_SERVER = '1'
        $env:PLAYWRIGHT_REAL_API = '1'
        $env:PLAYWRIGHT_BASE_URL = $baseUrl
        $env:PLAYWRIGHT_ADMISSION = if ($Lane -eq 'ADMISSION') { '1' } else { '0' }
        $env:PLAYWRIGHT_CONTENT_ONLY = if ($Lane -eq 'CONTENT_ONLY') { '1' } else { '0' }
        $env:PLAYWRIGHT_SLOW_PROVIDER = if ($Lane -eq 'BODY_STALL') { '1' } else { '0' }
        $env:PLAYWRIGHT_DEPTH_TWO = if ($Lane -eq 'DEPTH_TWO') { '1' } else { '0' }
        $env:PLAYWRIGHT_PROJECT_DISCUSSION = if ($Lane -eq 'PROJECT_DISCUSSION') {
            '1'
        } else {
            '0'
        }
        $env:PLAYWRIGHT_PROJECT_DISCUSSION_EXPIRY = if (
                $Lane -eq 'PROJECT_DISCUSSION_EXPIRY') { '1' } else { '0' }
        if ($RetrievalProfile -in @('KEYWORD_ONLY', 'HYBRID')) {
            $env:PLAYWRIGHT_REAL_RETRIEVAL = '1'
        }

        $playwrightCommand = @(
            '--prefix', (Join-Path $root 'frontend'),
            'run', $PlaywrightScript,
            '--', '--workers=1'
        ) + @($PlaywrightArguments)
        & $NpmExecutable @playwrightCommand
        $playwrightExitCode = $LASTEXITCODE
        if ($Lane -eq 'BODY_STALL') {
            $bodyStallFixtureProcess.Refresh()
            if ($bodyStallFixtureProcess.HasExited) {
                throw ("BODY_STALL fixture exited unexpectedly: exitCode={0}" -f `
                        $bodyStallFixtureProcess.ExitCode)
            }
        }
    }
}
finally {
    try {
        Restore-EnvironmentVariable 'PORTFOLIO_CONVERSATION_CONTEXT_MODE' `
            $environment.PORTFOLIO_CONVERSATION_CONTEXT_MODE
        Restore-EnvironmentVariable 'PORTFOLIO_CONTEXT_DATABASE_URL' `
            $environment.PORTFOLIO_CONTEXT_DATABASE_URL
        Restore-EnvironmentVariable 'PORTFOLIO_CONTEXT_DATABASE_USERNAME' `
            $environment.PORTFOLIO_CONTEXT_DATABASE_USERNAME
        Restore-EnvironmentVariable 'PORTFOLIO_CONTEXT_DATABASE_PASSWORD' `
            $environment.PORTFOLIO_CONTEXT_DATABASE_PASSWORD
        Restore-EnvironmentVariable 'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID' `
            $environment.PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID
        Restore-EnvironmentVariable 'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY' `
            $environment.PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY
        Restore-EnvironmentVariable 'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID' `
            $environment.PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID
        Restore-EnvironmentVariable 'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY' `
            $environment.PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY
        Restore-EnvironmentVariable 'PORTFOLIO_MODEL_PROVIDER' `
            $environment.PORTFOLIO_MODEL_PROVIDER
        Restore-EnvironmentVariable 'PORTFOLIO_AGENT_DEEPSEEK_API_KEY' `
            $environment.PORTFOLIO_AGENT_DEEPSEEK_API_KEY
        Restore-EnvironmentVariable 'PORTFOLIO_AGENT_GLM_API_KEY' `
            $environment.PORTFOLIO_AGENT_GLM_API_KEY
        Restore-EnvironmentVariable 'PLAYWRIGHT_EXTERNAL_SERVER' $environment.PLAYWRIGHT_EXTERNAL_SERVER
        Restore-EnvironmentVariable 'PLAYWRIGHT_REAL_API' $environment.PLAYWRIGHT_REAL_API
        Restore-EnvironmentVariable 'PLAYWRIGHT_BASE_URL' $environment.PLAYWRIGHT_BASE_URL
        Restore-EnvironmentVariable 'PLAYWRIGHT_REAL_RETRIEVAL' `
            $environment.PLAYWRIGHT_REAL_RETRIEVAL
        Restore-EnvironmentVariable 'PLAYWRIGHT_ADMISSION' $environment.PLAYWRIGHT_ADMISSION
        Restore-EnvironmentVariable 'PLAYWRIGHT_CONTENT_ONLY' $environment.PLAYWRIGHT_CONTENT_ONLY
        Restore-EnvironmentVariable 'PLAYWRIGHT_SLOW_PROVIDER' $environment.PLAYWRIGHT_SLOW_PROVIDER
        Restore-EnvironmentVariable 'PLAYWRIGHT_DEPTH_TWO' $environment.PLAYWRIGHT_DEPTH_TWO
        Restore-EnvironmentVariable 'PLAYWRIGHT_PROJECT_DISCUSSION' `
            $environment.PLAYWRIGHT_PROJECT_DISCUSSION
        Restore-EnvironmentVariable 'PLAYWRIGHT_PROJECT_DISCUSSION_EXPIRY' `
            $environment.PLAYWRIGHT_PROJECT_DISCUSSION_EXPIRY
        Restore-EnvironmentVariable 'PLAYWRIGHT_PROVIDER_STALL_COORDINATION_URL' `
            $environment.PLAYWRIGHT_PROVIDER_STALL_COORDINATION_URL
        Assert-EnvironmentRestored 'PLAYWRIGHT_EXTERNAL_SERVER' $environment.PLAYWRIGHT_EXTERNAL_SERVER
        Assert-EnvironmentRestored 'PLAYWRIGHT_REAL_API' $environment.PLAYWRIGHT_REAL_API
        Assert-EnvironmentRestored 'PLAYWRIGHT_BASE_URL' $environment.PLAYWRIGHT_BASE_URL
        Assert-EnvironmentRestored 'PLAYWRIGHT_REAL_RETRIEVAL' `
            $environment.PLAYWRIGHT_REAL_RETRIEVAL
        Assert-EnvironmentRestored 'PLAYWRIGHT_ADMISSION' $environment.PLAYWRIGHT_ADMISSION
        Assert-EnvironmentRestored 'PLAYWRIGHT_CONTENT_ONLY' $environment.PLAYWRIGHT_CONTENT_ONLY
        Assert-EnvironmentRestored 'PLAYWRIGHT_SLOW_PROVIDER' $environment.PLAYWRIGHT_SLOW_PROVIDER
        Assert-EnvironmentRestored 'PLAYWRIGHT_DEPTH_TWO' $environment.PLAYWRIGHT_DEPTH_TWO
        Assert-EnvironmentRestored 'PLAYWRIGHT_PROJECT_DISCUSSION' `
            $environment.PLAYWRIGHT_PROJECT_DISCUSSION
        Assert-EnvironmentRestored 'PLAYWRIGHT_PROJECT_DISCUSSION_EXPIRY' `
            $environment.PLAYWRIGHT_PROJECT_DISCUSSION_EXPIRY
        Assert-EnvironmentRestored 'PLAYWRIGHT_PROVIDER_STALL_COORDINATION_URL' `
            $environment.PLAYWRIGHT_PROVIDER_STALL_COORDINATION_URL
        Assert-EnvironmentRestored 'PORTFOLIO_MODEL_PROVIDER' $environment.PORTFOLIO_MODEL_PROVIDER
        Assert-EnvironmentRestored 'PORTFOLIO_AGENT_DEEPSEEK_API_KEY' `
            $environment.PORTFOLIO_AGENT_DEEPSEEK_API_KEY
        Assert-EnvironmentRestored 'PORTFOLIO_AGENT_GLM_API_KEY' `
            $environment.PORTFOLIO_AGENT_GLM_API_KEY
        Write-Output 'Playwright environment restored.'
    }
    finally {
        try {
        $process.Refresh()
        if (-not $process.HasExited) {
            Stop-Process -Id $process.Id -Force
            if (-not $process.WaitForExit(10000)) {
                throw "Packaged application process $($process.Id) did not stop."
            }
        }
        Write-Output "Packaged application process $($process.Id) is stopped."
        try {
            Assert-PackagedLogBoundary `
                -stdoutPath $stdoutPath `
                -stderrPath $stderrPath `
                -privacySentinel $privacySentinel
        }
        finally {
            foreach ($logPath in @($stdoutPath, $stderrPath)) {
                if (Test-Path -LiteralPath $logPath -PathType Leaf) {
                    Remove-Item -LiteralPath $logPath -Force
                }
            }
        }
        }
        finally {
            Remove-BodyStallFixture
        }
    }
}

if ($playwrightExitCode -ne 0) {
    Write-Output "Playwright failed with exit code $playwrightExitCode."
    exit $playwrightExitCode
}
