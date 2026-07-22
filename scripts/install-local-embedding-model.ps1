param(
    [string]$DescriptorPath = '',
    [string]$DestinationRoot = '',
    [string]$SourceDirectory = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($DescriptorPath)) {
    $DescriptorPath = Join-Path $root `
        'backend\src\main\resources\embedding-models\bge-small-zh-v1.5-int8.json'
}
if ([string]::IsNullOrWhiteSpace($DestinationRoot)) {
    $DestinationRoot = Join-Path $root 'runtime-models'
}

function Assert-Hex([string]$Value, [int]$Length, [string]$Field) {
    if ($Value -notmatch ('^[0-9a-f]{' + $Length + '}$')) {
        throw "$Field is invalid."
    }
}

function Resolve-ChildPath([string]$Base, [string]$Relative) {
    if ([string]::IsNullOrWhiteSpace($Relative) -or $Relative.Contains('..') `
            -or [System.IO.Path]::IsPathRooted($Relative)) {
        throw 'Model file path is invalid.'
    }
    $basePath = [System.IO.Path]::GetFullPath($Base)
    $candidate = [System.IO.Path]::GetFullPath(
        (Join-Path $basePath ($Relative.Replace('/', '\'))))
    $prefix = $basePath.TrimEnd('\') + '\'
    if (-not $candidate.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Model file escapes the installation directory.'
    }
    return $candidate
}

$descriptor = Get-Content -LiteralPath $DescriptorPath -Raw -Encoding utf8 | ConvertFrom-Json
if ($descriptor.schemaVersion -ne '1.0') { throw 'Unsupported model descriptor schemaVersion.' }
if ($descriptor.repository -notmatch '^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$') {
    throw 'Model repository is invalid.'
}
Assert-Hex $descriptor.revision 40 'revision'
if ($null -eq $descriptor.files -or $descriptor.files.Count -eq 0) {
    throw 'Model descriptor files are required.'
}

$installDirectory = if ([string]::IsNullOrWhiteSpace($descriptor.installDirectory)) {
    $descriptor.modelId.Replace('/', '--')
} else {
    $descriptor.installDirectory
}
if ($installDirectory -notmatch '^[A-Za-z0-9._-]+$') {
    throw 'Model installDirectory is invalid.'
}

$destinationRootPath = [System.IO.Path]::GetFullPath($DestinationRoot)
New-Item -ItemType Directory -Force -Path $destinationRootPath | Out-Null
$target = Resolve-ChildPath $destinationRootPath $installDirectory
if (Test-Path -LiteralPath $target) {
    throw 'Model installation target already exists.'
}
$temporary = Resolve-ChildPath $destinationRootPath ('.install-' + [guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $temporary | Out-Null

try {
    foreach ($file in $descriptor.files) {
        Assert-Hex $file.sha256 64 ('sha256 for ' + $file.path)
        if ([long]$file.size -le 0) { throw ('Invalid size for ' + $file.path) }
        $temporaryFile = Resolve-ChildPath $temporary $file.path
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $temporaryFile) `
            | Out-Null
        if ([string]::IsNullOrWhiteSpace($SourceDirectory)) {
            $url = 'https://huggingface.co/' + $descriptor.repository + '/resolve/' `
                + $descriptor.revision + '/' + $file.path
            Invoke-WebRequest -Uri $url -OutFile $temporaryFile -UseBasicParsing
        } else {
            $sourceFile = Resolve-ChildPath $SourceDirectory $file.path
            if (-not (Test-Path -LiteralPath $sourceFile -PathType Leaf)) {
                throw ('Source model file is missing: ' + $file.path)
            }
            Copy-Item -LiteralPath $sourceFile -Destination $temporaryFile
        }
        $actualFile = Get-Item -LiteralPath $temporaryFile
        if ($actualFile.Length -ne [long]$file.size) {
            throw ('Model file size mismatch: ' + $file.path)
        }
        $actualHash = (Get-FileHash -LiteralPath $temporaryFile -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualHash -ne $file.sha256) {
            throw ('Model file hash mismatch: ' + $file.path)
        }
    }
    Move-Item -LiteralPath $temporary -Destination $target
    Write-Host "Installed verified local embedding model to $target"
} finally {
    if (Test-Path -LiteralPath $temporary) {
        Remove-Item -LiteralPath $temporary -Recurse -Force
    }
}
