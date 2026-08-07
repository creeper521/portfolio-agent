package com.portfolio.agent.evaluation.reporting;

import com.portfolio.agent.evaluation.domain.EvalRunIdentity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EvalBaselineComparator {

    public EvalComparison compare(
            EvalRunIdentity currentIdentity,
            EvalMetrics currentMetrics,
            EvalBaseline baseline,
            List<String> currentCaseIds) {
        Objects.requireNonNull(currentIdentity, "currentIdentity");
        Objects.requireNonNull(currentMetrics, "currentMetrics");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(currentCaseIds, "currentCaseIds");
        EvalRunIdentity baselineIdentity = baseline.getIdentity();
        if (!compatible(currentIdentity, baselineIdentity)) {
            return EvalComparison.notComparable();
        }
        Map<String, BigDecimal> deltas = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : baseline.getMetrics().entrySet()) {
            BigDecimal current = currentMetrics.getValue(entry.getKey()).getValue();
            deltas.put(entry.getKey(), current.subtract(entry.getValue()));
        }
        List<String> baselineCaseIds = baseline.getBaselineCaseIds();
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        for (String caseId : currentCaseIds) {
            if (!baselineCaseIds.contains(caseId)) {
                added.add(caseId);
            }
        }
        for (String caseId : baselineCaseIds) {
            if (!currentCaseIds.contains(caseId)) {
                removed.add(caseId);
            }
        }
        return new EvalComparison(true, deltas, added, removed);
    }

    private boolean compatible(EvalRunIdentity current, EvalRunIdentity baseline) {
        return current.getDatasetVersion().equals(baseline.getDatasetVersion())
                && current.getDatasetHash().equals(baseline.getDatasetHash())
                && current.getBundleVersion().equals(baseline.getBundleVersion())
                && current.getBundleHash().equals(baseline.getBundleHash())
                && current.getProvider().equals(baseline.getProvider())
                && current.getModel().equals(baseline.getModel())
                && current.getJudgeModel().equals(baseline.getJudgeModel())
                && current.getJudgeRubricVersion().equals(baseline.getJudgeRubricVersion());
    }
}
