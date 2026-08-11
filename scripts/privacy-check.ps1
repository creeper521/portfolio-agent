param(
    [Parameter(Mandatory = $true)]
    [string]$Path
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Path)) {
    Write-Error "Privacy check path does not exist: $Path"
    exit 2
}

$resolvedPath = (Resolve-Path -LiteralPath $Path).Path
$allowedExtensions = @(
    '.java', '.class', '.xml', '.json', '.jsonl', '.js', '.ts', '.tsx', '.vue',
    '.css', '.html', '.yml', '.yaml', '.properties', '.txt', '.md', '.map',
    '.svg', '.csv', '.log', '.conf', '.env', '.ps1', '.sh', '.toml'
)
$excludedDirectoryNames = @(
    '.git', '.idea', '.worktrees', '.claude', '.playwright-cli', '.superpowers', 'node_modules',
    'runtime-models', 'docs', 'test', 'test-classes', 'test-results',
    'playwright-report', 'surefire-reports', 'antrun', 'maven-status',
'logs', 'output', 'target', 'dist'
)
$sourceLogSinkRegex = '(?i:\b(?:console\s*\.\s*(?:trace|debug|info|warn|error|log)|System\s*\.\s*(?:out|err)\s*\.\s*(?:print|println|printf))\s*\()'
$sensitiveLogIdentifierRegex = '(?:\b(?:question|visitorQuestion|history|conversationHistory|messages|answer|fullAnswer|answerText|generatedAnswer|modelAnswer|prompt|systemPrompt|userPrompt|modelPrompt|credential|credentials|requestCredentials|authorization|password|passwd|secret|token|apiKey|api_key|accessToken|authToken|bearerToken|providerToken|rawIp|raw_ip|clientIp|client_ip|remoteIp|remote_ip|ipAddress|ip_address|remoteAddress|header|headers|requestHeader|requestHeaders|responseHeader|responseHeaders|httpHeaders|body|requestBody|responseBody|payload|providerRequest|providerResponse|modelResponse|exception|exceptionMessage|normalizedQuery|queryVector|similarity|retrievalCandidate|rankedHit|contextEnvelope|toolPlan|toolResult)\b|request\s*\.\s*getQuestion\s*\(|session\s*\.\s*messages\b)'
$templateSensitiveExpressionRegex = '(?is)\$\{[^{}]{0,512}' +
        $sensitiveLogIdentifierRegex +
        '[^{}]{0,512}\}'
$diagnosticForbiddenFieldNameRegex = '(?:question|messages|answer|stack|url|headers|requestBody|responseBody|metadata|rawAddress|rawIp|clientIp|remoteIp|ipAddress|credentials?|authorization|password|secret|apiKey|accessToken|authToken|bearerToken)'
$javaDiagnosticForbiddenFieldRegex =
        '(?i)\b(?:public|protected|private)\b[^\r\n;(){}]*\b' +
        $diagnosticForbiddenFieldNameRegex +
        '\b\s*(?:[;=])|@JsonProperty\s*\(\s*["'']' +
        $diagnosticForbiddenFieldNameRegex +
        '["'']\s*\)'
$typeScriptDiagnosticForbiddenFieldRegex =
        '(?i)(?:^|[,{;\r\n])\s*(?:readonly\s+)?["'']?' +
        $diagnosticForbiddenFieldNameRegex +
        '["'']?\??\s*:'
$patterns = @(
    @{ Name = 'ipv4-address'; Regex = '(?<![\d.])(?:10(?:\.\d{1,3}){3}|192\.168(?:\.\d{1,3}){2}|172\.(?:1[6-9]|2\d|3[01])(?:\.\d{1,3}){2}|169\.254(?:\.\d{1,3}){2})(?![\d.])' },
    @{ Name = 'windows-absolute-path'; Regex = '(?i)[a-z]:\\(?:users|code|work|workspace)\\' },
    @{ Name = 'internal-linux-path'; Regex = '(?i)/(?:data|home|opt|srv)/(?:server|internal|company|private|prod)(?:/|\b)' },
    @{ Name = 'credential-assignment'; Regex = '(?i)(?<![A-Z0-9_$\{])(?!(?:requestToken)\s*[:=])(?!(?:integrityToken)\s*[:=]\s*(?:[A-Z0-9_$]+\.)+(?:integrityToken)\b)(?!(?:credentials)\s*[:=]\s*["'']?(?:omit|same-origin|include)["'']?(?:[:,;}\s]|$))(?:[A-Z0-9_-]*(?:password|passwd|secret|token|api[_-]?key))\s*[:=](?>[ \t]*)(?!["'']?\$(?:\{[A-Z0-9_]+(?::[^}]*)?\}|[A-Z0-9_]+)["'']?)(?!["'']?<[A-Z0-9_-]+>["'']?)[^\s,;]+'; ExcludeExtensions = @('.java') },
    @{ Name = 'java-credential-literal'; Regex = '(?i)\b(?:[A-Z0-9_]*(?:password|passwd|secret|token|apiKey))\s*=\s*"[^"\r\n]+"'; Extensions = @('.java') },
    @{ Name = 'internal-hostname'; Regex = '(?![A-Z][A-Za-z0-9_]*(?:\.[A-Z][A-Za-z0-9_]*)*\.LOCAL\b)(?i:(?<![a-z0-9-])(?<!compose\.)(?<!env\.)(?!(?:compose\.postgres\.local\.ya?ml|env\.postgres\.local)(?:\b|$))(?:https?://)?(?:[a-z0-9-]+\.)+(?:internal|corp|private|local)(?::\d+)?(?:/|\b))' },
    @{ Name = 'private-key-material'; Regex = '(?i)-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----' },
    @{ Name = 'standalone-api-key'; Regex = '(?i)(?:\bsk-[a-z0-9_-]{20,}\b|\b[0-9a-f]{32}\.[a-z0-9_-]{16,}\b)' },
    @{ Name = 'visitor-session-storage-key'; Regex = '(?i)portfolio\.agent\.sessions(?:\.|\b)' },
    @{ Name = 'question-in-url'; Regex = '(?i)[?&]question=' },
    @{ Name = 'visitor-question-provider-field'; Regex = '(?i)\b(?:provider|model)[A-Za-z0-9_]*\s*\.\s*(?:question|visitorQuestion)\s*=\s*[^\r\n;]*\brequest\s*\.\s*getQuestion\s*\(' }
)

