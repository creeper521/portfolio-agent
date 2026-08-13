param(
    [string]$JarPath,
    [string]$JavaExecutable = 'java.exe',
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
    [ValidateRange(1, 65535)]
    [int]$Port = 4173,
    [ValidateRange(1, 300)]
    [int]$ReadinessTimeoutSeconds = 60
)

$ErrorActionPreference = 'Stop'
$ContextMode = if ([string]::IsNullOrWhiteSpace($ContextMode)) {
    'POSTGRESQL'
} else {
    $ContextMode.Trim().ToUpperInvariant()
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
    P3_REAL_API = Get-EnvironmentSnapshot 'P3_REAL_API'
    PLAYWRIGHT_BASE_URL = Get-EnvironmentSnapshot 'PLAYWRIGHT_BASE_URL'
    PLAYWRIGHT_REAL_RETRIEVAL = Get-EnvironmentSnapshot 'PLAYWRIGHT_REAL_RETRIEVAL'
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
}

if ($ContextMode -notin @('POSTGRESQL', 'DISABLED')) {
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

$quotedJar = '"' + $jar + '"'
$applicationArguments = @(
    '-jar',
    $quotedJar,
    "--server.port=$Port",
    '--spring.profiles.active=prod',
    '--spring.main.banner-mode=off',
    "--portfolio.conversation-context.mode=$ContextMode",
    '--portfolio.diagnostics.frontend-ingest-enabled=true',
    '--portfolio.answer-production.requests-per-minute=1000'
)
if (-not $RequireLiveProvider) {
    $applicationArguments += '--portfolio.model-expression.enabled=false'
    $applicationArguments += '--portfolio.conversational-agent.enabled=false'
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
$process = Start-Process -FilePath $JavaExecutable `
    -ArgumentList $applicationArguments `
    -RedirectStandardOutput $stdoutPath `
    -RedirectStandardError $stderrPath `
    -PassThru -WindowStyle Hidden

Write-Output "Started packaged application process $($process.Id)."
if (-not $RequireLiveProvider) {
    Write-Output 'Provider calls disabled for deterministic smoke.'
}

$playwrightExitCode = 0
try {
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $process.Refresh()
        if ($process.HasExited) {
            throw "Packaged application exited before readiness with exit code $($process.ExitCode)."
        }

        $response = $null
        try {
            $response = Invoke-WebRequest -UseBasicParsing "$baseUrl/api/v1/public-content"
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
            $requiredFields = @('contentVersion', 'owner', 'projects', 'evidence', 'timeline')
            foreach ($field in $requiredFields) {
                $property = $publicContent.PSObject.Properties[$field]
                if ($null -eq $property -or $null -eq $property.Value) {
                    throw "Readiness public-content JSON is missing required field '$field'."
                }
            }
            if ([string]::IsNullOrWhiteSpace([string]$publicContent.contentVersion)) {
                throw "Readiness public-content JSON has a blank contentVersion."
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

    Write-Output "Packaged application process $($process.Id) owns port $Port; readiness returned validated public-content JSON."

    $correlationResponse = Invoke-WebRequest -UseBasicParsing `
        "$baseUrl/api/v1/public-content"
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
        -Uri "$baseUrl/api/v1/client-diagnostics" `
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
            -Uri "$baseUrl/api/v1/client-diagnostics" `
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
            -Uri "$baseUrl/api/v1/client-diagnostics" `
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

    $caseResponse = Invoke-RestMethod -UseBasicParsing `
        "$baseUrl/api/v1/cases/multilingual-image-preservation"
    if ([string]$caseResponse.slug -ne 'multilingual-image-preservation') {
        throw 'Packaged Case API returned the wrong subject.'
    }
    if (@($caseResponse.evidence).Count -eq 0) {
        throw 'Packaged Case API returned no public evidence.'
    }
    Write-Output 'Packaged Case API smoke passed.'

    $caseAgentRequest = @{
        turnId = 'packaged-case-agent-smoke'
        requestToken = 'b0b2b34a-b4bf-40db-909a-d2ce8d95fffb'
        question = $privacySentinel
        messages = @()
        context = @{
            projectSlug = $null
            caseSlug = 'multilingual-image-preservation'
            audienceRole = 'INTERVIEWER'
            source = 'CASE'
        }
    } | ConvertTo-Json -Depth 5 -Compress
    $caseAgentResponse = Invoke-RestMethod -UseBasicParsing `
        -Method Post `
        -Uri "$baseUrl/api/v2/answers" `
        -ContentType 'application/json; charset=utf-8' `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($caseAgentRequest))
    if ([string]$caseAgentResponse.contentVersion -ne [string]$publicContent.contentVersion) {
        throw 'Packaged Case Agent returned the wrong contentVersion.'
    }
    if (@($caseAgentResponse.blocks).Count -eq 0) {
        throw 'Packaged Case Agent returned no answer blocks.'
    }
    $serializedCaseAgentResponse =
            $caseAgentResponse | ConvertTo-Json -Depth 12 -Compress
    if ($serializedCaseAgentResponse -match [regex]::Escape($privacySentinel)) {
        throw 'Packaged Case Agent response leaked the visitor-content sentinel.'
    }
    Write-Output 'Packaged Case Agent smoke passed.'

    if ($RequireLiveProvider) {
        $probeOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File (Join-Path $root 'scripts\provider-probe\invoke-live-provider-probe.ps1') `
            -BackendBaseUrl $baseUrl `
            -ExpectedContentVersion ([string]$publicContent.contentVersion) `
            -TimeoutSeconds $ReadinessTimeoutSeconds `
            -FailOnDegraded 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or $probeOutput -ne 'LIVE_PROVIDER_CONNECTED') {
            throw "Live Provider verification failed: $probeOutput"
        }
        Write-Output 'Packaged Live Provider verification passed.'
    }

    $env:PLAYWRIGHT_EXTERNAL_SERVER = '1'
    $env:PLAYWRIGHT_REAL_API = '1'
    $env:P3_REAL_API = '1'
    $env:PLAYWRIGHT_BASE_URL = $baseUrl
    if ($RetrievalProfile -in @('KEYWORD_ONLY', 'HYBRID')) {
        $env:PLAYWRIGHT_REAL_RETRIEVAL = '1'
    }

    & $NpmExecutable --prefix (Join-Path $root 'frontend') run test:e2e -- --workers=1
    $playwrightExitCode = $LASTEXITCODE
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
        Restore-EnvironmentVariable 'PLAYWRIGHT_EXTERNAL_SERVER' $environment.PLAYWRIGHT_EXTERNAL_SERVER
        Restore-EnvironmentVariable 'PLAYWRIGHT_REAL_API' $environment.PLAYWRIGHT_REAL_API
        Restore-EnvironmentVariable 'P3_REAL_API' $environment.P3_REAL_API
        Restore-EnvironmentVariable 'PLAYWRIGHT_BASE_URL' $environment.PLAYWRIGHT_BASE_URL
        Restore-EnvironmentVariable 'PLAYWRIGHT_REAL_RETRIEVAL' `
            $environment.PLAYWRIGHT_REAL_RETRIEVAL
        Assert-EnvironmentRestored 'PLAYWRIGHT_EXTERNAL_SERVER' $environment.PLAYWRIGHT_EXTERNAL_SERVER
        Assert-EnvironmentRestored 'PLAYWRIGHT_REAL_API' $environment.PLAYWRIGHT_REAL_API
        Assert-EnvironmentRestored 'P3_REAL_API' $environment.P3_REAL_API
        Assert-EnvironmentRestored 'PLAYWRIGHT_BASE_URL' $environment.PLAYWRIGHT_BASE_URL
        Assert-EnvironmentRestored 'PLAYWRIGHT_REAL_RETRIEVAL' `
            $environment.PLAYWRIGHT_REAL_RETRIEVAL
        Write-Output 'Playwright environment restored.'
    }
    finally {
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
}

if ($playwrightExitCode -ne 0) {
    Write-Output "Playwright failed with exit code $playwrightExitCode."
    exit $playwrightExitCode
}
