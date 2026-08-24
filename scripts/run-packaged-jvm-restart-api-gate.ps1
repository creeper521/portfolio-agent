param(
    [string]$JarPath = '',
    [string]$DockerExecutable = 'docker.exe',
    [ValidateRange(1, 65535)]
    [int]$ApplicationPort = 4173,
    [ValidateRange(1, 120)]
    [int]$ReadinessTimeoutSeconds = 60
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = if ([string]::IsNullOrWhiteSpace($JarPath)) {
    Join-Path $root 'backend\target\portfolio-agent.jar'
}
else {
    [System.IO.Path]::GetFullPath($JarPath)
}
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw 'Packaged JAR is missing; JVM restart API gate cannot start.'
}
if ($null -eq (Get-Command $DockerExecutable -ErrorAction SilentlyContinue)) {
    throw 'Docker is unavailable; JVM restart API gate cannot provision PostgreSQL.'
}

$suffix = [guid]::NewGuid().ToString('N')
$containerName = "portfolio-agent-jvm-restart-$suffix"
$databaseName = 'agent_restart'
$databaseUser = 'agent_restart'
$databasePassword = 'agent-restart-fixture-password'
$image = 'pgvector/pgvector:0.8.5-pg16-bookworm'
$containerStarted = $false

function Assert-DockerExit([string]$Label) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Label failed with exit code $LASTEXITCODE."
    }
}

try {
    $containerId = (& $DockerExecutable run --detach --rm `
        --name $containerName `
        --env "POSTGRES_DB=$databaseName" `
        --env "POSTGRES_USER=$databaseUser" `
        --env "POSTGRES_PASSWORD=$databasePassword" `
        --publish-all $image | Out-String).Trim()
    Assert-DockerExit 'PostgreSQL container start'
    if ($containerId -notmatch '^[a-f0-9]{12,64}$') {
        throw 'PostgreSQL container returned an invalid identity.'
    }
    $containerStarted = $true

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($ReadinessTimeoutSeconds)
    $databaseReady = $false
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        & $DockerExecutable exec $containerName pg_isready `
            --username $databaseUser --dbname $databaseName 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $databaseReady = $true
            break
        }
        Start-Sleep -Milliseconds 200
    }
    if (-not $databaseReady) {
        throw 'Provisioned PostgreSQL did not become ready.'
    }

    $portOutput = (& $DockerExecutable port $containerName '5432/tcp' |
        Out-String).Trim()
    Assert-DockerExit 'PostgreSQL port discovery'
    $portMatch = [regex]::Match($portOutput, '(?m):(?<port>[0-9]{1,5})\s*$')
    if (-not $portMatch.Success) {
        throw 'Provisioned PostgreSQL port was not discoverable.'
    }
    $databasePort = [int]$portMatch.Groups['port'].Value
    if ($databasePort -lt 1 -or $databasePort -gt 65535) {
        throw 'Provisioned PostgreSQL port was invalid.'
    }

    $tokenKey = [byte[]]::new(32)
    $payloadKey = [byte[]]::new(32)
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($tokenKey)
        $random.GetBytes($payloadKey)
    }
    finally {
        $random.Dispose()
    }

    & (Join-Path $root 'scripts\run-jar-e2e.ps1') `
        -JarPath $jar `
        -Port $ApplicationPort `
        -ContextMode POSTGRESQL `
        -ContextDatabaseUrl "jdbc:postgresql://127.0.0.1:$databasePort/$databaseName" `
        -ContextDatabaseUsername $databaseUser `
        -ContextDatabasePassword $databasePassword `
        -CurrentTokenKeyId 'restart-token-key' `
        -CurrentTokenKey ([Convert]::ToBase64String($tokenKey)) `
        -CurrentPayloadKeyId 'restart-payload-key' `
        -CurrentPayloadKey ([Convert]::ToBase64String($payloadKey)) `
        -Lane JVM_RESTART `
        -SkipPlaywright `
        -ReadinessTimeoutSeconds $ReadinessTimeoutSeconds
    if ($LASTEXITCODE -ne 0) {
        throw "Packaged JVM restart API lane failed with exit code $LASTEXITCODE."
    }
    Write-Output 'PACKAGED_JVM_RESTART_GATE_PASS state=POSTGRESQL; jvmCount=2; browser=NOT_RUN'
}
finally {
    if ($containerStarted -and
            $containerName -match '^portfolio-agent-jvm-restart-[a-f0-9]{32}$') {
        & $DockerExecutable stop $containerName 2>&1 | Out-Null
    }
}
