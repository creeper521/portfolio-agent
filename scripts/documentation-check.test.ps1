$ErrorActionPreference = 'Stop'

$checker = Join-Path $PSScriptRoot 'documentation-check.ps1'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('portfolio-documentation-check-' + [guid]::NewGuid().ToString('N'))

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

function Write-Utf8File([string]$Path, [string]$Content) {
    $parent = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    [System.IO.File]::WriteAllText(
        $Path,
        $Content,
        [System.Text.UTF8Encoding]::new($false))
}

function Initialize-Fixture([string]$Path) {
    New-Item -ItemType Directory -Path $Path -Force | Out-Null

    $currentDocuments = @(
        'README.md',
        'AGENTS.md',
        'SECURITY.md',
        'docs/00-文档状态索引.md',
        'docs/04-项目代码约束.md',
        'docs/05-公开发布包契约.md',
        'docs/06-公开内容发布运行手册.md',
        'docs/08-当前实现状态.md',
        'docs/09-作品集资产库状态.md',
        'docs/10-本地PostgreSQL与pgvector运行手册.md',
        'docs/15-Agent 2.0真实交互问题清单与修复边界.md',
        'docs/16-Agent单权威持续收敛范式.md'
    )
    foreach ($relativePath in $currentDocuments) {
        Write-Utf8File (Join-Path $Path $relativePath) @"
# Current fixture
<!-- DOCUMENT_STATUS: CURRENT_AUTHORITY -->
"@
    }

    Write-Utf8File (Join-Path $Path 'README.md') @"
# Current fixture
<!-- DOCUMENT_STATUS: CURRENT_AUTHORITY -->

- ``POST /api/agent/turns``
- ``DELETE /api/agent/turns/{requestId}``
- ``GET /api/agent/conversations/current``
- ``DELETE /api/agent/conversations/current``

``PORTFOLIO_KNOWN_KEY`` is configured by the application.

<!-- RETIRED_CONTRACT_REFERENCES:BEGIN -->
不得恢复 ``POST /api/v2/answers``；旧 ``stp-v3`` 已删除。
| 历史合同 | ``ConversationAnswerResponse`` | 已取代 |
<!-- RETIRED_CONTRACT_REFERENCES:END -->
"@

    Write-Utf8File (Join-Path $Path 'docs/08-当前实现状态.md') @'
# Current snapshot fixture
<!-- DOCUMENT_STATUS: CURRENT_AUTHORITY -->

<!-- CURRENT_BUNDLE_SNAPSHOT:BEGIN -->
```text
schemaVersion=4.0
contentVersion=2026-08-05.1
projects=6
cases=52
claims=88
evidence=63
claimEvidenceLinks=88
timelineEvents=12
questionPresets=19
```
<!-- CURRENT_BUNDLE_SNAPSHOT:END -->
'@

    $historicalDocuments = @(
        'docs/01-项目背景.md',
        'docs/02-需求探索文档.md',
        'docs/03-可能技术选型.md',
        'docs/07-模块化单体后端审核记录.md',
        'docs/12-工程质量与未来优化评审备忘录.md',
        'docs/13-Agent对话体验与智能编排改造路线图.md',
        'docs/14-Agent架构债与防御性设计评审.md',
        'docs/superpowers/specs/example.md',
        'docs/superpowers/plans/example.md',
        'docs/reports/example.md',
        'docs/handoffs/example.md'
    )
    foreach ($relativePath in $historicalDocuments) {
        Write-Utf8File (Join-Path $Path $relativePath) @"
# Historical fixture
<!-- DOCUMENT_STATUS: HISTORICAL -->

The former endpoint was ``POST /api/v2/answers`` and used stp-v2.
"@
    }
    $activeSpec = `
        'docs/superpowers/specs/2026-08-19-agent-stabilization-and-repository-governance-design.md'
    $activePlan = `
        'docs/superpowers/plans/2026-08-19-agent-stabilization-and-repository-governance.md'
    Write-Utf8File (Join-Path $Path $activeSpec) @"
# Approved design fixture
<!-- DOCUMENT_STATUS: APPROVED -->

See [active plan](../plans/2026-08-19-agent-stabilization-and-repository-governance.md).
"@
    Write-Utf8File (Join-Path $Path $activePlan) @"
# Active plan fixture
<!-- DOCUMENT_STATUS: ACTIVE -->

See [approved spec](../specs/2026-08-19-agent-stabilization-and-repository-governance-design.md).
"@
    Add-Content -LiteralPath (Join-Path $Path 'docs/superpowers/specs/example.md') `
        -Encoding UTF8 -Value `
        "`nSee [plan](../plans/example.md?view=1#task) and [report][report-ref].`n`n[report-ref]: ../../reports/example.md"
    Add-Content -LiteralPath (Join-Path $Path 'docs/superpowers/specs/example.md') `
        -Encoding UTF8 -Value @'

Inline code is not a link: `[int][0]`.

```text
[not-a-link](../../../outside.md)
[string][0]
```
'@

    Write-Utf8File (Join-Path $Path 'docs/11-项目演进日志.md') @"
# Evolution fixture
<!-- DOCUMENT_STATUS: HISTORICAL -->

## 2026-08-18

### First event

See [guide](linked(dir)/guide.md?view=1#part) and [authority][authority-ref].

[authority-ref]: 00-文档状态索引.md "Authority"

## 2026-08-19

### Second event
"@
    Write-Utf8File (Join-Path $Path 'docs/linked(dir)/guide.md') '# Guide'

    Write-Utf8File (Join-Path $Path 'docs/agent-architecture-status.json') `
        '{"overallStatus":"IN_PROGRESS"}'
    Write-Utf8File (Join-Path $Path `
            'backend/src/main/resources/public-data/bundle/manifest.json') @'
{
  "schemaVersion": "4.0",
  "contentVersion": "2026-08-05.1",
  "counts": {
    "projects": 6,
    "cases": 52,
    "claims": 88,
    "evidence": 63,
    "claimEvidenceLinks": 88,
    "timelineEvents": 12,
    "questionPresets": 19
  }
}
'@
    Write-Utf8File (Join-Path $Path 'backend/src/main/resources/application.yml') `
        'example: ${PORTFOLIO_KNOWN_KEY:}'

    Write-Utf8File (Join-Path $Path `
            'backend/src/main/java/com/portfolio/agent/turn/api/AgentTurnController.java') @'
@RequestMapping("/api/agent/turns")
class AgentTurnController {
    @PostMapping
    public void create() {}
    @DeleteMapping("/{requestId}")
    public void cancel() {}
}
'@
    Write-Utf8File (Join-Path $Path `
            'backend/src/main/java/com/portfolio/agent/turn/api/AgentConversationController.java') @'
@RequestMapping("/api/agent/conversations/current")
class AgentConversationController {
    @GetMapping
    public void current() {}
    @DeleteMapping
    public void clear() {}
}
'@
}

function Invoke-Checker([string]$Path) {
    $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File $checker -RootPath $Path 2>&1 | Out-String
    $codes = @([regex]::Matches(
            $output,
            '(?m)^\[documentation-check\]\s+(?<code>[A-Z0-9_]+)\s') |
        ForEach-Object { $_.Groups['code'].Value })
    [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output = $output
        Codes = $codes
    }
}

function Assert-IssueSet(
        [string]$Name,
        [scriptblock]$Mutate,
        [string[]]$ExpectedCodes) {
    $casePath = Join-Path $fixtureRoot $Name
    Initialize-Fixture $casePath
    & $Mutate $casePath
    $result = Invoke-Checker $casePath
    $expected = @($ExpectedCodes | Sort-Object)
    $actual = @($result.Codes | Sort-Object)
    Assert-True ($result.ExitCode -ne 0) `
        "$Name should fail. Output: $($result.Output)"
    Assert-True (($expected -join ',') -eq ($actual -join ',')) `
        "$Name issue set mismatch. Expected=$($expected -join ',') Actual=$($actual -join ','). Output: $($result.Output)"
}

function Assert-Passes([string]$Name, [scriptblock]$Mutate) {
    $casePath = Join-Path $fixtureRoot $Name
    Initialize-Fixture $casePath
    & $Mutate $casePath
    $result = Invoke-Checker $casePath
    Assert-True ($result.ExitCode -eq 0) `
        "$Name should pass without issues. Output: $($result.Output)"
    Assert-True ($result.Codes.Count -eq 0) "$Name emitted unexpected issue codes."
}

try {
    foreach ($path in @($PSCommandPath, $checker)) {
        $parseErrors = $null
        [System.Management.Automation.Language.Parser]::ParseFile(
            $path, [ref]$null, [ref]$parseErrors) | Out-Null
        Assert-True ($parseErrors.Count -eq 0) `
            "PowerShell syntax errors in $path`: $($parseErrors.Message -join '; ')"
    }

    Assert-Passes 'positive' { param($path) }
    Assert-Passes 'negative-legacy' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path 'README.md') -Encoding UTF8 -Value @"

