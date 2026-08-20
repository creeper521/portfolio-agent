package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerKnowledge;
import com.portfolio.agent.turn.capability.portfolio.knowledge.RuntimeAnswerContent;
import com.portfolio.agent.turn.capability.portfolio.knowledge.PortfolioKnowledgeGateway;
import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContextMutationPlanner;
import com.portfolio.agent.turn.execution.SemanticTurnEngine;
import com.portfolio.agent.turn.planning.ClarificationProposal;
import com.portfolio.agent.turn.planning.BlockedGoalTemplate;
import com.portfolio.agent.turn.planning.GoalResolver;
import com.portfolio.agent.turn.planning.PlanCompilationResult;
import com.portfolio.agent.turn.planning.ResolvedGoalSet;
import com.portfolio.agent.turn.planning.SemanticPlanCompiler;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.projection.PublicAgentTurnProjector;
import com.portfolio.agent.turn.state.memory.InMemoryTurnExecutionStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTurnLifecycleClarificationRecoveryTest {
    private final java.util.Map<AgentStateStore,
            com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore>
            sessionStores = new java.util.IdentityHashMap<>();

    @Test
    void clarificationAnswerRestoresSameRecommendationWithoutReinterpretation() throws Exception {
        Clock clock = Clock.fixed(LifecycleTestFixture.NOW, ZoneOffset.UTC);
        ClarificationStore clarificationStore = new ClarificationStore(
                clock, Duration.ofMinutes(5));
        InMemoryTurnExecutionStore store = inMemoryStore(clarificationStore, clock);
        GoalResolver resolver = mock(GoalResolver.class);
        BlockedGoalTemplate blocked = BlockedGoalTemplate.recommendation(
                null, Set.of("BACKEND"), ClarificationProposal.Field.REQUESTED_SIZE);
        when(resolver.resolve(any(), any(), any())).thenReturn(
                ResolvedGoalSet.clarification(new ClarificationProposal(
                        ClarificationProposal.Field.REQUESTED_SIZE,
                        "provider text must not be persisted VISITOR_SENTINEL", blocked)));
        SemanticPlanCompiler compiler = mock(SemanticPlanCompiler.class);
        when(compiler.compile(any(), any(), any()))
                .thenReturn(PlanCompilationResult.rejected("stop-after-capture"));
        AgentTurnLifecycleService service = service(store, resolver, compiler, clock);

        UUID askRequestId = UUID.randomUUID();
        AgentTurnCommand.Ask ask = new AgentTurnCommand.Ask(
                askRequestId, new AgentTurnCommand.FreeText("推荐 8 个后端项目"),
                null, null);
        AgentTurnLifecycleService.Result first = service.execute(null, ask);
        PublicAgentTurn.Clarification clarification =
                (PublicAgentTurn.Clarification) first.turn();
        assertThat(clarification.getClarification().getPrompt())
                .isEqualTo("请选择要推荐的项目数量（1—5 个）。")
                .doesNotContain("VISITOR_SENTINEL");
        String persisted = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                store.find(first.turn().getRequestId()).orElseThrow().getChallenges());
        byte[] firstChallengeHash = store.find(first.turn().getRequestId()).orElseThrow()
                .getChallenges().getFirst().resumeTokenHash();
        assertThat(persisted).doesNotContain(
                "VISITOR_SENTINEL", "推荐 8 个后端项目", "inputAnchor");

        AgentTurnLifecycleService.Result replay = service.execute(null, ask);
        assertThat(replay.status()).isEqualTo(AgentTurnLifecycleService.Status.REPLAY);
        assertThat(replay.conversation().conversationId())
                .isEqualTo(first.conversation().conversationId());
        assertThat(replay.conversation().resumeToken())
                .isNotEqualTo(first.conversation().resumeToken());
        byte[] replayChallengeHash = store.find(first.turn().getRequestId()).orElseThrow()
                .getChallenges().getFirst().resumeTokenHash();
        assertThat(java.util.Arrays.equals(firstChallengeHash, replayChallengeHash)).isFalse();
        AgentTurnLifecycleService.Result rejectedOldToken = service.execute(
                first.conversation().resumeToken(),
                new AgentTurnCommand.ResolveClarification(
                        UUID.randomUUID(),
                        clarification.getClarification().getClarificationId(),
                        new AgentTurnCommand.ChoiceAnswer("choice_size_3"), null, null));
        assertThat(rejectedOldToken.status())
                .isEqualTo(AgentTurnLifecycleService.Status.UNAUTHORIZED);

        AgentTurnLifecycleService.Result resolved = service.execute(
                replay.conversation().resumeToken(),
                new AgentTurnCommand.ResolveClarification(
                        UUID.randomUUID(),
                        clarification.getClarification().getClarificationId(),
                        new AgentTurnCommand.ChoiceAnswer("choice_size_3"), null, null));

        assertThat(resolved.status()).isEqualTo(AgentTurnLifecycleService.Status.COMPLETED);
        ArgumentCaptor<UserGoalProposal> proposal = ArgumentCaptor.forClass(UserGoalProposal.class);
        verify(compiler).compile(proposal.capture(), any(), any());
        UserGoalProposal.PortfolioRecommendationParameters parameters =
                (UserGoalProposal.PortfolioRecommendationParameters) proposal.getValue()
                        .getGoals().getFirst().getParameters();
        assertThat(parameters.getRequestedSize()).isEqualTo(3);
        assertThat(parameters.getConstraints()).containsExactly("BACKEND");
        verify(resolver).resolve(any(), any(), any());
    }

    @Test
    void blockingClarificationConsumeCannotExceedTurnDeadline() throws Exception {
        Clock clock = Clock.systemUTC();
        java.util.concurrent.atomic.AtomicBoolean interrupted =
                new java.util.concurrent.atomic.AtomicBoolean();
        AgentStateStore store = mock(AgentStateStore.class);
        when(store.claim(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(TurnExecutionStore.ClaimResult.claimed());
        when(store.consumeClarification(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    try {
                        Thread.sleep(5_000);
                    } catch (InterruptedException expected) {
                        interrupted.set(true);
                        throw expected;
                    }
                    return ClarificationStore.ConsumeResult.of(ClarificationStore.Status.NOT_FOUND);
                });
        when(store.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(true);
        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newCachedThreadPool();
        AgentTurnLifecycleService service = service(
                store, mock(GoalResolver.class), mock(SemanticPlanCompiler.class),
                clock, Duration.ofMillis(250), executor);

        long startedAt = System.nanoTime();
        AgentTurnLifecycleService.Result result = service.execute(
                null, new AgentTurnCommand.ResolveClarification(
                        UUID.randomUUID(), "clarification_blocking_1",
                        new AgentTurnCommand.ChoiceAnswer("choice_size_2"), null, null));

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                .isLessThan(Duration.ofSeconds(1));
        assertThat(result.status()).isEqualTo(AgentTurnLifecycleService.Status.COMPLETED);
        assertThat(result.turn()).isInstanceOf(PublicAgentTurn.CapabilityUnavailable.class);
        org.assertj.core.api.Assertions.assertThat(interrupted).isTrue();
        executor.shutdownNow();
    }

    @Test
    void blockingContinuationReadCannotExceedTurnDeadline() {
        Clock clock = Clock.systemUTC();
        java.util.concurrent.atomic.AtomicBoolean interrupted =
                new java.util.concurrent.atomic.AtomicBoolean();
        AgentStateStore store = mock(AgentStateStore.class);
        when(store.claim(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(TurnExecutionStore.ClaimResult.claimed());
        when(store.findContext(any(), any(), any(), any())).thenAnswer(invocation -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException expected) {
                interrupted.set(true);
                throw expected;
            }
            return java.util.Optional.empty();
        });
        when(store.complete(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(true);
        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newCachedThreadPool();
        AgentTurnLifecycleService service = service(
                store, mock(GoalResolver.class), mock(SemanticPlanCompiler.class),
                clock, Duration.ofMillis(250), executor);

        long startedAt = System.nanoTime();
        AgentTurnLifecycleService.Result result = service.execute(
                null, new AgentTurnCommand.Continue(
                        UUID.randomUUID(), "context_blocking_123", null,
                        "继续", null, null));

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                .isLessThan(Duration.ofSeconds(1));
        assertThat(result.turn()).isInstanceOf(PublicAgentTurn.CapabilityUnavailable.class);
        assertThat(interrupted).isTrue();
        executor.shutdownNow();
    }

    @Test
    void emptyPublicCatalogDoesNotCreateUnresumableSubjectChallenge() {
        Clock clock = Clock.fixed(LifecycleTestFixture.NOW, ZoneOffset.UTC);
        InMemoryTurnExecutionStore store = inMemoryStore(
                new ClarificationStore(clock, Duration.ofMinutes(5)), clock);
        GoalResolver resolver = mock(GoalResolver.class);
        BlockedGoalTemplate blocked = new BlockedGoalTemplate(
                com.portfolio.agent.turn.planning.GoalKind.PORTFOLIO_FACT,
                java.util.List.of(),
                java.util.Set.of(com.portfolio.agent.turn.planning.GoalRequestedOutput.OVERVIEW),
                java.util.Set.of(UserGoalProposal.Facet.OVERVIEW), java.util.Set.of(),
                null, java.util.Set.of(), ClarificationProposal.Field.SUBJECT,
                java.util.Set.of(ClarificationProposal.Field.SUBJECT), 1);
        when(resolver.resolve(any(), any(), any())).thenReturn(
                ResolvedGoalSet.clarification(new ClarificationProposal(
                        ClarificationProposal.Field.SUBJECT, "provider prompt", blocked)));
        AgentTurnLifecycleService service = service(
                store, resolver, mock(SemanticPlanCompiler.class), clock);

        AgentTurnLifecycleService.Result result = service.execute(
                null, new AgentTurnCommand.Ask(
                        UUID.randomUUID(), new AgentTurnCommand.FreeText("这个项目"), null, null));

        assertThat(result.turn()).isInstanceOf(PublicAgentTurn.CapabilityUnavailable.class);
        assertThat(store.find(result.turn().getRequestId()).orElseThrow().getChallenges()).isEmpty();
    }

    @Test
    void twoStageClarificationAdvancesWithoutProviderOrFreshAsk() {
        Clock clock = Clock.fixed(LifecycleTestFixture.NOW, ZoneOffset.UTC);
        InMemoryTurnExecutionStore store = inMemoryStore(
                new ClarificationStore(clock, Duration.ofMinutes(5)), clock);
        GoalResolver resolver = mock(GoalResolver.class);
        BlockedGoalTemplate firstTemplate = new BlockedGoalTemplate(
                com.portfolio.agent.turn.planning.GoalKind.PORTFOLIO_FACT,
                java.util.List.of(), java.util.Set.of(),
                java.util.Set.of(UserGoalProposal.Facet.OVERVIEW), java.util.Set.of(),
                null, java.util.Set.of(), ClarificationProposal.Field.SUBJECT,
                java.util.Set.of(ClarificationProposal.Field.SUBJECT),
                java.util.List.of(ClarificationProposal.Field.OUTPUT), 1);
        when(resolver.resolve(any(), any(), any())).thenReturn(
                ResolvedGoalSet.clarification(new ClarificationProposal(
                        ClarificationProposal.Field.SUBJECT, "provider prompt", firstTemplate)));
        SemanticPlanCompiler compiler = mock(SemanticPlanCompiler.class);
        when(compiler.compile(any(), any(), any()))
                .thenReturn(PlanCompilationResult.rejected("captured"));
        com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerKnowledge project =
                mock(com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerKnowledge.class);
        when(project.getStableId()).thenReturn("project-a");
        when(project.getSlug()).thenReturn("project-a-slug");
        when(project.getTitle()).thenReturn("项目 A");
        AgentTurnLifecycleService service = serviceWithProjects(
                store, resolver, compiler, clock, java.util.List.of(project));

        AgentTurnLifecycleService.Result first = service.execute(
                null, new AgentTurnCommand.Ask(
                        UUID.randomUUID(), new AgentTurnCommand.FreeText("这个项目"), null, null));
        PublicAgentTurn.Clarification firstTurn = (PublicAgentTurn.Clarification) first.turn();
        String token = first.conversation().resumeToken();
        AgentTurnLifecycleService.Result second = service.execute(
                token, new AgentTurnCommand.ResolveClarification(
                        UUID.randomUUID(), firstTurn.getClarification().getClarificationId(),
                        new AgentTurnCommand.ChoiceAnswer("choice_subject_1"), null, null));
        PublicAgentTurn.Clarification secondTurn = (PublicAgentTurn.Clarification) second.turn();
        assertThat(secondTurn.getClarification().getFields()).hasSize(1);
        assertThat(secondTurn.getClarification().getPrompt())
                .isEqualTo("请选择期望的回答形式。");

        AgentTurnLifecycleService.Result third = service.execute(
                token, new AgentTurnCommand.ResolveClarification(
                        UUID.randomUUID(), secondTurn.getClarification().getClarificationId(),
                        new AgentTurnCommand.ChoiceAnswer("output_overview"), null, null));

        assertThat(third.status()).isEqualTo(AgentTurnLifecycleService.Status.COMPLETED);
        verify(resolver).resolve(any(), any(), any());
        verify(compiler).compile(any(), any(), any());
    }

    private AgentTurnLifecycleService service(
            AgentStateStore store,
            GoalResolver resolver,
            SemanticPlanCompiler compiler,
            Clock clock) {
        PortfolioKnowledgeGateway knowledge = mock(PortfolioKnowledgeGateway.class);
        RuntimeAnswerContent content = mock(RuntimeAnswerContent.class);
        when(content.getProjects()).thenReturn(java.util.List.of());
        when(content.getCases()).thenReturn(java.util.List.of());
        when(content.getContentVersion()).thenReturn("public-1");
        when(knowledge.getContent()).thenReturn(content);
        return new AgentTurnLifecycleService(
                knowledge, resolver, compiler, mock(SemanticTurnEngine.class),
                new PublicAgentTurnProjector(),
                new ContextMutationPlanner(() -> "context_handle_123"), store,
                new RequestFingerprintFactory(new byte[32]),
                sessionResolver(store, clock), ForkJoinPool.commonPool(),
                clock, Duration.ofSeconds(10), Duration.ofSeconds(5),
                Duration.ofSeconds(1), Duration.ofMinutes(10));
    }

    private InMemoryTurnExecutionStore inMemoryStore(
            ClarificationStore clarificationStore, Clock clock) {
        com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore sessions =
                new com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore();
        InMemoryTurnExecutionStore store = new InMemoryTurnExecutionStore(
                clarificationStore, Duration.ofMinutes(30),
                sessions, clock);
        sessionStores.put(store, sessions);
        return store;
    }

    private AgentTurnLifecycleService serviceWithProjects(
            AgentStateStore store,
            GoalResolver resolver,
            SemanticPlanCompiler compiler,
            Clock clock,
            java.util.List<com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerKnowledge> projects) {
        PortfolioKnowledgeGateway knowledge = mock(PortfolioKnowledgeGateway.class);
        RuntimeAnswerContent content = mock(RuntimeAnswerContent.class);
        when(content.getProjects()).thenReturn(projects);
        when(content.getCases()).thenReturn(java.util.List.of());
        when(content.getContentVersion()).thenReturn("public-1");
        when(knowledge.getContent()).thenReturn(content);
        return new AgentTurnLifecycleService(
                knowledge, resolver, compiler, mock(SemanticTurnEngine.class),
                new PublicAgentTurnProjector(),
                new ContextMutationPlanner(() -> "context_handle_123"), store,
                new RequestFingerprintFactory(new byte[32]),
                sessionResolver(store, clock), ForkJoinPool.commonPool(),
                clock, Duration.ofSeconds(10), Duration.ofSeconds(5),
                Duration.ofSeconds(1), Duration.ofMinutes(10));
    }

    private AgentTurnLifecycleService service(
            AgentStateStore store,
            GoalResolver resolver,
            SemanticPlanCompiler compiler,
            Clock clock,
            Duration turnTimeout) {
        return service(store, resolver, compiler, clock, turnTimeout,
                ForkJoinPool.commonPool());
    }

    private AgentTurnLifecycleService service(
            AgentStateStore store,
            GoalResolver resolver,
            SemanticPlanCompiler compiler,
            Clock clock,
            Duration turnTimeout,
            java.util.concurrent.ExecutorService executor) {
        PortfolioKnowledgeGateway knowledge = mock(PortfolioKnowledgeGateway.class);
        RuntimeAnswerContent content = mock(RuntimeAnswerContent.class);
        when(content.getProjects()).thenReturn(java.util.List.of());
        when(content.getCases()).thenReturn(java.util.List.of());
        when(content.getContentVersion()).thenReturn("public-1");
        when(knowledge.getContent()).thenReturn(content);
        return new AgentTurnLifecycleService(
                knowledge, resolver, compiler, mock(SemanticTurnEngine.class),
                new PublicAgentTurnProjector(),
                new ContextMutationPlanner(() -> "context_handle_123"), store,
                new RequestFingerprintFactory(new byte[32]),
                sessionResolver(store, clock), executor,
                clock, Duration.ofSeconds(10), turnTimeout,
                Duration.ofMillis(50), Duration.ofMinutes(10));
    }

    private com.portfolio.agent.turn.continuation.ConversationSessionResolver sessionResolver(
            AgentStateStore store, Clock clock) {
        com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore sessions =
                sessionStores.get(store);
        if (sessions == null) {
            sessions = new com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore();
        }
        return new com.portfolio.agent.turn.continuation.ConversationSessionResolver(
                sessions, new byte[32], clock, Duration.ofMinutes(30));
    }
}
