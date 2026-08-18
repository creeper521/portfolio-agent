package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.context.domain.CompletionReceipt;
import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ConversationContinuationStatus;
import com.portfolio.agent.answer.context.domain.RequestFingerprint;
import com.portfolio.agent.answer.context.gateway.RequestReceiptStore;
import com.portfolio.agent.answer.context.service.ConversationContextCommitter;
import com.portfolio.agent.answer.context.service.RequestReceiptService;
import com.portfolio.agent.answer.exception.AnswerRequestTimeoutException;
import com.portfolio.agent.answer.service.AnswerAdmission;
import com.portfolio.agent.answer.service.AnswerAdmissionGate;
import com.portfolio.agent.answer.service.AnswerIdempotencyCoordinator;
import com.portfolio.agent.answer.service.ConversationRequestContext;
import com.portfolio.agent.answer.service.ProductionConversationExecution;
import com.portfolio.agent.answer.service.RequestReceiptConflictException;
import com.portfolio.agent.answer.service.RequestReceiptInProgressException;
import com.portfolio.agent.common.observability.AnonymousSourceHasher;
import com.portfolio.agent.common.web.RequestContext;
import com.portfolio.agent.common.web.RequestContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Slice-1 production guard around the new command runtime; replaced by LifecycleService in Slice 5. */
public final class MigrationProductionTurnService {
    private final MigrationAgentTurnRuntime runtime;
    private final AnonymousSourceHasher sourceHasher;
    private final AnswerAdmissionGate admissionGate;
    private final AnswerIdempotencyCoordinator<ConversationAnswerResult> idempotency;
    private final ExecutorService executor;
    private final Duration timeout;
    private final Optional<RequestReceiptService> requestReceiptService;
    private final Optional<ConversationContextCommitter> contextCommitter;

