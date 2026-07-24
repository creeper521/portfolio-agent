package com.portfolio.agent.answer.adapter.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.ModelPolicy;
import com.portfolio.agent.answer.gateway.ConversationSummaryPort;
import com.portfolio.agent.answer.service.ConversationIntentRouter;
import com.portfolio.agent.answer.service.ConversationWindowManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConversationalAgentProperties.class)
public class ConversationalAgentConfiguration {

    @Bean
    ConversationalPromptFactory conversationalPromptFactory(
            ObjectMapper objectMapper
    ) throws IOException {
        ClassPathResource resource = new ClassPathResource(
                "prompts/portfolio-agent-system.zh-CN.txt");
        return new ConversationalPromptFactory(
                objectMapper,
                resource.getContentAsString(StandardCharsets.UTF_8));
    }

    @Bean
    OpenAiCompatibleConversationalModelAdapter conversationalModelAdapter(
            ObjectMapper objectMapper,
            ConversationalPromptFactory promptFactory,
            ModelExpressionProperties modelProperties,
            ModelProviderRegistrySnapshot registry
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(modelProperties.getTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(modelProperties.getTimeout());
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory);
        return new OpenAiCompatibleConversationalModelAdapter(
                builder,
                objectMapper,
                promptFactory,
                registry.getRequiredDescriptor(modelProperties.getProvider()),
                modelProperties.apiKeyFor(modelProperties.getProvider()),
                modelProperties.getMaxTokens());
    }

    @Bean
    ConversationWindowManager conversationWindowManager(
            ConversationSummaryPort summaryPort,
            ConversationalAgentProperties properties
    ) {
        return new ConversationWindowManager(
                summaryPort,
                properties.getMaxInputTokens(),
                properties.getRecentRawRounds());
    }

    @Bean
    ConversationIntentRouter conversationIntentRouter(
            OpenAiCompatibleConversationalModelAdapter modelAdapter,
            ConversationalAgentProperties properties
    ) {
        return new ConversationIntentRouter(
                modelAdapter,
                properties.getMinimumIntentConfidence());
    }

    @Bean
    ConversationProviderAccess conversationProviderAccess(
            ConversationalAgentProperties properties,
            ModelPolicy modelPolicy,
            ModelProviderRegistrySnapshot registry
    ) {
        return new ConversationProviderAccess(
                properties.allowsProviderCalls(modelPolicy, registry));
    }

    public static final class ConversationProviderAccess {

        private final boolean allowed;

        ConversationProviderAccess(boolean allowed) {
            this.allowed = allowed;
        }

        public boolean isAllowed() {
            return allowed;
        }
    }
}
