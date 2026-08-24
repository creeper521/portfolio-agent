param(
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidateSet(
        'start', 'bootstrap', 'status', 'check-context', 'verify', 'connections', 'stop', 'reset',
        'verify-public-bundle', 'import-public', 'activate-public',
        'scan-markdown', 'import-markdown', 'retry-markdown')]
    [string]$Command,
    [string]$EnvFile,
    [string]$Confirm,
    [string]$ReleaseId,
    [string]$ConfirmReleaseId,
    [string]$Root,
    [switch]$DryRun,
    [switch]$ImportPublic,
    [switch]$ActivatePublic,
    [int]$TimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'
$script:repositoryRoot = Split-Path -Parent $PSScriptRoot
$script:composeFile = Join-Path $script:repositoryRoot 'compose.postgres.local.yml'
$script:composeProject = 'portfolio-postgres-local'
$script:volumeName = 'portfolio-postgres-local_postgres_data'
$script:bundleRoot = Join-Path $script:repositoryRoot `
    'backend\src\main\resources\public-data\bundle'
$script:backendRoot = Join-Path $script:repositoryRoot 'backend'
$script:cliClassesPath = Join-Path $script:backendRoot 'target\classes'
$script:cliClasspathPath = Join-Path $script:backendRoot `
    'target\postgres-local-runtime-classpath.txt'
