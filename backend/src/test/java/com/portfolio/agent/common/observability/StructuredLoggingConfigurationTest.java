package com.portfolio.agent.common.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.PortfolioAgentApplication;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = PortfolioAgentApplication.class)
@ActiveProfiles("prod")
@TestPropertySource(properties = "portfolio.conversation-context.mode=DISABLED")
class StructuredLoggingConfigurationTest {

    @Autowired
    private Environment environment;

    @Test
    void productionProfileUsesConsoleOnlyEcsLogging() {
        assertThat(environment.getProperty("logging.structured.format.console"))
                .isEqualTo("ecs");
        assertThat(environment.getProperty("logging.file.name")).isNull();
        assertThat(environment.getProperty("logging.file.path")).isNull();
        assertThat(environment.getProperty("logging.level.com.portfolio.agent"))
                .isEqualTo("INFO");
    }

    @Test
    void ecsEncoderRendersEachClosedKeyOnceWithoutSensitiveSentinels() throws Exception {
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

        DiagnosticEvent diagnosticEvent = DiagnosticEvent.builder(
                        "provider.output.rejected", DiagnosticLevel.WARN)
                .field("provider.operation", "GOAL_INTERPRETATION")
                .field("failure.code", "SELECTED_MODEL_INVALID_RESPONSE")
                .field("failure.layer", "SCHEMA")
                .field("failure.reason", "CLARIFICATION_BLOCKED_GOAL_REQUIRED")
                .build();

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger sourceLogger = context.getLogger("prod-structured-logging-test");
        ListAppender<ILoggingEvent> capture = new ListAppender<>();
        capture.setContext(context);
        capture.start();
        sourceLogger.addAppender(capture);
        try {
            new Slf4jDiagnosticEventPublisher(
                    sourceLogger, new DroppedDiagnosticCounter()).publish(diagnosticEvent);

            StructuredLogEncoder encoder = new StructuredLogEncoder();
            encoder.setContext(context);
            encoder.setFormat("ecs");
            encoder.setCharset(StandardCharsets.UTF_8);
            encoder.start();
            try {
                String renderedLine = new String(
                        encoder.encode(capture.list.get(0)), StandardCharsets.UTF_8);
                JsonNode renderedJson = new ObjectMapper().readTree(renderedLine);

                assertThat(renderedLine)
                        .startsWith("{")
                        .doesNotContain("failure.layer=");
                assertThat(renderedLine.trim()).endsWith("}");
                assertThat(renderedJson.path("event").path("name").asText())
                        .isEqualTo("provider.output.rejected");
                assertThat(renderedJson.path("provider").path("operation").asText())
                        .isEqualTo("GOAL_INTERPRETATION");
                assertThat(renderedJson.path("failure").path("code").asText())
                        .isEqualTo("SELECTED_MODEL_INVALID_RESPONSE");
                assertThat(renderedJson.path("failure").path("layer").asText())
                        .isEqualTo("SCHEMA");
                assertThat(renderedJson.path("failure").path("reason").asText())
                        .isEqualTo("CLARIFICATION_BLOCKED_GOAL_REQUIRED");
                assertThat(occurrences(renderedLine, "\"layer\""))
                        .as("structured fields must not be duplicated by a trailing text kvp block")
                        .isEqualTo(1);
                forbiddenFields.values().forEach(sentinel ->
                        assertThat(renderedLine).doesNotContain(sentinel));
            } finally {
                encoder.stop();
            }
        } finally {
            sourceLogger.detachAppender(capture);
            capture.stop();
        }
    }

    private int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }
}
