package com.portfolio.agent.evaluation.grading;

import com.portfolio.agent.evaluation.domain.EvalVerdict;
import com.portfolio.agent.evaluation.domain.P4EvaluationDimension;
import com.portfolio.agent.evaluation.execution.P4EvalReport;
import java.util.List;
import java.util.Objects;

/** Ordinary CI requires four local dimensions; release remains incomplete without real trials. */
public final class P4EvalVerdictPolicy {
    private static final List<P4EvaluationDimension> ORDINARY_CI = List.of(
            P4EvaluationDimension.OFFLINE_VALIDATION,
            P4EvaluationDimension.MOCK_PROVIDER_INTEGRATION,
            P4EvaluationDimension.PRIVACY_CAPTURE,
            P4EvaluationDimension.MODEL_CONFORMANCE);

    public boolean ordinaryCiPasses(P4EvalReport report) {
        Objects.requireNonNull(report, "report");
        return ORDINARY_CI.stream().allMatch(dimension ->
                report.getDimensions().get(dimension) == EvalVerdict.PASS);
    }

    public EvalVerdict releaseVerdict(P4EvalReport report) {
        if (!ordinaryCiPasses(report)) return EvalVerdict.FAIL;
        return report.getDimensions().values().stream().allMatch(verdict -> verdict == EvalVerdict.PASS)
                ? EvalVerdict.PASS : EvalVerdict.INCOMPLETE;
    }
}
