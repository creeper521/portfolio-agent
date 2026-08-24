package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.continuation.ConversationSemanticState;
import com.portfolio.agent.turn.execution.AnswerSectionType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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

    @Test
    void omittedPageReferenceReceivesTheValidatedDefaultSubject() {
        GoalInterpretationInput.PublicSubjectDescriptor subject =
                new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT, "sql-audit", "SQL 审计项目");
        GoalInterpretationInput input = new GoalInterpretationInput(
                "进一步介绍这个项目", List.of(), List.of(subject),
                Set.of(GoalKind.PORTFOLIO_FACT),
                GoalInterpretationInput.InterpretationMode.STANDARD,
                GoalInterpretationInput.DiscussionState.NONE,
                null, List.of(), Set.of(SemanticRouteProposal.Route.STANDARD_GOAL),
                Set.of(), subject, SemanticTaskParameters.AudienceProfile.INTERVIEWER);
        UserGoalProposal.InputAnchor anchor =
                new UserGoalProposal.InputAnchor("进一步介绍这个项目", 0);
        UserGoalProposal unbound = new UserGoalProposal(List.of(
                new UserGoalProposal.ProposedGoal(
                        "page-project", GoalKind.PORTFOLIO_FACT, anchor, List.of(),
                        Set.of(GoalRequestedOutput.OVERVIEW),
                        GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                        new UserGoalProposal.PortfolioFactParameters(
                                Set.of(UserGoalProposal.Facet.OVERVIEW),
                                UserGoalProposal.Depth.STANDARD))));

        GoalSubjectReference bound = validator.validate(
                        SemanticRouteProposal.standardGoal(unbound), input)
                .getGoalProposal().orElseThrow().getGoals().getFirst()
                .getSubjectCandidates().getFirst();

        assertThat(bound.getReference()).isEqualTo("sql-audit");
        assertThat(bound.getBasis()).isEqualTo(GoalSubjectReference.Basis.SURFACE_HINT);
        assertThat(bound.getAnchor()).isEmpty();
    }

    @Test
    void explicitPreviousTurnReferenceReceivesOnlyTheTypedRecentSubject() {
        GoalInterpretationInput.PublicSubjectDescriptor subject =
                new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT,
                        "sql-audit", "SQL 审计项目");
        ConversationSemanticState state = new ConversationSemanticState(
                "public-1", List.of(new ConversationSemanticState.GoalSummary(
                "goal-1", GoalKind.PORTFOLIO_FACT,
                List.of(new ConversationSemanticState.Subject(
                        GoalSubjectReference.Kind.PROJECT, "sql-audit")),
                Set.of(GoalRequestedOutput.SOLUTION),
                Set.of(UserGoalProposal.Facet.SOLUTION),
                UserGoalProposal.Depth.STANDARD, Set.of(), null, Set.of(),
                List.of(new ConversationSemanticState.SectionReference(
                        "section-goal-1-1", AnswerSectionType.SOLUTION)))),
                Instant.parse("2026-08-24T05:00:00Z"));
        GoalInterpretationInput input = new GoalInterpretationInput(
                "进一步展开", List.of(), List.of(subject),
                Set.of(GoalKind.PORTFOLIO_FACT),
                GoalInterpretationInput.InterpretationMode.STANDARD,
                GoalInterpretationInput.DiscussionState.NONE,
                null, List.of(), Set.of(SemanticRouteProposal.Route.STANDARD_GOAL),
                Set.of(), null, SemanticTaskParameters.AudienceProfile.GUEST, state);
        UserGoalProposal.InputAnchor anchor =
                new UserGoalProposal.InputAnchor("进一步展开", 0);
        UserGoalProposal unbound = new UserGoalProposal(List.of(
                new UserGoalProposal.ProposedGoal(
                        "recent-project", GoalKind.PORTFOLIO_FACT, anchor, List.of(),
                        Set.of(GoalRequestedOutput.SOLUTION),
                        GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                        new UserGoalProposal.PortfolioFactParameters(
                                Set.of(UserGoalProposal.Facet.SOLUTION),
                                UserGoalProposal.Depth.DETAILED))));

        GoalSubjectReference bound = validator.validate(
                        SemanticRouteProposal.standardGoal(unbound,
                                new SemanticRouteProposal.RecentSemanticReference(
                                        "goal-1", "section-goal-1-1")), input)
                .getGoalProposal().orElseThrow().getGoals().getFirst()
                .getSubjectCandidates().getFirst();

        assertThat(bound.getReference()).isEqualTo("sql-audit");
        assertThat(bound.getBasis()).isEqualTo(GoalSubjectReference.Basis.RECENT_TURN);
        assertThat(bound.getAnchor()).isEmpty();
    }

    @Test
    void omittedSubjectWithoutExplicitRecentReferenceIsNotSilentlyBound() {
        GoalInterpretationInput.PublicSubjectDescriptor subject =
                new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT,
                        "sql-audit", "SQL 审计项目");
        ConversationSemanticState state = new ConversationSemanticState(
                "public-1", List.of(new ConversationSemanticState.GoalSummary(
                "goal-1", GoalKind.PORTFOLIO_FACT,
                List.of(new ConversationSemanticState.Subject(
                        GoalSubjectReference.Kind.PROJECT, "sql-audit")),
                Set.of(GoalRequestedOutput.OVERVIEW),
                Set.of(UserGoalProposal.Facet.OVERVIEW),
                UserGoalProposal.Depth.STANDARD, Set.of(), null, Set.of(), List.of())),
                Instant.parse("2026-08-24T05:00:00Z"));
        GoalInterpretationInput input = new GoalInterpretationInput(
                "介绍一个新主题", List.of(), List.of(subject),
                Set.of(GoalKind.PORTFOLIO_FACT),
                GoalInterpretationInput.InterpretationMode.STANDARD,
                GoalInterpretationInput.DiscussionState.NONE,
                null, List.of(), Set.of(SemanticRouteProposal.Route.STANDARD_GOAL),
                Set.of(), null, SemanticTaskParameters.AudienceProfile.GUEST, state);
        UserGoalProposal.InputAnchor anchor =
                new UserGoalProposal.InputAnchor("介绍一个新主题", 0);
        UserGoalProposal unbound = new UserGoalProposal(List.of(
                new UserGoalProposal.ProposedGoal(
                        "new-topic", GoalKind.PORTFOLIO_FACT, anchor, List.of(),
                        Set.of(GoalRequestedOutput.OVERVIEW),
                        GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                        new UserGoalProposal.PortfolioFactParameters(
                                Set.of(UserGoalProposal.Facet.OVERVIEW),
                                UserGoalProposal.Depth.STANDARD))));

        SemanticRouteProposal validated = validator.validate(
                SemanticRouteProposal.standardGoal(unbound), input);

        assertThat(validated.getGoalProposal().orElseThrow().getGoals().getFirst()
                .getSubjectCandidates()).isEmpty();
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
