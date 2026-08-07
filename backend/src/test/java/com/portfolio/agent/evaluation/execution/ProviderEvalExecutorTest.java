package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationDraft;
import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalProviderUsage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderEvalExecutorTest {

    private static final String QUESTION = "请介绍 SQL 审计与故障排查工具项目";

    @Test
    void successMarksProviderInvokedWithAvailableUsageAndAnswerShape() {
        ConversationalModelPort port = mock(ConversationalModelPort.class);
        when(port.generate(eq(QUESTION), any(ConversationWindow.class),
                any(ConversationRoute.class), any(PortfolioGroundingContext.class)))
                .thenReturn(ConversationModelResult.success(new ConversationDraft(
                        "t", AnswerResolution.ANSWERED, List.of(
                                new ConversationAnswerBlock(
                                        ConversationSourceScope.PORTFOLIO,
                                        "回答", List.of("claim-1"), List.of("E-01"))))));
        ProviderEvalExecutor executor = new ProviderEvalExecutor(port, "mock");

        EvalObservation observation = execute(executor);

        assertThat(observation.getStatus()).isEqualTo(EvalObservationStatus.PASS);
        assertThat(observation.isProviderInvoked()).isTrue();
        // usage is never fabricated: no real token data flows back from the
        // seam, so it is recorded as unavailable
        assertThat(observation.getProviderUsage().isAvailable()).isFalse();
        assertThat(observation.getSelectedClaimIds()).contains("claim-1");
        assertThat(observation.getAnswerShape().getBlockCount()).isEqualTo(1);
        verify(port).generate(eq(QUESTION), any(ConversationWindow.class),
                any(ConversationRoute.class), any(PortfolioGroundingContext.class));
    }

    @Test
    void timeoutFailureMapsToClosedProviderTimeoutWithUnavailableUsage() {
        ConversationalModelPort port = mock(ConversationalModelPort.class);
        when(port.generate(eq(QUESTION), any(ConversationWindow.class),
                any(ConversationRoute.class), any(PortfolioGroundingContext.class)))
                .thenReturn(ConversationModelResult.failure(
                        ConversationModelFailureCode.TIMEOUT));
        ProviderEvalExecutor executor = new ProviderEvalExecutor(port, "mock");

        EvalObservation observation = execute(executor);

        assertThat(observation.getStatus()).isEqualTo(EvalObservationStatus.FAIL);
        assertThat(observation.getReasonCodes()).contains("PROVIDER_TIMEOUT");
        assertThat(observation.getProviderUsage().isAvailable()).isFalse();
        assertThat(observation.isProviderInvoked()).isTrue();
    }

    @Test
    void emptyResponseMapsToProviderEmpty() {
        ConversationalModelPort port = mock(ConversationalModelPort.class);
        when(port.generate(eq(QUESTION), any(ConversationWindow.class),
                any(ConversationRoute.class), any(PortfolioGroundingContext.class)))
                .thenReturn(ConversationModelResult.success(new ConversationDraft(
                        "t", AnswerResolution.ANSWERED, List.of())));
        ProviderEvalExecutor executor = new ProviderEvalExecutor(port, "mock");

        EvalObservation observation = execute(executor);

        assertThat(observation.getReasonCodes()).contains("PROVIDER_EMPTY");
        assertThat(observation.getProviderUsage().isAvailable()).isFalse();
    }

    @Test
    void invalidResponseMapsToProviderInvalid() {
        ConversationalModelPort port = mock(ConversationalModelPort.class);
        when(port.generate(eq(QUESTION), any(ConversationWindow.class),
                any(ConversationRoute.class), any(PortfolioGroundingContext.class)))
                .thenReturn(ConversationModelResult.failure(
                        ConversationModelFailureCode.INVALID_RESPONSE));
        ProviderEvalExecutor executor = new ProviderEvalExecutor(port, "mock");

        EvalObservation observation = execute(executor);

        assertThat(observation.getReasonCodes()).contains("PROVIDER_INVALID");
    }

    @Test
    void fallbackResolutionMarksFailureWithoutClaimingSuccess() {
        ConversationalModelPort port = mock(ConversationalModelPort.class);
        when(port.generate(eq(QUESTION), any(ConversationWindow.class),
                any(ConversationRoute.class), any(PortfolioGroundingContext.class)))
                .thenReturn(ConversationModelResult.success(new ConversationDraft(
                        "t", AnswerResolution.NOT_SUPPORTED, List.of(
                                new ConversationAnswerBlock(
                                        ConversationSourceScope.PORTFOLIO,
                                        "无法回答", List.of(), List.of())))));
        ProviderEvalExecutor executor = new ProviderEvalExecutor(port, "mock");

        EvalObservation observation = execute(executor);

        assertThat(observation.getStatus()).isEqualTo(EvalObservationStatus.FAIL);
        assertThat(observation.getReasonCodes()).contains("PROVIDER_FALLBACK");
    }

    @Test
    void providerExceptionMapsToClosedProviderError() {
        ConversationalModelPort port = mock(ConversationalModelPort.class);
        when(port.generate(eq(QUESTION), any(ConversationWindow.class),
                any(ConversationRoute.class), any(PortfolioGroundingContext.class)))
                .thenThrow(new IllegalStateException("sensitive provider detail"));
        ProviderEvalExecutor executor = new ProviderEvalExecutor(port, "mock");

        EvalObservation observation = execute(executor);

        assertThat(observation.getReasonCodes()).contains("PROVIDER_ERROR");
        assertThat(observation.getStatus()).isEqualTo(EvalObservationStatus.FAIL);
    }

    private EvalObservation execute(ProviderEvalExecutor executor) {
        return executor.execute(
                new EvalExecutionInput("case-p", List.of(
                        new EvalMessage("user", QUESTION)),
                        EvalLayer.PROVIDER, 1),
                new EvalRunContext("run-1", "2026-08-06.1"));
    }
}
