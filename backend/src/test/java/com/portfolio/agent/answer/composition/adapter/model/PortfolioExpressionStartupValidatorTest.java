package com.portfolio.agent.answer.composition.adapter.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PortfolioExpressionStartupValidatorTest {

    private final PortfolioExpressionStartupValidator validator = new PortfolioExpressionStartupValidator();

    @Test
    void disabledConfigurationReturnsWithoutExternalPrerequisites() {
        PortfolioExpressionProperties properties = new PortfolioExpressionProperties();

        assertThatCode(() -> validator.validate(properties, true, "", "http://invalid", false, false))
                .doesNotThrowAnyException();
    }

    @Test
    void enabledConfigurationRejectsUnsafeLimitsAndMaterialKinds() {
        PortfolioExpressionProperties properties = new PortfolioExpressionProperties();
        properties.setEnabled(true);
        properties.setTimeout(Duration.ofSeconds(5));

        assertThatThrownBy(() -> validator.validate(properties, false, "", "", true, true))
                .isInstanceOf(IllegalStateException.class);

        properties.setTimeout(Duration.ofSeconds(4));
        properties.setAllowedMaterialKinds(Set.of(com.portfolio.agent.answer.composition.domain.MaterialKind.COMPARISON));
        assertThatThrownBy(() -> validator.validate(properties, false, "", "", true, true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productionEnabledConfigurationRequiresAllApprovedBoundaries() {
        PortfolioExpressionProperties properties = new PortfolioExpressionProperties();
        properties.setEnabled(true);
        properties.setExternalPublicDataPolicyApproved(true);

        assertThatThrownBy(() -> validator.validate(properties, true, "",
                "https://api.deepseek.com/chat/completions", true, true))
                .isInstanceOf(IllegalStateException.class);

        assertThatCode(() -> validator.validate(properties, true, "secret",
                "https://api.deepseek.com/chat/completions", true, true))
                .doesNotThrowAnyException();
    }

    @Test
    void enabledConfigurationRejectsUnsupportedProviderAndSchema() {
        PortfolioExpressionProperties properties = new PortfolioExpressionProperties();
        properties.setEnabled(true);
        properties.setProvider(com.portfolio.agent.answer.domain.ModelProviderKind.GLM_4_7);
        assertThatThrownBy(() -> validator.validate(
                properties, false, "", "https://example.test", true, true))
                .isInstanceOf(IllegalStateException.class);

        properties.setProvider(com.portfolio.agent.answer.domain.ModelProviderKind.DEEPSEEK_V4_FLASH);
        properties.setInputSchemaVersion("future-schema");
        assertThatThrownBy(() -> validator.validate(
                properties, false, "", "https://example.test", true, true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowedKindsMustRemainExactlyFact() {
        PortfolioExpressionProperties properties = new PortfolioExpressionProperties();
        properties.setEnabled(true);
        properties.setAllowedMaterialKinds(Set.of());
        assertThatThrownBy(() -> validator.validate(
                properties, false, "", "https://example.test", true, true))
                .isInstanceOf(IllegalStateException.class);
    }
}
