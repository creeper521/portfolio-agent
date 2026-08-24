package com.portfolio.agent.infrastructure.model;

import com.portfolio.agent.infrastructure.model.provider.ModelCatalogSnapshot;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderDescriptor;
import com.portfolio.agent.infrastructure.model.provider.ModelRef;
import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;

import java.util.Objects;

/** Resolves an explicit request selection against one frozen startup catalog. */
public final class ModelExecutionResolver {
    private final ModelCatalogSnapshot catalog;
    private final BindingSource bindings;

    public ModelExecutionResolver(
            ModelCatalogSnapshot catalog,
            BindingSource bindings) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
    }

    public ResolvedModelExecution resolve(
            AgentTurnCommand.ModelSelection selection) {
        AgentTurnCommand.ModelSelection required = Objects.requireNonNull(
                selection, "selection");
        if (required.getKind() == AgentTurnCommand.ModelSelectionKind.NONE) {
            return ResolvedModelExecution.none();
        }

        ModelRef modelRef = ModelRef.of(required.getModelRef().orElseThrow());
        ModelProviderDescriptor descriptor;
        try {
            descriptor = catalog.getRequiredDescriptor(modelRef);
        } catch (IllegalArgumentException unavailable) {
            throw new ModelExecutionResolutionException(
                    ModelExecutionResolutionException.Code.SELECTED_MODEL_UNAVAILABLE);
        }
        ModelTransportBinding binding;
        try {
            binding = bindings.getRequiredBinding(modelRef);
        } catch (IllegalArgumentException unavailable) {
            throw new ModelExecutionResolutionException(
                    ModelExecutionResolutionException.Code.SELECTED_MODEL_UNAVAILABLE);
        }
        if (!descriptor.getSelectionVersion().equals(
                required.getSelectionVersion().orElseThrow())) {
            throw new ModelExecutionResolutionException(
                    ModelExecutionResolutionException.Code.MODEL_SELECTION_STALE);
        }
        return ResolvedModelExecution.model(
                ModelExecutionSnapshot.model(descriptor), binding);
    }

    @FunctionalInterface
    public interface BindingSource {
        ModelTransportBinding getRequiredBinding(ModelRef modelRef);
    }
}
