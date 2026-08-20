param(
    [Parameter(Mandatory = $true)]
    [string]$Path
)

$ErrorActionPreference = 'Stop'
$resolvedPath = (Resolve-Path -LiteralPath $Path).Path
$rules = @(
    @{ Name = 'var-local'; Pattern = '\bvar\s+[A-Za-z_$][A-Za-z0-9_$]*\s*(?==|:|,|\))' },
    @{ Name = 'lombok-import'; Pattern = '^\s*import\s+lombok\.' },
    @{ Name = 'lombok-qualified-annotation'; Pattern = '@\s*lombok\.(Data|Getter|Setter|Value|Builder|RequiredArgsConstructor|AllArgsConstructor|NoArgsConstructor|Slf4j)\b' }
)

function Convert-JavaUnicodeEscapes([string]$Source) {
    $builder = New-Object System.Text.StringBuilder
    $consecutiveBackslashes = 0
    $lastBackslashFromUnicodeEscape = $false
    $index = 0

    while ($index -lt $Source.Length) {
        $current = $Source[$index]
        if ($current -eq '\') {
            $eligible = $lastBackslashFromUnicodeEscape -or ($consecutiveBackslashes % 2 -eq 0)
            $unicodeIndex = $index + 1
            if ($eligible -and $unicodeIndex -lt $Source.Length -and $Source[$unicodeIndex] -eq 'u') {
                while ($unicodeIndex -lt $Source.Length -and $Source[$unicodeIndex] -eq 'u') {
                    $unicodeIndex++
                }
                if ($unicodeIndex + 4 -le $Source.Length) {
                    $hex = $Source.Substring($unicodeIndex, 4)
                    if ($hex -match '^[0-9A-Fa-f]{4}$') {
                        $translated = [char][Convert]::ToUInt16($hex, 16)
                        [void]$builder.Append($translated)
                        if ($translated -eq '\') {
                            $consecutiveBackslashes++
                            $lastBackslashFromUnicodeEscape = $true
                        } else {
                            $consecutiveBackslashes = 0
                            $lastBackslashFromUnicodeEscape = $false
                        }
                        $index = $unicodeIndex + 4
                        continue
                    }
                }
            }
            [void]$builder.Append($current)
            $consecutiveBackslashes++
            $lastBackslashFromUnicodeEscape = $false
            $index++
            continue
        }
        [void]$builder.Append($current)
        $consecutiveBackslashes = 0
        $lastBackslashFromUnicodeEscape = $false
        $index++
    }
    return $builder.ToString()
}

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

$violations = New-Object System.Collections.Generic.List[string]
$javaFiles = Get-ChildItem -LiteralPath $resolvedPath -Recurse -File -Filter '*.java'
foreach ($file in $javaFiles) {
    $source = [System.IO.File]::ReadAllText($file.FullName)
    $lexicalSource = Remove-JavaCommentsAndLiterals (Convert-JavaUnicodeEscapes $source)
    $sourceLines = $source -split '\r?\n', -1
    $lexicalLines = $lexicalSource -split '\r?\n', -1
    foreach ($rule in $rules) {
        $matches = [regex]::Matches(
            $lexicalSource,
            $rule.Pattern,
            [System.Text.RegularExpressions.RegexOptions]::Multiline
        )
        $reportedLines = New-Object System.Collections.Generic.HashSet[int]
        foreach ($match in $matches) {
            $prefix = $lexicalSource.Substring(0, $match.Index)
            $lineNumber = ([regex]::Matches($prefix, '\r\n|\r|\n')).Count + 1
            if ($reportedLines.Add($lineNumber)) {
                $displayLine = if ($lineNumber -le $sourceLines.Count) {
                    $sourceLines[$lineNumber - 1].Trim()
                } elseif ($lineNumber -le $lexicalLines.Count) {
                    $lexicalLines[$lineNumber - 1].Trim()
                } else {
                    $match.Value.Trim()
                }
                $violations.Add("$($rule.Name):$($file.FullName):$lineNumber`:$displayLine")
            }
        }
    }
}

if ($violations.Count -gt 0) {
    $violations | ForEach-Object { Write-Output $_ }
    exit 1
}

Write-Output "Code quality check passed for $resolvedPath."
