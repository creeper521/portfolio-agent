package com.portfolio.agent.answer.adapter.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.gateway.ModelExpressionPort;
import org.springframework.web.client.RestClient;

import java.util.Objects;

public final class ModelProviderAdapterFactory {

    public ModelExpressionPort create(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            ModelProviderDescriptor descriptor,
            String selectedApiKey,
            int maxTokens
    ) {
        return new OpenAiCompatibleModelExpressionAdapter(
                builder,
                objectMapper,
                new ModelPromptFactory(objectMapper),
                Objects.requireNonNull(descriptor, "descriptor"),
                selectedApiKey == null ? "" : selectedApiKey.strip(),
                maxTokens);
    }
}
