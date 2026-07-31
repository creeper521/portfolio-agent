Set-StrictMode -Version Latest

function Get-LocalLogLevel {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [string]$Stream,
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Line
    )

    foreach ($level in @('ERROR', 'WARN', 'DEBUG', 'INFO', 'TRACE')) {
        if ($Line -match "(?i)(?:^|[\s\[\]])$level(?:[\s\[\]:-]|$)") {
            if ($level -eq 'TRACE') {
                return 'DEBUG'
            }
            return $level
        }
    }

    if ($Stream.EndsWith('_STDERR')) {
        return 'ERROR'
    }
    return 'INFO'
}

function Protect-LocalLogText {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Line,
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,
        [Parameter(Mandatory = $true)]
        [string]$HomeDirectory
    )

    $credentialPattern = '(?ix)' +
        '(authorization\s*:\s*(?:bearer|basic)\b|' +
        '(?:api[_-]?key|access[_-]?token|secret|password)\s*[:=]|' +
        '-----BEGIN\s+(?:RSA\s+|EC\s+|OPENSSH\s+)?PRIVATE\s+KEY-----)'
    if ($Line -match $credentialPattern) {
        return [pscustomobject]@{
            Text = 'OUTPUT_REDACTED reason=CREDENTIAL_PATTERN'
            Redacted = $true
        }
    }

    $text = $Line
    $redacted = $false

    $withoutAnsi = [regex]::Replace($text, "$([char]27)\[[0-?]*[ -/]*[@-~]", '')
    if ($withoutAnsi -ne $text) {
        $text = $withoutAnsi
        $redacted = $true
    }

    $withoutControls = [regex]::Replace($text, '[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]', '')
    if ($withoutControls -ne $text) {
        $text = $withoutControls
        $redacted = $true
    }

    foreach ($path in @($RepositoryRoot, $HomeDirectory)) {
        if (-not [string]::IsNullOrWhiteSpace($path)) {
            $replacement = if ($path -eq $RepositoryRoot) { '<REPOSITORY>' } else { '<HOME>' }
            $replaced = [regex]::Replace(
                $text,
                [regex]::Escape($path.TrimEnd('\', '/')),
                $replacement,
                [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
            )
            if ($replaced -ne $text) {
                $text = $replaced
                $redacted = $true
            }
        }
    }

    $withoutUrlDetails = [regex]::Replace(
        $text,
        '(?i)(https?://[^\s?#]+)[?#][^\s]*',
        '$1'
    )
    if ($withoutUrlDetails -ne $text) {
        $text = $withoutUrlDetails
        $redacted = $true
    }

    $maximumLength = 8192
    $marker = '...[TRUNCATED]'
    if ($text.Length -gt $maximumLength) {
        $text = $text.Substring(0, $maximumLength - $marker.Length) + $marker
        $redacted = $true
    }

    return [pscustomobject]@{
        Text = $text
        Redacted = $redacted
    }
}

function ConvertTo-LocalLogRecord {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet(
            'BACKEND_STDOUT',
            'BACKEND_STDERR',
            'VITE_STDOUT',
            'VITE_STDERR',
            'LAUNCHER'
        )]
        [string]$Stream,
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Line,
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,
        [Parameter(Mandatory = $true)]
        [string]$HomeDirectory,
        [Parameter(Mandatory = $true)]
        [DateTimeOffset]$Now
    )

    $domain = if ($Stream.StartsWith('VITE_')) { 'FRONTEND' } else { 'BACKEND' }
    $source = if ($Stream.StartsWith('VITE_')) {
        'VITE'
    } elseif ($Stream -eq 'LAUNCHER') {
        'LAUNCHER'
    } else {
        'SPRING'
    }
    if ($Line -match '(?:^|\s)event\.origin=browser(?:\s|$)') {
        $domain = 'FRONTEND'
        $source = 'BROWSER'
    }

    $level = Get-LocalLogLevel -Stream $Stream -Line $Line
    $sanitized = Protect-LocalLogText `
        -Line $Line `
        -RepositoryRoot $RepositoryRoot `
        -HomeDirectory $HomeDirectory

    return [pscustomobject]@{
        Timestamp = $Now
        Domain = $domain
        Level = $level
        Source = $source
        Text = $sanitized.Text
        Redacted = $sanitized.Redacted
    }
}

function Format-LocalLogRecord {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Record
    )

    return '{0} [{1}][{2}][{3}] {4}' -f `
        $Record.Timestamp.ToString('yyyy-MM-ddTHH:mm:ss.fffzzz'), `
        $Record.Domain, `
        $Record.Level, `
        $Record.Source, `
        $Record.Text
}

Export-ModuleMember -Function `
    ConvertTo-LocalLogRecord, `
    Format-LocalLogRecord
