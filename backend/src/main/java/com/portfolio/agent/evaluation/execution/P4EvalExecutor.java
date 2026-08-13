package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.evaluation.domain.EvalVerdict;
import com.portfolio.agent.evaluation.domain.P4EvaluationDimension;
import com.portfolio.agent.evaluation.domain.P4SafetyCheck;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Aggregates content-free observations produced through the production P4 seam. */
public final class P4EvalExecutor {
    public P4EvalReport execute(List<P4EvalSample> samples,
            boolean realProviderAuthorized, boolean realProviderExecuted,
            boolean realProviderPassed, boolean answerQualityComparisonExecuted,
            boolean answerQualityComparisonPassed) {
        List<P4EvalSample> safeSamples = List.copyOf(Objects.requireNonNull(samples, "samples"));
        EnumMap<P4SafetyCheck, Long> counts = new EnumMap<>(P4SafetyCheck.class);
        for (P4SafetyCheck check : P4SafetyCheck.values()) {
            counts.put(check, safeSamples.stream()
                    .filter(sample -> Boolean.TRUE.equals(sample.getChecks().get(check))).count());
        }
        boolean allChecksPass = !safeSamples.isEmpty()
                && counts.values().stream().allMatch(count -> count == safeSamples.size());
        boolean mockIntegrationPass = allChecksPass
                && safeSamples.stream().allMatch(P4EvalSample::isMockProviderInvoked);
        boolean privacyPass = !safeSamples.isEmpty()
                && safeSamples.stream().allMatch(P4EvalSample::isPrivacyCaptureSafe);
        EnumMap<P4EvaluationDimension, EvalVerdict> dimensions =
                new EnumMap<>(P4EvaluationDimension.class);
        dimensions.put(P4EvaluationDimension.OFFLINE_VALIDATION, verdict(allChecksPass));
        dimensions.put(P4EvaluationDimension.MOCK_PROVIDER_INTEGRATION,
                verdict(mockIntegrationPass));
        dimensions.put(P4EvaluationDimension.PRIVACY_CAPTURE, verdict(privacyPass));
        dimensions.put(P4EvaluationDimension.MODEL_CONFORMANCE,
                verdict(allChecksPass && mockIntegrationPass));
        dimensions.put(P4EvaluationDimension.REAL_PROVIDER_ACCEPTANCE,
                externalVerdict(realProviderAuthorized && realProviderExecuted,
                        realProviderPassed));
        dimensions.put(P4EvaluationDimension.ANSWER_QUALITY_COMPARISON,
                externalVerdict(answerQualityComparisonExecuted,
                        answerQualityComparisonPassed));
        return new P4EvalReport(dimensions, counts, safeSamples.size());
    }

    private EvalVerdict verdict(boolean value) {
        return value ? EvalVerdict.PASS : EvalVerdict.FAIL;
    }

    private EvalVerdict externalVerdict(boolean executed, boolean passed) {
        return !executed ? EvalVerdict.INCOMPLETE : verdict(passed);
    }
}
