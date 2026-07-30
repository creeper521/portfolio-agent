package com.portfolio.agent.selection.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PortfolioSelectionControllerConfigurationTest {

    @Test
    void controllerIsAbsentWhenPublicDatabaseIsDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(PortfolioSelectionController.class)
                .withPropertyValues("portfolio.database.public.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(PortfolioSelectionController.class));
    }
}
