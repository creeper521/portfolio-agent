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
    },
    @{
        File = 'safe-secret-references.sh'
        Source = @'
psql \
  --set=public_password="$PORTFOLIO_PUBLIC_DATABASE_PASSWORD" \
  --set=governance_password="$PORTFOLIO_GOVERNANCE_DATABASE_PASSWORD"
'@
    },
    @{
        File = 'safe-local-config-paths.ps1'
        Source = @'
$composeFile = 'compose.postgres.local.yml'
$environmentFile = '.env.postgres.local'
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

    $safeArchiveRoot = Join-Path $fixtureRoot 'safe-artifact'
    New-Item -ItemType Directory -Force -Path `
        (Join-Path $safeArchiveRoot 'META-INF') | Out-Null
    New-Item -ItemType Directory -Force -Path `
        (Join-Path $safeArchiveRoot 'org/apache/tomcat') | Out-Null
    Set-Content -LiteralPath (Join-Path $safeArchiveRoot 'application.yml') `
        -Value @'
portfolio.public-database.enabled: false
portfolio.database.public.password: ${PORTFOLIO_PUBLIC_DATABASE_PASSWORD:}
portfolio:
  database:
    governance:
      enabled: ${PORTFOLIO_GOVERNANCE_DATABASE_ENABLED:false}
      url: ${PORTFOLIO_GOVERNANCE_DATABASE_URL:}
      username: ${PORTFOLIO_GOVERNANCE_DATABASE_USERNAME:}
      password: ${PORTFOLIO_GOVERNANCE_DATABASE_PASSWORD:}
'@ -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $safeArchiveRoot 'META-INF/LICENSE.md') `
        -Value '# Third-party license' -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $safeArchiveRoot 'META-INF/NOTICE-third-party.md') `
        -Value '# Third-party notice' -Encoding UTF8
    Set-Content -LiteralPath `
        (Join-Path $safeArchiveRoot 'org/apache/tomcat/LocalStrings.properties') `
        -Value 'pemFile.noPassword=A password is required to decrypt the private key' `
        -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $safeArchiveRoot 'public-config.json') `
        -Value '{"password":"${PORTFOLIO_PUBLIC_DATABASE_PASSWORD:}"}' `
        -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $safeArchiveRoot 'token-model.json') `
        -Value '{"token":{"kind":"model"}}' `
        -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $safeArchiveRoot 'portfolio-label.json') `
        -Value '{"portfolio":"public","database":{"governance":"documentation-only"}}' `
        -Encoding UTF8
    $safeZip = Join-Path $fixtureRoot 'safe.zip'
    $safeArchive = Join-Path $fixtureRoot 'safe.jar'
    Compress-Archive -Path (Join-Path $safeArchiveRoot '*') -DestinationPath $safeZip
    Move-Item -LiteralPath $safeZip -Destination $safeArchive
    $safeArchiveOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File $checker -Path $safeArchive 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $safeArchiveOutput -notmatch '1 archive\(s\)') {
        throw "Expected public artifact archive to pass privacy check. Output: $safeArchiveOutput"
    }

    $archiveCases = @(
        @{ Name = 'knowledge-md'; Entry = 'private/knowledge.md'; Value = '# private'; Rule = 'artifact-private-markdown' },
        @{ Name = 'absolute-path'; Entry = 'application.yml'; Value = 'source: C:\Users\owner\notes'; Rule = 'artifact-local-absolute-path' },
        @{ Name = 'governance-secret'; Entry = 'governance.yml'; Value = 'password=real-value'; Rule = 'artifact-governance-config' },
        @{ Name = 'governance-app-config'; Entry = 'config/application-prod.yml'; Value = 'portfolio.database.governance.enabled: true'; Rule = 'artifact-governance-config' },
        @{ Name = 'governance-url-default'; Entry = 'config/application.yml'; Value = 'portfolio.database.governance.url: ${PORTFOLIO_GOVERNANCE_DATABASE_URL:jdbc:postgresql://localhost/private}'; Rule = 'artifact-governance-config' },
        @{ Name = 'governance-username-default'; Entry = 'config/application.yml'; Value = 'portfolio.database.governance.username: ${PORTFOLIO_GOVERNANCE_DATABASE_USERNAME:portfolio}'; Rule = 'artifact-governance-config' },
        @{ Name = 'private-vector'; Entry = 'private/nested/vector-payload.csv'; Value = '0.1,0.2'; Rule = 'artifact-private-vector' },
        @{ Name = 'html-path'; Entry = 'static/index.html'; Value = '<meta content="C:\code\private\index">'; Rule = 'artifact-local-absolute-path' },
        @{ Name = 'sql-secret'; Entry = 'schema/data.sql'; Value = 'password=real-value'; Rule = 'artifact-credential' },
        @{ Name = 'properties-db-password'; Entry = 'config/runtime.properties'; Value = 'spring.datasource.password=real-value'; Rule = 'artifact-credential' },
        @{ Name = 'properties-password-default'; Entry = 'config/default.properties'; Value = 'spring.datasource.password=${DATABASE_PASSWORD:real-value}'; Rule = 'artifact-credential' },
        @{ Name = 'json-password'; Entry = 'config/runtime-password.json'; Value = '{"password":"real-value"}'; Rule = 'artifact-credential' },
        @{ Name = 'json-api-key'; Entry = 'config/runtime-api.json'; Value = '{"apiKey":"real-value"}'; Rule = 'artifact-credential' },
        @{ Name = 'json-token'; Entry = 'config/runtime-token.json'; Value = '{"token":"real-value"}'; Rule = 'artifact-credential' },
        @{ Name = 'json-secret'; Entry = 'config/runtime-secret.json'; Value = '{"secret":"real-value"}'; Rule = 'artifact-credential' },
        @{ Name = 'json-nested-governance'; Entry = 'config/runtime.json'; Value = '{"portfolio":{"database":{"governance":{"enabled":true}}}}'; Rule = 'artifact-governance-config' },
        @{ Name = 'json-governance-database'; Entry = 'config/settings.json'; Value = '{"governance":{"database":{"url":"jdbc:postgresql://localhost/private"}}}'; Rule = 'artifact-governance-config' },
        @{ Name = 'governance-env'; Entry = 'config/runtime.env'; Value = 'PORTFOLIO_GOVERNANCE_DATABASE_URL=jdbc:postgresql://localhost/private'; Rule = 'artifact-governance-config' },
        @{ Name = 'javascript-api-key'; Entry = 'static/runtime.js'; Value = 'const apiKey = "real-value";'; Rule = 'artifact-credential' },
        @{ Name = 'html-api-key'; Entry = 'static/runtime.html'; Value = '<meta data-api-key="real-value">'; Rule = 'artifact-credential' },
        @{ Name = 'application-privacy-md'; Entry = 'Privacy.md'; Value = '# Application privacy notes'; Rule = 'artifact-private-markdown' },
        @{ Name = 'license-path'; Entry = 'META-INF/LICENSE.md'; Value = 'source: C:\Users\owner\notes'; Rule = 'artifact-local-absolute-path' },
        @{ Name = 'license-secret'; Entry = 'META-INF/NOTICE.md'; Value = 'api_key=real-value'; Rule = 'artifact-credential' }
    )
    foreach ($archiveCase in $archiveCases) {
        $caseDirectory = Join-Path $fixtureRoot ('archive-' + $archiveCase.Name)
        $entryPath = Join-Path $caseDirectory $archiveCase.Entry
        New-Item -ItemType Directory -Force -Path `
            ([System.IO.Path]::GetDirectoryName($entryPath)) | Out-Null
        Set-Content -LiteralPath $entryPath -Value $archiveCase.Value -Encoding UTF8
        $archivePath = Join-Path $fixtureRoot ($archiveCase.Name + '.zip')
        Compress-Archive -Path (Join-Path $caseDirectory '*') -DestinationPath $archivePath
        $archiveOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $checker -Path $archivePath 2>&1 | Out-String)
        if ($LASTEXITCODE -eq 0 -or
                $archiveOutput -notmatch [regex]::Escape($archiveCase.Rule)) {
            throw "Expected archive fixture $($archiveCase.Name) to report $($archiveCase.Rule)."
        }
    }

    $nestedRoot = Join-Path $fixtureRoot 'nested-archive'
    $nestedPayload = Join-Path $nestedRoot 'payload'
    New-Item -ItemType Directory -Force -Path $nestedPayload | Out-Null
    Set-Content -LiteralPath (Join-Path $nestedPayload 'unsafe.js') `
        -Value 'const source = "C:\workspace\private";' -Encoding UTF8
    $nestedZip = Join-Path $nestedRoot 'nested.zip'
    Compress-Archive -Path (Join-Path $nestedPayload '*') -DestinationPath $nestedZip
    $outerZip = Join-Path $fixtureRoot 'outer.zip'
    Compress-Archive -Path $nestedZip -DestinationPath $outerZip
    $nestedOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File $checker -Path $outerZip 2>&1 | Out-String)
    if ($LASTEXITCODE -eq 0 -or $nestedOutput -notmatch 'artifact-local-absolute-path') {
        throw 'Expected nested archive text content to be scanned recursively.'
    }

    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    function New-EmptyEntryArchive([string]$Path, [int]$EntryCount) {
        $zip = [System.IO.Compression.ZipFile]::Open(
            $Path,
            [System.IO.Compression.ZipArchiveMode]::Create)
        try {
            for ($index = 0; $index -lt $EntryCount; $index++) {
                [void]$zip.CreateEntry(('entry-{0:D5}.bin' -f $index))
            }
        }
        finally {
            $zip.Dispose()
        }
    }

    function New-UnsafeEntryArchive([string]$Path, [int]$EntryCount) {
        $zip = [System.IO.Compression.ZipFile]::Open(
            $Path,
            [System.IO.Compression.ZipArchiveMode]::Create)
        try {
            for ($index = 0; $index -lt $EntryCount; $index++) {
                [void]$zip.CreateEntry(('../entry-{0:D5}.bin' -f $index))
            }
        }
        finally {
            $zip.Dispose()
        }
    }

    function Assert-ArchiveRule([string]$Path, [string]$Rule, [string]$Message) {
        $output = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $checker -Path $Path 2>&1 | Out-String)
        if ($LASTEXITCODE -eq 0 -or $output -notmatch [regex]::Escape($Rule)) {
            throw "$Message Output: $output"
        }
    }

    function New-RandomBytes([int]$Length) {
        $bytes = New-Object byte[] $Length
        $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $generator.GetBytes($bytes)
        }
        finally {
            $generator.Dispose()
        }
        return $bytes
    }

    function New-SingleEntryArchive(
            [string]$Path,
            [string]$EntryName,
            [byte[]]$Content
    ) {
        $zip = [System.IO.Compression.ZipFile]::Open(
            $Path,
            [System.IO.Compression.ZipArchiveMode]::Create)
        try {
            $entry = $zip.CreateEntry(
                $EntryName,
                [System.IO.Compression.CompressionLevel]::NoCompression)
            $stream = $entry.Open()
            try {
                $stream.Write($Content, 0, $Content.Length)
            }
            finally {
                $stream.Dispose()
            }
        }
        finally {
            $zip.Dispose()
        }
    }

    function New-NestedThirdPartyArchive(
            [string]$OuterPath,
            [string]$MarkdownContent
    ) {
        $innerPath = [System.IO.Path]::ChangeExtension($OuterPath, '.inner.zip')
        $inner = [System.IO.Compression.ZipFile]::Open(
            $innerPath,
            [System.IO.Compression.ZipArchiveMode]::Create)
        try {
            $privacyEntry = $inner.CreateEntry('Privacy.md')
            $writer = New-Object System.IO.StreamWriter($privacyEntry.Open())
            try {
                $writer.Write($MarkdownContent)
            }
            finally {
                $writer.Dispose()
            }
        }
        finally {
            $inner.Dispose()
        }
        $outer = [System.IO.Compression.ZipFile]::Open(
            $OuterPath,
            [System.IO.Compression.ZipArchiveMode]::Create)
        try {
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $outer,
                $innerPath,
                'BOOT-INF/lib/vendor.jar',
                [System.IO.Compression.CompressionLevel]::NoCompression) | Out-Null
        }
        finally {
            $outer.Dispose()
        }
    }

    $safeThirdPartyPrivacy = Join-Path $fixtureRoot 'safe-third-party-privacy.jar'
    New-NestedThirdPartyArchive $safeThirdPartyPrivacy '# Vendor privacy statement'
    $thirdPartyPrivacyOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File $checker -Path $safeThirdPartyPrivacy 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) {
        throw "A safe nested third-party root Privacy.md must pass. Output: $thirdPartyPrivacyOutput"
    }

    $unsafeThirdPartyPrivacy = Join-Path $fixtureRoot 'unsafe-third-party-privacy.jar'
    New-NestedThirdPartyArchive $unsafeThirdPartyPrivacy 'api_key=real-value'
    Assert-ArchiveRule $unsafeThirdPartyPrivacy 'artifact-credential' `
        'Nested third-party Privacy.md content must still be scanned.'

    $unsafeEntryNames = @(
        '../escape.txt',
        '/rooted.txt',
        'C:/drive-qualified.txt',
        '..\backslash-escape.txt'
    )
    foreach ($unsafeEntryName in $unsafeEntryNames) {
        $unsafePathArchive = Join-Path $fixtureRoot `
            ('unsafe-entry-' + [guid]::NewGuid() + '.zip')
        New-SingleEntryArchive $unsafePathArchive $unsafeEntryName ([byte[]]@(65))
        Assert-ArchiveRule $unsafePathArchive 'artifact-archive-entry-path' `
            "Unsafe archive entry path $unsafeEntryName must fail closed."
    }

    $nestedUnsafeInner = Join-Path $fixtureRoot 'nested-unsafe-entry-inner.zip'
    New-SingleEntryArchive $nestedUnsafeInner '../escape.txt' ([byte[]]@(65))
    $nestedUnsafeOuter = Join-Path $fixtureRoot 'nested-unsafe-entry-outer.zip'
    $nestedUnsafeZip = [System.IO.Compression.ZipFile]::Open(
        $nestedUnsafeOuter,
        [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $nestedUnsafeZip,
            $nestedUnsafeInner,
            'BOOT-INF/lib/unsafe.jar',
            [System.IO.Compression.CompressionLevel]::NoCompression) | Out-Null
    }
    finally {
        $nestedUnsafeZip.Dispose()
    }
    Assert-ArchiveRule $nestedUnsafeOuter 'artifact-archive-entry-path' `
        'Unsafe nested archive entry paths must fail closed.'

    $corruptTopLevel = Join-Path $fixtureRoot 'corrupt-top-level.zip'
    [System.IO.File]::WriteAllBytes(
        $corruptTopLevel,
        [System.Text.Encoding]::UTF8.GetBytes('not a zip archive'))
    Assert-ArchiveRule $corruptTopLevel 'artifact-archive-unreadable' `
        'A corrupt top-level archive must become a stable finding.'

    $corruptNested = Join-Path $fixtureRoot 'corrupt-nested.zip'
    New-SingleEntryArchive $corruptNested 'BOOT-INF/lib/corrupt.jar' `
        ([System.Text.Encoding]::UTF8.GetBytes('not a nested archive'))
    Assert-ArchiveRule $corruptNested 'artifact-archive-unreadable' `
        'A corrupt nested archive must become a stable finding.'

    $perArchiveBudget = Join-Path $fixtureRoot 'per-archive-budget.zip'
    New-EmptyEntryArchive $perArchiveBudget 4097
    Assert-ArchiveRule $perArchiveBudget 'artifact-archive-entry-limit' `
        'An archive above the bounded per-archive entry budget must fail closed.'

    $nestedBudgetPayload = Join-Path $fixtureRoot 'global-budget-payload.zip'
    New-EmptyEntryArchive $nestedBudgetPayload 4000
    $globalBudgetArchive = Join-Path $fixtureRoot 'global-entry-budget.zip'
    $globalZip = [System.IO.Compression.ZipFile]::Open(
        $globalBudgetArchive,
        [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        for ($index = 0; $index -lt 9; $index++) {
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $globalZip,
                $nestedBudgetPayload,
                ('nested-{0:D2}.zip' -f $index),
                [System.IO.Compression.CompressionLevel]::NoCompression) | Out-Null
        }
    }
    finally {
        $globalZip.Dispose()
    }
    Assert-ArchiveRule $globalBudgetArchive 'artifact-archive-global-entry-limit' `
        'Nested archives above the bounded global entry budget must fail closed.'

    $multiArchiveBudgetRoot = Join-Path $fixtureRoot 'multi-archive-entry-budget'
    New-Item -ItemType Directory -Force -Path $multiArchiveBudgetRoot | Out-Null
    for ($index = 0; $index -lt 9; $index++) {
        Copy-Item -LiteralPath $nestedBudgetPayload -Destination `
            (Join-Path $multiArchiveBudgetRoot ('archive-{0:D2}.zip' -f $index))
    }
    Assert-ArchiveRule $multiArchiveBudgetRoot 'artifact-archive-global-entry-limit' `
        'The global entry budget must be shared across top-level archives.'

    $unsafeBudgetPayload = Join-Path $fixtureRoot 'unsafe-global-budget-payload.zip'
    New-UnsafeEntryArchive $unsafeBudgetPayload 4000
    $unsafeMultiArchiveBudgetRoot = Join-Path $fixtureRoot 'unsafe-multi-archive-entry-budget'
    New-Item -ItemType Directory -Force -Path $unsafeMultiArchiveBudgetRoot | Out-Null
    for ($index = 0; $index -lt 9; $index++) {
        Copy-Item -LiteralPath $unsafeBudgetPayload -Destination `
            (Join-Path $unsafeMultiArchiveBudgetRoot ('archive-{0:D2}.zip' -f $index))
    }
    Assert-ArchiveRule $unsafeMultiArchiveBudgetRoot 'artifact-archive-global-entry-limit' `
        'Unsafe archive paths must still count against the invocation-global entry budget.'

    $compressionBomb = Join-Path $fixtureRoot 'compression-bomb.zip'
    $compressionZip = [System.IO.Compression.ZipFile]::Open(
        $compressionBomb,
        [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        $entry = $compressionZip.CreateEntry(
            'highly-compressible.txt',
            [System.IO.Compression.CompressionLevel]::Optimal)
        $writer = New-Object System.IO.StreamWriter($entry.Open())
        try {
            $writer.Write(('A' * (1024 * 1024)))
        }
        finally {
            $writer.Dispose()
        }
    }
    finally {
        $compressionZip.Dispose()
    }
    Assert-ArchiveRule $compressionBomb 'artifact-archive-compression-limit' `
        'A high-ratio compressed payload must fail closed.'

    $largeEntryArchive = Join-Path $fixtureRoot 'large-entry.zip'
    $largeEntryZip = [System.IO.Compression.ZipFile]::Open(
        $largeEntryArchive,
        [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        $entry = $largeEntryZip.CreateEntry(
            'oversized.bin',
            [System.IO.Compression.CompressionLevel]::NoCompression)
        $stream = $entry.Open()
        try {
            $bytes = New-RandomBytes (1024 * 1024)
            for ($chunk = 0; $chunk -lt 65; $chunk++) {
                $stream.Write($bytes, 0, $bytes.Length)
            }
        }
        finally {
            $stream.Dispose()
        }
    }
    finally {
        $largeEntryZip.Dispose()
    }
    Assert-ArchiveRule $largeEntryArchive 'artifact-archive-size-limit' `
        'An entry above the per-entry byte budget must fail closed.'

    $textBudgetArchive = Join-Path $fixtureRoot 'text-budget.zip'
    $textBudgetZip = [System.IO.Compression.ZipFile]::Open(
        $textBudgetArchive,
        [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        for ($index = 0; $index -lt 17; $index++) {
            $entry = $textBudgetZip.CreateEntry(
                ('random-{0:D2}.txt' -f $index),
                [System.IO.Compression.CompressionLevel]::NoCompression)
            $stream = $entry.Open()
            try {
                $bytes = New-RandomBytes (1024 * 1024)
                $stream.Write($bytes, 0, $bytes.Length)
            }
            finally {
                $stream.Dispose()
            }
        }
    }
    finally {
        $textBudgetZip.Dispose()
    }
    Assert-ArchiveRule $textBudgetArchive 'artifact-archive-text-size-limit' `
        'Text expansion above the global text byte budget must fail closed.'

    $multiTextBudgetRoot = Join-Path $fixtureRoot 'multi-archive-text-budget'
    New-Item -ItemType Directory -Force -Path $multiTextBudgetRoot | Out-Null
    $nineMegabytes = New-RandomBytes (9 * 1024 * 1024)
    New-SingleEntryArchive (Join-Path $multiTextBudgetRoot 'first.zip') `
        'first.txt' $nineMegabytes
    New-SingleEntryArchive (Join-Path $multiTextBudgetRoot 'second.zip') `
        'second.txt' $nineMegabytes
    Assert-ArchiveRule $multiTextBudgetRoot 'artifact-archive-text-size-limit' `
        'The global text byte budget must be shared across top-level archives.'

    $deepArchive = Join-Path $fixtureRoot 'depth-0.zip'
    $depthZip = [System.IO.Compression.ZipFile]::Open(
        $deepArchive,
        [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        [void]$depthZip.CreateEntry('safe.bin')
    }
    finally {
        $depthZip.Dispose()
    }
    for ($depth = 1; $depth -le 4; $depth++) {
        $wrappedArchive = Join-Path $fixtureRoot ('depth-{0}.zip' -f $depth)
        $wrapper = [System.IO.Compression.ZipFile]::Open(
            $wrappedArchive,
            [System.IO.Compression.ZipArchiveMode]::Create)
        try {
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $wrapper,
                $deepArchive,
                ('nested-{0}.zip' -f $depth),
                [System.IO.Compression.CompressionLevel]::NoCompression) | Out-Null
        }
        finally {
            $wrapper.Dispose()
        }
        $deepArchive = $wrappedArchive
    }
    Assert-ArchiveRule $deepArchive 'artifact-archive-depth-limit' `
        'Nested archives above the recursion depth budget must fail closed.'

    $reparseRoot = Join-Path $fixtureRoot 'reparse-root'
    $reparseTarget = Join-Path $fixtureRoot 'reparse-target'
    New-Item -ItemType Directory -Force -Path $reparseRoot | Out-Null
    New-Item -ItemType Directory -Force -Path $reparseTarget | Out-Null
    Set-Content -LiteralPath (Join-Path $reparseTarget 'private.md') `
        -Value '# Must not be followed' -Encoding UTF8
    $junctionPath = Join-Path $reparseRoot 'linked-directory'
    New-Item -ItemType Junction -Path $junctionPath -Target $reparseTarget | Out-Null
    $reparseOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File $checker -Path $reparseRoot 2>&1 | Out-String)
    if ($LASTEXITCODE -eq 0 -or
            $reparseOutput -notmatch 'filesystem-reparse-point') {
        throw "Directory reparse points must fail closed. Output: $reparseOutput"
    }

    Write-Output 'privacy-check tests passed'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
}
