package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.StructuredModelRequest;
import com.portfolio.agent.infrastructure.model.StructuredModelResponse;
import com.portfolio.agent.infrastructure.model.ModelTransportBinding;
import com.portfolio.agent.infrastructure.model.ProviderAttemptContext;
import com.portfolio.agent.infrastructure.model.StructuredModelTransport;
import com.portfolio.agent.infrastructure.model.SelectedModelFailureException;
import com.portfolio.agent.infrastructure.model.StructuredModelFailure;
import com.portfolio.agent.infrastructure.model.structured.StructurallyValidatedOutput;
import com.portfolio.agent.infrastructure.model.structured.StructuredModelTestFixtures;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.ModelOutputDiagnostics;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeRequest;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleGeneralKnowledgeAdapterTest {
    @Test void qwenDraftBindingUsesDraftPromptAndCompilesCanonicalOutput() {
        AtomicReference<StructuredModelRequest> captured = new AtomicReference<>();
        OpenAiCompatibleGeneralKnowledgeAdapter adapter =
                new OpenAiCompatibleGeneralKnowledgeAdapter(
                        StructuredModelTestFixtures.gateway((binding, request) -> {
                            captured.set(request);
                            return new StructuredModelResponse("""
                                    {"definition":"并发控制是一种协调机制。",
                                     "mechanism":"它通过控制访问顺序减少冲突。",
                                     "caveats":[]}
                                    """);
                        }), new ObjectMapper(), "canonical-prompt", 1200,
                        Duration.ofSeconds(10), "draft-prompt",
                        ModelOutputDiagnostics.none());

        StructurallyValidatedOutput response = adapter.generate(GeneralKnowledgeRequest.explanation(
                "并发控制", UserGoalProposal.Depth.CONCISE,
                GeneralKnowledgeRequest.Audience.GUEST, "public-1",
                TurnDeadline.after(Duration.ofSeconds(12), Clock.systemUTC())),
                StructuredModelTestFixtures.resolvedModel(
                        StructuredModelTestFixtures.qwenV7ToolBindings()));

        assertThat(captured.get().systemPrompt()).isEqualTo("draft-prompt");
        assertThat(captured.get().temperature()).isZero();
        assertThat(response.contractRef().schemaVersion())
                .isEqualTo("general.draft.v3");
        assertThat(response.jsonTree().path("topic").textValue())
                .isEqualTo("并发控制");
        assertThat(response.jsonTree().path("statements")).hasSize(2);
    }

    @Test void noneSelectionDoesNotCallGeneralProvider() {
        AtomicInteger calls = new AtomicInteger();
        OpenAiCompatibleGeneralKnowledgeAdapter adapter =
                new OpenAiCompatibleGeneralKnowledgeAdapter(
                        StructuredModelTestFixtures.gateway((binding, request) -> {
                    calls.incrementAndGet();
                    throw new AssertionError("NONE must not call provider");
                }), new ObjectMapper(), "system", 1200, Duration.ofSeconds(10));

        assertThatThrownBy(() -> adapter.generate(
                GeneralKnowledgeRequest.explanation(
                        "并发控制", UserGoalProposal.Depth.STANDARD,
                        GeneralKnowledgeRequest.Audience.GUEST, "public-1",
                        TurnDeadline.after(Duration.ofSeconds(12), Clock.systemUTC())),
                com.portfolio.agent.infrastructure.model
                        .ResolvedModelExecution.none()))
                .isInstanceOf(com.portfolio.agent.turn.capability.general
                        .GeneralKnowledgeUnavailableException.class);
        assertThat(calls).hasValue(0);
    }

    @Test void sendsInjectedPromptAndPreservesTypedRequestProjection() {
        AtomicReference<StructuredModelRequest> captured = new AtomicReference<>();
        AtomicReference<ProviderAttemptContext> attempt = new AtomicReference<>();
        String systemPrompt = "general-system-prompt";
        OpenAiCompatibleGeneralKnowledgeAdapter adapter =
                new OpenAiCompatibleGeneralKnowledgeAdapter(
                        StructuredModelTestFixtures.gateway(
                                new StructuredModelTransport() {
                            @Override
                            public StructuredModelResponse execute(
                                    ModelTransportBinding binding,
                                    StructuredModelRequest request) {
                                throw new AssertionError(
                                        "GLM must use the single-attempt context seam");
                            }

                            @Override
                            public StructuredModelResponse execute(
                                    ModelTransportBinding binding,
                                    StructuredModelRequest request,
                                    ProviderAttemptContext context) {
                                captured.set(request);
                                attempt.set(context);
                                return new StructuredModelResponse("""
                                        {"topic":"并发控制","statements":[
                                          {"role":"DEFINITION","text":"定义。","aspects":["DEFINITION"]},
                                          {"role":"MECHANISM","text":"机制。","aspects":["MECHANISM"]}
                                        ],"caveats":[]}
                                        """);
                            }
                        }), new ObjectMapper(), systemPrompt, 1200,
                        Duration.ofSeconds(10));

        com.portfolio.agent.infrastructure.model.structured.StructurallyValidatedOutput response =
                adapter.generate(GeneralKnowledgeRequest.explanation(
                "并发控制", UserGoalProposal.Depth.DETAILED,
                GeneralKnowledgeRequest.Audience.GUEST, "public-1",
                TurnDeadline.after(Duration.ofSeconds(12), Clock.systemUTC())),
                ModelExecutionSnapshotFixture.model());

        assertThat(response.jsonTree().path("topic").textValue())
                .isEqualTo("并发控制");
        assertThat(captured.get().systemPrompt()).isEqualTo(systemPrompt);
        assertThat(captured.get().userPrompt())
                .contains("\"kind\":\"EXPLANATION\"", "\"topic\":\"并发控制\"",
                        "\"depth\":\"DETAILED\"", "\"audience\":\"GUEST\"",
                        "\"expectedContentVersion\":\"public-1\"");
        assertThat(captured.get().maxOutputTokens()).isEqualTo(1200);
        assertThat(captured.get().temperature()).isEqualTo(0.2d);
        assertThat(attempt.get().attemptIndex()).isEqualTo(1);
        assertThat(attempt.get().attemptCount()).isEqualTo(1);
        assertThat(attempt.get().attemptTimeoutCap()).isEmpty();
    }

    @Test
    void qwenV7GeneralRetriesOneEligibleConnectionFailureWithFrozenRequest() {
        AtomicInteger calls = new AtomicInteger();
        List<StructuredModelRequest> requests = new ArrayList<>();
        OpenAiCompatibleGeneralKnowledgeAdapter adapter =
                new OpenAiCompatibleGeneralKnowledgeAdapter(
                        StructuredModelTestFixtures.gateway((binding, request) -> {
                            requests.add(request);
                            if (calls.incrementAndGet() == 1) {
                                throw StructuredModelFailure.connection(
                                        new java.net.ConnectException(
                                                "test connection"));
                            }
                            return new StructuredModelResponse("""
                                    {"definition":"并发控制是一种协调机制。",
                                     "mechanism":"它通过控制访问顺序减少冲突。",
                                     "caveats":[]}
                                    """);
                        }), new ObjectMapper(), "canonical-prompt", 1200,
                        Duration.ofSeconds(10), "draft-prompt",
                        ModelOutputDiagnostics.none());
        com.portfolio.agent.infrastructure.model.ResolvedModelExecution execution =
                StructuredModelTestFixtures.resolvedModel(
                        StructuredModelTestFixtures.qwenV7ToolBindings());

        StructurallyValidatedOutput response = adapter.generate(
                GeneralKnowledgeRequest.explanation(
                        "并发控制", UserGoalProposal.Depth.CONCISE,
                        GeneralKnowledgeRequest.Audience.GUEST, "public-1",
                        TurnDeadline.after(
                                Duration.ofSeconds(12), Clock.systemUTC())),
                execution);

        assertThat(response.contractRef().schemaVersion())
                .isEqualTo("general.draft.v3");
        assertThat(calls).hasValue(2);
        assertThat(requests).hasSize(2);
        assertThat(requests.get(1)).isSameAs(requests.get(0));
        assertThat(requests.get(0).temperature()).isZero();
        assertThat(execution.wasAttempted(
                com.portfolio.agent.infrastructure.model.ResolvedModelExecution
                        .Stage.ANSWER_GENERATION)).isTrue();
    }

    @Test void providerRateLimitKeepsItsClosedSelectedModelSemantics() {
        AtomicInteger calls = new AtomicInteger();
        OpenAiCompatibleGeneralKnowledgeAdapter adapter =
                new OpenAiCompatibleGeneralKnowledgeAdapter(
                        StructuredModelTestFixtures.gateway((binding, request) -> {
                    calls.incrementAndGet();
                    throw new StructuredModelFailure(
                            StructuredModelFailure.Code.RATE_LIMITED, 41, null);
                }), new ObjectMapper(), "system", 1200, Duration.ofSeconds(10));

        SelectedModelFailureException failure =
                org.assertj.core.api.Assertions.catchThrowableOfType(
                        () -> adapter.generate(
                                GeneralKnowledgeRequest.explanation(
                                        "并发控制", UserGoalProposal.Depth.STANDARD,
                                        GeneralKnowledgeRequest.Audience.GUEST, "public-1",
                                        TurnDeadline.after(
                                                Duration.ofSeconds(12), Clock.systemUTC())),
                                ModelExecutionSnapshotFixture.model()),
                        SelectedModelFailureException.class);

        assertThat(failure.getCode()).isEqualTo(
                SelectedModelFailureException.Code.SELECTED_MODEL_RATE_LIMITED);
        assertThat(failure.getRetryAfterSeconds()).isEqualTo(41);
        assertThat(failure.isAttempted()).isTrue();
        assertThat(calls).hasValue(1);
    }

    @Test void schemaRejectionKeepsSelectedModelSemanticsAndClosedDiagnostics() {
        List<DiagnosticEvent> events = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        String providerBody = "{\"topic\":\"private-sentinel\",\"statements\":[]}";
        OpenAiCompatibleGeneralKnowledgeAdapter adapter =
                new OpenAiCompatibleGeneralKnowledgeAdapter(
                        StructuredModelTestFixtures.gateway((binding, request) -> {
                            calls.incrementAndGet();
                            return new StructuredModelResponse(providerBody);
                        }), new ObjectMapper(), "system", 1200,
                        Duration.ofSeconds(10),
                        new ModelOutputDiagnostics(events::add));

        SelectedModelFailureException failure =
                org.assertj.core.api.Assertions.catchThrowableOfType(
                        () -> adapter.generate(
                                GeneralKnowledgeRequest.explanation(
                                        "并发控制", UserGoalProposal.Depth.STANDARD,
                                        GeneralKnowledgeRequest.Audience.GUEST,
                                        "public-1", TurnDeadline.after(
                                                Duration.ofSeconds(12), Clock.systemUTC())),
                                ModelExecutionSnapshotFixture.model()),
                        SelectedModelFailureException.class);

        assertThat(failure.getCode()).isEqualTo(
                SelectedModelFailureException.Code.SELECTED_MODEL_INVALID_RESPONSE);
        assertThat(failure.isAttempted()).isTrue();
        assertThat(calls).hasValue(1);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getFields().get("provider.operation"))
                    .isEqualTo("GENERAL_KNOWLEDGE");
            assertThat(event.getFields().get("failure.layer"))
                    .isEqualTo("PROVIDER_DRAFT_SCHEMA");
            assertThat(event.getFields().get("failure.reason"))
                    .isEqualTo("ARRAY_CONSTRAINT_INVALID_STATEMENTS_MIN_ITEMS");
            assertThat(event.toString()).doesNotContain(
                    providerBody, "private-sentinel");
        });
    }

}
