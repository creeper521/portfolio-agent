$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root 'backend\target\portfolio-agent.jar'
$baseUrl = 'http://127.0.0.1:4173'

if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw 'Packaged JAR is missing. Build the frontend and run Maven clean package first.'
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

$environment = @{
    PLAYWRIGHT_EXTERNAL_SERVER = Get-EnvironmentSnapshot 'PLAYWRIGHT_EXTERNAL_SERVER'
    PLAYWRIGHT_REAL_API = Get-EnvironmentSnapshot 'PLAYWRIGHT_REAL_API'
    PLAYWRIGHT_BASE_URL = Get-EnvironmentSnapshot 'PLAYWRIGHT_BASE_URL'
}

$process = Start-Process -FilePath 'java.exe' `
    -ArgumentList @('-jar', $jar, '--server.port=4173') `
    -PassThru -WindowStyle Hidden

Write-Output "Started packaged application process $($process.Id)."

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

            $ownedListeners = @(Get-NetTCPConnection -LocalPort 4173 -State Listen `
                -ErrorAction SilentlyContinue | Where-Object { $_.OwningProcess -eq $process.Id })
            if ($ownedListeners.Count -eq 0) {
                throw "Port 4173 is not owned by packaged application process $($process.Id)."
            }

            $ready = $true
            break
        }

        Start-Sleep -Milliseconds 500
    }

    if (-not $ready) {
        throw 'Packaged application did not become ready.'
    }

    Write-Output "Packaged application process $($process.Id) owns port 4173; readiness returned application/json."
    $env:PLAYWRIGHT_EXTERNAL_SERVER = '1'
    $env:PLAYWRIGHT_REAL_API = '1'
    $env:PLAYWRIGHT_BASE_URL = $baseUrl

    & npm.cmd --prefix (Join-Path $root 'frontend') run test:e2e
    if ($LASTEXITCODE -ne 0) {
        throw "Playwright failed with exit code $LASTEXITCODE."
    }
}
finally {
    Restore-EnvironmentVariable 'PLAYWRIGHT_EXTERNAL_SERVER' $environment.PLAYWRIGHT_EXTERNAL_SERVER
    Restore-EnvironmentVariable 'PLAYWRIGHT_REAL_API' $environment.PLAYWRIGHT_REAL_API
    Restore-EnvironmentVariable 'PLAYWRIGHT_BASE_URL' $environment.PLAYWRIGHT_BASE_URL

    $process.Refresh()
    if (-not $process.HasExited) {
        Stop-Process -Id $process.Id -Force
        if (-not $process.WaitForExit(10000)) {
            throw "Packaged application process $($process.Id) did not stop."
        }
    }
    Write-Output "Packaged application process $($process.Id) is stopped."
}
