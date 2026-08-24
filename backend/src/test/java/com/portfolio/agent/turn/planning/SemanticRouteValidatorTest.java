package com.portfolio.agent.turn.planning;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticRouteValidatorTest {

    private final SemanticRouteValidator validator = new SemanticRouteValidator();

    @Test
    void acceptsAClosedStandardGoalInsideTheTrustedScope() {
        GoalInterpretationInput input = input(Set.of(
                SemanticRouteProposal.Route.STANDARD_GOAL,
                SemanticRouteProposal.Route.NEEDS_CLARIFICATION));
        UserGoalProposal goal = portfolioFact();
        SemanticRouteProposal proposal = SemanticRouteProposal.standardGoal(goal);

        SemanticRouteProposal validated = validator.validate(proposal, input);

        assertThat(validated).isSameAs(proposal);
        assertThat(validated.getGoalProposal()).containsSame(goal);
    }

    @Test
    void rejectsRoutesThatAreNotAllowedByTheTrustedInput() {
        GoalInterpretationInput input = input(Set.of(
                SemanticRouteProposal.Route.STANDARD_GOAL));
        SemanticRouteProposal proposal =
                SemanticRouteProposal.enterRecommendedResult("C1");

        assertThatThrownBy(() -> validator.validate(proposal, input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("route is not allowed");
    }

    @Test
    void rejectsCandidateKeysThatDoNotComeFromTypedCandidates() {
        GoalInterpretationInput input = input(Set.of(
                SemanticRouteProposal.Route.ENTER_RECOMMENDED_RESULT));
        SemanticRouteProposal proposal =
                SemanticRouteProposal.enterRecommendedResult("C1");

        assertThatThrownBy(() -> validator.validate(proposal, input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("candidate");
    }

    @Test
    void discussionGoalReceivesTheBackendLockedProject() {
        GoalInterpretationInput input = discussionInput();
        UserGoalProposal.InputAnchor anchor =
                new UserGoalProposal.InputAnchor("介绍实现", 0);
        UserGoalProposal unlocked = new UserGoalProposal(List.of(
                new UserGoalProposal.ProposedGoal(
                        "discussion-goal",
                        GoalKind.PORTFOLIO_FACT,
                        anchor,
                        List.of(),
                        Set.of(GoalRequestedOutput.SOLUTION),
                        GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                        new UserGoalProposal.PortfolioFactParameters(
                                Set.of(UserGoalProposal.Facet.SOLUTION),
                                UserGoalProposal.Depth.STANDARD))));

        SemanticRouteProposal validated = validator.validate(
                SemanticRouteProposal.discussion(
                        SemanticRouteProposal.Route.CONTINUE_CURRENT_PROJECT,
                        null, unlocked),
                input);

        assertThat(validated.getGoalProposal().orElseThrow()
                .getGoals().getFirst().getSubjectCandidates())
                .singleElement()
                .extracting(GoalSubjectReference::getReference)
                .isEqualTo("sql-audit");
    }

    private GoalInterpretationInput input(Set<SemanticRouteProposal.Route> routes) {
        return new GoalInterpretationInput(
                "介绍 SQL 审计项目",
                List.of(),
                List.of(new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT,
                        "sql-audit",
                        "SQL 审计项目")),
                Set.of(GoalKind.PORTFOLIO_FACT),
                GoalInterpretationInput.InterpretationMode.STANDARD,
                GoalInterpretationInput.DiscussionState.NONE,
                null,
                List.of(),
                routes);
    }

    private GoalInterpretationInput discussionInput() {
        GoalInterpretationInput.PublicSubjectDescriptor locked =
                new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT,
                        "sql-audit",
                        "SQL 审计项目");
        return new GoalInterpretationInput(
                "介绍实现",
                List.of(),
                List.of(locked),
                Set.of(
                        GoalKind.PORTFOLIO_FACT,
                        GoalKind.APPLY_GENERAL_CONCEPT_TO_PORTFOLIO),
                GoalInterpretationInput.InterpretationMode.DISCUSSION,
                GoalInterpretationInput.DiscussionState.ACTIVE,
                locked,
                List.of(),
                Set.of(
                        SemanticRouteProposal.Route.CONTINUE_CURRENT_PROJECT,
                        SemanticRouteProposal.Route.START_NEW_TOPIC,
                        SemanticRouteProposal.Route.NEEDS_CLARIFICATION));
    }

    private UserGoalProposal portfolioFact() {
        UserGoalProposal.InputAnchor anchor =
                new UserGoalProposal.InputAnchor("介绍 SQL 审计项目", 0);
        return new UserGoalProposal(List.of(new UserGoalProposal.ProposedGoal(
                "portfolio-overview",
                GoalKind.PORTFOLIO_FACT,
                anchor,
                List.of(new GoalSubjectReference(
                        GoalSubjectReference.Kind.PROJECT,
                        "sql-audit",
                        GoalSubjectReference.Basis.EXPLICIT_INPUT,
                        anchor)),
                Set.of(GoalRequestedOutput.OVERVIEW),
                GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                new UserGoalProposal.PortfolioFactParameters(
                        Set.of(UserGoalProposal.Facet.OVERVIEW),
                        UserGoalProposal.Depth.STANDARD))));
    }
}
