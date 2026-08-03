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

Import-Module $modulePath -Force -DisableNameChecking

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

$ignoreLines = @(Get-Content -LiteralPath (Join-Path $repositoryRoot '.gitignore'))
Assert-True ($ignoreLines -contains '/logs/') '/logs/ must be ignored'

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('local-log-router-' + [guid]::NewGuid())
$router = $null
$pressureRouter = $null
try {
    $logRoot = Join-Path $tempRoot 'logs'
    $router = New-LocalLogRouter `
        -RepositoryRoot $repositoryRoot `
        -LogDirectory $logRoot `
        -Clock { $fixedNow } `
        -MaxFileBytes 1024 `
        -MaxSegments 3 `
        -QueueCapacity 128

    Submit-LocalLogLine -Router $router -Stream BACKEND_STDOUT `
        -Line 'INFO backend-info-sentinel'
    Submit-LocalLogLine -Router $router -Stream BACKEND_STDERR `
        -Line 'ERROR backend-error-sentinel'
    Submit-LocalLogLine -Router $router -Stream BACKEND_STDOUT `
        -Line 'INFO event.origin=browser browser-info-sentinel'
    Submit-LocalLogLine -Router $router -Stream VITE_STDERR `
        -Line 'ERROR vite-error-sentinel'
    Flush-LocalLogRouter -Router $router

    $current = Join-Path $logRoot 'current'
    $backendInfo = Get-Content -LiteralPath (Join-Path $current 'backend-info.log') -Raw
    $backendError = Get-Content -LiteralPath (Join-Path $current 'backend-error.log') -Raw
    $frontendInfo = Get-Content -LiteralPath (Join-Path $current 'frontend-info.log') -Raw
    $frontendError = Get-Content -LiteralPath (Join-Path $current 'frontend-error.log') -Raw
    Assert-True $backendInfo.Contains('backend-info-sentinel') 'Backend INFO route'
    Assert-True (-not $backendInfo.Contains('backend-error-sentinel')) 'Backend ERROR must not duplicate'
    Assert-True $backendError.Contains('backend-error-sentinel') 'Backend ERROR route'
    Assert-True $frontendInfo.Contains('browser-info-sentinel') 'Browser INFO route'
    Assert-True $frontendError.Contains('vite-error-sentinel') 'Vite ERROR route'

    1..80 | ForEach-Object {
        Submit-LocalLogLine -Router $router -Stream BACKEND_STDOUT `
            -Line ("INFO segment-sentinel-{0:D3} {1}" -f $_, ('x' * 100))
    }
    Flush-LocalLogRouter -Router $router
    Assert-True (Test-Path (Join-Path $current 'backend-info.1.log')) 'First segment must exist'
    Assert-True (Test-Path (Join-Path $current 'backend-info.2.log')) 'Second segment must exist'
    Assert-True (-not (Test-Path (Join-Path $current 'backend-info.3.log'))) 'Old segment must be removed'
    $activeBytes = (Get-Item -LiteralPath (Join-Path $current 'backend-info.log')).Length
    Assert-True ($activeBytes -le 1024) 'Active file must respect segment limit'
    Assert-True $router.Truncated 'Segment overwrite must be recorded'
    Assert-True ($router.DiscardedSegmentCount -gt 0) 'Discarded segment count must be recorded'

    Stop-LocalLogRouter -Router $router
    Assert-Equal 'STOPPED' $router.StatusCode 'Router stop status'

    $pressureRoot = Join-Path $tempRoot 'pressure'
    $pressureRouter = New-LocalLogRouter `
        -RepositoryRoot $repositoryRoot `
        -LogDirectory $pressureRoot `
        -Clock { $fixedNow } `
        -MaxFileBytes 4096 `
        -MaxSegments 3 `
        -QueueCapacity 2
    1..2000 | ForEach-Object {
        Submit-LocalLogLine -Router $pressureRouter -Stream VITE_STDOUT `
            -Line "DEBUG pressure-$_"
    }
    Submit-LocalLogLine -Router $pressureRouter -Stream BACKEND_STDERR `
        -Line 'ERROR priority-sentinel'
    Flush-LocalLogRouter -Router $pressureRouter
    Stop-LocalLogRouter -Router $pressureRouter
    Assert-True ($pressureRouter.DroppedDebug -gt 0) 'Queue pressure must drop DEBUG'
    Assert-Equal 0 $pressureRouter.DroppedError 'ERROR must survive while DEBUG can be dropped'

    $allBytes = [System.IO.File]::ReadAllBytes((Join-Path $current 'frontend-info.log'))
    Assert-True (-not ($allBytes.Length -ge 3 -and $allBytes[0] -eq 0xEF -and $allBytes[1] -eq 0xBB -and $allBytes[2] -eq 0xBF)) `
        'Log files must be UTF-8 without BOM'
} finally {
    if ($null -ne $router -and $router.StatusCode -ne 'STOPPED') {
        Stop-LocalLogRouter -Router $router
    }
    if ($null -ne $pressureRouter -and $pressureRouter.StatusCode -ne 'STOPPED') {
        Stop-LocalLogRouter -Router $pressureRouter
    }
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}

Write-Output 'local-log-router tests passed'
