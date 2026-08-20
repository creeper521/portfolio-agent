package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;
import com.portfolio.agent.turn.lifecycle.ConversationWindow;
import com.portfolio.agent.turn.execution.TurnDeadline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GoalResolverTest {

    @Test
    void freeTextCallsOnlyGoalInterpretationPortOnce() {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger reviewedCalls = new AtomicInteger();
        AtomicReference<TurnDeadline> receivedDeadline = new AtomicReference<>();
        UserGoalProposal proposal = generalProposal(GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION);
        GoalResolver resolver = resolver((input, deadline) -> {
            modelCalls.incrementAndGet();
            receivedDeadline.set(deadline);
            return GoalInterpretationResult.semanticRoute(
                    SemanticRouteProposal.standardGoal(proposal));
        }, command -> {
            reviewedCalls.incrementAndGet();
            return proposal;
        });

        TurnDeadline deadline = deadline();
        ResolvedGoalSet result = resolver.resolve(
                freeText("解释幂等"), context(), deadline);

        assertThat(result.getKind()).isEqualTo(ResolvedGoalSet.Kind.GOALS);
        assertThat(result.getGoalProposal()).contains(proposal);
        assertThat(modelCalls).hasValue(1);
        assertThat(reviewedCalls).hasValue(0);
        assertThat(receivedDeadline).hasValue(deadline);
    }

    @Test
    void presetContinueAndClarificationUseReviewedSourceWithoutModelCall() {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger reviewedCalls = new AtomicInteger();
        UserGoalProposal proposal = generalProposal(GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION);
        GoalResolver resolver = resolver((input, deadline) -> {
            modelCalls.incrementAndGet();
            return GoalInterpretationResult.semanticRoute(
                    SemanticRouteProposal.standardGoal(proposal));
        }, command -> {
            reviewedCalls.incrementAndGet();
            return proposal;
        });

        assertThat(resolver.resolve(preset(), context(), deadline()).getKind()).isEqualTo(ResolvedGoalSet.Kind.GOALS);
        assertThat(resolver.resolve(continuation(), context(), deadline()).getKind()).isEqualTo(ResolvedGoalSet.Kind.GOALS);
        assertThat(resolver.resolve(clarification(), context(), deadline()).getKind()).isEqualTo(ResolvedGoalSet.Kind.GOALS);
        assertThat(modelCalls).hasValue(0);
        assertThat(reviewedCalls).hasValue(3);
    }

    @Test
    void providerFailureWithoutExactReviewedAliasIsCapabilityUnavailable() {
        GoalResolver resolver = resolver((input, deadline) -> {
            throw new GoalInterpretationUnavailableException();
        }, command -> generalProposal(GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION));

        ResolvedGoalSet result = resolver.resolve(
                freeText("请随便介绍一下"), context(), deadline());

        assertThat(result.getKind()).isEqualTo(ResolvedGoalSet.Kind.CAPABILITY_UNAVAILABLE);
    }

    @Test
    void providerFailureDoesNotUseReviewedAliasAsANaturalLanguageFallback() {
        GoalResolver resolver = resolver((input, deadline) -> {
            throw new GoalInterpretationUnavailableException();
        }, command -> generalProposal(GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION));

        ResolvedGoalSet result = resolver.resolve(
                freeText("SQL 审计项目"), context(), deadline());

        assertThat(result.getKind())
                .isEqualTo(ResolvedGoalSet.Kind.CAPABILITY_UNAVAILABLE);
        assertThat(result.getGoalProposal()).isEmpty();
    }

    @Test
    void expiredProviderFailureDoesNotEnterMinimalFallback() {
        GoalResolver resolver = resolver((input, deadline) -> {
            throw new GoalInterpretationUnavailableException();
        }, command -> generalProposal(GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION));
        TurnDeadline expired = new TurnDeadline(
                java.time.Instant.parse("2026-08-19T00:00:00Z"),
                java.time.Clock.fixed(
                        java.time.Instant.parse("2026-08-19T00:00:01Z"),
                        java.time.ZoneOffset.UTC));

        ResolvedGoalSet result = resolver.resolve(
                freeText("SQL 审计项目"), context(), expired);

        assertThat(result.getKind())
                .isEqualTo(ResolvedGoalSet.Kind.CAPABILITY_UNAVAILABLE);
    }

    @Test
    void semanticAmbiguityRemainsClarificationInsteadOfCapabilityFailure() {
        BlockedGoalTemplate blocked = new BlockedGoalTemplate(
                GoalKind.PORTFOLIO_FACT, List.of(), Set.of(GoalRequestedOutput.OVERVIEW),
                Set.of(UserGoalProposal.Facet.OVERVIEW), Set.of(), null, Set.of(),
                ClarificationProposal.Field.SUBJECT,
                Set.of(ClarificationProposal.Field.SUBJECT), 1);
        ClarificationProposal clarification = new ClarificationProposal(
                ClarificationProposal.Field.SUBJECT, "请选择项目",
                blocked);
        GoalResolver resolver = resolver(
                (input, deadline) -> GoalInterpretationResult.semanticRoute(
                        SemanticRouteProposal.needsClarification(clarification)),
                command -> generalProposal(GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION));

        ResolvedGoalSet result = resolver.resolve(
                freeText("这个项目怎么样"), context(), deadline());

        assertThat(result.getKind()).isEqualTo(ResolvedGoalSet.Kind.CLARIFICATION);
        assertThat(result.getClarification()).contains(clarification);
    }

    @Test
    void recommendationQuantityComesFromTheClosedProviderProposal() {
        AtomicInteger modelCalls = new AtomicInteger();
        UserGoalProposal proposal = recommendationProposal(2, Set.of());
        GoalResolver resolver = resolver((input, deadline) -> {
            modelCalls.incrementAndGet();
            return GoalInterpretationResult.semanticRoute(
                    SemanticRouteProposal.standardGoal(proposal));
        }, command -> generalProposal(GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION));

        ResolvedGoalSet result = resolver.resolve(
                freeText("给我推荐项目"), context(), deadline());

        UserGoalProposal.ProposedGoal goal = result.getGoalProposal()
                .orElseThrow().getGoals().getFirst();
        assertThat(goal.getGoalKind()).isEqualTo(GoalKind.PORTFOLIO_RECOMMEND);
        assertThat(goal.getSubjectCandidates()).isEmpty();
        assertThat(((UserGoalProposal.PortfolioRecommendationParameters)
                goal.getParameters()).getRequestedSize()).isEqualTo(2);
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void recommendationConstraintAndSizeAreNotParsedFromVisitorText() {
        AtomicInteger modelCalls = new AtomicInteger();
        UserGoalProposal proposal = recommendationProposal(
                5, Set.of("POSTGRESQL"));
        GoalResolver resolver = resolver((input, deadline) -> {
            modelCalls.incrementAndGet();
            return GoalInterpretationResult.semanticRoute(
                    SemanticRouteProposal.standardGoal(proposal));
        }, command -> generalProposal(GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION));

        ResolvedGoalSet result = resolver.resolve(
                freeText("给我找一些作品"), context(), deadline());

        UserGoalProposal.PortfolioRecommendationParameters parameters =
                (UserGoalProposal.PortfolioRecommendationParameters) result
                        .getGoalProposal().orElseThrow().getGoals()
                        .getFirst().getParameters();
        assertThat(parameters.getRequestedSize()).isEqualTo(5);
        assertThat(parameters.getConstraints()).containsExactly("POSTGRESQL");
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void negatedOrConflictingRecommendationConstraintDefersToProvider() {
        AtomicInteger modelCalls = new AtomicInteger();
        UserGoalProposal providerProposal = recommendationProposal(2, Set.of());
        GoalResolver resolver = resolver((input, deadline) -> {
            modelCalls.incrementAndGet();
            return GoalInterpretationResult.semanticRoute(
                    SemanticRouteProposal.standardGoal(providerProposal));
        }, command -> providerProposal);

        ResolvedGoalSet negated = resolver.resolve(
                freeText("推荐两个不要选择后端的项目"), context(), deadline());
        ResolvedGoalSet conflicting = resolver.resolve(
                freeText("推荐两个后端，但不要后端的项目"), context(), deadline());

        assertThat(modelCalls).hasValue(2);
        assertThat(((UserGoalProposal.PortfolioRecommendationParameters) negated
                .getGoalProposal().orElseThrow().getGoals().getFirst().getParameters())
                .getConstraints()).doesNotContain("BACKEND");
        assertThat(((UserGoalProposal.PortfolioRecommendationParameters) conflicting
                .getGoalProposal().orElseThrow().getGoals().getFirst().getParameters())
                .getConstraints()).doesNotContain("BACKEND");
    }

    @Test
    void negatedRecommendationIntentDoesNotCreateRecommendationGoal() {
        AtomicInteger modelCalls = new AtomicInteger();
        GoalResolver resolver = resolver((input, deadline) -> {
            modelCalls.incrementAndGet();
            return GoalInterpretationResult.conversational("按比较意图继续处理");
        }, command -> generalProposal(GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION));

        ResolvedGoalSet result = resolver.resolve(
                freeText("不用推荐项目，直接比较 A 和 B"), context(), deadline());

        assertThat(modelCalls).hasValue(1);
        assertThat(result.getKind()).isEqualTo(ResolvedGoalSet.Kind.CONVERSATIONAL);
        assertThat(result.getGoalProposal()).isEmpty();
    }

    @Test
    void greetingAndThanksAreConversationalBeforeProvider() {
        AtomicInteger modelCalls = new AtomicInteger();
        GoalResolver resolver = resolver((input, deadline) -> {
            modelCalls.incrementAndGet();
            throw new AssertionError("safe social input must not call provider");
        }, command -> generalProposal(GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION));

        assertThat(resolver.resolve(freeText("你好！"), context(), deadline()).getKind())
                .isEqualTo(ResolvedGoalSet.Kind.CONVERSATIONAL);
        assertThat(resolver.resolve(freeText("谢谢"), context(), deadline()).getKind())
                .isEqualTo(ResolvedGoalSet.Kind.CONVERSATIONAL);
        assertThat(modelCalls).hasValue(0);
    }

    private GoalResolver resolver(
            GoalInterpretationPort port, ReviewedGoalSource reviewedGoalSource) {
        return new GoalResolver(
                port, reviewedGoalSource, new GoalInterpretationInputFactory(),
                new SafeConversationalFastPath(), new SemanticRouteValidator(),
                new GoalBoundaryPolicy());
    }

    private GoalResolutionContext context() {
        return new GoalResolutionContext(
                List.of(new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT, "sql-audit", "SQL 审计项目")),
                Set.of(GoalKind.values()));
    }

    private TurnDeadline deadline() {
        return TurnDeadline.after(
                java.time.Duration.ofSeconds(1), java.time.Clock.systemUTC());
    }

    private AgentTurnCommand freeText(String text) {
        return new AgentTurnCommand.Ask(UUID.randomUUID(), new AgentTurnCommand.FreeText(text),
                AgentTurnCommand.SurfaceContext.empty(), ConversationWindow.empty());
    }

    private AgentTurnCommand preset() {
        return new AgentTurnCommand.Ask(UUID.randomUUID(),
                new AgentTurnCommand.Preset("question-sql-audit", "pcv1-0123456789abcdef"),
                AgentTurnCommand.SurfaceContext.empty(), ConversationWindow.empty());
    }

    private AgentTurnCommand continuation() {
        return new AgentTurnCommand.Continue(
                UUID.randomUUID(),
                AgentTurnCommand.ContinueOperation.ROUTE_IN_CONTEXT,
                "context_opaque", null, "继续", null,
                AgentTurnCommand.SurfaceContext.empty(),
                ConversationWindow.empty());
    }

    private AgentTurnCommand clarification() {
        return new AgentTurnCommand.ResolveClarification(
                UUID.randomUUID(), "clarification_opaque",
                new AgentTurnCommand.ChoiceAnswer("choice_opaque"),
                AgentTurnCommand.SurfaceContext.empty(), ConversationWindow.empty());
    }

    static UserGoalProposal generalProposal(GoalKnowledgeRequirement requirement) {
        return new UserGoalProposal(List.of(new UserGoalProposal.ProposedGoal(
                "general-goal", GoalKind.GENERAL_EXPLANATION,
                new UserGoalProposal.InputAnchor("幂等", 0), List.of(),
                Set.of(GoalRequestedOutput.EXPLANATION), requirement,
                new UserGoalProposal.GeneralExplanationParameters(
                        new UserGoalProposal.InputAnchor("幂等", 0),
                        UserGoalProposal.Depth.STANDARD))));
    }

    private static UserGoalProposal recommendationProposal(int size, Set<String> constraints) {
        UserGoalProposal.InputAnchor anchor = new UserGoalProposal.InputAnchor("推荐项目", 0);
        return new UserGoalProposal(List.of(new UserGoalProposal.ProposedGoal(
                "provider-recommendation", GoalKind.PORTFOLIO_RECOMMEND, anchor, List.of(),
                Set.of(GoalRequestedOutput.RECOMMENDATION),
                GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                new UserGoalProposal.PortfolioRecommendationParameters(size, constraints))));
    }
}
