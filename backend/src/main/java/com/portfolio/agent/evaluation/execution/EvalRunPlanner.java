package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalRiskLevel;
import com.portfolio.agent.evaluation.domain.EvalRunMode;
import com.portfolio.agent.evaluation.domain.EvalSplit;
import com.portfolio.agent.evaluation.domain.EvalSubjectRef;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Selects case-level work without supplying an executor with any oracle information.
 */
public final class EvalRunPlanner {

    private static final int REQUIRED_PROVIDER_TRIALS = 3;

    public EvalRunPlan plan(EvalRunMode mode, List<EvalCase> cases,
                            Set<EvalSubjectRef> changedSubjects) {
        Objects.requireNonNull(mode, "mode");
        List<EvalCase> sourceCases = cases == null ? List.of() : List.copyOf(cases);
        Set<EvalSubjectRef> sourceChangedSubjects = changedSubjects == null
                ? Set.of() : Set.copyOf(changedSubjects);
        if (mode == EvalRunMode.VALIDATE) {
            return new EvalRunPlan(mode, List.of(), false, true);
        }

        List<EvalCase> selected = new ArrayList<EvalCase>();
        for (EvalCase evalCase : sourceCases) {
            if (evalCase == null || evalCase.getSplit() == EvalSplit.CHALLENGE) {
                continue;
            }
            if (mode == EvalRunMode.OFFLINE || isProviderEligible(evalCase, sourceChangedSubjects)) {
                selected.add(evalCase);
            }
        }
        selected.sort(Comparator.comparing(EvalCase::getId));
        boolean requiresOfflineIdentity = mode == EvalRunMode.PROVIDER || mode == EvalRunMode.PERIODIC;
        return new EvalRunPlan(mode, selected, requiresOfflineIdentity, mode != EvalRunMode.PERIODIC);
    }

    private boolean isProviderEligible(EvalCase evalCase, Set<EvalSubjectRef> changedSubjects) {
        if (evalCase.getProviderTrials() != REQUIRED_PROVIDER_TRIALS) {
            return false;
        }
        if (evalCase.getRiskLevel() != EvalRiskLevel.HIGH
                && evalCase.getRiskLevel() != EvalRiskLevel.INVARIANT) {
            return false;
        }
        return !maintainsChangedSubject(evalCase, changedSubjects)
                || (!evalCase.isGeneratedFromBundle() && hasDeepLayer(evalCase));
    }

    private boolean maintainsChangedSubject(EvalCase evalCase, Set<EvalSubjectRef> changedSubjects) {
        if (changedSubjects.isEmpty()) {
            return false;
        }
        List<EvalSubjectRef> maintainedSubjects = evalCase.getMaintenanceSubjects();
        if (maintainedSubjects == null) {
            return false;
        }
        for (EvalSubjectRef maintained : maintainedSubjects) {
            for (EvalSubjectRef changed : changedSubjects) {
                if (sameSubject(maintained, changed)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasDeepLayer(EvalCase evalCase) {
        return evalCase.getLayers() != null
                && (evalCase.getLayers().contains(com.portfolio.agent.evaluation.domain.EvalLayer.INTELLIGENCE)
                || evalCase.getLayers().contains(com.portfolio.agent.evaluation.domain.EvalLayer.HTTP_E2E));
    }

    private boolean sameSubject(EvalSubjectRef left, EvalSubjectRef right) {
        return left != null && right != null && left.getType() == right.getType()
                && Objects.equals(left.getSlug(), right.getSlug());
    }
}
