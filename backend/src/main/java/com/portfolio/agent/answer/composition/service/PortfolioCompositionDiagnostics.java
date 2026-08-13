package com.portfolio.agent.answer.composition.service;

import com.portfolio.agent.answer.composition.domain.ExpressionDisposition;
import com.portfolio.agent.answer.composition.domain.MaterialKind;
import com.portfolio.agent.answer.composition.domain.TaskKind;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;
import java.util.Objects;

/** Closed, content-free diagnostics emitted by the composition orchestration. */
public final class PortfolioCompositionDiagnostics {
    public enum FailureCode {
        CIRCUIT_OPEN, PROVIDER_FAILURE, EMPTY_RESPONSE, SCHEMA_INVALID,
        GROUNDING_INVALID, PLAN_INVALID
    }
    private final DiagnosticEventPublisher publisher;
    public PortfolioCompositionDiagnostics(DiagnosticEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }
    public static PortfolioCompositionDiagnostics noOp() {
        return new PortfolioCompositionDiagnostics(event -> { });
    }
    public void eligibility(TaskKind taskKind, MaterialKind materialKind,
            ExpressionDisposition disposition, boolean attempted, int inputSize,
            ExpressionCircuitBreaker.State breakerState) {
        publish(DiagnosticEvent.builder("expression.eligibility", DiagnosticLevel.DEBUG)
                .field("task.kind", taskKind)
                .field("material.kind", materialKind)
                .field("expression.disposition", disposition)
                .field("expression.attempted", attempted)
                .field("input.size.bucket", sizeBucket(inputSize))
                .field("breaker.state", breakerState).build());
    }
    public void validation(MaterialKind kind, boolean accepted, FailureCode failureCode) {
        DiagnosticEvent.Builder builder = DiagnosticEvent.builder(
                        "expression.validation.completed",
                        accepted ? DiagnosticLevel.DEBUG : DiagnosticLevel.WARN)
                .field("material.kind", kind)
                .field("validation.accepted", accepted)
                .field("failure.code", failureCode == null ? "NONE" : failureCode)
                .field("section.count.bucket", "BOUNDED")
                .field("sentence.count.bucket", "BOUNDED");
        publish(builder.build());
    }
    public void fallback(ExpressionDisposition disposition, FailureCode failureCode,
            ExpressionCircuitBreaker.State breakerState) {
        publish(DiagnosticEvent.builder("expression.fallback.used", DiagnosticLevel.WARN)
                .field("expression.disposition", disposition)
                .field("failure.code", failureCode)
                .field("breaker.state", breakerState)
                .field("expression.fallback", true).build());
    }
    private String sizeBucket(int size) {
        if (size <= 0) return "NONE";
        if (size <= 4_000) return "SMALL";
        if (size <= 8_000) return "MEDIUM";
        if (size <= 12_000) return "LARGE";
        return "OVER_LIMIT";
    }
    private void publish(DiagnosticEvent event) {
        try { publisher.publish(event); } catch (RuntimeException ignored) { }
    }
}
