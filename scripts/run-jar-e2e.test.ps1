$ErrorActionPreference = 'Stop'

$runner = Join-Path $PSScriptRoot 'run-jar-e2e.ps1'
$releaseVerifier = Join-Path $PSScriptRoot 'verify-release.ps1'
$root = Split-Path -Parent $PSScriptRoot
$sourceJar = Join-Path $root 'backend\target\portfolio-agent.jar'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('portfolio runner with spaces ' + [guid]::NewGuid())
$spacedJar = Join-Path $fixtureRoot 'packaged app\portfolio agent.jar'
$fakeNpm = Join-Path $fixtureRoot 'fake npm\npm with spaces.cmd'
$successfulFakeNpm = Join-Path $fixtureRoot 'fake npm\npm success.cmd'
$fakeJava = Join-Path $fixtureRoot 'fake java\java with spaces.cmd'
$javaArgumentCapture = Join-Path $fixtureRoot 'java-arguments.txt'
$stdoutValidationRunner = Join-Path $fixtureRoot 'run-jar-e2e-stdout-fixture.ps1'
$latePlaintextRunner = Join-Path $fixtureRoot 'run-jar-e2e-late-plaintext.ps1'
$lateLeakRunner = Join-Path $fixtureRoot 'run-jar-e2e-late-leak.ps1'
$port = 43173
$runnerSource = Get-Content -LiteralPath $runner -Raw -Encoding UTF8
if ($runnerSource -notmatch [regex]::Escape('/api/portfolio')) {
    throw 'Packaged runner must load the unversioned portfolio snapshot.'
}
if ($runnerSource -notmatch [regex]::Escape('/api/client-diagnostics')) {
    throw 'Packaged runner must use the unversioned diagnostic endpoint.'
}

function Get-EnvironmentSnapshot([string]$Name) {
    $value = [System.Environment]::GetEnvironmentVariable(
        $Name,
        [System.EnvironmentVariableTarget]::Process
    )
    return @{
        Exists = $null -ne $value
        Value = $value
    }
}

function Restore-EnvironmentVariable([string]$Name, [hashtable]$Snapshot) {
    $value = if ($Snapshot.Exists) { [string]$Snapshot.Value } else { $null }
    [System.Environment]::SetEnvironmentVariable(
        $Name,
        $value,
        [System.EnvironmentVariableTarget]::Process
    )
}

$environment = @{
    PORTFOLIO_CONVERSATION_CONTEXT_MODE = Get-EnvironmentSnapshot `
        'PORTFOLIO_CONVERSATION_CONTEXT_MODE'
    PORTFOLIO_CONTEXT_DATABASE_URL = Get-EnvironmentSnapshot `
        'PORTFOLIO_CONTEXT_DATABASE_URL'
    PORTFOLIO_CONTEXT_DATABASE_USERNAME = Get-EnvironmentSnapshot `
        'PORTFOLIO_CONTEXT_DATABASE_USERNAME'
    PORTFOLIO_CONTEXT_DATABASE_PASSWORD = Get-EnvironmentSnapshot `
        'PORTFOLIO_CONTEXT_DATABASE_PASSWORD'
    PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID = Get-EnvironmentSnapshot `
        'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID'
    PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY = Get-EnvironmentSnapshot `
        'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY'
    PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID = Get-EnvironmentSnapshot `
        'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID'
    PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY = Get-EnvironmentSnapshot `
        'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY'
    PLAYWRIGHT_EXTERNAL_SERVER = Get-EnvironmentSnapshot 'PLAYWRIGHT_EXTERNAL_SERVER'
    PLAYWRIGHT_REAL_API = Get-EnvironmentSnapshot 'PLAYWRIGHT_REAL_API'
    PLAYWRIGHT_BASE_URL = Get-EnvironmentSnapshot 'PLAYWRIGHT_BASE_URL'
    PLAYWRIGHT_REAL_RETRIEVAL = Get-EnvironmentSnapshot 'PLAYWRIGHT_REAL_RETRIEVAL'
    PORTFOLIO_MODEL_ENABLED = Get-EnvironmentSnapshot 'PORTFOLIO_MODEL_ENABLED'
    PORTFOLIO_MODEL_DATA_POLICY_APPROVED = Get-EnvironmentSnapshot `
        'PORTFOLIO_MODEL_DATA_POLICY_APPROVED'
    PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED = Get-EnvironmentSnapshot `
        'PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED'
    PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED = Get-EnvironmentSnapshot `
        'PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED'
    PORTFOLIO_AGENT_DEEPSEEK_API_KEY = Get-EnvironmentSnapshot `
        'PORTFOLIO_AGENT_DEEPSEEK_API_KEY'
    PORTFOLIO_MODEL_TIMEOUT = Get-EnvironmentSnapshot 'PORTFOLIO_MODEL_TIMEOUT'
}

