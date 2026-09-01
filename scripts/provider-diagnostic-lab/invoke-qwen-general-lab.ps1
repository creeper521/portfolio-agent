param(
    [Parameter(Mandatory = $true)]
    [string]$CaseId,
    [Parameter(Mandatory = $true)]
    [ValidateSet('CONCISE', 'STANDARD', 'DETAILED')]
    [string]$Depth,
    [Parameter(Mandatory = $true)]
    [string]$RawArtifactRoot,
    [string]$SecretFile = '',
    [switch]$AuthorizeRealProvider,
    [ValidateRange(0, 65535)]
    [int]$TestOnlyLoopbackPort = 0
)

class LabClosedFailure : System.Exception {
    LabClosedFailure([string]$code) : base($code) { }
}

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.Net.Http
. (Join-Path $PSScriptRoot 'raw-root-common.ps1')

$ttlHours = 24
$maximumResponseBytes = 131072
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$corpusPath = Join-Path $PSScriptRoot `
    'qwen-general-explanation-corpus.v1.json'
$contractPath = Join-Path $repoRoot `
    'backend\src\main\resources\model-contracts\general.provider-draft.v4.schema.json'
$systemPromptPath = Join-Path $repoRoot `
    'backend\src\main\resources\prompts\general-provider-draft-system.txt'
$providerEndpoint = `
    'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions'
$providerModel = 'qwen3.7-flash'
$captureNamePattern = `
    '^capture-[a-z]+(?:-[a-z]+)*-[0-9]{3}-(?:concise|standard|detailed)-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{32}$'
$metadataFields = @(
    'schemaVersion', 'artifactId', 'caseId', 'depth', 'createdAtUtc',
    'expiresAtUtc', 'operatorIdentitySha256', 'provider', 'model',
    'selectionVersion', 'providerContract', 'compilerProfile', 'status',
    'httpClass', 'latencyBucket', 'latencyMs', 'attemptCount',
    'captureSource')
$script:ValidatedRawRoot = ''

function Stop-Lab([string]$Code) {
    throw [LabClosedFailure]::new($Code)
}

function Get-NormalizedPath([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) { return '' }
    return [System.IO.Path]::GetFullPath($Path).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar)
}

function Test-SameOrChild([string]$Candidate, [string]$Parent) {
    $candidatePath = Get-NormalizedPath $Candidate
    $parentPath = Get-NormalizedPath $Parent
    if ($candidatePath.Equals(
            $parentPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $true
    }
    return $candidatePath.StartsWith(
        $parentPath + [System.IO.Path]::DirectorySeparatorChar,
        [System.StringComparison]::OrdinalIgnoreCase)
}

function Test-ExactKeys([object]$Value, [string[]]$Expected) {
    if ($null -eq $Value) { return $false }
    $actual = @($Value.PSObject.Properties.Name | Sort-Object)
    $required = @($Expected | Sort-Object)
    return ($actual -join '|') -ceq ($required -join '|')
}

function Test-PathContainsReparsePoint([string]$Path) {
    $currentPath = Get-NormalizedPath $Path
    while (-not [string]::IsNullOrWhiteSpace($currentPath)) {
        if (Test-Path -LiteralPath $currentPath) {
            $current = Get-Item -LiteralPath $currentPath -Force
            if (($current.Attributes -band `
                    [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                return $true
            }
        }
        $parent = Split-Path -Parent $currentPath
        if ([string]::IsNullOrWhiteSpace($parent) -or
                $parent -ceq $currentPath) { break }
        $currentPath = $parent
    }
    return $false
}

function Write-Utf8NoBom([string]$Path, [string]$Value) {
    [System.IO.File]::WriteAllText(
        $Path, $Value, [System.Text.UTF8Encoding]::new($false))
}

function Write-TestTrace([string]$Root, [string]$Stage) {
    if ($env:QWEN_GENERAL_LAB_TEST_LOOPBACK -ceq
            'AUTHORIZED_TEST_PROCESS_ONLY') {
        [System.IO.File]::AppendAllText(
            (Join-Path $Root '.loopback-test-trace'),
            $Stage + ',', [System.Text.UTF8Encoding]::new($false))
    }
}

