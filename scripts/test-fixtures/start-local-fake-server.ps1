param(
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 65535)]
    [int]$Port,
    [Parameter(Mandatory = $true)]
    [ValidateSet('BACKEND_MODEL', 'BACKEND_FALLBACK', 'FRONTEND')]
    [string]$Mode
)

$ErrorActionPreference = 'Stop'
if ($Mode -eq 'FRONTEND') {
    Write-Output '[vite] ready vite-fixture-info'
    [Console]::Error.WriteLine('[vite] Internal server error vite-fixture-error')
}
else {
    Write-Output 'INFO backend-fixture-info'
    [Console]::Error.WriteLine('ERROR backend-fixture-error')
    Write-Output 'INFO event.origin=browser browser-fixture-info'
}
$listener = [System.Net.Sockets.TcpListener]::new(
    [System.Net.IPAddress]::Loopback,
    $Port
)
$listener.Start()

function Read-Request([System.Net.Sockets.NetworkStream]$Stream) {
    $reader = [System.IO.StreamReader]::new(
        $Stream,
        [System.Text.Encoding]::UTF8,
        $false,
        1024,
        $true
    )
    $requestLine = ''
    try {
        $requestLine = $reader.ReadLine()
        if ($null -eq $requestLine) {
            $requestLine = ''
        }
    }
    catch {
        $requestLine = ''
    }
    $contentLength = 0
    while (-not [string]::IsNullOrEmpty($requestLine)) {
        $line = $reader.ReadLine()
        if ([string]::IsNullOrEmpty($line)) {
            break
        }
        if ($line.StartsWith(
                'Content-Length:',
                [System.StringComparison]::OrdinalIgnoreCase
            )) {
            $contentLength = [int]$line.Substring(
                'Content-Length:'.Length
            ).Trim()
        }
    }
    $body = ''
    if ($contentLength -gt 0) {
        $buffer = [char[]]::new($contentLength)
        $read = $reader.ReadBlock($buffer, 0, $contentLength)
        if ($read -gt 0) {
            $body = -join $buffer[0..($read - 1)]
        }
    }
    return [pscustomobject]@{
        RequestLine = $requestLine
        Body = $body
    }
}

function Write-Response(
    [System.Net.Sockets.NetworkStream]$Stream,
    [int]$StatusCode,
    [string]$ContentType,
    [string]$Body
) {
    $reason = if ($StatusCode -eq 200) { 'OK' } else { 'Not Found' }
    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($Body)
    $header = "HTTP/1.1 $StatusCode $reason`r`n" +
        "Content-Type: $ContentType`r`n" +
        "Content-Length: $($bodyBytes.Length)`r`n" +
        "Connection: close`r`n`r`n"
    $headerBytes = [System.Text.Encoding]::ASCII.GetBytes($header)
    $Stream.Write($headerBytes, 0, $headerBytes.Length)
    $Stream.Write($bodyBytes, 0, $bodyBytes.Length)
    $Stream.Flush()
}

try {
    while ($true) {
        $client = $listener.AcceptTcpClient()
        try {
            $stream = $client.GetStream()
            $request = Read-Request $stream
            $requestLine = $request.RequestLine
            $requestBody = $request.Body
            if ([string]::IsNullOrWhiteSpace($requestLine)) {
                continue
            }
            $path = ($requestLine -split ' ')[1]
            if ($Mode -eq 'FRONTEND') {
                Write-Response $stream 200 'text/html; charset=utf-8' `
                    '<!doctype html><title>fake vite</title>'
            }
            elseif ($path -eq '/api/v1/public-content') {
                Write-Response $stream 200 'application/json; charset=utf-8' `
                    '{"contentVersion":"test-v1"}'
            }
            elseif ($path -eq '/api/agent/turns') {
                if ($Mode -eq 'BACKEND_MODEL') {
                    $requestId = try {
                        [string](($requestBody | ConvertFrom-Json).requestId)
                    }
                    catch {
                        [guid]::NewGuid().ToString()
                    }
                    $socialText = [string]([char]0x4f60) + [char]0x597d
                    $isSocial = try {
                        [string](($requestBody | ConvertFrom-Json).command.input.text) -ceq $socialText
                    }
                    catch {
                        $false
                    }
                    $body = if ($isSocial) {
                        @{
                            requestId = $requestId
                            kind = 'CONVERSATIONAL'
                            message = 'hello'
                            conversation = @{ conversationId = 'conversation-fixture' }
                        }
                    }
                    else {
                        @{
                            requestId = $requestId
                            kind = 'ANSWER'
                            conversation = @{ conversationId = 'conversation-fixture' }
                            answer = @{
                                resolution = 'COMPLETE'
                                contentReleaseId = 'test-v1'
                                goalResults = @(@{
                                    goalId = 'goal-general'
                                    label = 'general knowledge'
                                    coverage = 'FULL'
                                    notices = @()
                                })
                                sourceCatalog = @{ sources = @() }
                                sourceComposition = @('GENERAL_KNOWLEDGE')
                            }
                        }
                    }
                }
                else {
                    $body = @{
                        requestId = [guid]::NewGuid().ToString()
                        kind = 'CAPABILITY_UNAVAILABLE'
                        code = 'PROVIDER_DRAFT_REJECTED'
                        message = 'provider response unavailable'
                        retryable = $true
                    }
                }
                Write-Response $stream 200 'application/json; charset=utf-8' `
                    ($body | ConvertTo-Json -Depth 8 -Compress)
            }
            else {
                Write-Response $stream 404 'application/json; charset=utf-8' '{}'
            }
        }
        finally {
            $client.Dispose()
        }
    }
}
finally {
    $listener.Stop()
}
