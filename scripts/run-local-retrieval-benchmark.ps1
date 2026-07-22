param(
    [string]$ModelDirectory = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$pom = Join-Path $root 'backend\pom.xml'
$maven = if (Test-Path -LiteralPath 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd') {
    'C:\tools\apache-maven-3.9.9\bin\mvn.cmd'
} else {
    (Get-Command mvn.cmd -ErrorAction Stop).Source
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

& $maven -f $pom -DskipFrontend=true "-Dtest=$gateTests" test
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (-not [string]::IsNullOrWhiteSpace($ModelDirectory)) {
    $resolvedModel = (Resolve-Path -LiteralPath $ModelDirectory -ErrorAction Stop).Path
    & $maven -f $pom -DskipFrontend=true `
        '-Dtest=OnnxLocalEmbeddingAdapterSmokeTest,RetrievalBenchmarkTest' `
        "-Dportfolio.embedding.modelDir=$resolvedModel" test
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & $maven -f $pom -DskipFrontend=true `
        '-Dtest=LocalEmbeddingPerformanceTest' `
        '-DargLine=-XX:NativeMemoryTracking=summary' `
        "-Dportfolio.embedding.modelDir=$resolvedModel" test
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Output 'Local retrieval correctness gate passed.'
