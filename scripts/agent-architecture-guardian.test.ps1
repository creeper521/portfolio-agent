$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$skillPath = Join-Path $root '.agents\skills\agent-architecture-guardian\SKILL.md'
$metadataPath = Join-Path $root `
    '.agents\skills\agent-architecture-guardian\agents\openai.yaml'
$agentsPath = Join-Path $root 'AGENTS.md'
$paradigmFiles = @(Get-ChildItem -LiteralPath (Join-Path $root 'docs') `
        -File -Filter '16-*')
if ($paradigmFiles.Count -ne 1) {
    throw 'expected exactly one docs/16-* architecture paradigm file'
}
$paradigmPath = $paradigmFiles[0].FullName
$skill = Get-Content -LiteralPath $skillPath -Raw
$metadata = Get-Content -LiteralPath $metadataPath -Raw
$agents = Get-Content -LiteralPath $agentsPath -Raw
$paradigm = Get-Content -LiteralPath $paradigmPath -Raw

function Assert-Matches([string]$pattern, [string]$message) {
    if ($skill -notmatch $pattern) { throw $message }
}

function Assert-DoesNotMatch([string]$pattern, [string]$message) {
    if ($skill -match $pattern) { throw $message }
}

$frontmatterMatch = [regex]::Match($skill, '(?s)^---\r?\n(?<body>.*?)\r?\n---')
if (-not $frontmatterMatch.Success) { throw 'SKILL.md frontmatter is missing' }
$frontmatterKeys = @([regex]::Matches(
        $frontmatterMatch.Groups['body'].Value,
        '(?m)^(?<key>[a-z][a-z0-9_-]*):') | ForEach-Object { $_.Groups['key'].Value })
if (@($frontmatterKeys | Sort-Object).Count -ne 2 -or
        'name' -notin $frontmatterKeys -or 'description' -notin $frontmatterKeys) {
    throw 'frontmatter must contain only name and description'
}

Assert-Matches '(?m)^description: Use when ' `
    'description must start with Use when'
Assert-Matches '(?m)^description: Use when starting every conversation in this Portfolio Agent repository' `
    'skill must default to every conversation in this project'
Assert-Matches '(?im)^## Bootstrap$' `
    'skill must define a lightweight Bootstrap before loading architecture context'
Assert-Matches 'NOT_APPLICABLE' `
    'bootstrap must support an immediate non-architecture exit'
Assert-Matches 'Do not read the architecture documents or run the status checker' `
    'NOT_APPLICABLE must avoid the full architecture workflow'
Assert-Matches 'Level 1 and Level 2.*continue.*without waiting' `
    'ordinary changes must continue without repeated confirmation'
Assert-Matches 'approved Level 3.*continue' `
    'approved architecture replacement must continue without repeated approval'
Assert-Matches '(?im)^## Architecture Review$' `
    'skill must define an Architecture Review mode'
Assert-Matches 'Protect constraints, not incumbent implementations\.' `
    'skill must protect constraints rather than the current implementation'
Assert-Matches 'Do not treat an approved architecture as immutable\.' `
    'skill must explicitly permit evidence-driven architecture replacement'
Assert-Matches 'isolated prototype' `
    'skill must permit non-production prototypes during architecture review'
Assert-Matches 'repeated workarounds|recurring waivers|cross-layer branches' `
    'skill must name observable architecture-review triggers'
Assert-DoesNotMatch 'does not reopen an approved architecture' `
    'skill must not freeze an approved architecture'
Assert-DoesNotMatch 'stop only the conflicting expansion' `
    'skill must not block evidence-driven review with the old stop rule'
Assert-Matches '(?im)^## Handle Guardian Drift$' `
    'skill must define a lightweight drift escape hatch'
Assert-Matches 'newer code, passing tests, or an approved design' `
    'skill must recognize newer repository evidence over a stale rule'
Assert-Matches 'GUARDIAN_DRIFT' `
    'skill must reuse the existing deferred ledger for drift'
Assert-Matches 'Do not create a separate drift ledger or lifecycle state machine' `
    'skill must keep drift handling lightweight'
Assert-Matches 'Do not weaken privacy boundaries' `
    'skill drift handling must preserve privacy boundaries'

if ($metadata -notmatch '(?m)^policy:\r?\n\s+allow_implicit_invocation: true$') {
    throw 'openai.yaml must explicitly allow implicit invocation'
}
if ($agents -notmatch '(?im)^### Default Agent architecture guardian bootstrap$') {
    throw 'AGENTS.md must require the project-level default bootstrap'
}
if ($agents -notmatch 'NOT_APPLICABLE.*continue immediately') {
    throw 'AGENTS.md must preserve the fast non-architecture exit'
}
if ($agents -notmatch 'GUARDIAN_DRIFT.*deferredItems') {
    throw 'AGENTS.md must route stale Guardian rules through the existing ledger'
}
if ($paradigm -notmatch '(?im)^### 默认轻量 Bootstrap$' -or
        $paradigm -notmatch 'NOT_APPLICABLE.*立即继续') {
    throw 'the architecture paradigm must document the default lightweight bootstrap'
}
if ($paradigm -notmatch 'GUARDIAN_DRIFT.*deferredItems') {
    throw 'the architecture paradigm must document lightweight Guardian drift handling'
}

Write-Output 'AGENT_ARCHITECTURE_GUARDIAN_TESTS_OK tests=25'
