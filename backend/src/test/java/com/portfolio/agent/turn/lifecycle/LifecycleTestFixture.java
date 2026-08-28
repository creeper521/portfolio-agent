package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.capability.portfolio.knowledge.RuntimeAnswerContent;
import com.portfolio.agent.turn.capability.portfolio.knowledge.PortfolioKnowledgeGateway;
import com.portfolio.agent.turn.continuation.ContextMutationPlanner;
import com.portfolio.agent.turn.continuation.ConversationSessionResolver;
import com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore;
import com.portfolio.agent.turn.execution.SemanticTurnEngine;
import com.portfolio.agent.turn.planning.GoalResolver;
import com.portfolio.agent.turn.planning.ResolvedGoalSet;
import com.portfolio.agent.turn.planning.SemanticPlanCompiler;
import com.portfolio.agent.turn.projection.PublicAgentTurnProjector;
import com.portfolio.agent.infrastructure.model.ModelExecutionResolver;
import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class LifecycleTestFixture {
    static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");
    private LifecycleTestFixture() { }

    static AgentTurnLifecycleService service(
            AgentStateStore store, ResolvedGoalSet resolved) {
        return service(store, resolved,
                new RequestFingerprintFactory(new byte[32]), sessionResolver(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    static AgentTurnLifecycleService service(
            AgentStateStore store, GoalResolver resolver) {
        return service(store, resolver,
                new RequestFingerprintFactory(new byte[32]), sessionResolver(),
                Clock.fixed(NOW, ZoneOffset.UTC), mock(SemanticPlanCompiler.class),
                mock(SemanticTurnEngine.class));
    }

    static AgentTurnLifecycleService service(
            AgentStateStore store, GoalResolver resolver,
            SemanticPlanCompiler compiler, SemanticTurnEngine engine) {
        return service(store, resolver,
                new RequestFingerprintFactory(new byte[32]), sessionResolver(),
                Clock.fixed(NOW, ZoneOffset.UTC), compiler, engine);
    }

    static AgentTurnLifecycleService service(
            AgentStateStore store, ResolvedGoalSet resolved,
            SemanticPlanCompiler compiler, SemanticTurnEngine engine) {
        GoalResolver resolver = mock(GoalResolver.class);
        when(resolver.resolve(
                any(), any(), any(), any(ResolvedModelExecution.class)))
                .thenReturn(resolved);
        return service(store, resolver,
                new RequestFingerprintFactory(new byte[32]), sessionResolver(),
                Clock.fixed(NOW, ZoneOffset.UTC), compiler, engine);
    }

    static AgentTurnLifecycleService service(
            AgentStateStore store, ResolvedGoalSet resolved,
            RequestFingerprintFactory fingerprints,
            ConversationSessionResolver sessions, Clock clock) {
        GoalResolver resolver = mock(GoalResolver.class);
        when(resolver.resolve(
                any(), any(), any(), any(ResolvedModelExecution.class)))
                .thenReturn(resolved);
        return service(store, resolver, fingerprints, sessions, clock,
                mock(SemanticPlanCompiler.class), mock(SemanticTurnEngine.class));
    }

    private static AgentTurnLifecycleService service(
            AgentStateStore store, GoalResolver resolver,
            RequestFingerprintFactory fingerprints,
            ConversationSessionResolver sessions, Clock clock,
            SemanticPlanCompiler compiler, SemanticTurnEngine engine) {
        return service(store, resolver, fingerprints, sessions, clock,
                compiler, engine, null);
    }

    static AgentTurnLifecycleService service(
            AgentStateStore store, GoalResolver resolver,
            ModelExecutionResolver modelExecutionResolver) {
        return service(
                store, resolver,
                new RequestFingerprintFactory(new byte[32]), sessionResolver(),
                Clock.fixed(NOW, ZoneOffset.UTC), mock(SemanticPlanCompiler.class),
                mock(SemanticTurnEngine.class), modelExecutionResolver);
    }

    private static AgentTurnLifecycleService service(
            AgentStateStore store, GoalResolver resolver,
            RequestFingerprintFactory fingerprints,
            ConversationSessionResolver sessions, Clock clock,
            SemanticPlanCompiler compiler, SemanticTurnEngine engine,
            ModelExecutionResolver modelExecutionResolver) {
        PortfolioKnowledgeGateway knowledge = mock(PortfolioKnowledgeGateway.class);
        RuntimeAnswerContent content = mock(RuntimeAnswerContent.class);
        when(content.getProjects()).thenReturn(java.util.List.of());
        when(content.getCases()).thenReturn(java.util.List.of());
        when(content.getContentVersion()).thenReturn("public-1");
        when(knowledge.getContent()).thenReturn(content);
        if (modelExecutionResolver == null) {
            return new AgentTurnLifecycleService(
                knowledge, resolver, compiler,
                engine, new PublicAgentTurnProjector(),
                new ContextMutationPlanner(() -> "context_handle_123"), store,
                fingerprints, sessions,
                java.util.concurrent.ForkJoinPool.commonPool(),
                clock, Duration.ofSeconds(10),
                Duration.ofSeconds(5), Duration.ofSeconds(1),
                Duration.ofMinutes(10));
        }
        return new AgentTurnLifecycleService(
                knowledge, resolver, compiler,
                engine, new PublicAgentTurnProjector(),
                new ContextMutationPlanner(() -> "context_handle_123"), store,
                fingerprints, sessions,
                java.util.concurrent.ForkJoinPool.commonPool(),
                clock, Duration.ofSeconds(10),
                Duration.ofSeconds(5), Duration.ofSeconds(1),
                Duration.ofMinutes(10), modelExecutionResolver);
    }

    static ConversationSessionResolver sessionResolver() {
        return new ConversationSessionResolver(
                new InMemoryConversationSessionStore(), new byte[32],
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(30));
    }
}
