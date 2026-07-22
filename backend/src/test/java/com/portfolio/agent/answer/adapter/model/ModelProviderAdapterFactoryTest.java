package com.portfolio.agent.answer.adapter.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.gateway.ModelExpressionPort;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class ModelProviderAdapterFactoryTest {

    @Test
    void createsAnAdapterForTheSelectedDescriptorWithABlankKey() {
        ModelProviderDescriptor descriptor = ModelProviderRegistrySnapshot.builtIn()
                .getRequiredDescriptor(com.portfolio.agent.answer.domain.ModelProviderKind.GLM_4_7);

        ModelExpressionPort port = new ModelProviderAdapterFactory().create(
                RestClient.builder(), new ObjectMapper(), descriptor, "   ", 1200);

        assertThat(port).isInstanceOf(OpenAiCompatibleModelExpressionAdapter.class);
    }
}
