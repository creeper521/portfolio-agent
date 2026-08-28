$ErrorActionPreference = 'Stop'

$checker = Join-Path $PSScriptRoot 'assert-live-general-answer-quality.ps1'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('general-answer-quality-' + [guid]::NewGuid().ToString('N'))
$environmentNames = @(
    'PORTFOLIO_MODEL_RUNTIME_ENABLED',
    'PORTFOLIO_GLM_ENABLED',
    'PORTFOLIO_GLM_DATA_POLICY_APPROVED',
    'PORTFOLIO_GLM_API_KEY'
)

function ConvertFrom-CodePoints([int[]]$Values) {
    return -join @($Values | ForEach-Object { [char]$_ })
}

$conceptTitle = ConvertFrom-CodePoints @(0x6982, 0x5FF5)
$mechanismTitle = ConvertFrom-CodePoints @(0x673A, 0x5236)
$zhSentence = ConvertFrom-CodePoints @(0x4E2D, 0x6587, 0x3002)
$zhMessage = ConvertFrom-CodePoints @(0x4F60, 0x597D, 0x3002)

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Section(
    [string]$Title,
    [string]$Content,
    [string]$SectionKind = 'GENERAL_PRINCIPLE'
) {
    return @{
        sectionId = 'section-' + [guid]::NewGuid().ToString('N')
        sectionKind = $SectionKind
        title = $Title
        content = $Content
        support = @{ kind = 'GENERAL_KNOWLEDGE'; publicSourceKeys = @() }
    }
}

function Answer([object[]]$Sections) {
    return @{
        requestId = [guid]::NewGuid().ToString()
        kind = 'ANSWER'
        conversation = @{ conversationId = 'conversation-fixture' }
        answer = @{
            resolution = 'COMPLETE'
            contentReleaseId = 'test-v1'
            goalResults = @(@{
                goalId = 'goal-general'
                label = 'general'
                coverage = 'FULL'
                presentation = @{ kind = 'SECTIONED'; sections = $Sections }
                notices = @()
            })
            sourceCatalog = @{ sources = @() }
            sourceComposition = @('GENERAL_KNOWLEDGE')
        }
    }
}

function Write-Fixture([string]$Scenario, [object]$Value) {
    $path = Join-Path $fixtureRoot ($Scenario.ToLowerInvariant() + '-1.json')
    [System.IO.File]::WriteAllText(
        $path,
        ($Value | ConvertTo-Json -Depth 12 -Compress),
        [System.Text.UTF8Encoding]::new($false)
    )
}

