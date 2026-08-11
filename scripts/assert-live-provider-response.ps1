param(
    [Parameter(Mandatory = $true)]
    [string]$ResponsePath,
    [Parameter(Mandatory = $true)]
    [string]$ExpectedContentVersion
)

$ErrorActionPreference = 'Stop'

$script:allowedFailureCodes = @(
    'LIVE_PROVIDER_CONFIG_INVALID',
    'LIVE_PROVIDER_RESPONSE_UNREADABLE',
    'LIVE_PROVIDER_CONTENT_VERSION_MISMATCH',
    'LIVE_PROVIDER_REPORTED_DEGRADED',
    'LIVE_PROVIDER_ROUTE_BYPASSED',
    'LIVE_PROVIDER_CONSTRUCTION_INVALID',
    'LIVE_PROVIDER_EVIDENCE_UNVERIFIED',
    'LIVE_PROVIDER_RESOLUTION_INVALID',
    'LIVE_PROVIDER_BLOCKS_MISSING'
)

function Stop-Assertion([string]$Code) {
    if ($Code -notin $script:allowedFailureCodes) {
        throw 'LIVE_PROVIDER_RESPONSE_UNREADABLE'
    }
    throw $Code
}

function Test-ApprovedFlag([string]$Value) {
    return [string]::Equals($Value, 'true', [System.StringComparison]::OrdinalIgnoreCase)
}

function Test-ExactProvider([string]$Value, [string]$Expected) {
    return [string]::Equals($Value, $Expected, [System.StringComparison]::Ordinal)
}

function Get-ProcessEnvironmentValue([string]$Name) {
    return [System.Environment]::GetEnvironmentVariable(
        $Name,
        [System.EnvironmentVariableTarget]::Process
    )
}

try {
    foreach ($approvalName in @(
        'PORTFOLIO_MODEL_ENABLED',
        'PORTFOLIO_MODEL_DATA_POLICY_APPROVED',
        'PORTFOLIO_CONVERSATIONAL_AGENT_ENABLED',
        'PORTFOLIO_VISITOR_MODEL_DATA_POLICY_APPROVED'
    )) {
        if (-not (Test-ApprovedFlag (Get-ProcessEnvironmentValue $approvalName))) {
            Stop-Assertion 'LIVE_PROVIDER_CONFIG_INVALID'
        }
    }

    $provider = Get-ProcessEnvironmentValue 'PORTFOLIO_MODEL_PROVIDER'
    if (Test-ExactProvider $provider 'DEEPSEEK_V4_FLASH') {
        $selectedKey = Get-ProcessEnvironmentValue 'PORTFOLIO_AGENT_DEEPSEEK_API_KEY'
    }
    elseif (Test-ExactProvider $provider 'GLM_4_7') {
        $selectedKey = Get-ProcessEnvironmentValue 'PORTFOLIO_AGENT_GLM_API_KEY'
    }
    else {
        Stop-Assertion 'LIVE_PROVIDER_CONFIG_INVALID'
    }
    if ([string]::IsNullOrWhiteSpace($selectedKey)) {
        Stop-Assertion 'LIVE_PROVIDER_CONFIG_INVALID'
    }

    if (-not (Test-Path -LiteralPath $ResponsePath -PathType Leaf)) {
        Stop-Assertion 'LIVE_PROVIDER_RESPONSE_UNREADABLE'
    }
    try {
        $response = [System.IO.File]::ReadAllText(
            $ResponsePath,
            [System.Text.UTF8Encoding]::new($false)
        ) | ConvertFrom-Json
    }
    catch {
        Stop-Assertion 'LIVE_PROVIDER_RESPONSE_UNREADABLE'
    }

    if ($response.contentVersion -cne $ExpectedContentVersion) {
        Stop-Assertion 'LIVE_PROVIDER_CONTENT_VERSION_MISMATCH'
    }
    if ($response.degraded -isnot [bool] -or $response.degraded -ne $false) {
        Stop-Assertion 'LIVE_PROVIDER_REPORTED_DEGRADED'
    }
    if ($response.answerScope -cne 'GENERAL' -or $response.intentSource -cne 'RULE') {
        Stop-Assertion 'LIVE_PROVIDER_ROUTE_BYPASSED'
    }
    if ($response.constructionMode -cne 'GENERAL_MODEL') {
        Stop-Assertion 'LIVE_PROVIDER_CONSTRUCTION_INVALID'
    }
    if ($response.evidenceState -cne 'NOT_REQUIRED') {
        Stop-Assertion 'LIVE_PROVIDER_EVIDENCE_UNVERIFIED'
    }
    if ($response.resolution -cne 'ANSWERED') {
        Stop-Assertion 'LIVE_PROVIDER_RESOLUTION_INVALID'
    }
    if ($response.blocks -isnot [System.Array] -or $response.blocks.Count -lt 1) {
        Stop-Assertion 'LIVE_PROVIDER_BLOCKS_MISSING'
    }

    Write-Output "Live Provider verification passed: provider=$provider; contentVersion=$ExpectedContentVersion; answerScope=GENERAL; intentSource=RULE; constructionMode=GENERAL_MODEL; evidenceState=NOT_REQUIRED; resolution=ANSWERED; blocks=$($response.blocks.Count)."
}
catch {
    $code = [string]$_.Exception.Message
    if ($code -notin $script:allowedFailureCodes) {
        $code = 'LIVE_PROVIDER_RESPONSE_UNREADABLE'
    }
    [Console]::Error.WriteLine($code)
    exit 1
}
