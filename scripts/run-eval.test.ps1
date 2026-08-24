$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runner = Join-Path $PSScriptRoot 'run-eval.ps1'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('run-eval-test-' + [guid]::NewGuid().ToString('N'))
$fakeMaven = Join-Path $fixtureRoot 'fake-maven.cmd'
$fakeJava = Join-Path $fixtureRoot 'fake-java.cmd'
$javaArguments = Join-Path $fixtureRoot 'java-arguments.txt'

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

function Invoke-Runner([string[]]$AdditionalArguments = @()) {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $runner @AdditionalArguments 2>&1 | Out-String)
        return @{
            ExitCode = $LASTEXITCODE
            Output = $output
        }
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

try {
    New-Item -ItemType Directory -Path $fixtureRoot -Force | Out-Null
    [System.IO.File]::WriteAllText($fakeMaven, "@echo off`r`nexit /b 0`r`n",
        [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText(
        $fakeJava,
        "@echo off`r`necho %* > `"$javaArguments`"`r`nif `"%FAKE_JAVA_EXIT%`"==`"`" exit /b 0`r`nexit /b %FAKE_JAVA_EXIT%`r`n",
        [System.Text.UTF8Encoding]::new($false))

    $manifest = Join-Path $fixtureRoot 'suite.json'
    $policy = Join-Path $fixtureRoot 'policy.json'
    [System.IO.File]::WriteAllText($manifest, '{}', [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText($policy, '{}', [System.Text.UTF8Encoding]::new($false))
    $outputDir = Join-Path $fixtureRoot 'out'

    $passResult = Invoke-Runner @(
        'validate',
        '-CliArgs', @('--manifest', $manifest, '--policy', $policy,
            '--output-dir', $outputDir),
        '-MavenExecutable', $fakeMaven,
        '-JavaExecutable', $fakeJava)
    Assert-True ($passResult.ExitCode -eq 0) `
        "validate must exit 0. Output: $($passResult.Output)"

    $captured = Get-Content -LiteralPath $javaArguments -Raw
    Assert-True ($captured -match 'loader\.main=com\.portfolio\.agent\.evaluation\.cli\.EvalCli') `
        'runner must invoke the eval CLI main class.'
    Assert-True ($captured -match '\.jar') `
        'runner must launch the packaged jar.'
    Assert-True ($captured -notmatch 'authorize-real-provider') `
        'validate must not carry the real provider flag.'
    $runnerSource = Get-Content -LiteralPath $runner -Raw
    Assert-True ($runnerSource -match 'portfolio\.model-runtime\.enabled=false') `
        'offline eval must disable the configured model runtime.'
    Assert-True ($runnerSource -notmatch 'portfolio\.conversational-model|provider-ref') `
        'offline eval must not retain the retired single-provider authority.'
    Assert-True ($runnerSource -notmatch ('portfolio\.model-' + 'expression')) `
        'offline eval must not set the retired model property prefix.'

    $failResult = Invoke-Runner @(
        'validate', '--manifest', $manifest, '--policy', $policy,
        '--output-dir', (Join-Path $fixtureRoot 'out2'),
        '-MavenExecutable', $fakeMaven)
    $env:FAKE_JAVA_EXIT = '1'
    try {
        $failResult = Invoke-Runner @(
            'validate',
            '-CliArgs', @('--manifest', $manifest, '--policy', $policy,
                '--output-dir', (Join-Path $fixtureRoot 'out3')),
            '-MavenExecutable', $fakeMaven,
            '-JavaExecutable', $fakeJava)
    }
    finally {
        Remove-Item Env:FAKE_JAVA_EXIT -ErrorAction SilentlyContinue
    }
    Assert-True ($failResult.ExitCode -eq 1) `
        "a failing java exit must map to 1. Output: $($failResult.Output)"

    Write-Output 'run-eval tests passed'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        if (-not ([System.IO.Path]::GetFileName($resolved)).StartsWith('run-eval-test-')) {
            throw "Refusing to remove unverified fixture path: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
