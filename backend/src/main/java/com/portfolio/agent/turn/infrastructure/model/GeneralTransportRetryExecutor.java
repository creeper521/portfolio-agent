package com.portfolio.agent.turn.infrastructure.model;

import com.portfolio.agent.common.observability.ModelOutputDiagnostics;
import com.portfolio.agent.infrastructure.model.ModelTransportBinding;
import com.portfolio.agent.infrastructure.model.ProviderAttemptContext;
import com.portfolio.agent.infrastructure.model.StructuredModelFailure;
import com.portfolio.agent.infrastructure.model.StructuredModelRequest;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.structured.OperationBinding;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputCompiler;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputGateway;
import com.portfolio.agent.infrastructure.model.structured.StructurallyValidatedOutput;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Qwen General v4 的有界传输重试所有者。
 *
 * <p>Gateway 每次仍只执行一个 HTTP attempt；本类只对获批连接故障、
 * 无响应 timeout、502/503/504 和受限 429 编排最多一次重试。两次调用复用
 * 同一 binding、request、compiler 和 absolute deadline，不处理任何内容、
 * schema 或语义失败。</p>
 */
final class GeneralTransportRetryExecutor {
    static final long MINIMUM_RETRY_BUDGET_MILLIS = 3_000L;
    static final long FIRST_ATTEMPT_SAFETY_MARGIN_MILLIS = 250L;
    static final int MINIMUM_JITTER_MILLIS = 100;
    static final int MAXIMUM_JITTER_MILLIS = 250;

    private final StructuredOutputGateway gateway;
    private final ModelOutputDiagnostics diagnostics;
    private final Sleeper sleeper;
    private final IntSupplier jitterMillis;
    private final Supplier<UUID> attemptIdSupplier;

    GeneralTransportRetryExecutor(StructuredOutputGateway gateway) {
        this(gateway, ModelOutputDiagnostics.none());
    }

    GeneralTransportRetryExecutor(
            StructuredOutputGateway gateway,
            ModelOutputDiagnostics diagnostics) {
        this(gateway, diagnostics, Thread::sleep,
                () -> ThreadLocalRandom.current().nextInt(
                        MINIMUM_JITTER_MILLIS,
                        MAXIMUM_JITTER_MILLIS + 1),
                UUID::randomUUID);
    }

    GeneralTransportRetryExecutor(
            StructuredOutputGateway gateway,
            Sleeper sleeper,
            IntSupplier jitterMillis,
            Supplier<UUID> attemptIdSupplier) {
        this(gateway, ModelOutputDiagnostics.none(), sleeper,
                jitterMillis, attemptIdSupplier);
    }

