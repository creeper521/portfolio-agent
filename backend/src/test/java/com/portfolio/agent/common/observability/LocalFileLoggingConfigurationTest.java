package com.portfolio.agent.common.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.portfolio.agent.PortfolioAgentApplication;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = PortfolioAgentApplication.class)
@ActiveProfiles("local")
class LocalFileLoggingConfigurationTest {

    @Autowired
    private Environment environment;

    @Test
    void backendFileAppendersPreserveSafetyAndRenderStructuredKeyValues() throws IOException {
        String resource = loggingConfiguration();

        assertThat(appenderBlock(resource, "BACKEND_INFO"))
                .contains("com.portfolio.agent.common.observability.BrowserOriginEventFilter")
                .contains("ch.qos.logback.classic.filter.LevelFilter")
                .contains("<onMatch>DENY</onMatch>")
                .contains("ch.qos.logback.classic.filter.ThresholdFilter")
                .contains("%replace(%kvp)")
                .contains("<charset>UTF-8</charset>")
                .contains("ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy")
                .contains("<maxFileSize>20MB</maxFileSize>")
                .contains("<maxHistory>30</maxHistory>")
                .contains("<totalSizeCap>1GB</totalSizeCap>");
        assertThat(appenderBlock(resource, "BACKEND_ERROR"))
                .contains("com.portfolio.agent.common.observability.BrowserOriginEventFilter")
                .contains("ch.qos.logback.classic.filter.ThresholdFilter")
                .contains("<level>ERROR</level>")
                .contains("%replace(%kvp)")
                .contains("<charset>UTF-8</charset>")
                .contains("ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy")
                .contains("<maxFileSize>20MB</maxFileSize>")
                .contains("<maxHistory>30</maxHistory>")
                .contains("<totalSizeCap>1GB</totalSizeCap>");
    }

    @Test
    void plainConsoleProfilesUseKvpWhileProductionRemainsStructured() throws IOException {
        String resource = loggingConfiguration();

        assertThat(resource).contains("name=\"SAFE_PLAIN_CONSOLE_LOG_PATTERN\"")
                .contains("%replace(%kvp)");
        assertThat(profileBlock(resource, "local-file-logging"))
                .contains("name=\"CONSOLE_LOG_PATTERN\" value=\"${SAFE_PLAIN_CONSOLE_LOG_PATTERN}\"")
                .contains("logging/logback/console-appender.xml");
        assertThat(profileBlock(resource, "!prod &amp; !local-file-logging"))
                .contains("name=\"CONSOLE_LOG_PATTERN\" value=\"${SAFE_PLAIN_CONSOLE_LOG_PATTERN}\"")
                .contains("logging/logback/console-appender.xml");
        assertThat(profileBlock(resource, "prod &amp; !local-file-logging"))
                .contains("logging/logback/structured-console-appender.xml")
                .doesNotContain("CONSOLE_LOG_PATTERN")
                .doesNotContain("%kvp");
    }

    @Test
    void browserDiagnosticsNeverReachBackendActivityFiles() throws IOException {
        String directory = environment.getProperty("portfolio.log-directory");
        assertThat(directory).isNotNull();

        String browserMarker = "browser-" + UUID.randomUUID();
        String backendMarker = "backend-" + UUID.randomUUID();

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger sourceLogger = context.getLogger(
                "com.portfolio.agent.common.web.FrontendDiagnosticsController");
        Slf4jDiagnosticEventPublisher publisher = new Slf4jDiagnosticEventPublisher(
                sourceLogger, new DroppedDiagnosticCounter());

        DiagnosticEvent browserEvent = DiagnosticEvent.builder(
                        "frontend.agent.request.completed", DiagnosticLevel.INFO)
                .field("event.origin", "browser")
                .field("client.session.id", "session-" + browserMarker)
                .field("client.request.id", "request-" + browserMarker)
                .build();
        publisher.publish(browserEvent);

        sourceLogger.info("local backend marker " + backendMarker);

        Path backendInfo = Path.of(directory, "current", "backend-info.log");
        String fileContent = Files.readString(backendInfo, StandardCharsets.UTF_8);

        assertThat(fileContent).contains(backendMarker);
        assertThat(fileContent).doesNotContain(browserMarker);
    }

