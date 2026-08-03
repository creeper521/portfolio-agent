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
        [System.Text.Encoding]::ASCII,
        $false,
        1024,
        $true
    )
    $requestLine = $reader.ReadLine()
    $contentLength = 0
    while ($true) {
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
    if ($contentLength -gt 0) {
        $buffer = [char[]]::new($contentLength)
        [void]$reader.ReadBlock($buffer, 0, $contentLength)
    }
    return $requestLine
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
            $requestLine = Read-Request $stream
            $path = ($requestLine -split ' ')[1]
            if ($Mode -eq 'FRONTEND') {
                Write-Response $stream 200 'text/html; charset=utf-8' `
                    '<!doctype html><title>fake vite</title>'
            }
            elseif ($path -eq '/api/v1/public-content') {
                Write-Response $stream 200 'application/json; charset=utf-8' `
                    '{"contentVersion":"test-v1"}'
            }
            elseif ($path -eq '/api/v2/answers') {
                if ($Mode -eq 'BACKEND_MODEL') {
                    $body = '{"contentVersion":"test-v1","intentSource":"MODEL",' +
                        '"constructionMode":"EVIDENCE_COMPOSITION","evidenceState":"VERIFIED",' +
                        '"degraded":false,"resolution":"ANSWERED",' +
                        '"blocks":[{"content":"fixture"}]}'
                }
                else {
                    $body = '{"contentVersion":"test-v1","intentSource":"GLOBAL",' +
                        '"constructionMode":"TEMPLATE","evidenceState":"NOT_REQUIRED",' +
                        '"degraded":true,"resolution":"CAPABILITY_UNAVAILABLE",' +
                        '"noticeCode":"PROVIDER_DRAFT_REJECTED",' +
                        '"blocks":[{"content":"fixture"}]}'
                }
                Write-Response $stream 200 'application/json; charset=utf-8' $body
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
