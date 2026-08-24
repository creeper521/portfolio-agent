param(
    [string]$SecretsFile = '',
    [ValidateSet('POSTGRESQL', 'IN_MEMORY', 'DISABLED')]
    [string]$ContextMode = 'POSTGRESQL',
    [string]$PostgresEnvFile = '',
    [switch]$CheckOnly,
    [ValidateRange(1, 65535)]
    [int]$BackendPort = 8080,
    [ValidateRange(1, 65535)]
    [int]$FrontendPort = 5173,
    [string]$MavenExecutable = '',
    [string]$NpmExecutable = 'npm.cmd',
    [switch]$EnableGeneralAi,
    [switch]$ExitAfterProbe,
    [ValidateRange(1, 300)]
    [int]$ReadinessTimeoutSeconds = 60,
    [ValidateSet('', 'BACKEND_MODEL', 'BACKEND_FALLBACK')]
    [string]$BackendFixtureMode = '',
    [switch]$FrontendFixture,
    [string]$LogDirectory = '',
    [switch]$FollowLogs
)

$ErrorActionPreference = 'Stop'
$script:repositoryRoot = Split-Path -Parent $PSScriptRoot
$script:processReaders = @{}
$script:allowedNames = @(
    'PORTFOLIO_MODEL_ENABLED',
    'PORTFOLIO_MODEL_DATA_POLICY_APPROVED',
    'PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED',
    'PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED',
    'PORTFOLIO_MODEL_PROVIDER',
    'PORTFOLIO_AGENT_DEEPSEEK_API_KEY',
    'PORTFOLIO_AGENT_GLM_API_KEY',
    'PORTFOLIO_MODEL_MAX_TOKENS',
    'PORTFOLIO_GOAL_INTERPRETATION_MAX_OUTPUT_TOKENS',
    'PORTFOLIO_CONVERSATION_MAX_INPUT_TOKENS'
)
$script:generalAiEnvironment = @{
    PORTFOLIO_MODEL_OP_TURN_INTERPRETATION_MODE = 'ENABLED'
    PORTFOLIO_MODEL_OP_TURN_INTERPRETATION_PROVIDER_REF = ''
    PORTFOLIO_MODEL_OP_TURN_INTERPRETATION_SCHEMA_VERSION = 'goal.proposal.v2'
    PORTFOLIO_MODEL_OP_GENERAL_MODE = 'ENABLED'
    PORTFOLIO_MODEL_OP_GENERAL_PROVIDER_REF = ''
    PORTFOLIO_MODEL_OP_GENERAL_SCHEMA_VERSION = 'general.draft.v1'
}
$script:contextEnvironmentNames = @(
    'PORTFOLIO_CONVERSATION_CONTEXT_MODE',
    'PORTFOLIO_CONTEXT_DATABASE_URL',
    'PORTFOLIO_CONTEXT_DATABASE_USERNAME',
    'PORTFOLIO_CONTEXT_DATABASE_PASSWORD',
    'PORTFOLIO_CONTEXT_DATABASE_SCHEMA',
    'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID',
    'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY',
    'PORTFOLIO_CONTEXT_PREVIOUS_TOKEN_KEY_ID',
    'PORTFOLIO_CONTEXT_PREVIOUS_TOKEN_KEY',
    'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID',
    'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY',
    'PORTFOLIO_CONTEXT_PREVIOUS_PAYLOAD_KEY_ID',
    'PORTFOLIO_CONTEXT_PREVIOUS_PAYLOAD_KEY'
)
$script:managedEnvironmentNames = @(
    $script:allowedNames + @($script:generalAiEnvironment.Keys) +
        $script:contextEnvironmentNames
)

function Stop-WithCode([string]$Code) {
    throw $Code
}

