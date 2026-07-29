package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.exception.AnswerRequestTimeoutException;
import com.portfolio.agent.common.observability.AnonymousSourceHasher;
import com.portfolio.agent.common.web.RequestContext;
import com.portfolio.agent.common.web.RequestContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionConversationServiceTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.clear();
        MDC.clear();
    }

    @Test
    void cancelsRuntimeWorkAndReturnsAStableTimeoutAfterTheBudget() {
        RequestContextHolder.set(RequestContext.create(null, null));
        ConversationalAgentRuntime runtime = mock(ConversationalAgentRuntime.class);
        when(runtime.answer(any())).thenAnswer(ignored -> {
            Thread.sleep(10_000);
            return mock(ConversationAnswerResult.class);
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ConversationAnswerRequest request = mock(ConversationAnswerRequest.class);
        when(request.getRequestToken()).thenReturn(UUID.randomUUID());
        ProductionConversationService service = new ProductionConversationService(
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

    @Test
    void propagatesTheServletRequestContextIntoTheVirtualThreadExecutor() throws Exception {
        RequestContext servletContext = RequestContext.create(
                "550e8400-e29b-41d4-a716-446655440000",
                "6ba7b810-9dad-11d1-80b4-00c04fd430c8");
        RequestContextHolder.set(servletContext);
        RequestContextHolder.enrichTurnId("6ba7b811-9dad-11d1-80b4-00c04fd430c8");
        AtomicReference<RequestContext> captured = new AtomicReference<>();
        ConversationalAgentRuntime runtime = mock(ConversationalAgentRuntime.class);
        ConversationAnswerResult result = mock(ConversationAnswerResult.class);
        when(runtime.answer(any())).thenAnswer(ignored -> {
            captured.set(RequestContextHolder.requireCurrent());
            return result;
        });
        ContextInspectingVirtualExecutor executor = new ContextInspectingVirtualExecutor();
        ConversationAnswerRequest request = mock(ConversationAnswerRequest.class);
        when(request.getRequestToken()).thenReturn(UUID.randomUUID());
        ProductionConversationService service = new ProductionConversationService(
                runtime,
                new AnonymousSourceHasher(new byte[32]),
                new AnswerAdmissionGate(Clock.systemUTC(), 10, 2),
                new AnswerIdempotencyCoordinator<>(Clock.systemUTC(), Duration.ofMinutes(2)),
                executor,
                Duration.ofSeconds(1)
        );

        try {
            assertThat(service.answer(request, "203.0.113.7")).isSameAs(result);
            assertThat(captured.get()).isNotNull();
            assertThat(captured.get()).isNotSameAs(servletContext);
            assertThat(captured.get().getRequestId()).isEqualTo(servletContext.getRequestId());
            assertThat(captured.get().getClientSessionId())
                    .isEqualTo(servletContext.getClientSessionId());
            assertThat(captured.get().getClientRequestId())
                    .isEqualTo(servletContext.getClientRequestId());
            assertThat(captured.get().getTurnId()).isEqualTo(servletContext.getTurnId());
            executor.awaitCleanup();
            assertThat(executor.wasVirtualThread()).isTrue();
            assertThat(executor.getContextAfterTask()).isEmpty();
            assertThat(executor.getMdcAfterTask()).isNullOrEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void virtualThreadContextIsCleanedWhenRuntimeThrows() throws Exception {
        RequestContextHolder.set(RequestContext.create(null, null));
        IllegalStateException failure = new IllegalStateException("expected runtime failure");
        ConversationalAgentRuntime runtime = mock(ConversationalAgentRuntime.class);
        when(runtime.answer(any())).thenThrow(failure);
        ContextInspectingVirtualExecutor executor = new ContextInspectingVirtualExecutor();
        ConversationAnswerRequest request = mock(ConversationAnswerRequest.class);
        when(request.getRequestToken()).thenReturn(UUID.randomUUID());
        ProductionConversationService service = new ProductionConversationService(
                runtime,
                new AnonymousSourceHasher(new byte[32]),
                new AnswerAdmissionGate(Clock.systemUTC(), 10, 2),
                new AnswerIdempotencyCoordinator<>(Clock.systemUTC(), Duration.ofMinutes(2)),
                executor,
                Duration.ofSeconds(1));

        try {
            assertThatThrownBy(() -> service.answer(request, "203.0.113.7")).isSameAs(failure);
            executor.awaitCleanup();
            assertThat(executor.wasVirtualThread()).isTrue();
            assertThat(executor.getContextAfterTask()).isEmpty();
            assertThat(executor.getMdcAfterTask()).isNullOrEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class ContextInspectingVirtualExecutor extends AbstractExecutorService {

        private final ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
        private final CountDownLatch cleanupCaptured = new CountDownLatch(1);
        private final AtomicReference<Optional<RequestContext>> contextAfterTask =
                new AtomicReference<>();
        private final AtomicReference<Map<String, String>> mdcAfterTask = new AtomicReference<>();
        private final AtomicReference<Boolean> virtualThread = new AtomicReference<>(false);

        @Override
        public void execute(Runnable command) {
            delegate.execute(() -> {
                virtualThread.set(Thread.currentThread().isVirtual());
                try {
                    command.run();
                } finally {
                    contextAfterTask.set(RequestContextHolder.current());
                    mdcAfterTask.set(MDC.getCopyOfContextMap());
                    cleanupCaptured.countDown();
                }
            });
        }

        void awaitCleanup() throws InterruptedException {
            assertThat(cleanupCaptured.await(1, TimeUnit.SECONDS)).isTrue();
        }

        boolean wasVirtualThread() {
            return virtualThread.get();
        }

        Optional<RequestContext> getContextAfterTask() {
            return contextAfterTask.get();
        }

        Map<String, String> getMdcAfterTask() {
            return mdcAfterTask.get();
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }
    }
}
