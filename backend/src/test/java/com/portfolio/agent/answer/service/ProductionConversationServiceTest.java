package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.exception.AnswerRequestTimeoutException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionConversationServiceTest {

    @Test
    void cancelsRuntimeWorkAndReturnsAStableTimeoutAfterTheBudget() {
        var runtime = mock(ConversationalAgentRuntime.class);
        when(runtime.answer(any())).thenAnswer(ignored -> {
            Thread.sleep(10_000);
            return mock(ConversationAnswerResult.class);
        });
        var executor = Executors.newSingleThreadExecutor();
        var request = mock(ConversationAnswerRequest.class);
        when(request.getRequestToken()).thenReturn(UUID.randomUUID());
        var service = new ProductionConversationService(
                runtime,
                new AnonymousSourceHasher(new byte[32]),
                new AnswerAdmissionGate(Clock.systemUTC(), 10, 2),
                new AnswerIdempotencyCoordinator<>(Clock.systemUTC(), Duration.ofMinutes(2)),
                executor,
                Duration.ofMillis(20)
        );

        try {
            assertThatThrownBy(() -> service.answer(
                    request, "203.0.113.7"))
                    .isInstanceOf(AnswerRequestTimeoutException.class);
        } finally {
            executor.shutdownNow();
        }
    }
}
