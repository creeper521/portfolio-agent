param(
    [Parameter(Mandatory = $true)]
    [string]$Path
)

$ErrorActionPreference = 'Stop'
$resolvedPath = (Resolve-Path -LiteralPath $Path).Path
$violations = New-Object System.Collections.Generic.List[string]

function Add-Violation(
    [string]$Rule,
    [System.IO.FileInfo]$File,
    [Microsoft.PowerShell.Commands.MatchInfo]$Match
) {
    $violations.Add(
        "$Rule`:$($File.FullName)`:$($Match.LineNumber)`:$($Match.Line.Trim())"
    )
}

$javaFiles = Get-ChildItem -LiteralPath $resolvedPath -Recurse -File -Filter '*.java'
foreach ($file in $javaFiles) {
    $relative = $file.FullName.Substring($resolvedPath.Length).TrimStart('\')
    $relative = $relative -replace '^(main|test)\\java\\', ''
    $imports = Select-String -LiteralPath $file.FullName `
        -Pattern '^\s*import\s+com\.portfolio\.agent\.[^;]+;'

    foreach ($import in $imports) {
        $line = $import.Line

        if ($relative -match '^com\\portfolio\\agent\\common\\' -and
                $line -match 'com\.portfolio\.agent\.(portfolio|answer)\.') {
            Add-Violation 'common-business' $file $import
        }

        if ($relative -match '^com\\portfolio\\agent\\portfolio\\service\\' -and
                $line -match 'com\.portfolio\.agent\.portfolio\.(controller|dto)\.') {
            Add-Violation 'portfolio-service-controller' $file $import
        }

        if ($relative -match '^com\\portfolio\\agent\\answer\\(service|domain|engine|gateway)\\' -and
                $line -match 'com\.portfolio\.agent\.portfolio\.') {
            Add-Violation 'answer-core-portfolio' $file $import
        }

        if ($relative -match '\\controller\\' -and
                $line -match '\.(repository\.file|adapter|engine\.deterministic)\.') {
            Add-Violation 'controller-infrastructure' $file $import
        }

        if ($relative -match '^com\\portfolio\\agent\\portfolio\\' -and
                $line -match 'com\.portfolio\.agent\.answer\.') {
            Add-Violation 'portfolio-answer' $file $import
        }
    }
}

if ($violations.Count -gt 0) {
    $violations | ForEach-Object { Write-Output $_ }
    exit 1
}

Write-Output "Architecture check passed for $resolvedPath."
