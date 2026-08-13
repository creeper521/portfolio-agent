package com.portfolio.agent.common.observability;

import com.portfolio.agent.PortfolioAgentApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

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
}
