$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$script = Join-Path $PSScriptRoot 'build-retrieval-bundle.ps1'
$jar = Join-Path $root 'backend\target\portfolio-agent.jar'
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) `
    ('build-retrieval-bundle-' + [guid]::NewGuid().ToString('N'))
$candidate = Join-Path $fixtureRoot 'candidate'

try {
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
        throw 'Packaged JAR is required for retrieval bundle script tests.'
    }
    New-Item -ItemType Directory -Force -Path $candidate | Out-Null
    Copy-Item -LiteralPath (Join-Path $root `
        'backend\src\main\resources\public-data\bundle\portfolio.json') -Destination $candidate
    Copy-Item -LiteralPath (Join-Path $root `
        'backend\src\main\resources\public-data\bundle\presentation.json') -Destination $candidate

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $script `
        -CandidateDirectory $candidate -JarPath $jar *> $null
    if ($LASTEXITCODE -ne 0 -or
            -not (Test-Path -LiteralPath (Join-Path $candidate 'rag-documents.jsonl') -PathType Leaf)) {
        throw 'Canonical RAG candidate was not created.'
    }
    $names = @(Get-ChildItem -LiteralPath $candidate -File |
        ForEach-Object { $_.Name } | Sort-Object)
    if (($names -join ',') -ne 'portfolio.json,presentation.json,rag-documents.jsonl') {
        throw 'Candidate preparation changed the closed canonical payload file set.'
    }

    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $script `
            -CandidateDirectory $candidate -JarPath $jar *> $null
        $repeatExitCode = $LASTEXITCODE
    }
    finally { $ErrorActionPreference = $previousErrorAction }
    if ($repeatExitCode -eq 0) {
        throw 'Candidate preparation must refuse to overwrite existing canonical RAG bytes.'
    }

    Write-Output 'build-retrieval-bundle tests passed'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
}
