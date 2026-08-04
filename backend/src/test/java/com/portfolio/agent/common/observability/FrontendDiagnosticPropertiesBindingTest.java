package com.portfolio.agent.common.observability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FrontendDiagnosticPropertiesBindingTest {

    @Test
    void launcherEnvironmentSwitchActivatesFrontendIngestEnabled() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addLast(new SystemEnvironmentPropertySource(
                "portfolio-launcher-environment",
                Map.of("PORTFOLIO_DIAGNOSTICS_FRONTEND_INGEST_ENABLED", "true")));

        FrontendDiagnosticProperties properties = Binder.get(environment)
                .bind("portfolio.diagnostics", FrontendDiagnosticProperties.class)
                .get();

        assertThat(properties.isFrontendIngestEnabled()).isTrue();
    }

    @Test
    void frontendIngestEnabledDefaultsToFalse() {
        FrontendDiagnosticProperties properties = Binder.get(new StandardEnvironment())
                .bind("portfolio.diagnostics", FrontendDiagnosticProperties.class)
                .orElse(new FrontendDiagnosticProperties());

        assertThat(properties.isFrontendIngestEnabled()).isFalse();
    }
}
