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
$allowedExtensions = @('.json', '.js', '.css', '.html', '.yml', '.yaml', '.properties', '.txt', '.md', '.map', '.svg', '.csv', '.log', '.conf', '.env')
$patterns = @(
    @{ Name = 'ipv4-address'; Regex = '(?<!\d)(?:\d{1,3}\.){3}\d{1,3}(?!\d)' },
    @{ Name = 'windows-absolute-path'; Regex = '(?i)[a-z]:\\(?:users|code|work|workspace)\\' },
    @{ Name = 'internal-linux-path'; Regex = '(?i)/(?:data|home|opt|srv)/(?:server|internal|company|private|prod)(?:/|\b)' },
    @{ Name = 'credential-assignment'; Regex = '(?i)(?:password|passwd|secret|token|api[_-]?key)\s*[:=]\s*[^\s,;]+' },
    @{ Name = 'internal-hostname'; Regex = '(?i)(?:https?://)?(?:[a-z0-9-]+\.)+(?:internal|corp|private|local)(?::\d+)?(?:/|\b)' },
    @{ Name = 'private-key-material'; Regex = '(?i)-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----' },
    @{ Name = 'visitor-session-storage-key'; Regex = '(?i)portfolio\.agent\.sessions(?:\.|\b)' },
    @{ Name = 'question-in-url'; Regex = '(?i)[?&]question=' }
)

$item = Get-Item -LiteralPath $resolvedPath
if ($item.PSIsContainer) {
    $files = @(Get-ChildItem -LiteralPath $resolvedPath -Recurse -File | Where-Object {
        $allowedExtensions -contains $_.Extension.ToLowerInvariant()
    })
}
else {
    $files = @($item)
}

$findings = @()
foreach ($file in $files) {
    $lines = @(Get-Content -LiteralPath $file.FullName -Encoding UTF8)
    for ($index = 0; $index -lt $lines.Count; $index++) {
        foreach ($pattern in $patterns) {
            if ($lines[$index] -match $pattern.Regex) {
                $findings += [pscustomobject]@{
                    File = $file.FullName
                    Line = $index + 1
                    Rule = $pattern.Name
                }
            }
        }
    }
}

if ($findings.Count -gt 0) {
    Write-Output "Privacy check failed with $($findings.Count) finding(s)."
    $findings | Sort-Object File, Line, Rule | Format-Table -AutoSize
    exit 1
}

Write-Output "Privacy check passed for $($files.Count) file(s)."
exit 0
