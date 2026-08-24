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
    $headerBytes = [System.Collections.Generic.List[byte]]::new()
    $terminator = [byte[]]@(13, 10, 13, 10)
    while ($headerBytes.Count -lt 65536) {
        $value = $Stream.ReadByte()
        if ($value -lt 0) { break }
        $headerBytes.Add([byte]$value)
        if ($headerBytes.Count -ge 4) {
            $offset = $headerBytes.Count - 4
            if ($headerBytes[$offset] -eq $terminator[0] -and
                    $headerBytes[$offset + 1] -eq $terminator[1] -and
                    $headerBytes[$offset + 2] -eq $terminator[2] -and
                    $headerBytes[$offset + 3] -eq $terminator[3]) {
                break
            }
        }
    }
    $header = [System.Text.Encoding]::ASCII.GetString(
        $headerBytes.ToArray())
    $lines = @($header -split "`r`n")
    $requestLine = if ($lines.Count -gt 0) { $lines[0] } else { '' }
    $contentLength = 0
    foreach ($line in $lines) {
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
        $buffer = [byte[]]::new($contentLength)
        $read = 0
        while ($read -lt $contentLength) {
            $count = $Stream.Read(
                $buffer, $read, $contentLength - $read)
            if ($count -le 0) { break }
            $read += $count
        }
        if ($read -gt 0) {
            $body = [System.Text.Encoding]::UTF8.GetString(
                $buffer, 0, $read)
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
            elseif ($path -eq '/api/portfolio') {
                Write-Response $stream 200 'application/json; charset=utf-8' `
                    '{"contentVersion":"test-v1","agentAvailability":{"status":"AVAILABLE","freeTextSemanticRouting":"AVAILABLE","modelCatalogVersion":"fixture-v1","selectableModels":[{"modelRef":"glm-4-7-flash","selectionVersion":"glm-4-7-flash-v1","displayName":"GLM-4.7-Flash"}],"defaultModelSelection":{"kind":"MODEL","modelRef":"glm-4-7-flash","selectionVersion":"glm-4-7-flash-v1"}}}'
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
                            modelExecution = @{
                                selectionKind = 'MODEL'
                                requestedModelRef = 'glm-4-7-flash'
                                selectionVersion = 'glm-4-7-flash-v1'
                                participation = 'GOAL_INTERPRETATION_ONLY'
                            }
                        }
                    }
                    else {
                        @{
                            requestId = $requestId
                            kind = 'ANSWER'
                            conversation = @{ conversationId = 'conversation-fixture' }
                            modelExecution = @{
                                selectionKind = 'MODEL'
                                requestedModelRef = 'glm-4-7-flash'
                                selectionVersion = 'glm-4-7-flash-v1'
                                participation = 'GOAL_AND_ANSWER'
                            }
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
