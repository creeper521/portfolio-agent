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

function Add-ReferenceViolations(
    [System.IO.FileInfo]$File,
    [string]$Relative,
    [int]$LineNumber,
    [string]$Reference,
    [string]$Display
) {
    $isLegacyReference = $Reference -match '^com\.portfolio\.agent\.(portfolio|answer)\.(api|application|infrastructure|domain\.(model|repository))(\.|$)'
    if ($isLegacyReference) {
        Add-StatementViolation 'legacy-package' $File $LineNumber $Display
    }

    if ($Relative -match '^com\\portfolio\\agent\\common\\' -and
            $Reference -match '^com\.portfolio\.agent\.(portfolio|answer)\.') {
        Add-StatementViolation 'common-business' $File $LineNumber $Display
    }

    if ($Relative -match '^com\\portfolio\\agent\\portfolio\\service\\' -and
            $Reference -match '^com\.portfolio\.agent\.portfolio\.(controller|dto)\.') {
        Add-StatementViolation 'portfolio-service-controller' $File $LineNumber $Display
    }

    if ($Relative -match '^com\\portfolio\\agent\\answer\\(service|domain|engine|gateway)\\' -and
            $Reference -match '^com\.portfolio\.agent\.portfolio\.') {
        Add-StatementViolation 'answer-core-portfolio' $File $LineNumber $Display
    }

    if ($Relative -match '^com\\portfolio\\agent\\answer\\' -and
            $Relative -notmatch '^com\\portfolio\\agent\\answer\\adapter\\portfolio\\' -and
            $Reference -match '^com\.portfolio\.agent\.portfolio\.') {
        Add-StatementViolation 'answer-portfolio-boundary' $File $LineNumber $Display
    }

    if ($Relative -match '^com\\portfolio\\agent\\answer\\adapter\\portfolio\\' -and
            $Reference -match '^com\.portfolio\.agent\.portfolio\.' -and
            $Reference -notmatch '^com\.portfolio\.agent\.portfolio\.domain\.' -and
            $Reference -ne 'com.portfolio.agent.portfolio.repository.PublicPortfolioRepository') {
        Add-StatementViolation 'answer-portfolio-adapter-boundary' $File $LineNumber $Display
    }

    if ($Relative -match '^com\\portfolio\\agent\\answer\\engine\\' -and
            $Reference -match '^com\.portfolio\.agent\.answer\.(service|controller|dto|gateway|adapter|exception|repository)(\.|$)') {
        Add-StatementViolation 'answer-engine-boundary' $File $LineNumber $Display
    }

    if ($Relative -match '^com\\portfolio\\agent\\(portfolio|answer)\\controller\\' -and
            $Reference -match '^com\.portfolio\.agent\.(portfolio|answer)\.(repository|adapter|engine|validation)(\.|$)') {
        Add-StatementViolation 'controller-layer-boundary' $File $LineNumber $Display
    }

    if ($Relative -match '^com\\portfolio\\agent\\portfolio\\' -and
            $Reference -match '^com\.portfolio\.agent\.answer\.') {
        Add-StatementViolation 'portfolio-answer' $File $LineNumber $Display
    }
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
    $bodyBuilder = New-Object System.Text.StringBuilder $lexicalSource

    foreach ($statementMatch in $statementMatches) {
        $statement = [regex]::Replace($statementMatch.Value.Trim(), '\s+', ' ')
        $statement = [regex]::Replace($statement, '\s*\.\s*', '.')
        $statement = [regex]::Replace($statement, '\s*;\s*$', ';')
        $prefix = $lexicalSource.Substring(0, $statementMatch.Index)
        $lineNumber = ([regex]::Matches($prefix, "`n")).Count + 1
        $isLegacyPackage = $statement -match '^package com\.portfolio\.agent\.(portfolio|answer)\.(api|application|infrastructure|domain\.(model|repository))(\.|;)'
        if ($isLegacyPackage) {
            Add-StatementViolation 'legacy-package' $file $lineNumber $statement
        }

        if ($statement -match '^import ') {
            $reference = $statement -replace '^import (?:static )?', ''
            $reference = $reference -replace ';$', ''
            Add-ReferenceViolations $file $relative $lineNumber $reference $statement
        }

        for ($offset = 0; $offset -lt $statementMatch.Length; $offset++) {
            $position = $statementMatch.Index + $offset
            $character = $bodyBuilder[$position]
            if ($character -ne "`r" -and $character -ne "`n") {
                $bodyBuilder[$position] = ' '
            }
        }
    }

    $bodySource = $bodyBuilder.ToString()
    $qualifiedMatches = [regex]::Matches(
        $bodySource,
        '\bcom\s*\.\s*portfolio\s*\.\s*agent(?:\s*\.\s*[A-Za-z_$][A-Za-z0-9_$]*)+'
    )
    foreach ($qualifiedMatch in $qualifiedMatches) {
        $reference = [regex]::Replace($qualifiedMatch.Value, '\s*\.\s*', '.')
        $prefix = $bodySource.Substring(0, $qualifiedMatch.Index)
        $lineNumber = ([regex]::Matches($prefix, "`n")).Count + 1
        Add-ReferenceViolations $file $relative $lineNumber $reference $reference
    }
}

if ($violations.Count -gt 0) {
    $violations | ForEach-Object { Write-Output $_ }
    exit 1
}

Write-Output "Architecture check passed for $resolvedPath."
