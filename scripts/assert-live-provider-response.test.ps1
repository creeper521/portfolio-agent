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
    $item = Get-Item -LiteralPath "Env:$Name" -ErrorAction SilentlyContinue
    return @{
        Exists = $null -ne $item
        Value = if ($null -ne $item) { $item.Value } else { $null }
    }
}

function Restore-EnvironmentVariable([string]$Name, [hashtable]$Snapshot) {
    if ($Snapshot.Exists) {
        Set-Item -LiteralPath "Env:$Name" -Value $Snapshot.Value
    }
    else {
        Remove-Item -LiteralPath "Env:$Name" -ErrorAction SilentlyContinue
    }
}

function Set-ApprovedEnvironment([string]$Provider) {
    $env:PORTFOLIO_MODEL_ENABLED = 'TrUe'
    $env:PORTFOLIO_MODEL_DATA_POLICY_APPROVED = 'TRUE'
    $env:PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED = 'true'
    $env:PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED = 'tRuE'
    $env:PORTFOLIO_MODEL_PROVIDER = $Provider
    $env:PORTFOLIO_AGENT_DEEPSEEK_API_KEY = $keySentinel
    $env:PORTFOLIO_AGENT_GLM_API_KEY = $keySentinel
}

function Write-ResponseFixture(
    [string]$ContentVersion = $expectedContentVersion,
    [bool]$Degraded = $false,
    [string]$Resolution = 'ANSWERED',
    [object[]]$Blocks = @([pscustomobject]@{ content = $contentSentinel })
) {
    $response = [pscustomobject]@{
        contentVersion = $ContentVersion
        degraded = $Degraded
        resolution = $Resolution
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

$environment = @{}
foreach ($name in $environmentNames) {
    $environment[$name] = Get-EnvironmentSnapshot $name
}

try {
    Assert-True (Test-Path -LiteralPath $checker -PathType Leaf) `
        "Live Provider checker does not exist: $checker"
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
        Assert-True ($result.Output -match 'blocks=1') `
            'Approved output must name the block count.'
        Assert-True ($result.Output -match (
                '^Live Provider verification passed: provider=' + [regex]::Escape($provider) +
                '; contentVersion=' + [regex]::Escape($expectedContentVersion) +
                '; resolution=ANSWERED; blocks=1\.\s*$'
            )) 'Approved output must contain only the permitted assertion summary.'
        Assert-NoSensitiveOutput $result "approved $provider"
    }

    foreach ($approvalName in $environmentNames[0..3]) {
        foreach ($invalidValue in @($null, 'false')) {
            Set-ApprovedEnvironment 'DEEPSEEK_V4_FLASH'
            if ($null -eq $invalidValue) {
                Remove-Item -LiteralPath "Env:$approvalName" -ErrorAction SilentlyContinue
            }
            else {
                Set-Item -LiteralPath "Env:$approvalName" -Value $invalidValue
            }
            Write-ResponseFixture
            $result = Invoke-Checker
            Assert-True ($result.ExitCode -ne 0) "$approvalName=$invalidValue must fail."
            Assert-NoSensitiveOutput $result "$approvalName=$invalidValue"
        }
    }

    foreach ($providerKey in @(
        @{ Provider = 'DEEPSEEK_V4_FLASH'; KeyName = 'PORTFOLIO_AGENT_DEEPSEEK_API_KEY' },
        @{ Provider = 'GLM_4_7'; KeyName = 'PORTFOLIO_AGENT_GLM_API_KEY' }
    )) {
        foreach ($keyValue in @($null, '   ')) {
            Set-ApprovedEnvironment $providerKey.Provider
            if ($null -eq $keyValue) {
                Remove-Item -LiteralPath "Env:$($providerKey.KeyName)" -ErrorAction SilentlyContinue
            }
            else {
                Set-Item -LiteralPath "Env:$($providerKey.KeyName)" -Value $keyValue
            }
            Write-ResponseFixture
            $result = Invoke-Checker
            Assert-True ($result.ExitCode -ne 0) `
                "Missing selected Provider key for $($providerKey.Provider) must fail."
            Assert-NoSensitiveOutput $result "missing key for $($providerKey.Provider)"
        }
    }

    Set-ApprovedEnvironment 'UNSUPPORTED_PROVIDER'
    Write-ResponseFixture
    $result = Invoke-Checker
    Assert-True ($result.ExitCode -ne 0) 'Unsupported Provider must fail.'
    Assert-NoSensitiveOutput $result 'unsupported Provider'

    Set-ApprovedEnvironment 'DEEPSEEK_V4_FLASH'
    Write-ResponseFixture -ContentVersion 'wrong-version'
    $result = Invoke-Checker
    Assert-True ($result.ExitCode -ne 0) 'Wrong content version must fail.'
    Assert-NoSensitiveOutput $result 'wrong content version'

    Set-ApprovedEnvironment 'DEEPSEEK_V4_FLASH'
    Write-ResponseFixture -Degraded $true
    $result = Invoke-Checker
    Assert-True ($result.ExitCode -ne 0) 'degraded=true must fail.'
    Assert-NoSensitiveOutput $result 'degraded response'

    Set-ApprovedEnvironment 'DEEPSEEK_V4_FLASH'
    Write-ResponseFixture -Resolution 'NEEDS_CLARIFICATION'
    $result = Invoke-Checker
    Assert-True ($result.ExitCode -ne 0) 'Non-ANSWERED resolution must fail.'
    Assert-NoSensitiveOutput $result 'non-ANSWERED response'

    Set-ApprovedEnvironment 'DEEPSEEK_V4_FLASH'
    Write-ResponseFixture -Blocks @()
    $result = Invoke-Checker
    Assert-True ($result.ExitCode -ne 0) 'Empty blocks must fail.'
    Assert-NoSensitiveOutput $result 'empty blocks response'

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
