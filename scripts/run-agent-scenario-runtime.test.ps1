$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$runner = Join-Path $PSScriptRoot 'run-agent-scenario-runtime.ps1'
$server = Join-Path $PSScriptRoot 'test-fixtures\start-local-fake-server.ps1'
$probe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$probe.Start()
$port = ([Net.IPEndPoint]$probe.LocalEndpoint).Port
$probe.Stop()
$stdout = Join-Path ([IO.Path]::GetTempPath()) `
    ('scenario-runtime-' + [guid]::NewGuid().ToString('N') + '.out')
$stderr = $stdout + '.err'
$process = Start-Process powershell.exe -ArgumentList @(
    '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $server,
    '-Port', $port, '-Mode', 'BACKEND_MODEL'
) -RedirectStandardOutput $stdout -RedirectStandardError $stderr `
    -PassThru -WindowStyle Hidden

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

try {
    $ready = $false
    for ($attempt = 0; $attempt -lt 100; $attempt++) {
        try {
            $client = [Net.Sockets.TcpClient]::new()
            $client.Connect('127.0.0.1', $port)
            $client.Dispose()
            $ready = $true
            break
        }
        catch {
            Start-Sleep -Milliseconds 50
        }
    }
    Assert-True $ready 'Scenario runtime fixture did not become ready.'
    $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File $runner -BackendBaseUrl "http://127.0.0.1:$port" `
        -TimeoutSeconds 5
    Assert-True ($LASTEXITCODE -eq 0) 'Inspectable scenario run must return a report.'
    $report = ($output -join [Environment]::NewLine) | ConvertFrom-Json
    Assert-True ($report.executionMode -eq 'PRODUCTION_HTTP_COMMAND') `
        'Scenario report must identify the executed boundary.'
    Assert-True ($report.total -eq 35 -and $report.results.Count -eq 35) `
        'Every registered scenario command must be invoked and reported.'
    Assert-True ($report.overall -ne 'PASS') `
        'Unobserved hard errors and setup gaps must prevent PASS.'
    Assert-True (@($report.results | Where-Object {
        [string]::IsNullOrWhiteSpace([string]$_.caseId)
    }).Count -eq 0) 'Every scenario result must retain its safe case ID.'

    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $requiredOutput = & powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $runner -BackendBaseUrl "http://127.0.0.1:$port" `
            -TimeoutSeconds 5 -RequireComplete 2>&1
        $requiredExit = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previous
    }
    Assert-True ($requiredExit -eq 1) `
        'Complete mode must reject incomplete scenario evidence.'
    Assert-True (($requiredOutput -join "`n") -match
        'AGENT_SCENARIO_RUNTIME_INCOMPLETE') `
        'Complete mode must emit a stable failure code.'

    Write-Output 'run-agent-scenario-runtime tests passed'
}
finally {
    if (-not $process.HasExited) {
        Stop-Process -Id $process.Id -Force
        $null = $process.WaitForExit(5000)
    }
    foreach ($path in @($stdout, $stderr)) {
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            Remove-Item -LiteralPath $path -Force
        }
    }
}
