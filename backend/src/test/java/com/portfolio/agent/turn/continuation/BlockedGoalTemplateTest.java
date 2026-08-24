package com.portfolio.agent.turn.continuation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.turn.planning.BlockedGoalTemplate;
import com.portfolio.agent.turn.planning.ClarificationProposal;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalRequestedOutput;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlockedGoalTemplateTest {

    @Test
    void serializedTemplateContainsNoVisitorQuestionOrInputAnchor() throws Exception {
        String visitorSentinel = "VISITOR_PRIVATE_SENTINEL_8391";
        BlockedGoalTemplate template = BlockedGoalTemplate.recommendation(
                null, Set.of("CAPABILITY_SQL"), ClarificationProposal.Field.REQUESTED_SIZE);

        String json = new ObjectMapper().writeValueAsString(template);

        assertThat(json).doesNotContain(visitorSentinel, "inputAnchor", "question", "prompt");
        assertThat(json).contains("PORTFOLIO_RECOMMEND", "REQUESTED_SIZE", "CAPABILITY_SQL");
    }

    @Test
    void answerRestoresRecommendationSizeAndConstraintsWithoutRawAnchor() {
        BlockedGoalTemplate template = BlockedGoalTemplate.recommendation(
                null, Set.of("CAPABILITY_SQL"), ClarificationProposal.Field.REQUESTED_SIZE);

        BlockedGoalTemplate.Resolution resolution = template.resolve(
                new BlockedGoalTemplate.RequestedSizeValue(3));

        assertThat(resolution.kind()).isEqualTo(BlockedGoalTemplate.Resolution.Kind.RESOLVED);
        UserGoalProposal.ProposedGoal goal = resolution.proposal().getGoals().getFirst();
        assertThat(goal.getGoalKind()).isEqualTo(GoalKind.PORTFOLIO_RECOMMEND);
        assertThat(goal.getRequestedOutputs()).containsExactly(GoalRequestedOutput.RECOMMENDATION);
        UserGoalProposal.PortfolioRecommendationParameters parameters =
                (UserGoalProposal.PortfolioRecommendationParameters) goal.getParameters();
        assertThat(parameters.getRequestedSize()).isEqualTo(3);
        assertThat(parameters.getConstraints()).containsExactly("CAPABILITY_SQL");
        assertThat(goal.getInputAnchor().getText()).isEqualTo("已澄清的公开目标");
    }

    @Test
    void sameFieldCannotRepeatAndThirdClarificationCannotBeCreated() {
        assertThatThrownBy(() -> new BlockedGoalTemplate(
                GoalKind.PORTFOLIO_RECOMMEND, List.of(),
                Set.of(GoalRequestedOutput.RECOMMENDATION), Set.of(), Set.of(),
                null, Set.of(), ClarificationProposal.Field.REQUESTED_SIZE,
                Set.of(ClarificationProposal.Field.REQUESTED_SIZE),
                List.of(ClarificationProposal.Field.REQUESTED_SIZE), 1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new BlockedGoalTemplate(
                GoalKind.PORTFOLIO_FACT, List.of(), Set.of(GoalRequestedOutput.OVERVIEW),
                Set.of(UserGoalProposal.Facet.OVERVIEW), Set.of(), null, Set.of(),
                ClarificationProposal.Field.SUBJECT,
                Set.of(ClarificationProposal.Field.SUBJECT),
                List.of(ClarificationProposal.Field.SUBJECT), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noInformationCannotResolveGoal() {
        BlockedGoalTemplate template = BlockedGoalTemplate.recommendation(
                null, Set.of(), ClarificationProposal.Field.REQUESTED_SIZE);

        assertThat(template.resolve(null).kind())
                .isEqualTo(BlockedGoalTemplate.Resolution.Kind.NO_INFORMATION);
        assertThat(template.resolve(new BlockedGoalTemplate.ConstraintValue(
                Set.of("CAPABILITY_SQL"))).kind())
                .isEqualTo(BlockedGoalTemplate.Resolution.Kind.NO_INFORMATION);
    }

    @Test
    void outputIsDerivedFromFacetAndCannotBecomeASecondAuthority() {
        assertThatThrownBy(() -> new BlockedGoalTemplate(
                GoalKind.PORTFOLIO_FACT, List.of(), Set.of(GoalRequestedOutput.BACKGROUND),
                Set.of(UserGoalProposal.Facet.OVERVIEW), Set.of(), null, Set.of(),
                ClarificationProposal.Field.SUBJECT,
                Set.of(ClarificationProposal.Field.SUBJECT),
                List.of(), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match");
        assertThatThrownBy(() -> new BlockedGoalTemplate(
                GoalKind.PORTFOLIO_FACT, List.of(), Set.of(GoalRequestedOutput.OVERVIEW),
                Set.of(UserGoalProposal.Facet.OVERVIEW), Set.of(), null, Set.of(),
                ClarificationProposal.Field.OUTPUT,
                Set.of(ClarificationProposal.Field.OUTPUT), List.of(), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match goal kind");
    }

    @Test
    void rejectsResolvedFieldAndGoalFieldMismatch() {
        assertThatThrownBy(() -> BlockedGoalTemplate.recommendation(
                2, Set.of(), ClarificationProposal.Field.REQUESTED_SIZE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already populated");
        assertThatThrownBy(() -> new BlockedGoalTemplate(
                GoalKind.PORTFOLIO_FACT, List.of(), Set.of(GoalRequestedOutput.OVERVIEW),
                Set.of(UserGoalProposal.Facet.OVERVIEW), Set.of(), null, Set.of(),
                ClarificationProposal.Field.REQUESTED_SIZE,
                Set.of(ClarificationProposal.Field.REQUESTED_SIZE), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match goal kind");
    }
}
