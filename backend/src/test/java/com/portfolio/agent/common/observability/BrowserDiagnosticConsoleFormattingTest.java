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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PortfolioAgentApplication.class)
@ActiveProfiles("local")
class BrowserDiagnosticConsoleFormattingTest {

    @Test
    void browserDiagnosticRetainsOriginInTheConsoleLineConsumedByTheRouter() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        ConsoleAppender<ILoggingEvent> consoleAppender = (ConsoleAppender<ILoggingEvent>)
                rootLogger.getAppender("CONSOLE");
        Encoder<ILoggingEvent> encoder = consoleAppender.getEncoder();
        LayoutWrappingEncoder<ILoggingEvent> wrappingEncoder =
                (LayoutWrappingEncoder<ILoggingEvent>) encoder;

        DiagnosticEvent diagnosticEvent = DiagnosticEvent.builder(
                        "frontend.agent.request.completed", DiagnosticLevel.INFO)
                .field("event.origin", "browser")
                .field("client.session.id", "session-1")
                .field("client.request.id", "request-1")
                .build();

        ListAppender<ILoggingEvent> capture = new ListAppender<>();
        capture.start();
        Logger sourceLogger = context.getLogger(
                "com.portfolio.agent.common.web.FrontendDiagnosticsController");
        sourceLogger.addAppender(capture);
        try {
            new Slf4jDiagnosticEventPublisher(
                    sourceLogger, new DroppedDiagnosticCounter()).publish(diagnosticEvent);
            ILoggingEvent loggedEvent = capture.list.get(0);
            String renderedLine = wrappingEncoder.getLayout().doLayout(loggedEvent);

            assertThat(renderedLine).contains("event.origin=browser");
            assertThat(renderedLine).containsPattern("(?:^|\\s)event\\.origin=browser(?:\\s|$)");
        } finally {
            sourceLogger.detachAppender(capture);
        }
    }
}
