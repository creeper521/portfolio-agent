package com.portfolio.agent.answer.composition.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.answer.composition.domain.ExpressionDisposition;
import com.portfolio.agent.answer.composition.domain.MaterialKind;
import com.portfolio.agent.answer.composition.domain.TaskKind;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioCompositionDiagnosticsTest {

    @Test
    void emitsOnlyClosedContentFreeFieldsForEligibilityValidationAndFallback() {
        List<DiagnosticEvent> events = new ArrayList<>();
        PortfolioCompositionDiagnostics diagnostics =
                new PortfolioCompositionDiagnostics(events::add);

        diagnostics.eligibility(TaskKind.FACT, MaterialKind.FACT,
                ExpressionDisposition.ACCEPTED, true, 4200,
                ExpressionCircuitBreaker.State.CLOSED);
        diagnostics.validation(MaterialKind.FACT, false,
                PortfolioCompositionDiagnostics.FailureCode.GROUNDING_INVALID);
        diagnostics.fallback(ExpressionDisposition.FALLBACK_GROUNDING_INVALID,
                PortfolioCompositionDiagnostics.FailureCode.GROUNDING_INVALID,
                ExpressionCircuitBreaker.State.CLOSED);

        assertThat(events).extracting(DiagnosticEvent::getName).containsExactly(
                "expression.eligibility", "expression.validation.completed",
                "expression.fallback.used");
        String serialized = events.toString();
        assertThat(serialized)
                .doesNotContain("question", "goalLabel", "conversationId", "turnId",
                        "S001", "P01", "REF-SECRET", "api-key", "response", "prompt");
        assertThat(events).allSatisfy(event -> assertThat(event.getFields().values())
                .allMatch(value -> value instanceof String || value instanceof Boolean
                        || value instanceof Number));
    }
}
