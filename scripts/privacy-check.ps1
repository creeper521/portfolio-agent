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
    'playwright-report', 'surefire-reports', 'antrun', 'maven-status'
)
$patterns = @(
    @{ Name = 'ipv4-address'; Regex = '(?<![\d.])(?:10(?:\.\d{1,3}){3}|192\.168(?:\.\d{1,3}){2}|172\.(?:1[6-9]|2\d|3[01])(?:\.\d{1,3}){2}|169\.254(?:\.\d{1,3}){2})(?![\d.])' },
    @{ Name = 'windows-absolute-path'; Regex = '(?i)[a-z]:\\(?:users|code|work|workspace)\\' },
    @{ Name = 'internal-linux-path'; Regex = '(?i)/(?:data|home|opt|srv)/(?:server|internal|company|private|prod)(?:/|\b)' },
    @{ Name = 'credential-assignment'; Regex = '(?i)(?<![A-Z0-9_$\{])(?:[A-Z0-9_-]*(?:password|passwd|secret|token|api[_-]?key))\s*[:=](?>[ \t]*)(?!\$\{[A-Z0-9_]+(?::[^}]*)?\})(?!["'']?<[A-Z0-9_-]+>["'']?)[^\s,;]+'; ExcludeExtensions = @('.java') },
    @{ Name = 'java-credential-literal'; Regex = '(?i)\b(?:[A-Z0-9_]*(?:password|passwd|secret|token|apiKey))\s*=\s*"[^"\r\n]+"'; Extensions = @('.java') },
    @{ Name = 'internal-hostname'; Regex = '(?i)(?:https?://)?(?:[a-z0-9-]+\.)+(?:internal|corp|private|local)(?::\d+)?(?:/|\b)' },
    @{ Name = 'private-key-material'; Regex = '(?i)-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----' },
    @{ Name = 'standalone-api-key'; Regex = '(?i)(?:\bsk-[a-z0-9_-]{20,}\b|\b[0-9a-f]{32}\.[a-z0-9_-]{16,}\b)' },
    @{ Name = 'visitor-session-storage-key'; Regex = '(?i)portfolio\.agent\.sessions(?:\.|\b)' },
    @{ Name = 'question-in-url'; Regex = '(?i)[?&]question=' },
    @{ Name = 'raw-model-prompt-log'; Regex = '(?i)\b(?:logger|log)\s*\.\s*(?:trace|debug|info|warn|error)\s*\([^\r\n]*(?:prompt|requestBody|responseBody|modelResponse)' },
    @{ Name = 'raw-retrieval-log'; Regex = '(?i)\b(?:logger|log)\s*\.\s*(?:trace|debug|info|warn|error)\s*\([^\r\n]*(?:normalizedQuery|queryVector|similarity|retrievalCandidate|rankedHit)' },
    @{ Name = 'raw-context-or-tool-log'; Regex = '(?i)\b(?:logger|log)\s*\.\s*(?:trace|debug|info|warn|error)\s*\([^\r\n]*(?:contextEnvelope|toolPlan|toolResult)' },
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

        $terminator = if ($state -eq 'string') { '"' } else { "'" }
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

function Get-SourceLineNumber([string]$Source, [int]$Index) {
    return ([regex]::Matches($Source.Substring(0, $Index), "`n")).Count + 1
}

$item = Get-Item -LiteralPath $resolvedPath
if ($item.PSIsContainer) {
    $files = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
    $pending = [System.Collections.Generic.Stack[System.IO.DirectoryInfo]]::new()
    $pending.Push([System.IO.DirectoryInfo]$item)
    while ($pending.Count -gt 0) {
        $directory = $pending.Pop()
        foreach ($child in Get-ChildItem -LiteralPath $directory.FullName -Force) {
            if ($child.PSIsContainer) {
                if ($excludedDirectoryNames -notcontains $child.Name -and
                        -not ($child.Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
                    $pending.Push([System.IO.DirectoryInfo]$child)
                }
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
    $files = @($item)
}

$findings = @()
foreach ($file in $files) {
    if (([string]$file.Extension).ToLowerInvariant() -eq '.class') {
        $lines = @([System.Text.Encoding]::GetEncoding(28591).GetString(
            [System.IO.File]::ReadAllBytes($file.FullName)
        ) -split "`0")
    }
    else {
        $lines = @(Get-Content -LiteralPath $file.FullName -Encoding UTF8)
    }
    $isRegistryOrDescriptorSource =
            ([string]$file.Extension).Equals('.java', [System.StringComparison]::OrdinalIgnoreCase) -and
            $file.Name -match '(?i)(?:registry|descriptor).*\.java$'
    $source = if ($isRegistryOrDescriptorSource) {
        [System.IO.File]::ReadAllText($file.FullName)
    } else {
        ''
    }
    for ($index = 0; $index -lt $lines.Count; $index++) {
        foreach ($pattern in $patterns) {
            $extension = ([string]$file.Extension).ToLowerInvariant()
            if ($pattern.ContainsKey('Extensions') -and
                    $pattern.Extensions -notcontains $extension) {
                continue
            }
            if ($pattern.ContainsKey('ExcludeExtensions') -and
                    $pattern.ExcludeExtensions -contains $extension) {
                continue
            }
            if ($lines[$index] -match $pattern.Regex) {
                $findings += [pscustomobject]@{
                    File = $file.FullName
                    Line = $index + 1
                    Rule = $pattern.Name
                }
            }
        }
    }
    if ($isRegistryOrDescriptorSource) {
        $lexicalSource = Remove-JavaCommentsAndLiterals $source
        $statements = @(Get-JavaSemicolonStatements $lexicalSource)
        $fieldDeclarationPattern = '(?is)^\s*(?:(?:@[A-Za-z_$][A-Za-z0-9_$.]*(?:\s*\([^;]*\))?)\s+)*(?:(?:public|protected|private|static|final|transient|volatile)\s+)*(?<type>[A-Za-z_$][A-Za-z0-9_$.]*(?:\s*<[^;=(){}]+>)?(?:\s*\[\s*\])*)\s+(?<name>[A-Za-z_$][A-Za-z0-9_$]*)\s*(?:=\s*[^;]*)?;\s*$'
        $credentialNamePattern = '(?i)(?:password|passwd|secret|token|apiKey|credential|authorization|accessKey|bearer)'
        $rawProviderValuePattern = '(?i)\b(?:providerRequest|providerResponse|requestBody|responseBody|modelResponse|request|response|body|payload|draft|prompt)\b'
        $loggerSinkPattern = '\b(?:LOGGER|LOG|logger|log|[A-Za-z_$][A-Za-z0-9_$]*(?:Logger|Log|_LOGGER|_LOG|_logger|_log))\s*\.\s*(?:trace|debug|info|warn|error|fatal|atTrace|atDebug|atInfo|atWarn|atError|atFatal)\s*\('
        $consumerSinkPattern = '\b[A-Za-z_$][A-Za-z0-9_$]*(?:Logger|Log|Sink|_LOGGER|_LOG|_SINK|_logger|_log|_sink)\s*\.\s*(?:accept|write|append|publish|emit)\s*\('
        $consoleSinkPattern = '\bSystem\s*\.\s*(?:out|err)\s*\.\s*(?:print|println|printf)\s*\('

        foreach ($statement in $statements) {
            $fieldMatch = [regex]::Match($statement.Value, $fieldDeclarationPattern)
            if ($fieldMatch.Success -and
                    $fieldMatch.Groups['type'].Value -notmatch '^(?:return|throw|new|case|yield)$' -and
                    $fieldMatch.Groups['name'].Value -match $credentialNamePattern) {
                $findings += [pscustomobject]@{
                    File = $file.FullName
                    Line = Get-SourceLineNumber $lexicalSource $statement.Index
                    Rule = 'registry-credential-field'
                }
            }

            $isLoggingStatement = $statement.Value -match $loggerSinkPattern -or
                    $statement.Value -match $consumerSinkPattern -or
                    $statement.Value -match $consoleSinkPattern
            if ($isLoggingStatement -and
                    $statement.Value -match $rawProviderValuePattern) {
                $findings += [pscustomobject]@{
                    File = $file.FullName
                    Line = Get-SourceLineNumber $lexicalSource $statement.Index
                    Rule = 'registry-raw-provider-log'
                }
            }
        }

        $mutableApiMatch = [regex]::Match(
            $lexicalSource,
            '(?is)\b(?:public|protected|private)?\s*(?:(?:static|final)\s+)*(?:[A-Za-z_$][A-Za-z0-9_$]*(?:\s*<[^;=(){}]+>)?(?:\[\])?\s+)(?:register|remove|replace)[A-Za-z0-9_$]*\s*\('
        )
        if ($mutableApiMatch.Success) {
            $findings += [pscustomobject]@{
                File = $file.FullName
                Line = Get-SourceLineNumber $lexicalSource $mutableApiMatch.Index
                Rule = 'registry-mutable-api'
            }
        }

        $dynamicDiscoveryMatch = [regex]::Match(
            $lexicalSource,
            '\b(?:Class\s*\.\s*forName|ServiceLoader\s*\.\s*load|ClassLoader|Files\s*\.|FileInputStream|Paths\s*\.\s*get|URL\s*\(|HttpClient|HttpURLConnection|URLConnection|WebClient|RestTemplate|Socket)\b'
        )
        if ($dynamicDiscoveryMatch.Success) {
            $findings += [pscustomobject]@{
                File = $file.FullName
                Line = Get-SourceLineNumber $lexicalSource $dynamicDiscoveryMatch.Index
                Rule = 'registry-dynamic-discovery'
            }
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

Write-Output "Privacy check passed for $($files.Count) file(s)."
exit 0
