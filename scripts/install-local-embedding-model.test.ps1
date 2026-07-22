$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$script = Join-Path $PSScriptRoot 'install-local-embedding-model.ps1'
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('embedding-install-test-' + [guid]::NewGuid())

function Get-Sha256([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

try {
    $source = Join-Path $testRoot 'source'
    $destination = Join-Path $testRoot 'runtime-models'
    New-Item -ItemType Directory -Force -Path (Join-Path $source 'onnx') | Out-Null
    [System.IO.File]::WriteAllText((Join-Path $source 'tokenizer.json'), 'tokenizer')
    [System.IO.File]::WriteAllText((Join-Path $source 'onnx\model.onnx'), 'model')
    $descriptorPath = Join-Path $testRoot 'descriptor.json'
    $descriptor = [ordered]@{
        schemaVersion = '1.0'
        modelId = 'test/model'
        repository = 'test/model'
        revision = '0123456789012345678901234567890123456789'
        files = @(
            [ordered]@{
                path = 'onnx/model.onnx'
                size = (Get-Item -LiteralPath (Join-Path $source 'onnx\model.onnx')).Length
                sha256 = Get-Sha256 (Join-Path $source 'onnx\model.onnx')
            },
            [ordered]@{
                path = 'tokenizer.json'
                size = (Get-Item -LiteralPath (Join-Path $source 'tokenizer.json')).Length
                sha256 = Get-Sha256 (Join-Path $source 'tokenizer.json')
            }
        )
    }
    $descriptor | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $descriptorPath -Encoding utf8

    & $script -DescriptorPath $descriptorPath -DestinationRoot $destination `
        -SourceDirectory $source
    if (-not (Test-Path -LiteralPath (Join-Path $destination 'test--model\onnx\model.onnx'))) {
        throw 'model file was not installed'
    }

    [System.IO.File]::WriteAllText((Join-Path $source 'tokenizer.json'), 'tampered')
    $failureDestination = Join-Path $testRoot 'failed-runtime-models'
    $failed = $false
    try {
        & $script -DescriptorPath $descriptorPath -DestinationRoot $failureDestination `
            -SourceDirectory $source
    } catch {
        $failed = $true
    }
    if (-not $failed) { throw 'hash mismatch was accepted' }
    if (Test-Path -LiteralPath (Join-Path $failureDestination 'test--model')) {
        throw 'failed installation left a model directory'
    }

    Write-Host 'Local embedding installer tests passed.'
} finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
