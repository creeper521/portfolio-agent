package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.state.memory.InMemoryTurnExecutionStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTurnLifecycleReplayTest {
    @Test void generalAnswerBodyIsLiveOnlyAndReplayDoesNotExecutePlanAgain() {
        String sentinel = "provider-general-body-sentinel";
        com.portfolio.agent.turn.planning.UserGoalProposal.InputAnchor anchor =
                new com.portfolio.agent.turn.planning.UserGoalProposal.InputAnchor(
                        "visitor-general-sentinel", 0);
        com.portfolio.agent.turn.planning.UserGoalProposal.ProposedGoal goal =
                new com.portfolio.agent.turn.planning.UserGoalProposal.ProposedGoal(
                        "general", com.portfolio.agent.turn.planning.GoalKind.GENERAL_EXPLANATION,
                        anchor, List.of(),
                        java.util.Set.of(com.portfolio.agent.turn.planning.GoalRequestedOutput.EXPLANATION),
                        com.portfolio.agent.turn.planning.GoalKnowledgeRequirement
                                .STABLE_GENERAL_EXPLANATION,
                        new com.portfolio.agent.turn.planning.UserGoalProposal
                                .GeneralExplanationParameters(
                                anchor,
                                com.portfolio.agent.turn.planning.UserGoalProposal.Depth.STANDARD));
        com.portfolio.agent.turn.planning.SemanticPlanCompiler compiler =
                new com.portfolio.agent.turn.planning.SemanticPlanCompiler(
                        new com.portfolio.agent.turn.planning.SemanticPlanValidator());
        com.portfolio.agent.turn.execution.SemanticTurnEngine engine =
                org.mockito.Mockito.mock(
                        com.portfolio.agent.turn.execution.SemanticTurnEngine.class);
        org.mockito.Mockito.when(engine.execute(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(generalOutcome(sentinel));
        InMemoryTurnExecutionStore store = new InMemoryTurnExecutionStore();
        AgentTurnLifecycleService service = LifecycleTestFixture.service(
                store,
                com.portfolio.agent.turn.planning.ResolvedGoalSet.goals(
                        new com.portfolio.agent.turn.planning.UserGoalProposal(List.of(goal))),
                compiler, engine);
        UUID requestId = UUID.randomUUID();
        AgentTurnCommand command = new AgentTurnCommand.Ask(
                requestId, AgentTurnCommand.ModelSelection.none(),
                new AgentTurnCommand.FreeText("visitor-general-sentinel"),
                null, null);

        AgentTurnLifecycleService.Result first = service.execute(null, command);
        AgentTurnLifecycleService.Result replay = service.execute(null, command);

        PublicAgentTurn.Answer answer = (PublicAgentTurn.Answer) first.turn();
        com.portfolio.agent.turn.projection.PublicPresentation.Sectioned presentation =
                (com.portfolio.agent.turn.projection.PublicPresentation.Sectioned)
                        answer.getAnswer().getGoalResults().getFirst().getPresentation();
        assertThat(presentation.getSections().getFirst().getContent()).isEqualTo(sentinel);
        assertThat(answer.getAnswer().getGoalResults().getFirst().getLabel())
                .isEqualTo("通用概念说明");
        assertThat(replay.turn()).isInstanceOf(PublicAgentTurn.CapabilityUnavailable.class);
        assertThat(((PublicAgentTurn.CapabilityUnavailable) replay.turn()).getCode())
                .isEqualTo("REPLAY_BODY_NOT_RETAINED");
        org.mockito.Mockito.verify(engine, org.mockito.Mockito.times(1)).execute(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test void providerBodyIsReturnedOnceButSameRequestReplaysBodyNotRetainedTerminal() {
        InMemoryTurnExecutionStore store = new InMemoryTurnExecutionStore();
        UUID requestId = UUID.randomUUID();
        String providerBody = "provider-body-访客隐私问题-sentinel-原文";
        java.util.concurrent.atomic.AtomicInteger providerCalls =
                new java.util.concurrent.atomic.AtomicInteger();
        AgentTurnCommand command = new AgentTurnCommand.Ask(
                requestId, AgentTurnCommand.ModelSelection.none(),
                new AgentTurnCommand.FreeText("访客隐私问题-sentinel-原文"),
                null, null);
        com.portfolio.agent.turn.planning.GoalResolver resolver =
                new com.portfolio.agent.turn.planning.GoalResolver(
                        (input, deadline, modelExecution) -> {
                            providerCalls.incrementAndGet();
                            return com.portfolio.agent.turn.planning.GoalInterpretationResult
                                    .conversational(providerBody);
                        },
                        ignored -> {
                            throw new AssertionError("reviewed goals are not used for free text");
                        },
                        new com.portfolio.agent.turn.planning.GoalInterpretationInputFactory(),
                        new com.portfolio.agent.turn.planning.SafeConversationalFastPath(),
                        new com.portfolio.agent.turn.planning.SemanticRouteValidator(),
                        new com.portfolio.agent.turn.planning.GoalBoundaryPolicy());
        AgentTurnLifecycleService service = LifecycleTestFixture.service(store, resolver);

        AgentTurnLifecycleService.Result first = service.execute(null, command);
        AgentTurnLifecycleService.Result replay = service.execute(null, command);

        assertThat(first.status()).isEqualTo(AgentTurnLifecycleService.Status.COMPLETED);
        assertThat(((PublicAgentTurn.Conversational) first.turn()).getMessage())
                .isEqualTo(providerBody);
        assertThat(replay.status()).isEqualTo(AgentTurnLifecycleService.Status.REPLAY);
        PublicAgentTurn.CapabilityUnavailable unavailable =
                (PublicAgentTurn.CapabilityUnavailable) replay.turn();
        assertThat(unavailable.getCode()).isEqualTo("REPLAY_BODY_NOT_RETAINED");
        assertThat(unavailable.getMessage()).isEqualTo("该回答未被保留，请重新提问。");
        assertThat(unavailable.isRetryable()).isFalse();
        assertThat(unavailable.getSuggestedActions()).isEmpty();
        assertThat(unavailable.getMessage()).doesNotContain("sentinel", "访客隐私问题");
        assertThat(providerCalls).hasValue(1);
    }

    @Test void completedRequestReturnsTheExactStoredPublicSnapshot() {
        InMemoryTurnExecutionStore store = new InMemoryTurnExecutionStore();
        UUID requestId = UUID.randomUUID();
        AgentTurnCommand command = new AgentTurnCommand.Ask(
                requestId, AgentTurnCommand.ModelSelection.none(),
                new AgentTurnCommand.FreeText("你好"), null, null);
        byte[] fingerprint = new RequestFingerprintFactory(new byte[32]).fingerprint(command);
        String conversationId = LifecycleTestFixture.sessionResolver()
                .resolve(null, requestId,
                        com.portfolio.agent.turn.execution.TurnDeadline.after(
                                Duration.ofSeconds(5), java.time.Clock.fixed(
                                        LifecycleTestFixture.NOW, java.time.ZoneOffset.UTC)))
                .conversationId();
        com.portfolio.agent.turn.continuation.ConversationSessionStore.Session session =
                new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                        conversationId, new byte[32], LifecycleTestFixture.NOW,
                        LifecycleTestFixture.NOW.plus(Duration.ofMinutes(30)));
        TurnExecutionStore.SessionAccess access = TurnExecutionStore.SessionAccess.tentative(session);
        store.claim(requestId, conversationId, RequestFingerprintSet.single(fingerprint), access,
                LifecycleTestFixture.NOW, Duration.ofSeconds(10),
                com.portfolio.agent.turn.execution.TurnDeadline.after(
                        Duration.ofSeconds(5), java.time.Clock.fixed(
                                LifecycleTestFixture.NOW, java.time.ZoneOffset.UTC)));
        PublicAgentTurn snapshot = new PublicAgentTurn.Conversational(requestId, "你好", List.of());
        store.complete(requestId, fingerprint, snapshot, List.of(), List.of(), session, access,
                LifecycleTestFixture.NOW.plusSeconds(1),
                com.portfolio.agent.turn.execution.TurnDeadline.after(
                        Duration.ofSeconds(5), java.time.Clock.fixed(
                                LifecycleTestFixture.NOW, java.time.ZoneOffset.UTC)));
        AgentTurnLifecycleService service = LifecycleTestFixture.service(
                store, com.portfolio.agent.turn.planning.ResolvedGoalSet.conversational("不应执行"));

        AgentTurnLifecycleService.Result result = service.execute(
                null, command);
        assertThat(result.status()).isEqualTo(AgentTurnLifecycleService.Status.REPLAY);
        assertThat(result.turn()).isSameAs(snapshot);
    }

    @Test void lostFirstResponseReplaysAcrossKeyRotationWithFreshTokenAndStableConversation() {
        java.time.Clock clock = java.time.Clock.fixed(
                LifecycleTestFixture.NOW, java.time.ZoneOffset.UTC);
        com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore sessions =
                new com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore();
        InMemoryTurnExecutionStore store = new InMemoryTurnExecutionStore(
                new com.portfolio.agent.turn.continuation.ClarificationStore(
                        clock, Duration.ofMinutes(5)), Duration.ofMinutes(30), sessions, clock);
        byte[] previousKey = new byte[32];
        java.util.Arrays.fill(previousKey, (byte) 1);
        byte[] currentKey = new byte[32];
        java.util.Arrays.fill(currentKey, (byte) 2);
        UUID requestId = UUID.randomUUID();
        AgentTurnCommand command = new AgentTurnCommand.Ask(
                requestId, AgentTurnCommand.ModelSelection.none(),
                new AgentTurnCommand.FreeText("你好"), null, null);
        AgentTurnLifecycleService oldService = LifecycleTestFixture.service(
                store, com.portfolio.agent.turn.planning.ResolvedGoalSet.conversational("你好"),
                new RequestFingerprintFactory(previousKey),
                new com.portfolio.agent.turn.continuation.ConversationSessionResolver(
                        sessions, previousKey, clock, Duration.ofMinutes(30)), clock);
        AgentTurnLifecycleService.Result first = oldService.execute(null, command);

        AgentTurnLifecycleService rotatedService = LifecycleTestFixture.service(
                store, com.portfolio.agent.turn.planning.ResolvedGoalSet.conversational("不应执行"),
                new RequestFingerprintFactory(currentKey, List.of(previousKey)),
                new com.portfolio.agent.turn.continuation.ConversationSessionResolver(
                        sessions, currentKey, List.of(previousKey), clock,
                        Duration.ofMinutes(30)), clock);
        AgentTurnLifecycleService.Result replay = rotatedService.execute(null, command);

        assertThat(first.status()).isEqualTo(AgentTurnLifecycleService.Status.COMPLETED);
        assertThat(replay.status()).isEqualTo(AgentTurnLifecycleService.Status.REPLAY);
        assertThat(replay.conversation().conversationId())
                .isEqualTo(first.conversation().conversationId());
        assertThat(replay.conversation().resumeToken())
                .isNotEqualTo(first.conversation().resumeToken());
        assertThat(rotatedService.currentConversation(first.conversation().resumeToken()).authenticated())
                .isFalse();
        assertThat(rotatedService.currentConversation(replay.conversation().resumeToken()).authenticated())
                .isTrue();
    }

    @Test void clearedConversationCannotBeReusedUntilAbsoluteExpiryThenGetsFreshToken() {
        MutableClock clock = new MutableClock(LifecycleTestFixture.NOW);
        com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore sessions =
                new com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore();
        InMemoryTurnExecutionStore store = new InMemoryTurnExecutionStore(
                new com.portfolio.agent.turn.continuation.ClarificationStore(
                        clock, Duration.ofMinutes(5)), Duration.ofMinutes(30), sessions, clock);
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 3);
        AgentTurnLifecycleService service = LifecycleTestFixture.service(
                store, com.portfolio.agent.turn.planning.ResolvedGoalSet.conversational("你好"),
                new RequestFingerprintFactory(key),
                new com.portfolio.agent.turn.continuation.ConversationSessionResolver(
                        sessions, key, clock, Duration.ofMinutes(30)), clock);
        UUID requestId = UUID.randomUUID();
        AgentTurnCommand command = new AgentTurnCommand.Ask(
                requestId, AgentTurnCommand.ModelSelection.none(),
                new AgentTurnCommand.FreeText("你好"), null, null);
        AgentTurnLifecycleService.Result first = service.execute(null, command);
        assertThat(service.clearConversation(first.conversation().resumeToken())).isTrue();

        AgentTurnLifecycleService.Result blocked = service.execute(null, command);
        assertThat(blocked.status()).isEqualTo(AgentTurnLifecycleService.Status.CANCELLED);
        assertThat(service.currentConversation(first.conversation().resumeToken()).authenticated())
                .isFalse();

        clock.advance(Duration.ofMinutes(30));
        AgentTurnLifecycleService.Result replacement = service.execute(null, command);
        assertThat(replacement.status()).isEqualTo(AgentTurnLifecycleService.Status.COMPLETED);
        assertThat(replacement.conversation().conversationId())
                .isEqualTo(first.conversation().conversationId());
        assertThat(replacement.conversation().resumeToken())
                .isNotEqualTo(first.conversation().resumeToken());
        assertThat(service.currentConversation(first.conversation().resumeToken()).authenticated())
                .isFalse();
        assertThat(service.currentConversation(replacement.conversation().resumeToken()).authenticated())
                .isTrue();
    }

    private static final class MutableClock extends java.time.Clock {
        private java.time.Instant current;
        private MutableClock(java.time.Instant current) { this.current = current; }
        private void advance(Duration duration) { current = current.plus(duration); }
        @Override public java.time.Instant instant() { return current; }
        @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
    }

    private static com.portfolio.agent.turn.execution.SemanticTurnOutcome generalOutcome(
            String body) {
        com.portfolio.agent.turn.capability.general.GeneralSemanticResult result =
                new com.portfolio.agent.turn.capability.general.GeneralSemanticResult(
                        "主题", List.of(new com.portfolio.agent.turn.capability.general
                        .GeneralSemanticResult.Statement(
                        com.portfolio.agent.turn.capability.general.GeneralSemanticResult.Role.DEFINITION,
                        body, null, null)), List.of(), "public-1");
        com.portfolio.agent.turn.capability.general.GeneralPresentation presentation =
                new com.portfolio.agent.turn.capability.general.GeneralPresentation(
                        "主题", List.of(new com.portfolio.agent.turn.capability.general
                        .GeneralPresentation.Section(
                        com.portfolio.agent.turn.execution.AnswerSectionType.BACKGROUND,
                        "说明", body)));
        com.portfolio.agent.turn.execution.TaskArtifact artifact =
                new com.portfolio.agent.turn.execution.TaskArtifact(
                        result, presentation,
                        com.portfolio.agent.turn.execution.TaskProvenance.none());
        return new com.portfolio.agent.turn.execution.SemanticTurnOutcome(
                List.of(new com.portfolio.agent.turn.execution.TaskOutcome(
                        "task-goal-1",
                        new com.portfolio.agent.turn.execution.TaskOutcome.Produced(
                                artifact,
                                com.portfolio.agent.turn.execution.TaskOutcome.Fulfillment.FULL))),
                List.of(new com.portfolio.agent.turn.execution.GoalCoverage(
                        "goal-1",
                        com.portfolio.agent.turn.execution.GoalCoverage.Coverage.FULL)));
    }
}