$script:cliMarkerPath = Join-Path $script:backendRoot `
    'target\postgres-local-cli.ready'

function Stop-WithCode([string]$Code) {
    throw $Code
}

function Resolve-ExistingFile([string]$Path, [string]$FailureCode) {
    if ([string]::IsNullOrWhiteSpace($Path) -or
            -not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Stop-WithCode $FailureCode
    }
    return (Resolve-Path -LiteralPath $Path).Path
}

function Resolve-ExistingDirectory([string]$Path, [string]$FailureCode) {
    if ([string]::IsNullOrWhiteSpace($Path) -or
            -not (Test-Path -LiteralPath $Path -PathType Container)) {
        Stop-WithCode $FailureCode
    }
    return (Resolve-Path -LiteralPath $Path).Path
}

function Import-LocalEnvironment {
    $path = if ([string]::IsNullOrWhiteSpace($EnvFile)) {
        Join-Path $script:repositoryRoot '.env.postgres.local'
    } else {
        $EnvFile
    }
    $resolved = Resolve-ExistingFile $path 'POSTGRES_LOCAL_ENV_FILE_MISSING'
    $values = @{}
    $allowed = @(
        'PORTFOLIO_POSTGRES_PORT',
        'PORTFOLIO_POSTGRES_ADMIN_USERNAME',
        'PORTFOLIO_POSTGRES_ADMIN_PASSWORD',
        'PORTFOLIO_PUBLIC_DATABASE_NAME',
        'PORTFOLIO_PUBLIC_DATABASE_USERNAME',
        'PORTFOLIO_PUBLIC_DATABASE_PASSWORD',
        'PORTFOLIO_GOVERNANCE_DATABASE_NAME',
        'PORTFOLIO_GOVERNANCE_DATABASE_USERNAME',
        'PORTFOLIO_GOVERNANCE_DATABASE_PASSWORD',
        'PORTFOLIO_CONTEXT_DATABASE_NAME',
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
    foreach ($line in Get-Content -LiteralPath $resolved -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
            continue
        }
        $separator = $trimmed.IndexOf('=')
        if ($separator -le 0) {
            Stop-WithCode 'POSTGRES_LOCAL_ENV_FILE_INVALID'
        }
        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1)
        if ($name -notin $allowed -or
                $values.ContainsKey($name)) {
            Stop-WithCode 'POSTGRES_LOCAL_ENV_FIELD_INVALID'
        }
        $values[$name] = $value
    }
    $required = @(
        'PORTFOLIO_POSTGRES_PORT',
        'PORTFOLIO_POSTGRES_ADMIN_USERNAME',
        'PORTFOLIO_POSTGRES_ADMIN_PASSWORD',
        'PORTFOLIO_PUBLIC_DATABASE_NAME',
        'PORTFOLIO_PUBLIC_DATABASE_USERNAME',
        'PORTFOLIO_PUBLIC_DATABASE_PASSWORD',
        'PORTFOLIO_GOVERNANCE_DATABASE_NAME',
        'PORTFOLIO_GOVERNANCE_DATABASE_USERNAME',
        'PORTFOLIO_GOVERNANCE_DATABASE_PASSWORD',
        'PORTFOLIO_CONTEXT_DATABASE_NAME',
        'PORTFOLIO_CONTEXT_DATABASE_USERNAME',
        'PORTFOLIO_CONTEXT_DATABASE_PASSWORD',
        'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID',
        'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY',
        'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID',
        'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY'
    )
    foreach ($name in $required) {
        if (-not $values.ContainsKey($name) -or
                [string]::IsNullOrWhiteSpace([string]$values[$name])) {
            Stop-WithCode 'POSTGRES_LOCAL_REQUIRED_ENV_MISSING'
        }
    }
    if ([string]$values.PORTFOLIO_POSTGRES_PORT -notmatch '^[0-9]{2,5}$' -or
            [int]$values.PORTFOLIO_POSTGRES_PORT -gt 65535) {
        Stop-WithCode 'POSTGRES_LOCAL_PORT_INVALID'
    }
    foreach ($name in @(
            'PORTFOLIO_POSTGRES_ADMIN_USERNAME',
            'PORTFOLIO_PUBLIC_DATABASE_NAME',
            'PORTFOLIO_PUBLIC_DATABASE_USERNAME',
            'PORTFOLIO_GOVERNANCE_DATABASE_NAME',
            'PORTFOLIO_GOVERNANCE_DATABASE_USERNAME',
            'PORTFOLIO_CONTEXT_DATABASE_NAME',
            'PORTFOLIO_CONTEXT_DATABASE_USERNAME')) {
        if ([string]$values[$name] -notmatch '^[a-z_][a-z0-9_]{0,62}$') {
            Stop-WithCode 'POSTGRES_LOCAL_IDENTIFIER_INVALID'
        }
    }
    if ($values.PORTFOLIO_PUBLIC_DATABASE_NAME -eq
            $values.PORTFOLIO_GOVERNANCE_DATABASE_NAME -or
            $values.PORTFOLIO_PUBLIC_DATABASE_USERNAME -eq
            $values.PORTFOLIO_GOVERNANCE_DATABASE_USERNAME -or
            $values.PORTFOLIO_PUBLIC_DATABASE_USERNAME -eq
            $values.PORTFOLIO_POSTGRES_ADMIN_USERNAME -or
            $values.PORTFOLIO_GOVERNANCE_DATABASE_USERNAME -eq
            $values.PORTFOLIO_POSTGRES_ADMIN_USERNAME -or
            $values.PORTFOLIO_CONTEXT_DATABASE_NAME -eq
            $values.PORTFOLIO_PUBLIC_DATABASE_NAME -or
            $values.PORTFOLIO_CONTEXT_DATABASE_NAME -eq
            $values.PORTFOLIO_GOVERNANCE_DATABASE_NAME -or
            $values.PORTFOLIO_CONTEXT_DATABASE_USERNAME -eq
            $values.PORTFOLIO_PUBLIC_DATABASE_USERNAME -or
            $values.PORTFOLIO_CONTEXT_DATABASE_USERNAME -eq
            $values.PORTFOLIO_GOVERNANCE_DATABASE_USERNAME -or
            $values.PORTFOLIO_CONTEXT_DATABASE_USERNAME -eq
            $values.PORTFOLIO_POSTGRES_ADMIN_USERNAME) {
        Stop-WithCode 'POSTGRES_LOCAL_IDENTIFIERS_NOT_DISTINCT'
    }
    foreach ($name in @(
            'PORTFOLIO_POSTGRES_ADMIN_PASSWORD',
            'PORTFOLIO_PUBLIC_DATABASE_PASSWORD',
            'PORTFOLIO_GOVERNANCE_DATABASE_PASSWORD',
            'PORTFOLIO_CONTEXT_DATABASE_PASSWORD')) {
        if ([string]$values[$name] -notmatch
                '^[A-Za-z0-9_@%+=:,./!?~-]{12,}$') {
            Stop-WithCode 'POSTGRES_LOCAL_PASSWORD_UNSAFE'
        }
    }
    if ($values.ContainsKey('PORTFOLIO_CONTEXT_DATABASE_SCHEMA') -and
            [string]$values.PORTFOLIO_CONTEXT_DATABASE_SCHEMA -ne
            'agent_context') {
        Stop-WithCode 'POSTGRES_LOCAL_CONTEXT_SCHEMA_FIXED'
    }
    foreach ($name in @(
            'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID',
            'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID')) {
        if ([string]$values[$name] -notmatch '^[A-Za-z0-9._-]{1,64}$') {
            Stop-WithCode 'POSTGRES_LOCAL_CRYPTO_KEY_ID_INVALID'
        }
    }
    $decodedKeys = @{}
    foreach ($name in @(
            'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY',
            'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY')) {
        try {
            $decodedKey = [Convert]::FromBase64String([string]$values[$name])
        }
        catch {
            Stop-WithCode 'POSTGRES_LOCAL_CRYPTO_KEY_INVALID'
        }
        if ($decodedKey.Length -ne 32) {
            Stop-WithCode 'POSTGRES_LOCAL_CRYPTO_KEY_INVALID'
        }
        $decodedKeys[$name] = $decodedKey
    }
    if ($values.PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID -eq
            $values.PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID -or
            [Convert]::ToBase64String(
                $decodedKeys.PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY) -eq
            [Convert]::ToBase64String(
                $decodedKeys.PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY)) {
        Stop-WithCode 'POSTGRES_LOCAL_CRYPTO_KEYS_NOT_DISTINCT'
    }
    foreach ($entry in $values.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable(
            $entry.Key, [string]$entry.Value, 'Process')
    }
    $script:envPath = $resolved
    $script:settings = $values
}

function Get-DockerCommand {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if ($null -eq $docker) {
        Stop-WithCode 'POSTGRES_LOCAL_DOCKER_MISSING'
    }
    return $docker.Source
}

function Invoke-Native(
    [string]$Executable,
    [string[]]$Arguments,
    [string]$FailureCode,
    [switch]$Capture
) {
    if ($Capture) {
        $result = @(& $Executable @Arguments 2>&1 | ForEach-Object { "$_" })
        if ($LASTEXITCODE -ne 0) {
            Stop-WithCode $FailureCode
        }
        return $result
    }
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) {
        Stop-WithCode $FailureCode
    }
}

function Get-ComposeArguments([string[]]$Tail) {
    return @(
        'compose',
        '--env-file', $script:envPath,
        '-f', $script:composeFile,
        '-p', $script:composeProject
    ) + $Tail
}

function Assert-DockerAvailable {
    $script:docker = Get-DockerCommand
    [void](Invoke-Native $script:docker @('compose', 'version') `
        'POSTGRES_LOCAL_COMPOSE_UNAVAILABLE' -Capture)
    [void](Invoke-Native $script:docker @('info', '--format', '{{.ServerVersion}}') `
        'POSTGRES_LOCAL_DOCKER_UNAVAILABLE' -Capture)
}

function Wait-PostgresHealthy {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $ids = Invoke-Native $script:docker `
            (Get-ComposeArguments @('ps', '-q', 'postgres')) `
            'POSTGRES_LOCAL_COMPOSE_STATUS_FAILED' -Capture
        $containerId = @($ids | Where-Object {
                -not [string]::IsNullOrWhiteSpace($_)
            } | Select-Object -First 1)
        if ($containerId.Count -eq 1) {
            $health = @(& $script:docker inspect --format `
                '{{.State.Health.Status}}' $containerId[0] 2>$null)
            if ($LASTEXITCODE -eq 0 -and
                    @($health | Where-Object { $_ -eq 'healthy' }).Count -gt 0) {
                Write-Output 'PostgreSQL: healthy'
                return
            }
        }
        Start-Sleep -Seconds 2
    }
    Stop-WithCode 'POSTGRES_LOCAL_HEALTH_TIMEOUT'
}

