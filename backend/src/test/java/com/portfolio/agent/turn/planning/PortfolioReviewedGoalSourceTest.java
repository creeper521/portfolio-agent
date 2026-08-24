package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerKnowledge;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerQuestion;
import com.portfolio.agent.turn.capability.portfolio.knowledge.RuntimeAnswerContent;
import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;
import com.portfolio.agent.turn.lifecycle.ConversationWindow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioReviewedGoalSourceTest {

    @Test
    void presetOutputsAreDerivedFromReviewedFacetIntent() {
        AnswerQuestion question = new AnswerQuestion(
                "preset-verification", "验证项目", List.of(), "验证项目",
                List.of(AnswerClaimCategory.VERIFICATION),
                "pcv1-0123456789abcdef", List.of(), List.of(), 1, true);
        AnswerKnowledge project = new AnswerKnowledge(
                "project-a", "项目 A", "摘要", "背景", List.of(), "方案",
                List.of(), List.of(), "结果", "交接", "完成",
                List.of(question), List.of(), List.of());
        PortfolioReviewedGoalSource source = new PortfolioReviewedGoalSource(() ->
                new RuntimeAnswerContent("public-1", "hash-1", List.of(project)));
        AgentTurnCommand command = new AgentTurnCommand.Ask(
                UUID.randomUUID(),
                new AgentTurnCommand.Preset(
                        "preset-verification", "pcv1-0123456789abcdef"),
                AgentTurnCommand.SurfaceContext.empty(), ConversationWindow.empty());

        UserGoalProposal.ProposedGoal goal = source.resolve(command).getGoals().getFirst();

        assertThat(goal.getRequestedOutputs())
                .containsExactly(GoalRequestedOutput.VERIFICATION);
        UserGoalProposal.PortfolioFactParameters parameters =
                (UserGoalProposal.PortfolioFactParameters) goal.getParameters();
        assertThat(parameters.getFacets())
                .containsExactly(UserGoalProposal.Facet.VERIFICATION);
        assertThat(parameters.getDepth()).isEqualTo(UserGoalProposal.Depth.STANDARD);
    }
}
