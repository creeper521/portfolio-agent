$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$scriptPath = Join-Path $PSScriptRoot 'postgres-local.ps1'
$composePath = Join-Path $repositoryRoot 'compose.postgres.local.yml'
$examplePath = Join-Path $repositoryRoot '.env.postgres.example'
$powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) (
    'postgres-local-test-' + [guid]::NewGuid().ToString('N'))

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

function Invoke-Tool(
    [string[]]$Arguments,
    [hashtable]$Environment = @{},
    [string]$ExecutableScript = $scriptPath
) {
    $saved = @{}
    try {
        foreach ($entry in $Environment.GetEnumerator()) {
            $saved[$entry.Key] = [Environment]::GetEnvironmentVariable(
                $entry.Key, 'Process')
            [Environment]::SetEnvironmentVariable(
                $entry.Key, [string]$entry.Value, 'Process')
        }
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        $output = (& $powershell -NoProfile -ExecutionPolicy Bypass `
            -File $ExecutableScript @Arguments 2>&1 | Out-String)
        $ErrorActionPreference = $previousPreference
        return @{
            ExitCode = $LASTEXITCODE
            Output = $output
        }
    }
    finally {
        foreach ($entry in $saved.GetEnumerator()) {
            [Environment]::SetEnvironmentVariable(
                $entry.Key, $entry.Value, 'Process')
        }
    }
}

try {
    Assert-True (Test-Path -LiteralPath $scriptPath -PathType Leaf) `
        'postgres-local.ps1 must exist.'
    Assert-True (Test-Path -LiteralPath $composePath -PathType Leaf) `
        'compose.postgres.local.yml must exist.'
    Assert-True (Test-Path -LiteralPath $examplePath -PathType Leaf) `
        '.env.postgres.example must exist.'

    New-Item -ItemType Directory -Path $fixtureRoot | Out-Null
    $unicodeLeaf = ([char]0x672C).ToString() + ([char]0x5730) +
        ' ' + ([char]0x6570) + ([char]0x636E) + ([char]0x5E93) + '.env'
    $envFile = Join-Path $fixtureRoot $unicodeLeaf
    @'
PORTFOLIO_POSTGRES_PORT=54329
PORTFOLIO_POSTGRES_ADMIN_USERNAME=postgres
PORTFOLIO_POSTGRES_ADMIN_PASSWORD=admin-secret
PORTFOLIO_PUBLIC_DATABASE_NAME=portfolio_public_dev
PORTFOLIO_PUBLIC_DATABASE_USERNAME=portfolio_public_owner
PORTFOLIO_PUBLIC_DATABASE_PASSWORD=public-secret
PORTFOLIO_GOVERNANCE_DATABASE_NAME=portfolio_governance_dev
PORTFOLIO_GOVERNANCE_DATABASE_USERNAME=portfolio_governance_owner
PORTFOLIO_GOVERNANCE_DATABASE_PASSWORD=governance-secret
'@ | Set-Content -LiteralPath $envFile -Encoding UTF8

    $missingEnv = Invoke-Tool @('connections', '-EnvFile',
        (Join-Path $fixtureRoot 'missing.env'))
    Assert-True ($missingEnv.ExitCode -ne 0) `
        'Missing env file must fail.'
    Assert-True ($missingEnv.Output -match 'POSTGRES_LOCAL_ENV_FILE_MISSING') `
        'Missing env file must return a stable error code.'

    $connections = Invoke-Tool @('connections', '-EnvFile', $envFile)
    Assert-True ($connections.ExitCode -eq 0) `
        "connections must succeed. Output: $($connections.Output)"
    Assert-True ($connections.Output -match 'portfolio_public_dev') `
        'connections must include the public database.'
    Assert-True ($connections.Output -match 'portfolio_governance_dev') `
        'connections must include the governance database.'
    Assert-True ($connections.Output -notmatch 'admin-secret|public-secret|governance-secret') `
        'connections must not print passwords.'
    Assert-True ($connections.Output -match 'Passwords: read from') `
        'connections must explain where passwords are stored.'

    $unsafeEnv = Join-Path $fixtureRoot 'unsafe.env'
    (Get-Content -LiteralPath $envFile -Raw).Replace(
        'admin-secret', 'unsafe$password') |
        Set-Content -LiteralPath $unsafeEnv -Encoding UTF8
    $unsafePassword = Invoke-Tool @('connections', '-EnvFile', $unsafeEnv)
    Assert-True ($unsafePassword.ExitCode -ne 0) `
        'Passwords with Compose interpolation syntax must be rejected.'
    Assert-True ($unsafePassword.Output -match 'POSTGRES_LOCAL_PASSWORD_UNSAFE') `
        'Unsafe password syntax must return a stable error code.'

    $sameDatabaseEnv = Join-Path $fixtureRoot 'same-database.env'
    (Get-Content -LiteralPath $envFile -Raw).Replace(
        'portfolio_governance_dev', 'portfolio_public_dev') |
        Set-Content -LiteralPath $sameDatabaseEnv -Encoding UTF8
    $sameDatabase = Invoke-Tool @(
        'connections', '-EnvFile', $sameDatabaseEnv)
    Assert-True ($sameDatabase.ExitCode -ne 0) `
        'Public and governance databases must be distinct.'
    Assert-True ($sameDatabase.Output -match
            'POSTGRES_LOCAL_IDENTIFIERS_NOT_DISTINCT') `
        'Duplicate database identifiers must return a stable error code.'

    $oldPath = $env:PATH
    $env:PATH = $fixtureRoot
    try {
        $missingDocker = Invoke-Tool @('start', '-EnvFile', $envFile)
    }
    finally {
        $env:PATH = $oldPath
    }
    Assert-True ($missingDocker.ExitCode -ne 0) `
        'start must fail when Docker is missing.'
    Assert-True ($missingDocker.Output -match 'POSTGRES_LOCAL_DOCKER_MISSING') `
        'Missing Docker must return a stable error code.'
    Assert-True ($missingDocker.Output -notmatch 'secret') `
        'Docker failures must not print passwords.'

    $dockerLog = Join-Path $fixtureRoot 'docker.log'
    $fakeDocker = Join-Path $fixtureRoot 'docker.ps1'
    @'
$joined = $args -join ' '
[IO.File]::AppendAllText(
    $env:PORTFOLIO_POSTGRES_FAKE_DOCKER_LOG,
    "$joined$([Environment]::NewLine)")
if ($env:PORTFOLIO_POSTGRES_FAKE_DOCKER_MODE -eq 'FAIL_UP' -and
        $joined -match '\sup\s') {
    exit 41
}
if ($args[0] -eq 'inspect') {
    if ($env:PORTFOLIO_POSTGRES_FAKE_DOCKER_MODE -eq 'UNHEALTHY') {
        Write-Output 'starting'
    }
    else {
        Write-Output 'healthy'
    }
    exit 0
}
if ($joined -match 'ps -q postgres') {
    Write-Output 'fake-container-id'
    exit 0
}
if ($joined -match '\spsql\s') {
    if ($env:PORTFOLIO_POSTGRES_FAKE_DOCKER_MODE -eq 'FAIL_PSQL') {
        exit 42
    }
    Write-Output '1'
    exit 0
}
if ($args[0] -eq 'info') {
    Write-Output '27.0.0'
}
if ($args[0] -eq 'compose' -and $args[1] -eq 'version') {
    Write-Output 'Docker Compose version v2.29.0'
}
exit 0
'@ | Set-Content -LiteralPath $fakeDocker -Encoding Ascii

    $oldPath = $env:PATH
    $env:PATH = "$fixtureRoot;$oldPath"
    try {
        $fakeEnvironment = @{
            PORTFOLIO_POSTGRES_FAKE_DOCKER_LOG = $dockerLog
            PORTFOLIO_POSTGRES_FAKE_DOCKER_MODE = ''
        }
        $start = Invoke-Tool @('start', '-EnvFile', $envFile,
            '-TimeoutSeconds', '3') $fakeEnvironment
        Assert-True ($start.ExitCode -eq 0) `
            "start must wait for a healthy container. Output: $($start.Output)"
        Assert-True ($start.Output -match 'PostgreSQL: healthy') `
            'start must report health.'
        $startLog = Get-Content -LiteralPath $dockerLog -Raw
        Assert-True ($startLog -match 'compose .* up -d postgres') `
            'start must route through the dedicated Compose file.'
        Assert-True ($startLog -notmatch 'secret') `
            'Docker arguments must not contain passwords.'

        Clear-Content -LiteralPath $dockerLog
        $composeFailure = Invoke-Tool @('start', '-EnvFile', $envFile) @{
            PORTFOLIO_POSTGRES_FAKE_DOCKER_LOG = $dockerLog
            PORTFOLIO_POSTGRES_FAKE_DOCKER_MODE = 'FAIL_UP'
        }
        Assert-True ($composeFailure.ExitCode -ne 0) `
            'Compose startup failure must propagate a non-zero exit code.'
        Assert-True ($composeFailure.Output -match
                'POSTGRES_LOCAL_COMPOSE_START_FAILED') `
            'Compose startup failure must return a stable error code.'

        $timeout = Invoke-Tool @('start', '-EnvFile', $envFile,
            '-TimeoutSeconds', '1') @{
            PORTFOLIO_POSTGRES_FAKE_DOCKER_LOG = $dockerLog
            PORTFOLIO_POSTGRES_FAKE_DOCKER_MODE = 'UNHEALTHY'
        }
        Assert-True ($timeout.ExitCode -ne 0) `
            'Health timeout must return a non-zero exit code.'
        Assert-True ($timeout.Output -match 'POSTGRES_LOCAL_HEALTH_TIMEOUT') `
            'Health timeout must return a stable error code.'

        $status = Invoke-Tool @('status', '-EnvFile', $envFile) $fakeEnvironment
        Assert-True ($status.ExitCode -eq 0) `
            "status must return zero for reachable databases. Output: $($status.Output)"
        Assert-True ($status.Output -match 'portfolio_public_dev: reachable') `
            'status must check the public database.'
        Assert-True ($status.Output -match 'portfolio_governance_dev: reachable') `
            'status must check the governance database.'

        $verify = Invoke-Tool @('verify', '-EnvFile', $envFile,
            '-TimeoutSeconds', '3') $fakeEnvironment
        Assert-True ($verify.ExitCode -eq 0) `
            "verify must return zero when its queries succeed. Output: $($verify.Output)"

        $queryFailure = Invoke-Tool @('verify', '-EnvFile', $envFile,
            '-TimeoutSeconds', '3') @{
            PORTFOLIO_POSTGRES_FAKE_DOCKER_LOG = $dockerLog
            PORTFOLIO_POSTGRES_FAKE_DOCKER_MODE = 'FAIL_PSQL'
        }
        Assert-True ($queryFailure.ExitCode -ne 0) `
            'verify must propagate a query failure.'
        Assert-True ($queryFailure.Output -match
                'POSTGRES_LOCAL_DATABASE_QUERY_FAILED') `
            'Query failures must return a stable error code.'

        Clear-Content -LiteralPath $dockerLog
        $reset = Invoke-Tool @('reset', '-EnvFile', $envFile,
            '-Confirm', 'RESET-PORTFOLIO-LOCAL') $fakeEnvironment
        Assert-True ($reset.ExitCode -eq 0) `
            "Confirmed reset must succeed. Output: $($reset.Output)"
        Assert-True ($reset.Output -match
                'portfolio-postgres-local_postgres_data') `
            'reset must print the exact owned volume.'
        $resetLog = Get-Content -LiteralPath $dockerLog -Raw
        Assert-True ($resetLog -match 'down --volumes --remove-orphans') `
            'reset must remove only the current Compose project resources.'
        Assert-True ($resetLog -notmatch 'prune|unrelated') `
            'reset must not touch unrelated volumes.'

        $isolatedRepository = Join-Path $fixtureRoot 'isolated repository'
        $isolatedScripts = Join-Path $isolatedRepository 'scripts'
        $isolatedTarget = Join-Path $isolatedRepository 'backend\target'
        $isolatedSource = Join-Path $isolatedRepository 'backend\src'
        New-Item -ItemType Directory -Path $isolatedScripts | Out-Null
        New-Item -ItemType Directory -Path $isolatedTarget | Out-Null
        New-Item -ItemType Directory -Path $isolatedSource | Out-Null
        Copy-Item -LiteralPath $scriptPath -Destination $isolatedScripts
        Copy-Item -LiteralPath $composePath -Destination $isolatedRepository
        Set-Content -LiteralPath (
            Join-Path $isolatedRepository 'backend\pom.xml') -Value '<project/>'
        Set-Content -LiteralPath (
            Join-Path $isolatedTarget 'postgres-local-runtime-classpath.txt') `
            -Value 'fake-runtime.jar'
        Set-Content -LiteralPath (
            Join-Path $isolatedTarget 'postgres-local-cli.ready') -Value 'ready'
        $isolatedScript = Join-Path $isolatedScripts 'postgres-local.ps1'
        $javaLog = Join-Path $fixtureRoot 'java.log'
        $fakeJava = Join-Path $fixtureRoot 'java.ps1'
        @'
[IO.File]::AppendAllText(
    $env:PORTFOLIO_POSTGRES_FAKE_JAVA_LOG,
    (($args -join ' ') + [Environment]::NewLine))
if ($env:PORTFOLIO_POSTGRES_FAKE_JAVA_MODE -eq 'FAIL') {
    exit 43
}
exit 0
'@ | Set-Content -LiteralPath $fakeJava -Encoding Ascii

        $javaFailure = Invoke-Tool @('verify-public-bundle') @{
            PATH = $fixtureRoot
            PORTFOLIO_POSTGRES_FAKE_JAVA_LOG = $javaLog
            PORTFOLIO_POSTGRES_FAKE_JAVA_MODE = 'FAIL'
        } $isolatedScript
        Assert-True ($javaFailure.ExitCode -ne 0) `
            'Bundle verification must propagate a Java CLI failure.'
        Assert-True ($javaFailure.Output -match
                'POSTGRES_LOCAL_JAVA_COMMAND_FAILED') `
            'Java CLI failures must return a stable error code.'

        $unicodeRoot = Join-Path $fixtureRoot (
            ([char]0x77E5).ToString() + ([char]0x8BC6) + ' root')
        New-Item -ItemType Directory -Path $unicodeRoot | Out-Null
        $markdownScan = Invoke-Tool @(
            'scan-markdown', '-EnvFile', $envFile, '-Root', $unicodeRoot,
            '-TimeoutSeconds', '3') @{
            PATH = $fixtureRoot
            PORTFOLIO_POSTGRES_FAKE_DOCKER_LOG = $dockerLog
            PORTFOLIO_POSTGRES_FAKE_DOCKER_MODE = ''
            PORTFOLIO_POSTGRES_FAKE_JAVA_LOG = $javaLog
            PORTFOLIO_POSTGRES_FAKE_JAVA_MODE = ''
        } $isolatedScript
        Assert-True ($markdownScan.ExitCode -eq 0) `
            "Unicode Markdown root must be forwarded intact. Output: $($markdownScan.Output)"
        $javaArguments = Get-Content -LiteralPath $javaLog -Raw -Encoding UTF8
        Assert-True ($javaArguments -match
                [regex]::Escape((Resolve-Path -LiteralPath $unicodeRoot).Path)) `
            "Markdown root with Unicode and spaces must reach the Java CLI. Arguments: $javaArguments"

        $publicImportFailure = Invoke-Tool @(
            'import-public', '-EnvFile', $envFile, '-TimeoutSeconds', '3') @{
            PATH = $fixtureRoot
            PORTFOLIO_POSTGRES_FAKE_DOCKER_LOG = $dockerLog
            PORTFOLIO_POSTGRES_FAKE_DOCKER_MODE = ''
            PORTFOLIO_POSTGRES_FAKE_JAVA_LOG = $javaLog
            PORTFOLIO_POSTGRES_FAKE_JAVA_MODE = 'FAIL'
        } $isolatedScript
        Assert-True ($publicImportFailure.ExitCode -ne 0) `
            'Public import must propagate a Java CLI failure.'
        Assert-True ($publicImportFailure.Output -match
                'POSTGRES_LOCAL_JAVA_COMMAND_FAILED') `
            'Public import failures must return a stable error code.'

        $releaseId = '197d8d4b-f342-3f2e-9fc5-faf54807e7f1'
        $commonJavaEnvironment = @{
            PATH = $fixtureRoot
            PORTFOLIO_POSTGRES_FAKE_DOCKER_LOG = $dockerLog
            PORTFOLIO_POSTGRES_FAKE_DOCKER_MODE = ''
            PORTFOLIO_POSTGRES_FAKE_JAVA_LOG = $javaLog
            PORTFOLIO_POSTGRES_FAKE_JAVA_MODE = ''
        }
        $activation = Invoke-Tool @(
            'activate-public', '-EnvFile', $envFile,
            '-ReleaseId', $releaseId, '-ConfirmReleaseId', $releaseId,
            '-TimeoutSeconds', '3') $commonJavaEnvironment $isolatedScript
        Assert-True ($activation.ExitCode -eq 0) `
            "Confirmed activation must reach the Java CLI. Output: $($activation.Output)"

        $markdownImport = Invoke-Tool @(
            'import-markdown', '-EnvFile', $envFile, '-Root', $unicodeRoot,
            '-TimeoutSeconds', '3') $commonJavaEnvironment $isolatedScript
        $markdownRetry = Invoke-Tool @(
            'retry-markdown', '-EnvFile', $envFile, '-Root', $unicodeRoot,
            '-TimeoutSeconds', '3') $commonJavaEnvironment $isolatedScript
        Assert-True ($markdownImport.ExitCode -eq 0) `
            'Markdown import must reach the Java CLI.'
        Assert-True ($markdownRetry.ExitCode -eq 0) `
            'Markdown retry must reach the Java CLI.'

        $javaArguments = Get-Content -LiteralPath $javaLog -Raw -Encoding UTF8
        Assert-True ($javaArguments -match
                'PublicBundleDatabaseImportCli import --bundle') `
            'Public import arguments must reach the Java CLI.'
        Assert-True ($javaArguments -match (
                'PublicBundleDatabaseImportCli activate --release-id ' +
                [regex]::Escape($releaseId) + ' --confirm-release-id ' +
                [regex]::Escape($releaseId))) `
            'Activation must forward the same UUID twice.'
        $escapedRoot = [regex]::Escape(
            (Resolve-Path -LiteralPath $unicodeRoot).Path)
        Assert-True ($javaArguments -match
                "MarkdownImportCli import --root $escapedRoot") `
            'Markdown import must forward the exact root.'
        Assert-True ($javaArguments -match
                "MarkdownImportCli retry --root $escapedRoot") `
            'Markdown retry must forward the exact root.'
    }
    finally {
        $env:PATH = $oldPath
    }

    $invalidReset = Invoke-Tool @('reset', '-EnvFile', $envFile,
        '-Confirm', 'wrong')
    Assert-True ($invalidReset.ExitCode -ne 0) `
        'reset must reject an incorrect confirmation.'
    Assert-True ($invalidReset.Output -match 'POSTGRES_LOCAL_RESET_CONFIRMATION_REQUIRED') `
        'reset must require the exact confirmation token.'

    $source = Get-Content -LiteralPath $scriptPath -Raw -Encoding UTF8
    Assert-True ($source -match "'verify-public-bundle'") `
        'Command routing must include verify-public-bundle.'
    Assert-True ($source -match "'import-public'") `
        'Command routing must include import-public.'
    Assert-True ($source -match "'activate-public'") `
        'Command routing must include activate-public.'
    Assert-True ($source -match "'scan-markdown'") `
        'Command routing must include scan-markdown.'
    Assert-True ($source -match "'import-markdown'") `
        'Command routing must include import-markdown.'
    Assert-True ($source -match "'retry-markdown'") `
        'Command routing must include retry-markdown.'
    Assert-True ($source -match '--confirm-release-id') `
        'Release confirmation must be forwarded to the Java CLI.'
    Assert-True ($source -match '--root') `
        'Markdown root must be forwarded to the Java CLI.'
    Assert-True ($source -notmatch 'docker\s+volume\s+prune|docker\s+system\s+prune') `
        'The tool must never use unscoped Docker pruning.'

    $compose = Get-Content -LiteralPath $composePath -Raw -Encoding UTF8
    Assert-True ($compose -match 'pgvector/pgvector:0\.8\.5-pg16-bookworm') `
        'Compose must pin the pgvector and PostgreSQL image version.'
    Assert-True ($compose -match '127\.0\.0\.1:\$\{PORTFOLIO_POSTGRES_PORT') `
        'PostgreSQL must bind only to loopback.'
    Assert-True ($compose -match 'healthcheck:') `
        'Compose must define a healthcheck.'
    Assert-True ($compose -match 'postgres_data:') `
        'Compose must use a named volume.'

    Write-Output 'postgres-local tests passed'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        if ((Split-Path -Leaf $resolved) -notmatch
                '^postgres-local-test-[0-9a-f]{32}$') {
            throw "Refusing to remove unexpected fixture path: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
