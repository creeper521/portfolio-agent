$ErrorActionPreference = 'Stop'

$checker = Join-Path $PSScriptRoot 'privacy-check.ps1'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('portfolio-privacy-' + [guid]::NewGuid())
$safeRoot = Join-Path $fixtureRoot 'safe'
$registryUnsafeSource = @'
final class UnsafeModelProviderRegistry {
    private final String apiKey = "credential-literal";
    public void register(ModelProviderDescriptor descriptor) { }
    public void remove() { }
    public void replace() { }
    public void discoverFromClasspath() { Class.forName("provider"); }
    public void discoverFromFile() { Files.readString(path); }
    public void discoverFromNetwork() { HttpClient.newHttpClient(); }
    public void logProviderResponse() { logger.info("responseBody={}", responseBody); }
}
'@
$registryExpectedRules = @(
    'registry-credential-field',
    'registry-mutable-api',
    'registry-dynamic-discovery',
    'registry-raw-provider-log'
)
$registryNegativeFixtures = @(
    @{
        Name = 'registry-package-visible-credential-field'
        File = 'ModelProviderRegistry.java'
        Source = @'
final class UnsafeModelProviderRegistry {
    String credential;
}
'@
        Rule = 'registry-credential-field'
    },
    @{
        Name = 'descriptor-private-authorization-credential-field'
        File = 'ModelProviderDescriptor.java'
        Source = @'
final class UnsafeModelProviderDescriptor {
    private final String authorizationCredential;
}
'@
        Rule = 'registry-credential-field'
    },
    @{
        Name = 'registry-access-key-field'
        File = 'ModelProviderRegistry.java'
        Source = @'
final class UnsafeModelProviderRegistry {
    private String accessKey;
}
'@
        Rule = 'registry-credential-field'
    },
    @{
        Name = 'descriptor-bearer-field'
        File = 'ModelProviderDescriptor.java'
        Source = @'
final class UnsafeModelProviderDescriptor {
    final String bearer;
}
'@
        Rule = 'registry-credential-field'
    },
    @{
        Name = 'descriptor-concatenated-provider-response-log'
        File = 'ModelProviderDescriptor.java'
        Source = @'
final class UnsafeModelProviderDescriptor {
    void logResponse() {
        logger.info("response=" + response);
    }
}
'@
        Rule = 'registry-raw-provider-log'
    },
    @{
        Name = 'descriptor-generic-and-multiline-provider-logs'
        File = 'ModelProviderDescriptor.java'
        Source = @'
final class UnsafeModelProviderDescriptor {
    void logResponse() {
        logger.info("{}", response);
        log.debug("payload={}", body);
        logger.warn(
                "provider exchange={}",
                response);
    }
}
'@
        Rule = 'registry-raw-provider-log'
    },
    @{
        Name = 'registry-named-logger-request-log'
        File = 'ModelProviderRegistry.java'
        Source = @'
final class UnsafeModelProviderRegistry {
    void logRequest() {
        providerLogger.warn("request={}", request);
    }
}
'@
        Rule = 'registry-raw-provider-log'
    },
    @{
        Name = 'descriptor-fluent-provider-response-log'
        File = 'ModelProviderDescriptor.java'
        Source = @'
final class UnsafeModelProviderDescriptor {
    void logResponse() {
        AUDIT_LOG.atInfo()
                .addArgument(providerResponse)
                .log("provider response");
    }
}
'@
        Rule = 'registry-raw-provider-log'
    },
    @{
        Name = 'registry-provider-audit-sink-request-log'
        File = 'ModelProviderRegistry.java'
        Source = @'
final class UnsafeModelProviderRegistry {
    void logRequest() {
        providerAuditSink.accept(request);
    }
}
'@
        Rule = 'registry-raw-provider-log'
    }
)
$registryMetadataLogSource = @'
final class ModelProviderDescriptor {
    void logMetadata() {
        logger.info("{}", providerId);
        logger.debug("{}", modelName);
        logger.info("{}", responseDuration);
        log.debug("{}", payloadSize);
        // logger.info("{}", response);
    }