function Start-Postgres {
    Assert-DockerAvailable
    Invoke-Native $script:docker `
        (Get-ComposeArguments @('up', '-d', 'postgres')) `
        'POSTGRES_LOCAL_COMPOSE_START_FAILED'
    Wait-PostgresHealthy
}

function Set-ApplicationDatabaseEnvironment {
    $port = [string]$script:settings.PORTFOLIO_POSTGRES_PORT
    $publicDatabase = [string]$script:settings.PORTFOLIO_PUBLIC_DATABASE_NAME
    $governanceDatabase = [string]$script:settings.PORTFOLIO_GOVERNANCE_DATABASE_NAME
    $contextDatabase = [string]$script:settings.PORTFOLIO_CONTEXT_DATABASE_NAME
    $env:PORTFOLIO_PUBLIC_DATABASE_URL =
        "jdbc:postgresql://127.0.0.1:$port/$publicDatabase"
    $env:PORTFOLIO_PUBLIC_DATABASE_USERNAME =
        [string]$script:settings.PORTFOLIO_PUBLIC_DATABASE_USERNAME
    $env:PORTFOLIO_PUBLIC_DATABASE_PASSWORD =
        [string]$script:settings.PORTFOLIO_PUBLIC_DATABASE_PASSWORD
    $env:PORTFOLIO_GOVERNANCE_DATABASE_URL =
        "jdbc:postgresql://127.0.0.1:$port/$governanceDatabase"
    $env:PORTFOLIO_GOVERNANCE_DATABASE_USERNAME =
        [string]$script:settings.PORTFOLIO_GOVERNANCE_DATABASE_USERNAME
    $env:PORTFOLIO_GOVERNANCE_DATABASE_PASSWORD =
        [string]$script:settings.PORTFOLIO_GOVERNANCE_DATABASE_PASSWORD
    $env:PORTFOLIO_CONTEXT_DATABASE_URL =
        "jdbc:postgresql://127.0.0.1:$port/$contextDatabase"
    $env:PORTFOLIO_CONTEXT_DATABASE_USERNAME =
        [string]$script:settings.PORTFOLIO_CONTEXT_DATABASE_USERNAME
    $env:PORTFOLIO_CONTEXT_DATABASE_PASSWORD =
        [string]$script:settings.PORTFOLIO_CONTEXT_DATABASE_PASSWORD
    $env:PORTFOLIO_CONTEXT_DATABASE_SCHEMA = if (
            $script:settings.ContainsKey('PORTFOLIO_CONTEXT_DATABASE_SCHEMA')) {
        [string]$script:settings.PORTFOLIO_CONTEXT_DATABASE_SCHEMA
    }
    else {
        'agent_context'
    }
    $env:PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID =
        [string]$script:settings.PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID
    $env:PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY =
        [string]$script:settings.PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY
    $env:PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID =
        [string]$script:settings.PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID
    $env:PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY =
        [string]$script:settings.PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY
}

