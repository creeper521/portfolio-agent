param(
    [string]$StatusPath = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$resolvedStatusPath = if ([string]::IsNullOrWhiteSpace($StatusPath)) {
    Join-Path $root 'docs\agent-architecture-status.json'
} else {
    [System.IO.Path]::GetFullPath($StatusPath)
}

$errors = [System.Collections.Generic.List[string]]::new()

function Add-ValidationError([string]$message) {
    $errors.Add($message)
}

function Require-Text($value, [string]$path) {
    if ($null -eq $value -or [string]::IsNullOrWhiteSpace([string]$value)) {
        Add-ValidationError "$path must be non-empty"
    }
}

if (-not (Test-Path -LiteralPath $resolvedStatusPath -PathType Leaf)) {
    Write-Error 'Agent architecture status file is missing.'
    exit 1
}

try {
    $status = Get-Content -LiteralPath $resolvedStatusPath -Raw | ConvertFrom-Json
} catch {
    Write-Error 'Agent architecture status file is not valid JSON.'
    exit 1
}

if ($status.schemaVersion -ne 1) { Add-ValidationError 'schemaVersion must equal 1' }
if ($status.project -ne 'portfolio-agent') { Add-ValidationError 'project must equal portfolio-agent' }

$overallStatuses = @(
    'PENDING',
    'ARCHITECTURE_REVIEW',
    'IN_PROGRESS',
    'VERIFICATION_IN_PROGRESS',
    'COMPLETE'
)
if ($status.overallStatus -notin $overallStatuses) {
    Add-ValidationError 'overallStatus is outside the closed set'
}

$requiredInvariants = @(
    'SINGLE_RUNTIME_AUTHORITY',
    'NO_RUNTIME_COMPATIBILITY_BRIDGE',
    'VERSION_LEVEL_ROLLBACK_ONLY',
    'PUBLIC_CONTRACT_SINGLE_SOURCE',
    'EVIDENCE_BEFORE_COMPLETION',
    'PRIVACY_BOUNDARY'
)
$invariantStatuses = @('PASS', 'FAILED', 'BLOCKED')
$invariantIds = @($status.hardInvariants | ForEach-Object { [string]$_.id })
if (@($invariantIds | Sort-Object -Unique).Count -ne $invariantIds.Count) {
    Add-ValidationError 'hardInvariants ids must be unique'
}
foreach ($requiredInvariant in $requiredInvariants) {
    if ($requiredInvariant -notin $invariantIds) {
        Add-ValidationError "hardInvariants is missing $requiredInvariant"
    }
}
foreach ($invariant in @($status.hardInvariants)) {
    if ($invariant.status -notin $invariantStatuses) {
        Add-ValidationError "hard invariant $($invariant.id) cannot be waived or use an unknown status"
    }
    Require-Text $invariant.evidence "hardInvariants.$($invariant.id).evidence"
}

$requiredAuthorities = @(
    'httpCommand', 'userIntent', 'executionPlan', 'executionEngine',
    'publicProjection', 'publicContract', 'turnLifecycle', 'turnState',
    'frontendTransport', 'frontendParser'
)
foreach ($authority in $requiredAuthorities) {
    Require-Text $status.activeAuthorities.$authority "activeAuthorities.$authority"
}

$deferredStatuses = @('PENDING', 'IN_PROGRESS', 'WAIVED', 'BLOCKED', 'FAILED', 'CLOSED')
$deferredIds = @($status.deferredItems | ForEach-Object { [string]$_.id })
if (@($deferredIds | Sort-Object -Unique).Count -ne $deferredIds.Count) {
    Add-ValidationError 'deferredItems ids must be unique'
}
foreach ($item in @($status.deferredItems)) {
    Require-Text $item.id 'deferredItems.id'
    Require-Text $item.title "deferredItems.$($item.id).title"
    if ($item.status -notin $deferredStatuses) {
        Add-ValidationError "deferred item $($item.id) has an unknown status"
    }
    if ($item.status -ne 'CLOSED') {
        Require-Text $item.category "deferredItems.$($item.id).category"
        Require-Text $item.reason "deferredItems.$($item.id).reason"
        Require-Text $item.owner "deferredItems.$($item.id).owner"
        Require-Text $item.affectedGate "deferredItems.$($item.id).affectedGate"
        Require-Text $item.evidence.checkedAt "deferredItems.$($item.id).evidence.checkedAt"
        Require-Text $item.evidence.summary "deferredItems.$($item.id).evidence.summary"
        Require-Text $item.resumeWhen.condition "deferredItems.$($item.id).resumeWhen.condition"
        Require-Text $item.resumeWhen.checkCommand "deferredItems.$($item.id).resumeWhen.checkCommand"
        Require-Text $item.nextAction.command "deferredItems.$($item.id).nextAction.command"
        Require-Text $item.nextAction.successCondition "deferredItems.$($item.id).nextAction.successCondition"
        Require-Text $item.createdAt "deferredItems.$($item.id).createdAt"
        Require-Text $item.recheckBy "deferredItems.$($item.id).recheckBy"
        if (@($item.forbiddenClaims).Count -eq 0) {
            Add-ValidationError "deferredItems.$($item.id).forbiddenClaims must not be empty"
        }
    }
}

$openDeferredItems = @($status.deferredItems | Where-Object { $_.status -ne 'CLOSED' })
if ($status.overallStatus -eq 'COMPLETE') {
    if (@($status.hardInvariants | Where-Object { $_.status -ne 'PASS' }).Count -gt 0) {
        Add-ValidationError 'overallStatus COMPLETE requires every hard invariant to PASS'
    }
    if ($openDeferredItems.Count -gt 0) {
        Add-ValidationError 'overallStatus COMPLETE forbids unresolved deferred items'
    }
}

if ($errors.Count -gt 0) {
    foreach ($validationError in $errors) {
        Write-Error $validationError
    }
    exit 1
}

Write-Output ("AGENT_ARCHITECTURE_STATUS_OK overall={0} deferred.open={1}" -f `
        $status.overallStatus, $openDeferredItems.Count)
