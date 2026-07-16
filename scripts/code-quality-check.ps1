param(
    [Parameter(Mandatory = $true)]
    [string]$Path
)

$ErrorActionPreference = 'Stop'
$resolvedPath = (Resolve-Path -LiteralPath $Path).Path
$rules = @(
    @{ Name = 'var-local'; Pattern = '\bvar\s+[A-Za-z_$][A-Za-z0-9_$]*' },
    @{ Name = 'record-type'; Pattern = '\brecord\s+[A-Za-z_$][A-Za-z0-9_$]*' },
    @{ Name = 'lombok-import'; Pattern = '^\s*import\s+lombok\.' },
    @{ Name = 'lombok-qualified-annotation'; Pattern = '@\s*lombok\.(Data|Getter|Setter|Value|Builder|RequiredArgsConstructor|AllArgsConstructor|NoArgsConstructor|Slf4j)\b' }
)

$violations = New-Object System.Collections.Generic.List[string]
$javaFiles = Get-ChildItem -LiteralPath $resolvedPath -Recurse -File -Filter '*.java'
foreach ($file in $javaFiles) {
    foreach ($rule in $rules) {
        $matches = Select-String -LiteralPath $file.FullName -Pattern $rule.Pattern
        foreach ($match in $matches) {
            $violations.Add("$($rule.Name):$($file.FullName):$($match.LineNumber):$($match.Line.Trim())")
        }
    }
}

if ($violations.Count -gt 0) {
    $violations | ForEach-Object { Write-Output $_ }
    exit 1
}

Write-Output "Code quality check passed for $resolvedPath."
