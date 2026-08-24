param(
    [Parameter(Mandatory = $true)]
    [string]$BackendBaseUrl,
    [string]$ScenarioDirectory = '',
    [ValidateRange(1, 120)]
    [int]$TimeoutSeconds = 30,
    [switch]$RequireComplete
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$scenarioRoot = if ([string]::IsNullOrWhiteSpace($ScenarioDirectory)) {
    Join-Path $root 'contracts\agent-turn\scenarios'
}
else {
    [System.IO.Path]::GetFullPath($ScenarioDirectory)
}
if (-not (Test-Path -LiteralPath $scenarioRoot -PathType Container)) {
    throw 'AGENT_SCENARIO_DIRECTORY_MISSING'
}

function New-RequestBody([object]$Scenario, [string]$RequestId) {
    $source = $Scenario.command
    $command = [ordered]@{ kind = [string]$source.kind }
    switch ([string]$source.kind) {
        'ASK' {
            if ([string]$source.inputKind -eq 'FREE_TEXT') {
                $command.input = [ordered]@{
                    kind = 'FREE_TEXT'
                    text = [string]$source.text
                }
            }
            else {
                $command.input = [ordered]@{
                    kind = 'PRESET'
                    presetId = [string]$source.presetId
                }
            }
        }
        'CONTINUE' {
            $command.operation = [string]$source.operation
            $command.contextHandle = [string]$source.contextHandle
            if ($null -ne $source.PSObject.Properties['text']) {
                $command.text = [string]$source.text
            }
        }
        'RESOLVE_CLARIFICATION' {
            $command.clarificationId = [string]$source.clarificationId
            $command.answer = [ordered]@{
                kind = [string]$source.answerKind
                choiceId = [string]$source.choiceId
            }
        }
    }
    return [ordered]@{
        requestId = $RequestId
        command = $command
        surfaceContext = [ordered]@{
            audienceRole = 'INTERVIEWER'
            requestSource = 'AGENT_PAGE'
        }
        conversationWindow = @()
    }
}

function Invoke-ScenarioRequest([object]$Body) {
    $json = $Body | ConvertTo-Json -Depth 10 -Compress
    try {
        $http = Invoke-WebRequest -UseBasicParsing -Method Post `
            -Uri ($BackendBaseUrl.TrimEnd('/') + '/api/agent/turns') `
            -ContentType 'application/json; charset=utf-8' `
            -Body ([Text.Encoding]::UTF8.GetBytes($json)) `
            -TimeoutSec $TimeoutSeconds
        $parsed = if ([string]::IsNullOrWhiteSpace($http.Content)) {
            $null
        }
        else {
            $http.Content | ConvertFrom-Json
        }
        return [pscustomobject]@{
            StatusCode = [int]$http.StatusCode
            Body = $parsed
        }
    }
    catch {
        $statusCode = 0
        if ($null -ne $_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }
        $parsed = $null
        if (-not [string]::IsNullOrWhiteSpace([string]$_.ErrorDetails.Message)) {
            try { $parsed = $_.ErrorDetails.Message | ConvertFrom-Json } catch { }
        }
        return [pscustomobject]@{
            StatusCode = $statusCode
            Body = $parsed
        }
    }
}

function Test-PublicExpectation([object]$Expected, [object]$Actual) {
    if ($null -ne $Expected.PSObject.Properties['apiError']) {
        $actualCode = if ($null -ne $Actual.Body.error) {
            [string]$Actual.Body.error.code
        }
        else {
            [string]$Actual.Body.code
        }
        return $Actual.StatusCode -eq [int]$Expected.apiError.status -and
            $actualCode -ceq [string]$Expected.apiError.code
    }
    if ([string]$Expected.publication -eq 'NONE') {
        return $null -eq $Actual.Body
    }
    if ($Actual.StatusCode -ne 200 -or $null -eq $Actual.Body -or
            [string]$Actual.Body.kind -cne [string]$Expected.kind) {
        return $false
    }
    if ([string]$Expected.kind -ne 'ANSWER') {
        return $true
    }
    if ([string]$Actual.Body.answer.resolution -cne [string]$Expected.resolution) {
        return $false
    }
    $expectedGoals = @($Expected.goals)
    $actualGoals = @($Actual.Body.answer.goalResults)
    if ($actualGoals.Count -ne $expectedGoals.Count) {
        return $false
    }
    for ($index = 0; $index -lt $expectedGoals.Count; $index++) {
        if ([string]$actualGoals[$index].coverage -cne
                [string]$expectedGoals[$index].coverage) {
            return $false
        }
        if ($null -ne $expectedGoals[$index].PSObject.Properties['presentation'] -and
                [string]$actualGoals[$index].presentation.kind -cne
                [string]$expectedGoals[$index].presentation) {
            return $false
        }
    }
    if ($null -ne $Expected.PSObject.Properties['publicPresentationCount']) {
        $presentations = @($actualGoals | Where-Object {
            $null -ne $_.presentation
        }).Count
        if ($presentations -ne [int]$Expected.publicPresentationCount) {
            return $false
        }
    }
    return $true
}

$results = [System.Collections.Generic.List[object]]::new()
$manifests = @(Get-ChildItem -LiteralPath $scenarioRoot -Filter '*.json' -File |
    Sort-Object Name)
foreach ($manifestPath in $manifests) {
    $manifest = Get-Content -LiteralPath $manifestPath.FullName -Raw -Encoding UTF8 |
        ConvertFrom-Json
    foreach ($scenario in @($manifest.scenarios)) {
        $requestId = [guid]::NewGuid().ToString()
        $request = New-RequestBody $scenario $requestId
        $actual = Invoke-ScenarioRequest $request
        $setupRequired = @(
            'requestIdReuse', 'lifecycleSignal', 'settlementSignal',
            'providerBehavior'
        ) | Where-Object {
            $null -ne $scenario.command.PSObject.Properties[$_]
        }
        if ([string]$scenario.command.requestIdReuse -eq 'SAME_FINGERPRINT') {
            $actual = Invoke-ScenarioRequest $request
        }
        elseif ([string]$scenario.command.requestIdReuse -eq 'DIFFERENT_FINGERPRINT') {
            $changed = New-RequestBody $scenario $requestId
            $changed.command.input.text = [string]$changed.command.input.text + ' fixture-change'
            $actual = Invoke-ScenarioRequest $changed
        }
        $publicMatched = Test-PublicExpectation $scenario.expected $actual
        $hardErrorObservable = @($scenario.hardErrorExpectations).Count -eq 0
        $setupComplete = @($setupRequired).Count -eq 0
        $status = if (-not $publicMatched) {
            'FAILED'
        }
        elseif (-not $hardErrorObservable -or -not $setupComplete) {
            'IN_PROGRESS'
        }
        else {
            'PASS'
        }
        $results.Add([pscustomobject]@{
            caseId = [string]$scenario.caseId
            status = $status
            httpStatus = $actual.StatusCode
            kind = if ($null -eq $actual.Body) { 'NONE' } else { [string]$actual.Body.kind }
            resolution = if ($null -eq $actual.Body.answer) {
                'NONE'
            }
            else {
                [string]$actual.Body.answer.resolution
            }
            publicExpectationMatched = $publicMatched
            setupComplete = $setupComplete
            hardErrorExpectationsObservable = $hardErrorObservable
        })
    }
}

$total = $results.Count
$passed = @($results | Where-Object status -eq 'PASS').Count
$inProgress = @($results | Where-Object status -eq 'IN_PROGRESS').Count
$failed = @($results | Where-Object status -eq 'FAILED').Count
$overall = if ($failed -gt 0) { 'FAILED' } elseif ($inProgress -gt 0) {
    'IN_PROGRESS'
} else { 'PASS' }
[pscustomobject]@{
    schemaVersion = 1
    overall = $overall
    executionMode = 'PRODUCTION_HTTP_COMMAND'
    total = $total
    passed = $passed
    inProgress = $inProgress
    failed = $failed
    results = @($results)
} | ConvertTo-Json -Depth 6 -Compress | Write-Output

if ($RequireComplete -and $overall -ne 'PASS') {
    [Console]::Error.WriteLine('AGENT_SCENARIO_RUNTIME_INCOMPLETE')
    exit 1
}
