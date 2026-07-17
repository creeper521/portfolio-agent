$ErrorActionPreference = 'Stop'

$checker = Join-Path $PSScriptRoot 'verify-static-bundle.ps1'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('portfolio-static-bundle-' + [guid]::NewGuid())

function Write-FixtureFile([string]$Path, [string]$Content) {
    New-Item -ItemType Directory -Path (Split-Path -Parent $Path) -Force | Out-Null
    [System.IO.File]::WriteAllText(
        $Path,
        $Content,
        [System.Text.UTF8Encoding]::new($false)
    )
}

function Invoke-Checker([string]$DistPath, [string]$PackagedPath) {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker `
            -DistPath $DistPath -PackagedStaticPath $PackagedPath 2>&1 | Out-String)
        return @{
            ExitCode = $LASTEXITCODE
            Output = $output
        }
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

try {
    if (-not (Test-Path -LiteralPath $checker -PathType Leaf)) {
        throw "Static bundle checker does not exist: $checker"
    }

    $matchingDist = Join-Path $fixtureRoot 'matching\dist'
    $matchingPackaged = Join-Path $fixtureRoot 'matching\packaged'
    Write-FixtureFile (Join-Path $matchingDist 'index.html') '<main>portfolio</main>'
    Write-FixtureFile (Join-Path $matchingDist 'assets\App.js') 'console.log("public")'
    Write-FixtureFile (Join-Path $matchingPackaged 'index.html') '<main>portfolio</main>'
    Write-FixtureFile (Join-Path $matchingPackaged 'assets\App.js') 'console.log("public")'

    $matchingResult = Invoke-Checker $matchingDist $matchingPackaged
    if ($matchingResult.ExitCode -ne 0) {
        throw "Expected identical bundles to pass. Output: $($matchingResult.Output)"
    }

    $caseDist = Join-Path $fixtureRoot 'case\dist'
    $casePackaged = Join-Path $fixtureRoot 'case\packaged'
    Write-FixtureFile (Join-Path $caseDist 'index.html') 'case-index'
    Write-FixtureFile (Join-Path $casePackaged 'index.html') 'case-index'
    Write-FixtureFile (Join-Path $caseDist 'assets\App.js') 'same-content'
    Write-FixtureFile (Join-Path $casePackaged 'assets\app.js') 'same-content'

    $caseResult = Invoke-Checker $caseDist $casePackaged
    if ($caseResult.ExitCode -eq 0) {
        throw 'Expected an ordinal case-only path mismatch to fail.'
    }
    if (
        $caseResult.Output -notmatch 'ordinal comparison' -or
        $caseResult.Output -notmatch 'assets/App\.js' -or
        $caseResult.Output -notmatch 'assets/app\.js'
    ) {
        throw "Expected a precise ordinal path mismatch. Output: $($caseResult.Output)"
    }

    $hashDist = Join-Path $fixtureRoot 'hash\dist'
    $hashPackaged = Join-Path $fixtureRoot 'hash\packaged'
    Write-FixtureFile (Join-Path $hashDist 'index.html') 'hash-index'
    Write-FixtureFile (Join-Path $hashPackaged 'index.html') 'hash-index'
    Write-FixtureFile (Join-Path $hashDist 'assets\App.js') 'expected-content'
    Write-FixtureFile (Join-Path $hashPackaged 'assets\App.js') 'different-content'

    $hashResult = Invoke-Checker $hashDist $hashPackaged
    if ($hashResult.ExitCode -eq 0) {
        throw 'Expected equal paths with different content hashes to fail.'
    }
    if ($hashResult.Output -notmatch 'content hash differs.*assets/App\.js') {
        throw "Expected a precise content hash mismatch. Output: $($hashResult.Output)"
    }

    Write-Output 'verify-static-bundle tests passed'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolvedFixtureRoot = (Resolve-Path -LiteralPath $fixtureRoot).Path
        if (-not ([System.IO.Path]::GetFileName($resolvedFixtureRoot)).StartsWith(
            'portfolio-static-bundle-'
        )) {
            throw "Refusing to remove unverified fixture path: $resolvedFixtureRoot"
        }
        Remove-Item -LiteralPath $resolvedFixtureRoot -Recurse -Force
    }
}
