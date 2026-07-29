$ErrorActionPreference = 'Stop'

$checker = Join-Path $PSScriptRoot 'privacy-check.ps1'
$checkerSource = [System.IO.File]::ReadAllText($checker)
if ($checkerSource -match '\.Substring\(\s*0\s*,\s*\$Index\s*\)') {
    throw 'Line lookup must not rescan the source prefix for every finding.'
}
if ($checkerSource -notmatch 'function\s+Get-NewlineOffsets\b' -or
        $checkerSource -notmatch
        '\[System\.Collections\.Generic\.List\[object\]\]::new\(\)') {
    throw 'Privacy findings must use precomputed newline offsets and List.Add.'
}
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('portfolio-privacy-' + [guid]::NewGuid())
$safeRoot = Join-Path $fixtureRoot 'safe'
$registryUnsafeSource = @'
import org.slf4j.Logger;
final class UnsafeModelProviderRegistry {
    private final String apiKey = "credential-literal";
    private final Logger logger = null;
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
import org.slf4j.Logger;
final class UnsafeModelProviderDescriptor {
    private Logger logger;
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
import org.slf4j.Logger;
final class UnsafeModelProviderDescriptor {
    private Logger logger;
    private Logger log;
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
import org.slf4j.Logger;
final class UnsafeModelProviderRegistry {
    private Logger providerLogger;
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
import org.slf4j.Logger;
final class UnsafeModelProviderDescriptor {
    private Logger AUDIT_LOG;
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
    'visitor-question-logger' = 'LOGGER.info("question={}", request.getQuestion());'
    'visitor-history-logger' = 'LOGGER.info("history={}", conversationHistory);'
    'provider-payload-logger' = 'LOGGER.warn("payload={}", providerResponse);'
    'frontend-session-messages-console' = "console.error('messages', session.messages)"
    'full-answer-logger' = 'LOGGER.info("answer={}", fullAnswer);'
    'prompt-console' = "console.warn('prompt', systemPrompt)"
    'prompt-system-console' = 'System.err.println("prompt=" + prompt);'
    'credentials-logger' = 'LOGGER.warn("{}", requestCredentials);'
    'raw-ip-logger' = 'LOGGER.info("rawIp={}", rawIp);'
    'headers-console' = "console.error('headers', responseHeaders)"
    'ordinary-body-logger' = 'LOGGER.debug("body={}", body);'
    'exception-message-logger' = 'LOGGER.error("failure={}", exceptionMessage);'
    'multiline-java-question-logger' = @'
LOGGER.info(
        "operation failed",
        question
);
'@
    'multiline-typescript-headers-console' = @'
console.error(
  'diagnostic upload failed',
  headers,
)
'@
    'java-forbidden-format-label-placeholder' =
            'LOGGER.info("question={}", value);'
    'declared-audit-logger-alias' = @'
import org.slf4j.Logger;
final class AuditLoggerExample {
    private static final Logger AUDIT = null;

    void record(String question) {
        AUDIT.info(
                "question={}",
                question
        );
    }
}
'@
    'declared-security-logger-alias' = @'
import org.slf4j.Logger;
final class SecurityLoggerExample {
    private final Logger SECURITY = null;

    void record(String responseBody) {
        SECURITY.warn("responseBody={}", responseBody);
    }
}
'@
    'declared-multiple-logger-aliases' = @'
import org.slf4j.Logger;
final class MultipleLoggerExample {
    private static final Logger PRIMARY = null, AUDIT = null;