<!-- RETIRED_CONTRACT_REFERENCES:BEGIN -->
旧 ``/api/v2/answers`` 已移除，禁止恢复 ``stp-v1``。
``stp-v3`` 的生产默认切换仍不得实施。
<!-- RETIRED_CONTRACT_REFERENCES:END -->
"@
    }

    Assert-IssueSet 'duplicate-visible-snapshot-fact' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path 'README.md') -Encoding UTF8 `
            -Value "`n当前随包 Bundle 有 5 个 Project。"
    } @('BUNDLE_REFERENCE_UNMARKED')

    Assert-Passes 'marked-non-current-bundle-and-eval-counts' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path 'docs/05-公开发布包契约.md') `
            -Encoding UTF8 -Value @'

<!-- NON_CURRENT_BUNDLE_REFERENCE:BEGIN -->
historicalSchemaVersion=2.0
historicalContentVersion=2026-07-20.1
historicalProjects=5
This prose explains why the retired package remains documented.
<!-- NON_CURRENT_BUNDLE_REFERENCE:END -->
'@
        Add-Content -LiteralPath (Join-Path $path 'docs/08-当前实现状态.md') `
            -Encoding UTF8 -Value @'

Provider Eval manifest covers 21 Case fixtures.
<!-- NON_CURRENT_BUNDLE_REFERENCE:BEGIN -->
historicalSchemaVersion=3.0
This prose describes the retired Bundle without duplicating machine facts.
<!-- NON_CURRENT_BUNDLE_REFERENCE:END -->
'@
        Add-Content -LiteralPath (Join-Path $path 'docs/08-当前实现状态.md') `
            -Encoding UTF8 -Value `
            "`nThe API response schemaVersion=7 is a wire-contract revision."
    }

    Assert-IssueSet 'non-current-bundle-free-form-fact' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path 'docs/05-公开发布包契约.md') `
            -Encoding UTF8 -Value @'

<!-- NON_CURRENT_BUNDLE_REFERENCE:BEGIN -->
This CURRENT Bundle manifest uses schemaVersion=2.0.
<!-- NON_CURRENT_BUNDLE_REFERENCE:END -->
'@
    } @('NON_CURRENT_BUNDLE_FACT_FORMAT')

    foreach ($currentBundleClaim in @(
            '当前发布 schemaVersion=2.0。',
            '随包版本目前 contentVersion=2026-07-20.1。',
            '生产默认 manifest 包含 5 个 Project。')) {
        $caseName = 'unmarked-bundle-context-' + [guid]::NewGuid().ToString('N')
        Assert-IssueSet $caseName {
            param($path)
            Add-Content -LiteralPath (Join-Path $path 'README.md') `
                -Encoding UTF8 -Value "`n$currentBundleClaim"
        } @('BUNDLE_REFERENCE_UNMARKED')
    }

    Assert-IssueSet 'legacy-current' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path 'README.md') -Encoding UTF8 `
            -Value "`n当前唯一入口是 ``POST /api/v2/answers``。"
    } @('LEGACY_REFERENCE_UNMARKED')

    Assert-IssueSet 'legacy-current-table-row' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path 'README.md') -Encoding UTF8 -Value @"

