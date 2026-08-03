param(
    [Parameter(Mandatory = $true)]
    [string]$ResponsePath,
    [Parameter(Mandatory = $true)]
    [string]$ExpectedContentVersion
)

$ErrorActionPreference = 'Stop'

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
            throw 'approval'
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
        throw 'provider'
    }
    if ([string]::IsNullOrWhiteSpace($selectedKey)) {
        throw 'credential'
    }

    if (-not (Test-Path -LiteralPath $ResponsePath -PathType Leaf)) {
        throw 'response path'
    }
    try {
        $response = [System.IO.File]::ReadAllText(
            $ResponsePath,
            [System.Text.UTF8Encoding]::new($false)
        ) | ConvertFrom-Json
    }
    catch {
        throw 'response JSON'
    }

    if ($response.contentVersion -cne $ExpectedContentVersion) {
        throw 'content version'
    }
    if ($response.degraded -isnot [bool] -or $response.degraded -ne $false) {
        throw 'degraded'
    }
    if ($response.intentSource -cne 'MODEL') {
        throw 'intent source'
    }
    if ($response.constructionMode -cne 'EVIDENCE_COMPOSITION') {
        throw 'construction mode'
    }
    if ($response.evidenceState -cne 'VERIFIED') {
        throw 'evidence state'
    }
    if ($response.resolution -cne 'ANSWERED') {
        throw 'resolution'
    }
    if ($response.blocks -isnot [System.Array] -or $response.blocks.Count -lt 1) {
        throw 'blocks'
    }

    Write-Output "Live Provider verification passed: provider=$provider; contentVersion=$ExpectedContentVersion; intentSource=MODEL; constructionMode=EVIDENCE_COMPOSITION; evidenceState=VERIFIED; resolution=ANSWERED; blocks=$($response.blocks.Count)."
}
catch {
    Write-Error 'Live Provider response assertion failed.'
    exit 1
}
