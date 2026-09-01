package com.portfolio.agent.infrastructure.model.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRuntimePropertiesBindingTest {

    @Test
    void publishedQwenV8IsSelectableAndCannotBeOverriddenFromTheEnvironment() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/application.yml")) {
            assertThat(input).isNotNull();
            String configuration = new String(
                    input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(configuration)
                    .contains("# Qwen v8 is published in the selectable catalog.")
                    .contains("default-model-ref: qwen-3-7-flash")
                    .doesNotContain("PORTFOLIO_QWEN_SELECTABLE");
            assertThat(configuration).containsPattern(
                    "(?s)qwen-3-7-flash:\\s+enabled:.*?\\s+selectable: true"
                            + ".*?selection-version: qwen-3-7-flash-v8");
        }
    }

    @Test
    void bindsGlmAndQwenFromTheClosedModelRuntimeNamespace() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("portfolio.model-runtime.enabled", "true");
        values.put("portfolio.model-runtime.default-model-ref", "glm-4-7-flash");
        values.put("portfolio.model-runtime.models.glm-4-7-flash.enabled", "true");
        values.put("portfolio.model-runtime.models.glm-4-7-flash.selection-version", "glm-v1");
        values.put("portfolio.model-runtime.models.glm-4-7-flash.execution-profile",
                "GLM_4_7_FLASH_STRUCTURED_V4");
        values.put("portfolio.model-runtime.models.qwen-3-7-flash.enabled", "true");
        values.put("portfolio.model-runtime.models.qwen-3-7-flash.selection-version", "qwen-v1");
        values.put("portfolio.model-runtime.models.qwen-3-7-flash.execution-profile",
                "QWEN_3_7_FLASH_STRUCTURED_V8");

        ModelRuntimeProperties properties = new Binder(
                new MapConfigurationPropertySource(values))
                .bind("portfolio.model-runtime", ModelRuntimeProperties.class)
                .orElseThrow(() -> new AssertionError("model runtime did not bind"));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getDefaultModelRef()).isEqualTo("glm-4-7-flash");
        assertThat(properties.getModels()).containsOnlyKeys(
                "glm-4-7-flash", "qwen-3-7-flash");
        assertThat(properties.getModels().get("glm-4-7-flash").getExecutionProfile())
                .isEqualTo("GLM_4_7_FLASH_STRUCTURED_V4");
        assertThat(properties.getModels().get("qwen-3-7-flash").getExecutionProfile())
                .isEqualTo("QWEN_3_7_FLASH_STRUCTURED_V8");
    }
}
