$ErrorActionPreference = 'Stop'

$entryPoint = Join-Path $PSScriptRoot 'start-frontend.ps1'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$fixture = Join-Path $PSScriptRoot 'test-fixtures\direct-vite-fixture.ps1'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('portfolio-start-frontend-' + [guid]::NewGuid().ToString('N'))

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

function Assert-Equal($Expected, $Actual, [string]$Message) {
    if ($Expected -ne $Actual) {
        throw "$Message. Expected=[$Expected] Actual=[$Actual]"
    }
}

function Set-TemporaryEnvironment([hashtable]$Environment) {
    $snapshots = @{}
    foreach ($name in $Environment.Keys) {
        $snapshots[$name] = [Environment]::GetEnvironmentVariable(
            $name,
            [EnvironmentVariableTarget]::Process
        )
        [Environment]::SetEnvironmentVariable(
            $name,
            [string]$Environment[$name],
            [EnvironmentVariableTarget]::Process
        )
    }
    return $snapshots
}

function Restore-TemporaryEnvironment([hashtable]$Snapshots) {
    foreach ($name in $Snapshots.Keys) {
        [Environment]::SetEnvironmentVariable(
            $name,
            $Snapshots[$name],
            [EnvironmentVariableTarget]::Process
        )
    }
}

function Invoke-EntryPoint(
    [string]$LogDirectory,
    [hashtable]$Environment = @{},
    [string[]]$AdditionalArguments = @()
) {
    $stdoutPath = Join-Path $fixtureRoot ("stdout-$([guid]::NewGuid().ToString('N')).txt")
    $stderrPath = Join-Path $fixtureRoot ("stderr-$([guid]::NewGuid().ToString('N')).txt")
    $environmentSnapshot = Set-TemporaryEnvironment $Environment
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $entryPoint `
            -ViteCommand $fixture `
            -LogDirectory $LogDirectory `
            @AdditionalArguments 1> $stdoutPath 2> $stderrPath
        $exitCode = $LASTEXITCODE
        return @{
            ExitCode = $exitCode
            Stdout = if (Test-Path -LiteralPath $stdoutPath) {
                Get-Content -LiteralPath $stdoutPath -Raw
            }
            else {
                ''
            }
            Stderr = if (Test-Path -LiteralPath $stderrPath) {
                Get-Content -LiteralPath $stderrPath -Raw
            }
            else {
                ''
            }
        }
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
        Restore-TemporaryEnvironment $environmentSnapshot
    }
}

function Get-LogFile([string]$LogDirectory, [string]$Name) {
    $path = Join-Path $LogDirectory "current\$Name"
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        return ''
    }
    return Get-Content -LiteralPath $path -Raw
}

$testMode = 'PORTFOLIO_START_FRONTEND_TEST_MODE'

try {
    [System.IO.Directory]::CreateDirectory($fixtureRoot) | Out-Null

    $seamResult = Invoke-EntryPoint (Join-Path $fixtureRoot 'seam') @{}
    Assert-True ($seamResult.ExitCode -ne 0) 'Test seam must fail without test mode.'
    Assert-True ($seamResult.Stderr -match 'LOCAL_TEST_SEAM_FORBIDDEN') `
        'Test seam must report LOCAL_TEST_SEAM_FORBIDDEN.'

    $environment = @{
        $testMode = 'true'
        PORTFOLIO_FIXTURE_REPOSITORY_ROOT = $repositoryRoot
    }

    $directLogDirectory = Join-Path $fixtureRoot 'direct-logs'
    $directResult = Invoke-EntryPoint $directLogDirectory $environment
    Assert-Equal 42 $directResult.ExitCode 'Direct dev must propagate the child exit code.'
    $directInfo = Get-LogFile $directLogDirectory 'frontend-info.log'
    $directError = Get-LogFile $directLogDirectory 'frontend-error.log'
    Assert-True (Test-Path -LiteralPath `
            (Join-Path $directLogDirectory 'current\frontend-info.log')) `
        'Direct dev must create frontend-info.log.'
    Assert-True (Test-Path -LiteralPath `
            (Join-Path $directLogDirectory 'current\frontend-error.log')) `
        'Direct dev must create frontend-error.log.'
    Assert-True $directInfo.Contains('fixture-stdout-marker') 'Vite stdout route'
    Assert-True $directError.Contains('fixture-stderr-marker') 'Vite stderr route'
    Assert-True (-not $directInfo.Contains('fixture-stderr-marker')) `
        'Vite stderr must not leak into frontend-info.log'
    Assert-True $directResult.Stdout.Contains('fixture-stdout-marker') `
        'Direct dev must mirror Vite stdout to the terminal.'
    Assert-True $directResult.Stderr.Contains('fixture-stderr-marker') `
        'Direct dev must mirror Vite stderr to the terminal.'
    Assert-True $directResult.Stdout.Contains('LOG_DIRECTORY') `
        'Direct dev must report the log directory.'
    Assert-True (-not $directInfo.Contains($repositoryRoot)) `
        'Direct dev log must not leak the repository path.'
    Assert-True (-not $directInfo.Contains([char]27)) `
        'Direct dev log must strip ANSI control characters.'
    Assert-True (-not $directResult.Stdout.Contains($repositoryRoot)) `
        'Direct dev terminal mirror must not leak the repository path.'
    Assert-True (-not $directResult.Stdout.Contains([char]27)) `
        'Direct dev terminal mirror must strip ANSI control characters.'

    $delegatedEnvironment = @{
        $testMode = 'true'
        PORTFOLIO_FIXTURE_REPOSITORY_ROOT = $repositoryRoot
        PORTFOLIO_FRONTEND_LOG_OWNER = 'UNIFIED'
    }
    $delegatedLogDirectory = Join-Path $fixtureRoot 'delegated-logs'
    $delegatedResult = Invoke-EntryPoint $delegatedLogDirectory $delegatedEnvironment
    Assert-Equal 42 $delegatedResult.ExitCode `
        'Delegated mode must still propagate the child exit code.'
    Assert-True (-not (Test-Path -LiteralPath `
            (Join-Path $delegatedLogDirectory 'current\frontend-info.log'))) `
        'Delegated mode must not create frontend log files.'
    Assert-True $delegatedResult.Stdout.Contains('fixture-stdout-marker') `
        'Delegated mode must keep mirroring Vite stdout.'
    Assert-True (-not $delegatedResult.Stdout.Contains('LOG_DIRECTORY')) `
        'Delegated mode must not print launcher-owned diagnostics.'

    $degradedEnvironment = @{
        $testMode = 'true'
        PORTFOLIO_FIXTURE_REPOSITORY_ROOT = $repositoryRoot
    }
    $degradedResult = Invoke-EntryPoint `
        ([System.IO.Path]::GetPathRoot($env:SystemDrive)) `
        $degradedEnvironment
    Assert-Equal 42 $degradedResult.ExitCode `
        'Degraded router must not block Vite or the exit code.'
    Assert-True ($degradedResult.Stdout -match 'LOG_ROUTER_DEGRADED') `
        'Degraded router must report a safe degradation code.'
    Assert-True (-not $degradedResult.Stderr.Contains('LOCAL_LOG_DIRECTORY_UNSAFE')) `
        'Degraded router must not expose the underlying failure text.'

    $packageJson = Get-Content -LiteralPath `
        (Join-Path $repositoryRoot 'frontend\package.json') -Raw
    Assert-True ($packageJson -match '"dev"\s*:\s*"[^"]*start-frontend\.ps1') `
        'The frontend dev script must invoke the repository-owned entry point.'

    $argsFile = Join-Path $fixtureRoot 'vite-args.txt'
    $hostArgumentsEnvironment = @{
        $testMode = 'true'
        PORTFOLIO_FIXTURE_REPOSITORY_ROOT = $repositoryRoot
        PORTFOLIO_FIXTURE_ARGS_FILE = $argsFile
    }
    $defaultArgsDirectory = Join-Path $fixtureRoot 'default-args-logs'
    [void](Invoke-EntryPoint $defaultArgsDirectory $hostArgumentsEnvironment)
    $defaultArgs = Get-Content -LiteralPath $argsFile -Raw
    Assert-True ($defaultArgs -match '^--host\|127\.0\.0\.1\|--strictPort$') `
        'Direct dev must default to host 127.0.0.1 with --strictPort.'

    Remove-Item -LiteralPath $argsFile -Force
    $customHostEnvironment = @{
        $testMode = 'true'
        PORTFOLIO_FIXTURE_REPOSITORY_ROOT = $repositoryRoot
        PORTFOLIO_FIXTURE_ARGS_FILE = $argsFile
    }
    $customHostDirectory = Join-Path $fixtureRoot 'custom-host-logs'
    $customHostResult = Invoke-EntryPoint $customHostDirectory $customHostEnvironment @(
        '--host', '0.0.0.0'
    )
    $customArgs = Get-Content -LiteralPath $argsFile -Raw
    Assert-True ($customArgs -match '^--host\|0\.0\.0\.0\|--strictPort$') `
        'Direct dev must honour a developer-provided --host override.'

    Write-Output 'start-frontend tests passed'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolvedFixtureRoot = (Resolve-Path -LiteralPath $fixtureRoot).Path
        if (-not ([System.IO.Path]::GetFileName($resolvedFixtureRoot)).
                StartsWith('portfolio-start-frontend-')) {
            throw "Refusing to remove unverified fixture path: $resolvedFixtureRoot"
        }
        Remove-Item -LiteralPath $resolvedFixtureRoot -Recurse -Force
    }
}
