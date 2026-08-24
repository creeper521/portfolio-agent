package com.portfolio.agent.infrastructure.model.configuration;

import com.portfolio.agent.infrastructure.model.ModelTransportBinding;
import com.portfolio.agent.infrastructure.model.provider.ModelCapability;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogDefaultSelection;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogEntry;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogSnapshot;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderDescriptor;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderProtocolProfile;
import com.portfolio.agent.infrastructure.model.provider.ModelRef;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Builds a frozen catalog and server-only bindings from startup configuration. */
public final class ConfiguredModelCatalog {
    private static final Set<ModelCapability> CAPABILITIES = Set.of(
            ModelCapability.TURN_INTERPRETATION,
            ModelCapability.GENERAL_KNOWLEDGE);

    private final ModelCatalogSnapshot snapshot;
    private final Map<ModelRef, ModelProviderDescriptor> internalDescriptors;
    private final Map<ModelRef, ModelTransportBinding> bindings;

    public ConfiguredModelCatalog(ModelRuntimeProperties properties) {
        Objects.requireNonNull(properties, "properties");
        if (!properties.isEnabled()) {
            snapshot = ModelCatalogSnapshot.empty();
            internalDescriptors = Map.of();
            bindings = Map.of();
            return;
        }

        Map<String, ModelRuntimeProperties.ModelSettings> configured =
                properties.getModels();
        ModelRef defaultRef = configuredDefault(properties.getDefaultModelRef(), configured);
        List<ConfiguredEntry> selectable = new ArrayList<>();
        LinkedHashMap<ModelRef, ModelProviderDescriptor> internalByRef =
                new LinkedHashMap<>();
        LinkedHashMap<ModelRef, ModelTransportBinding> bindingByRef =
                new LinkedHashMap<>();
        for (Map.Entry<String, ModelRuntimeProperties.ModelSettings> entry
                : configured.entrySet()) {
            ModelRef ref = ModelRef.of(entry.getKey());
            ModelRuntimeProperties.ModelSettings settings =
                    Objects.requireNonNull(entry.getValue(), "model settings");
            if (!settings.isEnabled()) {
                continue;
            }
            ModelProviderDescriptor descriptor = descriptor(ref, settings);
            if (!settings.isDataPolicyApproved()
                    || isBlank(settings.getApiKey())) {
                continue;
            }
            ModelTransportBinding binding = new ModelTransportBinding(
                    ref, descriptor.getEndpoint(), descriptor.getModelName(),
                    descriptor.getProtocolProfile(), settings.getApiKey(),
                    descriptor.getMaxOutputTokens());
            internalByRef.put(ref, descriptor);
            bindingByRef.put(ref, binding);
            if (settings.isSelectable()) {
                selectable.add(new ConfiguredEntry(descriptor, binding));
            }
        }
        selectable.sort(Comparator
                .comparingInt((ConfiguredEntry entry) ->
                        entry.descriptor().getDisplayOrder())
                .thenComparing(entry -> entry.descriptor().getModelRef()));

        List<ModelProviderDescriptor> descriptors = selectable.stream()
                .map(ConfiguredEntry::descriptor)
                .toList();
        internalDescriptors = Map.copyOf(internalByRef);
        bindings = Map.copyOf(bindingByRef);

        ModelProviderDescriptor defaultDescriptor = descriptors.stream()
                .filter(descriptor -> descriptor.getModelRef().equals(defaultRef))
                .findFirst().orElse(null);
        ModelCatalogDefaultSelection defaultSelection = defaultDescriptor == null
                ? ModelCatalogDefaultSelection.none()
                : ModelCatalogDefaultSelection.model(defaultDescriptor);
        snapshot = new ModelCatalogSnapshot(
                snapshotVersion(descriptors, defaultSelection),
                descriptors, defaultSelection);
    }

    public ModelCatalogSnapshot snapshot() {
        return snapshot;
    }

    public ModelTransportBinding getRequiredBinding(ModelRef modelRef) {
        ModelTransportBinding binding = bindings.get(
                Objects.requireNonNull(modelRef, "modelRef"));
        if (binding == null) {
            throw new IllegalArgumentException(
                    "model transport is not configured: " + modelRef.value());
        }
        return binding;
    }

    ModelProviderDescriptor getRequiredInternalDescriptor(ModelRef modelRef) {
        ModelProviderDescriptor descriptor = internalDescriptors.get(
                Objects.requireNonNull(modelRef, "modelRef"));
        if (descriptor == null) {
            throw new IllegalArgumentException(
                    "model descriptor is not available for internal execution: "
                            + modelRef.value());
        }
        return descriptor;
    }

    private ModelRef configuredDefault(
            String configuredDefault,
            Map<String, ModelRuntimeProperties.ModelSettings> configured) {
        if (isBlank(configuredDefault)) {
            return null;
        }
        ModelRef defaultRef = ModelRef.of(configuredDefault);
        if (!configured.containsKey(defaultRef.value())) {
            throw new IllegalArgumentException("default model ref is not configured");
        }
        return defaultRef;
    }

    private ModelProviderDescriptor descriptor(
            ModelRef ref, ModelRuntimeProperties.ModelSettings settings) {
        requireExact(settings.getStructuredOutput(), "JSON_OBJECT", "structured output");
        requireExact(settings.getThinkingMode(), "DISABLED", "thinking mode");
        if (settings.isStreaming()) {
            throw new IllegalArgumentException("streaming must be disabled");
        }
        ModelProviderProtocolProfile profile =
                ModelProviderProtocolProfile.fromConfiguredName(
                        settings.getProtocolProfile());
        URI endpoint;
        try {
            endpoint = URI.create(requireText(settings.getEndpoint(), "endpoint"));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(
                    "endpoint must be an HTTPS URI with a host", failure);
        }
        return new ModelProviderDescriptor(
                ref,
                requireText(settings.getSelectionVersion(), "selectionVersion"),
                requireText(settings.getDisplayName(), "displayName"),
                settings.getDisplayOrder(),
                endpoint,
                requireText(settings.getModel(), "model"),
                profile,
                CAPABILITIES,
                settings.getMaxContextTokens(),
                settings.getMaxOutputTokens());
    }

    private String snapshotVersion(
            List<ModelProviderDescriptor> descriptors,
            ModelCatalogDefaultSelection defaultSelection) {
        List<String> values = new ArrayList<>();
        values.add(defaultSelection.kind().name());
        values.add(defaultSelection.modelRef() == null ? "" : defaultSelection.modelRef());
        values.add(defaultSelection.selectionVersion() == null
                ? "" : defaultSelection.selectionVersion());
        for (ModelProviderDescriptor descriptor : descriptors) {
            ModelCatalogEntry entry = descriptor.publicEntry();
            values.add(entry.modelRef());
            values.add(entry.displayName());
            values.add(Integer.toString(entry.displayOrder()));
            values.add(entry.selectionVersion());
            values.add(ModelProviderDescriptor.canonicalCapabilities(
                    entry.capabilities()));
        }
        return ModelProviderDescriptor.fingerprint(values.toArray(String[]::new));
    }

    private String requireText(String value, String name) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.strip();
    }

    private void requireExact(String value, String expected, String name) {
        if (!expected.equals(value)) {
            throw new IllegalArgumentException(name + " must be " + expected);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ConfiguredEntry(
            ModelProviderDescriptor descriptor,
            ModelTransportBinding binding) { }
}
