$ErrorActionPreference = 'Stop'

$probeScript = Join-Path $PSScriptRoot 'invoke-live-provider-probe.ps1'
$fixtureScript = Join-Path (Split-Path -Parent $PSScriptRoot) `
    'test-fixtures\start-local-fake-server.ps1'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('portfolio-provider-probe-' + [guid]::NewGuid().ToString('N'))
$keySentinel = 'probe-key-sentinel-' + [guid]::NewGuid().ToString('N')
$environmentNames = @(
    'PORTFOLIO_MODEL_ENABLED',
    'PORTFOLIO_MODEL_DATA_POLICY_APPROVED',
    'PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED',
    'PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED',
    'PORTFOLIO_MODEL_PROVIDER',
    'PORTFOLIO_AGENT_DEEPSEEK_API_KEY',
    'PORTFOLIO_AGENT_GLM_API_KEY'
)

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

function Get-FreePort {
    $listener = [System.Net.Sockets.TcpListener]::new(
        [System.Net.IPAddress]::Loopback,
        0
    )
    $listener.Start()
    try {
        return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    }
    finally {
        $listener.Stop()
    }
}

function Set-ProcessEnvironmentVariable(
    [string]$Name,
    [AllowNull()][string]$Value
) {
    [System.Environment]::SetEnvironmentVariable(
        $Name,
        $Value,
        [System.EnvironmentVariableTarget]::Process
    )
}

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
    $value = if ($Snapshot.Exists) { $Snapshot.Value } else { $null }
    Set-ProcessEnvironmentVariable $Name $value
}

function Set-ApprovedEnvironment {
    Set-ProcessEnvironmentVariable 'PORTFOLIO_MODEL_ENABLED' 'true'
    Set-ProcessEnvironmentVariable 'PORTFOLIO_MODEL_DATA_POLICY_APPROVED' 'true'
    Set-ProcessEnvironmentVariable 'PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED' 'true'
    Set-ProcessEnvironmentVariable 'PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED' 'true'
    Set-ProcessEnvironmentVariable 'PORTFOLIO_MODEL_PROVIDER' 'DEEPSEEK_V4_FLASH'
    Set-ProcessEnvironmentVariable 'PORTFOLIO_AGENT_DEEPSEEK_API_KEY' $keySentinel
    Set-ProcessEnvironmentVariable 'PORTFOLIO_AGENT_GLM_API_KEY' $keySentinel
}

function Test-PortOpen([int]$Port) {
    $listener = [System.Net.NetworkInformation.IPGlobalProperties]::
        GetIPGlobalProperties().GetActiveTcpListeners() |
        Where-Object { $_.Port -eq $Port } |
        Select-Object -First 1
    return $null -ne $listener
}

function Start-FakeServer([string]$Mode) {
    $port = Get-FreePort
    $errorPath = Join-Path $fixtureRoot "fake-$port.err.log"
    $stdoutPath = Join-Path $fixtureRoot "fake-$port.out.log"
    $process = Start-Process -FilePath 'powershell.exe' `
        -ArgumentList @(
            '-NoProfile',
            '-ExecutionPolicy', 'Bypass',
            '-File', $fixtureScript,
            '-Port', "$port",
            '-Mode', $Mode
        ) `
        -PassThru -WindowStyle Hidden `
        -RedirectStandardError $errorPath `
        -RedirectStandardOutput $stdoutPath
    $deadline = (Get-Date).AddSeconds(15)
    while (-not (Test-PortOpen $port) -and (Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 200
    }
    if (-not (Test-PortOpen $port)) {
        $process.Refresh()
        $detail = "HasExited=$($process.HasExited)"
        if ($process.HasExited) {
            $detail += " ExitCode=$($process.ExitCode)"
        }
        if (Test-Path -LiteralPath $errorPath -PathType Leaf) {
            $detail += " Stderr=" + (Get-Content -LiteralPath $errorPath -Raw)
        }
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        throw "Fake server did not open port $port. $detail"
    }
    return @{
        Process = $process
        Port = $port
    }
}

function Stop-FakeServer($Server) {
    if ($null -ne $Server -and $null -ne $Server.Process) {
        Stop-Process -Id $Server.Process.Id -Force -ErrorAction SilentlyContinue
    }
}

function Invoke-Probe(
    [int]$Port,
    [string[]]$AdditionalArguments = @()
) {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $probeScript `
            -BackendBaseUrl "http://127.0.0.1:$Port" `
            -ExpectedContentVersion 'test-v1' `
            -TimeoutSeconds 10 `
            @AdditionalArguments 2>&1 | Out-String)
        return @{
            ExitCode = $LASTEXITCODE
            Output = $output
        }
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Count-ProbeTempFiles {
    return @(Get-ChildItem -LiteralPath ([System.IO.Path]::GetTempPath()) `
        -Filter 'portfolio-provider-probe-*.json' -File -ErrorAction SilentlyContinue).Count
}