function Assert-ContainedLeaf([string]$Path, [string]$Root) {
    $fullPath = Get-NormalizedPath $Path
    if (-not (Test-SameOrChild $fullPath $Root) -or
            -not (Test-Path -LiteralPath $fullPath -PathType Leaf) -or
            (Test-PathContainsReparsePoint $fullPath)) {
        Stop-Lab 'LAB_CAPTURE_METADATA_REJECTED'
    }
    $resolved = Get-NormalizedPath (Resolve-Path -LiteralPath $fullPath).Path
    if (-not (Test-SameOrChild $resolved $Root)) {
        Stop-Lab 'LAB_CAPTURE_METADATA_REJECTED'
    }
    return $resolved
}

function Test-NoDuplicateJsonKeys([string]$Json) {
    $matches = [regex]::Matches($Json, '"([A-Za-z][A-Za-z0-9]*)"\s*:')
    $seen = @{}
    foreach ($match in $matches) {
        $name = $match.Groups[1].Value
        if ($seen.ContainsKey($name)) { return $false }
        $seen[$name] = $true
    }
    return $true
}

function Read-CaptureMetadata([string]$Directory, [string]$Root) {
    $metadataPath = Assert-ContainedLeaf `
        (Join-Path $Directory 'metadata.json') $Root
    try {
        $raw = Get-Content -LiteralPath $metadataPath -Raw -Encoding UTF8
        if (-not (Test-NoDuplicateJsonKeys $raw)) {
            Stop-Lab 'LAB_CAPTURE_METADATA_REJECTED'
        }
        $metadata = $raw | ConvertFrom-Json
    }
    catch [LabClosedFailure] { throw }
    catch { Stop-Lab 'LAB_CAPTURE_METADATA_REJECTED' }
    if (-not (Test-ExactKeys $metadata $metadataFields) -or
            $metadata.schemaVersion -cne 'qwen-general-lab-artifact.v2' -or
            $metadata.artifactId -cne `
                [System.IO.Path]::GetFileName($Directory) -or
            [string]$metadata.caseId -cnotmatch `
                '^[a-z]+(?:-[a-z]+)*-[0-9]{3}$' -or
            [string]$metadata.depth -cnotin @(
                'CONCISE', 'STANDARD', 'DETAILED') -or
            [string]$metadata.operatorIdentitySha256 -cnotmatch `
                '^[0-9a-f]{64}$' -or
            $metadata.provider -cne 'QWEN' -or
            $metadata.model -cne 'qwen3.7-flash' -or
            $metadata.selectionVersion -cne 'qwen-3-7-flash-v8' -or
            $metadata.providerContract -cne 'general.provider-draft.v4' -or
            $metadata.compilerProfile -cne `
                'general-provider-draft-compiler.v4' -or
            [string]$metadata.captureSource -cnotin @(
                'REAL_PROVIDER', 'TEST_LOOPBACK') -or
            [string]$metadata.status -cnotin @(
                'CAPTURED', 'RATE_LIMITED', 'SERVER_ERROR', 'CLIENT_ERROR',
                'TRANSPORT_FAILED', 'RESPONSE_REJECTED') -or
            [string]$metadata.httpClass -cnotin @(
                'SUCCESS', 'RATE_LIMITED', 'SERVER_ERROR', 'CLIENT_ERROR',
                'TRANSPORT_UNAVAILABLE') -or
            [string]$metadata.latencyBucket -cnotin @(
                'LT_100_MS', 'FROM_100_TO_499_MS',
                'FROM_500_TO_1999_MS', 'FROM_2000_TO_9999_MS',
                'GTE_10000_MS') -or
            ($metadata.latencyMs -isnot [int] -and
                $metadata.latencyMs -isnot [long]) -or
            [long]$metadata.latencyMs -lt 0 -or
            [long]$metadata.latencyMs -gt 120000 -or
            ($metadata.attemptCount -isnot [int] -and
                $metadata.attemptCount -isnot [long]) -or
            [long]$metadata.attemptCount -notin @(1, 2)) {
        Stop-Lab 'LAB_CAPTURE_METADATA_REJECTED'
    }
    try {
        $created = [datetimeoffset]::Parse([string]$metadata.createdAtUtc)
        $expires = [datetimeoffset]::Parse([string]$metadata.expiresAtUtc)
    }
    catch { Stop-Lab 'LAB_CAPTURE_METADATA_REJECTED' }
    if ($expires -le $created -or
            ($expires - $created) -gt [timespan]::FromHours(24)) {
        Stop-Lab 'LAB_CAPTURE_METADATA_REJECTED'
    }
    return @{ Value = $metadata; Expires = $expires.ToUniversalTime() }
}

function Assert-ValidatedTree(
    [string]$Directory,
    [string]$Root
) {
    $fullDirectory = Get-NormalizedPath $Directory
    if (-not (Test-SameOrChild $fullDirectory $Root) -or
            $fullDirectory.Equals(
                (Get-NormalizedPath $Root),
                [System.StringComparison]::OrdinalIgnoreCase)) {
        Stop-Lab 'LAB_CAPTURE_DELETE_REJECTED'
    }
    $item = Get-Item -LiteralPath $fullDirectory -Force
    if (($item.Attributes -band `
            [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        Stop-Lab 'LAB_CAPTURE_DELETE_REJECTED'
    }
    foreach ($child in @(Get-ChildItem -LiteralPath $fullDirectory -Force)) {
        $childPath = Get-NormalizedPath $child.FullName
        if (-not (Test-SameOrChild $childPath $fullDirectory) -or
                -not (Test-SameOrChild $childPath $Root) -or
                ($child.Attributes -band `
                    [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            Stop-Lab 'LAB_CAPTURE_DELETE_REJECTED'
        }
        if ($child.PSIsContainer) {
            Assert-ValidatedTree $childPath $Root
        }
    }
}

function Remove-ValidatedTree(
    [string]$Directory,
    [string]$Root
) {
    Assert-ValidatedTree $Directory $Root
    $fullDirectory = Get-NormalizedPath $Directory
    foreach ($child in @(Get-ChildItem -LiteralPath $fullDirectory -Force)) {
        $childPath = Get-NormalizedPath $child.FullName
        if ($child.PSIsContainer) {
            Remove-ValidatedTree $childPath $Root
        }
        else {
            Remove-Item -LiteralPath $childPath -Force
        }
    }
    Remove-Item -LiteralPath $fullDirectory -Force
}

function Remove-ExpiredArtifacts([string]$Root) {
    $now = [datetimeoffset](Get-Date).ToUniversalTime()
    foreach ($directory in @(Get-ChildItem -LiteralPath $Root -Directory -Force)) {
        if ([string]$directory.Name -cnotmatch $captureNamePattern) { continue }
        $resolved = Get-NormalizedPath $directory.FullName
        if (-not (Test-SameOrChild $resolved $Root) -or
                ($directory.Attributes -band `
                    [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            Stop-Lab 'LAB_CAPTURE_DELETE_REJECTED'
        }
        $parsed = Read-CaptureMetadata $resolved $Root
        if ($parsed.Expires -le $now) {
            Remove-ValidatedTree $resolved $Root
        }
    }
}

function Assert-OutsideRepositoryFile([string]$Path) {
    $fullPath = Get-NormalizedPath $Path
    if ([string]::IsNullOrWhiteSpace($fullPath) -or
            (Test-SameOrChild $fullPath $repoRoot) -or
            -not (Test-Path -LiteralPath $fullPath -PathType Leaf) -or
            (Test-PathContainsReparsePoint $fullPath)) {
        Stop-Lab 'LAB_SECRET_FILE_REJECTED'
    }
    return $fullPath
}

function Get-LatencyBucket([long]$Milliseconds) {
    if ($Milliseconds -lt 100) { return 'LT_100_MS' }
    if ($Milliseconds -lt 500) { return 'FROM_100_TO_499_MS' }
    if ($Milliseconds -lt 2000) { return 'FROM_500_TO_1999_MS' }
    if ($Milliseconds -lt 10000) { return 'FROM_2000_TO_9999_MS' }
    return 'GTE_10000_MS'
}

function Read-BoundedResponse(
    [System.Net.Http.HttpContent]$Content,
    [int]$MaximumBytes
) {
    $stream = $Content.ReadAsStreamAsync().GetAwaiter().GetResult()
    $buffer = New-Object byte[] 8192
    $memory = New-Object System.IO.MemoryStream
    try {
        while (($read = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
            if (($memory.Length + $read) -gt $MaximumBytes) {
                Stop-Lab 'LAB_PROVIDER_RESPONSE_TOO_LARGE'
            }
            $memory.Write($buffer, 0, $read)
        }
        return [System.Text.Encoding]::UTF8.GetString($memory.ToArray())
    }
    finally {
        $memory.Dispose()
        $stream.Dispose()
    }
}

function Get-OperatorHash {
    $operatorBytes = [System.Text.Encoding]::UTF8.GetBytes(
        [System.Security.Principal.WindowsIdentity]::GetCurrent().Name)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([System.BitConverter]::ToString(
            $sha.ComputeHash($operatorBytes))).Replace(
                '-', '').ToLowerInvariant()
    }
    finally { $sha.Dispose() }
}

function Write-CaptureMetadata(
    [string]$Directory,
    [string]$ArtifactId,
    [datetimeoffset]$Created,
    [string]$OperatorHash,
    [string]$Status,
    [string]$HttpClass,
    [string]$LatencyBucket,
    [long]$LatencyMs,
    [string]$CaptureSource
) {
    $metadata = [ordered]@{
        schemaVersion = 'qwen-general-lab-artifact.v2'
        artifactId = $ArtifactId
        caseId = $CaseId
        depth = $Depth
        createdAtUtc = $Created.ToUniversalTime().ToString('o')
        expiresAtUtc = $Created.ToUniversalTime().AddHours(
            $ttlHours).ToString('o')
        operatorIdentitySha256 = $OperatorHash
        provider = 'QWEN'
        model = $providerModel
        selectionVersion = 'qwen-3-7-flash-v8'
        providerContract = 'general.provider-draft.v4'
        compilerProfile = 'general-provider-draft-compiler.v4'
        captureSource = $CaptureSource
        status = $Status
        httpClass = $HttpClass
        latencyBucket = $LatencyBucket
        latencyMs = $LatencyMs
        attemptCount = 1
    }
    Write-Utf8NoBom (Join-Path $Directory 'metadata.json') `
        ($metadata | ConvertTo-Json -Compress)
}

function Invoke-LabMain {
    if (-not (Test-Path -LiteralPath $corpusPath -PathType Leaf)) {
        Stop-Lab 'LAB_CORPUS_MISSING'
    }
    $corpus = Get-Content -LiteralPath $corpusPath -Raw -Encoding UTF8 |
        ConvertFrom-Json
    $cases = @($corpus.cases | Where-Object { $_.caseId -ceq $CaseId })
    if ($cases.Count -ne 1) { Stop-Lab 'LAB_CASE_ID_REJECTED' }
    $case = $cases[0]

    try {
        $rawRoot = Assert-DedicatedRawRoot `
            $RawArtifactRoot $repoRoot 'LAB_RAW_ROOT_REJECTED'
    }
    catch {
        Stop-Lab 'LAB_RAW_ROOT_REJECTED'
    }
    try {
        Protect-DedicatedRawRootAcl `
            $rawRoot 'LAB_OS_ACCESS_BOUNDARY_FAILED'
    }
    catch { Stop-Lab 'LAB_OS_ACCESS_BOUNDARY_FAILED' }
    $script:ValidatedRawRoot = $rawRoot
    Remove-ExpiredArtifacts $rawRoot
    Write-TestTrace $rawRoot 'ROOT_READY'

    if (-not $AuthorizeRealProvider) {
        Write-Output (('LAB_AUTHORIZATION_REQUIRED caseId={0} depth={1}') -f `
            $CaseId, $Depth)
        exit 1
    }

    $secretPath = Assert-OutsideRepositoryFile $SecretFile
    if (-not (Test-Path -LiteralPath $contractPath -PathType Leaf) -or
            -not (Test-Path -LiteralPath $systemPromptPath -PathType Leaf)) {
        Stop-Lab 'LAB_CANDIDATE_BUNDLE_MISSING'
    }
    $apiKey = (Get-Content -LiteralPath $secretPath -Raw -Encoding UTF8).Trim()
    if ($apiKey.Length -lt 8 -or $apiKey.Length -gt 512 -or
            $apiKey -match '\s') {
        Stop-Lab 'LAB_SECRET_FILE_REJECTED'
    }

    $endpoint = $providerEndpoint
    if ($TestOnlyLoopbackPort -ne 0) {
        if ($env:QWEN_GENERAL_LAB_TEST_LOOPBACK -cne `
                'AUTHORIZED_TEST_PROCESS_ONLY') {
            Stop-Lab 'LAB_TEST_TRANSPORT_REJECTED'
        }
        $endpoint = 'http://127.0.0.1:{0}/qwen-general-lab-test/' -f `
            $TestOnlyLoopbackPort
    }
    $captureSource = if ($TestOnlyLoopbackPort -ne 0) {
        'TEST_LOOPBACK'
    }
    else {
        'REAL_PROVIDER'
    }
    Write-TestTrace $rawRoot 'TRANSPORT_READY'

    $created = [datetimeoffset](Get-Date).ToUniversalTime()
    $artifactId = ('capture-{0}-{1}-{2}-{3}' -f `
        $CaseId, $Depth.ToLowerInvariant(),
        $created.ToString('yyyyMMddTHHmmssZ'),
        [guid]::NewGuid().ToString('N'))
    if ($artifactId -cnotmatch $captureNamePattern) {
        Stop-Lab 'LAB_ARTIFACT_ID_REJECTED'
    }
    $artifactDirectory = Join-Path $rawRoot $artifactId
    New-Item -ItemType Directory -Path $artifactDirectory | Out-Null
    try {
        Protect-DedicatedRawRootAcl `
            $artifactDirectory 'LAB_OS_ACCESS_BOUNDARY_FAILED'
    }
    catch { Stop-Lab 'LAB_OS_ACCESS_BOUNDARY_FAILED' }
    Write-TestTrace $rawRoot 'ARTIFACT_READY'

    Add-Type -AssemblyName System.Web.Extensions
    $serializer = New-Object `
        System.Web.Script.Serialization.JavaScriptSerializer
    $serializer.MaxJsonLength = 262144
    $serializer.RecursionLimit = 64
    $contractRaw = Get-Content -LiteralPath $contractPath -Raw -Encoding UTF8
    [void]$serializer.DeserializeObject($contractRaw)
    Write-TestTrace $rawRoot 'CONTRACT_READY'
    $systemPrompt = Get-Content -LiteralPath $systemPromptPath -Raw -Encoding UTF8
    Write-TestTrace $rawRoot 'PROMPT_READY'
    $trustedInputJson = '{"kind":"EXPLANATION","topic":' +
        $serializer.Serialize([string]$case.topic) + ',"depth":' +
        $serializer.Serialize($Depth) + ',"audience":"GUEST",' +
        '"expectedContentVersion":"public-1","question":' +
        $serializer.Serialize([string]$case.prompts.$Depth) + '}'
    $maxTokens = 1200
    $parallelToolCalls = 'false'
    Write-TestTrace $rawRoot 'REQUEST_SERIALIZE_START'
    $requestJson = '{"model":' + $serializer.Serialize($providerModel) +
        ',"temperature":0.0,"max_tokens":' + [string]$maxTokens +
        ',"stream":false,"parallel_tool_calls":' + $parallelToolCalls +
        ',"messages":[{"role":"system","content":' +
        $serializer.Serialize($systemPrompt) +
        '},{"role":"user","content":' +
        $serializer.Serialize($trustedInputJson) +
        '}],"tools":[{"type":"function","function":{' +
        '"name":"emit_general_provider_draft_v4",' +
        '"description":"Return the approved General Explanation draft.",' +
        '"parameters":' + $contractRaw + '}}],"tool_choice":{' +
        '"type":"function","function":{' +
        '"name":"emit_general_provider_draft_v4"}}}'
    Write-Utf8NoBom (Join-Path $artifactDirectory 'request.raw.json') `
        $requestJson
    Write-TestTrace $rawRoot 'REQUEST_READY'

    $operatorHash = Get-OperatorHash
    Write-TestTrace $rawRoot 'OPERATOR_READY'
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $handler = $null
    if ($TestOnlyLoopbackPort -ne 0) {
        $handler = [System.Net.Http.HttpClientHandler]::new()
        $handler.UseProxy = $false
        $http = [System.Net.Http.HttpClient]::new($handler, $true)
    }
    else {
        $http = [System.Net.Http.HttpClient]::new()
    }
    Write-TestTrace $rawRoot 'HTTP_READY'
    $response = $null
    $message = $null
    try {
        $http.Timeout = [TimeSpan]::FromSeconds(20)
        $message = [System.Net.Http.HttpRequestMessage]::new(
            [System.Net.Http.HttpMethod]::Post, $endpoint)
        $message.Headers.ExpectContinue = $false
        $message.Headers.Authorization = `
            [System.Net.Http.Headers.AuthenticationHeaderValue]::new(
                'Bearer', $apiKey)
        $message.Content = [System.Net.Http.StringContent]::new(
            $requestJson, [System.Text.Encoding]::UTF8, 'application/json')
        try {
            Write-TestTrace $rawRoot 'SEND_START'
            $response = $http.SendAsync(
                $message,
                [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead
            ).GetAwaiter().GetResult()
            Write-TestTrace $rawRoot 'SEND_COMPLETE'
        }
        catch {
            $stopwatch.Stop()
            Write-CaptureMetadata $artifactDirectory $artifactId $created `
                $operatorHash 'TRANSPORT_FAILED' 'TRANSPORT_UNAVAILABLE' `
                (Get-LatencyBucket $stopwatch.ElapsedMilliseconds) `
                $stopwatch.ElapsedMilliseconds $captureSource
            Write-Output (('LAB_CAPTURE status=TRANSPORT_FAILED caseId={0} ' +
                'depth={1} httpClass=TRANSPORT_UNAVAILABLE latencyBucket={2} ' +
                'tokens=UNKNOWN') -f $CaseId, $Depth,
                (Get-LatencyBucket $stopwatch.ElapsedMilliseconds))
            exit 1
        }
        $statusCode = [int]$response.StatusCode
        try {
            $responseText = Read-BoundedResponse `
                $response.Content $maximumResponseBytes
        }
        catch [LabClosedFailure] {
            $stopwatch.Stop()
            Write-CaptureMetadata $artifactDirectory $artifactId $created `
                $operatorHash 'RESPONSE_REJECTED' 'SUCCESS' `
                (Get-LatencyBucket $stopwatch.ElapsedMilliseconds) `
                $stopwatch.ElapsedMilliseconds $captureSource
            throw
        }
        Write-Utf8NoBom (Join-Path $artifactDirectory 'response.raw.json') `
            $responseText
    }
    finally {
        $stopwatch.Stop()
        if ($null -ne $response) { $response.Dispose() }
        if ($null -ne $message) { $message.Dispose() }
        $http.Dispose()
    }

    $status = if ($statusCode -ge 200 -and $statusCode -lt 300) {
        'CAPTURED'
    } elseif ($statusCode -eq 429) {
        'RATE_LIMITED'
    } elseif ($statusCode -ge 500) {
        'SERVER_ERROR'
    } else {
        'CLIENT_ERROR'
    }
    $httpClass = if ($statusCode -ge 200 -and $statusCode -lt 300) {
        'SUCCESS'
    } elseif ($statusCode -eq 429) {
        'RATE_LIMITED'
    } elseif ($statusCode -ge 500) {
        'SERVER_ERROR'
    } else {
        'CLIENT_ERROR'
    }
    $latencyBucket = Get-LatencyBucket $stopwatch.ElapsedMilliseconds
    Write-CaptureMetadata $artifactDirectory $artifactId $created `
        $operatorHash $status $httpClass $latencyBucket `
        $stopwatch.ElapsedMilliseconds $captureSource
    Write-Output (('LAB_CAPTURE status={0} caseId={1} depth={2} ' +
        'httpClass={3} latencyBucket={4} tokens=UNKNOWN') -f `
        $status, $CaseId, $Depth, $httpClass, $latencyBucket)
    if ($status -ne 'CAPTURED') { exit 1 }
}

try {
    Invoke-LabMain
}
catch [LabClosedFailure] {
    Write-Output $_.Exception.Message
    exit 1
}
catch {
    if (-not [string]::IsNullOrWhiteSpace($script:ValidatedRawRoot)) {
        try {
            Write-TestTrace $script:ValidatedRawRoot `
                ('ERROR_' + $_.Exception.GetType().Name)
        }
        catch { }
    }
    Write-Output 'LAB_INTERNAL_ERROR'
    exit 1
}
