$ErrorActionPreference = 'Stop'

$checker = Join-Path $PSScriptRoot 'public-api-surface-check.ps1'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('portfolio-public-api-check-' + [guid]::NewGuid().ToString('N'))

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Write-Utf8File([string]$Path, [string]$Content) {
    $parent = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    try {
        [System.IO.File]::WriteAllText(
            $Path, $Content, [System.Text.UTF8Encoding]::new($false))
    }
    catch {
        throw "Failed to write fixture path '$Path': $($_.Exception.Message)"
    }
}

try {
    foreach ($currentDocument in @(
            'README.md',
            'AGENTS.md',
            'SECURITY.md',
            'docs/00-current.md',
            'docs/04-current.md',
            'docs/05-current.md',
            'docs/06-current.md',
            'docs/08-current.md',
            'docs/09-current.md',
            'docs/10-current.md',
            'docs/11-current.md',
            'docs/15-current.md',
            'docs/16-current.md')) {
        Write-Utf8File (Join-Path $fixtureRoot $currentDocument) `
            '# Current API: /api/portfolio'
    }
    Write-Utf8File (Join-Path $fixtureRoot `
            'backend/src/test/java/com/portfolio/agent/common/web/RetiredVersionedApiContractTest.java') `
        'final class RetiredVersionedApiContractTest { String path = "/api/v1/portfolio"; }'
    Write-Utf8File (Join-Path $fixtureRoot 'frontend/src/api.ts') `
        "fetch('/api/portfolio')"

    $positive = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File $checker -RootPath $fixtureRoot 2>&1 | Out-String)
    Assert-True ($LASTEXITCODE -eq 0) `
        "Approved retirement fixture must pass. Output: $positive"

    Write-Utf8File (Join-Path $fixtureRoot 'frontend/src/leak.ts') `
        "fetch('/api/v1/public-content')"
    $negative = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File $checker -RootPath $fixtureRoot 2>&1 | Out-String)
    Assert-True ($LASTEXITCODE -ne 0) 'Active versioned route must fail.'
    Assert-True ($negative -match 'RETIRED_PUBLIC_API_REFERENCE') `
        "Failure must use the stable issue code. Output: $negative"
    Assert-True ($negative -match 'frontend/src/leak.ts:1') `
        "Failure must identify the exact source line. Output: $negative"

    Remove-Item -LiteralPath (Join-Path $fixtureRoot 'frontend/src/leak.ts')
    Write-Utf8File (Join-Path $fixtureRoot 'frontend/e2e/leak.spec.ts') `
        "fetch('/api/v1/portfolio')"
    Write-Utf8File (Join-Path $fixtureRoot 'frontend/playwright.config.ts') `
        "const baseURL = '/api/v1/portfolio'"
    Write-Utf8File (Join-Path $fixtureRoot 'contracts/agent-turn/legacy.json') `
        '{"endpoint":"/api/v1/public-content"}'
    Write-Utf8File (Join-Path $fixtureRoot 'frontend/vite.config.ts') `
        "const proxyTarget = '/api/v1/portfolio'"
    Write-Utf8File (Join-Path $fixtureRoot 'frontend/index.html') `
        '<meta name="api" content="/api/v1/public-content">'
    Write-Utf8File (Join-Path $fixtureRoot 'frontend/package.json') `
        '{"scripts":{"legacy":"echo /api/v1/portfolio"}}'
    Write-Utf8File (Join-Path $fixtureRoot 'backend/pom.xml') `
        '<endpoint>/api/v1/public-content</endpoint>'
    $frontendGate = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File $checker -RootPath $fixtureRoot 2>&1 | Out-String)
    Assert-True ($LASTEXITCODE -ne 0) 'E2E and Playwright config references must fail.'
    Assert-True ($frontendGate -match 'frontend/e2e/leak.spec.ts:1') `
        "E2E failure must identify its source. Output: $frontendGate"
    Assert-True ($frontendGate -match 'frontend/playwright.config.ts:1') `
        "Playwright config failure must identify its source. Output: $frontendGate"
    foreach ($expectedPath in @(
            'contracts/agent-turn/legacy.json:1',
            'frontend/vite.config.ts:1',
            'frontend/index.html:1',
            'frontend/package.json:1',
            'backend/pom.xml:1')) {
        Assert-True ($frontendGate -match [regex]::Escape($expectedPath)) `
            "Surface failure must identify '$expectedPath'. Output: $frontendGate"
    }

    Write-Output 'Public API surface checker tests passed.'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
}
