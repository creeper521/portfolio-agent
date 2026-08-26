param(
    [Parameter(Mandatory = $true)]
    [string]$BackendBaseUrl,
    [Parameter(Mandatory = $true)]
    [string]$ExpectedContentVersion,
    [Parameter(Mandatory = $true)]
    [ValidateSet('glm-4-7-flash', 'qwen-3-7-flash')]
    [string]$ModelRef,
    [Parameter(Mandatory = $true)]
    [string]$SelectionVersion,
    [ValidateRange(5, 20)]
    [int]$DirectTrials = 5,
    [ValidateRange(5, 20)]
    [int]$TwoTurnTrials = 5,
    [ValidateRange(1, 300)]
    [int]$TimeoutSeconds = 60,
    [ValidateRange(0, 60000)]
    [int]$InterTrialDelayMilliseconds = 10000,
    [switch]$AuthorizeRealProvider
)

$ErrorActionPreference = 'Stop'

function Stop-Matrix([string]$Code) {
    throw $Code
}

function Decode-Text([string]$Value) {
    return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Value))
}

function Test-ApprovedFlag([string]$Value) {
    return [string]::Equals(
        $Value, 'true', [StringComparison]::OrdinalIgnoreCase)
}

function Get-ProcessEnvironmentValue([string]$Name) {
    return [Environment]::GetEnvironmentVariable(
        $Name, [EnvironmentVariableTarget]::Process)
}

function Assert-ApprovedEnvironment {
    if (-not (Test-ApprovedFlag (
            Get-ProcessEnvironmentValue 'PORTFOLIO_MODEL_RUNTIME_ENABLED'))) {
        Stop-Matrix 'GOAL_DRAFT_MATRIX_CONFIG_INVALID'
    }
    $names = if ($ModelRef -eq 'glm-4-7-flash') {
        @('PORTFOLIO_GLM_ENABLED', 'PORTFOLIO_GLM_DATA_POLICY_APPROVED',
            'PORTFOLIO_GLM_API_KEY')
    }
    else {
        @('PORTFOLIO_QWEN_ENABLED', 'PORTFOLIO_QWEN_DATA_POLICY_APPROVED',
            'PORTFOLIO_QWEN_API_KEY')
    }
    if (-not (Test-ApprovedFlag (Get-ProcessEnvironmentValue $names[0])) -or
            -not (Test-ApprovedFlag (Get-ProcessEnvironmentValue $names[1])) -or
            [string]::IsNullOrWhiteSpace(
                (Get-ProcessEnvironmentValue $names[2]))) {
        Stop-Matrix 'GOAL_DRAFT_MATRIX_CONFIG_INVALID'
    }
}

function Get-ClosedPublicFailure([object]$Body) {
    $kind = [string]$Body.kind
    $code = [string]$Body.code
    if ($kind -notmatch '^[A-Z_]{1,64}$') { $kind = 'UNKNOWN' }
    if ($code -notmatch '^[A-Z0-9_]{1,64}$') { $code = 'NONE' }
    return "$kind`:$code"
}

