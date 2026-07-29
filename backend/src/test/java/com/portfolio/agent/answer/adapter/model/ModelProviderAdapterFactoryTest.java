package com.portfolio.agent.answer.adapter.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.gateway.ModelExpressionPort;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class ModelProviderAdapterFactoryTest {

    @Test
    void createsAnAdapterForTheSelectedDescriptorWithABlankKey() {
        ModelProviderDescriptor descriptor = ModelProviderRegistrySnapshot.builtIn()
                .getRequiredDescriptor(com.portfolio.agent.answer.domain.ModelProviderKind.GLM_4_7);
        DiagnosticEventPublisher publisher = event -> { };

        ModelExpressionPort port = new ModelProviderAdapterFactory().create(
                RestClient.builder(),
                new ObjectMapper(),
                descriptor,
                "   ",
                1200,
                publisher);

        assertThat(port).isInstanceOf(OpenAiCompatibleModelExpressionAdapter.class);
    }
}
