package com.portfolio.agent.turn.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.ModelExecutionResolver;
import com.portfolio.agent.infrastructure.model.ModelExecutionSnapshot;
import com.portfolio.agent.infrastructure.model.ModelTransportBinding;
import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.SelectedModelFailureException;
import com.portfolio.agent.infrastructure.model.StructuredModelFailure;
import com.portfolio.agent.infrastructure.model.OpenAiCompatibleStructuredModelTransport;
import com.portfolio.agent.infrastructure.model.LoopbackStructuredTransportTestSupport;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputGateway;
import com.portfolio.agent.infrastructure.model.structured.StructuredModelTestFixtures;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogDefaultSelection;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogSnapshot;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderDescriptor;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderProtocolProfile;
import com.portfolio.agent.infrastructure.model.provider.ModelRef;
import com.portfolio.agent.turn.planning.GoalBoundaryPolicy;
import com.portfolio.agent.turn.planning.GoalInterpretationInputFactory;
import com.portfolio.agent.turn.planning.GoalResolver;
import com.portfolio.agent.turn.planning.GoalProposalCodec;
import com.portfolio.agent.turn.planning.SafeConversationalFastPath;
import com.portfolio.agent.turn.planning.SemanticRouteValidator;
import com.portfolio.agent.turn.planning.SemanticPlanCompiler;
import com.portfolio.agent.turn.planning.SemanticPlanValidator;
import com.portfolio.agent.turn.planning.SemanticRouteProposal;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import com.portfolio.agent.turn.planning.GoalRequestedOutput;
import com.portfolio.agent.turn.planning.GoalKnowledgeRequirement;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalInterpretationResult;
import com.portfolio.agent.turn.execution.SemanticTurnEngine;
import com.portfolio.agent.turn.infrastructure.model.GoalInterpretationAdapter;
import com.portfolio.agent.turn.projection.ModelExecutionProjection;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.state.memory.InMemoryTurnExecutionStore;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
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
    void publishedQwenV6SelectionSettlesStaleBeforeAnyProviderCallAgainstV7() {
        AtomicInteger providerCalls = new AtomicInteger();
        AtomicInteger bindingLookups = new AtomicInteger();
        GoalResolver goalResolver = new GoalResolver(
                (input, deadline, modelExecution) -> {
                    providerCalls.incrementAndGet();
                    throw new AssertionError("stale selection must not call Provider");
                },
                ignored -> {
                    throw new AssertionError("reviewed goals are not used");
                },
                new GoalInterpretationInputFactory(),
                new SafeConversationalFastPath(),
                new SemanticRouteValidator(), new GoalBoundaryPolicy());
        ModelExecutionResolver resolver = qwenV7Resolver(bindingLookups);
        AgentTurnLifecycleService service = LifecycleTestFixture.service(
                new InMemoryTurnExecutionStore(), goalResolver, resolver);

        AgentTurnLifecycleService.Result result = service.execute(
                null, command(UUID.randomUUID(),
                        AgentTurnCommand.ModelSelection.model(
                                "qwen-3-7-flash", "qwen-3-7-flash-v6")));

        assertUnavailable(result.turn(), "MODEL_SELECTION_STALE", false, null,
                ModelExecutionProjection.Participation.NONE);
        assertThat(bindingLookups).hasValue(1);
        assertThat(providerCalls).hasValue(0);
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

    @Test
    void goalPreflightSafetyFailureSettlesWithNoModelParticipation() {
        GoalResolver goalResolver = new GoalResolver(
                (input, deadline, modelExecution) -> {
                    throw SelectedModelFailureException.from(
                            outboundSecretFailure());
                },
                ignored -> {
                    throw new AssertionError("reviewed goals are not used");
                },
                new GoalInterpretationInputFactory(),
                new SafeConversationalFastPath(),
                new SemanticRouteValidator(), new GoalBoundaryPolicy());
        InMemoryTurnExecutionStore store = new InMemoryTurnExecutionStore();
        AgentTurnLifecycleService service = LifecycleTestFixture.service(
                store, goalResolver, resolver(new AtomicInteger()));
        AgentTurnCommand command = command(
                UUID.randomUUID(), AgentTurnCommand.ModelSelection.model(
                        MODEL_REF, SELECTION_VERSION));

        AgentTurnLifecycleService.Result first = service.execute(null, command);
        AgentTurnLifecycleService.Result replay = service.execute(null, command);

        assertUnavailable(
                first.turn(), "SELECTED_MODEL_UNAVAILABLE", false, null,
                ModelExecutionProjection.Participation.NONE);
        assertUnavailable(
                replay.turn(), "SELECTED_MODEL_UNAVAILABLE", false, null,
                ModelExecutionProjection.Participation.NONE);
    }

    @Test
    void realGoalAdapterTransportPreflightSettlesAndReplaysWithoutHttp() throws Exception {
        try (CountingLoopbackServer server = new CountingLoopbackServer()) {
            ObjectMapper mapper = new ObjectMapper();
            OpenAiCompatibleStructuredModelTransport transport =
                    LoopbackStructuredTransportTestSupport.transport(
                            server.endpoint(), mapper,
                            StructuredModelTestFixtures.contracts());
            GoalInterpretationAdapter goalAdapter =
                    new GoalInterpretationAdapter(
                            new StructuredOutputGateway(
                                    transport,
                                    StructuredModelTestFixtures.contracts()),
                            mapper, new GoalProposalCodec(), "system prompt",
                            1200, Duration.ofSeconds(5));
            GoalResolver goalResolver = new GoalResolver(
                    goalAdapter,
                    ignored -> {
                        throw new AssertionError("reviewed goals are not used");
                    },
                    new GoalInterpretationInputFactory(),
                    new SafeConversationalFastPath(),
                    new SemanticRouteValidator(), new GoalBoundaryPolicy());
            InMemoryTurnExecutionStore store = new InMemoryTurnExecutionStore();
            AgentTurnLifecycleService service = LifecycleTestFixture.service(
                    store, goalResolver, qwenV7Resolver(new AtomicInteger()));
            AgentTurnCommand command = command(
                    UUID.randomUUID(), AgentTurnCommand.ModelSelection.model(
                            "qwen-3-7-flash", "qwen-3-7-flash-v8"),
                    "解释并检查 api_key=synthetic-integration-secret-12345");

            AgentTurnLifecycleService.Result first = service.execute(null, command);
            AgentTurnLifecycleService.Result replay = service.execute(null, command);

            assertThat(first.status()).isEqualTo(
                    AgentTurnLifecycleService.Status.COMPLETED);
            assertThat(replay.status()).isEqualTo(
                    AgentTurnLifecycleService.Status.REPLAY);
            assertUnavailable(
                    first.turn(), "SELECTED_MODEL_UNAVAILABLE", false, null,
                    ModelExecutionProjection.Participation.NONE);
            assertUnavailable(
                    replay.turn(), "SELECTED_MODEL_UNAVAILABLE", false, null,
                    ModelExecutionProjection.Participation.NONE);
            assertThat(server.requestCount).hasValue(0);
        }
    }

    @Test
    void generalPreflightSafetyFailurePreservesAdoptedGoalParticipation() {
        UserGoalProposal proposal = generalProposal(
                "解释分布式事务的隔离机制");
        GoalResolver goalResolver = new GoalResolver(
                (input, deadline, modelExecution) ->
                        GoalInterpretationResult.semanticRoute(
                                SemanticRouteProposal.standardGoal(proposal)),
                ignored -> {
                    throw new AssertionError("reviewed goals are not used");
                },
                new GoalInterpretationInputFactory(),
                new SafeConversationalFastPath(),
                new SemanticRouteValidator(), new GoalBoundaryPolicy());
        SemanticTurnEngine engine = mock(SemanticTurnEngine.class);
        org.mockito.Mockito.when(engine.execute(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyBoolean(),
                        org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    ResolvedModelExecution execution = invocation.getArgument(4);
                    assertThat(execution.wasAdopted(
                            ResolvedModelExecution.Stage.GOAL_INTERPRETATION))
                            .isTrue();
                    assertThat(execution.wasAttempted(
                            ResolvedModelExecution.Stage.ANSWER_GENERATION))
                            .isFalse();
                    throw SelectedModelFailureException.from(
                            outboundSecretFailure());
                });
        InMemoryTurnExecutionStore store = new InMemoryTurnExecutionStore();
        AgentTurnLifecycleService service = LifecycleTestFixture.service(
                store, goalResolver,
                new SemanticPlanCompiler(new SemanticPlanValidator()), engine,
                resolver(new AtomicInteger()));
        AgentTurnCommand command = command(
                UUID.randomUUID(), AgentTurnCommand.ModelSelection.model(
                        MODEL_REF, SELECTION_VERSION));

        AgentTurnLifecycleService.Result first = service.execute(null, command);
        AgentTurnLifecycleService.Result replay = service.execute(null, command);

        assertUnavailable(
                first.turn(), "SELECTED_MODEL_UNAVAILABLE", false, null,
                ModelExecutionProjection.Participation.GOAL_INTERPRETATION_ONLY);
        assertUnavailable(
                replay.turn(), "SELECTED_MODEL_UNAVAILABLE", false, null,
                ModelExecutionProjection.Participation.GOAL_INTERPRETATION_ONLY);
    }

    private StructuredModelFailure outboundSecretFailure() {
        return new StructuredModelFailure(
                StructuredModelFailure.Code.OUTBOUND_SECRET_LIKE_REJECTED,
                StructuredModelFailure.Reason.SECRET_LIKE_CONTENT);
    }

    private UserGoalProposal generalProposal(String input) {
        UserGoalProposal.InputAnchor anchor =
                new UserGoalProposal.InputAnchor(input, 0);
        return new UserGoalProposal(List.of(
                new UserGoalProposal.ProposedGoal(
                        "general", GoalKind.GENERAL_EXPLANATION,
                        anchor, List.of(),
                        java.util.Set.of(GoalRequestedOutput.EXPLANATION),
                        GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION,
                        new UserGoalProposal.GeneralExplanationParameters(
                                anchor, UserGoalProposal.Depth.STANDARD))));
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
        return command(
                requestId, selection,
                "解释分布式事务的隔离机制");
    }

    private AgentTurnCommand command(
            UUID requestId,
            AgentTurnCommand.ModelSelection selection,
            String input) {
        return new AgentTurnCommand.Ask(
                requestId, selection,
                new AgentTurnCommand.FreeText(input),
                null, null);
    }

    private ModelExecutionResolver resolver(AtomicInteger bindingLookups) {
        ModelProviderDescriptor descriptor = descriptor();
        ModelCatalogSnapshot catalog = new ModelCatalogSnapshot(
                "catalog-v1", List.of(descriptor),
                ModelCatalogDefaultSelection.model(descriptor));
        ModelTransportBinding binding = new ModelTransportBinding(
                descriptor.getModelRef(), descriptor.getDescriptorFingerprint(),
                descriptor.getEndpoint(),
                descriptor.getModelName(), descriptor.getProtocolProfile(),
                "test-credential", descriptor.getMaxOutputTokens(),
                StructuredModelTestFixtures.nativeBindings());
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
                StructuredModelTestFixtures.nativeBindings(),
                100_000, 8_000);
    }

    private ModelExecutionResolver qwenV7Resolver(
            AtomicInteger bindingLookups) {
        ModelProviderDescriptor descriptor = new ModelProviderDescriptor(
                ModelRef.of("qwen-3-7-flash"), "qwen-3-7-flash-v8",
                "Qwen3.7-Flash", 20,
                URI.create("https://provider.example/v1/chat/completions"),
                "qwen3.7-flash",
                ModelProviderProtocolProfile.DASHSCOPE_CHAT_COMPLETIONS,
                StructuredModelTestFixtures.qwenV8ToolBindings(),
                100_000, 8_000);
        ModelCatalogSnapshot catalog = new ModelCatalogSnapshot(
                "candidate-v7", List.of(descriptor),
                ModelCatalogDefaultSelection.model(descriptor));
        ModelTransportBinding binding = new ModelTransportBinding(
                descriptor.getModelRef(), descriptor.getDescriptorFingerprint(),
                descriptor.getEndpoint(), descriptor.getModelName(),
                descriptor.getProtocolProfile(), "test-credential",
                descriptor.getMaxOutputTokens(),
                StructuredModelTestFixtures.qwenV8ToolBindings());
        return new ModelExecutionResolver(catalog, modelRef -> {
            bindingLookups.incrementAndGet();
            return binding;
        });
    }

    private static final class CountingLoopbackServer implements AutoCloseable {
        private final AtomicInteger requestCount = new AtomicInteger();
        private final HttpServer server;

        private CountingLoopbackServer() throws IOException {
            server = HttpServer.create(
                    new InetSocketAddress(
                            InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/chat", this::respond);
            server.start();
        }

        private URI endpoint() {
            return URI.create("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/chat");
        }

        private void respond(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            byte[] body = "unexpected".getBytes(
                    java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (exchange; java.io.OutputStream output =
                    exchange.getResponseBody()) {
                output.write(body);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
