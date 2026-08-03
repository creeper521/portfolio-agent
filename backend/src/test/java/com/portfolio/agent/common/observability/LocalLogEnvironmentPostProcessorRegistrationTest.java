package com.portfolio.agent.common.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.io.support.SpringFactoriesLoader;

import static org.assertj.core.api.Assertions.assertThat;

class LocalLogEnvironmentPostProcessorRegistrationTest {

    @Test
    void registersWithSpringBootEnvironmentPostProcessorDiscovery() {
        assertThat(SpringFactoriesLoader.loadFactoryNames(
                EnvironmentPostProcessor.class,
                LocalLogEnvironmentPostProcessorRegistrationTest.class.getClassLoader()))
                .contains(LocalLogEnvironmentPostProcessor.class.getName());
    }
}
