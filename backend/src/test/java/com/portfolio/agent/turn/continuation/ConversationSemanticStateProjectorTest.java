package com.portfolio.agent.turn.continuation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalRequestedOutput;
import com.portfolio.agent.turn.planning.GoalSubjectReference;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTaskParameters;
import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.planning.UserGoal;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import com.portfolio.agent.turn.projection.AnswerGoalResult;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.projection.PublicAnswer;
import com.portfolio.agent.turn.projection.PublicPresentation;
import com.portfolio.agent.turn.projection.PublicSection;
import com.portfolio.agent.turn.projection.PublicSourceCatalog;
import com.portfolio.agent.turn.projection.PublicSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationSemanticStateProjectorTest {

    @Test void projectsOnlyTypedGoalAndPublicSectionReferences() throws Exception {
        UserGoalProposal.PortfolioFactParameters parameters =
                new UserGoalProposal.PortfolioFactParameters(
                        Set.of(UserGoalProposal.Facet.SOLUTION),
                        UserGoalProposal.Depth.DETAILED);
        GoalSubjectReference subject = new GoalSubjectReference(
                GoalSubjectReference.Kind.PROJECT, "project-1",
                GoalSubjectReference.Basis.EXPLICIT_INPUT,
                new UserGoalProposal.InputAnchor("访客敏感原文", 0));
        UserGoal goal = new UserGoal(
                "goal-1", "服务端固定标签", GoalKind.PORTFOLIO_FACT,
                List.of(subject), Set.of(GoalRequestedOutput.SOLUTION),
                "task-goal-1");
        SemanticTask task = SemanticTask.of(
                "task-goal-1", SemanticTask.Type.PORTFOLIO_FACT,
                new SemanticTaskParameters(
                        GoalKind.PORTFOLIO_FACT, parameters, List.of(subject)),
                Set.of(GoalRequestedOutput.SOLUTION));
        SemanticTurnPlan plan = new SemanticTurnPlan(
                "public-1", List.of(goal), List.of(task), List.of());
        PublicSection section = new PublicSection(
                "section-goal-1-1", AnswerSectionType.SOLUTION,
                "模型标题敏感原文", "模型正文敏感原文",
                new PublicSupport(PublicSupport.Kind.GENERAL_KNOWLEDGE, List.of()));
        AnswerGoalResult result = new AnswerGoalResult(
                "goal-1", "服务端固定标签", AnswerGoalResult.Coverage.FULL,
                new PublicPresentation.Sectioned(List.of(section)), List.of());
        PublicAgentTurn turn = new PublicAgentTurn.Answer(
                UUID.randomUUID(), new PublicAnswer(
                PublicAnswer.Resolution.COMPLETE, "public-1", List.of(result),
                new PublicSourceCatalog(List.of()), List.of(), List.of(), null));

        ConversationSemanticState state = new ConversationSemanticStateProjector()
                .project(plan, turn, Instant.parse("2026-08-24T05:00:00Z"));

        assertThat(state.goals().getFirst().facets())
                .containsExactly(UserGoalProposal.Facet.SOLUTION);
        assertThat(state.goals().getFirst().depth())
                .isEqualTo(UserGoalProposal.Depth.DETAILED);
        assertThat(state.goals().getFirst().subjects().getFirst().reference())
                .isEqualTo("project-1");
        assertThat(state.goals().getFirst().sections().getFirst())
                .isEqualTo(new ConversationSemanticState.SectionReference(
                        "section-goal-1-1", AnswerSectionType.SOLUTION));
        String json = new ObjectMapper().findAndRegisterModules()
                .writeValueAsString(state);
        assertThat(json).doesNotContain(
                "访客敏感原文", "模型标题敏感原文", "模型正文敏感原文",
                "服务端固定标签");
    }
}