function Remove-JavaCommentsAndLiterals([string]$Source) {
    $builder = New-Object System.Text.StringBuilder
    $state = 'code'
    $index = 0

    while ($index -lt $Source.Length) {
        $current = $Source[$index]
        $next = if ($index + 1 -lt $Source.Length) { $Source[$index + 1] } else { [char]0 }
        $third = if ($index + 2 -lt $Source.Length) { $Source[$index + 2] } else { [char]0 }

        if ($state -eq 'code') {
            if ($current -eq '/' -and $next -eq '/') {
                [void]$builder.Append('  ')
                $state = 'line-comment'
                $index += 2
                continue
            }
            if ($current -eq '/' -and $next -eq '*') {
                [void]$builder.Append('  ')
                $state = 'block-comment'
                $index += 2
                continue
            }
            if ($current -eq '"' -and $next -eq '"' -and $third -eq '"') {
                [void]$builder.Append('   ')
                $state = 'text-block'
                $index += 3
                continue
            }
            if ($current -eq '"') {
                [void]$builder.Append(' ')
                $state = 'string'
                $index++
                continue
            }
            if ($current -eq [char]96) {
                [void]$builder.Append(' ')
                $state = 'template'
                $index++
                continue
            }
            if ($current -eq "'") {
                [void]$builder.Append(' ')
                $state = 'character'
                $index++
                continue
            }

            [void]$builder.Append($current)
            $index++
            continue
        }

        if ($state -eq 'line-comment') {
            if ($current -eq "`r" -or $current -eq "`n") {
                [void]$builder.Append($current)
                $state = 'code'
            } else {
                [void]$builder.Append(' ')
            }
            $index++
            continue
        }

        if ($state -eq 'block-comment') {
            if ($current -eq '*' -and $next -eq '/') {
                [void]$builder.Append('  ')
                $state = 'code'
                $index += 2
                continue
            }
            if ($current -eq "`r" -or $current -eq "`n") {
                [void]$builder.Append($current)
            } else {
                [void]$builder.Append(' ')
            }
            $index++
            continue
        }

        if ($state -eq 'text-block') {
            if ($current -eq '"' -and $next -eq '"' -and $third -eq '"') {
                $backslashCount = 0
                $backslashIndex = $index - 1
                while ($backslashIndex -ge 0 -and $Source[$backslashIndex] -eq '\') {
                    $backslashCount++
                    $backslashIndex--
                }
                if ($backslashCount % 2 -eq 0) {
                    [void]$builder.Append('   ')
                    $state = 'code'
                    $index += 3
                    continue
                }
            }
            if ($current -eq "`r" -or $current -eq "`n") {
                [void]$builder.Append($current)
            } else {
                [void]$builder.Append(' ')
            }
            $index++
            continue
        }

        if ($current -eq '\') {
            [void]$builder.Append(' ')
            if ($index + 1 -lt $Source.Length) {
                if ($next -eq "`r" -or $next -eq "`n") {
                    [void]$builder.Append($next)
                } else {
                    [void]$builder.Append(' ')
                }
                $index += 2
            } else {
                $index++
            }
            continue
        }

        $terminator = if ($state -eq 'string') {
            '"'
        } elseif ($state -eq 'template') {
            [char]96
        } else {
            "'"
        }
        if ($current -eq $terminator) {
            [void]$builder.Append(' ')
            $state = 'code'
        } elseif ($current -eq "`r" -or $current -eq "`n") {
            [void]$builder.Append($current)
        } else {
            [void]$builder.Append(' ')
        }
        $index++
    }

    return $builder.ToString()
}

function Get-BoundedDelimiterEndIndex(
        [string]$LexicalSource,
        [int]$OpenDelimiterIndex,
        [char]$OpenDelimiter,
        [char]$CloseDelimiter,
        [int]$MaximumLength = 4096
) {
    $depth = 0
    $limit = [Math]::Min(
        $LexicalSource.Length,
        $OpenDelimiterIndex + $MaximumLength
    )
    for ($index = $OpenDelimiterIndex; $index -lt $limit; $index++) {
        if ($LexicalSource[$index] -eq $OpenDelimiter) {
            $depth++
        } elseif ($LexicalSource[$index] -eq $CloseDelimiter) {
            $depth--
            if ($depth -eq 0) {
                return $index
            }
        }
    }
    return -1
}

