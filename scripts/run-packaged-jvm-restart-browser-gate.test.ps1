$ErrorActionPreference = 'Stop'
$gate = Join-Path $PSScriptRoot 'run-packaged-jvm-restart-browser-gate.ps1'
$source = Get-Content -LiteralPath $gate -Raw

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

$tokens = $null
$parseErrors = $null
[System.Management.Automation.Language.Parser]::ParseFile(
    $gate,
    [ref]$tokens,
    [ref]$parseErrors
) | Out-Null
Assert-True ($parseErrors.Count -eq 0) `
    'JVM restart browser gate must parse in Windows PowerShell.'

$command = Get-Command $gate
foreach ($parameter in @(
        'JarPath',
        'ContextDatabaseUrl',
        'ContextDatabaseUsername',
        'ContextDatabasePassword',
        'CurrentTokenKeyId',
        'CurrentTokenKey',
        'CurrentPayloadKeyId',
        'CurrentPayloadKey',
        'Port',
        'JavaExecutable',
        'NpmExecutable',
        'ReadinessTimeoutSeconds',
        'BrowserTimeoutSeconds'
    )) {
    Assert-True ($command.Parameters.ContainsKey($parameter)) `
        "JVM restart browser gate is missing parameter $parameter."
}

foreach ($required in @(
        'PLAYWRIGHT_EXTERNAL_SERVER',
        'PLAYWRIGHT_REAL_API',
        'PLAYWRIGHT_JVM_RESTART_BROWSER',
        'PLAYWRIGHT_JVM_RESTART_COORDINATION_DIR',
        'browser-ready.signal',
        'server-restarted.signal',
        'BROWSER_READY',
        'SERVER_RESTARTED',
        'portfolio-jvm-restart-browser-',
        'Start-PackagedJvm $jvm1StdOut $jvm1StdErr',
        'Start-PackagedJvm $jvm2StdOut $jvm2StdErr',
        'Wait-PackagedReadiness $jvm1',
        'Wait-PackagedReadiness $jvm2',
        'Move-Item -LiteralPath $signalTemporaryPath',
        '[System.Text.UTF8Encoding]::new($false)',
        'Assert-ClosedCoordinationDirectory',
        'Assert-LogPrivacy',
        'Stop-ControlledBrowserTree',
        'taskkill.exe /PID $controlledId /T /F',
        'Remove-ControlledArtifacts',
        '[string]::Equals(',
        '[StringComparison]::Ordinal',
        'PACKAGED_JVM_RESTART_BROWSER_PASS',
        'browser=PASS',
        'conversation=RECOVERED',
        'replay=EXACT_PUBLIC_TURN'
    )) {
    Assert-True ($source -match [regex]::Escape($required)) `
        "JVM restart browser gate is missing boundary $required."
}

$firstStop = $source.IndexOf("Stop-ControlledProcess `$jvm1")
$secondStart = $source.LastIndexOf('Start-PackagedJvm $jvm2StdOut')
$secondReady = $source.LastIndexOf('Wait-PackagedReadiness $jvm2')
$restartPublish = $source.IndexOf('Move-Item -LiteralPath $signalTemporaryPath')
Assert-True ($firstStop -ge 0 -and $secondStart -gt $firstStop) `
    'JVM #1 must stop before JVM #2 starts.'
Assert-True ($secondReady -gt $secondStart -and $restartPublish -gt $secondReady) `
    'SERVER_RESTARTED must be published only after JVM #2 readiness.'

Assert-True ($source -notmatch 'route\.fulfill') `
    'Packaged JVM restart browser gate must not use HTTP route injection.'
Assert-True ($source -notmatch 'Write-(?:Output|Host)\s+\$(?:content|secret|token|password)') `
    'JVM restart browser gate must not print private material.'

$missingJar = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('missing-jvm-restart-browser-' + [guid]::NewGuid().ToString('N') + '.jar')
$key = [Convert]::ToBase64String([byte[]]::new(32))
$failed = $false
try {
    & $gate `
        -JarPath $missingJar `
        -ContextDatabaseUrl 'jdbc:postgresql://127.0.0.1:5432/test' `
        -ContextDatabaseUsername 'test' `
        -ContextDatabasePassword 'not-printed' `
        -CurrentTokenKeyId 'test-token-key' `
        -CurrentTokenKey $key `
        -CurrentPayloadKeyId 'test-payload-key' `
        -CurrentPayloadKey $key `
        -JavaExecutable 'missing-java-command' `
        -NpmExecutable 'missing-npm-command'
}
catch {
    $failed = $_.Exception.Message -match 'Packaged JAR is missing'
}
Assert-True $failed 'Missing JAR must fail before process startup.'

Write-Output 'run-packaged-jvm-restart-browser-gate tests passed'
