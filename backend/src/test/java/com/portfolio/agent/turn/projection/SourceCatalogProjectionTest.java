package com.portfolio.agent.turn.projection;

import com.portfolio.agent.turn.execution.GoalCoverage;
import com.portfolio.agent.turn.execution.SemanticTurnOutcome;
import com.portfolio.agent.turn.execution.TaskArtifact;
import com.portfolio.agent.turn.execution.TaskOutcome;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalRequestedOutput;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTaskParameters;
import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.planning.UserGoal;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SourceCatalogProjectionTest {
    @Test void repeatedSectionReferencesBecomeOneCatalogEntry() {
        UserGoalProposal.PortfolioFactParameters parameters =
                new UserGoalProposal.PortfolioFactParameters(
                Set.of(UserGoalProposal.Facet.SOLUTION));
        SemanticTask task = SemanticTask.of(
                "task-portfolio", SemanticTask.Type.PORTFOLIO_FACT,
                new SemanticTaskParameters(GoalKind.PORTFOLIO_FACT, parameters, List.of()),
                Set.of(GoalRequestedOutput.SOLUTION));
        SemanticTurnPlan plan = new SemanticTurnPlan(
                "public-1", List.of(new UserGoal(
                "goal-portfolio", "介绍方案", GoalKind.PORTFOLIO_FACT,
                List.of(), Set.of(GoalRequestedOutput.SOLUTION), task.getTaskId())),
                List.of(task), List.of());
        ValidatedEvidenceUnit unit = ProjectionTestFixtures.unit("project-a", "E-01", "实现说明");
        TaskArtifact artifact = ProjectionTestFixtures.portfolioFactArtifact(List.of(unit, unit));
        SemanticTurnOutcome outcome = new SemanticTurnOutcome(
                List.of(new TaskOutcome(task.getTaskId(),
                        new TaskOutcome.Produced(artifact, TaskOutcome.Fulfillment.FULL))),
                List.of(new GoalCoverage("goal-portfolio", GoalCoverage.Coverage.FULL)));
        PublicAnswer answer = new PublicAgentTurnProjector().project(
                UUID.randomUUID(), plan, outcome).getAnswer();
        assertThat(answer.getSourceCatalog().getSources()).hasSize(1);
        assertThat(answer.getSourceCatalog().getSources().getFirst().getRoute())
                .isEqualTo("/evidence/project-a");
    }
}
