$ErrorActionPreference = 'Stop'
$script = Join-Path $PSScriptRoot 'run-local-retrieval-benchmark.ps1'
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) `
    ('run-local-retrieval-benchmark-' + [guid]::NewGuid().ToString('N'))
$callLog = Join-Path $fixtureRoot 'calls.log'
$fakeMaven = Join-Path $fixtureRoot 'mvn.cmd'
$fakeJava = Join-Path $fixtureRoot 'java.cmd'
$fakeJavaScript = Join-Path $fixtureRoot 'fake-java.ps1'
$modelDirectory = Join-Path $fixtureRoot 'model'
$casesPath = Join-Path $fixtureRoot 'cases.json'
$outputDirectory = Join-Path $fixtureRoot 'output'

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

function Invoke-Benchmark([string[]]$Arguments) {
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $lines = @(& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $script @Arguments 2>&1 | ForEach-Object { "$_" })
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorAction
    }
    return @{
        ExitCode = $exitCode
        Output = ($lines -join [Environment]::NewLine)
    }
}

function Reset-Fixture {
    Set-Content -LiteralPath $callLog -Value '' -Encoding UTF8
    Remove-Item -LiteralPath $outputDirectory -Recurse -Force -ErrorAction SilentlyContinue
    $env:BENCHMARK_FAIL_MATCH = ''
}

try {
    New-Item -ItemType Directory -Force -Path $fixtureRoot, $modelDirectory | Out-Null
    Set-Content -LiteralPath $casesPath -Value '{}' -Encoding UTF8
    @'
@echo off
echo MAVEN %*>>"%BENCHMARK_CALL_LOG%"
if "%BENCHMARK_FAIL_MATCH%"=="" exit /b 0
echo %* | findstr /C:"%BENCHMARK_FAIL_MATCH%" >nul
if %ERRORLEVEL% EQU 0 exit /b 9
exit /b 0
'@ | Set-Content -LiteralPath $fakeMaven -Encoding ASCII
    @'
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
Add-Content -LiteralPath $env:BENCHMARK_CALL_LOG `
    -Value ('JAVA ' + ($Arguments -join ' ')) -Encoding UTF8
$outputIndex = [Array]::IndexOf($Arguments, '--output-dir')
if ($outputIndex -ge 0 -and $outputIndex + 1 -lt $Arguments.Count) {
    $output = $Arguments[$outputIndex + 1]
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    Set-Content -LiteralPath (Join-Path $output 'comparison.json') `
        -Value '{}' -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $output 'comparison.md') `
        -Value '# comparison' -Encoding UTF8
}
if (-not [string]::IsNullOrWhiteSpace($env:BENCHMARK_FAIL_MATCH) -and
        (($Arguments -join ' ') -like ('*' + $env:BENCHMARK_FAIL_MATCH + '*'))) {
    exit 9
}
exit 0
'@ | Set-Content -LiteralPath $fakeJavaScript -Encoding UTF8
    @"
