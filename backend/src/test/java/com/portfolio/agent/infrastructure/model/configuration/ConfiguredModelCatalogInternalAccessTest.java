package com.portfolio.agent.infrastructure.model.configuration;

import com.portfolio.agent.infrastructure.model.ModelExecutionResolutionException;
import com.portfolio.agent.infrastructure.model.ModelExecutionResolver;
import com.portfolio.agent.infrastructure.model.provider.ModelRef;
import com.portfolio.agent.infrastructure.model.structured.StructuredModelTestFixtures;
import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguredModelCatalogInternalAccessTest {

    @Test
    void nonSelectableApprovedModelIsAvailableOnlyToControlledServerAccess() {
        ModelRuntimeProperties properties = runtimeWithNonSelectableModel();
        ConfiguredModelCatalog catalog = StructuredModelTestFixtures.catalog(properties);

        assertThat(catalog.snapshot().getEntries()).isEmpty();
        assertThatThrownBy(() -> catalog.snapshot().getRequiredDescriptor(
                ModelRef.of("canary-model")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(catalog.getRequiredInternalDescriptor(ModelRef.of("canary-model"))
                .getModelName()).isEqualTo("qwen3.7-flash");
        assertThat(catalog.getRequiredBinding(ModelRef.of("canary-model"))
                .getModelName()).isEqualTo("qwen3.7-flash");

        ModelExecutionResolver turnResolver = new ModelExecutionResolver(
                catalog.snapshot(), catalog::getRequiredBinding);
        assertThatThrownBy(() -> turnResolver.resolve(
                AgentTurnCommand.ModelSelection.model(
                        "canary-model", "qwen-3-7-flash-v8")))
                .isInstanceOf(ModelExecutionResolutionException.class)
                .extracting(failure -> ((ModelExecutionResolutionException) failure).getCode())
                .isEqualTo(ModelExecutionResolutionException.Code.SELECTED_MODEL_UNAVAILABLE);
    }

    private ModelRuntimeProperties runtimeWithNonSelectableModel() {
        ModelRuntimeProperties.ModelSettings settings =
                new ModelRuntimeProperties.ModelSettings();
        settings.setEnabled(true);
        settings.setSelectable(false);
        settings.setDisplayName("Internal canary");
        settings.setDisplayOrder(10);
        settings.setSelectionVersion("qwen-3-7-flash-v8");
        settings.setEndpoint("https://example.test/chat");
        settings.setModel("qwen3.7-flash");
        settings.setApiKey("server-secret");
        settings.setDataPolicyApproved(true);
        settings.setExecutionProfile("QWEN_3_7_FLASH_STRUCTURED_V8");
        settings.setMaxContextTokens(32_000);
        settings.setMaxOutputTokens(2_000);

        ModelRuntimeProperties properties = new ModelRuntimeProperties();
        properties.setEnabled(true);
        properties.setDefaultModelRef("canary-model");
        properties.setModels(new LinkedHashMap<>(Map.of("canary-model", settings)));
        return properties;
    }
}
