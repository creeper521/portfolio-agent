$ErrorActionPreference = 'Stop'
$path = Join-Path $PSScriptRoot 'assert-live-project-discussion-context.ps1'
$text = Get-Content -LiteralPath $path -Raw

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

Assert-True ($text -match '\[switch\]\$AuthorizeRealProvider') `
    'Live project discussion gate requires explicit authorization.'
Assert-True ($text -match "FromBase64String") `
    'Fixed synthetic inputs must be encoded in the script.'
Assert-True ($text -notmatch 'Write-(Output|Host).*Body') `
    'Live project discussion gate must not print response bodies.'
Assert-True ($text -notmatch 'ConvertTo-Json.*Write-(Output|Host)') `
    'Live project discussion gate must not print request payloads.'
Assert-True ($text -match 'PROJECT_DISCUSSION_PASS operation=') `
    'Live gate must emit only aggregate operation evidence.'
Assert-True ($text -match "kind = 'RESOLVE_CLARIFICATION'") `
    'Live gate must cover bounded selection recovery.'
Assert-True ($text -match "operation = 'ROUTE_IN_CONTEXT'") `
    'Live gate must cover locked in-context routing.'
Assert-True ($text -match "operation = 'EXIT_CONTEXT'") `
    'Live gate must cover explicit exit.'

Write-Output 'assert-live-project-discussion-context tests passed'
