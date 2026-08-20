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
                null, Set.of("BACKEND"), ClarificationProposal.Field.REQUESTED_SIZE);

        String json = new ObjectMapper().writeValueAsString(template);

        assertThat(json).doesNotContain(visitorSentinel, "inputAnchor", "question", "prompt");
        assertThat(json).contains("PORTFOLIO_RECOMMEND", "REQUESTED_SIZE", "BACKEND");
    }

    @Test
    void answerRestoresRecommendationSizeAndConstraintsWithoutRawAnchor() {
        BlockedGoalTemplate template = BlockedGoalTemplate.recommendation(
                null, Set.of("BACKEND"), ClarificationProposal.Field.REQUESTED_SIZE);

        BlockedGoalTemplate.Resolution resolution = template.resolve(
                new BlockedGoalTemplate.RequestedSizeValue(3));

        assertThat(resolution.kind()).isEqualTo(BlockedGoalTemplate.Resolution.Kind.RESOLVED);
        UserGoalProposal.ProposedGoal goal = resolution.proposal().getGoals().getFirst();
        assertThat(goal.getGoalKind()).isEqualTo(GoalKind.PORTFOLIO_RECOMMEND);
        assertThat(goal.getRequestedOutputs()).containsExactly(GoalRequestedOutput.RECOMMENDATION);
        UserGoalProposal.PortfolioRecommendationParameters parameters =
                (UserGoalProposal.PortfolioRecommendationParameters) goal.getParameters();
        assertThat(parameters.getRequestedSize()).isEqualTo(3);
        assertThat(parameters.getConstraints()).containsExactly("BACKEND");
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

        BlockedGoalTemplate second = new BlockedGoalTemplate(
                GoalKind.PORTFOLIO_FACT, List.of(), Set.of(GoalRequestedOutput.OVERVIEW),
                Set.of(UserGoalProposal.Facet.OVERVIEW), Set.of(), null, Set.of(),
                ClarificationProposal.Field.SUBJECT,
                Set.of(ClarificationProposal.Field.OUTPUT,
                        ClarificationProposal.Field.SUBJECT), 2);
        assertThat(second.getDepth()).isEqualTo(2);
        assertThatThrownBy(() -> new BlockedGoalTemplate(
                GoalKind.PORTFOLIO_FACT, List.of(), Set.of(GoalRequestedOutput.OVERVIEW),
                Set.of(UserGoalProposal.Facet.OVERVIEW), Set.of(), null, Set.of(),
                ClarificationProposal.Field.SUBJECT,
                Set.of(ClarificationProposal.Field.OUTPUT,
                        ClarificationProposal.Field.SUBJECT),
                List.of(ClarificationProposal.Field.OUTPUT), 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noInformationCannotResolveGoal() {
        BlockedGoalTemplate template = BlockedGoalTemplate.recommendation(
                null, Set.of(), ClarificationProposal.Field.REQUESTED_SIZE);

        assertThat(template.resolve(null).kind())
                .isEqualTo(BlockedGoalTemplate.Resolution.Kind.NO_INFORMATION);
        assertThat(template.resolve(new BlockedGoalTemplate.ConstraintValue(
                Set.of("BACKEND"))).kind())
                .isEqualTo(BlockedGoalTemplate.Resolution.Kind.NO_INFORMATION);
    }

    @Test
    void twoDistinctFieldsAdvanceOneChallengeAtATimeThenResolve() {
        BlockedGoalTemplate first = new BlockedGoalTemplate(
                GoalKind.PORTFOLIO_FACT, List.of(), Set.of(),
                Set.of(UserGoalProposal.Facet.OVERVIEW), Set.of(), null, Set.of(),
                ClarificationProposal.Field.SUBJECT,
                Set.of(ClarificationProposal.Field.SUBJECT),
                List.of(ClarificationProposal.Field.OUTPUT), 1);

        BlockedGoalTemplate.Resolution afterSubject = first.resolve(
                new BlockedGoalTemplate.SubjectValue(List.of(
                        new BlockedGoalTemplate.Subject(
                                com.portfolio.agent.turn.planning.GoalSubjectReference.Kind.PROJECT,
                                "project-a"))));

        assertThat(afterSubject.kind())
                .isEqualTo(BlockedGoalTemplate.Resolution.Kind.NEXT_CLARIFICATION);
        BlockedGoalTemplate second = afterSubject.continuation();
        assertThat(second.getUnresolvedField()).isEqualTo(ClarificationProposal.Field.OUTPUT);
        assertThat(second.getAskedFields()).containsExactlyInAnyOrder(
                ClarificationProposal.Field.SUBJECT, ClarificationProposal.Field.OUTPUT);
        assertThat(second.getDepth()).isEqualTo(2);
        assertThat(second.getRemainingFields()).isEmpty();

        BlockedGoalTemplate.Resolution completed = second.resolve(
                new BlockedGoalTemplate.OutputValue(Set.of(GoalRequestedOutput.OVERVIEW)));
        assertThat(completed.kind()).isEqualTo(BlockedGoalTemplate.Resolution.Kind.RESOLVED);
        assertThat(completed.proposal().getGoals().getFirst().getSubjectCandidates())
                .extracting(com.portfolio.agent.turn.planning.GoalSubjectReference::getReference)
                .containsExactly("project-a");
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
