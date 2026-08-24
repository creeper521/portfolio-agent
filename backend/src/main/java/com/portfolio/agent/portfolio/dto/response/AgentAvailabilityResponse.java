package com.portfolio.agent.portfolio.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogDefaultSelection;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogEntry;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogSnapshot;

import java.util.List;
import java.util.Objects;

/**
 * Public deployment and credential-free selectable-model projection.
 */
public final class AgentAvailabilityResponse {
    public enum Status { AVAILABLE, UNAVAILABLE }
    public enum FreeTextSemanticRouting { AVAILABLE, DISABLED }
    public enum SelectionKind { MODEL, NONE }

    private final Status status;
    private final FreeTextSemanticRouting freeTextSemanticRouting;
    private final String modelCatalogVersion;
    private final DefaultModelSelection defaultModelSelection;
    private final List<SelectableModel> selectableModels;

    private AgentAvailabilityResponse(
            Status status,
            FreeTextSemanticRouting freeTextSemanticRouting,
            ModelCatalogSnapshot catalog) {
        this.status = Objects.requireNonNull(status, "status");
        Objects.requireNonNull(freeTextSemanticRouting, "freeTextSemanticRouting");
        Objects.requireNonNull(catalog, "catalog");
        this.modelCatalogVersion = requireText(
                catalog.getSnapshotVersion(), "modelCatalogVersion");
        this.defaultModelSelection = DefaultModelSelection.from(
                catalog.getDefaultModelSelection());
        this.selectableModels = catalog.getEntries().stream()
                .map(SelectableModel::from)
                .toList();
        this.freeTextSemanticRouting = status == Status.UNAVAILABLE
                || selectableModels.isEmpty()
                ? FreeTextSemanticRouting.DISABLED
                : freeTextSemanticRouting;
    }

    public Status getStatus() {
        return status;
    }

    public FreeTextSemanticRouting getFreeTextSemanticRouting() {
        return freeTextSemanticRouting;
    }

    public String getModelCatalogVersion() { return modelCatalogVersion; }
    public DefaultModelSelection getDefaultModelSelection() {
        return defaultModelSelection;
    }
    public List<SelectableModel> getSelectableModels() { return selectableModels; }

    public static AgentAvailabilityResponse available() {
        return available(
                FreeTextSemanticRouting.DISABLED, ModelCatalogSnapshot.empty());
    }

    public static AgentAvailabilityResponse available(
            FreeTextSemanticRouting freeTextSemanticRouting,
            ModelCatalogSnapshot catalog) {
        return new AgentAvailabilityResponse(
                Status.AVAILABLE, freeTextSemanticRouting, catalog);
    }

    public static AgentAvailabilityResponse unavailable(
            ModelCatalogSnapshot catalog) {
        return new AgentAvailabilityResponse(
                Status.UNAVAILABLE, FreeTextSemanticRouting.DISABLED, catalog);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof AgentAvailabilityResponse that
                && status == that.status
                && freeTextSemanticRouting
                == that.freeTextSemanticRouting
                && modelCatalogVersion.equals(that.modelCatalogVersion)
                && defaultModelSelection.equals(that.defaultModelSelection)
                && selectableModels.equals(that.selectableModels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                status, freeTextSemanticRouting, modelCatalogVersion,
                defaultModelSelection, selectableModels);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class DefaultModelSelection {
        private final SelectionKind kind;
        private final String modelRef;
        private final String selectionVersion;

        private DefaultModelSelection(
                SelectionKind kind, String modelRef, String selectionVersion) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.modelRef = modelRef;
            this.selectionVersion = selectionVersion;
        }

        private static DefaultModelSelection from(
                ModelCatalogDefaultSelection selection) {
            Objects.requireNonNull(selection, "defaultModelSelection");
            return new DefaultModelSelection(
                    SelectionKind.valueOf(selection.kind().name()),
                    selection.modelRef(), selection.selectionVersion());
        }

        public SelectionKind getKind() { return kind; }
        public String getModelRef() { return modelRef; }
        public String getSelectionVersion() { return selectionVersion; }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof DefaultModelSelection that
                    && kind == that.kind
                    && Objects.equals(modelRef, that.modelRef)
                    && Objects.equals(selectionVersion, that.selectionVersion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, modelRef, selectionVersion);
        }
    }

    public static final class SelectableModel {
        private final String modelRef;
        private final String selectionVersion;
        private final String displayName;

        private SelectableModel(
                String modelRef, String selectionVersion, String displayName) {
            this.modelRef = requireText(modelRef, "modelRef");
            this.selectionVersion = requireText(
                    selectionVersion, "selectionVersion");
            this.displayName = requireText(displayName, "displayName");
        }

        private static SelectableModel from(ModelCatalogEntry entry) {
            Objects.requireNonNull(entry, "catalog entry");
            return new SelectableModel(
                    entry.modelRef(), entry.selectionVersion(), entry.displayName());
        }

        public String getModelRef() { return modelRef; }
        public String getSelectionVersion() { return selectionVersion; }
        public String getDisplayName() { return displayName; }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof SelectableModel that
                    && modelRef.equals(that.modelRef)
                    && selectionVersion.equals(that.selectionVersion)
                    && displayName.equals(that.displayName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(modelRef, selectionVersion, displayName);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
