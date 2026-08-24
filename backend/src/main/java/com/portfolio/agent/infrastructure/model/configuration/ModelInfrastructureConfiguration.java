package com.portfolio.agent.infrastructure.model.configuration;

import com.portfolio.agent.infrastructure.model.policy.ModelPolicy;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderRegistrySnapshot;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ModelExpressionProperties.class)
public class ModelInfrastructureConfiguration {

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
        boolean registryCompatible = registry.isApprovedConfiguration(
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
                properties.getMaxTokens(),
                properties.getMaxModelAttempts()
        );
    }
}
