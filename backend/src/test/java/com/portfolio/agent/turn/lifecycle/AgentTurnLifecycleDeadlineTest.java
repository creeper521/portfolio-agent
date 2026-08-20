package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.capability.portfolio.knowledge.RuntimeAnswerContent;
import com.portfolio.agent.turn.capability.portfolio.knowledge.PortfolioKnowledgeGateway;
import com.portfolio.agent.turn.continuation.ContextMutationPlanner;
import com.portfolio.agent.turn.continuation.ConversationSessionResolver;
import com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore;
import com.portfolio.agent.turn.execution.SemanticTurnEngine;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.GoalResolver;
import com.portfolio.agent.turn.planning.ResolvedGoalSet;
import com.portfolio.agent.turn.planning.SemanticPlanCompiler;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.projection.PublicAgentTurnProjector;
import com.portfolio.agent.turn.state.memory.InMemoryTurnExecutionStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AgentTurnLifecycleDeadlineTest {

    @Test
    void slowGoalInterpretationCannotOutliveTurnDeadline() {
        Duration turnTimeout = Duration.ofMillis(200);
        Duration settlementReserve = Duration.ofMillis(20);
        Clock clock = Clock.systemUTC();
        GoalResolver resolver = mock(GoalResolver.class);
        AtomicInteger interpretations = new AtomicInteger();
        when(resolver.resolve(any(), any(), any())).thenAnswer(invocation -> {
            interpretations.incrementAndGet();
            TurnDeadline deadline = invocation.getArgument(2);
            while (!deadline.isExpired()) {
                LockSupport.parkNanos(Duration.ofMillis(5).toNanos());
            }
            return ResolvedGoalSet.capabilityUnavailable("目标解释已超过本轮预算。");
        });
        AgentTurnLifecycleService service = service(
                resolver, clock, turnTimeout, settlementReserve);

        AgentTurnCommand command = new AgentTurnCommand.Ask(
                UUID.randomUUID(), new AgentTurnCommand.FreeText("解释幂等"),
                AgentTurnCommand.SurfaceContext.empty(), ConversationWindow.empty());
        long startedAt = System.nanoTime();
        AgentTurnLifecycleService.Result result = service.execute(null, command);
        AgentTurnLifecycleService.Result replay = service.execute(null, command);

        assertThat(result.turn()).isInstanceOf(PublicAgentTurn.CapabilityUnavailable.class);
        assertThat(result.status()).isEqualTo(AgentTurnLifecycleService.Status.COMPLETED);
        assertThat(replay.status()).isEqualTo(AgentTurnLifecycleService.Status.REPLAY);
        assertThat(interpretations).hasValue(1);
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                .isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void slowClaimConsumesTheOriginalTurnBudgetAndUsesTheSameLeaseStart() {
        Duration turnTimeout = Duration.ofMillis(200);
        Clock clock = Clock.systemUTC();
        AgentStateStore store = mock(AgentStateStore.class);
        AtomicReference<Instant> claimStart = new AtomicReference<>();
        AtomicReference<Duration> lease = new AtomicReference<>();
        AtomicReference<TurnDeadline> goalDeadline = new AtomicReference<>();
        when(store.claim(any(), any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            claimStart.set(invocation.getArgument(4));
            lease.set(invocation.getArgument(5));
            Thread.sleep(120);
            return TurnExecutionStore.ClaimResult.claimed();
        });
        when(store.complete(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(true);
        GoalResolver resolver = mock(GoalResolver.class);
        when(resolver.resolve(any(), any(), any())).thenAnswer(invocation -> {
            goalDeadline.set(invocation.getArgument(2));
            return ResolvedGoalSet.conversational("你好");
        });
        AgentTurnLifecycleService service = service(
                resolver, store, clock, turnTimeout, Duration.ofMillis(20));

        AgentTurnLifecycleService.Result result = service.execute(null, ask());

        assertThat(result.status()).isEqualTo(AgentTurnLifecycleService.Status.COMPLETED);
        assertThat(goalDeadline.get().getExpiresAt())
                .isEqualTo(claimStart.get().plusMillis(180));
        assertThat(goalDeadline.get().remainingMillis()).isLessThan(100);
        assertThat(lease).hasValue(Duration.ofSeconds(35));
    }

    @Test
    void blockingSettlementIsCancelledWithinTheAbsoluteTurnBudget() throws Exception {
        AgentStateStore store = mock(AgentStateStore.class);
        when(store.claim(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(TurnExecutionStore.ClaimResult.claimed());
        CountDownLatch settlementStarted = new CountDownLatch(1);
        AtomicReference<Boolean> settlementInterrupted = new AtomicReference<>(false);
        when(store.complete(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    settlementStarted.countDown();
                    while (true) {
                        LockSupport.parkNanos(Duration.ofMillis(5).toNanos());
                        if (Thread.interrupted()) {
                            settlementInterrupted.set(true);
                            return false;
                        }
                    }
                });
        when(store.cancel(any(), any(), any())).thenReturn(true);
        GoalResolver resolver = mock(GoalResolver.class);
        when(resolver.resolve(any(), any(), any()))
                .thenReturn(ResolvedGoalSet.conversational("你好"));
        ExecutorService stateExecutor = Executors.newSingleThreadExecutor();
        AgentTurnLifecycleService service = service(
                resolver, store, stateExecutor, Clock.systemUTC(),
                Duration.ofMillis(200), Duration.ofMillis(20));

        long startedAt = System.nanoTime();
        AgentTurnLifecycleService.Result result = service.execute(null, ask());
        try {
            Future<Boolean> marker = stateExecutor.submit(() -> true);
            assertThat(marker.get(200, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            stateExecutor.shutdownNow();
        }

        assertThat(settlementStarted.await(100, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(settlementInterrupted.get()).isTrue();
        assertThat(result.status()).isEqualTo(AgentTurnLifecycleService.Status.COMPLETED);
        assertThat(result.settlementFailed()).isTrue();
        assertThat(result.turn()).isInstanceOf(PublicAgentTurn.Conversational.class);
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                .isLessThan(Duration.ofSeconds(1));
        verify(store, never()).cancel(any(), any(), any());
        verify(store, never()).find(any());
    }

    @Test
    void claimPastTheDeadlineIsInterruptedAndNeverEntersGoalOrEngine() throws Exception {
        AgentStateStore store = mock(AgentStateStore.class);
        AtomicReference<Boolean> claimInterrupted = new AtomicReference<>(false);
        when(store.claim(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    try {
                        new CountDownLatch(1).await();
                        return TurnExecutionStore.ClaimResult.claimed();
                    } catch (InterruptedException interrupted) {
                        claimInterrupted.set(true);
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("claim interrupted", interrupted);
                    }
                });
        GoalResolver resolver = mock(GoalResolver.class);
        ExecutorService stateExecutor = Executors.newSingleThreadExecutor();
        AgentTurnLifecycleService service = service(
                resolver, store, stateExecutor, Clock.systemUTC(),
                Duration.ofMillis(200), Duration.ofMillis(20));

        long startedAt = System.nanoTime();
        AgentTurnLifecycleService.Result result = service.execute(null, ask());
        try {
            Future<Boolean> marker = stateExecutor.submit(() -> true);
            assertThat(marker.get(200, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            stateExecutor.shutdownNow();
        }

        assertThat(result.status()).isEqualTo(AgentTurnLifecycleService.Status.STORE_UNAVAILABLE);
        assertThat(claimInterrupted.get()).isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                .isLessThan(Duration.ofSeconds(1));
        verifyNoInteractions(resolver);
        verify(store, never()).cancel(any(), any(), any());
        verify(store, never()).find(any());
    }

    @Test
    void timedOutWaiterCannotCancelTheOriginalRequestOwner() throws Exception {
        AgentStateStore store = mock(AgentStateStore.class);
        AtomicInteger claims = new AtomicInteger();
        AtomicReference<PublicAgentTurn> storedTurn = new AtomicReference<>();
        AtomicReference<Boolean> waiterInterrupted = new AtomicReference<>(false);
        when(store.claim(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    int call = claims.incrementAndGet();
                    if (call == 1) return TurnExecutionStore.ClaimResult.claimed();
                    if (call == 2) {
                        try {
                            new CountDownLatch(1).await();
                        } catch (InterruptedException interrupted) {
                            waiterInterrupted.set(true);
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("waiter interrupted", interrupted);
                        }
                    }
                    return TurnExecutionStore.ClaimResult.replay(storedTurn.get());
                });
        when(store.complete(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    storedTurn.set(invocation.getArgument(2));
                    return true;
                });
        CountDownLatch ownerInGoal = new CountDownLatch(1);
        CountDownLatch releaseOwner = new CountDownLatch(1);
        GoalResolver ownerResolver = mock(GoalResolver.class);
        when(ownerResolver.resolve(any(), any(), any())).thenAnswer(invocation -> {
            ownerInGoal.countDown();
            releaseOwner.await(1, TimeUnit.SECONDS);
            return ResolvedGoalSet.conversational("原 owner 完成");
        });
        GoalResolver waiterResolver = mock(GoalResolver.class);
        Clock clock = Clock.systemUTC();
        ConversationSessionResolver sessions = new ConversationSessionResolver(
                new InMemoryConversationSessionStore(), new byte[32],
                clock, Duration.ofMinutes(30));
        ExecutorService ownerExecutor = Executors.newSingleThreadExecutor();
        ExecutorService waiterExecutor = Executors.newSingleThreadExecutor();
        AgentTurnLifecycleService owner = service(
                ownerResolver, store, sessions, ownerExecutor, clock,
                Duration.ofSeconds(1), Duration.ofMillis(100));
        AgentTurnLifecycleService waiter = service(
                waiterResolver, store, sessions, waiterExecutor, clock,
                Duration.ofMillis(150), Duration.ofMillis(20));
        AgentTurnCommand command = ask();
        AtomicReference<AgentTurnLifecycleService.Result> ownerResult = new AtomicReference<>();
        Thread ownerThread = Thread.ofPlatform().start(
                () -> ownerResult.set(owner.execute(null, command)));
        try {
            assertThat(ownerInGoal.await(1, TimeUnit.SECONDS)).isTrue();

            AgentTurnLifecycleService.Result waiterResult = waiter.execute(null, command);
            releaseOwner.countDown();
            ownerThread.join(1_000);
            AgentTurnLifecycleService.Result replay = owner.execute(null, command);

            assertThat(waiterResult.status())
                    .isEqualTo(AgentTurnLifecycleService.Status.STORE_UNAVAILABLE);
            assertThat(waiterInterrupted.get()).isTrue();
            assertThat(ownerResult.get().status())
                    .isEqualTo(AgentTurnLifecycleService.Status.COMPLETED);
            assertThat(replay.status()).isEqualTo(AgentTurnLifecycleService.Status.REPLAY);
            assertThat(((PublicAgentTurn.Conversational) replay.turn()).getMessage())
                    .isEqualTo("原 owner 完成");
            assertThat(claims).hasValue(3);
            verifyNoInteractions(waiterResolver);
            verify(store, never()).cancel(any(), any(), any());
            verify(store, never()).find(any());
        } finally {
            releaseOwner.countDown();
            ownerExecutor.shutdownNow();
            waiterExecutor.shutdownNow();
        }
    }

    private AgentTurnCommand ask() {
        return new AgentTurnCommand.Ask(
                UUID.randomUUID(), new AgentTurnCommand.FreeText("你好"),
                AgentTurnCommand.SurfaceContext.empty(), ConversationWindow.empty());
    }

    private AgentTurnLifecycleService service(
            GoalResolver resolver, Clock clock,
            Duration turnTimeout, Duration settlementReserve) {
        InMemoryTurnExecutionStore store = new InMemoryTurnExecutionStore();
        return service(resolver, store, clock, turnTimeout, settlementReserve);
    }

    private AgentTurnLifecycleService service(
            GoalResolver resolver, AgentStateStore store, Clock clock,
            Duration turnTimeout, Duration settlementReserve) {
        return service(
                resolver, store, java.util.concurrent.ForkJoinPool.commonPool(),
                clock, turnTimeout, settlementReserve);
    }

    private AgentTurnLifecycleService service(
            GoalResolver resolver, AgentStateStore store,
            ExecutorService stateExecutor, Clock clock,
            Duration turnTimeout, Duration settlementReserve) {
        ConversationSessionResolver sessions = new ConversationSessionResolver(
                new InMemoryConversationSessionStore(), new byte[32],
                clock, Duration.ofMinutes(30));
        return service(
                resolver, store, sessions, stateExecutor, clock,
                turnTimeout, settlementReserve);
    }

    private AgentTurnLifecycleService service(
            GoalResolver resolver, AgentStateStore store,
            ConversationSessionResolver sessions,
            ExecutorService stateExecutor, Clock clock,
            Duration turnTimeout, Duration settlementReserve) {
        PortfolioKnowledgeGateway knowledge = mock(PortfolioKnowledgeGateway.class);
        RuntimeAnswerContent content = mock(RuntimeAnswerContent.class);
        when(content.getProjects()).thenReturn(java.util.List.of());
        when(content.getCases()).thenReturn(java.util.List.of());
        when(content.getContentVersion()).thenReturn("public-1");
        when(knowledge.getContent()).thenReturn(content);
        return new AgentTurnLifecycleService(
                knowledge, resolver, mock(SemanticPlanCompiler.class),
                mock(SemanticTurnEngine.class), new PublicAgentTurnProjector(),
                new ContextMutationPlanner(() -> "context_handle_123"), store,
                new RequestFingerprintFactory(new byte[32]),
                sessions,
                stateExecutor,
                clock, Duration.ofSeconds(35), turnTimeout,
                settlementReserve, Duration.ofMinutes(30));
    }
}
