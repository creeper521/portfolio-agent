package com.portfolio.agent.infrastructure.model;

/** Server-only carrier pairing a credential-free snapshot with its Binding. */
public final class ResolvedModelExecution {
    private final ModelExecutionSnapshot snapshot;
    private final ModelTransportBinding binding;
    private final java.util.concurrent.atomic.AtomicBoolean goalAttempted =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean goalAdopted =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean answerAttempted =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean answerAdopted =
            new java.util.concurrent.atomic.AtomicBoolean();

    private ResolvedModelExecution(
            ModelExecutionSnapshot snapshot,
            ModelTransportBinding binding) {
        this.snapshot = java.util.Objects.requireNonNull(snapshot, "snapshot");
        this.binding = binding;
        if ((snapshot.getKind() == ModelExecutionSnapshot.Kind.MODEL)
                != (binding != null)) {
            throw new IllegalArgumentException(
                    "model execution snapshot and binding must agree");
        }
        if (binding != null && !snapshot.getModelRef().orElseThrow()
                .equals(binding.getModelRef())) {
            throw new IllegalArgumentException(
                    "model execution snapshot and binding must identify the same model");
        }
    }

    public static ResolvedModelExecution none() {
        return new ResolvedModelExecution(ModelExecutionSnapshot.none(), null);
    }

    public static ResolvedModelExecution model(
            ModelExecutionSnapshot snapshot,
            ModelTransportBinding binding) {
        return new ResolvedModelExecution(snapshot, binding);
    }

    public ModelExecutionSnapshot getSnapshot() {
        return snapshot;
    }

    public ModelTransportBinding getRequiredBinding() {
        if (binding == null) {
            throw new IllegalStateException("NONE model execution has no transport binding");
        }
        return binding;
    }

    public void markAttempted(Stage stage) {
        attempted(stage).set(true);
    }

    public void markAdopted(Stage stage) {
        attempted(stage).set(true);
        adopted(stage).set(true);
    }

    public boolean wasAttempted(Stage stage) {
        return attempted(stage).get();
    }

    public boolean wasAdopted(Stage stage) {
        return adopted(stage).get();
    }

    private java.util.concurrent.atomic.AtomicBoolean attempted(Stage stage) {
        return switch (java.util.Objects.requireNonNull(stage, "stage")) {
            case GOAL_INTERPRETATION -> goalAttempted;
            case ANSWER_GENERATION -> answerAttempted;
        };
    }

    private java.util.concurrent.atomic.AtomicBoolean adopted(Stage stage) {
        return switch (java.util.Objects.requireNonNull(stage, "stage")) {
            case GOAL_INTERPRETATION -> goalAdopted;
            case ANSWER_GENERATION -> answerAdopted;
        };
    }

    @Override
    public String toString() {
        return "ResolvedModelExecution{" + snapshot + '}';
    }

    public enum Stage {
        GOAL_INTERPRETATION,
        ANSWER_GENERATION
    }
}
