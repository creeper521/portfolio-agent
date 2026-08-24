package com.portfolio.agent.infrastructure.model.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRuntimePropertiesBindingTest {

    @Test
    void bindsGlmAndQwenFromTheClosedModelRuntimeNamespace() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("portfolio.model-runtime.enabled", "true");
        values.put("portfolio.model-runtime.default-model-ref", "glm-4-7-flash");
        values.put("portfolio.model-runtime.models.glm-4-7-flash.enabled", "true");
        values.put("portfolio.model-runtime.models.glm-4-7-flash.selection-version", "glm-v1");
        values.put("portfolio.model-runtime.models.glm-4-7-flash.protocol-profile",
                "ZHIPU_CHAT_COMPLETIONS");
        values.put("portfolio.model-runtime.models.qwen-3-7-flash.enabled", "true");
        values.put("portfolio.model-runtime.models.qwen-3-7-flash.selection-version", "qwen-v1");
        values.put("portfolio.model-runtime.models.qwen-3-7-flash.protocol-profile",
                "DASHSCOPE_CHAT_COMPLETIONS");

        ModelRuntimeProperties properties = new Binder(
                new MapConfigurationPropertySource(values))
                .bind("portfolio.model-runtime", ModelRuntimeProperties.class)
                .orElseThrow(() -> new AssertionError("model runtime did not bind"));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getDefaultModelRef()).isEqualTo("glm-4-7-flash");
        assertThat(properties.getModels()).containsOnlyKeys(
                "glm-4-7-flash", "qwen-3-7-flash");
        assertThat(properties.getModels().get("glm-4-7-flash").getProtocolProfile())
                .isEqualTo("ZHIPU_CHAT_COMPLETIONS");
        assertThat(properties.getModels().get("qwen-3-7-flash").getProtocolProfile())
                .isEqualTo("DASHSCOPE_CHAT_COMPLETIONS");
    }
}
