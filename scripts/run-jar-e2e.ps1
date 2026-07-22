param(
    [string]$JarPath,
    [string]$JavaExecutable = 'java.exe',
    [string]$NpmExecutable = 'npm.cmd',
    [string]$ReleaseRoot = '',
    [string]$RetrievalProfile = '',
    [string]$ModelDirectory = '',
    [ValidateRange(1, 65535)]
    [int]$Port = 4173
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = if ([string]::IsNullOrWhiteSpace($JarPath)) {
    Join-Path $root 'backend\target\portfolio-agent.jar'
}
else {
    [System.IO.Path]::GetFullPath($JarPath)
}
$baseUrl = "http://127.0.0.1:$Port"

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

$environment = @{
    PLAYWRIGHT_EXTERNAL_SERVER = Get-EnvironmentSnapshot 'PLAYWRIGHT_EXTERNAL_SERVER'
    PLAYWRIGHT_REAL_API = Get-EnvironmentSnapshot 'PLAYWRIGHT_REAL_API'
    PLAYWRIGHT_BASE_URL = Get-EnvironmentSnapshot 'PLAYWRIGHT_BASE_URL'
    PLAYWRIGHT_REAL_RETRIEVAL = Get-EnvironmentSnapshot 'PLAYWRIGHT_REAL_RETRIEVAL'
}

$quotedJar = '"' + $jar + '"'
$applicationArguments = @('-jar', $quotedJar, "--server.port=$Port")
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
    -PassThru -WindowStyle Hidden

Write-Output "Started packaged application process $($process.Id)."

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
    $env:PLAYWRIGHT_EXTERNAL_SERVER = '1'
    $env:PLAYWRIGHT_REAL_API = '1'
    $env:PLAYWRIGHT_BASE_URL = $baseUrl
    if ($RetrievalProfile -in @('KEYWORD_ONLY', 'HYBRID')) {
        $env:PLAYWRIGHT_REAL_RETRIEVAL = '1'
    }

    & $NpmExecutable --prefix (Join-Path $root 'frontend') run test:e2e
    $playwrightExitCode = $LASTEXITCODE
}
finally {
    try {
        Restore-EnvironmentVariable 'PLAYWRIGHT_EXTERNAL_SERVER' $environment.PLAYWRIGHT_EXTERNAL_SERVER
        Restore-EnvironmentVariable 'PLAYWRIGHT_REAL_API' $environment.PLAYWRIGHT_REAL_API
        Restore-EnvironmentVariable 'PLAYWRIGHT_BASE_URL' $environment.PLAYWRIGHT_BASE_URL
        Restore-EnvironmentVariable 'PLAYWRIGHT_REAL_RETRIEVAL' `
            $environment.PLAYWRIGHT_REAL_RETRIEVAL
        Assert-EnvironmentRestored 'PLAYWRIGHT_EXTERNAL_SERVER' $environment.PLAYWRIGHT_EXTERNAL_SERVER
        Assert-EnvironmentRestored 'PLAYWRIGHT_REAL_API' $environment.PLAYWRIGHT_REAL_API
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
    }
}

if ($playwrightExitCode -ne 0) {
    Write-Output "Playwright failed with exit code $playwrightExitCode."
    exit $playwrightExitCode
}
