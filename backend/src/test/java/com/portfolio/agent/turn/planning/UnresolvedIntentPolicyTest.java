package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.continuation.ConversationSemanticState;
import com.portfolio.agent.turn.execution.AnswerSectionType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UnresolvedIntentPolicyTest {

    private final UnresolvedIntentPolicy policy = new UnresolvedIntentPolicy();

    @Test
    void resolvesTheClosedUnionOfWhitespaceDigitsAndAllSevenPunctuationTypes() {
        for (String value : List.of(
                "1", "123", "？", "...", "1?", "12。。", "1 ...",
                "1 \t_-()“”?")) {
            assertThat(policy.tryResolve(standardInput(value, null, List.of(), null)))
                    .as("closed low-information input %s", value)
                    .isPresent()
                    .get()
                    .satisfies(result -> {
                        assertThat(result.getKind())
                                .isEqualTo(ResolvedGoalSet.Kind.CONVERSATIONAL);
                        assertThat(result.getMessageSource())
                                .isEqualTo(ResolvedGoalSet.MessageSource.SERVER_FIXED);
                        assertThat(result.getMessage().orElseThrow()).contains(
                                "介绍、比较还是推荐项目");
                    });
        }
    }

    @Test
    void releasesSymbolsLettersAndSemanticShortTextToTheInterpreter() {
        for (String value : List.of("~", "￥", "😀", "a", "嗯", "继续")) {
            assertThat(policy.tryResolve(standardInput(value, null, List.of(), null)))
                    .as("non-closed input %s", value)
                    .isEmpty();
        }
    }

    @Test
    void releasesNumericInputWhenTypedRecentSemanticStateExists() {
        assertThat(policy.tryResolve(standardInput(
                "1", recentSemanticState(), List.of(), null))).isEmpty();
    }

    @Test
    void releasesNumericInputWhenRouteCandidatesExist() {
        GoalInterpretationInput.RouteCandidate candidate =
                new GoalInterpretationInput.RouteCandidate(
                        "C1", GoalSubjectReference.Kind.PROJECT,
                        "sql-audit", "SQL 审计项目", Set.of("SQL 审计项目"));

        assertThat(policy.tryResolve(standardInput(
                "1", null, List.of(candidate), null))).isEmpty();
    }

    @Test
    void releasesNumericInputInDiscussionMode() {
        GoalInterpretationInput.PublicSubjectDescriptor subject = subject();
        GoalInterpretationInput input = new GoalInterpretationInput(
                "1", List.of(), List.of(subject), Set.of(GoalKind.values()),
                GoalInterpretationInput.InterpretationMode.DISCUSSION,
                GoalInterpretationInput.DiscussionState.ACTIVE,
                subject, List.of(),
                Set.of(SemanticRouteProposal.Route.CONTINUE_CURRENT_PROJECT),
                Set.of(), null,
                SemanticTaskParameters.AudienceProfile.GUEST, null);

        assertThat(policy.tryResolve(input)).isEmpty();
    }

    @Test
    void defaultSubjectDoesNotManufactureIntentForNumericInput() {
        GoalInterpretationInput.PublicSubjectDescriptor subject = subject();

        assertThat(policy.tryResolve(standardInput(
                "1", null, List.of(), subject))).isPresent();
    }

    private GoalInterpretationInput standardInput(
            String text,
            ConversationSemanticState state,
            List<GoalInterpretationInput.RouteCandidate> candidates,
            GoalInterpretationInput.PublicSubjectDescriptor defaultSubject) {
        GoalInterpretationInput.PublicSubjectDescriptor subject = subject();
        return new GoalInterpretationInput(
                text, List.of(), List.of(subject), Set.of(GoalKind.values()),
                GoalInterpretationInput.InterpretationMode.STANDARD,
                GoalInterpretationInput.DiscussionState.NONE, null,
                candidates,
                Set.of(SemanticRouteProposal.Route.STANDARD_GOAL,
                        SemanticRouteProposal.Route.NEEDS_CLARIFICATION),
                Set.of(), defaultSubject,
                SemanticTaskParameters.AudienceProfile.GUEST, state);
    }

    private GoalInterpretationInput.PublicSubjectDescriptor subject() {
        return new GoalInterpretationInput.PublicSubjectDescriptor(
                GoalSubjectReference.Kind.PROJECT,
                "sql-audit", "SQL 审计项目");
    }

    private ConversationSemanticState recentSemanticState() {
        return new ConversationSemanticState(
                "public-1",
                List.of(new ConversationSemanticState.GoalSummary(
                        "goal-1", GoalKind.PORTFOLIO_FACT,
                        List.of(new ConversationSemanticState.Subject(
                                GoalSubjectReference.Kind.PROJECT, "sql-audit")),
                        Set.of(GoalRequestedOutput.SOLUTION),
                        Set.of(UserGoalProposal.Facet.SOLUTION),
                        UserGoalProposal.Depth.STANDARD, Set.of(), null, Set.of(),
                        List.of(new ConversationSemanticState.SectionReference(
                                "section-goal-1-1", AnswerSectionType.SOLUTION)))),
                Instant.parse("2026-08-24T05:00:00Z"));
    }
}
