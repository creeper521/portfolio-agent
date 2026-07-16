$ErrorActionPreference = 'Stop'

$checker = Join-Path $PSScriptRoot 'code-quality-check.ps1'
$tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd([System.IO.Path]::DirectorySeparatorChar)
$fixtureRoot = Join-Path $tempRoot ('code-quality-check-' + [guid]::NewGuid())

$unsafeCases = [ordered]@{
    'var-local' = @{
        Rule = 'var-local'
        Source = 'class Sample { void run() { var value = "x"; } }'
    }
    'record-type' = @{
        Rule = 'record-type'
        Source = 'record Sample(String value) {}'
    }
    'lombok-import' = @{
        Rule = 'lombok-import'
        Source = "import lombok.Data;`nclass Sample {}"
    }
    'lombok-data' = @{
        Rule = 'lombok-qualified-annotation'
        Source = '@lombok.Data class Sample {}'
    }
}
$safeSource = @'
import org.springframework.beans.factory.annotation.Value;

final class Sample {
    private final String value;
    Sample(@Value("classpath:data.json") String value) { this.value = value; }
    String getValue() { return value; }
}
'@

function Invoke-Checker([string]$SourcePath) {
    $output = (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker -Path $SourcePath 2>&1 | Out-String)
    return @{
        ExitCode = $LASTEXITCODE
        Output = $output
    }
}

try {
    if (-not (Test-Path -LiteralPath $checker -PathType Leaf)) {
        throw "Checker does not exist: $checker"
    }

    New-Item -ItemType Directory -Path $fixtureRoot | Out-Null

    foreach ($case in $unsafeCases.GetEnumerator()) {
        $casePath = Join-Path $fixtureRoot $case.Key
        New-Item -ItemType Directory -Path $casePath | Out-Null
        $samplePath = Join-Path $casePath 'Sample.java'
        Set-Content -LiteralPath $samplePath -Value $case.Value.Source

        $result = Invoke-Checker $casePath
        if ($result.ExitCode -eq 0) {
            throw "Expected unsafe fixture '$($case.Key)' to fail. Output: $($result.Output)"
        }

        $expectedPrefix = "$($case.Value.Rule):$($samplePath):"
        $matchingViolation = @($result.Output -split '\r?\n' | Where-Object {
            $_.StartsWith($expectedPrefix, [System.StringComparison]::Ordinal)
        }) | Select-Object -First 1
        if ($null -eq $matchingViolation) {
            throw "Expected unsafe fixture '$($case.Key)' output to include '$expectedPrefix'. Output: $($result.Output)"
        }

        $lineAndSource = $matchingViolation.Substring($expectedPrefix.Length)
        $separatorIndex = $lineAndSource.IndexOf(':')
        if ($separatorIndex -lt 1) {
            throw "Expected unsafe fixture '$($case.Key)' output to include a line number. Output: $matchingViolation"
        }
        $lineNumber = 0
        $lineNumberText = $lineAndSource.Substring(0, $separatorIndex)
        if (-not [int]::TryParse($lineNumberText, [ref]$lineNumber) -or $lineNumber -lt 1) {
            throw "Expected unsafe fixture '$($case.Key)' line number to be a positive integer. Output: $matchingViolation"
        }
    }

    $safePath = Join-Path $fixtureRoot 'safe-source'
    New-Item -ItemType Directory -Path $safePath | Out-Null
    Set-Content -LiteralPath (Join-Path $safePath 'Sample.java') -Value $safeSource

    $safeResult = Invoke-Checker $safePath
    if ($safeResult.ExitCode -ne 0) {
        throw "Expected safe fixture to pass. Output: $($safeResult.Output)"
    }

    Write-Output 'code-quality-check tests passed'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolvedFixtureRoot = (Resolve-Path -LiteralPath $fixtureRoot).Path
        $fixtureParent = [System.IO.Path]::GetDirectoryName($resolvedFixtureRoot).TrimEnd([System.IO.Path]::DirectorySeparatorChar)
        if ($fixtureParent -ne $tempRoot -or -not ([System.IO.Path]::GetFileName($resolvedFixtureRoot).StartsWith('code-quality-check-'))) {
            throw "Refusing to remove unverified fixture path: $resolvedFixtureRoot"
        }
        Remove-Item -LiteralPath $resolvedFixtureRoot -Recurse -Force
    }
}
