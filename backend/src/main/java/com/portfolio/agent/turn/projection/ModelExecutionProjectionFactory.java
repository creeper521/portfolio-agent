package com.portfolio.agent.turn.projection;

import com.portfolio.agent.infrastructure.model.ModelExecutionSnapshot;
import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;

/**
 * 模型执行投影工厂：从冻结的执行快照只读地推导公众投影。
 *
 * <p>只投影不可变的选择信息与原子观测到的被采纳阶段，不读取也不暴露任何
 * 模型输入输出内容。</p>
 */
public final class ModelExecutionProjectionFactory {
    /**
     * 投影一次已解析的模型执行：按 GOAL_INTERPRETATION/ANSWER_GENERATION 两个
     * 阶段的采纳与尝试标记推导 {@code Participation}，NONE 快照直接投影为 NONE。
     */
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

    /** 只投影命令里的模型选择（未执行情形，Participation 恒为 NONE）。 */
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
