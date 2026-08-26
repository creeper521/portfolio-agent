package com.portfolio.agent.turn.planning;

import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.structured.StructuredModelTestFixtures;
import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;
import com.portfolio.agent.turn.lifecycle.ConversationWindow;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.continuation.ConversationSemanticState;
import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.portfolio.agent.common.observability.ModelOutputDiagnostics;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoalResolverTest {

    @Test
    void modelWithoutGeneralBindingCannotProposeGeneralOrCrossDomainGoals() {
        AtomicReference<GoalInterpretationInput> captured = new AtomicReference<>();
        GoalResolver resolver = resolver((input, deadline, modelExecution) -> {
            captured.set(input);
            return GoalInterpretationResult.conversational("需要作品集范围内的问题");
        }, command -> generalProposal(
                GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION));
        com.portfolio.agent.infrastructure.model.structured.OperationBinding turnBinding =
                StructuredModelTestFixtures.nativeBindings().get(
                        ModelOperation.TURN_INTERPRETATION);

        resolver.resolve(
                freeText("推荐两个项目"), context(), deadline(),
                StructuredModelTestFixtures.resolvedModel(Map.of(
                        ModelOperation.TURN_INTERPRETATION, turnBinding)));

        assertThat(captured.get().getAllowedGoalKinds()).containsExactlyInAnyOrder(
                GoalKind.PORTFOLIO_FACT,
                GoalKind.PORTFOLIO_COMPARE,
                GoalKind.PORTFOLIO_RECOMMEND);
    }

    @Test
    void lowInformationInputIsServerFixedWithoutProviderAttempt() {
        AtomicInteger modelCalls = new AtomicInteger();
        GoalResolver resolver = resolver((input, deadline, modelExecution) -> {
            modelCalls.incrementAndGet();
            modelExecution.markAttempted(
                    ResolvedModelExecution.Stage.GOAL_INTERPRETATION);
            throw new AssertionError("closed low-information input must not call provider");
        }, command -> generalProposal(
                GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION));
        ResolvedModelExecution execution = ResolvedModelExecution.none();

        ResolvedGoalSet result = resolver.resolve(
                freeText("1 ..."), context(), deadline(), execution);

        assertThat(result.getKind()).isEqualTo(ResolvedGoalSet.Kind.CONVERSATIONAL);
        assertThat(result.getMessageSource())
                .isEqualTo(ResolvedGoalSet.MessageSource.SERVER_FIXED);
        assertThat(modelCalls).hasValue(0);
        assertThat(execution.wasAttempted(
                ResolvedModelExecution.Stage.GOAL_INTERPRETATION)).isFalse();
        assertThat(execution.wasAdopted(
                ResolvedModelExecution.Stage.GOAL_INTERPRETATION)).isFalse();
    }

    @Test
    void lowInformationTurnDoesNotBlockFollowingIndependentRecommendation() {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicReference<GoalInterpretationInput> received = new AtomicReference<>();
        UserGoalProposal recommendation = recommendationProposal(2, Set.of());
        GoalResolver resolver = resolver((input, deadline, modelExecution) -> {
            modelCalls.incrementAndGet();
            received.set(input);
            return GoalInterpretationResult.semanticRoute(
                    SemanticRouteProposal.standardGoal(recommendation));
        }, command -> generalProposal(
                GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION));

        ResolvedGoalSet first = resolver.resolve(
                freeText("1"), context(), deadline(), ResolvedModelExecution.none());
        ConversationWindow recentConversation = new ConversationWindow(List.of(
                new ConversationWindow.Message(ConversationWindow.Role.USER, "1"),
                new ConversationWindow.Message(ConversationWindow.Role.ASSISTANT,
                        first.getMessage().orElseThrow())));
        AgentTurnCommand secondCommand = new AgentTurnCommand.Ask(
                UUID.randomUUID(), AgentTurnCommand.ModelSelection.none(),
                new AgentTurnCommand.FreeText("给我推荐两个项目"),
                AgentTurnCommand.SurfaceContext.empty(), recentConversation);

        ResolvedGoalSet second = resolver.resolve(
                secondCommand, context(), deadline(), ResolvedModelExecution.none());

        assertThat(first.getKind()).isEqualTo(ResolvedGoalSet.Kind.CONVERSATIONAL);
        assertThat(modelCalls).hasValue(1);
        assertThat(received.get().getRecentSemanticState()).isNull();
        assertThat(received.get().getRecentMessages()).hasSize(2);
        UserGoalProposal.PortfolioRecommendationParameters parameters =
                (UserGoalProposal.PortfolioRecommendationParameters) second
                        .getGoalProposal().orElseThrow().getGoals()
                        .getFirst().getParameters();
        assertThat(parameters.getRequestedSize()).isEqualTo(2);
    }

    @Test
    void typedRecentStateKeepsNumericReferenceOnTheInterpreterPath() {
        AtomicInteger modelCalls = new AtomicInteger();
        GoalResolver resolver = resolver((input, deadline, modelExecution) -> {
            modelCalls.incrementAndGet();
            assertThat(input.getRecentSemanticState()).isNotNull();
            return GoalInterpretationResult.conversational("展开第一部分");
        }, command -> generalProposal(
                GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION));
        ConversationSemanticState state = recentSemanticState();

        ResolvedGoalSet result = resolver.resolve(
                freeText("1"), context(), deadline(), state);

        assertThat(modelCalls).hasValue(1);
        assertThat(result.getMessageSource())
                .isEqualTo(ResolvedGoalSet.MessageSource.PROVIDER_DERIVED);
    }

    @Test
    void greetingFastPathRemainsAheadOfInputAndUnresolvedIntentPolicies() {
        AtomicInteger modelCalls = new AtomicInteger();
        GoalResolver resolver = resolver((input, deadline, modelExecution) -> {
            modelCalls.incrementAndGet();
            throw new AssertionError("greeting must stay on the social fast path");
        }, command -> generalProposal(
                GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION));

        ResolvedGoalSet result = resolver.resolve(
                freeText("你好！"), context(), deadline());

        assertThat(result.getKind()).isEqualTo(ResolvedGoalSet.Kind.CONVERSATIONAL);
        assertThat(result.getMessage().orElseThrow())
                .contains("你好，我可以介绍、比较或推荐公开项目");
        assertThat(modelCalls).hasValue(0);
    }

    @Test
    void reportsTypedScopeRejectionAsSemanticWithoutVisitorOrProposalText() {
        List<DiagnosticEvent> events = new ArrayList<>();
        UserGoalProposal proposal = recommendationProposal(2, Set.of());
        GoalResolver resolver = new GoalResolver(
                (input, deadline, modelExecution) -> GoalInterpretationResult.semanticRoute(
                        SemanticRouteProposal.standardGoal(proposal)),
                command -> proposal, new GoalInterpretationInputFactory(),
                new SafeConversationalFastPath(), new SemanticRouteValidator(),
                new GoalBoundaryPolicy(), new ModelOutputDiagnostics(events::add));
        GoalResolutionContext generalOnly = new GoalResolutionContext(
                context().getPublicSubjects(), Set.of(GoalKind.GENERAL_EXPLANATION));

        ResolvedGoalSet result = resolver.resolve(
                freeText("访客原文 sentinel"), generalOnly, deadline());

        assertThat(result.getKind()).isEqualTo(ResolvedGoalSet.Kind.CAPABILITY_UNAVAILABLE);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getFields().get("failure.layer")).isEqualTo("SEMANTIC");
            assertThat(event.getFields().get("failure.code"))
                    .isEqualTo("OUTPUT_SEMANTIC_REJECTED");
            assertThat(event.toString()).doesNotContain("访客原文", "sentinel");
        });
    }

    @Test
    void freeTextCallsOnlyGoalInterpretationPortOnce() {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger reviewedCalls = new AtomicInteger();
        AtomicReference<TurnDeadline> receivedDeadline = new AtomicReference<>();
        AtomicReference<ResolvedModelExecution> receivedExecution =
                new AtomicReference<>();
        UserGoalProposal proposal = generalProposal(GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION);
        GoalResolver resolver = resolver((input, deadline, modelExecution) -> {
            modelCalls.incrementAndGet();
            receivedDeadline.set(deadline);
            receivedExecution.set(modelExecution);
            return GoalInterpretationResult.semanticRoute(
                    SemanticRouteProposal.standardGoal(proposal));
        }, command -> {
            reviewedCalls.incrementAndGet();
            return proposal;
        });

        TurnDeadline deadline = deadline();
        ResolvedModelExecution selected = ResolvedModelExecution.none();
        ResolvedGoalSet result = resolver.resolve(
                freeText("解释幂等"), context(), deadline, selected);

        assertThat(result.getKind()).isEqualTo(ResolvedGoalSet.Kind.GOALS);
        assertThat(result.getGoalProposal()).contains(proposal);
        assertThat(modelCalls).hasValue(1);
        assertThat(reviewedCalls).hasValue(0);
        assertThat(receivedDeadline).hasValue(deadline);
        assertThat(receivedExecution).hasValue(selected);
    }

    @Test
    void authenticatedServerSemanticStateReachesInterpretationAsTypedAuthority() {
        AtomicReference<GoalInterpretationInput> received = new AtomicReference<>();
        UserGoalProposal proposal = generalProposal(
                GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION);
        GoalResolver resolver = resolver((input, deadline, modelExecution) -> {
            received.set(input);
            return GoalInterpretationResult.semanticRoute(
                    SemanticRouteProposal.standardGoal(proposal));
        }, command -> proposal);
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
                java.time.Instant.parse("2026-08-24T05:00:00Z"));

        resolver.resolve(freeText("解释幂等"), context(), deadline(), state);

        assertThat(received.get().getRecentSemanticState()).isSameAs(state);
        assertThat(received.get().getRecentMessages()).isEmpty();
    }

    @Test
    void presetContinueAndClarificationUseReviewedSourceWithoutModelCall() {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger reviewedCalls = new AtomicInteger();
        UserGoalProposal proposal = generalProposal(GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION);
        GoalResolver resolver = resolver((input, deadline, modelExecution) -> {
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
        GoalResolver resolver = resolver((input, deadline, modelExecution) -> {
            throw new GoalInterpretationUnavailableException();
        }, command -> generalProposal(GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION));

        ResolvedGoalSet result = resolver.resolve(
                freeText("请随便介绍一下"), context(), deadline());

        assertThat(result.getKind()).isEqualTo(ResolvedGoalSet.Kind.CAPABILITY_UNAVAILABLE);
    }

    @Test
    void providerFailureDoesNotUseReviewedAliasAsANaturalLanguageFallback() {
        GoalResolver resolver = resolver((input, deadline, modelExecution) -> {
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
        GoalResolver resolver = resolver((input, deadline, modelExecution) -> {
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
                (input, deadline, modelExecution) -> GoalInterpretationResult.semanticRoute(
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
        GoalResolver resolver = resolver((input, deadline, modelExecution) -> {
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
                5, Set.of("CAPABILITY_POSTGRESQL"));
        GoalResolver resolver = resolver((input, deadline, modelExecution) -> {
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
        assertThat(parameters.getConstraints()).containsExactly("CAPABILITY_POSTGRESQL");
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void negatedOrConflictingRecommendationConstraintDefersToProvider() {
        AtomicInteger modelCalls = new AtomicInteger();
        UserGoalProposal providerProposal = recommendationProposal(2, Set.of());
        GoalResolver resolver = resolver((input, deadline, modelExecution) -> {
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
        GoalResolver resolver = resolver((input, deadline, modelExecution) -> {
            modelCalls.incrementAndGet();
            return GoalInterpretationResult.conversational("按比较意图继续处理");
        }, command -> generalProposal(GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION));

        ResolvedGoalSet result = resolver.resolve(
                freeText("不用推荐项目，直接比较 A 和 B"), context(), deadline());

        assertThat(modelCalls).hasValue(1);
        assertThat(result.getKind()).isEqualTo(ResolvedGoalSet.Kind.CONVERSATIONAL);
        assertThat(result.getMessageSource())
                .isEqualTo(ResolvedGoalSet.MessageSource.PROVIDER_DERIVED);
        assertThat(result.getGoalProposal()).isEmpty();
    }

    @Test
    void greetingAndThanksAreConversationalBeforeProvider() {
        AtomicInteger modelCalls = new AtomicInteger();
        GoalResolver resolver = resolver((input, deadline, modelExecution) -> {
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
                new SafeConversationalFastPath(), new UnresolvedIntentPolicy(),
                new SemanticRouteValidator(),
                new GoalBoundaryPolicy());
    }

    private ConversationSemanticState recentSemanticState() {
        return new ConversationSemanticState(
                "public-1", List.of(new ConversationSemanticState.GoalSummary(
                "goal-1", GoalKind.PORTFOLIO_FACT,
                List.of(new ConversationSemanticState.Subject(
                        GoalSubjectReference.Kind.PROJECT, "sql-audit")),
                Set.of(GoalRequestedOutput.SOLUTION),
                Set.of(UserGoalProposal.Facet.SOLUTION),
                UserGoalProposal.Depth.STANDARD, Set.of(), null, Set.of(),
                List.of(new ConversationSemanticState.SectionReference(
                        "section-goal-1-1", AnswerSectionType.SOLUTION)))),
                java.time.Instant.parse("2026-08-24T05:00:00Z"));
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
        return new AgentTurnCommand.Ask(UUID.randomUUID(), AgentTurnCommand.ModelSelection.none(),
                new AgentTurnCommand.FreeText(text),
                AgentTurnCommand.SurfaceContext.empty(), ConversationWindow.empty());
    }

    private AgentTurnCommand preset() {
        return new AgentTurnCommand.Ask(UUID.randomUUID(),
                AgentTurnCommand.ModelSelection.none(),
                new AgentTurnCommand.Preset("question-sql-audit", "pcv1-0123456789abcdef"),
                AgentTurnCommand.SurfaceContext.empty(), ConversationWindow.empty());
    }

    private AgentTurnCommand continuation() {
        return new AgentTurnCommand.Continue(
                UUID.randomUUID(), AgentTurnCommand.ModelSelection.none(),
                AgentTurnCommand.ContinueOperation.ROUTE_IN_CONTEXT,
                "context_opaque", null, "继续", null,
                AgentTurnCommand.SurfaceContext.empty(),
                ConversationWindow.empty());
    }

    private AgentTurnCommand clarification() {
        return new AgentTurnCommand.ResolveClarification(
                UUID.randomUUID(), AgentTurnCommand.ModelSelection.none(),
                "clarification_opaque",
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