    GeneralTransportRetryExecutor(
            StructuredOutputGateway gateway,
            ModelOutputDiagnostics diagnostics,
            Sleeper sleeper,
            IntSupplier jitterMillis,
            Supplier<UUID> attemptIdSupplier) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.jitterMillis = Objects.requireNonNull(
                jitterMillis, "jitterMillis");
        this.attemptIdSupplier = Objects.requireNonNull(
                attemptIdSupplier, "attemptIdSupplier");
    }

    StructurallyValidatedOutput execute(
            ModelTransportBinding binding,
            StructuredModelRequest request,
            StructuredOutputCompiler compiler) {
        ModelTransportBinding frozenBinding = Objects.requireNonNull(
                binding, "binding");
        StructuredModelRequest frozenRequest = Objects.requireNonNull(
                request, "request");
        StructuredOutputCompiler frozenCompiler = Objects.requireNonNull(
                compiler, "compiler");
        if (frozenRequest.operation() != ModelOperation.GENERAL_KNOWLEDGE
                || !OperationBinding.GENERAL_DRAFT_OUTPUT_COMPILER_VERSION
                        .equals(frozenCompiler.profileVersion())) {
            throw new IllegalArgumentException(
                    "transport retry is only approved for General draft v4");
        }

        Duration firstAttemptTimeoutCap = firstAttemptTimeoutCap(
                frozenRequest.deadline().remainingMillis());
        Set<UUID> attemptIds = new HashSet<>(2);
        for (int attempt = 1; attempt <= 2; attempt++) {
            UUID attemptId = Objects.requireNonNull(
                    attemptIdSupplier.get(), "providerAttemptId");
            if (!attemptIds.add(attemptId)) {
                throw new IllegalStateException(
                        "provider attempt identity must be unique");
            }
            ProviderAttemptContext attemptContext =
                    new ProviderAttemptContext(
                            attemptId, attempt, 2, attempt > 1,
                            attempt == 1 ? firstAttemptTimeoutCap : null);
            try {
                return gateway.execute(
                        frozenBinding, frozenRequest, frozenCompiler,
                        attemptContext);
            } catch (StructuredModelFailure failure) {
                if (attempt == 2) {
                    throw failure;
                }
                long delayMillis = retryDelayMillis(failure);
                if (delayMillis < 0L
                        || frozenRequest.deadline().remainingMillis()
                                < delayMillis
                                + MINIMUM_RETRY_BUDGET_MILLIS) {
                    throw failure;
                }
                waitBeforeRetry(delayMillis);
                if (frozenRequest.deadline().remainingMillis()
                        < MINIMUM_RETRY_BUDGET_MILLIS) {
                    throw failure;
                }
                diagnostics.retryScheduled(
                        2, 2, failure.getCode().name(),
                        waitBucket(failure));
            }
        }
        throw new IllegalStateException("unreachable retry state");
    }

    private Duration firstAttemptTimeoutCap(long initialRemainingMillis) {
        long reservedMillis = MINIMUM_RETRY_BUDGET_MILLIS
                + FIRST_ATTEMPT_SAFETY_MARGIN_MILLIS;
        if (initialRemainingMillis <= reservedMillis) {
            return null;
        }
        return Duration.ofMillis(initialRemainingMillis - reservedMillis);
    }

    private long retryDelayMillis(StructuredModelFailure failure) {
        return switch (failure.getCode()) {
            case DEADLINE_EXCEEDED ->
                    failure.getTimeoutDisposition()
                            == StructuredModelFailure.TimeoutDisposition.NO_RESPONSE
                            ? 0L : -1L;
            case TRANSPORT_UNAVAILABLE ->
                    failure.getTransportDisposition()
                            == StructuredModelFailure.TransportDisposition.CONNECTION
                            ? 0L : -1L;
            case PROVIDER_UNAVAILABLE -> approvedUnavailableStatus(
                    failure.getHttpStatus()) ? 0L : -1L;
            case RATE_LIMITED -> rateLimitDelayMillis(failure);
            case AUTHENTICATION_REJECTED, BILLING_REJECTED,
                    PROVIDER_REJECTED, RESPONSE_TOO_LARGE,
                    RESPONSE_JSON_INVALID, RESPONSE_ENVELOPE_INVALID,
                    INVALID_RESPONSE -> -1L;
        };
    }

    private boolean approvedUnavailableStatus(Integer status) {
        return status != null
                && (status == 502 || status == 503 || status == 504);
    }

    private long rateLimitDelayMillis(StructuredModelFailure failure) {
        return switch (failure.getRetryAfterDisposition()) {
            case MISSING -> boundedJitterMillis();
            case VALID -> failure.getRetryAfterSeconds() != null
                    && failure.getRetryAfterSeconds() <= 1
                    ? failure.getRetryAfterSeconds() * 1_000L
                    : -1L;
            case INVALID, NOT_APPLICABLE -> -1L;
        };
    }

    private int boundedJitterMillis() {
        int value = jitterMillis.getAsInt();
        if (value < MINIMUM_JITTER_MILLIS
                || value > MAXIMUM_JITTER_MILLIS) {
            throw new IllegalStateException(
                    "retry jitter must be between 100 and 250 milliseconds");
        }
        return value;
    }

    private String waitBucket(StructuredModelFailure failure) {
        if (failure.getCode() != StructuredModelFailure.Code.RATE_LIMITED) {
            return "NO_WAIT";
        }
        return failure.getRetryAfterDisposition()
                == StructuredModelFailure.RetryAfterDisposition.MISSING
                ? "JITTER_100_250_MS"
                : "RETRY_AFTER_LE_1S";
    }

    private void waitBeforeRetry(long delayMillis) {
        if (delayMillis == 0L) {
            return;
        }
        try {
            sleeper.sleep(delayMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw StructuredModelFailure.interrupted(interrupted);
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
