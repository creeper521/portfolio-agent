param(
    [Parameter(Mandatory = $true)]
    [string]$BackendBaseUrl,
    [Parameter(Mandatory = $true)]
    [string]$ExpectedContentVersion,
    [Parameter(Mandatory = $true)]
    [ValidateSet('glm-4-7-flash', 'qwen-3-7-flash')]
    [string]$ModelRef,
    [Parameter(Mandatory = $true)]
    [string]$SelectionVersion,
    [ValidateRange(1, 10)]
    [int]$TrialsPerDepth = 3,
    [ValidateRange(1, 300)]
    [int]$TimeoutSeconds = 30,
    [ValidateRange(0, 60000)]
    [int]$InterTrialDelayMilliseconds = 10000,
    [string]$LatencySamplesFile = '',
    [switch]$Baseline,
    [string]$FixtureDirectory
)

$ErrorActionPreference = 'Stop'
$script:allowedFailureCodes = @(
    'GENERAL_QUALITY_CONFIG_INVALID',
    'GENERAL_QUALITY_RESPONSE_INVALID',
    'GENERAL_QUALITY_TRANSPORT_FAILED',
    'GENERAL_QUALITY_GATE_FAILED'
)

function Stop-Quality([string]$Code) {
    if ($Code -notin $script:allowedFailureCodes) {
        throw 'GENERAL_QUALITY_RESPONSE_INVALID'
    }
    throw $Code
}

function Decode-Text([string]$Value) {
    return [System.Text.Encoding]::UTF8.GetString(
        [System.Convert]::FromBase64String($Value)
    )
}

function Get-ProcessEnvironmentValue([string]$Name) {
    return [System.Environment]::GetEnvironmentVariable(
        $Name,
        [System.EnvironmentVariableTarget]::Process
    )
}

function Test-ApprovedFlag([string]$Value) {
    return [string]::Equals(
        $Value,
        'true',
        [System.StringComparison]::OrdinalIgnoreCase
    )
}

function Assert-ApprovedEnvironment {
    foreach ($name in @('PORTFOLIO_MODEL_RUNTIME_ENABLED')) {
        if (-not (Test-ApprovedFlag (Get-ProcessEnvironmentValue $name))) {
            Stop-Quality 'GENERAL_QUALITY_CONFIG_INVALID'
        }
    }
    $modelEnvironment = switch ($ModelRef) {
        'glm-4-7-flash' {
            @('PORTFOLIO_GLM_ENABLED', 'PORTFOLIO_GLM_DATA_POLICY_APPROVED',
                'PORTFOLIO_GLM_API_KEY')
        }
        'qwen-3-7-flash' {
            @('PORTFOLIO_QWEN_ENABLED', 'PORTFOLIO_QWEN_DATA_POLICY_APPROVED',
                'PORTFOLIO_QWEN_API_KEY')
        }
        default { Stop-Quality 'GENERAL_QUALITY_CONFIG_INVALID' }
    }
    if (-not (Test-ApprovedFlag (Get-ProcessEnvironmentValue $modelEnvironment[0])) -or
            -not (Test-ApprovedFlag (Get-ProcessEnvironmentValue $modelEnvironment[1])) -or
            [string]::IsNullOrWhiteSpace(
                (Get-ProcessEnvironmentValue $modelEnvironment[2])) -or
            [string]::IsNullOrWhiteSpace($SelectionVersion)) {
        Stop-Quality 'GENERAL_QUALITY_CONFIG_INVALID'
    }
}

function Get-Scenarios {
    return @(
        @{
            Id = 'CONCISE'
            Input = Decode-Text '566A6KaB5qaC5ousIEpXVCDnmoTmpoLlv7Xlkozlt6XkvZzmnLrliLY='
            ExpectedKind = 'ANSWER'
            ExpectedBucket = 'CONCISE'
            Trials = $TrialsPerDepth
        },
        @{
            Id = 'STANDARD'
            Input = Decode-Text '6Kej6YeK5LiA5LiLIFJlZGlzIOeahOaMgeS5heWMluacuuWItg=='
            ExpectedKind = 'ANSWER'
            ExpectedBucket = 'STANDARD'
            Trials = $TrialsPerDepth
        },
        @{
            Id = 'DETAILED'
            Input = Decode-Text '6K+m57uG5rex5YWl5Zyw6K6y6Kej5LiA5LiL5pWw5o2u5bqT57Si5byV55qE5bel5L2c5py65Yi25LiO6YCC55So6L6555WM'
            ExpectedKind = 'ANSWER'
            ExpectedBucket = 'DETAILED'
            Trials = $TrialsPerDepth
        },
        @{
            Id = 'CONVERSATIONAL'
            Input = Decode-Text '5L2g5aW9'
            ExpectedKind = 'CONVERSATIONAL'
            ExpectedBucket = 'NONE'
            Trials = $TrialsPerDepth
        },
        @{
            Id = 'COMPARISON'
            Input = Decode-Text '5a+55q+UIFJlZGlzIOWSjCBNZW1jYWNoZWQg5Zyo5oyB5LmF5YyW5ZKM57q/56iL5qih5Z6L5LiK55qE5beu5byC'
            ExpectedKind = 'ANSWER'
            ExpectedBucket = 'NONE'
            Subjects = @('Redis', 'Memcached')
            Dimensions = @('PERSISTENCE', 'THREAD_MODEL')
            Trials = 1
        }
    )
}

