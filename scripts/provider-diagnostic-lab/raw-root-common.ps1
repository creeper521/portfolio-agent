$script:DedicatedRawRootMarkerName =
    '.qwen-general-provider-lab-root.v1.json'

function Get-DedicatedRawNormalizedPath([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) { return '' }
    return [System.IO.Path]::GetFullPath($Path).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar)
}

function Test-DedicatedSameOrChild([string]$Candidate, [string]$Parent) {
    $candidatePath = Get-DedicatedRawNormalizedPath $Candidate
    $parentPath = Get-DedicatedRawNormalizedPath $Parent
    if ($candidatePath.Equals(
            $parentPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $true
    }
    return $candidatePath.StartsWith(
        $parentPath + [System.IO.Path]::DirectorySeparatorChar,
        [System.StringComparison]::OrdinalIgnoreCase)
}

function Test-DedicatedPathContainsReparsePoint([string]$Path) {
    $currentPath = Get-DedicatedRawNormalizedPath $Path
    while (-not [string]::IsNullOrWhiteSpace($currentPath)) {
        if (Test-Path -LiteralPath $currentPath) {
            $current = Get-Item -LiteralPath $currentPath -Force
            if (($current.Attributes -band
                    [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                return $true
            }
        }
        $parent = Split-Path -Parent $currentPath
        if ([string]::IsNullOrWhiteSpace($parent) -or
                $parent -ceq $currentPath) { break }
        $currentPath = $parent
    }
    return $false
}

function Test-DedicatedExactKeys([object]$Value, [string[]]$Expected) {
    if ($null -eq $Value) { return $false }
    $actual = @($Value.PSObject.Properties.Name | Sort-Object)
    $required = @($Expected | Sort-Object)
    return ($actual -join '|') -ceq ($required -join '|')
}

function Test-DedicatedBroadRoot([string]$Path) {
    $root = Get-DedicatedRawNormalizedPath $Path
    if ([string]::IsNullOrWhiteSpace($root)) { return $true }
    $volume = Get-DedicatedRawNormalizedPath (
        [System.IO.Path]::GetPathRoot($root))
    if ($root.Equals(
            $volume, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $true
    }
    $protected = @(
        [Environment]::GetFolderPath('UserProfile'),
        [Environment]::GetFolderPath('Desktop'),
        [Environment]::GetFolderPath('MyDocuments'),
        [Environment]::GetFolderPath('ApplicationData'),
        [Environment]::GetFolderPath('LocalApplicationData'),
        [Environment]::GetFolderPath('CommonApplicationData'),
        [Environment]::GetFolderPath('Windows'),
        [Environment]::SystemDirectory,
        [System.IO.Path]::GetTempPath())
    foreach ($value in $protected) {
        if ([string]::IsNullOrWhiteSpace($value)) { continue }
        $broad = Get-DedicatedRawNormalizedPath $value
        if ($root.Equals(
                $broad, [System.StringComparison]::OrdinalIgnoreCase) -or
                (Test-DedicatedSameOrChild $broad $root)) {
            return $true
        }
    }
    return $false
}

function Assert-DedicatedRawRoot(
    [string]$Path,
    [string]$RepoRoot,
    [string]$FailureCode = 'DEDICATED_RAW_ROOT_REJECTED'
) {
    try {
        $root = Get-DedicatedRawNormalizedPath $Path
        $repo = Get-DedicatedRawNormalizedPath $RepoRoot
        if ([string]::IsNullOrWhiteSpace($root) -or
                [string]::IsNullOrWhiteSpace($repo) -or
                (Test-DedicatedBroadRoot $root) -or
                (Test-DedicatedSameOrChild $root $repo) -or
                (Test-DedicatedSameOrChild $repo $root) -or
                -not (Test-Path -LiteralPath $root -PathType Container) -or
                (Test-DedicatedPathContainsReparsePoint $root)) {
            throw $FailureCode
        }
        $markerPath = Get-DedicatedRawNormalizedPath (
            Join-Path $root $script:DedicatedRawRootMarkerName)
        if (-not (Test-DedicatedSameOrChild $markerPath $root) -or
                -not (Test-Path -LiteralPath $markerPath -PathType Leaf) -or
                (Test-DedicatedPathContainsReparsePoint $markerPath)) {
            throw $FailureCode
        }
        $resolvedMarker = Get-DedicatedRawNormalizedPath (
            Resolve-Path -LiteralPath $markerPath).Path
        if (-not (Test-DedicatedSameOrChild $resolvedMarker $root)) {
            throw $FailureCode
        }
        $marker = Get-Content -LiteralPath $resolvedMarker -Raw -Encoding UTF8 |
            ConvertFrom-Json
        if (-not (Test-DedicatedExactKeys $marker @(
                    'schemaVersion', 'rootId', 'createdAtUtc')) -or
                $marker.schemaVersion -cne
                    'qwen-general-provider-lab-root.v1' -or
                [string]$marker.rootId -cnotmatch '^[0-9a-f]{32}$') {
            throw $FailureCode
        }
        [void][datetimeoffset]::Parse([string]$marker.createdAtUtc)
        return $root
    }
    catch {
        if ([string]$_.Exception.Message -ceq $FailureCode) { throw }
        throw $FailureCode
    }
}

function Protect-DedicatedRawRootAcl(
    [string]$Path,
    [string]$FailureCode = 'DEDICATED_RAW_ROOT_ACL_REJECTED'
) {
    try {
        $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
        $existing = Get-Acl -LiteralPath $Path
        $existingAllowed = @($existing.Access | Where-Object {
            $_.AccessControlType -eq
                [System.Security.AccessControl.AccessControlType]::Allow
        })
        $exclusive = $existing.AreAccessRulesProtected -and
            $existing.Owner.EndsWith(
                $identity.Name,
                [System.StringComparison]::OrdinalIgnoreCase) -and
            @($existingAllowed | Where-Object {
                $_.IdentityReference.Value -cne $identity.User.Value -and
                $_.IdentityReference.Value -cne $identity.Name
            }).Count -eq 0
        if (-not $exclusive) {
            $security = `
                [System.Security.AccessControl.DirectorySecurity]::new()
            $security.SetOwner($identity.User)
            $security.SetAccessRuleProtection($true, $false)
            $inheritance = [System.Security.AccessControl.InheritanceFlags]::`
                ContainerInherit -bor `
                [System.Security.AccessControl.InheritanceFlags]::ObjectInherit
            $rule = `
                [System.Security.AccessControl.FileSystemAccessRule]::new(
                $identity.User,
                [System.Security.AccessControl.FileSystemRights]::FullControl,
                $inheritance,
                [System.Security.AccessControl.PropagationFlags]::None,
                [System.Security.AccessControl.AccessControlType]::Allow)
            $security.AddAccessRule($rule)
            Set-Acl -LiteralPath $Path -AclObject $security
        }
        $verified = Get-Acl -LiteralPath $Path
        $allowed = @($verified.Access | Where-Object {
            $_.AccessControlType -eq
                [System.Security.AccessControl.AccessControlType]::Allow
        })
        if (-not $verified.AreAccessRulesProtected -or
                -not $verified.Owner.EndsWith(
                    $identity.Name,
                    [System.StringComparison]::OrdinalIgnoreCase) -or
                @($allowed | Where-Object {
                    $_.IdentityReference.Value -cne $identity.User.Value -and
                    $_.IdentityReference.Value -cne $identity.Name
                }).Count -gt 0) {
            throw $FailureCode
        }
    }
    catch {
        if ([string]$_.Exception.Message -ceq $FailureCode) { throw }
        throw $FailureCode
    }
}