try {
    $contextTestKey = [Convert]::ToBase64String([byte[]](0..31))
    [Environment]::SetEnvironmentVariable(
        'PORTFOLIO_CONTEXT_DATABASE_URL',
        'jdbc:postgresql://127.0.0.1:54329/portfolio_context_dev', 'Process')
    [Environment]::SetEnvironmentVariable(
        'PORTFOLIO_CONTEXT_DATABASE_USERNAME', 'context-user', 'Process')
    [Environment]::SetEnvironmentVariable(
        'PORTFOLIO_CONTEXT_DATABASE_PASSWORD', 'context-password-123', 'Process')
    [Environment]::SetEnvironmentVariable(
        'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID', 'current-token', 'Process')
    [Environment]::SetEnvironmentVariable(
        'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY', $contextTestKey, 'Process')
    [Environment]::SetEnvironmentVariable(
        'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID', 'current-payload', 'Process')
    [Environment]::SetEnvironmentVariable(
        'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY', $contextTestKey, 'Process')

    if (-not (Test-Path -LiteralPath $sourceJar -PathType Leaf)) {
        throw 'Packaged JAR is required before running run-jar-e2e tests.'
    }

    $runnerCommand = Get-Command $runner
    foreach ($parameterName in @(
        'JarPath',
        'KeytoolExecutable',
        'NpmExecutable',
        'Port',
        'RequireLiveProvider',
        'Lane',
        'SkipPlaywright'
    )) {
        if (-not $runnerCommand.Parameters.ContainsKey($parameterName)) {
            throw "Runner is missing testable parameter seam '$parameterName'."
        }
    }
    if ($runnerCommand.Parameters.ContainsKey('LiveProviderResponseCleanup')) {
        throw 'Production runner must not expose a replaceable cleanup scriptblock.'
    }
    $releaseCommand = Get-Command $releaseVerifier
    if (-not $releaseCommand.Parameters.ContainsKey('RequireLiveProvider')) {
        throw "Release verifier is missing parameter seam 'RequireLiveProvider'."
    }
    $releaseSource = Get-Content -LiteralPath $releaseVerifier -Raw
    if ($releaseSource -notmatch 'assert-live-public-turn-response\.test\.ps1') {
        throw 'Release verifier does not run the final PublicAgentTurn Provider assertion tests.'
    }
    $runnerSource = Get-Content -LiteralPath $runner -Raw
    if ($runnerSource -notmatch "'PROJECT_DISCUSSION'") {
        throw 'Packaged runner must expose a dedicated project discussion lane.'
    }
    if ($runnerSource -notmatch 'PLAYWRIGHT_PROJECT_DISCUSSION') {
        throw 'Project discussion lane must select its dedicated Playwright spec.'
    }
    if ($runnerSource -notmatch 'assert-live-project-discussion-context\.ps1') {
        throw 'Project discussion lane must run the privacy-safe live aggregate gate.'
    }
    if ($runnerSource -notmatch "'PROJECT_DISCUSSION_EXPIRY'" -or
            $runnerSource -notmatch 'PLAYWRIGHT_PROJECT_DISCUSSION_EXPIRY' -or
            $runnerSource -notmatch 'discussion-ttl=3s') {
        throw 'Packaged runner must expose a deterministic short-TTL discussion lane.'
    }
    if ($runnerSource -notmatch 'provider-probe\\invoke-live-provider-probe\.ps1') {
        throw 'Packaged runner must call the shared Live Provider probe.'
    }
    if ($runnerSource -match '/api/v2|stp-v[123]') {
        throw 'Packaged runner must not reference retired versioned Agent contracts.'
    }
    foreach ($lane in @('DEFAULT', 'ADMISSION', 'BODY_STALL', 'DEPTH_TWO', 'CONTENT_ONLY', 'LIVE', 'JVM_RESTART')) {
        if ($runnerSource -notmatch "(?<![A-Z_])$lane(?![A-Z_])") {
            throw "Packaged runner is missing explicit lane '$lane'."
        }
    }
    foreach ($restartEvidence in @(
        'PACKAGED_JVM_RESTART_API_PASS',
        'processIdentity=CHANGED',
        'conversation=RECOVERED',
        'replay=EXACT_PUBLIC_TURN'
    )) {
        if ($runnerSource -notmatch [regex]::Escape($restartEvidence)) {
            throw "Packaged JVM_RESTART lane is missing '$restartEvidence'."
        }
    }
    foreach ($scenarioEvidence in @(
        'run-agent-scenario-runtime.ps1',
        'AGENT_SCENARIO_RUNTIME_BASELINE',
        'did not execute every registered case'
    )) {
        if ($runnerSource -notmatch [regex]::Escape($scenarioEvidence)) {
            throw "Packaged default lane is missing scenario evidence '$scenarioEvidence'."
        }
    }
    foreach ($bodyStallEvidence in @(
        'start-provider-body-stall-https.ps1',
        'jdk.net.hosts.file',
        'javax.net.ssl.trustStore',
        'body-stall-fixture-key',
        'PLAYWRIGHT_PROVIDER_STALL_COORDINATION_URL',
        'Remove-BodyStallFixture'
    )) {
        if ($runnerSource -notmatch [regex]::Escape($bodyStallEvidence)) {
            throw "Packaged BODY_STALL lane is missing '$bodyStallEvidence'."
        }
    }
    foreach ($restoredName in @(
        'PORTFOLIO_CONVERSATION_CONTEXT_MODE',
        'PORTFOLIO_CONTEXT_DATABASE_URL',
        'PORTFOLIO_CONTEXT_DATABASE_USERNAME',
        'PORTFOLIO_CONTEXT_DATABASE_PASSWORD',
        'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID',
        'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY',
        'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID',
        'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY',
        'PORTFOLIO_MODEL_PROVIDER',
        'PORTFOLIO_AGENT_DEEPSEEK_API_KEY',
        'PORTFOLIO_AGENT_GLM_API_KEY',
        'PLAYWRIGHT_PROVIDER_STALL_COORDINATION_URL'
    )) {
        if ($runnerSource -notmatch (
                'function Assert-EarlyRunnerEnvironmentRestored[\s\S]+?' +
                [regex]::Escape("'$restoredName'") +
                '[\s\S]+?BODY_STALL early-failure environment restored')) {
            throw "BODY_STALL early restore assertion is missing '$restoredName'."
        }
    }
    if ($runnerSource -notmatch
            'function Complete-BodyStallEarlyFailure[\s\S]+?try\s*\{[\s\S]+?Remove-BodyStallFixture[\s\S]+?finally\s*\{[\s\S]+?SetEnvironmentVariable[\s\S]+?Assert-EarlyRunnerEnvironmentRestored') {
        throw 'BODY_STALL cleanup must restore and assert environment from a finally block.'
    }
    if (([regex]::Matches(
            $runnerSource,
            '(?m)^\s*Complete-BodyStallEarlyFailure\s*$')).Count -ne 2) {
        throw 'Both BODY_STALL early catches must use the cleanup-safe restore helper.'
    }
    if ($runnerSource -match '(?i)provider.*endpoint.*=|base-url.*fixture') {
        throw 'BODY_STALL must not add an arbitrary production Provider endpoint override.'
    }
    if ($runnerSource -match
            '\$caseAgentResponse\s*\|\s*ConvertTo-Json[\s\S]+assert-live-public-turn-response') {
        throw 'Case smoke response must not be reused as Live Provider evidence.'
    }

    $realJava = (Get-Command java.exe -ErrorAction Stop).Source
    $realKeytool = Join-Path (Split-Path -Parent $realJava) 'keytool.exe'
    if (-not (Test-Path -LiteralPath $realKeytool -PathType Leaf)) {
        throw 'BODY_STALL early-failure tests require keytool beside java.exe.'
    }
    $env:PORTFOLIO_MODEL_PROVIDER = 'DEEPSEEK_V4_FLASH'
    $env:PORTFOLIO_AGENT_DEEPSEEK_API_KEY = 'original-deepseek-sentinel'
    $env:PORTFOLIO_AGENT_GLM_API_KEY = 'original-glm-sentinel'

    $previousEarlyFailurePreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $keytoolFailureOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $runner -JarPath $sourceJar -Lane BODY_STALL -ContextMode IN_MEMORY `
            -JavaExecutable $realJava `
            -KeytoolExecutable (Join-Path $fixtureRoot 'missing-keytool.exe') `
            -SkipPlaywright -Port ($port + 20) 2>&1 | Out-String)
        $keytoolFailureExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousEarlyFailurePreference
    }
    if ($keytoolFailureExitCode -eq 0 -or
            $keytoolFailureOutput -notmatch 'BODY_STALL requires keytool' -or
            $keytoolFailureOutput -notmatch 'BODY_STALL early-failure environment restored') {
        throw "Missing-keytool path did not restore BODY_STALL environment: $keytoolFailureOutput"
    }

    $previousStartFailurePreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $startFailureOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $runner -JarPath $sourceJar -Lane BODY_STALL -ContextMode IN_MEMORY `
            -JavaExecutable (Join-Path $fixtureRoot 'missing-java.exe') `
            -KeytoolExecutable $realKeytool `
            -SkipPlaywright -Port ($port + 21) 2>&1 | Out-String)
        $startFailureExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousStartFailurePreference
    }
    if ($startFailureExitCode -eq 0 -or
            $startFailureOutput -notmatch 'BODY_STALL early-failure environment restored') {
        throw "Application Start-Process failure did not restore BODY_STALL environment: $startFailureOutput"
    }
    if (@(Get-NetTCPConnection -LocalPort 443 -State Listen -ErrorAction SilentlyContinue).Count -ne 0 -or
            @(Get-ChildItem -LiteralPath ([IO.Path]::GetTempPath()) `
                -Directory -Filter 'portfolio-body-stall-*' -ErrorAction SilentlyContinue).Count -ne 0) {
        throw 'BODY_STALL early-failure tests left a fixture listener or temporary directory.'
    }

    New-Item -ItemType Directory -Path (Split-Path -Parent $spacedJar) -Force | Out-Null
    New-Item -ItemType Directory -Path (Split-Path -Parent $fakeNpm) -Force | Out-Null
    New-Item -ItemType Directory -Path (Split-Path -Parent $fakeJava) -Force | Out-Null
    Copy-Item -LiteralPath $sourceJar -Destination $spacedJar
    [System.IO.File]::WriteAllText($fakeNpm, "@exit /b 23`r`n", [System.Text.Encoding]::ASCII)
    [System.IO.File]::WriteAllText(
        $successfulFakeNpm,
        "@exit /b 0`r`n",
        [System.Text.Encoding]::ASCII
    )
    $escapedJavaArgumentCapture = $javaArgumentCapture.Replace('%', '%%')
    [System.IO.File]::WriteAllText(
        $fakeJava,
        "@echo %* > `"$escapedJavaArgumentCapture`"`r`n@exit /b 31`r`n",
        [System.Text.Encoding]::ASCII
    )

    $previousArgumentCaptureErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $normalCaptureOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $runner -JarPath $spacedJar -JavaExecutable $fakeJava `
            -NpmExecutable $fakeNpm -Port ($port + 10) 2>&1 | Out-String)
        $normalCaptureExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousArgumentCaptureErrorActionPreference
    }
    if ($normalCaptureExitCode -eq 0) {
        throw "Expected capture-only Java wrapper to stop before readiness. Output: $normalCaptureOutput"
    }
    $normalJavaArguments = Get-Content -LiteralPath $javaArgumentCapture -Raw
    foreach ($disabledArgument in @(
        '--portfolio.conversational-model.enabled=false',
        '--portfolio.conversational-agent.enabled=false'
    )) {
        $matchCount = ([regex]::Matches(
            $normalJavaArguments,
            '(?<!\S)' + [regex]::Escape($disabledArgument) + '(?!\S)'
        )).Count
        if ($matchCount -ne 1) {
            throw "Expected normal mode to pass '$disabledArgument' exactly once."
        }
    }
    foreach ($requiredEvidence in @(
        'JAR SHA-256: ',
        'Workspace commit (not JAR identity): ',
        'Provider-disabled recommendation did not fail closed:',
        'Agent backend closure smoke passed.'
    )) {
        if ($runnerSource -notmatch [regex]::Escape($requiredEvidence)) {
            throw "Packaged runner is missing evidence '$requiredEvidence'."
        }
    }
    if ($runnerSource -match 'Build identity: commit=') {
        throw 'Packaged runner must not present workspace HEAD as JAR identity.'
    }
    foreach ($admissionArgument in @(
        '--portfolio.agent-runtime.requests-per-minute=1000',
        '--portfolio.agent-runtime.max-concurrent-per-source=1000',
        '--portfolio.agent-runtime.max-active-turns=1000'
    )) {
        $matchCount = ([regex]::Matches(
            $normalJavaArguments,
            '(?<!\S)' + [regex]::Escape($admissionArgument) + '(?!\S)'
        )).Count
        if ($matchCount -ne 1) {
            throw "Packaged JAR smoke must pass current admission override '$admissionArgument' exactly once."
        }
    }
    if ($normalJavaArguments -match
            '(?<!\S)--portfolio\.answer-production\.requests-per-minute(?:=|\s)') {
        throw 'Packaged JAR smoke must not pass the retired answer-production rate key.'
    }
    Remove-Item -LiteralPath $javaArgumentCapture -Force
    $previousLiveCaptureErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $liveCaptureOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $runner -JarPath $spacedJar -JavaExecutable $fakeJava `
            -NpmExecutable $fakeNpm -Port ($port + 11) -RequireLiveProvider `
            2>&1 | Out-String)
        $liveCaptureExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousLiveCaptureErrorActionPreference
    }
    if ($liveCaptureExitCode -eq 0) {
        throw "Expected capture-only Java wrapper to stop before readiness. Output: $liveCaptureOutput"
    }
    $liveJavaArguments = Get-Content -LiteralPath $javaArgumentCapture -Raw
    foreach ($disabledArgument in @(
        '--portfolio.conversational-model.enabled=false',
        '--portfolio.conversational-agent.enabled=false'
    )) {
    if ($liveJavaArguments -match [regex]::Escape($disabledArgument)) {
            throw "Live Provider mode unexpectedly passed '$disabledArgument'."
        }
    }

    $runnerSource = Get-Content -LiteralPath $runner -Raw
    foreach ($liveQualityContract in @(
        'assert-live-general-answer-quality.ps1',
        'GENERAL_QUALITY_RESULT status=PASS',
        '-Baseline',
        'Write-LiveProviderDiagnosticSummary'
    )) {
        if ($runnerSource -notmatch [regex]::Escape($liveQualityContract)) {
            throw "LIVE lane is missing general quality contract '$liveQualityContract'."
        }
    }
    if ($runnerSource -match "@\('SOCIAL', 'GENERAL'\)") {
        throw 'LIVE lane must not duplicate the General Quality authority with a one-shot probe.'
    }

    $env:PLAYWRIGHT_EXTERNAL_SERVER = 'original-external'
    $env:PLAYWRIGHT_REAL_API = 'original-real'
    $env:PLAYWRIGHT_BASE_URL = 'original-base'
    $env:PLAYWRIGHT_REAL_RETRIEVAL = 'original-retrieval'
    $env:PORTFOLIO_MODEL_ENABLED = 'true'
    $env:PORTFOLIO_MODEL_DATA_POLICY_APPROVED = 'true'
    $env:PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED = 'true'
    $env:PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED = 'true'
    $env:PORTFOLIO_AGENT_DEEPSEEK_API_KEY = 'provider-key-must-not-leak'
    $env:PORTFOLIO_MODEL_TIMEOUT = '1ms'
    $env:PORTFOLIO_CONVERSATION_CONTEXT_MODE = 'IN_MEMORY'

    $output = (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $runner `
        -JarPath $spacedJar -NpmExecutable $fakeNpm -Port $port 2>&1 | Out-String)
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 23) {
        throw "Expected runner to preserve Playwright exit code 23, got $exitCode. Output: $output"
    }
    if ($output -notmatch 'owns port 43173') {
        throw "Expected spaced JAR path to start and own the test port. Output: $output"
    }

    if ($output -notmatch 'Packaged portfolio snapshot Case smoke passed\.') {
        throw "Expected packaged portfolio snapshot Case smoke evidence. Output: $output"
    }
    if ($output -notmatch 'Packaged final Agent resource smoke passed\.') {
        throw "Expected final packaged Agent resource smoke evidence. Output: $output"
    }
    foreach ($requiredSmokeEvidence in @(
        'Packaged request correlation smoke passed.',
        'Packaged client diagnostics acceptance smoke passed.',
        'Packaged client diagnostics unknown-field rejection smoke passed.',
        'Packaged client diagnostics body-limit smoke passed.',
        'Packaged structured stdout privacy smoke passed.'
    )) {
        if ($output -notmatch [regex]::Escape($requiredSmokeEvidence)) {
            throw "Expected release smoke evidence '$requiredSmokeEvidence'. Output: $output"
        }
    }
    if ($output -match 'provider-key-must-not-leak') {
        throw 'Runner output leaked the Provider key sentinel.'
    }
    if ($output -notmatch 'Playwright environment restored\.') {
        throw "Expected runner to self-verify environment restoration. Output: $output"
    }
    if ($output -notmatch 'Packaged application process (?<pid>\d+) is stopped\.') {
        throw "Expected runner cleanup evidence. Output: $output"
    }

    $processId = [int]$Matches.pid
    if (Get-Process -Id $processId -ErrorAction SilentlyContinue) {
        throw "Runner left Java process $processId alive."
    }
    $listeners = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
    if ($listeners.Count -ne 0) {
        throw "Runner left port $port occupied."
    }

    if ($output -notmatch 'Provider calls disabled for deterministic smoke\.') {
        throw "Expected normal mode to override inherited Provider enablement. Output: $output"
    }

    $stdoutValidationSource = Get-Content -LiteralPath $runner -Raw
    $stdoutReadCommand = '$capturedStdout = Get-Content -LiteralPath $stdoutPath -Raw'
    if (([regex]::Matches(
        $stdoutValidationSource,
        [regex]::Escape($stdoutReadCommand)
    )).Count -ne 1) {
        throw 'Stdout fixture requires exactly one captured-stdout read command.'
    }
    $stdoutFixtureCommand = @'