function Find-Maven {
    foreach ($name in @('mvn.cmd', 'mvn')) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($null -ne $command) {
            return $command.Source
        }
    }
    $known = 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd'
    if (Test-Path -LiteralPath $known -PathType Leaf) {
        return $known
    }
    Stop-WithCode 'POSTGRES_LOCAL_MAVEN_MISSING'
}

function Ensure-BackendCli {
    $requiresBuild = -not (
        (Test-Path -LiteralPath $script:cliMarkerPath -PathType Leaf) -and
        (Test-Path -LiteralPath $script:cliClasspathPath -PathType Leaf))
    if (-not $requiresBuild) {
        $buildTimestamp =
            (Get-Item -LiteralPath $script:cliMarkerPath).LastWriteTimeUtc
        $inputs = @(
            Get-Item -LiteralPath (Join-Path $script:backendRoot 'pom.xml')
            Get-ChildItem -LiteralPath (Join-Path $script:backendRoot 'src') `
                -Recurse -File
        )
        $requiresBuild = @(
            $inputs | Where-Object { $_.LastWriteTimeUtc -gt $buildTimestamp }
        ).Count -gt 0
    }
    if (-not $requiresBuild) {
        return
    }
    $maven = Find-Maven
    Invoke-Native $maven @(
        '-f', (Join-Path $script:backendRoot 'pom.xml'),
        '-DskipFrontend=true',
        '-DskipTests',
        'compile',
        'dependency:build-classpath',
        "-Dmdep.outputFile=$($script:cliClasspathPath)",
        '-Dmdep.includeScope=runtime'
    ) 'POSTGRES_LOCAL_BACKEND_BUILD_FAILED'
    Set-Content -LiteralPath $script:cliMarkerPath -Value 'ready' `
        -Encoding Ascii
}

function Invoke-JavaCli([string]$MainClass, [string[]]$Arguments) {
    Ensure-BackendCli
    $java = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($null -eq $java) {
        $java = Get-Command java -ErrorAction SilentlyContinue
    }
    if ($null -eq $java) {
        Stop-WithCode 'POSTGRES_LOCAL_JAVA_MISSING'
    }
    $runtimeClasspath =
        (Get-Content -LiteralPath $script:cliClasspathPath -Raw).Trim()
    if ([string]::IsNullOrWhiteSpace($runtimeClasspath)) {
        Stop-WithCode 'POSTGRES_LOCAL_BACKEND_CLASSPATH_INVALID'
    }
    $javaArguments = @(
        '-cp', "$($script:cliClassesPath);$runtimeClasspath",
        $MainClass
    ) + $Arguments
    Invoke-Native $java.Source $javaArguments `
        'POSTGRES_LOCAL_JAVA_COMMAND_FAILED'
}

function Invoke-ContainerPsql(
    [string]$Database,
    [string]$Sql,
    [switch]$Capture
) {
    $admin = [string]$script:settings.PORTFOLIO_POSTGRES_ADMIN_USERNAME
    $arguments = Get-ComposeArguments @(
        'exec', '-T', 'postgres',
        'psql', '-X', '-v', 'ON_ERROR_STOP=1',
        '-U', $admin, '-d', $Database,
        '-At', '-F', '|', '-c', $Sql
    )
    return Invoke-Native $script:docker $arguments `
        'POSTGRES_LOCAL_DATABASE_QUERY_FAILED' -Capture:$Capture
}

function Invoke-Reconcile {
    Invoke-Native $script:docker `
        (Get-ComposeArguments @(
            'exec', '-T', 'postgres', 'bash',
            '/opt/portfolio/init-databases.sh'
        )) 'POSTGRES_LOCAL_DATABASE_INITIALIZATION_FAILED'
}

function Invoke-Migrations {
    Set-ApplicationDatabaseEnvironment
    Invoke-JavaCli 'com.portfolio.agent.database.DatabaseMigrationCli' @()
}

function Invoke-Verify {
    Assert-DockerAvailable
    Wait-PostgresHealthy
    $public = [string]$script:settings.PORTFOLIO_PUBLIC_DATABASE_NAME
    $governance = [string]$script:settings.PORTFOLIO_GOVERNANCE_DATABASE_NAME
    $context = [string]$script:settings.PORTFOLIO_CONTEXT_DATABASE_NAME
    Write-Output 'Public database:'
    Invoke-ContainerPsql $public @'
SELECT 'vector=' || extversion FROM pg_extension WHERE extname = 'vector';
SELECT 'flyway=' || COALESCE(MAX(version), 'none') FROM flyway_schema_history_public WHERE success;
SELECT 'release=' || cr.release_version || '|' || cr.release_id
FROM active_release ar JOIN content_release cr ON cr.release_id = ar.release_id;
SELECT 'projects=' || count(*) FROM project_profile p
JOIN active_release ar ON ar.release_id = p.release_id;
SELECT 'cases=' || count(*) FROM case_study c
JOIN active_release ar ON ar.release_id = c.release_id;
SELECT 'claims=' || count(*) FROM claim c
JOIN active_release ar ON ar.release_id = c.release_id;
SELECT 'evidence=' || count(*) FROM evidence e
JOIN active_release ar ON ar.release_id = e.release_id;
SELECT 'retrievalDocuments=' || count(*) FROM retrieval_document rd
JOIN active_release ar ON ar.release_id = rd.release_id;
SELECT 'embeddings=' || count(*) FROM retrieval_document rd
JOIN active_release ar ON ar.release_id = rd.release_id
WHERE rd.embedding IS NOT NULL;
SELECT 'selfDistance=' || COALESCE(MIN(embedding <=> embedding), 0)
FROM retrieval_document rd
JOIN active_release ar ON ar.release_id = rd.release_id
WHERE rd.embedding IS NOT NULL;
'@
    Write-Output 'Context database:'
    Invoke-ContainerPsql $context @'
SELECT 'vector=' || extversion FROM pg_extension WHERE extname = 'vector';
SELECT 'flyway=' || COALESCE(MAX(version), 'none') FROM agent_context.flyway_schema_history_context WHERE success;
SELECT 'sessions=' || count(*) FROM agent_context.conversation_session;
SELECT 'executions=' || count(*) FROM agent_context.agent_turn_execution;
SELECT 'contexts=' || count(*) FROM agent_context.agent_turn_context;
SELECT 'clarifications=' || count(*) FROM agent_context.agent_turn_clarification;
'@
    Write-Output 'Governance database:'
    Invoke-ContainerPsql $governance @'
SELECT 'vector=' || extversion FROM pg_extension WHERE extname = 'vector';
SELECT 'flyway=' || COALESCE(MAX(version), 'none') FROM flyway_schema_history_governance WHERE success;
SELECT 'documents=' || count(*) FROM source_document;
SELECT 'revisions=' || count(*) FROM source_revision;
SELECT 'chunks=' || count(*) FROM source_chunk;
'@
}

function Invoke-Connections {
    $port = [string]$script:settings.PORTFOLIO_POSTGRES_PORT
    $public = [string]$script:settings.PORTFOLIO_PUBLIC_DATABASE_NAME
    $governance = [string]$script:settings.PORTFOLIO_GOVERNANCE_DATABASE_NAME
    $context = [string]$script:settings.PORTFOLIO_CONTEXT_DATABASE_NAME
    Write-Output 'Public database'
    Write-Output '  Host: 127.0.0.1'
    Write-Output "  Port: $port"
    Write-Output "  Database: $public"
    Write-Output "  Username: $($script:settings.PORTFOLIO_PUBLIC_DATABASE_USERNAME)"
    Write-Output "  JDBC URL: jdbc:postgresql://127.0.0.1:$port/$public"
    Write-Output 'Governance database'
    Write-Output '  Host: 127.0.0.1'
    Write-Output "  Port: $port"
    Write-Output "  Database: $governance"
    Write-Output "  Username: $($script:settings.PORTFOLIO_GOVERNANCE_DATABASE_USERNAME)"
    Write-Output "  JDBC URL: jdbc:postgresql://127.0.0.1:$port/$governance"
    Write-Output 'Context database'
    Write-Output '  Host: 127.0.0.1'
    Write-Output "  Port: $port"
    Write-Output "  Database: $context"
    Write-Output "  Username: $($script:settings.PORTFOLIO_CONTEXT_DATABASE_USERNAME)"
    Write-Output "  JDBC URL: jdbc:postgresql://127.0.0.1:$port/$context"
    Write-Output "Passwords: read from $(Split-Path -Leaf $script:envPath)"
    Write-Output 'Runtime encryption keys: configured'
}

function Invoke-ContextReadiness {
    Assert-DockerAvailable
    Wait-PostgresHealthy
    $context = [string]$script:settings.PORTFOLIO_CONTEXT_DATABASE_NAME
    $schema = if ($script:settings.ContainsKey(
            'PORTFOLIO_CONTEXT_DATABASE_SCHEMA')) {
        [string]$script:settings.PORTFOLIO_CONTEXT_DATABASE_SCHEMA
    }
    else {
        'agent_context'
    }
    $result = Invoke-ContainerPsql $context @"
SELECT CASE
    WHEN to_regclass('$schema.agent_turn_execution') IS NOT NULL
     AND to_regclass('$schema.agent_turn_context') IS NOT NULL
     AND to_regclass('$schema.agent_turn_clarification') IS NOT NULL
     AND (SELECT count(*) FROM information_schema.columns
          WHERE table_schema='$schema'
            AND table_name='agent_turn_clarification'
            AND column_name IN (
                'reserved_by_request_id', 'reservation_expires_at')) = 2
     AND (SELECT count(*) FROM information_schema.columns
          WHERE table_schema='$schema'
            AND table_name='conversation_session'
            AND column_name IN (
                'active_discussion_handle',
                'active_discussion_project_id',
                'active_discussion_expires_at', 'revision',
                'semantic_state_key_id', 'semantic_state_nonce',
                'semantic_state_ciphertext',
                'semantic_state_updated_at')) = 8
    THEN 1 ELSE 0 END;
"@ -Capture
    if (@($result | Where-Object { $_ -eq '1' }).Count -eq 0) {
        Stop-WithCode 'POSTGRES_LOCAL_CONTEXT_SCHEMA_UNAVAILABLE'
    }
    Write-Output "$context`: Agent State ready"
}

try {
    if ($Command -notin @('verify-public-bundle')) {
        Import-LocalEnvironment
    }

    switch ($Command) {
        'start' {
            Start-Postgres
        }
        'bootstrap' {
            Start-Postgres
            Invoke-Reconcile
            Invoke-Migrations
            if ($ImportPublic) {
                Set-ApplicationDatabaseEnvironment
                Invoke-JavaCli `
                    'com.portfolio.agent.release.PublicBundleDatabaseImportCli' `
                    @('import', '--bundle', $script:bundleRoot)
            }
            if ($ActivatePublic) {
                if ([string]::IsNullOrWhiteSpace($ReleaseId) -or
                        $ReleaseId -ne $ConfirmReleaseId) {
                    Stop-WithCode 'POSTGRES_LOCAL_RELEASE_CONFIRMATION_REQUIRED'
                }
                Set-ApplicationDatabaseEnvironment
                Invoke-JavaCli `
                    'com.portfolio.agent.release.PublicBundleDatabaseImportCli' `
                    @('activate', '--release-id', $ReleaseId,
                        '--confirm-release-id', $ConfirmReleaseId)
            }
        }
        'status' {
            Assert-DockerAvailable
            Invoke-Native $script:docker `
                (Get-ComposeArguments @('ps')) `
                'POSTGRES_LOCAL_COMPOSE_STATUS_FAILED'
            foreach ($database in @(
                    [string]$script:settings.PORTFOLIO_PUBLIC_DATABASE_NAME,
                    [string]$script:settings.PORTFOLIO_GOVERNANCE_DATABASE_NAME,
                    [string]$script:settings.PORTFOLIO_CONTEXT_DATABASE_NAME)) {
                $result = Invoke-ContainerPsql $database 'SELECT 1' -Capture
                if (@($result | Where-Object { $_ -eq '1' }).Count -eq 0) {
                    Stop-WithCode 'POSTGRES_LOCAL_DATABASE_UNAVAILABLE'
                }
                Write-Output "$database`: reachable"
            }
        }
        'check-context' {
            Invoke-ContextReadiness
        }
        'verify' {
            Invoke-Verify
        }
        'connections' {
            Invoke-Connections
        }
        'stop' {
            Assert-DockerAvailable
            Invoke-Native $script:docker `
                (Get-ComposeArguments @('stop', 'postgres')) `
                'POSTGRES_LOCAL_COMPOSE_STOP_FAILED'
            Write-Output "Volume retained: $($script:volumeName)"
        }
        'reset' {
            if ($Confirm -ne 'RESET-PORTFOLIO-LOCAL') {
                Stop-WithCode 'POSTGRES_LOCAL_RESET_CONFIRMATION_REQUIRED'
            }
            Assert-DockerAvailable
            Write-Output "Compose project to remove: $($script:composeProject)"
            Write-Output "Volume to remove: $($script:volumeName)"
            Invoke-Native $script:docker `
                (Get-ComposeArguments @('down', '--volumes', '--remove-orphans')) `
                'POSTGRES_LOCAL_RESET_FAILED'
        }
        'verify-public-bundle' {
            Invoke-JavaCli `
                'com.portfolio.agent.release.PublicBundleDatabaseImportCli' `
                @('verify', '--bundle', $script:bundleRoot)
        }
        'import-public' {
            Start-Postgres
            Set-ApplicationDatabaseEnvironment
            Invoke-JavaCli `
                'com.portfolio.agent.release.PublicBundleDatabaseImportCli' `
                @('import', '--bundle', $script:bundleRoot)
        }
        'activate-public' {
            if ([string]::IsNullOrWhiteSpace($ReleaseId) -or
                    $ReleaseId -ne $ConfirmReleaseId) {
                Stop-WithCode 'POSTGRES_LOCAL_RELEASE_CONFIRMATION_REQUIRED'
            }
            Start-Postgres
            Set-ApplicationDatabaseEnvironment
            Invoke-JavaCli `
                'com.portfolio.agent.release.PublicBundleDatabaseImportCli' `
                @('activate', '--release-id', $ReleaseId,
                    '--confirm-release-id', $ConfirmReleaseId)
        }
        'scan-markdown' {
            $resolvedRoot = Resolve-ExistingDirectory $Root `
                'POSTGRES_LOCAL_MARKDOWN_ROOT_INVALID'
            Start-Postgres
            Set-ApplicationDatabaseEnvironment
            Invoke-JavaCli `
                'com.portfolio.agent.ingestion.cli.MarkdownImportCli' `
                @('scan', '--root', $resolvedRoot, '--dry-run')
        }
        'import-markdown' {
            $resolvedRoot = Resolve-ExistingDirectory $Root `
                'POSTGRES_LOCAL_MARKDOWN_ROOT_INVALID'
            Start-Postgres
            Set-ApplicationDatabaseEnvironment
            Invoke-JavaCli `
                'com.portfolio.agent.ingestion.cli.MarkdownImportCli' `
                @('import', '--root', $resolvedRoot)
        }
        'retry-markdown' {
            $resolvedRoot = Resolve-ExistingDirectory $Root `
                'POSTGRES_LOCAL_MARKDOWN_ROOT_INVALID'
            Start-Postgres
            Set-ApplicationDatabaseEnvironment
            Invoke-JavaCli `
                'com.portfolio.agent.ingestion.cli.MarkdownImportCli' `
                @('retry', '--root', $resolvedRoot)
        }
    }
}
catch {
    [Console]::Error.WriteLine([string]$_.Exception.Message)
    exit 1
}
