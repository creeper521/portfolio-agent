$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$checker = Join-Path $PSScriptRoot 'persistence-safe-replay-docs-check.ps1'
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('persistence-safe-replay-docs-' + [guid]::NewGuid().ToString('N'))

function Invoke-Checker([string]$path) {
    $output = & pwsh.exe -NoProfile -File $checker -RootPath $path 2>&1
    return [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output = ($output -join [Environment]::NewLine)
    }
}

try {
    New-Item -ItemType Directory -Path (Join-Path $temporaryRoot 'docs') -Force | Out-Null
    foreach ($relativePath in @(
        'AGENTS.md', 'SECURITY.md', 'docs/08-当前实现状态.md',
        'docs/15-Agent 2.0真实交互问题清单与修复边界.md',
        'docs/agent-architecture-status.json')) {
        Copy-Item -LiteralPath (Join-Path $root $relativePath) `
            -Destination (Join-Path $temporaryRoot $relativePath)
    }
    $passing = Invoke-Checker $temporaryRoot
    if ($passing.ExitCode -ne 0) {
        throw "canonical replay documentation fixture must pass: $($passing.Output)"
    }

    Set-Content -LiteralPath (Join-Path $temporaryRoot 'docs/08-当前实现状态.md') `
        -Value 'Provider 派生正文可精确恢复。' -Encoding UTF8
    $drifted = Invoke-Checker $temporaryRoot
    if ($drifted.ExitCode -eq 0 -or $drifted.Output -notmatch 'REPLAY_BODY_NOT_RETAINED') {
        throw 'replay documentation drift must fail with the missing contract token'
    }
    Write-Output 'PERSISTENCE_SAFE_REPLAY_DOCS_TESTS_OK tests=2'
} finally {
    Remove-Item -LiteralPath $temporaryRoot -Recurse -Force -ErrorAction SilentlyContinue
}
