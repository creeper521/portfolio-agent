$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runner = Join-Path $PSScriptRoot 'run-eval-offline.ps1'
$evalRunner = Join-Path $PSScriptRoot 'run-eval.ps1'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) `
    ('run-eval-offline-test-' + [guid]::NewGuid().ToString('N'))
$fakeMaven = Join-Path $fixtureRoot 'fake-maven.cmd'
$fakeJava = Join-Path $fixtureRoot 'fake-java.cmd'
$fakeJavaServer = Join-Path $fixtureRoot 'fake-java-server.ps1'
$javaArguments = Join-Path $fixtureRoot 'java-args.txt'

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

function Invoke-OfflineRunner {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $runner `
            -Manifest (Join-Path $fixtureRoot 'suite.json') `
            -Policy (Join-Path $fixtureRoot 'policy.json') `
            -OutputDir (Join-Path $fixtureRoot 'out') `
            -SkipInstall `
            -MavenExecutable $fakeMaven `
            -JavaExecutable $fakeJava 2>&1 | Out-String)
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
    New-Item -ItemType Directory -Path $fixtureRoot -Force | Out-Null
    [System.IO.File]::WriteAllText($fakeMaven, "@echo off`r`nexit /b 0`r`n",
        [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText(
        $fakeJava,
        "@echo off`r`necho %* > `"$javaArguments`"`r`n" +
            "echo %* | findstr /C:`"-jar`" >nul`r`n" +
            "if errorlevel 1 exit /b %FAKE_JAVA_EXIT%`r`n" +
            "powershell -NoProfile -ExecutionPolicy Bypass -File `"$fakeJavaServer`"`r`n" +
            "exit /b 0`r`n",
        [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText(
        $fakeJavaServer,
        @"
param()
`$raw = [System.IO.File]::ReadAllText('$javaArguments')
`$port = 0
`$eq = `$raw -match '--server\.port=(\d+)'
if (`$eq) { `$port = [int]`$Matches[1] }
`$tokens = `$raw -split ' '
for (`$i = 0; `$i -lt `$tokens.Count - 1; `$i++) {
    if (`$tokens[`$i] -eq '--server.port') { `$port = [int]`$tokens[`$i + 1] }
}
`$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, `$port)
`$listener.Start()
Start-Sleep -Seconds 120
`$listener.Stop()
"@,
        [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText(
        (Join-Path $fixtureRoot 'suite.json'), '{}', [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText(
        (Join-Path $fixtureRoot 'policy.json'), '{}', [System.Text.UTF8Encoding]::new($false))

    $result = Invoke-OfflineRunner
    Assert-True ($result.ExitCode -eq 0) `
        "offline runner must exit 0. Output: $($result.Output)"

    $captured = Get-Content -LiteralPath $javaArguments -Raw
    Assert-True ($captured -match '--base-url http://127\.0\.0\.1:\d+') `
        "offline runner must forward the local base url. Args: $captured"
    Assert-True ($captured -notmatch 'authorize-real-provider') `
        'offline runner must not carry the real provider flag.'
    Assert-True ($captured -match 'offline') `
        'offline runner must invoke the offline command.'
    $runnerSource = Get-Content -LiteralPath $runner -Raw
    Assert-True ($runnerSource -match 'portfolio\.conversational-model\.enabled=false') `
        'offline backend must disable the current model authority.'
    Assert-True ($runnerSource -notmatch ('portfolio\.model-' + 'expression')) `
        'offline backend must not set the retired model property prefix.'

    Start-Sleep -Seconds 1
    $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalAddress -eq '127.0.0.1' -and $_.OwningProcess -ne $PID })
    $leaked = $listeners | Where-Object {
        (Get-CimInstance Win32_Process -Filter "ProcessId=$($_.OwningProcess)" `
            -ErrorAction SilentlyContinue).CommandLine -match 'fake-java-server'
    }
    Assert-True (@($leaked).Count -eq 0) `
        'offline runner must stop the backend subprocess tree.'

    Write-Output 'run-eval-offline tests passed'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        if (-not ([System.IO.Path]::GetFileName($resolved)).StartsWith('run-eval-offline-test-')) {
            throw "Refusing to remove unverified fixture path: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
