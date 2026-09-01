$ErrorActionPreference = 'Stop'

$runner = Join-Path $PSScriptRoot 'invoke-qwen-general-lab.ps1'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('qwen-general-lab-test-' + [guid]::NewGuid().ToString('N'))
$rawRoot = Join-Path $fixtureRoot 'dedicated-raw'
$secretFile = Join-Path $fixtureRoot 'fake-provider.secret'
$markerName = '.qwen-general-provider-lab-root.v1.json'
$corpusPath = Join-Path $PSScriptRoot `
    'qwen-general-explanation-corpus.v1.json'

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Invoke-Runner([string[]]$Arguments) {
    $token = [guid]::NewGuid().ToString('N')
    $stdout = Join-Path $fixtureRoot ('runner-' + $token + '.stdout')
    $stderr = Join-Path $fixtureRoot ('runner-' + $token + '.stderr')
    $processArguments = @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $runner) +
        $Arguments
    $process = Start-Process -FilePath 'powershell.exe' `
        -ArgumentList $processArguments -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    try {
        if (-not $process.WaitForExit(15000)) {
            $process.Kill()
            $trace = ''
            $rootIndex = [Array]::IndexOf($Arguments, '-RawArtifactRoot')
            if ($rootIndex -ge 0 -and $rootIndex + 1 -lt $Arguments.Count) {
                $tracePath = Join-Path $Arguments[$rootIndex + 1] `
                    '.loopback-test-trace'
                if (Test-Path -LiteralPath $tracePath -PathType Leaf) {
                    $trace = [System.IO.File]::ReadAllText($tracePath)
                }
            }
            return @{
                ExitCode = 124
                Output = 'LAB_TEST_RUNNER_TIMEOUT stage=' + $trace
            }
        }
        $process.WaitForExit()
        $process.Refresh()
        $output = ''
        if (Test-Path -LiteralPath $stdout -PathType Leaf) {
            $output += [System.IO.File]::ReadAllText($stdout)
        }
        if (Test-Path -LiteralPath $stderr -PathType Leaf) {
            $output += [System.IO.File]::ReadAllText($stderr)
        }
        if ($output -match '^LAB_INTERNAL_ERROR') {
            $rootIndex = [Array]::IndexOf($Arguments, '-RawArtifactRoot')
            if ($rootIndex -ge 0 -and $rootIndex + 1 -lt $Arguments.Count) {
                $tracePath = Join-Path $Arguments[$rootIndex + 1] `
                    '.loopback-test-trace'
                if (Test-Path -LiteralPath $tracePath -PathType Leaf) {
                    $output = $output.Trim() + ' stage=' +
                        [System.IO.File]::ReadAllText($tracePath)
                }
            }
        }
        $observedExitCode = $process.ExitCode
        if ($null -eq $observedExitCode) {
            $observedExitCode = if ($output -match `
                    '^(?:LAB_RAW_ROOT_INITIALIZED|LAB_CAPTURE status=CAPTURED)') {
                0
            }
            else { 1 }
        }
        return @{ ExitCode = $observedExitCode; Output = $output }
    }
    finally {
        $process.Dispose()
        foreach ($path in @($stdout, $stderr)) {
            if (Test-Path -LiteralPath $path -PathType Leaf) {
                Remove-Item -LiteralPath $path -Force
            }
        }
    }
}

function Write-Json([string]$Path, [object]$Value) {
    [System.IO.File]::WriteAllText(
        $Path,
        ($Value | ConvertTo-Json -Depth 20 -Compress),
        [System.Text.UTF8Encoding]::new($false))
}

function Initialize-Root([string]$Path) {
    New-Item -ItemType Directory -Path $Path -Force | Out-Null
    Write-Json (Join-Path $Path $markerName) ([ordered]@{
        schemaVersion = 'qwen-general-provider-lab-root.v1'
        rootId = [guid]::NewGuid().ToString('N')
        createdAtUtc = [datetimeoffset]::UtcNow.ToString('o')
    })
    Assert-True (Test-Path -LiteralPath (Join-Path $Path $markerName) `
            -PathType Leaf) 'The dedicated raw root marker is required.'
}

function Get-AclFingerprint([string]$Path) {
    return (Get-Acl -LiteralPath $Path).Sddl
}

function Write-CaptureFixture(
    [string]$Root,
    [string]$Name,
    [datetime]$ExpiresAt,
    [timespan]$Ttl = ([timespan]::FromHours(1)),
    [string]$Payload = '{"fixture":true}'
) {
    $directory = Join-Path $Root $Name
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $metadata = [ordered]@{
        schemaVersion = 'qwen-general-lab-artifact.v2'
        artifactId = $Name
        caseId = 'java-spring-001'
        depth = 'CONCISE'
        createdAtUtc = $ExpiresAt.ToUniversalTime().Subtract($Ttl).ToString('o')
        expiresAtUtc = $ExpiresAt.ToUniversalTime().ToString('o')
        operatorIdentitySha256 = ('a' * 64)
        provider = 'QWEN'
        model = 'qwen3.7-flash'
        selectionVersion = 'qwen-3-7-flash-v8'
        providerContract = 'general.provider-draft.v4'
        compilerProfile = 'general-provider-draft-compiler.v4'
        captureSource = 'TEST_LOOPBACK'
        status = 'CAPTURED'
        httpClass = 'SUCCESS'
        latencyBucket = 'LT_100_MS'
        latencyMs = 50
        attemptCount = 1
    }
    [System.IO.File]::WriteAllText(
        (Join-Path $directory 'metadata.json'),
        ($metadata | ConvertTo-Json -Compress),
        [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText(
        (Join-Path $directory 'response.raw.json'), $Payload,
        [System.Text.UTF8Encoding]::new($false))
    return $directory
}

function Get-FreeLoopbackPort {
    $listener = [System.Net.Sockets.TcpListener]::new(
        [System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port }
    finally { $listener.Stop() }
}

function Start-FakeTransport([int]$Port, [string]$Mode, [string]$Marker) {
    $server = Join-Path $PSScriptRoot 'fake-qwen-loopback-server.test.ps1'
    $ready = Join-Path $fixtureRoot ('loopback-ready-' +
        [guid]::NewGuid().ToString('N'))
    $arguments = @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $server,
        '-Port', [string]$Port, '-Mode', $Mode, '-Marker', $Marker,
        '-ReadyFile', $ready)
    $process = Start-Process -FilePath 'powershell.exe' `
        -ArgumentList $arguments -WindowStyle Hidden -PassThru
    $deadline = (Get-Date).AddSeconds(5)
    while (-not (Test-Path -LiteralPath $ready -PathType Leaf) -and
            -not $process.HasExited -and (Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 25
        $process.Refresh()
    }
    Assert-True (Test-Path -LiteralPath $ready -PathType Leaf) `
        'The loopback fake transport did not become ready.'
    return @{ Process = $process; ReadyFile = $ready }
}

function Invoke-FakeCapture([string]$Root, [string]$Mode, [string]$Marker) {
    $port = Get-FreeLoopbackPort
    $fake = Start-FakeTransport $port $Mode $Marker
    $previousAttestation = $env:QWEN_GENERAL_LAB_TEST_LOOPBACK
    $env:QWEN_GENERAL_LAB_TEST_LOOPBACK = 'AUTHORIZED_TEST_PROCESS_ONLY'
    try {
        $result = Invoke-Runner @(
            '-CaseId', 'java-spring-001', '-Depth', 'CONCISE',
            '-RawArtifactRoot', $Root, '-SecretFile', $secretFile,
            '-AuthorizeRealProvider', '-TestOnlyLoopbackPort', [string]$port)
        $fake.Process.WaitForExit(5000) | Out-Null
        return $result
    }
    finally {
        $env:QWEN_GENERAL_LAB_TEST_LOOPBACK = $previousAttestation
        if (-not $fake.Process.HasExited) { $fake.Process.Kill() }
        $fake.Process.Dispose()
        if (Test-Path -LiteralPath $fake.ReadyFile -PathType Leaf) {
            Remove-Item -LiteralPath $fake.ReadyFile -Force
        }
    }
}

try {
    Assert-True (Test-Path -LiteralPath $runner -PathType Leaf) `
        'The lab runner must exist.'
    New-Item -ItemType Directory -Path $fixtureRoot -Force | Out-Null
    Set-Content -LiteralPath $secretFile -Value 'fake-secret-value' `
        -Encoding UTF8 -NoNewline

    $volumeRoot = Join-Path `
        ([System.IO.Path]::GetPathRoot($fixtureRoot)) '.'
    $volume = Invoke-Runner @(
        '-CaseId', 'java-spring-001', '-Depth', 'CONCISE',
        '-RawArtifactRoot', $volumeRoot)
    Assert-True ($volume.ExitCode -eq 1 -and
            $volume.Output -match '^LAB_RAW_ROOT_REJECTED\s*$') `
        ('A volume root must be rejected before ACL or enumeration. actual=' +
            $volume.ExitCode + ':' + $volume.Output)

    $repoAncestor = Split-Path -Parent $repoRoot
    $ancestor = Invoke-Runner @(
        '-CaseId', 'java-spring-001', '-Depth', 'CONCISE',
        '-RawArtifactRoot', $repoAncestor)
    Assert-True ($ancestor.ExitCode -eq 1 -and
            $ancestor.Output -match '^LAB_RAW_ROOT_REJECTED\s*$') `
        'A repository ancestor must be rejected.'

    $userRoot = [Environment]::GetFolderPath('UserProfile')
    $userAclBefore = Get-AclFingerprint $userRoot
    $broadUserRoot = Invoke-Runner @(
        '-CaseId', 'java-spring-001', '-Depth', 'CONCISE',
        '-RawArtifactRoot', $userRoot)
    Assert-True ($broadUserRoot.ExitCode -eq 1 -and
            $broadUserRoot.Output -match '^LAB_RAW_ROOT_REJECTED\s*$' -and
            (Get-AclFingerprint $userRoot) -ceq $userAclBefore) `
        'The user profile root must be rejected.'

    $unmarked = Join-Path $fixtureRoot 'unmarked-nonempty'
    New-Item -ItemType Directory -Path $unmarked -Force | Out-Null
    $sentinel = Join-Path $unmarked 'must-survive.txt'
    Set-Content -LiteralPath $sentinel -Value 'DO_NOT_DELETE' -Encoding UTF8
    $unmarkedAclBefore = Get-AclFingerprint $unmarked
    $unmarkedResult = Invoke-Runner @(
        '-CaseId', 'java-spring-001', '-Depth', 'CONCISE',
        '-RawArtifactRoot', $unmarked)
    Assert-True ($unmarkedResult.ExitCode -eq 1 -and
            $unmarkedResult.Output -match '^LAB_RAW_ROOT_REJECTED\s*$') `
        'An unmarked directory must never be treated as a lab root.'
    Assert-True (Test-Path -LiteralPath $sentinel -PathType Leaf) `
        'Rejected roots must not be recursively cleaned.'
    Assert-True ((Get-AclFingerprint $unmarked) -ceq $unmarkedAclBefore) `
        'A missing marker must be rejected before ACL mutation.'

    $rootJunctionTarget = Join-Path $fixtureRoot 'root-junction-target'
    Initialize-Root $rootJunctionTarget
    $rootJunction = Join-Path $fixtureRoot 'root-junction'
    New-Item -ItemType Junction -Path $rootJunction `
        -Target $rootJunctionTarget | Out-Null
    $rootJunctionAclBefore = Get-AclFingerprint $rootJunctionTarget
    $junctionRootResult = Invoke-Runner @(
        '-CaseId', 'java-spring-001', '-Depth', 'CONCISE',
        '-RawArtifactRoot', $rootJunction)
    Assert-True ($junctionRootResult.ExitCode -eq 1 -and
            $junctionRootResult.Output -match '^LAB_RAW_ROOT_REJECTED\s*$' -and
            (Get-AclFingerprint $rootJunctionTarget) -ceq
                $rootJunctionAclBefore) `
        'A raw-root junction must be rejected before target ACL mutation.'
    [System.IO.Directory]::Delete($rootJunction)

    $markerReparseRoot = Join-Path $fixtureRoot 'marker-reparse-root'
    New-Item -ItemType Directory -Path $markerReparseRoot | Out-Null
    $outsideMarker = Join-Path $fixtureRoot 'outside-root-marker-target'
    New-Item -ItemType Directory -Path $outsideMarker | Out-Null
    Write-Json (Join-Path $outsideMarker 'marker.json') ([ordered]@{
        schemaVersion = 'qwen-general-provider-lab-root.v1'
        rootId = [guid]::NewGuid().ToString('N')
        createdAtUtc = [datetimeoffset]::UtcNow.ToString('o')
    })
    $markerLink = Join-Path $markerReparseRoot $markerName
    New-Item -ItemType Junction -Path $markerLink `
        -Target $outsideMarker | Out-Null
    $markerRootAclBefore = Get-AclFingerprint $markerReparseRoot
    $markerTargetAclBefore = Get-AclFingerprint $outsideMarker
    $markerReparseResult = Invoke-Runner @(
        '-CaseId', 'java-spring-001', '-Depth', 'CONCISE',
        '-RawArtifactRoot', $markerReparseRoot)
    Assert-True ($markerReparseResult.ExitCode -eq 1 -and
            $markerReparseResult.Output -match '^LAB_RAW_ROOT_REJECTED\s*$' -and
            (Get-AclFingerprint $markerReparseRoot) -ceq
                $markerRootAclBefore -and
            (Get-AclFingerprint $outsideMarker) -ceq
                $markerTargetAclBefore) `
        'A reparse marker must be rejected before root or target ACL mutation.'
    [System.IO.Directory]::Delete($markerLink)

    $unsafeTraceRoot = Join-Path $fixtureRoot 'unvalidated-trace-target'
    New-Item -ItemType Directory -Path $unsafeTraceRoot | Out-Null
    $unsafeTraceAclBefore = Get-AclFingerprint $unsafeTraceRoot
    $previousTraceAttestation = $env:QWEN_GENERAL_LAB_TEST_LOOPBACK
    $env:QWEN_GENERAL_LAB_TEST_LOOPBACK = 'AUTHORIZED_TEST_PROCESS_ONLY'
    $corpusLock = [System.IO.File]::Open(
        $corpusPath,
        [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read,
        [System.IO.FileShare]::None)
    try {
        $corpusFailure = Invoke-Runner @(
            '-CaseId', 'java-spring-001', '-Depth', 'CONCISE',
            '-RawArtifactRoot', $unsafeTraceRoot)
    }
    finally {
        $corpusLock.Dispose()
        $env:QWEN_GENERAL_LAB_TEST_LOOPBACK = $previousTraceAttestation
    }
    Assert-True ($corpusFailure.ExitCode -eq 1 -and
            $corpusFailure.Output -match '^LAB_INTERNAL_ERROR\s*$' -and
            -not (Test-Path -LiteralPath (Join-Path $unsafeTraceRoot `
                '.loopback-test-trace')) -and
            (Get-AclFingerprint $unsafeTraceRoot) -ceq
                $unsafeTraceAclBefore) `
        'A pre-validation corpus failure must not write through caller raw-root input.'

    Initialize-Root $rawRoot
    Write-Output 'LAB_TEST_STEP root-initialized'

    $unauthorized = Invoke-Runner @(
        '-CaseId', 'java-spring-001', '-Depth', 'CONCISE',
        '-RawArtifactRoot', $rawRoot)
    Assert-True ($unauthorized.ExitCode -eq 1 -and
            $unauthorized.Output -match `
                '^LAB_AUTHORIZATION_REQUIRED caseId=java-spring-001 depth=CONCISE\s*$') `
        'An unauthorized call must fail with a closed result.'
    Assert-True (Get-Acl -LiteralPath $rawRoot).AreAccessRulesProtected `
        'ACL inheritance must be disabled after marker validation and before enumeration.'

    $unrelated = Join-Path $rawRoot 'old-unrelated-directory'
    New-Item -ItemType Directory -Path $unrelated -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $unrelated 'must-survive.txt') `
        -Value 'DO_NOT_DELETE' -Encoding UTF8
    $expiredName = 'capture-java-spring-001-concise-20260827T000000Z-' +
        [guid]::NewGuid().ToString('N')
    $freshName = 'capture-java-spring-001-concise-20260828T000000Z-' +
        [guid]::NewGuid().ToString('N')
    $expired = Write-CaptureFixture $rawRoot $expiredName `
        ((Get-Date).ToUniversalTime().AddMinutes(-1))
    $fresh = Write-CaptureFixture $rawRoot $freshName `
        ((Get-Date).ToUniversalTime().AddHours(23)) `
        ([timespan]::FromHours(24))
    $cleanup = Invoke-Runner @(
        '-CaseId', 'java-spring-001', '-Depth', 'DETAILED',
        '-RawArtifactRoot', $rawRoot)
    Assert-True ($cleanup.ExitCode -eq 1 -and
            -not (Test-Path -LiteralPath $expired) -and
            (Test-Path -LiteralPath $fresh -PathType Container) -and
            (Test-Path -LiteralPath (Join-Path $unrelated 'must-survive.txt'))) `
        ('Cleanup may delete only expired, valid capture directories and ' +
            'must accept an exact 24-hour TTL.')

    $overlongName = 'capture-java-spring-001-concise-20260828T000000Z-' +
        [guid]::NewGuid().ToString('N')
    $overlong = Write-CaptureFixture $rawRoot $overlongName `
        ((Get-Date).ToUniversalTime().AddHours(23)) `
        (([timespan]::FromHours(24)).Add(
            [timespan]::FromMilliseconds(1)))
    $overlongRejected = Invoke-Runner @(
        '-CaseId', 'java-spring-001', '-Depth', 'CONCISE',
        '-RawArtifactRoot', $rawRoot)
    Assert-True ($overlongRejected.ExitCode -eq 1 -and
            $overlongRejected.Output -match
                '^LAB_CAPTURE_METADATA_REJECTED\s*$' -and
            (Test-Path -LiteralPath $overlong -PathType Container)) `
        'Lab cleanup must reject a TTL of 24 hours plus one millisecond.'
    Remove-Item -LiteralPath $overlong -Recurse -Force

    $outsideTarget = Join-Path $fixtureRoot 'junction-target'
    New-Item -ItemType Directory -Path $outsideTarget -Force | Out-Null
    $outsideSentinel = Join-Path $outsideTarget 'must-survive.txt'
    Set-Content -LiteralPath $outsideSentinel -Value 'DO_NOT_DELETE' `
        -Encoding UTF8
    $junctionName = 'capture-java-spring-001-concise-20260827T000000Z-' +
        [guid]::NewGuid().ToString('N')
    $junctionCapture = Write-CaptureFixture $rawRoot $junctionName `
        ((Get-Date).ToUniversalTime().AddMinutes(-1))
    $junctionPath = Join-Path $junctionCapture 'escape-junction'
    New-Item -ItemType Junction -Path $junctionPath `
        -Target $outsideTarget | Out-Null
    $junctionCleanup = Invoke-Runner @(
        '-CaseId', 'java-spring-001', '-Depth', 'CONCISE',
        '-RawArtifactRoot', $rawRoot)
    Assert-True ($junctionCleanup.ExitCode -eq 1 -and
            $junctionCleanup.Output -match '^LAB_CAPTURE_DELETE_REJECTED\s*$' -and
            (Test-Path -LiteralPath $outsideSentinel -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $junctionCapture `
                'metadata.json') -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $junctionCapture `
                'response.raw.json') -PathType Leaf)) `
        'Cleanup must validate the complete tree before deleting any child.'
    [System.IO.Directory]::Delete($junctionPath)
    Remove-Item -LiteralPath $junctionCapture -Recurse -Force

    $malformedName = 'capture-java-spring-001-concise-20260827T000000Z-' +
        [guid]::NewGuid().ToString('N')
    $malformed = Join-Path $rawRoot $malformedName
    New-Item -ItemType Directory -Path $malformed -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $malformed 'metadata.json') `
        -Value '{"expiresAtUtc":"2000-01-01T00:00:00Z"}' -Encoding UTF8
    $malformedCleanup = Invoke-Runner @(
        '-CaseId', 'java-spring-001', '-Depth', 'CONCISE',
        '-RawArtifactRoot', $rawRoot)
    Assert-True ($malformedCleanup.ExitCode -eq 1 -and
            $malformedCleanup.Output -match '^LAB_CAPTURE_METADATA_REJECTED\s*$' -and
            (Test-Path -LiteralPath $malformed -PathType Container)) `
        'Malformed metadata must fail closed and must not authorize deletion.'
    Remove-Item -LiteralPath $malformed -Recurse -Force
    Write-Output 'LAB_TEST_STEP cleanup-verified'

    $secretInsideRepo = Join-Path $repoRoot 'secret-must-not-exist.fixture'
    $secretBoundary = Invoke-Runner @(
        '-CaseId', 'java-spring-001', '-Depth', 'CONCISE',
        '-RawArtifactRoot', $rawRoot, '-SecretFile', $secretInsideRepo,
        '-AuthorizeRealProvider')
    Assert-True ($secretBoundary.ExitCode -eq 1 -and
            $secretBoundary.Output -match '^LAB_SECRET_FILE_REJECTED\s*$') `
        'The explicit secret file must remain outside the repository.'

    $sensitiveMarker = 'RAW_RESPONSE_MUST_NEVER_REACH_STDOUT'
    Write-Output 'LAB_TEST_STEP fake-success-start'
    $success = Invoke-FakeCapture $rawRoot 'SUCCESS' $sensitiveMarker
    Assert-True ($success.ExitCode -eq 0 -and
            $success.Output -match `
                '^LAB_CAPTURE status=CAPTURED caseId=java-spring-001 depth=CONCISE ' -and
            $success.Output -notmatch [regex]::Escape($sensitiveMarker)) `
        ('The loopback success path must write raw data without echoing it. ' +
            'actual=' + $success.ExitCode + ':' + $success.Output)
    $captured = @(Get-ChildItem -LiteralPath $rawRoot -Directory -Filter `
        'capture-*' | Where-Object {
            Test-Path -LiteralPath (Join-Path $_.FullName 'request.raw.json')
        })
    Assert-True ($captured.Count -eq 1) `
        'Exactly one authorized fake capture must be written.'
    $capturedMetadata = Get-Content -LiteralPath `
        (Join-Path $captured[0].FullName 'metadata.json') -Raw |
        ConvertFrom-Json
    Assert-True ($capturedMetadata.status -ceq 'CAPTURED' -and
            $capturedMetadata.httpClass -ceq 'SUCCESS' -and
            $capturedMetadata.captureSource -ceq 'TEST_LOOPBACK' -and
            [long]$capturedMetadata.latencyMs -ge 0 -and
            [long]$capturedMetadata.attemptCount -eq 1) `
        'Successful fake transport metadata must use closed classifications.'
    Assert-True ((Get-Acl -LiteralPath $captured[0].FullName).
            AreAccessRulesProtected) `
        'Each capture directory must retain the protected ACL boundary.'

    $serverErrorRoot = Join-Path $fixtureRoot 'server-error-root'
    Write-Output 'LAB_TEST_STEP fake-success-verified'
    Initialize-Root $serverErrorRoot
    $serverError = Invoke-FakeCapture `
        $serverErrorRoot 'SERVER_ERROR' $sensitiveMarker
    Assert-True ($serverError.ExitCode -eq 1 -and
            $serverError.Output -match 'status=SERVER_ERROR' -and
            $serverError.Output -notmatch [regex]::Escape($sensitiveMarker)) `
        'HTTP 5xx must be classified without exposing its body.'

    $oversizeRoot = Join-Path $fixtureRoot 'oversize-root'
    Initialize-Root $oversizeRoot
    $oversize = Invoke-FakeCapture $oversizeRoot 'TOO_LARGE' $sensitiveMarker
    Assert-True ($oversize.ExitCode -eq 1 -and
            $oversize.Output -match '^LAB_PROVIDER_RESPONSE_TOO_LARGE\s*$' -and
            $oversize.Output -notmatch '\.ps1|at line|StackTrace') `
        'Oversized responses must use one closed error without path or stack.'

    $unknownCase = Invoke-Runner @(
        '-CaseId', 'missing-999', '-Depth', 'STANDARD',
        '-RawArtifactRoot', $rawRoot)
    Assert-True ($unknownCase.ExitCode -eq 1 -and
            $unknownCase.Output -match '^LAB_CASE_ID_REJECTED\s*$') `
        'A case outside the frozen corpus must be rejected.'

    $freeTextMarker = 'RAW_FREE_TEXT_MUST_NOT_BE_ACCEPTED'
    $freeText = Invoke-Runner @(
        '-CaseId', 'java-spring-001', '-Depth', 'CONCISE',
        '-RawArtifactRoot', $rawRoot, '-Prompt', $freeTextMarker)
    Assert-True ($freeText.ExitCode -ne 0 -and
            $freeText.Output -notmatch [regex]::Escape($freeTextMarker)) `
        'The runner must not accept or echo arbitrary free text.'

    $productionMatches = @(Get-ChildItem -LiteralPath `
        (Join-Path $repoRoot 'backend\src\main') -Recurse -File |
        Select-String -Pattern 'provider-diagnostic-lab|invoke-qwen-general-lab')
    Assert-True ($productionMatches.Count -eq 0) `
        'The lab must have no production Bean or HTTP entry point.'

    $runnerSource = Get-Content -LiteralPath $runner -Raw
    Assert-True ($runnerSource -notmatch 'InitializeRawArtifactRoot') `
        'The collector must not create or mark a raw root before validation.'
    Assert-True ($runnerSource -match '(?m)^\s*\$maxTokens\s*=\s*1200\s*$' -and
            $runnerSource -match '"max_tokens"') `
        'The General output-token ceiling must remain fixed.'
    Assert-True ($runnerSource -match `
            '(?m)^\s*\$parallelToolCalls\s*=\s*''false''\s*$' -and
            $runnerSource -match '"parallel_tool_calls"') `
        'Parallel tool calls must remain disabled.'
    Assert-True ($runnerSource -match 'emit_general_provider_draft_v4') `
        'The lab request must use the frozen production carrier name.'

    Write-Output 'QWEN_GENERAL_LAB_FENCE_TESTS_OK'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        Assert-True ([System.IO.Path]::GetFileName($resolved).StartsWith(
            'qwen-general-lab-test-')) `
            'Refusing to remove an unverified fixture root.'
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