    public MigrationProductionTurnService(
            MigrationAgentTurnRuntime runtime,
            AnonymousSourceHasher sourceHasher,
            AnswerAdmissionGate admissionGate,
            AnswerIdempotencyCoordinator<ConversationAnswerResult> idempotency,
            ExecutorService executor,
            Duration timeout,
            Optional<RequestReceiptService> requestReceiptService,
            Optional<ConversationContextCommitter> contextCommitter) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.sourceHasher = Objects.requireNonNull(sourceHasher, "sourceHasher");
        this.admissionGate = Objects.requireNonNull(admissionGate, "admissionGate");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.requestReceiptService = Objects.requireNonNull(requestReceiptService, "requestReceiptService");
        this.contextCommitter = Objects.requireNonNull(contextCommitter, "contextCommitter");
    }

    public ConversationAnswerResult answer(AgentTurnCommand command, String clientAddress) {
        Objects.requireNonNull(command, "command");
        String sourceHash = sourceHasher.hash(Objects.requireNonNull(clientAddress, "clientAddress"));
        return idempotency.execute(
                sourceHash, command.getRequestId(), fingerprint(command).value(), () -> {
                    try (AnswerAdmission ignored = admissionGate.acquire(
                            sourceHash, command.getRequestId())) {
                        return executeWithinBudget(command);
                    }
                });
    }

    public ProductionConversationExecution execute(
            AgentTurnCommand command,
            String clientAddress,
            ConversationRequestContext requestContext) {
        if (requestReceiptService.isEmpty() || requestContext == null) {
            return ProductionConversationExecution.answer(answer(command, clientAddress));
        }
        RequestFingerprint fingerprint = fingerprint(command);
        ContextHandle parent = requestContext.getContextReference()
                .map(com.portfolio.agent.answer.routing.domain.AuthorizedContextReference::getContextHandle)
                .map(ContextHandle::fromBase64Url).orElse(null);
        Instant now = Instant.now();
        RequestReceiptStore.ClaimResult claim = requestReceiptService.orElseThrow().claim(
                command.getRequestId(), requestContext.getConversationId(), requestContext.getResumeToken(),
                fingerprint, parent, now);
        if (claim.getStatus() == RequestReceiptStore.ClaimResult.Status.ALREADY_COMPLETED) {
            return ProductionConversationExecution.receipt(claim.getCompletionReceipt().orElseThrow());
        }
        if (claim.getStatus() == RequestReceiptStore.ClaimResult.Status.IN_PROGRESS) {
            throw new RequestReceiptInProgressException(claim.getRetryAfter().orElse(Duration.ofSeconds(1)));
        }
        if (claim.getStatus() == RequestReceiptStore.ClaimResult.Status.IDEMPOTENCY_KEY_CONFLICT) {
            throw new RequestReceiptConflictException();
        }
        UUID leaseId = claim.getLeaseId().orElseThrow();
        ConversationAnswerResult result = answer(command, clientAddress);
        Map<String, ContextHandle> handles = Map.of();
        ConversationContinuationStatus status = ConversationContinuationStatus.NOT_APPLICABLE;
        try {
            if (contextCommitter.isPresent()) {
                handles = contextCommitter.orElseThrow().commit(result, requestContext, Instant.now());
                status = handles.isEmpty() ? ConversationContinuationStatus.NOT_APPLICABLE
                        : ConversationContinuationStatus.AVAILABLE;
            }
        } catch (RuntimeException failure) {
            status = ConversationContinuationStatus.PERSISTENCE_UNAVAILABLE;
        }
        requestReceiptService.orElseThrow().complete(
                command.getRequestId(), leaseId, requestContext.getConversationId(), fingerprint,
                handles.values().stream().findFirst().orElse(null), status, Instant.now());
        return ProductionConversationExecution.answer(result, handles, status);
    }

    public Optional<CompletionReceipt> findCompleted(AgentTurnCommand command) {
        if (requestReceiptService.isEmpty()) return Optional.empty();
        return requestReceiptService.orElseThrow().findCompleted(command.getRequestId(), Instant.now())
                .filter(receipt -> receipt.getFingerprint().equals(fingerprint(command)));
    }

    private RequestFingerprint fingerprint(AgentTurnCommand command) {
        StringBuilder value = new StringBuilder(command.getClass().getSimpleName());
        if (command instanceof AgentTurnCommand.Ask ask) {
            value.append('|').append(ask.getInput().getClass().getSimpleName());
            if (ask.getInput() instanceof AgentTurnCommand.FreeText freeText) {
                value.append('|').append(freeText.getText());
            } else if (ask.getInput() instanceof AgentTurnCommand.Preset preset) {
                value.append('|').append(preset.getPresetId()).append('|').append(preset.getPresetRevision());
            }
        } else if (command instanceof AgentTurnCommand.Continue continuation) {
            value.append('|').append(continuation.getContextHandle())
                    .append('|').append(continuation.getResultItemId().orElse(""))
                    .append('|').append(continuation.getText());
        } else if (command instanceof AgentTurnCommand.ResolveClarification clarification) {
            value.append('|').append(clarification.getClarificationId())
                    .append('|').append(clarification.getAnswer().getClass().getSimpleName());
        }
        command.getConversationWindow().getMessages().forEach(message -> value
                .append('|').append(message.getRole()).append(':').append(message.getText()));
        return RequestFingerprint.sha256Canonical(value.toString());
    }

    private ConversationAnswerResult executeWithinBudget(AgentTurnCommand command) {
        RequestContext requestContext = RequestContextHolder.requireCurrent().copy();
        Future<ConversationAnswerResult> future = executor.submit(
                () -> RequestContextHolder.callWith(requestContext, () -> runtime.answer(command)));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException failure) {
            future.cancel(true);
            throw new AnswerRequestTimeoutException();
        } catch (InterruptedException failure) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new AnswerRequestTimeoutException();
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (failure.getCause() instanceof Error error) throw error;
            throw new IllegalStateException("turn execution failed", failure.getCause());
        }
    }
}