function Test-IsChildPath([string]$Parent, [string]$Candidate) {
    $prefix = [System.IO.Path]::GetFullPath($Parent).TrimEnd('\') + '\'
    return [System.IO.Path]::GetFullPath($Candidate).StartsWith(
        $prefix,
        [System.StringComparison]::OrdinalIgnoreCase
    )
}

function Read-LocalSecrets([string]$Path) {
    if (-not [System.IO.Path]::IsPathRooted($Path) -or
            -not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Stop-WithCode 'LOCAL_CONFIG_FILE_INVALID'
    }
    $resolved = (Resolve-Path -LiteralPath $Path).Path
    if (Test-IsChildPath $script:repositoryRoot $resolved) {
        Stop-WithCode 'LOCAL_CONFIG_MUST_BE_OUTSIDE_REPOSITORY'
    }

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $resolved -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
            continue
        }
        $separator = $trimmed.IndexOf('=')
        if ($separator -le 0) {
            Stop-WithCode 'LOCAL_CONFIG_FORMAT_INVALID'
        }
        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1)
        if ($name -notin $script:allowedNames -or
                $values.ContainsKey($name)) {
            Stop-WithCode 'LOCAL_CONFIG_FIELD_INVALID'
        }
        if ($value -match '(`|\$\(|\$\{|;|\||&&)') {
            Stop-WithCode 'LOCAL_CONFIG_VALUE_INVALID'
        }
        $values[$name] = $value
    }
    return $values
}

function Assert-TrueFlag([hashtable]$Values, [string]$Name) {
    if (-not $Values.ContainsKey($Name) -or
            -not [string]::Equals(
                [string]$Values[$Name],
                'true',
                [System.StringComparison]::OrdinalIgnoreCase
            )) {
        Stop-WithCode "LOCAL_CONFIG_REQUIRED_FLAG_MISSING:$Name"
    }
}

function Assert-LocalConfiguration([hashtable]$Values) {
    foreach ($name in @(
        'PORTFOLIO_MODEL_ENABLED',
        'PORTFOLIO_MODEL_DATA_POLICY_APPROVED',
        'PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED',
        'PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED'
    )) {
        Assert-TrueFlag $Values $name
    }

    $provider = [string]$Values.PORTFOLIO_MODEL_PROVIDER
    $keyName = switch ($provider) {
        'DEEPSEEK_V4_FLASH' { 'PORTFOLIO_AGENT_DEEPSEEK_API_KEY' }
        'GLM_4_7' { 'PORTFOLIO_AGENT_GLM_API_KEY' }
        default { Stop-WithCode 'LOCAL_CONFIG_PROVIDER_INVALID' }
    }
    if (-not $Values.ContainsKey($keyName) -or
            [string]::IsNullOrWhiteSpace([string]$Values[$keyName])) {
        Stop-WithCode "LOCAL_CONFIG_PROVIDER_KEY_MISSING:$keyName"
    }
}

function Resolve-PostgresEnvironmentFile {
    $path = if ([string]::IsNullOrWhiteSpace($PostgresEnvFile)) {
        Join-Path $script:repositoryRoot '.env.postgres.local'
    }
    else {
        $PostgresEnvFile
    }
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Stop-WithCode (
            'LOCAL_POSTGRES_CONFIGURATION_INVALID' + [Environment]::NewLine +
            'Fix: create or update "' + $path +
            '" from .env.postgres.example'
        )
    }
    return (Resolve-Path -LiteralPath $path).Path
}

