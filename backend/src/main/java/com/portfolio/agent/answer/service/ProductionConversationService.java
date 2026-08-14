package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.context.domain.CompletionReceipt;
import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ConversationContinuationStatus;
import com.portfolio.agent.answer.context.domain.RequestFingerprint;
import com.portfolio.agent.answer.context.service.RequestReceiptService;
import com.portfolio.agent.answer.context.service.ConversationContextCommitter;
import com.portfolio.agent.answer.context.gateway.RequestReceiptStore;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.exception.AnswerRequestTimeoutException;
import com.portfolio.agent.common.observability.AnonymousSourceHasher;
import com.portfolio.agent.common.web.RequestContext;
import com.portfolio.agent.common.web.RequestContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    private final Optional<RequestReceiptService> requestReceiptService;
    private final Optional<ConversationContextCommitter> contextCommitter;

    public ProductionConversationService(
            ConversationalAgentRuntime runtime,
            AnonymousSourceHasher sourceHasher,
            AnswerAdmissionGate admissionGate,
            AnswerIdempotencyCoordinator<ConversationAnswerResult> idempotency,
            ExecutorService executor,
            Duration timeout
    ) {
        this(runtime, sourceHasher, admissionGate, idempotency, executor, timeout, Optional.empty());
    }

    public ProductionConversationService(
            ConversationalAgentRuntime runtime,
            AnonymousSourceHasher sourceHasher,
            AnswerAdmissionGate admissionGate,
            AnswerIdempotencyCoordinator<ConversationAnswerResult> idempotency,
            ExecutorService executor,
            Duration timeout,
            Optional<RequestReceiptService> requestReceiptService
    ) {
        this(runtime, sourceHasher, admissionGate, idempotency, executor, timeout,
                requestReceiptService, Optional.empty());
    }

    public ProductionConversationService(
            ConversationalAgentRuntime runtime,
            AnonymousSourceHasher sourceHasher,
            AnswerAdmissionGate admissionGate,
            AnswerIdempotencyCoordinator<ConversationAnswerResult> idempotency,
            ExecutorService executor,
            Duration timeout,
            Optional<RequestReceiptService> requestReceiptService,
            Optional<ConversationContextCommitter> contextCommitter
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
        this.sourceHasher = Objects.requireNonNull(sourceHasher, "sourceHasher must not be null");
        this.admissionGate = Objects.requireNonNull(admissionGate, "admissionGate must not be null");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.requestReceiptService = Objects.requireNonNull(requestReceiptService,
                "requestReceiptService must not be null");
        this.contextCommitter = Objects.requireNonNull(contextCommitter,
                "contextCommitter must not be null");
    }

    public ConversationAnswerResult answer(ConversationAnswerRequest request, String clientAddress) {
        return answer(request, clientAddress, null);
    }

    public ConversationAnswerResult answer(
            ConversationAnswerRequest request,
            String clientAddress,
            ConversationRequestContext requestContext) {
        Objects.requireNonNull(request, "request must not be null");
        String sourceHash = sourceHasher.hash(
                Objects.requireNonNull(clientAddress, "clientAddress must not be null"));
        return idempotency.execute(sourceHash, request.getRequestToken(), () -> {
            try (AnswerAdmission admission = admissionGate.acquire(sourceHash, request.getRequestToken())) {
                return executeWithinBudget(request, requestContext);
            }
        });
    }

    /** Executes a request at the persistent receipt boundary when a Context session is available. */
    public ProductionConversationExecution execute(
            ConversationAnswerRequest request,
            String clientAddress,
            ConversationRequestContext requestContext) {
        Objects.requireNonNull(request, "request must not be null");
        if (requestReceiptService.isEmpty() || requestContext == null) {
            return ProductionConversationExecution.answer(answer(request, clientAddress, requestContext));
        }
        RequestFingerprint fingerprint = fingerprint(request);
        ContextHandle parent = requestContext.getContextReference()
                .map(com.portfolio.agent.answer.routing.domain.AuthorizedContextReference::getContextHandle)
                .map(ContextHandle::fromBase64Url)
                .orElse(null);
        Instant now = Instant.now();
        RequestReceiptStore.ClaimResult claim = requestReceiptService.orElseThrow().claim(
                request.getRequestToken(), requestContext.getConversationId(), requestContext.getResumeToken(),
                fingerprint, parent, now);
        if (claim.getStatus() == RequestReceiptStore.ClaimResult.Status.ALREADY_COMPLETED) {
            return ProductionConversationExecution.receipt(claim.getCompletionReceipt().orElseThrow());
        }
        if (claim.getStatus() == RequestReceiptStore.ClaimResult.Status.IN_PROGRESS) {
            throw new RequestReceiptInProgressException(
                    claim.getRetryAfter().orElse(Duration.ofSeconds(1)));
        }
        if (claim.getStatus() == RequestReceiptStore.ClaimResult.Status.IDEMPOTENCY_KEY_CONFLICT) {
            throw new RequestReceiptConflictException();
        }
        UUID leaseId = claim.getLeaseId().orElseThrow();
        ConversationAnswerResult result = answer(request, clientAddress, requestContext);
        Map<String, ContextHandle> contextHandles = Map.of();
        ConversationContinuationStatus continuationStatus =
                ConversationContinuationStatus.NOT_APPLICABLE;
        try {
            if (contextCommitter.isPresent()) {
                contextHandles = contextCommitter.orElseThrow().commit(result, requestContext, Instant.now());
                continuationStatus = contextHandles.isEmpty()
                        ? ConversationContinuationStatus.NOT_APPLICABLE
                        : ConversationContinuationStatus.AVAILABLE;
            }
        } catch (RuntimeException ignored) {
            // A valid answer must survive a best-effort Context write failure.
            continuationStatus = ConversationContinuationStatus.PERSISTENCE_UNAVAILABLE;
        }
        ContextHandle continuationHandle = contextHandles.values().stream().findFirst().orElse(null);
        requestReceiptService.orElseThrow().complete(
                request.getRequestToken(), leaseId, requestContext.getConversationId(), fingerprint,
                continuationHandle,
                continuationStatus,
                Instant.now());
        // The answer remains the primary response. The receipt is persisted for a retry and is not
        // returned here, so a successful first response is still an ANSWER response.
        return ProductionConversationExecution.answer(result, contextHandles, continuationStatus);
    }

    /** Finds a completed receipt only when the submitted request still has the same safe fingerprint. */
    public Optional<CompletionReceipt> findCompleted(ConversationAnswerRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (requestReceiptService.isEmpty()) {
            return Optional.empty();
        }
        try {
            return requestReceiptService.orElseThrow()
                    .findCompleted(request.getRequestToken(), Instant.now())
                    .filter(receipt -> receipt.getFingerprint().equals(fingerprint(request)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static RequestFingerprint fingerprint(ConversationAnswerRequest request) {
        StringBuilder canonical = new StringBuilder("p3-request-v1");
        append(canonical, request.getTurnId());
        append(canonical, request.getQuestionPresetId());
        append(canonical, request.getContractVersion());
        append(canonical, request.getAction().name());
        append(canonical, request.getQuestion());
        append(canonical, request.getAgentTurnContract());
        request.getMessages().forEach(message -> {
            append(canonical, message.getRole().name());
            append(canonical, message.getContent());
        });
        if (request.getContextReference() != null) {
            append(canonical, request.getContextReference().getContextHandle());
            append(canonical, request.getContextReference().getExpectedContextType().name());
            append(canonical, request.getContextReference().getResultItemId());
        } else {
            append(canonical, "context:none");
        }
        return RequestFingerprint.sha256Canonical(canonical.toString());
    }

    private static void append(StringBuilder canonical, String value) {
        String safe = value == null ? "<null>" : value;
        canonical.append('|').append(safe.length()).append(':').append(safe);
    }

    private ConversationAnswerResult executeWithinBudget(
            ConversationAnswerRequest request, ConversationRequestContext requestContext) {
        RequestContext context = RequestContextHolder.requireCurrent().copy();
        Future<ConversationAnswerResult> future = executor.submit(
                () -> RequestContextHolder.callWith(context,
                        () -> requestContext == null
                                ? runtime.answer(request)
                                : runtime.answer(request, requestContext)));
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