    String example() {
        return "private String credential;";
    }
}
'@
$unsafeCases = [ordered]@{
    'ipv4-address' = 'host=192.168.10.24'
    'windows-absolute-path' = 'path=C:\Users\internal\report.md'
    'internal-linux-path' = 'path=/data/server/private/report.md'
    'credential-assignment' = 'password=secret'
    'internal-hostname' = 'service=https://sql-audit.private.corp/api'
    'private-key-material' = '-----BEGIN PRIVATE KEY-----'
    'standalone-deepseek-key' = 'sk-1234567890abcdefghijklmnop'
    'standalone-glm-key' = '0123456789abcdef0123456789abcdef.Abcdefghijklmnop'
    'visitor-session-storage-key' = 'portfolio.agent.sessions.v1'
    'question-in-url' = '/agent?question=private-visitor-question'
    'provider-key-literal' = 'DEEPSEEK_API_KEY=literal-secret-value'
    'raw-model-prompt-log' = 'logger.info("prompt={}", prompt)'
    'raw-model-response-log' = 'log.debug("modelResponse={}", responseBody)'
    'raw-retrieval-log' = 'logger.info("queryVector={}", queryVector)'
    'raw-context-envelope-log' = 'logger.info("contextEnvelope={}", contextEnvelope)'
    'raw-tool-plan-log' = 'log.debug("toolPlan={}", toolPlan)'
    'raw-tool-result-log' = 'logger.warn("toolResult={}", toolResult)'
    'visitor-question-provider-field' = 'providerRequest.question = request.getQuestion()'
}

try {
    New-Item -ItemType Directory -Force -Path $safeRoot | Out-Null
    Set-Content -LiteralPath (Join-Path $safeRoot 'content.json') `
        -Value 'Public portfolio contains reviewed content only.' `
        -Encoding UTF8

    foreach ($case in $unsafeCases.GetEnumerator()) {
        $caseRoot = Join-Path $fixtureRoot $case.Key
        New-Item -ItemType Directory -Force -Path $caseRoot | Out-Null
        $extension = if ($case.Key -eq 'standalone-deepseek-key') { '.ps1' } else { '.json' }
        Set-Content -LiteralPath (Join-Path $caseRoot ("content" + $extension)) `
            -Value $case.Value `
            -Encoding UTF8

        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker -Path $caseRoot *> $null
        if ($LASTEXITCODE -eq 0) {
            throw "Expected privacy rule $($case.Key) to reject its fixture."
        }
    }

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker -Path $safeRoot *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Expected safe fixture to pass privacy check.'
    }

    $registryRoot = Join-Path $fixtureRoot 'registry-unsafe'
    $registryPath = Join-Path $registryRoot 'ModelProviderRegistry.java'
    New-Item -ItemType Directory -Force -Path $registryRoot | Out-Null
    Set-Content -LiteralPath $registryPath -Value $registryUnsafeSource -Encoding UTF8
    $registryOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker -Path $registryRoot 2>&1 | Out-String)
    if ($LASTEXITCODE -eq 0) {
        throw 'Expected registry-specific unsafe fixture to fail privacy check.'
    }
    foreach ($rule in $registryExpectedRules) {
        if ($registryOutput -notmatch [regex]::Escape($rule)) {
            throw "Expected registry-specific unsafe fixture to report $rule. Output: $registryOutput"
        }
    }

    $missingRegistryFixtureRules = [System.Collections.Generic.List[string]]::new()
    foreach ($fixture in $registryNegativeFixtures) {
        $fixturePath = Join-Path $fixtureRoot $fixture.Name
        New-Item -ItemType Directory -Force -Path $fixturePath | Out-Null
        Set-Content -LiteralPath (Join-Path $fixturePath $fixture.File) `
            -Value $fixture.Source `
            -Encoding UTF8
        $fixtureOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $checker -Path $fixturePath 2>&1 | Out-String)
        if ($LASTEXITCODE -eq 0 -or
                $fixtureOutput -notmatch [regex]::Escape($fixture.Rule)) {
            $missingRegistryFixtureRules.Add("$($fixture.Name):$($fixture.Rule)")
        }
    }
    if ($missingRegistryFixtureRules.Count -gt 0) {
        throw "Expected registry fixtures to report: $($missingRegistryFixtureRules -join ', ')"
    }

    $metadataLogRoot = Join-Path $fixtureRoot 'descriptor-metadata-logs'
    New-Item -ItemType Directory -Force -Path $metadataLogRoot | Out-Null
    Set-Content -LiteralPath (Join-Path $metadataLogRoot 'ModelProviderDescriptor.java') `
        -Value $registryMetadataLogSource `
        -Encoding UTF8
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker -Path $metadataLogRoot *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Expected Registry/Descriptor metadata logs to remain allowed.'
    }

    $propertiesRoot = Join-Path $fixtureRoot 'model-expression-properties'
    $propertiesPath = Join-Path $propertiesRoot 'ModelExpressionProperties.java'
    New-Item -ItemType Directory -Force -Path $propertiesRoot | Out-Null
    Set-Content -LiteralPath $propertiesPath -Value @'
final class ModelExpressionProperties {
    private String apiKey;
}
'@ -Encoding UTF8
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker -Path $propertiesRoot *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Expected ModelExpressionProperties credential holder fixture to remain allowed.'
    }

    Write-Output 'privacy-check tests passed'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
}
