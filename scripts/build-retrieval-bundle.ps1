param(
    [Parameter(Mandatory = $true)]
    [string]$CandidateDirectory,
    [string]$JarPath = '',
    [string]$JavaExecutable = 'java.exe'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$candidate = (Resolve-Path -LiteralPath $CandidateDirectory -ErrorAction Stop).Path
$repositoryRoot = (Resolve-Path -LiteralPath $root).Path
$candidatePrefix = $candidate.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
$repositoryPrefix = $repositoryRoot.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
if ($candidate.Equals($repositoryRoot, [StringComparison]::OrdinalIgnoreCase) -or
        $candidate.StartsWith($repositoryPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Retrieval candidate workspace must be outside the repository.'
}
$candidateItem = Get-Item -LiteralPath $candidate -Force
if (($candidateItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
    throw 'Retrieval candidate cannot be a symlink or junction.'
}
$names = @(Get-ChildItem -LiteralPath $candidate -File |
    ForEach-Object { $_.Name } | Sort-Object)
if (($names -join ',') -ne 'portfolio.json,presentation.json') {
    throw 'Retrieval candidate must initially contain only canonical portfolio and presentation files.'
}
$portfolioFile = Join-Path $candidate 'portfolio.json'
$portfolio = Get-Content -LiteralPath $portfolioFile -Raw -Encoding UTF8 | ConvertFrom-Json
$contentVersion = [string]$portfolio.contentVersion
if ($contentVersion -notmatch '^[0-9]{4}-[0-9]{2}-[0-9]{2}\.[0-9]+$') {
    throw 'Retrieval candidate contentVersion is invalid.'
}
$resolvedJar = if ([string]::IsNullOrWhiteSpace($JarPath)) {
    (Resolve-Path -LiteralPath (Join-Path $root 'backend\target\portfolio-agent.jar') `
        -ErrorAction Stop).Path
} else {
    (Resolve-Path -LiteralPath $JarPath -ErrorAction Stop).Path
}
$output = Join-Path $candidate 'rag-documents.jsonl'
$loaderArgument = '-Dloader.main=com.portfolio.agent.release.RagDocumentCompilerCli'
$arguments = @(
    $loaderArgument,
    '-cp', $resolvedJar,
    'org.springframework.boot.loader.launch.PropertiesLauncher',
    '--portfolio', $portfolioFile,
    '--output', $output,
    '--valid-from', $contentVersion.Substring(0, 10)
)
$previousErrorAction = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try { $compilerOutput = & $JavaExecutable @arguments 2>&1 }
finally { $ErrorActionPreference = $previousErrorAction }
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $output -PathType Leaf)) {
    if (Test-Path -LiteralPath $output) { Remove-Item -LiteralPath $output -Force }
    throw 'Canonical RAG document compilation failed.'
}
& powershell.exe -NoProfile -ExecutionPolicy Bypass `
    -File (Join-Path $root 'scripts\privacy-check.ps1') -Path $candidate *> $null
if ($LASTEXITCODE -ne 0) {
    Remove-Item -LiteralPath $output -Force
    throw 'Canonical retrieval candidate failed privacy scanning.'
}
Write-Output 'Canonical retrieval candidate payload prepared; human review and Approval are still required.'