function Get-FirstCallStringLiteral(
        [string]$RawCall,
        [int]$OpenParenthesisOffset
) {
    $index = $OpenParenthesisOffset + 1
    while ($index -lt $RawCall.Length) {
        while ($index -lt $RawCall.Length -and
                [char]::IsWhiteSpace($RawCall[$index])) {
            $index++
        }
        if ($index + 1 -lt $RawCall.Length -and
                $RawCall[$index] -eq '/' -and
                $RawCall[$index + 1] -eq '/') {
            $newlineIndex = $RawCall.IndexOf("`n", $index + 2)
            if ($newlineIndex -lt 0) {
                return $null
            }
            $index = $newlineIndex + 1
            continue
        }
        if ($index + 1 -lt $RawCall.Length -and
                $RawCall[$index] -eq '/' -and
                $RawCall[$index + 1] -eq '*') {
            $commentEndIndex = $RawCall.IndexOf('*/', $index + 2)
            if ($commentEndIndex -lt 0) {
                return $null
            }
            $index = $commentEndIndex + 2
            continue
        }
        break
    }
    if ($index -ge $RawCall.Length -or
            $RawCall[$index] -notin @('"', "'")) {
        return $null
    }

    $quote = $RawCall[$index]
    $builder = [System.Text.StringBuilder]::new()
    $index++
    while ($index -lt $RawCall.Length) {
        $current = $RawCall[$index]
        if ($current -eq '\') {
            if ($index + 1 -ge $RawCall.Length) {
                return $null
            }
            [void]$builder.Append($RawCall[$index + 1])
            $index += 2
            continue
        }
        if ($current -eq $quote) {
            return $builder.ToString()
        }
        [void]$builder.Append($current)
        $index++
    }
    return $null
}

function Get-JavaSemicolonStatements([string]$Source) {
    $statements = [System.Collections.Generic.List[object]]::new()
    $start = 0
    for ($index = 0; $index -lt $Source.Length; $index++) {
        $current = $Source[$index]
        if ($current -eq '{' -or $current -eq '}') {
            $start = $index + 1
            continue
        }
        if ($current -ne ';') {
            continue
        }

        $value = $Source.Substring($start, $index - $start + 1)
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            $leadingWhitespaceLength = [regex]::Match($value, '^\s*').Length
            $statements.Add([pscustomobject]@{
                Index = $start + $leadingWhitespaceLength
                Value = $value
            })
        }
        $start = $index + 1
    }
    return $statements
}

function Get-NewlineOffsets([string]$Source) {
    $offsets = [System.Collections.Generic.List[int]]::new()
    for ($index = 0; $index -lt $Source.Length; $index++) {
        if ($Source[$index] -eq "`n") {
            [void]$offsets.Add($index)
        }
    }
    return ,$offsets
}

function Get-SourceLineNumber(
        [System.Collections.Generic.List[int]]$NewlineOffsets,
        [int]$Index
) {
    $position = $NewlineOffsets.BinarySearch($Index)
    $precedingNewlineCount = if ($position -ge 0) {
        $position
    } else {
        -$position - 1
    }
    return $precedingNewlineCount + 1
}

function Add-JavaLoggerDeclaratorNames(
        [string]$Declarators,
        [System.Collections.Generic.HashSet[string]]$ReceiverNames
) {
    $segments = [System.Collections.Generic.List[string]]::new()
    $segmentStart = 0
    $parenthesisDepth = 0
    $bracketDepth = 0
    $braceDepth = 0
    for ($index = 0; $index -lt $Declarators.Length; $index++) {
        switch ($Declarators[$index]) {
            '(' { $parenthesisDepth++ }
            ')' { $parenthesisDepth-- }
            '[' { $bracketDepth++ }
            ']' { $bracketDepth-- }
            '{' { $braceDepth++ }
            '}' { $braceDepth-- }
            ',' {
                if ($parenthesisDepth -eq 0 -and
                        $bracketDepth -eq 0 -and
                        $braceDepth -eq 0) {
                    $segments.Add($Declarators.Substring(
                            $segmentStart,
                            $index - $segmentStart))
                    $segmentStart = $index + 1
                }
            }
        }
    }
    $segments.Add($Declarators.Substring($segmentStart))
    foreach ($segment in $segments) {
        $declarator = [regex]::Match(
                $segment,
                '^\s*(?<name>[A-Za-z_$][A-Za-z0-9_$]*)' +
                '\s*(?:\[\s*\])?\s*(?:=|$)')
        if ($declarator.Success) {
            [void]$ReceiverNames.Add($declarator.Groups['name'].Value)
        }
    }
}

function Get-JavaLoggerReceiverNames([string]$LexicalSource) {
    $receiverNames = [System.Collections.Generic.HashSet[string]]::new(
            [System.StringComparer]::Ordinal)
    $usesSimpleSlf4jLoggerName =
            $LexicalSource -match '\bimport\s+org\.slf4j\.Logger\s*;'
    $loggerTypePatterns = [System.Collections.Generic.List[string]]::new()
    $loggerTypePatterns.Add('org\.slf4j\.Logger')
    if ($usesSimpleSlf4jLoggerName) {
        $loggerTypePatterns.Add('Logger')
    }
    $modifierPattern =
            '(?:(?:public|protected|private|static|final|transient|' +
            'volatile)\s+)*'
    foreach ($loggerTypePattern in $loggerTypePatterns) {
        $declarationPatterns =
                [System.Collections.Generic.List[string]]::new()
        $declarationPatterns.Add(
                '(?ms)(?:^|[;{}])\s*' + $modifierPattern +
                $loggerTypePattern +
                '\s+(?<declarators>[^;]+);')
        $declarationPatterns.Add(
                '(?ms)\bfor\s*\(\s*' + $modifierPattern +
                $loggerTypePattern +
                '\s+(?<declarators>[^;]+);')
        foreach ($declarationPattern in $declarationPatterns) {
            foreach ($declaration in [regex]::Matches(
                    $LexicalSource,
                    $declarationPattern)) {
                Add-JavaLoggerDeclaratorNames `
                        $declaration.Groups['declarators'].Value `
                        $receiverNames
            }
        }
    }
    return $receiverNames
}

