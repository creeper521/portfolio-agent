param(
    [Parameter(Mandatory = $true)]
    [string]$BackendBaseUrl,
    [Parameter(Mandatory = $true)]
    [string]$ExpectedContentVersion,
    [ValidateRange(1, 120)]
    [int]$TimeoutSeconds = 30,
    [switch]$AuthorizeRealProvider
)

$ErrorActionPreference = 'Stop'

function Stop-DiscussionGate([string]$Code) {
    throw $Code
}

function Decode-Fixture([string]$Value) {
    [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Value))
}

function Latency-Bucket([TimeSpan]$Elapsed) {
    if ($Elapsed.TotalSeconds -lt 5) { return 'LT_5S' }
    if ($Elapsed.TotalSeconds -lt 10) { return 'LT_10S' }
    if ($Elapsed.TotalSeconds -lt 20) { return 'LT_20S' }
    return 'GE_20S'
}

function Invoke-Turn([hashtable]$Command, [string]$Token) {
    $payload = @{
        requestId = [guid]::NewGuid().ToString()
        command = $Command
        conversationWindow = @()
    } | ConvertTo-Json -Depth 12 -Compress
    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $headers.Authorization = "Bearer $Token"
    }
    $timer = [Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-RestMethod -Method Post `
            -Uri "$BackendBaseUrl/api/agent/turns" `
            -Headers $headers `
            -ContentType 'application/json; charset=utf-8' `
            -Body ([Text.Encoding]::UTF8.GetBytes($payload)) `
            -TimeoutSec $TimeoutSeconds
        $timer.Stop()
        return @{ Body = $response; Latency = (Latency-Bucket $timer.Elapsed) }
    }
    catch {
        Stop-DiscussionGate 'PROJECT_DISCUSSION_TRANSPORT_FAILED'
    }
}

if (-not $AuthorizeRealProvider) {
    Stop-DiscussionGate 'PROJECT_DISCUSSION_AUTHORIZATION_REQUIRED'
}
if ([string]::IsNullOrWhiteSpace($BackendBaseUrl) -or
        [string]::IsNullOrWhiteSpace($ExpectedContentVersion)) {
    Stop-DiscussionGate 'PROJECT_DISCUSSION_CONFIG_INVALID'
}

$recommendText = Decode-Fixture '5o6o6I2QIDIg5Liq6aG555uu'
$followText = Decode-Fixture '57un57ut56ys5LqM5Liq'
$routeText = Decode-Fixture '6K+m57uG6K+05piO5oqA5pyv5pa55qGI'
$latencies = New-Object System.Collections.Generic.List[string]

$recommend = Invoke-Turn @{
    kind = 'ASK'
    input = @{ kind = 'FREE_TEXT'; text = $recommendText }
} ''
$latencies.Add($recommend.Latency)
if ($recommend.Body.kind -ne 'ANSWER' -or
        $recommend.Body.answer.contentReleaseId -ne $ExpectedContentVersion) {
    $kind = [string]$recommend.Body.kind
    $code = [string]$recommend.Body.code
    if ($kind -notmatch '^[A-Z_]{1,64}$') { $kind = 'UNKNOWN' }
    if ($code -notmatch '^[A-Z0-9_]{1,64}$') { $code = 'NONE' }
    Stop-DiscussionGate "PROJECT_DISCUSSION_RECOMMENDATION_FAILED:$kind`:$code"
}
$recommendations = @($recommend.Body.answer.goalResults |
    Where-Object { $_.presentation.kind -eq 'RECOMMENDATION' })
if ($recommendations.Count -ne 1) {
    Stop-DiscussionGate "PROJECT_DISCUSSION_RECOMMENDATION_FAILED:PRESENTATION_COUNT:$($recommendations.Count)"
}
$items = @($recommendations[0].presentation.items)
if ($items.Count -ne 2) {
    Stop-DiscussionGate 'PROJECT_DISCUSSION_SCOPE_INVALID'
}
$actions = @($items | ForEach-Object { $_.discussionAction.continuation })
$handles = @($actions | Select-Object -ExpandProperty contextHandle -Unique)
$allowedItemIds = @($actions | Select-Object -ExpandProperty resultItemId)
if ($handles.Count -ne 1 -or
        @($actions | Where-Object { $_.operation -ne 'ENTER_RESULT' }).Count -ne 0 -or
        @($allowedItemIds | Select-Object -Unique).Count -ne 2) {
    Stop-DiscussionGate 'PROJECT_DISCUSSION_SCOPE_INVALID'
}
$token = [string]$recommend.Body.conversation.resumeToken
if ([string]::IsNullOrWhiteSpace($token)) {
    Stop-DiscussionGate 'PROJECT_DISCUSSION_SESSION_INVALID'
}

