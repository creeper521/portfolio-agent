package com.portfolio.agent.answer.adapter.model;

import com.portfolio.agent.answer.domain.ModelPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ModelExpressionProperties.class)
public class ModelExpressionConfiguration {

    @Bean
    ModelProviderRegistrySnapshot modelProviderRegistry() {
        return ModelProviderRegistrySnapshot.builtIn();
    }

    @Bean
    ModelPolicy modelPolicy(
            ModelExpressionProperties properties,
            ModelProviderRegistrySnapshot registry
    ) {
        String selectedApiKey = properties.apiKeyFor(properties.getProvider());
        boolean registryCompatible = registry.supports(
                properties.getProvider(),
                properties.getModelPolicyVersion(),
                properties.getAnswerSchemaVersion());
        return new ModelPolicy(
                properties.getModelPolicyVersion(),
                properties.getAnswerSchemaVersion(),
                properties.getProvider(),
                properties.isEnabled(),
                properties.isExternalDataPolicyApproved(),
                !selectedApiKey.isBlank() && registryCompatible,
                properties.getTimeout(),
                properties.getMaxTokens(),
                properties.getMaxModelAttempts()
        );
    }
}
