package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.infrastructure.model.ModelExecutionResolver;
import com.portfolio.agent.infrastructure.model.ModelExecutionSnapshot;
import com.portfolio.agent.infrastructure.model.ModelTransportBinding;
import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.SelectedModelFailureException;
import com.portfolio.agent.infrastructure.model.StructuredModelFailure;
import com.portfolio.agent.infrastructure.model.provider.ModelCapability;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogDefaultSelection;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogSnapshot;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderDescriptor;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderProtocolProfile;
import com.portfolio.agent.infrastructure.model.provider.ModelRef;
import com.portfolio.agent.turn.planning.GoalBoundaryPolicy;
import com.portfolio.agent.turn.planning.GoalInterpretationInputFactory;
import com.portfolio.agent.turn.planning.GoalResolver;
import com.portfolio.agent.turn.planning.SafeConversationalFastPath;
import com.portfolio.agent.turn.planning.SemanticRouteValidator;
import com.portfolio.agent.turn.projection.ModelExecutionProjection;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.state.memory.InMemoryTurnExecutionStore;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentTurnLifecycleSelectedModelFailureTest {
    private static final String MODEL_REF = "glm-4-7-flash";
    private static final String SELECTION_VERSION = "glm-4-7-flash-v1";

    @Test
    void staleAndUnavailableAreAtomicallySettledBeforeProviderExecutionAndThenReplayed() {
        assertSelectionResolutionFailure(
                AgentTurnCommand.ModelSelection.model(MODEL_REF, "old-version"),
                "MODEL_SELECTION_STALE", 1);
        assertSelectionResolutionFailure(
                AgentTurnCommand.ModelSelection.model(
                        "missing-model", SELECTION_VERSION),
                "SELECTED_MODEL_UNAVAILABLE", 0);
    }

    @Test
    void selectedModelFailuresSettleOnceAndOnlyANewRequestExecutesAgain() {
        assertOperationFailure(
                () -> SelectedModelFailureException.from(
                        new StructuredModelFailure(
                                StructuredModelFailure.Code.AUTHENTICATION_REJECTED)),
                "SELECTED_MODEL_UNAVAILABLE", false, null);
        assertOperationFailure(
                () -> SelectedModelFailureException.from(
                        new StructuredModelFailure(
                                StructuredModelFailure.Code.PROVIDER_UNAVAILABLE)),
                "SELECTED_MODEL_TEMPORARILY_UNAVAILABLE", true, null);
        assertOperationFailure(
                () -> SelectedModelFailureException.from(
                        new StructuredModelFailure(
                                StructuredModelFailure.Code.RATE_LIMITED, 23, null)),
                "SELECTED_MODEL_RATE_LIMITED", true, 23L);
        assertOperationFailure(
                () -> SelectedModelFailureException.invalidResponse(
                        new IllegalArgumentException(
                                "endpoint=https://internal key=secret provider-body")),
                "SELECTED_MODEL_INVALID_RESPONSE", false, null);
    }

    private void assertSelectionResolutionFailure(
            AgentTurnCommand.ModelSelection selection,
            String expectedCode,
            int expectedBindingLookups) {
        AtomicInteger bindingLookups = new AtomicInteger();
        ModelExecutionResolver modelResolver = resolver(bindingLookups);
        InMemoryTurnExecutionStore store = new InMemoryTurnExecutionStore();
        AgentTurnLifecycleService service = LifecycleTestFixture.service(
                store, mock(GoalResolver.class), modelResolver);
        UUID requestId = UUID.randomUUID();
        AgentTurnCommand command = command(requestId, selection);

        AgentTurnLifecycleService.Result first = service.execute(null, command);
        AgentTurnLifecycleService.Result replay = service.execute(null, command);

        assertThat(first.status()).isEqualTo(
                AgentTurnLifecycleService.Status.COMPLETED);
        assertThat(replay.status()).isEqualTo(
                AgentTurnLifecycleService.Status.REPLAY);
        assertThat(bindingLookups).hasValue(expectedBindingLookups);
        assertUnavailable(
                first.turn(), expectedCode, false, null,
                ModelExecutionProjection.Participation.NONE);
        assertUnavailable(
                replay.turn(), expectedCode, false, null,
                ModelExecutionProjection.Participation.NONE);
    }

    private void assertOperationFailure(
            Supplier<SelectedModelFailureException> failure,
            String expectedCode,
            boolean retryable,
            Long retryAfterSeconds) {
        AtomicInteger providerCalls = new AtomicInteger();
        GoalResolver goalResolver = new GoalResolver(
                (input, deadline, modelExecution) -> {
                    providerCalls.incrementAndGet();
                    modelExecution.markAttempted(
                            ResolvedModelExecution.Stage.GOAL_INTERPRETATION);
                    throw failure.get();
                },
                ignored -> {
                    throw new AssertionError("reviewed goals are not used");
                },
                new GoalInterpretationInputFactory(),
                new SafeConversationalFastPath(),
                new SemanticRouteValidator(),
                new GoalBoundaryPolicy());
        InMemoryTurnExecutionStore store = new InMemoryTurnExecutionStore();
        AgentTurnLifecycleService service = LifecycleTestFixture.service(
                store, goalResolver, resolver(new AtomicInteger()));
        AgentTurnCommand firstCommand = command(
                UUID.randomUUID(), AgentTurnCommand.ModelSelection.model(
                        MODEL_REF, SELECTION_VERSION));

        AgentTurnLifecycleService.Result first =
                service.execute(null, firstCommand);
        AgentTurnLifecycleService.Result replay =
                service.execute(null, firstCommand);
        AgentTurnLifecycleService.Result fresh = service.execute(
                null, command(UUID.randomUUID(),
                        AgentTurnCommand.ModelSelection.model(
                                MODEL_REF, SELECTION_VERSION)));

        assertThat(first.status()).isEqualTo(
                AgentTurnLifecycleService.Status.COMPLETED);
        assertThat(replay.status()).isEqualTo(
                AgentTurnLifecycleService.Status.REPLAY);
        assertThat(fresh.status()).isEqualTo(
                AgentTurnLifecycleService.Status.COMPLETED);
        assertThat(providerCalls).as(
                "same requestId replays; only the fresh request executes again")
                .hasValue(2);
        assertUnavailable(
                first.turn(), expectedCode, retryable, retryAfterSeconds,
                ModelExecutionProjection.Participation.ATTEMPTED_UNAVAILABLE);
        assertUnavailable(
                replay.turn(), expectedCode, retryable, retryAfterSeconds,
                ModelExecutionProjection.Participation.ATTEMPTED_UNAVAILABLE);
        assertUnavailable(
                fresh.turn(), expectedCode, retryable, retryAfterSeconds,
                ModelExecutionProjection.Participation.ATTEMPTED_UNAVAILABLE);
    }

    private void assertUnavailable(
            PublicAgentTurn turn,
            String expectedCode,
            boolean retryable,
            Long retryAfterSeconds,
            ModelExecutionProjection.Participation participation) {
        assertThat(turn).isInstanceOf(PublicAgentTurn.CapabilityUnavailable.class);
        PublicAgentTurn.CapabilityUnavailable unavailable =
                (PublicAgentTurn.CapabilityUnavailable) turn;
        assertThat(unavailable.getCode()).isEqualTo(expectedCode);
        assertThat(unavailable.isRetryable()).isEqualTo(retryable);
        assertThat(unavailable.getRetryAfterSeconds()).isEqualTo(retryAfterSeconds);
        assertThat(unavailable.getMessage()).doesNotContain(
                "endpoint", "key", "secret", "provider-body");
        assertThat(unavailable.getModelExecution().getSelectionKind())
                .isEqualTo(ModelExecutionProjection.SelectionKind.MODEL);
        assertThat(unavailable.getModelExecution().getRequestedModelRef())
                .isNotBlank();
        assertThat(unavailable.getModelExecution().getSelectionVersion())
                .isNotBlank();
        assertThat(unavailable.getModelExecution().getParticipation())
                .isEqualTo(participation);
    }

    private AgentTurnCommand command(
            UUID requestId,
            AgentTurnCommand.ModelSelection selection) {
        return new AgentTurnCommand.Ask(
                requestId, selection,
                new AgentTurnCommand.FreeText("解释分布式事务的隔离机制"),
                null, null);
    }

    private ModelExecutionResolver resolver(AtomicInteger bindingLookups) {
        ModelProviderDescriptor descriptor = descriptor();
        ModelCatalogSnapshot catalog = new ModelCatalogSnapshot(
                "catalog-v1", List.of(descriptor),
                ModelCatalogDefaultSelection.model(descriptor));
        ModelTransportBinding binding = new ModelTransportBinding(
                descriptor.getModelRef(), descriptor.getEndpoint(),
                descriptor.getModelName(), descriptor.getProtocolProfile(),
                "test-credential", descriptor.getMaxOutputTokens());
        return new ModelExecutionResolver(catalog, modelRef -> {
            bindingLookups.incrementAndGet();
            if (!modelRef.equals(descriptor.getModelRef())) {
                throw new IllegalArgumentException("binding unavailable");
            }
            return binding;
        });
    }

    private ModelProviderDescriptor descriptor() {
        return new ModelProviderDescriptor(
                ModelRef.of(MODEL_REF), SELECTION_VERSION,
                "GLM 4.7 Flash", 10,
                URI.create("https://provider.example/v1/chat/completions"),
                "glm-4.7-flash",
                ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS,
                Set.of(ModelCapability.TURN_INTERPRETATION,
                        ModelCapability.GENERAL_KNOWLEDGE),
                100_000, 8_000);
    }
}
