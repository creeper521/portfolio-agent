param(
    [Parameter(Mandatory = $true)]
    [string]$ResponsePath,
    [Parameter(Mandatory = $true)]
    [string]$ExpectedContentVersion,
    [ValidateSet('ANSWER', 'CONVERSATIONAL')]
    [string]$ExpectedKind = 'ANSWER'
)

$ErrorActionPreference = 'Stop'
$script:allowedFailureCodes = @(
    'LIVE_PROVIDER_CONFIG_INVALID',
    'LIVE_PROVIDER_RESPONSE_UNREADABLE',
    'LIVE_PROVIDER_CONTENT_VERSION_MISMATCH',
    'LIVE_PROVIDER_KIND_MISMATCH',
    'LIVE_PROVIDER_PUBLIC_TURN_INVALID',
    'LIVE_PROVIDER_GENERAL_SUPPORT_MISSING'
)

function Stop-Assertion([string]$Code) {
    if ($Code -notin $script:allowedFailureCodes) {
        throw 'LIVE_PROVIDER_RESPONSE_UNREADABLE'
    }
    throw $Code
}

function Get-ProcessEnvironmentValue([string]$Name) {
    return [System.Environment]::GetEnvironmentVariable(
        $Name,
        [System.EnvironmentVariableTarget]::Process
    )
}

function Test-ApprovedFlag([string]$Value) {
    return [string]::Equals($Value, 'true', [System.StringComparison]::OrdinalIgnoreCase)
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
    $selectedKey = switch ($provider) {
        'DEEPSEEK_V4_FLASH' { Get-ProcessEnvironmentValue 'PORTFOLIO_AGENT_DEEPSEEK_API_KEY' }
        'GLM_4_7' { Get-ProcessEnvironmentValue 'PORTFOLIO_AGENT_GLM_API_KEY' }
        default { Stop-Assertion 'LIVE_PROVIDER_CONFIG_INVALID' }
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

    if ([string]$response.kind -cne $ExpectedKind) {
        Stop-Assertion 'LIVE_PROVIDER_KIND_MISMATCH'
    }
    if ([string]::IsNullOrWhiteSpace([string]$response.requestId) -or
            [string]::IsNullOrWhiteSpace([string]$response.conversation.conversationId) -or
            $null -ne $response.agentTurn -or $null -ne $response.blocks -or
            $null -ne $response.degraded) {
        Stop-Assertion 'LIVE_PROVIDER_PUBLIC_TURN_INVALID'
    }

    if ($ExpectedKind -eq 'ANSWER') {
        if ($null -eq $response.answer -or
                [string]$response.answer.contentReleaseId -cne $ExpectedContentVersion) {
            Stop-Assertion 'LIVE_PROVIDER_CONTENT_VERSION_MISMATCH'
        }
        if ([string]$response.answer.resolution -notin @('COMPLETE', 'PARTIAL') -or
                @($response.answer.goalResults).Count -lt 1 -or
                $null -eq $response.answer.sourceCatalog) {
            Stop-Assertion 'LIVE_PROVIDER_PUBLIC_TURN_INVALID'
        }
        if ('GENERAL_KNOWLEDGE' -notin @($response.answer.sourceComposition)) {
            Stop-Assertion 'LIVE_PROVIDER_GENERAL_SUPPORT_MISSING'
        }
        $goalCount = @($response.answer.goalResults).Count
    }
    else {
        if ([string]::IsNullOrWhiteSpace([string]$response.message)) {
            Stop-Assertion 'LIVE_PROVIDER_PUBLIC_TURN_INVALID'
        }
        $goalCount = 0
    }

    Write-Output ("Live Provider PublicAgentTurn passed: provider={0}; kind={1}; contentVersion={2}; goals={3}." -f `
            $provider, $ExpectedKind, $ExpectedContentVersion, $goalCount)
}
catch {
    $code = [string]$_.Exception.Message
    if ($code -notin $script:allowedFailureCodes) {
        $code = 'LIVE_PROVIDER_RESPONSE_UNREADABLE'
    }
    [Console]::Error.WriteLine($code)
    exit 1
}
