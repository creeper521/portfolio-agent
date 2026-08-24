package com.portfolio.agent.infrastructure.model.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, credential-free model catalog snapshot. */
public final class ModelCatalogSnapshot {
    private final String snapshotVersion;
    private final Map<ModelRef, ModelProviderDescriptor> descriptors;
    private final List<ModelCatalogEntry> entries;
    private final ModelCatalogDefaultSelection defaultModelSelection;

    public ModelCatalogSnapshot(
            String snapshotVersion,
            List<ModelProviderDescriptor> descriptorList,
            ModelCatalogDefaultSelection defaultModelSelection) {
        if (snapshotVersion == null || snapshotVersion.isBlank()) {
            throw new IllegalArgumentException("snapshotVersion is required");
        }
        this.snapshotVersion = snapshotVersion;
        LinkedHashMap<ModelRef, ModelProviderDescriptor> byRef = new LinkedHashMap<>();
        for (ModelProviderDescriptor descriptor : descriptorList) {
            ModelProviderDescriptor required = Objects.requireNonNull(descriptor, "descriptor");
            if (byRef.putIfAbsent(required.getModelRef(), required) != null) {
                throw new IllegalArgumentException(
                        "duplicate model ref: " + required.getModelRef().value());
            }
        }
        descriptors = Map.copyOf(byRef);
        entries = descriptorList.stream()
                .map(ModelProviderDescriptor::publicEntry)
                .toList();
        this.defaultModelSelection = Objects.requireNonNull(
                defaultModelSelection, "defaultModelSelection");
    }

    public static ModelCatalogSnapshot empty() {
        return new ModelCatalogSnapshot(
                ModelProviderDescriptor.fingerprint("empty-model-catalog"),
                List.of(), ModelCatalogDefaultSelection.none());
    }

    public String getSnapshotVersion() { return snapshotVersion; }
    public List<ModelCatalogEntry> getEntries() { return entries; }
    public ModelCatalogDefaultSelection getDefaultModelSelection() {
        return defaultModelSelection;
    }

    public ModelProviderDescriptor getRequiredDescriptor(ModelRef modelRef) {
        ModelProviderDescriptor descriptor = descriptors.get(
                Objects.requireNonNull(modelRef, "modelRef"));
        if (descriptor == null) {
            throw new IllegalArgumentException(
                    "model is not configured: " + modelRef.value());
        }
        return descriptor;
    }
}
