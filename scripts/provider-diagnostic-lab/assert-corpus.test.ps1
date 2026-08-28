$ErrorActionPreference = 'Stop'

$corpusPath = Join-Path $PSScriptRoot 'qwen-general-explanation-corpus.v1.json'
$schemaPath = Join-Path $PSScriptRoot 'qwen-general-explanation-corpus.schema.json'

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Assert-ExactKeys(
    [object]$Value,
    [string[]]$Expected,
    [string]$Context
) {
    $actual = @($Value.PSObject.Properties.Name | Sort-Object)
    $expectedSorted = @($Expected | Sort-Object)
    Assert-True (($actual -join '|') -ceq ($expectedSorted -join '|')) `
        "$Context fields must be exact."
}

Assert-True (Test-Path -LiteralPath $corpusPath -PathType Leaf) `
    'The frozen Qwen General corpus is missing.'
Assert-True (Test-Path -LiteralPath $schemaPath -PathType Leaf) `
    'The frozen Qwen General corpus schema is missing.'

$corpusBytes = [System.IO.File]::ReadAllBytes($corpusPath)
$corpus = [System.Text.Encoding]::UTF8.GetString($corpusBytes) |
    ConvertFrom-Json
$schema = Get-Content -LiteralPath $schemaPath -Raw -Encoding UTF8 |
    ConvertFrom-Json

Assert-ExactKeys $corpus @('schemaVersion', 'cases') 'Corpus root'
Assert-True ($corpus.schemaVersion -ceq 'qwen-general-explanation-corpus.v1') `
    'Corpus version must be frozen to v1.'
Assert-True (@($corpus.cases).Count -eq 100) `
    'Certification corpus must contain exactly 100 cases.'

$categories = @(
    'JAVA_SPRING',
    'DATABASE_TRANSACTION',
    'REDIS_CACHE',
    'NETWORK',
    'DISTRIBUTED_SYSTEM',
    'FRONTEND',
    'DEVOPS_CONTAINER',
    'AI_LLM_AGENT',
    'ARCHITECTURE_PATTERN',
    'SECURITY_PERFORMANCE'
)
$seenCaseIds = @{}
$seenTopics = @{}
$promptCount = 0
function ConvertFrom-CodePoints([int[]]$Values) {
    return -join @($Values | ForEach-Object { [char]$_ })
}

$forbiddenChinese = @(
    @(0x771F, 0x5B9E, 0x7528, 0x6237),
    @(0x79C1, 0x6709, 0x9879, 0x76EE),
    @(0x59D3, 0x540D),
    @(0x8D26, 0x53F7),
    @(0x8EAB, 0x4EFD, 0x8BC1),
    @(0x94F6, 0x884C, 0x5361),
    @(0x8D44, 0x4EA7),
    @(0x6301, 0x4ED3),
    @(0x6570, 0x636E, 0x5E93, 0x5BC6, 0x7801),
    @(0x5185, 0x90E8, 0x4EE4, 0x724C),
    @(0x60A3, 0x8005),
    @(0x8BCA, 0x65AD, 0x610F, 0x89C1),
    @(0x6CD5, 0x5F8B, 0x610F, 0x89C1),
    @(0x6295, 0x8D44, 0x5EFA, 0x8BAE),
    @(0x5F53, 0x524D, 0x80A1, 0x4EF7),
    @(0x5B9E, 0x65F6, 0x884C, 0x60C5),
    @(0x4ECA, 0x5929, 0x7684, 0x65B0, 0x95FB)
) | ForEach-Object {
    [regex]::Escape((ConvertFrom-CodePoints $_))
}
$forbiddenAscii = @(
    'cookie', 'api[ _-]?key', 'authorization', 'bearer\s+',
    '\b(?:access[ _-]?)?token\b',
    'example\.com', '@[a-z0-9.-]+\.[a-z]{2,}'
)
$forbidden = '(?i)(' + (@($forbiddenChinese + $forbiddenAscii) -join '|') + ')'

function Test-ContainsForbiddenCorpusText([object]$Case) {
    $combined = @(
        [string]$Case.topic,
        [string]$Case.prompts.CONCISE,
        [string]$Case.prompts.STANDARD,
        [string]$Case.prompts.DETAILED
    ) -join "`n"
    return $combined -match $forbidden
}

