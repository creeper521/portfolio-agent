param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('validate', 'offline', 'provider', 'periodic')]
    [string]$Command,
    [Parameter(Mandatory = $true)]
    [string[]]$CliArgs,
    [switch]$SkipInstall,
    [string]$MavenExecutable = 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd',
    [string]$JavaExecutable = 'java.exe'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Assert-ExitCode([int]$Expected) {
    if ($LASTEXITCODE -ne $Expected) {
        throw "Unexpected exit code $LASTEXITCODE (expected $Expected)."
    }
}

try {
    if (-not $SkipInstall) {
        & $MavenExecutable -f (Join-Path $root 'backend\pom.xml') package -DskipTests
        Assert-ExitCode 0
    }
    $jar = Join-Path $root 'backend\target\portfolio-agent.jar'
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
        throw "Packaged jar not found: $jar"
    }

    $arguments = @(
        '-Dloader.main=com.portfolio.agent.evaluation.cli.EvalCli',
        '-Dspring.main.web-application-type=none',
        '-Dportfolio.retrieval.profile=KEYWORD_ONLY'
    )
    $realProvider = $CliArgs -contains '--authorize-real-provider'
    if (-not $realProvider) {
        $arguments += '-Dportfolio.model-runtime.enabled=false'
    }
    $arguments += @(
        '-cp', $jar,
        'org.springframework.boot.loader.launch.PropertiesLauncher',
        $Command) + $CliArgs

    & $JavaExecutable @arguments
    exit $LASTEXITCODE
}
catch {
    Write-Error $_
    exit 2
}
