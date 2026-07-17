param(
    [Parameter(Mandatory = $true)]
    [string]$DistPath,
    [Parameter(Mandatory = $true)]
    [string]$PackagedStaticPath
)

$ErrorActionPreference = 'Stop'

function Get-OrdinalFileMap([string]$RootPath) {
    if (-not (Test-Path -LiteralPath $RootPath -PathType Container)) {
        throw "Static bundle path does not exist: $RootPath"
    }

    $resolvedRoot = (Resolve-Path -LiteralPath $RootPath).Path.TrimEnd('\', '/')
    $files = [System.Collections.Generic.Dictionary[string, string]]::new(
        [System.StringComparer]::Ordinal
    )
    foreach ($file in Get-ChildItem -LiteralPath $resolvedRoot -Recurse -File) {
        $relativePath = $file.FullName.Substring($resolvedRoot.Length)
        $relativePath = $relativePath.TrimStart('\', '/')
        $relativePath = $relativePath.Replace('\', '/')
        if ($files.ContainsKey($relativePath)) {
            throw "Static bundle contains duplicate ordinal path '$relativePath'."
        }
        $files.Add($relativePath, $file.FullName)
    }
    return ,$files
}

$distFiles = Get-OrdinalFileMap $DistPath
$packagedFiles = Get-OrdinalFileMap $PackagedStaticPath

if (-not $distFiles.ContainsKey('index.html')) {
    throw 'frontend/dist does not contain index.html.'
}

$missingPaths = [System.Collections.Generic.List[string]]::new()
foreach ($relativePath in $distFiles.Keys) {
    if (-not $packagedFiles.ContainsKey($relativePath)) {
        $missingPaths.Add($relativePath)
    }
}

$unexpectedPaths = [System.Collections.Generic.List[string]]::new()
foreach ($relativePath in $packagedFiles.Keys) {
    if (-not $distFiles.ContainsKey($relativePath)) {
        $unexpectedPaths.Add($relativePath)
    }
}

if ($missingPaths.Count -gt 0 -or $unexpectedPaths.Count -gt 0) {
    $missingPaths.Sort([System.StringComparer]::Ordinal)
    $unexpectedPaths.Sort([System.StringComparer]::Ordinal)
    throw "Packaged static paths differ from frontend/dist using ordinal comparison. Missing: $($missingPaths -join ', '). Unexpected: $($unexpectedPaths -join ', ')."
}

$hashMismatches = [System.Collections.Generic.List[string]]::new()
foreach ($relativePath in $distFiles.Keys) {
    $distHash = (Get-FileHash -LiteralPath $distFiles[$relativePath] -Algorithm SHA256).Hash
    $packagedHash = (
        Get-FileHash -LiteralPath $packagedFiles[$relativePath] -Algorithm SHA256
    ).Hash
    if (-not [System.StringComparer]::Ordinal.Equals($distHash, $packagedHash)) {
        $hashMismatches.Add($relativePath)
    }
}

if ($hashMismatches.Count -gt 0) {
    $hashMismatches.Sort([System.StringComparer]::Ordinal)
    throw "Packaged static content hash differs from frontend/dist: $($hashMismatches -join ', ')."
}

Write-Output "Static bundle verification passed for $($distFiles.Count) file(s)."
