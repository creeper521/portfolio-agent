package com.portfolio.agent.common.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.read.ListAppender;
import com.portfolio.agent.PortfolioAgentApplication;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = PortfolioAgentApplication.class)
@ActiveProfiles("local")
class BrowserDiagnosticConsoleFormattingTest {

    @Test
    void browserDiagnosticRetainsOriginInTheConsoleLineConsumedByTheRouter() {
        DiagnosticEvent diagnosticEvent = DiagnosticEvent.builder(
                        "frontend.agent.request.completed", DiagnosticLevel.INFO)
                .field("event.origin", "browser")
                .field("client.session.id", "session-1")
                .field("client.request.id", "request-1")
                .build();

        String renderedLine = render(diagnosticEvent);

        assertThat(renderedLine).contains("event.origin=browser");
        assertThat(renderedLine).containsPattern("(?:^|\\s)event\\.origin=browser(?:\\s|$)");
    }

    @Test
    void consoleRendersClosedFailureFieldsWithoutSensitiveSentinels() {
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

        String renderedLine = render(diagnosticEvent);

        assertThat(renderedLine)
                .contains("event.name=provider.output.rejected")
                .contains("provider.operation=GOAL_INTERPRETATION")
                .contains("failure.code=SELECTED_MODEL_INVALID_RESPONSE")
                .contains("failure.layer=SCHEMA")
                .contains("failure.reason=CLARIFICATION_BLOCKED_GOAL_REQUIRED");
        forbiddenFields.values().forEach(sentinel ->
                assertThat(renderedLine).doesNotContain(sentinel));
    }

    private String render(DiagnosticEvent diagnosticEvent) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        ConsoleAppender<ILoggingEvent> consoleAppender = (ConsoleAppender<ILoggingEvent>)
                rootLogger.getAppender("CONSOLE");
        Encoder<ILoggingEvent> encoder = consoleAppender.getEncoder();
        LayoutWrappingEncoder<ILoggingEvent> wrappingEncoder =
                (LayoutWrappingEncoder<ILoggingEvent>) encoder;

        ListAppender<ILoggingEvent> capture = new ListAppender<>();
        capture.start();
        Logger sourceLogger = context.getLogger(
                "com.portfolio.agent.common.web.FrontendDiagnosticsController");
        sourceLogger.addAppender(capture);
        try {
            new Slf4jDiagnosticEventPublisher(
                    sourceLogger, new DroppedDiagnosticCounter()).publish(diagnosticEvent);
            ILoggingEvent loggedEvent = capture.list.get(0);
            return wrappingEncoder.getLayout().doLayout(loggedEvent);
        } finally {
            sourceLogger.detachAppender(capture);
            capture.stop();
        }
    }
}