function Read-FixtureResponse([string]$ScenarioId, [int]$Trial) {
    if (-not (Test-Path -LiteralPath $FixtureDirectory -PathType Container)) {
        Stop-Quality 'GENERAL_QUALITY_CONFIG_INVALID'
    }
    $path = Join-Path $FixtureDirectory `
        ($ScenarioId.ToLowerInvariant() + '-' + $Trial + '.json')
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Stop-Quality 'GENERAL_QUALITY_RESPONSE_INVALID'
    }
    try {
        return [System.IO.File]::ReadAllText(
            $path,
            [System.Text.UTF8Encoding]::new($false)
        ) | ConvertFrom-Json
    }
    catch {
        Stop-Quality 'GENERAL_QUALITY_RESPONSE_INVALID'
    }
}

function Invoke-LiveResponse([string]$InputText) {
    $body = @{
        requestId = [guid]::NewGuid()
        modelSelection = @{
            kind = 'MODEL'
            modelRef = $ModelRef
            selectionVersion = $SelectionVersion
        }
        command = @{
            kind = 'ASK'
            input = @{ kind = 'FREE_TEXT'; text = $InputText }
        }
        surfaceContext = @{
            audienceRole = 'INTERVIEWER'
            requestSource = 'AGENT_PAGE'
        }
        conversationWindow = @()
    } | ConvertTo-Json -Depth 6 -Compress
    try {
        $http = Invoke-WebRequest -UseBasicParsing `
            -Uri ($BackendBaseUrl.TrimEnd('/') + '/api/agent/turns') `
            -Method Post `
            -ContentType 'application/json; charset=utf-8' `
            -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) `
            -TimeoutSec $TimeoutSeconds
        $stream = $http.RawContentStream
        if ($null -eq $stream) {
            Stop-Quality 'GENERAL_QUALITY_RESPONSE_INVALID'
        }
        if ($stream.CanSeek) { $stream.Position = 0 }
        $reader = [System.IO.StreamReader]::new(
            $stream,
            [System.Text.UTF8Encoding]::new($false, $true),
            $true,
            1024,
            $true
        )
        try {
            return ($reader.ReadToEnd() | ConvertFrom-Json)
        }
        finally {
            $reader.Dispose()
        }
    }
    catch {
        Stop-Quality 'GENERAL_QUALITY_TRANSPORT_FAILED'
    }
}

function Get-Response([hashtable]$Scenario, [int]$Trial) {
    if (-not [string]::IsNullOrWhiteSpace($FixtureDirectory)) {
        return Read-FixtureResponse $Scenario.Id $Trial
    }
    return Invoke-LiveResponse $Scenario.Input
}

function Remove-NonProse([string]$Value) {
    $result = [regex]::Replace($Value, '(?s)```.*?```', ' ')
    $result = [regex]::Replace($result, '`[^`]*`', ' ')
    return [regex]::Replace($result, '(?i)(https?://|www\.)\S+', ' ')
}

function Test-CodeShape([string]$Segment) {
    $trimmed = $Segment.Trim()
    if ($trimmed -match '(=>|->|=)' -or $trimmed -match '[{}]\s*$') {
        return $true
    }
    if ($trimmed -match '(?i)^(SELECT|INSERT|UPDATE|DELETE|CREATE|DROP|ALTER|FROM|WHERE|JOIN|IMPORT|PACKAGE|CLASS|DEF|FUNCTION|CONST|LET|PUBLIC|PRIVATE|RETURN)\b') {
        return $true
    }
    return $trimmed -match '^<.*>\s*$'
}

function Test-SimplifiedChineseProse([string[]]$Values) {
    foreach ($value in $Values) {
        if ([string]::IsNullOrWhiteSpace($value)) { continue }
        $prose = Remove-NonProse $value
        foreach ($segment in @($prose -split '[\u3002\uFF01\uFF1F\uFF1B.!?;]+')) {
            if ([string]::IsNullOrWhiteSpace($segment) -or (Test-CodeShape $segment)) {
                continue
            }
            $containsCjk = $segment -match '[\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF]'
            $latinWords = [regex]::Matches(
                $segment,
                "[A-Za-z]+(?:[-'][A-Za-z]+)*"
            ).Count
            if (-not $containsCjk -and $latinWords -ge 3) {
                return $false
            }
        }
    }
    return $true
}

