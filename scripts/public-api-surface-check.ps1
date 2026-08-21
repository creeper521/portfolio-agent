param(
    [string]$RootPath = ''
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($RootPath)) {
    $RootPath = Split-Path -Parent $PSScriptRoot
}
$resolvedRoot = (Resolve-Path -LiteralPath $RootPath -ErrorAction Stop).Path
$rootPrefix = $resolvedRoot.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
$pattern = [regex]::new('(?i)/api/v1(?:/|\b)')
$issues = [System.Collections.Generic.List[string]]::new()

$scanDirectories = @(
    'backend/src/main',
    'backend/src/test',
    'contracts/agent-turn',
    'frontend/src',
    'frontend/e2e',
    'scripts'
)
$scanFiles = @(
    'backend/pom.xml',
    'frontend/index.html',
    'frontend/package.json',
    'frontend/playwright.config.ts',
    'frontend/vite.config.ts'
)
$currentDocumentPatterns = @(
    'README.md',
    'AGENTS.md',
    'SECURITY.md',
    'docs/00-*.md',
    'docs/04-*.md',
    'docs/05-*.md',
    'docs/06-*.md',
    'docs/08-*.md',
    'docs/09-*.md',
    'docs/10-*.md',
    'docs/11-*.md',
    'docs/15-*.md',
    'docs/16-*.md'
)
$allowedReferences = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::OrdinalIgnoreCase)
foreach ($path in @(
        'backend/src/test/java/com/portfolio/agent/common/web/RetiredVersionedApiContractTest.java',
        'scripts/public-api-surface-check.ps1',
        'scripts/public-api-surface-check.test.ps1')) {
    [void]$allowedReferences.Add($path)
}
$textExtensions = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::OrdinalIgnoreCase)
foreach ($extension in @(
        '.java', '.ts', '.tsx', '.vue', '.js', '.mjs', '.cjs', '.ps1',
        '.md', '.json', '.yml', '.yaml')) {
    [void]$textExtensions.Add($extension)
}

function Get-RelativePath([string]$FullPath) {
    $rootUri = [Uri]::new($rootPrefix)
    $fileUri = [Uri]::new($FullPath)
    return [Uri]::UnescapeDataString(
        $rootUri.MakeRelativeUri($fileUri).ToString()).Replace('\', '/')
}

function Test-File([string]$FullPath) {
    $relativePath = Get-RelativePath $FullPath
    if ($allowedReferences.Contains($relativePath)) { return }
    $lines = @(Get-Content -LiteralPath $FullPath -Encoding UTF8)
    for ($index = 0; $index -lt $lines.Count; $index++) {
        if ($pattern.IsMatch([string]$lines[$index])) {
            $issues.Add(
                "[public-api-surface-check] RETIRED_PUBLIC_API_REFERENCE ${relativePath}:$($index + 1)") | Out-Null
        }
    }
}

foreach ($relativeDirectory in $scanDirectories) {
    $fullDirectory = Join-Path $resolvedRoot $relativeDirectory
    if (-not (Test-Path -LiteralPath $fullDirectory -PathType Container)) { continue }
    foreach ($file in Get-ChildItem -LiteralPath $fullDirectory -File -Recurse) {
        if ($textExtensions.Contains($file.Extension)) {
            Test-File $file.FullName
        }
    }
}

foreach ($relativeFile in $scanFiles) {
    $fullFile = Join-Path $resolvedRoot $relativeFile
    if (Test-Path -LiteralPath $fullFile -PathType Leaf) {
        Test-File $fullFile
    }
}

foreach ($relativePattern in $currentDocumentPatterns) {
    $matches = @(Get-ChildItem -Path (Join-Path $resolvedRoot $relativePattern) -File)
    if ($matches.Count -ne 1) {
        $issues.Add(
            "[public-api-surface-check] CURRENT_AUTHORITY_MATCH_COUNT ${relativePattern}:0") | Out-Null
        continue
    }
    Test-File $matches[0].FullName
}

if ($issues.Count -gt 0) {
    $issues | Sort-Object | Write-Output
    Write-Output "Public API surface check failed with $($issues.Count) issue(s)."
    exit 1
}

Write-Output 'Public API surface check passed.'