$topicSensitiveFixture = [pscustomobject]@{
    topic = (-join @(
        [char]0x771F, [char]0x5B9E, [char]0x7528, [char]0x6237))
    prompts = [pscustomobject]@{
        CONCISE = 'Explain dependency injection briefly.'
        STANDARD = 'Explain dependency injection and its mechanism.'
        DETAILED = 'Explain dependency injection in detail.'
    }
}
Assert-True (Test-ContainsForbiddenCorpusText $topicSensitiveFixture) `
    'Forbidden scanning must reject a sensitive topic even when prompts are ordinary.'

$tokenSensitiveFixture = [pscustomobject]@{
    topic = 'OAuth credential lifecycle'
    prompts = [pscustomobject]@{
        CONCISE = 'Explain an OAuth access token briefly.'
        STANDARD = 'Explain the OAuth credential lifecycle.'
        DETAILED = 'Explain OAuth credentials in detail.'
    }
}
Assert-True (Test-ContainsForbiddenCorpusText $tokenSensitiveFixture) `
    'Forbidden scanning must reject an OAuth access token prompt.'

$assetSensitiveFixture = [pscustomobject]@{
    topic = ConvertFrom-CodePoints @(
        0x4E2A, 0x4EBA, 0x8D44, 0x4EA7)
    prompts = [pscustomobject]@{
        CONCISE = 'Explain the topic briefly.'
        STANDARD = 'Explain the topic and its mechanism.'
        DETAILED = 'Explain the topic in detail.'
    }
}
Assert-True (Test-ContainsForbiddenCorpusText $assetSensitiveFixture) `
    'Forbidden scanning must reject a personal-assets topic.'

foreach ($case in @($corpus.cases)) {
    Assert-ExactKeys $case @('caseId', 'category', 'topic', 'prompts') `
        "Case $($case.caseId)"
    Assert-True ([string]$case.caseId -match '^[a-z]+(?:-[a-z]+)*-[0-9]{3}$') `
        'Every caseId must be stable, opaque and lowercase.'
    Assert-True (-not $seenCaseIds.ContainsKey([string]$case.caseId)) `
        'caseId values must be unique.'
    $seenCaseIds[[string]$case.caseId] = $true
    Assert-True ([string]$case.category -cin $categories) `
        'Every category must belong to the approved closed set.'
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$case.topic)) `
        'Every topic must be non-empty.'
    Assert-True (-not $seenTopics.ContainsKey([string]$case.topic)) `
        'Topics must be unique across the frozen corpus.'
    $seenTopics[[string]$case.topic] = $true
    Assert-ExactKeys $case.prompts @('CONCISE', 'STANDARD', 'DETAILED') `
        "Prompts for $($case.caseId)"
    Assert-True (-not (Test-ContainsForbiddenCorpusText $case)) `
        'The frozen corpus contains a forbidden sensitive or unstable pattern.'
    foreach ($depth in @('CONCISE', 'STANDARD', 'DETAILED')) {
        $prompt = [string]$case.prompts.$depth
        Assert-True (-not [string]::IsNullOrWhiteSpace($prompt)) `
            'Every depth prompt must be non-empty.'
        Assert-True ($prompt.Length -le 120) `
            'Synthetic prompts must remain bounded.'
        $promptCount++
    }
}

foreach ($category in $categories) {
    $count = @($corpus.cases | Where-Object {
        $_.category -ceq $category
    }).Count
    Assert-True ($count -eq 10) `
        "Category $category must contain exactly 10 cases."
}
Assert-True ($promptCount -eq 300) `
    'Certification corpus must contain exactly 300 depth prompts.'

Assert-True ($schema.'$schema' -ceq 'https://json-schema.org/draft/2020-12/schema') `
    'Corpus schema must use JSON Schema 2020-12.'
Assert-True ($schema.additionalProperties -eq $false) `
    'Corpus schema root must reject unknown fields.'
Assert-True ($schema.properties.cases.minItems -eq 100 -and
        $schema.properties.cases.maxItems -eq 100) `
    'Corpus schema must freeze the case count at 100.'
Assert-True (@($schema.properties.cases.items.properties.category.enum).Count -eq 10) `
    'Corpus schema must freeze all ten categories.'
Assert-True ($schema.properties.cases.items.additionalProperties -eq $false) `
    'Corpus case objects must reject unknown fields.'
Assert-True ($schema.properties.cases.items.properties.prompts.additionalProperties -eq $false) `
    'Prompt maps must reject unknown depths.'

$sha256 = [System.Security.Cryptography.SHA256]::Create()
try {
    $hash = ([System.BitConverter]::ToString(
        $sha256.ComputeHash($corpusBytes))).Replace('-', '').ToLowerInvariant()
}
finally {
    $sha256.Dispose()
}

Write-Output (('CORPUS_ASSERT_PASS cases=100 prompts=300 sha256={0}') -f $hash)
