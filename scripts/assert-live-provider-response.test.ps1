$ErrorActionPreference = 'Stop'

$checker = Join-Path $PSScriptRoot 'assert-live-provider-response.ps1'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('assert-live-provider-response-' + [guid]::NewGuid().ToString('N'))
$responsePath = Join-Path $fixtureRoot 'response.json'
$expectedContentVersion = '2026-07-28.1'
$keySentinel = 'key-sentinel-' + [guid]::NewGuid().ToString('N')
$contentSentinel = 'response-content-sentinel-' + [guid]::NewGuid().ToString('N')
$environmentNames = @(
    'PORTFOLIO_MODEL_ENABLED',
    'PORTFOLIO_MODEL_DATA_POLICY_APPROVED',
    'PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED',
    'PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED',
    'PORTFOLIO_MODEL_PROVIDER',
    'PORTFOLIO_AGENT_DEEPSEEK_API_KEY',
    'PORTFOLIO_AGENT_GLM_API_KEY'
)

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
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

function Set-ProcessEnvironmentVariable(
    [string]$Name,
    [AllowNull()][string]$Value
) {
    [System.Environment]::SetEnvironmentVariable(
        $Name,
        $Value,
        [System.EnvironmentVariableTarget]::Process
    )
}

function Restore-EnvironmentVariable([string]$Name, [hashtable]$Snapshot) {
    $value = if ($Snapshot.Exists) { $Snapshot.Value } else { $null }
    Set-ProcessEnvironmentVariable $Name $value
}

function Set-ApprovedEnvironment([string]$Provider) {
    $approvedEnvironment = @{
        PORTFOLIO_MODEL_ENABLED = 'TrUe'
        PORTFOLIO_MODEL_DATA_POLICY_APPROVED = 'TRUE'
        PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED = 'true'
        PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED = 'tRuE'
        PORTFOLIO_MODEL_PROVIDER = $Provider
        PORTFOLIO_AGENT_DEEPSEEK_API_KEY = $keySentinel
        PORTFOLIO_AGENT_GLM_API_KEY = $keySentinel
    }
    foreach ($entry in $approvedEnvironment.GetEnumerator()) {
        Set-ProcessEnvironmentVariable $entry.Key $entry.Value
    }
}

function Write-ResponseFixture(
    [string]$ContentVersion = $expectedContentVersion,
    [bool]$Degraded = $false,
    [string]$Resolution = 'ANSWERED',
    [AllowNull()][string]$AnswerScope = 'GENERAL',
    [AllowNull()][string]$ConstructionMode = 'GENERAL_MODEL',
    [AllowNull()][string]$IntentSource = 'RULE',
    [AllowNull()][string]$EvidenceState = 'NOT_REQUIRED',
    [object[]]$Blocks = @([pscustomobject]@{ content = $contentSentinel })
) {
    $response = [pscustomobject]@{
        contentVersion = $ContentVersion
        degraded = $Degraded
        resolution = $Resolution
        answerScope = $AnswerScope
        constructionMode = $ConstructionMode
        intentSource = $IntentSource
        evidenceState = $EvidenceState
        blocks = $Blocks
    }
    [System.IO.File]::WriteAllText(
        $responsePath,
        ($response | ConvertTo-Json -Depth 8 -Compress),
        [System.Text.UTF8Encoding]::new($false)
    )
}

