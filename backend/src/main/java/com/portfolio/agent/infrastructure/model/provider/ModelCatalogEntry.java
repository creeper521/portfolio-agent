package com.portfolio.agent.infrastructure.model.provider;

import java.util.Set;

/** Secret-free public catalog entry. */
public record ModelCatalogEntry(
        String modelRef,
        String displayName,
        int displayOrder,
        String selectionVersion,
        Set<ModelCapability> capabilities) {

    public ModelCatalogEntry {
        modelRef = requireText(modelRef, "modelRef");
        displayName = requireText(displayName, "displayName");
        selectionVersion = requireText(selectionVersion, "selectionVersion");
        capabilities = Set.copyOf(capabilities);
        if (capabilities.isEmpty()) {
            throw new IllegalArgumentException("capabilities must not be empty");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
