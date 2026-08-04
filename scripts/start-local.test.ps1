$ErrorActionPreference = 'Stop'

$launcher = Join-Path $PSScriptRoot 'start-local.ps1'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('portfolio-start-local-' + [guid]::NewGuid().ToString('N'))
$keySentinel = 'key-' + [guid]::NewGuid().ToString('N')

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

function Write-Secrets([string]$Path, [string[]]$Lines) {
    [System.IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) |
        Out-Null
    [System.IO.File]::WriteAllLines(
        $Path,
        $Lines,
        [System.Text.UTF8Encoding]::new($false)
    )
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

function Invoke-Launcher(
    [string]$SecretsFile,
    [string[]]$AdditionalArguments = @()
) {
    $backendPort = Get-FreePort
    $frontendPort = Get-FreePort
    while ($frontendPort -eq $backendPort) {
        $frontendPort = Get-FreePort
    }
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $launcher `
            -SecretsFile $SecretsFile `
            -BackendPort $backendPort `
            -FrontendPort $frontendPort `
            -CheckOnly `
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

function Test-PortOpen([int]$Port) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync('127.0.0.1', $Port)
        if (-not $task.Wait(500)) {
            return $false
        }
        return $client.Connected
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

function Invoke-Orchestration(
    [string]$SecretsFile,
    [string]$BackendFixtureMode,
    [int]$BackendPort,
    [int]$FrontendPort
) {
    $logDirectory = Join-Path $fixtureRoot "logs-$BackendPort"
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $launcher `
            -SecretsFile $SecretsFile `
            -BackendPort $BackendPort `
            -FrontendPort $FrontendPort `
            -BackendFixtureMode $BackendFixtureMode `
            -FrontendFixture `
            -LogDirectory $logDirectory `
            -ReadinessTimeoutSeconds 10 `
            -ExitAfterProbe 2>&1 | Out-String)
        return @{
            ExitCode = $LASTEXITCODE
            Output = $output
            LogDirectory = $logDirectory
        }
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Valid-Lines([string]$Provider = 'DEEPSEEK_V4_FLASH') {
    $keyLine = if ($Provider -eq 'GLM_4_7') {
        "PORTFOLIO_AGENT_GLM_API_KEY=$keySentinel"
    }
    else {
        "PORTFOLIO_AGENT_DEEPSEEK_API_KEY=$keySentinel"
    }
    return @(
        'PORTFOLIO_MODEL_ENABLED=true',
        'PORTFOLIO_MODEL_DATA_POLICY_APPROVED=true',
        'PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED=true',
        'PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED=true',
        "PORTFOLIO_MODEL_PROVIDER=$Provider",
        $keyLine
    )
}

function Assert-SafeFailure(
    [hashtable]$Result,
    [string]$ExpectedCode,
    [string]$CaseName
) {
    Assert-True ($Result.ExitCode -ne 0) "$CaseName must fail."
    Assert-True ($Result.Output -match [regex]::Escape($ExpectedCode)) `
        "$CaseName did not report $ExpectedCode. Output: $($Result.Output)"
    Assert-True ($Result.Output -notmatch [regex]::Escape($keySentinel)) `
        "$CaseName leaked the key sentinel."
}

try {
    [System.IO.Directory]::CreateDirectory($fixtureRoot) | Out-Null

    foreach ($provider in @('DEEPSEEK_V4_FLASH', 'GLM_4_7')) {
        $valid = Join-Path $fixtureRoot "$provider.env"
        Write-Secrets $valid (Valid-Lines $provider)
        $result = Invoke-Launcher $valid
        Assert-True ($result.ExitCode -eq 0) `
            "Valid $provider secrets must pass. Output: $($result.Output)"
        Assert-True ($result.Output -match
                [regex]::Escape("LOCAL_CONFIG_VALID provider=$provider checks=6")) `
            "Valid $provider output did not report six checks."
        Assert-True ($result.Output -notmatch [regex]::Escape($keySentinel)) `
            "Valid $provider output leaked the key."
    }

    $missingFlag = Join-Path $fixtureRoot 'missing-flag.env'
    Write-Secrets $missingFlag @(
        'PORTFOLIO_MODEL_DATA_POLICY_APPROVED=true',
        'PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED=true',
        'PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED=true',
        'PORTFOLIO_MODEL_PROVIDER=DEEPSEEK_V4_FLASH',
        "PORTFOLIO_AGENT_DEEPSEEK_API_KEY=$keySentinel"
    )
    Assert-SafeFailure (Invoke-Launcher $missingFlag) `
        'LOCAL_CONFIG_REQUIRED_FLAG_MISSING:PORTFOLIO_MODEL_ENABLED' `
        'missing required flag'

    foreach ($fixture in @(
        @{
            Name = 'duplicate'
            Lines = @(
                'PORTFOLIO_MODEL_ENABLED=true',
                'PORTFOLIO_MODEL_ENABLED=true'
            )
            Code = 'LOCAL_CONFIG_FIELD_INVALID'
        },
        @{
            Name = 'unknown'
            Lines = @('NOT_ALLOWED=value')
            Code = 'LOCAL_CONFIG_FIELD_INVALID'
        },
        @{
            Name = 'expression'
            Lines = @('PORTFOLIO_MODEL_ENABLED=$(Get-ChildItem)')
            Code = 'LOCAL_CONFIG_VALUE_INVALID'
        },
        @{
            Name = 'separator'
            Lines = @('missing-separator')
            Code = 'LOCAL_CONFIG_FORMAT_INVALID'
        }
    )) {
        $path = Join-Path $fixtureRoot "$($fixture.Name).env"
        Write-Secrets $path $fixture.Lines
        Assert-SafeFailure (Invoke-Launcher $path) $fixture.Code $fixture.Name
    }

    $relativeResult = Invoke-Launcher 'relative.env'
    Assert-SafeFailure $relativeResult 'LOCAL_CONFIG_FILE_INVALID' `
        'relative secret path'

    $repositorySecret = Join-Path $repositoryRoot '.env.example'
    Assert-True (Test-Path -LiteralPath $repositorySecret -PathType Leaf) `
        'Repository-local rejection fixture is missing.'
    Assert-SafeFailure (Invoke-Launcher $repositorySecret) `
        'LOCAL_CONFIG_MUST_BE_OUTSIDE_REPOSITORY' `
        'repository-local secret path'

    $orchestrationSecrets = Join-Path $fixtureRoot 'orchestration.env'
    Write-Secrets $orchestrationSecrets (Valid-Lines)
    $testModeSnapshot = [Environment]::GetEnvironmentVariable(
        'PORTFOLIO_START_LOCAL_TEST_MODE',
        [EnvironmentVariableTarget]::Process
    )
    [Environment]::SetEnvironmentVariable(
        'PORTFOLIO_START_LOCAL_TEST_MODE',
        'true',
        [EnvironmentVariableTarget]::Process
    )
    try {
        $backendPort = Get-FreePort
        $frontendPort = Get-FreePort
        $modelResult = Invoke-Orchestration $orchestrationSecrets `
            'BACKEND_MODEL' $backendPort $frontendPort
        Assert-True ($modelResult.ExitCode -eq 0) `
            "MODEL fixture must pass. Output: $($modelResult.Output)"
        Assert-True ($modelResult.Output -match 'AI_CONNECTED provider=DEEPSEEK_V4_FLASH') `
            'MODEL fixture did not report AI_CONNECTED.'
        Assert-True ($modelResult.Output -notmatch [regex]::Escape($keySentinel)) `
            'MODEL fixture leaked the key.'
        Assert-True ($modelResult.Output -match 'LOG_DIRECTORY') `
            'MODEL fixture did not report the log directory.'
        $frontendInfo = Get-Content -LiteralPath `
            (Join-Path $modelResult.LogDirectory 'current\frontend-info.log') -Raw
        $frontendError = Get-Content -LiteralPath `
            (Join-Path $modelResult.LogDirectory 'current\frontend-error.log') -Raw
        $launcherLog = Get-Content -LiteralPath `
            (Join-Path $modelResult.LogDirectory 'current\launcher.log') -Raw
        Assert-True (-not (Test-Path -LiteralPath `
                (Join-Path $modelResult.LogDirectory 'current\backend-info.log'))) `
            'Fixture launcher must leave backend INFO ownership to Logback'
        Assert-True (-not (Test-Path -LiteralPath `
                (Join-Path $modelResult.LogDirectory 'current\backend-error.log'))) `
            'Fixture launcher must leave backend ERROR ownership to Logback'
        Assert-True $frontendInfo.Contains('browser-fixture-info') 'Browser INFO capture'
        Assert-True $frontendInfo.Contains('vite-fixture-info') 'Vite INFO capture'
        Assert-True $frontendError.Contains('vite-fixture-error') 'Vite ERROR capture'
        Assert-True $launcherLog.Contains('local.session.started') 'Launcher capture'
        $allLogs = $launcherLog + $frontendInfo + $frontendError
        Assert-True (-not $allLogs.Contains($keySentinel)) 'Log files leaked the key'
        Assert-True (-not (Test-PortOpen $backendPort)) `
            "Owned backend survived ExitAfterProbe on port $backendPort."
        Assert-True (-not (Test-PortOpen $frontendPort)) `
            "Owned frontend survived ExitAfterProbe on port $frontendPort."

        $backendPort = Get-FreePort
        $frontendPort = Get-FreePort
        $fallbackResult = Invoke-Orchestration $orchestrationSecrets `
            'BACKEND_FALLBACK' $backendPort $frontendPort
        Assert-True ($fallbackResult.ExitCode -eq 0) `
            "Fallback fixture must leave services in degraded mode. Output: $($fallbackResult.Output)"
        Assert-True ($fallbackResult.Output -match
                'AI_DEGRADED:PROVIDER_DRAFT_REJECTED') `
            'Fallback fixture did not report the safe degraded category.'
        Assert-True ($fallbackResult.Output -notmatch
                [regex]::Escape($keySentinel)) `
            'Fallback fixture leaked the key.'
        Assert-True (-not (Test-PortOpen $backendPort)) `
            'Degraded backend survived ExitAfterProbe.'
        Assert-True (-not (Test-PortOpen $frontendPort)) `
            'Degraded frontend survived ExitAfterProbe.'

        $occupiedPort = Get-FreePort
        $occupiedListener = [System.Net.Sockets.TcpListener]::new(
            [System.Net.IPAddress]::Loopback,
            $occupiedPort
        )
        $occupiedListener.Start()
        try {
            $occupiedResult = Invoke-Orchestration $orchestrationSecrets `
                'BACKEND_MODEL' $occupiedPort (Get-FreePort)
            Assert-SafeFailure $occupiedResult `
                "LOCAL_PORT_OCCUPIED:$occupiedPort" 'occupied backend port'
            Assert-True (Test-PortOpen $occupiedPort) `
                'Launcher stopped a listener it did not own.'
        }
        finally {
            $occupiedListener.Stop()
        }
    }
    finally {
        [Environment]::SetEnvironmentVariable(
            'PORTFOLIO_START_LOCAL_TEST_MODE',
            $testModeSnapshot,
            [EnvironmentVariableTarget]::Process
        )
    }

    $launcherText = Get-Content -LiteralPath $launcher -Raw
    Assert-True ($launcherText -match '\[int\]\$FrontendPort = 5173') `
        'Unified launcher must default the frontend port to 5173.'
    Assert-True ($launcherText -match "'--strictPort'") `
        'Unified launcher must pass --strictPort to the frontend.'
    Assert-True ($launcherText -match 'PORTFOLIO_DIAGNOSTICS_FRONTEND_INGEST_ENABLED') `
        'Unified launcher must bind the diagnostics switch to portfolio.diagnostics.frontend-ingest-enabled.'
    Assert-True ($launcherText -notmatch 'PORTFOLIO_FRONTEND_DIAGNOSTICS_ENABLED') `
        'Unified launcher must not use the unmapped legacy diagnostics switch.'
    Assert-True ($launcherText -match "PORTFOLIO_FRONTEND_LOG_OWNER.*=.*'UNIFIED'") `
        'Unified launcher must delegate frontend log ownership to its own router.'
    Assert-True ($launcherText -match '\$frontendEnvironment') `
        'Unified launcher must pass a frontend child environment.'

    Write-Output 'start-local tests passed'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolvedFixtureRoot = (Resolve-Path -LiteralPath $fixtureRoot).Path
        if (-not ([System.IO.Path]::GetFileName($resolvedFixtureRoot)).
                StartsWith('portfolio-start-local-')) {
            throw "Refusing to remove unverified fixture path: $resolvedFixtureRoot"
        }
        Remove-Item -LiteralPath $resolvedFixtureRoot -Recurse -Force
    }
}