function Invoke-Checker {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker `
            -ResponsePath $responsePath -ExpectedContentVersion $expectedContentVersion 2>&1 | Out-String)
        return @{
            ExitCode = $LASTEXITCODE
            Output = $output
        }
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Assert-NoSensitiveOutput([hashtable]$Result, [string]$CaseName) {
    Assert-True ($Result.Output -notmatch [regex]::Escape($keySentinel)) `
        "$CaseName leaked the key sentinel."
    Assert-True ($Result.Output -notmatch [regex]::Escape($contentSentinel)) `
        "$CaseName leaked the response-content sentinel."
}

$pathSnapshot = Get-EnvironmentSnapshot 'Path'
Assert-True ($pathSnapshot.ContainsKey('Exists') -and $pathSnapshot.ContainsKey('Value')) `
    'Process Path snapshot must tolerate case-duplicate host environment keys.'

$environmentProbeName = 'PORTFOLIO_ENVIRONMENT_PROBE_' + [guid]::NewGuid().ToString('N')
Set-ProcessEnvironmentVariable $environmentProbeName $null
$unsetProbe = Get-EnvironmentSnapshot $environmentProbeName
Assert-True (-not $unsetProbe.Exists -and $null -eq $unsetProbe.Value) `
    'An unset Process environment variable must remain distinguishable from a value.'
Set-ProcessEnvironmentVariable $environmentProbeName 'probe-value'
$setProbe = Get-EnvironmentSnapshot $environmentProbeName
Assert-True ($setProbe.Exists -and $setProbe.Value -eq 'probe-value') `
    'A set Process environment variable must retain its exact value.'
Set-ProcessEnvironmentVariable $environmentProbeName $null
Restore-EnvironmentVariable $environmentProbeName $setProbe
Assert-True (
    [System.Environment]::GetEnvironmentVariable(
        $environmentProbeName,
        [System.EnvironmentVariableTarget]::Process
    ) -eq 'probe-value'
) 'Restore must reinstate a previously set Process environment variable.'
Restore-EnvironmentVariable $environmentProbeName $unsetProbe
Assert-True (
    $null -eq [System.Environment]::GetEnvironmentVariable(
        $environmentProbeName,
        [System.EnvironmentVariableTarget]::Process
    )
) 'Restore must remove a previously unset Process environment variable.'

$environment = @{}
foreach ($name in $environmentNames) {
    $environment[$name] = Get-EnvironmentSnapshot $name
}

try {
    Assert-True (Test-Path -LiteralPath $checker -PathType Leaf) `
        "Live Provider checker does not exist: $checker"
    $checkerSource = [System.IO.File]::ReadAllText($checker)
    Assert-True ($checkerSource -notmatch '(?i)(Get-Item|Set-Item|Remove-Item)[^\r\n]*Env:') `
        'Live Provider checker must not use Env provider item access.'
    Assert-True ($checkerSource -notmatch '\$env:') `
        'Live Provider checker must not use the Env provider variable syntax.'
    Assert-True ($checkerSource -match 'GetEnvironmentVariable') `
        'Live Provider checker must read Process environment through System.Environment.'
    New-Item -ItemType Directory -Force -Path $fixtureRoot | Out-Null

    foreach ($provider in @('DEEPSEEK_V4_FLASH', 'GLM_4_7')) {
        Set-ApprovedEnvironment $provider
        Write-ResponseFixture
        $result = Invoke-Checker
        Assert-True ($result.ExitCode -eq 0) "Approved $provider fixture must pass. Output: $($result.Output)"
        Assert-True ($result.Output -match [regex]::Escape("Live Provider verification passed: provider=$provider;")) `
            "Approved $provider output must name only the selected Provider."
        Assert-True ($result.Output -match [regex]::Escape("contentVersion=$expectedContentVersion")) `
            'Approved output must name the content version.'
        Assert-True ($result.Output -match 'resolution=ANSWERED') `
            'Approved output must name the resolution.'
        Assert-True ($result.Output -match 'constructionMode=GENERAL_MODEL') `
            'Approved output must prove general model construction.'
        Assert-True ($result.Output -match 'blocks=1') `
            'Approved output must name the block count.'
        Assert-True ($result.Output -match (
                '^Live Provider verification passed: provider=' + [regex]::Escape($provider) +
                '; contentVersion=' + [regex]::Escape($expectedContentVersion) +
                '; answerScope=GENERAL; intentSource=RULE' +
                '; constructionMode=GENERAL_MODEL; evidenceState=NOT_REQUIRED' +
                '; resolution=ANSWERED; blocks=1\.\s*$'
            )) 'Approved output must contain only the permitted assertion summary.'
        Assert-NoSensitiveOutput $result "approved $provider"
    }

    foreach ($approvalName in $environmentNames[0..3]) {
        foreach ($invalidValue in @($null, 'false')) {
            Set-ApprovedEnvironment 'DEEPSEEK_V4_FLASH'
            Set-ProcessEnvironmentVariable $approvalName $invalidValue
            Write-ResponseFixture
            $result = Invoke-Checker
            Assert-True ($result.ExitCode -ne 0) "$approvalName=$invalidValue must fail."
            Assert-True ($result.Output -match 'LIVE_PROVIDER_CONFIG_INVALID') "$approvalName=$invalidValue must report LIVE_PROVIDER_CONFIG_INVALID. Output: $($result.Output)"
            Assert-NoSensitiveOutput $result "$approvalName=$invalidValue"
        }
    }

    foreach ($providerKey in @(
        @{ Provider = 'DEEPSEEK_V4_FLASH'; KeyName = 'PORTFOLIO_AGENT_DEEPSEEK_API_KEY' },
        @{ Provider = 'GLM_4_7'; KeyName = 'PORTFOLIO_AGENT_GLM_API_KEY' }
    )) {
        foreach ($keyValue in @($null, '   ')) {
            Set-ApprovedEnvironment $providerKey.Provider
            Set-ProcessEnvironmentVariable $providerKey.KeyName $keyValue
            Write-ResponseFixture
            $result = Invoke-Checker
            Assert-True ($result.ExitCode -ne 0) `
                "Missing selected Provider key for $($providerKey.Provider) must fail."
            Assert-True ($result.Output -match 'LIVE_PROVIDER_CONFIG_INVALID') `
                "Missing selected Provider key for $($providerKey.Provider) must report LIVE_PROVIDER_CONFIG_INVALID. Output: $($result.Output)"
            Assert-NoSensitiveOutput $result "missing key for $($providerKey.Provider)"
        }
    }

    Set-ApprovedEnvironment 'UNSUPPORTED_PROVIDER'
    Write-ResponseFixture
    $result = Invoke-Checker
    Assert-True ($result.ExitCode -ne 0) 'Unsupported Provider must fail.'
    Assert-True ($result.Output -match 'LIVE_PROVIDER_CONFIG_INVALID') "Unsupported Provider must report LIVE_PROVIDER_CONFIG_INVALID. Output: $($result.Output)"
    Assert-NoSensitiveOutput $result 'unsupported Provider'

    Set-ApprovedEnvironment 'DEEPSEEK_V4_FLASH'
    Write-ResponseFixture -ContentVersion 'wrong-version'
    $result = Invoke-Checker
    Assert-True ($result.ExitCode -ne 0) 'Wrong content version must fail.'
    Assert-True ($result.Output -match 'LIVE_PROVIDER_CONTENT_VERSION_MISMATCH') "Wrong content version must report LIVE_PROVIDER_CONTENT_VERSION_MISMATCH. Output: $($result.Output)"
    Assert-NoSensitiveOutput $result 'wrong content version'

    Set-ApprovedEnvironment 'DEEPSEEK_V4_FLASH'
    Write-ResponseFixture -Degraded $true
    $result = Invoke-Checker
    Assert-True ($result.ExitCode -ne 0) 'degraded=true must fail.'
    Assert-True ($result.Output -match 'LIVE_PROVIDER_REPORTED_DEGRADED') "degraded=true must report LIVE_PROVIDER_REPORTED_DEGRADED. Output: $($result.Output)"
    Assert-NoSensitiveOutput $result 'degraded response'

    foreach ($mode in @($null, '', 'TEMPLATE', 'EVIDENCE_COMPOSITION')) {
        Set-ApprovedEnvironment 'DEEPSEEK_V4_FLASH'
        Write-ResponseFixture -ConstructionMode $mode
        $result = Invoke-Checker
        Assert-True ($result.ExitCode -ne 0) "constructionMode=$mode must fail."
        Assert-True ($result.Output -match 'LIVE_PROVIDER_CONSTRUCTION_INVALID') "constructionMode=$mode must report LIVE_PROVIDER_CONSTRUCTION_INVALID. Output: $($result.Output)"
        Assert-NoSensitiveOutput $result "constructionMode=$mode"
    }

    foreach ($source in @($null, '', 'MODEL', 'PRESET', 'REFERENCE', 'GLOBAL')) {
        Set-ApprovedEnvironment 'DEEPSEEK_V4_FLASH'
        Write-ResponseFixture -IntentSource $source
        $result = Invoke-Checker
        Assert-True ($result.ExitCode -ne 0) "intentSource=$source must fail."
        Assert-True ($result.Output -match 'LIVE_PROVIDER_ROUTE_BYPASSED') "intentSource=$source must report LIVE_PROVIDER_ROUTE_BYPASSED. Output: $($result.Output)"
        Assert-NoSensitiveOutput $result "intentSource=$source"
    }

    foreach ($state in @($null, '', 'VERIFIED', 'INSUFFICIENT')) {
        Set-ApprovedEnvironment 'DEEPSEEK_V4_FLASH'
        Write-ResponseFixture -EvidenceState $state
        $result = Invoke-Checker
        Assert-True ($result.ExitCode -ne 0) "evidenceState=$state must fail."
        Assert-True ($result.Output -match 'LIVE_PROVIDER_EVIDENCE_UNVERIFIED') "evidenceState=$state must report LIVE_PROVIDER_EVIDENCE_UNVERIFIED. Output: $($result.Output)"
        Assert-NoSensitiveOutput $result "evidenceState=$state"
    }

    foreach ($scope in @($null, '', 'PORTFOLIO', 'MIXED')) {
        Set-ApprovedEnvironment 'DEEPSEEK_V4_FLASH'
        Write-ResponseFixture -AnswerScope $scope
        $result = Invoke-Checker
        Assert-True ($result.ExitCode -ne 0) "answerScope=$scope must fail."
        Assert-True ($result.Output -match 'LIVE_PROVIDER_ROUTE_BYPASSED') "answerScope=$scope must report LIVE_PROVIDER_ROUTE_BYPASSED. Output: $($result.Output)"
        Assert-NoSensitiveOutput $result "answerScope=$scope"
    }

    Set-ApprovedEnvironment 'DEEPSEEK_V4_FLASH'
    Write-ResponseFixture -Resolution 'NEEDS_CLARIFICATION'
    $result = Invoke-Checker
    Assert-True ($result.ExitCode -ne 0) 'Non-ANSWERED resolution must fail.'
    Assert-True ($result.Output -match 'LIVE_PROVIDER_RESOLUTION_INVALID') "Non-ANSWERED resolution must report LIVE_PROVIDER_RESOLUTION_INVALID. Output: $($result.Output)"
    Assert-NoSensitiveOutput $result 'non-ANSWERED response'

    Set-ApprovedEnvironment 'DEEPSEEK_V4_FLASH'
    Write-ResponseFixture -Blocks @()
    $result = Invoke-Checker
    Assert-True ($result.ExitCode -ne 0) 'Empty blocks must fail.'
    Assert-True ($result.Output -match 'LIVE_PROVIDER_BLOCKS_MISSING') "Empty blocks must report LIVE_PROVIDER_BLOCKS_MISSING. Output: $($result.Output)"
    Assert-NoSensitiveOutput $result 'empty blocks response'

    Set-ApprovedEnvironment 'DEEPSEEK_V4_FLASH'
    Remove-Item -LiteralPath $responsePath -Force
    $result = Invoke-Checker
    Assert-True ($result.ExitCode -ne 0) 'Missing response file must fail.'
    Assert-True ($result.Output -match 'LIVE_PROVIDER_RESPONSE_UNREADABLE') "Missing response file must report LIVE_PROVIDER_RESPONSE_UNREADABLE. Output: $($result.Output)"
    Assert-NoSensitiveOutput $result 'missing response file'

    Set-ApprovedEnvironment 'DEEPSEEK_V4_FLASH'
    [System.IO.File]::WriteAllText(
        $responsePath,
        '{not valid json',
        [System.Text.UTF8Encoding]::new($false)
    )
    $result = Invoke-Checker
    Assert-True ($result.ExitCode -ne 0) 'Invalid response JSON must fail.'
    Assert-True ($result.Output -match 'LIVE_PROVIDER_RESPONSE_UNREADABLE') "Invalid response JSON must report LIVE_PROVIDER_RESPONSE_UNREADABLE. Output: $($result.Output)"
    Assert-NoSensitiveOutput $result 'invalid response JSON'

    Write-Output 'assert-live-provider-response tests passed'
}
finally {
    foreach ($name in $environmentNames) {
        Restore-EnvironmentVariable $name $environment[$name]
    }
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolvedFixtureRoot = (Resolve-Path -LiteralPath $fixtureRoot).Path
        if (-not ([System.IO.Path]::GetFileName($resolvedFixtureRoot)).StartsWith(
            'assert-live-provider-response-'
        )) {
            throw "Refusing to remove unverified fixture path: $resolvedFixtureRoot"
        }
        Remove-Item -LiteralPath $resolvedFixtureRoot -Recurse -Force
    }
}