$environment = @{}
foreach ($name in $environmentNames) {
    $environment[$name] = Get-EnvironmentSnapshot $name
}

$modelServer = $null
$fallbackServer = $null
try {
    [System.IO.Directory]::CreateDirectory($fixtureRoot) | Out-Null
    Set-ApprovedEnvironment

    Assert-True (Test-Path -LiteralPath $probeScript -PathType Leaf) `
        "Live Provider probe script does not exist: $probeScript"
    $probeSource = [System.IO.File]::ReadAllText($probeScript)
    foreach ($forbidden in @(
        'projectSlug', 'caseSlug', 'questionPresetId', 'contractVersion',
        'referenceContext', 'recommendationContext'
    )) {
        Assert-True ($probeSource -notmatch "(?m)^\s*$forbidden\s*=") `
            "probe must not construct $forbidden"
    }

    $modelServer = Start-FakeServer 'BACKEND_MODEL'
    $connected = Invoke-Probe $modelServer.Port
    Assert-True ($connected.ExitCode -eq 0) `
        "subject-free canary must connect. Output: $($connected.Output)"
    Assert-True ($connected.Output.Trim() -eq 'LIVE_PROVIDER_CONNECTED') `
        "subject-free canary must report LIVE_PROVIDER_CONNECTED. Output: $($connected.Output)"
    Assert-True ($connected.Output -notmatch [regex]::Escape($keySentinel)) `
        'connected probe leaked the key sentinel.'
    $temporaryFilesBefore = Count-ProbeTempFiles
    $fallbackServer = Start-FakeServer 'BACKEND_FALLBACK'
    $degraded = Invoke-Probe $fallbackServer.Port
    Assert-True ($degraded.ExitCode -eq 0) `
        "fallback fixture must degrade without exiting. Output: $($degraded.Output)"
    Assert-True ($degraded.Output.Trim() -eq 'LIVE_PROVIDER_DEGRADED:PROVIDER_DRAFT_REJECTED') `
        'fallback fixture must report the safe degraded category.'
    Assert-True ($degraded.Output -notmatch [regex]::Escape($keySentinel)) `
        'degraded probe leaked the key sentinel.'
    Assert-True ((Count-ProbeTempFiles) -eq $temporaryFilesBefore) `
        'probe must clean up its temporary response file.'

    $failing = Invoke-Probe $fallbackServer.Port '-FailOnDegraded'
    Assert-True ($failing.ExitCode -eq 1) `
        '-FailOnDegraded must exit 1 on degraded responses.'
    Assert-True ($failing.Output.Trim() -eq 'LIVE_PROVIDER_DEGRADED:PROVIDER_DRAFT_REJECTED') `
        '-FailOnDegraded must still report the safe category.'
    Assert-True ((Count-ProbeTempFiles) -eq $temporaryFilesBefore) `
        'failing probe must clean up its temporary response file.'

    Stop-FakeServer $modelServer
    Stop-FakeServer $fallbackServer
    $modelServer = $null
    $fallbackServer = $null

    $unavailable = Invoke-Probe (Get-FreePort)
    Assert-True ($unavailable.ExitCode -eq 0) `
        "transport failure must degrade without exiting. Output: $($unavailable.Output)"
    Assert-True ($unavailable.Output.Trim() -eq 'LIVE_PROVIDER_DEGRADED:PROVIDER_UNAVAILABLE') `
        'transport failure must report PROVIDER_UNAVAILABLE.'

    Write-Output 'Live Provider probe tests passed'
}
finally {
    Stop-FakeServer $modelServer
    Stop-FakeServer $fallbackServer
    foreach ($name in $environmentNames) {
        Restore-EnvironmentVariable $name $environment[$name]
    }
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolvedFixtureRoot = (Resolve-Path -LiteralPath $fixtureRoot).Path
        if (-not ([System.IO.Path]::GetFileName($resolvedFixtureRoot)).
                StartsWith('portfolio-provider-probe-')) {
            throw "Refusing to remove unverified fixture path: $resolvedFixtureRoot"
        }
        Remove-Item -LiteralPath $resolvedFixtureRoot -Recurse -Force
    }
}
