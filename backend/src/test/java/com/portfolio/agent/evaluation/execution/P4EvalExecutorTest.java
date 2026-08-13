package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.evaluation.domain.EvalVerdict;
import com.portfolio.agent.evaluation.domain.P4EvaluationDimension;
import com.portfolio.agent.evaluation.domain.P4SafetyCheck;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class P4EvalExecutorTest {
    @Test
    void ordinaryMockRunPassesLocalDimensionsButLeavesExternalDimensionsIncomplete() {
        P4EvalReport report = new P4EvalExecutor().execute(
                List.of(sample(true)), false, false, false, false, false);

        assertThat(report.getDimensions()).containsEntry(
                P4EvaluationDimension.OFFLINE_VALIDATION, EvalVerdict.PASS)
                .containsEntry(P4EvaluationDimension.MOCK_PROVIDER_INTEGRATION, EvalVerdict.PASS)
                .containsEntry(P4EvaluationDimension.PRIVACY_CAPTURE, EvalVerdict.PASS)
                .containsEntry(P4EvaluationDimension.MODEL_CONFORMANCE, EvalVerdict.PASS)
                .containsEntry(P4EvaluationDimension.REAL_PROVIDER_ACCEPTANCE,
                        EvalVerdict.INCOMPLETE)
                .containsEntry(P4EvaluationDimension.ANSWER_QUALITY_COMPARISON,
                        EvalVerdict.INCOMPLETE);
        assertThat(report.toSafeMap().toString()).doesNotContain(
                "question", "answer text", "P01", "referenceKey", "subjectLabel");
    }

    @Test
    void executedButFailingExternalTrialsAreFailNotPass() {
        P4EvalReport report = new P4EvalExecutor().execute(
                List.of(sample(true)), true, true, false, true, false);

        assertThat(report.getDimensions()).containsEntry(
                P4EvaluationDimension.REAL_PROVIDER_ACCEPTANCE, EvalVerdict.FAIL)
                .containsEntry(P4EvaluationDimension.ANSWER_QUALITY_COMPARISON,
                        EvalVerdict.FAIL);
    }

    @Test
    void anySafetyFailureBlocksOfflineAndConformanceDimensions() {
        P4EvalReport report = new P4EvalExecutor().execute(
                List.of(sample(false)), false, false, false, false, false);

        assertThat(report.getDimensions()).containsEntry(
                P4EvaluationDimension.OFFLINE_VALIDATION, EvalVerdict.FAIL)
                .containsEntry(P4EvaluationDimension.MODEL_CONFORMANCE, EvalVerdict.FAIL);
        assertThat(report.getPassedCheckCounts().get(P4SafetyCheck.ATOMIC_FALLBACK))
                .isZero();
    }

    private P4EvalSample sample(boolean passes) {
        Map<P4SafetyCheck, Boolean> checks = new EnumMap<>(P4SafetyCheck.class);
        for (P4SafetyCheck check : P4SafetyCheck.values()) checks.put(check, passes);
        return new P4EvalSample(checks, true, true);
    }
}
