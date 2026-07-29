package com.portfolio.agent.answer.adapter.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.gateway.ModelExpressionPort;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import org.springframework.web.client.RestClient;

import java.util.Objects;

public final class ModelProviderAdapterFactory {

    public ModelExpressionPort create(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            ModelProviderDescriptor descriptor,
            String selectedApiKey,
            int maxTokens,
            DiagnosticEventPublisher diagnosticEventPublisher
    ) {
        return new OpenAiCompatibleModelExpressionAdapter(
                builder,
                objectMapper,
                new ModelPromptFactory(objectMapper),
                Objects.requireNonNull(descriptor, "descriptor"),
                selectedApiKey == null ? "" : selectedApiKey.strip(),
                maxTokens,
                Objects.requireNonNull(
                        diagnosticEventPublisher,
                        "diagnosticEventPublisher"));
    }
}
