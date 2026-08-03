param(
    [string]$LogDirectory = '',
    [switch]$IncludeCurrentDay,
    [ValidateRange(1, 365)]
    [int]$RetentionDays = 7,
    [ValidateRange(1, 10240)]
    [int]$TotalSizeMegabytes = 2048
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($LogDirectory)) {
    $LogDirectory = Join-Path $repositoryRoot 'logs'
}

Import-Module (Join-Path $PSScriptRoot 'logging\LocalLogRouter.psm1') `
    -Force `
    -DisableNameChecking

$now = [DateTimeOffset]::Now
$router = $null
try {
    $router = New-LocalLogRouter `
        -RepositoryRoot $repositoryRoot `
        -LogDirectory $LogDirectory `
        -Clock { [DateTimeOffset]::Now }
    Invoke-LocalLogMaintenance `
        -Router $router `
        -RetentionDays $RetentionDays `
        -TotalArchiveBytes ([long]$TotalSizeMegabytes * 1MB)
    if ($IncludeCurrentDay) {
        Flush-LocalLogRouter -Router $router
        $snapshot = New-LocalLogSnapshot `
            -RepositoryRoot $repositoryRoot `
            -LogDirectory $LogDirectory `
            -Now $now
        Write-Output "LOG_SNAPSHOT_CREATED path=$snapshot"
    } else {
        Write-Output 'LOG_MAINTENANCE_COMPLETED'
    }
} finally {
    if ($null -ne $router) {
        Stop-LocalLogRouter -Router $router
    }
}
