$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Assert-Equal {
    param($Expected, $Actual, [string]$Message)
    if ($Expected -ne $Actual) {
        throw "$Message. Expected=[$Expected] Actual=[$Actual]"
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$homeDirectory = 'C:\Users\local-log-user'
$fixedNow = [DateTimeOffset]::Parse('2026-07-31T12:34:56.789+08:00')
$modulePath = Join-Path $PSScriptRoot 'logging\LocalLogRouter.psm1'

Import-Module $modulePath -Force

$cases = @(
    @{
        Stream = 'BACKEND_STDOUT'
        Line = '2026-07-31 INFO com.portfolio.agent.diagnostics - http.request.completed'
        Domain = 'BACKEND'
        Level = 'INFO'
        Source = 'SPRING'
    },
    @{
        Stream = 'BACKEND_STDOUT'
        Line = '2026-07-31 ERROR com.portfolio.agent.diagnostics - http.request.failed'
        Domain = 'BACKEND'
        Level = 'ERROR'
        Source = 'SPRING'
    },
    @{
        Stream = 'BACKEND_STDOUT'
        Line = 'INFO event.origin=browser event.name=frontend.agent.request.completed'
        Domain = 'FRONTEND'
        Level = 'INFO'
        Source = 'BROWSER'
    },
    @{
        Stream = 'VITE_STDOUT'
        Line = '[vite] hmr update /src/App.vue'
        Domain = 'FRONTEND'
        Level = 'INFO'
        Source = 'VITE'
    },
    @{
        Stream = 'VITE_STDERR'
        Line = '[vite] Internal server error'
        Domain = 'FRONTEND'
        Level = 'ERROR'
        Source = 'VITE'
    },
    @{
        Stream = 'LAUNCHER'
        Line = 'WARN dependency probe was slow'
        Domain = 'BACKEND'
        Level = 'WARN'
        Source = 'LAUNCHER'
    }
)

foreach ($case in $cases) {
    $record = ConvertTo-LocalLogRecord `
        -Stream $case.Stream `
        -Line $case.Line `
        -RepositoryRoot $repositoryRoot `
        -HomeDirectory $homeDirectory `
        -Now $fixedNow
    Assert-Equal $case.Domain $record.Domain "$($case.Stream) domain"
    Assert-Equal $case.Level $record.Level "$($case.Stream) level"
    Assert-Equal $case.Source $record.Source "$($case.Stream) source"
}

$formatted = Format-LocalLogRecord -Record (ConvertTo-LocalLogRecord `
    -Stream 'BACKEND_STDOUT' `
    -Line 'INFO application.started' `
    -RepositoryRoot $repositoryRoot `
    -HomeDirectory $homeDirectory `
    -Now $fixedNow)
Assert-Equal `
    '2026-07-31T12:34:56.789+08:00 [BACKEND][INFO][SPRING] INFO application.started' `
    $formatted `
    'formatted record'

$sanitized = ConvertTo-LocalLogRecord `
    -Stream 'BACKEND_STDOUT' `
    -Line "$([char]27)[31mINFO$([char]27)[0m repo=$repositoryRoot home=$homeDirectory url=https://example.test/path?token=secret#fragment`0`tvalue" `
    -RepositoryRoot $repositoryRoot `
    -HomeDirectory $homeDirectory `
    -Now $fixedNow
Assert-True (-not $sanitized.Text.Contains([char]27)) 'ANSI escape must be removed'
Assert-True (-not $sanitized.Text.Contains($repositoryRoot)) 'Repository path must be replaced'
Assert-True (-not $sanitized.Text.Contains($homeDirectory)) 'Home path must be replaced'
Assert-True (-not $sanitized.Text.Contains('token=secret')) 'URL query must be removed'
Assert-True (-not $sanitized.Text.Contains('fragment')) 'URL fragment must be removed'
Assert-True (-not $sanitized.Text.Contains([char]0)) 'Control characters must be removed'
Assert-True $sanitized.Redacted 'Sanitized record must be marked redacted'

$credentialLines = @(
    'Authorization: Bearer top-secret-token',
    'api_key=top-secret-token',
    '-----BEGIN PRIVATE KEY-----',
    'password=top-secret-token'
)
foreach ($line in $credentialLines) {
    $record = ConvertTo-LocalLogRecord `
        -Stream 'BACKEND_STDERR' `
        -Line $line `
        -RepositoryRoot $repositoryRoot `
        -HomeDirectory $homeDirectory `
        -Now $fixedNow
    Assert-Equal 'OUTPUT_REDACTED reason=CREDENTIAL_PATTERN' $record.Text 'Credential line'
    Assert-True $record.Redacted 'Credential line redacted flag'
}

$longRecord = ConvertTo-LocalLogRecord `
    -Stream 'VITE_STDOUT' `
    -Line ('x' * 9000) `
    -RepositoryRoot $repositoryRoot `
    -HomeDirectory $homeDirectory `
    -Now $fixedNow
Assert-True ($longRecord.Text.Length -le 8192) 'Log text must be capped at 8 KB'
Assert-True $longRecord.Text.EndsWith('...[TRUNCATED]') 'Long line must have a truncation marker'
Assert-True $longRecord.Redacted 'Truncated line must be marked redacted'

$ignoreFile = Get-Content -LiteralPath (Join-Path $repositoryRoot '.gitignore') -Raw
Assert-True ($ignoreFile -match '(?m)^/logs/$') '/logs/ must be ignored'

Write-Output 'local-log-router tests passed'
