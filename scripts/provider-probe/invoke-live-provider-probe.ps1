param(
    [Parameter(Mandatory = $true)]
    [string]$BackendBaseUrl,
    [Parameter(Mandatory = $true)]
    [string]$ExpectedContentVersion,
    [ValidateRange(1, 300)]
    [int]$TimeoutSeconds = 60,
    [switch]$FailOnDegraded
)

$ErrorActionPreference = 'Stop'
$checker = Join-Path (Split-Path -Parent $PSScriptRoot) `
    'assert-live-provider-response.ps1'

$requestBody = @{
    turnId = [guid]::NewGuid()
    requestToken = [guid]::NewGuid()
    question = 'Explain optimistic locking and give one concise example.'
    messages = @()
    context = @{
        audienceRole = 'INTERVIEWER'
        source = 'AGENT_PAGE'
    }
} | ConvertTo-Json -Depth 6 -Compress

function Resolve-ProbeCategory(
    [object]$Response,
    [string]$AssertionOutput
) {
    $providerCategory = switch ([string]$Response.noticeCode) {
        'PROVIDER_AUTH_FAILED' { 'PROVIDER_AUTH_FAILED' }
        'PROVIDER_TIMEOUT' { 'PROVIDER_TIMEOUT' }
        'PROVIDER_CONNECTION_FAILED' { 'PROVIDER_UNAVAILABLE' }
        'PROVIDER_EMPTY_RESPONSE' { 'PROVIDER_RESPONSE_INVALID' }
        'PROVIDER_INVALID_RESPONSE' { 'PROVIDER_RESPONSE_INVALID' }
        'PROVIDER_DRAFT_REJECTED' { 'PROVIDER_DRAFT_REJECTED' }
        'PROVIDER_DISABLED' { 'PROVIDER_POLICY_INCOMPATIBLE' }
        default { $null }
    }
    if ($null -ne $providerCategory) {
        return $providerCategory
    }
    if ($AssertionOutput -match 'LIVE_PROVIDER_ROUTE_BYPASSED') {
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
            -Uri "$BackendBaseUrl/api/v2/answers" `
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
            -ExpectedContentVersion $ExpectedContentVersion 2>&1 | Out-String).Trim()
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
