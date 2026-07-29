package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.exception.AnswerRequestTimeoutException;
import com.portfolio.agent.common.observability.AnonymousSourceHasher;
import com.portfolio.agent.common.web.RequestContext;
import com.portfolio.agent.common.web.RequestContextHolder;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ProductionConversationService {

    private final ConversationalAgentRuntime runtime;
    private final AnonymousSourceHasher sourceHasher;
    private final AnswerAdmissionGate admissionGate;
    private final AnswerIdempotencyCoordinator<ConversationAnswerResult> idempotency;
    private final ExecutorService executor;
    private final Duration timeout;

    public ProductionConversationService(
            ConversationalAgentRuntime runtime,
            AnonymousSourceHasher sourceHasher,
            AnswerAdmissionGate admissionGate,
            AnswerIdempotencyCoordinator<ConversationAnswerResult> idempotency,
            ExecutorService executor,
            Duration timeout
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
        this.sourceHasher = Objects.requireNonNull(sourceHasher, "sourceHasher must not be null");
        this.admissionGate = Objects.requireNonNull(admissionGate, "admissionGate must not be null");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
    }

    public ConversationAnswerResult answer(ConversationAnswerRequest request, String clientAddress) {
        Objects.requireNonNull(request, "request must not be null");
        String sourceHash = sourceHasher.hash(
                Objects.requireNonNull(clientAddress, "clientAddress must not be null"));
        return idempotency.execute(sourceHash, request.getRequestToken(), () -> {
            try (AnswerAdmission admission = admissionGate.acquire(sourceHash, request.getRequestToken())) {
                return executeWithinBudget(request);
            }
        });
    }

    private ConversationAnswerResult executeWithinBudget(ConversationAnswerRequest request) {
        RequestContext context = RequestContextHolder.requireCurrent().copy();
        Future<ConversationAnswerResult> future = executor.submit(
                () -> RequestContextHolder.callWith(context, () -> runtime.answer(request)));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new AnswerRequestTimeoutException();
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new AnswerRequestTimeoutException();
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (exception.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("conversation execution failed", exception.getCause());
        }
    }
}
