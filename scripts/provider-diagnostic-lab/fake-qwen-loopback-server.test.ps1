param(
    [Parameter(Mandatory = $true)]
    [ValidateRange(1024, 65535)]
    [int]$Port,
    [Parameter(Mandatory = $true)]
    [ValidateSet('SUCCESS', 'SERVER_ERROR', 'TOO_LARGE')]
    [string]$Mode,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Z_]{1,96}$')]
    [string]$Marker,
    [Parameter(Mandatory = $true)]
    [string]$ReadyFile
)

$ErrorActionPreference = 'Stop'
$listener = [System.Net.Sockets.TcpListener]::new(
    [System.Net.IPAddress]::Loopback, $Port)
try {
    $listener.Start()
    [System.IO.File]::WriteAllText(
        $ReadyFile, 'READY', [System.Text.Encoding]::ASCII)
    $client = $listener.AcceptTcpClient()
    try {
        $stream = $client.GetStream()
        $headerBytes = [System.Collections.Generic.List[byte]]::new()
        $tail = ''
        while ($tail -cne "`r`n`r`n") {
            $value = $stream.ReadByte()
            if ($value -lt 0 -or $headerBytes.Count -ge 32768) { exit 2 }
            $headerBytes.Add([byte]$value)
            $tail = ($tail + [char]$value)
            if ($tail.Length -gt 4) { $tail = $tail.Substring($tail.Length - 4) }
        }
        $headers = [System.Text.Encoding]::ASCII.GetString(
            $headerBytes.ToArray())
        $contentLength = 0
        if ($headers -match '(?im)^Content-Length:\s*(\d+)\s*$') {
            $contentLength = [int]$Matches[1]
        }
        $buffer = New-Object byte[] 8192
        $remaining = $contentLength
        while ($remaining -gt 0) {
            $read = $stream.Read(
                $buffer, 0, [Math]::Min($buffer.Length, $remaining))
            if ($read -le 0) { exit 2 }
            $remaining -= $read
        }

        if ($Mode -eq 'TOO_LARGE') {
            $status = '200 OK'
            $body = 'x' * 131073
        }
        elseif ($Mode -eq 'SERVER_ERROR') {
            $status = '503 Service Unavailable'
            $body = '{"error":"' + $Marker + '"}'
        }
        else {
            $status = '200 OK'
            $arguments = [ordered]@{
                definition = 'Synthetic definition.'
                mechanism = 'Synthetic mechanism.'
                caveats = @()
                diagnosticMarker = $Marker
            } | ConvertTo-Json -Compress
            $body = [ordered]@{
                model = 'qwen3.7-flash'
                choices = @(@{
                    finish_reason = 'stop'
                    message = @{
                        content = $null
                        refusal = $null
                        tool_calls = @(@{
                            type = 'function'
                            function = @{
                                name = 'emit_general_provider_draft_v4'
                                arguments = $arguments
                            }
                        })
                    }
                })
            } | ConvertTo-Json -Depth 10 -Compress
        }
        $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($body)
        $responseHeaders = "HTTP/1.1 $status`r`n" +
            "Content-Type: application/json`r`n" +
            "Content-Length: $($bodyBytes.Length)`r`n" +
            "Connection: close`r`n`r`n"
        $responseHeaderBytes = [System.Text.Encoding]::ASCII.GetBytes(
            $responseHeaders)
        $stream.Write($responseHeaderBytes, 0, $responseHeaderBytes.Length)
        $stream.Write($bodyBytes, 0, $bodyBytes.Length)
        $stream.Flush()
    }
    finally { $client.Dispose() }
}
catch { exit 2 }
finally { $listener.Stop() }
