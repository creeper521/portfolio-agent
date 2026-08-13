package com.portfolio.agent.answer.composition.adapter.model;

import com.portfolio.agent.answer.adapter.model.ProviderOperation;
import com.portfolio.agent.answer.service.DurationBuckets;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;
import java.util.Objects;

/** Publishes only closed enums, booleans, and duration buckets. */
public final class PortfolioExpressionDiagnostics {
    public enum FailureCode { EMPTY_RESPONSE, PROVIDER_ERROR }
    private final DiagnosticEventPublisher publisher;
    public PortfolioExpressionDiagnostics(DiagnosticEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }
    public void completed(long startedAt) {
        publish(DiagnosticEvent.builder("expression.provider.completed", DiagnosticLevel.DEBUG)
                .field("provider.operation", ProviderOperation.EXPRESS)
                .field("event.outcome", "success")
                .field("duration.bucket", duration(startedAt))
                .field("response.present", true)
                .field("output.size.bucket", "PRESENT").build());
    }
    public void failed(FailureCode failureCode, boolean responsePresent, long startedAt) {
        publish(DiagnosticEvent.builder("expression.provider.failed", DiagnosticLevel.WARN)
                .field("provider.operation", ProviderOperation.EXPRESS)
                .field("event.outcome", "failure")
                .field("duration.bucket", duration(startedAt))
                .field("response.present", responsePresent)
                .field("failure.code", failureCode).build());
    }
    private com.portfolio.agent.answer.domain.DurationBucket duration(long startedAt) {
        return DurationBuckets.fromElapsedMillis((System.nanoTime() - startedAt) / 1_000_000L);
    }
    private void publish(DiagnosticEvent event) {
        try { publisher.publish(event); } catch (RuntimeException ignored) { }
    }
}
