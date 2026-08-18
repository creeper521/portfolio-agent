package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.turn.continuation.ContextMutationPlanner;
import com.portfolio.agent.turn.continuation.ConversationSessionResolver;
import com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore;
import com.portfolio.agent.turn.execution.SemanticTurnEngine;
import com.portfolio.agent.turn.planning.GoalResolver;
import com.portfolio.agent.turn.planning.ResolvedGoalSet;
import com.portfolio.agent.turn.planning.SemanticPlanCompiler;
import com.portfolio.agent.turn.projection.PublicAgentTurnProjector;

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
        PortfolioKnowledgeGateway knowledge = mock(PortfolioKnowledgeGateway.class);
        RuntimeAnswerContent content = mock(RuntimeAnswerContent.class);
        when(content.getProjects()).thenReturn(java.util.List.of());
        when(content.getCases()).thenReturn(java.util.List.of());
        when(content.getContentVersion()).thenReturn("public-1");
        when(knowledge.getContent()).thenReturn(content);
        GoalResolver resolver = mock(GoalResolver.class);
        when(resolver.resolve(any(), any())).thenReturn(resolved);
        return new AgentTurnLifecycleService(
                knowledge, resolver, mock(SemanticPlanCompiler.class),
                mock(SemanticTurnEngine.class), new PublicAgentTurnProjector(),
                new ContextMutationPlanner(() -> "context_handle_123"), store,
                new RequestFingerprintFactory(new byte[32]),
                sessionResolver(),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(10),
                Duration.ofSeconds(5), Duration.ofMinutes(10));
    }

    static ConversationSessionResolver sessionResolver() {
        return new ConversationSessionResolver(
                new InMemoryConversationSessionStore(), new byte[32],
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(30));
    }
}
