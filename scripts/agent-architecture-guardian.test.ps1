$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$skillPath = Join-Path $root '.agents\skills\agent-architecture-guardian\SKILL.md'
$skill = Get-Content -LiteralPath $skillPath -Raw

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

Write-Output 'AGENT_ARCHITECTURE_GUARDIAN_TESTS_OK tests=9'