function Invoke-Turn([string]$InputText, [string]$Token) {
    $requestId = [guid]::NewGuid().ToString()
    $payload = @{
        requestId = $requestId
        modelSelection = @{
            kind = 'MODEL'
            modelRef = $ModelRef
            selectionVersion = $SelectionVersion
        }
        command = @{
            kind = 'ASK'
            input = @{ kind = 'FREE_TEXT'; text = $InputText }
        }
        surfaceContext = @{
            audienceRole = 'INTERVIEWER'
            requestSource = 'AGENT_PAGE'
        }
        conversationWindow = @()
    } | ConvertTo-Json -Depth 8 -Compress
    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $headers.Authorization = "Bearer $Token"
    }
    try {
        $response = Invoke-RestMethod -Method Post `
            -Uri ($BackendBaseUrl.TrimEnd('/') + '/api/agent/turns') `
            -Headers $headers `
            -ContentType 'application/json; charset=utf-8' `
            -Body ([Text.Encoding]::UTF8.GetBytes($payload)) `
            -TimeoutSec $TimeoutSeconds
        return @{ RequestId = $requestId; Body = $response; Failure = '' }
    }
    catch {
        $body = $null
        if (-not [string]::IsNullOrWhiteSpace([string]$_.ErrorDetails.Message)) {
            try { $body = $_.ErrorDetails.Message | ConvertFrom-Json }
            catch { $body = $null }
        }
        $failure = if ($null -eq $body) {
            'TRANSPORT_FAILED'
        }
        else {
            'PUBLIC_' + (Get-ClosedPublicFailure $body)
        }
        return @{ RequestId = $requestId; Body = $body; Failure = $failure }
    }
}

function Test-ExecutionIdentity([object]$Body, [string]$Participation) {
    return $null -ne $Body.modelExecution -and
        [string]$Body.modelExecution.selectionKind -ceq 'MODEL' -and
        [string]$Body.modelExecution.requestedModelRef -ceq $ModelRef -and
        [string]$Body.modelExecution.selectionVersion -ceq $SelectionVersion -and
        [string]$Body.modelExecution.participation -ceq $Participation
}

function Test-ResponseRequestId([hashtable]$Result) {
    return $null -ne $Result.Body -and
        [string]$Result.Body.requestId -ceq [string]$Result.RequestId
}

function Test-Recommendation([object]$Body) {
    if ($null -eq $Body -or [string]$Body.kind -cne 'ANSWER' -or
            [string]$Body.answer.contentReleaseId -cne $ExpectedContentVersion -or
            -not (Test-ExecutionIdentity $Body 'GOAL_INTERPRETATION_ONLY')) {
        return $false
    }
    $presentations = @($Body.answer.goalResults |
        Where-Object { [string]$_.presentation.kind -ceq 'RECOMMENDATION' })
    if ($presentations.Count -ne 1) { return $false }
    $presentation = $presentations[0].presentation
    return [int]$presentation.requestedSize -eq 2 -and
        [int]$presentation.actualSize -eq 2 -and
        @($presentation.items).Count -eq 2
}

function Add-Failure(
    [System.Collections.Generic.List[string]]$Failures,
    [string]$Scope,
    [hashtable]$Result
) {
    $category = if (-not [string]::IsNullOrWhiteSpace($Result.Failure)) {
        $Result.Failure
    }
    elseif ($null -eq $Result.Body) {
        'EMPTY_RESPONSE'
    }
    else {
        'PUBLIC_' + (Get-ClosedPublicFailure $Result.Body)
    }
    if ($category -notmatch '^[A-Z0-9_:.-]{1,160}$') {
        $category = 'UNCLASSIFIED_FAILURE'
    }
    $Failures.Add("$Scope=$category")
}

if (-not $AuthorizeRealProvider) {
    Stop-Matrix 'GOAL_DRAFT_MATRIX_AUTHORIZATION_REQUIRED'
}
if ([string]::IsNullOrWhiteSpace($BackendBaseUrl) -or
        [string]::IsNullOrWhiteSpace($ExpectedContentVersion) -or
        [string]::IsNullOrWhiteSpace($SelectionVersion)) {
    Stop-Matrix 'GOAL_DRAFT_MATRIX_CONFIG_INVALID'
}
$expectedSelectionVersion = if ($ModelRef -eq 'qwen-3-7-flash') {
    'qwen-3-7-flash-v6'
}
else {
    'glm-4-7-flash-v4'
}
if (-not [string]::Equals(
        $SelectionVersion, $expectedSelectionVersion,
        [StringComparison]::Ordinal)) {
    Stop-Matrix 'GOAL_DRAFT_MATRIX_CONFIG_INVALID'
}
Assert-ApprovedEnvironment

$recommendText = Decode-Text '57uZ5oiR5o6o6I2Q5Lik5Liq6aG555uu'
$lowInformationText = '1'
$failures = [System.Collections.Generic.List[string]]::new()
$requestIds = [System.Collections.Generic.HashSet[string]]::new(
    [StringComparer]::Ordinal)
$directPassed = 0
$twoTurnPassed = 0
$zeroGoalPassed = 0

for ($trial = 1; $trial -le $DirectTrials; $trial++) {
    $result = Invoke-Turn $recommendText ''
    $null = $requestIds.Add([string]$result.RequestId)
    if ((Test-ResponseRequestId $result) -and
            (Test-Recommendation $result.Body)) {
        $directPassed++
    }
    else {
        Add-Failure $failures "DIRECT_$trial" $result
    }
    if ($InterTrialDelayMilliseconds -gt 0 -and $trial -lt $DirectTrials) {
        Start-Sleep -Milliseconds $InterTrialDelayMilliseconds
    }
}

for ($trial = 1; $trial -le $TwoTurnTrials; $trial++) {
    if ($InterTrialDelayMilliseconds -gt 0) {
        Start-Sleep -Milliseconds $InterTrialDelayMilliseconds
    }
    $first = Invoke-Turn $lowInformationText ''
    $null = $requestIds.Add([string]$first.RequestId)
    $token = if ($null -eq $first.Body) {
        ''
    }
    else {
        [string]$first.Body.conversation.resumeToken
    }
    $zeroGoalValid = [string]::IsNullOrWhiteSpace($first.Failure) -and
        (Test-ResponseRequestId $first) -and
        [string]$first.Body.kind -ceq 'CONVERSATIONAL' -and
        (Test-ExecutionIdentity $first.Body 'NONE') -and
        -not [string]::IsNullOrWhiteSpace(
            [string]$first.Body.conversation.conversationId) -and
        -not [string]::IsNullOrWhiteSpace($token)
    if (-not $zeroGoalValid) {
        Add-Failure $failures "ZERO_GOAL_$trial" $first
        continue
    }
    $zeroGoalPassed++
    $second = Invoke-Turn $recommendText $token
    $null = $requestIds.Add([string]$second.RequestId)
    $sameConversation = $null -ne $second.Body -and
        [string]$second.Body.conversation.conversationId -ceq
            [string]$first.Body.conversation.conversationId
    if ($sameConversation -and (Test-ResponseRequestId $second) -and
            (Test-Recommendation $second.Body)) {
        $twoTurnPassed++
    }
    else {
        Add-Failure $failures "TWO_TURN_$trial" $second
    }
}

$expectedRequestIds = $DirectTrials + ($TwoTurnTrials * 2)
$uniqueRequests = $requestIds.Count -eq $expectedRequestIds
$status = if ($failures.Count -eq 0 -and $uniqueRequests -and
        $directPassed -eq $DirectTrials -and
        $zeroGoalPassed -eq $TwoTurnTrials -and
        $twoTurnPassed -eq $TwoTurnTrials) { 'PASS' } else { 'FAIL' }

Write-Output (('GOAL_DRAFT_MATRIX_RESULT status={0} modelRef={1} selectionVersion={2} ' +
    'direct={3}/{4} zeroGoalParticipationNone={5}/{6} twoTurn={7}/{8} uniqueRequestIds={9}') -f `
    $status, $ModelRef, $SelectionVersion,
    $directPassed, $DirectTrials, $zeroGoalPassed, $TwoTurnTrials,
    $twoTurnPassed, $TwoTurnTrials, $uniqueRequests.ToString().ToLowerInvariant())
if ($failures.Count -gt 0) {
    Write-Output ('GOAL_DRAFT_MATRIX_FAILURES ' + ($failures -join ','))
}
if ($status -ne 'PASS') { exit 1 }
