param(
    [switch]$UnitOnly,
    [string]$ModelDirectory = '',
    [string]$BundleDirectory = '',
    [string]$CasesPath = 'backend\src\test\resources\retrieval-benchmark\cases.json',
    [string]$OutputDirectory = 'output\retrieval-benchmark\wave-0'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$pom = Join-Path $root 'backend\pom.xml'
$jar = Join-Path $root 'backend\target\portfolio-agent.jar'
$maven = if (-not [string]::IsNullOrWhiteSpace($env:BENCHMARK_MAVEN)) {
    $env:BENCHMARK_MAVEN
} elseif (Test-Path -LiteralPath 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd') {
    'C:\tools\apache-maven-3.9.9\bin\mvn.cmd'
} else {
    (Get-Command mvn.cmd -ErrorAction Stop).Source
}
$java = if (-not [string]::IsNullOrWhiteSpace($env:BENCHMARK_JAVA)) {
    $env:BENCHMARK_JAVA
} else {
    (Get-Command java.exe -ErrorAction Stop).Source
}
$gateTests = @(
    'RetrievalQueryNormalizerTest',
    'KeywordRetrieverTest',
    'VectorRetrieverTest',
    'ReciprocalRankFusionTest',
    'RetrievalContextValidatorTest',
    'LocalRetrievalCoordinatorTest',
    'PortfolioAgentRuntimeRetrievalTest'
) -join ','

function Resolve-RepositoryPath([string]$Value) {
    if ([IO.Path]::IsPathRooted($Value)) {
        return [IO.Path]::GetFullPath($Value)
    }
    return [IO.Path]::GetFullPath((Join-Path $root $Value))
}

function Assert-ExitCode([string]$Label) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Label failed with exit code $LASTEXITCODE."
    }
}

$hasModel = -not [string]::IsNullOrWhiteSpace($ModelDirectory)
if ($UnitOnly.IsPresent -eq $hasModel) {
    throw 'Exactly one retrieval benchmark mode is required: -UnitOnly or -ModelDirectory.'
}

& $maven -f $pom -DskipFrontend=true "-Dtest=$gateTests" test
Assert-ExitCode 'Local retrieval component gates'

if ($UnitOnly.IsPresent) {
    Write-Output 'Local retrieval unit gates passed; real-model comparison was not run.'
    exit 0
}

$hasBundle = -not [string]::IsNullOrWhiteSpace($BundleDirectory)
if (-not $hasBundle) {
    throw 'Real retrieval benchmark mode requires -BundleDirectory.'
}
$resolvedBundle = Resolve-RepositoryPath $BundleDirectory
if (-not (Test-Path -LiteralPath $resolvedBundle -PathType Container)) {
    throw 'Retrieval benchmark Bundle directory does not exist.'
}
$portfolio = Join-Path $resolvedBundle 'portfolio.json'
if (-not (Test-Path -LiteralPath $portfolio -PathType Leaf)) {
    throw 'Retrieval benchmark Bundle portfolio does not exist.'
}
$resolvedModel = Resolve-RepositoryPath $ModelDirectory
if (-not (Test-Path -LiteralPath $resolvedModel -PathType Container)) {
    throw 'Retrieval benchmark model directory does not exist.'
}
$resolvedCases = Resolve-RepositoryPath $CasesPath
if (-not (Test-Path -LiteralPath $resolvedCases -PathType Leaf)) {
    throw 'Retrieval benchmark cases file does not exist.'
}
$resolvedOutput = Resolve-RepositoryPath $OutputDirectory
if (Test-Path -LiteralPath $resolvedOutput) {
    throw 'Retrieval benchmark output directory must not already exist.'
}
$outputParent = Split-Path -Parent $resolvedOutput
New-Item -ItemType Directory -Force -Path $outputParent | Out-Null

& $maven -f $pom -DskipFrontend=true `
    '-Dtest=OnnxLocalEmbeddingAdapterSmokeTest,RetrievalBenchmarkTest' `
    "-Dportfolio.embedding.modelDir=$resolvedModel" test
Assert-ExitCode 'Local retrieval real-model acceptance'

& $maven -f $pom -DskipFrontend=true `
    '-Dtest=LocalEmbeddingPerformanceTest' `
    '-DargLine=-XX:NativeMemoryTracking=summary' `
    "-Dportfolio.embedding.modelDir=$resolvedModel" test
Assert-ExitCode 'Local embedding performance gate'

& $maven -f $pom -DskipFrontend=true -DskipTests package
Assert-ExitCode 'Retrieval comparison executable JAR package'

$portfolioContent = Get-Content -LiteralPath $portfolio -Raw -Encoding UTF8 |
    ConvertFrom-Json
$contentVersion = [string]$portfolioContent.contentVersion
if ($contentVersion -notmatch '^[0-9]{4}-[0-9]{2}-[0-9]{2}\.[0-9]+$') {
    throw 'Public portfolio contentVersion is invalid.'
}
$arguments = @(
    '-Dloader.main=com.portfolio.agent.release.RetrievalComparisonCli',
    '-cp', $jar,
    'org.springframework.boot.loader.launch.PropertiesLauncher',
    '--portfolio', $portfolio,
    '--cases', $resolvedCases,
    '--model-dir', $resolvedModel,
    '--output-dir', $resolvedOutput,
    '--valid-from', $contentVersion.Substring(0, 10)
)
& $java @arguments
Assert-ExitCode 'RetrievalComparisonCli'

if (-not (Test-Path -LiteralPath (Join-Path $resolvedOutput 'comparison.json') `
            -PathType Leaf) -or
        -not (Test-Path -LiteralPath (Join-Path $resolvedOutput 'comparison.md') `
            -PathType Leaf)) {
    throw 'Retrieval comparison reports are incomplete.'
}

Write-Output 'Local retrieval real-model comparison passed.'
