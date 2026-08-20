param(
    [Parameter(Mandatory = $true)]
    [string]$BackendBaseUrl,
    [Parameter(Mandatory = $true)]
    [string]$ExpectedContentVersion,
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
    'Explain optimistic locking and give one concise general example.'
}
$expectedKind = if ($Scenario -eq 'SOCIAL') { 'CONVERSATIONAL' } else { 'ANSWER' }
$requestBody = @{
    requestId = [guid]::NewGuid()
    command = @{
        kind = 'ASK'
        input = @{ kind = 'FREE_TEXT'; text = $question }
    }
    surfaceContext = @{ audienceRole = 'INTERVIEWER'; requestSource = 'AGENT_PAGE' }
    conversationWindow = @()
} | ConvertTo-Json -Depth 6 -Compress

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
