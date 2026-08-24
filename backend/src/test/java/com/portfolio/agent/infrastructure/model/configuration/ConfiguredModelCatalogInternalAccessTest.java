package com.portfolio.agent.infrastructure.model.configuration;

import com.portfolio.agent.infrastructure.model.ModelExecutionResolutionException;
import com.portfolio.agent.infrastructure.model.ModelExecutionResolver;
import com.portfolio.agent.infrastructure.model.provider.ModelRef;
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
        ConfiguredModelCatalog catalog = new ConfiguredModelCatalog(properties);

        assertThat(catalog.snapshot().getEntries()).isEmpty();
        assertThatThrownBy(() -> catalog.snapshot().getRequiredDescriptor(
                ModelRef.of("canary-model")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(catalog.getRequiredInternalDescriptor(ModelRef.of("canary-model"))
                .getModelName()).isEqualTo("canary-v1");
        assertThat(catalog.getRequiredBinding(ModelRef.of("canary-model"))
                .getModelName()).isEqualTo("canary-v1");

        ModelExecutionResolver turnResolver = new ModelExecutionResolver(
                catalog.snapshot(), catalog::getRequiredBinding);
        assertThatThrownBy(() -> turnResolver.resolve(
                AgentTurnCommand.ModelSelection.model("canary-model", "canary-v1")))
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
        settings.setSelectionVersion("canary-v1");
        settings.setEndpoint("https://example.test/chat");
        settings.setModel("canary-v1");
        settings.setApiKey("server-secret");
        settings.setProtocolProfile("ZHIPU_CHAT_COMPLETIONS");
        settings.setDataPolicyApproved(true);
        settings.setStructuredOutput("JSON_OBJECT");
        settings.setThinkingMode("DISABLED");
        settings.setStreaming(false);
        settings.setMaxContextTokens(32_000);
        settings.setMaxOutputTokens(2_000);

        ModelRuntimeProperties properties = new ModelRuntimeProperties();
        properties.setEnabled(true);
        properties.setDefaultModelRef("canary-model");
        properties.setModels(new LinkedHashMap<>(Map.of("canary-model", settings)));
        return properties;
    }
}
