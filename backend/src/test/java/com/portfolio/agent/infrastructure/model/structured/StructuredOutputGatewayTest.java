package com.portfolio.agent.infrastructure.model.structured;

import com.portfolio.agent.infrastructure.model.ModelTransportBinding;
import com.portfolio.agent.infrastructure.model.ProviderAttemptContext;
import com.portfolio.agent.infrastructure.model.StructuredModelRequest;
import com.portfolio.agent.infrastructure.model.StructuredModelResponse;
import com.portfolio.agent.infrastructure.model.StructuredModelTransport;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.turn.execution.TurnDeadline;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredOutputGatewayTest {

    @Test
    void defaultGatewayPathCreatesASingleAttemptIdentityForTransport() {
        AtomicReference<ProviderAttemptContext> received =
                new AtomicReference<>();
        StructuredModelTransport transport = new StructuredModelTransport() {
            @Override
            public StructuredModelResponse execute(
                    ModelTransportBinding binding,
                    StructuredModelRequest request) {
                throw new AssertionError(
                        "gateway must use the attempt-aware transport seam");
            }

            @Override
            public StructuredModelResponse execute(
                    ModelTransportBinding binding,
                    StructuredModelRequest request,
                    ProviderAttemptContext attempt) {
                received.set(attempt);
                return new StructuredModelResponse("""
                        {"topic":"并发控制","statements":[
                          {"role":"DEFINITION","text":"定义。",
                           "aspects":["DEFINITION"]}
                        ],"caveats":[]}
                        """);
            }
        };
        StructuredOutputGateway gateway =
                StructuredModelTestFixtures.gateway(transport);

        gateway.execute(
                StructuredModelTestFixtures.resolvedModel(
                        StructuredModelTestFixtures.nativeBindings())
                        .getRequiredBinding(),
                request(ModelOperation.GENERAL_KNOWLEDGE));

        assertThat(received.get()).isNotNull();
        assertThat(received.get().attemptIndex()).isEqualTo(1);
        assertThat(received.get().attemptCount()).isEqualTo(1);
        assertThat(received.get().duplicateBillingRisk()).isFalse();
        assertThat(received.get().attemptId()).isNotNull();
        assertThat(received.get().attemptTimeoutCap()).isEmpty();
    }

    @Test
    void validatesExtractedPayloadAgainstTheContractFrozenByTheBinding() {
        AtomicInteger calls = new AtomicInteger();
        StructuredOutputGateway gateway = StructuredModelTestFixtures.gateway(
                (binding, request) -> {
                    calls.incrementAndGet();
                    return new StructuredModelResponse("""
                            {"topic":"并发控制","statements":[
                              {"role":"DEFINITION","text":"定义。",
                               "aspects":["DEFINITION"]}
                            ],"caveats":[]}
                            """);
                });
        StructurallyValidatedOutput output = gateway.execute(
                StructuredModelTestFixtures.resolvedModel(
                        StructuredModelTestFixtures.nativeBindings())
                        .getRequiredBinding(),
                request(ModelOperation.GENERAL_KNOWLEDGE));

        assertThat(output.contractRef().schemaVersion())
                .isEqualTo("general.draft.v2");
        assertThat(output.jsonTree().path("topic").textValue())
                .isEqualTo("并发控制");
        assertThat(calls).hasValue(1);
    }

    @Test
    void missingOperationBindingFailsBeforeTransportInvocation() {
        AtomicInteger calls = new AtomicInteger();
        StructuredOutputGateway gateway = StructuredModelTestFixtures.gateway(
                (binding, request) -> {
                    calls.incrementAndGet();
                    throw new AssertionError("transport must not be called");
                });
        OperationBinding turn = StructuredModelTestFixtures.nativeBindings()
                .get(ModelOperation.TURN_INTERPRETATION);

        assertThatThrownBy(() -> gateway.execute(
                StructuredModelTestFixtures.resolvedModel(Map.of(
                        ModelOperation.TURN_INTERPRETATION, turn))
                        .getRequiredBinding(),
                request(ModelOperation.GENERAL_KNOWLEDGE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available");
        assertThat(calls).hasValue(0);
    }

    @Test
    void localSchemaRejectionNeverTriggersRepairOrRetry() {
        AtomicInteger calls = new AtomicInteger();
        StructuredOutputGateway gateway = StructuredModelTestFixtures.gateway(
                (binding, request) -> {
                    calls.incrementAndGet();
                    return new StructuredModelResponse("{}");
                });

        assertThatThrownBy(() -> gateway.execute(
                StructuredModelTestFixtures.resolvedModel(
                        StructuredModelTestFixtures.nativeBindings())
                        .getRequiredBinding(),
                request(ModelOperation.GENERAL_KNOWLEDGE)))
                .isInstanceOf(StructuredOutputValidationException.class)
                .extracting(failure -> ((StructuredOutputValidationException) failure)
                        .getReason())
                .isEqualTo(StructuredOutputValidationException.Reason.MISSING_REQUIRED_FIELD);
        assertThat(calls).hasValue(1);
    }

    @Test
    void deterministicCompilerRejectionIsClosedAndNeverRetriesTransport() {
        AtomicInteger calls = new AtomicInteger();
        StructuredOutputGateway gateway = StructuredModelTestFixtures.gateway(
                (binding, request) -> {
                    calls.incrementAndGet();
                    return new StructuredModelResponse("""
                            {"decision":"CONVERSATIONAL","message":"请补充目标。"}
                            """);
                });

        assertThatThrownBy(() -> gateway.execute(
                StructuredModelTestFixtures.resolvedModel(
                                StructuredModelTestFixtures.v4NativeBindings())
                        .getRequiredBinding(),
                request(ModelOperation.TURN_INTERPRETATION),
                StructuredOutputCompiler.named(
                        OperationBinding.GOAL_DRAFT_OUTPUT_COMPILER_VERSION,
                        ignored -> {
                    throw new StructuredOutputValidationException(
                            StructuredOutputValidationException.Reason
                                    .DRAFT_FIELD_CONFLICT);
                })))
                .isInstanceOf(StructuredOutputValidationException.class)
                .satisfies(failure -> assertThat(
                        ((StructuredOutputValidationException) failure).getStage())
                        .isEqualTo(StructuredOutputValidationException.Stage
                                .DETERMINISTIC_COMPILER));
        assertThat(calls).hasValue(1);
    }

    @Test
    void canonicalSchemaRejectionIsClosedAndNeverRetriesTransport() {
        AtomicInteger calls = new AtomicInteger();
        StructuredOutputGateway gateway = StructuredModelTestFixtures.gateway(
                (binding, request) -> {
                    calls.incrementAndGet();
                    return new StructuredModelResponse("""
                            {"decision":"CONVERSATIONAL","message":"请补充目标。"}
                            """);
                });

        assertThatThrownBy(() -> gateway.execute(
                StructuredModelTestFixtures.resolvedModel(
                                StructuredModelTestFixtures.v4NativeBindings())
                        .getRequiredBinding(),
                request(ModelOperation.TURN_INTERPRETATION),
                StructuredOutputCompiler.named(
                        OperationBinding.GOAL_DRAFT_OUTPUT_COMPILER_VERSION,
                        ignored -> com.fasterxml.jackson.databind.node.JsonNodeFactory
                                .instance.objectNode())))
                .isInstanceOf(StructuredOutputValidationException.class)
                .satisfies(failure -> assertThat(
                        ((StructuredOutputValidationException) failure).getStage())
                        .isEqualTo(StructuredOutputValidationException.Stage
                                .CANONICAL_SCHEMA));
        assertThat(calls).hasValue(1);
    }

    @Test
    void compilerProfileMismatchFailsBeforeTransportInvocation() {
        AtomicInteger calls = new AtomicInteger();
        StructuredOutputGateway gateway = StructuredModelTestFixtures.gateway(
                (binding, request) -> {
                    calls.incrementAndGet();
                    throw new AssertionError("transport must not be called");
                });

        assertThatThrownBy(() -> gateway.execute(
                StructuredModelTestFixtures.resolvedModel(
                                StructuredModelTestFixtures.v4NativeBindings())
                        .getRequiredBinding(),
                request(ModelOperation.TURN_INTERPRETATION)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("compiler profile");
        assertThat(calls).hasValue(0);
    }

    private StructuredModelRequest request(ModelOperation operation) {
        return new StructuredModelRequest(
                operation, "system", "user", 1200, 0.0d,
                TurnDeadline.after(Duration.ofSeconds(3), Clock.systemUTC()));
    }
}