$capturedStdout = '{"message":"structured fixture"}' `
    + [System.Environment]::NewLine + 'plaintext fixture must fail'
'@
    $stdoutValidationSource = $stdoutValidationSource.Replace(
        $stdoutReadCommand,
        $stdoutFixtureCommand.Trim()
    )
    [System.IO.File]::WriteAllText(
        $stdoutValidationRunner,
        $stdoutValidationSource,
        [System.Text.UTF8Encoding]::new($false)
    )
    $previousStdoutFixtureErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $stdoutFixtureOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $stdoutValidationRunner -JarPath $spacedJar `
            -NpmExecutable $fakeNpm -Port ($port + 3) 2>&1 | Out-String)
        $stdoutFixtureExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousStdoutFixtureErrorActionPreference
    }
    if ($stdoutFixtureExitCode -eq 0) {
        throw "Expected plaintext stdout fixture to fail. Output: $stdoutFixtureOutput"
    }
    if ($stdoutFixtureOutput -notmatch 'non-JSON application stdout line') {
        throw "Plaintext stdout fixture was not rejected by the JSON boundary. Output: $stdoutFixtureOutput"
    }

    $lateOutputSource = Get-Content -LiteralPath $runner -Raw
    $processStoppedCommand =
            'Write-Output "Packaged application process $($process.Id) is stopped."'
    if (([regex]::Matches(
        $lateOutputSource,
        [regex]::Escape($processStoppedCommand)
    )).Count -ne 1) {
        throw 'Late-output fixtures require exactly one process-stopped command.'
    }
    $latePlaintextSource = $lateOutputSource.Replace(
        $processStoppedCommand,
        $processStoppedCommand + [System.Environment]::NewLine +
                "[System.IO.File]::AppendAllText(`$stdoutPath, " +
                "'plaintext emitted after Playwright' + " +
                "[System.Environment]::NewLine)"
    )
    [System.IO.File]::WriteAllText(
        $latePlaintextRunner,
        $latePlaintextSource,
        [System.Text.UTF8Encoding]::new($false)
    )
    $previousLatePlaintextErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $latePlaintextOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $latePlaintextRunner -JarPath $spacedJar `
            -NpmExecutable $successfulFakeNpm -Port ($port + 4) 2>&1 | Out-String)
        $latePlaintextExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousLatePlaintextErrorActionPreference
    }
    if ($latePlaintextExitCode -eq 0) {
        throw "Expected late plaintext stdout to fail. Output: $latePlaintextOutput"
    }
    if ($latePlaintextOutput -notmatch 'non-JSON application stdout line') {
        throw "Late plaintext stdout was not rejected by the final JSON boundary. Output: $latePlaintextOutput"
    }

    $lateLeakSource = $lateOutputSource.Replace(
        $processStoppedCommand,
        $processStoppedCommand + [System.Environment]::NewLine +
                "[System.IO.File]::AppendAllText(`$stderrPath, " +
                "`$privacySentinel + [System.Environment]::NewLine)"
    )
    [System.IO.File]::WriteAllText(
        $lateLeakRunner,
        $lateLeakSource,
        [System.Text.UTF8Encoding]::new($false)
    )
    $previousLateLeakErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $lateLeakOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $lateLeakRunner -JarPath $spacedJar `
            -NpmExecutable $successfulFakeNpm -Port ($port + 5) 2>&1 | Out-String)
        $lateLeakExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousLateLeakErrorActionPreference
    }
    if ($lateLeakExitCode -eq 0) {
        throw "Expected late privacy sentinel to fail. Output: $lateLeakOutput"
    }
    if ($lateLeakOutput -notmatch 'leaked the visitor-content sentinel') {
        throw "Late privacy sentinel was not rejected by the final log boundary. Output: $lateLeakOutput"
    }

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $missingJarOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $runner `
            -JarPath (Join-Path $fixtureRoot 'missing.jar') -Port ($port + 1) 2>&1 | Out-String)
        $missingJarExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($missingJarExitCode -eq 0) {
        throw "Expected a missing-JAR startup failure to remain nonzero. Output: $missingJarOutput"
    }

    Write-Output 'run-jar-e2e tests passed'
}
finally {
    Restore-EnvironmentVariable 'PORTFOLIO_CONVERSATION_CONTEXT_MODE' `
        $environment.PORTFOLIO_CONVERSATION_CONTEXT_MODE
    Restore-EnvironmentVariable 'PORTFOLIO_CONTEXT_DATABASE_URL' `
        $environment.PORTFOLIO_CONTEXT_DATABASE_URL
    Restore-EnvironmentVariable 'PORTFOLIO_CONTEXT_DATABASE_USERNAME' `
        $environment.PORTFOLIO_CONTEXT_DATABASE_USERNAME
    Restore-EnvironmentVariable 'PORTFOLIO_CONTEXT_DATABASE_PASSWORD' `
        $environment.PORTFOLIO_CONTEXT_DATABASE_PASSWORD
    Restore-EnvironmentVariable 'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID' `
        $environment.PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY_ID
    Restore-EnvironmentVariable 'PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY' `
        $environment.PORTFOLIO_CONTEXT_CURRENT_TOKEN_KEY
    Restore-EnvironmentVariable 'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID' `
        $environment.PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY_ID
    Restore-EnvironmentVariable 'PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY' `
        $environment.PORTFOLIO_CONTEXT_CURRENT_PAYLOAD_KEY
    Restore-EnvironmentVariable 'PLAYWRIGHT_EXTERNAL_SERVER' $environment.PLAYWRIGHT_EXTERNAL_SERVER
    Restore-EnvironmentVariable 'PLAYWRIGHT_REAL_API' $environment.PLAYWRIGHT_REAL_API
    Restore-EnvironmentVariable 'PLAYWRIGHT_BASE_URL' $environment.PLAYWRIGHT_BASE_URL
    Restore-EnvironmentVariable 'PLAYWRIGHT_REAL_RETRIEVAL' `
        $environment.PLAYWRIGHT_REAL_RETRIEVAL
    Restore-EnvironmentVariable 'PORTFOLIO_MODEL_ENABLED' `
        $environment.PORTFOLIO_MODEL_ENABLED
    Restore-EnvironmentVariable 'PORTFOLIO_MODEL_DATA_POLICY_APPROVED' `
        $environment.PORTFOLIO_MODEL_DATA_POLICY_APPROVED
    Restore-EnvironmentVariable 'PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED' `
        $environment.PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED
    Restore-EnvironmentVariable 'PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED' `
        $environment.PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED
    Restore-EnvironmentVariable 'PORTFOLIO_AGENT_DEEPSEEK_API_KEY' `
        $environment.PORTFOLIO_AGENT_DEEPSEEK_API_KEY
    Restore-EnvironmentVariable 'PORTFOLIO_MODEL_TIMEOUT' `
        $environment.PORTFOLIO_MODEL_TIMEOUT

    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolvedFixtureRoot = (Resolve-Path -LiteralPath $fixtureRoot).Path
        if (-not ([System.IO.Path]::GetFileName($resolvedFixtureRoot)).StartsWith(
            'portfolio runner with spaces '
        )) {
            throw "Refusing to remove unverified fixture path: $resolvedFixtureRoot"
        }
        Remove-Item -LiteralPath $resolvedFixtureRoot -Recurse -Force
    }
}
