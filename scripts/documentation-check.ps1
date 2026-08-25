param(
    [string]$RootPath = ''
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($RootPath)) {
    $RootPath = Split-Path -Parent $PSScriptRoot
}
$resolvedRoot = (Resolve-Path -LiteralPath $RootPath -ErrorAction Stop).Path
$rootPrefix = $resolvedRoot.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
$issues = [System.Collections.Generic.List[object]]::new()

$currentAuthorityFiles = @(
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
$machineAuthorityFiles = @('docs/agent-architecture-status.json')
$historicalFiles = @(
    'docs/01-项目背景.md',
    'docs/02-需求探索文档.md',
    'docs/03-可能技术选型.md',
    'docs/07-模块化单体后端审核记录.md',
    'docs/11-项目演进日志.md',
    'docs/12-工程质量与未来优化评审备忘录.md',
    'docs/13-Agent对话体验与智能编排改造路线图.md',
    'docs/14-Agent架构债与防御性设计评审.md'
)
$historicalDirectories = @(
    'docs/superpowers/specs',
    'docs/superpowers/plans',
    'docs/reports',
    'docs/handoffs'
)
$activeWorkArtifactStatuses = [ordered]@{
    'docs/superpowers/specs/2026-08-19-agent-stabilization-and-repository-governance-design.md' = 'APPROVED'
    'docs/superpowers/plans/2026-08-19-agent-stabilization-and-repository-governance.md' = 'ACTIVE'
    'docs/superpowers/specs/2026-08-20-general-answer-language-and-depth-prompt-design.md' = 'APPROVED'
    'docs/superpowers/plans/2026-08-20-general-answer-language-and-depth-prompt.md' = 'ACTIVE'
    'docs/superpowers/specs/2026-08-20-project-discussion-context-design.md' = 'APPROVED'
    'docs/superpowers/plans/2026-08-20-project-discussion-context.md' = 'ACTIVE'
    'docs/superpowers/specs/2026-08-21-agent-failure-recovery-and-discussion-completion-design.md' = 'APPROVED'
    'docs/superpowers/plans/2026-08-21-agent-failure-recovery-and-discussion-completion.md' = 'ACTIVE'
    'docs/superpowers/specs/2026-08-21-portfolio-public-api-convergence-design.md' = 'APPROVED'
    'docs/superpowers/plans/2026-08-21-portfolio-public-api-convergence.md' = 'ACTIVE'
    'docs/superpowers/specs/2026-08-21-configured-user-selectable-model-catalog-design.md' = 'APPROVED'
    'docs/superpowers/plans/2026-08-21-configured-user-selectable-model-catalog.md' = 'ACTIVE'
    'docs/superpowers/specs/2026-08-24-agent-model-selection-frontend-ui-design.md' = 'APPROVED'
    'docs/superpowers/specs/2026-08-25-audience-role-session-switching-design.md' = 'APPROVED'
    'docs/superpowers/plans/2026-08-25-audience-role-behavior-foundation.md' = 'ACTIVE'
}
$activeWorkArtifactFiles = @($activeWorkArtifactStatuses.Keys)

function Add-Issue(
        [string]$Code,
        [string]$RelativePath,
        [int]$LineNumber,
        [string]$Message) {
    $issues.Add([pscustomobject]@{
            Code = $Code
            Path = $RelativePath.Replace('\', '/')
            Line = $LineNumber
            Message = $Message
        }) | Out-Null
}

function Get-RelativePath([string]$FullPath) {
    $rootUri = [System.Uri]::new($rootPrefix)
    $fileUri = [System.Uri]::new($FullPath)
    [System.Uri]::UnescapeDataString(
        $rootUri.MakeRelativeUri($fileUri).ToString()).Replace('/', '\')
}

function Get-Document([string]$RelativePath) {
    $fullPath = Join-Path $resolvedRoot $RelativePath
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        Add-Issue 'DOCUMENT_MISSING' $RelativePath 0 'Required documentation file does not exist.'
        return $null
    }
    $text = Get-Content -LiteralPath $fullPath -Raw -Encoding UTF8
    [pscustomobject]@{
        RelativePath = $RelativePath
        FullPath = $fullPath
        Text = $text
        Lines = @($text -split "`r?`n")
    }
}

function Get-LineNumber([string]$Text, [int]$Index) {
    if ($Index -le 0) {
        return 1
    }
    return ([regex]::Matches($Text.Substring(0, $Index), "`n").Count + 1)
}

function Remove-JavaComments([string]$Text) {
    $builder = [System.Text.StringBuilder]::new($Text.Length)
    $state = 'CODE'
    for ($index = 0; $index -lt $Text.Length; $index++) {
        $character = $Text[$index]
        $next = if ($index + 1 -lt $Text.Length) { $Text[$index + 1] } else { [char]0 }
        switch ($state) {
            'CODE' {
                if ($character -eq '/' -and $next -eq '/') {
                    [void]$builder.Append('  ')
                    $index++
                    $state = 'LINE_COMMENT'
                }
                elseif ($character -eq '/' -and $next -eq '*') {
                    [void]$builder.Append('  ')
                    $index++
                    $state = 'BLOCK_COMMENT'
                }
                else {
                    [void]$builder.Append($character)
                    if ($character -eq '"') { $state = 'STRING' }
                    elseif ($character -eq "'") { $state = 'CHAR' }
                }
            }
            'LINE_COMMENT' {
                if ($character -eq "`n" -or $character -eq "`r") {
                    [void]$builder.Append($character)
                    $state = 'CODE'
                }
                else {
                    [void]$builder.Append(' ')
                }
            }
            'BLOCK_COMMENT' {
                if ($character -eq '*' -and $next -eq '/') {
                    [void]$builder.Append('  ')
                    $index++
                    $state = 'CODE'
                }
                elseif ($character -eq "`n" -or $character -eq "`r") {
                    [void]$builder.Append($character)
                }
                else {
                    [void]$builder.Append(' ')
                }
            }
            'STRING' {
                [void]$builder.Append($character)
                if ($character -eq '\' -and $index + 1 -lt $Text.Length) {
                    [void]$builder.Append($next)
                    $index++
                }
                elseif ($character -eq '"') {
                    $state = 'CODE'
                }
            }
            'CHAR' {
                [void]$builder.Append($character)
                if ($character -eq '\' -and $index + 1 -lt $Text.Length) {
                    [void]$builder.Append($next)
                    $index++
                }
                elseif ($character -eq "'") {
                    $state = 'CODE'
                }
            }
        }
    }
    return $builder.ToString()
}

function Get-LinkDestination([string]$RawDestination) {
    $candidate = $RawDestination.Trim()
    if ([string]::IsNullOrWhiteSpace($candidate)) {
        return [pscustomobject]@{ Target = $null; Error = 'Link destination is empty.' }
    }
    if ($candidate.StartsWith('<')) {
        $close = $candidate.IndexOf('>')
        if ($close -lt 1) {
            return [pscustomobject]@{ Target = $null; Error = 'Angle-bracket link destination is not closed.' }
        }
        return [pscustomobject]@{ Target = $candidate.Substring(1, $close - 1); Error = $null }
    }
    $depth = 0
    $end = $candidate.Length
    for ($index = 0; $index -lt $candidate.Length; $index++) {
        $character = $candidate[$index]
        if ($character -eq '(') {
            $depth++
        }
        elseif ($character -eq ')' -and $depth -gt 0) {
            $depth--
        }
        elseif ([char]::IsWhiteSpace($character) -and $depth -eq 0) {
            $end = $index
            break
        }
    }
    return [pscustomobject]@{
        Target = $candidate.Substring(0, $end).Replace('\ ', ' ')
        Error = $null
    }
}

function Test-LocalLinkTarget(
        [string]$RawDestination,
        [string]$SourceRelativePath,
        [string]$SourceFullPath,
        [int]$LineNumber,
        [string]$CodePrefix) {
    $destination = Get-LinkDestination $RawDestination
    if ($null -ne $destination.Error) {
        Add-Issue "${CodePrefix}_LINK_PARSE" $SourceRelativePath $LineNumber $destination.Error
        return
    }
    $target = [string]$destination.Target
    if ($target -match '^(?i)(?:https?|mailto):' -or $target.StartsWith('#')) {
        return
    }
    $pathOnly = ($target -split '[?#]', 2)[0]
    if ([string]::IsNullOrWhiteSpace($pathOnly)) {
        return
    }
    try {
        $decodedPath = [System.Uri]::UnescapeDataString($pathOnly)
        $candidate = if ([System.IO.Path]::IsPathRooted($decodedPath)) {
            [System.IO.Path]::GetFullPath($decodedPath)
        }
        else {
            [System.IO.Path]::GetFullPath(
                (Join-Path (Split-Path -Parent $SourceFullPath) $decodedPath))
        }
    }
    catch {
        Add-Issue "${CodePrefix}_LINK_PARSE" $SourceRelativePath $LineNumber `
            "Cannot canonicalize link target '$target': $($_.Exception.Message)"
        return
    }
    if (-not $candidate.StartsWith(
            $rootPrefix,
            [System.StringComparison]::OrdinalIgnoreCase) -and
            $candidate -ne $resolvedRoot) {
        Add-Issue "${CodePrefix}_LINK_OUTSIDE_ROOT" $SourceRelativePath $LineNumber `
            "Local link target '$target' escapes the repository root."
        return
    }
    if (-not (Test-Path -LiteralPath $candidate)) {
        Add-Issue "${CodePrefix}_LINK_MISSING" $SourceRelativePath $LineNumber `
            "Local link target '$target' does not exist."
    }
}

function Test-DocumentLinks([object]$Document, [string]$CodePrefix) {
    $parseLines = [System.Collections.Generic.List[string]]::new()
    $fenceCharacter = $null
    foreach ($sourceLine in $Document.Lines) {
        $fence = [regex]::Match($sourceLine, '^\s*(?<fence>`{3,}|~{3,})')
        if ($fence.Success) {
            $currentFenceCharacter = $fence.Groups['fence'].Value.Substring(0, 1)
            if ($null -eq $fenceCharacter) {
                $fenceCharacter = $currentFenceCharacter
            }
            elseif ($fenceCharacter -eq $currentFenceCharacter) {
                $fenceCharacter = $null
            }
            $parseLines.Add('') | Out-Null
            continue
        }
        if ($null -ne $fenceCharacter) {
            $parseLines.Add('') | Out-Null
            continue
        }
        $withoutInlineCode = if ($sourceLine.Contains('`')) {
            [regex]::Replace(
                $sourceLine,
                '`+[^`]*`+',
                { param($match) ' ' * $match.Length })
        }
        else {
            $sourceLine
        }
        $parseLines.Add($withoutInlineCode) | Out-Null
    }
    $referenceDefinitions = @{}
    for ($lineIndex = 0; $lineIndex -lt $parseLines.Count; $lineIndex++) {
        $definition = [regex]::Match(
            $parseLines[$lineIndex],
            '^\s{0,3}\[(?<id>[^\]]+)\]:\s*(?<destination>.+?)\s*$')
        if ($definition.Success) {
            $referenceDefinitions[$definition.Groups['id'].Value.ToLowerInvariant()] = `
                [pscustomobject]@{
                    Destination = $definition.Groups['destination'].Value
                    Line = $lineIndex + 1
                }
        }
    }
    foreach ($reference in $referenceDefinitions.Values) {
        Test-LocalLinkTarget $reference.Destination $Document.RelativePath `
            $Document.FullPath $reference.Line $CodePrefix
    }
    for ($lineIndex = 0; $lineIndex -lt $parseLines.Count; $lineIndex++) {
        $line = $parseLines[$lineIndex]
        $searchIndex = 0
        while ($searchIndex -lt $line.Length) {
            $delimiter = $line.IndexOf('](', $searchIndex, [System.StringComparison]::Ordinal)
            if ($delimiter -lt 0) {
                break
            }
            $opening = $line.LastIndexOf('[', $delimiter)
            if ($opening -lt 0) {
                $searchIndex = $delimiter + 2
                continue
            }
            $destinationStart = $delimiter + 2
            $depth = 0
            $closing = -1
            for ($characterIndex = $destinationStart;
                    $characterIndex -lt $line.Length;
                    $characterIndex++) {
                $character = $line[$characterIndex]
                if ($character -eq '(') {
                    $depth++
                }
                elseif ($character -eq ')') {
                    if ($depth -eq 0) {
                        $closing = $characterIndex
                        break
                    }
                    $depth--
                }
            }
            if ($closing -lt 0) {
                Add-Issue "${CodePrefix}_LINK_PARSE" $Document.RelativePath `
                    ($lineIndex + 1) 'Inline Markdown link is missing its closing parenthesis.'
                break
            }
            $rawDestination = $line.Substring(
                $destinationStart,
                $closing - $destinationStart)
            Test-LocalLinkTarget $rawDestination $Document.RelativePath `
                $Document.FullPath ($lineIndex + 1) $CodePrefix
            $searchIndex = $closing + 1
        }

        $referenceLinks = [regex]::Matches(
            $line,
            '(?<!\!)\[(?<text>[^\]]+)\]\[(?<id>[^\]]*)\]')
        foreach ($referenceLink in $referenceLinks) {
            $referenceId = $referenceLink.Groups['id'].Value
            if ([string]::IsNullOrWhiteSpace($referenceId)) {
                $referenceId = $referenceLink.Groups['text'].Value
            }
            $normalizedId = $referenceId.ToLowerInvariant()
            if (-not $referenceDefinitions.ContainsKey($normalizedId)) {
                Add-Issue "${CodePrefix}_LINK_REFERENCE_MISSING" `
                    $Document.RelativePath ($lineIndex + 1) `
                    "Reference link '$referenceId' has no definition."
                continue
            }
        }
    }
}

function Get-ControllerRoutes([string]$SourceDirectory) {
    $routes = [System.Collections.Generic.List[object]]::new()
    if (-not (Test-Path -LiteralPath $SourceDirectory -PathType Container)) {
        Add-Issue 'AGENT_CONTROLLER_DIRECTORY_MISSING' `
            'backend/src/main/java' 0 'Backend Java source directory does not exist.'
        return $routes
    }
    foreach ($file in Get-ChildItem -LiteralPath $SourceDirectory -File `
            -Filter '*Controller.java' -Recurse) {
        $rawText = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
        $text = Remove-JavaComments $rawText
        $baseMatch = [regex]::Match(
            $text,
            '(?ms)^\s*@RequestMapping\(\s*(?:(?:value|path)\s*=\s*)?' +
            '"(?<path>/api/agent[^"]*)"\s*\)')
        if (-not $baseMatch.Success) {
            continue
        }
        $basePath = $baseMatch.Groups['path'].Value.TrimEnd('/')
        $methodPattern = `
            '(?msx)^\s*@(?<verb>Get|Post|Delete|Put|Patch)Mapping\b\s*' +
            '(?:\(\s*(?<args>.*?)\s*\))?\s*' +
            '(?:@[A-Za-z0-9_$.]+(?:\s*\(.*?\))?\s*)*' +
            'public\s+(?:static\s+)?(?:final\s+)?' +
            '[A-Za-z0-9_$.<>,?\[\]\s]+?\s+' +
            '(?<method>[A-Za-z_$][A-Za-z0-9_$]*)\s*\('
        foreach ($mapping in [regex]::Matches($text, $methodPattern)) {
            $methodName = $mapping.Groups['method'].Value
            $verb = $mapping.Groups['verb'].Value.ToUpperInvariant()
            $arguments = $mapping.Groups['args'].Value
            $pathMatch = [regex]::Match(
                $arguments,
                '(?:value\s*=\s*|path\s*=\s*)?"(?<path>[^"]*)"')
            $methodPath = if ($pathMatch.Success) {
                $pathMatch.Groups['path'].Value
            }
            else {
                ''
            }
            $fullPath = ($basePath + '/' + $methodPath.TrimStart('/')).TrimEnd('/')
            $routes.Add([pscustomobject]@{
                    Key = "$verb $fullPath"
                    Method = $methodName
                    RelativePath = Get-RelativePath $file.FullName
                    Line = Get-LineNumber $text $mapping.Index
                }) | Out-Null
        }
    }
    return $routes
}

$currentDocuments = [System.Collections.Generic.List[object]]::new()
foreach ($relativePath in $currentAuthorityFiles) {
    $document = Get-Document $relativePath
    if ($null -ne $document) {
        $currentDocuments.Add($document) | Out-Null
    }
}
foreach ($relativePath in $machineAuthorityFiles) {
    [void](Get-Document $relativePath)
}

$allHistoricalPaths = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::OrdinalIgnoreCase)
$activeWorkArtifactSet = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::OrdinalIgnoreCase)
foreach ($relativePath in $activeWorkArtifactFiles) {
    [void]$activeWorkArtifactSet.Add($relativePath.Replace('\', '/'))
}
foreach ($relativePath in $historicalFiles) {
    [void]$allHistoricalPaths.Add($relativePath)
}
foreach ($relativeDirectory in $historicalDirectories) {
    $fullDirectory = Join-Path $resolvedRoot $relativeDirectory
    if (-not (Test-Path -LiteralPath $fullDirectory -PathType Container)) {
        continue
    }
    foreach ($file in Get-ChildItem -LiteralPath $fullDirectory -File -Filter '*.md' -Recurse) {
        $relativePath = Get-RelativePath $file.FullName
        if (-not $activeWorkArtifactSet.Contains($relativePath.Replace('\', '/'))) {
            [void]$allHistoricalPaths.Add($relativePath)
        }
    }
}
$historicalDocuments = [System.Collections.Generic.List[object]]::new()
foreach ($relativePath in $allHistoricalPaths) {
    $document = Get-Document $relativePath
    if ($null -ne $document) {
        $historicalDocuments.Add($document) | Out-Null
    }
}
$activeWorkArtifacts = [System.Collections.Generic.List[object]]::new()
foreach ($relativePath in $activeWorkArtifactFiles) {
    $document = Get-Document $relativePath
    if ($null -ne $document) {
        $activeWorkArtifacts.Add($document) | Out-Null
    }
}

# 文档定位使用机器标记，不从自然语言中猜测“当前”或“历史”。
foreach ($document in $currentDocuments) {
    $header = ($document.Lines | Select-Object -First 8) -join "`n"
    if ($header -notmatch '<!--\s*DOCUMENT_STATUS:\s*CURRENT_AUTHORITY\s*-->') {
        Add-Issue 'POSITION_CURRENT_MISSING' $document.RelativePath 1 `
            'Header must contain <!-- DOCUMENT_STATUS: CURRENT_AUTHORITY -->.'
    }
}
foreach ($document in $historicalDocuments) {
    $header = ($document.Lines | Select-Object -First 8) -join "`n"
    if ($header -notmatch `
            '<!--\s*DOCUMENT_STATUS:\s*(?:HISTORICAL|SUPERSEDED|NON_AUTHORITATIVE)\s*-->') {
        Add-Issue 'POSITION_HISTORICAL_MISSING' $document.RelativePath 1 `
            'Header must contain a formal HISTORICAL, SUPERSEDED, or NON_AUTHORITATIVE marker.'
    }
}
foreach ($document in $activeWorkArtifacts) {
    $header = ($document.Lines | Select-Object -First 8) -join "`n"
    $expectedStatus = [string]$activeWorkArtifactStatuses[$document.RelativePath]
    $statusMarker = [regex]::Match(
        $header,
        '<!--\s*DOCUMENT_STATUS:\s*(?<status>[A-Z_]+)\s*-->')
    if (-not $statusMarker.Success) {
        Add-Issue 'POSITION_ACTIVE_MISSING' $document.RelativePath 1 `
            "Allowlisted work artifact must declare DOCUMENT_STATUS: $expectedStatus."
    }
    elseif ($statusMarker.Groups['status'].Value -ne $expectedStatus) {
        Add-Issue 'POSITION_ACTIVE_STATUS_MISMATCH' $document.RelativePath 1 `
            "Expected DOCUMENT_STATUS: $expectedStatus but found $($statusMarker.Groups['status'].Value)."
    }
}

# 当前文档对旧合同的任何引用都必须位于显式 retired 区块。
# 区块只能说明移除事实；其中仍出现 CURRENT/ACTIVE 声明依然失败。
$legacyPatterns = @(
    @{ Pattern = '(?i)/api/v2(?:/answers|/conversation-context)?'; Name = '/api/v2' },
    @{ Pattern = '(?i)\bstp-v[123]\b'; Name = 'stp-v1/v2/v3' },
    @{ Pattern = '\bConversationAnswerResponse\b'; Name = 'ConversationAnswerResponse' }
)
foreach ($document in $currentDocuments) {
    $insideRetiredReferences = $false
    for ($lineIndex = 0; $lineIndex -lt $document.Lines.Count; $lineIndex++) {
        $line = $document.Lines[$lineIndex]
        if ($line -match '<!--\s*RETIRED_CONTRACT_REFERENCES:BEGIN\s*-->') {
            if ($insideRetiredReferences) {
                Add-Issue 'RETIRED_REFERENCE_BLOCK_INVALID' $document.RelativePath `
                    ($lineIndex + 1) 'Retired-reference blocks cannot be nested.'
            }
            $insideRetiredReferences = $true
            continue
        }
        if ($line -match '<!--\s*RETIRED_CONTRACT_REFERENCES:END\s*-->') {
            if (-not $insideRetiredReferences) {
                Add-Issue 'RETIRED_REFERENCE_BLOCK_INVALID' $document.RelativePath `
                    ($lineIndex + 1) 'Retired-reference block ends without a matching BEGIN.'
            }
            $insideRetiredReferences = $false
            continue
        }
        foreach ($legacy in $legacyPatterns) {
            if ($line -notmatch $legacy.Pattern) {
                continue
            }
            if (-not $insideRetiredReferences) {
                Add-Issue 'LEGACY_REFERENCE_UNMARKED' $document.RelativePath ($lineIndex + 1) `
                    "Retired contract '$($legacy.Name)' must be inside a retired-reference block."
                continue
            }
            if ($line -match `
                    '(?i)(\bCURRENT\b|\bACTIVE\b|\bPRODUCTION\b|' +
                    '当前(?:唯一|正式|生产|运行|入口|使用)|' +
                    '现为|仍是.{0,16}入口|唯一.{0,16}入口)') {
                Add-Issue 'LEGACY_CURRENT_CLAIM' $document.RelativePath ($lineIndex + 1) `
                    "Retired-reference row for '$($legacy.Name)' also declares an active contract."
            }
        }
    }
    if ($insideRetiredReferences) {
        Add-Issue 'RETIRED_REFERENCE_BLOCK_INVALID' $document.RelativePath `
            $document.Lines.Count 'Retired-reference block is missing its END marker.'
    }
}

# docs/08 使用可见的规范 text 代码块作为唯一快照。
# 历史 schema 与非当前 Bundle 事实必须放入显式 NON_CURRENT 区块；Eval 用例数不属于 Bundle 快照。
$manifestRelativePath = 'backend/src/main/resources/public-data/bundle/manifest.json'
$manifestPath = Join-Path $resolvedRoot $manifestRelativePath
$manifest = $null
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    Add-Issue 'MANIFEST_MISSING' $manifestRelativePath 0 'Public Bundle manifest does not exist.'
}
else {
    try {
        $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 |
            ConvertFrom-Json
    }
    catch {
        Add-Issue 'MANIFEST_INVALID' $manifestRelativePath 1 $_.Exception.Message
    }
}
$docs08RelativePath = 'docs/08-当前实现状态.md'
$docs08 = $currentDocuments |
    Where-Object { $_.RelativePath -eq $docs08RelativePath } |
    Select-Object -First 1
if ($null -ne $manifest -and $null -ne $docs08) {
    $snapshotPattern = `
        '(?s)<!--\s*CURRENT_BUNDLE_SNAPSHOT:BEGIN\s*-->\s*' +
        '```text\s*(?<body>.*?)\s*```\s*' +
        '<!--\s*CURRENT_BUNDLE_SNAPSHOT:END\s*-->'
    $snapshotMatches = [regex]::Matches($docs08.Text, $snapshotPattern)
    if ($snapshotMatches.Count -ne 1) {
        Add-Issue 'CURRENT_SNAPSHOT_BLOCK_MISSING' $docs08RelativePath 1 `
            'docs/08 must contain exactly one visible CURRENT_BUNDLE_SNAPSHOT text block.'
    }
    else {
        $expectedSnapshot = [ordered]@{
            schemaVersion = [string]$manifest.schemaVersion
            contentVersion = [string]$manifest.contentVersion
            projects = [string]$manifest.counts.projects
            cases = [string]$manifest.counts.cases
            claims = [string]$manifest.counts.claims
            evidence = [string]$manifest.counts.evidence
            claimEvidenceLinks = [string]$manifest.counts.claimEvidenceLinks
            timelineEvents = [string]$manifest.counts.timelineEvents
            questionPresets = [string]$manifest.counts.questionPresets
        }
        $actualSnapshot = @{}
        $problems = [System.Collections.Generic.List[string]]::new()
        foreach ($line in @($snapshotMatches[0].Groups['body'].Value -split "`r?`n")) {
            if ([string]::IsNullOrWhiteSpace($line)) {
                continue
            }
            $entry = [regex]::Match(
                $line,
                '^\s*(?<key>[A-Za-z][A-Za-z0-9]*)\s*=\s*(?<value>[^\s]+)\s*$')
            if (-not $entry.Success) {
                $problems.Add("unparseable entry '$($line.Trim())'") | Out-Null
                continue
            }
            $key = $entry.Groups['key'].Value
            if ($actualSnapshot.ContainsKey($key)) {
                $problems.Add("duplicate key '$key'") | Out-Null
                continue
            }
            $actualSnapshot[$key] = $entry.Groups['value'].Value
        }
        foreach ($key in $expectedSnapshot.Keys) {
            if (-not $actualSnapshot.ContainsKey($key)) {
                $problems.Add("missing $key") | Out-Null
            }
            elseif ([string]$actualSnapshot[$key] -ne [string]$expectedSnapshot[$key]) {
                $problems.Add(
                    "$key=$($actualSnapshot[$key]) expected=$($expectedSnapshot[$key])") |
                    Out-Null
            }
        }
        foreach ($key in $actualSnapshot.Keys) {
            if (-not $expectedSnapshot.Contains($key)) {
                $problems.Add("unknown key '$key'") | Out-Null
            }
        }
        if ($problems.Count -gt 0) {
            Add-Issue 'CURRENT_SNAPSHOT_BLOCK_MISMATCH' $docs08RelativePath `
                (Get-LineNumber $docs08.Text $snapshotMatches[0].Index) `
                ($problems -join '; ')
        }
    }

    foreach ($document in $currentDocuments) {
        $textWithoutSnapshot = if ($document.RelativePath -eq $docs08RelativePath -and
                $snapshotMatches.Count -eq 1) {
            $placeholder = [regex]::Replace(
                $snapshotMatches[0].Value,
                '[^\r\n]',
                ' ')
            $docs08.Text.Remove(
                $snapshotMatches[0].Index,
                $snapshotMatches[0].Length).Insert(
                $snapshotMatches[0].Index,
                $placeholder)
        }
        else {
            $document.Text
        }
        $linesWithoutSnapshot = @($textWithoutSnapshot -split "`r?`n")
        $insideNonCurrentBundleReference = $false
        for ($lineIndex = 0; $lineIndex -lt $linesWithoutSnapshot.Count; $lineIndex++) {
            $line = $linesWithoutSnapshot[$lineIndex]
            if ($line -match '<!--\s*NON_CURRENT_BUNDLE_REFERENCE:BEGIN\s*-->') {
                if ($insideNonCurrentBundleReference) {
                    Add-Issue 'NON_CURRENT_BUNDLE_BLOCK_INVALID' `
                        $document.RelativePath ($lineIndex + 1) `
                        'Non-current Bundle reference blocks cannot be nested.'
                }
                $insideNonCurrentBundleReference = $true
                continue
            }
            if ($line -match '<!--\s*NON_CURRENT_BUNDLE_REFERENCE:END\s*-->') {
                if (-not $insideNonCurrentBundleReference) {
                    Add-Issue 'NON_CURRENT_BUNDLE_BLOCK_INVALID' `
                        $document.RelativePath ($lineIndex + 1) `
                        'Non-current Bundle reference block ends without a matching BEGIN.'
                }
                $insideNonCurrentBundleReference = $false
                continue
            }
            $hasBundleContext = `
                $line -match '(?i)(\bBundle\b|\bmanifest\b|public[-\s]?content|' +
                '随包|公开内容|内容快照|当前发布|现行发布|运行包|发布包|七文件)'
            $hasVersionReference = $hasBundleContext -and (
                $line -match '(?i)\bschema(?:Version)?\s*[`"'':= ]+\d+(?:\.\d+)?' -or
                $line -match '(?i)(?:contentVersion|内容版本)\s*(?:为|[:：=])?\s*[`"'']?\d{4}-\d{2}-\d{2}')
            $hasBundleCountReference = `
                $hasBundleContext -and
                $line -notmatch '(?i)(\bEval\b|benchmark|fixtures?|smoke|routing|phase[-\s]?\d|评测|测试|用例|数据集)' -and
                $line -match '(?i)\d+\s*(?:个|条)\s*(?:Project|Case|Claim|Evidence|TimelineEvent|QuestionPreset)\b'
            if ($insideNonCurrentBundleReference) {
                $historicalFact = [regex]::Match(
                    $line,
                    '^\s*(?<key>historical(?:SchemaVersion|ContentVersion|Projects|Cases|' +
                    'Claims|Evidence|ClaimEvidenceLinks|TimelineEvents|QuestionPresets))' +
                    '\s*=\s*(?<value>\S+)\s*$')
                $looksLikeHistoricalKey = `
                    $line -match '^\s*historical[A-Za-z]+\s*='
                $containsMachineFact = `
                    $line -match '(?i)\bschema(?:Version)?\s*(?:=|:|\s)\s*[`"'']?\d' -or
                    $line -match '(?i)(?:contentVersion|内容版本)\s*(?:=|:|：|为|\s)\s*[`"'']?\d{4}-\d{2}-\d{2}' -or
                    $line -match '(?i)\d+\s*(?:个|条)?\s*(?:Project|Case|Claim|Evidence|TimelineEvent|QuestionPreset)\b' -or
                    $line -match '(?i)\b(?:projects|cases|claims|evidence|claimEvidenceLinks|timelineEvents|questionPresets)\s*=\s*\d+'
                $historicalFactValueIsValid = $false
                if ($historicalFact.Success) {
                    $key = $historicalFact.Groups['key'].Value
                    $value = $historicalFact.Groups['value'].Value
                    $historicalFactValueIsValid = if ($key -eq 'historicalSchemaVersion') {
                        $value -match '^\d+(?:\.\d+)+$'
                    }
                    elseif ($key -eq 'historicalContentVersion') {
                        $value -match '^\d{4}-\d{2}-\d{2}(?:\.\d+)?$'
                    }
                    else {
                        $value -match '^\d+$'
                    }
                }
                if (($containsMachineFact -and -not $historicalFact.Success) -or
                        ($looksLikeHistoricalKey -and
                        (-not $historicalFact.Success -or -not $historicalFactValueIsValid))) {
                    Add-Issue 'NON_CURRENT_BUNDLE_FACT_FORMAT' `
                        $document.RelativePath ($lineIndex + 1) `
                        'Historical Bundle machine facts must use an approved historical*=value field.'
                }
            }
            if (($hasVersionReference -or $hasBundleCountReference) -and
                    -not $insideNonCurrentBundleReference) {
                Add-Issue 'BUNDLE_REFERENCE_UNMARKED' `
                    $document.RelativePath ($lineIndex + 1) `
                    'Bundle version/count references outside the canonical docs/08 snapshot must be marked NON_CURRENT.'
            }
        }
        if ($insideNonCurrentBundleReference) {
            Add-Issue 'NON_CURRENT_BUNDLE_BLOCK_INVALID' $document.RelativePath `
                $linesWithoutSnapshot.Count `
                'Non-current Bundle reference block is missing its END marker.'
        }
    }
}

# README 必须自身完整声明全部四条无版本资源。
$expectedAgentResources = @(
    'POST /api/agent/turns',
    'DELETE /api/agent/turns/{requestId}',
    'GET /api/agent/conversations/current',
    'DELETE /api/agent/conversations/current'
)
$readme = $currentDocuments |
    Where-Object { $_.RelativePath -eq 'README.md' } |
    Select-Object -First 1
if ($null -ne $readme) {
    foreach ($resource in $expectedAgentResources) {
        if ($readme.Text -notmatch [regex]::Escape($resource)) {
            Add-Issue 'AGENT_RESOURCE_README_MISSING' 'README.md' 0 `
                "README does not declare '$resource'."
        }
    }
}

$javaSourceDirectory = Join-Path $resolvedRoot 'backend/src/main/java'
$controllerRoutes = @(Get-ControllerRoutes $javaSourceDirectory)
$expectedRouteSet = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::Ordinal)
foreach ($resource in $expectedAgentResources) {
    [void]$expectedRouteSet.Add($resource)
}
$actualRouteSet = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::Ordinal)
foreach ($route in $controllerRoutes) {
    if (-not $actualRouteSet.Add($route.Key)) {
        Add-Issue 'AGENT_RESOURCE_SOURCE_DUPLICATE' $route.RelativePath $route.Line `
            "Agent route '$($route.Key)' is declared more than once."
    }
    if (-not $expectedRouteSet.Contains($route.Key)) {
        Add-Issue 'AGENT_RESOURCE_SOURCE_EXTRA' $route.RelativePath $route.Line `
            "Unexpected public Agent route '$($route.Key)' on method '$($route.Method)'."
    }
}
foreach ($resource in $expectedAgentResources) {
    if (-not $actualRouteSet.Contains($resource)) {
        Add-Issue 'AGENT_RESOURCE_SOURCE_MISSING' `
            'backend/src/main/java' 0 `
            "Controller source does not declare '$resource'."
    }
}

# docs/11 是单一正序时间线；所有本地链接必须解析后仍位于仓库内。
$docs11RelativePath = 'docs/11-项目演进日志.md'
$docs11 = $historicalDocuments |
    Where-Object { $_.RelativePath -eq $docs11RelativePath } |
    Select-Object -First 1
if ($null -ne $docs11) {
    $seenDates = @{}
    $previousDate = $null
    for ($lineIndex = 0; $lineIndex -lt $docs11.Lines.Count; $lineIndex++) {
        $line = $docs11.Lines[$lineIndex]
        $h2 = [regex]::Match($line, '^##(?!#)\s+(?<title>.+?)\s*$')
        if ($h2.Success) {
            $dateHeading = [regex]::Match(
                $h2.Groups['title'].Value,
                '^(?<date>\d{4}-\d{2}-\d{2})$')
            if (-not $dateHeading.Success) {
                Add-Issue 'DOCS11_H2_INVALID' $docs11RelativePath ($lineIndex + 1) `
                    "Every level-two heading must be YYYY-MM-DD; found '$($line.Trim())'."
                continue
            }
            $dateText = $dateHeading.Groups['date'].Value
            $parsedDate = [datetime]::MinValue
            $validDate = [datetime]::TryParseExact(
                $dateText,
                'yyyy-MM-dd',
                [System.Globalization.CultureInfo]::InvariantCulture,
                [System.Globalization.DateTimeStyles]::None,
                [ref]$parsedDate)
            if (-not $validDate) {
                Add-Issue 'DOCS11_DATE_INVALID' $docs11RelativePath ($lineIndex + 1) `
                    "Malformed date heading '$($line.Trim())'."
            }
            elseif ($seenDates.ContainsKey($dateText)) {
                Add-Issue 'DOCS11_DATE_DUPLICATE' $docs11RelativePath ($lineIndex + 1) `
                    "Date heading '$dateText' appears more than once."
            }
            else {
                $seenDates[$dateText] = $true
                if ($null -ne $previousDate -and $parsedDate -lt $previousDate) {
                    Add-Issue 'DOCS11_DATE_ORDER' $docs11RelativePath ($lineIndex + 1) `
                        "Date heading '$dateText' is earlier than the previous date."
                }
                $previousDate = $parsedDate
            }
        }
        if ($line -match `
                '^#{3,6}\s+(?:\d+(?:[.\-、]\d+)*[.:、]?\s*|' +
                'P\d+(?:[.\-/]\d+)*\b|Task[-\s]*\d+\b|FE[-\s]*\d+\b|' +
                '阶段\s*[0-9一二三四五六七八九十]+\b|' +
                '[一二三四五六七八九十]+[、.]\s*|' +
                '[（(][一二三四五六七八九十]+[）)]\s*)') {
            Add-Issue 'DOCS11_NUMBERED_HEADING' $docs11RelativePath ($lineIndex + 1) `
                'Evolution event headings must use semantic titles, not numeric/P/Task/FE/stage labels.'
        }

    }
    Test-DocumentLinks $docs11 'DOCS11'
}

foreach ($document in $historicalDocuments) {
    if ($document.RelativePath -eq $docs11RelativePath) {
        continue
    }
    if ($document.Text.Contains('](') -or $document.Text.Contains('][')) {
        Test-DocumentLinks $document 'HISTORICAL'
    }
}
foreach ($document in $activeWorkArtifacts) {
    if ($document.Text.Contains('](') -or $document.Text.Contains('][')) {
        Test-DocumentLinks $document 'ACTIVE'
    }
}

# 只核对 README 声明的环境变量。定义只来自 application ${ENV:} 或审批启动入口的赋值。
$environmentPattern = '\bPORTFOLIO_[A-Z][A-Z0-9_]*\b'
$knownEnvironmentKeys = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::Ordinal)
$configurationRoot = Join-Path $resolvedRoot 'backend/src/main/resources'
if (Test-Path -LiteralPath $configurationRoot -PathType Container) {
    foreach ($file in Get-ChildItem -LiteralPath $configurationRoot -File -Recurse |
            Where-Object { $_.Extension -in @('.yml', '.yaml', '.properties') }) {
        $text = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
        foreach ($match in [regex]::Matches(
                $text,
                '\$\{(?<key>PORTFOLIO_[A-Z][A-Z0-9_]*)(?::[^}]*)?\}')) {
            [void]$knownEnvironmentKeys.Add($match.Groups['key'].Value)
        }
    }
}
$approvedLauncherAssignmentFiles = @(
    'scripts/start-local.ps1',
    'scripts/start-frontend.ps1',
    'scripts/postgres-local.ps1',
    'scripts/run-jar-e2e.ps1',
    '.env.example',
    '.env.postgres.example'
)
foreach ($relativePath in $approvedLauncherAssignmentFiles) {
    $fullPath = Join-Path $resolvedRoot $relativePath
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        continue
    }
    $lines = @(Get-Content -LiteralPath $fullPath -Encoding UTF8)
    foreach ($line in $lines) {
        $assignment = [regex]::Match(
            $line,
            '^\s*(?:(?:\$env:|\$[A-Za-z_$][A-Za-z0-9_$]*\.)?' +
            '(?<key>PORTFOLIO_[A-Z][A-Z0-9_]*))\s*=')
        if ($assignment.Success) {
            [void]$knownEnvironmentKeys.Add($assignment.Groups['key'].Value)
        }
    }
}
if ($null -ne $readme) {
    for ($lineIndex = 0; $lineIndex -lt $readme.Lines.Count; $lineIndex++) {
        foreach ($match in [regex]::Matches($readme.Lines[$lineIndex], $environmentPattern)) {
            if (-not $knownEnvironmentKeys.Contains($match.Value)) {
                Add-Issue 'README_ENV_KEY_UNKNOWN' 'README.md' ($lineIndex + 1) `
                    "README environment key '$($match.Value)' has no approved definition."
            }
        }
    }
}

if ($issues.Count -gt 0) {
    foreach ($issue in $issues | Sort-Object Path, Line, Code, Message) {
        $location = if ($issue.Line -gt 0) {
            "$($issue.Path):$($issue.Line)"
        }
        else {
            $issue.Path
        }
        Write-Output "[documentation-check] $($issue.Code) $location - $($issue.Message)"
    }
    Write-Output "Documentation check failed with $($issues.Count) issue(s)."
    exit 1
}

Write-Output `
    "Documentation check passed for $($currentAuthorityFiles.Count) current-authority documents."
