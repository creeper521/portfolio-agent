package com.portfolio.agent.turn.projection;

import com.portfolio.agent.infrastructure.model.ModelExecutionSnapshot;
import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;

/** Projects only the immutable selection and atomically observed adopted stages. */
public final class ModelExecutionProjectionFactory {
    public ModelExecutionProjection project(ResolvedModelExecution execution) {
        ResolvedModelExecution required = java.util.Objects.requireNonNull(
                execution, "execution");
        ModelExecutionSnapshot snapshot = required.getSnapshot();
        if (snapshot.getKind() == ModelExecutionSnapshot.Kind.NONE) {
            return ModelExecutionProjection.none();
        }
        boolean goal = required.wasAdopted(
                ResolvedModelExecution.Stage.GOAL_INTERPRETATION);
        boolean answer = required.wasAdopted(
                ResolvedModelExecution.Stage.ANSWER_GENERATION);
        boolean attempted = required.wasAttempted(
                ResolvedModelExecution.Stage.GOAL_INTERPRETATION)
                || required.wasAttempted(
                ResolvedModelExecution.Stage.ANSWER_GENERATION);
        ModelExecutionProjection.Participation participation;
        if (goal && answer) {
            participation = ModelExecutionProjection.Participation.GOAL_AND_ANSWER;
        } else if (goal) {
            participation = ModelExecutionProjection.Participation.GOAL_INTERPRETATION_ONLY;
        } else if (answer) {
            participation = ModelExecutionProjection.Participation.ANSWER_GENERATION;
        } else if (attempted) {
            participation = ModelExecutionProjection.Participation.ATTEMPTED_UNAVAILABLE;
        } else {
            participation = ModelExecutionProjection.Participation.NONE;
        }
        return ModelExecutionProjection.model(
                snapshot.getModelRef().orElseThrow().value(),
                snapshot.getSelectionVersion().orElseThrow(), participation);
    }

    public ModelExecutionProjection selectionOnly(
            AgentTurnCommand.ModelSelection selection) {
        AgentTurnCommand.ModelSelection required = java.util.Objects.requireNonNull(
                selection, "selection");
        if (required.getKind() == AgentTurnCommand.ModelSelectionKind.NONE) {
            return ModelExecutionProjection.none();
        }
        return ModelExecutionProjection.model(
                required.getModelRef().orElseThrow(),
                required.getSelectionVersion().orElseThrow(),
                ModelExecutionProjection.Participation.NONE);
    }
}
