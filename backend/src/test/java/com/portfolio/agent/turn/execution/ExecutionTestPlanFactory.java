package com.portfolio.agent.turn.execution;

import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.SemanticPlanValidator;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTaskParameters;
import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.planning.UserGoal;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import com.portfolio.agent.turn.planning.ValidatedSemanticTurnPlan;

import java.util.List;
import java.util.Set;

final class ExecutionTestPlanFactory {
    private ExecutionTestPlanFactory() { }

    static ValidatedSemanticTurnPlan oneGeneralTask() {
        SemanticTask task = generalTask("task-one", "topic");
        SemanticTurnPlan plan = new SemanticTurnPlan(
                "public-1",
                List.of(new UserGoal("goal-one", "goal", GoalKind.GENERAL_EXPLANATION,
                        List.of(), Set.of(), task.getTaskId())),
                List.of(task), List.of());
        return new SemanticPlanValidator().validate(plan);
    }

    static ValidatedSemanticTurnPlan twoGeneralTasks() {
        SemanticTask first = generalTask("task-first", "first");
        SemanticTask second = generalTask("task-second", "second");
        SemanticTurnPlan plan = new SemanticTurnPlan(
                "public-1",
                List.of(
                        new UserGoal("goal-first", "first", GoalKind.GENERAL_EXPLANATION,
                                List.of(), Set.of(), first.getTaskId()),
                        new UserGoal("goal-second", "second", GoalKind.GENERAL_EXPLANATION,
                                List.of(), Set.of(), second.getTaskId())),
                List.of(first, second), List.of());
        return new SemanticPlanValidator().validate(plan);
    }

    static SemanticTask generalTask(String id, String text) {
        UserGoalProposal.InputAnchor anchor = new UserGoalProposal.InputAnchor(text, 0);
        return SemanticTask.of(id, SemanticTask.Type.GENERAL_EXPLANATION,
                new SemanticTaskParameters(GoalKind.GENERAL_EXPLANATION,
                        new UserGoalProposal.GeneralExplanationParameters(
                                anchor, UserGoalProposal.Depth.STANDARD), List.of()));
    }

    static TaskArtifact artifact() {
        return new TaskArtifact(new Result(), new Presentation(), TaskProvenance.none());
    }

    private static final class Result implements TaskSemanticResult { }
    private static final class Presentation implements TaskPresentation { }
}
