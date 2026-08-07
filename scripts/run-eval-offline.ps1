param(
    [Parameter(Mandatory = $true)]
    [string]$Manifest,
    [Parameter(Mandatory = $true)]
    [string]$Policy,
    [Parameter(Mandatory = $true)]
    [string]$OutputDir,
    [string]$OfflineReport,
    [switch]$SkipInstall,
    [string]$MavenExecutable = 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd',
    [string]$JavaExecutable = 'java.exe'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Get-FreePort {
    $listener = [System.Net.Sockets.TcpListener]::new(
        [System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try {
        return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    }
    finally {
        $listener.Stop()
    }
}

function Test-PortOpen([int]$Port) {
    $listener = [System.Net.NetworkInformation.IPGlobalProperties]::
        GetIPGlobalProperties().GetActiveTcpListeners() |
        Where-Object { $_.Port -eq $Port } |
        Select-Object -First 1
    return $null -ne $listener
}

try {
    if (-not $SkipInstall) {
        & $MavenExecutable -f (Join-Path $root 'backend\pom.xml') package -DskipTests
        if ($LASTEXITCODE -ne 0) {
            exit 2
        }
    }
    $jar = Join-Path $root 'backend\target\portfolio-agent.jar'
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
        exit 2
    }
    $port = Get-FreePort
    $serverProcess = $null
    try {
        $serverProcess = Start-Process -FilePath $JavaExecutable `
            -ArgumentList @('-jar', $jar, "--server.port=$port",
                '--portfolio.model-expression.enabled=false',
                '--portfolio.conversational-agent.enabled=false',
                '--portfolio.model-expression.external-data-policy-approved=false',
                '--portfolio.conversational-agent.visitor-data-policy-approved=false',
                '--portfolio.retrieval.profile=KEYWORD_ONLY') `
            -PassThru -WindowStyle Hidden
        $deadline = (Get-Date).AddSeconds(90)
        while (-not (Test-PortOpen $port) -and (Get-Date) -lt $deadline) {
            Start-Sleep -Milliseconds 500
        }
        if (-not (Test-PortOpen $port)) {
            throw "Backend did not open port $port."
        }
        $offlineArgs = @('--manifest', $Manifest, '--policy', $Policy,
            '--output-dir', $OutputDir, '--base-url', "http://127.0.0.1:$port")
        if ($OfflineReport) {
            $offlineArgs += @('--offline-report', $OfflineReport)
        }
        & (Join-Path $PSScriptRoot 'run-eval.ps1') -Command 'offline' `
            -CliArgs $offlineArgs -SkipInstall:$true `
            -MavenExecutable $MavenExecutable -JavaExecutable $JavaExecutable
        exit $LASTEXITCODE
    }
    finally {
        if ($null -ne $serverProcess) {
            & taskkill.exe /PID $serverProcess.Id /T /F 2>$null | Out-Null
        }
    }
}
catch {
    Write-Error $_
    exit 2
}