$findings = [System.Collections.Generic.List[object]]::new()
$archivePaths = [System.Collections.Generic.List[string]]::new()
$archiveInvocationState = @{
    GlobalEntryCount = 0
    GlobalExpandedBytes = [long]0
    GlobalTextBytes = [long]0
    BudgetExceeded = $false
    MaximumGlobalEntries = 32768
    MaximumExpandedBytes = [long](512 * 1024 * 1024)
    MaximumTextBytes = [long](16 * 1024 * 1024)
    MaximumEntryBytes = [long](64 * 1024 * 1024)
    MaximumDepth = 3
    MaximumCompressionRatio = 200.0
}

function Add-ArchivePrivacyFindings(
        [string]$ArchivePath,
        [System.Collections.Generic.List[object]]$FindingList,
        [hashtable]$InvocationState
) {
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $textExtensions = @(
        '.html', '.js', '.map', '.json', '.jsonl', '.yml', '.yaml', '.properties',
        '.xml', '.txt', '.md', '.java', '.sql', '.csv', '.env', '.conf'
    )
    $archiveCredentialAssignmentRegex =
            '(?im)(?<![A-Z0-9$])' +
            '(?:password|passwd|secret|api[-_]?key|' +
            '(?:(?:access|auth|bearer|provider|client|refresh)[-_]?)?token)' +
            '["'']?\s*[:=]\s*' +
            '(?!["'']?\$\{)(?!["'']?<)(?!["'']?\{[0-9]+\})' +
            '(?![\{\[])' +
            '(?:"[^"\r\n]+"|''[^''\r\n]+''|[^\s,;#<>"'']+)' +
            '(?=\s*(?:[,;}>]|\r?$))'
    $maximumEntriesPerArchive = 4096

    function Add-ArchiveFinding(
            [string]$DisplayPath,
            [string]$Rule
    ) {
        [void]$FindingList.Add([pscustomobject]@{
            File = $DisplayPath
            Line = 1
            Rule = $Rule
        })
    }

    function Get-JsonPropertyValue(
            [object]$Node,
            [string]$Name
    ) {
        if ($null -eq $Node -or
                $Node -isnot [System.Management.Automation.PSCustomObject]) {
            return $null
        }
        foreach ($property in $Node.PSObject.Properties) {
            if ($property.Name -ieq $Name) {
                return $property.Value
            }
        }
        return $null
    }

    function Test-JsonPropertyExists(
            [object]$Node,
            [string]$Name
    ) {
        if ($null -eq $Node -or
                $Node -isnot [System.Management.Automation.PSCustomObject]) {
            return $false
        }
        foreach ($property in $Node.PSObject.Properties) {
            if ($property.Name -ieq $Name) {
                return $true
            }
        }
        return $false
    }

    function Test-GovernanceJsonNode(
            [object]$Node,
            [int]$Depth
    ) {
        if ($null -eq $Node -or $Depth -gt 32) {
            return $false
        }
        if ($Node -is [System.Array]) {
            foreach ($item in $Node) {
                if (Test-GovernanceJsonNode $item ($Depth + 1)) {
                    return $true
                }
            }
            return $false
        }
        if ($Node -isnot [System.Management.Automation.PSCustomObject]) {
            return $false
        }
        if (Test-JsonPropertyExists $Node 'portfolio.database.governance') {
            return $true
        }
        $portfolio = Get-JsonPropertyValue $Node 'portfolio'
        $database = Get-JsonPropertyValue $portfolio 'database'
        if (Test-JsonPropertyExists $database 'governance') {
            return $true
        }
        $governance = Get-JsonPropertyValue $Node 'governance'
        if (Test-JsonPropertyExists $governance 'database') {
            return $true
        }
        foreach ($property in $Node.PSObject.Properties) {
            if (Test-GovernanceJsonNode $property.Value ($Depth + 1)) {
                return $true
            }
        }
        return $false
    }

    function Scan-ZipArchive(
            [System.IO.Compression.ZipArchive]$Zip,
            [string]$DisplayRoot,
            [int]$Depth
    ) {
        if ($InvocationState.BudgetExceeded) {
            return
        }
        if ($Depth -gt $InvocationState.MaximumDepth) {
            Add-ArchiveFinding $DisplayRoot 'artifact-archive-depth-limit'
            return
        }
        if ($Zip.Entries.Count -gt $maximumEntriesPerArchive) {
            Add-ArchiveFinding $DisplayRoot 'artifact-archive-entry-limit'
            return
        }
        foreach ($entry in $Zip.Entries) {
            if ($InvocationState.BudgetExceeded) {
                return
            }
            $InvocationState.GlobalEntryCount++
            if ($InvocationState.GlobalEntryCount -gt
                    $InvocationState.MaximumGlobalEntries) {
                Add-ArchiveFinding $DisplayRoot 'artifact-archive-global-entry-limit'
                $InvocationState.BudgetExceeded = $true
                return
            }
            $rawEntryName = $entry.FullName
            $entryName = $rawEntryName.Replace('\', '/')
            $displayPath = $DisplayRoot + '!/' + $entryName
            $hasUnsafeEntryPath =
                    $entryName -match '^(?:/|[A-Za-z]:)' -or
                    @($entryName.Split('/')) -contains '..'
            if ($hasUnsafeEntryPath) {
                Add-ArchiveFinding $displayPath 'artifact-archive-entry-path'
                continue
            }
            if ($entry.Length -gt $InvocationState.MaximumEntryBytes) {
                Add-ArchiveFinding $displayPath 'artifact-archive-size-limit'
                continue
            }
            if ($InvocationState.GlobalExpandedBytes + $entry.Length -gt
                    $InvocationState.MaximumExpandedBytes) {
                Add-ArchiveFinding $displayPath 'artifact-archive-size-limit'
                $InvocationState.BudgetExceeded = $true
                return
            }
            if ($entry.Length -gt 0 -and
                    $entry.Length / [Math]::Max(1.0, [double]$entry.CompressedLength) -gt
                    $InvocationState.MaximumCompressionRatio) {
                Add-ArchiveFinding $displayPath 'artifact-archive-compression-limit'
                continue
            }
            $InvocationState.GlobalExpandedBytes += $entry.Length
            $extension = [System.IO.Path]::GetExtension($entryName).ToLowerInvariant()
            $isMetadataLicenseOrNotice =
                    $entryName -match '(?i)(?:^|/)META-INF/(?:LICENSE|NOTICE)[^/]*\.md$'
            $isNestedThirdPartyRootPrivacy =
                    $Depth -eq 1 -and
                    $DisplayRoot -match '(?i)!/BOOT-INF/lib/[^/]+\.jar$' -and
                    $entryName -match '(?i)^Privacy\.md$'
            if ($entryName -match '(?i)\.md$' -and
                    -not $isMetadataLicenseOrNotice -and
                    -not $isNestedThirdPartyRootPrivacy) {
                Add-ArchiveFinding $displayPath 'artifact-private-markdown'
            }
            if ($entryName -match '(?i)(?:^|/)private(?:/[^/]+)*/vector[^/]*(?:/|$|\.(?:json|jsonl|bin|dat|npy|csv|txt))' -or
                    $entryName -match '(?i)(?:^|/)governance(?:/[^/]+)*/(?:vector|embedding)[^/]*') {
                Add-ArchiveFinding $displayPath 'artifact-private-vector'
            }
            if ($extension -in @('.jar', '.zip')) {
                $memory = New-Object System.IO.MemoryStream
                $nestedStream = $entry.Open()
                try {
                    $nestedStream.CopyTo($memory)
                    $memory.Position = 0
                    $nestedArchive = New-Object System.IO.Compression.ZipArchive(
                        $memory,
                        [System.IO.Compression.ZipArchiveMode]::Read,
                        $true)
                    try {
                        Scan-ZipArchive $nestedArchive $displayPath ($Depth + 1)
                    }
                    finally {
                        $nestedArchive.Dispose()
                    }
                }
                catch {
                    Add-ArchiveFinding $displayPath 'artifact-archive-unreadable'
                }
                finally {
                    $nestedStream.Dispose()
                    $memory.Dispose()
                }
                continue
            }
            if ($extension -notin $textExtensions) {
                continue
            }
            if ($InvocationState.GlobalTextBytes + $entry.Length -gt
                    $InvocationState.MaximumTextBytes) {
                Add-ArchiveFinding $displayPath 'artifact-archive-text-size-limit'
                $InvocationState.BudgetExceeded = $true
                return
            }
            $InvocationState.GlobalTextBytes += $entry.Length
            $reader = New-Object System.IO.StreamReader(
                $entry.Open(), [System.Text.Encoding]::UTF8, $true, 1024, $false)
            try {
                $content = $reader.ReadToEnd()
            }
            finally {
                $reader.Dispose()
            }
            if ($content -match '(?i)[a-z]:\\(?:users|code|work|workspace)\\' -or
                    $content -match '(?i)/(?:data|home|opt|srv)/(?:server|internal|company|private|prod)(?:/|\b)') {
                Add-ArchiveFinding $displayPath 'artifact-local-absolute-path'
            }
            $credentialScanContent = [regex]::Replace(
                $content,
                '(?i)\$\{[A-Z0-9_]+:?\}',
                '<ENV>')
            $hasCredential = $credentialScanContent -match
                    $archiveCredentialAssignmentRegex
            if ($hasCredential) {
                Add-ArchiveFinding $displayPath 'artifact-credential'
            }
            $hasGovernanceJson = $false
            if ($extension -eq '.json') {
                try {
                    $jsonValue = ConvertFrom-Json -InputObject $content -ErrorAction Stop
                    $hasGovernanceJson = Test-GovernanceJsonNode $jsonValue 0
                }
                catch {
                    $hasGovernanceJson =
                            $content -match '(?i)"portfolio\.database\.governance"\s*:'
                }
            }
            $governanceConfigurationContent = $content
            if ($entryName -match
                    '(?i)(?:^|/)application[^/]*\.(?:yml|yaml)$') {
                $safeGovernanceYamlBlock =
                        '(?ms)^    governance:\s*\r?\n' +
                        '      enabled:\s*\$\{PORTFOLIO_GOVERNANCE_DATABASE_ENABLED:false\}\s*\r?\n' +
                        '      url:\s*\$\{PORTFOLIO_GOVERNANCE_DATABASE_URL:\}\s*\r?\n' +
                        '      username:\s*\$\{PORTFOLIO_GOVERNANCE_DATABASE_USERNAME:\}\s*\r?\n' +
                        '      pass' + 'word:\s*\$\{PORTFOLIO_GOVERNANCE_DATABASE_PASSWORD:\}\s*' +
                        '(?=\r?\n {0,4}\S|\z)'
                $governanceConfigurationContent = [regex]::Replace(
                        $governanceConfigurationContent,
                        $safeGovernanceYamlBlock,
                        '')
            }
            $isGovernanceConfiguration =
                    $entryName -match '(?i)(?:^|/)[^/]*governance[^/]*\.(?:yml|yaml|properties|json|env|conf)$' -or
                    $governanceConfigurationContent -match '(?i)\bPORTFOLIO_GOVERNANCE_DATABASE_(?:URL|USERNAME|PASSWORD|ENABLED)\b' -or
                    $hasGovernanceJson -or
                    (
                        $entryName -match '(?i)(?:^|/)application[^/]*\.(?:yml|yaml|properties)$' -and
                        $governanceConfigurationContent -match '(?is)portfolio(?:\.|:|\s){1,16}database(?:\.|:|\s){1,16}governance'
                    )
            if ($isGovernanceConfiguration) {
                Add-ArchiveFinding $displayPath 'artifact-governance-config'
            }
            if ($hasCredential -and $entryName -match '(?i)governance') {
                Add-ArchiveFinding $displayPath 'artifact-governance-config'
            }
        }
    }

    $archive = $null
    try {
        $archive = [System.IO.Compression.ZipFile]::OpenRead($ArchivePath)
        Scan-ZipArchive $archive $ArchivePath 0
    }
    catch {
        [void]$FindingList.Add([pscustomobject]@{
            File = $ArchivePath
            Line = 1
            Rule = 'artifact-archive-unreadable'
        })
    }
    finally {
        if ($null -ne $archive) {
            $archive.Dispose()
        }
    }
}

$item = Get-Item -LiteralPath $resolvedPath
if ($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) {
    [void]$findings.Add([pscustomobject]@{
        File = $item.FullName
        Line = 1
        Rule = 'filesystem-reparse-point'
    })
    $files = @()
}
elseif ($item.PSIsContainer) {
    $files = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
    $pending = [System.Collections.Generic.Stack[System.IO.DirectoryInfo]]::new()
    $pending.Push([System.IO.DirectoryInfo]$item)
    while ($pending.Count -gt 0) {
        $directory = $pending.Pop()
        foreach ($child in Get-ChildItem -LiteralPath $directory.FullName -Force) {
            if ($child.Attributes -band [System.IO.FileAttributes]::ReparsePoint) {
                [void]$findings.Add([pscustomobject]@{
                    File = $child.FullName
                    Line = 1
                    Rule = 'filesystem-reparse-point'
                })
            }
            elseif ($child.PSIsContainer) {
                if ($excludedDirectoryNames -notcontains $child.Name) {
                    $pending.Push([System.IO.DirectoryInfo]$child)
                }
            }
            elseif (([string]$child.Extension).ToLowerInvariant() -in @('.jar', '.zip')) {
                $archivePaths.Add($child.FullName)
            }
            elseif ($allowedExtensions -contains
                    ([string]$child.Extension).ToLowerInvariant() -and
                    $child.Name -notmatch '(?i)\.(?:test|spec)\.[^.]+$') {
                $files.Add([System.IO.FileInfo]$child)
            }
        }
    }
}
else {
    if (([string]$item.Extension).ToLowerInvariant() -in @('.jar', '.zip')) {
        $files = @()
        $archivePaths.Add($item.FullName)
    }
    else {
        $files = @($item)
    }
}

foreach ($archivePath in $archivePaths) {
    Add-ArchivePrivacyFindings $archivePath $findings $archiveInvocationState
}
foreach ($file in $files) {
    $extension = ([string]$file.Extension).ToLowerInvariant()
    $isSourceFile = $extension -in @('.java', '.js', '.ts', '.tsx', '.vue')
    $isDiagnosticContractSource =
            $extension -in @('.java', '.ts', '.tsx') -and
            (
                $file.DirectoryName -match '(?i)(?:^|[\\/])diagnostics(?:[\\/]|$)' -or
                $file.BaseName -match '(?i)(?:diagnostic.*(?:event|request|batch|dto)|(?:event|request|batch|dto).*diagnostic)'
            )
    if (([string]$file.Extension).ToLowerInvariant() -eq '.class') {
        $lines = @([System.Text.Encoding]::GetEncoding(28591).GetString(
            [System.IO.File]::ReadAllBytes($file.FullName)
        ) -split "`0")
        $sourceText = ''
        $lexicalSourceText = ''
        $lexicalLines = @()
    }
    else {
        $lines = @(Get-Content -LiteralPath $file.FullName -Encoding UTF8)
        $sourceText = if ($isSourceFile) {
            [System.IO.File]::ReadAllText($file.FullName)
        } else {
            ''
        }
        $lexicalSourceText = if ($isSourceFile) {
            Remove-JavaCommentsAndLiterals $sourceText
        } else {
            ''
        }
        $lexicalLines = if ($isSourceFile) {
            @($lexicalSourceText -split '\r?\n')
        } else {
            @()
        }
    }
    $isRegistryOrDescriptorSource =
            ([string]$file.Extension).Equals('.java', [System.StringComparison]::OrdinalIgnoreCase) -and
            $file.Name -match '(?i)(?:registry|descriptor).*\.java$'
    $source = if ($isRegistryOrDescriptorSource) { $sourceText } else { '' }
    $newlineOffsets = Get-NewlineOffsets $lexicalSourceText
    for ($index = 0; $index -lt $lines.Count; $index++) {
        if ($isDiagnosticContractSource -and $extension -eq '.java') {
            if ($lexicalLines[$index] -match $javaDiagnosticForbiddenFieldRegex) {
                [void]$findings.Add([pscustomobject]@{
                    File = $file.FullName
                    Line = $index + 1
                    Rule = 'diagnostic-forbidden-field'
                })
            }
        }
        foreach ($pattern in $patterns) {
            if ($pattern.ContainsKey('Extensions') -and
                    $pattern.Extensions -notcontains $extension) {
                continue
            }
            if ($pattern.ContainsKey('ExcludeExtensions') -and
                    $pattern.ExcludeExtensions -contains $extension) {
                continue
            }
            if ($lines[$index] -match $pattern.Regex) {
                [void]$findings.Add([pscustomobject]@{
                    File = $file.FullName
                    Line = $index + 1
                    Rule = $pattern.Name
                })
            }
        }
    }
    if ($isDiagnosticContractSource -and $extension -in @('.ts', '.tsx')) {
        $typeDeclarationStartRegex =
                '(?is)\b(?:export\s+)?(?:' +
                'interface\s+[A-Za-z_$][A-Za-z0-9_$]*(?:\s+extends[^{]+)?|' +
                'type\s+[A-Za-z_$][A-Za-z0-9_$]*(?:\s*<[^>{}]+>)?\s*=' +
                ')\s*\{'
        $typeDeclarations = [regex]::Matches(
            $lexicalSourceText,
            $typeDeclarationStartRegex
        )
        foreach ($typeDeclaration in $typeDeclarations) {
            $openBraceIndex =
                    $typeDeclaration.Index + $typeDeclaration.Length - 1
            $closeBraceIndex = Get-BoundedDelimiterEndIndex `
                    $lexicalSourceText `
                    $openBraceIndex `
                    ([char]'{') `
                    ([char]'}') `
                    16384
            if ($closeBraceIndex -lt 0) {
                $boundaryRule = if (
                    $lexicalSourceText.Length - $openBraceIndex -gt 16384
                ) {
                    'diagnostic-contract-too-large'
                } else {
                    'diagnostic-contract-unbalanced'
                }
                [void]$findings.Add([pscustomobject]@{
                    File = $file.FullName
                    Line = Get-SourceLineNumber `
                            $newlineOffsets `
                            $typeDeclaration.Index
                    Rule = $boundaryRule
                })
                continue
            }
            $bodyStartIndex = $openBraceIndex + 1
            $body = $lexicalSourceText.Substring(
                $bodyStartIndex,
                $closeBraceIndex - $bodyStartIndex
            )
            $forbiddenFields = [regex]::Matches(
                $body,
                $typeScriptDiagnosticForbiddenFieldRegex,
                [System.Text.RegularExpressions.RegexOptions]::Multiline
            )
            foreach ($forbiddenField in $forbiddenFields) {
                [void]$findings.Add([pscustomobject]@{
                    File = $file.FullName
                    Line = Get-SourceLineNumber `
                            $newlineOffsets `
                            ($bodyStartIndex + $forbiddenField.Index)
                    Rule = 'diagnostic-forbidden-field'
                })
            }
        }
    }
    $loggerReceiverNames = if ($extension -eq '.java') {
        @(Get-JavaLoggerReceiverNames $lexicalSourceText)
    } else {
        @()
    }
    if ($isSourceFile) {
        $effectiveSourceLogSinkRegex = $sourceLogSinkRegex
        if ($loggerReceiverNames.Count -gt 0) {
            $escapedReceiverNames = $loggerReceiverNames |
                    ForEach-Object { [regex]::Escape($_) }
            $loggerAliasSinkRegex =
                    '\b(?:' + ($escapedReceiverNames -join '|') +
                    ')\s*\.\s*(?:trace|debug|info|warn|error|fatal|' +
                    'atTrace|atDebug|atInfo|atWarn|atError|atFatal)\s*\('
            $effectiveSourceLogSinkRegex =
                    '(?:' + $sourceLogSinkRegex + '|' +
                    $loggerAliasSinkRegex + ')'
        }
        $sinkMatches = [regex]::Matches(
                $lexicalSourceText,
                $effectiveSourceLogSinkRegex)
        foreach ($sinkMatch in $sinkMatches) {
            $openParenthesisIndex = $sinkMatch.Index + $sinkMatch.Length - 1
            $callEndIndex = Get-BoundedDelimiterEndIndex `
                    $lexicalSourceText `
                    $openParenthesisIndex `
                    ([char]'(') `
                    ([char]')')
            if ($callEndIndex -lt 0) {
                $boundaryRule = if (
                    $lexicalSourceText.Length - $openParenthesisIndex -gt 4096
                ) {
                    'log-call-too-large'
                } else {
                    'log-call-unbalanced'
                }
                [void]$findings.Add([pscustomobject]@{
                    File = $file.FullName
                    Line = Get-SourceLineNumber `
                            $newlineOffsets `
                            $sinkMatch.Index
                    Rule = $boundaryRule
                })
                continue
            }
            $callLength = $callEndIndex - $sinkMatch.Index + 1
            $lexicalCall = $lexicalSourceText.Substring(
                $sinkMatch.Index,
                $callLength
            )
            $rawCall = $sourceText.Substring($sinkMatch.Index, $callLength)
            $firstFormatString = Get-FirstCallStringLiteral `
                    $rawCall `
                    ($openParenthesisIndex - $sinkMatch.Index)
            $hasSensitiveFormatLabel = $null -ne $firstFormatString -and
                    $firstFormatString -match $sensitiveLogIdentifierRegex -and
                    $firstFormatString -match '\{[^{}\r\n]{0,64}\}'
            if ($lexicalCall -match $sensitiveLogIdentifierRegex -or
                    $rawCall -match $templateSensitiveExpressionRegex -or
                    $hasSensitiveFormatLabel) {
                [void]$findings.Add([pscustomobject]@{
                    File = $file.FullName
                    Line = Get-SourceLineNumber $newlineOffsets $sinkMatch.Index
                    Rule = 'sensitive-source-log'
                })
            }
        }
    }
    if ($isRegistryOrDescriptorSource) {
        $lexicalSource = Remove-JavaCommentsAndLiterals $source
        $statements = @(Get-JavaSemicolonStatements $lexicalSource)
        $fieldDeclarationPattern = '(?is)^\s*(?:(?:@[A-Za-z_$][A-Za-z0-9_$.]*(?:\s*\([^;]*\))?)\s+)*(?:(?:public|protected|private|static|final|transient|volatile)\s+)*(?<type>[A-Za-z_$][A-Za-z0-9_$.]*(?:\s*<[^;=(){}]+>)?(?:\s*\[\s*\])*)\s+(?<name>[A-Za-z_$][A-Za-z0-9_$]*)\s*(?:=\s*[^;]*)?;\s*$'
        $credentialNamePattern = '(?i)(?:password|passwd|secret|token|apiKey|credential|authorization|accessKey|bearer)'
        $rawProviderValuePattern = '(?i)\b(?:providerRequest|providerResponse|requestBody|responseBody|modelResponse|request|response|body|payload|draft|prompt)\b'
        $loggerSinkPattern = if ($loggerReceiverNames.Count -gt 0) {
            '\b(?:' + (($loggerReceiverNames |
                    ForEach-Object { [regex]::Escape($_) }) -join '|') +
                    ')\s*\.\s*(?:trace|debug|info|warn|error|fatal|' +
                    'atTrace|atDebug|atInfo|atWarn|atError|atFatal)\s*\('
        } else {
            '(?!)'
        }
        $consumerSinkPattern = '\b[A-Za-z_$][A-Za-z0-9_$]*(?:Sink|_SINK|_sink)\s*\.\s*(?:accept|write|append|publish|emit)\s*\('
        $consoleSinkPattern = '\bSystem\s*\.\s*(?:out|err)\s*\.\s*(?:print|println|printf)\s*\('

        foreach ($statement in $statements) {
            $fieldMatch = [regex]::Match($statement.Value, $fieldDeclarationPattern)
            if ($fieldMatch.Success -and
                    $fieldMatch.Groups['type'].Value -notmatch '^(?:return|throw|new|case|yield)$' -and
                    $fieldMatch.Groups['name'].Value -match $credentialNamePattern) {
                [void]$findings.Add([pscustomobject]@{
                    File = $file.FullName
                    Line = Get-SourceLineNumber $newlineOffsets $statement.Index
                    Rule = 'registry-credential-field'
                })
            }

            $isLoggingStatement = $statement.Value -match $loggerSinkPattern -or
                    $statement.Value -match $consumerSinkPattern -or
                    $statement.Value -match $consoleSinkPattern
            if ($isLoggingStatement -and
                    $statement.Value -match $rawProviderValuePattern) {
                [void]$findings.Add([pscustomobject]@{
                    File = $file.FullName
                    Line = Get-SourceLineNumber $newlineOffsets $statement.Index
                    Rule = 'registry-raw-provider-log'
                })
            }
        }

        $mutableApiMatch = [regex]::Match(
            $lexicalSource,
            '(?is)\b(?:public|protected|private)?\s*(?:(?:static|final)\s+)*(?:[A-Za-z_$][A-Za-z0-9_$]*(?:\s*<[^;=(){}]+>)?(?:\[\])?\s+)(?:register|remove|replace)[A-Za-z0-9_$]*\s*\('
        )
        if ($mutableApiMatch.Success) {
            [void]$findings.Add([pscustomobject]@{
                File = $file.FullName
                Line = Get-SourceLineNumber $newlineOffsets $mutableApiMatch.Index
                Rule = 'registry-mutable-api'
            })
        }

        $dynamicDiscoveryMatch = [regex]::Match(
            $lexicalSource,
            '\b(?:Class\s*\.\s*forName|ServiceLoader\s*\.\s*load|ClassLoader|Files\s*\.|FileInputStream|Paths\s*\.\s*get|URL\s*\(|HttpClient|HttpURLConnection|URLConnection|WebClient|RestTemplate|Socket)\b'
        )
        if ($dynamicDiscoveryMatch.Success) {
            [void]$findings.Add([pscustomobject]@{
                File = $file.FullName
                Line = Get-SourceLineNumber $newlineOffsets $dynamicDiscoveryMatch.Index
                Rule = 'registry-dynamic-discovery'
            })
        }
    }
}

if ($findings.Count -gt 0) {
    Write-Output "Privacy check failed with $($findings.Count) finding(s)."
    $findings | Sort-Object File, Line, Rule | ForEach-Object {
        Write-Output "$($_.Rule):$($_.File):$($_.Line)"
    }
    exit 1
}

Write-Output "Privacy check passed for $($files.Count) file(s), $($archivePaths.Count) archive(s)."
exit 0
