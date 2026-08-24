param(
    [Parameter(Mandatory = $true)]
    [string]$BackendBaseUrl,
    [Parameter(Mandatory = $true)]
    [string]$ExpectedContentVersion,
    [ValidateSet('', 'glm-4-7-flash', 'qwen-3-7-flash')]
    [string]$ModelRef = '',
    [ValidateSet('GENERAL', 'SOCIAL')]
    [string]$Scenario = 'GENERAL',
    [ValidateRange(1, 300)]
    [int]$TimeoutSeconds = 60,
    [switch]$FailOnDegraded
)

$ErrorActionPreference = 'Stop'
$checker = Join-Path (Split-Path -Parent $PSScriptRoot) `
    'assert-live-public-turn-response.ps1'

$question = if ($Scenario -eq 'SOCIAL') {
    [string]([char]0x4f60) + [char]0x597d
} else {
    [System.Text.Encoding]::UTF8.GetString(
        [System.Convert]::FromBase64String(
            '6Kej6YeK5LiA5LiLIFJlZGlzIOeahOaMgeS5heWMluacuuWItg=='))
}
$expectedKind = if ($Scenario -eq 'SOCIAL') { 'CONVERSATIONAL' } else { 'ANSWER' }
function Resolve-ModelSelection {
    try {
        $portfolio = Invoke-RestMethod -UseBasicParsing `
            -Uri "$BackendBaseUrl/api/portfolio" `
            -Method Get `
            -TimeoutSec $TimeoutSeconds
    }
    catch {
        Exit-Degraded 'PROVIDER_UNAVAILABLE'
    }
    $selection = if ([string]::IsNullOrWhiteSpace($ModelRef)) {
        $portfolio.agentAvailability.defaultModelSelection
    }
    else {
        @($portfolio.agentAvailability.selectableModels | Where-Object {
            [string]$_.modelRef -ceq $ModelRef
        } | Select-Object -First 1)
    }
    if ($selection -is [array]) {
        $selection = $selection | Select-Object -First 1
    }
    if ($null -eq $selection -or
            [string]$selection.kind -cne 'MODEL' -or
            [string]::IsNullOrWhiteSpace([string]$selection.modelRef) -or
            [string]::IsNullOrWhiteSpace([string]$selection.selectionVersion)) {
        Exit-Degraded 'PROVIDER_RESPONSE_INVALID'
    }
    return @{
        kind = 'MODEL'
        modelRef = [string]$selection.modelRef
        selectionVersion = [string]$selection.selectionVersion
    }
}

function Resolve-ProbeCategory(
    [object]$Response,
    [string]$AssertionOutput
) {
    if ($AssertionOutput -match 'LIVE_PROVIDER_KIND_MISMATCH') {
        return 'PROBE_ROUTE_BYPASSED'
    }
    return 'PROVIDER_RESPONSE_INVALID'
}

function Exit-Degraded([string]$Category) {
    Write-Output "LIVE_PROVIDER_DEGRADED:$Category"
    if ($FailOnDegraded) {
        exit 1
    }
    exit 0
}

$responsePath = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('portfolio-provider-probe-' + [guid]::NewGuid().ToString('N') + '.json')
try {
    $modelSelection = Resolve-ModelSelection
    $requestBody = @{
        requestId = [guid]::NewGuid()
        modelSelection = $modelSelection
        command = @{
            kind = 'ASK'
            input = @{ kind = 'FREE_TEXT'; text = $question }
        }
        surfaceContext = @{ audienceRole = 'INTERVIEWER'; requestSource = 'AGENT_PAGE' }
        conversationWindow = @()
    } | ConvertTo-Json -Depth 6 -Compress
    try {
        $http = Invoke-WebRequest -UseBasicParsing `
            -Uri "$BackendBaseUrl/api/agent/turns" `
            -Method Post `
            -ContentType 'application/json; charset=utf-8' `
            -Body ([System.Text.Encoding]::UTF8.GetBytes($requestBody)) `
            -TimeoutSec $TimeoutSeconds
    }
    catch {
        Exit-Degraded 'PROVIDER_UNAVAILABLE'
    }
    [System.IO.File]::WriteAllText(
        $responsePath,
        [string]$http.Content,
        [System.Text.UTF8Encoding]::new($false)
    )
    $assertionExitCode = 1
    $assertionOutput = ''
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $assertionOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $checker `
            -ResponsePath $responsePath `
            -ExpectedContentVersion $ExpectedContentVersion `
            -ExpectedModelRef ([string]$modelSelection.modelRef) `
            -ExpectedKind $expectedKind 2>&1 | Out-String).Trim()
        $assertionExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($assertionExitCode -eq 0) {
        Write-Output 'LIVE_PROVIDER_CONNECTED'
        exit 0
    }
    $response = $http.Content | ConvertFrom-Json
    $category = Resolve-ProbeCategory $response $assertionOutput
    Exit-Degraded $category
}
finally {
    if (Test-Path -LiteralPath $responsePath) {
        Remove-Item -LiteralPath $responsePath -Force
    }
}
