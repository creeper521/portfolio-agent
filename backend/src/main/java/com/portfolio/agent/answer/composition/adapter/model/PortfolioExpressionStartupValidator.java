package com.portfolio.agent.answer.composition.adapter.model;

import com.portfolio.agent.answer.composition.domain.MaterialKind;
import com.portfolio.agent.answer.domain.ModelProviderKind;
import java.time.Duration;

public final class PortfolioExpressionStartupValidator {
    public void validate(PortfolioExpressionProperties properties, boolean production, String apiKey, String httpsEndpoint, boolean registryCompatible, boolean schemaSupported) {
        if (properties == null) throw new IllegalArgumentException("properties must not be null");
        if (!properties.isEnabled()) return;
        if (properties.getProvider() != ModelProviderKind.DEEPSEEK_V4_FLASH
                || properties.getMaxOutputTokens() < 1
                || properties.getMaxOutputTokens() > 1600 || properties.getTimeout() == null
                || properties.getTimeout().isZero() || properties.getTimeout().isNegative()
                || properties.getTimeout().compareTo(Duration.ofSeconds(4)) > 0) {
            throw new IllegalStateException("expression limits or provider exceed policy");
        }
        if (!properties.getAllowedMaterialKinds().equals(java.util.Set.of(MaterialKind.FACT))) {
            throw new IllegalStateException("unsupported material kind");
        }
        if (!"p4-expression-policy-v1".equals(properties.getPolicyVersion())
                || !"portfolio-expression-input.v1".equals(properties.getInputSchemaVersion())
                || !"portfolio-expression-draft.v1".equals(properties.getDraftSchemaVersion())) {
            throw new IllegalStateException("expression schema or policy unsupported");
        }
        if (production && (!properties.isExternalPublicDataPolicyApproved()
                || apiKey == null || apiKey.isBlank()
                || httpsEndpoint == null || !httpsEndpoint.startsWith("https://")
                || !registryCompatible || !schemaSupported)) {
            throw new IllegalStateException("production expression prerequisites missing");
        }
    }
}
