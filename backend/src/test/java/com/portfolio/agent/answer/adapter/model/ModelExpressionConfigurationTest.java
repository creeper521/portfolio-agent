package com.portfolio.agent.answer.adapter.model;

import com.portfolio.agent.answer.domain.ModelPolicy;
import com.portfolio.agent.answer.domain.ModelProviderKind;
import com.portfolio.agent.answer.gateway.ModelExpressionPort;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ModelExpressionConfigurationTest {

    private final ModelExpressionConfiguration configuration =
            new ModelExpressionConfiguration();

    @Test
    void defaultConfigurationIsDeterministicAndContainsNoCredential() {
        ModelExpressionProperties properties = new ModelExpressionProperties();

        ModelPolicy policy = configuration.modelPolicy(
                properties, configuration.modelProviderRegistry());

        assertThat(policy.isModelEnabled()).isFalse();
        assertThat(policy.getProvider())
                .isEqualTo(ModelProviderKind.DEEPSEEK_V4_FLASH);
        assertThat(policy.getTimeout()).isEqualTo(Duration.ofSeconds(8));
        assertThat(properties.selectedApiKey()).isEmpty();
        ModelExpressionPort port = configuration.modelExpressionPort(
                new com.fasterxml.jackson.databind.ObjectMapper(),
                properties,
                configuration.modelProviderRegistry());
        assertThat(port).isNotNull();
        assertThat(ModelExpressionProperties.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("toString");
    }

    @Test
    void activatesExactlyTheSelectedProviderWhenEveryGateIsApproved() {
        ModelExpressionProperties properties = new ModelExpressionProperties();
        properties.setEnabled(true);
        properties.setExternalDataPolicyApproved(true);
        properties.setProvider(ModelProviderKind.GLM_4_7);
        properties.setDeepseekApiKey("unused-deepseek-test-key");
        properties.setGlmApiKey("selected-glm-test-key");
        properties.setTimeout(Duration.ofSeconds(3));
        properties.setMaxTokens(900);

        ModelPolicy policy = configuration.modelPolicy(
                properties, configuration.modelProviderRegistry());

        assertThat(policy.isModelEnabled()).isTrue();
        assertThat(policy.getProvider()).isEqualTo(ModelProviderKind.GLM_4_7);
        assertThat(properties.selectedApiKey()).isEqualTo("selected-glm-test-key");
        assertThat(policy.getTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(policy.getMaxTokens()).isEqualTo(900);
    }

    @Test
    void neverFallsBackToTheOtherProviderKey() {
        ModelExpressionProperties properties = new ModelExpressionProperties();
        properties.setEnabled(true);
        properties.setExternalDataPolicyApproved(true);
        properties.setProvider(ModelProviderKind.GLM_4_7);
        properties.setDeepseekApiKey("only-non-selected-key-is-present");

        ModelPolicy policy = configuration.modelPolicy(
                properties, configuration.modelProviderRegistry());

        assertThat(properties.selectedApiKey()).isEmpty();
        assertThat(policy.isModelEnabled()).isFalse();
    }

    @Test
    void incompatiblePolicyVersionDisablesModelExpression() {
        ModelExpressionProperties properties = approvedGlmProperties();
        properties.setModelPolicyVersion("unsupported-policy");

        ModelPolicy policy = configuration.modelPolicy(
                properties, configuration.modelProviderRegistry());

        assertThat(policy.isModelEnabled()).isFalse();
    }

    @Test
    void selectedCredentialNeverFallsBackToAnotherProvider() {
        ModelExpressionProperties properties = new ModelExpressionProperties();
        properties.setProvider(ModelProviderKind.GLM_4_7);
        properties.setDeepseekApiKey("deepseek-only-key");

        assertThat(properties.apiKeyFor(ModelProviderKind.GLM_4_7)).isEmpty();
    }

    @Test
    void bindsProviderSecretsFromPortfolioSpecificEnvironmentNames() throws Exception {
        String applicationYaml;
        try (java.io.InputStream input = getClass().getResourceAsStream("/application.yml")) {
            assertThat(input).isNotNull();
            applicationYaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(applicationYaml)
                .contains("${PORTFOLIO_AGENT_DEEPSEEK_API_KEY:}")
                .contains("${PORTFOLIO_AGENT_GLM_API_KEY:}")
                .contains("${PORTFOLIO_MODEL_TIMEOUT:8s}")
                .doesNotContain("${DEEPSEEK_API_KEY:}", "${GLM_API_KEY:}");
    }

    private ModelExpressionProperties approvedGlmProperties() {
        ModelExpressionProperties properties = new ModelExpressionProperties();
        properties.setEnabled(true);
        properties.setExternalDataPolicyApproved(true);
        properties.setProvider(ModelProviderKind.GLM_4_7);
        properties.setGlmApiKey("selected-glm-test-key");
        return properties;
    }
}