    void record(String question) {
        AUDIT.info("question={}", question);
    }
}
'@
    'declared-local-multiple-logger-aliases' = @'
import org.slf4j.Logger;
final class LocalMultipleLoggerExample {
    void record(String question) {
        final Logger PRIMARY = null, AUDIT = null;
        AUDIT.info("question={}", question);
    }
}
'@
    'declared-for-init-multiple-logger-aliases' = @'
import org.slf4j.Logger;
final class ForInitMultipleLoggerExample {
    void record(String question) {
        for (Logger PRIMARY = null, AUDIT = null; shouldContinue(); ) {
            AUDIT.info("question={}", question);
        }
    }
}
'@
}
$declaredJavaLoggerCaseNames = @(
    'raw-model-prompt-log',
    'raw-model-response-log',
    'raw-retrieval-log',
    'raw-context-envelope-log',
    'raw-tool-plan-log',
    'raw-tool-result-log',
    'visitor-question-logger',
    'visitor-history-logger',
    'provider-payload-logger',
    'full-answer-logger',
    'credentials-logger',
    'raw-ip-logger',
    'ordinary-body-logger',
    'exception-message-logger',
    'multiline-java-question-logger',
    'java-forbidden-format-label-placeholder'
)
$safeLoggingFixtures = @(
    @{
        Name = 'request-id-log'
        Source = 'LOGGER.info("requestId={}", requestId);'
    },
    @{
        Name = 'error-code-log'
        Source = 'LOGGER.warn("errorCode={}", errorCode);'
    },
    @{
        Name = 'duration-bucket-log'
        Source = 'LOGGER.info("durationBucket={}", durationBucket);'
    },
    @{
        Name = 'fingerprint-log'
        Source = 'LOGGER.debug("requestFingerprint={}", requestFingerprint);'
    }
)
$diagnosticForbiddenFields = @(
    'question', 'messages', 'answer', 'stack', 'url', 'headers',
    'requestBody', 'responseBody', 'metadata', 'rawAddress', 'rawIp',
    'clientIp', 'remoteIp', 'ipAddress', 'credential', 'credentials',
    'authorization', 'password', 'secret', 'apiKey', 'accessToken',
    'authToken', 'bearerToken'
)
$diagnosticUnsafeFixtures = foreach ($field in $diagnosticForbiddenFields) {
    @{
        Name = "java-diagnostic-forbidden-$field"
        File = 'UnsafeClientDiagnosticEvent.java'
        Source = "final class UnsafeClientDiagnosticEvent {`n    private final String $field;`n}"
    }
    @{
        Name = "typescript-diagnostic-forbidden-$field"
        File = 'shared/diagnostics/frontendDiagnosticTypes.ts'
        Source = "interface UnsafeClientDiagnosticEvent {`n  ${field}?: string`n}"
    }
}
$diagnosticUnsafeFixtures += @{
    Name = 'typescript-diagnostic-forbidden-after-nested-field'
    File = 'shared/diagnostics/frontendDiagnosticTypes.ts'
    Source = @'
interface UnsafeClientDiagnosticEvent {
  safeContext: {
    requestId: string
  }
  question: string
}
'@
}
$diagnosticUnsafeFixtures += @{
    Name = 'typescript-diagnostic-inline-nested-forbidden-field'
    File = 'shared/diagnostics/frontendDiagnosticTypes.ts'
    Source = @'
interface UnsafeClientDiagnosticEvent {
  context: { question: string }
}
'@
}
$diagnosticBoundaryFixtures = @(
    @{
        Name = 'typescript-diagnostic-contract-too-large'
        File = 'shared/diagnostics/frontendDiagnosticTypes.ts'
        Source = (
            'interface OversizedDiagnosticEvent { padding: "' +
            ('x' * 17000) +
            '"; requestId: string }'
        )
        Rule = 'diagnostic-contract-too-large'
    },
    @{
        Name = 'typescript-diagnostic-contract-unbalanced'
        File = 'shared/diagnostics/frontendDiagnosticTypes.ts'
        Source = 'interface UnbalancedDiagnosticEvent { requestId: string'
        Rule = 'diagnostic-contract-unbalanced'
    }
)
$logBoundaryFixtures = @(
    @{
        Name = 'java-log-call-too-large'
        File = 'OversizedLogCall.java'
        Source = (
            'import org.slf4j.Logger; final class OversizedLogCall {' +
            ' private Logger LOGGER; void record() { LOGGER.info(' +
            (' ' * 5000) +
            '"safe"); } }'
        )
        Rule = 'log-call-too-large'
    },
    @{
        Name = 'typescript-log-call-unbalanced'
        File = 'unbalancedLogCall.ts'
        Source = "console.info('safe', requestId"
        Rule = 'log-call-unbalanced'
    }
)
$safeDiagnosticFixtures = @(
    @{
        File = 'SafeDiagnosticEvent.java'
        Source = @'
final class SafeDiagnosticEvent {
    private final String requestId;
    private final String errorCode;
    private final String durationBucket;
    private final String fingerprint;
}
'@
    },
    @{
        File = 'shared/diagnostics/frontendDiagnosticTypes.ts'
        Source = @'
interface SafeDiagnosticEvent {
  requestId: string
  errorCode: string
  durationBucket: string
  fingerprint: string
}
'@
    }
)
$safeSourceExamples = @(
    @{
        File = 'SafeLogExamples.java'
        Source = @'
final class SafeLogExamples {
    // LOGGER.info("question={}", question);
    private final String example = "LOGGER.warn(\"requestBody={}\", requestBody)";
    private final AuditSink AUDIT = new AuditSink();
    private final AuditSink securityLog = new AuditSink();
    private final AuditSink AUDIT_LOG = new AuditSink();

    void logSafeMetadata(String requestId) {
        LOGGER.info("question values are never logged");
        LOGGER.info("requestId={}", requestId /* question is forbidden */);
        AUDIT.info("question={}", "safe non-logger domain value");
        securityLog.info("question={}", "safe non-logger domain value");
        AUDIT_LOG.info("responseBody={}", "safe non-logger domain value");
    }

    private static final class AuditSink {
        void info(String template, String value) {
            // This is a domain sink, not an slf4j Logger.
        }
    }
}
'@
    },
    @{
        File = 'safeLogExamples.ts'
        Source = @'
// console.error('headers', headers)
const example = "console.warn('answer', answer)"
const templateExample = `LOGGER.info("question={}", question)`
console.info('headers are never logged')
console.info('requestId', requestId /* answer is forbidden */)
console.info(`question values are never logged`)
'@
    },
    @{
        File = 'SafeLoggerMethodReturn.java'
        Source = @'
import org.slf4j.Logger;
final class SafeLoggerMethodReturn {
    private final AuditSink AUDIT = new AuditSink();

    Logger AUDIT() {
        return null;
    }

    void record() {
        AUDIT.info("question={}", "safe non-logger domain value");
    }

    private static final class AuditSink {
        void info(String template, String value) {
        }
    }
}
'@
    }
)

try {
    New-Item -ItemType Directory -Force -Path $safeRoot | Out-Null
    Set-Content -LiteralPath (Join-Path $safeRoot 'content.json') `
        -Value 'Public portfolio contains reviewed content only.' `
        -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $safeRoot 'request-token.ts') `
        -Value 'const payload = { requestToken: crypto.randomUUID() }' `
        -Encoding UTF8
    foreach ($fixture in $safeLoggingFixtures) {
        Set-Content -LiteralPath (Join-Path $safeRoot ($fixture.Name + '.java')) `
            -Value $fixture.Source `
            -Encoding UTF8
    }
    foreach ($fixture in $safeDiagnosticFixtures) {
        $safeFixturePath = Join-Path $safeRoot $fixture.File
        New-Item -ItemType Directory -Force -Path `
            ([System.IO.Path]::GetDirectoryName($safeFixturePath)) | Out-Null
        Set-Content -LiteralPath $safeFixturePath `
            -Value $fixture.Source `
            -Encoding UTF8
    }
    foreach ($fixture in $safeSourceExamples) {
        Set-Content -LiteralPath (Join-Path $safeRoot $fixture.File) `
            -Value $fixture.Source `
            -Encoding UTF8
    }

    foreach ($case in $unsafeCases.GetEnumerator()) {
        $caseRoot = Join-Path $fixtureRoot $case.Key
        New-Item -ItemType Directory -Force -Path $caseRoot | Out-Null
        $extension = switch ($case.Key) {
            'standalone-deepseek-key' { '.ps1' }
            'visitor-question-logger' { '.java' }
            'visitor-history-logger' { '.java' }
            'provider-payload-logger' { '.java' }
            'frontend-session-messages-console' { '.ts' }
            'full-answer-logger' { '.java' }
            'prompt-console' { '.ts' }
            'prompt-system-console' { '.java' }
            'credentials-logger' { '.java' }
            'raw-ip-logger' { '.java' }
            'headers-console' { '.ts' }
            'ordinary-body-logger' { '.java' }
            'exception-message-logger' { '.java' }
            'multiline-java-question-logger' { '.java' }
            'multiline-typescript-headers-console' { '.ts' }
            'java-forbidden-format-label-placeholder' { '.java' }
            'declared-audit-logger-alias' { '.java' }
            'declared-security-logger-alias' { '.java' }
            'declared-multiple-logger-aliases' { '.java' }
            'declared-local-multiple-logger-aliases' { '.java' }
            'declared-for-init-multiple-logger-aliases' { '.java' }
            'raw-model-prompt-log' { '.java' }
            'raw-model-response-log' { '.java' }
            'raw-retrieval-log' { '.java' }
            'raw-context-envelope-log' { '.java' }
            'raw-tool-plan-log' { '.java' }
            'raw-tool-result-log' { '.java' }
            default { '.json' }
        }
        $caseSource = $case.Value
        if ($declaredJavaLoggerCaseNames -contains $case.Key) {
            $caseSource = @"
import org.slf4j.Logger;
final class UnsafeLoggerFixture {
    private Logger LOGGER;
    private Logger logger;
    private Logger log;

    void record() {
$($case.Value)
    }
}
"@
        }
        Set-Content -LiteralPath (Join-Path $caseRoot ("content" + $extension)) `
            -Value $caseSource `
            -Encoding UTF8

        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker -Path $caseRoot *> $null
        if ($LASTEXITCODE -eq 0) {
            throw "Expected privacy rule $($case.Key) to reject its fixture."
        }
    }

    foreach ($fixture in $diagnosticUnsafeFixtures) {
        $fixturePath = Join-Path $fixtureRoot $fixture.Name
        New-Item -ItemType Directory -Force -Path $fixturePath | Out-Null
        $diagnosticFixturePath = Join-Path $fixturePath $fixture.File
        New-Item -ItemType Directory -Force -Path `
            ([System.IO.Path]::GetDirectoryName($diagnosticFixturePath)) | Out-Null
        Set-Content -LiteralPath $diagnosticFixturePath `
            -Value $fixture.Source `
            -Encoding UTF8
        $fixtureOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $checker -Path $fixturePath 2>&1 | Out-String)
        if ($LASTEXITCODE -eq 0 -or
                $fixtureOutput -notmatch 'diagnostic-forbidden-field') {
            throw "Expected diagnostic fixture $($fixture.Name) to report diagnostic-forbidden-field. Output: $fixtureOutput"
        }
    }
    $missingBoundaryRules = [System.Collections.Generic.List[string]]::new()
    $boundaryFixtures = @($diagnosticBoundaryFixtures) + @($logBoundaryFixtures)
    if ($boundaryFixtures.Count -ne 4) {
        throw "Expected four boundary fixtures, found $($boundaryFixtures.Count)."
    }
    foreach ($fixture in $boundaryFixtures) {
        if ([string]::IsNullOrWhiteSpace([string]$fixture.Rule)) {
            throw "Boundary fixture $($fixture.Name) has no expected rule."
        }
        $fixturePath = Join-Path $fixtureRoot $fixture.Name
        $fixtureFilePath = Join-Path $fixturePath $fixture.File
        New-Item -ItemType Directory -Force -Path `
            ([System.IO.Path]::GetDirectoryName($fixtureFilePath)) | Out-Null
        Set-Content -LiteralPath $fixtureFilePath `
            -Value $fixture.Source `
            -Encoding UTF8
        $fixtureOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $checker -Path $fixturePath 2>&1 | Out-String)
        $reportedExpectedRule = $fixtureOutput -match (
            '(?m)^' + [regex]::Escape([string]$fixture.Rule) + ':'
        )
        if ($LASTEXITCODE -eq 0 -or -not $reportedExpectedRule) {
            $missingBoundaryRules.Add("$($fixture.Name):$($fixture.Rule)")
        }
    }
    if ($missingBoundaryRules.Count -gt 0) {
        throw "Expected boundary fixtures to report: $($missingBoundaryRules -join ', ')"
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
