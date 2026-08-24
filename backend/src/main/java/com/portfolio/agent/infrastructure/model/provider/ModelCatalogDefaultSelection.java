package com.portfolio.agent.infrastructure.model.provider;

/** Safe default selection projected with the catalog. */
public record ModelCatalogDefaultSelection(
        Kind kind, String modelRef, String selectionVersion) {

    public enum Kind {
        MODEL,
        NONE
    }

    public static ModelCatalogDefaultSelection none() {
        return new ModelCatalogDefaultSelection(Kind.NONE, null, null);
    }

    public static ModelCatalogDefaultSelection model(ModelProviderDescriptor descriptor) {
        return new ModelCatalogDefaultSelection(
                Kind.MODEL,
                descriptor.getModelRef().value(),
                descriptor.getSelectionVersion());
    }

    public ModelCatalogDefaultSelection {
        if (kind == null) {
            throw new IllegalArgumentException("selection kind is required");
        }
        if (kind == Kind.MODEL
                && (modelRef == null || modelRef.isBlank()
                || selectionVersion == null || selectionVersion.isBlank())) {
            throw new IllegalArgumentException("model default is incomplete");
        }
        if (kind == Kind.NONE && (modelRef != null || selectionVersion != null)) {
            throw new IllegalArgumentException("NONE default must not carry model fields");
        }
    }
}
