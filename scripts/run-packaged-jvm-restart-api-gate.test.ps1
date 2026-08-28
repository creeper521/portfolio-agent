$ErrorActionPreference = 'Stop'
$gate = Join-Path $PSScriptRoot 'run-packaged-jvm-restart-api-gate.ps1'
$source = Get-Content -LiteralPath $gate -Raw

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

$command = Get-Command $gate
foreach ($parameter in @(
        'JarPath', 'DockerExecutable', 'JavaExecutable', 'NpmExecutable',
        'ApplicationPort',
        'ReadinessTimeoutSeconds'
    )) {
    Assert-True ($command.Parameters.ContainsKey($parameter)) `
        "JVM restart gate is missing parameter $parameter."
}
foreach ($required in @(
        '-Lane JVM_RESTART',
        '-ContextMode POSTGRESQL',
        '-SkipPlaywright',
        'run-packaged-jvm-restart-browser-gate.ps1',
        '-ContextDatabaseUrl $databaseUrl',
        '-CurrentTokenKey $tokenKeyBase64',
        '-CurrentPayloadKey $payloadKeyBase64',
        '-JavaExecutable $JavaExecutable',
        '-NpmExecutable $NpmExecutable',
        'RandomNumberGenerator',
        'PACKAGED_JVM_RESTART_API_PASS',
        'PACKAGED_JVM_RESTART_BROWSER_PASS',
        'PACKAGED_JVM_RESTART_GATE_PASS',
        'api=PASS',
        'browser=PASS'
    )) {
    Assert-True ($source -match [regex]::Escape($required)) `
        "JVM restart gate is missing boundary $required."
}

$apiInvocation = $source.IndexOf("scripts\run-jar-e2e.ps1")
$skipPlaywright = $source.IndexOf('-SkipPlaywright')
$browserInvocation = $source.IndexOf(
    'scripts\run-packaged-jvm-restart-browser-gate.ps1'
)
$passOutput = $source.IndexOf('PACKAGED_JVM_RESTART_GATE_PASS')
Assert-True ($apiInvocation -ge 0 -and $skipPlaywright -gt $apiInvocation) `
    'Generic run-jar JVM_RESTART lane must remain API-only.'
Assert-True ($browserInvocation -gt $skipPlaywright -and
        $passOutput -gt $browserInvocation) `
    'Dedicated browser restart proof must finish before the aggregate PASS.'
Assert-True ($source -notmatch 'browser=NOT_RUN') `
    'Aggregate JVM restart gate must no longer claim that Browser was not run.'

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