function Assert-PostgresReady([string]$ResolvedEnvFile) {
    $postgresTool = Join-Path $PSScriptRoot 'postgres-local.ps1'
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $postgresTool check-context -EnvFile $ResolvedEnvFile `
            -TimeoutSeconds $ReadinessTimeoutSeconds 2>&1 |
            Out-String)
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0) {
        $recovery = if ($output -match (
                'POSTGRES_LOCAL_(ENV_FILE_INVALID|ENV_FIELD_INVALID|' +
                'REQUIRED_ENV_MISSING|PORT_INVALID|IDENTIFIER_INVALID|' +
                'IDENTIFIERS_NOT_DISTINCT|PASSWORD_UNSAFE|' +
                'CONTEXT_SCHEMA_FIXED|' +
                'CRYPTO_KEY_INVALID|CRYPTO_KEY_ID_INVALID|' +
                'CRYPTO_KEYS_NOT_DISTINCT)')) {
            'Fix: update "' + $ResolvedEnvFile +
            '" from .env.postgres.example'
        }
        elseif ($output -match (
                'POSTGRES_LOCAL_(CONTEXT_SCHEMA_UNAVAILABLE|' +
                'DATABASE_QUERY_FAILED|DATABASE_UNAVAILABLE)')) {
            'Run: powershell.exe -NoProfile -ExecutionPolicy Bypass ' +
            '-File scripts\postgres-local.ps1 bootstrap -EnvFile "' +
            $ResolvedEnvFile + '"'
        }
        else {
            'Run: powershell.exe -NoProfile -ExecutionPolicy Bypass ' +
            '-File scripts\postgres-local.ps1 start -EnvFile "' +
            $ResolvedEnvFile + '"'
        }
        Stop-WithCode (
            'LOCAL_POSTGRES_NOT_READY' + [Environment]::NewLine +
            $recovery
        )
    }
    Write-Host 'LOCAL_POSTGRES_READY'
}

function Read-PostgresRuntimeSettings([string]$ResolvedEnvFile) {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $ResolvedEnvFile -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
            continue
        }
        $separator = $trimmed.IndexOf('=')
        if ($separator -le 0) {
            Stop-WithCode 'LOCAL_POSTGRES_ENV_FILE_INVALID'
        }
        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1)
        if ($values.ContainsKey($name)) {
            Stop-WithCode 'LOCAL_POSTGRES_ENV_FILE_INVALID'
        }
        $values[$name] = $value
    }
    foreach ($name in @(
        'PORTFOLIO_POSTGRES_PORT',
        'PORTFOLIO_CONTEXT_DATABASE_NAME',
        'PORTFOLIO_CONTEXT_DATABASE_USERNAME',
        'PORTFOLIO_CONTEXT_DATABASE_PASSWORD',
        'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID',
        'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY',
        'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID',
        'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY'
    )) {
        if (-not $values.ContainsKey($name) -or
                [string]::IsNullOrWhiteSpace([string]$values[$name])) {
            Stop-WithCode "LOCAL_POSTGRES_RUNTIME_FIELD_MISSING:$name"
        }
    }
    $port = [string]$values.PORTFOLIO_POSTGRES_PORT
    $database = [string]$values.PORTFOLIO_CONTEXT_DATABASE_NAME
    $runtime = @{
        PORTFOLIO_CONVERSATION_CONTEXT_MODE = 'POSTGRESQL'
        PORTFOLIO_CONTEXT_DATABASE_URL =
            "jdbc:postgresql://127.0.0.1:$port/$database"
        PORTFOLIO_CONTEXT_DATABASE_USERNAME =
            [string]$values.PORTFOLIO_CONTEXT_DATABASE_USERNAME
        PORTFOLIO_CONTEXT_DATABASE_PASSWORD =
            [string]$values.PORTFOLIO_CONTEXT_DATABASE_PASSWORD
        PORTFOLIO_CONTEXT_DATABASE_SCHEMA = if (
                $values.ContainsKey('PORTFOLIO_CONTEXT_DATABASE_SCHEMA')) {
            [string]$values.PORTFOLIO_CONTEXT_DATABASE_SCHEMA
        }
        else {
            'agent_context'
        }
    }
    foreach ($name in $script:contextEnvironmentNames) {
        if ($name -eq 'PORTFOLIO_CONVERSATION_CONTEXT_MODE' -or
                $name -eq 'PORTFOLIO_CONTEXT_DATABASE_URL' -or
                $name -eq 'PORTFOLIO_CONTEXT_DATABASE_USERNAME' -or
                $name -eq 'PORTFOLIO_CONTEXT_DATABASE_PASSWORD' -or
                $name -eq 'PORTFOLIO_CONTEXT_DATABASE_SCHEMA') {
            continue
        }
        if ($values.ContainsKey($name)) {
            $runtime[$name] = [string]$values[$name]
        }
    }
    return $runtime
}

function Resolve-RuntimeSettings {
    $settings = @{
        PORTFOLIO_CONVERSATION_CONTEXT_MODE = $ContextMode
    }
    if ($ContextMode -eq 'POSTGRESQL') {
        $resolvedPostgresEnv = Resolve-PostgresEnvironmentFile
        Assert-PostgresReady $resolvedPostgresEnv
        foreach ($entry in (Read-PostgresRuntimeSettings $resolvedPostgresEnv).
                GetEnumerator()) {
            $settings[$entry.Key] = $entry.Value
        }
    }
    if ($EnableGeneralAi) {
        if ([string]::IsNullOrWhiteSpace($SecretsFile)) {
            Stop-WithCode 'LOCAL_MODEL_SECRETS_REQUIRED'
        }
        $modelSettings = Read-LocalSecrets $SecretsFile
        Assert-LocalConfiguration $modelSettings
        foreach ($entry in $modelSettings.GetEnumerator()) {
            $settings[$entry.Key] = $entry.Value
        }
        foreach ($entry in $script:generalAiEnvironment.GetEnumerator()) {
            $settings[$entry.Key] = $entry.Value
        }
        $selectedProvider = [string]$modelSettings.PORTFOLIO_MODEL_PROVIDER
        $settings['PORTFOLIO_MODEL_OP_TURN_INTERPRETATION_PROVIDER_REF'] =
                $selectedProvider
        $settings['PORTFOLIO_MODEL_OP_GENERAL_PROVIDER_REF'] = $selectedProvider
    }
    return $settings
}

function Resolve-CommandPath([string]$Command, [string]$FailureCode) {
    $resolved = Get-Command $Command -ErrorAction SilentlyContinue
    if ($null -eq $resolved) {
        Stop-WithCode $FailureCode
    }
    return $resolved.Source
}

function Resolve-Maven {
    if (-not [string]::IsNullOrWhiteSpace($MavenExecutable)) {
        if (Test-Path -LiteralPath $MavenExecutable -PathType Leaf) {
            return (Resolve-Path -LiteralPath $MavenExecutable).Path
        }
        return Resolve-CommandPath $MavenExecutable 'LOCAL_MAVEN_MISSING'
    }
    foreach ($candidate in @(
        'mvn.cmd',
        'mvn',
        'C:\tools\apache-maven-3.9.9\bin\mvn.cmd'
    )) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
        $resolved = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($null -ne $resolved) {
            return $resolved.Source
        }
    }
    Stop-WithCode 'LOCAL_MAVEN_MISSING'
}

function Assert-Java21([string]$JavaExecutable) {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $versionText = (& $JavaExecutable -version 2>&1 | Out-String)
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0 -or
            $versionText -notmatch 'version "(?:1\.)?21(?:[.\-_"]|$)') {
        Stop-WithCode 'LOCAL_JAVA_21_REQUIRED'
    }
}

function Assert-Toolchain {
    $java = Resolve-CommandPath 'java.exe' 'LOCAL_JAVA_MISSING'
    Assert-Java21 $java
    $maven = Resolve-Maven
    $node = Resolve-CommandPath 'node.exe' 'LOCAL_NODE_MISSING'
    $npm = Resolve-CommandPath $NpmExecutable 'LOCAL_NPM_MISSING'
    $frontendDependencies = Join-Path $script:repositoryRoot `
        'frontend\node_modules'
    if (-not (Test-Path -LiteralPath $frontendDependencies `
            -PathType Container)) {
        Stop-WithCode 'LOCAL_FRONTEND_DEPENDENCIES_MISSING'
    }
    return @{
        Java = $java
        Maven = $maven
        Node = $node
        Npm = $npm
    }
}

function Assert-PortAvailable([int]$Port) {
    $listener = [System.Net.NetworkInformation.IPGlobalProperties]::
        GetIPGlobalProperties().GetActiveTcpListeners() |
        Where-Object { $_.Port -eq $Port } |
        Select-Object -First 1
    if ($null -ne $listener) {
        Stop-WithCode "LOCAL_PORT_OCCUPIED:$Port"
    }
}

function Set-TemporaryProcessEnvironment([hashtable]$Values) {
    $snapshot = @{}
    foreach ($name in $script:managedEnvironmentNames) {
        $snapshot[$name] = [Environment]::GetEnvironmentVariable(
            $name,
            [EnvironmentVariableTarget]::Process
        )
        $value = if ($Values.ContainsKey($name)) {
            [string]$Values[$name]
        }
        else {
            $null
        }
        [Environment]::SetEnvironmentVariable(
            $name,
            $value,
            [EnvironmentVariableTarget]::Process
        )
    }
    return $snapshot
}

function Restore-ProcessEnvironment([hashtable]$Snapshot) {
    foreach ($name in $script:managedEnvironmentNames) {
        [Environment]::SetEnvironmentVariable(
            $name,
            $Snapshot[$name],
            [EnvironmentVariableTarget]::Process
        )
    }
}

function Start-OwnedProcess(
    [string]$Executable,
    [string[]]$Arguments,
    [hashtable]$EnvironmentValues,
    [pscustomobject]$LogRouter,
    [string]$StandardOutputStream,
    [string]$StandardErrorStream
) {
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $Executable
    $startInfo.Arguments = (($Arguments | ForEach-Object {
        '"' + ([string]$_).Replace('"', '\"') + '"'
    }) -join ' ')
    $startInfo.WorkingDirectory = $script:repositoryRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $environmentSnapshot = Set-TemporaryProcessEnvironment $EnvironmentValues
    $auxiliaryNames = @(
        'PORTFOLIO_DIAGNOSTICS_FRONTEND_INGEST_ENABLED',
        'PORTFOLIO_FRONTEND_LOG_OWNER'
    )
    $auxiliarySnapshots = @{}
    foreach ($auxiliaryName in $auxiliaryNames) {
        $auxiliarySnapshots[$auxiliaryName] = [Environment]::GetEnvironmentVariable(
            $auxiliaryName,
            [EnvironmentVariableTarget]::Process
        )
    }
    try {
        foreach ($auxiliaryName in $auxiliaryNames) {
            $auxiliaryValue = if ($EnvironmentValues.ContainsKey($auxiliaryName)) {
                [string]$EnvironmentValues[$auxiliaryName]
            } else {
                $null
            }
            [Environment]::SetEnvironmentVariable(
                $auxiliaryName,
                $auxiliaryValue,
                [EnvironmentVariableTarget]::Process
            )
        }
        if (-not $process.Start()) {
            Stop-WithCode 'LOCAL_CHILD_START_FAILED'
        }
    } finally {
        Restore-ProcessEnvironment $environmentSnapshot
        foreach ($auxiliaryName in $auxiliaryNames) {
            [Environment]::SetEnvironmentVariable(
                $auxiliaryName,
                $auxiliarySnapshots[$auxiliaryName],
                [EnvironmentVariableTarget]::Process
            )
        }
    }

    $readerScript = {
        param($Reader, $Router, $Stream, $ModulePath)
        Import-Module $ModulePath -Force -DisableNameChecking
        while ($null -ne ($line = $Reader.ReadLine())) {
            if ($null -ne $Router) {
                Submit-LocalLogLine -Router $Router -Stream $Stream -Line $line
            }
        }
    }
    $modulePath = Join-Path $PSScriptRoot 'logging\LocalLogRouter.psm1'
    $readers = @()
    foreach ($readerDefinition in @(
        @{ Reader = $process.StandardOutput; Stream = $StandardOutputStream },
        @{ Reader = $process.StandardError; Stream = $StandardErrorStream }
    )) {
        $readerPowerShell = [powershell]::Create()
        [void]$readerPowerShell.AddScript($readerScript).
            AddArgument($readerDefinition.Reader).
            AddArgument($LogRouter).
            AddArgument($readerDefinition.Stream).
            AddArgument($modulePath)
        $readers += [pscustomobject]@{
            PowerShell = $readerPowerShell
            AsyncResult = $readerPowerShell.BeginInvoke()
        }
    }
    $script:processReaders[$process.Id] = $readers
    return $process
}

function Stop-OwnedProcess([System.Diagnostics.Process]$Process) {
    if ($null -eq $Process) {
        return
    }
    $Process.Refresh()
    if (-not $Process.HasExited) {
        $taskkill = Get-Command 'taskkill.exe' -ErrorAction SilentlyContinue
        if ($null -ne $taskkill) {
            $previousErrorActionPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                & $taskkill.Source /PID $Process.Id /T /F 2>&1 | Out-Null
            }
            finally {
                $ErrorActionPreference = $previousErrorActionPreference
            }
        }
        $Process.Refresh()
        if (-not $Process.HasExited) {
            Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
        }
    }
    [void]$Process.WaitForExit(5000)
    $Process.Refresh()
    if (-not $Process.HasExited) {
        Stop-WithCode "LOCAL_CHILD_CLEANUP_FAILED:$($Process.Id)"
    }
    if ($script:processReaders.ContainsKey($Process.Id)) {
        foreach ($reader in @($script:processReaders[$Process.Id])) {
            if (-not $reader.AsyncResult.AsyncWaitHandle.WaitOne(5000)) {
                $reader.PowerShell.Stop()
            }
            try {
                [void]$reader.PowerShell.EndInvoke($reader.AsyncResult)
            }
            finally {
                $reader.PowerShell.Dispose()
            }
        }
        $script:processReaders.Remove($Process.Id)
    }
}

function Wait-ForHttp(
    [string]$Uri,
    [System.Diagnostics.Process]$Process,
    [int]$TimeoutSeconds
) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $Process.Refresh()
        if ($Process.HasExited) {
            Stop-WithCode 'LOCAL_CHILD_EXITED_BEFORE_READY'
        }
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri `
                -TimeoutSec 2
            if ($response.StatusCode -eq 200) {
                return $response
            }
        }
        catch {
            $Process.Refresh()
            if ($Process.HasExited) {
                Stop-WithCode 'LOCAL_CHILD_EXITED_BEFORE_READY'
            }
        }
        Start-Sleep -Milliseconds 200
    }
    Stop-WithCode 'LOCAL_READINESS_TIMEOUT'
}

