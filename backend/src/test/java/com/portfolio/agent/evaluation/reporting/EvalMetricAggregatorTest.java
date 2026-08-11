package com.portfolio.agent.evaluation.reporting;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.evaluation.domain.EvalAnswerShape;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalProviderUsage;
import com.portfolio.agent.evaluation.domain.EvalSeverity;
import com.portfolio.agent.evaluation.grading.EvalGrade;
import com.portfolio.agent.evaluation.grading.EvalReasonCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvalMetricAggregatorTest {

    private final EvalMetricAggregator aggregator = new EvalMetricAggregator();

    @Test
    void aggregatesRatesAndPreservesRawNumeratorAndDenominator() {
        EvalGrade passing = grade("case-1", EvalLayer.INTELLIGENCE,
                "SUBJECT_MATCH", EvalSeverity.SCORED, true, EvalReasonCode.PASS, 1, 1);
        EvalGrade failing = grade("case-2", EvalLayer.INTELLIGENCE,
                "SUBJECT_MATCH", EvalSeverity.BLOCKING, false,
                EvalReasonCode.SUBJECT_MISMATCH, 0, 1);
        EvalGrade quality = grade("case-1", EvalLayer.INTELLIGENCE,
                "ANSWER_QUALITY", EvalSeverity.SCORED, true, EvalReasonCode.PASS, 1, 1);

        EvalMetrics metrics = aggregator.aggregate(
                List.of(passing, failing, quality),
                List.of(),
                2, 1);

        EvalMetrics.MetricValue routing = metrics.getValue("routing.top1");
        assertThat(routing.getValue()).isEqualByComparingTo("0.5");
        assertThat(routing.getNumerator()).isEqualTo(1L);
        assertThat(routing.getDenominator()).isEqualTo(2L);
        assertThat(metrics.getValue("structure.answerQualityPassRate").getValue())
                .isEqualByComparingTo("1.0");
        assertThat(metrics.getValue("content.smokeCoverage").getValue())
                .isEqualByComparingTo("0.5");
    }

    @Test
    void countsHardErrorsPerFixedReasonCode() {
        EvalGrade fake = grade("case-1", EvalLayer.INTELLIGENCE,
                "REFERENCE_INTEGRITY", EvalSeverity.BLOCKING, false,
                EvalReasonCode.FAKE_CITATION, 0, 1);
        EvalGrade sufficient = grade("case-2", EvalLayer.INTELLIGENCE,
                "RESOLUTION", EvalSeverity.BLOCKING, false,
                EvalReasonCode.FALSE_SUFFICIENT, 0, 1);

        EvalMetrics metrics = aggregator.aggregate(List.of(fake, sufficient), List.of(), 1, 1);

        assertThat(metrics.getValue("hardError.count").getValue())
                .isEqualByComparingTo("2");
        assertThat(metrics.getValue("hardError.fakeCitation").getValue())
                .isEqualByComparingTo("1");
        assertThat(metrics.getValue("hardError.falseSufficient").getValue())
                .isEqualByComparingTo("1");
    }

    @Test
    void reportsSemanticTurnStructurePassRate() {
        EvalGrade semantic = grade("case-1", EvalLayer.HTTP_E2E,
                "SEMANTIC_TURN_STRUCTURE", EvalSeverity.BLOCKING,
                true, EvalReasonCode.PASS, 1, 1);

        EvalMetrics metrics = aggregator.aggregate(List.of(semantic), List.of(), 1, 1);

        assertThat(metrics.getValue("semantic.turnStructurePassRate").getValue())
                .isEqualByComparingTo("1.0");
    }

    private EvalGrade grade(
            String caseId,
            EvalLayer layer,
            String type,
            EvalSeverity severity,
            boolean passed,
            EvalReasonCode reasonCode,
            long numerator,
            long denominator) {
        return new EvalGrade(caseId, layer, 1, type, severity, passed,
                reasonCode, numerator, denominator);
    }

    private EvalObservation observation(String caseId) {
        return new EvalObservation(
                caseId, EvalLayer.INTELLIGENCE, 1, EvalObservationStatus.PASS,
                null, null, List.of(), List.of(), List.of(),
                AnswerResolution.ANSWERED, ConversationAnswerScope.PORTFOLIO,
                GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                List.of(), 12L, EvalProviderUsage.unavailable(),
                EvalAnswerShape.empty(), false, false);
    }
}
