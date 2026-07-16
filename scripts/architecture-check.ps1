param(
    [Parameter(Mandatory = $true)]
    [string]$Path
)

$ErrorActionPreference = 'Stop'
$resolvedPath = (Resolve-Path -LiteralPath $Path).Path
$violations = New-Object System.Collections.Generic.List[string]

function Add-StatementViolation(
    [string]$Rule,
    [System.IO.FileInfo]$File,
    [int]$LineNumber,
    [string]$Statement
) {
    $violations.Add(
        "$Rule`:$($File.FullName)`:$LineNumber`:$Statement"
    )
}

function Convert-JavaUnicodeEscapes([string]$Source) {
    $builder = New-Object System.Text.StringBuilder
    $consecutiveBackslashes = 0
    $lastBackslashFromUnicodeEscape = $false
    $index = 0

    while ($index -lt $Source.Length) {
        $current = $Source[$index]
        if ($current -eq '\') {
            $eligible = $lastBackslashFromUnicodeEscape -or
                ($consecutiveBackslashes % 2 -eq 0)
            $unicodeIndex = $index + 1

            if ($eligible -and
                    $unicodeIndex -lt $Source.Length -and
                    $Source[$unicodeIndex] -eq 'u') {
                while ($unicodeIndex -lt $Source.Length -and
                        $Source[$unicodeIndex] -eq 'u') {
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

$javaFiles = Get-ChildItem -LiteralPath $resolvedPath -Recurse -File -Filter '*.java'
foreach ($file in $javaFiles) {
    $relative = $file.FullName.Substring($resolvedPath.Length).TrimStart('\')
    $relative = $relative -replace '^(main|test)\\java\\', ''
    $source = [System.IO.File]::ReadAllText($file.FullName)
    $unicodeSource = Convert-JavaUnicodeEscapes $source
    $lexicalSource = Remove-JavaCommentsAndLiterals $unicodeSource
    $statementMatches = [regex]::Matches(
        $lexicalSource,
        '^[ \t]*(?:package|import)\s+[^;]+;',
        [System.Text.RegularExpressions.RegexOptions]::Multiline
    )

    foreach ($statementMatch in $statementMatches) {
        $statement = [regex]::Replace($statementMatch.Value.Trim(), '\s+', ' ')
        $statement = [regex]::Replace($statement, '\s*\.\s*', '.')
        $statement = [regex]::Replace($statement, '\s*;\s*$', ';')
        $prefix = $lexicalSource.Substring(0, $statementMatch.Index)
        $lineNumber = ([regex]::Matches($prefix, "`n")).Count + 1
        $isLegacyPackage = $statement -match '^package com\.portfolio\.agent\.(portfolio|answer)\.(api|application|infrastructure|domain\.(model|repository))(\.|;)'
        $isLegacyImport = $statement -match '^import (?:static )?com\.portfolio\.agent\.(portfolio|answer)\.(api|application|infrastructure|domain\.(model|repository))\.'
        if ($isLegacyPackage -or $isLegacyImport) {
            Add-StatementViolation 'legacy-package' $file $lineNumber $statement
        }

        if ($statement -notmatch '^import ') {
            continue
        }

        if ($relative -match '^com\\portfolio\\agent\\common\\' -and
                $statement -match 'com\.portfolio\.agent\.(portfolio|answer)\.') {
            Add-StatementViolation 'common-business' $file $lineNumber $statement
        }

        if ($relative -match '^com\\portfolio\\agent\\portfolio\\service\\' -and
                $statement -match 'com\.portfolio\.agent\.portfolio\.(controller|dto)\.') {
            Add-StatementViolation 'portfolio-service-controller' $file $lineNumber $statement
        }

        if ($relative -match '^com\\portfolio\\agent\\answer\\(service|domain|engine|gateway)\\' -and
                $statement -match 'com\.portfolio\.agent\.portfolio\.') {
            Add-StatementViolation 'answer-core-portfolio' $file $lineNumber $statement
        }

        if ($relative -match '^com\\portfolio\\agent\\answer\\' -and
                $relative -notmatch '^com\\portfolio\\agent\\answer\\adapter\\portfolio\\' -and
                $statement -match 'com\.portfolio\.agent\.portfolio\.') {
            Add-StatementViolation 'answer-portfolio-boundary' $file $lineNumber $statement
        }

        if ($relative -match '^com\\portfolio\\agent\\answer\\adapter\\portfolio\\' -and
                $statement -match 'com\.portfolio\.agent\.portfolio\.' -and
                $statement -notmatch 'com\.portfolio\.agent\.portfolio\.domain\.[^;]+;' -and
                $statement -notmatch 'com\.portfolio\.agent\.portfolio\.repository\.PublicPortfolioRepository;') {
            Add-StatementViolation 'answer-portfolio-adapter-boundary' $file $lineNumber $statement
        }

        if ($relative -match '\\controller\\' -and
                $statement -match '\.(repository\.file|adapter|engine\.deterministic)\.') {
            Add-StatementViolation 'controller-infrastructure' $file $lineNumber $statement
        }

        if ($relative -match '^com\\portfolio\\agent\\portfolio\\' -and
                $statement -match 'com\.portfolio\.agent\.answer\.') {
            Add-StatementViolation 'portfolio-answer' $file $lineNumber $statement
        }
    }
}

if ($violations.Count -gt 0) {
    $violations | ForEach-Object { Write-Output $_ }
    exit 1
}

Write-Output "Architecture check passed for $resolvedPath."