$follow = Invoke-Turn @{
    kind = 'ASK'
    input = @{ kind = 'FREE_TEXT'; text = $followText }
    referenceContextHandle = $handles[0]
} $token
$latencies.Add($follow.Latency)
if ($follow.Body.kind -eq 'CLARIFICATION') {
    $fields = @($follow.Body.clarification.fields)
    $choices = if ($fields.Count -eq 1) { @($fields[0].choices) } else { @() }
    if ($choices.Count -ne 2) {
        Stop-DiscussionGate 'PROJECT_DISCUSSION_CHOICE_INVALID'
    }
    $follow = Invoke-Turn @{
        kind = 'RESOLVE_CLARIFICATION'
        clarificationId = [string]$follow.Body.clarification.clarificationId
        answer = @{ kind = 'CHOICE'; choiceId = [string]$choices[1].choiceId }
    } $token
    $latencies.Add($follow.Latency)
}
if ($follow.Body.kind -ne 'ANSWER') {
    $kind = [string]$follow.Body.kind
    $code = [string]$follow.Body.code
    if ($kind -notmatch '^[A-Z_]{1,64}$') { $kind = 'UNKNOWN' }
    if ($code -notmatch '^[A-Z0-9_]{1,64}$') { $code = 'NONE' }
    Stop-DiscussionGate "PROJECT_DISCUSSION_ENTER_FAILED:$kind`:$code"
}

$headers = @{ Authorization = "Bearer $token" }
$summary = Invoke-RestMethod -Method Get `
    -Uri "$BackendBaseUrl/api/agent/conversations/current" `
    -Headers $headers -TimeoutSec $TimeoutSeconds
if ($summary.activeDiscussion.status -ne 'ACTIVE') {
    Stop-DiscussionGate 'PROJECT_DISCUSSION_ACTIVE_STATE_FAILED'
}
$lockedSubject = [string]$summary.activeDiscussion.subject.reference
$routeHandle = [string]$summary.activeDiscussion.routeContinuation.contextHandle
if ([string]::IsNullOrWhiteSpace($lockedSubject) -or
        [string]::IsNullOrWhiteSpace($routeHandle)) {
    Stop-DiscussionGate 'PROJECT_DISCUSSION_ACTIVE_STATE_FAILED'
}

$routed = Invoke-Turn @{
    kind = 'CONTINUE'
    operation = 'ROUTE_IN_CONTEXT'
    contextHandle = $routeHandle
    text = $routeText
} $token
$latencies.Add($routed.Latency)
if ($routed.Body.kind -ne 'ANSWER') {
    Stop-DiscussionGate 'PROJECT_DISCUSSION_ROUTE_FAILED'
}
$afterRoute = Invoke-RestMethod -Method Get `
    -Uri "$BackendBaseUrl/api/agent/conversations/current" `
    -Headers $headers -TimeoutSec $TimeoutSeconds
if ($afterRoute.activeDiscussion.subject.reference -ne $lockedSubject) {
    Stop-DiscussionGate 'PROJECT_DISCUSSION_LOCKED_SUBJECT_CHANGED'
}

$exited = Invoke-Turn @{
    kind = 'CONTINUE'
    operation = 'EXIT_CONTEXT'
    contextHandle = [string]$afterRoute.activeDiscussion.exitAction.continuation.contextHandle
} $token
if ($exited.Body.kind -ne 'CONVERSATIONAL') {
    Stop-DiscussionGate 'PROJECT_DISCUSSION_EXIT_FAILED'
}
$afterExit = Invoke-RestMethod -Method Get `
    -Uri "$BackendBaseUrl/api/agent/conversations/current" `
    -Headers $headers -TimeoutSec $TimeoutSeconds
if ($null -ne $afterExit.activeDiscussion) {
    Stop-DiscussionGate 'PROJECT_DISCUSSION_EXIT_FAILED'
}

Write-Output ('PROJECT_DISCUSSION_PASS operation=ENTER_RESULT,ROUTE_IN_CONTEXT,EXIT_CONTEXT; ' +
    'goalKind=PORTFOLIO_RECOMMEND,PORTFOLIO_FACT; lockedSubject=true; ' +
    'candidateScope=true; terminal=PASS; latency=' + ($latencies -join ','))
