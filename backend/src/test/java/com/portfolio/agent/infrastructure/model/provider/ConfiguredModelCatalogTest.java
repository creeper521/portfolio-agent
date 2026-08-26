package com.portfolio.agent.infrastructure.model.provider;

import com.portfolio.agent.infrastructure.model.ModelTransportBinding;
import com.portfolio.agent.infrastructure.model.configuration.ConfiguredModelCatalog;
import com.portfolio.agent.infrastructure.model.configuration.ModelRuntimeProperties;
import com.portfolio.agent.infrastructure.model.structured.StructuredModelTestFixtures;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicy;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicyRegistry;
import com.portfolio.agent.infrastructure.model.policy.OperationMode;
import com.portfolio.agent.turn.api.request.AgentTurnRequest;
import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguredModelCatalogTest {

    @Test
    void buildsAnOrderedSafeSnapshotAndClosedBindingsFromConfiguration() {
        ModelRuntimeProperties properties = runtime(true, "glm-4-7-flash",
                Map.of(
                        "qwen-3-7-flash", model(
                                "Qwen3.7-Flash", 20, "qwen-3-7-flash-v6",
                                "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                                "qwen3.7-flash", "DASHSCOPE_CHAT_COMPLETIONS", "qwen-secret"),
                        "glm-4-7-flash", model(
                                "GLM-4.7-Flash", 10, "glm-4-7-flash-v4",
                                "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                                "glm-4.7-flash", "ZHIPU_CHAT_COMPLETIONS", "glm-secret")));

        ConfiguredModelCatalog catalog = StructuredModelTestFixtures.catalog(properties);
        ModelCatalogSnapshot snapshot = catalog.snapshot();

        assertThat(snapshot.getEntries())
                .extracting(ModelCatalogEntry::modelRef)
                .containsExactly("glm-4-7-flash", "qwen-3-7-flash");
        assertThat(snapshot.getDefaultModelSelection().kind())
                .isEqualTo(ModelCatalogDefaultSelection.Kind.MODEL);
        assertThat(snapshot.getDefaultModelSelection().modelRef())
                .isEqualTo("glm-4-7-flash");
        assertThat(snapshot.getDefaultModelSelection().selectionVersion())
                .isEqualTo("glm-4-7-flash-v4");
        assertThat(snapshot.getSnapshotVersion()).matches("[0-9a-f]{64}");
        assertThat(snapshot.getRequiredDescriptor(ModelRef.of("glm-4-7-flash"))
                .getModelName()).isEqualTo("glm-4.7-flash");
        assertThat(catalog.getRequiredBinding(ModelRef.of("qwen-3-7-flash")))
                .extracting(ModelTransportBinding::getProtocolProfile)
                .isEqualTo(ModelProviderProtocolProfile.DASHSCOPE_CHAT_COMPLETIONS);

        assertThat(snapshot.toString()).doesNotContain("glm-secret", "qwen-secret");
        assertThat(ModelCatalogSnapshot.class.getDeclaredFields())
                .extracting(Field::getName)
                .noneMatch(name -> name.toLowerCase().contains("key")
                        || name.toLowerCase().contains("secret")
                        || name.toLowerCase().contains("credential"));
        assertThat(ModelProviderDescriptor.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("toString", "supports", "isApprovedConfiguration");
        assertThat(ModelTransportBinding.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("toString", "getApiKey", "getCredential");
        assertThat(catalog.getRequiredBinding(ModelRef.of("glm-4-7-flash")).toString())
                .doesNotContain("glm-secret");
    }

    @Test
    void disabledRuntimeProducesAnEmptyNoneSnapshotWithoutValidatingDisabledModels() {
        ModelRuntimeProperties properties = runtime(false, "missing",
                Map.of("INVALID REF", new ModelRuntimeProperties.ModelSettings()));

        ConfiguredModelCatalog catalog = StructuredModelTestFixtures.catalog(properties);

        assertThat(catalog.snapshot().getEntries()).isEmpty();
        assertThat(catalog.snapshot().getDefaultModelSelection().kind())
                .isEqualTo(ModelCatalogDefaultSelection.Kind.NONE);
    }

    @Test
    void unavailableConfiguredDefaultDoesNotSilentlySelectAnotherReadyModel() {
        ModelRuntimeProperties.ModelSettings unavailable = model(
                "GLM", 10, "glm-4-7-flash-v4", "", "glm-4.7-flash",
                "ZHIPU_CHAT_COMPLETIONS", "");
        unavailable.setEnabled(false);
        ModelRuntimeProperties properties = runtime(true, "glm-4-7-flash",
                Map.of(
                        "glm-4-7-flash", unavailable,
                        "qwen-3-7-flash", model(
                                "Qwen", 20, "qwen-3-7-flash-v6",
                                "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                                "qwen3.7-flash", "DASHSCOPE_CHAT_COMPLETIONS", "qwen-secret")));

        ModelCatalogSnapshot snapshot = StructuredModelTestFixtures.catalog(properties).snapshot();

        assertThat(snapshot.getEntries()).extracting(ModelCatalogEntry::modelRef)
                .containsExactly("qwen-3-7-flash");
        assertThat(snapshot.getDefaultModelSelection().kind())
                .isEqualTo(ModelCatalogDefaultSelection.Kind.NONE);
    }

    @Test
    void rejectsInvalidEnabledEntriesAndUnknownDefaultsFailClosed() {
        assertInvalid("INVALID_REF", model(
                "GLM", 10, "glm-4-7-flash-v4",
                "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                "glm-4.7-flash", "ZHIPU_CHAT_COMPLETIONS", "secret"), "model ref");
        assertInvalid("glm", model(
                "GLM", 10, "glm-4-7-flash-v4", "http://example.test/chat",
                "glm-4.7-flash", "ZHIPU_CHAT_COMPLETIONS", "secret"), "HTTPS");
        assertInvalid("glm", model(
                "GLM", 10, "glm-4-7-flash-v4", "https://example.test/chat",
                "glm-4.7-flash", "UNKNOWN_PROFILE", "secret"), "execution profile");

        ModelRuntimeProperties properties = runtime(true, "missing",
                Map.of("glm", model(
                        "GLM", 10, "glm-4-7-flash-v4", "https://example.test/chat",
                        "glm-4.7-flash", "ZHIPU_CHAT_COMPLETIONS", "secret")));
        assertThatThrownBy(() -> StructuredModelTestFixtures.catalog(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("default model ref");
    }

    @Test
    void secretRotationDoesNotChangePublicOrDescriptorVersions() {
        ModelRuntimeProperties first = runtime(true, "glm", Map.of("glm", model(
                "GLM", 10, "glm-4-7-flash-v4", "https://example.test/chat",
                "glm-4.7-flash", "ZHIPU_CHAT_COMPLETIONS", "first-secret")));
        ModelRuntimeProperties rotated = runtime(true, "glm", Map.of("glm", model(
                "GLM", 10, "glm-4-7-flash-v4", "https://example.test/chat",
                "glm-4.7-flash", "ZHIPU_CHAT_COMPLETIONS", "rotated-secret")));

        ModelCatalogSnapshot firstSnapshot = StructuredModelTestFixtures.catalog(first).snapshot();
        ModelCatalogSnapshot rotatedSnapshot = StructuredModelTestFixtures.catalog(rotated).snapshot();

        assertThat(rotatedSnapshot.getSnapshotVersion())
                .isEqualTo(firstSnapshot.getSnapshotVersion());
        assertThat(rotatedSnapshot.getRequiredDescriptor(ModelRef.of("glm"))
                .getDescriptorFingerprint())
                .isEqualTo(firstSnapshot.getRequiredDescriptor(ModelRef.of("glm"))
                        .getDescriptorFingerprint());
    }

    @Test
    void everyPublicCatalogSelectionRoundTripsIntoTheTurnContract() {
        ModelRuntimeProperties properties = runtime(true, "glm-4-7-flash",
                Map.of("glm-4-7-flash", model(
                        "GLM", 10, "glm-4-7-flash-v4",
                        "https://example.test/chat", "glm-4.7-flash",
                        "ZHIPU_CHAT_COMPLETIONS", "secret")));

        ModelCatalogEntry entry = StructuredModelTestFixtures.catalog(properties)
                .snapshot().getEntries().getFirst();
        AgentTurnRequest.ModelModelSelectionRequest request =
                new AgentTurnRequest.ModelModelSelectionRequest(
                        entry.modelRef(), entry.selectionVersion());
        AgentTurnCommand.ModelSelection command = AgentTurnCommand.ModelSelection.model(
                request.getModelRef(), request.getSelectionVersion());

        assertThat(command.getModelRef()).contains(entry.modelRef());
        assertThat(command.getSelectionVersion()).contains(entry.selectionVersion());
    }

    @Test
    void configuredSelectionIdentityUsesExactlyTheClosedTurnShape() {
        assertInvalid("double--dash", model(
                "GLM", 10, "glm-4-7-flash-v4", "https://example.test/chat",
                "glm-4.7-flash", "ZHIPU_CHAT_COMPLETIONS", "secret"), "model ref");
        assertInvalid("glm", model(
                "GLM", 10, ".leading", "https://example.test/chat",
                "glm-4.7-flash", "ZHIPU_CHAT_COMPLETIONS", "secret"), "selectionVersion");
        assertInvalid("glm", model(
                "GLM", 10, "x".repeat(129), "https://example.test/chat",
                "glm-4.7-flash", "ZHIPU_CHAT_COMPLETIONS", "secret"), "selectionVersion");
    }

    @Test
    void rejectsEndpointQueryCredentialsWithoutReflectingThem() {
        ModelRuntimeProperties properties = runtime(true, "glm", Map.of("glm", model(
                "GLM", 10, "glm-4-7-flash-v4",
                "https://example.test/chat?api_key=query-secret",
                "glm-4.7-flash", "ZHIPU_CHAT_COMPLETIONS", "header-secret")));

        assertThatThrownBy(() -> StructuredModelTestFixtures.catalog(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpoint")
                .hasMessageNotContaining("query-secret")
                .hasMessageNotContaining("api_key");
    }

    @Test
    void disabledGeneralOperationShrinksThePublicCapabilitySet() {
        ModelRuntimeProperties properties = runtime(true, "qwen-3-7-flash",
                Map.of("qwen-3-7-flash", model(
                        "Qwen", 20, "qwen-3-7-flash-v6",
                        "https://example.test/chat", "qwen3.7-flash",
                        "DASHSCOPE_CHAT_COMPLETIONS", "secret")));

        ConfiguredModelCatalog catalog = new ConfiguredModelCatalog(
                properties, policies(OperationMode.ENABLED, OperationMode.DISABLED),
                StructuredModelTestFixtures.contracts());

        assertThat(catalog.snapshot().getEntries()).singleElement().satisfies(entry ->
                assertThat(entry.capabilities()).containsExactly(
                        ModelCapability.TURN_INTERPRETATION));
        assertThat(catalog.getRequiredBinding(ModelRef.of("qwen-3-7-flash"))
                .getOperationBindings()).containsOnlyKeys(
                        ModelOperation.TURN_INTERPRETATION);
    }

    @Test
    void selectableModelWithoutTurnInterpretationBindingFailsAtStartup() {
        ModelRuntimeProperties properties = runtime(true, "qwen-3-7-flash",
                Map.of("qwen-3-7-flash", model(
                        "Qwen", 20, "qwen-3-7-flash-v6",
                        "https://example.test/chat", "qwen3.7-flash",
                        "DASHSCOPE_CHAT_COMPLETIONS", "secret")));

        assertThatThrownBy(() -> new ConfiguredModelCatalog(
                properties, policies(OperationMode.DISABLED, OperationMode.ENABLED),
                StructuredModelTestFixtures.contracts()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selectable model requires turn interpretation binding");
    }

    private void assertInvalid(
            String ref, ModelRuntimeProperties.ModelSettings settings, String message) {
        ModelRuntimeProperties properties = runtime(true, ref, Map.of(ref, settings));
        assertThatThrownBy(() -> StructuredModelTestFixtures.catalog(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
    }

    private ModelRuntimeProperties runtime(
            boolean enabled, String defaultRef,
            Map<String, ModelRuntimeProperties.ModelSettings> models) {
        ModelRuntimeProperties properties = new ModelRuntimeProperties();
        properties.setEnabled(enabled);
        properties.setDefaultModelRef(defaultRef);
        properties.setModels(new LinkedHashMap<>(models));
        return properties;
    }

    private ModelRuntimeProperties.ModelSettings model(
            String displayName, int order, String selectionVersion,
            String endpoint, String model, String profile, String apiKey) {
        ModelRuntimeProperties.ModelSettings settings =
                new ModelRuntimeProperties.ModelSettings();
        settings.setEnabled(true);
        settings.setSelectable(true);
        settings.setDisplayName(displayName);
        settings.setDisplayOrder(order);
        settings.setSelectionVersion(selectionVersion);
        settings.setEndpoint(endpoint);
        settings.setModel(model);
        settings.setApiKey(apiKey);
        settings.setDataPolicyApproved(true);
        settings.setExecutionProfile(switch (profile) {
            case "DASHSCOPE_CHAT_COMPLETIONS" -> "QWEN_3_7_FLASH_STRUCTURED_V6";
            case "ZHIPU_CHAT_COMPLETIONS" -> "GLM_4_7_FLASH_STRUCTURED_V4";
            default -> "UNKNOWN_EXECUTION_PROFILE";
        });
        settings.setMaxContextTokens(200_000);
        settings.setMaxOutputTokens(8_000);
        return settings;
    }

    private ModelOperationPolicyRegistry policies(
            OperationMode turnMode, OperationMode generalMode) {
        return new ModelOperationPolicyRegistry(Map.of(
                ModelOperation.TURN_INTERPRETATION,
                new ModelOperationPolicy(
                        ModelOperation.TURN_INTERPRETATION, turnMode,
                        "goal.proposal.v5", 1600, Duration.ofSeconds(10)),
                ModelOperation.GENERAL_KNOWLEDGE,
                new ModelOperationPolicy(
                        ModelOperation.GENERAL_KNOWLEDGE, generalMode,
                        "general.draft.v2", 1200, Duration.ofSeconds(10))));
    }
}