function Invoke-Checker([switch]$Baseline) {
    $latencySamples = Join-Path $fixtureRoot `
        ('latency-' + [guid]::NewGuid().ToString('N') + '.csv')
    $arguments = @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $checker,
        '-BackendBaseUrl', 'http://fixture.invalid',
        '-ExpectedContentVersion', 'test-v1',
        '-ModelRef', 'glm-4-7-flash',
        '-SelectionVersion', 'glm-4-7-flash-v1',
        '-TrialsPerDepth', '1',
        '-FixtureDirectory', $fixtureRoot,
        '-LatencySamplesFile', $latencySamples
    )
    if ($Baseline) { $arguments += '-Baseline' }
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = (& powershell.exe @arguments 2>&1 | Out-String)
        return @{
            ExitCode = $LASTEXITCODE
            Output = $output
            LatencySamplesFile = $latencySamples
        }
    }
    finally {
        $ErrorActionPreference = $previous
    }
}

$snapshots = @{}
foreach ($name in $environmentNames) {
    $snapshots[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    New-Item -ItemType Directory -Path $fixtureRoot -Force | Out-Null
    foreach ($name in @(
        'PORTFOLIO_MODEL_RUNTIME_ENABLED',
        'PORTFOLIO_GLM_ENABLED',
        'PORTFOLIO_GLM_DATA_POLICY_APPROVED'
    )) {
        [Environment]::SetEnvironmentVariable($name, 'true', 'Process')
    }
    [Environment]::SetEnvironmentVariable(
        'PORTFOLIO_GLM_API_KEY', 'fixture-key', 'Process')

    Write-Fixture 'CONCISE' (Answer @(
        (Section $conceptTitle $zhSentence),
        (Section $mechanismTitle $zhSentence)
    ))
    Write-Fixture 'STANDARD' (Answer @(
        (Section $conceptTitle ($zhSentence + $zhSentence)),
        (Section $mechanismTitle ($zhSentence + $zhSentence))
    ))
    Write-Fixture 'DETAILED' (Answer @(
        (Section $conceptTitle ($zhSentence + $zhSentence + $zhSentence + $zhSentence)),
        (Section $mechanismTitle ($zhSentence + $zhSentence + $zhSentence + $zhSentence))
    ))
    Write-Fixture 'CONVERSATIONAL' @{
        requestId = [guid]::NewGuid().ToString()
        kind = 'CONVERSATIONAL'
        message = $zhMessage
        conversation = @{ conversationId = 'conversation-fixture' }
    }
    Write-Fixture 'COMPARISON' (Answer @(
        (Section 'Redis · PERSISTENCE' $zhSentence 'SOLUTION'),
        (Section 'Redis · THREAD_MODEL' $zhSentence 'SOLUTION'),
        (Section 'Memcached · PERSISTENCE' $zhSentence 'SOLUTION'),
        (Section 'Memcached · THREAD_MODEL' $zhSentence 'SOLUTION')
    ))

    $passing = Invoke-Checker
    Assert-True ($passing.ExitCode -eq 0) `
        "valid quality fixtures must pass: $($passing.Output)"
    Assert-True ($passing.Output -match 'GENERAL_QUALITY_PASS') `
        'valid quality fixtures must emit only the aggregate pass marker.'
    Assert-True ($passing.Output -match 'GENERAL_QUALITY_RESULT status=PASS') `
        'valid quality fixtures must emit the aggregate result.'
    Assert-True ($passing.Output -match 'scenario=CONCISE.*observed=CONCISE:1') `
        'quality report must name the observed output bucket.'
    Assert-True ($passing.Output -match 'scenario=CONCISE.*publicTerminal=ANSWER:COMPLETE:1') `
        'quality report must aggregate only the public terminal enum.'
    Assert-True ($passing.Output -match 'scenario=CONCISE.*timeout=0/1.*latencyP50Ms=\d+.*latencyP95Ms=\d+') `
        'quality report must include timeout rate and latency percentiles.'
    Assert-True ($passing.Output -notmatch 'Redis|Memcached|SELECT|example\.com') `
        'passing output leaked fixture questions or answers.'
    $latencyLines = @(Get-Content -LiteralPath $passing.LatencySamplesFile)
    Assert-True ($latencyLines.Count -eq 4) `
        'quality checker must emit samples only for General Provider trials.'
    Assert-True (@($latencyLines | Where-Object {
        $_ -notmatch '^GENERAL_KNOWLEDGE,[0-9]+$'
    }).Count -eq 0) 'latency samples must contain only operation and milliseconds.'

    Write-Fixture 'COMPARISON' (Answer @(
        (Section 'Redis · PERSISTENCE' $zhSentence 'SOLUTION'),
        (Section 'Redis · THREAD_MODEL' $zhSentence 'SOLUTION'),
        (Section 'Memcached · PERSISTENCE' $zhSentence 'SOLUTION')
    ))
    $missingComparisonPair = Invoke-Checker
    Assert-True ($missingComparisonPair.ExitCode -eq 1 -and
            $missingComparisonPair.Output -match
            'scenario=COMPARISON.*structure=0/1') `
        'Comparison must fail when one requested subject×dimension pair is absent.'

    Write-Fixture 'COMPARISON' (Answer @(
        (Section 'Redis · PERSISTENCE' $zhSentence 'SOLUTION'),
        (Section 'Redis · PERSISTENCE' $zhSentence 'SOLUTION'),
        (Section 'Memcached · PERSISTENCE' $zhSentence 'SOLUTION'),
        (Section 'Memcached · THREAD_MODEL' $zhSentence 'SOLUTION')
    ))
    $duplicateComparisonPair = Invoke-Checker
    Assert-True ($duplicateComparisonPair.ExitCode -eq 1 -and
            $duplicateComparisonPair.Output -match
            'scenario=COMPARISON.*structure=0/1') `
        'Comparison must fail when a pair is duplicated in place of another pair.'

    Write-Fixture 'COMPARISON' (Answer @(
        (Section 'Redis · PERSISTENCE' $zhSentence 'SOLUTION'),
        (Section 'Redis · THREAD_MODEL' $zhSentence 'SOLUTION'),
        (Section 'Memcached · PERSISTENCE' $zhSentence 'SOLUTION'),
        (Section 'Memcached · THREAD_MODEL' $zhSentence 'SOLUTION')
    ))

    Write-Fixture 'CONCISE' (Answer @(
        (Section $conceptTitle ($zhSentence + $zhSentence)),
        (Section $mechanismTitle $zhSentence)
    ))
    $wrongDepth = Invoke-Checker
    Assert-True ($wrongDepth.ExitCode -eq 1) `
        'a three-sentence CONCISE answer must fail the depth gate.'
    Assert-True ($wrongDepth.Output -match `
        'scenario=CONCISE.*bucket=0/1.*observed=OUTSIDE:1') `
        'wrong sentence count must be reported as an outside depth bucket.'

    Write-Fixture 'CONCISE' (Answer @(
        (Section $mechanismTitle $zhSentence),
        (Section $conceptTitle $zhSentence)
    ))
    $wrongStructure = Invoke-Checker
    Assert-True ($wrongStructure.ExitCode -eq 1) `
        'reversed required sections must fail the structure gate.'
    Assert-True ($wrongStructure.Output -match `
        'scenario=CONCISE.*structure=0/1') `
        'wrong section order must be reported as a structure failure.'

    $englishOne = 'This explanation works (usually).'
    $englishTwo = 'This is plain text; not code.'
    Write-Fixture 'CONCISE' (Answer @(
        (Section $conceptTitle $englishOne),
        (Section $mechanismTitle $englishTwo)
    ))

    $baseline = Invoke-Checker -Baseline
    Assert-True ($baseline.ExitCode -eq 0) `
        "baseline mode must collect rather than assert: $($baseline.Output)"
    Assert-True ($baseline.Output -match 'language=0/1') `
        'baseline mode must count the English violation.'
    Assert-True ($baseline.Output -match 'GENERAL_QUALITY_RESULT status=FAIL') `
        'baseline mode must report failure without stopping collection.'
    Assert-True ($baseline.Output -notmatch [regex]::Escape($englishOne)) `
        'baseline output leaked the first English response.'
    Assert-True ($baseline.Output -notmatch [regex]::Escape($englishTwo)) `
        'baseline output leaked the second English response.'

    $failing = Invoke-Checker
    Assert-True ($failing.ExitCode -eq 1) `
        'assertion mode must fail on an English response.'
    Assert-True ($failing.Output -match 'GENERAL_QUALITY_GATE_FAILED') `
        "failure output must contain the stable code: $($failing.Output)"
    Assert-True ($failing.Output -match 'scenario=CONCISE.*language=0/1') `
        'failure output must retain privacy-safe aggregate diagnostics.'
    Assert-True ($failing.Output -notmatch 'This explanation|plain text') `
        'failure output leaked the response body.'

    Write-Output 'assert-live-general-answer-quality tests passed'
}
finally {
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $snapshots[$name], 'Process')
    }
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        if (-not ([System.IO.Path]::GetFileName($resolved)).StartsWith('general-answer-quality-')) {
            throw "Refusing to remove unverified fixture path: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
