package com.portfolio.agent.common.observability;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public final class ApplicationStartupDiagnostics
        implements ApplicationListener<ApplicationReadyEvent> {

    private static final Set<String> RETRIEVAL_PROFILES = Set.of(
            "DISABLED",
            "KEYWORD_ONLY",
            "HYBRID");

    private final DiagnosticEventPublisher publisher;
    private final boolean modelExpressionEnabled;
    private final boolean conversationEnabled;
    private final String retrievalProfile;
    private final long answerRequestTimeoutMillis;
    private final int answerRequestsPerMinute;
    private final int answerMaxConcurrent;

    public ApplicationStartupDiagnostics(
            DiagnosticEventPublisher publisher,
            boolean modelExpressionEnabled,
            boolean conversationEnabled,
            String retrievalProfile,
            long answerRequestTimeoutMillis,
            int answerRequestsPerMinute,
            int answerMaxConcurrent
    ) {
        this.publisher = Objects.requireNonNull(
                publisher, "diagnostic event publisher must not be null");
        if (!RETRIEVAL_PROFILES.contains(retrievalProfile)) {
            throw new IllegalArgumentException("unsupported retrieval profile");
        }
        if (answerRequestTimeoutMillis <= 0
                || answerRequestsPerMinute <= 0
                || answerMaxConcurrent <= 0) {
            throw new IllegalArgumentException(
                    "answer startup diagnostic values must be positive");
        }
        this.modelExpressionEnabled = modelExpressionEnabled;
        this.conversationEnabled = conversationEnabled;
        this.retrievalProfile = retrievalProfile;
        this.answerRequestTimeoutMillis = answerRequestTimeoutMillis;
        this.answerRequestsPerMinute = answerRequestsPerMinute;
        this.answerMaxConcurrent = answerMaxConcurrent;
    }

    public void contentBundleLoaded(
            String schemaVersion,
            String contentVersion,
            boolean retrievalEnabled,
            int documentCount,
            int vectorDimension,
            long elapsedMillis
    ) {
        publishBestEffort(() -> DiagnosticEvent.builder(
                        "content.bundle.loaded", DiagnosticLevel.INFO)
                .field("schema.version", schemaVersion)
                .field("content.version", contentVersion)
                .field("retrieval.enabled", retrievalEnabled)
                .field("document.count", documentCount)
                .field("vector.dimension", vectorDimension)
                .field("duration.bucket", durationBucket(elapsedMillis))
                .build());
    }

    public void contentBundleFailed() {
        publishFailure(
                "application.startup.failed",
                StartupFailureCode.CONTENT_BUNDLE_INVALID);
    }

    public void embeddingModelLoaded(int vectorDimension, long elapsedMillis) {
        publishBestEffort(() -> DiagnosticEvent.builder(
                        "embedding.model.loaded", DiagnosticLevel.INFO)
                .field("vector.dimension", vectorDimension)
                .field("duration.bucket", durationBucket(elapsedMillis))
                .build());
    }

    public void embeddingModelFailed() {
        publishFailure(
                "embedding.model.failed",
                StartupFailureCode.RETRIEVAL_MODEL_LOAD_FAILED);
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        publishBestEffort(() -> DiagnosticEvent.builder(
                        "application.started", DiagnosticLevel.INFO)
                .field("model_expression.enabled", modelExpressionEnabled)
                .field("conversation.enabled", conversationEnabled)
                .field("retrieval.profile", retrievalProfile)
                .field("answer.request_timeout_ms", answerRequestTimeoutMillis)
                .field("answer.requests_per_minute", answerRequestsPerMinute)
                .field("answer.max_concurrent", answerMaxConcurrent)
                .build());
    }

    private void publishFailure(String eventName, StartupFailureCode failureCode) {
        publishBestEffort(() -> DiagnosticEvent.builder(eventName, DiagnosticLevel.ERROR)
                .field("failure.code", failureCode)
                .build());
    }

    private String durationBucket(long elapsedMillis) {
        if (elapsedMillis < 100) {
            return "LT_100_MS";
        }
        if (elapsedMillis < 500) {
            return "FROM_100_TO_499_MS";
        }
        if (elapsedMillis < 2000) {
            return "FROM_500_TO_1999_MS";
        }
        return "GE_2000_MS";
    }

    private void publishBestEffort(Supplier<DiagnosticEvent> eventFactory) {
        try {
            publisher.publish(eventFactory.get());
        } catch (RuntimeException ignored) {
            // Diagnostic publication must never change startup behavior.
        }
    }

    public enum StartupFailureCode implements DiagnosticCode {
        CONTENT_BUNDLE_INVALID,
        RETRIEVAL_MODEL_LOAD_FAILED;

        @Override
        public String code() {
            return name();
        }
    }
}
