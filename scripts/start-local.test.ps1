$ErrorActionPreference = 'Stop'

$launcher = Join-Path $PSScriptRoot 'start-local.ps1'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$modelExample = Join-Path $repositoryRoot '.env.example'
$postgresExample = Join-Path $repositoryRoot '.env.postgres.example'
$localProfile = Join-Path $repositoryRoot `
    'backend\src\main\resources\application-local.yml'
$prodProfile = Join-Path $repositoryRoot `
    'backend\src\main\resources\application-prod.yml'
$testLocalProfile = Join-Path $repositoryRoot `
    'backend\src\test\resources\application-local.yml'
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

function Write-PostgresEnvironment([string]$Path) {
    Write-Secrets $Path @(
        'PORTFOLIO_POSTGRES_PORT=54329',
        'PORTFOLIO_POSTGRES_ADMIN_USERNAME=postgres',
        'PORTFOLIO_POSTGRES_ADMIN_PASSWORD=admin-secret',
        'PORTFOLIO_PUBLIC_DATABASE_NAME=portfolio_public_dev',
        'PORTFOLIO_PUBLIC_DATABASE_USERNAME=portfolio_public_owner',
        'PORTFOLIO_PUBLIC_DATABASE_PASSWORD=public-secret',
        'PORTFOLIO_GOVERNANCE_DATABASE_NAME=portfolio_governance_dev',
        'PORTFOLIO_GOVERNANCE_DATABASE_USERNAME=portfolio_governance_owner',
        'PORTFOLIO_GOVERNANCE_DATABASE_PASSWORD=governance-secret',
        'PORTFOLIO_CONTEXT_DATABASE_NAME=portfolio_context_dev',
        'PORTFOLIO_CONTEXT_DATABASE_USERNAME=portfolio_context_owner',
        'PORTFOLIO_CONTEXT_DATABASE_PASSWORD=context-secret',
        'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID=local-token-v1',
        'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=',
        'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID=local-payload-v1',
        'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY=Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA='
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
        $arguments = @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass',
            '-File', $launcher,
            '-ContextMode', 'IN_MEMORY',
            '-EnableGeneralAi',
            '-BackendPort', "$backendPort",
            '-FrontendPort', "$frontendPort",
            '-CheckOnly'
        )
        if (-not [string]::IsNullOrWhiteSpace($SecretsFile)) {
            $arguments += @('-SecretsFile', $SecretsFile)
        }
        $arguments += $AdditionalArguments
        $output = (& powershell.exe @arguments 2>&1 | Out-String)
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
            -ContextMode IN_MEMORY `
            -EnableGeneralAi `
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

    $noModelOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File $launcher `
        -ContextMode IN_MEMORY `
        -BackendPort (Get-FreePort) `
        -FrontendPort (Get-FreePort) `
        -CheckOnly 2>&1 | Out-String)
    Assert-True ($LASTEXITCODE -eq 0) `
        "No-model IN_MEMORY preflight must not require SecretsFile. Output: $noModelOutput"
    Assert-True ($noModelOutput -match `
            'LOCAL_RUNTIME_VALID mode=IN_MEMORY model=DISABLED') `
        'No-model preflight must report the explicit memory mode.'

    $missingPostgresEnv = Join-Path $fixtureRoot 'missing-postgres.env'
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $postgresFailure = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $launcher `
            -ContextMode POSTGRESQL `
            -PostgresEnvFile $missingPostgresEnv `
            -BackendPort (Get-FreePort) `
            -FrontendPort (Get-FreePort) `
            -CheckOnly 2>&1 | Out-String)
        $postgresFailureExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }
    Assert-True ($postgresFailureExitCode -ne 0) `
        'Standard PostgreSQL mode must refuse launch when readiness cannot run.'
    Assert-True ($postgresFailure -match `
            'LOCAL_POSTGRES_CONFIGURATION_INVALID') `
        'A missing PostgreSQL env file must be classified as configuration.'
    Assert-True ($postgresFailure -match '\.env\.postgres\.example') `
        'A missing PostgreSQL env file must point to the reviewed example.'
    Assert-True ($postgresFailure -notmatch 'postgres-local\.ps1 (start|bootstrap)') `
        'A missing env file must not recommend a database lifecycle command.'

    $postgresEnv = Join-Path $fixtureRoot 'postgres.env'
    Write-PostgresEnvironment $postgresEnv
    $fakeDocker = Join-Path $fixtureRoot 'docker.ps1'
    @'
$joined = $args -join ' '
if ($args[0] -eq 'info' -or
        ($args[0] -eq 'compose' -and $args[1] -eq 'version')) {
    Write-Output 'ready'
    exit 0
}
if ($joined -match 'ps -q postgres') {
    if ($env:PORTFOLIO_START_LOCAL_FAKE_DOCKER_MODE -ne 'CONTAINER_DOWN') {
        Write-Output 'fake-container-id'
    }
    exit 0
}
if ($args[0] -eq 'inspect') {
    Write-Output 'healthy'
    exit 0
}
if ($joined -match '\spsql\s') {
    if ($env:PORTFOLIO_START_LOCAL_FAKE_DOCKER_MODE -eq 'SCHEMA_MISSING') {
        Write-Output '0'
    }
    elseif ($env:PORTFOLIO_START_LOCAL_FAKE_DOCKER_MODE -eq 'QUERY_FAILED') {
        exit 42
    }
    else {
        Write-Output '1'
    }
    exit 0
}
exit 0
'@ | Set-Content -LiteralPath $fakeDocker -Encoding Ascii
    $oldPath = $env:PATH
    $oldDockerMode = $env:PORTFOLIO_START_LOCAL_FAKE_DOCKER_MODE
    $env:PATH = "$fixtureRoot;$oldPath"
    try {
        foreach ($case in @(
            @{ Mode = 'CONTAINER_DOWN'; Command = 'start' },
            @{ Mode = 'SCHEMA_MISSING'; Command = 'bootstrap' },
            @{ Mode = 'QUERY_FAILED'; Command = 'bootstrap' }
        )) {
            $env:PORTFOLIO_START_LOCAL_FAKE_DOCKER_MODE = $case.Mode
            $previousPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                $readinessFailure = (& powershell.exe -NoProfile `
                    -ExecutionPolicy Bypass -File $launcher `
                    -ContextMode POSTGRESQL `
                    -PostgresEnvFile $postgresEnv `
                    -ReadinessTimeoutSeconds 1 `
                    -BackendPort (Get-FreePort) `
                    -FrontendPort (Get-FreePort) `
                    -CheckOnly 2>&1 | Out-String)
                $readinessExitCode = $LASTEXITCODE
            }
            finally {
                $ErrorActionPreference = $previousPreference
            }
            Assert-True ($readinessExitCode -ne 0) `
                "$($case.Mode) readiness must fail closed."
            Assert-True ($readinessFailure -match (
                    'postgres-local\.ps1 ' + $case.Command)) `
                "$($case.Mode) must recommend $($case.Command). Output: $readinessFailure"
        }

        $invalidPostgresEnv = Join-Path $fixtureRoot 'invalid-postgres.env'
        ((Get-Content -LiteralPath $postgresEnv -Raw) +
            [Environment]::NewLine + 'PATH=C:\unsafe') |
            Set-Content -LiteralPath $invalidPostgresEnv -Encoding UTF8
        $env:PORTFOLIO_START_LOCAL_FAKE_DOCKER_MODE = ''
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $invalidConfigFailure = (& powershell.exe -NoProfile `
                -ExecutionPolicy Bypass -File $launcher `
                -ContextMode POSTGRESQL `
                -PostgresEnvFile $invalidPostgresEnv `
                -ReadinessTimeoutSeconds 1 `
                -BackendPort (Get-FreePort) `
                -FrontendPort (Get-FreePort) `
                -CheckOnly 2>&1 | Out-String)
            $invalidConfigExitCode = $LASTEXITCODE
        }
        finally {
            $ErrorActionPreference = $previousPreference
        }
        Assert-True ($invalidConfigExitCode -ne 0) `
            'Invalid PostgreSQL configuration must fail closed.'
        Assert-True ($invalidConfigFailure -match 'Fix: update' -and
                $invalidConfigFailure -match '\.env\.postgres\.example') `
            "Invalid config must point to the reviewed example. Output: $invalidConfigFailure"
        Assert-True ($invalidConfigFailure -notmatch `
                'postgres-local\.ps1 (start|bootstrap)') `
            'Invalid configuration must not recommend lifecycle commands.'

        foreach ($invalidCase in @(
            @{
                Name = 'duplicate-keys'
                Content = (Get-Content -LiteralPath $postgresEnv -Raw).Replace(
                    'Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA=',
                    'AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=')
            },
            @{
                Name = 'invalid-schema'
                Content = ((Get-Content -LiteralPath $postgresEnv -Raw) +
                    [Environment]::NewLine +
                    'PORTFOLIO_CONTEXT_DATABASE_SCHEMA=Invalid-Schema')
            }
        )) {
            $invalidPath = Join-Path $fixtureRoot `
                ("$($invalidCase.Name)-postgres.env")
            Set-Content -LiteralPath $invalidPath `
                -Value $invalidCase.Content -Encoding UTF8
            $previousPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                $invalidOutput = (& powershell.exe -NoProfile `
                    -ExecutionPolicy Bypass -File $launcher `
                    -ContextMode POSTGRESQL `
                    -PostgresEnvFile $invalidPath `
                    -ReadinessTimeoutSeconds 1 `
                    -BackendPort (Get-FreePort) `
                    -FrontendPort (Get-FreePort) `
                    -CheckOnly 2>&1 | Out-String)
                $invalidExitCode = $LASTEXITCODE
            }
            finally {
                $ErrorActionPreference = $previousPreference
            }
            Assert-True ($invalidExitCode -ne 0) `
                "$($invalidCase.Name) configuration must fail closed."
            Assert-True ($invalidOutput -match 'Fix: update' -and
                    $invalidOutput -match '\.env\.postgres\.example') `
                "$($invalidCase.Name) must recommend fixing configuration. Output: $invalidOutput"
            Assert-True ($invalidOutput -notmatch `
                    'postgres-local\.ps1 (start|bootstrap)') `
                "$($invalidCase.Name) must not recommend lifecycle commands."
        }
    }
    finally {
        $env:PATH = $oldPath
        $env:PORTFOLIO_START_LOCAL_FAKE_DOCKER_MODE = $oldDockerMode
    }

    $contentOnlyOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File $launcher `
        -ContextMode DISABLED `
        -BackendPort (Get-FreePort) `
        -FrontendPort (Get-FreePort) `
        -CheckOnly 2>&1 | Out-String)
    Assert-True ($LASTEXITCODE -eq 0) `
        "Content-only preflight must not require PostgreSQL or SecretsFile. Output: $contentOnlyOutput"
    Assert-True ($contentOnlyOutput -match `
            'LOCAL_RUNTIME_VALID mode=DISABLED model=DISABLED') `
        'Content-only preflight must report Agent State as disabled.'

    Assert-SafeFailure (Invoke-Launcher '') `
        'LOCAL_MODEL_SECRETS_REQUIRED' 'model-enabled launch without secrets'

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
        Assert-True ($modelResult.Output -notmatch 'PROBE_ROUTE_BYPASSED') `
            'start-local sent a product-scoped request instead of the Provider canary.'
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
                'AI_DEGRADED:PROBE_ROUTE_BYPASSED') `
            "Fallback fixture did not report the safe degraded category. Output: $($fallbackResult.Output)"
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
    Assert-True ($launcherText -match '\[switch\]\$EnableGeneralAi') `
        'Unified launcher must expose explicit General AI opt-in.'
    foreach ($generalAiSetting in @(
        'PORTFOLIO_MODEL_OP_TURN_INTERPRETATION_MODE',
        'PORTFOLIO_MODEL_OP_TURN_INTERPRETATION_PROVIDER_REF',
        'PORTFOLIO_MODEL_OP_TURN_INTERPRETATION_SCHEMA_VERSION',
        'PORTFOLIO_MODEL_OP_GENERAL_MODE',
        'PORTFOLIO_MODEL_OP_GENERAL_PROVIDER_REF',
        'PORTFOLIO_MODEL_OP_GENERAL_SCHEMA_VERSION'
    )) {
        Assert-True ($launcherText -match [regex]::Escape($generalAiSetting)) `
            "General AI opt-in must configure $generalAiSetting."
    }
    Assert-True ($launcherText -match [regex]::Escape("'goal.proposal.v1'")) `
        'Goal Interpretation must declare the production Codec schema.'
    Assert-True ($launcherText -match [regex]::Escape("'general.draft.v1'")) `
        'General Knowledge must declare the production Codec schema.'
    Assert-True ($launcherText -match `
            '(?s)PORTFOLIO_MODEL_OP_TURN_INTERPRETATION_PROVIDER_REF.{0,120}selectedProvider') `
        'Goal Interpretation providerRef must use the selected Transport Provider.'
    Assert-True ($launcherText -match `
            'PORTFOLIO_MODEL_OP_GENERAL_PROVIDER_REF.*selectedProvider') `
        'General Knowledge providerRef must use the selected Transport Provider.'
    Assert-True ($launcherText -notmatch 'conversational-default') `
        'Unified launcher must not use the ambiguous Provider alias.'
    Assert-True ($launcherText -notmatch `
            'PORTFOLIO_MODEL_OP_ROUTING|PORTFOLIO_SEMANTIC_CLASSIFIER') `
        'Unified launcher must not write retired routing/classifier keys.'
    Assert-True ($launcherText -match `
            '\-File\s+\$postgresTool\s+check-context\s+\-EnvFile') `
        'Standard local mode must call only PostgreSQL status/readiness.'
    Assert-True ($launcherText -notmatch `
            "@\('(start|bootstrap|stop|reset|import-public|activate-public)'") `
        'Unified launcher must never manage the PostgreSQL lifecycle.'

    $modelExampleText = Get-Content -LiteralPath $modelExample -Raw
    Assert-True ($modelExampleText -notmatch `
            'PORTFOLIO_MODEL_EXPRESSION|PORTFOLIO_MODEL_TIMEOUT|PORTFOLIO_MODEL_OP_ROUTING') `
        'The current model example must not advertise retired aliases.'
    Assert-True ($modelExampleText -match `
            'PORTFOLIO_GOAL_INTERPRETATION_MAX_OUTPUT_TOKENS') `
        'The model example must advertise the current Goal output budget.'

    $postgresExampleText = Get-Content -LiteralPath $postgresExample -Raw
    foreach ($keyName in @(
        'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID',
        'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY',
        'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID',
        'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY'
    )) {
        Assert-True ($postgresExampleText -match [regex]::Escape($keyName)) `
            "The PostgreSQL example must include stable $keyName."
    }

    $localProfileText = Get-Content -LiteralPath $localProfile -Raw
    Assert-True ($localProfileText -match `
            'PORTFOLIO_CONVERSATION_CONTEXT_MODE:POSTGRESQL') `
        'The local profile must default Agent State to PostgreSQL.'
    $prodProfileText = Get-Content -LiteralPath $prodProfile -Raw
    Assert-True ($prodProfileText -match '(?m)^\s+mode:\s+POSTGRESQL\s*$') `
        'The production profile must require PostgreSQL Agent State.'
    $testLocalProfileText = Get-Content -LiteralPath $testLocalProfile -Raw
    Assert-True ($testLocalProfileText.Contains('mode: IN_MEMORY')) `
        'Spring tests must select IN_MEMORY explicitly instead of depending on a developer database.'

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
