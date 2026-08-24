package com.portfolio.agent.evaluation.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class EvalCliBootstrapTest {

    @AfterEach
    void cleanProperties() {
        System.clearProperty("PORTFOLIO_MODEL_RUNTIME_ENABLED");
    }

    @Test
    void forcedOffArgumentsWinEvenWhenEnvironmentPreSetsModelEnabledTrue() {
        // Adversarial environment: everything that could enable the real
        // provider is pre-set to true through relaxed-binding system properties.
        System.setProperty("PORTFOLIO_MODEL_RUNTIME_ENABLED", "true");

        ConfigurableApplicationContext context = new org.springframework.boot.builder
                .SpringApplicationBuilder(com.portfolio.agent.PortfolioAgentApplication.class)
                .web(org.springframework.boot.WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off")
                .run(EvalCliBootstrap.FORCED_OFF_ARGS);

        try {
            assertThat(context.getEnvironment().getProperty(
                    "portfolio.model-runtime.enabled")).isEqualTo("false");
        } finally {
            context.close();
        }
    }
}
