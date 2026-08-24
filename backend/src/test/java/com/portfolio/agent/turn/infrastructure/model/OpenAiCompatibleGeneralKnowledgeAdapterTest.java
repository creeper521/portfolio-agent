package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.StructuredModelRequest;
import com.portfolio.agent.infrastructure.model.StructuredModelResponse;
import com.portfolio.agent.infrastructure.model.SelectedModelFailureException;
import com.portfolio.agent.infrastructure.model.StructuredModelFailure;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeRequest;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleGeneralKnowledgeAdapterTest {
    @Test void noneSelectionDoesNotCallGeneralProvider() {
        AtomicInteger calls = new AtomicInteger();
        OpenAiCompatibleGeneralKnowledgeAdapter adapter =
                new OpenAiCompatibleGeneralKnowledgeAdapter((binding, request) -> {
                    calls.incrementAndGet();
                    throw new AssertionError("NONE must not call provider");
                }, new ObjectMapper(), "system", 1200, Duration.ofSeconds(10));

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
        String systemPrompt = "general-system-prompt";
        OpenAiCompatibleGeneralKnowledgeAdapter adapter =
                new OpenAiCompatibleGeneralKnowledgeAdapter((binding, request) -> {
                    captured.set(request);
                    return new StructuredModelResponse("""
                            {"topic":"并发控制","statements":[
                              {"role":"DEFINITION","text":"定义。","aspects":["DEFINITION"]},
                              {"role":"MECHANISM","text":"机制。","aspects":["MECHANISM"]}
                            ],"caveats":[]}
                            """);
                }, new ObjectMapper(), systemPrompt, 1200, Duration.ofSeconds(10));

        String response = adapter.generate(GeneralKnowledgeRequest.explanation(
                "并发控制", UserGoalProposal.Depth.DETAILED,
                GeneralKnowledgeRequest.Audience.GUEST, "public-1",
                TurnDeadline.after(Duration.ofSeconds(12), Clock.systemUTC())),
                ModelExecutionSnapshotFixture.model());

        assertThat(response).contains("并发控制");
        assertThat(captured.get().systemPrompt()).isEqualTo(systemPrompt);
        assertThat(captured.get().userPrompt())
                .contains("\"kind\":\"EXPLANATION\"", "\"topic\":\"并发控制\"",
                        "\"depth\":\"DETAILED\"", "\"audience\":\"GUEST\"",
                        "\"expectedContentVersion\":\"public-1\"");
        assertThat(captured.get().maxOutputTokens()).isEqualTo(1200);
        assertThat(captured.get().temperature()).isEqualTo(0.2d);
    }

    @Test void providerRateLimitKeepsItsClosedSelectedModelSemantics() {
        OpenAiCompatibleGeneralKnowledgeAdapter adapter =
                new OpenAiCompatibleGeneralKnowledgeAdapter((binding, request) -> {
                    throw new StructuredModelFailure(
                            StructuredModelFailure.Code.RATE_LIMITED, 41, null);
                }, new ObjectMapper(), "system", 1200, Duration.ofSeconds(10));

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
    }
}