@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$fakeJavaScript" %*
exit /b %ERRORLEVEL%
"@ | Set-Content -LiteralPath $fakeJava -Encoding ASCII

    $env:BENCHMARK_MAVEN = $fakeMaven
    $env:BENCHMARK_JAVA = $fakeJava
    $env:BENCHMARK_CALL_LOG = $callLog

    Reset-Fixture
    $result = Invoke-Benchmark @()
    Assert-True ($result.ExitCode -ne 0) 'No mode must fail.'

    Reset-Fixture
    $result = Invoke-Benchmark @('-UnitOnly', '-ModelDirectory', $modelDirectory)
    Assert-True ($result.ExitCode -ne 0) `
        'Unit-only and model-directory modes must be mutually exclusive.'

    Reset-Fixture
    $result = Invoke-Benchmark @('-UnitOnly')
    $calls = Get-Content -Raw -LiteralPath $callLog
    Assert-True ($result.ExitCode -eq 0) 'Unit-only mode must pass component gates.'
    Assert-True ($result.Output -match [regex]::Escape(
            'Local retrieval unit gates passed; real-model comparison was not run.')) `
        'Unit-only mode must print the exact truthful success message.'
    Assert-True ($calls -notmatch 'RetrievalBenchmarkTest') `
        'Unit-only mode must not run real-model acceptance.'
    Assert-True ($calls -notmatch 'LocalEmbeddingPerformanceTest') `
        'Unit-only mode must not run the performance test.'
    Assert-True ($calls -notmatch 'RetrievalComparisonCli') `
        'Unit-only mode must not run the comparison CLI.'

    Reset-Fixture
    $missingModel = Join-Path $fixtureRoot 'missing-model'
    $result = Invoke-Benchmark @('-ModelDirectory', $missingModel)
    Assert-True ($result.ExitCode -ne 0) `
        'Real mode must reject a nonexistent model directory.'

    Reset-Fixture
    $result = Invoke-Benchmark @(
        '-ModelDirectory', $modelDirectory,
        '-CasesPath', $casesPath,
        '-OutputDirectory', $outputDirectory)
    $calls = Get-Content -Raw -LiteralPath $callLog
    $callLines = @(Get-Content -LiteralPath $callLog |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    Assert-True ($result.ExitCode -eq 0) 'Real mode must complete all required stages.'
    Assert-True ($callLines.Count -eq 5) `
        'Real mode must execute exactly five stages.'
    Assert-True ($callLines[0] -match 'RetrievalQueryNormalizerTest') `
        'Stage 1 must be component gates.'
    Assert-True ($callLines[1] -match
            'OnnxLocalEmbeddingAdapterSmokeTest,RetrievalBenchmarkTest') `
        'Stage 2 must be real-model acceptance.'
    Assert-True ($callLines[2] -match 'LocalEmbeddingPerformanceTest') `
        'Stage 3 must be the performance gate.'
    Assert-True ($callLines[3] -match 'package') `
        'Stage 4 must package the executable JAR.'
    Assert-True ($callLines[4] -match 'RetrievalComparisonCli') `
        'Stage 5 must publish the comparison reports.'
    Assert-True ($calls -match 'RetrievalQueryNormalizerTest') `
        'Real mode must run component gates.'
    Assert-True ($calls -match
            'OnnxLocalEmbeddingAdapterSmokeTest,RetrievalBenchmarkTest') `
        'Real mode must run smoke and real-model acceptance tests.'
    Assert-True ($calls -match 'LocalEmbeddingPerformanceTest') `
        'Real mode must run the performance test.'
    Assert-True ($calls -match 'package') 'Real mode must package the executable JAR.'
    Assert-True ($calls -match 'PropertiesLauncher') `
        'Real mode must invoke the comparison CLI through PropertiesLauncher.'
    Assert-True ($calls -match 'RetrievalComparisonCli') `
        'Real mode must invoke RetrievalComparisonCli.'
    Assert-True (Test-Path -LiteralPath (Join-Path $outputDirectory 'comparison.json') `
            -PathType Leaf) 'Real mode must require comparison.json.'
    Assert-True (Test-Path -LiteralPath (Join-Path $outputDirectory 'comparison.md') `
            -PathType Leaf) 'Real mode must require comparison.md.'
    Assert-True ($result.Output -match [regex]::Escape(
            'Local retrieval real-model comparison passed.')) `
        'Real mode must print the exact success message.'

    Reset-Fixture
    $env:BENCHMARK_FAIL_MATCH = 'LocalEmbeddingPerformanceTest'
    $result = Invoke-Benchmark @(
        '-ModelDirectory', $modelDirectory,
        '-CasesPath', $casesPath,
        '-OutputDirectory', $outputDirectory)
    Assert-True ($result.ExitCode -ne 0) 'A failed real-mode stage must return nonzero.'
    Assert-True ($result.Output -notmatch [regex]::Escape(
            'Local retrieval real-model comparison passed.')) `
        'A failed real-mode stage must never print real success.'

    Write-Output 'run-local-retrieval-benchmark tests passed'
}
finally {
    Remove-Item Env:BENCHMARK_MAVEN -ErrorAction SilentlyContinue
    Remove-Item Env:BENCHMARK_JAVA -ErrorAction SilentlyContinue
    Remove-Item Env:BENCHMARK_CALL_LOG -ErrorAction SilentlyContinue
    Remove-Item Env:BENCHMARK_FAIL_MATCH -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
}