<!-- RETIRED_CONTRACT_REFERENCES:BEGIN -->
| 已删除 | ``POST /api/v2/answers`` | 当前唯一入口 | ACTIVE |
<!-- RETIRED_CONTRACT_REFERENCES:END -->
"@
    } @('LEGACY_CURRENT_CLAIM')

    Assert-IssueSet 'snapshot-missing' {
        param($path)
        $file = Join-Path $path 'docs/08-当前实现状态.md'
        Write-Utf8File $file "# Current`n<!-- DOCUMENT_STATUS: CURRENT_AUTHORITY -->"
    } @('CURRENT_SNAPSHOT_BLOCK_MISSING')

    Assert-IssueSet 'snapshot-stale' {
        param($path)
        $file = Join-Path $path 'docs/08-当前实现状态.md'
        $content = Get-Content -LiteralPath $file -Raw -Encoding UTF8
        Write-Utf8File $file $content.Replace('projects=6', 'projects=5')
    } @('CURRENT_SNAPSHOT_BLOCK_MISMATCH')

    Assert-IssueSet 'current-positioning' {
        param($path)
        Write-Utf8File (Join-Path $path 'SECURITY.md') `
            "# Security`nThis says current authority only in prose."
    } @('POSITION_CURRENT_MISSING')

    Assert-IssueSet 'historical-positioning' {
        param($path)
        Write-Utf8File (Join-Path $path 'docs/superpowers/specs/example.md') `
            "# Old spec`nFormer API: POST /api/v2/answers."
    } @('POSITION_HISTORICAL_MISSING')

    Assert-IssueSet 'active-positioning' {
        param($path)
        Write-Utf8File (Join-Path $path `
                'docs/superpowers/specs/2026-08-19-agent-stabilization-and-repository-governance-design.md') @"
# Approved design without marker
"@
    } @('POSITION_ACTIVE_MISSING')

    Assert-IssueSet 'active-missing-link' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path `
                'docs/superpowers/plans/2026-08-19-agent-stabilization-and-repository-governance.md') `
            -Encoding UTF8 -Value "`n[missing](does-not-exist.md)"
    } @('ACTIVE_LINK_MISSING')

    Assert-IssueSet 'approved-spec-cannot-be-active' {
        param($path)
        $file = Join-Path $path `
            'docs/superpowers/specs/2026-08-19-agent-stabilization-and-repository-governance-design.md'
        $content = Get-Content -LiteralPath $file -Raw -Encoding UTF8
        Write-Utf8File $file $content.Replace(
            'DOCUMENT_STATUS: APPROVED',
            'DOCUMENT_STATUS: ACTIVE')
    } @('POSITION_ACTIVE_STATUS_MISMATCH')

    Assert-IssueSet 'active-plan-cannot-be-approved' {
        param($path)
        $file = Join-Path $path `
            'docs/superpowers/plans/2026-08-19-agent-stabilization-and-repository-governance.md'
        $content = Get-Content -LiteralPath $file -Raw -Encoding UTF8
        Write-Utf8File $file $content.Replace(
            'DOCUMENT_STATUS: ACTIVE',
            'DOCUMENT_STATUS: APPROVED')
    } @('POSITION_ACTIVE_STATUS_MISMATCH')

    Assert-IssueSet 'readme-resource-missing' {
        param($path)
        $file = Join-Path $path 'README.md'
        $content = Get-Content -LiteralPath $file -Raw -Encoding UTF8
        Write-Utf8File $file ($content -replace `
                '(?m)^-\s+`DELETE /api/agent/turns/\{requestId\}`\s*$', '')
    } @('AGENT_RESOURCE_README_MISSING')

    Assert-IssueSet 'source-resource-extra' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path `
                'backend/src/main/java/com/portfolio/agent/turn/api/AgentTurnController.java') `
            -Encoding UTF8 -Value "`n    @GetMapping(`"/debug`")`n    public void debug() {}"
    } @('AGENT_RESOURCE_SOURCE_EXTRA')

    Assert-IssueSet 'source-resource-extra-inline' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path `
                'backend/src/main/java/com/portfolio/agent/turn/api/AgentTurnController.java') `
            -Encoding UTF8 -Value `
            "`n    @PatchMapping(`"/debug`") public void debugInline() {}"
    } @('AGENT_RESOURCE_SOURCE_EXTRA')

    Assert-IssueSet 'source-resource-extra-multiline' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path `
                'backend/src/main/java/com/portfolio/agent/turn/api/AgentTurnController.java') `
            -Encoding UTF8 -Value @'

    @PutMapping(
        path = "/multiline"
    )
    public void multiline() {}
'@
    } @('AGENT_RESOURCE_SOURCE_EXTRA')

    Assert-Passes 'commented-source-route' {
        param($path)
        Write-Utf8File (Join-Path $path `
                'backend/src/main/java/com/portfolio/agent/turn/api/CommentedController.java') @'
/*
@RequestMapping("/api/agent/commented")
class CommentedController {
    @GetMapping("/debug")
    public void debug() {}
}
*/
'@
    }

    Assert-IssueSet 'unknown-readme-environment' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path 'README.md') -Encoding UTF8 `
            -Value "`n``PORTFOLIO_UNKNOWN_CURRENT_KEY``"
    } @('README_ENV_KEY_UNKNOWN')

    Assert-IssueSet 'duplicate-date' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path 'docs/11-项目演进日志.md') `
            -Encoding UTF8 -Value "`n## 2026-08-19`n`n### Duplicate"
    } @('DOCS11_DATE_DUPLICATE')

    Assert-IssueSet 'decreasing-date' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path 'docs/11-项目演进日志.md') `
            -Encoding UTF8 -Value "`n## 2026-08-17`n`n### Older"
    } @('DOCS11_DATE_ORDER')

    Assert-IssueSet 'malformed-date' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path 'docs/11-项目演进日志.md') `
            -Encoding UTF8 -Value "`n## 2026-13-40"
    } @('DOCS11_DATE_INVALID')

    foreach ($heading in @('## 2026/08/19', '## 19-08-2026', '## 状态说明')) {
        $caseName = 'invalid-h2-' + [guid]::NewGuid().ToString('N')
        Assert-IssueSet $caseName {
            param($path)
            Add-Content -LiteralPath (Join-Path $path `
                    'docs/11-项目演进日志.md') `
                -Encoding UTF8 -Value "`n$heading"
        } @('DOCS11_H2_INVALID')
    }

    $numberedHeadings = @(
        '### 1. Event',
        '### P1 Event',
        '### Task 8',
        '### FE-2',
        '### 阶段一',
        '### 一、事件',
        '### （一）事件'
    )
    for ($index = 0; $index -lt $numberedHeadings.Count; $index++) {
        $heading = $numberedHeadings[$index]
        Assert-IssueSet "numbered-heading-$index" {
            param($path)
            Add-Content -LiteralPath (Join-Path $path `
                    'docs/11-项目演进日志.md') `
                -Encoding UTF8 -Value "`n$heading"
        } @('DOCS11_NUMBERED_HEADING')
    }

    Assert-IssueSet 'missing-inline-link' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path 'docs/11-项目演进日志.md') `
            -Encoding UTF8 -Value "`n[missing](does-not-exist.md?view=1#x)"
    } @('DOCS11_LINK_MISSING')

    Assert-IssueSet 'outside-root-link' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path 'docs/11-项目演进日志.md') `
            -Encoding UTF8 -Value "`n[outside](../../outside.md)"
    } @('DOCS11_LINK_OUTSIDE_ROOT')

    Assert-IssueSet 'malformed-inline-link' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path 'docs/11-项目演进日志.md') `
            -Encoding UTF8 -Value "`n[broken](target.md"
    } @('DOCS11_LINK_PARSE')

    Assert-IssueSet 'missing-reference-link' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path 'docs/11-项目演进日志.md') `
            -Encoding UTF8 -Value "`n[missing][unknown-reference]"
    } @('DOCS11_LINK_REFERENCE_MISSING')

    Assert-IssueSet 'historical-missing-link' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path 'docs/reports/example.md') `
            -Encoding UTF8 -Value "`n[missing](does-not-exist.md)"
    } @('HISTORICAL_LINK_MISSING')

    Assert-IssueSet 'historical-outside-root-link' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path 'docs/handoffs/example.md') `
            -Encoding UTF8 -Value "`n[outside](../../../outside.md)"
    } @('HISTORICAL_LINK_OUTSIDE_ROOT')

    Assert-IssueSet 'fixed-historical-missing-link' {
        param($path)
        Add-Content -LiteralPath (Join-Path $path `
                'docs/12-工程质量与未来优化评审备忘录.md') `
            -Encoding UTF8 -Value "`n[missing](does-not-exist.md)"
    } @('HISTORICAL_LINK_MISSING')

    Write-Output 'Documentation checker tests passed.'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
}
