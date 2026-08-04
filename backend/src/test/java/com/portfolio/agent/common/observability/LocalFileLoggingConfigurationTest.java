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
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PortfolioAgentApplication.class)
@ActiveProfiles("local")
class LocalFileLoggingConfigurationTest {

    @Autowired
    private Environment environment;

    @Test
    void browserOriginFilterIsWiredIntoBothBackendFileAppenders() throws IOException {
        String resource = new String(
                Objects.requireNonNull(getClass().getClassLoader()
                        .getResourceAsStream("logback-spring.xml"))
                        .readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(appenderBlock(resource, "BACKEND_INFO"))
                .contains("com.portfolio.agent.common.observability.BrowserOriginEventFilter");
        assertThat(appenderBlock(resource, "BACKEND_ERROR"))
                .contains("com.portfolio.agent.common.observability.BrowserOriginEventFilter");
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

    private String appenderBlock(String resource, String appenderName) {
        String startTag = "<appender name=\"" + appenderName + "\"";
        int start = resource.indexOf(startTag);
        assertThat(start).isNotNegative();
        int end = resource.indexOf("</appender>", start);
        assertThat(end).isNotNegative();
        return resource.substring(start, end);
    }
}
