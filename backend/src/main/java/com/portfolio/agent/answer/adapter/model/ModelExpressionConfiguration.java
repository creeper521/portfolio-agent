package com.portfolio.agent.answer.adapter.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.ModelPolicy;
import com.portfolio.agent.answer.gateway.ModelExpressionPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

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

    @Bean
    ModelExpressionPort modelExpressionPort(
            ObjectMapper objectMapper,
            ModelExpressionProperties properties,
            ModelProviderRegistrySnapshot registry
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getTimeout());
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory);
        ModelProviderDescriptor descriptor =
                registry.getRequiredDescriptor(properties.getProvider());
        return new ModelProviderAdapterFactory().create(
                builder,
                objectMapper,
                descriptor,
                properties.apiKeyFor(properties.getProvider()),
                properties.getMaxTokens()
        );
    }
}
