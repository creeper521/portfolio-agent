package com.portfolio.agent.evaluation.coverage;

import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalRiskLevel;
import com.portfolio.agent.evaluation.domain.EvalSubjectRef;
import com.portfolio.agent.evaluation.domain.EvalSuite;
import com.portfolio.agent.portfolio.domain.AchievementStatus;
import com.portfolio.agent.portfolio.domain.ContributionType;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Checks whether authored evaluation cases cover public subjects that need deeper regression
 * protection than their generated smoke case.
 */
public final class EvalCoverageLinter {

    private final Set<String> changedSubjectRefs;

    public EvalCoverageLinter(Set<String> changedSubjectRefs) {
        this.changedSubjectRefs = changedSubjectRefs == null
                ? Set.of() : Set.copyOf(changedSubjectRefs);
    }

    public EvalCoverageReport lint(RuntimeContentSnapshot snapshot, List<EvalCase> cases) {
        EvalBundleCatalog catalog = new EvalBundleCatalog(snapshot);
        Set<String> required = deepRequiredSubjects(catalog);
        required.addAll(changedSubjectRefs);

        List<String> deepCovered = new ArrayList<String>();
        List<EvalCoverageIssue> issues = new ArrayList<EvalCoverageIssue>();
        for (String subjectRef : required) {
            DeepCoverageState state = evaluateCoverage(subjectRef, cases);
            if (state.isComplete()) {
                deepCovered.add(subjectRef);
            } else {
                issues.add(new EvalCoverageIssue(
                        "MISSING_DEEP_COVERAGE", subjectRef,
                        "Deep coverage requires an authored maintenance case with HIGH or "
                                + "INVARIANT risk and an HTTP_E2E layer; missing "
                                + state.missingRequirements()));
            }
        }
        issues.sort(Comparator.comparing(EvalCoverageIssue::getCode)
                .thenComparing(EvalCoverageIssue::getSubjectRef));
        return new EvalCoverageReport(
                List.copyOf(required), List.copyOf(deepCovered), List.copyOf(issues));
    }

    public EvalCoverageReport lint(RuntimeContentSnapshot snapshot, EvalSuite suite) {
        return lint(snapshot, suite == null ? null : suite.getCases());
    }

    private Set<String> deepRequiredSubjects(EvalBundleCatalog catalog) {
        Set<String> required = new TreeSet<String>();
        Map<AchievementStatus, EvalBundleCatalog.PublicSubject> achievementSlices =
                new EnumMap<AchievementStatus, EvalBundleCatalog.PublicSubject>(AchievementStatus.class);
        Map<ContributionType, EvalBundleCatalog.PublicSubject> contributionSlices =
                new EnumMap<ContributionType, EvalBundleCatalog.PublicSubject>(ContributionType.class);

        for (EvalBundleCatalog.PublicSubject subject : catalog.getSubjects()) {
            boolean deepCaseByContent = subject.getType()
                    == com.portfolio.agent.portfolio.domain.ClaimSubjectType.CASE
                    && (subject.getClaimCount() > 1 || subject.getQuestionPresetCount() > 1);
            if (subject.isPrimaryProject() || deepCaseByContent) {
                required.add(subject.getCanonicalRef());
            }
            for (String featuredCaseId : subject.getFeaturedCaseIds()) {
                catalog.findCaseById(featuredCaseId)
                        .ifPresent(featured -> required.add(featured.getCanonicalRef()));
            }
            if (subject.getAchievementStatus() != null) {
                achievementSlices.putIfAbsent(subject.getAchievementStatus(), subject);
            }
            if (subject.getContributionType() != null) {
                contributionSlices.putIfAbsent(subject.getContributionType(), subject);
            }
        }
        addSliceRepresentatives(required, achievementSlices);
        addSliceRepresentatives(required, contributionSlices);
        return required;
    }

    private <T extends Enum<T>> void addSliceRepresentatives(
            Set<String> required, Map<T, EvalBundleCatalog.PublicSubject> slices) {
        for (EvalBundleCatalog.PublicSubject subject : slices.values()) {
            required.add(subject.getCanonicalRef());
        }
    }

    private DeepCoverageState evaluateCoverage(String requiredSubject, List<EvalCase> cases) {
        DeepCoverageState state = new DeepCoverageState();
        if (cases == null) {
            return state;
        }
        for (EvalCase evalCase : cases) {
            if (evalCase.isGeneratedFromBundle() || !maintainsSubject(evalCase, requiredSubject)) {
                continue;
            }
            state.hasAuthoredMaintenance = true;
            if (evalCase.getRiskLevel() == EvalRiskLevel.HIGH
                    || evalCase.getRiskLevel() == EvalRiskLevel.INVARIANT) {
                state.hasDeepRisk = true;
            }
            if (hasDeepLayer(evalCase)) {
                state.hasDeepLayer = true;
            }
            if ((evalCase.getRiskLevel() == EvalRiskLevel.HIGH
                    || evalCase.getRiskLevel() == EvalRiskLevel.INVARIANT)
                    && hasDeepLayer(evalCase)) {
                state.hasCompleteDeepCase = true;
            }
        }
        return state;
    }

    private boolean maintainsSubject(EvalCase evalCase, String requiredSubject) {
        List<EvalSubjectRef> subjects = evalCase.getMaintenanceSubjects();
        if (subjects == null) {
            return false;
        }
        for (EvalSubjectRef subject : subjects) {
            if (subject != null && requiredSubject.equals(subject.getType().name() + ":" + subject.getSlug())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDeepLayer(EvalCase evalCase) {
        List<EvalLayer> layers = evalCase.getLayers();
        return layers != null && layers.contains(EvalLayer.HTTP_E2E);
    }

    private static final class DeepCoverageState {

        private boolean hasAuthoredMaintenance;
        private boolean hasDeepRisk;
        private boolean hasDeepLayer;
        private boolean hasCompleteDeepCase;

        private boolean isComplete() {
            return hasCompleteDeepCase;
        }

        private Set<String> missingRequirements() {
            Set<String> missing = new TreeSet<String>();
            if (!hasAuthoredMaintenance) {
                missing.add("AUTHORED_MAINTENANCE_SUBJECT");
            }
            if (!hasDeepRisk) {
                missing.add("HIGH_OR_INVARIANT_RISK");
            }
            if (!hasDeepLayer) {
                missing.add("HTTP_E2E_LAYER");
            }
            return missing;
        }
    }
}