function Invoke-ProviderProbe(
    [string]$BackendBaseUrl,
    [string]$ContentVersion,
    [hashtable]$Settings
) {
    $environmentSnapshot = Set-TemporaryProcessEnvironment $Settings
    try {
        $probeOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File (Join-Path $PSScriptRoot `
                'provider-probe\invoke-live-provider-probe.ps1') `
            -BackendBaseUrl $BackendBaseUrl `
            -ExpectedContentVersion $ContentVersion `
            -TimeoutSeconds $ReadinessTimeoutSeconds 2>&1 | Out-String).Trim()
    }
    finally {
        Restore-ProcessEnvironment $environmentSnapshot
    }
    if ($probeOutput -eq 'LIVE_PROVIDER_CONNECTED') {
        return 'CONNECTED'
    }
    if ($probeOutput -match '^LIVE_PROVIDER_DEGRADED:(?<category>[A-Z0-9_]+)$') {
        return $Matches.category
    }
    return 'PROVIDER_RESPONSE_INVALID'
}

try {
    $testMode = [string]::Equals(
        [Environment]::GetEnvironmentVariable(
            'PORTFOLIO_START_LOCAL_TEST_MODE',
            [EnvironmentVariableTarget]::Process
        ),
        'true',
        [System.StringComparison]::OrdinalIgnoreCase
    )
    if (($BackendFixtureMode -ne '' -or $FrontendFixture) -and
            -not $testMode) {
        Stop-WithCode 'LOCAL_TEST_SEAM_FORBIDDEN'
    }
    if ($BackendPort -eq $FrontendPort) {
        Stop-WithCode "LOCAL_PORT_OCCUPIED:$BackendPort"
    }
    $settings = Resolve-RuntimeSettings
    $toolchain = Assert-Toolchain
    Assert-PortAvailable $BackendPort
    Assert-PortAvailable $FrontendPort
    if ($EnableGeneralAi) {
        Write-Output (
            'LOCAL_CONFIG_VALID provider=' +
            [string]$settings.PORTFOLIO_MODEL_PROVIDER +
            ' checks=6'
        )
    }
    $modelMode = if ($EnableGeneralAi) { 'ENABLED' } else { 'DISABLED' }
    Write-Output "LOCAL_RUNTIME_VALID mode=$ContextMode model=$modelMode"
    Write-Output 'LOCAL_PREFLIGHT_VALID java=21 maven=ready node=ready frontendDependencies=ready ports=ready'
    if ($CheckOnly) {
        exit 0
    }

    if ([string]::IsNullOrWhiteSpace($LogDirectory)) {
        $LogDirectory = Join-Path $script:repositoryRoot 'logs'
    }
    $LogDirectory = [System.IO.Path]::GetFullPath($LogDirectory)
    Import-Module (Join-Path $PSScriptRoot 'logging\LocalLogRouter.psm1') `
        -Force `
        -DisableNameChecking
    $logRouter = $null
    try {
        $logRouter = New-LocalLogRouter `
            -RepositoryRoot $script:repositoryRoot `
            -LogDirectory $LogDirectory
        Invoke-LocalLogMaintenance -Router $logRouter
        Submit-LocalLogLine -Router $logRouter -Stream LAUNCHER `
            -Line 'INFO event.name=local.session.started'
        Write-Output "LOG_DIRECTORY path=$LogDirectory"
        Write-Output (
            'LOG_WATCH_COMMAND command=powershell.exe -NoProfile ' +
            '-ExecutionPolicy Bypass -File scripts\watch-local-logs.ps1'
        )
    }
    catch {
        Write-Output 'LOG_ROUTER_DEGRADED:INITIALIZATION_FAILED'
    }

    $fixtureScript = Join-Path $PSScriptRoot `
        'test-fixtures\start-local-fake-server.ps1'
    $backendExecutable = if ($BackendFixtureMode -ne '') {
        'powershell.exe'
    }
    else {
        [string]$toolchain.Maven
    }
    $backendArguments = if ($BackendFixtureMode -ne '') {
        @(
            '-NoProfile',
            '-ExecutionPolicy', 'Bypass',
            '-File', $fixtureScript,
            '-Port', "$BackendPort",
            '-Mode', $BackendFixtureMode
        )
    }
    else {
        @(
            '-f', (Join-Path $script:repositoryRoot 'backend\pom.xml'),
            'spring-boot:run',
            '-Dspring-boot.run.profiles=local',
            "-Dspring-boot.run.arguments=--server.port=$BackendPort"
        )
    }

    $backend = $null
    $frontend = $null
    try {
        $backendEnvironment = @{}
        foreach ($entry in $settings.GetEnumerator()) {
            $backendEnvironment[$entry.Key] = $entry.Value
        }
        $backendEnvironment.PORTFOLIO_DIAGNOSTICS_FRONTEND_INGEST_ENABLED = 'true'
        $backendEnvironment.PORTFOLIO_LOG_DIRECTORY = $LogDirectory
        $backend = Start-OwnedProcess $backendExecutable `
            $backendArguments `
            $backendEnvironment `
            $logRouter `
            'BACKEND_STDOUT' `
            'BACKEND_STDERR'
        $backendBaseUrl = "http://127.0.0.1:$BackendPort"
        $publicContentResponse = Wait-ForHttp `
            "$backendBaseUrl/api/portfolio" `
            $backend `
            $ReadinessTimeoutSeconds
        try {
            $publicContent = $publicContentResponse.Content | ConvertFrom-Json
        }
        catch {
            Stop-WithCode 'LOCAL_PUBLIC_CONTENT_INVALID'
        }
        if ([string]::IsNullOrWhiteSpace(
                [string]$publicContent.contentVersion)) {
            Stop-WithCode 'LOCAL_PUBLIC_CONTENT_INVALID'
        }

        $frontendExecutable = if ($FrontendFixture) {
            'powershell.exe'
        }
        else {
            [string]$toolchain.Npm
        }
        $frontendArguments = if ($FrontendFixture) {
            @(
                '-NoProfile',
                '-ExecutionPolicy', 'Bypass',
                '-File', $fixtureScript,
                '-Port', "$FrontendPort",
                '-Mode', 'FRONTEND'
            )
        }
        else {
            @(
                '--prefix', (Join-Path $script:repositoryRoot 'frontend'),
                'run', 'dev', '--',
                '--host', '127.0.0.1',
                '--port', "$FrontendPort",
                '--strictPort'
            )
        }
        $frontendEnvironment = @{
            PORTFOLIO_FRONTEND_LOG_OWNER = 'UNIFIED'
        }
        $frontend = Start-OwnedProcess $frontendExecutable `
            $frontendArguments `
            $frontendEnvironment `
            $logRouter `
            'VITE_STDOUT' `
            'VITE_STDERR'
        $frontendBaseUrl = "http://127.0.0.1:$FrontendPort"
        [void](Wait-ForHttp $frontendBaseUrl $frontend `
            $ReadinessTimeoutSeconds)

        if ($EnableGeneralAi) {
            $probeStatus = Invoke-ProviderProbe $backendBaseUrl `
                ([string]$publicContent.contentVersion) `
                $settings
            if ($probeStatus -eq 'CONNECTED') {
                Write-Output (
                    'AI_CONNECTED provider=' +
                    [string]$settings.PORTFOLIO_MODEL_PROVIDER +
                    " backend=$backendBaseUrl frontend=$frontendBaseUrl"
                )
            }
            else {
                Write-Output "AI_DEGRADED:$probeStatus"
            }
        }
        else {
            Write-Output 'AI_DISABLED'
        }

        if (-not $ExitAfterProbe -and $FollowLogs) {
            & (Join-Path $PSScriptRoot 'watch-local-logs.ps1') `
                -LogDirectory $LogDirectory
        }
        elseif (-not $ExitAfterProbe) {
            while ($true) {
                Start-Sleep -Milliseconds 250
                $backend.Refresh()
                $frontend.Refresh()
                if ($backend.HasExited) {
                    Stop-WithCode 'LOCAL_CHILD_EXITED:BACKEND'
                }
                if ($frontend.HasExited) {
                    Stop-WithCode 'LOCAL_CHILD_EXITED:FRONTEND'
                }
            }
        }
    }
    finally {
        Stop-OwnedProcess $frontend
        Stop-OwnedProcess $backend
        if ($null -ne $logRouter) {
            try {
                Submit-LocalLogLine -Router $logRouter -Stream LAUNCHER `
                    -Line 'INFO event.name=local.session.stopped'
                Stop-LocalLogRouter -Router $logRouter
            }
            catch {
                Write-Output 'LOG_ROUTER_DEGRADED:CLEANUP_FAILED'
            }
        }
    }
}
catch {
    [Console]::Error.WriteLine([string]$_.Exception.Message)
    if ($testMode) {
        [Console]::Error.WriteLine(
            "LOCAL_TEST_FAILURE_POSITION line=$($_.InvocationInfo.ScriptLineNumber)"
        )
    }
    exit 1
}
