param(
    [string]$RootPath = ''
)

$ErrorActionPreference = 'Stop'
$root = if ([string]::IsNullOrWhiteSpace($RootPath)) {
    Split-Path -Parent $PSScriptRoot
} else {
    (Resolve-Path -LiteralPath $RootPath -ErrorAction Stop).Path
}
$requirements = @(
    @{ Path = 'AGENTS.md'; Tokens = @(
        'persistence-safe public replay', 'REPLAY_BODY_NOT_RETAINED', 'ContextHandle') },
    @{ Path = 'SECURITY.md'; Tokens = @(
        'persistence-safe', 'REPLAY_BODY_NOT_RETAINED', '加密不改变这条不持久化边界') },
    @{ Path = 'docs/08-当前实现状态.md'; Tokens = @(
        'REPLAY_BODY_NOT_RETAINED', 'Provider 派生', 'opaque ContextHandle') },
    @{ Path = 'docs/15-Agent 2.0真实交互问题清单与修复边界.md'; Tokens = @(
        'REPLAY_BODY_NOT_RETAINED', '关键词/sentinel 检测只属于测试',
        'Portfolio continuation handle 原样保留') },
    @{ Path = 'docs/agent-architecture-status.json'; Tokens = @(
        'AgentStatePayloadCodecTest', 'JdbcAgentStateStoreIntegrationTest',
        'complete settlement sentinel') }
)
$violations = [System.Collections.Generic.List[string]]::new()
foreach ($requirement in $requirements) {
    $path = Join-Path $root $requirement.Path
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        $violations.Add("missing:$($requirement.Path)")
        continue
    }
    $content = Get-Content -LiteralPath $path -Raw
    foreach ($requiredMarker in $requirement.Tokens) {
        if (-not $content.Contains($requiredMarker)) {
            $violations.Add("missing-marker:$($requirement.Path):$requiredMarker")
        }
    }
}
if ($violations.Count -gt 0) {
    $violations | ForEach-Object { Write-Error $_ }
    exit 1
}
Write-Output 'PERSISTENCE_SAFE_REPLAY_DOCS_OK files=5'