function Get-SentenceCount([string[]]$Values) {
    $count = 0
    foreach ($value in $Values) {
        if ([string]::IsNullOrWhiteSpace($value)) { continue }
        $prose = Remove-NonProse $value
        $count += @($prose -split '[\u3002\uFF01\uFF1F\uFF1B.!?;]+' |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count
    }
    return $count
}

function Get-ObservedBucket([int]$SentenceCount) {
    if ($SentenceCount -eq 2) { return 'CONCISE' }
    if ($SentenceCount -ge 4 -and $SentenceCount -le 6) { return 'STANDARD' }
    if ($SentenceCount -ge 8 -and $SentenceCount -le 12) { return 'DETAILED' }
    return 'OUTSIDE'
}

function Get-Sections([object]$Response) {
    $sections = @()
    foreach ($goal in @($Response.answer.goalResults)) {
        $sections += @($goal.presentation.sections)
    }
    return @($sections)
}

function Test-ComparisonPairSections(
    [object[]]$Sections,
    [string[]]$Subjects,
    [string[]]$Dimensions
) {
    $expected = [System.Collections.Generic.HashSet[string]]::new(
        [StringComparer]::Ordinal)
    foreach ($subject in $Subjects) {
        foreach ($dimension in $Dimensions) {
            $null = $expected.Add($subject + ' · ' + $dimension)
        }
    }
    $actual = [System.Collections.Generic.HashSet[string]]::new(
        [StringComparer]::Ordinal)
    foreach ($section in $Sections) {
        $title = [string]$section.title
        if (-not $expected.Contains($title)) { continue }
        if ([string]$section.sectionKind -cne 'SOLUTION' -or
                [string]::IsNullOrWhiteSpace([string]$section.content) -or
                -not $actual.Add($title)) {
            return $false
        }
    }
    return $actual.SetEquals($expected)
}

function Measure-Trial([hashtable]$Scenario, [int]$Trial) {
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Get-Response $Scenario $Trial
    }
    catch {
        $timer.Stop()
        $timedOut = $timer.Elapsed.TotalSeconds -ge ($TimeoutSeconds * 0.9)
        return @{
            Language = $false; Structure = $false; Bucket = $false
            Terminal = $false; Observed = 'INVALID'
            Latency = $timer.ElapsedMilliseconds; TimedOut = $timedOut
            PublicTerminal = if ($timedOut) {
                'TRANSPORT_TIMEOUT'
            } else {
                'TRANSPORT_FAILURE'
            }
        }
    }
    $timer.Stop()
    if ($null -eq $response -or [string]$response.kind -cne $Scenario.ExpectedKind) {
        return @{
            Language = $false; Structure = $false; Bucket = $false
            Terminal = $false; Observed = 'INVALID'; Latency = $timer.ElapsedMilliseconds
            TimedOut = $false
            PublicTerminal = if ($null -eq $response) {
                'MISSING'
            }
            elseif ([string]::IsNullOrWhiteSpace([string]$response.code)) {
                [string]$response.kind
            }
            else {
                [string]$response.kind + ':' + [string]$response.code
            }
        }
    }

    if ($Scenario.ExpectedKind -eq 'CONVERSATIONAL') {
        $message = [string]$response.message
        $valid = -not [string]::IsNullOrWhiteSpace($message)
        return @{
            Language = $valid -and (Test-SimplifiedChineseProse @($message))
            Structure = $valid; Bucket = $true; Terminal = $valid
            Observed = 'NONE'; Latency = $timer.ElapsedMilliseconds
            TimedOut = $false
            PublicTerminal = 'CONVERSATIONAL'
        }
    }

    $terminal = $null -ne $response.answer -and
        [string]$response.answer.contentReleaseId -ceq $ExpectedContentVersion -and
        [string]$response.answer.resolution -in @('COMPLETE', 'PARTIAL') -and
        'GENERAL_KNOWLEDGE' -in @($response.answer.sourceComposition)
    $sections = if ($terminal) { Get-Sections $response } else { @() }
    $contents = @($sections | ForEach-Object { [string]$_.content })
    $language = $terminal -and (Test-SimplifiedChineseProse $contents)

    if ($Scenario.Id -in @('CONCISE', 'STANDARD', 'DETAILED')) {
        $concept = Decode-Text '5qaC5b+1'
        $mechanism = Decode-Text '5py65Yi2'
        $main = @($sections | Where-Object {
            [string]$_.title -in @($concept, $mechanism)
        })
        $structure = $main.Count -eq 2 -and
            [string]$main[0].title -ceq $concept -and
            [string]$main[1].title -ceq $mechanism
        $sentenceCount = if ($structure) {
            Get-SentenceCount @([string]$main[0].content, [string]$main[1].content)
        } else { 0 }
        $observed = Get-ObservedBucket $sentenceCount
        return @{
            Language = $language; Structure = $structure
            Bucket = $observed -ceq $Scenario.ExpectedBucket
            Terminal = $terminal; Observed = $observed
            Latency = $timer.ElapsedMilliseconds
            TimedOut = $false
            PublicTerminal = 'ANSWER:' + [string]$response.answer.resolution
        }
    }

    return @{
        Language = $language
        Structure = $terminal -and (Test-ComparisonPairSections `
            $sections $Scenario.Subjects $Scenario.Dimensions)
        Bucket = $true; Terminal = $terminal
        Observed = 'NONE'; Latency = $timer.ElapsedMilliseconds
        TimedOut = $false
        PublicTerminal = 'ANSWER:' + [string]$response.answer.resolution
    }
}

function Format-Metric([string]$Name, [int]$Passed, [int]$Total) {
    return "$Name=$Passed/$Total"
}

function Get-Percentile([long[]]$Values, [double]$Percentile) {
    $sorted = @($Values | Sort-Object)
    if ($sorted.Count -eq 0) { return 0 }
    $index = [math]::Ceiling($Percentile * $sorted.Count) - 1
    return [long]$sorted[[math]::Max(0, [math]::Min($index, $sorted.Count - 1))]
}

try {
    Assert-ApprovedEnvironment
    $lines = @()
    $gatePassed = $true
    foreach ($scenario in Get-Scenarios) {
        $results = @()
        for ($trial = 1; $trial -le $scenario.Trials; $trial++) {
            if ([string]::IsNullOrWhiteSpace($FixtureDirectory) -and
                    $InterTrialDelayMilliseconds -gt 0 -and
                    ($lines.Count -gt 0 -or $trial -gt 1)) {
                Start-Sleep -Milliseconds $InterTrialDelayMilliseconds
            }
            $result = Measure-Trial $scenario $trial
            $results += $result
            if (-not [string]::IsNullOrWhiteSpace($LatencySamplesFile) -and
                    $scenario.Id -ne 'CONVERSATIONAL') {
                Add-Content -LiteralPath $LatencySamplesFile -Encoding UTF8 `
                    -Value ('GENERAL_KNOWLEDGE,' + [long]$result.Latency)
            }
        }
        $language = @($results | Where-Object { $_.Language }).Count
        $structure = @($results | Where-Object { $_.Structure }).Count
        $bucket = @($results | Where-Object { $_.Bucket }).Count
        $terminal = @($results | Where-Object { $_.Terminal }).Count
        $timeouts = @($results | Where-Object { $_.TimedOut }).Count
        $total = $results.Count
        [long[]]$latencies = @($results | ForEach-Object { [long]$_.Latency })
        $p50 = Get-Percentile $latencies 0.50
        $p95 = Get-Percentile $latencies 0.95
        $observed = @($results | Group-Object { [string]$_.Observed } | ForEach-Object {
            $_.Name + ':' + $_.Count
        }) -join ','
        $publicTerminals = @($results |
            Group-Object { [string]$_.PublicTerminal } | ForEach-Object {
            $_.Name + ':' + $_.Count
        }) -join ','
        if ($language -ne $total -or $structure -ne $total -or
                $bucket -ne $total -or $terminal -ne $total) {
            $gatePassed = $false
        }
        $lines += ('GENERAL_QUALITY scenario={0} trials={1} {2} {3} {4} {5} timeout={6}/{1} observed={7} publicTerminal={8} latencyP50Ms={9} latencyP95Ms={10}' -f `
            $scenario.Id, $total,
            (Format-Metric 'language' $language $total),
            (Format-Metric 'structure' $structure $total),
            (Format-Metric 'bucket' $bucket $total),
            (Format-Metric 'terminal' $terminal $total),
            $timeouts, $observed, $publicTerminals, $p50, $p95)
    }

    $lines | Write-Output
    Write-Output ('GENERAL_QUALITY_RESULT status=' + $(if ($gatePassed) {
        'PASS'
    } else {
        'FAIL'
    }))
    if (-not $Baseline -and -not $gatePassed) {
        Stop-Quality 'GENERAL_QUALITY_GATE_FAILED'
    }
    if (-not $Baseline) {
        Write-Output 'GENERAL_QUALITY_PASS'
    }
}
catch {
    $code = [string]$_.Exception.Message
    if ($code -notin $script:allowedFailureCodes) {
        $code = 'GENERAL_QUALITY_RESPONSE_INVALID'
    }
    [Console]::Error.WriteLine($code)
    exit 1
}
