param(
    [int]$ExitCode = 42,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ViteArguments
)

$ErrorActionPreference = 'Stop'
$argsFile = [Environment]::GetEnvironmentVariable(
    'PORTFOLIO_FIXTURE_ARGS_FILE',
    [EnvironmentVariableTarget]::Process
)
if (-not [string]::IsNullOrWhiteSpace($argsFile)) {
    [System.IO.File]::WriteAllText(
        $argsFile,
        ($ViteArguments -join '|'),
        [System.Text.UTF8Encoding]::new($false)
    )
}
Write-Output ("$([char]27)[31mfixture-stdout-marker$([char]27)[0m repo=" +
    [string]$env:PORTFOLIO_FIXTURE_REPOSITORY_ROOT)
[Console]::Error.WriteLine('fixture-stderr-marker')
Start-Sleep -Milliseconds 1500
exit $ExitCode
