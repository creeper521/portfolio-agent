$ErrorActionPreference = 'Stop'
$gate = Join-Path $PSScriptRoot 'run-packaged-jvm-restart-api-gate.ps1'
$source = Get-Content -LiteralPath $gate -Raw

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

$command = Get-Command $gate
foreach ($parameter in @(
        'JarPath', 'DockerExecutable', 'ApplicationPort',
        'ReadinessTimeoutSeconds'
    )) {
    Assert-True ($command.Parameters.ContainsKey($parameter)) `
        "JVM restart gate is missing parameter $parameter."
}
foreach ($required in @(
        '-Lane JVM_RESTART',
        '-ContextMode POSTGRESQL',
        'RandomNumberGenerator',
        'PACKAGED_JVM_RESTART_GATE_PASS',
        'browser=NOT_RUN'
    )) {
    Assert-True ($source -match [regex]::Escape($required)) `
        "JVM restart gate is missing boundary $required."
}

$missingJar = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('missing-jvm-restart-' + [guid]::NewGuid().ToString('N') + '.jar')
$failed = $false
try {
    & $gate -JarPath $missingJar -DockerExecutable 'missing-docker-command'
}
catch {
    $failed = $_.Exception.Message -match 'Packaged JAR is missing'
}
Assert-True $failed 'Missing JAR must fail before Docker is invoked.'

Write-Output 'run-packaged-jvm-restart-api-gate tests passed'