    @Test
    void backendFilesRenderClosedDiagnosticsWithoutSensitiveSentinels() throws IOException {
        String directory = environment.getProperty("portfolio.log-directory");
        assertThat(directory).isNotNull();

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger sourceLogger = context.getLogger("com.portfolio.agent.diagnostics");
        Slf4jDiagnosticEventPublisher publisher = new Slf4jDiagnosticEventPublisher(
                sourceLogger, new DroppedDiagnosticCounter());

        DiagnosticEvent schemaRejection = DiagnosticEvent.builder(
                        "provider.output.rejected", DiagnosticLevel.WARN)
                .field("provider.operation", "GOAL_INTERPRETATION")
                .field("failure.code", "SELECTED_MODEL_INVALID_RESPONSE")
                .field("failure.layer", "SCHEMA")
                .field("failure.reason", "CLARIFICATION_BLOCKED_GOAL_REQUIRED")
                .build();
        DiagnosticEvent startupFailure = DiagnosticEvent.builder(
                        "application.startup.failed", DiagnosticLevel.ERROR)
                .field("failure.code", "CONFIGURATION_INVALID")
                .build();

        Map<String, String> forbiddenFields = Map.of(
                "visitor-question", "USER_TEXT_SENTINEL_MUST_NOT_LEAK",
                "modelPrompt", "PROMPT_SENTINEL_MUST_NOT_LEAK",
                "provider-payload", "PROVIDER_OUTPUT_SENTINEL_MUST_NOT_LEAK",
                "request.authorization", "AUTHORIZATION_SENTINEL_MUST_NOT_LEAK",
                "api.key", "API_KEY_SENTINEL_MUST_NOT_LEAK");
        forbiddenFields.forEach((field, sentinel) -> assertThatThrownBy(() ->
                DiagnosticEvent.builder("provider.output.rejected", DiagnosticLevel.WARN)
                        .field(field, sentinel))
                .isInstanceOf(IllegalArgumentException.class));

        publisher.publish(schemaRejection);
        publisher.publish(startupFailure);

        String infoContent = Files.readString(
                Path.of(directory, "current", "backend-info.log"), StandardCharsets.UTF_8);
        String errorContent = Files.readString(
                Path.of(directory, "current", "backend-error.log"), StandardCharsets.UTF_8);

        assertThat(infoContent)
                .contains("event.name=provider.output.rejected")
                .contains("provider.operation=GOAL_INTERPRETATION")
                .contains("failure.code=SELECTED_MODEL_INVALID_RESPONSE")
                .contains("failure.layer=SCHEMA")
                .contains("failure.reason=CLARIFICATION_BLOCKED_GOAL_REQUIRED")
                .doesNotContain("failure.code=CONFIGURATION_INVALID");
        assertThat(errorContent)
                .contains("event.name=application.startup.failed")
                .contains("failure.code=CONFIGURATION_INVALID");
        forbiddenFields.values().forEach(sentinel -> {
            assertThat(infoContent).doesNotContain(sentinel);
            assertThat(errorContent).doesNotContain(sentinel);
        });
    }

    private String loggingConfiguration() throws IOException {
        return new String(
                Objects.requireNonNull(getClass().getClassLoader()
                        .getResourceAsStream("logback-spring.xml"))
                        .readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private String appenderBlock(String resource, String appenderName) {
        String startTag = "<appender name=\"" + appenderName + "\"";
        int start = resource.indexOf(startTag);
        assertThat(start).isNotNegative();
        int end = resource.indexOf("</appender>", start);
        assertThat(end).isNotNegative();
        return resource.substring(start, end);
    }

    private String profileBlock(String resource, String profileName) {
        String startTag = "<springProfile name=\"" + profileName + "\">";
        int start = resource.indexOf(startTag);
        assertThat(start).isNotNegative();
        int end = resource.indexOf("</springProfile>", start);
        assertThat(end).isNotNegative();
        return resource.substring(start, end);
    }
}
