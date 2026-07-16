$ErrorActionPreference = 'Stop'

$checker = Join-Path $PSScriptRoot 'privacy-check.ps1'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('portfolio-privacy-' + [guid]::NewGuid())
$safeRoot = Join-Path $fixtureRoot 'safe'
$unsafeCases = [ordered]@{
    'ipv4-address' = 'host=192.168.10.24'
    'windows-absolute-path' = 'path=C:\Users\internal\report.md'
    'internal-linux-path' = 'path=/data/server/private/report.md'
    'credential-assignment' = 'password=secret'
    'internal-hostname' = 'service=https://sql-audit.private.corp/api'
}

try {
    New-Item -ItemType Directory -Force -Path $safeRoot | Out-Null
    Set-Content -LiteralPath (Join-Path $safeRoot 'content.json') `
        -Value 'Public portfolio contains reviewed content only.' `
        -Encoding UTF8

    foreach ($case in $unsafeCases.GetEnumerator()) {
        $caseRoot = Join-Path $fixtureRoot $case.Key
        New-Item -ItemType Directory -Force -Path $caseRoot | Out-Null
        Set-Content -LiteralPath (Join-Path $caseRoot 'content.json') `
            -Value $case.Value `
            -Encoding UTF8

        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker -Path $caseRoot *> $null
        if ($LASTEXITCODE -eq 0) {
            throw "Expected privacy rule $($case.Key) to reject its fixture."
        }
    }

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker -Path $safeRoot *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Expected safe fixture to pass privacy check.'
    }

    Write-Output 'privacy-check tests passed'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
}
